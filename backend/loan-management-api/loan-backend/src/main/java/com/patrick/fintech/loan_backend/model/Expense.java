package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single operating expense (rent, salaries, utilities, etc.) paid from one of the
 * institution's bank/cash accounts. Unlike loan disbursements/payments, which the system
 * posts to the ledger automatically, an Expense is the primary record — recording it IS
 * the transaction, so posting it to the general ledger happens synchronously and any
 * posting failure rolls the whole creation back (see AccountingService#postExpense).
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
    name = "expenses",
    indexes = {
        @Index(name = "idx_expenses_org", columnList = "organization_id"),
        @Index(name = "idx_expenses_date", columnList = "expense_date"),
        @Index(name = "idx_expenses_category", columnList = "category"),
        @Index(name = "idx_expenses_branch", columnList = "branch_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    /** Which branch incurred the expense — null for head-office / org-wide costs. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Branch branch;

    /** Which bank/cash account actually paid for this — determines the GL credit side. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_account_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private BankAccount paymentAccount;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ExpenseCategory category;

    @Column(nullable = false)
    private Double amount;

    @Builder.Default
    @Column(length = 3)
    private String currency = "RWF";

    @Column(columnDefinition = "TEXT")
    private String description;

    // ---- Receipt attachment (same pattern as BorrowerFile) ----

    @Column(name = "receipt_file_name")
    private String receiptFileName;

    @Column(name = "receipt_file_type")
    private String receiptFileType;

    @Column(name = "receipt_file_size")
    private Long receiptFileSize;

    @JsonIgnore
    @Column(name = "receipt_data", columnDefinition = "bytea")
    private byte[] receiptData;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private Status status = Status.POSTED;

    /** The journal entry this expense posted — used to reverse it if voided. */
    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "created_by_name")
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "void_reason", columnDefinition = "TEXT")
    private String voidReason;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = Status.POSTED;
        if (currency == null || currency.isBlank()) currency = "RWF";
    }

    public boolean hasReceipt() {
        return receiptData != null && receiptData.length > 0;
    }

    public enum Status {
        POSTED,
        VOID
    }

    /** Each category maps 1:1 to its own Chart of Accounts expense line (see
     *  AccountingService.DEFAULT_ACCOUNTS) so the P&L breaks expenses down properly
     *  instead of dumping everything into one generic bucket. */
    public enum ExpenseCategory {

        SALARIES_AND_WAGES     ("Salaries and Wages",       "5200"),
        RENT                    ("Rent",                     "5201"),
        UTILITIES               ("Utilities",                "5202"),
        INTERNET                ("Internet",                 "5203"),
        TRANSPORT               ("Transport",                "5204"),
        FUEL                    ("Fuel",                      "5205"),
        OFFICE_SUPPLIES         ("Office Supplies",          "5206"),
        BANK_CHARGES            ("Bank Charges",             "5207"),
        INSURANCE               ("Insurance",                "5208"),
        MARKETING               ("Marketing",                "5209"),
        LEGAL_FEES              ("Legal Fees",               "5210"),
        AUDIT_FEES              ("Audit Fees",               "5211"),
        DEPRECIATION            ("Depreciation",             "5212"),
        LOAN_RECOVERY_EXPENSES  ("Loan Recovery Expenses",   "5213"),
        IT_EXPENSES             ("IT Expenses",              "5214"),
        OTHER_OPERATING_EXPENSES("Other Operating Expenses", "5215");

        private final String label;
        private final String accountCode;

        ExpenseCategory(String label, String accountCode) {
            this.label = label;
            this.accountCode = accountCode;
        }

        public String getLabel() { return label; }
        public String getAccountCode() { return accountCode; }
    }
}