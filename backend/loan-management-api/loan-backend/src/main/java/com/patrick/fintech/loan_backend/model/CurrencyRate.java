package com.patrick.fintech.loan_backend.model;

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
    private Double rate;
    private LocalDateTime fetchedAt;

    @PrePersist @PreUpdate protected void onSave() { fetchedAt = LocalDateTime.now(); }
}
