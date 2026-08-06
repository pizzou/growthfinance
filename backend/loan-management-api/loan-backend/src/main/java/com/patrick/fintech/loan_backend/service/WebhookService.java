package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.WebhookEndpoint;
import com.patrick.fintech.loan_backend.repository.WebhookRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final WebhookRepository webhookRepo;

    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate;


    // ================================================================
    // DISPATCH WEBHOOK
    // ================================================================

    /**
     * Dispatches a webhook event to all active endpoints belonging
     * to the specified organization.
     *
     * Example:
     *
     * PAYMENT_MADE
     * LOAN_APPROVED
     * LOAN_DISBURSED
     *
     * The method is asynchronous so payment processing does not have
     * to wait for an external webhook endpoint to respond.
     */
    @Async
    public void dispatch(
            Organization org,
            String eventType,
            Object payload
    ) {

        // ============================================================
        // BASIC VALIDATION
        // ============================================================

        if (org == null) {

            log.warn(
                    "[WEBHOOK] Dispatch aborted: organization is null. event={}",
                    eventType
            );

            return;
        }

        if (
                eventType == null
                        || eventType.isBlank()
        ) {

            log.warn(
                    "[WEBHOOK] Dispatch aborted: event type is empty. organization={}",
                    org.getId()
            );

            return;
        }


        Long organizationId =
                org.getId();


        String normalizedEvent =
                eventType.trim();


        // ============================================================
        // START LOG
        // ============================================================

        log.info(
                "============================================================"
        );

        log.info(
                "[WEBHOOK] DISPATCH STARTED"
        );

        log.info(
                "[WEBHOOK] Event       : {}",
                normalizedEvent
        );

        log.info(
                "[WEBHOOK] Organization: {}",
                organizationId
        );

        log.info(
                "[WEBHOOK] Thread      : {}",
                Thread.currentThread().getName()
        );

        log.info(
                "============================================================"
        );


        // ============================================================
        // FIND ACTIVE ENDPOINTS
        // ============================================================

        List<WebhookEndpoint> endpoints;

        try {

            endpoints =
                    webhookRepo
                            .findByOrganizationAndActiveTrue(org);

        } catch (Exception e) {

            log.error(
                    "[WEBHOOK] Could not load active webhook endpoints. organization={}, event={}",
                    organizationId,
                    normalizedEvent,
                    e
            );

            return;
        }


        // ============================================================
        // ENDPOINT COUNT
        // ============================================================

        int endpointCount =
                endpoints == null
                        ? 0
                        : endpoints.size();


        log.info(
                "[WEBHOOK] Active endpoints found: {}",
                endpointCount
        );


        // ============================================================
        // NO ENDPOINTS
        // ============================================================

        if (
                endpoints == null
                        || endpoints.isEmpty()
        ) {

            log.warn(
                    "[WEBHOOK] NO ACTIVE WEBHOOK ENDPOINTS FOUND"
            );

            log.warn(
                    "[WEBHOOK] organization={}, event={}",
                    organizationId,
                    normalizedEvent
            );

            log.warn(
                    "[WEBHOOK] If a borrower just paid, this means the PAYMENT_MADE event has nowhere to be delivered."
            );

            log.info(
                    "[WEBHOOK] DISPATCH FINISHED - NOTHING TO DELIVER"
            );

            return;
        }


        // ============================================================
        // PROCESS ENDPOINTS
        // ============================================================

        for (
                WebhookEndpoint endpoint :
                endpoints
        ) {

            if (endpoint == null) {

                log.warn(
                        "[WEBHOOK] Encountered null endpoint. Skipping."
                );

                continue;
            }


            Long endpointId =
                    endpoint.getId();


            log.info(
                    "------------------------------------------------------------"
            );

            log.info(
                    "[WEBHOOK] Processing endpoint {}",
                    endpointId
            );

            log.info(
                    "[WEBHOOK] URL         : {}",
                    endpoint.getUrl()
            );

            log.info(
                    "[WEBHOOK] Active      : {}",
                    endpoint.isActive()
            );

            log.info(
                    "[WEBHOOK] Events      : {}",
                    endpoint.getSubscribedEvents()
            );

            log.info(
                    "[WEBHOOK] Failures    : {}",
                    endpoint.getFailureCount()
            );


            // ========================================================
            // ACTIVE CHECK
            // ========================================================

            if (!endpoint.isActive()) {

                log.warn(
                        "[WEBHOOK] Endpoint {} is inactive. Skipping.",
                        endpointId
                );

                continue;
            }


            // ========================================================
            // EVENT SUBSCRIPTION CHECK
            // ========================================================

            List<String> subscribedEvents =
                    endpoint.getSubscribedEvents();


            boolean subscribed =
                    isSubscribed(
                            subscribedEvents,
                            normalizedEvent
                    );


            log.info(
                    "[WEBHOOK] Endpoint {} subscription check: event={}, subscribed={}",
                    endpointId,
                    normalizedEvent,
                    subscribed
            );


            if (!subscribed) {

                log.warn(
                        "[WEBHOOK] Endpoint {} is NOT subscribed to {}. No webhook will be sent.",
                        endpointId,
                        normalizedEvent
                );

                continue;
            }


            // ========================================================
            // URL VALIDATION
            // ========================================================

            String url =
                    endpoint.getUrl();


            if (
                    url == null
                            || url.isBlank()
            ) {

                log.error(
                        "[WEBHOOK] Endpoint {} has an empty URL.",
                        endpointId
                );

                recordFailure(
                        endpoint,
                        "Empty webhook URL"
                );

                continue;
            }


            url =
                    url.trim();


            // ========================================================
            // BUILD DATA
            // ========================================================

            Object webhookData =
                    buildWebhookData(
                            normalizedEvent,
                            payload
                    );


            // ========================================================
            // BUILD BODY
            // ========================================================

            String body;

            try {

                Map<String, Object> webhookPayload =
                        new HashMap<>();


                webhookPayload.put(
                        "event",
                        normalizedEvent
                );


                webhookPayload.put(
                        "timestamp",
                        System.currentTimeMillis()
                );


                webhookPayload.put(
                        "organizationId",
                        organizationId
                );


                webhookPayload.put(
                        "data",
                        webhookData
                );


                body =
                        objectMapper.writeValueAsString(
                                webhookPayload
                        );


                log.info(
                        "[WEBHOOK] Payload created successfully. endpoint={}, event={}, payloadSize={}",
                        endpointId,
                        normalizedEvent,
                        body.length()
                );


            } catch (Exception e) {

                log.error(
                        "[WEBHOOK] Failed to serialize webhook payload. endpoint={}, event={}",
                        endpointId,
                        normalizedEvent,
                        e
                );


                recordFailure(
                        endpoint,
                        "Payload serialization error"
                );

                continue;
            }


            // ========================================================
            // BUILD HEADERS
            // ========================================================

            HttpHeaders headers =
                    new HttpHeaders();


            headers.setContentType(
                    MediaType.APPLICATION_JSON
            );


            headers.set(
                    "X-Webhook-Event",
                    normalizedEvent
            );


            headers.set(
                    "X-Webhook-Organization",
                    String.valueOf(
                            organizationId
                    )
            );


            // ========================================================
            // HMAC SIGNATURE
            // ========================================================

            String secret =
                    endpoint.getSecret();


            if (
                    secret != null
                            && !secret.isBlank()
            ) {

                try {

                    String signature =
                            sign(
                                    body,
                                    secret
                            );


                    headers.set(
                            "X-Webhook-Signature",
                            signature
                    );


                    log.debug(
                            "[WEBHOOK] HMAC signature generated. endpoint={}",
                            endpointId
                    );


                } catch (Exception e) {

                    log.error(
                            "[WEBHOOK] Failed to generate HMAC signature. endpoint={}",
                            endpointId,
                            e
                    );


                    recordFailure(
                            endpoint,
                            "Signature generation error"
                    );

                    continue;
                }

            } else {

                log.warn(
                        "[WEBHOOK] Endpoint {} has no signing secret. Sending unsigned webhook.",
                        endpointId
                );
            }


            // ========================================================
            // SEND REQUEST
            // ========================================================

            try {

                log.info(
                        "[WEBHOOK] SENDING WEBHOOK"
                );

                log.info(
                        "[WEBHOOK] event      = {}",
                        normalizedEvent
                );

                log.info(
                        "[WEBHOOK] endpoint   = {}",
                        endpointId
                );

                log.info(
                        "[WEBHOOK] url        = {}",
                        url
                );


                HttpEntity<String> request =
                        new HttpEntity<>(
                                body,
                                headers
                        );


                ResponseEntity<String> response =
                        restTemplate.exchange(
                                url,
                                HttpMethod.POST,
                                request,
                                String.class
                        );


                int statusCode =
                        response.getStatusCode()
                                .value();


                log.info(
                        "[WEBHOOK] RESPONSE RECEIVED"
                );

                log.info(
                        "[WEBHOOK] endpoint   = {}",
                        endpointId
                );

                log.info(
                        "[WEBHOOK] HTTP status = {}",
                        statusCode
                );


                // ====================================================
                // SUCCESS
                // ====================================================

                if (
                        response.getStatusCode()
                                .is2xxSuccessful()
                ) {

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


                    log.info(
                            "[WEBHOOK] SUCCESS"
                    );

                    log.info(
                            "[WEBHOOK] Event       : {}",
                            normalizedEvent
                    );

                    log.info(
                            "[WEBHOOK] Endpoint    : {}",
                            endpointId
                    );

                    log.info(
                            "[WEBHOOK] HTTP status : {}",
                            statusCode
                    );


                } else {

                    String failureMessage =
                            "HTTP " + statusCode;


                    recordFailure(
                            endpoint,
                            failureMessage
                    );


                    log.error(
                            "[WEBHOOK] DELIVERY FAILED"
                    );

                    log.error(
                            "[WEBHOOK] Event       : {}",
                            normalizedEvent
                    );

                    log.error(
                            "[WEBHOOK] Endpoint    : {}",
                            endpointId
                    );

                    log.error(
                            "[WEBHOOK] HTTP status : {}",
                            statusCode
                    );
                }


            } catch (Exception e) {

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


                if (
                        errorMessage.length() > 250
                ) {

                    errorMessage =
                            errorMessage.substring(
                                    0,
                                    250
                            );
                }


                recordFailure(
                        endpoint,
                        errorMessage
                );


                log.error(
                        "[WEBHOOK] DELIVERY EXCEPTION"
                );

                log.error(
                        "[WEBHOOK] Event       : {}",
                        normalizedEvent
                );

                log.error(
                        "[WEBHOOK] Endpoint    : {}",
                        endpointId
                );

                log.error(
                        "[WEBHOOK] URL         : {}",
                        url
                );

                log.error(
                        "[WEBHOOK] Error       : {}",
                        errorMessage,
                        e
                );
            }
        }


        // ============================================================
        // FINISHED
        // ============================================================

        log.info(
                "============================================================"
        );

        log.info(
                "[WEBHOOK] DISPATCH FINISHED"
        );

        log.info(
                "[WEBHOOK] Event       : {}",
                normalizedEvent
        );

        log.info(
                "[WEBHOOK] Organization: {}",
                organizationId
        );

        log.info(
                "============================================================"
        );
    }


    // ================================================================
    // TEST WEBHOOK
    // ================================================================

    /**
     * Sends a test webhook.
     *
     * NOTE:
     *
     * The endpoint must subscribe to WEBHOOK_TEST if subscription
     * filtering is enabled.
     */
    public void sendTest(
            WebhookEndpoint endpoint
    ) {

        if (endpoint == null) {

            throw new IllegalArgumentException(
                    "Webhook endpoint cannot be null"
            );
        }


        if (!endpoint.isActive()) {

            throw new IllegalStateException(
                    "Webhook endpoint is inactive"
            );
        }


        Organization organization =
                endpoint.getOrganization();


        if (organization == null) {

            throw new IllegalStateException(
                    "Webhook endpoint has no organization"
            );
        }


        Map<String, Object> testData =
                new HashMap<>();


        testData.put(
                "message",
                "Webhook test successful"
        );


        testData.put(
                "webhookId",
                endpoint.getId()
        );


        testData.put(
                "sentAt",
                System.currentTimeMillis()
        );


        log.info(
                "[WEBHOOK] TEST REQUEST"
        );

        log.info(
                "[WEBHOOK] Endpoint={}",
                endpoint.getId()
        );


        dispatch(
                organization,
                "WEBHOOK_TEST",
                testData
        );
    }


    // ================================================================
    // SUBSCRIPTION CHECK
    // ================================================================

    /**
     * Determines whether an endpoint is subscribed to an event.
     *
     * If subscribedEvents is null or empty, the endpoint receives
     * all events.
     */
    private boolean isSubscribed(
            List<String> subscribedEvents,
            String eventType
    ) {

        if (
                subscribedEvents == null
                        || subscribedEvents.isEmpty()
        ) {

            return true;
        }


        if (
                eventType == null
                        || eventType.isBlank()
        ) {

            return false;
        }


        return subscribedEvents
                .stream()
                .filter(
                        event ->
                                event != null
                )
                .map(
                        String::trim
                )
                .anyMatch(
                        event ->
                                event.equalsIgnoreCase(
                                        eventType
                                )
                );
    }


    // ================================================================
    // WEBHOOK DATA
    // ================================================================

    /**
     * Builds a safer webhook payload.
     *
     * PAYMENT_MADE does not need to serialize the complete Loan
     * Hibernate entity.
     */
    private Object buildWebhookData(
            String eventType,
            Object payload
    ) {

        if (
                payload == null
        ) {

            return null;
        }


        if (
                payload instanceof Loan loan
        ) {

            Map<String, Object> data =
                    new HashMap<>();


            data.put(
                    "loanId",
                    loan.getId()
            );


            data.put(
                    "referenceNumber",
                    loan.getReferenceNumber()
            );


            data.put(
                    "status",
                    loan.getStatus() != null
                            ? loan.getStatus().name()
                            : null
            );


            data.put(
                    "currency",
                    loan.getCurrency()
            );


            data.put(
                    "outstandingBalance",
                    loan.getOutstandingBalance()
            );


            data.put(
                    "totalPaid",
                    loan.getTotalPaid()
            );


            data.put(
                    "lastPaymentDate",
                    loan.getLastPaymentDate()
            );


            data.put(
                    "nextDueDate",
                    loan.getNextDueDate()
            );


            data.put(
                    "nextPaymentDate",
                    loan.getNextPaymentDate()
            );


            data.put(
                    "eventType",
                    eventType
            );


            return data;
        }


        return payload;
    }


    // ================================================================
    // RECORD FAILURE
    // ================================================================

    private void recordFailure(
            WebhookEndpoint endpoint,
            String message
    ) {

        if (endpoint == null) {
            return;
        }


        int failures =
                incrementFailureCount(
                        endpoint.getFailureCount()
                );


        endpoint.setLastDeliveryAt(
                LocalDateTime.now()
        );


        String safeMessage =
                message;


        if (
                safeMessage == null
                        || safeMessage.isBlank()
        ) {

            safeMessage =
                    "Unknown webhook delivery error";
        }


        if (
                safeMessage.length() > 250
        ) {

            safeMessage =
                    safeMessage.substring(
                            0,
                            250
                    );
        }


        endpoint.setLastDeliveryStatus(
                "FAILED: "
                        + safeMessage
        );


        endpoint.setFailureCount(
                failures
        );


        if (
                failures >= 10
        ) {

            endpoint.setActive(
                    false
            );


            log.error(
                    "[WEBHOOK] Endpoint {} DISABLED after {} consecutive failures.",
                    endpoint.getId(),
                    failures
            );
        }


        try {

            webhookRepo.save(
                    endpoint
            );

        } catch (Exception e) {

            log.error(
                    "[WEBHOOK] Could not save webhook failure status. endpoint={}",
                    endpoint.getId(),
                    e
            );
        }
    }


    // ================================================================
    // FAILURE COUNT
    // ================================================================

    private int incrementFailureCount(
            Integer current
    ) {

        if (current == null) {
            return 1;
        }


        return current + 1;
    }


    // ================================================================
    // HMAC SHA-256
    // ================================================================

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


        for (
                byte b :
                hash
        ) {

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