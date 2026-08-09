package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanApproval;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanApprovalRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanApprovalService {

    private final LoanApprovalRepository approvalRepo;
    private final LoanService loanService;
    private final AuditService auditService;


    // ================================================================
    // DETERMINE REQUIRED APPROVAL ROLES
    // ================================================================

    /**
     * Determines which roles must approve a loan.
     *
     * IMPORTANT:
     *
     * The LOAN_OFFICER is the maker/creator in the normal workflow.
     * Therefore LOAN_OFFICER is deliberately NOT used as an approval
     * step here.
     *
     * Small loan:
     *
     *      Loan Officer creates
     *              ↓
     *          Manager approves
     *
     * Medium/large loan:
     *
     *      Loan Officer creates
     *              ↓
     *          Manager approves
     *              ↓
     *          Admin approves
     *
     * The actual thresholds are based on the organization's configured
     * maximum loan amount.
     */
    private List<String> requiredRolesFor(Loan loan) {

        if (loan == null) {
            throw new IllegalArgumentException(
                    "Loan is required to determine the approval chain."
            );
        }

        if (loan.getOrganization() == null) {
            throw new IllegalStateException(
                    "Loan organization is required to determine the approval chain."
            );
        }

        Double loanAmount = loan.getAmount();

        if (loanAmount == null || loanAmount <= 0) {
            throw new IllegalStateException(
                    "Loan amount must be greater than zero."
            );
        }

        Double organizationMaximum =
                loan.getOrganization().getMaxLoanAmount();

        /*
         * If the organization does not have a configured maximum,
         * use the conservative two-person approval chain.
         */
        if (organizationMaximum == null
                || organizationMaximum <= 0) {

            return List.of(
                    "MANAGER",
                    "ADMIN"
            );
        }

        double ratio =
                loanAmount / organizationMaximum;


        /*
         * SMALL LOAN
         *
         * Maker:
         *     Loan Officer
         *
         * Checker:
         *     Manager
         */
        if (ratio <= 0.20) {

            return List.of(
                    "MANAGER"
            );
        }


        /*
         * MEDIUM LOAN
         *
         * Maker:
         *     Loan Officer
         *
         * Checker 1:
         *     Manager
         *
         * Checker 2:
         *     Admin / Credit Committee
         */
        if (ratio <= 0.60) {

            return List.of(
                    "MANAGER",
                    "ADMIN"
            );
        }


        /*
         * LARGE LOAN
         *
         * Requires manager and senior approval.
         *
         * ADMIN currently represents the senior credit approval
         * role in this platform.
         */
        return List.of(
                "MANAGER",
                "ADMIN"
        );
    }


    // ================================================================
    // INITIATE APPROVAL CHAIN
    // ================================================================

    @Transactional
    public List<LoanApproval> initiateChain(
            Loan loan
    ) {

        if (loan == null) {
            throw new IllegalArgumentException(
                    "Loan is required."
            );
        }

        if (loan.getId() == null) {
            throw new IllegalStateException(
                    "Loan must be saved before an approval chain can be created."
            );
        }

        if (loan.getOrganization() == null) {
            throw new IllegalStateException(
                    "Loan organization is required."
            );
        }

        /*
         * Idempotency protection.
         *
         * If the chain already exists, never create duplicate
         * approval steps.
         */
        List<LoanApproval> existing =
                approvalRepo.findByLoan_IdOrderByStepOrderAsc(
                        loan.getId()
                );

        if (!existing.isEmpty()) {

            return existing;
        }

        List<String> roles =
                requiredRolesFor(loan);

        if (roles.isEmpty()) {

            throw new IllegalStateException(
                    "No approval roles are configured for this loan."
            );
        }

        int stepNumber = 1;

        for (String role : roles) {

            LoanApproval approval =
                    LoanApproval.builder()
                            .loan(loan)
                            .organization(
                                    loan.getOrganization()
                            )
                            .stepOrder(stepNumber)
                            .requiredRole(role)
                            .stepName(
                                    stepLabel(
                                            role,
                                            stepNumber,
                                            roles.size()
                                    )
                            )
                            .status("PENDING")
                            .build();

            approvalRepo.save(
                    approval
            );

            stepNumber++;
        }

        log.info(
                "Loan approval chain created. loanId={}, requiredRoles={}",
                loan.getId(),
                roles
        );

        return approvalRepo
                .findByLoan_IdOrderByStepOrderAsc(
                        loan.getId()
                );
    }


    // ================================================================
    // STEP LABEL
    // ================================================================

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


    // ================================================================
    // GET APPROVAL CHAIN
    // ================================================================

    @Transactional(readOnly = true)
    public List<LoanApproval> getChain(
            Long loanId
    ) {

        if (loanId == null) {
            throw new IllegalArgumentException(
                    "Loan ID is required."
            );
        }

        return approvalRepo
                .findByLoan_IdOrderByStepOrderAsc(
                        loanId
                );
    }


    // ================================================================
    // DECIDE
    // ================================================================

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


    // ================================================================
    // DECIDE WITH OPTIONAL INTEREST RATE
    // ================================================================

    @Transactional
    public LoanApproval decide(
            Long loanId,
            User decider,
            String decision,
            String comments,
            Double newInterestRate
    ) {

        // ------------------------------------------------------------
        // VALIDATE INPUT
        // ------------------------------------------------------------

        if (loanId == null) {
            throw new IllegalArgumentException(
                    "Loan ID is required."
            );
        }

        if (decider == null) {
            throw new IllegalStateException(
                    "Authenticated user is required."
            );
        }

        if (decider.getOrganization() == null
                || decider.getOrganization().getId() == null) {

            throw new IllegalStateException(
                    "Approver organization is required."
            );
        }

        if (decision == null
                || decision.isBlank()) {

            throw new IllegalArgumentException(
                    "Approval decision is required."
            );
        }

        String normalizedDecision =
                decision.trim().toUpperCase();

        if (!"APPROVED".equals(normalizedDecision)
                && !"REJECTED".equals(normalizedDecision)) {

            throw new IllegalArgumentException(
                    "Decision must be APPROVED or REJECTED."
            );
        }


        // ------------------------------------------------------------
        // LOAD LOAN WITH TENANT CHECK
        // ------------------------------------------------------------

        Loan loan =
                loanService.getLoanForOrg(
                        loanId,
                        decider.getOrganization().getId()
                );


        // ------------------------------------------------------------
        // LOAD APPROVAL CHAIN
        // ------------------------------------------------------------

        List<LoanApproval> chain =
                approvalRepo
                        .findByLoan_IdOrderByStepOrderAsc(
                                loanId
                        );

        /*
         * Defensive fallback.
         *
         * Normally the chain is created during loan creation.
         * If an older loan has no chain, initialize it here.
         */
        if (chain.isEmpty()) {

            chain =
                    initiateChain(
                            loan
                    );
        }


        // ------------------------------------------------------------
        // FIND NEXT PENDING STEP
        // ------------------------------------------------------------

        LoanApproval currentStep =
                chain.stream()
                        .filter(
                                approval ->
                                        "PENDING".equalsIgnoreCase(
                                                approval.getStatus()
                                        )
                        )
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "This loan has no pending approval step. "
                                                        + "It may already be approved or rejected."
                                        )
                        );


        // ------------------------------------------------------------
        // GET DECIDER ROLE
        // ------------------------------------------------------------

        String deciderRole =
                decider.getRole() != null
                        ? decider.getRole().getName()
                        : null;


        // ------------------------------------------------------------
        // ROLE AUTHORIZATION
        // ------------------------------------------------------------

        boolean roleMatches =
                currentStep
                        .getRequiredRole()
                        .equalsIgnoreCase(
                                deciderRole
                        )
                        || "ADMIN".equalsIgnoreCase(
                                deciderRole
                        );

        if (!roleMatches) {

            throw new IllegalStateException(
                    "This approval step requires "
                            + currentStep.getRequiredRole()
                            + ". Your role is "
                            + (
                            deciderRole != null
                                    ? deciderRole
                                    : "UNASSIGNED"
                    )
                            + "."
            );
        }


        // ------------------------------------------------------------
        // MAKER-CHECKER SEPARATION
        // ------------------------------------------------------------

        /*
         * The person who created the loan cannot approve the loan.
         *
         * In the current data model, loan.getLoanOfficer() represents
         * the officer responsible for / creator of the application.
         */
        if (loan.getLoanOfficer() != null
                && loan.getLoanOfficer().getId() != null
                && loan.getLoanOfficer()
                .getId()
                .equals(decider.getId())) {

            throw new IllegalStateException(
                    "You created this loan application. "
                            + "Another authorized user must review it "
                            + "under the maker-checker policy."
            );
        }


        // ------------------------------------------------------------
        // SAME USER CANNOT DECIDE MULTIPLE STEPS
        // ------------------------------------------------------------

        boolean alreadyDecidedByThisUser =
                chain.stream()
                        .anyMatch(
                                approval ->
                                        approval.getApprover() != null
                                                && approval.getApprover().getId() != null
                                                && approval.getApprover()
                                                .getId()
                                                .equals(
                                                        decider.getId()
                                                )
                        );

        if (alreadyDecidedByThisUser) {

            throw new IllegalStateException(
                    "You have already decided another step on this loan. "
                            + "A different authorized user must decide this step."
            );
        }


        // ------------------------------------------------------------
        // RECORD DECISION
        // ------------------------------------------------------------

        boolean approved =
                "APPROVED".equals(
                        normalizedDecision
                );

        currentStep.setStatus(
                approved
                        ? "APPROVED"
                        : "REJECTED"
        );

        currentStep.setApprover(
                decider
        );

        currentStep.setComments(
                comments != null
                        && !comments.isBlank()
                        ? comments.trim()
                        : null
        );

        currentStep.setDecidedAt(
                LocalDateTime.now()
        );

        approvalRepo.save(
                currentStep
        );


        // ------------------------------------------------------------
        // AUDIT
        // ------------------------------------------------------------

        auditService.log(
                loan.getOrganization(),
                decider,
                "LOAN_APPROVAL_STEP_" + currentStep.getStatus(),
                "LOAN",
                loanId.toString(),
                currentStep.getStepName()
                        + " — "
                        + currentStep.getStatus()
                        + (
                        comments != null
                                && !comments.isBlank()
                                ? ": "
                                + comments.trim()
                                : ""
                )
        );


        // ------------------------------------------------------------
        // REJECTION
        // ------------------------------------------------------------

        if (!approved) {

            String rejectionReason =
                    comments != null
                            && !comments.isBlank()
                            ? comments.trim()
                            : "Rejected at "
                            + currentStep.getStepName();

            loanService.rejectLoan(
                    loanId,
                    decider,
                    rejectionReason
            );

            log.info(
                    "Loan rejected. loanId={}, step={}, approverId={}",
                    loanId,
                    currentStep.getStepName(),
                    decider.getId()
            );

            return currentStep;
        }


        // ------------------------------------------------------------
        // RELOAD CHAIN AFTER CURRENT DECISION
        // ------------------------------------------------------------

        List<LoanApproval> updatedChain =
                approvalRepo
                        .findByLoan_IdOrderByStepOrderAsc(
                                loanId
                        );


        // ------------------------------------------------------------
        // CHECK WHETHER EVERY STEP IS APPROVED
        // ------------------------------------------------------------

        boolean allApproved =
                !updatedChain.isEmpty()
                        && updatedChain.stream()
                        .allMatch(
                                approval ->
                                        "APPROVED".equalsIgnoreCase(
                                                approval.getStatus()
                                        )
                        );


        // ------------------------------------------------------------
        // FINAL APPROVAL
        // ------------------------------------------------------------

        if (allApproved) {

            loanService.approveLoan(
                    loanId,
                    decider,
                    "Approved via "
                            + updatedChain.size()
                            + "-step maker-checker chain",
                    newInterestRate
            );

            log.info(
                    "Loan fully approved. loanId={}, finalApproverId={}, approvalSteps={}",
                    loanId,
                    decider.getId(),
                    updatedChain.size()
            );

        } else {

            log.info(
                    "Approval step completed. "
                            + "loanId={}, completedStep={}, waitingForNextStep=true",
                    loanId,
                    currentStep.getStepName()
            );
        }

        return currentStep;
    }
}