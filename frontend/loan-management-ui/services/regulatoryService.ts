import API, { get, post } from './api';


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
  | 'xlsx'
  | 'csv'
  | 'pdf';


export type BnrReportParams = {
  branchId?: number;
  period?: RegulatoryPeriod;
  from?: string;
  to?: string;
};


// ============================================================
// BNR SUMMARY
// ============================================================

export type BnrSummary = {
  organizationName?: string;
  bnrInstitutionCode?: string;

  reportPeriod?: string;

  periodStart?: string;
  periodEnd?: string;

  totalLoans?: number;
  totalLoansIssued?: number;

  activeLoans?: number;
  closedLoans?: number;
  pendingLoans?: number;
  rejectedLoans?: number;
  overdueLoans?: number;
  defaultedLoans?: number;

  totalPrincipalDisbursed?: number;
  outstandingPrincipal?: number;

  totalInterestCollected?: number;
  interestAccruedUnpaid?: number;

  totalProcessingFees?: number;

  maleBorrowers?: number;
  femaleBorrowers?: number;
  otherGenderBorrowers?: number;

  parAmount?: number;
  parRatio?: number;

  nplAmount?: number;
  nplRatio?: number;

  currency?: string;
};


// ============================================================
// BNR BREAKDOWN
// ============================================================
//
// This must match BnrBreakdownRow returned by the backend.
//
// If your Java BnrBreakdownRow has these three properties:
//
//     label
//     count
//     amount
//
// then this is the correct frontend type.
//

export type BreakdownRow = {
  label: string;
  count: number;
  amount: number;
};


// ============================================================
// FINANCIAL STATEMENT
// ============================================================

export type FinancialStatementAccount = {
  code?: string;
  name?: string;
  balance?: number;
};


export type BnrFinancialStatementReport = {

  assets?: FinancialStatementAccount[];

  liabilities?: FinancialStatementAccount[];

  equity?: FinancialStatementAccount[];

  income?: FinancialStatementAccount[];

  expenses?: FinancialStatementAccount[];


  totalAssets?: number;

  totalLiabilities?: number;

  totalEquity?: number;

  currentPeriodNetIncome?: number;


  balanceSheetBalanced?: boolean;


  totalIncome?: number;

  totalExpenses?: number;

  netIncome?: number;


  trialBalanceDebit?: number;

  trialBalanceCredit?: number;

  trialBalanceBalanced?: boolean;


  cashUsedForLending?: number;

  cashFromCollections?: number;

  cashFromFees?: number;

  otherCashMovement?: number;

  netChangeInCash?: number;


  currency?: string;

  organizationName?: string;

  periodStart?: string;

  periodEnd?: string;
};


// ============================================================
// CREDIT BUREAU
// ============================================================

export type CreditRecord = {
  borrowerId?: number;

  fullName?: string;

  nationalId?: string;

  loanNumber?: string;

  loanType?: string;

  loanStatus?: string;

  loanAmount?: number;

  outstandingBalance?: number;

  daysPastDue?: number;

  creditScore?: number;

  dateOpened?: string;

  lastPaymentDate?: string;

  branchName?: string;
};


// ============================================================
// API CLIENT
// ============================================================

export type ApiClient = {
  id: number;

  name: string;

  clientType:
    | 'BNR'
    | 'CREDIT_BUREAU';

  keyPrefix: string;

  active: boolean;

  contactEmail?: string;

  lastUsedAt?: string;

  revokedAt?: string;

  createdAt?: string;
};


// ============================================================
// API RESPONSE
// ============================================================

type ApiResponse<T> = {
  success?: boolean;

  message?: string;

  data?: T;

  error?: string;
};


// ============================================================
// QUERY STRING
// ============================================================

function qs(
  params: Record<string, unknown>
): string {

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
// ERROR MESSAGE
// ============================================================

function getErrorMessage(
  error: unknown,
  fallback = 'An unexpected error occurred.'
): string {

  const err =
    error as {
      response?: {
        data?: {
          message?: string;
          error?: string;
        };
      };

      message?: string;
    };


  return (
    err?.response?.data?.message ||
    err?.response?.data?.error ||
    err?.message ||
    fallback
  );
}


// ============================================================
// DOWNLOAD
// ============================================================

async function downloadFile(
  path: string,
  filename: string
): Promise<void> {

  const response =
    await API.get(
      path,
      {
        responseType: 'blob',
      }
    );


  const blob =
    response.data as Blob;


  const url =
    URL.createObjectURL(blob);


  const anchor =
    document.createElement('a');

  anchor.href = url;

  anchor.download = filename;

  document.body.appendChild(anchor);

  anchor.click();

  anchor.remove();


  setTimeout(
    () => URL.revokeObjectURL(url),
    60000
  );
}


// ============================================================
// REGULATORY API
// ============================================================

export const regulatoryApi = {

  // ==========================================================
  // BNR SUMMARY
  // ==========================================================

  bnrSummary: async (
    params: BnrReportParams
  ): Promise<BnrSummary> => {

    const response =
      await get(
        `/regulatory/bnr/summary?${qs(params)}`
      ) as BnrSummary | ApiResponse<BnrSummary>;


    /*
     * Supports both:
     *
     * {
     *   data: {...}
     * }
     *
     * and a direct object response.
     */

    if (
      response &&
      typeof response === 'object' &&
      'data' in response
    ) {

      return (
        (response as ApiResponse<BnrSummary>)
          .data as BnrSummary
      );
    }


    return response as BnrSummary;
  },


  // ==========================================================
  // BNR BY LOAN TYPE
  // ==========================================================

  bnrByLoanType: async (
    params: BnrReportParams
  ): Promise<BreakdownRow[]> => {

    const response =
      await get(
        `/regulatory/bnr/by-loan-type?${qs(params)}`
      ) as
        | BreakdownRow[]
        | ApiResponse<BreakdownRow[]>;


    if (
      response &&
      typeof response === 'object' &&
      'data' in response
    ) {

      return (
        (response as ApiResponse<BreakdownRow[]>)
          .data || []
      );
    }


    return response as BreakdownRow[];
  },


  // ==========================================================
  // BNR BY BRANCH
  // ==========================================================

  bnrByBranch: async (
    params: BnrReportParams
  ): Promise<BreakdownRow[]> => {

    const response =
      await get(
        `/regulatory/bnr/by-branch?${qs(params)}`
      ) as
        | BreakdownRow[]
        | ApiResponse<BreakdownRow[]>;


    if (
      response &&
      typeof response === 'object' &&
      'data' in response
    ) {

      return (
        (response as ApiResponse<BreakdownRow[]>)
          .data || []
      );
    }


    return response as BreakdownRow[];
  },


  // ==========================================================
  // BNR BY GENDER
  // ==========================================================

  bnrByGender: async (
    params: BnrReportParams
  ): Promise<BreakdownRow[]> => {

    const response =
      await get(
        `/regulatory/bnr/by-gender?${qs(params)}`
      ) as
        | BreakdownRow[]
        | ApiResponse<BreakdownRow[]>;


    if (
      response &&
      typeof response === 'object' &&
      'data' in response
    ) {

      return (
        (response as ApiResponse<BreakdownRow[]>)
          .data || []
      );
    }


    return response as BreakdownRow[];
  },


  // ==========================================================
  // BNR FINANCIAL STATEMENT
  // ==========================================================

  bnrFinancialStatement: async (
    params: BnrReportParams
  ): Promise<BnrFinancialStatementReport> => {

    const response =
      await get(
        `/regulatory/bnr/financial-statement?${qs(params)}`
      ) as
        | BnrFinancialStatementReport
        | ApiResponse<BnrFinancialStatementReport>;


    if (
      response &&
      typeof response === 'object' &&
      'data' in response
    ) {

      return (
        (response as ApiResponse<BnrFinancialStatementReport>)
          .data as BnrFinancialStatementReport
      );
    }


    return response as BnrFinancialStatementReport;
  },


  // ==========================================================
  // BNR EXPORT
  //
  // Summary export
  // ==========================================================

  bnrExport: (
    format: ExportFormat,
    params: BnrReportParams
  ): Promise<void> => {

    return downloadFile(
      `/regulatory/bnr/export?format=${format}&${qs(params)}`,
      `bnr-summary.${format}`
    );
  },


  // ==========================================================
  // BNR FINANCIAL STATEMENT EXPORT
  // ==========================================================

  bnrFinancialStatementExport: (
    format: ExportFormat,
    params: BnrReportParams
  ): Promise<void> => {

    return downloadFile(
      `/regulatory/bnr/financial-statement/export?format=${format}&${qs(params)}`,
      `BNR-Financial-Statement.${format}`
    );
  },


  // ==========================================================
  // CREDIT BUREAU PREVIEW
  // ==========================================================

  creditBureauPreview: async (
    params: {
      branchId?: number;
      from?: string;
      to?: string;
    }
  ): Promise<CreditRecord[]> => {

    const response =
      await get(
        `/regulatory/credit-bureau/preview?${qs(params)}`
      ) as
        | CreditRecord[]
        | ApiResponse<CreditRecord[]>;


    if (
      response &&
      typeof response === 'object' &&
      'data' in response
    ) {

      return (
        (response as ApiResponse<CreditRecord[]>)
          .data || []
      );
    }


    return response as CreditRecord[];
  },


  // ==========================================================
  // CREDIT BUREAU EXPORT
  // ==========================================================

  creditBureauExport: (
    format: ExportFormat,
    params: {
      branchId?: number;
      from?: string;
      to?: string;
    }
  ): Promise<void> => {

    return downloadFile(
      `/regulatory/credit-bureau/export?format=${format}&${qs(params)}`,
      `credit-bureau-export.${format}`
    );
  },


  // ==========================================================
  // API CLIENTS
  // ==========================================================

  listApiClients: async (): Promise<ApiClient[]> => {

    const response =
      await get(
        '/regulatory/api-clients'
      ) as
        | ApiClient[]
        | ApiResponse<ApiClient[]>;


    if (
      response &&
      typeof response === 'object' &&
      'data' in response
    ) {

      return (
        (response as ApiResponse<ApiClient[]>)
          .data || []
      );
    }


    return response as ApiClient[];
  },


  // ==========================================================
  // CREATE API CLIENT
  // ==========================================================

  createApiClient: (
    data: {
      name: string;
      clientType:
        | 'BNR'
        | 'CREDIT_BUREAU';
      contactEmail?: string;
      description?: string;
      expiresAt?: string | null;
    }
  ) => {

    return post(
      '/regulatory/api-clients',
      data
    );
  },


  // ==========================================================
  // REVOKE API CLIENT
  // ==========================================================

  revokeApiClient: (
    id: number,
    reason?: string
  ) => {

    return post(
      `/regulatory/api-clients/${id}/revoke`,
      {
        reason,
      }
    );
  },


  // ==========================================================
  // ERROR MESSAGE
  // ==========================================================

  getErrorMessage,

};

export default regulatoryApi;