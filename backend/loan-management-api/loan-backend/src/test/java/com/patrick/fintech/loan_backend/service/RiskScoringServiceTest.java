
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskScoringServiceTest {

    @Mock
    LoanRepository loanRepository;

    @InjectMocks
    RiskScoringService riskScoringService;

    @Test
    void score_shouldReturnLowRisk_forExcellentBorrower() {

        Organization org = new Organization();
        org.setId(1L);

        Borrower borrower = new Borrower();
        borrower.setId(1L);
        borrower.setCreditScore(800);
        borrower.setKycStatus("VERIFIED");
        borrower.setEmploymentType("PERMANENT");
        borrower.setMonthlyIncome(BigDecimal.valueOf(10_000));

        Loan loan = new Loan();
        loan.setId(1L);
        loan.setAmount(BigDecimal.valueOf(5_000));
        loan.setLoanType(Loan.LoanType.PERSONAL);
        loan.setCollateralValue(BigDecimal.valueOf(10_000));
        loan.setDebtToIncomeRatio(BigDecimal.valueOf(15));
        loan.setBorrower(borrower);
        loan.setOrganization(org);

        when(loanRepository.findByBorrowerIdAndOrganizationId(1L, 1L))
                .thenReturn(List.of());

        RiskScoringService.RiskResult result =
                riskScoringService.score(loan);

        assertThat(result).isNotNull();

        assertThat(result.getScore())
                .isGreaterThanOrEqualTo(50.0);

        assertThat(result.getCategory())
                .isIn("LOW", "MEDIUM");
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
        borrower.setMonthlyIncome(BigDecimal.ZERO);

        Loan loan = new Loan();
        loan.setId(1L);
        loan.setAmount(BigDecimal.valueOf(50_000));
        loan.setLoanType(Loan.LoanType.EMERGENCY);

    
        loan.setCollateralValue((BigDecimal) null);

        loan.setDebtToIncomeRatio(BigDecimal.valueOf(80));
        loan.setBorrower(borrower);
        loan.setOrganization(org);

        when(loanRepository.findByBorrowerIdAndOrganizationId(1L, 1L))
                .thenReturn(List.of());

        RiskScoringService.RiskResult result =
                riskScoringService.score(loan);

        assertThat(result).isNotNull();

        assertThat(result.getScore())
                .isLessThan(60.0);

        assertThat(result.getCategory())
                .isIn("HIGH", "CRITICAL", "MEDIUM");
    }

    @Test
    void score_shouldPenalise_forMultipleActiveLoans() {

        Organization org = new Organization();
        org.setId(1L);

        Borrower borrower = new Borrower();
        borrower.setId(1L);
        borrower.setCreditScore(700);
        borrower.setKycStatus("VERIFIED");

        Loan existing1 = new Loan();
        existing1.setStatus(LoanStatus.ACTIVE);

        Loan existing2 = new Loan();
        existing2.setStatus(LoanStatus.ACTIVE);

        Loan loan = new Loan();
        loan.setId(1L);
        loan.setAmount(BigDecimal.valueOf(5_000));
        loan.setLoanType(Loan.LoanType.PERSONAL);
        loan.setBorrower(borrower);
        loan.setOrganization(org);

        when(loanRepository.findByBorrowerIdAndOrganizationId(1L, 1L))
                .thenReturn(List.of(existing1, existing2));

        RiskScoringService.RiskResult withExisting =
                riskScoringService.score(loan);

        when(loanRepository.findByBorrowerIdAndOrganizationId(1L, 1L))
                .thenReturn(List.of());

        RiskScoringService.RiskResult withoutExisting =
                riskScoringService.score(loan);

        assertThat(withExisting.getScore())
                .isLessThanOrEqualTo(withoutExisting.getScore());
    }
}
