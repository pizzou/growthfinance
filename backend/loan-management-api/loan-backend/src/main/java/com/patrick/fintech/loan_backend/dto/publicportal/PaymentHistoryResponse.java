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
public class PaymentHistoryResponse {

    private Long paymentId;

    private LocalDate paymentDate;

    

    @JsonProperty("amount")
private BigDecimal amount;

    private String method;

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

    /** Backward-compatible Double builder overloads; state remains BigDecimal. */
    public static class PaymentHistoryResponseBuilder {
        private BigDecimal amount;

        public PaymentHistoryResponseBuilder amount(Double value) {
            this.amount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public PaymentHistoryResponseBuilder amount(BigDecimal value) {
            this.amount = value;
            return this;
        }
    }
}
