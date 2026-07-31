package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.PaymentGatewayRequest;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Flutterwave payment gateway.
 * Docs: https://developer.flutterwave.com/docs
 *
 * Supports: Card, Mobile Money (MTN MoMo, Airtel, Vodafone), Bank Transfer
 *
 * Set FLUTTERWAVE_SECRET_KEY in .env for real payments.
 * Leave empty to run in simulation mode (no real charges).
 */
@Slf4j
@Service
public class FlutterwaveService {

    private static final String FLW_BASE = "https://api.flutterwave.com/v3";

    @Value("${flutterwave.secret-key:}")
    private String secretKey;

    @Value("${flutterwave.public-key:}")
    private String publicKey;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    private final WebClient webClient;

    public FlutterwaveService(WebClient.Builder builder) {
        this.webClient = builder
            .baseUrl(FLW_BASE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }

    public PaymentGatewayResponse initiatePayment(
            Long loanId, PaymentGatewayRequest req, Double amount, String currency, String description) {
        if (!isConfigured()) {
            log.info("[FLW SIMULATION] {} {} via {} — SIMULATED SUCCESS", currency, amount, req.getPaymentMethod());
            return simulatedSuccess(amount, currency, req.getPaymentMethod());
        }
        return switch (req.getPaymentMethod().toUpperCase()) {
            case "CARD"          -> chargeCard(loanId, req, amount, currency);
            case "MOBILE_MONEY"  -> chargeMobileMoney(loanId, req, amount, currency);
            case "BANK_TRANSFER" -> chargeBankTransfer(loanId, req, amount, currency);
            default -> throw new RuntimeException("Unsupported payment method: " + req.getPaymentMethod());
        };
    }

    public boolean verifyTransaction(String transactionId) {
        if (!isConfigured()) {
            log.info("[FLW SIMULATION] Verify txId={} — SIMULATED SUCCESS", transactionId);
            return true;
        }
        try {
            Map<?, ?> response = webClient.get()
                .uri("/transactions/" + transactionId + "/verify")
                .header("Authorization", "Bearer " + secretKey)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            if (response == null) return false;
            Map<?, ?> data = (Map<?, ?>) response.get("data");
            return data != null && "successful".equals(data.get("status"));
        } catch (Exception e) {
            log.error("[FLW] Verify failed txId={}: {}", transactionId, e.getMessage());
            return false;
        }
    }

    private PaymentGatewayResponse chargeCard(
            Long loanId, PaymentGatewayRequest req, Double amount, String currency) {
        Map<String, Object> body = new HashMap<>();
        body.put("card_number",  req.getCardNumber());
        body.put("cvv",          req.getCardCvv());
        body.put("expiry_month", req.getCardExpiryMonth());
        body.put("expiry_year",  req.getCardExpiryYear());
        body.put("currency",     currency);
        body.put("amount",       amount);
        body.put("email",        req.getEmail());
        body.put("tx_ref",       txRef(loanId));
        body.put("redirect_url", frontendUrl + "/dashboard/payments/complete");
        return callFlutterwave("/charges?type=card", body, "CARD");
    }

    private PaymentGatewayResponse chargeMobileMoney(
            Long loanId, PaymentGatewayRequest req, Double amount, String currency) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount",       amount);
        body.put("currency",     currency);
        body.put("email",        req.getEmail() != null ? req.getEmail() : "customer@loansaas.com");
        body.put("phone_number", req.getPhoneNumber());
        body.put("network",      req.getNetwork() != null ? req.getNetwork() : "MTN");
        body.put("tx_ref",       txRef(loanId));
        String endpoint = switch (currency.toUpperCase()) {
            case "RWF"       -> "/charges?type=mobile_money_rwanda";
            case "KES"       -> "/charges?type=mpesa";
            case "GHS"       -> "/charges?type=mobile_money_ghana";
            case "UGX"       -> "/charges?type=mobile_money_uganda";
            case "XAF","XOF" -> "/charges?type=mobile_money_franco";
            default          -> "/charges?type=mobile_money_rwanda";
        };
        return callFlutterwave(endpoint, body, "MOBILE_MONEY");
    }

    private PaymentGatewayResponse chargeBankTransfer(
            Long loanId, PaymentGatewayRequest req, Double amount, String currency) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount",       amount);
        body.put("currency",     currency);
        body.put("email",        req.getEmail() != null ? req.getEmail() : "customer@loansaas.com");
        body.put("tx_ref",       txRef(loanId));
        body.put("is_permanent", false);
        return callFlutterwave("/charges?type=bank_transfer", body, "BANK_TRANSFER");
    }

    @SuppressWarnings("unchecked")
    private PaymentGatewayResponse callFlutterwave(
            String endpoint, Map<String, Object> body, String type) {
        try {
            Map<String, Object> response = webClient.post()
                .uri(endpoint)
                .header("Authorization", "Bearer " + secretKey)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            return parseResponse(response, type);
        } catch (Exception e) {
            log.error("[FLW] {} payment failed: {}", type, e.getMessage());
            throw new RuntimeException(type + " payment failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private PaymentGatewayResponse parseResponse(Map<String, Object> response, String type) {
        PaymentGatewayResponse r = new PaymentGatewayResponse();
        r.setPaymentType(type);
        if (response == null) { r.setStatus("failed"); r.setMessage("No response"); return r; }
        r.setStatus("success".equals(response.get("status")) ? "pending" : "failed");
        r.setMessage((String) response.get("message"));
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        if (data != null) {
            r.setTransactionId(String.valueOf(data.getOrDefault("id", "")));
            r.setFlwRef((String) data.get("flw_ref"));
            r.setRedirectUrl((String) data.get("redirect"));
            Object amt = data.get("amount");
            if (amt instanceof Number) r.setAmount(((Number) amt).doubleValue());
        }
        return r;
    }

    private PaymentGatewayResponse simulatedSuccess(Double amount, String currency, String method) {
        PaymentGatewayResponse r = new PaymentGatewayResponse();
        r.setStatus("success");
        r.setMessage("Simulated payment successful");
        r.setTransactionId("SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        r.setFlwRef("FLW-SIM-" + System.currentTimeMillis());
        r.setAmount(amount);
        r.setCurrency(currency);
        r.setPaymentType(method);
        return r;
    }

    private String txRef(Long loanId) {
        return "LOAN-" + loanId + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /** Parses the loan id back out of a tx_ref produced by txRef() — used by the webhook handler. */
    public static Long loanIdFromTxRef(String txRef) {
        try {
            String[] parts = txRef.split("-");
            return parts.length >= 2 ? Long.valueOf(parts[1]) : null;
        } catch (Exception e) { return null; }
    }
}