package com.patrick.fintech.loan_backend.dto.publicportal;

import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpcomingInstallmentResponse {

    private Integer installmentNumber;

    private LocalDate dueDate;

    private Double amount;

    private Double principal;

    private Double interest;

    private String status;

}