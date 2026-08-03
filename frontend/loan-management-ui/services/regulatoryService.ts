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


export type RegulatoryApiClientType =
  | 'BNR'
  | 'CREDIT_BUREAU';


// ============================================================
// PARAMETERS
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


export type CreditBureauRecord =
  CreditRecord;


// ============================================================
// API CLIENT
// ============================================================

export interface RegulatoryApiClient {

  id: number;

  name: string;

  clientType:
    | RegulatoryApiClientType
    | string;

  contactEmail?: string;

  description?: string;

  apiKey?: string;
  key?: string;

  active?: boolean;
  revoked?: boolean;

  createdAt?: string;

  expiresAt?: string | null;

  revokedAt?: string | null;

  revokedReason?: string | null;

  lastUsedAt?: string | null;
}


// ============================================================
// ENVELOPE
// ============================================================

interface ApiEnvelope<T = unknown> {

  success?: boolean;

  message?: string;

  data?: T;

  content?: T;
}


// ============================================================
// PAYLOAD
// ============================================================

function unwrap<T>(
  response: unknown
): T {

  let current =
    response;


  if (
    current &&
    typeof current === 'object'
  ) {

    const value =
      current as {
        data?: unknown;
      };


    if (
      value.data !== undefined
    ) {

      current =
        value.data;
    }
  }


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


  return current as T;
}


// ============================================================
// ARRAY UNWRAP
// ============================================================

function unwrapArray<T>(
  response: unknown
): T[] {

  let current =
    response;


  if (
    current &&
    typeof current === 'object'
  ) {

    const value =
      current as {
        data?: unknown;
      };


    if (
      value.data !== undefined
    ) {

      current =
        value.data;
    }
  }


  for (
    let depth = 0;
    depth < 8;
    depth++
  ) {

    if (
      Array.isArray(current)
    ) {

      return current as T[];
    }


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
// BNR QUERY
// ============================================================

function toQueryParams(
  params?: BnrReportParams
): QueryParams {

  const query: QueryParams = {};


  if (
    params?.branchId !== undefined
  ) {

    query.branchId =
      params.branchId;
  }


  if (
    params?.period
  ) {

    query.period =
      params.period;
  }


  if (
    params?.from
  ) {

    query.from =
      params.from;
  }


  if (
    params?.to
  ) {

    query.to =
      params.to;
  }


  return query;
}


// ============================================================
// CREDIT BUREAU QUERY
// ============================================================

function toCreditBureauQueryParams(
  params?: CreditBureauReportParams
): QueryParams {

  const query: QueryParams = {};


  if (
    params?.branchId !== undefined
  ) {

    query.branchId =
      params.branchId;
  }


  if (
    params?.from
  ) {

    query.from =
      params.from;
  }


  if (
    params?.to
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


    return unwrapArray<BreakdownRow>(
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


    return unwrapArray<BreakdownRow>(
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


    return unwrapArray<BreakdownRow>(
      response
    );
  },


  // ==========================================================
  // BNR EXPORT
  // ==========================================================

  async bnrExport(
    format: ExportFormat,
    params?: BnrReportParams
  ): Promise<void> {

    const response =
      await api.get(
        '/regulatory/bnr/export',
        {
          params: {
            ...toQueryParams(params),
            format,
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
                format === 'pdf'
                  ? 'application/pdf'
                  : format === 'csv'
                    ? 'text/csv'
                    : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            }
          );


    triggerDownload(
      blob,
      `bnr-summary.${format}`
    );
  },


  // ==========================================================
  // REGULATORY CREDIT BUREAU PREVIEW
  // ==========================================================

  async creditBureauPreview(
    params?: CreditBureauReportParams
  ): Promise<CreditRecord[]> {

    const response =
      await api.get(
        '/regulatory/credit-bureau/preview',
        {
          params:
            toCreditBureauQueryParams(
              params
            ),
        }
      );


    return unwrapArray<CreditRecord>(
      response
    );
  },


  // ==========================================================
  // REGULATORY CREDIT BUREAU EXPORT
  // ==========================================================

  async creditBureauExport(
    format: ExportFormat,
    params?: CreditBureauReportParams
  ): Promise<void> {

    const response =
      await api.get(
        '/regulatory/credit-bureau/export',
        {
          params: {
            ...toCreditBureauQueryParams(
              params
            ),

            format,
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
                format === 'pdf'
                  ? 'application/pdf'
                  : format === 'csv'
                    ? 'text/csv'
                    : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            }
          );


    triggerDownload(
      blob,
      `credit-bureau-export.${format}`
    );
  },


  // ==========================================================
  // ACTUAL CREDIT BUREAU HISTORY
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
  // ACTUAL CREDIT BUREAU LATEST
  // ==========================================================

  async creditBureauLatest(
    borrowerId: number
  ): Promise<unknown | null> {

    try {

      const response =
        await api.get(
          `/credit-bureau/borrowers/${borrowerId}/latest`
        );


      return (
        unwrap<unknown>(
          response
        ) ?? null
      );

    } catch {

      return null;
    }
  },


  // ==========================================================
  // ACTUAL CREDIT BUREAU CHECK
  // ==========================================================

  async runCreditBureauCheck(
    borrowerId: number
  ): Promise<unknown> {

    const response =
      await api.post(
        `/credit-bureau/borrowers/${borrowerId}/check`,
        undefined
      );


    return unwrap<unknown>(
      response
    );
  },


  // ==========================================================
  // API CLIENTS
  // ==========================================================

  async listApiClients(): Promise<RegulatoryApiClient[]> {

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
  // ==========================================================

  async createApiClient(
    data: {
      name: string;

      clientType:
        | 'BNR'
        | 'CREDIT_BUREAU';

      contactEmail?: string;

      description?: string;

      expiresAt?: string | null;
    }
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
  // ==========================================================

  async revokeApiClient(
    id: number,
    reason?: string
  ): Promise<RegulatoryApiClient> {

    const response =
      await api.post(
        `/regulatory/api-clients/${id}/revoke`,
        {
          reason,
        }
      );


    return unwrap<RegulatoryApiClient>(
      response
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


      const message =
        value.response
          ?.data
          ?.message;


      if (
        typeof message === 'string' &&
        message
      ) {

        return message;
      }


      const errorMessage =
        value.response
          ?.data
          ?.error;


      if (
        typeof errorMessage === 'string' &&
        errorMessage
      ) {

        return errorMessage;
      }


      if (
        typeof value.message === 'string' &&
        value.message
      ) {

        return value.message;
      }
    }


    return fallback;
  },
};


export default regulatoryApi;