package com.patrick.fintech.loan_backend.dto.regulatory;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * Borrower-level credit record for export to an authorized credit bureau. Unlike the BNR
 * report (aggregate portfolio statistics), the bureau needs per-loan identity + repayment
 * data so it can update the borrower's national credit file.
 */
@Data @Builder
public class CreditBureauRecord {
    private Long   borrowerId;
    private String fullName;
    private String nationalId;
    private LocalDate dateOfBirth;
    private String gender;
    private String phone;

    private String loanNumber;
    private String loanType;
    private String loanStatus;
    private Double loanAmount;
    private Double outstandingBalance;
    private Integer daysPastDue;
    private Integer creditScore;

    private LocalDate dateOpened;
    private LocalDate lastPaymentDate;
    private LocalDate maturityDate;
    private LocalDate dateClosed;   // populated only for CLOSED / PAID loans

    private String branchName;
    private String currency;
}