package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Single source of truth for deriving Loan.creditQuality / arrearsStatus / collectionsStage
 * from daysOverdue and status. Nothing else in the codebase should set those three fields
 * directly — always go through reclassify() so the mapping stays in one place and every
 * change is audit-logged.
 *
 * IMPORTANT: the day-boundaries below are a standard, illustrative provisioning ladder
 * (Current / Watch 1-30 / Substandard 31-90 / Doubtful 91-180 / Loss 180+), not a confirmed
 * BNR template. Before this is relied on for an actual regulatory submission, the exact bands
 * for this license category need to be confirmed against the current BNR reporting
 * instructions under Regulation 96/2026.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanClassificationService {

    private final LoanRepository loanRepo;
    private final AuditService   auditService;

  
    private static final List<LoanStatus> CLASSIFIABLE = List.of(
        LoanStatus.DISBURSED, LoanStatus.ACTIVE, LoanStatus.OVERDUE,
        LoanStatus.RESTRUCTURED, LoanStatus.DEFAULTED, LoanStatus.WRITTEN_OFF
    );

    public record Classification(Loan.CreditQuality quality, Loan.ArrearsStatus arrears, Loan.CollectionsStage stage) {}

    /** Pure computation — no side effects, no save. Safe to call for previews/reports. */
    public Classification classify(Loan loan) {
        if (loan.getStatus() == LoanStatus.WRITTEN_OFF) {
            return new Classification(Loan.CreditQuality.LOSS, Loan.ArrearsStatus.PAST_DUE, Loan.CollectionsStage.RECOVERY);
        }
        if (!CLASSIFIABLE.contains(loan.getStatus())) {
            return new Classification(Loan.CreditQuality.CURRENT, Loan.ArrearsStatus.NOT_DUE, Loan.CollectionsStage.NORMAL);
        }

        int dpd = loan.getDaysOverdue() != null ? loan.getDaysOverdue() : 0;
        if (dpd <= 0) {
            return new Classification(Loan.CreditQuality.CURRENT, Loan.ArrearsStatus.NOT_DUE, Loan.CollectionsStage.NORMAL);
        }

        Loan.ArrearsStatus arrears = Loan.ArrearsStatus.PAST_DUE;
        if (dpd <= 30)  return new Classification(Loan.CreditQuality.WATCH,       arrears, Loan.CollectionsStage.REMINDER);
        if (dpd <= 90)  return new Classification(Loan.CreditQuality.SUBSTANDARD, arrears, Loan.CollectionsStage.COLLECTION);
        if (dpd <= 180) return new Classification(Loan.CreditQuality.DOUBTFUL,    arrears, Loan.CollectionsStage.LEGAL);
        if (dpd <= 365) return new Classification(Loan.CreditQuality.LOSS,        arrears, Loan.CollectionsStage.LEGAL);
        return new Classification(Loan.CreditQuality.LOSS, arrears, Loan.CollectionsStage.RECOVERY);
    }

 
    @Transactional
    public boolean reclassify(Loan loan) {
        Classification next = classify(loan);
        boolean changed = loan.getCreditQuality() != next.quality()
            || loan.getArrearsStatus() != next.arrears()
            || loan.getCollectionsStage() != next.stage();

        if (!changed) return false;

        Loan.CreditQuality prevQuality = loan.getCreditQuality();
        Loan.CollectionsStage prevStage = loan.getCollectionsStage();

        loan.setCreditQuality(next.quality());
        loan.setArrearsStatus(next.arrears());
        loan.setCollectionsStage(next.stage());
        loan.setClassifiedAt(LocalDateTime.now());
        loanRepo.save(loan);

        try {
            auditService.log(loan.getOrganization(), null, "LOAN_RECLASSIFIED", "LOAN",
                String.valueOf(loan.getId()),
                "Credit quality " + prevQuality + " -> " + next.quality()
                    + ", collections stage " + prevStage + " -> " + next.stage()
                    + " (" + (loan.getDaysOverdue() != null ? loan.getDaysOverdue() : 0) + " days past due)");
        } catch (Exception e) {
            log.warn("Could not audit-log reclassification of loan {}: {}", loan.getId(), e.getMessage());
        }

        return true;
    }

    
    @Transactional
    public int reclassifyPortfolio(Organization org) {
        int changed = 0;
        List<Loan> loans = loanRepo.findByOrganization_Id(org.getId()).stream()
            .filter(l -> CLASSIFIABLE.contains(l.getStatus()))
            .toList();
        for (Loan loan : loans) {
            try {
                if (reclassify(loan)) changed++;
            } catch (Exception e) {
                log.warn("Reclassification failed for loan {}: {}", loan.getId(), e.getMessage());
            }
        }
        return changed;
    }
}