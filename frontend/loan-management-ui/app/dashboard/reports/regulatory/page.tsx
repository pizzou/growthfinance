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
  type CreditRecord,
} from '@/services/regulatoryService';


// ============================================================
// TYPES
// ============================================================

type DownloadingFormat =
  | ExportFormat
  | null;

type RegulatoryTab =
  | 'bnr'
  | 'credit-bureau'
  | 'api-clients';

type ApiClient = {
  id: number;
  name: string;
  clientType: 'BNR' | 'CREDIT_BUREAU';
  contactEmail?: string;
  description?: string;
  expiresAt?: string | null;
  status?: string;
  active?: boolean;
  apiKey?: string;
  key?: string;
  createdAt?: string;
  revokedAt?: string | null;
};

type CreditBureauPreviewResponse =
  | CreditRecord[]
  | {
      records?: CreditRecord[];
      content?: CreditRecord[];
      data?: CreditRecord[];
      totalRecords?: number;
    };


// ============================================================
// PAGE
// ============================================================

export default function RegulatoryReportingPage() {

  // ==========================================================
  // MAIN TAB
  // ==========================================================

  const [activeTab, setActiveTab] =
    useState<RegulatoryTab>('bnr');


  // ==========================================================
  // BNR FILTERS
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
  // BNR REPORT DATA
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
  // CREDIT BUREAU FILTERS
  // ==========================================================

  const [creditFrom, setCreditFrom] =
    useState<string>('');

  const [creditTo, setCreditTo] =
    useState<string>('');

  const [creditBranchId, setCreditBranchId] =
    useState<string>('');

  const [creditRecords, setCreditRecords] =
    useState<CreditRecord[]>([]);


  // ==========================================================
  // API CLIENTS
  // ==========================================================

  const [apiClients, setApiClients] =
    useState<ApiClient[]>([]);

  const [showCreateClient, setShowCreateClient] =
    useState<boolean>(false);

  const [newClientName, setNewClientName] =
    useState<string>('');

  const [newClientType, setNewClientType] =
    useState<'BNR' | 'CREDIT_BUREAU'>('BNR');

  const [newClientEmail, setNewClientEmail] =
    useState<string>('');

  const [newClientDescription, setNewClientDescription] =
    useState<string>('');

  const [newClientExpiresAt, setNewClientExpiresAt] =
    useState<string>('');


  // ==========================================================
  // UI STATE
  // ==========================================================

  const [loading, setLoading] =
    useState<boolean>(true);

  const [creditLoading, setCreditLoading] =
    useState<boolean>(false);

  const [apiClientsLoading, setApiClientsLoading] =
    useState<boolean>(false);

  const [downloadingFormat, setDownloadingFormat] =
    useState<DownloadingFormat>(null);

  const [creditDownloadingFormat, setCreditDownloadingFormat] =
    useState<DownloadingFormat>(null);

  const [error, setError] =
    useState<string | null>(null);

  const [successMessage, setSuccessMessage] =
    useState<string | null>(null);

  const [revealedApiKey, setRevealedApiKey] =
    useState<number | null>(null);


  // ==========================================================
  // BNR PARAMETERS
  // ==========================================================

  const reportParams =
    useMemo<BnrReportParams>(() => {

      const params: BnrReportParams = {
        period,
      };

      if (branchId) {
        params.branchId =
          Number(branchId);
      }

      if (period === 'CUSTOM') {

        if (from) {
          params.from = from;
        }

        if (to) {
          params.to = to;
        }
      }

      return params;

    }, [
      period,
      from,
      to,
      branchId,
    ]);


  // ==========================================================
  // VALIDATE BNR
  // ==========================================================

  const validateBnrFilters =
    useCallback((): string | null => {

      if (period !== 'CUSTOM') {
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
  // LOAD BNR REPORT
  // ==========================================================

  const loadReport =
    useCallback(async (): Promise<void> => {

      const validationError =
        validateBnrFilters();

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
      validateBnrFilters,
    ]);


  // ==========================================================
  // LOAD CREDIT BUREAU
  // ==========================================================

  const loadCreditBureau =
    useCallback(async (): Promise<void> => {

      try {

        setCreditLoading(true);

        setError(null);

        const response =
          await regulatoryApi.creditBureauPreview({
            branchId:
              creditBranchId
                ? Number(creditBranchId)
                : undefined,

            from:
              creditFrom || undefined,

            to:
              creditTo || undefined,
          });

        const normalized =
          normalizeCreditRecords(
            response as CreditBureauPreviewResponse
          );

        setCreditRecords(
          normalized
        );

      } catch (err) {

        console.error(
          'Failed to load Credit Bureau preview:',
          err
        );

        setError(
          regulatoryApi.getErrorMessage(
            err,
            'Failed to load the Credit Bureau preview.'
          )
        );

      } finally {

        setCreditLoading(false);
      }

    }, [
      creditBranchId,
      creditFrom,
      creditTo,
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

        setError(null);

        const response =
          await regulatoryApi.listApiClients();

        const clients =
          normalizeApiClients(
            response
          );

        setApiClients(
          clients
        );

      } catch (err) {

        console.error(
          'Failed to load API clients:',
          err
        );

        setError(
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
  // LOAD TAB DATA
  // ==========================================================

  useEffect(() => {

    if (
      activeTab === 'credit-bureau' &&
      creditRecords.length === 0
    ) {

      void loadCreditBureau();
    }

    if (
      activeTab === 'api-clients'
    ) {

      void loadApiClients();
    }

  }, [
    activeTab,
    creditRecords.length,
    loadCreditBureau,
    loadApiClients,
  ]);


  // ==========================================================
  // DOWNLOAD BNR
  // ==========================================================

  const downloadBnr =
    useCallback(
      async (
        format: ExportFormat
      ): Promise<void> => {

        const validationError =
          validateBnrFilters();

        if (validationError) {

          setError(
            validationError
          );

          return;
        }

        try {

          setDownloadingFormat(
            format
          );

          setError(null);

          await regulatoryApi.bnrExport(
            format,
            reportParams
          );

          setSuccessMessage(
            `BNR ${format.toUpperCase()} report downloaded successfully.`
          );

        } catch (err) {

          console.error(
            'BNR export failed:',
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
        validateBnrFilters,
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

          setError(null);

          await regulatoryApi.creditBureauExport(
            format,
            {
              branchId:
                creditBranchId
                  ? Number(creditBranchId)
                  : undefined,

              from:
                creditFrom || undefined,

              to:
                creditTo || undefined,
            }
          );

          setSuccessMessage(
            `Credit Bureau ${format.toUpperCase()} export downloaded successfully.`
          );

        } catch (err) {

          console.error(
            'Credit Bureau export failed:',
            err
          );

          setError(
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
        creditBranchId,
        creditFrom,
        creditTo,
      ]
    );


  // ==========================================================
  // CREATE API CLIENT
  // ==========================================================

  const createApiClient =
    useCallback(async (): Promise<void> => {

      if (!newClientName.trim()) {

        setError(
          'API client name is required.'
        );

        return;
      }

      try {

        setError(null);

        const response =
          await regulatoryApi.createApiClient({
            name:
              newClientName.trim(),

            clientType:
              newClientType,

            contactEmail:
              newClientEmail.trim() ||
              undefined,

            description:
              newClientDescription.trim() ||
              undefined,

            expiresAt:
              newClientExpiresAt ||
              null,
          });

        const created =
          normalizeApiClient(
            response
          );

        if (created) {

          setApiClients(
            current => [
              created,
              ...current,
            ]
          );
        }

        setShowCreateClient(
          false
        );

        setNewClientName(
          ''
        );

        setNewClientEmail(
          ''
        );

        setNewClientDescription(
          ''
        );

        setNewClientExpiresAt(
          ''
        );

        setSuccessMessage(
          'Regulatory API client created successfully.'
        );

      } catch (err) {

        console.error(
          'Failed to create API client:',
          err
        );

        setError(
          regulatoryApi.getErrorMessage(
            err,
            'Failed to create API client.'
          )
        );
      }

    }, [
      newClientName,
      newClientType,
      newClientEmail,
      newClientDescription,
      newClientExpiresAt,
    ]);


  // ==========================================================
  // REVOKE API CLIENT
  // ==========================================================

  const revokeApiClient =
    useCallback(
      async (
        client: ApiClient
      ): Promise<void> => {

        const confirmed =
          window.confirm(
            `Revoke API client "${client.name}"? This action should only be used when the key must no longer be accepted.`
          );

        if (!confirmed) {
          return;
        }

        const reason =
          window.prompt(
            'Reason for revocation:'
          );

        try {

          setError(null);

          await regulatoryApi.revokeApiClient(
            client.id,
            reason || undefined
          );

          setSuccessMessage(
            'API client revoked successfully.'
          );

          await loadApiClients();

        } catch (err) {

          console.error(
            'Failed to revoke API client:',
            err
          );

          setError(
            regulatoryApi.getErrorMessage(
              err,
              'Failed to revoke API client.'
            )
          );
        }

      },
      [
        loadApiClients,
      ]
    );


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

        return `${Number(value ?? 0).toFixed(2)}%`;
      },
      []
    );


  // ==========================================================
  // LOADING
  // ==========================================================

  if (
    loading &&
    activeTab === 'bnr'
  ) {

    return (
      <PageShell>

        <LoadingState />

      </PageShell>
    );
  }


  // ==========================================================
  // RENDER
  // ==========================================================

  return (

    <PageShell>

      <div className="mx-auto max-w-[1500px] space-y-6 p-4 md:p-6">


        {/* ================================================== */}
        {/* HEADER */}
        {/* ================================================== */}

        <div className="overflow-hidden rounded-2xl bg-gradient-to-br from-slate-950 via-slate-900 to-blue-950 p-6 text-white shadow-xl md:p-8">

          <div className="flex flex-col gap-6 xl:flex-row xl:items-end xl:justify-between">

            <div>

              <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/10 px-3 py-1 text-xs font-medium text-blue-100">

                <span className="h-2 w-2 rounded-full bg-emerald-400" />

                Regulatory Compliance Center

              </div>

              <h1 className="text-3xl font-bold tracking-tight md:text-4xl">
                Regulatory Reporting
              </h1>

              <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-300 md:text-base">
                Manage BNR regulatory reports, Credit Bureau submissions,
                financial statements, exports and regulatory API credentials
                from one centralized workspace.
              </p>

            </div>


            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">

              <HeaderStat
                label="BNR Status"
                value={
                  summary?.reportStatus ||
                  'Ready'
                }
              />

              <HeaderStat
                label="Loans"
                value={
                  formatNumber(
                    summary?.totalLoans
                  )
                }
              />

              <HeaderStat
                label="Borrowers"
                value={
                  formatNumber(
                    summary?.totalBorrowers
                  )
                }
              />

            </div>

          </div>

        </div>


        {/* ================================================== */}
        {/* ALERTS */}
        {/* ================================================== */}

        {successMessage && (

          <Alert
            type="success"
            message={successMessage}
            onClose={() =>
              setSuccessMessage(null)
            }
          />

        )}


        {error && (

          <Alert
            type="error"
            message={error}
            onClose={() =>
              setError(null)
            }
          />

        )}


        {/* ================================================== */}
        {/* NAVIGATION */}
        {/* ================================================== */}

        <div className="rounded-2xl border border-slate-200 bg-white p-2 shadow-sm">

          <div className="grid grid-cols-1 gap-2 md:grid-cols-3">

            <TabButton
              active={
                activeTab === 'bnr'
              }
              title="BNR Reporting"
              description="Regulatory portfolio and financial reports"
              icon="▣"
              onClick={() =>
                setActiveTab('bnr')
              }
            />

            <TabButton
              active={
                activeTab === 'credit-bureau'
              }
              title="Credit Bureau"
              description="Preview and export borrower credit records"
              icon="◉"
              onClick={() =>
                setActiveTab('credit-bureau')
              }
            />

            <TabButton
              active={
                activeTab === 'api-clients'
              }
              title="API Clients & Keys"
              description="Manage BNR and Credit Bureau credentials"
              icon="⚿"
              onClick={() =>
                setActiveTab('api-clients')
              }
            />

          </div>

        </div>


        {/* ================================================== */}
        {/* BNR */}
        {/* ================================================== */}

        {activeTab === 'bnr' && (

          <BnrSection
            period={period}
            setPeriod={setPeriod}
            from={from}
            setFrom={setFrom}
            to={to}
            setTo={setTo}
            branchId={branchId}
            setBranchId={setBranchId}
            summary={summary}
            financialStatement={financialStatement}
            loanTypeBreakdown={loanTypeBreakdown}
            branchBreakdown={branchBreakdown}
            genderBreakdown={genderBreakdown}
            formatMoney={formatMoney}
            formatNumber={formatNumber}
            formatPercent={formatPercent}
            loading={loading}
            downloadingFormat={downloadingFormat}
            onRefresh={() =>
              void loadReport()
            }
            onDownload={(format) =>
              void downloadBnr(format)
            }
          />

        )}


        {/* ================================================== */}
        {/* CREDIT BUREAU */}
        {/* ================================================== */}

        {activeTab === 'credit-bureau' && (

          <CreditBureauSection
            from={creditFrom}
            setFrom={setCreditFrom}
            to={creditTo}
            setTo={setCreditTo}
            branchId={creditBranchId}
            setBranchId={setCreditBranchId}
            records={creditRecords}
            loading={creditLoading}
            downloadingFormat={
              creditDownloadingFormat
            }
            onRefresh={() =>
              void loadCreditBureau()
            }
            onDownload={(format) =>
              void downloadCreditBureau(format)
            }
            formatMoney={formatMoney}
            formatNumber={formatNumber}
          />

        )}


        {/* ================================================== */}
        {/* API CLIENTS */}
        {/* ================================================== */}

        {activeTab === 'api-clients' && (

          <ApiClientsSection
            clients={apiClients}
            loading={apiClientsLoading}
            showCreate={showCreateClient}
            setShowCreate={setShowCreateClient}
            name={newClientName}
            setName={setNewClientName}
            clientType={newClientType}
            setClientType={setNewClientType}
            email={newClientEmail}
            setEmail={setNewClientEmail}
            description={newClientDescription}
            setDescription={setNewClientDescription}
            expiresAt={newClientExpiresAt}
            setExpiresAt={setNewClientExpiresAt}
            revealedApiKey={revealedApiKey}
            setRevealedApiKey={setRevealedApiKey}
            onCreate={() =>
              void createApiClient()
            }
            onRefresh={() =>
              void loadApiClients()
            }
            onRevoke={(client) =>
              void revokeApiClient(client)
            }
          />

        )}


        {/* ================================================== */}
        {/* FOOTER */}
        {/* ================================================== */}

        <div className="border-t border-slate-200 pt-6 pb-10 text-center text-xs text-slate-400">

          Regulatory Reporting Center

          {' • '}

          {summary?.organizationName ||
            'Organization'}

          {summary?.reportReference && (
            <>
              {' • '}
              {summary.reportReference}
            </>
          )}

        </div>

      </div>

    </PageShell>
  );
}


// ============================================================
// PAGE SHELL
// ============================================================

function PageShell({
  children,
}: {
  children: React.ReactNode;
}) {

  return (

    <div className="min-h-screen bg-slate-50">

      {children}

    </div>
  );
}


// ============================================================
// LOADING
// ============================================================

function LoadingState() {

  return (

    <div className="mx-auto max-w-[1500px] space-y-6 p-6">

      <div className="h-48 animate-pulse rounded-2xl bg-slate-200" />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">

        {Array.from({
          length: 3,
        }).map((_, index) => (

          <div
            key={index}
            className="h-24 animate-pulse rounded-2xl bg-slate-200"
          />

        ))}

      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-4">

        {Array.from({
          length: 8,
        }).map((_, index) => (

          <div
            key={index}
            className="h-32 animate-pulse rounded-2xl bg-slate-200"
          />

        ))}

      </div>

    </div>
  );
}


// ============================================================
// HEADER STAT
// ============================================================

function HeaderStat({
  label,
  value,
}: {
  label: string;
  value: string;
}) {

  return (

    <div className="rounded-xl border border-white/10 bg-white/10 p-3 backdrop-blur">

      <p className="text-xs text-slate-400">
        {label}
      </p>

      <p className="mt-1 truncate text-sm font-semibold text-white">
        {value}
      </p>

    </div>
  );
}


// ============================================================
// TAB BUTTON
// ============================================================

function TabButton({
  active,
  title,
  description,
  icon,
  onClick,
}: {
  active: boolean;
  title: string;
  description: string;
  icon: string;
  onClick: () => void;
}) {

  return (

    <button
      type="button"
      onClick={onClick}
      className={
        active
          ? 'rounded-xl border border-blue-200 bg-blue-50 p-4 text-left shadow-sm'
          : 'rounded-xl border border-transparent p-4 text-left hover:border-slate-200 hover:bg-slate-50'
      }
    >

      <div className="flex items-start gap-3">

        <div
          className={
            active
              ? 'flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-blue-600 text-lg text-white'
              : 'flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-lg text-slate-600'
          }
        >
          {icon}
        </div>

        <div>

          <p
            className={
              active
                ? 'font-semibold text-blue-900'
                : 'font-semibold text-slate-900'
            }
          >
            {title}
          </p>

          <p className="mt-1 text-xs leading-5 text-slate-500">
            {description}
          </p>

        </div>

      </div>

    </button>
  );
}


// ============================================================
// ALERT
// ============================================================

function Alert({
  type,
  message,
  onClose,
}: {
  type: 'success' | 'error';
  message: string;
  onClose: () => void;
}) {

  const success =
    type === 'success';

  return (

    <div
      className={
        success
          ? 'flex items-start justify-between gap-4 rounded-xl border border-emerald-200 bg-emerald-50 p-4'
          : 'flex items-start justify-between gap-4 rounded-xl border border-red-200 bg-red-50 p-4'
      }
    >

      <div>

        <p
          className={
            success
              ? 'font-semibold text-emerald-800'
              : 'font-semibold text-red-800'
          }
        >
          {success
            ? 'Success'
            : 'Report error'}
        </p>

        <p
          className={
            success
              ? 'mt-1 text-sm text-emerald-700'
              : 'mt-1 text-sm text-red-700'
          }
        >
          {message}
        </p>

      </div>

      <button
        type="button"
        onClick={onClose}
        className="text-sm font-medium text-slate-500 hover:text-slate-900"
      >
        Dismiss
      </button>

    </div>
  );
}


// ============================================================
// BNR SECTION
// ============================================================

function BnrSection({
  period,
  setPeriod,
  from,
  setFrom,
  to,
  setTo,
  branchId,
  setBranchId,
  summary,
  financialStatement,
  loanTypeBreakdown,
  branchBreakdown,
  genderBreakdown,
  formatMoney,
  formatNumber,
  formatPercent,
  loading,
  downloadingFormat,
  onRefresh,
  onDownload,
}: {
  period: RegulatoryPeriod;
  setPeriod: React.Dispatch<
    React.SetStateAction<RegulatoryPeriod>
  >;

  from: string;
  setFrom: React.Dispatch<
    React.SetStateAction<string>
  >;

  to: string;
  setTo: React.Dispatch<
    React.SetStateAction<string>
  >;

  branchId: string;
  setBranchId: React.Dispatch<
    React.SetStateAction<string>
  >;

  summary: BnrSummary | null;
  financialStatement: BnrFinancialStatementReport | null;

  loanTypeBreakdown: BreakdownRow[];
  branchBreakdown: BreakdownRow[];
  genderBreakdown: BreakdownRow[];

  formatMoney: (
    value?: number
  ) => string;

  formatNumber: (
    value?: number
  ) => string;

  formatPercent: (
    value?: number
  ) => string;

  loading: boolean;

  downloadingFormat: DownloadingFormat;

  onRefresh: () => void;

  onDownload: (
    format: ExportFormat
  ) => void;
}) {

  return (

    <div className="space-y-6">


      {/* ================================================== */}
      {/* REPORT CONTROL */}
      {/* ================================================== */}

      <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">

        <div className="mb-5 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">

          <div>

            <h2 className="text-lg font-bold text-slate-900">
              BNR Regulatory Report
            </h2>

            <p className="mt-1 text-sm text-slate-500">
              Select the reporting period and generate the
              regulatory portfolio and financial statement.
            </p>

          </div>


          <div className="flex flex-wrap gap-2">

            <ExportButton
              label="PDF"
              format="pdf"
              loading={
                downloadingFormat === 'pdf'
              }
              disabled={
                downloadingFormat !== null
              }
              onClick={onDownload}
            />

            <ExportButton
              label="Excel"
              format="xlsx"
              loading={
                downloadingFormat === 'xlsx'
              }
              disabled={
                downloadingFormat !== null
              }
              onClick={onDownload}
            />

            <ExportButton
              label="CSV"
              format="csv"
              loading={
                downloadingFormat === 'csv'
              }
              disabled={
                downloadingFormat !== null
              }
              onClick={onDownload}
            />

          </div>

        </div>


        <div className="grid grid-cols-1 gap-4 md:grid-cols-4">

          <Field label="Reporting Period">

            <select
              value={period}
              onChange={event =>
                setPeriod(
                  event.target.value as RegulatoryPeriod
                )
              }
              className="input"
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
              className="input"
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
              className="input"
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
              className="input"
            />

          </Field>

        </div>


        <div className="mt-5 flex justify-end">

          <button
            type="button"
            onClick={onRefresh}
            disabled={loading}
            className="rounded-xl bg-slate-900 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {loading
              ? 'Refreshing...'
              : 'Refresh Report'}
          </button>

        </div>

      </div>


      {/* ================================================== */}
      {/* ORGANIZATION */}
      {/* ================================================== */}

      {summary && (

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">

          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">

            <div>

              <p className="text-xs font-semibold uppercase tracking-wider text-blue-600">
                Reporting Institution
              </p>

              <h2 className="mt-1 text-xl font-bold text-slate-900">
                {summary.organizationName ||
                  'Organization'}
              </h2>

              <div className="mt-2 flex flex-wrap gap-2">

                <Badge>
                  BNR: {
                    summary.bnrInstitutionCode ||
                    'Not configured'
                  }
                </Badge>

                <Badge>
                  Currency: {
                    summary.currency ||
                    'RWF'
                  }
                </Badge>

                <Badge>
                  Status: {
                    summary.reportStatus ||
                    '—'
                  }
                </Badge>

              </div>

            </div>


            <div className="rounded-xl bg-slate-50 p-4 text-sm text-slate-600">

              <p>
                Reporting period
              </p>

              <p className="mt-1 font-semibold text-slate-900">
                {summary.periodStart ||
                  '—'}
                {' → '}
                {summary.periodEnd ||
                  '—'}
              </p>

            </div>

          </div>

        </div>

      )}


      {/* ================================================== */}
      {/* KPI */}
      {/* ================================================== */}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">

        <KpiCard
          label="Total Loans"
          value={formatNumber(summary?.totalLoans)}
          icon="▤"
        />

        <KpiCard
          label="Active Loans"
          value={formatNumber(summary?.activeLoans)}
          icon="↗"
        />

        <KpiCard
          label="Principal Disbursed"
          value={formatMoney(summary?.totalPrincipalDisbursed)}
          icon="₣"
        />

        <KpiCard
          label="Outstanding Principal"
          value={formatMoney(summary?.outstandingPrincipal)}
          icon="◈"
        />

        <KpiCard
          label="Interest Collected"
          value={formatMoney(summary?.totalInterestCollected)}
          icon="%"
        />

        <KpiCard
          label="Total Collected"
          value={formatMoney(summary?.totalAmountCollected)}
          icon="✓"
        />

        <KpiCard
          label="Overdue Loans"
          value={formatNumber(summary?.overdueLoans)}
          icon="!"
        />

        <KpiCard
          label="Defaulted Loans"
          value={formatNumber(summary?.defaultedLoans)}
          icon="!"
        />

      </div>


      {/* ================================================== */}
      {/* PORTFOLIO QUALITY */}
      {/* ================================================== */}

      <Section title="Portfolio Quality">

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">

          <MetricCard
            label="PAR"
            value={formatPercent(summary?.parRatio)}
            secondary={formatMoney(summary?.parAmount)}
          />

          <MetricCard
            label="PAR > 30 Days"
            value={formatPercent(summary?.par30Ratio)}
            secondary={formatMoney(getPar30Amount(summary))}
          />

          <MetricCard
            label="PAR > 60 Days"
            value={formatPercent(summary?.par60Ratio)}
            secondary={formatMoney(getPar60Amount(summary))}
          />

          <MetricCard
            label="PAR > 90 Days"
            value={formatPercent(summary?.par90Ratio)}
            secondary={formatMoney(getPar90Amount(summary))}
          />

          <MetricCard
            label="NPL Ratio"
            value={formatPercent(summary?.nplRatio)}
            secondary={formatMoney(summary?.nplAmount)}
          />

          <MetricCard
            label="NPL Loans"
            value={formatNumber(summary?.nplLoanCount)}
          />

          <MetricCard
            label="Loans > 30 DPD"
            value={formatNumber(summary?.loansOver30Days)}
          />

          <MetricCard
            label="Loans > 90 DPD"
            value={formatNumber(summary?.loansOver90Days)}
          />

        </div>

      </Section>


      {/* ================================================== */}
      {/* PAR AGING */}
      {/* ================================================== */}

      <Section title="Portfolio at Risk Aging">

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">

          <MetricCard
            label="1–30 Days"
            value={formatMoney(summary?.par1To30Amount)}
          />

          <MetricCard
            label="31–60 Days"
            value={formatMoney(summary?.par31To60Amount)}
          />

          <MetricCard
            label="61–90 Days"
            value={formatMoney(summary?.par61To90Amount)}
          />

          <MetricCard
            label="91–180 Days"
            value={formatMoney(summary?.par91To180Amount)}
          />

          <MetricCard
            label="181–365 Days"
            value={formatMoney(summary?.par181To365Amount)}
          />

          <MetricCard
            label="Over 365 Days"
            value={formatMoney(summary?.parOver365Amount)}
          />

        </div>

      </Section>


      {/* ================================================== */}
      {/* BORROWERS */}
      {/* ================================================== */}

      <Section title="Borrower & Credit Information">

        <div className="grid grid-cols-2 gap-4 md:grid-cols-4">

          <StatusItem
            label="Total Borrowers"
            value={summary?.totalBorrowers}
          />

          <StatusItem
            label="Active Borrowers"
            value={summary?.activeBorrowers}
          />

          <StatusItem
            label="Male Borrowers"
            value={summary?.maleBorrowers}
          />

          <StatusItem
            label="Female Borrowers"
            value={summary?.femaleBorrowers}
          />

          <StatusItem
            label="Youth Borrowers"
            value={summary?.youthBorrowers}
          />

          <StatusItem
            label="Adult Borrowers"
            value={summary?.adultBorrowers}
          />

          <StatusItem
            label="Senior Borrowers"
            value={summary?.seniorBorrowers}
          />

          <StatusItem
            label="Multiple Loans"
            value={summary?.borrowersWithMultipleLoans}
          />

          <StatusItem
            label="Credit Checked"
            value={summary?.borrowersCreditChecked}
          />

          <StatusItem
            label="Default History"
            value={summary?.borrowersWithDefaultHistory}
          />

          <StatusItem
            label="Active Listings"
            value={summary?.borrowersWithActiveListing}
          />

          <StatusItem
            label="Multiple Facilities"
            value={summary?.borrowersWithMultipleFacilities}
          />

        </div>


        <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">

          <MetricCard
            label="External Debt"
            value={formatMoney(summary?.totalExternalDebt)}
          />

          <MetricCard
            label="Outstanding Total"
            value={formatMoney(summary?.totalOutstanding)}
          />

        </div>

      </Section>


      {/* ================================================== */}
      {/* REPAYMENT */}
      {/* ================================================== */}

      <Section title="Repayment Performance">

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">

          <MetricCard
            label="Principal Collected"
            value={formatMoney(summary?.totalPrincipalCollected)}
          />

          <MetricCard
            label="Interest Collected"
            value={formatMoney(summary?.totalInterestCollected)}
          />

          <MetricCard
            label="Fees Collected"
            value={formatMoney(summary?.totalFeesCollected)}
          />

          <MetricCard
            label="Total Collected"
            value={formatMoney(summary?.totalAmountCollected)}
          />

          <MetricCard
            label="Unpaid Interest"
            value={formatMoney(summary?.interestAccruedUnpaid)}
          />

          <MetricCard
            label="Unpaid Fees"
            value={formatMoney(summary?.feesAccruedUnpaid)}
          />

          <MetricCard
            label="Missed Payments"
            value={formatNumber(summary?.missedPayments)}
          />

          <MetricCard
            label="Overdue Payments"
            value={formatNumber(summary?.overduePayments)}
          />

        </div>

      </Section>


      {/* ================================================== */}
      {/* FINANCIAL STATEMENT */}
      {/* ================================================== */}

      <FinancialStatementSection
        report={financialStatement}
        formatMoney={formatMoney}
      />


      {/* ================================================== */}
      {/* DATA QUALITY */}
      {/* ================================================== */}

      <Section title="Data Quality & Validation">

        <div className="grid grid-cols-2 gap-4 md:grid-cols-5">

          <StatusItem
            label="Missing Borrower"
            value={summary?.loansMissingBorrower}
          />

          <StatusItem
            label="Missing National ID"
            value={summary?.borrowersMissingNationalId}
          />

          <StatusItem
            label="Missing Branch"
            value={summary?.loansMissingBranch}
          />

          <StatusItem
            label="Missing Currency"
            value={summary?.loansMissingCurrency}
          />

          <StatusItem
            label="Missing Schedule"
            value={summary?.loansMissingRepaymentSchedule}
          />

        </div>


        {summary?.dataQualityWarnings &&
          summary.dataQualityWarnings.length > 0 && (

          <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4">

            <p className="font-semibold text-amber-900">
              Validation warnings
            </p>

            <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-amber-800">

              {summary.dataQualityWarnings.map(
                (warning, index) => (

                  <li
                    key={`${warning}-${index}`}
                  >
                    {warning}
                  </li>

                )
              )}

            </ul>

          </div>

        )}

      </Section>


      {/* ================================================== */}
      {/* BREAKDOWNS */}
      {/* ================================================== */}

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">

        <BreakdownTable
          title="Borrowers by Gender"
          rows={genderBreakdown}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />

        <BreakdownTable
          title="Loans by Loan Type"
          rows={loanTypeBreakdown}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />

        <BreakdownTable
          title="Loans by Branch"
          rows={branchBreakdown}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />

      </div>

    </div>
  );
}


// ============================================================
// CREDIT BUREAU SECTION
// ============================================================

function CreditBureauSection({
  from,
  setFrom,
  to,
  setTo,
  branchId,
  setBranchId,
  records,
  loading,
  downloadingFormat,
  onRefresh,
  onDownload,
  formatMoney,
  formatNumber,
}: {
  from: string;
  setFrom: React.Dispatch<
    React.SetStateAction<string>
  >;

  to: string;
  setTo: React.Dispatch<
    React.SetStateAction<string>
  >;

  branchId: string;
  setBranchId: React.Dispatch<
    React.SetStateAction<string>
  >;

  records: CreditRecord[];

  loading: boolean;

  downloadingFormat: DownloadingFormat;

  onRefresh: () => void;

  onDownload: (
    format: ExportFormat
  ) => void;

  formatMoney: (
    value?: number
  ) => string;

  formatNumber: (
    value?: number
  ) => string;
}) {

  return (

    <div className="space-y-6">


      {/* ================================================== */}
      {/* HEADER */}
      {/* ================================================== */}

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">

        <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">

          <div>

            <div className="mb-2 inline-flex rounded-full bg-purple-50 px-3 py-1 text-xs font-semibold text-purple-700">
              Credit Bureau
            </div>

            <h2 className="text-2xl font-bold text-slate-900">
              Credit Bureau Preview
            </h2>

            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
              Review borrower credit records before exporting
              them for regulatory or bureau submission.
            </p>

          </div>


          <div className="flex flex-wrap gap-2">

            <ExportButton
              label="PDF"
              format="pdf"
              loading={
                downloadingFormat === 'pdf'
              }
              disabled={
                downloadingFormat !== null
              }
              onClick={onDownload}
            />

            <ExportButton
              label="Excel"
              format="xlsx"
              loading={
                downloadingFormat === 'xlsx'
              }
              disabled={
                downloadingFormat !== null
              }
              onClick={onDownload}
            />

            <ExportButton
              label="CSV"
              format="csv"
              loading={
                downloadingFormat === 'csv'
              }
              disabled={
                downloadingFormat !== null
              }
              onClick={onDownload}
            />

          </div>

        </div>

      </div>


      {/* ================================================== */}
      {/* FILTERS */}
      {/* ================================================== */}

      <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">

        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">

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
              className="input"
            />

          </Field>


          <Field label="From">

            <input
              type="date"
              value={from}
              onChange={event =>
                setFrom(
                  event.target.value
                )
              }
              className="input"
            />

          </Field>


          <Field label="To">

            <input
              type="date"
              value={to}
              onChange={event =>
                setTo(
                  event.target.value
                )
              }
              className="input"
            />

          </Field>

        </div>


        <div className="mt-5 flex justify-end">

          <button
            type="button"
            onClick={onRefresh}
            disabled={loading}
            className="rounded-xl bg-slate-900 px-5 py-2.5 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-50"
          >
            {loading
              ? 'Loading...'
              : 'Refresh Preview'}
          </button>

        </div>

      </div>


      {/* ================================================== */}
      {/* SUMMARY */}
      {/* ================================================== */}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">

        <KpiCard
          label="Records"
          value={formatNumber(records.length)}
          icon="◉"
        />

        <KpiCard
          label="Total Loan Amount"
          value={formatMoney(
            records.reduce(
              (sum, row) =>
                sum +
                Number(row.loanAmount ?? 0),
              0
            )
          )}
          icon="₣"
        />

        <KpiCard
          label="Outstanding Balance"
          value={formatMoney(
            records.reduce(
              (sum, row) =>
                sum +
                Number(row.outstandingBalance ?? 0),
              0
            )
          )}
          icon="◈"
        />

      </div>


      {/* ================================================== */}
      {/* TABLE */}
      {/* ================================================== */}

      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">

        <div className="border-b border-slate-200 p-5">

          <h3 className="font-bold text-slate-900">
            Credit Bureau Records
          </h3>

          <p className="mt-1 text-sm text-slate-500">
            Staff preview of records returned by the regulatory
            Credit Bureau endpoint.
          </p>

        </div>


        {loading ? (

          <div className="p-10 text-center text-sm text-slate-500">
            Loading Credit Bureau records...
          </div>

        ) : records.length === 0 ? (

          <div className="p-10 text-center">

            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-xl">
              ◉
            </div>

            <p className="mt-3 font-semibold text-slate-900">
              No records found
            </p>

            <p className="mt-1 text-sm text-slate-500">
              Try changing the branch or date filters.
            </p>

          </div>

        ) : (

          <div className="overflow-x-auto">

            <table className="min-w-[1200px] w-full text-sm">

              <thead className="bg-slate-50">

                <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wider text-slate-500">

                  <th className="px-5 py-3">
                    Borrower
                  </th>

                  <th className="px-5 py-3">
                    National ID
                  </th>

                  <th className="px-5 py-3">
                    Loan
                  </th>

                  <th className="px-5 py-3 text-right">
                    Amount
                  </th>

                  <th className="px-5 py-3 text-right">
                    Outstanding
                  </th>

                  <th className="px-5 py-3 text-right">
                    DPD
                  </th>

                  <th className="px-5 py-3 text-right">
                    Credit Score
                  </th>

                  <th className="px-5 py-3">
                    Status
                  </th>

                </tr>

              </thead>


              <tbody className="divide-y divide-slate-100">

                {records.map(
                  (
                    record,
                    index
                  ) => (

                    <tr
                      key={`${record.borrowerId ?? record.loanNumber ?? 'record'}-${index}`}
                      className="hover:bg-slate-50"
                    >

                      <td className="px-5 py-4">

                        <p className="font-semibold text-slate-900">
                          {record.fullName ||
                            'Unknown borrower'}
                        </p>

                        <p className="mt-1 text-xs text-slate-500">
                          {record.phone ||
                            'No phone'}
                        </p>

                      </td>


                      <td className="px-5 py-4 text-slate-600">
                        {record.nationalId ||
                          '—'}
                      </td>


                      <td className="px-5 py-4">

                        <p className="font-medium text-slate-900">
                          {record.loanNumber ||
                            '—'}
                        </p>

                        <p className="mt-1 text-xs text-slate-500">
                          {record.loanType ||
                            '—'}
                        </p>

                      </td>


                      <td className="px-5 py-4 text-right font-medium text-slate-900">
                        {formatMoney(
                          record.loanAmount
                        )}
                      </td>


                      <td className="px-5 py-4 text-right font-medium text-slate-900">
                        {formatMoney(
                          record.outstandingBalance
                        )}
                      </td>


                      <td className="px-5 py-4 text-right">

                        <DpdBadge
                          days={
                            record.daysPastDue
                          }
                        />

                      </td>


                      <td className="px-5 py-4 text-right font-semibold text-slate-900">
                        {record.creditScore ??
                          '—'}
                      </td>


                      <td className="px-5 py-4">

                        <Badge>
                          {record.loanStatus ||
                            'Unknown'}
                        </Badge>

                      </td>

                    </tr>

                  )
                )}

              </tbody>

            </table>

          </div>

        )}

      </div>

    </div>
  );
}


// ============================================================
// API CLIENTS SECTION
// ============================================================

function ApiClientsSection({
  clients,
  loading,
  showCreate,
  setShowCreate,
  name,
  setName,
  clientType,
  setClientType,
  email,
  setEmail,
  description,
  setDescription,
  expiresAt,
  setExpiresAt,
  revealedApiKey,
  setRevealedApiKey,
  onCreate,
  onRefresh,
  onRevoke,
}: {
  clients: ApiClient[];

  loading: boolean;

  showCreate: boolean;

  setShowCreate: React.Dispatch<
    React.SetStateAction<boolean>
  >;

  name: string;
  setName: React.Dispatch<
    React.SetStateAction<string>
  >;

  clientType: 'BNR' | 'CREDIT_BUREAU';

  setClientType: React.Dispatch<
    React.SetStateAction<'BNR' | 'CREDIT_BUREAU'>
  >;

  email: string;
  setEmail: React.Dispatch<
    React.SetStateAction<string>
  >;

  description: string;
  setDescription: React.Dispatch<
    React.SetStateAction<string>
  >;

  expiresAt: string;
  setExpiresAt: React.Dispatch<
    React.SetStateAction<string>
  >;

  revealedApiKey: number | null;

  setRevealedApiKey: React.Dispatch<
    React.SetStateAction<number | null>
  >;

  onCreate: () => void;

  onRefresh: () => void;

  onRevoke: (
    client: ApiClient
  ) => void;
}) {

  return (

    <div className="space-y-6">


      {/* ================================================== */}
      {/* HEADER */}
      {/* ================================================== */}

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">

        <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">

          <div>

            <div className="mb-2 inline-flex rounded-full bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-700">
              Security & Integration
            </div>

            <h2 className="text-2xl font-bold text-slate-900">
              Regulatory API Clients
            </h2>

            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
              Manage API credentials used by BNR and Credit Bureau
              integrations. Revoke credentials immediately when
              they should no longer be trusted.
            </p>

          </div>


          <div className="flex gap-2">

            <button
              type="button"
              onClick={onRefresh}
              className="rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
            >
              Refresh
            </button>

            <button
              type="button"
              onClick={() =>
                setShowCreate(
                  current => !current
                )
              }
              className="rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-blue-700"
            >
              {showCreate
                ? 'Close'
                : '+ Create API Client'}
            </button>

          </div>

        </div>

      </div>


      {/* ================================================== */}
      {/* CREATE FORM */}
      {/* ================================================== */}

      {showCreate && (

        <div className="rounded-2xl border border-blue-200 bg-blue-50/50 p-6 shadow-sm">

          <div className="mb-5">

            <h3 className="text-lg font-bold text-slate-900">
              Create Regulatory API Client
            </h3>

            <p className="mt-1 text-sm text-slate-500">
              Create a credential for an authorized BNR or Credit
              Bureau integration.
            </p>

          </div>


          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">

            <Field label="Client Name">

              <input
                value={name}
                onChange={event =>
                  setName(
                    event.target.value
                  )
                }
                placeholder="e.g. BNR Production Integration"
                className="input"
              />

            </Field>


            <Field label="Client Type">

              <select
                value={clientType}
                onChange={event =>
                  setClientType(
                    event.target.value as
                    'BNR' |
                    'CREDIT_BUREAU'
                  )
                }
                className="input"
              >

                <option value="BNR">
                  BNR
                </option>

                <option value="CREDIT_BUREAU">
                  Credit Bureau
                </option>

              </select>

            </Field>


            <Field label="Contact Email">

              <input
                type="email"
                value={email}
                onChange={event =>
                  setEmail(
                    event.target.value
                  )
                }
                placeholder="integration@example.com"
                className="input"
              />

            </Field>


            <Field label="Expiration">

              <input
                type="date"
                value={expiresAt}
                onChange={event =>
                  setExpiresAt(
                    event.target.value
                  )
                }
                className="input"
              />

            </Field>


            <div className="md:col-span-2">

              <Field label="Description">

                <textarea
                  value={description}
                  onChange={event =>
                    setDescription(
                      event.target.value
                    )
                  }
                  rows={3}
                  placeholder="Describe how this API client will be used..."
                  className="input min-h-[100px]"
                />

              </Field>

            </div>

          </div>


          <div className="mt-5 flex justify-end gap-2">

            <button
              type="button"
              onClick={() =>
                setShowCreate(false)
              }
              className="rounded-xl border border-slate-300 bg-white px-5 py-2.5 text-sm font-semibold text-slate-700"
            >
              Cancel
            </button>

            <button
              type="button"
              onClick={onCreate}
              className="rounded-xl bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-blue-700"
            >
              Create Client
            </button>

          </div>

        </div>

      )}


      {/* ================================================== */}
      {/* SECURITY NOTICE */}
      {/* ================================================== */}

      <div className="rounded-2xl border border-amber-200 bg-amber-50 p-5">

        <div className="flex gap-3">

          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-amber-100 text-amber-700">
            ⚿
          </div>

          <div>

            <h3 className="font-bold text-amber-900">
              API credential security
            </h3>

            <p className="mt-1 text-sm leading-6 text-amber-800">
              Treat regulatory API keys as secrets. Do not place
              credentials in frontend source code, public repositories,
              screenshots or client-side environment variables.
            </p>

          </div>

        </div>

      </div>


      {/* ================================================== */}
      {/* CLIENT LIST */}
      {/* ================================================== */}

      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">

        <div className="border-b border-slate-200 p-5">

          <h3 className="font-bold text-slate-900">
            API Clients
          </h3>

          <p className="mt-1 text-sm text-slate-500">
            Active and revoked regulatory integration credentials.
          </p>

        </div>


        {loading ? (

          <div className="p-10 text-center text-sm text-slate-500">
            Loading API clients...
          </div>

        ) : clients.length === 0 ? (

          <div className="p-10 text-center">

            <p className="font-semibold text-slate-900">
              No API clients configured
            </p>

            <p className="mt-1 text-sm text-slate-500">
              Create an API client to establish a regulatory integration.
            </p>

          </div>

        ) : (

          <div className="overflow-x-auto">

            <table className="min-w-[1100px] w-full text-sm">

              <thead className="bg-slate-50">

                <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wider text-slate-500">

                  <th className="px-5 py-3">
                    Client
                  </th>

                  <th className="px-5 py-3">
                    Type
                  </th>

                  <th className="px-5 py-3">
                    API Key
                  </th>

                  <th className="px-5 py-3">
                    Expiration
                  </th>

                  <th className="px-5 py-3">
                    Status
                  </th>

                  <th className="px-5 py-3 text-right">
                    Action
                  </th>

                </tr>

              </thead>


              <tbody className="divide-y divide-slate-100">

                {clients.map(
                  client => {

                    const key =
                      client.apiKey ||
                      client.key ||
                      '';

                    const isActive =
                      client.active === true ||
                      client.status?.toUpperCase() ===
                      'ACTIVE';

                    return (

                      <tr
                        key={client.id}
                        className="hover:bg-slate-50"
                      >

                        <td className="px-5 py-4">

                          <p className="font-semibold text-slate-900">
                            {client.name}
                          </p>

                          {client.description && (

                            <p className="mt-1 max-w-sm text-xs text-slate-500">
                              {client.description}
                            </p>

                          )}

                          {client.contactEmail && (

                            <p className="mt-1 text-xs text-slate-400">
                              {client.contactEmail}
                            </p>

                          )}

                        </td>


                        <td className="px-5 py-4">

                          <Badge>
                            {client.clientType}
                          </Badge>

                        </td>


                        <td className="px-5 py-4">

                          {key ? (

                            <div className="flex items-center gap-2">

                              <code className="max-w-[260px] truncate rounded-lg bg-slate-100 px-3 py-2 text-xs text-slate-700">
                                {revealedApiKey === client.id
                                  ? key
                                  : maskApiKey(key)}
                              </code>

                              <button
                                type="button"
                                onClick={() =>
                                  setRevealedApiKey(
                                    current =>
                                      current === client.id
                                        ? null
                                        : client.id
                                  )
                                }
                                className="rounded-lg border border-slate-200 px-2.5 py-2 text-xs font-medium text-slate-600 hover:bg-slate-50"
                              >
                                {revealedApiKey === client.id
                                  ? 'Hide'
                                  : 'Show'}
                              </button>

                            </div>

                          ) : (

                            <span className="text-xs text-slate-400">
                              Key not returned
                            </span>

                          )}

                        </td>


                        <td className="px-5 py-4 text-slate-600">
                          {client.expiresAt ||
                            'Never'}
                        </td>


                        <td className="px-5 py-4">

                          <span
                            className={
                              isActive
                                ? 'inline-flex rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700'
                                : 'inline-flex rounded-full bg-red-50 px-2.5 py-1 text-xs font-semibold text-red-700'
                            }
                          >
                            {isActive
                              ? 'Active'
                              : client.status ||
                                'Revoked'}
                          </span>

                        </td>


                        <td className="px-5 py-4 text-right">

                          {isActive && (

                            <button
                              type="button"
                              onClick={() =>
                                onRevoke(
                                  client
                                )
                              }
                              className="rounded-lg border border-red-200 px-3 py-2 text-xs font-semibold text-red-700 hover:bg-red-50"
                            >
                              Revoke
                            </button>

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

    </div>
  );
}


// ============================================================
// FINANCIAL STATEMENT
// ============================================================

function FinancialStatementSection({
  report,
  formatMoney,
}: {
  report: BnrFinancialStatementReport | null;

  formatMoney: (
    value?: number
  ) => string;
}) {

  if (!report) {

    return (

      <Section title="BNR Financial Statement">

        <p className="text-sm text-slate-500">
          Financial statement data is not available for this period.
        </p>

      </Section>
    );
  }


  return (

    <div className="space-y-6">


      {/* ================================================== */}
      {/* BALANCE SHEET */}
      {/* ================================================== */}

      <Section title="Statement of Financial Position">

        <FinancialRows
          title="Assets"
          rows={report.assets}
          formatMoney={formatMoney}
        />

        <FinancialRows
          title="Liabilities"
          rows={report.liabilities}
          formatMoney={formatMoney}
        />

        <FinancialRows
          title="Equity"
          rows={report.equity}
          formatMoney={formatMoney}
        />


        <div className="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">

          <MetricCard
            label="Total Assets"
            value={formatMoney(report.totalAssets)}
          />

          <MetricCard
            label="Total Liabilities"
            value={formatMoney(report.totalLiabilities)}
          />

          <MetricCard
            label="Total Equity"
            value={formatMoney(report.totalEquity)}
          />

          <MetricCard
            label="Current Period Net Income"
            value={formatMoney(report.currentPeriodNetIncome)}
          />

        </div>


        <div className="mt-5">

          <BalanceIndicator
            label="Balance Sheet"
            balanced={
              report.balanceSheetBalanced
            }
          />

        </div>

      </Section>


      {/* ================================================== */}
      {/* INCOME */}
      {/* ================================================== */}

      <Section title="Income Statement">

        <FinancialRows
          title="Income"
          rows={report.income}
          formatMoney={formatMoney}
        />

        <FinancialRows
          title="Expenses"
          rows={report.expenses}
          formatMoney={formatMoney}
        />


        <div className="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-3">

          <MetricCard
            label="Total Income"
            value={formatMoney(report.totalIncome)}
          />

          <MetricCard
            label="Total Expenses"
            value={formatMoney(report.totalExpenses)}
          />

          <MetricCard
            label="Net Income"
            value={formatMoney(report.netIncome)}
          />

        </div>

      </Section>


      {/* ================================================== */}
      {/* CASH FLOW */}
      {/* ================================================== */}

      <Section title="Cash Flow">

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-5">

          <MetricCard
            label="Cash Used For Lending"
            value={formatMoney(report.cashUsedForLending)}
          />

          <MetricCard
            label="Cash From Collections"
            value={formatMoney(report.cashFromCollections)}
          />

          <MetricCard
            label="Cash From Fees"
            value={formatMoney(report.cashFromFees)}
          />

          <MetricCard
            label="Other Cash Movement"
            value={formatMoney(report.otherCashMovement)}
          />

          <MetricCard
            label="Net Change In Cash"
            value={formatMoney(report.netChangeInCash)}
          />

        </div>

      </Section>


      {/* ================================================== */}
      {/* TRIAL BALANCE */}
      {/* ================================================== */}

      <Section title="Trial Balance">

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">

          <MetricCard
            label="Total Debit"
            value={formatMoney(report.trialBalanceDebit)}
          />

          <MetricCard
            label="Total Credit"
            value={formatMoney(report.trialBalanceCredit)}
          />

          <BalanceIndicator
            label="Trial Balance"
            balanced={
              report.trialBalanceBalanced
            }
          />

        </div>

      </Section>

    </div>
  );
}


// ============================================================
// FINANCIAL ROWS
// ============================================================

function FinancialRows({
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

  if (!rows || rows.length === 0) {

    return (

      <div className="mt-5 rounded-xl border border-slate-200 bg-slate-50 p-4">

        <h4 className="font-semibold text-slate-800">
          {title}
        </h4>

        <p className="mt-1 text-sm text-slate-500">
          No accounts reported.
        </p>

      </div>
    );
  }


  return (

    <div className="mt-5 overflow-hidden rounded-xl border border-slate-200">

      <div className="border-b border-slate-200 bg-slate-50 px-4 py-3">

        <h4 className="font-semibold text-slate-800">
          {title}
        </h4>

      </div>


      <div className="overflow-x-auto">

        <table className="min-w-full text-sm">

          <thead>

            <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wider text-slate-500">

              <th className="px-4 py-3">
                Code
              </th>

              <th className="px-4 py-3">
                Account
              </th>

              <th className="px-4 py-3 text-right">
                Balance
              </th>

            </tr>

          </thead>


          <tbody className="divide-y divide-slate-100">

            {rows.map(
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
                    key={`${row.code || row.name || 'row'}-${index}`}
                    className="hover:bg-slate-50"
                  >

                    <td className="px-4 py-3 text-slate-500">
                      {row.code ||
                        '—'}
                    </td>

                    <td className="px-4 py-3 font-medium text-slate-800">
                      {row.name ||
                        'Unnamed Account'}
                    </td>

                    <td className="px-4 py-3 text-right font-semibold text-slate-900">
                      {formatMoney(value)}
                    </td>

                  </tr>

                );
              }
            )}

          </tbody>

        </table>

      </div>

    </div>
  );
}


// ============================================================
// SECTION
// ============================================================

function Section({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {

  return (

    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm md:p-6">

      <div className="mb-5">

        <h2 className="text-lg font-bold text-slate-900">
          {title}
        </h2>

      </div>

      {children}

    </section>
  );
}


// ============================================================
// KPI CARD
// ============================================================

function KpiCard({
  label,
  value,
  icon,
}: {
  label: string;
  value: string;
  icon?: string;
}) {

  return (

    <div className="group rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md">

      <div className="flex items-start justify-between gap-3">

        <p className="text-sm font-medium text-slate-500">
          {label}
        </p>

        {icon && (

          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-blue-50 font-bold text-blue-600">
            {icon}
          </div>

        )}

      </div>

      <p className="mt-3 break-words text-2xl font-bold tracking-tight text-slate-900">
        {value}
      </p>

    </div>
  );
}


// ============================================================
// METRIC CARD
// ============================================================

function MetricCard({
  label,
  value,
  secondary,
}: {
  label: string;
  value: string;
  secondary?: string;
}) {

  return (

    <div className="rounded-xl border border-slate-100 bg-slate-50 p-4">

      <p className="text-sm text-slate-500">
        {label}
      </p>

      <p className="mt-1 break-words text-xl font-bold text-slate-900">
        {value}
      </p>

      {secondary && (

        <p className="mt-1 text-xs text-slate-500">
          {secondary}
        </p>

      )}

    </div>
  );
}


// ============================================================
// STATUS ITEM
// ============================================================

function StatusItem({
  label,
  value,
}: {
  label: string;
  value?: number;
}) {

  return (

    <div className="rounded-xl border border-slate-100 bg-slate-50 p-4">

      <p className="text-sm text-slate-500">
        {label}
      </p>

      <p className="mt-1 text-xl font-bold text-slate-900">
        {new Intl.NumberFormat(
          'en-US'
        ).format(
          Number(value ?? 0)
        )}
      </p>

    </div>
  );
}


// ============================================================
// BALANCE INDICATOR
// ============================================================

function BalanceIndicator({
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
          ? 'rounded-xl border border-emerald-200 bg-emerald-50 p-4'
          : 'rounded-xl border border-red-200 bg-red-50 p-4'
      }
    >

      <p className="text-sm text-slate-500">
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
          ? '✓ Balanced'
          : '⚠ Not Balanced'}
      </p>

    </div>
  );
}


// ============================================================
// BREAKDOWN TABLE
// ============================================================

function BreakdownTable({
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

    <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">

      <div className="border-b border-slate-200 p-5">

        <h3 className="font-bold text-slate-900">
          {title}
        </h3>

      </div>


      {rows.length === 0 ? (

        <div className="p-6 text-center text-sm text-slate-500">
          No data available.
        </div>

      ) : (

        <div className="overflow-x-auto">

          <table className="min-w-full text-sm">

            <thead className="bg-slate-50">

              <tr className="text-xs uppercase tracking-wider text-slate-500">

                <th className="px-4 py-3 text-left">
                  Category
                </th>

                <th className="px-4 py-3 text-right">
                  Loans
                </th>

                <th className="px-4 py-3 text-right">
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
                    className="hover:bg-slate-50"
                  >

                    <td className="px-4 py-3 font-medium text-slate-900">
                      {row.label}
                    </td>

                    <td className="px-4 py-3 text-right text-slate-600">
                      {formatNumber(row.count)}
                    </td>

                    <td className="px-4 py-3 text-right font-semibold text-slate-900">
                      {formatMoney(row.amount)}
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
// EXPORT BUTTON
// ============================================================

function ExportButton({
  label,
  format,
  loading,
  disabled,
  onClick,
}: {
  label: string;
  format: ExportFormat;
  loading: boolean;
  disabled: boolean;
  onClick: (
    format: ExportFormat
  ) => void;
}) {

  return (

    <button
      type="button"
      disabled={disabled}
      onClick={() =>
        onClick(format)
      }
      className="rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-xs font-bold text-slate-700 shadow-sm transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
    >
      {loading
        ? 'Downloading...'
        : `Download ${label}`}
    </button>
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

    <div>

      <label className="mb-1.5 block text-sm font-semibold text-slate-700">
        {label}
      </label>

      {children}

    </div>
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

    <span className="inline-flex rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-700">
      {children}
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

  if (value <= 0) {

    return (
      <span className="inline-flex rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700">
        Current
      </span>
    );
  }

  if (value <= 30) {

    return (
      <span className="inline-flex rounded-full bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-700">
        {value} days
      </span>
    );
  }

  if (value <= 90) {

    return (
      <span className="inline-flex rounded-full bg-orange-50 px-2.5 py-1 text-xs font-semibold text-orange-700">
        {value} days
      </span>
    );
  }

  return (
    <span className="inline-flex rounded-full bg-red-50 px-2.5 py-1 text-xs font-semibold text-red-700">
      {value} days
    </span>
  );
}


// ============================================================
// INPUT CLASS
// ============================================================

// Tailwind cannot process a runtime class constant unless the
// class itself exists in the source, so this is intentionally
// kept as a literal constant.


// ============================================================
// NORMALIZE CREDIT RECORDS
// ============================================================

function normalizeCreditRecords(
  response: CreditBureauPreviewResponse
): CreditRecord[] {

  if (Array.isArray(response)) {
    return response;
  }

  if (
    response &&
    typeof response === 'object'
  ) {

    if (
      Array.isArray(response.records)
    ) {
      return response.records;
    }

    if (
      Array.isArray(response.data)
    ) {
      return response.data;
    }

    if (
      Array.isArray(response.content)
    ) {
      return response.content;
    }
  }

  return [];
}


// ============================================================
// NORMALIZE API CLIENTS
// ============================================================

function normalizeApiClients(
  response: unknown
): ApiClient[] {

  if (Array.isArray(response)) {

    return response
      .map(normalizeApiClient)
      .filter(
        (
          client
        ): client is ApiClient =>
          client !== null
      );
  }

  if (
    response &&
    typeof response === 'object'
  ) {

    const value =
      response as {
        data?: unknown;
        content?: unknown;
        items?: unknown;
        results?: unknown;
      };

    if (
      Array.isArray(value.data)
    ) {
      return normalizeApiClients(
        value.data
      );
    }

    if (
      Array.isArray(value.content)
    ) {
      return normalizeApiClients(
        value.content
      );
    }

    if (
      Array.isArray(value.items)
    ) {
      return normalizeApiClients(
        value.items
      );
    }

    if (
      Array.isArray(value.results)
    ) {
      return normalizeApiClients(
        value.results
      );
    }

    const single =
      normalizeApiClient(
        response
      );

    return single
      ? [single]
      : [];
  }

  return [];
}


// ============================================================
// NORMALIZE SINGLE API CLIENT
// ============================================================

function normalizeApiClient(
  response: unknown
): ApiClient | null {

  if (
    !response ||
    typeof response !== 'object'
  ) {

    return null;
  }

  const value =
    response as {
      id?: number;
      name?: string;
      clientType?: 'BNR' | 'CREDIT_BUREAU';
      contactEmail?: string;
      description?: string;
      expiresAt?: string | null;
      status?: string;
      active?: boolean;
      apiKey?: string;
      key?: string;
      createdAt?: string;
      revokedAt?: string | null;
    };

  if (
    value.id === undefined ||
    !value.name
  ) {

    return null;
  }

  return {
    id:
      Number(value.id),

    name:
      value.name,

    clientType:
      value.clientType ||
      'BNR',

    contactEmail:
      value.contactEmail,

    description:
      value.description,

    expiresAt:
      value.expiresAt,

    status:
      value.status,

    active:
      value.active,

    apiKey:
      value.apiKey,

    key:
      value.key,

    createdAt:
      value.createdAt,

    revokedAt:
      value.revokedAt,
  };
}


// ============================================================
// MASK API KEY
// ============================================================

function maskApiKey(
  key: string
): string {

  if (key.length <= 8) {

    return '••••••••';
  }

  return `${key.slice(0, 4)}••••••••${key.slice(-4)}`;
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
    Number(summary.par31To60Amount ?? 0) +
    Number(summary.par61To90Amount ?? 0) +
    Number(summary.par91To180Amount ?? 0) +
    Number(summary.par181To365Amount ?? 0) +
    Number(summary.parOver365Amount ?? 0)
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
    Number(summary.par61To90Amount ?? 0) +
    Number(summary.par91To180Amount ?? 0) +
    Number(summary.par181To365Amount ?? 0) +
    Number(summary.parOver365Amount ?? 0)
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
    Number(summary.par91To180Amount ?? 0) +
    Number(summary.par181To365Amount ?? 0) +
    Number(summary.parOver365Amount ?? 0)
  );
}