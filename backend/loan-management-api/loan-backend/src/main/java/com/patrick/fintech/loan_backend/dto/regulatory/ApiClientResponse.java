package com.patrick.fintech.loan_backend.dto.regulatory;

import com.patrick.fintech.loan_backend.model.RegulatoryApiClient;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** Safe view of a RegulatoryApiClient for listing in the admin UI — never includes the key. */
@Data @Builder
public class ApiClientResponse {
    private Long id;
    private String name;
    private RegulatoryApiClient.ClientType clientType;
    private String keyPrefix;
    private Boolean active;
    private String contactEmail;
    private String description;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private String lastUsedIp;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;

    public static ApiClientResponse from(RegulatoryApiClient c) {
        return ApiClientResponse.builder()
            .id(c.getId()).name(c.getName()).clientType(c.getClientType())
            .keyPrefix(c.getKeyPrefix()).active(c.getActive())
            .contactEmail(c.getContactEmail()).description(c.getDescription())
            .expiresAt(c.getExpiresAt()).lastUsedAt(c.getLastUsedAt()).lastUsedIp(c.getLastUsedIp())
            .revokedAt(c.getRevokedAt()).createdAt(c.getCreatedAt())
            .build();
    }
}