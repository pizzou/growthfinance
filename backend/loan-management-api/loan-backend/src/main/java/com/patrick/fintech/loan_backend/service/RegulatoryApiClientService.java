package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.regulatory.ApiClientCreatedResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.ApiClientResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.CreateApiClientRequest;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.RegulatoryApiClient;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.RegulatoryApiClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * Issues and manages the API keys that external regulatory/credit-bureau systems use to
 * call /api/regulatory/external/**. Keys are shown once, in full, at creation time — only
 * a bcrypt hash and a short lookup prefix are ever persisted (see RegulatoryApiClient).
 */
@Service
@RequiredArgsConstructor
public class RegulatoryApiClientService {

    private final RegulatoryApiClientRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public ApiClientCreatedResponse createClient(Organization org, User createdBy, CreateApiClientRequest req) {
        String typePrefix = req.getClientType() == RegulatoryApiClient.ClientType.BNR ? "bnr" : "crb";
        String secret = randomToken(32);
        String rawKey = typePrefix + "_live_" + secret;
        String lookupPrefix = rawKey.substring(0, Math.min(16, rawKey.length()));

        RegulatoryApiClient client = RegulatoryApiClient.builder()
            .organization(org)
            .name(req.getName())
            .clientType(req.getClientType())
            .keyPrefix(lookupPrefix)
            .keyHash(passwordEncoder.encode(rawKey))
            .active(true)
            .contactEmail(req.getContactEmail())
            .description(req.getDescription())
            .expiresAt(req.getExpiresAt())
            .createdBy(createdBy)
            .build();
        client = repository.save(client);

        auditService.log(org, createdBy, "CREATE",
            "RegulatoryApiClient", String.valueOf(client.getId()),
            "Issued " + req.getClientType() + " API key: " + req.getName(),
            null, null, "Regulatory Reporting");

        return ApiClientCreatedResponse.builder()
            .client(ApiClientResponse.from(client))
            .apiKey(rawKey)
            .build();
    }

    @Transactional(readOnly = true)
    public List<ApiClientResponse> listClients(Long orgId) {
        return repository.findByOrganization_IdOrderByCreatedAtDesc(orgId)
            .stream().map(ApiClientResponse::from).toList();
    }

    @Transactional
    public void revoke(Long orgId, Long clientId, User revokedBy, String reason) {
        RegulatoryApiClient client = repository.findById(clientId)
            .filter(c -> c.getOrganization() != null && c.getOrganization().getId().equals(orgId))
            .orElseThrow(() -> new IllegalArgumentException("API client not found"));
        client.setActive(false);
        client.setRevokedAt(LocalDateTime.now());
        client.setRevokedReason(reason);
        repository.save(client);

        auditService.log(client.getOrganization(), revokedBy, "REVOKE",
            "RegulatoryApiClient", String.valueOf(client.getId()),
            "Revoked " + client.getClientType() + " API key: " + client.getName() +
                (reason != null && !reason.isBlank() ? " (" + reason + ")" : ""),
            null, null, "Regulatory Reporting");
    }

    private String randomToken(int numBytes) {
        byte[] bytes = new byte[numBytes];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}