package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A balanced double-entry journal entry — total debits must equal total
 * credits across its lines (enforced in AccountingService before saving).
 * Posted automatically for the transactions that move real money: loan
 * disbursement, payment received, write-off. This is what makes the
 * platform's numbers reconcilable the way a bank's finance team expects,
 * not just a list of loans and payments.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "journal_entries", indexes = @Index(name = "idx_journal_org", columnList = "organization_id"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class JournalEntry {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    /** Which branch this entry belongs to, when the source transaction has one (e.g. a loan's
     *  branch) — null for org-wide entries (head-office postings, manual cashbook entries not
     *  tied to a branch). Lets branch-level reporting filter the same ledger without a parallel
     *  per-branch chart of accounts. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    private LocalDate entryDate;
    private String    reference;      // e.g. loan reference number, payment reference
    private String    sourceType;     // "LOAN_DISBURSEMENT", "PAYMENT_RECEIVED", "WRITE_OFF"
    private String    sourceId;       // id of the Loan/Payment that generated this entry
    private String    description;
    private String    createdBy;      // "SYSTEM" for automatic postings

    @Builder.Default
    private Boolean reversed = false;

    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JournalLine> lines;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (entryDate == null) entryDate = LocalDate.now();
        if (reversed == null) reversed = false;
    }

    public String getBranchName() { return branch != null ? branch.getName() : null; }
}
