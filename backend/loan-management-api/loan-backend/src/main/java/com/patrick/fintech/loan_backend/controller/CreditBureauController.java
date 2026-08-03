package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.CreditBureauCheckResponse;
import com.patrick.fintech.loan_backend.model.CreditBureauCheck;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.service.CreditBureauService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/credit-bureau")
@RequiredArgsConstructor
public class CreditBureauController {

    private final CreditBureauService creditBureauService;
    private final CurrentUserUtil currentUserUtil;


    // ============================================================
    // RUN CREDIT BUREAU CHECK
    // ============================================================

    @PostMapping("/borrowers/{borrowerId}/check")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CreditBureauCheckResponse> runCheck(

            @PathVariable Long borrowerId

    ) {

        // --------------------------------------------------------
        // Get authenticated user
        // --------------------------------------------------------

        User currentUser =
                currentUserUtil.getCurrentUser();

        if (currentUser == null) {

            return ResponseEntity
                    .status(401)
                    .build();
        }


        // --------------------------------------------------------
        // Get organization from authenticated user
        // --------------------------------------------------------

        if (currentUser.getOrganization() == null) {

            return ResponseEntity
                    .badRequest()
                    .build();
        }


        Long organizationId =
                currentUser
                        .getOrganization()
                        .getId();


        // --------------------------------------------------------
        // User has name, NOT username
        // --------------------------------------------------------

        String requestedBy =
                currentUser.getName();


        // --------------------------------------------------------
        // Run credit bureau check
        // --------------------------------------------------------

        CreditBureauCheck check =
                creditBureauService.runCheck(
                        borrowerId,
                        organizationId,
                        requestedBy
                );


        // --------------------------------------------------------
        // Convert response
        // --------------------------------------------------------

        CreditBureauCheckResponse response =
                creditBureauService.toOfficerResponse(
                        check
                );


        return ResponseEntity.ok(response);
    }


    // ============================================================
    // LATEST CREDIT BUREAU CHECK
    // ============================================================

    @GetMapping("/borrowers/{borrowerId}/latest")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CreditBureauCheckResponse> getLatest(

            @PathVariable Long borrowerId

    ) {

        User currentUser =
                currentUserUtil.getCurrentUser();

        if (currentUser == null) {

            return ResponseEntity
                    .status(401)
                    .build();
        }


        if (currentUser.getOrganization() == null) {

            return ResponseEntity
                    .badRequest()
                    .build();
        }


        Long organizationId =
                currentUser
                        .getOrganization()
                        .getId();


        return creditBureauService
                .getOfficerLatest(
                        borrowerId,
                        organizationId
                )
                .map(ResponseEntity::ok)
                .orElseGet(
                        () ->
                                ResponseEntity
                                        .notFound()
                                        .build()
                );
    }


    // ============================================================
    // CREDIT BUREAU HISTORY
    // ============================================================

    @GetMapping("/borrowers/{borrowerId}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CreditBureauCheckResponse>> getHistory(

            @PathVariable Long borrowerId

    ) {

        User currentUser =
                currentUserUtil.getCurrentUser();

        if (currentUser == null) {

            return ResponseEntity
                    .status(401)
                    .build();
        }


        if (currentUser.getOrganization() == null) {

            return ResponseEntity
                    .badRequest()
                    .build();
        }


        Long organizationId =
                currentUser
                        .getOrganization()
                        .getId();


        return ResponseEntity.ok(
                creditBureauService.getOfficerHistory(
                        borrowerId,
                        organizationId
                )
        );
    }
}