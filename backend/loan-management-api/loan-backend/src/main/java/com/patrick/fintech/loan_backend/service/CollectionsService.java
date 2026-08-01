package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionsService {

    private final CollectionCaseRepository caseRepo;
    private final CollectionActionRepository actionRepo;
    private final LoanRepository loanRepo;
    private final UserRepository userRepo;
    private final AuditService auditService;
    private final AccountingService accountingService;
    private final LoanClassificationService loanClassificationService;

    private static final List<LoanStatus> DELINQUENT_STATUSES =
            List.of(
                    LoanStatus.OVERDUE,
                    LoanStatus.DEFAULTED
            );

    private static final BigDecimal CLEARANCE_THRESHOLD =
            BigDecimal.valueOf(0.01);


    @Transactional
    public int syncCasesFromOverdueLoans() {

        int touched = 0;

        List<Loan> loans =
                loanRepo.findByStatusIn(DELINQUENT_STATUSES);

        for (Loan loan : loans) {

            CollectionCase collectionCase =
                    caseRepo.findByLoan_Id(loan.getId())
                            .orElse(null);

            int dpd =
                    loan.getDaysOverdue() != null
                            ? loan.getDaysOverdue()
                            : 0;

            CollectionCase.CollectionBucket bucket =
                    bucketFor(dpd);

            boolean isNew =
                    collectionCase == null;

          
            if (isNew) {

                collectionCase =
                        CollectionCase.builder()
                                .loan(loan)
                                .organization(loan.getOrganization())
                                .bucket(bucket)
                                .status(
                                        CollectionCase.CollectionStatus.OPEN
                                )
                                .priority(
                                        priorityFor(bucket)
                                )
                                .build();

            }

            else if (
                    collectionCase.getStatus()
                            == CollectionCase.CollectionStatus.RESOLVED
                            ||
                    collectionCase.getStatus()
                            == CollectionCase.CollectionStatus.WRITTEN_OFF
            ) {

               
                continue;

            }

            else {

                collectionCase.setBucket(bucket);

                collectionCase.setPriority(
                        priorityFor(bucket)
                );
            }

           

            collectionCase.setDaysPastDue(dpd);

            BigDecimal outstanding =
                    loan.getOutstandingBalance() != null
                            ? loan.getOutstandingBalance()
                            : BigDecimal.ZERO;

            collectionCase.setOverdueAmount(
                    outstanding
            );

            collectionCase.setTotalOutstanding(
                    outstanding
            );

            collectionCase =
                    caseRepo.save(collectionCase);


            /*
             * =====================================================
             * LOG CASE OPENING
             * =====================================================
             */

            if (isNew) {

                logAction(
                        collectionCase.getId(),
                        CollectionAction.ActionType.CASE_OPENED,
                        "Auto-opened: loan is "
                                + dpd
                                + " day(s) past due",
                        "SYSTEM",
                        null,
                        null,
                        null
                );
            }

            touched++;
        }

        return touched;
    }


    // ============================================================
    // COLLECTION QUEUE
    // ============================================================

    public List<CollectionCase> getQueue(
            Long orgId,
            CollectionCase.CollectionBucket bucket,
            CollectionCase.CollectionStatus status,
            Long agentId) {

        List<CollectionCase> cases =
                caseRepo.findByOrganization_Id(orgId);

        return cases.stream()

                .filter(c ->
                        bucket == null
                                || c.getBucket() == bucket
                )

                .filter(c ->
                        status == null
                                || c.getStatus() == status
                )

                .filter(c ->
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
                        Comparator.comparing(
                                (CollectionCase c) ->
                                        c.getDaysPastDue() == null
                                                ? 0
                                                : c.getDaysPastDue()
                        ).reversed()
                )

                .toList();
    }


    // ============================================================
    // GET CASE
    // ============================================================

    public CollectionCase getCase(Long caseId) {

        return caseRepo.findById(caseId)

                .orElseThrow(
                        () ->
                                new RuntimeException(
                                        "Collection case not found: "
                                                + caseId
                                )
                );
    }


    // ============================================================
    // ASSIGN AGENT
    // ============================================================

    @Transactional
    public CollectionCase assignAgent(
            Long caseId,
            Long agentUserId,
            String assignedBy) {

        CollectionCase collectionCase =
                getCase(caseId);

        User agent =
                userRepo.findById(agentUserId)

                        .orElseThrow(
                                () ->
                                        new RuntimeException(
                                                "Agent not found"
                                        )
                        );

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


        auditService.log(
                collectionCase.getOrganization(),
                null,
                "COLLECTION_CASE_ASSIGNED",
                "COLLECTION_CASE",
                String.valueOf(caseId),
                "Assigned to "
                        + agent.getName()
                        + " by "
                        + assignedBy
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
            Double promiseAmount) {

        CollectionCase collectionCase =
                getCase(caseId);


        /*
         * Convert legacy Double input immediately
         * to BigDecimal.
         */
        BigDecimal promiseAmountDecimal =
                promiseAmount == null
                        ? null
                        : BigDecimal.valueOf(promiseAmount);


        CollectionAction action =
                CollectionAction.builder()

                        .collectionCase(collectionCase)

                        .actionType(type)

                        .notes(notes)

                        .performedBy(performedBy)

                        .outcome(outcome)

                        .promiseDate(promiseDate)

                        .promiseAmount(promiseAmount)

                        .build();

        action =
                actionRepo.save(action);


        collectionCase.setLastContactDate(
                LocalDate.now()
        );


        // ========================================================
        // ACTION HANDLING
        // ========================================================

        switch (type) {

            // ----------------------------------------------------
            // PROMISE TO PAY
            // ----------------------------------------------------

            case PROMISE_TO_PAY -> {

                collectionCase.setStatus(
                        CollectionCase.CollectionStatus.PROMISE_TO_PAY
                );

                collectionCase.setPromiseToPayDate(
                        promiseDate
                );

                /*
                 * If CollectionCase still uses Double,
                 * keep the Double assignment here.
                 *
                 * If it has been migrated to BigDecimal,
                 * replace with promiseAmountDecimal.
                 */
                collectionCase.setPromiseToPayAmount(
                        promiseAmount
                );

                collectionCase.setNextActionDate(
                        promiseDate
                );
            }


            // ----------------------------------------------------
            // ESCALATED
            // ----------------------------------------------------

            case ESCALATED -> {

                collectionCase.setStatus(
                        CollectionCase.CollectionStatus.ESCALATED
                );
            }


            // ----------------------------------------------------
            // LEGAL NOTICE
            // ----------------------------------------------------

            case LEGAL_NOTICE -> {

                collectionCase.setStatus(
                        CollectionCase.CollectionStatus.LEGAL
                );
            }


            // ----------------------------------------------------
            // PAYMENT RECEIVED
            // ----------------------------------------------------

            case PAYMENT_RECEIVED -> {

                Loan loan =
                        collectionCase.getLoan();

                BigDecimal outstanding =
                        loan.getOutstandingBalance();

                boolean cleared =
                        outstanding == null
                                ||
                        outstanding.compareTo(
                                CLEARANCE_THRESHOLD
                        ) <= 0;


                if (cleared) {

                    collectionCase.setStatus(
                            CollectionCase.CollectionStatus.RESOLVED
                    );

                    collectionCase.setClosedAt(
                            LocalDateTime.now()
                    );

                }

                else if (
                        collectionCase.getStatus()
                                == CollectionCase.CollectionStatus.PROMISE_TO_PAY
                ) {

                    collectionCase.setStatus(
                            CollectionCase.CollectionStatus.IN_PROGRESS
                    );
                }
            }


            // ----------------------------------------------------
            // WRITE OFF
            // ----------------------------------------------------

            case WRITE_OFF -> {

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
                        notes
                );


                Loan loan =
                        collectionCase.getLoan();

                loan.setStatus(
                        LoanStatus.WRITTEN_OFF
                );

                /*
                 * BigDecimal-safe zero.
                 */
                loan.setOutstandingBalance(
                        BigDecimal.ZERO
                );

                loanRepo.save(loan);


                try {

                    loanClassificationService.reclassify(
                            loan
                    );

                } catch (Exception e) {

                    log.warn(
                            "Reclassification failed for loan {}: {}",
                            loan.getId(),
                            e.getMessage()
                    );
                }


                accountingService.postWriteOff(
                        loan
                );
            }


            // ----------------------------------------------------
            // CASE CLOSED
            // ----------------------------------------------------

            case CASE_CLOSED -> {

                collectionCase.setStatus(
                        CollectionCase.CollectionStatus.RESOLVED
                );

                collectionCase.setClosedAt(
                        LocalDateTime.now()
                );

                collectionCase.setResolutionNotes(
                        notes
                );
            }


            // ----------------------------------------------------
            // OTHER CONTACT ACTIONS
            // ----------------------------------------------------

            default -> {

                /*
                 * CALL
                 * SMS
                 * EMAIL
                 * FIELD_VISIT
                 * CASE_OPENED
                 *
                 * Only log the contact.
                 */
            }
        }


        caseRepo.save(collectionCase);


        auditService.log(
                collectionCase.getOrganization(),
                null,
                "COLLECTION_ACTION_" + type,
                "COLLECTION_CASE",
                String.valueOf(caseId),
                type
                        + " logged by "
                        + performedBy
                        + (
                        notes != null
                                ? ": " + notes
                                : ""
                )
        );


        return action;
    }


    // ============================================================
    // GET ACTIONS
    // ============================================================

    public List<CollectionAction> getActions(
            Long caseId) {

        return actionRepo
                .findByCollectionCase_IdOrderByCreatedAtDesc(
                        caseId
                );
    }


    
    // ============================================================
    // COLLECTION STATISTICS
    // ============================================================

    public Map<String, Object> getStats(
            Long orgId) {

        List<CollectionCase> cases =
                caseRepo.findByOrganization_Id(
                        orgId
                );


        Map<String, Long> byBucket =
                new LinkedHashMap<>();

        Map<String, BigDecimal> amountByBucket =
                new LinkedHashMap<>();


        for (
                CollectionCase.CollectionBucket bucket
                : CollectionCase.CollectionBucket.values()
        ) {

            byBucket.put(
                    bucket.name(),
                    0L
            );

            amountByBucket.put(
                    bucket.name(),
                    BigDecimal.ZERO
            );
        }


        BigDecimal totalOverdue =
                BigDecimal.ZERO;

        long promises = 0;


        for (CollectionCase collectionCase : cases) {

            if (
                    collectionCase.getStatus()
                            == CollectionCase.CollectionStatus.WRITTEN_OFF
            ) {

                continue;
            }


            String key =
                    collectionCase.getBucket().name();


            byBucket.merge(
                    key,
                    1L,
                    Long::sum
            );


            BigDecimal amount =
                    collectionCase.getOverdueAmount() != null
                            ? collectionCase.getOverdueAmount()
                            : BigDecimal.ZERO;


            amountByBucket.merge(
                    key,
                    amount,
                    BigDecimal::add
            );


            totalOverdue =
                    totalOverdue.add(
                            amount
                    );


            if (
                    collectionCase.getStatus()
                            == CollectionCase.CollectionStatus.PROMISE_TO_PAY
            ) {

                promises++;
            }
        }


        long totalOpenCases =
                cases.stream()

                        .filter(
                                c ->
                                        c.getStatus()
                                                != CollectionCase.CollectionStatus.RESOLVED
                                        &&
                                        c.getStatus()
                                                != CollectionCase.CollectionStatus.WRITTEN_OFF
                        )

                        .count();


        Map<String, Object> stats =
                new LinkedHashMap<>();


        stats.put(
                "casesByBucket",
                byBucket
        );

        stats.put(
                "overdueAmountByBucket",
                amountByBucket
        );

        stats.put(
                "totalOpenCases",
                totalOpenCases
        );

        /*
         * BigDecimal is returned instead of Double
         * to preserve financial precision.
         */
        stats.put(
                "totalOverdueAmount",
                totalOverdue
        );

        stats.put(
                "activePromises",
                promises
        );


        return stats;
    }


    // ============================================================
    // COLLECTION BUCKET
    // ============================================================

    private CollectionCase.CollectionBucket bucketFor(
            int dpd) {

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
    // COLLECTION PRIORITY
    // ============================================================

    private CollectionCase.Priority priorityFor(
            CollectionCase.CollectionBucket bucket) {

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
}
