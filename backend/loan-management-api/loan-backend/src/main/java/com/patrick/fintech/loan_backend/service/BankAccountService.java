
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

    /*
     * ============================================================
     * MONEY CONFIGURATION
     * ============================================================
     */

    private static final int MONEY_SCALE = 6;

    private static final RoundingMode MONEY_ROUNDING =
            RoundingMode.HALF_UP;

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    MONEY_ROUNDING
            );

    /*
     * GL account codes for bank/cash accounts.
     *
     * 10xxxx = cash/bank assets.
     */
    private static final String GL_CODE_PREFIX = "10";


    private final BankAccountRepository bankAccountRepo;

    private final ChartOfAccountRepository coaRepo;

    private final JournalLineRepository lineRepo;

    private final AccountingService accountingService;


    /*
     * ============================================================
     * MONEY HELPERS
     * ============================================================
     */

    private BigDecimal money(
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


    /**
     * Compatibility bridge for legacy callers.
     *
     * New financial code must use BigDecimal.
     */
    private BigDecimal money(
            double value
    ) {

        if (!Double.isFinite(value)) {

            throw new IllegalArgumentException(
                    "Monetary amount must be finite"
            );
        }

        return money(
                BigDecimal.valueOf(value)
        );
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
                    field +
                            " must be greater than zero"
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
                    field +
                            " cannot be negative"
            );
        }

        return normalized;
    }


    private void requireOrganization(
            Organization organization
    ) {

        if (organization == null) {

            throw new IllegalArgumentException(
                    "Organization is required"
            );
        }

        if (organization.getId() == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }
    }


    private String requireText(
            String value,
            String field
    ) {

        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    field +
                            " is required"
            );
        }

        return value.trim();
    }


    private String optionalText(
            String value
    ) {

        if (value == null
                || value.isBlank()) {

            return null;
        }

        return value.trim();
    }


    /*
     * ============================================================
     * GL CODE GENERATION
     * ============================================================
     *
     * This implementation deliberately checks the organization
     * before accepting a generated code.
     *
     * For very high concurrency, the database should additionally
     * enforce a UNIQUE constraint on:
     *
     *     (organization_id, code)
     *
     * and the application should retry on a duplicate-key race.
     */

    private String buildGlCode(
            long sequence
    ) {

        return GL_CODE_PREFIX +
                String.format(
                        "%04d",
                        sequence
                );
    }


    private String generateUniqueGlCode(
            Long organizationId
    ) {

        /*
         * Start with the number of bank accounts.
         *
         * This is only a starting point. The uniqueness check
         * below is authoritative for normal application flow.
         */
        long sequence =
                Math.max(
                        1L,
                        bankAccountRepo.count() + 1L
                );


        String code =
                buildGlCode(sequence);


        while (
                coaRepo.existsByOrganization_IdAndCode(
                        organizationId,
                        code
                )
        ) {

            sequence++;

            code =
                    buildGlCode(sequence);
        }


        return code;
    }


    /*
     * ============================================================
     * CREATE BANK/CASH ACCOUNT
     * ============================================================
     *
     * IMPORTANT:
     *
     * openingBalance = 0 is completely valid.
     *
     * The account is created with a zero GL balance.
     *
     * Later funds MUST be added through addFunds()
     * or recordTransaction().
     *
     * Never update the balance field directly.
     */

    @Transactional
    public BankAccount create(
            Organization organization,
            Branch branch,
            String name,
            String accountType,
            String bankName,
            String accountNumber,
            BigDecimal openingBalance,
            String openedBy
    ) {

        requireOrganization(
                organization
        );


        String normalizedName =
                requireText(
                        name,
                        "Account name"
                );


        String normalizedType =
                requireText(
                        accountType,
                        "Account type"
                ).toUpperCase();


        if (!"CASH".equals(normalizedType)
                && !"BANK".equals(normalizedType)) {

            throw new IllegalArgumentException(
                    "Account type must be CASH or BANK"
            );
        }


        BigDecimal opening =
                requireNonNegative(
                        openingBalance,
                        "Opening balance"
                );


        String normalizedBankName =
                optionalText(
                        bankName
                );


        String normalizedAccountNumber =
                optionalText(
                        accountNumber
                );


        /*
         * Generate an organization-specific GL code.
         */
        String glCode =
                generateUniqueGlCode(
                        organization.getId()
                );


        /*
         * Create the GL account first.
         */
        ChartOfAccount glAccount =
                accountingService.createAccount(
                        organization,
                        glCode,
                        normalizedName,
                        ChartOfAccount.AccountType.ASSET,
                        ChartOfAccount.NormalBalance.DEBIT
                );


        if (glAccount == null
                || glAccount.getId() == null) {

            throw new IllegalStateException(
                    "Unable to create GL account for bank account"
            );
        }


        /*
         * Create operational bank/cash account.
         */
        BankAccount account =
                BankAccount.builder()
                        .organization(
                                organization
                        )
                        .branch(
                                branch
                        )
                        .glAccount(
                                glAccount
                        )
                        .name(
                                normalizedName
                        )
                        .accountType(
                                normalizedType
                        )
                        .bankName(
                                normalizedBankName
                        )
                        .accountNumber(
                                normalizedAccountNumber
                        )
                        .active(
                                true
                        )
                        .build();


        account =
                bankAccountRepo.save(
                        account
                );


        if (account.getId() == null) {

            throw new IllegalStateException(
                    "Bank account was not assigned an ID"
            );
        }


        /*
         * ZERO OPENING BALANCE
         *
         * No journal entry is required.
         *
         * The account exists and has a zero balance.
         */
        if (opening.compareTo(
                BigDecimal.ZERO
        ) == 0) {

            log.info(
                    "Created zero-balance {} account {} " +
                            "for organization {}",
                    normalizedType,
                    account.getId(),
                    organization.getId()
            );

            return account;
        }


        /*
         * POSITIVE OPENING BALANCE
         *
         * DR Bank/Cash Asset
         * CR Equity
         */
        ChartOfAccount equityAccount =
                accountingService.getEquityAccount(
                        organization
                );


        if (equityAccount == null
                || equityAccount.getId() == null) {

            throw new IllegalStateException(
                    "Equity account not found for organization " +
                            organization.getId()
            );
        }


        accountingService.post(
                organization,
                branch,
                "BANK_ACCOUNT_OPENING",
                String.valueOf(
                        account.getId()
                ),
                normalizedName,
                "Opening balance for " +
                        normalizedName,

                List.of(

                        JournalLine.builder()
                                .account(
                                        glAccount
                                )
                                .debit(
                                        opening
                                )
                                .credit(
                                        ZERO
                                )
                                .description(
                                        "Opening balance - " +
                                                normalizedName
                                )
                                .build(),

                        JournalLine.builder()
                                .account(
                                        equityAccount
                                )
                                .debit(
                                        ZERO
                                )
                                .credit(
                                        opening
                                )
                                .description(
                                        "Opening balance funding - " +
                                                normalizedName
                                )
                                .build()
                )
        );


        log.info(
                "Created {} account {} with opening balance {} " +
                        "for organization {}",
                normalizedType,
                account.getId(),
                opening,
                organization.getId()
        );


        return account;
    }


    /**
     * Legacy double-compatible create method.
     *
     * @deprecated use the BigDecimal version.
     */
    @Deprecated
    @Transactional
    public BankAccount create(
            Organization organization,
            Branch branch,
            String name,
            String accountType,
            String bankName,
            String accountNumber,
            double openingBalance,
            String openedBy
    ) {

        return create(
                organization,
                branch,
                name,
                accountType,
                bankName,
                accountNumber,
                money(openingBalance),
                openedBy
        );
    }


    /*
     * ============================================================
     * LIST
     * ============================================================
     */

    @Transactional(readOnly = true)
    public List<BankAccount> list(
            Long organizationId
    ) {

        if (organizationId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }


        return bankAccountRepo
                .findByOrganization_IdOrderByNameAsc(
                        organizationId
                );
    }


    /*
     * ============================================================
     * API LIST
     * ============================================================
     */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForApi(
            Long organizationId
    ) {

        List<BankAccount> accounts =
                list(
                        organizationId
                );


        if (accounts.isEmpty()) {

            return List.of();
        }


        return accounts.stream()
                .map(
                        account ->
                                toApiRow(
                                        account
                                )
                )
                .toList();
    }


    private Map<String, Object> toApiRow(
            BankAccount account
    ) {

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
                !Boolean.FALSE.equals(
                        account.getActive()
                )
        );


        /*
         * Current ledger balance.
         */
        row.put(
                "balance",
                getBalanceDecimal(
                        account
                )
        );


        /*
         * Branch.
         */
        if (account.getBranch() != null) {

            row.put(
                    "branchId",
                    account
                            .getBranch()
                            .getId()
            );


            row.put(
                    "branchName",
                    account
                            .getBranch()
                            .getName()
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
         * GL account.
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
    }


    /*
     * ============================================================
     * GET ACCOUNT FOR ORGANIZATION
     * ============================================================
     */

    @Transactional(readOnly = true)
    public BankAccount getForOrg(
            Long accountId,
            Long organizationId
    ) {

        if (accountId == null) {

            throw new IllegalArgumentException(
                    "Bank account ID is required"
            );
        }


        if (organizationId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }


        return bankAccountRepo
                .findByIdAndOrganization_Id(
                        accountId,
                        organizationId
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Bank account not found: " +
                                                accountId
                                )
                );
    }


    /*
     * ============================================================
     * UPDATE ACCOUNT DETAILS
     * ============================================================
     *
     * IMPORTANT:
     *
     * This method does NOT modify the accounting balance.
     *
     * It only changes operational account information.
     */

    @Transactional
    public BankAccount updateDetails(
            Organization organization,
            Long accountId,
            Branch branch,
            String name,
            String bankName,
            String accountNumber
    ) {

        requireOrganization(
                organization
        );


        BankAccount account =
                getForOrg(
                        accountId,
                        organization.getId()
                );


        String normalizedName =
                requireText(
                        name,
                        "Account name"
                );


        account.setName(
                normalizedName
        );


        account.setBranch(
                branch
        );


        account.setBankName(
                optionalText(
                        bankName
                )
        );


        account.setAccountNumber(
                optionalText(
                        accountNumber
                )
        );


        BankAccount saved =
                bankAccountRepo.save(
                        account
                );


        log.info(
                "Updated bank account {} for organization {}",
                accountId,
                organization.getId()
        );


        return saved;
    }


    /*
     * ============================================================
     * ACTIVATE
     * ============================================================
     */

    @Transactional
    public BankAccount activate(
            Organization organization,
            Long accountId
    ) {

        requireOrganization(
                organization
        );


        BankAccount account =
                getForOrg(
                        accountId,
                        organization.getId()
                );


        account.setActive(
                true
        );


        return bankAccountRepo.save(
                account
        );
    }


    /*
     * ============================================================
     * DEACTIVATE
     * ============================================================
     *
     * Deactivation does NOT delete history.
     *
     * Existing journal entries remain untouched.
     */

    @Transactional
    public BankAccount deactivate(
            Organization organization,
            Long accountId
    ) {

        requireOrganization(
                organization
        );


        BankAccount account =
                getForOrg(
                        accountId,
                        organization.getId()
                );


        account.setActive(
                false
        );


        return bankAccountRepo.save(
                account
        );
    }


    /*
     * ============================================================
     * BALANCE
     * ============================================================
     *
     * Bank/cash is a debit-normal asset.
     *
     * Balance =
     *
     *      total debits
     *      -
     *      total credits
     *
     * Reversed original journal entries are excluded.
     * Reversal entries remain part of the ledger.
     */

    @Transactional(readOnly = true)
    public BigDecimal getBalanceDecimal(
            BankAccount account
    ) {

        if (account == null
                || account.getGlAccount() == null
                || account.getGlAccount().getId() == null) {

            return ZERO;
        }


        Long glAccountId =
                account
                        .getGlAccount()
                        .getId();


        List<JournalLine> lines =
                lineRepo.findByAccount_Id(
                        glAccountId
                );


        if (lines == null
                || lines.isEmpty()) {

            return ZERO;
        }


        BigDecimal balance =
                ZERO;


        for (JournalLine line :
                lines) {

            if (line == null) {
                continue;
            }


            JournalEntry entry =
                    line.getJournalEntry();


            if (entry != null
                    && Boolean.TRUE.equals(
                    entry.getReversed()
            )) {

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


        return money(
                balance
        );
    }


    /**
     * Get balance while enforcing organization ownership.
     */
    @Transactional(readOnly = true)
    public BigDecimal getBalanceDecimal(
            Long accountId,
            Long organizationId
    ) {

        BankAccount account =
                getForOrg(
                        accountId,
                        organizationId
                );


        return getBalanceDecimal(
                account
        );
    }


    /**
     * Legacy double-compatible balance method.
     *
     * @deprecated use getBalanceDecimal().
     */
    @Deprecated
    @Transactional(readOnly = true)
    public double getBalance(
            BankAccount account
    ) {

        return getBalanceDecimal(
                account
        ).doubleValue();
    }


    /*
     * ============================================================
     * ADD FUNDS
     * ============================================================
     *
     * THIS IS THE IMPORTANT MISSING FEATURE.
     *
     * An account can be created with 0.
     *
     * Later:
     *
     *     addFunds(account, 500000, counterAccount)
     *
     * creates:
     *
     *     DR Bank/Cash       500,000
     *     CR Counter Account 500,000
     *
     * The balance therefore becomes 500,000.
     */

    @Transactional
    public JournalEntry addFunds(
            Organization organization,
            Long bankAccountId,
            BigDecimal amount,
            Long counterAccountId,
            String description,
            String recordedBy
    ) {

        return recordTransaction(
                organization,
                bankAccountId,
                "DEPOSIT",
                amount,
                counterAccountId,
                description,
                recordedBy
        );
    }


    /*
     * ============================================================
     * WITHDRAW FUNDS
     * ============================================================
     */

    @Transactional
    public JournalEntry withdrawFunds(
            Organization organization,
            Long bankAccountId,
            BigDecimal amount,
            Long counterAccountId,
            String description,
            String recordedBy
    ) {

        return recordTransaction(
                organization,
                bankAccountId,
                "WITHDRAWAL",
                amount,
                counterAccountId,
                description,
                recordedBy
        );
    }


    /*
     * ============================================================
     * DEPOSIT / WITHDRAWAL
     * ============================================================
     */

    @Transactional
    public JournalEntry recordTransaction(
            Organization organization,
            Long bankAccountId,
            String type,
            BigDecimal amount,
            Long counterAccountId,
            String description,
            String recordedBy
    ) {

        requireOrganization(
                organization
        );


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


        String normalizedType =
                requireText(
                        type,
                        "Transaction type"
                ).toUpperCase();


        boolean deposit =
                "DEPOSIT".equals(
                        normalizedType
                );


        boolean withdrawal =
                "WITHDRAWAL".equals(
                        normalizedType
                );


        if (!deposit
                && !withdrawal) {

            throw new IllegalArgumentException(
                    "Transaction type must be DEPOSIT or WITHDRAWAL"
            );
        }


        BankAccount account =
                getForOrg(
                        bankAccountId,
                        organization.getId()
                );


        if (Boolean.FALSE.equals(
                account.getActive()
        )) {

            throw new IllegalStateException(
                    "Bank account is inactive: " +
                            bankAccountId
            );
        }


        if (account.getGlAccount() == null
                || account.getGlAccount().getId() == null) {

            throw new IllegalStateException(
                    "Bank account has no GL account: " +
                            bankAccountId
            );
        }


        ChartOfAccount counterAccount =
                coaRepo
                        .findByIdAndOrganization_Id(
                                counterAccountId,
                                organization.getId()
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Counter account not found: " +
                                                        counterAccountId
                                        )
                        );


        /*
         * Never allow a bank transaction to use another
         * organization's GL account.
         */
        if (counterAccount.getOrganization() == null
                || counterAccount
                .getOrganization()
                .getId() == null
                || !organization
                .getId()
                .equals(
                        counterAccount
                                .getOrganization()
                                .getId()
                )) {

            throw new IllegalArgumentException(
                    "Counter account does not belong to organization"
            );
        }


        /*
         * Prevent overdrawing the bank/cash ledger.
         */
        if (withdrawal) {

            BigDecimal currentBalance =
                    getBalanceDecimal(
                            account
                    );


            if (currentBalance.compareTo(
                    transactionAmount
            ) < 0) {

                throw new IllegalStateException(
                        "Insufficient balance. " +
                                "Available: " +
                                currentBalance +
                                ", requested: " +
                                transactionAmount
                );
            }
        }


        String safeDescription =
                description != null
                        && !description.isBlank()
                        ? description.trim()
                        : deposit
                        ? "Cashbook deposit"
                        : "Cashbook withdrawal";


        String auditDescription =
                (recordedBy != null
                        && !recordedBy.isBlank()
                        ? recordedBy.trim() +
                        ": "
                        : "")
                        +
                        safeDescription;


        List<JournalLine> lines;


        if (deposit) {

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
                                            account
                                                    .getGlAccount()
                                    )
                                    .debit(
                                            transactionAmount
                                    )
                                    .credit(
                                            ZERO
                                    )
                                    .description(
                                            safeDescription
                                    )
                                    .build(),

                            JournalLine.builder()
                                    .account(
                                            counterAccount
                                    )
                                    .debit(
                                            ZERO
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
                                    .account(
                                            counterAccount
                                    )
                                    .debit(
                                            transactionAmount
                                    )
                                    .credit(
                                            ZERO
                                    )
                                    .description(
                                            safeDescription
                                    )
                                    .build(),

                            JournalLine.builder()
                                    .account(
                                            account
                                                    .getGlAccount()
                                    )
                                    .debit(
                                            ZERO
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


        JournalEntry journalEntry =
                accountingService.post(
                        organization,
                        account.getBranch(),
                        "CASHBOOK_" +
                                normalizedType,
                        String.valueOf(
                                bankAccountId
                        ),
                        account.getName(),
                        auditDescription,
                        lines
                );


        log.info(
                "Recorded {} of {} on bank account {} " +
                        "for organization {}",
                normalizedType,
                transactionAmount,
                bankAccountId,
                organization.getId()
        );


        return journalEntry;
    }


    /**
     * Legacy double-compatible transaction method.
     *
     * @deprecated use BigDecimal.
     */
    @Deprecated
    @Transactional
    public JournalEntry recordTransaction(
            Organization organization,
            Long bankAccountId,
            String type,
            double amount,
            Long counterAccountId,
            String description,
            String recordedBy
    ) {

        return recordTransaction(
                organization,
                bankAccountId,
                type,
                money(amount),
                counterAccountId,
                description,
                recordedBy
        );
    }


    /*
     * ============================================================
     * INTERNAL TRANSFER
     * ============================================================
     *
     * DR Destination Bank/Cash
     * CR Source Bank/Cash
     *
     * No equity/revenue/expense account is involved.
     */

    @Transactional
    public JournalEntry transfer(
            Organization organization,
            Long fromAccountId,
            Long toAccountId,
            BigDecimal amount,
            String description,
            String recordedBy
    ) {

        requireOrganization(
                organization
        );


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
                    "Source and destination accounts must be different"
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
                        organization.getId()
                );


        BankAccount to =
                getForOrg(
                        toAccountId,
                        organization.getId()
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


        if (from.getGlAccount() == null
                || from.getGlAccount().getId() == null) {

            throw new IllegalStateException(
                    "Source bank account has no GL account: " +
                            fromAccountId
            );
        }


        if (to.getGlAccount() == null
                || to.getGlAccount().getId() == null) {

            throw new IllegalStateException(
                    "Destination bank account has no GL account: " +
                            toAccountId
            );
        }


        /*
         * Prevent overdraft.
         */
        BigDecimal sourceBalance =
                getBalanceDecimal(
                        from
                );


        if (sourceBalance.compareTo(
                transferAmount
        ) < 0) {

            throw new IllegalStateException(
                    "Insufficient source account balance. " +
                            "Available: " +
                            sourceBalance +
                            ", requested: " +
                            transferAmount
            );
        }


        String safeDescription =
                description != null
                        && !description.isBlank()
                        ? description.trim()
                        : "Internal transfer";


        String auditDescription =
                (recordedBy != null
                        && !recordedBy.isBlank()
                        ? recordedBy.trim() +
                        ": "
                        : "")
                        +
                        safeDescription;


        List<JournalLine> lines =
                List.of(

                        /*
                         * DR destination.
                         */
                        JournalLine.builder()
                                .account(
                                        to.getGlAccount()
                                )
                                .debit(
                                        transferAmount
                                )
                                .credit(
                                        ZERO
                                )
                                .description(
                                        "Transfer from " +
                                                from.getName()
                                )
                                .build(),

                        /*
                         * CR source.
                         */
                        JournalLine.builder()
                                .account(
                                        from.getGlAccount()
                                )
                                .debit(
                                        ZERO
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


        JournalEntry journalEntry =
                accountingService.post(
                        organization,
                        from.getBranch(),
                        "CASHBOOK_TRANSFER",
                        fromAccountId +
                                "->" +
                                toAccountId,
                        from.getName() +
                                " -> " +
                                to.getName(),
                        auditDescription,
                        lines
                );


        log.info(
                "Transferred {} from bank account {} " +
                        "to {} for organization {}",
                transferAmount,
                fromAccountId,
                toAccountId,
                organization.getId()
        );


        return journalEntry;
    }


    /**
     * Legacy double-compatible transfer method.
     *
     * @deprecated use BigDecimal.
     */
    @Deprecated
    @Transactional
    public JournalEntry transfer(
            Organization organization,
            Long fromAccountId,
            Long toAccountId,
            double amount,
            String description,
            String recordedBy
    ) {

        return transfer(
                organization,
                fromAccountId,
                toAccountId,
                money(amount),
                description,
                recordedBy
        );
    }
}
