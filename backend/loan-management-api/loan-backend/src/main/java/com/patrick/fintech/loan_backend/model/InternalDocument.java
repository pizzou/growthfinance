package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Staff document library — policies, contracts, board minutes, templates, internal memos.
 * Deliberately separate from {@link BorrowerFile}, which is KYC/application documents tied to
 * one borrower; this is org-wide and not tied to any borrower or loan.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "internal_documents")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class InternalDocument {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    private String title;

    /** POLICY, CONTRACT, MEMO, TEMPLATE, BOARD_MINUTES, COMPLIANCE, OTHER — see
     *  InternalDocumentService.CATEGORIES. */
    @Builder.Default
    private String category = "OTHER";

    private String description;

    private String fileName;
    private String fileType;
    private Long   fileSize;

    // No @Lob on purpose — see BorrowerFile's own comment on this; Hibernate 6 maps a plain
    // byte[] to Postgres bytea correctly without it, and @Lob here causes issues on Postgres.
    private byte[] data;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    private User uploadedBy;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (category == null || category.isBlank()) category = "OTHER";
    }
}
