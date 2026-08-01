package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;


import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    List<JournalEntry> findByOrganization_IdOrderByEntryDateDesc(Long organizationId);

    List<JournalEntry> findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
            Long organizationId,
            LocalDate from,
            LocalDate to
    );

    Optional<JournalEntry> findByIdAndOrganization_Id(
            Long entryId,
            Long organizationId
    );

   
    Optional<JournalEntry> findByOrganization_IdAndSourceTypeAndSourceId(
            Long organizationId,
            String sourceType,
            String sourceId
    );

    List<JournalEntry> findByOrganization_IdAndSourceType(
            Long organizationId,
            String sourceType
    );

    List<JournalEntry> findByOrganization_IdAndSourceId(
            Long organizationId,
            String sourceId
    );

    boolean existsByOrganization_IdAndSourceTypeAndSourceId(
            Long organizationId,
            String sourceType,
            String sourceId
    );
}