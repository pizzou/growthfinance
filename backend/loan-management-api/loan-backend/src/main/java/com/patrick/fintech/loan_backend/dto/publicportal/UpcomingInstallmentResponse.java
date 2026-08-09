package com.patrick.fintech.loan_backend.dto.publicportal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpcomingInstallmentResponse {

    private Integer installmentNumber;

    private LocalDate dueDate;

    

    @JsonProperty("amount")
private BigDecimal amount;

    

    @JsonProperty("principal")
private BigDecimal principal;

    

    @JsonProperty("interest")
private BigDecimal interest;

    private String status;


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

    /** Backward-compatible Double builder overloads; state remains BigDecimal. */
    public static class UpcomingInstallmentResponseBuilder {
        private BigDecimal amount;
        private BigDecimal interest;
        private BigDecimal principal;

        public UpcomingInstallmentResponseBuilder amount(Double value) {
            this.amount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public UpcomingInstallmentResponseBuilder amount(BigDecimal value) {
            this.amount = value;
            return this;
        }
        public UpcomingInstallmentResponseBuilder principal(Double value) {
            this.principal = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public UpcomingInstallmentResponseBuilder principal(BigDecimal value) {
            this.principal = value;
            return this;
        }
        public UpcomingInstallmentResponseBuilder interest(Double value) {
            this.interest = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public UpcomingInstallmentResponseBuilder interest(BigDecimal value) {
            this.interest = value;
            return this;
        }
    }
}
