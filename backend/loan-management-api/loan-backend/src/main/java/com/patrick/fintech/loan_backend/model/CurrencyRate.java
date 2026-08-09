package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Cached FX rates (updated daily via open exchange API).
 */
@Entity
@Table(name = "currency_rates",
    uniqueConstraints = @UniqueConstraint(columnNames = {"base_currency","target_currency"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CurrencyRate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String baseCurrency;
    private String targetCurrency;
    @Column(precision = 19, scale = 12)
    @JsonProperty("rate")
    private BigDecimal rate;
    private LocalDateTime fetchedAt;

    @PrePersist @PreUpdate protected void onSave() { fetchedAt = LocalDateTime.now(); }
    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getRateDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getRate() {
        return rate == null ? null : rate.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getRateDecimal() {
        return rate;
    }

    @Deprecated
    public void setRate(Double value) {
        this.rate = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setRate(BigDecimal value) {
        this.rate = value;
    }

    /** Backward-compatible builder overloads for legacy Double callers.
     *  Financial state is stored as BigDecimal.
     */
    public static class CurrencyRateBuilder {
        private BigDecimal rate;


        public CurrencyRateBuilder rate(Double value) {
            this.rate = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }        public CurrencyRateBuilder rate(BigDecimal value) {
            this.rate = value;
            return this;
        }
    }

}
