package com.patrick.fintech.loan_backend.dto.publicportal;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpcomingInstallmentResponse {

    private Integer installmentNumber;

    private LocalDate dueDate;

    private BigDecimal amount;

    private BigDecimal principal;

    private BigDecimal interest;

    private String status;

}