
package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
    name = "expenses",
    indexes = {
        @Index(
            name = "idx_expenses_org",
            columnList = "organization_id"
        ),
        @Index(
            name = "idx_expenses_date",
            columnList = "expense_date"
        ),
        @Index(
            name = "idx_expenses_category",
            columnList = "category"
        ),
        @Index(
            name = "idx_expenses_branch",
            columnList = "branch_id"
        ),
        @Index(
            name = "idx_expenses_payment_method",
            columnList = "payment_method"
        ),
        @Index(
            name = "idx_expenses_payment_reference",
            columnList = "payment_transaction_reference"
        ),
        @Index(
            name = "idx_expenses_payment_account",
            columnList = "payment_account_id"
        ),
        @Index(
            name = "idx_expenses_journal_entry",
            columnList = "journal_entry_id"
        )
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense {

    // ============================================================
    // ID
    // ============================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ============================================================
    // ORGANIZATION
    // ============================================================

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "organization_id",
        nullable = false
    )
    private Organization organization;

    // ============================================================
    // BRANCH
    // ============================================================

    /**
     * Branch that incurred the expense.
     *
     * Null means the expense is organization-wide /
     * Head Office.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    @JsonIgnoreProperties({
        "hibernateLazyInitializer",
        "handler"
    })
    private Branch branch;

    // ============================================================
    // PAYMENT ACCOUNT
    // ============================================================

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "payment_account_id",
        nullable = false
    )
    @JsonIgnoreProperties({
        "hibernateLazyInitializer",
        "handler"
    })
    private BankAccount paymentAccount;

    // ============================================================
    // EXPENSE DETAILS
    // ============================================================

    @Column(
        name = "expense_date",
        nullable = false
    )
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "category",
        nullable = false,
        length = 50
    )
    private ExpenseCategory category;

    /**
     * Financial amount.
     *
     * PostgreSQL:
     * NUMERIC(19,6)
     *
     * Never use Double for financial calculations.
     */
    @Column(
        name = "amount",
        nullable = false,
        precision = 19,
        scale = 6
    )
    @JsonProperty("amount")
    private BigDecimal amount;

    @Builder.Default
    @Column(
        name = "currency",
        nullable = false,
        length = 3
    )
    private String currency = "RWF";

    @Column(
        name = "description",
        columnDefinition = "TEXT"
    )
    private String description;

    // ============================================================
    // PAYMENT INFORMATION
    // ============================================================

    @Enumerated(EnumType.STRING)
    @Column(
        name = "payment_method",
        nullable = false,
        length = 30
    )
    @Builder.Default
    private PaymentMethod paymentMethod =
        PaymentMethod.CASH;

    @Column(
        name = "payment_provider",
        length = 100
    )
    private String paymentProvider;

    @Column(
        name = "payment_phone_number",
        length = 30
    )
    private String paymentPhoneNumber;

    @Column(
        name = "payment_transaction_reference",
        length = 150
    )
    private String paymentTransactionReference;

    @Column(
        name = "payment_code",
        length = 100
    )
    private String paymentCode;

    @Column(
        name = "card_brand",
        length = 30
    )
    private String cardBrand;

    @Column(
        name = "card_last_four",
        length = 4
    )
    private String cardLastFour;

    @Column(
        name = "card_authorization_code",
        length = 100
    )
    private String cardAuthorizationCode;

    @Column(
        name = "cheque_number",
        length = 100
    )
    private String chequeNumber;

    @Column(
        name = "payment_notes",
        columnDefinition = "TEXT"
    )
    private String paymentNotes;

    // ============================================================
    // RECEIPT
    // ============================================================

    @Column(
        name = "receipt_file_name",
        length = 255
    )
    private String receiptFileName;

    @Column(
        name = "receipt_file_type",
        length = 100
    )
    private String receiptFileType;

    @Column(name = "receipt_file_size")
    private Long receiptFileSize;

    /**
     * Receipt binary data.
     *
     * JSON serialization is disabled.
     */
    @JsonIgnore
    @Column(
        name = "receipt_data",
        columnDefinition = "bytea"
    )
    private byte[] receiptData;

    // ============================================================
    // STATUS
    // ============================================================

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(
        name = "status",
        nullable = false,
        length = 20
    )
    private Status status = Status.POSTED;

    // ============================================================
    // ACCOUNTING
    // ============================================================

    /**
     * General-ledger journal entry created when the
     * expense is posted.
     */
    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    // ============================================================
    // AUDIT
    // ============================================================

    @Column(
        name = "created_by_name",
        length = 255
    )
    private String createdByName;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private LocalDateTime createdAt;

    @Column(
        name = "void_reason",
        columnDefinition = "TEXT"
    )
    private String voidReason;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    // ============================================================
    // PRE-PERSIST
    // ============================================================

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (status == null) {
            status = Status.POSTED;
        }

        if (currency == null ||
            currency.isBlank()) {

            currency = "RWF";
        }

        if (paymentMethod == null) {
            paymentMethod = PaymentMethod.CASH;
        }

        normalizeAmount();
    }

    // ============================================================
    // PRE-UPDATE
    // ============================================================

    @PreUpdate
    protected void onUpdate() {
        normalizeAmount();
    }

    private void normalizeAmount() {

        if (amount != null) {

            amount = amount.setScale(
                6,
                RoundingMode.HALF_UP
            );
        }
    }

    // ============================================================
    // RECEIPT HELPER
    // ============================================================

    @JsonIgnore
    public boolean hasReceipt() {

        return receiptData != null &&
               receiptData.length > 0;
    }

    // ============================================================
    // STATUS
    // ============================================================

    public enum Status {

        POSTED,
        VOID
    }

    // ============================================================
    // PAYMENT METHOD
    // ============================================================

    public enum PaymentMethod {

        CASH,

        BANK_TRANSFER,

        MOBILE_MONEY,

        MOMO_PAY,

        CARD,

        CHEQUE,

        OTHER
    }

    // ============================================================
    // EXPENSE CATEGORIES
    // ============================================================

    public enum ExpenseCategory {

        SALARIES_AND_WAGES(
            "Salaries and Wages",
            "5200"
        ),

        RENT(
            "Rent",
            "5201"
        ),

        UTILITIES(
            "Utilities",
            "5202"
        ),

        INTERNET(
            "Internet",
            "5203"
        ),

        TRANSPORT(
            "Transport",
            "5204"
        ),

        FUEL(
            "Fuel",
            "5205"
        ),

        OFFICE_SUPPLIES(
            "Office Supplies",
            "5206"
        ),

        BANK_CHARGES(
            "Bank Charges",
            "5207"
        ),

        INSURANCE(
            "Insurance",
            "5208"
        ),

        MARKETING(
            "Marketing",
            "5209"
        ),

        LEGAL_FEES(
            "Legal Fees",
            "5210"
        ),

        AUDIT_FEES(
            "Audit Fees",
            "5211"
        ),

        DEPRECIATION(
            "Depreciation",
            "5212"
        ),

        LOAN_RECOVERY_EXPENSES(
            "Loan Recovery Expenses",
            "5213"
        ),

        IT_EXPENSES(
            "IT Expenses",
            "5214"
        ),

        OTHER_OPERATING_EXPENSES(
            "Other Operating Expenses",
            "5215"
        );

        private final String label;
        private final String accountCode;

        ExpenseCategory(
            String label,
            String accountCode
        ) {
            this.label = label;
            this.accountCode = accountCode;
        }

        public String getLabel() {
            return label;
        }

        public String getAccountCode() {
            return accountCode;
        }
    }
}
