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
public class BnrFinancialStatementService {

    private final ChartOfAccountRepository chartOfAccountRepository;
    private final JournalEntryRepository journalEntryRepository;


    // ============================================================
    // BNR FINANCIAL STATEMENT
    // ============================================================

    /**
     * Builds an accounting-based financial statement.
     *
     * Source of truth:
     *
     * JournalEntry
     *      ↓
     * JournalLine
     *      ↓
     * ChartOfAccount
     *
     * The report contains:
     *
     * 1. Statement of Financial Position
     * 2. Income Statement
     * 3. Accounting balance check
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildFinancialStatement(
            Long orgId,
            LocalDate from,
            LocalDate to
    ) {

        if (orgId == null) {
            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }

        if (from == null) {
            throw new IllegalArgumentException(
                    "Financial statement start date is required"
            );
        }

        if (to == null) {
            throw new IllegalArgumentException(
                    "Financial statement end date is required"
            );
        }

        if (to.isBefore(from)) {
            throw new IllegalArgumentException(
                    "Financial statement end date cannot be before start date"
            );
        }


        // ========================================================
        // LOAD CHART OF ACCOUNTS
        // ========================================================

        List<ChartOfAccount> accounts =
                chartOfAccountRepository
                        .findByOrganization_IdOrderByCodeAsc(
                                orgId
                        );


        // ========================================================
        // LOAD JOURNAL ENTRIES
        // ========================================================

        List<JournalEntry> entries =
                journalEntryRepository
                        .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
                                orgId,
                                from,
                                to
                        );


        // ========================================================
        // ACCOUNT BALANCES
        // ========================================================

        Map<Long, Double> balances =
                new LinkedHashMap<>();

        for (ChartOfAccount account : accounts) {

            balances.put(
                    account.getId(),
                    0.0
            );
        }


        // ========================================================
        // PROCESS JOURNAL ENTRIES
        // ========================================================

        for (JournalEntry entry : entries) {

            if (entry == null) {
                continue;
            }

            /*
             * Reversed original entries are excluded.
             *
             * The separate reversal journal entry remains
             * available and therefore offsets the original.
             */
            if (Boolean.TRUE.equals(
                    entry.getReversed()
            )) {
                continue;
            }

            if (entry.getLines() == null) {
                continue;
            }


            for (JournalLine line :
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


                double debit =
                        line.getDebit() != null
                                ? line.getDebit()
                                : 0.0;


                double credit =
                        line.getCredit() != null
                                ? line.getCredit()
                                : 0.0;


                double movement;


                /*
                 * Debit-normal accounts:
                 *
                 * Assets
                 * Expenses
                 *
                 * Credit-normal accounts:
                 *
                 * Liabilities
                 * Equity
                 * Income
                 */
                if (
                        account.getNormalBalance()
                                == ChartOfAccount.NormalBalance.DEBIT
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


        // ========================================================
        // STATEMENT SECTIONS
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


        double totalAssets = 0.0;

        double totalLiabilities = 0.0;

        double totalEquity = 0.0;

        double totalIncome = 0.0;

        double totalExpenses = 0.0;


        // ========================================================
        // CLASSIFY ACCOUNTS
        // ========================================================

        for (ChartOfAccount account : accounts) {

            double balance =
                    balances.getOrDefault(
                            account.getId(),
                            0.0
                    );


            /*
             * Ignore effectively-zero accounts in the
             * presentation.
             */
            if (Math.abs(balance) < 0.005) {
                continue;
            }


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


            switch (account.getType()) {

                // =================================================
                // ASSETS
                // =================================================

                case ASSET -> {

                    /*
                     * 1200 Loan Loss Reserve is currently defined
                     * in the existing AccountingService as:
                     *
                     * ASSET / CREDIT
                     *
                     * Therefore it behaves as a contra-asset.
                     */
                    if (
                            "1200".equals(
                                    account.getCode()
                            )
                    ) {

                        row.put(
                                "presentation",
                                "CONTRA_ASSET"
                        );

                        row.put(
                                "deduction",
                                -Math.abs(balance)
                        );

                        totalAssets -=
                                Math.abs(balance);

                    } else {

                        row.put(
                                "presentation",
                                "ASSET"
                        );

                        totalAssets +=
                                balance;
                    }

                    assets.add(row);
                }


                // =================================================
                // LIABILITIES
                // =================================================

                case LIABILITY -> {

                    row.put(
                            "presentation",
                            "LIABILITY"
                    );

                    totalLiabilities +=
                            balance;

                    liabilities.add(row);
                }


                // =================================================
                // EQUITY
                // =================================================

                case EQUITY -> {

                    row.put(
                            "presentation",
                            "EQUITY"
                    );

                    totalEquity +=
                            balance;

                    equity.add(row);
                }


                // =================================================
                // INCOME
                // =================================================

                case INCOME -> {

                    row.put(
                            "presentation",
                            "INCOME"
                    );

                    totalIncome +=
                            balance;

                    income.add(row);
                }


                // =================================================
                // EXPENSE
                // =================================================

                case EXPENSE -> {

                    row.put(
                            "presentation",
                            "EXPENSE"
                    );

                    totalExpenses +=
                            balance;

                    expenses.add(row);
                }
            }
        }


        // ========================================================
        // NET INCOME
        // ========================================================

        double netIncome =
                totalIncome -
                totalExpenses;


        /*
         * Current-period net income is added to equity for
         * the statement of financial position.
         */
        double totalEquityIncludingProfit =
                totalEquity +
                netIncome;


        double liabilitiesPlusEquity =
                totalLiabilities +
                totalEquityIncludingProfit;


        // ========================================================
        // BALANCE CHECK
        // ========================================================

        double balanceDifference =
                totalAssets -
                liabilitiesPlusEquity;


        boolean balanced =
                Math.abs(balanceDifference) < 0.01;


        // ========================================================
        // STATEMENT OF FINANCIAL POSITION
        // ========================================================

        Map<String, Object> statementOfFinancialPosition =
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
                balanced
        );


        // ========================================================
        // INCOME STATEMENT
        // ========================================================

        Map<String, Object> incomeStatement =
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
        // REPORT
        // ========================================================

        Map<String, Object> result =
                new LinkedHashMap<>();


        result.put(
                "reportType",
                "BNR_FINANCIAL_STATEMENT"
        );

        result.put(
                "organizationId",
                orgId
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
                "statementOfFinancialPosition",
                statementOfFinancialPosition
        );

        result.put(
                "incomeStatement",
                incomeStatement
        );

        result.put(
                "accountingBalanced",
                balanced
        );

        result.put(
                "balanceDifference",
                balanceDifference
        );

        result.put(
                "generatedAt",
                LocalDateTime.now()
        );


        return result;
    }
}