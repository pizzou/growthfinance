
package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayRequest;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayResponse;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.service.AirtelMobileMoneyService;
import com.patrick.fintech.loan_backend.service.FlutterwaveService;
import com.patrick.fintech.loan_backend.service.MtnMobileMoneyService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/loans/{loanId}/payments/gateway")
@RequiredArgsConstructor
@Slf4j
public class PaymentGatewayController {

    private final FlutterwaveService flutterwaveService;

    private final MtnMobileMoneyService mtnMobileMoneyService;

    private final AirtelMobileMoneyService airtelMoneyService;

    private final LoanRepository loanRepo;

    private final CurrentUserUtil currentUserUtil;

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initiate(
            @PathVariable Long loanId,
            @Valid @RequestBody PaymentGatewayRequest request) {

        var user =
                currentUserUtil.getCurrentUser();

        Loan loan =
                loanRepo.findById(loanId)
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Loan not found"
                                )
                        );

        if (loan.getOrganization() == null ||
                user.getOrganization() == null ||
                !loan.getOrganization()
                        .getId()
                        .equals(
                                user.getOrganization()
                                        .getId()
                        )) {

            throw new RuntimeException(
                    "Access denied"
            );
        }

        validateAmount(
                request.getAmount()
        );

        String provider =
                normalizeProvider(
                        request
                                .getProvider()
                );

        String paymentMethod =
                normalizeProvider(
                        request
                                .getPaymentMethod()
                );

        PaymentGatewayResponse gatewayResponse;

        String currency =
                loan.getCurrency() != null
                        ? loan.getCurrency()
                        : "RWF";

        String description =
                "Loan repayment "
                        + loan.getReferenceNumber();

        if ("MOBILE_MONEY".equals(
                paymentMethod)) {

            gatewayResponse =
                    initiateMobileMoney(
                            provider,
                            loanId,
                            request,
                            currency,
                            description
                    );

        } else if ("FLUTTERWAVE".equals(
                provider)) {

            gatewayResponse =
                    flutterwaveService
                            .initiatePayment(
                                    loanId,
                                    request,
                                    request.getAmount(),
                                    currency,
                                    description
                            );

        } else {

            throw new RuntimeException(
                    "Unsupported payment provider: "
                            + provider
            );
        }

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "status",
                gatewayResponse.getStatus()
        );

        result.put(
                "message",
                gatewayResponse.getMessage()
        );

        result.put(
                "transactionId",
                gatewayResponse
                        .getTransactionId()
        );

        result.put(
                "providerReference",
                gatewayResponse
                        .getProviderReference()
        );

        result.put(
                "provider",
                provider
        );

        result.put(
                "paymentMethod",
                paymentMethod
        );

        result.put(
                "amount",
                gatewayResponse.getAmount()
                        != null
                        ? gatewayResponse.getAmount()
                        : request.getAmount()
        );

        result.put(
                "currency",
                currency
        );

        result.put(
                "redirectUrl",
                gatewayResponse
                        .getRedirectUrl()
        );

        /*
         * CRITICAL:
         *
         * Do NOT record a real mobile-money payment here.
         *
         * MTN/Airtel/Flutterwave mobile-money requests are
         * asynchronous.
         *
         * The webhook/verification process records the
         * payment only after the transaction is confirmed.
         */

        boolean completedImmediately =
                "SUCCESS".equalsIgnoreCase(
                        gatewayResponse.getStatus()
                )
                &&
                !"MOBILE_MONEY".equals(
                        paymentMethod
                );

        result.put(
                "recorded",
                completedImmediately
        );

        if ("FAILED".equalsIgnoreCase(
                gatewayResponse.getStatus())) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            ApiResponse.ok(
                                    "Payment initiation failed",
                                    result
                            )
                    );
        }

        if ("PENDING".equalsIgnoreCase(
                gatewayResponse.getStatus()
        ) ||
            "INITIATED".equalsIgnoreCase(
                gatewayResponse.getStatus()
            )) {

            return ResponseEntity.ok(
                    ApiResponse.ok(
                            "Payment initiated. Waiting for customer confirmation.",
                            result
                    )
            );
        }

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Payment request processed",
                        result
                )
        );
    }

    private PaymentGatewayResponse initiateMobileMoney(
            String provider,
            Long loanId,
            PaymentGatewayRequest request,
            String currency,
            String description) {

        switch (provider) {

            case "FLUTTERWAVE":

                return flutterwaveService
                        .initiatePayment(
                                loanId,
                                request,
                                request.getAmount(),
                                currency,
                                description
                        );

            case "MTN_DIRECT":

                return mtnMobileMoneyService
                        .initiate(
                                loanId,
                                request,
                                request.getAmount(),
                                currency,
                                description
                        );

            case "AIRTEL_DIRECT":

                return airtelMoneyService
                        .initiate(
                                loanId,
                                request,
                                request.getAmount(),
                                currency,
                                description
                        );

            default:

                throw new RuntimeException(
                        "Unsupported mobile-money provider: "
                                + provider
                );
        }
    }

    private void validateAmount(
            Double amount) {

        if (amount == null ||
                amount <= 0) {

            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }
    }

    private String normalizeProvider(
            String value) {

        if (value == null) {

            throw new IllegalArgumentException(
                    "Payment provider is required"
            );
        }

        return value
                .trim()
                .toUpperCase();
    }
}
