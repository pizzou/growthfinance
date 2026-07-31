package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    List<ImportBatch> findByOrganization_IdOrderByCreatedAtDesc(Long organizationId);
}