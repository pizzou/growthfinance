
package com.patrick.fintech.loan_backend.dto.regulatory;

import lombok.Builder;
import lombok.Data;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


@Data
@Builder
public class BnrSummaryReport {

   

    

    private String taxIdentificationNumber;

    private String contactPhone;

    private String contactEmail;

    private String physicalAddress;

    private String currency;
    private String country;


    // ============================================================
    // BRANCH INFORMATION
    // ============================================================

   

    private String branchCode;

    private String branchAddress;

  
    private long individualBorrowers;

    private long organizationBorrowers;

 
    

    private double overduePrincipal;

    private double interestDue;

  

    private double overdueInterest;

 
    private double totalFeesOutstanding;

    private double par1Ratio;

    

    private double par30Ratio;

    

    private double par60Ratio;

    

    private double par90Ratio;





    private long loansCurrent;

    private long loans1To30DaysPastDue;

    private long loans31To60DaysPastDue;

    private long loans61To90DaysPastDue;

    


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




    private Long organizationId;

    private String organizationName;

    private String bnrInstitutionCode;

    private String registrationNumber;

    private String institutionType;

  

    // ============================================================
    // 2. REPORT
    // ============================================================

    private String reportPeriod;

    private LocalDate periodStart;

    private LocalDate periodEnd;

    private LocalDate reportDate;

    private LocalDateTime generatedAt;

    private String generatedBy;

    private String reportReference;

    // ============================================================
    // 3. BRANCH
    // ============================================================

    private Long branchId;

    private String branchName;

    private long totalBranches;

    // ============================================================
    // 4. LOAN COUNTS
    // ============================================================

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

    // ============================================================
    // 5. DISBURSEMENTS
    // ============================================================

    private Double totalPrincipalDisbursed;

    private Double totalApprovedAmount;

    private Double averageLoanSize;

    private Double largestLoanAmount;

    private Double smallestLoanAmount;

    // ============================================================
    // 6. OUTSTANDING
    // ============================================================

    private Double outstandingPrincipal;

    private Double outstandingInterest;

    private Double outstandingFees;

    private Double totalOutstanding;

    // ============================================================
    // 7. REPAYMENTS
    // ============================================================

    private Double totalPrincipalCollected;

    private Double totalInterestCollected;

    private Double totalFeesCollected;

    private Double totalAmountCollected;

    private Double interestAccruedUnpaid;

    private Double feesAccruedUnpaid;

    private long totalPayments;

    private long missedPayments;

    private long overduePayments;

    // ============================================================
    // 8. PAR
    // ============================================================

    private Double parAmount;

    private Double parRatio;

    private Double par1To30Amount;

    private Double par31To60Amount;

    private Double par61To90Amount;

    private Double par91To180Amount;

    private Double par181To365Amount;

    private Double parOver365Amount;

    // ============================================================
    // 9. NPL
    // ============================================================

    private Double nplAmount;

    private Double nplRatio;

    private long nplLoanCount;

    private long loansOver30Days;

    private long loansOver60Days;

    private long loansOver90Days;

    private long loansOver180Days;

    private long loansOver365Days;

    // ============================================================
    // 10. DEFAULT / WRITE-OFF
    // ============================================================

    private Double defaultedAmount;

    private Double writtenOffAmount;

    private Double recoveriesAfterWriteOff;

    // ============================================================
    // 11. PROVISION
    // ============================================================

    private Double requiredProvision;

    private Double existingProvision;

    private Double provisionShortfall;

    // ============================================================
    // 12. BORROWERS
    // ============================================================

    private long totalBorrowers;

    private long activeBorrowers;

    private long maleBorrowers;

    private long femaleBorrowers;

    private long otherGenderBorrowers;

    private long borrowersWithMultipleLoans;

    // ============================================================
    // 13. DEMOGRAPHICS
    // ============================================================

    private long youthBorrowers;

    private long adultBorrowers;

    private long seniorBorrowers;

    // ============================================================
    // 14. CREDIT BUREAU
    // ============================================================

    private long borrowersCreditChecked;

    private long borrowersWithDefaultHistory;

    private long borrowersWithActiveListing;

    private long borrowersWithMultipleFacilities;

    private Double totalExternalDebt;

    // ============================================================
    // 15. BREAKDOWNS
    // ============================================================

    private List<BnrBreakdownRow> loanTypeBreakdown;

    private List<BnrBreakdownRow> branchBreakdown;

    private List<BnrBreakdownRow> genderBreakdown;

    // ============================================================
    // 16. DATA QUALITY
    // ============================================================

    private long loansMissingBorrower;

    private long borrowersMissingNationalId;

    private long loansMissingBranch;

    private long loansMissingCurrency;

    private long loansMissingRepaymentSchedule;

    private List<String> dataQualityWarnings;

    // ============================================================
    // 17. STATUS
    // ============================================================

    private String reportStatus;

    private String submissionReference;
}
