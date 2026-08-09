package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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
     * Locks the loan row for the duration of the payment transaction.
     *
     * This is important for payment concurrency and does not
     * modify or reinterpret Loan.disbursedAt.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "borrower",
            "organization",
            "loanOfficer"
    })
    @Query("""
        SELECT l
        FROM Loan l
        WHERE l.id = :id
        """)
    Optional<Loan> findByIdForUpdate(
            @Param("id") Long id
    );


    /**
     * Find loan using its public reference number.
     */
    Optional<Loan> findByReferenceNumber(
            String referenceNumber
    );


    /**
     * Find a loan using reference number + hashed borrower phone.
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
     * Find all loans by borrower's hashed phone number.
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
    BigDecimal sumActivePrincipal(
            @Param("org") Organization org
    );


    @Query("""
        SELECT COALESCE(SUM(l.totalPaid), 0)
        FROM Loan l
        WHERE l.organization = :org
        """)
    BigDecimal sumTotalCollected(
            @Param("org") Organization org
    );


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
    BigDecimal sumOutstandingBalance(
            @Param("org") Organization org
    );


    // ============================================================
    // LOAN TYPE BREAKDOWN
    // ============================================================

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
     * IMPORTANT:
     *
     * Loan.disbursedAt is LocalDateTime.
     *
     * Therefore this repository method MUST also receive
     * LocalDateTime values.
     *
     * The service should convert:
     *
     * from date -> from.atStartOfDay()
     *
     * to date -> next day atStartOfDay()
     *
     * and use:
     *
     * disbursedAt >= fromDateTime
     * disbursedAt < toDateTime
     *
     * This preserves the exact time of disbursement.
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
          AND l.disbursedAt < :to
        ORDER BY l.disbursedAt ASC
        """)
    List<Loan> findLoansDisbursedDuringPeriod(
            @Param("orgId") Long orgId,
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );


    /**
     * Find portfolio as of a particular date/time.
     *
     * The service should pass the exclusive beginning of the
     * following day when it wants a complete LocalDate.
     *
     * Example:
     *
     * asOfDate = 2026-08-31
     *
     * asOfDateTime = 2026-09-01T00:00:00
     *
     * Query:
     *
     * l.disbursedAt < asOf
     *
     * This includes every loan disbursed on 2026-08-31,
     * regardless of its exact time.
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
          AND l.disbursedAt < :asOf
        ORDER BY l.disbursedAt ASC
        """)
    List<Loan> findPortfolioAsOf(
            @Param("orgId") Long orgId,
            @Param("branchId") Long branchId,
            @Param("asOf") LocalDateTime asOf
    );


    /**
     * Find loans for regulatory reporting.
     *
     * createdAt is also LocalDateTime, so the parameters correctly
     * remain LocalDateTime.
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


    // ============================================================
    // EXISTS
    // ============================================================

    boolean existsByOrganization_IdAndReferenceNumber(
            Long organizationId,
            String referenceNumber
    );
}