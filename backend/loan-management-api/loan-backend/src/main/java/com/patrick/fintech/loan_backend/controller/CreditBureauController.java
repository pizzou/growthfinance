package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.CreditBureauCheckResponse;
import com.patrick.fintech.loan_backend.model.CreditBureauCheck;
import com.patrick.fintech.loan_backend.service.CreditBureauService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/credit-bureau")
@RequiredArgsConstructor
public class CreditBureauController {

    private final CreditBureauService creditBureauService;

    // ============================================================
    // RUN CREDIT BUREAU CHECK
    // ============================================================

    @PostMapping("/borrowers/{borrowerId}/check")
    public ResponseEntity<CreditBureauCheckResponse> runCheck(
            @PathVariable Long borrowerId,
            @RequestParam(name = "organizationId") Long organizationId,
            @RequestParam(name = "requestedBy", required = false) String requestedBy
    ) {

        CreditBureauCheck check =
                creditBureauService.runCheck(
                        borrowerId,
                        organizationId,
                        requestedBy
                );

        return ResponseEntity.ok(
                creditBureauService.toOfficerResponse(check)
        );
    }

    // ============================================================
    // LATEST CREDIT BUREAU CHECK
    // ============================================================

    @GetMapping("/borrowers/{borrowerId}/latest")
    public ResponseEntity<CreditBureauCheckResponse> getLatest(
            @PathVariable Long borrowerId,
            @RequestParam(name = "organizationId") Long organizationId
    ) {

        return creditBureauService
                .getOfficerLatest(
                        borrowerId,
                        organizationId
                )
                .map(ResponseEntity::ok)
                .orElseGet(
                        () -> ResponseEntity.notFound().build()
                );
    }

    // ============================================================
    // CREDIT BUREAU HISTORY
    // ============================================================

    @GetMapping("/borrowers/{borrowerId}/history")
    public ResponseEntity<List<CreditBureauCheckResponse>> getHistory(
            @PathVariable Long borrowerId,
            @RequestParam(name = "organizationId") Long organizationId
    ) {

        return ResponseEntity.ok(
                creditBureauService.getOfficerHistory(
                        borrowerId,
                        organizationId
                )
        );
    }
}