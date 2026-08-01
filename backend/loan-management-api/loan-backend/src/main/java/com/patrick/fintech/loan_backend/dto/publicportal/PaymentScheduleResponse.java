package com.patrick.fintech.loan_backend.dto.publicportal;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PaymentScheduleResponse {

    private Integer installmentNumber;

    private LocalDate dueDate;

    private BigDecimal installmentAmount;

    private BigDecimal principal;

    private BigDecimal interest;

    private BigDecimal penalty;

    private BigDecimal paid;

    private BigDecimal balance;

    private String status;

}