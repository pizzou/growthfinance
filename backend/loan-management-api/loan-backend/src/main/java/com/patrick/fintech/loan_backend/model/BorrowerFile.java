package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "borrower_files")
@Data @NoArgsConstructor @AllArgsConstructor
public class BorrowerFile {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_id", nullable = false)
    private Borrower borrower;

    private String fileName;
    private String fileType;
    private Long   fileSize;
    private String filePath;

   @Enumerated(EnumType.STRING)
@Column(nullable = false)
private DocumentType documentType;

    /** True when this file was uploaded by the applicant from the public apply/track flow, not by staff. */
    private boolean uploadedByApplicant;

    @Enumerated(EnumType.STRING)
@Column(nullable = false)
private VerificationStatus verificationStatus;

    /** Loan officer's note on this specific document, e.g. why it was rejected or what's needed instead. */
    @Column(columnDefinition = "TEXT")
    private String officerComment;

    /** Denormalized name of whoever last verified/rejected this file — avoids a lazy-loading
     *  User reference on every document row just to show "verified by X" in a list. */
    private String verifiedByName;

    private LocalDateTime verifiedAt;

    // Use bytea explicitly for PostgreSQL — avoids the oid vs bytea conflict
    // No @Lob here on purpose — with Postgres, @Lob on a byte[] field makes Hibernate
    // route writes through the JDBC Large Object API (lo_creat/lo_open) instead of
    // writing straight to this bytea column, which is stricter about transaction
    // state and was throwing "cannot execute lo_creat() in a read-only transaction".
    // Hibernate 6 maps a plain byte[] to bytea correctly without @Lob.
    // @JsonIgnore — this is raw file content; list/detail endpoints must never serialize
    // it into JSON (huge payloads, not even renderable as an image). Actual file bytes
    // only ever leave the server through BorrowerFileController's dedicated
    // download/preview endpoints, which read it straight from the entity, not via Jackson.
    @JsonIgnore
    @Column(columnDefinition = "bytea")
    private byte[] data;

    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
        if (verificationStatus == null) {
    verificationStatus = VerificationStatus.PENDING;
}
    }
}
