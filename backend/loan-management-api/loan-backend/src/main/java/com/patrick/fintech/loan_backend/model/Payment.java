package com.patrick.fintech.loan_backend.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
        name = "payments",
        indexes = {
                @Index(name = "idx_payment_loan", columnList = "loan_id"),
                @Index(name = "idx_payment_due", columnList = "due_date"),
                @Index(name = "idx_payment_paid_date", columnList = "paid_date"),
                @Index(name = "idx_payment_status", columnList = "status"),
                @Index(name = "idx_payment_org", columnList = "organization_id"),
                @Index(name = "idx_payment_transaction", columnList = "transaction_id"),
                @Index(name = "idx_payment_interest_date", columnList = "interest_calculation_date")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    

    @Column(
            name = "payment_reference",
            unique = true,
            length = 100
    )
    private String paymentReference;



    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "loan_id",
            nullable = false
    )
    private Loan loan;


    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "organization_id",
            nullable = false
    )
    private Organization organization;


    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;


   

    @Column(name = "installment_number")
    private Integer installmentNumber;

    @Column(name = "amount", precision = 19, scale = 6)
    @JsonProperty("amount")
    private BigDecimal amount;

    @Column(name = "principal_component", precision = 19, scale = 6)
    @JsonProperty("principalComponent")
    private BigDecimal principalComponent;


    @Column(name = "interest_component", precision = 19, scale = 6)
    @JsonProperty("interestComponent")
    private BigDecimal interestComponent;

    @Column(name = "amount_paid", precision = 19, scale = 6)
    @JsonProperty("amountPaid")
    private BigDecimal amountPaid;


    @Column(name = "penalty", precision = 19, scale = 6)
    @Builder.Default
    @JsonProperty("penalty")
    private BigDecimal penalty = BigDecimal.ZERO;


    @Column(name = "waived_amount", precision = 19, scale = 6)
    @Builder.Default
    @JsonProperty("waivedAmount")
    private BigDecimal waivedAmount = BigDecimal.ZERO;


    @Column(name = "outstanding_after", precision = 19, scale = 6)
    @JsonProperty("outstandingAfter")
    private BigDecimal outstandingAfter;



    @Column(name = "paid")
    @Builder.Default
    private Boolean paid = false;


    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            length = 30
    )
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;


    
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "paid_date")
    private LocalDate paidDate;


    @Column(name = "interest_calculation_date")
    private LocalDateTime interestCalculationDate;

    @Column(name = "days_late")
    @Builder.Default
    private Integer daysLate = 0;


    @Column(name = "is_late")
    @Builder.Default
    private boolean isLate = false;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;


    @Column(name = "transaction_id", length = 150)
    private String transactionId;


    @Column(name = "external_reference", length = 150)
    private String externalReference;


    @Column(
            name = "gateway_response",
            columnDefinition = "TEXT"
    )
    private String gatewayResponse;


    @Column(name = "channel", length = 50)
    private String channel;


    @Column(
            name = "notes",
            columnDefinition = "TEXT"
    )
    private String notes;



    @Column(name = "created_at")
    private LocalDateTime createdAt;


    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;


    @Column(name = "cycle_interest_due", precision = 19, scale = 6)
    @JsonProperty("cycleInterestDue")
    private BigDecimal cycleInterestDue;

    @Column(name = "cycle_interest_remaining", precision = 19, scale = 6)
    @JsonProperty("cycleInterestRemaining")
    private BigDecimal cycleInterestRemaining;



    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (paid == null) {
            paid = false;
        }

        if (penalty == null) {
            penalty = BigDecimal.valueOf(0.0);
        }

        if (waivedAmount == null) {
            waivedAmount = BigDecimal.valueOf(0.0);
        }

        if (daysLate == null) {
            daysLate = 0;
        }

        if (status == null) {
            status = PaymentStatus.PENDING;
        }

        if (amountPaid == null) {
            amountPaid = BigDecimal.valueOf(0.0);
        }

        if (principalComponent == null) {
            principalComponent = BigDecimal.valueOf(0.0);
        }

        if (interestComponent == null) {
            interestComponent = BigDecimal.valueOf(0.0);
        }

        if (cycleInterestDue == null) {
            cycleInterestDue = BigDecimal.valueOf(0.0);
        }

        if (cycleInterestRemaining == null) {
            cycleInterestRemaining = BigDecimal.valueOf(0.0);
        }

        if (daysLate > 0) {
            isLate = true;
        }
    }


    @PreUpdate
    protected void onUpdate() {

        if (daysLate != null && daysLate > 0) {
            isLate = true;
        }

        if (cycleInterestDue == null) {
            cycleInterestDue = BigDecimal.valueOf(0.0);
        }

        if (cycleInterestRemaining == null) {
            cycleInterestRemaining = BigDecimal.valueOf(0.0);
        }
    }


    public enum PaymentStatus {

        PENDING,

        COMPLETED,

        FAILED,

        REVERSED,

        PARTIALLY_PAID
    }
   
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


    @Deprecated
    @JsonIgnore
    public Double getPrincipalComponent() {
        return principalComponent == null ? null : principalComponent.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getPrincipalComponentDecimal() {
        return principalComponent;
    }

    @Deprecated
    public void setPrincipalComponent(Double value) {
        this.principalComponent = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setPrincipalComponent(BigDecimal value) {
        this.principalComponent = value;
    }


   
    @Deprecated
    @JsonIgnore
    public Double getInterestComponent() {
        return interestComponent == null ? null : interestComponent.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getInterestComponentDecimal() {
        return interestComponent;
    }

    @Deprecated
    public void setInterestComponent(Double value) {
        this.interestComponent = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setInterestComponent(BigDecimal value) {
        this.interestComponent = value;
    }


    @Deprecated
    @JsonIgnore
    public Double getAmountPaid() {
        return amountPaid == null ? null : amountPaid.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getAmountPaidDecimal() {
        return amountPaid;
    }

    @Deprecated
    public void setAmountPaid(Double value) {
        this.amountPaid = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setAmountPaid(BigDecimal value) {
        this.amountPaid = value;
    }


    @Deprecated
    @JsonIgnore
    public Double getPenalty() {
        return penalty == null ? null : penalty.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getPenaltyDecimal() {
        return penalty;
    }

    @Deprecated
    public void setPenalty(Double value) {
        this.penalty = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setPenalty(BigDecimal value) {
        this.penalty = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getWaivedAmountDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getWaivedAmount() {
        return waivedAmount == null ? null : waivedAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getWaivedAmountDecimal() {
        return waivedAmount;
    }

    @Deprecated
    public void setWaivedAmount(Double value) {
        this.waivedAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setWaivedAmount(BigDecimal value) {
        this.waivedAmount = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getOutstandingAfterDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getOutstandingAfter() {
        return outstandingAfter == null ? null : outstandingAfter.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getOutstandingAfterDecimal() {
        return outstandingAfter;
    }

    @Deprecated
    public void setOutstandingAfter(Double value) {
        this.outstandingAfter = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setOutstandingAfter(BigDecimal value) {
        this.outstandingAfter = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getCycleInterestDueDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getCycleInterestDue() {
        return cycleInterestDue == null ? null : cycleInterestDue.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getCycleInterestDueDecimal() {
        return cycleInterestDue;
    }

    @Deprecated
    public void setCycleInterestDue(Double value) {
        this.cycleInterestDue = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setCycleInterestDue(BigDecimal value) {
        this.cycleInterestDue = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getCycleInterestRemainingDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getCycleInterestRemaining() {
        return cycleInterestRemaining == null ? null : cycleInterestRemaining.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getCycleInterestRemainingDecimal() {
        return cycleInterestRemaining;
    }

    @Deprecated
    public void setCycleInterestRemaining(Double value) {
        this.cycleInterestRemaining = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setCycleInterestRemaining(BigDecimal value) {
        this.cycleInterestRemaining = value;
    }

    /** Backward-compatible builder overloads for legacy Double callers.
     *  Financial state is stored as BigDecimal.
     */
    public static class PaymentBuilder {

        private BigDecimal amount;
        private BigDecimal principalComponent;
        private BigDecimal interestComponent;
        private BigDecimal amountPaid;
        private BigDecimal penalty;
        private BigDecimal waivedAmount;
        private BigDecimal outstandingAfter;
        private BigDecimal cycleInterestDue;
        private BigDecimal cycleInterestRemaining;


        public PaymentBuilder amount(Double value) {
            this.amount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentBuilder principalComponent(Double value) {
            this.principalComponent = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentBuilder interestComponent(Double value) {
            this.interestComponent = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentBuilder amountPaid(Double value) {
            this.amountPaid = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentBuilder penalty(Double value) {
            this.penalty = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentBuilder waivedAmount(Double value) {
            this.waivedAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentBuilder outstandingAfter(Double value) {
            this.outstandingAfter = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentBuilder cycleInterestDue(Double value) {
            this.cycleInterestDue = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentBuilder cycleInterestRemaining(Double value) {
            this.cycleInterestRemaining = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }        public PaymentBuilder amount(BigDecimal value) {
            this.amount = value;
            return this;
        }
        public PaymentBuilder principalComponent(BigDecimal value) {
            this.principalComponent = value;
            return this;
        }
        public PaymentBuilder interestComponent(BigDecimal value) {
            this.interestComponent = value;
            return this;
        }
        public PaymentBuilder amountPaid(BigDecimal value) {
            this.amountPaid = value;
            return this;
        }
        public PaymentBuilder penalty(BigDecimal value) {
            this.penalty = value;
            return this;
        }
        public PaymentBuilder waivedAmount(BigDecimal value) {
            this.waivedAmount = value;
            return this;
        }
        public PaymentBuilder outstandingAfter(BigDecimal value) {
            this.outstandingAfter = value;
            return this;
        }
        public PaymentBuilder cycleInterestDue(BigDecimal value) {
            this.cycleInterestDue = value;
            return this;
        }
        public PaymentBuilder cycleInterestRemaining(BigDecimal value) {
            this.cycleInterestRemaining = value;
            return this;
        }
    }

}
