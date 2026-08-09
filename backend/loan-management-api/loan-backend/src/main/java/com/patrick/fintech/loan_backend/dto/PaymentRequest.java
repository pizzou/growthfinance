package com.patrick.fintech.loan_backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class PaymentRequest {

    @NotNull
    @DecimalMin(value = "0.01")
    @JsonProperty("amount")
    private BigDecimal amount;

    @NotBlank
    private String paymentMethod;

    private String transactionId;

    private String channel;

    private String notes;
}
