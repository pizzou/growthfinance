
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LegacyLoanImportRowService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private static final BigDecimal ZERO =
        BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);

    private static final List<String> ALLOWED_IMPORT_STATUSES = List.of(
        "ACTIVE",
        "OVERDUE",
        "PAID",
        "CLOSED",
        "DEFAULTED",
        "WRITTEN_OFF",
        "RESTRUCTURED"
    );

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    private final BorrowerRepository borrowerRepo;
    private final LoanRepository loanRepo;
    private final LoanService loanService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportRowResult importRow(
            Map<String, String> row,
            int rowNumber,
            Organization org,
            Long importBatchId,
            boolean commit,
            Map<String, Borrower> sessionBorrowers) {

        try {

            // ============================================================
            // 1. BORROWER DATA
            // ============================================================

            String nationalId = req(row, "national_id");

            if (!nationalId.matches("\\d{16}")) {
                return fail(
                    rowNumber,
                    "national_id must be exactly 16 digits (got \"" +
                    nationalId +
                    "\") — this is the field used to match/create the borrower."
                );
            }

            String firstName = req(row, "first_name");
            String lastName  = req(row, "last_name");
            String phone     = req(row, "phone");
            String gender    = normalizeGender(req(row, "gender"));

            // ============================================================
            // 2. LOAN INPUT
            // ============================================================

            BigDecimal amount = reqMoney(row, "amount");

            BigDecimal interestRate = reqMoney(row, "interest_rate");

            int durationMonths = reqInt(row, "duration_months");

            if (durationMonths <= 0) {
                return fail(
                    rowNumber,
                    "duration_months must be greater than zero."
                );
            }

            LocalDate startDate = reqDate(row, "start_date");

            // ============================================================
            // 3. STATUS
            // ============================================================

            String statusRaw = req(row, "status").toUpperCase(Locale.ROOT);

            if (!ALLOWED_IMPORT_STATUSES.contains(statusRaw)) {
                return fail(
                    rowNumber,
                    "status must be one of " +
                    ALLOWED_IMPORT_STATUSES +
                    " for imported loans (got \"" +
                    statusRaw +
                    "\") — in-flight workflow statuses like " +
                    "PENDING/APPROVED don't apply to historical records."
                );
            }

            LoanStatus status = LoanStatus.valueOf(statusRaw);

            // ============================================================
            // 4. INTEREST RATE TYPE
            // ============================================================

            String rateTypeRaw =
                opt(row, "interest_rate_type", "ANNUAL")
                    .toUpperCase(Locale.ROOT);

            String rateType =
                "MONTHLY".equals(rateTypeRaw)
                    ? "MONTHLY"
                    : "ANNUAL";

            // ============================================================
            // 5. LOAN TYPE
            // ============================================================

            String loanTypeRaw =
                opt(row, "loan_type", "PERSONAL")
                    .toUpperCase(Locale.ROOT)
                    .replace(' ', '_');

            Loan.LoanType loanType;

            try {
                loanType = Loan.LoanType.valueOf(loanTypeRaw);
            } catch (Exception e) {
                return fail(
                    rowNumber,
                    "loan_type \"" +
                    loanTypeRaw +
                    "\" isn't recognized. Valid values: " +
                    Arrays.toString(Loan.LoanType.values())
                );
            }

            // ============================================================
            // 6. FIND OR CREATE BORROWER
            // ============================================================

            String nationalIdHash = HmacIndexer.index(nationalId);

            Borrower borrower = sessionBorrowers.get(nationalIdHash);

            if (borrower == null) {
                borrower =
                    borrowerRepo
                        .findByNationalIdHashAndOrganization_Id(
                            nationalIdHash,
                            org.getId()
                        )
                        .orElse(null);
            }

            String borrowerAction;

            if (borrower == null) {

                borrower = Borrower.builder()
                    .organization(org)
                    .firstName(firstName)
                    .lastName(lastName)
                    .nationalId(nationalId)
                    .email(
                        optOrGenerated(
                            row,
                            "email",
                            nationalId,
                            org.getId()
                        )
                    )
                    .phone(phone)
                    .gender(gender)
                    .maritalStatus(
                        opt(row, "marital_status", "UNKNOWN")
                    )
                    .address(
                        opt(row, "address", null)
                    )
                    .monthlyIncome(
                        optMoney(row, "monthly_income")
                    )
                    .kycStatus("PENDING")
                    .status(Borrower.BorrowerStatus.ACTIVE)
                    .imported(true)
                    .build();

                if (commit) {
                    borrower = borrowerRepo.save(borrower);
                }

                borrowerAction = "CREATED_NEW_BORROWER";

            } else {

                borrowerAction = "MATCHED_EXISTING_BORROWER";
            }

            sessionBorrowers.put(nationalIdHash, borrower);

            // ============================================================
            // 7. EXISTING PAYMENT / OUTSTANDING BALANCE
            // ============================================================

            BigDecimal totalPaid =
                optMoney(row, "total_paid");

            BigDecimal outstandingGiven =
                optMoney(row, "outstanding_balance");

            BigDecimal totalRepayable;
            BigDecimal outstandingBalance;

            if (outstandingGiven != null) {

                /*
                 * If the legacy ledger already provides an outstanding
                 * balance, trust it instead of trying to reconstruct
                 * historical adjustments.
                 */

                outstandingBalance = money(outstandingGiven);

                BigDecimal paid =
                    totalPaid != null
                        ? money(totalPaid)
                        : ZERO;

                totalRepayable =
                    money(paid.add(outstandingBalance));

            } else {

                /*
                 * LoanService.amortize() currently works with doubles.
                 * Convert only at the service boundary and immediately
                 * convert the result back to BigDecimal.
                 */

                double[] calc =
                    loanService.amortize(
                        amount.doubleValue(),
                        interestRate.doubleValue(),
                        durationMonths,
                        rateType
                    );

                if (calc == null || calc.length < 2) {
                    throw new IllegalStateException(
                        "Loan amortization returned invalid results."
                    );
                }

                totalRepayable =
                    money(BigDecimal.valueOf(calc[1]));

                if (totalPaid != null) {

                    BigDecimal paid =
                        money(totalPaid);

                    outstandingBalance =
                        totalRepayable
                            .subtract(paid)
                            .max(ZERO)
                            .setScale(
                                MONEY_SCALE,
                                MONEY_ROUNDING
                            );

                } else {

                    outstandingBalance =
                        totalRepayable;
                }
            }

            // ============================================================
            // 8. LOAN REFERENCE
            // ============================================================

            String refFromFile =
                opt(row, "loan_reference", null);

            String referenceNumber =
                (refFromFile != null && !refFromFile.isBlank())
                    ? refFromFile
                    : loanService.newReferenceNumber(org);

            // ============================================================
            // 9. HISTORICAL LOAN FLAGS
            // ============================================================

            boolean pastApproval =
                ALLOWED_IMPORT_STATUSES.contains(statusRaw);

            // ============================================================
            // 10. BUILD LOAN
            // ============================================================

            Loan loan = Loan.builder()
                .referenceNumber(referenceNumber)
                .organization(org)
                .borrower(borrower)
                .loanType(loanType)
                .status(status)

                // BigDecimal monetary fields
                .amount(amount)
                .interestRate(interestRate)
                .interestRateType(rateType)
                .durationMonths(durationMonths)
                .currency(
                    opt(
                        row,
                        "currency",
                        org.getDefaultCurrency()
                    )
                )
                .totalRepayable(totalRepayable)
                .totalPaid(
                    totalPaid != null
                        ? money(totalPaid)
                        : ZERO
                )
                .outstandingBalance(outstandingBalance)

                .startDate(startDate)
                .approvedAt(
                    pastApproval
                        ? startDate
                        : null
                )
                .disbursedAt(
                    pastApproval
                        ? startDate
                        : null
                )
                .notes(
                    opt(row, "notes", null)
                )
                .internalNotes(
                    "Imported from legacy ledger (batch #" +
                    importBatchId +
                    ")"
                )
                .imported(true)
                .importBatchId(importBatchId)
                .build();

            // ============================================================
            // 11. SAVE
            // ============================================================

            if (commit) {
                loan = loanRepo.save(loan);
            }

            // ============================================================
            // 12. RESULT
            // ============================================================

            return ImportRowResult.builder()
                .rowNumber(rowNumber)
                .success(true)
                .borrowerAction(borrowerAction)
                .borrowerName(
                    firstName + " " + lastName
                )
                .loanReferenceNumber(referenceNumber)
                .build();

        } catch (IllegalArgumentException e) {

            return fail(
                rowNumber,
                e.getMessage()
            );

        } catch (Exception e) {

            return fail(
                rowNumber,
                "Unexpected error: " +
                (e.getMessage() != null
                    ? e.getMessage()
                    : e.getClass().getSimpleName())
            );
        }
    }

    // ================================================================
    // MONEY HELPERS
    // ================================================================

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }

        return value.setScale(
            MONEY_SCALE,
            MONEY_ROUNDING
        );
    }

    private BigDecimal reqMoney(
            Map<String, String> row,
            String key) {

        String value = req(row, key);

        try {

            return money(
                new BigDecimal(
                    value.replace(",", "").trim()
                )
            );

        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                "\"" + key +
                "\" must be a valid monetary amount " +
                "(got \"" + value + "\")."
            );
        }
    }

    private BigDecimal optMoney(
            Map<String, String> row,
            String key) {

        String value = row.get(key);

        if (value == null || value.isBlank()) {
            return null;
        }

        try {

            return money(
                new BigDecimal(
                    value.replace(",", "").trim()
                )
            );

        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                "\"" + key +
                "\" must be a valid monetary amount if provided " +
                "(got \"" + value + "\")."
            );
        }
    }

    // ================================================================
    // INTEGER HELPERS
    // ================================================================

    private int reqInt(
            Map<String, String> row,
            String key) {

        String value = req(row, key);

        try {

            return Integer.parseInt(
                value.replace(",", "").trim()
            );

        } catch (NumberFormatException e) {

            /*
             * Also accept values such as "12.0" from Excel.
             */

            try {

                double d =
                    Double.parseDouble(
                        value.replace(",", "").trim()
                    );

                if (d != Math.floor(d)) {
                    throw new NumberFormatException();
                }

                return (int) d;

            } catch (NumberFormatException ignored) {

                throw new IllegalArgumentException(
                    "\"" + key +
                    "\" must be a whole number " +
                    "(got \"" + value + "\")."
                );
            }
        }
    }

    // ================================================================
    // STRING HELPERS
    // ================================================================

    private String normalizeGender(String value) {

        String gender =
            value.trim().toUpperCase(Locale.ROOT);

        if (gender.equals("M") ||
            gender.equals("MALE")) {

            return "Male";
        }

        if (gender.equals("F") ||
            gender.equals("FEMALE")) {

            return "Female";
        }

        return value.trim();
    }

    private String req(
            Map<String, String> row,
            String key) {

        String value = row.get(key);

        if (value == null || value.isBlank()) {

            throw new IllegalArgumentException(
                "\"" + key +
                "\" is required but was blank."
            );
        }

        return value.trim();
    }

    private String opt(
            Map<String, String> row,
            String key,
            String fallback) {

        String value = row.get(key);

        return (value == null || value.isBlank())
            ? fallback
            : value.trim();
    }

    private String optOrGenerated(
            Map<String, String> row,
            String key,
            String nationalId,
            Long orgId) {

        String value = row.get(key);

        if (value != null && !value.isBlank()) {
            return value.trim();
        }

        /*
         * Legacy records frequently don't have email addresses.
         * Generate a stable organization-scoped placeholder.
         */

        return "member." +
            nationalId +
            ".org" +
            orgId +
            "@imported.local";
    }

    // ================================================================
    // DATE
    // ================================================================

    private LocalDate reqDate(
            Map<String, String> row,
            String key) {

        String value = req(row, key);

        for (DateTimeFormatter formatter : DATE_FORMATS) {

            try {

                return LocalDate.parse(
                    value,
                    formatter
                );

            } catch (DateTimeParseException ignored) {
                // Try next format.
            }
        }

        throw new IllegalArgumentException(
            "\"" + key +
            "\" isn't a recognized date " +
            "(got \"" + value +
            "\") — use YYYY-MM-DD or DD/MM/YYYY."
        );
    }

    // ================================================================
    // RESULT
    // ================================================================

    private ImportRowResult fail(
            int rowNumber,
            String error) {

        return ImportRowResult.builder()
            .rowNumber(rowNumber)
            .success(false)
            .error(
                error != null
                    ? error
                    : "Unknown import error"
            )
            .build();
    }
}
