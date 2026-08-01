package com.patrick.fintech.loan_backend.dto;

import com.patrick.fintech.loan_backend.model.Loan;
import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanRequest {
    @NotNull  private Long    borrowerId;
    @NotNull @Positive private Double  amount;
    private Double  interestRate;
    /** "MONTHLY" or "ANNUAL" — how to interpret interestRate above when it's supplied.
     *  Ignored (falls back to the product's own type) when interestRate is null. */
    private String  interestRateType;
    @NotNull @Min(1) @Max(12) private Integer durationMonths;
    private String  currency;
    private String  startDate;
    private String  purpose;
    private String  notes;
    private Double  collateralValue;
    private String  collateralDescription;
    private Loan.LoanType         loanType;
    private Loan.RepaymentFrequency repaymentFrequency;
    private String  disbursementMethod;
    private String  disbursementAccount;
}
