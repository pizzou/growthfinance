package com.patrick.fintech.loan_backend.dto.publicportal;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class PaymentScheduleResponse {

    private Integer installmentNumber;

    private LocalDate dueDate;

    private Double installmentAmount;

    private Double principal;

    private Double interest;

    private Double penalty;

    private Double paid;

    private Double balance;

    private String status;

}