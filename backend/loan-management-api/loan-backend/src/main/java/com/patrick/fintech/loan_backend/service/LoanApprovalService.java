
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanApproval;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanApprovalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanApprovalService {

    private static final String ROLE_LOAN_OFFICER = "LOAN_OFFICER";
    private static final String ROLE_MANAGER = "MANAGER";
    private static final String ROLE_ADMIN = "ADMIN";

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    /**
     * Normal loans:
     *
     *     Loan Officer creates
     *             |
     *             v
     *         Manager
     *
     * Higher exposure:
     *
     *     Loan Officer creates
     *             |
     *             v
     *         Manager
     *             |
     *             v
     *          Admin
     *
     * The loan officer is the maker, NOT an approval step.
     */
    private static final BigDecimal MANAGER_THRESHOLD =
            new BigDecimal("0.60");

    private final LoanApprovalRepository approvalRepo;
    private final LoanService loanService;
    private final AuditService auditService;

    // ============================================================
    // PUBLIC API
    // ============================================================

    /**
     * Creates the maker-checker approval chain.
     *
     * The creator/loan officer is deliberately NOT inserted into
     * the approval chain.
     */
    @Transactional
    public List<LoanApproval> initiateChain(Loan loan) {

        validateLoan(loan);

        List<LoanApproval> existing =
                approvalRepo.findByLoan_IdOrderByStepOrderAsc(loan.getId());

        if (!existing.isEmpty()) {

            /*
             * Existing chains may have been created by an older
             * version of the application where LOAN_OFFICER was
             * incorrectly stored as an approval step.
             *
             * Repair the chain in-place instead of creating
             * duplicates.
             */
            repairLegacyChain(loan, existing);

            return approvalRepo.findByLoan_IdOrderByStepOrderAsc(
                    loan.getId()
            );
        }

        List<String> roles = requiredRolesFor(loan);

        int stepOrder = 1;

        for (String role : roles) {

            LoanApproval approval =
                    LoanApproval.builder()
                            .loan(loan)
                            .organization(loan.getOrganization())
                            .stepOrder(stepOrder)
                            .requiredRole(role)
                            .stepName(
                                    stepLabel(
                                            role,
                                            stepOrder,
                                            roles.size()
                                    )
                            )
                            .status(STATUS_PENDING)
                            .build();

            approvalRepo.save(approval);

            stepOrder++;
        }

        log.info(
                "Approval chain created. loanId={}, roles={}",
                loan.getId(),
                roles
        );

        return approvalRepo.findByLoan_IdOrderByStepOrderAsc(
                loan.getId()
        );
    }

    /**
     * Returns the approval chain after repairing any legacy
     * LOAN_OFFICER approval step.
     */
    @Transactional
    public List<LoanApproval> getChain(Long loanId) {

        Loan loan =
                loanService.getLoanForOrg(
                        loanId,
                        currentOrganizationIdFromLoan(loanId)
                );

        List<LoanApproval> chain =
                approvalRepo.findByLoan_IdOrderByStepOrderAsc(
                        loanId
                );

        repairLegacyChain(loan, chain);

        return approvalRepo.findByLoan_IdOrderByStepOrderAsc(
                loanId
        );
    }

    /**
     * Existing controller/service compatibility method.
     */
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

    /**
     * Main production approval workflow.
     *
     * newInterestRate is only accepted for the final approval.
     */
    @Transactional
    public LoanApproval decide(
            Long loanId,
            User decider,
            String decision,
            String comments,
            Double newInterestRate
    ) {

        if (loanId == null) {
            throw new IllegalArgumentException(
                    "Loan ID is required."
            );
        }

        if (decider == null) {
            throw new IllegalArgumentException(
                    "Authenticated user is required."
            );
        }

        String normalizedDecision =
                normalizeDecision(decision);

        String deciderRole =
                normalizeRole(
                        decider.getRole() != null
                                ? decider.getRole().getName()
                                : null
                );

        validateApproverRole(deciderRole);

        Long organizationId =
                getOrganizationId(decider);

        Loan loan =
                loanService.getLoanForOrg(
                        loanId,
                        organizationId
                );

        validateLoanStateForDecision(loan);

        List<LoanApproval> chain =
                approvalRepo.findByLoan_IdOrderByStepOrderAsc(
                        loanId
                );

        /*
         * If the loan has no chain, create one.
         */
        if (chain == null || chain.isEmpty()) {

            chain = initiateChain(loan);

        } else {

            /*
             * Remove/repair legacy LOAN_OFFICER approval steps.
             */
            repairLegacyChain(loan, chain);

            chain =
                    approvalRepo
                            .findByLoan_IdOrderByStepOrderAsc(
                                    loanId
                            );
        }

        /*
         * Determine the current pending approval.
         */
        LoanApproval step =
                findEligiblePendingStep(
                        loan,
                        chain,
                        decider,
                        deciderRole
                );

        if (step == null) {

            throw new IllegalStateException(
                    "No approval step is currently available for your role. "
                            + "The loan may already be approved, rejected, "
                            + "or waiting for another authorized approver."
            );
        }

        /*
         * Maker-checker:
         *
         * The person who originated the loan can never approve it.
         *
         * Your Loan model uses loanOfficer as the maker identity.
         */
        if (isLoanMaker(loan, decider)) {

            throw new IllegalStateException(
                    "You created this loan application. "
                            + "Another authorized user must review it "
                            + "under the maker-checker policy."
            );
        }

        /*
         * Same-user multi-stage approval is prohibited.
         */
        if (alreadyDecidedByUser(chain, decider)) {

            throw new IllegalStateException(
                    "You have already approved or rejected another "
                            + "step on this loan. A different authorized "
                            + "user must perform this approval step."
            );
        }

        /*
         * Ensure the role is actually allowed to perform this step.
         */
        if (!isRoleAuthorizedForStep(step, deciderRole)) {

            throw new IllegalStateException(
                    "This approval step requires "
                            + step.getRequiredRole()
                            + ". Your role is "
                            + deciderRole
                            + "."
            );
        }

        /*
         * Prevent modifying an already decided step.
         */
        if (!STATUS_PENDING.equalsIgnoreCase(step.getStatus())) {

            throw new IllegalStateException(
                    "This approval step has already been decided."
            );
        }

        boolean approved =
                STATUS_APPROVED.equalsIgnoreCase(
                        normalizedDecision
                );

        LocalDateTime now =
                LocalDateTime.now();

        /*
         * Record the decision.
         */
        step.setStatus(
                approved
                        ? STATUS_APPROVED
                        : STATUS_REJECTED
        );

        step.setApprover(decider);
        step.setComments(sanitizeComments(comments));
        step.setDecidedAt(now);

        approvalRepo.save(step);

        auditDecision(
                loan,
                decider,
                step,
                normalizedDecision
        );

        /*
         * REJECTION
         *
         * One rejection terminates the application.
         */
        if (!approved) {

            loanService.rejectLoan(
                    loanId,
                    decider,
                    comments != null && !comments.isBlank()
                            ? comments.trim()
                            : "Rejected during "
                            + step.getStepName()
            );

            log.info(
                    "Loan rejected through approval chain. loanId={}, "
                            + "step={}, approverId={}",
                    loanId,
                    step.getStepName(),
                    decider.getId()
            );

            return step;
        }

        /*
         * APPROVAL
         *
         * Check whether all required approval steps are complete.
         */
        List<LoanApproval> updatedChain =
                approvalRepo.findByLoan_IdOrderByStepOrderAsc(
                        loanId
                );

        boolean allApproved =
                updatedChain.stream()
                        .allMatch(
                                a ->
                                        STATUS_APPROVED.equalsIgnoreCase(
                                                a.getStatus()
                                        )
                        );

        if (allApproved) {

            /*
             * Final approval is idempotent.
             *
             * Never call LoanService.approveLoan() again if the
             * loan has already reached an approved/final state.
             */
            if (!isAlreadyApproved(loan)) {

                loanService.approveLoan(
                        loanId,
                        decider,
                        "Approved through production "
                                + "maker-checker approval chain",
                        newInterestRate
                );

                log.info(
                        "Loan fully approved. loanId={}, "
                                + "approverId={}, role={}",
                        loanId,
                        decider.getId(),
                        deciderRole
                );
            }

        } else {

            /*
             * The chain continues to the next authorized role.
             */
            log.info(
                    "Loan approval step completed. loanId={}, "
                            + "step={}, nextStepPending=true",
                    loanId,
                    step.getStepName()
            );
        }

        return step;
    }

    // ============================================================
    // APPROVAL POLICY
    // ============================================================

    /**
     * Determines the required approval roles.
     *
     * Loan Officer is never an approval role.
     *
     * The loan officer creates the loan.
     * Manager and Admin approve it.
     */
    private List<String> requiredRolesFor(Loan loan) {

        BigDecimal amount =
                loan.getAmountDecimal() != null
                        ? loan.getAmountDecimal()
                        : BigDecimal.ZERO;

        Organization organization =
                loan.getOrganization();

        BigDecimal organizationMaximum =
                organization != null
                        ? organization.getMaxLoanAmountDecimal()
                        : null;

        /*
         * If organization maximum is unavailable,
         * default to the safer high-exposure workflow.
         */
        if (organizationMaximum == null
                || organizationMaximum.compareTo(BigDecimal.ZERO) <= 0) {

            return List.of(
                    ROLE_MANAGER,
                    ROLE_ADMIN
            );
        }

        BigDecimal ratio =
                amount.divide(
                        organizationMaximum,
                        10,
                        java.math.RoundingMode.HALF_UP
                );

        /*
         * Up to 60% of organization's maximum:
         *
         * Manager approval.
         */
        if (ratio.compareTo(MANAGER_THRESHOLD) <= 0) {

            return List.of(
                    ROLE_MANAGER
            );
        }

        /*
         * More than 60%:
         *
         * Manager + Admin.
         */
        return List.of(
                ROLE_MANAGER,
                ROLE_ADMIN
        );
    }

    // ============================================================
    // LEGACY CHAIN REPAIR
    // ============================================================

    /**
     * Repairs approval chains generated by the old workflow.
     *
     * OLD:
     *
     *     LOAN_OFFICER
     *          ->
     *     MANAGER
     *
     * NEW:
     *
     *     MANAGER
     *
     * Or:
     *
     *     MANAGER
     *          ->
     *     ADMIN
     *
     * This is intentionally conservative:
     * approved/rejected historical records are preserved.
     */
    private void repairLegacyChain(
            Loan loan,
            List<LoanApproval> chain
    ) {

        if (chain == null || chain.isEmpty()) {
            return;
        }

        boolean changed = false;

        /*
         * First remove only PENDING legacy LOAN_OFFICER steps.
         *
         * Historical APPROVED/REJECTED LOAN_OFFICER records are
         * preserved for audit purposes.
         */
        for (LoanApproval approval : chain) {

            if (ROLE_LOAN_OFFICER.equalsIgnoreCase(
                    normalizeRole(approval.getRequiredRole())
            )
                    && STATUS_PENDING.equalsIgnoreCase(
                    approval.getStatus()
            )) {

                approval.setStatus(STATUS_APPROVED);

                approval.setComments(
                        appendSystemComment(
                                approval.getComments(),
                                "Legacy LOAN_OFFICER approval step "
                                        + "automatically retired during "
                                        + "approval workflow migration."
                        )
                );

                approval.setDecidedAt(
                        approval.getDecidedAt() != null
                                ? approval.getDecidedAt()
                                : LocalDateTime.now()
                );

                changed = true;
            }
        }

        if (changed) {

            chain.sort(
                    Comparator.comparing(
                            LoanApproval::getStepOrder,
                            Comparator.nullsLast(
                                    Integer::compareTo
                            )
                    )
            );

            for (LoanApproval approval : chain) {

                if (approval.getStepOrder() != null) {

                    /*
                     * Preserve historical ordering.
                     */
                    approvalRepo.save(approval);
                }
            }
        }

        /*
         * If all steps became approved after retiring the old
         * LOAN_OFFICER step, create the current production chain
         * if the loan itself is still awaiting approval.
         */
        boolean hasPendingManager =
                chain.stream()
                        .anyMatch(
                                a ->
                                        STATUS_PENDING.equalsIgnoreCase(
                                                a.getStatus()
                                        )
                                                && ROLE_MANAGER.equalsIgnoreCase(
                                                normalizeRole(
                                                        a.getRequiredRole()
                                                )
                                        )
                        );

        boolean hasPendingAdmin =
                chain.stream()
                        .anyMatch(
                                a ->
                                        STATUS_PENDING.equalsIgnoreCase(
                                                a.getStatus()
                                        )
                                                && ROLE_ADMIN.equalsIgnoreCase(
                                                normalizeRole(
                                                        a.getRequiredRole()
                                                )
                                        )
                        );

        if (!hasPendingManager && !hasPendingAdmin) {

            /*
             * Only create replacement steps when the loan has not
             * already reached a terminal approval state.
             */
            if (!isAlreadyApproved(loan)
                    && loan.getStatus() != LoanStatus.REJECTED
            ) {

                List<String> requiredRoles =
                        requiredRolesFor(loan);

                int nextStep =
                        chain.stream()
                                .map(LoanApproval::getStepOrder)
                                .filter(Objects::nonNull)
                                .max(Integer::compareTo)
                                .orElse(0)
                                + 1;

                for (String role : requiredRoles) {

                    boolean alreadyExists =
                            chain.stream()
                                    .anyMatch(
                                            a ->
                                                    role.equalsIgnoreCase(
                                                            normalizeRole(
                                                                    a.getRequiredRole()
                                                            )
                                                    )
                                    );

                    if (!alreadyExists) {

                        LoanApproval replacement =
                                LoanApproval.builder()
                                        .loan(loan)
                                        .organization(
                                                loan.getOrganization()
                                        )
                                        .stepOrder(nextStep++)
                                        .requiredRole(role)
                                        .stepName(
                                                stepLabel(
                                                        role,
                                                        nextStep - 1,
                                                        requiredRoles.size()
                                                )
                                        )
                                        .status(STATUS_PENDING)
                                        .build();

                        approvalRepo.save(replacement);
                    }
                }
            }
        }
    }

    // ============================================================
    // FIND CURRENT STEP
    // ============================================================

    private LoanApproval findEligiblePendingStep(
            Loan loan,
            List<LoanApproval> chain,
            User decider,
            String deciderRole
    ) {

        List<LoanApproval> ordered =
                new ArrayList<>(chain);

        ordered.sort(
                Comparator.comparing(
                        LoanApproval::getStepOrder,
                        Comparator.nullsLast(
                                Integer::compareTo
                        )
                )
        );

        /*
         * Ignore retired legacy LOAN_OFFICER steps.
         */
        for (LoanApproval approval : ordered) {

            if (!STATUS_PENDING.equalsIgnoreCase(
                    approval.getStatus()
            )) {
                continue;
            }

            String requiredRole =
                    normalizeRole(
                            approval.getRequiredRole()
                    );

            if (ROLE_LOAN_OFFICER.equals(requiredRole)) {
                continue;
            }

            /*
             * Current step must be the first pending step.
             */
            boolean previousStepsComplete =
                    ordered.stream()
                            .filter(
                                    a ->
                                            a.getStepOrder() != null
                                                    && approval.getStepOrder() != null
                                                    && a.getStepOrder()
                                                    < approval.getStepOrder()
                            )
                            .allMatch(
                                    a ->
                                            STATUS_APPROVED.equalsIgnoreCase(
                                                    a.getStatus()
                                            )
                                            || (
                                                    ROLE_LOAN_OFFICER.equals(
                                                            normalizeRole(
                                                                    a.getRequiredRole()
                                                            )
                                                    )
                                                            && STATUS_PENDING.equalsIgnoreCase(
                                                            a.getStatus()
                                                    )
                                            )
                            );

            if (!previousStepsComplete) {
                continue;
            }

            /*
             * Exact role.
             */
            if (requiredRole.equals(deciderRole)) {
                return approval;
            }

            /*
             * ADMIN is senior authority and may act on a pending
             * Manager step when the manager is unavailable.
             *
             * However, ADMIN cannot approve a loan they created.
             */
            if (ROLE_ADMIN.equals(deciderRole)
                    && ROLE_MANAGER.equals(requiredRole)) {

                return approval;
            }
        }

        return null;
    }

    // ============================================================
    // MAKER-CHECKER
    // ============================================================

    private boolean isLoanMaker(
            Loan loan,
            User user
    ) {

        if (loan == null
                || loan.getLoanOfficer() == null
                || user == null
                || loan.getLoanOfficer().getId() == null
                || user.getId() == null) {

            return false;
        }

        return loan.getLoanOfficer()
                .getId()
                .equals(user.getId());
    }

    private boolean alreadyDecidedByUser(
            List<LoanApproval> chain,
            User user
    ) {

        if (chain == null
                || user == null
                || user.getId() == null) {

            return false;
        }

        return chain.stream()
                .anyMatch(
                        approval ->
                                approval.getApprover() != null
                                        && approval.getApprover().getId() != null
                                        && approval.getApprover()
                                        .getId()
                                        .equals(user.getId())
                                        && (
                                        STATUS_APPROVED.equalsIgnoreCase(
                                                approval.getStatus()
                                        )
                                                || STATUS_REJECTED.equalsIgnoreCase(
                                                approval.getStatus()
                                        )
                                )
                );
    }

    // ============================================================
    // AUTHORIZATION
    // ============================================================

    private boolean isRoleAuthorizedForStep(
            LoanApproval step,
            String deciderRole
    ) {

        String required =
                normalizeRole(
                        step.getRequiredRole()
                );

        if (required.equals(deciderRole)) {
            return true;
        }

        /*
         * Admin is senior authority and may perform Manager-level
         * approval.
         */
        return ROLE_ADMIN.equals(deciderRole)
                && ROLE_MANAGER.equals(required);
    }

    private void validateApproverRole(
            String role
    ) {

        if (!ROLE_LOAN_OFFICER.equals(role)
                && !ROLE_MANAGER.equals(role)
                && !ROLE_ADMIN.equals(role)) {

            throw new IllegalStateException(
                    "Your role is not authorized to approve loans."
            );
        }
    }

    // ============================================================
    // LOAN VALIDATION
    // ============================================================

    private void validateLoan(
            Loan loan
    ) {

        if (loan == null) {
            throw new IllegalArgumentException(
                    "Loan is required."
            );
        }

        if (loan.getId() == null) {
            throw new IllegalArgumentException(
                    "Loan ID is required."
            );
        }

        if (loan.getOrganization() == null
                || loan.getOrganization().getId() == null) {

            throw new IllegalStateException(
                    "Loan organization is required."
            );
        }
    }

    private void validateLoanStateForDecision(
            Loan loan
    ) {

        if (loan.getStatus() == null) {
            throw new IllegalStateException(
                    "Loan status is not defined."
            );
        }

        if (loan.getStatus() == LoanStatus.REJECTED) {

            throw new IllegalStateException(
                    "This loan has already been rejected."
            );
        }

        /*
         * Depending on your enum, APPROVED may not exist.
         * Therefore final approval is determined primarily
         * through the approval chain and LoanService.
         */
    }

    private boolean isAlreadyApproved(
            Loan loan
    ) {

        if (loan == null
                || loan.getStatus() == null) {

            return false;
        }

        String status =
                loan.getStatus().name();

        return "APPROVED".equalsIgnoreCase(status)
                || "ACTIVE".equalsIgnoreCase(status)
                || "DISBURSED".equalsIgnoreCase(status);
    }

    // ============================================================
    // AUDIT
    // ============================================================

    private void auditDecision(
            Loan loan,
            User decider,
            LoanApproval step,
            String decision
    ) {

        String comments =
                step.getComments();

        String description =
                step.getStepName()
                        + " — "
                        + decision;

        if (comments != null
                && !comments.isBlank()) {

            description +=
                    ": "
                            + comments;
        }

        auditService.log(
                loan.getOrganization(),
                decider,
                "LOAN_APPROVAL_STEP_"
                        + step.getStatus(),
                "LOAN",
                loan.getId().toString(),
                description
        );
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private String normalizeDecision(
            String decision
    ) {

        if (decision == null
                || decision.isBlank()) {

            throw new IllegalArgumentException(
                    "Decision is required."
            );
        }

        String normalized =
                decision.trim()
                        .toUpperCase();

        if (!STATUS_APPROVED.equals(normalized)
                && !STATUS_REJECTED.equals(normalized)) {

            throw new IllegalArgumentException(
                    "Decision must be APPROVED or REJECTED."
            );
        }

        return normalized;
    }

    private String normalizeRole(
            String role
    ) {

        if (role == null) {
            return "";
        }

        return role
                .trim()
                .toUpperCase()
                .replace("ROLE_", "");
    }

    private String sanitizeComments(
            String comments
    ) {

        if (comments == null) {
            return null;
        }

        String value =
                comments.trim();

        return value.isEmpty()
                ? null
                : value;
    }

    private String appendSystemComment(
            String existing,
            String addition
    ) {

        if (existing == null
                || existing.isBlank()) {

            return addition;
        }

        return existing
                + "\n"
                + addition;
    }

    private String stepLabel(
            String role,
            int step,
            int total
    ) {

        return switch (normalizeRole(role)) {

            case ROLE_MANAGER ->
                    "Branch Manager Approval";

            case ROLE_ADMIN ->
                    "Credit Committee / Admin Approval";

            default ->
                    role
                            + " Approval (Step "
                            + step
                            + "/"
                            + total
                            + ")";
        };
    }

    private Long getOrganizationId(
            User user
    ) {

        if (user.getOrganization() == null
                || user.getOrganization().getId() == null) {

            throw new IllegalStateException(
                    "Authenticated user is not associated with an organization."
            );
        }

        return user.getOrganization().getId();
    }

    /**
     * This helper exists only because getChain() needs the organization
     * before it can use LoanService.getLoanForOrg().
     *
     * If your LoanService exposes a simpler organization-safe lookup,
     * use that method instead.
     */
    private Long currentOrganizationIdFromLoan(
            Long loanId
    ) {

        /*
         * getChain() should normally be called from a secured request
         * where the current user is already known.
         *
         * This method intentionally does not invent an organization ID.
         *
         * For production, the controller should pass the current user's
         * organization ID through getChainForOrganization().
         */
        throw new IllegalStateException(
                "Use getChainForOrganization() for tenant-safe approval-chain access."
        );
    }

    /**
     * Production tenant-safe chain retrieval.
     */
    @Transactional(readOnly = true)
    public List<LoanApproval> getChainForOrganization(
            Long loanId,
            Long organizationId
    ) {

        if (loanId == null) {
            throw new IllegalArgumentException(
                    "Loan ID is required."
            );
        }

        if (organizationId == null) {
            throw new IllegalArgumentException(
                    "Organization ID is required."
            );
        }

        Loan loan =
                loanService.getLoanForOrg(
                        loanId,
                        organizationId
                );

        List<LoanApproval> chain =
                approvalRepo.findByLoan_IdOrderByStepOrderAsc(
                        loan.getId()
                );

        return chain;
    }
}
