
package com.patrick.fintech.loan_backend.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
    name = "credit_bureau_checks",
    indexes = {
        @Index(
            name = "idx_cbc_borrower",
            columnList = "borrower_id"
        ),
        @Index(
            name = "idx_cbc_org",
            columnList = "organization_id"
        ),
        @Index(
            name = "idx_cbc_org_borrower",
            columnList = "organization_id, borrower_id"
        ),
        @Index(
            name = "idx_cbc_reference",
            columnList = "reference"
        ),
        @Index(
            name = "idx_cbc_status",
            columnList = "status"
        ),
        @Index(
            name = "idx_cbc_created_at",
            columnList = "createdAt"
        )
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditBureauCheck {

    // ============================================================
    // PRIMARY KEY
    // ============================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // ============================================================
    // BORROWER
    // ============================================================

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "borrower_id",
        nullable = false
    )
    private Borrower borrower;


    // ============================================================
    // ORGANIZATION / TENANT
    // ============================================================

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "organization_id",
        nullable = false
    )
    private Organization organization;


    // ============================================================
    // BUREAU REFERENCE
    // ============================================================

    /**
     * Internal/provider reference.
     *
     * Example:
     * CRB-RW-2026-000123
     */
    @Column(
        name = "reference",
        unique = true,
        length = 100
    )
    private String reference;


    // ============================================================
    // PROVIDER
    // ============================================================

    /**
     * Examples:
     *
     * TRANSUNION_RW
     * CRB_AFRICA
     * INTERNAL_SIMULATED
     */
    @Column(
        name = "provider",
        length = 100
    )
    private String provider;


    // ============================================================
    // IDENTIFICATION
    // ============================================================

    /**
     * National ID used when requesting the bureau check.
     *
     * Stored separately from borrower.nationalId so the exact
     * identifier submitted to the provider can be retained.
     */
    @Column(
        name = "national_id_checked",
        length = 100
    )
    private String nationalIdChecked;


    // ============================================================
    // CHECK STATUS
    // ============================================================

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 30
    )
    @Builder.Default
    private CheckStatus status = CheckStatus.PENDING;


    // ============================================================
    // CREDIT SCORE
    // ============================================================

    /**
     * Typical bureau score range may be 300-850,
     * depending on provider.
     */
    @Column(name = "credit_score")
    private Integer creditScore;


    /**
     * Examples:
     *
     * EXCELLENT
     * GOOD
     * FAIR
     * POOR
     * VERY_POOR
     */
    @Column(
        name = "risk_grade",
        length = 30
    )
    private String riskGrade;


    // ============================================================
    // FACILITIES / DEBT
    // ============================================================

    /**
     * Number of currently active credit facilities.
     */
    @Column(name = "active_facilities")
    private Integer activeFacilities;


    /**
     * Number of delinquent credit accounts.
     */
    @Column(name = "delinquent_accounts")
    private Integer delinquentAccounts;


    /**
     * Keep Double here.
     *
     * Do NOT change this to BigDecimal because the rest of the
     * existing financial system currently uses Double.
     */
    @Column(name = "total_outstanding_debt", precision = 19, scale = 6)
    @JsonProperty("totalOutstandingDebt")
    private BigDecimal totalOutstandingDebt;


    /**
     * Total monthly obligations reported by the bureau.
     *
     * Keep Double for compatibility with the existing system.
     */
    @Column(name = "total_monthly_obligations", precision = 19, scale = 6)
    @JsonProperty("totalMonthlyObligations")
    private BigDecimal totalMonthlyObligations;


    // ============================================================
    // DEFAULT / NEGATIVE LISTING
    // ============================================================

    /**
     * Whether the borrower has historical defaults.
     */
    @Column(name = "has_default_history")
    private Boolean hasDefaultHistory;


    /**
     * Whether the borrower currently has an active
     * negative/blacklist listing.
     */
    @Column(name = "has_active_listing")
    private Boolean hasActiveListing;


    /**
     * Explanation supplied by the bureau/provider.
     */
    @Column(
        name = "listing_reason",
        length = 500
    )
    private String listingReason;


    // ============================================================
    // RAW PROVIDER RESPONSE
    // ============================================================

    /**
     * JSON/XML/raw response returned by the provider.
     *
     * Stored for:
     * - audit
     * - troubleshooting
     * - regulatory review
     * - dispute handling
     */
    @Lob
    @Column(
        name = "raw_response",
        columnDefinition = "TEXT"
    )
    private String rawResponse;


    // ============================================================
    // REQUEST INFORMATION
    // ============================================================

    /**
     * User/system actor who requested the check.
     */
    @Column(
        name = "requested_by",
        length = 150
    )
    private String requestedBy;


    /**
     * Provider or internal failure reason.
     */
    @Column(
        name = "failure_reason",
        length = 1000
    )
    private String failureReason;


    // ============================================================
    // DATES
    // ============================================================

    @Column(
        name = "created_at",
        nullable = false
    )
    private LocalDateTime createdAt;


    /**
     * Bureau report validity period.
     *
     * Default:
     * 90 days after creation.
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;


    // ============================================================
    // ENTITY LIFECYCLE
    // ============================================================

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (expiresAt == null) {
            expiresAt = createdAt.plusDays(90);
        }

        if (status == null) {
            status = CheckStatus.PENDING;
        }
    }


    // ============================================================
    // EXPIRATION
    // ============================================================

    /**
     * Returns true when the bureau report has expired.
     */
    public boolean isExpired() {

        return expiresAt != null
            && !expiresAt.isAfter(LocalDateTime.now());
    }


    /**
     * Returns true when the report is still valid.
     */
    public boolean isValid() {

        return !isExpired()
            && status == CheckStatus.COMPLETED;
    }


    // ============================================================
    // ENUM
    // ============================================================

    public enum CheckStatus {

        /**
         * Request created but provider response
         * has not yet been received.
         */
        PENDING,

        /**
         * Provider returned a successful bureau result.
         */
        COMPLETED,

        /**
         * Provider request failed.
         */
        FAILED,

        /**
         * Provider responded successfully but
         * found no bureau record.
         */
        NO_RECORD_FOUND
    }
    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getTotalOutstandingDebtDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getTotalOutstandingDebt() {
        return totalOutstandingDebt == null ? null : totalOutstandingDebt.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalOutstandingDebtDecimal() {
        return totalOutstandingDebt;
    }

    @Deprecated
    public void setTotalOutstandingDebt(Double value) {
        this.totalOutstandingDebt = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalOutstandingDebt(BigDecimal value) {
        this.totalOutstandingDebt = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getTotalMonthlyObligationsDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getTotalMonthlyObligations() {
        return totalMonthlyObligations == null ? null : totalMonthlyObligations.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalMonthlyObligationsDecimal() {
        return totalMonthlyObligations;
    }

    @Deprecated
    public void setTotalMonthlyObligations(Double value) {
        this.totalMonthlyObligations = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalMonthlyObligations(BigDecimal value) {
        this.totalMonthlyObligations = value;
    }

    /** Backward-compatible builder overloads for legacy Double callers.
     *  Financial state is stored as BigDecimal.
     */
    public static class CreditBureauCheckBuilder {
        private BigDecimal totalMonthlyObligations;
        private BigDecimal totalOutstandingDebt;


        public CreditBureauCheckBuilder totalOutstandingDebt(Double value) {
            this.totalOutstandingDebt = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public CreditBureauCheckBuilder totalMonthlyObligations(Double value) {
            this.totalMonthlyObligations = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }        public CreditBureauCheckBuilder totalOutstandingDebt(BigDecimal value) {
            this.totalOutstandingDebt = value;
            return this;
        }
        public CreditBureauCheckBuilder totalMonthlyObligations(BigDecimal value) {
            this.totalMonthlyObligations = value;
            return this;
        }
    }

}
