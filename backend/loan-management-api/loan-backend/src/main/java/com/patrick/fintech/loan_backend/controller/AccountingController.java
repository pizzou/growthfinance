package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.ChartOfAccount;
import com.patrick.fintech.loan_backend.model.JournalEntry;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.ChartOfAccountRepository;
import com.patrick.fintech.loan_backend.repository.JournalEntryRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.service.AccountingService;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.ReportExportService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounting")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','ACCOUNTANT')")
public class AccountingController {

    private final AccountingService        accountingService;
    private final ChartOfAccountRepository  coaRepo;
    private final JournalEntryRepository    journalRepo;
    private final OrganizationRepository     orgRepo;
    private final CurrentUserUtil            currentUserUtil;
    private final AuditService               auditService;
    private final ReportExportService      exportService;

    // ---- Chart of Accounts ----

    @GetMapping("/chart-of-accounts")
    public ResponseEntity<ApiResponse<List<ChartOfAccount>>> chartOfAccounts() {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        Organization org = orgRepo.findById(orgId).orElseThrow(() -> new RuntimeException("Organization not found"));
        accountingService.ensureChartOfAccounts(org); // self-heal for orgs that predate this feature
        return ResponseEntity.ok(ApiResponse.ok(coaRepo.findByOrganization_IdOrderByCodeAsc(orgId)));
    }

    @PostMapping("/chart-of-accounts")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<ChartOfAccount>> createAccount(@RequestBody Map<String,String> body) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        Organization org = orgRepo.findById(orgId).orElseThrow(() -> new RuntimeException("Organization not found"));
        ChartOfAccount created = accountingService.createAccount(org, body.get("code"), body.get("name"),
            ChartOfAccount.AccountType.valueOf(body.get("type")),
            ChartOfAccount.NormalBalance.valueOf(body.get("normalBalance")));
        auditService.log(org, currentUserUtil.getCurrentUser(), "COA_ACCOUNT_CREATED", "CHART_OF_ACCOUNT",
            String.valueOf(created.getId()), "Created account " + created.getCode() + " — " + created.getName(),
            null, null, "Accounting");
        return ResponseEntity.ok(ApiResponse.ok("Account created", created));
    }

    @PutMapping("/chart-of-accounts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<ChartOfAccount>> updateAccount(@PathVariable Long id, @RequestBody Map<String,Object> body) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        String name = body.get("name") != null ? body.get("name").toString() : null;
        Boolean active = body.get("active") != null ? Boolean.valueOf(body.get("active").toString()) : null;
        ChartOfAccount updated = accountingService.updateAccount(orgId, id, name, active);
        auditService.log(updated.getOrganization(), currentUserUtil.getCurrentUser(), "COA_ACCOUNT_UPDATED", "CHART_OF_ACCOUNT",
            String.valueOf(id), "Updated account " + updated.getCode(), null, null, "Accounting");
        return ResponseEntity.ok(ApiResponse.ok("Account updated", updated));
    }

    // ---- Journal ----

    @GetMapping("/journal")
    public ResponseEntity<ApiResponse<List<JournalEntry>>> journal() {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        return ResponseEntity.ok(ApiResponse.ok(journalRepo.findByOrganization_IdOrderByEntryDateDesc(orgId)));
    }

    @PostMapping("/journal/{id}/reverse")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<JournalEntry>> reverseEntry(@PathVariable Long id, @RequestBody(required = false) Map<String,String> body) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        String reason = body != null ? body.get("reason") : null;
        JournalEntry reversal = accountingService.reverseEntry(orgId, id, currentUserUtil.getCurrentUser().getName(), reason);
        auditService.log(reversal.getOrganization(), currentUserUtil.getCurrentUser(), "JOURNAL_ENTRY_REVERSED", "JOURNAL_ENTRY",
            String.valueOf(id), "Reversed entry #" + id + (reason != null && !reason.isBlank() ? ": " + reason : ""),
            null, null, "Accounting");
        return ResponseEntity.ok(ApiResponse.ok("Entry reversed", reversal));
    }

    // ---- General Ledger ----

    @GetMapping("/ledger/{accountId}")
    public ResponseEntity<ApiResponse<Map<String,Object>>> ledger(@PathVariable Long accountId) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        return ResponseEntity.ok(ApiResponse.ok(accountingService.getLedger(orgId, accountId)));
    }

    // ---- Reports ----

    @GetMapping("/trial-balance")
    public ResponseEntity<ApiResponse<Map<String,Object>>> trialBalance() {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        return ResponseEntity.ok(ApiResponse.ok(accountingService.getTrialBalance(orgId)));
    }

    @GetMapping("/balance-sheet")
    public ResponseEntity<ApiResponse<Map<String,Object>>> balanceSheet() {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        return ResponseEntity.ok(ApiResponse.ok(accountingService.getBalanceSheet(orgId)));
    }

    @GetMapping("/profit-and-loss")
    public ResponseEntity<ApiResponse<Map<String,Object>>> profitAndLoss(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        LocalDate fromDate = from != null ? LocalDate.parse(from) : LocalDate.now().withDayOfMonth(1);
        LocalDate toDate   = to   != null ? LocalDate.parse(to)   : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(accountingService.getProfitAndLoss(orgId, fromDate, toDate)));
    }

    @GetMapping("/cash-flow")
    public ResponseEntity<ApiResponse<Map<String,Object>>> cashFlow(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        LocalDate fromDate = from != null ? LocalDate.parse(from) : LocalDate.now().withDayOfMonth(1);
        LocalDate toDate   = to   != null ? LocalDate.parse(to)   : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(accountingService.getCashFlow(orgId, fromDate, toDate)));
    }

    @GetMapping("/branch-summary")
    public ResponseEntity<ApiResponse<List<Map<String,Object>>>> branchSummary(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        LocalDate fromDate = from != null ? LocalDate.parse(from) : LocalDate.now().withDayOfMonth(1);
        LocalDate toDate   = to   != null ? LocalDate.parse(to)   : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(accountingService.getBranchSummary(orgId, fromDate, toDate)));
    }

    // ---- Exports (Phase 10) ----

    @GetMapping("/trial-balance/export")
    public ResponseEntity<String> exportTrialBalance() {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        Map<String,Object> tb = accountingService.getTrialBalance(orgId);
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> rows = (List<Map<String,Object>>) tb.get("accounts");
        StringBuilder csv = new StringBuilder("Code,Name,Type,Debit,Credit\n");
        for (Map<String,Object> r : rows)
            csv.append(r.get("code")).append(',').append(csvField(String.valueOf(r.get("name")))).append(',')
               .append(r.get("type")).append(',').append(r.get("debit")).append(',').append(r.get("credit")).append('\n');
        csv.append("TOTAL,,,").append(tb.get("totalDebit")).append(',').append(tb.get("totalCredit")).append('\n');
        return csvResponse(csv.toString(), "trial-balance");
    }

    @GetMapping("/balance-sheet/export")
    public ResponseEntity<String> exportBalanceSheet() {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        Map<String,Object> bs = accountingService.getBalanceSheet(orgId);
        StringBuilder csv = new StringBuilder("Section,Code,Name,Balance\n");
        appendSection(csv, "Assets", bs.get("assets"));
        appendSection(csv, "Liabilities", bs.get("liabilities"));
        appendSection(csv, "Equity", bs.get("equity"));
        csv.append("Total Assets,,,").append(bs.get("totalAssets")).append('\n');
        csv.append("Total Liabilities,,,").append(bs.get("totalLiabilities")).append('\n');
        csv.append("Total Equity,,,").append(bs.get("totalEquity")).append('\n');
        return csvResponse(csv.toString(), "balance-sheet");
    }

    @GetMapping("/trial-balance/export/excel")
    public ResponseEntity<byte[]> exportTrialBalanceExcel() {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        Map<String,Object> tb = accountingService.getTrialBalance(orgId);
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> rows = (List<Map<String,Object>>) tb.get("accounts");
        byte[] bytes = exportService.toExcel("Trial Balance",
            List.of("code", "name", "type", "debit", "credit"), rows);
        return fileResponse(bytes, "trial-balance.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @GetMapping("/trial-balance/export/pdf")
    public ResponseEntity<byte[]> exportTrialBalancePdf() {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        Organization org = orgRepo.findById(orgId).orElseThrow(() -> new RuntimeException("Organization not found"));
        Map<String,Object> tb = accountingService.getTrialBalance(orgId);
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> rows = (List<Map<String,Object>>) tb.get("accounts");
        byte[] bytes = exportService.toPdf("Trial Balance",
            List.of("code", "name", "type", "debit", "credit"), rows, org.getName());
        return fileResponse(bytes, "trial-balance.pdf", "application/pdf");
    }

    @GetMapping("/balance-sheet/export/excel")
    public ResponseEntity<byte[]> exportBalanceSheetExcel() {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        Map<String,Object> bs = accountingService.getBalanceSheet(orgId);
        byte[] bytes = exportService.toExcel("Balance Sheet",
            List.of("section", "code", "name", "balance"), flattenBalanceSheet(bs));
        return fileResponse(bytes, "balance-sheet.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @GetMapping("/balance-sheet/export/pdf")
    public ResponseEntity<byte[]> exportBalanceSheetPdf() {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        Organization org = orgRepo.findById(orgId).orElseThrow(() -> new RuntimeException("Organization not found"));
        Map<String,Object> bs = accountingService.getBalanceSheet(orgId);
        byte[] bytes = exportService.toPdf("Balance Sheet",
            List.of("section", "code", "name", "balance"), flattenBalanceSheet(bs), org.getName());
        return fileResponse(bytes, "balance-sheet.pdf", "application/pdf");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> flattenBalanceSheet(Map<String,Object> bs) {
        List<Map<String,Object>> flat = new java.util.ArrayList<>();
        for (var entry : Map.of("Assets", bs.get("assets"), "Liabilities", bs.get("liabilities"),
                                  "Equity", bs.get("equity")).entrySet()) {
            for (Map<String,Object> r : (List<Map<String,Object>>) entry.getValue()) {
                Map<String,Object> row = new java.util.LinkedHashMap<>();
                row.put("section", entry.getKey());
                row.put("code", r.get("code"));
                row.put("name", r.get("name"));
                row.put("balance", r.get("balance"));
                flat.add(row);
            }
        }
        return flat;
    }

    private ResponseEntity<byte[]> fileResponse(byte[] bytes, String filename, String contentType) {
        return ResponseEntity.ok()
            .header("Content-Type", contentType)
            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
            .body(bytes);
    }

    @SuppressWarnings("unchecked")
    private void appendSection(StringBuilder csv, String section, Object rowsObj) {
        for (Map<String,Object> r : (List<Map<String,Object>>) rowsObj)
            csv.append(section).append(',').append(r.get("code")).append(',')
               .append(csvField(String.valueOf(r.get("name")))).append(',').append(r.get("balance")).append('\n');
    }

    private String csvField(String v) {
        if (v == null) return "";
        return v.contains(",") || v.contains("\"") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    private ResponseEntity<String> csvResponse(String csv, String filename) {
        return ResponseEntity.ok()
            .header("Content-Type", "text/csv")
            .header("Content-Disposition", "attachment; filename=\"" + filename + ".csv\"")
            .body(csv);
    }
}
