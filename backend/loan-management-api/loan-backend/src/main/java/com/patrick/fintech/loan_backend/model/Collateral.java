package com.patrick.fintech.loan_backend.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "collaterals", indexes = @Index(name = "idx_collateral_loan", columnList = "loan_id"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Collateral {

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

    @Enumerated(EnumType.STRING)
    private CollateralType type;

    private String description;
    private String ownerName;
    @Column(precision = 19, scale = 6)
    @JsonProperty("estimatedValue")
    private BigDecimal estimatedValue;
    private String currency;

    private Boolean insured;
    private LocalDate insuranceExpiryDate;
    private String insurancePolicyNumber;

    private String documentUrl;       // title deed, logbook, valuation report, photo, etc.
    private String registrationNumber; // plate number, land title number, serial number, etc.

    @Enumerated(EnumType.STRING)
    private CollateralStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now();
        if (status == null) status = CollateralStatus.PLEDGED;
        if (insured == null) insured = false;
    }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum CollateralType { PROPERTY, VEHICLE, LAND, EQUIPMENT, CASH_DEPOSIT, SHARES, OTHER }
    public enum CollateralStatus { PLEDGED, RELEASED, SEIZED, LIQUIDATED }
    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getEstimatedValueDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getEstimatedValue() {
        return estimatedValue == null ? null : estimatedValue.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getEstimatedValueDecimal() {
        return estimatedValue;
    }

    @Deprecated
    public void setEstimatedValue(Double value) {
        this.estimatedValue = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setEstimatedValue(BigDecimal value) {
        this.estimatedValue = value;
    }

    /** Backward-compatible builder overloads for legacy Double callers.
     *  Financial state is stored as BigDecimal.
     */
    public static class CollateralBuilder {
        private BigDecimal estimatedValue;


        public CollateralBuilder estimatedValue(Double value) {
            this.estimatedValue = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }        public CollateralBuilder estimatedValue(BigDecimal value) {
            this.estimatedValue = value;
            return this;
        }
    }

}
