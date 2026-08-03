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
// API ENVELOPE
// ============================================================

interface ApiEnvelope<T = unknown> {
  success?: boolean;
  message?: string;
  data?: T;
  content?: T;
}


// ============================================================
// AXIOS RESPONSE-LIKE TYPE
//
// We intentionally don't import AxiosResponse here so this
// service remains compatible with the existing api wrapper.
// ============================================================

interface ApiResponseLike<T = unknown> {
  data?: T;
  status?: number;
  statusText?: string;
  headers?: unknown;
}


// ============================================================
// EXTRACT ACTUAL PAYLOAD
//
// Backend:
// ResponseEntity<ApiResponse<T>>
//
// Axios:
// AxiosResponse<ApiResponse<T>>
//
// Therefore:
//
// response
//   -> response.data
//       -> envelope.data
//           -> actual payload
// ============================================================

function extractPayload<T>(
  response: unknown
): T {

  let current: unknown =
    response;


  // ----------------------------------------------------------
  // First unwrap Axios response
  // ----------------------------------------------------------

  if (
    current &&
    typeof current === 'object'
  ) {

    const axiosResponse =
      current as ApiResponseLike;

    if (
      axiosResponse.data !== undefined
    ) {

      current =
        axiosResponse.data;
    }
  }


  // ----------------------------------------------------------
  // Unwrap ApiResponse
  // ----------------------------------------------------------

  if (
    current &&
    typeof current === 'object'
  ) {

    const envelope =
      current as ApiEnvelope<T>;


    if (
      envelope.data !== undefined
    ) {

      return envelope.data as T;
    }


    if (
      envelope.content !== undefined
    ) {

      return envelope.content as T;
    }
  }


  // ----------------------------------------------------------
  // Already the actual payload
  // ----------------------------------------------------------

  return current as T;
}


// ============================================================
// NORMALIZE SINGLE OBJECT
// ============================================================

function unwrap<T>(
  response: unknown
): T {

  return extractPayload<T>(
    response
  );
}


// ============================================================
// NORMALIZE ARRAY
//
// Supports:
//
// AxiosResponse<array>
//
// AxiosResponse<ApiResponse<array>>
//
// AxiosResponse<ApiResponse<Page<array>>>
//
// {
//   data: []
// }
//
// {
//   data: {
//     data: []
//   }
// }
//
// {
//   data: {
//     content: []
//   }
// }
//
// {
//   content: []
// }
//
// {
//   items: []
// }
//
// {
//   results: []
// }
//
// The important part is that this function ALWAYS returns
// an array.
// ============================================================

function unwrapArray<T>(
  response: unknown
): T[] {

  let current: unknown =
    response;


  // ----------------------------------------------------------
  // Axios response
  // ----------------------------------------------------------

  if (
    current &&
    typeof current === 'object'
  ) {

    const axiosResponse =
      current as ApiResponseLike;

    if (
      axiosResponse.data !== undefined
    ) {

      current =
        axiosResponse.data;
    }
  }


  // ----------------------------------------------------------
  // Repeatedly unwrap common envelope structures
  // ----------------------------------------------------------

  for (
    let depth = 0;
    depth < 6;
    depth++
  ) {

    // --------------------------------------------------------
    // Already array
    // --------------------------------------------------------

    if (
      Array.isArray(current)
    ) {

      return current as T[];
    }


    // --------------------------------------------------------
    // Must be object to continue
    // --------------------------------------------------------

    if (
      !current ||
      typeof current !== 'object'
    ) {

      return [];
    }


    const value =
      current as {
        data?: unknown;
        content?: unknown;
        items?: unknown;
        results?: unknown;
      };


    // --------------------------------------------------------
    // Direct array properties
    // --------------------------------------------------------

    if (
      Array.isArray(value.data)
    ) {

      return value.data as T[];
    }


    if (
      Array.isArray(value.content)
    ) {

      return value.content as T[];
    }


    if (
      Array.isArray(value.items)
    ) {

      return value.items as T[];
    }


    if (
      Array.isArray(value.results)
    ) {

      return value.results as T[];
    }


    // --------------------------------------------------------
    // Nested objects
    // --------------------------------------------------------

    if (
      value.data &&
      typeof value.data === 'object'
    ) {

      current =
        value.data;

      continue;
    }


    if (
      value.content &&
      typeof value.content === 'object'
    ) {

      current =
        value.content;

      continue;
    }


    if (
      value.items &&
      typeof value.items === 'object'
    ) {

      current =
        value.items;

      continue;
    }


    if (
      value.results &&
      typeof value.results === 'object'
    ) {

      current =
        value.results;

      continue;
    }


    return [];
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
    window.URL.createObjectURL(
      blob
    );


  const anchor =
    document.createElement(
      'a'
    );


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
            error?: string;
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


    const apiError =
      value.response
        ?.data
        ?.error;


    if (
      apiError &&
      typeof apiError === 'string'
    ) {

      return apiError;
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


    const report =
      unwrap<BnrFinancialStatementReport>(
        response
      );


    // --------------------------------------------------------
    // Keep financial statement sections as arrays.
    //
    // This prevents the page from ever doing:
    //
    // report.assets.map(...)
    //
    // when assets is null/object.
    // --------------------------------------------------------

    return {
      ...report,

      assets:
        Array.isArray(report?.assets)
          ? report.assets
          : [],

      liabilities:
        Array.isArray(report?.liabilities)
          ? report.liabilities
          : [],

      equity:
        Array.isArray(report?.equity)
          ? report.equity
          : [],

      income:
        Array.isArray(report?.income)
          ? report.income
          : [],

      expenses:
        Array.isArray(report?.expenses)
          ? report.expenses
          : [],
    };
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
  //
  // NOTE:
  // Your BNR controller does NOT expose:
  //
  // /regulatory/credit-bureau
  //
  // Your actual CreditBureauController exposes:
  //
  // /credit-bureau/borrowers/{id}/check
  // /credit-bureau/borrowers/{id}/history
  // /credit-bureau/borrowers/{id}/latest
  //
  // Therefore the old generic creditBureau() method should
  // not pretend that /regulatory/credit-bureau exists.
  // ==========================================================

  async creditBureauHistory(
    borrowerId: number
  ): Promise<unknown[]> {

    const response =
      await api.get(
        `/credit-bureau/borrowers/${borrowerId}/history`
      );


    return unwrapArray<unknown>(
      response
    );
  },


  // ==========================================================
  // CREDIT BUREAU LATEST
  // ==========================================================

  async creditBureauLatest(
    borrowerId: number
  ): Promise<unknown | null> {

    try {

      const response =
        await api.get(
          `/credit-bureau/borrowers/${borrowerId}/latest`
        );


      const result =
        unwrap<unknown>(
          response
        );


      return result ?? null;

    } catch {

      return null;
    }
  },


  // ==========================================================
  // RUN CREDIT BUREAU CHECK
  // ==========================================================

  async runCreditBureauCheck(
    borrowerId: number
  ): Promise<unknown> {

    const response =
      await api.post(
        `/credit-bureau/borrowers/${borrowerId}/check`
      );


    return unwrap<unknown>(
      response
    );
  },


  // ==========================================================
  // LEGACY CREDIT BUREAU METHOD
  //
  // Kept so existing imports do not immediately break.
  //
  // The endpoint itself is NOT called because the backend
  // controller you supplied does not define a generic
  // /regulatory/credit-bureau endpoint.
  // ==========================================================

  async creditBureau(
    _params?: BnrReportParams
  ): Promise<CreditRecord[]> {

    return [];
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
            format:
              normalizedFormat,
          },

          responseType:
            'blob',
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