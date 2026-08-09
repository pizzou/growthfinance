
package com.patrick.fintech.loan_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.patrick.fintech.loan_backend.model.Loan;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanRequest {

    @NotNull
    private Long borrowerId;

    @NotNull
    @Positive
    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("interestRate")
    private BigDecimal interestRate;

   
    private String interestRateType;

    @NotNull
    @Min(1)
    @Max(12)
    private Integer durationMonths;

    private String currency;

    private String startDate;

    private String purpose;

    private String notes;

    @JsonProperty("collateralValue")
    private BigDecimal collateralValue;

    private String collateralDescription;

    private Loan.LoanType loanType;

    private Loan.RepaymentFrequency repaymentFrequency;

    private String disbursementMethod;

    private String disbursementAccount;
}
