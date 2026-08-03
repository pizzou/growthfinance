import API, { get, post } from './api';

/* ============================================================
   TYPES
   ============================================================ */

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
  period?: RegulatoryPeriod | string;
  from?: string;
  to?: string;
};

export type CreditBureauParams = {
  branchId?: number;
  from?: string;
  to?: string;
};

export type BreakdownRow = {
  label: string;
  count: number;
  amount: number;
};

export type BnrSummary = {
  organizationName?: string;
  bnrInstitutionCode?: string;
  reportPeriod?: string;
  periodStart?: string;
  periodEnd?: string;

  totalLoans?: number;
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

export type ApiClient = {
  id: number;
  name: string;
  clientType: 'BNR' | 'CREDIT_BUREAU';
  keyPrefix: string;
  active: boolean;
  contactEmail?: string;
  lastUsedAt?: string;
  revokedAt?: string;
  createdAt?: string;
};

export type CreateApiClientRequest = {
  name: string;
  clientType: 'BNR' | 'CREDIT_BUREAU';
  contactEmail?: string;
  description?: string;
  expiresAt?: string | null;
};

export type CreateApiClientResponse = {
  apiKey: string;
  client: ApiClient;
};


/* ============================================================
   RESPONSE NORMALIZATION
   ============================================================ */

/**
 * Some backend/API configurations return arrays directly:
 *
 * [
 *   { label: "Personal", count: 10, amount: 100000 }
 * ]
 *
 * while others may wrap the array:
 *
 * {
 *   data: [...]
 * }
 *
 * {
 *   rows: [...]
 * }
 *
 * {
 *   content: [...]
 * }
 *
 * This function guarantees that the frontend receives an array.
 */
function asArray<T>(value: unknown): T[] {
  if (Array.isArray(value)) {
    return value as T[];
  }

  if (
    value !== null &&
    typeof value === 'object'
  ) {
    const object =
      value as Record<string, unknown>;

    if (Array.isArray(object.data)) {
      return object.data as T[];
    }

    if (Array.isArray(object.rows)) {
      return object.rows as T[];
    }

    if (Array.isArray(object.content)) {
      return object.content as T[];
    }

    if (Array.isArray(object.items)) {
      return object.items as T[];
    }

    if (Array.isArray(object.results)) {
      return object.results as T[];
    }
  }

  return [];
}


/**
 * Some API clients return the useful response body
 * directly, while others return an Axios-style object.
 *
 * This safely unwraps common response wrappers.
 */
function unwrap<T>(value: unknown): T {
  if (
    value !== null &&
    typeof value === 'object'
  ) {
    const object =
      value as Record<string, unknown>;

    if (
      'data' in object &&
      object.data !== undefined
    ) {
      return object.data as T;
    }
  }

  return value as T;
}


/* ============================================================
   QUERY STRING
   ============================================================ */

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


/* ============================================================
   BNR
   ============================================================ */

export const regulatoryApi = {

  /**
   * BNR summary
   */
  bnrSummary: async (
    params: BnrReportParams
  ): Promise<BnrSummary> => {

    const response =
      await get(
        `/regulatory/bnr/summary?${qs(params)}`
      );

    return unwrap<BnrSummary>(
      response
    );
  },


  /**
   * BNR breakdown by loan type
   */
  bnrByLoanType: async (
    params: BnrReportParams
  ): Promise<BreakdownRow[]> => {

    const response =
      await get(
        `/regulatory/bnr/breakdown/loan-type?${qs(params)}`
      );

    return asArray<BreakdownRow>(
      unwrap<unknown>(response)
    );
  },


  /**
   * BNR breakdown by branch
   */
  bnrByBranch: async (
    params: BnrReportParams
  ): Promise<BreakdownRow[]> => {

    const response =
      await get(
        `/regulatory/bnr/breakdown/branch?${qs(params)}`
      );

    return asArray<BreakdownRow>(
      unwrap<unknown>(response)
    );
  },


  /**
   * BNR breakdown by gender
   */
  bnrByGender: async (
    params: BnrReportParams
  ): Promise<BreakdownRow[]> => {

    const response =
      await get(
        `/regulatory/bnr/breakdown/gender?${qs(params)}`
      );

    return asArray<BreakdownRow>(
      unwrap<unknown>(response)
    );
  },


  /**
   * BNR export
   */
  bnrExport: (
    format: ExportFormat,
    params: BnrReportParams
  ): Promise<void> => {

    return downloadFile(
      `/regulatory/bnr/export?format=${encodeURIComponent(format)}&${qs(params)}`,
      `bnr-summary.${format}`
    );
  },


  /* ==========================================================
     CREDIT BUREAU
     ========================================================== */


  /**
   * Credit bureau preview
   */
  creditBureauPreview: async (
    params: CreditBureauParams
  ): Promise<CreditRecord[]> => {

    const response =
      await get(
        `/regulatory/credit-bureau/preview?${qs(params)}`
      );

    return asArray<CreditRecord>(
      unwrap<unknown>(response)
    );
  },


  /**
   * Credit bureau export
   */
  creditBureauExport: (
    format: ExportFormat,
    params: CreditBureauParams
  ): Promise<void> => {

    return downloadFile(
      `/regulatory/credit-bureau/export?format=${encodeURIComponent(format)}&${qs(params)}`,
      `credit-bureau-export.${format}`
    );
  },


  /* ==========================================================
     API CLIENT MANAGEMENT
     ========================================================== */


  /**
   * List API clients
   */
  listApiClients: async (): Promise<ApiClient[]> => {

    const response =
      await get(
        '/regulatory/api-clients'
      );

    return asArray<ApiClient>(
      unwrap<unknown>(response)
    );
  },


  /**
   * Create API client
   */
  createApiClient: async (
    data: CreateApiClientRequest
  ): Promise<CreateApiClientResponse> => {

    const response =
      await post(
        '/regulatory/api-clients',
        data
      );

    return unwrap<CreateApiClientResponse>(
      response
    );
  },


  /**
   * Revoke API client
   */
  revokeApiClient: (
    id: number,
    reason?: string
  ): Promise<unknown> => {

    return post(
      `/regulatory/api-clients/${id}/revoke`,
      {
        reason,
      }
    );
  },


  /**
   * Convert API errors into a useful frontend message.
   */
  getErrorMessage: (
    error: unknown,
    fallback = 'Something went wrong.'
  ): string => {

    if (
      error !== null &&
      typeof error === 'object'
    ) {

      const object =
        error as Record<string, unknown>;

      const response =
        object.response;

      if (
        response !== null &&
        typeof response === 'object'
      ) {

        const responseObject =
          response as Record<string, unknown>;

        const data =
          responseObject.data;

        if (
          data !== null &&
          typeof data === 'object'
        ) {

          const dataObject =
            data as Record<string, unknown>;

          if (
            typeof dataObject.error === 'string'
          ) {
            return dataObject.error;
          }

          if (
            typeof dataObject.message === 'string'
          ) {
            return dataObject.message;
          }
        }

        if (
          typeof responseObject.statusText === 'string' &&
          responseObject.statusText
        ) {
          return responseObject.statusText;
        }
      }

      if (
        typeof object.message === 'string' &&
        object.message
      ) {
        return object.message;
      }
    }

    if (error instanceof Error) {
      return error.message;
    }

    return fallback;
  },

};


/* ============================================================
   FILE DOWNLOAD
   ============================================================ */

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
    () => {
      URL.revokeObjectURL(url);
    },
    60000
  );
}