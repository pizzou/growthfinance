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
     * PAYMENT ALLOCATION RULE
     * ------------------------
     *
     * Within one monthly cycle:
     *
     * 1. Calculate monthly interest ONCE.
     *
     * 2. Payment first pays remaining interest.
     *
     * 3. Remaining payment reduces principal.
     *
     * 4. Additional payments during the same cycle do NOT generate
     *    another month's interest.
     *
     * Example:
     *
     * Opening principal = 5,000,000
     * Monthly rate       = 10%
     * Cycle interest     = 500,000
     *
     * Payment 1 = 2,000,000
     *
     * Interest  = 500,000
     * Principal = 1,500,000
     * Balance   = 3,500,000
     *
     * Payment 2 = 1,000,000
     *
     * Interest  = 0
     * Principal = 1,000,000
     * Balance   = 2,500,000
     *
     * Payment 3 = 2,300,000
     *
     * Interest  = 0
     * Principal = 2,300,000
     * Balance   = 200,000
     *
     * Payment 4 = 53,000
     *
     * Interest  = 0
     * Principal = 53,000
     * Balance   = 147,000
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

        // ============================================================
        // 1. VALIDATE AMOUNT
        // ============================================================

        if (amount == null || amount <= 0) {

            throw new IllegalArgumentException(
                "Payment amount must be greater than zero"
            );
        }

        amount = round(amount);


        // ============================================================
        // 2. LOAD LOAN
        // ============================================================

        Loan loan =
            loanRepo.findById(loanId)
                .orElseThrow(
                    () ->
                        new RuntimeException(
                            "Loan not found: " + loanId
                        )
                );


        // ============================================================
        // 3. ORGANIZATION SECURITY
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
        // 4. CHECK LOAN STATUS
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
        // 5. DUPLICATE TRANSACTION CHECK
        // ============================================================

        if (
            txnId != null
            && !txnId.isBlank()
            && loan.getOrganization() != null
        ) {

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
                        "Duplicate payment transaction {} detected "
                            + "for loan {}. Returning payment {}.",
                        txnId,
                        loanId,
                        existing.getId()
                    );

                    return existing;
                }

                throw new IllegalStateException(
                    "Transaction ID "
                        + txnId
                        + " has already been used "
                        + "for another payment."
                );
            }
        }


        // ============================================================
        // 6. FIND CURRENT MONTHLY CYCLE
        // ============================================================

        List<Payment> loanPayments =
            paymentRepo.findByLoanId(loanId);


        /*
         * Find the first unpaid scheduled installment.
         *
         * IMPORTANT:
         *
         * This installment represents the current monthly cycle.
         */
        Optional<Payment> currentInstallmentOpt =
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


        LocalDate cycleDueDate =
            currentInstallmentOpt
                .map(Payment::getDueDate)
                .orElse(
                    loan.getNextDueDate() != null
                        ? loan.getNextDueDate()
                        : LocalDate.now()
                );


        Payment installment =
            currentInstallmentOpt.orElse(null);


        // ============================================================
        // 7. CREATE CURRENT CYCLE IF NECESSARY
        // ============================================================

        if (installment == null) {

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
                        cycleDueDate
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
                    .build();
        }


        // ============================================================
        // 8. DETERMINE WHETHER THIS CYCLE ALREADY RECEIVED PAYMENT
        // ============================================================

        double previousAmountPaid =
            safe(
                installment.getAmountPaid()
            );


        boolean cycleAlreadyHasPayment =
            previousAmountPaid > 0.0;


        // ============================================================
        // 9. GET ACTUAL INTEREST ALREADY PAID
        // ============================================================

        /*
         * IMPORTANT:
         *
         * interestComponent is treated as ACTUAL interest paid
         * during this cycle.
         *
         * We do NOT use the scheduled/projected interest here.
         */
        double interestAlreadyPaid =
            safe(
                installment.getInterestComponent()
            );


        interestAlreadyPaid =
            Math.max(
                0.0,
                round(
                    interestAlreadyPaid
                )
            );


        // ============================================================
        // 10. GET ACTUAL PRINCIPAL ALREADY PAID
        // ============================================================

        /*
         * IMPORTANT:
         *
         * principalComponent is treated as ACTUAL principal paid.
         *
         * We do NOT take the scheduled principal and add it here.
         */
        double principalAlreadyPaid =
            safe(
                installment.getPrincipalComponent()
            );


        principalAlreadyPaid =
            Math.max(
                0.0,
                round(
                    principalAlreadyPaid
                )
            );


        // ============================================================
        // 11. CURRENT OUTSTANDING PRINCIPAL
        // ============================================================

        double balance =
            safe(
                loan.getOutstandingBalance()
            );


        balance =
            Math.max(
                0.0,
                round(balance)
            );


        if (balance <= 0.0) {

            throw new RuntimeException(
                "Loan has no outstanding principal."
            );
        }


        // ============================================================
        // 12. INTEREST RATE
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
            "MONTHLY".equalsIgnoreCase(
                rateType
            )
        ) {

            monthlyRate =
                rate / 100.0;

        } else {

            /*
             * Treat other rate types as annual rates.
             */
            monthlyRate =
                rate / 100.0 / 12.0;
        }


        // ============================================================
        // 13. CALCULATE CYCLE INTEREST
        // ============================================================

        /*
         * CRITICAL RULE:
         *
         * If this is the FIRST payment in the cycle,
         * calculate the monthly interest.
         *
         * If this cycle has already received a payment,
         * DO NOT calculate a new month's interest.
         *
         * This prevents:
         *
         * Payment 1 -> 500,000 interest
         * Payment 2 -> another 350,000 interest
         *
         * which is WRONG.
         *
         * Instead:
         *
         * Payment 1 -> cycle interest calculated once.
         * Payment 2 -> remaining cycle interest only.
         */
        double cycleInterest;


        if (!cycleAlreadyHasPayment) {

            cycleInterest =
                round(
                    balance
                    * monthlyRate
                );

        } else {

            /*
             * Interest was already established
             * when the first payment was made.
             *
             * The current installment's accumulated
             * interest represents what was actually paid.
             *
             * If interest has already been completely
             * paid, there is no interest remaining.
             */
            cycleInterest =
                interestAlreadyPaid;
        }


        // ============================================================
        // 14. DETERMINE REMAINING INTEREST
        // ============================================================

        double remainingInterest;


        if (!cycleAlreadyHasPayment) {

            /*
             * First payment:
             *
             * Entire cycle interest is still outstanding.
             */
            remainingInterest =
                Math.max(
                    0.0,
                    cycleInterest
                );

        } else {

            /*
             * Same cycle:
             *
             * If interest was already fully paid,
             * remaining interest = 0.
             *
             * If the first payment only partially paid
             * interest, the unpaid amount remains.
             *
             * NOTE:
             *
             * With the existing Payment entity, we need a way
             * to preserve the ORIGINAL cycle interest.
             *
             * Therefore if the first payment was partial,
             * we use the fact that the amount paid was less
             * than the calculated interest and preserve the
             * unpaid portion using the current cycle balance
             * calculation below.
             */
            if (
                interestAlreadyPaid > 0.0
            ) {

                remainingInterest = 0.0;

            } else {

                remainingInterest = 0.0;
            }
        }


        // ============================================================
        // 15. SPECIAL CASE:
        // PARTIAL FIRST PAYMENT
        // ============================================================

        /*
         * We need to correctly preserve unpaid interest.
         *
         * If the current installment has no previous payment,
         * remainingInterest is the full cycle interest.
         *
         * After a first payment that does not fully cover
         * interest, the installment remains unpaid.
         *
         * The actual unpaid interest is derived from:
         *
         * original cycle interest - accumulated actual interest paid.
         *
         * Because the current Payment entity does not have
         * a dedicated cycleInterest field, we reconstruct it
         * only when the cycle has not yet been completed.
         */
        if (
            cycleAlreadyHasPayment
            && !Boolean.TRUE.equals(
                installment.getPaid()
            )
        ) {

            /*
             * The safest interpretation for an installment that
             * remains unpaid is that the current cycle still has
             * unpaid interest.
             *
             * However, because the original cycle interest is not
             * stored separately in Payment, we cannot reliably
             * reconstruct it after a partial interest payment.
             *
             * This is why the first payment logic below stores
             * the cycle interest in the installment notes when
             * necessary.
             */
            Double storedCycleInterest =
                extractStoredCycleInterest(
                    installment
                );


            if (
                storedCycleInterest != null
            ) {

                cycleInterest =
                    storedCycleInterest;

                remainingInterest =
                    Math.max(
                        0.0,
                        round(
                            cycleInterest
                            - interestAlreadyPaid
                        )
                    );

            } else {

                /*
                 * Fallback for old records created before this
                 * implementation.
                 */
                cycleInterest =
                    round(
                        (
                            balance
                            + principalAlreadyPaid
                        )
                        * monthlyRate
                    );


                remainingInterest =
                    Math.max(
                        0.0,
                        round(
                            cycleInterest
                            - interestAlreadyPaid
                        )
                    );
            }
        }


        // ============================================================
        // 16. PENALTY
        // ============================================================

        boolean isLate =
            LocalDate.now()
                .isAfter(
                    cycleDueDate
                );


        int daysLate =
            isLate
                ? (int)
                    ChronoUnit.DAYS.between(
                        cycleDueDate,
                        LocalDate.now()
                    )
                : 0;


        double calculatedPenalty =
            0.0;


        if (
            isLate
            && daysLate > 0
        ) {

            /*
             * 2% per 30 days, prorated.
             *
             * Penalty is based on the current payment amount.
             */
            calculatedPenalty =
                round(
                    amount
                    * 0.02
                    * daysLate
                    / 30.0
                );
        }


        double previousPenalty =
            safe(
                installment.getPenalty()
            );


        previousPenalty =
            Math.max(
                0.0,
                round(
                    previousPenalty
                )
            );


        double penalty =
            Math.max(
                0.0,
                round(
                    calculatedPenalty
                    - previousPenalty
                )
            );


        // ============================================================
        // 17. AMOUNT AVAILABLE AFTER PENALTY
        // ============================================================

        double netAvailable =
            round(
                Math.max(
                    0.0,
                    amount
                    - penalty
                )
            );


        // ============================================================
        // 18. INTEREST ALLOCATION
        // ============================================================

        double interestPaid =
            Math.min(
                netAvailable,
                remainingInterest
            );


        interestPaid =
            round(
                interestPaid
            );


        // ============================================================
        // 19. PRINCIPAL ALLOCATION
        // ============================================================

        double availableForPrincipal =
            Math.max(
                0.0,
                round(
                    netAvailable
                    - interestPaid
                )
            );


        double principalPaid =
            Math.min(
                availableForPrincipal,
                balance
            );


        principalPaid =
            round(
                principalPaid
            );


        // ============================================================
        // 20. NEW BALANCE
        // ============================================================

        double newBalance =
            round(
                Math.max(
                    0.0,
                    balance
                    - principalPaid
                )
            );


        // ============================================================
        // 21. ACCUMULATED ACTUAL INTEREST
        // ============================================================

        double totalInterestPaid =
            round(
                interestAlreadyPaid
                + interestPaid
            );


        // ============================================================
        // 22. ACCUMULATED ACTUAL PRINCIPAL
        // ============================================================

        double totalPrincipalPaid =
            round(
                principalAlreadyPaid
                + principalPaid
            );


        // ============================================================
        // 23. INTEREST COVERED?
        // ============================================================

        boolean interestCovered;


        if (
            cycleInterest <= 0.01
        ) {

            interestCovered = true;

        } else {

            interestCovered =
                totalInterestPaid
                    >= cycleInterest - 0.01;
        }


        // ============================================================
        // 24. FULLY PAID?
        // ============================================================

        boolean fullyPaidOff =
            newBalance <= 0.01;


        // ============================================================
        // 25. UPDATE INSTALLMENT AMOUNT
        // ============================================================

        double newAmountPaid =
            round(
                previousAmountPaid
                + amount
            );


        installment.setAmountPaid(
            newAmountPaid
        );


        // ============================================================
        // 26. STORE ACTUAL INTEREST
        // ============================================================

        installment.setInterestComponent(
            totalInterestPaid
        );


        // ============================================================
        // 27. STORE ACTUAL PRINCIPAL
        // ============================================================

        installment.setPrincipalComponent(
            totalPrincipalPaid
        );


        // ============================================================
        // 28. STORE PENALTY
        // ============================================================

        installment.setPenalty(
            round(
                previousPenalty
                + penalty
            )
        );


        // ============================================================
        // 29. STORE OUTSTANDING
        // ============================================================

        installment.setOutstandingAfter(
            newBalance
        );


        // ============================================================
        // 30. LATE INFORMATION
        // ============================================================

        installment.setLate(
            isLate
            || installment.isLate()
        );


        int previousDaysLate =
            installment.getDaysLate() != null
                ? installment.getDaysLate()
                : 0;


        installment.setDaysLate(
            Math.max(
                previousDaysLate,
                daysLate
            )
        );


        // ============================================================
        // 31. PAYMENT METADATA
        // ============================================================

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
            buildPaymentNotes(
                installment,
                cycleInterest
            )
        );


        // ============================================================
        // 32. PAYMENT REFERENCE
        // ============================================================

        if (
            installment.getPaymentReference() == null
            || installment
                .getPaymentReference()
                .isBlank()
        ) {

            installment.setPaymentReference(
                generateRef(loan)
            );
        }


        // ============================================================
        // 33. CYCLE COMPLETION
        // ============================================================

        boolean cycleCompleted =
            interestCovered
            || fullyPaidOff;


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


        // ============================================================
        // 34. SAVE PAYMENT
        // ============================================================

        installment =
            paymentRepo.save(
                installment
            );


        // ============================================================
        // 35. UPDATE LOAN TOTAL PAID
        // ============================================================

        double oldTotalPaid =
            safe(
                loan.getTotalPaid()
            );


        loan.setTotalPaid(
            round(
                oldTotalPaid
                + amount
            )
        );


        // ============================================================
        // 36. UPDATE OUTSTANDING BALANCE
        // ============================================================

        loan.setOutstandingBalance(
            newBalance
        );


        // ============================================================
        // 37. LAST PAYMENT DATE
        // ============================================================

        loan.setLastPaymentDate(
            LocalDate.now()
        );


        // ============================================================
        // 38. LOAN STATUS
        // ============================================================

        if (fullyPaidOff) {

            // --------------------------------------------------------
            // LOAN FULLY PAID
            // --------------------------------------------------------

            loan.setStatus(
                LoanStatus.PAID
            );


            /*
             * Delete future projected installments.
             */
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


            if (
                !stillPending.isEmpty()
            ) {

                paymentRepo.deleteAll(
                    stillPending
                );
            }

        } else {

            // --------------------------------------------------------
            // LOAN STILL ACTIVE
            // --------------------------------------------------------

            loan.setStatus(
                LoanStatus.ACTIVE
            );


            if (interestCovered) {

                /*
                 * Current cycle interest is fully satisfied.
                 *
                 * Next monthly cycle begins next month.
                 */
                loan.setNextDueDate(
                    cycleDueDate
                        .plusMonths(1)
                );

            } else {

                /*
                 * Interest remains unpaid.
                 *
                 * Stay in the same cycle.
                 */
                loan.setNextDueDate(
                    cycleDueDate
                );
            }
        }


        // ============================================================
        // 39. SAVE LOAN
        // ============================================================

        loanRepo.save(
            loan
        );


        // ============================================================
        // 40. AUDIT
        // ============================================================

        audit(
            loan.getOrganization(),
            recordedBy,
            "PAYMENT_RECORDED",
            "PAYMENT",
            installment.getId()
                .toString(),
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
                + ", outstanding: "
                + newBalance
        );


        // ============================================================
        // 41. EMAIL
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
        // 42. SMS
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
        // 43. OFFICER NOTIFICATION
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
                        + ". Outstanding balance: "
                        + newBalance,
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
        // 44. WEBHOOK
        // ============================================================

        webhookService.dispatch(
            loan.getOrganization(),
            "PAYMENT_MADE",
            loan
        );


        // ============================================================
        // 45. ACCOUNTING
        // ============================================================

        /*
         * PaymentService has already calculated the exact allocation.
         *
         * AccountingService must NOT calculate interest again.
         */
        accountingService.postPaymentReceived(
            installment,
            amount,
            principalPaid,
            interestPaid,
            penalty
        );


        // ============================================================
        // 46. LOG RESULT
        // ============================================================

        log.info(
            "Payment recorded: loan={}, amount={}, interest={}, "
                + "principal={}, penalty={}, outstanding={}, "
                + "cycleInterest={}, cycleInterestPaid={}, "
                + "cycleCompleted={}",
            loanId,
            amount,
            interestPaid,
            principalPaid,
            penalty,
            newBalance,
            cycleInterest,
            totalInterestPaid,
            cycleCompleted
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


        for (
            Payment p :
            overduePayments
        ) {

            Loan loan =
                p.getLoan();


            if (
                loan == null
            ) {

                continue;
            }


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


                loanRepo.save(
                    loan
                );
            }
        }
    }


    // ================================================================
    // HELPERS
    // ================================================================

    /**
     * Safely converts nullable Double to zero.
     */
    private double safe(
        Double value
    ) {

        return value != null
            ? value
            : 0.0;
    }


    /**
     * Rounds monetary values to two decimal places.
     *
     * NOTE:
     *
     * BigDecimal is preferable for financial calculations.
     * This helper keeps compatibility with the current Double-based
     * Loan and Payment entities.
     */
    private double round(
        double value
    ) {

        return Math.round(
            value * 100.0
        ) / 100.0;
    }


    /**
     * Stores the cycle interest in the installment notes so that
     * a partially-paid cycle can remember the original interest
     * amount without requiring an immediate Payment entity migration.
     */
    private String buildPaymentNotes(
        Payment installment,
        double cycleInterest
    ) {

        String existing =
            installment.getNotes();


        String marker =
            "[CYCLE_INTEREST="
                + round(cycleInterest)
                + "]";


        if (
            existing == null
            || existing.isBlank()
        ) {

            return marker;
        }


        /*
         * Don't append duplicate cycle-interest markers.
         */
        if (
            existing.contains(
                "[CYCLE_INTEREST="
            )
        ) {

            return existing;
        }


        return existing
            + " "
            + marker;
    }


    /**
     * Reads the stored cycle interest from the installment notes.
     *
     * Returns null when no stored value exists.
     */
    private Double extractStoredCycleInterest(
        Payment installment
    ) {

        String notes =
            installment.getNotes();


        if (
            notes == null
            || notes.isBlank()
        ) {

            return null;
        }


        String marker =
            "[CYCLE_INTEREST=";


        int start =
            notes.indexOf(
                marker
            );


        if (
            start < 0
        ) {

            return null;
        }


        int valueStart =
            start
            + marker.length();


        int end =
            notes.indexOf(
                "]",
                valueStart
            );


        if (
            end < 0
        ) {

            return null;
        }


        String value =
            notes.substring(
                valueStart,
                end
            );


        try {

            return round(
                Double.parseDouble(
                    value
                )
            );

        } catch (
            NumberFormatException e
        ) {

            log.warn(
                "Could not parse stored cycle interest: {}",
                value
            );

            return null;
        }
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