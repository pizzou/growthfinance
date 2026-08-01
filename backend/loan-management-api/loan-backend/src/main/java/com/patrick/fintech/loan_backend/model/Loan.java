package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
     * Unique loan reference visible to staff, borrowers and regulators.
     *
     * Example:
     * KCB-2024-000123
     */
    @Column(
        name = "reference_number",
        unique = true,
        nullable = false,
        length = 100
    )
    private String referenceNumber;


    
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

    /**
     * Branch responsible for the loan.
     */
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


    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "approved_by",
        foreignKey = @ForeignKey(name = "fk_loan_approved_by")
    )
    @JsonIgnore
    private User approvedBy;

    /**
     * Loan officer responsible for the facility.
     */
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

    /**
     * Original requested / approved principal.
     */
    @Column(name = "amount")
    private Double amount;

    /**
     * Amount actually disbursed.
     *
     * Regulatory reports should normally use this for
     * actual disbursement statistics.
     */
    @Column(name = "disbursed_amount")
    private Double disbursedAmount;

    /**
     * Total amount scheduled to be repaid.
     */
    @Column(name = "total_repayable")
    private Double totalRepayable;

    /**
     * Total amount actually paid by borrower.
     */
    @Column(name = "total_paid")
    @Builder.Default
    private Double totalPaid = 0.0;

    /**
     * Current outstanding loan balance.
     */
    @Column(name = "outstanding_balance")
    @Builder.Default
    private Double outstandingBalance = 0.0;


    // ============================================================
    // REPAYMENT / INSTALLMENT INFORMATION
    // ============================================================

    @Column(name = "next_installment_amount")
    private Double nextInstallmentAmount;

    @Column(name = "next_payment_date")
    private LocalDate nextPaymentDate;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "last_payment_date")
    private LocalDate lastPaymentDate;

    /**
     * Number of installments missed.
     */
    @Column(name = "missed_installments")
    @Builder.Default
    private Integer missedInstallments = 0;

    /**
     * Number of days currently overdue.
     */
    @Column(name = "days_overdue")
    @Builder.Default
    private Integer daysOverdue = 0;


    // ============================================================
    // INTEREST
    // ============================================================

    @Column(name = "interest_rate")
    private Double interestRate;

    /**
     * MONTHLY or ANNUAL.
     *
     * This should be copied from the loan product when the
     * loan is created so historical reports remain consistent
     * even if the product changes later.
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

    /**
     * ISO-4217 currency code.
     *
     * Example:
     * RWF
     * USD
     * EUR
     */
    @Column(
        name = "currency",
        length = 3
    )
    @Builder.Default
    private String currency = "RWF";


    // ============================================================
    // PROCESSING FEES
    // ============================================================

    /**
     * Processing fee rate as percentage.
     */
    @Column(name = "processing_fee_rate")
    @Builder.Default
    private Double processingFeeRate = 2.0;

    /**
     * Actual processing fee amount.
     */
    @Column(name = "processing_fee")
    @Builder.Default
    private Double processingFee = 0.0;


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

    @Column(name = "collateral_value")
    private Double collateralValue;

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

    @Column(name = "risk_score")
    private Double riskScore;

    /**
     * LOW
     * MEDIUM
     * HIGH
     * CRITICAL
     */
    @Column(
        name = "risk_category",
        length = 30
    )
    private String riskCategory;

    @Column(name = "debt_to_income_ratio")
    private Double debtToIncomeRatio;

    /**
     * Credit score captured when the loan was evaluated.
     *
     * Important for historical reporting because the borrower's
     * current credit score may be different.
     */
    @Column(name = "credit_score_snapshot")
    private Integer creditScoreSnapshot;


    // ============================================================
    // IMPORTANT REGULATORY DATES
    // ============================================================

    /**
     * Loan start date.
     */
    @Column(name = "start_date")
    private LocalDate startDate;

    /**
     * Date loan was approved.
     */
    @Column(name = "approved_at")
    private LocalDate approvedAt;

    /**
     * Actual date loan was disbursed.
     */
    @Column(name = "disbursed_at")
    private LocalDate disbursedAt;

    /**
     * Contractual maturity date.
     */
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
    // TERMS & CONDITIONS
    // ============================================================

    /**
     * Timestamp proving that the applicant accepted the terms.
     *
     * Useful for compliance, dispute resolution and audit.
     */
    @Column(name = "terms_accepted_at")
    private LocalDateTime termsAcceptedAt;


    // ============================================================
    // PAYMENTS
    // ============================================================

    /**
     * Payment history.
     *
     * LAZY is important here because loading every payment whenever
     * a loan is displayed can become very expensive.
     */
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
            totalPaid = 0.0;
        }

        if (outstandingBalance == null) {
            outstandingBalance = 0.0;
        }

        if (processingFeeRate == null) {
            processingFeeRate = 2.0;
        }

        if (processingFee == null) {
            processingFee = 0.0;
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
}