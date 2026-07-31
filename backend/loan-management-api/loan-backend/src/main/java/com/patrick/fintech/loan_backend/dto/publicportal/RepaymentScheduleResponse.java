package com.patrick.fintech.loan_backend.dto.publicportal;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class RepaymentScheduleResponse {

    private Integer installmentNo;

    private LocalDate dueDate;

    private Double installmentAmount;

    private Double principalAmount;

    private Double interestAmount;

    private Double penalty;

    private Double amountPaid;

    private Double balanceAfterPayment;

    private String status;
}