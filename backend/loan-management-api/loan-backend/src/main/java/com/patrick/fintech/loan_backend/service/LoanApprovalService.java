
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanApproval;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanApprovalRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LoanApprovalService {

    private final LoanApprovalRepository approvalRepo;
    private final LoanService loanService;
    private final AuditService auditService;


    // ============================================================
    // ROLE NORMALIZATION
    // ============================================================

    private String normalizeRole(String role) {

        if (role == null) {
            return null;
        }

        String normalized = role.trim().toUpperCase();

        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring(5);
        }

        return normalized;
    }


    private String getUserRole(User user) {

        if (user == null || user.getRole() == null) {
            return null;
        }

        return normalizeRole(user.getRole().getName());
    }


    // ============================================================
    // REQUIRED APPROVAL ROLES
    // ============================================================

    /**
     * Determines the approval levels required by loan size.
     *
     * Important:
     *
     * The loan creator is NEVER allowed to approve their own loan.
     *
     * Therefore, the creator's role is removed from the required
     * approval chain.
     */
    private List<String> requiredRolesFor(Loan loan) {

        if (loan == null) {
            throw new IllegalArgumentException("Loan is required.");
        }

        if (loan.getOrganization() == null) {
            throw new IllegalStateException(
                    "Loan has no organization. Cannot determine approval policy."
            );
        }

        Double orgMaxLegacy = loan.getOrganization().getMaxLoanAmount();

        double orgMax = orgMaxLegacy != null
                ? orgMaxLegacy
                : 0.0;

        double amount = loan.getAmount() != null
                ? loan.getAmount()
                : 0.0;

        double ratio;

        if (orgMax > 0.0) {
            ratio = amount / orgMax;
        } else {
            ratio = 1.0;
        }

        List<String> roles = new ArrayList<>();

        if (ratio <= 0.20) {

            roles.add("LOAN_OFFICER");

        } else if (ratio <= 0.60) {

            roles.add("LOAN_OFFICER");
            roles.add("MANAGER");

        } else {

            roles.add("LOAN_OFFICER");
            roles.add("MANAGER");
            roles.add("ADMIN");
        }

        /*
         * Remove the creator's role from the chain.
         *
         * This is the important maker-checker rule.
         */
        User creator = loan.getCreatedBy();

        if (creator != null) {

            String creatorRole = getUserRole(creator);

            if (creatorRole != null) {

                roles.removeIf(
                        role -> creatorRole.equals(normalizeRole(role))
                );
            }
        }

        /*
         * Backward compatibility for older loans where createdBy
         * was not stored.
         *
         * loanOfficer is used as the historical creator.
         */
        if (creator == null && loan.getLoanOfficer() != null) {

            String creatorRole = getUserRole(loan.getLoanOfficer());

            if (creatorRole != null) {

                roles.removeIf(
                        role -> creatorRole.equals(normalizeRole(role))
                );
            }
        }

        /*
         * A valid loan must have at least one independent reviewer.
         *
         * If the normal policy becomes empty because the creator was
         * the only required role, require MANAGER approval.
         */
        if (roles.isEmpty()) {

            String creatorRole = creator != null
                    ? getUserRole(creator)
                    : loan.getLoanOfficer() != null
                        ? getUserRole(loan.getLoanOfficer())
                        : null;

            if (!"MANAGER".equals(creatorRole)) {

                roles.add("MANAGER");

            } else {

                roles.add("ADMIN");
            }
        }

        return roles;
    }


    // ============================================================
    // INITIATE APPROVAL CHAIN
    // ============================================================

    @Transactional
    public List<LoanApproval> initiateChain(Loan loan) {

        if (loan == null || loan.getId() == null) {
            throw new IllegalArgumentException(
                    "A persisted loan is required to initiate approval."
            );
        }

        List<LoanApproval> existing =
                approvalRepo.findByLoan_IdOrderByStepOrderAsc(loan.getId());

        /*
         * Existing chains are never deleted.
         *
         * If the chain contains historical decisions, preserve them.
         *
         * If it contains only untouched PENDING steps, repair it so
         * the maker-checker policy can be applied correctly.
         */
        if (!existing.isEmpty()) {

            boolean hasDecision = existing.stream()
                    .anyMatch(a ->
                            a.getStatus() != null
                                    && !"PENDING".equalsIgnoreCase(a.getStatus())
                    );

            if (hasDecision) {
                return existing;
            }

            return repairPendingChain(loan, existing);
        }


        List<String> roles = requiredRolesFor(loan);

        int stepOrder = 1;

        for (String role : roles) {

            LoanApproval approval = LoanApproval.builder()
                    .loan(loan)
                    .organization(loan.getOrganization())
                    .stepOrder(stepOrder)
                    .requiredRole(normalizeRole(role))
                    .stepName(
                            stepLabel(
                                    normalizeRole(role),
                                    stepOrder,
                                    roles.size()
                            )
                    )
                    .status("PENDING")
                    .build();

            approvalRepo.save(approval);

            stepOrder++;
        }

        return approvalRepo
                .findByLoan_IdOrderByStepOrderAsc(loan.getId());
    }


    // ============================================================
    // REPAIR LEGACY PENDING CHAIN
    // ============================================================

    /**
     * Repairs an old chain without destroying its audit records.
     *
     * Example:
     *
     * OLD:
     *
     * LOAN_OFFICER PENDING
     * MANAGER      PENDING
     * ADMIN        PENDING
     *
     * If Loan Officer created the loan:
     *
     * NEW:
     *
     * LOAN_OFFICER SKIPPED
     * MANAGER      PENDING
     * ADMIN        PENDING
     */
    private List<LoanApproval> repairPendingChain(
            Loan loan,
            List<LoanApproval> existing
    ) {

        User creator = loan.getCreatedBy();

        String creatorRole = null;

        if (creator != null) {

            creatorRole = getUserRole(creator);

        } else if (loan.getLoanOfficer() != null) {

            /*
             * Legacy compatibility.
             */
            creatorRole = getUserRole(loan.getLoanOfficer());
        }


        if (creatorRole == null) {
            return existing;
        }


        boolean changed = false;


        for (LoanApproval approval : existing) {

            if (!"PENDING".equalsIgnoreCase(approval.getStatus())) {
                continue;
            }

            String requiredRole =
                    normalizeRole(approval.getRequiredRole());

            /*
             * Only skip the creator's role.
             *
             * We never touch an already decided step.
             */
            if (creatorRole.equals(requiredRole)) {

                approval.setStatus("SKIPPED");

                approval.setComments(
                        "Skipped automatically because the loan creator "
                                + "cannot approve their own loan under the "
                                + "maker-checker policy."
                );

                approval.setDecidedAt(LocalDateTime.now());

                approvalRepo.save(approval);

                changed = true;

                auditService.log(
                        loan.getOrganization(),
                        creator,
                        "LOAN_APPROVAL_STEP_SKIPPED",
                        "LOAN",
                        loan.getId().toString(),
                        approval.getStepName()
                                + " skipped because the loan creator "
                                + "cannot approve their own loan.",
                        null,
                        null,
                        "Loan Approval"
                );
            }
        }


        if (changed) {

            return approvalRepo
                    .findByLoan_IdOrderByStepOrderAsc(loan.getId());
        }

        return existing;
    }


    // ============================================================
    // GET APPROVAL CHAIN
    // ============================================================

    @Transactional(readOnly = true)
    public List<LoanApproval> getChain(Long loanId) {

        if (loanId == null) {
            throw new IllegalArgumentException("Loan ID is required.");
        }

        return approvalRepo
                .findByLoan_IdOrderByStepOrderAsc(loanId);
    }


    // ============================================================
    // DECIDE
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
    // DECIDE WITH INTEREST RATE
    // ============================================================

    @Transactional
    public LoanApproval decide(
            Long loanId,
            User decider,
            String decision,
            String comments,
            Double newInterestRate
    ) {

        if (loanId == null) {
            throw new IllegalArgumentException("Loan ID is required.");
        }

        if (decider == null) {
            throw new IllegalStateException(
                    "Authenticated user is required."
            );
        }

        if (decision == null || decision.isBlank()) {
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


        if (decider.getOrganization() == null
                || decider.getOrganization().getId() == null) {

            throw new IllegalStateException(
                    "Authenticated user is not associated with an organization."
            );
        }


        Long organizationId =
                decider.getOrganization().getId();


        Loan loan =
                loanService.getLoanForOrg(
                        loanId,
                        organizationId
                );


        /*
         * Get the existing chain.
         */
        List<LoanApproval> chain =
                approvalRepo.findByLoan_IdOrderByStepOrderAsc(
                        loanId
                );


        /*
         * If no chain exists, create it.
         */
        if (chain.isEmpty()) {

            chain = initiateChain(loan);
        }


        /*
         * Repair an untouched legacy chain before finding the
         * next pending step.
         */
        boolean hasDecision = chain.stream()
                .anyMatch(a ->
                        a.getStatus() != null
                                && !"PENDING".equalsIgnoreCase(a.getStatus())
                );


        if (!hasDecision) {

            chain = repairPendingChain(
                    loan,
                    chain
            );
        }


        /*
         * Find the next pending step.
         */
        LoanApproval step = chain.stream()
                .filter(a ->
                        "PENDING".equalsIgnoreCase(a.getStatus())
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "This loan has no pending approval step. "
                                        + "It may already be fully approved, "
                                        + "rejected, or awaiting another workflow."
                        )
                );


        String deciderRole =
                getUserRole(decider);


        if (deciderRole == null) {

            throw new IllegalStateException(
                    "Your account does not have a valid role."
            );
        }


        String requiredRole =
                normalizeRole(step.getRequiredRole());


        /*
         * ADMIN is the escalation authority.
         *
         * Manager cannot approve a Loan Officer step.
         *
         * Loan Officer cannot approve a Manager step.
         */
        boolean roleMatches =
                requiredRole.equals(deciderRole)
                        || "ADMIN".equals(deciderRole);


        if (!roleMatches) {

            throw new IllegalStateException(
                    "This approval step requires "
                            + requiredRole
                            + ". Your role is "
                            + deciderRole
                            + "."
            );
        }


        // ========================================================
        // MAKER-CHECKER
        // ========================================================

        User creator =
                loan.getCreatedBy();


        /*
         * Backward compatibility.
         *
         * Old loans may not have createdBy populated.
         */
        if (creator == null) {
            creator = loan.getLoanOfficer();
        }


        if (creator != null
                && creator.getId() != null
                && creator.getId().equals(decider.getId())) {

            throw new IllegalStateException(
                    "You created this loan application. "
                            + "Another authorized user must review it "
                            + "under the maker-checker policy."
            );
        }


        /*
         * A user cannot approve multiple steps of the same loan.
         *
         * This prevents one administrator from effectively approving
         * every stage alone.
         */
        boolean alreadyDecidedByThisUser =
                chain.stream()
                        .filter(a ->
                                !"PENDING".equalsIgnoreCase(
                                        a.getStatus()
                                ))
                        .anyMatch(a ->
                                a.getApprover() != null
                                        && a.getApprover().getId() != null
                                        && a.getApprover()
                                            .getId()
                                            .equals(decider.getId())
                        );


        if (alreadyDecidedByThisUser) {

            throw new IllegalStateException(
                    "You have already decided another step "
                            + "for this loan. A different authorized "
                            + "user must complete the next step."
            );
        }


        // ========================================================
        // RECORD DECISION
        // ========================================================

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


        // ========================================================
        // AUDIT
        // ========================================================

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
                        ),
                null,
                null,
                "Loan Approval"
        );


        // ========================================================
        // REJECTION
        // ========================================================

        if (!approved) {

            loanService.rejectLoan(
                    loanId,
                    decider,
                    comments != null && !comments.isBlank()
                            ? comments.trim()
                            : "Rejected at "
                                    + step.getStepName()
            );

            return step;
        }


        // ========================================================
        // CHECK WHETHER ALL REQUIRED STEPS ARE COMPLETE
        // ========================================================

        List<LoanApproval> updatedChain =
                approvalRepo.findByLoan_IdOrderByStepOrderAsc(
                        loanId
                );


        boolean hasPending =
                updatedChain.stream()
                        .anyMatch(a ->
                                "PENDING".equalsIgnoreCase(
                                        a.getStatus()
                                )
                        );


        boolean hasRejected =
                updatedChain.stream()
                        .anyMatch(a ->
                                "REJECTED".equalsIgnoreCase(
                                        a.getStatus()
                                )
                        );


        boolean allApproved =
                !hasPending
                        && !hasRejected
                        && updatedChain.stream()
                            .filter(a ->
                                    !"SKIPPED".equalsIgnoreCase(
                                            a.getStatus()
                                    )
                            )
                            .allMatch(a ->
                                    "APPROVED".equalsIgnoreCase(
                                            a.getStatus()
                                    )
                            );


        // ========================================================
        // FINAL APPROVAL
        // ========================================================

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


    // ============================================================
    // STEP LABEL
    // ============================================================

    private String stepLabel(
            String role,
            int step,
            int total
    ) {

        return switch (normalizeRole(role)) {

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
}
