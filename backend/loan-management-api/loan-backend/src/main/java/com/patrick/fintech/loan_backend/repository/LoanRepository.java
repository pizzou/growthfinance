package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {

    // ============================================================
    // GENERAL
    // ============================================================

    /**
     * Find loan by ID and eagerly load the relationships that are
     * commonly required after the repository call.
     */
    @EntityGraph(attributePaths = {
            "borrower",
            "organization"
    })
    @Override
    Optional<Loan> findById(Long id);


    /**
     * Find loan using its public reference number.
     */
    Optional<Loan> findByReferenceNumber(
            String referenceNumber
    );


    /**
     * Find a loan using reference number + hashed borrower phone.
     *
     * This is useful for public borrower verification.
     */
    @EntityGraph(attributePaths = {
            "borrower",
            "organization"
    })
    Optional<Loan> findByReferenceNumberAndBorrower_PhoneHash(
            String referenceNumber,
            String phoneHash
    );


    /**
     * Public dashboard lookup.
     *
     * Borrower, organization and loan officer are loaded together
     * so PublicPortalService can safely access them after the
     * repository query without triggering:
     *
     * could not initialize proxy - no Session
     */
    @EntityGraph(attributePaths = {
            "borrower",
            "organization",
            "loanOfficer"
    })
    @Query("""
        SELECT l
        FROM Loan l
        WHERE l.referenceNumber = :referenceNumber
          AND l.borrower.phoneHash = :phoneHash
        """)
    Optional<Loan> findPublicDashboardLoan(
            @Param("referenceNumber") String referenceNumber,
            @Param("phoneHash") String phoneHash
    );


    /**
     * Find all loans belonging to an organization.
     */
    @EntityGraph(attributePaths = {
            "borrower",
            "organization"
    })
    List<Loan> findByOrganization_Id(
            Long organizationId
    );


    /**
     * Find all loans belonging to a borrower within an organization.
     */
    @EntityGraph(attributePaths = {
            "borrower",
            "organization"
    })
    List<Loan> findByBorrowerIdAndOrganizationId(
            Long borrowerId,
            Long organizationId
    );


    /**
     * Find loans whose status is one of the supplied statuses.
     */
    List<Loan> findByStatusIn(
            List<LoanStatus> statuses
    );


    /**
     * Find all loans by the borrower's hashed phone number.
     */
    List<Loan> findByBorrower_PhoneHash(
            String phoneHash
    );


    /**
     * Count all loans belonging to an organization.
     */
    long countByOrganization(
            Organization organization
    );


    /**
     * Count loans by organization ID.
     */
    long countByOrganization_Id(
            Long organizationId
    );


    /**
     * Count loans by organization and status.
     */
    long countByOrganizationAndStatus(
            Organization organization,
            LoanStatus status
    );


    /**
     * Count loans by organization ID and status.
     */
    long countByOrganization_IdAndStatus(
            Long organizationId,
            LoanStatus status
    );


    // ============================================================
    // FILTERING
    // ============================================================

    /**
     * Filter organization loans by status and loan type.
     */
    @EntityGraph(attributePaths = {
            "borrower",
            "organization"
    })
    @Query("""
        SELECT l
        FROM Loan l
        WHERE l.organization = :org
          AND (:status IS NULL OR l.status = :status)
          AND (:type IS NULL OR l.loanType = :type)
        ORDER BY l.createdAt DESC
        """)
    Page<Loan> findByFilters(
            @Param("org") Organization org,
            @Param("status") LoanStatus status,
            @Param("type") Loan.LoanType type,
            Pageable pageable
    );


    // ============================================================
    // DASHBOARD
    // ============================================================

    /**
     * Sum active principal.
     */
    @Query("""
        SELECT COALESCE(SUM(l.amount), 0)
        FROM Loan l
        WHERE l.organization = :org
          AND l.status IN (
              'ACTIVE',
              'DISBURSED',
              'OVERDUE'
          )
        """)
    Double sumActivePrincipal(
            @Param("org") Organization org
    );


    /**
     * Sum total collected.
     */
    @Query("""
        SELECT COALESCE(SUM(l.totalPaid), 0)
        FROM Loan l
        WHERE l.organization = :org
        """)
    Double sumTotalCollected(
            @Param("org") Organization org
    );


    /**
     * Sum outstanding balance.
     */
    @Query("""
        SELECT COALESCE(SUM(l.outstandingBalance), 0)
        FROM Loan l
        WHERE l.organization = :org
          AND l.status IN (
              'ACTIVE',
              'DISBURSED',
              'OVERDUE'
          )
        """)
    Double sumOutstandingBalance(
            @Param("org") Organization org
    );


    // ============================================================
    // LOAN TYPE BREAKDOWN
    // ============================================================

    /**
     * Get loan type breakdown.
     */
    @Query("""
        SELECT l.loanType,
               COUNT(l),
               COALESCE(SUM(l.amount), 0)
        FROM Loan l
        WHERE l.organization = :org
        GROUP BY l.loanType
        """)
    List<Object[]> getLoanTypeBreakdown(
            @Param("org") Organization org
    );


    // ============================================================
    // RECENT
    // ============================================================

    /**
     * Find recent loans for an organization.
     */
    @EntityGraph(attributePaths = {
            "borrower",
            "organization"
    })
    @Query("""
        SELECT l
        FROM Loan l
        WHERE l.organization = :org
        ORDER BY l.createdAt DESC
        """)
    List<Loan> findRecentByOrg(
            @Param("org") Organization org,
            Pageable pageable
    );


    // ============================================================
    // REGULATORY
    // ============================================================

    /**
     * Find loans disbursed during a period.
     */
    @EntityGraph(attributePaths = {
            "borrower",
            "organization",
            "branch"
    })
    @Query("""
        SELECT l
        FROM Loan l
        WHERE l.organization.id = :orgId
          AND (:branchId IS NULL OR l.branch.id = :branchId)
          AND l.disbursedAt IS NOT NULL
          AND l.disbursedAt >= :from
          AND l.disbursedAt <= :to
        ORDER BY l.disbursedAt ASC
        """)
    List<Loan> findLoansDisbursedDuringPeriod(
            @Param("orgId") Long orgId,
            @Param("branchId") Long branchId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );


    /**
     * Find portfolio as of a particular date.
     */
    @EntityGraph(attributePaths = {
            "borrower",
            "organization",
            "branch",
            "payments"
    })
    @Query("""
        SELECT DISTINCT l
        FROM Loan l
        WHERE l.organization.id = :orgId
          AND (:branchId IS NULL OR l.branch.id = :branchId)
          AND l.disbursedAt IS NOT NULL
          AND l.disbursedAt <= :asOf
        ORDER BY l.disbursedAt ASC
        """)
    List<Loan> findPortfolioAsOf(
            @Param("orgId") Long orgId,
            @Param("branchId") Long branchId,
            @Param("asOf") LocalDate asOf
    );


    /**
     * Find loans for regulatory reporting.
     */
    @EntityGraph(attributePaths = {
            "borrower",
            "organization",
            "branch",
            "payments"
    })
    @Query("""
        SELECT DISTINCT l
        FROM Loan l
        WHERE l.organization.id = :orgId
          AND (:branchId IS NULL OR l.branch.id = :branchId)
          AND (:from IS NULL OR l.createdAt >= :from)
          AND (:to IS NULL OR l.createdAt < :to)
        ORDER BY l.createdAt DESC
        """)
    List<Loan> findForRegulatoryReport(
            @Param("orgId") Long orgId,
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );


    /**
     * Find loans created during a period.
     */
    @Query("""
        SELECT l
        FROM Loan l
        WHERE l.organization.id = :orgId
          AND (:branchId IS NULL OR l.branch.id = :branchId)
          AND l.createdAt >= :from
          AND l.createdAt < :to
        ORDER BY l.createdAt ASC
        """)
    List<Loan> findLoansCreatedDuringPeriod(
            @Param("orgId") Long orgId,
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

}