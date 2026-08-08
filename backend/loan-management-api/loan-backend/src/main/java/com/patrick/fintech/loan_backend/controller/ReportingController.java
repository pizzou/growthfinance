package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.service.ReportingService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportingController {

    private final ReportingService reportingService;
    private final CurrentUserUtil currentUserUtil;

    public ReportingController(
            ReportingService reportingService,
            CurrentUserUtil currentUserUtil) {

        this.reportingService = reportingService;
        this.currentUserUtil = currentUserUtil;
    }

    // ============================================================
    // REPORT DATA
    // ============================================================

    @GetMapping("/loans/{orgId}")
    public ResponseEntity<Map<String, Long>> loanStatusReport(
            @PathVariable Long orgId) {

        verifyOrganization(orgId);

        return ResponseEntity.ok(
                reportingService.loanStatusReport(orgId)
        );
    }

    @GetMapping("/payments/{orgId}")
    public ResponseEntity<Map<String, Double>> paymentReport(
            @PathVariable Long orgId) {

        verifyOrganization(orgId);

        return ResponseEntity.ok(
                reportingService.paymentReport(orgId)
        );
    }

    // ============================================================
    // CSV EXPORTS
    // ============================================================

    @GetMapping("/export/loans")
    public ResponseEntity<String> exportLoansCsv() {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        return csvResponse(
                reportingService.exportLoansCsv(organizationId),
                "loans"
        );
    }

    @GetMapping("/export/payments")
    public ResponseEntity<String> exportPaymentsCsv() {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        return csvResponse(
                reportingService.exportPaymentsCsv(organizationId),
                "payments"
        );
    }

    @GetMapping("/export/overdue")
    public ResponseEntity<String> exportOverdueCsv() {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        return csvResponse(
                reportingService.exportOverdueCsv(organizationId),
                "overdue-payments"
        );
    }

    @GetMapping("/export/summary")
    public ResponseEntity<String> exportSummaryCsv() {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        return csvResponse(
                reportingService.exportPortfolioSummaryCsv(organizationId),
                "portfolio-summary"
        );
    }

    // ============================================================
    // EXCEL EXPORTS
    // ============================================================

    @GetMapping("/export/loans/excel")
    public ResponseEntity<byte[]> exportLoansExcel() {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        return excelResponse(
                reportingService.exportLoansExcel(organizationId),
                "loans"
        );
    }

    @GetMapping("/export/payments/excel")
    public ResponseEntity<byte[]> exportPaymentsExcel() {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        return excelResponse(
                reportingService.exportPaymentsExcel(organizationId),
                "payments"
        );
    }

    @GetMapping("/export/overdue/excel")
    public ResponseEntity<byte[]> exportOverdueExcel() {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        return excelResponse(
                reportingService.exportOverdueExcel(organizationId),
                "overdue-payments"
        );
    }

    @GetMapping("/export/summary/excel")
    public ResponseEntity<byte[]> exportSummaryExcel() {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        return excelResponse(
                reportingService.exportPortfolioSummaryExcel(organizationId),
                "portfolio-summary"
        );
    }

    // ============================================================
    // SECURITY
    // ============================================================

    private void verifyOrganization(Long organizationId) {

        Long currentOrganizationId =
                currentUserUtil.getCurrentOrganizationId();

        if (currentOrganizationId == null
                || !organizationId.equals(currentOrganizationId)) {

            throw new RuntimeException("Access denied");
        }
    }

    // ============================================================
    // RESPONSE HELPERS
    // ============================================================

    private ResponseEntity<String> csvResponse(
            String csv,
            String filename) {

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_TYPE,
                        "text/csv; charset=UTF-8"
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""
                                + filename
                                + "-"
                                + LocalDate.now()
                                + ".csv\""
                )
                .body(csv);
    }

    private ResponseEntity<byte[]> excelResponse(
            byte[] excel,
            String filename) {

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""
                                + filename
                                + "-"
                                + LocalDate.now()
                                + ".xlsx\""
                )
                .body(excel);
    }
}