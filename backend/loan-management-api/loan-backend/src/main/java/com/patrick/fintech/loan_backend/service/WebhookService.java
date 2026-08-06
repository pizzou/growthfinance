package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.WebhookDelivery;
import com.patrick.fintech.loan_backend.model.WebhookEndpoint;
import com.patrick.fintech.loan_backend.repository.WebhookDeliveryRepository;
import com.patrick.fintech.loan_backend.repository.WebhookRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * WebhookService
 *
 * Responsible for delivering webhook events to registered external
 * endpoints.
 *
 * Every delivery is also recorded in webhook_deliveries so that the
 * webhook dashboard can show:
 *
 * - event type
 * - endpoint
 * - payload
 * - success/failure
 * - HTTP status
 * - response body
 * - error message
 * - delivery timestamp
 * - attempt count
 *
 * Webhook payloads are signed using HMAC-SHA256 when the endpoint
 * has a secret configured.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final WebhookRepository webhookRepo;

    private final WebhookDeliveryRepository webhookDeliveryRepo;

    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate;


    // ============================================================
    // DISPATCH WEBHOOK
    // ============================================================

    /**
     * Dispatch an event to every active webhook endpoint belonging
     * to the organization.
     *
     * Example:
     *
     * PAYMENT_MADE
     * LOAN_APPROVED
     * LOAN_REJECTED
     *
     * This method runs asynchronously so webhook delivery does not
     * block the main payment transaction.
     */
    @Async
    public void dispatch(
            Organization org,
            String eventType,
            Object payload
    ) {

        if (org == null) {

            log.warn(
                    "[WEBHOOK] Cannot dispatch event because organization is null. event={}",
                    eventType
            );

            return;
        }


        if (
                eventType == null
                        || eventType.isBlank()
        ) {

            log.warn(
                    "[WEBHOOK] Cannot dispatch webhook because event type is empty."
            );

            return;
        }


        // ========================================================
        // FIND ACTIVE ENDPOINTS
        // ========================================================

        List<WebhookEndpoint> endpoints;

        try {

            endpoints =
                    webhookRepo
                            .findByOrganizationAndActiveTrue(org);

        } catch (Exception e) {

            log.error(
                    "[WEBHOOK] Failed to load webhook endpoints for organization {}",
                    org.getId(),
                    e
            );

            return;
        }


        if (
                endpoints == null
                        || endpoints.isEmpty()
        ) {

            log.info(
                    "[WEBHOOK] No active webhook endpoints for organization {}. event={}",
                    org.getId(),
                    eventType
            );

            return;
        }


        // ========================================================
        // PROCESS EACH ENDPOINT
        // ========================================================

        for (
                WebhookEndpoint endpoint :
                endpoints
        ) {

            if (endpoint == null) {
                continue;
            }


            // ----------------------------------------------------
            // CHECK SUBSCRIBED EVENTS
            // ----------------------------------------------------

            if (
                    endpoint.getSubscribedEvents() != null
                            && !endpoint
                            .getSubscribedEvents()
                            .isEmpty()
                            && !endpoint
                            .getSubscribedEvents()
                            .contains(eventType)
            ) {

                log.debug(
                        "[WEBHOOK] Endpoint {} is not subscribed to event {}",
                        endpoint.getId(),
                        eventType
                );

                continue;
            }


            // ----------------------------------------------------
            // CHECK URL
            // ----------------------------------------------------

            if (
                    endpoint.getUrl() == null
                            || endpoint.getUrl().isBlank()
            ) {

                log.warn(
                        "[WEBHOOK] Endpoint {} has no URL. Skipping.",
                        endpoint.getId()
                );

                createFailedDelivery(
                        endpoint,
                        org,
                        eventType,
                        null,
                        null,
                        "Webhook endpoint URL is empty."
                );

                continue;
            }


            // ----------------------------------------------------
            // DELIVER
            // ----------------------------------------------------

            deliverToEndpoint(
                    endpoint,
                    org,
                    eventType,
                    payload
            );
        }
    }


    // ============================================================
    // DELIVER TO ENDPOINT
    // ============================================================

    /**
     * Sends one webhook request and records the delivery result.
     */
    private void deliverToEndpoint(
            WebhookEndpoint endpoint,
            Organization org,
            String eventType,
            Object payload
    ) {

        String body = null;


        try {

            // ====================================================
            // BUILD PAYLOAD
            // ====================================================

            body =
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "event",
                                    eventType,

                                    "timestamp",
                                    System.currentTimeMillis(),

                                    "organizationId",
                                    org.getId(),

                                    "data",
                                    payload
                            )
                    );


            log.info(
                    "[WEBHOOK] Sending event={} to endpoint={} url={}",
                    eventType,
                    endpoint.getId(),
                    endpoint.getUrl()
            );


            // ====================================================
            // HTTP HEADERS
            // ====================================================

            HttpHeaders headers =
                    new HttpHeaders();

            headers.setContentType(
                    MediaType.APPLICATION_JSON
            );


            headers.set(
                    "X-Webhook-Event",
                    eventType
            );


            headers.set(
                    "X-Webhook-Timestamp",
                    String.valueOf(
                            System.currentTimeMillis()
                    )
            );


            // ----------------------------------------------------
            // HMAC SIGNATURE
            // ----------------------------------------------------

            if (
                    endpoint.getSecret() != null
                            && !endpoint
                            .getSecret()
                            .isBlank()
            ) {

                String signature =
                        sign(
                                body,
                                endpoint.getSecret()
                        );

                headers.set(
                        "X-Webhook-Signature",
                        signature
                );
            }


            // ====================================================
            // SEND REQUEST
            // ====================================================

            HttpEntity<String> request =
                    new HttpEntity<>(
                            body,
                            headers
                    );


            ResponseEntity<String> response =
                    restTemplate.exchange(
                            endpoint.getUrl(),
                            HttpMethod.POST,
                            request,
                            String.class
                    );


            // ====================================================
            // RESPONSE
            // ====================================================

            HttpStatusCode statusCode =
                    response.getStatusCode();

            int httpStatus =
                    statusCode.value();

            String responseBody =
                    response.getBody();


            boolean successful =
                    statusCode.is2xxSuccessful();


            if (successful) {

                // =================================================
                // SUCCESS
                // =================================================

                endpoint.setLastDeliveryAt(
                        LocalDateTime.now()
                );

                endpoint.setLastDeliveryStatus(
                        "SUCCESS"
                );

                endpoint.setFailureCount(
                        0
                );


                webhookRepo.save(
                        endpoint
                );


                createSuccessfulDelivery(
                        endpoint,
                        org,
                        eventType,
                        body,
                        httpStatus,
                        responseBody
                );


                log.info(
                        "[WEBHOOK] Successfully delivered event={} endpoint={} HTTP={}",
                        eventType,
                        endpoint.getId(),
                        httpStatus
                );

            } else {

                // =================================================
                // HTTP FAILURE
                // =================================================

                String failureMessage =
                        "Webhook endpoint returned HTTP "
                                + httpStatus;


                handleFailedDelivery(
                        endpoint,
                        org,
                        eventType,
                        body,
                        httpStatus,
                        responseBody,
                        failureMessage
                );
            }


        } catch (Exception e) {

            // ====================================================
            // DELIVERY EXCEPTION
            // ====================================================

            String errorMessage =
                    e.getMessage();

            if (
                    errorMessage == null
                            || errorMessage.isBlank()
            ) {

                errorMessage =
                        e.getClass()
                                .getSimpleName();
            }


            handleFailedDelivery(
                    endpoint,
                    org,
                    eventType,
                    body,
                    null,
                    null,
                    errorMessage
            );
        }
    }


    // ============================================================
    // SUCCESS DELIVERY RECORD
    // ============================================================

    /**
     * Creates a persistent successful webhook delivery record.
     */
    @Transactional
    protected void createSuccessfulDelivery(
            WebhookEndpoint endpoint,
            Organization org,
            String eventType,
            String body,
            Integer httpStatus,
            String responseBody
    ) {

        try {

            WebhookDelivery delivery =
                    WebhookDelivery.builder()
                            .webhookEndpoint(endpoint)
                            .organization(org)
                            .eventType(eventType)
                            .payload(body)
                            .endpointUrl(endpoint.getUrl())
                            .status("SUCCESS")
                            .httpStatus(httpStatus)
                            .responseBody(responseBody)
                            .errorMessage(null)
                            .attemptCount(1)
                            .createdAt(LocalDateTime.now())
                            .deliveredAt(LocalDateTime.now())
                            .build();


            webhookDeliveryRepo.save(
                    delivery
            );


            log.debug(
                    "[WEBHOOK] Delivery history saved. deliveryId={}, event={}, endpoint={}",
                    delivery.getId(),
                    eventType,
                    endpoint.getId()
            );

        } catch (Exception e) {

            /*
             * IMPORTANT:
             *
             * A failure to save webhook history must NOT undo an
             * already successful external webhook delivery.
             */
            log.error(
                    "[WEBHOOK] External delivery succeeded but delivery history could not be saved. endpoint={}, event={}",
                    endpoint.getId(),
                    eventType,
                    e
            );
        }
    }


    // ============================================================
    // FAILED DELIVERY
    // ============================================================

    /**
     * Handles a failed webhook delivery.
     *
     * The endpoint failure counter is increased.
     *
     * After 10 consecutive failures, the endpoint is disabled.
     */
    private void handleFailedDelivery(
            WebhookEndpoint endpoint,
            Organization org,
            String eventType,
            String body,
            Integer httpStatus,
            String responseBody,
            String errorMessage
    ) {

        try {

            // ====================================================
            // FAILURE COUNT
            // ====================================================

            int failureCount =
                    endpoint.getFailureCount() == null
                            ? 0
                            : endpoint.getFailureCount();


            failureCount++;


            endpoint.setFailureCount(
                    failureCount
            );


            endpoint.setLastDeliveryAt(
                    LocalDateTime.now()
            );


            endpoint.setLastDeliveryStatus(
                    "FAILED"
            );


            // ====================================================
            // DISABLE AFTER 10 FAILURES
            // ====================================================

            if (failureCount >= 10) {

                endpoint.setActive(
                        false
                );


                log.warn(
                        "[WEBHOOK] Endpoint {} disabled after {} consecutive failures.",
                        endpoint.getId(),
                        failureCount
                );
            }


            webhookRepo.save(
                    endpoint
            );


            // ====================================================
            // SAVE DELIVERY HISTORY
            // ====================================================

            WebhookDelivery delivery =
                    WebhookDelivery.builder()
                            .webhookEndpoint(endpoint)
                            .organization(org)
                            .eventType(eventType)
                            .payload(body)
                            .endpointUrl(endpoint.getUrl())
                            .status("FAILED")
                            .httpStatus(httpStatus)
                            .responseBody(responseBody)
                            .errorMessage(errorMessage)
                            .attemptCount(1)
                            .createdAt(LocalDateTime.now())
                            .deliveredAt(null)
                            .build();


            webhookDeliveryRepo.save(
                    delivery
            );


            log.warn(
                    "[WEBHOOK] Delivery failed. endpoint={}, event={}, HTTP={}, error={}",
                    endpoint.getId(),
                    eventType,
                    httpStatus,
                    errorMessage
            );


        } catch (Exception e) {

            log.error(
                    "[WEBHOOK] Failed to save webhook failure history. endpoint={}, event={}",
                    endpoint.getId(),
                    eventType,
                    e
            );
        }
    }


    // ============================================================
    // FAILED DELIVERY WITHOUT PAYLOAD
    // ============================================================

    /**
     * Used when an endpoint is invalid before an HTTP request
     * can be attempted.
     */
    private void createFailedDelivery(
            WebhookEndpoint endpoint,
            Organization org,
            String eventType,
            String body,
            Integer httpStatus,
            String errorMessage
    ) {

        try {

            WebhookDelivery delivery =
                    WebhookDelivery.builder()
                            .webhookEndpoint(endpoint)
                            .organization(org)
                            .eventType(eventType)
                            .payload(body)
                            .endpointUrl(endpoint.getUrl())
                            .status("FAILED")
                            .httpStatus(httpStatus)
                            .responseBody(null)
                            .errorMessage(errorMessage)
                            .attemptCount(0)
                            .createdAt(LocalDateTime.now())
                            .deliveredAt(null)
                            .build();


            webhookDeliveryRepo.save(
                    delivery
            );

        } catch (Exception e) {

            log.error(
                    "[WEBHOOK] Failed to create failed delivery history.",
                    e
            );
        }
    }


    // ============================================================
    // HMAC SHA-256
    // ============================================================

    /**
     * Creates an HMAC-SHA256 signature.
     *
     * Result format:
     *
     * sha256=abcdef...
     */
    private String sign(
            String payload,
            String secret
    ) throws Exception {

        Mac mac =
                Mac.getInstance(
                        "HmacSHA256"
                );


        SecretKeySpec secretKey =
                new SecretKeySpec(
                        secret.getBytes(
                                StandardCharsets.UTF_8
                        ),
                        "HmacSHA256"
                );


        mac.init(
                secretKey
        );


        byte[] hash =
                mac.doFinal(
                        payload.getBytes(
                                StandardCharsets.UTF_8
                        )
                );


        StringBuilder result =
                new StringBuilder(
                        "sha256="
                );


        for (byte b : hash) {

            result.append(
                    String.format(
                            "%02x",
                            b
                    )
            );
        }


        return result.toString();
    }
}