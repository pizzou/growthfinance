package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
    name = "chart_of_accounts",
    indexes = {
        @Index(
            name = "idx_coa_org",
            columnList = "organization_id"
        ),
        @Index(
            name = "idx_coa_org_code",
            columnList = "organization_id, code"
        )
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_coa_organization_code",
            columnNames = {"organization_id", "code"}
        )
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChartOfAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Organization that owns this account.
     *
     * Even though the application currently uses a single organization,
     * keeping this relationship protects the accounting data model and
     * prevents accounts belonging to another organization from being used.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "organization_id",
        nullable = false
    )
    private Organization organization;

    /**
     * Accounting code.
     *
     * Examples:
     *
     * 1000 - Cash and Bank
     * 1100 - Loans Receivable
     * 4000 - Interest Income
     * 5100 - Operating Expenses
     */
    @Column(
        nullable = false,
        length = 20
    )
    private String code;

    /**
     * Human-readable account name.
     */
    @Column(
        nullable = false,
        length = 150
    )
    private String name;

    /**
     * Accounting classification.
     */
    @Enumerated(EnumType.STRING)
    @Column(
        nullable = false,
        length = 20
    )
    private AccountType type;

    /**
     * Determines how the account balance is calculated.
     *
     * ASSET / EXPENSE normally have DEBIT balances.
     * LIABILITY / EQUITY / INCOME normally have CREDIT balances.
     */
    @Enumerated(EnumType.STRING)
    @Column(
        nullable = false,
        length = 10
    )
    private NormalBalance normalBalance;

    /**
     * Inactive accounts remain in the ledger for historical reporting
     * but should no longer be available for new transactions.
     */
    @Builder.Default
    @Column(
        nullable = false
    )
    private Boolean active = true;

    public enum AccountType {
        ASSET,
        LIABILITY,
        EQUITY,
        INCOME,
        EXPENSE
    }

    public enum NormalBalance {
        DEBIT,
        CREDIT
    }

    /**
     * Returns the normal balance direction for this account.
     */
    @Transient
    public boolean isDebitNormal() {
        return normalBalance == NormalBalance.DEBIT;
    }

    /**
     * Returns the normal balance direction for this account.
     */
    @Transient
    public boolean isCreditNormal() {
        return normalBalance == NormalBalance.CREDIT;
    }

    /**
     * Automatically normalizes the account code.
     *
     * This prevents accidental duplicate accounts such as:
     *
     * "1000"
     * " 1000 "
     */
    @PrePersist
    @PreUpdate
    protected void normalize() {

        if (code != null) {
            code = code.trim();
        }

        if (name != null) {
            name = name.trim();
        }

        if (active == null) {
            active = true;
        }
    }
}