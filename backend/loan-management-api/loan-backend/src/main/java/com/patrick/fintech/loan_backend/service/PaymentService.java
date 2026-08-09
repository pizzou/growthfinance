package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.AuditLogRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
private final PaymentRepository paymentRepo;
private final LoanRepository loanRepo;
private final AuditLogRepository auditRepo;
private final AuditService auditService;
private final UserRepository userRepo;
private final NotificationService notifService;
private final MailService mailService;
private final SmsService smsService;
private final WebhookService webhookService;
private final AccountingService accountingService;

// ================================================================
// RECORD PAYMENT
// ================================================================

@Transactional
public Payment recordPayment(
        Long loanId,
        BigDecimal amount,
        String method,
        String txnId,
        String channel,
        String notes,
        User recordedBy
) {

    if (loanId == null) {
        throw new IllegalArgumentException("Loan ID is required");
    }

    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException(
                "Payment amount must be greater than zero"
        );
    }

    amount = roundMoney(amount);

    String normalizedTxnId =
            normalizeTransactionId(txnId);

    // ============================================================
    // FIND LOAN
    // ============================================================

    Loan loan =
            loanRepo.findById(loanId)
                    .orElseThrow(
                            () -> new RuntimeException(
                                    "Loan not found: " + loanId
                            )
                    );

    // ============================================================
    // ORGANIZATION SECURITY
    // ============================================================

    if (
            recordedBy != null
                    && loan.getOrganization() != null
                    && recordedBy.getOrganization() != null
                    && loan.getOrganization().getId() != null
                    && recordedBy.getOrganization().getId() != null
                    && !loan.getOrganization()
                    .getId()
                    .equals(
                            recordedBy.getOrganization().getId()
                    )
    ) {

        throw new RuntimeException("Access denied");
    }

    // ============================================================
    // IDEMPOTENCY
    // ============================================================

    if (normalizedTxnId != null) {

        if (
                loan.getOrganization() == null
                        || loan.getOrganization().getId() == null
        ) {
            throw new IllegalStateException(
                    "Loan organization is required for transaction validation."
            );
        }

        Optional<Payment> existingPayment =
                paymentRepo
                        .findByOrganization_IdAndTransactionId(
                                loan.getOrganization().getId(),
                                normalizedTxnId
                        );

        if (existingPayment.isPresent()) {

            Payment existing =
                    existingPayment.get();

            if (
                    existing.getLoan() != null
                            && existing.getLoan()
                            .getId()
                            .equals(loanId)
            ) {

                log.info(
                        "Duplicate payment transaction detected. transactionId={}, loanId={}, paymentId={}",
                        normalizedTxnId,
                        loanId,
                        existing.getId()
                );

                return existing;
            }

            throw new IllegalStateException(
                    "Transaction ID "
                            + normalizedTxnId
                            + " has already been used for another loan."
            );
        }
    }

    // ============================================================
    // LOAN STATUS
    // ============================================================

    if (
            loan.getStatus() != LoanStatus.ACTIVE
                    && loan.getStatus() != LoanStatus.OVERDUE
    ) {

        throw new RuntimeException(
                "Loan is not active (status: "
                        + loan.getStatus()
                        + ")"
        );
    }

    // ============================================================
    // CURRENT DATE AND EXACT TIME
    // ============================================================

    LocalDate today =
            LocalDate.now();

    LocalDateTime now =
            LocalDateTime.now();

    // ============================================================
    // FIND EXISTING PAYMENT RECORDS
    // ============================================================

    List<Payment> loanPayments =
            paymentRepo.findByLoanId(loanId);

    // ============================================================
    // FIND CURRENT PARTIAL INSTALLMENT
    // ============================================================

    Optional<Payment> existingCurrentCycle =
            loanPayments.stream()
                    .filter(
                            p ->
                                    !Boolean.TRUE.equals(
                                            p.getPaid()
                                    )
                                            && safe(
                                            p.getAmountPaid()
                                    ).compareTo(BigDecimal.ZERO) > 0
                    )
                    .min(
                            Comparator.comparing(
                                    Payment::getDueDate,
                                    Comparator.nullsLast(
                                            Comparator.naturalOrder()
                                    )
                            )
                    );

    // ============================================================
    // FIND NEXT UNPAID INSTALLMENT
    // ============================================================

    Optional<Payment> unpaidInstallment =
            loanPayments.stream()
                    .filter(
                            p ->
                                    !Boolean.TRUE.equals(
                                            p.getPaid()
                                    )
                    )
                    .min(
                            Comparator.comparing(
                                    Payment::getDueDate,
                                    Comparator.nullsLast(
                                            Comparator.naturalOrder()
                                    )
                            )
                    );

    Payment installment;

    // ============================================================
    // SELECT INSTALLMENT
    // ============================================================

    if (existingCurrentCycle.isPresent()) {

        installment =
                existingCurrentCycle.get();

        log.info(
                "Continuing existing payment cycle. loanId={}, installment={}, paymentId={}",
                loanId,
                installment.getInstallmentNumber(),
                installment.getId()
        );

    } else if (unpaidInstallment.isPresent()) {

        installment =
                unpaidInstallment.get();

        log.info(
                "Using unpaid scheduled installment. loanId={}, installment={}, paymentId={}",
                loanId,
                installment.getInstallmentNumber(),
                installment.getId()
        );

    } else {

        LocalDate dueDate =
                loan.getNextDueDate() != null
                        ? loan.getNextDueDate()
                        : today;

        int nextNumber =
        loanPayments.stream()
                .map(Payment::getInstallmentNumber)
                .filter(n -> n != null)
                .max(Integer::compareTo)
                .orElse(0)
                + 1;

        LocalDateTime previousInterestTimestamp =
                findLatestInterestCalculationTimestamp(
                        loanPayments,
                        loan
                );

        installment =
                Payment.builder()
                        .loan(loan)
                        .organization(
                                loan.getOrganization()
                        )
                        .installmentNumber(
                                nextNumber
                        )
                        .dueDate(
                                dueDate
                        )
                        .amount(
                                loan.getNextInstallmentAmount()
                        )
                        .amountPaid(
                                BigDecimal.ZERO
                        )
                        .principalComponent(
                                BigDecimal.ZERO
                        )
                        .interestComponent(
                                BigDecimal.ZERO
                        )
                        .penalty(
                                BigDecimal.ZERO
                        )
                        .cycleInterestDue(
                                BigDecimal.ZERO
                        )
                        .cycleInterestRemaining(
                                BigDecimal.ZERO
                        )
                        .interestCalculationDate(
                                previousInterestTimestamp
                        )
                        .paid(
                                false
                        )
                        .status(
                                Payment.PaymentStatus.PENDING
                        )
                        .build();

        log.info(
                "Creating new payment cycle. loanId={}, installment={}, inheritedInterestTimestamp={}",
                loanId,
                nextNumber,
                previousInterestTimestamp
        );
    }

    // ================================================================
    // PAYMENT DUE DATE
    // ================================================================

    LocalDate cycleDueDate =
            installment.getDueDate() != null
                    ? installment.getDueDate()
                    : (
                    loan.getNextDueDate() != null
                            ? loan.getNextDueDate()
                            : today
            );

    long daysLateLong =
            ChronoUnit.DAYS.between(
                    cycleDueDate,
                    today
            );

    int daysLate =
            (int)
                    Math.max(
                            0L,
                            daysLateLong
                    );

    boolean isLate =
            daysLate > 0;

    // ================================================================
    // EXISTING VALUES
    // ================================================================

    BigDecimal amountPaidSoFar =
            safe(
                    installment.getAmountPaid()
            );

    BigDecimal interestAlreadyPaid =
            safe(
                    installment.getInterestComponent()
            );

    interestAlreadyPaid =
            interestAlreadyPaid.max(
                    BigDecimal.ZERO
            );

    interestAlreadyPaid =
            roundMoney(
                    interestAlreadyPaid
            );

    BigDecimal penaltyAlreadyRecorded =
            safe(
                    installment.getPenalty()
            );

    penaltyAlreadyRecorded =
            penaltyAlreadyRecorded.max(
                    BigDecimal.ZERO
            );

    penaltyAlreadyRecorded =
            roundMoney(
                    penaltyAlreadyRecorded
            );

    BigDecimal existingCycleInterestDue =
            safe(
                    installment.getCycleInterestDue()
            );

    existingCycleInterestDue =
            existingCycleInterestDue.max(
                    BigDecimal.ZERO
            );

    existingCycleInterestDue =
            roundMoney(
                    existingCycleInterestDue
            );

    // ================================================================
    // CURRENT PRINCIPAL
    // ================================================================

    BigDecimal currentBalance =
            safe(
                    loan.getOutstandingBalance()
            );

    currentBalance =
            currentBalance.max(
                    BigDecimal.ZERO
            );

    currentBalance =
            roundMoney(
                    currentBalance
            );

    if (currentBalance.compareTo(BigDecimal.ZERO) <= 0) {

        throw new IllegalStateException(
                "Loan has no outstanding principal balance."
        );
    }

    // ================================================================
    // INTEREST START TIMESTAMP
    // ================================================================

    LocalDateTime interestStartDateTime =
            determineInterestStartDateTime(
                    installment,
                    loan,
                    loanPayments,
                    now
            );

    if (interestStartDateTime.isAfter(now)) {
        interestStartDateTime = now;
    }

    // ================================================================
    // EXACT ELAPSED HOURS
    // ================================================================

    long elapsedHours =
            ChronoUnit.HOURS.between(
                    interestStartDateTime,
                    now
            );

    if (elapsedHours < 0) {
        elapsedHours = 0;
    }

    // ================================================================
    // INTEREST DAYS
    // ================================================================

    /*
     * FIRST PAYMENT:
     *
     * Loan disbursed today at 10:00
     * Borrower pays today at 10:10
     *
     * Interest = 1 day
     *
     * NOT a full month.
     *
     * After that:
     *
     * Payment at 10:10
     * Another payment at 10:20
     *
     * Additional interest = 0 days.
     *
     * Payment at 10:10 next day
     *
     * Additional interest = 1 day.
     */

    boolean firstInterestCalculation =
            installment.getInterestCalculationDate() == null
                    && amountPaidSoFar.compareTo(BigDecimal.ZERO) <= 0
                    && interestAlreadyPaid.compareTo(BigDecimal.ZERO) <= 0
                    && existingCycleInterestDue.compareTo(BigDecimal.ZERO) <= 0;

    long elapsedDays;

    if (firstInterestCalculation) {

        elapsedDays =
                Math.max(
                        1L,
                        elapsedHours / 24L
                );

    } else {

        elapsedDays =
                elapsedHours / 24L;
    }

    // ================================================================
    // DAILY INTEREST RATE
    // ================================================================

    BigDecimal dailyRate =
            calculateDailyRate(
                    loan
            );

    // ================================================================
    // NEW INTEREST
    // ================================================================

    BigDecimal newlyAccruedInterest =
            roundMoney(
                    currentBalance
                            .multiply(dailyRate)
                            .multiply(
                                    BigDecimal.valueOf(
                                            elapsedDays
                                    )
                            )
            );

    newlyAccruedInterest =
            newlyAccruedInterest.max(
                    BigDecimal.ZERO
            );

    log.info(
            "INTEREST: loanId={}, principal={}, rate={}, rateType={}, dailyRate={}, start={}, now={}, elapsedHours={}, interestDays={}, newInterest={}",
            loan.getId(),
            currentBalance,
            loan.getInterestRate(),
            loan.getInterestRateType(),
            dailyRate,
            interestStartDateTime,
            now,
            elapsedHours,
            elapsedDays,
            newlyAccruedInterest
    );

    // ================================================================
    // TOTAL INTEREST
    // ================================================================

    BigDecimal totalCycleInterestDue =
            roundMoney(
                    existingCycleInterestDue
                            .add(
                                    newlyAccruedInterest
                            )
            );

    totalCycleInterestDue =
            totalCycleInterestDue.max(
                    BigDecimal.ZERO
            );

    BigDecimal remainingInterestBeforePayment =
            roundMoney(
                    totalCycleInterestDue
                            .subtract(
                                    interestAlreadyPaid
                            )
                            .max(
                                    BigDecimal.ZERO
                            )
            );

    // ================================================================
    // DAILY PENALTY
    // ================================================================

    BigDecimal dailyPenaltyRate =
            BigDecimal.valueOf(0.02)
                    .divide(
                            BigDecimal.valueOf(30),
                            12,
                            RoundingMode.HALF_UP
                    );

    BigDecimal calculatedPenalty =
            BigDecimal.ZERO;

    if (daysLate > 0) {

        calculatedPenalty =
                roundMoney(
                        currentBalance
                                .multiply(
                                        dailyPenaltyRate
                                )
                                .multiply(
                                        BigDecimal.valueOf(
                                                daysLate
                                        )
                                )
                );
    }

    BigDecimal newPenalty =
            roundMoney(
                    calculatedPenalty
                            .subtract(
                                    penaltyAlreadyRecorded
                            )
                            .max(
                                    BigDecimal.ZERO
                            )
            );

    BigDecimal totalPenalty =
            roundMoney(
                    penaltyAlreadyRecorded
                            .add(
                                    newPenalty
                            )
            );

    // ================================================================
    // PAYMENT ALLOCATION
    // ================================================================

    /*
     * PAYMENT ORDER:
     *
     * 1. Penalty
     * 2. Interest
     * 3. Principal
     */

    BigDecimal amountAfterPenalty =
            roundMoney(
                    amount
                            .subtract(
                                    newPenalty
                            )
                            .max(
                                    BigDecimal.ZERO
                            )
            );

    BigDecimal interestPaid =
            roundMoney(
                    amountAfterPenalty.min(
                            remainingInterestBeforePayment
                    )
            );

    BigDecimal amountAvailableForPrincipal =
            roundMoney(
                    amountAfterPenalty
                            .subtract(
                                    interestPaid
                            )
                            .max(
                                    BigDecimal.ZERO
                            )
            );

    BigDecimal principalPaid =
            roundMoney(
                    amountAvailableForPrincipal.min(
                            currentBalance
                    )
            );

    // ================================================================
    // NEW PRINCIPAL BALANCE
    // ================================================================

    BigDecimal newBalance =
            roundMoney(
                    currentBalance
                            .subtract(
                                    principalPaid
                            )
                            .max(
                                    BigDecimal.ZERO
                            )
            );

    // ================================================================
    // CUMULATIVE VALUES
    // ================================================================

    BigDecimal totalInterestPaid =
            roundMoney(
                    interestAlreadyPaid
                            .add(
                                    interestPaid
                            )
            );

    BigDecimal existingPrincipalPaid =
            safe(
                    installment.getPrincipalComponent()
            );

    BigDecimal totalPrincipalPaid =
            roundMoney(
                    existingPrincipalPaid
                            .add(
                                    principalPaid
                            )
            );

    BigDecimal remainingInterestAfterPayment =
            roundMoney(
                    totalCycleInterestDue
                            .subtract(
                                    totalInterestPaid
                            )
                            .max(
                                    BigDecimal.ZERO
                            )
            );

    // ================================================================
    // COMPLETION
    // ================================================================

    boolean interestCovered =
            remainingInterestAfterPayment
                    .compareTo(
                            new BigDecimal("0.01")
                    ) <= 0;

    boolean fullyPaidOff =
            newBalance
                    .compareTo(
                            new BigDecimal("0.01")
                    ) <= 0;

    boolean cycleCompleted =
            interestCovered
                    || fullyPaidOff;

    // ================================================================
    // UPDATE PAYMENT
    // ================================================================

    BigDecimal newAmountPaid =
            roundMoney(
                    amountPaidSoFar
                            .add(
                                    amount
                            )
            );

    installment.setAmountPaid(
            newAmountPaid
    );

    installment.setInterestComponent(
            totalInterestPaid
    );

    installment.setPrincipalComponent(
            totalPrincipalPaid
    );

    installment.setPenalty(
            totalPenalty
    );

    installment.setOutstandingAfter(
            newBalance
    );

    installment.setCycleInterestDue(
            totalCycleInterestDue
    );

    installment.setCycleInterestRemaining(
            remainingInterestAfterPayment
    );

    installment.setLate(
            isLate
                    || installment.isLate()
    );

    installment.setDaysLate(
            Math.max(
                    installment.getDaysLate() != null
                            ? installment.getDaysLate()
                            : 0,
                    daysLate
            )
    );

    installment.setPaymentMethod(
            method
    );

    installment.setTransactionId(
            normalizedTxnId
    );

    installment.setChannel(
            channel
    );

    installment.setNotes(
            notes
    );

    if (recordedBy != null) {

        installment.setRecordedBy(
                recordedBy
        );
    }

    installment.setPaidDate(
            today
    );

    /*
     * CRITICAL:
     *
     * Reset the 24-hour interest clock from the exact
     * payment timestamp.
     */
    installment.setInterestCalculationDate(
            now
    );

    installment.setPaid(
            cycleCompleted
    );

    installment.setStatus(
            cycleCompleted
                    ? Payment.PaymentStatus.COMPLETED
                    : Payment.PaymentStatus.PARTIALLY_PAID
    );

    // ================================================================
    // PAYMENT REFERENCE
    // ================================================================

    if (
            installment.getPaymentReference() == null
                    || installment.getPaymentReference()
                    .isBlank()
    ) {

        installment.setPaymentReference(
                generateRef(
                        loan
                )
        );
    }

    // ================================================================
    // SAVE PAYMENT
    // ================================================================

    try {

        installment =
                paymentRepo.save(
                        installment
                );

    } catch (DataIntegrityViolationException e) {

        if (normalizedTxnId != null) {

            Optional<Payment> concurrentPayment =
                    paymentRepo
                            .findByOrganization_IdAndTransactionId(
                                    loan.getOrganization().getId(),
                                    normalizedTxnId
                            );

            if (concurrentPayment.isPresent()) {

                Payment existing =
                        concurrentPayment.get();

                if (
                        existing.getLoan() != null
                                && existing.getLoan()
                                .getId()
                                .equals(loanId)
                ) {

                    log.info(
                            "Concurrent duplicate payment detected. transactionId={}, loanId={}, paymentId={}",
                            normalizedTxnId,
                            loanId,
                            existing.getId()
                    );

                    return existing;
                }
            }
        }

        throw e;
    }

    // ================================================================
    // UPDATE LOAN
    // ================================================================

    BigDecimal oldTotalPaid =
            safe(
                    loan.getTotalPaid()
            );

    loan.setTotalPaid(
            toDouble(
                    roundMoney(
                            oldTotalPaid
                                    .add(
                                            amount
                                    )
                    )
            )
    );

    loan.setOutstandingBalance(
            toDouble(
                    newBalance
            )
    );

    loan.setLastPaymentDate(
            today
    );

    // ================================================================
    // LOAN STATUS
    // ================================================================

    if (fullyPaidOff) {

        loan.setStatus(
                LoanStatus.PAID
        );

        Long currentInstallmentId =
                installment.getId();

        List<Payment> stillPending =
                paymentRepo
                        .findByLoanId(
                                loanId
                        )
                        .stream()
                        .filter(
                                p ->
                                        !Boolean.TRUE.equals(
                                                p.getPaid()
                                        )
                                                && (
                                                p.getId() == null
                                                        || !p.getId()
                                                        .equals(
                                                                currentInstallmentId
                                                        )
                                        )
                        )
                        .toList();

        if (!stillPending.isEmpty()) {

            paymentRepo.deleteAll(
                    stillPending
            );
        }

        loan.setNextDueDate(
                null
        );

        loan.setNextPaymentDate(
                null
        );

        loan.setNextInstallmentAmount(
                0.0
        );

    } else {

        /*
         * A payment does NOT stop daily interest.
         *
         * The reduced principal continues accruing interest
         * from the new interestCalculationDate.
         */
        loan.setStatus(
                isLate
                        ? LoanStatus.OVERDUE
                        : LoanStatus.ACTIVE
        );

        if (
                cycleCompleted
                        || interestCovered
        ) {

            LocalDate nextDue =
                    cycleDueDate.plusMonths(
                            1
                    );

            loan.setNextDueDate(
                    nextDue
            );

            loan.setNextPaymentDate(
                    nextDue
            );

        } else {

            loan.setNextDueDate(
                    cycleDueDate
            );

            loan.setNextPaymentDate(
                    cycleDueDate
            );
        }
    }

    loanRepo.save(
            loan
    );

    // ================================================================
    // AUDIT
    // ================================================================

    audit(
            loan.getOrganization(),
            recordedBy,
            "PAYMENT_RECORDED",
            "PAYMENT",
            installment.getId() != null
                    ? installment.getId().toString()
                    : "UNKNOWN",
            "Payment of "
                    + amount
                    + " on loan "
                    + loan.getReferenceNumber()
                    + " — interest start: "
                    + interestStartDateTime
                    + ", payment time: "
                    + now
                    + ", elapsed hours: "
                    + elapsedHours
                    + ", interest days: "
                    + elapsedDays
                    + ", daily interest: "
                    + roundMoney(
                    currentBalance.multiply(dailyRate)
            )
                    + ", newly accrued interest: "
                    + newlyAccruedInterest
                    + ", interest paid: "
                    + interestPaid
                    + ", principal: "
                    + principalPaid
                    + ", penalty days: "
                    + daysLate
                    + ", daily penalty rate: "
                    + dailyPenaltyRate
                    + ", new penalty: "
                    + newPenalty
                    + ", total penalty: "
                    + totalPenalty
                    + ", remaining interest: "
                    + remainingInterestAfterPayment
                    + ", transactionId: "
                    + normalizedTxnId
    );

    // ================================================================
    // EMAIL
    // ================================================================

    try {

        mailService.sendPaymentConfirmation(
                loan,
                toDouble(amount)
        );

    } catch (Exception e) {

        log.warn(
                "Payment email notification failed",
                e
        );
    }

    // ================================================================
    // SMS
    // ================================================================

    try {

        /*
         * SmsService currently expects:
         *
         * sendPaymentConfirmed(Loan loan, double amount)
         *
         * Therefore BigDecimal is converted only at this
         * service boundary.
         */
        smsService.sendPaymentConfirmed(
                loan,
                toDouble(amount)
        );

    } catch (Exception e) {

        log.warn(
                "Payment SMS notification failed",
                e
        );
    }

    // ================================================================
    // OFFICER NOTIFICATION
    // ================================================================

    if (
            loan.getLoanOfficer() != null
                    && (
                    recordedBy == null
                            || loan.getLoanOfficer()
                            .getId() == null
                            || recordedBy.getId() == null
                            || !loan.getLoanOfficer()
                            .getId()
                            .equals(
                                    recordedBy.getId()
                            )
            )
    ) {

        try {

            notifService.notifyUsers(
                    List.of(
                            loan.getLoanOfficer()
                    ),
                    "Payment Received",
                    "A payment of "
                            + loan.getCurrency()
                            + " "
                            + amount
                            + " was recorded on loan "
                            + loan.getReferenceNumber()
                            + (
                            recordedBy != null
                                    ? " by "
                                    + recordedBy.getName()
                                    : " (automatic)"
                    )
                            + ".",
                    "success",
                    "/dashboard/loans/"
                            + loan.getId()
            );

        } catch (Exception e) {

            log.warn(
                    "In-app payment notification failed",
                    e
            );
        }
    }

    // ================================================================
    // WEBHOOK
    // ================================================================

    try {

        Map<String, Object> paymentWebhook =
                new HashMap<>();

        paymentWebhook.put(
                "paymentId",
                installment.getId()
        );

        paymentWebhook.put(
                "loanId",
                loan.getId()
        );

        paymentWebhook.put(
                "loanReference",
                loan.getReferenceNumber()
        );

        if (loan.getBorrower() != null) {

            paymentWebhook.put(
                    "borrowerId",
                    loan.getBorrower().getId()
            );
        }

        paymentWebhook.put(
                "amount",
                amount
        );

        paymentWebhook.put(
                "principalPaid",
                principalPaid
        );

        paymentWebhook.put(
                "interestPaid",
                interestPaid
        );

        paymentWebhook.put(
                "penalty",
                newPenalty
        );

        paymentWebhook.put(
                "totalPenalty",
                totalPenalty
        );

        paymentWebhook.put(
                "penaltyDays",
                daysLate
        );

        paymentWebhook.put(
                "dailyPenaltyRate",
                dailyPenaltyRate
        );

        paymentWebhook.put(
                "totalInterestPaid",
                totalInterestPaid
        );

        paymentWebhook.put(
                "totalInterestDue",
                totalCycleInterestDue
        );

        paymentWebhook.put(
                "remainingInterest",
                remainingInterestAfterPayment
        );

        paymentWebhook.put(
                "totalPrincipalPaid",
                totalPrincipalPaid
        );

        paymentWebhook.put(
                "outstandingBalance",
                newBalance
        );

        paymentWebhook.put(
                "interestDays",
                elapsedDays
        );

        paymentWebhook.put(
                "interestHours",
                elapsedHours
        );

        paymentWebhook.put(
                "dailyInterestRate",
                dailyRate
        );

        paymentWebhook.put(
                "paymentMethod",
                method
        );

        paymentWebhook.put(
                "channel",
                channel
        );

        paymentWebhook.put(
                "transactionId",
                normalizedTxnId
        );

        paymentWebhook.put(
                "paymentReference",
                installment.getPaymentReference()
        );

        paymentWebhook.put(
                "paymentDate",
                today.toString()
        );

        paymentWebhook.put(
                "paymentTimestamp",
                now.toString()
        );

        paymentWebhook.put(
                "interestCalculationStart",
                interestStartDateTime.toString()
        );

        paymentWebhook.put(
                "interestCalculationDate",
                installment.getInterestCalculationDate() != null
                        ? installment
                        .getInterestCalculationDate()
                        .toString()
                        : null
        );

        paymentWebhook.put(
                "installmentNumber",
                installment.getInstallmentNumber()
        );

        paymentWebhook.put(
                "paymentStatus",
                installment.getStatus() != null
                        ? installment.getStatus().name()
                        : null
        );

        paymentWebhook.put(
                "loanStatus",
                loan.getStatus() != null
                        ? loan.getStatus().name()
                        : null
        );

        log.info(
                "[PAYMENT WEBHOOK] Dispatching PAYMENT_MADE. loanId={}, paymentId={}, amount={}, transactionId={}",
                loan.getId(),
                installment.getId(),
                amount,
                normalizedTxnId
        );

        webhookService.dispatch(
                loan.getOrganization(),
                "PAYMENT_MADE",
                paymentWebhook
        );

    } catch (Exception e) {

        log.error(
                "[PAYMENT WEBHOOK] Failed to dispatch PAYMENT_MADE. loanId={}, paymentId={}",
                loan.getId(),
                installment.getId(),
                e
        );
    }

    // ================================================================
    // ACCOUNTING
    // ================================================================

    try {

        /*
         * AccountingService currently uses the existing numeric
         * signature, so convert BigDecimal only at this boundary.
         */
        accountingService.postPaymentReceived(
                installment,
                toDouble(amount),
                toDouble(principalPaid),
                toDouble(interestPaid),
                toDouble(newPenalty)
        );

    } catch (Exception e) {

        log.error(
                "Accounting posting failed for payment {}",
                installment.getId(),
                e
        );
    }

    return installment;
}

// ================================================================
// GET LOAN SCHEDULE
// ================================================================

public List<Payment> getLoanSchedule(
        Long loanId,
        Long orgId
) {

    Loan loan =
            loanRepo.findById(
                    loanId
            )
                    .orElseThrow(
                            () ->
                                    new RuntimeException(
                                            "Loan not found"
                                    )
                    );

    if (
            loan.getOrganization() == null
                    || loan.getOrganization().getId() == null
                    || orgId == null
                    || !loan.getOrganization()
                    .getId()
                    .equals(
                            orgId
                    )
    ) {

        throw new RuntimeException(
                "Access denied"
        );
    }

    return paymentRepo.findByLoanId(
            loanId
    );
}

// ================================================================
// MARK OVERDUE
// ================================================================

@Transactional
public void markOverdueLoans(
        Long orgId
) {

    List<Payment> overduePayments =
            paymentRepo
                    .findByOrganization_IdAndPaidFalseAndDueDateBefore(
                            orgId,
                            LocalDate.now()
                    );

    for (Payment payment :
            overduePayments) {

        Loan loan =
                payment.getLoan();

        if (loan == null) {
            continue;
        }

        if (
                loan.getStatus()
                        == LoanStatus.ACTIVE
        ) {

            loan.setStatus(
                    LoanStatus.OVERDUE
            );
        }

        if (payment.getDueDate() != null) {

            int days =
                    Math.max(
                            0,
                            (int)
                                    ChronoUnit.DAYS.between(
                                            payment.getDueDate(),
                                            LocalDate.now()
                                    )
                    );

            loan.setDaysOverdue(
                    Math.max(
                            loan.getDaysOverdue() != null
                                    ? loan.getDaysOverdue()
                                    : 0,
                            days
                    )
            );
        }

        loanRepo.save(
                loan
        );
    }
}

// ================================================================
// DETERMINE INTEREST START TIMESTAMP
// ================================================================

private LocalDateTime determineInterestStartDateTime(
        Payment installment,
        Loan loan,
        List<Payment> loanPayments,
        LocalDateTime now
) {

    /*
     * FIRST:
     *
     * Existing installment already has an interest clock.
     */
    if (
            installment.getInterestCalculationDate() != null
    ) {

        return installment.getInterestCalculationDate();
    }

    /*
     * SECOND:
     *
     * Find the latest exact payment timestamp.
     */
    LocalDateTime latestTimestamp =
            findLatestInterestCalculationTimestamp(
                    loanPayments,
                    null
            );

    if (latestTimestamp != null) {

        return latestTimestamp;
    }

    /*
     * THIRD:
     *
     * New loan starts from exact disbursement timestamp.
     */
    if (
            loan.getDisbursedAt() != null
    ) {

        return loan.getDisbursedAt();
    }

    /*
     * LEGACY FALLBACK
     */
    if (
            installment.getCreatedAt() != null
    ) {

        return installment.getCreatedAt();
    }

    /*
     * FINAL FALLBACK
     */
    if (
            loan.getStartDate() != null
    ) {

        return loan.getStartDate()
                .atStartOfDay();
    }

    return now;
}

// ================================================================
// FIND LATEST INTEREST TIMESTAMP
// ================================================================

private LocalDateTime findLatestInterestCalculationTimestamp(
        List<Payment> payments,
        Loan loan
) {

    Optional<LocalDateTime> latest =
            payments.stream()
                    .map(
                            Payment::getInterestCalculationDate
                    )
                    .filter(
                            timestamp ->
                                    timestamp != null
                    )
                    .max(
                            LocalDateTime::compareTo
                    );

    if (latest.isPresent()) {

        return latest.get();
    }

    /*
     * No previous payment timestamp exists.
     *
     * Start from exact loan disbursement timestamp.
     */
    if (
            loan != null
                    && loan.getDisbursedAt() != null
    ) {

        return loan.getDisbursedAt();
    }

    return null;
}

// ================================================================
// DAILY INTEREST RATE
// ================================================================

private BigDecimal calculateDailyRate(
        Loan loan
) {

    BigDecimal rate =
            safe(
                    loan.getInterestRate()
            );

    if (
            rate.compareTo(
                    BigDecimal.ZERO
            ) <= 0
    ) {

        log.warn(
                "Loan {} has zero or missing interest rate. interestRate={}",
                loan.getId(),
                loan.getInterestRate()
        );

        return BigDecimal.ZERO;
    }

    String rateType =
            loan.getInterestRateType() != null
                    ? loan.getInterestRateType().trim()
                    : "MONTHLY";

    // ============================================================
    // MONTHLY RATE
    // ============================================================

    /*
     * Example:
     *
     * Interest rate = 10% MONTHLY
     *
     * 10 / 100 / 30
     *
     * = 0.0033333333 daily rate
     */
    if (
            "MONTHLY".equalsIgnoreCase(
                    rateType
            )
    ) {

        return rate
                .divide(
                        BigDecimal.valueOf(100),
                        12,
                        RoundingMode.HALF_UP
                )
                .divide(
                        BigDecimal.valueOf(30),
                        12,
                        RoundingMode.HALF_UP
                );
    }

    // ============================================================
    // ANNUAL RATE
    // ============================================================

    if (
            "ANNUAL".equalsIgnoreCase(
                    rateType
            )
    ) {

        return rate
                .divide(
                        BigDecimal.valueOf(100),
                        12,
                        RoundingMode.HALF_UP
                )
                .divide(
                        BigDecimal.valueOf(12),
                        12,
                        RoundingMode.HALF_UP
                )
                .divide(
                        BigDecimal.valueOf(30),
                        12,
                        RoundingMode.HALF_UP
                );
    }

    log.warn(
            "Unknown interestRateType '{}' for loan {}. Treating rate as MONTHLY.",
            rateType,
            loan.getId()
    );

    return rate
            .divide(
                    BigDecimal.valueOf(100),
                    12,
                    RoundingMode.HALF_UP
            )
            .divide(
                    BigDecimal.valueOf(30),
                    12,
                    RoundingMode.HALF_UP
            );
}

// ================================================================
// HELPERS
// ================================================================

private String normalizeTransactionId(
        String txnId
) {

    if (txnId == null) {
        return null;
    }

    String normalized =
            txnId.trim();

    return normalized.isBlank()
            ? null
            : normalized;
}

/*
 * BigDecimal version.
 */
private BigDecimal safe(
        BigDecimal value
) {

    if (value == null) {
        return BigDecimal.ZERO;
    }

    return value;
}

/*
 * Double version.
 *
 * Your Loan/Payment entities still contain some Double
 * fields, so this overload is required.
 */
private BigDecimal safe(
        Double value
) {

    if (
            value == null
                    || Double.isNaN(value)
                    || Double.isInfinite(value)
    ) {

        return BigDecimal.ZERO;
    }

    return BigDecimal.valueOf(
            value
    );
}

private BigDecimal roundMoney(
        BigDecimal value
) {

    if (value == null) {

        return BigDecimal.ZERO.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    return value.setScale(
            2,
            RoundingMode.HALF_UP
    );
}

/*
 * Convert BigDecimal back to Double only when calling
 * existing model/service methods that still require Double.
 */
private double toDouble(
        BigDecimal value
) {

    if (value == null) {
        return 0.0;
    }

    return value.doubleValue();
}

private String generateRef(
        Loan loan
) {

    return "PAY-"
            + loan.getReferenceNumber()
            + "-"
            + (
            System.currentTimeMillis()
                    % 100000
    );
}

private void audit(
        Organization org,
        User user,
        String action,
        String entityType,
        String entityId,
        String desc
) {

    auditService.log(
            org,
            user,
            action,
            entityType,
            entityId,
            desc
    );
}


}
