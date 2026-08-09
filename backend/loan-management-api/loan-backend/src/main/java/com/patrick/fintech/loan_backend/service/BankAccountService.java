
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.BankAccount;
import com.patrick.fintech.loan_backend.model.Branch;
import com.patrick.fintech.loan_backend.model.ChartOfAccount;
import com.patrick.fintech.loan_backend.model.JournalEntry;
import com.patrick.fintech.loan_backend.model.JournalLine;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.BankAccountRepository;
import com.patrick.fintech.loan_backend.repository.ChartOfAccountRepository;
import com.patrick.fintech.loan_backend.repository.JournalLineRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankAccountService {

    private static final int MONEY_SCALE = 6;

    private static final RoundingMode MONEY_ROUNDING =
            RoundingMode.HALF_UP;

    private final BankAccountRepository bankAccountRepo;

    private final ChartOfAccountRepository coaRepo;

    private final JournalLineRepository lineRepo;

    private final AccountingService accountingService;


    // ============================================================
    // MONEY HELPERS
    // ============================================================

    /**
     * Converts a BigDecimal monetary value to the application's
     * standard accounting scale.
     */
    private BigDecimal money(BigDecimal value) {

        if (value == null) {
            return BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    MONEY_ROUNDING
            );
        }

        return value.setScale(
                MONEY_SCALE,
                MONEY_ROUNDING
        );
    }


    /**
     * Compatibility bridge for existing double-based callers.
     *
     * New financial code should use BigDecimal directly.
     */
    private BigDecimal money(double value) {

        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "Monetary amount must be a finite number"
            );
        }

        return money(
                BigDecimal.valueOf(value)
        );
    }


    private void requireOrganization(
            Organization org
    ) {

        if (org == null) {

            throw new IllegalArgumentException(
                    "Organization is required"
            );
        }

        if (org.getId() == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }
    }


    private BigDecimal requirePositive(
            BigDecimal amount,
            String field
    ) {

        BigDecimal normalized =
                money(amount);

        if (normalized.compareTo(
                BigDecimal.ZERO
        ) <= 0) {

            throw new IllegalArgumentException(
                    field + " must be greater than zero"
            );
        }

        return normalized;
    }


    private BigDecimal requireNonNegative(
            BigDecimal amount,
            String field
    ) {

        BigDecimal normalized =
                money(amount);

        if (normalized.compareTo(
                BigDecimal.ZERO
        ) < 0) {

            throw new IllegalArgumentException(
                    field + " cannot be negative"
            );
        }

        return normalized;
    }


    // ============================================================
    // CREATE
    // ============================================================

    /**
     * Production BigDecimal version.
     */
    @Transactional
    public BankAccount create(
            Organization org,
            Branch branch,
            String name,
            String accountType,
            String bankName,
            String accountNumber,
            BigDecimal openingBalance,
            String openedBy
    ) {

        requireOrganization(org);

        if (name == null || name.isBlank()) {

            throw new IllegalArgumentException(
                    "Account name is required"
            );
        }

        if (accountType == null
                || accountType.isBlank()) {

            throw new IllegalArgumentException(
                    "Account type is required"
            );
        }

        if (!"CASH".equalsIgnoreCase(accountType)
                && !"BANK".equalsIgnoreCase(accountType)) {

            throw new IllegalArgumentException(
                    "accountType must be CASH or BANK"
            );
        }

        BigDecimal opening =
                requireNonNegative(
                        openingBalance,
                        "Opening balance"
                );


        String normalizedType =
                accountType
                        .trim()
                        .toUpperCase();


        /*
         * Generate a GL code.
         *
         * We use the existing bank-account count as the
         * starting sequence and then verify uniqueness
         * against the organization's chart of accounts.
         */
        long sequence =
                bankAccountRepo.count() + 1;


        String code =
                buildGlCode(sequence);


        while (
                coaRepo.existsByOrganization_IdAndCode(
                        org.getId(),
                        code
                )
        ) {

            sequence++;

            code =
                    buildGlCode(sequence);
        }


        /*
         * Create dedicated GL account.
         *
         * Bank and cash accounts are assets with debit
         * normal balances.
         */
        ChartOfAccount glAccount =
                accountingService.createAccount(
                        org,
                        code,
                        name.trim(),
                        ChartOfAccount.AccountType.ASSET,
                        ChartOfAccount.NormalBalance.DEBIT
                );


        if (glAccount == null) {

            throw new IllegalStateException(
                    "Unable to create GL account for bank account"
            );
        }


        /*
         * Create the operational bank/cash account.
         */
        BankAccount account =
                BankAccount.builder()
                        .organization(org)
                        .branch(branch)
                        .glAccount(glAccount)
                        .name(name.trim())
                        .accountType(normalizedType)
                        .bankName(
                                bankName != null
                                        ? bankName.trim()
                                        : null
                        )
                        .accountNumber(
                                accountNumber != null
                                        ? accountNumber.trim()
                                        : null
                        )
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
         * Opening balance accounting:
         *
         * DR Bank/Cash Asset
         * CR Owner's Equity
         *
         * This is intentionally posted only when the
         * opening balance is greater than zero.
         */
        if (opening.compareTo(
                BigDecimal.ZERO
        ) > 0) {

            ChartOfAccount equityAccount =
                    accountingService.getEquityAccount(
                            org
                    );


            if (equityAccount == null) {

                throw new IllegalStateException(
                        "Equity account not found for organization"
                );
            }


            accountingService.post(
                    org,
                    branch,
                    "BANK_ACCOUNT_OPENING",
                    String.valueOf(
                            account.getId()
                    ),
                    name.trim(),
                    "Opening balance for " +
                            name.trim(),

                    List.of(

                            JournalLine.builder()
                                    .account(glAccount)
                                    .debit(opening)
                                    .credit(BigDecimal.ZERO)
                                    .description(
                                            "Opening balance — " +
                                                    name.trim()
                                    )
                                    .build(),

                            JournalLine.builder()
                                    .account(equityAccount)
                                    .debit(BigDecimal.ZERO)
                                    .credit(opening)
                                    .description(
                                            "Opening balance funding — " +
                                                    name.trim()
                                    )
                                    .build()
                    )
            );
        }


        log.info(
                "Created {} account {} for organization {}",
                normalizedType,
                account.getId(),
                org.getId()
        );


        return account;
    }


    /**
     * Backward-compatible double overload.
     *
     * Existing callers can continue using double while
     * the actual accounting operation is converted to
     * BigDecimal immediately.
     *
     * New code should use the BigDecimal overload.
     */
    @Deprecated
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

        return create(
                org,
                branch,
                name,
                accountType,
                bankName,
                accountNumber,
                money(openingBalance),
                openedBy
        );
    }


    private String buildGlCode(
            long sequence
    ) {

        return "10"
                + String.format(
                        "%04d",
                        sequence
                );
    }


    // ============================================================
    // LIST FOR API
    // ============================================================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForApi(
            Long orgId
    ) {

        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }


        List<BankAccount> accounts =
                bankAccountRepo
                        .findByOrganization_IdOrderByNameAsc(
                                orgId
                        );


        if (accounts == null
                || accounts.isEmpty()) {

            return List.of();
        }


        return accounts.stream()
                .map(account -> {

                    Map<String, Object> row =
                            new LinkedHashMap<>();


                    row.put(
                            "id",
                            account.getId()
                    );

                    row.put(
                            "name",
                            account.getName()
                    );

                    row.put(
                            "accountType",
                            account.getAccountType()
                    );

                    row.put(
                            "bankName",
                            account.getBankName()
                    );

                    row.put(
                            "accountNumber",
                            account.getAccountNumber()
                    );


                    row.put(
                            "active",
                            account.getActive() != null
                                    ? account.getActive()
                                    : Boolean.FALSE
                    );


                    /*
                     * Branch.
                     */
                    if (account.getBranch() != null) {

                        row.put(
                                "branchId",
                                account.getBranch().getId()
                        );

                        row.put(
                                "branchName",
                                account.getBranch().getName()
                        );

                    } else {

                        row.put(
                                "branchId",
                                null
                        );

                        row.put(
                                "branchName",
                                "Unassigned"
                        );
                    }


                    /*
                     * Chart of Account.
                     */
                    if (account.getGlAccount() != null) {

                        row.put(
                                "glAccountId",
                                account
                                        .getGlAccount()
                                        .getId()
                        );

                        row.put(
                                "glAccountCode",
                                account
                                        .getGlAccount()
                                        .getCode()
                        );

                        row.put(
                                "glAccountName",
                                account
                                        .getGlAccount()
                                        .getName()
                        );

                    } else {

                        row.put(
                                "glAccountId",
                                null
                        );

                        row.put(
                                "glAccountCode",
                                null
                        );

                        row.put(
                                "glAccountName",
                                null
                        );
                    }


                    return row;
                })
                .toList();
    }


    // ============================================================
    // LIST
    // ============================================================

    @Transactional(readOnly = true)
    public List<BankAccount> list(
            Long orgId
    ) {

        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }


        return bankAccountRepo
                .findByOrganization_IdOrderByNameAsc(
                        orgId
                );
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
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Bank account not found: " +
                                                id
                                )
                );
    }


    // ============================================================
    // BALANCE - BIGDECIMAL
    // ============================================================

    /**
     * Production balance method.
     *
     * Bank/cash accounts are debit-normal assets:
     *
     * Balance = Total Debits - Total Credits
     */
    @Transactional(readOnly = true)
    public BigDecimal getBalanceDecimal(
            BankAccount account
    ) {

        if (account == null) {

            return BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    MONEY_ROUNDING
            );
        }


        if (account.getGlAccount() == null) {

            return BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    MONEY_ROUNDING
            );
        }


        Long glId =
                account
                        .getGlAccount()
                        .getId();


        if (glId == null) {

            return BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    MONEY_ROUNDING
            );
        }


        List<JournalLine> lines =
                lineRepo.findByAccount_Id(
                        glId
                );


        if (lines == null
                || lines.isEmpty()) {

            return BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    MONEY_ROUNDING
            );
        }


        BigDecimal balance =
                BigDecimal.ZERO.setScale(
                        MONEY_SCALE,
                        MONEY_ROUNDING
                );


        for (JournalLine line :
                lines) {

            if (line == null) {
                continue;
            }


            JournalEntry entry =
                    line.getJournalEntry();


            /*
             * Reversed original entries are excluded.
             *
             * The reversal journal entry remains active and
             * therefore continues to affect the balance.
             */
            if (
                    entry != null
                            && Boolean.TRUE.equals(
                            entry.getReversed()
                    )
            ) {

                continue;
            }


            BigDecimal debit =
                    money(
                            line.getDebitDecimal()
                    );


            BigDecimal credit =
                    money(
                            line.getCreditDecimal()
                    );


            balance =
                    balance
                            .add(debit)
                            .subtract(credit);
        }


        return money(balance);
    }


    /**
     * Backward-compatible balance method.
     *
     * Existing callers that expect double can continue
     * working. New financial code should use getBalanceDecimal().
     */
    @Transactional(readOnly = true)
    public double getBalance(
            BankAccount account
    ) {

        return getBalanceDecimal(
                account
        ).doubleValue();
    }


    // ============================================================
    // DEPOSIT / WITHDRAWAL
    // ============================================================

    /**
     * Production BigDecimal implementation.
     */
    @Transactional
    public JournalEntry recordTransaction(
            Organization org,
            Long bankAccountId,
            String type,
            BigDecimal amount,
            Long counterAccountId,
            String description,
            String recordedBy
    ) {

        requireOrganization(org);


        if (bankAccountId == null) {

            throw new IllegalArgumentException(
                    "Bank account ID is required"
            );
        }


        if (counterAccountId == null) {

            throw new IllegalArgumentException(
                    "Counter account ID is required"
            );
        }


        BigDecimal transactionAmount =
                requirePositive(
                        amount,
                        "Amount"
                );


        if (type == null
                || type.isBlank()) {

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
                    "Bank account has no GL account: " +
                            bankAccountId
            );
        }


        if (Boolean.FALSE.equals(
                account.getActive()
        )) {

            throw new IllegalStateException(
                    "Bank account is inactive: " +
                            bankAccountId
            );
        }


        ChartOfAccount counter =
                coaRepo
                        .findByIdAndOrganization_Id(
                                counterAccountId,
                                org.getId()
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Counter account not found: " +
                                                        counterAccountId
                                        )
                        );


        boolean isDeposit =
                "DEPOSIT".equalsIgnoreCase(
                        type.trim()
                );


        boolean isWithdrawal =
                "WITHDRAWAL".equalsIgnoreCase(
                        type.trim()
                );


        if (!isDeposit
                && !isWithdrawal) {

            throw new IllegalArgumentException(
                    "type must be DEPOSIT or WITHDRAWAL"
            );
        }


        String safeDescription =
                description != null
                        && !description.isBlank()
                        ? description.trim()
                        : "Cashbook transaction";


        List<JournalLine> lines;


        if (isDeposit) {

            /*
             * DEPOSIT
             *
             * DR Bank/Cash
             * CR Counter Account
             */
            lines =
                    List.of(

                            JournalLine.builder()
                                    .account(
                                            account.getGlAccount()
                                    )
                                    .debit(
                                            transactionAmount
                                    )
                                    .credit(
                                            BigDecimal.ZERO
                                    )
                                    .description(
                                            safeDescription
                                    )
                                    .build(),

                            JournalLine.builder()
                                    .account(counter)
                                    .debit(
                                            BigDecimal.ZERO
                                    )
                                    .credit(
                                            transactionAmount
                                    )
                                    .description(
                                            safeDescription
                                    )
                                    .build()
                    );

        } else {

            /*
             * WITHDRAWAL
             *
             * DR Counter Account
             * CR Bank/Cash
             */
            lines =
                    List.of(

                            JournalLine.builder()
                                    .account(counter)
                                    .debit(
                                            transactionAmount
                                    )
                                    .credit(
                                            BigDecimal.ZERO
                                    )
                                    .description(
                                            safeDescription
                                    )
                                    .build(),

                            JournalLine.builder()
                                    .account(
                                            account.getGlAccount()
                                    )
                                    .debit(
                                            BigDecimal.ZERO
                                    )
                                    .credit(
                                            transactionAmount
                                    )
                                    .description(
                                            safeDescription
                                    )
                                    .build()
                    );
        }


        return accountingService.post(
                org,
                account.getBranch(),
                "CASHBOOK_" +
                        type.trim().toUpperCase(),
                String.valueOf(
                        bankAccountId
                ),
                account.getName(),
                (recordedBy != null
                        && !recordedBy.isBlank()
                        ? recordedBy.trim() + ": "
                        : "")
                        + safeDescription,
                lines
        );
    }


    /**
     * Backward-compatible double implementation.
     *
     * Converts to BigDecimal immediately and delegates to
     * the production implementation.
     */
    @Deprecated
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

        return recordTransaction(
                org,
                bankAccountId,
                type,
                money(amount),
                counterAccountId,
                description,
                recordedBy
        );
    }


    // ============================================================
    // TRANSFER
    // ============================================================

    /**
     * Production BigDecimal implementation.
     *
     * Internal transfer:
     *
     * DR Destination Bank/Cash
     * CR Source Bank/Cash
     */
    @Transactional
    public JournalEntry transfer(
            Organization org,
            Long fromAccountId,
            Long toAccountId,
            BigDecimal amount,
            String description,
            String recordedBy
    ) {

        requireOrganization(org);


        if (fromAccountId == null
                || toAccountId == null) {

            throw new IllegalArgumentException(
                    "Both source and destination accounts are required"
            );
        }


        if (fromAccountId.equals(
                toAccountId
        )) {

            throw new IllegalArgumentException(
                    "Cannot transfer an account to itself"
            );
        }


        BigDecimal transferAmount =
                requirePositive(
                        amount,
                        "Amount"
                );


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


        if (Boolean.FALSE.equals(
                from.getActive()
        )) {

            throw new IllegalStateException(
                    "Source bank account is inactive: " +
                            fromAccountId
            );
        }


        if (Boolean.FALSE.equals(
                to.getActive()
        )) {

            throw new IllegalStateException(
                    "Destination bank account is inactive: " +
                            toAccountId
            );
        }


        if (from.getGlAccount() == null) {

            throw new IllegalStateException(
                    "Source bank account has no GL account: " +
                            fromAccountId
            );
        }


        if (to.getGlAccount() == null) {

            throw new IllegalStateException(
                    "Destination bank account has no GL account: " +
                            toAccountId
            );
        }


        String safeDescription =
                description != null
                        && !description.isBlank()
                        ? description.trim()
                        : "Internal transfer";


        List<JournalLine> lines =
                List.of(

                        /*
                         * DR Destination account.
                         */
                        JournalLine.builder()
                                .account(
                                        to.getGlAccount()
                                )
                                .debit(
                                        transferAmount
                                )
                                .credit(
                                        BigDecimal.ZERO
                                )
                                .description(
                                        "Transfer from " +
                                                from.getName()
                                )
                                .build(),

                        /*
                         * CR Source account.
                         */
                        JournalLine.builder()
                                .account(
                                        from.getGlAccount()
                                )
                                .debit(
                                        BigDecimal.ZERO
                                )
                                .credit(
                                        transferAmount
                                )
                                .description(
                                        "Transfer to " +
                                                to.getName()
                                )
                                .build()
                );


        return accountingService.post(
                org,
                from.getBranch(),
                "CASHBOOK_TRANSFER",
                fromAccountId +
                        "->" +
                        toAccountId,
                from.getName() +
                        " -> " +
                        to.getName(),
                (recordedBy != null
                        && !recordedBy.isBlank()
                        ? recordedBy.trim() + ": "
                        : "")
                        + safeDescription,
                lines
        );
    }


    /**
     * Backward-compatible double implementation.
     */
    @Deprecated
    @Transactional
    public JournalEntry transfer(
            Organization org,
            Long fromAccountId,
            Long toAccountId,
            double amount,
            String description,
            String recordedBy
    ) {

        return transfer(
                org,
                fromAccountId,
                toAccountId,
                money(amount),
                description,
                recordedBy
        );
    }
}
