package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanProductRepository extends JpaRepository<LoanProduct, Long> {
    List<LoanProduct> findByOrganization_IdOrderByDisplayOrderAsc(Long orgId);
    List<LoanProduct> findByOrganization_IdAndActiveTrueOrderByDisplayOrderAsc(Long orgId);
    Optional<LoanProduct> findFirstByOrganization_IdAndLoanTypeAndActiveTrue(Long orgId, Loan.LoanType loanType);
}
