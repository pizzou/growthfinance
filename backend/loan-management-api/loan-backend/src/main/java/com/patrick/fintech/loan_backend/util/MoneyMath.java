package com.patrick.fintech.loan_backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Central monetary normalization utilities.
 *
 * Financial values must never be calculated using binary floating point.
 * BigDecimal is the canonical representation inside financial calculations;
 * conversion to double is retained only at legacy API boundaries.
 */
public final class MoneyMath {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
    public static final BigDecimal ONE = BigDecimal.ONE.setScale(SCALE, ROUNDING);
    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    public static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    public static final BigDecimal THIRTY = BigDecimal.valueOf(30);

    private MoneyMath() {
    }

    public static BigDecimal of(Number value) {
        if (value == null) {
            return ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(SCALE, ROUNDING);
        }
        return new BigDecimal(value.toString()).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal amount(BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, ROUNDING);
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

    public static BigDecimal divide(BigDecimal a, BigDecimal b, int scale) {
        if (b == null || b.signum() == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return amount(a).divide(b, scale, ROUNDING);
    }

    public static boolean isZero(BigDecimal value) {
        return amount(value).compareTo(ZERO) == 0;
    }

    public static boolean isPositive(BigDecimal value) {
        return amount(value).compareTo(ZERO) > 0;
    }

    public static boolean isNonNegative(BigDecimal value) {
        return amount(value).compareTo(ZERO) >= 0;
    }

    /** Legacy boundary only. Do not use for financial arithmetic. */
    public static double toDouble(BigDecimal value) {
        return amount(value).doubleValue();
    }
}
