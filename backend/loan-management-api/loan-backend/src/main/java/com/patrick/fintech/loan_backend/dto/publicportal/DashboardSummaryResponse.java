package com.patrick.fintech.loan_backend.dto.publicportal;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class DashboardSummaryResponse {

    private Integer totalLoans;

    private Integer activeLoans;

    private BigDecimal totalBorrowed;

    private BigDecimal outstandingBalance;

    private BigDecimal totalPaid;

    private BigDecimal nextPaymentAmount;

    private LocalDate nextPaymentDate;

    private Integer overdueLoans;
}