package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * A specific cash-in-hand or bank account the institution actually holds — as opposed to the
 * single generic "1000 Cash and Bank" GL account that automatic loan postings use. Each one is
 * backed by its own dedicated ChartOfAccount sub-ledger (see BankAccountService#create), so
 * "how much is in the Kigali branch petty cash drawer" is a real, separately reconcilable number.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "bank_accounts", indexes = @Index(name = "idx_bank_account_org", columnList = "organization_id"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class BankAccount {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch; // null = organization-wide (head office) account

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chart_of_account_id", nullable = false)
    private ChartOfAccount glAccount; // the dedicated sub-ledger account backing this bank/cash account

    private String name;          // e.g. "Kigali Branch Petty Cash", "Bank of Kigali - Main Account"
    private String accountType;   // CASH or BANK
    private String bankName;      // null for CASH
    private String accountNumber; // null for CASH

    @Builder.Default
    private Boolean active = true;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
