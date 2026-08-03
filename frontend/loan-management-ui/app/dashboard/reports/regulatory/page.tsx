'use client';

import React, {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';

import {
  regulatoryApi,
  type BnrFinancialStatementReport,
  type BnrReportParams,
  type BnrSummary,
  type BreakdownRow,
  type ExportFormat,
  type FinancialStatementRow,
  type RegulatoryPeriod,
} from '@/services/regulatoryService';


// ============================================================
// LOCAL TYPES
// ============================================================

type DownloadingFormat =
  | ExportFormat
  | null;

type ApiClientType =
  | 'BNR'
  | 'CREDIT_BUREAU';

interface ApiClientRecord {
  id?: number;
  name?: string;
  clientType?: ApiClientType | string;
  contactEmail?: string;
  description?: string;

  apiKey?: string;
  key?: string;
  clientKey?: string;

  status?: string;
  active?: boolean;
  revoked?: boolean;

  createdAt?: string;
  expiresAt?: string | null;
  revokedAt?: string | null;
  revokeReason?: string | null;

  lastUsedAt?: string | null;

  [key: string]: unknown;
}

interface CreditBureauPreviewRecord {
  borrowerId?: number;

  fullName?: string;
  borrowerName?: string;

  nationalId?: string;
  maskedNationalId?: string;

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

  [key: string]: unknown;
}

interface ApiClientForm {
  name: string;
  clientType: ApiClientType;
  contactEmail: string;
  description: string;
  expiresAt: string;
}


// ============================================================
// UTILITY TYPES
// ============================================================

type UnknownRecord =
  Record<string, unknown>;


// ============================================================
// PAGE
// ============================================================

export default function BnrReportPage() {

  // ==========================================================
  // FILTERS
  // ==========================================================

  const [period, setPeriod] =
    useState<RegulatoryPeriod>('MONTHLY');

  const [from, setFrom] =
    useState<string>('');

  const [to, setTo] =
    useState<string>('');

  const [branchId, setBranchId] =
    useState<string>('');


  // ==========================================================
  // REPORT DATA
  // ==========================================================

  const [summary, setSummary] =
    useState<BnrSummary | null>(null);

  const [financialStatement, setFinancialStatement] =
    useState<BnrFinancialStatementReport | null>(null);

  const [loanTypeBreakdown, setLoanTypeBreakdown] =
    useState<BreakdownRow[]>([]);

  const [branchBreakdown, setBranchBreakdown] =
    useState<BreakdownRow[]>([]);

  const [genderBreakdown, setGenderBreakdown] =
    useState<BreakdownRow[]>([]);


  // ==========================================================
  // CREDIT BUREAU
  // ==========================================================

  const [creditBureauRows, setCreditBureauRows] =
    useState<CreditBureauPreviewRecord[]>([]);

  const [creditBureauLoading, setCreditBureauLoading] =
    useState<boolean>(false);

  const [creditBureauError, setCreditBureauError] =
    useState<string | null>(null);

  const [creditSearch, setCreditSearch] =
    useState<string>('');


  // ==========================================================
  // API CLIENTS
  // ==========================================================

  const [apiClients, setApiClients] =
    useState<ApiClientRecord[]>([]);

  const [apiClientsLoading, setApiClientsLoading] =
    useState<boolean>(false);

  const [apiClientsError, setApiClientsError] =
    useState<string | null>(null);

  const [showApiClientModal, setShowApiClientModal] =
    useState<boolean>(false);

  const [creatingApiClient, setCreatingApiClient] =
    useState<boolean>(false);

  const [revokingClientId, setRevokingClientId] =
    useState<number | null>(null);

  const [visibleApiKeyId, setVisibleApiKeyId] =
    useState<number | null>(null);

  const [copiedApiKeyId, setCopiedApiKeyId] =
    useState<number | null>(null);

  const [apiClientForm, setApiClientForm] =
    useState<ApiClientForm>({
      name: '',
      clientType: 'BNR',
      contactEmail: '',
      description: '',
      expiresAt: '',
    });


  // ==========================================================
  // UI STATE
  // ==========================================================

  const [loading, setLoading] =
    useState<boolean>(true);

  const [downloadingFormat, setDownloadingFormat] =
    useState<DownloadingFormat>(null);

  const [creditDownloadingFormat, setCreditDownloadingFormat] =
    useState<DownloadingFormat>(null);

  const [error, setError] =
    useState<string | null>(null);


  // ==========================================================
  // REPORT PARAMETERS
  // ==========================================================

  const reportParams =
    useMemo<BnrReportParams>(() => {

      const params: BnrReportParams = {};

      if (branchId.trim() !== '') {

        const numericBranchId =
          Number(branchId);

        if (
          Number.isFinite(numericBranchId)
        ) {
          params.branchId =
            numericBranchId;
        }
      }

      if (period) {
        params.period =
          period;
      }

      if (
        period === 'CUSTOM' &&
        from
      ) {
        params.from =
          from;
      }

      if (
        period === 'CUSTOM' &&
        to
      ) {
        params.to =
          to;
      }

      return params;

    }, [
      branchId,
      period,
      from,
      to,
    ]);


  // ==========================================================
  // CREDIT BUREAU PARAMETERS
  // ==========================================================

  const creditBureauParams =
    useMemo(() => {

      const params: {
        branchId?: number;
        from?: string;
        to?: string;
      } = {};

      if (
        branchId.trim() !== ''
      ) {

        const numericBranchId =
          Number(branchId);

        if (
          Number.isFinite(numericBranchId)
        ) {
          params.branchId =
            numericBranchId;
        }
      }

      if (from) {
        params.from =
          from;
      }

      if (to) {
        params.to =
          to;
      }

      return params;

    }, [
      branchId,
      from,
      to,
    ]);


  // ==========================================================
  // VALIDATE FILTERS
  // ==========================================================

  const validateFilters =
    useCallback((): string | null => {

      if (
        period !== 'CUSTOM'
      ) {
        return null;
      }

      if (!from) {
        return 'Please select a start date.';
      }

      if (!to) {
        return 'Please select an end date.';
      }

      if (from > to) {
        return 'The start date cannot be after the end date.';
      }

      return null;

    }, [
      period,
      from,
      to,
    ]);


  // ==========================================================
  // FORMAT MONEY
  // ==========================================================

  const formatMoney =
    useCallback(
      (
        value?: number
      ): string => {

        const currency =
          summary?.currency ||
          financialStatement?.currency ||
          'RWF';

        const amount =
          Number(value ?? 0);

        try {

          return new Intl.NumberFormat(
            'en-RW',
            {
              style: 'currency',
              currency,
              maximumFractionDigits: 2,
            }
          ).format(amount);

        } catch {

          return `${currency} ${amount.toLocaleString(
            'en-US',
            {
              maximumFractionDigits: 2,
            }
          )}`;
        }

      },
      [
        summary?.currency,
        financialStatement?.currency,
      ]
    );


  // ==========================================================
  // FORMAT NUMBER
  // ==========================================================

  const formatNumber =
    useCallback(
      (
        value?: number
      ): string => {

        return new Intl.NumberFormat(
          'en-US'
        ).format(
          Number(value ?? 0)
        );

      },
      []
    );


  // ==========================================================
  // FORMAT PERCENT
  // ==========================================================

  const formatPercent =
    useCallback(
      (
        value?: number
      ): string => {

        return `${Number(
          value ?? 0
        ).toFixed(2)}%`;

      },
      []
    );


  // ==========================================================
  // FORMAT DATE
  // ==========================================================

  const formatDate =
    useCallback(
      (
        value?: string | null
      ): string => {

        if (!value) {
          return '—';
        }

        const date =
          new Date(value);

        if (
          Number.isNaN(
            date.getTime()
          )
        ) {
          return value;
        }

        return new Intl.DateTimeFormat(
          'en-RW',
          {
            year: 'numeric',
            month: 'short',
            day: '2-digit',
          }
        ).format(date);

      },
      []
    );


  // ==========================================================
  // LOAD MAIN REPORT
  // ==========================================================

  const loadReport =
    useCallback(async (): Promise<void> => {

      const validationError =
        validateFilters();

      if (validationError) {

        setError(
          validationError
        );

        return;
      }

      try {

        setLoading(true);

        setError(null);

        const [
          summaryResult,
          financialStatementResult,
          loanTypeResult,
          branchResult,
          genderResult,
        ] = await Promise.all([

          regulatoryApi.bnrSummary(
            reportParams
          ),

          regulatoryApi.bnrFinancialStatement(
            reportParams
          ),

          regulatoryApi.bnrByLoanType(
            reportParams
          ),

          regulatoryApi.bnrByBranch(
            reportParams
          ),

          regulatoryApi.bnrByGender(
            reportParams
          ),

        ]);

        setSummary(
          summaryResult
        );

        setFinancialStatement(
          financialStatementResult
        );

        setLoanTypeBreakdown(
          loanTypeResult
        );

        setBranchBreakdown(
          branchResult
        );

        setGenderBreakdown(
          genderResult
        );

      } catch (err) {

        console.error(
          'Failed to load BNR report:',
          err
        );

        setError(
          regulatoryApi.getErrorMessage(
            err,
            'Failed to load the BNR report.'
          )
        );

      } finally {

        setLoading(false);

      }

    }, [
      reportParams,
      validateFilters,
    ]);


  // ==========================================================
  // LOAD CREDIT BUREAU
  // ==========================================================

  const loadCreditBureau =
    useCallback(async (): Promise<void> => {

      try {

        setCreditBureauLoading(
          true
        );

        setCreditBureauError(
          null
        );

        const response =
          await regulatoryApi.creditBureauPreview(
            creditBureauParams
          );

        const normalized =
          normalizeArray<CreditBureauPreviewRecord>(
            response
          );

        setCreditBureauRows(
          normalized
        );

      } catch (err) {

        console.error(
          'Failed to load credit bureau preview:',
          err
        );

        setCreditBureauError(
          regulatoryApi.getErrorMessage(
            err,
            'Failed to load Credit Bureau preview.'
          )
        );

      } finally {

        setCreditBureauLoading(
          false
        );
      }

    }, [
      creditBureauParams,
    ]);


  // ==========================================================
  // LOAD API CLIENTS
  // ==========================================================

  const loadApiClients =
    useCallback(async (): Promise<void> => {

      try {

        setApiClientsLoading(
          true
        );

        setApiClientsError(
          null
        );

        const response =
          await regulatoryApi.listApiClients();

        const normalized =
          normalizeArray<ApiClientRecord>(
            response
          );

        setApiClients(
          normalized
        );

      } catch (err) {

        console.error(
          'Failed to load API clients:',
          err
        );

        setApiClientsError(
          regulatoryApi.getErrorMessage(
            err,
            'Failed to load regulatory API clients.'
          )
        );

      } finally {

        setApiClientsLoading(
          false
        );
      }

    }, []);


  // ==========================================================
  // INITIAL LOAD
  // ==========================================================

  useEffect(() => {

    void loadReport();

  }, [
    loadReport,
  ]);


  // ==========================================================
  // INITIAL CREDIT BUREAU LOAD
  // ==========================================================

  useEffect(() => {

    void loadCreditBureau();

  }, [
    loadCreditBureau,
  ]);


  // ==========================================================
  // INITIAL API CLIENT LOAD
  // ==========================================================

  useEffect(() => {

    void loadApiClients();

  }, [
    loadApiClients,
  ]);


  // ==========================================================
  // DOWNLOAD BNR
  // ==========================================================

  const downloadReport =
    useCallback(
      async (
        format: ExportFormat
      ): Promise<void> => {

        const validationError =
          validateFilters();

        if (validationError) {

          setError(
            validationError
          );

          return;
        }

        try {

          setError(null);

          setDownloadingFormat(
            format
          );

          await regulatoryApi.bnrExport(
            format,
            reportParams
          );

        } catch (err) {

          console.error(
            `Failed to download BNR ${format}:`,
            err
          );

          setError(
            regulatoryApi.getErrorMessage(
              err,
              `Failed to download BNR ${format.toUpperCase()} report.`
            )
          );

        } finally {

          setDownloadingFormat(
            null
          );

        }

      },
      [
        reportParams,
        validateFilters,
      ]
    );


  // ==========================================================
  // DOWNLOAD CREDIT BUREAU
  // ==========================================================

  const downloadCreditBureau =
    useCallback(
      async (
        format: ExportFormat
      ): Promise<void> => {

        try {

          setCreditDownloadingFormat(
            format
          );

          setCreditBureauError(
            null
          );

          await regulatoryApi.creditBureauExport(
            format,
            creditBureauParams
          );

        } catch (err) {

          console.error(
            `Failed to download Credit Bureau ${format}:`,
            err
          );

          setCreditBureauError(
            regulatoryApi.getErrorMessage(
              err,
              `Failed to download Credit Bureau ${format.toUpperCase()} report.`
            )
          );

        } finally {

          setCreditDownloadingFormat(
            null
          );

        }

      },
      [
        creditBureauParams,
      ]
    );


  // ==========================================================
  // CREATE API CLIENT
  // ==========================================================

  const createApiClient =
    useCallback(async (): Promise<void> => {

      if (
        apiClientForm.name.trim() === ''
      ) {

        setApiClientsError(
          'API client name is required.'
        );

        return;
      }

      try {

        setCreatingApiClient(
          true
        );

        setApiClientsError(
          null
        );

        await regulatoryApi.createApiClient({

          name:
            apiClientForm.name.trim(),

          clientType:
            apiClientForm.clientType,

          contactEmail:
            apiClientForm.contactEmail.trim() ||
            undefined,

          description:
            apiClientForm.description.trim() ||
            undefined,

          expiresAt:
            apiClientForm.expiresAt ||
            null,

        });

        setShowApiClientModal(
          false
        );

        setApiClientForm({
          name: '',
          clientType: 'BNR',
          contactEmail: '',
          description: '',
          expiresAt: '',
        });

        await loadApiClients();

      } catch (err) {

        console.error(
          'Failed to create API client:',
          err
        );

        setApiClientsError(
          regulatoryApi.getErrorMessage(
            err,
            'Failed to create API client.'
          )
        );

      } finally {

        setCreatingApiClient(
          false
        );
      }

    }, [
      apiClientForm,
      loadApiClients,
    ]);


  // ==========================================================
  // REVOKE API CLIENT
  // ==========================================================

  const revokeApiClient =
    useCallback(
      async (
        client: ApiClientRecord
      ): Promise<void> => {

        if (
          client.id === undefined
        ) {
          return;
        }

        const confirmed =
          window.confirm(
            `Revoke API client "${client.name || 'Unnamed client'}"?`
          );

        if (!confirmed) {
          return;
        }

        const reason =
          window.prompt(
            'Reason for revocation (optional):'
          ) ?? '';

        try {

          setRevokingClientId(
            client.id
          );

          setApiClientsError(
            null
          );

          await regulatoryApi.revokeApiClient(
            client.id,
            reason
          );

          await loadApiClients();

        } catch (err) {

          console.error(
            'Failed to revoke API client:',
            err
          );

          setApiClientsError(
            regulatoryApi.getErrorMessage(
              err,
              'Failed to revoke API client.'
            )
          );

        } finally {

          setRevokingClientId(
            null
          );
        }

      },
      [
        loadApiClients,
      ]
    );


  // ==========================================================
  // COPY API KEY
  // ==========================================================

  const copyApiKey =
    useCallback(
      async (
        client: ApiClientRecord
      ): Promise<void> => {

        const key =
          client.apiKey ||
          client.key ||
          client.clientKey;

        if (!key) {
          return;
        }

        try {

          await navigator.clipboard.writeText(
            key
          );

          if (
            client.id !== undefined
          ) {

            setCopiedApiKeyId(
              client.id
            );

            window.setTimeout(
              () => {
                setCopiedApiKeyId(
                  null
                );
              },
              2000
            );
          }

        } catch (err) {

          console.error(
            'Unable to copy API key:',
            err
          );

        }

      },
      []
    );


  // ==========================================================
  // FILTER CREDIT BUREAU
  // ==========================================================

  const filteredCreditBureauRows =
    useMemo(() => {

      const search =
        creditSearch
          .trim()
          .toLowerCase();

      if (!search) {
        return creditBureauRows;
      }

      return creditBureauRows.filter(
        row => {

          const values = [

            row.fullName,
            row.borrowerName,
            row.nationalId,
            row.maskedNationalId,
            row.loanNumber,
            row.loanType,
            row.loanStatus,
            row.branchName,
            row.phone,

          ];

          return values.some(
            value =>
              String(
                value ?? ''
              )
                .toLowerCase()
                .includes(search)
          );
        }
      );

    }, [
      creditBureauRows,
      creditSearch,
    ]);


  // ==========================================================
  // DERIVED KPI
  // ==========================================================

  const portfolioAtRisk =
    Number(
      summary?.parRatio ?? 0
    );

  const nplRatio =
    Number(
      summary?.nplRatio ?? 0
    );

  const totalOutstanding =
    Number(
      summary?.totalOutstanding ??
      summary?.outstandingPrincipal ??
      0
    );


  // ==========================================================
  // LOADING SCREEN
  // ==========================================================

  if (loading) {

    return (
      <PremiumLoadingScreen />
    );
  }


  // ==========================================================
  // RENDER
  // ==========================================================

  return (

    <div className="min-h-screen bg-[#f5f7fb] text-slate-900">

      {/* ======================================================
          TOP ACCENT
      ====================================================== */}

      <div className="h-1 bg-gradient-to-r from-slate-950 via-blue-700 to-cyan-500" />


      <div className="mx-auto max-w-[1600px] px-4 py-6 sm:px-6 lg:px-8">


        {/* ====================================================
            HEADER
        ==================================================== */}

        <header className="mb-6">

          <div className="overflow-hidden rounded-3xl bg-slate-950 shadow-2xl">

            <div className="relative px-6 py-8 sm:px-8 lg:px-10">

              <div className="absolute -right-24 -top-24 h-72 w-72 rounded-full bg-blue-500/20 blur-3xl" />

              <div className="absolute -bottom-32 left-1/3 h-72 w-72 rounded-full bg-cyan-500/10 blur-3xl" />

              <div className="relative flex flex-col gap-7 xl:flex-row xl:items-center xl:justify-between">

                <div>

                  <div className="mb-3 flex items-center gap-3">

                    <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-white/10 ring-1 ring-white/20">

                      <ShieldIcon />

                    </div>

                    <div>

                      <p className="text-xs font-semibold uppercase tracking-[0.2em] text-blue-300">
                        Regulatory Intelligence
                      </p>

                      <p className="text-xs text-slate-400">
                        BNR reporting & compliance
                      </p>

                    </div>

                  </div>

                  <h1 className="text-3xl font-bold tracking-tight text-white sm:text-4xl">
                    Regulatory Report
                  </h1>

                  <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-300">
                    A consolidated regulatory view of portfolio performance,
                    financial position, credit information and reporting
                    infrastructure.
                  </p>

                </div>


                <div className="flex flex-wrap items-center gap-2">

                  <button
                    type="button"
                    onClick={() => void loadReport()}
                    disabled={loading}
                    className="inline-flex items-center gap-2 rounded-xl border border-white/15 bg-white/10 px-4 py-2.5 text-sm font-semibold text-white backdrop-blur transition hover:bg-white/15 disabled:opacity-50"
                  >
                    <RefreshIcon />
                    Refresh
                  </button>

                  <button
                    type="button"
                    onClick={() =>
                      void downloadReport('pdf')
                    }
                    disabled={
                      downloadingFormat !== null
                    }
                    className="inline-flex items-center gap-2 rounded-xl bg-white px-4 py-2.5 text-sm font-semibold text-slate-900 shadow-lg transition hover:bg-slate-100 disabled:opacity-50"
                  >
                    <DownloadIcon />

                    {downloadingFormat === 'pdf'
                      ? 'Generating...'
                      : 'PDF'}
                  </button>

                  <button
                    type="button"
                    onClick={() =>
                      void downloadReport('xlsx')
                    }
                    disabled={
                      downloadingFormat !== null
                    }
                    className="inline-flex items-center gap-2 rounded-xl bg-emerald-500 px-4 py-2.5 text-sm font-semibold text-white shadow-lg transition hover:bg-emerald-400 disabled:opacity-50"
                  >
                    <DownloadIcon />

                    {downloadingFormat === 'xlsx'
                      ? 'Generating...'
                      : 'Excel'}
                  </button>

                  <button
                    type="button"
                    onClick={() =>
                      void downloadReport('csv')
                    }
                    disabled={
                      downloadingFormat !== null
                    }
                    className="inline-flex items-center gap-2 rounded-xl bg-blue-500 px-4 py-2.5 text-sm font-semibold text-white shadow-lg transition hover:bg-blue-400 disabled:opacity-50"
                  >
                    <DownloadIcon />

                    {downloadingFormat === 'csv'
                      ? 'Generating...'
                      : 'CSV'}
                  </button>

                </div>

              </div>


              {/* HEADER STATUS */}

              <div className="relative mt-8 grid grid-cols-1 gap-3 sm:grid-cols-3">

                <HeaderStatus
                  label="Reporting period"
                  value={
                    summary?.periodStart &&
                    summary?.periodEnd
                      ? `${summary.periodStart} → ${summary.periodEnd}`
                      : period
                  }
                />

                <HeaderStatus
                  label="Institution"
                  value={
                    summary?.organizationName ||
                    'Organization'
                  }
                />

                <HeaderStatus
                  label="BNR institution code"
                  value={
                    summary?.bnrInstitutionCode ||
                    'Not configured'
                  }
                />

              </div>

            </div>

          </div>

        </header>


        {/* ====================================================
            ERROR
        ==================================================== */}

        {error && (

          <Alert
            title="Report error"
            message={error}
            onDismiss={() =>
              setError(null)
            }
          />

        )}


        {/* ====================================================
            FILTER BAR
        ==================================================== */}

        <section className="mb-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">

          <div className="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">

            <div>

              <p className="text-xs font-semibold uppercase tracking-[0.16em] text-blue-600">
                Reporting controls
              </p>

              <h2 className="mt-1 text-lg font-bold text-slate-900">
                Reporting period
              </h2>

              <p className="mt-1 text-sm text-slate-500">
                Configure the regulatory reporting window and branch scope.
              </p>

            </div>


            <div className="grid w-full grid-cols-1 gap-3 sm:grid-cols-2 xl:max-w-4xl xl:grid-cols-4">

              <Field label="Period">

                <select
                  value={period}
                  onChange={event =>
                    setPeriod(
                      event.target.value as RegulatoryPeriod
                    )
                  }
                  className="input-control"
                >

                  <option value="DAILY">
                    Daily
                  </option>

                  <option value="WEEKLY">
                    Weekly
                  </option>

                  <option value="MONTHLY">
                    Monthly
                  </option>

                  <option value="QUARTERLY">
                    Quarterly
                  </option>

                  <option value="YEARLY">
                    Yearly
                  </option>

                  <option value="CUSTOM">
                    Custom
                  </option>

                </select>

              </Field>


              <Field label="Branch ID">

                <input
                  type="number"
                  min="1"
                  value={branchId}
                  onChange={event =>
                    setBranchId(
                      event.target.value
                    )
                  }
                  placeholder="All branches"
                  className="input-control"
                />

              </Field>


              <Field label="From">

                <input
                  type="date"
                  value={from}
                  disabled={
                    period !== 'CUSTOM'
                  }
                  onChange={event =>
                    setFrom(
                      event.target.value
                    )
                  }
                  className="input-control disabled:bg-slate-100 disabled:text-slate-400"
                />

              </Field>


              <Field label="To">

                <input
                  type="date"
                  value={to}
                  disabled={
                    period !== 'CUSTOM'
                  }
                  onChange={event =>
                    setTo(
                      event.target.value
                    )
                  }
                  className="input-control disabled:bg-slate-100 disabled:text-slate-400"
                />

              </Field>

            </div>


            <button
              type="button"
              onClick={() =>
                void loadReport()
              }
              className="inline-flex shrink-0 items-center justify-center gap-2 rounded-xl bg-slate-950 px-5 py-3 text-sm font-semibold text-white shadow-lg transition hover:bg-slate-800"
            >
              <RefreshIcon />
              Apply
            </button>

          </div>

        </section>


        {/* ====================================================
            INSTITUTION CARD
        ==================================================== */}

        {summary && (

          <section className="mb-6 grid grid-cols-1 gap-4 lg:grid-cols-[1fr_auto]">

            <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">

              <div className="flex flex-col gap-5 md:flex-row md:items-center md:justify-between">

                <div>

                  <div className="flex items-center gap-3">

                    <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-blue-50 text-blue-700">
                      <BuildingIcon />
                    </div>

                    <div>

                      <p className="text-xs font-semibold uppercase tracking-[0.15em] text-slate-400">
                        Reporting institution
                      </p>

                      <h2 className="text-xl font-bold text-slate-900">
                        {summary.organizationName ||
                          'Organization'}
                      </h2>

                    </div>

                  </div>

                  <div className="mt-4 flex flex-wrap gap-2">

                    <Badge>
                      BNR: {
                        summary.bnrInstitutionCode ||
                        'Not configured'
                      }
                    </Badge>

                    <Badge>
                      Registration: {
                        summary.registrationNumber ||
                        'Not configured'
                      }
                    </Badge>

                    <Badge>
                      Currency: {
                        summary.currency ||
                        'RWF'
                      }
                    </Badge>

                  </div>

                </div>


                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">

                  <MiniStat
                    label="Status"
                    value={
                      summary.reportStatus ||
                      '—'
                    }
                  />

                  <MiniStat
                    label="Reference"
                    value={
                      summary.reportReference ||
                      '—'
                    }
                  />

                  <MiniStat
                    label="Generated"
                    value={
                      formatDate(
                        summary.generatedAt
                      )
                    }
                  />

                </div>

              </div>

            </div>


            <div className="rounded-2xl bg-gradient-to-br from-blue-600 to-indigo-700 p-6 text-white shadow-xl">

              <p className="text-xs font-semibold uppercase tracking-[0.16em] text-blue-100">
                Portfolio health
              </p>

              <p className="mt-3 text-4xl font-bold">
                {portfolioAtRisk.toFixed(2)}%
              </p>

              <p className="mt-1 text-sm text-blue-100">
                Portfolio at Risk
              </p>

              <div className="mt-5 h-2 overflow-hidden rounded-full bg-white/20">

                <div
                  className="h-full rounded-full bg-white"
                  style={{
                    width: `${Math.min(
                      Math.max(
                        portfolioAtRisk,
                        0
                      ),
                      100
                    )}%`,
                  }}
                />

              </div>

              <div className="mt-4 flex items-center justify-between text-xs text-blue-100">

                <span>
                  NPL {nplRatio.toFixed(2)}%
                </span>

                <span>
                  {formatMoney(
                    totalOutstanding
                  )}{' '}
                  outstanding
                </span>

              </div>

            </div>

          </section>

        )}


        {/* ====================================================
            KPI GRID
        ==================================================== */}

        <section className="mb-6">

          <SectionHeading
            eyebrow="Executive overview"
            title="Portfolio snapshot"
            description="Core indicators used to monitor the institution's lending portfolio."
          />

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">

            <KpiCardPremium
              label="Total loans"
              value={formatNumber(
                summary?.totalLoans
              )}
              icon={<LoanIcon />}
              accent="blue"
            />

            <KpiCardPremium
              label="Active loans"
              value={formatNumber(
                summary?.activeLoans
              )}
              icon={<ActivityIcon />}
              accent="emerald"
            />

            <KpiCardPremium
              label="Principal disbursed"
              value={formatMoney(
                summary?.totalPrincipalDisbursed
              )}
              icon={<MoneyIcon />}
              accent="violet"
            />

            <KpiCardPremium
              label="Outstanding principal"
              value={formatMoney(
                summary?.outstandingPrincipal
              )}
              icon={<WalletIcon />}
              accent="amber"
            />

            <KpiCardPremium
              label="Interest collected"
              value={formatMoney(
                summary?.totalInterestCollected
              )}
              icon={<PercentIcon />}
              accent="cyan"
            />

            <KpiCardPremium
              label="Total collected"
              value={formatMoney(
                summary?.totalAmountCollected
              )}
              icon={<CollectionsIcon />}
              accent="green"
            />

            <KpiCardPremium
              label="Overdue loans"
              value={formatNumber(
                summary?.overdueLoans
              )}
              icon={<ClockIcon />}
              accent="orange"
            />

            <KpiCardPremium
              label="Defaulted loans"
              value={formatNumber(
                summary?.defaultedLoans
              )}
              icon={<WarningIcon />}
              accent="red"
            />

          </div>

        </section>


        {/* ====================================================
            PORTFOLIO QUALITY
        ==================================================== */}

        <section className="mb-6 rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">

          <SectionHeading
            eyebrow="Risk monitoring"
            title="Portfolio quality"
            description="Delinquency, non-performing loans and portfolio-at-risk indicators."
          />

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">

            <RiskCard
              label="PAR"
              ratio={
                summary?.parRatio
              }
              amount={
                summary?.parAmount
              }
              formatMoney={formatMoney}
              formatPercent={formatPercent}
              tone="blue"
            />

            <RiskCard
              label="PAR > 30 days"
              ratio={
                summary?.par30Ratio
              }
              amount={
                getPar30Amount(summary)
              }
              formatMoney={formatMoney}
              formatPercent={formatPercent}
              tone="amber"
            />

            <RiskCard
              label="PAR > 60 days"
              ratio={
                summary?.par60Ratio
              }
              amount={
                getPar60Amount(summary)
              }
              formatMoney={formatMoney}
              formatPercent={formatPercent}
              tone="orange"
            />

            <RiskCard
              label="PAR > 90 days"
              ratio={
                summary?.par90Ratio
              }
              amount={
                getPar90Amount(summary)
              }
              formatMoney={formatMoney}
              formatPercent={formatPercent}
              tone="red"
            />

            <RiskCard
              label="NPL ratio"
              ratio={
                summary?.nplRatio
              }
              amount={
                summary?.nplAmount
              }
              formatMoney={formatMoney}
              formatPercent={formatPercent}
              tone="red"
            />

            <MetricTile
              label="NPL loans"
              value={formatNumber(
                summary?.nplLoanCount
              )}
            />

            <MetricTile
              label="Loans > 30 DPD"
              value={formatNumber(
                summary?.loansOver30Days
              )}
            />

            <MetricTile
              label="Loans > 90 DPD"
              value={formatNumber(
                summary?.loansOver90Days
              )}
            />

          </div>

        </section>


        {/* ====================================================
            PAR AGING
        ==================================================== */}

        <section className="mb-6 rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">

          <SectionHeading
            eyebrow="Aging analysis"
            title="Portfolio at Risk aging"
            description="Outstanding exposure distributed across delinquency buckets."
          />

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-6">

            <AgingCard
              label="1–30 days"
              value={
                summary?.par1To30Amount
              }
              formatMoney={formatMoney}
              tone="blue"
            />

            <AgingCard
              label="31–60 days"
              value={
                summary?.par31To60Amount
              }
              formatMoney={formatMoney}
              tone="cyan"
            />

            <AgingCard
              label="61–90 days"
              value={
                summary?.par61To90Amount
              }
              formatMoney={formatMoney}
              tone="amber"
            />

            <AgingCard
              label="91–180 days"
              value={
                summary?.par91To180Amount
              }
              formatMoney={formatMoney}
              tone="orange"
            />

            <AgingCard
              label="181–365 days"
              value={
                summary?.par181To365Amount
              }
              formatMoney={formatMoney}
              tone="red"
            />

            <AgingCard
              label="Over 365 days"
              value={
                summary?.parOver365Amount
              }
              formatMoney={formatMoney}
              tone="slate"
            />

          </div>

        </section>


        {/* ====================================================
            STATUS + BORROWERS
        ==================================================== */}

        <div className="mb-6 grid grid-cols-1 gap-6 xl:grid-cols-2">

          <StatusPanel
            title="Loan status"
            eyebrow="Portfolio composition"
            items={[

              ['Active', summary?.activeLoans],
              ['Closed', summary?.closedLoans],
              ['Paid', summary?.paidLoans],
              ['Pending', summary?.pendingLoans],
              ['Approved', summary?.approvedLoans],
              ['Rejected', summary?.rejectedLoans],
              ['Cancelled', summary?.cancelledLoans],
              ['Defaulted', summary?.defaultedLoans],
              ['Written off', summary?.writtenOffLoans],
              ['Overdue', summary?.overdueLoans],

            ]}
            formatNumber={formatNumber}
          />


          <StatusPanel
            title="Borrower statistics"
            eyebrow="Customer portfolio"
            items={[

              ['Total borrowers', summary?.totalBorrowers],
              ['Active borrowers', summary?.activeBorrowers],
              ['Male borrowers', summary?.maleBorrowers],
              ['Female borrowers', summary?.femaleBorrowers],
              ['Youth borrowers', summary?.youthBorrowers],
              ['Adult borrowers', summary?.adultBorrowers],
              ['Senior borrowers', summary?.seniorBorrowers],
              ['Multiple loans', summary?.borrowersWithMultipleLoans],

            ]}
            formatNumber={formatNumber}
          />

        </div>


        {/* ====================================================
            CREDIT INFORMATION
        ==================================================== */}

        <section className="mb-6 rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">

          <SectionHeading
            eyebrow="Credit intelligence"
            title="Credit information"
            description="Credit checks, active facilities and external debt exposure."
          />

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-5">

            <MetricTile
              label="Credit checked"
              value={formatNumber(
                summary?.borrowersCreditChecked
              )}
            />

            <MetricTile
              label="Default history"
              value={formatNumber(
                summary?.borrowersWithDefaultHistory
              )}
            />

            <MetricTile
              label="Active listings"
              value={formatNumber(
                summary?.borrowersWithActiveListing
              )}
            />

            <MetricTile
              label="Multiple facilities"
              value={formatNumber(
                summary?.borrowersWithMultipleFacilities
              )}
            />

            <MetricTile
              label="External debt"
              value={formatMoney(
                summary?.totalExternalDebt
              )}
            />

          </div>

        </section>


        {/* ====================================================
            REPAYMENT PERFORMANCE
        ==================================================== */}

        <section className="mb-6 rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">

          <SectionHeading
            eyebrow="Collections"
            title="Repayment performance"
            description="Collection activity and unpaid obligations during the reporting period."
          />

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">

            <MetricTile
              label="Principal collected"
              value={formatMoney(
                summary?.totalPrincipalCollected
              )}
            />

            <MetricTile
              label="Interest collected"
              value={formatMoney(
                summary?.totalInterestCollected
              )}
            />

            <MetricTile
              label="Fees collected"
              value={formatMoney(
                summary?.totalFeesCollected
              )}
            />

            <MetricTile
              label="Total collected"
              value={formatMoney(
                summary?.totalAmountCollected
              )}
            />

            <MetricTile
              label="Unpaid interest"
              value={formatMoney(
                summary?.interestAccruedUnpaid
              )}
            />

            <MetricTile
              label="Unpaid fees"
              value={formatMoney(
                summary?.feesAccruedUnpaid
              )}
            />

            <MetricTile
              label="Missed payments"
              value={formatNumber(
                summary?.missedPayments
              )}
            />

            <MetricTile
              label="Overdue payments"
              value={formatNumber(
                summary?.overduePayments
              )}
            />

          </div>

        </section>


        {/* ====================================================
            FINANCIAL STATEMENT
        ==================================================== */}

        <FinancialStatementSectionPremium
          report={
            financialStatement
          }
          formatMoney={
            formatMoney
          }
          formatNumber={
            formatNumber
          }
        />


        {/* ====================================================
            DATA QUALITY
        ==================================================== */}

        <section className="mb-6 rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">

          <SectionHeading
            eyebrow="Compliance controls"
            title="Data quality"
            description="Data completeness indicators that can affect regulatory submission quality."
          />

          <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-5">

            <MetricTile
              label="Missing borrower"
              value={formatNumber(
                summary?.loansMissingBorrower
              )}
              tone={
                Number(
                  summary?.loansMissingBorrower ?? 0
                ) > 0
                  ? 'danger'
                  : 'success'
              }
            />

            <MetricTile
              label="Missing national ID"
              value={formatNumber(
                summary?.borrowersMissingNationalId
              )}
              tone={
                Number(
                  summary?.borrowersMissingNationalId ?? 0
                ) > 0
                  ? 'danger'
                  : 'success'
              }
            />

            <MetricTile
              label="Missing branch"
              value={formatNumber(
                summary?.loansMissingBranch
              )}
              tone={
                Number(
                  summary?.loansMissingBranch ?? 0
                ) > 0
                  ? 'danger'
                  : 'success'
              }
            />

            <MetricTile
              label="Missing currency"
              value={formatNumber(
                summary?.loansMissingCurrency
              )}
              tone={
                Number(
                  summary?.loansMissingCurrency ?? 0
                ) > 0
                  ? 'danger'
                  : 'success'
              }
            />

            <MetricTile
              label="Missing schedule"
              value={formatNumber(
                summary?.loansMissingRepaymentSchedule
              )}
              tone={
                Number(
                  summary?.loansMissingRepaymentSchedule ?? 0
                ) > 0
                  ? 'danger'
                  : 'success'
              }
            />

          </div>


          {summary?.dataQualityWarnings &&
            summary.dataQualityWarnings.length > 0 && (

            <div className="mt-6 rounded-2xl border border-amber-200 bg-amber-50 p-5">

              <div className="flex gap-3">

                <div className="mt-0.5 text-amber-600">
                  <WarningIcon />
                </div>

                <div>

                  <p className="font-semibold text-amber-900">
                    Validation warnings
                  </p>

                  <ul className="mt-2 space-y-1 text-sm text-amber-800">

                    {summary.dataQualityWarnings.map(
                      (
                        warning,
                        index
                      ) => (

                        <li
                          key={`${warning}-${index}`}
                          className="flex gap-2"
                        >
                          <span>•</span>
                          <span>
                            {warning}
                          </span>
                        </li>

                      )
                    )}

                  </ul>

                </div>

              </div>

            </div>

          )}

        </section>


        {/* ====================================================
            BREAKDOWNS
        ==================================================== */}

        <div className="mb-6 grid grid-cols-1 gap-6 xl:grid-cols-3">

          <BreakdownTablePremium
            title="Borrowers by gender"
            rows={
              genderBreakdown
            }
            formatMoney={
              formatMoney
            }
            formatNumber={
              formatNumber
            }
          />

          <BreakdownTablePremium
            title="Loans by loan type"
            rows={
              loanTypeBreakdown
            }
            formatMoney={
              formatMoney
            }
            formatNumber={
              formatNumber
            }
          />

          <BreakdownTablePremium
            title="Loans by branch"
            rows={
              branchBreakdown
            }
            formatMoney={
              formatMoney
            }
            formatNumber={
              formatNumber
            }
          />

        </div>


        {/* ====================================================
            CREDIT BUREAU
        ==================================================== */}

        <section className="mb-6 overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">

          <div className="border-b border-slate-200 bg-gradient-to-r from-slate-950 to-slate-900 p-6 text-white">

            <div className="flex flex-col gap-5 xl:flex-row xl:items-center xl:justify-between">

              <div>

                <div className="flex items-center gap-3">

                  <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-white/10">
                    <CreditIcon />
                  </div>

                  <div>

                    <p className="text-xs font-semibold uppercase tracking-[0.16em] text-cyan-300">
                      Credit reporting
                    </p>

                    <h2 className="text-xl font-bold">
                      Credit Bureau
                    </h2>

                  </div>

                </div>

                <p className="mt-3 max-w-2xl text-sm text-slate-400">
                  Staff preview of borrower credit records and facilities
                  within the selected reporting scope.
                </p>

              </div>


              <div className="flex flex-wrap gap-2">

                <button
                  type="button"
                  onClick={() =>
                    void loadCreditBureau()
                  }
                  disabled={
                    creditBureauLoading
                  }
                  className="inline-flex items-center gap-2 rounded-xl border border-white/10 bg-white/10 px-4 py-2.5 text-sm font-semibold text-white hover:bg-white/15 disabled:opacity-50"
                >
                  <RefreshIcon />

                  Refresh
                </button>

                <button
                  type="button"
                  onClick={() =>
                    void downloadCreditBureau(
                      'pdf'
                    )
                  }
                  disabled={
                    creditDownloadingFormat !== null
                  }
                  className="rounded-xl bg-white px-4 py-2.5 text-sm font-semibold text-slate-900 disabled:opacity-50"
                >
                  PDF
                </button>

                <button
                  type="button"
                  onClick={() =>
                    void downloadCreditBureau(
                      'xlsx'
                    )
                  }
                  disabled={
                    creditDownloadingFormat !== null
                  }
                  className="rounded-xl bg-emerald-500 px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50"
                >
                  Excel
                </button>

                <button
                  type="button"
                  onClick={() =>
                    void downloadCreditBureau(
                      'csv'
                    )
                  }
                  disabled={
                    creditDownloadingFormat !== null
                  }
                  className="rounded-xl bg-blue-500 px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-50"
                >
                  CSV
                </button>

              </div>

            </div>

          </div>


          <div className="p-6">

            {creditBureauError && (

              <Alert
                title="Credit Bureau error"
                message={
                  creditBureauError
                }
                onDismiss={() =>
                  setCreditBureauError(
                    null
                  )
                }
              />

            )}


            <div className="mb-5 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">

              <div>

                <p className="text-sm font-semibold text-slate-900">
                  {formatNumber(
                    filteredCreditBureauRows.length
                  )}{' '}
                  records
                </p>

                <p className="text-xs text-slate-500">
                  Preview results
                </p>

              </div>


              <div className="relative w-full md:w-80">

                <SearchIcon />

                <input
                  type="search"
                  value={creditSearch}
                  onChange={event =>
                    setCreditSearch(
                      event.target.value
                    )
                  }
                  placeholder="Search borrower, ID, loan..."
                  className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-3 text-sm outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-50"
                />

              </div>

            </div>


            {creditBureauLoading ? (

              <TableSkeleton />

            ) : filteredCreditBureauRows.length === 0 ? (

              <EmptyState
                icon={
                  <CreditIcon />
                }
                title="No Credit Bureau records"
                description="No credit records are available for the current reporting scope."
              />

            ) : (

              <div className="overflow-x-auto rounded-2xl border border-slate-200">

                <table className="min-w-[1200px] w-full text-sm">

                  <thead className="bg-slate-50">

                    <tr>

                      <TableHeader>
                        Borrower
                      </TableHeader>

                      <TableHeader>
                        National ID
                      </TableHeader>

                      <TableHeader>
                        Loan
                      </TableHeader>

                      <TableHeader>
                        Status
                      </TableHeader>

                      <TableHeader align="right">
                        Loan amount
                      </TableHeader>

                      <TableHeader align="right">
                        Outstanding
                      </TableHeader>

                      <TableHeader align="center">
                        DPD
                      </TableHeader>

                      <TableHeader align="center">
                        Score
                      </TableHeader>

                      <TableHeader>
                        Branch
                      </TableHeader>

                    </tr>

                  </thead>


                  <tbody className="divide-y divide-slate-100 bg-white">

                    {filteredCreditBureauRows.map(
                      (
                        row,
                        index
                      ) => (

                        <tr
                          key={`${row.borrowerId || row.loanNumber || 'credit'}-${index}`}
                          className="transition hover:bg-slate-50"
                        >

                          <td className="px-4 py-4">

                            <p className="font-semibold text-slate-900">
                              {
                                row.fullName ||
                                row.borrowerName ||
                                'Unknown borrower'
                              }
                            </p>

                            <p className="mt-0.5 text-xs text-slate-500">
                              {row.phone ||
                                'No phone'}
                            </p>

                          </td>


                          <td className="px-4 py-4 font-mono text-xs text-slate-600">
                            {
                              row.maskedNationalId ||
                              row.nationalId ||
                              '—'
                            }
                          </td>


                          <td className="px-4 py-4">

                            <p className="font-medium text-slate-900">
                              {row.loanNumber ||
                                '—'}
                            </p>

                            <p className="text-xs text-slate-500">
                              {row.loanType ||
                                '—'}
                            </p>

                          </td>


                          <td className="px-4 py-4">

                            <StatusBadge
                              status={
                                row.loanStatus
                              }
                            />

                          </td>


                          <td className="px-4 py-4 text-right font-medium text-slate-900">

                            {formatMoney(
                              row.loanAmount
                            )}

                          </td>


                          <td className="px-4 py-4 text-right font-semibold text-slate-900">

                            {formatMoney(
                              row.outstandingBalance
                            )}

                          </td>


                          <td className="px-4 py-4 text-center">

                            <DpdBadge
                              days={
                                row.daysPastDue
                              }
                            />

                          </td>


                          <td className="px-4 py-4 text-center">

                            <ScoreBadge
                              score={
                                row.creditScore
                              }
                            />

                          </td>


                          <td className="px-4 py-4 text-slate-600">

                            {row.branchName ||
                              '—'}

                          </td>

                        </tr>

                      )
                    )}

                  </tbody>

                </table>

              </div>

            )}

          </div>

        </section>


        {/* ====================================================
            API CLIENT MANAGEMENT
        ==================================================== */}

        <section className="mb-8 overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">

          <div className="border-b border-slate-200 bg-gradient-to-r from-indigo-950 to-slate-950 p-6 text-white">

            <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">

              <div>

                <div className="flex items-center gap-3">

                  <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-white/10">
                    <KeyIcon />
                  </div>

                  <div>

                    <p className="text-xs font-semibold uppercase tracking-[0.16em] text-indigo-300">
                      Integration security
                    </p>

                    <h2 className="text-xl font-bold">
                      API Clients
                    </h2>

                  </div>

                </div>

                <p className="mt-3 max-w-2xl text-sm text-slate-400">
                  Manage external regulatory and Credit Bureau API
                  integrations, credentials and client lifecycle.
                </p>

              </div>


              <button
                type="button"
                onClick={() =>
                  setShowApiClientModal(
                    true
                  )
                }
                className="inline-flex items-center justify-center gap-2 rounded-xl bg-white px-5 py-3 text-sm font-semibold text-slate-900 shadow-lg transition hover:bg-slate-100"
              >
                <PlusIcon />
                Create API client
              </button>

            </div>

          </div>


          <div className="p-6">

            {apiClientsError && (

              <Alert
                title="API client error"
                message={
                  apiClientsError
                }
                onDismiss={() =>
                  setApiClientsError(
                    null
                  )
                }
              />

            )}


            {apiClientsLoading ? (

              <TableSkeleton />

            ) : apiClients.length === 0 ? (

              <EmptyState
                icon={
                  <KeyIcon />
                }
                title="No API clients configured"
                description="Create a BNR or Credit Bureau API client to establish an integration."
                action={
                  <button
                    type="button"
                    onClick={() =>
                      setShowApiClientModal(
                        true
                      )
                    }
                    className="rounded-xl bg-slate-950 px-4 py-2.5 text-sm font-semibold text-white"
                  >
                    Create first client
                  </button>
                }
              />

            ) : (

              <div className="overflow-x-auto rounded-2xl border border-slate-200">

                <table className="min-w-[1100px] w-full text-sm">

                  <thead className="bg-slate-50">

                    <tr>

                      <TableHeader>
                        Client
                      </TableHeader>

                      <TableHeader>
                        Type
                      </TableHeader>

                      <TableHeader>
                        Status
                      </TableHeader>

                      <TableHeader>
                        API key
                      </TableHeader>

                      <TableHeader>
                        Created
                      </TableHeader>

                      <TableHeader>
                        Expires
                      </TableHeader>

                      <TableHeader>
                        Last used
                      </TableHeader>

                      <TableHeader align="right">
                        Actions
                      </TableHeader>

                    </tr>

                  </thead>


                  <tbody className="divide-y divide-slate-100 bg-white">

                    {apiClients.map(
                      (
                        client,
                        index
                      ) => {

                        const clientId =
                          client.id;

                        const key =
                          client.apiKey ||
                          client.key ||
                          client.clientKey;

                        const isVisible =
                          clientId !== undefined &&
                          visibleApiKeyId === clientId;

                        const isCopied =
                          clientId !== undefined &&
                          copiedApiKeyId === clientId;

                        const revoked =
                          client.revoked === true ||
                          String(
                            client.status || ''
                          ).toUpperCase() ===
                            'REVOKED';

                        return (

                          <tr
                            key={`${client.id || client.name || 'client'}-${index}`}
                            className="hover:bg-slate-50"
                          >

                            <td className="px-4 py-4">

                              <p className="font-semibold text-slate-900">
                                {client.name ||
                                  'Unnamed client'}
                              </p>

                              <p className="mt-1 text-xs text-slate-500">
                                {client.contactEmail ||
                                  'No contact email'}
                              </p>

                              {client.description && (

                                <p className="mt-1 max-w-sm text-xs text-slate-400">
                                  {client.description}
                                </p>

                              )}

                            </td>


                            <td className="px-4 py-4">

                              <ClientTypeBadge
                                type={
                                  client.clientType
                                }
                              />

                            </td>


                            <td className="px-4 py-4">

                              <ClientStatusBadge
                                revoked={
                                  revoked
                                }
                                active={
                                  client.active
                                }
                                status={
                                  client.status
                                }
                              />

                            </td>


                            <td className="px-4 py-4">

                              {key ? (

                                <div className="flex items-center gap-2">

                                  <code className="max-w-[260px] truncate rounded-lg bg-slate-100 px-3 py-2 text-xs text-slate-600">

                                    {isVisible
                                      ? key
                                      : maskApiKey(key)}

                                  </code>

                                  <button
                                    type="button"
                                    onClick={() => {

                                      if (
                                        clientId === undefined
                                      ) {
                                        return;
                                      }

                                      setVisibleApiKeyId(
                                        current =>
                                          current ===
                                          clientId
                                            ? null
                                            : clientId
                                      );

                                    }}
                                    className="rounded-lg border border-slate-200 px-2.5 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50"
                                  >
                                    {isVisible
                                      ? 'Hide'
                                      : 'View'}
                                  </button>

                                  <button
                                    type="button"
                                    onClick={() =>
                                      void copyApiKey(
                                        client
                                      )
                                    }
                                    className="rounded-lg border border-slate-200 px-2.5 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50"
                                  >
                                    {isCopied
                                      ? 'Copied'
                                      : 'Copy'}
                                  </button>

                                </div>

                              ) : (

                                <span className="text-xs text-slate-400">
                                  Key not returned
                                </span>

                              )}

                            </td>


                            <td className="px-4 py-4 text-slate-600">
                              {formatDate(
                                client.createdAt
                              )}
                            </td>


                            <td className="px-4 py-4 text-slate-600">
                              {formatDate(
                                client.expiresAt
                              )}
                            </td>


                            <td className="px-4 py-4 text-slate-600">
                              {formatDate(
                                client.lastUsedAt
                              )}
                            </td>


                            <td className="px-4 py-4 text-right">

                              <button
                                type="button"
                                disabled={
                                  revoked ||
                                  clientId === undefined ||
                                  revokingClientId === clientId
                                }
                                onClick={() =>
                                  void revokeApiClient(
                                    client
                                  )
                                }
                                className="rounded-lg border border-red-200 px-3 py-2 text-xs font-semibold text-red-600 transition hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-40"
                              >
                                {revokingClientId === clientId
                                  ? 'Revoking...'
                                  : revoked
                                    ? 'Revoked'
                                    : 'Revoke'}
                              </button>

                            </td>

                          </tr>

                        );
                      }
                    )}

                  </tbody>

                </table>

              </div>

            )}

          </div>

        </section>


        {/* ====================================================
            FOOTER
        ==================================================== */}

        <footer className="border-t border-slate-200 pb-8 pt-6">

          <div className="flex flex-col gap-2 text-xs text-slate-400 sm:flex-row sm:items-center sm:justify-between">

            <div>

              BNR Regulatory Report
              {' • '}
              {period}

              {summary?.reportReference && (
                <>
                  {' • '}
                  {summary.reportReference}
                </>
              )}

            </div>

            <div>

              Generated{' '}
              {formatDate(
                summary?.generatedAt
              )}

            </div>

          </div>

        </footer>

      </div>


      {/* ======================================================
          API CLIENT MODAL
      ====================================================== */}

      {showApiClientModal && (

        <ApiClientModal

          form={
            apiClientForm
          }

          setForm={
            setApiClientForm
          }

          loading={
            creatingApiClient
          }

          onClose={() =>
            setShowApiClientModal(
              false
            )
          }

          onSubmit={() =>
            void createApiClient()
          }

        />

      )}

    </div>
  );
}


// ============================================================
// PREMIUM LOADING SCREEN
// ============================================================

function PremiumLoadingScreen() {

  return (

    <div className="min-h-screen bg-[#f5f7fb]">

      <div className="h-1 bg-gradient-to-r from-slate-950 via-blue-700 to-cyan-500" />

      <div className="mx-auto max-w-[1600px] space-y-6 px-6 py-8">

        <div className="h-64 animate-pulse rounded-3xl bg-slate-900" />

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">

          {Array.from({
            length: 8,
          }).map(
            (_, index) => (

              <div
                key={index}
                className="h-32 animate-pulse rounded-2xl bg-white shadow-sm"
              />

            )
          )}

        </div>

        <div className="h-96 animate-pulse rounded-3xl bg-white shadow-sm" />

      </div>

    </div>
  );
}


// ============================================================
// API CLIENT MODAL
// ============================================================

function ApiClientModal({
  form,
  setForm,
  loading,
  onClose,
  onSubmit,
}: {
  form: ApiClientForm;
  setForm: React.Dispatch<
    React.SetStateAction<ApiClientForm>
  >;
  loading: boolean;
  onClose: () => void;
  onSubmit: () => void;
}) {

  return (

    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4 backdrop-blur-sm">

      <div className="w-full max-w-xl overflow-hidden rounded-3xl bg-white shadow-2xl">

        <div className="bg-gradient-to-r from-slate-950 to-indigo-950 p-6 text-white">

          <div className="flex items-center justify-between">

            <div>

              <p className="text-xs font-semibold uppercase tracking-[0.16em] text-indigo-300">
                Integration security
              </p>

              <h2 className="mt-1 text-xl font-bold">
                Create API Client
              </h2>

            </div>

            <button
              type="button"
              onClick={onClose}
              className="rounded-xl bg-white/10 px-3 py-2 text-sm text-white hover:bg-white/15"
            >
              Close
            </button>

          </div>

        </div>


        <div className="space-y-5 p-6">

          <Field label="Client name">

            <input
              value={form.name}
              onChange={event =>
                setForm(
                  current => ({
                    ...current,
                    name:
                      event.target.value,
                  })
                )
              }
              placeholder="e.g. BNR Integration"
              className="input-control"
            />

          </Field>


          <Field label="Client type">

            <select
              value={form.clientType}
              onChange={event =>
                setForm(
                  current => ({
                    ...current,
                    clientType:
                      event.target.value as ApiClientType,
                  })
                )
              }
              className="input-control"
            >

              <option value="BNR">
                BNR
              </option>

              <option value="CREDIT_BUREAU">
                Credit Bureau
              </option>

            </select>

          </Field>


          <Field label="Contact email">

            <input
              type="email"
              value={form.contactEmail}
              onChange={event =>
                setForm(
                  current => ({
                    ...current,
                    contactEmail:
                      event.target.value,
                  })
                )
              }
              placeholder="compliance@example.com"
              className="input-control"
            />

          </Field>


          <Field label="Description">

            <textarea
              rows={3}
              value={form.description}
              onChange={event =>
                setForm(
                  current => ({
                    ...current,
                    description:
                      event.target.value,
                  })
                )
              }
              placeholder="Describe the integration purpose..."
              className="input-control resize-none"
            />

          </Field>


          <Field label="Expiration date">

            <input
              type="date"
              value={form.expiresAt}
              onChange={event =>
                setForm(
                  current => ({
                    ...current,
                    expiresAt:
                      event.target.value,
                  })
                )
              }
              className="input-control"
            />

          </Field>


          <div className="rounded-2xl border border-blue-100 bg-blue-50 p-4 text-sm text-blue-800">

            <div className="flex gap-3">

              <KeyIcon />

              <p>
                The backend should generate and return the API
                credential according to your API-client security
                implementation. Avoid exposing permanent secrets
                unnecessarily.
              </p>

            </div>

          </div>


          <div className="flex justify-end gap-3 pt-2">

            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              className="rounded-xl border border-slate-200 px-5 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50"
            >
              Cancel
            </button>

            <button
              type="button"
              onClick={onSubmit}
              disabled={
                loading ||
                form.name.trim() === ''
              }
              className="rounded-xl bg-slate-950 px-5 py-2.5 text-sm font-semibold text-white shadow-lg hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {loading
                ? 'Creating...'
                : 'Create client'}
            </button>

          </div>

        </div>

      </div>

    </div>
  );
}


// ============================================================
// FINANCIAL STATEMENT
// ============================================================

function FinancialStatementSectionPremium({
  report,
  formatMoney,
  formatNumber,
}: {
  report: BnrFinancialStatementReport | null;

  formatMoney: (
    value?: number
  ) => string;

  formatNumber: (
    value?: number
  ) => string;
}) {

  if (!report) {

    return (

      <section className="mb-6 rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">

        <SectionHeading
          eyebrow="Accounting"
          title="Financial statement"
          description="Statement of financial position, income, cash flow and trial balance."
        />

        <EmptyState
          title="Financial statement unavailable"
          description="Financial statement data is not available for this reporting period."
        />

      </section>
    );
  }


  return (

    <section className="mb-6 space-y-6">

      <SectionHeading
        eyebrow="Accounting"
        title="Financial statement"
        description="Statement of financial position, income, cash flow and trial balance."
      />


      {/* BALANCE SHEET */}

      <div className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">

        <div className="border-b border-slate-200 bg-slate-950 p-6 text-white">

          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-blue-300">
            Balance sheet
          </p>

          <h3 className="mt-1 text-xl font-bold">
            Statement of Financial Position
          </h3>

          <p className="mt-1 text-sm text-slate-400">
            {report.periodStart ||
              '—'}
            {' → '}
            {report.periodEnd ||
              '—'}
          </p>

        </div>


        <div className="grid grid-cols-1 gap-0 lg:grid-cols-3">

          <FinancialAccountGroup
            title="Assets"
            rows={
              report.assets
            }
            formatMoney={
              formatMoney
            }
          />

          <FinancialAccountGroup
            title="Liabilities"
            rows={
              report.liabilities
            }
            formatMoney={
              formatMoney
            }
          />

          <FinancialAccountGroup
            title="Equity"
            rows={
              report.equity
            }
            formatMoney={
              formatMoney
            }
          />

        </div>


        <div className="grid grid-cols-1 gap-4 border-t border-slate-200 p-6 sm:grid-cols-2 xl:grid-cols-4">

          <MetricTile
            label="Total assets"
            value={formatMoney(
              report.totalAssets
            )}
          />

          <MetricTile
            label="Total liabilities"
            value={formatMoney(
              report.totalLiabilities
            )}
          />

          <MetricTile
            label="Total equity"
            value={formatMoney(
              report.totalEquity
            )}
          />

          <MetricTile
            label="Current period net income"
            value={formatMoney(
              report.currentPeriodNetIncome
            )}
          />

        </div>


        <div className="border-t border-slate-200 p-6">

          <BalanceIndicatorPremium
            label="Balance Sheet"
            balanced={
              report.balanceSheetBalanced
            }
          />

        </div>

      </div>


      {/* INCOME */}

      <div className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">

        <div className="border-b border-slate-200 p-6">

          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-emerald-600">
            Profitability
          </p>

          <h3 className="mt-1 text-xl font-bold text-slate-900">
            Income Statement
          </h3>

        </div>


        <div className="grid grid-cols-1 gap-0 lg:grid-cols-2">

          <FinancialAccountGroup
            title="Income"
            rows={
              report.income
            }
            formatMoney={
              formatMoney
            }
          />

          <FinancialAccountGroup
            title="Expenses"
            rows={
              report.expenses
            }
            formatMoney={
              formatMoney
            }
          />

        </div>


        <div className="grid grid-cols-1 gap-4 border-t border-slate-200 p-6 sm:grid-cols-3">

          <MetricTile
            label="Total income"
            value={formatMoney(
              report.totalIncome
            )}
          />

          <MetricTile
            label="Total expenses"
            value={formatMoney(
              report.totalExpenses
            )}
          />

          <MetricTile
            label="Net income"
            value={formatMoney(
              report.netIncome
            )}
            tone={
              Number(
                report.netIncome ?? 0
              ) >= 0
                ? 'success'
                : 'danger'
            }
          />

        </div>

      </div>


      {/* CASH FLOW */}

      <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">

        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-cyan-600">
          Liquidity
        </p>

        <h3 className="mt-1 text-xl font-bold text-slate-900">
          Cash Flow
        </h3>

        <div className="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-5">

          <MetricTile
            label="Cash used for lending"
            value={formatMoney(
              report.cashUsedForLending
            )}
          />

          <MetricTile
            label="Cash from collections"
            value={formatMoney(
              report.cashFromCollections
            )}
          />

          <MetricTile
            label="Cash from fees"
            value={formatMoney(
              report.cashFromFees
            )}
          />

          <MetricTile
            label="Other cash movement"
            value={formatMoney(
              report.otherCashMovement
            )}
          />

          <MetricTile
            label="Net change in cash"
            value={formatMoney(
              report.netChangeInCash
            )}
          />

        </div>

      </div>


      {/* TRIAL BALANCE */}

      <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">

        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-violet-600">
          Accounting control
        </p>

        <h3 className="mt-1 text-xl font-bold text-slate-900">
          Trial Balance
        </h3>

        <div className="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-3">

          <MetricTile
            label="Total debit"
            value={formatMoney(
              report.trialBalanceDebit
            )}
          />

          <MetricTile
            label="Total credit"
            value={formatMoney(
              report.trialBalanceCredit
            )}
          />

          <BalanceIndicatorPremium
            label="Trial Balance"
            balanced={
              report.trialBalanceBalanced
            }
          />

        </div>

      </div>

    </section>
  );
}


// ============================================================
// FINANCIAL ACCOUNT GROUP
// ============================================================

function FinancialAccountGroup({
  title,
  rows,
  formatMoney,
}: {
  title: string;
  rows?: FinancialStatementRow[];
  formatMoney: (
    value?: number
  ) => string;
}) {

  const safeRows =
    Array.isArray(rows)
      ? rows
      : [];

  return (

    <div className="border-b border-slate-200 p-6 lg:border-b-0 lg:border-r last:lg:border-r-0">

      <h4 className="mb-4 flex items-center justify-between">

        <span className="font-bold text-slate-900">
          {title}
        </span>

        <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-500">
          {safeRows.length}
        </span>

      </h4>


      {safeRows.length === 0 ? (

        <p className="rounded-xl bg-slate-50 p-4 text-sm text-slate-500">
          No accounts reported.
        </p>

      ) : (

        <div className="overflow-hidden rounded-xl border border-slate-100">

          <table className="w-full text-sm">

            <thead className="bg-slate-50">

              <tr>

                <th className="px-3 py-2 text-left text-xs font-semibold uppercase text-slate-400">
                  Account
                </th>

                <th className="px-3 py-2 text-right text-xs font-semibold uppercase text-slate-400">
                  Balance
                </th>

              </tr>

            </thead>


            <tbody className="divide-y divide-slate-100">

              {safeRows.map(
                (
                  row,
                  index
                ) => {

                  const value =
                    row.balance ??
                    row.amount ??
                    row.credit ??
                    row.debit ??
                    0;

                  return (

                    <tr
                      key={`${row.code || row.name || 'account'}-${index}`}
                    >

                      <td className="px-3 py-3">

                        <p className="font-medium text-slate-800">
                          {row.name ||
                            'Unnamed account'}
                        </p>

                        {row.code && (

                          <p className="font-mono text-xs text-slate-400">
                            {row.code}
                          </p>

                        )}

                      </td>

                      <td className="px-3 py-3 text-right font-semibold text-slate-900">
                        {formatMoney(
                          value
                        )}
                      </td>

                    </tr>

                  );
                }
              )}

            </tbody>

          </table>

        </div>

      )}

    </div>
  );
}


// ============================================================
// BREAKDOWN TABLE
// ============================================================

function BreakdownTablePremium({
  title,
  rows,
  formatMoney,
  formatNumber,
}: {
  title: string;
  rows: BreakdownRow[];
  formatMoney: (
    value?: number
  ) => string;
  formatNumber: (
    value?: number
  ) => string;
}) {

  return (

    <div className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">

      <div className="border-b border-slate-200 p-5">

        <p className="text-xs font-semibold uppercase tracking-[0.15em] text-slate-400">
          Distribution
        </p>

        <h3 className="mt-1 text-lg font-bold text-slate-900">
          {title}
        </h3>

      </div>


      {rows.length === 0 ? (

        <EmptyState
          title="No data"
          description="No data available for this reporting period."
        />

      ) : (

        <div className="overflow-x-auto">

          <table className="min-w-full text-sm">

            <thead className="bg-slate-50">

              <tr>

                <th className="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Category
                </th>

                <th className="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Loans
                </th>

                <th className="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Amount
                </th>

              </tr>

            </thead>


            <tbody className="divide-y divide-slate-100">

              {rows.map(
                (
                  row,
                  index
                ) => (

                  <tr
                    key={`${row.label}-${index}`}
                    className="transition hover:bg-slate-50"
                  >

                    <td className="px-5 py-4 font-medium text-slate-900">
                      {row.label}
                    </td>

                    <td className="px-5 py-4 text-right text-slate-600">
                      {formatNumber(
                        row.count
                      )}
                    </td>

                    <td className="px-5 py-4 text-right font-semibold text-slate-900">
                      {formatMoney(
                        row.amount
                      )}
                    </td>

                  </tr>

                )
              )}

            </tbody>

          </table>

        </div>

      )}

    </div>
  );
}


// ============================================================
// STATUS PANEL
// ============================================================

function StatusPanel({
  title,
  eyebrow,
  items,
  formatNumber,
}: {
  title: string;
  eyebrow: string;
  items: Array<
    [
      string,
      number | undefined
    ]
  >;
  formatNumber: (
    value?: number
  ) => string;
}) {

  return (

    <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">

      <p className="text-xs font-semibold uppercase tracking-[0.15em] text-slate-400">
        {eyebrow}
      </p>

      <h2 className="mt-1 text-xl font-bold text-slate-900">
        {title}
      </h2>

      <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-3">

        {items.map(
          (
            [
              label,
              value,
            ],
            index
          ) => (

            <div
              key={`${label}-${index}`}
              className="rounded-2xl bg-slate-50 p-4 transition hover:bg-slate-100"
            >

              <p className="text-xs leading-5 text-slate-500">
                {label}
              </p>

              <p className="mt-1 text-xl font-bold text-slate-900">
                {formatNumber(
                  value
                )}
              </p>

            </div>

          )
        )}

      </div>

    </section>
  );
}


// ============================================================
// RISK CARD
// ============================================================

function RiskCard({
  label,
  ratio,
  amount,
  formatMoney,
  formatPercent,
  tone,
}: {
  label: string;
  ratio?: number;
  amount?: number;
  formatMoney: (
    value?: number
  ) => string;
  formatPercent: (
    value?: number
  ) => string;
  tone:
    | 'blue'
    | 'amber'
    | 'orange'
    | 'red';
}) {

  const tones = {
    blue: 'bg-blue-50 text-blue-700',
    amber: 'bg-amber-50 text-amber-700',
    orange: 'bg-orange-50 text-orange-700',
    red: 'bg-red-50 text-red-700',
  };

  return (

    <div className="rounded-2xl border border-slate-100 bg-slate-50 p-5">

      <div className="flex items-center justify-between">

        <p className="text-sm font-medium text-slate-500">
          {label}
        </p>

        <span
          className={`rounded-lg px-2 py-1 text-xs font-bold ${tones[tone]}`}
        >
          RISK
        </span>

      </div>

      <p className="mt-4 text-2xl font-bold text-slate-900">
        {formatPercent(
          ratio
        )}
      </p>

      <p className="mt-1 text-xs text-slate-500">
        {formatMoney(
          amount
        )}
      </p>

    </div>
  );
}


// ============================================================
// AGING CARD
// ============================================================

function AgingCard({
  label,
  value,
  formatMoney,
  tone,
}: {
  label: string;
  value?: number;
  formatMoney: (
    value?: number
  ) => string;
  tone:
    | 'blue'
    | 'cyan'
    | 'amber'
    | 'orange'
    | 'red'
    | 'slate';
}) {

  const border = {
    blue: 'border-blue-100',
    cyan: 'border-cyan-100',
    amber: 'border-amber-100',
    orange: 'border-orange-100',
    red: 'border-red-100',
    slate: 'border-slate-200',
  };

  return (

    <div
      className={`rounded-2xl border bg-white p-5 ${border[tone]}`}
    >

      <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
        {label}
      </p>

      <p className="mt-3 text-lg font-bold text-slate-900">
        {formatMoney(
          value
        )}
      </p>

    </div>
  );
}


// ============================================================
// KPI CARD
// ============================================================

function KpiCardPremium({
  label,
  value,
  icon,
  accent,
}: {
  label: string;
  value: string;
  icon: React.ReactNode;
  accent:
    | 'blue'
    | 'emerald'
    | 'violet'
    | 'amber'
    | 'cyan'
    | 'green'
    | 'orange'
    | 'red';
}) {

  const styles = {
    blue: 'bg-blue-50 text-blue-700',
    emerald: 'bg-emerald-50 text-emerald-700',
    violet: 'bg-violet-50 text-violet-700',
    amber: 'bg-amber-50 text-amber-700',
    cyan: 'bg-cyan-50 text-cyan-700',
    green: 'bg-green-50 text-green-700',
    orange: 'bg-orange-50 text-orange-700',
    red: 'bg-red-50 text-red-700',
  };

  return (

    <div className="group rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-lg">

      <div className="flex items-start justify-between gap-3">

        <div>

          <p className="text-sm font-medium text-slate-500">
            {label}
          </p>

          <p className="mt-3 break-words text-2xl font-bold tracking-tight text-slate-900">
            {value}
          </p>

        </div>

        <div
          className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${styles[accent]}`}
        >
          {icon}
        </div>

      </div>

    </div>
  );
}


// ============================================================
// METRIC TILE
// ============================================================

function MetricTile({
  label,
  value,
  tone = 'default',
}: {
  label: string;
  value: string;
  tone?:
    | 'default'
    | 'success'
    | 'danger';
}) {

  const valueClass =
    tone === 'success'
      ? 'text-emerald-700'
      : tone === 'danger'
        ? 'text-red-700'
        : 'text-slate-900';

  return (

    <div className="rounded-2xl bg-slate-50 p-5">

      <p className="text-xs font-medium uppercase tracking-wide text-slate-400">
        {label}
      </p>

      <p
        className={`mt-2 break-words text-xl font-bold ${valueClass}`}
      >
        {value}
      </p>

    </div>
  );
}


// ============================================================
// BALANCE INDICATOR
// ============================================================

function BalanceIndicatorPremium({
  label,
  balanced,
}: {
  label: string;
  balanced?: boolean;
}) {

  const isBalanced =
    balanced === true;

  return (

    <div
      className={
        isBalanced
          ? 'flex items-center justify-between rounded-2xl border border-emerald-200 bg-emerald-50 p-5'
          : 'flex items-center justify-between rounded-2xl border border-red-200 bg-red-50 p-5'
      }
    >

      <div>

        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
          {label}
        </p>

        <p
          className={
            isBalanced
              ? 'mt-1 font-bold text-emerald-700'
              : 'mt-1 font-bold text-red-700'
          }
        >
          {isBalanced
            ? 'Balanced'
            : 'Not Balanced'}
        </p>

      </div>

      <div
        className={
          isBalanced
            ? 'flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-100 text-emerald-700'
            : 'flex h-10 w-10 items-center justify-center rounded-xl bg-red-100 text-red-700'
        }
      >
        {isBalanced
          ? <CheckIcon />
          : <WarningIcon />}
      </div>

    </div>
  );
}


// ============================================================
// HEADER STATUS
// ============================================================

function HeaderStatus({
  label,
  value,
}: {
  label: string;
  value: string;
}) {

  return (

    <div className="rounded-2xl border border-white/10 bg-white/5 p-4">

      <p className="text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-500">
        {label}
      </p>

      <p className="mt-1 truncate text-sm font-semibold text-white">
        {value}
      </p>

    </div>
  );
}


// ============================================================
// MINI STAT
// ============================================================

function MiniStat({
  label,
  value,
}: {
  label: string;
  value: string;
}) {

  return (

    <div className="rounded-xl bg-slate-50 p-3">

      <p className="text-[10px] font-semibold uppercase tracking-wide text-slate-400">
        {label}
      </p>

      <p className="mt-1 truncate text-xs font-semibold text-slate-700">
        {value}
      </p>

    </div>
  );
}


// ============================================================
// SECTION HEADING
// ============================================================

function SectionHeading({
  eyebrow,
  title,
  description,
}: {
  eyebrow: string;
  title: string;
  description: string;
}) {

  return (

    <div className="mb-5">

      <p className="text-xs font-semibold uppercase tracking-[0.16em] text-blue-600">
        {eyebrow}
      </p>

      <h2 className="mt-1 text-xl font-bold tracking-tight text-slate-900">
        {title}
      </h2>

      <p className="mt-1 text-sm text-slate-500">
        {description}
      </p>

    </div>
  );
}


// ============================================================
// FIELD
// ============================================================

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {

  return (

    <label className="block">

      <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-slate-500">
        {label}
      </span>

      {children}

    </label>
  );
}


// ============================================================
// BADGE
// ============================================================

function Badge({
  children,
}: {
  children: React.ReactNode;
}) {

  return (

    <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs font-medium text-slate-600">
      {children}
    </span>
  );
}


// ============================================================
// STATUS BADGE
// ============================================================

function StatusBadge({
  status,
}: {
  status?: string;
}) {

  const normalized =
    String(
      status || 'UNKNOWN'
    ).toUpperCase();

  let className =
    'bg-slate-100 text-slate-600';

  if (
    normalized === 'ACTIVE' ||
    normalized === 'PAID' ||
    normalized === 'CLOSED'
  ) {
    className =
      'bg-emerald-50 text-emerald-700';
  }

  if (
    normalized === 'OVERDUE' ||
    normalized === 'PENDING'
  ) {
    className =
      'bg-amber-50 text-amber-700';
  }

  if (
    normalized === 'DEFAULTED' ||
    normalized === 'WRITTEN_OFF' ||
    normalized === 'REJECTED'
  ) {
    className =
      'bg-red-50 text-red-700';
  }

  return (

    <span
      className={`rounded-full px-2.5 py-1 text-xs font-semibold ${className}`}
    >
      {status ||
        'Unknown'}
    </span>
  );
}


// ============================================================
// DPD BADGE
// ============================================================

function DpdBadge({
  days,
}: {
  days?: number;
}) {

  const value =
    Number(days ?? 0);

  const className =
    value <= 0
      ? 'bg-emerald-50 text-emerald-700'
      : value <= 30
        ? 'bg-blue-50 text-blue-700'
        : value <= 90
          ? 'bg-amber-50 text-amber-700'
          : 'bg-red-50 text-red-700';

  return (

    <span
      className={`rounded-full px-2.5 py-1 text-xs font-bold ${className}`}
    >
      {value} days
    </span>
  );
}


// ============================================================
// SCORE BADGE
// ============================================================

function ScoreBadge({
  score,
}: {
  score?: number;
}) {

  if (
    score === undefined ||
    score === null
  ) {

    return (
      <span className="text-xs text-slate-400">
        —
      </span>
    );
  }

  const className =
    score >= 750
      ? 'bg-emerald-50 text-emerald-700'
      : score >= 650
        ? 'bg-blue-50 text-blue-700'
        : score >= 550
          ? 'bg-amber-50 text-amber-700'
          : 'bg-red-50 text-red-700';

  return (

    <span
      className={`rounded-full px-2.5 py-1 text-xs font-bold ${className}`}
    >
      {score}
    </span>
  );
}


// ============================================================
// CLIENT TYPE BADGE
// ============================================================

function ClientTypeBadge({
  type,
}: {
  type?: string;
}) {

  const normalized =
    String(
      type || ''
    ).toUpperCase();

  const credit =
    normalized ===
    'CREDIT_BUREAU';

  return (

    <span
      className={
        credit
          ? 'rounded-full bg-violet-50 px-2.5 py-1 text-xs font-semibold text-violet-700'
          : 'rounded-full bg-blue-50 px-2.5 py-1 text-xs font-semibold text-blue-700'
      }
    >
      {credit
        ? 'Credit Bureau'
        : 'BNR'}
    </span>
  );
}


// ============================================================
// CLIENT STATUS BADGE
// ============================================================

function ClientStatusBadge({
  revoked,
  active,
  status,
}: {
  revoked?: boolean;
  active?: boolean;
  status?: string;
}) {

  if (revoked) {

    return (
      <span className="rounded-full bg-red-50 px-2.5 py-1 text-xs font-semibold text-red-700">
        Revoked
      </span>
    );
  }

  if (
    active === false
  ) {

    return (
      <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600">
        Inactive
      </span>
    );
  }

  return (
    <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700">
      {status || 'Active'}
    </span>
  );
}


// ============================================================
// TABLE HEADER
// ============================================================

function TableHeader({
  children,
  align = 'left',
}: {
  children: React.ReactNode;
  align?: 'left' | 'right' | 'center';
}) {

  const alignment =
    align === 'right'
      ? 'text-right'
      : align === 'center'
        ? 'text-center'
        : 'text-left';

  return (

    <th
      className={`px-4 py-3 text-xs font-semibold uppercase tracking-wider text-slate-400 ${alignment}`}
    >
      {children}
    </th>
  );
}


// ============================================================
// ALERT
// ============================================================

function Alert({
  title,
  message,
  onDismiss,
}: {
  title: string;
  message: string;
  onDismiss?: () => void;
}) {

  return (

    <div className="mb-6 rounded-2xl border border-red-200 bg-red-50 p-4">

      <div className="flex gap-3">

        <div className="mt-0.5 text-red-600">
          <WarningIcon />
        </div>

        <div className="flex-1">

          <p className="font-semibold text-red-900">
            {title}
          </p>

          <p className="mt-1 text-sm text-red-700">
            {message}
          </p>

        </div>

        {onDismiss && (

          <button
            type="button"
            onClick={onDismiss}
            className="text-xs font-semibold text-red-700 hover:text-red-900"
          >
            Dismiss
          </button>

        )}

      </div>

    </div>
  );
}


// ============================================================
// EMPTY STATE
// ============================================================

function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon?: React.ReactNode;
  title: string;
  description: string;
  action?: React.ReactNode;
}) {

  return (

    <div className="flex flex-col items-center justify-center px-6 py-12 text-center">

      {icon && (

        <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100 text-slate-500">
          {icon}
        </div>

      )}

      <p className="font-semibold text-slate-800">
        {title}
      </p>

      <p className="mt-1 max-w-md text-sm text-slate-500">
        {description}
      </p>

      {action && (

        <div className="mt-5">
          {action}
        </div>

      )}

    </div>
  );
}


// ============================================================
// TABLE SKELETON
// ============================================================

function TableSkeleton() {

  return (

    <div className="space-y-3">

      {Array.from({
        length: 6,
      }).map(
        (_, index) => (

          <div
            key={index}
            className="h-14 animate-pulse rounded-xl bg-slate-100"
          />

        )
      )}

    </div>
  );
}


// ============================================================
// NORMALIZE ARRAY
// ============================================================

function normalizeArray<T>(
  value: unknown
): T[] {

  if (
    Array.isArray(value)
  ) {
    return value as T[];
  }

  if (
    value &&
    typeof value === 'object'
  ) {

    const record =
      value as UnknownRecord;

    const candidates = [

      record.data,
      record.content,
      record.items,
      record.results,
      record.records,

    ];

    for (
      const candidate of candidates
    ) {

      if (
        Array.isArray(candidate)
      ) {
        return candidate as T[];
      }

      if (
        candidate &&
        typeof candidate === 'object'
      ) {

        const nested =
          normalizeArray<T>(
            candidate
          );

        if (
          nested.length > 0
        ) {
          return nested;
        }
      }
    }

  }

  return [];
}


// ============================================================
// MASK API KEY
// ============================================================

function maskApiKey(
  key: string
): string {

  if (
    key.length <= 8
  ) {
    return '••••••••';
  }

  return `${key.slice(
    0,
    4
  )}••••••••${key.slice(
    -4
  )}`;
}


// ============================================================
// PAR 30
// ============================================================

function getPar30Amount(
  summary: BnrSummary | null
): number {

  if (!summary) {
    return 0;
  }

  return (
    Number(
      summary.par31To60Amount ?? 0
    ) +
    Number(
      summary.par61To90Amount ?? 0
    ) +
    Number(
      summary.par91To180Amount ?? 0
    ) +
    Number(
      summary.par181To365Amount ?? 0
    ) +
    Number(
      summary.parOver365Amount ?? 0
    )
  );
}


// ============================================================
// PAR 60
// ============================================================

function getPar60Amount(
  summary: BnrSummary | null
): number {

  if (!summary) {
    return 0;
  }

  return (
    Number(
      summary.par61To90Amount ?? 0
    ) +
    Number(
      summary.par91To180Amount ?? 0
    ) +
    Number(
      summary.par181To365Amount ?? 0
    ) +
    Number(
      summary.parOver365Amount ?? 0
    )
  );
}


// ============================================================
// PAR 90
// ============================================================

function getPar90Amount(
  summary: BnrSummary | null
): number {

  if (!summary) {
    return 0;
  }

  return (
    Number(
      summary.par91To180Amount ?? 0
    ) +
    Number(
      summary.par181To365Amount ?? 0
    ) +
    Number(
      summary.parOver365Amount ?? 0
    )
  );
}


// ============================================================
// SVG ICONS
// ============================================================

function ShieldIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
    >
      <path
        d="M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6l7-3z"
      />
      <path d="M9 12l2 2 4-4" />
    </svg>
  );
}


function BuildingIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
    >
      <path d="M4 21V5l8-3 8 3v16" />
      <path d="M8 9h1M15 9h1M8 13h1M15 13h1M8 17h1M15 17h1" />
      <path d="M10 21v-4h4v4" />
    </svg>
  );
}


function LoanIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
    >
      <rect
        x="3"
        y="5"
        width="18"
        height="14"
        rx="2"
      />
      <path d="M7 10h10M7 14h6" />
    </svg>
  );
}


function ActivityIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
    >
      <path d="M3 12h4l3-7 4 14 3-7h4" />
    </svg>
  );
}


function MoneyIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
    >
      <circle
        cx="12"
        cy="12"
        r="9"
      />
      <path d="M12 7v10M15 9c-.8-1-2-1.5-3-1.5-1.5 0-2.5.8-2.5 2s1 2 2.5 2 2.5.8 2.5 2-1 2-2.5 2c-1 0-2.2-.5-3-1.5" />
    </svg>
  );
}


function WalletIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
    >
      <path d="M4 6h15a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2V7a2 2 0 011-1z" />
      <path d="M16 13h5" />
      <circle
        cx="16"
        cy="13"
        r="1"
      />
    </svg>
  );
}


function PercentIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
    >
      <path d="M7 17L17 7" />
      <circle
        cx="7"
        cy="7"
        r="2"
      />
      <circle
        cx="17"
        cy="17"
        r="2"
      />
    </svg>
  );
}


function CollectionsIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
    >
      <path d="M5 4h14v16H5z" />
      <path d="M8 8h8M8 12h8M8 16h5" />
    </svg>
  );
}


function ClockIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
    >
      <circle
        cx="12"
        cy="12"
        r="9"
      />
      <path d="M12 7v5l3 2" />
    </svg>
  );
}


function WarningIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
    >
      <path d="M12 3l10 18H2L12 3z" />
      <path d="M12 9v5M12 17h.01" />
    </svg>
  );
}


function RefreshIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-4 w-4"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
    >
      <path d="M20 11a8 8 0 00-14-5L4 8" />
      <path d="M4 4v4h4" />
      <path d="M4 13a8 8 0 0014 5l2-2" />
      <path d="M20 20v-4h-4" />
    </svg>
  );
}


function DownloadIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-4 w-4"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
    >
      <path d="M12 3v12" />
      <path d="M7 10l5 5 5-5" />
      <path d="M4 21h16" />
    </svg>
  );
}


function CheckIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
    >
      <path d="M5 12l4 4L19 7" />
    </svg>
  );
}


function CreditIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
    >
      <rect
        x="3"
        y="5"
        width="18"
        height="14"
        rx="2"
      />
      <path d="M7 9h5M7 13h3M15 13h3" />
      <circle
        cx="17"
        cy="9"
        r="1.5"
      />
    </svg>
  );
}


function KeyIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
    >
      <circle
        cx="8"
        cy="15"
        r="4"
      />
      <path d="M11 12l8-8M16 7l2 2M14 9l2 2" />
    </svg>
  );
}


function PlusIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-4 w-4"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
    >
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}


function SearchIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
    >
      <circle
        cx="11"
        cy="11"
        r="7"
      />
      <path d="M16 16l5 5" />
    </svg>
  );
}