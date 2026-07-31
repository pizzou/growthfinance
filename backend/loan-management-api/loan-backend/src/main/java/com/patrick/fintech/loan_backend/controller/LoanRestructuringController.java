package com.patrick.fintech.loan_backend.controller;
import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.service.LoanRestructuringService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/loans/{loanId}") @RequiredArgsConstructor
public class LoanRestructuringController {
    private final LoanRestructuringService svc;
    private final CurrentUserUtil currentUserUtil;
    @PostMapping("/restructure") @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Loan>> restructure(@PathVariable Long loanId,@RequestBody Map<String,Object> body) {
        var u=currentUserUtil.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok("Loan restructured",svc.restructure(loanId,u.getOrganization().getId(),u,
            Integer.parseInt(body.get("newDurationMonths").toString()),
            body.containsKey("newInterestRate")?Double.parseDouble(body.get("newInterestRate").toString()):null,
            body.getOrDefault("reason","Borrower request").toString())));
    }
    @PostMapping("/write-off") @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Loan>> writeOff(@PathVariable Long loanId,@RequestBody Map<String,String> body) {
        var u=currentUserUtil.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok("Loan written off",svc.writeOff(loanId,u.getOrganization().getId(),u,body.getOrDefault("reason","Uncollectible"))));
    }
    @PostMapping("/moratorium") @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Loan>> moratorium(@PathVariable Long loanId,@RequestBody Map<String,Object> body) {
        var u=currentUserUtil.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok("Moratorium granted",svc.grantMoratorium(loanId,u.getOrganization().getId(),u,
            Integer.parseInt(body.get("pauseMonths").toString()),body.getOrDefault("reason","Payment holiday").toString())));
    }
}
