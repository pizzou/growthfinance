package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.LoanApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanApprovalRepository extends JpaRepository<LoanApproval, Long> {
    List<LoanApproval> findByLoan_IdOrderByStepOrderAsc(Long loanId);
    Optional<LoanApproval> findFirstByLoan_IdAndStatusOrderByStepOrderAsc(Long loanId, String status);
    long countByLoan_IdAndStatus(Long loanId, String status);
}