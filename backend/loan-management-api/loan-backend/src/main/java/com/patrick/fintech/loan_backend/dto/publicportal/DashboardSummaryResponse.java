package com.patrick.fintech.loan_backend.dto.publicportal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class DashboardSummaryResponse {

    private Integer totalLoans;

    private Integer activeLoans;

    

    @JsonProperty("totalBorrowed")
private BigDecimal totalBorrowed;

    

    @JsonProperty("outstandingBalance")
private BigDecimal outstandingBalance;

    

    @JsonProperty("totalPaid")
private BigDecimal totalPaid;

    

    @JsonProperty("nextPaymentAmount")
private BigDecimal nextPaymentAmount;

    private LocalDate nextPaymentDate;

    private Integer overdueLoans;

    @Deprecated
    @JsonIgnore
    public Double getTotalBorrowed() {
        return totalBorrowed == null ? null : totalBorrowed.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalBorrowedDecimal() {
        return totalBorrowed;
    }

    @Deprecated
    public void setTotalBorrowed(Double value) {
        this.totalBorrowed = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalBorrowed(BigDecimal value) {
        this.totalBorrowed = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getOutstandingBalance() {
        return outstandingBalance == null ? null : outstandingBalance.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getOutstandingBalanceDecimal() {
        return outstandingBalance;
    }

    @Deprecated
    public void setOutstandingBalance(Double value) {
        this.outstandingBalance = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setOutstandingBalance(BigDecimal value) {
        this.outstandingBalance = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getTotalPaid() {
        return totalPaid == null ? null : totalPaid.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalPaidDecimal() {
        return totalPaid;
    }

    @Deprecated
    public void setTotalPaid(Double value) {
        this.totalPaid = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalPaid(BigDecimal value) {
        this.totalPaid = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getNextPaymentAmount() {
        return nextPaymentAmount == null ? null : nextPaymentAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getNextPaymentAmountDecimal() {
        return nextPaymentAmount;
    }

    @Deprecated
    public void setNextPaymentAmount(Double value) {
        this.nextPaymentAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setNextPaymentAmount(BigDecimal value) {
        this.nextPaymentAmount = value;
    }

    /** Backward-compatible Double builder overloads; state remains BigDecimal. */
    public static class DashboardSummaryResponseBuilder {
        private BigDecimal nextPaymentAmount;
        private BigDecimal outstandingBalance;
        private BigDecimal totalBorrowed;
        private BigDecimal totalPaid;

        public DashboardSummaryResponseBuilder totalBorrowed(Double value) {
            this.totalBorrowed = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public DashboardSummaryResponseBuilder totalBorrowed(BigDecimal value) {
            this.totalBorrowed = value;
            return this;
        }
        public DashboardSummaryResponseBuilder outstandingBalance(Double value) {
            this.outstandingBalance = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public DashboardSummaryResponseBuilder outstandingBalance(BigDecimal value) {
            this.outstandingBalance = value;
            return this;
        }
        public DashboardSummaryResponseBuilder totalPaid(Double value) {
            this.totalPaid = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public DashboardSummaryResponseBuilder totalPaid(BigDecimal value) {
            this.totalPaid = value;
            return this;
        }
        public DashboardSummaryResponseBuilder nextPaymentAmount(Double value) {
            this.nextPaymentAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public DashboardSummaryResponseBuilder nextPaymentAmount(BigDecimal value) {
            this.nextPaymentAmount = value;
            return this;
        }
    }
}
