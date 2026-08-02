
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.BankAccount;
import com.patrick.fintech.loan_backend.model.Branch;
import com.patrick.fintech.loan_backend.model.Expense;
import com.patrick.fintech.loan_backend.model.JournalEntry;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.BankAccountRepository;
import com.patrick.fintech.loan_backend.repository.BranchRepository;
import com.patrick.fintech.loan_backend.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    // ============================================================
    // RECEIPT CONFIGURATION
    // ============================================================

    private static final Set<String> ALLOWED_RECEIPT_TYPES = Set.of(
        "application/pdf",
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp"
    );

    private static final long MAX_RECEIPT_BYTES =
        8L * 1024 * 1024;


    // ============================================================
    // REPOSITORIES / SERVICES
    // ============================================================

    private final ExpenseRepository expenseRepository;

    private final BankAccountRepository bankAccountRepository;

    private final BranchRepository branchRepository;

    private final AccountingService accountingService;


    // ============================================================
    // CREATE EXPENSE
    // ============================================================

    @Transactional
    public Expense create(
            Organization org,
            LocalDate expenseDate,
            Expense.ExpenseCategory category,
            Double amount,
            Long paymentAccountId,
            Long branchId,
            String description,
            String createdByName,

            // Payment information
            Expense.PaymentMethod paymentMethod,
            String paymentProvider,
            String paymentPhoneNumber,
            String paymentTransactionReference,
            String paymentCode,
            String cardBrand,
            String cardLastFour,
            String cardAuthorizationCode,
            String chequeNumber,
            String paymentNotes,

            MultipartFile receipt
    ) throws IOException {

        // ========================================================
        // BASIC VALIDATION
        // ========================================================

        if (org == null) {
            throw new IllegalArgumentException(
                "Organization is required"
            );
        }

        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException(
                "Expense amount must be greater than zero"
            );
        }

        /*
         * Keep the database column as DOUBLE PRECISION.
         *
         * BigDecimal is used ONLY here to avoid binary floating-point
         * rounding problems before the Double value is stored.
         *
         * Example:
         *
         * 20000       -> 20000.00 -> 20000.0
         * 19999.999   -> 20000.00
         * 19999.994   -> 19999.99
         */
        amount =
            BigDecimal.valueOf(amount)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();


        if (category == null) {
            throw new IllegalArgumentException(
                "Expense category is required"
            );
        }

        if (paymentAccountId == null) {
            throw new IllegalArgumentException(
                "Payment account is required"
            );
        }


        // ========================================================
        // FIND PAYMENT ACCOUNT
        // ========================================================

        BankAccount paymentAccount =
            bankAccountRepository
                .findByIdAndOrganization_Id(
                    paymentAccountId,
                    org.getId()
                )
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "Payment account not found: "
                            + paymentAccountId
                    )
                );


        // ========================================================
        // VERIFY PAYMENT ACCOUNT
        // ========================================================

        if (paymentAccount.getActive() != null
                && !paymentAccount.getActive()) {

            throw new IllegalArgumentException(
                "Payment account is inactive and cannot be used"
            );
        }


        /*
         * Make sure the required BankAccount relationships are
         * initialized while the Hibernate session is still open.
         *
         * This prevents:
         *
         * LazyInitializationException:
         * could not initialize proxy [BankAccount#X] - no Session
         */
        initializeBankAccount(paymentAccount);


        // ========================================================
        // FIND BRANCH
        // ========================================================

        Branch branch = null;

        if (branchId != null) {

            branch =
                branchRepository
                    .findByIdAndOrganization_Id(
                        branchId,
                        org.getId()
                    )
                    .orElseThrow(() ->
                        new IllegalArgumentException(
                            "Branch not found: " + branchId
                        )
                    );

            Hibernate.initialize(branch);
        }


        // ========================================================
        // PAYMENT METHOD
        // ========================================================

        if (paymentMethod == null) {
            paymentMethod =
                Expense.PaymentMethod.CASH;
        }


        // ========================================================
        // NORMALIZE PAYMENT INFORMATION
        // ========================================================

        paymentProvider =
            clean(paymentProvider);

        paymentPhoneNumber =
            clean(paymentPhoneNumber);

        paymentTransactionReference =
            clean(paymentTransactionReference);

        paymentCode =
            clean(paymentCode);

        cardBrand =
            clean(cardBrand);

        cardLastFour =
            clean(cardLastFour);

        cardAuthorizationCode =
            clean(cardAuthorizationCode);

        chequeNumber =
            clean(chequeNumber);

        paymentNotes =
            clean(paymentNotes);


        // ========================================================
        // VALIDATE PAYMENT INFORMATION
        // ========================================================

        validatePaymentDetails(
            paymentMethod,
            paymentProvider,
            paymentPhoneNumber,
            paymentTransactionReference,
            paymentCode,
            cardBrand,
            cardLastFour,
            cardAuthorizationCode,
            chequeNumber,
            paymentNotes
        );


        // ========================================================
        // CREATE EXPENSE
        // ========================================================

        Expense expense =
            Expense.builder()

                // Organization
                .organization(org)

                // Branch
                .branch(branch)

                // Payment account
                .paymentAccount(paymentAccount)

                // Expense date
                .expenseDate(
                    expenseDate != null
                        ? expenseDate
                        : LocalDate.now()
                )

                // Category
                .category(category)

                // Amount
                .amount(amount)

                // Currency
                .currency("RWF")

                // Description
                .description(description)

                // Payment information
                .paymentMethod(paymentMethod)

                .paymentProvider(paymentProvider)

                .paymentPhoneNumber(paymentPhoneNumber)

                .paymentTransactionReference(
                    paymentTransactionReference
                )

                .paymentCode(paymentCode)

                .cardBrand(cardBrand)

                .cardLastFour(cardLastFour)

                .cardAuthorizationCode(
                    cardAuthorizationCode
                )

                .chequeNumber(chequeNumber)

                .paymentNotes(paymentNotes)

                // Status
                .status(Expense.Status.POSTED)

                // Audit
                .createdByName(createdByName)

                .build();


        // ========================================================
        // ATTACH RECEIPT
        // ========================================================

        attachReceiptIfPresent(
            expense,
            receipt
        );


        // ========================================================
        // SAVE EXPENSE
        // ========================================================

        expense =
            expenseRepository.save(expense);


        // ========================================================
        // POST TO GENERAL LEDGER
        // ========================================================

        JournalEntry entry =
            accountingService.postExpense(expense);


        // ========================================================
        // STORE JOURNAL ENTRY ID
        // ========================================================

        if (entry != null) {

            expense.setJournalEntryId(
                entry.getId()
            );
        }


        // ========================================================
        // SAVE AGAIN
        // ========================================================

        expense =
            expenseRepository.save(expense);


        /*
         * Initialize relationships before leaving the transaction.
         *
         * The controller will eventually serialize this Expense as JSON.
         * At that point the Hibernate session may already be closed.
         */
        initializeExpense(expense);


        return expense;
    }


    // ============================================================
    // INITIALIZE EXPENSE RELATIONSHIPS
    // ============================================================

    /**
     * Initializes relationships that may be lazy-loaded before
     * the Expense object is serialized by Jackson.
     *
     * This is especially important for:
     *
     * Expense
     *   -> paymentAccount
     *       -> glAccount
     */
    private void initializeExpense(
            Expense expense
    ) {

        if (expense == null) {
            return;
        }

        // Payment account
        BankAccount paymentAccount =
            expense.getPaymentAccount();

        if (paymentAccount != null) {

            initializeBankAccount(
                paymentAccount
            );
        }


        // Branch
        if (expense.getBranch() != null) {

            Hibernate.initialize(
                expense.getBranch()
            );
        }


        // Organization
        if (expense.getOrganization() != null) {

            Hibernate.initialize(
                expense.getOrganization()
            );
        }
    }


    // ============================================================
    // INITIALIZE BANK ACCOUNT
    // ============================================================

    /**
     * Initializes the BankAccount and its GL account while
     * Hibernate session is active.
     *
     * This fixes:
     *
     * LazyInitializationException:
     *
     * could not initialize proxy
     * [BankAccount#X] - no Session
     */
    private void initializeBankAccount(
            BankAccount account
    ) {

        if (account == null) {
            return;
        }

        Hibernate.initialize(account);


        if (account.getGlAccount() != null) {

            Hibernate.initialize(
                account.getGlAccount()
            );
        }


        if (account.getBranch() != null) {

            Hibernate.initialize(
                account.getBranch()
            );
        }


        if (account.getOrganization() != null) {

            Hibernate.initialize(
                account.getOrganization()
            );
        }
    }


    // ============================================================
    // PAYMENT VALIDATION
    // ============================================================

    private void validatePaymentDetails(
            Expense.PaymentMethod paymentMethod,
            String paymentProvider,
            String paymentPhoneNumber,
            String paymentTransactionReference,
            String paymentCode,
            String cardBrand,
            String cardLastFour,
            String cardAuthorizationCode,
            String chequeNumber,
            String paymentNotes
    ) {

        switch (paymentMethod) {

            // ----------------------------------------------------
            // CASH
            // ----------------------------------------------------

            case CASH:

                /*
                 * Payment account identifies the cash account.
                 */
                break;


            // ----------------------------------------------------
            // BANK TRANSFER
            // ----------------------------------------------------

            case BANK_TRANSFER:

                if (isBlank(
                    paymentTransactionReference
                )) {

                    throw new IllegalArgumentException(
                        "Bank transaction/reference number is required"
                    );
                }

                break;


            // ----------------------------------------------------
            // MOBILE MONEY
            // ----------------------------------------------------

            case MOBILE_MONEY:

                if (isBlank(paymentProvider)) {

                    throw new IllegalArgumentException(
                        "Mobile money provider is required"
                    );
                }

                if (isBlank(paymentPhoneNumber)) {

                    throw new IllegalArgumentException(
                        "Mobile money phone number is required"
                    );
                }

                if (isBlank(
                    paymentTransactionReference
                )) {

                    throw new IllegalArgumentException(
                        "Mobile money transaction number is required"
                    );
                }

                break;


            // ----------------------------------------------------
            // MOMO PAY
            // ----------------------------------------------------

            case MOMO_PAY:

                if (isBlank(paymentProvider)) {

                    throw new IllegalArgumentException(
                        "MoMo Pay provider is required"
                    );
                }

                if (isBlank(paymentCode)) {

                    throw new IllegalArgumentException(
                        "MoMo Pay code is required"
                    );
                }

                if (isBlank(
                    paymentTransactionReference
                )) {

                    throw new IllegalArgumentException(
                        "MoMo Pay transaction number is required"
                    );
                }

                break;


            // ----------------------------------------------------
            // CARD
            // ----------------------------------------------------

            case CARD:

                if (isBlank(cardBrand)) {

                    throw new IllegalArgumentException(
                        "Card brand is required"
                    );
                }

                if (isBlank(cardLastFour)) {

                    throw new IllegalArgumentException(
                        "Last four digits of the card are required"
                    );
                }

                if (!cardLastFour.matches("\\d{4}")) {

                    throw new IllegalArgumentException(
                        "Card last four digits must contain exactly 4 digits"
                    );
                }

                if (isBlank(cardAuthorizationCode)
                        && isBlank(
                            paymentTransactionReference
                        )) {

                    throw new IllegalArgumentException(
                        "Card authorization code or transaction reference is required"
                    );
                }

                break;


            // ----------------------------------------------------
            // CHEQUE
            // ----------------------------------------------------

            case CHEQUE:

                if (isBlank(chequeNumber)) {

                    throw new IllegalArgumentException(
                        "Cheque number is required"
                    );
                }

                break;


            // ----------------------------------------------------
            // OTHER
            // ----------------------------------------------------

            case OTHER:

                if (isBlank(paymentNotes)
                        && isBlank(
                            paymentTransactionReference
                        )) {

                    throw new IllegalArgumentException(
                        "Payment reference or payment notes are required"
                    );
                }

                break;
        }
    }


    // ============================================================
    // RECEIPT
    // ============================================================

    private void attachReceiptIfPresent(
            Expense expense,
            MultipartFile receipt
    ) throws IOException {

        if (receipt == null
                || receipt.isEmpty()) {

            return;
        }


        // --------------------------------------------------------
        // FILE SIZE
        // --------------------------------------------------------

        if (receipt.getSize()
                > MAX_RECEIPT_BYTES) {

            throw new IllegalArgumentException(
                "Maximum receipt file size is 8MB."
            );
        }


        // --------------------------------------------------------
        // CONTENT TYPE
        // --------------------------------------------------------

        String contentType =
            receipt.getContentType();


        if (contentType == null
                || !ALLOWED_RECEIPT_TYPES.contains(
                    contentType.toLowerCase()
                )) {

            throw new IllegalArgumentException(
                "Unsupported receipt type. "
                    + "Allowed: PDF, JPG, PNG, WEBP."
            );
        }


        // --------------------------------------------------------
        // STORE RECEIPT
        // --------------------------------------------------------

        expense.setReceiptFileName(
            receipt.getOriginalFilename()
        );

        expense.setReceiptFileType(
            contentType
        );

        expense.setReceiptFileSize(
            receipt.getSize()
        );

        expense.setReceiptData(
            receipt.getBytes()
        );
    }


    // ============================================================
    // LIST EXPENSES
    // ============================================================

    @Transactional(readOnly = true)
    public Page<Expense> list(
            Long orgId,
            Expense.ExpenseCategory category,
            Long branchId,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    ) {

        // ========================================================
        // VALIDATION
        // ========================================================

        if (orgId == null) {

            throw new IllegalArgumentException(
                "Organization ID is required"
            );
        }


        // ========================================================
        // QUERY
        // ========================================================

        Page<Expense> page =
            expenseRepository.findByFilters(
                orgId,
                category,
                branchId,
                from,
                to,
                pageable
            );


        // ========================================================
        // INITIALIZE LAZY RELATIONSHIPS
        // ========================================================

        /*
         * IMPORTANT:
         *
         * Jackson serializes the Page AFTER this service method
         * returns.
         *
         * Therefore, simply having @Transactional on the method
         * is not enough unless we initialize the lazy relationships
         * before returning.
         */
        page.getContent()
            .forEach(this::initializeExpense);


        return page;
    }


    // ============================================================
    // GET ONE EXPENSE
    // ============================================================

    @Transactional(readOnly = true)
    public Expense getForOrg(
            Long id,
            Long orgId
    ) {

        if (id == null) {

            throw new IllegalArgumentException(
                "Expense ID is required"
            );
        }

        if (orgId == null) {

            throw new IllegalArgumentException(
                "Organization ID is required"
            );
        }


        Expense expense =
            expenseRepository
                .findByIdAndOrganization_Id(
                    id,
                    orgId
                )
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "Expense not found: " + id
                    )
                );


        initializeExpense(expense);


        return expense;
    }


    // ============================================================
    // VOID EXPENSE
    // ============================================================

    @Transactional
    public Expense voidExpense(
            Long id,
            Long orgId,
            String voidedBy,
            String reason
    ) {

        Expense expense =
            getForOrg(
                id,
                orgId
            );


        // --------------------------------------------------------
        // ALREADY VOID
        // --------------------------------------------------------

        if (expense.getStatus()
                == Expense.Status.VOID) {

            throw new IllegalStateException(
                "Expense "
                    + id
                    + " is already void"
            );
        }


        // --------------------------------------------------------
        // REVERSE JOURNAL ENTRY
        // --------------------------------------------------------

        if (expense.getJournalEntryId() != null) {

            accountingService.reverseExpense(
                orgId,
                expense.getJournalEntryId(),
                voidedBy,
                reason
            );
        }


        // --------------------------------------------------------
        // MARK EXPENSE VOID
        // --------------------------------------------------------

        expense.setStatus(
            Expense.Status.VOID
        );

        expense.setVoidReason(
            clean(reason)
        );

        expense.setVoidedAt(
            LocalDateTime.now()
        );


        expense =
            expenseRepository.save(
                expense
            );


        initializeExpense(expense);


        return expense;
    }


    // ============================================================
    // EXPENSE SUMMARY
    // ============================================================

    @Transactional(readOnly = true)
    public Map<String, Object> summary(
            Long orgId,
            LocalDate from,
            LocalDate to
    ) {

        if (orgId == null) {

            throw new IllegalArgumentException(
                "Organization ID is required"
            );
        }


        if (from == null) {

            from =
                LocalDate.now()
                    .withDayOfMonth(1);
        }


        if (to == null) {

            to =
                LocalDate.now();
        }


        if (from.isAfter(to)) {

            throw new IllegalArgumentException(
                "From date cannot be after to date"
            );
        }


        // --------------------------------------------------------
        // SUM BY CATEGORY
        // --------------------------------------------------------

        List<Object[]> rows =
            expenseRepository.sumByCategory(
                orgId,
                from,
                to
            );


        Map<String, Object> byCategory =
            new LinkedHashMap<>();


        for (Object[] row : rows) {

            Expense.ExpenseCategory category =
                (Expense.ExpenseCategory) row[0];

            Object total =
                row[1];


            /*
             * Normalize summary values to two decimal places
             * when they are numeric.
             */
            if (total instanceof Number) {

                total =
                    BigDecimal
                        .valueOf(
                            ((Number) total)
                                .doubleValue()
                        )
                        .setScale(
                            2,
                            RoundingMode.HALF_UP
                        )
                        .doubleValue();
            }


            byCategory.put(
                category.getLabel(),
                total
            );
        }


        // --------------------------------------------------------
        // RESULT
        // --------------------------------------------------------

        Map<String, Object> result =
            new LinkedHashMap<>();


        result.put(
            "from",
            from
        );

        result.put(
            "to",
            to
        );

        result.put(
            "byCategory",
            byCategory
        );


        Object total =
            expenseRepository.sumTotal(
                orgId,
                from,
                to
            );


        if (total instanceof Number) {

            total =
                BigDecimal
                    .valueOf(
                        ((Number) total)
                            .doubleValue()
                    )
                    .setScale(
                        2,
                        RoundingMode.HALF_UP
                    )
                    .doubleValue();
        }


        result.put(
            "total",
            total
        );


        return result;
    }


    // ============================================================
    // STRING HELPERS
    // ============================================================

    private String clean(
            String value
    ) {

        if (value == null) {
            return null;
        }


        String cleaned =
            value.trim();


        return cleaned.isEmpty()
            ? null
            : cleaned;
    }


    private boolean isBlank(
            String value
    ) {

        return value == null
            || value.trim().isEmpty();
    }
}
