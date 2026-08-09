package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.PaymentTransaction;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.AuditLogRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.repository.PaymentTransactionRepository;
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
    private final PaymentTransactionRepository paymentTransactionRepo;
    private final LoanRepository loanRepo;
    private final AuditLogRepository auditRepo;
    private final AuditService auditService;
    private final UserRepository userRepo;
    private final NotificationService notifService;
    private final MailService mailService;
    private final SmsService smsService;
    private final WebhookService webhookService;
    private final AccountingService accountingService;
    private final FinancialCalculationService financialCalculationService;

    private static final double MONEY_EPSILON = 0.01;

    /*
     * ============================================================
     * RECORD PAYMENT
     * ============================================================
     *
     * Interest model:
     *
     * MONTHLY:
     *      dailyRate = monthlyRate / 100 / 30
     *
     * ANNUAL:
     *      dailyRate = annualRate / 100 / 12 / 30
     *
     * Interest:
     *
     *      interest =
     *          outstandingPrincipal
     *          × dailyRate
     *          × interestDays
     *
     * First-ever payment:
     *
     *      minimum interest days = 1
     *
     * Subsequent payments:
     *
     *      interestDays =
     *          complete 24-hour periods since
     *          previous interest timestamp
     *
     * Payment allocation:
     *
     *      1. Penalty
     *      2. Interest
     *      3. Principal
     *
     * The exact payment timestamp becomes the new
     * interest calculation timestamp.
     */
    @Transactional
    public Payment recordPayment(
            Long loanId,
            Double amount,
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
                || Double.isNaN(amount)
                || Double.isInfinite(amount)
                || amount <= 0.0) {

            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }

        amount = roundMoney(amount);

        if (amount <= 0.0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }

        String normalizedTxnId =
                normalizeTransactionId(txnId);

        /*
         * ========================================================
         * FIND LOAN WITH DATABASE LOCK
         * ========================================================
         */
        Loan loan =
                loanRepo.findByIdForUpdate(loanId)
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Loan not found: " + loanId
                                )
                        );

        /*
         * ========================================================
         * ORGANIZATION SECURITY
         * ========================================================
         */
        if (recordedBy != null
                && loan.getOrganization() != null
                && recordedBy.getOrganization() != null
                && loan.getOrganization().getId() != null
                && recordedBy.getOrganization().getId() != null
                && !loan.getOrganization()
                        .getId()
                        .equals(
                                recordedBy.getOrganization().getId()
                        )) {

            throw new RuntimeException(
                    "Access denied"
            );
        }

        /*
         * ========================================================
         * ORGANIZATION VALIDATION
         * ========================================================
         */
        if (loan.getOrganization() == null
                || loan.getOrganization().getId() == null) {

            throw new IllegalStateException(
                    "Loan organization is required"
            );
        }

        /*
         * ========================================================
         * IDEMPOTENCY
         * ========================================================
         *
         * The same transaction reference cannot be posted twice.
         */
        if (normalizedTxnId != null) {

            Optional<Payment> existingPayment =
                    paymentRepo
                            .findByOrganization_IdAndTransactionId(
                                    loan.getOrganization().getId(),
                                    normalizedTxnId
                            );

            if (existingPayment.isPresent()) {

                Payment existing =
                        existingPayment.get();

                if (existing.getLoan() != null
                        && existing.getLoan()
                                .getId()
                                .equals(loanId)) {

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

        /*
         * ========================================================
         * LOAN STATUS
         * ========================================================
         */
        if (loan.getStatus() != LoanStatus.ACTIVE
                && loan.getStatus() != LoanStatus.OVERDUE) {

            throw new RuntimeException(
                    "Loan is not active (status: "
                            + loan.getStatus()
                            + ")"
            );
        }

        /*
         * ========================================================
         * CURRENT DATE / EXACT TIME
         * ========================================================
         */
        LocalDate today =
                LocalDate.now();

        LocalDateTime now =
                LocalDateTime.now();

        /*
         * ========================================================
         * FIND ALL PAYMENT RECORDS
         * ========================================================
         */
        List<Payment> loanPayments =
                paymentRepo.findByLoanId(loanId);

        if (loanPayments == null) {
            loanPayments = List.of();
        }

        /*
         * ========================================================
         * FIND CURRENT PARTIAL INSTALLMENT
         * ========================================================
         */
        Optional<Payment> existingCurrentCycle =
                loanPayments.stream()
                        .filter(
                                p ->
                                        p != null
                                                && !Boolean.TRUE.equals(
                                                p.getPaid()
                                        )
                                                && safe(
                                                p.getAmountPaid()
                                        ) > 0.0
                        )
                        .min(
                                Comparator.comparing(
                                        Payment::getDueDate,
                                        Comparator.nullsLast(
                                                Comparator.naturalOrder()
                                        )
                                )
                        );

        /*
         * ========================================================
         * FIND NEXT UNPAID INSTALLMENT
         * ========================================================
         */
        Optional<Payment> unpaidInstallment =
                loanPayments.stream()
                        .filter(
                                p ->
                                        p != null
                                                && !Boolean.TRUE.equals(
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

        /*
         * ========================================================
         * SELECT INSTALLMENT
         * ========================================================
         */
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
                            .filter(p -> p != null)
                            .map(Payment::getInstallmentNumber)
                            .filter(n -> n != null)
                            .max(Integer::compareTo)
                            .orElse(0)
                            + 1;

            /*
             * For a new payment cycle, inherit the exact timestamp
             * from the previous interest calculation.
             *
             * For the very first cycle, this will be the exact
             * disbursement timestamp.
             */
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
                                    safe(
                                            loan.getNextInstallmentAmount()
                                    )
                            )
                            .amountPaid(
                                    0.0
                            )
                            .principalComponent(
                                    0.0
                            )
                            .interestComponent(
                                    0.0
                            )
                            .penalty(
                                    0.0
                            )
                            .cycleInterestDue(
                                    0.0
                            )
                            .cycleInterestRemaining(
                                    0.0
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

        /*
         * ========================================================
         * PAYMENT DUE DATE
         * ========================================================
         *
         * Due date itself = 0 penalty days.
         *
         * 08/08 = 0
         * 09/08 = 1
         * 10/08 = 2
         */
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

        /*
         * ========================================================
         * EXISTING PAYMENT VALUES
         * ========================================================
         */
        double amountPaidSoFar =
                safe(
                        installment.getAmountPaid()
                );

        double interestAlreadyPaid =
                Math.max(
                        0.0,
                        roundMoney(
                                safe(
                                        installment.getInterestComponent()
                                )
                        )
                );

        double penaltyAlreadyRecorded =
                Math.max(
                        0.0,
                        roundMoney(
                                safe(
                                        installment.getPenalty()
                                )
                        )
                );

        double existingCycleInterestDue =
                Math.max(
                        0.0,
                        roundMoney(
                                safe(
                                        installment.getCycleInterestDue()
                                )
                        )
                );

        /*
         * ========================================================
         * CURRENT OUTSTANDING PRINCIPAL
         * ========================================================
         */
        double currentBalance =
                Math.max(
                        0.0,
                        roundMoney(
                                safe(
                                        loan.getOutstandingBalance()
                                )
                        )
                );

        if (currentBalance <= 0.0) {

            throw new IllegalStateException(
                    "Loan has no outstanding principal balance."
            );
        }

        /*
         * ========================================================
         * INTEREST START TIMESTAMP
         * ========================================================
         */
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

        /*
         * ========================================================
         * DETERMINE WHETHER THIS IS THE FIRST LOAN INTEREST
         * CALCULATION
         * ========================================================
         *
         * This is the critical correction.
         *
         * A newly disbursed loan may have:
         *
         *      disbursedAt = 10:00
         *      payment     = 10:01
         *
         * The borrower must still pay one interest day.
         *
         * However, if the borrower paid at 10:00 and then pays
         * again at 10:01, the second payment must NOT create
         * another interest day.
         *
         * Therefore first-loan-payment detection is based on
         * whether there has ever been a previous interest event,
         * not simply whether interestCalculationDate is null.
         */
        boolean firstLoanInterestCalculation =
                isFirstLoanInterestCalculation(
                        loan,
                        loanPayments,
                        installment
                );

        /*
         * ========================================================
         * EXACT ELAPSED HOURS
         * ========================================================
         */
        long elapsedHours =
                ChronoUnit.HOURS.between(
                        interestStartDateTime,
                        now
                );

        if (elapsedHours < 0L) {
            elapsedHours = 0L;
        }

        /*
         * ========================================================
         * INTEREST DAYS
         * ========================================================
         *
         * FIRST PAYMENT:
         *
         *      minimum = 1 day
         *
         *      10:00 -> 10:01 = 1 day
         *      10:00 -> 15:00 = 1 day
         *      10:00 -> next day 10:00 = 1 day
         *      10:00 -> two days later = 2 days
         *
         * SUBSEQUENT PAYMENTS:
         *
         *      only completed 24-hour periods count.
         *
         *      10:00 -> 10:01 = 0
         *      10:00 -> 23:00 = 0
         *      10:00 -> next day 10:00 = 1
         *      10:00 -> two days later = 2
         */
        long elapsedDays;

        if (firstLoanInterestCalculation) {

            elapsedDays =
                    Math.max(
                            1L,
                            elapsedHours / 24L
                    );

        } else {

            elapsedDays =
                    elapsedHours / 24L;
        }

        /*
         * ========================================================
         * DAILY INTEREST RATE
         * ========================================================
         */
        BigDecimal dailyRateDecimal =
                financialCalculationService.dailyRate(
                        loan.getInterestRate(),
                        loan.getInterestRateType()
                );

        if (dailyRateDecimal == null) {
            dailyRateDecimal =
                    BigDecimal.ZERO;
        }

        dailyRateDecimal =
                dailyRateDecimal.setScale(
                        12,
                        RoundingMode.HALF_UP
                );

        double dailyRate =
                dailyRateDecimal.doubleValue();

        /*
         * ========================================================
         * NEW INTEREST
         * ========================================================
         *
         * Formula:
         *
         *      Interest =
         *          Outstanding Principal
         *          × Daily Rate
         *          × Interest Days
         *
         * Example:
         *
         *      Principal = 5,000,000
         *      Monthly rate = 10%
         *
         *      Daily rate = 10% / 30
         *
         *      1 day =
         *          5,000,000 × 0.10 / 30
         *
         *      = 16,666.67
         */
        BigDecimal newlyAccruedInterestDecimal =
                financialCalculationService.interest(
                        financialCalculationService.money(
                                currentBalance
                        ),
                        dailyRateDecimal,
                        elapsedDays
                );

        double newlyAccruedInterest =
                newlyAccruedInterestDecimal != null
                        ? newlyAccruedInterestDecimal.doubleValue()
                        : 0.0;

        newlyAccruedInterest =
                Math.max(
                        0.0,
                        roundMoney(
                                newlyAccruedInterest
                        )
                );

        log.info(
                "INTEREST CALCULATION: loanId={}, principal={}, interestRate={}, rateType={}, dailyRate={}, start={}, now={}, elapsedHours={}, interestDays={}, newInterest={}",
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

        /*
         * ========================================================
         * TOTAL CYCLE INTEREST
         * ========================================================
         */
        double totalCycleInterestDue =
                roundMoney(
                        existingCycleInterestDue
                                + newlyAccruedInterest
                );

        totalCycleInterestDue =
                Math.max(
                        0.0,
                        totalCycleInterestDue
                );

        /*
         * ========================================================
         * REMAINING INTEREST BEFORE PAYMENT
         * ========================================================
         */
        double remainingInterestBeforePayment =
                roundMoney(
                        Math.max(
                                0.0,
                                totalCycleInterestDue
                                        - interestAlreadyPaid
                        )
                );

        /*
         * ========================================================
         * PENALTY
         * ========================================================
         *
         * Penalty is independent from interest.
         *
         * Current FinancialCalculationService rule:
         *
         *      2% per 30 days
         *
         *      daily penalty =
         *          2% / 30
         *
         * Penalty base:
         *
         *      outstanding principal
         */
        BigDecimal calculatedPenaltyDecimal =
                financialCalculationService.penalty(
                        financialCalculationService.money(
                                currentBalance
                        ),
                        daysLate
                );

        double calculatedPenalty =
                calculatedPenaltyDecimal != null
                        ? calculatedPenaltyDecimal.doubleValue()
                        : 0.0;

        calculatedPenalty =
                Math.max(
                        0.0,
                        roundMoney(
                                calculatedPenalty
                        )
                );

        /*
         * Only the newly accumulated penalty is charged.
         */
        double newPenalty =
                Math.max(
                        0.0,
                        roundMoney(
                                calculatedPenalty
                                        - penaltyAlreadyRecorded
                        )
                );

        double totalPenalty =
                roundMoney(
                        penaltyAlreadyRecorded
                                + newPenalty
                );

        /*
         * ========================================================
         * PAYMENT ALLOCATION
         * ========================================================
         *
         * PaymentTransaction.AllocationData does NOT exist.
         *
         * We use:
         *
         *      FinancialCalculationService.Allocation
         *
         * Allocation order implemented by FinancialCalculationService:
         *
         *      1. Penalty
         *      2. Interest
         *      3. Principal
         *      4. Unapplied
         */
        FinancialCalculationService.Allocation allocation =
                financialCalculationService.allocatePayment(
                        financialCalculationService.money(
                                amount
                        ),
                        financialCalculationService.money(
                                newPenalty
                        ),
                        financialCalculationService.money(
                                remainingInterestBeforePayment
                        ),
                        financialCalculationService.money(
                                currentBalance
                        )
                );

        if (allocation == null) {
            throw new IllegalStateException(
                    "Payment allocation failed."
            );
        }

        double interestPaid =
                allocation.interestPaid() != null
                        ? allocation.interestPaid().doubleValue()
                        : 0.0;

        double principalPaid =
                allocation.principalPaid() != null
                        ? allocation.principalPaid().doubleValue()
                        : 0.0;

        double newBalance =
                allocation.newPrincipalBalance() != null
                        ? allocation.newPrincipalBalance().doubleValue()
                        : currentBalance;

        double unappliedAmount =
                allocation.unappliedAfterInterestAndPenalty() != null
                        ? allocation
                        .unappliedAfterInterestAndPenalty()
                        .doubleValue()
                        : 0.0;

        interestPaid =
                Math.max(
                        0.0,
                        roundMoney(
                                interestPaid
                        )
                );

        principalPaid =
                Math.max(
                        0.0,
                        roundMoney(
                                principalPaid
                        )
                );

        newBalance =
                Math.max(
                        0.0,
                        roundMoney(
                                newBalance
                        )
                );

        unappliedAmount =
                Math.max(
                        0.0,
                        roundMoney(
                                unappliedAmount
                        )
                );

        /*
         * ========================================================
         * CUMULATIVE INTEREST PAID
         * ========================================================
         */
        double totalInterestPaid =
                roundMoney(
                        interestAlreadyPaid
                                + interestPaid
                );

        /*
         * ========================================================
         * CUMULATIVE PRINCIPAL PAID
         * ========================================================
         */
        double existingPrincipalPaid =
                safe(
                        installment.getPrincipalComponent()
                );

        double totalPrincipalPaid =
                roundMoney(
                        existingPrincipalPaid
                                + principalPaid
                );

        /*
         * ========================================================
         * REMAINING INTEREST
         * ========================================================
         */
        double remainingInterestAfterPayment =
                roundMoney(
                        Math.max(
                                0.0,
                                totalCycleInterestDue
                                        - totalInterestPaid
                        )
                );

        /*
         * ========================================================
         * COMPLETION
         * ========================================================
         */
        boolean interestCovered =
                remainingInterestAfterPayment
                        <= MONEY_EPSILON;

        boolean fullyPaidOff =
                newBalance
                        <= MONEY_EPSILON;

        boolean cycleCompleted =
                interestCovered
                        || fullyPaidOff;

        /*
         * ========================================================
         * NEW PAYMENT AMOUNT
         * ========================================================
         */
        double newAmountPaid =
                roundMoney(
                        amountPaidSoFar
                                + amount
                );

        /*
         * ========================================================
         * UPDATE PAYMENT RECORD
         * ========================================================
         */
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
         * ========================================================
         * RESET INTEREST CLOCK
         * ========================================================
         *
         * This exact timestamp is the beginning of the next
         * interest period.
         *
         * Example:
         *
         *      Payment 1: 10:00
         *      Payment 2: 10:01
         *
         * Payment 2 gets:
         *
         *      elapsedHours = 0
         *      elapsedDays  = 0
         *
         * Therefore no duplicate interest.
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

        /*
         * ========================================================
         * PAYMENT REFERENCE
         * ========================================================
         */
        if (installment.getPaymentReference() == null
                || installment.getPaymentReference().isBlank()) {

            installment.setPaymentReference(
                    generateRef(
                            loan
                    )
            );
        }

        /*
         * ========================================================
         * SAVE PAYMENT
         * ========================================================
         */
        try {

            installment =
                    paymentRepo.save(
                            installment
                    );

        } catch (DataIntegrityViolationException e) {

            /*
             * Concurrent duplicate transaction protection.
             */
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

                    if (existing.getLoan() != null
                            && existing.getLoan()
                                    .getId()
                                    .equals(loanId)) {

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

        /*
         * ========================================================
         * UPDATE LOAN TOTAL PAID
         * ========================================================
         */
        double oldTotalPaid =
                safe(
                        loan.getTotalPaid()
                );

        loan.setTotalPaid(
                roundMoney(
                        oldTotalPaid
                                + amount
                )
        );

        /*
         * ========================================================
         * UPDATE OUTSTANDING BALANCE
         * ========================================================
         */
        loan.setOutstandingBalance(
                newBalance
        );

        loan.setLastPaymentDate(
                today
        );

        /*
         * ========================================================
         * LOAN STATUS
         * ========================================================
         */
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
                                            p != null
                                                    && !Boolean.TRUE.equals(
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
             * Payment does NOT stop interest.
             *
             * The reduced principal continues accruing interest
             * from the exact payment timestamp.
             */
            loan.setStatus(
                    isLate
                            ? LoanStatus.OVERDUE
                            : LoanStatus.ACTIVE
            );

            if (cycleCompleted
                    || interestCovered) {

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

        /*
         * ========================================================
         * AUDIT
         * ========================================================
         */
        double dailyInterestAmount =
                roundMoney(
                        currentBalance
                                * dailyRate
                );

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
                        + ", principal before payment: "
                        + currentBalance
                        + ", daily interest rate: "
                        + dailyRate
                        + ", daily interest amount: "
                        + dailyInterestAmount
                        + ", newly accrued interest: "
                        + newlyAccruedInterest
                        + ", interest paid: "
                        + interestPaid
                        + ", principal paid: "
                        + principalPaid
                        + ", new outstanding principal: "
                        + newBalance
                        + ", penalty days: "
                        + daysLate
                        + ", new penalty: "
                        + newPenalty
                        + ", total penalty: "
                        + totalPenalty
                        + ", remaining interest: "
                        + remainingInterestAfterPayment
                        + ", unapplied amount: "
                        + unappliedAmount
                        + ", transactionId: "
                        + normalizedTxnId
        );

        /*
         * ========================================================
         * EMAIL
         * ========================================================
         */
        try {

            mailService.sendPaymentConfirmation(
                    loan,
                    amount
            );

        } catch (Exception e) {

            log.warn(
                    "Payment email notification failed",
                    e
            );
        }

        /*
         * ========================================================
         * SMS
         * ========================================================
         */
        try {

            smsService.sendPaymentConfirmed(
                    loan,
                    amount
            );

        } catch (Exception e) {

            log.warn(
                    "Payment SMS notification failed",
                    e
            );
        }

        /*
         * ========================================================
         * OFFICER NOTIFICATION
         * ========================================================
         */
        if (loan.getLoanOfficer() != null
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

        /*
         * ========================================================
         * WEBHOOK
         * ========================================================
         */
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
                    financialCalculationService
                            .penalty(
                                    BigDecimal.ONE,
                                    1
                            )
                            .doubleValue()
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

        /*
         * ========================================================
         * IMMUTABLE PAYMENT TRANSACTION
         * ========================================================
         *
         * Uses the actual PaymentTransaction fields.
         *
         * There is NO AllocationData.
         */
        String transactionReference =
                normalizedTxnId != null
                        ? normalizedTxnId
                        : installment.getPaymentReference();

        if (transactionReference != null
                && !transactionReference.isBlank()) {

            paymentTransactionRepo.save(
                    PaymentTransaction.builder()
                            .loan(loan)
                            .organization(
                                    loan.getOrganization()
                            )
                            .installment(
                                    installment
                            )
                            .recordedBy(
                                    recordedBy
                            )
                            .transactionReference(
                                    transactionReference
                            )
                            .amount(
                                    financialCalculationService
                                            .money(
                                                    amount
                                            )
                                            .setScale(
                                                    2,
                                                    RoundingMode.HALF_UP
                                            )
                            )
                            .penaltyComponent(
                                    financialCalculationService
                                            .money(
                                                    newPenalty
                                            )
                                            .setScale(
                                                    2,
                                                    RoundingMode.HALF_UP
                                            )
                            )
                            .interestComponent(
                                    financialCalculationService
                                            .money(
                                                    interestPaid
                                            )
                                            .setScale(
                                                    2,
                                                    RoundingMode.HALF_UP
                                            )
                            )
                            .principalComponent(
                                    financialCalculationService
                                            .money(
                                                    principalPaid
                                            )
                                            .setScale(
                                                    2,
                                                    RoundingMode.HALF_UP
                                            )
                            )
                            .unappliedAmount(
                                    financialCalculationService
                                            .money(
                                                    unappliedAmount
                                            )
                                            .setScale(
                                                    2,
                                                    RoundingMode.HALF_UP
                                            )
                            )
                            .status(
                                    PaymentTransaction.TransactionStatus.POSTED
                            )
                            .reversed(
                                    false
                            )
                            .build()
            );
        }

        /*
         * ========================================================
         * ACCOUNTING
         * ========================================================
         */
        accountingService.postPaymentReceived(
                installment,
                amount,
                principalPaid,
                interestPaid,
                newPenalty
        );

        return installment;
    }

    /*
     * ============================================================
     * GET LOAN SCHEDULE
     * ============================================================
     */
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
                        .equals(
                                orgId
                        )) {

            throw new RuntimeException(
                    "Access denied"
            );
        }

        List<Payment> schedule =
                paymentRepo.findByLoanId(
                        loanId
                );

        return schedule != null
                ? schedule
                : List.of();
    }

    /*
     * ============================================================
     * MARK OVERDUE
     * ============================================================
     */
    @Transactional
    public void markOverdueLoans(
            Long orgId
    ) {

        if (orgId == null) {
            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }

        List<Payment> overduePayments =
                paymentRepo
                        .findByOrganization_IdAndPaidFalseAndDueDateBefore(
                                orgId,
                                LocalDate.now()
                        );

        if (overduePayments == null) {
            return;
        }

        for (Payment payment :
                overduePayments) {

            if (payment == null) {
                continue;
            }

            Loan loan =
                    payment.getLoan();

            if (loan == null) {
                continue;
            }

            if (loan.getStatus()
                    == LoanStatus.ACTIVE) {

                loan.setStatus(
                        LoanStatus.OVERDUE
                );
            }

            /*
             * Due date:
             *
             * 08/08 = 0
             * 09/08 = 1
             * 10/08 = 2
             */
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

    /*
     * ============================================================
     * DETERMINE INTEREST START TIMESTAMP
     * ============================================================
     */
    private LocalDateTime determineInterestStartDateTime(
            Payment installment,
            Loan loan,
            List<Payment> loanPayments,
            LocalDateTime now
    ) {

        /*
         * Existing installment clock always wins.
         */
        if (installment != null
                && installment.getInterestCalculationDate() != null) {

            return installment.getInterestCalculationDate();
        }

        /*
         * Otherwise find the most recent interest timestamp
         * on this loan.
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
        if (loan != null
                && loan.getDisbursedAt() != null) {

            return loan.getDisbursedAt();
        }

        /*
         * Legacy fallback.
         */
        if (installment != null
                && installment.getCreatedAt() != null) {

            return installment.getCreatedAt();
        }

        /*
         * Start date fallback.
         */
        if (loan != null
                && loan.getStartDate() != null) {

            return loan.getStartDate()
                    .atStartOfDay();
        }

        return now;
    }

    /*
     * ============================================================
     * FIRST LOAN INTEREST CALCULATION
     * ============================================================
     */
    private boolean isFirstLoanInterestCalculation(
            Loan loan,
            List<Payment> payments,
            Payment installment
    ) {

        /*
         * If there are previous payment transaction records,
         * this is not the first loan payment.
         */
        if (payments != null) {

            boolean hasPreviousPayment =
                    payments.stream()
                            .anyMatch(
                                    p ->
                                            p != null
                                                    && (
                                                    safe(
                                                            p.getAmountPaid()
                                                    ) > 0.0
                                                            || safe(
                                                            p.getInterestComponent()
                                                    ) > 0.0
                                                            || safe(
                                                            p.getPrincipalComponent()
                                                    ) > 0.0
                                            )
                            );

            if (hasPreviousPayment) {
                return false;
            }
        }

        /*
         * If the current installment already contains an interest
         * amount, it is no longer the first calculation.
         */
        if (installment != null) {

            if (safe(
                    installment.getInterestComponent()
            ) > 0.0) {

                return false;
            }

            if (safe(
                    installment.getCycleInterestDue()
            ) > 0.0) {

                return false;
            }

            if (safe(
                    installment.getAmountPaid()
            ) > 0.0) {

                return false;
            }
        }

        /*
         * A loan with a valid disbursement timestamp and no
         * previous payment is a first-interest calculation.
         */
        if (loan != null
                && loan.getDisbursedAt() != null) {

            return true;
        }

        /*
         * No history means first calculation.
         */
        return true;
    }

    /*
     * ============================================================
     * FIND LATEST INTEREST TIMESTAMP
     * ============================================================
     */
    private LocalDateTime findLatestInterestCalculationTimestamp(
            List<Payment> payments,
            Loan loan
    ) {

        if (payments != null) {

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

        /*
         * No previous payment timestamp exists.
         *
         * Start from exact disbursement timestamp.
         */
        if (loan != null
                && loan.getDisbursedAt() != null) {

            return loan.getDisbursedAt();
        }

        return null;
    }

    /*
     * ============================================================
     * DAILY INTEREST RATE
     * ============================================================
     */
    private double calculateDailyRate(
            Loan loan
    ) {

        if (loan == null) {
            return 0.0;
        }

        BigDecimal dailyRate =
                financialCalculationService.dailyRate(
                        loan.getInterestRate(),
                        loan.getInterestRateType()
                );

        return dailyRate != null
                ? dailyRate.doubleValue()
                : 0.0;
    }

    /*
     * ============================================================
     * NORMALIZE TRANSACTION ID
     * ============================================================
     */
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
     * ============================================================
     * SAFE DOUBLE
     * ============================================================
     */
    private double safe(
            Double value
    ) {

        if (value == null
                || Double.isNaN(value)
                || Double.isInfinite(value)) {

            return 0.0;
        }

        return value;
    }

    /*
     * ============================================================
     * ROUND MONEY
     * ============================================================
     */
    private double roundMoney(
            double value
    ) {

        if (Double.isNaN(value)
                || Double.isInfinite(value)) {

            return 0.0;
        }

        return Math.round(
                value * 100.0
        ) / 100.0;
    }

    /*
     * ============================================================
     * PAYMENT REFERENCE
     * ============================================================
     */
    private String generateRef(
            Loan loan
    ) {

        String loanReference =
                loan != null
                        && loan.getReferenceNumber() != null
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

    /*
     * ============================================================
     * AUDIT
     * ============================================================
     */
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