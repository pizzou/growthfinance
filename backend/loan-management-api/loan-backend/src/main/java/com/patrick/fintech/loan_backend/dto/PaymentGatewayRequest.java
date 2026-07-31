package com.patrick.fintech.loan_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentGatewayRequest {
    /** Optional — if omitted, the earliest unpaid installment on the loan is paid (see PaymentService.recordPayment). */
    private Long paymentId;

    @NotNull
    private Double amount;

    @NotBlank
    private String paymentMethod;   // CARD, MOBILE_MONEY, BANK_TRANSFER

    // Card fields
    private String cardNumber;
    private String cardCvv;
    private String cardExpiryMonth;
    private String cardExpiryYear;

    // Mobile money fields
    private String phoneNumber;
    private String network;          // MTN, AIRTEL, VODAFONE

    // Bank transfer
    private String accountNumber;
    private String bankCode;

    // Common
    private String email;
    private String redirectUrl;
}