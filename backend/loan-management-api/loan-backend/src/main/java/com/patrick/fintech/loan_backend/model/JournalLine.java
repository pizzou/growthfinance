package com.patrick.fintech.loan_backend.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    // ============================================================
    // ID
    // ============================================================

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
            nullable = false,
            precision = 19,
            scale = 6
    )
    @JsonProperty("debit")
    private BigDecimal debit = BigDecimal.ZERO;


    // ============================================================
    // CREDIT
    // ============================================================

    @Builder.Default
    @Column(
            nullable = false,
            precision = 19,
            scale = 6
    )
    @JsonProperty("credit")
    private BigDecimal credit = BigDecimal.ZERO;


    // ============================================================
    // DESCRIPTION
    // ============================================================

    @Column(
            length = 500
    )
    private String description;


    // ============================================================
    // ENTITY NORMALIZATION
    // ============================================================

    @PrePersist
    @PreUpdate
    protected void normalizeAmounts() {

        if (debit == null) {
            debit = BigDecimal.ZERO;
        }

        if (credit == null) {
            credit = BigDecimal.ZERO;
        }

        debit = debit.setScale(
                6,
                java.math.RoundingMode.HALF_UP
        );

        credit = credit.setScale(
                6,
                java.math.RoundingMode.HALF_UP
        );
    }


    // ============================================================
    // DEBIT CHECK
    // ============================================================

    @Transient
    public boolean isDebit() {

        return debit != null
                && debit.compareTo(BigDecimal.ZERO) > 0;
    }


    // ============================================================
    // CREDIT CHECK
    // ============================================================

    @Transient
    public boolean isCredit() {

        return credit != null
                && credit.compareTo(BigDecimal.ZERO) > 0;
    }


    // ============================================================
    // AMOUNT
    // ============================================================

    /**
     * Returns the larger of debit or credit.
     *
     * This is useful for display/reporting where a journal line
     * has exactly one populated side.
     */
    @Transient
    public BigDecimal getAmount() {

        BigDecimal debitAmount =
                debit != null
                        ? debit
                        : BigDecimal.ZERO;

        BigDecimal creditAmount =
                credit != null
                        ? credit
                        : BigDecimal.ZERO;

        return debitAmount.max(
                creditAmount
        );
    }


    // ============================================================
    // SAFE DEBIT SETTER
    // ============================================================

    /**
     * Keeps the financial value as BigDecimal.
     *
     * Do not introduce Double here. Monetary values must remain
     * decimal throughout the accounting layer.
     */
    public void setDebit(BigDecimal value) {

        this.debit =
                value != null
                        ? value
                        : BigDecimal.ZERO;
    }


    // ============================================================
    // SAFE CREDIT SETTER
    // ============================================================

    /**
     * Keeps the financial value as BigDecimal.
     *
     * Do not introduce Double here. Monetary values must remain
     * decimal throughout the accounting layer.
     */
    public void setCredit(BigDecimal value) {

        this.credit =
                value != null
                        ? value
                        : BigDecimal.ZERO;
    }


    // ============================================================
    // DECIMAL ACCESSORS
    // ============================================================

    /**
     * Explicit decimal accessor for financial services.
     */
    @Transient
    @JsonIgnore
    public BigDecimal getDebitDecimal() {

        return debit != null
                ? debit
                : BigDecimal.ZERO;
    }


    /**
     * Explicit decimal accessor for financial services.
     */
    @Transient
    @JsonIgnore
    public BigDecimal getCreditDecimal() {

        return credit != null
                ? credit
                : BigDecimal.ZERO;
    }
}