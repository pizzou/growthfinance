package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * One run of importing a client's pre-existing manual (Excel/CSV) ledger into the platform.
 * Kept for audit — who imported what, when, and the per-row outcome — since a bulk data load
 * bypassing normal loan origination is exactly the kind of thing an auditor or regulator will
 * ask about later.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "import_batches")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ImportBatch {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "imported_by")
    private User importedBy;

    private String fileName;
    private Integer totalRows;
    private Integer successCount;
    private Integer failureCount;
    private String status; // COMPLETED, FAILED, PARTIAL

    /** JSON array of per-row outcomes: row number, action taken, and any error. */
    @Column(columnDefinition = "TEXT")
    private String rowResults;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (successCount == null) successCount = 0;
        if (failureCount == null) failureCount = 0;
        if (totalRows == null) totalRows = 0;
    }
}