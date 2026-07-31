package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {

    Optional<Loan> findByReferenceNumber(String ref);

    @Query("SELECT l FROM Loan l WHERE l.organization = :org " +
           "AND (:status IS NULL OR l.status = :status) " +
           "AND (:type IS NULL OR l.loanType = :type) " +
           "ORDER BY l.createdAt DESC")
    Page<Loan> findByFilters(@Param("org") Organization org,
                             @Param("status") LoanStatus status,
                             @Param("type") Loan.LoanType type,
                             Pageable pageable);

    List<Loan> findByOrganization_Id(Long orgId);
    long countByOrganization(Organization org);
    long countByOrganizationAndStatus(Organization org, LoanStatus status);

    @Query("SELECT COALESCE(SUM(l.amount),0) FROM Loan l WHERE l.organization=:org AND l.status IN ('ACTIVE','DISBURSED','OVERDUE')")
    Double sumActivePrincipal(@Param("org") Organization org);

    @Query("SELECT COALESCE(SUM(l.totalPaid),0) FROM Loan l WHERE l.organization=:org")
    Double sumTotalCollected(@Param("org") Organization org);

    @Query("SELECT COALESCE(SUM(l.outstandingBalance),0) FROM Loan l WHERE l.organization=:org AND l.status='ACTIVE'")
    Double sumOutstandingBalance(@Param("org") Organization org);

    @Query("SELECT l.loanType, COUNT(l), COALESCE(SUM(l.amount),0) FROM Loan l WHERE l.organization=:org GROUP BY l.loanType")
    List<Object[]> getLoanTypeBreakdown(@Param("org") Organization org);

    @Query("SELECT l FROM Loan l WHERE l.organization=:org ORDER BY l.createdAt DESC")
    List<Loan> findRecentByOrg(@Param("org") Organization org, Pageable pageable);

    List<Loan> findByBorrowerIdAndOrganizationId(Long borrowerId, Long orgId);

    // Used by DashboardService
    long countByOrganization_Id(Long orgId);
    long countByOrganization_IdAndStatus(Long orgId, LoanStatus status);

    // Used by CollectionsService
    List<Loan> findByStatusIn(List<LoanStatus> statuses);

    List<Loan> findByBorrower_PhoneHash(String phoneHash);

    /**
 * Used by the public borrower dashboard.
 */
Optional<Loan> findByReferenceNumberAndBorrower_PhoneHash(
        String referenceNumber,
        String phoneHash
);

// ---- Regulatory reporting (BNR / Credit Bureau) ----
    @Query("SELECT l FROM Loan l WHERE l.organization.id=:orgId " +
           "AND (:branchId IS NULL OR l.branch.id=:branchId) " +
           "AND (cast(:from as timestamp) IS NULL OR l.createdAt >= :from) " +
           "AND (cast(:to as timestamp) IS NULL OR l.createdAt < :to)")
    List<Loan> findForRegulatoryReport(@Param("orgId") Long orgId,
                                        @Param("branchId") Long branchId,
                                        @Param("from") java.time.LocalDateTime from,
                                        @Param("to") java.time.LocalDateTime to);

}