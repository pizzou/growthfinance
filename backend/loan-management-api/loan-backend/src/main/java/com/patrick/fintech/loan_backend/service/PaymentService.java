
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
            Double amount,
            String method,
            String txnId,
            String channel,
            String notes,
            User recordedBy
    ) {

        if (loanId == null) {
            throw new IllegalArgumentException("Loan ID is required");
        }

        if (amount == null || amount <= 0) {
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

            /*
             * IMPORTANT:
             *
             * When a previous installment was completed, the new
             * installment must inherit the latest interest timestamp.
             *
             * We do NOT use loan.lastPaymentDate.atStartOfDay()
             * because that would destroy the exact 24-hour interest
             * calculation.
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
                                    loan.getNextInstallmentAmount()
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

        /*
         * IMPORTANT:
         *
         * Due date itself has ZERO penalty days.
         *
         * 08/08/2026 -> 0
         * 09/08/2026 -> 1
         * 10/08/2026 -> 2
         */
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

        // ============================================================
        // EXISTING VALUES
        // ============================================================

        double amountPaidSoFar =
                safe(
                        installment.getAmountPaid()
                );

        double interestAlreadyPaid =
                safe(
                        installment.getInterestComponent()
                );

        interestAlreadyPaid =
                Math.max(
                        0.0,
                        roundMoney(
                                interestAlreadyPaid
                        )
                );

        double penaltyAlreadyRecorded =
                safe(
                        installment.getPenalty()
                );

        penaltyAlreadyRecorded =
                Math.max(
                        0.0,
                        roundMoney(
                                penaltyAlreadyRecorded
                        )
                );

        double existingCycleInterestDue =
                safe(
                        installment.getCycleInterestDue()
                );

        existingCycleInterestDue =
                Math.max(
                        0.0,
                        roundMoney(
                                existingCycleInterestDue
                        )
                );

        // ============================================================
        // CURRENT PRINCIPAL
        // ============================================================

        double currentBalance =
                safe(
                        loan.getOutstandingBalance()
                );

        currentBalance =
                Math.max(
                        0.0,
                        roundMoney(
                                currentBalance
                        )
                );

        if (currentBalance <= 0.0) {

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

        if (interestStartDateTime.isAfter(now)) {
            interestStartDateTime = now;
        }

        // ============================================================
        // EXACT ELAPSED HOURS
        // ============================================================

        long elapsedHours =
                ChronoUnit.HOURS.between(
                        interestStartDateTime,
                        now
                );

        if (elapsedHours < 0) {
            elapsedHours = 0;
        }

        // ============================================================
        // INTEREST DAYS
        // ============================================================

        /*
         * BUSINESS RULE:
         *
         * The first day starts immediately when money is disbursed.
         *
         * Therefore:
         *
         * Disbursed 10:00
         * Paid 10:10 same day
         * = 1 interest day
         *
         * After that:
         *
         * Payment at 10:10
         * Another payment at 10:20
         * = 0 additional days
         *
         * Payment at 10:10 next day
         * = 1 additional day
         *
         * Payment at 10:10 two days later
         * = 2 additional days
         */

        boolean firstInterestCalculation =
                installment.getInterestCalculationDate() == null
                        && amountPaidSoFar <= 0.0
                        && interestAlreadyPaid <= 0.0
                        && existingCycleInterestDue <= 0.0;

        long elapsedDays;

        if (firstInterestCalculation) {

            /*
             * Day one exists immediately after disbursement.
             *
             * Any payment before the first 24 hours therefore
             * still owes one day of interest.
             */
            elapsedDays =
                    Math.max(
                            1L,
                            elapsedHours / 24L
                    );

        } else {

            /*
             * After the first calculation, only complete 24-hour
             * periods generate new interest.
             */
            elapsedDays =
                    elapsedHours / 24L;
        }

        // ============================================================
        // DAILY INTEREST RATE
        // ============================================================

        double dailyRate =
                calculateDailyRate(
                        loan
                );

        // ============================================================
        // NEW INTEREST
        // ============================================================

        double newlyAccruedInterest =
                roundMoney(
                        currentBalance
                                * dailyRate
                                * elapsedDays
                );

        newlyAccruedInterest =
                Math.max(
                        0.0,
                        newlyAccruedInterest
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

        // ============================================================
        // TOTAL INTEREST
        // ============================================================

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

        double remainingInterestBeforePayment =
                roundMoney(
                        Math.max(
                                0.0,
                                totalCycleInterestDue
                                        - interestAlreadyPaid
                        )
                );

        // ============================================================
        // DAILY PENALTY
        // ============================================================

        /*
         * PENALTY IS COMPLETELY INDEPENDENT FROM INTEREST.
         *
         * Due date: 08/08 -> 0 days
         * 09/08 -> 1 day
         * 10/08 -> 2 days
         *
         * Existing rule:
         *
         * 2% per 30 days
         *
         * Therefore:
         *
         * daily penalty rate = 2% / 30
         */
        double dailyPenaltyRate =
                0.02 / 30.0;

        /*
         * Penalty is calculated against the outstanding principal.
         *
         * This means that if the borrower reduces principal,
         * future daily penalty also reduces.
         */
        double calculatedPenalty =
                0.0;

        if (
                daysLate > 0
        ) {

            calculatedPenalty =
                    roundMoney(
                            currentBalance
                                    * dailyPenaltyRate
                                    * daysLate
                    );
        }

        /*
         * Only charge the new penalty that has accumulated since
         * the previous payment.
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

        // ============================================================
        // PAYMENT ALLOCATION
        // ============================================================

        /*
         * PAYMENT ORDER:
         *
         * 1. Penalty
         * 2. Interest
         * 3. Principal
         *
         * Interest calculation itself remains independent of penalty.
         */

        double amountAfterPenalty =
                roundMoney(
                        Math.max(
                                0.0,
                                amount
                                        - newPenalty
                        )
                );

        double interestPaid =
                roundMoney(
                        Math.min(
                                amountAfterPenalty,
                                remainingInterestBeforePayment
                        )
                );

        double amountAvailableForPrincipal =
                roundMoney(
                        Math.max(
                                0.0,
                                amountAfterPenalty
                                        - interestPaid
                        )
                );

        double principalPaid =
                roundMoney(
                        Math.min(
                                amountAvailableForPrincipal,
                                currentBalance
                        )
                );

        // ============================================================
        // NEW PRINCIPAL BALANCE
        // ============================================================

        double newBalance =
                roundMoney(
                        Math.max(
                                0.0,
                                currentBalance
                                        - principalPaid
                        )
                );

        // ============================================================
        // CUMULATIVE VALUES
        // ============================================================

        double totalInterestPaid =
                roundMoney(
                        interestAlreadyPaid
                                + interestPaid
                );

        double existingPrincipalPaid =
                safe(
                        installment.getPrincipalComponent()
                );

        double totalPrincipalPaid =
                roundMoney(
                        existingPrincipalPaid
                                + principalPaid
                );

        double remainingInterestAfterPayment =
                roundMoney(
                        Math.max(
                                0.0,
                                totalCycleInterestDue
                                        - totalInterestPaid
                        )
                );

        // ============================================================
        // COMPLETION
        // ============================================================

        boolean interestCovered =
                remainingInterestAfterPayment <= 0.01;

        boolean fullyPaidOff =
                newBalance <= 0.01;

        boolean cycleCompleted =
                interestCovered
                        || fullyPaidOff;

        // ============================================================
        // UPDATE PAYMENT
        // ============================================================

        double newAmountPaid =
                roundMoney(
                        amountPaidSoFar
                                + amount
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
         * This is the ONLY timestamp needed to control the
         * 24-hour interest clock.
         *
         * After payment:
         *
         * now = new interest starting point.
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
                        || installment.getPaymentReference()
                        .isBlank()
        ) {

            installment.setPaymentReference(
                    generateRef(
                            loan
                    )
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

        // ============================================================
        // UPDATE LOAN
        // ============================================================

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
                    0.0
            );

        } else {

            /*
             * A payment does NOT stop daily interest.
             *
             * The reduced principal continues accruing interest from
             * the new interestCalculationDate.
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
                        + ", elapsed hours: "
                        + elapsedHours
                        + ", interest days: "
                        + elapsedDays
                        + ", daily interest: "
                        + roundMoney(
                        currentBalance * dailyRate
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

        // ============================================================
        // EMAIL
        // ============================================================

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

        // ============================================================
        // SMS
        // ============================================================

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

        // ============================================================
        // OFFICER NOTIFICATION
        // ============================================================

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

        // ============================================================
        // ACCOUNTING
        // ============================================================

        try {

            accountingService.postPaymentReceived(
                    installment,
                    amount,
                    principalPaid,
                    interestPaid,
                    newPenalty
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

            /*
             * Due date = 08/08
             *
             * 08/08 -> 0
             * 09/08 -> 1
             * 10/08 -> 2
             *
             * This calculation is intentionally independent from
             * interestCalculationDate.
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
         * If this exact installment already has an interest clock,
         * use it.
         */
        if (
                installment.getInterestCalculationDate() != null
        ) {

            return installment.getInterestCalculationDate();
        }

        /*
         * SECOND:
         *
         * Find the most recent payment interest timestamp on the loan.
         *
         * This is important when a previous installment was completed.
         *
         * We must NOT use:
         *
         * loan.getLastPaymentDate().atStartOfDay()
         *
         * because that loses the exact payment time.
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
         * Start from exact disbursement timestamp.
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

    private double calculateDailyRate(
            Loan loan
    ) {

        double rate =
                safe(
                        loan.getInterestRate()
                );

        if (rate <= 0.0) {

            log.warn(
                    "Loan {} has zero or missing interest rate. interestRate={}",
                    loan.getId(),
                    loan.getInterestRate()
            );

            return 0.0;
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
         * = 0.0033333333 per day
         */
        if (
                "MONTHLY".equalsIgnoreCase(
                        rateType
                )
        ) {

            return rate
                    / 100.0
                    / 30.0;
        }

        /*
         * ANNUAL RATE
         *
         * annual -> monthly -> daily
         */
        if (
                "ANNUAL".equalsIgnoreCase(
                        rateType
                )
        ) {

            return rate
                    / 100.0
                    / 12.0
                    / 30.0;
        }

        log.warn(
                "Unknown interestRateType '{}' for loan {}. Treating rate as MONTHLY.",
                rateType,
                loan.getId()
        );

        return rate
                / 100.0
                / 30.0;
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

    private double safe(
            Double value
    ) {

        if (
                value == null
                        || Double.isNaN(value)
                        || Double.isInfinite(value)
        ) {

            return 0.0;
        }

        return value;
    }

    private double roundMoney(
            double value
    ) {

        if (
                Double.isNaN(value)
                        || Double.isInfinite(value)
        ) {

            return 0.0;
        }

        return Math.round(
                value * 100.0
        ) / 100.0;
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
