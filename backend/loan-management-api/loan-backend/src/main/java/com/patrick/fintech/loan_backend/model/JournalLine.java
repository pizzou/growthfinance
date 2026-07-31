package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "journal_lines", indexes = @Index(name = "idx_journal_line_entry", columnList = "journal_entry_id"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class JournalLine {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    
    private JournalEntry journalEntry;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private ChartOfAccount account;

    @Builder.Default
    private Double debit = 0.0;
    @Builder.Default
    private Double credit = 0.0;

    private String description;
}
