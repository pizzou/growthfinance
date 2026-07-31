package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.CreditBureauCheckRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Multi-factor risk scoring engine.
 * Combines credit score, DTI ratio, employment type,
 * collateral presence, credit bureau delinquency history,
 * and loan type into a 0-100 risk score.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiskScoringService {

    private final LoanRepository loanRepo;
    private final CreditBureauCheckRepository creditBureauCheckRepo;

    public RiskResult score(Loan loan) {
        Borrower b = loan.getBorrower();
        double score = 100.0; // start healthy

        // 1. Credit score (max 30 pts)
        if (b.getCreditScore() != null) {
            int cs = b.getCreditScore();
            if      (cs >= 800) score -= 0;
            else if (cs >= 750) score -= 5;
            else if (cs >= 700) score -= 12;
            else if (cs >= 650) score -= 20;
            else if (cs >= 600) score -= 28;
            else                score -= 35;
        } else { score -= 20; } // no credit history

        // 2. Debt-to-income ratio (max 25 pts)
        if (loan.getDebtToIncomeRatio() != null) {
            double dti = loan.getDebtToIncomeRatio();
            if      (dti < 20)  score -= 0;
            else if (dti < 30)  score -= 5;
            else if (dti < 40)  score -= 12;
            else if (dti < 50)  score -= 20;
            else                score -= 28;
        }

        // 3. Employment type (max 15 pts)
        if (b.getEmploymentType() != null) {
            switch (b.getEmploymentType().toUpperCase()) {
                case "PERMANENT"     -> {}
                case "CONTRACT"      -> score -= 5;
                case "SELF_EMPLOYED" -> score -= 8;
                case "UNEMPLOYED"    -> score -= 18;
                default              -> score -= 5;
            }
        }

        // 4. Collateral (max 15 pts)
        if (loan.getCollateralValue() == null || loan.getCollateralValue() == 0) score -= 12;
        else if (loan.getCollateralValue() < loan.getAmount() * 0.5)             score -= 6;

        // 5. Loan type multiplier (max 10 pts)
        if (loan.getLoanType() != null) {
            switch (loan.getLoanType()) {
                case MORTGAGE, AGRICULTURAL -> {}
                case AUTO, ASSET_FINANCE    -> score -= 2;
                case BUSINESS               -> score -= 5;
                case PERSONAL               -> score -= 8;
                case EMERGENCY              -> score -= 12;
                case MICROFINANCE           -> score -= 10;
                default                     -> score -= 5;
            }
        }

        // 6. Existing loans penalty
        long existingActive = loanRepo.findByBorrowerIdAndOrganizationId(
            b.getId(), loan.getOrganization().getId()).stream()
            .filter(l -> l.getStatus() == LoanStatus.ACTIVE || l.getStatus() == LoanStatus.OVERDUE)
            .count();
        score -= existingActive * 8;

        // 7. Credit bureau history (max 15 pts) — uses the freshest non-expired bureau pull on file
        var bureauCheck = creditBureauCheckRepo.findFirstByBorrower_IdOrderByCreatedAtDesc(b.getId())
            .filter(c -> !c.isExpired() && c.getStatus() == CreditBureauCheck.CheckStatus.COMPLETED);
        if (bureauCheck.isPresent()) {
            CreditBureauCheck cb = bureauCheck.get();
            if (Boolean.TRUE.equals(cb.getHasDefaultHistory())) score -= 12;
            if (Boolean.TRUE.equals(cb.getHasActiveListing())) score -= 15;
            if (cb.getDelinquentAccounts() != null && cb.getDelinquentAccounts() > 0)
                score -= Math.min(cb.getDelinquentAccounts() * 4, 12);
        }

        score = Math.max(0, Math.min(100, score));

        String category;
        if      (score >= 80) category = "LOW";
        else if (score >= 60) category = "MEDIUM";
        else if (score >= 40) category = "HIGH";
        else                  category = "CRITICAL";

        return new RiskResult(round(score), category);
    }

    private double round(double v) { return Math.round(v * 10.0) / 10.0; }

    public record RiskResult(double score, String category) {
        public double getScore()    { return score; }
        public String getCategory() { return category; }
    }
}
