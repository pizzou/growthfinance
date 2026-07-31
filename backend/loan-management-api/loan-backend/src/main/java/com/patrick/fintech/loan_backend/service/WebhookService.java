package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.WebhookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Delivers real-time webhook events to registered endpoints.
 * Uses HMAC-SHA256 signing so receivers can verify authenticity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final WebhookRepository webhookRepo;
    private final ObjectMapper      objectMapper;
    private final RestTemplate      restTemplate;

    @Async
    public void dispatch(Organization org, String eventType, Object payload) {
        List<WebhookEndpoint> endpoints = webhookRepo.findByOrganizationAndActiveTrue(org);
        for (WebhookEndpoint ep : endpoints) {
            if (ep.getSubscribedEvents() != null && !ep.getSubscribedEvents().contains(eventType)) continue;
            try {
                String body = objectMapper.writeValueAsString(Map.of(
                    "event", eventType,
                    "timestamp", System.currentTimeMillis(),
                    "organizationId", org.getId(),
                    "data", payload
                ));
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                if (ep.getSecret() != null)
                    headers.set("X-Webhook-Signature", sign(body, ep.getSecret()));
                headers.set("X-Webhook-Event", eventType);

                restTemplate.exchange(ep.getUrl(), HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);

                ep.setLastDeliveryAt(java.time.LocalDateTime.now());
                ep.setLastDeliveryStatus("SUCCESS");
                ep.setFailureCount(0);
            } catch (Exception e) {
                log.warn("Webhook delivery failed to {}: {}", ep.getUrl(), e.getMessage());
                ep.setLastDeliveryStatus("FAILED: " + e.getMessage());
                ep.setFailureCount(ep.getFailureCount() == null ? 1 : ep.getFailureCount() + 1);
                if (ep.getFailureCount() >= 10) {
                    ep.setActive(false);
                    log.warn("Webhook {} disabled after 10 consecutive failures", ep.getId());
                }
            }
            webhookRepo.save(ep);
        }
    }

    private String sign(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder("sha256=");
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
