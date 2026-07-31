package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.CreditBureauCheck;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
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
    private final BorrowerRepository borrowerRepository;
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
        assertOwnedByCallerOrg(id);
        return ResponseEntity.ok(ApiResponse.ok(creditBureauService.getHistory(id)));
    }

    @GetMapping("/borrowers/{id}/latest")
    public ResponseEntity<ApiResponse<CreditBureauCheck>> latest(@PathVariable Long id) {
        assertOwnedByCallerOrg(id);
        Optional<CreditBureauCheck> latest = creditBureauService.getLatest(id);
        return ResponseEntity.ok(latest.map(ApiResponse::ok)
            .orElseGet(() -> ApiResponse.error("No credit bureau check on file for this borrower")));
    }

    /** Confirms the borrower belongs to the caller's own organization before returning any
     *  bureau data for them — credit history, default status, and outstanding debt are
     *  sensitive enough that a borrower ID alone must not be sufficient to read them. */
    private void assertOwnedByCallerOrg(Long borrowerId) {
        Borrower borrower = borrowerRepository.findById(borrowerId)
            .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));
        Long callerOrgId = currentUserUtil.getCurrentOrganizationId();
        if (borrower.getOrganization() == null || !borrower.getOrganization().getId().equals(callerOrgId)) {
            throw new RuntimeException("Access denied");
        }
    }
}