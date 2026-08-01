package com.patrick.fintech.loan_backend.dto.publicportal;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class DashboardSummaryResponse {

    private Integer totalLoans;

    private Integer activeLoans;

    private Double totalBorrowed;

    private Double outstandingBalance;

    private Double totalPaid;

    private Double nextPaymentAmount;

    private LocalDate nextPaymentDate;

    private Integer overdueLoans;
}