package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * A real, priced loan product offered by an organization — the single
 * source of truth for both what's advertised on the org's public website
 * and what rate/limits actually get applied when a loan is created.
 * Replaces the old setup where every org silently shared one hardcoded
 * global rate table regardless of what their site advertised.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "loan_products", indexes = @Index(name = "idx_lp_org", columnList = "organization_id"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanProduct {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String name;                 // e.g. "Personal Loan"
    private String icon;                 // emoji shown on the site
    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Loan.LoanType loanType;      // drives which product is picked up when a loan of this type is created

    @Column(nullable = false)
    private Double interestRate;         // meaning depends on interestRateType — see below, before credit-score adjustment

    /** MONTHLY (e.g. 6/8/10% per month — common for microfinance/salary-advance products)
     *  or ANNUAL (the rate the system assumed everywhere before this field existed). */
    @Builder.Default
    private String interestRateType = "MONTHLY";

    @Column(nullable = false)
    private Double minAmount;
    /** Null means no upper limit ("unlimited") for this product. */
    private Double maxAmount;
    @Column(nullable = false)
    private Integer minTermMonths;
    @Column(nullable = false)
    private Integer maxTermMonths;

    private Double processingFeePercent; // defaults to 2% if not set

    @Builder.Default
    private Boolean active = true;
    private Integer displayOrder;

    /** Comma-separated document type codes (see BorrowerFileService.DOCUMENT_TYPES) that a
     *  borrower must have on file before a loan of this product can be approved/disbursed.
     *  Null/blank falls back to LoanService.DEFAULT_REQUIRED_DOCS — see V23 migration. */
    private String requiredDocumentTypes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now();
        if (active == null) active = true;
        if (processingFeePercent == null) processingFeePercent = 2.0;
        if (interestRateType == null) interestRateType = "MONTHLY";
    }

    /** Parsed, trimmed list — null (not empty) when unset, so callers can distinguish
     *  "this product has no requirement configured, use the default" from "explicitly none". */
    @JsonIgnore
    public java.util.List<String> getRequiredDocumentTypesList() {
        if (requiredDocumentTypes == null || requiredDocumentTypes.isBlank()) return null;
        return java.util.Arrays.stream(requiredDocumentTypes.split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
