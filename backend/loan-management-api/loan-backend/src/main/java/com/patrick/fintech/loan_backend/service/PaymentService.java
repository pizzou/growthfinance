package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
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

    /**
     * Records a payment against a loan.
     *
     * PAYMENT ORDER:
     *
     * 1. Penalty
     * 2. Current cycle's unpaid interest
     * 3. Principal
     *
     * IMPORTANT:
     *
     * Monthly interest is calculated ONCE for a monthly cycle.
     *
     * Example:
     *
     * Principal = 5,000,000
     * Monthly rate = 10%
     * Monthly interest = 500,000
     *
     * Payment 1 = 2,000,000
     *
     *     Interest  = 500,000
     *     Principal = 1,500,000
     *     Balance   = 3,500,000
     *
     * Payment 2 = 1,000,000 on same cycle
     *
     *     Interest  = 0
     *     Principal = 1,000,000
     *     Balance   = 2,500,000
     *
     * Payment 3 = 500,000 on same cycle
     *
     *     Interest  = 0
     *     Principal = 500,000
     *     Balance   = 2,000,000
     *
     * NO additional monthly interest is charged during the same cycle.
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

        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }

        /*
         * Keep Double.
         *
         * Normalize incoming payment to two decimal places.
         */
        amount = roundMoney(amount);

        Loan loan =
                loanRepo.findById(loanId)
                        .orElseThrow(() ->
                                new RuntimeException(
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
                        && !loan.getOrganization()
                        .getId()
                        .equals(
                                recordedBy.getOrganization().getId()
                        )
        ) {
            throw new RuntimeException("Access denied");
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
        // TRANSACTION IDEMPOTENCY
        // ============================================================

        if (txnId != null && !txnId.isBlank()) {

            Optional<Payment> existingPayment =
                    paymentRepo
                            .findByOrganization_IdAndTransactionId(
                                    loan.getOrganization().getId(),
                                    txnId
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
                            "Duplicate payment transaction {} detected for loan {}. Returning payment {}.",
                            txnId,
                            loanId,
                            existing.getId()
                    );

                    return existing;
                }

                throw new IllegalStateException(
                        "Transaction ID "
                                + txnId
                                + " has already been used for another payment."
                );
            }
        }


        // ============================================================
        // FIND CURRENT PAYMENT CYCLE
        // ============================================================

        List<Payment> loanPayments =
                paymentRepo.findByLoanId(loanId);

        LocalDate today =
                LocalDate.now();

        /*
         * VERY IMPORTANT:
         *
         * First look for an installment that has already received
         * money and whose due date has NOT passed.
         *
         * This is what prevents:
         *
         * Payment 1 -> installment 1
         * Payment 2 -> installment 2
         *
         * on the same day/month.
         *
         * Instead:
         *
         * Payment 1 -> installment 1
         * Payment 2 -> installment 1
         * Payment 3 -> installment 1
         */
        Optional<Payment> existingCurrentCycle =
                loanPayments.stream()
                        .filter(p ->
                                p.getAmountPaid() != null
                                        && p.getAmountPaid() > 0
                                        && p.getDueDate() != null
                                        && !p.getDueDate().isBefore(today)
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
         * If there is no payment already made in the current cycle,
         * use the first unpaid installment.
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

            /*
             * CONTINUE THE SAME MONTHLY CYCLE.
             */
            installment =
                    existingCurrentCycle.get();

            log.info(
                    "Continuing existing payment cycle. Loan={}, installment={}, dueDate={}",
                    loanId,
                    installment.getInstallmentNumber(),
                    installment.getDueDate()
            );

        } else if (unpaidInstallment.isPresent()) {

            installment =
                    unpaidInstallment.get();

        } else {

            /*
             * No schedule installment exists.
             *
             * Create one dynamically.
             */
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
                            .dueDate(dueDate)
                            .amountPaid(0.0)
                            .principalComponent(0.0)
                            .interestComponent(0.0)
                            .penalty(0.0)
                            .paid(false)
                            .build();
        }


        // ============================================================
        // DUE DATE / LATE STATUS
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
                today.isAfter(cycleDueDate);

        int daysLate =
                isLate
                        ? (int) ChronoUnit.DAYS.between(
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


        /*
         * interestComponent represents ACTUAL interest paid after
         * the first payment has been recorded.
         *
         * Before any payment:
         *
         *     amountPaid = 0
         *
         * Therefore the schedule's projected interest is ignored.
         */
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
        // CURRENT OUTSTANDING PRINCIPAL
        // ============================================================

        double currentBalance =
                safe(
                        loan.getOutstandingBalance()
                );

        currentBalance =
                Math.max(
                        0.0,
                        roundMoney(currentBalance)
                );


        // ============================================================
        // DETERMINE CYCLE-OPENING PRINCIPAL
        // ============================================================

        /*
         * THIS IS VERY IMPORTANT.
         *
         * If this is the first payment:
         *
         *     cycleOpeningBalance = currentBalance
         *
         * If this is a later payment in the same cycle:
         *
         *     cumulativePrincipalPaid =
         *
         *         amountPaid
         *         - interestPaid
         *         - penalty
         *
         * Therefore:
         *
         *     cycleOpeningBalance =
         *
         *         currentBalance
         *         + cumulativePrincipalPaid
         *
         * This reconstructs the principal balance at the beginning
         * of the monthly cycle without changing the database model.
         */

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
                        .equalsIgnoreCase(rateType)
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

        /*
         * Interest is ALWAYS calculated from the balance at the
         * beginning of this cycle.
         *
         * It is NOT recalculated from the reduced balance after
         * every payment.
         */
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

        if (isLate && daysLate > 0) {

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
        // PAYMENT AVAILABLE AFTER PENALTY
        // ============================================================

        double netAvailable =
                roundMoney(
                        Math.max(
                                0.0,
                                amount - newPenalty
                        )
                );


        // ============================================================
        // INTEREST FIRST
        // ============================================================

        double interestPaid =
                Math.min(
                        netAvailable,
                        remainingInterest
                );

        interestPaid =
                roundMoney(
                        interestPaid
                );


        // ============================================================
        // PRINCIPAL SECOND
        // ============================================================

        double principalPaid =
                Math.min(
                        Math.max(
                                0.0,
                                netAvailable
                                        - interestPaid
                        ),
                        currentBalance
                );

        principalPaid =
                roundMoney(
                        principalPaid
                );


        // ============================================================
        // NEW OUTSTANDING BALANCE
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
        // CUMULATIVE CYCLE VALUES
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
        // CYCLE COMPLETION
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


        /*
         * Store ACTUAL cumulative interest paid.
         */
        installment.setInterestComponent(
                totalInterestPaid
        );


        /*
         * Store ACTUAL cumulative principal paid.
         */
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
                txnId
        );

        installment.setChannel(
                channel
        );

        installment.setNotes(
                notes
        );


        /*
         * A cycle is complete when its interest has been fully paid.
         *
         * IMPORTANT:
         *
         * Even if paid=true, recordPayment() will still find this
         * installment again during the same cycle because it searches
         * for amountPaid > 0 and dueDate >= today.
         */
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
                        || installment.getPaymentReference().isBlank()
        ) {

            installment.setPaymentReference(
                    generateRef(loan)
            );
        }


        installment =
                paymentRepo.save(
                        installment
                );


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
        // LOAN STATUS / NEXT CYCLE
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

            loan.setNextInstallmentAmount(
                    0.0
            );

        } else {

            loan.setStatus(
                    LoanStatus.ACTIVE
            );


            /*
             * Current cycle interest has been satisfied.
             *
             * The next cycle starts at the next monthly due date.
             */
            if (interestCovered) {

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
                 * Interest is still unpaid.
                 *
                 * Stay in the current cycle.
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
        // AUDIT
        // ============================================================

        audit(
                loan.getOrganization(),
                recordedBy,
                "PAYMENT_RECORDED",
                "PAYMENT",
                installment.getId().toString(),
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
        // WEBHOOK
        // ============================================================

        webhookService.dispatch(
                loan.getOrganization(),
                "PAYMENT_MADE",
                loan
        );


        // ============================================================
        // ACCOUNTING
        // ============================================================

        /*
         * ONLY post this transaction's allocation.
         *
         * Do NOT pass cumulative principal/interest here.
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
                                () ->
                                        new RuntimeException(
                                                "Loan not found"
                                        )
                        );

        if (
                loan.getOrganization() == null
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
    // MARK OVERDUE LOANS
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


    /**
     * Money rounding while keeping Double.
     */
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