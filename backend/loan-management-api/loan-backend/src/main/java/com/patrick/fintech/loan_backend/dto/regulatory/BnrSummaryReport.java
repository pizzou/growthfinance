package com.patrick.fintech.loan_backend.dto.regulatory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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

    

    @JsonProperty("totalPrincipalDisbursed")
private BigDecimal totalPrincipalDisbursed;
    
    @JsonProperty("totalApprovedAmount")
private BigDecimal totalApprovedAmount;
    
    @JsonProperty("averageLoanSize")
private BigDecimal averageLoanSize;
    
    @JsonProperty("largestLoanAmount")
private BigDecimal largestLoanAmount;
    
    @JsonProperty("smallestLoanAmount")
private BigDecimal smallestLoanAmount;

    

    @JsonProperty("outstandingPrincipal")
private BigDecimal outstandingPrincipal;
    
    @JsonProperty("outstandingInterest")
private BigDecimal outstandingInterest;
    
    @JsonProperty("outstandingFees")
private BigDecimal outstandingFees;
    
    @JsonProperty("totalOutstanding")
private BigDecimal totalOutstanding;

    

    @JsonProperty("totalPrincipalCollected")
private BigDecimal totalPrincipalCollected;
    
    @JsonProperty("totalInterestCollected")
private BigDecimal totalInterestCollected;
    
    @JsonProperty("totalFeesCollected")
private BigDecimal totalFeesCollected;
    
    @JsonProperty("totalAmountCollected")
private BigDecimal totalAmountCollected;

    

    @JsonProperty("interestAccruedUnpaid")
private BigDecimal interestAccruedUnpaid;
    
    @JsonProperty("feesAccruedUnpaid")
private BigDecimal feesAccruedUnpaid;

    private long totalPayments;
    private long missedPayments;
    private long overduePayments;

    

    @JsonProperty("parAmount")
private BigDecimal parAmount;
    
    @JsonProperty("parRatio")
private BigDecimal parRatio;

    

    @JsonProperty("par1Ratio")
private BigDecimal par1Ratio;
    
    @JsonProperty("par30Ratio")
private BigDecimal par30Ratio;
    
    @JsonProperty("par60Ratio")
private BigDecimal par60Ratio;
    
    @JsonProperty("par90Ratio")
private BigDecimal par90Ratio;

    

    @JsonProperty("par1To30Amount")
private BigDecimal par1To30Amount;
    
    @JsonProperty("par31To60Amount")
private BigDecimal par31To60Amount;
    
    @JsonProperty("par61To90Amount")
private BigDecimal par61To90Amount;
    
    @JsonProperty("par91To180Amount")
private BigDecimal par91To180Amount;
    
    @JsonProperty("par181To365Amount")
private BigDecimal par181To365Amount;
    
    @JsonProperty("parOver365Amount")
private BigDecimal parOver365Amount;

    

    @JsonProperty("nplAmount")
private BigDecimal nplAmount;
    
    @JsonProperty("nplRatio")
private BigDecimal nplRatio;
    private long nplLoanCount;

    private long loansOver30Days;
    private long loansOver60Days;
    private long loansOver90Days;
    private long loansOver180Days;
    private long loansOver365Days;

    

    @JsonProperty("defaultedAmount")
private BigDecimal defaultedAmount;
    
    @JsonProperty("writtenOffAmount")
private BigDecimal writtenOffAmount;
    
    @JsonProperty("recoveriesAfterWriteOff")
private BigDecimal recoveriesAfterWriteOff;

    

    @JsonProperty("requiredProvision")
private BigDecimal requiredProvision;
    
    @JsonProperty("existingProvision")
private BigDecimal existingProvision;
    
    @JsonProperty("provisionShortfall")
private BigDecimal provisionShortfall;

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

    

    @JsonProperty("totalExternalDebt")
private BigDecimal totalExternalDebt;

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

    @Deprecated
    @JsonIgnore
    public Double getTotalPrincipalDisbursed() {
        return totalPrincipalDisbursed == null ? null : totalPrincipalDisbursed.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalPrincipalDisbursedDecimal() {
        return totalPrincipalDisbursed;
    }

    @Deprecated
    public void setTotalPrincipalDisbursed(Double value) {
        this.totalPrincipalDisbursed = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalPrincipalDisbursed(BigDecimal value) {
        this.totalPrincipalDisbursed = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getTotalApprovedAmount() {
        return totalApprovedAmount == null ? null : totalApprovedAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalApprovedAmountDecimal() {
        return totalApprovedAmount;
    }

    @Deprecated
    public void setTotalApprovedAmount(Double value) {
        this.totalApprovedAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalApprovedAmount(BigDecimal value) {
        this.totalApprovedAmount = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getAverageLoanSize() {
        return averageLoanSize == null ? null : averageLoanSize.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getAverageLoanSizeDecimal() {
        return averageLoanSize;
    }

    @Deprecated
    public void setAverageLoanSize(Double value) {
        this.averageLoanSize = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setAverageLoanSize(BigDecimal value) {
        this.averageLoanSize = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getLargestLoanAmount() {
        return largestLoanAmount == null ? null : largestLoanAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getLargestLoanAmountDecimal() {
        return largestLoanAmount;
    }

    @Deprecated
    public void setLargestLoanAmount(Double value) {
        this.largestLoanAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setLargestLoanAmount(BigDecimal value) {
        this.largestLoanAmount = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getSmallestLoanAmount() {
        return smallestLoanAmount == null ? null : smallestLoanAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getSmallestLoanAmountDecimal() {
        return smallestLoanAmount;
    }

    @Deprecated
    public void setSmallestLoanAmount(Double value) {
        this.smallestLoanAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setSmallestLoanAmount(BigDecimal value) {
        this.smallestLoanAmount = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getOutstandingPrincipal() {
        return outstandingPrincipal == null ? null : outstandingPrincipal.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getOutstandingPrincipalDecimal() {
        return outstandingPrincipal;
    }

    @Deprecated
    public void setOutstandingPrincipal(Double value) {
        this.outstandingPrincipal = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setOutstandingPrincipal(BigDecimal value) {
        this.outstandingPrincipal = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getOutstandingInterest() {
        return outstandingInterest == null ? null : outstandingInterest.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getOutstandingInterestDecimal() {
        return outstandingInterest;
    }

    @Deprecated
    public void setOutstandingInterest(Double value) {
        this.outstandingInterest = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setOutstandingInterest(BigDecimal value) {
        this.outstandingInterest = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getOutstandingFees() {
        return outstandingFees == null ? null : outstandingFees.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getOutstandingFeesDecimal() {
        return outstandingFees;
    }

    @Deprecated
    public void setOutstandingFees(Double value) {
        this.outstandingFees = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setOutstandingFees(BigDecimal value) {
        this.outstandingFees = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getTotalOutstanding() {
        return totalOutstanding == null ? null : totalOutstanding.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalOutstandingDecimal() {
        return totalOutstanding;
    }

    @Deprecated
    public void setTotalOutstanding(Double value) {
        this.totalOutstanding = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalOutstanding(BigDecimal value) {
        this.totalOutstanding = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getTotalPrincipalCollected() {
        return totalPrincipalCollected == null ? null : totalPrincipalCollected.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalPrincipalCollectedDecimal() {
        return totalPrincipalCollected;
    }

    @Deprecated
    public void setTotalPrincipalCollected(Double value) {
        this.totalPrincipalCollected = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalPrincipalCollected(BigDecimal value) {
        this.totalPrincipalCollected = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getTotalInterestCollected() {
        return totalInterestCollected == null ? null : totalInterestCollected.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalInterestCollectedDecimal() {
        return totalInterestCollected;
    }

    @Deprecated
    public void setTotalInterestCollected(Double value) {
        this.totalInterestCollected = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalInterestCollected(BigDecimal value) {
        this.totalInterestCollected = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getTotalFeesCollected() {
        return totalFeesCollected == null ? null : totalFeesCollected.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalFeesCollectedDecimal() {
        return totalFeesCollected;
    }

    @Deprecated
    public void setTotalFeesCollected(Double value) {
        this.totalFeesCollected = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalFeesCollected(BigDecimal value) {
        this.totalFeesCollected = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getTotalAmountCollected() {
        return totalAmountCollected == null ? null : totalAmountCollected.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalAmountCollectedDecimal() {
        return totalAmountCollected;
    }

    @Deprecated
    public void setTotalAmountCollected(Double value) {
        this.totalAmountCollected = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalAmountCollected(BigDecimal value) {
        this.totalAmountCollected = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getInterestAccruedUnpaid() {
        return interestAccruedUnpaid == null ? null : interestAccruedUnpaid.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getInterestAccruedUnpaidDecimal() {
        return interestAccruedUnpaid;
    }

    @Deprecated
    public void setInterestAccruedUnpaid(Double value) {
        this.interestAccruedUnpaid = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setInterestAccruedUnpaid(BigDecimal value) {
        this.interestAccruedUnpaid = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getFeesAccruedUnpaid() {
        return feesAccruedUnpaid == null ? null : feesAccruedUnpaid.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getFeesAccruedUnpaidDecimal() {
        return feesAccruedUnpaid;
    }

    @Deprecated
    public void setFeesAccruedUnpaid(Double value) {
        this.feesAccruedUnpaid = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setFeesAccruedUnpaid(BigDecimal value) {
        this.feesAccruedUnpaid = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getParAmount() {
        return parAmount == null ? null : parAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getParAmountDecimal() {
        return parAmount;
    }

    @Deprecated
    public void setParAmount(Double value) {
        this.parAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setParAmount(BigDecimal value) {
        this.parAmount = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getParRatio() {
        return parRatio == null ? null : parRatio.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getParRatioDecimal() {
        return parRatio;
    }

    @Deprecated
    public void setParRatio(Double value) {
        this.parRatio = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setParRatio(BigDecimal value) {
        this.parRatio = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getPar1Ratio() {
        return par1Ratio == null ? null : par1Ratio.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getPar1RatioDecimal() {
        return par1Ratio;
    }

    @Deprecated
    public void setPar1Ratio(Double value) {
        this.par1Ratio = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setPar1Ratio(BigDecimal value) {
        this.par1Ratio = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getPar30Ratio() {
        return par30Ratio == null ? null : par30Ratio.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getPar30RatioDecimal() {
        return par30Ratio;
    }

    @Deprecated
    public void setPar30Ratio(Double value) {
        this.par30Ratio = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setPar30Ratio(BigDecimal value) {
        this.par30Ratio = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getPar60Ratio() {
        return par60Ratio == null ? null : par60Ratio.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getPar60RatioDecimal() {
        return par60Ratio;
    }

    @Deprecated
    public void setPar60Ratio(Double value) {
        this.par60Ratio = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setPar60Ratio(BigDecimal value) {
        this.par60Ratio = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getPar90Ratio() {
        return par90Ratio == null ? null : par90Ratio.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getPar90RatioDecimal() {
        return par90Ratio;
    }

    @Deprecated
    public void setPar90Ratio(Double value) {
        this.par90Ratio = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setPar90Ratio(BigDecimal value) {
        this.par90Ratio = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getPar1To30Amount() {
        return par1To30Amount == null ? null : par1To30Amount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getPar1To30AmountDecimal() {
        return par1To30Amount;
    }

    @Deprecated
    public void setPar1To30Amount(Double value) {
        this.par1To30Amount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setPar1To30Amount(BigDecimal value) {
        this.par1To30Amount = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getPar31To60Amount() {
        return par31To60Amount == null ? null : par31To60Amount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getPar31To60AmountDecimal() {
        return par31To60Amount;
    }

    @Deprecated
    public void setPar31To60Amount(Double value) {
        this.par31To60Amount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setPar31To60Amount(BigDecimal value) {
        this.par31To60Amount = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getPar61To90Amount() {
        return par61To90Amount == null ? null : par61To90Amount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getPar61To90AmountDecimal() {
        return par61To90Amount;
    }

    @Deprecated
    public void setPar61To90Amount(Double value) {
        this.par61To90Amount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setPar61To90Amount(BigDecimal value) {
        this.par61To90Amount = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getPar91To180Amount() {
        return par91To180Amount == null ? null : par91To180Amount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getPar91To180AmountDecimal() {
        return par91To180Amount;
    }

    @Deprecated
    public void setPar91To180Amount(Double value) {
        this.par91To180Amount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setPar91To180Amount(BigDecimal value) {
        this.par91To180Amount = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getPar181To365Amount() {
        return par181To365Amount == null ? null : par181To365Amount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getPar181To365AmountDecimal() {
        return par181To365Amount;
    }

    @Deprecated
    public void setPar181To365Amount(Double value) {
        this.par181To365Amount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setPar181To365Amount(BigDecimal value) {
        this.par181To365Amount = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getParOver365Amount() {
        return parOver365Amount == null ? null : parOver365Amount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getParOver365AmountDecimal() {
        return parOver365Amount;
    }

    @Deprecated
    public void setParOver365Amount(Double value) {
        this.parOver365Amount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setParOver365Amount(BigDecimal value) {
        this.parOver365Amount = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getNplAmount() {
        return nplAmount == null ? null : nplAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getNplAmountDecimal() {
        return nplAmount;
    }

    @Deprecated
    public void setNplAmount(Double value) {
        this.nplAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setNplAmount(BigDecimal value) {
        this.nplAmount = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getNplRatio() {
        return nplRatio == null ? null : nplRatio.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getNplRatioDecimal() {
        return nplRatio;
    }

    @Deprecated
    public void setNplRatio(Double value) {
        this.nplRatio = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setNplRatio(BigDecimal value) {
        this.nplRatio = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getDefaultedAmount() {
        return defaultedAmount == null ? null : defaultedAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getDefaultedAmountDecimal() {
        return defaultedAmount;
    }

    @Deprecated
    public void setDefaultedAmount(Double value) {
        this.defaultedAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setDefaultedAmount(BigDecimal value) {
        this.defaultedAmount = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getWrittenOffAmount() {
        return writtenOffAmount == null ? null : writtenOffAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getWrittenOffAmountDecimal() {
        return writtenOffAmount;
    }

    @Deprecated
    public void setWrittenOffAmount(Double value) {
        this.writtenOffAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setWrittenOffAmount(BigDecimal value) {
        this.writtenOffAmount = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getRecoveriesAfterWriteOff() {
        return recoveriesAfterWriteOff == null ? null : recoveriesAfterWriteOff.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getRecoveriesAfterWriteOffDecimal() {
        return recoveriesAfterWriteOff;
    }

    @Deprecated
    public void setRecoveriesAfterWriteOff(Double value) {
        this.recoveriesAfterWriteOff = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setRecoveriesAfterWriteOff(BigDecimal value) {
        this.recoveriesAfterWriteOff = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getRequiredProvision() {
        return requiredProvision == null ? null : requiredProvision.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getRequiredProvisionDecimal() {
        return requiredProvision;
    }

    @Deprecated
    public void setRequiredProvision(Double value) {
        this.requiredProvision = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setRequiredProvision(BigDecimal value) {
        this.requiredProvision = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getExistingProvision() {
        return existingProvision == null ? null : existingProvision.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getExistingProvisionDecimal() {
        return existingProvision;
    }

    @Deprecated
    public void setExistingProvision(Double value) {
        this.existingProvision = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setExistingProvision(BigDecimal value) {
        this.existingProvision = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getProvisionShortfall() {
        return provisionShortfall == null ? null : provisionShortfall.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getProvisionShortfallDecimal() {
        return provisionShortfall;
    }

    @Deprecated
    public void setProvisionShortfall(Double value) {
        this.provisionShortfall = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setProvisionShortfall(BigDecimal value) {
        this.provisionShortfall = value;
    }

    @Deprecated
    @JsonIgnore
    public Double getTotalExternalDebt() {
        return totalExternalDebt == null ? null : totalExternalDebt.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getTotalExternalDebtDecimal() {
        return totalExternalDebt;
    }

    @Deprecated
    public void setTotalExternalDebt(Double value) {
        this.totalExternalDebt = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setTotalExternalDebt(BigDecimal value) {
        this.totalExternalDebt = value;
    }

    /** Backward-compatible Double builder overloads; state remains BigDecimal. */
    public static class BnrSummaryReportBuilder {
        private BigDecimal averageLoanSize;
        private BigDecimal defaultedAmount;
        private BigDecimal existingProvision;
        private BigDecimal feesAccruedUnpaid;
        private BigDecimal interestAccruedUnpaid;
        private BigDecimal largestLoanAmount;
        private BigDecimal nplAmount;
        private BigDecimal nplRatio;
        private BigDecimal outstandingFees;
        private BigDecimal outstandingInterest;
        private BigDecimal outstandingPrincipal;
        private BigDecimal par181To365Amount;
        private BigDecimal par1Ratio;
        private BigDecimal par1To30Amount;
        private BigDecimal par30Ratio;
        private BigDecimal par31To60Amount;
        private BigDecimal par60Ratio;
        private BigDecimal par61To90Amount;
        private BigDecimal par90Ratio;
        private BigDecimal par91To180Amount;
        private BigDecimal parAmount;
        private BigDecimal parOver365Amount;
        private BigDecimal parRatio;
        private BigDecimal provisionShortfall;
        private BigDecimal recoveriesAfterWriteOff;
        private BigDecimal requiredProvision;
        private BigDecimal smallestLoanAmount;
        private BigDecimal totalAmountCollected;
        private BigDecimal totalApprovedAmount;
        private BigDecimal totalExternalDebt;
        private BigDecimal totalFeesCollected;
        private BigDecimal totalInterestCollected;
        private BigDecimal totalOutstanding;
        private BigDecimal totalPrincipalCollected;
        private BigDecimal totalPrincipalDisbursed;
        private BigDecimal writtenOffAmount;

        public BnrSummaryReportBuilder totalPrincipalDisbursed(Double value) {
            this.totalPrincipalDisbursed = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder totalPrincipalDisbursed(BigDecimal value) {
            this.totalPrincipalDisbursed = value;
            return this;
        }
        public BnrSummaryReportBuilder totalApprovedAmount(Double value) {
            this.totalApprovedAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder totalApprovedAmount(BigDecimal value) {
            this.totalApprovedAmount = value;
            return this;
        }
        public BnrSummaryReportBuilder averageLoanSize(Double value) {
            this.averageLoanSize = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder averageLoanSize(BigDecimal value) {
            this.averageLoanSize = value;
            return this;
        }
        public BnrSummaryReportBuilder largestLoanAmount(Double value) {
            this.largestLoanAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder largestLoanAmount(BigDecimal value) {
            this.largestLoanAmount = value;
            return this;
        }
        public BnrSummaryReportBuilder smallestLoanAmount(Double value) {
            this.smallestLoanAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder smallestLoanAmount(BigDecimal value) {
            this.smallestLoanAmount = value;
            return this;
        }
        public BnrSummaryReportBuilder outstandingPrincipal(Double value) {
            this.outstandingPrincipal = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder outstandingPrincipal(BigDecimal value) {
            this.outstandingPrincipal = value;
            return this;
        }
        public BnrSummaryReportBuilder outstandingInterest(Double value) {
            this.outstandingInterest = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder outstandingInterest(BigDecimal value) {
            this.outstandingInterest = value;
            return this;
        }
        public BnrSummaryReportBuilder outstandingFees(Double value) {
            this.outstandingFees = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder outstandingFees(BigDecimal value) {
            this.outstandingFees = value;
            return this;
        }
        public BnrSummaryReportBuilder totalOutstanding(Double value) {
            this.totalOutstanding = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder totalOutstanding(BigDecimal value) {
            this.totalOutstanding = value;
            return this;
        }
        public BnrSummaryReportBuilder totalPrincipalCollected(Double value) {
            this.totalPrincipalCollected = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder totalPrincipalCollected(BigDecimal value) {
            this.totalPrincipalCollected = value;
            return this;
        }
        public BnrSummaryReportBuilder totalInterestCollected(Double value) {
            this.totalInterestCollected = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder totalInterestCollected(BigDecimal value) {
            this.totalInterestCollected = value;
            return this;
        }
        public BnrSummaryReportBuilder totalFeesCollected(Double value) {
            this.totalFeesCollected = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder totalFeesCollected(BigDecimal value) {
            this.totalFeesCollected = value;
            return this;
        }
        public BnrSummaryReportBuilder totalAmountCollected(Double value) {
            this.totalAmountCollected = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder totalAmountCollected(BigDecimal value) {
            this.totalAmountCollected = value;
            return this;
        }
        public BnrSummaryReportBuilder interestAccruedUnpaid(Double value) {
            this.interestAccruedUnpaid = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder interestAccruedUnpaid(BigDecimal value) {
            this.interestAccruedUnpaid = value;
            return this;
        }
        public BnrSummaryReportBuilder feesAccruedUnpaid(Double value) {
            this.feesAccruedUnpaid = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder feesAccruedUnpaid(BigDecimal value) {
            this.feesAccruedUnpaid = value;
            return this;
        }
        public BnrSummaryReportBuilder parAmount(Double value) {
            this.parAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder parAmount(BigDecimal value) {
            this.parAmount = value;
            return this;
        }
        public BnrSummaryReportBuilder parRatio(Double value) {
            this.parRatio = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder parRatio(BigDecimal value) {
            this.parRatio = value;
            return this;
        }
        public BnrSummaryReportBuilder par1Ratio(Double value) {
            this.par1Ratio = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder par1Ratio(BigDecimal value) {
            this.par1Ratio = value;
            return this;
        }
        public BnrSummaryReportBuilder par30Ratio(Double value) {
            this.par30Ratio = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder par30Ratio(BigDecimal value) {
            this.par30Ratio = value;
            return this;
        }
        public BnrSummaryReportBuilder par60Ratio(Double value) {
            this.par60Ratio = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder par60Ratio(BigDecimal value) {
            this.par60Ratio = value;
            return this;
        }
        public BnrSummaryReportBuilder par90Ratio(Double value) {
            this.par90Ratio = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder par90Ratio(BigDecimal value) {
            this.par90Ratio = value;
            return this;
        }
        public BnrSummaryReportBuilder par1To30Amount(Double value) {
            this.par1To30Amount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder par1To30Amount(BigDecimal value) {
            this.par1To30Amount = value;
            return this;
        }
        public BnrSummaryReportBuilder par31To60Amount(Double value) {
            this.par31To60Amount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder par31To60Amount(BigDecimal value) {
            this.par31To60Amount = value;
            return this;
        }
        public BnrSummaryReportBuilder par61To90Amount(Double value) {
            this.par61To90Amount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder par61To90Amount(BigDecimal value) {
            this.par61To90Amount = value;
            return this;
        }
        public BnrSummaryReportBuilder par91To180Amount(Double value) {
            this.par91To180Amount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder par91To180Amount(BigDecimal value) {
            this.par91To180Amount = value;
            return this;
        }
        public BnrSummaryReportBuilder par181To365Amount(Double value) {
            this.par181To365Amount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder par181To365Amount(BigDecimal value) {
            this.par181To365Amount = value;
            return this;
        }
        public BnrSummaryReportBuilder parOver365Amount(Double value) {
            this.parOver365Amount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder parOver365Amount(BigDecimal value) {
            this.parOver365Amount = value;
            return this;
        }
        public BnrSummaryReportBuilder nplAmount(Double value) {
            this.nplAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder nplAmount(BigDecimal value) {
            this.nplAmount = value;
            return this;
        }
        public BnrSummaryReportBuilder nplRatio(Double value) {
            this.nplRatio = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder nplRatio(BigDecimal value) {
            this.nplRatio = value;
            return this;
        }
        public BnrSummaryReportBuilder defaultedAmount(Double value) {
            this.defaultedAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder defaultedAmount(BigDecimal value) {
            this.defaultedAmount = value;
            return this;
        }
        public BnrSummaryReportBuilder writtenOffAmount(Double value) {
            this.writtenOffAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder writtenOffAmount(BigDecimal value) {
            this.writtenOffAmount = value;
            return this;
        }
        public BnrSummaryReportBuilder recoveriesAfterWriteOff(Double value) {
            this.recoveriesAfterWriteOff = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder recoveriesAfterWriteOff(BigDecimal value) {
            this.recoveriesAfterWriteOff = value;
            return this;
        }
        public BnrSummaryReportBuilder requiredProvision(Double value) {
            this.requiredProvision = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder requiredProvision(BigDecimal value) {
            this.requiredProvision = value;
            return this;
        }
        public BnrSummaryReportBuilder existingProvision(Double value) {
            this.existingProvision = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder existingProvision(BigDecimal value) {
            this.existingProvision = value;
            return this;
        }
        public BnrSummaryReportBuilder provisionShortfall(Double value) {
            this.provisionShortfall = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder provisionShortfall(BigDecimal value) {
            this.provisionShortfall = value;
            return this;
        }
        public BnrSummaryReportBuilder totalExternalDebt(Double value) {
            this.totalExternalDebt = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BnrSummaryReportBuilder totalExternalDebt(BigDecimal value) {
            this.totalExternalDebt = value;
            return this;
        }
    }
}
