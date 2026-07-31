package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.BankAccountRepository;
import com.patrick.fintech.loan_backend.repository.ChartOfAccountRepository;
import com.patrick.fintech.loan_backend.repository.JournalLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Cashbook & Bank Management (Phase 5). An institution can hold several actual cash/bank
 * accounts (a head-office bank account, a branch's petty cash drawer, a mobile money float).
 * Each one gets its own dedicated ChartOfAccount sub-ledger — separate from the single generic
 * "1000 Cash and Bank" account that automatic loan postings (disbursement, payment, fees) use —
 * so it can be reconciled and reported on individually, the way a real cashbook works.
 *
 * Deliberately NOT wired into loan disbursement/payment postings: retrofitting those to target
 * a specific bank account instead of the generic 1000 account is a real migration (which account
 * is "the" operating account? per branch?) that belongs to a deliberate follow-up, not something
 * to silently change while adding cashbook management.
 */
@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository    bankAccountRepo;
    private final ChartOfAccountRepository coaRepo;
    private final JournalLineRepository    lineRepo;
    private final AccountingService        accountingService;

    @Transactional
    public BankAccount create(Organization org, Branch branch, String name, String accountType,
                              String bankName, String accountNumber, double openingBalance, String openedBy) {
        if (!"CASH".equals(accountType) && !"BANK".equals(accountType))
            throw new IllegalArgumentException("accountType must be CASH or BANK");

        // Each bank/cash account is its own GL sub-account, code "10<id-padded>" e.g. 100001,
        // 100002 — distinct from the "1000" head account so trial balance/balance sheet still
        // pick it up automatically as just another ASSET account.
        long seq = bankAccountRepo.count() + 1;
        String code = "10" + String.format("%04d", seq);
        while (coaRepo.existsByOrganization_IdAndCode(org.getId(), code)) {
            seq++;
            code = "10" + String.format("%04d", seq);
        }
        ChartOfAccount glAccount = accountingService.createAccount(
            org, code, name, ChartOfAccount.AccountType.ASSET, ChartOfAccount.NormalBalance.DEBIT);

        BankAccount account = bankAccountRepo.save(BankAccount.builder()
            .organization(org).branch(branch).glAccount(glAccount)
            .name(name).accountType(accountType).bankName(bankName).accountNumber(accountNumber)
            .active(true).build());

        if (openingBalance > 0) {
            accountingService.post(org, branch, "BANK_ACCOUNT_OPENING", String.valueOf(account.getId()), name,
                "Opening balance for " + name,
                List.of(
                    JournalLine.builder().account(glAccount).debit(openingBalance).credit(0.0)
                        .description("Opening balance — " + name).build(),
                    JournalLine.builder().account(accountingService.getEquityAccount(org)).debit(0.0).credit(openingBalance)
                        .description("Opening balance funding — " + name).build()
                ));
        }
        return account;
    }

    public List<BankAccount> list(Long orgId) {
        return bankAccountRepo.findByOrganization_IdOrderByNameAsc(orgId);
    }

    public BankAccount getForOrg(Long id, Long orgId) {
        return bankAccountRepo.findByIdAndOrganization_Id(id, orgId)
            .orElseThrow(() -> new IllegalArgumentException("Bank account not found: " + id));
    }

    /** Current balance — always derived live from the ledger, never stored, so it can never
     *  drift out of sync with the journal the way a cached balance field could. */
    public double getBalance(BankAccount account) {
        List<JournalLine> lines = lineRepo.findByAccount_Id(account.getGlAccount().getId());
        return lines.stream().mapToDouble(l ->
            (l.getDebit() != null ? l.getDebit() : 0) - (l.getCredit() != null ? l.getCredit() : 0)).sum();
    }

    /** A deposit or withdrawal against one account, posted to a chosen counter-account
     *  (e.g. an expense account for a withdrawal that pays rent, an income/equity account for
     *  a deposit that's a capital injection) — a manual cashbook entry, not a loan transaction. */
    @Transactional
    public JournalEntry recordTransaction(Organization org, Long bankAccountId, String type,
                                           double amount, Long counterAccountId, String description, String recordedBy) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        BankAccount account = getForOrg(bankAccountId, org.getId());
        ChartOfAccount counter = coaRepo.findByIdAndOrganization_Id(counterAccountId, org.getId())
            .orElseThrow(() -> new IllegalArgumentException("Counter account not found: " + counterAccountId));

        boolean isDeposit = "DEPOSIT".equalsIgnoreCase(type);
        boolean isWithdrawal = "WITHDRAWAL".equalsIgnoreCase(type);
        if (!isDeposit && !isWithdrawal) throw new IllegalArgumentException("type must be DEPOSIT or WITHDRAWAL");

        List<JournalLine> lines = isDeposit
            ? List.of(
                JournalLine.builder().account(account.getGlAccount()).debit(amount).credit(0.0)
                    .description(description).build(),
                JournalLine.builder().account(counter).debit(0.0).credit(amount)
                    .description(description).build())
            : List.of(
                JournalLine.builder().account(counter).debit(amount).credit(0.0)
                    .description(description).build(),
                JournalLine.builder().account(account.getGlAccount()).debit(0.0).credit(amount)
                    .description(description).build());

        return accountingService.post(org, account.getBranch(), "CASHBOOK_" + type.toUpperCase(),
            String.valueOf(bankAccountId), account.getName(),
            (recordedBy != null ? recordedBy + ": " : "") + description, lines);
    }

    /** Moves cash between two of the institution's own accounts (e.g. branch cash drawer to
     *  head-office bank account) — both sides are the institution's own money, so this never
     *  touches an income/expense account, unlike recordTransaction above. */
    @Transactional
    public JournalEntry transfer(Organization org, Long fromAccountId, Long toAccountId, double amount, String description, String recordedBy) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        if (fromAccountId.equals(toAccountId)) throw new IllegalArgumentException("Cannot transfer an account to itself");
        BankAccount from = getForOrg(fromAccountId, org.getId());
        BankAccount to   = getForOrg(toAccountId, org.getId());

        List<JournalLine> lines = List.of(
            JournalLine.builder().account(to.getGlAccount()).debit(amount).credit(0.0)
                .description("Transfer from " + from.getName()).build(),
            JournalLine.builder().account(from.getGlAccount()).debit(0.0).credit(amount)
                .description("Transfer to " + to.getName()).build()
        );
        return accountingService.post(org, from.getBranch(), "CASHBOOK_TRANSFER",
            fromAccountId + "->" + toAccountId, from.getName() + " -> " + to.getName(),
            (recordedBy != null ? recordedBy + ": " : "") + (description != null ? description : "Internal transfer"),
            lines);
    }
}
