package com.patrick.fintech.loan_backend.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MakePaymentRequest {

    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @JsonProperty("amount")
    private BigDecimal amount;

    /** e.g. MOBILE_MONEY, BANK_TRANSFER, CASH */
    @NotBlank(message = "Payment method is required")
    private String paymentMethod;

    /** Optional external transaction reference */
    private String transactionId;
}
