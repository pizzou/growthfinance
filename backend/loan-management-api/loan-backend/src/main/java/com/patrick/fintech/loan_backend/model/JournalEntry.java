package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "journal_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    @JsonIgnore
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    @JsonIgnore
    private Branch branch;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    /*
     * Examples:
     *
     * LOAN_DISBURSEMENT
     * LOAN_PAYMENT
     * LOAN_FEE
     * BANK_ACCOUNT_OPENING
     * CASHBOOK_DEPOSIT
     * CASHBOOK_WITHDRAWAL
     * CASHBOOK_TRANSFER
     * MANUAL
     */
    @Column(name = "source_type", length = 100)
    private String sourceType;

    /*
     * ID of the originating transaction.
     * Stored as text because some source IDs may not be numeric.
     */
    @Column(name = "source_id", length = 100)
    private String sourceId;

    @Column(name = "reference", length = 255)
    private String reference;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "reversed", nullable = false)
    @Builder.Default
    private Boolean reversed = false;

    @OneToMany(
        mappedBy = "journalEntry",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @Builder.Default
    @JsonIgnore
    private List<JournalLine> lines = new ArrayList<>();

    public void addLine(JournalLine line) {
        if (line == null) {
            return;
        }

        lines.add(line);
        line.setJournalEntry(this);
    }

    public void removeLine(JournalLine line) {
        if (line == null) {
            return;
        }

        lines.remove(line);
        line.setJournalEntry(null);
    }
}
