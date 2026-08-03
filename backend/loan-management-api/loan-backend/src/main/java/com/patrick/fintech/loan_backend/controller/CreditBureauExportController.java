package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.CreditBureauRecord;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.RegulatoryReportingService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/regulatory/credit-bureau")
@RequiredArgsConstructor
@PreAuthorize(
        "hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ADMIN','MANAGER')"
)
public class CreditBureauExportController {

    private final RegulatoryReportingService reportingService;

    private final ReportExportService exportService;

    private final AuditService auditService;

    private final CurrentUserUtil currentUserUtil;


    // ============================================================
    // EXPORT COLUMNS
    // ============================================================

    private static final List<String> COLUMNS = List.of(
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
            "Branch",
            "Currency"
    );


    // ============================================================
    // PREVIEW
    // ============================================================

    @GetMapping("/preview")
    public ResponseEntity<ApiResponse<List<CreditBureauRecord>>> preview(
            @RequestParam(required = false)
            Long branchId,

            @RequestParam(required = false)
            String from,

            @RequestParam(required = false)
            String to
    ) {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();


        List<CreditBureauRecord> records =
                reportingService.buildCreditBureauExport(
                        organizationId,
                        branchId,
                        parseDate(from),
                        parseDate(to)
                );


        auditService.log(
                currentUserUtil.getCurrentUser().getOrganization(),
                currentUserUtil.getCurrentUser(),
                "VIEW",
                "CreditBureauExport",
                "preview",
                "Previewed credit bureau export (" +
                        records.size() +
                        " records)",
                null,
                null,
                "Regulatory Reporting"
        );


        return ResponseEntity.ok(
                ApiResponse.ok(records)
        );
    }


    // ============================================================
    // EXPORT
    // ============================================================

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(
                    defaultValue = "xlsx"
            )
            String format,

            @RequestParam(required = false)
            Long branchId,

            @RequestParam(required = false)
            String from,

            @RequestParam(required = false)
            String to
    ) {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();


        List<CreditBureauRecord> records =
                reportingService.buildCreditBureauExport(
                        organizationId,
                        branchId,
                        parseDate(from),
                        parseDate(to)
                );


        String organizationName =
                currentUserUtil
                        .getCurrentUser()
                        .getOrganization()
                        .getName();


        List<Map<String, Object>> rows =
                records.stream()
                        .map(this::toRow)
                        .toList();


        String normalizedFormat =
                format == null ||
                        format.isBlank()
                        ? "xlsx"
                        : format
                                .trim()
                                .toLowerCase();


        String filename =
                "Credit-Bureau-Export-" +
                        LocalDate.now()
                                .format(
                                        DateTimeFormatter.ISO_DATE
                                );


        auditService.log(
                currentUserUtil.getCurrentUser().getOrganization(),
                currentUserUtil.getCurrentUser(),
                "EXPORT",
                "CreditBureauExport",
                "export",
                "Exported credit bureau data as " +
                        normalizedFormat.toUpperCase() +
                        " (" +
                        records.size() +
                        " borrower records)",
                null,
                null,
                "Regulatory Reporting"
        );


        return createFileResponse(
                normalizedFormat,
                filename,
                "Credit Bureau Export",
                COLUMNS,
                rows,
                organizationName
        );
    }


    // ============================================================
    // ROW CONVERSION
    // ============================================================

    private Map<String, Object> toRow(
            CreditBureauRecord record
    ) {

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

        row.put(
                "Currency",
                record.getCurrency()
        );


        return row;
    }


    // ============================================================
    // FILE RESPONSE
    // ============================================================

    private ResponseEntity<byte[]> createFileResponse(
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


        switch (format) {

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


            default -> {

                throw new IllegalArgumentException(
                        "Unsupported export format: " +
                                format +
                                ". Supported formats: csv, pdf, xlsx."
                );
            }
        }


        return ResponseEntity.ok()
                .contentType(contentType)
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
    // CSV
    // ============================================================

    private byte[] toCsv(
            List<String> columns,
            List<Map<String, Object>> rows
    ) {

        StringBuilder csv =
                new StringBuilder();


        // Header
        for (
                int i = 0;
                i < columns.size();
                i++
        ) {

            csv.append(
                    escapeCsv(
                            columns.get(i)
                    )
            );


            if (
                    i <
                            columns.size() - 1
            ) {

                csv.append(",");
            }
        }


        csv.append("\n");


        // Rows
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
                                : String.valueOf(value);


                csv.append(
                        escapeCsv(cell)
                );


                if (
                        i <
                                columns.size() - 1
                ) {

                    csv.append(",");
                }
            }


            csv.append("\n");
        }


        // UTF-8 BOM for Excel
        byte[] bom =
                new byte[]{
                        (byte) 0xEF,
                        (byte) 0xBB,
                        (byte) 0xBF
                };


        byte[] content =
                csv.toString()
                        .getBytes(
                                StandardCharsets.UTF_8
                        );


        byte[] result =
                new byte[
                        bom.length +
                                content.length
                ];


        System.arraycopy(
                bom,
                0,
                result,
                0,
                bom.length
        );


        System.arraycopy(
                content,
                0,
                result,
                bom.length,
                content.length
        );


        return result;
    }


    // ============================================================
    // CSV ESCAPING
    // ============================================================

    private String escapeCsv(
            String value
    ) {

        if (value == null) {
            return "";
        }


        String escaped =
                value.replace(
                        "\"",
                        "\"\""
                );


        if (
                escaped.contains(",") ||
                escaped.contains("\"") ||
                escaped.contains("\n") ||
                escaped.contains("\r")
        ) {

            return "\"" +
                    escaped +
                    "\"";
        }


        return escaped;
    }


    // ============================================================
    // DATE
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
}