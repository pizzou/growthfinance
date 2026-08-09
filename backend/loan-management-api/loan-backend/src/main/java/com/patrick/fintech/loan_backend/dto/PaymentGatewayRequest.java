
package com.patrick.fintech.loan_backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentGatewayRequest {

    private Long paymentId;

    @NotNull
    

    @JsonProperty("amount")
private BigDecimal amount;

    @NotBlank
    private String paymentMethod;

    private String provider;

    private String phoneNumber;

    private String network;

    private String email;

    private String redirectUrl;

    private String cardNumber;
    private String cardCvv;
    private String cardExpiryMonth;
    private String cardExpiryYear;

    private String accountNumber;
    private String bankCode;

    @Deprecated
    @JsonIgnore
    public Double getAmount() {
        return amount == null ? null : amount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getAmountDecimal() {
        return amount;
    }

    @Deprecated
    public void setAmount(Double value) {
        this.amount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setAmount(BigDecimal value) {
        this.amount = value;
    }
}
