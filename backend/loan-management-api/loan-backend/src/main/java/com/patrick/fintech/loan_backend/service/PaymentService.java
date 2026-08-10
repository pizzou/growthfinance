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

    /**
     * Accounting liability account for borrower overpayments.
     *
     * 2100 - Borrower Refunds Payable
     */
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
            throw new IllegalArgumentException(
                    "Loan ID is required"
            );
        }

        if (amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }

        amount = roundMoney(amount);

        String normalizedTxnId =
                normalizeTransactionId(txnId);

        // ============================================================
        // LOAD LOAN
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
        // DETERMINE WHETHER THIS IS THE FIRST ACTUAL PAYMENT
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

        /*
         * interestComponent has two meanings across the payment lifecycle:
         *
         *  - before the first real payment it contains the SCHEDULED
         *    installment interest generated by LoanService;
         *  - after a real payment it contains the CUMULATIVE ACTUAL
         *    interest paid for that installment.
         *
         * Never treat the scheduled amount as already-paid interest.
         */
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
                );

        BigDecimal existingCycleInterestRemaining =
                roundMoney(
                        safe(
                                installment.getCycleInterestRemainingDecimal()
                        )
                );

        BigDecimal penaltyAssessed =
                roundMoney(
                        safe(
                                installment.getPenaltyDecimal()
                        )
                );

        /*
         * Current Payment model does not contain penaltyPaid /
         * penaltyRemaining.
         *
         * Therefore the already-assessed penalty is preserved.
         */
        BigDecimal penaltyAlreadyPaid =
                BigDecimal.ZERO;

        BigDecimal penaltyRemainingBeforePayment =
                penaltyAssessed
                        .subtract(
                                penaltyAlreadyPaid
                        )
                        .max(ZERO);

        interestAlreadyPaid =
                interestAlreadyPaid.max(ZERO);

        existingCycleInterestDue =
                existingCycleInterestDue.max(ZERO);

        existingCycleInterestRemaining =
                existingCycleInterestRemaining.max(ZERO);

        penaltyAssessed =
                penaltyAssessed.max(ZERO);

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

        if (currentBalance.compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalStateException(
                    "Loan has no outstanding principal balance."
            );
        }

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

        long elapsedDays =
                calculateActualInterestDays(
                        interestStartDateTime,
                        now,
                        installment,
                        loan,
                        firstInterestCalculation
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

        /*
         * Never allow persisted remaining interest to disappear.
         */
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

        if (daysLate > 0) {

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

        /*
         * Never reduce an already assessed penalty.
         */
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

        /*
         * Like interestComponent, principalComponent initially contains
         * the SCHEDULED principal portion. It becomes cumulative actual
         * principal paid only after the first real payment is recorded.
         */
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

        if (overpayment.compareTo(ZERO) > 0
                && !fullyPaidOff) {

            throw new IllegalStateException(
                    "Invalid payment allocation: overpayment exists while " +
                            "principal remains outstanding."
            );
        }

        boolean cycleCompleted =
                fullyPaidOff
                        || (
                        scheduledAmountCovered
                                && penaltyCovered
                                && interestCovered
                );

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

        /*
         * CRITICAL:
         *
         * The timestamp is updated ONLY AFTER interest has been
         * calculated for this payment.
         *
         * Therefore:
         *
         * First payment:
         * 10 Aug 10:01 -> 1 day
         *
         * Second payment:
         * 10 Aug 10:05 -> 0 days
         *
         * Third payment:
         * 11 Aug -> 1 day
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

                /*
                 * Partial payment keeps the same installment open.
                 */
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

    /**
     * Determines whether this is the first actual borrower payment
     * for interest purposes.
     *
     * This deliberately does NOT depend on interestCalculationDate.
     *
     * Why?
     *
     * A scheduled Payment record may already have:
     *
     *     interestCalculationDate
     *
     * populated before the borrower makes the first payment.
     *
     * That must NOT prevent the minimum-one-day first-interest rule.
     */
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

            /*
             * IMPORTANT: principalComponent and interestComponent are
             * scheduled installment values. They are populated when
             * the repayment schedule is generated, before the borrower
             * has paid anything. They MUST NOT be used to decide whether
             * the first real payment has happened.
             */
            BigDecimal amountPaid = safe(payment.getAmountPaidDecimal());
            if (amountPaid.compareTo(BigDecimal.ZERO) > 0) {
                return false;
            }

            /*
             * A positive cycle interest balance means an actual interest
             * calculation has already been performed for this loan. In
             * that case the next payment continues from that calculation
             * point instead of receiving the first-payment minimum day.
             */
            BigDecimal cycleInterestDue =
                    safe(payment.getCycleInterestDueDecimal());
            BigDecimal cycleInterestRemaining =
                    safe(payment.getCycleInterestRemainingDecimal());

            if (cycleInterestDue.compareTo(BigDecimal.ZERO) > 0
                    || cycleInterestRemaining.compareTo(BigDecimal.ZERO) > 0) {
                return false;
            }
        }

        /*
         * The remaining records are unpaid schedule placeholders only.
         * Therefore the next real borrower payment is the first
         * interest-bearing payment.
         */
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
                loanRepo.findById(
                                loanId
                        )
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
         * FIRST EVER INTEREST CALCULATION
         *
         * Always begin at the actual disbursement timestamp.
         *
         * This is the most important correction.
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
         * Existing installment:
         *
         * Continue from the last actual interest calculation.
         */
        if (installment != null
                && installment.getInterestCalculationDate() != null) {

            return installment.getInterestCalculationDate();
        }

        /*
         * Otherwise use the latest interest calculation timestamp
         * anywhere on the loan.
         */
        LocalDateTime latestTimestamp =
                findLatestInterestCalculationTimestamp(
                        loanPayments,
                        loan
                );

        if (latestTimestamp != null) {

            return latestTimestamp;
        }

        /*
         * Final fallback to disbursement.
         */
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
    // ACTUAL INTEREST DAYS
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

            return 0L;
        }

        if (!interestStart.isBefore(now)) {

            /*
             * Even if the timestamps are extremely close or equal,
             * the first actual payment still receives the minimum
             * one-day interest.
             */
            if (firstInterestCalculation) {

                log.info(
                        "Applying minimum one-day interest despite zero elapsed " +
                                "timestamp difference. loanId={}, interestStart={}, now={}",
                        loan != null
                                ? loan.getId()
                                : null,
                        interestStart,
                        now
                );

                return 1L;
            }

            return 0L;
        }

        // ============================================================
        // FIRST ACTUAL PAYMENT
        // ============================================================

        if (firstInterestCalculation) {

            /*
             * Minimum one calendar day.
             *
             * Examples:
             *
             * 10:00 -> 10:01 = 1
             * 10:00 -> 10:30 = 1
             * 10:00 -> 23:59 = 1
             * 10:00 -> next day = 1
             */
            long calendarDays =
                    ChronoUnit.DAYS.between(
                            interestStart.toLocalDate(),
                            now.toLocalDate()
                    );

            long result =
                    Math.max(
                            1L,
                            calendarDays
                    );

            log.info(
                    "FIRST INTEREST CALCULATION. " +
                            "loanId={}, disbursement={}, paymentTime={}, " +
                            "calendarDays={}, chargedInterestDays={}",
                    loan != null
                            ? loan.getId()
                            : null,
                    interestStart,
                    now,
                    calendarDays,
                    result
            );

            return result;
        }

        // ============================================================
        // SUBSEQUENT PAYMENTS
        // ============================================================

        /*
         * After the first interest calculation, use calendar days
         * from the last actual interest calculation timestamp.
         *
         * Same calendar day:
         *
         * 10 Aug 10:01 -> 10 Aug 10:05 = 0
         *
         * Next calendar day:
         *
         * 10 Aug 10:01 -> 11 Aug 10:00 = 1
         *
         * Two days later:
         *
         * 10 Aug -> 12 Aug = 2
         */
        long calendarDays =
                ChronoUnit.DAYS.between(
                        interestStart.toLocalDate(),
                        now.toLocalDate()
                );

        long result =
                Math.max(
                        0L,
                        calendarDays
                );

        log.info(
                "SUBSEQUENT INTEREST CALCULATION. " +
                        "loanId={}, interestStart={}, now={}, " +
                        "calendarDays={}, chargedInterestDays={}",
                loan != null
                        ? loan.getId()
                        : null,
                interestStart,
                now,
                calendarDays,
                result
        );

        return result;
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
        // MONTHLY
        // ============================================================

        /*
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

            return rate
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
        }

        // ============================================================
        // ANNUAL
        // ============================================================

        /*
         * Example:
         *
         * 12% annual
         *
         * 12 / 100 / 12 / 30
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

        if (elapsedDays <= 0) {

            return ZERO;
        }

        BigDecimal interest =
                currentBalance
                        .multiply(
                                dailyRate
                        )
                        .multiply(
                                BigDecimal.valueOf(
                                        elapsedDays
                                )
                        );

        BigDecimal rounded =
                roundMoney(
                        interest
                );

        log.info(
                "Calculated interest. " +
                        "balance={}, dailyRate={}, days={}, interest={}",
                currentBalance,
                dailyRate,
                elapsedDays,
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