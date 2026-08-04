
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



@RestController
@RequestMapping("/api/regulatory/credit-bureau")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
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
           @GetMapping(value = "/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {

        // 1. Validate Organization
        Long organizationId = currentUserUtil.getCurrentOrganizationId();
        if (organizationId == null) {
            throw new IllegalStateException("Current user is not associated with an organization.");
        }

        // 2. Parse and Validate Dates
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);
        validateDateRange(fromDate, toDate);

        // 3. Fetch the Credit Bureau Records
        List<CreditBureauRecord> records = reportingService.buildCreditBureauExport(
                organizationId, branchId, fromDate, toDate
        );

        // 4. Generate File Bytes (Self-contained CSV stream with primitive structural fixes)
        byte[] fileBytes;
        try {
            StringBuilder csvContent = new StringBuilder();
            
            // Append Headers
            csvContent.append(String.join(",", COLUMNS)).append("\n");
            
            // Append Data Rows Safely
            if (records != null) {
                for (CreditBureauRecord record : records) {
                    // Changed format to use %f for loanAmount and outstandingBalance, and %d for integers
                    csvContent.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%.2f,%.2f,%d,%d,%s,%s,%s,%s,%s,%s\n",
                        record.getBorrowerId() != null ? record.getBorrowerId() : "",
                        record.getNationalId() != null ? record.getNationalId() : "",
                        record.getFullName() != null ? record.getFullName().replace(",", " ") : "",
                        record.getDateOfBirth() != null ? record.getDateOfBirth() : "",
                        record.getGender() != null ? record.getGender() : "",
                        record.getPhone() != null ? record.getPhone() : "",
                        record.getLoanNumber() != null ? record.getLoanNumber() : "",
                        record.getLoanType() != null ? record.getLoanType() : "",
                        record.getLoanStatus() != null ? record.getLoanStatus() : "",
                        record.getRepaymentClassification() != null ? record.getRepaymentClassification() : "",
                        record.getLoanAmount(),         // Primitive double: cannot be null, prints directly as float
                        record.getOutstandingBalance(), // Primitive double: cannot be null, prints directly as float
                        record.getDaysPastDue(),        // Primitive int: cannot be null, prints directly
                        record.getCreditScore(),        // Primitive int: cannot be null, prints directly
                        record.getDateOpened() != null ? record.getDateOpened() : "",
                        record.getLastPaymentDate() != null ? record.getLastPaymentDate() : "",
                        record.getMaturityDate() != null ? record.getMaturityDate() : "",
                        record.getDateClosed() != null ? record.getDateClosed() : "",
                        record.getBranchName() != null ? record.getBranchName() : "",
                        record.getCurrency() != null ? record.getCurrency() : ""
                    ));
                }
            }
            fileBytes = csvContent.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate export data stream", e);
        }

        // 5. Setup Safe Download Headers
        HttpHeaders headers = new HttpHeaders();
        String fileName = "credit_bureau_report_" + LocalDate.now();

        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", fileName + ".csv");
        headers.setCacheControl("no-cache, no-store, must-revalidate");

        // 6. Log the Audit Trail
        auditService.log(
                currentUserUtil.getCurrentUser().getOrganization(),
                currentUserUtil.getCurrentUser(),
                "EXPORT", 
                "CreditBureauExport",
                "export",
                "Exported Credit Bureau report as CSV (Self-contained stream)",
                null, null, "Regulatory Reporting"
        );

        // 7. Return the file bytes directly
        return ResponseEntity.ok()
                .headers(headers)
                .body(fileBytes);
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
