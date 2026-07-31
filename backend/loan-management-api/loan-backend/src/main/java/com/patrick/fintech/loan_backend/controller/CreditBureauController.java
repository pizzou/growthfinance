package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.CreditBureauCheck;
import com.patrick.fintech.loan_backend.service.CreditBureauService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/credit-bureau")
@RequiredArgsConstructor
public class CreditBureauController {

    private final CreditBureauService creditBureauService;
    private final CurrentUserUtil currentUserUtil;

    @PostMapping("/borrowers/{id}/check")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','CREDIT_ANALYST')")
    public ResponseEntity<ApiResponse<CreditBureauCheck>> check(@PathVariable Long id) {
        CreditBureauCheck result = creditBureauService.runCheck(
            id, currentUserUtil.getCurrentOrganizationId(), currentUserUtil.getCurrentUser().getName());
        return ResponseEntity.ok(ApiResponse.ok("Credit bureau check completed", result));
    }

    @GetMapping("/borrowers/{id}/history")
    public ResponseEntity<ApiResponse<List<CreditBureauCheck>>> history(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(creditBureauService.getHistory(id)));
    }

    @GetMapping("/borrowers/{id}/latest")
    public ResponseEntity<ApiResponse<CreditBureauCheck>> latest(@PathVariable Long id) {
        Optional<CreditBureauCheck> latest = creditBureauService.getLatest(id);
        return ResponseEntity.ok(latest.map(ApiResponse::ok)
            .orElseGet(() -> ApiResponse.error("No credit bureau check on file for this borrower")));
    }
}
