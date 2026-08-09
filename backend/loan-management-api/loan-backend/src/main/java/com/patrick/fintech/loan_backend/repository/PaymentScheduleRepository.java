package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.PaymentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentScheduleRepository
        extends JpaRepository<PaymentSchedule,Long> {

    List<PaymentSchedule> findByLoanIdOrderByInstallmentNumberAsc(Long loanId);

void deleteByLoanId(Long loanId);

Optional<PaymentSchedule> findFirstByLoanIdAndStatusOrderByInstallmentNumberAsc(
        Long loanId,
        PaymentSchedule.ScheduleStatus status
);
}
