package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.CreditBureauCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CreditBureauCheckRepository extends JpaRepository<CreditBureauCheck, Long> {
    List<CreditBureauCheck> findByBorrower_IdOrderByCreatedAtDesc(Long borrowerId);
    List<CreditBureauCheck> findByOrganization_Id(Long orgId);
    Optional<CreditBureauCheck> findFirstByBorrower_IdOrderByCreatedAtDesc(Long borrowerId);
}
