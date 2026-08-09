package com.patrick.fintech.loan_backend.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "collection_cases",
    indexes = {
        @Index(name = "idx_cc_org", columnList = "organization_id"),
        @Index(name = "idx_cc_loan", columnList = "loan_id"),
        @Index(name = "idx_cc_bucket", columnList = "bucket"),
        @Index(name = "idx_cc_status", columnList = "status")
    })
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CollectionCase {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false, unique = true)
    private Loan loan;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_agent_id")
    private User assignedAgent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CollectionBucket bucket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, precision = 19, scale = 6)
    private CollectionStatus status;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    private Integer daysPastDue;
    @JsonProperty("overdueAmount")
    @Column(precision = 19, scale = 6)
    private BigDecimal overdueAmount;
    @JsonProperty("totalOutstanding")
    @Column(precision = 19, scale = 6)
    private BigDecimal totalOutstanding;

    private LocalDate lastContactDate;
    private LocalDate nextActionDate;
    private LocalDate promiseToPayDate;
    @JsonProperty("promiseToPayAmount")
    @Column(precision = 19, scale = 6)
    private BigDecimal promiseToPayAmount;

    private String resolutionNotes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "collectionCase", cascade = CascadeType.ALL)
    private List<CollectionAction> actions;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now();
        if (status == null) status = CollectionStatus.OPEN;
        if (priority == null) priority = Priority.MEDIUM;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum CollectionBucket { CURRENT, DPD_1_30, DPD_31_60, DPD_61_90, DPD_90_PLUS, WRITE_OFF }
    public enum CollectionStatus { OPEN, IN_PROGRESS, PROMISE_TO_PAY, ESCALATED, LEGAL, RESOLVED, WRITTEN_OFF }
    public enum Priority { LOW, MEDIUM, HIGH, URGENT }
    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getOverdueAmountDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getOverdueAmount() {
        return overdueAmount == null ? null : overdueAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getOverdueAmountDecimal() {
        return overdueAmount;
    }

    @Deprecated
    public void setOverdueAmount(Double value) {
        this.overdueAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setOverdueAmount(BigDecimal value) {
        this.overdueAmount = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getTotalOutstandingDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getTotalOutstanding() {
        return totalOutstanding == null ? null : totalOutstanding.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalOutstandingDecimal() {
        return totalOutstanding;
    }

    @Deprecated
    public void setTotalOutstanding(Double value) {
        this.totalOutstanding = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalOutstanding(BigDecimal value) {
        this.totalOutstanding = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getPromiseToPayAmountDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getPromiseToPayAmount() {
        return promiseToPayAmount == null ? null : promiseToPayAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getPromiseToPayAmountDecimal() {
        return promiseToPayAmount;
    }

    @Deprecated
    public void setPromiseToPayAmount(Double value) {
        this.promiseToPayAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setPromiseToPayAmount(BigDecimal value) {
        this.promiseToPayAmount = value;
    }

    /** Backward-compatible builder overloads for legacy Double callers.
     *  Financial state is stored as BigDecimal.
     */
    public static class CollectionCaseBuilder {
        private BigDecimal overdueAmount;
        private BigDecimal promiseToPayAmount;
        private BigDecimal totalOutstanding;


        public CollectionCaseBuilder overdueAmount(Double value) {
            this.overdueAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public CollectionCaseBuilder totalOutstanding(Double value) {
            this.totalOutstanding = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public CollectionCaseBuilder promiseToPayAmount(Double value) {
            this.promiseToPayAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }        public CollectionCaseBuilder overdueAmount(BigDecimal value) {
            this.overdueAmount = value;
            return this;
        }
        public CollectionCaseBuilder totalOutstanding(BigDecimal value) {
            this.totalOutstanding = value;
            return this;
        }
        public CollectionCaseBuilder promiseToPayAmount(BigDecimal value) {
            this.promiseToPayAmount = value;
            return this;
        }
    }

}
