package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Payment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FinancialPrecisionModelTest {

    @Test
    void borrowerStoresFinancialValuesAsBigDecimal() {
        Borrower borrower = new Borrower();
        borrower.setMonthlyIncome(new BigDecimal("123456.789123"));
        borrower.setMonthlyExpenses(new BigDecimal("45678.123456"));
        borrower.setNetWorth(new BigDecimal("9876543.123456"));

        assertEquals(new BigDecimal("123456.789123"), borrower.getMonthlyIncomeDecimal());
        assertEquals(new BigDecimal("45678.123456"), borrower.getMonthlyExpensesDecimal());
        assertEquals(new BigDecimal("9876543.123456"), borrower.getNetWorthDecimal());
    }

    @Test
    void loanStoresRatesAndAmountsAsBigDecimal() {
        Loan loan = new Loan();
        loan.setAmount(new BigDecimal("100000.123456"));
        loan.setInterestRate(new BigDecimal("12.345678901"));
        loan.setOutstandingBalance(new BigDecimal("99999.654321"));

        assertEquals(new BigDecimal("100000.123456"), loan.getAmountDecimal());
        assertEquals(new BigDecimal("12.345678901"), loan.getInterestRateDecimal());
        assertEquals(new BigDecimal("99999.654321"), loan.getOutstandingBalanceDecimal());
    }

    @Test
    void legacyDoubleBoundaryDoesNotChangeStoredDecimalValue() {
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("1250.55"));

        assertEquals(new BigDecimal("1250.55"), payment.getAmountDecimal());
        assertEquals(1250.55d, payment.getAmount(), 0.0000001d);
    }

    @Test
    void nullFinancialValuesRemainNull() {
        Borrower borrower = new Borrower();
        assertNull(borrower.getMonthlyIncomeDecimal());
        assertNull(borrower.getMonthlyExpensesDecimal());
        assertNull(borrower.getNetWorthDecimal());
    }
}
