package com.patrick.fintech.loan_backend.dto.regulatory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BnrBreakdownRow {

    private String label;

    private long count;

    

    @JsonProperty("amount")
private BigDecimal amount;

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
    public static class BnrBreakdownRowBuilder {
        private BigDecimal amount;

        public BnrBreakdownRowBuilder amount(Double value) {
            this.amount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrBreakdownRowBuilder amount(BigDecimal value) {
            this.amount = value;
            return this;
        }
    }
}
