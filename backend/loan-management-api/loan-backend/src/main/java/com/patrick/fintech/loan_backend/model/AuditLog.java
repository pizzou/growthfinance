package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "audit_logs",
    indexes = {
        @Index(name = "idx_audit_org", columnList = "organization_id"),
        @Index(name = "idx_audit_entity", columnList = "entity_type,entity_id")
    })
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String action;        // LOAN_APPROVED, PAYMENT_RECORDED, etc.
    private String entityType;    // LOAN, PAYMENT, BORROWER, USER
    private String entityId;
    private String description;

    /** Human-readable module/page the action belongs to, e.g. "Loans", "Payments", "KYC & Documents".
     *  Auto-derived from entityType (see AuditPersistenceService#deriveModule) so existing call
     *  sites never had to change, but callers can also pass one explicitly for finer granularity. */
    private String module;

    @Column(columnDefinition = "TEXT")
    private String beforeValue;

    @Column(columnDefinition = "TEXT")
    private String afterValue;

    private String ipAddress;
    private String userAgent;
    private String operatingSystem;   // parsed from user-agent, e.g. "Windows 10", "macOS", "Android"
    private String browser;           // parsed from user-agent, e.g. "Chrome", "Safari"
    private String location;          // best-effort geolocation from IP, e.g. "Kigali, Rwanda"
    private LocalDateTime timestamp;

    // ---- Tamper-evidence chain ----
    // Each entry's hash covers its own content plus the previous entry's hash,
    // forming a chain: altering or deleting any historical row breaks every
    // hash after it, detectable by AuditController's /verify endpoint. This is
    // best-effort integrity evidence, not a substitute for database backups,
    // access controls, or an actual blockchain — but it means nobody can quietly
    // edit an old audit entry without it being provably detectable afterward.
    private String previousHash;
    private String entryHash;

    @PrePersist protected void onCreate() { timestamp = LocalDateTime.now(); }
}
