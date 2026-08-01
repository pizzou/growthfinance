
package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.service.ReportingService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportingController {

    private final ReportingService reportingService;
    private final CurrentUserUtil currentUserUtil;

    /**
     * Loan status report for the currently authenticated organization.
     */
    @GetMapping("/loans/{orgId}")
    public ResponseEntity<Map<String, Long>> loanStatusReport(
            @PathVariable Long orgId) {

        validateOrganization(orgId);

        return ResponseEntity.ok(
            reportingService.loanStatusReport(orgId)
        );
    }

    /**
     * Payment report for the currently authenticated organization.
     *
     * BigDecimal is used because financial amounts should not be represented
     * using Double.
     */
    @GetMapping("/payments/{orgId}")
    public ResponseEntity<Map<String, BigDecimal>> paymentReport(
            @PathVariable Long orgId) {

        validateOrganization(orgId);

        return ResponseEntity.ok(
            reportingService.paymentReport(orgId)
        );
    }

    /**
     * Export loans belonging to the authenticated organization.
     */
    @GetMapping("/export/loans")
    public ResponseEntity<String> exportLoans() {

        Long orgId = currentUserUtil.getCurrentOrganizationId();

        return csvResponse(
            reportingService.exportLoansCsv(orgId),
            "loans"
        );
    }

    /**
     * Export payments belonging to the authenticated organization.
     */
    @GetMapping("/export/payments")
    public ResponseEntity<String> exportPayments() {

        Long orgId = currentUserUtil.getCurrentOrganizationId();

        return csvResponse(
            reportingService.exportPaymentsCsv(orgId),
            "payments"
        );
    }

    /**
     * Export overdue payments belonging to the authenticated organization.
     */
    @GetMapping("/export/overdue")
    public ResponseEntity<String> exportOverdue() {

        Long orgId = currentUserUtil.getCurrentOrganizationId();

        return csvResponse(
            reportingService.exportOverdueCsv(orgId),
            "overdue-payments"
        );
    }

    /**
     * Export portfolio summary belonging to the authenticated organization.
     */
    @GetMapping("/export/summary")
    public ResponseEntity<String> exportSummary() {

        Long orgId = currentUserUtil.getCurrentOrganizationId();

        return csvResponse(
            reportingService.exportPortfolioSummaryCsv(orgId),
            "portfolio-summary"
        );
    }

    /**
     * Make sure the organization in the URL belongs to the
     * currently authenticated user.
     */
    private void validateOrganization(Long requestedOrgId) {

        Long currentOrgId = currentUserUtil.getCurrentOrganizationId();

        if (requestedOrgId == null || currentOrgId == null
                || !requestedOrgId.equals(currentOrgId)) {

            throw new RuntimeException("Access denied");
        }
    }

    /**
     * Build a CSV HTTP response.
     */
    private ResponseEntity<String> csvResponse(
            String csv,
            String filename) {

        String finalFilename =
            filename + "-" + LocalDate.now() + ".csv";

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + finalFilename + "\""
            )
            .body(csv);
    }
}
