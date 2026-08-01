package com.patrick.fintech.loan_backend.dto.publicportal;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BorrowerDashboardResponse {

    private Long loanId;
    private String referenceNumber;

    private String borrowerName;

    private String status;
    private String loanType;

    private Double principal;
    private Double outstandingBalance;
    private Double totalPaid;
    private Double totalRepayable;

    private Double nextInstallmentAmount;

    private LocalDate nextPaymentDate;
    private LocalDate nextDueDate;
    private LocalDate maturityDate;

    private Integer missedInstallments;
    private Integer daysOverdue;

    private Double interestRate;

    private String currency;

    private String loanOfficer;

    private Integer activeLoans;
    private Integer overdueLoans;
    private Integer completedLoans;

    private Integer daysUntilDue;
    private Double repaymentProgress;

private List<PaymentHistoryResponse> recentPayments;

private List<UpcomingInstallmentResponse> upcomingInstallments;

private List<String> availablePaymentMethods;
}