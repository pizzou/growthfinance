
import axios, {
  AxiosError,
  AxiosHeaders,
  AxiosInstance,
  AxiosRequestConfig,
} from 'axios';

/**
 * ============================================================
 * API CONFIGURATION
 * ============================================================
 */

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_URL ||
  'http://localhost:8080/api';

const API: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 20000,
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * ============================================================
 * REQUEST INTERCEPTOR
 * ============================================================
 *
 * Every authenticated request receives:
 *
 * Authorization: Bearer <JWT>
 *
 * This includes:
 *
 * - BNR
 * - Credit Bureau
 * - Loans
 * - Borrowers
 * - Accounting
 * - Compliance
 * - etc.
 *
 * ============================================================
 */

API.interceptors.request.use(
  (config) => {
    if (typeof window !== 'undefined') {
      const token = localStorage.getItem('token');

      if (token) {
        /**
         * Axios 1.x expects AxiosHeaders here.
         *
         * Using AxiosHeaders.set() avoids:
         *
         * Type 'string | number | true | AxiosHeaders | string[]'
         * is not assignable to type 'string'
         *
         * errors.
         */
        const headers =
          config.headers instanceof AxiosHeaders
            ? config.headers
            : new AxiosHeaders(config.headers);

        headers.set(
          'Authorization',
          `Bearer ${token}`,
        );

        config.headers = headers;
      }
    }

    return config;
  },
  (error) => Promise.reject(error),
);

/**
 * ============================================================
 * RESPONSE INTERCEPTOR
 * ============================================================
 */

API.interceptors.response.use(
  (response) => response,

  (error: AxiosError<any>) => {
    const status = error.response?.status;
    const responseData = error.response?.data;

    /**
     * --------------------------------------------------------
     * 401 = AUTHENTICATION FAILURE
     * --------------------------------------------------------
     *
     * Only remove the token for 401.
     *
     * A 403 means the user is authenticated but does not have
     * permission, so we must NOT log them out automatically.
     */
    if (
      status === 401 &&
      typeof window !== 'undefined'
    ) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');

      window.location.href = '/login';
    }

    const message =
      responseData?.error ||
      responseData?.message ||
      error.message ||
      'An error occurred';

    const err = new Error(message) as Error & {
      status?: number;
      data?: unknown;
    };

    err.status = status;
    err.data = responseData;

    return Promise.reject(err);
  },
);

/**
 * ============================================================
 * RESPONSE UNWRAPPER
 * ============================================================
 *
 * Supports backend responses such as:
 *
 * {
 *   success: true,
 *   data: {...}
 * }
 *
 * and also plain responses.
 * ============================================================
 */

const unwrap = (
  body: unknown,
): unknown => {
  if (
    body &&
    typeof body === 'object' &&
    'data' in body
  ) {
    return (
      body as Record<string, unknown>
    ).data;
  }

  return body;
};

/**
 * ============================================================
 * GENERIC HTTP HELPERS
 * ============================================================
 */

export const get = (
  url: string,
  config?: AxiosRequestConfig,
): Promise<any> =>
  API.get(url, config).then((response) =>
    unwrap(response.data),
  );

export const post = (
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<any> =>
  API.post(url, data, config).then((response) =>
    unwrap(response.data),
  );

export const put = (
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<any> =>
  API.put(url, data, config).then((response) =>
    unwrap(response.data),
  );

export const del = (
  url: string,
  config?: AxiosRequestConfig,
): Promise<any> =>
  API.delete(url, config).then((response) =>
    unwrap(response.data),
  );

/**
 * ============================================================
 * AUTH API
 * ============================================================
 */

export const authApi = {
  login: (
    email: string,
    password: string,
    mfaCode?: string,
    otp?: string,
  ) =>
    post('/auth/login', {
      email,
      password,
      mfaCode,
      otp,
    }),

  register: (
    data: unknown,
  ) =>
    post(
      '/auth/register',
      data,
    ),

  me: () =>
    get('/auth/me'),
};

/**
 * ============================================================
 * LOAN API
 * ============================================================
 */

export const loanApi = {
  list: (
    page = 0,
    size = 20,
    status = '',
    type = '',
  ) =>
    get(
      `/loans?page=${page}&size=${size}` +
        `${
          status
            ? `&status=${encodeURIComponent(status)}`
            : ''
        }` +
        `${
          type
            ? `&type=${encodeURIComponent(type)}`
            : ''
        }`,
    ),

  get: (
    id: number,
  ) =>
    get(
      `/loans/${id}`,
    ),

  create: (
    data: unknown,
  ) =>
    post(
      '/loans',
      data,
    ),

  approve: (
    id: number,
    notes = '',
    interestRate?: number,
  ) =>
    post(
      `/loans/${id}/approve`,
      {
        notes,
        interestRate:
          interestRate != null
            ? String(interestRate)
            : undefined,
      },
    ),

  reject: (
    id: number,
    reason: string,
  ) =>
    post(
      `/loans/${id}/reject`,
      {
        reason,
      },
    ),

  disburse: (
    id: number,
    method: string,
  ) =>
    post(
      `/loans/${id}/disburse`,
      {
        disbursementMethod: method,
      },
    ),

  updateStatus: (
    id: number,
    status: string,
    notes?: string,
  ) =>
    post(
      `/loans/${id}/status`,
      {
        status,
        notes,
      },
    ),

  dashboard: () =>
    get('/loans/dashboard'),

  schedule: (
    id: number,
  ) =>
    get(
      `/loans/${id}/schedule`,
    ),

  risk: (
    id: number,
  ) =>
    get(
      `/loans/${id}/risk`,
    ),

  documentRequirements: (
    id: number,
  ) =>
    get(
      `/loans/${id}/document-requirements`,
    ),

  restructure: (
    id: number,
    data: unknown,
  ) =>
    post(
      `/loans/${id}/restructure`,
      data,
    ),

  writeOff: (
    id: number,
    reason: string,
  ) =>
    post(
      `/loans/${id}/write-off`,
      {
        reason,
      },
    ),

  moratorium: (
    id: number,
    data: unknown,
  ) =>
    post(
      `/loans/${id}/moratorium`,
      data,
    ),

  getComments: (
    id: number,
  ) =>
    get(
      `/loans/${id}/comments`,
    ),

  addComment: (
    id: number,
    message: string,
    visibleToApplicant = true,
  ) =>
    post(
      `/loans/${id}/comments`,
      {
        message,
        visibleToApplicant,
      },
    ),
};

/**
 * ============================================================
 * BRANCH API
 * ============================================================
 */

export const branchApi = {
  list: () =>
    get('/branches'),
};

/**
 * ============================================================
 * EXPENSE API
 * ============================================================
 */

export const expenseApi = {
  list: (
    params: {
      page?: number;
      size?: number;
      category?: string;
      branchId?: number;
      from?: string;
      to?: string;
    } = {},
  ) => {
    const query =
      new URLSearchParams();

    query.set(
      'page',
      String(params.page ?? 0),
    );

    query.set(
      'size',
      String(params.size ?? 20),
    );

    if (params.category) {
      query.set(
        'category',
        params.category,
      );
    }

    if (
      params.branchId != null
    ) {
      query.set(
        'branchId',
        String(params.branchId),
      );
    }

    if (params.from) {
      query.set(
        'from',
        params.from,
      );
    }

    if (params.to) {
      query.set(
        'to',
        params.to,
      );
    }

    return get(
      `/expenses?${query.toString()}`,
    );
  },

  get: (
    id: number,
  ) =>
    get(
      `/expenses/${id}`,
    ),

  summary: (
    from?: string,
    to?: string,
  ) =>
    get(
      `/expenses/summary${
        from && to
          ? `?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`
          : ''
      }`,
    ),

  void: (
    id: number,
    reason?: string,
  ) =>
    post(
      `/expenses/${id}/void`,
      {
        reason,
      },
    ),

  receiptUrl: (
    id: number,
  ) =>
    `${API_BASE_URL}/expenses/${id}/receipt`,

  create: (data: {
    expenseDate: string;
    category: string;
    amount: number;
    paymentAccountId: number;
    branchId?: number;
    description?: string;
    paymentMethod: string;
    paymentProvider?: string;
    paymentPhoneNumber?: string;
    paymentTransactionReference?: string;
    paymentCode?: string;
    cardBrand?: string;
    cardLastFour?: string;
    cardAuthorizationCode?: string;
    chequeNumber?: string;
    paymentNotes?: string;
    receipt?: File | null;
  }) => {
    const form =
      new FormData();

    form.append(
      'expenseDate',
      data.expenseDate,
    );

    form.append(
      'category',
      data.category,
    );

    form.append(
      'amount',
      String(data.amount),
    );

    form.append(
      'paymentAccountId',
      String(data.paymentAccountId),
    );

    if (
      data.branchId != null
    ) {
      form.append(
        'branchId',
        String(data.branchId),
      );
    }

    if (data.description) {
      form.append(
        'description',
        data.description,
      );
    }

    form.append(
      'paymentMethod',
      data.paymentMethod,
    );

    if (data.paymentProvider) {
      form.append(
        'paymentProvider',
        data.paymentProvider,
      );
    }

    if (data.paymentPhoneNumber) {
      form.append(
        'paymentPhoneNumber',
        data.paymentPhoneNumber,
      );
    }

    if (
      data.paymentTransactionReference
    ) {
      form.append(
        'paymentTransactionReference',
        data.paymentTransactionReference,
      );
    }

    if (data.paymentCode) {
      form.append(
        'paymentCode',
        data.paymentCode,
      );
    }

    if (data.cardBrand) {
      form.append(
        'cardBrand',
        data.cardBrand,
      );
    }

    if (data.cardLastFour) {
      form.append(
        'cardLastFour',
        data.cardLastFour,
      );
    }

    if (
      data.cardAuthorizationCode
    ) {
      form.append(
        'cardAuthorizationCode',
        data.cardAuthorizationCode,
      );
    }

    if (data.chequeNumber) {
      form.append(
        'chequeNumber',
        data.chequeNumber,
      );
    }

    if (data.paymentNotes) {
      form.append(
        'paymentNotes',
        data.paymentNotes,
      );
    }

    if (data.receipt) {
      form.append(
        'receipt',
        data.receipt,
      );
    }

    return API.post(
      '/expenses',
      form,
      {
        headers: {
          'Content-Type':
            'multipart/form-data',
        },
      },
    ).then((response) =>
      unwrap(response.data),
    );
  },
};

/**
 * ============================================================
 * PAYMENT API
 * ============================================================
 */

export const paymentApi = {
  record: (
    loanId: number,
    data: unknown,
    idempotencyKey?: string,
  ) =>
    API.post(
      `/loans/${loanId}/payments`,
      data,
      {
        headers: idempotencyKey
          ? {
              'Idempotency-Key':
                idempotencyKey,
            }
          : {},
      },
    ).then((response) =>
      unwrap(response.data),
    ),

  schedule: (
    loanId: number,
  ) =>
    get(
      `/loans/${loanId}/payments`,
    ),
};

/**
 * ============================================================
 * BORROWER API
 * ============================================================
 */

export const borrowerApi = {
  list: (
    page = 0,
    size = 20,
    q = '',
  ) =>
    get(
      `/borrowers?page=${page}&size=${size}` +
        `${
          q
            ? `&q=${encodeURIComponent(q)}`
            : ''
        }`,
    ),

  get: (
    id: number,
  ) =>
    get(
      `/borrowers/${id}`,
    ),

  getDetails: (
    id: number,
  ) =>
    get(
      `/borrowers/${id}/details`,
    ),

  create: (
    data: unknown,
  ) =>
    post(
      '/borrowers',
      data,
    ),

  update: (
    id: number,
    data: unknown,
  ) =>
    put(
      `/borrowers/${id}`,
      data,
    ),
};

/**
 * ============================================================
 * COMPLIANCE API
 * ============================================================
 */

export const complianceApi = {
  screen: (
    borrowerId: number,
  ) =>
    post(
      `/compliance/borrowers/${borrowerId}/screen`,
    ),

  history: (
    borrowerId: number,
  ) =>
    get(
      `/compliance/borrowers/${borrowerId}/history`,
    ),

  status: (
    borrowerId: number,
  ) =>
    get(
      `/compliance/borrowers/${borrowerId}/status`,
    ),

  pendingReviews: () =>
    get(
      '/compliance/pending-reviews',
    ),

  decide: (
    checkId: number,
    data: unknown,
  ) =>
    post(
      `/compliance/checks/${checkId}/decide`,
      data,
    ),
};

/**
 * ============================================================
 * MFA API
 * ============================================================
 */

export const mfaApi = {
  setup: () =>
    post('/mfa/setup'),

  confirm: (
    code: string,
  ) =>
    post(
      '/mfa/confirm',
      {
        code,
      },
    ),

  disable: () =>
    post('/mfa/disable'),
};

/**
 * ============================================================
 * BULK API
 * ============================================================
 */

export const bulkApi = {
  disburse: (
    loanIds: number[],
    method = 'BANK_TRANSFER',
  ) =>
    post(
      '/bulk/disburse',
      {
        loanIds,
        disbursementMethod: method,
      },
    ),
};

/**
 * ============================================================
 * ORGANIZATION API
 * ============================================================
 */

export const orgApi = {
  me: () =>
    get('/organizations/me'),

  update: (
    data: unknown,
  ) =>
    put(
      '/organizations/me',
      data,
    ),

  users: () =>
    get(
      '/organizations/me/users',
    ),
};

/**
 * ============================================================
 * WEBHOOK API
 * ============================================================
 */

export const webhookApi = {
  list: () =>
    get('/webhooks'),

  create: (
    data: unknown,
  ) =>
    post(
      '/webhooks',
      data,
    ),

  remove: (
    id: number,
  ) =>
    del(
      `/webhooks/${id}`,
    ),
};

/**
 * ============================================================
 * CURRENCY API
 * ============================================================
 */

export const currencyApi = {
  rates: (
    base = 'USD',
  ) =>
    get(
      `/currencies?base=${encodeURIComponent(base)}`,
    ),

  convert: (
    from: string,
    to: string,
    amount: number,
  ) =>
    get(
      `/currencies/convert?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&amount=${amount}`,
    ),

  supported: () =>
    get(
      '/currencies/supported',
    ),

  status: () =>
    get(
      '/currencies/status',
    ),

  refresh: () =>
    post(
      '/currencies/refresh',
    ),
};

/**
 * ============================================================
 * PRIVACY API
 * ============================================================
 */

export const privacyApi = {
  exportData: (
    id: number,
  ) =>
    get(
      `/privacy/borrowers/${id}/export`,
    ),

  eraseData: (
    id: number,
  ) =>
    del(
      `/privacy/borrowers/${id}/erase`,
    ),
};

/**
 * ============================================================
 * CREDIT BUREAU API
 * ============================================================
 */

export const creditBureauApi = {
  check: (
    borrowerId: number,
  ) =>
    post(
      `/credit-bureau/borrowers/${borrowerId}/check`,
    ),

  history: (
    borrowerId: number,
  ) =>
    get(
      `/credit-bureau/borrowers/${borrowerId}/history`,
    ),

  latest: (
    borrowerId: number,
  ) =>
    get(
      `/credit-bureau/borrowers/${borrowerId}/latest`,
    ),

  reportForLoan: (
    loanId: number,
  ) =>
    get(
      `/credit-bureau/loans/${loanId}/report`,
    ),

  retryReport: (
    loanId: number,
  ) =>
    post(
      `/credit-bureau/loans/${loanId}/report/retry`,
    ),
};

/**
 * ============================================================
 * E-SIGNATURE API
 * ============================================================
 */

export const esignatureApi = {
  initiate: (
    loanId: number,
    documentType = 'LOAN_AGREEMENT',
  ) =>
    post(
      `/loans/${loanId}/esignature/initiate`,
      {
        documentType,
      },
    ),

  history: (
    loanId: number,
  ) =>
    get(
      `/loans/${loanId}/esignature`,
    ),
};

/**
 * ============================================================
 * ACCOUNTING API
 * ============================================================
 */

export const accountingApi = {
  chartOfAccounts: () =>
    get(
      '/accounting/chart-of-accounts',
    ),

  createAccount: (data: {
    code: string;
    name: string;
    type: string;
    normalBalance: string;
  }) =>
    post(
      '/accounting/chart-of-accounts',
      data,
    ),

  updateAccount: (
    id: number,
    data: {
      name?: string;
      active?: boolean;
    },
  ) =>
    put(
      `/accounting/chart-of-accounts/${id}`,
      data,
    ),

  journal: () =>
    get(
      '/accounting/journal',
    ),

  reverseEntry: (
    id: number,
    reason?: string,
  ) =>
    post(
      `/accounting/journal/${id}/reverse`,
      {
        reason,
      },
    ),

  ledger: (
    accountId: number,
  ) =>
    get(
      `/accounting/ledger/${accountId}`,
    ),

  trialBalance: () =>
    get(
      '/accounting/trial-balance',
    ),

  balanceSheet: () =>
    get(
      '/accounting/balance-sheet',
    ),

  profitAndLoss: (
    from?: string,
    to?: string,
  ) =>
    get(
      `/accounting/profit-and-loss${
        from && to
          ? `?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`
          : ''
      }`,
    ),

  cashFlow: (
    from?: string,
    to?: string,
  ) =>
    get(
      `/accounting/cash-flow${
        from && to
          ? `?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`
          : ''
      }`,
    ),

  branchSummary: (
    from?: string,
    to?: string,
  ) =>
    get(
      `/accounting/branch-summary${
        from && to
          ? `?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`
          : ''
      }`,
    ),
};

/**
 * ============================================================
 * BANK ACCOUNT API
 * ============================================================
 */

export const bankAccountApi = {
  list: () =>
    get(
      '/bank-accounts',
    ),

  create: (data: {
    name: string;
    accountType: string;
    bankName?: string;
    accountNumber?: string;
    openingBalance?: number;
    branchId?: number;
  }) =>
    post(
      '/bank-accounts',
      data,
    ),

  recordTransaction: (
    id: number,
    data: {
      type: string;
      amount: number;
      counterAccountId: number;
      description?: string;
    },
  ) =>
    post(
      `/bank-accounts/${id}/transactions`,
      data,
    ),

  transfer: (data: {
    fromAccountId: number;
    toAccountId: number;
    amount: number;
    description?: string;
  }) =>
    post(
      '/bank-accounts/transfer',
      data,
    ),
};

/**
 * ============================================================
 * CONTACT MESSAGE API
 * ============================================================
 */

export const contactMessageApi = {
  list: () =>
    get(
      '/contact-messages',
    ),

  unreadCount: () =>
    get(
      '/contact-messages/unread-count',
    ),

  markRead: (
    id: number,
  ) =>
    post(
      `/contact-messages/${id}/read`,
    ),

  delete: (
    id: number,
  ) =>
    del(
      `/contact-messages/${id}`,
    ),
};

/**
 * ============================================================
 * PUBLIC API
 * ============================================================
 */

export const publicApi = {
  getTenant: (
    slug: string,
  ) =>
    get(
      `/public/tenant/${encodeURIComponent(slug)}`,
    ),

  getProducts: (
    slug: string,
  ) =>
    get(
      `/public/tenant/${encodeURIComponent(slug)}/products`,
    ),

  apply: (
    data: unknown,
  ) =>
    post(
      '/public/loan-application',
      data,
    ),

  trackApplication: (
    reference: string,
    phone: string,
  ) =>
    get(
      `/public/applications/${encodeURIComponent(reference.trim())}/status?phone=${encodeURIComponent(phone.trim())}`,
    ),

  trackDashboard: (
    reference: string,
    phone: string,
  ) =>
    post(
      '/public/dashboard',
      {
        reference: reference.trim(),
        phone: phone.trim(),
      },
    ),

  initiatePayment: (
    reference: string,
    phone: string,
    data: {
      amount?: number;
      paymentMethod:
        | 'MOBILE_MONEY'
        | 'CARD'
        | 'BANK_TRANSFER';
      phoneNumber?: string;
      network?: string;
      cardNumber?: string;
      cardCvv?: string;
      cardExpiryMonth?: string;
      cardExpiryYear?: string;
      accountNumber?: string;
      bankCode?: string;
      email?: string;
    },
  ) =>
    post(
      `/public/applications/${encodeURIComponent(reference.trim())}/payments/initiate?phone=${encodeURIComponent(phone.trim())}`,
      data,
    ),

  trackComments: (
    reference: string,
    phone: string,
  ) =>
    get(
      `/public/applications/${encodeURIComponent(reference.trim())}/comments?phone=${encodeURIComponent(phone.trim())}`,
    ),

  listDocuments: (
    reference: string,
    phone: string,
  ) =>
    get(
      `/public/applications/${encodeURIComponent(reference.trim())}/documents?phone=${encodeURIComponent(phone.trim())}`,
    ),

  downloadDocument: (
    reference: string,
    phone: string,
    doc:
      | 'agreement'
      | 'schedule'
      | 'receipt',
  ) =>
    API.get(
      `/public/applications/${encodeURIComponent(reference.trim())}/documents/${doc}.pdf?phone=${encodeURIComponent(phone.trim())}`,
      {
        responseType: 'blob',
      },
    ),

  deleteDocument: (
    reference: string,
    phone: string,
    fileId: number,
  ) =>
    del(
      `/public/applications/${encodeURIComponent(reference.trim())}/documents/${fileId}?phone=${encodeURIComponent(phone.trim())}`,
    ),

  uploadDocument: (
    reference: string,
    phone: string,
    documentType: string,
    file: File | Blob,
    fileName?: string,
  ) => {
    const form =
      new FormData();

    form.append(
      'phone',
      phone.trim(),
    );

    form.append(
      'documentType',
      documentType,
    );

    /**
     * --------------------------------------------------------
     * FIX:
     *
     * File has .name
     * Blob does not have .name
     *
     * Therefore we explicitly check whether the object has
     * a name property before accessing it.
     * --------------------------------------------------------
     */

    const resolvedFileName =
      fileName ||
      (
        'name' in file &&
        typeof file.name === 'string'
          ? file.name
          : 'upload.jpg'
      );

    form.append(
      'file',
      file,
      resolvedFileName,
    );

    return API.post(
      `/public/applications/${encodeURIComponent(reference.trim())}/documents`,
      form,
      {
        headers: {
          'Content-Type':
            'multipart/form-data',
        },
      },
    ).then((response) =>
      unwrap(response.data),
    );
  },
};

/**
 * ============================================================
 * IMPORT API
 * ============================================================
 */

export const importApi = {
  template: () =>
    API.get(
      '/import/legacy-loans/template',
      {
        responseType: 'blob',
      },
    ),

  preview: (
    file: File,
  ) => {
    const form =
      new FormData();

    form.append(
      'file',
      file,
    );

    return API.post(
      '/import/legacy-loans/preview',
      form,
      {
        headers: {
          'Content-Type':
            'multipart/form-data',
        },
      },
    ).then((response) =>
      unwrap(response.data),
    );
  },

  commit: (
    file: File,
  ) => {
    const form =
      new FormData();

    form.append(
      'file',
      file,
    );

    return API.post(
      '/import/legacy-loans/commit',
      form,
      {
        headers: {
          'Content-Type':
            'multipart/form-data',
        },
      },
    ).then((response) =>
      unwrap(response.data),
    );
  },

  batches: () =>
    get(
      '/import/legacy-loans/batches',
    ),
};

/**
 * ============================================================
 * REGULATORY / BNR API
 * ============================================================
 *
 * IMPORTANT:
 *
 * This is ONLY the frontend API client.
 *
 * It is NOT RegulatoryService.java.
 *
 * Frontend:
 *
 * regulatoryApi.summary()
 *       |
 *       v
 * GET /api/regulatory/bnr/summary
 *       |
 *       v
 * Backend RegulatoryController
 *       |
 *       v
 * Backend RegulatoryService
 *
 * Therefore:
 *
 * regulatoryApi.ts
 *     = frontend HTTP client
 *
 * RegulatoryService.java
 *     = backend business/service layer
 *
 * ============================================================
 */

export const regulatoryApi = {
  /**
   * ----------------------------------------------------------
   * BNR SUMMARY
   * ----------------------------------------------------------
   */

  summary: (
    period = 'MONTHLY',
  ) =>
    get(
      `/regulatory/bnr/summary?period=${encodeURIComponent(period)}`,
    ),

  /**
   * ----------------------------------------------------------
   * BNR BY LOAN TYPE
   * ----------------------------------------------------------
   */

  byLoanType: (
    period = 'MONTHLY',
  ) =>
    get(
      `/regulatory/bnr/by-loan-type?period=${encodeURIComponent(period)}`,
    ),

  /**
   * ----------------------------------------------------------
   * BNR BY GENDER
   * ----------------------------------------------------------
   */

  byGender: (
    period = 'MONTHLY',
  ) =>
    get(
      `/regulatory/bnr/by-gender?period=${encodeURIComponent(period)}`,
    ),

  /**
   * ----------------------------------------------------------
   * BNR BY BRANCH
   * ----------------------------------------------------------
   */

  byBranch: (
    period = 'MONTHLY',
  ) =>
    get(
      `/regulatory/bnr/by-branch?period=${encodeURIComponent(period)}`,
    ),

  /**
   * ----------------------------------------------------------
   * LOAN TYPE BREAKDOWN
   * ----------------------------------------------------------
   */

  loanTypeBreakdown: (
    period = 'MONTHLY',
  ) =>
    get(
      `/regulatory/bnr/breakdown/loan-type?period=${encodeURIComponent(period)}`,
    ),

  /**
   * ----------------------------------------------------------
   * GENDER BREAKDOWN
   * ----------------------------------------------------------
   */

  genderBreakdown: (
    period = 'MONTHLY',
  ) =>
    get(
      `/regulatory/bnr/breakdown/gender?period=${encodeURIComponent(period)}`,
    ),

  /**
   * ----------------------------------------------------------
   * BRANCH BREAKDOWN
   * ----------------------------------------------------------
   */

  branchBreakdown: (
    period = 'MONTHLY',
  ) =>
    get(
      `/regulatory/bnr/breakdown/branch?period=${encodeURIComponent(period)}`,
    ),

  /**
   * ----------------------------------------------------------
   * FINANCIAL STATEMENT
   * ----------------------------------------------------------
   */

  financialStatement: (
    organizationId: number,
    branchId: number | null,
    period: string,
    startDate: string,
    endDate: string,
  ) =>
    get(
      `/regulatory/bnr/financial-statement` +
        `?organizationId=${organizationId}` +
        `${
          branchId != null
            ? `&branchId=${branchId}`
            : ''
        }` +
        `&period=${encodeURIComponent(period)}` +
        `&startDate=${encodeURIComponent(startDate)}` +
        `&endDate=${encodeURIComponent(endDate)}`,
    ),

  /**
   * ----------------------------------------------------------
   * CREDIT BUREAU PREVIEW
   * ----------------------------------------------------------
   *
   * Backend endpoint from your error:
   *
   * GET /api/regulatory/credit-bureau/preview
   *
   * The JWT interceptor automatically adds:
   *
   * Authorization: Bearer <token>
   *
   * ----------------------------------------------------------
   */

  creditBureauPreview: (
    queryParams?: Record<
      string,
      string | number | boolean | null | undefined
    >,
  ) => {
    const query =
      new URLSearchParams();

    if (queryParams) {
      Object.entries(
        queryParams,
      ).forEach(
        ([key, value]) => {
          if (
            value !== null &&
            value !== undefined
          ) {
            query.set(
              key,
              String(value),
            );
          }
        },
      );
    }

    const queryString =
      query.toString();

    return get(
      `/regulatory/credit-bureau/preview${
        queryString
          ? `?${queryString}`
          : ''
      }`,
    );
  },

  /**
   * ----------------------------------------------------------
   * CREDIT BUREAU EXPORT
   * ----------------------------------------------------------
   *
   * Backend endpoint:
   *
   * GET /api/regulatory/credit-bureau/export
   *
   * Example:
   *
   * /api/regulatory/credit-bureau/export?format=pdf
   *
   * ----------------------------------------------------------
   */

  creditBureauExport: (
    format = 'pdf',
    queryParams?: Record<
      string,
      string | number | boolean | null | undefined
    >,
  ) => {
    const query =
      new URLSearchParams();

    query.set(
      'format',
      format,
    );

    if (queryParams) {
      Object.entries(
        queryParams,
      ).forEach(
        ([key, value]) => {
          if (
            value !== null &&
            value !== undefined
          ) {
            query.set(
              key,
              String(value),
            );
          }
        },
      );
    }

    return API.get(
      `/regulatory/credit-bureau/export?${query.toString()}`,
      {
        responseType: 'blob',
      },
    );
  },
};

/**
 * ============================================================
 * DEFAULT EXPORT
 * ============================================================
 */

export default API;

/**
 * ============================================================
 * NAMED API EXPORT
 * ============================================================
 */

export { API };
