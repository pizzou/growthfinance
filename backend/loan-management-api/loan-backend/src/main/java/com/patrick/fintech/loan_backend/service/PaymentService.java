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
            throw new IllegalArgumentException(
                    "Loan ID is required"
            );
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

            throw new RuntimeException(
                    "Access denied"
            );
        }

        // ============================================================
        // IDEMPOTENCY
        // ============================================================

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
                paymentRepo.findByLoanId(
                        loanId
                );

        /*
         * Continue an installment that already has a partial payment.
         *
         * A completed installment is never reused.
         */
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

        /*
         * Otherwise find the next unpaid scheduled installment.
         */
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
        // SELECT PAYMENT CYCLE
        // ============================================================

        if (existingCurrentCycle.isPresent()) {

            installment =
                    existingCurrentCycle.get();

            /*
             * If this installment somehow has no interest timestamp,
             * initialise it safely now using the loan's real
             * disbursement timestamp.
             */
            if (installment.getInterestCalculationDate() == null) {

                LocalDateTime initialInterestTime =
                        determineInitialInterestStart(
                                installment,
                                loan,
                                now
                        );

                installment.setInterestCalculationDate(
                        initialInterestTime
                );
            }

            log.info(
                    "Continuing existing payment cycle. loanId={}, installment={}, paymentId={}, interestStart={}",
                    loanId,
                    installment.getInstallmentNumber(),
                    installment.getId(),
                    installment.getInterestCalculationDate()
            );

        } else if (unpaidInstallment.isPresent()) {

            installment =
                    unpaidInstallment.get();

            /*
             * Existing scheduled installment may have been created
             * before the new timestamp-based interest logic existed.
             *
             * Initialise its interest clock correctly.
             */
            if (installment.getInterestCalculationDate() == null) {

                LocalDateTime initialInterestTime =
                        determineInitialInterestStart(
                                installment,
                                loan,
                                now
                        );

                installment.setInterestCalculationDate(
                        initialInterestTime
                );
            }

            log.info(
                    "Using unpaid scheduled installment. loanId={}, installment={}, paymentId={}, interestStart={}",
                    loanId,
                    installment.getInstallmentNumber(),
                    installment.getId(),
                    installment.getInterestCalculationDate()
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
             * A new payment cycle starts its interest clock at the
             * exact time the previous payment/cycle was evaluated.
             *
             * For a brand-new loan this will be the disbursement
             * timestamp through determineInitialInterestStart().
             */
            LocalDateTime initialInterestStart =
                    determineInitialInterestStart(
                            null,
                            loan,
                            now
                    );

            /*
             * If this is a new cycle after a previous payment,
             * the most reliable timestamp available in the current
             * Payment model is the previous completed payment's
             * interestCalculationDate.
             *
             * If none exists, fall back to disbursement.
             */
            Optional<Payment> latestCompletedPayment =
                    loanPayments.stream()
                            .filter(
                                    p ->
                                            Boolean.TRUE.equals(
                                                    p.getPaid()
                                            )
                            )
                            .filter(
                                    p ->
                                            p.getInterestCalculationDate() != null
                            )
                            .max(
                                    Comparator.comparing(
                                            Payment::getInterestCalculationDate
                                    )
                            );

            if (latestCompletedPayment.isPresent()) {

                initialInterestStart =
                        latestCompletedPayment
                                .get()
                                .getInterestCalculationDate();
            }

            /*
             * If no timestamp is available at all, use now rather
             * than midnight. This prevents accidental charging of
             * an entire partial day.
             */
            if (initialInterestStart == null) {
                initialInterestStart = now;
            }

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
                                    initialInterestStart
                            )
                            .paid(
                                    false
                            )
                            .status(
                                    Payment.PaymentStatus.PENDING
                            )
                            .build();

            log.info(
                    "Creating new payment cycle. loanId={}, installment={}, interestStart={}",
                    loanId,
                    nextNumber,
                    initialInterestStart
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

        boolean isLate =
                today.isAfter(
                        cycleDueDate
                );

        int daysLate =
                isLate
                        ? (int)
                        ChronoUnit.DAYS.between(
                                cycleDueDate,
                                today
                        )
                        : 0;

        // ============================================================
        // EXISTING PAYMENT VALUES
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
        // CURRENT PRINCIPAL BALANCE
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
        // EXACT INTEREST START TIMESTAMP
        // ============================================================

        LocalDateTime interestStartDateTime =
                determineInterestStartDateTime(
                        installment,
                        loan,
                        now
                );

        /*
         * Never allow a future timestamp to create negative elapsed
         * time.
         */
        if (interestStartDateTime.isAfter(now)) {

            log.warn(
                    "Interest start timestamp is in the future. loanId={}, start={}, now={}. Using now.",
                    loanId,
                    interestStartDateTime,
                    now
            );

            interestStartDateTime =
                    now;

            installment.setInterestCalculationDate(
                    now
            );
        }

        // ============================================================
        // EXACT ELAPSED TIME
        // ============================================================

        long elapsedHours =
                ChronoUnit.HOURS.between(
                        interestStartDateTime,
                        now
                );

        if (elapsedHours < 0) {
            elapsedHours = 0;
        }

        /*
         * Interest is charged only for completed 24-hour periods.
         *
         * Example:
         *
         * Monday 10:00 -> Tuesday 10:00 = 1 day
         * Monday 10:00 -> Tuesday 09:59 = 0 days
         * Monday 10:00 -> Wednesday 10:00 = 2 days
         */
        long elapsedDays =
                elapsedHours / 24L;

        // ============================================================
        // DAILY INTEREST RATE
        // ============================================================

        double dailyRate =
                calculateDailyRate(
                        loan
                );

        // ============================================================
        // NEW DAILY INTEREST
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

        // ============================================================
        // TOTAL CYCLE INTEREST DUE
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

        /*
         * Interest already paid during this cycle is deducted from
         * total accrued interest.
         */
        double remainingInterestBeforePayment =
                roundMoney(
                        Math.max(
                                0.0,
                                totalCycleInterestDue
                                        - interestAlreadyPaid
                        )
                );

        // ============================================================
        // PENALTY
        // ============================================================

        double calculatedPenalty =
                0.0;

        if (
                isLate
                        && daysLate > 0
        ) {

            /*
             * Existing penalty rule preserved:
             *
             * 2% per 30 days against the current payment amount.
             */
            calculatedPenalty =
                    roundMoney(
                            amount
                                    * 0.02
                                    * daysLate
                                    / 30.0
                    );
        }

        double newPenalty =
                Math.max(
                        0.0,
                        roundMoney(
                                calculatedPenalty
                                        - penaltyAlreadyRecorded
                        )
                );

        // ============================================================
        // NET PAYMENT AFTER PENALTY
        // ============================================================

        double netAvailable =
                roundMoney(
                        Math.max(
                                0.0,
                                amount
                                        - newPenalty
                        )
                );

        // ============================================================
        // INTEREST FIRST
        // ============================================================

        double interestPaid =
                roundMoney(
                        Math.min(
                                netAvailable,
                                remainingInterestBeforePayment
                        )
                );

        // ============================================================
        // PRINCIPAL SECOND
        // ============================================================

        double amountAvailableForPrincipal =
                roundMoney(
                        Math.max(
                                0.0,
                                netAvailable
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

        double totalPenalty =
                roundMoney(
                        penaltyAlreadyRecorded
                                + newPenalty
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

        /*
         * A payment cycle is completed when the current cycle's
         * interest has been fully paid OR the entire loan has been
         * paid off.
         *
         * The exact payment timestamp is stored below so the next
         * cycle begins from this exact time.
         */
        boolean cycleCompleted =
                interestCovered
                        || fullyPaidOff;

        // ============================================================
        // UPDATE PAYMENT
        // ============================================================

        double oldAmountPaid =
                amountPaidSoFar;

        double newAmountPaid =
                roundMoney(
                        oldAmountPaid
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
         * Interest is calculated up to THIS EXACT PAYMENT TIME.
         *
         * The next interest period therefore begins from this
         * exact timestamp.
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

            loan.setStatus(
                    LoanStatus.ACTIVE
            );

            /*
             * Contractual due date remains independent from the
             * daily-interest clock.
             *
             * Paying interest/principal early does not change the
             * fact that interest itself is measured from the exact
             * timestamp.
             */
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
                        + ", completed interest days: "
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
                        + ", penalty: "
                        + newPenalty
                        + ", remaining interest: "
                        + remainingInterestAfterPayment
                        + ", new principal balance: "
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
        // WEBHOOK — PAYMENT_MADE
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

                if (payment.getDueDate() != null) {

                    int days =
                            (int)
                                    ChronoUnit.DAYS.between(
                                            payment.getDueDate(),
                                            LocalDate.now()
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
    }

    // ================================================================
    // DETERMINE EXACT INTEREST START TIMESTAMP
    // ================================================================

    private LocalDateTime determineInterestStartDateTime(
            Payment installment,
            Loan loan,
            LocalDateTime now
    ) {

        /*
         * FIRST PRIORITY:
         *
         * If this payment cycle already has an exact timestamp,
         * use it.
         *
         * This is the most important field for the daily-interest
         * clock.
         */
        if (
                installment != null
                        && installment.getInterestCalculationDate() != null
        ) {

            return installment.getInterestCalculationDate();
        }

        /*
         * SECOND PRIORITY:
         *
         * For a newly disbursed loan, interest begins at the exact
         * disbursement timestamp.
         */
        if (
                loan.getDisbursedAt() != null
        ) {

            return loan.getDisbursedAt();
        }

        /*
         * THIRD PRIORITY:
         *
         * Legacy payment records may have a creation timestamp.
         */
        if (
                installment != null
                        && installment.getCreatedAt() != null
                        && safe(
                        installment.getAmountPaid()
                ) > 0.0
        ) {

            return installment.getCreatedAt();
        }

        /*
         * FOURTH PRIORITY:
         *
         * If no exact timestamp exists, use the loan start date.
         *
         * This is a legacy fallback only.
         */
        if (
                loan.getStartDate() != null
        ) {

            return loan.getStartDate()
                    .atStartOfDay();
        }

        /*
         * FINAL FALLBACK:
         *
         * Never create negative elapsed time.
         */
        return now;
    }

    // ================================================================
    // INITIAL INTEREST START
    // ================================================================

    private LocalDateTime determineInitialInterestStart(
            Payment installment,
            Loan loan,
            LocalDateTime now
    ) {

        /*
         * Existing payment timestamp wins.
         */
        if (
                installment != null
                        && installment.getInterestCalculationDate() != null
        ) {

            return installment.getInterestCalculationDate();
        }

        /*
         * For the first loan cycle, use exact disbursement timestamp.
         */
        if (
                loan.getDisbursedAt() != null
        ) {

            return loan.getDisbursedAt();
        }

        /*
         * Legacy fallback.
         */
        if (
                loan.getStartDate() != null
        ) {

            return loan.getStartDate()
                    .atStartOfDay();
        }

        /*
         * Last resort.
         */
        return now;
    }

    // ================================================================
    // DAILY RATE
    // ================================================================

    private double calculateDailyRate(
            Loan loan
    ) {

        double rate =
                safe(
                        loan.getInterestRate()
                );

        if (rate <= 0.0) {
            return 0.0;
        }

        String rateType =
                loan.getInterestRateType() != null
                        ? loan.getInterestRateType().trim()
                        : "MONTHLY";

        /*
         * MONTHLY
         *
         * Example:
         *
         * 10% monthly
         *
         * 10 / 100 / 30
         *
         * = 0.0033333333 daily
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
         * ANNUAL
         *
         * Annual percentage
         * -> monthly percentage
         * -> daily 30-day rate.
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

        /*
         * Unknown rate type:
         *
         * Preserve existing behavior by treating it as MONTHLY.
         */
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