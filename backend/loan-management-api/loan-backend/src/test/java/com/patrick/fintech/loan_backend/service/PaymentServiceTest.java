package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Payment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceInterestStateTest {

    @Test
    void schedulePlaceholderMustStillBeFirstInterestCalculation() {
        Payment scheduled = Payment.builder()
                .amount(new BigDecimal("100000.00"))
                .principalComponent(new BigDecimal("80000.00"))
                .interestComponent(new BigDecimal("20000.00"))
                .amountPaid(BigDecimal.ZERO)
                .cycleInterestDue(BigDecimal.ZERO)
                .cycleInterestRemaining(BigDecimal.ZERO)
                .paid(false)
                .status(Payment.PaymentStatus.PENDING)
                .build();

        PaymentService service = new PaymentService(
                null, null, null, null, null, null, null, null, null, null
        );

        assertThat(service.isFirstInterestCalculation(List.of(scheduled)))
                .isTrue();
    }

    @Test
    void actualPaymentMustEndFirstInterestCalculationState() {
        Payment paid = Payment.builder()
                .amount(new BigDecimal("100000.00"))
                .principalComponent(new BigDecimal("80000.00"))
                .interestComponent(new BigDecimal("20000.00"))
                .amountPaid(new BigDecimal("100000.00"))
                .cycleInterestDue(new BigDecimal("20000.00"))
                .cycleInterestRemaining(BigDecimal.ZERO)
                .paid(true)
                .status(Payment.PaymentStatus.COMPLETED)
                .build();

        PaymentService service = new PaymentService(
                null, null, null, null, null, null, null, null, null, null
        );

        assertThat(service.isFirstInterestCalculation(List.of(paid)))
                .isFalse();
    }

    @Test
    void previouslyAccruedCycleInterestMustEndFirstInterestState() {
        Payment accrued = Payment.builder()
                .amount(new BigDecimal("100000.00"))
                .principalComponent(new BigDecimal("80000.00"))
                .interestComponent(new BigDecimal("20000.00"))
                .amountPaid(BigDecimal.ZERO)
                .cycleInterestDue(new BigDecimal("5000.00"))
                .cycleInterestRemaining(new BigDecimal("5000.00"))
                .paid(false)
                .status(Payment.PaymentStatus.PENDING)
                .build();

        PaymentService service = new PaymentService(
                null, null, null, null, null, null, null, null, null, null
        );

        assertThat(service.isFirstInterestCalculation(List.of(accrued)))
                .isFalse();
    }
}
