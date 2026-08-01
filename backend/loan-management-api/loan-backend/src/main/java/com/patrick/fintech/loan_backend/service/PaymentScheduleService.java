package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.publicportal.PaymentScheduleResponse;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.PaymentSchedule;
import com.patrick.fintech.loan_backend.model.PaymentSchedule.ScheduleStatus;
import com.patrick.fintech.loan_backend.repository.PaymentScheduleRepository;

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
public class PaymentScheduleService {

    private final PaymentScheduleRepository repository;

    private static final int MONEY_SCALE = 2;
    private static final int CALCULATION_SCALE = 12;

    private static final RoundingMode ROUNDING_MODE =
            RoundingMode.HALF_UP;

    private static final BigDecimal ONE_HUNDRED =
            BigDecimal.valueOf(100);

    private static final BigDecimal TWELVE =
            BigDecimal.valueOf(12);

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING_MODE);


    // ============================================================
    // GET PAYMENT SCHEDULE
    // ============================================================

    @Transactional(readOnly = true)
    public List<PaymentScheduleResponse> getSchedule(Long loanId) {

        if (loanId == null) {
            throw new IllegalArgumentException(
                    "Loan ID cannot be null."
            );
        }

        return repository
                .findByLoanIdOrderByInstallmentNumberAsc(loanId)
                .stream()
                .map(this::toResponse)
                .toList();
    }


    // ============================================================
    // MAP ENTITY -> RESPONSE
    // ============================================================

    private PaymentScheduleResponse toResponse(
            PaymentSchedule schedule) {

        return PaymentScheduleResponse.builder()
                .installmentNumber(schedule.getInstallmentNumber())
                .dueDate(schedule.getDueDate())
                .installmentAmount(money(schedule.getInstallmentAmount()))
                .principal(money(schedule.getPrincipalAmount()))
                .interest(money(schedule.getInterestAmount()))
                .penalty(money(schedule.getPenaltyAmount()))
                .paid(money(schedule.getAmountPaid()))
                .balance(money(schedule.getRemainingBalance()))
                .status(
                        schedule.getStatus() != null
                                ? schedule.getStatus().name()
                                : ScheduleStatus.PENDING.name()
                )
                .build();
    }


    // ============================================================
    // GENERATE MONTHLY REPAYMENT SCHEDULE
    // ============================================================

    /**
     * Generates a complete monthly reducing-balance repayment
     * schedule for the supplied loan.
     *
     * Financial rules:
     *
     * - Repayment is MONTHLY.
     * - Interest is calculated on the outstanding principal.
     * - ANNUAL rates are divided by 12.
     * - MONTHLY rates are used directly.
     * - All financial calculations use BigDecimal.
     * - All stored money values use 2 decimal places.
     * - The final installment clears the remaining principal.
     * - Existing schedule entries are removed before regeneration.
     *
     * This method is transactional.
     */
    @Transactional
    public void generateSchedule(Loan loan) {

        if (loan == null) {
            throw new IllegalArgumentException(
                    "Loan cannot be null when generating payment schedule."
            );
        }

        if (loan.getId() == null) {
            throw new IllegalArgumentException(
                    "Loan must be persisted before generating payment schedule."
            );
        }

        /*
         * This system uses monthly repayment.
         */
        if (loan.getRepaymentFrequency() != null
                && loan.getRepaymentFrequency()
                        != Loan.RepaymentFrequency.MONTHLY) {

            throw new IllegalArgumentException(
                    "Only MONTHLY repayment frequency is supported."
            );
        }

        /*
         * Validate principal.
         */
        if (loan.getAmount() == null
                || loan.getAmount()
                        .compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Loan principal amount must be greater than zero."
            );
        }

        /*
         * Validate duration.
         */
        Integer duration = loan.getDurationMonths();

        if (duration == null || duration <= 0) {
            throw new IllegalArgumentException(
                    "Loan duration must be greater than zero months."
            );
        }

        /*
         * Validate interest rate.
         */
        if (loan.getInterestRate() == null
                || loan.getInterestRate()
                        .compareTo(BigDecimal.ZERO) < 0) {

            throw new IllegalArgumentException(
                    "Loan interest rate cannot be negative."
            );
        }

        /*
         * Existing schedules are removed first.
         *
         * This is required when:
         *
         * - loan is restructured
         * - interest rate changes
         * - schedule is corrected
         * - schedule generation is retried
         */
        repository.deleteByLoanId(loan.getId());


        // ========================================================
        // PRINCIPAL
        // ========================================================

        BigDecimal principal =
                money(loan.getAmount());


        // ========================================================
        // INTEREST RATE
        // ========================================================

        BigDecimal configuredRate =
                loan.getInterestRate();


        String rateType =
                loan.getInterestRateType() != null
                        ? loan.getInterestRateType().trim().toUpperCase()
                        : "ANNUAL";


        /*
         * Convert configured rate into monthly decimal rate.
         *
         * ANNUAL 12%  -> 0.01
         *
         * MONTHLY 2% -> 0.02
         */
        BigDecimal monthlyRate =
                calculateMonthlyRate(
                        configuredRate,
                        rateType
                );


        // ========================================================
        // DISBURSEMENT DATE
        // ========================================================

        LocalDate disbursementDate =
                loan.getDisbursedAt();

        if (disbursementDate == null) {

            disbursementDate =
                    loan.getStartDate();
        }

        if (disbursementDate == null) {

            disbursementDate =
                    LocalDate.now();
        }


        /*
         * First repayment is one month after disbursement.
         */
        LocalDate firstDueDate =
                disbursementDate.plusMonths(1);


        // ========================================================
        // MONTHLY INSTALLMENT
        // ========================================================

        BigDecimal monthlyInstallment =
                calculateMonthlyInstallment(
                        principal,
                        monthlyRate,
                        duration
                );


        // ========================================================
        // GENERATE SCHEDULE
        // ========================================================

        BigDecimal remainingBalance =
                principal;


        for (int installmentNumber = 1;
             installmentNumber <= duration;
             installmentNumber++) {


            /*
             * Interest is calculated before applying the
             * installment to the outstanding principal.
             */
            BigDecimal interest =
                    money(
                            remainingBalance
                                    .multiply(monthlyRate)
                    );


            BigDecimal principalComponent;


            /*
             * Final installment:
             *
             * Always clear the exact remaining principal.
             *
             * This prevents rounding residue.
             */
            if (installmentNumber == duration) {

                principalComponent =
                        remainingBalance;

            } else {

                principalComponent =
                        money(
                                monthlyInstallment
                                        .subtract(interest)
                        );


                /*
                 * Principal component cannot be negative.
                 */
                if (principalComponent.compareTo(
                        BigDecimal.ZERO) < 0) {

                    principalComponent =
                            ZERO;
                }


                /*
                 * Principal component cannot exceed
                 * outstanding principal.
                 */
                if (principalComponent.compareTo(
                        remainingBalance) > 0) {

                    principalComponent =
                            remainingBalance;
                }
            }


            principalComponent =
                    money(principalComponent);


            /*
             * Final installment amount is always recalculated
             * from actual principal + interest.
             */
            BigDecimal installmentAmount =
                    money(
                            principalComponent
                                    .add(interest)
                    );


            /*
             * Calculate remaining balance.
             */
            BigDecimal newBalance =
                    money(
                            remainingBalance
                                    .subtract(principalComponent)
                    );


            if (newBalance.compareTo(
                    BigDecimal.ZERO) < 0) {

                newBalance =
                        ZERO;
            }


            // ====================================================
            // CREATE SCHEDULE RECORD
            // ====================================================

            PaymentSchedule schedule =
                    new PaymentSchedule();

            schedule.setLoan(loan);

            schedule.setInstallmentNumber(
                    installmentNumber
            );

            schedule.setDueDate(
                    firstDueDate.plusMonths(
                            installmentNumber - 1L
                    )
            );

            schedule.setInstallmentAmount(
                    installmentAmount
            );

            schedule.setPrincipalAmount(
                    principalComponent
            );

            schedule.setInterestAmount(
                    interest
            );

            schedule.setPenaltyAmount(
                    ZERO
            );

            schedule.setAmountPaid(
                    ZERO
            );

            schedule.setRemainingBalance(
                    newBalance
            );

            schedule.setStatus(
                    ScheduleStatus.PENDING
            );


            repository.save(schedule);


            /*
             * Move to next month's balance.
             */
            remainingBalance =
                    newBalance;
        }


        // ========================================================
        // FINAL VALIDATION
        // ========================================================

        if (remainingBalance.compareTo(
                BigDecimal.ZERO) != 0) {

            throw new IllegalStateException(
                    "Payment schedule generation failed for loan "
                            + loan.getReferenceNumber()
                            + ". Remaining principal balance: "
                            + remainingBalance
            );
        }


        /*
         * Keep the Loan's next due date synchronized with
         * the generated schedule.
         */
        loan.setNextDueDate(firstDueDate);


        log.info(
                "Monthly repayment schedule generated successfully. "
                        + "Loan={}, Installments={}, Principal={}, "
                        + "MonthlyRate={}, MonthlyInstallment={}",
                loan.getReferenceNumber(),
                duration,
                principal,
                monthlyRate,
                monthlyInstallment
        );
    }


    private BigDecimal calculateMonthlyRate(
            BigDecimal rate,
            String rateType) {

        if (rate == null
                || rate.compareTo(BigDecimal.ZERO) == 0) {

            return BigDecimal.ZERO;
        }

        BigDecimal percentage =
                rate.divide(
                        ONE_HUNDRED,
                        CALCULATION_SCALE,
                        ROUNDING_MODE
                );


        if ("MONTHLY".equalsIgnoreCase(rateType)) {

            return percentage;
        }


        /*
         * Default to annual rate.
         */
        return percentage.divide(
                TWELVE,
                CALCULATION_SCALE,
                ROUNDING_MODE
        );
    }


    // ============================================================
    // CALCULATE MONTHLY INSTALLMENT
    // ============================================================

    /**
     * Calculates a fixed monthly reducing-balance installment.
     *
     * Formula:
     *
     * P × r × (1+r)^n
     * -----------------
     *     (1+r)^n - 1
     *
     * P = principal
     * r = monthly rate
     * n = number of months
     */
    private BigDecimal calculateMonthlyInstallment(
            BigDecimal principal,
            BigDecimal monthlyRate,
            int months) {

        if (months <= 0) {

            throw new IllegalArgumentException(
                    "Loan duration must be greater than zero."
            );
        }


        /*
         * Zero-interest loan.
         */
        if (monthlyRate.compareTo(
                BigDecimal.ZERO) == 0) {

            return money(
                    principal.divide(
                            BigDecimal.valueOf(months),
                            CALCULATION_SCALE,
                            ROUNDING_MODE
                    )
            );
        }


        BigDecimal onePlusRate =
                BigDecimal.ONE.add(monthlyRate);


        BigDecimal power =
                onePlusRate.pow(months);


        BigDecimal numerator =
                principal
                        .multiply(monthlyRate)
                        .multiply(power);


        BigDecimal denominator =
                power.subtract(
                        BigDecimal.ONE
                );


        if (denominator.compareTo(
                BigDecimal.ZERO) == 0) {

            throw new IllegalStateException(
                    "Unable to calculate monthly loan installment."
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
    // GET NEXT INSTALLMENT
    // ============================================================

    @Transactional(readOnly = true)
    public PaymentSchedule getNextInstallment(
            Long loanId) {

        if (loanId == null) {
            return null;
        }

        return repository
                .findFirstByLoanIdAndStatusOrderByInstallmentNumberAsc(
                        loanId,
                        ScheduleStatus.PENDING
                )
                .orElse(null);
    }


    // ============================================================
    // MONEY NORMALIZATION
    // ============================================================

    /**
     * Normalizes monetary values to exactly two decimals.
     */
    private BigDecimal money(BigDecimal value) {

        if (value == null) {

            return ZERO;
        }

        return value.setScale(
                MONEY_SCALE,
                ROUNDING_MODE
        );
    }
}
