
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.ChartOfAccountRepository;
import com.patrick.fintech.loan_backend.repository.JournalEntryRepository;
import com.patrick.fintech.loan_backend.repository.JournalLineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingService {

    private final ChartOfAccountRepository coaRepo;
    private final JournalEntryRepository journalRepo;
    private final JournalLineRepository lineRepo;

    // -------------------------------------------------------------------------
    // DEFAULT CHART OF ACCOUNTS
    // -------------------------------------------------------------------------

    private static final String[][] DEFAULT_ACCOUNTS = {

        // code, name, type, normalBalance

        {"1000", "Cash and Bank",              "ASSET",     "DEBIT"},
        {"1100", "Loans Receivable",           "ASSET",     "DEBIT"},
        {"1150", "Interest Receivable",        "ASSET",     "DEBIT"},
        {"1200", "Loan Loss Reserve",          "ASSET",     "CREDIT"},

        {"2000", "Customer Deposits Payable",  "LIABILITY", "CREDIT"},

        {"3000", "Owner's Equity",             "EQUITY",    "CREDIT"},

        {"4000", "Interest Income",            "INCOME",    "CREDIT"},
        {"4100", "Fee and Penalty Income",     "INCOME",    "CREDIT"},

        {"5000", "Loan Loss Expense",          "EXPENSE",   "DEBIT"},
        {"5100", "Operating Expenses",         "EXPENSE",   "DEBIT"},

        // Granular operating expenses
        {"5200", "Salaries and Wages",         "EXPENSE",   "DEBIT"},
        {"5201", "Rent",                       "EXPENSE",   "DEBIT"},
        {"5202", "Utilities",                  "EXPENSE",   "DEBIT"},
        {"5203", "Internet",                  "EXPENSE",   "DEBIT"},
        {"5204", "Transport",                 "EXPENSE",   "DEBIT"},
        {"5205", "Fuel",                       "EXPENSE",   "DEBIT"},
        {"5206", "Office Supplies",            "EXPENSE",   "DEBIT"},
        {"5207", "Bank Charges",               "EXPENSE",   "DEBIT"},
        {"5208", "Insurance",                  "EXPENSE",   "DEBIT"},
        {"5209", "Marketing",                  "EXPENSE",   "DEBIT"},
        {"5210", "Legal Fees",                 "EXPENSE",   "DEBIT"},
        {"5211", "Audit Fees",                 "EXPENSE",   "DEBIT"},
        {"5212", "Depreciation",               "EXPENSE",   "DEBIT"},
        {"5213", "Loan Recovery Expenses",     "EXPENSE",   "DEBIT"},
        {"5214", "IT Expenses",                "EXPENSE",   "DEBIT"},
        {"5215", "Other Operating Expenses",   "EXPENSE",   "DEBIT"}
    };

    // -------------------------------------------------------------------------
    // CHART OF ACCOUNTS
    // -------------------------------------------------------------------------

    /**
     * Ensures every organization has the standard chart of accounts.
     *
     * Existing accounts are never deleted or overwritten.
     * Newly introduced default accounts are automatically backfilled.
     */
    @Transactional
    public void ensureChartOfAccounts(Organization org) {

        List<ChartOfAccount> existing =
            coaRepo.findByOrganization_IdOrderByCodeAsc(org.getId());

        Set<String> existingCodes = new HashSet<>();

        for (ChartOfAccount account : existing) {
            existingCodes.add(account.getCode());
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
                        ChartOfAccount.AccountType.valueOf(account[2])
                    )
                    .normalBalance(
                        ChartOfAccount.NormalBalance.valueOf(account[3])
                    )
                    .active(true)
                    .build()
            );
        }

        if (existing.isEmpty()) {
            log.info(
                "Seeded default chart of accounts for organization {}",
                org.getId()
            );
        }
    }

    /**
     * Gets an account by organization and account code.
     */
    private ChartOfAccount account(
        Organization org,
        String code
    ) {

        return coaRepo
            .findByOrganization_IdAndCode(org.getId(), code)
            .orElseThrow(
                () -> new IllegalStateException(
                    "Chart of accounts not set up for this organization " +
                    "(missing account " + code + ")"
                )
            );
    }

    /**
     * Returns the default equity account.
     */
    public ChartOfAccount getEquityAccount(Organization org) {

        ensureChartOfAccounts(org);

        return account(org, "3000");
    }

    /**
     * Creates an organization-specific account.
     */
    @Transactional
    public ChartOfAccount createAccount(
        Organization org,
        String code,
        String name,
        ChartOfAccount.AccountType type,
        ChartOfAccount.NormalBalance normalBalance
    ) {

        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Account code is required");
        }

        if (coaRepo.existsByOrganization_IdAndCode(
            org.getId(),
            code
        )) {
            throw new IllegalArgumentException(
                "Account code " + code + " already exists"
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

    /**
     * Updates account name/active status.
     *
     * Code and account type intentionally remain immutable once used.
     */
    @Transactional
    public ChartOfAccount updateAccount(
        Long orgId,
        Long accountId,
        String name,
        Boolean active
    ) {

        ChartOfAccount acc =
            coaRepo.findByIdAndOrganization_Id(
                accountId,
                orgId
            ).orElseThrow(
                () -> new IllegalArgumentException(
                    "Account not found: " + accountId
                )
            );

        if (name != null && !name.isBlank()) {
            acc.setName(name);
        }

        if (active != null) {
            acc.setActive(active);
        }

        return coaRepo.save(acc);
    }

    // -------------------------------------------------------------------------
    // JOURNAL POSTING
    // -------------------------------------------------------------------------

    /**
     * Posts a journal entry without branch.
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

    /**
     * Posts a journal entry with optional branch.
     *
     * Every journal entry must balance:
     *
     *      Total Debits = Total Credits
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

        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException(
                "Journal entry must contain at least one line"
            );
        }

        double totalDebit =
            lines.stream()
                .mapToDouble(
                    l -> l.getDebit() != null
                        ? l.getDebit()
                        : 0.0
                )
                .sum();

        double totalCredit =
            lines.stream()
                .mapToDouble(
                    l -> l.getCredit() != null
                        ? l.getCredit()
                        : 0.0
                )
                .sum();

        if (Math.abs(totalDebit - totalCredit) > 0.01) {

            throw new IllegalStateException(
                String.format(
                    "Journal entry does not balance: debits %.2f != credits %.2f (%s)",
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

        entry = journalRepo.save(entry);

        for (JournalLine line : lines) {

            line.setJournalEntry(entry);

            if (line.getDebit() == null) {
                line.setDebit(0.0);
            }

            if (line.getCredit() == null) {
                line.setCredit(0.0);
            }

            lineRepo.save(line);
        }

        return entry;
    }

    // -------------------------------------------------------------------------
    // LOAN DISBURSEMENT
    // -------------------------------------------------------------------------

    /**
     * Loan disbursement:
     *
     * DR Loans Receivable
     * CR Cash
     *
     * If there is a processing fee collected immediately:
     *
     * DR Cash
     * CR Fee Income
     */
    @Transactional
    public void postDisbursement(Loan loan) {

        try {

            Organization org = loan.getOrganization();

            ensureChartOfAccounts(org);

            List<JournalLine> lines =
                new ArrayList<>(
                    List.of(

                        JournalLine.builder()
                            .account(account(org, "1100"))
                            .debit(loan.getAmount())
                            .credit(0.0)
                            .description(
                                "Loans Receivable — " +
                                loan.getReferenceNumber()
                            )
                            .build(),

                        JournalLine.builder()
                            .account(account(org, "1000"))
                            .debit(0.0)
                            .credit(loan.getAmount())
                            .description(
                                "Cash disbursed — " +
                                loan.getReferenceNumber()
                            )
                            .build()
                    )
                );

            post(
                org,
                loan.getBranch(),
                "LOAN_DISBURSEMENT",
                String.valueOf(loan.getId()),
                loan.getReferenceNumber(),
                "Disbursement of loan " +
                    loan.getReferenceNumber(),
                lines
            );

            double fee =
                loan.getProcessingFee() != null
                    ? loan.getProcessingFee()
                    : 0.0;

            if (fee > 0) {

                post(
                    org,
                    loan.getBranch(),
                    "PROCESSING_FEE",
                    String.valueOf(loan.getId()),
                    loan.getReferenceNumber(),
                    "Processing fee collected on " +
                        loan.getReferenceNumber(),

                    List.of(

                        JournalLine.builder()
                            .account(account(org, "1000"))
                            .debit(fee)
                            .credit(0.0)
                            .description(
                                "Processing fee — " +
                                loan.getReferenceNumber()
                            )
                            .build(),

                        JournalLine.builder()
                            .account(account(org, "4100"))
                            .debit(0.0)
                            .credit(fee)
                            .description(
                                "Processing fee income — " +
                                loan.getReferenceNumber()
                            )
                            .build()
                    )
                );
            }

        } catch (Exception e) {

            log.warn(
                "Could not post GL entry for disbursement of loan {}: {}",
                loan.getId(),
                e.getMessage()
            );
        }
    }

    
    @Transactional
    public void postInterestAccrual(
        Loan loan,
        double dailyInterestAmount
    ) {

        if (dailyInterestAmount <= 0) {
            return;
        }

        try {

            Organization org = loan.getOrganization();

            ensureChartOfAccounts(org);

            post(
                org,
                loan.getBranch(),
                "INTEREST_ACCRUAL",
                String.valueOf(loan.getId()),
                loan.getReferenceNumber(),
                "Daily interest accrual for " +
                    loan.getReferenceNumber() +
                    " (" + LocalDate.now() + ")",

                List.of(

                    JournalLine.builder()
                        .account(account(org, "1150"))
                        .debit(dailyInterestAmount)
                        .credit(0.0)
                        .description(
                            "Interest accrued — " +
                            loan.getReferenceNumber()
                        )
                        .build(),

                    JournalLine.builder()
                        .account(account(org, "4000"))
                        .debit(0.0)
                        .credit(dailyInterestAmount)
                        .description(
                            "Interest income accrued — " +
                            loan.getReferenceNumber()
                        )
                        .build()
                )
            );

        } catch (Exception e) {

            log.warn(
                "Could not post interest accrual for loan {}: {}",
                loan.getId(),
                e.getMessage()
            );
        }
    }

    
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

        Loan loan = payment.getLoan();

        if (loan == null) {
            throw new IllegalArgumentException(
                "Payment has no loan"
            );
        }

        Organization org = loan.getOrganization();

        ensureChartOfAccounts(org);

        double total =
            paymentAmount != null
                ? paymentAmount
                : 0.0;

        double interest =
            Math.max(0.0, interestAmount);

        double principal =
            Math.max(0.0, principalAmount);

        double penalty =
            Math.max(0.0, penaltyAmount);

        

        double allocated =
            interest +
            principal +
            penalty;

        
        double difference = total - allocated;

        if (Math.abs(difference) > 0.01) {

            
            if (difference > 0) {
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

        /*
         * Nothing should be posted for a zero/negative payment.
         */
        if (total <= 0) {
            throw new IllegalArgumentException(
                "Payment amount must be greater than zero"
            );
        }

        List<JournalLine> lines = new ArrayList<>();

        

        lines.add(
            JournalLine.builder()
                .account(account(org, "1000"))
                .debit(total)
                .credit(0.0)
                .description(
                    "Payment received — " +
                    loan.getReferenceNumber()
                )
                .build()
        );

        

        if (principal > 0.009) {

            lines.add(
                JournalLine.builder()
                    .account(account(org, "1100"))
                    .debit(0.0)
                    .credit(principal)
                    .description(
                        "Principal repayment — " +
                        loan.getReferenceNumber()
                    )
                    .build()
            );
        }

       

        if (interest > 0.009) {

           
            double accrued =
                accruedInterestReceivable(
                    org,
                    loan.getReferenceNumber()
                );

            double clearReceivable =
                Math.min(
                    interest,
                    Math.max(accrued, 0.0)
                );

            double directIncome =
                interest -
                clearReceivable;

            if (clearReceivable > 0.009) {

                lines.add(
                    JournalLine.builder()
                        .account(account(org, "1150"))
                        .debit(0.0)
                        .credit(clearReceivable)
                        .description(
                            "Clears accrued interest — " +
                            loan.getReferenceNumber()
                        )
                        .build()
                );
            }

            if (directIncome > 0.009) {

                lines.add(
                    JournalLine.builder()
                        .account(account(org, "4000"))
                        .debit(0.0)
                        .credit(directIncome)
                        .description(
                            "Interest income — " +
                            loan.getReferenceNumber()
                        )
                        .build()
                );
            }
        }

       

        if (penalty > 0.009) {

            lines.add(
                JournalLine.builder()
                    .account(account(org, "4100"))
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

    
    @Transactional
    public JournalEntry postPaymentReceived(Payment payment) {

        double amount =
            payment.getAmountPaid() != null
                ? payment.getAmountPaid()
                : payment.getAmount() != null
                    ? payment.getAmount()
                    : 0.0;

        double interest =
            payment.getInterestComponent() != null
                ? payment.getInterestComponent()
                : 0.0;

        double principal =
            payment.getPrincipalComponent() != null
                ? payment.getPrincipalComponent()
                : 0.0;

        double penalty =
            payment.getPenalty() != null
                ? payment.getPenalty()
                : 0.0;

        return postPaymentReceived(
            payment,
            amount,
            interest,
            principal,
            penalty
        );
    }

   
    private double accruedInterestReceivable(
        Organization org,
        String loanReference
    ) {

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

        return lineRepo
            .findAccrualLinesForLoan(
                receivable.getId(),
                loanReference
            )
            .stream()
            .mapToDouble(
                l ->
                    (l.getDebit() != null
                        ? l.getDebit()
                        : 0.0)
                    -
                    (l.getCredit() != null
                        ? l.getCredit()
                        : 0.0)
            )
            .sum();
    }

    
    @Transactional
    public void postWriteOff(Loan loan) {

        try {

            Organization org = loan.getOrganization();

            ensureChartOfAccounts(org);

            double outstanding =
                loan.getOutstandingBalance() != null
                    ? loan.getOutstandingBalance()
                    : 0.0;

            if (outstanding <= 0) {
                return;
            }

            post(
                org,
                loan.getBranch(),
                "WRITE_OFF",
                String.valueOf(loan.getId()),
                loan.getReferenceNumber(),
                "Write-off of loan " +
                    loan.getReferenceNumber(),

                List.of(

                    JournalLine.builder()
                        .account(account(org, "5000"))
                        .debit(outstanding)
                        .credit(0.0)
                        .description(
                            "Loan loss expense — " +
                            loan.getReferenceNumber()
                        )
                        .build(),

                    JournalLine.builder()
                        .account(account(org, "1100"))
                        .debit(0.0)
                        .credit(outstanding)
                        .description(
                            "Write off receivable — " +
                            loan.getReferenceNumber()
                        )
                        .build()
                )
            );

        } catch (Exception e) {

            log.warn(
                "Could not post GL entry for write-off of loan {}: {}",
                loan.getId(),
                e.getMessage()
            );
        }
    }

    
    @Transactional
    public JournalEntry postExpense(Expense expense) {

        Organization org = expense.getOrganization();

        ensureChartOfAccounts(org);

        ChartOfAccount expenseAccount =
            account(
                org,
                expense.getCategory().getAccountCode()
            );

        ChartOfAccount paymentGlAccount =
            expense.getPaymentAccount().getGlAccount();

        String reference =
            "EXP-" + expense.getId();

        return post(
            org,
            expense.getBranch(),
            "EXPENSE",
            String.valueOf(expense.getId()),
            reference,
            "Expense — " +
                expense.getCategory().getLabel() +
                (
                    expense.getDescription() != null &&
                    !expense.getDescription().isBlank()
                        ? ": " + expense.getDescription()
                        : ""
                ),

            List.of(

                JournalLine.builder()
                    .account(expenseAccount)
                    .debit(expense.getAmount())
                    .credit(0.0)
                    .description(
                        expense.getCategory().getLabel() +
                        " — " +
                        reference
                    )
                    .build(),

                JournalLine.builder()
                    .account(paymentGlAccount)
                    .debit(0.0)
                    .credit(expense.getAmount())
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

    /**
     * Reverses the journal entry backing a posted expense.
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

    
    @Transactional
    public JournalEntry reverseEntry(
        Long orgId,
        Long entryId,
        String reversedBy,
        String reason
    ) {

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

        if (Boolean.TRUE.equals(original.getReversed())) {

            throw new IllegalStateException(
                "Entry " +
                entryId +
                " has already been reversed"
            );
        }

        List<JournalLine> reversedLines =
            original.getLines()
                .stream()
                .map(
                    l ->
                        JournalLine.builder()
                            .account(l.getAccount())
                            .debit(
                                l.getCredit() != null
                                    ? l.getCredit()
                                    : 0.0
                            )
                            .credit(
                                l.getDebit() != null
                                    ? l.getDebit()
                                    : 0.0
                            )
                            .description(
                                "Reversal of #" +
                                entryId +
                                " — " +
                                l.getDescription()
                            )
                            .build()
                )
                .toList();

        JournalEntry reversal =
            JournalEntry.builder()
                .organization(original.getOrganization())
                .branch(original.getBranch())
                .entryDate(LocalDate.now())
                .sourceType("REVERSAL")
                .sourceId(String.valueOf(entryId))
                .reference(original.getReference())
                .description(
                    "Reversal of entry #" +
                    entryId +
                    (
                        reason != null &&
                        !reason.isBlank()
                            ? ": " + reason
                            : ""
                    ) +
                    " — " +
                    original.getDescription()
                )
                .createdBy(
                    reversedBy != null
                        ? reversedBy
                        : "SYSTEM"
                )
                .reversed(false)
                .build();

        reversal = journalRepo.save(reversal);

        for (JournalLine line : reversedLines) {

            line.setJournalEntry(reversal);

            lineRepo.save(line);
        }

        original.setReversed(true);

        journalRepo.save(original);

        return reversal;
    }

    
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
            lineRepo.findLedgerForAccount(accountId);

        List<Map<String, Object>> rows =
            new ArrayList<>();

        double running = 0.0;

        for (JournalLine line : lines) {

            double debit =
                line.getDebit() != null
                    ? line.getDebit()
                    : 0.0;

            double credit =
                line.getCredit() != null
                    ? line.getCredit()
                    : 0.0;

            running +=
                debitNormal
                    ? debit - credit
                    : credit - debit;

            Map<String, Object> row =
                new LinkedHashMap<>();

            JournalEntry entry =
                line.getJournalEntry();

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

            row.put("debit", debit);
            row.put("credit", credit);
            row.put("balance", running);
            row.put("reversed", entry.getReversed());

            rows.add(row);
        }

        Map<String, Object> result =
            new LinkedHashMap<>();

        result.put("account", acc);
        result.put("entries", rows);
        result.put("closingBalance", running);

        return result;
    }

    
   
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

        for (ChartOfAccount acc : accounts) {

            List<JournalLine> lines =
                lineRepo.findByAccount_Id(
                    acc.getId()
                );

            double debit =
                lines.stream()
                    .mapToDouble(
                        l ->
                            l.getDebit() != null
                                ? l.getDebit()
                                : 0.0
                    )
                    .sum();

            double credit =
                lines.stream()
                    .mapToDouble(
                        l ->
                            l.getCredit() != null
                                ? l.getCredit()
                                : 0.0
                    )
                    .sum();

            double net = debit - credit;

            Map<String, Object> row =
                new LinkedHashMap<>();

            row.put("code", acc.getCode());
            row.put("name", acc.getName());
            row.put("type", acc.getType());

            row.put(
                "debit",
                net > 0
                    ? net
                    : 0.0
            );

            row.put(
                "credit",
                net < 0
                    ? -net
                    : 0.0
            );

            rows.add(row);

            totalDebit +=
                net > 0
                    ? net
                    : 0.0;

            totalCredit +=
                net < 0
                    ? -net
                    : 0.0;
        }

        Map<String, Object> result =
            new LinkedHashMap<>();

        result.put("accounts", rows);
        result.put("totalDebit", totalDebit);
        result.put("totalCredit", totalCredit);

        result.put(
            "balanced",
            Math.abs(totalDebit - totalCredit) < 0.01
        );

        return result;
    }

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

        for (ChartOfAccount acc : accounts) {

            double balance =
                netBalance(acc);

            Map<String, Object> row =
                new LinkedHashMap<>();

            row.put("code", acc.getCode());
            row.put("name", acc.getName());
            row.put("balance", balance);

            byType
                .get(acc.getType())
                .add(row);

            switch (acc.getType()) {

                case ASSET ->
                    totalAssets += balance;

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

        double netIncome =
            totalIncome -
            totalExpense;

        totalEquity += netIncome;

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

        result.put("totalAssets", totalAssets);
        result.put(
            "totalLiabilities",
            totalLiabilities
        );

        result.put(
            "totalEquity",
            totalEquity
        );

        result.put(
            "balanced",
            Math.abs(
                totalAssets -
                (
                    totalLiabilities +
                    totalEquity
                )
            ) < 0.01
        );

        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProfitAndLoss(
        Long orgId,
        LocalDate from,
        LocalDate to
    ) {

        List<JournalEntry> entries =
            journalRepo
                .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
                    orgId,
                    from,
                    to
                );

        Map<String, double[]> perAccount =
            new LinkedHashMap<>();

        Map<String, String> names =
            new LinkedHashMap<>();

        Map<
            String,
            ChartOfAccount.AccountType
        > types =
            new LinkedHashMap<>();

        for (JournalEntry entry : entries) {

            if (Boolean.TRUE.equals(
                entry.getReversed()
            )) {
                continue;
            }

            for (JournalLine line :
                entry.getLines()
            ) {

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
                    line.getDebit() != null
                        ? line.getDebit()
                        : 0.0;

                double credit =
                    line.getCredit() != null
                        ? line.getCredit()
                        : 0.0;

                double net =
                    acc.getType()
                        == ChartOfAccount.AccountType.INCOME

                        ? credit - debit

                        : debit - credit;

                perAccount.merge(
                    acc.getCode(),
                    new double[]{net},
                    (a, b) ->
                        new double[]{
                            a[0] + b[0]
                        }
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

        List<Map<String, Object>> income =
            new ArrayList<>();

        List<Map<String, Object>> expense =
            new ArrayList<>();

        double totalIncome = 0.0;
        double totalExpense = 0.0;

        for (
            var entry :
            perAccount.entrySet()
        ) {

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
                entry.getValue()[0]
            );

            if (
                types.get(entry.getKey())
                    == ChartOfAccount.AccountType.INCOME
            ) {

                income.add(row);

                totalIncome +=
                    entry.getValue()[0];

            } else {

                expense.add(row);

                totalExpense +=
                    entry.getValue()[0];
            }
        }

        Map<String, Object> result =
            new LinkedHashMap<>();

        result.put("from", from);
        result.put("to", to);
        result.put("income", income);
        result.put("expense", expense);
        result.put("totalIncome", totalIncome);
        result.put("totalExpense", totalExpense);

        result.put(
            "netIncome",
            totalIncome - totalExpense
        );

        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCashFlow(
        Long orgId,
        LocalDate from,
        LocalDate to
    ) {

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

        for (JournalEntry entry : entries) {

            if (Boolean.TRUE.equals(
                entry.getReversed()
            )) {
                continue;
            }

            for (JournalLine line :
                entry.getLines()
            ) {

                if (
                    !"1000".equals(
                        line.getAccount().getCode()
                    )
                ) {
                    continue;
                }

                double net =
                    (
                        line.getDebit() != null
                            ? line.getDebit()
                            : 0.0
                    )
                    -
                    (
                        line.getCredit() != null
                            ? line.getCredit()
                            : 0.0
                    );

                switch (
                    entry.getSourceType() != null
                        ? entry.getSourceType()
                        : ""
                ) {

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

        double netChange =
            lending +
            collections +
            feesAndPenalties +
            other;

        Map<String, Object> result =
            new LinkedHashMap<>();

        result.put("from", from);
        result.put("to", to);

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

   @Transactional(readOnly = true)
    public List<Map<String, Object>> getBranchSummary(
        Long orgId,
        LocalDate from,
        LocalDate to
    ) {

        List<JournalEntry> entries =
            journalRepo
                .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
                    orgId,
                    from,
                    to
                );

        Map<String, double[]> byBranch =
            new LinkedHashMap<>();

        for (JournalEntry entry : entries) {

            if (Boolean.TRUE.equals(
                entry.getReversed()
            )) {
                continue;
            }

            String branchName =
                entry.getBranchName() != null
                    ? entry.getBranchName()
                    : "Unassigned";

            double[] totals =
                byBranch.computeIfAbsent(
                    branchName,
                    k -> new double[3]
                );

            double debitTotal =
                entry.getLines()
                    .stream()
                    .mapToDouble(
                        l ->
                            l.getDebit() != null
                                ? l.getDebit()
                                : 0.0
                    )
                    .sum();

            switch (
                entry.getSourceType() != null
                    ? entry.getSourceType()
                    : ""
            ) {

                case "LOAN_DISBURSEMENT" ->
                    totals[0] += debitTotal;

                case "PAYMENT_RECEIVED" ->
                    totals[1] += debitTotal;

                case "PROCESSING_FEE" ->
                    totals[2] += debitTotal;

                default -> {
                    // Not part of this summary.
                }
            }
        }

        List<Map<String, Object>> rows =
            new ArrayList<>();

        for (
            var entry :
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

    
    private double netBalance(
        ChartOfAccount acc
    ) {

        List<JournalLine> lines =
            lineRepo.findByAccount_Id(
                acc.getId()
            );

        double debit =
            lines.stream()
                .mapToDouble(
                    l ->
                        l.getDebit() != null
                            ? l.getDebit()
                            : 0.0
                )
                .sum();

        double credit =
            lines.stream()
                .mapToDouble(
                    l ->
                        l.getCredit() != null
                            ? l.getCredit()
                            : 0.0
                )
                .sum();

        return acc.getNormalBalance()
            == ChartOfAccount.NormalBalance.DEBIT

            ? debit - credit

            : credit - debit;
    }
}
