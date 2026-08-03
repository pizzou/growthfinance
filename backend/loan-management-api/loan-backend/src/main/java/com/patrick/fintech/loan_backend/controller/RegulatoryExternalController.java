
package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.CreditBureauRecord;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.security.RegulatoryApiPrincipal;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.RegulatoryReportingService;
import com.patrick.fintech.loan_backend.service.ReportExportService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

    private final CurrentUserUtil currentUserUtil;


    

    @GetMapping(
            value = "/credit-bureau/preview",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<List<CreditBureauRecord>>>
    creditBureauPreview() {

        User currentUser =
                currentUserUtil.getCurrentUser();

        // --------------------------------------------------------
        // AUTHENTICATION
        // --------------------------------------------------------

        if (currentUser == null) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(
                            ApiResponse.error(
                                    "Authentication required."
                            )
                    );
        }

        // --------------------------------------------------------
        // ORGANIZATION
        // --------------------------------------------------------

        if (currentUser.getOrganization() == null) {

            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(
                            ApiResponse.error(
                                    "User is not associated with an organization."
                            )
                    );
        }

        Long organizationId =
                currentUser
                        .getOrganization()
                        .getId();

        // --------------------------------------------------------
        // BUILD CREDIT BUREAU DATA
        // --------------------------------------------------------

        List<CreditBureauRecord> records =
                reportingService.buildCreditBureauExport(
                        organizationId,
                        null,
                        null,
                        null
                );

        // --------------------------------------------------------
        // AUDIT
        // --------------------------------------------------------

        auditService.log(

                currentUser.getOrganization(),

                currentUser,

                "VIEW",

                "CreditBureauReport",

                String.valueOf(
                        currentUser.getId()
                ),

                "Admin viewed Credit Bureau records.",

                null,

                null,

                "Credit Bureau"
        );

        // --------------------------------------------------------
        // RESPONSE
        // --------------------------------------------------------

        return ResponseEntity.ok(
                ApiResponse.ok(records)
        );
    }


    // ============================================================
    // ============================================================
    // ADMIN / MANAGER CREDIT BUREAU EXPORT
    //
    // Frontend URL:
    //
    // GET
    // /api/regulatory/credit-bureau/export?format=pdf
    //
    // Authentication:
    // Normal logged-in application user.
    //
    // This is the endpoint your Credit Bureau frontend button uses.
    // ============================================================
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
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<?> creditBureauAdminExport(

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

        User currentUser =
                currentUserUtil.getCurrentUser();

        // --------------------------------------------------------
        // AUTHENTICATION
        // --------------------------------------------------------

        if (currentUser == null) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(
                            ApiResponse.error(
                                    "Authentication required."
                            )
                    );
        }

        // --------------------------------------------------------
        // ORGANIZATION
        // --------------------------------------------------------

        if (currentUser.getOrganization() == null) {

            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(
                            ApiResponse.error(
                                    "User is not associated with an organization."
                            )
                    );
        }

        Long organizationId =
                currentUser
                        .getOrganization()
                        .getId();

        // --------------------------------------------------------
        // DATES
        // --------------------------------------------------------

        LocalDate fromDate =
                parseDate(from);

        LocalDate toDate =
                parseDate(to);

        // --------------------------------------------------------
        // BUILD CREDIT BUREAU REPORT
        // --------------------------------------------------------

        List<CreditBureauRecord> records =
                reportingService.buildCreditBureauExport(
                        organizationId,
                        null,
                        fromDate,
                        toDate
                );

        // --------------------------------------------------------
        // AUDIT
        // --------------------------------------------------------

        auditService.log(

                currentUser.getOrganization(),

                currentUser,

                "EXPORT",

                "CreditBureauReport",

                String.valueOf(
                        currentUser.getId()
                ),

                "Admin exported " +
                        records.size() +
                        " Credit Bureau records as " +
                        format.toUpperCase(),

                null,

                null,

                "Credit Bureau"
        );

        // --------------------------------------------------------
        // JSON
        // --------------------------------------------------------

        if ("json".equalsIgnoreCase(format)) {

            return ResponseEntity.ok(
                    ApiResponse.ok(records)
            );
        }

        // --------------------------------------------------------
        // ORGANIZATION NAME
        // --------------------------------------------------------

        String organizationName =
                currentUser
                        .getOrganization()
                        .getName();

        // --------------------------------------------------------
        // COLUMNS
        // --------------------------------------------------------

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
                        "Branch",
                        "Currency"
                );

        // --------------------------------------------------------
        // ROWS
        // --------------------------------------------------------

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

                            row.put(
                                    "Currency",
                                    record.getCurrency()
                            );

                            return row;
                        })
                        .toList();

        // --------------------------------------------------------
        // GENERATE FILE
        // --------------------------------------------------------

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
    // ============================================================
    // EXTERNAL CREDIT BUREAU API
    //
    // IMPORTANT:
    //
    // This is kept separate from the admin frontend endpoint.
    //
    // External URL:
    //
    // /api/regulatory/external/credit-bureau/export
    //
    // Authentication:
    // ROLE_CREDIT_BUREAU_API
    //
    // Your RegulatoryApiPrincipal/API-key mechanism can continue
    // using this endpoint.
    // ============================================================
    // ============================================================

    @GetMapping(
            value = "/external/credit-bureau/export",
            produces = {
                    MediaType.APPLICATION_JSON_VALUE,
                    "text/csv",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    MediaType.APPLICATION_PDF_VALUE
            }
    )
    @PreAuthorize("hasAuthority('ROLE_CREDIT_BUREAU_API')")
    public ResponseEntity<?> externalCreditBureauExport(

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

        RegulatoryApiPrincipal regulatoryPrincipal =
                principal();

        Long organizationId =
                regulatoryPrincipal
                        .getOrganizationId();

        LocalDate fromDate =
                parseDate(from);

        LocalDate toDate =
                parseDate(to);

        // --------------------------------------------------------
        // BUILD REPORT
        // --------------------------------------------------------

        List<CreditBureauRecord> records =
                reportingService.buildCreditBureauExport(
                        organizationId,
                        null,
                        fromDate,
                        toDate
                );

        // --------------------------------------------------------
        // AUDIT
        // --------------------------------------------------------

        auditExternal(
                "EXPORT",
                "Exported " +
                        records.size() +
                        " borrower credit records as " +
                        format.toUpperCase()
        );

        // --------------------------------------------------------
        // JSON
        // --------------------------------------------------------

        if ("json".equalsIgnoreCase(format)) {

            return ResponseEntity.ok(
                    ApiResponse.ok(records)
            );
        }

        // --------------------------------------------------------
        // ORGANIZATION NAME
        // --------------------------------------------------------

        String organizationName =
                organizationRepository
                        .findById(organizationId)
                        .map(o -> o.getName())
                        .orElse("");

        // --------------------------------------------------------
        // COLUMNS
        // --------------------------------------------------------

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
                        "Branch",
                        "Currency"
                );

        // --------------------------------------------------------
        // ROWS
        // --------------------------------------------------------

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

                            row.put(
                                    "Currency",
                                    record.getCurrency()
                            );

                            return row;
                        })
                        .toList();

        // --------------------------------------------------------
        // GENERATE FILE
        // --------------------------------------------------------

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
    // ============================================================
    // REGULATORY API PRINCIPAL
    // ============================================================
    // ============================================================

    private RegulatoryApiPrincipal principal() {

        if (
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                == null
        ) {

            throw new IllegalStateException(
                    "No authenticated regulatory API principal."
            );
        }

        Object authenticatedPrincipal =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        if (
                !(authenticatedPrincipal
                        instanceof RegulatoryApiPrincipal)
        ) {

            throw new IllegalStateException(
                    "Authenticated principal is not a RegulatoryApiPrincipal."
            );
        }

        return (RegulatoryApiPrincipal)
                authenticatedPrincipal;
    }


    // ============================================================
    // ============================================================
    // EXTERNAL API AUDIT
    // ============================================================
    // ============================================================

    private void auditExternal(
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
    // ============================================================
    // FILE RESPONSE
    // ============================================================
    // ============================================================

    private ResponseEntity<byte[]> fileResponse(

            String format,

            String filenameBase,

            String title,

            List<String> columns,

            List<Map<String, Object>> rows,

            String organizationName
    ) {

        if (
                format == null ||
                format.isBlank()
        ) {

            format = "xlsx";
        }

        byte[] bytes;

        MediaType contentType;

        String extension;

        switch (format.toLowerCase()) {

            // ----------------------------------------------------
            // CSV
            // ----------------------------------------------------

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
            // INVALID FORMAT
            // ----------------------------------------------------

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
    // ============================================================
    // DATE PARSER
    // ============================================================
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
