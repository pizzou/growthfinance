package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

    @Column(columnDefinition = "TEXT")
    private String notes;

    private String outcome;               // NO_ANSWER, PROMISED, REFUSED, PAID, DISPUTED, WRONG_NUMBER
    private LocalDate promiseDate;
    private Double    promiseAmount;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public enum ActionType {
        CALL, SMS, EMAIL, FIELD_VISIT, LEGAL_NOTICE, PROMISE_TO_PAY,
        PAYMENT_RECEIVED, ESCALATED, CASE_OPENED, CASE_CLOSED, WRITE_OFF
    }
}
