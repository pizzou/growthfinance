package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository
        extends JpaRepository<Payment, Long> {

    // ============================================================
    // BASIC QUERIES
    // ============================================================

    List<Payment> findByLoanId(Long loanId);

    List<Payment> findByLoan_Organization_Id(Long orgId);

    List<Payment> findByPaidFalseAndDueDateBefore(
            LocalDate date
    );

    List<Payment> findByOrganization_IdAndPaidFalseAndDueDateBefore(
            Long orgId,
            LocalDate date
    );

    Optional<Payment> findByPaymentReference(
            String ref
    );

    /**
     * Returns the most recent payment for a loan that has a
     * payment date.
     *
     * Used as a fallback when determining the date from which
     * daily interest should start accruing.
     */
    Optional<Payment> findTopByLoanIdAndPaidDateIsNotNullOrderByPaidDateDesc(
            Long loanId
    );


    // ============================================================
    // LOAN PAYMENT SCHEDULE
    // ============================================================

    /**
     * Tenant-safe loan schedule.
     *
     * Returns only payments belonging to the specified loan
     * AND organization.
     */
    @Query("""
        SELECT p
        FROM Payment p
        WHERE p.loan.id = :loanId
          AND p.organization.id = :organizationId
        ORDER BY p.dueDate ASC
        """)
    List<Payment> findLoanSchedule(
            @Param("loanId") Long loanId,
            @Param("organizationId") Long organizationId
    );


    /**
     * Alternative simple schedule query.
     */
    List<Payment> findByLoanIdOrderByDueDateAsc(
            Long loanId
    );


    // ============================================================
    // BORROWER PAYMENT HISTORY
    // ============================================================

    @Query("""
        SELECT p
        FROM Payment p
        JOIN p.loan l
        JOIN l.borrower b
        WHERE b.id = :borrowerId
          AND l.organization.id = :organizationId
        ORDER BY p.paidDate DESC
        """)
    List<Payment> findByBorrowerIdAndOrganizationId(
            @Param("borrowerId") Long borrowerId,
            @Param("organizationId") Long organizationId
    );


    /**
     * Only completed/paid payments for a borrower.
     */
    @Query("""
        SELECT p
        FROM Payment p
        JOIN p.loan l
        JOIN l.borrower b
        WHERE b.id = :borrowerId
          AND l.organization.id = :organizationId
          AND p.paid = true
        ORDER BY p.paidDate DESC
        """)
    List<Payment> findPaidPaymentsByBorrower(
            @Param("borrowerId") Long borrowerId,
            @Param("organizationId") Long organizationId
    );


    /**
     * Full borrower payment history.
     */
    @Query("""
        SELECT p
        FROM Payment p
        JOIN FETCH p.loan l
        JOIN FETCH l.borrower b
        WHERE b.id = :borrowerId
          AND l.organization.id = :organizationId
        ORDER BY p.paidDate DESC
        """)
    List<Payment> findBorrowerPaymentHistory(
            @Param("borrowerId") Long borrowerId,
            @Param("organizationId") Long organizationId
    );


    // ============================================================
    // BORROWER PAYMENT STATISTICS
    // ============================================================

    @Query("""
        SELECT COUNT(p)
        FROM Payment p
        JOIN p.loan l
        JOIN l.borrower b
        WHERE b.id = :borrowerId
          AND l.organization.id = :organizationId
        """)
    long countByBorrowerIdAndOrganizationId(
            @Param("borrowerId") Long borrowerId,
            @Param("organizationId") Long organizationId
    );


    @Query("""
        SELECT COUNT(p)
        FROM Payment p
        JOIN p.loan l
        JOIN l.borrower b
        WHERE b.id = :borrowerId
          AND l.organization.id = :organizationId
          AND p.paid = true
        """)
    long countPaidPaymentsByBorrower(
            @Param("borrowerId") Long borrowerId,
            @Param("organizationId") Long organizationId
    );


    @Query("""
        SELECT COUNT(p)
        FROM Payment p
        JOIN p.loan l
        JOIN l.borrower b
        WHERE b.id = :borrowerId
          AND l.organization.id = :organizationId
          AND p.isLate = true
        """)
    long countLatePaymentsByBorrower(
            @Param("borrowerId") Long borrowerId,
            @Param("organizationId") Long organizationId
    );


    @Query("""
        SELECT COUNT(p)
        FROM Payment p
        JOIN p.loan l
        JOIN l.borrower b
        WHERE b.id = :borrowerId
          AND l.organization.id = :organizationId
          AND p.paid = false
          AND p.dueDate < :today
        """)
    long countOverduePaymentsByBorrower(
            @Param("borrowerId") Long borrowerId,
            @Param("organizationId") Long organizationId,
            @Param("today") LocalDate today
    );


    // ============================================================
    // BORROWER PAYMENT TOTALS
    // ============================================================

    @Query("""
        SELECT COALESCE(SUM(p.amountPaid), 0.0)
        FROM Payment p
        JOIN p.loan l
        JOIN l.borrower b
        WHERE b.id = :borrowerId
          AND l.organization.id = :organizationId
          AND p.paid = true
        """)
    BigDecimal sumPaidByBorrower(
            @Param("borrowerId") Long borrowerId,
            @Param("organizationId") Long organizationId
    );


    @Query("""
        SELECT COALESCE(SUM(p.principalComponent), 0.0)
        FROM Payment p
        JOIN p.loan l
        JOIN l.borrower b
        WHERE b.id = :borrowerId
          AND l.organization.id = :organizationId
          AND p.paid = true
        """)
    BigDecimal sumPrincipalPaidByBorrower(
            @Param("borrowerId") Long borrowerId,
            @Param("organizationId") Long organizationId
    );


    @Query("""
        SELECT COALESCE(SUM(p.interestComponent), 0.0)
        FROM Payment p
        JOIN p.loan l
        JOIN l.borrower b
        WHERE b.id = :borrowerId
          AND l.organization.id = :organizationId
          AND p.paid = true
        """)
    BigDecimal sumInterestPaidByBorrower(
            @Param("borrowerId") Long borrowerId,
            @Param("organizationId") Long organizationId
    );


    @Query("""
        SELECT COALESCE(SUM(p.penalty), 0.0)
        FROM Payment p
        JOIN p.loan l
        JOIN l.borrower b
        WHERE b.id = :borrowerId
          AND l.organization.id = :organizationId
          AND p.paid = true
        """)
    BigDecimal sumPenaltyPaidByBorrower(
            @Param("borrowerId") Long borrowerId,
            @Param("organizationId") Long organizationId
    );


    // ============================================================
    // COLLECTIONS
    // ============================================================

    @Query("""
        SELECT COALESCE(SUM(p.amountPaid), 0.0)
        FROM Payment p
        WHERE p.organization = :org
          AND p.paid = true
          AND p.paidDate >= :from
        """)
    BigDecimal sumCollectedSince(
            @Param("org") Organization org,
            @Param("from") LocalDate from
    );


    // ============================================================
    // LATE PAYMENTS
    // ============================================================

    @Query("""
        SELECT COUNT(p)
        FROM Payment p
        WHERE p.organization = :org
          AND p.isLate = true
        """)
    Long countLatePayments(
            @Param("org") Organization org
    );


    // ============================================================
    // UNPAID PAYMENTS
    // ============================================================

    long countByOrganizationAndPaidFalse(
            Organization org
    );


    // ============================================================
    // RECENT PAYMENTS
    // ============================================================

    List<Payment> findTop10ByLoanIdOrderByPaidDateDesc(
            Long loanId
    );


    // ============================================================
    // REGULATORY REPORTING
    // ============================================================

    @Query("""
        SELECT p
        FROM Payment p
        JOIN p.loan l
        WHERE p.organization.id = :organizationId
          AND (:branchId IS NULL OR l.branch.id = :branchId)
          AND p.paid = true
          AND p.paidDate >= :from
          AND p.paidDate <= :to
        ORDER BY p.paidDate ASC
        """)
    List<Payment> findPaymentsDuringPeriod(
            @Param("organizationId") Long organizationId,
            @Param("branchId") Long branchId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );


    // ============================================================
    // TRANSACTION LOOKUP
    // ============================================================

    Optional<Payment> findByOrganization_IdAndTransactionId(
            Long id,
            String txnId
    );
}
