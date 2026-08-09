
package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    List<JournalEntry> findByOrganization_IdOrderByEntryDateDesc(Long organizationId);

    List<JournalEntry> findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
            Long organizationId,
            LocalDate from,
            LocalDate to
    );

    Optional<JournalEntry> findByIdAndOrganization_Id(
            Long id,
            Long organizationId
    );

    List<JournalEntry> findByOrganization_IdAndReversedFalseOrderByEntryDateAsc(
            Long organizationId
    );

    List<JournalEntry> findByOrganization_IdAndEntryDateBetweenAndReversedFalseOrderByEntryDateAsc(
            Long organizationId,
            LocalDate from,
            LocalDate to
    );
}
