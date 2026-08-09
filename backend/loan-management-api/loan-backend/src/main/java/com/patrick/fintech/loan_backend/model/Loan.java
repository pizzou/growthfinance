package com.patrick.fintech.loan_backend.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@JsonIgnoreProperties({
"hibernateLazyInitializer",
"handler"
})
@Entity
@Table(
name = "loans",
indexes = {
@Index(
name = "idx_loans_org",
columnList = "organization_id"
),
@Index(
name = "idx_loans_branch",
columnList = "branch_id"
),
@Index(
name = "idx_loans_borrower",
columnList = "borrower_id"
),
@Index(
name = "idx_loans_status",
columnList = "status"
),
@Index(
name = "idx_loans_type",
columnList = "loan_type"
),
@Index(
name = "idx_loans_created_at",
columnList = "created_at"
),
@Index(
name = "idx_loans_created_by",
columnList = "created_by"
),
@Index(
name = "idx_loans_disbursed_at",
columnList = "disbursed_at"
),
@Index(
name = "idx_loans_days_overdue",
columnList = "days_overdue"
),
@Index(
name = "idx_loans_maturity_date",
columnList = "maturity_date"
)
}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Loan {


@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

/**
 * Optimistic concurrency guard.
 */
@Version
@Column(
        name = "version",
        nullable = false
)
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
@ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
)
@JoinColumn(
        name = "organization_id",
        nullable = false,
        foreignKey = @ForeignKey(
                name = "fk_loan_organization"
        )
)
private Organization organization;


// ============================================================
// BRANCH
// ============================================================

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(
        name = "branch_id",
        foreignKey = @ForeignKey(
                name = "fk_loan_branch"
        )
)
@JsonIgnoreProperties({
        "hibernateLazyInitializer",
        "handler"
})
private Branch branch;


// ============================================================
// BORROWER
// ============================================================

@ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
)
@JoinColumn(
        name = "borrower_id",
        nullable = false,
        foreignKey = @ForeignKey(
                name = "fk_loan_borrower"
        )
)
@JsonIgnoreProperties({
        "hibernateLazyInitializer",
        "handler"
})
private Borrower borrower;


// ============================================================
// MAKER / CREATOR
// ============================================================

/**
 * The user who created/submitted this loan application.
 *
 * This is the authoritative maker-checker field.
 *
 * createdBy:
 *     User who created/submitted the application.
 *
 * loanOfficer:
 *     Officer assigned to manage the loan.
 *
 * approvedBy:
 *     User who approved the loan.
 *
 * A user must never approve a loan they created.
 */
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(
        name = "created_by",
        foreignKey = @ForeignKey(
                name = "fk_loan_created_by"
        )
)
@JsonIgnore
private User createdBy;


// ============================================================
// APPROVAL / OFFICER
// ============================================================

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(
        name = "approved_by",
        foreignKey = @ForeignKey(
                name = "fk_loan_approved_by"
        )
)
@JsonIgnore
private User approvedBy;


@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(
        name = "loan_officer_id",
        foreignKey = @ForeignKey(
                name = "fk_loan_officer"
        )
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

@Column(
        name = "amount",
        precision = 19,
        scale = 6
)
@JsonProperty("amount")
private BigDecimal amount;


@Column(
        name = "disbursed_amount",
        precision = 19,
        scale = 6
)
@JsonProperty("disbursedAmount")
private BigDecimal disbursedAmount;


@Column(
        name = "total_repayable",
        precision = 19,
        scale = 6
)
@JsonProperty("totalRepayable")
private BigDecimal totalRepayable;


@Column(
        name = "total_paid",
        precision = 19,
        scale = 6
)
@Builder.Default
@JsonProperty("totalPaid")
private BigDecimal totalPaid = BigDecimal.ZERO;


@Column(
        name = "outstanding_balance",
        precision = 19,
        scale = 6
)
@Builder.Default
@JsonProperty("outstandingBalance")
private BigDecimal outstandingBalance = BigDecimal.ZERO;


// ============================================================
// REPAYMENT INFORMATION
// ============================================================

@Column(
        name = "next_installment_amount",
        precision = 19,
        scale = 6
)
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

@Column(
        name = "interest_rate",
        precision = 19,
        scale = 9
)
@JsonProperty("interestRate")
private BigDecimal interestRate;


/**
 * Interest rate type.
 *
 * MONTHLY:
 *     The configured percentage is a monthly rate.
 *
 * ANNUAL:
 *     The configured percentage is an annual rate and is
 *     converted to a monthly rate for contractual schedules.
 *
 * IMPORTANT:
 *
 * Actual payment-time elapsed interest is NOT determined
 * merely from this field.
 *
 * Payment processing must use the exact disbursement/payment
 * timestamps and the appropriate daily rate.
 *
 * Required elapsed-day behavior:
 *
 * 09 Aug 2026 10:00 disbursement
 * 09 Aug 2026 10:01 payment
 *
 * = 1 interest day.
 *
 * Another payment at:
 *
 * 09 Aug 2026 10:05
 *
 * = 0 additional days.
 *
 * Payment at:
 *
 * 10 Aug 2026 10:00
 *
 * = 1 additional day.
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

@Column(
        name = "processing_fee_rate",
        precision = 19,
        scale = 9
)
@Builder.Default
@JsonProperty("processingFeeRate")
private BigDecimal processingFeeRate =
        new BigDecimal("2.0");


@Column(
        name = "processing_fee",
        precision = 19,
        scale = 6
)
@Builder.Default
@JsonProperty("processingFee")
private BigDecimal processingFee =
        BigDecimal.ZERO;


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


@Column(
        name = "collateral_value",
        precision = 19,
        scale = 6
)
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

@Column(
        name = "risk_score",
        precision = 19,
        scale = 9
)
@JsonProperty("riskScore")
private BigDecimal riskScore;


@Column(
        name = "risk_category",
        length = 30
)
private String riskCategory;


@Column(
        name = "debt_to_income_ratio",
        precision = 19,
        scale = 9
)
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
 * Exact timestamp when the loan was disbursed.
 *
 * This timestamp is important for payment-time elapsed-day
 * interest calculations.
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
private List<Payment> payments =
        new ArrayList<>();


// ============================================================
// JPA LIFECYCLE
// ============================================================

@PrePersist
protected void onCreate() {

    LocalDateTime now =
            LocalDateTime.now();

    if (createdAt == null) {
        createdAt = now;
    }

    if (updatedAt == null) {
        updatedAt = now;
    }

    if (status == null) {
        status = LoanStatus.PENDING;
    }

    if (interestRateType == null
            || interestRateType.isBlank()) {

        interestRateType = "MONTHLY";
    }

    interestRateType =
            interestRateType.trim().toUpperCase();

    if (currency == null
            || currency.isBlank()) {

        currency = "RWF";
    }

    currency =
            currency.trim().toUpperCase();

    if (missedInstallments == null) {
        missedInstallments = 0;
    }

    if (daysOverdue == null) {
        daysOverdue = 0;
    }

    if (totalPaid == null) {
        totalPaid = BigDecimal.ZERO;
    }

    if (outstandingBalance == null) {
        outstandingBalance = BigDecimal.ZERO;
    }

    if (processingFeeRate == null) {
        processingFeeRate =
                new BigDecimal("2.0");
    }

    if (processingFee == null) {
        processingFee = BigDecimal.ZERO;
    }

    if (imported == null) {
        imported = false;
    }
}


@PreUpdate
protected void onUpdate() {

    updatedAt =
            LocalDateTime.now();
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


// ============================================================
// AUTHORITATIVE BIGDECIMAL FINANCIAL GETTERS
// ============================================================

/**
 * Authoritative loan amount getter for financial calculations.
 *
 * New code should use this method instead of getAmount().
 */
@JsonIgnore
public BigDecimal getAmountDecimal() {

    return amount;
}


/**
 * Authoritative disbursed amount getter.
 */
@JsonIgnore
public BigDecimal getDisbursedAmountDecimal() {

    return disbursedAmount;
}


/**
 * Authoritative total repayable getter.
 */
@JsonIgnore
public BigDecimal getTotalRepayableDecimal() {

    return totalRepayable;
}


/**
 * Authoritative total paid getter.
 */
@JsonIgnore
public BigDecimal getTotalPaidDecimal() {

    return totalPaid;
}


/**
 * Authoritative outstanding balance getter.
 */
@JsonIgnore
public BigDecimal getOutstandingBalanceDecimal() {

    return outstandingBalance;
}


/**
 * Authoritative next installment amount getter.
 */
@JsonIgnore
public BigDecimal getNextInstallmentAmountDecimal() {

    return nextInstallmentAmount;
}


/**
 * Authoritative interest rate getter.
 *
 * New financial code must use this method.
 */
@JsonIgnore
public BigDecimal getInterestRateDecimal() {

    return interestRate;
}


/**
 * Authoritative processing fee rate getter.
 */
@JsonIgnore
public BigDecimal getProcessingFeeRateDecimal() {

    return processingFeeRate;
}


/**
 * Authoritative processing fee getter.
 */
@JsonIgnore
public BigDecimal getProcessingFeeDecimal() {

    return processingFee;
}


/**
 * Authoritative collateral value getter.
 */
@JsonIgnore
public BigDecimal getCollateralValueDecimal() {

    return collateralValue;
}


/**
 * Authoritative risk score getter.
 */
@JsonIgnore
public BigDecimal getRiskScoreDecimal() {

    return riskScore;
}


/**
 * Authoritative debt-to-income ratio getter.
 */
@JsonIgnore
public BigDecimal getDebtToIncomeRatioDecimal() {

    return debtToIncomeRatio;
}


// ============================================================
// LEGACY DOUBLE GETTERS
// ============================================================

/**
 * Legacy compatibility getter.
 *
 * Deprecated because Double is not appropriate for financial
 * calculations.
 */
@Deprecated
@JsonIgnore
public Double getAmount() {

    return amount == null
            ? null
            : amount.doubleValue();
}


@Deprecated
@JsonIgnore
public Double getDisbursedAmount() {

    return disbursedAmount == null
            ? null
            : disbursedAmount.doubleValue();
}


@Deprecated
@JsonIgnore
public Double getTotalRepayable() {

    return totalRepayable == null
            ? null
            : totalRepayable.doubleValue();
}


@Deprecated
@JsonIgnore
public Double getTotalPaid() {

    return totalPaid == null
            ? null
            : totalPaid.doubleValue();
}


@Deprecated
@JsonIgnore
public Double getOutstandingBalance() {

    return outstandingBalance == null
            ? null
            : outstandingBalance.doubleValue();
}


@Deprecated
@JsonIgnore
public Double getNextInstallmentAmount() {

    return nextInstallmentAmount == null
            ? null
            : nextInstallmentAmount.doubleValue();
}


@Deprecated
@JsonIgnore
public Double getInterestRate() {

    return interestRate == null
            ? null
            : interestRate.doubleValue();
}


@Deprecated
@JsonIgnore
public Double getProcessingFeeRate() {

    return processingFeeRate == null
            ? null
            : processingFeeRate.doubleValue();
}


@Deprecated
@JsonIgnore
public Double getProcessingFee() {

    return processingFee == null
            ? null
            : processingFee.doubleValue();
}


@Deprecated
@JsonIgnore
public Double getCollateralValue() {

    return collateralValue == null
            ? null
            : collateralValue.doubleValue();
}


@Deprecated
@JsonIgnore
public Double getRiskScore() {

    return riskScore == null
            ? null
            : riskScore.doubleValue();
}


@Deprecated
@JsonIgnore
public Double getDebtToIncomeRatio() {

    return debtToIncomeRatio == null
            ? null
            : debtToIncomeRatio.doubleValue();
}


// ============================================================
// EXPLICIT BIGDECIMAL SETTERS
// ============================================================

public void setAmount(
        BigDecimal value
) {

    this.amount = value;
}


public void setDisbursedAmount(
        BigDecimal value
) {

    this.disbursedAmount = value;
}


public void setTotalRepayable(
        BigDecimal value
) {

    this.totalRepayable = value;
}


public void setTotalPaid(
        BigDecimal value
) {

    this.totalPaid = value;
}


public void setOutstandingBalance(
        BigDecimal value
) {

    this.outstandingBalance = value;
}


public void setNextInstallmentAmount(
        BigDecimal value
) {

    this.nextInstallmentAmount = value;
}


public void setInterestRate(
        BigDecimal value
) {

    this.interestRate = value;
}


public void setProcessingFeeRate(
        BigDecimal value
) {

    this.processingFeeRate = value;
}


public void setProcessingFee(
        BigDecimal value
) {

    this.processingFee = value;
}


public void setCollateralValue(
        BigDecimal value
) {

    this.collateralValue = value;
}


public void setRiskScore(
        BigDecimal value
) {

    this.riskScore = value;
}


public void setDebtToIncomeRatio(
        BigDecimal value
) {

    this.debtToIncomeRatio = value;
}


// ============================================================
// LEGACY DOUBLE SETTERS
// ============================================================

/**
 * Legacy compatibility setter.
 *
 * New code should use BigDecimal.
 */
@Deprecated
public void setAmount(
        Double value
) {

    this.amount =
            value == null
                    ? null
                    : BigDecimal.valueOf(value);
}


@Deprecated
public void setDisbursedAmount(
        Double value
) {

    this.disbursedAmount =
            value == null
                    ? null
                    : BigDecimal.valueOf(value);
}


@Deprecated
public void setTotalRepayable(
        Double value
) {

    this.totalRepayable =
            value == null
                    ? null
                    : BigDecimal.valueOf(value);
}


@Deprecated
public void setTotalPaid(
        Double value
) {

    this.totalPaid =
            value == null
                    ? null
                    : BigDecimal.valueOf(value);
}


@Deprecated
public void setOutstandingBalance(
        Double value
) {

    this.outstandingBalance =
            value == null
                    ? null
                    : BigDecimal.valueOf(value);
}


@Deprecated
public void setNextInstallmentAmount(
        Double value
) {

    this.nextInstallmentAmount =
            value == null
                    ? null
                    : BigDecimal.valueOf(value);
}


@Deprecated
public void setInterestRate(
        Double value
) {

    this.interestRate =
            value == null
                    ? null
                    : BigDecimal.valueOf(value);
}


@Deprecated
public void setProcessingFeeRate(
        Double value
) {

    this.processingFeeRate =
            value == null
                    ? null
                    : BigDecimal.valueOf(value);
}


@Deprecated
public void setProcessingFee(
        Double value
) {

    this.processingFee =
            value == null
                    ? null
                    : BigDecimal.valueOf(value);
}


@Deprecated
public void setCollateralValue(
        Double value
) {

    this.collateralValue =
            value == null
                    ? null
                    : BigDecimal.valueOf(value);
}


@Deprecated
public void setRiskScore(
        Double value
) {

    this.riskScore =
            value == null
                    ? null
                    : BigDecimal.valueOf(value);
}


@Deprecated
public void setDebtToIncomeRatio(
        Double value
) {

    this.debtToIncomeRatio =
            value == null
                    ? null
                    : BigDecimal.valueOf(value);
}


// ============================================================
// BACKWARD-COMPATIBLE LOMBOK BUILDER
// ============================================================

/**
 * Lombok generates the normal BigDecimal builder methods.
 *
 * These explicit Double overloads are retained so older code
 * that still constructs Loan objects with Double values does
 * not immediately break.
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


    public LoanBuilder amount(
            Double value
    ) {

        this.amount =
                value == null
                        ? null
                        : BigDecimal.valueOf(value);

        return this;
    }


    public LoanBuilder amount(
            BigDecimal value
    ) {

        this.amount = value;

        return this;
    }


    public LoanBuilder disbursedAmount(
            Double value
    ) {

        this.disbursedAmount =
                value == null
                        ? null
                        : BigDecimal.valueOf(value);

        return this;
    }


    public LoanBuilder disbursedAmount(
            BigDecimal value
    ) {

        this.disbursedAmount = value;

        return this;
    }


    public LoanBuilder totalRepayable(
            Double value
    ) {

        this.totalRepayable =
                value == null
                        ? null
                        : BigDecimal.valueOf(value);

        return this;
    }


    public LoanBuilder totalRepayable(
            BigDecimal value
    ) {

        this.totalRepayable = value;

        return this;
    }


    public LoanBuilder totalPaid(
            Double value
    ) {

        this.totalPaid =
                value == null
                        ? null
                        : BigDecimal.valueOf(value);

        return this;
    }


    public LoanBuilder totalPaid(
            BigDecimal value
    ) {

        this.totalPaid = value;

        return this;
    }


    public LoanBuilder outstandingBalance(
            Double value
    ) {

        this.outstandingBalance =
                value == null
                        ? null
                        : BigDecimal.valueOf(value);

        return this;
    }


    public LoanBuilder outstandingBalance(
            BigDecimal value
    ) {

        this.outstandingBalance = value;

        return this;
    }


    public LoanBuilder nextInstallmentAmount(
            Double value
    ) {

        this.nextInstallmentAmount =
                value == null
                        ? null
                        : BigDecimal.valueOf(value);

        return this;
    }


    public LoanBuilder nextInstallmentAmount(
            BigDecimal value
    ) {

        this.nextInstallmentAmount = value;

        return this;
    }


    public LoanBuilder interestRate(
            Double value
    ) {

        this.interestRate =
                value == null
                        ? null
                        : BigDecimal.valueOf(value);

        return this;
    }


    public LoanBuilder interestRate(
            BigDecimal value
    ) {

        this.interestRate = value;

        return this;
    }


    public LoanBuilder processingFeeRate(
            Double value
    ) {

        this.processingFeeRate =
                value == null
                        ? null
                        : BigDecimal.valueOf(value);

        return this;
    }


    public LoanBuilder processingFeeRate(
            BigDecimal value
    ) {

        this.processingFeeRate = value;

        return this;
    }


    public LoanBuilder processingFee(
            Double value
    ) {

        this.processingFee =
                value == null
                        ? null
                        : BigDecimal.valueOf(value);

        return this;
    }


    public LoanBuilder processingFee(
            BigDecimal value
    ) {

        this.processingFee = value;

        return this;
    }


    public LoanBuilder collateralValue(
            Double value
    ) {

        this.collateralValue =
                value == null
                        ? null
                        : BigDecimal.valueOf(value);

        return this;
    }


    public LoanBuilder collateralValue(
            BigDecimal value
    ) {

        this.collateralValue = value;

        return this;
    }


    public LoanBuilder riskScore(
            Double value
    ) {

        this.riskScore =
                value == null
                        ? null
                        : BigDecimal.valueOf(value);

        return this;
    }


    public LoanBuilder riskScore(
            BigDecimal value
    ) {

        this.riskScore = value;

        return this;
    }


    public LoanBuilder debtToIncomeRatio(
            Double value
    ) {

        this.debtToIncomeRatio =
                value == null
                        ? null
                        : BigDecimal.valueOf(value);

        return this;
    }


    public LoanBuilder debtToIncomeRatio(
            BigDecimal value
    ) {

        this.debtToIncomeRatio = value;

        return this;
    }
}


}
