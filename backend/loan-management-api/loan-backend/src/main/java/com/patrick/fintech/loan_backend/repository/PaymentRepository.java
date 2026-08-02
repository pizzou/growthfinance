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

    Optional<Payment> findByOrganization_IdAndTransactionId(Long id, String txnId);
}