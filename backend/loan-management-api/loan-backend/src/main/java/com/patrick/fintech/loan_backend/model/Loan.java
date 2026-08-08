
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

    @Column(name = "amount")
    private Double amount;


    @Column(name = "disbursed_amount")
    private Double disbursedAmount;


    @Column(name = "total_repayable")
    private Double totalRepayable;


    @Column(name = "total_paid")
    @Builder.Default
    private Double totalPaid = 0.0;


    @Column(name = "outstanding_balance")
    @Builder.Default
    private Double outstandingBalance = 0.0;


    // ============================================================
    // REPAYMENT INFORMATION
    // ============================================================

    @Column(name = "next_installment_amount")
    private Double nextInstallmentAmount;


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

    @Column(name = "interest_rate")
    private Double interestRate;


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

    @Column(name = "processing_fee_rate")
    @Builder.Default
    private Double processingFeeRate = 2.0;


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


    @Column(
            name = "risk_category",
            length = 30
    )
    private String riskCategory;


    @Column(name = "debt_to_income_ratio")
    private Double debtToIncomeRatio;


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
