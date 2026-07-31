package com.patrick.fintech.loan_backend.service;


import com.patrick.fintech.loan_backend.dto.publicportal.PaymentScheduleResponse;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.PaymentSchedule;
import com.patrick.fintech.loan_backend.model.PaymentSchedule.ScheduleStatus;
import com.patrick.fintech.loan_backend.repository.PaymentScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentScheduleService {

    private final PaymentScheduleRepository repository;

    public List<PaymentScheduleResponse> getSchedule(Long loanId) {

        return repository.findByLoanIdOrderByInstallmentNumberAsc(loanId)
                .stream()
                .map(s -> PaymentScheduleResponse.builder()
                        .installmentNumber(s.getInstallmentNumber())
                        .dueDate(s.getDueDate())
                        .installmentAmount(s.getInstallmentAmount())
                        .principal(s.getPrincipalAmount())
                        .interest(s.getInterestAmount())
                        .penalty(s.getPenaltyAmount())
                        .paid(s.getAmountPaid())
                        .balance(s.getRemainingBalance())
                        .status(s.getStatus().name())
                        .build())
                .toList();
    }

    /**
     * Generate a simple equal-installment repayment schedule.
     */
    public void generateSchedule(Loan loan) {

    repository.deleteByLoanId(loan.getId());

    int months = loan.getDurationMonths();

    if (months <= 0) {
        return;
    }

    double principal = loan.getAmount();
    double rate = loan.getInterestRate();

    // Determine the monthly rate based on the interest rate type
    double monthlyRate;

    if ("MONTHLY".equalsIgnoreCase(loan.getInterestRateType())) {
        monthlyRate = rate / 100.0;
    } else {
        monthlyRate = rate / 100.0 / 12.0;
    }

    /*
     * Special case:
     * One-month loans should simply repay:
     * Principal + One Month Interest
     */
    if (months == 1) {

        double interest = principal * monthlyRate;
        double total = principal + interest;

        PaymentSchedule schedule = new PaymentSchedule();

        schedule.setLoan(loan);
        schedule.setInstallmentNumber(1);
        schedule.setDueDate(loan.getDisbursedAt().plusMonths(1));
        schedule.setInstallmentAmount(total);
        schedule.setPrincipalAmount(principal);
        schedule.setInterestAmount(interest);
        schedule.setPenaltyAmount(0.0);
        schedule.setAmountPaid(0.0);
        schedule.setRemainingBalance(0.0);
        schedule.setStatus(ScheduleStatus.PENDING);

        repository.save(schedule);
        return;
    }

    /*
     * Multi-month loans
     * Uses the normal EMI (reducing balance) calculation.
     */

    double monthlyPayment;

    if (monthlyRate == 0) {
        monthlyPayment = principal / months;
    } else {
        monthlyPayment =
                (principal * monthlyRate)
                        / (1 - Math.pow(1 + monthlyRate, -months));
    }

    double balance = principal;

    for (int i = 1; i <= months; i++) {

        double interest = balance * monthlyRate;
        double principalPart = monthlyPayment - interest;

        balance -= principalPart;

        if (balance < 0) {
            balance = 0;
        }

        PaymentSchedule schedule = new PaymentSchedule();

        schedule.setLoan(loan);
        schedule.setInstallmentNumber(i);
        schedule.setDueDate(loan.getDisbursedAt().plusMonths(i));
        schedule.setInstallmentAmount(monthlyPayment);
        schedule.setPrincipalAmount(principalPart);
        schedule.setInterestAmount(interest);
        schedule.setPenaltyAmount(0.0);
        schedule.setAmountPaid(0.0);
        schedule.setRemainingBalance(balance);
        schedule.setStatus(ScheduleStatus.PENDING);

        repository.save(schedule);
    }
}

   public PaymentSchedule getNextInstallment(Long loanId) {

    return repository
            .findFirstByLoanIdAndStatusOrderByInstallmentNumberAsc(
                    loanId,
                    ScheduleStatus.PENDING)
            .orElse(null);
}
}