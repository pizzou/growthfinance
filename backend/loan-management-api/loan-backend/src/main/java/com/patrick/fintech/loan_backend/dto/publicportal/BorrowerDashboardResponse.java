package com.patrick.fintech.loan_backend.dto.publicportal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BorrowerDashboardResponse {

    private Long loanId;
    private String referenceNumber;

    private String borrowerName;

    private String status;
    private String loanType;

    

    @JsonProperty("principal")
private BigDecimal principal;
    
    @JsonProperty("outstandingBalance")
private BigDecimal outstandingBalance;
    
    @JsonProperty("totalPaid")
private BigDecimal totalPaid;
    
    @JsonProperty("totalRepayable")
private BigDecimal totalRepayable;

    

    @JsonProperty("nextInstallmentAmount")
private BigDecimal nextInstallmentAmount;

    private LocalDate nextPaymentDate;
    private LocalDate nextDueDate;
    private LocalDate maturityDate;

    private Integer missedInstallments;
    private Integer daysOverdue;

    

    @JsonProperty("interestRate")
private BigDecimal interestRate;

    private String currency;

    private String loanOfficer;

    private Integer activeLoans;
    private Integer overdueLoans;
    private Integer completedLoans;

    private Integer daysUntilDue;
    
    @JsonProperty("repaymentProgress")
private BigDecimal repaymentProgress;

private List<PaymentHistoryResponse> recentPayments;

private List<UpcomingInstallmentResponse> upcomingInstallments;

private List<String> availablePaymentMethods;

    @Deprecated
    @JsonIgnore
    public Double getPrincipal() {
        return principal == null ? null : principal.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getPrincipalDecimal() {
        return principal;
    }

    @Deprecated
    public void setPrincipal(Double value) {
        this.principal = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setPrincipal(BigDecimal value) {
        this.principal = value;
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
    public Double getTotalRepayable() {
        return totalRepayable == null ? null : totalRepayable.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalRepayableDecimal() {
        return totalRepayable;
    }

    @Deprecated
    public void setTotalRepayable(Double value) {
        this.totalRepayable = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalRepayable(BigDecimal value) {
        this.totalRepayable = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getNextInstallmentAmount() {
        return nextInstallmentAmount == null ? null : nextInstallmentAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getNextInstallmentAmountDecimal() {
        return nextInstallmentAmount;
    }

    @Deprecated
    public void setNextInstallmentAmount(Double value) {
        this.nextInstallmentAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setNextInstallmentAmount(BigDecimal value) {
        this.nextInstallmentAmount = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getInterestRate() {
        return interestRate == null ? null : interestRate.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getInterestRateDecimal() {
        return interestRate;
    }

    @Deprecated
    public void setInterestRate(Double value) {
        this.interestRate = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setInterestRate(BigDecimal value) {
        this.interestRate = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getRepaymentProgress() {
        return repaymentProgress == null ? null : repaymentProgress.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getRepaymentProgressDecimal() {
        return repaymentProgress;
    }

    @Deprecated
    public void setRepaymentProgress(Double value) {
        this.repaymentProgress = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setRepaymentProgress(BigDecimal value) {
        this.repaymentProgress = value;
    }

    /** Backward-compatible Double builder overloads; state remains BigDecimal. */
    public static class BorrowerDashboardResponseBuilder {
        private BigDecimal interestRate;
        private BigDecimal nextInstallmentAmount;
        private BigDecimal outstandingBalance;
        private BigDecimal principal;
        private BigDecimal repaymentProgress;
        private BigDecimal totalPaid;
        private BigDecimal totalRepayable;

        public BorrowerDashboardResponseBuilder principal(Double value) {
            this.principal = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerDashboardResponseBuilder principal(BigDecimal value) {
            this.principal = value;
            return this;
        }
        public BorrowerDashboardResponseBuilder outstandingBalance(Double value) {
            this.outstandingBalance = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerDashboardResponseBuilder outstandingBalance(BigDecimal value) {
            this.outstandingBalance = value;
            return this;
        }
        public BorrowerDashboardResponseBuilder totalPaid(Double value) {
            this.totalPaid = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerDashboardResponseBuilder totalPaid(BigDecimal value) {
            this.totalPaid = value;
            return this;
        }
        public BorrowerDashboardResponseBuilder totalRepayable(Double value) {
            this.totalRepayable = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerDashboardResponseBuilder totalRepayable(BigDecimal value) {
            this.totalRepayable = value;
            return this;
        }
        public BorrowerDashboardResponseBuilder nextInstallmentAmount(Double value) {
            this.nextInstallmentAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerDashboardResponseBuilder nextInstallmentAmount(BigDecimal value) {
            this.nextInstallmentAmount = value;
            return this;
        }
        public BorrowerDashboardResponseBuilder interestRate(Double value) {
            this.interestRate = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerDashboardResponseBuilder interestRate(BigDecimal value) {
            this.interestRate = value;
            return this;
        }
        public BorrowerDashboardResponseBuilder repaymentProgress(Double value) {
            this.repaymentProgress = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerDashboardResponseBuilder repaymentProgress(BigDecimal value) {
            this.repaymentProgress = value;
            return this;
        }
    }
}
