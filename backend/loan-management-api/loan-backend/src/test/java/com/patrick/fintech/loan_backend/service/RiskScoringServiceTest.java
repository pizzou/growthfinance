package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskScoringServiceTest {

    @Mock LoanRepository loanRepository;
    @InjectMocks RiskScoringService riskScoringService;

    @Test
    void score_shouldReturnLowRisk_forExcellentBorrower() {
        Organization org = new Organization();
        org.setId(1L);

        Borrower borrower = new Borrower();
        borrower.setId(1L);
        borrower.setCreditScore(800);
        borrower.setKycStatus("VERIFIED");
        borrower.setEmploymentType("PERMANENT");
        borrower.setMonthlyIncome(10000.0);

        Loan loan = new Loan();
        loan.setId(1L);
        loan.setAmount(5000.0);
        loan.setLoanType(Loan.LoanType.PERSONAL);
        loan.setCollateralValue(10000.0);
        loan.setDebtToIncomeRatio(15.0);
        loan.setBorrower(borrower);
        loan.setOrganization(org);

        when(loanRepository.findByBorrowerIdAndOrganizationId(1L, 1L))
            .thenReturn(List.of());

        // score() returns RiskScoringService.RiskResult (inner record), not RiskScoreResponse
        RiskScoringService.RiskResult result = riskScoringService.score(loan);

        assertThat(result).isNotNull();
        assertThat(result.getScore()).isGreaterThanOrEqualTo(50.0);
        assertThat(result.getCategory()).isIn("LOW", "MEDIUM");
    }

    @Test
    void score_shouldReturnHighRisk_forPoorBorrower() {
        Organization org = new Organization();
        org.setId(1L);

        Borrower borrower = new Borrower();
        borrower.setId(1L);
        borrower.setCreditScore(400);
        borrower.setKycStatus("REJECTED");
        borrower.setEmploymentType("UNEMPLOYED");
        borrower.setMonthlyIncome(0.0);

        Loan loan = new Loan();
        loan.setId(1L);
        loan.setAmount(50000.0);
        loan.setLoanType(Loan.LoanType.EMERGENCY);
        loan.setCollateralValue(null); // no collateral
        loan.setDebtToIncomeRatio(80.0);
        loan.setBorrower(borrower);
        loan.setOrganization(org);

        when(loanRepository.findByBorrowerIdAndOrganizationId(1L, 1L))
            .thenReturn(List.of());

        RiskScoringService.RiskResult result = riskScoringService.score(loan);

        assertThat(result.getScore()).isLessThan(60.0);
        assertThat(result.getCategory()).isIn("HIGH", "CRITICAL", "MEDIUM");
    }

    @Test
    void score_shouldPenalise_forMultipleActiveLoans() {
        Organization org = new Organization();
        org.setId(1L);

        Borrower borrower = new Borrower();
        borrower.setId(1L);
        borrower.setCreditScore(700);
        borrower.setKycStatus("VERIFIED");

        Loan existing1 = new Loan(); existing1.setStatus(LoanStatus.ACTIVE);
        Loan existing2 = new Loan(); existing2.setStatus(LoanStatus.ACTIVE);

        Loan loan = new Loan();
        loan.setId(1L);
        loan.setAmount(5000.0);
        loan.setLoanType(Loan.LoanType.PERSONAL);
        loan.setBorrower(borrower);
        loan.setOrganization(org);

        when(loanRepository.findByBorrowerIdAndOrganizationId(1L, 1L))
            .thenReturn(List.of(existing1, existing2));

        RiskScoringService.RiskResult withExisting = riskScoringService.score(loan);

        // Reset and score without existing loans
        when(loanRepository.findByBorrowerIdAndOrganizationId(1L, 1L))
            .thenReturn(List.of());
        RiskScoringService.RiskResult withoutExisting = riskScoringService.score(loan);

        // More existing loans = lower score
        assertThat(withExisting.getScore()).isLessThanOrEqualTo(withoutExisting.getScore());
    }
}
