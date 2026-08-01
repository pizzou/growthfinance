
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanRestructuringService {

    private final LoanRepository loanRepo;
    private final PaymentRepository paymentRepo;
    private final AuditService auditService;
    private final WebhookService webhookService;
    private final MailService mailService;
    private final SmsService smsService;
    private final LoanClassificationService loanClassificationService;

    private static final int MONEY_SCALE = 2;
    private static final int CALCULATION_SCALE = 12;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;


    // ============================================================
    // RESTRUCTURE LOAN
    // ============================================================

    @Transactional
    public Loan restructure(
            Long loanId,
            Long orgId,
            User officer,
            int newMonths,
            Double newRate,
            String reason) {

        Loan loan = get(loanId, orgId);

        if (loan.getStatus() != LoanStatus.ACTIVE
                && loan.getStatus() != LoanStatus.OVERDUE
                && loan.getStatus() != LoanStatus.DEFAULTED) {

            throw new RuntimeException(
                    "Only ACTIVE/OVERDUE/DEFAULTED loans can be restructured"
            );
        }

        if (newMonths <= 0) {
            throw new IllegalArgumentException(
                    "New loan duration must be greater than zero."
            );
        }

        BigDecimal previousRate = loan.getInterestRate();
        int previousMonths = loan.getDurationMonths();

        if (newRate != null) {
            loan.setInterestRate(
                    BigDecimal.valueOf(newRate)
            );
        }

        loan.setDurationMonths(newMonths);
        loan.setStatus(LoanStatus.RESTRUCTURED);

        loan.setDaysOverdue(0);

        loan.setInternalNotes(
                "[RESTRUCTURED] "
                        + safe(reason)
                        + " | "
                        + previousMonths
                        + "mo@"
                        + safe(previousRate)
                        + "% -> "
                        + newMonths
                        + "mo@"
                        + safe(loan.getInterestRate())
                        + "%"
        );

        regenerateSchedule(loan, officer);

        Loan saved = loanRepo.save(loan);

        try {
            loanClassificationService.reclassify(saved);
        } catch (Exception e) {
            log.warn(
                    "Reclassification failed for loan {}: {}",
                    saved.getId(),
                    e.getMessage()
            );
        }

        audit(
                loan.getOrganization(),
                officer,
                "LOAN_RESTRUCTURED",
                loanId,
                "Restructured: " + safe(reason)
        );

        webhookService.dispatch(
                loan.getOrganization(),
                "LOAN_RESTRUCTURED",
                saved
        );

        notify(
                saved,
                () -> mailService.sendLoanRestructured(
                        saved,
                        reason
                ),
                "Your loan "
                        + saved.getReferenceNumber()
                        + " has been restructured. New term: "
                        + saved.getDurationMonths()
                        + "mo at "
                        + safe(saved.getInterestRate())
                        + "%."
        );

        return saved;
    }


    // ============================================================
    // WRITE OFF LOAN
    // ============================================================

    @Transactional
    public Loan writeOff(
            Long loanId,
            Long orgId,
            User officer,
            String reason) {

        Loan loan = get(loanId, orgId);

        if (loan.getStatus() == LoanStatus.PAID
                || loan.getStatus() == LoanStatus.CLOSED) {

            throw new RuntimeException(
                    "Cannot write off a PAID or CLOSED loan"
            );
        }

        BigDecimal outstandingAmount =
                loan.getOutstandingBalance() != null
                        ? money(loan.getOutstandingBalance())
                        : BigDecimal.ZERO.setScale(
                                MONEY_SCALE,
                                ROUNDING_MODE
                        );

        loan.setStatus(LoanStatus.WRITTEN_OFF);

        /*
         * IMPORTANT:
         *
         * outstandingBalance is BigDecimal.
         *
         * Do NOT use:
         *
         * loan.setOutstandingBalance(0.0);
         *
         * Use BigDecimal.ZERO.
         */
        loan.setOutstandingBalance(
                BigDecimal.ZERO.setScale(
                        MONEY_SCALE,
                        ROUNDING_MODE
                )
        );

        loan.setInternalNotes(
                "[WRITTEN OFF] "
                        + safe(reason)
                        + " | Amount: "
                        + safe(loan.getCurrency())
                        + " "
                        + outstandingAmount
                        + " | "
                        + LocalDate.now()
        );

        Loan saved = loanRepo.save(loan);

        try {
            loanClassificationService.reclassify(saved);
        } catch (Exception e) {
            log.warn(
                    "Reclassification failed for loan {}: {}",
                    saved.getId(),
                    e.getMessage()
            );
        }

        audit(
                loan.getOrganization(),
                officer,
                "LOAN_WRITTEN_OFF",
                loanId,
                "Written off "
                        + safe(loan.getCurrency())
                        + " "
                        + outstandingAmount
                        + " | "
                        + safe(reason)
        );

        webhookService.dispatch(
                loan.getOrganization(),
                "LOAN_WRITTEN_OFF",
                saved
        );

        notify(
                saved,
                () -> mailService.sendLoanWrittenOff(
                        saved,
                        reason
                ),
                "There's an update on your loan "
                        + saved.getReferenceNumber()
                        + ". Please contact us for details."
        );

        return saved;
    }


    // ============================================================
    // GRANT MORATORIUM
    // ============================================================

    @Transactional
    public Loan grantMoratorium(
            Long loanId,
            Long orgId,
            User officer,
            int pauseMonths,
            String reason) {

        Loan loan = get(loanId, orgId);

        if (loan.getStatus() != LoanStatus.ACTIVE
                && loan.getStatus() != LoanStatus.OVERDUE) {

            throw new RuntimeException(
                    "Moratorium only applies to ACTIVE or OVERDUE loans"
            );
        }

        if (pauseMonths <= 0) {
            throw new IllegalArgumentException(
                    "Moratorium duration must be greater than zero months."
            );
        }

        List<Payment> unpaidPayments =
                paymentRepo.findByLoanId(loanId)
                        .stream()
                        .filter(p -> !Boolean.TRUE.equals(p.getPaid()))
                        .toList();

        for (Payment payment : unpaidPayments) {

            if (payment.getDueDate() != null) {

                payment.setDueDate(
                        payment.getDueDate()
                                .plusMonths(pauseMonths)
                );
            }

            paymentRepo.save(payment);
        }

        if (loan.getMaturityDate() != null) {

            loan.setMaturityDate(
                    loan.getMaturityDate()
                            .plusMonths(pauseMonths)
            );
        }

        if (loan.getNextDueDate() != null) {

            loan.setNextDueDate(
                    loan.getNextDueDate()
                            .plusMonths(pauseMonths)
            );
        }

        loan.setStatus(LoanStatus.ACTIVE);
        loan.setDaysOverdue(0);

        loan.setInternalNotes(
                (loan.getInternalNotes() != null
                        ? loan.getInternalNotes() + " | "
                        : "")
                        + "[MORATORIUM "
                        + pauseMonths
                        + "mo] "
                        + safe(reason)
        );

        Loan saved = loanRepo.save(loan);

        try {
            loanClassificationService.reclassify(saved);
        } catch (Exception e) {
            log.warn(
                    "Reclassification failed for loan {}: {}",
                    saved.getId(),
                    e.getMessage()
            );
        }

        audit(
                loan.getOrganization(),
                officer,
                "MORATORIUM_GRANTED",
                loanId,
                pauseMonths
                        + "mo moratorium: "
                        + safe(reason)
        );

        notify(
                saved,
                () -> mailService.sendMoratoriumGranted(
                        saved,
                        pauseMonths,
                        reason
                ),
                "Your payments on loan "
                        + saved.getReferenceNumber()
                        + " are paused for "
                        + pauseMonths
                        + " month(s). Next due date: "
                        + saved.getNextDueDate()
                        + "."
        );

        return saved;
    }


    // ============================================================
    // REGENERATE REPAYMENT SCHEDULE
    // ============================================================

    private void regenerateSchedule(
            Loan loan,
            User officer) {

        /*
         * Delete all future unpaid payments.
         *
         * Historical/paid payments remain untouched.
         */
        List<Payment> futurePayments =
                paymentRepo.findByLoanId(loan.getId())
                        .stream()
                        .filter(p -> !Boolean.TRUE.equals(p.getPaid()))
                        .toList();

        paymentRepo.deleteAll(futurePayments);


        /*
         * Starting outstanding balance.
         */
        BigDecimal balance =
                loan.getOutstandingBalance() != null
                        ? money(loan.getOutstandingBalance())
                        : BigDecimal.ZERO.setScale(
                                MONEY_SCALE,
                                ROUNDING_MODE
                        );

        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            balance = BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    ROUNDING_MODE
            );
        }


        /*
         * Validate duration.
         */
        Integer durationValue =
                loan.getDurationMonths();

        if (durationValue == null || durationValue <= 0) {

            throw new IllegalArgumentException(
                    "Loan duration must be greater than zero."
            );
        }

        int months = durationValue;


        /*
         * Interest rate.
         */
        BigDecimal rate =
                loan.getInterestRate() != null
                        ? loan.getInterestRate()
                        : BigDecimal.ZERO;


        if (rate.compareTo(BigDecimal.ZERO) < 0) {

            throw new IllegalArgumentException(
                    "Loan interest rate cannot be negative."
            );
        }


        /*
         * Interest rate type.
         */
        String rateType =
                loan.getInterestRateType() != null
                        ? loan.getInterestRateType()
                        : "MONTHLY";


        /*
         * Convert configured percentage
         * into a monthly decimal rate.
         *
         * MONTHLY:
         *
         * 2% -> 0.02
         *
         * ANNUAL:
         *
         * 12% -> 0.01
         */
        BigDecimal monthlyRate =
                calculateMonthlyRate(
                        rate,
                        rateType
                );


        /*
         * Calculate fixed monthly installment.
         */
        BigDecimal monthlyInstallment =
                calculateMonthlyInstallment(
                        balance,
                        monthlyRate,
                        months
                );


        /*
         * First payment is one month from now.
         */
        LocalDate dueDate =
                LocalDate.now().plusMonths(1);


        /*
         * Generate schedule.
         */
        for (int installmentNumber = 1;
             installmentNumber <= months;
             installmentNumber++) {

            /*
             * Interest for this period.
             */
            BigDecimal interest =
                    money(
                            balance.multiply(monthlyRate)
                    );


            BigDecimal principalComponent;


            /*
             * Final installment pays whatever
             * principal remains.
             */
            if (installmentNumber == months) {

                principalComponent =
                        money(balance);

            } else {

                principalComponent =
                        money(
                                monthlyInstallment
                                        .subtract(interest)
                        );

                /*
                 * Never allow negative principal.
                 */
                if (principalComponent.compareTo(
                        BigDecimal.ZERO
                ) < 0) {

                    principalComponent =
                            BigDecimal.ZERO.setScale(
                                    MONEY_SCALE,
                                    ROUNDING_MODE
                            );
                }

                /*
                 * Never repay more principal
                 * than the remaining balance.
                 */
                if (principalComponent.compareTo(
                        balance
                ) > 0) {

                    principalComponent =
                            money(balance);
                }
            }


            /*
             * Actual installment amount.
             *
             * Final installment may differ slightly
             * because of rounding.
             */
            BigDecimal installmentAmount =
                    money(
                            principalComponent
                                    .add(interest)
                    );


            /*
             * Remaining balance after payment.
             */
            BigDecimal newBalance =
                    money(
                            balance
                                    .subtract(
                                            principalComponent
                                    )
                    );


            if (newBalance.compareTo(
                    BigDecimal.ZERO
            ) < 0) {

                newBalance =
                        BigDecimal.ZERO.setScale(
                                MONEY_SCALE,
                                ROUNDING_MODE
                        );
            }


            /*
             * Create payment.
             */
            Payment payment =
                    Payment.builder()
                            .paymentReference(
                                    "PAY-"
                                            + loan.getReferenceNumber()
                                            + "-R"
                                            + String.format(
                                                    "%03d",
                                                    installmentNumber
                                            )
                            )
                            .loan(loan)
                            .organization(
                                    loan.getOrganization()
                            )
                            .recordedBy(officer)
                            .installmentNumber(
                                    installmentNumber
                            )
                            .amount(
                                    installmentAmount
                            )
                            .principalComponent(
                                    principalComponent
                            )
                            .interestComponent(
                                    interest
                            )
                            .amountPaid(
                                    BigDecimal.ZERO.setScale(
                                            MONEY_SCALE,
                                            ROUNDING_MODE
                                    )
                            )
                            .dueDate(dueDate)
                            .paid(false)
                            .penalty(
                                    BigDecimal.ZERO.setScale(
                                            MONEY_SCALE,
                                            ROUNDING_MODE
                                    )
                            )
                            .waivedAmount(
                                    BigDecimal.ZERO.setScale(
                                            MONEY_SCALE,
                                            ROUNDING_MODE
                                    )
                            )
                            .outstandingAfter(
                                    newBalance
                            )
                            .status(
                                    Payment.PaymentStatus.PENDING
                            )
                            .build();


            paymentRepo.save(payment);


            /*
             * Move to next period.
             */
            balance = newBalance;

            dueDate =
                    dueDate.plusMonths(1);
        }


        /*
         * Update loan's next due date.
         */
        loan.setNextDueDate(
                LocalDate.now().plusMonths(1)
        );


        /*
         * Update outstanding balance to the
         * regenerated schedule balance.
         *
         * Normally this will be zero only after
         * all scheduled payments are paid, so we
         * intentionally DO NOT overwrite the
         * current outstanding balance here.
         */
        log.info(
                "Repayment schedule regenerated for loan {}. " +
                        "Months: {}, Starting balance: {}, " +
                        "Monthly rate: {}, Monthly installment: {}",
                loan.getReferenceNumber(),
                months,
                loan.getOutstandingBalance(),
                monthlyRate,
                monthlyInstallment
        );
    }


    // ============================================================
    // CALCULATE MONTHLY RATE
    // ============================================================

    private BigDecimal calculateMonthlyRate(
            BigDecimal rate,
            String rateType) {

        if (rate == null
                || rate.compareTo(BigDecimal.ZERO) == 0) {

            return BigDecimal.ZERO;
        }

        BigDecimal percentage =
                rate.divide(
                        BigDecimal.valueOf(100),
                        CALCULATION_SCALE,
                        ROUNDING_MODE
                );

        if ("MONTHLY".equalsIgnoreCase(rateType)) {

            return percentage;
        }

        /*
         * Default non-MONTHLY rates to ANNUAL.
         */
        return percentage.divide(
                BigDecimal.valueOf(12),
                CALCULATION_SCALE,
                ROUNDING_MODE
        );
    }


    // ============================================================
    // CALCULATE MONTHLY INSTALLMENT
    // ============================================================

    private BigDecimal calculateMonthlyInstallment(
            BigDecimal principal,
            BigDecimal monthlyRate,
            int months) {

        if (principal == null
                || principal.compareTo(BigDecimal.ZERO) <= 0) {

            return BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    ROUNDING_MODE
            );
        }

        if (months <= 0) {

            throw new IllegalArgumentException(
                    "Loan duration must be greater than zero."
            );
        }


        /*
         * Zero-interest loan:
         *
         * Payment = Principal / Months
         */
        if (monthlyRate.compareTo(
                BigDecimal.ZERO
        ) == 0) {

            return money(
                    principal.divide(
                            BigDecimal.valueOf(months),
                            CALCULATION_SCALE,
                            ROUNDING_MODE
                    )
            );
        }


        /*
         * Amortization formula:
         *
         * P × r × (1+r)^n
         * ----------------
         *    (1+r)^n - 1
         */
        BigDecimal onePlusRate =
                BigDecimal.ONE.add(monthlyRate);

        BigDecimal power =
                onePlusRate.pow(months);

        BigDecimal numerator =
                principal
                        .multiply(monthlyRate)
                        .multiply(power);

        BigDecimal denominator =
                power.subtract(BigDecimal.ONE);

        if (denominator.compareTo(
                BigDecimal.ZERO
        ) == 0) {

            throw new IllegalStateException(
                    "Unable to calculate monthly installment."
            );
        }

        return money(
                numerator.divide(
                        denominator,
                        CALCULATION_SCALE,
                        ROUNDING_MODE
                )
        );
    }


    // ============================================================
    // FIND LOAN
    // ============================================================

    private Loan get(
            Long loanId,
            Long orgId) {

        if (loanId == null) {
            throw new IllegalArgumentException(
                    "Loan ID cannot be null."
            );
        }

        if (orgId == null) {
            throw new IllegalArgumentException(
                    "Organization ID cannot be null."
            );
        }

        Loan loan =
                loanRepo.findById(loanId)
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Loan not found: "
                                                + loanId
                                )
                        );

        if (loan.getOrganization() == null
                || loan.getOrganization().getId() == null
                || !loan.getOrganization()
                        .getId()
                        .equals(orgId)) {

            throw new RuntimeException(
                    "Access denied"
            );
        }

        return loan;
    }


    // ============================================================
    // AUDIT
    // ============================================================

    private void audit(
            Organization organization,
            User user,
            String action,
            Long id,
            String description) {

        auditService.log(
                organization,
                user,
                action,
                "LOAN",
                String.valueOf(id),
                description
        );
    }


    // ============================================================
    // NOTIFICATIONS
    // ============================================================

    private void notify(
            Loan loan,
            Runnable sendEmail,
            String smsText) {

        if (loan == null
                || loan.getBorrower() == null) {

            return;
        }

        /*
         * Email failure must not roll back
         * the loan transaction.
         */
        try {

            sendEmail.run();

        } catch (Exception e) {

            log.warn(
                    "Email notification failed for loan {}: {}",
                    loan.getId(),
                    e.getMessage()
            );
        }


        /*
         * SMS failure must not roll back
         * the loan transaction.
         */
        try {

            if (loan.getBorrower().getPhone() != null) {

                smsService.sendCustom(
                        loan.getBorrower().getPhone(),
                        smsText
                );
            }

        } catch (Exception e) {

            log.warn(
                    "SMS notification failed for loan {}: {}",
                    loan.getId(),
                    e.getMessage()
            );
        }
    }


    // ============================================================
    // MONEY NORMALIZATION
    // ============================================================

    private BigDecimal money(
            BigDecimal value) {

        if (value == null) {

            return BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    ROUNDING_MODE
            );
        }

        return value.setScale(
                MONEY_SCALE,
                ROUNDING_MODE
        );
    }


    // ============================================================
    // NULL-SAFE STRING REPRESENTATION
    // ============================================================

    private String safe(
            Object value) {

        return value == null
                ? "null"
                : value.toString();
    }
}
