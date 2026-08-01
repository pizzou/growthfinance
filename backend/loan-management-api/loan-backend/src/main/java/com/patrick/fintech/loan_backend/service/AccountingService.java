package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Production-oriented double-entry general ledger.
 *
 * All persisted monetary values are BigDecimal with a 2-decimal accounting scale.
 * Every journal entry is validated before persistence: at least two lines, non-negative
 * amounts, exactly one side per line, organization-safe accounts, and exact debit/credit
 * equality. Financial posting failures are never swallowed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);

    private final ChartOfAccountRepository coaRepo;
    private final JournalEntryRepository journalRepo;
    private final JournalLineRepository lineRepo;

    private static final String[][] DEFAULT_ACCOUNTS = {
        {"1000", "Cash and Bank", "ASSET", "DEBIT"},
        {"1100", "Loans Receivable", "ASSET", "DEBIT"},
        {"1150", "Interest Receivable", "ASSET", "DEBIT"},
        {"1200", "Loan Loss Reserve", "ASSET", "CREDIT"},
        {"2000", "Customer Deposits Payable", "LIABILITY", "CREDIT"},
        {"3000", "Owner's Equity", "EQUITY", "CREDIT"},
        {"3100", "Opening Balance Equity", "EQUITY", "CREDIT"},
        {"4000", "Interest Income", "INCOME", "CREDIT"},
        {"4100", "Fee and Penalty Income", "INCOME", "CREDIT"},
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
        {"5215", "Other Operating Expenses", "EXPENSE", "DEBIT"},
    };

    private static BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private static boolean positive(BigDecimal value) {
        return money(value).compareTo(ZERO) > 0;
    }

    private static boolean zeroOrPositive(BigDecimal value) {
        return money(value).compareTo(ZERO) >= 0;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    @Transactional
    public void ensureChartOfAccounts(Organization org) {
        List<ChartOfAccount> existing = coaRepo.findByOrganization_IdOrderByCodeAsc(org.getId());
        Set<String> existingCodes = existing.stream().map(ChartOfAccount::getCode).collect(Collectors.toSet());
        for (String[] a : DEFAULT_ACCOUNTS) {
            if (existingCodes.contains(a[0])) continue;
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
            .orElseThrow(() -> new IllegalStateException("Chart of accounts not set up for this organization (missing account " + code + ")"));
    }

    public ChartOfAccount getEquityAccount(Organization org) {
        ensureChartOfAccounts(org);
        return account(org, "3000");
    }

    @Transactional
    public ChartOfAccount createAccount(Organization org, String code, String name,
                                        ChartOfAccount.AccountType type, ChartOfAccount.NormalBalance normalBalance) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Account code is required");
        if (coaRepo.existsByOrganization_IdAndCode(org.getId(), code))
            throw new IllegalArgumentException("Account code " + code + " already exists");
        return coaRepo.save(ChartOfAccount.builder()
            .organization(org).code(code).name(name).type(type).normalBalance(normalBalance).active(true).build());
    }

    @Transactional
    public ChartOfAccount updateAccount(Long orgId, Long accountId, String name, Boolean active) {
        ChartOfAccount acc = coaRepo.findByIdAndOrganization_Id(accountId, orgId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        if (name != null && !name.isBlank()) acc.setName(name);
        if (active != null) acc.setActive(active);
        return coaRepo.save(acc);
    }

    @Transactional
    public JournalEntry reverseEntry(Long orgId, Long entryId, String reversedBy, String reason) {
        JournalEntry original = journalRepo.findByIdAndOrganization_Id(entryId, orgId)
            .orElseThrow(() -> new IllegalArgumentException("Journal entry not found: " + entryId));
        if (Boolean.TRUE.equals(original.getReversed()))
            throw new IllegalStateException("Entry " + entryId + " has already been reversed");

        List<JournalLine> reversedLines = original.getLines().stream()
            .map(l -> JournalLine.builder()
                .account(l.getAccount())
                .debit(money(l.getCredit()))
                .credit(money(l.getDebit()))
                .description("Reversal of #" + entryId + " — " + l.getDescription())
                .build())
            .collect(Collectors.toList());

        JournalEntry reversal = post(
            original.getOrganization(), original.getBranch(), LocalDate.now(),
            "REVERSAL", String.valueOf(entryId), original.getReference(),
            "Reversal of entry #" + entryId + (reason != null && !reason.isBlank() ? ": " + reason : "") + " — " + original.getDescription(),
            reversedLines, reversedBy != null ? reversedBy : "SYSTEM");

        original.setReversed(true);
        journalRepo.save(original);
        return reversal;
    }

    public Map<String,Object> getLedger(Long orgId, Long accountId) {
        ChartOfAccount acc = coaRepo.findByIdAndOrganization_Id(accountId, orgId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        boolean debitNormal = acc.getNormalBalance() == ChartOfAccount.NormalBalance.DEBIT;
        List<JournalLine> lines = lineRepo.findLedgerForAccount(accountId);
        List<Map<String,Object>> rows = new ArrayList<>();
        BigDecimal running = ZERO;
        for (JournalLine l : lines) {
            BigDecimal debit = money(l.getDebit());
            BigDecimal credit = money(l.getCredit());
            running = running.add(debitNormal ? debit.subtract(credit) : credit.subtract(debit));
            Map<String,Object> row = new LinkedHashMap<>();
            JournalEntry e = l.getJournalEntry();
            row.put("entryId", e.getId());
            row.put("date", e.getEntryDate());
            row.put("reference", e.getReference());
            row.put("sourceType", e.getSourceType());
            row.put("description", l.getDescription() != null ? l.getDescription() : e.getDescription());
            row.put("debit", debit); row.put("credit", credit); row.put("balance", running);
            row.put("reversed", e.getReversed()); rows.add(row);
        }
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("account", acc); result.put("entries", rows); result.put("closingBalance", running);
        return result;
    }

    @Transactional
    public JournalEntry post(Organization org, String sourceType, String sourceId, String reference,
                             String description, List<JournalLine> lines) {
        return post(org, null, sourceType, sourceId, reference, description, lines);
    }

    @Transactional
    public JournalEntry post(Organization org, Branch branch, String sourceType, String sourceId, String reference,
                             String description, List<JournalLine> lines) {
        return post(org, branch, LocalDate.now(), sourceType, sourceId, reference, description, lines);
    }

    @Transactional
    public JournalEntry post(Organization org, Branch branch, LocalDate entryDate, String sourceType, String sourceId,
                             String reference, String description, List<JournalLine> lines) {
        return post(org, branch, entryDate, sourceType, sourceId, reference, description, lines, "SYSTEM");
    }

    private JournalEntry post(Organization org, Branch branch, LocalDate entryDate, String sourceType, String sourceId,
                              String reference, String description, List<JournalLine> lines, String createdBy) {
        validateLines(org, lines, description);
        Optional<JournalEntry> existing = journalRepo.findByOrganization_IdAndSourceTypeAndSourceId(org.getId(), sourceType, sourceId);
        if (existing.isPresent()) return existing.get();

        JournalEntry entry = JournalEntry.builder()
            .organization(org).branch(branch).entryDate(entryDate != null ? entryDate : LocalDate.now())
            .sourceType(sourceType).sourceId(sourceId).reference(reference)
            .description(description).createdBy(createdBy).reversed(false).build();
        entry = journalRepo.save(entry);
        for (JournalLine line : lines) {
            line.setJournalEntry(entry);
            line.setDebit(money(line.getDebit()));
            line.setCredit(money(line.getCredit()));
            lineRepo.save(line);
        }
        return entry;
    }

    private void validateLines(Organization org, List<JournalLine> lines, String description) {
        if (lines == null || lines.size() < 2)
            throw new IllegalStateException("A journal entry must contain at least two lines: " + description);

        BigDecimal totalDebit = ZERO;
        BigDecimal totalCredit = ZERO;
        for (JournalLine line : lines) {
            if (line == null || line.getAccount() == null)
                throw new IllegalStateException("Every journal line must have an account: " + description);
            ChartOfAccount account = line.getAccount();
            if (account.getOrganization() == null || !Objects.equals(account.getOrganization().getId(), org.getId()))
                throw new IllegalStateException("Journal line account belongs to a different organization: " + account.getCode());
            BigDecimal debit = money(line.getDebit());
            BigDecimal credit = money(line.getCredit());
            if (!zeroOrPositive(debit) || !zeroOrPositive(credit))
                throw new IllegalStateException("Journal amounts cannot be negative: " + description);
            if (debit.compareTo(ZERO) > 0 && credit.compareTo(ZERO) > 0)
                throw new IllegalStateException("A journal line cannot contain both debit and credit: " + description);
            if (debit.compareTo(ZERO) == 0 && credit.compareTo(ZERO) == 0)
                throw new IllegalStateException("A journal line must contain a debit or credit amount: " + description);
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }
        if (totalDebit.compareTo(totalCredit) != 0)
            throw new IllegalStateException("Journal entry does not balance: debits " + totalDebit + " != credits " + totalCredit + " (" + description + ")");
    }

    @Transactional
    public void postDisbursement(Loan loan) {
        Organization org = loan.getOrganization();
        ensureChartOfAccounts(org);
        BigDecimal amount = money(loan.getAmount());
        if (!positive(amount)) return;
        post(org, loan.getBranch(), "LOAN_DISBURSEMENT", String.valueOf(loan.getId()), loan.getReferenceNumber(),
            "Disbursement of loan " + loan.getReferenceNumber(), List.of(
                JournalLine.builder().account(account(org, "1100")).debit(amount).credit(ZERO).description("Loans Receivable — " + loan.getReferenceNumber()).build(),
                JournalLine.builder().account(account(org, "1000")).debit(ZERO).credit(amount).description("Cash disbursed — " + loan.getReferenceNumber()).build()
            ));

        BigDecimal fee = money(loan.getProcessingFee());
        if (positive(fee)) {
            post(org, loan.getBranch(), "PROCESSING_FEE", String.valueOf(loan.getId()), loan.getReferenceNumber(),
                "Processing fee collected on " + loan.getReferenceNumber(), List.of(
                    JournalLine.builder().account(account(org, "1000")).debit(fee).credit(ZERO).description("Processing fee — " + loan.getReferenceNumber()).build(),
                    JournalLine.builder().account(account(org, "4100")).debit(ZERO).credit(fee).description("Processing fee income — " + loan.getReferenceNumber()).build()
                ));
        }
    }

    /** Compatibility overload for existing scheduler callers; the ledger itself remains BigDecimal. */
    @Transactional
    public void postInterestAccrual(Loan loan, double dailyInterestAmount) {
        postInterestAccrual(loan, money(dailyInterestAmount));
    }

    @Transactional
    public void postInterestAccrual(Loan loan, BigDecimal dailyInterestAmount) {
        BigDecimal amount = money(dailyInterestAmount);
        if (!positive(amount)) return;
        Organization org = loan.getOrganization();
        ensureChartOfAccounts(org);
        post(org, loan.getBranch(), "INTEREST_ACCRUAL", String.valueOf(loan.getId()) + "-" + LocalDate.now(), loan.getReferenceNumber(),
            "Daily interest accrual for " + loan.getReferenceNumber() + " (" + LocalDate.now() + ")", List.of(
                JournalLine.builder().account(account(org, "1150")).debit(amount).credit(ZERO).description("Interest accrued — " + loan.getReferenceNumber()).build(),
                JournalLine.builder().account(account(org, "4000")).debit(ZERO).credit(amount).description("Interest income accrued — " + loan.getReferenceNumber()).build()
            ));
    }

    @Transactional
    public void postPaymentReceived(PaymentTransaction tx) {
        Loan loan = tx.getLoan();
        Organization org = loan.getOrganization();
        ensureChartOfAccounts(org);

        BigDecimal principal = money(tx.getPrincipalComponent());
        BigDecimal interest = money(tx.getInterestComponent());
        BigDecimal penalty = money(tx.getPenaltyComponent());
        BigDecimal total = money(tx.getAmount());
        BigDecimal unapplied = money(tx.getUnappliedAmount());

        BigDecimal accounted = principal.add(interest).add(penalty).add(unapplied);
        if (accounted.compareTo(total) != 0)
            throw new IllegalStateException("Payment transaction components do not equal total for " + tx.getTransactionReference());

        List<JournalLine> lines = new ArrayList<>();
        lines.add(JournalLine.builder().account(account(org, "1000")).debit(total).credit(ZERO)
            .description("Payment received — " + loan.getReferenceNumber()).build());

        if (positive(principal))
            lines.add(JournalLine.builder().account(account(org, "1100")).debit(ZERO).credit(principal)
                .description("Principal — " + loan.getReferenceNumber()).build());

        if (positive(interest)) {
            BigDecimal accrued = accruedInterestReceivable(org, loan.getReferenceNumber());
            BigDecimal clearReceivable = interest.min(accrued.max(ZERO));
            BigDecimal remainder = interest.subtract(clearReceivable);
            if (positive(clearReceivable))
                lines.add(JournalLine.builder().account(account(org, "1150")).debit(ZERO).credit(clearReceivable)
                    .description("Clears accrued interest — " + loan.getReferenceNumber()).build());
            if (positive(remainder))
                lines.add(JournalLine.builder().account(account(org, "4000")).debit(ZERO).credit(remainder)
                    .description("Interest — " + loan.getReferenceNumber()).build());
        }
        if (positive(penalty))
            lines.add(JournalLine.builder().account(account(org, "4100")).debit(ZERO).credit(penalty)
                .description("Penalty/fee — " + loan.getReferenceNumber()).build());

        // Unapplied credit is a liability until allocated/refunded.
        if (positive(unapplied))
            lines.add(JournalLine.builder().account(account(org, "2000")).debit(ZERO).credit(unapplied)
                .description("Unapplied customer payment — " + loan.getReferenceNumber()).build());

        post(org, loan.getBranch(), "PAYMENT_RECEIVED", String.valueOf(tx.getId()), tx.getTransactionReference(),
            "Payment received on " + loan.getReferenceNumber(), lines);
    }

    /** Backward-compatible adapter for older callers. */
    @Transactional
    public void postPaymentReceived(Payment payment) {
        if (payment.getId() == null)
            throw new IllegalArgumentException("Payment must be persisted before accounting is posted");
        PaymentTransaction tx = PaymentTransaction.builder()
            .id(payment.getId())
            .loan(payment.getLoan())
            .organization(payment.getOrganization())
            .transactionReference(payment.getTransactionId() != null ? payment.getTransactionId() : payment.getPaymentReference())
            .amount(money(payment.getAmountPaid() != null ? payment.getAmountPaid() : payment.getAmount()))
            .principalComponent(money(payment.getPrincipalComponent()))
            .interestComponent(money(payment.getInterestComponent()))
            .penaltyComponent(money(payment.getPenalty()))
            .unappliedAmount(ZERO)
            .build();
        postPaymentReceived(tx);
    }

    @Transactional
    public JournalEntry reversePayment(PaymentTransaction tx, String reversedBy, String reason) {
        Organization org = tx.getOrganization();
        JournalEntry original = journalRepo.findByOrganization_IdAndSourceTypeAndSourceId(
                org.getId(), "PAYMENT_RECEIVED", String.valueOf(tx.getId()))
            .orElseThrow(() -> new IllegalStateException("Accounting entry not found for payment transaction " + tx.getId()));
        if (Boolean.TRUE.equals(original.getReversed()))
            throw new IllegalStateException("Accounting entry for payment transaction " + tx.getId() + " is already reversed");
        return reverseEntry(org.getId(), original.getId(), reversedBy, reason);
    }

    private BigDecimal accruedInterestReceivable(Organization org, String loanReference) {
        ChartOfAccount receivable = coaRepo.findByOrganization_IdAndCode(org.getId(), "1150").orElse(null);
        if (receivable == null) return ZERO;
        return lineRepo.findAccrualLinesForLoan(receivable.getId(), loanReference).stream()
            .map(l -> money(l.getDebit()).subtract(money(l.getCredit())))
            .reduce(ZERO, BigDecimal::add);
    }

    @Transactional
    public void postWriteOff(Loan loan) {
        Organization org = loan.getOrganization();
        ensureChartOfAccounts(org);
        BigDecimal outstanding = money(loan.getOutstandingBalance());
        if (!positive(outstanding)) return;
        post(org, loan.getBranch(), "WRITE_OFF", String.valueOf(loan.getId()), loan.getReferenceNumber(),
            "Write-off of loan " + loan.getReferenceNumber(), List.of(
                JournalLine.builder().account(account(org, "5000")).debit(outstanding).credit(ZERO).description("Loan loss expense — " + loan.getReferenceNumber()).build(),
                JournalLine.builder().account(account(org, "1100")).debit(ZERO).credit(outstanding).description("Write off receivable — " + loan.getReferenceNumber()).build()
            ));
    }

    @Transactional
    public JournalEntry postOpeningBalance(Loan loan) {
        Organization org = loan.getOrganization();
        ensureChartOfAccounts(org);
        BigDecimal outstanding = money(loan.getOutstandingBalance());
        if (!positive(outstanding)) return null;
        LocalDate asOf = loan.getStartDate() != null ? loan.getStartDate() : LocalDate.now();
        return post(org, loan.getBranch(), asOf, "OPENING_BALANCE", String.valueOf(loan.getId()), loan.getReferenceNumber(),
            "Opening balance — migrated loan " + loan.getReferenceNumber(), List.of(
                JournalLine.builder().account(account(org, "1100")).debit(outstanding).credit(ZERO).description("Opening balance — " + loan.getReferenceNumber()).build(),
                JournalLine.builder().account(account(org, "3100")).debit(ZERO).credit(outstanding).description("Opening balance equity — " + loan.getReferenceNumber()).build()
            ));
    }

    @Transactional
    public JournalEntry postExpense(Expense expense) {
        Organization org = expense.getOrganization();
        ensureChartOfAccounts(org);
        ChartOfAccount expenseAccount = account(org, expense.getCategory().getAccountCode());
        ChartOfAccount paymentGlAccount = expense.getPaymentAccount().getGlAccount();
        BigDecimal amount = money(expense.getAmount());
        if (!positive(amount)) throw new IllegalArgumentException("Expense amount must be positive");
        String reference = "EXP-" + expense.getId();
        return post(org, expense.getBranch(), "EXPENSE", String.valueOf(expense.getId()), reference,
            "Expense — " + expense.getCategory().getLabel()
                + (expense.getDescription() != null && !expense.getDescription().isBlank() ? ": " + expense.getDescription() : ""),
            List.of(
                JournalLine.builder().account(expenseAccount).debit(amount).credit(ZERO).description(expense.getCategory().getLabel() + " — " + reference).build(),
                JournalLine.builder().account(paymentGlAccount).debit(ZERO).credit(amount).description("Paid from " + expense.getPaymentAccount().getName() + " — " + reference).build()
            ));
    }

    @Transactional
    public JournalEntry reverseExpense(Long orgId, Long journalEntryId, String reversedBy, String reason) {
        return reverseEntry(orgId, journalEntryId, reversedBy, reason);
    }

    public Map<String,Object> getTrialBalance(Long orgId) {
        List<ChartOfAccount> accounts = coaRepo.findByOrganization_IdOrderByCodeAsc(orgId);
        List<Map<String,Object>> rows = new ArrayList<>();
        BigDecimal totalDebit = ZERO, totalCredit = ZERO;
        for (ChartOfAccount acc : accounts) {
            List<JournalLine> lines = lineRepo.findByAccount_Id(acc.getId());
            BigDecimal debit = lines.stream().map(l -> money(l.getDebit())).reduce(ZERO, BigDecimal::add);
            BigDecimal credit = lines.stream().map(l -> money(l.getCredit())).reduce(ZERO, BigDecimal::add);
            BigDecimal net = debit.subtract(credit);
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("code", acc.getCode()); row.put("name", acc.getName()); row.put("type", acc.getType());
            row.put("debit", net.signum() > 0 ? net : ZERO); row.put("credit", net.signum() < 0 ? net.negate() : ZERO);
            rows.add(row);
            totalDebit = totalDebit.add(net.signum() > 0 ? net : ZERO);
            totalCredit = totalCredit.add(net.signum() < 0 ? net.negate() : ZERO);
        }
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("accounts", rows); result.put("totalDebit", totalDebit); result.put("totalCredit", totalCredit);
        result.put("balanced", totalDebit.compareTo(totalCredit) == 0);
        return result;
    }

    public Map<String,Object> getBalanceSheet(Long orgId) {
        List<ChartOfAccount> accounts = coaRepo.findByOrganization_IdOrderByCodeAsc(orgId);
        Map<ChartOfAccount.AccountType, List<Map<String,Object>>> byType = new EnumMap<>(ChartOfAccount.AccountType.class);
        for (ChartOfAccount.AccountType t : ChartOfAccount.AccountType.values()) byType.put(t, new ArrayList<>());
        BigDecimal totalAssets = ZERO, totalLiabilities = ZERO, totalEquity = ZERO, totalIncome = ZERO, totalExpense = ZERO;
        for (ChartOfAccount acc : accounts) {
            BigDecimal balance = netBalance(acc);
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("code", acc.getCode()); row.put("name", acc.getName()); row.put("balance", balance);
            byType.get(acc.getType()).add(row);
            switch (acc.getType()) {
                case ASSET -> totalAssets = totalAssets.add(balance);
                case LIABILITY -> totalLiabilities = totalLiabilities.add(balance);
                case EQUITY -> totalEquity = totalEquity.add(balance);
                case INCOME -> totalIncome = totalIncome.add(balance);
                case EXPENSE -> totalExpense = totalExpense.add(balance);
            }
        }
        BigDecimal netIncome = totalIncome.subtract(totalExpense);
        totalEquity = totalEquity.add(netIncome);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("asOf", LocalDate.now()); result.put("assets", byType.get(ChartOfAccount.AccountType.ASSET));
        result.put("liabilities", byType.get(ChartOfAccount.AccountType.LIABILITY)); result.put("equity", byType.get(ChartOfAccount.AccountType.EQUITY));
        result.put("currentPeriodNetIncome", netIncome); result.put("totalAssets", totalAssets);
        result.put("totalLiabilities", totalLiabilities); result.put("totalEquity", totalEquity);
        result.put("balanced", totalAssets.compareTo(totalLiabilities.add(totalEquity)) == 0);
        return result;
    }

    public Map<String,Object> getProfitAndLoss(Long orgId, LocalDate from, LocalDate to) {
        List<JournalEntry> entries = journalRepo.findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(orgId, from, to);
        Map<String, BigDecimal> perAccount = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, ChartOfAccount.AccountType> types = new LinkedHashMap<>();
        for (JournalEntry e : entries) {
            if (Boolean.TRUE.equals(e.getReversed())) continue;
            for (JournalLine l : e.getLines()) {
                ChartOfAccount acc = l.getAccount();
                if (acc.getType() != ChartOfAccount.AccountType.INCOME && acc.getType() != ChartOfAccount.AccountType.EXPENSE) continue;
                BigDecimal debit = money(l.getDebit()), credit = money(l.getCredit());
                BigDecimal net = acc.getType() == ChartOfAccount.AccountType.INCOME ? credit.subtract(debit) : debit.subtract(credit);
                perAccount.merge(acc.getCode(), net, BigDecimal::add);
                names.put(acc.getCode(), acc.getName()); types.put(acc.getCode(), acc.getType());
            }
        }
        List<Map<String,Object>> income = new ArrayList<>(), expense = new ArrayList<>();
        BigDecimal totalIncome = ZERO, totalExpense = ZERO;
        for (var entry : perAccount.entrySet()) {
            Map<String,Object> row = new LinkedHashMap<>(); row.put("code", entry.getKey()); row.put("name", names.get(entry.getKey())); row.put("amount", entry.getValue());
            if (types.get(entry.getKey()) == ChartOfAccount.AccountType.INCOME) { income.add(row); totalIncome = totalIncome.add(entry.getValue()); }
            else { expense.add(row); totalExpense = totalExpense.add(entry.getValue()); }
        }
        Map<String,Object> result = new LinkedHashMap<>(); result.put("from", from); result.put("to", to);
        result.put("income", income); result.put("expense", expense); result.put("totalIncome", totalIncome); result.put("totalExpense", totalExpense);
        result.put("netIncome", totalIncome.subtract(totalExpense)); return result;
    }

    public Map<String,Object> getCashFlow(Long orgId, LocalDate from, LocalDate to) {
        List<JournalEntry> entries = journalRepo.findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(orgId, from, to);
        BigDecimal lending = ZERO, collections = ZERO, feesAndPenalties = ZERO, other = ZERO;
        for (JournalEntry e : entries) {
            if (Boolean.TRUE.equals(e.getReversed())) continue;
            for (JournalLine l : e.getLines()) {
                if (!"1000".equals(l.getAccount().getCode())) continue;
                BigDecimal net = money(l.getDebit()).subtract(money(l.getCredit()));
                switch (e.getSourceType() != null ? e.getSourceType() : "") {
                    case "LOAN_DISBURSEMENT" -> lending = lending.add(net);
                    case "PAYMENT_RECEIVED" -> collections = collections.add(net);
                    case "PROCESSING_FEE" -> feesAndPenalties = feesAndPenalties.add(net);
                    default -> other = other.add(net);
                }
            }
        }
        Map<String,Object> result = new LinkedHashMap<>(); result.put("from", from); result.put("to", to);
        result.put("cashUsedForLending", lending); result.put("cashFromCollections", collections); result.put("cashFromFees", feesAndPenalties);
        result.put("otherCashMovement", other); result.put("netChangeInCash", lending.add(collections).add(feesAndPenalties).add(other)); return result;
    }

    public List<Map<String,Object>> getBranchSummary(Long orgId, LocalDate from, LocalDate to) {
        List<JournalEntry> entries = journalRepo.findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(orgId, from, to);
        Map<String, BigDecimal[]> byBranch = new LinkedHashMap<>();
        for (JournalEntry e : entries) {
            if (Boolean.TRUE.equals(e.getReversed())) continue;
            String branchName = e.getBranchName() != null ? e.getBranchName() : "Unassigned";
            BigDecimal[] totals = byBranch.computeIfAbsent(branchName, k -> new BigDecimal[]{ZERO, ZERO, ZERO});
            BigDecimal debitTotal = e.getLines().stream().map(l -> money(l.getDebit())).reduce(ZERO, BigDecimal::add);
            switch (e.getSourceType() != null ? e.getSourceType() : "") {
                case "LOAN_DISBURSEMENT" -> totals[0] = totals[0].add(debitTotal);
                case "PAYMENT_RECEIVED" -> totals[1] = totals[1].add(debitTotal);
                case "PROCESSING_FEE" -> totals[2] = totals[2].add(debitTotal);
                default -> { }
            }
        }
        List<Map<String,Object>> rows = new ArrayList<>();
        for (var entry : byBranch.entrySet()) {
            Map<String,Object> row = new LinkedHashMap<>(); row.put("branch", entry.getKey());
            row.put("disbursed", entry.getValue()[0]); row.put("collected", entry.getValue()[1]); row.put("feeIncome", entry.getValue()[2]); rows.add(row);
        }
        return rows;
    }

    private BigDecimal netBalance(ChartOfAccount acc) {
        List<JournalLine> lines = lineRepo.findByAccount_Id(acc.getId());
        BigDecimal debit = lines.stream().map(l -> money(l.getDebit())).reduce(ZERO, BigDecimal::add);
        BigDecimal credit = lines.stream().map(l -> money(l.getCredit())).reduce(ZERO, BigDecimal::add);
        return acc.getNormalBalance() == ChartOfAccount.NormalBalance.DEBIT ? debit.subtract(credit) : credit.subtract(debit);
    }
}