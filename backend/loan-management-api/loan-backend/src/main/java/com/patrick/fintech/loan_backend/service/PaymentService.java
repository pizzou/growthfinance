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
            new BigDecimal("30");

    private static final BigDecimal TWELVE =
            new BigDecimal("12");

    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100");

    private static final BigDecimal DEFAULT_MONTHLY_PENALTY_RATE =
            new BigDecimal("0.02");

    private static final String BORROWER_REFUNDS_PAYABLE_ACCOUNT =
            "2100";

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
        // LOAD LOAN WITH ROW LOCK
        // ============================================================

        Loan loan =
                loanRepo.findByIdForUpdate(loanId)
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Loan not found: " + loanId
                                )
                        );

        // ============================================================
        // ORGANIZATION ACCESS
        // ============================================================

        validateOrganizationAccess(
                loan,
                recordedBy
        );

        if (loan.getOrganization() == null
                || loan.getOrganization().getId() == null) {

            throw new IllegalStateException(
                    "Loan organization is required."
            );
        }

        Long organizationId =
                loan.getOrganization().getId();

        // ============================================================
        // IDEMPOTENCY
        // ============================================================

        if (normalizedTxnId != null) {

            Optional<Payment> existingPayment =
                    paymentRepo
                            .findByOrganization_IdAndTransactionId(
                                    organizationId,
                                    normalizedTxnId
                            );

            if (existingPayment.isPresent()) {

                Payment existing =
                        existingPayment.get();

                if (existing.getLoan() != null
                        && existing.getLoan().getId() != null
                        && existing.getLoan()
                        .getId()
                        .equals(loanId)) {

                    log.info(
                            "Duplicate payment transaction detected. " +
                                    "transactionId={}, loanId={}, paymentId={}",
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

        if (loan.getStatus() != LoanStatus.ACTIVE
                && loan.getStatus() != LoanStatus.OVERDUE) {

            throw new IllegalStateException(
                    "Loan is not active. Current status: "
                            + loan.getStatus()
            );
        }

        LocalDate today =
                LocalDate.now();

        LocalDateTime now =
                LocalDateTime.now();

        // ============================================================
        // LOAD PAYMENT HISTORY
        // ============================================================

        List<Payment> loanPayments =
                paymentRepo.findByLoanId(loanId);

        if (loanPayments == null) {
            loanPayments = List.of();
        }

        // ============================================================
        // FIRST ACTUAL PAYMENT
        // ============================================================

        boolean firstInterestCalculation =
                isFirstInterestCalculation(
                        loanPayments
                );

        log.info(
                "Interest calculation state. " +
                        "loanId={}, firstInterestCalculation={}, " +
                        "paymentHistoryCount={}, disbursedAt={}, now={}",
                loanId,
                firstInterestCalculation,
                loanPayments.size(),
                loan.getDisbursedAt(),
                now
        );

        // ============================================================
        // FIND OPEN CURRENT CYCLE
        // ============================================================

        Optional<Payment> existingCurrentCycle =
                loanPayments.stream()
                        .filter(p -> p != null)
                        .filter(
                                p -> !Boolean.TRUE.equals(
                                        p.getPaid()
                                )
                        )
                        .filter(
                                p -> safe(
                                        p.getAmountPaidDecimal()
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
        // FIND OLDEST UNPAID INSTALLMENT
        // ============================================================

        Optional<Payment> unpaidInstallment =
                loanPayments.stream()
                        .filter(p -> p != null)
                        .filter(
                                p -> !Boolean.TRUE.equals(
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
        // SELECT PAYMENT RECORD
        // ============================================================

        if (existingCurrentCycle.isPresent()) {

            installment =
                    existingCurrentCycle.get();

            log.info(
                    "Continuing existing payment cycle. " +
                            "loanId={}, installment={}, paymentId={}",
                    loanId,
                    installment.getInstallmentNumber(),
                    installment.getId()
            );

        } else if (unpaidInstallment.isPresent()) {

            installment =
                    unpaidInstallment.get();

            log.info(
                    "Using unpaid scheduled installment. " +
                            "loanId={}, installment={}, paymentId={}",
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
                            .filter(p -> p != null)
                            .map(Payment::getInstallmentNumber)
                            .filter(n -> n != null)
                            .max(Integer::compareTo)
                            .orElse(0)
                            + 1;

            installment =
                    Payment.builder()
                            .loan(loan)
                            .organization(loan.getOrganization())
                            .installmentNumber(nextNumber)
                            .dueDate(dueDate)
                            .amount(
                                    safe(
                                            loan.getNextInstallmentAmountDecimal()
                                    )
                            )
                            .amountPaid(BigDecimal.ZERO)
                            .principalComponent(BigDecimal.ZERO)
                            .interestComponent(BigDecimal.ZERO)
                            .penalty(BigDecimal.ZERO)
                            .cycleInterestDue(BigDecimal.ZERO)
                            .cycleInterestRemaining(BigDecimal.ZERO)
                            .interestCalculationDate(null)
                            .paid(false)
                            .status(Payment.PaymentStatus.PENDING)
                            .build();

            log.info(
                    "Creating new payment cycle. " +
                            "loanId={}, installment={}",
                    loanId,
                    nextNumber
            );
        }

        // ============================================================
        // CYCLE DUE DATE
        // ============================================================

        LocalDate cycleDueDate =
                installment.getDueDate() != null
                        ? installment.getDueDate()
                        : (
                        loan.getNextDueDate() != null
                                ? loan.getNextDueDate()
                                : today
                );

        // ============================================================
        // LATE DAYS
        // ============================================================

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
                amountPaidSoFar.compareTo(BigDecimal.ZERO) > 0
                        ? roundMoney(
                        safe(
                                installment.getInterestComponentDecimal()
                        )
                )
                        : ZERO;

        BigDecimal existingCycleInterestDue =
                roundMoney(
                        safe(
                                installment.getCycleInterestDueDecimal()
                        )
                ).max(ZERO);

        BigDecimal existingCycleInterestRemaining =
                roundMoney(
                        safe(
                                installment.getCycleInterestRemainingDecimal()
                        )
                ).max(ZERO);

        BigDecimal penaltyAssessed =
                roundMoney(
                        safe(
                                installment.getPenaltyDecimal()
                        )
                ).max(ZERO);

        BigDecimal penaltyAlreadyPaid =
                ZERO;

        BigDecimal penaltyRemainingBeforePayment =
                penaltyAssessed
                        .subtract(
                                penaltyAlreadyPaid
                        )
                        .max(ZERO);

        // ============================================================
        // CURRENT PRINCIPAL
        // ============================================================

        BigDecimal currentBalance =
                roundMoney(
                        safe(
                                loan.getOutstandingBalanceDecimal()
                        )
                ).max(ZERO);

        if (currentBalance.compareTo(BigDecimal.ZERO) <= 0) {

            if (existingCycleInterestRemaining.compareTo(ZERO) <= 0
                    && penaltyRemainingBeforePayment.compareTo(ZERO) <= 0) {

                throw new IllegalStateException(
                        "Loan has no outstanding principal balance."
                );
            }
        }

        // ============================================================
        // INTEREST CYCLE STATE
        // ============================================================

        /*
         * An established cycle must NEVER calculate interest again.
         *
         * This is what prevents:
         *
         * Payment 1 -> interest charged
         * Payment 2 -> same cycle -> interest charged again
         *
         * Once cycleInterestDue exists, that cycle's interest
         * obligation is fixed.
         */
        boolean cycleInterestAlreadyEstablished =
                existingCycleInterestDue.compareTo(ZERO) > 0;

        // ============================================================
        // INTEREST START
        // ============================================================

        LocalDateTime interestStartDateTime =
                determineInterestStartDateTime(
                        installment,
                        loan,
                        loanPayments,
                        now,
                        firstInterestCalculation
                );

        if (interestStartDateTime == null) {

            interestStartDateTime =
                    loan.getDisbursedAt() != null
                            ? loan.getDisbursedAt()
                            : now;
        }

        if (interestStartDateTime.isAfter(now)) {

            interestStartDateTime =
                    now;
        }

        // ============================================================
        // INTEREST DAYS
        // ============================================================

        long elapsedDays;

        if (cycleInterestAlreadyEstablished) {

            /*
             * IMPORTANT:
             *
             * This is an already-established cycle.
             *
             * No new interest is calculated for another payment
             * inside the same cycle.
             */
            elapsedDays =
                    0L;

            log.info(
                    "Cycle interest already established. " +
                            "No additional interest accrued. " +
                            "loanId={}, installment={}, " +
                            "cycleInterestDue={}, " +
                            "cycleInterestRemaining={}",
                    loanId,
                    installment.getInstallmentNumber(),
                    existingCycleInterestDue,
                    existingCycleInterestRemaining
            );

        } else {

            /*
             * NEW INTEREST CYCLE:
             *
             * Daily-basis calculation.
             *
             * Minimum = 1 day.
             *
             * Therefore:
             *
             * 10 Aug 15:54 -> 10 Aug 16:15 = 1 day
             *
             * 10 Aug -> 11 Aug = 1 day
             *
             * 10 Aug -> 12 Aug = 2 days
             *
             * This is the actual fix for the previous:
             *
             * interestDays=0
             */
            elapsedDays =
                    calculateActualInterestDays(
                            interestStartDateTime,
                            now,
                            installment,
                            loan,
                            firstInterestCalculation
                    );
        }

        // ============================================================
        // DAILY INTEREST RATE
        // ============================================================

        BigDecimal dailyRate =
                calculateDailyRate(loan);

        // ============================================================
        // NEW INTEREST
        // ============================================================

        BigDecimal newlyAccruedInterest;

        if (cycleInterestAlreadyEstablished) {

            newlyAccruedInterest =
                    ZERO;

        } else {

            newlyAccruedInterest =
                    calculateNewInterest(
                            currentBalance,
                            dailyRate,
                            elapsedDays
                    );
        }

        // ============================================================
        // TOTAL CYCLE INTEREST
        // ============================================================

        BigDecimal totalCycleInterestDue;

        if (cycleInterestAlreadyEstablished) {

            totalCycleInterestDue =
                    existingCycleInterestDue;

        } else {

            totalCycleInterestDue =
                    roundMoney(
                            existingCycleInterestDue
                                    .add(
                                            newlyAccruedInterest
                                    )
                    );
        }

        // ============================================================
        // INTEREST REMAINING
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

        BigDecimal remainingInterestBeforePayment =
                calculatedRemainingInterest.max(
                        existingCycleInterestRemaining
                );

        if (remainingInterestBeforePayment.compareTo(
                totalCycleInterestDue
        ) > 0) {

            remainingInterestBeforePayment =
                    totalCycleInterestDue;
        }

        remainingInterestBeforePayment =
                roundMoney(
                        remainingInterestBeforePayment
                );

        // ============================================================
        // PENALTY
        // ============================================================

        BigDecimal monthlyPenaltyRate =
                DEFAULT_MONTHLY_PENALTY_RATE;

        BigDecimal dailyPenaltyRate =
                monthlyPenaltyRate.divide(
                        THIRTY,
                        16,
                        RoundingMode.HALF_UP
                );

        BigDecimal calculatedTotalPenalty =
                BigDecimal.ZERO;

        if (daysLate > 0
                && currentBalance.compareTo(ZERO) > 0) {

            calculatedTotalPenalty =
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

        BigDecimal totalPenalty =
                calculatedTotalPenalty.max(
                        penaltyAssessed
                );

        totalPenalty =
                roundMoney(totalPenalty);

        BigDecimal newPenalty =
                roundMoney(
                        totalPenalty
                                .subtract(
                                        penaltyAssessed
                                )
                                .max(
                                        BigDecimal.ZERO
                                )
                );

        penaltyRemainingBeforePayment =
                roundMoney(
                        totalPenalty
                                .subtract(
                                        penaltyAlreadyPaid
                                )
                                .max(
                                        BigDecimal.ZERO
                                )
                );

        // ============================================================
        // PAYMENT ALLOCATION
        // ============================================================

        BigDecimal paymentRemaining =
                amount;

        // ============================================================
        // 1. PENALTY
        // ============================================================

        BigDecimal penaltyPaidThisPayment =
                roundMoney(
                        paymentRemaining.min(
                                penaltyRemainingBeforePayment
                        )
                );

        paymentRemaining =
                roundMoney(
                        paymentRemaining
                                .subtract(
                                        penaltyPaidThisPayment
                                )
                                .max(
                                        BigDecimal.ZERO
                                )
                );

        // ============================================================
        // 2. INTEREST
        // ============================================================

        BigDecimal interestPaidThisPayment =
                roundMoney(
                        paymentRemaining.min(
                                remainingInterestBeforePayment
                        )
                );

        paymentRemaining =
                roundMoney(
                        paymentRemaining
                                .subtract(
                                        interestPaidThisPayment
                                )
                                .max(
                                        BigDecimal.ZERO
                                )
                );

        // ============================================================
        // 3. PRINCIPAL
        // ============================================================

        BigDecimal principalPaidThisPayment =
                roundMoney(
                        paymentRemaining.min(
                                currentBalance
                        )
                );

        paymentRemaining =
                roundMoney(
                        paymentRemaining
                                .subtract(
                                        principalPaidThisPayment
                                )
                                .max(
                                        BigDecimal.ZERO
                                )
                );

        // ============================================================
        // 4. OVERPAYMENT
        // ============================================================

        BigDecimal overpayment =
                roundMoney(
                        paymentRemaining.max(
                                BigDecimal.ZERO
                        )
                );

        // ============================================================
        // NEW BALANCE
        // ============================================================

        BigDecimal newBalance =
                roundMoney(
                        currentBalance
                                .subtract(
                                        principalPaidThisPayment
                                )
                                .max(
                                        BigDecimal.ZERO
                                )
                );

        // ============================================================
        // TOTAL COMPONENTS
        // ============================================================

        BigDecimal existingPrincipalPaid =
                amountPaidSoFar.compareTo(BigDecimal.ZERO) > 0
                        ? roundMoney(
                        safe(
                                installment
                                        .getPrincipalComponentDecimal()
                        )
                )
                        : ZERO;

        BigDecimal totalPrincipalPaid =
                roundMoney(
                        existingPrincipalPaid
                                .add(
                                        principalPaidThisPayment
                                )
                );

        BigDecimal totalInterestPaid =
                roundMoney(
                        interestAlreadyPaid
                                .add(
                                        interestPaidThisPayment
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

        boolean penaltyCovered =
                penaltyRemainingBeforePayment
                        .subtract(
                                penaltyPaidThisPayment
                        )
                        .compareTo(
                                ONE_CENT
                        ) <= 0;

        boolean interestCovered =
                remainingInterestAfterPayment
                        .compareTo(
                                ONE_CENT
                        ) <= 0;

        boolean fullyPaidOff =
                newBalance.compareTo(
                        ONE_CENT
                ) <= 0;

        // ============================================================
        // SCHEDULED INSTALLMENT
        // ============================================================

        boolean scheduledAmountCovered =
                isScheduledInstallmentCovered(
                        installment,
                        amountPaidSoFar,
                        amount
                );

        // ============================================================
        // OVERPAYMENT VALIDATION
        // ============================================================

        if (overpayment.compareTo(ZERO) > 0
                && !fullyPaidOff) {

            throw new IllegalStateException(
                    "Invalid payment allocation: overpayment exists while " +
                            "principal remains outstanding."
            );
        }

        // ============================================================
        // CYCLE COMPLETION
        // ============================================================

        /*
         * PRINCIPAL ALONE IS NOT ENOUGH.
         *
         * Interest must be fully covered.
         * Penalty must be fully covered.
         */
        boolean cycleCompleted =
                fullyPaidOff
                        && penaltyCovered
                        && interestCovered;

        if (!fullyPaidOff) {

            cycleCompleted =
                    scheduledAmountCovered
                            && penaltyCovered
                            && interestCovered;
        }

        // ============================================================
        // UPDATE PAYMENT RECORD
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
                isLate || installment.isLate()
        );

        int existingDaysLate =
                installment.getDaysLate() != null
                        ? installment.getDaysLate()
                        : 0;

        installment.setDaysLate(
                Math.max(
                        existingDaysLate,
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

        // ============================================================
        // INTEREST CALCULATION DATE
        // ============================================================

        /*
         * Once this cycle has generated interest, persist the
         * calculation timestamp.
         *
         * A second payment in the same cycle must never move
         * this timestamp.
         */
        if (!cycleInterestAlreadyEstablished
                && totalCycleInterestDue.compareTo(ZERO) > 0) {

            installment.setInterestCalculationDate(
                    now
            );
        }

        if (installment.getInterestCalculationDate() == null
                && totalCycleInterestDue.compareTo(ZERO) > 0) {

            installment.setInterestCalculationDate(
                    now
            );
        }

        installment.setPaid(
                cycleCompleted
        );

        installment.setStatus(
                cycleCompleted
                        ? Payment.PaymentStatus.COMPLETED
                        : Payment.PaymentStatus.PARTIALLY_PAID
        );

        if (installment.getPaymentReference() == null
                || installment
                .getPaymentReference()
                .isBlank()) {

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
                                        organizationId,
                                        normalizedTxnId
                                );

                if (concurrentPayment.isPresent()) {

                    Payment existing =
                            concurrentPayment.get();

                    if (existing.getLoan() != null
                            && existing.getLoan().getId() != null
                            && existing.getLoan()
                            .getId()
                            .equals(loanId)) {

                        log.info(
                                "Concurrent duplicate payment detected. " +
                                        "transactionId={}, loanId={}, paymentId={}",
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
        // UPDATE LOAN TOTAL PAID
        // ============================================================

        BigDecimal oldTotalPaid =
                roundMoney(
                        safe(
                                loan.getTotalPaidDecimal()
                        )
                );

        BigDecimal newTotalPaid =
                roundMoney(
                        oldTotalPaid
                                .add(
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
        // LOAN FULLY PAID
        // ============================================================

        if (fullyPaidOff
                && interestCovered
                && penaltyCovered) {

            loan.setStatus(
                    LoanStatus.PAID
            );

            Long currentInstallmentId =
                    installment.getId();

            List<Payment> stillPending =
                    paymentRepo
                            .findByLoanId(loanId)
                            .stream()
                            .filter(p -> p != null)
                            .filter(
                                    p -> !Boolean.TRUE.equals(
                                            p.getPaid()
                                    )
                            )
                            .filter(
                                    p ->
                                            p.getId() == null
                                                    || !p.getId()
                                                    .equals(
                                                            currentInstallmentId
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

            /*
             * CRITICAL:
             *
             * Principal = 0 is NOT sufficient to mark the loan PAID.
             *
             * If interest remains, loan stays ACTIVE/OVERDUE.
             */
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
        // ACCOUNTING
        // ============================================================

        accountingService.postPaymentReceived(
                installment,
                amount,
                principalPaidThisPayment,
                interestPaidThisPayment,
                penaltyPaidThisPayment,
                overpayment
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
                        + " — first interest calculation: "
                        + firstInterestCalculation
                        + ", cycle interest already established: "
                        + cycleInterestAlreadyEstablished
                        + ", interest start: "
                        + interestStartDateTime
                        + ", payment time: "
                        + now
                        + ", interest days: "
                        + elapsedDays
                        + ", daily interest rate: "
                        + dailyRate
                        + ", newly accrued interest: "
                        + newlyAccruedInterest
                        + ", total cycle interest: "
                        + totalCycleInterestDue
                        + ", interest paid: "
                        + interestPaidThisPayment
                        + ", principal paid: "
                        + principalPaidThisPayment
                        + ", penalty days: "
                        + daysLate
                        + ", penalty paid: "
                        + penaltyPaidThisPayment
                        + ", total penalty: "
                        + totalPenalty
                        + ", remaining interest: "
                        + remainingInterestAfterPayment
                        + ", outstanding principal: "
                        + newBalance
                        + ", overpayment: "
                        + overpayment
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
                    "Payment email notification failed for loanId={}",
                    loan.getId(),
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
                    "Payment SMS notification failed for loanId={}",
                    loan.getId(),
                    e
            );
        }

        // ============================================================
        // LOAN OFFICER NOTIFICATION
        // ============================================================

        if (loan.getLoanOfficer() != null
                && (
                recordedBy == null
                        || loan.getLoanOfficer().getId() == null
                        || recordedBy.getId() == null
                        || !loan.getLoanOfficer()
                        .getId()
                        .equals(
                                recordedBy.getId()
                        )
        )) {

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
                                        : " automatically"
                        )
                                + (
                                overpayment.compareTo(ZERO) > 0
                                        ? ". Borrower refund payable: "
                                        + loan.getCurrency()
                                        + " "
                                        + overpayment
                                        : "."
                        ),
                        "success",
                        "/dashboard/loans/"
                                + loan.getId()
                );

            } catch (Exception e) {

                log.warn(
                        "In-app payment notification failed for loanId={}",
                        loan.getId(),
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
                    principalPaidThisPayment
            );

            paymentWebhook.put(
                    "interestPaid",
                    interestPaidThisPayment
            );

            paymentWebhook.put(
                    "penaltyPaid",
                    penaltyPaidThisPayment
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
                    "overpayment",
                    overpayment
            );

            paymentWebhook.put(
                    "borrowerRefundPayable",
                    overpayment
            );

            paymentWebhook.put(
                    "borrowerRefundPayableAccount",
                    BORROWER_REFUNDS_PAYABLE_ACCOUNT
            );

            paymentWebhook.put(
                    "interestDays",
                    elapsedDays
            );

            paymentWebhook.put(
                    "firstInterestCalculation",
                    firstInterestCalculation
            );

            paymentWebhook.put(
                    "cycleInterestAlreadyEstablished",
                    cycleInterestAlreadyEstablished
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

            webhookService.dispatch(
                    loan.getOrganization(),
                    "PAYMENT_MADE",
                    paymentWebhook
            );

        } catch (Exception e) {

            log.error(
                    "[PAYMENT WEBHOOK] Failed to dispatch PAYMENT_MADE. " +
                            "loanId={}, paymentId={}",
                    loan.getId(),
                    installment.getId(),
                    e
            );
        }

        // ============================================================
        // FINAL LOG
        // ============================================================

        log.info(
                "Payment successfully recorded. " +
                        "loanId={}, paymentId={}, amount={}, " +
                        "firstInterestCalculation={}, " +
                        "cycleInterestAlreadyEstablished={}, " +
                        "interestStart={}, " +
                        "interestDays={}, interestPaid={}, " +
                        "principalPaid={}, penaltyPaid={}, " +
                        "penaltyDays={}, overpayment={}, " +
                        "outstandingBalance={}, cycleCompleted={}, " +
                        "loanStatus={}",
                loan.getId(),
                installment.getId(),
                amount,
                firstInterestCalculation,
                cycleInterestAlreadyEstablished,
                interestStartDateTime,
                elapsedDays,
                interestPaidThisPayment,
                principalPaidThisPayment,
                penaltyPaidThisPayment,
                daysLate,
                overpayment,
                newBalance,
                cycleCompleted,
                loan.getStatus()
        );

        return installment;
    }

    // ================================================================
    // FIRST INTEREST CALCULATION
    // ================================================================

    boolean isFirstInterestCalculation(
            List<Payment> payments
    ) {

        if (payments == null || payments.isEmpty()) {
            return true;
        }

        for (Payment payment : payments) {

            if (payment == null) {
                continue;
            }

            BigDecimal amountPaid =
                    safe(
                            payment.getAmountPaidDecimal()
                    );

            if (amountPaid.compareTo(
                    BigDecimal.ZERO
            ) > 0) {

                return false;
            }
        }

        return true;
    }

    // ================================================================
    // INSTALLMENT COMPLETION
    // ================================================================

    private boolean isScheduledInstallmentCovered(
            Payment installment,
            BigDecimal amountPaidSoFar,
            BigDecimal currentPayment
    ) {

        if (installment == null) {
            return false;
        }

        BigDecimal scheduledAmount =
                roundMoney(
                        safe(
                                installment.getAmountDecimal()
                        )
                );

        if (scheduledAmount.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            return false;
        }

        BigDecimal newPaidAmount =
                roundMoney(
                        safe(amountPaidSoFar)
                                .add(
                                        safe(currentPayment)
                                )
                );

        return newPaidAmount.compareTo(
                scheduledAmount
        ) >= 0;
    }

    // ================================================================
    // VALIDATE ORGANIZATION ACCESS
    // ================================================================

    private void validateOrganizationAccess(
            Loan loan,
            User recordedBy
    ) {

        if (loan == null) {

            throw new IllegalArgumentException(
                    "Loan is required"
            );
        }

        if (loan.getOrganization() == null
                || loan.getOrganization().getId() == null) {

            throw new IllegalStateException(
                    "Loan organization is required."
            );
        }

        if (recordedBy == null) {
            return;
        }

        if (recordedBy.getOrganization() == null
                || recordedBy.getOrganization().getId() == null) {

            throw new IllegalStateException(
                    "Recorded user's organization is required."
            );
        }

        Long loanOrganizationId =
                loan.getOrganization().getId();

        Long userOrganizationId =
                recordedBy.getOrganization().getId();

        if (!loanOrganizationId.equals(
                userOrganizationId
        )) {

            throw new IllegalStateException(
                    "Access denied."
            );
        }
    }

    // ================================================================
    // GET LOAN SCHEDULE
    // ================================================================

    @Transactional(readOnly = true)
    public List<Payment> getLoanSchedule(
            Long loanId,
            Long orgId
    ) {

        if (loanId == null) {

            throw new IllegalArgumentException(
                    "Loan ID is required"
            );
        }

        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }

        Loan loan =
                loanRepo.findById(loanId)
                        .orElseThrow(
                                () ->
                                        new RuntimeException(
                                                "Loan not found"
                                        )
                        );

        if (loan.getOrganization() == null
                || loan.getOrganization().getId() == null
                || !loan.getOrganization()
                .getId()
                .equals(orgId)) {

            throw new IllegalStateException(
                    "Access denied."
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

        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }

        LocalDate today =
                LocalDate.now();

        List<Payment> overduePayments =
                paymentRepo
                        .findByOrganization_IdAndPaidFalseAndDueDateBefore(
                                orgId,
                                today
                        );

        if (overduePayments == null
                || overduePayments.isEmpty()) {

            return;
        }

        for (Payment payment : overduePayments) {

            if (payment == null) {
                continue;
            }

            Loan loan =
                    payment.getLoan();

            if (loan == null) {
                continue;
            }

            if (loan.getOrganization() == null
                    || loan.getOrganization().getId() == null
                    || !orgId.equals(
                    loan.getOrganization().getId()
            )) {

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
                                        today
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

                payment.setLate(
                        true
                );

                payment.setDaysLate(
                        Math.max(
                                payment.getDaysLate() != null
                                        ? payment.getDaysLate()
                                        : 0,
                                days
                        )
                );

                paymentRepo.save(
                        payment
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
            LocalDateTime now,
            boolean firstInterestCalculation
    ) {

        /*
         * FIRST ACTUAL BORROWER PAYMENT:
         *
         * Interest starts from the actual disbursement timestamp.
         */
        if (firstInterestCalculation) {

            if (loan != null
                    && loan.getDisbursedAt() != null) {

                return loan.getDisbursedAt();
            }

            if (loan != null
                    && loan.getStartDate() != null) {

                return loan.getStartDate()
                        .atStartOfDay();
            }

            return now;
        }

        /*
         * EXISTING PAYMENT CYCLE:
         *
         * Use its established interest timestamp.
         */
        if (installment != null
                && installment.getInterestCalculationDate() != null) {

            return installment.getInterestCalculationDate();
        }

        /*
         * NEW PAYMENT CYCLE:
         *
         * Start from the latest established interest timestamp.
         */
        LocalDateTime latestTimestamp =
                findLatestInterestCalculationTimestamp(
                        loanPayments,
                        loan
                );

        if (latestTimestamp != null) {

            return latestTimestamp;
        }

        if (loan != null
                && loan.getDisbursedAt() != null) {

            return loan.getDisbursedAt();
        }

        if (loan != null
                && loan.getStartDate() != null) {

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

        if (payments != null
                && !payments.isEmpty()) {

            Optional<LocalDateTime> latest =
                    payments.stream()
                            .filter(p -> p != null)
                            .filter(
                                    p ->
                                            safe(
                                                    p.getAmountPaidDecimal()
                                            ).compareTo(
                                                    BigDecimal.ZERO
                                            ) > 0
                            )
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

        if (loan != null
                && loan.getDisbursedAt() != null) {

            return loan.getDisbursedAt();
        }

        return null;
    }

    // ================================================================
    // ACTUAL DAILY INTEREST DAYS
    // ================================================================

    private long calculateActualInterestDays(
            LocalDateTime interestStart,
            LocalDateTime now,
            Payment installment,
            Loan loan,
            boolean firstInterestCalculation
    ) {

        if (interestStart == null
                || now == null) {

            /*
             * A new interest cycle must never silently become
             * a zero-day cycle.
             */
            return 1L;
        }

        if (interestStart.isAfter(now)) {

            log.warn(
                    "Interest start is after payment time. " +
                            "Using minimum one interest day. " +
                            "loanId={}, interestStart={}, now={}",
                    loan != null
                            ? loan.getId()
                            : null,
                    interestStart,
                    now
            );

            return 1L;
        }

        /*
         * DAILY-BASIS RULE
         *
         * Interest is based on calendar days.
         *
         * Same calendar day:
         *
         * 10 Aug 15:54
         * ->
         * 10 Aug 16:15
         *
         * = 1 interest day.
         *
         * This is intentional. A newly established interest
         * cycle can NEVER have zero interest days.
         */
        long calendarDays =
                ChronoUnit.DAYS.between(
                        interestStart.toLocalDate(),
                        now.toLocalDate()
                );

        long effectiveDays =
                Math.max(
                        1L,
                        calendarDays
                );

        log.info(
                "DAILY INTEREST CALCULATION. " +
                        "loanId={}, installment={}, " +
                        "firstInterestCalculation={}, " +
                        "interestStart={}, paymentTime={}, " +
                        "calendarDays={}, effectiveInterestDays={}",
                loan != null
                        ? loan.getId()
                        : null,
                installment != null
                        ? installment.getInstallmentNumber()
                        : null,
                firstInterestCalculation,
                interestStart,
                now,
                calendarDays,
                effectiveDays
        );

        return effectiveDays;
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

        if (rate.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            log.warn(
                    "Loan {} has zero or missing interest rate. " +
                            "interestRate={}",
                    loan.getId(),
                    rate
            );

            return BigDecimal.ZERO;
        }

        String rateType =
                loan.getInterestRateType() != null
                        ? loan.getInterestRateType().trim()
                        : null;

        if (rateType == null
                || rateType.isBlank()) {

            throw new IllegalStateException(
                    "Interest rate type is required for loan "
                            + loan.getId()
            );
        }

        // ============================================================
        // MONTHLY -> DAILY
        // ============================================================

        /*
         * Monthly rate / 100 / 30
         *
         * Example:
         *
         * 10% monthly
         *
         * 10 / 100 / 30
         *
         * = 0.003333333333...
         */

        if ("MONTHLY".equalsIgnoreCase(
                rateType
        )) {

            BigDecimal dailyRate =
                    rate
                            .divide(
                                    ONE_HUNDRED,
                                    16,
                                    RoundingMode.HALF_UP
                            )
                            .divide(
                                    THIRTY,
                                    16,
                                    RoundingMode.HALF_UP
                            );

            log.debug(
                    "Monthly interest converted to daily rate. " +
                            "loanId={}, monthlyRate={}, dailyRate={}",
                    loan.getId(),
                    rate,
                    dailyRate
            );

            return dailyRate;
        }

        // ============================================================
        // ANNUAL -> DAILY
        // ============================================================

        /*
         * Annual rate / 100 / 12 / 30
         */

        if ("ANNUAL".equalsIgnoreCase(
                rateType
        )) {

            return rate
                    .divide(
                            ONE_HUNDRED,
                            16,
                            RoundingMode.HALF_UP
                    )
                    .divide(
                            TWELVE,
                            16,
                            RoundingMode.HALF_UP
                    )
                    .divide(
                            THIRTY,
                            16,
                            RoundingMode.HALF_UP
                    );
        }

        throw new IllegalStateException(
                "Unsupported interest rate type '"
                        + rateType
                        + "' for loan "
                        + loan.getId()
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

        if (currentBalance == null
                || currentBalance.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            return ZERO;
        }

        if (dailyRate == null
                || dailyRate.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            return ZERO;
        }

        /*
         * SAFETY:
         *
         * A newly established cycle must never produce zero
         * interest days.
         */
        long effectiveDays =
                Math.max(
                        1L,
                        elapsedDays
                );

        BigDecimal interest =
                currentBalance
                        .multiply(
                                dailyRate
                        )
                        .multiply(
                                BigDecimal.valueOf(
                                        effectiveDays
                                )
                        );

        BigDecimal rounded =
                roundMoney(
                        interest
                );

        log.info(
                "Calculated DAILY interest. " +
                        "balance={}, dailyRate={}, days={}, interest={}",
                currentBalance,
                dailyRate,
                effectiveDays,
                rounded
        );

        return rounded;
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

        String loanReference =
                loan != null
                        && loan.getReferenceNumber() != null
                        && !loan.getReferenceNumber().isBlank()
                        ? loan.getReferenceNumber()
                        : String.valueOf(
                        loan != null
                                ? loan.getId()
                                : "UNKNOWN"
                );

        return "PAY-"
                + loanReference
                + "-"
                + System.currentTimeMillis();
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