package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.BorrowerFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BorrowerFileRepository extends JpaRepository<BorrowerFile, Long> {
    List<BorrowerFile> findByBorrowerId(Long borrowerId);
}