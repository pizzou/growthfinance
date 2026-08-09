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

    private static final BigDecimal TWELVE =
            BigDecimal.valueOf(12);

    private static final BigDecimal ONE_HUNDRED =
            BigDecimal.valueOf(100);

    /**
     * Record a borrower payment.
     *
     * Business rules:
     *
     * 1. Payment is allocated to penalty first.
     * 2. Then accrued interest.
     * 3. Only the remainder reduces principal.
     * 4. Interest is calculated only for newly elapsed calendar days.
     * 5. The exact interestCalculationDate is used as the interest clock.
     * 6. Multiple payments during the same calendar day do not create
     *    another day of interest.
     * 7. A payment on a later calendar day calculates interest only for
     *    the newly elapsed days.
     * 8. New interest is calculated on the current outstanding principal.
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
        // EXISTING PAYMENT RECORDS
        // ============================================================

        List<Payment> loanPayments =
                paymentRepo.findByLoanId(loanId);

        // ============================================================
        // FIND EXISTING PARTIAL PAYMENT CYCLE
        // ============================================================

        Optional<Payment> existingCurrentCycle =
                loanPayments.stream()
                        .filter(
                                p ->
                                        !Boolean.TRUE.equals(
                                                p.getPaid()
                                        )
                                                && getAmountPaidDecimal(p)
                                                .compareTo(
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

            BigDecimal nextInstallmentAmount =
                    getNextInstallmentAmountDecimal(loan);

            installment =
                    Payment.builder()
                            .loan(loan)
                            .organization(
                                    loan.getOrganization()
                            )
                            .installmentNumber(nextNumber)
                            .dueDate(dueDate)
                            .amount(nextInstallmentAmount)
                            .amountPaid(BigDecimal.ZERO)
                            .principalComponent(BigDecimal.ZERO)
                            .interestComponent(BigDecimal.ZERO)
                            .penalty(BigDecimal.ZERO)
                            .cycleInterestDue(BigDecimal.ZERO)
                            .cycleInterestRemaining(BigDecimal.ZERO)
                            .interestCalculationDate(
                                    previousInterestTimestamp
                            )
                            .paid(false)
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
                        getAmountPaidDecimal(installment)
                );

        BigDecimal interestAlreadyPaid =
                roundMoney(
                        getInterestComponentDecimal(
                                installment
                        )
                );

        BigDecimal penaltyAlreadyRecorded =
                roundMoney(
                        getPenaltyDecimal(
                                installment
                        )
                );

        BigDecimal existingCycleInterestDue =
                roundMoney(
                        getCycleInterestDueDecimal(
                                installment
                        )
                );

        BigDecimal existingCycleInterestRemaining =
                roundMoney(
                        getCycleInterestRemainingDecimal(
                                installment
                        )
                );

        amountPaidSoFar =
                amountPaidSoFar.max(ZERO);

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
                        getOutstandingBalanceDecimal(
                                loan
                        )
                );

        currentBalance =
                currentBalance.max(ZERO);

        if (currentBalance.compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalStateException(
                    "Loan has no outstanding principal balance."
            );
        }

        // ============================================================
        // INTEREST START TIMESTAMP
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
        // ACTUAL DAYS USED
        // ============================================================

        long elapsedDays =
                calculateActualInterestDays(
                        interestStartDateTime,
                        now
                );

        // ============================================================
        // DAILY INTEREST RATE
        // ============================================================

        BigDecimal dailyRate =
                calculateDailyRate(loan);

        // ============================================================
        // NEWLY ACCRUED INTEREST
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

        /*
         * Preserve existing unpaid interest.
         *
         * The remaining interest can never disappear simply because
         * another payment was made.
         */
        BigDecimal derivedRemainingInterest =
                roundMoney(
                        totalCycleInterestDue
                                .subtract(
                                        interestAlreadyPaid
                                )
                                .max(BigDecimal.ZERO)
                );

        BigDecimal previousRemainingPlusNew =
                roundMoney(
                        existingCycleInterestRemaining
                                .add(
                                        newlyAccruedInterest
                                )
                );

        BigDecimal remainingInterestBeforePayment =
                derivedRemainingInterest.max(
                        previousRemainingPlusNew
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
        // INTEREST DIAGNOSTIC LOG
        // ============================================================

        log.info(
                "INTEREST CALCULATION: loanId={}, principal={}, interestRate={}, rateType={}, dailyRate={}, start={}, now={}, elapsedDays={}, existingCycleInterest={}, newlyAccruedInterest={}, totalCycleInterest={}, interestAlreadyPaid={}, remainingInterest={}",
                loan.getId(),
                currentBalance,
                getInterestRateDecimal(loan),
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
        // DAILY PENALTY
        // ============================================================

        BigDecimal dailyPenaltyRate =
                new BigDecimal("0.02")
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
                                .max(BigDecimal.ZERO)
                );

        BigDecimal totalPenalty =
                roundMoney(
                        penaltyAlreadyRecorded
                                .add(newPenalty)
                );

        // ============================================================
        // PAYMENT ALLOCATION
        // ============================================================

        /*
         * PAYMENT PRIORITY:
         *
         * 1. PENALTY
         * 2. INTEREST
         * 3. PRINCIPAL
         */

        BigDecimal amountAfterPenalty =
                roundMoney(
                        amount
                                .subtract(
                                        newPenalty
                                )
                                .max(BigDecimal.ZERO)
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
                                .max(BigDecimal.ZERO)
                );

        BigDecimal principalPaid =
                roundMoney(
                        amountAvailableForPrincipal.min(
                                currentBalance
                        )
                );

        // ============================================================
        // NEW PRINCIPAL BALANCE
        // ============================================================

        BigDecimal newBalance =
                roundMoney(
                        currentBalance
                                .subtract(
                                        principalPaid
                                )
                                .max(BigDecimal.ZERO)
                );

        // ============================================================
        // CUMULATIVE INTEREST
        // ============================================================

        BigDecimal totalInterestPaid =
                roundMoney(
                        interestAlreadyPaid
                                .add(interestPaid)
                );

        // ============================================================
        // CUMULATIVE PRINCIPAL
        // ============================================================

        BigDecimal existingPrincipalPaid =
                roundMoney(
                        getPrincipalComponentDecimal(
                                installment
                        )
                );

        BigDecimal totalPrincipalPaid =
                roundMoney(
                        existingPrincipalPaid
                                .add(principalPaid)
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
                                .max(BigDecimal.ZERO)
                );

        // ============================================================
        // PAYMENT COMPLETION
        // ============================================================

        boolean interestCovered =
                remainingInterestAfterPayment
                        .compareTo(ONE_CENT) <= 0;

        boolean fullyPaidOff =
                newBalance
                        .compareTo(ONE_CENT) <= 0;

        /*
         * Once the interest for the current monthly cycle is fully
         * paid, the current cycle is complete.
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
                                .add(amount)
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
                isLate || installment.isLate()
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
         * The interest clock advances to this exact timestamp.
         *
         * Monday 10:00 -> Monday 15:00
         * = 0 additional calendar days.
         *
         * Monday 10:00 -> Tuesday 10:00
         * = 1 additional calendar day.
         *
         * Tuesday 10:00 -> Wednesday 10:00
         * = another 1 day.
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
                        getTotalPaidDecimal(loan)
                );

        BigDecimal newTotalPaid =
                roundMoney(
                        oldTotalPaid.add(amount)
                );

        setTotalPaidDecimal(
                loan,
                newTotalPaid
        );

        setOutstandingBalanceDecimal(
                loan,
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
                            .findByLoanId(loanId)
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

            setNextInstallmentAmountDecimal(
                    loan,
                    BigDecimal.ZERO
            );

        } else {

            loan.setStatus(
                    isLate
                            ? LoanStatus.OVERDUE
                            : LoanStatus.ACTIVE
            );

            /*
             * Move to the next monthly cycle only when the current
             * cycle's interest has been completely paid.
             *
             * If interest is still outstanding, remain in the same
             * payment cycle.
             */
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
                loanRepo.findById(loanId)
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

        for (Payment payment : overduePayments) {

            Loan loan =
                    payment.getLoan();

            if (loan == null) {
                continue;
            }

            if (loan.getStatus() == LoanStatus.ACTIVE) {

                loan.setStatus(
                        LoanStatus.OVERDUE
                );
            }

            if (payment.getDueDate() != null) {

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
    // DETERMINE INTEREST START TIMESTAMP
    // ================================================================

    private LocalDateTime determineInterestStartDateTime(
            Payment installment,
            Loan loan,
            List<Payment> loanPayments,
            LocalDateTime now
    ) {

        /*
         * Existing payment cycle has its own interest clock.
         */
        if (
                installment.getInterestCalculationDate() != null
        ) {

            return installment
                    .getInterestCalculationDate();
        }

        /*
         * Otherwise use the latest recorded interest timestamp.
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
         * New loan starts from exact disbursement timestamp.
         */
        if (loan.getDisbursedAt() != null) {

            return loan.getDisbursedAt();
        }

        /*
         * Payment creation timestamp.
         */
        if (installment.getCreatedAt() != null) {

            return installment.getCreatedAt();
        }

        /*
         * Loan start date.
         */
        if (loan.getStartDate() != null) {

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
                        && loan.getDisbursedAt() != null
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
            LocalDateTime now
    ) {

        if (
                interestStart == null
                        || now == null
        ) {
            return 0L;
        }

        if (
                !interestStart.isBefore(now)
        ) {
            return 0L;
        }

        /*
         * Calendar-day rule.
         *
         * 2026-08-08 10:30
         * ->
         * 2026-08-09 10:30
         *
         * = 1 day.
         *
         * 2026-08-08 10:30
         * ->
         * 2026-08-08 15:30
         *
         * = 0 days.
         *
         * This prevents multiple same-day payments from charging
         * the same daily interest again.
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
                getInterestRateDecimal(
                        loan
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

            return rate
                    .divide(
                            ONE_HUNDRED,
                            12,
                            RoundingMode.HALF_UP
                    )
                    .divide(
                            TWELVE,
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
                        .multiply(dailyRate)
                        .multiply(
                                BigDecimal.valueOf(
                                        elapsedDays
                                )
                        )
        );
    }

    // ================================================================
    // PAYMENT DECIMAL GETTERS
    // ================================================================

    private BigDecimal getAmountPaidDecimal(
            Payment payment
    ) {

        if (payment == null) {
            return ZERO;
        }

        BigDecimal value =
                payment.getAmountPaidDecimal();

        return safe(value);
    }

    private BigDecimal getInterestComponentDecimal(
            Payment payment
    ) {

        if (payment == null) {
            return ZERO;
        }

        BigDecimal value =
                payment.getInterestComponentDecimal();

        return safe(value);
    }

    private BigDecimal getPrincipalComponentDecimal(
            Payment payment
    ) {

        if (payment == null) {
            return ZERO;
        }

        BigDecimal value =
                payment.getPrincipalComponentDecimal();

        return safe(value);
    }

    private BigDecimal getPenaltyDecimal(
            Payment payment
    ) {

        if (payment == null) {
            return ZERO;
        }

        BigDecimal value =
                payment.getPenaltyDecimal();

        return safe(value);
    }

    private BigDecimal getCycleInterestDueDecimal(
            Payment payment
    ) {

        if (payment == null) {
            return ZERO;
        }

        BigDecimal value =
                payment.getCycleInterestDueDecimal();

        return safe(value);
    }

    private BigDecimal getCycleInterestRemainingDecimal(
            Payment payment
    ) {

        if (payment == null) {
            return ZERO;
        }

        BigDecimal value =
                payment.getCycleInterestRemainingDecimal();

        return safe(value);
    }

    // ================================================================
    // LOAN DECIMAL GETTERS
    // ================================================================

    private BigDecimal getOutstandingBalanceDecimal(
            Loan loan
    ) {

        if (loan == null) {
            return ZERO;
        }

        BigDecimal value =
                loan.getOutstandingBalanceDecimal();

        return safe(value);
    }

    private BigDecimal getTotalPaidDecimal(
            Loan loan
    ) {

        if (loan == null) {
            return ZERO;
        }

        BigDecimal value =
                loan.getTotalPaidDecimal();

        return safe(value);
    }

    private BigDecimal getNextInstallmentAmountDecimal(
            Loan loan
    ) {

        if (loan == null) {
            return ZERO;
        }

        BigDecimal value =
                loan.getNextInstallmentAmountDecimal();

        return safe(value);
    }

    private BigDecimal getInterestRateDecimal(
            Loan loan
    ) {

        if (loan == null) {
            return ZERO;
        }

        BigDecimal value =
                loan.getInterestRateDecimal();

        return safe(value);
    }

    // ================================================================
    // LOAN DECIMAL SETTERS
    // ================================================================

    private void setOutstandingBalanceDecimal(
            Loan loan,
            BigDecimal value
    ) {

        loan.setOutstandingBalance(
                safe(value)
        );
    }

    private void setTotalPaidDecimal(
            Loan loan,
            BigDecimal value
    ) {

        loan.setTotalPaid(
                safe(value)
        );
    }

    private void setNextInstallmentAmountDecimal(
            Loan loan,
            BigDecimal value
    ) {

        loan.setNextInstallmentAmount(
                safe(value)
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