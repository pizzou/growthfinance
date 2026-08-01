package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.CreditBureauRecord;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.RegulatoryReportingService;
import com.patrick.fintech.loan_backend.service.ReportExportService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Staff-facing Credit Bureau export screen. Borrower-level credit data (identity +
 * repayment status) — access is intentionally narrower than BNR reports since this is
 * PII, not aggregate statistics: ADMIN and MANAGER only, no AUDITOR.
 *
 * The feed an actual credit bureau consumes programmatically lives at
 * /api/regulatory/external/credit-bureau/**, protected by API key.
 */
@RestController
@RequestMapping("/api/regulatory/credit-bureau")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class CreditBureauExportController {

    private final RegulatoryReportingService reportingService;
    private final ReportExportService exportService;
    private final AuditService auditService;
    private final CurrentUserUtil currentUserUtil;

    private static final List<String> COLUMNS = List.of(
        "National ID", "Full Name", "Date of Birth", "Gender", "Phone",
        "Loan Number", "Loan Type", "Loan Amount", "Outstanding Balance",
        "Status", "Days Past Due", "Credit Score", "Date Opened", "Last Payment", "Date Closed", "Branch"
    );

    @GetMapping("/preview")
    public ResponseEntity<ApiResponse<List<CreditBureauRecord>>> preview(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        List<CreditBureauRecord> records = reportingService.buildCreditBureauExport(orgId, branchId, parseDate(from), parseDate(to));
        auditService.log(currentUserUtil.getCurrentUser().getOrganization(), currentUserUtil.getCurrentUser(),
            "VIEW", "CreditBureauExport", "preview", "Previewed credit bureau export (" + records.size() + " records)",
            null, null, "Regulatory Reporting");
        return ResponseEntity.ok(ApiResponse.ok(records));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        List<CreditBureauRecord> records = reportingService.buildCreditBureauExport(orgId, branchId, parseDate(from), parseDate(to));
        String orgName = currentUserUtil.getCurrentUser().getOrganization().getName();
        List<Map<String, Object>> rows = records.stream().map(this::toRow).toList();
        String filename = "Credit-Bureau-Export-" + LocalDate.now().format(DateTimeFormatter.ISO_DATE);

        auditService.log(currentUserUtil.getCurrentUser().getOrganization(), currentUserUtil.getCurrentUser(),
            "EXPORT", "CreditBureauExport", "export",
            "Exported credit bureau data as " + format.toUpperCase() + " (" + records.size() + " borrower records)",
            null, null, "Regulatory Reporting");

        byte[] bytes;
        MediaType contentType;
        String ext;
        switch (format.toLowerCase()) {
            case "csv" -> { bytes = BnrReportController.toCsv(COLUMNS, rows); contentType = MediaType.parseMediaType("text/csv"); ext = "csv"; }
            case "pdf" -> { bytes = exportService.toPdf("Credit Bureau Export", COLUMNS, rows, orgName); contentType = MediaType.APPLICATION_PDF; ext = "pdf"; }
            default -> { bytes = exportService.toExcel("Credit Bureau Export", COLUMNS, rows); contentType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); ext = "xlsx"; }
        }
        return ResponseEntity.ok()
            .contentType(contentType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "." + ext + "\"")
            .body(bytes);
    }

    private Map<String, Object> toRow(CreditBureauRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("National ID", r.getNationalId());
        m.put("Full Name", r.getFullName());
        m.put("Date of Birth", r.getDateOfBirth());
        m.put("Gender", r.getGender());
        m.put("Phone", r.getPhone());
        m.put("Loan Number", r.getLoanNumber());
        m.put("Loan Type", r.getLoanType());
        m.put("Loan Amount", r.getLoanAmount());
        m.put("Outstanding Balance", r.getOutstandingBalance());
        m.put("Status", r.getLoanStatus());
        m.put("Days Past Due", r.getDaysPastDue());
        m.put("Credit Score", r.getCreditScore());
        m.put("Date Opened", r.getDateOpened());
        m.put("Last Payment", r.getLastPaymentDate());
        m.put("Date Closed", r.getDateClosed());
        m.put("Branch", r.getBranchName());
        return m;
    }

    private LocalDate parseDate(String s) {
        return (s == null || s.isBlank()) ? null : LocalDate.parse(s);
    }
}