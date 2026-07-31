package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.BranchRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.BankAccountService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bank-accounts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','ACCOUNTANT')")
public class BankAccountController {

    private final BankAccountService    bankAccountService;
    private final OrganizationRepository orgRepo;
    private final BranchRepository       branchRepo;
    private final CurrentUserUtil        currentUserUtil;
    private final AuditService           auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String,Object>>>> list() {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        List<Map<String,Object>> out = bankAccountService.list(orgId).stream().map(a -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", a.getId()); m.put("name", a.getName()); m.put("accountType", a.getAccountType());
            m.put("bankName", a.getBankName()); m.put("accountNumber", a.getAccountNumber());
            m.put("branchName", a.getBranch() != null ? a.getBranch().getName() : null);
            m.put("glAccountCode", a.getGlAccount().getCode());
            m.put("active", a.getActive());
            m.put("balance", bankAccountService.getBalance(a));
            return m;
        }).toList();
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<BankAccount>> create(@RequestBody Map<String,Object> body) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        Organization org = orgRepo.findById(orgId).orElseThrow(() -> new RuntimeException("Organization not found"));
        Branch branch = body.get("branchId") != null
            ? branchRepo.findById(Long.valueOf(body.get("branchId").toString())).orElse(null) : null;
        double opening = body.get("openingBalance") != null ? Double.parseDouble(body.get("openingBalance").toString()) : 0;

        BankAccount created = bankAccountService.create(org, branch,
            (String) body.get("name"), (String) body.get("accountType"),
            (String) body.get("bankName"), (String) body.get("accountNumber"),
            opening, currentUserUtil.getCurrentUser().getName());

        auditService.log(org, currentUserUtil.getCurrentUser(), "BANK_ACCOUNT_CREATED", "BANK_ACCOUNT",
            String.valueOf(created.getId()), "Created " + created.getAccountType() + " account: " + created.getName(),
            null, null, "Cashbook & Banking");
        return ResponseEntity.ok(ApiResponse.ok("Account created", created));
    }

    @PostMapping("/{id}/transactions")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','MANAGER')")
    public ResponseEntity<ApiResponse<JournalEntry>> recordTransaction(@PathVariable Long id, @RequestBody Map<String,Object> body) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        Organization org = orgRepo.findById(orgId).orElseThrow(() -> new RuntimeException("Organization not found"));
        String type = (String) body.get("type"); // DEPOSIT or WITHDRAWAL
        double amount = Double.parseDouble(body.get("amount").toString());
        Long counterAccountId = Long.valueOf(body.get("counterAccountId").toString());
        String description = (String) body.getOrDefault("description", type + " on bank account " + id);

        JournalEntry entry = bankAccountService.recordTransaction(org, id, type, amount, counterAccountId,
            description, currentUserUtil.getCurrentUser().getName());

        auditService.log(org, currentUserUtil.getCurrentUser(), "CASHBOOK_" + type.toUpperCase(), "BANK_ACCOUNT",
            String.valueOf(id), description + " (" + amount + ")", null, null, "Cashbook & Banking");
        return ResponseEntity.ok(ApiResponse.ok("Transaction recorded", entry));
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','MANAGER')")
    public ResponseEntity<ApiResponse<JournalEntry>> transfer(@RequestBody Map<String,Object> body) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        Organization org = orgRepo.findById(orgId).orElseThrow(() -> new RuntimeException("Organization not found"));
        Long fromId = Long.valueOf(body.get("fromAccountId").toString());
        Long toId   = Long.valueOf(body.get("toAccountId").toString());
        double amount = Double.parseDouble(body.get("amount").toString());
        String description = (String) body.get("description");

        JournalEntry entry = bankAccountService.transfer(org, fromId, toId, amount, description,
            currentUserUtil.getCurrentUser().getName());

        auditService.log(org, currentUserUtil.getCurrentUser(), "CASHBOOK_TRANSFER", "BANK_ACCOUNT",
            fromId + "->" + toId, "Transferred " + amount + " between accounts", null, null, "Cashbook & Banking");
        return ResponseEntity.ok(ApiResponse.ok("Transfer complete", entry));
    }
}
