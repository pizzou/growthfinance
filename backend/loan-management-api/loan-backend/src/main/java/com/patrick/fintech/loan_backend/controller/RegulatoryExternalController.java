package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.CreditBureauRecord;
import com.patrick.fintech.loan_backend.security.RegulatoryApiPrincipal;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.RegulatoryReportingService;
import com.patrick.fintech.loan_backend.service.ReportExportService;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/regulatory")
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

                "[" +
                        p.getClientType() +
                        " API: " +
                        p.getClientName() +
                        "] " +
                        description,

                null,
                null,

                "Regulatory Reporting"
        );
    }

    // ============================================================
    // CREDIT BUREAU EXPORT
    //
    // IMPORTANT:
    // BNR endpoints are intentionally NOT here.
    //
    // BNR frontend endpoints are handled by:
    // BnrReportController
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
                principal()
                        .getOrganizationId();

        LocalDate fromDate =
                parseDate(from);

        LocalDate toDate =
                parseDate(to);

        List<CreditBureauRecord> records =
                reportingService.buildCreditBureauExport(
                        organizationId,
                        null,
                        fromDate,
                        toDate
                );

        audit(
                "EXPORT",
                "Exported " +
                        records.size() +
                        " borrower credit records as " +
                        format.toUpperCase()
        );

        if ("json".equalsIgnoreCase(format)) {

            return ResponseEntity.ok(
                    ApiResponse.ok(records)
            );
        }

        String organizationName =
                organizationRepository
                        .findById(organizationId)
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
                organizationName
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

            String organizationName
    ) {

        if (format == null || format.isBlank()) {
            format = "xlsx";
        }

        byte[] bytes;

        MediaType contentType;

        String extension;

        switch (format.toLowerCase()) {

            case "csv" -> {

                bytes =
                        BnrReportController.toCsv(
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

            default -> throw new IllegalArgumentException(
                    "Unsupported export format: " +
                            format +
                            ". Supported formats: csv, pdf, xlsx."
            );
        }

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" +
                                filenameBase +
                                "." +
                                extension +
                                "\""
                )
                .body(bytes);
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

        return LocalDate.parse(value);
    }
}
