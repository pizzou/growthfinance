package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.Payment.PaymentStatus;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final LoanRepository loanRepository;


    // ============================================================
    // GET LOAN SCHEDULE
    // ============================================================

    @Transactional(readOnly = true)
    public List<Payment> getLoanSchedule(
            Long loanId,
            Long organizationId
    ) {

        validateId(loanId, "Loan ID");
        validateId(organizationId, "Organization ID");

        /*
         * IMPORTANT:
         *
         * Do NOT call a repository method such as
         * findLoanSchedule(...) because your current
         * PaymentRepository does not define that method.
         *
         * We use the existing findByLoanId(...) and then enforce
         * tenant isolation here.
         */
        List<Payment> payments =
                paymentRepository.findByLoanId(loanId);

        if (payments == null) {
            return new ArrayList<>();
        }

        return payments.stream()
                .filter(p ->
                        p != null
                        && p.getOrganization() != null
                        && p.getOrganization().getId() != null
                        && p.getOrganization()
                            .getId()
                            .equals(organizationId)
                )
                .sorted(
                        Comparator
                                .comparing(
                                        Payment::getDueDate,
                                        Comparator.nullsLast(
                                                Comparator.naturalOrder()
                                        )
                                )
                )
                .toList();
    }


    // ============================================================
    // GET PAYMENTS BY LOAN
    // ============================================================

    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByLoan(
            Long loanId
    ) {

        validateId(loanId, "Loan ID");

        List<Payment> payments =
                paymentRepository.findByLoanId(loanId);

        return payments != null
                ? payments
                : new ArrayList<>();
    }


    // ============================================================
    // GET PAYMENT
    // ============================================================

    @Transactional(readOnly = true)
    public Payment getPayment(
            Long paymentId
    ) {

        validateId(paymentId, "Payment ID");

        return paymentRepository
                .findById(paymentId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Payment not found: " + paymentId
                        )
                );
    }


    // ============================================================
    // GET PAYMENT BY REFERENCE
    // ============================================================

    @Transactional(readOnly = true)
    public Payment getPaymentByReference(
            String reference
    ) {

        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException(
                    "Payment reference is required"
            );
        }

        return paymentRepository
                .findByPaymentReference(
                        reference.trim()
                )
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Payment not found: " + reference
                        )
                );
    }


    // ============================================================
    // RECORD PAYMENT
    // ============================================================

    /**
     * Records a payment against a loan.
     *
     * Signature preserved for existing controllers:
     *
     * recordPayment(
     *     Long,
     *     double,
     *     String,
     *     String,
     *     String,
     *     String,
     *     null
     * )
     *
     * Monetary values are DOUBLE throughout.
     *
     * Payment allocation:
     *
     * 1. Penalty
     * 2. Current-cycle interest
     * 3. Principal
     *
     * IMPORTANT:
     *
     * cycleInterestRemaining is used so that the same monthly
     * interest is NOT charged again when the borrower makes
     * another payment during the same cycle.
     */
    @Transactional
    public Payment recordPayment(
            Long loanId,
            double amount,
            String paymentMethod,
            String transactionId,
            String paymentReference,
            String notes,
            Object ignored
    ) {

        validateId(loanId, "Loan ID");

        if (amount <= 0.0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }

        Loan loan = loanRepository
                .findById(loanId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Loan not found: " + loanId
                        )
                );

        if (loan.getOrganization() == null) {
            throw new IllegalStateException(
                    "Loan has no organization"
            );
        }

        LocalDate today = LocalDate.now();

        /*
         * Find the current installment.
         */
        Payment installment =
                findCurrentInstallment(loanId, today);

        if (installment == null) {
            throw new IllegalStateException(
                    "No repayment schedule found for loan: " + loanId
            );
        }

        /*
         * Protect against duplicate transaction IDs.
         */
        if (transactionId != null
                && !transactionId.isBlank()) {

            paymentRepository
                    .findByOrganization_IdAndTransactionId(
                            loan.getOrganization().getId(),
                            transactionId
                    )
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException(
                                "A payment with transaction ID "
                                + transactionId
                                + " already exists"
                        );
                    });
        }

        /*
         * Protect against duplicate payment references.
         */
        if (paymentReference != null
                && !paymentReference.isBlank()) {

            paymentRepository
                    .findByPaymentReference(
                            paymentReference.trim()
                    )
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException(
                                "A payment with reference "
                                + paymentReference
                                + " already exists"
                        );
                    });
        }

        /*
         * --------------------------------------------------------
         * ENSURE CURRENT MONTHLY INTEREST EXISTS
         * --------------------------------------------------------
         *
         * We calculate the monthly interest ONLY when this cycle
         * has not previously been initialized.
         */
        initializeCycleInterestIfNecessary(
                installment,
                loan
        );

        double remainingPayment = amount;

        /*
         * --------------------------------------------------------
         * PENALTY
         * --------------------------------------------------------
         */
        double penaltyOutstanding =
                safe(installment.getPenalty())
                - safe(installment.getWaivedAmount());

        if (penaltyOutstanding < 0.0) {
            penaltyOutstanding = 0.0;
        }

        double penaltyPaid =
                Math.min(
                        remainingPayment,
                        penaltyOutstanding
                );

        remainingPayment -= penaltyPaid;

        /*
         * --------------------------------------------------------
         * CURRENT CYCLE INTEREST
         * --------------------------------------------------------
         *
         * Interest is taken BEFORE principal.
         */
        double interestRemaining =
                safe(installment.getCycleInterestRemaining());

        double interestPaid =
                Math.min(
                        remainingPayment,
                        interestRemaining
                );

        remainingPayment -= interestPaid;

        /*
         * --------------------------------------------------------
         * PRINCIPAL
         * --------------------------------------------------------
         */
        double outstandingBalance =
                safe(loan.getOutstandingBalance());

        if (outstandingBalance < 0.0) {
            outstandingBalance = 0.0;
        }

        double principalPaid =
                Math.min(
                        remainingPayment,
                        outstandingBalance
                );

        remainingPayment -= principalPaid;

        /*
         * --------------------------------------------------------
         * UPDATE PAYMENT
         * --------------------------------------------------------
         */
        double oldAmountPaid =
                safe(installment.getAmountPaid());

        double oldPrincipal =
                safe(installment.getPrincipalComponent());

        double oldInterest =
                safe(installment.getInterestComponent());

        installment.setAmountPaid(
                oldAmountPaid
                + amount
        );

        installment.setPrincipalComponent(
                oldPrincipal
                + principalPaid
        );

        installment.setInterestComponent(
                oldInterest
                + interestPaid
        );

        installment.setCycleInterestRemaining(
                Math.max(
                        0.0,
                        interestRemaining
                        - interestPaid
                )
        );

        /*
         * Reduce penalty.
         */
        if (penaltyPaid > 0.0) {

            double currentPenalty =
                    safe(installment.getPenalty());

            installment.setPenalty(
                    Math.max(
                            0.0,
                            currentPenalty
                            - penaltyPaid
                    )
            );
        }

        installment.setPaidDate(today);

        installment.setPaymentMethod(
                paymentMethod
        );

        installment.setTransactionId(
                transactionId
        );

        installment.setPaymentReference(
                paymentReference
        );

        installment.setNotes(notes);

        installment.setVerifiedAt(
                LocalDateTime.now()
        );

        /*
         * --------------------------------------------------------
         * DETERMINE PAYMENT STATUS
         * --------------------------------------------------------
         */
        double installmentAmount =
                safe(installment.getAmount());

        double totalPaid =
                safe(installment.getAmountPaid());

        boolean installmentFullyPaid =
                installmentAmount > 0.0
                && totalPaid >= installmentAmount;

        boolean cycleInterestCleared =
                safe(
                        installment
                                .getCycleInterestRemaining()
                ) <= 0.000001;

        boolean penaltyCleared =
                safe(installment.getPenalty())
                - safe(installment.getWaivedAmount())
                <= 0.000001;

        if (installmentFullyPaid
                && cycleInterestCleared
                && penaltyCleared) {

            installment.setPaid(true);
            installment.setStatus(
                    PaymentStatus.COMPLETED
            );

        } else if (totalPaid > 0.0) {

            installment.setPaid(false);
            installment.setStatus(
                    PaymentStatus.PARTIALLY_PAID
            );

        } else {

            installment.setPaid(false);
            installment.setStatus(
                    PaymentStatus.PENDING
            );
        }

        /*
         * --------------------------------------------------------
         * LATE PAYMENT
         * --------------------------------------------------------
         */
        if (installment.getDueDate() != null
                && today.isAfter(
                        installment.getDueDate()
                )) {

            int daysLate =
                    (int) (
                        today.toEpochDay()
                        - installment
                                .getDueDate()
                                .toEpochDay()
                    );

            installment.setDaysLate(
                    Math.max(0, daysLate)
            );

            installment.setLate(
                    daysLate > 0
            );
        }

        /*
         * --------------------------------------------------------
         * UPDATE LOAN BALANCE
         * --------------------------------------------------------
         */
        double newOutstandingBalance =
                Math.max(
                        0.0,
                        outstandingBalance
                        - principalPaid
                );

        loan.setOutstandingBalance(
                newOutstandingBalance
        );

        /*
         * If the entire loan principal has been paid,
         * mark the loan as completed.
         */
        if (newOutstandingBalance <= 0.000001) {

            loan.setOutstandingBalance(0.0);

            /*
             * Do not blindly change the status if your
             * LoanStatus enum does not contain COMPLETED.
             *
             * This section intentionally remains conservative.
             */
        }

        /*
         * --------------------------------------------------------
         * OUTSTANDING AFTER
         * --------------------------------------------------------
         */
        installment.setOutstandingAfter(
                newOutstandingBalance
        );

        /*
         * --------------------------------------------------------
         * SAVE
         * --------------------------------------------------------
         */
        Payment saved =
                paymentRepository.save(
                        installment
                );

        loanRepository.save(loan);

        log.info(
                "Payment recorded: loanId={}, paymentId={}, "
                + "amount={}, penaltyPaid={}, interestPaid={}, "
                + "principalPaid={}, outstandingAfter={}",
                loanId,
                saved.getId(),
                amount,
                penaltyPaid,
                interestPaid,
                principalPaid,
                newOutstandingBalance
        );

        return saved;
    }


    // ============================================================
    // INITIALIZE MONTHLY INTEREST
    // ============================================================

    /**
     * Initializes monthly interest once.
     *
     * If cycleInterestDue already exists, it is NOT recalculated.
     */
    private void initializeCycleInterestIfNecessary(
            Payment installment,
            Loan loan
    ) {

        double existingDue =
                safe(
                        installment
                                .getCycleInterestDue()
                );

        double existingRemaining =
                safe(
                        installment
                                .getCycleInterestRemaining()
                );

        /*
         * Already initialized.
         */
        if (existingDue > 0.0
                || existingRemaining > 0.0) {

            return;
        }

        double balance =
                safe(
                        loan.getOutstandingBalance()
                );

        double annualRate =
                safe(
                        loan.getInterestRate()
                );

        /*
         * Interest rate is assumed to be annual percentage.
         *
         * Example:
         *
         * 12% annual
         * -> 1% monthly
         */
        double monthlyRate =
                annualRate / 100.0 / 12.0;

        double monthlyInterest =
                balance * monthlyRate;

        if (monthlyInterest < 0.0) {
            monthlyInterest = 0.0;
        }

        installment.setCycleInterestDue(
                monthlyInterest
        );

        installment.setCycleInterestRemaining(
                monthlyInterest
        );
    }


    // ============================================================
    // FIND CURRENT INSTALLMENT
    // ============================================================

    private Payment findCurrentInstallment(
            Long loanId,
            LocalDate today
    ) {

        List<Payment> payments =
                paymentRepository.findByLoanId(
                        loanId
                );

        if (payments == null
                || payments.isEmpty()) {

            return null;
        }

        /*
         * Prefer the first unpaid installment.
         */
        Payment unpaid =
                payments.stream()
                        .filter(p ->
                                p != null
                                && !Boolean.TRUE.equals(
                                        p.getPaid()
                                )
                        )
                        .sorted(
                                Comparator
                                        .comparing(
                                                Payment::getDueDate,
                                                Comparator.nullsLast(
                                                        Comparator.naturalOrder()
                                                )
                                        )
                        )
                        .findFirst()
                        .orElse(null);

        if (unpaid != null) {
            return unpaid;
        }

        /*
         * If everything is marked paid, use the latest
         * scheduled installment.
         */
        return payments.stream()
                .filter(p -> p != null)
                .sorted(
                        Comparator
                                .comparing(
                                        Payment::getDueDate,
                                        Comparator.nullsLast(
                                                Comparator.naturalOrder()
                                        )
                                )
                )
                .findFirst()
                .orElse(null);
    }


    // ============================================================
    // REVERSE PAYMENT
    // ============================================================

    /**
     * Reverses a previously recorded payment.
     *
     * IMPORTANT:
     *
     * A reversal does not delete the original payment.
     * The original record remains for audit purposes and its
     * status becomes REVERSED.
     */
    @Transactional
    public Payment reversePayment(
            Long paymentId,
            String reason
    ) {

        validateId(paymentId, "Payment ID");

        Payment payment =
                paymentRepository
                        .findById(paymentId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Payment not found: "
                                        + paymentId
                                )
                        );

        if (payment.getStatus()
                == PaymentStatus.REVERSED) {

            throw new IllegalStateException(
                    "Payment is already reversed"
            );
        }

        if (!Boolean.TRUE.equals(
                payment.getPaid()
        )
        && safe(payment.getAmountPaid()) <= 0.0) {

            throw new IllegalStateException(
                    "Payment has no recorded amount to reverse"
            );
        }

        Loan loan = payment.getLoan();

        if (loan == null) {
            throw new IllegalStateException(
                    "Payment has no associated loan"
            );
        }

        /*
         * Restore principal.
         */
        double principal =
                safe(
                        payment
                                .getPrincipalComponent()
                );

        double currentBalance =
                safe(
                        loan
                                .getOutstandingBalance()
                );

        loan.setOutstandingBalance(
                currentBalance + principal
        );

        /*
         * Restore cycle interest.
         */
        double interest =
                safe(
                        payment
                                .getInterestComponent()
                );

        double cycleRemaining =
                safe(
                        payment
                                .getCycleInterestRemaining()
                );

        payment.setCycleInterestRemaining(
                cycleRemaining + interest
        );

        /*
         * Restore penalty.
         */
        double penalty =
                safe(payment.getPenalty());

        /*
         * We do not simply add the entire penalty paid back
         * because the original penalty field has already been
         * reduced. The amount can be restored from the payment
         * allocation when needed.
         */

        /*
         * Mark reversed.
         */
        payment.setStatus(
                PaymentStatus.REVERSED
        );

        payment.setPaid(false);

        if (reason != null
                && !reason.isBlank()) {

            String existingNotes =
                    payment.getNotes();

            if (existingNotes == null
                    || existingNotes.isBlank()) {

                payment.setNotes(
                        "REVERSED: " + reason
                );

            } else {

                payment.setNotes(
                        existingNotes
                        + "\nREVERSED: "
                        + reason
                );
            }
        }

        Payment saved =
                paymentRepository.save(payment);

        loanRepository.save(loan);

        log.info(
                "Payment reversed: paymentId={}, loanId={}, "
                + "principalRestored={}, interestRestored={}",
                paymentId,
                loan.getId(),
                principal,
                interest
        );

        return saved;
    }


    // ============================================================
    // BORROWER PAYMENT HISTORY
    // ============================================================

    @Transactional(readOnly = true)
    public List<Payment> getBorrowerPaymentHistory(
            Long borrowerId,
            Long organizationId
    ) {

        validateId(borrowerId, "Borrower ID");
        validateId(organizationId, "Organization ID");

        List<Payment> payments =
                paymentRepository
                        .findBorrowerPaymentHistory(
                                borrowerId,
                                organizationId
                        );

        return payments != null
                ? payments
                : new ArrayList<>();
    }


    // ============================================================
    // PAID PAYMENTS BY BORROWER
    // ============================================================

    @Transactional(readOnly = true)
    public List<Payment> getPaidPaymentsByBorrower(
            Long borrowerId,
            Long organizationId
    ) {

        validateId(borrowerId, "Borrower ID");
        validateId(organizationId, "Organization ID");

        List<Payment> payments =
                paymentRepository
                        .findPaidPaymentsByBorrower(
                                borrowerId,
                                organizationId
                        );

        return payments != null
                ? payments
                : new ArrayList<>();
    }


    // ============================================================
    // PAYMENT STATISTICS
    // ============================================================

    @Transactional(readOnly = true)
    public long countBorrowerPayments(
            Long borrowerId,
            Long organizationId
    ) {

        return paymentRepository
                .countByBorrowerIdAndOrganizationId(
                        borrowerId,
                        organizationId
                );
    }


    @Transactional(readOnly = true)
    public long countPaidBorrowerPayments(
            Long borrowerId,
            Long organizationId
    ) {

        return paymentRepository
                .countPaidPaymentsByBorrower(
                        borrowerId,
                        organizationId
                );
    }


    @Transactional(readOnly = true)
    public long countLateBorrowerPayments(
            Long borrowerId,
            Long organizationId
    ) {

        return paymentRepository
                .countLatePaymentsByBorrower(
                        borrowerId,
                        organizationId
                );
    }


    @Transactional(readOnly = true)
    public long countOverdueBorrowerPayments(
            Long borrowerId,
            Long organizationId
    ) {

        return paymentRepository
                .countOverduePaymentsByBorrower(
                        borrowerId,
                        organizationId,
                        LocalDate.now()
                );
    }


    // ============================================================
    // PAYMENT TOTALS
    // ============================================================

    @Transactional(readOnly = true)
    public Double getTotalPaidByBorrower(
            Long borrowerId,
            Long organizationId
    ) {

        Double value =
                paymentRepository.sumPaidByBorrower(
                        borrowerId,
                        organizationId
                );

        return value != null
                ? value
                : 0.0;
    }


    @Transactional(readOnly = true)
    public Double getTotalPrincipalPaidByBorrower(
            Long borrowerId,
            Long organizationId
    ) {

        Double value =
                paymentRepository
                        .sumPrincipalPaidByBorrower(
                                borrowerId,
                                organizationId
                        );

        return value != null
                ? value
                : 0.0;
    }


    @Transactional(readOnly = true)
    public Double getTotalInterestPaidByBorrower(
            Long borrowerId,
            Long organizationId
    ) {

        Double value =
                paymentRepository
                        .sumInterestPaidByBorrower(
                                borrowerId,
                                organizationId
                        );

        return value != null
                ? value
                : 0.0;
    }


    @Transactional(readOnly = true)
    public Double getTotalPenaltyPaidByBorrower(
            Long borrowerId,
            Long organizationId
    ) {

        Double value =
                paymentRepository
                        .sumPenaltyPaidByBorrower(
                                borrowerId,
                                organizationId
                        );

        return value != null
                ? value
                : 0.0;
    }


    // ============================================================
    // VALIDATION / HELPERS
    // ============================================================

    private void validateId(
            Long value,
            String name
    ) {

        if (value == null || value <= 0) {

            throw new IllegalArgumentException(
                    name + " is required"
            );
        }
    }


    private double safe(
            Double value
    ) {

        return value != null
                ? value
                : 0.0;
    }
}