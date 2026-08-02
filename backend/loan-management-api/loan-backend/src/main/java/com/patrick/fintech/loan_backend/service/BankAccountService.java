package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.BankAccountRepository;
import com.patrick.fintech.loan_backend.repository.ChartOfAccountRepository;
import com.patrick.fintech.loan_backend.repository.JournalLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository bankAccountRepo;
    private final ChartOfAccountRepository coaRepo;
    private final JournalLineRepository lineRepo;
    private final AccountingService accountingService;


    // ============================================================
    // CREATE
    // ============================================================

    @Transactional
    public BankAccount create(
            Organization org,
            Branch branch,
            String name,
            String accountType,
            String bankName,
            String accountNumber,
            double openingBalance,
            String openedBy) {

        if (org == null || org.getId() == null) {
            throw new IllegalArgumentException(
                    "Organization is required"
            );
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Account name is required"
            );
        }

        if (!"CASH".equalsIgnoreCase(accountType)
                && !"BANK".equalsIgnoreCase(accountType)) {

            throw new IllegalArgumentException(
                    "accountType must be CASH or BANK"
            );
        }

        if (openingBalance < 0) {
            throw new IllegalArgumentException(
                    "Opening balance cannot be negative"
            );
        }

        /*
         * Make sure the branch belongs to the same organization.
         */
        if (branch != null) {

            if (branch.getOrganization() == null
                    || branch.getOrganization().getId() == null
                    || !branch.getOrganization()
                              .getId()
                              .equals(org.getId())) {

                throw new IllegalArgumentException(
                        "Branch does not belong to the current organization"
                );
            }
        }


        // ========================================================
        // GENERATE UNIQUE GL CODE
        // ========================================================

        long seq =
                bankAccountRepo.count() + 1;

        String code =
                "10" + String.format("%04d", seq);

        while (
                coaRepo.existsByOrganization_IdAndCode(
                        org.getId(),
                        code
                )
        ) {

            seq++;

            code =
                    "10"
                            + String.format(
                                    "%04d",
                                    seq
                            );
        }


        // ========================================================
        // CREATE GL ACCOUNT
        // ========================================================

        ChartOfAccount glAccount =
                accountingService.createAccount(
                        org,
                        code,
                        name,
                        ChartOfAccount.AccountType.ASSET,
                        ChartOfAccount.NormalBalance.DEBIT
                );

        if (glAccount == null
                || glAccount.getId() == null) {

            throw new IllegalStateException(
                    "Failed to create GL account for bank account"
            );
        }


        // ========================================================
        // CREATE BANK ACCOUNT
        // ========================================================

        BankAccount account =
                bankAccountRepo.save(
                        BankAccount.builder()
                                .organization(org)
                                .branch(branch)
                                .glAccount(glAccount)
                                .name(name)
                                .accountType(
                                        accountType.toUpperCase()
                                )
                                .bankName(bankName)
                                .accountNumber(accountNumber)
                                .active(true)
                                .build()
                );


        // ========================================================
        // OPENING BALANCE
        // ========================================================

        if (openingBalance > 0) {

            ChartOfAccount equityAccount =
                    accountingService.getEquityAccount(
                            org
                    );

            if (equityAccount == null) {
                throw new IllegalStateException(
                        "Equity account not configured for organization"
                );
            }

            accountingService.post(
                    org,
                    branch,
                    "BANK_ACCOUNT_OPENING",
                    String.valueOf(account.getId()),
                    name,
                    "Opening balance for " + name,

                    List.of(

                            JournalLine.builder()
                                    .account(glAccount)
                                    .debit(openingBalance)
                                    .credit(0.0)
                                    .description(
                                            "Opening balance — "
                                                    + name
                                    )
                                    .build(),

                            JournalLine.builder()
                                    .account(equityAccount)
                                    .debit(0.0)
                                    .credit(openingBalance)
                                    .description(
                                            "Opening balance funding — "
                                                    + name
                                    )
                                    .build()
                    )
            );
        }

        return account;
    }


    // ============================================================
    // LIST
    // ============================================================

    @Transactional(readOnly = true)
    public List<BankAccount> list(Long orgId) {

        if (orgId == null) {
            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }

        return bankAccountRepo
                .findByOrganization_IdOrderByNameAsc(orgId);
    }


    // ============================================================
    // GET FOR ORGANIZATION
    // ============================================================

    @Transactional(readOnly = true)
    public BankAccount getForOrg(
            Long id,
            Long orgId) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "Bank account ID is required"
            );
        }

        if (orgId == null) {
            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }

        return bankAccountRepo
                .findByIdAndOrganization_Id(
                        id,
                        orgId
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Bank account not found: "
                                        + id
                        )
                );
    }


    // ============================================================
    // BALANCE
    // ============================================================

    @Transactional(readOnly = true)
    public double getBalance(
            BankAccount account) {

        if (account == null) {
            throw new IllegalArgumentException(
                    "Bank account is required"
            );
        }

        if (account.getGlAccount() == null
                || account.getGlAccount().getId() == null) {

            throw new IllegalStateException(
                    "Bank account "
                            + account.getId()
                            + " has no GL account"
            );
        }

        List<JournalLine> lines =
                lineRepo.findByAccount_Id(
                        account.getGlAccount().getId()
                );

        return lines.stream()
                .mapToDouble(line ->
                        (line.getDebit() != null
                                ? line.getDebit()
                                : 0.0)
                        -
                        (line.getCredit() != null
                                ? line.getCredit()
                                : 0.0)
                )
                .sum();
    }


    // ============================================================
    // RECORD TRANSACTION
    // ============================================================

    @Transactional
    public JournalEntry recordTransaction(
            Organization org,
            Long bankAccountId,
            String type,
            double amount,
            Long counterAccountId,
            String description,
            String recordedBy) {

        if (org == null || org.getId() == null) {
            throw new IllegalArgumentException(
                    "Organization is required"
            );
        }

        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be positive"
            );
        }

        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException(
                    "Transaction type is required"
            );
        }

        BankAccount account =
                getForOrg(
                        bankAccountId,
                        org.getId()
                );

        if (account.getGlAccount() == null) {
            throw new IllegalStateException(
                    "Bank account has no GL account"
            );
        }

        if (counterAccountId == null) {
            throw new IllegalArgumentException(
                    "Counter account is required"
            );
        }

        ChartOfAccount counter =
                coaRepo
                        .findByIdAndOrganization_Id(
                                counterAccountId,
                                org.getId()
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Counter account not found: "
                                                + counterAccountId
                                )
                        );

        boolean isDeposit =
                "DEPOSIT".equalsIgnoreCase(type);

        boolean isWithdrawal =
                "WITHDRAWAL".equalsIgnoreCase(type);

        if (!isDeposit && !isWithdrawal) {
            throw new IllegalArgumentException(
                    "type must be DEPOSIT or WITHDRAWAL"
            );
        }


        List<JournalLine> lines;

        if (isDeposit) {

            lines =
                    List.of(

                            JournalLine.builder()
                                    .account(
                                            account.getGlAccount()
                                    )
                                    .debit(amount)
                                    .credit(0.0)
                                    .description(description)
                                    .build(),

                            JournalLine.builder()
                                    .account(counter)
                                    .debit(0.0)
                                    .credit(amount)
                                    .description(description)
                                    .build()
                    );

        } else {

            lines =
                    List.of(

                            JournalLine.builder()
                                    .account(counter)
                                    .debit(amount)
                                    .credit(0.0)
                                    .description(description)
                                    .build(),

                            JournalLine.builder()
                                    .account(
                                            account.getGlAccount()
                                    )
                                    .debit(0.0)
                                    .credit(amount)
                                    .description(description)
                                    .build()
                    );
        }


        return accountingService.post(
                org,
                account.getBranch(),
                "CASHBOOK_"
                        + type.toUpperCase(),
                String.valueOf(bankAccountId),
                account.getName(),
                (recordedBy != null
                        ? recordedBy + ": "
                        : "")
                        + description,
                lines
        );
    }


    // ============================================================
    // TRANSFER
    // ============================================================

    @Transactional
    public JournalEntry transfer(
            Organization org,
            Long fromAccountId,
            Long toAccountId,
            double amount,
            String description,
            String recordedBy) {

        if (org == null || org.getId() == null) {
            throw new IllegalArgumentException(
                    "Organization is required"
            );
        }

        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be positive"
            );
        }

        if (fromAccountId == null
                || toAccountId == null) {

            throw new IllegalArgumentException(
                    "Both source and destination accounts are required"
            );
        }

        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException(
                    "Cannot transfer an account to itself"
            );
        }

        BankAccount from =
                getForOrg(
                        fromAccountId,
                        org.getId()
                );

        BankAccount to =
                getForOrg(
                        toAccountId,
                        org.getId()
                );

        if (from.getGlAccount() == null
                || to.getGlAccount() == null) {

            throw new IllegalStateException(
                    "Both bank accounts must have GL accounts"
            );
        }


        List<JournalLine> lines =
                List.of(

                        JournalLine.builder()
                                .account(
                                        to.getGlAccount()
                                )
                                .debit(amount)
                                .credit(0.0)
                                .description(
                                        "Transfer from "
                                                + from.getName()
                                )
                                .build(),

                        JournalLine.builder()
                                .account(
                                        from.getGlAccount()
                                )
                                .debit(0.0)
                                .credit(amount)
                                .description(
                                        "Transfer to "
                                                + to.getName()
                                )
                                .build()
                );


        return accountingService.post(
                org,
                from.getBranch(),
                "CASHBOOK_TRANSFER",
                fromAccountId
                        + "->"
                        + toAccountId,
                from.getName()
                        + " -> "
                        + to.getName(),
                (recordedBy != null
                        ? recordedBy + ": "
                        : "")
                        + (
                        description != null
                                && !description.isBlank()
                                ? description
                                : "Internal transfer"
                ),
                lines
        );
    }
}
