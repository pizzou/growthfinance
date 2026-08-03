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

export interface CreditBureauReportParams {
  branchId?: number;
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

  reportPeriod?: string;
  periodStart?: string;
  periodEnd?: string;
  reportDate?: string;
  generatedAt?: string;
  generatedBy?: string;
  reportReference?: string;

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
  // DISBURSEMENT
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
  // COLLECTIONS
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


  // ----------------------------------------------------------
  // PAR AGING
  // ----------------------------------------------------------

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


  // ----------------------------------------------------------
  // DPD
  // ----------------------------------------------------------

  loansOver30Days?: number;
  loansOver60Days?: number;
  loansOver90Days?: number;
  loansOver180Days?: number;
  loansOver365Days?: number;


  // ----------------------------------------------------------
  // DEFAULT / WRITE-OFF
  // ----------------------------------------------------------

  defaultedAmount?: number;
  writtenOffAmount?: number;
  recoveriesAfterWriteOff?: number;


  // ----------------------------------------------------------
  // PROVISIONS
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
  // AGE
  // ----------------------------------------------------------

  youthBorrowers?: number;
  adultBorrowers?: number;
  seniorBorrowers?: number;


  // ----------------------------------------------------------
  // CREDIT INFORMATION
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
  // REPORT STATUS
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


  // ----------------------------------------------------------
  // PERIOD
  // ----------------------------------------------------------

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
  // INCOME STATEMENT
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


  // ----------------------------------------------------------
  // LOAN
  // ----------------------------------------------------------

  loanNumber?: string;

  loanType?: string;

  loanStatus?: string;

  loanAmount?: number;

  outstandingBalance?: number;

  daysPastDue?: number;

  creditScore?: number;


  // ----------------------------------------------------------
  // DATES
  // ----------------------------------------------------------

  dateOpened?: string;

  lastPaymentDate?: string;

  maturityDate?: string;

  dateClosed?: string;


  // ----------------------------------------------------------
  // ORGANIZATION
  // ----------------------------------------------------------

  branchName?: string;

  currency?: string;


  // ----------------------------------------------------------
  // ALLOW ADDITIONAL BACKEND FIELDS
  // ----------------------------------------------------------

  [key: string]: unknown;
}


export type CreditBureauRecord =
  CreditRecord;


// ============================================================
// API CLIENT / API KEY
// ============================================================

export type RegulatoryApiClientType =
  | 'BNR'
  | 'CREDIT_BUREAU';


export interface RegulatoryApiClient {

  id?: number;

  name?: string;

  clientType?: RegulatoryApiClientType;

  contactEmail?: string;

  description?: string;

  expiresAt?: string | null;

  createdAt?: string;

  updatedAt?: string;

  revokedAt?: string | null;

  revoked?: boolean;

  active?: boolean;

  status?: string;

  apiKey?: string;

  key?: string;

  secret?: string;

  [key: string]: unknown;
}


export interface CreateApiClientRequest {

  name: string;

  clientType:
    | 'BNR'
    | 'CREDIT_BUREAU';

  contactEmail?: string;

  description?: string;

  expiresAt?: string | null;
}


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
// ============================================================

interface ApiResponseLike<T = unknown> {

  data?: T;

  status?: number;

  statusText?: string;

  headers?: unknown;
}


// ============================================================
// EXTRACT PAYLOAD
// ============================================================

function extractPayload<T>(
  response: unknown
): T {

  let current: unknown =
    response;


  // ----------------------------------------------------------
  // UNWRAP AXIOS RESPONSE
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
  // UNWRAP API ENVELOPE
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
  // ALREADY PAYLOAD
  // ----------------------------------------------------------

  return current as T;
}


// ============================================================
// UNWRAP SINGLE OBJECT
// ============================================================

function unwrap<T>(
  response: unknown
): T {

  return extractPayload<T>(
    response
  );
}


// ============================================================
// UNWRAP ARRAY
// ============================================================

function unwrapArray<T>(
  response: unknown
): T[] {

  let current: unknown =
    response;


  // ----------------------------------------------------------
  // AXIOS RESPONSE
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
  // REPEATED UNWRAPPING
  // ----------------------------------------------------------

  for (
    let depth = 0;
    depth < 8;
    depth++
  ) {

    // --------------------------------------------------------
    // ARRAY
    // --------------------------------------------------------

    if (
      Array.isArray(current)
    ) {

      return current as T[];
    }


    // --------------------------------------------------------
    // OBJECT REQUIRED
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
        records?: unknown;
      };


    // --------------------------------------------------------
    // DIRECT ARRAYS
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


    if (
      Array.isArray(value.records)
    ) {

      return value.records as T[];
    }


    // --------------------------------------------------------
    // NESTED OBJECTS
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


    if (
      value.records &&
      typeof value.records === 'object'
    ) {

      current =
        value.records;

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


  const query: QueryParams =
    {};


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
  query.period = params.period;
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
// CREDIT BUREAU QUERY PARAMETERS
// ============================================================

function toCreditBureauQueryParams(
  params?: CreditBureauReportParams
): QueryParams {

  if (!params) {

    return {};
  }


  const query: QueryParams =
    {};


  if (
    params.branchId !== undefined &&
    params.branchId !== null
  ) {

    query.branchId =
      params.branchId;
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
// BUILD QUERY STRING
// ============================================================

function buildQueryString(
  params?: QueryParams
): string {

  if (!params) {

    return '';
  }


  const searchParams =
    new URLSearchParams();


  Object.entries(params).forEach(
    ([key, value]) => {

      if (
        value !== undefined &&
        value !== null &&
        value !== ''
      ) {

        searchParams.set(
          key,
          String(value)
        );
      }
    }
  );


  return searchParams.toString();
}


// ============================================================
// TRIGGER DOWNLOAD
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
// EXPORT MIME TYPE
// ============================================================

function getExportMimeType(
  format: ExportFormat
): string {

  switch (
    format
  ) {

    case 'pdf':

      return 'application/pdf';


    case 'csv':

      return 'text/csv';


    case 'xlsx':

      return 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';


    default:

      return 'application/octet-stream';
  }
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

            error?: string;

            detail?: string;
          };
        };

        message?: string;
      };


    // --------------------------------------------------------
    // API MESSAGE
    // --------------------------------------------------------

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


    // --------------------------------------------------------
    // API ERROR
    // --------------------------------------------------------

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


    // --------------------------------------------------------
    // DETAIL
    // --------------------------------------------------------

    const detail =
      value.response
        ?.data
        ?.detail;


    if (
      detail &&
      typeof detail === 'string'
    ) {

      return detail;
    }


    // --------------------------------------------------------
    // STANDARD ERROR
    // --------------------------------------------------------

    if (
      value.message &&
      typeof value.message === 'string'
    ) {

      return value.message;
    }
  }


  // ----------------------------------------------------------
  // STRING ERROR
  // ----------------------------------------------------------

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


    const report =
      unwrap<BnrFinancialStatementReport>(
        response
      );


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
  // BNR BY LOAN TYPE
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
  // BNR BY BRANCH
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
  // BNR BY GENDER
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
  // BNR EXPORT
  //
  // GET:
  //
  // /regulatory/bnr/export
  //
  // Example:
  //
  // /regulatory/bnr/export?format=xlsx&period=MONTHLY
  // ==========================================================

  async bnrExport(
    format: ExportFormat,
    params?: BnrReportParams
  ): Promise<void> {

    const normalizedFormat =
      format.toLowerCase() as ExportFormat;


    const query =
      toQueryParams(params);


    const queryString =
      buildQueryString({
        ...query,

        format:
          normalizedFormat,
      });


    const response =
      await api.get(
        `/regulatory/bnr/export?${queryString}`,
        {
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
                getExportMimeType(
                  normalizedFormat
                ),
            }
          );


    triggerDownload(
      blob,
      `bnr-summary.${normalizedFormat}`
    );
  },


  // ==========================================================
  // CREDIT BUREAU PREVIEW
  //
  // STAFF PREVIEW
  //
  // GET:
  //
  // /regulatory/credit-bureau/preview
  // ==========================================================

  async creditBureauPreview(
    params?: CreditBureauReportParams
  ): Promise<CreditRecord[]> {

    const query =
      toCreditBureauQueryParams(
        params
      );


    const queryString =
      buildQueryString(
        query
      );


    const endpoint =
      queryString.length > 0

        ? `/regulatory/credit-bureau/preview?${queryString}`

        : '/regulatory/credit-bureau/preview';


    const response =
      await api.get(
        endpoint
      );


    return unwrapArray<CreditRecord>(
      response
    );
  },


  // ==========================================================
  // CREDIT BUREAU EXPORT
  //
  // GET:
  //
  // /regulatory/credit-bureau/export
  //
  // Example:
  //
  // /regulatory/credit-bureau/export?format=xlsx
  // ==========================================================

  async creditBureauExport(
    format: ExportFormat,
    params?: CreditBureauReportParams
  ): Promise<void> {

    const normalizedFormat =
      format.toLowerCase() as ExportFormat;


    const query =
      toCreditBureauQueryParams(
        params
      );


    const queryString =
      buildQueryString({

        ...query,

        format:
          normalizedFormat,
      });


    const response =
      await api.get(
        `/regulatory/credit-bureau/export?${queryString}`,
        {
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
                getExportMimeType(
                  normalizedFormat
                ),
            }
          );


    triggerDownload(
      blob,
      `credit-bureau-export.${normalizedFormat}`
    );
  },


  // ==========================================================
  // CREDIT BUREAU — BORROWER HISTORY
  //
  // GET:
  //
  // /credit-bureau/borrowers/{id}/history
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
  // CREDIT BUREAU — LATEST
  //
  // GET:
  //
  // /credit-bureau/borrowers/{id}/latest
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
  // CREDIT BUREAU — RUN CHECK
  //
  // POST:
  //
  // /credit-bureau/borrowers/{id}/check
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
  // CREDIT BUREAU — LEGACY METHOD
  //
  // Kept for backwards compatibility.
  //
  // Instead of returning [] forever, this now calls the
  // restored staff preview endpoint.
  // ==========================================================

  async creditBureau(
    params?: BnrReportParams
  ): Promise<CreditRecord[]> {

    return this.creditBureauPreview({

      branchId:
        params?.branchId,

      from:
        params?.from,

      to:
        params?.to,
    });
  },


  // ==========================================================
  // CREDIT BUREAU REPORT ALIAS
  // ==========================================================

  async creditBureauReport(
    params?: BnrReportParams
  ): Promise<CreditRecord[]> {

    return this.creditBureauPreview({

      branchId:
        params?.branchId,

      from:
        params?.from,

      to:
        params?.to,
    });
  },


  // ==========================================================
  // API CLIENTS
  //
  // GET:
  //
  // /regulatory/api-clients
  // ==========================================================

  async listApiClients():
    Promise<RegulatoryApiClient[]> {

    const response =
      await api.get(
        '/regulatory/api-clients'
      );


    return unwrapArray<RegulatoryApiClient>(
      response
    );
  },


  // ==========================================================
  // CREATE API CLIENT
  //
  // POST:
  //
  // /regulatory/api-clients
  // ==========================================================

  async createApiClient(
    data: CreateApiClientRequest
  ): Promise<RegulatoryApiClient> {

    const response =
      await api.post(
        '/regulatory/api-clients',
        data
      );


    return unwrap<RegulatoryApiClient>(
      response
    );
  },


  // ==========================================================
  // REVOKE API CLIENT
  //
  // POST:
  //
  // /regulatory/api-clients/{id}/revoke
  // ==========================================================

  async revokeApiClient(
    id: number,
    reason?: string
  ): Promise<unknown> {

    const response =
      await api.post(
        `/regulatory/api-clients/${id}/revoke`,
        {
          reason,
        }
      );


    return unwrap<unknown>(
      response
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


// ============================================================
// DEFAULT EXPORT
// ============================================================

export default regulatoryApi;