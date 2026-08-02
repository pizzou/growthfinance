
package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.BnrBreakdownRow;
import com.patrick.fintech.loan_backend.dto.regulatory.BnrSummaryReport;
import com.patrick.fintech.loan_backend.dto.regulatory.CreditBureauRecord;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.security.RegulatoryApiPrincipal;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.RegulatoryReportingService;
import com.patrick.fintech.loan_backend.service.RegulatoryReportingService.ReportPeriod;
import com.patrick.fintech.loan_backend.service.ReportExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * External regulatory reporting API.
 *
 * Supports:
 *
 * BNR:
 * - Summary
 * - Loan type breakdown
 * - Branch breakdown
 * - Gender breakdown
 * - PAR aging breakdown
 * - Export
 *
 * Credit Bureau:
 * - Borrower-level export
 *
 * All requests are scoped to the organization associated
 * with the regulatory API key.
 */
@RestController
@RequestMapping("/api/regulatory/external")
@RequiredArgsConstructor
public class RegulatoryExternalController {


    private final RegulatoryReportingService reportingService;

    private final ReportExportService exportService;

    private final AuditService auditService;

    private final OrganizationRepository organizationRepository;


    // ============================================================
    // REGULATORY API PRINCIPAL
    // ============================================================

    private RegulatoryApiPrincipal principal() {

        return (RegulatoryApiPrincipal)
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();
    }


    // ============================================================
    // AUDIT
    // ============================================================

    private void audit(
            String action,
            String description
    ) {

        RegulatoryApiPrincipal p =
                principal();


        auditService.log(

                organizationRepository
                        .findById(
                                p.getOrganizationId()
                        )
                        .orElse(null),

                null,

                action,

                "RegulatoryApiAccess",

                p.getClientName(),

                "["
                        + p.getClientType()
                        + " API: "
                        + p.getClientName()
                        + "] "
                        + description,

                null,

                null,

                "Regulatory Reporting"
        );
    }


    // ============================================================
    // BNR SUMMARY
    // ============================================================

    @GetMapping("/bnr/summary")
    @PreAuthorize("hasAuthority('ROLE_BNR_API')")
    public ResponseEntity<ApiResponse<BnrSummaryReport>> bnrSummary(

            @RequestParam(
                    required = false,
                    defaultValue = "MONTHLY"
            )
            ReportPeriod period,

            @RequestParam(required = false)
            String from,

            @RequestParam(required = false)
            String to

    ) {

        Long orgId =
                principal()
                        .getOrganizationId();


        BnrSummaryReport report =
                reportingService.buildBnrSummary(

                        orgId,

                        null,

                        period,

                        parseDate(from),

                        parseDate(to)
                );


        audit(
                "VIEW",
                "Fetched BNR portfolio summary ("
                        + period
                        + ")"
        );


        return ResponseEntity.ok(
                ApiResponse.ok(report)
        );
    }


    // ============================================================
    // BNR - LOAN TYPE
    // ============================================================

    @GetMapping("/bnr/breakdown/loan-type")
    @PreAuthorize("hasAuthority('ROLE_BNR_API')")
    public ResponseEntity<ApiResponse<List<BnrBreakdownRow>>>
    bnrByLoanType(

            @RequestParam(
                    required = false,
                    defaultValue = "MONTHLY"
            )
            ReportPeriod period,

            @RequestParam(required = false)
            String from,

            @RequestParam(required = false)
            String to

    ) {

        Long orgId =
                principal()
                        .getOrganizationId();


        List<BnrBreakdownRow> rows =
                reportingService.breakdownByLoanType(

                        orgId,

                        null,

                        period,

                        parseDate(from),

                        parseDate(to)
                );


        audit(
                "VIEW",
                "Fetched BNR loan-type breakdown ("
                        + period
                        + ")"
        );


        return ResponseEntity.ok(
                ApiResponse.ok(rows)
        );
    }


    // ============================================================
    // BNR - BRANCH
    // ============================================================

    @GetMapping("/bnr/breakdown/branch")
    @PreAuthorize("hasAuthority('ROLE_BNR_API')")
    public ResponseEntity<ApiResponse<List<BnrBreakdownRow>>>
    bnrByBranch(

            @RequestParam(
                    required = false,
                    defaultValue = "MONTHLY"
            )
            ReportPeriod period,

            @RequestParam(required = false)
            String from,

            @RequestParam(required = false)
            String to

    ) {

        Long orgId =
                principal()
                        .getOrganizationId();


        List<BnrBreakdownRow> rows =
                reportingService.breakdownByBranch(

                        orgId,

                        period,

                        parseDate(from),

                        parseDate(to)
                );


        audit(
                "VIEW",
                "Fetched BNR branch breakdown ("
                        + period
                        + ")"
        );


        return ResponseEntity.ok(
                ApiResponse.ok(rows)
        );
    }


    // ============================================================
    // BNR - GENDER
    // ============================================================

    @GetMapping("/bnr/breakdown/gender")
    @PreAuthorize("hasAuthority('ROLE_BNR_API')")
    public ResponseEntity<ApiResponse<List<BnrBreakdownRow>>>
    bnrByGender(

            @RequestParam(
                    required = false,
                    defaultValue = "MONTHLY"
            )
            ReportPeriod period,

            @RequestParam(required = false)
            String from,

            @RequestParam(required = false)
            String to

    ) {

        Long orgId =
                principal()
                        .getOrganizationId();


        List<BnrBreakdownRow> rows =
                reportingService.breakdownByGender(

                        orgId,

                        null,

                        period,

                        parseDate(from),

                        parseDate(to)
                );


        audit(
                "VIEW",
                "Fetched BNR gender breakdown ("
                        + period
                        + ")"
        );


        return ResponseEntity.ok(
                ApiResponse.ok(rows)
        );
    }


    // ============================================================
    // BNR - PAR AGING
    // ============================================================

    @GetMapping("/bnr/breakdown/par")
    @PreAuthorize("hasAuthority('ROLE_BNR_API')")
    public ResponseEntity<ApiResponse<List<BnrBreakdownRow>>>
    bnrByParBucket(

            @RequestParam(
                    required = false,
                    defaultValue = "MONTHLY"
            )
            ReportPeriod period,

            @RequestParam(required = false)
            String from,

            @RequestParam(required = false)
            String to

    ) {

        Long orgId =
                principal()
                        .getOrganizationId();


        List<BnrBreakdownRow> rows =
                reportingService.breakdownByParBucket(

                        orgId,

                        null,

                        period,

                        parseDate(from),

                        parseDate(to)
                );


        audit(
                "VIEW",
                "Fetched BNR PAR aging breakdown ("
                        + period
                        + ")"
        );


        return ResponseEntity.ok(
                ApiResponse.ok(rows)
        );
    }


    // ============================================================
    // BNR EXPORT
    // ============================================================

    @GetMapping(
            value = "/bnr/export",
            produces = {
                    "application/json",
                    "text/csv",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    MediaType.APPLICATION_PDF_VALUE
            }
    )
    @PreAuthorize("hasAuthority('ROLE_BNR_API')")
    public ResponseEntity<?> bnrExport(

            @RequestParam(
                    defaultValue = "json"
            )
            String format,

            @RequestParam(
                    required = false,
                    defaultValue = "MONTHLY"
            )
            ReportPeriod period,

            @RequestParam(required = false)
            String from,

            @RequestParam(required = false)
            String to

    ) {

        Long orgId =
                principal()
                        .getOrganizationId();


        BnrSummaryReport summary =
                reportingService.buildBnrSummary(

                        orgId,

                        null,

                        period,

                        parseDate(from),

                        parseDate(to)
                );


        audit(
                "EXPORT",
                "Exported BNR portfolio summary as "
                        + format.toUpperCase()
                        + " ("
                        + period
                        + ")"
        );


        // --------------------------------------------------------
        // JSON
        // --------------------------------------------------------

        if (
                "json".equalsIgnoreCase(format)
        ) {

            return ResponseEntity.ok(
                    ApiResponse.ok(summary)
            );
        }


        String orgName =
                organizationRepository
                        .findById(orgId)
                        .map(o -> o.getName())
                        .orElse("");


        List<String> columns =
                List.of(
                        "Metric",
                        "Value"
                );


        List<Map<String, Object>> rows =
                flattenSummary(summary);


        return fileResponse(
                format,
                "bnr-summary",
                "BNR Loan Portfolio Summary",
                columns,
                rows,
                orgName
        );
    }


    // ============================================================
    // CREDIT BUREAU EXPORT
    // ============================================================

    @GetMapping(
            value = "/credit-bureau/export",
            produces = {
                    "application/json",
                    "text/csv",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    MediaType.APPLICATION_PDF_VALUE
            }
    )
    @PreAuthorize("hasAuthority('ROLE_CREDIT_BUREAU_API')")
    public ResponseEntity<?> creditBureauExport(

            @RequestParam(
                    defaultValue = "json"
            )
            String format,

            @RequestParam(required = false)
            String from,

            @RequestParam(required = false)
            String to

    ) {

        Long orgId =
                principal()
                        .getOrganizationId();


        List<CreditBureauRecord> records =
                reportingService.buildCreditBureauExport(

                        orgId,

                        null,

                        parseDate(from),

                        parseDate(to)
                );


        audit(
                "EXPORT",
                "Exported "
                        + records.size()
                        + " borrower credit records as "
                        + format.toUpperCase()
        );


        // --------------------------------------------------------
        // JSON
        // --------------------------------------------------------

        if (
                "json".equalsIgnoreCase(format)
        ) {

            return ResponseEntity.ok(
                    ApiResponse.ok(records)
            );
        }


        String orgName =
                organizationRepository
                        .findById(orgId)
                        .map(o -> o.getName())
                        .orElse("");


        List<String> columns =
                List.of(
                        "National ID",
                        "Full Name",
                        "Date of Birth",
                        "Gender",
                        "Phone",
                        "Loan Number",
                        "Loan Type",
                        "Loan Amount",
                        "Outstanding Balance",
                        "Status",
                        "Days Past Due",
                        "Credit Score",
                        "Date Opened",
                        "Last Payment",
                        "Date Closed",
                        "Branch"
                );


        List<Map<String, Object>> rows =
                records.stream()
                        .map(record -> {

                            Map<String, Object> row =
                                    new LinkedHashMap<>();


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
                                    "Loan Amount",
                                    record.getLoanAmount()
                            );

                            row.put(
                                    "Outstanding Balance",
                                    record.getOutstandingBalance()
                            );

                            row.put(
                                    "Status",
                                    record.getLoanStatus()
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
                                    "Date Closed",
                                    record.getDateClosed()
                            );

                            row.put(
                                    "Branch",
                                    record.getBranchName()
                            );


                            return row;
                        })
                        .toList();


        return fileResponse(
                format,
                "credit-bureau-export",
                "Credit Bureau Export",
                columns,
                rows,
                orgName
        );
    }


    // ============================================================
    // FILE RESPONSE
    // ============================================================

    private ResponseEntity<byte[]> fileResponse(

            String format,

            String filenameBase,

            String title,

            List<String> columns,

            List<Map<String, Object>> rows,

            String orgName

    ) {

        byte[] bytes;

        MediaType contentType;

        String extension;


        switch (
                format.toLowerCase()
        ) {

            case "csv":

                bytes =
                        BnrReportController.toCsv(
                                columns,
                                rows
                        );

                contentType =
                        MediaType.parseMediaType(
                                "text/csv"
                        );

                extension =
                        "csv";

                break;


            case "pdf":

                bytes =
                        exportService.toPdf(
                                title,
                                columns,
                                rows,
                                orgName
                        );

                contentType =
                        MediaType.APPLICATION_PDF;

                extension =
                        "pdf";

                break;


            case "xlsx":
            case "excel":

                bytes =
                        exportService.toExcel(
                                title,
                                columns,
                                rows
                        );

                contentType =
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        );

                extension =
                        "xlsx";

                break;


            default:

                throw new IllegalArgumentException(
                        "Unsupported export format: "
                                + format
                                + ". Supported formats: json, csv, xlsx, pdf"
                );
        }


        return ResponseEntity.ok()

                .contentType(contentType)

                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""
                                + filenameBase
                                + "."
                                + extension
                                + "\""
                )

                .body(bytes);
    }


    // ============================================================
    // FLATTEN BNR SUMMARY
    // ============================================================

    private List<Map<String, Object>> flattenSummary(
            BnrSummaryReport summary
    ) {

        List<Map<String, Object>> rows =
                new ArrayList<>();


        java.util.function.BiConsumer<String, Object> add =
                (metric, value) -> {

                    Map<String, Object> row =
                            new LinkedHashMap<>();

                    row.put(
                            "Metric",
                            metric
                    );

                    row.put(
                            "Value",
                            value
                    );

                    rows.add(row);
                };


        add.accept(
                "Organization",
                summary.getOrganizationName()
        );

        add.accept(
                "BNR Institution Code",
                summary.getBnrInstitutionCode()
        );

        add.accept(
                "Report Period",
                summary.getReportPeriod()
        );

        add.accept(
                "Period Start",
                summary.getPeriodStart()
        );

        add.accept(
                "Period End",
                summary.getPeriodEnd()
        );

        add.accept(
                "Total Loans Issued",
                summary.getTotalLoansIssued()
        );

        add.accept(
                "Active Loans",
                summary.getActiveLoans()
        );

        add.accept(
                "Closed Loans",
                summary.getClosedLoans()
        );

        add.accept(
                "Pending Loans",
                summary.getPendingLoans()
        );

        add.accept(
                "Rejected Loans",
                summary.getRejectedLoans()
        );

        add.accept(
                "Overdue Loans",
                summary.getOverdueLoans()
        );

        add.accept(
                "Defaulted Loans",
                summary.getDefaultedLoans()
        );

        add.accept(
                "Total Principal Disbursed",
                summary.getTotalPrincipalDisbursed()
        );

        add.accept(
                "Outstanding Principal",
                summary.getOutstandingPrincipal()
        );

        add.accept(
                "Total Interest Collected",
                summary.getTotalInterestCollected()
        );

        add.accept(
                "Interest Accrued Unpaid",
                summary.getInterestAccruedUnpaid()
        );

        add.accept(
                "Total Processing Fees",
                summary.getTotalProcessingFees()
        );

        add.accept(
                "Male Borrowers",
                summary.getMaleBorrowers()
        );

        add.accept(
                "Female Borrowers",
                summary.getFemaleBorrowers()
        );

        add.accept(
                "Other Gender Borrowers",
                summary.getOtherGenderBorrowers()
        );

        add.accept(
                "PAR Amount",
                summary.getParAmount()
        );

        add.accept(
                "PAR Ratio",
                summary.getParRatio()
        );

        add.accept(
                "NPL Amount",
                summary.getNplAmount()
        );

        add.accept(
                "NPL Ratio",
                summary.getNplRatio()
        );

        add.accept(
                "Currency",
                summary.getCurrency()
        );

        add.accept(
                "Generated At",
                summary.getGeneratedAt()
        );


        return rows;
    }


    // ============================================================
    // DATE PARSER
    // ============================================================

    private LocalDate parseDate(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {
            return null;
        }


        try {

            return LocalDate.parse(
                    value.trim()
            );

        } catch (Exception ex) {

            throw new IllegalArgumentException(
                    "Invalid date: "
                            + value
                            + ". Expected format: yyyy-MM-dd"
            );
        }
    }
}
