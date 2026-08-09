
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
    // APPROVAL CHAIN
    // ============================================================

    /**
     * Production maker-checker workflow.
     *
     * The person who created/submitted the loan must NEVER be an
     * approval step for their own loan.
     *
     * Current maker is represented by Loan.loanOfficer because the
     * Loan entity already contains that field.
     *
     * Examples:
     *
     * LOAN_OFFICER creates:
     *      MANAGER
     *      ADMIN for larger exposure
     *
     * MANAGER creates:
     *      ADMIN
     *
     * ADMIN creates:
     *      ADMIN is not allowed to self-approve, therefore the system
     *      requires another ADMIN user.
     */
    private List<String> requiredRolesFor(Loan loan) {

        List<String> roles = new ArrayList<>();

        String makerRole = null;

        if (loan.getLoanOfficer() != null
                && loan.getLoanOfficer().getRole() != null) {

            makerRole = loan.getLoanOfficer()
                    .getRole()
                    .getName();
        }

        // --------------------------------------------------------
        // Determine organization exposure ratio
        // --------------------------------------------------------

        double loanAmount = loan.getAmountDecimal() != null
                ? loan.getAmountDecimal().doubleValue()
                : 0.0;

        double organizationMaximum = 0.0;

        if (loan.getOrganization() != null
                && loan.getOrganization().getMaxLoanAmountDecimal() != null) {

            organizationMaximum = loan.getOrganization()
                    .getMaxLoanAmountDecimal()
                    .doubleValue();
        }

        double ratio;

        if (organizationMaximum > 0.0) {
            ratio = loanAmount / organizationMaximum;
        } else {
            ratio = 1.0;
        }

        // --------------------------------------------------------
        // Maker-checker hierarchy
        // --------------------------------------------------------

        if ("LOAN_OFFICER".equalsIgnoreCase(makerRole)) {

            /*
             * Loan Officer created the application.
             *
             * The Loan Officer must NOT approve it.
             *
             * Manager is the first approval level.
             */
            roles.add("MANAGER");

            /*
             * Larger exposures require an additional ADMIN/
             * credit-committee level.
             */
            if (ratio > 0.60) {
                roles.add("ADMIN");
            }

        } else if ("MANAGER".equalsIgnoreCase(makerRole)) {

            /*
             * Manager created the application.
             *
             * Manager cannot approve their own loan.
             *
             * ADMIN becomes the first approval level.
             */
            roles.add("ADMIN");

        } else if ("ADMIN".equalsIgnoreCase(makerRole)) {

            /*
             * ADMIN created the application.
             *
             * Another ADMIN must approve it.
             *
             * This still enforces separation of duties because
             * decide() below prevents the maker from approving.
             */
            roles.add("ADMIN");

        } else {

            /*
             * Unknown/missing maker role.
             *
             * Fail safely toward a senior approval level rather
             * than allowing an unknown user to approve.
             */
            roles.add("MANAGER");

            if (ratio > 0.60) {
                roles.add("ADMIN");
            }
        }

        return roles;
    }


    // ============================================================
    // STEP LABEL
    // ============================================================

    private String stepLabel(
            String role,
            int step,
            int total) {

        return switch (role) {

            case "LOAN_OFFICER" ->
                    "Loan Officer Review";

            case "MANAGER" ->
                    "Branch Manager Approval";

            case "ADMIN" ->
                    "Credit Committee / Senior Management Approval";

            default ->
                    role + " Approval (Step "
                            + step
                            + "/"
                            + total
                            + ")";
        };
    }


    // ============================================================
    // INITIATE APPROVAL CHAIN
    // ============================================================

    @Transactional
    public List<LoanApproval> initiateChain(Loan loan) {

        if (loan == null) {
            throw new IllegalArgumentException(
                    "Loan is required to initiate approval chain.");
        }

        if (loan.getId() == null) {
            throw new IllegalArgumentException(
                    "Loan must be persisted before initiating approval chain.");
        }

        /*
         * Idempotent.
         *
         * Never create duplicate approval steps for the same loan.
         */
        List<LoanApproval> existing =
                approvalRepo.findByLoan_IdOrderByStepOrderAsc(
                        loan.getId());

        if (!existing.isEmpty()) {
            return existing;
        }

        List<String> roles = requiredRolesFor(loan);

        if (roles.isEmpty()) {
            throw new IllegalStateException(
                    "No valid approval roles could be determined for loan "
                            + loan.getId());
        }

        int step = 1;

        for (String role : roles) {

            LoanApproval approval =
                    LoanApproval.builder()
                            .loan(loan)
                            .organization(loan.getOrganization())
                            .stepOrder(step)
                            .requiredRole(role)
                            .stepName(
                                    stepLabel(
                                            role,
                                            step,
                                            roles.size()))
                            .status("PENDING")
                            .build();

            approvalRepo.save(approval);

            step++;
        }

        return approvalRepo.findByLoan_IdOrderByStepOrderAsc(
                loan.getId());
    }


    // ============================================================
    // GET APPROVAL CHAIN
    // ============================================================

    @Transactional(readOnly = true)
    public List<LoanApproval> getChain(Long loanId) {

        if (loanId == null) {
            throw new IllegalArgumentException(
                    "Loan ID is required.");
        }

        return approvalRepo
                .findByLoan_IdOrderByStepOrderAsc(loanId);
    }


    // ============================================================
    // STANDARD DECISION
    // ============================================================

    @Transactional
    public LoanApproval decide(
            Long loanId,
            User decider,
            String decision,
            String comments) {

        return decide(
                loanId,
                decider,
                decision,
                comments,
                null);
    }


    // ============================================================
    // DECISION WITH INTEREST RATE OVERRIDE
    // ============================================================

    /**
     * Approves or rejects the current approval step.
     *
     * newInterestRate is only used when the final approval step
     * completes successfully.
     */
    @Transactional
    public LoanApproval decide(
            Long loanId,
            User decider,
            String decision,
            String comments,
            Double newInterestRate) {

        // --------------------------------------------------------
        // Basic validation
        // --------------------------------------------------------

        if (loanId == null) {
            throw new IllegalArgumentException(
                    "Loan ID is required.");
        }

        if (decider == null) {
            throw new IllegalStateException(
                    "Authenticated user is required.");
        }

        if (decision == null
                || decision.isBlank()) {

            throw new IllegalArgumentException(
                    "Approval decision is required.");
        }

        String normalizedDecision =
                decision.trim().toUpperCase();

        if (!normalizedDecision.equals("APPROVED")
                && !normalizedDecision.equals("REJECTED")) {

            throw new IllegalArgumentException(
                    "Invalid approval decision. "
                            + "Use APPROVED or REJECTED.");
        }

        // --------------------------------------------------------
        // Organization validation
        // --------------------------------------------------------

        if (decider.getOrganization() == null
                || decider.getOrganization().getId() == null) {

            throw new IllegalStateException(
                    "The approving user is not associated with an organization.");
        }

        Long organizationId =
                decider.getOrganization().getId();

        // --------------------------------------------------------
        // Load loan with organization ownership check
        // --------------------------------------------------------

        Loan loan =
                loanService.getLoanForOrg(
                        loanId,
                        organizationId);

        if (loan == null) {
            throw new IllegalArgumentException(
                    "Loan not found.");
        }

        // --------------------------------------------------------
        // Ensure approval chain exists
        // --------------------------------------------------------

        List<LoanApproval> chain =
                approvalRepo.findByLoan_IdOrderByStepOrderAsc(
                        loanId);

        if (chain.isEmpty()) {

            chain = initiateChain(loan);
        }

        if (chain == null || chain.isEmpty()) {

            throw new IllegalStateException(
                    "No approval chain exists for this loan.");
        }

        // --------------------------------------------------------
        // Prevent approval of already completed loans
        // --------------------------------------------------------

        if (loan.getStatus() != null) {

            String loanStatus =
                    loan.getStatus().name();

            if ("REJECTED".equalsIgnoreCase(loanStatus)) {

                throw new IllegalStateException(
                        "This loan has already been rejected.");
            }

            /*
             * Do not block APPROVED here because an approval chain
             * may have completed but LoanService could have changed
             * the status slightly differently.
             *
             * The approval chain itself remains the source of truth
             * for whether another approval step exists.
             */
        }

        // --------------------------------------------------------
        // Find current pending step
        // --------------------------------------------------------

        LoanApproval step =
                chain.stream()
                        .filter(Objects::nonNull)
                        .filter(a ->
                                "PENDING".equalsIgnoreCase(
                                        a.getStatus()))
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "This loan has no pending approval step. "
                                                + "It may already be fully approved or rejected."));

        // --------------------------------------------------------
        // Decider role
        // --------------------------------------------------------

        String deciderRole = null;

        if (decider.getRole() != null) {

            deciderRole =
                    decider.getRole().getName();
        }

        if (deciderRole == null
                || deciderRole.isBlank()) {

            throw new IllegalStateException(
                    "Your user account has no assigned role. "
                            + "An approval role is required.");
        }

        deciderRole =
                deciderRole.trim().toUpperCase();

        String requiredRole =
                step.getRequiredRole() != null
                        ? step.getRequiredRole()
                        .trim()
                        .toUpperCase()
                        : null;

        if (requiredRole == null
                || requiredRole.isBlank()) {

            throw new IllegalStateException(
                    "The current approval step has no required role.");
        }

        // --------------------------------------------------------
        // Maker-checker: maker can NEVER approve
        // --------------------------------------------------------

        User maker =
                loan.getLoanOfficer();

        if (maker != null
                && maker.getId() != null
                && decider.getId() != null
                && maker.getId().equals(decider.getId())) {

            throw new IllegalStateException(
                    "You created this loan application. "
                            + "Another authorized user must review it "
                            + "under the maker-checker policy.");
        }

        // --------------------------------------------------------
        // Prevent same person approving multiple steps
        // --------------------------------------------------------

        boolean alreadyDecidedByThisUser =
                chain.stream()
                        .filter(Objects::nonNull)
                        .anyMatch(a ->
                                a.getApprover() != null
                                        && a.getApprover().getId() != null
                                        && decider.getId() != null
                                        && a.getApprover()
                                        .getId()
                                        .equals(decider.getId()));

        if (alreadyDecidedByThisUser) {

            throw new IllegalStateException(
                    "You have already approved or rejected another "
                            + "step on this loan. A different authorized "
                            + "user must perform the next approval.");
        }

        // --------------------------------------------------------
        // Role validation
        // --------------------------------------------------------

        boolean roleMatches =
                requiredRole.equals(deciderRole);

        /*
         * ADMIN can perform a MANAGER step only if your organization
         * intentionally allows senior override.
         *
         * However, ADMIN must still be a different person from
         * the maker and cannot have already decided another step.
         */
        boolean seniorOverride =
                "ADMIN".equals(deciderRole)
                        && (
                        "MANAGER".equals(requiredRole)
                                || "LOAN_OFFICER".equals(requiredRole)
                );

        if (!roleMatches && !seniorOverride) {

            throw new IllegalStateException(
                    "This approval step requires "
                            + requiredRole
                            + ". Your role is "
                            + deciderRole
                            + ".");
        }

        // --------------------------------------------------------
        // Final approval rate validation
        // --------------------------------------------------------

        if (newInterestRate != null) {

            if (!Double.isFinite(newInterestRate)) {

                throw new IllegalArgumentException(
                        "Interest rate must be a valid number.");
            }

            if (newInterestRate < 0.0) {

                throw new IllegalArgumentException(
                        "Interest rate cannot be negative.");
            }

            if (newInterestRate > 100.0) {

                throw new IllegalArgumentException(
                        "Interest rate cannot exceed 100%.");
            }
        }

        // --------------------------------------------------------
        // Record decision
        // --------------------------------------------------------

        boolean approved =
                "APPROVED".equals(normalizedDecision);

        step.setStatus(
                approved
                        ? "APPROVED"
                        : "REJECTED");

        step.setApprover(decider);

        step.setComments(
                comments != null
                        ? comments.trim()
                        : null);

        step.setDecidedAt(
                LocalDateTime.now());

        approvalRepo.save(step);

        // --------------------------------------------------------
        // Audit
        // --------------------------------------------------------

        String auditAction =
                "LOAN_APPROVAL_STEP_"
                        + step.getStatus();

        String auditDescription =
                step.getStepName()
                        + " — "
                        + step.getStatus();

        if (comments != null
                && !comments.isBlank()) {

            auditDescription +=
                    ": "
                            + comments.trim();
        }

        auditService.log(
                loan.getOrganization(),
                decider,
                auditAction,
                "LOAN",
                loanId.toString(),
                auditDescription);

        // --------------------------------------------------------
        // Rejection
        // --------------------------------------------------------

        if (!approved) {

            String rejectionReason =
                    comments != null
                            && !comments.isBlank()
                            ? comments.trim()
                            : "Rejected at "
                            + step.getStepName();

            loanService.rejectLoan(
                    loanId,
                    decider,
                    rejectionReason);

            return step;
        }

        // --------------------------------------------------------
        // Check whether every step has been approved
        // --------------------------------------------------------

        List<LoanApproval> updatedChain =
                approvalRepo
                        .findByLoan_IdOrderByStepOrderAsc(
                                loanId);

        boolean allApproved =
                updatedChain.stream()
                        .allMatch(a ->
                                "APPROVED".equalsIgnoreCase(
                                        a.getStatus()));

        // --------------------------------------------------------
        // Final approval
        // --------------------------------------------------------

        if (allApproved) {

            loanService.approveLoan(
                    loanId,
                    decider,
                    "Approved via "
                            + updatedChain.size()
                            + "-step maker-checker approval chain",
                    newInterestRate);
        }

        return step;
    }
}
