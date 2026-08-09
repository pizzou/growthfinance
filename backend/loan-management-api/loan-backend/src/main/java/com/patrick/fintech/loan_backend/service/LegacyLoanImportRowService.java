package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.ImportRowResult;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.security.HmacIndexer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class LegacyLoanImportRowService {

    // ================================================================
    // CONFIGURATION
    // ================================================================

    private static final int MONEY_SCALE = 2;

    private static final int RATE_SCALE = 6;

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    RoundingMode.HALF_UP
            );

    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100");

    private static final BigDecimal MAX_INTEREST_RATE =
            new BigDecimal("1000");

    private static final int MIN_DURATION_MONTHS = 1;

    private static final int MAX_DURATION_MONTHS = 600;

    private static final int MAX_TEXT_LENGTH = 5000;

    private static final int MAX_NAME_LENGTH = 150;

    private static final int MAX_PHONE_LENGTH = 50;

    private static final int MAX_NATIONAL_ID_LENGTH = 100;

    private static final Set<String> ALLOWED_IMPORT_STATUSES =
            Set.of(
                    "ACTIVE",
                    "OVERDUE",
                    "PAID",
                    "CLOSED",
                    "DEFAULTED",
                    "WRITTEN_OFF",
                    "RESTRUCTURED"
            );

    private static final List<DateTimeFormatter> DATE_FORMATS =
            List.of(
                    DateTimeFormatter.ISO_LOCAL_DATE
                            .withResolverStyle(
                                    ResolverStyle.STRICT
                            ),

                    DateTimeFormatter.ofPattern(
                                    "dd/MM/uuuu"
                            )
                            .withResolverStyle(
                                    ResolverStyle.STRICT
                            ),

                    DateTimeFormatter.ofPattern(
                                    "d/M/uuuu"
                            )
                            .withResolverStyle(
                                    ResolverStyle.STRICT
                            ),

                    DateTimeFormatter.ofPattern(
                                    "dd-MM-uuuu"
                            )
                            .withResolverStyle(
                                    ResolverStyle.STRICT
                            )
            );

    private final BorrowerRepository borrowerRepo;

    private final LoanRepository loanRepo;

    private final LoanService loanService;


    // ================================================================
    // IMPORT ONE ROW
    // ================================================================

    /**
     * Imports exactly one historical loan row.
     *
     * Each row executes inside its own REQUIRES_NEW transaction.
     *
     * This means a failure on row 217 does not roll back rows
     * that were already successfully committed.
     *
     * Historical loans intentionally bypass the normal origination,
     * approval and underwriting workflow.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportRowResult importRow(
            Map<String, String> row,
            int rowNumber,
            Organization org,
            Long importBatchId,
            boolean commit,
            Map<String, Borrower> sessionBorrowers
    ) {

        String borrowerAction = null;

        try {

            // ========================================================
            // IMPORT CONTEXT
            // ========================================================

            validateImportContext(
                    row,
                    rowNumber,
                    org,
                    importBatchId,
                    sessionBorrowers
            );


            // ========================================================
            // BORROWER INFORMATION
            // ========================================================

            String nationalId =
                    normalizeNationalId(
                            req(
                                    row,
                                    "national_id"
                            )
                    );

            validateNationalId(
                    nationalId
            );


            String firstName =
                    normalizeRequiredText(
                            req(
                                    row,
                                    "first_name"
                            ),
                            "first_name",
                            MAX_NAME_LENGTH
                    );


            String lastName =
                    normalizeRequiredText(
                            req(
                                    row,
                                    "last_name"
                            ),
                            "last_name",
                            MAX_NAME_LENGTH
                    );


            String phone =
                    normalizeRequiredText(
                            req(
                                    row,
                                    "phone"
                            ),
                            "phone",
                            MAX_PHONE_LENGTH
                    );


            String gender =
                    normalizeGender(
                            req(
                                    row,
                                    "gender"
                            )
                    );


            // ========================================================
            // LOAN INFORMATION
            // ========================================================

            BigDecimal amount =
                    reqMoney(
                            row,
                            "amount"
                    );

            validatePositiveMoney(
                    amount,
                    "amount"
            );


            BigDecimal interestRate =
                    reqRate(
                            row,
                            "interest_rate"
                    );

            validateInterestRate(
                    interestRate
            );


            int durationMonths =
                    reqInteger(
                            row,
                            "duration_months"
                    );

            validateDuration(
                    durationMonths
            );


            LocalDate startDate =
                    reqDate(
                            row,
                            "start_date"
                    );


            String statusRaw =
                    req(
                            row,
                            "status"
                    )
                            .toUpperCase(
                                    Locale.ROOT
                            )
                            .trim();


            validateStatus(
                    statusRaw
            );


            LoanStatus status =
                    LoanStatus.valueOf(
                            statusRaw
                    );


            // ========================================================
            // INTEREST RATE TYPE
            // ========================================================

            String rateTypeRaw =
                    opt(
                            row,
                            "interest_rate_type",
                            "ANNUAL"
                    )
                            .toUpperCase(
                                    Locale.ROOT
                            )
                            .trim();


            String rateType;

            if ("MONTHLY".equals(rateTypeRaw)) {

                rateType = "MONTHLY";

            } else if ("ANNUAL".equals(rateTypeRaw)) {

                rateType = "ANNUAL";

            } else {

                return fail(
                        rowNumber,
                        "interest_rate_type must be MONTHLY or ANNUAL. " +
                                "Got \"" +
                                rateTypeRaw +
                                "\"."
                );
            }


            // ========================================================
            // LOAN TYPE
            // ========================================================

            String loanTypeRaw =
                    opt(
                            row,
                            "loan_type",
                            "PERSONAL"
                    )
                            .toUpperCase(
                                    Locale.ROOT
                            )
                            .trim()
                            .replace(
                                    ' ',
                                    '_'
                            );


            Loan.LoanType loanType;

            try {

                loanType =
                        Loan.LoanType.valueOf(
                                loanTypeRaw
                        );

            } catch (IllegalArgumentException e) {

                return fail(
                        rowNumber,
                        "loan_type \"" +
                                loanTypeRaw +
                                "\" is not recognized. Valid values: " +
                                Arrays.toString(
                                        Loan.LoanType.values()
                                )
                );
            }


            // ========================================================
            // OPTIONAL FINANCIAL VALUES
            // ========================================================

            BigDecimal totalPaid =
                    optMoney(
                            row,
                            "total_paid"
                    );


            BigDecimal outstandingGiven =
                    optMoney(
                            row,
                            "outstanding_balance"
                    );


            BigDecimal totalRepayableGiven =
                    optMoney(
                            row,
                            "total_repayable"
                    );


            validateOptionalMoney(
                    totalPaid,
                    "total_paid"
            );


            validateOptionalMoney(
                    outstandingGiven,
                    "outstanding_balance"
            );


            validateOptionalMoney(
                    totalRepayableGiven,
                    "total_repayable"
            );


            if (totalPaid == null) {

                totalPaid = ZERO;

            } else {

                totalPaid =
                        money(
                                totalPaid
                        );
            }


            // ========================================================
            // FINANCIAL CONSISTENCY
            // ========================================================

            if (
                    totalPaid.compareTo(
                            ZERO
                    ) < 0
            ) {

                return fail(
                        rowNumber,
                        "total_paid cannot be negative."
                );
            }


            if (
                    outstandingGiven != null
                            && outstandingGiven.compareTo(
                            ZERO
                    ) < 0
            ) {

                return fail(
                        rowNumber,
                        "outstanding_balance cannot be negative."
                );
            }


            if (
                    totalRepayableGiven != null
                            && totalRepayableGiven.compareTo(
                            ZERO
                    ) < 0
            ) {

                return fail(
                        rowNumber,
                        "total_repayable cannot be negative."
                );
            }


            // ========================================================
            // DETERMINE HISTORICAL BALANCE
            // ========================================================

            BigDecimal totalRepayable;

            BigDecimal outstandingBalance;


            /*
             * Priority:
             *
             * 1. Explicit outstanding balance
             * 2. Explicit total repayable
             * 3. Reconstruct using amortization
             */

            if (outstandingGiven != null) {

                outstandingBalance =
                        money(
                                outstandingGiven
                        );


                if (totalRepayableGiven != null) {

                    totalRepayable =
                            money(
                                    totalRepayableGiven
                            );

                } else {

                    totalRepayable =
                            money(
                                    totalPaid.add(
                                            outstandingBalance
                                    )
                            );
                }


            } else if (totalRepayableGiven != null) {

                totalRepayable =
                        money(
                                totalRepayableGiven
                        );


                outstandingBalance =
                        money(
                                totalRepayable
                                        .subtract(
                                                totalPaid
                                        )
                                        .max(
                                                ZERO
                                        )
                        );


            } else {

                double[] calculated =
                        loanService.amortize(
                                amount.doubleValue(),
                                interestRate.doubleValue(),
                                durationMonths,
                                rateType
                        );


                if (
                        calculated == null
                                || calculated.length < 2
                ) {

                    throw new IllegalStateException(
                            "Unable to calculate historical loan amortization."
                    );
                }


                totalRepayable =
                        money(
                                BigDecimal.valueOf(
                                        calculated[1]
                                )
                        );


                outstandingBalance =
                        money(
                                totalRepayable
                                        .subtract(
                                                totalPaid
                                        )
                                        .max(
                                                ZERO
                                        )
                        );
            }


            outstandingBalance =
                    outstandingBalance.max(
                            ZERO
                    );


            totalRepayable =
                    totalRepayable.max(
                            ZERO
                    );


            // ========================================================
            // STATUS / BALANCE CONSISTENCY
            // ========================================================

            if (
                    (
                            "PAID".equals(statusRaw)
                                    || "CLOSED".equals(statusRaw)
                    )
                            && outstandingBalance.compareTo(
                            ZERO
                    ) > 0
            ) {

                return fail(
                        rowNumber,
                        "Loan status " +
                                statusRaw +
                                " is inconsistent with outstanding_balance=" +
                                outstandingBalance +
                                ". A PAID/CLOSED loan must have zero outstanding balance."
                );
            }


            if (
                    (
                            "PAID".equals(statusRaw)
                                    || "CLOSED".equals(statusRaw)
                    )
                            && totalPaid.compareTo(
                            totalRepayable
                    ) < 0
            ) {

                log.warn(
                        "Historical loan marked {} although totalPaid={} < totalRepayable={}. " +
                                "rowNumber={}, organizationId={}",
                        statusRaw,
                        totalPaid,
                        totalRepayable,
                        rowNumber,
                        org.getId()
                );
            }


            // ========================================================
            // BORROWER MATCHING
            // ========================================================

            String nationalIdHash =
                    HmacIndexer.index(
                            nationalId
                    );


            Borrower borrower =
                    sessionBorrowers.get(
                            nationalIdHash
                    );


            if (borrower == null) {

                Optional<Borrower> existingBorrower =
                        borrowerRepo
                                .findByNationalIdHashAndOrganization_Id(
                                        nationalIdHash,
                                        org.getId()
                                );


                if (existingBorrower.isPresent()) {

                    borrower =
                            existingBorrower.get();

                    borrowerAction =
                            "MATCHED_EXISTING_BORROWER";

                }

            } else {

                borrowerAction =
                        "MATCHED_SESSION_BORROWER";
            }


            // ========================================================
            // CREATE BORROWER
            // ========================================================

            if (borrower == null) {

                borrower =
                        Borrower.builder()
                                .organization(
                                        org
                                )
                                .firstName(
                                        firstName
                                )
                                .lastName(
                                        lastName
                                )
                                .nationalId(
                                        nationalId
                                )
                                .email(
                                        resolveEmail(
                                                row,
                                                nationalId,
                                                org.getId()
                                        )
                                )
                                .phone(
                                        phone
                                )
                                .gender(
                                        gender
                                )
                                .maritalStatus(
                                        opt(
                                                row,
                                                "marital_status",
                                                "UNKNOWN"
                                        )
                                )
                                .address(
                                        opt(
                                                row,
                                                "address",
                                                null
                                        )
                                )
                                .monthlyIncome(
                                        optDouble(
                                                row,
                                                "monthly_income"
                                        )
                                )
                                .kycStatus(
                                        "PENDING"
                                )
                                .status(
                                        Borrower.BorrowerStatus.ACTIVE
                                )
                                .imported(
                                        true
                                )
                                .build();


                if (commit) {

                    try {

                        borrower =
                                borrowerRepo.save(
                                        borrower
                                );


                        borrowerAction =
                                "CREATED_NEW_BORROWER";


                    } catch (
                            DataIntegrityViolationException e
                    ) {

                        /*
                         * A concurrent import may have created the
                         * same borrower between our lookup and save.
                         *
                         * We deliberately do not attempt to continue
                         * after a database constraint violation inside
                         * the same transaction because Hibernate/Spring
                         * may mark that transaction rollback-only.
                         *
                         * The row is therefore reported as failed and
                         * can safely be retried.
                         */

                        log.warn(
                                "Concurrent borrower creation conflict. " +
                                        "rowNumber={}, organizationId={}, nationalIdHash={}",
                                rowNumber,
                                org.getId(),
                                nationalIdHash,
                                e
                        );


                        return fail(
                                rowNumber,
                                "Borrower could not be created because another " +
                                        "record appears to have created the same borrower " +
                                        "at the same time. Please retry this row."
                        );
                    }

                } else {

                    borrowerAction =
                            "CREATED_NEW_BORROWER_PREVIEW";
                }
            }


            if (borrowerAction == null) {

                borrowerAction =
                        commit
                                ? "MATCHED_EXISTING_BORROWER"
                                : "MATCHED_EXISTING_BORROWER_PREVIEW";
            }


            /*
             * Cache only the actual borrower object.
             *
             * In preview mode this object is intentionally not persisted.
             */

            sessionBorrowers.put(
                    nationalIdHash,
                    borrower
            );


            // ========================================================
            // LOAN REFERENCE
            // ========================================================

            String suppliedReference =
                    opt(
                            row,
                            "loan_reference",
                            null
                    );


            String referenceNumber;


            if (
                    suppliedReference != null
                            && !suppliedReference.isBlank()
            ) {

                referenceNumber =
                        normalizeReference(
                                suppliedReference
                        );

            } else {

                referenceNumber =
                        loanService.newReferenceNumber(
                                org
                        );
            }


            if (
                    referenceNumber == null
                            || referenceNumber.isBlank()
            ) {

                throw new IllegalStateException(
                        "Unable to generate a loan reference number."
                );
            }


            // ========================================================
            // HISTORICAL STATUS
            // ========================================================

            boolean historicalLoan =
                    isHistoricalLoanStatus(
                            statusRaw
                    );


            // ========================================================
            // BUILD LOAN
            // ========================================================

            Loan.LoanBuilder builder =
                    Loan.builder()
                            .referenceNumber(
                                    referenceNumber
                            )
                            .organization(
                                    org
                            )
                            .borrower(
                                    borrower
                            )
                            .loanType(
                                    loanType
                            )
                            .status(
                                    status
                            )
                            .amount(
                                    amount.doubleValue()
                            )
                            .interestRate(
                                    interestRate.doubleValue()
                            )
                            .interestRateType(
                                    rateType
                            )
                            .durationMonths(
                                    durationMonths
                            )
                            .currency(
                                    resolveCurrency(
                                            row,
                                            org
                                    )
                            )
                            .totalRepayable(
                                    totalRepayable.doubleValue()
                            )
                            .totalPaid(
                                    totalPaid.doubleValue()
                            )
                            .outstandingBalance(
                                    outstandingBalance.doubleValue()
                            )
                            .startDate(
                                    startDate
                            )
                            .approvedAt(
                                    historicalLoan
                                            ? startDate
                                            : null
                            )
                            .disbursedAt(
                                    historicalLoan
                                            ? startDate.atStartOfDay()
                                            : null
                            )
                            .notes(
                                    normalizeOptionalText(
                                            opt(
                                                    row,
                                                    "notes",
                                                    null
                                            ),
                                            "notes",
                                            MAX_TEXT_LENGTH
                                    )
                            )
                            .internalNotes(
                                    buildInternalImportNote(
                                            importBatchId
                                    )
                            )
                            .imported(
                                    true
                            )
                            .importBatchId(
                                    importBatchId
                            );


            Loan loan =
                    builder.build();


            // ========================================================
            // PREVIEW MODE
            // ========================================================

            if (!commit) {

                return ImportRowResult.builder()
                        .rowNumber(
                                rowNumber
                        )
                        .success(
                                true
                        )
                        .borrowerAction(
                                borrowerAction
                        )
                        .borrowerName(
                                firstName +
                                        " " +
                                        lastName
                        )
                        .loanReferenceNumber(
                                referenceNumber
                        )
                        .build();
            }


            // ========================================================
            // SAVE LOAN
            // ========================================================

            try {

                loan =
                        loanRepo.save(
                                loan
                        );


            } catch (
                    DataIntegrityViolationException e
            ) {

                log.warn(
                        "Legacy loan import database constraint violation. " +
                                "rowNumber={}, organizationId={}, referenceNumber={}",
                        rowNumber,
                        org.getId(),
                        referenceNumber,
                        e
                );


                return fail(
                        rowNumber,
                        "Loan with reference number \"" +
                                referenceNumber +
                                "\" could not be imported because " +
                                "a conflicting record already exists or " +
                                "the database rejected the record."
                );
            }


            // ========================================================
            // SUCCESS LOG
            // ========================================================

            log.info(
                    "Legacy loan imported successfully. " +
                            "rowNumber={}, organizationId={}, loanId={}, " +
                            "referenceNumber={}, borrowerId={}, status={}, " +
                            "amount={}, totalRepayable={}, totalPaid={}, " +
                            "outstandingBalance={}, batchId={}",
                    rowNumber,
                    org.getId(),
                    loan.getId(),
                    referenceNumber,
                    borrower.getId(),
                    status,
                    amount,
                    totalRepayable,
                    totalPaid,
                    outstandingBalance,
                    importBatchId
            );


            // ========================================================
            // RETURN SUCCESS
            // ========================================================

            return ImportRowResult.builder()
                    .rowNumber(
                            rowNumber
                    )
                    .success(
                            true
                    )
                    .borrowerAction(
                            borrowerAction
                    )
                    .borrowerName(
                            firstName +
                                    " " +
                                    lastName
                    )
                    .loanReferenceNumber(
                            referenceNumber
                    )
                    .build();


        } catch (IllegalArgumentException e) {

            log.warn(
                    "Legacy loan import validation failure. " +
                            "rowNumber={}, organizationId={}, error={}",
                    rowNumber,
                    org != null
                            ? org.getId()
                            : null,
                    e.getMessage()
            );


            return fail(
                    rowNumber,
                    e.getMessage()
            );


        } catch (Exception e) {

            log.error(
                    "Unexpected legacy loan import failure. " +
                            "rowNumber={}, organizationId={}",
                    rowNumber,
                    org != null
                            ? org.getId()
                            : null,
                    e
            );


            return fail(
                    rowNumber,
                    "Unexpected import error. " +
                            "The row was not imported. " +
                            "Reference row number: " +
                            rowNumber
            );
        }
    }


    // ================================================================
    // IMPORT CONTEXT VALIDATION
    // ================================================================

    private void validateImportContext(
            Map<String, String> row,
            int rowNumber,
            Organization org,
            Long importBatchId,
            Map<String, Borrower> sessionBorrowers
    ) {

        if (row == null) {

            throw new IllegalArgumentException(
                    "Import row " +
                            rowNumber +
                            " is empty."
            );
        }


        if (rowNumber <= 0) {

            throw new IllegalArgumentException(
                    "Invalid import row number: " +
                            rowNumber
            );
        }


        if (org == null) {

            throw new IllegalArgumentException(
                    "Organization is required for legacy import."
            );
        }


        if (org.getId() == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required for legacy import."
            );
        }


        if (sessionBorrowers == null) {

            throw new IllegalArgumentException(
                    "Import borrower session cache is required."
            );
        }


        if (
                importBatchId != null
                        && importBatchId <= 0
        ) {

            throw new IllegalArgumentException(
                    "Invalid import batch ID."
            );
        }
    }


    // ================================================================
    // NATIONAL ID
    // ================================================================

    private String normalizeNationalId(
            String value
    ) {

        if (value == null) {

            throw new IllegalArgumentException(
                    "national_id is required."
            );
        }


        String normalized =
                value
                        .trim()
                        .replaceAll(
                                "\\s+",
                                ""
                        );


        return normalized;
    }


    private void validateNationalId(
            String nationalId
    ) {

        if (
                nationalId == null
                        || nationalId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "national_id is required."
            );
        }


        if (
                nationalId.length()
                        > MAX_NATIONAL_ID_LENGTH
        ) {

            throw new IllegalArgumentException(
                    "national_id is too long."
            );
        }
    }


    // ================================================================
    // STATUS
    // ================================================================

    private void validateStatus(
            String status
    ) {

        if (
                status == null
                        || !ALLOWED_IMPORT_STATUSES.contains(
                        status
                )
        ) {

            throw new IllegalArgumentException(
                    "status must be one of " +
                            ALLOWED_IMPORT_STATUSES +
                            " for historical imported loans. " +
                            "Got \"" +
                            status +
                            "\"."
            );
        }
    }


    private boolean isHistoricalLoanStatus(
            String status
    ) {

        return ALLOWED_IMPORT_STATUSES.contains(
                status
        );
    }


    // ================================================================
    // MONEY PARSING
    // ================================================================

    private BigDecimal reqMoney(
            Map<String, String> row,
            String key
    ) {

        String value =
                req(
                        row,
                        key
                );


        try {

            String normalized =
                    value
                            .replace(
                                    ",",
                                    ""
                            )
                            .trim();


            BigDecimal parsed =
                    new BigDecimal(
                            normalized
                    );


            return money(
                    parsed
            );


        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                    "\"" +
                            key +
                            "\" must be a valid decimal amount. Got \"" +
                            value +
                            "\"."
            );
        }
    }


    private BigDecimal optMoney(
            Map<String, String> row,
            String key
    ) {

        String value =
                row.get(
                        key
                );


        if (
                value == null
                        || value.isBlank()
        ) {

            return null;
        }


        try {

            BigDecimal parsed =
                    new BigDecimal(
                            value
                                    .replace(
                                            ",",
                                            ""
                                    )
                                    .trim()
                    );


            return money(
                    parsed
            );


        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                    "\"" +
                            key +
                            "\" must be a valid decimal amount if provided. Got \"" +
                            value +
                            "\"."
            );
        }
    }


    private void validateOptionalMoney(
            BigDecimal value,
            String field
    ) {

        if (value == null) {

            return;
        }


        if (
                value.compareTo(
                        ZERO
                ) < 0
        ) {

            throw new IllegalArgumentException(
                    field +
                            " cannot be negative."
            );
        }


        /*
         * Reject absurdly large financial values.
         *
         * This is a protection against corrupted spreadsheets
         * and accidental scientific-number imports.
         */

        if (
                value.precision() > 30
        ) {

            throw new IllegalArgumentException(
                    field +
                            " contains an unreasonably large value."
            );
        }
    }


    private void validatePositiveMoney(
            BigDecimal value,
            String field
    ) {

        if (
                value == null
                        || value.compareTo(
                        ZERO
                ) <= 0
        ) {

            throw new IllegalArgumentException(
                    field +
                            " must be greater than zero."
            );
        }
    }


    private BigDecimal money(
            BigDecimal value
    ) {

        if (value == null) {

            return ZERO;
        }


        return value.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }


    // ================================================================
    // INTEREST RATE
    // ================================================================

    private BigDecimal reqRate(
            Map<String, String> row,
            String key
    ) {

        String value =
                req(
                        row,
                        key
                );


        try {

            return new BigDecimal(
                    value
                            .replace(
                                    ",",
                                    ""
                            )
                            .trim()
            )
                    .setScale(
                            RATE_SCALE,
                            RoundingMode.HALF_UP
                    );


        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                    "\"" +
                            key +
                            "\" must be a valid interest rate. Got \"" +
                            value +
                            "\"."
            );
        }
    }


    private void validateInterestRate(
            BigDecimal rate
    ) {

        if (
                rate == null
                        || rate.compareTo(
                        ZERO
                ) < 0
        ) {

            throw new IllegalArgumentException(
                    "interest_rate cannot be negative."
            );
        }


        if (
                rate.compareTo(
                        MAX_INTEREST_RATE
                ) > 0
        ) {

            throw new IllegalArgumentException(
                    "interest_rate is unreasonably high. " +
                            "Maximum accepted import rate is " +
                            MAX_INTEREST_RATE +
                            "%."
            );
        }
    }


    // ================================================================
    // INTEGER PARSING
    // ================================================================

    private int reqInteger(
            Map<String, String> row,
            String key
    ) {

        String value =
                req(
                        row,
                        key
                );


        try {

            BigDecimal decimal =
                    new BigDecimal(
                            value
                                    .replace(
                                            ",",
                                            ""
                                    )
                                    .trim()
                    );


            if (
                    decimal.stripTrailingZeros()
                            .scale() > 0
            ) {

                throw new IllegalArgumentException(
                        "\"" +
                                key +
                                "\" must be a whole number."
                );
            }


            return decimal.intValueExact();


        } catch (NumberFormatException |
                 ArithmeticException e) {

            throw new IllegalArgumentException(
                    "\"" +
                            key +
                            "\" must be a valid whole number. Got \"" +
                            value +
                            "\"."
            );
        }
    }


    private void validateDuration(
            int durationMonths
    ) {

        if (
                durationMonths < MIN_DURATION_MONTHS
                        || durationMonths > MAX_DURATION_MONTHS
        ) {

            throw new IllegalArgumentException(
                    "duration_months must be between " +
                            MIN_DURATION_MONTHS +
                            " and " +
                            MAX_DURATION_MONTHS +
                            "."
            );
        }
    }


    // ================================================================
    // DATE PARSING
    // ================================================================

    private LocalDate reqDate(
            Map<String, String> row,
            String key
    ) {

        String value =
                req(
                        row,
                        key
                );


        for (
                DateTimeFormatter formatter
                : DATE_FORMATS
        ) {

            try {

                return LocalDate.parse(
                        value,
                        formatter
                );

            } catch (DateTimeParseException ignored) {

                // Try next supported format.
            }
        }


        throw new IllegalArgumentException(
                "\"" +
                        key +
                        "\" isn't a recognized date. " +
                        "Preferred format is YYYY-MM-DD. Got \"" +
                        value +
                        "\"."
        );
    }


    // ================================================================
    // GENDER
    // ================================================================

    private String normalizeGender(
            String value
    ) {

        if (value == null) {

            return "UNKNOWN";
        }


        String gender =
                value
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );


        if (
                "M".equals(gender)
                        || "MALE".equals(gender)
        ) {

            return "Male";
        }


        if (
                "F".equals(gender)
                        || "FEMALE".equals(gender)
        ) {

            return "Female";
        }


        if (
                gender.isBlank()
        ) {

            return "UNKNOWN";
        }


        /*
         * Preserve historical values rather than
         * destroying source information.
         */

        return value.trim();
    }


    // ================================================================
    // REQUIRED TEXT
    // ================================================================

    private String normalizeRequiredText(
            String value,
            String field,
            int maxLength
    ) {

        String normalized =
                value == null
                        ? ""
                        : value.trim();


        if (normalized.isBlank()) {

            throw new IllegalArgumentException(
                    field +
                            " is required."
            );
        }


        if (
                normalized.length()
                        > maxLength
        ) {

            throw new IllegalArgumentException(
                    field +
                            " is too long. Maximum length is " +
                            maxLength +
                            " characters."
            );
        }


        return normalized;
    }


    private String normalizeOptionalText(
            String value,
            String field,
            int maxLength
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {

            return null;
        }


        String normalized =
                value.trim();


        if (
                normalized.length()
                        > maxLength
        ) {

            throw new IllegalArgumentException(
                    field +
                            " is too long. Maximum length is " +
                            maxLength +
                            " characters."
            );
        }


        return normalized;
    }


    // ================================================================
    // REQUIRED FIELD
    // ================================================================

    private String req(
            Map<String, String> row,
            String key
    ) {

        String value =
                row.get(
                        key
                );


        if (
                value == null
                        || value.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "\"" +
                            key +
                            "\" is required but was blank."
            );
        }


        return value.trim();
    }


    // ================================================================
    // OPTIONAL TEXT
    // ================================================================

    private String opt(
            Map<String, String> row,
            String key,
            String fallback
    ) {

        String value =
                row.get(
                        key
                );


        if (
                value == null
                        || value.isBlank()
        ) {

            return fallback;
        }


        return value.trim();
    }


    // ================================================================
    // OPTIONAL DOUBLE
    // ================================================================

    /**
     * Used for legacy borrower fields such as monthly income.
     *
     * Returns null when the source field is empty.
     */
    private Double optDouble(
            Map<String, String> row,
            String key
    ) {

        String value =
                row.get(
                        key
                );


        if (
                value == null
                        || value.isBlank()
        ) {

            return null;
        }


        try {

            String normalized =
                    value
                            .replace(
                                    ",",
                                    ""
                            )
                            .trim();


            double parsed =
                    Double.parseDouble(
                            normalized
                    );


            if (
                    !Double.isFinite(
                            parsed
                    )
            ) {

                throw new IllegalArgumentException(
                        "\"" +
                                key +
                                "\" must be a finite number."
                );
            }


            if (
                    parsed < 0
            ) {

                throw new IllegalArgumentException(
                        "\"" +
                                key +
                                "\" cannot be negative."
                );
            }


            return parsed;


        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                    "\"" +
                            key +
                            "\" must be a valid number if provided. Got \"" +
                            value +
                            "\"."
            );
        }
    }


    // ================================================================
    // EMAIL
    // ================================================================

    /**
     * Generates a deterministic placeholder email when a historical
     * record has no email address.
     *
     * This prevents repeated imports from generating a different
     * placeholder every time.
     */
    private String resolveEmail(
            Map<String, String> row,
            String nationalId,
            Long organizationId
    ) {

        String supplied =
                row.get(
                        "email"
                );


        if (
                supplied != null
                        && !supplied.isBlank()
        ) {

            String email =
                    supplied
                            .trim()
                            .toLowerCase(
                                    Locale.ROOT
                            );


            if (
                    email.length() > 320
            ) {

                throw new IllegalArgumentException(
                        "email is too long."
                );
            }


            return email;
        }


        return "imported." +
                organizationId +
                "." +
                nationalId +
                "@imported.local";
    }


    // ================================================================
    // BACKWARD-COMPATIBLE ALIAS
    // ================================================================

    /**
     * Kept as a compatibility alias because older versions of this
     * service called this helper optOrGenerated().
     */
    private String optOrGenerated(
            Map<String, String> row,
            String key,
            String nationalId,
            Long organizationId
    ) {

        return resolveEmail(
                row,
                nationalId,
                organizationId
        );
    }


    // ================================================================
    // CURRENCY
    // ================================================================

    private String resolveCurrency(
            Map<String, String> row,
            Organization org
    ) {

        String supplied =
                opt(
                        row,
                        "currency",
                        null
                );


        if (
                supplied != null
                        && !supplied.isBlank()
        ) {

            String currency =
                    supplied
                            .trim()
                            .toUpperCase(
                                    Locale.ROOT
                            );


            if (
                    currency.length() != 3
            ) {

                throw new IllegalArgumentException(
                        "currency must be a valid 3-letter currency code."
                );
            }


            return currency;
        }


        String organizationCurrency =
                org.getDefaultCurrency();


        if (
                organizationCurrency == null
                        || organizationCurrency.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Currency is missing and the organization has no default currency."
            );
        }


        String currency =
                organizationCurrency
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );


        if (
                currency.length() != 3
        ) {

            throw new IllegalArgumentException(
                    "Organization default currency is invalid: " +
                            currency
            );
        }


        return currency;
    }


    // ================================================================
    // LOAN REFERENCE
    // ================================================================

    private String normalizeReference(
            String reference
    ) {

        if (
                reference == null
                        || reference.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "loan_reference cannot be blank."
            );
        }


        String normalized =
                reference.trim();


        if (
                normalized.length() > 150
        ) {

            throw new IllegalArgumentException(
                    "loan_reference is too long."
            );
        }


        return normalized;
    }


    // ================================================================
    // INTERNAL IMPORT NOTE
    // ================================================================

    private String buildInternalImportNote(
            Long importBatchId
    ) {

        if (importBatchId == null) {

            return "Imported from legacy ledger.";
        }


        return "Imported from legacy ledger " +
                "(batch #" +
                importBatchId +
                ").";
    }


    // ================================================================
    // FAILURE RESULT
    // ================================================================

    private ImportRowResult fail(
            int rowNumber,
            String error
    ) {

        String safeError =
                error == null
                        || error.isBlank()
                        ? "Import failed."
                        : error;


        return ImportRowResult.builder()
                .rowNumber(
                        rowNumber
                )
                .success(
                        false
                )
                .error(
                        safeError
                )
                .build();
    }
}