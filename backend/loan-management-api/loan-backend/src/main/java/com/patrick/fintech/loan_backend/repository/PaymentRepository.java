
package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // ============================================================
    // BASIC QUERIES
    // ============================================================

    List<Payment> findByLoanId(Long loanId);

    List<Payment> findByLoan_Organization_Id(Long orgId);

    List<Payment> findByPaidFalseAndDueDateBefore(LocalDate date);

    List<Payment> findByOrganization_IdAndPaidFalseAndDueDateBefore(
            Long orgId,
            LocalDate date
    );

    Optional<Payment> findByPaymentReference(String ref);


    // ============================================================
    // BORROWER PAYMENT HISTORY
    // ============================================================

    /**
     * All payments belonging to a borrower inside an organization.
     *
     * Payment -> Loan -> Borrower
     *
     * The organization condition is important for multi-tenancy.
     */
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
     * Only successful/recorded payments for a borrower.
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
     * Payment history for one borrower with the newest
     * payment first.
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

    /**
     * Number of payments made/recorded by borrower.
     */
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


    /**
     * Number of successful/paid payments.
     */
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


    /**
     * Number of late payments.
     */
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


    /**
     * Number of unpaid/overdue scheduled payments.
     */
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

    /**
     * Total amount paid by borrower.
     */
    @Query("""
        SELECT COALESCE(SUM(p.amountPaid), 0)
        FROM Payment p
        JOIN p.loan l
        JOIN l.borrower b
        WHERE b.id = :borrowerId
          AND l.organization.id = :organizationId
          AND p.paid = true
        """)
    Double sumPaidByBorrower(
            @Param("borrowerId") Long borrowerId,
            @Param("organizationId") Long organizationId
    );


    /**
     * Total principal paid by borrower.
     *
     * This assumes Payment has a principalPaid field.
     * If your Payment entity uses another name, change only
     * p.principalPaid to the actual field.
     */
    @Query("""
        SELECT COALESCE(SUM(p.principalPaid), 0)
        FROM Payment p
        JOIN p.loan l
        JOIN l.borrower b
        WHERE b.id = :borrowerId
          AND l.organization.id = :organizationId
          AND p.paid = true
        """)
    Double sumPrincipalPaidByBorrower(
            @Param("borrowerId") Long borrowerId,
            @Param("organizationId") Long organizationId
    );


    /**
     * Total interest paid by borrower.
     *
     * This assumes Payment has an interestPaid field.
     */
    @Query("""
        SELECT COALESCE(SUM(p.interestPaid), 0)
        FROM Payment p
        JOIN p.loan l
        JOIN l.borrower b
        WHERE b.id = :borrowerId
          AND l.organization.id = :organizationId
          AND p.paid = true
        """)
    Double sumInterestPaidByBorrower(
            @Param("borrowerId") Long borrowerId,
            @Param("organizationId") Long organizationId
    );


    /**
     * Total penalties paid by borrower.
     */
    @Query("""
        SELECT COALESCE(SUM(p.penalty), 0)
        FROM Payment p
        JOIN p.loan l
        JOIN l.borrower b
        WHERE b.id = :borrowerId
          AND l.organization.id = :organizationId
          AND p.paid = true
        """)
    Double sumPenaltyPaidByBorrower(
            @Param("borrowerId") Long borrowerId,
            @Param("organizationId") Long organizationId
    );


    // ============================================================
    // COLLECTIONS
    // ============================================================

    @Query("""
        SELECT COALESCE(SUM(p.amountPaid), 0)
        FROM Payment p
        WHERE p.organization = :org
          AND p.paid = true
          AND p.paidDate >= :from
        """)
    Double sumCollectedSince(
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
    // RECENT PAYMENTS FOR A LOAN
    // ============================================================

    List<Payment> findTop10ByLoanIdOrderByPaidDateDesc(
            Long loanId
    );


    // ============================================================
    // REGULATORY
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
