package com.patrick.fintech.loan_backend.dto.publicportal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PaymentScheduleResponse {

    private Integer installmentNumber;

    private LocalDate dueDate;

    

    @JsonProperty("installmentAmount")
private BigDecimal installmentAmount;

    

    @JsonProperty("principal")
private BigDecimal principal;

    

    @JsonProperty("interest")
private BigDecimal interest;

    

    @JsonProperty("penalty")
private BigDecimal penalty;

    

    @JsonProperty("paid")
private BigDecimal paid;

    

    @JsonProperty("balance")
private BigDecimal balance;

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
    public Double getPaid() {
        return paid == null ? null : paid.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getPaidDecimal() {
        return paid;
    }

    @Deprecated
    public void setPaid(Double value) {
        this.paid = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setPaid(BigDecimal value) {
        this.paid = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getBalance() {
        return balance == null ? null : balance.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getBalanceDecimal() {
        return balance;
    }

    @Deprecated
    public void setBalance(Double value) {
        this.balance = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setBalance(BigDecimal value) {
        this.balance = value;
    }

    /** Backward-compatible Double builder overloads; state remains BigDecimal. */
    public static class PaymentScheduleResponseBuilder {
        private BigDecimal balance;
        private BigDecimal installmentAmount;
        private BigDecimal interest;
        private BigDecimal paid;
        private BigDecimal penalty;
        private BigDecimal principal;

        public PaymentScheduleResponseBuilder installmentAmount(Double value) {
            this.installmentAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentScheduleResponseBuilder installmentAmount(BigDecimal value) {
            this.installmentAmount = value;
            return this;
        }
        public PaymentScheduleResponseBuilder principal(Double value) {
            this.principal = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentScheduleResponseBuilder principal(BigDecimal value) {
            this.principal = value;
            return this;
        }
        public PaymentScheduleResponseBuilder interest(Double value) {
            this.interest = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentScheduleResponseBuilder interest(BigDecimal value) {
            this.interest = value;
            return this;
        }
        public PaymentScheduleResponseBuilder penalty(Double value) {
            this.penalty = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentScheduleResponseBuilder penalty(BigDecimal value) {
            this.penalty = value;
            return this;
        }
        public PaymentScheduleResponseBuilder paid(Double value) {
            this.paid = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentScheduleResponseBuilder paid(BigDecimal value) {
            this.paid = value;
            return this;
        }
        public PaymentScheduleResponseBuilder balance(Double value) {
            this.balance = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentScheduleResponseBuilder balance(BigDecimal value) {
            this.balance = value;
            return this;
        }
    }
}
