package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.BankAccountRepository;
import com.patrick.fintech.loan_backend.repository.ChartOfAccountRepository;
import com.patrick.fintech.loan_backend.repository.JournalLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BankAccountService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private final BankAccountRepository bankAccountRepo;
    private final ChartOfAccountRepository coaRepo;
    private final JournalLineRepository lineRepo;
    private final AccountingService accountingService;

    /**
     * Create a bank/cash account and optionally post its opening balance.
     */
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

        if (!"CASH".equalsIgnoreCase(accountType)
                && !"BANK".equalsIgnoreCase(accountType)) {

            throw new IllegalArgumentException(
                    "accountType must be CASH or BANK"
            );
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Bank account name is required"
            );
        }

        if (openingBalance < 0) {
            throw new IllegalArgumentException(
                    "Opening balance cannot be negative"
            );
        }

        /*
         * Each bank/cash account gets its own GL sub-account.
         *
         * Examples:
         * 100001
         * 100002
         * 100003
         */
        long seq = bankAccountRepo.count() + 1;

        String code = "10" + String.format("%04d", seq);

        while (coaRepo.existsByOrganization_IdAndCode(
                org.getId(),
                code)) {

            seq++;
            code = "10" + String.format("%04d", seq);
        }

        ChartOfAccount glAccount =
                accountingService.createAccount(
                        org,
                        code,
                        name,
                        ChartOfAccount.AccountType.ASSET,
                        ChartOfAccount.NormalBalance.DEBIT
                );

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

        /*
         * Post opening balance into the general ledger.
         */
        if (openingBalance > 0) {

            BigDecimal opening =
                    money(openingBalance);

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
                                    .debit(opening)
                                    .credit(BigDecimal.ZERO)
                                    .description(
                                            "Opening balance — " + name
                                    )
                                    .build(),

                            JournalLine.builder()
                                    .account(
                                            accountingService
                                                    .getEquityAccount(org)
                                    )
                                    .debit(BigDecimal.ZERO)
                                    .credit(opening)
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

    /**
     * List all bank/cash accounts belonging to an organization.
     */
    public List<BankAccount> list(Long orgId) {

        return bankAccountRepo
                .findByOrganization_IdOrderByNameAsc(orgId);
    }

    /**
     * Get one bank/cash account belonging to an organization.
     */
    public BankAccount getForOrg(
            Long id,
            Long orgId) {

        return bankAccountRepo
                .findByIdAndOrganization_Id(id, orgId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Bank account not found: " + id
                        )
                );
    }

    /**
     * Current balance.
     *
     * The balance is calculated directly from the ledger.
     *
     * Debit increases the cash/bank asset.
     * Credit decreases the cash/bank asset.
     *
     * Everything remains BigDecimal until the final return.
     */
    public double getBalance(BankAccount account) {

        List<JournalLine> lines =
                lineRepo.findByAccount_Id(
                        account.getGlAccount().getId()
                );

        BigDecimal balance = BigDecimal.ZERO;

        for (JournalLine line : lines) {

            BigDecimal debit =
                    line.getDebit() != null
                            ? line.getDebit()
                            : BigDecimal.ZERO;

            BigDecimal credit =
                    line.getCredit() != null
                            ? line.getCredit()
                            : BigDecimal.ZERO;

            balance = balance
                    .add(debit)
                    .subtract(credit);
        }

        return balance
                .setScale(
                        MONEY_SCALE,
                        MONEY_ROUNDING
                )
                .doubleValue();
    }

    /**
     * Record a deposit or withdrawal against a counter-account.
     *
     * DEPOSIT:
     *     Dr Bank/Cash
     *     Cr Counter Account
     *
     * WITHDRAWAL:
     *     Dr Counter Account
     *     Cr Bank/Cash
     */
    @Transactional
    public JournalEntry recordTransaction(
            Organization org,
            Long bankAccountId,
            String type,
            double amount,
            Long counterAccountId,
            String description,
            String recordedBy) {

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

        BigDecimal amountValue =
                money(amount);

        List<JournalLine> lines;

        if (isDeposit) {

            /*
             * Deposit:
             *
             * Dr Bank/Cash
             * Cr Counter Account
             */
            lines = List.of(

                    JournalLine.builder()
                            .account(account.getGlAccount())
                            .debit(amountValue)
                            .credit(BigDecimal.ZERO)
                            .description(description)
                            .build(),

                    JournalLine.builder()
                            .account(counter)
                            .debit(BigDecimal.ZERO)
                            .credit(amountValue)
                            .description(description)
                            .build()
            );

        } else {

            /*
             * Withdrawal:
             *
             * Dr Counter Account
             * Cr Bank/Cash
             */
            lines = List.of(

                    JournalLine.builder()
                            .account(counter)
                            .debit(amountValue)
                            .credit(BigDecimal.ZERO)
                            .description(description)
                            .build(),

                    JournalLine.builder()
                            .account(account.getGlAccount())
                            .debit(BigDecimal.ZERO)
                            .credit(amountValue)
                            .description(description)
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
                        + (description != null
                        ? description
                        : ""),
                lines
        );
    }

    /**
     * Transfer money between two institution-owned accounts.
     *
     * Dr destination account
     * Cr source account
     */
    @Transactional
    public JournalEntry transfer(
            Organization org,
            Long fromAccountId,
            Long toAccountId,
            double amount,
            String description,
            String recordedBy) {

        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be positive"
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

        BigDecimal amountValue =
                money(amount);

        List<JournalLine> lines =
                List.of(

                        /*
                         * Destination receives money.
                         */
                        JournalLine.builder()
                                .account(
                                        to.getGlAccount()
                                )
                                .debit(amountValue)
                                .credit(BigDecimal.ZERO)
                                .description(
                                        "Transfer from "
                                                + from.getName()
                                )
                                .build(),

                        /*
                         * Source loses money.
                         */
                        JournalLine.builder()
                                .account(
                                        from.getGlAccount()
                                )
                                .debit(BigDecimal.ZERO)
                                .credit(amountValue)
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
                from.getName() + " -> " + to.getName(),
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

    /**
     * Convert a double to the application's accounting money type.
     *
     * All persisted/accounting calculations should use BigDecimal.
     */
    private BigDecimal money(double value) {

        return BigDecimal
                .valueOf(value)
                .setScale(
                        MONEY_SCALE,
                        MONEY_ROUNDING
                );
    }
}