package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "credit_bureau_checks",
    indexes = {
        @Index(name = "idx_cbc_borrower", columnList = "borrower_id"),
        @Index(name = "idx_cbc_org", columnList = "organization_id")
    })
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CreditBureauCheck {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_id", nullable = false)
    private Borrower borrower;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(unique = true)
    private String reference;         // e.g. CRB-RW-2026-000123

    private String provider;          // TRANSUNION_RW, CRB_AFRICA, INTERNAL_SIMULATED
    private String nationalIdChecked;

    @Enumerated(EnumType.STRING)
    private CheckStatus status;

    private Integer creditScore;      // typically 300-850 scale
    private String  riskGrade;        // EXCELLENT, GOOD, FAIR, POOR, VERY_POOR

    private Integer activeFacilities;
    private Integer delinquentAccounts;
    private Double  totalOutstandingDebt;
    private Double  totalMonthlyObligations;
    private Boolean hasDefaultHistory;
    private Boolean hasActiveListing;   // currently blacklisted/negatively listed
    private String  listingReason;

    @Column(columnDefinition = "TEXT")
    private String rawResponse;         // JSON snapshot from provider (or simulator)

    private String requestedBy;
    private String failureReason;

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;    // bureau reports typically valid ~90 days

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (expiresAt == null) expiresAt = createdAt.plusDays(90);
        if (status == null) status = CheckStatus.PENDING;
    }

    public boolean isExpired() { return expiresAt != null && expiresAt.isBefore(LocalDateTime.now()); }

    public enum CheckStatus { PENDING, COMPLETED, FAILED, NO_RECORD_FOUND }
}
