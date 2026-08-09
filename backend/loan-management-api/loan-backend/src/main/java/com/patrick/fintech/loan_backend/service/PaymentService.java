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

        String normalizedTxnId = normalizeTransactionId(txnId);

        // ============================================================
        // FIND LOAN
        // ============================================================

        Loan loan = loanRepo.findById(loanId)
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
                        .equals(recordedBy.getOrganization().getId())
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

                Payment existing = existingPayment.get();

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
        // CURRENT DATE/TIME
        // ============================================================

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // ============================================================
        // FIND EXISTING PAYMENT RECORDS
        // ============================================================

        List<Payment> loanPayments =
                paymentRepo.findByLoanId(loanId);

        // ============================================================
        // FIND EXISTING PARTIALLY PAID INSTALLMENT
        // ============================================================

        Optional<Payment> existingCurrentCycle =
                loanPayments.stream()
                        .filter(
                                p ->
                                        !Boolean.TRUE.equals(p.getPaid())
                                                && safe(p.getAmountPaid())
                                                .compareTo(BigDecimal.ZERO) > 0
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
                                        !Boolean.TRUE.equals(p.getPaid())
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
        // SELECT PAYMENT CYCLE
        // ============================================================

        if (existingCurrentCycle.isPresent()) {

            installment = existingCurrentCycle.get();

            log.info(
                    "Continuing existing monthly payment cycle. loanId={}, installment={}, paymentId={}",
                    loanId,
                    installment.getInstallmentNumber(),
                    installment.getId()
            );

        } else if (unpaidInstallment.isPresent()) {

            installment = unpaidInstallment.get();

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
                            .organization(loan.getOrganization())
                            .installmentNumber(nextNumber)
                            .dueDate(dueDate)
                            .amount(loan.getNextInstallmentAmount())
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
                    "Creating new monthly payment cycle. loanId={}, installment={}, previousInterestTimestamp={}",
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
                (int) Math.max(
                        0L,
                        daysLateLong
                );

        boolean isLate = daysLate > 0;

        // ================================================================
        // EXISTING PAYMENT VALUES
        // ================================================================

        BigDecimal amountPaidSoFar =
                roundMoney(
                        safe(
                                installment.getAmountPaid()
                        )
                );

        BigDecimal interestAlreadyPaid =
                roundMoney(
                        safe(
                                installment.getInterestComponent()
                        )
                );

        interestAlreadyPaid =
                interestAlreadyPaid.max(
                        BigDecimal.ZERO
                );

        BigDecimal penaltyAlreadyRecorded =
                roundMoney(
                        safe(
                                installment.getPenalty()
                        )
                );

        penaltyAlreadyRecorded =
                penaltyAlreadyRecorded.max(
                        BigDecimal.ZERO
                );

        BigDecimal existingCycleInterestDue =
                roundMoney(
                        safe(
                                installment.getCycleInterestDue()
                        )
                );

        existingCycleInterestDue =
                existingCycleInterestDue.max(
                        BigDecimal.ZERO
                );

        BigDecimal existingCycleInterestRemaining =
                roundMoney(
                        safe(
                                installment.getCycleInterestRemaining()
                        )
                );

        existingCycleInterestRemaining =
                existingCycleInterestRemaining.max(
                        BigDecimal.ZERO
                );

        // ================================================================
        // CURRENT PRINCIPAL
        // ================================================================

        BigDecimal currentBalance =
                roundMoney(
                        safe(
                                loan.getOutstandingBalance()
                        )
                );

        currentBalance =
                currentBalance.max(
                        BigDecimal.ZERO
                );

        if (
                currentBalance.compareTo(
                        BigDecimal.ZERO
                ) <= 0
        ) {

            throw new IllegalStateException(
                    "Loan has no outstanding principal balance."
            );
        }

        // ================================================================
        // MONTHLY INTEREST
        // ================================================================

        /*
         * IMPORTANT BUSINESS RULE
         *
         * Interest is calculated ONCE for the current monthly
         * payment cycle.
         *
         * Example:
         *
         * Principal = 12,800
         * Monthly interest = 12%
         *
         * Monthly interest:
         *
         * 12,800 × 12 / 100 = 1,536.00
         *
         * First payment:
         *
         * Payment = 500
         * Interest = 500
         * Principal = 0
         *
         * Second payment during SAME cycle:
         *
         * No new 12% interest calculation.
         * Remaining interest is still 1,036.
         *
         * Once the monthly cycle is completed, the next cycle
         * receives a new interest calculation based on the
         * reduced outstanding principal.
         */

        boolean interestAlreadyCalculatedForCycle =
                existingCycleInterestDue.compareTo(
                        BigDecimal.ZERO
                ) > 0
                        || installment.getInterestCalculationDate() != null;

        BigDecimal newlyAccruedInterest =
                BigDecimal.ZERO;

        if (!interestAlreadyCalculatedForCycle) {

            newlyAccruedInterest =
                    calculateMonthlyInterest(
                            currentBalance,
                            loan
                    );

            newlyAccruedInterest =
                    roundMoney(
                            newlyAccruedInterest
                    );

            installment.setInterestCalculationDate(
                    now
            );

            log.info(
                    "MONTHLY INTEREST CALCULATED: loanId={}, principal={}, rate={}, rateType={}, monthlyInterest={}, calculationDate={}",
                    loan.getId(),
                    currentBalance,
                    loan.getInterestRate(),
                    loan.getInterestRateType(),
                    newlyAccruedInterest,
                    now
            );

        } else {

            log.info(
                    "MONTHLY INTEREST ALREADY CALCULATED FOR CURRENT CYCLE: loanId={}, existingInterestDue={}, interestCalculationDate={}",
                    loan.getId(),
                    existingCycleInterestDue,
                    installment.getInterestCalculationDate()
            );
        }

        // ================================================================
        // TOTAL CYCLE INTEREST
        // ================================================================

        BigDecimal totalCycleInterestDue;

        if (interestAlreadyCalculatedForCycle) {

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

        totalCycleInterestDue =
                totalCycleInterestDue.max(
                        BigDecimal.ZERO
                );

        // ================================================================
        // INTEREST REMAINING BEFORE PAYMENT
        // ================================================================

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
        // PENALTY
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
         * PAYMENT PRIORITY:
         *
         * 1. PENALTY
         * 2. INTEREST
         * 3. PRINCIPAL
         *
         * This guarantees interest is charged/collected BEFORE
         * principal is reduced.
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
        // CUMULATIVE INTEREST
        // ================================================================

        BigDecimal totalInterestPaid =
                roundMoney(
                        interestAlreadyPaid
                                .add(
                                        interestPaid
                                )
                );

        // ================================================================
        // CUMULATIVE PRINCIPAL
        // ================================================================

        BigDecimal existingPrincipalPaid =
                roundMoney(
                        safe(
                                installment.getPrincipalComponent()
                        )
                );

        BigDecimal totalPrincipalPaid =
                roundMoney(
                        existingPrincipalPaid
                                .add(
                                        principalPaid
                                )
                );

        // ================================================================
        // REMAINING INTEREST
        // ================================================================

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
        // AMOUNT REMAINING ON INSTALLMENT
        // ================================================================

        BigDecimal scheduledInstallmentAmount =
                roundMoney(
                        safe(
                                installment.getAmount()
                        )
                );

        BigDecimal newAmountPaid =
                roundMoney(
                        amountPaidSoFar
                                .add(
                                        amount
                                )
                );

        BigDecimal remainingScheduledAmount =
                roundMoney(
                        scheduledInstallmentAmount
                                .subtract(
                                        newAmountPaid
                                )
                                .max(
                                        BigDecimal.ZERO
                                )
                );

        // ================================================================
        // CYCLE COMPLETION
        // ================================================================

        boolean interestCovered =
                remainingInterestAfterPayment.compareTo(
                        new BigDecimal("0.01")
                ) <= 0;

        boolean scheduledAmountCovered =
                remainingScheduledAmount.compareTo(
                        new BigDecimal("0.01")
                ) <= 0;

        boolean fullyPaidOff =
                newBalance.compareTo(
                        new BigDecimal("0.01")
                ) <= 0;

        /*
         * IMPORTANT:
         *
         * Interest being paid does NOT automatically mean the
         * monthly installment is completed.
         *
         * The cycle is completed when:
         *
         * - the scheduled amount is satisfied, OR
         * - the entire loan is paid off.
         *
         * Interest being covered alone is NOT enough.
         */

        boolean cycleCompleted =
                scheduledAmountCovered
                        || fullyPaidOff;

        // ================================================================
        // LOG PAYMENT ALLOCATION
        // ================================================================

        log.info(
                "PAYMENT ALLOCATION: loanId={}, payment={}, penalty={}, interestPaid={}, principalPaid={}, oldBalance={}, newBalance={}, cycleInterestDue={}, interestRemaining={}, cycleCompleted={}",
                loan.getId(),
                amount,
                newPenalty,
                interestPaid,
                principalPaid,
                currentBalance,
                newBalance,
                totalCycleInterestDue,
                remainingInterestAfterPayment,
                cycleCompleted
        );

        // ================================================================
        // UPDATE PAYMENT
        // ================================================================

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
         * Store the exact timestamp of this payment.
         *
         * IMPORTANT:
         *
         * We do NOT use this timestamp to calculate another
         * monthly interest amount inside the same cycle.
         */
        installment.setInterestCalculationDate(
                installment.getInterestCalculationDate() != null
                        ? installment.getInterestCalculationDate()
                        : now
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
                        || installment.getPaymentReference().isBlank()
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
                roundMoney(
                        safe(
                                loan.getTotalPaid()
                        )
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

            loan.setStatus(
                    isLate
                            ? LoanStatus.OVERDUE
                            : LoanStatus.ACTIVE
            );

            /*
             * Only move to the next monthly cycle when the current
             * scheduled installment has actually been completed.
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

                /*
                 * Keep the current cycle open.
                 *
                 * This is especially important when the borrower
                 * pays interest first and still has principal due.
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
                        + " — monthly interest due: "
                        + totalCycleInterestDue
                        + ", interest paid: "
                        + interestPaid
                        + ", interest remaining: "
                        + remainingInterestAfterPayment
                        + ", principal paid: "
                        + principalPaid
                        + ", previous principal: "
                        + currentBalance
                        + ", new principal: "
                        + newBalance
                        + ", penalty: "
                        + totalPenalty
                        + ", monthly interest calculated: "
                        + (!interestAlreadyCalculatedForCycle)
                        + ", interest calculation date: "
                        + installment.getInterestCalculationDate()
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
                                + ". Interest paid: "
                                + interestPaid
                                + ". Principal paid: "
                                + principalPaid
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
                    "monthlyInterestCalculated",
                    !interestAlreadyCalculatedForCycle
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
                    "[PAYMENT WEBHOOK] Dispatching PAYMENT_MADE. loanId={}, paymentId={}, amount={}, interestPaid={}, principalPaid={}, transactionId={}",
                    loan.getId(),
                    installment.getId(),
                    amount,
                    interestPaid,
                    principalPaid,
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
    // CALCULATE MONTHLY INTEREST
    // ================================================================

    private BigDecimal calculateMonthlyInterest(
            BigDecimal principal,
            Loan loan
    ) {

        if (principal == null) {
            return BigDecimal.ZERO;
        }

        principal =
                principal.max(
                        BigDecimal.ZERO
                );

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

        if (
                "MONTHLY".equalsIgnoreCase(
                        rateType
                )
        ) {

            return roundMoney(
                    principal
                            .multiply(rate)
                            .divide(
                                    BigDecimal.valueOf(100),
                                    12,
                                    RoundingMode.HALF_UP
                            )
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
             * Annual rate converted to one monthly cycle.
             *
             * Example:
             *
             * 24% annual
             *
             * 24 / 12 = 2% monthly
             */

            BigDecimal monthlyRate =
                    rate
                            .divide(
                                    BigDecimal.valueOf(12),
                                    12,
                                    RoundingMode.HALF_UP
                            );

            return roundMoney(
                    principal
                            .multiply(monthlyRate)
                            .divide(
                                    BigDecimal.valueOf(100),
                                    12,
                                    RoundingMode.HALF_UP
                            )
            );
        }

        // ============================================================
        // UNKNOWN TYPE
        // ============================================================

        log.warn(
                "Unknown interestRateType '{}' for loan {}. Treating rate as MONTHLY.",
                rateType,
                loan.getId()
        );

        return roundMoney(
                principal
                        .multiply(rate)
                        .divide(
                                BigDecimal.valueOf(100),
                                12,
                                RoundingMode.HALF_UP
                        )
        );
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

        if (
                installment.getInterestCalculationDate() != null
        ) {

            return installment.getInterestCalculationDate();
        }

        LocalDateTime latestTimestamp =
                findLatestInterestCalculationTimestamp(
                        loanPayments,
                        null
                );

        if (latestTimestamp != null) {
            return latestTimestamp;
        }

        if (
                loan.getDisbursedAt() != null
        ) {

            return loan.getDisbursedAt();
        }

        if (
                installment.getCreatedAt() != null
        ) {

            return installment.getCreatedAt();
        }

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

        if (payments == null || payments.isEmpty()) {

            if (
                    loan != null
                            && loan.getDisbursedAt() != null
            ) {

                return loan.getDisbursedAt();
            }

            return null;
        }

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

        if (
                loan != null
                        && loan.getDisbursedAt() != null
        ) {

            return loan.getDisbursedAt();
        }

        return null;
    }

    // ================================================================
    // NORMALIZE TRANSACTION ID
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
            return BigDecimal.ZERO;
        }

        return value;
    }

    // ================================================================
    // SAFE DOUBLE
    // ================================================================

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

    // ================================================================
    // ROUND MONEY
    // ================================================================

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

    // ================================================================
    // BIGDECIMAL -> DOUBLE
    // ================================================================

    private double toDouble(
            BigDecimal value
    ) {

        if (value == null) {
            return 0.0;
        }

        return value.doubleValue();
    }

    // ================================================================
    // GENERATE PAYMENT REFERENCE
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