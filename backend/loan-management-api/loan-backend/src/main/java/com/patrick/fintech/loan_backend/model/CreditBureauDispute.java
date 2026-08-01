package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A borrower's exercise of their right (Law No. 73/2018) to challenge or correct information
 *  reported about them to the CRB. */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "credit_bureau_disputes",
    indexes = {
        @Index(name = "idx_cbd_org", columnList = "organization_id"),
        @Index(name = "idx_cbd_borrower", columnList = "borrower_id"),
        @Index(name = "idx_cbd_status", columnList = "status")
    })
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CreditBureauDispute {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "borrower_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Borrower borrower;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "loan_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Loan loan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_record_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private CreditBureauSubmissionRecord submissionRecord;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "disputed_field")
    private String disputedField;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "requested_value")
    private String requestedValue;

    @Column(name = "supporting_document_url")
    private String supportingDocumentUrl;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (submittedAt == null) submittedAt = LocalDateTime.now();
        if (status == null) status = Status.OPEN;
    }

    public enum Status { OPEN, UNDER_REVIEW, ACCEPTED, REJECTED, CORRECTED, ESCALATED }
}