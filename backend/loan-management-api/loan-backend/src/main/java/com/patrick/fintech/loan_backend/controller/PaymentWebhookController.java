package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.service.FlutterwaveService;
import com.patrick.fintech.loan_backend.service.IdempotencyService;
import com.patrick.fintech.loan_backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Receives asynchronous payment confirmations from Flutterwave — this is how
 * mobile money and bank transfer payments actually complete (the borrower
 * approves on their phone or the bank settles, sometimes minutes later; the
 * initiate call in PaymentGatewayController can't know the outcome yet).
 *
 * Configure this URL in your Flutterwave dashboard:
 *   https://yourdomain.com/api/public/webhooks/flutterwave
 *
 * Security: never trusts the webhook payload's claimed status — always
 * re-verifies the transaction directly with Flutterwave's API before
 * recording a payment. Also checks the "verif-hash" header against
 * FLUTTERWAVE_WEBHOOK_SECRET when configured, and is idempotent against
 * Flutterwave's automatic webhook retries.
 */
@RestController
@RequestMapping("/api/public/webhooks")
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookController {

    private final FlutterwaveService  flutterwaveService;
    private final PaymentService      paymentService;
    private final LoanRepository      loanRepo;
    private final IdempotencyService  idempotencyService;

    @Value("${flutterwave.webhook-secret:}")
    private String webhookSecret;

    @PostMapping("/flutterwave")
    public ResponseEntity<String> handleFlutterwaveWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "verif-hash", required = false) String signature) {

        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (signature == null || !signature.equals(webhookSecret)) {
                log.warn("[FLW Webhook] Rejected — missing or invalid verif-hash signature");
                return ResponseEntity.status(401).body("Invalid signature");
            }
        }

        Object dataObj = payload.get("data");
        if (!(dataObj instanceof Map)) return ResponseEntity.ok("ignored: no data");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) dataObj;

        String txId  = String.valueOf(data.get("id"));
        String txRef = String.valueOf(data.get("tx_ref"));
        Long loanId  = FlutterwaveService.loanIdFromTxRef(txRef);
        if (loanId == null) {
            log.warn("[FLW Webhook] Could not parse loan id from tx_ref={}", txRef);
            return ResponseEntity.ok("ignored: unrecognized tx_ref");
        }

        // Never trust the payload's claimed status — always re-verify server-to-server
        boolean verified = flutterwaveService.verifyTransaction(txId);
        if (!verified) {
            log.warn("[FLW Webhook] Transaction {} failed re-verification, not recording", txId);
            return ResponseEntity.ok("not verified");
        }

        Loan loan = loanRepo.findById(loanId).orElse(null);
        if (loan == null) {
            log.warn("[FLW Webhook] Loan {} from tx_ref not found", loanId);
            return ResponseEntity.ok("ignored: unknown loan");
        }

        // Flutterwave retries webhooks until it gets a 200 — this stops a retry from
        // recording the same payment twice.
        var idempotency = idempotencyService.checkOrReserve(
            "flw-webhook-" + txId, loan.getOrganization(), "POST /webhooks/flutterwave", payload.toString());
        if (idempotency.isReplay()) return ResponseEntity.ok("already processed");

        Object amountObj = data.get("amount");
        double amount = amountObj instanceof Number n ? n.doubleValue() : 0;
        String method = String.valueOf(data.getOrDefault("payment_type", "MOBILE_MONEY")).toUpperCase();

        try {
            paymentService.recordPayment(loanId, amount, method, txId, "GATEWAY_WEBHOOK",
                "Confirmed via Flutterwave webhook", null);
            log.info("[FLW Webhook] Recorded payment of {} on loan {} (txId={})", amount, loanId, txId);
        } catch (Exception e) {
            log.error("[FLW Webhook] Failed to record confirmed payment for loan {}: {}", loanId, e.getMessage());
            return ResponseEntity.ok("error recording payment"); // still 200 — don't let Flutterwave hammer retries on a data issue
        }

        return ResponseEntity.ok("processed");
    }
}
