package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.CollectionCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CollectionCaseRepository extends JpaRepository<CollectionCase, Long> {
    Optional<CollectionCase> findByLoan_Id(Long loanId);
    List<CollectionCase> findByOrganization_Id(Long orgId);
    List<CollectionCase> findByOrganization_IdAndBucket(Long orgId, CollectionCase.CollectionBucket bucket);
    List<CollectionCase> findByOrganization_IdAndStatus(Long orgId, CollectionCase.CollectionStatus status);
    List<CollectionCase> findByAssignedAgent_Id(Long agentId);
    List<CollectionCase> findByOrganization_IdAndStatusNotIn(Long orgId, List<CollectionCase.CollectionStatus> statuses);
}
