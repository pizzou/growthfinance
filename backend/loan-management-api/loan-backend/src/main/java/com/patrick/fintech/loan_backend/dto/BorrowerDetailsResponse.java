package com.patrick.fintech.loan_backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BorrowerDetailsResponse {

    // ============================================================
    // BORROWER PROFILE
    // ============================================================

    private Long borrowerId;

    private String fullName;

    private String firstName;

    private String lastName;

    private String email;

    private String phone;

    private String alternatePhone;

    private String nationalId;

    private String passportNumber;

    private LocalDate dateOfBirth;

    private String gender;

    private String maritalStatus;

    private String nationality;

    private String country;

    private String address;

    // ============================================================
    // EMPLOYMENT / FINANCIAL PROFILE
    // ============================================================

    private String employerName;

    private String employmentType;

    private String jobTitle;

    

    @JsonProperty("monthlyIncome")
private BigDecimal monthlyIncome;

    

    @JsonProperty("monthlyExpenses")
private BigDecimal monthlyExpenses;

    

    @JsonProperty("netWorth")
private BigDecimal netWorth;

    private Integer creditScore;

    private String creditBureau;

    private LocalDate creditReportDate;

    // ============================================================
    // BORROWER STATUS
    // ============================================================

    private String status;

    private LocalDate createdAt;

    // ============================================================
    // LOAN SUMMARY
    // ============================================================

    private int totalLoans;

    private int activeLoans;

    private int completedLoans;

    private int overdueLoans;

    private int defaultedLoans;

    private int writtenOffLoans;

    

    @JsonProperty("totalBorrowed")
private BigDecimal totalBorrowed;

    

    @JsonProperty("totalDisbursed")
private BigDecimal totalDisbursed;

    

    @JsonProperty("totalOutstanding")
private BigDecimal totalOutstanding;

    

    @JsonProperty("totalPrincipalPaid")
private BigDecimal totalPrincipalPaid;

    

    @JsonProperty("totalInterestPaid")
private BigDecimal totalInterestPaid;

    

    @JsonProperty("totalFeesPaid")
private BigDecimal totalFeesPaid;

    

    @JsonProperty("totalPaid")
private BigDecimal totalPaid;

    // ============================================================
    // REPAYMENT PERFORMANCE
    // ============================================================

    private int totalPayments;

    private int successfulPayments;

    private int missedPayments;

    private int overduePayments;

    

    @JsonProperty("repaymentRate")
private BigDecimal repaymentRate;

    

    @JsonProperty("onTimePaymentRate")
private BigDecimal onTimePaymentRate;

    private int currentDaysPastDue;

    private int maximumDaysPastDue;

    // ============================================================
    // RISK
    // ============================================================

    private String riskLevel;

    private String repaymentBehaviour;

    private boolean goodPayer;

    private boolean currentlyOverdue;

    private boolean hasDefaultHistory;

    private boolean hasMultipleActiveLoans;

    // ============================================================
    // LOANS
    // ============================================================

    private List<LoanSummary> loans;

    // ============================================================
    // PAYMENTS
    // ============================================================

    private List<PaymentSummary> payments;


    // ============================================================
    // LOAN SUMMARY
    // ============================================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoanSummary {

        private Long loanId;

        private String loanNumber;

        private String loanType;

        private String status;

        

        @JsonProperty("loanAmount")
private BigDecimal loanAmount;

        

        @JsonProperty("disbursedAmount")
private BigDecimal disbursedAmount;

        

        @JsonProperty("outstandingBalance")
private BigDecimal outstandingBalance;

        

        @JsonProperty("principalPaid")
private BigDecimal principalPaid;

        

        @JsonProperty("interestPaid")
private BigDecimal interestPaid;

        

        @JsonProperty("totalPaid")
private BigDecimal totalPaid;

        

        @JsonProperty("interestRate")
private BigDecimal interestRate;

        private int durationMonths;

        private int daysPastDue;

        private String repaymentClassification;

        private LocalDate dateOpened;

        private LocalDate maturityDate;

        private LocalDate lastPaymentDate;

        private String branchName;

        private String currency;
    
    @Deprecated
    @JsonIgnore
    public Double getLoanAmount() {
        return loanAmount == null ? null : loanAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getLoanAmountDecimal() {
        return loanAmount;
    }

    @Deprecated
    public void setLoanAmount(Double value) {
        this.loanAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setLoanAmount(BigDecimal value) {
        this.loanAmount = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getDisbursedAmount() {
        return disbursedAmount == null ? null : disbursedAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getDisbursedAmountDecimal() {
        return disbursedAmount;
    }

    @Deprecated
    public void setDisbursedAmount(Double value) {
        this.disbursedAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setDisbursedAmount(BigDecimal value) {
        this.disbursedAmount = value;
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
    public Double getPrincipalPaid() {
        return principalPaid == null ? null : principalPaid.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getPrincipalPaidDecimal() {
        return principalPaid;
    }

    @Deprecated
    public void setPrincipalPaid(Double value) {
        this.principalPaid = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setPrincipalPaid(BigDecimal value) {
        this.principalPaid = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getInterestPaid() {
        return interestPaid == null ? null : interestPaid.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getInterestPaidDecimal() {
        return interestPaid;
    }

    @Deprecated
    public void setInterestPaid(Double value) {
        this.interestPaid = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setInterestPaid(BigDecimal value) {
        this.interestPaid = value;
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

    /** Backward-compatible Double builder overloads; state remains BigDecimal. */
    public static class LoanSummaryBuilder {
        private BigDecimal disbursedAmount;
        private BigDecimal interestPaid;
        private BigDecimal interestRate;
        private BigDecimal loanAmount;
        private BigDecimal outstandingBalance;
        private BigDecimal principalPaid;
        private BigDecimal totalPaid;

        public LoanSummaryBuilder loanAmount(Double value) {
            this.loanAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanSummaryBuilder loanAmount(BigDecimal value) {
            this.loanAmount = value;
            return this;
        }
        public LoanSummaryBuilder disbursedAmount(Double value) {
            this.disbursedAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanSummaryBuilder disbursedAmount(BigDecimal value) {
            this.disbursedAmount = value;
            return this;
        }
        public LoanSummaryBuilder outstandingBalance(Double value) {
            this.outstandingBalance = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanSummaryBuilder outstandingBalance(BigDecimal value) {
            this.outstandingBalance = value;
            return this;
        }
        public LoanSummaryBuilder principalPaid(Double value) {
            this.principalPaid = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanSummaryBuilder principalPaid(BigDecimal value) {
            this.principalPaid = value;
            return this;
        }
        public LoanSummaryBuilder interestPaid(Double value) {
            this.interestPaid = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanSummaryBuilder interestPaid(BigDecimal value) {
            this.interestPaid = value;
            return this;
        }
        public LoanSummaryBuilder totalPaid(Double value) {
            this.totalPaid = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanSummaryBuilder totalPaid(BigDecimal value) {
            this.totalPaid = value;
            return this;
        }
        public LoanSummaryBuilder interestRate(Double value) {
            this.interestRate = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public LoanSummaryBuilder interestRate(BigDecimal value) {
            this.interestRate = value;
            return this;
        }
    }
}


    // ============================================================
    // PAYMENT SUMMARY
    // ============================================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSummary {

        private Long paymentId;

        private Long loanId;

        private String loanNumber;

        private String borrowerName;

        

        @JsonProperty("amount")
private BigDecimal amount;

        

        @JsonProperty("principal")
private BigDecimal principal;

        

        @JsonProperty("interest")
private BigDecimal interest;

        

        @JsonProperty("fees")
private BigDecimal fees;

        

        @JsonProperty("penalty")
private BigDecimal penalty;

        

        @JsonProperty("totalPaid")
private BigDecimal totalPaid;

        private LocalDate dueDate;

        private LocalDate paidDate;

        private String paymentMethod;

        private String status;

        private boolean onTime;

        private int daysLate;
    
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
    public Double getInterest() {
        return interest == null ? null : interest.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getInterestDecimal() {
        return interest;
    }

    @Deprecated
    public void setInterest(Double value) {
        this.interest = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setInterest(BigDecimal value) {
        this.interest = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getFees() {
        return fees == null ? null : fees.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getFeesDecimal() {
        return fees;
    }

    @Deprecated
    public void setFees(Double value) {
        this.fees = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setFees(BigDecimal value) {
        this.fees = value;
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

    /** Backward-compatible Double builder overloads; state remains BigDecimal. */
    public static class PaymentSummaryBuilder {
        public PaymentSummaryBuilder amount(Double value) {
            this.amount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentSummaryBuilder amount(BigDecimal value) {
            this.amount = value;
            return this;
        }
        public PaymentSummaryBuilder principal(Double value) {
            this.principal = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentSummaryBuilder principal(BigDecimal value) {
            this.principal = value;
            return this;
        }
        public PaymentSummaryBuilder interest(Double value) {
            this.interest = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentSummaryBuilder interest(BigDecimal value) {
            this.interest = value;
            return this;
        }
        public PaymentSummaryBuilder fees(Double value) {
            this.fees = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentSummaryBuilder fees(BigDecimal value) {
            this.fees = value;
            return this;
        }
        public PaymentSummaryBuilder penalty(Double value) {
            this.penalty = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentSummaryBuilder penalty(BigDecimal value) {
            this.penalty = value;
            return this;
        }
        public PaymentSummaryBuilder totalPaid(Double value) {
            this.totalPaid = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentSummaryBuilder totalPaid(BigDecimal value) {
            this.totalPaid = value;
            return this;
        }
    }
}

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

    @Deprecated
    @JsonIgnore
    public Double getMonthlyExpenses() {
        return monthlyExpenses == null ? null : monthlyExpenses.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getMonthlyExpensesDecimal() {
        return monthlyExpenses;
    }

    @Deprecated
    public void setMonthlyExpenses(Double value) {
        this.monthlyExpenses = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setMonthlyExpenses(BigDecimal value) {
        this.monthlyExpenses = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getNetWorth() {
        return netWorth == null ? null : netWorth.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getNetWorthDecimal() {
        return netWorth;
    }

    @Deprecated
    public void setNetWorth(Double value) {
        this.netWorth = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setNetWorth(BigDecimal value) {
        this.netWorth = value;
    }

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
    public Double getTotalDisbursed() {
        return totalDisbursed == null ? null : totalDisbursed.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalDisbursedDecimal() {
        return totalDisbursed;
    }

    @Deprecated
    public void setTotalDisbursed(Double value) {
        this.totalDisbursed = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalDisbursed(BigDecimal value) {
        this.totalDisbursed = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getTotalOutstanding() {
        return totalOutstanding == null ? null : totalOutstanding.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalOutstandingDecimal() {
        return totalOutstanding;
    }

    @Deprecated
    public void setTotalOutstanding(Double value) {
        this.totalOutstanding = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalOutstanding(BigDecimal value) {
        this.totalOutstanding = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getTotalPrincipalPaid() {
        return totalPrincipalPaid == null ? null : totalPrincipalPaid.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalPrincipalPaidDecimal() {
        return totalPrincipalPaid;
    }

    @Deprecated
    public void setTotalPrincipalPaid(Double value) {
        this.totalPrincipalPaid = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalPrincipalPaid(BigDecimal value) {
        this.totalPrincipalPaid = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getTotalInterestPaid() {
        return totalInterestPaid == null ? null : totalInterestPaid.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalInterestPaidDecimal() {
        return totalInterestPaid;
    }

    @Deprecated
    public void setTotalInterestPaid(Double value) {
        this.totalInterestPaid = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalInterestPaid(BigDecimal value) {
        this.totalInterestPaid = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getTotalFeesPaid() {
        return totalFeesPaid == null ? null : totalFeesPaid.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalFeesPaidDecimal() {
        return totalFeesPaid;
    }

    @Deprecated
    public void setTotalFeesPaid(Double value) {
        this.totalFeesPaid = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalFeesPaid(BigDecimal value) {
        this.totalFeesPaid = value;
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
    public Double getRepaymentRate() {
        return repaymentRate == null ? null : repaymentRate.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getRepaymentRateDecimal() {
        return repaymentRate;
    }

    @Deprecated
    public void setRepaymentRate(Double value) {
        this.repaymentRate = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setRepaymentRate(BigDecimal value) {
        this.repaymentRate = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getOnTimePaymentRate() {
        return onTimePaymentRate == null ? null : onTimePaymentRate.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getOnTimePaymentRateDecimal() {
        return onTimePaymentRate;
    }

    @Deprecated
    public void setOnTimePaymentRate(Double value) {
        this.onTimePaymentRate = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setOnTimePaymentRate(BigDecimal value) {
        this.onTimePaymentRate = value;
    }

    /** Backward-compatible Double builder overloads; state remains BigDecimal. */
    public static class BorrowerDetailsResponseBuilder {
        public BorrowerDetailsResponseBuilder monthlyIncome(Double value) {
            this.monthlyIncome = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerDetailsResponseBuilder monthlyIncome(BigDecimal value) {
            this.monthlyIncome = value;
            return this;
        }
        public BorrowerDetailsResponseBuilder monthlyExpenses(Double value) {
            this.monthlyExpenses = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerDetailsResponseBuilder monthlyExpenses(BigDecimal value) {
            this.monthlyExpenses = value;
            return this;
        }
        public BorrowerDetailsResponseBuilder netWorth(Double value) {
            this.netWorth = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerDetailsResponseBuilder netWorth(BigDecimal value) {
            this.netWorth = value;
            return this;
        }
        public BorrowerDetailsResponseBuilder totalBorrowed(Double value) {
            this.totalBorrowed = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerDetailsResponseBuilder totalBorrowed(BigDecimal value) {
            this.totalBorrowed = value;
            return this;
        }
        public BorrowerDetailsResponseBuilder totalDisbursed(Double value) {
            this.totalDisbursed = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerDetailsResponseBuilder totalDisbursed(BigDecimal value) {
            this.totalDisbursed = value;
            return this;
        }
        public BorrowerDetailsResponseBuilder totalOutstanding(Double value) {
            this.totalOutstanding = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerDetailsResponseBuilder totalOutstanding(BigDecimal value) {
            this.totalOutstanding = value;
            return this;
        }
        public BorrowerDetailsResponseBuilder totalPrincipalPaid(Double value) {
            this.totalPrincipalPaid = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerDetailsResponseBuilder totalPrincipalPaid(BigDecimal value) {
            this.totalPrincipalPaid = value;
            return this;
        }
        public BorrowerDetailsResponseBuilder totalInterestPaid(Double value) {
            this.totalInterestPaid = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerDetailsResponseBuilder totalInterestPaid(BigDecimal value) {
            this.totalInterestPaid = value;
            return this;
        }
        public BorrowerDetailsResponseBuilder totalFeesPaid(Double value) {
            this.totalFeesPaid = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerDetailsResponseBuilder totalFeesPaid(BigDecimal value) {
            this.totalFeesPaid = value;
            return this;
        }
        public BorrowerDetailsResponseBuilder totalPaid(Double value) {
            this.totalPaid = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerDetailsResponseBuilder totalPaid(BigDecimal value) {
            this.totalPaid = value;
            return this;
        }
        public BorrowerDetailsResponseBuilder repaymentRate(Double value) {
            this.repaymentRate = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerDetailsResponseBuilder repaymentRate(BigDecimal value) {
            this.repaymentRate = value;
            return this;
        }
        public BorrowerDetailsResponseBuilder onTimePaymentRate(Double value) {
            this.onTimePaymentRate = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerDetailsResponseBuilder onTimePaymentRate(BigDecimal value) {
            this.onTimePaymentRate = value;
            return this;
        }
    }
}
