package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.CreditBureauDispute;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.service.CreditBureauDisputeService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/regulatory/credit-bureau/disputes")
@RequiredArgsConstructor
public class CreditBureauDisputeController {

    private final CreditBureauDisputeService disputeService;
    private final OrganizationRepository orgRepo;
    private final CurrentUserUtil currentUserUtil;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','AUDITOR','CUSTOMER_SUPPORT')")
    public ResponseEntity<ApiResponse<CreditBureauDispute>> open(@RequestBody Map<String, Object> body) {
        Organization org = orgRepo.findById(currentUserUtil.getCurrentOrganizationId())
            .orElseThrow(() -> new RuntimeException("Organization not found"));

        CreditBureauDispute dispute = disputeService.open(
            org,
            Long.valueOf(String.valueOf(body.get("borrowerId"))),
            body.get("loanId") != null ? Long.valueOf(String.valueOf(body.get("loanId"))) : null,
            body.get("submissionRecordId") != null ? Long.valueOf(String.valueOf(body.get("submissionRecordId"))) : null,
            (String) body.get("reason"),
            (String) body.get("disputedField"),
            (String) body.get("oldValue"),
            (String) body.get("requestedValue"),
            (String) body.get("supportingDocumentUrl")
        );
        return ResponseEntity.ok(ApiResponse.ok("Dispute opened", dispute));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','AUDITOR','CUSTOMER_SUPPORT')")
    public ResponseEntity<ApiResponse<List<CreditBureauDispute>>> list(@RequestParam(required = false) String status) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        CreditBureauDispute.Status s = status != null ? CreditBureauDispute.Status.valueOf(status.toUpperCase()) : null;
        return ResponseEntity.ok(ApiResponse.ok(disputeService.list(orgId, s)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','AUDITOR','CUSTOMER_SUPPORT')")
    public ResponseEntity<ApiResponse<CreditBureauDispute>> get(@PathVariable Long id) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        return ResponseEntity.ok(ApiResponse.ok(disputeService.getForOrg(id, orgId)));
    }

    @PatchMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','AUDITOR')")
    public ResponseEntity<ApiResponse<CreditBureauDispute>> review(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        String notes = body != null ? body.get("notes") : null;
        CreditBureauDispute d = disputeService.review(id, orgId, currentUserUtil.getCurrentUser().getName(), notes);
        return ResponseEntity.ok(ApiResponse.ok("Under review", d));
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','AUDITOR')")
    public ResponseEntity<ApiResponse<CreditBureauDispute>> resolve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        CreditBureauDispute.Status outcome = CreditBureauDispute.Status.valueOf(body.get("outcome").toUpperCase());
        CreditBureauDispute d = disputeService.resolve(id, orgId, currentUserUtil.getCurrentUser().getName(), outcome, body.get("resolution"));
        return ResponseEntity.ok(ApiResponse.ok("Resolved", d));
    }
}