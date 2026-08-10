package com.patrick.fintech.loan_backend.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
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

    private final ObjectMapper objectMapper;

    // ============================================================
    // WEBHOOK SECRETS
    // ============================================================

    @Value("${flutterwave.webhook-secret:}")
    private String flutterwaveWebhookSecret;

    @Value("${mtn.mobile-money.webhook-secret:}")
    private String mtnWebhookSecret;

    @Value("${airtel.money.webhook-secret:}")
    private String airtelWebhookSecret;

    // ============================================================
    // FLUTTERWAVE WEBHOOK
    // ============================================================

    @PostMapping("/flutterwave")
    public ResponseEntity<String> handleFlutterwaveWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(
                    value = "verif-hash",
                    required = false
            ) String signature
    ) {

        final String provider = "FLUTTERWAVE";

        try {

            if (payload == null || payload.isEmpty()) {

                log.warn(
                        "[PAYMENT WEBHOOK] Empty Flutterwave payload"
                );

                return ResponseEntity
                        .badRequest()
                        .body("Invalid payload");
            }

            // ====================================================
            // VERIFY WEBHOOK AUTHENTICITY
            // ====================================================

            if (!isWebhookSecretValid(
                    flutterwaveWebhookSecret,
                    signature
            )) {

                log.warn(
                        "[PAYMENT WEBHOOK] Invalid Flutterwave webhook signature"
                );

                return ResponseEntity
                        .status(401)
                        .body("Invalid signature");
            }

            // ====================================================
            // EXTRACT DATA
            // ====================================================

            Object dataObject =
                    payload.get("data");

            if (!(dataObject instanceof Map<?, ?>)) {

                log.warn(
                        "[PAYMENT WEBHOOK] Flutterwave webhook has no data object"
                );

                return ResponseEntity
                        .ok("ignored: no data");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data =
                    (Map<String, Object>) dataObject;

            // ====================================================
            // TRANSACTION ID
            // ====================================================

            String transactionId =
                    firstNonBlank(
                            data,
                            "id",
                            "transaction_id",
                            "transactionId"
                    );

            if (transactionId == null) {

                log.warn(
                        "[PAYMENT WEBHOOK] Flutterwave transaction ID missing"
                );

                return ResponseEntity
                        .ok("ignored: missing transaction id");
            }

            // ====================================================
            // STATUS
            // ====================================================

            String status =
                    firstNonBlank(
                            data,
                            "status"
                    );

            if (!isSuccessfulStatus(status)) {

                log.info(
                        "[PAYMENT WEBHOOK] Flutterwave transaction not successful. " +
                                "transaction={}, status={}",
                        transactionId,
                        status
                );

                return ResponseEntity
                        .ok("payment not successful");
            }

            // ====================================================
            // TRANSACTION REFERENCE
            // ====================================================

            String transactionReference =
                    firstNonBlank(
                            data,
                            "tx_ref",
                            "txRef",
                            "reference"
                    );

            if (transactionReference == null) {

                log.warn(
                        "[PAYMENT WEBHOOK] Flutterwave transaction reference missing. " +
                                "transaction={}",
                        transactionId
                );

                return ResponseEntity
                        .ok("ignored: missing transaction reference");
            }

            // ====================================================
            // LOAN ID
            // ====================================================

            Long loanId =
                    FlutterwaveService.loanIdFromTxRef(
                            transactionReference
                    );

            if (loanId == null) {

                log.warn(
                        "[PAYMENT WEBHOOK] Unrecognized Flutterwave transaction. " +
                                "transaction={}",
                        transactionId
                );

                return ResponseEntity
                        .ok("ignored: unrecognized transaction");
            }

            // ====================================================
            // FIND LOAN
            // ====================================================

            Loan loan =
                    loanRepo.findById(loanId)
                            .orElse(null);

            if (loan == null) {

                log.warn(
                        "[PAYMENT WEBHOOK] Loan not found. loan={}",
                        loanId
                );

                /*
                 * Do not return 500 here.
                 *
                 * This is not an application-processing failure.
                 * The transaction does not belong to a known loan.
                 */
                return ResponseEntity
                        .ok("ignored: unknown loan");
            }

            // ====================================================
            // VERIFY TRANSACTION WITH FLUTTERWAVE
            // ====================================================

            boolean verified =
                    flutterwaveService.verifyTransaction(
                            transactionId
                    );

            if (!verified) {

                log.warn(
                        "[PAYMENT WEBHOOK] Flutterwave transaction verification failed. " +
                                "transaction={}, loan={}",
                        transactionId,
                        loanId
                );

                /*
                 * Do NOT call PaymentService.
                 *
                 * A webhook alone is not sufficient confirmation
                 * when provider-side transaction verification fails.
                 */
                return ResponseEntity
                        .ok("not verified");
            }

            // ====================================================
            // AMOUNT
            // ====================================================

            BigDecimal amount =
                    extractAmount(
                            data.get("amount")
                    );

            if (amount == null || amount.signum() <= 0) {

                log.warn(
                        "[PAYMENT WEBHOOK] Invalid Flutterwave amount. " +
                                "transaction={}, loan={}",
                        transactionId,
                        loanId
                );

                return ResponseEntity
                        .ok("invalid amount");
            }

            // ====================================================
            // IDEMPOTENCY
            // ====================================================

            var idempotency =
                    idempotencyService.checkOrReserve(
                            "flw-webhook-" + transactionId,
                            loan.getOrganization(),
                            "POST /api/public/webhooks/flutterwave",
                            toJson(payload)
                    );

            if (idempotency.isReplay()) {

                log.info(
                        "[PAYMENT WEBHOOK] Flutterwave replay ignored. " +
                                "transaction={}, loan={}",
                        transactionId,
                        loanId
                );

                return ResponseEntity
                        .ok("already processed");
            }

            // ====================================================
            // PAYMENT METHOD
            // ====================================================

            String method =
                    firstNonBlank(
                            data,
                            "payment_type",
                            "paymentType"
                    );

            if (method == null) {
                method = "MOBILE_MONEY";
            }

            method =
                    normalizePaymentMethod(method);

            // ====================================================
            // RECORD PAYMENT
            // ====================================================

            paymentService.recordPayment(
                    loanId,
                    amount,
                    method,
                    transactionId,
                    provider + "_WEBHOOK",
                    "Confirmed via Flutterwave webhook",
                    null
            );

            log.info(
                    "[PAYMENT WEBHOOK] Flutterwave payment processed. " +
                            "loan={}, amount={}, transaction={}",
                    loanId,
                    amount,
                    transactionId
            );

            return ResponseEntity
                    .ok("processed");

        } catch (Exception e) {

            log.error(
                    "[PAYMENT WEBHOOK] Flutterwave processing failed",
                    e
            );

            /*
             * IMPORTANT:
             *
             * Return 500 so Flutterwave can retry.
             *
             * Do not return 200 when your internal payment
             * processing failed.
             */
            return ResponseEntity
                    .internalServerError()
                    .body("error processing webhook");
        }
    }

    // ============================================================
    // MTN MOBILE MONEY WEBHOOK
    // ============================================================

    @PostMapping("/mtn")
    public ResponseEntity<String> handleMtnWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(
                    value = "X-Webhook-Secret",
                    required = false
            ) String signature
    ) {

        final String provider = "MTN_MOBILE_MONEY";

        try {

            if (payload == null || payload.isEmpty()) {

                log.warn(
                        "[PAYMENT WEBHOOK] Empty MTN payload"
                );

                return ResponseEntity
                        .badRequest()
                        .body("Invalid payload");
            }

            // ====================================================
            // AUTHENTICATION
            // ====================================================

            if (!isWebhookSecretValid(
                    mtnWebhookSecret,
                    signature
            )) {

                log.warn(
                        "[PAYMENT WEBHOOK] Invalid MTN webhook signature"
                );

                return ResponseEntity
                        .status(401)
                        .body("Invalid signature");
            }

            // ====================================================
            // TRANSACTION ID
            // ====================================================

            String transactionId =
                    firstNonBlank(
                            payload,
                            "transactionId",
                            "transaction_id",
                            "externalId",
                            "external_id",
                            "financialTransactionId",
                            "financial_transaction_id",
                            "reference",
                            "id"
                    );

            if (transactionId == null) {

                log.warn(
                        "[PAYMENT WEBHOOK] MTN transaction ID missing"
                );

                return ResponseEntity
                        .ok("ignored: missing transaction id");
            }

            // ====================================================
            // STATUS
            // ====================================================

            String status =
                    firstNonBlank(
                            payload,
                            "status",
                            "transactionStatus",
                            "transaction_status",
                            "resultCode",
                            "result_code"
                    );

            if (!isSuccessfulStatus(status)) {

                log.info(
                        "[PAYMENT WEBHOOK] MTN payment not successful. " +
                                "transaction={}, status={}",
                        transactionId,
                        status
                );

                return ResponseEntity
                        .ok("payment not successful");
            }

            // ====================================================
            // LOAN ID
            // ====================================================

            Long loanId =
                    extractLoanId(
                            payload,
                            transactionId
                    );

            if (loanId == null) {

                log.warn(
                        "[PAYMENT WEBHOOK] Cannot determine MTN loan. " +
                                "transaction={}",
                        transactionId
                );

                return ResponseEntity
                        .ok("ignored: unknown loan");
            }

            // ====================================================
            // FIND LOAN
            // ====================================================

            Loan loan =
                    loanRepo.findById(loanId)
                            .orElse(null);

            if (loan == null) {

                log.warn(
                        "[PAYMENT WEBHOOK] MTN loan not found. loan={}",
                        loanId
                );

                return ResponseEntity
                        .ok("ignored: unknown loan");
            }

            // ====================================================
            // AMOUNT
            // ====================================================

            BigDecimal amount =
                    extractAmount(
                            payload.get("amount")
                    );

            if (amount == null || amount.signum() <= 0) {

                log.warn(
                        "[PAYMENT WEBHOOK] Invalid MTN amount. " +
                                "transaction={}, loan={}",
                        transactionId,
                        loanId
                );

                return ResponseEntity
                        .ok("invalid amount");
            }

            // ====================================================
            // IDEMPOTENCY
            // ====================================================

            var idempotency =
                    idempotencyService.checkOrReserve(
                            "mtn-webhook-" + transactionId,
                            loan.getOrganization(),
                            "POST /api/public/webhooks/mtn",
                            toJson(payload)
                    );

            if (idempotency.isReplay()) {

                log.info(
                        "[PAYMENT WEBHOOK] MTN replay ignored. " +
                                "transaction={}, loan={}",
                        transactionId,
                        loanId
                );

                return ResponseEntity
                        .ok("already processed");
            }

            // ====================================================
            // RECORD PAYMENT
            // ====================================================

            paymentService.recordPayment(
                    loanId,
                    amount,
                    "MOBILE_MONEY",
                    transactionId,
                    provider,
                    "Confirmed via direct MTN Mobile Money webhook",
                    null
            );

            log.info(
                    "[PAYMENT WEBHOOK] MTN payment processed. " +
                            "loan={}, amount={}, transaction={}",
                    loanId,
                    amount,
                    transactionId
            );

            return ResponseEntity
                    .ok("processed");

        } catch (Exception e) {

            log.error(
                    "[PAYMENT WEBHOOK] MTN processing failed",
                    e
            );

            return ResponseEntity
                    .internalServerError()
                    .body("error processing webhook");
        }
    }

    // ============================================================
    // AIRTEL MONEY WEBHOOK
    // ============================================================

    @PostMapping("/airtel")
    public ResponseEntity<String> handleAirtelWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(
                    value = "X-Webhook-Secret",
                    required = false
            ) String signature
    ) {

        final String provider = "AIRTEL_MONEY";

        try {

            if (payload == null || payload.isEmpty()) {

                log.warn(
                        "[PAYMENT WEBHOOK] Empty Airtel payload"
                );

                return ResponseEntity
                        .badRequest()
                        .body("Invalid payload");
            }

            // ====================================================
            // AUTHENTICATION
            // ====================================================

            if (!isWebhookSecretValid(
                    airtelWebhookSecret,
                    signature
            )) {

                log.warn(
                        "[PAYMENT WEBHOOK] Invalid Airtel webhook signature"
                );

                return ResponseEntity
                        .status(401)
                        .body("Invalid signature");
            }

            // ====================================================
            // TRANSACTION ID
            // ====================================================

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
                        "[PAYMENT WEBHOOK] Airtel transaction ID missing"
                );

                return ResponseEntity
                        .ok("ignored: missing transaction id");
            }

            // ====================================================
            // STATUS
            // ====================================================

            String status =
                    firstNonBlank(
                            payload,
                            "status",
                            "transactionStatus",
                            "transaction_status",
                            "resultCode",
                            "result_code"
                    );

            if (!isSuccessfulStatus(status)) {

                log.info(
                        "[PAYMENT WEBHOOK] Airtel payment not successful. " +
                                "transaction={}, status={}",
                        transactionId,
                        status
                );

                return ResponseEntity
                        .ok("payment not successful");
            }

            // ====================================================
            // LOAN ID
            // ====================================================

            Long loanId =
                    extractLoanId(
                            payload,
                            transactionId
                    );

            if (loanId == null) {

                log.warn(
                        "[PAYMENT WEBHOOK] Cannot determine Airtel loan. " +
                                "transaction={}",
                        transactionId
                );

                return ResponseEntity
                        .ok("ignored: unknown loan");
            }

            // ====================================================
            // FIND LOAN
            // ====================================================

            Loan loan =
                    loanRepo.findById(loanId)
                            .orElse(null);

            if (loan == null) {

                log.warn(
                        "[PAYMENT WEBHOOK] Airtel loan not found. loan={}",
                        loanId
                );

                return ResponseEntity
                        .ok("ignored: unknown loan");
            }

            // ====================================================
            // AMOUNT
            // ====================================================

            BigDecimal amount =
                    extractAmount(
                            payload.get("amount")
                    );

            if (amount == null || amount.signum() <= 0) {

                log.warn(
                        "[PAYMENT WEBHOOK] Invalid Airtel amount. " +
                                "transaction={}, loan={}",
                        transactionId,
                        loanId
                );

                return ResponseEntity
                        .ok("invalid amount");
            }

            // ====================================================
            // IDEMPOTENCY
            // ====================================================

            var idempotency =
                    idempotencyService.checkOrReserve(
                            "airtel-webhook-" + transactionId,
                            loan.getOrganization(),
                            "POST /api/public/webhooks/airtel",
                            toJson(payload)
                    );

            if (idempotency.isReplay()) {

                log.info(
                        "[PAYMENT WEBHOOK] Airtel replay ignored. " +
                                "transaction={}, loan={}",
                        transactionId,
                        loanId
                );

                return ResponseEntity
                        .ok("already processed");
            }

            // ====================================================
            // RECORD PAYMENT
            // ====================================================

            paymentService.recordPayment(
                    loanId,
                    amount,
                    "MOBILE_MONEY",
                    transactionId,
                    provider,
                    "Confirmed via direct Airtel Money webhook",
                    null
            );

            log.info(
                    "[PAYMENT WEBHOOK] Airtel payment processed. " +
                            "loan={}, amount={}, transaction={}",
                    loanId,
                    amount,
                    transactionId
            );

            return ResponseEntity
                    .ok("processed");

        } catch (Exception e) {

            log.error(
                    "[PAYMENT WEBHOOK] Airtel processing failed",
                    e
            );

            return ResponseEntity
                    .internalServerError()
                    .body("error processing webhook");
        }
    }

    // ============================================================
    // WEBHOOK SECRET VALIDATION
    // ============================================================

    private boolean isWebhookSecretValid(
            String configuredSecret,
            String receivedSignature
    ) {

        /*
         * In production a webhook secret MUST be configured.
         *
         * We deliberately fail closed.
         *
         * This prevents an accidentally unconfigured endpoint
         * from accepting arbitrary public POST requests.
         */
        if (
                configuredSecret == null
                        ||
                configuredSecret.isBlank()
        ) {

            log.error(
                    "[PAYMENT WEBHOOK] Webhook secret is not configured"
            );

            return false;
        }

        if (
                receivedSignature == null
                        ||
                receivedSignature.isBlank()
        ) {

            return false;
        }

        return constantTimeEquals(
                configuredSecret,
                receivedSignature
        );
    }

    // ============================================================
    // CONSTANT-TIME SECRET COMPARISON
    // ============================================================

    private boolean constantTimeEquals(
            String expected,
            String actual
    ) {

        byte[] expectedBytes =
                expected.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8
                );

        byte[] actualBytes =
                actual.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8
                );

        return java.security.MessageDigest.isEqual(
                expectedBytes,
                actualBytes
        );
    }

    // ============================================================
    // AMOUNT EXTRACTION
    // ============================================================

    private BigDecimal extractAmount(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        try {

            if (value instanceof BigDecimal decimal) {

                return decimal;
            }

            if (value instanceof Number number) {

                return new BigDecimal(
                        number.toString()
                );
            }

            String text =
                    String.valueOf(value)
                            .trim();

            if (text.isBlank()) {
                return null;
            }

            return new BigDecimal(text);

        } catch (Exception e) {

            return null;
        }
    }

    // ============================================================
    // STRING EXTRACTION
    // ============================================================

    private String firstNonBlank(
            Map<String, Object> payload,
            String... fields
    ) {

        if (payload == null) {
            return null;
        }

        for (String field : fields) {

            if (field == null) {
                continue;
            }

            Object value =
                    payload.get(field);

            if (value == null) {
                continue;
            }

            String text =
                    String.valueOf(value)
                            .trim();

            if (!text.isBlank()) {

                return text;
            }
        }

        return null;
    }

    // ============================================================
    // LOAN ID EXTRACTION
    // ============================================================

    private Long extractLoanId(
            Map<String, Object> payload,
            String transactionReference
    ) {

        if (payload == null) {
            return null;
        }

        // ========================================================
        // DIRECT LOAN ID
        // ========================================================

        Object value =
                payload.get("loanId");

        if (value == null) {

            value =
                    payload.get("loan_id");
        }

        if (value != null) {

            try {

                Long loanId =
                        Long.valueOf(
                                String.valueOf(value)
                                        .trim()
                        );

                if (loanId > 0) {
                    return loanId;
                }

            } catch (Exception ignored) {
                // Continue to reference parsing.
            }
        }

        // ========================================================
        // TRANSACTION REFERENCE
        // ========================================================

        if (
                transactionReference == null
                        ||
                transactionReference.isBlank()
        ) {

            return null;
        }

        /*
         * Expected format:
         *
         * LOAN-123-ABC456
         *
         * This is intentionally strict.
         */
        String normalized =
                transactionReference.trim();

        String[] parts =
                normalized.split("-");

        if (parts.length < 2) {
            return null;
        }

        if (
                !"LOAN".equalsIgnoreCase(
                        parts[0]
                )
        ) {

            return null;
        }

        try {

            Long loanId =
                    Long.valueOf(
                            parts[1]
                    );

            return loanId > 0
                    ? loanId
                    : null;

        } catch (NumberFormatException e) {

            return null;
        }
    }

    // ============================================================
    // PAYMENT STATUS
    // ============================================================

    private boolean isSuccessfulStatus(
            String status
    ) {

        /*
         * A missing status is NOT automatically treated as
         * successful.
         *
         * For production payment processing this is safer.
         */
        if (status == null || status.isBlank()) {

            return false;
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
                || normalized.equals("successful payment")
                || normalized.equals("200")
                || normalized.equals("0");
    }

    // ============================================================
    // PAYMENT METHOD
    // ============================================================

    private String normalizePaymentMethod(
            String method
    ) {

        if (method == null || method.isBlank()) {
            return "MOBILE_MONEY";
        }

        String normalized =
                method.trim()
                        .toUpperCase();

        if (
                normalized.equals("MOBILEMONEY")
                        ||
                normalized.equals("MOBILE-MONEY")
                        ||
                normalized.equals("MOMO")
        ) {

            return "MOBILE_MONEY";
        }

        return normalized;
    }

    // ============================================================
    // JSON SERIALIZATION
    // ============================================================

    private String toJson(
            Object payload
    ) {

        if (payload == null) {
            return "{}";
        }

        try {

            return objectMapper.writeValueAsString(
                    payload
            );

        } catch (JsonProcessingException e) {

            /*
             * Idempotency should still receive a deterministic
             * non-sensitive marker rather than Map.toString().
             */
            log.warn(
                    "[PAYMENT WEBHOOK] Could not serialize payload for idempotency"
            );

            return "{\"serialization\":\"failed\"}";
        }
    }
}