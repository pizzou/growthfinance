// ============================================================
// REGULATORY REPORTING SERVICE
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

type QueryParams = Record<string, unknown>;


// ============================================================
// BREAKDOWN
// ============================================================

export interface BreakdownRow {
  label: string;
  count: number;
  amount: number;
}

export type BnrBreakdownRow = BreakdownRow;


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

  reportPeriod?: string;
  periodStart?: string;
  periodEnd?: string;
  reportDate?: string;
  generatedAt?: string;
  generatedBy?: string;
  reportReference?: string;

  branchId?: number;
  branchName?: string;

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

  totalPrincipalDisbursed?: number;
  totalApprovedAmount?: number;
  averageLoanSize?: number;
  largestLoanAmount?: number;
  smallestLoanAmount?: number;

  outstandingPrincipal?: number;
  outstandingInterest?: number;
  outstandingFees?: number;
  totalOutstanding?: number;

  totalPrincipalCollected?: number;
  totalInterestCollected?: number;
  totalFeesCollected?: number;
  totalAmountCollected?: number;
  interestAccruedUnpaid?: number;
  feesAccruedUnpaid?: number;
  totalPayments?: number;
  missedPayments?: number;
  overduePayments?: number;

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

  nplAmount?: number;
  nplRatio?: number;
  nplLoanCount?: number;

  loansOver30Days?: number;
  loansOver60Days?: number;
  loansOver90Days?: number;
  loansOver180Days?: number;
  loansOver365Days?: number;

  defaultedAmount?: number;
  writtenOffAmount?: number;
  recoveriesAfterWriteOff?: number;

  requiredProvision?: number;
  existingProvision?: number;
  provisionShortfall?: number;

  totalBorrowers?: number;
  activeBorrowers?: number;
  maleBorrowers?: number;
  femaleBorrowers?: number;
  otherGenderBorrowers?: number;
  borrowersWithMultipleLoans?: number;

  youthBorrowers?: number;
  adultBorrowers?: number;
  seniorBorrowers?: number;

  borrowersCreditChecked?: number;
  borrowersWithDefaultHistory?: number;
  borrowersWithActiveListing?: number;
  borrowersWithMultipleFacilities?: number;
  totalExternalDebt?: number;

  loanTypeBreakdown?: BreakdownRow[];
  branchBreakdown?: BreakdownRow[];
  genderBreakdown?: BreakdownRow[];

  loansMissingBorrower?: number;
  borrowersMissingNationalId?: number;
  loansMissingBranch?: number;
  loansMissingCurrency?: number;
  loansMissingRepaymentSchedule?: number;

  dataQualityWarnings?: string[];

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

  assets?: FinancialStatementRow[];
  liabilities?: FinancialStatementRow[];
  equity?: FinancialStatementRow[];

  totalAssets?: number;
  totalLiabilities?: number;
  totalEquity?: number;
  currentPeriodNetIncome?: number;
  balanceSheetBalanced?: boolean;

  income?: FinancialStatementRow[];
  expenses?: FinancialStatementRow[];

  totalIncome?: number;
  totalExpenses?: number;
  netIncome?: number;

  cashUsedForLending?: number;
  cashFromCollections?: number;
  cashFromFees?: number;
  otherCashMovement?: number;
  netChangeInCash?: number;

  trialBalanceDebit?: number;
  trialBalanceCredit?: number;
  trialBalanceBalanced?: boolean;
}


export type BnrFinancialStatement =
  BnrFinancialStatementReport;


// ============================================================
// CREDIT BUREAU
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

export type CreditBureauRecord = CreditRecord;


// ============================================================
// RESPONSE HELPERS
// ============================================================

interface ApiEnvelope<T> {
  data?: T;
  message?: string;
  success?: boolean;
  content?: T;
}


// ============================================================
// NORMALIZE SINGLE OBJECT
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

    if (
      value.content !== undefined
    ) {
      return value.content as T;
    }
  }

  return response as T;
}


// ============================================================
// NORMALIZE ARRAY
//
// IMPORTANT:
// This prevents:
// TypeError: a.map is not a function
//
// It accepts:
// []
// { data: [] }
// { content: [] }
// { data: { content: [] } }
// { items: [] }
// { results: [] }
// ============================================================

function unwrapArray<T>(
  response: unknown
): T[] {

  if (Array.isArray(response)) {
    return response;
  }

  if (
    response &&
    typeof response === 'object'
  ) {

    const value =
      response as {
        data?: unknown;
        content?: unknown;
        items?: unknown;
        results?: unknown;
      };


    if (Array.isArray(value.data)) {
      return value.data as T[];
    }


    if (Array.isArray(value.content)) {
      return value.content as T[];
    }


    if (Array.isArray(value.items)) {
      return value.items as T[];
    }


    if (Array.isArray(value.results)) {
      return value.results as T[];
    }


    if (
      value.data &&
      typeof value.data === 'object'
    ) {

      const nested =
        value.data as {
          content?: unknown;
          items?: unknown;
          results?: unknown;
        };


      if (Array.isArray(nested.content)) {
        return nested.content as T[];
      }


      if (Array.isArray(nested.items)) {
        return nested.items as T[];
      }


      if (Array.isArray(nested.results)) {
        return nested.results as T[];
      }
    }
  }

  return [];
}


// ============================================================
// QUERY PARAMETERS
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
// DOWNLOAD
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
// ERROR
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
  // FINANCIAL STATEMENT
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
  // LOAN TYPE
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

    return unwrapArray<BreakdownRow>(
      response
    );
  },


  // ==========================================================
  // BRANCH
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

    return unwrapArray<BreakdownRow>(
      response
    );
  },


  // ==========================================================
  // GENDER
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

    return unwrapArray<BreakdownRow>(
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

    return unwrapArray<CreditRecord>(
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
  // ERROR
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