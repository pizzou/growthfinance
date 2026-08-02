
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.regulatory.BnrBreakdownRow;
import com.patrick.fintech.loan_backend.dto.regulatory.BnrFinancialStatementReport;
import com.patrick.fintech.loan_backend.dto.regulatory.BnrSummaryReport;
import com.patrick.fintech.loan_backend.dto.regulatory.CreditBureauRecord;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class RegulatoryReportingService {

    private final LoanRepository loanRepository;

    private final OrganizationRepository organizationRepository;

    private final AccountingService accountingService;


    // ============================================================
    // REPORT PERIOD
    // ============================================================

    public enum ReportPeriod {

        DAILY,

        WEEKLY,

        MONTHLY,

        QUARTERLY,

        YEARLY,

        CUSTOM
    }


    // ============================================================
    // RESOLVE REPORTING PERIOD
    // ============================================================

    public LocalDate[] resolvePeriod(
            ReportPeriod period,
            LocalDate from,
            LocalDate to
    ) {

        LocalDate today =
                LocalDate.now();

        if (period == null) {

            period =
                    ReportPeriod.CUSTOM;
        }


        return switch (period) {

            // ----------------------------------------------------
            // DAILY
            // ----------------------------------------------------

            case DAILY ->

                    new LocalDate[]{
                            today,
                            today.plusDays(1)
                    };


            // ----------------------------------------------------
            // WEEKLY
            // ----------------------------------------------------

            case WEEKLY -> {

                LocalDate start =
                        today.with(
                                TemporalAdjusters.previousOrSame(
                                        DayOfWeek.MONDAY
                                )
                        );

                yield new LocalDate[]{
                        start,
                        today.plusDays(1)
                };
            }


            // ----------------------------------------------------
            // MONTHLY
            // ----------------------------------------------------

            case MONTHLY -> {

                LocalDate start =
                        today.withDayOfMonth(1);

                yield new LocalDate[]{
                        start,
                        today.plusDays(1)
                };
            }


            // ----------------------------------------------------
            // QUARTERLY
            // ----------------------------------------------------

            case QUARTERLY -> {

                int quarterStartMonth =
                        ((today.getMonthValue() - 1) / 3) * 3 + 1;

                LocalDate start =
                        LocalDate.of(
                                today.getYear(),
                                quarterStartMonth,
                                1
                        );

                yield new LocalDate[]{
                        start,
                        today.plusDays(1)
                };
            }


            // ----------------------------------------------------
            // YEARLY
            // ----------------------------------------------------

            case YEARLY -> {

                LocalDate start =
                        today.withDayOfYear(1);

                yield new LocalDate[]{
                        start,
                        today.plusDays(1)
                };
            }


            // ----------------------------------------------------
            // CUSTOM
            // ----------------------------------------------------

            case CUSTOM -> {

                LocalDate exclusiveTo =
                        to == null
                                ? null
                                : to.plusDays(1);

                yield new LocalDate[]{
                        from,
                        exclusiveTo
                };
            }
        };
    }


    // ============================================================
    // FETCH LOANS
    // ============================================================

    private List<Loan> fetchLoans(
            Long orgId,
            Long branchId,
            LocalDate from,
            LocalDate to
    ) {

        LocalDateTime fromDt =
                from == null
                        ? null
                        : from.atStartOfDay();


        LocalDateTime toDt =
                to == null
                        ? null
                        : to.atStartOfDay();


        return loanRepository.findForRegulatoryReport(
                orgId,
                branchId,
                fromDt,
                toDt
        );
    }


    // ============================================================
    // BNR SUMMARY
    // ============================================================

    @Transactional(readOnly = true)
    public BnrSummaryReport buildBnrSummary(
            Long orgId,
            Long branchId,
            ReportPeriod period,
            LocalDate from,
            LocalDate to
    ) {

        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }


        LocalDate[] window =
                resolvePeriod(
                        period,
                        from,
                        to
                );


        List<Loan> loans =
                fetchLoans(
                        orgId,
                        branchId,
                        window[0],
                        window[1]
                );


        Organization org =
                organizationRepository
                        .findById(orgId)
                        .orElse(null);


        // ========================================================
        // COUNTERS
        // ========================================================

        long active = 0;

        long closed = 0;

        long pending = 0;

        long rejected = 0;

        long overdue = 0;

        long defaulted = 0;


        // ========================================================
        // FINANCIAL TOTALS
        // ========================================================

        double principalDisbursed = 0.0;

        double outstanding = 0.0;

        double interestCollected = 0.0;

        double interestAccrued = 0.0;

        double fees = 0.0;


        // ========================================================
        // GENDER
        // ========================================================

        long male = 0;

        long female = 0;

        long other = 0;


        // ========================================================
        // PORTFOLIO RISK
        // ========================================================

        double parAmount = 0.0;

        double nplAmount = 0.0;


        // ========================================================
        // PROCESS LOANS
        // ========================================================

        for (Loan loan : loans) {

            if (loan == null) {
                continue;
            }


            LoanStatus status =
                    loan.getStatus();


            // ----------------------------------------------------
            // STATUS COUNTS
            // ----------------------------------------------------

            if (status != null) {

                switch (status) {

                    case ACTIVE:
                    case DISBURSED:

                        active++;

                        break;


                    case CLOSED:
                    case PAID:

                        closed++;

                        break;


                    case PENDING:
                    case UNDER_REVIEW:
                    case APPROVED:

                        pending++;

                        break;


                    case REJECTED:
                    case CANCELLED:

                        rejected++;

                        break;


                    default:

                        break;
                }


                // ------------------------------------------------
                // DEFAULTED
                // ------------------------------------------------

                if (
                        status == LoanStatus.DEFAULTED
                                || status == LoanStatus.WRITTEN_OFF
                ) {

                    defaulted++;
                }
            }


            // ----------------------------------------------------
            // DAYS OVERDUE
            // ----------------------------------------------------

            Integer daysOverdue =
                    loan.getDaysOverdue();


            /*
             * A loan is overdue when:
             *
             * 1. Its status is explicitly OVERDUE
             *
             * OR
             *
             * 2. It has positive days overdue.
             *
             * The loan is counted only once.
             */

            boolean isOverdue =
                    status == LoanStatus.OVERDUE
                            || (
                            daysOverdue != null
                                    && daysOverdue > 0
                    );


            if (isOverdue) {

                overdue++;
            }


            // ----------------------------------------------------
            // PRINCIPAL DISBURSED
            // ----------------------------------------------------

            if (
                    loan.getDisbursedAmount() != null
            ) {

                principalDisbursed +=
                        loan.getDisbursedAmount();

            } else if (
                    loan.getAmount() != null
            ) {

                principalDisbursed +=
                        loan.getAmount();
            }


            // ----------------------------------------------------
            // OUTSTANDING
            // ----------------------------------------------------

            if (
                    loan.getOutstandingBalance() != null
            ) {

                outstanding +=
                        loan.getOutstandingBalance();
            }


            // ----------------------------------------------------
            // PROCESSING FEES
            // ----------------------------------------------------

            if (
                    loan.getProcessingFee() != null
            ) {

                fees +=
                        loan.getProcessingFee();
            }


            // ----------------------------------------------------
            // INTEREST
            // ----------------------------------------------------

            if (
                    loan.getPayments() != null
            ) {

                for (
                        Payment payment :
                        loan.getPayments()
                ) {

                    if (
                            payment == null
                                    || payment.getInterestComponent() == null
                    ) {
                        continue;
                    }


                    if (
                            Boolean.TRUE.equals(
                                    payment.getPaid()
                            )
                    ) {

                        interestCollected +=
                                payment.getInterestComponent();

                    } else {

                        interestAccrued +=
                                payment.getInterestComponent();
                    }
                }
            }


            // ----------------------------------------------------
            // GENDER
            // ----------------------------------------------------

            Borrower borrower =
                    loan.getBorrower();


            String gender =
                    borrower != null
                            ? borrower.getGender()
                            : null;


            if (gender != null) {

                switch (
                        gender
                                .trim()
                                .toUpperCase()
                ) {

                    case "MALE":
                    case "M":

                        male++;

                        break;


                    case "FEMALE":
                    case "F":

                        female++;

                        break;


                    default:

                        other++;

                        break;
                }
            }


            // ----------------------------------------------------
            // LOAN OUTSTANDING
            // ----------------------------------------------------

            double loanOutstanding =
                    loan.getOutstandingBalance() != null
                            ? loan.getOutstandingBalance()
                            : 0.0;


            // ----------------------------------------------------
            // PAR / NPL
            // ----------------------------------------------------

            if (
                    daysOverdue != null
                            && daysOverdue > 0
                            && loanOutstanding > 0
            ) {

                parAmount +=
                        loanOutstanding;


                if (
                        daysOverdue > 90
                ) {

                    nplAmount +=
                            loanOutstanding;
                }
            }


            // ----------------------------------------------------
            // DEFAULTED / WRITTEN OFF AS NPL
            // ----------------------------------------------------

            if (
                    status == LoanStatus.DEFAULTED
                            || status == LoanStatus.WRITTEN_OFF
            ) {

                if (
                        loanOutstanding > 0
                                && (
                                daysOverdue == null
                                        || daysOverdue <= 90
                        )
                ) {

                    nplAmount +=
                            loanOutstanding;
                }
            }
        }


        // ========================================================
        // REPORT PERIOD
        // ========================================================

        String reportPeriod =
                (
                        period == null
                                ? ReportPeriod.CUSTOM
                                : period
                ).name();


        LocalDate periodEnd =
                window[1] == null
                        ? null
                        : window[1].minusDays(1);


        // ========================================================
        // BUILD SUMMARY
        // ========================================================

        return BnrSummaryReport.builder()

                .organizationId(
                        orgId
                )

                .organizationName(
                        org != null
                                ? org.getName()
                                : null
                )

                .bnrInstitutionCode(
                        org != null
                                ? org.getRegistrationNumber()
                                : null
                )

                .branchId(
                        branchId
                )

                .branchName(
                        null
                )

                .reportPeriod(
                        reportPeriod
                )

                .periodStart(
                        window[0]
                )

                .periodEnd(
                        periodEnd
                )

                .totalLoansIssued(
                        loans.size()
                )

                .activeLoans(
                        active
                )

                .closedLoans(
                        closed
                )

                .pendingLoans(
                        pending
                )

                .rejectedLoans(
                        rejected
                )

                .overdueLoans(
                        overdue
                )

                .defaultedLoans(
                        defaulted
                )

                .totalPrincipalDisbursed(
                        principalDisbursed
                )

                .outstandingPrincipal(
                        outstanding
                )

                .totalInterestCollected(
                        interestCollected
                )

                .interestAccruedUnpaid(
                        interestAccrued
                )

                .totalProcessingFees(
                        fees
                )

                .maleBorrowers(
                        male
                )

                .femaleBorrowers(
                        female
                )

                .otherGenderBorrowers(
                        other
                )

                .parAmount(
                        parAmount
                )

                .parRatio(
                        outstanding > 0
                                ? parAmount / outstanding
                                : 0.0
                )

                .nplAmount(
                        nplAmount
                )

                .nplRatio(
                        outstanding > 0
                                ? nplAmount / outstanding
                                : 0.0
                )

                .currency(
                        org != null
                                && org.getDefaultCurrency() != null
                                ? org.getDefaultCurrency()
                                : "RWF"
                )

                .generatedAt(
                        LocalDateTime.now()
                )

                .build();
    }


    // ============================================================
    // BNR - LOAN TYPE
    // ============================================================

    @Transactional(readOnly = true)
    public List<BnrBreakdownRow> breakdownByLoanType(
            Long orgId,
            Long branchId,
            ReportPeriod period,
            LocalDate from,
            LocalDate to
    ) {

        LocalDate[] window =
                resolvePeriod(
                        period,
                        from,
                        to
                );


        List<Loan> loans =
                fetchLoans(
                        orgId,
                        branchId,
                        window[0],
                        window[1]
                );


        return groupAndSum(
                loans,
                loan ->
                        loan.getLoanType() == null
                                ? "UNSPECIFIED"
                                : loan.getLoanType().name()
        );
    }


    // ============================================================
    // BNR - BRANCH
    // ============================================================

    @Transactional(readOnly = true)
    public List<BnrBreakdownRow> breakdownByBranch(
            Long orgId,
            ReportPeriod period,
            LocalDate from,
            LocalDate to
    ) {

        LocalDate[] window =
                resolvePeriod(
                        period,
                        from,
                        to
                );


        List<Loan> loans =
                fetchLoans(
                        orgId,
                        null,
                        window[0],
                        window[1]
                );


        return groupAndSum(
                loans,
                loan ->
                        loan.getBranch() == null
                                ? "Unassigned"
                                : loan.getBranch().getName()
        );
    }


    // ============================================================
    // BNR - GENDER
    // ============================================================

    @Transactional(readOnly = true)
    public List<BnrBreakdownRow> breakdownByGender(
            Long orgId,
            Long branchId,
            ReportPeriod period,
            LocalDate from,
            LocalDate to
    ) {

        LocalDate[] window =
                resolvePeriod(
                        period,
                        from,
                        to
                );


        List<Loan> loans =
                fetchLoans(
                        orgId,
                        branchId,
                        window[0],
                        window[1]
                );


        return groupAndSum(
                loans,
                loan -> {

                    String gender =
                            loan.getBorrower() != null
                                    ? loan.getBorrower().getGender()
                                    : null;


                    if (gender == null) {
                        return "UNSPECIFIED";
                    }


                    return switch (
                            gender
                                    .trim()
                                    .toUpperCase()
                    ) {

                        case "MALE", "M" ->

                                "MALE";


                        case "FEMALE", "F" ->

                                "FEMALE";


                        default ->

                                "OTHER";
                    };
                }
        );
    }


    // ============================================================
    // BNR - PAR AGING
    // ============================================================

    /**
     * Breaks the outstanding portfolio into aging buckets.
     *
     * CURRENT
     * 1-30 DAYS
     * 31-60 DAYS
     * 61-90 DAYS
     * 91-180 DAYS
     * 181-365 DAYS
     * 365+ DAYS
     */

    @Transactional(readOnly = true)
    public List<BnrBreakdownRow> breakdownByParBucket(
            Long orgId,
            Long branchId,
            ReportPeriod period,
            LocalDate from,
            LocalDate to
    ) {

        LocalDate[] window =
                resolvePeriod(
                        period,
                        from,
                        to
                );


        List<Loan> loans =
                fetchLoans(
                        orgId,
                        branchId,
                        window[0],
                        window[1]
                );


        List<String> buckets =
                List.of(
                        "CURRENT",
                        "1-30 DAYS",
                        "31-60 DAYS",
                        "61-90 DAYS",
                        "91-180 DAYS",
                        "181-365 DAYS",
                        "365+ DAYS"
                );


        Map<String, Long> counts =
                new LinkedHashMap<>();


        Map<String, Double> amounts =
                new LinkedHashMap<>();


        for (String bucket : buckets) {

            counts.put(
                    bucket,
                    0L
            );


            amounts.put(
                    bucket,
                    0.0
            );
        }


        // ========================================================
        // PROCESS LOANS
        // ========================================================

        for (Loan loan : loans) {

            if (loan == null) {
                continue;
            }


            double outstanding =
                    loan.getOutstandingBalance() != null
                            ? loan.getOutstandingBalance()
                            : 0.0;


            /*
             * No outstanding principal means there is nothing
             * to include in the portfolio aging amount.
             */

            if (outstanding <= 0.0) {
                continue;
            }


            int daysOverdue =
                    loan.getDaysOverdue() != null
                            ? Math.max(
                            loan.getDaysOverdue(),
                            0
                    )
                            : 0;


            String bucket;


            if (daysOverdue == 0) {

                bucket =
                        "CURRENT";

            } else if (daysOverdue <= 30) {

                bucket =
                        "1-30 DAYS";

            } else if (daysOverdue <= 60) {

                bucket =
                        "31-60 DAYS";

            } else if (daysOverdue <= 90) {

                bucket =
                        "61-90 DAYS";

            } else if (daysOverdue <= 180) {

                bucket =
                        "91-180 DAYS";

            } else if (daysOverdue <= 365) {

                bucket =
                        "181-365 DAYS";

            } else {

                bucket =
                        "365+ DAYS";
            }


            counts.put(
                    bucket,
                    counts.get(bucket) + 1
            );


            amounts.put(
                    bucket,
                    amounts.get(bucket) + outstanding
            );
        }


        // ========================================================
        // BUILD RESULT
        // ========================================================

        List<BnrBreakdownRow> result =
                new ArrayList<>();


        for (String bucket : buckets) {

            result.add(
                    new BnrBreakdownRow(
                            bucket,
                            counts.get(bucket),
                            amounts.get(bucket)
                    )
            );
        }


        return result;
    }


    // ============================================================
    // GENERIC BNR BREAKDOWN
    // ============================================================

    private List<BnrBreakdownRow> groupAndSum(
            List<Loan> loans,
            Function<Loan, String> keyFn
    ) {

        Map<String, Long> counts =
                new LinkedHashMap<>();


        Map<String, Double> amounts =
                new LinkedHashMap<>();


        for (Loan loan : loans) {

            if (loan == null) {
                continue;
            }


            String key =
                    keyFn.apply(loan);


            if (
                    key == null
                            || key.isBlank()
            ) {

                key =
                        "UNSPECIFIED";
            }


            counts.put(
                    key,
                    counts.getOrDefault(
                            key,
                            0L
                    ) + 1
            );


            double amount =
                    0.0;


            if (
                    loan.getDisbursedAmount() != null
            ) {

                amount =
                        loan.getDisbursedAmount();

            } else if (
                    loan.getAmount() != null
            ) {

                amount =
                        loan.getAmount();
            }


            amounts.put(
                    key,
                    amounts.getOrDefault(
                            key,
                            0.0
                    ) + amount
            );
        }


        return counts.entrySet()
                .stream()

                .map(
                        entry ->
                                new BnrBreakdownRow(
                                        entry.getKey(),
                                        entry.getValue(),
                                        amounts.getOrDefault(
                                                entry.getKey(),
                                                0.0
                                        )
                                )
                )

                .sorted(
                        Comparator.comparing(
                                BnrBreakdownRow::getLabel
                        )
                )

                .collect(
                        Collectors.toList()
                );
    }


    // ============================================================
    // BNR FINANCIAL STATEMENT
    // ============================================================

    @Transactional(readOnly = true)
    public BnrFinancialStatementReport buildBnrFinancialStatement(
            Long orgId,
            Long branchId,
            ReportPeriod period,
            LocalDate from,
            LocalDate to
    ) {

        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }


        // --------------------------------------------------------
        // Resolve reporting period
        // --------------------------------------------------------

        LocalDate[] window =
                resolvePeriod(
                        period,
                        from,
                        to
                );


        LocalDate periodStart =
                window[0];


        LocalDate periodEnd =
                window[1] == null
                        ? null
                        : window[1].minusDays(1);


        // --------------------------------------------------------
        // Organization
        // --------------------------------------------------------

        Organization org =
                organizationRepository
                        .findById(orgId)
                        .orElse(null);


        // --------------------------------------------------------
        // Financial statements
        // --------------------------------------------------------

        Map<String, Object> balanceSheet =
                accountingService.getBalanceSheet(
                        orgId
                );


        Map<String, Object> profitAndLoss =
                accountingService.getProfitAndLoss(
                        orgId,
                        periodStart,
                        periodEnd
                );


        Map<String, Object> cashFlow =
                accountingService.getCashFlow(
                        orgId,
                        periodStart,
                        periodEnd
                );


        Map<String, Object> trialBalance =
                accountingService.getTrialBalance(
                        orgId
                );


        // --------------------------------------------------------
        // Branch name
        // --------------------------------------------------------

        String branchName =
                null;


        if (branchId != null) {

            List<Loan> branchLoans =
                    fetchLoans(
                            orgId,
                            branchId,
                            periodStart,
                            window[1]
                    );


            if (
                    !branchLoans.isEmpty()
                            && branchLoans.get(0).getBranch() != null
            ) {

                branchName =
                        branchLoans
                                .get(0)
                                .getBranch()
                                .getName();
            }
        }


        // --------------------------------------------------------
        // Extract balance sheet
        // --------------------------------------------------------

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assets =
                (List<Map<String, Object>>)
                        balanceSheet.get(
                                "assets"
                        );


        @SuppressWarnings("unchecked")
        List<Map<String, Object>> liabilities =
                (List<Map<String, Object>>)
                        balanceSheet.get(
                                "liabilities"
                        );


        @SuppressWarnings("unchecked")
        List<Map<String, Object>> equity =
                (List<Map<String, Object>>)
                        balanceSheet.get(
                                "equity"
                        );


        // --------------------------------------------------------
        // Extract P&L
        // --------------------------------------------------------

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> income =
                (List<Map<String, Object>>)
                        profitAndLoss.get(
                                "income"
                        );


        @SuppressWarnings("unchecked")
        List<Map<String, Object>> expenses =
                (List<Map<String, Object>>)
                        profitAndLoss.get(
                                "expense"
                        );


        // --------------------------------------------------------
        // Extract totals
        // --------------------------------------------------------

        double totalAssets =
                toDouble(
                        balanceSheet.get(
                                "totalAssets"
                        )
                );


        double totalLiabilities =
                toDouble(
                        balanceSheet.get(
                                "totalLiabilities"
                        )
                );


        double totalEquity =
                toDouble(
                        balanceSheet.get(
                                "totalEquity"
                        )
                );


        double currentPeriodNetIncome =
                toDouble(
                        balanceSheet.get(
                                "currentPeriodNetIncome"
                        )
                );


        double totalIncome =
                toDouble(
                        profitAndLoss.get(
                                "totalIncome"
                        )
                );


        double totalExpenses =
                toDouble(
                        profitAndLoss.get(
                                "totalExpense"
                        )
                );


        double netIncome =
                toDouble(
                        profitAndLoss.get(
                                "netIncome"
                        )
                );


        // --------------------------------------------------------
        // Cash flow
        // --------------------------------------------------------

        double cashUsedForLending =
                toDouble(
                        cashFlow.get(
                                "cashUsedForLending"
                        )
                );


        double cashFromCollections =
                toDouble(
                        cashFlow.get(
                                "cashFromCollections"
                        )
                );


        double cashFromFees =
                toDouble(
                        cashFlow.get(
                                "cashFromFees"
                        )
                );


        double otherCashMovement =
                toDouble(
                        cashFlow.get(
                                "otherCashMovement"
                        )
                );


        double netChangeInCash =
                toDouble(
                        cashFlow.get(
                                "netChangeInCash"
                        )
                );


        // --------------------------------------------------------
        // Trial balance
        // --------------------------------------------------------

        double trialBalanceDebit =
                toDouble(
                        trialBalance.get(
                                "totalDebit"
                        )
                );


        double trialBalanceCredit =
                toDouble(
                        trialBalance.get(
                                "totalCredit"
                        )
                );


        boolean trialBalanceBalanced =
                Boolean.TRUE.equals(
                        trialBalance.get(
                                "balanced"
                        )
                );


        // --------------------------------------------------------
        // Report period name
        // --------------------------------------------------------

        String reportPeriod =
                (
                        period == null
                                ? ReportPeriod.CUSTOM
                                : period
                ).name();


        // --------------------------------------------------------
        // Currency
        // --------------------------------------------------------

        String currency =
                org != null
                        && org.getDefaultCurrency() != null
                        && !org.getDefaultCurrency().isBlank()

                        ? org.getDefaultCurrency()

                        : "RWF";


        // --------------------------------------------------------
        // Build financial report
        // --------------------------------------------------------

        return BnrFinancialStatementReport.builder()

                .organizationId(
                        orgId
                )

                .organizationName(
                        org != null
                                ? org.getName()
                                : null
                )

                .bnrInstitutionCode(
                        org != null
                                ? org.getRegistrationNumber()
                                : null
                )

                .branchId(
                        branchId
                )

                .branchName(
                        branchName
                )

                .currency(
                        currency
                )

                .reportPeriod(
                        reportPeriod
                )

                .periodStart(
                        periodStart
                )

                .periodEnd(
                        periodEnd
                )

                .generatedAt(
                        LocalDateTime.now()
                )


                // ------------------------------------------------
                // BALANCE SHEET
                // ------------------------------------------------

                .assets(
                        assets != null
                                ? assets
                                : new ArrayList<>()
                )

                .liabilities(
                        liabilities != null
                                ? liabilities
                                : new ArrayList<>()
                )

                .equity(
                        equity != null
                                ? equity
                                : new ArrayList<>()
                )

                .totalAssets(
                        totalAssets
                )

                .totalLiabilities(
                        totalLiabilities
                )

                .totalEquity(
                        totalEquity
                )

                .currentPeriodNetIncome(
                        currentPeriodNetIncome
                )

                .balanceSheetBalanced(
                        Boolean.TRUE.equals(
                                balanceSheet.get(
                                        "balanced"
                                )
                        )
                )


                // ------------------------------------------------
                // PROFIT & LOSS
                // ------------------------------------------------

                .income(
                        income != null
                                ? income
                                : new ArrayList<>()
                )

                .expenses(
                        expenses != null
                                ? expenses
                                : new ArrayList<>()
                )

                .totalIncome(
                        totalIncome
                )

                .totalExpenses(
                        totalExpenses
                )

                .netIncome(
                        netIncome
                )


                // ------------------------------------------------
                // CASH FLOW
                // ------------------------------------------------

                .cashUsedForLending(
                        cashUsedForLending
                )

                .cashFromCollections(
                        cashFromCollections
                )

                .cashFromFees(
                        cashFromFees
                )

                .otherCashMovement(
                        otherCashMovement
                )

                .netChangeInCash(
                        netChangeInCash
                )


                // ------------------------------------------------
                // TRIAL BALANCE CONTROL
                // ------------------------------------------------

                .trialBalanceDebit(
                        trialBalanceDebit
                )

                .trialBalanceCredit(
                        trialBalanceCredit
                )

                .trialBalanceBalanced(
                        trialBalanceBalanced
                )

                .build();
    }


    // ============================================================
    // SAFE NUMBER CONVERSION
    // ============================================================

    private double toDouble(
            Object value
    ) {

        if (value == null) {
            return 0.0;
        }


        if (value instanceof Number number) {

            return number.doubleValue();
        }


        try {

            return Double.parseDouble(
                    value.toString()
            );

        } catch (NumberFormatException e) {

            return 0.0;
        }
    }


    // ============================================================
    // CREDIT BUREAU EXPORT
    // ============================================================

    @Transactional(readOnly = true)
    public List<CreditBureauRecord> buildCreditBureauExport(
            Long orgId,
            Long branchId,
            LocalDate from,
            LocalDate to
    ) {

        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }


        /*
         * Credit Bureau export receives an inclusive
         * 'to' date from the caller.
         *
         * Convert it to an exclusive repository date.
         */

        LocalDate exclusiveTo =
                to == null
                        ? null
                        : to.plusDays(1);


        List<Loan> loans =
                fetchLoans(
                        orgId,
                        branchId,
                        from,
                        exclusiveTo
                );


        List<CreditBureauRecord> records =
                new ArrayList<>();


        for (Loan loan : loans) {

            if (loan == null) {
                continue;
            }


            Borrower borrower =
                    loan.getBorrower();


            // ----------------------------------------------------
            // CLOSED LOAN
            // ----------------------------------------------------

            boolean closed =
                    loan.getStatus() == LoanStatus.CLOSED
                            || loan.getStatus() == LoanStatus.PAID;


            // ----------------------------------------------------
            // BORROWER NAME
            // ----------------------------------------------------

            String fullName =
                    null;


            if (borrower != null) {

                String firstName =
                        borrower.getFirstName() != null
                                ? borrower.getFirstName()
                                : "";


                String lastName =
                        borrower.getLastName() != null
                                ? borrower.getLastName()
                                : "";


                fullName =
                        (
                                firstName
                                        + " "
                                        + lastName
                        ).trim();
            }


            // ----------------------------------------------------
            // LOAN AMOUNT
            // ----------------------------------------------------

            Double loanAmount =
                    loan.getDisbursedAmount() != null
                            ? loan.getDisbursedAmount()
                            : loan.getAmount();


            // ----------------------------------------------------
            // CREDIT SCORE
            // ----------------------------------------------------

            Integer creditScore =
                    loan.getCreditScoreSnapshot() != null

                            ? loan.getCreditScoreSnapshot()

                            : borrower != null
                            ? borrower.getCreditScore()
                            : null;


            // ----------------------------------------------------
            // BUILD RECORD
            // ----------------------------------------------------

            records.add(

                    CreditBureauRecord.builder()

                            .borrowerId(
                                    borrower != null
                                            ? borrower.getId()
                                            : null
                            )

                            .fullName(
                                    fullName
                            )

                            .nationalId(
                                    borrower != null
                                            ? borrower.getNationalId()
                                            : null
                            )

                            .dateOfBirth(
                                    borrower != null
                                            ? borrower.getDateOfBirth()
                                            : null
                            )

                            .gender(
                                    borrower != null
                                            ? borrower.getGender()
                                            : null
                            )

                            .phone(
                                    borrower != null
                                            ? borrower.getPhone()
                                            : null
                            )

                            .loanNumber(
                                    loan.getReferenceNumber()
                            )

                            .loanType(
                                    loan.getLoanType() != null
                                            ? loan.getLoanType().name()
                                            : null
                            )

                            .loanStatus(
                                    loan.getStatus() != null
                                            ? loan.getStatus().name()
                                            : null
                            )

                            .loanAmount(
                                    loanAmount
                            )

                            .outstandingBalance(
                                    loan.getOutstandingBalance()
                            )

                            .daysPastDue(
                                    loan.getDaysOverdue()
                            )

                            .creditScore(
                                    creditScore
                            )

                            .dateOpened(
                                    loan.getDisbursedAt() != null
                                            ? loan.getDisbursedAt()
                                            : loan.getStartDate()
                            )

                            .lastPaymentDate(
                                    loan.getLastPaymentDate()
                            )

                            .maturityDate(
                                    loan.getMaturityDate()
                            )

                            .dateClosed(
                                    closed
                                            ? loan.getMaturityDate()
                                            : null
                            )

                            .branchName(
                                    loan.getBranch() != null
                                            ? loan.getBranch().getName()
                                            : null
                            )

                            .currency(
                                    loan.getCurrency()
                            )

                            .build()
            );
        }


        return records;
    }
}
