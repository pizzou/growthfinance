package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanApproval;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanApprovalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LoanApprovalService {

    private final LoanApprovalRepository approvalRepo;
    private final LoanService loanService;
    private final AuditService auditService;

    
    private List<String> requiredRolesFor(Loan loan) {

        List<String> roles = new ArrayList<>();

        BigDecimal amount = loan.getAmountDecimal() != null
                ? loan.getAmountDecimal()
                : BigDecimal.ZERO;

        Organization organization = loan.getOrganization();

        BigDecimal orgMax = organization != null
                ? organization.getMaxLoanAmountDecimal()
                : null;

        double ratio;

        if (orgMax != null && orgMax.compareTo(BigDecimal.ZERO) > 0) {
            ratio = amount
                    .divide(orgMax, 10, java.math.RoundingMode.HALF_UP)
                    .doubleValue();
        } else {
            ratio = 1.0;
        }

        /*
         * Determine the approval hierarchy required by exposure.
         *
         * <= 20%:
         *     one checker
         *
         * <= 60%:
         *     two checkers
         *
         * > 60%:
         *     three checkers
         */
        if (ratio <= 0.20) {

            roles.add("MANAGER");

        } else if (ratio <= 0.60) {

            roles.add("MANAGER");
            roles.add("ADMIN");

        } else {

            roles.add("MANAGER");
            roles.add("ADMIN");
        }

        /*
         * Remove the creator's role from the chain.
         *
         * This is critical for maker-checker.
         */
        User creator = loan.getCreatedBy();

        if (creator != null) {

            String creatorRole = normalizeRole(creator);

            roles.removeIf(role -> role.equals(creatorRole));
        }

        /*
         * Never allow LOAN_OFFICER to remain as an approval step
         * simply because the loan was created by a Loan Officer.
         *
         * The Loan Officer is the maker, not the checker.
         */
        roles.removeIf(role -> "LOAN_OFFICER".equals(role));

        /*
         * Remove duplicate roles while preserving order.
         */
        roles = roles.stream()
                .distinct()
                .toList();

        /*
         * Safety fallback.
         *
         * If all configured roles were removed because the creator
         * already has those roles, the next higher authority must
         * still be available.
         */
        if (roles.isEmpty()) {

            String creatorRole = creator != null
                    ? normalizeRole(creator)
                    : null;

            if ("LOAN_OFFICER".equals(creatorRole)) {

                roles = List.of("MANAGER");

            } else if ("MANAGER".equals(creatorRole)) {

                roles = List.of("ADMIN");

            } else if ("ADMIN".equals(creatorRole)) {

                /*
                 * ADMIN is the highest configured authority.
                 *
                 * There is no higher role in this simple hierarchy.
                 * Such a loan can be treated as requiring no additional
                 * approval chain, but this should normally be prevented
                 * by the creation policy.
                 */
                roles = List.of();

            } else {

                roles = List.of("MANAGER");
            }
        }

        return roles;
    }

    // ============================================================
    // ROLE NORMALIZATION
    // ============================================================

    private String normalizeRole(User user) {

        if (user == null || user.getRole() == null) {
            return "";
        }

        String role = user.getRole().getName();

        if (role == null) {
            return "";
        }

        role = role.trim().toUpperCase(Locale.ROOT);

        /*
         * Support both:
         *
         * MANAGER
         * ROLE_MANAGER
         *
         * depending on how the application's roles are stored.
         */
        if (role.startsWith("ROLE_")) {
            role = role.substring(5);
        }

        return role;
    }

    // ============================================================
    // CHAIN INITIALIZATION
    // ============================================================

    @Transactional
    public List<LoanApproval> initiateChain(Loan loan) {

        if (loan == null || loan.getId() == null) {
            throw new IllegalArgumentException(
                    "Cannot initialize approval chain for an unsaved loan."
            );
        }

        List<LoanApproval> existing =
                approvalRepo.findByLoan_IdOrderByStepOrderAsc(loan.getId());

        /*
         * Idempotent.
         *
         * Never create duplicate approval chains.
         */
        if (!existing.isEmpty()) {
            return existing;
        }

        List<String> roles = requiredRolesFor(loan);

        /*
         * If there are no approval roles, the loan was created by
         * the highest authority.
         *
         * Do not silently approve it here. The creation/approval
         * policy should explicitly determine whether this is allowed.
         */
        if (roles.isEmpty()) {
            return List.of();
        }

        int step = 1;

        for (String role : roles) {

            LoanApproval approval = LoanApproval.builder()
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
                    .build();

            approvalRepo.save(approval);

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
                    "Credit Committee / Admin Approval";

            default ->
                    role + " Approval (Step "
                            + step
                            + "/"
                            + total
                            + ")";
        };
    }

    // ============================================================
    // GET CHAIN
    // ============================================================

    @Transactional(readOnly = true)
    public List<LoanApproval> getChain(Long loanId) {

        return approvalRepo
                .findByLoan_IdOrderByStepOrderAsc(loanId);
    }

    // ============================================================
    // DECIDE
    // ============================================================

    @Transactional(readOnly = true)
public List<LoanApproval> getChainForOrganization(
        Long loanId,
        Long organizationId
) {
    if (loanId == null) {
        throw new IllegalArgumentException("Loan ID is required.");
    }

    if (organizationId == null) {
        throw new IllegalArgumentException("Organization ID is required.");
    }

    // Verify that the loan belongs to the authenticated organization.
    loanService.getLoanForOrg(loanId, organizationId);

    return approvalRepo.findByLoan_IdOrderByStepOrderAsc(loanId);
}

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

        if (decider == null) {
            throw new IllegalArgumentException(
                    "Authenticated user is required."
            );
        }

        if (loanId == null) {
            throw new IllegalArgumentException(
                    "Loan ID is required."
            );
        }

        if (decision == null || decision.isBlank()) {
            throw new IllegalArgumentException(
                    "Approval decision is required."
            );
        }

        String normalizedDecision =
                decision.trim().toUpperCase(Locale.ROOT);

        if (!"APPROVED".equals(normalizedDecision)
                && !"REJECTED".equals(normalizedDecision)) {

            throw new IllegalArgumentException(
                    "Decision must be APPROVED or REJECTED."
            );
        }

        if (decider.getOrganization() == null
                || decider.getOrganization().getId() == null) {

            throw new IllegalArgumentException(
                    "Authenticated user has no organization."
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
         * Only loans that are still awaiting approval may enter
         * this workflow.
         */
       LoanStatus currentStatus = loan.getStatus();

if (currentStatus != LoanStatus.PENDING
        && currentStatus != LoanStatus.UNDER_REVIEW) {

    throw new IllegalStateException(
            "Loan " + loan.getReferenceNumber()
                    + " cannot be approved from its current status: "
                    + currentStatus
    );
}

        List<LoanApproval> chain =
                approvalRepo
                        .findByLoan_IdOrderByStepOrderAsc(loanId);

        /*
         * Legacy / missing chain protection.
         */
        if (chain.isEmpty()) {

            chain = initiateChain(loan);

            /*
             * Re-read after creation.
             */
            chain = approvalRepo
                    .findByLoan_IdOrderByStepOrderAsc(loanId);
        }

        /*
         * If the chain is genuinely empty, there is no configured
         * checker for this loan.
         */
        if (chain.isEmpty()) {

            throw new IllegalStateException(
                    "This loan has no configured approval step. "
                            + "Configure an approval authority before approving the loan."
            );
        }

        /*
         * Find the FIRST pending step.
         */
        LoanApproval step = chain.stream()
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

        String deciderRole =
                normalizeRole(decider);

        String requiredRole =
                step.getRequiredRole() != null
                        ? step.getRequiredRole()
                        .trim()
                        .toUpperCase(Locale.ROOT)
                        : "";

        if (requiredRole.startsWith("ROLE_")) {
            requiredRole =
                    requiredRole.substring(5);
        }

        /*
         * ADMIN is the highest authority and may perform any
         * lower-level approval step.
         *
         * MANAGER can perform MANAGER steps.
         *
         * LOAN_OFFICER can perform LOAN_OFFICER steps, although
         * maker-checker below still prevents the creator from
         * approving their own loan.
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

        /*
         * ========================================================
         * MAKER-CHECKER
         * ========================================================
         *
         * The creator can NEVER approve their own loan.
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

        /*
         * Backward compatibility for old loans where created_by
         * may not yet exist.
         *
         * If loanOfficer is populated and matches the decider,
         * still prevent self-approval.
         */
        if (creator == null
                && loan.getLoanOfficer() != null
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

        /*
         * ========================================================
         * SAME USER CANNOT APPROVE MULTIPLE STEPS
         * ========================================================
         */
        boolean alreadyDecidedByThisUser =
                chain.stream()
                        .anyMatch(a ->
                                a.getApprover() != null
                                        && a.getApprover()
                                        .getId()
                                        .equals(decider.getId())
                        );

        if (alreadyDecidedByThisUser) {

            throw new IllegalStateException(
                    "You have already approved a previous step "
                            + "on this loan. A different authorized "
                            + "user must approve the next step."
            );
        }

        /*
         * ========================================================
         * RECORD DECISION
         * ========================================================
         */
        boolean approved =
                "APPROVED".equals(normalizedDecision);

        step.setStatus(
                approved
                        ? "APPROVED"
                        : "REJECTED"
        );

        step.setApprover(decider);
        step.setComments(comments);
        step.setDecidedAt(LocalDateTime.now());

        approvalRepo.save(step);

        /*
         * ========================================================
         * AUDIT
         * ========================================================
         */
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
                                ? ": " + comments
                                : ""
                )
        );

        /*
         * ========================================================
         * REJECTION
         * ========================================================
         */
        if (!approved) {

            loanService.rejectLoan(
                    loanId,
                    decider,
                    comments != null
                            && !comments.isBlank()
                            ? comments
                            : "Rejected at "
                            + step.getStepName()
            );

            return step;
        }

        /*
         * ========================================================
         * CHECK WHETHER ALL STEPS ARE APPROVED
         * ========================================================
         */
        List<LoanApproval> updatedChain =
                approvalRepo
                        .findByLoan_IdOrderByStepOrderAsc(loanId);

        boolean allApproved =
                !updatedChain.isEmpty()
                        && updatedChain.stream()
                        .allMatch(a ->
                                "APPROVED".equalsIgnoreCase(
                                        a.getStatus()
                                )
                        );

        /*
         * ========================================================
         * FINAL APPROVAL
         * ========================================================
         */
        if (allApproved) {

            String approvalMessage =
                    "Approved via "
                            + updatedChain.size()
                            + "-step maker-checker chain";

            loanService.approveLoan(
                    loanId,
                    decider,
                    approvalMessage,
                    newInterestRate
            );
        }

        return step;
    }
}