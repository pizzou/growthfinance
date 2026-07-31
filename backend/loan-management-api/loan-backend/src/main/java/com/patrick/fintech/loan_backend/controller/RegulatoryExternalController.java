package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.BnrBreakdownRow;
import com.patrick.fintech.loan_backend.dto.regulatory.BnrSummaryReport;
import com.patrick.fintech.loan_backend.dto.regulatory.CreditBureauRecord;
import com.patrick.fintech.loan_backend.security.RegulatoryApiPrincipal;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.RegulatoryReportingService;
import com.patrick.fintech.loan_backend.service.RegulatoryReportingService.ReportPeriod;
import com.patrick.fintech.loan_backend.service.ReportExportService;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The actual integration surface: what BNR's systems and an authorized credit bureau's
 * systems call directly, authenticated with an X-Api-Key issued via
 * /api/regulatory/api-clients (see RegulatoryApiClientController).
 *
 * Every call is org-scoped to whichever tenant the key belongs to (never a request
 * parameter — a BNR key can only ever read its own institution's data) and every access
 * is written to the audit log, per the requirement that "every access should be recorded".
 */
@RestController
@RequestMapping("/api/regulatory/external")
@RequiredArgsConstructor
public class RegulatoryExternalController {

    private final RegulatoryReportingService reportingService;
    private final ReportExportService exportService;
    private final AuditService auditService;
    private final OrganizationRepository organizationRepository;

    private RegulatoryApiPrincipal principal() {
        return (RegulatoryApiPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private void audit(String action, String description) {
        RegulatoryApiPrincipal p = principal();
        auditService.log(organizationRepository.findById(p.getOrganizationId()).orElse(null), null,
            action, "RegulatoryApiAccess", p.getClientName(),
            "[" + p.getClientType() + " API: " + p.getClientName() + "] " + description,
            null, null, "Regulatory Reporting");
    }

    // ---- BNR ----

    @GetMapping("/bnr/summary")
    @PreAuthorize("hasAuthority('ROLE_BNR_API')")
    public ResponseEntity<ApiResponse<BnrSummaryReport>> bnrSummary(
            @RequestParam(required = false, defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Long orgId = principal().getOrganizationId();
        BnrSummaryReport report = reportingService.buildBnrSummary(orgId, null, period, parseDate(from), parseDate(to));
        audit("VIEW", "Fetched BNR portfolio summary (" + period + ")");
        return ResponseEntity.ok(ApiResponse.ok(report));
    }

    @GetMapping("/bnr/breakdown/loan-type")
    @PreAuthorize("hasAuthority('ROLE_BNR_API')")
    public ResponseEntity<ApiResponse<List<BnrBreakdownRow>>> bnrByLoanType(
            @RequestParam(required = false, defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Long orgId = principal().getOrganizationId();
        List<BnrBreakdownRow> rows = reportingService.breakdownByLoanType(orgId, null, period, parseDate(from), parseDate(to));
        audit("VIEW", "Fetched BNR loan-type breakdown (" + period + ")");
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    @GetMapping("/bnr/breakdown/branch")
    @PreAuthorize("hasAuthority('ROLE_BNR_API')")
    public ResponseEntity<ApiResponse<List<BnrBreakdownRow>>> bnrByBranch(
            @RequestParam(required = false, defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Long orgId = principal().getOrganizationId();
        List<BnrBreakdownRow> rows = reportingService.breakdownByBranch(orgId, period, parseDate(from), parseDate(to));
        audit("VIEW", "Fetched BNR branch breakdown (" + period + ")");
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    @GetMapping("/bnr/breakdown/gender")
    @PreAuthorize("hasAuthority('ROLE_BNR_API')")
    public ResponseEntity<ApiResponse<List<BnrBreakdownRow>>> bnrByGender(
            @RequestParam(required = false, defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Long orgId = principal().getOrganizationId();
        List<BnrBreakdownRow> rows = reportingService.breakdownByGender(orgId, null, period, parseDate(from), parseDate(to));
        audit("VIEW", "Fetched BNR gender breakdown (" + period + ")");
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    @GetMapping(value = "/bnr/export", produces = { "application/json", "text/csv",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", MediaType.APPLICATION_PDF_VALUE })
    @PreAuthorize("hasAuthority('ROLE_BNR_API')")
    public ResponseEntity<?> bnrExport(
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(required = false, defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Long orgId = principal().getOrganizationId();
        BnrSummaryReport summary = reportingService.buildBnrSummary(orgId, null, period, parseDate(from), parseDate(to));
        audit("EXPORT", "Exported BNR portfolio summary as " + format.toUpperCase() + " (" + period + ")");

        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok(ApiResponse.ok(summary));
        }
        String orgName = organizationRepository.findById(orgId).map(o -> o.getName()).orElse("");
        List<String> columns = List.of("Metric", "Value");
        List<Map<String, Object>> rows = flattenSummary(summary);
        return fileResponse(format, "bnr-summary", "BNR Loan Portfolio Summary", columns, rows, orgName);
    }

    // ---- Credit Bureau ----

    @GetMapping(value = "/credit-bureau/export", produces = { "application/json", "text/csv",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", MediaType.APPLICATION_PDF_VALUE })
    @PreAuthorize("hasAuthority('ROLE_CREDIT_BUREAU_API')")
    public ResponseEntity<?> creditBureauExport(
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Long orgId = principal().getOrganizationId();
        List<CreditBureauRecord> records = reportingService.buildCreditBureauExport(orgId, null, parseDate(from), parseDate(to));
        audit("EXPORT", "Exported " + records.size() + " borrower credit records as " + format.toUpperCase());

        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok(ApiResponse.ok(records));
        }
        String orgName = organizationRepository.findById(orgId).map(o -> o.getName()).orElse("");
        List<String> columns = List.of("National ID", "Full Name", "Date of Birth", "Gender", "Phone",
            "Loan Number", "Loan Type", "Loan Amount", "Outstanding Balance", "Status", "Days Past Due",
            "Credit Score", "Date Opened", "Last Payment", "Date Closed", "Branch");
        List<Map<String, Object>> rows = records.stream().map(r -> {
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
        }).toList();
        return fileResponse(format, "credit-bureau-export", "Credit Bureau Export", columns, rows, orgName);
    }

    // ---- shared helpers ----

    private ResponseEntity<byte[]> fileResponse(String format, String filenameBase, String title,
                                                 List<String> columns, List<Map<String, Object>> rows, String orgName) {
        byte[] bytes;
        MediaType contentType;
        String ext;
        switch (format.toLowerCase()) {
            case "csv" -> { bytes = BnrReportController.toCsv(columns, rows); contentType = MediaType.parseMediaType("text/csv"); ext = "csv"; }
            case "pdf" -> { bytes = exportService.toPdf(title, columns, rows, orgName); contentType = MediaType.APPLICATION_PDF; ext = "pdf"; }
            default -> { bytes = exportService.toExcel(title, columns, rows); contentType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); ext = "xlsx"; }
        }
        return ResponseEntity.ok()
            .contentType(contentType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filenameBase + "." + ext + "\"")
            .body(bytes);
    }

    private List<Map<String, Object>> flattenSummary(BnrSummaryReport s) {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        java.util.function.BiConsumer<String, Object> add = (k, v) -> {
            Map<String, Object> m = new LinkedHashMap<>(); m.put("Metric", k); m.put("Value", v); rows.add(m);
        };
        add.accept("Total Loans Issued", s.getTotalLoansIssued());
        add.accept("Active Loans", s.getActiveLoans());
        add.accept("Overdue Loans", s.getOverdueLoans());
        add.accept("Total Principal Disbursed", s.getTotalPrincipalDisbursed());
        add.accept("Outstanding Principal", s.getOutstandingPrincipal());
        add.accept("Total Interest Collected", s.getTotalInterestCollected());
        add.accept("Interest Accrued Unpaid", s.getInterestAccruedUnpaid());
        add.accept("Male Borrowers", s.getMaleBorrowers());
        add.accept("Female Borrowers", s.getFemaleBorrowers());
        add.accept("PAR Ratio", s.getParRatio());
        add.accept("NPL Ratio", s.getNplRatio());
        return rows;
    }

    private LocalDate parseDate(String s) {
        return (s == null || s.isBlank()) ? null : LocalDate.parse(s);
    }
}