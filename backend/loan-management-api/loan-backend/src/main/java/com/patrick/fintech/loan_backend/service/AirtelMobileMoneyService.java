
package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayRequest;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Direct Airtel Money collection service.
 *
 * Flow:
 *
 * 1. Request OAuth access token from Airtel.
 * 2. Create a merchant collection request.
 * 3. Airtel prompts the customer to authorize the payment.
 * 4. Return PENDING to the website.
 * 5. Airtel callback/status verification completes the payment.
 *
 * IMPORTANT:
 * The exact production base URL and collection URL must be supplied
 * by your Airtel Money merchant/API account.
 *
 * They are therefore configurable through application.properties.
 */
@Slf4j
@Service
public class AirtelMobileMoneyService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${airtel.money.enabled:false}")
    private boolean enabled;

    /**
     * Example:
     * https://openapiuat.airtel.africa
     *
     * Replace with the production base URL supplied for your Airtel
     * Money merchant account.
     */
    @Value("${airtel.money.base-url:}")
    private String baseUrl;

    /**
     * OAuth endpoint.
     *
     * Default follows the Airtel Money Open API structure.
     */
    @Value("${airtel.money.token-path:/auth/oauth2/token}")
    private String tokenPath;

    /**
     * Collection endpoint.
     *
     * Default follows the Airtel Money merchant collection API structure.
     */
    @Value("${airtel.money.collection-path:/merchant/v1/payments/}")
    private String collectionPath;

    /**
     * Transaction status endpoint.
     *
     * Configure this according to the endpoint supplied by Airtel
     * for your merchant account.
     */
    @Value("${airtel.money.status-path:/standard/v1/payments/{transactionId}}")
    private String statusPath;

    @Value("${airtel.money.client-id:}")
    private String clientId;

    @Value("${airtel.money.client-secret:}")
    private String clientSecret;

    @Value("${airtel.money.api-key:}")
    private String apiKey;

    @Value("${airtel.money.callback-url:}")
    private String callbackUrl;

    @Value("${airtel.money.country:RW}")
    private String country;

    @Value("${airtel.money.currency:RWF}")
    private String defaultCurrency;

    public AirtelMobileMoneyService(
            WebClient.Builder builder,
            ObjectMapper objectMapper) {

        this.webClient = builder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * Returns true only when the minimum production configuration exists.
     */
    public boolean isConfigured() {

        return enabled
                && hasText(baseUrl)
                && hasText(clientId)
                && hasText(clientSecret);
    }

    /**
     * Starts an Airtel Money collection transaction.
     */
    public PaymentGatewayResponse initiate(
            Long loanId,
            PaymentGatewayRequest request,
            Double amount,
            String currency,
            String description) {

        if (loanId == null) {
            return PaymentGatewayResponse.failed(
                    "AIRTEL_DIRECT",
                    "Loan ID is required"
            );
        }

        if (amount == null || amount <= 0) {
            return PaymentGatewayResponse.failed(
                    "AIRTEL_DIRECT",
                    "Payment amount must be greater than zero"
            );
        }

        if (request == null) {
            return PaymentGatewayResponse.failed(
                    "AIRTEL_DIRECT",
                    "Payment request is required"
            );
        }

        String phone = normalizePhone(request.getPhoneNumber());

        if (!hasText(phone)) {
            return PaymentGatewayResponse.failed(
                    "AIRTEL_DIRECT",
                    "Airtel Money phone number is required"
            );
        }

        if (!isConfigured()) {

            log.warn(
                    "[AIRTEL] Direct Airtel Money is not configured. " +
                    "loanId={}, amount={}, phone={}",
                    loanId,
                    amount,
                    maskPhone(phone)
            );

            return PaymentGatewayResponse.failed(
                    "AIRTEL_DIRECT",
                    "Airtel Money direct integration is not configured"
            );
        }

        String paymentReference = createReference(loanId);

        String paymentCurrency =
                hasText(currency)
                        ? currency.toUpperCase()
                        : defaultCurrency.toUpperCase();

        try {

            String accessToken = getAccessToken();

            if (!hasText(accessToken)) {

                return PaymentGatewayResponse.failed(
                        "AIRTEL_DIRECT",
                        "Unable to obtain Airtel Money access token"
                );
            }

            Map<String, Object> payload =
                    buildCollectionPayload(
                            paymentReference,
                            phone,
                            amount,
                            paymentCurrency,
                            description
                    );

            Map<String, Object> response =
                    sendCollectionRequest(
                            accessToken,
                            payload
                    );

            return parseCollectionResponse(
                    response,
                    paymentReference,
                    amount,
                    paymentCurrency
            );

        } catch (Exception e) {

            log.error(
                    "[AIRTEL] Payment initiation failed. " +
                    "loanId={}, reference={}, error={}",
                    loanId,
                    paymentReference,
                    e.getMessage(),
                    e
            );

            return PaymentGatewayResponse.failed(
                    "AIRTEL_DIRECT",
                    "Unable to initiate Airtel Money payment: "
                            + safeMessage(e)
            );
        }
    }

    /**
     * Obtains an OAuth access token from Airtel.
     */
    private String getAccessToken() {

        Map<String, Object> body =
                new LinkedHashMap<>();

        body.put(
                "client_id",
                clientId
        );

        body.put(
                "client_secret",
                clientSecret
        );

        body.put(
                "grant_type",
                "client_credentials"
        );

        WebClient.RequestBodySpec request =
                webClient
                        .post()
                        .uri(buildUri(tokenPath))
                        .contentType(
                                MediaType.APPLICATION_FORM_URLENCODED
                        );

        Map<String, Object> response =
                request
                        .bodyValue(
                                "client_id="
                                        + urlEncode(clientId)
                                        + "&client_secret="
                                        + urlEncode(clientSecret)
                                        + "&grant_type=client_credentials"
                        )
                        .retrieve()
                        .bodyToMono(
                                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
                        )
                        .block();

        if (response == null) {
            throw new IllegalStateException(
                    "Empty OAuth response from Airtel"
            );
        }

        Object token =
                response.get("access_token");

        if (token == null) {

            Object data =
                    response.get("data");

            if (data instanceof Map<?, ?> map) {
                token = map.get("access_token");
            }
        }

        if (token == null) {

            throw new IllegalStateException(
                    "Airtel OAuth response did not contain access_token"
            );
        }

        return String.valueOf(token);
    }

    /**
     * Sends the actual Airtel Money collection request.
     */
    private Map<String, Object> sendCollectionRequest(
            String accessToken,
            Map<String, Object> payload) {

        WebClient.RequestBodySpec request =
                webClient
                        .post()
                        .uri(buildUri(collectionPath))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken
                        )
                        .header(
                                "X-Country",
                                country
                        )
                        .header(
                                "X-Currency",
                                defaultCurrency
                        );

        if (hasText(apiKey)) {

            request.header(
                    "X-API-Key",
                    apiKey
            );
        }

        if (hasText(callbackUrl)) {

            request.header(
                    "X-Callback-Url",
                    callbackUrl
            );
        }

        return request
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(
                        new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
                )
                .block();
    }

    /**
     * Builds the Airtel collection payload.
     *
     * This is deliberately isolated so that if Airtel gives your merchant
     * account a different payload schema, only this method needs adjustment.
     */
    private Map<String, Object> buildCollectionPayload(
            String reference,
            String phone,
            Double amount,
            String currency,
            String description) {

        Map<String, Object> payload =
                new LinkedHashMap<>();

        payload.put(
                "reference",
                reference
        );

        Map<String, Object> subscriber =
                new LinkedHashMap<>();

        subscriber.put(
                "country",
                country
        );

        subscriber.put(
                "currency",
                currency
        );

        subscriber.put(
                "msisdn",
                phone
        );

        payload.put(
                "subscriber",
                subscriber
        );

        Map<String, Object> transaction =
                new LinkedHashMap<>();

        transaction.put(
                "amount",
                amount
        );

        transaction.put(
                "country",
                country
        );

        transaction.put(
                "currency",
                currency
        );

        transaction.put(
                "id",
                reference
        );

        if (hasText(description)) {

            transaction.put(
                    "description",
                    description
            );
        }

        payload.put(
                "transaction",
                transaction
        );

        return payload;
    }

    /**
     * Converts Airtel's response into your existing PaymentGatewayResponse.
     */
    @SuppressWarnings("unchecked")
    private PaymentGatewayResponse parseCollectionResponse(
            Map<String, Object> response,
            String reference,
            Double amount,
            String currency) {

        if (response == null) {

            return PaymentGatewayResponse.failed(
                    "AIRTEL_DIRECT",
                    "Airtel returned an empty response"
            );
        }

        log.info(
                "[AIRTEL] Collection response reference={}: {}",
                reference,
                response
        );

        String status =
                stringValue(
                        response.get("status")
                );

        String message =
                stringValue(
                        response.get("message")
                );

        Map<String, Object> data =
                null;

        Object dataObject =
                response.get("data");

        if (dataObject instanceof Map<?, ?>) {

            data =
                    (Map<String, Object>) dataObject;
        }

        String transactionId =
                reference;

        if (data != null) {

            Object id =
                    data.get("transaction_id");

            if (id == null) {
                id = data.get("transactionId");
            }

            if (id == null) {
                id = data.get("id");
            }

            if (id != null) {
                transactionId =
                        String.valueOf(id);
            }

            Object responseMessage =
                    data.get("message");

            if (!hasText(message)
                    && responseMessage != null) {

                message =
                        String.valueOf(
                                responseMessage
                        );
            }

            Object responseStatus =
                    data.get("status");

            if (!hasText(status)
                    && responseStatus != null) {

                status =
                        String.valueOf(
                                responseStatus
                        );
            }
        }

        if (isSuccessfulStatus(status)) {

            return PaymentGatewayResponse.pending(
                    hasText(message)
                            ? message
                            : "Payment request sent. Please approve the payment on your Airtel Money phone.",
                    transactionId,
                    "AIRTEL_DIRECT"
            );
        }

        if (isPendingStatus(status)) {

            return PaymentGatewayResponse.pending(
                    hasText(message)
                            ? message
                            : "Payment is awaiting Airtel Money customer confirmation.",
                    transactionId,
                    "AIRTEL_DIRECT"
            );
        }

        return PaymentGatewayResponse.failed(
                "AIRTEL_DIRECT",
                hasText(message)
                        ? message
                        : "Airtel Money rejected the payment request"
        );
    }

    /**
     * Verifies an Airtel Money transaction.
     *
     * The exact status endpoint must match the endpoint enabled for
     * your Airtel merchant account.
     */
    public boolean verify(
            String transactionId) {

        if (!isConfigured()) {

            log.warn(
                    "[AIRTEL] Verification requested while service is not configured"
            );

            return false;
        }

        if (!hasText(transactionId)) {
            return false;
        }

        try {

            String accessToken =
                    getAccessToken();

            String uri =
                    buildStatusUri(
                            transactionId
                    );

            WebClient.RequestHeadersSpec<?> request =
                    webClient
                            .get()
                            .uri(uri)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + accessToken
                            )
                            .header(
                                    "X-Country",
                                    country
                            )
                            .header(
                                    "X-Currency",
                                    defaultCurrency
                            );

            if (hasText(apiKey)) {

                request =
                        request.header(
                                "X-API-Key",
                                apiKey
                        );
            }

            Map<String, Object> response =
                    request
                            .retrieve()
                            .bodyToMono(
                                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
                            )
                            .block();

            return response != null
                    && responseIndicatesSuccessfulPayment(
                            response
                    );

        } catch (Exception e) {

            log.error(
                    "[AIRTEL] Transaction verification failed. " +
                    "transactionId={}, error={}",
                    transactionId,
                    e.getMessage()
            );

            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean responseIndicatesSuccessfulPayment(
            Map<String, Object> response) {

        String status =
                stringValue(
                        response.get("status")
                );

        if (isSuccessfulTransactionStatus(status)) {
            return true;
        }

        Object data =
                response.get("data");

        if (data instanceof Map<?, ?> map) {

            Object nestedStatus =
                    map.get("status");

            if (isSuccessfulTransactionStatus(
                    stringValue(nestedStatus))) {

                return true;
            }

            Object transaction =
                    map.get("transaction");

            if (transaction instanceof Map<?, ?> tx) {

                return isSuccessfulTransactionStatus(
                        stringValue(
                                tx.get("status")
                        )
                );
            }
        }

        return false;
    }

    private boolean isSuccessfulStatus(
            String status) {

        if (status == null) {
            return false;
        }

        String value =
                status.trim().toUpperCase();

        return value.equals("SUCCESS")
                || value.equals("SUCCESSFUL")
                || value.equals("PENDING");
    }

    private boolean isPendingStatus(
            String status) {

        if (status == null) {
            return true;
        }

        String value =
                status.trim().toUpperCase();

        return value.equals("PENDING")
                || value.equals("PROCESSING")
                || value.equals("INITIATED")
                || value.equals("IN_PROGRESS")
                || value.equals("ACCEPTED");
    }

    private boolean isSuccessfulTransactionStatus(
            String status) {

        if (status == null) {
            return false;
        }

        String value =
                status.trim().toUpperCase();

        return value.equals("SUCCESS")
                || value.equals("SUCCESSFUL")
                || value.equals("COMPLETED")
                || value.equals("COMPLETED_SUCCESSFULLY");
    }

    private String createReference(
            Long loanId) {

        return "LOAN-"
                + loanId
                + "-AIR-"
                + Instant.now().toEpochMilli()
                + "-"
                + UUID.randomUUID()
                    .toString()
                    .substring(0, 8)
                    .toUpperCase();
    }

    private String buildUri(
            String path) {

        if (path == null || path.isBlank()) {

            throw new IllegalStateException(
                    "Airtel API path is not configured"
            );
        }

        String cleanBase =
                baseUrl.endsWith("/")
                        ? baseUrl.substring(
                                0,
                                baseUrl.length() - 1
                        )
                        : baseUrl;

        String cleanPath =
                path.startsWith("/")
                        ? path
                        : "/" + path;

        return cleanBase + cleanPath;
    }

    private String buildStatusUri(
            String transactionId) {

        String path =
                statusPath.replace(
                        "{transactionId}",
                        transactionId
                );

        return buildUri(path);
    }

    private String normalizePhone(
            String phone) {

        if (!hasText(phone)) {
            return null;
        }

        String value =
                phone.trim()
                        .replace(" ", "")
                        .replace("-", "");

        if (value.startsWith("+")) {
            value = value.substring(1);
        }

        /*
         * Rwanda:
         * +250 78xxxxxxx -> 25078xxxxxxx
         *
         * If the frontend sends 078xxxxxxx,
         * convert it to 25078xxxxxxx.
         */
        if (value.startsWith("0")
                && value.length() == 10) {

            value =
                    "250"
                            + value.substring(1);
        }

        return value;
    }

    private String maskPhone(
            String phone) {

        if (!hasText(phone)
                || phone.length() < 6) {

            return "***";
        }

        return phone.substring(0, 4)
                + "****"
                + phone.substring(
                        phone.length() - 2
                );
    }

    private String stringValue(
            Object value) {

        return value == null
                ? null
                : String.valueOf(value);
    }

    private boolean hasText(
            String value) {

        return value != null
                && !value.isBlank();
    }

    private String safeMessage(
            Exception e) {

        if (e.getMessage() == null
                || e.getMessage().isBlank()) {

            return e.getClass()
                    .getSimpleName();
        }

        return e.getMessage();
    }

    private String urlEncode(
            String value) {

        try {

            return java.net.URLEncoder
                    .encode(
                            value,
                            java.nio.charset.StandardCharsets.UTF_8
                    );

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Unable to encode Airtel OAuth parameter",
                    e
            );
        }
    }
}
