
import api from '@/services/api';


// ============================================================
// REPORT TYPES
// ============================================================

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
// BNR REPORT TYPES
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
// FINANCIAL REPORT
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
// API CLIENT TYPES
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

  /**
   * ISO date/time.
   *
   * null means no expiration.
   */
  expiresAt?: string | null;
};


export type CreateApiClientResponse = {
  apiKey: string;

  client: ApiClient;
};


// ============================================================
// GENERIC QUERY PARAMETER HELPER
// ============================================================

function buildQueryParams<T extends object>(
  params: T
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
        String(value)
      );
    }
  );

  return searchParams;
}


// ============================================================
// ERROR HANDLER
// ============================================================

function getErrorMessage(
  error: unknown,
  fallback: string
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
    params: BnrReportParams
  ): Promise<BnrSummary> {

    const query =
      buildQueryParams(params);

    const response =
      await api.get<BnrSummary>(
        `/regulatory/bnr/summary?${query.toString()}`
      );

    return response.data;
  },


  // ==========================================================
  // BNR BY LOAN TYPE
  // ==========================================================

  async bnrByLoanType(
    params: BnrReportParams
  ): Promise<BreakdownRow[]> {

    const query =
      buildQueryParams(params);

    const response =
      await api.get<BreakdownRow[]>(
        `/regulatory/bnr/by-loan-type?${query.toString()}`
      );

    return response.data || [];
  },


  // ==========================================================
  // BNR BY BRANCH
  // ==========================================================

  async bnrByBranch(
    params: BnrReportParams
  ): Promise<BreakdownRow[]> {

    const query =
      buildQueryParams(params);

    const response =
      await api.get<BreakdownRow[]>(
        `/regulatory/bnr/by-branch?${query.toString()}`
      );

    return response.data || [];
  },


  // ==========================================================
  // BNR BY GENDER
  // ==========================================================

  async bnrByGender(
    params: BnrReportParams
  ): Promise<BreakdownRow[]> {

    const query =
      buildQueryParams(params);

    const response =
      await api.get<BreakdownRow[]>(
        `/regulatory/bnr/by-gender?${query.toString()}`
      );

    return response.data || [];
  },


  // ==========================================================
  // BNR EXPORT
  // ==========================================================

  async bnrExport(
    format: ExportFormat,
    params: BnrReportParams
  ): Promise<void> {

    const query =
      buildQueryParams(params);

    const response =
      await api.get(
        `/regulatory/bnr/export/${format}?${query.toString()}`,
        {
          responseType: 'blob',
        }
      );

    downloadBlob(
      response.data,
      `bnr-report.${format}`
    );
  },


  // ==========================================================
  // FINANCIAL SUMMARY
  // ==========================================================

  async financialSummary(
    params: FinancialReportParams
  ): Promise<FinancialSummary> {

    const query =
      buildQueryParams(params);

    const response =
      await api.get<FinancialSummary>(
        `/regulatory/financial/summary?${query.toString()}`
      );

    return response.data;
  },


  // ==========================================================
  // FINANCIAL INCOME
  // ==========================================================

  async financialIncome(
    params: FinancialReportParams
  ): Promise<FinancialBreakdownRow[]> {

    const query =
      buildQueryParams(params);

    const response =
      await api.get<FinancialBreakdownRow[]>(
        `/regulatory/financial/income?${query.toString()}`
      );

    return response.data || [];
  },


  // ==========================================================
  // FINANCIAL EXPENSES
  // ==========================================================

  async financialExpenses(
    params: FinancialReportParams
  ): Promise<FinancialBreakdownRow[]> {

    const query =
      buildQueryParams(params);

    const response =
      await api.get<FinancialBreakdownRow[]>(
        `/regulatory/financial/expenses?${query.toString()}`
      );

    return response.data || [];
  },


  // ==========================================================
  // FINANCIAL CASH FLOW
  // ==========================================================

  async financialCashFlow(
    params: FinancialReportParams
  ): Promise<FinancialBreakdownRow[]> {

    const query =
      buildQueryParams(params);

    const response =
      await api.get<FinancialBreakdownRow[]>(
        `/regulatory/financial/cash-flow?${query.toString()}`
      );

    return response.data || [];
  },


  // ==========================================================
  // FINANCIAL EXPORT
  // ==========================================================

  async financialExport(
    format: ExportFormat,
    params: FinancialReportParams
  ): Promise<void> {

    const query =
      buildQueryParams(params);

    const response =
      await api.get(
        `/regulatory/financial/export/${format}?${query.toString()}`,
        {
          responseType: 'blob',
        }
      );

    downloadBlob(
      response.data,
      `financial-report.${format}`
    );
  },


  // ==========================================================
  // CREDIT BUREAU PREVIEW
  // ==========================================================

  async creditBureauPreview(
    params: CreditBureauParams = {}
  ): Promise<CreditRecord[]> {

    const query =
      buildQueryParams(params);

    const response =
      await api.get<CreditRecord[]>(
        `/regulatory/credit-bureau/preview?${query.toString()}`
      );

    return response.data || [];
  },


  // ==========================================================
  // CREDIT BUREAU EXPORT
  // ==========================================================

  async creditBureauExport(
    format: ExportFormat,
    params: CreditBureauParams = {}
  ): Promise<void> {

    const query =
      buildQueryParams(params);

    const response =
      await api.get(
        `/regulatory/credit-bureau/export/${format}?${query.toString()}`,
        {
          responseType: 'blob',
        }
      );

    downloadBlob(
      response.data,
      `credit-bureau-report.${format}`
    );
  },


  // ==========================================================
  // API CLIENTS
  // ==========================================================

  async listApiClients(): Promise<ApiClient[]> {

    const response =
      await api.get<ApiClient[]>(
        `/regulatory/api-clients`
      );

    return response.data || [];
  },


  // ==========================================================
  // CREATE API CLIENT
  // ==========================================================

  async createApiClient(
    request: CreateApiClientRequest
  ): Promise<CreateApiClientResponse> {

    const response =
      await api.post<CreateApiClientResponse>(
        `/regulatory/api-clients`,
        request
      );

    return response.data;
  },


  // ==========================================================
  // REVOKE API CLIENT
  // ==========================================================

  async revokeApiClient(
    id: number,
    reason?: string
  ): Promise<void> {

    await api.post(
      `/regulatory/api-clients/${id}/revoke`,
      {
        reason:
          reason?.trim() || undefined,
      }
    );
  },
};


// ============================================================
// DOWNLOAD HELPER
// ============================================================

function downloadBlob(
  blob: Blob,
  filename: string
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
