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
    // LOAN STATUS REPORT
    // ============================================================

    @GetMapping("/loans/{orgId}")
    public ResponseEntity<Map<String, Long>> loanStatusReport(
            @PathVariable Long orgId) {

        validateOrganization(orgId);

        return ResponseEntity.ok(
                reportingService.loanStatusReport(orgId)
        );
    }

    // ============================================================
    // PAYMENT REPORT
    // ============================================================

    @GetMapping("/payments/{orgId}")
    public ResponseEntity<Map<String, Double>> paymentReport(
            @PathVariable Long orgId) {

        validateOrganization(orgId);

        return ResponseEntity.ok(
                reportingService.paymentReport(orgId)
        );
    }

    // ============================================================
    // CSV - LOANS
    // ============================================================

    @GetMapping("/export/loans")
    public ResponseEntity<String> exportLoansCsv() {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        return csvResponse(
                reportingService.exportLoansCsv(
                        organizationId
                ),
                "loans"
        );
    }

    // ============================================================
    // EXCEL - LOANS
    // ============================================================

    @GetMapping("/export/loans/excel")
    public ResponseEntity<byte[]> exportLoansExcel() {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        return excelResponse(
                reportingService.exportLoansExcel(
                        organizationId
                ),
                "loans"
        );
    }

    // ============================================================
    // CSV - PAYMENTS
    // ============================================================

    @GetMapping("/export/payments")
    public ResponseEntity<String> exportPaymentsCsv() {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        return csvResponse(
                reportingService.exportPaymentsCsv(
                        organizationId
                ),
                "payments"
        );
    }

    // ============================================================
    // EXCEL - PAYMENTS
    // ============================================================

    @GetMapping("/export/payments/excel")
    public ResponseEntity<byte[]> exportPaymentsExcel() {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        return excelResponse(
                reportingService.exportPaymentsExcel(
                        organizationId
                ),
                "payments"
        );
    }

    // ============================================================
    // CSV - OVERDUE
    // ============================================================

    @GetMapping("/export/overdue")
    public ResponseEntity<String> exportOverdueCsv() {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        return csvResponse(
                reportingService.exportOverdueCsv(
                        organizationId
                ),
                "overdue-payments"
        );
    }

    // ============================================================
    // EXCEL - OVERDUE
    // ============================================================

    @GetMapping("/export/overdue/excel")
    public ResponseEntity<byte[]> exportOverdueExcel() {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        return excelResponse(
                reportingService.exportOverdueExcel(
                        organizationId
                ),
                "overdue-payments"
        );
    }

    // ============================================================
    // CSV - SUMMARY
    // ============================================================

    @GetMapping("/export/summary")
    public ResponseEntity<String> exportSummaryCsv() {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        return csvResponse(
                reportingService.exportPortfolioSummaryCsv(
                        organizationId
                ),
                "portfolio-summary"
        );
    }

    // ============================================================
    // EXCEL - SUMMARY
    // ============================================================

    @GetMapping("/export/summary/excel")
    public ResponseEntity<byte[]> exportSummaryExcel() {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        return excelResponse(
                reportingService.exportPortfolioSummaryExcel(
                        organizationId
                ),
                "portfolio-summary"
        );
    }

    // ============================================================
    // ORGANIZATION SECURITY
    // ============================================================

    private void validateOrganization(
            Long organizationId) {

        Long currentOrganizationId =
                currentUserUtil.getCurrentOrganizationId();

        if (currentOrganizationId == null
                || !organizationId.equals(currentOrganizationId)) {

            throw new RuntimeException(
                    "Access denied"
            );
        }
    }

    // ============================================================
    // CSV RESPONSE
    // ============================================================

    private ResponseEntity<String> csvResponse(
            String csv,
            String filename) {

        String finalFilename =
                filename
                        + "-"
                        + LocalDate.now()
                        + ".csv";

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_TYPE,
                        "text/csv; charset=UTF-8"
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""
                                + finalFilename
                                + "\""
                )
                .body(csv);
    }

    // ============================================================
    // EXCEL RESPONSE
    // ============================================================

    private ResponseEntity<byte[]> excelResponse(
            byte[] excel,
            String filename) {

        String finalFilename =
                filename
                        + "-"
                        + LocalDate.now()
                        + ".xlsx";

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        )
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""
                                + finalFilename
                                + "\""
                )
                .body(excel);
    }
}
