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
     * Records a payment against a loan.
     *
     * Interest policy:
     *
     * 1. Interest begins immediately from loan.disbursedAt.
     * 2. First payment always carries at least one day of interest
     *    when payment occurs after disbursement.
     * 3. Additional payments on the same calendar day do not create
     *    additional interest.
     * 4. A payment on the following calendar day accrues one additional
     *    day of interest.
     * 5. Interest is calculated against the outstanding principal.
     * 6. Interest is paid before principal.
     * 7. The interest calculation timestamp is advanced only after
     *    the payment is successfully processed.
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

        Loan loan =
                loanRepo.findById(loanId)
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Loan not found: " + loanId
                                )
                        );

        validateOrganizationAccess(
                loan,
                recordedBy
        );

        /*
         * Idempotency / duplicate transaction protection.
         */
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

        /*
         * Payments are allowed only against active/overdue loans.
         */
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

        LocalDate today =
                LocalDate.now();

        LocalDateTime now =
                LocalDateTime.now();

        List<Payment> loanPayments =
                paymentRepo.findByLoanId(loanId);

        /*
         * Find the currently open installment.
         *
         * A partially paid installment remains open even when the
         * borrower has paid all currently accrued interest.
         */
        Optional<Payment> existingCurrentCycle =
                loanPayments.stream()
                        .filter(
                                p ->
                                        !Boolean.TRUE.equals(
                                                p.getPaid()
                                        )
                        )
                        .filter(
                                p ->
                                        safe(
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

        /*
         * Continue the existing open cycle whenever possible.
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

        /*
         * Determine the timestamp from which new interest must be
         * calculated.
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
         * Calculate calendar days.
         *
         * First payment after disbursement:
         *
         * 10:00 -> 10:01
         *
         * calendar difference = 0
         *
         * But business rule requires minimum 1 day.
         */
        long elapsedDays =
                calculateActualInterestDays(
                        interestStartDateTime,
                        now,
                        installment,
                        loan
                );

        BigDecimal dailyRate =
                calculateDailyRate(loan);

        BigDecimal newlyAccruedInterest =
                calculateNewInterest(
                        currentBalance,
                        dailyRate,
                        elapsedDays
                );

        /*
         * Never recalculate already persisted interest.
         *
         * New interest is added only for the newly elapsed period.
         */
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

        BigDecimal persistedRemainingInterest =
                existingCycleInterestRemaining
                        .max(BigDecimal.ZERO);

        /*
         * Use the larger value so a previously persisted interest
         * obligation cannot accidentally disappear.
         */
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

        /*
         * Penalty calculation.
         *
         * 2% monthly / 30 days.
         */
        BigDecimal monthlyPenaltyRate =
                BigDecimal.valueOf(0.02);

        BigDecimal dailyPenaltyRate =
                monthlyPenaltyRate
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

        /*
         * Penalty is paid first.
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

        /*
         * Interest is paid second.
         */
        BigDecimal interestPaid =
                roundMoney(
                        amountAfterPenalty.min(
                                remainingInterestBeforePayment
                        )
                );

        /*
         * Principal is paid last.
         */
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

        BigDecimal totalInterestPaid =
                roundMoney(
                        interestAlreadyPaid
                                .add(
                                        interestPaid
                                )
                );

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
         * IMPORTANT PRODUCTION RULE:
         *
         * Paying the interest does NOT complete the installment.
         *
         * The loan/cycle is only considered fully completed when
         * principal is paid off, OR when the scheduled cycle itself
         * has been satisfied according to the repayment schedule.
         *
         * For partial payments, keep the installment open.
         */
        boolean scheduledAmountCovered =
                isScheduledInstallmentCovered(
                        installment,
                        amountPaidSoFar,
                        amount
                );

        boolean cycleCompleted =
                fullyPaidOff
                        || scheduledAmountCovered;

        /*
         * Do not allow a payment that merely covers interest to
         * prematurely close the installment.
         */
        if (
                !fullyPaidOff
                        && !scheduledAmountCovered
        ) {
            cycleCompleted = false;
        }

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
         * Advance the interest timestamp to the exact payment time.
         *
         * Example:
         *
         * Disbursement:
         * 2026-08-09 10:00
         *
         * Payment:
         * 2026-08-09 10:01
         *
         * Timestamp becomes:
         * 2026-08-09 10:01
         *
         * Another payment:
         * 2026-08-09 15:00
         *
         * elapsed calendar days = 0
         *
         * Therefore no duplicate daily interest.
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

        if (
                installment.getPaymentReference() == null
                        || installment.getPaymentReference().isBlank()
        ) {

            installment.setPaymentReference(
                    generateRef(loan)
            );
        }

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
                                    && existing.getLoan()
                                    .getId()
                                    .equals(
                                            loanId
                                    )
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

        /*
         * Update loan totals.
         */
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

        /*
         * Loan completely repaid.
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

            /*
             * Only move to the next scheduled cycle when the
             * installment itself is genuinely completed.
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
                 * Keep the same installment open.
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

        /*
         * Audit.
         */
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

        /*
         * Email.
         */
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

        /*
         * SMS.
         */
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

        /*
         * Notify loan officer.
         */
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

        /*
         * Webhook.
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

        /*
         * Accounting.
         */
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

        if (
                scheduledAmount.compareTo(
                        BigDecimal.ZERO
                ) <= 0
        ) {
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

        if (
                recordedBy == null
                        || loan == null
                        || loan.getOrganization() == null
                        || recordedBy.getOrganization() == null
        ) {
            return;
        }

        Long loanOrganizationId =
                loan.getOrganization().getId();

        Long userOrganizationId =
                recordedBy.getOrganization().getId();

        if (
                loanOrganizationId == null
                        || userOrganizationId == null
                        || !loanOrganizationId.equals(
                        userOrganizationId
                )
        ) {

            throw new RuntimeException(
                    "Access denied"
            );
        }
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
         * For an existing open installment, continue exactly from
         * the last successful interest calculation.
         */
        if (
                installment != null
                        && installment.getInterestCalculationDate() != null
        ) {

            return installment
                    .getInterestCalculationDate();
        }

        /*
         * Look for the latest recorded calculation timestamp.
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
         * First interest calculation begins at exact disbursement
         * timestamp.
         */
        if (
                loan != null
                        && loan.getDisbursedAt() != null
        ) {

            return loan.getDisbursedAt();
        }

        /*
         * Fallback for legacy loans.
         */
        if (
                loan != null
                        && loan.getStartDate() != null
        ) {

            return loan
                    .getStartDate()
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
         * Determine whether this is genuinely the first payment
         * for this loan/cycle.
         */
        boolean firstPayment =
                installment != null
                        && installment.getInterestCalculationDate() == null
                        && safe(
                        installment.getAmountPaidDecimal()
                ).compareTo(
                        BigDecimal.ZERO
                ) <= 0
                        && safe(
                        installment.getInterestComponentDecimal()
                ).compareTo(
                        BigDecimal.ZERO
                ) <= 0
                        && safe(
                        installment.getCycleInterestDueDecimal()
                ).compareTo(
                        BigDecimal.ZERO
                ) <= 0;

        /*
         * Business rule:
         *
         * Disbursement:
         * 10:00
         *
         * Payment:
         * 10:01
         *
         * Charge one day.
         */
        if (firstPayment) {
            return 1L;
        }

        /*
         * Subsequent payments use calendar-day accrual.
         *
         * Same calendar day:
         * 0
         *
         * Next calendar day:
         * 1
         *
         * Five calendar days:
         * 5
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

        /*
         * MONTHLY RATE
         *
         * Example:
         *
         * 10% monthly
         *
         * 10 / 100 / 30
         *
         * = 0.003333333...
         *
         * For 100,000 principal:
         *
         * daily interest =
         * 100,000 * 0.003333...
         * = 333.33
         */
        if (
                "MONTHLY".equalsIgnoreCase(
                        rateType
                )
        ) {

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

        /*
         * ANNUAL RATE
         *
         * Example:
         *
         * 24% annual
         *
         * 24 / 100 / 12 / 30
         */
        if (
                "ANNUAL".equalsIgnoreCase(
                        rateType
                )
        ) {

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

        log.warn(
                "Unknown interestRateType '{}' for loan {}. Treating rate as MONTHLY.",
                rateType,
                loan.getId()
        );

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