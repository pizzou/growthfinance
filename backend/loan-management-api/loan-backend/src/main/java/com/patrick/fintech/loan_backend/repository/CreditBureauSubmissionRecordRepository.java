package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.CreditBureauSubmissionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CreditBureauSubmissionRecordRepository extends JpaRepository<CreditBureauSubmissionRecord, Long> {

    List<CreditBureauSubmissionRecord> findByOrganization_IdAndReportingPeriodOrderByIdAsc(Long orgId, String period);

    List<CreditBureauSubmissionRecord> findByLoan_IdOrderByCreatedAtDesc(Long loanId);

    List<CreditBureauSubmissionRecord> findBySubmission_Id(Long submissionId);

    @Query("""
        SELECT r FROM CreditBureauSubmissionRecord r
        WHERE r.loan.id = :loanId AND r.reportingPeriod = :period
        ORDER BY r.createdAt DESC
        """)
    List<CreditBureauSubmissionRecord> findVersionsForLoanPeriod(@Param("loanId") Long loanId, @Param("period") String period);

    default Optional<CreditBureauSubmissionRecord> findLatestForLoanPeriod(Long loanId, String period) {
        List<CreditBureauSubmissionRecord> versions = findVersionsForLoanPeriod(loanId, period);
        return versions.isEmpty() ? Optional.empty() : Optional.of(versions.get(0));
    }
}