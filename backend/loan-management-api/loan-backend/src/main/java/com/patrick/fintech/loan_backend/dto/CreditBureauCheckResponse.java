package com.patrick.fintech.loan_backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.patrick.fintech.loan_backend.model.CreditBureauCheck;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditBureauCheckResponse {


    private Long id;

    private String reference;

    private String provider;

    private CreditBureauCheck.CheckStatus status;


   
    // private Long borrowerId;
    // private String borrowerName;

    private String nationalIdChecked;


    private Integer creditScore;

    private String riskGrade;


    private Integer activeFacilities;

    private Integer delinquentAccounts;

    

    @JsonProperty("totalOutstandingDebt")
private BigDecimal totalOutstandingDebt;

    

    @JsonProperty("totalMonthlyObligations")
private BigDecimal totalMonthlyObligations;


    private Boolean hasDefaultHistory;

    private Boolean hasActiveListing;

    private String listingReason;


    private String requestedBy;

    private String failureReason;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;


    private Boolean valid;

    private Boolean expired;

    @Deprecated
    @JsonIgnore
    public Double getTotalOutstandingDebt() {
        return totalOutstandingDebt == null ? null : totalOutstandingDebt.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalOutstandingDebtDecimal() {
        return totalOutstandingDebt;
    }

    @Deprecated
    public void setTotalOutstandingDebt(Double value) {
        this.totalOutstandingDebt = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalOutstandingDebt(BigDecimal value) {
        this.totalOutstandingDebt = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getTotalMonthlyObligations() {
        return totalMonthlyObligations == null ? null : totalMonthlyObligations.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalMonthlyObligationsDecimal() {
        return totalMonthlyObligations;
    }

    @Deprecated
    public void setTotalMonthlyObligations(Double value) {
        this.totalMonthlyObligations = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalMonthlyObligations(BigDecimal value) {
        this.totalMonthlyObligations = value;
    }

    /** Backward-compatible Double builder overloads; state remains BigDecimal. */
    public static class CreditBureauCheckResponseBuilder {
        private BigDecimal totalMonthlyObligations;
        private BigDecimal totalOutstandingDebt;

        public CreditBureauCheckResponseBuilder totalOutstandingDebt(Double value) {
            this.totalOutstandingDebt = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public CreditBureauCheckResponseBuilder totalOutstandingDebt(BigDecimal value) {
            this.totalOutstandingDebt = value;
            return this;
        }
        public CreditBureauCheckResponseBuilder totalMonthlyObligations(Double value) {
            this.totalMonthlyObligations = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public CreditBureauCheckResponseBuilder totalMonthlyObligations(BigDecimal value) {
            this.totalMonthlyObligations = value;
            return this;
        }
    }
}
