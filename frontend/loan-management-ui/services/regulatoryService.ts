
import api from '@/services/api';



export type RegulatoryReportType =
  | 'BNR'
  | 'FINANCIAL'
  | 'CREDIT_BUREAU';

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

export type ClientType =
  | 'BNR'
  | 'CREDIT_BUREAU';

// ============================================================
// BNR
// ============================================================

export type BnrReportParams = {
  period: RegulatoryPeriod;
  from?: string;
  to?: string;
};

export type BnrSummary = {
  organizationName?: string;
  bnrInstitutionCode?: string;

  reportPeriod?:
    | RegulatoryPeriod
    | string;

  periodStart?: string;
  periodEnd?: string;

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

export type BreakdownRow = {
  label: string;
  count: number;
  amount: number;
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

export type CreditBureauParams = {
  from?: string;
  to?: string;
};

// ============================================================
// FINANCIAL
// ============================================================

export type FinancialReportParams = {
  from: string;
  to: string;
};

export type FinancialSummary = {
  organizationName?: string;

  periodStart?: string;
  periodEnd?: string;

  currency?: string;

  totalIncome?: number;
  totalExpenses?: number;
  netProfit?: number;

  openingCashBalance?: number;
  closingCashBalance?: number;

  cashInflows?: number;
  cashOutflows?: number;
  netCashFlow?: number;
};

export type FinancialBreakdownRow = {
  label: string;
  amount: number;
  category?: string;
};

// ============================================================
// API CLIENTS
// ============================================================

export type ApiClient = {
  id: number;

  name: string;

  clientType: ClientType;

  keyPrefix: string;

  active: boolean;

  contactEmail?: string;

  description?: string;

  lastUsedAt?: string;

  revokedAt?: string;

  expiresAt?: string;

  createdAt?: string;
};

export type CreateApiClientRequest = {
  name: string;

  clientType: ClientType;

  contactEmail?: string;

  description?: string;

  expiresAt?: string | null;
};

export type CreateApiClientResponse = {
  apiKey: string;

  client: ApiClient;
};

// ============================================================
// BACKEND ApiResponse TYPE
// ============================================================

type BackendApiResponse<T> = {
  success?: boolean;
  message?: string;
  error?: string;
  data?: T;
};

// ============================================================
// QUERY BUILDER
// ============================================================

function buildQueryParams<T extends object>(
  params: T,
): URLSearchParams {
  const searchParams =
    new URLSearchParams();

  Object.entries(params).forEach(
    ([key, value]) => {
      if (
        value === undefined ||
        value === null ||
        value === ''
      ) {
        return;
      }

      searchParams.set(
        key,
        String(value),
      );
    },
  );

  return searchParams;
}

// ============================================================
// RESPONSE UNWRAPPER
// ============================================================
//
// Your api.ts already has an unwrap() helper, but it is not
// exported. This service therefore unwraps the backend
// ApiResponse explicitly.
//
// This also safely handles an already-unwrapped response.
// ============================================================

function unwrapResponse<T>(
  body: unknown,
): T {
  if (
    body &&
    typeof body === 'object' &&
    'data' in body
  ) {
    return (
      body as BackendApiResponse<T>
    ).data as T;
  }

  return body as T;
}

// ============================================================
// ERROR HANDLER
// ============================================================

function getErrorMessage(
  error: unknown,
  fallback: string,
): string {
  const axiosError =
    error as {
      response?: {
        data?: {
          error?: string;
          message?: string;
        };
      };

      message?: string;
    };

  return (
    axiosError?.response?.data?.error ||
    axiosError?.response?.data?.message ||
    axiosError?.message ||
    fallback
  );
}

// ============================================================
// ARRAY NORMALIZER
// ============================================================
//
// Prevents React from ever receiving an object where an array
// is expected.
//
// This is especially important for .map() calls.
// ============================================================

function asArray<T>(
  value: unknown,
): T[] {
  if (Array.isArray(value)) {
    return value as T[];
  }

  return [];
}

// ============================================================
// API
// ============================================================

export const regulatoryApi = {

  // ==========================================================
  // ERROR
  // ==========================================================

  getErrorMessage,

  // ==========================================================
  // BNR SUMMARY
  // ==========================================================

  async bnrSummary(
    params: BnrReportParams,
  ): Promise<BnrSummary> {
    const query =
      buildQueryParams(params);

    const response =
      await api.get(
        `/regulatory/bnr/summary?${query.toString()}`,
      );

    return unwrapResponse<BnrSummary>(
      response.data,
    );
  },

  // ==========================================================
  // BNR BY LOAN TYPE
  // ==========================================================

  async bnrByLoanType(
    params: BnrReportParams,
  ): Promise<BreakdownRow[]> {
    const query =
      buildQueryParams(params);

    const response =
      await api.get(
        `/regulatory/bnr/by-loan-type?${query.toString()}`,
      );

    return asArray<BreakdownRow>(
      unwrapResponse<unknown>(
        response.data,
      ),
    );
  },

  // ==========================================================
  // BNR BY BRANCH
  // ==========================================================

  async bnrByBranch(
    params: BnrReportParams,
  ): Promise<BreakdownRow[]> {
    const query =
      buildQueryParams(params);

    const response =
      await api.get(
        `/regulatory/bnr/by-branch?${query.toString()}`,
      );

    return asArray<BreakdownRow>(
      unwrapResponse<unknown>(
        response.data,
      ),
    );
  },

  // ==========================================================
  // BNR BY GENDER
  // ==========================================================

  async bnrByGender(
    params: BnrReportParams,
  ): Promise<BreakdownRow[]> {
    const query =
      buildQueryParams(params);

    const response =
      await api.get(
        `/regulatory/bnr/by-gender?${query.toString()}`,
      );

    return asArray<BreakdownRow>(
      unwrapResponse<unknown>(
        response.data,
      ),
    );
  },

  // ==========================================================
  // BNR EXPORT
  // ==========================================================
  //
  // BACKEND:
  //
  // GET /api/regulatory/bnr/export?format=xlsx
  //
  // NOT:
  //
  // /regulatory/bnr/export/xlsx
  //
  // ==========================================================

  async bnrExport(
    format: ExportFormat,
    params: BnrReportParams,
  ): Promise<void> {
    const query =
      buildQueryParams({
        ...params,
        format,
      });

    const response =
      await api.get(
        `/regulatory/bnr/export?${query.toString()}`,
        {
          responseType: 'blob',
        },
      );

    downloadBlob(
      response.data,
      `bnr-report.${format}`,
    );
  },

  // ==========================================================
  // FINANCIAL SUMMARY
  // ==========================================================
  //
  // NOTE:
  // These endpoints are NOT present in the Java controller
  // you supplied:
  //
  // /regulatory/financial/summary
  //
  // Keep them only if another backend controller actually
  // implements them.
  // ==========================================================

  async financialSummary(
    params: FinancialReportParams,
  ): Promise<FinancialSummary> {
    const query =
      buildQueryParams(params);

    const response =
      await api.get(
        `/regulatory/financial/summary?${query.toString()}`,
      );

    return unwrapResponse<FinancialSummary>(
      response.data,
    );
  },

  async financialIncome(
    params: FinancialReportParams,
  ): Promise<FinancialBreakdownRow[]> {
    const query =
      buildQueryParams(params);

    const response =
      await api.get(
        `/regulatory/financial/income?${query.toString()}`,
      );

    return asArray<FinancialBreakdownRow>(
      unwrapResponse<unknown>(
        response.data,
      ),
    );
  },

  async financialExpenses(
    params: FinancialReportParams,
  ): Promise<FinancialBreakdownRow[]> {
    const query =
      buildQueryParams(params);

    const response =
      await api.get(
        `/regulatory/financial/expenses?${query.toString()}`,
      );

    return asArray<FinancialBreakdownRow>(
      unwrapResponse<unknown>(
        response.data,
      ),
    );
  },

  async financialCashFlow(
    params: FinancialReportParams,
  ): Promise<FinancialBreakdownRow[]> {
    const query =
      buildQueryParams(params);

    const response =
      await api.get(
        `/regulatory/financial/cash-flow?${query.toString()}`,
      );

    return asArray<FinancialBreakdownRow>(
      unwrapResponse<unknown>(
        response.data,
      ),
    );
  },

  async financialExport(
    format: ExportFormat,
    params: FinancialReportParams,
  ): Promise<void> {
    const query =
      buildQueryParams({
        ...params,
        format,
      });

    const response =
      await api.get(
        `/regulatory/financial/export?${query.toString()}`,
        {
          responseType: 'blob',
        },
      );

    downloadBlob(
      response.data,
      `financial-report.${format}`,
    );
  },

  // ==========================================================
  // CREDIT BUREAU PREVIEW
  // ==========================================================
  //
  // IMPORTANT:
  // The Java controller you supplied does NOT expose:
  //
  // GET /regulatory/credit-bureau/preview
  //
  // It exposes only:
  //
  // GET /regulatory/credit-bureau/export
  //
  // So this method will work only if another backend controller
  // implements the preview endpoint.
  // ==========================================================

  async creditBureauPreview(
    params: CreditBureauParams = {},
  ): Promise<CreditRecord[]> {
    const query =
      buildQueryParams(params);

    const response =
      await api.get(
        `/regulatory/credit-bureau/preview?${query.toString()}`,
      );

    return asArray<CreditRecord>(
      unwrapResponse<unknown>(
        response.data,
      ),
    );
  },

  // ==========================================================
  // CREDIT BUREAU EXPORT
  // ==========================================================
  //
  // BACKEND:
  //
  // GET /api/regulatory/credit-bureau/export?format=xlsx
  //
  // ==========================================================

  async creditBureauExport(
    format: ExportFormat,
    params: CreditBureauParams = {},
  ): Promise<void> {
    const query =
      buildQueryParams({
        ...params,
        format,
      });

    const response =
      await api.get(
        `/regulatory/credit-bureau/export?${query.toString()}`,
        {
          responseType: 'blob',
        },
      );

    downloadBlob(
      response.data,
      `credit-bureau-report.${format}`,
    );
  },

  // ==========================================================
  // API CLIENTS
  // ==========================================================
  //
  // THIS IS THE IMPORTANT FIX FOR:
  //
  // TypeError: e.map is not a function
  //
  // ==========================================================

  async listApiClients(): Promise<ApiClient[]> {
    const response =
      await api.get(
        `/regulatory/api-clients`,
      );

    const data =
      unwrapResponse<unknown>(
        response.data,
      );

    return asArray<ApiClient>(
      data,
    );
  },

  // ==========================================================
  // CREATE API CLIENT
  // ==========================================================

  async createApiClient(
    request: CreateApiClientRequest,
  ): Promise<CreateApiClientResponse> {
    const response =
      await api.post(
        `/regulatory/api-clients`,
        request,
      );

    return unwrapResponse<CreateApiClientResponse>(
      response.data,
    );
  },

  // ==========================================================
  // REVOKE API CLIENT
  // ==========================================================

  async revokeApiClient(
    id: number,
    reason?: string,
  ): Promise<void> {
    await api.post(
      `/regulatory/api-clients/${id}/revoke`,
      {
        reason:
          reason?.trim() ||
          undefined,
      },
    );
  },
};

// ============================================================
// DOWNLOAD HELPER
// ============================================================

function downloadBlob(
  blob: Blob,
  filename: string,
): void {
  const url =
    window.URL.createObjectURL(blob);

  const anchor =
    document.createElement('a');

  anchor.href = url;

  anchor.download = filename;

  document.body.appendChild(anchor);

  anchor.click();

  anchor.remove();

  window.URL.revokeObjectURL(url);
}
