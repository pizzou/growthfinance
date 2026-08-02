package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;

import lombok.*;

@JsonIgnoreProperties({
        "hibernateLazyInitializer",
        "handler"
})
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
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;


    // ============================================================
    // JOURNAL ENTRY
    // ============================================================

    @JsonIgnore
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "journal_entry_id",
            nullable = false
    )
    private JournalEntry journalEntry;


    // ============================================================
    // ACCOUNT
    // ============================================================

    @JsonIgnore
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "account_id",
            nullable = false
    )
    private ChartOfAccount account;


    // ============================================================
    // DEBIT
    // ============================================================

    @Builder.Default
    @Column(
            nullable = false
    )
    private Double debit = 0.0;


    // ============================================================
    // CREDIT
    // ============================================================

    @Builder.Default
    @Column(
            nullable = false
    )
    private Double credit = 0.0;


    // ============================================================
    // DESCRIPTION
    // ============================================================

    @Column(
            length = 500
    )
    private String description;


    // ============================================================
    // NORMALIZE
    // ============================================================

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


    // ============================================================
    // DEBIT CHECK
    // ============================================================

    @Transient
    public boolean isDebit() {

        return debit != null
                && debit > 0.0;
    }


    // ============================================================
    // CREDIT CHECK
    // ============================================================

    @Transient
    public boolean isCredit() {

        return credit != null
                && credit > 0.0;
    }


   

    @Transient
    public double getAmount() {

        double debitAmount =
                debit != null
                        ? debit
                        : 0.0;

        double creditAmount =
                credit != null
                        ? credit
                        : 0.0;

        return Math.max(
                debitAmount,
                creditAmount
        );
    }
}