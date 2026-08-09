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

private static final BigDecimal ZERO =
        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

private static final BigDecimal ONE_CENT =
        new BigDecimal("0.01");

private static final BigDecimal THIRTY =
        BigDecimal.valueOf(30);

private static final BigDecimal ONE_HUNDRED =
        BigDecimal.valueOf(100);

/**
 * Records a payment against a loan.
 *
 * INTEREST RULES:
 *
 * 1. Interest starts from the exact loan disbursement timestamp.
 *
 * 2. The first payment always attracts at least one day of interest
 *    whenever the payment occurs after disbursement.
 *
 *    Example:
 *
 *    Disbursed:  10:00
 *    Paid:       10:05
 *
 *    Elapsed time is only 5 minutes, but the system charges:
 *
 *    1 day interest.
 *
 * 3. After a payment, interestCalculationDate becomes the exact
 *    payment timestamp.
 *
 * 4. A second payment during the SAME calendar day does NOT create
 *    another interest charge.
 *
 * 5. A payment on the following calendar day creates one additional
 *    day of interest.
 *
 * 6. If several calendar days pass, interest is charged for every
 *    calendar day since the previous interest timestamp.
 *
 * 7. Interest is paid before principal.
 *
 * 8. Existing unpaid cycle interest is never lost.
 *
 * 9. Principal is reduced only after penalty and interest have been
 *    satisfied.
 */
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
                paymentRepo.findByOrganization_IdAndTransactionId(
                        loan.getOrganization().getId(),
                        normalizedTxnId
                );

        if (existingPayment.isPresent()) {

            Payment existing =
                    existingPayment.get();

            if (
                    existing.getLoan() != null
                            && existing.getLoan().getId() != null
                            && existing.getLoan().getId().equals(loanId)
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
    // CURRENT DATE / TIME
    // ============================================================

    LocalDate today =
            LocalDate.now();

    LocalDateTime now =
            LocalDateTime.now();

    // ============================================================
    // FIND PAYMENT RECORDS
    // ============================================================

    List<Payment> loanPayments =
            paymentRepo.findByLoanId(loanId);

    // ============================================================
    // FIND CURRENT PARTIAL PAYMENT
    // ============================================================

    Optional<Payment> existingCurrentCycle =
            loanPayments.stream()
                    .filter(
                            p ->
                                    !Boolean.TRUE.equals(
                                            p.getPaid()
                                    )
                                            && safe(
                                            p.getAmountPaidDecimal()
                                    ).compareTo(
                                            BigDecimal.ZERO
                                    ) > 0
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

        /*
         * IMPORTANT:
         *
         * Do NOT put the disbursement timestamp into
         * interestCalculationDate here.
         *
         * NULL means:
         *
         * "No payment has yet been made in this cycle."
         *
         * recordPayment() will then use loan.disbursedAt as the
         * starting point.
         */
        int nextNumber =
        loanPayments.stream()
                .map(Payment::getInstallmentNumber)
                .filter(n -> n != null)
                .max(Integer::compareTo)
                .orElse(0)
                + 1;

        installment =
                Payment.builder()
                        .loan(loan)
                        .organization(
                                loan.getOrganization()
                        )
                        .installmentNumber(nextNumber)
                        .dueDate(dueDate)
                        .amount(
                                safe(
                                        loan.getNextInstallmentAmountDecimal()
                                )
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
                                null
                        )
                        .paid(false)
                        .status(
                                Payment.PaymentStatus.PENDING
                        )
                        .build();

        log.info(
                "Creating new payment cycle. loanId={}, installment={}",
                loanId,
                nextNumber
        );
    }

    // ============================================================
    // PAYMENT DUE DATE
    // ============================================================

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
            (int) Math.max(
                    0L,
                    daysLateLong
            );

    boolean isLate =
            daysLate > 0;

    // ============================================================
    // EXISTING PAYMENT VALUES
    // ============================================================

    BigDecimal amountPaidSoFar =
            roundMoney(
                    safe(
                            installment.getAmountPaidDecimal()
                    )
            );

    BigDecimal interestAlreadyPaid =
            roundMoney(
                    safe(
                            installment
                                    .getInterestComponentDecimal()
                    )
            );

    BigDecimal penaltyAlreadyRecorded =
            roundMoney(
                    safe(
                            installment.getPenaltyDecimal()
                    )
            );

    BigDecimal existingCycleInterestDue =
            roundMoney(
                    safe(
                            installment
                                    .getCycleInterestDueDecimal()
                    )
            );

    BigDecimal existingCycleInterestRemaining =
            roundMoney(
                    safe(
                            installment
                                    .getCycleInterestRemainingDecimal()
                    )
            );

    interestAlreadyPaid =
            interestAlreadyPaid.max(ZERO);

    penaltyAlreadyRecorded =
            penaltyAlreadyRecorded.max(ZERO);

    existingCycleInterestDue =
            existingCycleInterestDue.max(ZERO);

    existingCycleInterestRemaining =
            existingCycleInterestRemaining.max(ZERO);

    // ============================================================
    // CURRENT PRINCIPAL
    // ============================================================

    BigDecimal currentBalance =
            roundMoney(
                    safe(
                            loan.getOutstandingBalanceDecimal()
                    )
            );

    currentBalance =
            currentBalance.max(ZERO);

    if (
            currentBalance.compareTo(
                    BigDecimal.ZERO
            ) <= 0
    ) {

        throw new IllegalStateException(
                "Loan has no outstanding principal balance."
        );
    }

    // ============================================================
    // DETERMINE INTEREST START
    // ============================================================

    LocalDateTime interestStartDateTime =
            determineInterestStartDateTime(
                    installment,
                    loan,
                    loanPayments,
                    now
            );

    if (interestStartDateTime == null) {
        interestStartDateTime = now;
    }

    if (interestStartDateTime.isAfter(now)) {
        interestStartDateTime = now;
    }

    // ============================================================
    // CALCULATE INTEREST DAYS
    // ============================================================

    long elapsedDays =
            calculateActualInterestDays(
                    interestStartDateTime,
                    now,
                    installment,
                    loan
            );

    // ============================================================
    // DAILY INTEREST RATE
    // ============================================================

    BigDecimal dailyRate =
            calculateDailyRate(loan);

    // ============================================================
    // NEW INTEREST
    // ============================================================

    BigDecimal newlyAccruedInterest =
            calculateNewInterest(
                    currentBalance,
                    dailyRate,
                    elapsedDays
            );

    // ============================================================
    // TOTAL CYCLE INTEREST
    // ============================================================

    BigDecimal totalCycleInterestDue =
            roundMoney(
                    existingCycleInterestDue
                            .add(
                                    newlyAccruedInterest
                            )
            );

    // ============================================================
    // REMAINING INTEREST BEFORE PAYMENT
    // ============================================================

    BigDecimal calculatedRemainingInterest =
            roundMoney(
                    totalCycleInterestDue
                            .subtract(
                                    interestAlreadyPaid
                            )
                            .max(
                                    BigDecimal.ZERO
                            )
            );

    BigDecimal persistedRemainingInterest =
            existingCycleInterestRemaining
                    .max(BigDecimal.ZERO);

    BigDecimal remainingInterestBeforePayment =
            calculatedRemainingInterest.max(
                    persistedRemainingInterest
            );

    if (
            remainingInterestBeforePayment
                    .compareTo(
                            totalCycleInterestDue
                    ) > 0
    ) {
        remainingInterestBeforePayment =
                totalCycleInterestDue;
    }

    remainingInterestBeforePayment =
            roundMoney(
                    remainingInterestBeforePayment
            );

    // ============================================================
    // INTEREST LOG
    // ============================================================

    log.info(
            "INTEREST CALCULATION: loanId={}, principal={}, rate={}, rateType={}, dailyRate={}, start={}, paymentTime={}, elapsedDays={}, existingInterest={}, newlyAccrued={}, totalInterestDue={}, interestPaid={}, remainingInterest={}",
            loan.getId(),
            currentBalance,
            loan.getInterestRateDecimal(),
            loan.getInterestRateType(),
            dailyRate,
            interestStartDateTime,
            now,
            elapsedDays,
            existingCycleInterestDue,
            newlyAccruedInterest,
            totalCycleInterestDue,
            interestAlreadyPaid,
            remainingInterestBeforePayment
    );

    // ============================================================
    // PENALTY
    // ============================================================

    BigDecimal dailyPenaltyRate =
            BigDecimal.valueOf(0.02)
                    .divide(
                            THIRTY,
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

    // ============================================================
    // PAYMENT ALLOCATION
    // ============================================================
    //
    // 1. PENALTY
    // 2. INTEREST
    // 3. PRINCIPAL
    // ============================================================

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

    // ============================================================
    // NEW PRINCIPAL
    // ============================================================

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

    // ============================================================
    // CUMULATIVE INTEREST
    // ============================================================

    BigDecimal totalInterestPaid =
            roundMoney(
                    interestAlreadyPaid
                            .add(
                                    interestPaid
                            )
            );

    // ============================================================
    // CUMULATIVE PRINCIPAL
    // ============================================================

    BigDecimal existingPrincipalPaid =
            roundMoney(
                    safe(
                            installment
                                    .getPrincipalComponentDecimal()
                    )
            );

    BigDecimal totalPrincipalPaid =
            roundMoney(
                    existingPrincipalPaid
                            .add(
                                    principalPaid
                            )
            );

    // ============================================================
    // REMAINING INTEREST
    // ============================================================

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

    // ============================================================
    // PAYMENT COMPLETION
    // ============================================================

    boolean interestCovered =
            remainingInterestAfterPayment
                    .compareTo(
                            ONE_CENT
                    ) <= 0;

    boolean fullyPaidOff =
            newBalance.compareTo(
                    ONE_CENT
            ) <= 0;

    /*
     * The cycle can advance once the current cycle's interest
     * has been completely paid.
     *
     * If the loan itself is fully paid, it is also completed.
     */
    boolean cycleCompleted =
            interestCovered
                    || fullyPaidOff;

    // ============================================================
    // UPDATE PAYMENT
    // ============================================================

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
     * CRITICAL INTEREST CLOCK.
     *
     * Every successful payment moves the interest clock to the
     * exact payment timestamp.
     *
     * First payment:
     *
     * disbursement 10:00
     * payment      10:05
     * charge       1 day
     *
     * Second payment:
     *
     * previous payment 10:05
     * second payment   10:10
     * same calendar day
     * charge           0 additional days
     *
     * Next-day payment:
     *
     * previous payment 10:05
     * next day payment 10:05
     * charge           1 additional day
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

    // ============================================================
    // PAYMENT REFERENCE
    // ============================================================

    if (
            installment.getPaymentReference() == null
                    || installment.getPaymentReference().isBlank()
    ) {

        installment.setPaymentReference(
                generateRef(loan)
        );
    }

    // ============================================================
    // SAVE PAYMENT
    // ============================================================

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
                                && existing.getLoan().getId() != null
                                && existing.getLoan().getId()
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

    // ============================================================
    // UPDATE LOAN
    // ============================================================

    BigDecimal oldTotalPaid =
            roundMoney(
                    safe(
                            loan.getTotalPaidDecimal()
                    )
            );

    BigDecimal newTotalPaid =
            roundMoney(
                    oldTotalPaid.add(
                            amount
                    )
            );

    loan.setTotalPaid(
            newTotalPaid
    );

    loan.setOutstandingBalance(
            newBalance
    );

    loan.setLastPaymentDate(
            today
    );

    // ============================================================
    // LOAN STATUS
    // ============================================================

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
                BigDecimal.ZERO
        );

    } else {

        loan.setStatus(
                isLate
                        ? LoanStatus.OVERDUE
                        : LoanStatus.ACTIVE
        );

        if (cycleCompleted) {

            LocalDate nextDue =
                    cycleDueDate.plusMonths(1);

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

    // ============================================================
    // AUDIT
    // ============================================================

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
                    + ", elapsed days: "
                    + elapsedDays
                    + ", daily interest rate: "
                    + dailyRate
                    + ", daily interest before payment: "
                    + roundMoney(
                    currentBalance.multiply(
                            dailyRate
                    )
            )
                    + ", newly accrued interest: "
                    + newlyAccruedInterest
                    + ", total cycle interest: "
                    + totalCycleInterestDue
                    + ", interest paid: "
                    + interestPaid
                    + ", principal paid: "
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
                    + ", outstanding principal: "
                    + newBalance
                    + ", transactionId: "
                    + normalizedTxnId
    );

    // ============================================================
    // EMAIL
    // ============================================================

    try {

        mailService.sendPaymentConfirmation(
                loan,
                amount.doubleValue()
        );

    } catch (Exception e) {

        log.warn(
                "Payment email notification failed",
                e
        );
    }

    // ============================================================
    // SMS
    // ============================================================

    try {

        smsService.sendPaymentConfirmed(
                loan,
                amount.doubleValue()
        );

    } catch (Exception e) {

        log.warn(
                "Payment SMS notification failed",
                e
        );
    }

    // ============================================================
    // OFFICER NOTIFICATION
    // ============================================================

    if (
            loan.getLoanOfficer() != null
                    && (
                    recordedBy == null
                            || loan.getLoanOfficer().getId() == null
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

    // ============================================================
    // WEBHOOK
    // ============================================================

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
                "[PAYMENT WEBHOOK] Dispatching PAYMENT_MADE. loanId={}, paymentId={}, amount={}, interestPaid={}, principalPaid={}, elapsedDays={}, transactionId={}",
                loan.getId(),
                installment.getId(),
                amount,
                interestPaid,
                principalPaid,
                elapsedDays,
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

    // ============================================================
    // ACCOUNTING
    // ============================================================

    try {

        accountingService.postPaymentReceived(
                installment,
                amount.doubleValue(),
                principalPaid.doubleValue(),
                interestPaid.doubleValue(),
                newPenalty.doubleValue()
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
                            () -> new RuntimeException(
                                    "Loan not found"
                            )
                    );

    if (
            loan.getOrganization() == null
                    || loan.getOrganization().getId() == null
                    || orgId == null
                    || !loan.getOrganization()
                    .getId()
                    .equals(orgId)
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

        if (
                payment.getDueDate()
                        != null
        ) {

            int days =
                    Math.max(
                            0,
                            (int) ChronoUnit.DAYS.between(
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
// DETERMINE INTEREST START
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
     * If this payment cycle already has a payment, the interest
     * clock is the exact timestamp of the last payment.
     */
    if (
            installment.getInterestCalculationDate()
                    != null
    ) {

        return installment
                .getInterestCalculationDate();
    }

    /*
     * SECOND:
     *
     * Search all previous payment records.
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
     * For a first payment, interest starts exactly when money was
     * disbursed.
     */
    if (
            loan.getDisbursedAt()
                    != null
    ) {

        return loan.getDisbursedAt();
    }

    /*
     * FOURTH:
     *
     * Fallback to loan start.
     */
    if (
            loan.getStartDate()
                    != null
    ) {

        return loan
                .getStartDate()
                .atStartOfDay();
    }

    /*
     * LAST FALLBACK.
     */
    return now;
}

// ================================================================
// FIND LATEST INTEREST TIMESTAMP
// ================================================================

private LocalDateTime findLatestInterestCalculationTimestamp(
        List<Payment> payments,
        Loan loan
) {

    if (
            payments != null
                    && !payments.isEmpty()
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
    }

    if (
            loan != null
                    && loan.getDisbursedAt()
                    != null
    ) {

        return loan.getDisbursedAt();
    }

    return null;
}

// ================================================================
// ACTUAL INTEREST DAYS
// ================================================================

private long calculateActualInterestDays(
        LocalDateTime interestStart,
        LocalDateTime now,
        Payment installment,
        Loan loan
) {

    if (
            interestStart == null
                    || now == null
    ) {
        return 0L;
    }

    if (!interestStart.isBefore(now)) {
        return 0L;
    }

    /*
     * ============================================================
     * FIRST PAYMENT AFTER DISBURSEMENT
     * ============================================================
     *
     * This is the special rule requested.
     *
     * Example:
     *
     * Disbursement: 10:00
     * Payment:      10:05
     *
     * The elapsed time is only five minutes.
     *
     * Nevertheless:
     *
     * interestDays = 1
     *
     * This prevents a borrower from receiving the money and
     * immediately avoiding the minimum one-day interest charge.
     */
    boolean firstPayment =
            installment.getInterestCalculationDate()
                    == null
                    && safe(
                    installment.getAmountPaidDecimal()
            ).compareTo(
                    BigDecimal.ZERO
            ) <= 0
                    && safe(
                    installment
                            .getInterestComponentDecimal()
            ).compareTo(
                    BigDecimal.ZERO
            ) <= 0
                    && safe(
                    installment
                            .getCycleInterestDueDecimal()
            ).compareTo(
                    BigDecimal.ZERO
            ) <= 0;

    if (firstPayment) {

        /*
         * The interest clock is normally loan.disbursedAt.
         *
         * Any positive elapsed time produces at least one day.
         */
        if (interestStart.isBefore(now)) {
            return 1L;
        }

        return 0L;
    }

    /*
     * ============================================================
     * SUBSEQUENT PAYMENTS
     * ============================================================
     *
     * Same calendar day:
     *
     * 10:05 -> 15:30
     *
     * = 0 additional interest days.
     *
     * Next calendar day:
     *
     * Aug 8 -> Aug 9
     *
     * = 1 additional day.
     *
     * Two calendar days:
     *
     * Aug 8 -> Aug 10
     *
     * = 2 days.
     */
    long calendarDays =
            ChronoUnit.DAYS.between(
                    interestStart.toLocalDate(),
                    now.toLocalDate()
            );

    return Math.max(
            0L,
            calendarDays
    );
}

// ================================================================
// DAILY INTEREST RATE
// ================================================================

private BigDecimal calculateDailyRate(
        Loan loan
) {

    BigDecimal rate =
            safe(
                    loan.getInterestRateDecimal()
            );

    if (
            rate.compareTo(
                    BigDecimal.ZERO
            ) <= 0
    ) {

        log.warn(
                "Loan {} has zero or missing interest rate. interestRate={}",
                loan.getId(),
                rate
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

    if (
            "MONTHLY".equalsIgnoreCase(
                    rateType
            )
    ) {

        /*
         * Example:
         *
         * Monthly interest = 10%
         *
         * Daily rate:
         *
         * 10 / 100 / 30
         *
         * = 0.0033333333
         */
        return rate
                .divide(
                        ONE_HUNDRED,
                        12,
                        RoundingMode.HALF_UP
                )
                .divide(
                        THIRTY,
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

        /*
         * Example:
         *
         * Annual rate = 24%
         *
         * Daily rate:
         *
         * 24 / 100 / 12 / 30
         */
        return rate
                .divide(
                        ONE_HUNDRED,
                        12,
                        RoundingMode.HALF_UP
                )
                .divide(
                        BigDecimal.valueOf(12),
                        12,
                        RoundingMode.HALF_UP
                )
                .divide(
                        THIRTY,
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
                    ONE_HUNDRED,
                    12,
                    RoundingMode.HALF_UP
            )
            .divide(
                    THIRTY,
                    12,
                    RoundingMode.HALF_UP
            );
}

// ================================================================
// CALCULATE NEW INTEREST
// ================================================================

private BigDecimal calculateNewInterest(
        BigDecimal currentBalance,
        BigDecimal dailyRate,
        long elapsedDays
) {

    if (
            currentBalance == null
                    || currentBalance.compareTo(
                    BigDecimal.ZERO
            ) <= 0
    ) {
        return ZERO;
    }

    if (
            dailyRate == null
                    || dailyRate.compareTo(
                    BigDecimal.ZERO
            ) <= 0
    ) {
        return ZERO;
    }

    if (elapsedDays <= 0) {

        log.info(
                "No new interest accrued because elapsedDays={}",
                elapsedDays
        );

        return ZERO;
    }

    return roundMoney(
            currentBalance
                    .multiply(
                            dailyRate
                    )
                    .multiply(
                            BigDecimal.valueOf(
                                    elapsedDays
                            )
                    )
    );
}

// ================================================================
// SAFE BIGDECIMAL
// ================================================================

private BigDecimal safe(
        BigDecimal value
) {

    if (value == null) {
        return ZERO;
    }

    return value;
}

// ================================================================
// ROUND MONEY
// ================================================================

private BigDecimal roundMoney(
        BigDecimal value
) {

    if (value == null) {
        return ZERO;
    }

    return value.setScale(
            2,
            RoundingMode.HALF_UP
    );
}

// ================================================================
// TRANSACTION ID
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

// ================================================================
// PAYMENT REFERENCE
// ================================================================

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

// ================================================================
// AUDIT
// ================================================================

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
