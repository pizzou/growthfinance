package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.LoanApproval;
import com.patrick.fintech.loan_backend.service.LoanApprovalService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/loans/{loanId}/approval-chain")
@RequiredArgsConstructor
public class LoanApprovalController {

    private final LoanApprovalService approvalService;
    private final CurrentUserUtil     currentUserUtil;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LoanApproval>>> getChain(@PathVariable Long loanId) {
        return ResponseEntity.ok(ApiResponse.ok(approvalService.getChain(loanId)));
    }

    @PostMapping("/decide")
    public ResponseEntity<ApiResponse<LoanApproval>> decide(
            @PathVariable Long loanId, @RequestBody Map<String,String> body) {
        String decision = body.get("decision"); // "APPROVED" or "REJECTED"
        String comments = body.get("comments");
        LoanApproval result = approvalService.decide(loanId, currentUserUtil.getCurrentUser(), decision, comments);
        return ResponseEntity.ok(ApiResponse.ok("Decision recorded", result));
    }
}
