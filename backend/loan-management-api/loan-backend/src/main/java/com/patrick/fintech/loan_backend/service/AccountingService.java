
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.ChartOfAccountRepository;
import com.patrick.fintech.loan_backend.repository.JournalEntryRepository;
import com.patrick.fintech.loan_backend.repository.JournalLineRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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


    // ============================================================
    // DEFAULT CHART OF ACCOUNTS
    // ============================================================

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
             * Therefore it has a CREDIT normal balance.
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


    // ============================================================
    // SAFE MONEY CONVERSION
    // ============================================================

    /**
     * Converts any numeric monetary value to double safely.
     *
     * This allows the service to work with BigDecimal, Double,
     * Integer, Long, etc.
     *
     * The entities in this project increasingly use BigDecimal
     * for monetary values, so this helper prevents repeated
     * BigDecimal -> double compilation errors.
     */
    private double money(Number value) {

        if (value == null) {
            return 0.0;
        }

        return value.doubleValue();
    }


    /**
     * Normalizes very small floating-point values.
     */
    private double normalize(double value) {

        return Math.abs(value) < 0.0000001
                ? 0.0
                : value;
    }


    // ============================================================
    // DEFAULT CHART OF ACCOUNTS
    // ============================================================

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

                if (account != null
                        && account.getCode() != null) {

                    existingCodes.add(
                            account.getCode()
                    );
                }
            }
        }

        for (String[] account : DEFAULT_ACCOUNTS) {

            String code = account[0];

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


    // ============================================================
    // ACCOUNT LOOKUP
    // ============================================================

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


    // ============================================================
    // EQUITY ACCOUNT
    // ============================================================

    public ChartOfAccount getEquityAccount(
            Organization org
    ) {

        ensureChartOfAccounts(org);

        return account(
                org,
                "3000"
        );
    }


    // ============================================================
    // CREATE ACCOUNT
    // ============================================================

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
                        .code(code)
                        .name(name)
                        .type(type)
                        .normalBalance(normalBalance)
                        .active(true)
                        .build()
        );
    }


    // ============================================================
    // UPDATE ACCOUNT
    // ============================================================

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

            acc.setName(name.trim());
        }

        if (active != null) {

            acc.setActive(active);
        }

        return coaRepo.save(acc);
    }


    // ============================================================
    // JOURNAL POSTING
    // ============================================================

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


    // ============================================================
    // JOURNAL POSTING WITH BRANCH
    // ============================================================

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

        double totalDebit = 0.0;

        double totalCredit = 0.0;


        for (JournalLine line : lines) {

            if (line == null) {

                throw new IllegalArgumentException(
                        "Journal entry contains a null line"
                );
            }

            if (line.getAccount() == null
                    || line.getAccount().getId() == null) {

                throw new IllegalArgumentException(
                        "Every journal line must have an account"
                );
            }

            double debit =
                    money(line.getDebit());

            double credit =
                    money(line.getCredit());

            if (debit < 0.0 || credit < 0.0) {

                throw new IllegalArgumentException(
                        "Debit and credit amounts cannot be negative"
                );
            }

            if (debit > 0.0 && credit > 0.0) {

                throw new IllegalArgumentException(
                        "A journal line cannot contain both debit and credit"
                );
            }

            if (debit == 0.0 && credit == 0.0) {

                throw new IllegalArgumentException(
                        "A journal line must contain a debit or credit amount"
                );
            }

            /*
             * Ensure amounts are never stored as null.
             */
            line.setDebit(debit);

            line.setCredit(credit);

            totalDebit += debit;

            totalCredit += credit;
        }


        if (
                Math.abs(
                        totalDebit -
                        totalCredit
                ) > 0.01
        ) {

            throw new IllegalStateException(
                    String.format(
                            "Journal entry does not balance: " +
                            "debits %.2f != credits %.2f (%s)",
                            totalDebit,
                            totalCredit,
                            description
                    )
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


        for (JournalLine line : lines) {

            line.setJournalEntry(entry);

            lineRepo.save(line);
        }


        return entry;
    }


    // ============================================================
    // LOAN DISBURSEMENT
    // ============================================================

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


            double amount =
                    money(loan.getAmount());


            if (amount <= 0.0) {

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


            // ----------------------------------------------------
            // DR Loans Receivable
            // ----------------------------------------------------

            lines.add(
                    JournalLine.builder()
                            .account(
                                    account(org, "1100")
                            )
                            .debit(amount)
                            .credit(0.0)
                            .description(
                                    "Loans Receivable — " +
                                    reference
                            )
                            .build()
            );


            // ----------------------------------------------------
            // CR Cash
            // ----------------------------------------------------

            lines.add(
                    JournalLine.builder()
                            .account(
                                    account(org, "1000")
                            )
                            .debit(0.0)
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
                    String.valueOf(loan.getId()),
                    reference,
                    "Disbursement of loan " + reference,
                    lines
            );


            // ----------------------------------------------------
            // PROCESSING FEE
            // ----------------------------------------------------

            double fee =
                    money(loan.getProcessingFee());


            if (fee > 0.0) {

                post(
                        org,
                        loan.getBranch(),
                        "PROCESSING_FEE",
                        String.valueOf(loan.getId()),
                        reference,
                        "Processing fee collected on " +
                        reference,

                        List.of(

                                // DR Cash
                                JournalLine.builder()
                                        .account(
                                                account(org, "1000")
                                        )
                                        .debit(fee)
                                        .credit(0.0)
                                        .description(
                                                "Processing fee — " +
                                                reference
                                        )
                                        .build(),

                                // CR Fee Income
                                JournalLine.builder()
                                        .account(
                                                account(org, "4100")
                                        )
                                        .debit(0.0)
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


    // ============================================================
    // INTEREST ACCRUAL
    // ============================================================

    @Transactional
    public void postInterestAccrual(
            Loan loan,
            double dailyInterestAmount
    ) {

        if (loan == null) {
            return;
        }

        if (dailyInterestAmount <= 0.0) {
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
                String.valueOf(loan.getId()),
                reference,
                "Daily interest accrual for " +
                reference +
                " (" +
                LocalDate.now() +
                ")",

                List.of(

                        // DR Interest Receivable
                        JournalLine.builder()
                                .account(
                                        account(org, "1150")
                                )
                                .debit(
                                        dailyInterestAmount
                                )
                                .credit(0.0)
                                .description(
                                        "Interest accrued — " +
                                        reference
                                )
                                .build(),

                        // CR Interest Income
                        JournalLine.builder()
                                .account(
                                        account(org, "4000")
                                )
                                .debit(0.0)
                                .credit(
                                        dailyInterestAmount
                                )
                                .description(
                                        "Interest income accrued — " +
                                        reference
                                )
                                .build()
                )
        );
    }


    // ============================================================
    // PAYMENT RECEIVED
    // ============================================================

    @Transactional
    public JournalEntry postPaymentReceived(
            Payment payment,
            Double paymentAmount,
            double interestAmount,
            double principalAmount,
            double penaltyAmount
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


        double total =
                paymentAmount != null
                        ? paymentAmount
                        : 0.0;


        if (total <= 0.0) {

            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }


        double interest =
                Math.max(
                        0.0,
                        interestAmount
                );


        double principal =
                Math.max(
                        0.0,
                        principalAmount
                );


        double penalty =
                Math.max(
                        0.0,
                        penaltyAmount
                );


        double allocated =
                interest +
                principal +
                penalty;


        double difference =
                total -
                allocated;


        if (Math.abs(difference) > 0.01) {

            if (difference > 0.0) {

                /*
                 * Any unallocated amount reduces principal.
                 */
                principal += difference;

            } else {

                throw new IllegalStateException(
                        String.format(
                                "Payment allocation exceeds payment amount: " +
                                "payment %.2f, interest %.2f, principal %.2f, penalty %.2f",
                                total,
                                interest,
                                principal,
                                penalty
                        )
                );
            }
        }


        List<JournalLine> lines =
                new ArrayList<>();


        // --------------------------------------------------------
        // DR CASH
        // --------------------------------------------------------

        lines.add(
                JournalLine.builder()
                        .account(
                                account(org, "1000")
                        )
                        .debit(total)
                        .credit(0.0)
                        .description(
                                "Payment received — " +
                                loan.getReferenceNumber()
                        )
                        .build()
        );


        // --------------------------------------------------------
        // CR PRINCIPAL
        // --------------------------------------------------------

        if (principal > 0.009) {

            lines.add(
                    JournalLine.builder()
                            .account(
                                    account(org, "1100")
                            )
                            .debit(0.0)
                            .credit(principal)
                            .description(
                                    "Principal repayment — " +
                                    loan.getReferenceNumber()
                            )
                            .build()
            );
        }


        // --------------------------------------------------------
        // INTEREST
        // --------------------------------------------------------

        if (interest > 0.009) {

            double accrued =
                    accruedInterestReceivable(
                            org,
                            loan.getReferenceNumber()
                    );


            double clearReceivable =
                    Math.min(
                            interest,
                            Math.max(
                                    accrued,
                                    0.0
                            )
                    );


            double directIncome =
                    interest -
                    clearReceivable;


            // CR Interest Receivable
            if (clearReceivable > 0.009) {

                lines.add(
                        JournalLine.builder()
                                .account(
                                        account(org, "1150")
                                )
                                .debit(0.0)
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


            // CR Interest Income
            if (directIncome > 0.009) {

                lines.add(
                        JournalLine.builder()
                                .account(
                                        account(org, "4000")
                                )
                                .debit(0.0)
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


        // --------------------------------------------------------
        // PENALTY / FEE
        // --------------------------------------------------------

        if (penalty > 0.009) {

            lines.add(
                    JournalLine.builder()
                            .account(
                                    account(org, "4100")
                            )
                            .debit(0.0)
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
                String.valueOf(payment.getId()),
                reference,
                "Payment received on loan " +
                loan.getReferenceNumber(),
                lines
        );
    }


    // ============================================================
    // PAYMENT OVERLOAD
    // ============================================================

    @Transactional
    public JournalEntry postPaymentReceived(
            Payment payment
    ) {

        if (payment == null) {

            throw new IllegalArgumentException(
                    "Payment is required"
            );
        }


        double amount =
                payment.getAmountPaid() != null
                        ? payment.getAmountPaid().doubleValue()
                        : payment.getAmount() != null
                                ? payment.getAmount().doubleValue()
                                : 0.0;


        double interest =
                payment.getInterestComponent() != null
                        ? payment.getInterestComponent().doubleValue()
                        : 0.0;


        double principal =
                payment.getPrincipalComponent() != null
                        ? payment.getPrincipalComponent().doubleValue()
                        : 0.0;


        double penalty =
                payment.getPenalty() != null
                        ? payment.getPenalty().doubleValue()
                        : 0.0;


        return postPaymentReceived(
                payment,
                amount,
                interest,
                principal,
                penalty
        );
    }


    // ============================================================
    // ACCRUED INTEREST
    // ============================================================

    private double accruedInterestReceivable(
            Organization org,
            String loanReference
    ) {

        if (org == null || org.getId() == null) {
            return 0.0;
        }

        ChartOfAccount receivable =
                coaRepo
                        .findByOrganization_IdAndCode(
                                org.getId(),
                                "1150"
                        )
                        .orElse(null);


        if (receivable == null) {
            return 0.0;
        }


        List<JournalLine> lines =
                lineRepo.findAccrualLinesForLoan(
                        receivable.getId(),
                        loanReference
                );


        if (lines == null || lines.isEmpty()) {
            return 0.0;
        }


        double balance = 0.0;


        for (JournalLine line : lines) {

            if (line == null) {
                continue;
            }

            JournalEntry entry =
                    line.getJournalEntry();

            /*
             * Reversed entries must not remain part of the
             * accrued-interest calculation.
             */
            if (
                    entry != null
                    && Boolean.TRUE.equals(
                            entry.getReversed()
                    )
            ) {
                continue;
            }


            balance +=
                    money(line.getDebit())
                    -
                    money(line.getCredit());
        }


        return Math.max(
                balance,
                0.0
        );
    }


    // ============================================================
    // WRITE OFF
    // ============================================================

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


        double outstanding =
                money(
                        loan.getOutstandingBalance()
                );


        if (outstanding <= 0.0) {
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
                String.valueOf(loan.getId()),
                reference,
                "Write-off of loan " +
                reference,

                List.of(

                        // DR Loan Loss Expense
                        JournalLine.builder()
                                .account(
                                        account(org, "5000")
                                )
                                .debit(outstanding)
                                .credit(0.0)
                                .description(
                                        "Loan loss expense — " +
                                        reference
                                )
                                .build(),

                        // CR Loans Receivable
                        JournalLine.builder()
                                .account(
                                        account(org, "1100")
                                )
                                .debit(0.0)
                                .credit(outstanding)
                                .description(
                                        "Write off receivable — " +
                                        reference
                                )
                                .build()
                )
        );
    }


    // ============================================================
    // EXPENSE
    // ============================================================

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


        /*
         * Important:
         *
         * expense.getAmount() may be BigDecimal.
         */
        double amount =
                money(expense.getAmount());


        if (amount <= 0.0) {

            throw new IllegalArgumentException(
                    "Expense amount must be greater than zero"
            );
        }


        String reference =
                "EXP-" +
                expense.getId();


        String description =
                "Expense — " +
                expense.getCategory().getLabel();


        if (
                expense.getDescription() != null
                && !expense.getDescription().isBlank()
        ) {

            description +=
                    ": " +
                    expense.getDescription();
        }


        return post(
                org,
                expense.getBranch(),
                "EXPENSE",
                String.valueOf(expense.getId()),
                reference,
                description,

                List.of(

                        // DR Expense
                        JournalLine.builder()
                                .account(
                                        expenseAccount
                                )
                                .debit(amount)
                                .credit(0.0)
                                .description(
                                        expense.getCategory().getLabel() +
                                        " — " +
                                        reference
                                )
                                .build(),

                        // CR Cash / Bank / Payable
                        JournalLine.builder()
                                .account(
                                        paymentGlAccount
                                )
                                .debit(0.0)
                                .credit(amount)
                                .description(
                                        "Paid from " +
                                        expense.getPaymentAccount().getName() +
                                        " — " +
                                        reference
                                )
                                .build()
                )
        );
    }


    // ============================================================
    // REVERSE EXPENSE
    // ============================================================

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


    // ============================================================
    // REVERSE JOURNAL ENTRY
    // ============================================================

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
                original.getLines()
                        .stream()
                        .filter(
                                l -> l != null
                        )
                        .map(
                                l ->
                                        JournalLine.builder()
                                                .account(
                                                        l.getAccount()
                                                )
                                                .debit(
                                                        money(
                                                                l.getCredit()
                                                        )
                                                )
                                                .credit(
                                                        money(
                                                                l.getDebit()
                                                        )
                                                )
                                                .description(
                                                        "Reversal of #" +
                                                        entryId +
                                                        " — " +
                                                        (
                                                                l.getDescription() != null
                                                                        ? l.getDescription()
                                                                        : ""
                                                        )
                                                )
                                                .build()
                        )
                        .toList();


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
                                "Reversal of entry #" +
                                entryId +
                                (
                                        reason != null
                                        && !reason.isBlank()
                                                ? ": " + reason
                                                : ""
                                ) +
                                " — " +
                                (
                                        original.getDescription() != null
                                                ? original.getDescription()
                                                : ""
                                )
                        )
                        .createdBy(
                                reversedBy != null
                                        && !reversedBy.isBlank()
                                                ? reversedBy
                                                : "SYSTEM"
                        )
                        .reversed(false)
                        .build();


        reversal =
                journalRepo.save(reversal);


        for (JournalLine line :
                reversedLines) {

            line.setJournalEntry(
                    reversal
            );

            lineRepo.save(line);
        }


        /*
         * Mark the original entry reversed.
         *
         * The reversal entry itself remains active and therefore
         * represents the accounting correction.
         */
        original.setReversed(true);

        journalRepo.save(original);


        return reversal;
    }


    // ============================================================
    // LEDGER
    // ============================================================

    @Transactional(readOnly = true)
    public Map<String, Object> getLedger(
            Long orgId,
            Long accountId
    ) {

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


        double running = 0.0;


        if (lines != null) {

            for (JournalLine line :
                    lines) {

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


                double debit =
                        money(line.getDebit());


                double credit =
                        money(line.getCredit());


                running +=
                        debitNormal
                                ? debit - credit
                                : credit - debit;


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


    // ============================================================
    // TRIAL BALANCE
    // ============================================================

    @Transactional(readOnly = true)
    public Map<String, Object> getTrialBalance(
            Long orgId
    ) {

        List<ChartOfAccount> accounts =
                coaRepo
                        .findByOrganization_IdOrderByCodeAsc(
                                orgId
                        );


        List<Map<String, Object>> rows =
                new ArrayList<>();


        double totalDebit = 0.0;

        double totalCredit = 0.0;


        if (accounts != null) {

            for (ChartOfAccount acc :
                    accounts) {

                if (acc == null) {
                    continue;
                }


                List<JournalLine> lines =
                        lineRepo.findByAccount_Id(
                                acc.getId()
                        );


                double debit = 0.0;

                double credit = 0.0;


                if (lines != null) {

                    for (JournalLine line :
                            lines) {

                        if (line == null) {
                            continue;
                        }


                        JournalEntry entry =
                                line.getJournalEntry();


                        /*
                         * Reversed original entries are excluded.
                         * Their reversal entries remain included.
                         */
                        if (
                                entry != null
                                && Boolean.TRUE.equals(
                                        entry.getReversed()
                                )
                        ) {
                            continue;
                        }


                        debit +=
                                money(
                                        line.getDebit()
                                );

                        credit +=
                                money(
                                        line.getCredit()
                                );
                    }
                }


                double net =
                        debit -
                        credit;


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
                        net > 0.0
                                ? net
                                : 0.0
                );

                row.put(
                        "credit",
                        net < 0.0
                                ? -net
                                : 0.0
                );


                rows.add(row);


                totalDebit +=
                        net > 0.0
                                ? net
                                : 0.0;


                totalCredit +=
                        net < 0.0
                                ? -net
                                : 0.0;
            }
        }


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
                Math.abs(
                        totalDebit -
                        totalCredit
                ) < 0.01
        );


        return result;
    }


    // ============================================================
    // BALANCE SHEET
    // ============================================================

    @Transactional(readOnly = true)
    public Map<String, Object> getBalanceSheet(
            Long orgId
    ) {

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


        double totalAssets = 0.0;

        double totalLiabilities = 0.0;

        double totalEquity = 0.0;

        double totalIncome = 0.0;

        double totalExpense = 0.0;


        if (accounts != null) {

            for (ChartOfAccount acc :
                    accounts) {

                if (acc == null) {
                    continue;
                }


                double balance =
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

                    case ASSET -> {

                        /*
                         * 1200 Loan Loss Reserve is a
                         * credit-normal contra-asset.
                         *
                         * netBalance() already returns the
                         * correct signed balance.
                         */
                        totalAssets +=
                                balance;
                    }

                    case LIABILITY ->
                            totalLiabilities += balance;

                    case EQUITY ->
                            totalEquity += balance;

                    case INCOME ->
                            totalIncome += balance;

                    case EXPENSE ->
                            totalExpense += balance;
                }
            }
        }


        double netIncome =
                totalIncome -
                totalExpense;


        /*
         * Current-period income is included in equity.
         */
        totalEquity += netIncome;


        double balanceDifference =
                totalAssets -
                (
                        totalLiabilities +
                        totalEquity
                );


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
                totalLiabilities +
                totalEquity
        );

        result.put(
                "balanceDifference",
                balanceDifference
        );

        result.put(
                "balanced",
                Math.abs(
                        balanceDifference
                ) < 0.01
        );


        return result;
    }


    // ============================================================
    // PROFIT AND LOSS
    // ============================================================

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


        List<JournalEntry> entries =
                journalRepo
                        .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
                                orgId,
                                from,
                                to
                        );


        Map<String, Double> perAccount =
                new LinkedHashMap<>();


        Map<String, String> names =
                new LinkedHashMap<>();


        Map<
                String,
                ChartOfAccount.AccountType
        > types =
                new LinkedHashMap<>();


        if (entries != null) {

            for (JournalEntry entry :
                    entries) {

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


                    double debit =
                            money(line.getDebit());


                    double credit =
                            money(line.getCredit());


                    double net =
                            acc.getType()
                                    == ChartOfAccount.AccountType.INCOME
                                    ? credit - debit
                                    : debit - credit;


                    perAccount.merge(
                            acc.getCode(),
                            net,
                            Double::sum
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


        double totalIncome = 0.0;

        double totalExpense = 0.0;


        for (
                Map.Entry<String, Double> entry :
                perAccount.entrySet()
        ) {

            double amount =
                    normalize(
                            entry.getValue()
                    );


            /*
             * Ignore zero-value accounts in the report.
             */
            if (Math.abs(amount) < 0.005) {
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
                    names.get(entry.getKey())
            );

            row.put(
                    "amount",
                    amount
            );


            if (
                    types.get(entry.getKey())
                            == ChartOfAccount.AccountType.INCOME
            ) {

                income.add(row);

                totalIncome += amount;

            } else {

                expense.add(row);

                totalExpense += amount;
            }
        }


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
                totalIncome
        );

        result.put(
                "totalExpense",
                totalExpense
        );

        result.put(
                "netIncome",
                totalIncome -
                totalExpense
        );


        return result;
    }


    // ============================================================
    // CASH FLOW
    // ============================================================

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


        List<JournalEntry> entries =
                journalRepo
                        .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
                                orgId,
                                from,
                                to
                        );


        double lending = 0.0;

        double collections = 0.0;

        double feesAndPenalties = 0.0;

        double other = 0.0;


        if (entries != null) {

            for (JournalEntry entry :
                    entries) {

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
                                    line.getAccount().getCode()
                            )
                    ) {
                        continue;
                    }


                    double debit =
                            money(line.getDebit());


                    double credit =
                            money(line.getCredit());


                    /*
                     * For cash:
                     *
                     * Debit  = cash inflow
                     * Credit = cash outflow
                     */
                    double net =
                            debit -
                            credit;


                    String source =
                            entry.getSourceType() != null
                                    ? entry.getSourceType()
                                    : "";


                    switch (source) {

                        case "LOAN_DISBURSEMENT" ->
                                lending += net;

                        case "PAYMENT_RECEIVED" ->
                                collections += net;

                        case "PROCESSING_FEE" ->
                                feesAndPenalties += net;

                        default ->
                                other += net;
                    }
                }
            }
        }


        double netChange =
                lending +
                collections +
                feesAndPenalties +
                other;


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
                lending
        );

        result.put(
                "cashFromCollections",
                collections
        );

        result.put(
                "cashFromFees",
                feesAndPenalties
        );

        result.put(
                "otherCashMovement",
                other
        );

        result.put(
                "netChangeInCash",
                netChange
        );


        return result;
    }


    // ============================================================
    // BRANCH SUMMARY
    // ============================================================

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


        List<JournalEntry> entries =
                journalRepo
                        .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
                                orgId,
                                from,
                                to
                        );


        Map<String, double[]> byBranch =
                new LinkedHashMap<>();


        if (entries != null) {

            for (JournalEntry entry :
                    entries) {

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
                                ? entry.getBranch().getName()
                                : "Unassigned";


                double[] totals =
                        byBranch.computeIfAbsent(
                                branchName,
                                k -> new double[3]
                        );


                double debitTotal =
                        entry.getLines() == null
                                ? 0.0
                                : entry.getLines()
                                        .stream()
                                        .filter(
                                                l ->
                                                        l != null
                                        )
                                        .mapToDouble(
                                                l ->
                                                        money(
                                                                l.getDebit()
                                                        )
                                        )
                                        .sum();


                String source =
                        entry.getSourceType() != null
                                ? entry.getSourceType()
                                : "";


                switch (source) {

                    case "LOAN_DISBURSEMENT" ->

                            totals[0] +=
                                    debitTotal;

                    case "PAYMENT_RECEIVED" ->

                            totals[1] +=
                                    debitTotal;

                    case "PROCESSING_FEE" ->

                            totals[2] +=
                                    debitTotal;

                    default -> {
                    }
                }
            }
        }


        List<Map<String, Object>> rows =
                new ArrayList<>();


        for (
                Map.Entry<String, double[]> entry :
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
                    entry.getValue()[0]
            );

            row.put(
                    "collected",
                    entry.getValue()[1]
            );

            row.put(
                    "feeIncome",
                    entry.getValue()[2]
            );


            rows.add(row);
        }


        return rows;
    }


    // ============================================================
    // NET ACCOUNT BALANCE
    // ============================================================

    private double netBalance(
            ChartOfAccount acc
    ) {

        if (
                acc == null
                || acc.getId() == null
        ) {
            return 0.0;
        }


        List<JournalLine> lines =
                lineRepo.findByAccount_Id(
                        acc.getId()
                );


        if (lines == null || lines.isEmpty()) {
            return 0.0;
        }


        double debit = 0.0;

        double credit = 0.0;


        for (JournalLine line :
                lines) {

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


            debit +=
                    money(line.getDebit());


            credit +=
                    money(line.getCredit());
        }


        if (
                acc.getNormalBalance()
                        == ChartOfAccount.NormalBalance.DEBIT
        ) {

            return normalize(
                    debit -
                    credit
            );
        }


        return normalize(
                credit -
                debit
        );
    }


    // ============================================================
    // DATE VALIDATION
    // ============================================================

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

