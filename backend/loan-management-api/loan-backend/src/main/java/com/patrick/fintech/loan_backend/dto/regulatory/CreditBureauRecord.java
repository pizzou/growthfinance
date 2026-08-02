package com.patrick.fintech.loan_backend.dto.regulatory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditBureauRecord {

    // ============================================================
    // BORROWER
    // ============================================================

    private Long borrowerId;

    private String nationalId;

    private String fullName;

    private LocalDate dateOfBirth;

    private String gender;

    private String phone;

    // ============================================================
    // LOAN FACILITY
    // ============================================================

    private String loanNumber;

    private String loanType;

    private String loanStatus;

    private double loanAmount;

    private double outstandingBalance;

    private int daysPastDue;

    // ============================================================
    // CREDIT
    // ============================================================

    private Integer creditScore;

    private LocalDate creditReportDate;

    // ============================================================
    // DATES
    // ============================================================

    private LocalDate dateOpened;

    private LocalDate lastPaymentDate;

    private LocalDate maturityDate;

    private LocalDate dateClosed;

    // ============================================================
    // BRANCH / CURRENCY
    // ============================================================

    private Long branchId;

    private String branchName;

    private String currency;

    // ============================================================
    // REPAYMENT INFORMATION
    // ============================================================

    private double principalPaid;

    private double interestPaid;

    private double totalPaid;

    private double amountPastDue;

    private double principalPastDue;

    private double interestPastDue;

    private double penaltyPastDue;

    private int numberOfPayments;

    private int missedPayments;

    // ============================================================
    // CRB CLASSIFICATION
    // ============================================================

    private boolean current;

    private boolean overdue;

    private boolean defaulted;

    private boolean writtenOff;

    private boolean closed;

    private boolean restructured;
}