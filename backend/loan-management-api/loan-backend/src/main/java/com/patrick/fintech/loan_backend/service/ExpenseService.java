
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    // ============================================================
    // RECEIPT CONFIGURATION
    // ============================================================

    private static final Set<String> ALLOWED_RECEIPT_TYPES =
        Set.of(
            "application/pdf",
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
        );

    private static final long MAX_RECEIPT_BYTES =
        8L * 1024L * 1024L;

    private static final int MONEY_SCALE = 6;

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
        BigDecimal amount,
        Long paymentAccountId,
        Long branchId,
        String description,
        String createdByName,

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

        if (org.getId() == null) {
            throw new IllegalArgumentException(
                "Organization ID is required"
            );
        }

        if (amount == null) {
            throw new IllegalArgumentException(
                "Expense amount is required"
            );
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "Expense amount must be greater than zero"
            );
        }

        amount = normalizeAmount(amount);

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
        // PAYMENT ACCOUNT
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

        if (paymentAccount.getActive() != null
            && !paymentAccount.getActive()) {

            throw new IllegalArgumentException(
                "Payment account is inactive and cannot be used"
            );
        }

        // ========================================================
        // BRANCH
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
                            "Branch not found: "
                                + branchId
                        )
                    );
        }

        // ========================================================
        // PAYMENT METHOD
        // ========================================================

        if (paymentMethod == null) {
            paymentMethod =
                Expense.PaymentMethod.CASH;
        }

        // ========================================================
        // CLEAN PAYMENT DATA
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

        description =
            clean(description);

        createdByName =
            clean(createdByName);

        // ========================================================
        // VALIDATE PAYMENT DETAILS
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
                .organization(org)
                .branch(branch)
                .paymentAccount(paymentAccount)
                .expenseDate(
                    expenseDate != null
                        ? expenseDate
                        : LocalDate.now()
                )
                .category(category)
                .amount(amount)
                .currency("RWF")
                .description(description)
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
                .status(Expense.Status.POSTED)
                .createdByName(createdByName)
                .build();

        // ========================================================
        // RECEIPT
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
            accountingService.postExpense(
                expense
            );

        // ========================================================
        // STORE JOURNAL ENTRY ID
        // ========================================================

        if (entry != null
            && entry.getId() != null) {

            expense.setJournalEntryId(
                entry.getId()
            );

            expense =
                expenseRepository.save(
                    expense
                );
        }

        return expense;
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

        if (orgId == null) {
            throw new IllegalArgumentException(
                "Organization ID is required"
            );
        }

        if (pageable == null) {
            throw new IllegalArgumentException(
                "Pageable is required"
            );
        }

        if (from != null
            && to != null
            && from.isAfter(to)) {

            throw new IllegalArgumentException(
                "From date cannot be after to date"
            );
        }

        return expenseRepository.findByFilters(
            orgId,
            category,
            branchId,
            from,
            to,
            pageable
        );
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

        return expenseRepository
            .findByIdAndOrganization_Id(
                id,
                orgId
            )
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "Expense not found: " + id
                )
            );
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

        if (expense.getStatus()
            == Expense.Status.VOID) {

            throw new IllegalStateException(
                "Expense " + id
                    + " is already void"
            );
        }

        String cleanedReason =
            clean(reason);

        if (cleanedReason == null) {
            throw new IllegalArgumentException(
                "Void reason is required"
            );
        }

        String cleanedVoidedBy =
            clean(voidedBy);

        if (cleanedVoidedBy == null) {
            throw new IllegalArgumentException(
                "Voided by user is required"
            );
        }

        // ========================================================
        // REVERSE GENERAL LEDGER ENTRY
        // ========================================================

        if (expense.getJournalEntryId() != null) {

            accountingService.reverseExpense(
                orgId,
                expense.getJournalEntryId(),
                cleanedVoidedBy,
                cleanedReason
            );
        }

        // ========================================================
        // MARK EXPENSE VOID
        // ========================================================

        expense.setStatus(
            Expense.Status.VOID
        );

        expense.setVoidReason(
            cleanedReason
        );

        expense.setVoidedAt(
            LocalDateTime.now()
        );

        return expenseRepository.save(
            expense
        );
    }

    // ============================================================
    // SUMMARY
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
            to = LocalDate.now();
        }

        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                "From date cannot be after to date"
            );
        }

        List<Object[]> rows =
            expenseRepository.sumByCategory(
                orgId,
                from,
                to
            );

        Map<String, Object> byCategory =
            new LinkedHashMap<>();

        for (Object[] row : rows) {

            if (row == null
                || row.length < 2) {
                continue;
            }

            Expense.ExpenseCategory category =
                (Expense.ExpenseCategory) row[0];

            BigDecimal total =
                row[1] instanceof BigDecimal
                    ? (BigDecimal) row[1]
                    : row[1] == null
                        ? BigDecimal.ZERO
                        : new BigDecimal(
                            row[1].toString()
                        );

            byCategory.put(
                category.getLabel(),
                total
            );
        }

        BigDecimal total =
            expenseRepository.sumTotal(
                orgId,
                from,
                to
            );

        if (total == null) {
            total = BigDecimal.ZERO;
        }

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

        result.put(
            "total",
            total
        );

        return result;
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

        if (receipt.getSize()
            > MAX_RECEIPT_BYTES) {

            throw new IllegalArgumentException(
                "Maximum receipt file size is 8MB."
            );
        }

        String contentType =
            clean(receipt.getContentType());

        if (contentType == null
            || !ALLOWED_RECEIPT_TYPES.contains(
                contentType.toLowerCase(Locale.ROOT)
            )) {

            throw new IllegalArgumentException(
                "Unsupported receipt type. "
                    + "Allowed: PDF, JPG, PNG, WEBP."
            );
        }

        String originalFilename =
            clean(
                receipt.getOriginalFilename()
            );

        if (originalFilename != null
            && originalFilename.length() > 255) {

            originalFilename =
                originalFilename.substring(
                    0,
                    255
                );
        }

        expense.setReceiptFileName(
            originalFilename
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

            case CASH:

                break;

            case BANK_TRANSFER:

                if (isBlank(
                    paymentTransactionReference
                )) {

                    throw new IllegalArgumentException(
                        "Bank transaction/reference "
                            + "number is required"
                    );
                }

                break;

            case MOBILE_MONEY:

                if (isBlank(paymentProvider)) {

                    throw new IllegalArgumentException(
                        "Mobile money provider "
                            + "is required"
                    );
                }

                if (isBlank(paymentPhoneNumber)) {

                    throw new IllegalArgumentException(
                        "Mobile money phone number "
                            + "is required"
                    );
                }

                if (isBlank(
                    paymentTransactionReference
                )) {

                    throw new IllegalArgumentException(
                        "Mobile money transaction "
                            + "number is required"
                    );
                }

                break;

            case MOMO_PAY:

                if (isBlank(paymentProvider)) {

                    throw new IllegalArgumentException(
                        "MoMo Pay provider "
                            + "is required"
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
                        "MoMo Pay transaction "
                            + "number is required"
                    );
                }

                break;

            case CARD:

                if (isBlank(cardBrand)) {

                    throw new IllegalArgumentException(
                        "Card brand is required"
                    );
                }

                if (isBlank(cardLastFour)) {

                    throw new IllegalArgumentException(
                        "Last four digits of the "
                            + "card are required"
                    );
                }

                if (!cardLastFour.matches(
                    "\\d{4}"
                )) {

                    throw new IllegalArgumentException(
                        "Card last four digits must "
                            + "contain exactly 4 digits"
                    );
                }

                if (isBlank(
                    cardAuthorizationCode
                )
                    && isBlank(
                        paymentTransactionReference
                    )) {

                    throw new IllegalArgumentException(
                        "Card authorization code or "
                            + "transaction reference "
                            + "is required"
                    );
                }

                break;

            case CHEQUE:

                if (isBlank(chequeNumber)) {

                    throw new IllegalArgumentException(
                        "Cheque number is required"
                    );
                }

                break;

            case OTHER:

                if (isBlank(paymentNotes)
                    && isBlank(
                        paymentTransactionReference
                    )) {

                    throw new IllegalArgumentException(
                        "Payment reference or payment "
                            + "notes are required"
                    );
                }

                break;

            default:

                throw new IllegalArgumentException(
                    "Unsupported payment method"
                );
        }
    }

    // ============================================================
    // MONEY NORMALIZATION
    // ============================================================

    private BigDecimal normalizeAmount(
        BigDecimal amount
    ) {

        return amount.setScale(
            MONEY_SCALE,
            RoundingMode.HALF_UP
        );
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
