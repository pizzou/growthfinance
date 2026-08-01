package com.patrick.fintech.loan_backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyMath {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    public static final BigDecimal HUNDRED =
            BigDecimal.valueOf(100);

    public static final BigDecimal TWELVE =
            BigDecimal.valueOf(12);

    private MoneyMath() {
    }

    public static BigDecimal of(Double value) {
        if (value == null) {
            return ZERO;
        }

        return BigDecimal
                .valueOf(value)
                .setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(double value) {
        return BigDecimal
                .valueOf(value)
                .setScale(SCALE, ROUNDING);
    }

    public static BigDecimal amount(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }

        return value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal round(BigDecimal value) {
        return amount(value);
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return amount(amount(a).add(amount(b)));
    }

    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return amount(amount(a).subtract(amount(b)));
    }

    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return amount(amount(a).multiply(amount(b)));
    }

    public static boolean isZero(BigDecimal value) {
        return amount(value).compareTo(ZERO) == 0;
    }

    public static boolean isPositive(BigDecimal value) {
        return amount(value).compareTo(ZERO) > 0;
    }

    public static double toDouble(BigDecimal value) {
        return amount(value).doubleValue();
    }
}