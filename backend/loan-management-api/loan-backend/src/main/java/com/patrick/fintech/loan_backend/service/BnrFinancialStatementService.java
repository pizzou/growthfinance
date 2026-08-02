package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.ChartOfAccount;
import com.patrick.fintech.loan_backend.model.JournalEntry;
import com.patrick.fintech.loan_backend.model.JournalLine;
import com.patrick.fintech.loan_backend.repository.ChartOfAccountRepository;
import com.patrick.fintech.loan_backend.repository.JournalEntryRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BnrFinancialStatementService {

    private final ChartOfAccountRepository chartOfAccountRepository;
    private final JournalEntryRepository journalEntryRepository;


    // ============================================================
    // MAIN FINANCIAL STATEMENT
    // ============================================================

    /**
     * Builds the accounting-based BNR financial statement.
     *
     * Sources:
     *
     * ChartOfAccount
     * JournalEntry
     * JournalLine
     *
     * The report contains:
     *
     * 1. Statement of Financial Position
     * 2. Income Statement
     * 3. Trial Balance control
     * 4. Accounting balance validation
     *
     * IMPORTANT:
     *
     * Balance Sheet:
     *     Uses all accounting transactions up to the report end date.
     *
     * Income Statement:
     *     Uses transactions only between from and to.
     *
     * This prevents the balance sheet from incorrectly showing only
     * the current reporting-period movements.
     */
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
        // BALANCE SHEET ENTRIES
        // ========================================================

        /*
         * We need the cumulative accounting position through the
         * report end date.
         *
         * PostgreSQL dates support this safely.
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

        Map<Long, Double> endingBalances =
                createBalanceMap(accounts);


        Map<Long, Double> periodDebits =
                createBalanceMap(accounts);


        Map<Long, Double> periodCredits =
                createBalanceMap(accounts);


        // ========================================================
        // PROCESS BALANCE SHEET TRANSACTIONS
        // ========================================================

        for (JournalEntry entry : balanceSheetEntries) {

            processEndingBalanceEntry(
                    entry,
                    endingBalances
            );
        }


        // ========================================================
        // PROCESS PERIOD TRANSACTIONS
        // ========================================================

        for (JournalEntry entry : periodEntries) {

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


        double totalAssets =
                0.0;

        double totalLiabilities =
                0.0;

        double totalEquity =
                0.0;

        double totalIncome =
                0.0;

        double totalExpenses =
                0.0;


        // ========================================================
        // CLASSIFY ACCOUNTS
        // ========================================================

        for (ChartOfAccount account : accounts) {

            if (account == null) {
                continue;
            }

            Long accountId =
                    account.getId();

            if (accountId == null) {
                continue;
            }


            double endingBalance =
                    endingBalances.getOrDefault(
                            accountId,
                            0.0
                    );


            double debit =
                    periodDebits.getOrDefault(
                            accountId,
                            0.0
                    );


            double credit =
                    periodCredits.getOrDefault(
                            accountId,
                            0.0
                    );


            // ====================================================
            // ASSET / LIABILITY / EQUITY BALANCE
            // ====================================================

            if (account.getType() != null) {

                switch (account.getType()) {

                    // ============================================
                    // ASSETS
                    // ============================================

                    case ASSET -> {

                        if (
                                Math.abs(
                                        endingBalance
                                ) >= 0.005
                        ) {

                            Map<String, Object> row =
                                    accountRow(
                                            account,
                                            endingBalance
                                    );


                            /*
                             * Account 1200 is treated as a
                             * contra-asset because your existing
                             * accounting configuration defines it
                             * as an asset with credit normal balance.
                             */
                            if (
                                    "1200".equals(
                                            account.getCode()
                                    )
                                    ||
                                    account.getNormalBalance()
                                            == ChartOfAccount.NormalBalance.CREDIT
                            ) {

                                row.put(
                                        "presentation",
                                        "CONTRA_ASSET"
                                );

                                row.put(
                                        "deduction",
                                        -Math.abs(
                                                endingBalance
                                        )
                                );

                                totalAssets -=
                                        Math.abs(
                                                endingBalance
                                        );

                            } else {

                                row.put(
                                        "presentation",
                                        "ASSET"
                                );

                                totalAssets +=
                                        endingBalance;
                            }


                            assets.add(row);
                        }
                    }


                    // ============================================
                    // LIABILITIES
                    // ============================================

                    case LIABILITY -> {

                        if (
                                Math.abs(
                                        endingBalance
                                ) >= 0.005
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

                            totalLiabilities +=
                                    endingBalance;

                            liabilities.add(row);
                        }
                    }


                    // ============================================
                    // EQUITY
                    // ============================================

                    case EQUITY -> {

                        if (
                                Math.abs(
                                        endingBalance
                                ) >= 0.005
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

                            totalEquity +=
                                    endingBalance;

                            equity.add(row);
                        }
                    }


                    // ============================================
                    // INCOME
                    // ============================================

                    case INCOME -> {

                        double incomeAmount =
                                credit - debit;

                        if (
                                Math.abs(
                                        incomeAmount
                                ) >= 0.005
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

                            totalIncome +=
                                    incomeAmount;

                            income.add(row);
                        }
                    }


                    // ============================================
                    // EXPENSE
                    // ============================================

                    case EXPENSE -> {

                        double expenseAmount =
                                debit - credit;

                        if (
                                Math.abs(
                                        expenseAmount
                                ) >= 0.005
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

                            totalExpenses +=
                                    expenseAmount;

                            expenses.add(row);
                        }
                    }
                }
            }
        }


        // ========================================================
        // NET INCOME
        // ========================================================

        double netIncome =
                totalIncome -
                        totalExpenses;


        // ========================================================
        // EQUITY INCLUDING CURRENT PERIOD PROFIT
        // ========================================================

        double totalEquityIncludingProfit =
                totalEquity +
                        netIncome;


        // ========================================================
        // LIABILITIES + EQUITY
        // ========================================================

        double liabilitiesPlusEquity =
                totalLiabilities +
                        totalEquityIncludingProfit;


        // ========================================================
        // BALANCE SHEET DIFFERENCE
        // ========================================================

        double balanceDifference =
                totalAssets -
                        liabilitiesPlusEquity;


        boolean balanceSheetBalanced =
                Math.abs(
                        balanceDifference
                ) < 0.01;


        // ========================================================
        // TRIAL BALANCE
        // ========================================================

        double trialBalanceDebit =
                0.0;

        double trialBalanceCredit =
                0.0;


        /*
         * Trial balance is based on the period transactions.
         *
         * Every journal line contributes to either debit or credit.
         */
        for (JournalEntry entry : periodEntries) {

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

                if (line == null) {
                    continue;
                }


                trialBalanceDebit +=
                        value(
                                line.getDebit()
                        );


                trialBalanceCredit +=
                        value(
                                line.getCredit()
                        );
            }
        }


        double trialBalanceDifference =
                trialBalanceDebit -
                        trialBalanceCredit;


        boolean trialBalanceBalanced =
                Math.abs(
                        trialBalanceDifference
                ) < 0.01;


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

    private Map<Long, Double> createBalanceMap(
            List<ChartOfAccount> accounts
    ) {

        Map<Long, Double> balances =
                new LinkedHashMap<>();


        for (
                ChartOfAccount account :
                accounts
        ) {

            if (
                    account != null
                    &&
                    account.getId() != null
            ) {

                balances.put(
                        account.getId(),
                        0.0
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
            Map<Long, Double> balances
    ) {

        if (entry == null) {
            return;
        }


        /*
         * Reversed journal entries are excluded.
         *
         * Their reversal journal entry remains and offsets
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


        for (
                JournalLine line :
                entry.getLines()
        ) {

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


            double debit =
                    value(
                            line.getDebit()
                    );


            double credit =
                    value(
                            line.getCredit()
                    );


            double movement;


            if (
                    account.getNormalBalance()
                            ==
                            ChartOfAccount.NormalBalance.DEBIT
            ) {

                movement =
                        debit - credit;

            } else {

                movement =
                        credit - debit;
            }


            balances.merge(
                    account.getId(),
                    movement,
                    Double::sum
            );
        }
    }


    // ============================================================
    // PROCESS PERIOD ENTRY
    // ============================================================

    private void processPeriodEntry(
            JournalEntry entry,
            Map<Long, Double> debits,
            Map<Long, Double> credits
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


        for (
                JournalLine line :
                entry.getLines()
        ) {

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


            double debit =
                    value(
                            line.getDebit()
                    );


            double credit =
                    value(
                            line.getCredit()
                    );


            debits.merge(
                    accountId,
                    debit,
                    Double::sum
            );


            credits.merge(
                    accountId,
                    credit,
                    Double::sum
            );
        }
    }


    // ============================================================
    // ACCOUNT ROW
    // ============================================================

    private Map<String, Object> accountRow(
            ChartOfAccount account,
            double balance
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
                balance
        );


        return row;
    }


    // ============================================================
    // DOUBLE VALUE
    // ============================================================

    private double value(
            Double value
    ) {

        return value == null
                ? 0.0
                : value;
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