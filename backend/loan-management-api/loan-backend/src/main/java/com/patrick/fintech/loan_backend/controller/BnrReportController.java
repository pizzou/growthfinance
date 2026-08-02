package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.BnrFinancialStatementReport;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

            @RequestParam(
                    required = false
            )
            Long branchId,

            @RequestParam(
                    required = false,
                    defaultValue = "MONTHLY"
            )
            ReportPeriod period,

            @RequestParam(
                    required = false
            )
            String from,

            @RequestParam(
                    required = false
            )
            String to

    ) {

        Long organizationId =
                currentUserUtil
                        .getCurrentOrganizationId();


        BnrSummaryReport report =
                reportingService
                        .buildBnrSummary(
                                organizationId,
                                branchId,
                                period,
                                parseDate(from),
                                parseDate(to)
                        );


        auditService.log(

                currentUserUtil
                        .getCurrentUser()
                        .getOrganization(),

                currentUserUtil
                        .getCurrentUser(),

                "VIEW",

                "BnrReport",

                period.name(),

                "Viewed BNR portfolio summary (" +
                        period.name() +
                        ")",

                null,
                null,

                "Regulatory Reporting"
        );


        return ResponseEntity.ok(
                ApiResponse.ok(
                        report
                )
        );
    }


    

    @GetMapping("/financial-statement")
    public ResponseEntity<
            ApiResponse<BnrFinancialStatementReport>
            >
    financialStatement(

            @RequestParam(
                    required = false
            )
            Long branchId,

            @RequestParam(
                    required = false,
                    defaultValue = "MONTHLY"
            )
            ReportPeriod period,

            @RequestParam(
                    required = false
            )
            String from,

            @RequestParam(
                    required = false
            )
            String to

    ) {

        Long organizationId =
                currentUserUtil
                        .getCurrentOrganizationId();


        BnrFinancialStatementReport report =
                reportingService
                        .buildBnrFinancialStatement(
                                organizationId,
                                branchId,
                                period,
                                parseDate(from),
                                parseDate(to)
                        );


        auditService.log(

                currentUserUtil
                        .getCurrentUser()
                        .getOrganization(),

                currentUserUtil
                        .getCurrentUser(),

                "VIEW",

                "BnrFinancialStatement",

                period.name(),

                "Viewed BNR accounting financial statement (" +
                        period.name() +
                        ")",

                null,
                null,

                "Regulatory Reporting"
        );


        return ResponseEntity.ok(
                ApiResponse.ok(
                        report
                )
        );
    }


   

    @GetMapping("/breakdown/by-loan-type")
    public ResponseEntity<
            ApiResponse<
                    List<com.patrick.fintech.loan_backend.dto.regulatory.BnrBreakdownRow>
                    >
            >
    byLoanType(

            @RequestParam(
                    required = false
            )
            Long branchId,

            @RequestParam(
                    required = false,
                    defaultValue = "MONTHLY"
            )
            ReportPeriod period,

            @RequestParam(
                    required = false
            )
            String from,

            @RequestParam(
                    required = false
            )
            String to

    ) {

        Long organizationId =
                currentUserUtil
                        .getCurrentOrganizationId();


        return ResponseEntity.ok(

                ApiResponse.ok(

                        reportingService
                                .breakdownByLoanType(
                                        organizationId,
                                        branchId,
                                        period,
                                        parseDate(from),
                                        parseDate(to)
                                )
                )
        );
    }


    // ============================================================
    // BRANCH BREAKDOWN
    // ============================================================

    @GetMapping("/breakdown/by-branch")
    public ResponseEntity<
            ApiResponse<
                    List<com.patrick.fintech.loan_backend.dto.regulatory.BnrBreakdownRow>
                    >
            >
    byBranch(

            @RequestParam(
                    required = false,
                    defaultValue = "MONTHLY"
            )
            ReportPeriod period,

            @RequestParam(
                    required = false
            )
            String from,

            @RequestParam(
                    required = false
            )
            String to

    ) {

        Long organizationId =
                currentUserUtil
                        .getCurrentOrganizationId();


        return ResponseEntity.ok(

                ApiResponse.ok(

                        reportingService
                                .breakdownByBranch(
                                        organizationId,
                                        period,
                                        parseDate(from),
                                        parseDate(to)
                                )
                )
        );
    }


    // ============================================================
    // GENDER BREAKDOWN
    // ============================================================

    @GetMapping("/breakdown/by-gender")
    public ResponseEntity<
            ApiResponse<
                    List<com.patrick.fintech.loan_backend.dto.regulatory.BnrBreakdownRow>
                    >
            >
    byGender(

            @RequestParam(
                    required = false
            )
            Long branchId,

            @RequestParam(
                    required = false,
                    defaultValue = "MONTHLY"
            )
            ReportPeriod period,

            @RequestParam(
                    required = false
            )
            String from,

            @RequestParam(
                    required = false
            )
            String to

    ) {

        Long organizationId =
                currentUserUtil
                        .getCurrentOrganizationId();


        return ResponseEntity.ok(

                ApiResponse.ok(

                        reportingService
                                .breakdownByGender(
                                        organizationId,
                                        branchId,
                                        period,
                                        parseDate(from),
                                        parseDate(to)
                                )
                )
        );
    }


    // ============================================================
    // BNR FINANCIAL STATEMENT EXPORT
    // ============================================================

    @GetMapping("/financial-statement/export")
    public ResponseEntity<byte[]> exportFinancialStatement(

            @RequestParam(
                    defaultValue = "xlsx"
            )
            String format,

            @RequestParam(
                    required = false
            )
            Long branchId,

            @RequestParam(
                    required = false,
                    defaultValue = "MONTHLY"
            )
            ReportPeriod period,

            @RequestParam(
                    required = false
            )
            String from,

            @RequestParam(
                    required = false
            )
            String to

    ) {

        Long organizationId =
                currentUserUtil
                        .getCurrentOrganizationId();


        BnrFinancialStatementReport report =
                reportingService
                        .buildBnrFinancialStatement(
                                organizationId,
                                branchId,
                                period,
                                parseDate(from),
                                parseDate(to)
                        );


        List<String> columns =
                List.of(
                        "Section",
                        "Account",
                        "Value"
                );


        List<Map<String, Object>> rows =
                financialStatementRows(
                        report
                );


        String organizationName =
                currentUserUtil
                        .getCurrentUser()
                        .getOrganization()
                        .getName();


        String filename =
                "BNR-Financial-Statement-" +
                        LocalDate.now()
                                .format(
                                        DateTimeFormatter.ISO_DATE
                                );


        auditService.log(

                currentUserUtil
                        .getCurrentUser()
                        .getOrganization(),

                currentUserUtil
                        .getCurrentUser(),

                "EXPORT",

                "BnrFinancialStatement",

                period.name(),

                "Exported BNR financial statement as " +
                        format.toUpperCase(),

                null,
                null,

                "Regulatory Reporting"
        );


        return respond(

                format,

                filename,

                "BNR Financial Statement",

                columns,

                rows,

                organizationName
        );
    }


    // ============================================================
    // FINANCIAL STATEMENT ROWS
    // ============================================================

    private List<Map<String, Object>>
    financialStatementRows(
            BnrFinancialStatementReport report
    ) {

        List<Map<String, Object>> rows =
                new ArrayList<>();


        // ========================================================
        // BALANCE SHEET
        // ========================================================

        addSectionRows(
                rows,
                "ASSETS",
                report.getAssets()
        );


        addSectionRows(
                rows,
                "LIABILITIES",
                report.getLiabilities()
        );


        addSectionRows(
                rows,
                "EQUITY",
                report.getEquity()
        );


        addRow(
                rows,
                "BALANCE SHEET",
                "Total Assets",
                report.getTotalAssets()
        );


        addRow(
                rows,
                "BALANCE SHEET",
                "Total Liabilities",
                report.getTotalLiabilities()
        );


        addRow(
                rows,
                "BALANCE SHEET",
                "Total Equity",
                report.getTotalEquity()
        );


        addRow(
                rows,
                "BALANCE SHEET",
                "Current Period Net Income",
                report.getCurrentPeriodNetIncome()
        );


        addRow(
                rows,
                "BALANCE SHEET",
                "Balance Sheet Balanced",
                report.isBalanceSheetBalanced()
        );


        // ========================================================
        // INCOME STATEMENT
        // ========================================================

        addSectionRows(
                rows,
                "INCOME",
                report.getIncome()
        );


        addSectionRows(
                rows,
                "EXPENSES",
                report.getExpenses()
        );


        addRow(
                rows,
                "PROFIT AND LOSS",
                "Total Income",
                report.getTotalIncome()
        );


        addRow(
                rows,
                "PROFIT AND LOSS",
                "Total Expenses",
                report.getTotalExpenses()
        );


        addRow(
                rows,
                "PROFIT AND LOSS",
                "Net Income",
                report.getNetIncome()
        );


        // ========================================================
        // TRIAL BALANCE
        // ========================================================

        addRow(
                rows,
                "TRIAL BALANCE",
                "Total Debit",
                report.getTrialBalanceDebit()
        );


        addRow(
                rows,
                "TRIAL BALANCE",
                "Total Credit",
                report.getTrialBalanceCredit()
        );


        addRow(
                rows,
                "TRIAL BALANCE",
                "Balanced",
                report.isTrialBalanceBalanced()
        );


        // ========================================================
        // CASH FLOW
        // ========================================================

        addRow(
                rows,
                "CASH FLOW",
                "Cash Used For Lending",
                report.getCashUsedForLending()
        );


        addRow(
                rows,
                "CASH FLOW",
                "Cash From Collections",
                report.getCashFromCollections()
        );


        addRow(
                rows,
                "CASH FLOW",
                "Cash From Fees",
                report.getCashFromFees()
        );


        addRow(
                rows,
                "CASH FLOW",
                "Other Cash Movement",
                report.getOtherCashMovement()
        );


        addRow(
                rows,
                "CASH FLOW",
                "Net Change In Cash",
                report.getNetChangeInCash()
        );


        return rows;
    }


    // ============================================================
    // ADD SECTION ROWS
    // ============================================================

    private void addSectionRows(

            List<Map<String, Object>> rows,

            String section,

            List<Map<String, Object>> sectionRows

    ) {

        if (
                sectionRows == null
                ||
                sectionRows.isEmpty()
        ) {

            return;
        }


        for (
                Map<String, Object> item :
                sectionRows
        ) {

            if (item == null) {
                continue;
            }


            String account =
                    item.get("code") +
                            " - " +
                            item.get("name");


            Object value =
                    item.get("balance");


            addRow(
                    rows,
                    section,
                    account,
                    value
            );
        }
    }


    // ============================================================
    // ADD ROW
    // ============================================================

    private void addRow(

            List<Map<String, Object>> rows,

            String section,

            String account,

            Object value

    ) {

        Map<String, Object> row =
                new LinkedHashMap<>();


        row.put(
                "Section",
                section
        );


        row.put(
                "Account",
                account
        );


        row.put(
                "Value",
                value
        );


        rows.add(
                row
        );
    }


    // ============================================================
    // EXPORT RESPONSE
    // ============================================================

    private ResponseEntity<byte[]> respond(

            String format,

            String filename,

            String title,

            List<String> columns,

            List<Map<String, Object>> rows,

            String organizationName

    ) {

        byte[] bytes;

        MediaType contentType;

        String extension;


        if (
                format == null
                ||
                format.isBlank()
        ) {

            format =
                    "xlsx";
        }


        switch (
                format.toLowerCase()
        ) {

            case "csv" -> {

                bytes =
                        toCsv(
                                columns,
                                rows
                        );


                contentType =
                        MediaType.parseMediaType(
                                "text/csv"
                        );


                extension =
                        "csv";
            }


            case "pdf" -> {

                bytes =
                        exportService.toPdf(
                                title,
                                columns,
                                rows,
                                organizationName
                        );


                contentType =
                        MediaType.APPLICATION_PDF;


                extension =
                        "pdf";
            }


            case "xlsx" -> {

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
            }


            default ->

                    throw new IllegalArgumentException(
                            "Unsupported export format: " +
                                    format +
                                    ". Supported formats: csv, pdf, xlsx."
                    );
        }


        return ResponseEntity.ok()

                .contentType(
                        contentType
                )

                .header(
                        HttpHeaders.CONTENT_DISPOSITION,

                        "attachment; filename=\"" +
                                filename +
                                "." +
                                extension +
                                "\""
                )

                .body(
                        bytes
                );
    }


    // ============================================================
    // CSV
    // ============================================================

    static byte[] toCsv(

            List<String> columns,

            List<Map<String, Object>> rows

    ) {

        StringBuilder sb =
                new StringBuilder();


        sb.append(
                String.join(
                        ",",
                        columns
                )
        );


        sb.append(
                "\n"
        );


        for (
                Map<String, Object> row :
                rows
        ) {

            for (
                    int i = 0;
                    i < columns.size();
                    i++
            ) {

                Object value =
                        row.get(
                                columns.get(i)
                        );


                String cell =
                        value == null
                                ? ""
                                : value.toString();


                cell =
                        cell.replace(
                                "\"",
                                "\"\""
                        );


                if (
                        cell.contains(",")
                        ||
                        cell.contains("\"")
                        ||
                        cell.contains("\n")
                ) {

                    cell =
                            "\"" +
                                    cell +
                                    "\"";
                }


                sb.append(
                        cell
                );


                if (
                        i <
                                columns.size() - 1
                ) {

                    sb.append(
                            ","
                    );
                }
            }


            sb.append(
                    "\n"
            );
        }


        return sb.toString()
                .getBytes(
                        java.nio.charset.StandardCharsets.UTF_8
                );
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


        return LocalDate.parse(
                value
        );
    }
}