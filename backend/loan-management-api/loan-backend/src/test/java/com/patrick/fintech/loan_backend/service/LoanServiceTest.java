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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    // ============================================================
    // REPOSITORIES
    // ============================================================

    @Mock
    LoanRepository loanRepository;

    @Mock
    OrganizationRepository organizationRepository;

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    BorrowerRepository borrowerRepository;

    @Mock
    LoanProductRepository loanProductRepository;

    @Mock
    AuditLogRepository auditLogRepository;


    // ============================================================
    // SERVICES
    // ============================================================

    @Mock
    RiskScoringService riskScoringService;

    @Mock
    NotificationService notificationService;

    @Mock
    MailService mailService;

    @Mock
    SmsService smsService;

    @Mock
    WebhookService webhookService;

    @Mock
    AuditService auditService;

    @Mock
    AccountingService accountingService;

    @Mock
    BorrowerFileService borrowerFileService;

    @Mock
    HolidayService holidayService;

    @Mock
    CreditBureauService creditBureauService;

    @Mock
    PaymentScheduleService paymentScheduleService;


    // ============================================================
    // SERVICE UNDER TEST
    // ============================================================

    @InjectMocks
    LoanService loanService;


    // ============================================================
    // TEST DATA
    // ============================================================

    private Organization org;

    private Borrower borrower;

    private User officer;


    // ============================================================
    // SETUP
    // ============================================================

    @BeforeEach
    void setUp() {

        // --------------------------------------------------------
        // ORGANIZATION
        // --------------------------------------------------------

        org = new Organization();

        org.setId(1L);

        org.setName(
                "TestOrg"
        );

        org.setDefaultCurrency(
                "USD"
        );


        // --------------------------------------------------------
        // BORROWER
        // --------------------------------------------------------

        borrower = new Borrower();

        borrower.setId(1L);

        borrower.setFirstName(
                "John"
        );

        borrower.setLastName(
                "Doe"
        );

        borrower.setKycStatus(
                "VERIFIED"
        );

        borrower.setCreditScore(
                750
        );

        borrower.setOrganization(
                org
        );

        /*
         * LoanService uses monthlyIncome for DTI.
         *
         * Borrower.monthlyIncome is BigDecimal.
         */
        borrower.setMonthlyIncome(
                new BigDecimal("5000.00")
        );


        // --------------------------------------------------------
        // OFFICER
        // --------------------------------------------------------

        officer = new User();

        officer.setId(1L);

        officer.setName(
                "Test Officer"
        );

        officer.setOrganization(
                org
        );
    }


    // ============================================================
    // CREATE LOAN
    // ============================================================

    @Test
    void createLoan_shouldSaveLoan_withAllFields() {

        LoanRequest req =
                new LoanRequest();

        req.setBorrowerId(
                1L
        );

        /*
         * LoanRequest.amount is BigDecimal.
         */
        req.setAmount(
                new BigDecimal("10000.00")
        );

        /*
         * LoanRequest.interestRate is BigDecimal.
         */
       req.setAmount(
    new BigDecimal("10000.00")
);

req.setInterestRate(
    12.0
);

req.setDurationMonths(12);

req.setCurrency("USD");

req.setStartDate("2026-01-01");

req.setCollateralValue(
    new BigDecimal("15000.00")
);

        req.setDurationMonths(
                12
        );

        req.setCurrency(
                "USD"
        );

        req.setStartDate(
                "2026-01-01"
        );

        /*
         * collateralValue is BigDecimal.
         */
        req.setCollateralValue(
                new BigDecimal("15000.00")
        );

        req.setCollateralDescription(
                "Land title"
        );


        // --------------------------------------------------------
        // SAVED LOAN
        // --------------------------------------------------------

        Loan savedLoan =
                new Loan();

        savedLoan.setId(
                1L
        );

        savedLoan.setBorrower(
                borrower
        );

        savedLoan.setOrganization(
                org
        );

        /*
         * Loan.amount is BigDecimal.
         */
        savedLoan.setAmount(
                new BigDecimal("10000.00")
        );

        savedLoan.setStatus(
                LoanStatus.PENDING
        );


        // --------------------------------------------------------
        // MOCK ORGANIZATION
        // --------------------------------------------------------

        when(
                organizationRepository.findById(1L)
        )
                .thenReturn(
                        Optional.of(org)
                );


        // --------------------------------------------------------
        // MOCK BORROWER
        // --------------------------------------------------------

        when(
                borrowerRepository.findById(1L)
        )
                .thenReturn(
                        Optional.of(borrower)
                );


        // --------------------------------------------------------
        // NO LOAN PRODUCT
        // --------------------------------------------------------

        when(
                loanProductRepository
                        .findFirstByOrganization_IdAndLoanTypeAndActiveTrue(
                                eq(1L),
                                any(Loan.LoanType.class)
                        )
        )
                .thenReturn(
                        Optional.empty()
                );


        // --------------------------------------------------------
        // SAVE
        // --------------------------------------------------------

        when(
                loanRepository.save(
                        any(Loan.class)
                )
        )
                .thenReturn(
                        savedLoan
                );


        // --------------------------------------------------------
        // RISK SCORING
        // --------------------------------------------------------

        when(
                riskScoringService.score(
                        any(Loan.class)
                )
        )
                .thenReturn(
                        new RiskScoringService.RiskResult(
                                80.0,
                                "LOW"
                        )
                );


        // --------------------------------------------------------
        // CREATE
        // --------------------------------------------------------

        Loan result =
                loanService.createLoan(
                        req,
                        1L,
                        officer
                );


        // --------------------------------------------------------
        // ASSERTIONS
        // --------------------------------------------------------

        assertThat(
                result
        )
                .isNotNull();

        assertThat(
                result.getStatus()
        )
                .isEqualTo(
                        LoanStatus.PENDING
                );

        verify(
                loanRepository,
                atLeastOnce()
        )
                .save(
                        any(Loan.class)
                );
    }


    // ============================================================
    // CREATE LOAN - BORROWER NOT FOUND
    // ============================================================

    @Test
    void createLoan_shouldThrow_whenBorrowerNotFound() {

        LoanRequest req =
                new LoanRequest();

        req.setBorrowerId(
                99L
        );

        req.setAmount(
                new BigDecimal("1000.00")
        );

        req.setAmount(
    new BigDecimal("1000.00")
);

req.setInterestRate(
    10.0
);

req.setDurationMonths(6);

req.setCurrency("USD");

req.setStartDate("2026-01-01");

        req.setDurationMonths(
                6
        );

        req.setCurrency(
                "USD"
        );

        req.setStartDate(
                "2026-01-01"
        );


        // --------------------------------------------------------
        // ORGANIZATION EXISTS
        // --------------------------------------------------------

        when(
                organizationRepository.findById(1L)
        )
                .thenReturn(
                        Optional.of(org)
                );


        // --------------------------------------------------------
        // BORROWER DOES NOT EXIST
        // --------------------------------------------------------

        when(
                borrowerRepository.findById(99L)
        )
                .thenReturn(
                        Optional.empty()
                );


        // --------------------------------------------------------
        // ASSERT
        // --------------------------------------------------------

        assertThatThrownBy(
                () ->
                        loanService.createLoan(
                                req,
                                1L,
                                officer
                        )
        )
                .isInstanceOf(
                        RuntimeException.class
                )
                .hasMessageContaining(
                        "Borrower not found"
                );
    }


    // ============================================================
    // APPROVE LOAN
    // ============================================================

    @Test
    void approveLoan_shouldSetStatusApproved_andGenerateSchedule() {

        Loan loan =
                new Loan();

        loan.setId(
                1L
        );

        loan.setStatus(
                LoanStatus.PENDING
        );

        /*
         * Loan.amount is BigDecimal.
         */
        loan.setAmount(
                new BigDecimal("12000.00")
        );

        /*
         * Loan.interestRate is BigDecimal.
         */
        loan.setInterestRate(
                new BigDecimal("12.00")
        );

        loan.setInterestRateType(
                "ANNUAL"
        );

        loan.setDurationMonths(
                12
        );

        loan.setStartDate(
                LocalDate.of(
                        2026,
                        1,
                        1
                )
        );

        loan.setBorrower(
                borrower
        );

        loan.setOrganization(
                org
        );


        // --------------------------------------------------------
        // LOAN LOOKUP
        // --------------------------------------------------------

        when(
                loanRepository.findById(1L)
        )
                .thenReturn(
                        Optional.of(loan)
                );


        // --------------------------------------------------------
        // REQUIRED DOCUMENTS
        //
        // We don't want the test to fail because of document
        // requirements. Return no missing documents.
        // --------------------------------------------------------

        when(
                borrowerFileService.getMissingDocumentTypes(
                        eq(1L),
                        anyList()
                )
        )
                .thenReturn(
                        java.util.List.of()
                );


        // --------------------------------------------------------
        // SAVE
        // --------------------------------------------------------

        when(
                loanRepository.save(
                        any(Loan.class)
                )
        )
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0)
                );


        // --------------------------------------------------------
        // EXISTING PAYMENTS
        //
        // Empty means LoanService will generate the schedule.
        // --------------------------------------------------------

        when(
                paymentRepository.findByLoanId(1L)
        )
                .thenReturn(
                        java.util.List.of()
                );


        // --------------------------------------------------------
        // PAYMENT SAVE
        // --------------------------------------------------------

        when(
                paymentRepository.save(
                        any(Payment.class)
                )
        )
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0)
                );


        // --------------------------------------------------------
        // HOLIDAY SERVICE
        //
        // Return the same date so the test doesn't depend on
        // holiday configuration.
        // --------------------------------------------------------

        when(
                holidayService.adjustToBusinessDay(
                        anyLong(),
                        any(LocalDate.class)
                )
        )
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(1)
                );


        // --------------------------------------------------------
        // APPROVE
        // --------------------------------------------------------

        Loan result =
                loanService.approveLoan(
                        1L,
                        officer,
                        null,
                        null
                );


        // --------------------------------------------------------
        // ASSERT
        // --------------------------------------------------------

        assertThat(
                result.getStatus()
        )
                .isEqualTo(
                        LoanStatus.APPROVED
                );


        verify(
                paymentRepository,
                times(12)
        )
                .save(
                        any(Payment.class)
                );
    }


    // ============================================================
    // APPROVE LOAN - ALREADY APPROVED
    // ============================================================

    @Test
    void approveLoan_shouldThrow_whenAlreadyApproved() {

        Loan loan =
                new Loan();

        loan.setId(
                1L
        );

        loan.setStatus(
                LoanStatus.APPROVED
        );

        loan.setOrganization(
                org
        );


        // --------------------------------------------------------
        // FIND LOAN
        // --------------------------------------------------------

        when(
                loanRepository.findById(1L)
        )
                .thenReturn(
                        Optional.of(loan)
                );


        // --------------------------------------------------------
        // ASSERT
        // --------------------------------------------------------

        assertThatThrownBy(
                () ->
                        loanService.approveLoan(
                                1L,
                                officer,
                                null,
                                null
                        )
        )
                .isInstanceOf(
                        RuntimeException.class
                )
                .hasMessageContaining(
                        "Cannot approve a loan that is APPROVED"
                );
    }
}