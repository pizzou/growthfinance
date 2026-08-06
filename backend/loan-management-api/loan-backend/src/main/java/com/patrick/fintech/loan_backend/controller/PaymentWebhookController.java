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
    // FLUTTERWAVE
    // ============================================================

    /**
     * Flutterwave webhook endpoint.
     *
     * POST
     *
     * /api/public/webhooks/flutterwave
     *
     * Flutterwave sends the webhook after the transaction changes
     * state.
     */
    @PostMapping("/flutterwave")
    public ResponseEntity<String> handleFlutterwaveWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(
                    value = "verif-hash",
                    required = false
            ) String signature
    ) {

        try {

            // ====================================================
            // VERIFY WEBHOOK SIGNATURE
            // ====================================================

            if (
                    flutterwaveWebhookSecret != null
                            && !flutterwaveWebhookSecret.isBlank()
            ) {

                if (
                        signature == null
                                || !signature.equals(
                                flutterwaveWebhookSecret
                        )
                ) {

                    log.warn(
                            "[FLW WEBHOOK] Invalid webhook signature"
                    );

                    return ResponseEntity
                            .status(401)
                            .body("Invalid signature");
                }
            }


            // ====================================================
            // EXTRACT DATA
            // ====================================================

            Object dataObject =
                    payload.get("data");


            if (!(dataObject instanceof Map<?, ?>)) {

                log.warn(
                        "[FLW WEBHOOK] Missing data object"
                );

                return ResponseEntity
                        .ok(
                                "ignored: no data"
                        );
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
                        "[FLW WEBHOOK] Missing transaction ID"
                );

                return ResponseEntity
                        .ok(
                                "ignored: missing transaction id"
                        );
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
                        "[FLW WEBHOOK] Missing transaction reference. transaction={}",
                        transactionId
                );

                return ResponseEntity
                        .ok(
                                "ignored: missing transaction reference"
                        );
            }


            // ====================================================
            // TRANSACTION STATUS
            // ====================================================

            String status =
                    firstNonBlank(
                            data,
                            "status"
                    );


            if (
                    status != null
                            && !isSuccessfulStatus(
                            status
                    )
            ) {

                log.info(
                        "[FLW WEBHOOK] Transaction {} status={}",
                        transactionId,
                        status
                );

                return ResponseEntity
                        .ok(
                                "payment not successful"
                        );
            }


            // ====================================================
            // EXTRACT LOAN ID
            // ====================================================

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
                        .ok(
                                "ignored: unrecognized transaction"
                        );
            }


            // ====================================================
            // FIND LOAN
            // ====================================================

            Loan loan =
                    loanRepo.findById(
                            loanId
                    )
                            .orElse(null);


            if (loan == null) {

                log.warn(
                        "[FLW WEBHOOK] Loan {} not found",
                        loanId
                );

                return ResponseEntity
                        .ok(
                                "ignored: unknown loan"
                        );
            }


            // ====================================================
            // VERIFY TRANSACTION DIRECTLY WITH FLUTTERWAVE
            // ====================================================

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
                        .ok(
                                "not verified"
                        );
            }


            // ====================================================
            // WEBHOOK IDEMPOTENCY
            // ====================================================

            var idempotency =
                    idempotencyService.checkOrReserve(
                            "flw-webhook-" + transactionId,
                            loan.getOrganization(),
                            "POST /webhooks/flutterwave",
                            payload.toString()
                    );


            if (idempotency.isReplay()) {

                log.info(
                        "[FLW WEBHOOK] Replay detected. transaction={}",
                        transactionId
                );

                return ResponseEntity
                        .ok(
                                "already processed"
                        );
            }


            // ====================================================
            // AMOUNT
            // ====================================================

            double amount =
                    extractAmount(
                            data.get("amount")
                    );


            if (amount <= 0) {

                log.warn(
                        "[FLW WEBHOOK] Invalid amount. transaction={}",
                        transactionId
                );

                return ResponseEntity
                        .ok(
                                "invalid amount"
                        );
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
                    method.toUpperCase();


            // ====================================================
            // RECORD PAYMENT
            // ====================================================

            /*
             * IMPORTANT:
             *
             * transactionId is passed directly to PaymentService.
             *
             * If the immediate payment path already recorded this
             * transaction, PaymentService will find the existing
             * Payment and return it.
             *
             * Therefore the webhook will NOT:
             *
             * - add money again
             * - reduce principal again
             * - charge interest again
             * - send another SMS
             * - send another email
             * - create another accounting transaction
             */
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
                    "[FLW WEBHOOK] Payment processed. " +
                            "loan={}, amount={}, transaction={}",
                    loanId,
                    amount,
                    transactionId
            );


            return ResponseEntity
                    .ok(
                            "processed"
                    );

        } catch (Exception e) {

            log.error(
                    "[FLW WEBHOOK] Processing error",
                    e
            );

            /*
             * Return 500 so the provider can retry.
             *
             * This is safer than returning 200 for an internal
             * processing failure because a 200 tells the provider
             * that the webhook was successfully handled.
             */
            return ResponseEntity
                    .internalServerError()
                    .body(
                            "error processing webhook"
                    );
        }
    }


    // ============================================================
    // MTN MOBILE MONEY
    // ============================================================

    /**
     * MTN Mobile Money webhook.
     *
     * POST
     *
     * /api/public/webhooks/mtn
     */
    @PostMapping("/mtn")
    public ResponseEntity<String> handleMtnWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(
                    value = "X-Webhook-Secret",
                    required = false
            ) String signature
    ) {

        try {

            // ====================================================
            // SIGNATURE
            // ====================================================

            if (
                    mtnWebhookSecret != null
                            && !mtnWebhookSecret.isBlank()
            ) {

                if (
                        signature == null
                                || !signature.equals(
                                mtnWebhookSecret
                        )
                ) {

                    log.warn(
                            "[MTN WEBHOOK] Invalid webhook signature"
                    );

                    return ResponseEntity
                            .status(401)
                            .body(
                                    "Invalid signature"
                            );
                }
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
                            "reference",
                            "financialTransactionId",
                            "financial_transaction_id"
                    );


            if (transactionId == null) {

                log.warn(
                        "[MTN WEBHOOK] Missing transaction ID: {}",
                        payload
                );

                return ResponseEntity
                        .ok(
                                "ignored: missing transaction id"
                        );
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
                        "[MTN WEBHOOK] Cannot determine loan. transaction={}",
                        transactionId
                );

                return ResponseEntity
                        .ok(
                                "ignored: unknown loan"
                        );
            }


            // ====================================================
            // FIND LOAN
            // ====================================================

            Loan loan =
                    loanRepo.findById(
                            loanId
                    )
                            .orElse(null);


            if (loan == null) {

                log.warn(
                        "[MTN WEBHOOK] Loan {} not found",
                        loanId
                );

                return ResponseEntity
                        .ok(
                                "ignored: unknown loan"
                        );
            }


            // ====================================================
            // STATUS
            // ====================================================

            String status =
                    firstNonBlank(
                            payload,
                            "status",
                            "transactionStatus",
                            "transaction_status"
                    );


            if (
                    status != null
                            && !isSuccessfulStatus(
                            status
                    )
            ) {

                log.info(
                        "[MTN WEBHOOK] Transaction {} status={}",
                        transactionId,
                        status
                );

                return ResponseEntity
                        .ok(
                                "payment not successful"
                        );
            }


            // ====================================================
            // IDEMPOTENCY
            // ====================================================

            var idempotency =
                    idempotencyService.checkOrReserve(
                            "mtn-webhook-" + transactionId,
                            loan.getOrganization(),
                            "POST /webhooks/mtn",
                            payload.toString()
                    );


            if (idempotency.isReplay()) {

                log.info(
                        "[MTN WEBHOOK] Replay detected. transaction={}",
                        transactionId
                );

                return ResponseEntity
                        .ok(
                                "already processed"
                        );
            }


            // ====================================================
            // AMOUNT
            // ====================================================

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
                        .ok(
                                "invalid amount"
                        );
            }


            // ====================================================
            // RECORD PAYMENT
            // ====================================================

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
                    "[MTN WEBHOOK] Payment processed. " +
                            "loan={}, amount={}, transaction={}",
                    loanId,
                    amount,
                    transactionId
            );


            return ResponseEntity
                    .ok(
                            "processed"
                    );

        } catch (Exception e) {

            log.error(
                    "[MTN WEBHOOK] Processing error",
                    e
            );

            return ResponseEntity
                    .internalServerError()
                    .body(
                            "error processing webhook"
                    );
        }
    }


    // ============================================================
    // AIRTEL MONEY
    // ============================================================

    /**
     * Airtel Money webhook.
     *
     * POST
     *
     * /api/public/webhooks/airtel
     */
    @PostMapping("/airtel")
    public ResponseEntity<String> handleAirtelWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(
                    value = "X-Webhook-Secret",
                    required = false
            ) String signature
    ) {

        try {

            // ====================================================
            // SIGNATURE
            // ====================================================

            if (
                    airtelWebhookSecret != null
                            && !airtelWebhookSecret.isBlank()
            ) {

                if (
                        signature == null
                                || !signature.equals(
                                airtelWebhookSecret
                        )
                ) {

                    log.warn(
                            "[AIRTEL WEBHOOK] Invalid webhook signature"
                    );

                    return ResponseEntity
                            .status(401)
                            .body(
                                    "Invalid signature"
                            );
                }
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
                        "[AIRTEL WEBHOOK] Missing transaction ID"
                );

                return ResponseEntity
                        .ok(
                                "ignored: missing transaction id"
                        );
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
                        "[AIRTEL WEBHOOK] Cannot determine loan. transaction={}",
                        transactionId
                );

                return ResponseEntity
                        .ok(
                                "ignored: unknown loan"
                        );
            }


            // ====================================================
            // FIND LOAN
            // ====================================================

            Loan loan =
                    loanRepo.findById(
                            loanId
                    )
                            .orElse(null);


            if (loan == null) {

                log.warn(
                        "[AIRTEL WEBHOOK] Loan {} not found",
                        loanId
                );

                return ResponseEntity
                        .ok(
                                "ignored: unknown loan"
                        );
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


            if (
                    status != null
                            && !isSuccessfulStatus(
                            status
                    )
            ) {

                log.info(
                        "[AIRTEL WEBHOOK] Transaction {} status={}",
                        transactionId,
                        status
                );

                return ResponseEntity
                        .ok(
                                "payment not successful"
                        );
            }


            // ====================================================
            // IDEMPOTENCY
            // ====================================================

            var idempotency =
                    idempotencyService.checkOrReserve(
                            "airtel-webhook-" + transactionId,
                            loan.getOrganization(),
                            "POST /webhooks/airtel",
                            payload.toString()
                    );


            if (idempotency.isReplay()) {

                log.info(
                        "[AIRTEL WEBHOOK] Replay detected. transaction={}",
                        transactionId
                );

                return ResponseEntity
                        .ok(
                                "already processed"
                        );
            }


            // ====================================================
            // AMOUNT
            // ====================================================

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
                        .ok(
                                "invalid amount"
                        );
            }


            // ====================================================
            // RECORD PAYMENT
            // ====================================================

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
                    "[AIRTEL WEBHOOK] Payment processed. " +
                            "loan={}, amount={}, transaction={}",
                    loanId,
                    amount,
                    transactionId
            );


            return ResponseEntity
                    .ok(
                            "processed"
                    );

        } catch (Exception e) {

            log.error(
                    "[AIRTEL WEBHOOK] Processing error",
                    e
            );

            return ResponseEntity
                    .internalServerError()
                    .body(
                            "error processing webhook"
                    );
        }
    }


    // ============================================================
    // HELPER METHODS
    // ============================================================

    /**
     * Extract amount safely from a webhook value.
     */
    private double extractAmount(
            Object value
    ) {

        if (value == null) {
            return 0.0;
        }


        if (value instanceof Number number) {

            return number.doubleValue();
        }


        try {

            return Double.parseDouble(
                    String.valueOf(
                            value
                    ).trim()
            );

        } catch (Exception e) {

            return 0.0;
        }
    }


    /**
     * Extract a string from the first available field.
     */
    private String firstNonBlank(
            Map<String, Object> payload,
            String... fields
    ) {

        if (payload == null) {
            return null;
        }


        for (String field :
                fields) {

            Object value =
                    payload.get(
                            field
                    );


            if (value == null) {
                continue;
            }


            String text =
                    String.valueOf(
                            value
                    ).trim();


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
     *     loanId
     *
     * or:
     *
     *     loan_id
     *
     * Otherwise attempt:
     *
     *     LOAN-123-ABC456
     */
    private Long extractLoanId(
            Map<String, Object> payload,
            String transactionReference
    ) {

        Object value =
                payload.get(
                        "loanId"
                );


        if (value == null) {

            value =
                    payload.get(
                            "loan_id"
                    );
        }


        if (value != null) {

            try {

                return Long.valueOf(
                        String.valueOf(
                                value
                        ).trim()
                );

            } catch (Exception ignored) {
                // Continue to reference parsing.
            }
        }


        // ========================================================
        // TRY TRANSACTION REFERENCE
        // ========================================================

        if (
                transactionReference != null
                        && !transactionReference.isBlank()
        ) {

            try {

                String[] parts =
                        transactionReference.split(
                                "-"
                        );


                if (
                        parts.length >= 2
                                && "LOAN".equalsIgnoreCase(
                                parts[0]
                        )
                ) {

                    return Long.valueOf(
                            parts[1]
                    );
                }

            } catch (Exception ignored) {
                // Unable to extract loan ID.
            }
        }


        return null;
    }


    /**
     * Determine whether a provider status represents a successful
     * transaction.
     */
    private boolean isSuccessfulStatus(
            String status
    ) {

        if (status == null) {
            return true;
        }


        String normalized =
                status.trim()
                        .toLowerCase();


        return normalized.equals(
                "success"
        )
                || normalized.equals(
                "successful"
        )
                || normalized.equals(
                "completed"
        )
                || normalized.equals(
                "complete"
        )
                || normalized.equals(
                "paid"
        )
                || normalized.equals(
                "successful_payment"
        )
                || normalized.equals(
                "successful payment"
        )
                || normalized.equals(
                "200"
        )
                || normalized.equals(
                "0"
        );
    }
}