package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private static final Set<String> ALLOWED_RECEIPT_TYPES = Set.of(
        "application/pdf", "image/jpeg", "image/jpg", "image/png", "image/webp"
    );
    private static final long MAX_RECEIPT_BYTES = 8L * 1024 * 1024;

    private final ExpenseRepository expenseRepository;
    private final BankAccountRepository bankAccountRepository;
    private final BranchRepository branchRepository;
    private final AccountingService accountingService;

    @Transactional
    public Expense create(Organization org, LocalDate expenseDate, Expense.ExpenseCategory category,
                           Double amount, Long paymentAccountId, Long branchId, String description,
                           String createdByName, MultipartFile receipt) throws IOException {

        if (amount == null || amount <= 0)
            throw new IllegalArgumentException("Expense amount must be greater than zero");

        BankAccount paymentAccount = bankAccountRepository.findByIdAndOrganization_Id(paymentAccountId, org.getId())
            .orElseThrow(() -> new IllegalArgumentException("Payment account not found: " + paymentAccountId));

        Branch branch = null;
        if (branchId != null) {
            branch = branchRepository.findByIdAndOrganization_Id(branchId, org.getId())
                .orElseThrow(() -> new IllegalArgumentException("Branch not found: " + branchId));
        }

        Expense expense = Expense.builder()
            .organization(org)
            .branch(branch)
            .paymentAccount(paymentAccount)
            .expenseDate(expenseDate != null ? expenseDate : LocalDate.now())
            .category(category)
            .amount(amount)
            .description(description)
            .status(Expense.Status.POSTED)
            .createdByName(createdByName)
            .build();

        attachReceiptIfPresent(expense, receipt);

        expense = expenseRepository.save(expense);

        // Posting is the primary action here — if this throws, the whole save rolls back.
        JournalEntry entry = accountingService.postExpense(expense);
        expense.setJournalEntryId(entry.getId());

        return expenseRepository.save(expense);
    }

    private void attachReceiptIfPresent(Expense expense, MultipartFile receipt) throws IOException {
        if (receipt == null || receipt.isEmpty()) return;

        if (receipt.getSize() > MAX_RECEIPT_BYTES)
            throw new IllegalArgumentException("Maximum receipt file size is 8MB.");

        String contentType = receipt.getContentType();
        if (contentType == null || !ALLOWED_RECEIPT_TYPES.contains(contentType.toLowerCase()))
            throw new IllegalArgumentException("Unsupported receipt type. Allowed: PDF, JPG, PNG, WEBP.");

        expense.setReceiptFileName(receipt.getOriginalFilename());
        expense.setReceiptFileType(contentType);
        expense.setReceiptFileSize(receipt.getSize());
        expense.setReceiptData(receipt.getBytes());
    }

    public Page<Expense> list(Long orgId, Expense.ExpenseCategory category, Long branchId,
                               LocalDate from, LocalDate to, Pageable pageable) {
        return expenseRepository.findByFilters(orgId, category, branchId, from, to, pageable);
    }

    public Expense getForOrg(Long id, Long orgId) {
        return expenseRepository.findByIdAndOrganization_Id(id, orgId)
            .orElseThrow(() -> new IllegalArgumentException("Expense not found: " + id));
    }

    @Transactional
    public Expense voidExpense(Long id, Long orgId, String voidedBy, String reason) {
        Expense expense = getForOrg(id, orgId);
        if (expense.getStatus() == Expense.Status.VOID)
            throw new IllegalStateException("Expense " + id + " is already void");

        if (expense.getJournalEntryId() != null) {
            accountingService.reverseExpense(orgId, expense.getJournalEntryId(), voidedBy, reason);
        }

        expense.setStatus(Expense.Status.VOID);
        expense.setVoidReason(reason);
        expense.setVoidedAt(LocalDateTime.now());
        return expenseRepository.save(expense);
    }


    public Map<String, Object> summary(Long orgId, LocalDate from, LocalDate to) {
        List<Object[]> rows = expenseRepository.sumByCategory(orgId, from, to);
        Map<String, Object> byCategory = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Expense.ExpenseCategory cat = (Expense.ExpenseCategory) row[0];
            byCategory.put(cat.getLabel(), row[1]);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from);
        result.put("to", to);
        result.put("byCategory", byCategory);
        result.put("total", expenseRepository.sumTotal(orgId, from, to));
        return result;
    }
}