
package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;


import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


public interface JournalEntryRepository
        extends JpaRepository<JournalEntry, Long> {

    /*
     * ============================================================
     * ORGANIZATION ENTRIES
     * ============================================================
     */

    List<JournalEntry> findByOrganization_IdOrderByEntryDateDesc(
            Long organizationId
    );


    /*
     * ============================================================
     * ENTRIES BETWEEN DATES
     * ============================================================
     */

    List<JournalEntry>
    findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
            Long organizationId,
            LocalDate from,
            LocalDate to
    );


    /*
     * ============================================================
     * ENTRIES BETWEEN DATES - DETERMINISTIC ORDER
     * ============================================================
     *
     * Entry date alone is not sufficient when several journal
     * entries are posted on the same day.
     *
     * ID provides deterministic ordering.
     */

    List<JournalEntry>
    findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAscIdAsc(
            Long organizationId,
            LocalDate from,
            LocalDate to
    );


    /*
     * ============================================================
     * FIND ENTRY BY ID + ORGANIZATION
     * ============================================================
     */

    Optional<JournalEntry> findByIdAndOrganization_Id(
            Long id,
            Long organizationId
    );


    /*
     * ============================================================
     * ACTIVE / NON-REVERSED ENTRIES
     * ============================================================
     */

    List<JournalEntry>
    findByOrganization_IdAndReversedFalseOrderByEntryDateAsc(
            Long organizationId
    );


    /*
     * ============================================================
     * ACTIVE ENTRIES BETWEEN DATES
     * ============================================================
     */

    List<JournalEntry>
    findByOrganization_IdAndEntryDateBetweenAndReversedFalseOrderByEntryDateAsc(
            Long organizationId,
            LocalDate from,
            LocalDate to
    );


    /*
     * ============================================================
     * SOURCE EVENT LOOKUP
     * ============================================================
     *
     * This is important for production accounting.
     *
     * Examples:
     *
     * LOAN_DISBURSEMENT + loan ID
     * PAYMENT_RECEIVED  + payment ID
     * INTEREST_ACCRUAL  + loan ID
     * EXPENSE           + expense ID
     *
     * The organization is included in the lookup.
     */

    Optional<JournalEntry>
    findFirstByOrganization_IdAndSourceTypeAndSourceId(
            Long organizationId,
            String sourceType,
            String sourceId
    );


    /*
     * ============================================================
     * ACTIVE SOURCE EVENT LOOKUP
     * ============================================================
     *
     * Useful when checking whether a business event already has
     * an active journal entry.
     */

    Optional<JournalEntry>
    findFirstByOrganization_IdAndSourceTypeAndSourceIdAndReversedFalse(
            Long organizationId,
            String sourceType,
            String sourceId
    );


    /*
     * ============================================================
     * ALL ENTRIES FOR SOURCE EVENT
     * ============================================================
     *
     * This can be useful for auditing because a source event may
     * have an original entry and a reversal entry.
     */

    List<JournalEntry>
    findByOrganization_IdAndSourceTypeAndSourceId(
            Long organizationId,
            String sourceType,
            String sourceId
    );


    /*
     * ============================================================
     * SOURCE TYPE
     * ============================================================
     */

    List<JournalEntry>
    findByOrganization_IdAndSourceTypeOrderByEntryDateAscIdAsc(
            Long organizationId,
            String sourceType
    );


    /*
     * ============================================================
     * SOURCE TYPE + DATE RANGE
     * ============================================================
     */

    List<JournalEntry>
    findByOrganization_IdAndSourceTypeAndEntryDateBetweenOrderByEntryDateAscIdAsc(
            Long organizationId,
            String sourceType,
            LocalDate from,
            LocalDate to
    );


    /*
     * ============================================================
     * REVERSED ENTRIES
     * ============================================================
     */

    List<JournalEntry>
    findByOrganization_IdAndReversedTrueOrderByEntryDateDesc(
            Long organizationId
    );
}
