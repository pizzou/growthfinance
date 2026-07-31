package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.ApiClientCreatedResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.ApiClientResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.CreateApiClientRequest;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.service.RegulatoryApiClientService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin-only screen for issuing and revoking the API keys that let BNR / an authorized
 * credit bureau call /api/regulatory/external/**. Only ADMIN — this is credential
 * issuance, one tier more sensitive than viewing the reports themselves.
 */
@RestController
@RequestMapping("/api/regulatory/api-clients")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN','MANAGER','LOAN_OFFICER')")
public class RegulatoryApiClientController {

    private final RegulatoryApiClientService service;
    private final CurrentUserUtil currentUserUtil;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ApiClientResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(service.listClients(currentUserUtil.getCurrentOrganizationId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ApiClientCreatedResponse>> create(@RequestBody CreateApiClientRequest req) {
        User user = currentUserUtil.getCurrentUser();
        ApiClientCreatedResponse created = service.createClient(user.getOrganization(), user, req);
        return ResponseEntity.ok(ApiResponse.ok(created));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<ApiResponse<Void>> revoke(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        User user = currentUserUtil.getCurrentUser();
        String reason = body != null ? body.getOrDefault("reason", null) : null;
        service.revoke(user.getOrganization().getId(), id, user, reason);
        return ResponseEntity.ok(ApiResponse.ok("API key revoked"));
    }
}