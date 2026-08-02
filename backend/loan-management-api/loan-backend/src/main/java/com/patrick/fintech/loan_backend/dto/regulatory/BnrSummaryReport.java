
package com.patrick.fintech.loan_backend.dto.regulatory;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Data
@Builder
public class BnrSummaryReport {

   

    private Long organizationId;

    private String organizationName;

    
    private String bnrInstitutionCode;

    private String institutionType;

    private String registrationNumber;

    private String taxIdentificationNumber;

    private String contactPhone;

    private String contactEmail;

    private String physicalAddress;

    private String currency;


    // ============================================================
    // BRANCH INFORMATION
    // ============================================================

    private Long branchId;

    private String branchName;

    private String branchCode;

    private String branchAddress;

    private String reportPeriod;

    private LocalDate periodStart;

    private LocalDate periodEnd;

    private LocalDateTime generatedAt;

    private long totalLoansIssued;

    private long activeLoans;

    private long disbursedLoans;

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

    private long totalBorrowers;

    private long maleBorrowers;

    private long femaleBorrowers;

    private long otherGenderBorrowers;

    private long individualBorrowers;

    private long organizationBorrowers;

    private double totalPrincipalDisbursed;

    private double totalLoanAmount;

    private double outstandingPrincipal;

   
    private double principalCollected;

    private double overduePrincipal;

    private double defaultedPrincipal;

  
    private double writtenOffPrincipal;

    private double totalInterestCollected;

    private double interestAccruedUnpaid;

    private double interestDue;

    private double interestPaid;

    private double overdueInterest;

    private double totalProcessingFees;

    private double totalFeesCollected;

    private double totalFeesOutstanding;

    private double parAmount;

    private double parRatio;

    private double par1Amount;

    private double par1Ratio;

    private double par30Amount;

    private double par30Ratio;

    private double par60Amount;

    private double par60Ratio;

    private double par90Amount;

    private double par90Ratio;


    private double nplAmount;

    private double nplRatio;

    private long nplLoanCount;



    private long loansCurrent;

    private long loans1To30DaysPastDue;

    private long loans31To60DaysPastDue;

    private long loans61To90DaysPastDue;

    private long loansOver90DaysPastDue;


    private long personalLoans;

    private long businessLoans;

    private long mortgageLoans;

    private long autoLoans;

    private long studentLoans;

    private long emergencyLoans;

    private long assetFinanceLoans;

    private long salaryAdvanceLoans;

    private long microfinanceLoans;

    private long agriculturalLoans;

    private long tradeFinanceLoans;

    private long groupLoans;


    private double personalLoanAmount;

    private double businessLoanAmount;

    private double mortgageLoanAmount;

    private double autoLoanAmount;

    private double studentLoanAmount;

    private double emergencyLoanAmount;

    private double assetFinanceLoanAmount;

    private double salaryAdvanceLoanAmount;

    private double microfinanceLoanAmount;

    private double agriculturalLoanAmount;

    private double tradeFinanceLoanAmount;

    private double groupLoanAmount;


    // ============================================================
    // RESTRUCTURING / RECOVERY
    // ============================================================

    private double restructuredLoanAmount;

    private double recoveredAmount;

    private double recoveryRate;

    private double writtenOffAmount;


    // ============================================================
    // FINANCIAL INCLUSION
    // ============================================================

    private long femaleLoanCount;

    private double femaleLoanAmount;

    private long maleLoanCount;

    private double maleLoanAmount;

    private long otherGenderLoanCount;

    private double otherGenderLoanAmount;


    // ============================================================
    // DATA QUALITY / REPORT STATUS
    // ============================================================

    /**
     * Number of records included in the report.
     */
    private long recordsIncluded;

    /**
     * Number of records excluded because of missing/incomplete
     * regulatory information.
     */
    private long recordsWithMissingData;

    /**
     * Indicates whether the report passed internal validation.
     */
    private boolean validated;

    /**
     * Human-readable validation information.
     */
    private String validationMessage;
}
