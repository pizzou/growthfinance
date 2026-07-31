package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.Guarantor;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.repository.GuarantorRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/loans/{loanId}/guarantors")
@RequiredArgsConstructor
public class GuarantorController {

    private final GuarantorRepository guarantorRepo;
    private final LoanRepository      loanRepo;
    private final CurrentUserUtil     currentUserUtil;
    private final AuditService        auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Guarantor>>> list(@PathVariable Long loanId) {
        return ResponseEntity.ok(ApiResponse.ok(guarantorRepo.findByLoan_Id(loanId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Guarantor>> add(@PathVariable Long loanId, @RequestBody Map<String,Object> body) {
        Loan loan = loanRepo.findById(loanId).orElseThrow(() -> new RuntimeException("Loan not found"));
        var user = currentUserUtil.getCurrentUser();
        if (!loan.getOrganization().getId().equals(user.getOrganization().getId())) throw new RuntimeException("Access denied");

        Guarantor g = Guarantor.builder()
            .loan(loan).organization(loan.getOrganization())
            .fullName(str(body,"fullName")).nationalId(str(body,"nationalId")).phone(str(body,"phone"))
            .address(str(body,"address")).relationship(str(body,"relationship"))
            .employerName(str(body,"employerName"))
            .monthlyIncome(num(body,"monthlyIncome"))
            .guaranteedAmount(num(body,"guaranteedAmount"))
            .consentGiven(Boolean.TRUE.equals(body.get("consentGiven")))
            .documentUrl(str(body,"documentUrl"))
            .build();
        g = guarantorRepo.save(g);

        auditService.log(loan.getOrganization(), user, "GUARANTOR_ADDED", "LOAN", loanId.toString(),
            "Guarantor " + g.getFullName() + " added for " + (g.getGuaranteedAmount() != null ? g.getGuaranteedAmount() : 0));
        return ResponseEntity.ok(ApiResponse.ok("Guarantor added", g));
    }

    @DeleteMapping("/{guarantorId}")
    public ResponseEntity<ApiResponse<String>> remove(@PathVariable Long loanId, @PathVariable Long guarantorId) {
        Guarantor g = guarantorRepo.findById(guarantorId).orElseThrow(() -> new RuntimeException("Guarantor not found"));
        var user = currentUserUtil.getCurrentUser();
        if (!g.getOrganization().getId().equals(user.getOrganization().getId())) throw new RuntimeException("Access denied");
        guarantorRepo.delete(g);
        auditService.log(g.getOrganization(), user, "GUARANTOR_REMOVED", "LOAN", loanId.toString(),
            "Guarantor " + g.getFullName() + " removed");
        return ResponseEntity.ok(ApiResponse.ok("Guarantor removed"));
    }

    private String str(Map<String,Object> b, String k) { return b.get(k) != null ? b.get(k).toString() : null; }
    private Double num(Map<String,Object> b, String k) { return b.get(k) != null ? Double.valueOf(b.get(k).toString()) : null; }
}
