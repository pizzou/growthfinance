package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.BnrBreakdownRow;
import com.patrick.fintech.loan_backend.dto.regulatory.BnrSummaryReport;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.RegulatoryReportingService;
import com.patrick.fintech.loan_backend.service.RegulatoryReportingService.ReportPeriod;
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
 * Staff-facing BNR regulatory report screens: preview in the dashboard and export to
 * PDF/Excel/CSV. Authenticated the normal way (JWT). The read-only feed that BNR's own
 * systems pull programmatically lives at /api/regulatory/external/bnr/** in
 * {@link RegulatoryExternalController}, protected by API key instead.
 */
@RestController
@RequestMapping("/api/regulatory/bnr")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','AUDITOR')")
public class BnrReportController {

    private final RegulatoryReportingService reportingService;
    private final ReportExportService exportService;
    private final AuditService auditService;
    private final CurrentUserUtil currentUserUtil;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<BnrSummaryReport>> summary(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false, defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        BnrSummaryReport report = reportingService.buildBnrSummary(orgId, branchId, period, parseDate(from), parseDate(to));
        auditService.log(currentUserUtil.getCurrentUser().getOrganization(), currentUserUtil.getCurrentUser(),
            "VIEW", "BnrReport", period.name(), "Viewed BNR portfolio summary (" + period + ")",
            null, null, "Regulatory Reporting");
        return ResponseEntity.ok(ApiResponse.ok(report));
    }

    @GetMapping("/breakdown/loan-type")
    public ResponseEntity<ApiResponse<List<BnrBreakdownRow>>> byLoanType(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false, defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        return ResponseEntity.ok(ApiResponse.ok(reportingService.breakdownByLoanType(orgId, branchId, period, parseDate(from), parseDate(to))));
    }

    @GetMapping("/breakdown/branch")
    public ResponseEntity<ApiResponse<List<BnrBreakdownRow>>> byBranch(
            @RequestParam(required = false, defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        return ResponseEntity.ok(ApiResponse.ok(reportingService.breakdownByBranch(orgId, period, parseDate(from), parseDate(to))));
    }

    @GetMapping("/breakdown/gender")
    public ResponseEntity<ApiResponse<List<BnrBreakdownRow>>> byGender(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false, defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        return ResponseEntity.ok(ApiResponse.ok(reportingService.breakdownByGender(orgId, branchId, period, parseDate(from), parseDate(to))));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false, defaultValue = "MONTHLY") ReportPeriod period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        BnrSummaryReport summary = reportingService.buildBnrSummary(orgId, branchId, period, parseDate(from), parseDate(to));
        String orgName = currentUserUtil.getCurrentUser().getOrganization().getName();

        List<String> columns = List.of("Metric", "Value");
        List<Map<String, Object>> rows = summaryToRows(summary);
        String filename = "BNR-Portfolio-Summary-" + LocalDate.now().format(DateTimeFormatter.ISO_DATE);

        auditService.log(currentUserUtil.getCurrentUser().getOrganization(), currentUserUtil.getCurrentUser(),
            "EXPORT", "BnrReport", period.name(), "Exported BNR portfolio summary as " + format.toUpperCase(),
            null, null, "Regulatory Reporting");

        return respond(format, filename, "BNR Loan Portfolio Summary", columns, rows, orgName);
    }

    private LocalDate parseDate(String s) {
        return (s == null || s.isBlank()) ? null : LocalDate.parse(s);
    }

    private List<Map<String, Object>> summaryToRows(BnrSummaryReport s) {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        java.util.function.BiConsumer<String, Object> add = (k, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("Metric", k); m.put("Value", v);
            rows.add(m);
        };
        add.accept("Report Period", s.getReportPeriod() + " (" + s.getPeriodStart() + " to " + s.getPeriodEnd() + ")");
        add.accept("Institution", s.getOrganizationName() + (s.getBnrInstitutionCode() != null ? " (" + s.getBnrInstitutionCode() + ")" : ""));
        add.accept("Total Loans Issued", s.getTotalLoansIssued());
        add.accept("Active Loans", s.getActiveLoans());
        add.accept("Closed Loans", s.getClosedLoans());
        add.accept("Pending Loans", s.getPendingLoans());
        add.accept("Rejected Loans", s.getRejectedLoans());
        add.accept("Overdue Loans", s.getOverdueLoans());
        add.accept("Defaulted / Written-off Loans", s.getDefaultedLoans());
        add.accept("Total Principal Disbursed (" + s.getCurrency() + ")", s.getTotalPrincipalDisbursed());
        add.accept("Outstanding Principal (" + s.getCurrency() + ")", s.getOutstandingPrincipal());
        add.accept("Total Interest Collected (" + s.getCurrency() + ")", s.getTotalInterestCollected());
        add.accept("Interest Accrued but Unpaid (" + s.getCurrency() + ")", s.getInterestAccruedUnpaid());
        add.accept("Total Processing Fees (" + s.getCurrency() + ")", s.getTotalProcessingFees());
        add.accept("Male Borrowers", s.getMaleBorrowers());
        add.accept("Female Borrowers", s.getFemaleBorrowers());
        add.accept("Other / Unspecified", s.getOtherGenderBorrowers());
        add.accept("Portfolio at Risk (PAR) Amount", s.getParAmount());
        add.accept("PAR Ratio", String.format("%.2f%%", s.getParRatio() * 100));
        add.accept("NPL Amount (>90 days)", s.getNplAmount());
        add.accept("NPL Ratio", String.format("%.2f%%", s.getNplRatio() * 100));
        return rows;
    }

    private ResponseEntity<byte[]> respond(String format, String filename, String title,
                                            List<String> columns, List<Map<String, Object>> rows, String orgName) {
        byte[] bytes;
        MediaType contentType;
        String ext;
        switch (format.toLowerCase()) {
            case "csv" -> { bytes = toCsv(columns, rows); contentType = MediaType.parseMediaType("text/csv"); ext = "csv"; }
            case "pdf" -> { bytes = exportService.toPdf(title, columns, rows, orgName); contentType = MediaType.APPLICATION_PDF; ext = "pdf"; }
            default -> { bytes = exportService.toExcel(title, columns, rows); contentType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"); ext = "xlsx"; }
        }
        return ResponseEntity.ok()
            .contentType(contentType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "." + ext + "\"")
            .body(bytes);
    }

    static byte[] toCsv(List<String> columns, List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", columns)).append("\n");
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < columns.size(); i++) {
                Object v = row.get(columns.get(i));
                String cell = v == null ? "" : v.toString().replace("\"", "\"\"");
                if (cell.contains(",") || cell.contains("\"") || cell.contains("\n")) cell = "\"" + cell + "\"";
                sb.append(cell);
                if (i < columns.size() - 1) sb.append(",");
            }
            sb.append("\n");
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}