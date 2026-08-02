package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
    name = "regulatory_api_clients",
    indexes = {
        @Index(
            name = "idx_reg_api_client_org",
            columnList = "organization_id"
        ),
        @Index(
            name = "idx_reg_api_client_prefix",
            columnList = "key_prefix"
        )
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegulatoryApiClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "organization_id",
        nullable = false
    )
    private Organization organization;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClientType clientType;

    /**
     * Short lookup prefix.
     *
     * Example:
     * bnr_live_ABC123
     *
     * The complete secret is never stored.
     */
    @Column(
        name = "key_prefix",
        nullable = false,
        unique = true,
        length = 20
    )
    private String keyPrefix;

    /**
     * BCrypt hash of the complete API key.
     */
    @JsonIgnore
    @Column(nullable = false)
    private String keyHash;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    private String contactEmail;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime expiresAt;

    private LocalDateTime lastUsedAt;

    private String lastUsedIp;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    private LocalDateTime revokedAt;

    @Column(columnDefinition = "TEXT")
    private String revokedReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;

        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isCurrentlyValid() {

        if (!Boolean.TRUE.equals(active)) {
            return false;
        }

        if (revokedAt != null) {
            return false;
        }

        return expiresAt == null
            || expiresAt.isAfter(LocalDateTime.now());
    }

    public enum ClientType {
        BNR,
        CREDIT_BUREAU
    }
}