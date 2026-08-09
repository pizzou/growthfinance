package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.JournalLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {
    List<JournalLine> findByJournalEntry_Organization_Id(Long orgId);
    List<JournalLine> findByAccount_Id(Long accountId);

    @Query("SELECT l FROM JournalLine l JOIN FETCH l.journalEntry e WHERE l.account.id = :accountId " +
           "ORDER BY e.entryDate ASC, e.id ASC")
    List<JournalLine> findLedgerForAccount(@Param("accountId") Long accountId);

    @Query("SELECT l FROM JournalLine l JOIN l.journalEntry e WHERE l.account.id = :accountId " +
           "AND e.sourceType IN ('INTEREST_ACCRUAL', 'PAYMENT_RECEIVED') AND l.description LIKE CONCAT('%', :loanReference)")
    List<JournalLine> findAccrualLinesForLoan(@Param("accountId") Long accountId, @Param("loanReference") String loanReference);
}
