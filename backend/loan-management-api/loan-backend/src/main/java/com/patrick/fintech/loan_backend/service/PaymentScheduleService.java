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

private static final BigDecimal ONE_HUNDRED =
        new BigDecimal("100");

private static final BigDecimal TWELVE =
        new BigDecimal("12");

private static final BigDecimal ZERO =
        BigDecimal.ZERO;

private static final BigDecimal ONE =
        BigDecimal.ONE;

private static final BigDecimal ONE_CENT =
        new BigDecimal("0.01");

/**
 * Internal calculation precision.
 *
 * Financial calculations are performed using BigDecimal.
 * Money is rounded to two decimal places only when stored
 * as an actual monetary amount.
 */
private static final int CALCULATION_SCALE = 16;

/**
 * Database/API monetary scale.
 */
private static final int MONEY_SCALE = 2;


// ================================================================
// GET SCHEDULE
// ================================================================

@Transactional(readOnly = true)
public List<PaymentScheduleResponse> getSchedule(Long loanId) {

    if (loanId == null) {
        throw new IllegalArgumentException(
                "Loan ID is required"
        );
    }

    return repository
            .findByLoanIdOrderByInstallmentNumberAsc(loanId)
            .stream()
            .map(this::toResponse)
            .toList();
}


// ================================================================
// CONVERT TO RESPONSE
// ================================================================

private PaymentScheduleResponse toResponse(
        PaymentSchedule schedule
) {

    if (schedule == null) {
        return null;
    }

    return PaymentScheduleResponse.builder()
            .installmentNumber(
                    schedule.getInstallmentNumber()
            )
            .dueDate(
                    schedule.getDueDate()
            )
            .installmentAmount(
                    money(
                            schedule.getInstallmentAmount()
                    )
            )
            .principal(
                    money(
                            schedule.getPrincipalAmount()
                    )
            )
            .interest(
                    money(
                            schedule.getInterestAmount()
                    )
            )
            .penalty(
                    money(
                            schedule.getPenaltyAmount()
                    )
            )
            .paid(
                    money(
                            schedule.getAmountPaid()
                    )
            )
            .balance(
                    money(
                            schedule.getRemainingBalance()
                    )
            )
            .status(
                    schedule.getStatus() != null
                            ? schedule.getStatus().name()
                            : ScheduleStatus.PENDING.name()
            )
            .build();
}



@Transactional
public void generateSchedule(
        Loan loan
) {

    // ------------------------------------------------------------
    // BASIC VALIDATION
    // ------------------------------------------------------------

    if (loan == null) {
        throw new IllegalArgumentException(
                "Loan cannot be null"
        );
    }

    if (loan.getId() == null) {
        throw new IllegalArgumentException(
                "Loan must be persisted before generating a schedule"
        );
    }

    if (loan.getOrganization() == null
            || loan.getOrganization().getId() == null) {

        throw new IllegalArgumentException(
                "Loan organization is required"
        );
    }

    
    BigDecimal amount =
            loan.getAmountDecimal();

    if (amount == null) {
        throw new IllegalArgumentException(
                "Loan principal is required"
        );
    }

   
    BigDecimal interestRate =
            loan.getInterestRateDecimal();

    if (interestRate == null) {
        throw new IllegalArgumentException(
                "Loan interest rate is required"
        );
    }

    if (loan.getDurationMonths() == null) {
        throw new IllegalArgumentException(
                "Loan duration is required"
        );
    }


    // ------------------------------------------------------------
    // DURATION
    // ------------------------------------------------------------

    int months =
            loan.getDurationMonths();

    if (months <= 0) {
        throw new IllegalArgumentException(
                "Loan duration must be greater than zero"
        );
    }


    // ------------------------------------------------------------
    // NORMALIZE PRINCIPAL
    // ------------------------------------------------------------

    BigDecimal principal =
            normalizeMoney(amount);

    if (principal.compareTo(ZERO) <= 0) {
        throw new IllegalArgumentException(
                "Loan principal must be greater than zero"
        );
    }


    // ------------------------------------------------------------
    // VALIDATE INTEREST RATE
    // ------------------------------------------------------------

    BigDecimal rate =
            interestRate.setScale(
                    CALCULATION_SCALE,
                    RoundingMode.HALF_UP
            );

    if (rate.compareTo(ZERO) < 0) {
        throw new IllegalArgumentException(
                "Loan interest rate cannot be negative"
        );
    }


    // ------------------------------------------------------------
    // INTEREST RATE TYPE
    // ------------------------------------------------------------

    String rateType =
            loan.getInterestRateType();

    if (rateType == null
            || rateType.isBlank()) {

        rateType = "MONTHLY";
    }

    rateType =
            rateType
                    .trim()
                    .toUpperCase();

    validateRateType(
            rateType
    );


    // ------------------------------------------------------------
    // RESOLVE SCHEDULE START DATE
    // ------------------------------------------------------------

    LocalDate baseDate =
            resolveScheduleStartDate(
                    loan
            );


    // ------------------------------------------------------------
    // PRODUCTION SAFETY
    //
    // Do not destroy a schedule that already contains
    // payment activity.
    // ------------------------------------------------------------

    List<PaymentSchedule> existingSchedules =
            repository.findByLoanIdOrderByInstallmentNumberAsc(
                    loan.getId()
            );

    if (!existingSchedules.isEmpty()) {

        boolean hasPaymentActivity =
                existingSchedules.stream()
                        .anyMatch(this::hasPaymentActivity);

        if (hasPaymentActivity) {

            throw new IllegalStateException(
                    "Cannot regenerate payment schedule for loan "
                            + loan.getReferenceNumber()
                            + " because payment activity already exists"
            );
        }

        /*
         * Existing schedule has no payment activity.
         * It is safe to replace it.
         */
        repository.deleteByLoanId(
                loan.getId()
        );
    }


    // ------------------------------------------------------------
    // CALCULATE MONTHLY RATE
    // ------------------------------------------------------------

    BigDecimal monthlyRate =
            calculateMonthlyRate(
                    rate,
                    rateType
            );


    // ------------------------------------------------------------
    // CALCULATE MONTHLY PAYMENT
    // ------------------------------------------------------------

    BigDecimal monthlyPayment =
            calculateMonthlyPayment(
                    principal,
                    monthlyRate,
                    months
            );


    log.info(
            "Generating payment schedule for loan {}: principal={}, rate={}, rateType={}, months={}, monthlyRate={}, monthlyPayment={}",
            loan.getReferenceNumber(),
            principal,
            rate,
            rateType,
            months,
            monthlyRate,
            monthlyPayment
    );


    // ------------------------------------------------------------
    // GENERATE INSTALLMENTS
    // ------------------------------------------------------------

    BigDecimal balance =
            principal;

    for (
            int installmentNumber = 1;
            installmentNumber <= months;
            installmentNumber++
    ) {

        balance =
                money(balance);


        // --------------------------------------------------------
        // CONTRACTUAL MONTHLY INTEREST
        // --------------------------------------------------------

        BigDecimal interest =
                money(
                        balance.multiply(
                                monthlyRate
                        )
                );


        BigDecimal principalComponent;

        BigDecimal installmentAmount;


        // --------------------------------------------------------
        // FINAL INSTALLMENT
        // --------------------------------------------------------

        if (installmentNumber == months) {

            principalComponent =
                    money(balance);

            installmentAmount =
                    money(
                            principalComponent.add(
                                    interest
                            )
                    );

            balance =
                    ZERO;

        } else {

            // ----------------------------------------------------
            // NORMAL INSTALLMENT
            // ----------------------------------------------------

            installmentAmount =
                    money(
                            monthlyPayment
                    );


            principalComponent =
                    money(
                            installmentAmount.subtract(
                                    interest
                            )
                    );


            // ----------------------------------------------------
            // PROTECT AGAINST NEGATIVE PRINCIPAL
            // ----------------------------------------------------

            if (
                    principalComponent.compareTo(
                            ZERO
                    ) < 0
            ) {

                principalComponent =
                        ZERO;
            }


            // ----------------------------------------------------
            // PROTECT AGAINST EXCESS PRINCIPAL
            // ----------------------------------------------------

            if (
                    principalComponent.compareTo(
                            balance
                    ) > 0
            ) {

                principalComponent =
                        balance;
            }


            // ----------------------------------------------------
            // UPDATE BALANCE
            // ----------------------------------------------------

            balance =
                    money(
                            balance.subtract(
                                    principalComponent
                            )
                    );


            // ----------------------------------------------------
            // REMOVE TINY ROUNDING RESIDUAL
            // ----------------------------------------------------

            if (
                    balance.compareTo(
                            ONE_CENT
                    ) < 0
            ) {

                balance =
                        ZERO;
            }
        }


        // --------------------------------------------------------
        // DUE DATE
        // --------------------------------------------------------

        LocalDate dueDate =
                baseDate.plusMonths(
                        installmentNumber
                );


        // --------------------------------------------------------
        // BUILD PAYMENT SCHEDULE
        // --------------------------------------------------------

        PaymentSchedule schedule =
                PaymentSchedule.builder()
                        .loan(loan)
                        .installmentNumber(
                                installmentNumber
                        )
                        .dueDate(
                                dueDate
                        )
                        .installmentAmount(
                                installmentAmount
                        )
                        .principalAmount(
                                money(
                                        principalComponent
                                )
                        )
                        .interestAmount(
                                money(
                                        interest
                                )
                        )
                        .penaltyAmount(
                                ZERO
                        )
                        .amountPaid(
                                ZERO
                        )
                        .remainingBalance(
                                balance
                        )
                        .status(
                                ScheduleStatus.PENDING
                        )
                        .build();


        repository.save(
                schedule
        );
    }


    // ============================================================
    // SYNCHRONIZE LOAN AGGREGATE
    // ============================================================

    /*
     * The Loan entity now stores monetary values as BigDecimal.
     *
     * Therefore we deliberately use the BigDecimal setters.
     *
     * We do NOT convert these values back to Double.
     */

    loan.setAmount(
            principal
    );

    loan.setOutstandingBalance(
            principal
    );

    loan.setNextDueDate(
            baseDate.plusMonths(1)
    );

    loan.setNextPaymentDate(
            baseDate.plusMonths(1)
    );

    loan.setNextInstallmentAmount(
            monthlyPayment
    );


    log.info(
            "Payment schedule generated successfully for loan {} with {} installments",
            loan.getReferenceNumber(),
            months
    );
}


// ================================================================
// CHECK PAYMENT ACTIVITY
// ================================================================

/**
 * Determines whether an existing schedule contains payment
 * activity that makes schedule regeneration unsafe.
 */
private boolean hasPaymentActivity(
        PaymentSchedule schedule
) {

    if (schedule == null) {
        return false;
    }

    BigDecimal amountPaid =
            schedule.getAmountPaid();

    if (amountPaid != null
            && amountPaid.compareTo(ZERO) > 0) {

        return true;
    }

    ScheduleStatus status =
            schedule.getStatus();

    return status == ScheduleStatus.PAID
            || status == ScheduleStatus.PARTIAL;
}


// ================================================================
// NEXT INSTALLMENT
// ================================================================

@Transactional(readOnly = true)
public PaymentSchedule getNextInstallment(
        Long loanId
) {

    if (loanId == null) {
        throw new IllegalArgumentException(
                "Loan ID is required"
        );
    }

    return repository
            .findFirstByLoanIdAndStatusOrderByInstallmentNumberAsc(
                    loanId,
                    ScheduleStatus.PENDING
            )
            .orElseGet(
                    () ->
                            repository
                                    .findFirstByLoanIdAndStatusOrderByInstallmentNumberAsc(
                                            loanId,
                                            ScheduleStatus.PARTIAL
                                    )
                                    .orElse(null)
            );
}


// ================================================================
// MONTHLY RATE
// ================================================================

/**
 * Converts the configured interest rate into a contractual
 * monthly rate.
 *
 * MONTHLY:
 *
 *     10% / 100
 *
 *     = 0.10 monthly
 *
 * ANNUAL:
 *
 *     10% / 100 / 12
 *
 *     = 0.008333...
 *
 * IMPORTANT:
 *
 * This is the contractual monthly schedule rate.
 *
 * It is NOT the elapsed-day payment interest calculation.
 */
private BigDecimal calculateMonthlyRate(
        BigDecimal rate,
        String rateType
) {

    if (rate == null) {
        throw new IllegalArgumentException(
                "Interest rate cannot be null"
        );
    }

    if ("MONTHLY".equalsIgnoreCase(rateType)) {

        return rate.divide(
                ONE_HUNDRED,
                CALCULATION_SCALE,
                RoundingMode.HALF_UP
        );
    }

    return rate
            .divide(
                    ONE_HUNDRED,
                    CALCULATION_SCALE,
                    RoundingMode.HALF_UP
            )
            .divide(
                    TWELVE,
                    CALCULATION_SCALE,
                    RoundingMode.HALF_UP
            );
}


// ================================================================
// MONTHLY PAYMENT
// ================================================================

/**
 * Calculates the contractual monthly EMI.
 *
 * Zero interest:
 *
 *     P / n
 *
 * Normal interest:
 *
 *          P * r
 *     ----------------
 *     1 - (1 + r)^-n
 *
 * BigDecimal is used for all monetary values.
 *
 * Math.pow() is used only for the mathematical exponentiation
 * required by the EMI formula.
 */
private BigDecimal calculateMonthlyPayment(
        BigDecimal principal,
        BigDecimal monthlyRate,
        int months
) {

    if (principal == null) {
        throw new IllegalArgumentException(
                "Principal cannot be null"
        );
    }

    if (monthlyRate == null) {
        throw new IllegalArgumentException(
                "Monthly rate cannot be null"
        );
    }

    if (months <= 0) {
        throw new IllegalArgumentException(
                "Number of months must be greater than zero"
        );
    }


    // ------------------------------------------------------------
    // ZERO INTEREST
    // ------------------------------------------------------------

    if (
            monthlyRate.compareTo(
                    ZERO
            ) == 0
    ) {

        return money(
                principal.divide(
                        BigDecimal.valueOf(months),
                        CALCULATION_SCALE,
                        RoundingMode.HALF_UP
                )
        );
    }


    // ------------------------------------------------------------
    // EMI EXPONENT
    // ------------------------------------------------------------

    double rateDouble =
            monthlyRate.doubleValue();

    double factorDouble =
            Math.pow(
                    1.0 + rateDouble,
                    -months
            );


    if (
            Double.isNaN(
                    factorDouble
            )
                    || Double.isInfinite(
                    factorDouble
            )
    ) {

        throw new IllegalArgumentException(
                "Unable to calculate monthly installment"
        );
    }


    BigDecimal discountFactor =
            BigDecimal.valueOf(
                    factorDouble
            );


    BigDecimal denominator =
            ONE.subtract(
                    discountFactor
            );


    if (
            denominator.compareTo(
                    ZERO
            ) == 0
    ) {

        throw new IllegalArgumentException(
                "Invalid interest calculation"
        );
    }


    BigDecimal payment =
            principal
                    .multiply(
                            monthlyRate
                    )
                    .divide(
                            denominator,
                            CALCULATION_SCALE,
                            RoundingMode.HALF_UP
                    );


    return money(
            payment
    );
}


// ================================================================
// RESOLVE SCHEDULE START DATE
// ================================================================

private LocalDate resolveScheduleStartDate(
        Loan loan
) {

    /*
     * For a disbursed loan, the disbursement date is the
     * authoritative contractual schedule start date.
     */
    if (loan.getDisbursedAt() != null) {

        return loan
                .getDisbursedAt()
                .toLocalDate();
    }


    /*
     * Older/test records may not have disbursedAt.
     */
    if (loan.getStartDate() != null) {

        return loan.getStartDate();
    }


    /*
     * Final fallback for legacy/test data.
     */
    return LocalDate.now();
}


// ================================================================
// VALIDATE RATE TYPE
// ================================================================

private void validateRateType(
        String rateType
) {

    if (
            !"ANNUAL".equalsIgnoreCase(rateType)
                    && !"MONTHLY".equalsIgnoreCase(rateType)
    ) {

        throw new IllegalArgumentException(
                "Interest rate type must be MONTHLY or ANNUAL"
        );
    }
}


// ================================================================
// MONEY
// ================================================================

/**
 * Converts a BigDecimal value into a two-decimal monetary value.
 */
private BigDecimal money(
        BigDecimal value
) {

    if (value == null) {

        return ZERO.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }

    return value.setScale(
            MONEY_SCALE,
            RoundingMode.HALF_UP
    );
}


// ================================================================
// NORMALIZE MONEY
// ================================================================

/**
 * Normalizes an authoritative BigDecimal monetary value.
 *
 * No Double conversion is performed here.
 *
 * This is important because Loan stores its financial
 * fields as BigDecimal.
 */
private BigDecimal normalizeMoney(
        BigDecimal value
) {

    if (value == null) {

        throw new IllegalArgumentException(
                "Money value cannot be null"
        );
    }

    return value.setScale(
            MONEY_SCALE,
            RoundingMode.HALF_UP
    );
}


}
