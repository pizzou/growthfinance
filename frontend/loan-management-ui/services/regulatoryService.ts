// ============================================================
// REGULATORY REPORTING SERVICE
// ============================================================
//
// Frontend service for:
//
// - BNR portfolio summary
// - BNR financial statement
// - BNR loan-type breakdown
// - BNR branch breakdown
// - BNR gender breakdown
// - BNR exports
// - Credit-bureau reporting
//
// IMPORTANT:
// This file intentionally does NOT use generic type arguments
// on ApiClient because the project's ApiClient is non-generic.
// ============================================================

import api from '@/services/api';


// ============================================================
// TYPES
// ============================================================

export type RegulatoryPeriod =
  | 'DAILY'
  | 'WEEKLY'
  | 'MONTHLY'
  | 'QUARTERLY'
  | 'YEARLY'
  | 'CUSTOM';


export type ExportFormat =
  | 'pdf'
  | 'xlsx'
  | 'csv';


// ============================================================
// REPORT PARAMETERS
// ============================================================

export interface BnrReportParams {
  branchId?: number;
  period?: RegulatoryPeriod;
  from?: string;
  to?: string;
}


// ============================================================
// API QUERY PARAMETERS
// ============================================================
//
// This is deliberately compatible with clients expecting:
//
// Record<string, unknown>
//
// BnrReportParams itself does not need an index signature.
// ============================================================

type QueryParams =
  Record<string, unknown>;


// ============================================================
// BREAKDOWN
// ============================================================

export interface BreakdownRow {
  label: string;
  count: number;
  amount: number;
}


// Backwards-compatible alias.
//
// Some older pages may still import BnrBreakdownRow.
// Keep it exported so those pages do not break.
//

export type BnrBreakdownRow =
  BreakdownRow;


// ============================================================
// BNR SUMMARY
// ============================================================

export interface BnrSummary {

  organizationId?: number;

  organizationName?: string;

  bnrInstitutionCode?: string;

  registrationNumber?: string;

  institutionType?: string;

  country?: string;

  currency?: string;


  // ----------------------------------------------------------
  // PERIOD
  // ----------------------------------------------------------

  reportPeriod?: string;

  periodStart?: string;

  periodEnd?: string;

  reportDate?: string;

  generatedAt?: string;

  generatedBy?: string;

  reportReference?: string;


  // ----------------------------------------------------------
  // BRANCH
  // ----------------------------------------------------------

  branchId?: number;

  branchName?: string;


  // ----------------------------------------------------------
  // LOANS
  // ----------------------------------------------------------

  totalLoans?: number;

  loansDisbursedDuringPeriod?: number;

  activeLoans?: number;

  closedLoans?: number;

  paidLoans?: number;

  pendingLoans?: number;

  approvedLoans?: number;

  rejectedLoans?: number;

  cancelledLoans?: number;

  overdueLoans?: number;

  defaultedLoans?: number;

  writtenOffLoans?: number;

  restructuredLoans?: number;


  // ----------------------------------------------------------
  // DISBURSEMENTS
  // ----------------------------------------------------------

  totalPrincipalDisbursed?: number;

  totalApprovedAmount?: number;

  averageLoanSize?: number;

  largestLoanAmount?: number;

  smallestLoanAmount?: number;


  // ----------------------------------------------------------
  // OUTSTANDING
  // ----------------------------------------------------------

  outstandingPrincipal?: number;

  outstandingInterest?: number;

  outstandingFees?: number;

  totalOutstanding?: number;


  // ----------------------------------------------------------
  // REPAYMENTS
  // ----------------------------------------------------------

  totalPrincipalCollected?: number;

  totalInterestCollected?: number;

  totalFeesCollected?: number;

  totalAmountCollected?: number;

  interestAccruedUnpaid?: number;

  feesAccruedUnpaid?: number;

  totalPayments?: number;

  missedPayments?: number;

  overduePayments?: number;


  // ----------------------------------------------------------
  // PAR
  // ----------------------------------------------------------

  parAmount?: number;

  parRatio?: number;

  par1Ratio?: number;

  par30Ratio?: number;

  par60Ratio?: number;

  par90Ratio?: number;

  par1To30Amount?: number;

  par31To60Amount?: number;

  par61To90Amount?: number;

  par91To180Amount?: number;

  par181To365Amount?: number;

  parOver365Amount?: number;


  // ----------------------------------------------------------
  // NPL
  // ----------------------------------------------------------

  nplAmount?: number;

  nplRatio?: number;

  nplLoanCount?: number;

  loansOver30Days?: number;

  loansOver60Days?: number;

  loansOver90Days?: number;

  loansOver180Days?: number;

  loansOver365Days?: number;


  // ----------------------------------------------------------
  // DEFAULT / WRITE OFF
  // ----------------------------------------------------------

  defaultedAmount?: number;

  writtenOffAmount?: number;

  recoveriesAfterWriteOff?: number;


  // ----------------------------------------------------------
  // PROVISION
  // ----------------------------------------------------------

  requiredProvision?: number;

  existingProvision?: number;

  provisionShortfall?: number;


  // ----------------------------------------------------------
  // BORROWERS
  // ----------------------------------------------------------

  totalBorrowers?: number;

  activeBorrowers?: number;

  maleBorrowers?: number;

  femaleBorrowers?: number;

  otherGenderBorrowers?: number;

  borrowersWithMultipleLoans?: number;


  // ----------------------------------------------------------
  // FINANCIAL INCLUSION
  // ----------------------------------------------------------

  youthBorrowers?: number;

  adultBorrowers?: number;

  seniorBorrowers?: number;


  // ----------------------------------------------------------
  // CREDIT
  // ----------------------------------------------------------

  borrowersCreditChecked?: number;

  borrowersWithDefaultHistory?: number;

  borrowersWithActiveListing?: number;

  borrowersWithMultipleFacilities?: number;

  totalExternalDebt?: number;


  // ----------------------------------------------------------
  // BREAKDOWNS
  // ----------------------------------------------------------

  loanTypeBreakdown?: BreakdownRow[];

  branchBreakdown?: BreakdownRow[];

  genderBreakdown?: BreakdownRow[];


  // ----------------------------------------------------------
  // DATA QUALITY
  // ----------------------------------------------------------

  loansMissingBorrower?: number;

  borrowersMissingNationalId?: number;

  loansMissingBranch?: number;

  loansMissingCurrency?: number;

  loansMissingRepaymentSchedule?: number;

  dataQualityWarnings?: string[];


  // ----------------------------------------------------------
  // STATUS
  // ----------------------------------------------------------

  reportStatus?: string;

  submissionReference?: string | null;
}


// ============================================================
// FINANCIAL STATEMENT
// ============================================================

export interface FinancialStatementRow {

  code?: string;

  name?: string;

  balance?: number;

  debit?: number;

  credit?: number;

  amount?: number;

  [key: string]: unknown;
}


export interface BnrFinancialStatementReport {

  organizationId?: number;

  organizationName?: string;

  bnrInstitutionCode?: string;

  branchId?: number;

  branchName?: string;

  currency?: string;


  reportPeriod?: string;

  periodStart?: string;

  periodEnd?: string;

  generatedAt?: string;


  // ----------------------------------------------------------
  // BALANCE SHEET
  // ----------------------------------------------------------

  assets?: FinancialStatementRow[];

  liabilities?: FinancialStatementRow[];

  equity?: FinancialStatementRow[];

  totalAssets?: number;

  totalLiabilities?: number;

  totalEquity?: number;

  currentPeriodNetIncome?: number;

  balanceSheetBalanced?: boolean;


  // ----------------------------------------------------------
  // PROFIT AND LOSS
  // ----------------------------------------------------------

  income?: FinancialStatementRow[];

  expenses?: FinancialStatementRow[];

  totalIncome?: number;

  totalExpenses?: number;

  netIncome?: number;


  // ----------------------------------------------------------
  // CASH FLOW
  // ----------------------------------------------------------

  cashUsedForLending?: number;

  cashFromCollections?: number;

  cashFromFees?: number;

  otherCashMovement?: number;

  netChangeInCash?: number;


  // ----------------------------------------------------------
  // TRIAL BALANCE
  // ----------------------------------------------------------

  trialBalanceDebit?: number;

  trialBalanceCredit?: number;

  trialBalanceBalanced?: boolean;
}


// Backwards-compatible name.
//
// Older frontend code may import BnrFinancialStatement.
//

export type BnrFinancialStatement =
  BnrFinancialStatementReport;


// ============================================================
// CREDIT BUREAU RECORD
// ============================================================

export interface CreditRecord {

  borrowerId?: number;

  fullName?: string;

  nationalId?: string;

  dateOfBirth?: string;

  gender?: string;

  phone?: string;


  loanNumber?: string;

  loanType?: string;

  loanStatus?: string;


  loanAmount?: number;

  outstandingBalance?: number;

  daysPastDue?: number;

  creditScore?: number;


  dateOpened?: string;

  lastPaymentDate?: string;

  maturityDate?: string;

  dateClosed?: string;


  branchName?: string;

  currency?: string;
}


// Backwards-compatible name.

export type CreditBureauRecord =
  CreditRecord;


// ============================================================
// API RESPONSE HELPERS
// ============================================================

interface ApiEnvelope<T> {

  data?: T;

  message?: string;

  success?: boolean;
}


// ============================================================
// API CLIENT RESPONSE NORMALIZATION
// ============================================================
//
// The project's API client returns untyped values. These helpers
// safely normalize the response without using:
//
// ApiClient<T>
//
// which was the source of the TS2558 errors.
// ============================================================

function unwrap<T>(
  response: unknown
): T {

  if (
    response &&
    typeof response === 'object'
  ) {

    const value =
      response as ApiEnvelope<T>;

    if (
      value.data !== undefined
    ) {

      return value.data as T;
    }
  }

  return response as T;
}


// ============================================================
// QUERY PARAMETER BUILDER
// ============================================================

function toQueryParams(
  params?: BnrReportParams
): QueryParams {

  if (!params) {
    return {};
  }

  const query: QueryParams = {};

  if (
    params.branchId !== undefined &&
    params.branchId !== null
  ) {

    query.branchId =
      params.branchId;
  }

  if (
    params.period !== undefined &&
    params.period !== null
  ) {

    query.period =
      params.period;
  }

  if (
    params.from !== undefined &&
    params.from !== ''
  ) {

    query.from =
      params.from;
  }

  if (
    params.to !== undefined &&
    params.to !== ''
  ) {

    query.to =
      params.to;
  }

  return query;
}


// ============================================================
// DOWNLOAD HELPER
// ============================================================

function triggerDownload(
  blob: Blob,
  filename: string
): void {

  const url =
    window.URL.createObjectURL(blob);

  const anchor =
    document.createElement('a');

  anchor.href =
    url;

  anchor.download =
    filename;

  document.body.appendChild(
    anchor
  );

  anchor.click();

  anchor.remove();

  window.URL.revokeObjectURL(
    url
  );
}


// ============================================================
// ERROR MESSAGE
// ============================================================

function extractErrorMessage(
  error: unknown,
  fallback: string
): string {

  if (
    error &&
    typeof error === 'object'
  ) {

    const value =
      error as {
        response?: {
          data?: {
            message?: string;
          };
        };

        message?: string;
      };


    const apiMessage =
      value.response
        ?.data
        ?.message;


    if (
      apiMessage &&
      typeof apiMessage === 'string'
    ) {

      return apiMessage;
    }


    if (
      value.message &&
      typeof value.message === 'string'
    ) {

      return value.message;
    }
  }


  if (
    typeof error === 'string'
  ) {

    return error;
  }


  return fallback;
}


// ============================================================
// API
// ============================================================

export const regulatoryApi = {

  // ==========================================================
  // BNR SUMMARY
  // ==========================================================

  async bnrSummary(
    params?: BnrReportParams
  ): Promise<BnrSummary> {

    const response =
      await api.get(
        '/regulatory/bnr/summary',
        {
          params:
            toQueryParams(params),
        }
      );

    return unwrap<BnrSummary>(
      response
    );
  },


  // ==========================================================
  // BNR FINANCIAL STATEMENT
  // ==========================================================

  async bnrFinancialStatement(
    params?: BnrReportParams
  ): Promise<BnrFinancialStatementReport> {

    const response =
      await api.get(
        '/regulatory/bnr/financial-statement',
        {
          params:
            toQueryParams(params),
        }
      );

    return unwrap<BnrFinancialStatementReport>(
      response
    );
  },


  // ==========================================================
  // BNR LOAN TYPE
  // ==========================================================

  async bnrByLoanType(
    params?: BnrReportParams
  ): Promise<BreakdownRow[]> {

    const response =
      await api.get(
        '/regulatory/bnr/breakdown/loan-type',
        {
          params:
            toQueryParams(params),
        }
      );

    return unwrap<BreakdownRow[]>(
      response
    );
  },


  // ==========================================================
  // BNR BRANCH
  // ==========================================================

  async bnrByBranch(
    params?: BnrReportParams
  ): Promise<BreakdownRow[]> {

    const response =
      await api.get(
        '/regulatory/bnr/breakdown/branch',
        {
          params:
            toQueryParams(params),
        }
      );

    return unwrap<BreakdownRow[]>(
      response
    );
  },


  // ==========================================================
  // BNR GENDER
  // ==========================================================

  async bnrByGender(
    params?: BnrReportParams
  ): Promise<BreakdownRow[]> {

    const response =
      await api.get(
        '/regulatory/bnr/breakdown/gender',
        {
          params:
            toQueryParams(params),
        }
      );

    return unwrap<BreakdownRow[]>(
      response
    );
  },


  // ==========================================================
  // CREDIT BUREAU
  // ==========================================================

  async creditBureau(
    params?: BnrReportParams
  ): Promise<CreditRecord[]> {

    const response =
      await api.get(
        '/regulatory/credit-bureau',
        {
          params:
            toQueryParams(params),
        }
      );

    return unwrap<CreditRecord[]>(
      response
    );
  },


  // ==========================================================
  // CREDIT BUREAU ALIAS
  // ==========================================================

  async creditBureauReport(
    params?: BnrReportParams
  ): Promise<CreditRecord[]> {

    return this.creditBureau(
      params
    );
  },


  // ==========================================================
  // BNR EXPORT
  // ==========================================================

  // ============================================================
// BNR EXPORT
// ============================================================

async bnrExport(
  format: ExportFormat,
  params?: BnrReportParams
): Promise<void> {

  const normalizedFormat =
    format.toLowerCase() as ExportFormat;

  const response =
    await api.get(
      '/regulatory/bnr/financial-statement/export',
      {
        params: {
          ...toQueryParams(params),
          format: normalizedFormat,
        },

        responseType: 'blob',
      }
    );

  // Axios returns AxiosResponse<Blob>.
  // The actual file is inside response.data.
  const blob =
    response.data instanceof Blob
      ? response.data
      : new Blob(
          [response.data],
          {
            type:
              normalizedFormat === 'pdf'
                ? 'application/pdf'
                : normalizedFormat === 'csv'
                  ? 'text/csv'
                  : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          }
        );

  const filename =
    `BNR-Financial-Statement-${new Date()
      .toISOString()
      .slice(0, 10)}.${normalizedFormat}`;

  triggerDownload(
    blob,
    filename
  );
},


  // ==========================================================
  // ERROR MESSAGE
  // ==========================================================

  getErrorMessage(
    error: unknown,
    fallback =
      'An error occurred while loading the regulatory report.'
  ): string {

    return extractErrorMessage(
      error,
      fallback
    );
  },
};




export default regulatoryApi;