package com.patrick.fintech.loan_backend.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "collection_actions",
    indexes = @Index(name = "idx_ca_case", columnList = "collection_case_id"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CollectionAction {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_case_id", nullable = false)
    private CollectionCase collectionCase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionType actionType;

    private String performedBy;

    @Column(columnDefinition = "TEXT", precision = 19, scale = 6)
    private String notes;

    private String outcome;               // NO_ANSWER, PROMISED, REFUSED, PAID, DISPUTED, WRONG_NUMBER
    private LocalDate promiseDate;
    @JsonProperty("promiseAmount")
    @Column(precision = 19, scale = 6)
    private BigDecimal promiseAmount;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public enum ActionType {
        CALL, SMS, EMAIL, FIELD_VISIT, LEGAL_NOTICE, PROMISE_TO_PAY,
        PAYMENT_RECEIVED, ESCALATED, CASE_OPENED, CASE_CLOSED, WRITE_OFF
    }
    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getPromiseAmountDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getPromiseAmount() {
        return promiseAmount == null ? null : promiseAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getPromiseAmountDecimal() {
        return promiseAmount;
    }

    @Deprecated
    public void setPromiseAmount(Double value) {
        this.promiseAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setPromiseAmount(BigDecimal value) {
        this.promiseAmount = value;
    }

    /** Backward-compatible builder overloads for legacy Double callers.
     *  Financial state is stored as BigDecimal.
     */
    public static class CollectionActionBuilder {
        private BigDecimal promiseAmount;


        public CollectionActionBuilder promiseAmount(Double value) {
            this.promiseAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }        public CollectionActionBuilder promiseAmount(BigDecimal value) {
            this.promiseAmount = value;
            return this;
        }
    }

}
