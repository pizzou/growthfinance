package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.ImportRowResult;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.security.HmacIndexer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Imports exactly one row of a client's legacy (Excel/CSV) ledger as a loan + its borrower
 * (matched by National ID if one already exists in this org, created otherwise).
 *
 * Each call is its OWN transaction (REQUIRES_NEW) — called from LegacyLoanImportService in a
 * loop, one row at a time — so a bad row (bad date format, missing field, whatever) fails and
 * is reported back without rolling back every row already committed before it. A bulk import
 * of 500 rows where row 217 is malformed should still leave the other 499 in place.
 *
 * Imported loans skip this platform's normal origination path entirely (LoanService.createLoan
 * / the maker-checker approval chain / automatic credit-bureau "loan approved" reporting) —
 * they're historical fact, not a new application moving through underwriting. Loan.imported
 * and Borrower.imported are set so reporting/audit can always tell the two apart.
 */
@Service
@RequiredArgsConstructor
public class LegacyLoanImportRowService {

    private static final List<String> ALLOWED_IMPORT_STATUSES = List.of(
        "ACTIVE", "OVERDUE", "PAID", "CLOSED", "DEFAULTED", "WRITTEN_OFF", "RESTRUCTURED");

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE,                 // 2024-03-15
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"));

    private final BorrowerRepository borrowerRepo;
    private final LoanRepository loanRepo;
    private final LoanService loanService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportRowResult importRow(Map<String, String> row, int rowNumber, Organization org,
                                      Long importBatchId, boolean commit,
                                      Map<String, Borrower> sessionBorrowers) {
        try {
            String nationalId = req(row, "national_id");
            if (!nationalId.matches("\\d{16}")) {
                return fail(rowNumber, "national_id must be exactly 16 digits (got \"" + nationalId + "\") — this is the field used to match/create the borrower.");
            }
            String firstName = req(row, "first_name");
            String lastName  = req(row, "last_name");
            String phone     = req(row, "phone");
            String gender    = normalizeGender(req(row, "gender"));
            double amount          = reqDouble(row, "amount");
            double interestRate    = reqDouble(row, "interest_rate");
            int    durationMonths  = (int) reqDouble(row, "duration_months");
            LocalDate startDate    = reqDate(row, "start_date");

            String statusRaw = req(row, "status").toUpperCase(Locale.ROOT);
            if (!ALLOWED_IMPORT_STATUSES.contains(statusRaw)) {
                return fail(rowNumber, "status must be one of " + ALLOWED_IMPORT_STATUSES +
                    " for imported loans (got \"" + statusRaw + "\") — in-flight workflow statuses like " +
                    "PENDING/APPROVED don't apply to historical records with no approval trail.");
            }
            LoanStatus status = LoanStatus.valueOf(statusRaw);

            String rateTypeRaw = opt(row, "interest_rate_type", "ANNUAL").toUpperCase(Locale.ROOT);
            String rateType = "MONTHLY".equals(rateTypeRaw) ? "MONTHLY" : "ANNUAL";

            String loanTypeRaw = opt(row, "loan_type", "PERSONAL").toUpperCase(Locale.ROOT).replace(' ', '_');
            Loan.LoanType loanType;
            try { loanType = Loan.LoanType.valueOf(loanTypeRaw); }
            catch (Exception e) {
                return fail(rowNumber, "loan_type \"" + loanTypeRaw + "\" isn't recognized. Valid values: " +
                    Arrays.toString(Loan.LoanType.values()));
            }

            // ---- borrower: match by National ID within this org, or create ----
            // Checked against an in-run cache first, not just the DB — a preview hasn't
            // committed anything yet, so two rows for the same person in one file would
            // otherwise both look like "new borrower" even though only one should be created.
            String nationalIdHash = HmacIndexer.index(nationalId);
            Borrower borrower = sessionBorrowers.get(nationalIdHash);
            if (borrower == null) {
                borrower = borrowerRepo.findByNationalIdHashAndOrganization_Id(nationalIdHash, org.getId())
                    .orElse(null);
            }
            String borrowerAction;
            if (borrower == null) {
                borrower = Borrower.builder()
                    .organization(org)
                    .firstName(firstName)
                    .lastName(lastName)
                    .nationalId(nationalId)
                    .email(optOrGenerated(row, "email", nationalId, org.getId()))
                    .phone(phone)
                    .gender(gender)
                    .maritalStatus(opt(row, "marital_status", "UNKNOWN"))
                    .address(opt(row, "address", null))
                    .monthlyIncome(optDouble(row, "monthly_income"))
                    .kycStatus("PENDING")
                    .status(Borrower.BorrowerStatus.ACTIVE)
                    .imported(true)
                    .build();
                if (commit) borrower = borrowerRepo.save(borrower);
                borrowerAction = "CREATED_NEW_BORROWER";
            } else {
                borrowerAction = "MATCHED_EXISTING_BORROWER";
            }
            sessionBorrowers.put(nationalIdHash, borrower);

            // ---- loan ----
            Double totalPaid          = optDouble(row, "total_paid");
            Double outstandingGiven   = optDouble(row, "outstanding_balance");
            double totalRepayable;
            double outstandingBalance;
            if (outstandingGiven != null) {
                // Real-world ledgers reflect adjustments/partial payments a formula can't
                // reconstruct — trust the client's own figure over recomputing from scratch.
                outstandingBalance = outstandingGiven;
                totalRepayable = (totalPaid != null ? totalPaid : 0) + outstandingBalance;
            } else {
                double[] calc = loanService.amortize(amount, interestRate, durationMonths, rateType);
                totalRepayable = round2(calc[1]);
                outstandingBalance = totalPaid != null ? round2(Math.max(0, totalRepayable - totalPaid)) : totalRepayable;
            }

            String refFromFile = opt(row, "loan_reference", null);
            String referenceNumber = (refFromFile != null && !refFromFile.isBlank())
                ? refFromFile : loanService.newReferenceNumber(org);

            boolean pastApproval = List.of("ACTIVE","OVERDUE","PAID","CLOSED","DEFAULTED","WRITTEN_OFF","RESTRUCTURED").contains(statusRaw);

            Loan loan = Loan.builder()
                .referenceNumber(referenceNumber)
                .organization(org)
                .borrower(borrower)
                .loanType(loanType)
                .status(status)
                .amount(amount)
                .interestRate(interestRate)
                .interestRateType(rateType)
                .durationMonths(durationMonths)
                .currency(opt(row, "currency", org.getDefaultCurrency()))
                .totalRepayable(totalRepayable)
                .totalPaid(totalPaid != null ? totalPaid : 0.0)
                .outstandingBalance(outstandingBalance)
                .startDate(startDate)
                .approvedAt(pastApproval ? startDate : null)
                .disbursedAt(pastApproval ? startDate : null)
                .notes(opt(row, "notes", null))
                .internalNotes("Imported from legacy ledger (batch #" + importBatchId + ")")
                .imported(true)
                .importBatchId(importBatchId)
                .build();

            if (commit) {
                loan = loanRepo.save(loan);
            }

            return ImportRowResult.builder()
                .rowNumber(rowNumber).success(true)
                .borrowerAction(borrowerAction)
                .borrowerName(firstName + " " + lastName)
                .loanReferenceNumber(referenceNumber)
                .build();

        } catch (IllegalArgumentException e) {
            return fail(rowNumber, e.getMessage());
        } catch (Exception e) {
            return fail(rowNumber, "Unexpected error: " + e.getMessage());
        }
    }

    // ---------- helpers ----------

    private String normalizeGender(String v) {
        String g = v.trim().toUpperCase(Locale.ROOT);
        if (g.equals("M") || g.equals("MALE")) return "Male";
        if (g.equals("F") || g.equals("FEMALE")) return "Female";
        return v.trim(); // pass through anything else as given rather than reject it outright
    }

    private String req(Map<String, String> row, String key) {
        String v = row.get(key);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("\"" + key + "\" is required but was blank.");
        return v.trim();
    }

    private double reqDouble(Map<String, String> row, String key) {
        String v = req(row, key);
        try { return Double.parseDouble(v.replace(",", "")); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("\"" + key + "\" must be a number (got \"" + v + "\")."); }
    }

    private Double optDouble(Map<String, String> row, String key) {
        String v = row.get(key);
        if (v == null || v.isBlank()) return null;
        try { return Double.parseDouble(v.replace(",", "")); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("\"" + key + "\" must be a number if provided (got \"" + v + "\")."); }
    }

    private String opt(Map<String, String> row, String key, String fallback) {
        String v = row.get(key);
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    private String optOrGenerated(Map<String, String> row, String key, String nationalId, Long orgId) {
        String v = row.get(key);
        if (v != null && !v.isBlank()) return v.trim();
        // No email on file — extremely common for a manual paper/Excel ledger. Email is
        // required + unique in the schema, so generate a stable, org-scoped placeholder
        // rather than blocking the import on a field most legacy records won't have.
        return "member." + nationalId + ".org" + orgId + "@imported.local";
    }

    private LocalDate reqDate(Map<String, String> row, String key) {
        String v = req(row, key);
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(v, fmt); } catch (DateTimeParseException ignored) {}
        }
        throw new IllegalArgumentException("\"" + key + "\" isn't a recognized date (got \"" + v +
            "\") — use YYYY-MM-DD or DD/MM/YYYY.");
    }

    private double round2(double d) { return Math.round(d * 100.0) / 100.0; }

    private ImportRowResult fail(int rowNumber, String error) {
        return ImportRowResult.builder().rowNumber(rowNumber).success(false).error(error).build();
    }
}