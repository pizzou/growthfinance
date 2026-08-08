
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
        // FIND PAYMENT SCHEDULE
        // ============================================================

        List<Payment> loanPayments =
                paymentRepo.findByLoanId(
                        loanId
                );

        /*
         * Continue an installment that already has a partial payment.
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
                            .paid(
                                    false
                            )
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
        // DETERMINE INTEREST START
        // ============================================================

        LocalDateTime interestStartDateTime =
                determineInterestStartDateTime(
                        installment,
                        loan,
                        now
                );

        if (interestStartDateTime.isAfter(now)) {

            interestStartDateTime =
                    now;
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

        // ============================================================
        // INTEREST DAYS
        // ============================================================

        /*
         * IMPORTANT BUSINESS RULE:
         *
         * First payment after disbursement:
         *
         * 10 minutes  -> 1 interest day
         * 10 hours    -> 1 interest day
         * 23 hours    -> 1 interest day
         * 24 hours    -> 1 additional interest day
         * 48 hours    -> 2 additional interest days
         *
         * Once a payment has already been made in the current cycle,
         * we do NOT charge another day until a complete 24-hour
         * period has elapsed from the previous interest calculation.
         */

        boolean firstInterestCalculation =
                installment.getInterestCalculationDate() == null
                        && amountPaidSoFar <= 0.0
                        && interestAlreadyPaid <= 0.0
                        && existingCycleInterestDue <= 0.0;

        long elapsedDays;

        if (firstInterestCalculation) {

            /*
             * Day 1 begins immediately when money is disbursed.
             *
             * Therefore even a payment made a few minutes after
             * disbursement owes one day of interest.
             */
            elapsedDays =
                    Math.max(
                            1L,
                            elapsedHours / 24L
                    );

        } else {

            /*
             * After the first payment, only COMPLETED 24-hour
             * periods generate additional interest.
             *
             * This prevents:
             *
             * Payment 1 -> 1 day
             * Payment 2 ten minutes later -> another day
             *
             * Instead:
             *
             * Payment 1 -> 1 day
             * 10 minutes later -> 0 additional days
             * 24 hours later -> 1 additional day
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

        log.info(
                "Interest calculation: loanId={}, principal={}, rate={}, dailyRate={}, start={}, now={}, elapsedHours={}, interestDays={}, newInterest={}",
                loan.getId(),
                currentBalance,
                loan.getInterestRate(),
                dailyRate,
                interestStartDateTime,
                now,
                elapsedHours,
                elapsedDays,
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

        // ============================================================
        // REMAINING INTEREST BEFORE PAYMENT
        // ============================================================

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
         * The installment cycle is complete once its accrued interest
         * has been completely paid OR the entire loan has been paid.
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
         * After this payment, the interest clock starts from the
         * exact payment timestamp.
         *
         * Therefore another payment ten minutes later does NOT
         * generate another interest day.
         *
         * After 24 hours, one additional interest day is generated.
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
                        + ", penalty: "
                        + newPenalty
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
         * If this payment cycle has already calculated interest,
         * continue from the exact previous calculation timestamp.
         */
        if (
                installment.getInterestCalculationDate() != null
        ) {

            return installment.getInterestCalculationDate();
        }

        /*
         * Legacy fallback for an installment that has payment data
         * but no interest timestamp.
         */
        if (
                installment.getPaidDate() != null
                        && safe(
                        installment.getAmountPaid()
                ) > 0.0
        ) {

            if (installment.getCreatedAt() != null) {

                return installment.getCreatedAt();
            }
        }

        /*
         * Existing loan fallback.
         */
        if (
                loan.getLastPaymentDate() != null
        ) {

            return loan.getLastPaymentDate()
                    .atStartOfDay();
        }

        /*
         * MOST IMPORTANT:
         *
         * Interest starts from the exact disbursement timestamp.
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
         * MONTHLY:
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
         * ANNUAL:
         *
         * annual percentage
         * -> monthly
         * -> daily
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
