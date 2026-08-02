package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.service.BnrFinancialStatementService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/bnr/financial-reports")
@RequiredArgsConstructor
public class BnrFinancialReportController {

    private final BnrFinancialStatementService
            bnrFinancialStatementService;


    // ============================================================
    // BNR FINANCIAL STATEMENT
    // ============================================================

    /**
     * Accounting-based BNR financial statement.
     *
     * Source:
     *
     * JournalEntry
     *      ↓
     * JournalLine
     *      ↓
     * ChartOfAccount
     *
     * Example:
     *
     * GET /api/bnr/financial-reports/financial-statement
     *     ?orgId=1
     *     &from=2026-01-01
     *     &to=2026-01-31
     */
    @GetMapping("/financial-statement")
    public ResponseEntity<Map<String, Object>>
    getFinancialStatement(

            @RequestParam Long orgId,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate from,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate to
    ) {

        Map<String, Object> report =
                bnrFinancialStatementService
                        .buildFinancialStatement(
                                orgId,
                                from,
                                to
                        );

        return ResponseEntity.ok(report);
    }
}