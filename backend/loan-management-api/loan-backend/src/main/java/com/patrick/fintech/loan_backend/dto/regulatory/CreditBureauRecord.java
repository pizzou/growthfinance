
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

    private Long borrowerId;

    private String fullName;

    private String nationalId;

    private LocalDate dateOfBirth;

    private String gender;

    private String phone;

    private String loanNumber;

    private String loanType;

    private String loanStatus;

    private String repaymentClassification;

    private double loanAmount;

    private double outstandingBalance;

    private int daysPastDue;

    private Integer creditScore;

    private LocalDate dateOpened;

    private LocalDate lastPaymentDate;

    private LocalDate maturityDate;

    private LocalDate dateClosed;

    private String branchName;

    private String currency;
}