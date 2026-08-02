package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
    name = "journal_lines",
    indexes = {
        @Index(
            name = "idx_journal_line_entry",
            columnList = "journal_entry_id"
        ),
        @Index(
            name = "idx_journal_line_account",
            columnList = "account_id"
        )
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Parent journal entry.
     *
     * Example:
     *
     * Journal Entry #125
     *      |
     *      ├── Line 1: DR Loans Receivable
     *      └── Line 2: CR Cash and Bank
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "journal_entry_id",
        nullable = false
    )
    private JournalEntry journalEntry;

    /**
     * Chart of account affected by this line.
     *
     * Examples:
     *
     * 1000 Cash and Bank
     * 1100 Loans Receivable
     * 4000 Interest Income
     * 5100 Operating Expenses
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "account_id",
        nullable = false
    )
    private ChartOfAccount account;

    /**
     * Debit amount.
     *
     * A journal line should contain either a debit
     * or a credit, not both.
     */
    @Builder.Default
    @Column(
        nullable = false
    )
    private Double debit = 0.0;

    /**
     * Credit amount.
     *
     * A journal line should contain either a debit
     * or a credit, not both.
     */
    @Builder.Default
    @Column(
        nullable = false
    )
    private Double credit = 0.0;

    /**
     * Explanation of what this individual line represents.
     */
    @Column(length = 500)
    private String description;

    /**
     * Makes sure null values never reach the accounting calculations.
     */
    @PrePersist
    @PreUpdate
    protected void normalizeAmounts() {

        if (debit == null) {
            debit = 0.0;
        }

        if (credit == null) {
            credit = 0.0;
        }
    }

    /**
     * Returns true when this line is a debit.
     */
    @Transient
    public boolean isDebit() {
        return debit != null && debit > 0.0;
    }

    /**
     * Returns true when this line is a credit.
     */
    @Transient
    public boolean isCredit() {
        return credit != null && credit > 0.0;
    }

    /**
     * Returns the amount represented by this line.
     */
    @Transient
    public double getAmount() {

        double debitAmount =
            debit != null ? debit : 0.0;

        double creditAmount =
            credit != null ? credit : 0.0;

        return Math.max(debitAmount, creditAmount);
    }
}