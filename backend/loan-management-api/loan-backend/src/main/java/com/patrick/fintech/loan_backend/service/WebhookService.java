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

    // ================================================================
    // DISPATCH WEBHOOK
    // ================================================================

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

        if (eventType == null || eventType.isBlank()) {
            log.warn(
                    "[WEBHOOK] Cannot dispatch webhook with empty event type"
            );
            return;
        }

        Long organizationId = org.getId();

        log.info(
                "[WEBHOOK] Dispatch started. event={}, organization={}",
                eventType,
                organizationId
        );

        // ============================================================
        // FIND ACTIVE ENDPOINTS
        // ============================================================

        List<WebhookEndpoint> endpoints;

        try {

            endpoints =
                    webhookRepo.findByOrganizationAndActiveTrue(org);

        } catch (Exception e) {

            log.error(
                    "[WEBHOOK] Could not load webhook endpoints. organization={}, event={}",
                    organizationId,
                    eventType,
                    e
            );

            return;
        }

        if (endpoints == null || endpoints.isEmpty()) {

            log.info(
                    "[WEBHOOK] No active webhook endpoints. organization={}, event={}",
                    organizationId,
                    eventType
            );

            return;
        }

        log.info(
                "[WEBHOOK] Found {} active endpoint(s). organization={}, event={}",
                endpoints.size(),
                organizationId,
                eventType
        );

        // ============================================================
        // PROCESS ENDPOINTS
        // ============================================================

        for (WebhookEndpoint endpoint : endpoints) {

            if (endpoint == null) {
                continue;
            }

            if (!endpoint.isActive()) {

                log.info(
                        "[WEBHOOK] Endpoint {} inactive. Skipping.",
                        endpoint.getId()
                );

                continue;
            }

            // ========================================================
            // EVENT SUBSCRIPTION
            // ========================================================

            if (!isSubscribed(endpoint, eventType)) {

                log.info(
                        "[WEBHOOK] Endpoint {} is not subscribed to {}. Skipping.",
                        endpoint.getId(),
                        eventType
                );

                continue;
            }

            // ========================================================
            // URL VALIDATION
            // ========================================================

            String url = endpoint.getUrl();

            if (url == null || url.isBlank()) {

                markFailure(
                        endpoint,
                        "Empty webhook URL"
                );

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
                        "[WEBHOOK] Payload serialization failed. endpoint={}, event={}",
                        endpoint.getId(),
                        eventType,
                        e
                );

                markFailure(
                        endpoint,
                        "Payload serialization error: "
                                + shorten(e.getMessage())
                );

                continue;
            }

            // ========================================================
            // HEADERS
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
                    String.valueOf(organizationId)
            );

            headers.set(
                    "X-Webhook-Delivery",
                    "loansaas"
            );

            // ========================================================
            // HMAC
            // ========================================================

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
                            "[WEBHOOK] Signature generation failed. endpoint={}",
                            endpoint.getId(),
                            e
                    );

                    markFailure(
                            endpoint,
                            "Signature generation error"
                    );

                    continue;
                }
            }

            // ========================================================
            // SEND
            // ========================================================

            try {

                log.info(
                        "[WEBHOOK] Sending {} to endpoint={} url={}",
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

                int status =
                        response.getStatusCode().value();

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
                            "[WEBHOOK] SUCCESS. event={}, endpoint={}, HTTP={}",
                            eventType,
                            endpoint.getId(),
                            status
                    );

                } else {

                    markFailure(
                            endpoint,
                            "HTTP " + status
                    );

                    log.warn(
                            "[WEBHOOK] Endpoint returned HTTP {}. event={}, endpoint={}",
                            status,
                            eventType,
                            endpoint.getId()
                    );
                }

            } catch (Exception e) {

                String error =
                        shorten(
                                e.getMessage()
                        );

                if (
                        error == null
                                || error.isBlank()
                ) {
                    error =
                            e.getClass()
                                    .getSimpleName();
                }

                markFailure(
                        endpoint,
                        error
                );

                log.error(
                        "[WEBHOOK] Delivery failed. event={}, endpoint={}, url={}, error={}",
                        eventType,
                        endpoint.getId(),
                        url,
                        error,
                        e
                );
            }
        }

        log.info(
                "[WEBHOOK] Dispatch finished. event={}, organization={}",
                eventType,
                organizationId
        );
    }

    // ================================================================
    // EVENT SUBSCRIPTION
    // ================================================================

    private boolean isSubscribed(
            WebhookEndpoint endpoint,
            String eventType
    ) {

        List<String> subscribedEvents =
                endpoint.getSubscribedEvents();

        /*
         * Empty/null subscription list means all events.
         */
        if (
                subscribedEvents == null
                        || subscribedEvents.isEmpty()
        ) {
            return true;
        }

        String normalizedEvent =
                eventType.trim();

        return subscribedEvents
                .stream()
                .filter(event -> event != null)
                .map(String::trim)
                .anyMatch(
                        event ->
                                event.equalsIgnoreCase(
                                        normalizedEvent
                                )
                );
    }

    // ================================================================
    // FAILURE HANDLING
    // ================================================================

    private void markFailure(
            WebhookEndpoint endpoint,
            String reason
    ) {

        int failures =
                endpoint.getFailureCount() == null
                        ? 1
                        : endpoint.getFailureCount() + 1;

        endpoint.setLastDeliveryAt(
                LocalDateTime.now()
        );

        endpoint.setLastDeliveryStatus(
                "FAILED: " + shorten(reason)
        );

        endpoint.setFailureCount(
                failures
        );

        if (failures >= 10) {

            endpoint.setActive(
                    false
            );

            log.warn(
                    "[WEBHOOK] Endpoint {} disabled after {} failures",
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
    // TEST WEBHOOK
    // ================================================================

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
         * IMPORTANT:
         *
         * sendTest() uses a dedicated event type.
         *
         * If the endpoint does not subscribe to WEBHOOK_TEST,
         * the dispatch method intentionally skips it.
         *
         * For this reason, the dashboard test endpoint should
         * either subscribe to WEBHOOK_TEST or dispatch the test
         * directly.
         */
        dispatch(
                organization,
                "WEBHOOK_TEST",
                testData
        );
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

        SecretKeySpec key =
                new SecretKeySpec(
                        secret.getBytes(
                                StandardCharsets.UTF_8
                        ),
                        "HmacSHA256"
                );

        mac.init(
                key
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

    // ================================================================
    // SHORTEN ERROR
    // ================================================================

    private String shorten(
            String value
    ) {

        if (value == null) {
            return "Unknown error";
        }

        String cleaned =
                value.replace(
                        "\n",
                        " "
                ).replace(
                        "\r",
                        " "
                );

        if (cleaned.length() > 250) {

            return cleaned.substring(
                    0,
                    250
            );
        }

        return cleaned;
    }
}
