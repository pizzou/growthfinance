package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
    name = "journal_entries",
    indexes = {
        @Index(
            name = "idx_journal_org",
            columnList = "organization_id"
        ),
        @Index(
            name = "idx_journal_org_date",
            columnList = "organization_id, entry_date"
        ),
        @Index(
            name = "idx_journal_org_source",
            columnList = "organization_id, source_type, source_id"
        ),
        @Index(
            name = "idx_journal_reference",
            columnList = "reference"
        )
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The organization that owns this accounting entry.
     *
     * The application currently operates as a single organization,
     * but keeping this relationship protects the accounting data model
     * and makes future expansion possible without redesigning the ledger.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "organization_id",
        nullable = false
    )
    private Organization organization;

    /**
     * Optional branch associated with the accounting transaction.
     *
     * Examples:
     * - Loan disbursement from a branch
     * - Payment received at a branch
     * - Branch expense
     *
     * Head-office transactions may have no branch.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    /**
     * Accounting date.
     *
     * This is the date used for financial reporting.
     */
    @Column(nullable = false)
    private LocalDate entryDate;

    /**
     * Human-readable transaction reference.
     *
     * Examples:
     * LOAN-RW202608020001
     * PAY-RW202608020001
     * EXP-145
     */
    @Column(length = 100)
    private String reference;

    /**
     * Identifies the business transaction that generated
     * this accounting entry.
     *
     * Examples:
     *
     * LOAN_DISBURSEMENT
     * PAYMENT_RECEIVED
     * PROCESSING_FEE
     * INTEREST_ACCRUAL
     * EXPENSE
     * WRITE_OFF
     * BANK_TRANSFER
     * REVERSAL
     * OWNER_CAPITAL
     * ADJUSTMENT
     */
    @Column(
        name = "source_type",
        nullable = false,
        length = 50
    )
    private String sourceType;

    /**
     * ID of the business object that generated this entry.
     *
     * For example:
     *
     * Loan ID
     * Payment ID
     * Expense ID
     * Bank transaction ID
     *
     * For system/manual transactions this may contain
     * another unique reference.
     */
    @Column(
        name = "source_id",
        length = 100
    )
    private String sourceId;

    /**
     * Human-readable explanation of the journal entry.
     */
    @Column(length = 500)
    private String description;

    /**
     * User who created the entry.
     *
     * Automatic system postings use SYSTEM.
     */
    @Column(length = 150)
    private String createdBy;

    /**
     * True when this journal entry has been reversed.
     *
     * Accounting history should never be deleted or edited.
     * A reversal creates a new journal entry instead.
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean reversed = false;

    /**
     * Creation timestamp.
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * Debit and credit lines belonging to this journal entry.
     *
     * Every journal entry must have:
     *
     * total debits = total credits
     */
    @Builder.Default
    @OneToMany(
        mappedBy = "journalEntry",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @OrderBy("id ASC")
    private List<JournalLine> lines = new ArrayList<>();

    /**
     * Automatically populated before the record is inserted.
     */
    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (entryDate == null) {
            entryDate = LocalDate.now();
        }

        if (reversed == null) {
            reversed = false;
        }

        if (lines == null) {
            lines = new ArrayList<>();
        }
    }

    /**
     * Convenience method for adding a journal line.
     */
    public void addLine(JournalLine line) {

        if (line == null) {
            return;
        }

        if (lines == null) {
            lines = new ArrayList<>();
        }

        lines.add(line);
        line.setJournalEntry(this);
    }

    /**
     * Convenience method for removing a journal line.
     */
    public void removeLine(JournalLine line) {

        if (line == null || lines == null) {
            return;
        }

        lines.remove(line);
        line.setJournalEntry(null);
    }

    /**
     * Returns the branch name without exposing the lazy Branch
     * relationship directly through JSON.
     */
    @Transient
    public String getBranchName() {
        return branch != null ? branch.getName() : null;
    }
}