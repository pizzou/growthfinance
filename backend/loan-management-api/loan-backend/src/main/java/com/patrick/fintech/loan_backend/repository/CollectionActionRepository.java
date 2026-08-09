package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.CollectionAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CollectionActionRepository extends JpaRepository<CollectionAction, Long> {
    List<CollectionAction> findByCollectionCase_IdOrderByCreatedAtDesc(Long caseId);
}
