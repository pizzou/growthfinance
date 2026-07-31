package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.service.ReportingService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportingController {

    private final ReportingService reportingService;
    private final CurrentUserUtil  currentUserUtil;

    public ReportingController(ReportingService reportingService, CurrentUserUtil currentUserUtil) {
        this.reportingService = reportingService;
        this.currentUserUtil  = currentUserUtil;
    }

    @GetMapping("/loans/{orgId}")
    public ResponseEntity<Map<String, Long>> loanStatusReport(@PathVariable Long orgId) {
        if (!orgId.equals(currentUserUtil.getCurrentOrganizationId())) throw new RuntimeException("Access denied");
        return ResponseEntity.ok(reportingService.loanStatusReport(orgId));
    }

    @GetMapping("/payments/{orgId}")
    public ResponseEntity<Map<String, Double>> paymentReport(@PathVariable Long orgId) {
        if (!orgId.equals(currentUserUtil.getCurrentOrganizationId())) throw new RuntimeException("Access denied");
        return ResponseEntity.ok(reportingService.paymentReport(orgId));
    }

    // ---- Exports — orgId is taken from the caller's own session, not the path, so a staff
    // member can never export another organization's data by editing the URL. ----

    @GetMapping("/export/loans")
    public ResponseEntity<String> exportLoans() {
        return csvResponse(reportingService.exportLoansCsv(currentUserUtil.getCurrentOrganizationId()), "loans");
    }

    @GetMapping("/export/payments")
    public ResponseEntity<String> exportPayments() {
        return csvResponse(reportingService.exportPaymentsCsv(currentUserUtil.getCurrentOrganizationId()), "payments");
    }

    @GetMapping("/export/overdue")
    public ResponseEntity<String> exportOverdue() {
        return csvResponse(reportingService.exportOverdueCsv(currentUserUtil.getCurrentOrganizationId()), "overdue-payments");
    }

    @GetMapping("/export/summary")
    public ResponseEntity<String> exportSummary() {
        return csvResponse(reportingService.exportPortfolioSummaryCsv(currentUserUtil.getCurrentOrganizationId()), "portfolio-summary");
    }

    private ResponseEntity<String> csvResponse(String csv, String filename) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "text/csv")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "-" + java.time.LocalDate.now() + ".csv\"")
            .body(csv);
    }
}