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

@Service
@RequiredArgsConstructor
public class RegulatoryApiClientService {

    private final RegulatoryApiClientRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    private final SecureRandom secureRandom = new SecureRandom();

    
    @Transactional
    public ApiClientCreatedResponse createClient(
            Organization organization,
            User createdBy,
            CreateApiClientRequest request
    ) {

        if (organization == null || organization.getId() == null) {
            throw new IllegalArgumentException(
                    "Organization is required."
            );
        }

        if (createdBy == null) {
            throw new IllegalArgumentException(
                    "Creating user is required."
            );
        }

        if (request == null) {
            throw new IllegalArgumentException(
                    "API client request is required."
            );
        }

        if (request.getName() == null ||
                request.getName().isBlank()) {

            throw new IllegalArgumentException(
                    "API client name is required."
            );
        }

        if (request.getClientType() == null) {
            throw new IllegalArgumentException(
                    "Client type is required."
            );
        }

        /*
         * Validate expiration date.
         */
        if (request.getExpiresAt() != null &&
                request.getExpiresAt().isBefore(LocalDateTime.now())) {

            throw new IllegalArgumentException(
                    "API key expiration date must be in the future."
            );
        }

        String typePrefix =
                request.getClientType()
                        == RegulatoryApiClient.ClientType.BNR
                        ? "bnr"
                        : "crb";

        /*
         * Generate strong random secret.
         */
        String secret = generateRandomToken(32);

        /*
         * Example:
         *
         * bnr_live_xxxxxxxxxxxxxxxxxxxxxxxxx
         *
         * crb_live_xxxxxxxxxxxxxxxxxxxxxxxxx
         */
        String rawApiKey =
                typePrefix +
                "_live_" +
                secret;

        /*
         * Prefix is only used for lookup.
         *
         * IMPORTANT:
         * Do not store the entire API key.
         */
        String keyPrefix =
                rawApiKey.substring(
                        0,
                        Math.min(16, rawApiKey.length())
                );

        /*
         * Make sure the generated prefix is not already used.
         * Extremely unlikely, but this prevents a unique constraint failure.
         */
        while (repository.findByKeyPrefix(keyPrefix).isPresent()) {

            secret = generateRandomToken(32);

            rawApiKey =
                    typePrefix +
                    "_live_" +
                    secret;

            keyPrefix =
                    rawApiKey.substring(
                            0,
                            Math.min(16, rawApiKey.length())
                    );
        }

        /*
         * Hash the complete API key.
         */
        String keyHash =
                passwordEncoder.encode(rawApiKey);

        RegulatoryApiClient client =
                RegulatoryApiClient.builder()
                        .organization(organization)
                        .name(request.getName().trim())
                        .clientType(request.getClientType())
                        .keyPrefix(keyPrefix)
                        .keyHash(keyHash)
                        .active(true)
                        .contactEmail(normalize(
                                request.getContactEmail()))
                        .description(normalize(
                                request.getDescription()))
                        .expiresAt(request.getExpiresAt())
                        .createdBy(createdBy)
                        .build();

        client = repository.save(client);

        /*
         * Audit API-key creation.
         *
         * Never write the raw API key to the audit log.
         */
        auditService.log(
                organization,
                createdBy,
                "CREATE",
                "RegulatoryApiClient",
                String.valueOf(client.getId()),
                "Issued " +
                        client.getClientType() +
                        " API client: " +
                        client.getName() +
                        " (" +
                        client.getKeyPrefix() +
                        "...)",
                null,
                null,
                "Regulatory Reporting"
        );

        /*
         * Raw API key is returned ONLY here.
         *
         * The frontend must display it once and tell the administrator
         * to store it securely.
         */
        return ApiClientCreatedResponse.builder()
                .client(ApiClientResponse.from(client))
                .apiKey(rawApiKey)
                .build();
    }

    /**
     * Returns all regulatory API clients belonging to the current organization.
     */
    @Transactional(readOnly = true)
    public List<ApiClientResponse> listClients(Long organizationId) {

        if (organizationId == null) {
            throw new IllegalArgumentException(
                    "Organization ID is required."
            );
        }

        return repository
                .findByOrganization_IdOrderByCreatedAtDesc(
                        organizationId
                )
                .stream()
                .map(ApiClientResponse::from)
                .toList();
    }

    /**
     * Revoke an API client.
     *
     * Organization ownership is checked here as a second layer of protection,
     * even though the controller already obtains the caller's organization.
     */
    @Transactional
    public void revoke(
            Long organizationId,
            Long clientId,
            User revokedBy,
            String reason
    ) {

        if (organizationId == null) {
            throw new IllegalArgumentException(
                    "Organization ID is required."
            );
        }

        if (clientId == null) {
            throw new IllegalArgumentException(
                    "API client ID is required."
            );
        }

        if (revokedBy == null) {
            throw new IllegalArgumentException(
                    "Revoking user is required."
            );
        }

        RegulatoryApiClient client =
                repository.findById(clientId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "API client not found: " +
                                                clientId
                                )
                        );

        /*
         * Tenant isolation.
         */
        if (client.getOrganization() == null ||
                client.getOrganization().getId() == null ||
                !client.getOrganization()
                        .getId()
                        .equals(organizationId)) {

            throw new SecurityException(
                    "Access denied."
            );
        }

        /*
         * Already revoked.
         */
        if (!client.isCurrentlyValid()) {

            if (client.getRevokedAt() != null) {
                throw new IllegalStateException(
                        "API client is already revoked."
                );
            }

            if (Boolean.FALSE.equals(client.getActive())) {
                throw new IllegalStateException(
                        "API client is already inactive."
                );
            }

            if (client.getExpiresAt() != null &&
                    client.getExpiresAt()
                            .isBefore(LocalDateTime.now())) {

                throw new IllegalStateException(
                        "API client has already expired."
                );
            }
        }

        String normalizedReason =
                normalize(reason);

        client.setActive(false);
        client.setRevokedAt(LocalDateTime.now());
        client.setRevokedReason(
                normalizedReason != null
                        ? normalizedReason
                        : "Revoked by administrator"
        );

        repository.save(client);

        auditService.log(
                client.getOrganization(),
                revokedBy,
                "REVOKE",
                "RegulatoryApiClient",
                String.valueOf(client.getId()),
                "Revoked " +
                        client.getClientType() +
                        " API client: " +
                        client.getName() +
                        " (" +
                        client.getKeyPrefix() +
                        "...)"
                        +
                        (normalizedReason != null
                                ? " Reason: " +
                                normalizedReason
                                : ""),
                null,
                null,
                "Regulatory Reporting"
        );
    }

    /**
     * Generates a URL-safe random token.
     */
    private String generateRandomToken(int numberOfBytes) {

        byte[] bytes =
                new byte[numberOfBytes];

        secureRandom.nextBytes(bytes);

        return Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    /**
     * Converts blank strings to null.
     */
    private String normalize(String value) {

        if (value == null ||
                value.isBlank()) {

            return null;
        }

        return value.trim();
    }
}