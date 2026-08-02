
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


    // ============================================================
    // RUN CREDIT BUREAU CHECK
    // ============================================================

    @PostMapping("/borrowers/{id}/check")
    @PreAuthorize(
            "hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','CREDIT_ANALYST')"
    )
    public ResponseEntity<ApiResponse<CreditBureauCheck>> check(
            @PathVariable Long id
    ) {

        Long orgId =
                currentUserUtil
                        .getCurrentOrganizationId();


        String requestedBy =
                currentUserUtil
                        .getCurrentUser()
                        .getName();


        CreditBureauCheck result =
                creditBureauService.runCheck(
                        id,
                        orgId,
                        requestedBy
                );


        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Credit bureau check completed",
                        result
                )
        );
    }


    // ============================================================
    // CREDIT BUREAU HISTORY
    // ============================================================

    @GetMapping("/borrowers/{id}/history")
    @PreAuthorize(
            "hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','CREDIT_ANALYST')"
    )
    public ResponseEntity<ApiResponse<List<CreditBureauCheck>>> history(
            @PathVariable Long id
    ) {

        Long orgId =
                currentUserUtil
                        .getCurrentOrganizationId();


        List<CreditBureauCheck> history =
                creditBureauService.getHistory(
                        id,
                        orgId
                );


        return ResponseEntity.ok(
                ApiResponse.ok(
                        history
                )
        );
    }


    // ============================================================
    // LATEST CREDIT BUREAU REPORT
    // ============================================================

    @GetMapping("/borrowers/{id}/latest")
    @PreAuthorize(
            "hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','CREDIT_ANALYST')"
    )
    public ResponseEntity<ApiResponse<CreditBureauCheck>> latest(
            @PathVariable Long id
    ) {

        Long orgId =
                currentUserUtil
                        .getCurrentOrganizationId();


        Optional<CreditBureauCheck> latest =
                creditBureauService.getLatest(
                        id,
                        orgId
                );


        if (latest.isEmpty()) {

            return ResponseEntity.ok(
                    ApiResponse.error(
                            "No credit bureau check on file "
                                    + "for this borrower"
                    )
            );
        }


        return ResponseEntity.ok(
                ApiResponse.ok(
                        latest.get()
                )
        );
    }


    // ============================================================
    // BORROWER OWNERSHIP CHECK
    // ============================================================
    //
    // Kept here because this controller may later expose
    // additional borrower-specific endpoints.
    //
    // The actual CreditBureauService also validates ownership,
    // so tenant isolation is enforced at both layers.
    // ============================================================

    private void assertOwnedByCallerOrg(
            Long borrowerId
    ) {

        Borrower borrower =
                borrowerRepository
                        .findById(borrowerId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Borrower not found: "
                                                + borrowerId
                                )
                        );


        Long callerOrgId =
                currentUserUtil
                        .getCurrentOrganizationId();


        if (borrower.getOrganization() == null ||
                borrower.getOrganization().getId() == null ||
                !borrower.getOrganization()
                        .getId()
                        .equals(callerOrgId)) {

            throw new SecurityException(
                    "Access denied"
            );
        }
    }
}
