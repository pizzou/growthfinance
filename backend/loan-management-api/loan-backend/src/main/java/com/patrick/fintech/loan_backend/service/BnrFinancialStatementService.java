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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BnrFinancialStatementService {


private final ChartOfAccountRepository chartOfAccountRepository;

private final JournalEntryRepository journalEntryRepository;


private static final int MONEY_SCALE = 6;

private static final RoundingMode MONEY_ROUNDING =
        RoundingMode.HALF_UP;

private static final BigDecimal ZERO =
        BigDecimal.ZERO.setScale(
                MONEY_SCALE,
                MONEY_ROUNDING
        );

/*
 * One cent is the materiality threshold used for balance checks.
 *
 * A balance of less than one cent is treated as immaterial.
 */
private static final BigDecimal BALANCE_TOLERANCE =
        new BigDecimal("0.01");

/*
 * Accounting history does not normally start in 1970.
 *
 * However, using a stable lower bound allows this service to
 * work with existing installations without requiring an
 * accounting-period table.
 */
private static final LocalDate ACCOUNTING_EPOCH =
        LocalDate.of(
                1970,
                1,
                1
        );

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

    log.info(
            "Generating BNR financial statement: organizationId={}, from={}, to={}",
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

    /*
     * Remove invalid/null accounts defensively and make the
     * presentation deterministic.
     */
    accounts =
            accounts.stream()
                    .filter(Objects::nonNull)
                    .filter(account -> account.getId() != null)
                    .sorted(
                            Comparator.comparing(
                                    account -> safeString(
                                            account.getCode()
                                    )
                            )
                    )
                    .toList();

    // ========================================================
    // LOAD HISTORICAL JOURNAL ENTRIES
    // ========================================================

    List<JournalEntry> historicalEntries =
            journalEntryRepository
                    .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
                            organizationId,
                            ACCOUNTING_EPOCH,
                            to
                    );

    if (historicalEntries == null) {
        historicalEntries =
                new ArrayList<>();
    }

    // ========================================================
    // LOAD PERIOD JOURNAL ENTRIES
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

    /*
     * Keep only active/postable entries.
     *
     * Reversed original entries are deliberately excluded.
     * Their corresponding reversal entry remains active and
     * therefore provides the accounting offset.
     */
    historicalEntries =
            activeEntries(
                    historicalEntries
            );

    periodEntries =
            activeEntries(
                    periodEntries
            );

    // ========================================================
    // ACCOUNT MAPS
    // ========================================================

    Map<Long, BigDecimal> endingBalances =
            createBalanceMap(
                    accounts
            );

    Map<Long, BigDecimal> periodDebits =
            createBalanceMap(
                    accounts
            );

    Map<Long, BigDecimal> periodCredits =
            createBalanceMap(
                    accounts
            );

    Map<Long, BigDecimal> historicalIncome =
            createBalanceMap(
                    accounts
            );

    Map<Long, BigDecimal> historicalExpenses =
            createBalanceMap(
                    accounts
            );

    // ========================================================
    // PROCESS HISTORICAL ENTRIES
    // ========================================================

    for (JournalEntry entry :
            historicalEntries) {

        processEndingBalanceEntry(
                entry,
                endingBalances,
                organizationId
        );

        processIncomeExpenseEntry(
                entry,
                historicalIncome,
                historicalExpenses,
                organizationId
        );
    }

    // ========================================================
    // PROCESS CURRENT PERIOD
    // ========================================================

    for (JournalEntry entry :
            periodEntries) {

        processPeriodEntry(
                entry,
                periodDebits,
                periodCredits,
                organizationId
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

    // ========================================================
    // TOTALS
    // ========================================================

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

    BigDecimal historicalIncomeTotal =
            ZERO;

    BigDecimal historicalExpenseTotal =
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

        if (account.getType() == null) {
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

        BigDecimal historicalIncomeAmount =
                normalizeMoney(
                        historicalIncome.getOrDefault(
                                accountId,
                                ZERO
                        )
                );

        BigDecimal historicalExpenseAmount =
                normalizeMoney(
                        historicalExpenses.getOrDefault(
                                accountId,
                                ZERO
                        )
                );

        switch (account.getType()) {

            // =================================================
            // ASSET
            // =================================================

            case ASSET -> {

                if (!isMaterial(endingBalance)) {
                    continue;
                }

                Map<String, Object> row =
                        accountRow(
                                account,
                                endingBalance
                        );

                boolean contraAsset =
                        isContraAsset(
                                account
                        );

                if (contraAsset) {

                    row.put(
                            "presentation",
                            "CONTRA_ASSET"
                    );

                    BigDecimal deduction =
                            normalizeMoney(
                                    endingBalance
                                            .abs()
                                            .negate()
                            );

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

            // =================================================
            // LIABILITY
            // =================================================

            case LIABILITY -> {

                if (!isMaterial(endingBalance)) {
                    continue;
                }

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
                                normalizedStatementBalance(
                                        account,
                                        endingBalance
                                )
                        );

                liabilities.add(row);
            }

            // =================================================
            // EQUITY
            // =================================================

            case EQUITY -> {

                if (!isMaterial(endingBalance)) {
                    continue;
                }

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
                                normalizedStatementBalance(
                                        account,
                                        endingBalance
                                )
                        );

                equity.add(row);
            }

            // =================================================
            // INCOME
            // =================================================

            case INCOME -> {

                /*
                 * Current-period income is based on the movement
                 * during the requested reporting period.
                 */
                BigDecimal periodIncome =
                        subtract(
                                credit,
                                debit
                        );

                /*
                 * Historical income is useful for diagnostics
                 * and retained-profit analysis, but is not
                 * presented as current-period revenue.
                 */
                historicalIncomeTotal =
                        add(
                                historicalIncomeTotal,
                                historicalIncomeAmount
                        );

                if (!isMaterial(periodIncome)) {
                    continue;
                }

                Map<String, Object> row =
                        accountRow(
                                account,
                                periodIncome
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

                row.put(
                        "periodAmount",
                        periodIncome
                );

                totalIncome =
                        add(
                                totalIncome,
                                periodIncome
                        );

                income.add(row);
            }

            // =================================================
            // EXPENSE
            // =================================================

            case EXPENSE -> {

                BigDecimal periodExpense =
                        subtract(
                                debit,
                                credit
                        );

                historicalExpenseTotal =
                        add(
                                historicalExpenseTotal,
                                historicalExpenseAmount
                        );

                if (!isMaterial(periodExpense)) {
                    continue;
                }

                Map<String, Object> row =
                        accountRow(
                                account,
                                periodExpense
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

                row.put(
                        "periodAmount",
                        periodExpense
                );

                totalExpenses =
                        add(
                                totalExpenses,
                                periodExpense
                        );

                expenses.add(row);
            }
        }
    }

    // ========================================================
    // CURRENT PERIOD NET INCOME
    // ========================================================

    BigDecimal netIncome =
            subtract(
                    totalIncome,
                    totalExpenses
            );

    // ========================================================
    // CURRENT PERIOD EQUITY
    // ========================================================

    /*
     * Income and expense accounts normally have temporary
     * balances. Their current-period result therefore flows
     * into equity for presentation purposes.
     *
     * We do not add historical income/expense again here,
     * because doing so would double-count retained earnings
     * where the ledger already contains closing/retained-profit
     * entries.
     */
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

    for (JournalEntry entry :
            periodEntries) {

        if (entry == null) {
            continue;
        }

        if (entry.getLines() == null) {
            continue;
        }

        validateJournalEntry(
                entry,
                organizationId
        );

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
    // ACCOUNTING INTEGRITY
    // ========================================================

    boolean accountingBalanced =
            balanceSheetBalanced
                    && trialBalanceBalanced;

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
            "currencyPrecision",
            MONEY_SCALE
    );

    result.put(
            "materialityTolerance",
            BALANCE_TOLERANCE
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
            accountingBalanced
    );

    result.put(
            "balanceSheetBalanced",
            balanceSheetBalanced
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

    result.put(
            "historicalIncomeTotal",
            historicalIncomeTotal
    );

    result.put(
            "historicalExpenseTotal",
            historicalExpenseTotal
    );

    log.info(
            "BNR financial statement generated: organizationId={}, from={}, to={}, assets={}, liabilities={}, equity={}, income={}, expenses={}, netIncome={}, balanceDifference={}, trialBalanceDifference={}, accountingBalanced={}",
            organizationId,
            from,
            to,
            totalAssets,
            totalLiabilities,
            totalEquityIncludingProfit,
            totalIncome,
            totalExpenses,
            netIncome,
            balanceDifference,
            trialBalanceDifference,
            accountingBalanced
    );

    return result;
}

// ============================================================
// ACTIVE ENTRIES
// ============================================================

private List<JournalEntry> activeEntries(
        List<JournalEntry> entries
) {

    if (entries == null || entries.isEmpty()) {
        return new ArrayList<>();
    }

    return entries.stream()
            .filter(Objects::nonNull)
            .filter(entry ->
                    !Boolean.TRUE.equals(
                            entry.getReversed()
                    )
            )
            .sorted(
                    Comparator
                            .comparing(
                                    JournalEntry::getEntryDate,
                                    Comparator.nullsLast(
                                            Comparator.naturalOrder()
                                    )
                            )
            )
            .toList();
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

        if (account == null) {
            continue;
        }

        if (account.getId() == null) {
            continue;
        }

        balances.put(
                account.getId(),
                ZERO
        );
    }

    return balances;
}

// ============================================================
// PROCESS ENDING BALANCE
// ============================================================

private void processEndingBalanceEntry(
        JournalEntry entry,
        Map<Long, BigDecimal> balances,
        Long organizationId
) {

    if (entry == null) {
        return;
    }

    if (Boolean.TRUE.equals(
            entry.getReversed()
    )) {
        return;
    }

    validateJournalEntry(
            entry,
            organizationId
    );

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

        ChartOfAccount.NormalBalance normalBalance =
                account.getNormalBalance();

        if (normalBalance ==
                ChartOfAccount.NormalBalance.DEBIT) {

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
        Map<Long, BigDecimal> credits,
        Long organizationId
) {

    if (entry == null) {
        return;
    }

    if (Boolean.TRUE.equals(
            entry.getReversed()
    )) {
        return;
    }

    validateJournalEntry(
            entry,
            organizationId
    );

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
// PROCESS HISTORICAL INCOME / EXPENSE
// ============================================================

private void processIncomeExpenseEntry(
        JournalEntry entry,
        Map<Long, BigDecimal> historicalIncome,
        Map<Long, BigDecimal> historicalExpenses,
        Long organizationId
) {

    if (entry == null) {
        return;
    }

    if (Boolean.TRUE.equals(
            entry.getReversed()
    )) {
        return;
    }

    validateJournalEntry(
            entry,
            organizationId
    );

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

        if (account.getType() ==
                ChartOfAccount.AccountType.INCOME) {

            BigDecimal amount =
                    subtract(
                            credit,
                            debit
                    );

            historicalIncome.merge(
                    account.getId(),
                    amount,
                    this::add
            );

        } else if (
                account.getType() ==
                        ChartOfAccount.AccountType.EXPENSE
        ) {

            BigDecimal amount =
                    subtract(
                            debit,
                            credit
                    );

            historicalExpenses.merge(
                    account.getId(),
                    amount,
                    this::add
            );
        }
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
            "id",
            account.getId()
    );

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
// CONTRA ASSET
// ============================================================

private boolean isContraAsset(
        ChartOfAccount account
) {

    if (account == null) {
        return false;
    }

    /*
     * 1200 is retained for compatibility with the existing
     * chart of accounts.
     *
     * A credit-normal asset is also automatically treated as
     * a contra-asset.
     */
    if ("1200".equals(
            account.getCode()
    )) {
        return true;
    }

    return account.getNormalBalance() ==
            ChartOfAccount.NormalBalance.CREDIT;
}

// ============================================================
// NORMALIZED STATEMENT BALANCE
// ============================================================

private BigDecimal normalizedStatementBalance(
        ChartOfAccount account,
        BigDecimal balance
) {

    if (account == null) {
        return ZERO;
    }

    BigDecimal normalized =
            normalizeMoney(
                    balance
            );

    /*
     * For a normal balance account the ending balance is already
     * represented in its normal direction.
     *
     * A negative balance is retained rather than silently
     * converting it to a positive value because an abnormal
     * accounting balance is useful for detecting ledger issues.
     */
    return normalized;
}

// ============================================================
// BIGDECIMAL VALUE
// ============================================================

/**
 * Converts a financial value into BigDecimal.
 *
 * This accepts Number so the service remains compatible with
 * existing JournalLine implementations while all calculations
 * after conversion are performed using BigDecimal.
 *
 * IMPORTANT:
 *
 * Financial entities should ideally expose BigDecimal directly.
 * This compatibility method prevents the statement service from
 * being tightly coupled to legacy Double getters.
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

    if (value instanceof Long) {

        return normalizeMoney(
                BigDecimal.valueOf(
                        value.longValue()
                )
        );
    }

    if (value instanceof Integer) {

        return normalizeMoney(
                BigDecimal.valueOf(
                        value.intValue()
                )
        );
    }

    if (value instanceof Short) {

        return normalizeMoney(
                BigDecimal.valueOf(
                        value.shortValue()
                )
        );
    }

    if (value instanceof Byte) {

        return normalizeMoney(
                BigDecimal.valueOf(
                        value.byteValue()
                )
        );
    }

    if (value instanceof Double) {

        double doubleValue =
                value.doubleValue();

        if (Double.isNaN(doubleValue)
                || Double.isInfinite(doubleValue)) {

            throw new IllegalArgumentException(
                    "Financial value cannot be NaN or infinite."
            );
        }

        return normalizeMoney(
                BigDecimal.valueOf(
                        doubleValue
                )
        );
    }

    if (value instanceof Float) {

        float floatValue =
                value.floatValue();

        if (Float.isNaN(floatValue)
                || Float.isInfinite(floatValue)) {

            throw new IllegalArgumentException(
                    "Financial value cannot be NaN or infinite."
            );
        }

        return normalizeMoney(
                BigDecimal.valueOf(
                        floatValue
                )
        );
    }

    throw new IllegalArgumentException(
            "Unsupported financial number type: "
                    + value.getClass().getName()
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

    return value
            .abs()
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

    return value
            .abs()
            .compareTo(
                    BALANCE_TOLERANCE
            ) < 0;
}

// ============================================================
// JOURNAL ENTRY VALIDATION
// ============================================================

/**
 * Performs defensive validation of journal entries before
 * allowing them to influence regulatory financial reporting.
 *
 * This does not mutate the journal.
 */
private void validateJournalEntry(
        JournalEntry entry,
        Long organizationId
) {

    if (entry == null) {
        return;
    }

    if (organizationId == null) {
        throw new IllegalArgumentException(
                "Organization ID is required."
        );
    }

    if (entry.getOrganization() != null
            && entry.getOrganization().getId() != null
            && !organizationId.equals(
                    entry.getOrganization().getId()
            )) {

        throw new IllegalStateException(
                "Journal entry "
                        + entry.getId()
                        + " belongs to another organization."
        );
    }

    if (entry.getEntryDate() == null) {

        throw new IllegalStateException(
                "Journal entry "
                        + entry.getId()
                        + " has no entry date."
        );
    }

    if (entry.getLines() == null
            || entry.getLines().isEmpty()) {

        throw new IllegalStateException(
                "Journal entry "
                        + entry.getId()
                        + " contains no journal lines."
        );
    }

    BigDecimal debitTotal =
            ZERO;

    BigDecimal creditTotal =
            ZERO;

    for (JournalLine line :
            entry.getLines()) {

        if (line == null) {
            throw new IllegalStateException(
                    "Journal entry "
                            + entry.getId()
                            + " contains a null journal line."
            );
        }

        if (line.getAccount() == null
                || line.getAccount().getId() == null) {

            throw new IllegalStateException(
                    "Journal entry "
                            + entry.getId()
                            + " contains a journal line without an account."
            );
        }

        BigDecimal debit =
                value(
                        line.getDebit()
                );

        BigDecimal credit =
                value(
                        line.getCredit()
                );

        if (debit.compareTo(ZERO) < 0) {

            throw new IllegalStateException(
                    "Journal entry "
                            + entry.getId()
                            + " contains a negative debit."
            );
        }

        if (credit.compareTo(ZERO) < 0) {

            throw new IllegalStateException(
                    "Journal entry "
                            + entry.getId()
                            + " contains a negative credit."
            );
        }

        /*
         * A journal line should not normally contain both a debit
         * and credit amount.
         */
        if (debit.compareTo(ZERO) > 0
                && credit.compareTo(ZERO) > 0) {

            throw new IllegalStateException(
                    "Journal entry "
                            + entry.getId()
                            + " contains a line with both debit and credit."
            );
        }

        if (debit.compareTo(ZERO) == 0
                && credit.compareTo(ZERO) == 0) {

            throw new IllegalStateException(
                    "Journal entry "
                            + entry.getId()
                            + " contains a zero-value journal line."
            );
        }

        debitTotal =
                add(
                        debitTotal,
                        debit
                );

        creditTotal =
                add(
                        creditTotal,
                        credit
                );
    }

    BigDecimal difference =
            subtract(
                    debitTotal,
                    creditTotal
            );

    if (!isWithinTolerance(
            difference
    )) {

        throw new IllegalStateException(
                "Unbalanced journal entry "
                        + entry.getId()
                        + ": debit="
                        + debitTotal
                        + ", credit="
                        + creditTotal
                        + ", difference="
                        + difference
        );
    }
}

// ============================================================
// SAFE STRING
// ============================================================

private String safeString(
        String value
) {

    return value == null
            ? ""
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
