package com.patrick.fintech.loan_backend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.BnrBreakdownRow;
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

import java.nio.charset.StandardCharsets;

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

   
    private final ObjectMapper objectMapper;


   
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<BnrSummaryReport>> summary(

            @RequestParam(required = false)
            Long branchId,

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

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();


        BnrSummaryReport report =
                reportingService.buildBnrSummary(

                        organizationId,

                        branchId,

                        period,

                        parseDate(from),

                        parseDate(to)
                );


        auditView(

                "BnrReport",

                period,

                "Viewed BNR portfolio summary"
        );


        return ResponseEntity.ok(
                ApiResponse.ok(report)
        );
    }


    
    @GetMapping("/financial-statement")
    public ResponseEntity<ApiResponse<BnrFinancialStatementReport>>
    financialStatement(

            @RequestParam(required = false)
            Long branchId,

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

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();


        BnrFinancialStatementReport report =
                reportingService.buildBnrFinancialStatement(

                        organizationId,

                        branchId,

                        period,

                        parseDate(from),

                        parseDate(to)
                );


        auditView(

                "BnrFinancialStatement",

                period,

                "Viewed BNR financial statement"
        );


        return ResponseEntity.ok(
                ApiResponse.ok(report)
        );
    }

    @GetMapping("/breakdown/loan-type")
    public ResponseEntity<ApiResponse<List<BnrBreakdownRow>>>
    byLoanType(

            @RequestParam(required = false)
            Long branchId,

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

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();


        List<BnrBreakdownRow> result =
                reportingService.breakdownByLoanType(

                        organizationId,

                        branchId,

                        period,

                        parseDate(from),

                        parseDate(to)
                );


        return ResponseEntity.ok(
                ApiResponse.ok(result)
        );
    }


    @GetMapping("/breakdown/branch")
    public ResponseEntity<ApiResponse<List<BnrBreakdownRow>>>
    byBranch(

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

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();


        List<BnrBreakdownRow> result =
                reportingService.breakdownByBranch(

                        organizationId,

                        period,

                        parseDate(from),

                        parseDate(to)
                );


        return ResponseEntity.ok(
                ApiResponse.ok(result)
        );
    }


    @GetMapping("/breakdown/gender")
    public ResponseEntity<ApiResponse<List<BnrBreakdownRow>>>
    byGender(

            @RequestParam(required = false)
            Long branchId,

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

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();


        List<BnrBreakdownRow> result =
                reportingService.breakdownByGender(

                        organizationId,

                        branchId,

                        period,

                        parseDate(from),

                        parseDate(to)
                );


        return ResponseEntity.ok(
                ApiResponse.ok(result)
        );
    }


    @GetMapping("/export")
    public ResponseEntity<byte[]> exportBnrSummary(

            @RequestParam(
                    defaultValue = "xlsx"
            )
            String format,

            @RequestParam(required = false)
            Long branchId,

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

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();


       

        BnrSummaryReport report =
                reportingService.buildBnrSummary(

                        organizationId,

                        branchId,

                        period,

                        parseDate(from),

                        parseDate(to)
                );


       

        List<Map<String, Object>> rows =
                summaryRows(report);


       

        List<String> columns =
                List.of(
                        "Metric",
                        "Value"
                );


        String organizationName =
                currentUserUtil
                        .getCurrentUser()
                        .getOrganization()
                        .getName();


        String filename =
                "BNR-Summary-" +
                LocalDate.now().format(
                        DateTimeFormatter.ISO_DATE
                );


       

        auditExport(

                "BnrReport",

                period,

                format
        );


       

        return respond(

                format,

                filename,

                "BNR Regulatory Summary",

                columns,

                rows,

                organizationName
        );
    }


    @GetMapping("/financial-statement/export")
    public ResponseEntity<byte[]> exportFinancialStatement(

            @RequestParam(
                    defaultValue = "xlsx"
            )
            String format,

            @RequestParam(required = false)
            Long branchId,

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

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();


        BnrFinancialStatementReport report =
                reportingService.buildBnrFinancialStatement(

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
                financialStatementRows(report);


        String organizationName =
                currentUserUtil
                        .getCurrentUser()
                        .getOrganization()
                        .getName();


        String filename =
                "BNR-Financial-Statement-" +
                LocalDate.now().format(
                        DateTimeFormatter.ISO_DATE
                );


        auditExport(

                "BnrFinancialStatement",

                period,

                format
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


    private List<Map<String, Object>> summaryRows(
            BnrSummaryReport report
    ) {

        List<Map<String, Object>> rows =
                new ArrayList<>();


        if (report == null) {
            return rows;
        }


        Map<String, Object> values =
                objectMapper.convertValue(

                        report,

                        new TypeReference<
                                LinkedHashMap<String, Object>
                                >() {}
                );


        for (
                Map.Entry<String, Object> entry
                        : values.entrySet()
        ) {

            String metric =
                    prettifyMetricName(
                            entry.getKey()
                    );


            Object value =
                    entry.getValue();


      

            if (
                    value instanceof Map
                            ||
                    value instanceof List
            ) {

                continue;
            }


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
        }


        return rows;
    }


    // ============================================================
    // FINANCIAL STATEMENT ROWS
    // ============================================================

    private List<Map<String, Object>> financialStatementRows(
            BnrFinancialStatementReport report
    ) {

        List<Map<String, Object>> rows =
                new ArrayList<>();


        if (report == null) {
            return rows;
        }


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
                "Balanced",
                report.isBalanceSheetBalanced()
        );


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


    private void addSectionRows(

            List<Map<String, Object>> rows,

            String section,

            List<Map<String, Object>> sectionRows

    ) {

        if (sectionRows == null) {
            return;
        }


        for (
                Map<String, Object> item
                        : sectionRows
        ) {

            if (item == null) {
                continue;
            }


            String code =
                    String.valueOf(
                            item.getOrDefault(
                                    "code",
                                    ""
                            )
                    );


            String name =
                    String.valueOf(
                            item.getOrDefault(
                                    "name",
                                    ""
                            )
                    );


            Object value =
                    item.get("balance");


            String account;


            if (code.isBlank()) {

                account = name;

            } else {

                account =
                        code +
                        " - " +
                        name;
            }


            addRow(

                    rows,

                    section,

                    account,

                    value
            );
        }
    }



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


        rows.add(row);
    }


    // ============================================================
    // FILE RESPONSE
    // ============================================================

    private ResponseEntity<byte[]> respond(

            String format,

            String filename,

            String title,

            List<String> columns,

            List<Map<String, Object>> rows,

            String organizationName

    ) {

        String normalized =

                format == null ||
                format.isBlank()

                        ? "xlsx"

                        : format
                                .trim()
                                .toLowerCase();


        byte[] bytes;

        MediaType contentType;

        String extension;


        switch (normalized) {


            // ----------------------------------------------------
            // CSV
            // ----------------------------------------------------

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


                extension = "csv";
            }


            // ----------------------------------------------------
            // PDF
            // ----------------------------------------------------

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


                extension = "pdf";
            }


            // ----------------------------------------------------
            // XLSX
            // ----------------------------------------------------

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


                extension = "xlsx";
            }


            // ----------------------------------------------------
            // INVALID
            // ----------------------------------------------------

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

                .body(bytes);
    }


    // ============================================================
    // CSV GENERATOR
    // ============================================================

    static byte[] toCsv(

            List<String> columns,

            List<Map<String, Object>> rows

    ) {

        StringBuilder sb =
                new StringBuilder();


        // --------------------------------------------------------
        // Header
        // --------------------------------------------------------

        sb.append(
                String.join(
                        ",",
                        columns
                )
        );


        sb.append('\n');


        // --------------------------------------------------------
        // Rows
        // --------------------------------------------------------

        for (
                Map<String, Object> row
                        : rows
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


                // ------------------------------------------------
                // CSV formula injection protection
                // ------------------------------------------------

                cell =
                        cell.replace(
                                "\"",
                                "\"\""
                        );


                if (

                        !cell.isEmpty()

                                &&

                        "=+-@\t".indexOf(
                                cell.charAt(0)
                        ) >= 0

                ) {

                    cell =
                            "'" +
                            cell;
                }


                // ------------------------------------------------
                // CSV quoting
                // ------------------------------------------------

                if (

                        cell.contains(",")

                                ||

                        cell.contains("\"")

                                ||

                        cell.contains("\n")

                                ||

                        cell.contains("\r")

                ) {

                    cell =
                            "\"" +
                            cell +
                            "\"";
                }


                sb.append(cell);


                if (
                        i <
                        columns.size() - 1
                ) {

                    sb.append(',');
                }
            }


            sb.append('\n');
        }


        return sb
                .toString()
                .getBytes(
                        StandardCharsets.UTF_8
                );
    }


    // ============================================================
    // AUDIT VIEW
    // ============================================================

    private void auditView(

            String entity,

            ReportPeriod period,

            String description

    ) {

        auditService.log(

                currentUserUtil
                        .getCurrentUser()
                        .getOrganization(),

                currentUserUtil
                        .getCurrentUser(),

                "VIEW",

                entity,

                period.name(),

                description +
                        " (" +
                        period.name() +
                        ")",

                null,

                null,

                "Regulatory Reporting"
        );
    }


    // ============================================================
    // AUDIT EXPORT
    // ============================================================

    private void auditExport(

            String entity,

            ReportPeriod period,

            String format

    ) {

        auditService.log(

                currentUserUtil
                        .getCurrentUser()
                        .getOrganization(),

                currentUserUtil
                        .getCurrentUser(),

                "EXPORT",

                entity,

                period.name(),

                "Exported " +
                        entity +
                        " as " +
                        format.toUpperCase(),

                null,

                null,

                "Regulatory Reporting"
        );
    }


    // ============================================================
    // DATE PARSER
    // ============================================================

    private LocalDate parseDate(
            String value
    ) {

        if (
                value == null ||
                value.isBlank()
        ) {

            return null;
        }


        return LocalDate.parse(
                value
        );
    }


    // ============================================================
    // PRETTY METRIC NAME
    // ============================================================

    /**
     * Converts:
     *
     * totalLoans
     *
     * into:
     *
     * Total Loans
     */
    private String prettifyMetricName(
            String value
    ) {

        if (
                value == null ||
                value.isBlank()
        ) {

            return "";
        }


        String result =
                value.replaceAll(
                        "([a-z])([A-Z])",
                        "$1 $2"
                );


        result =
                result.replace(
                        "_",
                        " "
                );


        return Character.toUpperCase(
                result.charAt(0)
        )
                + result.substring(1);
    }
}
