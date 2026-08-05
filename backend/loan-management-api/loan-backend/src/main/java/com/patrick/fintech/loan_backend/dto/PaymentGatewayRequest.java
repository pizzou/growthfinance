
package com.patrick.fintech.loan_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentGatewayRequest {

    private Long paymentId;

    @NotNull
    private Double amount;

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
}