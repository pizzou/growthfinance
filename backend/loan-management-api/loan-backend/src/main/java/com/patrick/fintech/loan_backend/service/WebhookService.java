package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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


    
    @Async
    public void dispatch(
            Organization org,
            String eventType,
            Object payload
    ) {

        

        if (org == null) {

            log.warn(
                    "[WEBHOOK] Cannot dispatch {} because organization is null",
                    eventType
            );

            return;
        }


        if (
                eventType == null
                        || eventType.isBlank()
        ) {

            log.warn(
                    "[WEBHOOK] Cannot dispatch webhook with empty event type"
            );

            return;
        }


        // ------------------------------------------------------------
        // Organization ID
        // ------------------------------------------------------------

        Long organizationId =
                org.getId();


        log.info(
                "[WEBHOOK] Dispatching event={} organization={}",
                eventType,
                organizationId
        );


        // ------------------------------------------------------------
        // Find active endpoints
        // ------------------------------------------------------------

        List<WebhookEndpoint> endpoints;

        try {

            endpoints =
                    webhookRepo
                            .findByOrganizationAndActiveTrue(org);

        } catch (Exception e) {

            log.error(
                    "[WEBHOOK] Failed to load webhook endpoints. organization={}, event={}",
                    organizationId,
                    eventType,
                    e
            );

            return;
        }


        // ------------------------------------------------------------
        // No endpoints
        // ------------------------------------------------------------

        if (
                endpoints == null
                        || endpoints.isEmpty()
        ) {

            log.info(
                    "[WEBHOOK] No active webhook endpoints configured. organization={}, event={}",
                    organizationId,
                    eventType
            );

            return;
        }


        // ------------------------------------------------------------
        // Process every endpoint
        // ------------------------------------------------------------

        for (
                WebhookEndpoint endpoint :
                endpoints
        ) {

            if (endpoint == null) {
                continue;
            }


            // ========================================================
            // SAFETY CHECK
            // ========================================================

            if (!endpoint.isActive()) {

                log.info(
                        "[WEBHOOK] Endpoint {} is inactive. Skipping.",
                        endpoint.getId()
                );

                continue;
            }


            // ========================================================
            // EVENT SUBSCRIPTION CHECK
            // ========================================================

            List<String> subscribedEvents =
                    endpoint.getSubscribedEvents();


            if (
                    subscribedEvents != null
                            && !subscribedEvents.isEmpty()
            ) {

                boolean subscribed =
                        subscribedEvents
                                .stream()
                                .anyMatch(
                                        subscribedEvent ->
                                                subscribedEvent != null
                                                        && subscribedEvent
                                                        .trim()
                                                        .equalsIgnoreCase(
                                                                eventType.trim()
                                                        )
                                );


                if (!subscribed) {

                    log.debug(
                            "[WEBHOOK] Endpoint {} is not subscribed to event {}. Skipping.",
                            endpoint.getId(),
                            eventType
                    );

                    continue;
                }
            }


            // ========================================================
            // VALIDATE URL
            // ========================================================

            String url =
                    endpoint.getUrl();


            if (
                    url == null
                            || url.isBlank()
            ) {

                log.warn(
                        "[WEBHOOK] Endpoint {} has empty URL",
                        endpoint.getId()
                );

                endpoint.setLastDeliveryAt(
                        LocalDateTime.now()
                );

                endpoint.setLastDeliveryStatus(
                        "FAILED: Empty webhook URL"
                );

                endpoint.setFailureCount(
                        incrementFailureCount(
                                endpoint.getFailureCount()
                        )
                );


                webhookRepo.save(endpoint);

                continue;
            }


            // ========================================================
            // BUILD PAYLOAD
            // ========================================================

            String body;

            try {

                Map<String, Object> webhookPayload =
                        new HashMap<>();


                webhookPayload.put(
                        "event",
                        eventType
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
                        payload
                );


                body =
                        objectMapper.writeValueAsString(
                                webhookPayload
                        );


            } catch (Exception e) {

                log.error(
                        "[WEBHOOK] Failed to serialize payload. endpoint={}, event={}",
                        endpoint.getId(),
                        eventType,
                        e
                );


                endpoint.setLastDeliveryAt(
                        LocalDateTime.now()
                );


                endpoint.setLastDeliveryStatus(
                        "FAILED: Payload serialization error"
                );


                endpoint.setFailureCount(
                        incrementFailureCount(
                                endpoint.getFailureCount()
                        )
                );


                webhookRepo.save(endpoint);

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
                    eventType
            );


            headers.set(
                    "X-Webhook-Organization",
                    String.valueOf(
                            organizationId
                    )
            );


            // --------------------------------------------------------
            // HMAC SIGNATURE
            // --------------------------------------------------------

            if (
                    endpoint.getSecret() != null
                            && !endpoint.getSecret().isBlank()
            ) {

                try {

                    String signature =
                            sign(
                                    body,
                                    endpoint.getSecret()
                            );


                    headers.set(
                            "X-Webhook-Signature",
                            signature
                    );


                } catch (Exception e) {

                    log.error(
                            "[WEBHOOK] Failed to create HMAC signature. endpoint={}",
                            endpoint.getId(),
                            e
                    );


                    endpoint.setLastDeliveryAt(
                            LocalDateTime.now()
                    );


                    endpoint.setLastDeliveryStatus(
                            "FAILED: Signature generation error"
                    );


                    endpoint.setFailureCount(
                            incrementFailureCount(
                                    endpoint.getFailureCount()
                            )
                    );


                    webhookRepo.save(endpoint);

                    continue;
                }
            }


            // ========================================================
            // SEND WEBHOOK
            // ========================================================

            try {

                log.info(
                        "[WEBHOOK] Sending event={} to endpoint={} url={}",
                        eventType,
                        endpoint.getId(),
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
                            "[WEBHOOK] SUCCESS event={} endpoint={} status={}",
                            eventType,
                            endpoint.getId(),
                            statusCode
                    );

                } else {

                    // ================================================
                    // HTTP ERROR
                    // ================================================

                    int failures =
                            incrementFailureCount(
                                    endpoint.getFailureCount()
                            );


                    endpoint.setLastDeliveryAt(
                            LocalDateTime.now()
                    );


                    endpoint.setLastDeliveryStatus(
                            "FAILED: HTTP "
                                    + statusCode
                    );


                    endpoint.setFailureCount(
                            failures
                    );


                    if (failures >= 10) {

                        endpoint.setActive(
                                false
                        );


                        log.warn(
                                "[WEBHOOK] Endpoint {} disabled after {} consecutive failures",
                                endpoint.getId(),
                                failures
                        );
                    }


                    webhookRepo.save(
                            endpoint
                    );


                    log.warn(
                            "[WEBHOOK] FAILED event={} endpoint={} HTTP status={}",
                            eventType,
                            endpoint.getId(),
                            statusCode
                    );
                }


            } catch (Exception e) {

                // ====================================================
                // DELIVERY EXCEPTION
                // ====================================================

                int failures =
                        incrementFailureCount(
                                endpoint.getFailureCount()
                        );


                endpoint.setLastDeliveryAt(
                        LocalDateTime.now()
                );


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


                /*
                 * Keep dashboard message reasonably short.
                 */
                if (errorMessage.length() > 250) {

                    errorMessage =
                            errorMessage.substring(
                                    0,
                                    250
                            );
                }


                endpoint.setLastDeliveryStatus(
                        "FAILED: "
                                + errorMessage
                );


                endpoint.setFailureCount(
                        failures
                );


                if (failures >= 10) {

                    endpoint.setActive(
                            false
                    );


                    log.warn(
                            "[WEBHOOK] Endpoint {} disabled after {} consecutive failures",
                            endpoint.getId(),
                            failures
                    );
                }


                webhookRepo.save(
                        endpoint
                );


                log.warn(
                        "[WEBHOOK] Delivery failed. event={} endpoint={} url={} error={}",
                        eventType,
                        endpoint.getId(),
                        url,
                        errorMessage
                );
            }
        }


        log.info(
                "[WEBHOOK] Finished dispatch event={} organization={}",
                eventType,
                organizationId
        );
    }


    // ================================================================
    // TEST WEBHOOK
    // ================================================================

    /**
     * Sends a test event to one webhook endpoint.
     *
     * This is useful from the dashboard because it allows you to
     * confirm that:
     *
     * 1. The endpoint URL is reachable.
     * 2. HMAC signing works.
     * 3. The dashboard updates.
     *
     * Event:
     *
     *     WEBHOOK_TEST
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


        /*
         * Dispatch normally.
         *
         * Because dispatch is asynchronous, this method simply
         * triggers the delivery.
         */
        dispatch(
                organization,
                "WEBHOOK_TEST",
                testData
        );
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