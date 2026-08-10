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

    /*
     * Six decimal places are retained internally so that
     * collection calculations do not lose precision.
     *
     * Database/entity financial fields may still use their
     * own configured scale.
     */
    private static final int MONEY_SCALE = 6;

    private static final RoundingMode MONEY_ROUNDING =
            RoundingMode.HALF_UP;

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    MONEY_ROUNDING
            );

    /**
     * Loans handled by the collections module.
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
    // MONEY
    // ============================================================

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
     * Compatibility conversion for legacy Double entity fields.
     *
     * No financial calculation should be performed using Double.
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


    private boolean isZeroOrLess(BigDecimal value) {

        return money(value).compareTo(ZERO) <= 0;
    }


    private boolean isEffectivelyCleared(BigDecimal value) {

        return money(value).compareTo(ZERO) == 0;
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
     * Synchronizes overdue/defaulted loans into collection cases.
     *
     * Rules:
     *
     * - OVERDUE and DEFAULTED loans are considered delinquent.
     * - Existing active cases are refreshed.
     * - RESOLVED cases are never automatically reopened.
     * - WRITTEN_OFF cases are never automatically reopened.
     * - Organization is always copied from the loan.
     * - Collection bucket is derived from days overdue.
     */
    @Transactional
    public int syncCasesFromOverdueLoans() {

        List<Loan> delinquentLoans =
                loanRepo.findByStatusIn(
                        DELINQUENT_STATUSES
                );

        if (delinquentLoans == null
                || delinquentLoans.isEmpty()) {

            log.info(
                    "Collection synchronization completed. No delinquent loans found."
            );

            return 0;
        }

        int touched = 0;

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

            CollectionCase collectionCase =
                    caseRepo
                            .findByLoan_Id(
                                    loan.getId()
                            )
                            .orElse(null);

            int daysPastDue =
                    loan.getDaysOverdue() == null
                            ? 0
                            : Math.max(
                                    loan.getDaysOverdue(),
                                    0
                            );

            CollectionCase.CollectionBucket bucket =
                    bucketFor(daysPastDue);

            boolean isNew =
                    collectionCase == null;

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
                                .daysPastDue(
                                        daysPastDue
                                )
                                .build();

            } else {

                /*
                 * A resolved or written-off case is a terminal
                 * collection state and must not be reopened
                 * automatically.
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

                collectionCase.setBucket(bucket);

                collectionCase.setPriority(
                        priorityFor(bucket)
                );

                collectionCase.setDaysPastDue(
                        daysPastDue
                );
            }

            /*
             * Current Loan model exposes outstandingBalance.
             *
             * Until Loan exposes a separate contractual overdue
             * amount, this value is used as the collection amount.
             *
             * It must NOT be interpreted as a separate overdue
             * principal/interest calculation.
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
     * Returns collection cases for one organization.
     *
     * Organization ID is mandatory.
     */
    @Transactional(readOnly = true)
    public List<CollectionCase> getQueue(
            Long orgId,
            CollectionCase.CollectionBucket bucket,
            CollectionCase.CollectionStatus status,
            Long agentId
    ) {

        requireOrganizationId(orgId);

        List<CollectionCase> cases =
                caseRepo.findByOrganization_Id(
                        orgId
                );

        if (cases == null
                || cases.isEmpty()) {

            return List.of();
        }

        return cases.stream()
                .filter(Objects::nonNull)

                .filter(
                        collectionCase ->
                                bucket == null
                                        ||
                                collectionCase.getBucket() == bucket
                )

                .filter(
                        collectionCase ->
                                status == null
                                        ||
                                collectionCase.getStatus() == status
                )

                .filter(
                        collectionCase ->
                                agentId == null
                                        ||
                                (
                                        collectionCase.getAssignedAgent() != null
                                                &&
                                        agentId.equals(
                                                collectionCase
                                                        .getAssignedAgent()
                                                        .getId()
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
    // CASE LOOKUP
    // ============================================================

    /**
     * Legacy lookup.
     *
     * Internal use only.
     *
     * Tenant-sensitive controllers should use getCaseForOrg().
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
                .findById(caseId)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Collection case not found: "
                                                + caseId
                                )
                );
    }


    /**
     * Production-safe organization-scoped lookup.
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

        requireOrganizationId(orgId);

        CollectionCase collectionCase =
                getCase(caseId);

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
             * Do not disclose that a case belonging to another
             * tenant exists.
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
                getCase(caseId);

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

        if (
                collectionCase.getStatus()
                        == CollectionCase.CollectionStatus.WRITTEN_OFF
        ) {

            throw new IllegalStateException(
                    "Cannot assign an agent to a written-off case"
            );
        }

        if (
                collectionCase.getStatus()
                        == CollectionCase.CollectionStatus.RESOLVED
        ) {

            throw new IllegalStateException(
                    "Cannot assign an agent to a resolved case"
            );
        }

        Long organizationId =
                collectionCase
                        .getOrganization()
                        .getId();

        User agent =
                userRepo
                        .findById(agentUserId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Agent not found: "
                                                        + agentUserId
                                        )
                        );

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

        collectionCase.setAssignedAgent(agent);

        if (
                collectionCase.getStatus()
                        == CollectionCase.CollectionStatus.OPEN
        ) {

            collectionCase.setStatus(
                    CollectionCase.CollectionStatus.IN_PROGRESS
            );
        }

        collectionCase =
                caseRepo.save(collectionCase);

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
                getCase(caseId);

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
                notes == null
                        ? null
                        : notes.trim();

        String safeOutcome =
                outcome == null
                        ? null
                        : outcome.trim();

        // --------------------------------------------------------
        // Promise-to-pay validation
        // --------------------------------------------------------

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
                    money(promiseAmount);

            if (
                    promise.compareTo(ZERO) <= 0
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

        // --------------------------------------------------------
        // Terminal state protection
        // --------------------------------------------------------

        if (
                collectionCase.getStatus()
                        == CollectionCase.CollectionStatus.WRITTEN_OFF
        ) {

            throw new IllegalStateException(
                    "Cannot add actions to a written-off collection case"
            );
        }

        if (
                collectionCase.getStatus()
                        == CollectionCase.CollectionStatus.RESOLVED
                &&
                type
                        != CollectionAction.ActionType.CASE_CLOSED
        ) {

            throw new IllegalStateException(
                    "Cannot add operational actions to a resolved collection case"
            );
        }

        BigDecimal normalizedPromiseAmount =
                promiseAmount == null
                        ? null
                        : money(promiseAmount);

        /*
         * Persist the action only after validation.
         *
         * All subsequent business changes occur in the same
         * transaction.
         */
        CollectionAction action =
                CollectionAction.builder()
                        .collectionCase(collectionCase)
                        .actionType(type)
                        .notes(safeNotes)
                        .performedBy(actor)
                        .outcome(safeOutcome)
                        .promiseDate(promiseDate)
                        .promiseAmount(
                                normalizedPromiseAmount == null
                                        ? null
                                        : normalizedPromiseAmount.doubleValue()
                        )
                        .build();

        // --------------------------------------------------------
        // CASE STATE TRANSITIONS
        // --------------------------------------------------------

        collectionCase.setLastContactDate(
                LocalDate.now()
        );

        switch (type) {

            case PROMISE_TO_PAY -> {

                BigDecimal promise =
                        money(promiseAmount);

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

                /*
                 * A PAYMENT_RECEIVED action does not itself mean
                 * the loan is fully paid.
                 *
                 * The actual loan balance is authoritative.
                 */
                if (
                        isEffectivelyCleared(outstanding)
                ) {

                    collectionCase.setStatus(
                            CollectionCase.CollectionStatus.RESOLVED
                    );

                    collectionCase.setClosedAt(
                            LocalDateTime.now()
                    );

                    collectionCase.setNextActionDate(
                            null
                    );

                } else {

                    /*
                     * A partially paid delinquent loan remains
                     * in collections.
                     */
                    if (
                            collectionCase.getStatus()
                                    == CollectionCase.CollectionStatus.PROMISE_TO_PAY
                    ) {

                        collectionCase.setStatus(
                                CollectionCase.CollectionStatus.IN_PROGRESS
                        );
                    }
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

                if (loan.getId() == null) {

                    throw new IllegalStateException(
                            "Cannot write off loan without an ID"
                    );
                }

                if (
                        loan.getStatus()
                                == LoanStatus.WRITTEN_OFF
                ) {

                    throw new IllegalStateException(
                            "Loan has already been written off"
                    );
                }

                /*
                 * Change loan state inside the same transaction
                 * as accounting and collection state.
                 */
                loan.setStatus(
                        LoanStatus.WRITTEN_OFF
                );

                loanRepo.save(loan);

                collectionCase.setStatus(
                        CollectionCase.CollectionStatus.WRITTEN_OFF
                );

                collectionCase.setBucket(
                        CollectionCase.CollectionBucket.WRITE_OFF
                );

                collectionCase.setPriority(
                        CollectionCase.Priority.URGENT
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
                 * AccountingService MUST make this operation
                 * idempotent using a unique business reference.
                 *
                 * The database transaction protects the loan and
                 * collection state, while AccountingService must
                 * protect against duplicate journal entries.
                 */
                accountingService.postWriteOff(loan);
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
                 * Contact events do not automatically change
                 * the collection status.
                 */
            }


            default -> {

                log.debug(
                        "No explicit collection transition for action {}",
                        type
                );
            }
        }

        /*
         * Save the collection case before returning.
         */
        collectionCase =
                caseRepo.save(
                        collectionCase
                );

        /*
         * Save the action after business validation and state
         * transition have succeeded.
         *
         * If accounting or another transactional operation fails,
         * the entire transaction rolls back.
         */
        action =
                actionRepo.save(action);

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

    /**
     * Legacy method.
     *
     * Prefer getActionsForOrg() for tenant-sensitive endpoints.
     */
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

        if (
                actions == null
                        || actions.isEmpty()
        ) {

            return List.of();
        }

        return actions;
    }


    /**
     * Production-safe tenant-scoped action history.
     */
    @Transactional(readOnly = true)
    public List<CollectionAction> getActionsForOrg(
            Long caseId,
            Long orgId
    ) {

        /*
         * This verifies tenant ownership before querying
         * collection history.
         */
        getCaseForOrg(
                caseId,
                orgId
        );

        return getActions(caseId);
    }


    // ============================================================
    // STATISTICS
    // ============================================================

    @Transactional(readOnly = true)
    public Map<String, Object> getStats(
            Long orgId
    ) {

        requireOrganizationId(orgId);

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

            /*
             * Written-off balances are excluded from active
             * collections statistics.
             */
            if (
                    status
                            == CollectionCase.CollectionStatus.WRITTEN_OFF
            ) {

                continue;
            }

            if (
                    status
                            != CollectionCase.CollectionStatus.RESOLVED
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
                money(totalOverdue)
        );

        stats.put(
                "activePromises",
                activePromises
        );

        return stats;
    }


    // ============================================================
    // BUCKET
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
    // VALIDATION
    // ============================================================

    private void requireId(
            Long id,
            String field
    ) {

        if (
                id == null
                        || id <= 0
        ) {

            throw new IllegalArgumentException(
                    field + " is required"
            );
        }
    }


    private void requireOrganizationId(
            Long orgId
    ) {

        if (
                orgId == null
                        || orgId <= 0
        ) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }
    }
}