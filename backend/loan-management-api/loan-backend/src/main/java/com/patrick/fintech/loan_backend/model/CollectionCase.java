package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @Column(nullable = false)
    private CollectionStatus status;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    private Integer daysPastDue;
    private Double  overdueAmount;
    private Double  totalOutstanding;

    private LocalDate lastContactDate;
    private LocalDate nextActionDate;
    private LocalDate promiseToPayDate;
    private Double    promiseToPayAmount;

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
}
