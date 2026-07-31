package com.patrick.fintech.loan_backend.controller;
import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.MfaService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/mfa") @RequiredArgsConstructor
public class MfaController {
    private final MfaService mfaService;
    private final CurrentUserUtil currentUserUtil;
    private final AuditService auditService;
    @PostMapping("/setup")
    public ResponseEntity<ApiResponse<MfaService.MfaSetupResponse>> setup() {
        String orgName=currentUserUtil.getCurrentUser().getOrganization()!=null?currentUserUtil.getCurrentUser().getOrganization().getName():"LoanSaaS Pro";
        return ResponseEntity.ok(ApiResponse.ok(mfaService.beginSetup(currentUserUtil.getCurrentUserId(),orgName)));
    }
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<Map<String,Object>>> confirm(@RequestBody Map<String,String> body) {
        boolean valid=mfaService.confirmSetup(currentUserUtil.getCurrentUserId(),body.get("code"));
        if(!valid) throw new RuntimeException("Invalid verification code");
        User user = currentUserUtil.getCurrentUser();
        auditService.log(user.getOrganization(), user, "MFA_ENABLED", "AUTH",
            String.valueOf(user.getId()), "Two-factor authentication enabled", null, null, "Authentication");
        return ResponseEntity.ok(ApiResponse.ok("MFA enabled",Map.of("enabled",true)));
    }
    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<Void>> disable() {
        User user = currentUserUtil.getCurrentUser();
        mfaService.disable(currentUserUtil.getCurrentUserId());
        auditService.log(user.getOrganization(), user, "MFA_DISABLED", "AUTH",
            String.valueOf(user.getId()), "Two-factor authentication disabled", null, null, "Authentication");
        return ResponseEntity.ok(ApiResponse.ok("MFA disabled"));
    }
}
