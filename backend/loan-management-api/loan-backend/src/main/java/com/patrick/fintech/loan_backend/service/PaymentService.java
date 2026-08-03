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
     * Records a payment against a flexible loan.
     *
     * PAYMENT ALLOCATION RULE
     * -----------------------
     *
     * For each monthly cycle:
     *
     *   1. Interest for the cycle is calculated once.
     *   2. Payments first satisfy the remaining interest for that cycle.
     *   3. Once the cycle interest is fully satisfied, additional money
     *      reduces principal.
     *   4. Multiple payments during the same cycle do NOT create another
     *      month's interest charge.
     *   5. If a payment is smaller than the interest due, the unpaid
     *      interest remains outstanding and the cycle remains open.
     *   6. The next cycle begins only after the current cycle's interest
     *      has been fully covered.
     *
     * Example:
     *
     * Monthly interest = 100
     *
     * Payment 1 = 40
     *   Interest = 40
     *   Principal = 0
     *   Remaining interest = 60
     *
     * Payment 2 = 80
     *   Interest = 60
     *   Principal = 20
     *   Remaining interest = 0
     *
     * Payment 3 = 200
     *   Interest = 0
     *   Principal = 200
     *
     * The borrower is therefore never charged the same monthly interest
     * twice simply because they made multiple payments.
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

        amount = round(amount);

       
        Loan loan = loanRepo.findById(loanId)
            .orElseThrow(() ->
                new RuntimeException("Loan not found: " + loanId)
            );

     
        if (
            recordedBy != null
            && loan.getOrganization() != null
            && recordedBy.getOrganization() != null
            && !loan.getOrganization().getId()
                .equals(recordedBy.getOrganization().getId())
        ) {
            throw new RuntimeException("Access denied");
        }

      

        if (
            loan.getStatus() != LoanStatus.ACTIVE
            && loan.getStatus() != LoanStatus.OVERDUE
        ) {
            throw new RuntimeException(
                "Loan is not active (status: " + loan.getStatus() + ")"
            );
        }

       
        if (txnId != null && !txnId.isBlank()) {

            Optional<Payment> existingPayment =
                paymentRepo.findByOrganization_IdAndTransactionId(
                    loan.getOrganization().getId(),
                    txnId
                );

            if (existingPayment.isPresent()) {
                Payment existing = existingPayment.get();

               
                if (
                    existing.getLoan() != null
                    && existing.getLoan().getId().equals(loanId)
                ) {
                    log.info(
                        "Duplicate payment transaction {} detected for loan {}. Returning existing payment {}.",
                        txnId,
                        loanId,
                        existing.getId()
                    );

                    return existing;
                }

                throw new IllegalStateException(
                    "Transaction ID " + txnId +
                    " has already been used for another payment."
                );
            }
        }

       

        List<Payment> loanPayments =
            paymentRepo.findByLoanId(loanId);

        Optional<Payment> nextInstallmentOpt =
            loanPayments.stream()
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

        boolean isLate =
            LocalDate.now().isAfter(cycleDueDate);

        int daysLate =
            isLate
                ? (int) ChronoUnit.DAYS.between(
                    cycleDueDate,
                    LocalDate.now()
                )
                : 0;


        Payment installment = nextInstallmentOpt.orElse(null);

        if (installment == null) {

            int nextNumber =
                loanPayments.size() + 1;

            installment = Payment.builder()
                .loan(loan)
                .organization(loan.getOrganization())
                .installmentNumber(nextNumber)
                .dueDate(cycleDueDate)
                .amountPaid(0.0)
                .principalComponent(0.0)
                .interestComponent(0.0)
                .penalty(0.0)
                .build();
        }

        
        double amountPaidSoFarThisCycle =
            installment.getAmountPaid() != null
                ? installment.getAmountPaid()
                : 0.0;

        boolean cycleAlreadyReceivedARealPayment =
            amountPaidSoFarThisCycle > 0.0;

        double interestAlreadyPaid =
            (cycleAlreadyReceivedARealPayment
                && installment.getInterestComponent() != null)
                ? installment.getInterestComponent()
                : 0.0;

        interestAlreadyPaid =
            Math.max(0.0, round(interestAlreadyPaid));

        // ------------------------------------------------------------
        // 9. Calculate monthly interest for THIS cycle
        // ------------------------------------------------------------

        double balance =
            loan.getOutstandingBalance() != null
                ? loan.getOutstandingBalance()
                : 0.0;

        balance = Math.max(0.0, round(balance));

        double rate =
            loan.getInterestRate() != null
                ? loan.getInterestRate()
                : 0.0;

        String rateType =
            loan.getInterestRateType() != null
                ? loan.getInterestRateType()
                : "MONTHLY";

        double monthlyRate;

        if ("MONTHLY".equalsIgnoreCase(rateType)) {

            monthlyRate = rate / 100.0;

        } else {

            /*
             * Existing architecture treats non-monthly rates as annual
             * rates and converts them to a monthly rate.
             */
            monthlyRate =
                rate / 100.0 / 12.0;
        }

        /*
         * Monthly interest is based on the principal outstanding at
         * the beginning of the cycle.
         *
         * Since principal is not reduced until the cycle interest is
         * satisfied, repeated partial payments do not cause the system
         * to recalculate a fresh interest amount against a changed
         * balance within the same cycle.
         */
        double monthlyInterest =
            round(balance * monthlyRate);

        /*
         * Only the UNPAID portion of this cycle's interest remains due.
         */
        double remainingInterest =
            Math.max(
                0.0,
                round(monthlyInterest - interestAlreadyPaid)
            );

        // ------------------------------------------------------------
        // 10. Calculate penalty
        // ------------------------------------------------------------

        /*
         * Preserve your existing penalty calculation.
         *
         * 2% per 30 days late, prorated by the number of days late.
         *
         * However, because the borrower may make multiple payments in
         * the same overdue cycle, we do NOT want to charge the same
         * penalty repeatedly on every payment.
         *
         * Therefore the penalty already recorded against this cycle
         * is deducted from the newly calculated penalty.
         */
        double calculatedPenalty = 0.0;

        if (isLate && daysLate > 0) {

            calculatedPenalty =
                round(
                    amount
                    * 0.02
                    * daysLate
                    / 30.0
                );
        }

        double penaltyAlreadyPaid =
            installment.getPenalty() != null
                ? installment.getPenalty()
                : 0.0;

        penaltyAlreadyPaid =
            Math.max(
                0.0,
                round(penaltyAlreadyPaid)
            );

        /*
         * Only charge newly calculated penalty.
         */
        double penalty =
            Math.max(
                0.0,
                round(
                    calculatedPenalty
                    - penaltyAlreadyPaid
                )
            );

        // ------------------------------------------------------------
        // 11. Remove penalty from cash available for interest/principal
        // ------------------------------------------------------------

        double netAvailable =
            round(
                Math.max(
                    0.0,
                    amount - penalty
                )
            );

        // ------------------------------------------------------------
        // 12. Allocate payment
        // ------------------------------------------------------------

        /*
         * FIRST: remaining monthly interest.
         */
        double interestPaid =
            Math.min(
                netAvailable,
                remainingInterest
            );

        interestPaid =
            round(interestPaid);

        /*
         * THEN: whatever remains reduces principal.
         */
        double principalPaid =
            Math.min(
                Math.max(
                    0.0,
                    netAvailable - interestPaid
                ),
                balance
            );

        principalPaid =
            round(principalPaid);

        // ------------------------------------------------------------
        // 13. New principal balance
        // ------------------------------------------------------------

        double newBalance =
            round(
                Math.max(
                    0.0,
                    balance - principalPaid
                )
            );

        // ------------------------------------------------------------
        // 14. Determine total interest paid for this cycle
        // ------------------------------------------------------------

        double totalInterestPaidThisCycle =
            round(
                interestAlreadyPaid
                + interestPaid
            );

        /*
         * The cycle is interest-covered when the monthly interest has
         * been completely satisfied.
         */
        boolean interestCovered =
            totalInterestPaidThisCycle
                >= monthlyInterest - 0.01;

        boolean fullyPaidOff =
            newBalance <= 0.01;

        // 15. Update payment installment
        // ------------------------------------------------------------

        double oldAmountPaid =
            installment.getAmountPaid() != null
                ? installment.getAmountPaid()
                : 0.0;

        double newAmountPaid =
            round(
                oldAmountPaid + amount
            );

        double oldPenalty =
            installment.getPenalty() != null
                ? installment.getPenalty()
                : 0.0;

        double totalPenalty =
            round(
                oldPenalty + penalty
            );

        installment.setAmountPaid(
            newAmountPaid
        );

        /*
         * IMPORTANT:
         *
         * Do not overwrite the previous interest component.
         * Accumulate it because several payments can belong to the
         * same monthly cycle.
         */
        installment.setInterestComponent(
            totalInterestPaidThisCycle
        );

        /*
         * Principal is also accumulated across multiple payments
         * against the same cycle — but same caveat as interestAlreadyPaid
         * above: on the first real payment, installment.getPrincipalComponent()
         * still holds the schedule generator's PROJECTED figure, not an
         * actually-paid amount, so it must not be added to.
         */
        double oldPrincipal =
            (cycleAlreadyReceivedARealPayment
                && installment.getPrincipalComponent() != null)
                ? installment.getPrincipalComponent()
                : 0.0;

        installment.setPrincipalComponent(
            round(
                oldPrincipal + principalPaid
            )
        );

        installment.setPenalty(
            totalPenalty
        );

        installment.setOutstandingAfter(
            newBalance
        );

        installment.setLate(
            isLate || installment.isLate()
        );

        installment.setDaysLate(
            Math.max(
                installment.getDaysLate() != null
                    ? installment.getDaysLate()
                    : 0,
                daysLate
            )
        );

        installment.setPaymentMethod(method);
        installment.setTransactionId(txnId);
        installment.setChannel(channel);
        installment.setNotes(notes);

        /*
         * A cycle is completed once its interest has been satisfied,
         * or the entire loan has been paid.
         */
        boolean cycleCompleted =
            interestCovered || fullyPaidOff;

        installment.setPaid(
            cycleCompleted
        );

        installment.setPaidDate(
            LocalDate.now()
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
            paymentRepo.save(installment);

        // ------------------------------------------------------------
        // 16. Update loan
        // ------------------------------------------------------------

        double oldTotalPaid =
            loan.getTotalPaid() != null
                ? loan.getTotalPaid()
                : 0.0;

        loan.setTotalPaid(
            round(
                oldTotalPaid + amount
            )
        );

        loan.setOutstandingBalance(
            newBalance
        );

        loan.setLastPaymentDate(
            LocalDate.now()
        );

        // ------------------------------------------------------------
        // 17. Loan status and next cycle
        // ------------------------------------------------------------

        if (fullyPaidOff) {

            loan.setStatus(
                LoanStatus.PAID
            );

            /*
             * Remove all projected future installments that are no
             * longer applicable because the loan has been fully paid.
             */
            Long currentInstallmentId =
                installment.getId();

            List<Payment> stillPending =
                paymentRepo.findByLoanId(loanId)
                    .stream()
                    .filter(
                        p ->
                            !Boolean.TRUE.equals(p.getPaid())
                            && (
                                p.getId() == null
                                || !p.getId().equals(
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

        } else {

            loan.setStatus(
                LoanStatus.ACTIVE
            );

            /*
             * Only advance to the next monthly cycle once the current
             * cycle's interest has been fully satisfied.
             */
            if (interestCovered) {

                loan.setNextDueDate(
                    cycleDueDate.plusMonths(1)
                );
            } else {

                /*
                 * Interest is still outstanding.
                 *
                 * Keep the current due date so the borrower remains
                 * in the same cycle.
                 */
                loan.setNextDueDate(
                    cycleDueDate
                );
            }
        }

        loanRepo.save(loan);

        // ------------------------------------------------------------
        // 18. Audit
        // ------------------------------------------------------------

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

        // ------------------------------------------------------------
        // 19. Notifications
        // ------------------------------------------------------------

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

        // ------------------------------------------------------------
        // 20. Webhook
        // ------------------------------------------------------------

        webhookService.dispatch(
            loan.getOrganization(),
            "PAYMENT_MADE",
            loan
        );

        // ------------------------------------------------------------
        // 21. Accounting
        // ------------------------------------------------------------

        /*
         * VERY IMPORTANT:
         *
         * PaymentService has already determined the exact allocation.
         *
         * AccountingService must NOT recalculate interest.
         *
         * It receives:
         *
         *   amount
         *   principalPaid
         *   interestPaid
         *   penalty
         */
        accountingService.postPaymentReceived(
            installment,
            amount,
            principalPaid,
            interestPaid,
            penalty
        );

        return installment;
    }


    // ================================================================
    // GET LOAN PAYMENT SCHEDULE
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
            !loan.getOrganization()
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

    /**
     * Nightly job:
     *
     * Finds unpaid installments whose due date has passed and marks
     * their loans as overdue.
     */
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

        for (Payment p : overduePayments) {

            Loan loan = p.getLoan();

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
                            p.getDueDate(),
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

                loanRepo.save(loan);
            }
        }
    }


    // ================================================================
    // HELPERS
    // ================================================================

    private double round(double value) {
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
            + System.currentTimeMillis()
            % 100000;
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