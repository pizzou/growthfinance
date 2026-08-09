package com.patrick.fintech.loan_backend.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @Column(nullable = false, precision = 19, scale = 9)
    @JsonProperty("interestRate")
    private BigDecimal interestRate;         // meaning depends on interestRateType — see below, before credit-score adjustment

    /** MONTHLY (e.g. 6/8/10% per month — common for microfinance/salary-advance products)
     *  or ANNUAL (the rate the system assumed everywhere before this field existed). */
    @Builder.Default
    private String interestRateType = "MONTHLY";

    @Column(nullable = false, precision = 19, scale = 6)
    @JsonProperty("minAmount")
    private BigDecimal minAmount;
    /** Null means no upper limit ("unlimited") for this product. */
    @Column(precision = 19, scale = 6)
    @JsonProperty("maxAmount")
    private BigDecimal maxAmount;
    @Column(nullable = false)
    private Integer minTermMonths;
    @Column(nullable = false, precision = 19, scale = 6)
    private Integer maxTermMonths;
    @JsonProperty("processingFeePercent")

    private BigDecimal processingFeePercent; // defaults to 2% if not set

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
        if (processingFeePercent == null) processingFeePercent = BigDecimal.valueOf(2.0);
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
    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getInterestRateDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getInterestRate() {
        return interestRate == null ? null : interestRate.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getInterestRateDecimal() {
        return interestRate;
    }

    @Deprecated
    public void setInterestRate(Double value) {
        this.interestRate = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setInterestRate(BigDecimal value) {
        this.interestRate = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getMinAmountDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getMinAmount() {
        return minAmount == null ? null : minAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getMinAmountDecimal() {
        return minAmount;
    }

    @Deprecated
    public void setMinAmount(Double value) {
        this.minAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setMinAmount(BigDecimal value) {
        this.minAmount = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getMaxAmountDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getMaxAmount() {
        return maxAmount == null ? null : maxAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getMaxAmountDecimal() {
        return maxAmount;
    }

    @Deprecated
    public void setMaxAmount(Double value) {
        this.maxAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setMaxAmount(BigDecimal value) {
        this.maxAmount = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getProcessingFeePercentDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getProcessingFeePercent() {
        return processingFeePercent == null ? null : processingFeePercent.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getProcessingFeePercentDecimal() {
        return processingFeePercent;
    }

    @Deprecated
    public void setProcessingFeePercent(Double value) {
        this.processingFeePercent = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setProcessingFeePercent(BigDecimal value) {
        this.processingFeePercent = value;
    }

    /** Backward-compatible builder overloads for legacy Double callers.
     *  Financial state is stored as BigDecimal.
     */
    public static class LoanProductBuilder {
        private BigDecimal interestRate;
        private BigDecimal maxAmount;
        private BigDecimal minAmount;
        private BigDecimal processingFeePercent;


        public LoanProductBuilder interestRate(Double value) {
            this.interestRate = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanProductBuilder minAmount(Double value) {
            this.minAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanProductBuilder maxAmount(Double value) {
            this.maxAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanProductBuilder processingFeePercent(Double value) {
            this.processingFeePercent = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }        public LoanProductBuilder interestRate(BigDecimal value) {
            this.interestRate = value;
            return this;
        }
        public LoanProductBuilder minAmount(BigDecimal value) {
            this.minAmount = value;
            return this;
        }
        public LoanProductBuilder maxAmount(BigDecimal value) {
            this.maxAmount = value;
            return this;
        }
        public LoanProductBuilder processingFeePercent(BigDecimal value) {
            this.processingFeePercent = value;
            return this;
        }
    }

}
