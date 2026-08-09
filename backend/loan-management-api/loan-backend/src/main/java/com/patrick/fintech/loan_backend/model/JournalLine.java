package com.patrick.fintech.loan_backend.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    @Column(nullable = false, precision = 19, scale = 6)
    @JsonProperty("debit")
    private BigDecimal debit = BigDecimal.ZERO;


    // ============================================================
    // CREDIT
    // ============================================================

    @Builder.Default
    @Column(nullable = false, precision = 19, scale = 6)
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
    // NORMALIZE
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


   

    @Transient
    public double getAmount() {

        double debitAmount =
                debit != null
                        ? debit.doubleValue()
                        : 0.0;

        double creditAmount =
                credit != null
                        ? credit.doubleValue()
                        : 0.0;

        return Math.max(
                debitAmount,
                creditAmount
        );
    }
    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getDebitDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getDebit() {
        return debit == null ? null : debit.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getDebitDecimal() {
        return debit;
    }

    @Deprecated
    public void setDebit(Double value) {
        this.debit = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setDebit(BigDecimal value) {
        this.debit = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getCreditDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getCredit() {
        return credit == null ? null : credit.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getCreditDecimal() {
        return credit;
    }

    @Deprecated
    public void setCredit(Double value) {
        this.credit = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setCredit(BigDecimal value) {
        this.credit = value;
    }

    /** Backward-compatible builder overloads for legacy Double callers.
     *  Financial state is stored as BigDecimal.
     */
    public static class JournalLineBuilder {

        private BigDecimal debit;
        private BigDecimal credit;


        public JournalLineBuilder debit(Double value) {
            this.debit = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public JournalLineBuilder credit(Double value) {
            this.credit = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }        public JournalLineBuilder debit(BigDecimal value) {
            this.debit = value;
            return this;
        }
        public JournalLineBuilder credit(BigDecimal value) {
            this.credit = value;
            return this;
        }
    }

}
