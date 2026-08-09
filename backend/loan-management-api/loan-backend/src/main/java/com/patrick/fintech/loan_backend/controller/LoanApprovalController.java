
package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.LoanApproval;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.service.LoanApprovalService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/loans/{loanId}/approval-chain")
@RequiredArgsConstructor
public class LoanApprovalController {

    private final LoanApprovalService approvalService;
    private final CurrentUserUtil currentUserUtil;

    /**
     * Returns the approval chain for the authenticated user's organization.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
    public ResponseEntity<ApiResponse<List<LoanApproval>>> getChain(
            @PathVariable Long loanId
    ) {

        User user =
                currentUserUtil.getCurrentUser();

        Long organizationId =
                user.getOrganization() != null
                        ? user.getOrganization().getId()
                        : null;

        return ResponseEntity.ok(
                ApiResponse.ok(
                        approvalService.getChainForOrganization(
                                loanId,
                                organizationId
                        )
                )
        );
    }

    /**
     * Explicit approval-chain decision endpoint.
     *
     * This endpoint is kept for compatibility with the dashboard.
     */
    @PostMapping("/decide")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<LoanApproval>> decide(
            @PathVariable Long loanId,
            @RequestBody(required = false)
            Map<String, String> body
    ) {

        User user =
                currentUserUtil.getCurrentUser();

        String decision =
                body != null
                        ? body.get("decision")
                        : null;

        String comments =
                body != null
                        ? body.get("comments")
                        : null;

        LoanApproval result =
                approvalService.decide(
                        loanId,
                        user,
                        decision,
                        comments
                );

        return ResponseEntity
                .ok(
                        ApiResponse.ok(
                                "Decision recorded",
                                result
                        )
                );
    }

    /**
     * Dedicated approval endpoint.
     *
     * This gives the dashboard a clean endpoint for approving.
     */
    @PostMapping("/approve")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<LoanApproval>> approve(
            @PathVariable Long loanId,
            @RequestBody(required = false)
            Map<String, String> body
    ) {

        User user =
                currentUserUtil.getCurrentUser();

        String comments =
                body != null
                        ? firstNonBlank(
                        body.get("comments"),
                        body.get("notes")
                )
                        : null;

        Double newInterestRate =
                parseInterestRate(body);

        LoanApproval result =
                approvalService.decide(
                        loanId,
                        user,
                        "APPROVED",
                        comments,
                        newInterestRate
                );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Loan approval decision recorded",
                        result
                )
        );
    }

    /**
     * Dedicated rejection endpoint.
     */
    @PostMapping("/reject")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<LoanApproval>> reject(
            @PathVariable Long loanId,
            @RequestBody(required = false)
            Map<String, String> body
    ) {

        User user =
                currentUserUtil.getCurrentUser();

        String reason =
                body != null
                        ? firstNonBlank(
                        body.get("reason"),
                        body.get("comments"),
                        body.get("notes")
                )
                        : null;

        if (reason == null) {
            reason =
                    "Rejected by authorized approver.";
        }

        LoanApproval result =
                approvalService.decide(
                        loanId,
                        user,
                        "REJECTED",
                        reason
                );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Loan rejection recorded",
                        result
                )
        );
    }

    private Double parseInterestRate(
            Map<String, String> body
    ) {

        if (body == null) {
            return null;
        }

        String raw =
                body.get("interestRate");

        if (raw == null
                || raw.isBlank()) {

            return null;
        }

        try {

            return Double.valueOf(
                    raw.trim()
            );

        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                    "interestRate must be a valid number."
            );
        }
    }

    private String firstNonBlank(
            String... values
    ) {

        if (values == null) {
            return null;
        }

        for (String value : values) {

            if (value != null
                    && !value.isBlank()) {

                return value.trim();
            }
        }

        return null;
    }
}
