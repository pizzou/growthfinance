package com.patrick.fintech.loan_backend.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinancialCalculationServiceTest {

    private final FinancialCalculationService service = new FinancialCalculationService();

    @Test
    void monthlyDailyRateUsesThirtyDayCycle() {
        BigDecimal rate = service.dailyRate(new BigDecimal("10.00"), "MONTHLY");
        assertEquals(new BigDecimal("0.003333333333"), rate);
    }

    @Test
    void annualDailyRateUsesAnnualToMonthlyToThirtyDayConversion() {
        BigDecimal rate = service.dailyRate(new BigDecimal("12.00"), "ANNUAL");
        assertEquals(new BigDecimal("0.000333333333"), rate);
    }

    @Test
    void paymentAllocationAlwaysAppliesPenaltyThenInterestThenPrincipal() {
        FinancialCalculationService.Allocation allocation = service.allocatePayment(
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                new BigDecimal("30.00"),
                new BigDecimal("500.00")
        );

        assertEquals(new BigDecimal("30.00"), allocation.interestPaid());
        assertEquals(new BigDecimal("60.00"), allocation.principalPaid());
        assertEquals(new BigDecimal("440.00"), allocation.newPrincipalBalance());
    }

    @Test
    void interestAndPenaltyAreRoundedOnlyAtMoneyBoundary() {
        BigDecimal dailyRate = service.dailyRate(new BigDecimal("10.00"), "MONTHLY");
        BigDecimal interest = service.interest(new BigDecimal("1000.00"), dailyRate, 3);
        BigDecimal penalty = service.penalty(new BigDecimal("1000.00"), 2);

        assertEquals(new BigDecimal("10.00"), interest);
        assertEquals(new BigDecimal("1.33"), penalty);
    }
}
