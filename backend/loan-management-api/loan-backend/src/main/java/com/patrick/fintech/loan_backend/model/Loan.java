
package com.patrick.fintech.loan_backend.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
        name = "loans",
        indexes = {
                @Index(name = "idx_loans_org", columnList = "organization_id"),
                @Index(name = "idx_loans_branch", columnList = "branch_id"),
                @Index(name = "idx_loans_borrower", columnList = "borrower_id"),
                @Index(name = "idx_loans_status", columnList = "status"),
                @Index(name = "idx_loans_type", columnList = "loan_type"),
                @Index(name = "idx_loans_created_at", columnList = "created_at"),
                @Index(name = "idx_loans_disbursed_at", columnList = "disbursed_at"),
                @Index(name = "idx_loans_days_overdue", columnList = "days_overdue"),
                @Index(name = "idx_loans_maturity_date", columnList = "maturity_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Loan {

    // ============================================================
    // IDENTITY
    // ============================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic concurrency guard for loan state changes outside the
     * pessimistically locked payment workflow.
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(
            name = "reference_number",
            unique = true,
            nullable = false,
            length = 100
    )
    private String referenceNumber;


    // ============================================================
    // ORGANIZATION
    // ============================================================

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "organization_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_loan_organization")
    )
    private Organization organization;


    // ============================================================
    // BRANCH
    // ============================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "branch_id",
            foreignKey = @ForeignKey(name = "fk_loan_branch")
    )
    @JsonIgnoreProperties({
            "hibernateLazyInitializer",
            "handler"
    })
    private Branch branch;


    // ============================================================
    // BORROWER
    // ============================================================

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "borrower_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_loan_borrower")
    )
    @JsonIgnoreProperties({
            "hibernateLazyInitializer",
            "handler"
    })
    private Borrower borrower;


    // ============================================================
    // APPROVAL / OFFICER
    // ============================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "approved_by",
            foreignKey = @ForeignKey(name = "fk_loan_approved_by")
    )
    @JsonIgnore
    private User approvedBy;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "loan_officer_id",
            foreignKey = @ForeignKey(name = "fk_loan_officer")
    )
    @JsonIgnore
    private User loanOfficer;


    // ============================================================
    // LOAN CLASSIFICATION
    // ============================================================

    @Enumerated(EnumType.STRING)
    @Column(
            name = "loan_type",
            nullable = false,
            length = 50
    )
    private LoanType loanType;


    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 50
    )
    @Builder.Default
    private LoanStatus status = LoanStatus.PENDING;


    @Enumerated(EnumType.STRING)
    @Column(
            name = "repayment_frequency",
            length = 30
    )
    private RepaymentFrequency repaymentFrequency;


    // ============================================================
    // LOAN AMOUNTS
    // ============================================================

    @Column(name = "amount", precision = 19, scale = 6)
    @JsonProperty("amount")
    private BigDecimal amount;


    @Column(name = "disbursed_amount", precision = 19, scale = 6)
    @JsonProperty("disbursedAmount")
    private BigDecimal disbursedAmount;


    @Column(name = "total_repayable", precision = 19, scale = 6)
    @JsonProperty("totalRepayable")
    private BigDecimal totalRepayable;


    @Column(name = "total_paid", precision = 19, scale = 6)
    @Builder.Default
    @JsonProperty("totalPaid")
    private BigDecimal totalPaid = BigDecimal.ZERO;


    @Column(name = "outstanding_balance", precision = 19, scale = 6)
    @Builder.Default
    @JsonProperty("outstandingBalance")
    private BigDecimal outstandingBalance = BigDecimal.ZERO;


    // ============================================================
    // REPAYMENT INFORMATION
    // ============================================================

    @Column(name = "next_installment_amount", precision = 19, scale = 6)
    @JsonProperty("nextInstallmentAmount")
    private BigDecimal nextInstallmentAmount;


    @Column(name = "next_payment_date")
    private LocalDate nextPaymentDate;


    @Column(name = "next_due_date")
    private LocalDate nextDueDate;


    @Column(name = "last_payment_date")
    private LocalDate lastPaymentDate;


    @Column(name = "missed_installments")
    @Builder.Default
    private Integer missedInstallments = 0;


    @Column(name = "days_overdue")
    @Builder.Default
    private Integer daysOverdue = 0;


    // ============================================================
    // INTEREST
    // ============================================================

    @Column(name = "interest_rate", precision = 19, scale = 9)
    @JsonProperty("interestRate")
    private BigDecimal interestRate;


    /**
     * MONTHLY or ANNUAL.
     *
     * DAILY INTEREST MODEL:
     *
     * MONTHLY:
     *     daily rate = monthly rate / 100 / 30
     *
     * Example:
     *
     *     10% monthly
     *
     *     10 / 100 / 30
     *     = 0.0033333333 per day
     *
     * ANNUAL:
     *
     *     daily rate = annual rate / 100 / 12 / 30
     */
    @Column(
            name = "interest_rate_type",
            length = 20
    )
    @Builder.Default
    private String interestRateType = "MONTHLY";


    @Column(name = "duration_months")
    private Integer durationMonths;


    // ============================================================
    // CURRENCY
    // ============================================================

    @Column(
            name = "currency",
            length = 3
    )
    @Builder.Default
    private String currency = "RWF";


    // ============================================================
    // PROCESSING FEES
    // ============================================================

    @Column(name = "processing_fee_rate", precision = 19, scale = 9)
    @Builder.Default
    @JsonProperty("processingFeeRate")
    private BigDecimal processingFeeRate = BigDecimal.valueOf(2.0);


    @Column(name = "processing_fee", precision = 19, scale = 6)
    @Builder.Default
    @JsonProperty("processingFee")
    private BigDecimal processingFee = BigDecimal.ZERO;


    // ============================================================
    // PURPOSE / SECURITY
    // ============================================================

    @Column(columnDefinition = "TEXT")
    private String notes;


    @Column(length = 255)
    private String purpose;


    @Column(
            name = "collateral_description",
            columnDefinition = "TEXT"
    )
    private String collateralDescription;


    @Column(name = "collateral_value", precision = 19, scale = 6)
    @JsonProperty("collateralValue")
    private BigDecimal collateralValue;


    @Column(
            name = "rejection_reason",
            columnDefinition = "TEXT"
    )
    private String rejectionReason;


    @Column(
            name = "internal_notes",
            columnDefinition = "TEXT"
    )
    private String internalNotes;


    // ============================================================
    // IMPORT INFORMATION
    // ============================================================

    @Column(nullable = false)
    @Builder.Default
    private Boolean imported = false;


    @Column(name = "import_batch_id")
    private Long importBatchId;


    // ============================================================
    // CREDIT / RISK
    // ============================================================

    @Column(name = "risk_score", precision = 19, scale = 9)
    @JsonProperty("riskScore")
    private BigDecimal riskScore;


    @Column(
            name = "risk_category",
            length = 30
    )
    private String riskCategory;


    @Column(name = "debt_to_income_ratio", precision = 19, scale = 9)
    @JsonProperty("debtToIncomeRatio")
    private BigDecimal debtToIncomeRatio;


    @Column(name = "credit_score_snapshot")
    private Integer creditScoreSnapshot;


    // ============================================================
    // REGULATORY DATES
    // ============================================================

    @Column(name = "start_date")
    private LocalDate startDate;


    @Column(name = "approved_at")
    private LocalDate approvedAt;


    /**
     * EXACT DATE AND TIME THE LOAN WAS DISBURSED.
     *
     * This is intentionally LocalDateTime.
     *
     * Daily interest is based on elapsed 24-hour periods from
     * this timestamp.
     *
     * Example:
     *
     * 2026-08-08 10:30:00
     *
     * to
     *
     * 2026-08-09 10:30:00
     *
     * = exactly 24 hours = 1 day of interest.
     */
    @Column(name = "disbursed_at")
    private LocalDateTime disbursedAt;


    @Column(name = "maturity_date")
    private LocalDate maturityDate;


    // ============================================================
    // AUDIT / SYSTEM DATES
    // ============================================================

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;


    @Column(
            name = "updated_at",
            nullable = false
    )
    private LocalDateTime updatedAt;


    // ============================================================
    // TERMS
    // ============================================================

    @Column(name = "terms_accepted_at")
    private LocalDateTime termsAcceptedAt;


    // ============================================================
    // PAYMENTS
    // ============================================================

    @JsonIgnore
    @OneToMany(
            mappedBy = "loan",
            cascade = CascadeType.ALL,
            orphanRemoval = false,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<Payment> payments = new ArrayList<>();


    // ============================================================
    // JPA LIFECYCLE
    // ============================================================

    @PrePersist
    protected void onCreate() {

        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }

        if (status == null) {
            status = LoanStatus.PENDING;
        }

        if (interestRateType == null ||
                interestRateType.isBlank()) {

            interestRateType = "MONTHLY";
        }

        if (currency == null ||
                currency.isBlank()) {

            currency = "RWF";
        }

        if (missedInstallments == null) {
            missedInstallments = 0;
        }

        if (daysOverdue == null) {
            daysOverdue = 0;
        }

        if (totalPaid == null) {
            totalPaid = BigDecimal.valueOf(0.0);
        }

        if (outstandingBalance == null) {
            outstandingBalance = BigDecimal.valueOf(0.0);
        }

        if (processingFeeRate == null) {
            processingFeeRate = BigDecimal.valueOf(2.0);
        }

        if (processingFee == null) {
            processingFee = BigDecimal.valueOf(0.0);
        }

        if (imported == null) {
            imported = false;
        }
    }


    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }


    // ============================================================
    // ENUMS
    // ============================================================

    public enum LoanType {

        PERSONAL,

        MORTGAGE,

        AUTO,

        BUSINESS,

        STUDENT,

        EMERGENCY,

        ASSET_FINANCE,

        SALARY_ADVANCE,

        MICROFINANCE,

        AGRICULTURAL,

        TRADE_FINANCE,

        GROUP
    }


    public enum RepaymentFrequency {

        WEEKLY,

        BIWEEKLY,

        MONTHLY,

        QUARTERLY,

        BULLET
    }
    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getAmountDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getAmount() {
        return amount == null ? null : amount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getAmountDecimal() {
        return amount;
    }

    @Deprecated
    public void setAmount(Double value) {
        this.amount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setAmount(BigDecimal value) {
        this.amount = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getDisbursedAmountDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getDisbursedAmount() {
        return disbursedAmount == null ? null : disbursedAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getDisbursedAmountDecimal() {
        return disbursedAmount;
    }

    @Deprecated
    public void setDisbursedAmount(Double value) {
        this.disbursedAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setDisbursedAmount(BigDecimal value) {
        this.disbursedAmount = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getTotalRepayableDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getTotalRepayable() {
        return totalRepayable == null ? null : totalRepayable.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalRepayableDecimal() {
        return totalRepayable;
    }

    @Deprecated
    public void setTotalRepayable(Double value) {
        this.totalRepayable = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalRepayable(BigDecimal value) {
        this.totalRepayable = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getTotalPaidDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getTotalPaid() {
        return totalPaid == null ? null : totalPaid.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalPaidDecimal() {
        return totalPaid;
    }

    @Deprecated
    public void setTotalPaid(Double value) {
        this.totalPaid = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalPaid(BigDecimal value) {
        this.totalPaid = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getOutstandingBalanceDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getOutstandingBalance() {
        return outstandingBalance == null ? null : outstandingBalance.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getOutstandingBalanceDecimal() {
        return outstandingBalance;
    }

    @Deprecated
    public void setOutstandingBalance(Double value) {
        this.outstandingBalance = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setOutstandingBalance(BigDecimal value) {
        this.outstandingBalance = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getNextInstallmentAmountDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getNextInstallmentAmount() {
        return nextInstallmentAmount == null ? null : nextInstallmentAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getNextInstallmentAmountDecimal() {
        return nextInstallmentAmount;
    }

    @Deprecated
    public void setNextInstallmentAmount(Double value) {
        this.nextInstallmentAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setNextInstallmentAmount(BigDecimal value) {
        this.nextInstallmentAmount = value;
    }


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
     * New financial code should use getProcessingFeeRateDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getProcessingFeeRate() {
        return processingFeeRate == null ? null : processingFeeRate.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getProcessingFeeRateDecimal() {
        return processingFeeRate;
    }

    @Deprecated
    public void setProcessingFeeRate(Double value) {
        this.processingFeeRate = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setProcessingFeeRate(BigDecimal value) {
        this.processingFeeRate = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getProcessingFeeDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getProcessingFee() {
        return processingFee == null ? null : processingFee.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getProcessingFeeDecimal() {
        return processingFee;
    }

    @Deprecated
    public void setProcessingFee(Double value) {
        this.processingFee = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setProcessingFee(BigDecimal value) {
        this.processingFee = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getCollateralValueDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getCollateralValue() {
        return collateralValue == null ? null : collateralValue.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getCollateralValueDecimal() {
        return collateralValue;
    }

    @Deprecated
    public void setCollateralValue(Double value) {
        this.collateralValue = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setCollateralValue(BigDecimal value) {
        this.collateralValue = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getRiskScoreDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getRiskScore() {
        return riskScore == null ? null : riskScore.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getRiskScoreDecimal() {
        return riskScore;
    }

    @Deprecated
    public void setRiskScore(Double value) {
        this.riskScore = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setRiskScore(BigDecimal value) {
        this.riskScore = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getDebtToIncomeRatioDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getDebtToIncomeRatio() {
        return debtToIncomeRatio == null ? null : debtToIncomeRatio.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getDebtToIncomeRatioDecimal() {
        return debtToIncomeRatio;
    }

    @Deprecated
    public void setDebtToIncomeRatio(Double value) {
        this.debtToIncomeRatio = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setDebtToIncomeRatio(BigDecimal value) {
        this.debtToIncomeRatio = value;
    }

    /** Backward-compatible builder overloads for legacy Double callers.
     *  Financial state is stored as BigDecimal.
     */
    public static class LoanBuilder {

        private BigDecimal amount;
        private BigDecimal disbursedAmount;
        private BigDecimal totalRepayable;
        private BigDecimal totalPaid;
        private BigDecimal outstandingBalance;
        private BigDecimal nextInstallmentAmount;
        private BigDecimal interestRate;
        private BigDecimal processingFeeRate;
        private BigDecimal processingFee;
        private BigDecimal collateralValue;
        private BigDecimal riskScore;
        private BigDecimal debtToIncomeRatio;


        public LoanBuilder amount(Double value) {
            this.amount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanBuilder disbursedAmount(Double value) {
            this.disbursedAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanBuilder totalRepayable(Double value) {
            this.totalRepayable = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanBuilder totalPaid(Double value) {
            this.totalPaid = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanBuilder outstandingBalance(Double value) {
            this.outstandingBalance = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanBuilder nextInstallmentAmount(Double value) {
            this.nextInstallmentAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanBuilder interestRate(Double value) {
            this.interestRate = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanBuilder processingFeeRate(Double value) {
            this.processingFeeRate = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanBuilder processingFee(Double value) {
            this.processingFee = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanBuilder collateralValue(Double value) {
            this.collateralValue = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanBuilder riskScore(Double value) {
            this.riskScore = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanBuilder debtToIncomeRatio(Double value) {
            this.debtToIncomeRatio = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }        public LoanBuilder amount(BigDecimal value) {
            this.amount = value;
            return this;
        }
        public LoanBuilder disbursedAmount(BigDecimal value) {
            this.disbursedAmount = value;
            return this;
        }
        public LoanBuilder totalRepayable(BigDecimal value) {
            this.totalRepayable = value;
            return this;
        }
        public LoanBuilder totalPaid(BigDecimal value) {
            this.totalPaid = value;
            return this;
        }
        public LoanBuilder outstandingBalance(BigDecimal value) {
            this.outstandingBalance = value;
            return this;
        }
        public LoanBuilder nextInstallmentAmount(BigDecimal value) {
            this.nextInstallmentAmount = value;
            return this;
        }
        public LoanBuilder interestRate(BigDecimal value) {
            this.interestRate = value;
            return this;
        }
        public LoanBuilder processingFeeRate(BigDecimal value) {
            this.processingFeeRate = value;
            return this;
        }
        public LoanBuilder processingFee(BigDecimal value) {
            this.processingFee = value;
            return this;
        }
        public LoanBuilder collateralValue(BigDecimal value) {
            this.collateralValue = value;
            return this;
        }
        public LoanBuilder riskScore(BigDecimal value) {
            this.riskScore = value;
            return this;
        }
        public LoanBuilder debtToIncomeRatio(BigDecimal value) {
            this.debtToIncomeRatio = value;
            return this;
        }
    }

}
