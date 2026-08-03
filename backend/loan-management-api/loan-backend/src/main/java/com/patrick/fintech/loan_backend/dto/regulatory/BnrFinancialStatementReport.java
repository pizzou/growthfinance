package com.patrick.fintech.loan_backend.dto.regulatory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BnrFinancialStatementReport {

    // ============================================================
    // REPORT INFORMATION
    // ============================================================

    private Long organizationId;

    private String organizationName;

    private String bnrInstitutionCode;

    private Long branchId;

    private String branchName;

    private String currency;

    private String reportPeriod;

    private LocalDate periodStart;

    private LocalDate periodEnd;

    private LocalDateTime generatedAt;


    // ============================================================
    // STATEMENT OF FINANCIAL POSITION
    // ============================================================

    @Builder.Default
    private List<Map<String, Object>> assets =
            new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> liabilities =
            new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> equity =
            new ArrayList<>();

    private double totalAssets;

    private double totalLiabilities;

    private double totalEquity;

    private double currentPeriodNetIncome;

    private boolean balanceSheetBalanced;


    // ============================================================
    // INCOME STATEMENT
    // ============================================================

    @Builder.Default
    private List<Map<String, Object>> income =
            new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> expenses =
            new ArrayList<>();

    private double totalIncome;

    private double totalExpenses;

    private double netIncome;


    // ============================================================
    // TRIAL BALANCE
    // ============================================================

    private double trialBalanceDebit;

    private double trialBalanceCredit;

    private boolean trialBalanceBalanced;


    // ============================================================
    // CASH FLOW
    // ============================================================

    private double cashUsedForLending;

    private double cashFromCollections;

    private double cashFromFees;

    private double otherCashMovement;

    private double netChangeInCash;
}