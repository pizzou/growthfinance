package com.patrick.fintech.loan_backend.dto;

import lombok.Data;

@Data
public class PaymentGatewayResponse {
    private String  status;         // success, pending, failed
    private String  message;
    private String  transactionId;  // Flutterwave tx ref
    private String  flwRef;
    private Double  amount;
    private String  currency;
    private String  paymentType;
    private String  redirectUrl;    // for card 3DS
}