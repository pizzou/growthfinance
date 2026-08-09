package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Branch;
import com.patrick.fintech.loan_backend.model.ChartOfAccount;
import com.patrick.fintech.loan_backend.model.Expense;
import com.patrick.fintech.loan_backend.model.JournalEntry;
import com.patrick.fintech.loan_backend.model.JournalLine;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
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

    private static final int MONEY_SCALE = 6;

    private static final RoundingMode MONEY_ROUNDING =
            RoundingMode.HALF_UP;

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    MONEY_ROUNDING
            );

    private static final String[][] DEFAULT_ACCOUNTS = {

            // ASSETS
            {"1000", "Cash and Bank", "ASSET", "DEBIT"},
            {"1100", "Loans Receivable", "ASSET", "DEBIT"},
            {"1150", "Interest Receivable", "ASSET", "DEBIT"},

            // CONTRA ASSET
            {"1200", "Loan Loss Reserve", "ASSET", "CREDIT"},

            // LIABILITIES
            {"2000", "Customer Deposits Payable", "LIABILITY", "CREDIT"},
            {"2100", "Borrower Refunds Payable", "LIABILITY", "CREDIT"},

            // EQUITY
            {"3000", "Owner's Equity", "EQUITY", "CREDIT"},

            // INCOME
            {"4000", "Interest Income", "INCOME", "CREDIT"},
            {"4100", "Fee and Penalty Income", "INCOME", "CREDIT"},

            // EXPENSES
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

    /*
     * ============================================================
     * MONEY HELPERS
     * ============================================================
     */

    private BigDecimal money(BigDecimal value) {

        if (value == null) {
            return ZERO;
        }

        return value.setScale(
                MONEY_SCALE,
                MONEY_ROUNDING
        );
    }

    private BigDecimal money(Double value) {

        if (value == null) {
            return ZERO;
        }

        return BigDecimal.valueOf(value)
                .setScale(
                        MONEY_SCALE,
                        MONEY_ROUNDING
                );
    }

    private BigDecimal money(double value) {

        return BigDecimal.valueOf(value)
                .setScale(
                        MONEY_SCALE,
                        MONEY_ROUNDING
                );
    }

    private BigDecimal money(Number value) {

        if (value == null) {
            return ZERO;
        }

        if (value instanceof BigDecimal) {
            return money((BigDecimal) value);
        }

        return BigDecimal.valueOf(
                        value.doubleValue()
                )
                .setScale(
                        MONEY_SCALE,
                        MONEY_ROUNDING
                );
    }

    private BigDecimal maxZero(BigDecimal value) {

        BigDecimal normalized = money(value);

        return normalized.compareTo(ZERO) < 0
                ? ZERO
                : normalized;
    }

    private boolean isPositive(BigDecimal value) {

        return money(value)
                .compareTo(ZERO) > 0;
    }

    private BigDecimal normalize(BigDecimal value) {

        return money(value);
    }

    /*
     * ============================================================
     * VALIDATION
     * ============================================================
     */

    private void requireOrganization(Organization org) {

        if (org == null || org.getId() == null) {
            throw new IllegalArgumentException(
                    "Organization is required"
            );
        }
    }

    private void requireOrganizationId(Long orgId) {

        if (orgId == null) {
            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }
    }

    private void requireAccountId(Long accountId) {

        if (accountId == null) {
            throw new IllegalArgumentException(
                    "Account ID is required"
            );
        }
    }

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

    /*
     * ============================================================
     * CHART OF ACCOUNTS
     * ============================================================
     */

    @Transactional
    public void ensureChartOfAccounts(
            Organization org
    ) {

        requireOrganization(org);

        List<ChartOfAccount> existing =
                coaRepo.findByOrganization_IdOrderByCodeAsc(
                        org.getId()
                );

        Set<String> existingCodes =
                new HashSet<>();

        if (existing != null) {

            for (ChartOfAccount account : existing) {

                if (
                        account != null
                        && account.getCode() != null
                ) {

                    existingCodes.add(
                            account.getCode().trim()
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

    private ChartOfAccount account(
            Organization org,
            String code
    ) {

        requireOrganization(org);

        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(
                    "Account code is required"
            );
        }

        String normalizedCode = code.trim();

        ChartOfAccount account =
                coaRepo
                        .findByOrganization_IdAndCode(
                                org.getId(),
                                normalizedCode
                        )
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Chart of accounts is not configured " +
                                        "for organization " +
                                        org.getId() +
                                        " (missing account " +
                                        normalizedCode +
                                        ")"
                                )
                        );

        if (!Boolean.TRUE.equals(account.getActive())) {
            throw new IllegalStateException(
                    "GL account " +
                    normalizedCode +
                    " is inactive"
            );
        }

        return account;
    }

    private void validateAccountOwnership(
            Organization org,
            ChartOfAccount account
    ) {

        requireOrganization(org);

        if (account == null || account.getId() == null) {
            throw new IllegalArgumentException(
                    "Journal line account is required"
            );
        }

        if (
                account.getOrganization() == null
                || account.getOrganization().getId() == null
        ) {
            throw new IllegalStateException(
                    "GL account has no organization"
            );
        }

        if (
                !org.getId().equals(
                        account.getOrganization().getId()
                )
        ) {
            throw new IllegalStateException(
                    "GL account " +
                    account.getId() +
                    " does not belong to organization " +
                    org.getId()
            );
        }

        if (!Boolean.TRUE.equals(account.getActive())) {
            throw new IllegalStateException(
                    "GL account " +
                    account.getCode() +
                    " is inactive"
            );
        }
    }

    private void validateBranchOwnership(
            Organization org,
            Branch branch
    ) {

        if (branch == null) {
            return;
        }

        requireOrganization(org);

        if (
                branch.getId() == null
                || branch.getOrganization() == null
                || branch.getOrganization().getId() == null
        ) {
            throw new IllegalStateException(
                    "Branch must belong to an organization"
            );
        }

        if (
                !org.getId().equals(
                        branch.getOrganization().getId()
                )
        ) {
            throw new IllegalStateException(
                    "Branch " +
                    branch.getId() +
                    " does not belong to organization " +
                    org.getId()
            );
        }
    }

    @Transactional
    public ChartOfAccount getEquityAccount(
            Organization org
    ) {

        ensureChartOfAccounts(org);

        return account(
                org,
                "3000"
        );
    }

    @Transactional
    public ChartOfAccount createAccount(
            Organization org,
            String code,
            String name,
            ChartOfAccount.AccountType type,
            ChartOfAccount.NormalBalance normalBalance
    ) {

        requireOrganization(org);

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

        String normalizedCode = code.trim();

        if (
                coaRepo.existsByOrganization_IdAndCode(
                        org.getId(),
                        normalizedCode
                )
        ) {
            throw new IllegalArgumentException(
                    "Account code " +
                    normalizedCode +
                    " already exists"
            );
        }

        return coaRepo.save(
                ChartOfAccount.builder()
                        .organization(org)
                        .code(normalizedCode)
                        .name(name.trim())
                        .type(type)
                        .normalBalance(normalBalance)
                        .active(true)
                        .build()
        );
    }

    @Transactional
    public ChartOfAccount updateAccount(
            Long orgId,
            Long accountId,
            String name,
            Boolean active
    ) {

        requireOrganizationId(orgId);
        requireAccountId(accountId);

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

    /*
     * ============================================================
     * GENERIC JOURNAL POSTING
     * ============================================================
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

        requireOrganization(org);

        validateBranchOwnership(
                org,
                branch
        );

        if (sourceType == null || sourceType.isBlank()) {
            throw new IllegalArgumentException(
                    "Journal source type is required"
            );
        }

        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException(
                    "Journal source ID is required"
            );
        }

        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException(
                    "Journal reference is required"
            );
        }

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "Journal description is required"
            );
        }

        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException(
                    "Journal entry must contain at least one line"
            );
        }

        /*
         * REVERSAL entries are intentionally allowed to have the
         * same source type/source ID as the original transaction.
         *
         * All normal accounting events remain idempotent.
         */
        if (!"REVERSAL".equals(sourceType)) {

            JournalEntry existing =
                    journalRepo
                            .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                    org.getId(),
                                    sourceType,
                                    sourceId
                            )
                            .orElse(null);

            if (existing != null) {

                log.warn(
                        "Accounting event already posted. " +
                        "Returning existing journal entry {} for {}:{}",
                        existing.getId(),
                        sourceType,
                        sourceId
                );

                return existing;
            }
        }

        BigDecimal totalDebit = ZERO;
        BigDecimal totalCredit = ZERO;

        for (JournalLine line : lines) {

            if (line == null) {
                throw new IllegalArgumentException(
                        "Journal entry contains a null line"
                );
            }

            ChartOfAccount lineAccount =
                    line.getAccount();

            validateAccountOwnership(
                    org,
                    lineAccount
            );

            BigDecimal debit =
                    money(
                            line.getDebitDecimal()
                    );

            BigDecimal credit =
                    money(
                            line.getCreditDecimal()
                    );

            if (
                    debit.compareTo(ZERO) < 0
                    || credit.compareTo(ZERO) < 0
            ) {
                throw new IllegalArgumentException(
                        "Debit and credit amounts cannot be negative"
                );
            }

            if (
                    debit.compareTo(ZERO) > 0
                    && credit.compareTo(ZERO) > 0
            ) {
                throw new IllegalArgumentException(
                        "A journal line cannot contain both debit and credit"
                );
            }

            if (
                    debit.compareTo(ZERO) == 0
                    && credit.compareTo(ZERO) == 0
            ) {
                throw new IllegalArgumentException(
                        "A journal line must contain a debit or credit amount"
                );
            }

            line.setDebit(debit);
            line.setCredit(credit);

            totalDebit =
                    totalDebit.add(debit);

            totalCredit =
                    totalCredit.add(credit);
        }

        totalDebit = money(totalDebit);
        totalCredit = money(totalCredit);

        if (
                totalDebit.compareTo(totalCredit) != 0
        ) {
            throw new IllegalStateException(
                    "Journal entry does not balance: " +
                    "debits " +
                    totalDebit.toPlainString() +
                    " != credits " +
                    totalCredit.toPlainString() +
                    " (" +
                    description +
                    ")"
            );
        }

        JournalEntry entry =
                JournalEntry.builder()
                        .organization(org)
                        .branch(branch)
                        .entryDate(LocalDate.now())
                        .sourceType(sourceType.trim())
                        .sourceId(sourceId.trim())
                        .reference(reference.trim())
                        .description(description.trim())
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

    /*
     * ============================================================
     * LOAN DISBURSEMENT
     * ============================================================
     */

    @Transactional
    public void postDisbursement(
            Loan loan
    ) {

        if (loan == null) {
            throw new IllegalArgumentException(
                    "Loan is required"
            );
        }

        if (loan.getId() == null) {
            throw new IllegalArgumentException(
                    "Loan ID is required"
            );
        }

        Organization org =
                loan.getOrganization();

        requireOrganization(org);

        ensureChartOfAccounts(org);

        BigDecimal amount =
                money(
                        loan.getAmount()
                );

        if (amount.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Loan disbursement amount must be greater than zero"
            );
        }

        String sourceId =
                String.valueOf(
                        loan.getId()
                );

        String reference =
                loan.getReferenceNumber() != null
                        && !loan.getReferenceNumber().isBlank()
                        ? loan.getReferenceNumber().trim()
                        : "LOAN-" + loan.getId();

        JournalEntry existing =
                journalRepo
                        .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                org.getId(),
                                "LOAN_DISBURSEMENT",
                                sourceId
                        )
                        .orElse(null);

        if (existing != null) {

            log.info(
                    "Loan {} disbursement already posted as journal {}",
                    loan.getId(),
                    existing.getId()
            );

            return;
        }

        List<JournalLine> lines =
                new ArrayList<>();

        /*
         * DR Loans Receivable
         */

        lines.add(
                JournalLine.builder()
                        .account(
                                account(
                                        org,
                                        "1100"
                                )
                        )
                        .debit(amount)
                        .credit(ZERO)
                        .description(
                                "Loans Receivable — " +
                                reference
                        )
                        .build()
        );

        /*
         * CR Cash
         */

        lines.add(
                JournalLine.builder()
                        .account(
                                account(
                                        org,
                                        "1000"
                                )
                        )
                        .debit(ZERO)
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
                sourceId,
                reference,
                "Disbursement of loan " +
                reference,
                lines
        );

        /*
         * PROCESSING FEE
         */

        BigDecimal fee =
                money(
                        loan.getProcessingFee()
                );

        if (fee.compareTo(ZERO) > 0) {

            post(
                    org,
                    loan.getBranch(),
                    "PROCESSING_FEE",
                    sourceId,
                    reference,
                    "Processing fee collected on " +
                    reference,
                    List.of(

                            JournalLine.builder()
                                    .account(
                                            account(
                                                    org,
                                                    "1000"
                                            )
                                    )
                                    .debit(fee)
                                    .credit(ZERO)
                                    .description(
                                            "Processing fee — " +
                                            reference
                                    )
                                    .build(),

                            JournalLine.builder()
                                    .account(
                                            account(
                                                    org,
                                                    "4100"
                                            )
                                    )
                                    .debit(ZERO)
                                    .credit(fee)
                                    .description(
                                            "Processing fee income — " +
                                            reference
                                    )
                                    .build()
                    )
            );
        }
    }

    /*
     * ============================================================
     * INTEREST ACCRUAL
     * ============================================================
     */

    @Transactional
    public void postInterestAccrual(
            Loan loan,
            double dailyInterestAmount
    ) {

        postInterestAccrual(
                loan,
                money(dailyInterestAmount)
        );
    }

    @Transactional
    public void postInterestAccrual(
            Loan loan,
            BigDecimal dailyInterestAmount
    ) {

        if (loan == null) {
            throw new IllegalArgumentException(
                    "Loan is required"
            );
        }

        if (loan.getId() == null) {
            throw new IllegalArgumentException(
                    "Loan ID is required"
            );
        }

        Organization org =
                loan.getOrganization();

        requireOrganization(org);

        ensureChartOfAccounts(org);

        BigDecimal interest =
                maxZero(
                        dailyInterestAmount
                );

        if (interest.compareTo(ZERO) <= 0) {
            return;
        }

        String sourceId =
                loan.getId() +
                "-" +
                LocalDate.now();

        JournalEntry existing =
                journalRepo
                        .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                org.getId(),
                                "INTEREST_ACCRUAL",
                                sourceId
                        )
                        .orElse(null);

        if (existing != null) {

            log.info(
                    "Interest already accrued for loan {} on {}. Journal {}",
                    loan.getId(),
                    LocalDate.now(),
                    existing.getId()
            );

            return;
        }

        String reference =
                loan.getReferenceNumber() != null
                        && !loan.getReferenceNumber().isBlank()
                        ? loan.getReferenceNumber().trim()
                        : "LOAN-" + loan.getId();

        post(
                org,
                loan.getBranch(),
                "INTEREST_ACCRUAL",
                sourceId,
                reference,
                "Interest accrual for " +
                reference +
                " (" +
                LocalDate.now() +
                ")",

                List.of(

                        JournalLine.builder()
                                .account(
                                        account(
                                                org,
                                                "1150"
                                        )
                                )
                                .debit(interest)
                                .credit(ZERO)
                                .description(
                                        "Interest accrued — " +
                                        reference
                                )
                                .build(),

                        JournalLine.builder()
                                .account(
                                        account(
                                                org,
                                                "4000"
                                        )
                                )
                                .debit(ZERO)
                                .credit(interest)
                                .description(
                                        "Interest income accrued — " +
                                        reference
                                )
                                .build()
                )
        );
    }

    /*
     * ============================================================
     * PAYMENT RECEIVED
     *
     * IMPORTANT:
     *
     * This method now accepts SIX transaction values:
     *
     * payment
     * paymentAmount
     * principalAmount
     * interestAmount
     * penaltyAmount
     * overpaymentAmount
     *
     * PaymentService should call:
     *
     * accountingService.postPaymentReceived(
     *     installment,
     *     amount.doubleValue(),
     *     principalPaid.doubleValue(),
     *     interestPaid.doubleValue(),
     *     newPenalty.doubleValue(),
     *     overpayment.doubleValue()
     * );
     *
     * Accounting:
     *
     * DR Cash
     * CR Loans Receivable
     * CR Interest Receivable / Interest Income
     * CR Fee/Penalty Income
     * CR Borrower Refunds Payable
     * ============================================================
     */

    @Transactional
    public JournalEntry postPaymentReceived(
            Payment payment,
            Double paymentAmount,
            double principalAmount,
            double interestAmount,
            double penaltyAmount,
            double overpaymentAmount
    ) {

        return postPaymentReceived(
                payment,
                money(paymentAmount),
                money(principalAmount),
                money(interestAmount),
                money(penaltyAmount),
                money(overpaymentAmount)
        );
    }

    @Transactional
    public JournalEntry postPaymentReceived(
            Payment payment,
            BigDecimal paymentAmount,
            BigDecimal principalAmount,
            BigDecimal interestAmount,
            BigDecimal penaltyAmount,
            BigDecimal overpaymentAmount
    ) {

        if (payment == null) {
            throw new IllegalArgumentException(
                    "Payment is required"
            );
        }

        if (payment.getId() == null) {
            throw new IllegalArgumentException(
                    "Payment ID is required"
            );
        }

        Loan loan =
                payment.getLoan();

        if (loan == null) {
            throw new IllegalArgumentException(
                    "Payment has no loan"
            );
        }

        if (loan.getId() == null) {
            throw new IllegalArgumentException(
                    "Payment loan has no ID"
            );
        }

        Organization org =
                loan.getOrganization();

        requireOrganization(org);

        ensureChartOfAccounts(org);

        String sourceId =
                String.valueOf(
                        payment.getId()
                );

        JournalEntry existing =
                journalRepo
                        .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                org.getId(),
                                "PAYMENT_RECEIVED",
                                sourceId
                        )
                        .orElse(null);

        if (existing != null) {

            log.info(
                    "Payment {} already posted as journal {}",
                    payment.getId(),
                    existing.getId()
            );

            return existing;
        }

        /*
         * ========================================================
         * TRANSACTION AMOUNTS
         * ========================================================
         */

        BigDecimal total =
                maxZero(paymentAmount);

        if (total.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero"
            );
        }

        BigDecimal principal =
                maxZero(principalAmount);

        BigDecimal interest =
                maxZero(interestAmount);

        BigDecimal penalty =
                maxZero(penaltyAmount);

        BigDecimal overpayment =
                maxZero(overpaymentAmount);

        /*
         * ========================================================
         * VALIDATE ALLOCATION
         * ========================================================
         *
         * The five components must exactly explain the payment.
         *
         * principal
         * + interest
         * + penalty
         * + overpayment
         * = payment
         *
         * We allow a very small rounding tolerance because all
         * amounts are normalized to MONEY_SCALE.
         */

        BigDecimal allocated =
                principal
                        .add(interest)
                        .add(penalty)
                        .add(overpayment);

        allocated =
                money(allocated);

        BigDecimal allocationDifference =
                total.subtract(allocated);

        allocationDifference =
                money(allocationDifference);

        if (
                allocationDifference.compareTo(ZERO) != 0
        ) {

            throw new IllegalStateException(
                    "Payment allocation does not equal payment amount: " +
                    "payment=" +
                    total.toPlainString() +
                    ", principal=" +
                    principal.toPlainString() +
                    ", interest=" +
                    interest.toPlainString() +
                    ", penalty=" +
                    penalty.toPlainString() +
                    ", overpayment=" +
                    overpayment.toPlainString() +
                    ", allocated=" +
                    allocated.toPlainString()
            );
        }

        List<JournalLine> lines =
                new ArrayList<>();

        /*
         * ========================================================
         * LOAN REFERENCE
         * ========================================================
         */

        String loanReference =
                loan.getReferenceNumber() != null
                        && !loan.getReferenceNumber().isBlank()
                        ? loan.getReferenceNumber().trim()
                        : "LOAN-" + loan.getId();

        /*
         * ========================================================
         * DR CASH
         * ========================================================
         */

        lines.add(
                JournalLine.builder()
                        .account(
                                account(
                                        org,
                                        "1000"
                                )
                        )
                        .debit(total)
                        .credit(ZERO)
                        .description(
                                "Payment received — " +
                                loanReference
                        )
                        .build()
        );

        /*
         * ========================================================
         * CR LOANS RECEIVABLE
         * ========================================================
         */

        if (principal.compareTo(ZERO) > 0) {

            lines.add(
                    JournalLine.builder()
                            .account(
                                    account(
                                            org,
                                            "1100"
                                    )
                            )
                            .debit(ZERO)
                            .credit(principal)
                            .description(
                                    "Principal repayment — " +
                                    loanReference
                            )
                            .build()
            );
        }

        /*
         * ========================================================
         * INTEREST
         *
         * First clear Interest Receivable.
         *
         * Any amount above the accrued receivable is posted
         * directly to Interest Income.
         * ========================================================
         */

        if (interest.compareTo(ZERO) > 0) {

            BigDecimal accrued =
                    accruedInterestReceivable(
                            org,
                            loan.getId()
                    );

            BigDecimal clearReceivable =
                    interest.min(
                            maxZero(accrued)
                    );

            BigDecimal directIncome =
                    interest.subtract(
                            clearReceivable
                    );

            clearReceivable =
                    maxZero(clearReceivable);

            directIncome =
                    maxZero(directIncome);

            if (
                    clearReceivable.compareTo(ZERO) > 0
            ) {

                lines.add(
                        JournalLine.builder()
                                .account(
                                        account(
                                                org,
                                                "1150"
                                        )
                                )
                                .debit(ZERO)
                                .credit(clearReceivable)
                                .description(
                                        "Clears accrued interest — " +
                                        loanReference
                                )
                                .build()
                );
            }

            if (
                    directIncome.compareTo(ZERO) > 0
            ) {

                lines.add(
                        JournalLine.builder()
                                .account(
                                        account(
                                                org,
                                                "4000"
                                        )
                                )
                                .debit(ZERO)
                                .credit(directIncome)
                                .description(
                                        "Interest income — " +
                                        loanReference
                                )
                                .build()
                );
            }
        }

        /*
         * ========================================================
         * PENALTY / FEE INCOME
         * ========================================================
         */

        if (penalty.compareTo(ZERO) > 0) {

            lines.add(
                    JournalLine.builder()
                            .account(
                                    account(
                                            org,
                                            "4100"
                                    )
                            )
                            .debit(ZERO)
                            .credit(penalty)
                            .description(
                                    "Penalty/fee income — " +
                                    loanReference
                            )
                            .build()
            );
        }

        /*
         * ========================================================
         * OVERPAYMENT
         *
         * This is NOT income.
         *
         * It is a LIABILITY because the money belongs to the
         * borrower until it is refunded or otherwise lawfully
         * applied to a future obligation.
         *
         * DR Cash
         * CR Borrower Refunds Payable
         * ========================================================
         */

        if (overpayment.compareTo(ZERO) > 0) {

            lines.add(
                    JournalLine.builder()
                            .account(
                                    account(
                                            org,
                                            "2100"
                                    )
                            )
                            .debit(ZERO)
                            .credit(overpayment)
                            .description(
                                    "Borrower overpayment refundable — " +
                                    loanReference
                            )
                            .build()
            );

            log.info(
                    "Payment {} has borrower overpayment of {}. " +
                    "Amount posted to Borrower Refunds Payable.",
                    payment.getId(),
                    overpayment.toPlainString()
            );
        }

        /*
         * ========================================================
         * FINAL BALANCE VALIDATION
         * ========================================================
         */

        BigDecimal totalDebits =
                ZERO;

        BigDecimal totalCredits =
                ZERO;

        for (JournalLine line : lines) {

            totalDebits =
                    totalDebits.add(
                            money(
                                    line.getDebitDecimal()
                            )
                    );

            totalCredits =
                    totalCredits.add(
                            money(
                                    line.getCreditDecimal()
                            )
                    );
        }

        totalDebits =
                money(totalDebits);

        totalCredits =
                money(totalCredits);

        if (
                totalDebits.compareTo(
                        totalCredits
                ) != 0
        ) {

            throw new IllegalStateException(
                    "Payment accounting does not balance: " +
                    "debits=" +
                    totalDebits.toPlainString() +
                    ", credits=" +
                    totalCredits.toPlainString()
            );
        }

        /*
         * ========================================================
         * PAYMENT REFERENCE
         * ========================================================
         */

        String reference =
                payment.getPaymentReference() != null
                        && !payment.getPaymentReference().isBlank()
                        ? payment.getPaymentReference().trim()
                        : "PAY-" + payment.getId();

        /*
         * ========================================================
         * POST JOURNAL
         * ========================================================
         */

        return post(
                org,
                loan.getBranch(),
                "PAYMENT_RECEIVED",
                sourceId,
                reference,
                "Payment received on loan " +
                loanReference,
                lines
        );
    }

    /*
     * ============================================================
     * PAYMENT RECEIVED FROM PAYMENT ENTITY
     *
     * Compatibility method.
     *
     * If Payment contains an explicit overpayment field, this
     * method should use it. Otherwise it derives overpayment from
     * payment minus principal/interest/penalty.
     * ============================================================
     */

    @Transactional
    public JournalEntry postPaymentReceived(
            Payment payment
    ) {

        if (payment == null) {
            throw new IllegalArgumentException(
                    "Payment is required"
            );
        }

        BigDecimal amount =
                payment.getAmountPaid() != null
                        ? money(
                                payment.getAmountPaid()
                        )
                        : payment.getAmount() != null
                                ? money(
                                        payment.getAmount()
                                )
                                : ZERO;

        BigDecimal principal =
                payment.getPrincipalComponent() != null
                        ? money(
                                payment.getPrincipalComponent()
                        )
                        : ZERO;

        BigDecimal interest =
                payment.getInterestComponent() != null
                        ? money(
                                payment.getInterestComponent()
                        )
                        : ZERO;

        BigDecimal penalty =
                payment.getPenalty() != null
                        ? money(
                                payment.getPenalty()
                        )
                        : ZERO;

        /*
         * Derive overpayment only for this compatibility method.
         *
         * PaymentService should preferably call the six-argument
         * method directly because it already knows the exact
         * transaction-level overpayment.
         */

        BigDecimal derivedOverpayment =
                amount
                        .subtract(principal)
                        .subtract(interest)
                        .subtract(penalty);

        derivedOverpayment =
                maxZero(derivedOverpayment);

        return postPaymentReceived(
                payment,
                amount,
                principal,
                interest,
                penalty,
                derivedOverpayment
        );
    }

    /*
     * ============================================================
     * OVERPAYMENT REFUND PAYABLE
     *
     * This method remains available when overpayment is recorded
     * separately from the payment journal.
     *
     * NOTE:
     * PaymentService should NOT call this method for the same
     * overpayment if the six-argument postPaymentReceived() already
     * posted the overpayment to account 2100. Otherwise the liability
     * would be duplicated.
     * ============================================================
     */

    @Transactional
    public JournalEntry postOverpaymentRefundPayable(
            Payment payment,
            BigDecimal refundAmount
    ) {

        if (payment == null) {
            throw new IllegalArgumentException(
                    "Payment is required"
            );
        }

        if (payment.getId() == null) {
            throw new IllegalArgumentException(
                    "Payment ID is required"
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

        requireOrganization(org);

        ensureChartOfAccounts(org);

        BigDecimal amount =
                maxZero(refundAmount);

        if (amount.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Refund payable amount must be greater than zero"
            );
        }

        String sourceId =
                String.valueOf(
                        payment.getId()
                );

        JournalEntry existing =
                journalRepo
                        .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                org.getId(),
                                "OVERPAYMENT_REFUND_PAYABLE",
                                sourceId
                        )
                        .orElse(null);

        if (existing != null) {
            return existing;
        }

        String reference =
                payment.getPaymentReference() != null
                        && !payment.getPaymentReference().isBlank()
                        ? payment.getPaymentReference().trim()
                        : "PAY-" + payment.getId();

        return post(
                org,
                loan.getBranch(),
                "OVERPAYMENT_REFUND_PAYABLE",
                sourceId,
                "REFUND-" + payment.getId(),
                "Borrower refund payable for payment " +
                reference,

                List.of(

                        JournalLine.builder()
                                .account(
                                        account(
                                                org,
                                                "1000"
                                        )
                                )
                                .debit(amount)
                                .credit(ZERO)
                                .description(
                                        "Excess payment received — " +
                                        reference
                                )
                                .build(),

                        JournalLine.builder()
                                .account(
                                        account(
                                                org,
                                                "2100"
                                        )
                                )
                                .debit(ZERO)
                                .credit(amount)
                                .description(
                                        "Borrower refund payable — " +
                                        reference
                                )
                                .build()
                )
        );
    }

    /*
     * ============================================================
     * REFUND PAYMENT
     * ============================================================
     */

    @Transactional
    public JournalEntry postRefundPaid(
            Organization org,
            Branch branch,
            String refundReference,
            String sourceId,
            BigDecimal refundAmount,
            String description
    ) {

        requireOrganization(org);

        ensureChartOfAccounts(org);

        validateBranchOwnership(
                org,
                branch
        );

        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException(
                    "Refund source ID is required"
            );
        }

        if (
                refundReference == null
                || refundReference.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Refund reference is required"
            );
        }

        BigDecimal amount =
                maxZero(refundAmount);

        if (amount.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Refund amount must be greater than zero"
            );
        }

        BigDecimal payableBalance =
                borrowerRefundsPayableBalance(
                        org
                );

        if (
                amount.compareTo(
                        payableBalance
                ) > 0
        ) {
            throw new IllegalStateException(
                    "Refund amount exceeds borrower refund liability. " +
                    "Requested=" +
                    amount.toPlainString() +
                    ", available=" +
                    payableBalance.toPlainString()
            );
        }

        JournalEntry existing =
                journalRepo
                        .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                org.getId(),
                                "REFUND_PAYMENT",
                                sourceId
                        )
                        .orElse(null);

        if (existing != null) {
            return existing;
        }

        String finalDescription =
                description != null
                        && !description.isBlank()
                        ? description.trim()
                        : "Borrower refund paid";

        return post(
                org,
                branch,
                "REFUND_PAYMENT",
                sourceId,
                refundReference.trim(),
                finalDescription,

                List.of(

                        JournalLine.builder()
                                .account(
                                        account(
                                                org,
                                                "2100"
                                        )
                                )
                                .debit(amount)
                                .credit(ZERO)
                                .description(
                                        "Borrower refund liability settled — " +
                                        refundReference
                                )
                                .build(),

                        JournalLine.builder()
                                .account(
                                        account(
                                                org,
                                                "1000"
                                        )
                                )
                                .debit(ZERO)
                                .credit(amount)
                                .description(
                                        "Refund paid to borrower — " +
                                        refundReference
                                )
                                .build()
                )
        );
    }

    /*
     * ============================================================
     * REFUND LIABILITY BALANCE
     * ============================================================
     */

    @Transactional(readOnly = true)
    public BigDecimal borrowerRefundsPayableBalance(
            Organization org
    ) {

        requireOrganization(org);

        ChartOfAccount refundAccount =
                coaRepo
                        .findByOrganization_IdAndCode(
                                org.getId(),
                                "2100"
                        )
                        .orElse(null);

        if (refundAccount == null) {
            return ZERO;
        }

        List<JournalLine> lines =
                lineRepo.findByAccount_IdAndOrganization_Id(
                        refundAccount.getId(),
                        org.getId()
                );

        if (lines == null || lines.isEmpty()) {
            return ZERO;
        }

        BigDecimal debit =
                ZERO;

        BigDecimal credit =
                ZERO;

        for (JournalLine line : lines) {

            if (line == null) {
                continue;
            }

            debit =
                    debit.add(
                            money(
                                    line.getDebitDecimal()
                            )
                    );

            credit =
                    credit.add(
                            money(
                                    line.getCreditDecimal()
                            )
                    );
        }

        return maxZero(
                credit.subtract(debit)
        );
    }

    /*
     * ============================================================
     * ACCRUED INTEREST RECEIVABLE
     * ============================================================
     */

    private BigDecimal accruedInterestReceivable(
            Organization org,
            Long loanId
    ) {

        requireOrganization(org);

        if (loanId == null) {
            return ZERO;
        }

        ChartOfAccount receivable =
                coaRepo
                        .findByOrganization_IdAndCode(
                                org.getId(),
                                "1150"
                        )
                        .orElse(null);

        if (receivable == null) {
            return ZERO;
        }

        List<JournalLine> lines =
                lineRepo.findInterestReceivableLinesForLoan(
                        receivable.getId(),
                        org.getId(),
                        loanId
                );

        if (
                lines == null
                || lines.isEmpty()
        ) {
            return ZERO;
        }

        BigDecimal balance =
                ZERO;

        for (JournalLine line : lines) {

            if (line == null) {
                continue;
            }

            JournalEntry entry =
                    line.getJournalEntry();

            if (entry == null) {
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

        return maxZero(balance);
    }

    /*
     * ============================================================
     * WRITE OFF
     * ============================================================
     */

    @Transactional
    public void postWriteOff(
            Loan loan
    ) {

        if (loan == null) {
            throw new IllegalArgumentException(
                    "Loan is required"
            );
        }

        if (loan.getId() == null) {
            throw new IllegalArgumentException(
                    "Loan ID is required"
            );
        }

        Organization org =
                loan.getOrganization();

        requireOrganization(org);

        ensureChartOfAccounts(org);

        BigDecimal outstanding =
                money(
                        loan.getOutstandingBalance()
                );

        if (outstanding.compareTo(ZERO) <= 0) {
            return;
        }

        String sourceId =
                String.valueOf(
                        loan.getId()
                );

        JournalEntry existing =
                journalRepo
                        .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                org.getId(),
                                "WRITE_OFF",
                                sourceId
                        )
                        .orElse(null);

        if (existing != null) {

            log.info(
                    "Loan {} already has a write-off journal {}",
                    loan.getId(),
                    existing.getId()
            );

            return;
        }

        String reference =
                loan.getReferenceNumber() != null
                        && !loan.getReferenceNumber().isBlank()
                        ? loan.getReferenceNumber().trim()
                        : "LOAN-" + loan.getId();

        post(
                org,
                loan.getBranch(),
                "WRITE_OFF",
                sourceId,
                reference,
                "Write-off of loan " +
                reference,

                List.of(

                        JournalLine.builder()
                                .account(
                                        account(
                                                org,
                                                "5000"
                                        )
                                )
                                .debit(outstanding)
                                .credit(ZERO)
                                .description(
                                        "Loan loss expense — " +
                                        reference
                                )
                                .build(),

                        JournalLine.builder()
                                .account(
                                        account(
                                                org,
                                                "1100"
                                        )
                                )
                                .debit(ZERO)
                                .credit(outstanding)
                                .description(
                                        "Write off receivable — " +
                                        reference
                                )
                                .build()
                )
        );
    }

    /*
     * ============================================================
     * EXPENSE
     * ============================================================
     */

    @Transactional
    public JournalEntry postExpense(
            Expense expense
    ) {

        if (expense == null) {
            throw new IllegalArgumentException(
                    "Expense is required"
            );
        }

        if (expense.getId() == null) {
            throw new IllegalArgumentException(
                    "Expense ID is required"
            );
        }

        Organization org =
                expense.getOrganization();

        requireOrganization(org);

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

        validateAccountOwnership(
                org,
                paymentGlAccount
        );

        BigDecimal amount =
                money(
                        expense.getAmount()
                );

        if (amount.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Expense amount must be greater than zero"
            );
        }

        String sourceId =
                String.valueOf(
                        expense.getId()
                );

        JournalEntry existing =
                journalRepo
                        .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                org.getId(),
                                "EXPENSE",
                                sourceId
                        )
                        .orElse(null);

        if (existing != null) {

            log.info(
                    "Expense {} already posted as journal {}",
                    expense.getId(),
                    existing.getId()
            );

            return existing;
        }

        String reference =
                "EXP-" +
                expense.getId();

        String description =
                "Expense — " +
                expense
                        .getCategory()
                        .getLabel();

        if (
                expense.getDescription() != null
                && !expense.getDescription().isBlank()
        ) {

            description +=
                    ": " +
                    expense.getDescription().trim();
        }

        return post(
                org,
                expense.getBranch(),
                "EXPENSE",
                sourceId,
                reference,
                description,

                List.of(

                        JournalLine.builder()
                                .account(expenseAccount)
                                .debit(amount)
                                .credit(ZERO)
                                .description(
                                        expense
                                                .getCategory()
                                                .getLabel() +
                                        " — " +
                                        reference
                                )
                                .build(),

                        JournalLine.builder()
                                .account(paymentGlAccount)
                                .debit(ZERO)
                                .credit(amount)
                                .description(
                                        "Paid from " +
                                        expense
                                                .getPaymentAccount()
                                                .getName() +
                                        " — " +
                                        reference
                                )
                                .build()
                )
        );
    }

    /*
     * ============================================================
     * REVERSE EXPENSE
     * ============================================================
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

        requireOrganizationId(orgId);

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
                "REVERSAL".equals(
                        original.getSourceType()
                )
        ) {
            throw new IllegalStateException(
                    "A reversal entry cannot itself be reversed"
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

        /*
         * A payment journal cannot be reversed after its associated
         * refund has already been paid.
         */

        if (
                "PAYMENT_RECEIVED".equals(
                        original.getSourceType()
                )
        ) {

            String paymentSourceId =
                    original.getSourceId();

            JournalEntry paidRefund =
                    journalRepo
                            .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                    orgId,
                                    "REFUND_PAYMENT",
                                    paymentSourceId
                            )
                            .orElse(null);

            if (paidRefund != null) {

                throw new IllegalStateException(
                        "Payment journal " +
                        entryId +
                        " cannot be reversed because a borrower refund " +
                        "has already been paid under refund journal " +
                        paidRefund.getId() +
                        ". Handle the refund transaction first."
                );
            }
        }

        /*
         * PREVENT DUPLICATE REVERSALS
         */

        String reversalSourceId =
                String.valueOf(entryId);

        JournalEntry existingReversal =
                journalRepo
                        .findFirstByOrganization_IdAndSourceTypeAndSourceId(
                                orgId,
                                "REVERSAL",
                                reversalSourceId
                        )
                        .orElse(null);

        if (existingReversal != null) {

            log.warn(
                    "Entry {} already has reversal {}",
                    entryId,
                    existingReversal.getId()
            );

            return existingReversal;
        }

        List<JournalLine> reversedLines =
                new ArrayList<>();

        for (
                JournalLine line :
                original.getLines()
        ) {

            if (line == null) {
                continue;
            }

            ChartOfAccount lineAccount =
                    line.getAccount();

            validateAccountOwnership(
                    original.getOrganization(),
                    lineAccount
            );

            BigDecimal originalDebit =
                    money(
                            line.getDebitDecimal()
                    );

            BigDecimal originalCredit =
                    money(
                            line.getCreditDecimal()
                    );

            reversedLines.add(
                    JournalLine.builder()
                            .account(lineAccount)
                            .debit(originalCredit)
                            .credit(originalDebit)
                            .description(
                                    "Reversal of #" +
                                    entryId +
                                    " — " +
                                    (
                                            line.getDescription() != null
                                                    ? line.getDescription()
                                                    : ""
                                    )
                            )
                            .build()
            );
        }

        if (reversedLines.isEmpty()) {
            throw new IllegalStateException(
                    "Journal entry contains no valid lines: " +
                    entryId
            );
        }

        BigDecimal reversalDebit =
                ZERO;

        BigDecimal reversalCredit =
                ZERO;

        for (
                JournalLine line :
                reversedLines
        ) {

            reversalDebit =
                    reversalDebit.add(
                            money(
                                    line.getDebitDecimal()
                            )
                    );

            reversalCredit =
                    reversalCredit.add(
                            money(
                                    line.getCreditDecimal()
                            )
                    );
        }

        reversalDebit =
                normalize(reversalDebit);

        reversalCredit =
                normalize(reversalCredit);

        if (
                reversalDebit.compareTo(
                        reversalCredit
                ) != 0
        ) {
            throw new IllegalStateException(
                    "Generated reversal does not balance for entry " +
                    entryId
            );
        }

        String reversalDescription =
                "Reversal of entry #" +
                entryId;

        if (
                reason != null
                && !reason.isBlank()
        ) {

            reversalDescription +=
                    ": " +
                    reason.trim();
        }

        if (
                original.getDescription() != null
                && !original.getDescription().isBlank()
        ) {

            reversalDescription +=
                    " — " +
                    original.getDescription();
        }

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
                                reversalSourceId
                        )
                        .reference(
                                original.getReference() != null
                                        ? original.getReference()
                                        : "REV-" + entryId
                        )
                        .description(
                                reversalDescription
                        )
                        .createdBy(
                                reversedBy != null
                                        && !reversedBy.isBlank()
                                                ? reversedBy.trim()
                                                : "SYSTEM"
                        )
                        .reversed(false)
                        .build();

        reversal =
                journalRepo.save(
                        reversal
                );

        for (
                JournalLine line :
                reversedLines
        ) {

            line.setJournalEntry(
                    reversal
            );

            lineRepo.save(line);
        }

        original.setReversed(true);

        journalRepo.save(original);

        log.info(
                "Journal entry {} reversed by journal {}",
                entryId,
                reversal.getId()
        );

        return reversal;
    }

    /*
     * ============================================================
     * LEDGER
     * ============================================================
     */

    @Transactional(readOnly = true)
    public Map<String, Object> getLedger(
            Long orgId,
            Long accountId
    ) {

        requireOrganizationId(orgId);
        requireAccountId(accountId);

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
                lineRepo.findLedgerForAccountAndOrganization(
                        accountId,
                        orgId
                );

        List<Map<String, Object>> rows =
                new ArrayList<>();

        BigDecimal running =
                ZERO;

        if (lines != null) {

            for (
                    JournalLine line :
                    lines
            ) {

                if (line == null) {
                    continue;
                }

                JournalEntry entry =
                        line.getJournalEntry();

                if (entry == null) {
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

                running =
                        running.add(
                                debitNormal
                                        ? debit.subtract(credit)
                                        : credit.subtract(debit)
                        );

                running =
                        normalize(running);

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
                        "sourceId",
                        entry.getSourceId()
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
                        Boolean.TRUE.equals(
                                entry.getReversed()
                        )
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

    /*
     * ============================================================
     * TRIAL BALANCE
     * ============================================================
     */

    @Transactional(readOnly = true)
    public Map<String, Object> getTrialBalance(
            Long orgId
    ) {

        requireOrganizationId(orgId);

        List<ChartOfAccount> accounts =
                coaRepo
                        .findByOrganization_IdOrderByCodeAsc(
                                orgId
                        );

        List<Map<String, Object>> rows =
                new ArrayList<>();

        BigDecimal totalDebit =
                ZERO;

        BigDecimal totalCredit =
                ZERO;

        if (accounts != null) {

            for (
                    ChartOfAccount acc :
                    accounts
            ) {

                if (acc == null) {
                    continue;
                }

                List<JournalLine> lines =
                        lineRepo.findByAccount_IdAndOrganization_Id(
                                acc.getId(),
                                orgId
                        );

                BigDecimal debit =
                        ZERO;

                BigDecimal credit =
                        ZERO;

                if (lines != null) {

                    for (
                            JournalLine line :
                            lines
                    ) {

                        if (line == null) {
                            continue;
                        }

                        debit =
                                debit.add(
                                        money(
                                                line.getDebitDecimal()
                                        )
                                );

                        credit =
                                credit.add(
                                        money(
                                                line.getCreditDecimal()
                                        )
                                );
                    }
                }

                debit =
                        normalize(debit);

                credit =
                        normalize(credit);

                BigDecimal net =
                        debit.subtract(credit);

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
                        net.compareTo(ZERO) > 0
                                ? net
                                : ZERO
                );

                row.put(
                        "credit",
                        net.compareTo(ZERO) < 0
                                ? net.negate()
                                : ZERO
                );

                rows.add(row);

                if (net.compareTo(ZERO) > 0) {

                    totalDebit =
                            totalDebit.add(net);

                } else if (net.compareTo(ZERO) < 0) {

                    totalCredit =
                            totalCredit.add(
                                    net.negate()
                            );
                }
            }
        }

        totalDebit =
                normalize(totalDebit);

        totalCredit =
                normalize(totalCredit);

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
                totalDebit.compareTo(
                        totalCredit
                ) == 0
        );

        return result;
    }

    /*
     * ============================================================
     * BALANCE SHEET
     * ============================================================
     */

    @Transactional(readOnly = true)
    public Map<String, Object> getBalanceSheet(
            Long orgId
    ) {

        requireOrganizationId(orgId);

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

        BigDecimal totalAssets =
                ZERO;

        BigDecimal totalLiabilities =
                ZERO;

        BigDecimal totalEquity =
                ZERO;

        BigDecimal totalIncome =
                ZERO;

        BigDecimal totalExpense =
                ZERO;

        BigDecimal contraAssets =
                ZERO;

        if (accounts != null) {

            for (
                    ChartOfAccount acc :
                    accounts
            ) {

                if (acc == null) {
                    continue;
                }

                BigDecimal balance =
                        netBalance(
                                acc,
                                orgId
                        );

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

                        if (
                                acc.getNormalBalance()
                                        == ChartOfAccount.NormalBalance.CREDIT
                        ) {

                            contraAssets =
                                    contraAssets.add(
                                            balance
                                    );

                            totalAssets =
                                    totalAssets.subtract(
                                            balance
                                    );

                        } else {

                            totalAssets =
                                    totalAssets.add(
                                            balance
                                    );
                        }
                    }

                    case LIABILITY ->
                            totalLiabilities =
                                    totalLiabilities.add(
                                            balance
                                    );

                    case EQUITY ->
                            totalEquity =
                                    totalEquity.add(
                                            balance
                                    );

                    case INCOME ->
                            totalIncome =
                                    totalIncome.add(
                                            balance
                                    );

                    case EXPENSE ->
                            totalExpense =
                                    totalExpense.add(
                                            balance
                                    );
                }
            }
        }

        BigDecimal netIncome =
                totalIncome.subtract(
                        totalExpense
                );

        totalEquity =
                totalEquity.add(
                        netIncome
                );

        BigDecimal liabilitiesPlusEquity =
                totalLiabilities.add(
                        totalEquity
                );

        BigDecimal balanceDifference =
                totalAssets.subtract(
                        liabilitiesPlusEquity
                );

        totalAssets =
                normalize(totalAssets);

        totalLiabilities =
                normalize(totalLiabilities);

        totalEquity =
                normalize(totalEquity);

        netIncome =
                normalize(netIncome);

        liabilitiesPlusEquity =
                normalize(liabilitiesPlusEquity);

        balanceDifference =
                normalize(balanceDifference);

        contraAssets =
                normalize(contraAssets);

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
                "contraAssets",
                contraAssets
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
                liabilitiesPlusEquity
        );

        result.put(
                "balanceDifference",
                balanceDifference
        );

        result.put(
                "balanced",
                balanceDifference.compareTo(
                        ZERO
                ) == 0
        );

        return result;
    }

    /*
     * ============================================================
     * PROFIT AND LOSS
     * ============================================================
     */

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

        requireOrganizationId(orgId);

        List<JournalEntry> entries =
                journalRepo
                        .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
                                orgId,
                                from,
                                to
                        );

        Map<String, BigDecimal> perAccount =
                new LinkedHashMap<>();

        Map<String, String> names =
                new LinkedHashMap<>();

        Map<
                String,
                ChartOfAccount.AccountType
        > types =
                new LinkedHashMap<>();

        if (entries != null) {

            for (
                    JournalEntry entry :
                    entries
            ) {

                if (entry == null) {
                    continue;
                }

                if (entry.getLines() == null) {
                    continue;
                }

                for (
                        JournalLine line :
                        entry.getLines()
                ) {

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

                    BigDecimal debit =
                            money(
                                    line.getDebitDecimal()
                            );

                    BigDecimal credit =
                            money(
                                    line.getCreditDecimal()
                            );

                    BigDecimal net =
                            acc.getType()
                                    == ChartOfAccount.AccountType.INCOME
                                    ? credit.subtract(debit)
                                    : debit.subtract(credit);

                    perAccount.merge(
                            acc.getCode(),
                            net,
                            BigDecimal::add
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

        BigDecimal totalIncome =
                ZERO;

        BigDecimal totalExpense =
                ZERO;

        for (
                Map.Entry<String, BigDecimal> entry :
                perAccount.entrySet()
        ) {

            BigDecimal amount =
                    normalize(
                            entry.getValue()
                    );

            if (amount.compareTo(ZERO) == 0) {
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
                    names.get(
                            entry.getKey()
                    )
            );

            row.put(
                    "amount",
                    amount
            );

            if (
                    types.get(
                            entry.getKey()
                    )
                            == ChartOfAccount.AccountType.INCOME
            ) {

                income.add(row);

                totalIncome =
                        totalIncome.add(amount);

            } else {

                expense.add(row);

                totalExpense =
                        totalExpense.add(amount);
            }
        }

        BigDecimal netIncome =
                totalIncome.subtract(
                        totalExpense
                );

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
                normalize(totalIncome)
        );

        result.put(
                "totalExpense",
                normalize(totalExpense)
        );

        result.put(
                "netIncome",
                normalize(netIncome)
        );

        return result;
    }

    /*
     * ============================================================
     * CASH FLOW
     * ============================================================
     */

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

        requireOrganizationId(orgId);

        List<JournalEntry> entries =
                journalRepo
                        .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
                                orgId,
                                from,
                                to
                        );

        BigDecimal lending =
                ZERO;

        BigDecimal collections =
                ZERO;

        BigDecimal feesAndPenalties =
                ZERO;

        BigDecimal refunds =
                ZERO;

        BigDecimal other =
                ZERO;

        if (entries != null) {

            for (
                    JournalEntry entry :
                    entries
            ) {

                if (entry == null) {
                    continue;
                }

                if (entry.getLines() == null) {
                    continue;
                }

                for (
                        JournalLine line :
                        entry.getLines()
                ) {

                    if (
                            line == null
                            || line.getAccount() == null
                    ) {
                        continue;
                    }

                    if (
                            !"1000".equals(
                                    line
                                            .getAccount()
                                            .getCode()
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

                    BigDecimal net =
                            debit.subtract(
                                    credit
                            );

                    String source =
                            entry.getSourceType() != null
                                    ? entry.getSourceType()
                                    : "";

                    switch (source) {

                        case "LOAN_DISBURSEMENT" ->

                                lending =
                                        lending.add(net);

                        case "PAYMENT_RECEIVED" ->

                                collections =
                                        collections.add(net);

                        case "PROCESSING_FEE" ->

                                feesAndPenalties =
                                        feesAndPenalties.add(net);

                        case "REFUND_PAYMENT" ->

                                refunds =
                                        refunds.add(net);

                        default ->

                                other =
                                        other.add(net);
                    }
                }
            }
        }

        BigDecimal netChange =
                lending
                        .add(collections)
                        .add(feesAndPenalties)
                        .add(refunds)
                        .add(other);

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
                normalize(lending)
        );

        result.put(
                "cashFromCollections",
                normalize(collections)
        );

        result.put(
                "cashFromFees",
                normalize(feesAndPenalties)
        );

        result.put(
                "cashRefundedToBorrowers",
                normalize(refunds)
        );

        result.put(
                "otherCashMovement",
                normalize(other)
        );

        result.put(
                "netChangeInCash",
                normalize(netChange)
        );

        return result;
    }

    /*
     * ============================================================
     * BRANCH SUMMARY
     * ============================================================
     */

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

        requireOrganizationId(orgId);

        List<JournalEntry> entries =
                journalRepo
                        .findByOrganization_IdAndEntryDateBetweenOrderByEntryDateAsc(
                                orgId,
                                from,
                                to
                        );

        Map<
                String,
                BigDecimal[]
        > byBranch =
                new LinkedHashMap<>();

        if (entries != null) {

            for (
                    JournalEntry entry :
                    entries
            ) {

                if (entry == null) {
                    continue;
                }

                String branchName =
                        entry.getBranch() != null
                                && entry
                                        .getBranch()
                                        .getName() != null
                                ? entry
                                        .getBranch()
                                        .getName()
                                : "Unassigned";

                BigDecimal[] totals =
                        byBranch.computeIfAbsent(
                                branchName,
                                k ->
                                        new BigDecimal[]{
                                                ZERO,
                                                ZERO,
                                                ZERO,
                                                ZERO
                                        }
                        );

                if (entry.getLines() == null) {
                    continue;
                }

                String source =
                        entry.getSourceType() != null
                                ? entry.getSourceType()
                                : "";

                for (
                        JournalLine line :
                        entry.getLines()
                ) {

                    if (
                            line == null
                            || line.getAccount() == null
                    ) {
                        continue;
                    }

                    if (
                            !"1000".equals(
                                    line
                                            .getAccount()
                                            .getCode()
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

                    switch (source) {

                        case "LOAN_DISBURSEMENT" -> {

                            BigDecimal disbursed =
                                    credit.subtract(debit);

                            if (
                                    disbursed.compareTo(ZERO) < 0
                            ) {
                                throw new IllegalStateException(
                                        "Invalid cash direction for " +
                                        "loan disbursement journal " +
                                        entry.getId()
                                );
                            }

                            totals[0] =
                                    totals[0].add(
                                            disbursed
                                    );
                        }

                        case "PAYMENT_RECEIVED" -> {

                            BigDecimal collected =
                                    debit.subtract(
                                            credit
                                    );

                            if (
                                    collected.compareTo(ZERO) < 0
                            ) {
                                throw new IllegalStateException(
                                        "Invalid cash direction for " +
                                        "payment journal " +
                                        entry.getId()
                                );
                            }

                            totals[1] =
                                    totals[1].add(
                                            collected
                                    );
                        }

                        case "PROCESSING_FEE" -> {

                            BigDecimal fee =
                                    debit.subtract(
                                            credit
                                    );

                            if (
                                    fee.compareTo(ZERO) < 0
                            ) {
                                throw new IllegalStateException(
                                        "Invalid cash direction for " +
                                        "processing-fee journal " +
                                        entry.getId()
                                );
                            }

                            totals[2] =
                                    totals[2].add(
                                            fee
                                    );
                        }

                        case "REFUND_PAYMENT" -> {

                            BigDecimal refund =
                                    credit.subtract(
                                            debit
                                    );

                            if (
                                    refund.compareTo(ZERO) < 0
                            ) {
                                throw new IllegalStateException(
                                        "Invalid cash direction for " +
                                        "refund journal " +
                                        entry.getId()
                                );
                            }

                            totals[3] =
                                    totals[3].add(
                                            refund
                                    );
                        }

                        default -> {
                            // Not part of this summary.
                        }
                    }
                }
            }
        }

        List<Map<String, Object>> rows =
                new ArrayList<>();

        for (
                Map.Entry<
                        String,
                        BigDecimal[]
                > entry :
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
                    normalize(
                            entry.getValue()[0]
                    )
            );

            row.put(
                    "collected",
                    normalize(
                            entry.getValue()[1]
                    )
            );

            row.put(
                    "feeIncome",
                    normalize(
                            entry.getValue()[2]
                    )
            );

            row.put(
                    "refunded",
                    normalize(
                            entry.getValue()[3]
                    )
            );

            rows.add(row);
        }

        return rows;
    }

    /*
     * ============================================================
     * NET ACCOUNT BALANCE
     * ============================================================
     */

    private BigDecimal netBalance(
            ChartOfAccount acc,
            Long orgId
    ) {

        if (
                acc == null
                || acc.getId() == null
        ) {
            return ZERO;
        }

        List<JournalLine> lines =
                lineRepo.findByAccount_IdAndOrganization_Id(
                        acc.getId(),
                        orgId
                );

        if (
                lines == null
                || lines.isEmpty()
        ) {
            return ZERO;
        }

        BigDecimal debit =
                ZERO;

        BigDecimal credit =
                ZERO;

        for (
                JournalLine line :
                lines
        ) {

            if (line == null) {
                continue;
            }

            debit =
                    debit.add(
                            money(
                                    line.getDebitDecimal()
                            )
                    );

            credit =
                    credit.add(
                            money(
                                    line.getCreditDecimal()
                            )
                    );
        }

        if (
                acc.getNormalBalance()
                        == ChartOfAccount.NormalBalance.DEBIT
        ) {

            return normalize(
                    debit.subtract(credit)
            );
        }

        return normalize(
                credit.subtract(debit)
        );
    }
}