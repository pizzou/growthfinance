package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.LoanApprovalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;


@Service
@RequiredArgsConstructor
public class LoanApprovalService {

    private final LoanApprovalRepository approvalRepo;
    private final LoanService            loanService;
    private final AuditService           auditService;

    
    private List<String> requiredRolesFor(Loan loan) {
        Double orgMax = loan.getOrganization().getMaxLoanAmount();
        BigDecimal ratio = (orgMax != null && orgMax > 0) ? loan.getAmount().divide(new BigDecimal(orgMax), 2, RoundingMode.HALF_UP) : new BigDecimal(1.0);

        if (ratio.compareTo(new BigDecimal(0.20)) <= 0) return List.of("LOAN_OFFICER");
        if (ratio.compareTo(new BigDecimal(0.60)) <= 0) return List.of("LOAN_OFFICER", "MANAGER");
        return List.of("LOAN_OFFICER", "MANAGER", "ADMIN"); // ADMIN stands in for "credit committee" sign-off
    }

    @Transactional
    public List<LoanApproval> initiateChain(Loan loan) {
        // Idempotent — don't create a second chain if one already exists (e.g. loan re-submitted)
        List<LoanApproval> existing = approvalRepo.findByLoan_IdOrderByStepOrderAsc(loan.getId());
        if (!existing.isEmpty()) return existing;

        List<String> roles = requiredRolesFor(loan);
        int step = 1;
        for (String role : roles) {
            approvalRepo.save(LoanApproval.builder()
                .loan(loan).organization(loan.getOrganization())
                .stepOrder(step).requiredRole(role)
                .stepName(stepLabel(role, step, roles.size()))
                .status("PENDING")
                .build());
            step++;
        }
        return approvalRepo.findByLoan_IdOrderByStepOrderAsc(loan.getId());
    }

    private String stepLabel(String role, int step, int total) {
        return switch (role) {
            case "LOAN_OFFICER" -> "Loan Officer Review";
            case "MANAGER"      -> "Branch Manager Approval";
            case "ADMIN"        -> "Credit Committee Sign-off";
            default             -> role + " Approval (Step " + step + "/" + total + ")";
        };
    }

    public List<LoanApproval> getChain(Long loanId) {
        return approvalRepo.findByLoan_IdOrderByStepOrderAsc(loanId);
    }

   
    @Transactional
    public LoanApproval decide(Long loanId, User decider, String decision, String comments) {
        return decide(loanId, decider, decision, comments, null);
    }

    /** @param newInterestRate only used when this decision is the final APPROVED step in the
     *                         chain — passed straight through to LoanService.approveLoan(). */
    @Transactional
    public LoanApproval decide(Long loanId, User decider, String decision, String comments, Double newInterestRate) {
        Loan loan = loanService.getLoanForOrg(loanId, decider.getOrganization().getId());

        List<LoanApproval> chain = approvalRepo.findByLoan_IdOrderByStepOrderAsc(loanId);
        if (chain.isEmpty()) chain = initiateChain(loan);

        LoanApproval step = chain.stream()
            .filter(a -> "PENDING".equals(a.getStatus()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("This loan has no pending approval step — it may already be fully approved or rejected."));

        String deciderRole = decider.getRole() != null ? decider.getRole().getName() : null;
        boolean roleMatches = step.getRequiredRole().equals(deciderRole) || "ADMIN".equals(deciderRole);
        if (!roleMatches) {
            throw new RuntimeException("This step requires a " + step.getRequiredRole()
                + " — your role (" + deciderRole + ") can't decide it.");
        }

        if (loan.getLoanOfficer() != null && loan.getLoanOfficer().getId().equals(decider.getId())) {
            throw new RuntimeException("You created this loan application — someone else must review it (maker-checker separation).");
        }

        // Guard against the same person deciding two steps in this chain — a real
        // second person is required at every step, not just a different click.
        boolean alreadyDecidedByThisUser = chain.stream()
            .anyMatch(a -> a.getApprover() != null && a.getApprover().getId().equals(decider.getId()));
        if (alreadyDecidedByThisUser) {
            throw new RuntimeException("You've already decided a step on this loan's approval chain — a different person must decide this step.");
        }

        boolean approved = "APPROVED".equalsIgnoreCase(decision);
        step.setStatus(approved ? "APPROVED" : "REJECTED");
        step.setApprover(decider);
        step.setComments(comments);
        step.setDecidedAt(LocalDateTime.now());
        approvalRepo.save(step);

        auditService.log(loan.getOrganization(), decider,
            "LOAN_APPROVAL_STEP_" + step.getStatus(), "LOAN", loanId.toString(),
            step.getStepName() + " — " + step.getStatus() + (comments != null && !comments.isBlank() ? ": " + comments : ""));

        if (!approved) {
            loanService.rejectLoan(loanId, decider, comments != null ? comments : "Rejected at " + step.getStepName());
            return step;
        }

        boolean allApproved = approvalRepo.findByLoan_IdOrderByStepOrderAsc(loanId).stream()
            .allMatch(a -> "APPROVED".equals(a.getStatus()));
        if (allApproved) {
            loanService.approveLoan(loanId, decider, "Approved via " + chain.size() + "-step maker-checker chain", newInterestRate);
        }

        return step;
    }
}