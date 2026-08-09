
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.PaymentGatewayRequest;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MtnMobileMoneyService {

    private static final String MTN_PROVIDER = "MTN_MOMO";

    private final WebClient.Builder webClientBuilder;

    // ============================================================
    // CONFIGURATION
    // ============================================================

    @Value("${mtn.momo.enabled:false}")
    private boolean enabled;

    @Value("${mtn.momo.sandbox:true}")
    private boolean sandbox;

    @Value("${mtn.momo.base-url:https://sandbox.momodeveloper.mtn.com}")
    private String baseUrl;

    @Value("${mtn.momo.subscription-key:}")
    private String subscriptionKey;

    @Value("${mtn.momo.api-user:}")
    private String apiUser;

    @Value("${mtn.momo.api-key:}")
    private String apiKey;

    @Value("${mtn.momo.environment:sandbox}")
    private String environment;

    @Value("${mtn.momo.currency:RWF}")
    private String configuredCurrency;


    // ============================================================
    // INITIATE PAYMENT
    // ============================================================

    public PaymentGatewayResponse initiate(
            Long loanId,
            PaymentGatewayRequest request,
            Double amount,
            String currency,
            String description) {

        // --------------------------------------------------------
        // BASIC VALIDATION
        // --------------------------------------------------------

        if (loanId == null) {
            return PaymentGatewayResponse.failed(
                    "Loan ID is required",
                    MTN_PROVIDER
            );
        }

        if (request == null) {
            return PaymentGatewayResponse.failed(
                    "Payment request is required",
                    MTN_PROVIDER
            );
        }

        if (amount == null || amount <= 0) {
            return PaymentGatewayResponse.failed(
                    "Payment amount must be greater than zero",
                    MTN_PROVIDER
            );
        }

        if (request.getPhoneNumber() == null ||
                request.getPhoneNumber().isBlank()) {

            return PaymentGatewayResponse.failed(
                    "MTN Mobile Money phone number is required",
                    MTN_PROVIDER
            );
        }

        String phoneNumber =
                normalizeRwandaPhone(
                        request.getPhoneNumber()
                );

        String transactionId =
                "MTN-" +
                loanId +
                "-" +
                UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase();

        String paymentCurrency =
                currency != null && !currency.isBlank()
                        ? currency
                        : configuredCurrency;

        log.info(
                "[MTN MOMO] Initiating payment. loanId={}, amount={}, currency={}, phone={}, transactionId={}, sandbox={}",
                loanId,
                amount,
                paymentCurrency,
                maskPhone(phoneNumber),
                transactionId,
                sandbox
        );


        // ========================================================
        // DISABLED
        // ========================================================

        if (!enabled) {

            return PaymentGatewayResponse.failed(
                    "MTN Mobile Money integration is disabled",
                    MTN_PROVIDER
            );
        }


        // ========================================================
        // LOCAL SANDBOX SIMULATION
        //
        // This allows us to test the complete application flow
        // without MTN credentials and without charging real money.
        // ========================================================

        if (sandbox) {

            log.info(
                    "[MTN SANDBOX] Simulating payment request. loanId={}, transactionId={}, amount={}",
                    loanId,
                    transactionId,
                    amount
            );

            return PaymentGatewayResponse.pending(
                    "MTN Mobile Money sandbox payment created. " +
                    "Waiting for simulated customer confirmation.",
                    transactionId,
                    MTN_PROVIDER
            );
        }


        // ========================================================
        // REAL MTN CONFIGURATION CHECK
        // ========================================================

        if (!isConfigured()) {

            log.error(
                    "[MTN MOMO] Production integration enabled but credentials are missing."
            );

            return PaymentGatewayResponse.failed(
                    "MTN Mobile Money credentials are not configured",
                    MTN_PROVIDER
            );
        }


        // ========================================================
        // REAL MTN REQUEST
        // ========================================================

        try {

            String referenceId =
                    UUID.randomUUID().toString();

            String accessToken =
                    getAccessToken();

            if (accessToken == null ||
                    accessToken.isBlank()) {

                return PaymentGatewayResponse.failed(
                        "Unable to authenticate with MTN Mobile Money",
                        MTN_PROVIDER
                );
            }


            String externalId =
                    externalReference(loanId);


            MtnRequestBody body =
                    new MtnRequestBody(
                            amount.toString(),
                            paymentCurrency,
                            externalId,
                            new MtnPayer(
                                    phoneNumber,
                                    "msisdn"
                            ),
                            description != null
                                    ? description
                                    : "Loan repayment"
                    );


            var response =
                    webClientBuilder
                            .baseUrl(baseUrl)
                            .build()
                            .post()
                            .uri(
                                    "/collection/v1_0/requesttopay"
                            )
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .header(
                                    "Authorization",
                                    "Bearer " + accessToken
                            )
                            .header(
                                    "X-Reference-Id",
                                    referenceId
                            )
                            .header(
                                    "X-Target-Environment",
                                    environment
                            )
                            .header(
                                    "Ocp-Apim-Subscription-Key",
                                    subscriptionKey
                            )
                            .bodyValue(body)
                            .retrieve()
                            .toBodilessEntity()
                            .block();


            log.info(
                    "[MTN MOMO] Request submitted. HTTP status={}, loanId={}, referenceId={}",
                    response != null
                            ? response.getStatusCode().value()
                            : "unknown",
                    loanId,
                    referenceId
            );


            // ----------------------------------------------------
            // MTN accepted the request.
            //
            // This does NOT mean payment is completed.
            // ----------------------------------------------------

            return PaymentGatewayResponse.pending(
                    "Payment request sent to MTN Mobile Money. " +
                    "Please approve the payment on your phone.",
                    referenceId,
                    MTN_PROVIDER
            );

        } catch (Exception e) {

            log.error(
                    "[MTN MOMO] Payment initiation failed. loanId={}: {}",
                    loanId,
                    e.getMessage(),
                    e
            );

            return PaymentGatewayResponse.failed(
                    "MTN Mobile Money payment initiation failed",
                    MTN_PROVIDER
            );
        }
    }


    // ============================================================
    // SANDBOX CONFIRMATION
    //
    // Used ONLY for local testing.
    // This simulates MTN confirming a pending transaction.
    // ============================================================

    public PaymentGatewayResponse simulateConfirmation(
            Long loanId,
            String transactionId,
            Double amount,
            String currency) {

        if (!sandbox) {

            return PaymentGatewayResponse.failed(
                    "Sandbox simulation is disabled",
                    MTN_PROVIDER
            );
        }

        if (transactionId == null ||
                transactionId.isBlank()) {

            return PaymentGatewayResponse.failed(
                    "Transaction ID is required",
                    MTN_PROVIDER
            );
        }

        if (amount == null ||
                amount <= 0) {

            return PaymentGatewayResponse.failed(
                    "Payment amount must be greater than zero",
                    MTN_PROVIDER
            );
        }

        log.info(
                "[MTN SANDBOX] Simulated successful payment. loanId={}, transactionId={}, amount={}",
                loanId,
                transactionId,
                amount
        );

        return PaymentGatewayResponse.success(
                "MTN Mobile Money sandbox payment confirmed",
                transactionId,
                amount,
                currency != null
                        ? currency
                        : configuredCurrency,
                "MOBILE_MONEY",
                MTN_PROVIDER
        );
    }


    // ============================================================
    // VERIFY TRANSACTION
    // ============================================================

    public boolean verify(
            String transactionId) {

        if (transactionId == null ||
                transactionId.isBlank()) {

            return false;
        }

        // --------------------------------------------------------
        // Sandbox
        // --------------------------------------------------------

        if (sandbox) {

            log.info(
                    "[MTN SANDBOX] Transaction {} considered verified for testing",
                    transactionId
            );

            return true;
        }

        // --------------------------------------------------------
        // Production verification
        // --------------------------------------------------------

        if (!isConfigured()) {

            log.warn(
                    "[MTN MOMO] Cannot verify transaction because credentials are missing"
            );

            return false;
        }

        try {

            String accessToken =
                    getAccessToken();

            if (accessToken == null ||
                    accessToken.isBlank()) {

                return false;
            }

            var response =
                    webClientBuilder
                            .baseUrl(baseUrl)
                            .build()
                            .get()
                            .uri(
                                    "/collection/v1_0/requesttopay/{referenceId}",
                                    transactionId
                            )
                            .header(
                                    "Authorization",
                                    "Bearer " + accessToken
                            )
                            .header(
                                    "X-Target-Environment",
                                    environment
                            )
                            .header(
                                    "Ocp-Apim-Subscription-Key",
                                    subscriptionKey
                            )
                            .retrieve()
                            .bodyToMono(
                                    Map.class
                            )
                            .block();

            if (response == null) {
                return false;
            }

            Object status =
                    response.get("status");

            return status != null &&
                    "SUCCESSFUL".equalsIgnoreCase(
                            status.toString()
                    );

        } catch (Exception e) {

            log.error(
                    "[MTN MOMO] Verification failed: {}",
                    e.getMessage()
            );

            return false;
        }
    }


    // ============================================================
    // ACCESS TOKEN
    // ============================================================

    private String getAccessToken() {

        try {

            String credentials =
                    apiUser + ":" + apiKey;

            String basicAuth =
                    Base64.getEncoder()
                            .encodeToString(
                                    credentials.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            );

            Map response =
                    webClientBuilder
                            .baseUrl(baseUrl)
                            .build()
                            .post()
                            .uri(
                                    "/collection/token/"
                            )
                            .header(
                                    "Authorization",
                                    "Basic " + basicAuth
                            )
                            .header(
                                    "Ocp-Apim-Subscription-Key",
                                    subscriptionKey
                            )
                            .contentType(
                                    MediaType.APPLICATION_FORM_URLENCODED
                            )
                            .retrieve()
                            .bodyToMono(
                                    Map.class
                            )
                            .block();

            if (response == null) {
                return null;
            }

            Object token =
                    response.get("access_token");

            return token != null
                    ? token.toString()
                    : null;

        } catch (Exception e) {

            log.error(
                    "[MTN MOMO] Failed to obtain access token: {}",
                    e.getMessage()
            );

            return null;
        }
    }


    // ============================================================
    // CONFIGURATION
    // ============================================================

    private boolean isConfigured() {

        return subscriptionKey != null &&
                !subscriptionKey.isBlank() &&

                apiUser != null &&
                !apiUser.isBlank() &&

                apiKey != null &&
                !apiKey.isBlank() &&

                baseUrl != null &&
                !baseUrl.isBlank();
    }


    // ============================================================
    // RWANDA PHONE NORMALIZATION
    // ============================================================

    private String normalizeRwandaPhone(
            String phone) {

        String value =
                phone.trim()
                        .replace(" ", "")
                        .replace("-", "");

        if (value.startsWith("+250")) {
            return value.substring(1);
        }

        if (value.startsWith("250")) {
            return value;
        }

        if (value.startsWith("07")) {
            return "250" + value.substring(1);
        }

        if (value.startsWith("7")) {
            return "250" + value;
        }

        return value;
    }


    // ============================================================
    // MASK PHONE
    // ============================================================

    private String maskPhone(
            String phone) {

        if (phone == null ||
                phone.length() < 4) {

            return "***";
        }

        return "***" +
                phone.substring(
                        phone.length() - 4
                );
    }


    // ============================================================
    // EXTERNAL REFERENCE
    // ============================================================

    private String externalReference(
            Long loanId) {

        return "LOAN-" +
                loanId +
                "-" +
                UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase();
    }


    // ============================================================
    // MTN REQUEST DTOs
    // ============================================================

    private record MtnRequestBody(
            String amount,
            String currency,
            String externalId,
            MtnPayer payer,
            String payerMessage
    ) {
    }


    private record MtnPayer(
            String partyId,
            String partyIdType
    ) {
    }
}
