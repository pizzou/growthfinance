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
// PAGE
// ============================================================

export default function BnrReportPage() {

  // ==========================================================
  // FILTERS
  // ==========================================================

  const [period, setPeriod] =
    useState<RegulatoryPeriod>('MONTHLY');

  const [from, setFrom] =
    useState('');

  const [to, setTo] =
    useState('');

  // ==========================================================
  // DATA
  // ==========================================================

  const [summary, setSummary] =
    useState<BnrSummary | null>(null);

  const [financialStatement, setFinancialStatement] =
    useState<BnrFinancialStatementReport | null>(null);

  const [loanTypes, setLoanTypes] =
    useState<BreakdownRow[]>([]);

  const [branches, setBranches] =
    useState<BreakdownRow[]>([]);

  const [genders, setGenders] =
    useState<BreakdownRow[]>([]);

  // ==========================================================
  // UI
  // ==========================================================

  const [loading, setLoading] =
    useState(true);

  const [error, setError] =
    useState<string | null>(null);

  const [downloading, setDownloading] =
    useState<ExportFormat | null>(null);


  // ==========================================================
  // PARAMETERS
  // ==========================================================

  const params =
    useMemo<BnrReportParams>(() => {

      const result: BnrReportParams = {
        period,
      };

      if (period === 'CUSTOM') {

        if (from) {
          result.from = from;
        }

        if (to) {
          result.to = to;
        }
      }

      return result;

    }, [
      period,
      from,
      to,
    ]);


  // ==========================================================
  // VALIDATION
  // ==========================================================

  const validate =
    useCallback((): string | null => {

      if (period !== 'CUSTOM') {
        return null;
      }

      if (!from) {
        return 'Please select the start date.';
      }

      if (!to) {
        return 'Please select the end date.';
      }

      if (from > to) {
        return 'Start date cannot be after the end date.';
      }

      return null;

    }, [
      period,
      from,
      to,
    ]);


  // ==========================================================
  // LOAD
  // ==========================================================

  const loadReport =
    useCallback(async () => {

      const validationError =
        validate();

      if (validationError) {

        setError(validationError);

        return;
      }

      try {

        setLoading(true);
        setError(null);

        const [
          summaryData,
          financialData,
          loanTypeData,
          branchData,
          genderData,
        ] =
          await Promise.all([

            regulatoryApi.bnrSummary(params),

            regulatoryApi.bnrFinancialStatement(params),

            regulatoryApi.bnrByLoanType(params),

            regulatoryApi.bnrByBranch(params),

            regulatoryApi.bnrByGender(params),

          ]);

        setSummary(summaryData);

        setFinancialStatement(financialData);

        setLoanTypes(loanTypeData);

        setBranches(branchData);

        setGenders(genderData);

      } catch (err) {

        console.error(
          'BNR report error:',
          err
        );

        setError(
          regulatoryApi.getErrorMessage(
            err,
            'Unable to load the BNR report.'
          )
        );

      } finally {

        setLoading(false);

      }

    }, [
      params,
      validate,
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
  // EXPORT
  // ==========================================================

  const exportReport =
    useCallback(
      async (
        format: ExportFormat
      ) => {

        const validationError =
          validate();

        if (validationError) {

          setError(validationError);

          return;
        }

        try {

          setDownloading(format);

          setError(null);

          await regulatoryApi.bnrExport(
            format,
            params
          );

        } catch (err) {

          console.error(
            'BNR export error:',
            err
          );

          setError(
            regulatoryApi.getErrorMessage(
              err,
              `Unable to export BNR ${format.toUpperCase()} report.`
            )
          );

        } finally {

          setDownloading(null);

        }

      },
      [
        params,
        validate,
      ]
    );


  // ==========================================================
  // FORMATTERS
  // ==========================================================

  const money =
    useCallback(
      (value?: number) => {

        const currency =
          summary?.currency ||
          financialStatement?.currency ||
          'RWF';

        try {

          return new Intl.NumberFormat(
            'en-RW',
            {
              style: 'currency',
              currency,
              maximumFractionDigits: 2,
            }
          ).format(
            Number(value ?? 0)
          );

        } catch {

          return `${currency} ${Number(
            value ?? 0
          ).toLocaleString()}`;

        }

      },
      [
        summary?.currency,
        financialStatement?.currency,
      ]
    );


  const number =
    useCallback(
      (value?: number) =>
        new Intl.NumberFormat(
          'en-US'
        ).format(
          Number(value ?? 0)
        ),
      []
    );


  const percent =
    useCallback(
      (value?: number) =>
        `${Number(value ?? 0).toFixed(2)}%`,
      []
    );


  // ==========================================================
  // LOADING
  // ==========================================================

  if (loading) {

    return (
      <div className="min-h-screen bg-slate-50 p-6">

        <div className="mx-auto max-w-7xl space-y-6">

          <div className="h-32 animate-pulse rounded-3xl bg-white shadow-sm" />

          <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-4">

            {Array.from({ length: 8 }).map(
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


  // ==========================================================
  // RENDER
  // ==========================================================

  return (

    <main className="min-h-screen bg-slate-50">

      <div className="mx-auto max-w-[1600px] space-y-6 p-4 md:p-6 lg:p-8">

        {/* ================================================== */}
        {/* HEADER */}
        {/* ================================================== */}

        <section className="overflow-hidden rounded-3xl bg-gradient-to-br from-slate-950 via-slate-900 to-blue-950 p-6 text-white shadow-xl md:p-8">

          <div className="flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">

            <div>

              <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/10 px-3 py-1 text-xs font-medium text-blue-100">

                <span className="h-2 w-2 rounded-full bg-emerald-400" />

                Regulatory Reporting

              </div>

              <h1 className="text-3xl font-bold tracking-tight md:text-4xl">
                BNR Reports
              </h1>

              <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-300 md:text-base">
                Regulatory portfolio reporting, financial statements,
                portfolio quality and institutional reporting information.
              </p>

            </div>


            <div className="flex flex-wrap gap-2">

              <ExportButton
                label="PDF"
                loading={downloading === 'pdf'}
                onClick={() => void exportReport('pdf')}
              />

              <ExportButton
                label="Excel"
                loading={downloading === 'xlsx'}
                onClick={() => void exportReport('xlsx')}
              />

              <ExportButton
                label="CSV"
                loading={downloading === 'csv'}
                onClick={() => void exportReport('csv')}
              />

            </div>

          </div>

        </section>


        {/* ================================================== */}
        {/* ERROR */}
        {/* ================================================== */}

        {error && (

          <div className="flex items-start justify-between gap-4 rounded-2xl border border-red-200 bg-red-50 p-4">

            <div>

              <p className="font-semibold text-red-800">
                Report Error
              </p>

              <p className="mt-1 text-sm text-red-700">
                {error}
              </p>

            </div>

            <button
              type="button"
              onClick={() => setError(null)}
              className="text-sm font-medium text-red-700 hover:text-red-900"
            >
              Dismiss
            </button>

          </div>

        )}


        {/* ================================================== */}
        {/* FILTER */}
        {/* ================================================== */}

        <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm md:p-6">

          <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">

            <div className="grid flex-1 gap-4 md:grid-cols-3">

              <Field label="Reporting Period">

                <select
                  value={period}
                  onChange={(event) =>
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


              <Field label="From">

                <input
                  type="date"
                  value={from}
                  disabled={period !== 'CUSTOM'}
                  onChange={(event) =>
                    setFrom(event.target.value)
                  }
                  className="input disabled:bg-slate-100"
                />

              </Field>


              <Field label="To">

                <input
                  type="date"
                  value={to}
                  disabled={period !== 'CUSTOM'}
                  onChange={(event) =>
                    setTo(event.target.value)
                  }
                  className="input disabled:bg-slate-100"
                />

              </Field>

            </div>


            <button
              type="button"
              onClick={() => void loadReport()}
              className="rounded-xl bg-slate-950 px-6 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-slate-800"
            >
              Refresh Report
            </button>

          </div>

        </section>


        {/* ================================================== */}
        {/* INSTITUTION */}
        {/* ================================================== */}

        {summary && (

          <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">

            <div className="flex flex-col gap-5 md:flex-row md:items-center md:justify-between">

              <div>

                <p className="text-xs font-semibold uppercase tracking-wider text-blue-600">
                  Reporting Institution
                </p>

                <h2 className="mt-1 text-2xl font-bold text-slate-900">
                  {summary.organizationName ||
                    'Organization'}
                </h2>

                <p className="mt-1 text-sm text-slate-500">
                  BNR Institution Code:{' '}
                  <span className="font-medium text-slate-700">
                    {summary.bnrInstitutionCode ||
                      'Not configured'}
                  </span>
                </p>

              </div>


              <div className="grid grid-cols-2 gap-4 text-sm md:text-right">

                <div>

                  <p className="text-slate-400">
                    Reporting Period
                  </p>

                  <p className="font-semibold text-slate-800">
                    {summary.periodStart || '—'}
                    {' → '}
                    {summary.periodEnd || '—'}
                  </p>

                </div>

                <div>

                  <p className="text-slate-400">
                    Currency
                  </p>

                  <p className="font-semibold text-slate-800">
                    {summary.currency || 'RWF'}
                  </p>

                </div>

              </div>

            </div>

          </section>

        )}


        {/* ================================================== */}
        {/* KPI */}
        {/* ================================================== */}

        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">

          <Kpi
            label="Total Loans"
            value={number(summary?.totalLoans)}
            icon="▣"
          />

          <Kpi
            label="Active Loans"
            value={number(summary?.activeLoans)}
            icon="↗"
          />

          <Kpi
            label="Principal Disbursed"
            value={money(summary?.totalPrincipalDisbursed)}
            icon="₣"
          />

          <Kpi
            label="Outstanding Principal"
            value={money(summary?.outstandingPrincipal)}
            icon="◈"
          />

          <Kpi
            label="Interest Collected"
            value={money(summary?.totalInterestCollected)}
            icon="%"
          />

          <Kpi
            label="Total Collected"
            value={money(summary?.totalAmountCollected)}
            icon="✓"
          />

          <Kpi
            label="Overdue Loans"
            value={number(summary?.overdueLoans)}
            icon="!"
            danger
          />

          <Kpi
            label="Defaulted Loans"
            value={number(summary?.defaultedLoans)}
            icon="!"
            danger
          />

        </div>


        {/* ================================================== */}
        {/* PORTFOLIO QUALITY */}
        {/* ================================================== */}

        <Section title="Portfolio Quality">

          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">

            <Metric
              label="PAR"
              value={percent(summary?.parRatio)}
              secondary={money(summary?.parAmount)}
            />

            <Metric
              label="PAR > 30 Days"
              value={percent(summary?.par30Ratio)}
              secondary={money(
                getPar30Amount(summary)
              )}
            />

            <Metric
              label="PAR > 60 Days"
              value={percent(summary?.par60Ratio)}
              secondary={money(
                getPar60Amount(summary)
              )}
            />

            <Metric
              label="PAR > 90 Days"
              value={percent(summary?.par90Ratio)}
              secondary={money(
                getPar90Amount(summary)
              )}
            />

            <Metric
              label="NPL Ratio"
              value={percent(summary?.nplRatio)}
              secondary={money(summary?.nplAmount)}
            />

            <Metric
              label="NPL Loans"
              value={number(summary?.nplLoanCount)}
            />

            <Metric
              label="Loans > 30 DPD"
              value={number(summary?.loansOver30Days)}
            />

            <Metric
              label="Loans > 90 DPD"
              value={number(summary?.loansOver90Days)}
            />

          </div>

        </Section>


        {/* ================================================== */}
        {/* AGING */}
        {/* ================================================== */}

        <Section title="Portfolio at Risk Aging">

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">

            <Metric
              label="1–30 Days"
              value={money(summary?.par1To30Amount)}
            />

            <Metric
              label="31–60 Days"
              value={money(summary?.par31To60Amount)}
            />

            <Metric
              label="61–90 Days"
              value={money(summary?.par61To90Amount)}
            />

            <Metric
              label="91–180 Days"
              value={money(summary?.par91To180Amount)}
            />

            <Metric
              label="181–365 Days"
              value={money(summary?.par181To365Amount)}
            />

            <Metric
              label="Over 365 Days"
              value={money(summary?.parOver365Amount)}
            />

          </div>

        </Section>


        {/* ================================================== */}
        {/* BORROWERS */}
        {/* ================================================== */}

        <Section title="Borrower Statistics">

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">

            <Metric
              label="Total Borrowers"
              value={number(summary?.totalBorrowers)}
            />

            <Metric
              label="Active Borrowers"
              value={number(summary?.activeBorrowers)}
            />

            <Metric
              label="Male Borrowers"
              value={number(summary?.maleBorrowers)}
            />

            <Metric
              label="Female Borrowers"
              value={number(summary?.femaleBorrowers)}
            />

            <Metric
              label="Youth Borrowers"
              value={number(summary?.youthBorrowers)}
            />

            <Metric
              label="Adult Borrowers"
              value={number(summary?.adultBorrowers)}
            />

            <Metric
              label="Senior Borrowers"
              value={number(summary?.seniorBorrowers)}
            />

            <Metric
              label="Multiple Loans"
              value={number(summary?.borrowersWithMultipleLoans)}
            />

          </div>

        </Section>


        {/* ================================================== */}
        {/* REPAYMENT */}
        {/* ================================================== */}

        <Section title="Repayment Performance">

          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">

            <Metric
              label="Principal Collected"
              value={money(summary?.totalPrincipalCollected)}
            />

            <Metric
              label="Interest Collected"
              value={money(summary?.totalInterestCollected)}
            />

            <Metric
              label="Fees Collected"
              value={money(summary?.totalFeesCollected)}
            />

            <Metric
              label="Total Collected"
              value={money(summary?.totalAmountCollected)}
            />

            <Metric
              label="Unpaid Interest"
              value={money(summary?.interestAccruedUnpaid)}
            />

            <Metric
              label="Unpaid Fees"
              value={money(summary?.feesAccruedUnpaid)}
            />

            <Metric
              label="Missed Payments"
              value={number(summary?.missedPayments)}
            />

            <Metric
              label="Overdue Payments"
              value={number(summary?.overduePayments)}
            />

          </div>

        </Section>


        {/* ================================================== */}
        {/* FINANCIAL STATEMENT */}
        {/* ================================================== */}

        <FinancialStatement
          report={financialStatement}
          money={money}
        />


        {/* ================================================== */}
        {/* BREAKDOWNS */}
        {/* ================================================== */}

        <div className="grid gap-6 xl:grid-cols-3">

          <Breakdown
            title="Loans by Loan Type"
            rows={loanTypes}
            money={money}
            number={number}
          />

          <Breakdown
            title="Loans by Branch"
            rows={branches}
            money={money}
            number={number}
          />

          <Breakdown
            title="Borrowers by Gender"
            rows={genders}
            money={money}
            number={number}
          />

        </div>


        {/* ================================================== */}
        {/* DATA QUALITY */}
        {/* ================================================== */}

        <Section title="Data Quality">

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">

            <Metric
              label="Missing Borrower"
              value={number(summary?.loansMissingBorrower)}
            />

            <Metric
              label="Missing National ID"
              value={number(summary?.borrowersMissingNationalId)}
            />

            <Metric
              label="Missing Branch"
              value={number(summary?.loansMissingBranch)}
            />

            <Metric
              label="Missing Currency"
              value={number(summary?.loansMissingCurrency)}
            />

            <Metric
              label="Missing Schedule"
              value={number(summary?.loansMissingRepaymentSchedule)}
            />

          </div>


          {summary?.dataQualityWarnings &&
            summary.dataQualityWarnings.length > 0 && (

            <div className="mt-5 rounded-2xl border border-amber-200 bg-amber-50 p-5">

              <p className="font-semibold text-amber-900">
                Validation Warnings
              </p>

              <ul className="mt-2 space-y-1 pl-5 text-sm text-amber-800">

                {summary.dataQualityWarnings.map(
                  (warning, index) => (

                    <li
                      key={`${warning}-${index}`}
                      className="list-disc"
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
        {/* FOOTER */}
        {/* ================================================== */}

        <footer className="pb-8 pt-2 text-center text-xs text-slate-400">

          BNR Regulatory Report
          {' • '}
          {period}

          {summary?.reportReference && (
            <>
              {' • '}
              {summary.reportReference}
            </>
          )}

        </footer>

      </div>

    </main>
  );
}


// ============================================================
// COMPONENTS
// ============================================================

function ExportButton({
  label,
  loading,
  onClick,
}: {
  label: string;
  loading: boolean;
  onClick: () => void;
}) {

  return (

    <button
      type="button"
      onClick={onClick}
      disabled={loading}
      className="rounded-xl border border-white/15 bg-white/10 px-4 py-2 text-sm font-semibold text-white backdrop-blur transition hover:bg-white/20 disabled:opacity-50"
    >
      {loading
        ? `Preparing ${label}...`
        : `Export ${label}`}
    </button>

  );
}


function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {

  return (

    <label className="block">

      <span className="mb-1.5 block text-sm font-medium text-slate-700">
        {label}
      </span>

      {children}

    </label>

  );
}


function Kpi({
  label,
  value,
  icon,
  danger = false,
}: {
  label: string;
  value: string;
  icon: string;
  danger?: boolean;
}) {

  return (

    <div className="group rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md">

      <div className="flex items-start justify-between">

        <p className="text-sm font-medium text-slate-500">
          {label}
        </p>

        <span
          className={
            danger
              ? 'flex h-9 w-9 items-center justify-center rounded-xl bg-red-50 text-sm font-bold text-red-600'
              : 'flex h-9 w-9 items-center justify-center rounded-xl bg-blue-50 text-sm font-bold text-blue-600'
          }
        >
          {icon}
        </span>

      </div>

      <p
        className={
          danger
            ? 'mt-4 text-2xl font-bold text-red-700'
            : 'mt-4 text-2xl font-bold tracking-tight text-slate-900'
        }
      >
        {value}
      </p>

    </div>

  );
}


function Section({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {

  return (

    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm md:p-6">

      <div className="mb-5">

        <h2 className="text-xl font-bold tracking-tight text-slate-900">
          {title}
        </h2>

      </div>

      {children}

    </section>

  );
}


function Metric({
  label,
  value,
  secondary,
}: {
  label: string;
  value: string;
  secondary?: string;
}) {

  return (

    <div className="rounded-2xl bg-slate-50 p-4 transition hover:bg-slate-100">

      <p className="text-sm text-slate-500">
        {label}
      </p>

      <p className="mt-1 text-xl font-bold text-slate-900">
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
// FINANCIAL STATEMENT
// ============================================================

function FinancialStatement({
  report,
  money,
}: {
  report: BnrFinancialStatementReport | null;
  money: (value?: number) => string;
}) {

  if (!report) {

    return null;

  }

  return (

    <Section title="Financial Statement">

      <div className="grid gap-6 xl:grid-cols-2">

        <FinancialGroup
          title="Assets"
          rows={report.assets}
          money={money}
        />

        <FinancialGroup
          title="Liabilities"
          rows={report.liabilities}
          money={money}
        />

        <FinancialGroup
          title="Equity"
          rows={report.equity}
          money={money}
        />

        <FinancialGroup
          title="Income"
          rows={report.income}
          money={money}
        />

        <FinancialGroup
          title="Expenses"
          rows={report.expenses}
          money={money}
        />

      </div>


      <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">

        <Metric
          label="Total Assets"
          value={money(report.totalAssets)}
        />

        <Metric
          label="Total Liabilities"
          value={money(report.totalLiabilities)}
        />

        <Metric
          label="Total Equity"
          value={money(report.totalEquity)}
        />

        <Metric
          label="Net Income"
          value={money(report.netIncome)}
        />

      </div>


      <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-5">

        <Metric
          label="Cash Used for Lending"
          value={money(report.cashUsedForLending)}
        />

        <Metric
          label="Cash From Collections"
          value={money(report.cashFromCollections)}
        />

        <Metric
          label="Cash From Fees"
          value={money(report.cashFromFees)}
        />

        <Metric
          label="Other Cash Movement"
          value={money(report.otherCashMovement)}
        />

        <Metric
          label="Net Change in Cash"
          value={money(report.netChangeInCash)}
        />

      </div>


      <div className="mt-6 grid gap-4 sm:grid-cols-3">

        <Metric
          label="Trial Balance Debit"
          value={money(report.trialBalanceDebit)}
        />

        <Metric
          label="Trial Balance Credit"
          value={money(report.trialBalanceCredit)}
        />

        <Metric
          label="Trial Balance"
          value={
            report.trialBalanceBalanced
              ? 'Balanced'
              : 'Not Balanced'
          }
        />

      </div>

    </Section>

  );
}


function FinancialGroup({
  title,
  rows,
  money,
}: {
  title: string;
  rows?: FinancialStatementRow[];
  money: (value?: number) => string;
}) {

  return (

    <div className="overflow-hidden rounded-2xl border border-slate-200">

      <div className="border-b border-slate-200 bg-slate-50 px-4 py-3">

        <h3 className="font-semibold text-slate-800">
          {title}
        </h3>

      </div>

      {!rows || rows.length === 0 ? (

        <div className="p-5 text-sm text-slate-400">
          No accounts reported.
        </div>

      ) : (

        <div className="overflow-x-auto">

          <table className="min-w-full text-sm">

            <tbody className="divide-y divide-slate-100">

              {rows.map(
                (row, index) => {

                  const value =
                    row.balance ??
                    row.amount ??
                    row.credit ??
                    row.debit ??
                    0;

                  return (

                    <tr
                      key={`${row.code || row.name}-${index}`}
                      className="hover:bg-slate-50"
                    >

                      <td className="px-4 py-3 text-slate-400">
                        {row.code || '—'}
                      </td>

                      <td className="px-4 py-3 font-medium text-slate-700">
                        {row.name || 'Unnamed Account'}
                      </td>

                      <td className="px-4 py-3 text-right font-semibold text-slate-900">
                        {money(value)}
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
// BREAKDOWN
// ============================================================

function Breakdown({
  title,
  rows,
  money,
  number,
}: {
  title: string;
  rows: BreakdownRow[];
  money: (value?: number) => string;
  number: (value?: number) => string;
}) {

  return (

    <section className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">

      <div className="border-b border-slate-200 p-5">

        <h2 className="font-bold text-slate-900">
          {title}
        </h2>

      </div>

      {rows.length === 0 ? (

        <div className="p-8 text-center text-sm text-slate-400">
          No data available.
        </div>

      ) : (

        <div className="overflow-x-auto">

          <table className="min-w-full text-sm">

            <thead className="bg-slate-50">

              <tr>

                <th className="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
                  Category
                </th>

                <th className="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-slate-500">
                  Loans
                </th>

                <th className="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-slate-500">
                  Amount
                </th>

              </tr>

            </thead>

            <tbody className="divide-y divide-slate-100">

              {rows.map(
                (row, index) => (

                  <tr
                    key={`${row.label}-${index}`}
                    className="hover:bg-slate-50"
                  >

                    <td className="px-5 py-3 font-medium text-slate-800">
                      {row.label}
                    </td>

                    <td className="px-5 py-3 text-right text-slate-600">
                      {number(row.count)}
                    </td>

                    <td className="px-5 py-3 text-right font-semibold text-slate-900">
                      {money(row.amount)}
                    </td>

                  </tr>

                )
              )}

            </tbody>

          </table>

        </div>

      )}

    </section>

  );
}


// ============================================================
// PAR CALCULATIONS
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