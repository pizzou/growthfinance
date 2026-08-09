package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.CollectionAction;
import com.patrick.fintech.loan_backend.model.CollectionCase;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.CollectionActionRepository;
import com.patrick.fintech.loan_backend.repository.CollectionCaseRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionsService {

    private static final int MONEY_SCALE = 6;

    private static final RoundingMode MONEY_ROUNDING =
            RoundingMode.HALF_UP;

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    MONEY_ROUNDING
            );

    /**
     * Loans considered delinquent by the collections module.
     */
    private static final List<LoanStatus> DELINQUENT_STATUSES =
            List.of(
                    LoanStatus.OVERDUE,
                    LoanStatus.DEFAULTED
            );

    private final CollectionCaseRepository caseRepo;

    private final CollectionActionRepository actionRepo;

    private final LoanRepository loanRepo;

    private final UserRepository userRepo;

    private final AuditService auditService;

    private final AccountingService accountingService;


    // ============================================================
    // MONEY HELPERS
    // ============================================================

    /**
     * Normalizes monetary values used by collections.
     */
    private BigDecimal money(BigDecimal value) {

        if (value == null) {
            return ZERO;
        }

        return value.setScale(
                MONEY_SCALE,
                MONEY_ROUNDING
        );
    }


    /**
     * Converts legacy Double values safely.
     *
     * This method is retained only for compatibility with
     * existing entity fields or callers that still use Double.
     *
     * New financial code should use BigDecimal.
     */
    private BigDecimal money(Double value) {

        if (value == null) {
            return ZERO;
        }

        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "Monetary amount must be finite"
            );
        }

        return money(
                BigDecimal.valueOf(value)
        );
    }


    private BigDecimal money(double value) {

        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "Monetary amount must be finite"
            );
        }

        return money(
                BigDecimal.valueOf(value)
        );
    }


    private boolean isZeroOrLess(
            BigDecimal value
    ) {

        return money(value).compareTo(
                ZERO
        ) <= 0;
    }


    private boolean isEffectivelyCleared(
            BigDecimal value
    ) {

        /*
         * Six-decimal accounting precision means that a balance
         * rounded to zero is considered cleared.
         */
        return money(value).compareTo(
                ZERO
        ) == 0;
    }


    private String safeText(
            String value,
            String fallback
    ) {

        if (value == null || value.isBlank()) {
            return fallback;
        }

        return value.trim();
    }


    // ============================================================
    // SYNC DELINQUENT LOANS
    // ============================================================

    /**
     * Scans overdue/defaulted loans and creates or refreshes
     * collection cases.
     *
     * This method is intended for scheduled execution.
     *
     * Important:
     * - Resolved cases are not automatically reopened.
     * - Written-off cases are not automatically reopened.
     * - Existing active cases are refreshed.
     * - Money is normalized to BigDecimal.
     */
    @Transactional
    public int syncCasesFromOverdueLoans() {

        int touched = 0;

        List<Loan> delinquentLoans =
                loanRepo.findByStatusIn(
                        DELINQUENT_STATUSES
                );

        if (delinquentLoans == null
                || delinquentLoans.isEmpty()) {

            return 0;
        }


        for (Loan loan : delinquentLoans) {

            if (loan == null
                    || loan.getId() == null) {

                continue;
            }


            if (loan.getOrganization() == null
                    || loan.getOrganization().getId() == null) {

                log.warn(
                        "Skipping delinquent loan {} because organization is missing",
                        loan.getId()
                );

                continue;
            }


            CollectionCase existingCase =
                    caseRepo
                            .findByLoan_Id(
                                    loan.getId()
                            )
                            .orElse(null);


            int daysPastDue =
                    loan.getDaysOverdue() != null
                            ? Math.max(
                                    loan.getDaysOverdue(),
                                    0
                            )
                            : 0;


            CollectionCase.CollectionBucket bucket =
                    bucketFor(
                            daysPastDue
                    );


            boolean isNew =
                    existingCase == null;


            CollectionCase collectionCase;


            if (isNew) {

                collectionCase =
                        CollectionCase.builder()
                                .loan(loan)
                                .organization(
                                        loan.getOrganization()
                                )
                                .bucket(bucket)
                                .status(
                                        CollectionCase.CollectionStatus.OPEN
                                )
                                .priority(
                                        priorityFor(bucket)
                                )
                                .build();

            } else {

                collectionCase =
                        existingCase;


                /*
                 * Closed collection cases are intentionally
                 * not reopened automatically.
                 */
                if (
                        collectionCase.getStatus()
                                == CollectionCase.CollectionStatus.RESOLVED

                                ||

                        collectionCase.getStatus()
                                == CollectionCase.CollectionStatus.WRITTEN_OFF
                ) {

                    continue;
                }


                collectionCase.setBucket(
                        bucket
                );

                collectionCase.setPriority(
                        priorityFor(bucket)
                );
            }


            collectionCase.setDaysPastDue(
                    daysPastDue
            );


            /*
             * IMPORTANT:
             *
             * Your current implementation uses outstandingBalance
             * for overdueAmount.
             *
             * That is only correct if your Loan model defines the
             * entire outstanding balance as overdue.
             *
             * Since the exact Loan overdue-principal/interest fields
             * are not available here, we preserve the existing
             * behavior rather than inventing an entity property.
             */
            BigDecimal outstanding =
                    money(
                            loan.getOutstandingBalance()
                    );


            collectionCase.setOverdueAmount(
                    outstanding
            );

            collectionCase.setTotalOutstanding(
                    outstanding
            );


            collectionCase =
                    caseRepo.save(
                            collectionCase
                    );


            if (isNew) {

                logAction(
                        collectionCase.getId(),
                        CollectionAction.ActionType.CASE_OPENED,
                        "Auto-opened: loan is "
                                + daysPastDue
                                + " day(s) past due",
                        "SYSTEM",
                        null,
                        null,
                        null
                );
            }


            touched++;
        }


        log.info(
                "Collection synchronization completed. {} case(s) touched.",
                touched
        );


        return touched;
    }


    // ============================================================
    // COLLECTION QUEUE
    // ============================================================

    /**
     * Returns the collection queue for an organization.
     *
     * Organization ID is mandatory to maintain tenant isolation.
     */
    @Transactional(readOnly = true)
    public List<CollectionCase> getQueue(
            Long orgId,
            CollectionCase.CollectionBucket bucket,
            CollectionCase.CollectionStatus status,
            Long agentId
    ) {

        requireOrganizationId(
                orgId
        );


        List<CollectionCase> cases =
                caseRepo.findByOrganization_Id(
                        orgId
                );


        if (cases == null
                || cases.isEmpty()) {

            return List.of();
        }


        return cases.stream()

                .filter(
                        Objects::nonNull
                )

                .filter(
                        c ->
                                bucket == null
                                        || c.getBucket() == bucket
                )

                .filter(
                        c ->
                                status == null
                                        || c.getStatus() == status
                )

                .filter(
                        c ->
                                agentId == null
                                        ||
                                        (
                                                c.getAssignedAgent() != null
                                                        &&
                                                agentId.equals(
                                                        c.getAssignedAgent().getId()
                                                )
                                        )
                )

                .sorted(
                        Comparator
                                .comparing(
                                        (
                                                CollectionCase c
                                        ) ->
                                                c.getDaysPastDue() == null
                                                        ? 0
                                                        : c.getDaysPastDue()
                                )
                                .reversed()
                )

                .toList();
    }


    // ============================================================
    // GET CASE
    // ============================================================

    /**
     * Gets a collection case without organization filtering.
     *
     * Retained for compatibility with existing callers.
     *
     * Tenant-sensitive controllers should prefer getCaseForOrg().
     */
    @Transactional(readOnly = true)
    public CollectionCase getCase(
            Long caseId
    ) {

        requireId(
                caseId,
                "Collection case ID"
        );


        return caseRepo
                .findById(
                        caseId
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Collection case not found: "
                                                + caseId
                                )
                );
    }


    /**
     * Production-safe tenant-scoped case lookup.
     */
    @Transactional(readOnly = true)
    public CollectionCase getCaseForOrg(
            Long caseId,
            Long orgId
    ) {

        requireId(
                caseId,
                "Collection case ID"
        );

        requireOrganizationId(
                orgId
        );


        CollectionCase collectionCase =
                getCase(
                        caseId
                );


        if (
                collectionCase.getOrganization() == null
                        ||
                collectionCase.getOrganization().getId() == null
        ) {

            throw new IllegalStateException(
                    "Collection case has no organization: "
                            + caseId
            );
        }


        if (
                !orgId.equals(
                        collectionCase
                                .getOrganization()
                                .getId()
                )
        ) {

            /*
             * Do not reveal whether another tenant's case exists.
             */
            throw new IllegalArgumentException(
                    "Collection case not found: "
                            + caseId
            );
        }


        return collectionCase;
    }


    // ============================================================
    // ASSIGN AGENT
    // ============================================================

    /**
     * Assigns a collection case to an agent.
     *
     * Agent must belong to the same organization as the case.
     */
    @Transactional
    public CollectionCase assignAgent(
            Long caseId,
            Long agentUserId,
            String assignedBy
    ) {

        requireId(
                caseId,
                "Collection case ID"
        );

        requireId(
                agentUserId,
                "Agent user ID"
        );


        CollectionCase collectionCase =
                getCase(
                        caseId
                );


        if (
                collectionCase.getOrganization() == null
                        ||
                collectionCase
                        .getOrganization()
                        .getId() == null
        ) {

            throw new IllegalStateException(
                    "Collection case has no organization"
            );
        }


        Long organizationId =
                collectionCase
                        .getOrganization()
                        .getId();


        User agent =
                userRepo
                        .findById(
                                agentUserId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Agent not found: "
                                                        + agentUserId
                                        )
                        );


        /*
         * Tenant isolation.
         *
         * We do not allow an agent belonging to another
         * organization to be assigned to this case.
         *
         * This assumes User has getOrganization(), which is
         * consistent with the multi-tenant architecture.
         */
        if (
                agent.getOrganization() == null
                        ||
                agent.getOrganization().getId() == null
        ) {

            throw new IllegalStateException(
                    "Agent has no organization: "
                            + agentUserId
            );
        }


        if (
                !organizationId.equals(
                        agent
                                .getOrganization()
                                .getId()
                )
        ) {

            throw new IllegalArgumentException(
                    "Agent does not belong to the same organization"
            );
        }


        collectionCase.setAssignedAgent(
                agent
        );


        if (
                collectionCase.getStatus()
                        == CollectionCase.CollectionStatus.OPEN
        ) {

            collectionCase.setStatus(
                    CollectionCase.CollectionStatus.IN_PROGRESS
            );
        }


        collectionCase =
                caseRepo.save(
                        collectionCase
                );


        String actor =
                safeText(
                        assignedBy,
                        "SYSTEM"
                );


        auditService.log(
                collectionCase.getOrganization(),
                null,
                "COLLECTION_CASE_ASSIGNED",
                "COLLECTION_CASE",
                String.valueOf(caseId),
                "Assigned to "
                        + safeText(
                                agent.getName(),
                                "agent"
                        )
                        + " by "
                        + actor
        );


        return collectionCase;
    }


    // ============================================================
    // LOG COLLECTION ACTION
    // ============================================================

    /**
     * Records a collection action and updates the case state.
     *
     * This is transactional because the action, collection case,
     * loan write-off and accounting entry must remain consistent.
     */
    @Transactional
    public CollectionAction logAction(
            Long caseId,
            CollectionAction.ActionType type,
            String notes,
            String performedBy,
            String outcome,
            LocalDate promiseDate,
            Double promiseAmount
    ) {

        requireId(
                caseId,
                "Collection case ID"
        );


        if (type == null) {

            throw new IllegalArgumentException(
                    "Collection action type is required"
            );
        }


        CollectionCase collectionCase =
                getCase(
                        caseId
                );


        if (
                collectionCase.getOrganization() == null
                        ||
                collectionCase
                        .getOrganization()
                        .getId() == null
        ) {

            throw new IllegalStateException(
                    "Collection case has no organization"
            );
        }


        String actor =
                safeText(
                        performedBy,
                        "SYSTEM"
                );


        String safeNotes =
                notes != null
                        ? notes.trim()
                        : null;


        String safeOutcome =
                outcome != null
                        ? outcome.trim()
                        : null;


        /*
         * Promise-to-pay validation.
         */
        if (
                type
                        == CollectionAction.ActionType.PROMISE_TO_PAY
        ) {

            if (promiseDate == null) {

                throw new IllegalArgumentException(
                        "Promise-to-pay date is required"
                );
            }


            BigDecimal promise =
                    money(
                            promiseAmount
                    );


            if (
                    promise.compareTo(
                            ZERO
                    ) <= 0
            ) {

                throw new IllegalArgumentException(
                        "Promise-to-pay amount must be greater than zero"
                );
            }


            if (
                    promiseDate.isBefore(
                            LocalDate.now()
                    )
            ) {

                throw new IllegalArgumentException(
                        "Promise-to-pay date cannot be in the past"
                );
            }
        }


        /*
         * Do not allow operational actions on cases that are
         * already written off.
         */
        if (
                collectionCase.getStatus()
                        == CollectionCase.CollectionStatus.WRITTEN_OFF
                &&
                type
                        != CollectionAction.ActionType.CASE_CLOSED
        ) {

            throw new IllegalStateException(
                    "Cannot add this action to a written-off collection case"
            );
        }


        /*
         * Do not create another write-off for a case that has
         * already been written off.
         *
         * This is an important protection against duplicate
         * accounting entries.
         */
        if (
                type
                        == CollectionAction.ActionType.WRITE_OFF
                &&
                collectionCase.getStatus()
                        == CollectionCase.CollectionStatus.WRITTEN_OFF
        ) {

            throw new IllegalStateException(
                    "Collection case has already been written off"
            );
        }


        BigDecimal normalizedPromiseAmount =
                promiseAmount == null
                        ? null
                        : money(
                                promiseAmount
                        );


        CollectionAction action =
                CollectionAction.builder()
                        .collectionCase(collectionCase)
                        .actionType(type)
                        .notes(safeNotes)
                        .performedBy(actor)
                        .outcome(safeOutcome)
                        .promiseDate(promiseDate)
                        .promiseAmount(
                                normalizedPromiseAmount != null
                                        ? normalizedPromiseAmount.doubleValue()
                                        : null
                        )
                        .build();


        action =
                actionRepo.save(
                        action
                );


        collectionCase.setLastContactDate(
                LocalDate.now()
        );


        // ========================================================
        // STATUS TRANSITIONS
        // ========================================================

        switch (type) {

            case PROMISE_TO_PAY -> {

                BigDecimal promise =
                        money(
                                promiseAmount
                        );


                collectionCase.setStatus(
                        CollectionCase.CollectionStatus.PROMISE_TO_PAY
                );


                collectionCase.setPromiseToPayDate(
                        promiseDate
                );


                collectionCase.setPromiseToPayAmount(
                        promise.doubleValue()
                );


                collectionCase.setNextActionDate(
                        promiseDate
                );
            }


            case ESCALATED -> {

                collectionCase.setStatus(
                        CollectionCase.CollectionStatus.ESCALATED
                );
            }


            case LEGAL_NOTICE -> {

                collectionCase.setStatus(
                        CollectionCase.CollectionStatus.LEGAL
                );
            }


            case PAYMENT_RECEIVED -> {

                Loan loan =
                        collectionCase.getLoan();


                if (loan == null) {

                    throw new IllegalStateException(
                            "Collection case has no loan"
                    );
                }


                BigDecimal outstanding =
                        money(
                                loan.getOutstandingBalance()
                        );


                boolean cleared =
                        isEffectivelyCleared(
                                outstanding
                        );


                if (cleared) {

                    collectionCase.setStatus(
                            CollectionCase.CollectionStatus.RESOLVED
                    );


                    collectionCase.setClosedAt(
                            LocalDateTime.now()
                    );


                    collectionCase.setNextActionDate(
                            null
                    );

                } else if (
                        collectionCase.getStatus()
                                == CollectionCase.CollectionStatus.PROMISE_TO_PAY
                ) {

                    collectionCase.setStatus(
                            CollectionCase.CollectionStatus.IN_PROGRESS
                    );
                }
            }


            case WRITE_OFF -> {

                Loan loan =
                        collectionCase.getLoan();


                if (loan == null) {

                    throw new IllegalStateException(
                            "Cannot write off collection case without a loan"
                    );
                }


                if (
                        loan.getId() == null
                ) {

                    throw new IllegalStateException(
                            "Cannot write off loan without an ID"
                    );
                }


                /*
                 * The loan status is changed before accounting
                 * so the transaction can roll back both changes
                 * if accounting fails.
                 */
                loan.setStatus(
                        LoanStatus.WRITTEN_OFF
                );


                loanRepo.save(
                        loan
                );


                collectionCase.setStatus(
                        CollectionCase.CollectionStatus.WRITTEN_OFF
                );


                collectionCase.setBucket(
                        CollectionCase.CollectionBucket.WRITE_OFF
                );


                collectionCase.setClosedAt(
                        LocalDateTime.now()
                );


                collectionCase.setResolutionNotes(
                        safeNotes
                );


                collectionCase.setNextActionDate(
                        null
                );


                /*
                 * Accounting write-off.
                 *
                 * This MUST be idempotent in AccountingService.
                 * If postWriteOff() can create duplicates when called
                 * twice, that service should be protected with a
                 * unique reference/idempotency check.
                 */
                accountingService.postWriteOff(
                        loan
                );
            }


            case CASE_CLOSED -> {

                collectionCase.setStatus(
                        CollectionCase.CollectionStatus.RESOLVED
                );


                collectionCase.setClosedAt(
                        LocalDateTime.now()
                );


                collectionCase.setResolutionNotes(
                        safeNotes
                );


                collectionCase.setNextActionDate(
                        null
                );
            }


            case CALL,
                 SMS,
                 EMAIL,
                 FIELD_VISIT,
                 CASE_OPENED -> {

                /*
                 * Contact/action-only events.
                 *
                 * Do not change the case status automatically.
                 */
            }


            default -> {

                log.debug(
                        "No explicit collection status transition for action {}",
                        type
                );
            }
        }


        collectionCase =
                caseRepo.save(
                        collectionCase
                );


        /*
         * Audit after all business changes have succeeded.
         *
         * Because this method is transactional, a failure later
         * causes the entire transaction to roll back.
         */
        auditService.log(
                collectionCase.getOrganization(),
                null,
                "COLLECTION_ACTION_" + type.name(),
                "COLLECTION_CASE",
                String.valueOf(caseId),
                type.name()
                        + " logged by "
                        + actor
                        + (
                                safeNotes != null
                                        ? ": " + safeNotes
                                        : ""
                        )
        );


        return action;
    }


    // ============================================================
    // ACTION HISTORY
    // ============================================================

    @Transactional(readOnly = true)
    public List<CollectionAction> getActions(
            Long caseId
    ) {

        requireId(
                caseId,
                "Collection case ID"
        );


        List<CollectionAction> actions =
                actionRepo
                        .findByCollectionCase_IdOrderByCreatedAtDesc(
                                caseId
                        );


        if (actions == null
                || actions.isEmpty()) {

            return List.of();
        }


        return actions;
    }


    // ============================================================
    // STATS
    // ============================================================

    /**
     * Returns collection statistics for one organization.
     *
     * BigDecimal is used for all monetary calculations.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStats(
            Long orgId
    ) {

        requireOrganizationId(
                orgId
        );


        List<CollectionCase> cases =
                caseRepo.findByOrganization_Id(
                        orgId
                );


        if (cases == null) {
            cases = List.of();
        }


        Map<CollectionCase.CollectionBucket, Long>
                bucketCounts =
                new EnumMap<>(
                        CollectionCase.CollectionBucket.class
                );


        Map<CollectionCase.CollectionBucket, BigDecimal>
                bucketAmounts =
                new EnumMap<>(
                        CollectionCase.CollectionBucket.class
                );


        for (
                CollectionCase.CollectionBucket bucket
                :
                CollectionCase.CollectionBucket.values()
        ) {

            bucketCounts.put(
                    bucket,
                    0L
            );


            bucketAmounts.put(
                    bucket,
                    ZERO
            );
        }


        BigDecimal totalOverdue =
                ZERO;


        long activePromises =
                0L;


        long totalOpenCases =
                0L;


        for (
                CollectionCase collectionCase
                :
                cases
        ) {

            if (collectionCase == null) {
                continue;
            }


            CollectionCase.CollectionStatus status =
                    collectionCase.getStatus();


            if (
                    status
                            == CollectionCase.CollectionStatus.WRITTEN_OFF
            ) {

                continue;
            }


            if (
                    status != CollectionCase.CollectionStatus.RESOLVED
            ) {

                totalOpenCases++;
            }


            CollectionCase.CollectionBucket bucket =
                    collectionCase.getBucket();


            if (bucket != null) {

                bucketCounts.merge(
                        bucket,
                        1L,
                        Long::sum
                );


                BigDecimal amount =
                        money(
                                collectionCase
                                        .getOverdueAmount()
                        );


                bucketAmounts.merge(
                        bucket,
                        amount,
                        BigDecimal::add
                );


                totalOverdue =
                        totalOverdue.add(
                                amount
                        );
            }


            if (
                    status
                            == CollectionCase.CollectionStatus.PROMISE_TO_PAY
            ) {

                activePromises++;
            }
        }


        /*
         * Convert maps to String-keyed maps for predictable JSON.
         */
        Map<String, Long> casesByBucket =
                new LinkedHashMap<>();


        Map<String, BigDecimal> overdueAmountByBucket =
                new LinkedHashMap<>();


        for (
                CollectionCase.CollectionBucket bucket
                :
                CollectionCase.CollectionBucket.values()
        ) {

            casesByBucket.put(
                    bucket.name(),
                    bucketCounts.getOrDefault(
                            bucket,
                            0L
                    )
            );


            overdueAmountByBucket.put(
                    bucket.name(),
                    money(
                            bucketAmounts.getOrDefault(
                                    bucket,
                                    ZERO
                            )
                    )
            );
        }


        Map<String, Object> stats =
                new LinkedHashMap<>();


        stats.put(
                "casesByBucket",
                casesByBucket
        );


        stats.put(
                "overdueAmountByBucket",
                overdueAmountByBucket
        );


        stats.put(
                "totalOpenCases",
                totalOpenCases
        );


        stats.put(
                "totalOverdueAmount",
                money(
                        totalOverdue
                )
        );


        stats.put(
                "activePromises",
                activePromises
        );


        return stats;
    }


    // ============================================================
    // BUCKET CALCULATION
    // ============================================================

    private CollectionCase.CollectionBucket bucketFor(
            int dpd
    ) {

        if (dpd <= 0) {

            return CollectionCase.CollectionBucket.CURRENT;
        }


        if (dpd <= 30) {

            return CollectionCase.CollectionBucket.DPD_1_30;
        }


        if (dpd <= 60) {

            return CollectionCase.CollectionBucket.DPD_31_60;
        }


        if (dpd <= 90) {

            return CollectionCase.CollectionBucket.DPD_61_90;
        }


        return CollectionCase.CollectionBucket.DPD_90_PLUS;
    }


    // ============================================================
    // PRIORITY
    // ============================================================

    private CollectionCase.Priority priorityFor(
            CollectionCase.CollectionBucket bucket
    ) {

        if (bucket == null) {

            return CollectionCase.Priority.LOW;
        }


        return switch (bucket) {

            case CURRENT,
                 DPD_1_30 ->
                    CollectionCase.Priority.LOW;


            case DPD_31_60 ->
                    CollectionCase.Priority.MEDIUM;


            case DPD_61_90 ->
                    CollectionCase.Priority.HIGH;


            case DPD_90_PLUS,
                 WRITE_OFF ->
                    CollectionCase.Priority.URGENT;
        };
    }


    // ============================================================
    // VALIDATION HELPERS
    // ============================================================

    private void requireId(
            Long id,
            String field
    ) {

        if (id == null
                || id <= 0) {

            throw new IllegalArgumentException(
                    field + " is required"
            );
        }
    }


    private void requireOrganizationId(
            Long orgId
    ) {

        if (orgId == null
                || orgId <= 0) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }
    }
}