package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.Expense;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.ExpenseService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','ACCOUNTANT')")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final OrganizationRepository orgRepo;
    private final CurrentUserUtil currentUserUtil;
    private final AuditService auditService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Expense>> create(
            @RequestParam("expenseDate") String expenseDate,
            @RequestParam("category") String category,
            @RequestParam("amount") Double amount,
            @RequestParam("paymentAccountId") Long paymentAccountId,
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "receipt", required = false) MultipartFile receipt) throws Exception {

        Organization org = orgRepo.findById(currentUserUtil.getCurrentOrganizationId())
            .orElseThrow(() -> new RuntimeException("Organization not found"));

        Expense created = expenseService.create(
            org,
            LocalDate.parse(expenseDate),
            Expense.ExpenseCategory.valueOf(category.toUpperCase()),
            amount,
            paymentAccountId,
            branchId,
            description,
            currentUserUtil.getCurrentUser().getName(),
            receipt
        );

        auditService.log(org, currentUserUtil.getCurrentUser(), "EXPENSE_RECORDED", "EXPENSE",
            String.valueOf(created.getId()),
            "Recorded " + created.getCategory().getLabel() + " expense of " + created.getAmount()
                + " " + created.getCurrency(),
            null, null, "Accounting");

        return ResponseEntity.ok(ApiResponse.ok("Expense recorded", created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Expense>>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long orgId = currentUserUtil.getCurrentOrganizationId();
        Expense.ExpenseCategory cat = category != null ? Expense.ExpenseCategory.valueOf(category.toUpperCase()) : null;
        LocalDate fromDate = from != null ? LocalDate.parse(from) : null;
        LocalDate toDate = to != null ? LocalDate.parse(to) : null;

        return ResponseEntity.ok(ApiResponse.ok(
            expenseService.list(orgId, cat, branchId, fromDate, toDate, PageRequest.of(page, size))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Expense>> get(@PathVariable Long id) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getForOrg(id, orgId)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        Long orgId = currentUserUtil.getCurrentOrganizationId();
        LocalDate fromDate = from != null ? LocalDate.parse(from) : LocalDate.now().withDayOfMonth(1);
        LocalDate toDate = to != null ? LocalDate.parse(to) : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(expenseService.summary(orgId, fromDate, toDate)));
    }

    @PatchMapping("/{id}/void")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<Expense>> voidExpense(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {

        Long orgId = currentUserUtil.getCurrentOrganizationId();
        String reason = body != null ? body.get("reason") : null;
        Expense voided = expenseService.voidExpense(id, orgId, currentUserUtil.getCurrentUser().getName(), reason);

        auditService.log(voided.getOrganization(), currentUserUtil.getCurrentUser(), "EXPENSE_VOIDED", "EXPENSE",
            String.valueOf(id), "Voided expense #" + id + (reason != null && !reason.isBlank() ? ": " + reason : ""),
            null, null, "Accounting");

        return ResponseEntity.ok(ApiResponse.ok("Expense voided", voided));
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<byte[]> receipt(@PathVariable Long id) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        Expense expense = expenseService.getForOrg(id, orgId);
        if (!expense.hasReceipt()) throw new RuntimeException("No receipt attached to this expense");

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(
                expense.getReceiptFileType() != null ? expense.getReceiptFileType() : "application/octet-stream"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + expense.getReceiptFileName() + "\"")
            .body(expense.getReceiptData());
    }
}