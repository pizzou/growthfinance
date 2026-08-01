package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.CreditBureauSubmission;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;
import java.util.Optional;


public interface CreditBureauSubmissionRepository extends JpaRepository<CreditBureauSubmission, Long> {
    List<CreditBureauSubmission> findByOrganization_IdOrderByCreatedAtDesc(Long orgId);
    Optional<CreditBureauSubmission> findByIdAndOrganization_Id(Long id, Long orgId);
}