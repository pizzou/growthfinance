package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.patrick.fintech.loan_backend.util.MoneyMath;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_payment_loan", columnList = "loan_id"),
        @Index(name = "idx_payment_due", columnList = "due_date"),
        @Index(name = "idx_payment_org", columnList = "organization_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    // ============================================================
    // ID
    // ============================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // ============================================================
    // PAYMENT REFERENCE
    // ============================================================

    @Column(unique = true)
    private String paymentReference;


    // ============================================================
    // RELATIONSHIPS
    // ============================================================

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;


    // ============================================================
    // INSTALLMENT INFORMATION
    // ============================================================

    private Integer installmentNumber;


    // ============================================================
    // FINANCIAL FIELDS
    //
    // All monetary values use BigDecimal.
    // ============================================================

    @Column(
        precision = 19,
        scale = 2
    )
    private BigDecimal amount;

    @Column(
        precision = 19,
        scale = 2
    )
    private BigDecimal principalComponent;

    @Column(
        precision = 19,
        scale = 2
    )
    private BigDecimal interestComponent;

    @Column(
        precision = 19,
        scale = 2
    )
    private BigDecimal amountPaid;

    @Builder.Default
    @Column(
        precision = 19,
        scale = 2
    )
    private BigDecimal penalty = MoneyMath.ZERO;

    @Builder.Default
    @Column(
        precision = 19,
        scale = 2
    )
    private BigDecimal waivedAmount = MoneyMath.ZERO;

    @Column(
        precision = 19,
        scale = 2
    )
    private BigDecimal outstandingAfter;


    // ============================================================
    // PAYMENT STATUS
    // ============================================================

    @Builder.Default
    private Boolean paid = false;

    private LocalDate dueDate;

    private LocalDate paidDate;


    // ============================================================
    // PAYMENT DETAILS
    // ============================================================

    private String paymentMethod;

    private String transactionId;

    private String externalReference;

    private String gatewayResponse;

    private String channel;

    private String notes;


    // ============================================================
    // LATE PAYMENT INFORMATION
    // ============================================================

    @Builder.Default
    private boolean isLate = false;

    @Builder.Default
    private Integer daysLate = 0;


    // ============================================================
    // STATUS
    // ============================================================

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;


    // ============================================================
    // AUDIT DATES
    // ============================================================

    private LocalDateTime createdAt;

    private LocalDateTime verifiedAt;


    // ============================================================
    // JPA LIFECYCLE
    // ============================================================

    @PrePersist
    protected void onCreate() {

        createdAt = LocalDateTime.now();

        if (paid == null) {
            paid = false;
        }

        if (penalty == null) {
            penalty = MoneyMath.ZERO;
        }

        if (waivedAmount == null) {
            waivedAmount = MoneyMath.ZERO;
        }

        if (daysLate == null) {
            daysLate = 0;
        }

        if (status == null) {
            status = PaymentStatus.PENDING;
        }
    }


    @PreUpdate
    protected void onUpdate() {
        // Reserved for future payment update auditing.
    }


    // ============================================================
    // BIGDECIMAL SETTERS
    //
    // These are the authoritative financial setters.
    // PaymentService can safely pass BigDecimal values.
    // ============================================================

    public void setAmount(BigDecimal value) {
        this.amount = normalize(value);
    }

    public void setPrincipalComponent(BigDecimal value) {
        this.principalComponent = normalize(value);
    }

    public void setInterestComponent(BigDecimal value) {
        this.interestComponent = normalize(value);
    }

    public void setAmountPaid(BigDecimal value) {
        this.amountPaid = normalize(value);
    }

    public void setPenalty(BigDecimal value) {
        this.penalty = normalize(value);
    }

    public void setWaivedAmount(BigDecimal value) {
        this.waivedAmount = normalize(value);
    }

    public void setOutstandingAfter(BigDecimal value) {
        this.outstandingAfter = normalize(value);
    }


    // ============================================================
    // DOUBLE COMPATIBILITY SETTERS
    //
    // Keep these only for old controllers/integrations that still
    // send Double values.
    //
    // New financial code should always use BigDecimal.
    // ============================================================

    public void setAmount(Double value) {
        this.amount = value == null
            ? null
            : BigDecimal.valueOf(value);
    }

    public void setPrincipalComponent(Double value) {
        this.principalComponent = value == null
            ? null
            : BigDecimal.valueOf(value);
    }

    public void setInterestComponent(Double value) {
        this.interestComponent = value == null
            ? null
            : BigDecimal.valueOf(value);
    }

    public void setAmountPaid(Double value) {
        this.amountPaid = value == null
            ? null
            : BigDecimal.valueOf(value);
    }

    public void setPenalty(Double value) {
        this.penalty = value == null
            ? null
            : BigDecimal.valueOf(value);
    }

    public void setWaivedAmount(Double value) {
        this.waivedAmount = value == null
            ? null
            : BigDecimal.valueOf(value);
    }

    public void setOutstandingAfter(Double value) {
        this.outstandingAfter = value == null
            ? null
            : BigDecimal.valueOf(value);
    }


    // ============================================================
    // DOUBLE READ-ONLY COMPATIBILITY ACCESSORS
    //
    // These do not affect persistence.
    // ============================================================

    public Double getAmountDouble() {
        return amount == null
            ? null
            : amount.doubleValue();
    }

    public Double getPrincipalComponentDouble() {
        return principalComponent == null
            ? null
            : principalComponent.doubleValue();
    }

    public Double getInterestComponentDouble() {
        return interestComponent == null
            ? null
            : interestComponent.doubleValue();
    }

    public Double getAmountPaidDouble() {
        return amountPaid == null
            ? null
            : amountPaid.doubleValue();
    }

    public Double getPenaltyDouble() {
        return penalty == null
            ? null
            : penalty.doubleValue();
    }

    public Double getWaivedAmountDouble() {
        return waivedAmount == null
            ? null
            : waivedAmount.doubleValue();
    }

    public Double getOutstandingAfterDouble() {
        return outstandingAfter == null
            ? null
            : outstandingAfter.doubleValue();
    }


    // ============================================================
    // MONEY NORMALIZATION
    // ============================================================

    private BigDecimal normalize(BigDecimal value) {

        if (value == null) {
            return null;
        }

        return value.setScale(
            MoneyMath.SCALE,
            MoneyMath.ROUNDING
        );
    }


    // ============================================================
    // ENUM
    // ============================================================

    public enum PaymentStatus {

        PENDING,

        COMPLETED,

        FAILED,

        REVERSED,

        PARTIALLY_PAID
    }
}
