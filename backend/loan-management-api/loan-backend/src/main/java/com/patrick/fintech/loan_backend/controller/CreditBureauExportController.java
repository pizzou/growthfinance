
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

/**
 * Staff-facing Credit Bureau export screen.
 *
 * Provides:
 * - Preview
 * - CSV export
 * - PDF export
 * - Excel export
 *
 * Access:
 * ADMIN and MANAGER only.
 *
 * Organization scope is obtained from the authenticated user.
 */
@RestController
@RequestMapping("/api/regulatory/credit-bureau")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class CreditBureauExportController {

    private final RegulatoryReportingService reportingService;
    private final ReportExportService exportService;
    private final AuditService auditService;
    private final CurrentUserUtil currentUserUtil;

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

        Long orgId =
                currentUserUtil.getCurrentOrganizationId();

        List<CreditBureauRecord> records =
                reportingService.buildCreditBureauExport(
                        orgId,
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

        Long orgId =
                currentUserUtil.getCurrentOrganizationId();

        List<CreditBureauRecord> records =
                reportingService.buildCreditBureauExport(
                        orgId,
                        branchId,
                        parseDate(from),
                        parseDate(to)
                );

        String orgName =
                currentUserUtil
                        .getCurrentUser()
                        .getOrganization()
                        .getName();

        List<Map<String, Object>> rows =
                records.stream()
                        .map(this::toRow)
                        .toList();

        String filename =
                "Credit-Bureau-Export-" +
                        LocalDate.now()
                                .format(DateTimeFormatter.ISO_DATE);

        auditService.log(
                currentUserUtil.getCurrentUser().getOrganization(),
                currentUserUtil.getCurrentUser(),
                "EXPORT",
                "CreditBureauExport",
                "export",
                "Exported credit bureau data as " +
                        format.toUpperCase() +
                        " (" +
                        records.size() +
                        " borrower records)",
                null,
                null,
                "Regulatory Reporting"
        );

        return createFileResponse(
                format,
                filename,
                "Credit Bureau Export",
                COLUMNS,
                rows,
                orgName
        );
    }

    // ============================================================
    // ROW CONVERSION
    // ============================================================

    private Map<String, Object> toRow(
            CreditBureauRecord r
    ) {

        Map<String, Object> row =
                new LinkedHashMap<>();

        row.put(
                "National ID",
                r.getNationalId()
        );

        row.put(
                "Full Name",
                r.getFullName()
        );

        row.put(
                "Date of Birth",
                r.getDateOfBirth()
        );

        row.put(
                "Gender",
                r.getGender()
        );

        row.put(
                "Phone",
                r.getPhone()
        );

        row.put(
                "Loan Number",
                r.getLoanNumber()
        );

        row.put(
                "Loan Type",
                r.getLoanType()
        );

        row.put(
                "Loan Amount",
                r.getLoanAmount()
        );

        row.put(
                "Outstanding Balance",
                r.getOutstandingBalance()
        );

        row.put(
                "Status",
                r.getLoanStatus()
        );

        row.put(
                "Days Past Due",
                r.getDaysPastDue()
        );

        row.put(
                "Credit Score",
                r.getCreditScore()
        );

        row.put(
                "Date Opened",
                r.getDateOpened()
        );

        row.put(
                "Last Payment",
                r.getLastPaymentDate()
        );

        row.put(
                "Date Closed",
                r.getDateClosed()
        );

        row.put(
                "Branch",
                r.getBranchName()
        );

        row.put(
                "Currency",
                r.getCurrency()
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

        if (format == null ||
                format.isBlank()) {

            format = "xlsx";
        }

        byte[] bytes;

        MediaType contentType;

        String extension;

        switch (format.toLowerCase()) {

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

        csv.append(
                String.join(",", columns)
        );

        csv.append("\n");

        for (Map<String, Object> row : rows) {

            for (int i = 0;
                 i < columns.size();
                 i++) {

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

                if (cell.contains(",") ||
                        cell.contains("\"") ||
                        cell.contains("\n")) {

                    cell =
                            "\"" +
                                    cell +
                                    "\"";
                }

                csv.append(cell);

                if (i < columns.size() - 1) {
                    csv.append(",");
                }
            }

            csv.append("\n");
        }

        return csv.toString()
                .getBytes(StandardCharsets.UTF_8);
    }

    // ============================================================
    // DATE
    // ============================================================

    private LocalDate parseDate(
            String value
    ) {

        if (value == null ||
                value.isBlank()) {

            return null;
        }

        return LocalDate.parse(value);
    }
}
