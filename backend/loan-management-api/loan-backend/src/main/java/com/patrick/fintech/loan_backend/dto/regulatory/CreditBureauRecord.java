
package com.patrick.fintech.loan_backend.dto.regulatory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditBureauRecord {

    private Long borrowerId;

    private String fullName;

    private String nationalId;

    private LocalDate dateOfBirth;

    private String gender;

    private String phone;

    private String loanNumber;

    private String loanType;

    private String loanStatus;

    private String repaymentClassification;

    

    @JsonProperty("loanAmount")
private BigDecimal loanAmount;

    

    @JsonProperty("outstandingBalance")
private BigDecimal outstandingBalance;

    private int daysPastDue;

    private Integer creditScore;

    private LocalDate dateOpened;

    private LocalDate lastPaymentDate;

    private LocalDate maturityDate;

    private LocalDate dateClosed;

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

    /** Backward-compatible Double builder overloads; state remains BigDecimal. */
    public static class CreditBureauRecordBuilder {
        private BigDecimal loanAmount;
        private BigDecimal outstandingBalance;

        public CreditBureauRecordBuilder loanAmount(Double value) {
            this.loanAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public CreditBureauRecordBuilder loanAmount(BigDecimal value) {
            this.loanAmount = value;
            return this;
        }
        public CreditBureauRecordBuilder outstandingBalance(Double value) {
            this.outstandingBalance = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public CreditBureauRecordBuilder outstandingBalance(BigDecimal value) {
            this.outstandingBalance = value;
            return this;
        }
    }
}
