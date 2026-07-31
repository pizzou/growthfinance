package com.patrick.fintech.loan_backend.dto.regulatory;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The BNR "Loan Portfolio Summary" report — the first thing a regulator asks for.
 * Covers loan counts, principal/interest figures, gender split, and portfolio risk,
 * scoped to an organization and (optionally) a branch and reporting period.
 */
@Data @Builder
public class BnrSummaryReport {
    private Long   organizationId;
    private String organizationName;
    private String bnrInstitutionCode;   // organization.registrationNumber, if set
    private Long   branchId;
    private String branchName;

    private String reportPeriod;         // DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY, CUSTOM
    private LocalDate periodStart;
    private LocalDate periodEnd;

    // Loan counts
    private long totalLoansIssued;
    private long activeLoans;
    private long closedLoans;
    private long pendingLoans;
    private long rejectedLoans;
    private long overdueLoans;
    private long defaultedLoans;

    // Principal & interest
    private double totalPrincipalDisbursed;
    private double outstandingPrincipal;
    private double totalInterestCollected;
    private double interestAccruedUnpaid;
    private double totalProcessingFees;

    // Gender / financial inclusion
    private long maleBorrowers;
    private long femaleBorrowers;
    private long otherGenderBorrowers;

    // Portfolio risk
    private double parAmount;            // total principal past due, any bucket
    private double parRatio;             // parAmount / outstandingPrincipal, as a fraction
    private double nplAmount;            // outstanding balance of loans >90 days overdue / DEFAULTED / WRITTEN_OFF
    private double nplRatio;

    private String currency;
    private LocalDateTime generatedAt;
}