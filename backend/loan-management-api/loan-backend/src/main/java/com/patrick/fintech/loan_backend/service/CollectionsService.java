package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Delinquency / collections workflow.
 * Buckets overdue loans by days-past-due, tracks agent assignment, contact
 * attempts, promise-to-pay commitments, and escalation up to write-off —
 * standard practice for microfinance/SACCO collections desks.
 */
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

    private static final List<LoanStatus> DELINQUENT_STATUSES =
        List.of(LoanStatus.OVERDUE, LoanStatus.DEFAULTED);

    /** Scans overdue/defaulted loans and opens or refreshes a case + bucket for each. Called by the scheduler. */
    @Transactional
    public int syncCasesFromOverdueLoans() {
        int touched = 0;
        for (Loan loan : loanRepo.findByStatusIn(DELINQUENT_STATUSES)) {
            CollectionCase c = caseRepo.findByLoan_Id(loan.getId()).orElse(null);
            int dpd = loan.getDaysOverdue() != null ? loan.getDaysOverdue() : 0;
            CollectionCase.CollectionBucket bucket = bucketFor(dpd);

            boolean isNew = c == null;
            if (isNew) {
                c = CollectionCase.builder()
                    .loan(loan).organization(loan.getOrganization())
                    .bucket(bucket).status(CollectionCase.CollectionStatus.OPEN)
                    .priority(priorityFor(bucket))
                    .build();
            } else if (c.getStatus() == CollectionCase.CollectionStatus.RESOLVED
                    || c.getStatus() == CollectionCase.CollectionStatus.WRITTEN_OFF) {
                continue; // don't reopen closed cases automatically
            } else {
                c.setBucket(bucket);
                c.setPriority(priorityFor(bucket));
            }
            c.setDaysPastDue(dpd);
            c.setOverdueAmount(loan.getOutstandingBalance());
            c.setTotalOutstanding(loan.getOutstandingBalance());
            c = caseRepo.save(c);

            if (isNew) {
                logAction(c.getId(), CollectionAction.ActionType.CASE_OPENED,
                    "Auto-opened: loan is " + dpd + " day(s) past due", "SYSTEM", null, null, null);
            }
            touched++;
        }
        return touched;
    }

    public List<CollectionCase> getQueue(Long orgId, CollectionCase.CollectionBucket bucket,
                                          CollectionCase.CollectionStatus status, Long agentId) {
        List<CollectionCase> cases = caseRepo.findByOrganization_Id(orgId);
        return cases.stream()
            .filter(c -> bucket == null || c.getBucket() == bucket)
            .filter(c -> status == null || c.getStatus() == status)
            .filter(c -> agentId == null || (c.getAssignedAgent() != null && agentId.equals(c.getAssignedAgent().getId())))
            .sorted(Comparator.comparing((CollectionCase c) -> c.getDaysPastDue() == null ? 0 : c.getDaysPastDue()).reversed())
            .toList();
    }

    public CollectionCase getCase(Long caseId) {
        return caseRepo.findById(caseId).orElseThrow(() -> new RuntimeException("Collection case not found: " + caseId));
    }

    @Transactional
    public CollectionCase assignAgent(Long caseId, Long agentUserId, String assignedBy) {
        CollectionCase c = getCase(caseId);
        User agent = userRepo.findById(agentUserId).orElseThrow(() -> new RuntimeException("Agent not found"));
        c.setAssignedAgent(agent);
        if (c.getStatus() == CollectionCase.CollectionStatus.OPEN) c.setStatus(CollectionCase.CollectionStatus.IN_PROGRESS);
        c = caseRepo.save(c);
        auditService.log(c.getOrganization(), null, "COLLECTION_CASE_ASSIGNED", "COLLECTION_CASE",
            String.valueOf(caseId), "Assigned to " + agent.getName() + " by " + assignedBy);
        return c;
    }

    @Transactional
    public CollectionAction logAction(Long caseId, CollectionAction.ActionType type, String notes,
                                       String performedBy, String outcome, LocalDate promiseDate, Double promiseAmount) {
        CollectionCase c = getCase(caseId);

        CollectionAction action = CollectionAction.builder()
            .collectionCase(c).actionType(type).notes(notes).performedBy(performedBy)
            .outcome(outcome).promiseDate(promiseDate).promiseAmount(promiseAmount)
            .build();
        action = actionRepo.save(action);

        c.setLastContactDate(LocalDate.now());
        switch (type) {
            case PROMISE_TO_PAY -> {
                c.setStatus(CollectionCase.CollectionStatus.PROMISE_TO_PAY);
                c.setPromiseToPayDate(promiseDate);
                c.setPromiseToPayAmount(promiseAmount);
                c.setNextActionDate(promiseDate);
            }
            case ESCALATED -> c.setStatus(CollectionCase.CollectionStatus.ESCALATED);
            case LEGAL_NOTICE -> c.setStatus(CollectionCase.CollectionStatus.LEGAL);
            case PAYMENT_RECEIVED -> {
                Loan loan = c.getLoan();
                boolean cleared = loan.getOutstandingBalance() == null || loan.getOutstandingBalance() <= 0.01;
                if (cleared) {
                    c.setStatus(CollectionCase.CollectionStatus.RESOLVED);
                    c.setClosedAt(java.time.LocalDateTime.now());
                } else if (c.getStatus() == CollectionCase.CollectionStatus.PROMISE_TO_PAY) {
                    c.setStatus(CollectionCase.CollectionStatus.IN_PROGRESS);
                }
            }
            case WRITE_OFF -> {
                c.setStatus(CollectionCase.CollectionStatus.WRITTEN_OFF);
                c.setBucket(CollectionCase.CollectionBucket.WRITE_OFF);
                c.setClosedAt(java.time.LocalDateTime.now());
                c.setResolutionNotes(notes);
                Loan loan = c.getLoan();
                loan.setStatus(LoanStatus.WRITTEN_OFF);
                loanRepo.save(loan);
                accountingService.postWriteOff(loan);
            }
            case CASE_CLOSED -> {
                c.setStatus(CollectionCase.CollectionStatus.RESOLVED);
                c.setClosedAt(java.time.LocalDateTime.now());
                c.setResolutionNotes(notes);
            }
            default -> { /* CALL/SMS/EMAIL/FIELD_VISIT/CASE_OPENED just log contact */ }
        }
        caseRepo.save(c);

        auditService.log(c.getOrganization(), null, "COLLECTION_ACTION_" + type, "COLLECTION_CASE",
            String.valueOf(caseId), type + " logged by " + performedBy + (notes != null ? ": " + notes : ""));

        return action;
    }

    public List<CollectionAction> getActions(Long caseId) {
        return actionRepo.findByCollectionCase_IdOrderByCreatedAtDesc(caseId);
    }

    public Map<String, Object> getStats(Long orgId) {
        List<CollectionCase> cases = caseRepo.findByOrganization_Id(orgId);
        Map<String, Long> byBucket = new LinkedHashMap<>();
        Map<String, Double> amountByBucket = new LinkedHashMap<>();
        for (CollectionCase.CollectionBucket b : CollectionCase.CollectionBucket.values()) {
            byBucket.put(b.name(), 0L);
            amountByBucket.put(b.name(), 0.0);
        }
        double totalOverdue = 0;
        long promises = 0;
        for (CollectionCase c : cases) {
            if (c.getStatus() == CollectionCase.CollectionStatus.WRITTEN_OFF) continue;
            String key = c.getBucket().name();
            byBucket.merge(key, 1L, Long::sum);
            double amt = c.getOverdueAmount() != null ? c.getOverdueAmount() : 0;
            amountByBucket.merge(key, amt, Double::sum);
            totalOverdue += amt;
            if (c.getStatus() == CollectionCase.CollectionStatus.PROMISE_TO_PAY) promises++;
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("casesByBucket", byBucket);
        stats.put("overdueAmountByBucket", amountByBucket);
        stats.put("totalOpenCases", cases.stream().filter(c -> c.getStatus() != CollectionCase.CollectionStatus.RESOLVED
            && c.getStatus() != CollectionCase.CollectionStatus.WRITTEN_OFF).count());
        stats.put("totalOverdueAmount", totalOverdue);
        stats.put("activePromises", promises);
        return stats;
    }

    private CollectionCase.CollectionBucket bucketFor(int dpd) {
        if (dpd <= 0)  return CollectionCase.CollectionBucket.CURRENT;
        if (dpd <= 30) return CollectionCase.CollectionBucket.DPD_1_30;
        if (dpd <= 60) return CollectionCase.CollectionBucket.DPD_31_60;
        if (dpd <= 90) return CollectionCase.CollectionBucket.DPD_61_90;
        return CollectionCase.CollectionBucket.DPD_90_PLUS;
    }

    private CollectionCase.Priority priorityFor(CollectionCase.CollectionBucket bucket) {
        return switch (bucket) {
            case CURRENT, DPD_1_30 -> CollectionCase.Priority.LOW;
            case DPD_31_60 -> CollectionCase.Priority.MEDIUM;
            case DPD_61_90 -> CollectionCase.Priority.HIGH;
            case DPD_90_PLUS, WRITE_OFF -> CollectionCase.Priority.URGENT;
        };
    }
}
