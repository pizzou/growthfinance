package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.ESignatureRequest;
import com.patrick.fintech.loan_backend.service.ESignatureService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Staff-side controls for sending a loan agreement out for e-signature. */
@RestController
@RequestMapping("/api/loans/{loanId}/esignature")
@RequiredArgsConstructor
public class ESignatureController {

    private final ESignatureService esignatureService;
    private final CurrentUserUtil currentUserUtil;

    @PostMapping("/initiate")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initiate(
            @PathVariable Long loanId, @RequestBody(required = false) Map<String, String> body) {
        String docType = body != null ? body.getOrDefault("documentType", "LOAN_AGREEMENT") : "LOAN_AGREEMENT";
        ESignatureRequest req = esignatureService.initiate(loanId, docType, currentUserUtil.getCurrentUser().getName());
        // signingToken is deliberately the only borrower-facing identifier — never expose the OTP hash
        return ResponseEntity.ok(ApiResponse.ok("Signing link sent to borrower's phone", Map.of(
            "id", req.getId(),
            "status", req.getStatus(),
            "signingToken", req.getSigningToken(),
            "expiresAt", req.getExpiresAt()
        )));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ESignatureRequest>>> history(@PathVariable Long loanId) {
        return ResponseEntity.ok(ApiResponse.ok(esignatureService.history(loanId)));
    }
}
