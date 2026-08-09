package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    List<Holiday> findByOrganization_IdOrderByHolidayDateAsc(Long orgId);
    Optional<Holiday> findByOrganization_IdAndHolidayDate(Long orgId, LocalDate date);

    // Recurring holidays repeat every year on the same month/day (e.g. national
    // independence day) -- checked separately since the stored date's *year* won't match.
    @org.springframework.data.jpa.repository.Query(
        "SELECT h FROM Holiday h WHERE h.organization.id = :orgId AND h.recurringAnnually = true " +
        "AND EXTRACT(MONTH FROM h.holidayDate) = :month AND EXTRACT(DAY FROM h.holidayDate) = :day")
    List<Holiday> findRecurringForMonthDay(Long orgId, int month, int day);
}
