package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class WebhookController {

    private final WebhookRepository webhookRepo;
    private final UserRepository    userRepo;
    private final AuditService      auditService;

    @GetMapping
    public ResponseEntity<?> list(Authentication auth) {
        User user = currentUser(auth);
        return ResponseEntity.ok(webhookRepo.findByOrganization(user.getOrganization()));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody WebhookEndpoint body, Authentication auth) {
        User user = currentUser(auth);
        body.setOrganization(user.getOrganization());
        body.setSecret(UUID.randomUUID().toString().replace("-",""));
        WebhookEndpoint saved = webhookRepo.save(body);
        auditService.log(user.getOrganization(), user, "WEBHOOK_CREATED", "WEBHOOK",
            String.valueOf(saved.getId()), "Created webhook endpoint " + saved.getUrl(),
            null, null, "Webhooks & Integrations");
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        User user = currentUser(auth);
        WebhookEndpoint ep = webhookRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Webhook not found"));
        if (!ep.getOrganization().getId().equals(user.getOrganization().getId()))
            throw new RuntimeException("Access denied");
        webhookRepo.delete(ep);
        auditService.log(user.getOrganization(), user, "WEBHOOK_DELETED", "WEBHOOK",
            String.valueOf(id), "Deleted webhook endpoint " + ep.getUrl(), null, null, "Webhooks & Integrations");
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    private User currentUser(Authentication auth) {
        return userRepo.findByEmail(auth.getName())
            .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
