
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.BankAccountRepository;
import com.patrick.fintech.loan_backend.repository.ChartOfAccountRepository;
import com.patrick.fintech.loan_backend.repository.JournalLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            String openedBy
    ) {

        if (org == null) {
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


        String normalizedType =
                accountType.toUpperCase().trim();


       
        long seq =
                bankAccountRepo.count() + 1;


        String code =
                buildGlCode(seq);


        while (
                coaRepo.existsByOrganization_IdAndCode(
                        org.getId(),
                        code
                )
        ) {

            seq++;

            code =
                    buildGlCode(seq);
        }


        /*
         * Create dedicated GL account.
         */
        ChartOfAccount glAccount =
                accountingService.createAccount(
                        org,
                        code,
                        name,
                        ChartOfAccount.AccountType.ASSET,
                        ChartOfAccount.NormalBalance.DEBIT
                );


        if (glAccount == null) {
            throw new IllegalStateException(
                    "Unable to create GL account for bank account"
            );
        }


        /*
         * Create bank/cash account.
         */
        BankAccount account =
                BankAccount.builder()
                        .organization(org)
                        .branch(branch)
                        .glAccount(glAccount)
                        .name(name)
                        .accountType(normalizedType)
                        .bankName(bankName)
                        .accountNumber(accountNumber)
                        .active(true)
                        .build();


        account =
                bankAccountRepo.save(account);


        if (account.getId() == null) {
            throw new IllegalStateException(
                    "Bank account was not assigned an ID"
            );
        }


        /*
         * Opening balance.
         */
        if (openingBalance > 0) {

            ChartOfAccount equityAccount =
                    accountingService.getEquityAccount(org);

            if (equityAccount == null) {
                throw new IllegalStateException(
                        "Equity account not found for organization"
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
                                            "Opening balance — " + name
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


    private String buildGlCode(long sequence) {

        return "10"
                + String.format(
                        "%04d",
                        sequence
                );
    }


    
@Transactional(readOnly = true)
public List<Map<String, Object>> listForApi(Long orgId) {

    List<BankAccount> accounts =
            bankAccountRepo.findByOrganization_IdOrderByNameAsc(orgId);

    return accounts.stream()
            .map(account -> {

                Map<String, Object> row = new LinkedHashMap<>();

                row.put("id", account.getId());
                row.put("name", account.getName());
                row.put("accountType", account.getAccountType());
                row.put("bankName", account.getBankName());
                row.put("accountNumber", account.getAccountNumber());

                // Boolean -> getActive(), NOT isActive()
                row.put(
                        "active",
                        account.getActive() != null
                                ? account.getActive()
                                : Boolean.FALSE
                );

                // Branch
                if (account.getBranch() != null) {
                    row.put("branchId", account.getBranch().getId());
                    row.put("branchName", account.getBranch().getName());
                } else {
                    row.put("branchId", null);
                    row.put("branchName", "Unassigned");
                }

                // Chart of Account
                if (account.getGlAccount() != null) {
                    row.put("glAccountId", account.getGlAccount().getId());
                    row.put("glAccountCode", account.getGlAccount().getCode());
                    row.put("glAccountName", account.getGlAccount().getName());
                } else {
                    row.put("glAccountId", null);
                    row.put("glAccountCode", null);
                    row.put("glAccountName", null);
                }

                return row;
            })
            .toList();
}


   
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
            Long orgId
    ) {

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
                                "Bank account not found: " + id
                        )
                );
    }


    // ============================================================
    // BALANCE
    // ============================================================

    @Transactional(readOnly = true)
    public double getBalance(
            BankAccount account
    ) {

        if (account == null) {
            return 0.0;
        }

        if (account.getGlAccount() == null) {
            return 0.0;
        }

        Long glId =
                account.getGlAccount().getId();

        if (glId == null) {
            return 0.0;
        }


        List<JournalLine> lines =
                lineRepo.findByAccount_Id(glId);


        if (lines == null || lines.isEmpty()) {
            return 0.0;
        }


        return lines.stream()
                .mapToDouble(line -> {

                    double debit =
                            line.getDebit() != null
                                    ? line.getDebit()
                                    : 0.0;

                    double credit =
                            line.getCredit() != null
                                    ? line.getCredit()
                                    : 0.0;

                    return debit - credit;
                })
                .sum();
    }


    // ============================================================
    // DEPOSIT / WITHDRAWAL
    // ============================================================

    @Transactional
    public JournalEntry recordTransaction(
            Organization org,
            Long bankAccountId,
            String type,
            double amount,
            Long counterAccountId,
            String description,
            String recordedBy
    ) {

        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be positive"
            );
        }


        BankAccount account =
                getForOrg(
                        bankAccountId,
                        org.getId()
                );


        if (account.getGlAccount() == null) {
            throw new IllegalStateException(
                    "Bank account has no GL account: "
                            + bankAccountId
            );
        }


        ChartOfAccount counter =
                coaRepo.findByIdAndOrganization_Id(
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


        String safeDescription =
                description != null
                        && !description.isBlank()
                        ? description
                        : "Cashbook transaction";


        List<JournalLine> lines;


        if (isDeposit) {

            lines =
                    List.of(

                            JournalLine.builder()
                                    .account(account.getGlAccount())
                                    .debit(amount)
                                    .credit(0.0)
                                    .description(safeDescription)
                                    .build(),

                            JournalLine.builder()
                                    .account(counter)
                                    .debit(0.0)
                                    .credit(amount)
                                    .description(safeDescription)
                                    .build()
                    );

        } else {

            lines =
                    List.of(

                            JournalLine.builder()
                                    .account(counter)
                                    .debit(amount)
                                    .credit(0.0)
                                    .description(safeDescription)
                                    .build(),

                            JournalLine.builder()
                                    .account(account.getGlAccount())
                                    .debit(0.0)
                                    .credit(amount)
                                    .description(safeDescription)
                                    .build()
                    );
        }


        return accountingService.post(
                org,
                account.getBranch(),
                "CASHBOOK_" + type.toUpperCase(),
                String.valueOf(bankAccountId),
                account.getName(),
                (recordedBy != null
                        ? recordedBy + ": "
                        : "")
                        + safeDescription,
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
            String recordedBy
    ) {

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


        if (from.getGlAccount() == null) {

            throw new IllegalStateException(
                    "Source bank account has no GL account: "
                            + fromAccountId
            );
        }


        if (to.getGlAccount() == null) {

            throw new IllegalStateException(
                    "Destination bank account has no GL account: "
                            + toAccountId
            );
        }


        String safeDescription =
                description != null
                        && !description.isBlank()
                        ? description
                        : "Internal transfer";


        List<JournalLine> lines =
                List.of(

                        JournalLine.builder()
                                .account(to.getGlAccount())
                                .debit(amount)
                                .credit(0.0)
                                .description(
                                        "Transfer from "
                                                + from.getName()
                                )
                                .build(),

                        JournalLine.builder()
                                .account(from.getGlAccount())
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
                fromAccountId + "->" + toAccountId,
                from.getName()
                        + " -> "
                        + to.getName(),
                (recordedBy != null
                        ? recordedBy + ": "
                        : "")
                        + safeDescription,
                lines
        );
    }
}
