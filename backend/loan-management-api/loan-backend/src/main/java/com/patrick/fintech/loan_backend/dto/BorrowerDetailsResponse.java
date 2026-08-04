package com.patrick.fintech.loan_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BorrowerDetailsResponse {

    // ============================================================
    // BORROWER PROFILE
    // ============================================================

    private Long borrowerId;

    private String fullName;

    private String firstName;

    private String lastName;

    private String email;

    private String phone;

    private String alternatePhone;

    private String nationalId;

    private String passportNumber;

    private LocalDate dateOfBirth;

    private String gender;

    private String maritalStatus;

    private String nationality;

    private String country;

    private String address;

    // ============================================================
    // EMPLOYMENT / FINANCIAL PROFILE
    // ============================================================

    private String employerName;

    private String employmentType;

    private String jobTitle;

    private Double monthlyIncome;

    private Double monthlyExpenses;

    private Double netWorth;

    private Integer creditScore;

    private String creditBureau;

    private LocalDate creditReportDate;

    // ============================================================
    // BORROWER STATUS
    // ============================================================

    private String status;

    private LocalDate createdAt;

    // ============================================================
    // LOAN SUMMARY
    // ============================================================

    private int totalLoans;

    private int activeLoans;

    private int completedLoans;

    private int overdueLoans;

    private int defaultedLoans;

    private int writtenOffLoans;

    private double totalBorrowed;

    private double totalDisbursed;

    private double totalOutstanding;

    private double totalPrincipalPaid;

    private double totalInterestPaid;

    private double totalFeesPaid;

    private double totalPaid;

    // ============================================================
    // REPAYMENT PERFORMANCE
    // ============================================================

    private int totalPayments;

    private int successfulPayments;

    private int missedPayments;

    private int overduePayments;

    private double repaymentRate;

    private double onTimePaymentRate;

    private int currentDaysPastDue;

    private int maximumDaysPastDue;

    // ============================================================
    // RISK
    // ============================================================

    private String riskLevel;

    private String repaymentBehaviour;

    private boolean goodPayer;

    private boolean currentlyOverdue;

    private boolean hasDefaultHistory;

    private boolean hasMultipleActiveLoans;

    // ============================================================
    // LOANS
    // ============================================================

    private List<LoanSummary> loans;

    // ============================================================
    // PAYMENTS
    // ============================================================

    private List<PaymentSummary> payments;


    // ============================================================
    // LOAN SUMMARY
    // ============================================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoanSummary {

        private Long loanId;

        private String loanNumber;

        private String loanType;

        private String status;

        private double loanAmount;

        private double disbursedAmount;

        private double outstandingBalance;

        private double principalPaid;

        private double interestPaid;

        private double totalPaid;

        private double interestRate;

        private int durationMonths;

        private int daysPastDue;

        private String repaymentClassification;

        private LocalDate dateOpened;

        private LocalDate maturityDate;

        private LocalDate lastPaymentDate;

        private String branchName;

        private String currency;
    }


    // ============================================================
    // PAYMENT SUMMARY
    // ============================================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSummary {

        private Long paymentId;

        private Long loanId;

        private String loanNumber;

        private String borrowerName;

        private double amount;

        private double principal;

        private double interest;

        private double fees;

        private double penalty;

        private double totalPaid;

        private LocalDate dueDate;

        private LocalDate paidDate;

        private String paymentMethod;

        private String status;

        private boolean onTime;

        private int daysLate;
    }
}