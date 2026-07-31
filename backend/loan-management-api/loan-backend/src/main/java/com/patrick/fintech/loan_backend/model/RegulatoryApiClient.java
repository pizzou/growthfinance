package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Credentials for an external regulatory/credit-bureau system (e.g. the National Bank of
 * Rwanda, or a licensed credit bureau) to call the read-only reporting APIs under
 * /api/regulatory/external/**. Deliberately separate from {@link User} — these are
 * machine-to-machine integrations, not staff logins, so they don't need a password, MFA
 * enrollment, or a seat in the org's user list.
 *
 * The raw API key is shown to the admin exactly once at creation time and is never stored —
 * only its bcrypt hash (keyHash) plus a short, non-secret lookup prefix (keyPrefix) used to
 * find the candidate row before doing the (slow, intentionally) bcrypt comparison.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "regulatory_api_clients",
    indexes = {
        @Index(name = "idx_reg_api_client_org", columnList = "organization_id"),
        @Index(name = "idx_reg_api_client_prefix", columnList = "keyPrefix")
    })
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RegulatoryApiClient {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String name;              // e.g. "BNR Production Integration"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClientType clientType;    // BNR or CREDIT_BUREAU

    /** First 12 chars of the raw key — safe to store/search in plaintext, not secret on its own. */
    @Column(nullable = false, unique = true, length = 20)
    private String keyPrefix;

    /** Bcrypt hash of the full raw key. The raw key itself is never persisted. */
    @JsonIgnore
    @Column(nullable = false)
    private String keyHash;

    @Builder.Default
    private Boolean active = true;

    private String contactEmail;      // integration contact at BNR / the bureau, for incident notices
    private String description;

    private LocalDateTime expiresAt;  // null = no expiry
    private LocalDateTime lastUsedAt;
    private String lastUsedIp;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    private LocalDateTime revokedAt;
    private String revokedReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() {
        createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now();
        if (active == null) active = true;
    }

    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public boolean isCurrentlyValid() {
        return Boolean.TRUE.equals(active)
            && revokedAt == null
            && (expiresAt == null || expiresAt.isAfter(LocalDateTime.now()));
    }

    public enum ClientType { BNR, CREDIT_BUREAU }
}