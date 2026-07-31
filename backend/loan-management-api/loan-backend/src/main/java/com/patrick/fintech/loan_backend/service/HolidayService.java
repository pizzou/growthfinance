package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Holiday;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class HolidayService {

    private final HolidayRepository holidayRepo;

    public Holiday create(Organization org, LocalDate date, String name, boolean recurring) {
        return holidayRepo.save(Holiday.builder()
            .organization(org).holidayDate(date).name(name).recurringAnnually(recurring).build());
    }

    public java.util.List<Holiday> list(Long orgId) {
        return holidayRepo.findByOrganization_IdOrderByHolidayDateAsc(orgId);
    }

    public void delete(Long orgId, Long id) {
        Holiday h = holidayRepo.findById(id)
            .filter(x -> x.getOrganization().getId().equals(orgId))
            .orElseThrow(() -> new RuntimeException("Holiday not found"));
        holidayRepo.delete(h);
    }

    /** True if this date is a weekend, an exact-date holiday, or a recurring (annual) holiday. */
    public boolean isNonBusinessDay(Long orgId, LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) return true;
        if (holidayRepo.findByOrganization_IdAndHolidayDate(orgId, date).isPresent()) return true;
        return !holidayRepo.findRecurringForMonthDay(orgId, date.getMonthValue(), date.getDayOfMonth()).isEmpty();
    }

    /**
     * Rolls a due date forward to the next real business day if it lands on a
     * weekend or configured holiday. Standard lending-schedule practice: never
     * push a payment due date backward (that would shorten the borrower's grace
     * period), only forward.
     */
    public LocalDate adjustToBusinessDay(Long orgId, LocalDate date) {
        LocalDate adjusted = date;
        // Cap the loop -- a org with an absurd number of consecutive configured
        // holidays shouldn't be able to spin this forever; 30 days is generous.
        int guard = 0;
        while (isNonBusinessDay(orgId, adjusted) && guard < 30) {
            adjusted = adjusted.plusDays(1);
            guard++;
        }
        return adjusted;
    }
}