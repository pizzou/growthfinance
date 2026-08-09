package com.patrick.fintech.loan_backend.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "guarantors", indexes = @Index(name = "idx_guarantor_loan", columnList = "loan_id"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Guarantor {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    private String fullName;

    @Convert(converter = com.patrick.fintech.loan_backend.security.CryptoConverter.class)
    private String nationalId;
    @Convert(converter = com.patrick.fintech.loan_backend.security.CryptoConverter.class)
    private String phone;
    @Convert(converter = com.patrick.fintech.loan_backend.security.CryptoConverter.class)
    private String address;

    private String relationship;      // e.g. Spouse, Sibling, Colleague, Friend
    private String employerName;
    @Column(precision = 19, scale = 6)
    @JsonProperty("monthlyIncome")
    private BigDecimal monthlyIncome;
    @Column(precision = 19, scale = 6)
    @JsonProperty("guaranteedAmount")

    private BigDecimal guaranteedAmount;  // how much of the loan this guarantor is on the hook for
    private Boolean consentGiven;
    private String documentUrl;       // scanned signed guarantee form, if uploaded

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (consentGiven == null) consentGiven = false;
    }
    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getMonthlyIncomeDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getMonthlyIncome() {
        return monthlyIncome == null ? null : monthlyIncome.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getMonthlyIncomeDecimal() {
        return monthlyIncome;
    }

    @Deprecated
    public void setMonthlyIncome(Double value) {
        this.monthlyIncome = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setMonthlyIncome(BigDecimal value) {
        this.monthlyIncome = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getGuaranteedAmountDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getGuaranteedAmount() {
        return guaranteedAmount == null ? null : guaranteedAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getGuaranteedAmountDecimal() {
        return guaranteedAmount;
    }

    @Deprecated
    public void setGuaranteedAmount(Double value) {
        this.guaranteedAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setGuaranteedAmount(BigDecimal value) {
        this.guaranteedAmount = value;
    }

    /** Backward-compatible builder overloads for legacy Double callers.
     *  Financial state is stored as BigDecimal.
     */
    public static class GuarantorBuilder {
        private BigDecimal guaranteedAmount;
        private BigDecimal monthlyIncome;


        public GuarantorBuilder monthlyIncome(Double value) {
            this.monthlyIncome = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public GuarantorBuilder guaranteedAmount(Double value) {
            this.guaranteedAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }        public GuarantorBuilder monthlyIncome(BigDecimal value) {
            this.monthlyIncome = value;
            return this;
        }
        public GuarantorBuilder guaranteedAmount(BigDecimal value) {
            this.guaranteedAmount = value;
            return this;
        }
    }

}
