package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayRequest;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayResponse;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.service.FlutterwaveService;
import com.patrick.fintech.loan_backend.service.PaymentService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Initiates a real payment (card, mobile money, or bank transfer) through
 * Flutterwave for a loan repayment.
 *
 * With no FLUTTERWAVE_SECRET_KEY configured, this runs in simulation mode
 * (no real charge, payment recorded immediately) — safe for demos. Set real
 * credentials and it starts making real charges automatically, no code
 * changes needed.
 *
 * Card/bank-transfer/some mobile-money charges are asynchronous — Flutterwave
 * confirms them later via webhook (see PaymentWebhookController), not in
 * this response. Simulation mode always completes immediately.
 */
@RestController
@RequestMapping("/api/loans/{loanId}/payments/gateway")
@RequiredArgsConstructor
@Slf4j
public class PaymentGatewayController {

    private final FlutterwaveService flutterwaveService;
    private final PaymentService     paymentService;
    private final LoanRepository     loanRepo;
    private final CurrentUserUtil    currentUserUtil;

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<Map<String,Object>>> initiate(
            @PathVariable Long loanId, @Valid @RequestBody PaymentGatewayRequest req) {

        var user = currentUserUtil.getCurrentUser();
        Loan loan = loanRepo.findById(loanId).orElseThrow(() -> new RuntimeException("Loan not found"));
        if (!loan.getOrganization().getId().equals(user.getOrganization().getId()))
            throw new RuntimeException("Access denied");

        PaymentGatewayResponse gw = flutterwaveService.initiatePayment(
            loanId, req, req.getAmount(), loan.getCurrency(),
            "Loan repayment " + loan.getReferenceNumber());

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("status", gw.getStatus());
        result.put("message", gw.getMessage());
        result.put("transactionId", gw.getTransactionId());
        result.put("redirectUrl", gw.getRedirectUrl()); // present for card 3DS — frontend should redirect here if set

        if ("success".equals(gw.getStatus())) {
            // Simulation mode, or a gateway that confirms synchronously — record right away
            paymentService.recordPayment(loanId, gw.getAmount() != null ? gw.getAmount() : req.getAmount(),
                req.getPaymentMethod(), gw.getTransactionId(), "GATEWAY", "Paid via Flutterwave", user);
            result.put("recorded", true);
            return ResponseEntity.ok(ApiResponse.ok("Payment completed", result));
        }

        if ("pending".equals(gw.getStatus())) {
            // Real mobile money / bank transfer — waiting on the borrower to approve on their phone,
            // or on bank settlement. The webhook completes this, NOT this response.
            result.put("recorded", false);
            return ResponseEntity.ok(ApiResponse.ok(
                "Payment initiated — waiting for the borrower to confirm on their phone, or for bank settlement", result));
        }

        throw new RuntimeException("Payment failed: " + gw.getMessage());
    }
}
