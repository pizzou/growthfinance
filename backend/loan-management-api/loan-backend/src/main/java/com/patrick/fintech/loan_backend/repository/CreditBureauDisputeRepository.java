package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.CreditBureauDispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CreditBureauDisputeRepository extends JpaRepository<CreditBureauDispute, Long> {
    List<CreditBureauDispute> findByOrganization_IdOrderByCreatedAtDesc(Long orgId);
    List<CreditBureauDispute> findByOrganization_IdAndStatusOrderByCreatedAtDesc(Long orgId, CreditBureauDispute.Status status);
    List<CreditBureauDispute> findByBorrower_IdOrderByCreatedAtDesc(Long borrowerId);
    Optional<CreditBureauDispute> findByIdAndOrganization_Id(Long id, Long orgId);
}