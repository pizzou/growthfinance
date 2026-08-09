package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.AuditLogRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    LoanRepository loanRepository;

    @Mock
    AuditLogRepository auditLogRepository;

    @Mock
    AuditService auditService;

    @Mock
    UserRepository userRepository;

    @Mock
    NotificationService notificationService;

    @Mock
    MailService mailService;

    @Mock
    SmsService smsService;

    @Mock
    WebhookService webhookService;

    @Mock
    AccountingService accountingService;

    @InjectMocks
    PaymentService paymentService;

    private Organization org;
    private Loan loan;
    private User teller;

    @BeforeEach
    void setUp() {

        org = new Organization();
        org.setId(1L);
        org.setName("TestOrg");
        org.setDefaultCurrency("USD");

        loan = new Loan();
        loan.setId(1L);

        loan.setStatus(
                LoanStatus.ACTIVE
        );

        /*
         * IMPORTANT:
         * Use the setters that actually exist in Loan.
         * Do NOT use setAmountDecimal(),
         * setInterestRateDecimal(), etc.
         */
        loan.setAmount(
                12000.0
        );

        loan.setInterestRate(
                12.0
        );

        loan.setDurationMonths(
                12
        );

        loan.setTotalRepayable(
                12800.0
        );

        loan.setOutstandingBalance(
                12800.0
        );

        loan.setTotalPaid(
                0.0
        );

        loan.setOrganization(
                org
        );

        teller = new User();
        teller.setId(1L);
        teller.setName("Test Teller");
        teller.setOrganization(org);
    }

    @Test
    void recordPayment_shouldMarkInstallmentPaid_andUpdateLoanBalance() {

        Payment installment = new Payment();

        installment.setId(1L);

        installment.setPaid(
                false
        );

        installment.setAmount(
                1066.67
        );

        installment.setPenalty(
                0.0
        );

        installment.setDueDate(
                LocalDate.now().plusDays(5)
        );

        installment.setLoan(
                loan
        );

        installment.setOrganization(
                org
        );

        installment.setAmountPaid(
                BigDecimal.ZERO
        );

        installment.setPrincipalComponent(
                0.0
        );

        installment.setInterestComponent(
                0.0
        );

        installment.setCycleInterestDue(
                0.0
        );

        installment.setCycleInterestRemaining(
                0.0
        );

        installment.setOutstandingAfter(
                12800.0
        );

        when(
                loanRepository.findById(1L)
        ).thenReturn(
                Optional.of(loan)
        );

        when(
                paymentRepository.findByLoanId(1L)
        ).thenReturn(
                List.of(installment)
        );

        when(
                paymentRepository.save(any(Payment.class))
        ).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        when(
                loanRepository.save(any(Loan.class))
        ).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        Payment result =
                paymentService.recordPayment(
                        1L,
                        BigDecimal.valueOf(1066.67),
                        "CASH",
                        null,
                        null,
                        null,
                        teller
                );

        assertThat(result)
                .isNotNull();

        assertThat(result.getPaid())
                .isTrue();

        assertThat(result.getPaidDate())
                .isEqualTo(
                        LocalDate.now()
                );

        /*
         * getPenalty() is a legacy Double getter.
         * Therefore compare it with a Double,
         * NOT BigDecimal.
         */
        assertThat(result.getPenalty())
                .isEqualByComparingTo(
                        0.0
                );
    }

    @Test
    void recordPayment_shouldApplyPenalty_whenInstallmentOverdue() {

        Payment installment = new Payment();

        installment.setId(1L);

        installment.setPaid(
                false
        );

        installment.setAmount(
                1066.67
        );

        installment.setPenalty(
                0.0
        );

        installment.setDueDate(
                LocalDate.now().minusDays(10)
        );

        installment.setLoan(
                loan
        );

        installment.setOrganization(
                org
        );

        installment.setAmountPaid(
                BigDecimal.ZERO
        );

        installment.setPrincipalComponent(
                0.0
        );

        installment.setInterestComponent(
                0.0
        );

        installment.setCycleInterestDue(
                0.0
        );

        installment.setCycleInterestRemaining(
                0.0
        );

        installment.setOutstandingAfter(
                12800.0
        );

        when(
                loanRepository.findById(1L)
        ).thenReturn(
                Optional.of(loan)
        );

        when(
                paymentRepository.findByLoanId(1L)
        ).thenReturn(
                List.of(installment)
        );

        when(
                paymentRepository.save(any(Payment.class))
        ).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        when(
                loanRepository.save(any(Loan.class))
        ).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        Payment result =
                paymentService.recordPayment(
                        1L,
                        BigDecimal.valueOf(1066.67),
                        "BANK_TRANSFER",
                        null,
                        null,
                        "Late payment",
                        teller
                );

        assertThat(result)
                .isNotNull();

        assertThat(result.getPaid())
                .isTrue();

        assertThat(result.isLate())
                .isTrue();

        assertThat(result.getDaysLate())
                .isGreaterThan(0);

        /*
         * getPenalty() returns Double in your current
         * Payment entity, so compare using Double.
         */
        assertThat(result.getPenalty())
                .isGreaterThan(0.0);
    }

    @Test
    void recordPayment_shouldThrow_whenLoanNotActive() {

        loan.setStatus(
                LoanStatus.PENDING
        );

        when(
                loanRepository.findById(1L)
        ).thenReturn(
                Optional.of(loan)
        );

        assertThatThrownBy(
                () ->
                        paymentService.recordPayment(
                                1L,
                                BigDecimal.valueOf(500.0),
                                "CASH",
                                null,
                                null,
                                null,
                                teller
                        )
        )
                .isInstanceOf(
                        RuntimeException.class
                )
                .hasMessageContaining(
                        "not active"
                );
    }
}