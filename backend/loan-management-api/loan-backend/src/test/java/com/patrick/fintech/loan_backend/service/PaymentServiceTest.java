package com.patrick.fintech.loan_backend.service;

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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {


@Mock
PaymentRepository paymentRepository;

@Mock
LoanRepository loanRepository;

@Mock
AuditLogRepository auditLogRepository;

@Mock
UserRepository userRepository;

@Mock
NotificationService notificationService;

@Mock
WebhookService webhookService;

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
    loan.setStatus(LoanStatus.ACTIVE);

    // Loan financial fields are BigDecimal
    loan.setAmount(
            BigDecimal.valueOf(12000.00)
    );

    loan.setInterestRate(
            BigDecimal.valueOf(12.00)
    );

    loan.setDurationMonths(12);

    loan.setTotalRepayable(
            BigDecimal.valueOf(12800.00)
    );

    loan.setOutstandingBalance(
            BigDecimal.valueOf(12800.00)
    );

    loan.setTotalPaid(
            BigDecimal.ZERO
    );

    loan.setOrganization(org);

    teller = new User();
    teller.setId(1L);
    teller.setName("Test Teller");
    teller.setOrganization(org);
}

// ============================================================
// RECORD PAYMENT
// ============================================================

@Test
void recordPayment_shouldMarkInstallmentPaid_andUpdateLoanBalance() {

    Payment installment = new Payment();

    installment.setId(1L);
    installment.setPaid(false);

    installment.setAmount(
            BigDecimal.valueOf(1066.67)
    );

    installment.setPenalty(
            BigDecimal.ZERO
    );

    installment.setDueDate(
            LocalDate.now().plusDays(5)
    );

    installment.setLoan(loan);

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
            paymentRepository.save(any())
    ).thenAnswer(
            inv -> inv.getArgument(0)
    );

    when(
            loanRepository.save(any())
    ).thenReturn(
            loan
    );

    /*
     * recordPayment(
     *     Long loanId,
     *     Double amount,
     *     String method,
     *     String txnId,
     *     String channel,
     *     String notes,
     *     User recordedBy
     * )
     */

    Payment result =
            paymentService.recordPayment(
                    1L,
                    1066.67,
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

    assertThat(result.getPenalty())
            .isEqualByComparingTo(
                    BigDecimal.ZERO
            );
}

// ============================================================
// OVERDUE PAYMENT
// ============================================================

@Test
void recordPayment_shouldApplyPenalty_whenInstallmentOverdue() {

    Payment installment = new Payment();

    installment.setId(1L);
    installment.setPaid(false);

    installment.setAmount(
            BigDecimal.valueOf(1066.67)
    );

    installment.setPenalty(
            BigDecimal.ZERO
    );

    installment.setDueDate(
            LocalDate.now().minusDays(10)
    );

    installment.setLoan(loan);

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
            paymentRepository.save(any())
    ).thenAnswer(
            inv -> inv.getArgument(0)
    );

    when(
            loanRepository.save(any())
    ).thenReturn(
            loan
    );

    Payment result =
            paymentService.recordPayment(
                    1L,
                    1066.67,
                    "BANK_TRANSFER",
                    null,
                    null,
                    "Late payment",
                    teller
            );

    assertThat(result.getPaid())
            .isTrue();

    assertThat(result.isLate())
            .isTrue();

    assertThat(result.getDaysLate())
            .isGreaterThan(0);
}

// ============================================================
// LOAN NOT ACTIVE
// ============================================================

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
                            500.0,
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
