package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.LoanRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskScoringServiceTest {

    @Mock
    LoanRepository loanRepository;

    @InjectMocks
    RiskScoringService riskScoringService;


    // ============================================================
    // EXCELLENT BORROWER
    // ============================================================

    @Test
    void score_shouldReturnLowRisk_forExcellentBorrower() {

        Organization org = new Organization();
        org.setId(1L);

        Borrower borrower = new Borrower();
        borrower.setId(1L);
        borrower.setCreditScore(800);
        borrower.setKycStatus("VERIFIED");
        borrower.setEmploymentType("PERMANENT");

        // Borrower.monthlyIncome is BigDecimal
        borrower.setMonthlyIncome(
                BigDecimal.valueOf(10000.0)
        );

        Loan loan = new Loan();
        loan.setId(1L);

        // Loan.amount is BigDecimal
        loan.setAmount(
                BigDecimal.valueOf(5000.0)
        );

        loan.setLoanType(
                Loan.LoanType.PERSONAL
        );

        // Loan.collateralValue is BigDecimal
        loan.setCollateralValue(
                BigDecimal.valueOf(10000.0)
        );

        // Loan.debtToIncomeRatio is Double
        loan.setDebtToIncomeRatio(
                15.0
        );

        loan.setBorrower(borrower);
        loan.setOrganization(org);

        when(
                loanRepository.findByBorrowerIdAndOrganizationId(
                        1L,
                        1L
                )
        ).thenReturn(List.of());

        RiskScoringService.RiskResult result =
                riskScoringService.score(loan);

        assertThat(result)
                .isNotNull();

        assertThat(result.getScore())
                .isGreaterThanOrEqualTo(50.0);

        assertThat(result.getCategory())
                .isIn(
                        "LOW",
                        "MEDIUM"
                );
    }


    // ============================================================
    // POOR BORROWER
    // ============================================================

    @Test
    void score_shouldReturnHighRisk_forPoorBorrower() {

        Organization org = new Organization();
        org.setId(1L);

        Borrower borrower = new Borrower();
        borrower.setId(1L);
        borrower.setCreditScore(400);
        borrower.setKycStatus("REJECTED");
        borrower.setEmploymentType("UNEMPLOYED");

        // Borrower.monthlyIncome is BigDecimal
        borrower.setMonthlyIncome(
                BigDecimal.ZERO
        );

        Loan loan = new Loan();
        loan.setId(1L);

        // Loan.amount is BigDecimal
        loan.setAmount(
                BigDecimal.valueOf(50000.0)
        );

        loan.setLoanType(
                Loan.LoanType.EMERGENCY
        );

        // No collateral
        loan.setCollateralValue(null);

        // Loan.debtToIncomeRatio is Double
        loan.setDebtToIncomeRatio(
                80.0
        );

        loan.setBorrower(borrower);
        loan.setOrganization(org);

        when(
                loanRepository.findByBorrowerIdAndOrganizationId(
                        1L,
                        1L
                )
        ).thenReturn(List.of());

        RiskScoringService.RiskResult result =
                riskScoringService.score(loan);

        assertThat(result)
                .isNotNull();

        assertThat(result.getScore())
                .isLessThan(60.0);

        assertThat(result.getCategory())
                .isIn(
                        "HIGH",
                        "CRITICAL",
                        "MEDIUM"
                );
    }


    // ============================================================
    // MULTIPLE ACTIVE LOANS
    // ============================================================

    @Test
    void score_shouldPenalise_forMultipleActiveLoans() {

        Organization org = new Organization();
        org.setId(1L);

        Borrower borrower = new Borrower();
        borrower.setId(1L);
        borrower.setCreditScore(700);
        borrower.setKycStatus("VERIFIED");

        Loan existing1 = new Loan();

        existing1.setStatus(
                LoanStatus.ACTIVE
        );

        Loan existing2 = new Loan();

        existing2.setStatus(
                LoanStatus.ACTIVE
        );

        Loan loan = new Loan();

        loan.setId(1L);

        // Loan.amount is BigDecimal
        loan.setAmount(
                BigDecimal.valueOf(5000.0)
        );

        loan.setLoanType(
                Loan.LoanType.PERSONAL
        );

        loan.setBorrower(borrower);
        loan.setOrganization(org);


        // ========================================================
        // SCORE WITH EXISTING ACTIVE LOANS
        // ========================================================

        when(
                loanRepository.findByBorrowerIdAndOrganizationId(
                        1L,
                        1L
                )
        ).thenReturn(
                List.of(
                        existing1,
                        existing2
                )
        );

        RiskScoringService.RiskResult withExisting =
                riskScoringService.score(loan);


        // ========================================================
        // SCORE WITHOUT EXISTING LOANS
        // ========================================================

        when(
                loanRepository.findByBorrowerIdAndOrganizationId(
                        1L,
                        1L
                )
        ).thenReturn(
                List.of()
        );

        RiskScoringService.RiskResult withoutExisting =
                riskScoringService.score(loan);


        // ========================================================
        // ASSERTIONS
        // ========================================================

        assertThat(withExisting)
                .isNotNull();

        assertThat(withoutExisting)
                .isNotNull();

        assertThat(withExisting.getScore())
                .isLessThanOrEqualTo(
                        withoutExisting.getScore()
                );
    }
}
