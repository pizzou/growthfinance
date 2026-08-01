
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import com.patrick.fintech.loan_backend.util.MoneyMath;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    private final LoanClassificationService loanClassificationService;
    private final PaymentTransactionRepository paymentTransactionRepo;


    /*
     * ============================================================
     * RECORD PAYMENT
     * ============================================================
     */

    @Transactional
    public Payment recordPayment(
            Long loanId,
            BigDecimal amount,
            String method,
            String txnId,
            String channel,
            String notes,
            User recordedBy) {

        /*
         * --------------------------------------------------------
         * Validate amount
         * --------------------------------------------------------
         */

        amount = money(amount);

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }


        /*
         * --------------------------------------------------------
         * Transaction reference
         * --------------------------------------------------------
         */

        String transactionReference =
                txnId != null && !txnId.isBlank()
                        ? txnId.trim()
                        : "PAYTX-" + UUID.randomUUID();


        /*
         * --------------------------------------------------------
         * Load loan
         * --------------------------------------------------------
         */

        Loan loan = loanRepo.findById(loanId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Loan not found: " + loanId
                        ));


        /*
         * --------------------------------------------------------
         * Organization security
         * --------------------------------------------------------
         */

        if (recordedBy != null
                && recordedBy.getOrganization() != null
                && loan.getOrganization() != null
                && !loan.getOrganization()
                        .getId()
                        .equals(recordedBy.getOrganization().getId())) {

            throw new RuntimeException("Access denied");
        }


        /*
         * --------------------------------------------------------
         * Duplicate transaction protection
         * --------------------------------------------------------
         */

        if (paymentTransactionRepo
                .findByOrganization_IdAndTransactionReference(
                        loan.getOrganization().getId(),
                        transactionReference)
                .isPresent()) {

            throw new IllegalStateException(
                    "A payment with transaction reference "
                            + transactionReference
                            + " has already been recorded"
            );
        }


        /*
         * --------------------------------------------------------
         * Loan status validation
         * --------------------------------------------------------
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
         * --------------------------------------------------------
         * Find current installment
         * --------------------------------------------------------
         */

        Optional<Payment> nextInstallmentOpt =
                paymentRepo.findByLoanId(loanId)
                        .stream()
                        .filter(p ->
                                !Boolean.TRUE.equals(p.getPaid()))
                        .filter(p -> p.getDueDate() != null)
                        .min(
                                Comparator.comparing(
                                        Payment::getDueDate
                                )
                        );


        /*
         * --------------------------------------------------------
         * Determine payment cycle
         * --------------------------------------------------------
         */

        LocalDate cycleDueDate =
                nextInstallmentOpt
                        .map(Payment::getDueDate)
                        .orElse(
                                loan.getNextDueDate() != null
                                        ? loan.getNextDueDate()
                                        : LocalDate.now()
                        );


        /*
         * --------------------------------------------------------
         * Calculate lateness
         * --------------------------------------------------------
         */

        LocalDate today = LocalDate.now();

        boolean isLate = today.isAfter(cycleDueDate);

        int daysLate = isLate
                ? (int) ChronoUnit.DAYS.between(
                        cycleDueDate,
                        today)
                : 0;


        /*
         * --------------------------------------------------------
         * Late penalty
         *
         * Current policy:
         * 2% monthly prorated by days late.
         *
         * IMPORTANT:
         * This is retained from your existing business rule.
         * If your institution has a different penalty policy,
         * change it centrally rather than in this service.
         * --------------------------------------------------------
         */

        BigDecimal penalty;

        if (isLate && daysLate > 0) {

            penalty = money(
                    amount
                            .multiply(new BigDecimal("0.02"))
                            .multiply(
                                    BigDecimal.valueOf(daysLate)
                            )
                            .divide(
                                    new BigDecimal("30"),
                                    MoneyMath.SCALE,
                                    MoneyMath.ROUNDING
                            )
            );

        } else {

            penalty = MoneyMath.ZERO;
        }


        /*
         * --------------------------------------------------------
         * Loan balance
         * --------------------------------------------------------
         */

        BigDecimal balance =
                money(
                        loan.getOutstandingBalance()
                                != null
                                ? loan.getOutstandingBalance()
                                : BigDecimal.ZERO
                );


        /*
         * --------------------------------------------------------
         * Prevent overpayment from creating negative balance
         * --------------------------------------------------------
         */

        if (balance.signum() < 0) {
            balance = MoneyMath.ZERO;
        }


        /*
         * --------------------------------------------------------
         * Interest rate
         * --------------------------------------------------------
         */

        BigDecimal rate =
                money(
                        loan.getInterestRate() != null
                                ? loan.getInterestRate()
                                : BigDecimal.ZERO
                );

        String rateType =
                loan.getInterestRateType() != null
                        ? loan.getInterestRateType()
                        : "MONTHLY";


        /*
         * --------------------------------------------------------
         * Convert rate to monthly rate
         *
         * MONTHLY:
         *      rate / 100
         *
         * ANNUAL:
         *      rate / 1200
         * --------------------------------------------------------
         */

        BigDecimal monthlyRate;

        if ("MONTHLY".equalsIgnoreCase(rateType)) {

            monthlyRate =
                    rate.divide(
                            new BigDecimal("100"),
                            12,
                            MoneyMath.ROUNDING
                    );

        } else {

            monthlyRate =
                    rate.divide(
                            new BigDecimal("1200"),
                            12,
                            MoneyMath.ROUNDING
                    );
        }


        /*
         * --------------------------------------------------------
         * Interest due
         * --------------------------------------------------------
         */

        BigDecimal interestDue =
                money(
                        balance.multiply(monthlyRate)
                );


        /*
         * --------------------------------------------------------
         * Payment allocation
         *
         * Payment:
         *
         * 1. Penalty
         * 2. Interest
         * 3. Principal
         * --------------------------------------------------------
         */

        BigDecimal netAvailable =
                money(
                        amount
                                .subtract(penalty)
                                .max(BigDecimal.ZERO)
                );


        /*
         * Interest paid
         */

        BigDecimal interestPaid =
                netAvailable.min(interestDue);


        /*
         * Remaining amount after interest
         */

        BigDecimal afterInterest =
                netAvailable.subtract(interestPaid);


        /*
         * Principal paid
         */

        BigDecimal principalPaid =
                afterInterest
                        .max(BigDecimal.ZERO)
                        .min(balance);


        /*
         * Remaining balance
         */

        BigDecimal newBalance =
                money(
                        balance
                                .subtract(principalPaid)
                                .max(BigDecimal.ZERO)
                );


        /*
         * --------------------------------------------------------
         * Payment completion
         * --------------------------------------------------------
         */

        BigDecimal oneCent =
                new BigDecimal("0.01");

        boolean interestCovered =
                netAvailable.compareTo(
                        interestDue.subtract(oneCent)
                ) >= 0;


        boolean fullyPaidOff =
                newBalance.compareTo(oneCent) <= 0;


        if (fullyPaidOff) {
            newBalance = MoneyMath.ZERO;
        }


        /*
         * --------------------------------------------------------
         * Get/create installment
         * --------------------------------------------------------
         */

        Payment installment =
                nextInstallmentOpt.orElse(null);


        if (installment == null) {

            int nextNumber =
                    paymentRepo.findByLoanId(loanId)
                            .size() + 1;

            installment =
                    Payment.builder()
                            .loan(loan)
                            .organization(
                                    loan.getOrganization()
                            )
                            .installmentNumber(nextNumber)
                            .dueDate(cycleDueDate)
                            .amountPaid(MoneyMath.ZERO)
                            .build();
        }


        /*
         * --------------------------------------------------------
         * Existing installment totals
         * --------------------------------------------------------
         */

        BigDecimal existingPaid =
                money(
                        installment.getAmountPaid() != null
                                ? installment.getAmountPaid()
                                : BigDecimal.ZERO
                );


        /*
         * --------------------------------------------------------
         * Update installment paid amount
         * --------------------------------------------------------
         */

        installment.setAmountPaid(
                money(existingPaid.add(amount))
        );


        /*
         * --------------------------------------------------------
         * Scheduled amount
         * --------------------------------------------------------
         */

        if (installment.getAmount() == null) {

            installment.setAmount(
                    loan.getNextInstallmentAmount() != null
                            ? loan.getNextInstallmentAmount()
                            : amount
            );
        }


        /*
         * --------------------------------------------------------
         * Installment status
         * --------------------------------------------------------
         */

        boolean installmentCompleted =
                interestCovered || fullyPaidOff;

        installment.setPaid(
                installmentCompleted
        );

        installment.setPaidDate(
                installmentCompleted
                        ? today
                        : null
        );


        /*
         * --------------------------------------------------------
         * Principal aggregate
         * --------------------------------------------------------
         */

        BigDecimal existingPrincipal =
                money(
                        installment.getPrincipalComponent()
                                != null
                                ? installment
                                        .getPrincipalComponent()
                                : BigDecimal.ZERO
                );


        BigDecimal cumulativePrincipal =
                money(
                        existingPrincipal
                                .add(principalPaid)
                );


        /*
         * --------------------------------------------------------
         * Interest aggregate
         * --------------------------------------------------------
         */

        BigDecimal existingInterest =
                money(
                        installment.getInterestComponent()
                                != null
                                ? installment
                                        .getInterestComponent()
                                : BigDecimal.ZERO
                );


        BigDecimal cumulativeInterest =
                money(
                        existingInterest
                                .add(interestPaid)
                );


        /*
         * --------------------------------------------------------
         * Penalty aggregate
         * --------------------------------------------------------
         */

        BigDecimal existingPenalty =
                money(
                        installment.getPenalty() != null
                                ? installment.getPenalty()
                                : BigDecimal.ZERO
                );


        BigDecimal cumulativePenalty =
                money(
                        existingPenalty
                                .add(penalty)
                );


        installment.setPrincipalComponent(
                cumulativePrincipal
        );

        installment.setInterestComponent(
                cumulativeInterest
        );

        installment.setPenalty(
                cumulativePenalty
        );

        installment.setOutstandingAfter(
                newBalance
        );

        installment.setLate(isLate);

        installment.setDaysLate(daysLate);

        installment.setPaymentMethod(method);

        installment.setTransactionId(
                transactionReference
        );

        installment.setChannel(channel);

        installment.setNotes(notes);

        installment.setStatus(
                installmentCompleted
                        ? Payment.PaymentStatus.COMPLETED
                        : Payment.PaymentStatus.PARTIALLY_PAID
        );


        /*
         * --------------------------------------------------------
         * Generate payment reference
         * --------------------------------------------------------
         */

        if (installment.getPaymentReference() == null
                || installment
                        .getPaymentReference()
                        .isBlank()) {

            installment.setPaymentReference(
                    generateRef(loan)
            );
        }


        paymentRepo.save(installment);


        /*
         * ========================================================
         * UPDATE LOAN
         * ========================================================
         */

        BigDecimal oldTotalPaid =
                money(
                        loan.getTotalPaid() != null
                                ? loan.getTotalPaid()
                                : BigDecimal.ZERO
                );


        BigDecimal newTotalPaid =
                money(
                        oldTotalPaid.add(amount)
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
         * --------------------------------------------------------
         * Loan status
         * --------------------------------------------------------
         */

        if (fullyPaidOff) {

            loan.setStatus(
                    LoanStatus.PAID
            );

            loan.setDaysOverdue(0);

        } else {

            /*
             * If the current installment is still overdue,
             * keep the loan overdue.
             */
            if (isLate) {

                loan.setStatus(
                        LoanStatus.OVERDUE
                );

            } else {

                loan.setStatus(
                        LoanStatus.ACTIVE
                );
            }


            /*
             * Interest has been covered.
             *
             * Move the next due date forward one month.
             */
            if (interestCovered) {

                loan.setDaysOverdue(0);

                loan.setNextDueDate(
                        cycleDueDate.plusMonths(1)
                );
            }
        }


        loanRepo.save(loan);


        /*
         * --------------------------------------------------------
         * Loan classification
         * --------------------------------------------------------
         */

        try {

            loanClassificationService.reclassify(
                    loan
            );

        } catch (Exception e) {

            log.warn(
                    "Reclassification failed for loan {}: {}",
                    loan.getId(),
                    e.getMessage()
            );
        }


        /*
         * ========================================================
         * UNAPPLIED PAYMENT
         * ========================================================
         */

        BigDecimal unapplied =
                money(
                        amount
                                .subtract(penalty)
                                .subtract(interestPaid)
                                .subtract(principalPaid)
                                .max(BigDecimal.ZERO)
                );


        /*
         * ========================================================
         * PAYMENT TRANSACTION
         * ========================================================
         *
         * This is the immutable financial transaction record.
         */

        PaymentTransaction transaction =
                PaymentTransaction.builder()
                        .loan(loan)
                        .organization(
                                loan.getOrganization()
                        )
                        .installment(installment)
                        .recordedBy(recordedBy)
                        .transactionReference(
                                transactionReference
                        )
                        .amount(amount)
                        .penaltyComponent(penalty)
                        .interestComponent(
                                interestPaid
                        )
                        .principalComponent(
                                principalPaid
                        )
                        .unappliedAmount(
                                unapplied
                        )
                        .paymentMethod(method)
                        .channel(channel)
                        .notes(notes)
                        .status(
                                PaymentTransaction
                                        .TransactionStatus
                                        .POSTED
                        )
                        .reversed(false)
                        .build();


        transaction =
                paymentTransactionRepo.save(
                        transaction
                );


        /*
         * ========================================================
         * ACCOUNTING
         * ========================================================
         *
         * This must succeed before external notifications.
         *
         * If accounting throws a RuntimeException, the
         * transaction rolls back.
         */

        accountingService.postPaymentReceived(
                transaction
        );


        /*
         * ========================================================
         * AUDIT
         * ========================================================
         */

        audit(
                loan.getOrganization(),
                recordedBy,
                "PAYMENT_RECORDED",
                "PAYMENT",
                transaction.getId().toString(),
                "Payment transaction "
                        + transaction
                                .getTransactionReference()
                        + " of "
                        + amount.toPlainString()
                        + " on loan "
                        + loan.getReferenceNumber()
        );


        /*
         * ========================================================
         * EMAIL
         * ========================================================
         *
         * Existing MailService API accepts Double.
         * This is intentionally kept at the integration boundary.
         */

        try {

            mailService.sendPaymentConfirmation(
                    loan,
                    amount.doubleValue()
            );

        } catch (Exception e) {

            log.warn(
                    "Payment confirmation email failed",
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
                    amount.doubleValue()
            );

        } catch (Exception e) {

            log.warn(
                    "Payment confirmation SMS failed",
                    e
            );
        }


        /*
         * ========================================================
         * IN-APP NOTIFICATION
         * ========================================================
         */

        if (loan.getLoanOfficer() != null
                && (
                    recordedBy == null
                    || !loan.getLoanOfficer()
                            .getId()
                            .equals(recordedBy.getId())
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
                                + amount.toPlainString()
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
                        "In-app notification failed",
                        e
                );
            }
        }


        /*
         * ========================================================
         * WEBHOOK
         * ========================================================
         */

        webhookService.dispatch(
                loan.getOrganization(),
                "PAYMENT_MADE",
                loan
        );


        return installment;
    }


    /*
     * ============================================================
     * LEGACY DOUBLE COMPATIBILITY METHOD
     * ============================================================
     *
     * Existing callers can continue using Double temporarily.
     *
     * New code should use BigDecimal.
     */

    @Transactional
    public Payment recordPayment(
            Long loanId,
            Double amount,
            String method,
            String txnId,
            String channel,
            String notes,
            User recordedBy) {

        if (amount == null) {

            throw new IllegalArgumentException(
                    "Payment amount cannot be null"
            );
        }

        return recordPayment(
                loanId,
                BigDecimal.valueOf(amount),
                method,
                txnId,
                channel,
                notes,
                recordedBy
        );
    }


    /*
     * ============================================================
     * REVERSE PAYMENT
     * ============================================================
     */

    @Transactional
    public PaymentTransaction reversePayment(
            Long loanId,
            Long transactionId,
            String reason,
            User reversedBy) {

        Long orgId =
                reversedBy != null
                        && reversedBy.getOrganization() != null
                        ? reversedBy
                                .getOrganization()
                                .getId()
                        : null;


        /*
         * --------------------------------------------------------
         * Find transaction with organization isolation
         * --------------------------------------------------------
         */

        PaymentTransaction tx;

        if (orgId != null) {

            tx =
                    paymentTransactionRepo
                            .findByOrganization_IdAndId(
                                    orgId,
                                    transactionId
                            )
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Payment transaction not found: "
                                                    + transactionId
                                    ));

        } else {

            tx =
                    paymentTransactionRepo
                            .findById(transactionId)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Payment transaction not found: "
                                                    + transactionId
                                    ));
        }


        /*
         * --------------------------------------------------------
         * Already reversed
         * --------------------------------------------------------
         */

        if (Boolean.TRUE.equals(tx.getReversed())
                || tx.getStatus()
                    == PaymentTransaction
                        .TransactionStatus.REVERSED) {

            throw new IllegalStateException(
                    "Payment transaction is already reversed"
            );
        }


        /*
         * --------------------------------------------------------
         * Loan validation
         * --------------------------------------------------------
         */

        Loan loan = tx.getLoan();

        if (loan == null) {

            throw new IllegalStateException(
                    "Payment transaction has no loan"
            );
        }


        if (!loan.getId().equals(loanId)) {

            throw new IllegalArgumentException(
                    "Payment transaction does not belong to this loan"
            );
        }


        /*
         * --------------------------------------------------------
         * Organization security
         * --------------------------------------------------------
         */

        if (reversedBy != null
                && reversedBy.getOrganization() != null
                && loan.getOrganization() != null
                && !loan.getOrganization()
                        .getId()
                        .equals(
                                reversedBy
                                        .getOrganization()
                                        .getId()
                        )) {

            throw new IllegalStateException(
                    "Access denied"
            );
        }


        /*
         * --------------------------------------------------------
         * Installment
         * --------------------------------------------------------
         */

        Payment installment =
                tx.getInstallment();

        if (installment == null) {

            throw new IllegalStateException(
                    "Payment transaction has no installment"
            );
        }


        /*
         * ========================================================
         * REVERSE ACCOUNTING FIRST
         * ========================================================
         *
         * The accounting reversal must succeed.
         */

        accountingService.reversePayment(
                tx,
                reversedBy != null
                        ? reversedBy.getName()
                        : "SYSTEM",
                reason
        );


        /*
         * ========================================================
         * FINANCIAL RESTORATION
         * ========================================================
         */

        BigDecimal transactionAmount =
                money(
                        tx.getAmount()
                                != null
                                ? tx.getAmount()
                                : BigDecimal.ZERO
                );


        BigDecimal transactionPrincipal =
                money(
                        tx.getPrincipalComponent()
                                != null
                                ? tx.getPrincipalComponent()
                                : BigDecimal.ZERO
                );


        BigDecimal currentBalance =
                money(
                        loan.getOutstandingBalance()
                                != null
                                ? loan.getOutstandingBalance()
                                : BigDecimal.ZERO
                );


        BigDecimal restoredBalance =
                money(
                        currentBalance
                                .add(transactionPrincipal)
                );


        /*
         * Never restore more than the original principal.
         *
         * This protects against corrupted/duplicated reversal
         * data.
         */

        BigDecimal originalPrincipal =
                money(
                        loan.getAmount()
                                != null
                                ? loan.getAmount()
                                : BigDecimal.ZERO
                );


        if (restoredBalance.compareTo(
                originalPrincipal) > 0) {

            restoredBalance =
                    originalPrincipal;
        }


        /*
         * --------------------------------------------------------
         * Restore total paid
         * --------------------------------------------------------
         */

        BigDecimal currentTotalPaid =
                money(
                        loan.getTotalPaid()
                                != null
                                ? loan.getTotalPaid()
                                : BigDecimal.ZERO
                );


        BigDecimal restoredTotalPaid =
                currentTotalPaid
                        .subtract(transactionAmount)
                        .max(BigDecimal.ZERO);


        loan.setTotalPaid(
                money(restoredTotalPaid)
        );

        loan.setOutstandingBalance(
                money(restoredBalance)
        );


        /*
         * --------------------------------------------------------
         * Mark transaction reversed
         * --------------------------------------------------------
         */

        tx.setReversed(true);

        tx.setStatus(
                PaymentTransaction
                        .TransactionStatus
                        .REVERSED
        );

        tx.setReversedAt(
                LocalDateTime.now()
        );

        tx.setReversalReason(
                reason
        );

        tx.setReversalReference(
                "REV-" + UUID.randomUUID()
        );


        /*
         * ========================================================
         * REBUILD INSTALLMENT AGGREGATES
         * ========================================================
         */

        List<PaymentTransaction>
                remainingTransactions =
                paymentTransactionRepo
                        .findByInstallmentIdOrderByCreatedAtAsc(
                                installment.getId()
                        )
                        .stream()
                        .filter(t ->
                                !Boolean.TRUE.equals(
                                        t.getReversed()
                                ))
                        .toList();


        /*
         * --------------------------------------------------------
         * Remaining paid
         * --------------------------------------------------------
         */

        BigDecimal remainingPaid =
                remainingTransactions
                        .stream()
                        .map(t ->
                                money(
                                        t.getAmount()
                                                != null
                                                ? t.getAmount()
                                                : BigDecimal.ZERO
                                ))
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );


        /*
         * --------------------------------------------------------
         * Remaining principal
         * --------------------------------------------------------
         */

        BigDecimal remainingPrincipal =
                remainingTransactions
                        .stream()
                        .map(t ->
                                money(
                                        t.getPrincipalComponent()
                                                != null
                                                ? t.getPrincipalComponent()
                                                : BigDecimal.ZERO
                                ))
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );


        /*
         * --------------------------------------------------------
         * Remaining interest
         * --------------------------------------------------------
         */

        BigDecimal remainingInterest =
                remainingTransactions
                        .stream()
                        .map(t ->
                                money(
                                        t.getInterestComponent()
                                                != null
                                                ? t.getInterestComponent()
                                                : BigDecimal.ZERO
                                ))
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );


        /*
         * --------------------------------------------------------
         * Remaining penalty
         * --------------------------------------------------------
         */

        BigDecimal remainingPenalty =
                remainingTransactions
                        .stream()
                        .map(t ->
                                money(
                                        t.getPenaltyComponent()
                                                != null
                                                ? t.getPenaltyComponent()
                                                : BigDecimal.ZERO
                                ))
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );


        /*
         * --------------------------------------------------------
         * Update installment
         * --------------------------------------------------------
         */

        installment.setAmountPaid(
                money(remainingPaid)
        );

        installment.setPrincipalComponent(
                money(remainingPrincipal)
        );

        installment.setInterestComponent(
                money(remainingInterest)
        );

        installment.setPenalty(
                money(remainingPenalty)
        );

        installment.setOutstandingAfter(
                money(restoredBalance)
        );


        /*
         * --------------------------------------------------------
         * Determine installment status
         * --------------------------------------------------------
         */

        BigDecimal scheduledAmount =
                money(
                        installment.getAmount()
                                != null
                                ? installment.getAmount()
                                : BigDecimal.ZERO
                );


        boolean installmentPaid =
                scheduledAmount.signum() > 0
                        && remainingPaid.compareTo(
                                scheduledAmount
                        ) >= 0;


        installment.setPaid(
                installmentPaid
        );

        installment.setPaidDate(
                installmentPaid
                        ? LocalDate.now()
                        : null
        );


        if (installmentPaid) {

            installment.setStatus(
                    Payment.PaymentStatus
                            .COMPLETED
            );

        } else if (remainingPaid.signum() > 0) {

            installment.setStatus(
                    Payment.PaymentStatus
                            .PARTIALLY_PAID
            );

        } else {

            installment.setStatus(
                    Payment.PaymentStatus
                            .PENDING
            );
        }


        /*
         * --------------------------------------------------------
         * Restore latest transaction reference
         * --------------------------------------------------------
         */

        installment.setTransactionId(
                remainingTransactions.isEmpty()
                        ? null
                        : remainingTransactions
                                .get(
                                    remainingTransactions.size()
                                            - 1
                                )
                                .getTransactionReference()
        );


        /*
         * ========================================================
         * RESTORE LOAN STATUS
         * ========================================================
         */

        if (restoredBalance.signum() == 0) {

            loan.setStatus(
                    LoanStatus.PAID
            );

            loan.setDaysOverdue(0);

        } else {

            boolean overdue =
                    installment.getDueDate() != null
                            && LocalDate.now()
                                    .isAfter(
                                            installment
                                                    .getDueDate()
                                    );

            loan.setStatus(
                    overdue
                            ? LoanStatus.OVERDUE
                            : LoanStatus.ACTIVE
            );


            if (overdue) {

                int days =
                        (int) ChronoUnit.DAYS.between(
                                installment.getDueDate(),
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

            } else {

                loan.setDaysOverdue(0);
            }
        }


        /*
         * --------------------------------------------------------
         * Restore next due date
         * --------------------------------------------------------
         */

        if (installmentPaid
                && installment.getDueDate() != null) {

            loan.setNextDueDate(
                    installment
                            .getDueDate()
                            .plusMonths(1)
            );

        } else {

            loan.setNextDueDate(
                    installment.getDueDate()
            );
        }


        /*
         * --------------------------------------------------------
         * Restore last payment date
         * --------------------------------------------------------
         */

        loan.setLastPaymentDate(
                remainingTransactions.isEmpty()
                        ? null
                        : remainingTransactions
                                .get(
                                    remainingTransactions.size()
                                            - 1
                                )
                                .getCreatedAt()
                                .toLocalDate()
        );


        /*
         * --------------------------------------------------------
         * Persist
         * --------------------------------------------------------
         */

        paymentRepo.save(
                installment
        );

        loanRepo.save(
                loan
        );

        paymentTransactionRepo.save(
                tx
        );


        /*
         * --------------------------------------------------------
         * Reclassify
         * --------------------------------------------------------
         */

        try {

            loanClassificationService.reclassify(
                    loan
            );

        } catch (Exception e) {

            log.warn(
                    "Reclassification failed after "
                            + "payment reversal for loan {}: {}",
                    loan.getId(),
                    e.getMessage()
            );
        }


        /*
         * --------------------------------------------------------
         * Audit
         * --------------------------------------------------------
         */

        audit(
                loan.getOrganization(),
                reversedBy,
                "PAYMENT_REVERSED",
                "PAYMENT_TRANSACTION",
                transactionId.toString(),
                "Reversed payment transaction "
                        + tx.getTransactionReference()
                        + " on loan "
                        + loan.getReferenceNumber()
                        + (
                            reason != null
                                && !reason.isBlank()
                                ? ": " + reason
                                : ""
                        )
        );


        /*
         * --------------------------------------------------------
         * Webhook
         * --------------------------------------------------------
         */

        try {

            webhookService.dispatch(
                    loan.getOrganization(),
                    "PAYMENT_REVERSED",
                    loan
            );

        } catch (Exception e) {

            log.warn(
                    "Payment reversal webhook failed "
                            + "for loan {}",
                    loan.getId(),
                    e
            );
        }


        return tx;
    }


    /*
     * ============================================================
     * GET LOAN PAYMENT SCHEDULE
     * ============================================================
     */

    public List<Payment> getLoanSchedule(
            Long loanId,
            Long orgId) {

        Loan loan =
                loanRepo.findById(loanId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Loan not found"
                                ));


        if (loan.getOrganization() == null
                || !loan.getOrganization()
                        .getId()
                        .equals(orgId)) {

            throw new RuntimeException(
                    "Access denied"
            );
        }


        return paymentRepo.findByLoanId(
                loanId
        );
    }


    /*
     * ============================================================
     * MARK OVERDUE LOANS
     * ============================================================
     */

    @Transactional
    public void markOverdueLoans(
            Long orgId) {

        LocalDate today =
                LocalDate.now();


        List<Payment> overduePayments =
                paymentRepo
                        .findByOrganization_IdAndPaidFalseAndDueDateBefore(
                                orgId,
                                today
                        );


        for (Payment payment : overduePayments) {

            if (payment.getLoan() == null) {
                continue;
            }


            Loan loan =
                    payment.getLoan();


            if (loan.getStatus()
                    == LoanStatus.ACTIVE) {

                loan.setStatus(
                        LoanStatus.OVERDUE
                );


                if (payment.getDueDate() != null) {

                    int days =
                            (int) ChronoUnit.DAYS.between(
                                    payment.getDueDate(),
                                    today
                            );


                    int existingDays =
                            loan.getDaysOverdue() != null
                                    ? loan.getDaysOverdue()
                                    : 0;


                    loan.setDaysOverdue(
                            Math.max(
                                    existingDays,
                                    days
                            )
                    );
                }


                loanRepo.save(
                        loan
                );


                try {

                    loanClassificationService
                            .reclassify(loan);

                } catch (Exception e) {

                    log.warn(
                            "Reclassification failed "
                                    + "for loan {}: {}",
                            loan.getId(),
                            e.getMessage()
                    );
                }
            }
        }
    }


    /*
     * ============================================================
     * PAYMENT REFERENCE
     * ============================================================
     */

    private String generateRef(
            Loan loan) {

        return "PAY-"
                + loan.getReferenceNumber()
                + "-"
                + System.currentTimeMillis()
                + "-"
                + UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase();
    }


    /*
     * ============================================================
     * MONEY NORMALIZATION
     * ============================================================
     *
     * Centralizes financial rounding.
     */

    private BigDecimal money(
            BigDecimal value) {

        if (value == null) {
            return MoneyMath.ZERO;
        }

        return MoneyMath.amount(value);
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
            String desc) {

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
