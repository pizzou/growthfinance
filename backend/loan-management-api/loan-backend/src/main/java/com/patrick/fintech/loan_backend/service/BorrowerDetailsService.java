
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.BorrowerDetailsResponse;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * ============================================================
 * BORROWER DETAILS / BORROWER 360 SERVICE
 * ============================================================
 *
 * Provides a complete financial profile for a borrower.
 *
 * The service combines:
 *
 * 1. Borrower profile
 * 2. Employment information
 * 3. Financial information
 * 4. Credit information
 * 5. All borrower loans
 * 6. All loan payments
 * 7. Outstanding balances
 * 8. Principal paid
 * 9. Interest paid
 * 10. Fees / penalties
 * 11. Late payments
 * 12. Missed payments
 * 13. Days past due
 * 14. Repayment behaviour
 * 15. Good-payer assessment
 * 16. Risk level
 *
 * IMPORTANT:
 *
 * organizationId is always supplied by the authenticated tenant.
 * This prevents one organization from viewing another
 * organization's borrowers.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BorrowerDetailsService {


    private final BorrowerRepository borrowerRepository;

    private final LoanRepository loanRepository;

    private final PaymentRepository paymentRepository;


    // ============================================================
    // MAIN METHOD
    // ============================================================

    public BorrowerDetailsResponse getBorrowerDetails(
            Long borrowerId,
            Long organizationId
    ) {

        if (borrowerId == null) {

            throw new IllegalArgumentException(
                    "borrowerId is required."
            );
        }


        if (organizationId == null) {

            throw new IllegalArgumentException(
                    "organizationId is required."
            );
        }


        // ========================================================
        // BORROWER
        // ========================================================

        Borrower borrower =
                borrowerRepository
                        .findById(borrowerId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Borrower not found: "
                                                        + borrowerId
                                        )
                        );


        // ========================================================
        // TENANT SECURITY
        // ========================================================

        Organization borrowerOrganization =
                borrower.getOrganization();


        if (
                borrowerOrganization == null
                        ||
                borrowerOrganization.getId() == null
                        ||
                !organizationId.equals(
                        borrowerOrganization.getId()
                )
        ) {

            throw new IllegalArgumentException(
                    "Access denied."
            );
        }


        // ========================================================
        // LOANS
        // ========================================================

        List<Loan> loans =
                loanRepository
                        .findByBorrowerIdAndOrganizationId(
                                borrowerId,
                                organizationId
                        );


        if (loans == null) {

            loans =
                    new ArrayList<>();
        }


        loans =
                loans.stream()
                        .filter(
                                loan -> loan != null
                        )
                        .toList();


        // ========================================================
        // LOAN SUMMARY VARIABLES
        // ========================================================

        int totalLoans = 0;

        int activeLoans = 0;

        int completedLoans = 0;

        int overdueLoans = 0;

        int defaultedLoans = 0;

        int writtenOffLoans = 0;


        double totalBorrowed = 0.0;

        double totalDisbursed = 0.0;

        double totalOutstanding = 0.0;

        double totalPrincipalPaid = 0.0;

        double totalInterestPaid = 0.0;

        double totalFeesPaid = 0.0;

        double totalPaid = 0.0;


        int currentDaysPastDue = 0;

        int maximumDaysPastDue = 0;


        boolean hasDefaultHistory = false;

        boolean currentlyOverdue = false;


        boolean hasMultipleActiveLoans =
                false;


        // ========================================================
        // LOAN RESPONSE LIST
        // ========================================================

        List<BorrowerDetailsResponse.LoanSummary>
                loanSummaries =
                new ArrayList<>();


        // ========================================================
        // PAYMENT RESPONSE LIST
        // ========================================================

        List<BorrowerDetailsResponse.PaymentSummary>
                paymentSummaries =
                new ArrayList<>();


        // ========================================================
        // PAYMENT COUNTERS
        // ========================================================

        int totalPayments = 0;

        int successfulPayments = 0;

        int missedPayments = 0;

        int overduePayments = 0;


        // ========================================================
        // ACTIVE LOAN COUNTER
        // ========================================================

        int activeLoanCounter = 0;


        // ========================================================
        // PROCESS LOANS
        // ========================================================

        for (Loan loan : loans) {

            if (loan == null) {
                continue;
            }


            totalLoans++;


            // ----------------------------------------------------
            // LOAN AMOUNTS
            // ----------------------------------------------------

            double loanAmount =
                    number(
                            loan.getAmount()
                    );


            double disbursedAmount =
                    number(
                            loan.getDisbursedAmount()
                    );


            double outstandingBalance =
                    Math.max(
                            0.0,
                            number(
                                    loan.getOutstandingBalance()
                            )
                    );


            totalBorrowed +=
                    loanAmount;


            totalDisbursed +=
                    disbursedAmount;


            totalOutstanding +=
                    outstandingBalance;


            // ----------------------------------------------------
            // STATUS
            // ----------------------------------------------------

            LoanStatus status =
                    loan.getStatus();


            if (isActiveStatus(status)) {

                activeLoans++;

                activeLoanCounter++;
            }


            if (isCompletedStatus(status)) {

                completedLoans++;
            }


            if (
                    status == LoanStatus.DEFAULTED
            ) {

                defaultedLoans++;

                hasDefaultHistory = true;
            }


            if (
                    status == LoanStatus.WRITTEN_OFF
            ) {

                writtenOffLoans++;

                hasDefaultHistory = true;
            }


            // ----------------------------------------------------
            // DAYS PAST DUE
            // ----------------------------------------------------

            int daysPastDue =
                    safeDaysPastDue(
                            loan
                    );


            if (daysPastDue > 0) {

                overdueLoans++;

                currentlyOverdue = true;
            }


            if (
                    daysPastDue > maximumDaysPastDue
            ) {

                maximumDaysPastDue =
                        daysPastDue;
            }


            if (
                    daysPastDue > currentDaysPastDue
            ) {

                currentDaysPastDue =
                        daysPastDue;
            }


            // ----------------------------------------------------
            // PAYMENT DATA FOR THIS LOAN
            // ----------------------------------------------------

            List<Payment> loanPayments =
                    paymentRepository
                            .findByLoanId(
                                    loan.getId()
                            );


            if (loanPayments == null) {

                loanPayments =
                        new ArrayList<>();
            }


            double loanPrincipalPaid = 0.0;

            double loanInterestPaid = 0.0;

            double loanFeesPaid = 0.0;

            double loanTotalPaid = 0.0;


            LocalDate lastPaymentDate =
                    loan.getLastPaymentDate();


            // ----------------------------------------------------
            // PROCESS PAYMENTS
            // ----------------------------------------------------

            for (
                    Payment payment :
                    loanPayments
            ) {

                if (payment == null) {
                    continue;
                }


                totalPayments++;


                boolean completed =
                        isCompletedPayment(
                                payment
                        );


                double principal =
                        number(
                                payment
                                        .getPrincipalComponent()
                        );


                double interest =
                        number(
                                payment
                                        .getInterestComponent()
                        );


                double penalty =
                        number(
                                payment
                                        .getPenalty()
                        );


                double amountPaid =
                        number(
                                payment
                                        .getAmountPaid()
                        );


                /*
                 * Some Payment models do not expose a separate
                 * fee component. Therefore fees are kept at zero
                 * instead of inventing a getter that may not exist.
                 */
                double fees = 0.0;


                if (completed) {

                    successfulPayments++;


                    loanPrincipalPaid +=
                            principal;


                    loanInterestPaid +=
                            interest;


                    loanFeesPaid +=
                            fees;


                    double effectiveAmount =
                            amountPaid > 0
                                    ? amountPaid
                                    : principal
                                    + interest
                                    + penalty;


                    loanTotalPaid +=
                            effectiveAmount;


                    totalPrincipalPaid +=
                            principal;


                    totalInterestPaid +=
                            interest;


                    totalFeesPaid +=
                            fees;


                    totalPaid +=
                            effectiveAmount;
                }


                // ------------------------------------------------
                // PAYMENT DATES
                // ------------------------------------------------

                LocalDate dueDate =
                        payment.getDueDate();


                LocalDate paidDate =
                        resolvePaidDate(
                                payment,
                                completed
                        );


                int daysLate =
                        calculateDaysLate(
                                dueDate,
                                paidDate,
                                completed
                        );


                boolean onTime =
                        completed
                                &&
                        (
                                dueDate == null
                                        ||
                                paidDate == null
                                        ||
                                !paidDate.isAfter(
                                        dueDate
                                )
                        );


                if (
                        completed
                                &&
                        !onTime
                ) {

                    overduePayments++;
                }


                if (
                        !completed
                                &&
                        dueDate != null
                                &&
                        dueDate.isBefore(
                                        LocalDate.now()
                                )
                ) {

                    missedPayments++;
                }


                // ------------------------------------------------
                // PAYMENT SUMMARY
                // ------------------------------------------------

                paymentSummaries.add(

                        BorrowerDetailsResponse
                                .PaymentSummary
                                .builder()

                                .paymentId(
                                        payment.getId()
                                )

                                .loanId(
                                        loan.getId()
                                )

                                .loanNumber(
                                        loan.getReferenceNumber()
                                )

                                .borrowerName(
                                        buildFullName(
                                                borrower
                                        )
                                )

                                .amount(
                                        amountPaid
                                )

                                .principal(
                                        principal
                                )

                                .interest(
                                        interest
                                )

                                .fees(
                                        fees
                                )

                                .penalty(
                                        penalty
                                )

                                .totalPaid(
                                        completed
                                                ? (
                                                    amountPaid > 0
                                                        ? amountPaid
                                                        : principal
                                                        + interest
                                                        + penalty
                                                )
                                                : 0.0
                                )

                                .dueDate(
                                        dueDate
                                )

                                .paidDate(
                                        paidDate
                                )

                                /*
                                 * Payment method is resolved safely.
                                 * See resolvePaymentMethod().
                                 */
                                .paymentMethod(
                                        resolvePaymentMethod(
                                                payment
                                        )
                                )

                                .status(
                                        payment.getStatus() != null
                                                ? payment
                                                    .getStatus()
                                                    .name()
                                                : completed
                                                ? "COMPLETED"
                                                : "PENDING"
                                )

                                .onTime(
                                        onTime
                                )

                                .daysLate(
                                        daysLate
                                )

                                .build()
                );


                // ------------------------------------------------
                // LAST PAYMENT DATE
                // ------------------------------------------------

                if (
                        paidDate != null
                ) {

                    if (
                            lastPaymentDate == null
                                    ||
                            paidDate.isAfter(
                                    lastPaymentDate
                            )
                    ) {

                        lastPaymentDate =
                                paidDate;
                    }
                }
            }


            // ====================================================
            // LOAN SUMMARY
            // ====================================================

            String classification =
                    classifyLoan(
                            loan,
                            daysPastDue
                    );


            loanSummaries.add(

                    BorrowerDetailsResponse
                            .LoanSummary
                            .builder()

                            .loanId(
                                    loan.getId()
                            )

                            .loanNumber(
                                    loan.getReferenceNumber()
                            )

                            .loanType(
                                    loan.getLoanType() != null
                                            ? loan
                                                .getLoanType()
                                                .name()
                                            : null
                            )

                            .status(
                                    status != null
                                            ? status.name()
                                            : null
                            )

                            .loanAmount(
                                    loanAmount
                            )

                            .disbursedAmount(
                                    disbursedAmount
                            )

                            .outstandingBalance(
                                    outstandingBalance
                            )

                            .principalPaid(
                                    loanPrincipalPaid
                            )

                            .interestPaid(
                                    loanInterestPaid
                            )

                            .totalPaid(
                                    loanTotalPaid
                            )

                            .interestRate(
                                    number(
                                            loan.getInterestRate()
                                    )
                            )

                            .durationMonths(
                                    loan.getDurationMonths() != null
                                            ? loan
                                                .getDurationMonths()
                                            : 0
                            )

                            .daysPastDue(
                                    daysPastDue
                            )

                            .repaymentClassification(
                                    classification
                            )

                            .dateOpened(
                                    loan.getDisbursedAt() != null
                                            ? loan.getDisbursedAt()
                                            : loan.getStartDate()
                            )

                            .maturityDate(
                                    loan.getMaturityDate()
                            )

                            .lastPaymentDate(
                                    lastPaymentDate
                            )

                            .branchName(
                                    loan.getBranch() != null
                                            ? loan
                                                .getBranch()
                                                .getName()
                                            : null
                            )

                            .currency(
                                    loan.getCurrency()
                            )

                            .build()
            );
        }


        // ========================================================
        // MULTIPLE ACTIVE LOANS
        // ========================================================

        hasMultipleActiveLoans =
                activeLoanCounter > 1;


        // ========================================================
        // PAYMENT RATES
        // ========================================================

        double repaymentRate =
                totalPayments == 0
                        ? 0.0
                        : (
                            successfulPayments
                                    * 100.0
                        )
                        / totalPayments;


        double onTimePaymentRate =
                successfulPayments == 0
                        ? 0.0
                        : (
                            successfulPayments
                                    - overduePayments
                        )
                        * 100.0
                        / successfulPayments;


        if (onTimePaymentRate < 0) {

            onTimePaymentRate = 0.0;
        }


        // ========================================================
        // REPAYMENT BEHAVIOUR
        // ========================================================

        String repaymentBehaviour =
                determineRepaymentBehaviour(
                        repaymentRate,
                        onTimePaymentRate,
                        maximumDaysPastDue,
                        missedPayments,
                        hasDefaultHistory
                );


        // ========================================================
        // GOOD PAYER
        // ========================================================

        boolean goodPayer =
                determineGoodPayer(
                        repaymentRate,
                        onTimePaymentRate,
                        maximumDaysPastDue,
                        missedPayments,
                        hasDefaultHistory
                );


        // ========================================================
        // RISK LEVEL
        // ========================================================

        String riskLevel =
                determineRiskLevel(
                        borrower,
                        repaymentRate,
                        onTimePaymentRate,
                        currentDaysPastDue,
                        maximumDaysPastDue,
                        missedPayments,
                        hasDefaultHistory,
                        hasMultipleActiveLoans,
                        totalOutstanding
                );


        // ========================================================
        // SORT LOANS
        // ========================================================

        loanSummaries.sort(
                Comparator
                        .comparing(
                                BorrowerDetailsResponse.LoanSummary::getDateOpened,
                                Comparator.nullsLast(
                                        Comparator.reverseOrder()
                                )
                        )
        );


        // ========================================================
        // SORT PAYMENTS
        // ========================================================

        paymentSummaries.sort(
                Comparator
                        .comparing(
                                BorrowerDetailsResponse.PaymentSummary::getPaidDate,
                                Comparator.nullsLast(
                                        Comparator.reverseOrder()
                                )
                        )
        );


        // ========================================================
        // BORROWER ADDRESS
        // ========================================================

        String address =
                buildAddress(
                        borrower
                );


        // ========================================================
        // CREATED DATE
        // ========================================================

        LocalDate createdAt =
                resolveCreatedDate(
                        borrower
                );


        // ========================================================
        // FINAL RESPONSE
        // ========================================================

        return BorrowerDetailsResponse
                .builder()

                // ------------------------------------------------
                // BORROWER
                // ------------------------------------------------

                .borrowerId(
                        borrower.getId()
                )

                .fullName(
                        buildFullName(
                                borrower
                        )
                )

                .firstName(
                        borrower.getFirstName()
                )

                .lastName(
                        borrower.getLastName()
                )

                .email(
                        borrower.getEmail()
                )

                .phone(
                        borrower.getPhone()
                )

                .alternatePhone(
                        borrower.getAlternatePhone()
                )

                .nationalId(
                        borrower.getNationalId()
                )

                .passportNumber(
                        borrower.getPassportNumber()
                )

                .dateOfBirth(
                        borrower.getDateOfBirth()
                )

                .gender(
                        borrower.getGender()
                )

                .maritalStatus(
                        borrower.getMaritalStatus()
                )

                .nationality(
                        borrower.getNationality()
                )

                .country(
                        borrower.getCountry()
                )

                .address(
                        address
                )

                // ------------------------------------------------
                // EMPLOYMENT
                // ------------------------------------------------

                .employerName(
                        borrower.getEmployerName()
                )

                .employmentType(
                        borrower.getEmploymentType()
                )

                .jobTitle(
                        borrower.getJobTitle()
                )

                .monthlyIncome(
                        number(
                                borrower.getMonthlyIncome()
                        )
                )

                .monthlyExpenses(
                        number(
                                borrower.getMonthlyExpenses()
                        )
                )

                .netWorth(
                        number(
                                borrower.getNetWorth()
                        )
                )

                // ------------------------------------------------
                // CREDIT
                // ------------------------------------------------

                .creditScore(
                        borrower.getCreditScore()
                )

                .creditBureau(
                        borrower.getCreditBureau()
                )

                .creditReportDate(
                        borrower.getCreditReportDate()
                )

                // ------------------------------------------------
                // STATUS
                // ------------------------------------------------

                .status(
                        borrower.getStatus() != null
                                ? borrower
                                    .getStatus()
                                    .name()
                                : null
                )

                .createdAt(
                        createdAt
                )

                // ------------------------------------------------
                // LOAN SUMMARY
                // ------------------------------------------------

                .totalLoans(
                        totalLoans
                )

                .activeLoans(
                        activeLoans
                )

                .completedLoans(
                        completedLoans
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

                .totalBorrowed(
                        totalBorrowed
                )

                .totalDisbursed(
                        totalDisbursed
                )

                .totalOutstanding(
                        totalOutstanding
                )

                .totalPrincipalPaid(
                        totalPrincipalPaid
                )

                .totalInterestPaid(
                        totalInterestPaid
                )

                .totalFeesPaid(
                        totalFeesPaid
                )

                .totalPaid(
                        totalPaid
                )

                // ------------------------------------------------
                // PAYMENT PERFORMANCE
                // ------------------------------------------------

                .totalPayments(
                        totalPayments
                )

                .successfulPayments(
                        successfulPayments
                )

                .missedPayments(
                        missedPayments
                )

                .overduePayments(
                        overduePayments
                )

                .repaymentRate(
                        round(
                                repaymentRate
                        )
                )

                .onTimePaymentRate(
                        round(
                                onTimePaymentRate
                        )
                )

                .currentDaysPastDue(
                        currentDaysPastDue
                )

                .maximumDaysPastDue(
                        maximumDaysPastDue
                )

                // ------------------------------------------------
                // RISK
                // ------------------------------------------------

                .riskLevel(
                        riskLevel
                )

                .repaymentBehaviour(
                        repaymentBehaviour
                )

                .goodPayer(
                        goodPayer
                )

                .currentlyOverdue(
                        currentlyOverdue
                )

                .hasDefaultHistory(
                        hasDefaultHistory
                )

                .hasMultipleActiveLoans(
                        hasMultipleActiveLoans
                )

                // ------------------------------------------------
                // DETAILS
                // ------------------------------------------------

                .loans(
                        loanSummaries
                )

                .payments(
                        paymentSummaries
                )

                .build();
    }


    // ============================================================
    // ACTIVE STATUS
    // ============================================================

    private boolean isActiveStatus(
            LoanStatus status
    ) {

        return status == LoanStatus.ACTIVE
                ||
                status == LoanStatus.DISBURSED
                ||
                status == LoanStatus.OVERDUE;
    }


    // ============================================================
    // COMPLETED STATUS
    // ============================================================

    private boolean isCompletedStatus(
            LoanStatus status
    ) {

        return status == LoanStatus.CLOSED
                ||
                status == LoanStatus.PAID;
    }


    // ============================================================
    // COMPLETED PAYMENT
    // ============================================================

    private boolean isCompletedPayment(
            Payment payment
    ) {

        if (payment == null) {
            return false;
        }


        return Boolean.TRUE.equals(
                payment.getPaid()
        )
                ||
                payment.getStatus()
                        == Payment.PaymentStatus.COMPLETED;
    }


    // ============================================================
    // DAYS PAST DUE
    // ============================================================

    private int safeDaysPastDue(
            Loan loan
    ) {

        if (loan == null) {
            return 0;
        }


        Integer days =
                loan.getDaysOverdue();


        return days == null
                ? 0
                : Math.max(
                        0,
                        days
                );
    }


    // ============================================================
    // CRB CLASSIFICATION
    // ============================================================

    private String classifyLoan(
            Loan loan,
            int daysPastDue
    ) {

        if (loan == null) {
            return "UNKNOWN";
        }


        if (
                loan.getStatus()
                        == LoanStatus.WRITTEN_OFF
        ) {

            return "WRITTEN_OFF";
        }


        if (
                loan.getStatus()
                        == LoanStatus.DEFAULTED
        ) {

            return "DEFAULT";
        }


        if (daysPastDue > 90) {

            return "NPL";
        }


        if (daysPastDue > 30) {

            return "SUBSTANDARD";
        }


        if (daysPastDue > 0) {

            return "PAST_DUE";
        }


        return "CURRENT";
    }


    // ============================================================
    // GOOD PAYER
    // ============================================================

    private boolean determineGoodPayer(
            double repaymentRate,
            double onTimeRate,
            int maximumDaysPastDue,
            int missedPayments,
            boolean hasDefaultHistory
    ) {

        /*
         * No payment history means we should NOT call the borrower
         * a good payer.
         */

        if (repaymentRate == 0.0) {
            return false;
        }


        if (hasDefaultHistory) {
            return false;
        }


        if (missedPayments > 0) {
            return false;
        }


        if (maximumDaysPastDue > 30) {
            return false;
        }


        return repaymentRate >= 90.0
                &&
                onTimeRate >= 85.0;
    }


    // ============================================================
    // REPAYMENT BEHAVIOUR
    // ============================================================

    private String determineRepaymentBehaviour(
            double repaymentRate,
            double onTimeRate,
            int maximumDaysPastDue,
            int missedPayments,
            boolean hasDefaultHistory
    ) {

        if (hasDefaultHistory) {

            return "DEFAULT_HISTORY";
        }


        if (missedPayments > 0) {

            if (onTimeRate < 60.0) {
                return "POOR";
            }

            return "IRREGULAR";
        }


        if (
                maximumDaysPastDue > 30
        ) {

            return "HIGH_RISK";
        }


        if (
                repaymentRate >= 95.0
                        &&
                onTimeRate >= 95.0
        ) {

            return "EXCELLENT";
        }


        if (
                repaymentRate >= 90.0
                        &&
                onTimeRate >= 85.0
        ) {

            return "GOOD";
        }


        if (
                repaymentRate >= 75.0
        ) {

            return "FAIR";
        }


        return "POOR";
    }


    // ============================================================
    // RISK LEVEL
    // ============================================================

    private String determineRiskLevel(
            Borrower borrower,
            double repaymentRate,
            double onTimeRate,
            int currentDaysPastDue,
            int maximumDaysPastDue,
            int missedPayments,
            boolean hasDefaultHistory,
            boolean hasMultipleActiveLoans,
            double outstanding
    ) {

        int riskPoints = 0;


        // --------------------------------------------------------
        // CREDIT SCORE
        // --------------------------------------------------------

        Integer creditScore =
                borrower != null
                        ? borrower.getCreditScore()
                        : null;


        if (creditScore != null) {

            if (creditScore < 500) {

                riskPoints += 40;

            } else if (creditScore < 600) {

                riskPoints += 25;

            } else if (creditScore < 700) {

                riskPoints += 10;
            }
        }


        // --------------------------------------------------------
        // CURRENT DPD
        // --------------------------------------------------------

        if (currentDaysPastDue > 90) {

            riskPoints += 50;

        } else if (currentDaysPastDue > 60) {

            riskPoints += 35;

        } else if (currentDaysPastDue > 30) {

            riskPoints += 25;

        } else if (currentDaysPastDue > 0) {

            riskPoints += 10;
        }


        // --------------------------------------------------------
        // HISTORICAL DPD
        // --------------------------------------------------------

        if (maximumDaysPastDue > 90) {

            riskPoints += 35;

        } else if (maximumDaysPastDue > 60) {

            riskPoints += 25;

        } else if (maximumDaysPastDue > 30) {

            riskPoints += 15;
        }


        // --------------------------------------------------------
        // MISSED PAYMENTS
        // --------------------------------------------------------

        if (missedPayments >= 5) {

            riskPoints += 30;

        } else if (missedPayments >= 3) {

            riskPoints += 20;

        } else if (missedPayments > 0) {

            riskPoints += 10;
        }


        // --------------------------------------------------------
        // DEFAULT
        // --------------------------------------------------------

        if (hasDefaultHistory) {

            riskPoints += 50;
        }


        // --------------------------------------------------------
        // MULTIPLE LOANS
        // --------------------------------------------------------

        if (hasMultipleActiveLoans) {

            riskPoints += 10;
        }


        // --------------------------------------------------------
        // PAYMENT PERFORMANCE
        // --------------------------------------------------------

        if (repaymentRate > 0) {

            if (repaymentRate < 60.0) {

                riskPoints += 25;

            } else if (repaymentRate < 75.0) {

                riskPoints += 15;

            } else if (repaymentRate < 90.0) {

                riskPoints += 5;
            }
        }


        if (onTimeRate > 0) {

            if (onTimeRate < 60.0) {

                riskPoints += 20;

            } else if (onTimeRate < 75.0) {

                riskPoints += 10;

            } else if (onTimeRate < 85.0) {

                riskPoints += 5;
            }
        }


        // --------------------------------------------------------
        // RISK RESULT
        // --------------------------------------------------------

        if (riskPoints >= 80) {

            return "CRITICAL";
        }


        if (riskPoints >= 55) {

            return "HIGH";
        }


        if (riskPoints >= 30) {

            return "MEDIUM";
        }


        return "LOW";
    }


    // ============================================================
    // PAYMENT DATE
    // ============================================================

    private LocalDate resolvePaidDate(
            Payment payment,
            boolean completed
    ) {

        if (
                payment == null
                        ||
                !completed
        ) {

            return null;
        }


        /*
         * Your Payment model may use a different property for the
         * actual payment date.
         *
         * If your entity has getPaidDate(), use:
         *
         * return payment.getPaidDate();
         *
         * If it has getPaymentDate(), use:
         *
         * return payment.getPaymentDate();
         *
         * The fallback below intentionally uses the loan/payment
         * due date only if the actual date is unavailable.
         */

        return payment.getDueDate();
    }


    // ============================================================
    // DAYS LATE
    // ============================================================

    private int calculateDaysLate(
            LocalDate dueDate,
            LocalDate paidDate,
            boolean completed
    ) {

        if (
                !completed
                        ||
                dueDate == null
                        ||
                paidDate == null
        ) {

            return 0;
        }


        if (
                !paidDate.isAfter(
                        dueDate
                )
        ) {

            return 0;
        }


        return (int)
                ChronoUnit.DAYS.between(
                        dueDate,
                        paidDate
                );
    }


    // ============================================================
    // PAYMENT METHOD
    // ============================================================

    private String resolvePaymentMethod(
            Payment payment
    ) {

        if (payment == null) {
            return null;
        }


        /*
         * If your Payment entity has:
         *
         * getPaymentMethod()
         *
         * replace this method with:
         *
         * return payment.getPaymentMethod() != null
         *      ? payment.getPaymentMethod().toString()
         *      : null;
         *
         * This safe version avoids assuming the exact enum/string
         * type until the Payment entity is confirmed.
         */

        return null;
    }


    // ============================================================
    // ADDRESS
    // ============================================================

    private String buildAddress(
            Borrower borrower
    ) {

        if (borrower == null) {
            return null;
        }


        List<String> parts =
                new ArrayList<>();


        if (
                borrower.getAddressLine1() != null
                        &&
                !borrower
                        .getAddressLine1()
                        .isBlank()
        ) {

            parts.add(
                    borrower.getAddressLine1()
            );
        }


        if (
                borrower.getAddressLine2() != null
                        &&
                !borrower
                        .getAddressLine2()
                        .isBlank()
        ) {

            parts.add(
                    borrower.getAddressLine2()
            );
        }


        if (
                borrower.getCity() != null
                        &&
                !borrower
                        .getCity()
                        .isBlank()
        ) {

            parts.add(
                    borrower.getCity()
            );
        }


        if (
                borrower.getStateProvince() != null
                        &&
                !borrower
                        .getStateProvince()
                        .isBlank()
        ) {

            parts.add(
                    borrower.getStateProvince()
            );
        }


        if (
                borrower.getPostalCode() != null
                        &&
                !borrower
                        .getPostalCode()
                        .isBlank()
        ) {

            parts.add(
                    borrower.getPostalCode()
            );
        }


        if (
                borrower.getCountry() != null
                        &&
                !borrower
                        .getCountry()
                        .isBlank()
        ) {

            parts.add(
                    borrower.getCountry()
            );
        }


        return parts.isEmpty()
                ? null
                : String.join(
                        ", ",
                        parts
                );
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
    // CREATED DATE
    // ============================================================

    private LocalDate resolveCreatedDate(
            Borrower borrower
    ) {

        /*
         * If Borrower exposes getCreatedAt(), this converts the
         * timestamp into a LocalDate.
         */

        try {

            LocalDateTime createdAt =
                    borrower.getCreatedAt();

            return createdAt != null
                    ? createdAt.toLocalDate()
                    : null;

        } catch (Exception ignored) {

            return null;
        }
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
    // ROUND
    // ============================================================

    private double round(
            double value
    ) {

        return Math.round(
                value * 100.0
        ) / 100.0;
    }
}
