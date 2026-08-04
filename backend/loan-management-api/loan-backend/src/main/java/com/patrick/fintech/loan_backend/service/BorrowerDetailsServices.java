package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.BorrowerDetailsResponse;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BorrowerDetailsServices {

    private final BorrowerRepository borrowerRepository;
    private final LoanRepository loanRepository;
    private final PaymentRepository paymentRepository;


    // ============================================================
    // MAIN METHOD
    // ============================================================

    @Transactional(readOnly = true)
    public BorrowerDetailsResponse getBorrowerDetails(
            Long borrowerId,
            Organization organization
    ) {

        Borrower borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Borrower not found: " + borrowerId
                        )
                );

        // ========================================================
        // TENANT SECURITY
        // ========================================================

        if (borrower.getOrganization() == null ||
                borrower.getOrganization().getId() == null ||
                organization == null ||
                organization.getId() == null ||
                !borrower.getOrganization()
                        .getId()
                        .equals(organization.getId())) {

            throw new RuntimeException("Access denied");
        }


        // ========================================================
        // LOANS
        // ========================================================

        List<Loan> loans =
                loanRepository.findByBorrowerIdAndOrganizationId(
                        borrowerId,
                        organization.getId()
                );

        if (loans == null) {
            loans = new ArrayList<>();
        }


        // ========================================================
        // PAYMENTS
        // ========================================================

        List<Payment> payments = new ArrayList<>();

        for (Loan loan : loans) {

            if (loan == null || loan.getId() == null) {
                continue;
            }

            List<Payment> loanPayments =
                    paymentRepository.findByLoanId(
                            loan.getId()
                    );

            if (loanPayments != null) {
                payments.addAll(loanPayments);
            }
        }


        // ========================================================
        // RESPONSE
        // ========================================================

        BorrowerDetailsResponse response =
                BorrowerDetailsResponse.builder()

                        // ------------------------------------------------
                        // BORROWER PROFILE
                        // ------------------------------------------------

                        .borrowerId(
                                borrower.getId()
                        )

                        .fullName(
                                buildFullName(borrower)
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
                                buildAddress(borrower)
                        )


                        // ------------------------------------------------
                        // EMPLOYMENT / FINANCIAL PROFILE
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
                                toDouble(
                                        borrower.getMonthlyIncome()
                                )
                        )

                        .monthlyExpenses(
                                toDouble(
                                        borrower.getMonthlyExpenses()
                                )
                        )

                        .netWorth(
                                toDouble(
                                        borrower.getNetWorth()
                                )
                        )

                        .creditScore(
                                borrower.getCreditScore()
                        )

                        .creditBureau(
                                borrower.getCreditBureau()
                        )

                        /*
                         * No credit report date field has been
                         * established on the Borrower model.
                         */
                        .creditReportDate(
                                null
                        )


                        // ------------------------------------------------
                        // BORROWER STATUS
                        // ------------------------------------------------

                        .status(
                                borrower.getStatus() != null
                                        ? borrower.getStatus().name()
                                        : null
                        )

                        /*
                         * Borrower.createdAt is LocalDateTime.
                         * Response expects LocalDate.
                         */
                        .createdAt(
                                borrower.getCreatedAt() != null
                                        ? borrower.getCreatedAt()
                                                .toLocalDate()
                                        : null
                        )


                        // ------------------------------------------------
                        // LOAN SUMMARY
                        // ------------------------------------------------

                        .totalLoans(
                                loans.size()
                        )

                        .activeLoans(
                                countActiveLoans(loans)
                        )

                        .completedLoans(
                                countCompletedLoans(loans)
                        )

                        .overdueLoans(
                                countOverdueLoans(loans)
                        )

                        .defaultedLoans(
                                countDefaultedLoans(loans)
                        )

                        .writtenOffLoans(
                                countWrittenOffLoans(loans)
                        )

                        .totalBorrowed(
                                sumLoanAmounts(loans)
                        )

                        .totalDisbursed(
                                sumDisbursedAmounts(loans)
                        )

                        .totalOutstanding(
                                sumOutstandingBalances(loans)
                        )

                        .totalPrincipalPaid(
                                calculatePrincipalPaid(loans)
                        )

                        .totalInterestPaid(
                                calculateInterestPaid(payments)
                        )

                        .totalFeesPaid(
                                calculateFeesPaid(payments)
                        )

                        .totalPaid(
                                calculateTotalPaid(payments, loans)
                        )


                        // ------------------------------------------------
                        // REPAYMENT PERFORMANCE
                        // ------------------------------------------------

                        .totalPayments(
                                payments.size()
                        )

                        .successfulPayments(
                                countSuccessfulPayments(payments)
                        )

                        .missedPayments(
                                countMissedPayments(payments)
                        )

                        .overduePayments(
                                countOverduePayments(payments)
                        )

                        .repaymentRate(
                                calculateRepaymentRate(loans)
                        )

                        .onTimePaymentRate(
                                calculateOnTimePaymentRate(
                                        payments
                                )
                        )

                        .currentDaysPastDue(
                                calculateCurrentDaysPastDue(loans)
                        )

                        .maximumDaysPastDue(
                                calculateMaximumDaysPastDue(loans)
                        )


                        // ------------------------------------------------
                        // RISK
                        // ------------------------------------------------

                        .riskLevel(
                                determineRiskLevel(
                                        borrower,
                                        loans,
                                        payments
                                )
                        )

                        .repaymentBehaviour(
                                determineRepaymentBehaviour(
                                        payments
                                )
                        )

                        .goodPayer(
                                isGoodPayer(
                                        loans,
                                        payments
                                )
                        )

                        .currentlyOverdue(
                                hasCurrentOverdueLoan(loans)
                        )

                        .hasDefaultHistory(
                                hasDefaultHistory(loans)
                        )

                        .hasMultipleActiveLoans(
                                countActiveLoans(loans) > 1
                        )


                        // ------------------------------------------------
                        // LOANS
                        // ------------------------------------------------

                        .loans(
                                buildLoanSummaries(loans)
                        )


                        // ------------------------------------------------
                        // PAYMENTS
                        // ------------------------------------------------

                        .payments(
                                buildPaymentSummaries(
                                        payments
                                )
                        )

                        .build();


        return response;
    }


    // ============================================================
    // LOAN SUMMARIES
    // ============================================================

    private List<BorrowerDetailsResponse.LoanSummary>
    buildLoanSummaries(List<Loan> loans) {

        List<BorrowerDetailsResponse.LoanSummary> result =
                new ArrayList<>();

        for (Loan loan : loans) {

            if (loan == null) {
                continue;
            }

            double amount =
                    safeDouble(loan.getAmount());

            double outstanding =
                    safeDouble(
                            loan.getOutstandingBalance()
                    );

            double totalPaid =
                    safeDouble(
                            loan.getTotalPaid()
                    );

            /*
             * Principal paid can be estimated from:
             *
             * original principal - current outstanding.
             */
            double principalPaid =
                    Math.max(
                            0.0,
                            amount - outstanding
                    );

            BorrowerDetailsResponse.LoanSummary summary =
                    BorrowerDetailsResponse.LoanSummary.builder()

                            .loanId(
                                    loan.getId()
                            )

                            /*
                             * Loan has referenceNumber,
                             * not loanNumber.
                             */
                            .loanNumber(
                                    loan.getReferenceNumber()
                            )

                            .loanType(
                                    loan.getLoanType() != null
                                            ? loan.getLoanType().name()
                                            : null
                            )

                            .status(
                                    loan.getStatus() != null
                                            ? loan.getStatus().name()
                                            : null
                            )

                            .loanAmount(
                                    amount
                            )

                            .disbursedAmount(
                                    safeDouble(
                                            loan.getDisbursedAmount()
                                    )
                            )

                            .outstandingBalance(
                                    outstanding
                            )

                            .principalPaid(
                                    principalPaid
                            )

                            /*
                             * We can calculate interest paid from
                             * actual Payment records below.
                             *
                             * For an individual loan, calculate
                             * directly from its payments.
                             */
                            .interestPaid(
                                    calculateLoanInterestPaid(
                                            loan
                                    )
                            )

                            .totalPaid(
                                    totalPaid
                            )

                            .interestRate(
                                    safeDouble(
                                            loan.getInterestRate()
                                    )
                            )

                            .durationMonths(
                                    safeInt(
                                            loan.getDurationMonths()
                                    )
                            )

                            /*
                             * Loan model calls this daysOverdue.
                             */
                            .daysPastDue(
                                    safeInt(
                                            loan.getDaysOverdue()
                                    )
                            )

                            .repaymentClassification(
                                    determineLoanClassification(
                                            loan
                                    )
                            )

                            .dateOpened(
                                    loan.getStartDate()
                            )

                            .maturityDate(
                                    loan.getMaturityDate()
                            )

                            .lastPaymentDate(
                                    loan.getLastPaymentDate()
                            )

                            .branchName(
                                    loan.getBranch() != null
                                            ? loan.getBranch()
                                                    .getName()
                                            : null
                            )

                            .currency(
                                    loan.getCurrency()
                            )

                            .build();

            result.add(summary);
        }

        return result;
    }


    // ============================================================
    // PAYMENT SUMMARIES
    // ============================================================

    private List<BorrowerDetailsResponse.PaymentSummary>
    buildPaymentSummaries(
            List<Payment> payments
    ) {

        List<BorrowerDetailsResponse.PaymentSummary> result =
                new ArrayList<>();

        for (Payment payment : payments) {

            if (payment == null) {
                continue;
            }

            Loan loan =
                    payment.getLoan();

            String borrowerName = "";

            if (loan != null &&
                    loan.getBorrower() != null) {

                borrowerName =
                        buildFullName(
                                loan.getBorrower()
                        );
            }


            double scheduledAmount =
                    safeDouble(
                            payment.getAmount()
                    );

            double amountPaid =
                    safeDouble(
                            payment.getAmountPaid()
                    );

            double principal =
                    safeDouble(
                            payment.getPrincipalComponent()
                    );

            double interest =
                    safeDouble(
                            payment.getInterestComponent()
                    );

            double penalty =
                    safeDouble(
                            payment.getPenalty()
                    );


            /*
             * There is no fee field in Payment.
             *
             * Therefore we do not fabricate a fee.
             */
            double fees = 0.0;


            LocalDate dueDate =
                    payment.getDueDate();

            LocalDate paidDate =
                    payment.getPaidDate();


            int daysLate =
                    safeInt(
                            payment.getDaysLate()
                    );


            /*
             * If daysLate wasn't stored correctly but both dates
             * exist, calculate it dynamically.
             */
            if (daysLate == 0 &&
                    dueDate != null &&
                    paidDate != null &&
                    paidDate.isAfter(dueDate)) {

                daysLate =
                        (int) ChronoUnit.DAYS.between(
                                dueDate,
                                paidDate
                        );
            }


            boolean onTime =
                    paidDate != null &&
                    dueDate != null &&
                    !paidDate.isAfter(dueDate);


            /*
             * If the payment was recorded as late, respect that
             * information as well.
             */
            if (payment.isLate()) {
                onTime = false;
            }


            String status =
                    payment.getStatus() != null
                            ? payment.getStatus().name()
                            : determinePaymentStatus(
                                    payment
                            );


            BorrowerDetailsResponse.PaymentSummary summary =
                    BorrowerDetailsResponse.PaymentSummary.builder()

                            .paymentId(
                                    payment.getId()
                            )

                            .loanId(
                                    loan != null
                                            ? loan.getId()
                                            : null
                            )

                            /*
                             * Again, referenceNumber is the actual
                             * field on Loan.
                             */
                            .loanNumber(
                                    loan != null
                                            ? loan.getReferenceNumber()
                                            : null
                            )

                            /*
                             * THIS IS THE IMPORTANT PART FOR YOUR
                             * PAYMENT DASHBOARD:
                             *
                             * Every payment now contains the
                             * borrower name.
                             */
                            .borrowerName(
                                    borrowerName
                            )

                            /*
                             * Scheduled installment amount.
                             */
                            .amount(
                                    scheduledAmount
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
                                    amountPaid
                            )

                            .dueDate(
                                    dueDate
                            )

                            .paidDate(
                                    paidDate
                            )

                            .paymentMethod(
                                    payment.getPaymentMethod()
                            )

                            .status(
                                    status
                            )

                            .onTime(
                                    onTime
                            )

                            .daysLate(
                                    daysLate
                            )

                            .build();

            result.add(summary);
        }


        // Newest payment first
        result.sort(
                Comparator.comparing(
                        BorrowerDetailsResponse.PaymentSummary
                                ::getPaidDate,
                        Comparator.nullsLast(
                                Comparator.reverseOrder()
                        )
                )
        );


        return result;
    }


    // ============================================================
    // INTEREST CALCULATIONS
    // ============================================================

    private double calculateInterestPaid(
            List<Payment> payments
    ) {

        double total = 0.0;

        for (Payment payment : payments) {

            if (payment == null) {
                continue;
            }

            total += safeDouble(
                    payment.getInterestComponent()
            );
        }

        return total;
    }


    private double calculateLoanInterestPaid(
            Loan loan
    ) {

        if (loan == null ||
                loan.getPayments() == null) {

            return 0.0;
        }

        double total = 0.0;

        for (Payment payment :
                loan.getPayments()) {

            if (payment == null) {
                continue;
            }

            total += safeDouble(
                    payment.getInterestComponent()
            );
        }

        return total;
    }


    // ============================================================
    // FEES
    // ============================================================

    private double calculateFeesPaid(
            List<Payment> payments
    ) {

        /*
         * Payment does not have a fee field.
         *
         * Loan has processingFee, but that is a loan-level fee,
         * not a payment-level allocation.
         *
         * We therefore do not incorrectly classify processing
         * fees as paid here.
         */
        return 0.0;
    }


    // ============================================================
    // TOTAL PAID
    // ============================================================

    private double calculateTotalPaid(
            List<Payment> payments,
            List<Loan> loans
    ) {

        /*
         * Prefer payment records because they represent actual
         * money received.
         */
        double total = 0.0;

        for (Payment payment : payments) {

            if (payment == null) {
                continue;
            }

            total += safeDouble(
                    payment.getAmountPaid()
            );
        }

        /*
         * If there are no payment records, fall back to the
         * loan totals.
         */
        if (payments.isEmpty()) {

            for (Loan loan : loans) {

                if (loan != null) {
                    total += safeDouble(
                            loan.getTotalPaid()
                    );
                }
            }
        }

        return total;
    }


    // ============================================================
    // LOAN COUNTS
    // ============================================================

    private int countActiveLoans(
            List<Loan> loans
    ) {

        int count = 0;

        for (Loan loan : loans) {

            if (loan == null ||
                    loan.getStatus() == null) {
                continue;
            }

            String status =
                    loan.getStatus().name();

            if ("ACTIVE".equals(status) ||
                    "DISBURSED".equals(status) ||
                    "OVERDUE".equals(status)) {

                count++;
            }
        }

        return count;
    }


    private int countCompletedLoans(
            List<Loan> loans
    ) {

        int count = 0;

        for (Loan loan : loans) {

            if (loan == null ||
                    loan.getStatus() == null) {
                continue;
            }

            String status =
                    loan.getStatus().name();

            if ("COMPLETED".equals(status) ||
                    "PAID".equals(status) ||
                    "CLOSED".equals(status)) {

                count++;
            }
        }

        return count;
    }


    private int countOverdueLoans(
            List<Loan> loans
    ) {

        int count = 0;

        for (Loan loan : loans) {

            if (loan == null) {
                continue;
            }

            if (safeInt(
                    loan.getDaysOverdue()
            ) > 0) {

                count++;
                continue;
            }

            if (loan.getStatus() != null &&
                    "OVERDUE".equals(
                            loan.getStatus().name()
                    )) {

                count++;
            }
        }

        return count;
    }


    private int countDefaultedLoans(
            List<Loan> loans
    ) {

        int count = 0;

        for (Loan loan : loans) {

            if (loan == null ||
                    loan.getStatus() == null) {
                continue;
            }

            String status =
                    loan.getStatus().name();

            if ("DEFAULTED".equals(status) ||
                    "DEFAULT".equals(status)) {

                count++;
            }
        }

        return count;
    }


    private int countWrittenOffLoans(
            List<Loan> loans
    ) {

        int count = 0;

        for (Loan loan : loans) {

            if (loan == null ||
                    loan.getStatus() == null) {
                continue;
            }

            if ("WRITTEN_OFF".equals(
                    loan.getStatus().name()
            )) {

                count++;
            }
        }

        return count;
    }


    // ============================================================
    // LOAN TOTALS
    // ============================================================

    private double sumLoanAmounts(
            List<Loan> loans
    ) {

        double total = 0.0;

        for (Loan loan : loans) {

            if (loan != null) {
                total += safeDouble(
                        loan.getAmount()
                );
            }
        }

        return total;
    }


    private double sumDisbursedAmounts(
            List<Loan> loans
    ) {

        double total = 0.0;

        for (Loan loan : loans) {

            if (loan != null) {
                total += safeDouble(
                        loan.getDisbursedAmount()
                );
            }
        }

        return total;
    }


    private double sumOutstandingBalances(
            List<Loan> loans
    ) {

        double total = 0.0;

        for (Loan loan : loans) {

            if (loan != null) {
                total += safeDouble(
                        loan.getOutstandingBalance()
                );
            }
        }

        return total;
    }


    private double calculatePrincipalPaid(
            List<Loan> loans
    ) {

        double total = 0.0;

        for (Loan loan : loans) {

            if (loan == null) {
                continue;
            }

            double amount =
                    safeDouble(
                            loan.getAmount()
                    );

            double outstanding =
                    safeDouble(
                            loan.getOutstandingBalance()
                    );

            total += Math.max(
                    0.0,
                    amount - outstanding
            );
        }

        return total;
    }


    // ============================================================
    // PAYMENT PERFORMANCE
    // ============================================================

    private int countSuccessfulPayments(
            List<Payment> payments
    ) {

        int count = 0;

        for (Payment payment : payments) {

            if (payment == null) {
                continue;
            }

            if (Boolean.TRUE.equals(
                    payment.getPaid()
            )) {

                count++;
            }
        }

        return count;
    }


    private int countMissedPayments(
            List<Payment> payments
    ) {

        int count = 0;

        LocalDate today =
                LocalDate.now();

        for (Payment payment : payments) {

            if (payment == null) {
                continue;
            }

            if (!Boolean.TRUE.equals(
                    payment.getPaid()
            ) &&
                    payment.getDueDate() != null &&
                    payment.getDueDate()
                            .isBefore(today)) {

                count++;
            }
        }

        return count;
    }


    private int countOverduePayments(
            List<Payment> payments
    ) {

        int count = 0;

        for (Payment payment : payments) {

            if (payment == null) {
                continue;
            }

            if (payment.isLate()) {
                count++;
                continue;
            }

            if (!Boolean.TRUE.equals(
                    payment.getPaid()
            ) &&
                    payment.getDueDate() != null &&
                    payment.getDueDate()
                            .isBefore(
                                    LocalDate.now()
                            )) {

                count++;
            }
        }

        return count;
    }


    // ============================================================
    // REPAYMENT RATE
    // ============================================================

    private double calculateRepaymentRate(
            List<Loan> loans
    ) {

        if (loans.isEmpty()) {
            return 0.0;
        }

        double totalRepayable = 0.0;
        double totalPaid = 0.0;

        for (Loan loan : loans) {

            if (loan == null) {
                continue;
            }

            totalRepayable += safeDouble(
                    loan.getTotalRepayable()
            );

            totalPaid += safeDouble(
                    loan.getTotalPaid()
            );
        }

        if (totalRepayable <= 0.0) {
            return 0.0;
        }

        return Math.min(
                100.0,
                (totalPaid / totalRepayable)
                        * 100.0
        );
    }


    private double calculateOnTimePaymentRate(
            List<Payment> payments
    ) {

        int paidPayments = 0;
        int onTimePayments = 0;

        for (Payment payment : payments) {

            if (payment == null ||
                    !Boolean.TRUE.equals(
                            payment.getPaid()
                    )) {

                continue;
            }

            paidPayments++;

            if (payment.isLate()) {
                continue;
            }

            if (payment.getDueDate() == null ||
                    payment.getPaidDate() == null) {

                continue;
            }

            if (!payment.getPaidDate()
                    .isAfter(
                            payment.getDueDate()
                    )) {

                onTimePayments++;
            }
        }

        if (paidPayments == 0) {
            return 0.0;
        }

        return (
                (double) onTimePayments
                        / paidPayments
        ) * 100.0;
    }


    private int calculateCurrentDaysPastDue(
            List<Loan> loans
    ) {

        int maximum = 0;

        for (Loan loan : loans) {

            if (loan == null) {
                continue;
            }

            maximum =
                    Math.max(
                            maximum,
                            safeInt(
                                    loan.getDaysOverdue()
                            )
                    );
        }

        return maximum;
    }


    private int calculateMaximumDaysPastDue(
            List<Loan> loans
    ) {

        int maximum = 0;

        for (Loan loan : loans) {

            if (loan == null) {
                continue;
            }

            maximum =
                    Math.max(
                            maximum,
                            safeInt(
                                    loan.getDaysOverdue()
                            )
                    );
        }

        return maximum;
    }


    // ============================================================
    // RISK
    // ============================================================

    private String determineRiskLevel(
            Borrower borrower,
            List<Loan> loans,
            List<Payment> payments
    ) {

        if (hasDefaultHistory(loans)) {
            return "CRITICAL";
        }

        if (hasCurrentOverdueLoan(loans)) {
            return "HIGH";
        }

        Integer creditScore =
                borrower.getCreditScore();

        double onTimeRate =
                calculateOnTimePaymentRate(
                        payments
                );

        if (creditScore != null) {

            if (creditScore < 500) {
                return "HIGH";
            }

            if (creditScore < 650) {
                return "MEDIUM";
            }

            if (creditScore >= 750 &&
                    onTimeRate >= 90.0) {

                return "LOW";
            }
        }

        if (payments.isEmpty()) {
            return "UNKNOWN";
        }

        if (onTimeRate >= 90.0) {
            return "LOW";
        }

        if (onTimeRate >= 70.0) {
            return "MEDIUM";
        }

        return "HIGH";
    }


    private String determineRepaymentBehaviour(
            List<Payment> payments
    ) {

        if (payments.isEmpty()) {
            return "NO_PAYMENT_HISTORY";
        }

        double rate =
                calculateOnTimePaymentRate(
                        payments
                );

        if (rate >= 95.0) {
            return "EXCELLENT";
        }

        if (rate >= 85.0) {
            return "GOOD";
        }

        if (rate >= 70.0) {
            return "FAIR";
        }

        if (rate >= 50.0) {
            return "POOR";
        }

        return "VERY_POOR";
    }


    private boolean isGoodPayer(
            List<Loan> loans,
            List<Payment> payments
    ) {

        if (payments.isEmpty()) {
            return false;
        }

        if (hasDefaultHistory(loans)) {
            return false;
        }

        if (hasCurrentOverdueLoan(loans)) {
            return false;
        }

        return calculateOnTimePaymentRate(
                payments
        ) >= 85.0;
    }


    private boolean hasCurrentOverdueLoan(
            List<Loan> loans
    ) {

        for (Loan loan : loans) {

            if (loan == null) {
                continue;
            }

            if (safeInt(
                    loan.getDaysOverdue()
            ) > 0) {

                return true;
            }

            if (loan.getStatus() != null &&
                    "OVERDUE".equals(
                            loan.getStatus().name()
                    )) {

                return true;
            }
        }

        return false;
    }


    private boolean hasDefaultHistory(
            List<Loan> loans
    ) {

        for (Loan loan : loans) {

            if (loan == null ||
                    loan.getStatus() == null) {
                continue;
            }

            String status =
                    loan.getStatus().name();

            if ("DEFAULTED".equals(status) ||
                    "DEFAULT".equals(status) ||
                    "WRITTEN_OFF".equals(status)) {

                return true;
            }
        }

        return false;
    }


    // ============================================================
    // LOAN CLASSIFICATION
    // ============================================================

    private String determineLoanClassification(
            Loan loan
    ) {

        if (loan == null) {
            return "UNKNOWN";
        }

        int days =
                safeInt(
                        loan.getDaysOverdue()
                );

        if (days > 90) {
            return "DEFAULT";
        }

        if (days > 30) {
            return "SUBSTANDARD";
        }

        if (days > 0) {
            return "WATCH";
        }

        return "CURRENT";
    }


    // ============================================================
    // PAYMENT STATUS
    // ============================================================

    private String determinePaymentStatus(
            Payment payment
    ) {

        if (payment == null) {
            return "UNKNOWN";
        }

        if (Boolean.TRUE.equals(
                payment.getPaid()
        )) {

            if (payment.isLate()) {
                return "PAID_LATE";
            }

            return "PAID";
        }

        if (payment.getDueDate() != null &&
                payment.getDueDate()
                        .isBefore(
                                LocalDate.now()
                        )) {

            return "OVERDUE";
        }

        return "PENDING";
    }


    // ============================================================
    // BORROWER NAME
    // ============================================================

    private String buildFullName(
            Borrower borrower
    ) {

        if (borrower == null) {
            return "";
        }

        String first =
                borrower.getFirstName() != null
                        ? borrower.getFirstName().trim()
                        : "";

        String last =
                borrower.getLastName() != null
                        ? borrower.getLastName().trim()
                        : "";

        return (first + " " + last).trim();
    }


    // ============================================================
    // ADDRESS
    // ============================================================

    private String buildAddress(
            Borrower borrower
    ) {

        List<String> parts =
                new ArrayList<>();

        addIfPresent(
                parts,
                borrower.getAddressLine1()
        );

        addIfPresent(
                parts,
                borrower.getAddressLine2()
        );

        addIfPresent(
                parts,
                borrower.getCity()
        );

        addIfPresent(
                parts,
                borrower.getStateProvince()
        );

        addIfPresent(
                parts,
                borrower.getPostalCode()
        );

        addIfPresent(
                parts,
                borrower.getCountry()
        );

        return String.join(
                ", ",
                parts
        );
    }


    private void addIfPresent(
            List<String> parts,
            String value
    ) {

        if (value != null &&
                !value.isBlank()) {

            parts.add(value);
        }
    }


    // ============================================================
    // SAFE CONVERSIONS
    // ============================================================

    private double safeDouble(
            Double value
    ) {

        return value != null
                ? value
                : 0.0;
    }


    private double toDouble(
            Number value
    ) {

        return value != null
                ? value.doubleValue()
                : 0.0;
    }


    private int safeInt(
            Integer value
    ) {

        return value != null
                ? value
                : 0;
    }
}
