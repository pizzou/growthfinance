package com.patrick.fintech.loan_backend.dto.regulatory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BnrSummaryReport {

    private Long organizationId;
    private String organizationName;
    private String bnrInstitutionCode;
    private String registrationNumber;
    private String institutionType;
    private String country;
    private String currency;

    private String reportPeriod;

    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDate reportDate;

    private LocalDateTime generatedAt;

    private String generatedBy;
    private String reportReference;

    private Long branchId;
    private String branchName;

    private long totalLoans;
    private long loansDisbursedDuringPeriod;

    private long activeLoans;
    private long closedLoans;
    private long paidLoans;
    private long pendingLoans;
    private long approvedLoans;
    private long rejectedLoans;
    private long cancelledLoans;
    private long overdueLoans;
    private long defaultedLoans;
    private long writtenOffLoans;
    private long restructuredLoans;

    private double totalPrincipalDisbursed;
    private double totalApprovedAmount;
    private double averageLoanSize;
    private double largestLoanAmount;
    private double smallestLoanAmount;

    private double outstandingPrincipal;
    private double outstandingInterest;
    private double outstandingFees;
    private double totalOutstanding;

    private double totalPrincipalCollected;
    private double totalInterestCollected;
    private double totalFeesCollected;
    private double totalAmountCollected;

    private double interestAccruedUnpaid;
    private double feesAccruedUnpaid;

    private long totalPayments;
    private long missedPayments;
    private long overduePayments;

    private double parAmount;
    private double parRatio;

    private double par1Ratio;
    private double par30Ratio;
    private double par60Ratio;
    private double par90Ratio;

    private double par1To30Amount;
    private double par31To60Amount;
    private double par61To90Amount;
    private double par91To180Amount;
    private double par181To365Amount;
    private double parOver365Amount;

    private double nplAmount;
    private double nplRatio;
    private long nplLoanCount;

    private long loansOver30Days;
    private long loansOver60Days;
    private long loansOver90Days;
    private long loansOver180Days;
    private long loansOver365Days;

    private double defaultedAmount;
    private double writtenOffAmount;
    private double recoveriesAfterWriteOff;

    private double requiredProvision;
    private double existingProvision;
    private double provisionShortfall;

    private long totalBorrowers;
    private long activeBorrowers;
    private long maleBorrowers;
    private long femaleBorrowers;
    private long otherGenderBorrowers;

    private long borrowersWithMultipleLoans;

    private long youthBorrowers;
    private long adultBorrowers;
    private long seniorBorrowers;

    private long borrowersCreditChecked;
    private long borrowersWithDefaultHistory;
    private long borrowersWithActiveListing;
    private long borrowersWithMultipleFacilities;

    private double totalExternalDebt;

    @Builder.Default
    private List<BnrBreakdownRow> loanTypeBreakdown =
            new ArrayList<>();

    @Builder.Default
    private List<BnrBreakdownRow> branchBreakdown =
            new ArrayList<>();

    @Builder.Default
    private List<BnrBreakdownRow> genderBreakdown =
            new ArrayList<>();

    private long loansMissingBorrower;
    private long borrowersMissingNationalId;
    private long loansMissingBranch;
    private long loansMissingCurrency;
    private long loansMissingRepaymentSchedule;

    @Builder.Default
    private List<String> dataQualityWarnings =
            new ArrayList<>();

    private String reportStatus;

    private String submissionReference;
}