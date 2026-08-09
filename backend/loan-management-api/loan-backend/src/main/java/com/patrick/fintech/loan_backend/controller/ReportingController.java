package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.service.ReportingService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportingController {

    private final ReportingService reportingService;
    private final CurrentUserUtil currentUserUtil;

    private static final MediaType EXCEL_MEDIA_TYPE =
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            );

    private static final MediaType CSV_MEDIA_TYPE =
            MediaType.parseMediaType(
                    "text/csv"
            );

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
    //
    // IMPORTANT:
    // Financial amounts are BigDecimal.
    // Do NOT change this back to Double.
    // ============================================================

    @GetMapping("/payments/{orgId}")
    public ResponseEntity<Map<String, java.math.BigDecimal>> paymentReport(
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
    public ResponseEntity<byte[]> exportLoansCsv() {

        Long organizationId =
                getCurrentOrganizationId();

        String csv =
                reportingService.exportLoansCsv(
                        organizationId
                );

        return csvResponse(
                csv,
                "loans"
        );
    }

    // ============================================================
    // EXCEL - LOANS
    // ============================================================

    @GetMapping("/export/loans/excel")
    public ResponseEntity<byte[]> exportLoansExcel() {

        Long organizationId =
                getCurrentOrganizationId();

        byte[] excel =
                reportingService.exportLoansExcel(
                        organizationId
                );

        return excelResponse(
                excel,
                "loans"
        );
    }

    // ============================================================
    // CSV - PAYMENTS
    // ============================================================

    @GetMapping("/export/payments")
    public ResponseEntity<byte[]> exportPaymentsCsv() {

        Long organizationId =
                getCurrentOrganizationId();

        String csv =
                reportingService.exportPaymentsCsv(
                        organizationId
                );

        return csvResponse(
                csv,
                "payments"
        );
    }

    // ============================================================
    // EXCEL - PAYMENTS
    // ============================================================

    @GetMapping("/export/payments/excel")
    public ResponseEntity<byte[]> exportPaymentsExcel() {

        Long organizationId =
                getCurrentOrganizationId();

        byte[] excel =
                reportingService.exportPaymentsExcel(
                        organizationId
                );

        return excelResponse(
                excel,
                "payments"
        );
    }

    // ============================================================
    // CSV - OVERDUE
    // ============================================================

    @GetMapping("/export/overdue")
    public ResponseEntity<byte[]> exportOverdueCsv() {

        Long organizationId =
                getCurrentOrganizationId();

        String csv =
                reportingService.exportOverdueCsv(
                        organizationId
                );

        return csvResponse(
                csv,
                "overdue-payments"
        );
    }

    // ============================================================
    // EXCEL - OVERDUE
    // ============================================================

    @GetMapping("/export/overdue/excel")
    public ResponseEntity<byte[]> exportOverdueExcel() {

        Long organizationId =
                getCurrentOrganizationId();

        byte[] excel =
                reportingService.exportOverdueExcel(
                        organizationId
                );

        return excelResponse(
                excel,
                "overdue-payments"
        );
    }

    // ============================================================
    // CSV - PORTFOLIO SUMMARY
    // ============================================================

    @GetMapping("/export/summary")
    public ResponseEntity<byte[]> exportSummaryCsv() {

        Long organizationId =
                getCurrentOrganizationId();

        String csv =
                reportingService.exportPortfolioSummaryCsv(
                        organizationId
                );

        return csvResponse(
                csv,
                "portfolio-summary"
        );
    }

    // ============================================================
    // EXCEL - PORTFOLIO SUMMARY
    // ============================================================

    @GetMapping("/export/summary/excel")
    public ResponseEntity<byte[]> exportSummaryExcel() {

        Long organizationId =
                getCurrentOrganizationId();

        byte[] excel =
                reportingService.exportPortfolioSummaryExcel(
                        organizationId
                );

        return excelResponse(
                excel,
                "portfolio-summary"
        );
    }

    // ============================================================
    // CURRENT ORGANIZATION
    // ============================================================

    private Long getCurrentOrganizationId() {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        if (organizationId == null) {

            log.warn(
                    "Report request rejected because no organization "
                            + "could be resolved for the authenticated user"
            );

            throw new IllegalStateException(
                    "Current organization could not be determined."
            );
        }

        return organizationId;
    }

    // ============================================================
    // ORGANIZATION SECURITY
    // ============================================================

    private void validateOrganization(
            Long requestedOrganizationId) {

        if (requestedOrganizationId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required."
            );
        }

        Long currentOrganizationId =
                currentUserUtil.getCurrentOrganizationId();

        if (currentOrganizationId == null) {

            log.warn(
                    "Report access denied: authenticated user has no "
                            + "resolved organization"
            );

            throw new IllegalStateException(
                    "Current organization could not be determined."
            );
        }

        if (!requestedOrganizationId.equals(
                currentOrganizationId
        )) {

            log.warn(
                    "Cross-organization report access blocked. "
                            + "requestedOrganizationId={}, currentOrganizationId={}",
                    requestedOrganizationId,
                    currentOrganizationId
            );

            throw new org.springframework.security.access.AccessDeniedException(
                    "Access denied."
            );
        }
    }

    // ============================================================
    // CSV RESPONSE
    // ============================================================

    private ResponseEntity<byte[]> csvResponse(
            String csv,
            String filename) {

        if (csv == null) {
            csv = "";
        }

        String finalFilename =
                sanitizeFilename(filename)
                        + "-"
                        + LocalDate.now()
                        + ".csv";

        byte[] body =
                csv.getBytes(
                        StandardCharsets.UTF_8
                );

        HttpHeaders headers =
                new HttpHeaders();

        headers.setContentType(
                new MediaType(
                        "text",
                        "csv",
                        StandardCharsets.UTF_8
                )
        );

        headers.setContentDisposition(
                ContentDisposition
                        .attachment()
                        .filename(
                                finalFilename,
                                StandardCharsets.UTF_8
                        )
                        .build()
        );

        headers.setContentLength(
                body.length
        );

        headers.setCacheControl(
                CacheControl
                        .noCache()
                        .noStore()
                        .mustRevalidate()
        );

        headers.add(
                HttpHeaders.PRAGMA,
                "no-cache"
        );

        headers.add(
                HttpHeaders.EXPIRES,
                "0"
        );

        return ResponseEntity
                .ok()
                .headers(headers)
                .body(body);
    }

    // ============================================================
    // EXCEL RESPONSE
    // ============================================================

    private ResponseEntity<byte[]> excelResponse(
            byte[] excel,
            String filename) {

        if (excel == null) {

            throw new IllegalStateException(
                    "Excel report generation returned no data."
            );
        }

        if (excel.length == 0) {

            throw new IllegalStateException(
                    "Excel report generation returned an empty file."
            );
        }

        String finalFilename =
                sanitizeFilename(filename)
                        + "-"
                        + LocalDate.now()
                        + ".xlsx";

        HttpHeaders headers =
                new HttpHeaders();

        headers.setContentType(
                EXCEL_MEDIA_TYPE
        );

        headers.setContentDisposition(
                ContentDisposition
                        .attachment()
                        .filename(
                                finalFilename,
                                StandardCharsets.UTF_8
                        )
                        .build()
        );

        headers.setContentLength(
                excel.length
        );

        /*
         * Do not let browser/proxy caching cause an old or corrupted
         * report to be reused.
         */
        headers.setCacheControl(
                CacheControl
                        .noCache()
                        .noStore()
                        .mustRevalidate()
        );

        headers.add(
                HttpHeaders.PRAGMA,
                "no-cache"
        );

        headers.add(
                HttpHeaders.EXPIRES,
                "0"
        );

        return ResponseEntity
                .ok()
                .headers(headers)
                .body(excel);
    }

    // ============================================================
    // FILENAME SECURITY
    // ============================================================

    private String sanitizeFilename(
            String filename) {

        if (filename == null
                || filename.isBlank()) {

            return "report";
        }

        /*
         * Prevent accidental path/header manipulation.
         */
        return filename
                .replace(
                        "\\",
                        "-"
                )
                .replace(
                        "/",
                        "-"
                )
                .replace(
                        "\"",
                        "-"
                )
                .replace(
                        "\r",
                        "-"
                )
                .replace(
                        "\n",
                        "-"
                )
                .trim();
    }
}