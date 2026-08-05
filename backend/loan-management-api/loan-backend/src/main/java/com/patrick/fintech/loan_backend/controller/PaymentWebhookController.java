
package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.service.AirtelMobileMoneyService;
import com.patrick.fintech.loan_backend.service.FlutterwaveService;
import com.patrick.fintech.loan_backend.service.IdempotencyService;
import com.patrick.fintech.loan_backend.service.MtnMobileMoneyService;
import com.patrick.fintech.loan_backend.service.PaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@RequestMapping("/api/public/webhooks")
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookController {

    private final FlutterwaveService flutterwaveService;

    private final MtnMobileMoneyService mtnMobileMoneyService;

    private final AirtelMobileMoneyService airtelMoneyService;

    private final PaymentService paymentService;

    private final LoanRepository loanRepo;

    private final IdempotencyService idempotencyService;


    /**
     * ============================================================
     * FLUTTERWAVE WEBHOOK SECRET
     * ============================================================
     */
    @Value("${flutterwave.webhook-secret:}")
    private String flutterwaveWebhookSecret;


    /**
     * ============================================================
     * MTN WEBHOOK SECRET
     * ============================================================
     *
     * Optional.
     *
     * If your MTN integration does not use a webhook secret,
     * leave this empty.
     */
    @Value("${mtn.mobile-money.webhook-secret:}")
    private String mtnWebhookSecret;


    /**
     * ============================================================
     * AIRTEL WEBHOOK SECRET
     * ============================================================
     *
     * Optional.
     */
    @Value("${airtel.money.webhook-secret:}")
    private String airtelWebhookSecret;


    // ============================================================
    // FLUTTERWAVE
    // ============================================================

    /**
     * Flutterwave webhook.
     *
     * Example:
     *
     * POST
     * /api/public/webhooks/flutterwave
     *
     * Flutterwave should call this endpoint after a transaction
     * changes state.
     */
    @PostMapping("/flutterwave")
    public ResponseEntity<String> handleFlutterwaveWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(
                    value = "verif-hash",
                    required = false
            ) String signature) {

        try {

            // ----------------------------------------------------
            // Verify webhook signature
            // ----------------------------------------------------

            if (
                    flutterwaveWebhookSecret != null
                    && !flutterwaveWebhookSecret.isBlank()
            ) {

                if (
                        signature == null
                        || !signature.equals(flutterwaveWebhookSecret)
                ) {

                    log.warn(
                            "[FLW WEBHOOK] Invalid webhook signature"
                    );

                    return ResponseEntity
                            .status(401)
                            .body("Invalid signature");
                }
            }


            // ----------------------------------------------------
            // Extract data
            // ----------------------------------------------------

            Object dataObject = payload.get("data");

            if (!(dataObject instanceof Map<?, ?>)) {

                return ResponseEntity
                        .ok("ignored: no data");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data =
                    (Map<String, Object>) dataObject;


            String transactionId =
                    String.valueOf(data.get("id"));

            String transactionReference =
                    String.valueOf(data.get("tx_ref"));


            // ----------------------------------------------------
            // Extract loan ID
            // ----------------------------------------------------

            Long loanId =
                    FlutterwaveService.loanIdFromTxRef(
                            transactionReference
                    );

            if (loanId == null) {

                log.warn(
                        "[FLW WEBHOOK] Cannot determine loan from tx_ref={}",
                        transactionReference
                );

                return ResponseEntity
                        .ok("ignored: unrecognized transaction");
            }


            // ----------------------------------------------------
            // Verify transaction directly with Flutterwave
            // ----------------------------------------------------

            boolean verified =
                    flutterwaveService.verifyTransaction(
                            transactionId
                    );

            if (!verified) {

                log.warn(
                        "[FLW WEBHOOK] Transaction {} failed verification",
                        transactionId
                );

                return ResponseEntity
                        .ok("not verified");
            }


            // ----------------------------------------------------
            // Find loan
            // ----------------------------------------------------

            Loan loan =
                    loanRepo.findById(loanId)
                            .orElse(null);

            if (loan == null) {

                log.warn(
                        "[FLW WEBHOOK] Loan {} not found",
                        loanId
                );

                return ResponseEntity
                        .ok("ignored: unknown loan");
            }


            // ----------------------------------------------------
            // Idempotency
            // ----------------------------------------------------

            var idempotency =
                    idempotencyService.checkOrReserve(
                            "flw-webhook-" + transactionId,
                            loan.getOrganization(),
                            "POST /webhooks/flutterwave",
                            payload.toString()
                    );

            if (idempotency.isReplay()) {

                return ResponseEntity
                        .ok("already processed");
            }


            // ----------------------------------------------------
            // Amount
            // ----------------------------------------------------

            double amount =
                    extractAmount(data.get("amount"));


            if (amount <= 0) {

                log.warn(
                        "[FLW WEBHOOK] Invalid amount for transaction {}",
                        transactionId
                );

                return ResponseEntity
                        .ok("invalid amount");
            }


            // ----------------------------------------------------
            // Payment method
            // ----------------------------------------------------

            String method =
                    String.valueOf(
                            data.getOrDefault(
                                    "payment_type",
                                    "MOBILE_MONEY"
                            )
                    ).toUpperCase();


            // ----------------------------------------------------
            // Record payment
            // ----------------------------------------------------

            paymentService.recordPayment(
                    loanId,
                    amount,
                    method,
                    transactionId,
                    "FLUTTERWAVE_WEBHOOK",
                    "Confirmed via Flutterwave webhook",
                    null
            );


            log.info(
                    "[FLW WEBHOOK] Payment recorded. loan={}, amount={}, transaction={}",
                    loanId,
                    amount,
                    transactionId
            );


            return ResponseEntity
                    .ok("processed");

        } catch (Exception e) {

            log.error(
                    "[FLW WEBHOOK] Processing error",
                    e
            );

            /*
             * Return 200 so the gateway does not continuously retry
             * a malformed/internal request forever.
             *
             * The payment is NOT marked successful if recording failed.
             */
            return ResponseEntity
                    .ok("error processing webhook");
        }
    }


    // ============================================================
    // DIRECT MTN MOBILE MONEY
    // ============================================================

    /**
     * ============================================================
     * MTN MOBILE MONEY WEBHOOK
     * ============================================================
     *
     * Example:
     *
     * POST
     * /api/public/webhooks/mtn
     *
     * The exact payload sent by MTN depends on the MTN API product
     * and integration environment.
     *
     * Therefore this endpoint intentionally accepts a generic
     * JSON payload and extracts common transaction fields.
     */
    @PostMapping("/mtn")
    public ResponseEntity<String> handleMtnWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(
                    value = "X-Webhook-Secret",
                    required = false
            ) String signature) {

        try {

            // ----------------------------------------------------
            // Optional signature validation
            // ----------------------------------------------------

            if (
                    mtnWebhookSecret != null
                    && !mtnWebhookSecret.isBlank()
            ) {

                if (
                        signature == null
                        || !signature.equals(mtnWebhookSecret)
                ) {

                    log.warn(
                            "[MTN WEBHOOK] Invalid webhook signature"
                    );

                    return ResponseEntity
                            .status(401)
                            .body("Invalid signature");
                }
            }


            // ----------------------------------------------------
            // Extract transaction reference
            // ----------------------------------------------------

            String transactionId =
                    firstNonBlank(
                            payload,
                            "transactionId",
                            "transaction_id",
                            "externalId",
                            "external_id",
                            "reference",
                            "financialTransactionId"
                    );


            if (transactionId == null) {

                log.warn(
                        "[MTN WEBHOOK] Missing transaction ID: {}",
                        payload
                );

                return ResponseEntity
                        .ok("ignored: missing transaction id");
            }


            // ----------------------------------------------------
            // Extract loan ID
            // ----------------------------------------------------

            Long loanId =
                    extractLoanId(
                            payload,
                            transactionId
                    );


            if (loanId == null) {

                log.warn(
                        "[MTN WEBHOOK] Cannot determine loan. transaction={}",
                        transactionId
                );

                return ResponseEntity
                        .ok("ignored: unknown loan");
            }


            // ----------------------------------------------------
            // Find loan
            // ----------------------------------------------------

            Loan loan =
                    loanRepo.findById(loanId)
                            .orElse(null);

            if (loan == null) {

                return ResponseEntity
                        .ok("ignored: unknown loan");
            }


            // ----------------------------------------------------
            // Check payment status
            // ----------------------------------------------------

            String status =
                    firstNonBlank(
                            payload,
                            "status",
                            "transactionStatus",
                            "transaction_status"
                    );


            if (
                    status != null
                    && !isSuccessfulStatus(status)
            ) {

                log.info(
                        "[MTN WEBHOOK] Transaction {} status={}",
                        transactionId,
                        status
                );

                return ResponseEntity
                        .ok("payment not successful");
            }


            // ----------------------------------------------------
            // Idempotency
            // ----------------------------------------------------

            var idempotency =
                    idempotencyService.checkOrReserve(
                            "mtn-webhook-" + transactionId,
                            loan.getOrganization(),
                            "POST /webhooks/mtn",
                            payload.toString()
                    );

            if (idempotency.isReplay()) {

                return ResponseEntity
                        .ok("already processed");
            }


            // ----------------------------------------------------
            // Amount
            // ----------------------------------------------------

            double amount =
                    extractAmount(
                            payload.get("amount")
                    );


            if (amount <= 0) {

                log.warn(
                        "[MTN WEBHOOK] Invalid amount. transaction={}",
                        transactionId
                );

                return ResponseEntity
                        .ok("invalid amount");
            }


            // ----------------------------------------------------
            // Record payment
            // ----------------------------------------------------

            paymentService.recordPayment(
                    loanId,
                    amount,
                    "MOBILE_MONEY",
                    transactionId,
                    "MTN_MOBILE_MONEY",
                    "Confirmed via direct MTN Mobile Money",
                    null
            );


            log.info(
                    "[MTN WEBHOOK] Payment recorded. loan={}, amount={}, transaction={}",
                    loanId,
                    amount,
                    transactionId
            );


            return ResponseEntity
                    .ok("processed");

        } catch (Exception e) {

            log.error(
                    "[MTN WEBHOOK] Processing error",
                    e
            );

            return ResponseEntity
                    .ok("error processing webhook");
        }
    }


    // ============================================================
    // DIRECT AIRTEL MONEY
    // ============================================================

    /**
     * ============================================================
     * AIRTEL MONEY WEBHOOK
     * ============================================================
     *
     * Example:
     *
     * POST
     * /api/public/webhooks/airtel
     */
    @PostMapping("/airtel")
    public ResponseEntity<String> handleAirtelWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(
                    value = "X-Webhook-Secret",
                    required = false
            ) String signature) {

        try {

            // ----------------------------------------------------
            // Optional signature validation
            // ----------------------------------------------------

            if (
                    airtelWebhookSecret != null
                    && !airtelWebhookSecret.isBlank()
            ) {

                if (
                        signature == null
                        || !signature.equals(airtelWebhookSecret)
                ) {

                    log.warn(
                            "[AIRTEL WEBHOOK] Invalid webhook signature"
                    );

                    return ResponseEntity
                            .status(401)
                            .body("Invalid signature");
                }
            }


            // ----------------------------------------------------
            // Extract transaction ID
            // ----------------------------------------------------

            String transactionId =
                    firstNonBlank(
                            payload,
                            "transactionId",
                            "transaction_id",
                            "transaction",
                            "reference",
                            "externalId",
                            "external_id",
                            "id"
                    );


            if (transactionId == null) {

                log.warn(
                        "[AIRTEL WEBHOOK] Missing transaction ID"
                );

                return ResponseEntity
                        .ok("ignored: missing transaction id");
            }


            // ----------------------------------------------------
            // Extract loan ID
            // ----------------------------------------------------

            Long loanId =
                    extractLoanId(
                            payload,
                            transactionId
                    );


            if (loanId == null) {

                log.warn(
                        "[AIRTEL WEBHOOK] Cannot determine loan. transaction={}",
                        transactionId
                );

                return ResponseEntity
                        .ok("ignored: unknown loan");
            }


            // ----------------------------------------------------
            // Find loan
            // ----------------------------------------------------

            Loan loan =
                    loanRepo.findById(loanId)
                            .orElse(null);

            if (loan == null) {

                return ResponseEntity
                        .ok("ignored: unknown loan");
            }


            // ----------------------------------------------------
            // Check transaction status
            // ----------------------------------------------------

            String status =
                    firstNonBlank(
                            payload,
                            "status",
                            "transactionStatus",
                            "transaction_status",
                            "resultCode"
                    );


            if (
                    status != null
                    && !isSuccessfulStatus(status)
            ) {

                log.info(
                        "[AIRTEL WEBHOOK] Transaction {} status={}",
                        transactionId,
                        status
                );

                return ResponseEntity
                        .ok("payment not successful");
            }


            // ----------------------------------------------------
            // Idempotency
            // ----------------------------------------------------

            var idempotency =
                    idempotencyService.checkOrReserve(
                            "airtel-webhook-" + transactionId,
                            loan.getOrganization(),
                            "POST /webhooks/airtel",
                            payload.toString()
                    );

            if (idempotency.isReplay()) {

                return ResponseEntity
                        .ok("already processed");
            }


            // ----------------------------------------------------
            // Amount
            // ----------------------------------------------------

            double amount =
                    extractAmount(
                            payload.get("amount")
                    );


            if (amount <= 0) {

                log.warn(
                        "[AIRTEL WEBHOOK] Invalid amount. transaction={}",
                        transactionId
                );

                return ResponseEntity
                        .ok("invalid amount");
            }


            // ----------------------------------------------------
            // Record payment
            // ----------------------------------------------------

            paymentService.recordPayment(
                    loanId,
                    amount,
                    "MOBILE_MONEY",
                    transactionId,
                    "AIRTEL_MONEY",
                    "Confirmed via direct Airtel Money",
                    null
            );


            log.info(
                    "[AIRTEL WEBHOOK] Payment recorded. loan={}, amount={}, transaction={}",
                    loanId,
                    amount,
                    transactionId
            );


            return ResponseEntity
                    .ok("processed");

        } catch (Exception e) {

            log.error(
                    "[AIRTEL WEBHOOK] Processing error",
                    e
            );

            return ResponseEntity
                    .ok("error processing webhook");
        }
    }


    // ============================================================
    // HELPER METHODS
    // ============================================================

    /**
     * Extract amount safely from webhook payload.
     */
    private double extractAmount(Object value) {

        if (value == null) {
            return 0;
        }

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        try {

            return Double.parseDouble(
                    String.valueOf(value)
            );

        } catch (Exception e) {

            return 0;
        }
    }


    /**
     * Extract a string from the first available field.
     */
    private String firstNonBlank(
            Map<String, Object> payload,
            String... fields) {

        for (String field : fields) {

            Object value =
                    payload.get(field);

            if (value == null) {
                continue;
            }

            String text =
                    String.valueOf(value).trim();

            if (!text.isBlank()) {
                return text;
            }
        }

        return null;
    }


    /**
     * Extract loan ID.
     *
     * Preferred:
     *
     *      loanId
     *
     * or:
     *
     *      loan_id
     *
     * Otherwise attempt to recover it from a transaction reference
     * such as:
     *
     *      LOAN-123-ABC456
     */
    private Long extractLoanId(
            Map<String, Object> payload,
            String transactionReference) {

        Object value =
                payload.get("loanId");

        if (value == null) {
            value = payload.get("loan_id");
        }

        if (value != null) {

            try {

                return Long.valueOf(
                        String.valueOf(value)
                );

            } catch (Exception ignored) {
            }
        }


        // Try reference format:
        // LOAN-123-XXXX

        if (transactionReference != null) {

            try {

                String[] parts =
                        transactionReference.split("-");

                if (
                        parts.length >= 2
                        && "LOAN".equalsIgnoreCase(parts[0])
                ) {

                    return Long.valueOf(parts[1]);
                }

            } catch (Exception ignored) {
            }
        }

        return null;
    }


    /**
     * Determine whether a provider status represents a successful
     * transaction.
     */
    private boolean isSuccessfulStatus(
            String status) {

        if (status == null) {
            return true;
        }

        String normalized =
                status.trim()
                        .toLowerCase();


        return normalized.equals("success")
                || normalized.equals("successful")
                || normalized.equals("completed")
                || normalized.equals("complete")
                || normalized.equals("paid")
                || normalized.equals("successful_payment")
                || normalized.equals("200")
                || normalized.equals("0");
    }
}
