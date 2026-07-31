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
import java.util.*;

/**
 * Double-entry general ledger. Every transaction that moves real money —
 * disbursing a loan, receiving a payment, writing off a bad debt — posts a
 * balanced journal entry here automatically, so the platform's numbers can
 * actually be reconciled by a finance team the way a bank expects, not just
 * read off a list of loan/payment rows.
 *
 * This is a real, working general ledger for the core lending transactions.
 * It is NOT a full accounting suite (no AP/AR beyond loans, no multi-currency
 * revaluation, no period close/lock) — those are legitimate next steps, not
 * silently faked here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingService {

    private final ChartOfAccountRepository coaRepo;
    private final JournalEntryRepository   journalRepo;
    private final JournalLineRepository    lineRepo;

    // ---- Standard chart of accounts, seeded once per organization ----

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
        // Expense module (Phase 11) — granular operating expense lines, additive only;
        // existing 5100 is left untouched for any pre-existing manual entries.
        {"5200", "Salaries and Wages",         "EXPENSE",   "DEBIT"},
        {"5201", "Rent",                       "EXPENSE",   "DEBIT"},
        {"5202", "Utilities",                  "EXPENSE",   "DEBIT"},
        {"5203", "Internet",                   "EXPENSE",   "DEBIT"},
        {"5204", "Transport",                  "EXPENSE",   "DEBIT"},
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
        {"5215", "Other Operating Expenses",   "EXPENSE",   "DEBIT"},
    };

    @Transactional
    public void ensureChartOfAccounts(Organization org) {
        List<ChartOfAccount> existing = coaRepo.findByOrganization_IdOrderByCodeAsc(org.getId());
        Set<String> existingCodes = new HashSet<>();
        for (ChartOfAccount a : existing) existingCodes.add(a.getCode());
        for (String[] a : DEFAULT_ACCOUNTS) {
            if (existingCodes.contains(a[0])) continue; // backfills accounts added after an org was created
            coaRepo.save(ChartOfAccount.builder()
                .organization(org).code(a[0]).name(a[1])
                .type(ChartOfAccount.AccountType.valueOf(a[2]))
                .normalBalance(ChartOfAccount.NormalBalance.valueOf(a[3]))
                .active(true).build());
        }
        if (existing.isEmpty()) log.info("Seeded default chart of accounts for org {}", org.getId());
    }

    private ChartOfAccount account(Organization org, String code) {
        return coaRepo.findByOrganization_IdAndCode(org.getId(), code)
            .orElseThrow(() -> new IllegalStateException(
                "Chart of accounts not set up for this organization (missing account " + code + ")"));
    }

    /** The default equity account (3000) — used as the counter-account when a bank/cash
     *  account is opened with a starting balance (funded by the owner/institution's capital). */
    public ChartOfAccount getEquityAccount(Organization org) {
        ensureChartOfAccounts(org);
        return account(org, "3000");
    }

    /** Adds an institution-specific account (e.g. a second bank account, a local tax liability)
     *  on top of the seeded defaults. Code must be unique within the organization. */
    @Transactional
    public ChartOfAccount createAccount(Organization org, String code, String name,
                                         ChartOfAccount.AccountType type, ChartOfAccount.NormalBalance normalBalance) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Account code is required");
        if (coaRepo.existsByOrganization_IdAndCode(org.getId(), code))
            throw new IllegalArgumentException("Account code " + code + " already exists");
        return coaRepo.save(ChartOfAccount.builder()
            .organization(org).code(code).name(name).type(type).normalBalance(normalBalance).active(true).build());
    }

    /** Renaming or activating/deactivating an account. Code and type are intentionally not
     *  editable here — changing them after journal lines exist would misclassify history. */
    @Transactional
    public ChartOfAccount updateAccount(Long orgId, Long accountId, String name, Boolean active) {
        ChartOfAccount acc = coaRepo.findByIdAndOrganization_Id(accountId, orgId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        if (name != null && !name.isBlank()) acc.setName(name);
        if (active != null) acc.setActive(active);
        return coaRepo.save(acc);
    }

    /** Reverses a posted entry with an equal-and-opposite entry, rather than editing or deleting
     *  history — the original stays on the books (marked reversed) and the reversal is its own
     *  auditable entry, the way real double-entry bookkeeping requires corrections to work. */
    @Transactional
    public JournalEntry reverseEntry(Long orgId, Long entryId, String reversedBy, String reason) {
        JournalEntry original = journalRepo.findByIdAndOrganization_Id(entryId, orgId)
            .orElseThrow(() -> new IllegalArgumentException("Journal entry not found: " + entryId));
        if (Boolean.TRUE.equals(original.getReversed()))
            throw new IllegalStateException("Entry " + entryId + " has already been reversed");

        List<JournalLine> reversedLines = original.getLines().stream()
            .map(l -> JournalLine.builder()
                .account(l.getAccount())
                .debit(l.getCredit() != null ? l.getCredit() : 0.0)
                .credit(l.getDebit()  != null ? l.getDebit()  : 0.0)
                .description("Reversal of #" + entryId + " — " + l.getDescription())
                .build())
            .collect(java.util.stream.Collectors.toList());

        JournalEntry reversal = JournalEntry.builder()
            .organization(original.getOrganization()).entryDate(LocalDate.now())
            .sourceType("REVERSAL").sourceId(String.valueOf(entryId))
            .reference(original.getReference())
            .description("Reversal of entry #" + entryId + (reason != null && !reason.isBlank() ? ": " + reason : "") + " — " + original.getDescription())
            .createdBy(reversedBy != null ? reversedBy : "SYSTEM").reversed(false)
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

    // ---- General Ledger — per-account transaction history with running balance ----

    /** Full transaction history for one account, in chronological order, with a running
     *  balance expressed in the account's normal-balance sense (e.g. an ASSET account's
     *  balance rises on debits) — this is the actual "General Ledger" view, as distinct
     *  from the trial balance's single net figure per account. */
    public Map<String,Object> getLedger(Long orgId, Long accountId) {
        ChartOfAccount acc = coaRepo.findByIdAndOrganization_Id(accountId, orgId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        boolean debitNormal = acc.getNormalBalance() == ChartOfAccount.NormalBalance.DEBIT;

        List<JournalLine> lines = lineRepo.findLedgerForAccount(accountId);
        List<Map<String,Object>> rows = new ArrayList<>();
        double running = 0;
        for (JournalLine l : lines) {
            double debit  = l.getDebit()  != null ? l.getDebit()  : 0;
            double credit = l.getCredit() != null ? l.getCredit() : 0;
            running += debitNormal ? (debit - credit) : (credit - debit);

            Map<String,Object> row = new LinkedHashMap<>();
            JournalEntry e = l.getJournalEntry();
            row.put("entryId", e.getId());
            row.put("date", e.getEntryDate());
            row.put("reference", e.getReference());
            row.put("sourceType", e.getSourceType());
            row.put("description", l.getDescription() != null ? l.getDescription() : e.getDescription());
            row.put("debit", debit);
            row.put("credit", credit);
            row.put("balance", running);
            row.put("reversed", e.getReversed());
            rows.add(row);
        }

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("account", acc);
        result.put("entries", rows);
        result.put("closingBalance", running);
        return result;
    }
    @Transactional
    public JournalEntry post(Organization org, String sourceType, String sourceId, String reference,
                              String description, List<JournalLine> lines) {
        return post(org, null, sourceType, sourceId, reference, description, lines);
    }

    /** Same as above, but tags the entry with a branch for branch-level reporting (Phase 9). */
    @Transactional
    public JournalEntry post(Organization org, Branch branch, String sourceType, String sourceId, String reference,
                              String description, List<JournalLine> lines) {
        double totalDebit  = lines.stream().mapToDouble(l -> l.getDebit()  != null ? l.getDebit()  : 0).sum();
        double totalCredit = lines.stream().mapToDouble(l -> l.getCredit() != null ? l.getCredit() : 0).sum();
        if (Math.abs(totalDebit - totalCredit) > 0.01) {
            throw new IllegalStateException(String.format(
                "Journal entry does not balance: debits %.2f != credits %.2f (%s)", totalDebit, totalCredit, description));
        }

        JournalEntry entry = JournalEntry.builder()
            .organization(org).branch(branch).entryDate(LocalDate.now())
            .sourceType(sourceType).sourceId(sourceId).reference(reference)
            .description(description).createdBy("SYSTEM").reversed(false)
            .build();
        entry = journalRepo.save(entry);

        for (JournalLine line : lines) {
            line.setJournalEntry(entry);
            lineRepo.save(line);
        }
        return entry;
    }

    // ---- Standard postings for core lending transactions ----

    /** DR Loans Receivable, CR Cash — money actually leaves the bank when a loan disburses.
     *  If a processing fee applies, it's collected in cash alongside disbursement and recognized
     *  as income immediately (DR Cash / CR Fee Income) — previously this fee was charged to the
     *  borrower and stored on the Loan record but never posted anywhere in the ledger. */
    @Transactional
    public void postDisbursement(Loan loan) {
        try {
            Organization org = loan.getOrganization();
            ensureChartOfAccounts(org);
            List<JournalLine> lines = new ArrayList<>(List.of(
                JournalLine.builder().account(account(org, "1100")).debit(loan.getAmount()).credit(0.0)
                    .description("Loans Receivable — " + loan.getReferenceNumber()).build(),
                JournalLine.builder().account(account(org, "1000")).debit(0.0).credit(loan.getAmount())
                    .description("Cash disbursed — " + loan.getReferenceNumber()).build()
            ));
            post(org, loan.getBranch(), "LOAN_DISBURSEMENT", String.valueOf(loan.getId()), loan.getReferenceNumber(),
                "Disbursement of loan " + loan.getReferenceNumber(), lines);

            double fee = loan.getProcessingFee() != null ? loan.getProcessingFee() : 0;
            if (fee > 0) {
                post(org, loan.getBranch(), "PROCESSING_FEE", String.valueOf(loan.getId()), loan.getReferenceNumber(),
                    "Processing fee collected on " + loan.getReferenceNumber(),
                    List.of(
                        JournalLine.builder().account(account(org, "1000")).debit(fee).credit(0.0)
                            .description("Processing fee — " + loan.getReferenceNumber()).build(),
                        JournalLine.builder().account(account(org, "4100")).debit(0.0).credit(fee)
                            .description("Processing fee income — " + loan.getReferenceNumber()).build()
                    ));
            }
        } catch (Exception e) {
            log.warn("Could not post GL entry for disbursement of loan {}: {}", loan.getId(), e.getMessage());
        }
    }

    /** DR Interest Receivable, CR Interest Income — recognizes interest earned but not yet
     *  collected (accrual basis). Meant to be run once per day per active loan by the EOD job,
     *  for the interest that accrued on that day only — never call this with a period total or
     *  it will double-count against days already accrued. */
    @Transactional
    public void postInterestAccrual(Loan loan, double dailyInterestAmount) {
        if (dailyInterestAmount <= 0) return;
        try {
            Organization org = loan.getOrganization();
            ensureChartOfAccounts(org);
            post(org, loan.getBranch(), "INTEREST_ACCRUAL", String.valueOf(loan.getId()), loan.getReferenceNumber(),
                "Daily interest accrual for " + loan.getReferenceNumber() + " (" + LocalDate.now() + ")",
                List.of(
                    JournalLine.builder().account(account(org, "1150")).debit(dailyInterestAmount).credit(0.0)
                        .description("Interest accrued — " + loan.getReferenceNumber()).build(),
                    JournalLine.builder().account(account(org, "4000")).debit(0.0).credit(dailyInterestAmount)
                        .description("Interest income accrued — " + loan.getReferenceNumber()).build()
                ));
        } catch (Exception e) {
            log.warn("Could not post interest accrual for loan {}: {}", loan.getId(), e.getMessage());
        }
    }

    /** DR Cash, split CR across Loans Receivable (principal) + Interest Income + Fee/Penalty Income. */
    @Transactional
    public void postPaymentReceived(Payment payment) {
        try {
            Loan loan = payment.getLoan();
            Organization org = loan.getOrganization();
            ensureChartOfAccounts(org);

            double principal = payment.getPrincipalComponent() != null ? payment.getPrincipalComponent() : 0;
            double interest  = payment.getInterestComponent()  != null ? payment.getInterestComponent()  : 0;
            double penalty   = payment.getPenalty()            != null ? payment.getPenalty()            : 0;
            double total = payment.getAmountPaid() != null ? payment.getAmountPaid() : payment.getAmount();
            // Reconcile rounding — whatever isn't explicitly principal/interest/penalty falls to principal
            double accounted = principal + interest + penalty;
            if (Math.abs(accounted - total) > 0.01) principal += (total - accounted);

            List<JournalLine> lines = new ArrayList<>();
            lines.add(JournalLine.builder().account(account(org, "1000")).debit(total).credit(0.0)
                .description("Payment received — " + loan.getReferenceNumber()).build());
            if (principal > 0) lines.add(JournalLine.builder().account(account(org, "1100")).debit(0.0).credit(principal)
                .description("Principal — " + loan.getReferenceNumber()).build());
            if (interest > 0) {
                // If interest was already accrued (DR Interest Receivable / CR Interest Income) by the
                // EOD job for this loan, this cash receipt clears the receivable instead of recognizing
                // the income a second time. Any interest beyond what's accrued (e.g. accrual isn't in
                // use, or this payment covers more than was accrued so far) still hits Interest Income
                // directly — this is what keeps cash-basis institutions behaving exactly as before.
                double accrued = accruedInterestReceivable(org, loan.getReferenceNumber());
                double clearReceivable = Math.min(interest, Math.max(accrued, 0));
                double remainder = interest - clearReceivable;
                if (clearReceivable > 0) lines.add(JournalLine.builder().account(account(org, "1150")).debit(0.0).credit(clearReceivable)
                    .description("Clears accrued interest — " + loan.getReferenceNumber()).build());
                if (remainder > 0) lines.add(JournalLine.builder().account(account(org, "4000")).debit(0.0).credit(remainder)
                    .description("Interest — " + loan.getReferenceNumber()).build());
            }
            if (penalty > 0) lines.add(JournalLine.builder().account(account(org, "4100")).debit(0.0).credit(penalty)
                .description("Penalty/fee — " + loan.getReferenceNumber()).build());

            post(org, loan.getBranch(), "PAYMENT_RECEIVED", String.valueOf(payment.getId()), payment.getPaymentReference(),
                "Payment received on " + loan.getReferenceNumber(), lines);
        } catch (Exception e) {
            log.warn("Could not post GL entry for payment {}: {}", payment.getId(), e.getMessage());
        }
    }

    /** Net accrued-but-uncollected interest for a loan (sum of Interest Receivable debits from
     *  accrual entries, minus whatever's already been cleared against prior payments). */
    private double accruedInterestReceivable(Organization org, String loanReference) {
        ChartOfAccount receivable = coaRepo.findByOrganization_IdAndCode(org.getId(), "1150").orElse(null);
        if (receivable == null) return 0;
        return lineRepo.findAccrualLinesForLoan(receivable.getId(), loanReference).stream()
            .mapToDouble(l -> (l.getDebit() != null ? l.getDebit() : 0) - (l.getCredit() != null ? l.getCredit() : 0))
            .sum();
    }

    /** DR Loan Loss Expense, CR Loans Receivable — recognizing a bad debt when a loan is written off. */
    @Transactional
    public void postWriteOff(Loan loan) {
        try {
            Organization org = loan.getOrganization();
            ensureChartOfAccounts(org);
            double outstanding = loan.getOutstandingBalance() != null ? loan.getOutstandingBalance() : 0;
            if (outstanding <= 0) return;
            post(org, loan.getBranch(), "WRITE_OFF", String.valueOf(loan.getId()), loan.getReferenceNumber(),
                "Write-off of loan " + loan.getReferenceNumber(),
                List.of(
                    JournalLine.builder().account(account(org, "5000")).debit(outstanding).credit(0.0)
                        .description("Loan loss expense — " + loan.getReferenceNumber()).build(),
                    JournalLine.builder().account(account(org, "1100")).debit(0.0).credit(outstanding)
                        .description("Write off receivable — " + loan.getReferenceNumber()).build()
                ));
        } catch (Exception e) {
            log.warn("Could not post GL entry for write-off of loan {}: {}", loan.getId(), e.getMessage());
        }
    }

    /** DR the expense's specific category account, CR the bank/cash account it was paid from.
     *  Unlike the loan postings above, failures here are NOT swallowed — recording the expense
     *  IS the ledger entry, so if it can't be posted (e.g. missing account), the whole
     *  expense creation must roll back rather than silently leaving a POSTED expense with no
     *  matching journal entry. */
    @Transactional
    public JournalEntry postExpense(Expense expense) {
        Organization org = expense.getOrganization();
        ensureChartOfAccounts(org);

        ChartOfAccount expenseAccount = account(org, expense.getCategory().getAccountCode());
        ChartOfAccount paymentGlAccount = expense.getPaymentAccount().getGlAccount();

        String reference = "EXP-" + expense.getId();
        return post(org, expense.getBranch(), "EXPENSE", String.valueOf(expense.getId()), reference,
            "Expense — " + expense.getCategory().getLabel()
                + (expense.getDescription() != null && !expense.getDescription().isBlank()
                    ? ": " + expense.getDescription() : ""),
            List.of(
                JournalLine.builder().account(expenseAccount).debit(expense.getAmount()).credit(0.0)
                    .description(expense.getCategory().getLabel() + " — " + reference).build(),
                JournalLine.builder().account(paymentGlAccount).debit(0.0).credit(expense.getAmount())
                    .description("Paid from " + expense.getPaymentAccount().getName() + " — " + reference).build()
            ));
    }

    /** Reverses the journal entry backing a POSTED expense — used when an expense is voided. */
    @Transactional
    public JournalEntry reverseExpense(Long orgId, Long journalEntryId, String reversedBy, String reason) {
        return reverseEntry(orgId, journalEntryId, reversedBy, reason);
    }
    // ---- Reporting ----

    /** Trial balance as of now — every account's net debit/credit position. Total debits must equal total credits. */
    public Map<String,Object> getTrialBalance(Long orgId) {
        List<ChartOfAccount> accounts = coaRepo.findByOrganization_IdOrderByCodeAsc(orgId);
        List<Map<String,Object>> rows = new ArrayList<>();
        double totalDebit = 0, totalCredit = 0;

        for (ChartOfAccount acc : accounts) {
            List<JournalLine> lines = lineRepo.findByAccount_Id(acc.getId());
            double debit  = lines.stream().mapToDouble(l -> l.getDebit()  != null ? l.getDebit()  : 0).sum();
            double credit = lines.stream().mapToDouble(l -> l.getCredit() != null ? l.getCredit() : 0).sum();
            double net = debit - credit;

            Map<String,Object> row = new LinkedHashMap<>();
            row.put("code", acc.getCode());
            row.put("name", acc.getName());
            row.put("type", acc.getType());
            row.put("debit",  net > 0 ? net : 0);
            row.put("credit", net < 0 ? -net : 0);
            rows.add(row);
            totalDebit  += net > 0 ? net : 0;
            totalCredit += net < 0 ? -net : 0;
        }

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("accounts", rows);
        result.put("totalDebit", totalDebit);
        result.put("totalCredit", totalCredit);
        result.put("balanced", Math.abs(totalDebit - totalCredit) < 0.01);
        return result;
    }

    /** Balance Sheet as of now: Assets = Liabilities + Equity (including current-period net
     *  income folded into equity, the way an unclosed accounting period is normally shown). */
    public Map<String,Object> getBalanceSheet(Long orgId) {
        List<ChartOfAccount> accounts = coaRepo.findByOrganization_IdOrderByCodeAsc(orgId);
        Map<ChartOfAccount.AccountType, List<Map<String,Object>>> byType = new EnumMap<>(ChartOfAccount.AccountType.class);
        for (ChartOfAccount.AccountType t : ChartOfAccount.AccountType.values()) byType.put(t, new ArrayList<>());

        double totalAssets = 0, totalLiabilities = 0, totalEquity = 0, totalIncome = 0, totalExpense = 0;
        for (ChartOfAccount acc : accounts) {
            double balance = netBalance(acc);
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("code", acc.getCode()); row.put("name", acc.getName()); row.put("balance", balance);
            byType.get(acc.getType()).add(row);
            switch (acc.getType()) {
                case ASSET     -> totalAssets      += balance;
                case LIABILITY -> totalLiabilities += balance;
                case EQUITY    -> totalEquity       += balance;
                case INCOME    -> totalIncome       += balance;
                case EXPENSE   -> totalExpense      += balance;
            }
        }
        double netIncome = totalIncome - totalExpense; // folded into equity for an unclosed period
        totalEquity += netIncome;

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("asOf", LocalDate.now());
        result.put("assets", byType.get(ChartOfAccount.AccountType.ASSET));
        result.put("liabilities", byType.get(ChartOfAccount.AccountType.LIABILITY));
        result.put("equity", byType.get(ChartOfAccount.AccountType.EQUITY));
        result.put("currentPeriodNetIncome", netIncome);
        result.put("totalAssets", totalAssets);
        result.put("totalLiabilities", totalLiabilities);
        result.put("totalEquity", totalEquity);
        result.put("balanced", Math.abs(totalAssets - (totalLiabilities + totalEquity)) < 0.01);
        return result;
    }

    /** Profit & Loss for a date range: Income accounts minus Expense accounts, using only
     *  journal activity that falls inside [from, to] — unlike the balance sheet, a P&L is
     *  always for a period, never a point in time. */
    public Map<String,Object> getProfitAndLoss(Long orgId, LocalDate from, LocalDate to) {
        List<JournalEntry> entries = journalRepo.findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(orgId, from, to);
        Map<String, double[]> perAccount = new LinkedHashMap<>(); // code -> [name-index unused, net]
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, ChartOfAccount.AccountType> types = new LinkedHashMap<>();

        for (JournalEntry e : entries) {
            if (Boolean.TRUE.equals(e.getReversed())) continue;
            for (JournalLine l : e.getLines()) {
                ChartOfAccount acc = l.getAccount();
                if (acc.getType() != ChartOfAccount.AccountType.INCOME && acc.getType() != ChartOfAccount.AccountType.EXPENSE) continue;
                double debit  = l.getDebit()  != null ? l.getDebit()  : 0;
                double credit = l.getCredit() != null ? l.getCredit() : 0;
                double net = acc.getType() == ChartOfAccount.AccountType.INCOME ? (credit - debit) : (debit - credit);
                perAccount.merge(acc.getCode(), new double[]{net}, (a, b) -> new double[]{a[0] + b[0]});
                names.put(acc.getCode(), acc.getName());
                types.put(acc.getCode(), acc.getType());
            }
        }

        List<Map<String,Object>> income = new ArrayList<>(), expense = new ArrayList<>();
        double totalIncome = 0, totalExpense = 0;
        for (var entry : perAccount.entrySet()) {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("code", entry.getKey()); row.put("name", names.get(entry.getKey())); row.put("amount", entry.getValue()[0]);
            if (types.get(entry.getKey()) == ChartOfAccount.AccountType.INCOME) { income.add(row); totalIncome += entry.getValue()[0]; }
            else { expense.add(row); totalExpense += entry.getValue()[0]; }
        }

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("from", from); result.put("to", to);
        result.put("income", income); result.put("expense", expense);
        result.put("totalIncome", totalIncome); result.put("totalExpense", totalExpense);
        result.put("netIncome", totalIncome - totalExpense);
        return result;
    }

    /** Simplified direct-method Cash Flow Statement: every journal line touching the Cash and
     *  Bank account (1000) in the period, grouped by why the cash moved. This is cash-basis by
     *  construction (it only looks at the cash account), which is exactly what a cash flow
     *  statement is supposed to be — separate from the accrual-basis P&L above. */
    public Map<String,Object> getCashFlow(Long orgId, LocalDate from, LocalDate to) {
        List<JournalEntry> entries = journalRepo.findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(orgId, from, to);
        double lending = 0, collections = 0, feesAndPenalties = 0, other = 0;
        for (JournalEntry e : entries) {
            if (Boolean.TRUE.equals(e.getReversed())) continue;
            for (JournalLine l : e.getLines()) {
                if (!"1000".equals(l.getAccount().getCode())) continue;
                double net = (l.getDebit() != null ? l.getDebit() : 0) - (l.getCredit() != null ? l.getCredit() : 0);
                switch (e.getSourceType() != null ? e.getSourceType() : "") {
                    case "LOAN_DISBURSEMENT"  -> lending          += net; // negative: cash out
                    case "PAYMENT_RECEIVED"   -> collections      += net; // positive: cash in (principal+interest+penalty)
                    case "PROCESSING_FEE"     -> feesAndPenalties += net;
                    default -> other += net;
                }
            }
        }
        double netChange = lending + collections + feesAndPenalties + other;

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("from", from); result.put("to", to);
        result.put("cashUsedForLending", lending);
        result.put("cashFromCollections", collections);
        result.put("cashFromFees", feesAndPenalties);
        result.put("otherCashMovement", other);
        result.put("netChangeInCash", netChange);
        return result;
    }

    /** Branch-level summary (Phase 9): cash disbursed, collected, and fee income per branch for
     *  a period, from the same journal — entries without a branch (head-office/cashbook entries
     *  not tied to a loan) are grouped under "Unassigned". */
    public List<Map<String,Object>> getBranchSummary(Long orgId, LocalDate from, LocalDate to) {
        List<JournalEntry> entries = journalRepo.findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(orgId, from, to);
        Map<String, double[]> byBranch = new LinkedHashMap<>(); // name -> [disbursed, collected, fees]
        for (JournalEntry e : entries) {
            if (Boolean.TRUE.equals(e.getReversed())) continue;
            String branchName = e.getBranchName() != null ? e.getBranchName() : "Unassigned";
            double[] totals = byBranch.computeIfAbsent(branchName, k -> new double[3]);
            double debitTotal = e.getLines().stream().mapToDouble(l -> l.getDebit() != null ? l.getDebit() : 0).sum();
            switch (e.getSourceType() != null ? e.getSourceType() : "") {
                case "LOAN_DISBURSEMENT" -> totals[0] += debitTotal;
                case "PAYMENT_RECEIVED"  -> totals[1] += debitTotal;
                case "PROCESSING_FEE"    -> totals[2] += debitTotal;
                default -> { /* not part of this summary */ }
            }
        }
        List<Map<String,Object>> rows = new ArrayList<>();
        for (var entry : byBranch.entrySet()) {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("branch", entry.getKey());
            row.put("disbursed", entry.getValue()[0]);
            row.put("collected", entry.getValue()[1]);
            row.put("feeIncome", entry.getValue()[2]);
            rows.add(row);
        }
        return rows;
    }

    private double netBalance(ChartOfAccount acc) {
        List<JournalLine> lines = lineRepo.findByAccount_Id(acc.getId());
        double debit  = lines.stream().mapToDouble(l -> l.getDebit()  != null ? l.getDebit()  : 0).sum();
        double credit = lines.stream().mapToDouble(l -> l.getCredit() != null ? l.getCredit() : 0).sum();
        return acc.getNormalBalance() == ChartOfAccount.NormalBalance.DEBIT ? (debit - credit) : (credit - debit);
    }
}
