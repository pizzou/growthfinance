package com.patrick.fintech.loan_backend.controller;
import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.KycCheck;
import com.patrick.fintech.loan_backend.service.ComplianceService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/api/compliance") @RequiredArgsConstructor
public class ComplianceController {
    private final ComplianceService complianceService;
    private final CurrentUserUtil currentUserUtil;
    @PostMapping("/borrowers/{id}/screen")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','CREDIT_ANALYST')")
    public ResponseEntity<ApiResponse<KycCheck>> screen(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(complianceService.runFullScreening(id,currentUserUtil.getCurrentOrganizationId())));
    }
    @GetMapping("/borrowers/{id}/history")
    public ResponseEntity<ApiResponse<List<KycCheck>>> history(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(complianceService.getHistoryForBorrower(id)));
    }
    @GetMapping("/borrowers/{id}/status")
    public ResponseEntity<ApiResponse<Map<String,Object>>> status(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("kycClear",complianceService.isKycCurrentlyClear(id))));
    }
    @GetMapping("/pending-reviews")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CREDIT_ANALYST')")
    public ResponseEntity<ApiResponse<List<KycCheck>>> pending() {
        return ResponseEntity.ok(ApiResponse.ok(complianceService.getPendingReviews(currentUserUtil.getCurrentOrganizationId())));
    }
    @PostMapping("/checks/{checkId}/decide")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','CREDIT_ANALYST')")
    public ResponseEntity<ApiResponse<KycCheck>> decide(@PathVariable Long checkId,@RequestBody Map<String,String> body) {
        return ResponseEntity.ok(ApiResponse.ok(complianceService.manualReview(checkId,
            currentUserUtil.getCurrentUser().getName(),KycCheck.CheckResult.valueOf(body.get("decision")),body.getOrDefault("notes",""))));
    }
}
