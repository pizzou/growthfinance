package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.PaymentSchedule;
import com.patrick.fintech.loan_backend.repository.PaymentScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PaymentScheduleGeneratorService {

    private final PaymentScheduleRepository repository;

    public void generate(Loan loan){

        repository.deleteByLoanId(loan.getId());

        int months = loan.getDurationMonths();

        double principal = loan.getAmount();

        double rate = loan.getInterestRate();

        String rateType = loan.getInterestRateType() != null ? loan.getInterestRateType() : "MONTHLY";

        double monthlyRate = "MONTHLY".equalsIgnoreCase(rateType) ? rate / 100 : rate / 100 / 12;

        double installmentAmount = monthlyRate == 0
                ? principal / months
                : principal * (monthlyRate * Math.pow(1 + monthlyRate, months))
                        / (Math.pow(1 + monthlyRate, months) - 1);

        double remaining = principal;

        LocalDate dueDate = loan.getStartDate();

        for(int i=1;i<=months;i++){

            double interest = remaining * monthlyRate;

            double installment = (i == months) ? (remaining + interest) : installmentAmount;

            double monthlyPrincipal = installment - interest;

            remaining -= monthlyPrincipal;

            PaymentSchedule schedule = PaymentSchedule.builder()

                    .loan(loan)

                    .installmentNumber(i)

                    .dueDate(dueDate.plusMonths(i))

                    .principalAmount(round(monthlyPrincipal))

                    .interestAmount(round(interest))

                    .installmentAmount(round(installment))

                    .remainingBalance(round(Math.max(remaining,0)))

                    .status(PaymentSchedule.ScheduleStatus.PENDING)

                    .build();

            repository.save(schedule);

        }

    }

    private double round(double value){
        return Math.round(value*100.0)/100.0;
    }

}