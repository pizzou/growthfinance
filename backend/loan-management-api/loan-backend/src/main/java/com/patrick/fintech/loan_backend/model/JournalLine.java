
package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
    name = "journal_lines",
    indexes = @Index(
        name = "idx_journal_line_entry",
        columnList = "journal_entry_id"
    )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "journal_entry_id",
        nullable = false
    )
    private JournalEntry journalEntry;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "account_id",
        nullable = false
    )
    private ChartOfAccount account;

    @Builder.Default
    @Column(
        nullable = false,
        precision = 19,
        scale = 2
    )
    private BigDecimal debit = BigDecimal.ZERO.setScale(2);

    @Builder.Default
    @Column(
        nullable = false,
        precision = 19,
        scale = 2
    )
    private BigDecimal credit = BigDecimal.ZERO.setScale(2);

    private String description;

    /**
     * Returns debit as a primitive double for legacy integrations.
     */
    public double getDebitDouble() {
        return debit != null
            ? debit.doubleValue()
            : 0.0d;
    }

    /**
     * Returns credit as a primitive double for legacy integrations.
     */
    public double getCreditDouble() {
        return credit != null
            ? credit.doubleValue()
            : 0.0d;
    }
}
