package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.service.IdempotencyService;
import com.patrick.fintech.loan_backend.service.PaymentService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/loans/{loanId}/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService     paymentService;
    private final CurrentUserUtil    currentUserUtil;
    private final IdempotencyService idempotencyService;

    /**
     * Record a manual payment (cash, bank transfer, mobile money, etc.)
     *
     * Accepts an optional Idempotency-Key header — this matters most for
     * payments recorded while offline and synced later: if a sync retry
     * ever resends the same request (flaky connection, app killed mid-sync),
     * the same key returns the original result instead of recording the
     * payment twice.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Payment>> recordPayment(
            @PathVariable Long loanId,
            @RequestBody Map<String,Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        var org = currentUserUtil.getCurrentUser().getOrganization();
        var outcome = idempotencyService.checkOrReserve(idempotencyKey, org, "POST /loans/" + loanId + "/payments", body.toString());
        if (outcome.isReplay()) {
            return ResponseEntity.ok(ApiResponse.ok("Payment recorded", null));
        }

        try {
            Double amount  = Double.parseDouble(body.get("amount").toString());
            String method  = body.getOrDefault("paymentMethod","BANK_TRANSFER").toString();
            String txnId   = body.getOrDefault("transactionId","").toString();
            String channel = body.getOrDefault("channel","").toString();
            String notes   = body.getOrDefault("notes","").toString();
            Payment p = paymentService.recordPayment(
                loanId, amount, method, txnId, channel, notes,
                currentUserUtil.getCurrentUser());
            idempotencyService.recordSuccess(idempotencyKey, org, p, 200);
            return ResponseEntity.ok(ApiResponse.ok("Payment recorded", p));
        } catch (Exception e) {
            idempotencyService.recordFailure(idempotencyKey, org);
            throw e;
        }
    }

    /** Get full repayment schedule for a loan */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Payment>>> getSchedule(@PathVariable Long loanId) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        return ResponseEntity.ok(ApiResponse.ok(
            paymentService.getLoanSchedule(loanId, orgId)));
    }
}
