package com.patrick.fintech.loan_backend.dto.regulatory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BnrFinancialStatementReport {

    // ============================================================
    // REPORT INFORMATION
    // ============================================================

    private Long organizationId;

    private String organizationName;

    private String bnrInstitutionCode;

    private Long branchId;

    private String branchName;

    private String currency;

    private String reportPeriod;

    private LocalDate periodStart;

    private LocalDate periodEnd;

    private LocalDateTime generatedAt;


    // ============================================================
    // STATEMENT OF FINANCIAL POSITION
    // ============================================================

    @Builder.Default
    private List<Map<String, Object>> assets =
            new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> liabilities =
            new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> equity =
            new ArrayList<>();

    

    @JsonProperty("totalAssets")
private BigDecimal totalAssets;

    

    @JsonProperty("totalLiabilities")
private BigDecimal totalLiabilities;

    

    @JsonProperty("totalEquity")
private BigDecimal totalEquity;

    

    @JsonProperty("currentPeriodNetIncome")
private BigDecimal currentPeriodNetIncome;

    private boolean balanceSheetBalanced;


    // ============================================================
    // INCOME STATEMENT
    // ============================================================

    @Builder.Default
    private List<Map<String, Object>> income =
            new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> expenses =
            new ArrayList<>();

    

    @JsonProperty("totalIncome")
private BigDecimal totalIncome;

    

    @JsonProperty("totalExpenses")
private BigDecimal totalExpenses;

    

    @JsonProperty("netIncome")
private BigDecimal netIncome;


    // ============================================================
    // TRIAL BALANCE
    // ============================================================

    

    @JsonProperty("trialBalanceDebit")
private BigDecimal trialBalanceDebit;

    

    @JsonProperty("trialBalanceCredit")
private BigDecimal trialBalanceCredit;

    private boolean trialBalanceBalanced;


    // ============================================================
    // CASH FLOW
    // ============================================================

    

    @JsonProperty("cashUsedForLending")
private BigDecimal cashUsedForLending;

    

    @JsonProperty("cashFromCollections")
private BigDecimal cashFromCollections;

    

    @JsonProperty("cashFromFees")
private BigDecimal cashFromFees;

    

    @JsonProperty("otherCashMovement")
private BigDecimal otherCashMovement;

    

    @JsonProperty("netChangeInCash")
private BigDecimal netChangeInCash;

    @Deprecated
    @JsonIgnore
    public Double getTotalAssets() {
        return totalAssets == null ? null : totalAssets.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalAssetsDecimal() {
        return totalAssets;
    }

    @Deprecated
    public void setTotalAssets(Double value) {
        this.totalAssets = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalAssets(BigDecimal value) {
        this.totalAssets = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getTotalLiabilities() {
        return totalLiabilities == null ? null : totalLiabilities.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalLiabilitiesDecimal() {
        return totalLiabilities;
    }

    @Deprecated
    public void setTotalLiabilities(Double value) {
        this.totalLiabilities = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalLiabilities(BigDecimal value) {
        this.totalLiabilities = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getTotalEquity() {
        return totalEquity == null ? null : totalEquity.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalEquityDecimal() {
        return totalEquity;
    }

    @Deprecated
    public void setTotalEquity(Double value) {
        this.totalEquity = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalEquity(BigDecimal value) {
        this.totalEquity = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getCurrentPeriodNetIncome() {
        return currentPeriodNetIncome == null ? null : currentPeriodNetIncome.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getCurrentPeriodNetIncomeDecimal() {
        return currentPeriodNetIncome;
    }

    @Deprecated
    public void setCurrentPeriodNetIncome(Double value) {
        this.currentPeriodNetIncome = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setCurrentPeriodNetIncome(BigDecimal value) {
        this.currentPeriodNetIncome = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getTotalIncome() {
        return totalIncome == null ? null : totalIncome.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalIncomeDecimal() {
        return totalIncome;
    }

    @Deprecated
    public void setTotalIncome(Double value) {
        this.totalIncome = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalIncome(BigDecimal value) {
        this.totalIncome = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getTotalExpenses() {
        return totalExpenses == null ? null : totalExpenses.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalExpensesDecimal() {
        return totalExpenses;
    }

    @Deprecated
    public void setTotalExpenses(Double value) {
        this.totalExpenses = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalExpenses(BigDecimal value) {
        this.totalExpenses = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getNetIncome() {
        return netIncome == null ? null : netIncome.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getNetIncomeDecimal() {
        return netIncome;
    }

    @Deprecated
    public void setNetIncome(Double value) {
        this.netIncome = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setNetIncome(BigDecimal value) {
        this.netIncome = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getTrialBalanceDebit() {
        return trialBalanceDebit == null ? null : trialBalanceDebit.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTrialBalanceDebitDecimal() {
        return trialBalanceDebit;
    }

    @Deprecated
    public void setTrialBalanceDebit(Double value) {
        this.trialBalanceDebit = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTrialBalanceDebit(BigDecimal value) {
        this.trialBalanceDebit = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getTrialBalanceCredit() {
        return trialBalanceCredit == null ? null : trialBalanceCredit.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTrialBalanceCreditDecimal() {
        return trialBalanceCredit;
    }

    @Deprecated
    public void setTrialBalanceCredit(Double value) {
        this.trialBalanceCredit = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTrialBalanceCredit(BigDecimal value) {
        this.trialBalanceCredit = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getCashUsedForLending() {
        return cashUsedForLending == null ? null : cashUsedForLending.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getCashUsedForLendingDecimal() {
        return cashUsedForLending;
    }

    @Deprecated
    public void setCashUsedForLending(Double value) {
        this.cashUsedForLending = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setCashUsedForLending(BigDecimal value) {
        this.cashUsedForLending = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getCashFromCollections() {
        return cashFromCollections == null ? null : cashFromCollections.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getCashFromCollectionsDecimal() {
        return cashFromCollections;
    }

    @Deprecated
    public void setCashFromCollections(Double value) {
        this.cashFromCollections = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setCashFromCollections(BigDecimal value) {
        this.cashFromCollections = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getCashFromFees() {
        return cashFromFees == null ? null : cashFromFees.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getCashFromFeesDecimal() {
        return cashFromFees;
    }

    @Deprecated
    public void setCashFromFees(Double value) {
        this.cashFromFees = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setCashFromFees(BigDecimal value) {
        this.cashFromFees = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getOtherCashMovement() {
        return otherCashMovement == null ? null : otherCashMovement.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getOtherCashMovementDecimal() {
        return otherCashMovement;
    }

    @Deprecated
    public void setOtherCashMovement(Double value) {
        this.otherCashMovement = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setOtherCashMovement(BigDecimal value) {
        this.otherCashMovement = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getNetChangeInCash() {
        return netChangeInCash == null ? null : netChangeInCash.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getNetChangeInCashDecimal() {
        return netChangeInCash;
    }

    @Deprecated
    public void setNetChangeInCash(Double value) {
        this.netChangeInCash = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setNetChangeInCash(BigDecimal value) {
        this.netChangeInCash = value;
    }

    /** Backward-compatible Double builder overloads; state remains BigDecimal. */
    public static class BnrFinancialStatementReportBuilder {
        private BigDecimal cashFromCollections;
        private BigDecimal cashFromFees;
        private BigDecimal cashUsedForLending;
        private BigDecimal currentPeriodNetIncome;
        private BigDecimal netChangeInCash;
        private BigDecimal netIncome;
        private BigDecimal otherCashMovement;
        private BigDecimal totalAssets;
        private BigDecimal totalEquity;
        private BigDecimal totalExpenses;
        private BigDecimal totalIncome;
        private BigDecimal totalLiabilities;
        private BigDecimal trialBalanceCredit;
        private BigDecimal trialBalanceDebit;

        public BnrFinancialStatementReportBuilder totalAssets(Double value) {
            this.totalAssets = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrFinancialStatementReportBuilder totalAssets(BigDecimal value) {
            this.totalAssets = value;
            return this;
        }
        public BnrFinancialStatementReportBuilder totalLiabilities(Double value) {
            this.totalLiabilities = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrFinancialStatementReportBuilder totalLiabilities(BigDecimal value) {
            this.totalLiabilities = value;
            return this;
        }
        public BnrFinancialStatementReportBuilder totalEquity(Double value) {
            this.totalEquity = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrFinancialStatementReportBuilder totalEquity(BigDecimal value) {
            this.totalEquity = value;
            return this;
        }
        public BnrFinancialStatementReportBuilder currentPeriodNetIncome(Double value) {
            this.currentPeriodNetIncome = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrFinancialStatementReportBuilder currentPeriodNetIncome(BigDecimal value) {
            this.currentPeriodNetIncome = value;
            return this;
        }
        public BnrFinancialStatementReportBuilder totalIncome(Double value) {
            this.totalIncome = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrFinancialStatementReportBuilder totalIncome(BigDecimal value) {
            this.totalIncome = value;
            return this;
        }
        public BnrFinancialStatementReportBuilder totalExpenses(Double value) {
            this.totalExpenses = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrFinancialStatementReportBuilder totalExpenses(BigDecimal value) {
            this.totalExpenses = value;
            return this;
        }
        public BnrFinancialStatementReportBuilder netIncome(Double value) {
            this.netIncome = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrFinancialStatementReportBuilder netIncome(BigDecimal value) {
            this.netIncome = value;
            return this;
        }
        public BnrFinancialStatementReportBuilder trialBalanceDebit(Double value) {
            this.trialBalanceDebit = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrFinancialStatementReportBuilder trialBalanceDebit(BigDecimal value) {
            this.trialBalanceDebit = value;
            return this;
        }
        public BnrFinancialStatementReportBuilder trialBalanceCredit(Double value) {
            this.trialBalanceCredit = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrFinancialStatementReportBuilder trialBalanceCredit(BigDecimal value) {
            this.trialBalanceCredit = value;
            return this;
        }
        public BnrFinancialStatementReportBuilder cashUsedForLending(Double value) {
            this.cashUsedForLending = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrFinancialStatementReportBuilder cashUsedForLending(BigDecimal value) {
            this.cashUsedForLending = value;
            return this;
        }
        public BnrFinancialStatementReportBuilder cashFromCollections(Double value) {
            this.cashFromCollections = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrFinancialStatementReportBuilder cashFromCollections(BigDecimal value) {
            this.cashFromCollections = value;
            return this;
        }
        public BnrFinancialStatementReportBuilder cashFromFees(Double value) {
            this.cashFromFees = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrFinancialStatementReportBuilder cashFromFees(BigDecimal value) {
            this.cashFromFees = value;
            return this;
        }
        public BnrFinancialStatementReportBuilder otherCashMovement(Double value) {
            this.otherCashMovement = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrFinancialStatementReportBuilder otherCashMovement(BigDecimal value) {
            this.otherCashMovement = value;
            return this;
        }
        public BnrFinancialStatementReportBuilder netChangeInCash(Double value) {
            this.netChangeInCash = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrFinancialStatementReportBuilder netChangeInCash(BigDecimal value) {
            this.netChangeInCash = value;
            return this;
        }
    }
}
