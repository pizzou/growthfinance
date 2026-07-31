package com.patrick.fintech.loan_backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentRequest {

    @NotNull
    @DecimalMin("0.01")
    private Double amount;

    @NotBlank
    private String paymentMethod;  // CASH, CARD, MOBILE_MONEY, BANK_TRANSFER

    private String transactionId;  // optional reference number
}