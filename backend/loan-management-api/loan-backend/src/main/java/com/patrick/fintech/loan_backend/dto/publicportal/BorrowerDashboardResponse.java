package com.patrick.fintech.loan_backend.dto.publicportal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BorrowerDashboardResponse {

    private Long loanId;

    private String referenceNumber;

    private String borrowerName;

    private String loanOfficer;

    private String status;

    private String loanType;

    private BigDecimal principal;

    private BigDecimal outstandingBalance;

    private BigDecimal totalPaid;

    private BigDecimal totalRepayable;

    private BigDecimal nextInstallmentAmount;

    private LocalDate nextPaymentDate;
    private Integer daysUntilDue;

    private LocalDate maturityDate;

    private Integer missedInstallments;

    private Integer daysOverdue;

    private String currency;

    /*
     * IMPORTANT:
     * Loan.interestRate is BigDecimal,
     * so this must also be BigDecimal.
     */
    private BigDecimal interestRate;

    private String interestRateType;

    private List<PaymentHistoryResponse> recentPayments;

    private List<UpcomingInstallmentResponse> upcomingInstallments;
}