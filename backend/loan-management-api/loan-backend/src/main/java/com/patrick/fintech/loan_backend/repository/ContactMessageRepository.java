package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {
    List<ContactMessage> findByOrganization_IdOrderByCreatedAtDesc(Long orgId);
    Optional<ContactMessage> findByIdAndOrganization_Id(Long id, Long orgId);
    long countByOrganization_IdAndReadFalse(Long orgId);
}
