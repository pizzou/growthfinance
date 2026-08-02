package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.ApiClientCreatedResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.ApiClientResponse;
import com.patrick.fintech.loan_backend.dto.regulatory.CreateApiClientRequest;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.service.RegulatoryApiClientService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/regulatory/api-clients")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class RegulatoryApiClientController {

    private final RegulatoryApiClientService service;
    private final CurrentUserUtil currentUserUtil;

   
    @GetMapping
    public ResponseEntity<ApiResponse<List<ApiClientResponse>>> list() {

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        List<ApiClientResponse> clients =
                service.listClients(organizationId);

        return ResponseEntity.ok(
                ApiResponse.ok(clients)
        );
    }

    
    @PostMapping
    public ResponseEntity<ApiResponse<ApiClientCreatedResponse>> create(
            @RequestBody CreateApiClientRequest request
    ) {

        User currentUser =
                currentUserUtil.getCurrentUser();

        if (currentUser == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(
                            ApiResponse.error(
                                    "Authentication required."
                            )
                    );
        }

        if (currentUser.getOrganization() == null) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(
                            ApiResponse.error(
                                    "User is not associated with an organization."
                            )
                    );
        }

        ApiClientCreatedResponse created =
                service.createClient(
                        currentUser.getOrganization(),
                        currentUser,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.ok(
                                "API client created successfully. " +
                                "The API key is shown only once.",
                                created
                        )
                );
    }

    /**
     * Revoke an API client.
     *
     * POST
     * /api/regulatory/api-clients/{id}/revoke
     *
     * Optional body:
     *
     * {
     *   "reason": "Credentials rotated"
     * }
     */
    @PostMapping("/{id}/revoke")
    public ResponseEntity<ApiResponse<Void>> revoke(
            @PathVariable Long id,
            @RequestBody(required = false)
            Map<String, String> body
    ) {

        User currentUser =
                currentUserUtil.getCurrentUser();

        if (currentUser == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(
                            ApiResponse.error(
                                    "Authentication required."
                            )
                    );
        }

        if (currentUser.getOrganization() == null) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(
                            ApiResponse.error(
                                    "User is not associated with an organization."
                            )
                    );
        }

        String reason = null;

        if (body != null) {
            reason = body.get("reason");
        }

        service.revoke(
                currentUser
                        .getOrganization()
                        .getId(),
                id,
                currentUser,
                reason
        );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "API key revoked."
                )
        );
    }
}