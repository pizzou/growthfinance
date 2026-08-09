package com.patrick.fintech.loan_backend.dto.publicportal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class RepaymentScheduleResponse {

    private Integer installmentNo;

    private LocalDate dueDate;

    

    @JsonProperty("installmentAmount")
private BigDecimal installmentAmount;

    

    @JsonProperty("principalAmount")
private BigDecimal principalAmount;

    

    @JsonProperty("interestAmount")
private BigDecimal interestAmount;

    

    @JsonProperty("penalty")
private BigDecimal penalty;

    

    @JsonProperty("amountPaid")
private BigDecimal amountPaid;

    

    @JsonProperty("balanceAfterPayment")
private BigDecimal balanceAfterPayment;

    private String status;

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
    public Double getBalanceAfterPayment() {
        return balanceAfterPayment == null ? null : balanceAfterPayment.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getBalanceAfterPaymentDecimal() {
        return balanceAfterPayment;
    }

    @Deprecated
    public void setBalanceAfterPayment(Double value) {
        this.balanceAfterPayment = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setBalanceAfterPayment(BigDecimal value) {
        this.balanceAfterPayment = value;
    }

    /** Backward-compatible Double builder overloads; state remains BigDecimal. */
    public static class RepaymentScheduleResponseBuilder {
        private BigDecimal amountPaid;
        private BigDecimal balanceAfterPayment;
        private BigDecimal installmentAmount;
        private BigDecimal interestAmount;
        private BigDecimal penalty;
        private BigDecimal principalAmount;

        public RepaymentScheduleResponseBuilder installmentAmount(Double value) {
            this.installmentAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public RepaymentScheduleResponseBuilder installmentAmount(BigDecimal value) {
            this.installmentAmount = value;
            return this;
        }
        public RepaymentScheduleResponseBuilder principalAmount(Double value) {
            this.principalAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public RepaymentScheduleResponseBuilder principalAmount(BigDecimal value) {
            this.principalAmount = value;
            return this;
        }
        public RepaymentScheduleResponseBuilder interestAmount(Double value) {
            this.interestAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public RepaymentScheduleResponseBuilder interestAmount(BigDecimal value) {
            this.interestAmount = value;
            return this;
        }
        public RepaymentScheduleResponseBuilder penalty(Double value) {
            this.penalty = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public RepaymentScheduleResponseBuilder penalty(BigDecimal value) {
            this.penalty = value;
            return this;
        }
        public RepaymentScheduleResponseBuilder amountPaid(Double value) {
            this.amountPaid = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public RepaymentScheduleResponseBuilder amountPaid(BigDecimal value) {
            this.amountPaid = value;
            return this;
        }
        public RepaymentScheduleResponseBuilder balanceAfterPayment(Double value) {
            this.balanceAfterPayment = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public RepaymentScheduleResponseBuilder balanceAfterPayment(BigDecimal value) {
            this.balanceAfterPayment = value;
            return this;
        }
    }
}
