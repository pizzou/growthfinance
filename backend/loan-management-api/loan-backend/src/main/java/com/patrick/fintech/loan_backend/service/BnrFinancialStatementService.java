package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.ChartOfAccount;
import com.patrick.fintech.loan_backend.model.JournalEntry;
import com.patrick.fintech.loan_backend.model.JournalLine;
import com.patrick.fintech.loan_backend.repository.ChartOfAccountRepository;
import com.patrick.fintech.loan_backend.repository.JournalEntryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BnrFinancialStatementService {

    private final ChartOfAccountRepository chartOfAccountRepository;

    private final JournalEntryRepository journalEntryRepository;

    /*
     * Financial calculations must use BigDecimal.
     *
     * Scale 6 is consistent with JournalLine:
     *
     * precision = 19
     * scale     = 6
     */
    private static final int MONEY_SCALE = 6;

    private static final RoundingMode MONEY_ROUNDING =
            RoundingMode.HALF_UP;

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    MONEY_ROUNDING
            );

    private static final BigDecimal BALANCE_TOLERANCE =
            new BigDecimal("0.01");

    // ============================================================
    // MAIN FINANCIAL STATEMENT
    // ============================================================

    public Map<String, Object> buildFinancialStatement(
            Long organizationId,
            LocalDate from,
            LocalDate to
    ) {

        validateDates(
                organizationId,
                from,
                to
        );

        // ========================================================
        // LOAD CHART OF ACCOUNTS
        // ========================================================

        List<ChartOfAccount> accounts =
                chartOfAccountRepository
                        .findByOrganization_IdOrderByCodeAsc(
                                organizationId
                        );

        if (accounts == null) {
            accounts = new ArrayList<>();
        }

        // ========================================================
        // ACCOUNTING START
        // ========================================================

        /*
         * We calculate balance-sheet balances from the beginning
         * of the accounting history through the requested end date.
         *
         * This allows the statement to show cumulative asset,
         * liability and equity balances.
         */
        LocalDate accountingStart =
                LocalDate.of(
                        1970,
                        1,
                        1
                );

        List<JournalEntry> balanceSheetEntries =
                journalEntryRepository
                        .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
                                organizationId,
                                accountingStart,
                                to
                        );

        if (balanceSheetEntries == null) {
            balanceSheetEntries =
                    new ArrayList<>();
        }

        // ========================================================
        // PERIOD ENTRIES
        // ========================================================

        List<JournalEntry> periodEntries =
                journalEntryRepository
                        .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
                                organizationId,
                                from,
                                to
                        );

        if (periodEntries == null) {
            periodEntries =
                    new ArrayList<>();
        }

        // ========================================================
        // ACCOUNT BALANCES
        // ========================================================

        Map<Long, BigDecimal> endingBalances =
                createBalanceMap(accounts);

        Map<Long, BigDecimal> periodDebits =
                createBalanceMap(accounts);

        Map<Long, BigDecimal> periodCredits =
                createBalanceMap(accounts);

        // ========================================================
        // PROCESS BALANCE SHEET TRANSACTIONS
        // ========================================================

        for (JournalEntry entry :
                balanceSheetEntries) {

            processEndingBalanceEntry(
                    entry,
                    endingBalances
            );
        }

        // ========================================================
        // PROCESS PERIOD TRANSACTIONS
        // ========================================================

        for (JournalEntry entry :
                periodEntries) {

            processPeriodEntry(
                    entry,
                    periodDebits,
                    periodCredits
            );
        }

        // ========================================================
        // STATEMENT COLLECTIONS
        // ========================================================

        List<Map<String, Object>> assets =
                new ArrayList<>();

        List<Map<String, Object>> liabilities =
                new ArrayList<>();

        List<Map<String, Object>> equity =
                new ArrayList<>();

        List<Map<String, Object>> income =
                new ArrayList<>();

        List<Map<String, Object>> expenses =
                new ArrayList<>();

        BigDecimal totalAssets =
                ZERO;

        BigDecimal totalLiabilities =
                ZERO;

        BigDecimal totalEquity =
                ZERO;

        BigDecimal totalIncome =
                ZERO;

        BigDecimal totalExpenses =
                ZERO;

        // ========================================================
        // CLASSIFY ACCOUNTS
        // ========================================================

        for (ChartOfAccount account :
                accounts) {

            if (account == null) {
                continue;
            }

            Long accountId =
                    account.getId();

            if (accountId == null) {
                continue;
            }

            BigDecimal endingBalance =
                    normalizeMoney(
                            endingBalances.getOrDefault(
                                    accountId,
                                    ZERO
                            )
                    );

            BigDecimal debit =
                    normalizeMoney(
                            periodDebits.getOrDefault(
                                    accountId,
                                    ZERO
                            )
                    );

            BigDecimal credit =
                    normalizeMoney(
                            periodCredits.getOrDefault(
                                    accountId,
                                    ZERO
                            )
                    );

            if (account.getType() == null) {
                continue;
            }

            // ====================================================
            // ASSETS
            // ====================================================

            switch (account.getType()) {

                case ASSET -> {

                    if (
                            isMaterial(
                                    endingBalance
                            )
                    ) {

                        Map<String, Object> row =
                                accountRow(
                                        account,
                                        endingBalance
                                );

                        /*
                         * Account 1200 is a credit-normal
                         * contra-asset.
                         */
                        boolean contraAsset =
                                "1200".equals(
                                        account.getCode()
                                )
                                ||
                                account.getNormalBalance()
                                        == ChartOfAccount.NormalBalance.CREDIT;

                        if (contraAsset) {

                            row.put(
                                    "presentation",
                                    "CONTRA_ASSET"
                            );

                            BigDecimal deduction =
                                    endingBalance
                                            .abs()
                                            .negate();

                            row.put(
                                    "deduction",
                                    deduction
                            );

                            totalAssets =
                                    subtract(
                                            totalAssets,
                                            endingBalance.abs()
                                    );

                        } else {

                            row.put(
                                    "presentation",
                                    "ASSET"
                            );

                            totalAssets =
                                    add(
                                            totalAssets,
                                            endingBalance
                                    );
                        }

                        assets.add(row);
                    }
                }

                // ====================================================
                // LIABILITIES
                // ====================================================

                case LIABILITY -> {

                    if (
                            isMaterial(
                                    endingBalance
                            )
                    ) {

                        Map<String, Object> row =
                                accountRow(
                                        account,
                                        endingBalance
                                );

                        row.put(
                                "presentation",
                                "LIABILITY"
                        );

                        totalLiabilities =
                                add(
                                        totalLiabilities,
                                        endingBalance
                                );

                        liabilities.add(row);
                    }
                }

                // ====================================================
                // EQUITY
                // ====================================================

                case EQUITY -> {

                    if (
                            isMaterial(
                                    endingBalance
                            )
                    ) {

                        Map<String, Object> row =
                                accountRow(
                                        account,
                                        endingBalance
                                );

                        row.put(
                                "presentation",
                                "EQUITY"
                        );

                        totalEquity =
                                add(
                                        totalEquity,
                                        endingBalance
                                );

                        equity.add(row);
                    }
                }

                // ====================================================
                // INCOME
                // ====================================================

                case INCOME -> {

                    BigDecimal incomeAmount =
                            subtract(
                                    credit,
                                    debit
                            );

                    if (
                            isMaterial(
                                    incomeAmount
                            )
                    ) {

                        Map<String, Object> row =
                                accountRow(
                                        account,
                                        incomeAmount
                                );

                        row.put(
                                "presentation",
                                "INCOME"
                        );

                        row.put(
                                "periodDebit",
                                debit
                        );

                        row.put(
                                "periodCredit",
                                credit
                        );

                        totalIncome =
                                add(
                                        totalIncome,
                                        incomeAmount
                                );

                        income.add(row);
                    }
                }

                // ====================================================
                // EXPENSE
                // ====================================================

                case EXPENSE -> {

                    BigDecimal expenseAmount =
                            subtract(
                                    debit,
                                    credit
                            );

                    if (
                            isMaterial(
                                    expenseAmount
                            )
                    ) {

                        Map<String, Object> row =
                                accountRow(
                                        account,
                                        expenseAmount
                                );

                        row.put(
                                "presentation",
                                "EXPENSE"
                        );

                        row.put(
                                "periodDebit",
                                debit
                        );

                        row.put(
                                "periodCredit",
                                credit
                        );

                        totalExpenses =
                                add(
                                        totalExpenses,
                                        expenseAmount
                                );

                        expenses.add(row);
                    }
                }
            }
        }

        // ========================================================
        // NET INCOME
        // ========================================================

        BigDecimal netIncome =
                subtract(
                        totalIncome,
                        totalExpenses
                );

        // ========================================================
        // EQUITY INCLUDING CURRENT PERIOD PROFIT
        // ========================================================

        BigDecimal totalEquityIncludingProfit =
                add(
                        totalEquity,
                        netIncome
                );

        // ========================================================
        // LIABILITIES + EQUITY
        // ========================================================

        BigDecimal liabilitiesPlusEquity =
                add(
                        totalLiabilities,
                        totalEquityIncludingProfit
                );

        // ========================================================
        // BALANCE SHEET DIFFERENCE
        // ========================================================

        BigDecimal balanceDifference =
                subtract(
                        totalAssets,
                        liabilitiesPlusEquity
                );

        boolean balanceSheetBalanced =
                isWithinTolerance(
                        balanceDifference
                );

        // ========================================================
        // TRIAL BALANCE
        // ========================================================

        BigDecimal trialBalanceDebit =
                ZERO;

        BigDecimal trialBalanceCredit =
                ZERO;

        /*
         * Every journal line contributes to either debit or credit.
         *
         * Reversed original entries are excluded.
         * Their reversal entries remain active and therefore
         * offset the original transaction.
         */
        for (JournalEntry entry :
                periodEntries) {

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

            for (JournalLine line :
                    entry.getLines()) {

                if (line == null) {
                    continue;
                }

                trialBalanceDebit =
                        add(
                                trialBalanceDebit,
                                value(
                                        line.getDebit()
                                )
                        );

                trialBalanceCredit =
                        add(
                                trialBalanceCredit,
                                value(
                                        line.getCredit()
                                )
                        );
            }
        }

        trialBalanceDebit =
                normalizeMoney(
                        trialBalanceDebit
                );

        trialBalanceCredit =
                normalizeMoney(
                        trialBalanceCredit
                );

        BigDecimal trialBalanceDifference =
                subtract(
                        trialBalanceDebit,
                        trialBalanceCredit
                );

        boolean trialBalanceBalanced =
                isWithinTolerance(
                        trialBalanceDifference
                );

        // ========================================================
        // STATEMENT OF FINANCIAL POSITION
        // ========================================================

        Map<String, Object>
                statementOfFinancialPosition =
                new LinkedHashMap<>();

        statementOfFinancialPosition.put(
                "assets",
                assets
        );

        statementOfFinancialPosition.put(
                "liabilities",
                liabilities
        );

        statementOfFinancialPosition.put(
                "equity",
                equity
        );

        statementOfFinancialPosition.put(
                "currentPeriodNetIncome",
                netIncome
        );

        statementOfFinancialPosition.put(
                "totalAssets",
                totalAssets
        );

        statementOfFinancialPosition.put(
                "totalLiabilities",
                totalLiabilities
        );

        statementOfFinancialPosition.put(
                "totalEquity",
                totalEquityIncludingProfit
        );

        statementOfFinancialPosition.put(
                "liabilitiesPlusEquity",
                liabilitiesPlusEquity
        );

        statementOfFinancialPosition.put(
                "balanceDifference",
                balanceDifference
        );

        statementOfFinancialPosition.put(
                "balanced",
                balanceSheetBalanced
        );

        // ========================================================
        // INCOME STATEMENT
        // ========================================================

        Map<String, Object>
                incomeStatement =
                new LinkedHashMap<>();

        incomeStatement.put(
                "income",
                income
        );

        incomeStatement.put(
                "expenses",
                expenses
        );

        incomeStatement.put(
                "totalIncome",
                totalIncome
        );

        incomeStatement.put(
                "totalExpenses",
                totalExpenses
        );

        incomeStatement.put(
                "netIncome",
                netIncome
        );

        // ========================================================
        // TRIAL BALANCE
        // ========================================================

        Map<String, Object>
                trialBalance =
                new LinkedHashMap<>();

        trialBalance.put(
                "debit",
                trialBalanceDebit
        );

        trialBalance.put(
                "credit",
                trialBalanceCredit
        );

        trialBalance.put(
                "difference",
                trialBalanceDifference
        );

        trialBalance.put(
                "balanced",
                trialBalanceBalanced
        );

        // ========================================================
        // FINAL REPORT
        // ========================================================

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "reportType",
                "BNR_FINANCIAL_STATEMENT"
        );

        result.put(
                "organizationId",
                organizationId
        );

        result.put(
                "from",
                from
        );

        result.put(
                "to",
                to
        );

        result.put(
                "generatedAt",
                LocalDateTime.now()
        );

        result.put(
                "statementOfFinancialPosition",
                statementOfFinancialPosition
        );

        result.put(
                "incomeStatement",
                incomeStatement
        );

        result.put(
                "trialBalance",
                trialBalance
        );

        result.put(
                "accountingBalanced",
                balanceSheetBalanced
                        && trialBalanceBalanced
        );

        result.put(
                "balanceDifference",
                balanceDifference
        );

        result.put(
                "trialBalanceDebit",
                trialBalanceDebit
        );

        result.put(
                "trialBalanceCredit",
                trialBalanceCredit
        );

        result.put(
                "trialBalanceDifference",
                trialBalanceDifference
        );

        result.put(
                "trialBalanceBalanced",
                trialBalanceBalanced
        );

        return result;
    }

    // ============================================================
    // CREATE BALANCE MAP
    // ============================================================

    private Map<Long, BigDecimal> createBalanceMap(
            List<ChartOfAccount> accounts
    ) {

        Map<Long, BigDecimal> balances =
                new LinkedHashMap<>();

        if (accounts == null) {
            return balances;
        }

        for (ChartOfAccount account :
                accounts) {

            if (
                    account != null
                    &&
                    account.getId() != null
            ) {

                balances.put(
                        account.getId(),
                        ZERO
                );
            }
        }

        return balances;
    }

    // ============================================================
    // PROCESS ENDING BALANCE
    // ============================================================

    private void processEndingBalanceEntry(
            JournalEntry entry,
            Map<Long, BigDecimal> balances
    ) {

        if (entry == null) {
            return;
        }

        /*
         * Do not count reversed original entries.
         *
         * The reversal entry remains active and offsets
         * the original transaction.
         */
        if (
                Boolean.TRUE.equals(
                        entry.getReversed()
                )
        ) {
            return;
        }

        if (entry.getLines() == null) {
            return;
        }

        for (JournalLine line :
                entry.getLines()) {

            if (line == null) {
                continue;
            }

            if (line.getAccount() == null) {
                continue;
            }

            ChartOfAccount account =
                    line.getAccount();

            if (account.getId() == null) {
                continue;
            }

            BigDecimal debit =
                    value(
                            line.getDebit()
                    );

            BigDecimal credit =
                    value(
                            line.getCredit()
                    );

            BigDecimal movement;

            if (
                    account.getNormalBalance()
                            ==
                            ChartOfAccount.NormalBalance.DEBIT
            ) {

                movement =
                        subtract(
                                debit,
                                credit
                        );

            } else {

                movement =
                        subtract(
                                credit,
                                debit
                        );
            }

            balances.merge(
                    account.getId(),
                    movement,
                    this::add
            );
        }
    }

    // ============================================================
    // PROCESS PERIOD ENTRY
    // ============================================================

    private void processPeriodEntry(
            JournalEntry entry,
            Map<Long, BigDecimal> debits,
            Map<Long, BigDecimal> credits
    ) {

        if (entry == null) {
            return;
        }

        if (
                Boolean.TRUE.equals(
                        entry.getReversed()
                )
        ) {
            return;
        }

        if (entry.getLines() == null) {
            return;
        }

        for (JournalLine line :
                entry.getLines()) {

            if (line == null) {
                continue;
            }

            if (line.getAccount() == null) {
                continue;
            }

            Long accountId =
                    line.getAccount().getId();

            if (accountId == null) {
                continue;
            }

            BigDecimal debit =
                    value(
                            line.getDebit()
                    );

            BigDecimal credit =
                    value(
                            line.getCredit()
                    );

            debits.merge(
                    accountId,
                    debit,
                    this::add
            );

            credits.merge(
                    accountId,
                    credit,
                    this::add
            );
        }
    }

    // ============================================================
    // ACCOUNT ROW
    // ============================================================

    private Map<String, Object> accountRow(
            ChartOfAccount account,
            BigDecimal balance
    ) {

        Map<String, Object> row =
                new LinkedHashMap<>();

        row.put(
                "code",
                account.getCode()
        );

        row.put(
                "name",
                account.getName()
        );

        row.put(
                "type",
                account.getType()
        );

        row.put(
                "normalBalance",
                account.getNormalBalance()
        );

        row.put(
                "balance",
                normalizeMoney(balance)
        );

        return row;
    }

    // ============================================================
    // BIGDECIMAL VALUE
    // ============================================================

    /**
     * Converts a financial value into a normalized BigDecimal.
     *
     * This method deliberately accepts Number so it remains
     * compatible with either:
     *
     * BigDecimal
     * Double
     * Integer
     * Long
     *
     * Therefore the service will continue compiling even if a
     * legacy JournalLine getter returns Double.
     */
    private BigDecimal value(
            Number value
    ) {

        if (value == null) {
            return ZERO;
        }

        if (value instanceof BigDecimal) {

            return normalizeMoney(
                    (BigDecimal) value
            );
        }

        /*
         * BigDecimal.valueOf(double) is safer than:
         *
         * new BigDecimal(double)
         *
         * because the latter exposes the binary floating-point
         * representation.
         */
        return normalizeMoney(
                BigDecimal.valueOf(
                        value.doubleValue()
                )
        );
    }

    // ============================================================
    // ADD
    // ============================================================

    private BigDecimal add(
            BigDecimal first,
            BigDecimal second
    ) {

        BigDecimal a =
                first == null
                        ? ZERO
                        : first;

        BigDecimal b =
                second == null
                        ? ZERO
                        : second;

        return normalizeMoney(
                a.add(b)
        );
    }

    // ============================================================
    // SUBTRACT
    // ============================================================

    private BigDecimal subtract(
            BigDecimal first,
            BigDecimal second
    ) {

        BigDecimal a =
                first == null
                        ? ZERO
                        : first;

        BigDecimal b =
                second == null
                        ? ZERO
                        : second;

        return normalizeMoney(
                a.subtract(b)
        );
    }

    // ============================================================
    // NORMALIZE MONEY
    // ============================================================

    private BigDecimal normalizeMoney(
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

    // ============================================================
    // MATERIAL VALUE CHECK
    // ============================================================

    private boolean isMaterial(
            BigDecimal value
    ) {

        if (value == null) {
            return false;
        }

        return value.abs()
                .compareTo(
                        BALANCE_TOLERANCE
                ) >= 0;
    }

    // ============================================================
    // TOLERANCE CHECK
    // ============================================================

    private boolean isWithinTolerance(
            BigDecimal value
    ) {

        if (value == null) {
            return true;
        }

        return value.abs()
                .compareTo(
                        BALANCE_TOLERANCE
                ) < 0;
    }

    // ============================================================
    // VALIDATION
    // ============================================================

    private void validateDates(
            Long organizationId,
            LocalDate from,
            LocalDate to
    ) {

        if (organizationId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required."
            );
        }

        if (from == null) {

            throw new IllegalArgumentException(
                    "Financial statement start date is required."
            );
        }

        if (to == null) {

            throw new IllegalArgumentException(
                    "Financial statement end date is required."
            );
        }

        if (from.isAfter(to)) {

            throw new IllegalArgumentException(
                    "Financial statement start date cannot be after end date."
            );
        }
    }
}