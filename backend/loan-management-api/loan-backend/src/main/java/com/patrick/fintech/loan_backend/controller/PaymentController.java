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

    private final PaymentService paymentService;
    private final CurrentUserUtil currentUserUtil;
    private final IdempotencyService idempotencyService;


    /**
     * Record a manual payment.
     *
     * All monetary values remain Double to match the existing
     * application model and inherited classes.
     *
     * Supports Idempotency-Key for offline synchronization and
     * retry protection.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Payment>> recordPayment(
            @PathVariable Long loanId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            ) String idempotencyKey) {

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


        // ============================================================
        // IDEMPOTENCY
        // ============================================================

        var outcome =
                idempotencyService.checkOrReserve(
                        idempotencyKey,
                        org,
                        "POST /loans/" + loanId + "/payments",
                        body.toString()
                );


        /*
         * If this request is a retry, do not create another payment.
         *
         * We return null here because the current IdempotencyService
         * implementation does not expose the original Payment through
         * the replay result.
         *
         * The important part is that the payment is NOT duplicated.
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

            // ========================================================
            // AMOUNT
            // ========================================================

            Object amountValue =
                    body.get("amount");

            if (amountValue == null) {

                throw new RuntimeException(
                        "Payment amount is required."
                );
            }

            Double amount;

            try {

                amount =
                        Double.parseDouble(
                                amountValue
                                        .toString()
                                        .trim()
                        );

            } catch (NumberFormatException e) {

                throw new RuntimeException(
                        "Payment amount must be a valid number."
                );
            }


            if (amount == null ||
                    amount <= 0) {

                throw new RuntimeException(
                        "Payment amount must be greater than zero."
                );
            }


            // ========================================================
            // PAYMENT DETAILS
            // ========================================================

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


            String channel =
                    body.getOrDefault(
                            "channel",
                            ""
                    )
                    .toString()
                    .trim();


            String notes =
                    body.getOrDefault(
                            "notes",
                            ""
                    )
                    .toString()
                    .trim();


            // ========================================================
            // RECORD PAYMENT
            // ========================================================

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


            // ========================================================
            // RECORD IDEMPOTENCY SUCCESS
            // ========================================================

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


    /**
     * Get the full repayment schedule for a loan.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Payment>>>
    getSchedule(
            @PathVariable Long loanId) {

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