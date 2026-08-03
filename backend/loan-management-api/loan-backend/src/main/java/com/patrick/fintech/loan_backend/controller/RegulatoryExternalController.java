
package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.CreditBureauRecord;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.security.RegulatoryApiPrincipal;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.RegulatoryReportingService;
import com.patrick.fintech.loan_backend.service.ReportExportService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
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
    // CREDIT BUREAU EXPORT
    // ============================================================

    @GetMapping(
            value = "/credit-bureau/export",
            produces = {
                    MediaType.APPLICATION_JSON_VALUE,
                    "text/csv",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    MediaType.APPLICATION_PDF_VALUE
            }
    )
    @PreAuthorize(
            "hasAuthority('ROLE_CREDIT_BUREAU_API')"
    )
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
                parseDate(
                        from
                );


        LocalDate toDate =
                parseDate(
                        to
                );


        String requestedFormat =
                format == null
                        ? "json"
                        : format.trim()
                        .toLowerCase();


        List<CreditBureauRecord> records =
                reportingService.buildCreditBureauExport(

                        organizationId,

                        null,

                        fromDate,

                        toDate
                );


        audit(
                "EXPORT",
                "Exported "
                        + records.size()
                        + " borrower credit records as "
                        + requestedFormat.toUpperCase()
        );


        // ========================================================
        // JSON
        // ========================================================

        if (
                "json".equalsIgnoreCase(
                        requestedFormat
                )
        ) {

            return ResponseEntity.ok(
                    ApiResponse.ok(
                            records
                    )
            );
        }


        // ========================================================
        // ORGANIZATION
        // ========================================================

        String organizationName =
                organizationRepository
                        .findById(
                                organizationId
                        )
                        .map(
                                organization ->
                                        organization.getName()
                        )
                        .orElse(
                                "Organization"
                        );


        // ========================================================
        // COLUMNS
        // ========================================================

        List<String> columns =
                List.of(

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


        // ========================================================
        // ROWS
        // ========================================================

        List<Map<String, Object>> rows =
                records.stream()
                        .map(
                                record -> {

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
                        )
                        .toList();


        // ========================================================
        // FILE RESPONSE
        // ========================================================

        return fileResponse(

                requestedFormat,

                "credit-bureau-export",

                "Credit Bureau / CRB Report",

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

        String normalizedFormat =
                format == null
                        ? "xlsx"
                        : format.trim()
                        .toLowerCase();


        byte[] bytes;

        MediaType contentType;

        String extension;


        switch (normalizedFormat) {

            case "csv" -> {

                bytes =
                        BnrReportController.toCsv(
                                columns,
                                rows
                        );


                contentType =
                        MediaType.parseMediaType(
                                "text/csv;charset=UTF-8"
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


            default -> throw new IllegalArgumentException(

                    "Unsupported export format: "
                            + normalizedFormat
                            + ". Supported formats: csv, pdf, xlsx."
            );
        }


        String filename =
                filenameBase
                        + "."
                        + extension;


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
                                + filename
                                + "\""
                )

                .body(
                        bytes
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
                value.trim()
        );
    }
}
