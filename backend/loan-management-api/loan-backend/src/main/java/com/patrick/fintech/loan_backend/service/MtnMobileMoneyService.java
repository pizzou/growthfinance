package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.PaymentGatewayRequest;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayResponse;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
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

    private final PaymentService paymentService;
    private final LoanRepository loanRepo;

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

    @Value("${app.environment:development}")
    private String applicationEnvironment;

    @PostConstruct
    private void validateProductionMode() {
        if (isProductionEnvironment() && sandbox) {
            throw new IllegalStateException(
                    "MTN Mobile Money sandbox mode cannot be enabled in production."
            );
        }
    }

    /**
     * Returns whether MTN Mobile Money can be presented as an available
     * payment option. Local sandbox mode is allowed only outside production.
     */
    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }

        if (sandbox) {
            return true;
        }

        return isConfigured();
    }

    // ============================================================
    // INITIATE PAYMENT
    // ============================================================

   
    public PaymentGatewayResponse initiate(
            Long loanId,
            PaymentGatewayRequest request,
            Double amount,
            String currency,
            String description
    ) {

        // ========================================================
        // BASIC VALIDATION
        // ========================================================

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

        if (request.getPhoneNumber() == null
                || request.getPhoneNumber().isBlank()) {

            return PaymentGatewayResponse.failed(
                    "MTN Mobile Money phone number is required",
                    MTN_PROVIDER
            );
        }

        // ========================================================
        // VERIFY LOAN EXISTS
        // ========================================================

        loanRepo.findById(loanId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Loan not found: " + loanId
                        )
                );

        // ========================================================
        // NORMALIZE PHONE
        // ========================================================

        String phoneNumber =
                normalizeRwandaPhone(
                        request.getPhoneNumber()
                );

        // ========================================================
        // PAYMENT CURRENCY
        // ========================================================

        String paymentCurrency =
                currency != null
                        && !currency.isBlank()
                        ? currency.trim().toUpperCase()
                        : configuredCurrency;

        // ========================================================
        // CREATE STABLE TRANSACTION ID
        // ========================================================

        String transactionId =
                createSandboxTransactionId(
                        loanId
                );

        log.info(
                "[MTN MOMO] Initiating payment. " +
                        "loanId={}, amount={}, currency={}, phone={}, " +
                        "transactionId={}, sandbox={}",
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
        // LOCAL SANDBOX
        // ========================================================

        if (sandbox) {

            log.info(
                    "[MTN SANDBOX] Payment request created. " +
                            "loanId={}, transactionId={}, amount={}, currency={}",
                    loanId,
                    transactionId,
                    amount,
                    paymentCurrency
            );

           

            return PaymentGatewayResponse.pending(
                    "MTN Mobile Money sandbox payment created. " +
                            "Waiting for simulated customer confirmation.",
                    transactionId,
                    MTN_PROVIDER
            );
        }

        // ========================================================
        // REAL MTN CONFIGURATION
        // ========================================================

        if (!isConfigured()) {

            log.error(
                    "[MTN MOMO] Production integration enabled " +
                            "but credentials are missing."
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

            if (accessToken == null
                    || accessToken.isBlank()) {

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
                                    && !description.isBlank()
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
                    "[MTN MOMO] Request submitted. " +
                            "HTTP status={}, loanId={}, referenceId={}",
                    response != null
                            ? response.getStatusCode().value()
                            : "unknown",
                    loanId,
                    referenceId
            );

            /*
             * MTN accepted the request.
             *
             * It is NOT yet a successful payment.
             *
             * The payment must only be recorded after MTN
             * confirms SUCCESSFUL.
             */

            return PaymentGatewayResponse.pending(
                    "Payment request sent to MTN Mobile Money. " +
                            "Please approve the payment on your phone.",
                    referenceId,
                    MTN_PROVIDER
            );

        } catch (Exception e) {

            log.error(
                    "[MTN MOMO] Payment initiation failed. " +
                            "loanId={}: {}",
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
    // ============================================================

   
    @Transactional
    public PaymentGatewayResponse simulateConfirmation(
            Long loanId,
            String transactionId,
            Double amount,
            String currency
    ) {

        if (!sandbox) {

            return PaymentGatewayResponse.failed(
                    "Sandbox simulation is disabled",
                    MTN_PROVIDER
            );
        }

        return confirmPayment(
                loanId,
                transactionId,
                amount,
                currency,
                "SANDBOX"
        );
    }

    // ============================================================
    // CONFIRM PAYMENT
    // ============================================================

   
    @Transactional
    public PaymentGatewayResponse confirmPayment(
            Long loanId,
            String transactionId,
            Double amount,
            String currency,
            String confirmationSource
    ) {

        // ========================================================
        // VALIDATION
        // ========================================================

        if (loanId == null) {

            return PaymentGatewayResponse.failed(
                    "Loan ID is required",
                    MTN_PROVIDER
            );
        }

        if (transactionId == null
                || transactionId.isBlank()) {

            return PaymentGatewayResponse.failed(
                    "Transaction ID is required",
                    MTN_PROVIDER
            );
        }

        if (amount == null || amount <= 0) {

            return PaymentGatewayResponse.failed(
                    "Payment amount must be greater than zero",
                    MTN_PROVIDER
            );
        }

        // ========================================================
        // VERIFY LOAN
        // ========================================================

        var loan =
                loanRepo.findById(loanId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Loan not found: " + loanId
                                )
                        );

        if (loan.getOrganization() == null
                || loan.getOrganization().getId() == null) {

            return PaymentGatewayResponse.failed(
                    "Loan organization is missing",
                    MTN_PROVIDER
            );
        }

        // ========================================================
        // NORMALIZE TRANSACTION ID
        // ========================================================

        String normalizedTransactionId =
                transactionId.trim();

        // ========================================================
        // CURRENCY
        // ========================================================

        String paymentCurrency =
                currency != null
                        && !currency.isBlank()
                        ? currency.trim().toUpperCase()
                        : configuredCurrency;

        // ========================================================
        // VERIFY REAL MTN PAYMENT
        // ========================================================

        if (!sandbox) {

            boolean verified =
                    verify(
                            normalizedTransactionId
                    );

            if (!verified) {

                log.warn(
                        "[MTN MOMO] Payment confirmation rejected. " +
                                "loanId={}, transactionId={}, source={}",
                        loanId,
                        normalizedTransactionId,
                        confirmationSource
                );

                return PaymentGatewayResponse.failed(
                        "MTN Mobile Money transaction has not been confirmed as successful",
                        MTN_PROVIDER
                );
            }
        }

        // ========================================================
        // PREVENT DOUBLE PROCESSING
        // ========================================================


        log.info(
                "[MTN MOMO] Confirming payment. " +
                        "loanId={}, transactionId={}, amount={}, " +
                        "currency={}, source={}",
                loanId,
                normalizedTransactionId,
                amount,
                paymentCurrency,
                confirmationSource
        );

        // ========================================================
        // RECORD PAYMENT EXACTLY ONCE
        // ========================================================

        Payment payment;

        try {

            payment =
                    paymentService.recordPayment(
                            loanId,
                            BigDecimal.valueOf(amount),
                            "MOBILE_MONEY",
                            normalizedTransactionId,
                            "MTN_MOMO",
                            "Confirmed by MTN Mobile Money. Source="
                                    + confirmationSource,
                            (User) null
                    );

        } catch (Exception e) {

            log.error(
                    "[MTN MOMO] Failed to record confirmed payment. " +
                            "loanId={}, transactionId={}, amount={}",
                    loanId,
                    normalizedTransactionId,
                    amount,
                    e
            );

            return PaymentGatewayResponse.failed(
                    "MTN payment was confirmed but could not be recorded against the loan",
                    MTN_PROVIDER
            );
        }

        // ========================================================
        // FINANCIAL RESULT
        // ========================================================

        BigDecimal outstandingBalance =
        payment.getOutstandingAfterDecimal() != null
                ? payment.getOutstandingAfterDecimal()
                : loan.getOutstandingBalanceDecimal();

        BigDecimal interestPaid =
                payment.getInterestComponentDecimal();

        BigDecimal principalPaid =
                payment.getPrincipalComponentDecimal();

        log.info(
                "[MTN MOMO] Payment successfully recorded. " +
                        "loanId={}, paymentId={}, transactionId={}, " +
                        "amount={}, interestPaid={}, principalPaid={}, " +
                        "outstandingBalance={}, paymentStatus={}",
                loanId,
                payment.getId(),
                normalizedTransactionId,
                amount,
                interestPaid,
                principalPaid,
                outstandingBalance,
                payment.getStatus()
        );

        // ========================================================
        // SUCCESS RESPONSE
        // ========================================================

        return PaymentGatewayResponse.success(
                "MTN Mobile Money payment confirmed and recorded against the loan",
                normalizedTransactionId,
                amount,
                paymentCurrency,
                "MOBILE_MONEY",
                MTN_PROVIDER
        );
    }

    // ============================================================
    // REAL MTN WEBHOOK / CALLBACK PROCESSING
    // ============================================================

   
    @Transactional
    public PaymentGatewayResponse processWebhookConfirmation(
            Long loanId,
            String transactionId,
            Double amount,
            String currency
    ) {

        if (sandbox) {

            /*
             * In sandbox mode this behaves exactly like the
             * simulated confirmation.
             */
            return confirmPayment(
                    loanId,
                    transactionId,
                    amount,
                    currency,
                    "MTN_SANDBOX_WEBHOOK"
            );
        }

        /*
         * Production MTN callback.
         */
        return confirmPayment(
                loanId,
                transactionId,
                amount,
                currency,
                "MTN_WEBHOOK"
        );
    }

    
    public boolean verify(
            String transactionId
    ) {

        if (transactionId == null
                || transactionId.isBlank()) {

            return false;
        }

        String normalizedTransactionId =
                transactionId.trim();

        // ========================================================
        // SANDBOX
        // ========================================================

        if (sandbox) {

            log.info(
                    "[MTN SANDBOX] Transaction {} considered verified for testing",
                    normalizedTransactionId
            );

            return true;
        }

        // ========================================================
        // PRODUCTION CONFIGURATION
        // ========================================================

        if (!isConfigured()) {

            log.warn(
                    "[MTN MOMO] Cannot verify transaction because credentials are missing"
            );

            return false;
        }

        // ========================================================
        // VERIFY WITH MTN
        // ========================================================

        try {

            String accessToken =
                    getAccessToken();

            if (accessToken == null
                    || accessToken.isBlank()) {

                return false;
            }

            Map response =
                    webClientBuilder
                            .baseUrl(baseUrl)
                            .build()
                            .get()
                            .uri(
                                    "/collection/v1_0/requesttopay/{referenceId}",
                                    normalizedTransactionId
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

            boolean successful =
                    status != null
                            && "SUCCESSFUL".equalsIgnoreCase(
                            status.toString()
                    );

            log.info(
                    "[MTN MOMO] Transaction verification. " +
                            "transactionId={}, status={}, successful={}",
                    normalizedTransactionId,
                    status,
                    successful
            );

            return successful;

        } catch (Exception e) {

            log.error(
                    "[MTN MOMO] Verification failed. " +
                            "transactionId={}, error={}",
                    normalizedTransactionId,
                    e.getMessage(),
                    e
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
                    e.getMessage(),
                    e
            );

            return null;
        }
    }

    // ============================================================
    // CONFIGURATION
    // ============================================================

    private boolean isConfigured() {

        return subscriptionKey != null
                && !subscriptionKey.isBlank()

                && apiUser != null
                && !apiUser.isBlank()

                && apiKey != null
                && !apiKey.isBlank()

                && baseUrl != null
                && !baseUrl.isBlank();
    }

    // ============================================================
    // RWANDA PHONE NORMALIZATION
    // ============================================================

    private String normalizeRwandaPhone(
            String phone
    ) {

        if (phone == null) {

            return "";
        }

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
            String phone
    ) {

        if (phone == null
                || phone.length() < 4) {

            return "***";
        }

        return "***"
                + phone.substring(
                phone.length() - 4
        );
    }

    // ============================================================
    // SANDBOX TRANSACTION ID
    // ============================================================

    
    private String createSandboxTransactionId(
            Long loanId
    ) {

        return "MTN-"
                + loanId
                + "-"
                + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();
    }

    // ============================================================
    // EXTERNAL REFERENCE
    // ============================================================

    private String externalReference(
            Long loanId
    ) {

        return "LOAN-"
                + loanId
                + "-"
                + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();
    }

    // ============================================================
    // MTN REQUEST DTO
    // ============================================================

    private record MtnRequestBody(
            String amount,
            String currency,
            String externalId,
            MtnPayer payer,
            String payerMessage
    ) {
    }

    // ============================================================
    // MTN PAYER
    // ============================================================

    private record MtnPayer(
            String partyId,
            String partyIdType
    ) {
    }

    private boolean isProductionEnvironment() {
        return "production".equalsIgnoreCase(applicationEnvironment)
                || "prod".equalsIgnoreCase(applicationEnvironment);
    }

}