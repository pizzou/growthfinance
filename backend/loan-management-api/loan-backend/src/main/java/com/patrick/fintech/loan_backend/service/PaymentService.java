package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
                                () ->
                                        new RuntimeException(
                                                "Loan not found: "
                                                        + loanId
                                        )
                        );

        // ============================================================
        // ORGANIZATION SECURITY
        // ============================================================

        if (
                recordedBy != null
                        && loan.getOrganization() != null
                        && recordedBy.getOrganization() != null
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
                                .equals(
                                        loanId
                                )
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
        // CURRENT PAYMENT CYCLE
        // ============================================================

        List<Payment> loanPayments =
                paymentRepo.findByLoanId(
                        loanId
                );

        LocalDate today =
                LocalDate.now();

        Optional<Payment> existingCurrentCycle =
                loanPayments.stream()
                        .filter(
                                p ->
                                        p.getAmountPaid() != null
                                                && p.getAmountPaid() > 0
                                                && p.getDueDate() != null
                                                && !p.getDueDate()
                                                .isBefore(today)
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

        if (existingCurrentCycle.isPresent()) {

            installment =
                    existingCurrentCycle.get();

            log.info(
                    "Continuing current payment cycle. loanId={}, installment={}, dueDate={}",
                    loanId,
                    installment.getInstallmentNumber(),
                    installment.getDueDate()
            );

        } else if (unpaidInstallment.isPresent()) {

            installment =
                    unpaidInstallment.get();

        } else {

            LocalDate dueDate =
                    loan.getNextDueDate() != null
                            ? loan.getNextDueDate()
                            : today;

            int nextNumber =
                    loanPayments.size() + 1;

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
                            .paid(
                                    false
                            )
                            .build();
        }

        // ============================================================
        // DUE DATE
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
        // EXISTING CYCLE VALUES
        // ============================================================

        double amountPaidSoFar =
                safe(
                        installment.getAmountPaid()
                );

        boolean existingCycle =
                amountPaidSoFar > 0.0;

        double interestAlreadyPaid =
                existingCycle
                        ? safe(
                        installment.getInterestComponent()
                )
                        : 0.0;

        interestAlreadyPaid =
                Math.max(
                        0.0,
                        roundMoney(
                                interestAlreadyPaid
                        )
                );

        double penaltyAlreadyRecorded =
                existingCycle
                        ? safe(
                        installment.getPenalty()
                )
                        : 0.0;

        penaltyAlreadyRecorded =
                Math.max(
                        0.0,
                        roundMoney(
                                penaltyAlreadyRecorded
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

        // ============================================================
        // CYCLE OPENING BALANCE
        // ============================================================

        double cumulativePrincipalPaid =
                0.0;

        if (existingCycle) {

            cumulativePrincipalPaid =
                    roundMoney(
                            amountPaidSoFar
                                    - interestAlreadyPaid
                                    - penaltyAlreadyRecorded
                    );

            if (cumulativePrincipalPaid < 0) {
                cumulativePrincipalPaid = 0.0;
            }
        }

        double cycleOpeningBalance =
                roundMoney(
                        currentBalance
                                + cumulativePrincipalPaid
                );

        cycleOpeningBalance =
                Math.max(
                        0.0,
                        cycleOpeningBalance
                );

        // ============================================================
        // INTEREST RATE
        // ============================================================

        double rate =
                safe(
                        loan.getInterestRate()
                );

        String rateType =
                loan.getInterestRateType() != null
                        ? loan.getInterestRateType()
                        : "MONTHLY";

        double monthlyRate;

        if (
                "MONTHLY"
                        .equalsIgnoreCase(
                                rateType
                        )
        ) {

            monthlyRate =
                    rate / 100.0;

        } else {

            monthlyRate =
                    rate / 100.0 / 12.0;
        }

        // ============================================================
        // MONTHLY INTEREST
        // ============================================================

        double monthlyInterest =
                roundMoney(
                        cycleOpeningBalance
                                * monthlyRate
                );

        monthlyInterest =
                Math.max(
                        0.0,
                        monthlyInterest
                );

        // ============================================================
        // REMAINING INTEREST
        // ============================================================

        double remainingInterest =
                Math.max(
                        0.0,
                        roundMoney(
                                monthlyInterest
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
        // NET PAYMENT
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
                                remainingInterest
                        )
                );

        // ============================================================
        // PRINCIPAL SECOND
        // ============================================================

        double principalPaid =
                roundMoney(
                        Math.min(
                                Math.max(
                                        0.0,
                                        netAvailable
                                                - interestPaid
                                ),
                                currentBalance
                        )
                );

        // ============================================================
        // NEW BALANCE
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

        double totalPrincipalPaid =
                roundMoney(
                        cumulativePrincipalPaid
                                + principalPaid
                );

        double totalPenalty =
                roundMoney(
                        penaltyAlreadyRecorded
                                + newPenalty
                );

        // ============================================================
        // COMPLETION
        // ============================================================

        boolean interestCovered =
                totalInterestPaid
                        >= monthlyInterest - 0.01;

        boolean fullyPaidOff =
                newBalance <= 0.01;

        boolean cycleCompleted =
                interestCovered
                        || fullyPaidOff;

        // ============================================================
        // UPDATE INSTALLMENT
        // ============================================================

        double oldAmountPaid =
                safe(
                        installment.getAmountPaid()
                );

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

        installment.setPaid(
                cycleCompleted
        );

        installment.setPaidDate(
                today
        );

        installment.setStatus(
                cycleCompleted
                        ? Payment.PaymentStatus.COMPLETED
                        : Payment.PaymentStatus.PARTIALLY_PAID
        );

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

            if (interestCovered) {

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
                        + " — interest: "
                        + interestPaid
                        + ", principal: "
                        + principalPaid
                        + ", penalty: "
                        + newPenalty
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

            /*
             * IMPORTANT:
             *
             * Do NOT send the complete Loan entity.
             *
             * A Loan entity may contain lazy relationships and
             * circular Hibernate relationships which can make
             * Jackson serialization fail.
             *
             * Instead send a clean webhook-specific payload.
             */

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
                    "totalPrincipalPaid",
                    totalPrincipalPaid
            );

            paymentWebhook.put(
                    "outstandingBalance",
                    newBalance
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

            /*
             * Never undo a successful payment because an outbound
             * webhook failed.
             */

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

            if (
                    loan.getStatus()
                            == LoanStatus.ACTIVE
            ) {

                loan.setStatus(
                        LoanStatus.OVERDUE
                );

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

                loanRepo.save(
                        loan
                );
            }
        }
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