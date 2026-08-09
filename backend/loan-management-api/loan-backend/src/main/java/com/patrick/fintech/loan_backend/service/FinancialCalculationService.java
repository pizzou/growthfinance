package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.util.MoneyMath;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;


@Service
public class FinancialCalculationService {

    private static final int RATE_SCALE = 12;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal THIRTY = BigDecimal.valueOf(30);
    private static final BigDecimal PENALTY_MONTHLY_RATE = new BigDecimal("0.02");

    public BigDecimal money(Number value) {
        if (value == null) {
            return MoneyMath.ZERO;
        }
        return new BigDecimal(value.toString()).setScale(MoneyMath.SCALE, ROUNDING);
    }

    public BigDecimal dailyRate(Number interestRate, String interestRateType) {
        BigDecimal rate = interestRate == null
                ? BigDecimal.ZERO
                : new BigDecimal(interestRate.toString());

        if (rate.signum() <= 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, ROUNDING);
        }

        BigDecimal normalized = rate.divide(ONE_HUNDRED, RATE_SCALE, ROUNDING);

        if ("ANNUAL".equalsIgnoreCase(interestRateType)) {
            return normalized.divide(TWELVE, RATE_SCALE, ROUNDING)
                    .divide(THIRTY, RATE_SCALE, ROUNDING);
        }

        // MONTHLY is the system default for unknown/blank rate types.
        return normalized.divide(THIRTY, RATE_SCALE, ROUNDING);
    }

    public BigDecimal interest(BigDecimal principal, BigDecimal dailyRate, long days) {
        if (principal == null || dailyRate == null || days <= 0) {
            return MoneyMath.ZERO;
        }
        return principal.multiply(dailyRate)
                .multiply(BigDecimal.valueOf(days))
                .setScale(MoneyMath.SCALE, ROUNDING);
    }

    public BigDecimal penalty(BigDecimal principal, int daysLate) {
        if (principal == null || principal.signum() <= 0 || daysLate <= 0) {
            return MoneyMath.ZERO;
        }
        BigDecimal dailyPenaltyRate = PENALTY_MONTHLY_RATE.divide(THIRTY, RATE_SCALE, ROUNDING);
        return principal.multiply(dailyPenaltyRate)
                .multiply(BigDecimal.valueOf(daysLate))
                .setScale(MoneyMath.SCALE, ROUNDING);
    }

    public Allocation allocatePayment(
            BigDecimal payment,
            BigDecimal newPenalty,
            BigDecimal remainingInterest,
            BigDecimal principalBalance) {

        BigDecimal amount = money(payment);
        BigDecimal penalty = nonNegative(newPenalty);
        BigDecimal interest = nonNegative(remainingInterest);
        BigDecimal principal = nonNegative(principalBalance);

        BigDecimal afterPenalty = amount.subtract(penalty).max(BigDecimal.ZERO);
        BigDecimal interestPaid = afterPenalty.min(interest).setScale(MoneyMath.SCALE, ROUNDING);
        BigDecimal principalAvailable = afterPenalty.subtract(interestPaid).max(BigDecimal.ZERO);
        BigDecimal principalPaid = principalAvailable.min(principal).setScale(MoneyMath.SCALE, ROUNDING);
        BigDecimal newBalance = principal.subtract(principalPaid).max(BigDecimal.ZERO)
                .setScale(MoneyMath.SCALE, ROUNDING);

        return new Allocation(
                interestPaid,
                principalPaid,
                newBalance,
                principalAvailable.subtract(principalPaid).max(BigDecimal.ZERO)
                        .setScale(MoneyMath.SCALE, ROUNDING)
        );
    }

    public BigDecimal nonNegative(BigDecimal value) {
        return money(value).max(BigDecimal.ZERO).setScale(MoneyMath.SCALE, ROUNDING);
    }

    public record Allocation(
            BigDecimal interestPaid,
            BigDecimal principalPaid,
            BigDecimal newPrincipalBalance,
            BigDecimal unappliedAfterInterestAndPenalty) {
    }
}
