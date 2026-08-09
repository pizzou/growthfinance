package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(nullable = false)
    private Integer installmentNumber;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 19, scale = 6)
    @JsonProperty("installmentAmount")
    private BigDecimal installmentAmount;

    @Column(nullable = false, precision = 19, scale = 6)
    @JsonProperty("principalAmount")
    private BigDecimal principalAmount;

    @Column(nullable =false, precision = 19, scale = 6)
    @JsonProperty("interestAmount")
    private BigDecimal interestAmount;

    @Builder.Default
    @JsonProperty("penaltyAmount")
    @Column(precision = 19, scale = 6)
    private BigDecimal penaltyAmount = BigDecimal.ZERO;

    @Builder.Default
    @JsonProperty("amountPaid")
    @Column(precision = 19, scale = 6)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Builder.Default
    @JsonProperty("remainingBalance")
    @Column(precision = 19, scale = 6)
    private BigDecimal remainingBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private ScheduleStatus status;

    private LocalDate paidDate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {

        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if(status==null){
            status = ScheduleStatus.PENDING;
        }

        if(amountPaid==null){
            amountPaid = BigDecimal.valueOf(0.0);
        }

        if(penaltyAmount==null){
            penaltyAmount = BigDecimal.valueOf(0.0);
        }

    }

    @PreUpdate
    public void onUpdate(){
        updatedAt = LocalDateTime.now();
    }

    public enum ScheduleStatus{
        PENDING,
        PAID,
        PARTIAL,
        OVERDUE
    }

    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getInstallmentAmountDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getInstallmentAmount() {
        return installmentAmount == null ? null : installmentAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getInstallmentAmountDecimal() {
        return installmentAmount;
    }

    @Deprecated
    public void setInstallmentAmount(Double value) {
        this.installmentAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setInstallmentAmount(BigDecimal value) {
        this.installmentAmount = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getPrincipalAmountDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getPrincipalAmount() {
        return principalAmount == null ? null : principalAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getPrincipalAmountDecimal() {
        return principalAmount;
    }

    @Deprecated
    public void setPrincipalAmount(Double value) {
        this.principalAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setPrincipalAmount(BigDecimal value) {
        this.principalAmount = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getInterestAmountDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getInterestAmount() {
        return interestAmount == null ? null : interestAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getInterestAmountDecimal() {
        return interestAmount;
    }

    @Deprecated
    public void setInterestAmount(Double value) {
        this.interestAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setInterestAmount(BigDecimal value) {
        this.interestAmount = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getPenaltyAmountDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getPenaltyAmount() {
        return penaltyAmount == null ? null : penaltyAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getPenaltyAmountDecimal() {
        return penaltyAmount;
    }

    @Deprecated
    public void setPenaltyAmount(Double value) {
        this.penaltyAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setPenaltyAmount(BigDecimal value) {
        this.penaltyAmount = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getAmountPaidDecimal().
     */
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


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getRemainingBalanceDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getRemainingBalance() {
        return remainingBalance == null ? null : remainingBalance.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getRemainingBalanceDecimal() {
        return remainingBalance;
    }

    @Deprecated
    public void setRemainingBalance(Double value) {
        this.remainingBalance = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setRemainingBalance(BigDecimal value) {
        this.remainingBalance = value;
    }

    /** Backward-compatible builder overloads for legacy Double callers.
     *  Financial state is stored as BigDecimal.
     */
    public static class PaymentScheduleBuilder {

        private BigDecimal installmentAmount;
        private BigDecimal principalAmount;
        private BigDecimal interestAmount;
        private BigDecimal penaltyAmount;
        private BigDecimal amountPaid;
        private BigDecimal remainingBalance;


        public PaymentScheduleBuilder installmentAmount(Double value) {
            this.installmentAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentScheduleBuilder principalAmount(Double value) {
            this.principalAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentScheduleBuilder interestAmount(Double value) {
            this.interestAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentScheduleBuilder penaltyAmount(Double value) {
            this.penaltyAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentScheduleBuilder amountPaid(Double value) {
            this.amountPaid = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentScheduleBuilder remainingBalance(Double value) {
            this.remainingBalance = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }        public PaymentScheduleBuilder installmentAmount(BigDecimal value) {
            this.installmentAmount = value;
            return this;
        }
        public PaymentScheduleBuilder principalAmount(BigDecimal value) {
            this.principalAmount = value;
            return this;
        }
        public PaymentScheduleBuilder interestAmount(BigDecimal value) {
            this.interestAmount = value;
            return this;
        }
        public PaymentScheduleBuilder penaltyAmount(BigDecimal value) {
            this.penaltyAmount = value;
            return this;
        }
        public PaymentScheduleBuilder amountPaid(BigDecimal value) {
            this.amountPaid = value;
            return this;
        }
        public PaymentScheduleBuilder remainingBalance(BigDecimal value) {
            this.remainingBalance = value;
            return this;
        }
    }

}
