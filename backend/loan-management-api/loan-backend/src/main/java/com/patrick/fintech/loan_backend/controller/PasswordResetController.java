package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.ForgotPasswordRequest;
import com.patrick.fintech.loan_backend.dto.ResetPasswordRequest;
import com.patrick.fintech.loan_backend.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest req) {
        passwordResetService.requestReset(req.getEmail());
        return ResponseEntity.ok(ApiResponse.ok(
            "If that email exists, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.resetPassword(req.getToken(), req.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok("Password reset successfully."));
    }
}