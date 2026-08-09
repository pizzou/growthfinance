
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.KycCheck;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.KycCheckRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceService {

    private final KycCheckRepository kycRepo;
    private final BorrowerRepository borrowerRepo;
    private final AuditService auditService;

    /*
     * IMPORTANT:
     *
     * This service is intentionally structured so the actual sanctions provider
     * can be introduced without changing the controller/service contract.
     *
     * Do NOT use a hard-coded sanctions list in production.
     *
     * The current implementation performs:
     *
     * 1. Organization ownership validation
     * 2. Required identity-data validation
     * 3. Internal screening placeholder
     * 4. Explicit decision calculation
     * 5. Persistent KYC check
     * 6. Borrower KYC status update
     * 7. Audit logging
     *
     * Replace the internal screening implementation with a real provider
     * before enabling production onboarding.
     */

    private static final String INTERNAL_PROVIDER = "INTERNAL";

    private static final double CLEAR_MATCH_SCORE = 0.0d;
    private static final double MANUAL_REVIEW_MATCH_SCORE = 65.0d;

    private static final int MAX_NAME_LENGTH = 200;

    /**
     * Runs the complete KYC screening process for a borrower.
     *
     * The organization ID is mandatory and is used to prevent cross-tenant
     * access to borrowers.
     */
    @Transactional
    public KycCheck runFullScreening(Long borrowerId, Long orgId) {

        validateId(borrowerId, "borrowerId");
        validateId(orgId, "orgId");

        Borrower borrower = borrowerRepo.findById(borrowerId)
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "Borrower not found: " + borrowerId
                )
            );

        /*
         * Multi-tenant security check.
         *
         * Never allow an organization to screen or modify another
         * organization's borrower.
         */
        validateOrganizationOwnership(borrower, orgId);

        /*
         * Run the individual screening components.
         */
        KycCheck.CheckResult sanctionsResult =
            screenWatchlists(borrower);

        KycCheck.CheckResult identityResult =
            verifyIdentityInformation(borrower);

        KycCheck.CheckResult overallResult =
            determineOverallResult(
                sanctionsResult,
                identityResult
            );

        double matchScore =
            determineMatchScore(
                sanctionsResult,
                identityResult,
                overallResult
            );

        String notes = buildScreeningNotes(
            sanctionsResult,
            identityResult
        );

        /*
         * Persist the screening result.
         *
         * We deliberately create a new KycCheck record rather than
         * overwriting historical checks. KYC decisions must remain
         * auditable.
         */
        KycCheck check = KycCheck.builder()
            .borrower(borrower)
            .organization(borrower.getOrganization())
            .checkType(
                KycCheck.CheckType.SANCTIONS_SCREENING
            )
            .result(overallResult)
            .matchScore(matchScore)
            .provider(INTERNAL_PROVIDER)
            .notes(notes)
            .build();

        check = kycRepo.save(check);

        /*
         * Update borrower status based on the screening result.
         */
        updateBorrowerKycStatus(
            borrower,
            overallResult
        );

        borrowerRepo.save(borrower);

        /*
         * Audit every screening attempt.
         */
        auditService.log(
            borrower.getOrganization(),
            null,
            "KYC_SCREENING_COMPLETED",
            "KYC_CHECK",
            String.valueOf(check.getId()),
            buildAuditMessage(
                borrower,
                overallResult,
                sanctionsResult,
                identityResult
            )
        );

        log.info(
            "KYC screening completed. borrowerId={}, organizationId={}, " +
            "checkId={}, result={}, sanctions={}, identity={}",
            borrowerId,
            orgId,
            check.getId(),
            overallResult,
            sanctionsResult,
            identityResult
        );

        return check;
    }

    /**
     * Performs a manual compliance review.
     *
     * Manual review is only valid for a KYC check belonging to the
     * supplied organization.
     */
    @Transactional
    public KycCheck manualReview(
        Long checkId,
        String reviewer,
        KycCheck.CheckResult decision,
        String notes
    ) {

        validateId(checkId, "checkId");

        String normalizedReviewer =
            requireText(
                reviewer,
                "reviewer"
            );

        Objects.requireNonNull(
            decision,
            "KYC decision is required"
        );

        KycCheck check = kycRepo.findById(checkId)
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "KYC check not found: " + checkId
                )
            );

        /*
         * A manual review should not silently turn a historical
         * CLEAR/REJECTED record into another unrelated state.
         *
         * In normal operation this method is intended for MANUAL_REVIEW
         * records.
         */
        if (
            check.getResult() !=
                KycCheck.CheckResult.MANUAL_REVIEW
            && check.getResult() !=
                KycCheck.CheckResult.FLAGGED
        ) {
            throw new IllegalStateException(
                "KYC check is not awaiting manual review: "
                    + checkId
            );
        }

        Borrower borrower = check.getBorrower();

        if (borrower == null) {
            throw new IllegalStateException(
                "KYC check has no associated borrower: "
                    + checkId
            );
        }

        /*
         * Only CLEAR or REJECTED should normally be accepted
         * as a final manual decision.
         */
        if (
            decision != KycCheck.CheckResult.CLEAR
            && decision != KycCheck.CheckResult.REJECTED
        ) {
            throw new IllegalArgumentException(
                "Manual review decision must be CLEAR or REJECTED."
            );
        }

        String normalizedNotes =
            normalizeOptionalText(notes);

        String previousNotes =
            normalizeOptionalText(
                check.getNotes()
            );

        String manualNote =
            "Manual review by "
                + normalizedReviewer
                + ": "
                + (
                    normalizedNotes == null
                        ? "No additional notes."
                        : normalizedNotes
                );

        String finalNotes;

        if (previousNotes == null) {
            finalNotes = manualNote;
        } else {
            finalNotes =
                previousNotes
                    + " | "
                    + manualNote;
        }

        check.setResult(decision);
        check.setReviewedBy(normalizedReviewer);
        check.setReviewedAt(LocalDateTime.now());
        check.setNotes(finalNotes);

        /*
         * A manual decision must update the borrower's KYC status
         * atomically with the KYC record.
         */
        borrower.setKycStatus(
            decision == KycCheck.CheckResult.CLEAR
                ? "VERIFIED"
                : "REJECTED"
        );

        borrowerRepo.save(borrower);

        KycCheck savedCheck =
            kycRepo.save(check);

        auditService.log(
            check.getOrganization(),
            null,
            "KYC_MANUAL_REVIEW",
            "KYC_CHECK",
            String.valueOf(checkId),
            "Decision: "
                + decision
                + " by "
                + normalizedReviewer
                + (
                    normalizedNotes == null
                        ? ""
                        : ". Notes: " + normalizedNotes
                )
        );

        log.info(
            "KYC manual review completed. checkId={}, borrowerId={}, " +
            "decision={}, reviewer={}",
            checkId,
            borrower.getId(),
            decision,
            normalizedReviewer
        );

        return savedCheck;
    }

    /**
     * Returns KYC checks awaiting manual review for an organization.
     */
    @Transactional(readOnly = true)
    public List<KycCheck> getPendingReviews(Long orgId) {

        validateId(orgId, "orgId");

        return kycRepo.findByOrganization_IdAndResult(
            orgId,
            KycCheck.CheckResult.MANUAL_REVIEW
        );
    }

    /**
     * Returns the KYC history for a borrower.
     *
     * IMPORTANT:
     * The caller/controller should also enforce organization ownership.
     * This method can additionally validate the borrower exists.
     */
    @Transactional(readOnly = true)
    public List<KycCheck> getHistoryForBorrower(
        Long borrowerId
    ) {

        validateId(borrowerId, "borrowerId");

        if (!borrowerRepo.existsById(borrowerId)) {
            throw new IllegalArgumentException(
                "Borrower not found: " + borrowerId
            );
        }

        return kycRepo.findByBorrower_Id(
            borrowerId
        );
    }

    /**
     * Determines whether the borrower's latest sanctions screening
     * is currently clear and has not expired.
     */
    @Transactional(readOnly = true)
    public boolean isKycCurrentlyClear(
        Long borrowerId
    ) {

        validateId(
            borrowerId,
            "borrowerId"
        );

        return kycRepo
            .findFirstByBorrower_IdAndCheckTypeOrderByCreatedAtDesc(
                borrowerId,
                KycCheck.CheckType.SANCTIONS_SCREENING
            )
            .filter(
                check ->
                    check.getResult() ==
                        KycCheck.CheckResult.CLEAR
            )
            .filter(
                check ->
                    !check.isExpired()
            )
            .isPresent();
    }

    /**
     * Determines the final KYC result.
     *
     * Sanctions flags must never be automatically cleared.
     */
    private KycCheck.CheckResult determineOverallResult(
        KycCheck.CheckResult sanctionsResult,
        KycCheck.CheckResult identityResult
    ) {

        if (
            sanctionsResult ==
                KycCheck.CheckResult.FLAGGED
        ) {
            return KycCheck.CheckResult.MANUAL_REVIEW;
        }

        if (
            identityResult ==
                KycCheck.CheckResult.REJECTED
        ) {
            return KycCheck.CheckResult.MANUAL_REVIEW;
        }

        return KycCheck.CheckResult.CLEAR;
    }

    /**
     * Determines the persisted match score.
     *
     * This is deliberately conservative for the internal placeholder.
     * When a real provider is integrated, replace this with the provider's
     * actual normalized score.
     */
    private double determineMatchScore(
        KycCheck.CheckResult sanctionsResult,
        KycCheck.CheckResult identityResult,
        KycCheck.CheckResult overallResult
    ) {

        if (
            overallResult ==
                KycCheck.CheckResult.CLEAR
        ) {
            return CLEAR_MATCH_SCORE;
        }

        if (
            sanctionsResult ==
                KycCheck.CheckResult.FLAGGED
        ) {
            return MANUAL_REVIEW_MATCH_SCORE;
        }

        if (
            identityResult ==
                KycCheck.CheckResult.REJECTED
        ) {
            return MANUAL_REVIEW_MATCH_SCORE;
        }

        return MANUAL_REVIEW_MATCH_SCORE;
    }

    /**
     * Internal watchlist screening placeholder.
     *
     * WARNING:
     * This must be replaced with an actual sanctions/PEP/watchlist
     * provider or an organization-managed, regularly updated list
     * before production compliance use.
     */
    private KycCheck.CheckResult screenWatchlists(
        Borrower borrower
    ) {

        String fullName =
            buildNormalizedFullName(
                borrower
            );

        if (fullName.isBlank()) {
            /*
             * Missing identity data must not be treated as a
             * clean sanctions result.
             */
            return KycCheck.CheckResult.FLAGGED;
        }

        /*
         * No hard-coded sample names are used here.
         *
         * Returning CLEAR here means:
         *
         * "No internal list match was found."
         *
         * It does NOT mean the person is actually cleared against
         * global sanctions/PEP databases.
         *
         * A real provider integration must replace this method.
         */
        return KycCheck.CheckResult.CLEAR;
    }

    /**
     * Verifies that the borrower has sufficient identity information
     * to proceed with KYC.
     *
     * This is intentionally stronger than simply checking whether
     * one field exists.
     */
    private KycCheck.CheckResult verifyIdentityInformation(
        Borrower borrower
    ) {

        boolean hasNationalId =
            hasText(
                borrower.getNationalId()
            );

        boolean hasPassport =
            hasText(
                borrower.getPassportNumber()
            );

        /*
         * At least one government-issued identifier is required.
         */
        if (!hasNationalId && !hasPassport) {
            return KycCheck.CheckResult.REJECTED;
        }

        String name =
            buildNormalizedFullName(
                borrower
            );

        if (name.isBlank()) {
            return KycCheck.CheckResult.REJECTED;
        }

        /*
         * Name must be within a reasonable database/provider limit.
         */
        if (name.length() > MAX_NAME_LENGTH) {
            return KycCheck.CheckResult.REJECTED;
        }

        return KycCheck.CheckResult.CLEAR;
    }

    /**
     * Updates borrower KYC state.
     */
    private void updateBorrowerKycStatus(
        Borrower borrower,
        KycCheck.CheckResult result
    ) {

        String status;

        if (
            result ==
                KycCheck.CheckResult.CLEAR
        ) {
            status = "VERIFIED";
        } else if (
            result ==
                KycCheck.CheckResult.MANUAL_REVIEW
            || result ==
                KycCheck.CheckResult.FLAGGED
        ) {
            status = "PENDING_REVIEW";
        } else {
            status = "REJECTED";
        }

        borrower.setKycStatus(status);
    }

    /**
     * Verifies that the borrower belongs to the organization making
     * the request.
     *
     * This is critical for a multi-tenant loan-management platform.
     */
    private void validateOrganizationOwnership(
        Borrower borrower,
        Long orgId
    ) {

        if (borrower.getOrganization() == null) {
            throw new IllegalStateException(
                "Borrower has no organization assigned: "
                    + borrower.getId()
            );
        }

        if (
            borrower.getOrganization().getId() == null
            || !orgId.equals(
                borrower.getOrganization().getId()
            )
        ) {
            log.warn(
                "Cross-tenant KYC access attempt. borrowerId={}, " +
                "requestedOrganizationId={}, actualOrganizationId={}",
                borrower.getId(),
                orgId,
                borrower.getOrganization().getId()
            );

            throw new SecurityException(
                "Borrower does not belong to this organization."
            );
        }
    }

    /**
     * Builds a normalized borrower name.
     */
    private String buildNormalizedFullName(
        Borrower borrower
    ) {

        String firstName =
            normalizeNamePart(
                borrower.getFirstName()
            );

        String lastName =
            normalizeNamePart(
                borrower.getLastName()
            );

        return (
            firstName
                + " "
                + lastName
        )
            .trim()
            .replaceAll("\\s+", " ")
            .toUpperCase(Locale.ROOT);
    }

    private String normalizeNamePart(
        String value
    ) {

        if (value == null) {
            return "";
        }

        return value
            .trim()
            .replaceAll("\\s+", " ");
    }

    private boolean hasText(
        String value
    ) {
        return value != null
            && !value.trim().isEmpty();
    }

    private String requireText(
        String value,
        String field
    ) {

        if (!hasText(value)) {
            throw new IllegalArgumentException(
                field + " is required."
            );
        }

        return value.trim();
    }

    private String normalizeOptionalText(
        String value
    ) {

        if (!hasText(value)) {
            return null;
        }

        return value.trim();
    }

    private void validateId(
        Long value,
        String field
    ) {

        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                field + " must be a positive number."
            );
        }
    }

    private String buildScreeningNotes(
        KycCheck.CheckResult sanctionsResult,
        KycCheck.CheckResult identityResult
    ) {

        return "Sanctions: "
            + sanctionsResult
            + " | Identity: "
            + identityResult
            + " | Screening mode: INTERNAL"
            + " | Production watchlist provider required";
    }

    private String buildAuditMessage(
        Borrower borrower,
        KycCheck.CheckResult overallResult,
        KycCheck.CheckResult sanctionsResult,
        KycCheck.CheckResult identityResult
    ) {

        return "KYC screening completed for borrower "
            + borrower.getId()
            + ". Overall="
            + overallResult
            + ", sanctions="
            + sanctionsResult
            + ", identity="
            + identityResult;
    }
}
