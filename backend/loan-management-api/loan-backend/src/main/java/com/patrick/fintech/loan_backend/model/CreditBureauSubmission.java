package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Persisted batch header for one period's credit bureau reporting — the "what did we send,
 *  when, what came back" record your existing live export/preview endpoints don't keep. */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "credit_bureau_submissions",
    indexes = @Index(name = "idx_cbs_org_period", columnList = "organization_id, reporting_period"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CreditBureauSubmission {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "reporting_period", nullable = false, length = 7)
    private String reportingPeriod;

    /** "INTERNAL_SIMULATED" until real CRB connectivity is configured. */
    @Builder.Default
    private String provider = "INTERNAL_SIMULATED";

    @Builder.Default
    @Column(name = "record_count")
    private Integer recordCount = 0;

    @Column(name = "payload_checksum", length = 64)
    private String payloadChecksum;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "response_reference")
    private String responseReference;

    @Column(name = "response_message", columnDefinition = "TEXT")
    private String responseMessage;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = Status.PENDING;
        if (provider == null || provider.isBlank()) provider = "INTERNAL_SIMULATED";
    }

    public enum Status { PENDING, SUBMITTED, ACCEPTED, REJECTED }
}