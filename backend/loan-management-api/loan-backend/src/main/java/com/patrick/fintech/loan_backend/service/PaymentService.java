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

    /**
     * Records a payment against a loan.
     *
     * PAYMENT ALLOCATION RULE:
     *
     * 1. A monthly interest obligation is established once per cycle.
     * 2. The first payment covers interest first.
     * 3. If the first payment is less than the interest due, NO principal
     *    is reduced.
     * 4. Subsequent payments against the same cycle continue paying the
     *    remaining interest.
     * 5. Only after the cycle's interest is fully covered can the remaining
     *    payment amount reduce principal.
     * 6. The monthly interest amount is NOT recalculated after every
     *    partial payment.
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
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        Loan loan = loanRepo.findById(loanId)
            .orElseThrow(() ->
                new RuntimeException("Loan not found: " + loanId)
            );

        /*
         * Organization security.
         */
        if (recordedBy != null
            && !loan.getOrganization().getId().equals(
                recordedBy.getOrganization().getId()
            )) {

            throw new RuntimeException("Access denied");
        }

        /*
         * A loan must be active or overdue to receive a payment.
         */
        if (loan.getStatus() != LoanStatus.ACTIVE
            && loan.getStatus() != LoanStatus.OVERDUE) {

            throw new RuntimeException(
                "Loan is not active (status: " + loan.getStatus() + ")"
            );
        }

        /*
         * Prevent duplicate gateway/webhook transactions.
         *
         * A transaction ID must be unique within an organization.
         */
        if (txnId != null && !txnId.isBlank()) {

            Optional<Payment> existingPayment =
                paymentRepo.findByOrganization_IdAndTransactionId(
                    loan.getOrganization().getId(),
                    txnId
                );

            if (existingPayment.isPresent()) {
                log.info(
                    "Ignoring duplicate payment transaction {} for organization {}",
                    txnId,
                    loan.getOrganization().getId()
                );

                return existingPayment.get();
            }
        }

        /*
         * Find the current unpaid cycle.
         *
         * IMPORTANT:
         *
         * We do NOT create a new cycle for every payment.
         *
         * If the borrower makes:
         *
         *   Payment 1 = 10,000
         *   Payment 2 = 15,000
         *   Payment 3 = 10,000
         *
         * while the same monthly cycle remains unpaid, all three payments
         * belong to the same Payment record.
         */
        Optional<Payment> nextInstallmentOpt =
            paymentRepo.findByLoanId(loanId)
                .stream()
                .filter(p -> !Boolean.TRUE.equals(p.getPaid()))
                .min(
                    Comparator.comparing(
                        Payment::getDueDate,
                        Comparator.nullsLast(Comparator.naturalOrder())
                    )
                );

        LocalDate cycleDueDate =
            nextInstallmentOpt
                .map(Payment::getDueDate)
                .orElse(
                    loan.getNextDueDate() != null
                        ? loan.getNextDueDate()
                        : LocalDate.now()
                );

        /*
         * Late calculation.
         */
        boolean isLate = LocalDate.now().isAfter(cycleDueDate);

        int daysLate = isLate
            ? (int) ChronoUnit.DAYS.between(
                cycleDueDate,
                LocalDate.now()
            )
            : 0;

        /*
         * Penalty is calculated on this payment.
         *
         * The penalty is deducted before allocating the remaining cash
         * toward interest and principal.
         */
        double penalty =
            isLate
                ? round(amount * 0.02 * daysLate / 30.0)
                : 0.0;

        double netAvailable =
            round(Math.max(0.0, amount - penalty));

        /*
         * Current principal outstanding.
         */
        double balance =
            loan.getOutstandingBalance() != null
                ? loan.getOutstandingBalance()
                : 0.0;

        if (balance < 0) {
            balance = 0;
        }

        /*
         * Get or create the current cycle.
         */
        Payment installment;

        if (nextInstallmentOpt.isPresent()) {

            installment = nextInstallmentOpt.get();

        } else {

            int nextNumber =
                paymentRepo.findByLoanId(loanId).size() + 1;

            installment = Payment.builder()
                .loan(loan)
                .organization(loan.getOrganization())
                .installmentNumber(nextNumber)
                .dueDate(cycleDueDate)
                .amountPaid(0.0)
                .principalComponent(0.0)
                .interestComponent(0.0)
                .penalty(0.0)
                .cycleInterestDue(0.0)
                .cycleInterestRemaining(0.0)
                .paid(false)
                .status(Payment.PaymentStatus.PENDING)
                .build();
        }

        /*
         * -------------------------------------------------------------
         * ESTABLISH MONTHLY INTEREST ONCE
         * -------------------------------------------------------------
         *
         * If this is the first payment against the cycle, calculate the
         * monthly interest using the principal outstanding at the beginning
         * of the cycle.
         *
         * Once stored in cycleInterestDue, this number does NOT change for
         * subsequent partial payments in the same cycle.
         */
        double cycleInterestDue =
            installment.getCycleInterestDue() != null
                ? installment.getCycleInterestDue()
                : 0.0;

        double cycleInterestRemaining =
            installment.getCycleInterestRemaining() != null
                ? installment.getCycleInterestRemaining()
                : 0.0;

        boolean firstPaymentOfCycle =
            cycleInterestDue <= 0.0;

        if (firstPaymentOfCycle) {

            double rate =
                loan.getInterestRate() != null
                    ? loan.getInterestRate()
                    : 0.0;

            String rateType =
                loan.getInterestRateType() != null
                    ? loan.getInterestRateType()
                    : "MONTHLY";

            /*
             * Your existing business rule:
             *
             * MONTHLY = rate / 100
             *
             * Other rate types are converted to monthly.
             */
            double monthlyRate;

            if ("MONTHLY".equalsIgnoreCase(rateType)) {

                monthlyRate = rate / 100.0;

            } else {

                monthlyRate = rate / 100.0 / 12.0;
            }

            /*
             * Monthly interest is calculated from the outstanding principal
             * at the beginning of the cycle.
             */
            cycleInterestDue =
                round(balance * monthlyRate);

            cycleInterestRemaining =
                cycleInterestDue;

            installment.setCycleInterestDue(cycleInterestDue);
            installment.setCycleInterestRemaining(
                cycleInterestRemaining
            );
        }

        /*
         * -------------------------------------------------------------
         * APPLY PAYMENT
         * -------------------------------------------------------------
         *
         * FIRST:
         *
         *     penalty
         *
         * Then:
         *
         *     outstanding monthly interest
         *
         * Then:
         *
         *     principal
         */
        double interestPaid =
            round(
                Math.min(
                    netAvailable,
                    Math.max(0.0, cycleInterestRemaining)
                )
            );

        double remainingAfterInterest =
            round(netAvailable - interestPaid);

        double principalPaid =
            round(
                Math.min(
                    Math.max(0.0, remainingAfterInterest),
                    balance
                )
            );

        double unusedAmount =
            round(
                Math.max(
                    0.0,
                    remainingAfterInterest - principalPaid
                )
            );

        /*
         * This should normally only happen when the borrower pays more
         * than the total outstanding principal + interest.
         *
         * We do not silently lose that money.
         */
        if (unusedAmount > 0.01) {

            log.warn(
                "Payment {} exceeds current loan obligation by {} for loan {}",
                amount,
                unusedAmount,
                loan.getReferenceNumber()
            );
        }

        /*
         * New remaining interest for this cycle.
         */
        double newInterestRemaining =
            round(
                Math.max(
                    0.0,
                    cycleInterestRemaining - interestPaid
                )
            );

        /*
         * New principal balance.
         */
        double newBalance =
            round(
                Math.max(
                    0.0,
                    balance - principalPaid
                )
            );

        /*
         * A cycle is considered satisfied once all monthly interest has
         * been paid.
         *
         * The borrower does NOT have to pay principal in order for the
         * cycle's interest to be considered satisfied.
         */
        boolean interestCovered =
            newInterestRemaining <= 0.01;

        boolean fullyPaidOff =
            newBalance <= 0.01
            && interestCovered;

        /*
         * -------------------------------------------------------------
         * UPDATE PAYMENT/CYCLE
         * -------------------------------------------------------------
         */

        double previousAmountPaid =
            installment.getAmountPaid() != null
                ? installment.getAmountPaid()
                : 0.0;

        installment.setAmountPaid(
            round(previousAmountPaid + amount)
        );

        /*
         * These fields represent the allocation of THIS payment.
         */
        installment.setPrincipalComponent(principalPaid);
        installment.setInterestComponent(interestPaid);

        /*
         * Penalty is cumulative for the cycle.
         */
        double previousPenalty =
            installment.getPenalty() != null
                ? installment.getPenalty()
                : 0.0;

        installment.setPenalty(
            round(previousPenalty + penalty)
        );

        installment.setCycleInterestDue(
            cycleInterestDue
        );

        installment.setCycleInterestRemaining(
            newInterestRemaining
        );

        installment.setOutstandingAfter(newBalance);

        installment.setLate(isLate);

        installment.setDaysLate(daysLate);

        installment.setPaymentMethod(method);

        installment.setTransactionId(txnId);

        installment.setChannel(channel);

        installment.setNotes(notes);

        installment.setPaidDate(LocalDate.now());

        /*
         * The cycle becomes completed only when its interest obligation
         * has been fully covered, or the loan is fully paid.
         */
        installment.setPaid(
            interestCovered || fullyPaidOff
        );

        installment.setStatus(
            interestCovered || fullyPaidOff
                ? Payment.PaymentStatus.COMPLETED
                : Payment.PaymentStatus.PARTIALLY_PAID
        );

        /*
         * Only generate the reference if this cycle does not already have
         * one.
         */
        if (installment.getPaymentReference() == null
            || installment.getPaymentReference().isBlank()) {

            installment.setPaymentReference(
                generateRef(loan)
            );
        }

        /*
         * Keep track of who recorded the latest payment.
         */
        installment.setRecordedBy(recordedBy);

        /*
         * Save the payment/cycle before accounting so it has its ID.
         */
        installment = paymentRepo.save(installment);

        /*
         * -------------------------------------------------------------
         * UPDATE LOAN
         * -------------------------------------------------------------
         */

        double oldTotalPaid =
            loan.getTotalPaid() != null
                ? loan.getTotalPaid()
                : 0.0;

        loan.setTotalPaid(
            round(oldTotalPaid + amount)
        );

        loan.setOutstandingBalance(newBalance);

        loan.setLastPaymentDate(LocalDate.now());

        if (fullyPaidOff) {

            /*
             * Loan completely paid.
             */
            loan.setStatus(LoanStatus.PAID);

            /*
             * Remove future projected installments because they are no
             * longer owed.
             */
            Long installmentId = installment.getId();

            List<Payment> stillPending =
                paymentRepo.findByLoanId(loanId)
                    .stream()
                    .filter(p ->
                        !Boolean.TRUE.equals(p.getPaid())
                        && !p.getId().equals(installmentId)
                    )
                    .toList();

            if (!stillPending.isEmpty()) {
                paymentRepo.deleteAll(stillPending);
            }

        } else {

            loan.setStatus(LoanStatus.ACTIVE);

            /*
             * Only move to the next monthly cycle once this cycle's
             * interest has been fully satisfied.
             */
            if (interestCovered) {

                loan.setNextDueDate(
                    cycleDueDate.plusMonths(1)
                );
            }
        }

        loanRepo.save(loan);

        /*
         * -------------------------------------------------------------
         * ACCOUNTING
         * -------------------------------------------------------------
         *
         * IMPORTANT:
         *
         * We pass the CURRENT payment amount and allocation to accounting,
         * not the cumulative installment amount.
         *
         * Otherwise:
         *
         * Payment 1 = 10,000
         * Payment 2 = 15,000
         *
         * could accidentally result in:
         *
         * GL payment 1 = 10,000
         * GL payment 2 = 25,000
         *
         * instead of:
         *
         * GL payment 1 = 10,000
         * GL payment 2 = 15,000
         */
        accountingService.postPaymentReceived(
            installment,
            amount,
            principalPaid,
            interestPaid,
            penalty
        );

        /*
         * -------------------------------------------------------------
         * AUDIT
         * -------------------------------------------------------------
         */

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
                + penalty
        );

        /*
         * -------------------------------------------------------------
         * NOTIFICATIONS
         * -------------------------------------------------------------
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

        if (
            loan.getLoanOfficer() != null
            && (
                recordedBy == null
                || !loan.getLoanOfficer()
                    .getId()
                    .equals(recordedBy.getId())
            )
        ) {

            try {

                notifService.notifyUsers(
                    List.of(loan.getLoanOfficer()),
                    "Payment Received",
                    "A payment of "
                        + loan.getCurrency()
                        + " "
                        + amount
                        + " was recorded on loan "
                        + loan.getReferenceNumber()
                        + (
                            recordedBy != null
                                ? " by " + recordedBy.getName()
                                : " (automatic)"
                        )
                        + ".",
                    "success",
                    "/dashboard/loans/" + loan.getId()
                );

            } catch (Exception e) {

                log.warn(
                    "In-app notification failed",
                    e
                );
            }
        }

        /*
         * Webhook/event notification.
         */
        webhookService.dispatch(
            loan.getOrganization(),
            "PAYMENT_MADE",
            loan
        );

        return installment;
    }

    /**
     * Returns the payment schedule for a loan.
     */
    public List<Payment> getLoanSchedule(
        Long loanId,
        Long orgId
    ) {

        Loan loan =
            loanRepo.findById(loanId)
                .orElseThrow(
                    () -> new RuntimeException(
                        "Loan not found"
                    )
                );

        if (
            !loan.getOrganization()
                .getId()
                .equals(orgId)
        ) {
            throw new RuntimeException(
                "Access denied"
            );
        }

        return paymentRepo.findByLoanId(loanId);
    }

    /**
     * Nightly job:
     *
     * Marks loans with unpaid cycles as overdue.
     */
    @Transactional
    public void markOverdueLoans(Long orgId) {

        List<Payment> overduePayments =
            paymentRepo
                .findByOrganization_IdAndPaidFalseAndDueDateBefore(
                    orgId,
                    LocalDate.now()
                );

        for (Payment payment : overduePayments) {

            Loan loan = payment.getLoan();

            if (loan.getStatus() == LoanStatus.ACTIVE) {

                loan.setStatus(
                    LoanStatus.OVERDUE
                );

                int days =
                    (int) ChronoUnit.DAYS.between(
                        payment.getDueDate(),
                        LocalDate.now()
                    );

                int currentDaysOverdue =
                    loan.getDaysOverdue() != null
                        ? loan.getDaysOverdue()
                        : 0;

                loan.setDaysOverdue(
                    Math.max(
                        currentDaysOverdue,
                        days
                    )
                );

                loanRepo.save(loan);
            }
        }
    }

    private double round(double value) {

        return Math.round(value * 100.0) / 100.0;
    }

    private String generateRef(Loan loan) {

        return "PAY-"
            + loan.getReferenceNumber()
            + "-"
            + System.currentTimeMillis() % 100000;
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