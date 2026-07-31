package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByLoanId(Long loanId);
    List<Payment> findByLoan_Organization_Id(Long orgId);
    List<Payment> findByPaidFalseAndDueDateBefore(LocalDate date);
    List<Payment> findByOrganization_IdAndPaidFalseAndDueDateBefore(Long orgId, LocalDate date);
    Optional<Payment> findByPaymentReference(String ref);

    @Query("SELECT COALESCE(SUM(p.amountPaid),0) FROM Payment p WHERE p.organization=:org AND p.paid=true AND p.paidDate>=:from")
    Double sumCollectedSince(@Param("org") Organization org, @Param("from") LocalDate from);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.organization=:org AND p.isLate=true")
    Long countLatePayments(@Param("org") Organization org);

    long countByOrganizationAndPaidFalse(Organization org);

    List<Payment> findTop10ByLoanIdOrderByPaidDateDesc(Long loanId);
}