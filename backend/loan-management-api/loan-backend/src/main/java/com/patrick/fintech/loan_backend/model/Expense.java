package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
    name = "expenses",
    indexes = {
        @Index(name = "idx_expenses_org", columnList = "organization_id"),
        @Index(name = "idx_expenses_date", columnList = "expense_date"),
        @Index(name = "idx_expenses_category", columnList = "category"),
        @Index(name = "idx_expenses_branch", columnList = "branch_id"),
        @Index(name = "idx_expenses_payment_method", columnList = "payment_method"),
        @Index(name = "idx_expenses_payment_reference", columnList = "payment_transaction_reference")
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

    // ============================================================
    // ORGANIZATION
    // ============================================================

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;


    // ============================================================
    // BRANCH
    // ============================================================

    /**
     * Branch that incurred the expense.
     *
     * Null means Head Office / organization-wide expense.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Branch branch;



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


   
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.CASH;


    @Column(name = "payment_provider", length = 100)
    private String paymentProvider;


    @Column(name = "payment_phone_number", length = 30)
    private String paymentPhoneNumber;


    @Column(name = "payment_transaction_reference", length = 150)
    private String paymentTransactionReference;


   
    @Column(name = "payment_code", length = 100)
    private String paymentCode;


    @Column(name = "card_brand", length = 30)
    private String cardBrand;


   
    @Column(name = "card_last_four", length = 4)
    private String cardLastFour;


   
    @Column(name = "card_authorization_code", length = 100)
    private String cardAuthorizationCode;


    @Column(name = "cheque_number", length = 100)
    private String chequeNumber;


    
    @Column(name = "payment_notes", columnDefinition = "TEXT")
    private String paymentNotes;


    // ============================================================
    // RECEIPT
    // ============================================================

    @Column(name = "receipt_file_name")
    private String receiptFileName;

    @Column(name = "receipt_file_type")
    private String receiptFileType;

    @Column(name = "receipt_file_size")
    private Long receiptFileSize;

    @JsonIgnore
    @Column(name = "receipt_data", columnDefinition = "bytea")
    private byte[] receiptData;


    // ============================================================
    // STATUS
    // ============================================================

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private Status status = Status.POSTED;


    // ============================================================
    // ACCOUNTING
    // ============================================================

    /**
     * Journal entry created when this expense was posted.
     */
    @Column(name = "journal_entry_id")
    private Long journalEntryId;


    // ============================================================
    // AUDIT
    // ============================================================

    @Column(name = "created_by_name")
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "void_reason", columnDefinition = "TEXT")
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

        if (currency == null || currency.isBlank()) {
            currency = "RWF";
        }

        if (paymentMethod == null) {
            paymentMethod = PaymentMethod.CASH;
        }
    }


    // ============================================================
    // RECEIPT HELPER
    // ============================================================

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

        /**
         * Physical cash.
         */
        CASH,

        /**
         * Bank transfer.
         */
        BANK_TRANSFER,

        /**
         * Mobile money transaction.
         */
        MOBILE_MONEY,

        /**
         * MoMo Pay / merchant payment.
         */
        MOMO_PAY,

        /**
         * Debit or credit card.
         */
        CARD,

        /**
         * Cheque.
         */
        CHEQUE,

        /**
         * Other payment method.
         */
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