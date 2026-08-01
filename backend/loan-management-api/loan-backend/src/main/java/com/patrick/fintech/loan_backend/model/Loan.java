package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.patrick.fintech.loan_backend.util.MoneyMath;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
    name = "loans",
    indexes = {
        @Index(name = "idx_loans_org", columnList = "organization_id"),
        @Index(name = "idx_loans_borrower", columnList = "borrower_id"),
        @Index(name = "idx_loans_status", columnList = "status"),
        @Index(name = "idx_loans_credit_quality", columnList = "credit_quality"),
        @Index(name = "idx_loans_arrears_status", columnList = "arrears_status")
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

    @Column(unique = true, nullable = false)
    private String referenceNumber;


    // ============================================================
    // ORGANIZATION / RELATIONSHIPS
    // ============================================================

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "borrower_id", nullable = false)
    private Borrower borrower;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "loan_officer_id")
    private User loanOfficer;


    // ============================================================
    // LOAN TYPE / STATUS
    // ============================================================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanType loanType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanStatus status;


    // ============================================================
    // CREDIT CLASSIFICATION
    // ============================================================

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(
        name = "credit_quality",
        nullable = false,
        length = 20
    )
    private CreditQuality creditQuality = CreditQuality.CURRENT;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(
        name = "arrears_status",
        nullable = false,
        length = 20
    )
    private ArrearsStatus arrearsStatus = ArrearsStatus.NOT_DUE;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(
        name = "collections_stage",
        nullable = false,
        length = 20
    )
    private CollectionsStage collectionsStage = CollectionsStage.NORMAL;

    @Column(name = "classified_at")
    private LocalDateTime classifiedAt;


    // ============================================================
    // REPAYMENT CONFIGURATION
    // ============================================================

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(
        name = "repayment_frequency",
        nullable = false
    )
    private RepaymentFrequency repaymentFrequency =
        RepaymentFrequency.MONTHLY;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(
        name = "next_installment_amount",
        precision = 19,
        scale = 2
    )
    private BigDecimal nextInstallmentAmount;

    @Column(name = "next_payment_date")
    private LocalDate nextPaymentDate;

    /**
     * Interest rate percentage.
     *
     * Example:
     * 12.00 = 12%
     */
    @Column(precision = 10, scale = 4)
    private BigDecimal interestRate;

    /**
     * MONTHLY or ANNUAL.
     */
    @Builder.Default
    @Column(name = "interest_rate_type")
    private String interestRateType = "MONTHLY";

    private Integer durationMonths;

    /**
     * ISO-4217 currency code.
     */
    private String currency;


    // ============================================================
    // FEES
    // ============================================================

    /**
     * Processing fee percentage.
     */
    @Column(precision = 10, scale = 4)
    private BigDecimal processingFeeRate;

    @Column(
        precision = 19,
        scale = 2
    )
    private BigDecimal processingFee;


    // ============================================================
    // FINANCIAL AMOUNTS
    // ============================================================

    @Column(
        precision = 19,
        scale = 2
    )
    private BigDecimal disbursedAmount;

    @Column(
        precision = 19,
        scale = 2
    )
    private BigDecimal totalRepayable;

    @Column(
        precision = 19,
        scale = 2
    )
    private BigDecimal totalPaid;

    @Column(
        precision = 19,
        scale = 2
    )
    private BigDecimal outstandingBalance;


    // ============================================================
    // LOAN INFORMATION
    // ============================================================

    private String notes;

    private String purpose;

    private String collateralDescription;

    @Column(
        precision = 19,
        scale = 2
    )
    private BigDecimal collateralValue;

    private String rejectionReason;

    private String internalNotes;


    // ============================================================
    // IMPORT
    // ============================================================

    @Builder.Default
    private Boolean imported = false;

    private Long importBatchId;


    // ============================================================
    // RISK
    // ============================================================

    private Double riskScore;

    private String riskCategory;

    private Double debtToIncomeRatio;

    private Integer creditScoreSnapshot;


    // ============================================================
    // IMPORTANT DATES
    // ============================================================

    private LocalDate startDate;

    private LocalDate approvedAt;

    private LocalDate disbursedAt;

    private LocalDate maturityDate;

    private LocalDate nextDueDate;

    private LocalDate lastPaymentDate;


    // ============================================================
    // REPAYMENT TRACKING
    // ============================================================

    @Builder.Default
    private Integer missedInstallments = 0;

    @Builder.Default
    private Integer daysOverdue = 0;


    // ============================================================
    // AUDIT DATES
    // ============================================================

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime termsAcceptedAt;


    // ============================================================
    // PAYMENTS
    // ============================================================

    @JsonIgnore
    @OneToMany(
        mappedBy = "loan",
        cascade = CascadeType.ALL
    )
    private List<Payment> payments;




    @PrePersist
    protected void onCreate() {

        LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;

        if (status == null) {
            status = LoanStatus.PENDING;
        }

        if (interestRateType == null ||
            interestRateType.isBlank()) {

            interestRateType = "MONTHLY";
        }

        if (repaymentFrequency == null) {
            repaymentFrequency = RepaymentFrequency.MONTHLY;
        }

        if (missedInstallments == null) {
            missedInstallments = 0;
        }

        if (daysOverdue == null) {
            daysOverdue = 0;
        }

        if (totalPaid == null) {
            totalPaid = MoneyMath.ZERO;
        }

        if (processingFeeRate == null) {
            processingFeeRate =
                BigDecimal.valueOf(2)
                    .setScale(
                        MoneyMath.SCALE,
                        MoneyMath.ROUNDING
                    );
        }

        if (creditQuality == null) {
            creditQuality = CreditQuality.CURRENT;
        }

        if (arrearsStatus == null) {
            arrearsStatus = ArrearsStatus.NOT_DUE;
        }

        if (collectionsStage == null) {
            collectionsStage = CollectionsStage.NORMAL;
        }

        
        if (outstandingBalance == null && amount != null) {
            outstandingBalance = amount;
        }
    }


    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }


   

    public Double getAmountDouble() {
        return amount == null
            ? null
            : amount.doubleValue();
    }

    public Double getInterestRateDouble() {
        return interestRate == null
            ? null
            : interestRate.doubleValue();
    }

    public Double getProcessingFeeRateDouble() {
        return processingFeeRate == null
            ? null
            : processingFeeRate.doubleValue();
    }

    public Double getProcessingFeeDouble() {
        return processingFee == null
            ? null
            : processingFee.doubleValue();
    }

    public Double getDisbursedAmountDouble() {
        return disbursedAmount == null
            ? null
            : disbursedAmount.doubleValue();
    }

    public Double getTotalRepayableDouble() {
        return totalRepayable == null
            ? null
            : totalRepayable.doubleValue();
    }

    public Double getTotalPaidDouble() {
        return totalPaid == null
            ? null
            : totalPaid.doubleValue();
    }

    public Double getOutstandingBalanceDouble() {
        return outstandingBalance == null
            ? null
            : outstandingBalance.doubleValue();
    }

    public Double getCollateralValueDouble() {
        return collateralValue == null
            ? null
            : collateralValue.doubleValue();
    }

    public Double getNextInstallmentAmountDouble() {
        return nextInstallmentAmount == null
            ? null
            : nextInstallmentAmount.doubleValue();
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

        MONTHLY
    }


    public enum CreditQuality {

        CURRENT,
        WATCH,
        SUBSTANDARD,
        DOUBTFUL,
        LOSS
    }


    public enum ArrearsStatus {

        NOT_DUE,
        PAST_DUE
    }


    public enum CollectionsStage {

        NORMAL,
        REMINDER,
        COLLECTION,
        LEGAL,
        RECOVERY
    }
    
}