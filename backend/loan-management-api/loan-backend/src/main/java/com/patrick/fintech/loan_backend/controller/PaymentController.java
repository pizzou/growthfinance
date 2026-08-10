package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayResponse;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.service.IdempotencyService;
import com.patrick.fintech.loan_backend.service.MtnMobileMoneyService;
import com.patrick.fintech.loan_backend.service.PaymentService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/loans/{loanId}/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final CurrentUserUtil currentUserUtil;
    private final IdempotencyService idempotencyService;
    private final MtnMobileMoneyService mtnMobileMoneyService;

    // ============================================================
    // RECORD MANUAL PAYMENT
    // ============================================================

    /**
     * Records a normal/manual payment.
     *
     * Supports Idempotency-Key for retry protection.
     *
     * Financial allocation is performed by PaymentService:
     *
     * 1. Penalty
     * 2. Interest
     * 3. Principal
     * 4. Overpayment
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Payment>> recordPayment(
            @PathVariable Long loanId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            ) String idempotencyKey
    ) {

        var currentUser =
                currentUserUtil.getCurrentUser();

        if (currentUser == null) {
            throw new RuntimeException(
                    "Authenticated user could not be determined."
            );
        }

        var org =
                currentUser.getOrganization();

        if (org == null) {
            throw new RuntimeException(
                    "User is not associated with an organization."
            );
        }

        // ========================================================
        // IDEMPOTENCY
        // ========================================================

        var outcome =
                idempotencyService.checkOrReserve(
                        idempotencyKey,
                        org,
                        "POST /loans/" + loanId + "/payments",
                        body.toString()
                );

        /*
         * Retry of an already completed request.
         *
         * We do not call PaymentService again.
         */
        if (outcome.isReplay()) {

            return ResponseEntity.ok(
                    ApiResponse.ok(
                            "Payment already recorded",
                            null
                    )
            );
        }

        try {

            // ====================================================
            // AMOUNT
            // ====================================================

            Object amountValue =
                    body.get("amount");

            if (amountValue == null) {

                throw new RuntimeException(
                        "Payment amount is required."
                );
            }

            BigDecimal amount;

            try {

                amount =
                        new BigDecimal(
                                amountValue
                                        .toString()
                                        .trim()
                        );

            } catch (NumberFormatException e) {

                throw new RuntimeException(
                        "Payment amount must be a valid number."
                );
            }

            if (amount.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new RuntimeException(
                        "Payment amount must be greater than zero."
                );
            }

            // ====================================================
            // PAYMENT DETAILS
            // ====================================================

            String method =
                    body.getOrDefault(
                            "paymentMethod",
                            "BANK_TRANSFER"
                    )
                    .toString()
                    .trim();

            if (method.isBlank()) {
                method = "BANK_TRANSFER";
            }

            String txnId =
                    body.getOrDefault(
                            "transactionId",
                            ""
                    )
                    .toString()
                    .trim();

            if (txnId.isBlank()) {
                txnId = null;
            }

            String channel =
                    body.getOrDefault(
                            "channel",
                            ""
                    )
                    .toString()
                    .trim();

            if (channel.isBlank()) {
                channel = null;
            }

            String notes =
                    body.getOrDefault(
                            "notes",
                            ""
                    )
                    .toString()
                    .trim();

            if (notes.isBlank()) {
                notes = null;
            }

            // ====================================================
            // RECORD PAYMENT
            // ====================================================

            Payment payment =
                    paymentService.recordPayment(
                            loanId,
                            amount,
                            method,
                            txnId,
                            channel,
                            notes,
                            currentUser
                    );

            // ====================================================
            // RECORD IDEMPOTENCY SUCCESS
            // ====================================================

            idempotencyService.recordSuccess(
                    idempotencyKey,
                    org,
                    payment,
                    200
            );

            return ResponseEntity.ok(
                    ApiResponse.ok(
                            "Payment recorded",
                            payment
                    )
            );

        } catch (Exception e) {

            idempotencyService.recordFailure(
                    idempotencyKey,
                    org
            );

            throw e;
        }
    }

    // ============================================================
    // MTN MOBILE MONEY - SANDBOX CONFIRMATION
    // ============================================================

    /**
     * Confirms a simulated MTN Mobile Money sandbox payment.
     *
     * IMPORTANT:
     *
     * This endpoint does NOT stop after returning a successful
     * MTN response.
     *
     * It performs the complete financial flow:
     *
     *     sandbox confirmation
     *             ↓
     *     PaymentService.recordPayment()
     *             ↓
     *     interest allocation
     *             ↓
     *     principal allocation
     *             ↓
     *     loan outstanding balance updated
     *             ↓
     *     PAYMENT_MADE webhook dispatched
     *
     * The MTN transaction ID is used as the payment transaction ID.
     *
     * Therefore:
     *
     *     same MTN transaction ID
     *             +
     *     repeated webhook
     *             =
     *     existing payment returned
     *
     * and the loan is NOT reduced twice.
     *
     * This endpoint is intended for sandbox/local testing only.
     */
    @PostMapping("/mtn/sandbox/confirm")
    public ResponseEntity<ApiResponse<Payment>> confirmMtnSandboxPayment(
            @PathVariable Long loanId,
            @RequestBody Map<String, Object> body
    ) {

        // ========================================================
        // VALIDATE LOAN ID
        // ========================================================

        if (loanId == null) {

            throw new IllegalArgumentException(
                    "Loan ID is required."
            );
        }

        // ========================================================
        // TRANSACTION ID
        // ========================================================

        Object transactionValue =
                body != null
                        ? body.get("transactionId")
                        : null;

        if (transactionValue == null) {

            throw new IllegalArgumentException(
                    "MTN transactionId is required."
            );
        }

        String transactionId =
                transactionValue
                        .toString()
                        .trim();

        if (transactionId.isBlank()) {

            throw new IllegalArgumentException(
                    "MTN transactionId is required."
            );
        }

        // ========================================================
        // AMOUNT
        // ========================================================

        Object amountValue =
                body.get("amount");

        if (amountValue == null) {

            throw new IllegalArgumentException(
                    "MTN payment amount is required."
            );
        }

        BigDecimal amount;

        try {

            amount =
                    new BigDecimal(
                            amountValue
                                    .toString()
                                    .trim()
                    );

        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                    "MTN payment amount must be a valid number."
            );
        }

        if (amount.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            throw new IllegalArgumentException(
                    "MTN payment amount must be greater than zero."
            );
        }

        // ========================================================
        // CURRENCY
        // ========================================================

        String currency =
                body.getOrDefault(
                        "currency",
                        "RWF"
                )
                .toString()
                .trim();

        if (currency.isBlank()) {
            currency = "RWF";
        }

        // ========================================================
        // LOG
        // ========================================================

        org.slf4j.LoggerFactory
                .getLogger(PaymentController.class)
                .info(
                        "[MTN SANDBOX CONFIRM] Starting confirmation. " +
                                "loanId={}, transactionId={}, amount={}, currency={}",
                        loanId,
                        transactionId,
                        amount,
                        currency
                );

        // ========================================================
        // STEP 1
        // SIMULATE MTN SUCCESS
        // ========================================================

        PaymentGatewayResponse confirmation =
                mtnMobileMoneyService.simulateConfirmation(
                        loanId,
                        transactionId,
                        amount.doubleValue(),
                        currency
                );

        if (confirmation == null) {

            throw new IllegalStateException(
                    "MTN sandbox confirmation returned no response."
            );
        }

        // ========================================================
        // VERIFY SUCCESS
        // ========================================================

        /*
         * Your PaymentGatewayResponse.success(...) should produce
         * a successful response.
         *
         * To avoid depending on a specific getter name from the
         * DTO, we use the transaction verification as an additional
         * sandbox confirmation check.
         */
        boolean verified =
                mtnMobileMoneyService.verify(
                        transactionId
                );

        if (!verified) {

            throw new IllegalStateException(
                    "MTN sandbox transaction could not be verified: "
                            + transactionId
            );
        }

        // ========================================================
        // STEP 2
        // RECORD FINANCIAL PAYMENT
        // ========================================================

        /*
         * CRITICAL:
         *
         * recordedBy = null
         *
         * because this is an automatic MTN confirmation and not a
         * manually recorded staff payment.
         *
         * PaymentService already permits recordedBy == null.
         *
         * It will:
         *
         * - calculate interest
         * - charge minimum one day interest on first payment
         * - allocate payment to interest first
         * - allocate remaining amount to principal
         * - update loan.outstandingBalance
         * - update totalPaid
         * - update payment status
         * - send email/SMS
         * - notify loan officer
         * - dispatch PAYMENT_MADE webhook
         */
        Payment payment =
                paymentService.recordPayment(
                        loanId,
                        amount,
                        "MOBILE_MONEY",
                        transactionId,
                        "MTN_MOMO",
                        "MTN Mobile Money sandbox payment confirmed automatically.",
                        null
                );

        // ========================================================
        // STEP 3
        // FINAL LOG
        // ========================================================

        org.slf4j.LoggerFactory
                .getLogger(PaymentController.class)
                .info(
                        "[MTN SANDBOX CONFIRM] Payment recorded successfully. " +
                                "loanId={}, transactionId={}, paymentId={}, amount={}",
                        loanId,
                        transactionId,
                        payment != null
                                ? payment.getId()
                                : null,
                        amount
                );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "MTN Mobile Money sandbox payment confirmed and recorded successfully.",
                        payment
                )
        );
    }

    // ============================================================
    // MTN MOBILE MONEY - SANDBOX WEBHOOK ALIAS
    // ============================================================

    /**
     * Sandbox webhook-style endpoint.
     *
     * This endpoint exists so the frontend/test simulator can use
     * a webhook-like URL.
     *
     * It intentionally delegates to the exact same confirmation
     * method so there is only ONE financial recording path.
     *
     * DO NOT call PaymentService.recordPayment() separately here.
     */
    @PostMapping("/mtn/webhook")
    public ResponseEntity<ApiResponse<Payment>> mtnWebhook(
            @PathVariable Long loanId,
            @RequestBody Map<String, Object> body
    ) {

        return confirmMtnSandboxPayment(
                loanId,
                body
        );
    }

    // ============================================================
    // GET LOAN SCHEDULE
    // ============================================================

    /**
     * Gets the complete repayment schedule for a loan.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Payment>>>
    getSchedule(
            @PathVariable Long loanId
    ) {

        Long organizationId =
                currentUserUtil
                        .getCurrentOrganizationId();

        return ResponseEntity.ok(
                ApiResponse.ok(
                        paymentService.getLoanSchedule(
                                loanId,
                                organizationId
                        )
                )
        );
    }
}
