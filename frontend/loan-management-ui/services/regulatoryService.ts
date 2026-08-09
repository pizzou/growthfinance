
import api from '@/services/api';



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

/**
 * ============================================================
 * PARAMETERS
 * ============================================================
 */

export interface BnrReportParams {
  branchId?: number;
  period?: RegulatoryPeriod;
  from?: string;
  to?: string;
}

export interface CreditBureauReportParams {
  borrowerId?: number;
  branchId?: number;
  from?: string;
  to?: string;
}

type QueryParams =
  Record<string, unknown>;

/**
 * ============================================================
 * BREAKDOWN
 * ============================================================
 */

export interface BreakdownRow {
  label: string;
  count: number;
  amount: number;
}

export type BnrBreakdownRow =
  BreakdownRow;

/**
 * ============================================================
 * BNR SUMMARY
 * ============================================================
 */

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

  loanTypeBreakdown?:
    BreakdownRow[];

  branchBreakdown?:
    BreakdownRow[];

  genderBreakdown?:
    BreakdownRow[];

  loansMissingBorrower?: number;
  borrowersMissingNationalId?: number;
  loansMissingBranch?: number;
  loansMissingCurrency?: number;
  loansMissingRepaymentSchedule?: number;

  dataQualityWarnings?: string[];

  reportStatus?: string;

  submissionReference?:
    string | null;
}

/**
 * ============================================================
 * FINANCIAL STATEMENT
 * ============================================================
 */

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

  assets?:
    FinancialStatementRow[];

  liabilities?:
    FinancialStatementRow[];

  equity?:
    FinancialStatementRow[];

  totalAssets?: number;
  totalLiabilities?: number;
  totalEquity?: number;

  currentPeriodNetIncome?: number;

  balanceSheetBalanced?: boolean;

  income?:
    FinancialStatementRow[];

  expenses?:
    FinancialStatementRow[];

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

/**
 * ============================================================
 * REGULATORY API CLIENT
 * ============================================================
 */

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

  expiresAt?:
    string | null;

  revokedAt?:
    string | null;

  revokedReason?:
    string | null;

  lastUsedAt?:
    string | null;
}

/**
 * ============================================================
 * API ENVELOPE
 * ============================================================
 */

interface ApiEnvelope<T = unknown> {

  success?: boolean;

  message?: string;

  data?: T;

  content?: T;
}

/**
 * ============================================================
 * UNWRAP
 * ============================================================
 */

function unwrap<T>(
  response: unknown
): T {

  let current =
    response;

  /**
   * Axios response:
   *
   * {
   *   data: ...
   * }
   */

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

  /**
   * Backend envelope:
   *
   * {
   *   data: ...
   * }
   *
   * or
   *
   * {
   *   content: ...
   * }
   */

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

/**
 * ============================================================
 * ARRAY UNWRAP
 * ============================================================
 */

function unwrapArray<T>(
  response: unknown
): T[] {

  let current =
    response;

  /**
   * First remove Axios response.data.
   */

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

  /**
   * Then recursively inspect common
   * backend wrappers.
   */

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
      Array.isArray(
        value.data
      )
    ) {
      return value.data as T[];
    }

    if (
      Array.isArray(
        value.content
      )
    ) {
      return value.content as T[];
    }

    if (
      Array.isArray(
        value.items
      )
    ) {
      return value.items as T[];
    }

    if (
      Array.isArray(
        value.results
      )
    ) {
      return value.results as T[];
    }

    if (
      Array.isArray(
        value.records
      )
    ) {
      return value.records as T[];
    }

    if (
      value.data &&
      typeof value.data ===
        'object'
    ) {
      current =
        value.data;

      continue;
    }

    if (
      value.content &&
      typeof value.content ===
        'object'
    ) {
      current =
        value.content;

      continue;
    }

    if (
      value.items &&
      typeof value.items ===
        'object'
    ) {
      current =
        value.items;

      continue;
    }

    if (
      value.results &&
      typeof value.results ===
        'object'
    ) {
      current =
        value.results;

      continue;
    }

    if (
      value.records &&
      typeof value.records ===
        'object'
    ) {
      current =
        value.records;

      continue;
    }

    return [];
  }

  return [];
}

/**
 * ============================================================
 * QUERY PARAMETER HELPERS
 * ============================================================
 */

function toQueryParams(
  params?: BnrReportParams
): QueryParams {

  const query:
    QueryParams = {};

  if (
    params?.branchId !==
      undefined
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

function toCreditBureauQueryParams(
  params?: CreditBureauReportParams
): QueryParams {

  const query:
    QueryParams = {};

  if (
    params?.borrowerId !==
      undefined
  ) {
    query.borrowerId =
      params.borrowerId;
  }

  if (
    params?.branchId !==
      undefined
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

/**
 * ============================================================
 * EXPORT MIME TYPES
 * ============================================================
 */

function getExportContentType(
  format: ExportFormat
): string {

  switch (
    format.toLowerCase()
  ) {

    case 'pdf':
      return 'application/pdf';

    case 'csv':
      return 'text/csv;charset=utf-8';

    case 'xlsx':
      return 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

    default:
      return 'application/octet-stream';
  }
}

function getExportAcceptHeader(
  format: ExportFormat
): string {

  switch (
    format.toLowerCase()
  ) {

    case 'pdf':
      return 'application/pdf';

    case 'csv':
      return 'text/csv';

    case 'xlsx':
      return 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

    default:
      return '*/*';
  }
}

/**
 * ============================================================
 * DOWNLOAD
 * ============================================================
 */

function triggerDownload(
  blob: Blob,
  filename: string
): void {

  if (
    typeof window ===
      'undefined'
  ) {
    return;
  }

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

  window.setTimeout(
    () => {
      window.URL.revokeObjectURL(
        url
      );
    },
    1000
  );
}

/**
 * ============================================================
 * BLOB ERROR MESSAGE
 * ============================================================
 */

async function getBlobErrorMessage(
  error: unknown
): Promise<string | null> {

  if (
    !error ||
    typeof error !== 'object'
  ) {
    return null;
  }

  const value =
    error as {
      response?: {
        status?: number;
        data?: unknown;
        headers?: unknown;
      };

      status?: number;

      data?: unknown;

      message?: string;
    };

  const response =
    value.response;

  /**
   * If Axios response is preserved,
   * inspect the response body.
   */

  if (response) {

    const responseData =
      response.data;

    if (
      responseData instanceof
      Blob
    ) {

      try {

        const text =
          await responseData.text();

        if (!text) {
          return null;
        }

        try {

          const json =
            JSON.parse(text) as {
              message?: string;
              error?: string;
              detail?: string;
            };

          return (
            json.message ||
            json.error ||
            json.detail ||
            null
          );

        } catch {

          return text;
        }

      } catch {

        return null;
      }
    }

    if (
      typeof responseData ===
        'string'
    ) {
      return (
        responseData ||
        null
      );
    }

    if (
      responseData &&
      typeof responseData ===
        'object'
    ) {

      const data =
        responseData as {
          message?: string;
          error?: string;
          detail?: string;
        };

      return (
        data.message ||
        data.error ||
        data.detail ||
        null
      );
    }
  }

  /**
   * Fallback to enhanced error data.
   */

  if (
    value.data instanceof
    Blob
  ) {

    try {

      const text =
        await value.data.text();

      if (!text) {
        return null;
      }

      try {

        const json =
          JSON.parse(text) as {
            message?: string;
            error?: string;
            detail?: string;
          };

        return (
          json.message ||
          json.error ||
          json.detail ||
          null
        );

      } catch {

        return text;
      }

    } catch {

      return null;
    }
  }

  if (
    value.data &&
    typeof value.data ===
      'object'
  ) {

    const data =
      value.data as {
        message?: string;
        error?: string;
        detail?: string;
      };

    return (
      data.message ||
      data.error ||
      data.detail ||
      null
    );
  }

  return (
    value.message ||
    null
  );
}

/**
 * ============================================================
 * API CLIENT
 * ============================================================
 */

export const regulatoryApi = {

  /**
   * ==========================================================
   * BNR SUMMARY
   * ==========================================================
   */

  async bnrSummary(
    params?: BnrReportParams
  ): Promise<BnrSummary> {

    const response =
      await api.get(
        '/regulatory/bnr/summary',
        {
          params:
            toQueryParams(
              params
            ),
        }
      );

    return unwrap<BnrSummary>(
      response
    );
  },

  /**
   * ==========================================================
   * BNR FINANCIAL STATEMENT
   * ==========================================================
   */

  async bnrFinancialStatement(
    params?: BnrReportParams
  ): Promise<BnrFinancialStatementReport> {

    const response =
      await api.get(
        '/regulatory/bnr/financial-statement',
        {
          params:
            toQueryParams(
              params
            ),
        }
      );

    const report =
      unwrap<BnrFinancialStatementReport>(
        response
      );

    return {
      ...report,

      assets:
        Array.isArray(
          report?.assets
        )
          ? report.assets
          : [],

      liabilities:
        Array.isArray(
          report?.liabilities
        )
          ? report.liabilities
          : [],

      equity:
        Array.isArray(
          report?.equity
        )
          ? report.equity
          : [],

      income:
        Array.isArray(
          report?.income
        )
          ? report.income
          : [],

      expenses:
        Array.isArray(
          report?.expenses
        )
          ? report.expenses
          : [],
    };
  },

  /**
   * ==========================================================
   * BNR LOAN TYPE
   * ==========================================================
   */

  async bnrByLoanType(
    params?: BnrReportParams
  ): Promise<BreakdownRow[]> {

    const response =
      await api.get(
        '/regulatory/bnr/breakdown/loan-type',
        {
          params:
            toQueryParams(
              params
            ),
        }
      );

    return unwrapArray<
      BreakdownRow
    >(response);
  },

  /**
   * ==========================================================
   * BNR BRANCH
   * ==========================================================
   */

  async bnrByBranch(
    params?: BnrReportParams
  ): Promise<BreakdownRow[]> {

    const response =
      await api.get(
        '/regulatory/bnr/breakdown/branch',
        {
          params:
            toQueryParams(
              params
            ),
        }
      );

    return unwrapArray<
      BreakdownRow
    >(response);
  },

  /**
   * ==========================================================
   * BNR GENDER
   * ==========================================================
   */

  async bnrByGender(
    params?: BnrReportParams
  ): Promise<BreakdownRow[]> {

    const response =
      await api.get(
        '/regulatory/bnr/breakdown/gender',
        {
          params:
            toQueryParams(
              params
            ),
        }
      );

    return unwrapArray<
      BreakdownRow
    >(response);
  },

  /**
   * ==========================================================
   * BNR EXPORT
   * ==========================================================
   */

  async bnrExport(
    format: ExportFormat,
    params?: BnrReportParams
  ): Promise<void> {

    try {

      const response =
        await api.get(
          '/regulatory/bnr/export',
          {
            params: {
              ...toQueryParams(
                params
              ),
              format,
            },

            responseType:
              'blob',

            headers: {
              Accept:
                getExportAcceptHeader(
                  format
                ),
            },
          }
        );

      const blob =
        response.data instanceof
        Blob
          ? response.data
          : new Blob(
              [response.data],
              {
                type:
                  getExportContentType(
                    format
                  ),
              }
            );

      triggerDownload(
        blob,
        `bnr-summary.${format}`
      );

    } catch (error) {

      console.error(
        'BNR export failed:',
        error
      );

      throw error;
    }
  },

  /**
   * ==========================================================
   * CREDIT BUREAU PREVIEW
   * ==========================================================
   */

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

    return unwrapArray<
      CreditRecord
    >(response);
  },

  /**
   * ==========================================================
   * CREDIT BUREAU EXPORT
   * ==========================================================
   *
   * IMPORTANT:
   *
   * This uses the exact same `api` instance as BNR.
   *
   * Therefore:
   *
   * Authorization: Bearer <JWT>
   *
   * is attached by api.ts.
   *
   * ==========================================================
   */

  async creditBureauExport(
    format: ExportFormat,
    params?: CreditBureauReportParams
  ): Promise<void> {

    const queryParams =
      toCreditBureauQueryParams(
        params
      );

    console.log(
      'Credit Bureau export request:',
      {
        url:
          '/regulatory/credit-bureau/download',

        format,

        queryParams,

        hasToken:
          typeof window !==
            'undefined' &&
          Boolean(
            localStorage.getItem(
              'token'
            )
          ),
      }
    );

    try {

      /**
       * ------------------------------------------------------
       * IMPORTANT
       * ------------------------------------------------------
       *
       * We deliberately do NOT manually set the Authorization
       * header here.
       *
       * api.ts request interceptor does that consistently for
       * both BNR and Credit Bureau.
       *
       * ------------------------------------------------------
       */

      const response =
        await api.get(
          '/regulatory/credit-bureau/download',
          {
            params: {
              ...queryParams,
              format,
            },

            responseType:
              'blob',

            headers: {
              Accept:
                getExportAcceptHeader(
                  format
                ),
            },
          }
        );

      console.log(
        'Credit Bureau export response:',
        {
          status:
            response.status,

          contentType:
            response.headers?.[
              'content-type'
            ],

          contentDisposition:
            response.headers?.[
              'content-disposition'
            ],
        }
      );

      const blob =
        response.data instanceof
        Blob
          ? response.data
          : new Blob(
              [response.data],
              {
                type:
                  getExportContentType(
                    format
                  ),
              }
            );

      triggerDownload(
        blob,
        `credit-bureau-export.${format}`
      );

    } catch (error) {

      console.error(
        'Credit Bureau export failed:',
        error
      );

      /**
       * IMPORTANT:
       *
       * Because api.ts now preserves AxiosError,
       * this can inspect:
       *
       * error.response.status
       * error.response.data
       */

      const blobMessage =
        await getBlobErrorMessage(
          error
        );

      if (
        blobMessage
      ) {

        const enhancedError =
          error instanceof Error
            ? error
            : new Error(
                blobMessage
              );

        enhancedError.message =
          blobMessage;

        throw enhancedError;
      }

      throw error;
    }
  },

  /**
   * ==========================================================
   * CREDIT BUREAU HISTORY
   * ==========================================================
   */

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

  /**
   * ==========================================================
   * CREDIT BUREAU LATEST
   * ==========================================================
   */

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

  /**
   * ==========================================================
   * CREDIT BUREAU CHECK
   * ==========================================================
   */

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

  /**
   * ==========================================================
   * API CLIENTS
   * ==========================================================
   */

  async listApiClients():
    Promise<
      RegulatoryApiClient[]
    > {

    const response =
      await api.get(
        '/regulatory/api-clients'
      );

    return unwrapArray<
      RegulatoryApiClient
    >(response);
  },

  /**
   * ==========================================================
   * CREATE API CLIENT
   * ==========================================================
   */

  async createApiClient(
    data: {
      name: string;

      clientType:
        | 'BNR'
        | 'CREDIT_BUREAU';

      contactEmail?: string;

      description?: string;

      expiresAt?:
        string | null;
    }
  ): Promise<RegulatoryApiClient> {

    const response =
      await api.post(
        '/regulatory/api-clients',
        data
      );

    return unwrap<
      RegulatoryApiClient
    >(response);
  },

  /**
   * ==========================================================
   * REVOKE API CLIENT
   * ==========================================================
   */

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

    return unwrap<
      RegulatoryApiClient
    >(response);
  },

  /**
   * ==========================================================
   * ERROR MESSAGE
   * ==========================================================
   */

  getErrorMessage(
    error: unknown,
    fallback =
      'An error occurred while loading the regulatory report.'
  ): string {

    if (
      error &&
      typeof error ===
        'object'
    ) {

      const value =
        error as {
          response?: {
            status?: number;
            data?: unknown;
          };

          status?: number;

          data?: unknown;

          message?: string;
        };

      /**
       * ------------------------------------------------------
       * Axios response body
       * ------------------------------------------------------
       */

      const response =
        value.response;

      if (response) {

        /**
         * Blob response
         */

        if (
          response.data instanceof
          Blob
        ) {

          /**
           * We cannot synchronously read
           * Blob here, so use the status
           * message below.
           */
        }

        /**
         * JSON response
         */

        if (
          response.data &&
          typeof response.data ===
            'object'
        ) {

          const data =
            response.data as {
              message?: string;
              error?: string;
              detail?: string;
            };

          if (
            typeof data.message ===
              'string' &&
            data.message
          ) {
            return data.message;
          }

          if (
            typeof data.error ===
              'string' &&
            data.error
          ) {
            return data.error;
          }

          if (
            typeof data.detail ===
              'string' &&
            data.detail
          ) {
            return data.detail;
          }
        }

        /**
         * String response
         */

        if (
          typeof response.data ===
            'string' &&
          response.data
        ) {
          return response.data;
        }

        /**
         * HTTP status
         */

        if (
          response.status ===
            403
        ) {
          return (
            'Access denied (403). The server rejected the Credit Bureau export request.'
          );
        }

        if (
          response.status ===
            401
        ) {
          return (
            'Authentication failed (401). Please sign in again.'
          );
        }
      }

      /**
       * Enhanced Axios error status
       */

      if (
        value.status ===
          403
      ) {
        return (
          'Access denied (403). The server rejected the Credit Bureau export request.'
        );
      }

      if (
        value.status ===
          401
      ) {
        return (
          'Authentication failed (401). Please sign in again.'
        );
      }

      /**
       * Normal Error message
       */

      if (
        typeof value.message ===
          'string' &&
        value.message
      ) {
        return value.message;
      }
    }

    return fallback;
  },
};

export default regulatoryApi;
