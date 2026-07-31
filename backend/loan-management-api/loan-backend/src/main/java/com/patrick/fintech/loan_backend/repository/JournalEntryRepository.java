package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    List<JournalEntry> findByOrganization_IdOrderByEntryDateDesc(Long orgId);
    Optional<JournalEntry> findByIdAndOrganization_Id(Long id, Long orgId);
    List<JournalEntry> findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(Long orgId, LocalDate from, LocalDate to);
}
