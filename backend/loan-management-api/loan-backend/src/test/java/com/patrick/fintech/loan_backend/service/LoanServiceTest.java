
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.LoanRequest;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.AuditLogRepository;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock
    LoanRepository loanRepository;

    @Mock
    OrganizationRepository organizationRepository;

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    BorrowerRepository borrowerRepository;

    @Mock
    RiskScoringService riskScoringService;

    @Mock
    NotificationService notificationService;

    @Mock
    AuditLogRepository auditLogRepository;

    @Mock
    WebhookService webhookService;

    @InjectMocks
    LoanService loanService;

    private Organization org;
    private Borrower borrower;
    private User officer;

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
        req.setAmount(BigDecimal.valueOf(10_000));
        req.setInterestRate(BigDecimal.valueOf(12));
        req.setDurationMonths(12);
        req.setCurrency("USD");
        req.setStartDate("2026-01-01");
        req.setCollateralValue(BigDecimal.valueOf(15_000));
        req.setCollateralDescription("Land title");

        Loan savedLoan = new Loan();

        savedLoan.setId(1L);
        savedLoan.setBorrower(borrower);
        savedLoan.setOrganization(org);
        savedLoan.setAmount(BigDecimal.valueOf(10_000));
        savedLoan.setStatus(LoanStatus.PENDING);

        when(organizationRepository.findById(1L))
                .thenReturn(Optional.of(org));

        when(borrowerRepository.findById(1L))
                .thenReturn(Optional.of(borrower));

        when(loanRepository.save(any(Loan.class)))
                .thenReturn(savedLoan);

        /*
         * RiskResult.score is currently a double.
         * Therefore 80.0 is intentional here.
         */
        when(riskScoringService.score(any(Loan.class)))
                .thenReturn(
                        new RiskScoringService.RiskResult(
                                80.0,
                                "LOW"
                        )
                );

        Loan result = loanService.createLoan(
                req,
                1L,
                officer
        );

        assertThat(result).isNotNull();

        assertThat(result.getStatus())
                .isEqualTo(LoanStatus.PENDING);

        verify(loanRepository, atLeastOnce())
                .save(any(Loan.class));
    }

    @Test
    void createLoan_shouldThrow_whenBorrowerNotFound() {

        LoanRequest req = new LoanRequest();

        req.setBorrowerId(99L);
        req.setAmount(BigDecimal.valueOf(1_000));
        req.setInterestRate(BigDecimal.valueOf(10));
        req.setDurationMonths(6);
        req.setCurrency("USD");
        req.setStartDate("2026-01-01");

        when(organizationRepository.findById(1L))
                .thenReturn(Optional.of(org));

        when(borrowerRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                loanService.createLoan(
                        req,
                        1L,
                        officer
                )
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Borrower not found");
    }

    @Test
    void approveLoan_shouldSetStatusApproved_andGenerateSchedule() {

        Loan loan = new Loan();

        loan.setId(1L);
        loan.setStatus(LoanStatus.PENDING);
        loan.setAmount(BigDecimal.valueOf(12_000));
        loan.setInterestRate(BigDecimal.valueOf(12));
        loan.setDurationMonths(12);
        loan.setStartDate(
                java.time.LocalDate.of(2026, 1, 1)
        );
        loan.setBorrower(borrower);
        loan.setOrganization(org);

        when(loanRepository.findById(1L))
                .thenReturn(Optional.of(loan));

        when(loanRepository.save(any(Loan.class)))
                .thenReturn(loan);

        when(paymentRepository.save(any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        /*
         * Current LoanService signature:
         *
         * approveLoan(Long loanId,
         *             User approvedBy,
         *             String notes,
         *             String rejectionReason)
         */
        Loan result = loanService.approveLoan(
                1L,
                officer,
                null,
                null
        );

        assertThat(result).isNotNull();

        assertThat(result.getStatus())
                .isEqualTo(LoanStatus.APPROVED);

        verify(paymentRepository, times(12))
                .save(any());
    }

    @Test
    void approveLoan_shouldThrow_whenAlreadyApproved() {

        Loan loan = new Loan();

        loan.setId(1L);
        loan.setStatus(LoanStatus.APPROVED);
        loan.setOrganization(org);

        when(loanRepository.findById(1L))
                .thenReturn(Optional.of(loan));

        assertThatThrownBy(() ->
                loanService.approveLoan(
                        1L,
                        officer,
                        null,
                        null
                )
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Already approved");
    }
}
