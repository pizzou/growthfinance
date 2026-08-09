package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.ESignatureRequest;
import com.patrick.fintech.loan_backend.service.ESignatureService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public, token-secured borrower signing flow — no login required, matching
 * how borrowers actually receive and act on a signing link via SMS.
 * The token itself is the credential; OTP adds a second factor.
 */
@RestController
@RequestMapping("/api/public/esignature")
@RequiredArgsConstructor
@Slf4j
public class PublicESignatureController {

    private final ESignatureService esignatureService;

    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> view(@PathVariable String token) {
        ESignatureRequest req = esignatureService.getByToken(token);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("status", req.getStatus());
        view.put("documentType", req.getDocumentType());
        view.put("documentText", req.getDocumentSnapshot());
        view.put("consentText", req.getConsentText());
        view.put("borrowerName", req.getBorrower() != null ? req.getBorrower().getFullName() : null);
        view.put("expiresAt", req.getExpiresAt());
        view.put("signedAt", req.getSignedAt());
        // OTP hash is never exposed here
        return ResponseEntity.ok(ApiResponse.ok(view));
    }

    @PostMapping("/{token}/resend-otp")
    public ResponseEntity<ApiResponse<String>> resend(@PathVariable String token) {
        esignatureService.resendOtp(token);
        return ResponseEntity.ok(ApiResponse.ok("A new verification code has been sent by SMS."));
    }

    @PostMapping("/{token}/sign")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sign(
            @PathVariable String token, @RequestBody Map<String, String> body, HttpServletRequest httpReq) {
        String otp = body.get("otp");
        String fullName = body.get("fullName");
        String ip = clientIp(httpReq);
        String ua = httpReq.getHeader("User-Agent");

        ESignatureRequest req = esignatureService.verifyAndSign(token, otp, fullName, ip, ua);
        return ResponseEntity.ok(ApiResponse.ok("Document signed successfully", Map.of(
            "status", req.getStatus(),
            "signedAt", req.getSignedAt(),
            "documentHash", req.getDocumentHash()
        )));
    }

    @PostMapping("/{token}/decline")
    public ResponseEntity<ApiResponse<String>> decline(@PathVariable String token, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "Not specified") : "Not specified";
        esignatureService.decline(token, reason);
        return ResponseEntity.ok(ApiResponse.ok("We've recorded that you declined to sign. Your loan officer will follow up."));
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
