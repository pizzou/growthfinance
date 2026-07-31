package com.patrick.fintech.loan_backend.dto;

import com.patrick.fintech.loan_backend.model.Loan;
import lombok.*;
import java.util.List;
import java.util.Map;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DashboardStats {
    private long   totalLoans;
    private long   pendingLoans;
    private long   activeLoans;
    private long   overdueLoans;
    private long   completedLoans;
    private long   defaultedLoans;
    private double totalDisbursed;
    private double totalCollected;
    private double outstandingBalance;
    private double collectedThisMonth;
    private long   totalBorrowers;
    private long   latePaymentsCount;
    private double portfolioAtRiskPct;
    private List<Loan>              recentLoans;
    private List<Map<String,Object>> loanTypeBreakdown;
}
