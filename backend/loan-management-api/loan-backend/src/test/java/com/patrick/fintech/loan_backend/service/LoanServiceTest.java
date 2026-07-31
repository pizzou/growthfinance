package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.LoanRequest;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock LoanRepository         loanRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock PaymentRepository      paymentRepository;
    @Mock BorrowerRepository     borrowerRepository;
    @Mock RiskScoringService     riskScoringService;
    @Mock NotificationService    notificationService;
    @Mock AuditLogRepository     auditLogRepository;
    @Mock WebhookService         webhookService;
    @InjectMocks LoanService loanService;

    private Organization org;
    private Borrower     borrower;
    private User         officer;

    @BeforeEach
    void setUp() {
        org = new Organization();
        org.setId(1L);
        org.setName("TestOrg");
        org.setDefaultCurrency("USD");

        borrower = new Borrower();
        borrower.setId(1L);
        borrower.setFirstName("John");
        borrower.setLastName("Doe");
        borrower.setKycStatus("VERIFIED");
        borrower.setCreditScore(750);
        borrower.setOrganization(org);

        officer = new User();
        officer.setId(1L);
        officer.setName("Test Officer");
        officer.setOrganization(org);
    }

    @Test
    void createLoan_shouldSaveLoan_withAllFields() {
        LoanRequest req = new LoanRequest();
        req.setBorrowerId(1L);
        req.setAmount(10000.0);
        req.setInterestRate(12.0);
        req.setDurationMonths(12);
        req.setCurrency("USD");
        req.setStartDate("2026-01-01");
        req.setCollateralValue(15000.0);
        req.setCollateralDescription("Land title");

        Loan savedLoan = new Loan();
        savedLoan.setId(1L);
        savedLoan.setBorrower(borrower);
        savedLoan.setOrganization(org);
        savedLoan.setAmount(10000.0);
        savedLoan.setStatus(LoanStatus.PENDING);

        when(organizationRepository.findById(1L)).thenReturn(Optional.of(org));
        when(borrowerRepository.findById(1L)).thenReturn(Optional.of(borrower));
        when(loanRepository.save(any())).thenReturn(savedLoan);

        // riskScoringService.score() returns RiskResult (inner record) — not RiskScoreResponse
        when(riskScoringService.score(any()))
            .thenReturn(new RiskScoringService.RiskResult(80.0, "LOW"));

        // createLoan(LoanRequest, Long orgId, User createdBy)
        Loan result = loanService.createLoan(req, 1L, officer);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(LoanStatus.PENDING);
        verify(loanRepository, atLeastOnce()).save(any());
    }

    @Test
    void createLoan_shouldThrow_whenBorrowerNotFound() {
        LoanRequest req = new LoanRequest();
        req.setBorrowerId(99L);
        req.setAmount(1000.0);
        req.setInterestRate(10.0);
        req.setDurationMonths(6);
        req.setCurrency("USD");
        req.setStartDate("2026-01-01");

        when(organizationRepository.findById(1L)).thenReturn(Optional.of(org));
        when(borrowerRepository.findById(99L)).thenReturn(Optional.empty());

        // createLoan(LoanRequest, Long orgId, User createdBy)
        assertThatThrownBy(() -> loanService.createLoan(req, 1L, officer))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Borrower not found");
    }

    @Test
    void approveLoan_shouldSetStatusApproved_andGenerateSchedule() {
        Loan loan = new Loan();
        loan.setId(1L);
        loan.setStatus(LoanStatus.PENDING);
        loan.setAmount(12000.0);
        loan.setInterestRate(12.0);
        loan.setDurationMonths(12);
        loan.setStartDate(java.time.LocalDate.of(2026, 1, 1));
        loan.setBorrower(borrower);
        loan.setOrganization(org);

        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
        when(loanRepository.save(any())).thenReturn(loan);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // approveLoan(Long loanId, User approvedBy, String notes)
       Loan result = loanService.approveLoan(
        1L,
        officer,
        null,
        null
);

        assertThat(result.getStatus()).isEqualTo(LoanStatus.APPROVED);
        verify(paymentRepository, times(12)).save(any());
    }

    @Test
    void approveLoan_shouldThrow_whenAlreadyApproved() {
        Loan loan = new Loan();
        loan.setId(1L);
        loan.setStatus(LoanStatus.APPROVED);
        loan.setOrganization(org);

        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));

        // approveLoan(Long loanId, User approvedBy, String notes)
        assertThatThrownBy(() ->
        loanService.approveLoan(
                1L,
                officer,
                null,
                null
        ))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Already approved");
    }
}
