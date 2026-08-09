
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.PaymentGatewayRequest;
import com.patrick.fintech.loan_backend.dto.PaymentGatewayResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ============================================================
 * FLUTTERWAVE PAYMENT SERVICE
 * ============================================================
 *
 * Supports:
 *
 *  - Card
 *  - Mobile Money
 *  - Bank Transfer
 *
 * Rwanda:
 *  - RWF
 *  - MTN Mobile Money
 *  - Airtel Money
 *
 * Real payments are used when:
 *
 *     FLUTTERWAVE_SECRET_KEY
 *
 * is configured.
 *
 * If the secret key is empty, the service runs in simulation
 * mode. Simulation mode must NEVER be used in production.
 */
@Slf4j
@Service
public class FlutterwaveService {

    private static final String FLW_BASE =
            "https://api.flutterwave.com/v3";

    @Value("${flutterwave.secret-key:}")
    private String secretKey;

    @Value("${flutterwave.public-key:}")
    private String publicKey;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    private final WebClient webClient;

    public FlutterwaveService(WebClient.Builder builder) {

        this.webClient = builder
                .baseUrl(FLW_BASE)
                .defaultHeader(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
    }

    /**
     * ============================================================
     * CONFIGURATION
     * ============================================================
     */
    public boolean isConfigured() {

        return secretKey != null
                && !secretKey.isBlank();
    }

    /**
     * ============================================================
     * INITIATE PAYMENT
     * ============================================================
     */
    public PaymentGatewayResponse initiatePayment(
            Long loanId,
            PaymentGatewayRequest req,
            Double amount,
            String currency,
            String description
    ) {

        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }

        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException(
                    "Loan currency is required"
            );
        }

        if (req == null) {
            throw new IllegalArgumentException(
                    "Payment request is required"
            );
        }

        String paymentMethod =
                req.getPaymentMethod();

        if (paymentMethod == null
                || paymentMethod.isBlank()) {

            throw new IllegalArgumentException(
                    "Payment method is required"
            );
        }

        /**
         * --------------------------------------------------------
         * DEVELOPMENT / SIMULATION MODE
         * --------------------------------------------------------
         *
         * Only used when no Flutterwave secret key exists.
         */
        if (!isConfigured()) {

            log.warn(
                    "[FLW SIMULATION] {} {} via {}",
                    currency,
                    amount,
                    paymentMethod
            );

            return simulatedSuccess(
                    amount,
                    currency,
                    paymentMethod
            );
        }

        return switch (
                paymentMethod.toUpperCase()
        ) {

            case "CARD" ->
                    chargeCard(
                            loanId,
                            req,
                            amount,
                            currency
                    );

            case "MOBILE_MONEY" ->
                    chargeMobileMoney(
                            loanId,
                            req,
                            amount,
                            currency
                    );

            case "BANK_TRANSFER" ->
                    chargeBankTransfer(
                            loanId,
                            req,
                            amount,
                            currency
                    );

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported payment method: "
                                    + paymentMethod
                    );
        };
    }

    /**
     * ============================================================
     * VERIFY FLUTTERWAVE TRANSACTION
     * ============================================================
     *
     * Never trust the webhook payload alone.
     *
     * The backend asks Flutterwave directly whether the
     * transaction is actually successful.
     */
    @SuppressWarnings("unchecked")
    public boolean verifyTransaction(
            String transactionId
    ) {

        if (transactionId == null
                || transactionId.isBlank()) {

            return false;
        }

        if (!isConfigured()) {

            log.warn(
                    "[FLW SIMULATION] Transaction {} treated as verified",
                    transactionId
            );

            return true;
        }

        try {

            Map<String, Object> response =
                    webClient.get()
                            .uri(
                                    "/transactions/"
                                            + transactionId
                                            + "/verify"
                            )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + secretKey
                            )
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block();

            if (response == null) {
                return false;
            }

            Map<String, Object> data =
                    (Map<String, Object>)
                            response.get("data");

            if (data == null) {
                return false;
            }

            String status =
                    String.valueOf(
                            data.get("status")
                    );

            return "successful".equalsIgnoreCase(
                    status
            );

        } catch (Exception e) {

            log.error(
                    "[FLW] Transaction verification failed: {}",
                    e.getMessage(),
                    e
            );

            return false;
        }
    }

    /**
     * ============================================================
     * CARD
     * ============================================================
     */
    private PaymentGatewayResponse chargeCard(
            Long loanId,
            PaymentGatewayRequest req,
            Double amount,
            String currency
    ) {

        Map<String, Object> body =
                new HashMap<>();

        body.put(
                "card_number",
                req.getCardNumber()
        );

        body.put(
                "cvv",
                req.getCardCvv()
        );

        body.put(
                "expiry_month",
                req.getCardExpiryMonth()
        );

        body.put(
                "expiry_year",
                req.getCardExpiryYear()
        );

        body.put(
                "currency",
                currency
        );

        body.put(
                "amount",
                amount
        );

        body.put(
                "email",
                safeEmail(req)
        );

        body.put(
                "tx_ref",
                txRef(loanId)
        );

        body.put(
                "redirect_url",
                frontendUrl
                        + "/dashboard/payments/complete"
        );

        return callFlutterwave(
                "/charges?type=card",
                body,
                "CARD"
        );
    }

    /**
     * ============================================================
     * MOBILE MONEY
     * ============================================================
     *
     * Rwanda:
     *
     *   RWF
     *   MTN
     *   Airtel
     *
     * The actual network is supplied by:
     *
     *   req.getNetwork()
     */
    private PaymentGatewayResponse chargeMobileMoney(
            Long loanId,
            PaymentGatewayRequest req,
            Double amount,
            String currency
    ) {

        if (req.getPhoneNumber() == null
                || req.getPhoneNumber().isBlank()) {

            throw new IllegalArgumentException(
                    "Mobile money phone number is required"
            );
        }

        String network =
                req.getNetwork();

        if (network == null
                || network.isBlank()) {

            network = "MTN";
        }

        network =
                network.trim()
                        .toUpperCase();

        /**
         * --------------------------------------------------------
         * Validate Rwanda mobile networks
         * --------------------------------------------------------
         */
        if ("RWF".equalsIgnoreCase(currency)) {

            if (!network.equals("MTN")
                    && !network.equals("AIRTEL")) {

                throw new IllegalArgumentException(
                        "Unsupported Rwanda mobile network: "
                                + network
                                + ". Use MTN or AIRTEL."
                );
            }
        }

        Map<String, Object> body =
                new HashMap<>();

        body.put(
                "amount",
                amount
        );

        body.put(
                "currency",
                currency.toUpperCase()
        );

        body.put(
                "email",
                safeEmail(req)
        );

        body.put(
                "phone_number",
                req.getPhoneNumber()
        );

        body.put(
                "network",
                network
        );

        body.put(
                "tx_ref",
                txRef(loanId)
        );

        /**
         * --------------------------------------------------------
         * Flutterwave mobile-money endpoint
         * --------------------------------------------------------
         */
        String endpoint;

        switch (
                currency.toUpperCase()
        ) {

            case "RWF":

                endpoint =
                        "/charges?type=mobile_money_rwanda";

                break;

            case "KES":

                endpoint =
                        "/charges?type=mpesa";

                break;

            case "GHS":

                endpoint =
                        "/charges?type=mobile_money_ghana";

                break;

            case "UGX":

                endpoint =
                        "/charges?type=mobile_money_uganda";

                break;

            case "XAF":
            case "XOF":

                endpoint =
                        "/charges?type=mobile_money_franco";

                break;

            default:

                throw new IllegalArgumentException(
                        "Mobile money is not configured for currency: "
                                + currency
                );
        }

        log.info(
                "[FLW] Initiating mobile money payment: loan={}, currency={}, network={}, phone={}",
                loanId,
                currency,
                network,
                maskPhone(req.getPhoneNumber())
        );

        return callFlutterwave(
                endpoint,
                body,
                "MOBILE_MONEY"
        );
    }

    /**
     * ============================================================
     * BANK TRANSFER
     * ============================================================
     */
    private PaymentGatewayResponse chargeBankTransfer(
            Long loanId,
            PaymentGatewayRequest req,
            Double amount,
            String currency
    ) {

        Map<String, Object> body =
                new HashMap<>();

        body.put(
                "amount",
                amount
        );

        body.put(
                "currency",
                currency
        );

        body.put(
                "email",
                safeEmail(req)
        );

        body.put(
                "tx_ref",
                txRef(loanId)
        );

        body.put(
                "is_permanent",
                false
        );

        return callFlutterwave(
                "/charges?type=bank_transfer",
                body,
                "BANK_TRANSFER"
        );
    }

    /**
     * ============================================================
     * CALL FLUTTERWAVE
     * ============================================================
     */
    @SuppressWarnings("unchecked")
    private PaymentGatewayResponse callFlutterwave(
            String endpoint,
            Map<String, Object> body,
            String type
    ) {

        try {

            Map<String, Object> response =
                    webClient.post()
                            .uri(endpoint)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + secretKey
                            )
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .block();

            return parseResponse(
                    response,
                    type
            );

        } catch (Exception e) {

            log.error(
                    "[FLW] {} payment failed: {}",
                    type,
                    e.getMessage(),
                    e
            );

            throw new RuntimeException(
                    type
                            + " payment failed: "
                            + e.getMessage(),
                    e
            );
        }
    }

    /**
     * ============================================================
     * PARSE FLUTTERWAVE RESPONSE
     * ============================================================
     */
    @SuppressWarnings("unchecked")
    private PaymentGatewayResponse parseResponse(
            Map<String, Object> response,
            String type
    ) {

        PaymentGatewayResponse result =
                new PaymentGatewayResponse();

        result.setPaymentType(type);

        if (response == null) {

            result.setStatus("failed");
            result.setMessage(
                    "No response received from Flutterwave"
            );

            return result;
        }

        String gatewayStatus =
                String.valueOf(
                        response.get("status")
                );

        /**
         * Flutterwave "success" means that the charge
         * was accepted/created.
         *
         * It does NOT necessarily mean the money has
         * already been received.
         *
         * Therefore mobile money remains PENDING until
         * the webhook confirms the payment.
         */
        if ("success".equalsIgnoreCase(
                gatewayStatus
        )) {

            result.setStatus("pending");

        } else {

            result.setStatus("failed");
        }

        result.setMessage(
                response.get("message") != null
                        ? String.valueOf(
                                response.get("message")
                        )
                        : "No gateway message"
        );

        Map<String, Object> data =
                (Map<String, Object>)
                        response.get("data");

        if (data != null) {

            Object id =
                    data.get("id");

            if (id != null) {

                result.setTransactionId(
                        String.valueOf(id)
                );
            }

            Object flwRef =
                    data.get("flw_ref");

            if (flwRef != null) {

                result.setFlwRef(
                        String.valueOf(flwRef)
                );
            }

            Object redirect =
                    data.get("redirect");

            if (redirect != null) {

                result.setRedirectUrl(
                        String.valueOf(redirect)
                );
            }

            Object amt =
                    data.get("amount");

            if (amt instanceof Number number) {

                result.setAmount(
                        number.doubleValue()
                );
            }

            result.setCurrency(
                    currencyFromResponse(
                            data
                    )
            );
        }

        return result;
    }

    /**
     * ============================================================
     * SIMULATION
     * ============================================================
     */
    private PaymentGatewayResponse simulatedSuccess(
            Double amount,
            String currency,
            String method
    ) {

        PaymentGatewayResponse result =
                new PaymentGatewayResponse();

        result.setStatus("success");

        result.setMessage(
                "Simulated payment successful"
        );

        result.setTransactionId(
                "SIM-"
                        + UUID.randomUUID()
                                .toString()
                                .substring(0, 8)
                                .toUpperCase()
        );

        result.setFlwRef(
                "FLW-SIM-"
                        + System.currentTimeMillis()
        );

        result.setAmount(amount);

        result.setCurrency(currency);

        result.setPaymentType(method);

        return result;
    }

    /**
     * ============================================================
     * TRANSACTION REFERENCE
     * ============================================================
     */
    private String txRef(Long loanId) {

        return "LOAN-"
                + loanId
                + "-"
                + UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase();
    }

    /**
     * ============================================================
     * EXTRACT LOAN ID FROM TRANSACTION REFERENCE
     * ============================================================
     */
    public static Long loanIdFromTxRef(
            String txRef
    ) {

        if (txRef == null
                || txRef.isBlank()) {

            return null;
        }

        try {

            String[] parts =
                    txRef.split("-");

            if (parts.length < 2) {
                return null;
            }

            return Long.valueOf(
                    parts[1]
            );

        } catch (Exception e) {

            return null;
        }
    }

    /**
     * ============================================================
     * EMAIL
     * ============================================================
     *
     * We intentionally do NOT call:
     *
     *     req.getFullName()
     *
     * because PaymentGatewayRequest does not have that field.
     */
    private String safeEmail(
            PaymentGatewayRequest req
    ) {

        if (req.getEmail() != null
                && !req.getEmail().isBlank()) {

            return req.getEmail().trim();
        }

        return "customer@loansaas.com";
    }

    /**
     * ============================================================
     * PHONE MASKING
     * ============================================================
     *
     * Prevents full phone numbers from being written to logs.
     */
    private String maskPhone(
            String phone
    ) {

        if (phone == null
                || phone.length() < 4) {

            return "****";
        }

        int visible =
                Math.min(4, phone.length());

        return "****"
                + phone.substring(
                        phone.length() - visible
                );
    }

    /**
     * ============================================================
     * CURRENCY FROM RESPONSE
     * ============================================================
     */
    private String currencyFromResponse(
            Map<String, Object> data
    ) {

        Object currency =
                data.get("currency");

        return currency != null
                ? String.valueOf(currency)
                : null;
    }
}
