
package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.CreditBureauCheckResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.CreditBureauRecord;
import com.patrick.fintech.loan_backend.model.CreditBureauCheck;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.CreditBureauService;
import com.patrick.fintech.loan_backend.service.RegulatoryReportingService;
import com.patrick.fintech.loan_backend.service.ReportExportService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/credit-bureau")
@RequiredArgsConstructor
public class CreditBureauController {

    private final CreditBureauService creditBureauService;

    private final CurrentUserUtil currentUserUtil;

    private final RegulatoryReportingService reportingService;

    private final ReportExportService exportService;

    private final AuditService auditService;

    private final OrganizationRepository organizationRepository;


    // ============================================================
    // RUN CREDIT BUREAU CHECK
    // ============================================================

    @PostMapping("/borrowers/{borrowerId}/check")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CreditBureauCheckResponse> runCheck(

            @PathVariable Long borrowerId

    ) {

        User currentUser =
                currentUserUtil.getCurrentUser();


        if (currentUser == null) {

            return ResponseEntity
                    .status(401)
                    .build();
        }


        if (currentUser.getOrganization() == null) {

            return ResponseEntity
                    .badRequest()
                    .build();
        }


        Long organizationId =
                currentUser
                        .getOrganization()
                        .getId();


        String requestedBy =
                currentUser.getName();


        CreditBureauCheck check =
                creditBureauService.runCheck(
                        borrowerId,
                        organizationId,
                        requestedBy
                );


        CreditBureauCheckResponse response =
                creditBureauService.toOfficerResponse(
                        check
                );


        return ResponseEntity.ok(response);
    }


    // ============================================================
    // LATEST CREDIT BUREAU CHECK
    // ============================================================

    @GetMapping("/borrowers/{borrowerId}/latest")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CreditBureauCheckResponse> getLatest(

            @PathVariable Long borrowerId

    ) {

        User currentUser =
                currentUserUtil.getCurrentUser();


        if (currentUser == null) {

            return ResponseEntity
                    .status(401)
                    .build();
        }


        if (currentUser.getOrganization() == null) {

            return ResponseEntity
                    .badRequest()
                    .build();
        }


        Long organizationId =
                currentUser
                        .getOrganization()
                        .getId();


        return creditBureauService
                .getOfficerLatest(
                        borrowerId,
                        organizationId
                )
                .map(ResponseEntity::ok)
                .orElseGet(
                        () ->
                                ResponseEntity
                                        .notFound()
                                        .build()
                );
    }


    // ============================================================
    // CREDIT BUREAU HISTORY
    // ============================================================

    @GetMapping("/borrowers/{borrowerId}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CreditBureauCheckResponse>> getHistory(

            @PathVariable Long borrowerId

    ) {

        User currentUser =
                currentUserUtil.getCurrentUser();


        if (currentUser == null) {

            return ResponseEntity
                    .status(401)
                    .build();
        }


        if (currentUser.getOrganization() == null) {

            return ResponseEntity
                    .badRequest()
                    .build();
        }


        Long organizationId =
                currentUser
                        .getOrganization()
                        .getId();


        return ResponseEntity.ok(
                creditBureauService.getOfficerHistory(
                        borrowerId,
                        organizationId
                )
        );
    }


    // ============================================================
    // CREDIT BUREAU EXPORT
    //
    // This is the normal authenticated application export.
    //
    // IMPORTANT:
    // This endpoint does NOT use RegulatoryApiPrincipal.
    // External regulatory API authentication remains under:
    //
    // /api/regulatory/external/**
    //
    // ============================================================

    @GetMapping(
            value = "/export",
            produces = {
                    MediaType.APPLICATION_JSON_VALUE,
                    "text/csv",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    MediaType.APPLICATION_PDF_VALUE
            }
    )
    @PreAuthorize(
            "isAuthenticated()"
    )
    public ResponseEntity<?> export(

            @RequestParam(
                    defaultValue = "xlsx"
            )
            String format,

            @RequestParam(
                    required = false
            )
            Long borrowerId,

            @RequestParam(
                    required = false
            )
            String from,

            @RequestParam(
                    required = false
            )
            String to
    ) {

        // --------------------------------------------------------
        // CURRENT USER
        // --------------------------------------------------------

        User currentUser =
                currentUserUtil.getCurrentUser();


        if (currentUser == null) {

            return ResponseEntity
                    .status(401)
                    .body(
                            ApiResponse.error(
                                    "Authentication required."
                            )
                    );
        }


        // --------------------------------------------------------
        // ORGANIZATION
        // --------------------------------------------------------

        if (currentUser.getOrganization() == null ||
                currentUser.getOrganization().getId() == null) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            ApiResponse.error(
                                    "Your account is not associated with an organization."
                            )
                    );
        }


        Long organizationId =
                currentUser
                        .getOrganization()
                        .getId();


        // --------------------------------------------------------
        // FORMAT
        // --------------------------------------------------------

        String requestedFormat =
                format == null
                        ? "xlsx"
                        : format
                                .trim()
                                .toLowerCase();


        if (!List.of(
                "json",
                "csv",
                "xlsx",
                "pdf"
        ).contains(requestedFormat)) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            ApiResponse.error(
                                    "Unsupported export format. " +
                                    "Supported formats: json, csv, xlsx, pdf."
                            )
                    );
        }


        // --------------------------------------------------------
        // DATES
        // --------------------------------------------------------

        LocalDate fromDate;

        LocalDate toDate;


        try {

            fromDate =
                    parseDate(from);


            toDate =
                    parseDate(to);


        } catch (IllegalArgumentException exception) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            ApiResponse.error(
                                    "Invalid date. Use YYYY-MM-DD."
                            )
                    );
        }


        // --------------------------------------------------------
        // DATE VALIDATION
        // --------------------------------------------------------

        if (
                fromDate != null &&
                toDate != null &&
                fromDate.isAfter(toDate)
        ) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            ApiResponse.error(
                                    "The 'from' date cannot be after the 'to' date."
                            )
                    );
        }


        // --------------------------------------------------------
        // BUILD CREDIT BUREAU RECORDS
        // --------------------------------------------------------

        List<CreditBureauRecord> records =
                reportingService.buildCreditBureauExport(

                        organizationId,

                        borrowerId,

                        fromDate,

                        toDate
                );


        // --------------------------------------------------------
        // AUDIT
        // --------------------------------------------------------

        auditExport(
                currentUser,
                organizationId,
                records.size(),
                requestedFormat
        );


        // --------------------------------------------------------
        // JSON
        // --------------------------------------------------------

        if ("json".equals(requestedFormat)) {

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
                        .map(
                                organization ->
                                        organization.getName()
                        )
                        .orElse(
                                "Organization"
                        );


        // --------------------------------------------------------
        // COLUMNS
        // --------------------------------------------------------

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


        // --------------------------------------------------------
        // ROWS
        // --------------------------------------------------------

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


        // --------------------------------------------------------
        // FILE RESPONSE
        // --------------------------------------------------------

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
    // AUDIT EXPORT
    // ============================================================

    private void auditExport(

            User currentUser,

            Long organizationId,

            int recordCount,

            String format

    ) {

        try {

            String userName =
                    currentUser.getName();


            auditService.log(

                    organizationRepository
                            .findById(organizationId)
                            .orElse(null),

                    currentUser,

                    "EXPORT",

                    "CreditBureau",

                    userName,

                    "Credit Bureau export generated. " +
                    "Format=" +
                    format.toUpperCase() +
                    ", Records=" +
                    recordCount,

                    null,
                    null,

                    "Credit Bureau"
            );

        } catch (Exception exception) {

            /*
             * Audit failure should not destroy
             * an otherwise successful report export.
             *
             * The failed audit should still be
             * logged for investigation.
             */

            System.err.println(
                    "Credit Bureau export audit failed: "
                            + exception.getMessage()
            );
        }
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
                        : format
                                .trim()
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
                value == null ||
                value.isBlank()
        ) {

            return null;
        }


        try {

            return LocalDate.parse(
                    value.trim()
            );

        } catch (Exception exception) {

            throw new IllegalArgumentException(
                    "Invalid date"
            );
        }
    }
}

