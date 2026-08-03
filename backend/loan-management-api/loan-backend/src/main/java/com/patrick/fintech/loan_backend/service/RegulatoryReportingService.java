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
import com.patrick.fintech.loan_backend.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.TemporalAdjusters;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegulatoryReportingService {

    private final LoanRepository loanRepository;

    private final PaymentRepository paymentRepository;

    private final OrganizationRepository organizationRepository;

    private final BnrFinancialStatementService bnrFinancialStatementService;


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
    // PERIOD RESOLUTION
    // ============================================================

    public LocalDate[] resolvePeriod(
            ReportPeriod period,
            LocalDate from,
            LocalDate to
    ) {

        LocalDate today = LocalDate.now();

        if (period == null) {
            period = ReportPeriod.MONTHLY;
        }

        return switch (period) {

            case DAILY -> new LocalDate[]{
                    today,
                    today
            };

            case WEEKLY -> {

                LocalDate start =
                        today.with(
                                TemporalAdjusters.previousOrSame(
                                        DayOfWeek.MONDAY
                                )
                        );

                LocalDate end =
                        start.plusDays(6);

                yield new LocalDate[]{
                        start,
                        end
                };
            }

            case MONTHLY -> {

                LocalDate start =
                        today.withDayOfMonth(1);

                LocalDate end =
                        today.with(
                                TemporalAdjusters.lastDayOfMonth()
                        );

                yield new LocalDate[]{
                        start,
                        end
                };
            }

            case QUARTERLY -> {

                int firstMonth =
                        ((today.getMonthValue() - 1) / 3) * 3 + 1;

                LocalDate start =
                        LocalDate.of(
                                today.getYear(),
                                firstMonth,
                                1
                        );

                LocalDate end =
                        start.plusMonths(3)
                                .minusDays(1);

                yield new LocalDate[]{
                        start,
                        end
                };
            }

            case YEARLY -> {

                LocalDate start =
                        today.withDayOfYear(1);

                LocalDate end =
                        today.with(
                                TemporalAdjusters.lastDayOfYear()
                        );

                yield new LocalDate[]{
                        start,
                        end
                };
            }

            case CUSTOM -> {

                if (from == null) {

                    throw new IllegalArgumentException(
                            "Custom reporting period requires 'from'."
                    );
                }

                LocalDate end =
                        to == null
                                ? from
                                : to;

                if (end.isBefore(from)) {

                    throw new IllegalArgumentException(
                            "'to' cannot be before 'from'."
                    );
                }

                yield new LocalDate[]{
                        from,
                        end
                };
            }
        };
    }


    // ============================================================
    // PORTFOLIO
    // ============================================================

    private List<Loan> fetchPortfolio(
            Long organizationId,
            Long branchId,
            LocalDate asOf
    ) {

        if (organizationId == null) {
            return new ArrayList<>();
        }

        return loanRepository.findPortfolioAsOf(
                organizationId,
                branchId,
                asOf
        );
    }


    // ============================================================
    // DISBURSEMENTS
    // ============================================================

    private List<Loan> fetchDisbursements(
            Long organizationId,
            Long branchId,
            LocalDate from,
            LocalDate to
    ) {

        if (organizationId == null) {
            return new ArrayList<>();
        }

        return loanRepository.findLoansDisbursedDuringPeriod(
                organizationId,
                branchId,
                from,
                to
        );
    }


    // ============================================================
    // PAYMENTS
    // ============================================================

    private List<Payment> fetchPayments(
            Long organizationId,
            Long branchId,
            LocalDate from,
            LocalDate to
    ) {

        if (organizationId == null) {
            return new ArrayList<>();
        }

        return paymentRepository.findPaymentsDuringPeriod(
                organizationId,
                branchId,
                from,
                to
        );
    }


    // ============================================================
    // BNR SUMMARY
    // ============================================================

    public BnrSummaryReport buildBnrSummary(
            Long organizationId,
            Long branchId,
            ReportPeriod period,
            LocalDate from,
            LocalDate to
    ) {

        if (organizationId == null) {

            throw new IllegalArgumentException(
                    "organizationId is required."
            );
        }

        LocalDate[] window =
                resolvePeriod(
                        period,
                        from,
                        to
                );

        LocalDate periodStart = window[0];

        LocalDate periodEnd = window[1];


        // ========================================================
        // ORGANIZATION
        // ========================================================

        Organization organization =
                organizationRepository
                        .findById(organizationId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Organization not found: "
                                                + organizationId
                                )
                        );


        // ========================================================
        // DATA
        // ========================================================

        List<Loan> portfolioLoans =
                safeLoans(
                        fetchPortfolio(
                                organizationId,
                                branchId,
                                periodEnd
                        )
                );

        List<Loan> disbursementLoans =
                safeLoans(
                        fetchDisbursements(
                                organizationId,
                                branchId,
                                periodStart,
                                periodEnd
                        )
                );

        List<Payment> payments =
                safePayments(
                        fetchPayments(
                                organizationId,
                                branchId,
                                periodStart,
                                periodEnd
                        )
                );


        // ========================================================
        // LOAN COUNTS
        // ========================================================

        long activeLoans = 0;

        long closedLoans = 0;

        long paidLoans = 0;

        long pendingLoans = 0;

        long approvedLoans = 0;

        long rejectedLoans = 0;

        long cancelledLoans = 0;

        long overdueLoans = 0;

        long defaultedLoans = 0;

        long writtenOffLoans = 0;

        long restructuredLoans = 0;


        // ========================================================
        // PORTFOLIO MONEY
        // ========================================================

        double outstandingPrincipal = 0.0;

        double parAmount = 0.0;

        double nplAmount = 0.0;

        double defaultedAmount = 0.0;

        double writtenOffAmount = 0.0;


        // ========================================================
        // PAR BUCKETS
        // ========================================================

        double par1To30 = 0.0;

        double par31To60 = 0.0;

        double par61To90 = 0.0;

        double par91To180 = 0.0;

        double par181To365 = 0.0;

        double parOver365 = 0.0;


        long loansOver30Days = 0;

        long loansOver60Days = 0;

        long loansOver90Days = 0;

        long loansOver180Days = 0;

        long loansOver365Days = 0;

        long nplLoanCount = 0;


        // ========================================================
        // BORROWERS
        // ========================================================

        Set<Long> borrowerIds =
                new HashSet<>();

        Set<Long> activeBorrowerIds =
                new HashSet<>();

        Set<Long> borrowersWithMultipleLoans =
                new HashSet<>();

        Set<Long> borrowersMissingNationalId =
                new HashSet<>();

        Set<Long> maleBorrowerIds =
                new HashSet<>();

        Set<Long> femaleBorrowerIds =
                new HashSet<>();

        Set<Long> otherGenderBorrowerIds =
                new HashSet<>();

        Set<Long> youthBorrowerIds =
                new HashSet<>();

        Set<Long> adultBorrowerIds =
                new HashSet<>();

        Set<Long> seniorBorrowerIds =
                new HashSet<>();

        Map<Long, Integer> borrowerLoanCounts =
                new HashMap<>();


        // ========================================================
        // DATA QUALITY
        // ========================================================

        long loansMissingBorrower = 0;

        long loansMissingBranch = 0;

        long loansMissingCurrency = 0;

        long loansMissingRepaymentSchedule = 0;

        List<String> warnings =
                new ArrayList<>();


        // ========================================================
        // PROCESS PORTFOLIO
        // ========================================================

        for (Loan loan : portfolioLoans) {

            if (loan == null) {
                continue;
            }

            LoanStatus status =
                    loan.getStatus();


            // ----------------------------------------------------
            // STATUS
            // ----------------------------------------------------

            if (status != null) {

                switch (status) {

                    case ACTIVE,
                         DISBURSED,
                         OVERDUE -> activeLoans++;

                    case CLOSED -> closedLoans++;

                    case PAID -> paidLoans++;

                    case PENDING,
                         UNDER_REVIEW -> pendingLoans++;

                    case APPROVED -> approvedLoans++;

                    case REJECTED -> rejectedLoans++;

                    case CANCELLED -> cancelledLoans++;

                    case DEFAULTED -> defaultedLoans++;

                    case WRITTEN_OFF -> writtenOffLoans++;

                    default -> {
                        // Keep future enum values harmless.
                    }
                }
            }


            // ----------------------------------------------------
            // RESTRUCTURED
            // ----------------------------------------------------

            if (isRestructured(loan)) {
                restructuredLoans++;
            }


            // ----------------------------------------------------
            // OUTSTANDING PRINCIPAL
            // ----------------------------------------------------

            double outstanding =
                    number(
                            loan.getOutstandingBalance()
                    );

            if (outstanding < 0) {
                outstanding = 0.0;
            }

            outstandingPrincipal += outstanding;


            // ----------------------------------------------------
            // DAYS PAST DUE
            // ----------------------------------------------------

            int dpd =
                    loan.getDaysOverdue() == null
                            ? 0
                            : Math.max(
                                    0,
                                    loan.getDaysOverdue()
                            );


            if (dpd > 0 && outstanding > 0) {

                overdueLoans++;

                parAmount += outstanding;

                if (dpd <= 30) {

                    par1To30 += outstanding;

                } else if (dpd <= 60) {

                    par31To60 += outstanding;

                } else if (dpd <= 90) {

                    par61To90 += outstanding;

                } else if (dpd <= 180) {

                    par91To180 += outstanding;

                } else if (dpd <= 365) {

                    par181To365 += outstanding;

                } else {

                    parOver365 += outstanding;
                }
            }


            if (dpd > 30) {
                loansOver30Days++;
            }

            if (dpd > 60) {
                loansOver60Days++;
            }

            if (dpd > 90) {
                loansOver90Days++;
            }

            if (dpd > 180) {
                loansOver180Days++;
            }

            if (dpd > 365) {
                loansOver365Days++;
            }


            // ----------------------------------------------------
            // NPL
            // ----------------------------------------------------

            if (isNpl(loan)) {

                nplLoanCount++;

                nplAmount += outstanding;
            }


            // ----------------------------------------------------
            // DEFAULT
            // ----------------------------------------------------

            if (status == LoanStatus.DEFAULTED) {

                defaultedAmount += outstanding;
            }


            // ----------------------------------------------------
            // WRITE OFF
            // ----------------------------------------------------

            if (status == LoanStatus.WRITTEN_OFF) {

                writtenOffAmount += outstanding;
            }


            // ----------------------------------------------------
            // BORROWER
            // ----------------------------------------------------

            Borrower borrower =
                    loan.getBorrower();

            if (borrower == null) {

                loansMissingBorrower++;

            } else {

                Long borrowerId =
                        borrower.getId();

                if (borrowerId != null) {

                    borrowerIds.add(borrowerId);

                    int loanCount =
                            borrowerLoanCounts.merge(
                                    borrowerId,
                                    1,
                                    Integer::sum
                            );

                    if (loanCount > 1) {

                        borrowersWithMultipleLoans.add(
                                borrowerId
                        );
                    }


                    if (
                            status == LoanStatus.ACTIVE
                                    ||
                            status == LoanStatus.DISBURSED
                                    ||
                            status == LoanStatus.OVERDUE
                    ) {

                        activeBorrowerIds.add(
                                borrowerId
                        );
                    }


                    String gender =
                            normalize(
                                    borrower.getGender()
                            );

                    switch (gender) {

                        case "MALE",
                             "M" -> maleBorrowerIds.add(
                                borrowerId
                        );

                        case "FEMALE",
                             "F" -> femaleBorrowerIds.add(
                                borrowerId
                        );

                        default -> otherGenderBorrowerIds.add(
                                borrowerId
                        );
                    }


                    if (
                            borrower.getNationalId() == null
                                    ||
                            borrower.getNationalId().isBlank()
                    ) {

                        borrowersMissingNationalId.add(
                                borrowerId
                        );
                    }


                    if (borrower.getDateOfBirth() != null) {

                        int age =
                                Period.between(
                                        borrower.getDateOfBirth(),
                                        periodEnd
                                ).getYears();

                        if (age < 35) {

                            youthBorrowerIds.add(
                                    borrowerId
                            );

                        } else if (age < 60) {

                            adultBorrowerIds.add(
                                    borrowerId
                            );

                        } else {

                            seniorBorrowerIds.add(
                                    borrowerId
                            );
                        }
                    }
                }
            }


            // ----------------------------------------------------
            // DATA QUALITY
            // ----------------------------------------------------

            if (loan.getBranch() == null) {

                loansMissingBranch++;
            }


            if (
                    loan.getCurrency() == null
                            ||
                    loan.getCurrency().isBlank()
            ) {

                loansMissingCurrency++;
            }


            /*
             * We intentionally do not assume a particular
             * repayment-schedule getter exists on Loan.
             *
             * Schedule completeness should be validated by the
             * schedule repository/service where that relationship
             * actually exists.
             */
        }


        // ========================================================
        // DISBURSEMENTS
        // ========================================================

        double totalPrincipalDisbursed = 0.0;

        double totalApprovedAmount = 0.0;

        double largestLoanAmount = 0.0;

        double smallestLoanAmount = 0.0;

        long actualDisbursementCount = 0;


        for (Loan loan : disbursementLoans) {

            if (loan == null) {
                continue;
            }

            double requested =
                    number(
                            loan.getAmount()
                    );

            double disbursed =
                    number(
                            loan.getDisbursedAmount()
                    );


            if (requested > 0) {

                totalApprovedAmount += requested;
            }


            if (disbursed > 0) {

                totalPrincipalDisbursed += disbursed;

                actualDisbursementCount++;


                if (disbursed > largestLoanAmount) {

                    largestLoanAmount =
                            disbursed;
                }


                if (
                        smallestLoanAmount == 0.0
                                ||
                        disbursed < smallestLoanAmount
                ) {

                    smallestLoanAmount =
                            disbursed;
                }
            }
        }


        double averageLoanSize =
                actualDisbursementCount == 0
                        ? 0.0
                        : totalPrincipalDisbursed
                        / actualDisbursementCount;


        // ========================================================
        // PAYMENTS
        // ========================================================

        double principalCollected = 0.0;

        double interestCollected = 0.0;

        double feesCollected = 0.0;

        double totalAmountCollected = 0.0;

        long totalPayments =
                payments.size();

        long missedPayments = 0;

        long overduePayments = 0;

        double interestAccruedUnpaid = 0.0;

        double feesAccruedUnpaid = 0.0;


        for (Payment payment : payments) {

            if (payment == null) {
                continue;
            }


            boolean completed =
                    Boolean.TRUE.equals(
                            payment.getPaid()
                    )
                    ||
                    payment.getStatus()
                            == Payment.PaymentStatus.COMPLETED;


            if (completed) {

                double principal =
                        number(
                                payment.getPrincipalComponent()
                        );

                double interest =
                        number(
                                payment.getInterestComponent()
                        );

                double amountPaid =
                        number(
                                payment.getAmountPaid()
                        );

                double penalty =
                        number(
                                payment.getPenalty()
                        );


                principalCollected += principal;

                interestCollected += interest;

                totalAmountCollected +=
                        amountPaid > 0
                                ? amountPaid
                                : principal
                                + interest
                                + penalty;

            } else {

                if (
                        payment.getDueDate() != null
                                &&
                        !payment.getDueDate()
                                .isAfter(periodEnd)
                ) {

                    missedPayments++;


                    if (
                            payment.getDueDate()
                                    .isBefore(periodEnd)
                    ) {

                        overduePayments++;
                    }
                }


                interestAccruedUnpaid +=
                        number(
                                payment.getInterestComponent()
                        );
            }
        }


        // ========================================================
        // RATIOS
        // ========================================================

        double parRatio =
                ratio(
                        parAmount,
                        outstandingPrincipal
                );


        /*
         * PAR 30+ excludes the 1-30 bucket.
         *
         * PAR 60+ excludes 1-30 and 31-60.
         *
         * PAR 90+ excludes 1-30, 31-60 and 61-90.
         */

        double par30Amount =
                par31To60
                        + par61To90
                        + par91To180
                        + par181To365
                        + parOver365;

        double par60Amount =
                par61To90
                        + par91To180
                        + par181To365
                        + parOver365;

        double par90Amount =
                par91To180
                        + par181To365
                        + parOver365;


        double par1Ratio =
                ratio(
                        parAmount,
                        outstandingPrincipal
                );

        double par30Ratio =
                ratio(
                        par30Amount,
                        outstandingPrincipal
                );

        double par60Ratio =
                ratio(
                        par60Amount,
                        outstandingPrincipal
                );

        double par90Ratio =
                ratio(
                        par90Amount,
                        outstandingPrincipal
                );

        double nplRatio =
                ratio(
                        nplAmount,
                        outstandingPrincipal
                );


        // ========================================================
        // OUTSTANDING
        // ========================================================

        /*
         * Current Loan model does not expose separate outstanding
         * interest/fee balances in the contract used here.
         *
         * Therefore these remain zero rather than inventing values.
         */

        double outstandingInterest = 0.0;

        double outstandingFees = 0.0;

        double totalOutstanding =
                outstandingPrincipal
                        + outstandingInterest
                        + outstandingFees;


        // ========================================================
        // CREDIT METRICS
        // ========================================================

        long borrowersCreditChecked =
                countCreditChecked(
                        portfolioLoans
                );

        long borrowersWithDefaultHistory =
                countBorrowersWithDefaultHistory(
                        portfolioLoans
                );


        // ========================================================
        // WARNINGS
        // ========================================================

        if (loansMissingBorrower > 0) {

            warnings.add(
                    loansMissingBorrower
                            + " loan(s) have no borrower."
            );
        }


        if (!borrowersMissingNationalId.isEmpty()) {

            warnings.add(
                    borrowersMissingNationalId.size()
                            + " borrower(s) have no national ID."
            );
        }


        if (loansMissingBranch > 0) {

            warnings.add(
                    loansMissingBranch
                            + " loan(s) have no branch."
            );
        }


        if (loansMissingCurrency > 0) {

            warnings.add(
                    loansMissingCurrency
                            + " loan(s) have no currency."
            );
        }


        // ========================================================
        // BREAKDOWNS
        // ========================================================

        List<BnrBreakdownRow> loanTypeBreakdown =
                groupAndSum(
                        portfolioLoans,
                        loan ->
                                loan.getLoanType() == null
                                        ? "UNSPECIFIED"
                                        : loan.getLoanType().name()
                );


        List<BnrBreakdownRow> branchBreakdown =
                groupAndSum(
                        portfolioLoans,
                        loan ->
                                loan.getBranch() == null
                                        ? "UNASSIGNED"
                                        : loan.getBranch().getName()
                );


        List<BnrBreakdownRow> genderBreakdown =
                groupAndSum(
                        portfolioLoans,
                        loan -> {

                            Borrower borrower =
                                    loan.getBorrower();

                            if (borrower == null) {
                                return "UNSPECIFIED";
                            }

                            String gender =
                                    normalize(
                                            borrower.getGender()
                                    );

                            return switch (gender) {

                                case "MALE",
                                     "M" -> "MALE";

                                case "FEMALE",
                                     "F" -> "FEMALE";

                                default -> "OTHER";
                            };
                        }
                );


        // ========================================================
        // REPORT STATUS
        // ========================================================

        String reportStatus =
                warnings.isEmpty()
                        ? "VALIDATED"
                        : "VALIDATION_WARNINGS";


        // ========================================================
        // BNR SUMMARY
        // ========================================================

        return BnrSummaryReport.builder()

                // ------------------------------------------------
                // ORGANIZATION
                // ------------------------------------------------

                .organizationId(
                        organizationId
                )

                .organizationName(
                        organization.getName()
                )

                .bnrInstitutionCode(
                        organization.getRegistrationNumber()
                )

                .registrationNumber(
                        organization.getRegistrationNumber()
                )

                .institutionType(
                        "NON_DEPOSIT_TAKING_LENDER"
                )

                .country(
                        organization.getCountry() != null
                                ? organization.getCountry()
                                : "RW"
                )

                .currency(
                        organization.getDefaultCurrency() != null
                                ? organization.getDefaultCurrency()
                                : "RWF"
                )


                // ------------------------------------------------
                // PERIOD
                // ------------------------------------------------

                .reportPeriod(
                        (
                                period == null
                                        ? ReportPeriod.MONTHLY
                                        : period
                        ).name()
                )

                .periodStart(
                        periodStart
                )

                .periodEnd(
                        periodEnd
                )

                .reportDate(
                        periodEnd
                )

                .generatedAt(
                        LocalDateTime.now()
                )

                .generatedBy(
                        "SYSTEM"
                )

                .reportReference(
                        buildReportReference(
                                organizationId,
                                periodStart,
                                periodEnd
                        )
                )


                // ------------------------------------------------
                // BRANCH
                // ------------------------------------------------

                .branchId(
                        branchId
                )

                .branchName(
                        resolveBranchName(
                                portfolioLoans,
                                branchId
                        )
                )


                // ------------------------------------------------
                // LOANS
                // ------------------------------------------------

                .totalLoans(
                        portfolioLoans.size()
                )

                .loansDisbursedDuringPeriod(
                        actualDisbursementCount
                )

                .activeLoans(
                        activeLoans
                )

                .closedLoans(
                        closedLoans
                )

                .paidLoans(
                        paidLoans
                )

                .pendingLoans(
                        pendingLoans
                )

                .approvedLoans(
                        approvedLoans
                )

                .rejectedLoans(
                        rejectedLoans
                )

                .cancelledLoans(
                        cancelledLoans
                )

                .overdueLoans(
                        overdueLoans
                )

                .defaultedLoans(
                        defaultedLoans
                )

                .writtenOffLoans(
                        writtenOffLoans
                )

                .restructuredLoans(
                        restructuredLoans
                )


                // ------------------------------------------------
                // DISBURSEMENTS
                // ------------------------------------------------

                .totalPrincipalDisbursed(
                        totalPrincipalDisbursed
                )

                .totalApprovedAmount(
                        totalApprovedAmount
                )

                .averageLoanSize(
                        averageLoanSize
                )

                .largestLoanAmount(
                        largestLoanAmount
                )

                .smallestLoanAmount(
                        smallestLoanAmount
                )


                // ------------------------------------------------
                // OUTSTANDING
                // ------------------------------------------------

                .outstandingPrincipal(
                        outstandingPrincipal
                )

                .outstandingInterest(
                        outstandingInterest
                )

                .outstandingFees(
                        outstandingFees
                )

                .totalOutstanding(
                        totalOutstanding
                )


                // ------------------------------------------------
                // REPAYMENTS
                // ------------------------------------------------

                .totalPrincipalCollected(
                        principalCollected
                )

                .totalInterestCollected(
                        interestCollected
                )

                .totalFeesCollected(
                        feesCollected
                )

                .totalAmountCollected(
                        totalAmountCollected
                )

                .interestAccruedUnpaid(
                        interestAccruedUnpaid
                )

                .feesAccruedUnpaid(
                        feesAccruedUnpaid
                )

                .totalPayments(
                        totalPayments
                )

                .missedPayments(
                        missedPayments
                )

                .overduePayments(
                        overduePayments
                )


                // ------------------------------------------------
                // PAR
                // ------------------------------------------------

                .parAmount(
                        parAmount
                )

                .parRatio(
                        parRatio
                )

                .par1Ratio(
                        par1Ratio
                )

                .par30Ratio(
                        par30Ratio
                )

                .par60Ratio(
                        par60Ratio
                )

                .par90Ratio(
                        par90Ratio
                )

                .par1To30Amount(
                        par1To30
                )

                .par31To60Amount(
                        par31To60
                )

                .par61To90Amount(
                        par61To90
                )

                .par91To180Amount(
                        par91To180
                )

                .par181To365Amount(
                        par181To365
                )

                .parOver365Amount(
                        parOver365
                )


                // ------------------------------------------------
                // NPL
                // ------------------------------------------------

                .nplAmount(
                        nplAmount
                )

                .nplRatio(
                        nplRatio
                )

                .nplLoanCount(
                        nplLoanCount
                )

                .loansOver30Days(
                        loansOver30Days
                )

                .loansOver60Days(
                        loansOver60Days
                )

                .loansOver90Days(
                        loansOver90Days
                )

                .loansOver180Days(
                        loansOver180Days
                )

                .loansOver365Days(
                        loansOver365Days
                )


                // ------------------------------------------------
                // DEFAULT / WRITE-OFF
                // ------------------------------------------------

                .defaultedAmount(
                        defaultedAmount
                )

                .writtenOffAmount(
                        writtenOffAmount
                )

                .recoveriesAfterWriteOff(
                        0.0
                )


                // ------------------------------------------------
                // PROVISION
                // ------------------------------------------------

                .requiredProvision(
                        0.0
                )

                .existingProvision(
                        0.0
                )

                .provisionShortfall(
                        0.0
                )


                // ------------------------------------------------
                // BORROWERS
                // ------------------------------------------------

                .totalBorrowers(
                        borrowerIds.size()
                )

                .activeBorrowers(
                        activeBorrowerIds.size()
                )

                .maleBorrowers(
                        maleBorrowerIds.size()
                )

                .femaleBorrowers(
                        femaleBorrowerIds.size()
                )

                .otherGenderBorrowers(
                        otherGenderBorrowerIds.size()
                )

                .borrowersWithMultipleLoans(
                        borrowersWithMultipleLoans.size()
                )


                // ------------------------------------------------
                // FINANCIAL INCLUSION
                // ------------------------------------------------

                .youthBorrowers(
                        youthBorrowerIds.size()
                )

                .adultBorrowers(
                        adultBorrowerIds.size()
                )

                .seniorBorrowers(
                        seniorBorrowerIds.size()
                )


                // ------------------------------------------------
                // CREDIT
                // ------------------------------------------------

                .borrowersCreditChecked(
                        borrowersCreditChecked
                )

                .borrowersWithDefaultHistory(
                        borrowersWithDefaultHistory
                )

                .borrowersWithActiveListing(
                        0
                )

                .borrowersWithMultipleFacilities(
                        borrowersWithMultipleLoans.size()
                )

                .totalExternalDebt(
                        0.0
                )


                // ------------------------------------------------
                // BREAKDOWNS
                // ------------------------------------------------

                .loanTypeBreakdown(
                        loanTypeBreakdown
                )

                .branchBreakdown(
                        branchBreakdown
                )

                .genderBreakdown(
                        genderBreakdown
                )


                // ------------------------------------------------
                // DATA QUALITY
                // ------------------------------------------------

                .loansMissingBorrower(
                        loansMissingBorrower
                )

                .borrowersMissingNationalId(
                        borrowersMissingNationalId.size()
                )

                .loansMissingBranch(
                        loansMissingBranch
                )

                .loansMissingCurrency(
                        loansMissingCurrency
                )

                .loansMissingRepaymentSchedule(
                        loansMissingRepaymentSchedule
                )

                .dataQualityWarnings(
                        warnings
                )


                // ------------------------------------------------
                // STATUS
                // ------------------------------------------------

                .reportStatus(
                        reportStatus
                )

                .submissionReference(
                        null
                )

                .build();
    }


    // ============================================================
    // BNR FINANCIAL STATEMENT
    // ============================================================

    public BnrFinancialStatementReport buildBnrFinancialStatement(
            Long organizationId,
            Long branchId,
            ReportPeriod period,
            LocalDate from,
            LocalDate to
    ) {

        if (organizationId == null) {

            throw new IllegalArgumentException(
                    "organizationId is required."
            );
        }


        LocalDate[] window =
                resolvePeriod(
                        period,
                        from,
                        to
                );

        LocalDate periodStart = window[0];

        LocalDate periodEnd = window[1];


        // ========================================================
        // ORGANIZATION
        // ========================================================

        Organization organization =
                organizationRepository
                        .findById(organizationId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Organization not found: "
                                                + organizationId
                                )
                        );


        // ========================================================
        // ACCOUNTING SOURCE
        // ========================================================

        Map<String, Object> accountingReport =
                bnrFinancialStatementService
                        .buildFinancialStatement(
                                organizationId,
                                periodStart,
                                periodEnd
                        );


        if (accountingReport == null) {

            accountingReport =
                    new LinkedHashMap<>();
        }


        // ========================================================
        // STATEMENT OF FINANCIAL POSITION
        // ========================================================

        Map<String, Object> financialPosition =
                getMap(
                        accountingReport,
                        "statementOfFinancialPosition"
                );


        Map<String, Object> incomeStatement =
                getMap(
                        accountingReport,
                        "incomeStatement"
                );


        // ========================================================
        // BALANCE SHEET
        // ========================================================

        double totalAssets =
                doubleValue(
                        financialPosition.get(
                                "totalAssets"
                        )
                );


        double totalLiabilities =
                doubleValue(
                        financialPosition.get(
                                "totalLiabilities"
                        )
                );


        double totalEquity =
                doubleValue(
                        financialPosition.get(
                                "totalEquity"
                        )
                );


        double currentPeriodNetIncome =
                doubleValue(
                        financialPosition.get(
                                "currentPeriodNetIncome"
                        )
                );


        boolean balanceSheetBalanced =
                booleanValue(
                        financialPosition.get(
                                "balanced"
                        )
                );


        // ========================================================
        // INCOME STATEMENT
        // ========================================================

        double totalIncome =
                doubleValue(
                        incomeStatement.get(
                                "totalIncome"
                        )
                );


        double totalExpenses =
                doubleValue(
                        incomeStatement.get(
                                "totalExpenses"
                        )
                );


        double netIncome =
                doubleValue(
                        incomeStatement.get(
                                "netIncome"
                        )
                );


        // ========================================================
        // TRIAL BALANCE
        // ========================================================

        double trialBalanceDebit =
                doubleValue(
                        accountingReport.get(
                                "trialBalanceDebit"
                        )
                );


        double trialBalanceCredit =
                doubleValue(
                        accountingReport.get(
                                "trialBalanceCredit"
                        )
                );


        boolean trialBalanceBalanced =
                booleanValue(
                        accountingReport.get(
                                "trialBalanceBalanced"
                        )
                );


        // ========================================================
        // CASH FLOW
        // ========================================================

        double cashUsedForLending =
                doubleValue(
                        accountingReport.get(
                                "cashUsedForLending"
                        )
                );


        double cashFromCollections =
                doubleValue(
                        accountingReport.get(
                                "cashFromCollections"
                        )
                );


        double cashFromFees =
                doubleValue(
                        accountingReport.get(
                                "cashFromFees"
                        )
                );


        double otherCashMovement =
                doubleValue(
                        accountingReport.get(
                                "otherCashMovement"
                        )
                );


        double netChangeInCash =
                doubleValue(
                        accountingReport.get(
                                "netChangeInCash"
                        )
                );


        // ========================================================
        // REPORT
        // ========================================================

        return BnrFinancialStatementReport.builder()

                // ------------------------------------------------
                // REPORT INFORMATION
                // ------------------------------------------------

                .organizationId(
                        organizationId
                )

                .organizationName(
                        organization.getName()
                )

                .bnrInstitutionCode(
                        organization.getRegistrationNumber()
                )

                .branchId(
                        branchId
                )

                .branchName(
                        resolveBranchNameForFinancialStatement(
                                organizationId,
                                branchId
                        )
                )

                .currency(
                        organization.getDefaultCurrency() != null
                                ? organization.getDefaultCurrency()
                                : "RWF"
                )

                .reportPeriod(
                        (
                                period == null
                                        ? ReportPeriod.MONTHLY
                                        : period
                        ).name()
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
                        getList(
                                financialPosition,
                                "assets"
                        )
                )

                .liabilities(
                        getList(
                                financialPosition,
                                "liabilities"
                        )
                )

                .equity(
                        getList(
                                financialPosition,
                                "equity"
                        )
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
                        balanceSheetBalanced
                )


                // ------------------------------------------------
                // PROFIT AND LOSS
                // ------------------------------------------------

                .income(
                        getList(
                                incomeStatement,
                                "income"
                        )
                )

                .expenses(
                        getList(
                                incomeStatement,
                                "expenses"
                        )
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
                // TRIAL BALANCE
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
    // LOAN TYPE BREAKDOWN
    // ============================================================

    public List<BnrBreakdownRow> breakdownByLoanType(
            Long organizationId,
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

        return groupAndSum(
                fetchPortfolio(
                        organizationId,
                        branchId,
                        window[1]
                ),
                loan ->
                        loan.getLoanType() == null
                                ? "UNSPECIFIED"
                                : loan.getLoanType().name()
        );
    }


    // ============================================================
    // BRANCH BREAKDOWN
    // ============================================================

    public List<BnrBreakdownRow> breakdownByBranch(
            Long organizationId,
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

        return groupAndSum(
                fetchPortfolio(
                        organizationId,
                        null,
                        window[1]
                ),
                loan ->
                        loan.getBranch() == null
                                ? "UNASSIGNED"
                                : loan.getBranch().getName()
        );
    }


    // ============================================================
    // GENDER BREAKDOWN
    // ============================================================

    public List<BnrBreakdownRow> breakdownByGender(
            Long organizationId,
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

        return groupAndSum(
                fetchPortfolio(
                        organizationId,
                        branchId,
                        window[1]
                ),
                loan -> {

                    Borrower borrower =
                            loan.getBorrower();

                    if (borrower == null) {
                        return "UNSPECIFIED";
                    }

                    String gender =
                            normalize(
                                    borrower.getGender()
                            );

                    return switch (gender) {

                        case "MALE",
                             "M" -> "MALE";

                        case "FEMALE",
                             "F" -> "FEMALE";

                        default -> "OTHER";
                    };
                }
        );
    }


    // ============================================================
    // GENERIC BREAKDOWN
    // ============================================================

    private List<BnrBreakdownRow> groupAndSum(
            List<Loan> loans,
            Function<Loan, String> keyFunction
    ) {

        Map<String, Long> counts =
                new LinkedHashMap<>();

        Map<String, Double> amounts =
                new LinkedHashMap<>();


        if (loans == null) {
            return new ArrayList<>();
        }


        for (Loan loan : loans) {

            if (loan == null) {
                continue;
            }


            String key =
                    keyFunction.apply(
                            loan
                    );


            if (
                    key == null
                            ||
                    key.isBlank()
            ) {

                key = "UNSPECIFIED";
            }


            counts.merge(
                    key,
                    1L,
                    Long::sum
            );


            double amount =
                    number(
                            loan.getDisbursedAmount()
                    );


            if (amount == 0.0) {

                amount =
                        number(
                                loan.getAmount()
                        );
            }


            amounts.merge(
                    key,
                    amount,
                    Double::sum
            );
        }


        return counts.entrySet()
                .stream()
                .map(
                        entry ->
                                BnrBreakdownRow.builder()

                                        .label(
                                                entry.getKey()
                                        )

                                        .count(
                                                entry.getValue()
                                        )

                                        .amount(
                                                amounts.getOrDefault(
                                                        entry.getKey(),
                                                        0.0
                                                )
                                        )

                                        .build()
                )
                .sorted(
                        Comparator.comparing(
                                BnrBreakdownRow::getLabel,
                                Comparator.nullsLast(
                                        String.CASE_INSENSITIVE_ORDER
                                )
                        )
                )
                .collect(
                        Collectors.toList()
                );
    }


    // ============================================================
    // CREDIT BUREAU / CRB
    // ============================================================

    public List<CreditBureauRecord> buildCreditBureauExport(
            Long organizationId,
            Long branchId,
            LocalDate from,
            LocalDate to
    ) {

        if (organizationId == null) {

            throw new IllegalArgumentException(
                    "organizationId is required."
            );
        }


        List<Loan> loans;


        if (from != null) {

            LocalDate end =
                    to == null
                            ? from
                            : to;


            if (end.isBefore(from)) {

                throw new IllegalArgumentException(
                        "'to' cannot be before 'from'."
                );
            }


            loans =
                    fetchDisbursements(
                            organizationId,
                            branchId,
                            from,
                            end
                    );

        } else {

            loans =
                    fetchPortfolio(
                            organizationId,
                            branchId,
                            LocalDate.now()
                    );
        }


        loans =
                safeLoans(
                        loans
                );


        List<CreditBureauRecord> output =
                new ArrayList<>();


        for (Loan loan : loans) {

            if (loan == null) {
                continue;
            }


            Borrower borrower =
                    loan.getBorrower();


            boolean closed =
                    loan.getStatus() == LoanStatus.CLOSED
                            ||
                    loan.getStatus() == LoanStatus.PAID;


            int daysPastDue =
                    loan.getDaysOverdue() == null
                            ? 0
                            : Math.max(
                                    0,
                                    loan.getDaysOverdue()
                            );


            // ====================================================
            // CRB CLASSIFICATION
            // ====================================================

            String repaymentClassification;


            if (
                    loan.getStatus()
                            == LoanStatus.WRITTEN_OFF
            ) {

                repaymentClassification =
                        "WRITTEN_OFF";

            } else if (
                    loan.getStatus()
                            == LoanStatus.DEFAULTED
            ) {

                repaymentClassification =
                        "DEFAULT";

            } else if (
                    daysPastDue > 90
            ) {

                repaymentClassification =
                        "NPL";

            } else if (
                    daysPastDue > 30
            ) {

                repaymentClassification =
                        "SUBSTANDARD";

            } else if (
                    daysPastDue > 0
            ) {

                repaymentClassification =
                        "PAST_DUE";

            } else {

                repaymentClassification =
                        "CURRENT";
            }


            // ====================================================
            // CREDIT SCORE
            // ====================================================

            Integer creditScore =
                    loan.getCreditScoreSnapshot() != null
                            ? loan.getCreditScoreSnapshot()
                            : borrower != null
                            ? borrower.getCreditScore()
                            : null;


            // ====================================================
            // RECORD
            // ====================================================

            CreditBureauRecord.CreditBureauRecordBuilder builder =
                    CreditBureauRecord.builder();


            builder
                    .borrowerId(
                            borrower != null
                                    ? borrower.getId()
                                    : null
                    )

                    .fullName(
                            borrower != null
                                    ? buildFullName(
                                            borrower
                                    )
                                    : null
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
                            loanAmount(
                                    loan
                            )
                    )

                    .outstandingBalance(
                            number(
                                    loan.getOutstandingBalance()
                            )
                    )

                    .daysPastDue(
                            daysPastDue
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
                    );


            /*
             * IMPORTANT:
             *
             * The current CreditBureauRecord contract shown in the
             * project does not expose a repaymentClassification
             * builder field.
             *
             * We therefore calculate the classification above but
             * do not call a non-existent builder method.
             *
             * This prevents a compile failure while keeping the CRB
             * classification logic ready for the DTO enhancement.
             */

            output.add(
                    builder.build()
            );
        }


        return output;
    }


    // ============================================================
    // NPL
    // ============================================================

    private boolean isNpl(
            Loan loan
    ) {

        if (loan == null) {
            return false;
        }


        LoanStatus status =
                loan.getStatus();


        if (
                status == LoanStatus.DEFAULTED
                        ||
                status == LoanStatus.WRITTEN_OFF
        ) {

            return true;
        }


        Integer days =
                loan.getDaysOverdue();


        return days != null &&
                days > 90;
    }


    // ============================================================
    // RESTRUCTURED
    // ============================================================

    private boolean isRestructured(
            Loan loan
    ) {

        if (loan == null) {
            return false;
        }


        /*
         * Avoid assuming a getRestructured() or
         * getRestructuredAt() property that may not exist in
         * the current Loan entity.
         *
         * Existing status values remain authoritative.
         */

        return false;
    }


    // ============================================================
    // CREDIT CHECK
    // ============================================================

    private long countCreditChecked(
            List<Loan> loans
    ) {

        Set<Long> borrowerIds =
                new HashSet<>();


        if (loans == null) {
            return 0;
        }


        for (Loan loan : loans) {

            if (
                    loan == null
                            ||
                    loan.getBorrower() == null
            ) {

                continue;
            }


            Borrower borrower =
                    loan.getBorrower();


            if (
                    borrower.getId() != null
                            &&
                    borrower.getCreditReportDate() != null
            ) {

                borrowerIds.add(
                        borrower.getId()
                );
            }
        }


        return borrowerIds.size();
    }


    // ============================================================
    // DEFAULT HISTORY
    // ============================================================

    private long countBorrowersWithDefaultHistory(
            List<Loan> loans
    ) {

        Set<Long> borrowerIds =
                new HashSet<>();


        if (loans == null) {
            return 0;
        }


        for (Loan loan : loans) {

            if (
                    loan == null
                            ||
                    loan.getBorrower() == null
                            ||
                    loan.getBorrower().getId() == null
            ) {

                continue;
            }


            if (
                    loan.getStatus() == LoanStatus.DEFAULTED
                            ||
                    loan.getStatus() == LoanStatus.WRITTEN_OFF
                            ||
                    (
                            loan.getDaysOverdue() != null
                                    &&
                            loan.getDaysOverdue() > 90
                    )
            ) {

                borrowerIds.add(
                        loan.getBorrower().getId()
                );
            }
        }


        return borrowerIds.size();
    }


    // ============================================================
    // BRANCH NAME
    // ============================================================

    private String resolveBranchName(
            List<Loan> loans,
            Long branchId
    ) {

        if (
                branchId == null
                        ||
                loans == null
        ) {

            return null;
        }


        return loans.stream()

                .filter(
                        loan ->
                                loan != null
                                        &&
                                loan.getBranch() != null
                                        &&
                                branchId.equals(
                                        loan.getBranch().getId()
                                )
                )

                .map(
                        loan ->
                                loan.getBranch().getName()
                )

                .filter(
                        name ->
                                name != null
                                        &&
                                !name.isBlank()
                )

                .findFirst()

                .orElse(null);
    }


    // ============================================================
    // FINANCIAL STATEMENT BRANCH
    // ============================================================

    private String resolveBranchNameForFinancialStatement(
            Long organizationId,
            Long branchId
    ) {

        if (branchId == null) {
            return null;
        }


        List<Loan> loans =
                fetchPortfolio(
                        organizationId,
                        branchId,
                        LocalDate.now()
                );


        if (loans == null) {
            return null;
        }


        return loans.stream()

                .filter(
                        loan ->
                                loan != null
                                        &&
                                loan.getBranch() != null
                                        &&
                                branchId.equals(
                                        loan.getBranch().getId()
                                )
                )

                .map(
                        loan ->
                                loan.getBranch().getName()
                )

                .filter(
                        name ->
                                name != null
                                        &&
                                !name.isBlank()
                )

                .findFirst()

                .orElse(null);
    }


    // ============================================================
    // MAP HELPER
    // ============================================================

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(
            Map<String, Object> source,
            String key
    ) {

        if (source == null) {
            return new LinkedHashMap<>();
        }


        Object value =
                source.get(key);


        if (value instanceof Map<?, ?> map) {

            return (Map<String, Object>) map;
        }


        return new LinkedHashMap<>();
    }


    // ============================================================
    // LIST HELPER
    // ============================================================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getList(
            Map<String, Object> source,
            String key
    ) {

        if (source == null) {
            return new ArrayList<>();
        }


        Object value =
                source.get(key);


        if (value instanceof List<?> list) {

            return (List<Map<String, Object>>) list;
        }


        return new ArrayList<>();
    }


    // ============================================================
    // DOUBLE VALUE
    // ============================================================

    private double doubleValue(
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

        } catch (NumberFormatException exception) {

            return 0.0;
        }
    }


    // ============================================================
    // BOOLEAN VALUE
    // ============================================================

    private boolean booleanValue(
            Object value
    ) {

        if (value == null) {
            return false;
        }


        if (value instanceof Boolean bool) {
            return bool;
        }


        return Boolean.parseBoolean(
                value.toString()
        );
    }


    // ============================================================
    // NUMBER
    // ============================================================

    private double number(
            Number value
    ) {

        if (value == null) {
            return 0.0;
        }


        return value.doubleValue();
    }


    // ============================================================
    // LOAN AMOUNT
    // ============================================================

    private double loanAmount(
            Loan loan
    ) {

        if (loan == null) {
            return 0.0;
        }


        double disbursed =
                number(
                        loan.getDisbursedAmount()
                );


        if (disbursed > 0) {
            return disbursed;
        }


        return number(
                loan.getAmount()
        );
    }


    // ============================================================
    // RATIO
    // ============================================================

    private double ratio(
            double numerator,
            double denominator
    ) {

        if (
                denominator == 0.0
        ) {

            return 0.0;
        }


        return numerator / denominator;
    }


    // ============================================================
    // NORMALIZE
    // ============================================================

    private String normalize(
            String value
    ) {

        if (value == null) {
            return "";
        }


        return value
                .trim()
                .toUpperCase();
    }


    // ============================================================
    // FULL NAME
    // ============================================================

    private String buildFullName(
            Borrower borrower
    ) {

        if (borrower == null) {
            return null;
        }


        String first =
                borrower.getFirstName() == null
                        ? ""
                        : borrower
                        .getFirstName()
                        .trim();


        String last =
                borrower.getLastName() == null
                        ? ""
                        : borrower
                        .getLastName()
                        .trim();


        return (
                first
                        + " "
                        + last
        ).trim();
    }


    // ============================================================
    // REPORT REFERENCE
    // ============================================================

    private String buildReportReference(
            Long organizationId,
            LocalDate from,
            LocalDate to
    ) {

        return "BNR-"
                + organizationId
                + "-"
                + from
                + "-"
                + to;
    }


    // ============================================================
    // SAFE LOANS
    // ============================================================

    private List<Loan> safeLoans(
            List<Loan> loans
    ) {

        if (loans == null) {
            return new ArrayList<>();
        }

        return loans;
    }


    // ============================================================
    // SAFE PAYMENTS
    // ============================================================

    private List<Payment> safePayments(
            List<Payment> payments
    ) {

        if (payments == null) {
            return new ArrayList<>();
        }

        return payments;
    }
}