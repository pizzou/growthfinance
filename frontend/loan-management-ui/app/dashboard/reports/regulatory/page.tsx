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
// TYPES
// ============================================================

type DownloadingFormat = ExportFormat | null;

type IconName =
  | 'briefcase'
  | 'wallet'
  | 'chart'
  | 'alert'
  | 'users'
  | 'check'
  | 'calendar'
  | 'download'
  | 'refresh'
  | 'building'
  | 'shield'
  | 'database'
  | 'file'
  | 'arrow'
  | 'warning';


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
  // UI STATE
  // ==========================================================

  const [loading, setLoading] =
    useState<boolean>(true);

  const [downloadingFormat, setDownloadingFormat] =
    useState<DownloadingFormat>(null);

  const [error, setError] =
    useState<string | null>(null);

  // ==========================================================
  // REPORT PARAMETERS
  // ==========================================================

  const reportParams =
    useMemo<BnrReportParams>(() => {
      const params: BnrReportParams = {
        period,
      };

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
    ]);

  // ==========================================================
  // VALIDATE FILTERS
  // ==========================================================

  const validateFilters =
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
  // LOAD REPORT
  // ==========================================================

  const loadReport =
    useCallback(async (): Promise<void> => {
      const validationError =
        validateFilters();

      if (validationError) {
        setError(validationError);
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

        setSummary(summaryResult);

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
  // INITIAL LOAD
  // ==========================================================

  useEffect(() => {
    void loadReport();
  }, [
    loadReport,
  ]);

  // ==========================================================
  // DOWNLOAD
  // ==========================================================

  const downloadReport =
    useCallback(
      async (
        format: ExportFormat
      ): Promise<void> => {
        const validationError =
          validateFilters();

        if (validationError) {
          setError(validationError);
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
            `Failed to download BNR ${format} report:`,
            err
          );

          setError(
            regulatoryApi.getErrorMessage(
              err,
              `Failed to download BNR ${format.toUpperCase()} report.`
            )
          );
        } finally {
          setDownloadingFormat(null);
        }
      },
      [
        reportParams,
        validateFilters,
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
  // PERIOD LABEL
  // ==========================================================

  const periodLabel =
    useMemo(() => {
      switch (period) {
        case 'DAILY':
          return 'Daily';

        case 'WEEKLY':
          return 'Weekly';

        case 'MONTHLY':
          return 'Monthly';

        case 'QUARTERLY':
          return 'Quarterly';

        case 'YEARLY':
          return 'Yearly';

        case 'CUSTOM':
          return 'Custom';

        default:
          return period;
      }
    }, [
      period,
    ]);

  // ==========================================================
  // LOADING
  // ==========================================================

  if (loading) {
    return (
      <BnrLoadingState />
    );
  }

  // ==========================================================
  // RENDER
  // ==========================================================

  return (
    <div className="min-h-screen bg-[#f4f7fb] text-slate-900">

      {/* ======================================================
          TOP BRAND BAR
      ====================================================== */}

      <div className="border-b border-slate-800 bg-slate-950 text-white">
        <div className="mx-auto flex max-w-[1600px] items-center justify-between px-4 py-3 sm:px-6 lg:px-8">

          <div className="flex items-center gap-3">

            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-600 shadow-lg shadow-blue-950/30">
              <Icon
                name="building"
                className="h-5 w-5"
              />
            </div>

            <div>
              <p className="text-sm font-semibold tracking-wide">
                Regulatory Reporting
              </p>

              <p className="text-[11px] text-slate-400">
                BNR • Banking & Financial Reporting
              </p>
            </div>

          </div>

          <div className="hidden items-center gap-2 text-xs text-slate-400 sm:flex">
            <span className="h-2 w-2 rounded-full bg-emerald-400" />
            Reporting system operational
          </div>

        </div>
      </div>


      {/* ======================================================
          MAIN
      ====================================================== */}

      <main className="mx-auto max-w-[1600px] space-y-6 px-4 py-6 sm:px-6 lg:px-8">

        {/* ====================================================
            HEADER
        ==================================================== */}

        <section className="overflow-hidden rounded-2xl bg-gradient-to-br from-slate-950 via-slate-900 to-blue-950 shadow-xl">

          <div className="relative px-5 py-7 sm:px-8 sm:py-9">

            <div className="absolute -right-20 -top-24 h-72 w-72 rounded-full bg-blue-500/10 blur-3xl" />

            <div className="absolute -bottom-32 left-1/3 h-72 w-72 rounded-full bg-indigo-500/10 blur-3xl" />

            <div className="relative flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">

              <div className="max-w-3xl">

                <div className="mb-4 flex items-center gap-2">

                  <span className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/10 px-3 py-1 text-xs font-medium text-blue-100 backdrop-blur">
                    <span className="h-1.5 w-1.5 rounded-full bg-blue-400" />
                    Regulatory Report
                  </span>

                  <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs text-slate-300">
                    {periodLabel}
                  </span>

                </div>

                <h1 className="text-3xl font-bold tracking-tight text-white sm:text-4xl">
                  BNR Regulatory Report
                </h1>

                <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-300 sm:text-base">
                  Comprehensive regulatory reporting covering
                  portfolio quality, loan performance,
                  borrower statistics, financial position,
                  cash flow and BNR reporting information.
                </p>

              </div>

              <div className="shrink-0 rounded-xl border border-white/10 bg-white/5 p-4 backdrop-blur">

                <p className="text-[11px] font-medium uppercase tracking-wider text-slate-400">
                  Reporting period
                </p>

                <p className="mt-1 text-lg font-semibold text-white">
                  {summary?.periodStart || '—'}
                  <span className="mx-2 text-slate-500">
                    →
                  </span>
                  {summary?.periodEnd || '—'}
                </p>

                <p className="mt-1 text-xs text-slate-400">
                  {summary?.currency || 'RWF'} reporting currency
                </p>

              </div>

            </div>

          </div>

        </section>


        {/* ====================================================
            ERROR
        ==================================================== */}

        {error && (
          <section className="rounded-xl border border-red-200 bg-red-50 p-4 shadow-sm">

            <div className="flex items-start gap-3">

              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-red-100 text-red-600">
                <Icon
                  name="warning"
                  className="h-5 w-5"
                />
              </div>

              <div className="min-w-0 flex-1">

                <p className="font-semibold text-red-900">
                  Report error
                </p>

                <p className="mt-1 text-sm text-red-700">
                  {error}
                </p>

              </div>

              <button
                type="button"
                onClick={() => setError(null)}
                className="rounded-lg px-3 py-1.5 text-xs font-semibold text-red-700 transition hover:bg-red-100"
              >
                Dismiss
              </button>

            </div>

          </section>
        )}


        {/* ====================================================
            ORGANIZATION + EXPORT
        ==================================================== */}

        <section className="grid grid-cols-1 gap-4 xl:grid-cols-[1fr_auto]">

          {/* ORGANIZATION */}

          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">

            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">

              <div className="flex items-center gap-4">

                <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-blue-50 text-blue-700">
                  <Icon
                    name="building"
                    className="h-6 w-6"
                  />
                </div>

                <div>

                  <p className="text-xs font-medium uppercase tracking-wider text-slate-400">
                    Reporting institution
                  </p>

                  <h2 className="mt-0.5 text-lg font-bold text-slate-900">
                    {summary?.organizationName ||
                      'Organization'}
                  </h2>

                  <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-500">

                    <span>
                      BNR Code:{' '}
                      <strong className="text-slate-700">
                        {summary?.bnrInstitutionCode ||
                          'Not configured'}
                      </strong>
                    </span>

                    <span>
                      Registration:{' '}
                      <strong className="text-slate-700">
                        {summary?.registrationNumber ||
                          'Not configured'}
                      </strong>
                    </span>

                  </div>

                </div>

              </div>


              <div className="flex items-center gap-2">

                <div className="hidden rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500 sm:block">
                  Status:{' '}
                  <span className="font-semibold text-emerald-700">
                    {summary?.reportStatus || '—'}
                  </span>
                </div>

              </div>

            </div>

          </div>


          {/* EXPORT */}

          <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">

            <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-slate-400">
              Export report
            </p>

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
                onClick={() =>
                  void downloadReport('pdf')
                }
                className="border-red-200 bg-red-50 text-red-700 hover:bg-red-100"
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
                onClick={() =>
                  void downloadReport('xlsx')
                }
                className="border-emerald-200 bg-emerald-50 text-emerald-700 hover:bg-emerald-100"
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
                onClick={() =>
                  void downloadReport('csv')
                }
                className="border-blue-200 bg-blue-50 text-blue-700 hover:bg-blue-100"
              />

            </div>

          </div>

        </section>


        {/* ====================================================
            FILTER PANEL
        ==================================================== */}

        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">

          <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">

            <div>

              <div className="flex items-center gap-2">

                <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-slate-100 text-slate-700">
                  <Icon
                    name="calendar"
                    className="h-5 w-5"
                  />
                </div>

                <div>

                  <h2 className="font-semibold text-slate-900">
                    Reporting period
                  </h2>

                  <p className="text-xs text-slate-500">
                    Choose the period used to generate regulatory figures.
                  </p>

                </div>

              </div>

            </div>


            <div className="grid w-full grid-cols-1 gap-3 sm:grid-cols-3 lg:max-w-3xl">

              <FilterField label="Period">

                <select
                  id="bnr-period"
                  value={period}
                  onChange={(event) =>
                    setPeriod(
                      event.target.value as RegulatoryPeriod
                    )
                  }
                  className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm font-medium text-slate-800 outline-none transition focus:border-blue-500 focus:bg-white focus:ring-4 focus:ring-blue-100"
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

              </FilterField>


              <FilterField label="From">

                <input
                  id="bnr-from"
                  type="date"
                  value={from}
                  disabled={
                    period !== 'CUSTOM'
                  }
                  onChange={(event) =>
                    setFrom(
                      event.target.value
                    )
                  }
                  className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm text-slate-800 outline-none transition focus:border-blue-500 focus:bg-white focus:ring-4 focus:ring-blue-100 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400"
                />

              </FilterField>


              <FilterField label="To">

                <input
                  id="bnr-to"
                  type="date"
                  value={to}
                  disabled={
                    period !== 'CUSTOM'
                  }
                  onChange={(event) =>
                    setTo(
                      event.target.value
                    )
                  }
                  className="h-10 w-full rounded-lg border border-slate-200 bg-slate-50 px-3 text-sm text-slate-800 outline-none transition focus:border-blue-500 focus:bg-white focus:ring-4 focus:ring-blue-100 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400"
                />

              </FilterField>

            </div>


            <button
              type="button"
              onClick={() => void loadReport()}
              disabled={loading}
              className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-slate-950 px-5 text-sm font-semibold text-white shadow-sm transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <Icon
                name="refresh"
                className="h-4 w-4"
              />

              Refresh Report
            </button>

          </div>

        </section>


        {/* ====================================================
            KPI OVERVIEW
        ==================================================== */}

        <section>

          <SectionHeading
            eyebrow="Portfolio overview"
            title="Key performance indicators"
            description="High-level indicators for the selected reporting period."
          />

          <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">

            <KpiCard
              label="Total Loans"
              value={formatNumber(
                summary?.totalLoans
              )}
              icon="briefcase"
              tone="blue"
              helper="Loans in portfolio"
            />

            <KpiCard
              label="Active Loans"
              value={formatNumber(
                summary?.activeLoans
              )}
              icon="chart"
              tone="indigo"
              helper="Currently active facilities"
            />

            <KpiCard
              label="Principal Disbursed"
              value={formatMoney(
                summary?.totalPrincipalDisbursed
              )}
              icon="wallet"
              tone="emerald"
              helper="Total principal issued"
            />

            <KpiCard
              label="Outstanding Principal"
              value={formatMoney(
                summary?.outstandingPrincipal
              )}
              icon="database"
              tone="amber"
              helper="Current portfolio exposure"
            />

            <KpiCard
              label="Interest Collected"
              value={formatMoney(
                summary?.totalInterestCollected
              )}
              icon="chart"
              tone="emerald"
              helper="Interest received"
            />

            <KpiCard
              label="Total Collected"
              value={formatMoney(
                summary?.totalAmountCollected
              )}
              icon="check"
              tone="blue"
              helper="Principal + interest + fees"
            />

            <KpiCard
              label="Overdue Loans"
              value={formatNumber(
                summary?.overdueLoans
              )}
              icon="alert"
              tone="orange"
              helper="Loans currently overdue"
            />

            <KpiCard
              label="Defaulted Loans"
              value={formatNumber(
                summary?.defaultedLoans
              )}
              icon="warning"
              tone="red"
              helper="Loans classified as defaulted"
            />

          </div>

        </section>


        {/* ====================================================
            PORTFOLIO QUALITY
        ==================================================== */}

        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">

          <SectionHeading
            eyebrow="Risk monitoring"
            title="Portfolio quality"
            description="Portfolio-at-risk and non-performing loan indicators."
          />

          <div className="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">

            <RiskMetricCard
              label="PAR"
              value={formatPercent(
                summary?.parRatio
              )}
              amount={formatMoney(
                summary?.parAmount
              )}
              severity={getRiskSeverity(
                summary?.parRatio
              )}
            />

            <RiskMetricCard
              label="PAR > 30 Days"
              value={formatPercent(
                summary?.par30Ratio
              )}
              amount={formatMoney(
                getPar30Amount(summary)
              )}
              severity={getRiskSeverity(
                summary?.par30Ratio
              )}
            />

            <RiskMetricCard
              label="PAR > 60 Days"
              value={formatPercent(
                summary?.par60Ratio
              )}
              amount={formatMoney(
                getPar60Amount(summary)
              )}
              severity={getRiskSeverity(
                summary?.par60Ratio
              )}
            />

            <RiskMetricCard
              label="PAR > 90 Days"
              value={formatPercent(
                summary?.par90Ratio
              )}
              amount={formatMoney(
                getPar90Amount(summary)
              )}
              severity={getRiskSeverity(
                summary?.par90Ratio
              )}
            />

            <RiskMetricCard
              label="NPL Ratio"
              value={formatPercent(
                summary?.nplRatio
              )}
              amount={formatMoney(
                summary?.nplAmount
              )}
              severity={getRiskSeverity(
                summary?.nplRatio
              )}
            />

            <KpiCard
              label="NPL Loans"
              value={formatNumber(
                summary?.nplLoanCount
              )}
              icon="warning"
              tone="red"
              helper="Non-performing facilities"
            />

            <KpiCard
              label="Loans > 30 DPD"
              value={formatNumber(
                summary?.loansOver30Days
              )}
              icon="alert"
              tone="orange"
              helper="30+ days past due"
            />

            <KpiCard
              label="Loans > 90 DPD"
              value={formatNumber(
                summary?.loansOver90Days
              )}
              icon="alert"
              tone="red"
              helper="90+ days past due"
            />

          </div>

        </section>


        {/* ====================================================
            PAR AGING
        ==================================================== */}

        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">

          <SectionHeading
            eyebrow="Delinquency aging"
            title="Portfolio at risk aging"
            description="Outstanding exposure grouped by delinquency bucket."
          />

          <div className="mt-6 grid grid-cols-1 gap-5 lg:grid-cols-2 xl:grid-cols-3">

            <ParAgingCard
              label="1–30 Days"
              amount={summary?.par1To30Amount}
              total={summary?.outstandingPrincipal}
              tone="blue"
              formatMoney={formatMoney}
            />

            <ParAgingCard
              label="31–60 Days"
              amount={summary?.par31To60Amount}
              total={summary?.outstandingPrincipal}
              tone="amber"
              formatMoney={formatMoney}
            />

            <ParAgingCard
              label="61–90 Days"
              amount={summary?.par61To90Amount}
              total={summary?.outstandingPrincipal}
              tone="orange"
              formatMoney={formatMoney}
            />

            <ParAgingCard
              label="91–180 Days"
              amount={summary?.par91To180Amount}
              total={summary?.outstandingPrincipal}
              tone="red"
              formatMoney={formatMoney}
            />

            <ParAgingCard
              label="181–365 Days"
              amount={summary?.par181To365Amount}
              total={summary?.outstandingPrincipal}
              tone="rose"
              formatMoney={formatMoney}
            />

            <ParAgingCard
              label="Over 365 Days"
              amount={summary?.parOver365Amount}
              total={summary?.outstandingPrincipal}
              tone="slate"
              formatMoney={formatMoney}
            />

          </div>

        </section>


        {/* ====================================================
            STATUS + BORROWER OVERVIEW
        ==================================================== */}

        <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">

          {/* LOAN STATUS */}

          <DashboardPanel
            eyebrow="Portfolio composition"
            title="Loan status"
            icon="briefcase"
          >

            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">

              <StatusItem
                label="Active"
                value={summary?.activeLoans}
                tone="blue"
              />

              <StatusItem
                label="Closed"
                value={summary?.closedLoans}
                tone="slate"
              />

              <StatusItem
                label="Paid"
                value={summary?.paidLoans}
                tone="emerald"
              />

              <StatusItem
                label="Pending"
                value={summary?.pendingLoans}
                tone="amber"
              />

              <StatusItem
                label="Approved"
                value={summary?.approvedLoans}
                tone="indigo"
              />

              <StatusItem
                label="Rejected"
                value={summary?.rejectedLoans}
                tone="red"
              />

              <StatusItem
                label="Cancelled"
                value={summary?.cancelledLoans}
                tone="slate"
              />

              <StatusItem
                label="Defaulted"
                value={summary?.defaultedLoans}
                tone="red"
              />

              <StatusItem
                label="Written Off"
                value={summary?.writtenOffLoans}
                tone="orange"
              />

              <StatusItem
                label="Overdue"
                value={summary?.overdueLoans}
                tone="amber"
              />

            </div>

          </DashboardPanel>


          {/* BORROWERS */}

          <DashboardPanel
            eyebrow="Customer portfolio"
            title="Borrower statistics"
            icon="users"
          >

            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">

              <StatusItem
                label="Total"
                value={summary?.totalBorrowers}
                tone="blue"
              />

              <StatusItem
                label="Active"
                value={summary?.activeBorrowers}
                tone="emerald"
              />

              <StatusItem
                label="Male"
                value={summary?.maleBorrowers}
                tone="indigo"
              />

              <StatusItem
                label="Female"
                value={summary?.femaleBorrowers}
                tone="rose"
              />

              <StatusItem
                label="Youth"
                value={summary?.youthBorrowers}
                tone="blue"
              />

              <StatusItem
                label="Adult"
                value={summary?.adultBorrowers}
                tone="slate"
              />

              <StatusItem
                label="Senior"
                value={summary?.seniorBorrowers}
                tone="amber"
              />

              <StatusItem
                label="Multiple Loans"
                value={summary?.borrowersWithMultipleLoans}
                tone="orange"
              />

            </div>

          </DashboardPanel>

        </div>


        {/* ====================================================
            CREDIT INFORMATION
        ==================================================== */}

        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">

          <SectionHeading
            eyebrow="Credit risk"
            title="Credit information"
            description="Credit checks, borrower exposure and external debt indicators."
          />

          <div className="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">

            <KpiCard
              label="Credit Checked"
              value={formatNumber(
                summary?.borrowersCreditChecked
              )}
              icon="shield"
              tone="blue"
              helper="Borrowers with credit checks"
            />

            <KpiCard
              label="Default History"
              value={formatNumber(
                summary?.borrowersWithDefaultHistory
              )}
              icon="warning"
              tone="red"
              helper="Borrowers with prior defaults"
            />

            <KpiCard
              label="Active Listings"
              value={formatNumber(
                summary?.borrowersWithActiveListing
              )}
              icon="database"
              tone="orange"
              helper="Borrowers with active listings"
            />

            <KpiCard
              label="Multiple Facilities"
              value={formatNumber(
                summary?.borrowersWithMultipleFacilities
              )}
              icon="briefcase"
              tone="amber"
              helper="Multiple credit facilities"
            />

          </div>


          <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-5">

            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">

              <div>

                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Total external debt
                </p>

                <p className="mt-1 text-2xl font-bold text-slate-900">
                  {formatMoney(
                    summary?.totalExternalDebt
                  )}
                </p>

              </div>

              <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-white text-amber-600 shadow-sm">
                <Icon
                  name="database"
                  className="h-6 w-6"
                />
              </div>

            </div>

          </div>

        </section>


        {/* ====================================================
            REPAYMENT PERFORMANCE
        ==================================================== */}

        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">

          <SectionHeading
            eyebrow="Collections"
            title="Repayment performance"
            description="Collection performance, accrued amounts and missed payment indicators."
          />

          <div className="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">

            <KpiCard
              label="Principal Collected"
              value={formatMoney(
                summary?.totalPrincipalCollected
              )}
              icon="wallet"
              tone="blue"
              helper="Principal received"
            />

            <KpiCard
              label="Interest Collected"
              value={formatMoney(
                summary?.totalInterestCollected
              )}
              icon="chart"
              tone="emerald"
              helper="Interest received"
            />

            <KpiCard
              label="Fees Collected"
              value={formatMoney(
                summary?.totalFeesCollected
              )}
              icon="check"
              tone="indigo"
              helper="Fees received"
            />

            <KpiCard
              label="Total Collected"
              value={formatMoney(
                summary?.totalAmountCollected
              )}
              icon="wallet"
              tone="emerald"
              helper="All collections"
            />

            <KpiCard
              label="Unpaid Interest"
              value={formatMoney(
                summary?.interestAccruedUnpaid
              )}
              icon="alert"
              tone="amber"
              helper="Accrued but unpaid"
            />

            <KpiCard
              label="Unpaid Fees"
              value={formatMoney(
                summary?.feesAccruedUnpaid
              )}
              icon="alert"
              tone="orange"
              helper="Accrued fees outstanding"
            />

            <KpiCard
              label="Missed Payments"
              value={formatNumber(
                summary?.missedPayments
              )}
              icon="warning"
              tone="red"
              helper="Missed repayment events"
            />

            <KpiCard
              label="Overdue Payments"
              value={formatNumber(
                summary?.overduePayments
              )}
              icon="alert"
              tone="orange"
              helper="Payments past due"
            />

          </div>

        </section>


        {/* ====================================================
            FINANCIAL STATEMENT
        ==================================================== */}

        <FinancialStatementSection
          report={financialStatement}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />


        {/* ====================================================
            DATA QUALITY
        ==================================================== */}

        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">

          <SectionHeading
            eyebrow="Data integrity"
            title="Data quality"
            description="Validation indicators identifying incomplete or inconsistent portfolio data."
          />

          <div className="mt-5 grid grid-cols-2 gap-3 md:grid-cols-5">

            <StatusItem
              label="Missing Borrower"
              value={summary?.loansMissingBorrower}
              tone="red"
            />

            <StatusItem
              label="Missing National ID"
              value={summary?.borrowersMissingNationalId}
              tone="orange"
            />

            <StatusItem
              label="Missing Branch"
              value={summary?.loansMissingBranch}
              tone="amber"
            />

            <StatusItem
              label="Missing Currency"
              value={summary?.loansMissingCurrency}
              tone="amber"
            />

            <StatusItem
              label="Missing Schedule"
              value={summary?.loansMissingRepaymentSchedule}
              tone="red"
            />

          </div>


          {summary?.dataQualityWarnings &&
            summary.dataQualityWarnings.length > 0 && (

              <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4">

                <div className="flex gap-3">

                  <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-amber-100 text-amber-700">
                    <Icon
                      name="warning"
                      className="h-5 w-5"
                    />
                  </div>

                  <div>

                    <p className="font-semibold text-amber-900">
                      Validation warnings
                    </p>

                    <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-amber-800">

                      {summary.dataQualityWarnings.map(
                        (
                          warning,
                          index
                        ) => (
                          <li
                            key={`${warning}-${index}`}
                          >
                            {warning}
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

        <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">

          <BreakdownTable
            title="Borrowers by Gender"
            subtitle="Borrower distribution by gender."
            rows={genderBreakdown}
            formatMoney={formatMoney}
            formatNumber={formatNumber}
          />

          <BreakdownTable
            title="Loans by Loan Type"
            subtitle="Portfolio distribution by loan product."
            rows={loanTypeBreakdown}
            formatMoney={formatMoney}
            formatNumber={formatNumber}
          />

        </div>


        <BreakdownTable
          title="Loans by Branch"
          subtitle="Loan distribution across branches."
          rows={branchBreakdown}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
          fullWidth
        />


        {/* ====================================================
            FOOTER
        ==================================================== */}

        <footer className="border-t border-slate-200 py-6">

          <div className="flex flex-col gap-2 text-center text-xs text-slate-400 sm:flex-row sm:items-center sm:justify-between sm:text-left">

            <p>
              BNR Regulatory Report •{' '}
              {periodLabel}
            </p>

            <p>
              {summary?.reportReference
                ? `Reference: ${summary.reportReference}`
                : 'Regulatory reporting information'}
            </p>

          </div>

        </footer>

      </main>

    </div>
  );
}


// ============================================================
// LOADING STATE
// ============================================================

function BnrLoadingState() {
  return (
    <div className="min-h-screen bg-[#f4f7fb]">

      <div className="border-b border-slate-800 bg-slate-950 px-4 py-3 sm:px-6">
        <div className="mx-auto max-w-[1600px]">
          <div className="h-8 w-48 animate-pulse rounded bg-slate-800" />
        </div>
      </div>

      <div className="mx-auto max-w-[1600px] space-y-6 px-4 py-6 sm:px-6 lg:px-8">

        <div className="h-52 animate-pulse rounded-2xl bg-slate-800" />

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

        <div className="h-72 animate-pulse rounded-2xl bg-white shadow-sm" />

        <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">

          <div className="h-72 animate-pulse rounded-2xl bg-white shadow-sm" />

          <div className="h-72 animate-pulse rounded-2xl bg-white shadow-sm" />

        </div>

      </div>
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
  description?: string;
}) {
  return (
    <div>

      <p className="text-[11px] font-bold uppercase tracking-[0.16em] text-blue-600">
        {eyebrow}
      </p>

      <h2 className="mt-1 text-xl font-bold tracking-tight text-slate-900">
        {title}
      </h2>

      {description && (
        <p className="mt-1 max-w-3xl text-sm text-slate-500">
          {description}
        </p>
      )}

    </div>
  );
}


// ============================================================
// DASHBOARD PANEL
// ============================================================

function DashboardPanel({
  eyebrow,
  title,
  icon,
  children,
}: {
  eyebrow: string;
  title: string;
  icon: IconName;
  children: React.ReactNode;
}) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">

      <div className="mb-5 flex items-center gap-3">

        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-100 text-slate-700">
          <Icon
            name={icon}
            className="h-5 w-5"
          />
        </div>

        <div>

          <p className="text-[10px] font-bold uppercase tracking-[0.15em] text-blue-600">
            {eyebrow}
          </p>

          <h2 className="text-lg font-bold text-slate-900">
            {title}
          </h2>

        </div>

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
  tone,
  helper,
}: {
  label: string;
  value: string;
  icon: IconName;
  tone:
    | 'blue'
    | 'indigo'
    | 'emerald'
    | 'amber'
    | 'orange'
    | 'red'
    | 'rose'
    | 'slate';
  helper?: string;
}) {
  const toneMap = {
    blue: {
      icon: 'bg-blue-50 text-blue-700',
      dot: 'bg-blue-500',
    },
    indigo: {
      icon: 'bg-indigo-50 text-indigo-700',
      dot: 'bg-indigo-500',
    },
    emerald: {
      icon: 'bg-emerald-50 text-emerald-700',
      dot: 'bg-emerald-500',
    },
    amber: {
      icon: 'bg-amber-50 text-amber-700',
      dot: 'bg-amber-500',
    },
    orange: {
      icon: 'bg-orange-50 text-orange-700',
      dot: 'bg-orange-500',
    },
    red: {
      icon: 'bg-red-50 text-red-700',
      dot: 'bg-red-500',
    },
    rose: {
      icon: 'bg-rose-50 text-rose-700',
      dot: 'bg-rose-500',
    },
    slate: {
      icon: 'bg-slate-100 text-slate-700',
      dot: 'bg-slate-500',
    },
  }[tone];

  return (
    <div className="group rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition duration-200 hover:-translate-y-0.5 hover:shadow-md">

      <div className="flex items-start justify-between gap-3">

        <div
          className={`flex h-10 w-10 items-center justify-center rounded-xl ${toneMap.icon}`}
        >
          <Icon
            name={icon}
            className="h-5 w-5"
          />
        </div>

        <span
          className={`mt-1 h-2 w-2 rounded-full ${toneMap.dot}`}
        />

      </div>

      <p className="mt-5 text-sm font-medium text-slate-500">
        {label}
      </p>

      <p className="mt-1 break-words text-2xl font-bold tracking-tight text-slate-950">
        {value}
      </p>

      {helper && (
        <p className="mt-2 text-xs text-slate-400">
          {helper}
        </p>
      )}

    </div>
  );
}


// ============================================================
// RISK METRIC CARD
// ============================================================

function RiskMetricCard({
  label,
  value,
  amount,
  severity,
}: {
  label: string;
  value: string;
  amount: string;
  severity: 'low' | 'medium' | 'high' | 'critical';
}) {
  const config = {
    low: {
      badge: 'Low',
      badgeClass: 'bg-emerald-50 text-emerald-700',
      barClass: 'bg-emerald-500',
    },
    medium: {
      badge: 'Moderate',
      badgeClass: 'bg-amber-50 text-amber-700',
      barClass: 'bg-amber-500',
    },
    high: {
      badge: 'High',
      badgeClass: 'bg-orange-50 text-orange-700',
      barClass: 'bg-orange-500',
    },
    critical: {
      badge: 'Critical',
      badgeClass: 'bg-red-50 text-red-700',
      barClass: 'bg-red-500',
    },
  }[severity];

  const numericValue =
    Math.max(
      0,
      Number(
        value.replace('%', '')
      )
    );

  const barWidth =
    Math.min(
      100,
      numericValue * 2
    );

  return (
    <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">

      <div className="flex items-center justify-between gap-2">

        <p className="text-sm font-semibold text-slate-700">
          {label}
        </p>

        <span
          className={`rounded-full px-2 py-1 text-[10px] font-bold uppercase tracking-wide ${config.badgeClass}`}
        >
          {config.badge}
        </span>

      </div>

      <div className="mt-4 flex items-end justify-between gap-3">

        <p className="text-3xl font-bold tracking-tight text-slate-950">
          {value}
        </p>

        <p className="text-right text-xs text-slate-500">
          {amount}
        </p>

      </div>

      <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-slate-200">

        <div
          className={`h-full rounded-full ${config.barClass}`}
          style={{
            width: `${barWidth}%`,
          }}
        />

      </div>

    </div>
  );
}


// ============================================================
// PAR AGING CARD
// ============================================================

function ParAgingCard({
  label,
  amount,
  total,
  tone,
  formatMoney,
}: {
  label: string;
  amount?: number;
  total?: number;
  tone:
    | 'blue'
    | 'amber'
    | 'orange'
    | 'red'
    | 'rose'
    | 'slate';
  formatMoney: (
    value?: number
  ) => string;
}) {
  const amountNumber =
    Number(amount ?? 0);

  const totalNumber =
    Number(total ?? 0);

  const percentage =
    totalNumber > 0
      ? (amountNumber / totalNumber) * 100
      : 0;

  const toneMap = {
    blue: 'bg-blue-500',
    amber: 'bg-amber-500',
    orange: 'bg-orange-500',
    red: 'bg-red-500',
    rose: 'bg-rose-500',
    slate: 'bg-slate-500',
  };

  return (
    <div className="rounded-xl border border-slate-200 p-5">

      <div className="flex items-center justify-between">

        <div>

          <p className="text-sm font-semibold text-slate-800">
            {label}
          </p>

          <p className="mt-1 text-xs text-slate-400">
            Of outstanding principal
          </p>

        </div>

        <p className="text-sm font-bold text-slate-700">
          {percentage.toFixed(2)}%
        </p>

      </div>

      <p className="mt-5 text-2xl font-bold text-slate-950">
        {formatMoney(amountNumber)}
      </p>

      <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-100">

        <div
          className={`h-full rounded-full ${toneMap[tone]}`}
          style={{
            width: `${Math.min(
              100,
              percentage
            )}%`,
          }}
        />

      </div>

    </div>
  );
}


// ============================================================
// STATUS ITEM
// ============================================================

function StatusItem({
  label,
  value,
  tone = 'slate',
}: {
  label: string;
  value?: number;
  tone?:
    | 'blue'
    | 'indigo'
    | 'emerald'
    | 'amber'
    | 'orange'
    | 'red'
    | 'rose'
    | 'slate';
}) {
  const toneMap = {
    blue: 'bg-blue-50 text-blue-700',
    indigo: 'bg-indigo-50 text-indigo-700',
    emerald: 'bg-emerald-50 text-emerald-700',
    amber: 'bg-amber-50 text-amber-700',
    orange: 'bg-orange-50 text-orange-700',
    red: 'bg-red-50 text-red-700',
    rose: 'bg-rose-50 text-rose-700',
    slate: 'bg-slate-50 text-slate-700',
  };

  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50 p-4">

      <p className="truncate text-xs font-medium text-slate-500">
        {label}
      </p>

      <div className="mt-3 flex items-center justify-between gap-2">

        <p className="text-xl font-bold text-slate-950">
          {new Intl.NumberFormat(
            'en-US'
          ).format(
            Number(value ?? 0)
          )}
        </p>

        <span
          className={`h-2 w-2 rounded-full ${toneMap[tone].split(' ')[0]}`}
        />

      </div>

    </div>
  );
}


// ============================================================
// FILTER FIELD
// ============================================================

function FilterField({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div>

      <label className="mb-1.5 block text-xs font-semibold text-slate-500">
        {label}
      </label>

      {children}

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
  className,
}: {
  label: string;
  format: ExportFormat;
  loading: boolean;
  disabled: boolean;
  onClick: () => void;
  className: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={`Download ${format.toUpperCase()} report`}
      className={`inline-flex h-10 items-center gap-2 rounded-lg border px-3 text-xs font-bold transition disabled:cursor-not-allowed disabled:opacity-50 ${className}`}
    >

      <Icon
        name="download"
        className="h-4 w-4"
      />

      {loading
        ? 'Downloading...'
        : label}

    </button>
  );
}


// ============================================================
// FINANCIAL STATEMENT SECTION
// ============================================================

function FinancialStatementSection({
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
      <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">

        <SectionHeading
          eyebrow="Financial reporting"
          title="BNR financial statement"
          description="Financial statement data is not available for this reporting period."
        />

      </section>
    );
  }

  return (
    <section className="space-y-6">

      {/* HEADER */}

      <div className="rounded-2xl bg-slate-950 p-6 text-white shadow-lg">

        <div className="flex flex-col gap-5 md:flex-row md:items-center md:justify-between">

          <div>

            <p className="text-[11px] font-bold uppercase tracking-[0.16em] text-blue-400">
              Financial reporting
            </p>

            <h2 className="mt-1 text-2xl font-bold">
              BNR Financial Statement
            </h2>

            <p className="mt-2 max-w-2xl text-sm text-slate-400">
              Statement of financial position, income,
              expenses, cash flow and trial balance.
            </p>

          </div>

          <div className="rounded-xl border border-white/10 bg-white/5 px-4 py-3">

            <p className="text-[10px] uppercase tracking-wider text-slate-500">
              Statement period
            </p>

            <p className="mt-1 text-sm font-semibold text-white">
              {report.periodStart || '—'}
              <span className="mx-2 text-slate-500">
                →
              </span>
              {report.periodEnd || '—'}
            </p>

          </div>

        </div>

      </div>


      {/* BALANCE SHEET */}

      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">

        <div className="border-b border-slate-200 p-5 sm:p-6">

          <p className="text-[10px] font-bold uppercase tracking-[0.15em] text-blue-600">
            Statement of financial position
          </p>

          <h3 className="mt-1 text-xl font-bold text-slate-900">
            Balance Sheet
          </h3>

        </div>


        <FinancialRows
          title="Assets"
          rows={report.assets}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />

        <FinancialRows
          title="Liabilities"
          rows={report.liabilities}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />

        <FinancialRows
          title="Equity"
          rows={report.equity}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />


        <div className="grid grid-cols-1 gap-3 border-t border-slate-200 bg-slate-50 p-5 sm:grid-cols-2 xl:grid-cols-4">

          <FinancialSummaryCard
            label="Total Assets"
            value={formatMoney(
              report.totalAssets
            )}
          />

          <FinancialSummaryCard
            label="Total Liabilities"
            value={formatMoney(
              report.totalLiabilities
            )}
          />

          <FinancialSummaryCard
            label="Total Equity"
            value={formatMoney(
              report.totalEquity
            )}
          />

          <FinancialSummaryCard
            label="Current Period Net Income"
            value={formatMoney(
              report.currentPeriodNetIncome
            )}
          />

        </div>


        <div className="border-t border-slate-200 p-5">

          <BalanceIndicator
            label="Balance Sheet"
            balanced={
              report.balanceSheetBalanced
            }
          />

        </div>

      </div>


      {/* INCOME STATEMENT */}

      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">

        <div className="border-b border-slate-200 p-5 sm:p-6">

          <p className="text-[10px] font-bold uppercase tracking-[0.15em] text-emerald-600">
            Profitability
          </p>

          <h3 className="mt-1 text-xl font-bold text-slate-900">
            Income Statement
          </h3>

        </div>


        <FinancialRows
          title="Income"
          rows={report.income}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />

        <FinancialRows
          title="Expenses"
          rows={report.expenses}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />


        <div className="grid grid-cols-1 gap-3 border-t border-slate-200 bg-slate-50 p-5 sm:grid-cols-3">

          <FinancialSummaryCard
            label="Total Income"
            value={formatMoney(
              report.totalIncome
            )}
          />

          <FinancialSummaryCard
            label="Total Expenses"
            value={formatMoney(
              report.totalExpenses
            )}
          />

          <FinancialSummaryCard
            label="Net Income"
            value={formatMoney(
              report.netIncome
            )}
            highlighted
          />

        </div>

      </div>


      {/* CASH FLOW */}

      <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">

        <SectionHeading
          eyebrow="Liquidity"
          title="Cash flow"
          description="Cash movements related to lending and collections."
        />

        <div className="mt-5 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5">

          <FinancialSummaryCard
            label="Cash Used For Lending"
            value={formatMoney(
              report.cashUsedForLending
            )}
          />

          <FinancialSummaryCard
            label="Cash From Collections"
            value={formatMoney(
              report.cashFromCollections
            )}
          />

          <FinancialSummaryCard
            label="Cash From Fees"
            value={formatMoney(
              report.cashFromFees
            )}
          />

          <FinancialSummaryCard
            label="Other Cash Movement"
            value={formatMoney(
              report.otherCashMovement
            )}
          />

          <FinancialSummaryCard
            label="Net Change In Cash"
            value={formatMoney(
              report.netChangeInCash
            )}
            highlighted
          />

        </div>

      </div>


      {/* TRIAL BALANCE */}

      <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">

        <SectionHeading
          eyebrow="Accounting control"
          title="Trial balance"
          description="Debit and credit totals with balance verification."
        />

        <div className="mt-5 grid grid-cols-1 gap-3 sm:grid-cols-3">

          <FinancialSummaryCard
            label="Total Debit"
            value={formatMoney(
              report.trialBalanceDebit
            )}
          />

          <FinancialSummaryCard
            label="Total Credit"
            value={formatMoney(
              report.trialBalanceCredit
            )}
          />

          <BalanceIndicator
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
// FINANCIAL SUMMARY CARD
// ============================================================

function FinancialSummaryCard({
  label,
  value,
  highlighted = false,
}: {
  label: string;
  value: string;
  highlighted?: boolean;
}) {
  return (
    <div
      className={
        highlighted
          ? 'rounded-xl border border-emerald-200 bg-emerald-50 p-4'
          : 'rounded-xl border border-slate-200 bg-white p-4'
      }
    >

      <p className="text-xs font-medium text-slate-500">
        {label}
      </p>

      <p
        className={
          highlighted
            ? 'mt-2 break-words text-lg font-bold text-emerald-800'
            : 'mt-2 break-words text-lg font-bold text-slate-900'
        }
      >
        {value}
      </p>

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
  formatNumber,
}: {
  title: string;
  rows?: FinancialStatementRow[];

  formatMoney: (
    value?: number
  ) => string;

  formatNumber: (
    value?: number
  ) => string;
}) {
  if (!rows || rows.length === 0) {
    return (
      <div className="border-b border-slate-200 p-5 sm:p-6">

        <h4 className="font-semibold text-slate-800">
          {title}
        </h4>

        <p className="mt-2 text-sm text-slate-400">
          No accounts reported.
        </p>

      </div>
    );
  }

  return (
    <div className="border-b border-slate-200">

      <div className="px-5 pb-2 pt-5 sm:px-6">

        <h4 className="font-semibold text-slate-800">
          {title}
        </h4>

      </div>


      <div className="overflow-x-auto px-5 pb-5 sm:px-6">

        <table className="min-w-full text-sm">

          <thead>

            <tr className="border-b border-slate-200 text-left">

              <th className="px-3 py-3 text-[10px] font-bold uppercase tracking-wider text-slate-400">
                Code
              </th>

              <th className="px-3 py-3 text-[10px] font-bold uppercase tracking-wider text-slate-400">
                Account
              </th>

              <th className="px-3 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400">
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
                    className="transition hover:bg-slate-50"
                  >

                    <td className="whitespace-nowrap px-3 py-3 font-mono text-xs text-slate-400">
                      {row.code || '—'}
                    </td>

                    <td className="px-3 py-3 font-medium text-slate-700">
                      {row.name ||
                        'Unnamed Account'}
                    </td>

                    <td className="whitespace-nowrap px-3 py-3 text-right font-semibold text-slate-900">
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

      <div className="flex items-center justify-between gap-3">

        <div>

          <p className="text-xs font-medium text-slate-500">
            {label}
          </p>

          <p
            className={
              isBalanced
                ? 'mt-1 text-lg font-bold text-emerald-700'
                : 'mt-1 text-lg font-bold text-red-700'
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
              ? 'flex h-10 w-10 items-center justify-center rounded-full bg-emerald-100 text-emerald-700'
              : 'flex h-10 w-10 items-center justify-center rounded-full bg-red-100 text-red-700'
          }
        >
          <Icon
            name={
              isBalanced
                ? 'check'
                : 'warning'
            }
            className="h-5 w-5"
          />
        </div>

      </div>

    </div>
  );
}


// ============================================================
// BREAKDOWN TABLE
// ============================================================

function BreakdownTable({
  title,
  subtitle,
  rows,
  formatMoney,
  formatNumber,
  fullWidth = false,
}: {
  title: string;
  subtitle?: string;
  rows: BreakdownRow[];

  formatMoney: (
    value?: number
  ) => string;

  formatNumber: (
    value?: number
  ) => string;

  fullWidth?: boolean;
}) {
  const maxAmount =
    Math.max(
      ...rows.map(
        (row) =>
          Number(row.amount ?? 0)
      ),
      0
    );

  return (
    <section
      className={
        fullWidth
          ? 'rounded-2xl border border-slate-200 bg-white shadow-sm'
          : 'rounded-2xl border border-slate-200 bg-white shadow-sm'
      }
    >

      <div className="border-b border-slate-200 p-5">

        <p className="text-[10px] font-bold uppercase tracking-[0.15em] text-blue-600">
          Portfolio breakdown
        </p>

        <h2 className="mt-1 text-lg font-bold text-slate-900">
          {title}
        </h2>

        {subtitle && (
          <p className="mt-1 text-sm text-slate-500">
            {subtitle}
          </p>
        )}

      </div>


      {rows.length === 0 ? (

        <div className="p-10 text-center">

          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-xl bg-slate-100 text-slate-400">
            <Icon
              name="database"
              className="h-6 w-6"
            />
          </div>

          <p className="mt-3 text-sm font-medium text-slate-600">
            No data available
          </p>

          <p className="mt-1 text-xs text-slate-400">
            No records were returned for this reporting period.
          </p>

        </div>

      ) : (

        <div className="overflow-x-auto">

          <table className="min-w-full divide-y divide-slate-200">

            <thead className="bg-slate-50">

              <tr>

                <th className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  Category
                </th>

                <th className="px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  Loans / Borrowers
                </th>

                <th className="min-w-[250px] px-5 py-3 text-right text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  Amount
                </th>

              </tr>

            </thead>


            <tbody className="divide-y divide-slate-100">

              {rows.map(
                (
                  row,
                  index
                ) => {
                  const amount =
                    Number(
                      row.amount ?? 0
                    );

                  const percentage =
                    maxAmount > 0
                      ? (amount / maxAmount) * 100
                      : 0;

                  return (
                    <tr
                      key={`${row.label}-${index}`}
                      className="transition hover:bg-slate-50"
                    >

                      <td className="whitespace-nowrap px-5 py-4 text-sm font-semibold text-slate-800">
                        {row.label}
                      </td>

                      <td className="whitespace-nowrap px-5 py-4 text-right text-sm text-slate-600">
                        {formatNumber(
                          row.count
                        )}
                      </td>

                      <td className="px-5 py-4">

                        <div className="flex items-center gap-4">

                          <div className="hidden min-w-[100px] flex-1 sm:block">

                            <div className="h-2 overflow-hidden rounded-full bg-slate-100">

                              <div
                                className="h-full rounded-full bg-blue-500"
                                style={{
                                  width: `${percentage}%`,
                                }}
                              />

                            </div>

                          </div>

                          <span className="min-w-[130px] text-right text-sm font-bold text-slate-900">
                            {formatMoney(
                              amount
                            )}
                          </span>

                        </div>

                      </td>

                    </tr>
                  );
                }
              )}

            </tbody>

          </table>

        </div>
      )}

    </section>
  );
}


// ============================================================
// RISK SEVERITY
// ============================================================

function getRiskSeverity(
  value?: number
): 'low' | 'medium' | 'high' | 'critical' {
  const numericValue =
    Number(value ?? 0);

  if (numericValue <= 2) {
    return 'low';
  }

  if (numericValue <= 5) {
    return 'medium';
  }

  if (numericValue <= 10) {
    return 'high';
  }

  return 'critical';
}


// ============================================================
// PAR 30 AMOUNT
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
// PAR 60 AMOUNT
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
// PAR 90 AMOUNT
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
// ICON COMPONENT
// ============================================================

function Icon({
  name,
  className = 'h-5 w-5',
}: {
  name: IconName;
  className?: string;
}) {
  const common = {
    className,
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.8,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    viewBox: '0 0 24 24',
    xmlns: 'http://www.w3.org/2000/svg',
  };

  switch (name) {
    case 'briefcase':
      return (
        <svg {...common}>
          <rect
            x="3"
            y="7"
            width="18"
            height="13"
            rx="2"
          />
          <path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
          <path d="M3 12h18" />
          <path d="M10 12v2h4v-2" />
        </svg>
      );

    case 'wallet':
      return (
        <svg {...common}>
          <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H19a2 2 0 0 1 2 2v2H6.5A2.5 2.5 0 0 0 4 9.5v0A2.5 2.5 0 0 0 6.5 12H21v7a2 2 0 0 1-2 2H6.5A2.5 2.5 0 0 1 4 18.5v-13Z" />
          <path d="M21 7H6.5a2.5 2.5 0 0 0 0 5H21" />
          <path d="M17 9.5h.01" />
        </svg>
      );

    case 'chart':
      return (
        <svg {...common}>
          <path d="M4 19V5" />
          <path d="M4 19h17" />
          <path d="m7 15 4-4 3 2 5-6" />
          <path d="M16 7h3v3" />
        </svg>
      );

    case 'alert':
      return (
        <svg {...common}>
          <path d="M10.3 3.5 2.7 17a2 2 0 0 0 1.7 3h15.2a2 2 0 0 0 1.7-3L13.7 3.5a2 2 0 0 0-3.4 0Z" />
          <path d="M12 9v4" />
          <path d="M12 17h.01" />
        </svg>
      );

    case 'warning':
      return (
        <svg {...common}>
          <path d="M12 3 2.8 19h18.4L12 3Z" />
          <path d="M12 9v4" />
          <path d="M12 16h.01" />
        </svg>
      );

    case 'users':
      return (
        <svg {...common}>
          <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
          <circle
            cx="9"
            cy="7"
            r="4"
          />
          <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
          <path d="M16 3.13a4 4 0 0 1 0 7.75" />
        </svg>
      );

    case 'check':
      return (
        <svg {...common}>
          <path d="m5 12 4 4L19 6" />
        </svg>
      );

    case 'calendar':
      return (
        <svg {...common}>
          <rect
            x="3"
            y="4"
            width="18"
            height="17"
            rx="2"
          />
          <path d="M16 2v4" />
          <path d="M8 2v4" />
          <path d="M3 10h18" />
        </svg>
      );

    case 'download':
      return (
        <svg {...common}>
          <path d="M12 3v12" />
          <path d="m7 10 5 5 5-5" />
          <path d="M5 21h14" />
        </svg>
      );

    case 'refresh':
      return (
        <svg {...common}>
          <path d="M20 11a8.1 8.1 0 0 0-15.5-2M4 5v4h4" />
          <path d="M4 13a8.1 8.1 0 0 0 15.5 2M20 19v-4h-4" />
        </svg>
      );

    case 'building':
      return (
        <svg {...common}>
          <path d="M4 21V5a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v16" />
          <path d="M2 21h20" />
          <path d="M8 7h2" />
          <path d="M14 7h2" />
          <path d="M8 11h2" />
          <path d="M14 11h2" />
          <path d="M8 15h2" />
          <path d="M14 15h2" />
          <path d="M10 21v-3h4v3" />
        </svg>
      );

    case 'shield':
      return (
        <svg {...common}>
          <path d="M12 3 20 6v5c0 5-3.3 8.6-8 10-4.7-1.4-8-5-8-10V6l8-3Z" />
          <path d="m8.5 12 2.2 2.2 4.8-5" />
        </svg>
      );

    case 'database':
      return (
        <svg {...common}>
          <ellipse
            cx="12"
            cy="5"
            rx="8"
            ry="3"
          />
          <path d="M4 5v7c0 1.7 3.6 3 8 3s8-1.3 8-3V5" />
          <path d="M4 12v7c0 1.7 3.6 3 8 3s8-1.3 8-3v-7" />
        </svg>
      );

    case 'file':
      return (
        <svg {...common}>
          <path d="M6 3h8l4 4v14H6z" />
          <path d="M14 3v5h5" />
          <path d="M9 13h6" />
          <path d="M9 17h6" />
        </svg>
      );

    case 'arrow':
      return (
        <svg {...common}>
          <path d="M5 12h14" />
          <path d="m13 6 6 6-6 6" />
        </svg>
      );

    default:
      return (
        <svg {...common}>
          <circle
            cx="12"
            cy="12"
            r="9"
          />
        </svg>
      );
  }
}