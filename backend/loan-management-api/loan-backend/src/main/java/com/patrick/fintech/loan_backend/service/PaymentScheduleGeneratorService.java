package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.PaymentSchedule;
import com.patrick.fintech.loan_backend.repository.PaymentScheduleRepository;
import com.patrick.fintech.loan_backend.util.MoneyMath;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PaymentScheduleGeneratorService {

    private final PaymentScheduleRepository repository;

    @Transactional
    public void generate(Loan loan) {
        if (loan == null) {
            throw new IllegalArgumentException("Loan is required");
        }
        if (loan.getId() == null) {
            throw new IllegalArgumentException("Loan must be persisted before generating schedule");
        }
        if (loan.getAmount() == null || loan.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Loan amount must be greater than zero");
        }
        if (loan.getDurationMonths() == null || loan.getDurationMonths() <= 0) {
            throw new IllegalArgumentException("Loan duration must be greater than zero");
        }

        repository.deleteByLoanId(loan.getId());

        int months = loan.getDurationMonths();
        BigDecimal principal = MoneyMath.amount(loan.getAmount());
        BigDecimal rate = loan.getInterestRate() == null
                ? MoneyMath.ZERO
                : loan.getInterestRate();
        String rateType = loan.getInterestRateType() != null
                ? loan.getInterestRateType()
                : "MONTHLY";

        BigDecimal monthlyRate = "MONTHLY".equalsIgnoreCase(rateType)
                ? rate.divide(BigDecimal.valueOf(100), 12, MoneyMath.ROUNDING)
                : rate.divide(BigDecimal.valueOf(1200), 12, MoneyMath.ROUNDING);

        BigDecimal installmentAmount = calculateInstallment(principal, monthlyRate, months);
        BigDecimal remaining = principal;
        LocalDate startDate = loan.getStartDate() != null ? loan.getStartDate() : LocalDate.now();

        for (int i = 1; i <= months; i++) {
            BigDecimal interest = MoneyMath.amount(remaining.multiply(monthlyRate));
            BigDecimal installment = i == months
                    ? MoneyMath.amount(remaining.add(interest))
                    : installmentAmount;

            BigDecimal monthlyPrincipal = MoneyMath.amount(installment.subtract(interest));
            if (monthlyPrincipal.compareTo(remaining) > 0) {
                monthlyPrincipal = remaining;
            }

            remaining = MoneyMath.amount(remaining.subtract(monthlyPrincipal));
            if (remaining.signum() < 0) {
                remaining = MoneyMath.ZERO;
            }

            PaymentSchedule schedule = PaymentSchedule.builder()
                    .loan(loan)
                    .installmentNumber(i)
                    .dueDate(startDate.plusMonths(i))
                    .principalAmount(monthlyPrincipal)
                    .interestAmount(interest)
                    .installmentAmount(installment)
                    .remainingBalance(remaining)
                    .amountPaid(MoneyMath.ZERO)
                    .penaltyAmount(MoneyMath.ZERO)
                    .status(PaymentSchedule.ScheduleStatus.PENDING)
                    .build();

            repository.save(schedule);
        }
    }

    private BigDecimal calculateInstallment(
            BigDecimal principal,
            BigDecimal monthlyRate,
            int months) {

        if (monthlyRate.signum() == 0) {
            return MoneyMath.amount(
                    principal.divide(
                            BigDecimal.valueOf(months),
                            12,
                            RoundingMode.HALF_UP));
        }

        /*
         * EMI uses the standard annuity formula. Math.pow is used only for
         * the exponentiation because java.math.BigDecimal has no general
         * fractional-power operation. All monetary values are rounded through
         * MoneyMath at the monetary boundary.
         */
        double r = monthlyRate.doubleValue();
        double factor = Math.pow(1.0d + r, months);

        BigDecimal compound = BigDecimal.valueOf(factor);
        BigDecimal numerator = principal
                .multiply(monthlyRate)
                .multiply(compound);
        BigDecimal denominator = compound.subtract(BigDecimal.ONE);

        return MoneyMath.amount(
                numerator.divide(denominator, 12, RoundingMode.HALF_UP));
    }
}