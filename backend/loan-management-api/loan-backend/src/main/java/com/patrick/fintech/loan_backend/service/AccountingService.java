package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Branch;
import com.patrick.fintech.loan_backend.model.ChartOfAccount;
import com.patrick.fintech.loan_backend.model.Expense;
import com.patrick.fintech.loan_backend.model.JournalEntry;
import com.patrick.fintech.loan_backend.model.JournalLine;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.ChartOfAccountRepository;
import com.patrick.fintech.loan_backend.repository.JournalEntryRepository;
import com.patrick.fintech.loan_backend.repository.JournalLineRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingService {

    private final ChartOfAccountRepository coaRepo;

    private final JournalEntryRepository journalRepo;

    private final JournalLineRepository lineRepo;


    /*
     * ============================================================
     * MONEY CONFIGURATION
     * ============================================================
     */

    private static final int MONEY_SCALE = 6;

    private static final RoundingMode MONEY_ROUNDING =
            RoundingMode.HALF_UP;

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    MONEY_ROUNDING
            );


    /*
     * ============================================================
     * DEFAULT CHART OF ACCOUNTS
     * ============================================================
     */

    private static final String[][] DEFAULT_ACCOUNTS = {

            // ----------------------------------------------------
            // ASSETS
            // ----------------------------------------------------

            {"1000", "Cash and Bank", "ASSET", "DEBIT"},

            {"1100", "Loans Receivable", "ASSET", "DEBIT"},

            {"1150", "Interest Receivable", "ASSET", "DEBIT"},

            /*
             * Contra-asset account.
             *
             * Credit normal balance.
             */
            {"1200", "Loan Loss Reserve", "ASSET", "CREDIT"},


            // ----------------------------------------------------
            // LIABILITIES
            // ----------------------------------------------------

            {"2000", "Customer Deposits Payable", "LIABILITY", "CREDIT"},


            // ----------------------------------------------------
            // EQUITY
            // ----------------------------------------------------

            {"3000", "Owner's Equity", "EQUITY", "CREDIT"},


            // ----------------------------------------------------
            // INCOME
            // ----------------------------------------------------

            {"4000", "Interest Income", "INCOME", "CREDIT"},

            {"4100", "Fee and Penalty Income", "INCOME", "CREDIT"},


            // ----------------------------------------------------
            // EXPENSES
            // ----------------------------------------------------

            {"5000", "Loan Loss Expense", "EXPENSE", "DEBIT"},

            {"5100", "Operating Expenses", "EXPENSE", "DEBIT"},

            {"5200", "Salaries and Wages", "EXPENSE", "DEBIT"},

            {"5201", "Rent", "EXPENSE", "DEBIT"},

            {"5202", "Utilities", "EXPENSE", "DEBIT"},

            {"5203", "Internet", "EXPENSE", "DEBIT"},

            {"5204", "Transport", "EXPENSE", "DEBIT"},

            {"5205", "Fuel", "EXPENSE", "DEBIT"},

            {"5206", "Office Supplies", "EXPENSE", "DEBIT"},

            {"5207", "Bank Charges", "EXPENSE", "DEBIT"},

            {"5208", "Insurance", "EXPENSE", "DEBIT"},

            {"5209", "Marketing", "EXPENSE", "DEBIT"},

            {"5210", "Legal Fees", "EXPENSE", "DEBIT"},

            {"5211", "Audit Fees", "EXPENSE", "DEBIT"},

            {"5212", "Depreciation", "EXPENSE", "DEBIT"},

            {"5213", "Loan Recovery Expenses", "EXPENSE", "DEBIT"},

            {"5214", "IT Expenses", "EXPENSE", "DEBIT"},

            {"5215", "Other Operating Expenses", "EXPENSE", "DEBIT"}
    };


    /*
     * ============================================================
     * MONEY HELPERS
     * ============================================================
     */

    private BigDecimal money(
            BigDecimal value
    ) {

        if (value == null) {
            return ZERO;
        }

        return value.setScale(
                MONEY_SCALE,
                MONEY_ROUNDING
        );
    }


    private BigDecimal money(
            Double value
    ) {

        if (value == null) {
            return ZERO;
        }

        return BigDecimal.valueOf(value)
                .setScale(
                        MONEY_SCALE,
                        MONEY_ROUNDING
                );
    }


    private BigDecimal money(
            double value
    ) {

        return BigDecimal.valueOf(value)
                .setScale(
                        MONEY_SCALE,
                        MONEY_ROUNDING
                );
    }


    private BigDecimal money(
            Number value
    ) {

        if (value == null) {
            return ZERO;
        }

        if (value instanceof BigDecimal) {

            return money(
                    (BigDecimal) value
            );
        }

        return BigDecimal.valueOf(
                        value.doubleValue()
                )
                .setScale(
                        MONEY_SCALE,
                        MONEY_ROUNDING
                );
    }


    private BigDecimal maxZero(
            BigDecimal value
    ) {

        BigDecimal normalized =
                money(value);

        return normalized.compareTo(
                ZERO
        ) < 0
                ? ZERO
                : normalized;
    }


    private boolean isPositive(
            BigDecimal value
    ) {

        return money(value)
                .compareTo(ZERO) > 0;
    }


    private BigDecimal normalize(
            BigDecimal value
    ) {

        return money(value);
    }


    /*
     * ============================================================
     * DEFAULT CHART OF ACCOUNTS
     * ============================================================
     */

    @Transactional
    public void ensureChartOfAccounts(
            Organization org
    ) {

        if (org == null || org.getId() == null) {

            throw new IllegalArgumentException(
                    "Organization is required"
            );
        }

        List<ChartOfAccount> existing =
                coaRepo.findByOrganization_IdOrderByCodeAsc(
                        org.getId()
                );

        Set<String> existingCodes =
                new HashSet<>();

        if (existing != null) {

            for (ChartOfAccount account : existing) {

                if (
                        account != null
                        && account.getCode() != null
                ) {

                    existingCodes.add(
                            account.getCode()
                    );
                }
            }
        }


        for (String[] account :
                DEFAULT_ACCOUNTS) {

            String code =
                    account[0];

            if (existingCodes.contains(code)) {
                continue;
            }


            coaRepo.save(
                    ChartOfAccount.builder()
                            .organization(org)
                            .code(code)
                            .name(account[1])
                            .type(
                                    ChartOfAccount.AccountType
                                            .valueOf(account[2])
                            )
                            .normalBalance(
                                    ChartOfAccount.NormalBalance
                                            .valueOf(account[3])
                            )
                            .active(true)
                            .build()
            );
        }


        log.info(
                "Chart of accounts verified for organization {}",
                org.getId()
        );
    }


    /*
     * ============================================================
     * ACCOUNT LOOKUP
     * ============================================================
     */

    private ChartOfAccount account(
            Organization org,
            String code
    ) {

        if (org == null || org.getId() == null) {

            throw new IllegalArgumentException(
                    "Organization is required"
            );
        }

        if (code == null || code.isBlank()) {

            throw new IllegalArgumentException(
                    "Account code is required"
            );
        }


        return coaRepo
                .findByOrganization_IdAndCode(
                        org.getId(),
                        code
                )
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Chart of accounts is not configured " +
                                "for organization " +
                                org.getId() +
                                " (missing account " +
                                code +
                                ")"
                        )
                );
    }


    /*
     * ============================================================
     * EQUITY ACCOUNT
     * ============================================================
     */

    public ChartOfAccount getEquityAccount(
            Organization org
    ) {

        ensureChartOfAccounts(org);

        return account(
                org,
                "3000"
        );
    }


    /*
     * ============================================================
     * CREATE ACCOUNT
     * ============================================================
     */

    @Transactional
    public ChartOfAccount createAccount(
            Organization org,
            String code,
            String name,
            ChartOfAccount.AccountType type,
            ChartOfAccount.NormalBalance normalBalance
    ) {

        if (org == null || org.getId() == null) {

            throw new IllegalArgumentException(
                    "Organization is required"
            );
        }

        if (code == null || code.isBlank()) {

            throw new IllegalArgumentException(
                    "Account code is required"
            );
        }

        if (name == null || name.isBlank()) {

            throw new IllegalArgumentException(
                    "Account name is required"
            );
        }

        if (type == null) {

            throw new IllegalArgumentException(
                    "Account type is required"
            );
        }

        if (normalBalance == null) {

            throw new IllegalArgumentException(
                    "Normal balance is required"
            );
        }


        if (
                coaRepo.existsByOrganization_IdAndCode(
                        org.getId(),
                        code
                )
        ) {

            throw new IllegalArgumentException(
                    "Account code " +
                    code +
                    " already exists"
            );
        }


        return coaRepo.save(
                ChartOfAccount.builder()
                        .organization(org)
                        .code(code.trim())
                        .name(name.trim())
                        .type(type)
                        .normalBalance(normalBalance)
                        .active(true)
                        .build()
        );
    }


    /*
     * ============================================================
     * UPDATE ACCOUNT
     * ============================================================
     */

    @Transactional
    public ChartOfAccount updateAccount(
            Long orgId,
            Long accountId,
            String name,
            Boolean active
    ) {

        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }

        if (accountId == null) {

            throw new IllegalArgumentException(
                    "Account ID is required"
            );
        }


        ChartOfAccount acc =
                coaRepo.findByIdAndOrganization_Id(
                        accountId,
                        orgId
                ).orElseThrow(
                        () -> new IllegalArgumentException(
                                "Account not found: " +
                                accountId
                        )
                );


        if (name != null && !name.isBlank()) {

            acc.setName(
                    name.trim()
            );
        }


        if (active != null) {

            acc.setActive(active);
        }


        return coaRepo.save(acc);
    }


    /*
     * ============================================================
     * JOURNAL POSTING
     * ============================================================
     */

    @Transactional
    public JournalEntry post(
            Organization org,
            String sourceType,
            String sourceId,
            String reference,
            String description,
            List<JournalLine> lines
    ) {

        return post(
                org,
                null,
                sourceType,
                sourceId,
                reference,
                description,
                lines
        );
    }


    /*
     * ============================================================
     * JOURNAL POSTING WITH BRANCH
     * ============================================================
     */

    @Transactional
    public JournalEntry post(
            Organization org,
            Branch branch,
            String sourceType,
            String sourceId,
            String reference,
            String description,
            List<JournalLine> lines
    ) {

        if (org == null || org.getId() == null) {

            throw new IllegalArgumentException(
                    "Organization is required"
            );
        }


        if (lines == null || lines.isEmpty()) {

            throw new IllegalArgumentException(
                    "Journal entry must contain at least one line"
            );
        }


        BigDecimal totalDebit =
                ZERO;

        BigDecimal totalCredit =
                ZERO;


        for (JournalLine line :
                lines) {

            if (line == null) {

                throw new IllegalArgumentException(
                        "Journal entry contains a null line"
                );
            }


            if (
                    line.getAccount() == null
                    || line.getAccount().getId() == null
            ) {

                throw new IllegalArgumentException(
                        "Every journal line must have an account"
                );
            }


            BigDecimal debit =
                    money(
                            line.getDebitDecimal()
                    );

            BigDecimal credit =
                    money(
                            line.getCreditDecimal()
                    );


            if (
                    debit.compareTo(ZERO) < 0
                    || credit.compareTo(ZERO) < 0
            ) {

                throw new IllegalArgumentException(
                        "Debit and credit amounts cannot be negative"
                );
            }


            if (
                    debit.compareTo(ZERO) > 0
                    && credit.compareTo(ZERO) > 0
            ) {

                throw new IllegalArgumentException(
                        "A journal line cannot contain both debit and credit"
                );
            }


            if (
                    debit.compareTo(ZERO) == 0
                    && credit.compareTo(ZERO) == 0
            ) {

                throw new IllegalArgumentException(
                        "A journal line must contain a debit or credit amount"
                );
            }


            /*
             * Normalize values before persistence.
             */

            line.setDebit(debit);

            line.setCredit(credit);


            totalDebit =
                    totalDebit.add(debit);

            totalCredit =
                    totalCredit.add(credit);
        }


        totalDebit =
                money(totalDebit);

        totalCredit =
                money(totalCredit);


        /*
         * ========================================================
         * EXACT GL BALANCE CHECK
         * ========================================================
         *
         * Because journal amounts are BigDecimal, there is no
         * floating-point tolerance here.
         */

        if (
                totalDebit.compareTo(
                        totalCredit
                ) != 0
        ) {

            throw new IllegalStateException(
                    "Journal entry does not balance: " +
                    "debits " +
                    totalDebit.toPlainString() +
                    " != credits " +
                    totalCredit.toPlainString() +
                    " (" +
                    description +
                    ")"
            );
        }


        JournalEntry entry =
                JournalEntry.builder()
                        .organization(org)
                        .branch(branch)
                        .entryDate(LocalDate.now())
                        .sourceType(sourceType)
                        .sourceId(sourceId)
                        .reference(reference)
                        .description(description)
                        .createdBy("SYSTEM")
                        .reversed(false)
                        .build();


        entry =
                journalRepo.save(entry);


        for (JournalLine line :
                lines) {

            line.setJournalEntry(entry);

            lineRepo.save(line);
        }


        return entry;
    }


    /*
     * ============================================================
     * LOAN DISBURSEMENT
     * ============================================================
     */

    @Transactional
    public void postDisbursement(
            Loan loan
    ) {

        if (loan == null) {
            return;
        }


        try {

            Organization org =
                    loan.getOrganization();


            ensureChartOfAccounts(org);


            BigDecimal amount =
                    money(
                            loan.getAmount()
                    );


            if (
                    amount.compareTo(ZERO) <= 0
            ) {

                log.debug(
                        "Skipping accounting disbursement for loan {} because amount is zero",
                        loan.getId()
                );

                return;
            }


            String reference =
                    loan.getReferenceNumber() != null
                            ? loan.getReferenceNumber()
                            : "LOAN-" + loan.getId();


            List<JournalLine> lines =
                    new ArrayList<>();


            /*
             * DR Loans Receivable
             */

            lines.add(
                    JournalLine.builder()
                            .account(
                                    account(
                                            org,
                                            "1100"
                                    )
                            )
                            .debit(amount)
                            .credit(ZERO)
                            .description(
                                    "Loans Receivable — " +
                                    reference
                            )
                            .build()
            );


            /*
             * CR Cash
             */

            lines.add(
                    JournalLine.builder()
                            .account(
                                    account(
                                            org,
                                            "1000"
                                    )
                            )
                            .debit(ZERO)
                            .credit(amount)
                            .description(
                                    "Cash disbursed — " +
                                    reference
                            )
                            .build()
            );


            post(
                    org,
                    loan.getBranch(),
                    "LOAN_DISBURSEMENT",
                    String.valueOf(
                            loan.getId()
                    ),
                    reference,
                    "Disbursement of loan " +
                    reference,
                    lines
            );


            /*
             * ====================================================
             * PROCESSING FEE
             * ====================================================
             */

            BigDecimal fee =
                    money(
                            loan.getProcessingFee()
                    );


            if (
                    fee.compareTo(ZERO) > 0
            ) {

                post(
                        org,
                        loan.getBranch(),
                        "PROCESSING_FEE",
                        String.valueOf(
                                loan.getId()
                        ),
                        reference,
                        "Processing fee collected on " +
                        reference,

                        List.of(

                                /*
                                 * DR Cash
                                 */

                                JournalLine.builder()
                                        .account(
                                                account(
                                                        org,
                                                        "1000"
                                                )
                                        )
                                        .debit(fee)
                                        .credit(ZERO)
                                        .description(
                                                "Processing fee — " +
                                                reference
                                        )
                                        .build(),

                                /*
                                 * CR Fee Income
                                 */

                                JournalLine.builder()
                                        .account(
                                                account(
                                                        org,
                                                        "4100"
                                                )
                                        )
                                        .debit(ZERO)
                                        .credit(fee)
                                        .description(
                                                "Processing fee income — " +
                                                reference
                                        )
                                        .build()
                        )
                );
            }

        } catch (Exception e) {

            log.error(
                    "Could not post GL entry for disbursement of loan {}",
                    loan.getId(),
                    e
            );

            throw e;
        }
    }


    /*
     * ============================================================
     * INTEREST ACCRUAL
     * ============================================================
     */

    @Transactional
    public void postInterestAccrual(
            Loan loan,
            double dailyInterestAmount
    ) {

        postInterestAccrual(
                loan,
                money(dailyInterestAmount)
        );
    }


    @Transactional
    public void postInterestAccrual(
            Loan loan,
            BigDecimal dailyInterestAmount
    ) {

        if (loan == null) {
            return;
        }


        BigDecimal interest =
                maxZero(
                        dailyInterestAmount
                );


        if (
                interest.compareTo(ZERO) <= 0
        ) {
            return;
        }


        Organization org =
                loan.getOrganization();


        ensureChartOfAccounts(org);


        String reference =
                loan.getReferenceNumber() != null
                        ? loan.getReferenceNumber()
                        : "LOAN-" + loan.getId();


        post(
                org,
                loan.getBranch(),
                "INTEREST_ACCRUAL",
                String.valueOf(
                        loan.getId()
                ),
                reference,
                "Daily interest accrual for " +
                reference +
                " (" +
                LocalDate.now() +
                ")",

                List.of(

                        /*
                         * DR Interest Receivable
                         */

                        JournalLine.builder()
                                .account(
                                        account(
                                                org,
                                                "1150"
                                        )
                                )
                                .debit(interest)
                                .credit(ZERO)
                                .description(
                                        "Interest accrued — " +
                                        reference
                                )
                                .build(),

                        /*
                         * CR Interest Income
                         */

                        JournalLine.builder()
                                .account(
                                        account(
                                                org,
                                                "4000"
                                        )
                                )
                                .debit(ZERO)
                                .credit(interest)
                                .description(
                                        "Interest income accrued — " +
                                        reference
                                )
                                .build()
                )
        );
    }


    /*
     * ============================================================
     * PAYMENT RECEIVED
     * ============================================================
     */

    @Transactional
    public JournalEntry postPaymentReceived(
            Payment payment,
            Double paymentAmount,
            double interestAmount,
            double principalAmount,
            double penaltyAmount
    ) {

        return postPaymentReceived(
                payment,
                money(paymentAmount),
                money(interestAmount),
                money(principalAmount),
                money(penaltyAmount)
        );
    }


    @Transactional
    public JournalEntry postPaymentReceived(
            Payment payment,
            BigDecimal paymentAmount,
            BigDecimal interestAmount,
            BigDecimal principalAmount,
            BigDecimal penaltyAmount
    ) {

        if (payment == null) {

            throw new IllegalArgumentException(
                    "Payment is required"
            );
        }


        Loan loan =
                payment.getLoan();


        if (loan == null) {

            throw new IllegalArgumentException(
                    "Payment has no loan"
            );
        }


        Organization org =
                loan.getOrganization();


        ensureChartOfAccounts(org);


        BigDecimal total =
                maxZero(paymentAmount);


        if (
                total.compareTo(ZERO) <= 0
        ) {

            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }


        BigDecimal interest =
                maxZero(interestAmount);


        BigDecimal principal =
                maxZero(principalAmount);


        BigDecimal penalty =
                maxZero(penaltyAmount);


        BigDecimal allocated =
                interest
                        .add(principal)
                        .add(penalty);


        BigDecimal difference =
                total.subtract(allocated);


        /*
         * ========================================================
         * PAYMENT ALLOCATION VALIDATION
         * ========================================================
         */

        if (
                difference.compareTo(ZERO) > 0
        ) {

            /*
             * Any unallocated payment amount reduces principal.
             */

            principal =
                    principal.add(
                            difference
                    );
        }


        if (
                difference.compareTo(ZERO) < 0
        ) {

            throw new IllegalStateException(
                    "Payment allocation exceeds payment amount: " +
                    "payment " +
                    total.toPlainString() +
                    ", interest " +
                    interest.toPlainString() +
                    ", principal " +
                    principal.toPlainString() +
                    ", penalty " +
                    penalty.toPlainString()
            );
        }


        /*
         * Final allocation validation.
         */

        BigDecimal finalAllocated =
                interest
                        .add(principal)
                        .add(penalty);


        if (
                finalAllocated.compareTo(
                        total
                ) != 0
        ) {

            throw new IllegalStateException(
                    "Payment allocation does not equal payment amount"
            );
        }


        List<JournalLine> lines =
                new ArrayList<>();


        /*
         * ========================================================
         * DR CASH
         * ========================================================
         */

        lines.add(
                JournalLine.builder()
                        .account(
                                account(
                                        org,
                                        "1000"
                                )
                        )
                        .debit(total)
                        .credit(ZERO)
                        .description(
                                "Payment received — " +
                                loan.getReferenceNumber()
                        )
                        .build()
        );


        /*
         * ========================================================
         * PRINCIPAL
         * ========================================================
         */

        if (
                principal.compareTo(ZERO) > 0
        ) {

            lines.add(
                    JournalLine.builder()
                            .account(
                                    account(
                                            org,
                                            "1100"
                                    )
                            )
                            .debit(ZERO)
                            .credit(principal)
                            .description(
                                    "Principal repayment — " +
                                    loan.getReferenceNumber()
                            )
                            .build()
            );
        }


        /*
         * ========================================================
         * INTEREST
         * ========================================================
         */

        if (
                interest.compareTo(ZERO) > 0
        ) {

            BigDecimal accrued =
                    accruedInterestReceivable(
                            org,
                            loan.getReferenceNumber()
                    );


            BigDecimal clearReceivable =
                    interest.min(
                            maxZero(accrued)
                    );


            BigDecimal directIncome =
                    interest.subtract(
                            clearReceivable
                    );


            /*
             * CR Interest Receivable
             */

            if (
                    clearReceivable.compareTo(
                            ZERO
                    ) > 0
            ) {

                lines.add(
                        JournalLine.builder()
                                .account(
                                        account(
                                                org,
                                                "1150"
                                        )
                                )
                                .debit(ZERO)
                                .credit(
                                        clearReceivable
                                )
                                .description(
                                        "Clears accrued interest — " +
                                        loan.getReferenceNumber()
                                )
                                .build()
                );
            }


            /*
             * CR Interest Income
             *
             * This handles interest that was received directly
             * without an existing interest receivable balance.
             */

            if (
                    directIncome.compareTo(
                            ZERO
                    ) > 0
            ) {

                lines.add(
                        JournalLine.builder()
                                .account(
                                        account(
                                                org,
                                                "4000"
                                        )
                                )
                                .debit(ZERO)
                                .credit(
                                        directIncome
                                )
                                .description(
                                        "Interest income — " +
                                        loan.getReferenceNumber()
                                )
                                .build()
                );
            }
        }


        /*
         * ========================================================
         * PENALTY / FEE
         * ========================================================
         */

        if (
                penalty.compareTo(ZERO) > 0
        ) {

            lines.add(
                    JournalLine.builder()
                            .account(
                                    account(
                                            org,
                                            "4100"
                                    )
                            )
                            .debit(ZERO)
                            .credit(penalty)
                            .description(
                                    "Penalty/fee income — " +
                                    loan.getReferenceNumber()
                            )
                            .build()
            );
        }


        String reference =
                payment.getPaymentReference() != null
                        ? payment.getPaymentReference()
                        : "PAY-" + payment.getId();


        return post(
                org,
                loan.getBranch(),
                "PAYMENT_RECEIVED",
                String.valueOf(
                        payment.getId()
                ),
                reference,
                "Payment received on loan " +
                loan.getReferenceNumber(),
                lines
        );
    }


    /*
     * ============================================================
     * PAYMENT OVERLOAD
     * ============================================================
     */

    @Transactional
    public JournalEntry postPaymentReceived(
            Payment payment
    ) {

        if (payment == null) {

            throw new IllegalArgumentException(
                    "Payment is required"
            );
        }


        BigDecimal amount =
                payment.getAmountPaid() != null
                        ? money(
                                payment.getAmountPaid()
                        )
                        : payment.getAmount() != null
                                ? money(
                                        payment.getAmount()
                                )
                                : ZERO;


        BigDecimal interest =
                payment.getInterestComponent() != null
                        ? money(
                                payment.getInterestComponent()
                        )
                        : ZERO;


        BigDecimal principal =
                payment.getPrincipalComponent() != null
                        ? money(
                                payment.getPrincipalComponent()
                        )
                        : ZERO;


        BigDecimal penalty =
                payment.getPenalty() != null
                        ? money(
                                payment.getPenalty()
                        )
                        : ZERO;


        return postPaymentReceived(
                payment,
                amount,
                interest,
                principal,
                penalty
        );
    }


    /*
     * ============================================================
     * ACCRUED INTEREST
     * ============================================================
     */

    private BigDecimal accruedInterestReceivable(
            Organization org,
            String loanReference
    ) {

        if (
                org == null
                || org.getId() == null
        ) {

            return ZERO;
        }


        ChartOfAccount receivable =
                coaRepo
                        .findByOrganization_IdAndCode(
                                org.getId(),
                                "1150"
                        )
                        .orElse(null);


        if (receivable == null) {
            return ZERO;
        }


        List<JournalLine> lines =
                lineRepo.findAccrualLinesForLoan(
                        receivable.getId(),
                        loanReference
                );


        if (
                lines == null
                || lines.isEmpty()
        ) {

            return ZERO;
        }


        BigDecimal balance =
                ZERO;


        for (JournalLine line :
                lines) {

            if (line == null) {
                continue;
            }


            JournalEntry entry =
                    line.getJournalEntry();


            /*
             * Reversed entries must not contribute to the
             * outstanding interest receivable.
             */

            if (
                    entry != null
                    && Boolean.TRUE.equals(
                            entry.getReversed()
                    )
            ) {

                continue;
            }


            balance =
                    balance
                            .add(
                                    money(
                                            line.getDebitDecimal()
                                    )
                            )
                            .subtract(
                                    money(
                                            line.getCreditDecimal()
                                    )
                            );
        }


        return maxZero(balance);
    }


    /*
     * ============================================================
     * WRITE OFF
     * ============================================================
     */

    @Transactional
    public void postWriteOff(
            Loan loan
    ) {

        if (loan == null) {
            return;
        }


        Organization org =
                loan.getOrganization();


        ensureChartOfAccounts(org);


        BigDecimal outstanding =
                money(
                        loan.getOutstandingBalance()
                );


        if (
                outstanding.compareTo(ZERO) <= 0
        ) {

            return;
        }


        String reference =
                loan.getReferenceNumber() != null
                        ? loan.getReferenceNumber()
                        : "LOAN-" + loan.getId();


        post(
                org,
                loan.getBranch(),
                "WRITE_OFF",
                String.valueOf(
                        loan.getId()
                ),
                reference,
                "Write-off of loan " +
                reference,

                List.of(

                        /*
                         * DR Loan Loss Expense
                         */

                        JournalLine.builder()
                                .account(
                                        account(
                                                org,
                                                "5000"
                                        )
                                )
                                .debit(outstanding)
                                .credit(ZERO)
                                .description(
                                        "Loan loss expense — " +
                                        reference
                                )
                                .build(),

                        /*
                         * CR Loans Receivable
                         */

                        JournalLine.builder()
                                .account(
                                        account(
                                                org,
                                                "1100"
                                        )
                                )
                                .debit(ZERO)
                                .credit(outstanding)
                                .description(
                                        "Write off receivable — " +
                                        reference
                                )
                                .build()
                )
        );
    }


    /*
     * ============================================================
     * EXPENSE
     * ============================================================
     */

    @Transactional
    public JournalEntry postExpense(
            Expense expense
    ) {

        if (expense == null) {

            throw new IllegalArgumentException(
                    "Expense is required"
            );
        }


        Organization org =
                expense.getOrganization();


        ensureChartOfAccounts(org);


        if (expense.getCategory() == null) {

            throw new IllegalArgumentException(
                    "Expense category is required"
            );
        }


        if (expense.getPaymentAccount() == null) {

            throw new IllegalArgumentException(
                    "Expense payment account is required"
            );
        }


        ChartOfAccount expenseAccount =
                account(
                        org,
                        expense
                                .getCategory()
                                .getAccountCode()
                );


        ChartOfAccount paymentGlAccount =
                expense
                        .getPaymentAccount()
                        .getGlAccount();


        if (paymentGlAccount == null) {

            throw new IllegalArgumentException(
                    "Payment account has no GL account"
            );
        }


        BigDecimal amount =
                money(
                        expense.getAmount()
                );


        if (
                amount.compareTo(ZERO) <= 0
        ) {

            throw new IllegalArgumentException(
                    "Expense amount must be greater than zero"
            );
        }


        String reference =
                "EXP-" +
                expense.getId();


        String description =
                "Expense — " +
                expense
                        .getCategory()
                        .getLabel();


        if (
                expense.getDescription() != null
                && !expense.getDescription().isBlank()
        ) {

            description +=
                    ": " +
                    expense.getDescription().trim();
        }


        return post(
                org,
                expense.getBranch(),
                "EXPENSE",
                String.valueOf(
                        expense.getId()
                ),
                reference,
                description,

                List.of(

                        /*
                         * DR Expense
                         */

                        JournalLine.builder()
                                .account(
                                        expenseAccount
                                )
                                .debit(amount)
                                .credit(ZERO)
                                .description(
                                        expense
                                                .getCategory()
                                                .getLabel() +
                                        " — " +
                                        reference
                                )
                                .build(),

                        /*
                         * CR Cash / Bank / Payable
                         */

                        JournalLine.builder()
                                .account(
                                        paymentGlAccount
                                )
                                .debit(ZERO)
                                .credit(amount)
                                .description(
                                        "Paid from " +
                                        expense
                                                .getPaymentAccount()
                                                .getName() +
                                        " — " +
                                        reference
                                )
                                .build()
                )
        );
    }


    /*
     * ============================================================
     * REVERSE EXPENSE
     * ============================================================
     */

    @Transactional
    public JournalEntry reverseExpense(
            Long orgId,
            Long journalEntryId,
            String reversedBy,
            String reason
    ) {

        return reverseEntry(
                orgId,
                journalEntryId,
                reversedBy,
                reason
        );
    }


    /*
     * ============================================================
     * REVERSE JOURNAL ENTRY
     * ============================================================
     */

    @Transactional
    public JournalEntry reverseEntry(
            Long orgId,
            Long entryId,
            String reversedBy,
            String reason
    ) {

        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }


        if (entryId == null) {

            throw new IllegalArgumentException(
                    "Journal entry ID is required"
            );
        }


        JournalEntry original =
                journalRepo
                        .findByIdAndOrganization_Id(
                                entryId,
                                orgId
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Journal entry not found: " +
                                        entryId
                                )
                        );


        if (
                Boolean.TRUE.equals(
                        original.getReversed()
                )
        ) {

            throw new IllegalStateException(
                    "Entry " +
                    entryId +
                    " has already been reversed"
            );
        }


        if (
                original.getLines() == null
                || original.getLines().isEmpty()
        ) {

            throw new IllegalStateException(
                    "Journal entry has no lines: " +
                    entryId
            );
        }


        List<JournalLine> reversedLines =
                new ArrayList<>();


        for (
                JournalLine line :
                original.getLines()
        ) {

            if (line == null) {
                continue;
            }


            BigDecimal originalDebit =
                    money(
                            line.getDebitDecimal()
                    );


            BigDecimal originalCredit =
                    money(
                            line.getCreditDecimal()
                    );


            reversedLines.add(
                    JournalLine.builder()
                            .account(
                                    line.getAccount()
                            )
                            .debit(
                                    originalCredit
                            )
                            .credit(
                                    originalDebit
                            )
                            .description(
                                    "Reversal of #" +
                                    entryId +
                                    " — " +
                                    (
                                            line.getDescription() != null
                                                    ? line.getDescription()
                                                    : ""
                                    )
                            )
                            .build()
            );
        }


        if (reversedLines.isEmpty()) {

            throw new IllegalStateException(
                    "Journal entry contains no valid lines: " +
                    entryId
            );
        }


        /*
         * Verify reversal itself balances before saving.
         */

        BigDecimal reversalDebit =
                ZERO;

        BigDecimal reversalCredit =
                ZERO;


        for (
                JournalLine line :
                reversedLines
        ) {

            reversalDebit =
                    reversalDebit.add(
                            money(
                                    line.getDebitDecimal()
                            )
                    );

            reversalCredit =
                    reversalCredit.add(
                            money(
                                    line.getCreditDecimal()
                            )
                    );
        }


        if (
                reversalDebit.compareTo(
                        reversalCredit
                ) != 0
        ) {

            throw new IllegalStateException(
                    "Generated reversal does not balance for entry " +
                    entryId
            );
        }


        String reversalDescription =
                "Reversal of entry #" +
                entryId;


        if (
                reason != null
                && !reason.isBlank()
        ) {

            reversalDescription +=
                    ": " +
                    reason.trim();
        }


        if (
                original.getDescription() != null
                && !original.getDescription().isBlank()
        ) {

            reversalDescription +=
                    " — " +
                    original.getDescription();
        }


        JournalEntry reversal =
                JournalEntry.builder()
                        .organization(
                                original.getOrganization()
                        )
                        .branch(
                                original.getBranch()
                        )
                        .entryDate(
                                LocalDate.now()
                        )
                        .sourceType(
                                "REVERSAL"
                        )
                        .sourceId(
                                String.valueOf(entryId)
                        )
                        .reference(
                                original.getReference()
                        )
                        .description(
                                reversalDescription
                        )
                        .createdBy(
                                reversedBy != null
                                        && !reversedBy.isBlank()
                                                ? reversedBy.trim()
                                                : "SYSTEM"
                        )
                        .reversed(false)
                        .build();


        reversal =
                journalRepo.save(reversal);


        for (
                JournalLine line :
                reversedLines
        ) {

            line.setJournalEntry(
                    reversal
            );

            lineRepo.save(line);
        }


        /*
         * Mark original entry as reversed.
         *
         * The reversal entry itself remains active.
         */

        original.setReversed(true);

        journalRepo.save(original);


        return reversal;
    }


    /*
     * ============================================================
     * LEDGER
     * ============================================================
     */

    @Transactional(readOnly = true)
    public Map<String, Object> getLedger(
            Long orgId,
            Long accountId
    ) {

        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }


        if (accountId == null) {

            throw new IllegalArgumentException(
                    "Account ID is required"
            );
        }


        ChartOfAccount acc =
                coaRepo
                        .findByIdAndOrganization_Id(
                                accountId,
                                orgId
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Account not found: " +
                                        accountId
                                )
                        );


        boolean debitNormal =
                acc.getNormalBalance()
                        == ChartOfAccount.NormalBalance.DEBIT;


        List<JournalLine> lines =
                lineRepo.findLedgerForAccount(
                        accountId
                );


        List<Map<String, Object>> rows =
                new ArrayList<>();


        BigDecimal running =
                ZERO;


        if (lines != null) {

            for (
                    JournalLine line :
                    lines
            ) {

                if (line == null) {
                    continue;
                }


                JournalEntry entry =
                        line.getJournalEntry();


                if (entry == null) {
                    continue;
                }


                if (
                        Boolean.TRUE.equals(
                                entry.getReversed()
                        )
                ) {

                    continue;
                }


                BigDecimal debit =
                        money(
                                line.getDebitDecimal()
                        );


                BigDecimal credit =
                        money(
                                line.getCreditDecimal()
                        );


                running =
                        running.add(
                                debitNormal
                                        ? debit.subtract(credit)
                                        : credit.subtract(debit)
                        );


                running =
                        normalize(running);


                Map<String, Object> row =
                        new LinkedHashMap<>();


                row.put(
                        "entryId",
                        entry.getId()
                );


                row.put(
                        "date",
                        entry.getEntryDate()
                );


                row.put(
                        "reference",
                        entry.getReference()
                );


                row.put(
                        "sourceType",
                        entry.getSourceType()
                );


                row.put(
                        "description",
                        line.getDescription() != null
                                ? line.getDescription()
                                : entry.getDescription()
                );


                row.put(
                        "debit",
                        debit
                );


                row.put(
                        "credit",
                        credit
                );


                row.put(
                        "balance",
                        running
                );


                row.put(
                        "reversed",
                        entry.getReversed()
                );


                rows.add(row);
            }
        }


        Map<String, Object> result =
                new LinkedHashMap<>();


        result.put(
                "account",
                acc
        );


        result.put(
                "entries",
                rows
        );


        result.put(
                "closingBalance",
                running
        );


        return result;
    }


    /*
     * ============================================================
     * TRIAL BALANCE
     * ============================================================
     */

    @Transactional(readOnly = true)
    public Map<String, Object> getTrialBalance(
            Long orgId
    ) {

        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }


        List<ChartOfAccount> accounts =
                coaRepo
                        .findByOrganization_IdOrderByCodeAsc(
                                orgId
                        );


        List<Map<String, Object>> rows =
                new ArrayList<>();


        BigDecimal totalDebit =
                ZERO;


        BigDecimal totalCredit =
                ZERO;


        if (accounts != null) {

            for (
                    ChartOfAccount acc :
                    accounts
            ) {

                if (acc == null) {
                    continue;
                }


                List<JournalLine> lines =
                        lineRepo.findByAccount_Id(
                                acc.getId()
                        );


                BigDecimal debit =
                        ZERO;


                BigDecimal credit =
                        ZERO;


                if (lines != null) {

                    for (
                            JournalLine line :
                            lines
                    ) {

                        if (line == null) {
                            continue;
                        }


                        JournalEntry entry =
                                line.getJournalEntry();


                        if (
                                entry != null
                                && Boolean.TRUE.equals(
                                        entry.getReversed()
                                )
                        ) {

                            continue;
                        }


                        debit =
                                debit.add(
                                        money(
                                                line.getDebitDecimal()
                                        )
                                );


                        credit =
                                credit.add(
                                        money(
                                                line.getCreditDecimal()
                                        )
                                );
                    }
                }


                debit =
                        normalize(debit);


                credit =
                        normalize(credit);


                BigDecimal net =
                        debit.subtract(credit);


                Map<String, Object> row =
                        new LinkedHashMap<>();


                row.put(
                        "code",
                        acc.getCode()
                );


                row.put(
                        "name",
                        acc.getName()
                );


                row.put(
                        "type",
                        acc.getType()
                );


                row.put(
                        "debit",
                        net.compareTo(ZERO) > 0
                                ? net
                                : ZERO
                );


                row.put(
                        "credit",
                        net.compareTo(ZERO) < 0
                                ? net.negate()
                                : ZERO
                );


                rows.add(row);


                if (
                        net.compareTo(ZERO) > 0
                ) {

                    totalDebit =
                            totalDebit.add(net);

                } else if (
                        net.compareTo(ZERO) < 0
                ) {

                    totalCredit =
                            totalCredit.add(
                                    net.negate()
                            );
                }
            }
        }


        totalDebit =
                normalize(totalDebit);


        totalCredit =
                normalize(totalCredit);


        Map<String, Object> result =
                new LinkedHashMap<>();


        result.put(
                "accounts",
                rows
        );


        result.put(
                "totalDebit",
                totalDebit
        );


        result.put(
                "totalCredit",
                totalCredit
        );


        result.put(
                "balanced",
                totalDebit.compareTo(
                        totalCredit
                ) == 0
        );


        return result;
    }


    /*
     * ============================================================
     * BALANCE SHEET
     * ============================================================
     */

    @Transactional(readOnly = true)
    public Map<String, Object> getBalanceSheet(
            Long orgId
    ) {

        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }


        List<ChartOfAccount> accounts =
                coaRepo
                        .findByOrganization_IdOrderByCodeAsc(
                                orgId
                        );


        Map<
                ChartOfAccount.AccountType,
                List<Map<String, Object>>
        > byType =
                new EnumMap<>(
                        ChartOfAccount.AccountType.class
                );


        for (
                ChartOfAccount.AccountType type :
                ChartOfAccount.AccountType.values()
        ) {

            byType.put(
                    type,
                    new ArrayList<>()
            );
        }


        BigDecimal totalAssets =
                ZERO;


        BigDecimal totalLiabilities =
                ZERO;


        BigDecimal totalEquity =
                ZERO;


        BigDecimal totalIncome =
                ZERO;


        BigDecimal totalExpense =
                ZERO;


        if (accounts != null) {

            for (
                    ChartOfAccount acc :
                    accounts
            ) {

                if (acc == null) {
                    continue;
                }


                BigDecimal balance =
                        netBalance(acc);


                Map<String, Object> row =
                        new LinkedHashMap<>();


                row.put(
                        "code",
                        acc.getCode()
                );


                row.put(
                        "name",
                        acc.getName()
                );


                row.put(
                        "type",
                        acc.getType()
                );


                row.put(
                        "normalBalance",
                        acc.getNormalBalance()
                );


                row.put(
                        "balance",
                        balance
                );


                byType
                        .get(acc.getType())
                        .add(row);


                switch (acc.getType()) {

                    case ASSET ->

                            totalAssets =
                                    totalAssets.add(
                                            balance
                                    );


                    case LIABILITY ->

                            totalLiabilities =
                                    totalLiabilities.add(
                                            balance
                                    );


                    case EQUITY ->

                            totalEquity =
                                    totalEquity.add(
                                            balance
                                    );


                    case INCOME ->

                            totalIncome =
                                    totalIncome.add(
                                            balance
                                    );


                    case EXPENSE ->

                            totalExpense =
                                    totalExpense.add(
                                            balance
                                    );
                }
            }
        }


        BigDecimal netIncome =
                totalIncome.subtract(
                        totalExpense
                );


        /*
         * Current-period income is included in equity.
         */

        totalEquity =
                totalEquity.add(
                        netIncome
                );


        BigDecimal liabilitiesPlusEquity =
                totalLiabilities.add(
                        totalEquity
                );


        BigDecimal balanceDifference =
                totalAssets.subtract(
                        liabilitiesPlusEquity
                );


        totalAssets =
                normalize(totalAssets);


        totalLiabilities =
                normalize(totalLiabilities);


        totalEquity =
                normalize(totalEquity);


        netIncome =
                normalize(netIncome);


        liabilitiesPlusEquity =
                normalize(liabilitiesPlusEquity);


        balanceDifference =
                normalize(balanceDifference);


        Map<String, Object> result =
                new LinkedHashMap<>();


        result.put(
                "asOf",
                LocalDate.now()
        );


        result.put(
                "assets",
                byType.get(
                        ChartOfAccount.AccountType.ASSET
                )
        );


        result.put(
                "liabilities",
                byType.get(
                        ChartOfAccount.AccountType.LIABILITY
                )
        );


        result.put(
                "equity",
                byType.get(
                        ChartOfAccount.AccountType.EQUITY
                )
        );


        result.put(
                "currentPeriodNetIncome",
                netIncome
        );


        result.put(
                "totalAssets",
                totalAssets
        );


        result.put(
                "totalLiabilities",
                totalLiabilities
        );


        result.put(
                "totalEquity",
                totalEquity
        );


        result.put(
                "liabilitiesPlusEquity",
                liabilitiesPlusEquity
        );


        result.put(
                "balanceDifference",
                balanceDifference
        );


        result.put(
                "balanced",
                balanceDifference.compareTo(
                        ZERO
                ) == 0
        );


        return result;
    }


    /*
     * ============================================================
     * PROFIT AND LOSS
     * ============================================================
     */

    @Transactional(readOnly = true)
    public Map<String, Object> getProfitAndLoss(
            Long orgId,
            LocalDate from,
            LocalDate to
    ) {

        validateDateRange(
                from,
                to
        );


        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }


        List<JournalEntry> entries =
                journalRepo
                        .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
                                orgId,
                                from,
                                to
                        );


        Map<String, BigDecimal> perAccount =
                new LinkedHashMap<>();


        Map<String, String> names =
                new LinkedHashMap<>();


        Map<
                String,
                ChartOfAccount.AccountType
        > types =
                new LinkedHashMap<>();


        if (entries != null) {

            for (
                    JournalEntry entry :
                    entries
            ) {

                if (entry == null) {
                    continue;
                }


                if (
                        Boolean.TRUE.equals(
                                entry.getReversed()
                        )
                ) {

                    continue;
                }


                if (entry.getLines() == null) {
                    continue;
                }


                for (
                        JournalLine line :
                        entry.getLines()
                ) {

                    if (
                            line == null
                            || line.getAccount() == null
                    ) {

                        continue;
                    }


                    ChartOfAccount acc =
                            line.getAccount();


                    if (
                            acc.getType()
                                    != ChartOfAccount.AccountType.INCOME
                            &&
                            acc.getType()
                                    != ChartOfAccount.AccountType.EXPENSE
                    ) {

                        continue;
                    }


                    BigDecimal debit =
                            money(
                                    line.getDebitDecimal()
                            );


                    BigDecimal credit =
                            money(
                                    line.getCreditDecimal()
                            );


                    BigDecimal net =
                            acc.getType()
                                    == ChartOfAccount.AccountType.INCOME
                                    ? credit.subtract(debit)
                                    : debit.subtract(credit);


                    perAccount.merge(
                            acc.getCode(),
                            net,
                            BigDecimal::add
                    );


                    names.put(
                            acc.getCode(),
                            acc.getName()
                    );


                    types.put(
                            acc.getCode(),
                            acc.getType()
                    );
                }
            }
        }


        List<Map<String, Object>> income =
                new ArrayList<>();


        List<Map<String, Object>> expense =
                new ArrayList<>();


        BigDecimal totalIncome =
                ZERO;


        BigDecimal totalExpense =
                ZERO;


        for (
                Map.Entry<String, BigDecimal> entry :
                perAccount.entrySet()
        ) {

            BigDecimal amount =
                    normalize(
                            entry.getValue()
                    );


            if (
                    amount.compareTo(ZERO) == 0
            ) {

                continue;
            }


            Map<String, Object> row =
                    new LinkedHashMap<>();


            row.put(
                    "code",
                    entry.getKey()
            );


            row.put(
                    "name",
                    names.get(
                            entry.getKey()
                    )
            );


            row.put(
                    "amount",
                    amount
            );


            if (
                    types.get(
                            entry.getKey()
                    )
                            == ChartOfAccount.AccountType.INCOME
            ) {

                income.add(row);

                totalIncome =
                        totalIncome.add(amount);

            } else {

                expense.add(row);

                totalExpense =
                        totalExpense.add(amount);
            }
        }


        BigDecimal netIncome =
                totalIncome.subtract(
                        totalExpense
                );


        Map<String, Object> result =
                new LinkedHashMap<>();


        result.put(
                "from",
                from
        );


        result.put(
                "to",
                to
        );


        result.put(
                "income",
                income
        );


        result.put(
                "expense",
                expense
        );


        result.put(
                "totalIncome",
                normalize(totalIncome)
        );


        result.put(
                "totalExpense",
                normalize(totalExpense)
        );


        result.put(
                "netIncome",
                normalize(netIncome)
        );


        return result;
    }


    /*
     * ============================================================
     * CASH FLOW
     * ============================================================
     */

    @Transactional(readOnly = true)
    public Map<String, Object> getCashFlow(
            Long orgId,
            LocalDate from,
            LocalDate to
    ) {

        validateDateRange(
                from,
                to
        );


        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }


        List<JournalEntry> entries =
                journalRepo
                        .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
                                orgId,
                                from,
                                to
                        );


        BigDecimal lending =
                ZERO;


        BigDecimal collections =
                ZERO;


        BigDecimal feesAndPenalties =
                ZERO;


        BigDecimal other =
                ZERO;


        if (entries != null) {

            for (
                    JournalEntry entry :
                    entries
            ) {

                if (entry == null) {
                    continue;
                }


                if (
                        Boolean.TRUE.equals(
                                entry.getReversed()
                        )
                ) {

                    continue;
                }


                if (entry.getLines() == null) {
                    continue;
                }


                for (
                        JournalLine line :
                        entry.getLines()
                ) {

                    if (
                            line == null
                            || line.getAccount() == null
                    ) {

                        continue;
                    }


                    /*
                     * Cash and Bank account.
                     */

                    if (
                            !"1000".equals(
                                    line
                                            .getAccount()
                                            .getCode()
                            )
                    ) {

                        continue;
                    }


                    BigDecimal debit =
                            money(
                                    line.getDebitDecimal()
                            );


                    BigDecimal credit =
                            money(
                                    line.getCreditDecimal()
                            );


                    /*
                     * Cash:
                     *
                     * Debit  = inflow
                     * Credit = outflow
                     */

                    BigDecimal net =
                            debit.subtract(
                                    credit
                            );


                    String source =
                            entry.getSourceType() != null
                                    ? entry.getSourceType()
                                    : "";


                    switch (source) {

                        case "LOAN_DISBURSEMENT" ->

                                lending =
                                        lending.add(
                                                net
                                        );


                        case "PAYMENT_RECEIVED" ->

                                collections =
                                        collections.add(
                                                net
                                        );


                        case "PROCESSING_FEE" ->

                                feesAndPenalties =
                                        feesAndPenalties.add(
                                                net
                                        );


                        default ->

                                other =
                                        other.add(
                                                net
                                        );
                    }
                }
            }
        }


        BigDecimal netChange =
                lending
                        .add(collections)
                        .add(feesAndPenalties)
                        .add(other);


        Map<String, Object> result =
                new LinkedHashMap<>();


        result.put(
                "from",
                from
        );


        result.put(
                "to",
                to
        );


        result.put(
                "cashUsedForLending",
                normalize(lending)
        );


        result.put(
                "cashFromCollections",
                normalize(collections)
        );


        result.put(
                "cashFromFees",
                normalize(feesAndPenalties)
        );


        result.put(
                "otherCashMovement",
                normalize(other)
        );


        result.put(
                "netChangeInCash",
                normalize(netChange)
        );


        return result;
    }


    /*
     * ============================================================
     * BRANCH SUMMARY
     * ============================================================
     *
     * Uses actual cash movements.
     *
     * This is important because the previous implementation used
     * total journal debits for LOAN_DISBURSEMENT. That is not the
     * amount disbursed from cash; the debit is Loans Receivable.
     */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getBranchSummary(
            Long orgId,
            LocalDate from,
            LocalDate to
    ) {

        validateDateRange(
                from,
                to
        );


        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }


        List<JournalEntry> entries =
                journalRepo
                        .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
                                orgId,
                                from,
                                to
                        );


        Map<
                String,
                BigDecimal[]
        > byBranch =
                new LinkedHashMap<>();


        if (entries != null) {

            for (
                    JournalEntry entry :
                    entries
            ) {

                if (entry == null) {
                    continue;
                }


                if (
                        Boolean.TRUE.equals(
                                entry.getReversed()
                        )
                ) {

                    continue;
                }


                String branchName =
                        entry.getBranch() != null
                                && entry.getBranch().getName() != null
                                ? entry
                                        .getBranch()
                                        .getName()
                                : "Unassigned";


                BigDecimal[] totals =
                        byBranch.computeIfAbsent(
                                branchName,
                                k -> new BigDecimal[]{
                                        ZERO,
                                        ZERO,
                                        ZERO
                                }
                        );


                if (entry.getLines() == null) {
                    continue;
                }


                String source =
                        entry.getSourceType() != null
                                ? entry.getSourceType()
                                : "";


                for (
                        JournalLine line :
                        entry.getLines()
                ) {

                    if (
                            line == null
                            || line.getAccount() == null
                    ) {

                        continue;
                    }


                    /*
                     * Branch summary is based on cash account.
                     */

                    if (
                            !"1000".equals(
                                    line
                                            .getAccount()
                                            .getCode()
                            )
                    ) {

                        continue;
                    }


                    BigDecimal debit =
                            money(
                                    line.getDebitDecimal()
                            );


                    BigDecimal credit =
                            money(
                                    line.getCreditDecimal()
                            );


                    switch (source) {

                        /*
                         * Loan disbursement is a cash outflow,
                         * therefore the cash credit is the
                         * positive amount disbursed.
                         */

                        case "LOAN_DISBURSEMENT" ->

                                totals[0] =
                                        totals[0].add(
                                                credit
                                                        .subtract(debit)
                                                        .abs()
                                        );


                        /*
                         * Payment received is a cash inflow.
                         */

                        case "PAYMENT_RECEIVED" ->

                                totals[1] =
                                        totals[1].add(
                                                debit.subtract(
                                                        credit
                                                )
                                        );


                        /*
                         * Processing fee is a cash inflow.
                         */

                        case "PROCESSING_FEE" ->

                                totals[2] =
                                        totals[2].add(
                                                debit.subtract(
                                                        credit
                                                )
                                        );


                        default -> {
                            // Nothing to aggregate.
                        }
                    }
                }
            }
        }


        List<Map<String, Object>> rows =
                new ArrayList<>();


        for (
                Map.Entry<
                        String,
                        BigDecimal[]
                > entry :
                byBranch.entrySet()
        ) {

            Map<String, Object> row =
                    new LinkedHashMap<>();


            row.put(
                    "branch",
                    entry.getKey()
            );


            row.put(
                    "disbursed",
                    normalize(
                            entry.getValue()[0]
                    )
            );


            row.put(
                    "collected",
                    normalize(
                            entry.getValue()[1]
                    )
            );


            row.put(
                    "feeIncome",
                    normalize(
                            entry.getValue()[2]
                    )
            );


            rows.add(row);
        }


        return rows;
    }


    /*
     * ============================================================
     * NET ACCOUNT BALANCE
     * ============================================================
     */

    private BigDecimal netBalance(
            ChartOfAccount acc
    ) {

        if (
                acc == null
                || acc.getId() == null
        ) {

            return ZERO;
        }


        List<JournalLine> lines =
                lineRepo.findByAccount_Id(
                        acc.getId()
                );


        if (
                lines == null
                || lines.isEmpty()
        ) {

            return ZERO;
        }


        BigDecimal debit =
                ZERO;


        BigDecimal credit =
                ZERO;


        for (
                JournalLine line :
                lines
        ) {

            if (line == null) {
                continue;
            }


            JournalEntry entry =
                    line.getJournalEntry();


            /*
             * Do not count reversed original entries.
             */

            if (
                    entry != null
                    && Boolean.TRUE.equals(
                            entry.getReversed()
                    )
            ) {

                continue;
            }


            debit =
                    debit.add(
                            money(
                                    line.getDebitDecimal()
                            )
                    );


            credit =
                    credit.add(
                            money(
                                    line.getCreditDecimal()
                            )
                    );
        }


        if (
                acc.getNormalBalance()
                        == ChartOfAccount.NormalBalance.DEBIT
        ) {

            return normalize(
                    debit.subtract(credit)
            );
        }


        return normalize(
                credit.subtract(debit)
        );
    }


    /*
     * ============================================================
     * DATE VALIDATION
     * ============================================================
     */

    private void validateDateRange(
            LocalDate from,
            LocalDate to
    ) {

        if (from == null) {

            throw new IllegalArgumentException(
                    "Start date is required"
            );
        }


        if (to == null) {

            throw new IllegalArgumentException(
                    "End date is required"
            );
        }


        if (to.isBefore(from)) {

            throw new IllegalArgumentException(
                    "End date cannot be before start date"
            );
        }
    }
}