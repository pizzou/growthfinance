
package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.CreditBureauRecord;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.RegulatoryReportingService;
import com.patrick.fintech.loan_backend.service.ReportExportService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Staff-facing Credit Bureau reporting controller.
 *
 * IMPORTANT:
 *
 * This controller is for ADMIN/MANAGER users inside the
 * administration dashboard.
 *
 * It is NOT the external Credit Bureau API.
 *
 * External Credit Bureau API:
 *
 * /api/regulatory/external/credit-bureau/**
 *
 * Staff/Admin Credit Bureau:
 *
 * /api/regulatory/credit-bureau/**
 *
 *
 * AUTHORITY MODEL:
 *
 * This application uses authorities such as:
 *
 * ADMIN
 * MANAGER
 *
 * NOT:
 *
 * ROLE_ADMIN
 * ROLE_MANAGER
 *
 * Therefore this controller uses:
 *
 * @PreAuthorize("hasAnyAuthority('ADMIN','MANAGER')")
 *
 * instead of:
 *
 * @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
 */
@RestController
@RequestMapping("/api/regulatory/credit-bureau")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN','MANAGER')")
public class CreditBureauExportController {


    // ============================================================
    // SERVICES
    // ============================================================

    private final RegulatoryReportingService reportingService;

    private final ReportExportService exportService;

    private final AuditService auditService;

    private final CurrentUserUtil currentUserUtil;


    // ============================================================
    // REPORT COLUMNS
    // ============================================================

    /**
     * Columns used consistently by:
     *
     * - Preview
     * - Excel
     * - CSV
     * - PDF
     */
    private static final List<String> COLUMNS = List.of(

            "Borrower ID",

            "National ID",

            "Full Name",

            "Date of Birth",

            "Gender",

            "Phone",

            "Loan Number",

            "Loan Type",

            "Loan Status",

            "Repayment Classification",

            "Loan Amount",

            "Outstanding Balance",

            "Days Past Due",

            "Credit Score",

            "Date Opened",

            "Last Payment",

            "Maturity Date",

            "Date Closed",

            "Branch",

            "Currency"
    );


    // ============================================================
    // PREVIEW
    // ============================================================

    /**
     * Preview Credit Bureau records.
     *
     * GET:
     *
     * /api/regulatory/credit-bureau/preview
     *
     * Optional:
     *
     * ?branchId=5
     *
     * ?from=2026-01-01
     *
     * ?to=2026-06-30
     *
     * Example:
     *
     * /api/regulatory/credit-bureau/preview
     *     ?from=2026-01-01
     *     &to=2026-06-30
     */
    @GetMapping("/preview")
    public ResponseEntity<ApiResponse<List<CreditBureauRecord>>> preview(

            @RequestParam(
                    required = false
            )
            Long branchId,

            @RequestParam(
                    required = false
            )
            String from,

            @RequestParam(
                    required = false
            )
            String to
    ) {


        // ========================================================
        // CURRENT ORGANIZATION
        // ========================================================

        Long organizationId =
                currentUserUtil
                        .getCurrentOrganizationId();


        if (organizationId == null) {

            throw new IllegalStateException(
                    "Current user is not associated with an organization."
            );
        }


        // ========================================================
        // DATE FILTERS
        // ========================================================

        LocalDate fromDate =
                parseDate(from);

        LocalDate toDate =
                parseDate(to);


        validateDateRange(
                fromDate,
                toDate
        );


        // ========================================================
        // BUILD CREDIT BUREAU REPORT
        // ========================================================

        List<CreditBureauRecord> records =
                reportingService.buildCreditBureauExport(

                        organizationId,

                        branchId,

                        fromDate,

                        toDate
                );


        // ========================================================
        // AUDIT
        // ========================================================

        auditService.log(

                currentUserUtil
                        .getCurrentUser()
                        .getOrganization(),

                currentUserUtil
                        .getCurrentUser(),

                "VIEW",

                "CreditBureauExport",

                "preview",

                "Previewed Credit Bureau report"
                        + " | Records: "
                        + records.size()
                        + " | Branch: "
                        + (
                                branchId == null
                                        ? "ALL"
                                        : branchId
                        )
                        + " | From: "
                        + (
                                fromDate == null
                                        ? "ALL"
                                        : fromDate
                        )
                        + " | To: "
                        + (
                                toDate == null
                                        ? "ALL"
                                        : toDate
                        ),

                null,

                null,

                "Regulatory Reporting"
        );


        // ========================================================
        // RESPONSE
        // ========================================================

        return ResponseEntity.ok(

                ApiResponse.ok(
                        records
                )
        );
    }


    // ============================================================
    // EXPORT
    // ============================================================

    /**
     * Export Credit Bureau report.
     *
     * Supported formats:
     *
     * xlsx
     * csv
     * pdf
     *
     * Examples:
     *
     * /api/regulatory/credit-bureau/export?format=xlsx
     *
     * /api/regulatory/credit-bureau/export?format=csv
     *
     * /api/regulatory/credit-bureau/export?format=pdf
     *
     * With date filters:
     *
     * /api/regulatory/credit-bureau/export
     *     ?format=xlsx
     *     &from=2026-01-01
     *     &to=2026-06-30
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(

            @RequestParam(
                    defaultValue = "xlsx"
            )
            String format,

            @RequestParam(
                    required = false
            )
            Long branchId,

            @RequestParam(
                    required = false
            )
            String from,

            @RequestParam(
                    required = false
            )
            String to
    ) {


        // ========================================================
        // CURRENT ORGANIZATION
        // ========================================================

        Long organizationId =
                currentUserUtil
                        .getCurrentOrganizationId();


        if (organizationId == null) {

            throw new IllegalStateException(
                    "Current user is not associated with an organization."
            );
        }


        // ========================================================
        // DATE FILTERS
        // ========================================================

        LocalDate fromDate =
                parseDate(from);

        LocalDate toDate =
                parseDate(to);


        validateDateRange(
                fromDate,
                toDate
        );


        // ========================================================
        // FORMAT
        // ========================================================

        String normalizedFormat =
                format == null
                        ? "xlsx"
                        : format
                                .trim()
                                .toLowerCase();


        validateExportFormat(
                normalizedFormat
        );


        // ========================================================
        // BUILD REPORT
        // ========================================================

        List<CreditBureauRecord> records =
                reportingService.buildCreditBureauExport(

                        organizationId,

                        branchId,

                        fromDate,

                        toDate
                );


        // ========================================================
        // ORGANIZATION NAME
        // ========================================================

        String organizationName =
                currentUserUtil
                        .getCurrentUser()
                        .getOrganization()
                        .getName();


        if (
                organizationName == null
                        ||
                organizationName.isBlank()
        ) {

            organizationName =
                    "Loan Management System";
        }


        // ========================================================
        // CONVERT RECORDS TO EXPORT ROWS
        // ========================================================

        List<Map<String, Object>> rows =
                records.stream()
                        .map(this::toRow)
                        .toList();


        // ========================================================
        // FILE NAME
        // ========================================================

        String filenameBase =
                buildFilename(
                        fromDate,
                        toDate
                );


        // ========================================================
        // AUDIT
        // ========================================================

        auditService.log(

                currentUserUtil
                        .getCurrentUser()
                        .getOrganization(),

                currentUserUtil
                        .getCurrentUser(),

                "EXPORT",

                "CreditBureauExport",

                "export",

                "Exported Credit Bureau report"
                        + " | Format: "
                        + normalizedFormat.toUpperCase()
                        + " | Records: "
                        + records.size()
                        + " | Branch: "
                        + (
                                branchId == null
                                        ? "ALL"
                                        : branchId
                        )
                        + " | From: "
                        + (
                                fromDate == null
                                        ? "ALL"
                                        : fromDate
                        )
                        + " | To: "
                        + (
                                toDate == null
                                        ? "ALL"
                                        : toDate
                        ),

                null,

                null,

                "Regulatory Reporting"
        );


        // ========================================================
        // GENERATE FILE
        // ========================================================

        byte[] bytes;

        MediaType contentType;

        String extension;


        switch (normalizedFormat) {


            // ====================================================
            // CSV
            // ====================================================

            case "csv" -> {

                bytes =
                        BnrReportController.toCsv(

                                COLUMNS,

                                rows
                        );


                contentType =
                        MediaType.parseMediaType(
                                "text/csv;charset=UTF-8"
                        );


                extension =
                        "csv";
            }


            // ====================================================
            // PDF
            // ====================================================

            case "pdf" -> {

                bytes =
                        exportService.toPdf(

                                "Credit Bureau / CRB Report",

                                COLUMNS,

                                rows,

                                organizationName
                        );


                contentType =
                        MediaType.APPLICATION_PDF;


                extension =
                        "pdf";
            }


            // ====================================================
            // EXCEL
            // ====================================================

            case "xlsx" -> {

                bytes =
                        exportService.toExcel(

                                "Credit Bureau / CRB Report",

                                COLUMNS,

                                rows
                        );


                contentType =
                        MediaType.parseMediaType(

                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

                        );


                extension =
                        "xlsx";
            }


            // ====================================================
            // SAFETY
            // ====================================================

            default -> throw new IllegalArgumentException(

                    "Unsupported export format: "
                            + normalizedFormat
                            + ". Supported formats: xlsx, csv, pdf."
            );
        }


        // ========================================================
        // FILE RESPONSE
        // ========================================================

        return ResponseEntity.ok()

                .contentType(
                        contentType
                )

                .contentLength(
                        bytes.length
                )

                .cacheControl(
                        CacheControl.noCache()
                )

                .header(

                        HttpHeaders.CONTENT_DISPOSITION,

                        "attachment; filename=\""
                                + filenameBase
                                + "."
                                + extension
                                + "\""
                )

                .body(
                        bytes
                );
    }


    // ============================================================
    // CONVERT DTO TO EXPORT ROW
    // ============================================================

    private Map<String, Object> toRow(

            CreditBureauRecord record

    ) {

        Map<String, Object> row =
                new LinkedHashMap<>();


        row.put(
                "Borrower ID",
                record.getBorrowerId()
        );


        row.put(
                "National ID",
                record.getNationalId()
        );


        row.put(
                "Full Name",
                record.getFullName()
        );


        row.put(
                "Date of Birth",
                record.getDateOfBirth()
        );


        row.put(
                "Gender",
                record.getGender()
        );


        row.put(
                "Phone",
                record.getPhone()
        );


        row.put(
                "Loan Number",
                record.getLoanNumber()
        );


        row.put(
                "Loan Type",
                record.getLoanType()
        );


        row.put(
                "Loan Status",
                record.getLoanStatus()
        );


        row.put(
                "Repayment Classification",
                record.getRepaymentClassification()
        );


        row.put(
                "Loan Amount",
                record.getLoanAmount()
        );


        row.put(
                "Outstanding Balance",
                record.getOutstandingBalance()
        );


        row.put(
                "Days Past Due",
                record.getDaysPastDue()
        );


        row.put(
                "Credit Score",
                record.getCreditScore()
        );


        row.put(
                "Date Opened",
                record.getDateOpened()
        );


        row.put(
                "Last Payment",
                record.getLastPaymentDate()
        );


        row.put(
                "Maturity Date",
                record.getMaturityDate()
        );


        row.put(
                "Date Closed",
                record.getDateClosed()
        );


        row.put(
                "Branch",
                record.getBranchName()
        );


        row.put(
                "Currency",
                record.getCurrency()
        );


        return row;
    }


    // ============================================================
    // DATE PARSER
    // ============================================================

    private LocalDate parseDate(

            String value

    ) {

        if (
                value == null
                        ||
                value.isBlank()
        ) {

            return null;
        }


        try {

            return LocalDate.parse(

                    value.trim(),

                    DateTimeFormatter.ISO_LOCAL_DATE

            );

        } catch (Exception exception) {

            throw new IllegalArgumentException(

                    "Invalid date: "
                            + value
                            + ". Expected format: yyyy-MM-dd."

            );
        }
    }


    // ============================================================
    // DATE RANGE VALIDATION
    // ============================================================

    private void validateDateRange(

            LocalDate from,

            LocalDate to

    ) {

        if (
                from != null
                        &&
                to != null
                        &&
                from.isAfter(to)
        ) {

            throw new IllegalArgumentException(

                    "'from' date cannot be after 'to' date."

            );
        }
    }


    // ============================================================
    // EXPORT FORMAT VALIDATION
    // ============================================================

    private void validateExportFormat(

            String format

    ) {

        if (
                !"xlsx".equals(format)
                        &&
                !"csv".equals(format)
                        &&
                !"pdf".equals(format)
        ) {

            throw new IllegalArgumentException(

                    "Unsupported export format: "
                            + format
                            + ". Supported formats: xlsx, csv, pdf."

            );
        }
    }


    // ============================================================
    // FILE NAME
    // ============================================================

    private String buildFilename(

            LocalDate from,

            LocalDate to

    ) {

        StringBuilder filename =

                new StringBuilder(
                        "Credit-Bureau-Report"
                );


        if (from != null) {

            filename.append(
                    "-from-"
            );

            filename.append(
                    from
            );
        }


        if (to != null) {

            filename.append(
                    "-to-"
            );

            filename.append(
                    to
            );
        }


        filename.append(
                "-"
        );


        filename.append(

                LocalDate.now()
                        .format(
                                DateTimeFormatter.ISO_DATE
                        )

        );


        return filename.toString();
    }
}
