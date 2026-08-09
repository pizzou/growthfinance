package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanApproval;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanApprovalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LoanApprovalService {

    private final LoanApprovalRepository approvalRepo;
    private final LoanService loanService;
    private final AuditService auditService;


    // ============================================================
    // APPROVAL POLICY
    // ============================================================

    /**
     * Determines the approval chain according to the loan exposure
     * relative to the organization's configured maximum loan amount.
     *
     * Small loans:
     *     Loan Officer
     *
     * Medium loans:
     *     Loan Officer -> Manager
     *
     * Large loans:
     *     Loan Officer -> Manager -> Admin
     */
    private List<String> requiredRolesFor(Loan loan) {

        Double orgMax = loan.getOrganization().getMaxLoanAmount();

        double amount = loan.getAmount() != null
                ? loan.getAmount()
                : 0.0;

        double ratio;

        if (orgMax != null && orgMax > 0.0) {
            ratio = amount / orgMax;
        } else {
            ratio = 1.0;
        }

        if (ratio <= 0.20) {
            return List.of("LOAN_OFFICER");
        }

        if (ratio <= 0.60) {
            return List.of(
                    "LOAN_OFFICER",
                    "MANAGER"
            );
        }

        return List.of(
                "LOAN_OFFICER",
                "MANAGER",
                "ADMIN"
        );
    }


    // ============================================================
    // INITIATE APPROVAL CHAIN
    // ============================================================

    @Transactional
    public List<LoanApproval> initiateChain(Loan loan) {

        if (loan == null || loan.getId() == null) {
            throw new IllegalArgumentException(
                    "Loan is required to initiate approval chain"
            );
        }

        List<LoanApproval> existing =
                approvalRepo.findByLoan_IdOrderByStepOrderAsc(
                        loan.getId()
                );

        // Idempotent.
        if (!existing.isEmpty()) {
            return existing;
        }

        List<String> roles = requiredRolesFor(loan);

        int step = 1;

        for (String role : roles) {

            approvalRepo.save(
                    LoanApproval.builder()
                            .loan(loan)
                            .organization(loan.getOrganization())
                            .stepOrder(step)
                            .requiredRole(role)
                            .stepName(
                                    stepLabel(
                                            role,
                                            step,
                                            roles.size()
                                    )
                            )
                            .status("PENDING")
                            .build()
            );

            step++;
        }

        return approvalRepo.findByLoan_IdOrderByStepOrderAsc(
                loan.getId()
        );
    }


    // ============================================================
    // STEP LABEL
    // ============================================================

    private String stepLabel(
            String role,
            int step,
            int total
    ) {

        return switch (role) {

            case "LOAN_OFFICER" ->
                    "Loan Officer Review";

            case "MANAGER" ->
                    "Branch Manager Approval";

            case "ADMIN" ->
                    "Credit Committee Sign-off";

            default ->
                    role
                            + " Approval (Step "
                            + step
                            + "/"
                            + total
                            + ")";
        };
    }


    // ============================================================
    // GET APPROVAL CHAIN
    // ============================================================

    @Transactional(readOnly = true)
    public List<LoanApproval> getChain(Long loanId) {

        if (loanId == null) {
            throw new IllegalArgumentException(
                    "Loan ID is required"
            );
        }

        return approvalRepo.findByLoan_IdOrderByStepOrderAsc(
                loanId
        );
    }


    // ============================================================
    // DECIDE - NORMAL APPROVAL
    // ============================================================

    @Transactional
    public LoanApproval decide(
            Long loanId,
            User decider,
            String decision,
            String comments
    ) {

        return decide(
                loanId,
                decider,
                decision,
                comments,
                null
        );
    }


    // ============================================================
    // DECIDE - WITH OPTIONAL INTEREST RATE CHANGE
    // ============================================================

    /**
     * Production maker-checker workflow.
     *
     * Rules:
     *
     * 1. Decider must belong to same organization.
     * 2. Loan must belong to same organization.
     * 3. There must be a pending approval step.
     * 4. User must have the role required by that step.
     * 5. Loan creator can NEVER approve their own loan.
     * 6. A user who already approved/rejected another step cannot
     *    approve another step on the same loan.
     * 7. Rejection immediately rejects the loan.
     * 8. Final approval only happens when every step is approved.
     */
    @Transactional
    public LoanApproval decide(
            Long loanId,
            User decider,
            String decision,
            String comments,
            Double newInterestRate
    ) {

        // --------------------------------------------------------
        // BASIC VALIDATION
        // --------------------------------------------------------

        if (loanId == null) {
            throw new IllegalArgumentException(
                    "Loan ID is required"
            );
        }

        if (decider == null || decider.getId() == null) {
            throw new IllegalArgumentException(
                    "Authenticated user is required"
            );
        }

        if (decision == null || decision.isBlank()) {
            throw new IllegalArgumentException(
                    "Decision is required"
            );
        }

        String normalizedDecision =
                decision.trim().toUpperCase();

        if (!"APPROVED".equals(normalizedDecision)
                && !"REJECTED".equals(normalizedDecision)) {

            throw new IllegalArgumentException(
                    "Decision must be APPROVED or REJECTED"
            );
        }


        // --------------------------------------------------------
        // LOAD LOAN WITH ORGANIZATION ACCESS CHECK
        // --------------------------------------------------------

        Long organizationId =
                decider.getOrganization() != null
                        ? decider.getOrganization().getId()
                        : null;

        if (organizationId == null) {
            throw new IllegalArgumentException(
                    "Authenticated user has no organization"
            );
        }

        Loan loan =
                loanService.getLoanForOrg(
                        loanId,
                        organizationId
                );


        // --------------------------------------------------------
        // LOAD APPROVAL CHAIN
        // --------------------------------------------------------

        List<LoanApproval> chain =
                approvalRepo.findByLoan_IdOrderByStepOrderAsc(
                        loanId
                );

        if (chain.isEmpty()) {

            chain = initiateChain(loan);
        }


        // --------------------------------------------------------
        // FIND NEXT PENDING STEP
        // --------------------------------------------------------

        LoanApproval step =
                chain.stream()
                        .filter(a ->
                                "PENDING".equalsIgnoreCase(
                                        a.getStatus()
                                )
                        )
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "This loan has no pending approval step. "
                                                + "It may already be fully approved or rejected."
                                )
                        );


        // --------------------------------------------------------
        // DETERMINE USER ROLE
        // --------------------------------------------------------

        String deciderRole =
                decider.getRole() != null
                        ? decider.getRole().getName()
                        : null;

        if (deciderRole == null
                || deciderRole.isBlank()) {

            throw new IllegalArgumentException(
                    "Authenticated user has no assigned role"
            );
        }

        deciderRole =
                deciderRole.trim().toUpperCase();


        String requiredRole =
                step.getRequiredRole() != null
                        ? step.getRequiredRole()
                                .trim()
                                .toUpperCase()
                        : null;


        // --------------------------------------------------------
        // ROLE AUTHORIZATION
        // --------------------------------------------------------

        boolean roleMatches =
                requiredRole != null
                        && requiredRole.equals(deciderRole);

        if (!roleMatches) {

            throw new IllegalStateException(
                    "This approval step requires "
                            + requiredRole
                            + ". Your role is "
                            + deciderRole
                            + "."
            );
        }


        // --------------------------------------------------------
        // MAKER-CHECKER
        // --------------------------------------------------------

        /**
         * IMPORTANT:
         *
         * We deliberately use createdBy.
         *
         * loanOfficer represents the current responsible officer.
         * createdBy represents the original maker.
         */
        User creator = loan.getCreatedBy();

        if (creator != null
                && creator.getId() != null
                && creator.getId().equals(decider.getId())) {

            throw new IllegalStateException(
                    "You created this loan application. "
                            + "Another authorized user must review it "
                            + "under the maker-checker policy."
            );
        }


        // --------------------------------------------------------
        // SAME USER CANNOT APPROVE MULTIPLE STEPS
        // --------------------------------------------------------

        boolean alreadyDecidedByThisUser =
                chain.stream()
                        .anyMatch(a ->
                                a.getApprover() != null
                                        && a.getApprover().getId() != null
                                        && a.getApprover()
                                                .getId()
                                                .equals(
                                                        decider.getId()
                                                )
                        );

        if (alreadyDecidedByThisUser) {

            throw new IllegalStateException(
                    "You have already decided a step on this loan. "
                            + "A different authorized user must decide this step."
            );
        }


        // --------------------------------------------------------
        // RECORD DECISION
        // --------------------------------------------------------

        boolean approved =
                "APPROVED".equals(normalizedDecision);

        step.setStatus(
                approved
                        ? "APPROVED"
                        : "REJECTED"
        );

        step.setApprover(decider);

        step.setComments(
                comments != null
                        ? comments.trim()
                        : null
        );

        step.setDecidedAt(
                LocalDateTime.now()
        );

        approvalRepo.save(step);


        // --------------------------------------------------------
        // AUDIT
        // --------------------------------------------------------

        auditService.log(
                loan.getOrganization(),
                decider,
                "LOAN_APPROVAL_STEP_" + step.getStatus(),
                "LOAN",
                loanId.toString(),
                step.getStepName()
                        + " — "
                        + step.getStatus()
                        + (
                        comments != null
                                && !comments.isBlank()
                                ? ": " + comments.trim()
                                : ""
                )
        );


        // --------------------------------------------------------
        // REJECTION
        // --------------------------------------------------------

        if (!approved) {

            loanService.rejectLoan(
                    loanId,
                    decider,
                    comments != null
                            && !comments.isBlank()
                            ? comments.trim()
                            : "Rejected at "
                            + step.getStepName()
            );

            return step;
        }


        // --------------------------------------------------------
        // CHECK WHETHER ALL STEPS ARE APPROVED
        // --------------------------------------------------------

        List<LoanApproval> updatedChain =
                approvalRepo.findByLoan_IdOrderByStepOrderAsc(
                        loanId
                );

        boolean allApproved =
                !updatedChain.isEmpty()
                        && updatedChain.stream()
                        .allMatch(a ->
                                "APPROVED".equalsIgnoreCase(
                                        a.getStatus()
                                )
                        );


        // --------------------------------------------------------
        // FINAL APPROVAL
        // --------------------------------------------------------

        if (allApproved) {

            loanService.approveLoan(
                    loanId,
                    decider,
                    "Approved via "
                            + updatedChain.size()
                            + "-step maker-checker chain",
                    newInterestRate
            );
        }

        return step;
    }
}