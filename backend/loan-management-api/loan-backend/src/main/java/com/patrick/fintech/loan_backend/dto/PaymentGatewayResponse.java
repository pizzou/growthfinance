
package com.patrick.fintech.loan_backend.dto;

import lombok.Data;

@Data
public class PaymentGatewayResponse {

    private String status;
    private String message;
    private String transactionId;

    private String providerReference;
    private String flwRef;

    private Double amount;
    private String currency;

    private String paymentType;
    private String provider;

    private String phoneNumber;
    private String network;

    private String redirectUrl;
    private String instructions;
    private String providerCode;


 

    public static PaymentGatewayResponse success(
            String message,
            String transactionId,
            Double amount,
            String currency,
            String paymentType,
            String provider) {

        PaymentGatewayResponse response =
                new PaymentGatewayResponse();

        response.setStatus("success");
        response.setMessage(message);
        response.setTransactionId(transactionId);
        response.setAmount(amount);
        response.setCurrency(currency);
        response.setPaymentType(paymentType);
        response.setProvider(provider);

        return response;
    }




    public static PaymentGatewayResponse success(
            String message,
            String transactionId,
            String provider) {

        PaymentGatewayResponse response =
                new PaymentGatewayResponse();

        response.setStatus("success");
        response.setMessage(message);
        response.setTransactionId(transactionId);
        response.setProvider(provider);

        return response;
    }




    public static PaymentGatewayResponse pending(
            String message,
            String transactionId,
            String provider) {

        PaymentGatewayResponse response =
                new PaymentGatewayResponse();

        response.setStatus("pending");
        response.setMessage(message);
        response.setTransactionId(transactionId);
        response.setProvider(provider);

        return response;
    }

    public static PaymentGatewayResponse failed(
            String message,
            String provider) {

        PaymentGatewayResponse response =
                new PaymentGatewayResponse();

        response.setStatus("failed");
        response.setMessage(message);
        response.setProvider(provider);

        return response;
    }
}
