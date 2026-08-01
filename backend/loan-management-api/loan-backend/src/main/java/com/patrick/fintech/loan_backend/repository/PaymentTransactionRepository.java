package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByOrganization_IdAndTransactionReference(Long organizationId, String reference);
    Optional<PaymentTransaction> findByOrganization_IdAndId(Long organizationId, Long id);
    List<PaymentTransaction> findByLoanIdOrderByCreatedAtDesc(Long loanId);
    List<PaymentTransaction> findByInstallmentIdOrderByCreatedAtAsc(Long installmentId);
}