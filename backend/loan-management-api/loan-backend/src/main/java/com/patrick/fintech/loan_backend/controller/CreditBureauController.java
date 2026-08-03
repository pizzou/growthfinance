package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.CreditBureauCheck;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.CreditBureauService;
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
import java.util.Optional;

@RestController
@RequestMapping("/api/credit-bureau")
@RequiredArgsConstructor
public class CreditBureauController {

    private final CreditBureauService creditBureauService;

    private final BorrowerRepository borrowerRepository;

    private final CurrentUserUtil currentUserUtil;

    private final ReportExportService exportService;

    private final AuditService auditService;


    // ============================================================
    // RUN CREDIT BUREAU CHECK
    // ============================================================

    @PostMapping("/borrowers/{id}/check")
    @PreAuthorize(
            "hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','CREDIT_ANALYST')"
    )
    public ResponseEntity<ApiResponse<CreditBureauCheck>> check(
            @PathVariable Long id
    ) {

        Long orgId =
                currentUserUtil
                        .getCurrentOrganizationId();

        String requestedBy =
                currentUserUtil
                        .getCurrentUser()
                        .getName();

        CreditBureauCheck result =
                creditBureauService.runCheck(
                        id,
                        orgId,
                        requestedBy
                );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Credit bureau check completed",
                        result
                )
        );
    }


    // ============================================================
    // CREDIT BUREAU HISTORY
    // ============================================================

    @GetMapping("/borrowers/{id}/history")
    @PreAuthorize(
            "hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','CREDIT_ANALYST')"
    )
    public ResponseEntity<ApiResponse<List<CreditBureauCheck>>> history(
            @PathVariable Long id
    ) {

        Long orgId =
                currentUserUtil
                        .getCurrentOrganizationId();

        List<CreditBureauCheck> history =
                creditBureauService.getHistory(
                        id,
                        orgId
                );

        return ResponseEntity.ok(
                ApiResponse.ok(history)
        );
    }


    // ============================================================
    // LATEST CREDIT BUREAU REPORT
    // ============================================================

    @GetMapping("/borrowers/{id}/latest")
    @PreAuthorize(
            "hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','CREDIT_ANALYST')"
    )
    public ResponseEntity<ApiResponse<CreditBureauCheck>> latest(
            @PathVariable Long id
    ) {

        Long orgId =
                currentUserUtil
                        .getCurrentOrganizationId();

        Optional<CreditBureauCheck> latest =
                creditBureauService.getLatest(
                        id,
                        orgId
                );

        if (latest.isEmpty()) {

            return ResponseEntity.ok(
                    ApiResponse.error(
                            "No credit bureau check on file "
                                    + "for this borrower"
                    )
            );
        }

        return ResponseEntity.ok(
                ApiResponse.ok(
                        latest.get()
                )
        );
    }


    // ============================================================
    // REGULATORY CREDIT BUREAU REPORT
    // ============================================================

    @GetMapping("/regulatory")
    @PreAuthorize(
            "hasAnyRole('ADMIN','MANAGER','AUDITOR')"
    )
    public ResponseEntity<
            ApiResponse<List<CreditBureauCheck>>
            > regulatoryReport(

            @RequestParam(required = false)
            Long borrowerId,

            @RequestParam(required = false)
            String from,

            @RequestParam(required = false)
            String to
    ) {

        Long organizationId =
                currentUserUtil
                        .getCurrentOrganizationId();

        List<CreditBureauCheck> result =
                creditBureauService.getRegulatoryHistory(
                        organizationId,
                        borrowerId,
                        parseDate(from),
                        parseDate(to)
                );

        return ResponseEntity.ok(
                ApiResponse.ok(result)
        );
    }


    // ============================================================
    // REGULATORY CREDIT BUREAU EXPORT
    // ============================================================

    /**
     * GET
     *
     * /api/credit-bureau/regulatory/export
     *
     * Examples:
     *
     * /api/credit-bureau/regulatory/export?format=pdf
     *
     * /api/credit-bureau/regulatory/export?format=xlsx
     *
     * /api/credit-bureau/regulatory/export?format=csv
     *
     * Optional:
     *
     * borrowerId
     * from
     * to
     */
    @GetMapping("/regulatory/export")
    @PreAuthorize(
            "hasAnyRole('ADMIN','MANAGER','AUDITOR')"
    )
    public ResponseEntity<byte[]> exportRegulatoryReport(

            @RequestParam(
                    defaultValue = "xlsx"
            )
            String format,

            @RequestParam(required = false)
            Long borrowerId,

            @RequestParam(required = false)
            String from,

            @RequestParam(required = false)
            String to
    ) {

        Long organizationId =
                currentUserUtil
                        .getCurrentOrganizationId();

        LocalDate fromDate =
                parseDate(from);

        LocalDate toDate =
                parseDate(to);

        List<CreditBureauCheck> checks =
                creditBureauService.getRegulatoryHistory(
                        organizationId,
                        borrowerId,
                        fromDate,
                        toDate
                );

        List<String> columns =
                List.of(
                        "Reference",
                        "Borrower",
                        "National ID",
                        "Provider",
                        "Status",
                        "Credit Score",
                        "Risk Grade",
                        "Active Facilities",
                        "Delinquent Accounts",
                        "Outstanding Debt",
                        "Monthly Obligations",
                        "Default History",
                        "Active Listing",
                        "Listing Reason",
                        "Requested By",
                        "Created At"
                );

        List<Map<String, Object>> rows =
                new ArrayList<>();

        for (CreditBureauCheck check : checks) {

            Map<String, Object> row =
                    new LinkedHashMap<>();

            row.put(
                    "Reference",
                    check.getReference()
            );

            row.put(
                    "Borrower",
                    borrowerName(
                            check.getBorrower()
                    )
            );

            row.put(
                    "National ID",
                    check.getNationalIdChecked()
            );

            row.put(
                    "Provider",
                    check.getProvider()
            );

            row.put(
                    "Status",
                    check.getStatus() != null
                            ? check.getStatus().name()
                            : null
            );

            row.put(
                    "Credit Score",
                    check.getCreditScore()
            );

            row.put(
                    "Risk Grade",
                    check.getRiskGrade()
            );

            row.put(
                    "Active Facilities",
                    check.getActiveFacilities()
            );

            row.put(
                    "Delinquent Accounts",
                    check.getDelinquentAccounts()
            );

            row.put(
                    "Outstanding Debt",
                    check.getTotalOutstandingDebt()
            );

            row.put(
                    "Monthly Obligations",
                    check.getTotalMonthlyObligations()
            );

            row.put(
                    "Default History",
                    check.getHasDefaultHistory()
            );

            row.put(
                    "Active Listing",
                    check.getHasActiveListing()
            );

            row.put(
                    "Listing Reason",
                    check.getListingReason()
            );

            row.put(
                    "Requested By",
                    check.getRequestedBy()
            );

            row.put(
                    "Created At",
                    check.getCreatedAt()
            );

            rows.add(row);
        }


        String organizationName =
                currentUserUtil
                        .getCurrentUser()
                        .getOrganization()
                        .getName();

        String filename =
                "Credit-Bureau-Regulatory-Report-" +
                LocalDate.now().format(
                        DateTimeFormatter.ISO_DATE
                );


        // ========================================================
        // AUDIT EXPORT
        // ========================================================

        auditService.log(

                currentUserUtil
                        .getCurrentUser()
                        .getOrganization(),

                currentUserUtil
                        .getCurrentUser(),

                "EXPORT",

                "CreditBureauRegulatoryReport",

                borrowerId != null
                        ? String.valueOf(borrowerId)
                        : "ALL",

                "Exported Credit Bureau regulatory report as "
                        + format.toUpperCase(),

                null,

                null,

                "Credit Bureau"
        );


        return respond(
                format,
                filename,
                "Credit Bureau Regulatory Report",
                columns,
                rows,
                organizationName
        );
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


            default -> throw new IllegalArgumentException(
                    "Unsupported export format: "
                            + format
                            + ". Supported formats: "
                            + "csv, pdf, xlsx."
            );
        }


        return ResponseEntity.ok()

                .contentType(contentType)

                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""
                                + filename
                                + "."
                                + extension
                                + "\""
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

        StringBuilder sb =
                new StringBuilder();

        sb.append(
                String.join(
                        ",",
                        columns
                )
        );

        sb.append('\n');


        for (Map<String, Object> row : rows) {

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


                // CSV formula injection protection
                if (
                        !cell.isEmpty()
                                &&
                        "=+-@\t".indexOf(
                                cell.charAt(0)
                        ) >= 0
                ) {

                    cell =
                            "'" + cell;
                }


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
                            "\"" + cell + "\"";
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
    // BORROWER NAME
    // ============================================================

    private String borrowerName(
            Borrower borrower
    ) {

        if (borrower == null) {
            return "";
        }

        String first =
                borrower.getFirstName() != null
                        ? borrower.getFirstName().trim()
                        : "";

        String last =
                borrower.getLastName() != null
                        ? borrower.getLastName().trim()
                        : "";

        return (
                first + " " + last
        ).trim();
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

        return LocalDate.parse(value);
    }


    // ============================================================
    // BORROWER OWNERSHIP CHECK
    // ============================================================

    private void assertOwnedByCallerOrg(
            Long borrowerId
    ) {

        Borrower borrower =
                borrowerRepository
                        .findById(borrowerId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Borrower not found: "
                                                + borrowerId
                                )
                        );


        Long callerOrgId =
                currentUserUtil
                        .getCurrentOrganizationId();


        if (
                borrower.getOrganization() == null
                        ||
                borrower.getOrganization().getId() == null
                        ||
                !borrower
                        .getOrganization()
                        .getId()
                        .equals(callerOrgId)
        ) {

            throw new SecurityException(
                    "Access denied"
            );
        }
    }
}