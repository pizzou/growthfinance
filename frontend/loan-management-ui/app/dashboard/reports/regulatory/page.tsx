'use client';

import { useEffect, useMemo, useState } from 'react';

import { useAuth } from '@/hooks/useAuth';

import {
  regulatoryApi,
  type ApiClient,
  type BnrFinancialStatementReport,
  type BnrReportParams,
  type BnrSummary,
  type BreakdownRow,
  type CreditRecord,
  type ExportFormat,
  type FinancialStatementAccount,
  type RegulatoryPeriod,
} from '@/services/regulatoryService';
import {
  PageSpinner,
} from '@/components/ui/Skeleton';

import {
  Button,
} from '@/components/ui/Button';

import {
  Modal,
} from '@/components/ui/Modal';


/* ============================================================
   HELPERS
   ============================================================ */

const fmt = (
  n?: number,
  currency = 'RWF'
): string => {

  if (n == null) {
    return '—';
  }

  return (
    new Intl.NumberFormat(
      'en-US',
      {
        maximumFractionDigits: 0,
      }
    ).format(n) +
    ' ' +
    currency
  );
};


const PERIODS: RegulatoryPeriod[] = [
  'DAILY',
  'WEEKLY',
  'MONTHLY',
  'QUARTERLY',
  'YEARLY',
  'CUSTOM',
];


/* ============================================================
   MAIN PAGE
   ============================================================ */

export default function RegulatoryReportsPage() {

  const {
    isAdmin,
    user,
  } = useAuth();

  const canView =
    isAdmin ||
    [
      'MANAGER',
      'AUDITOR',
    ].includes(
      user?.role || ''
    );

  const [
    tab,
    setTab,
  ] = useState<
    'bnr' |
    'credit-bureau' |
    'api-keys'
  >('bnr');


  if (!canView) {

    return (

      <div className="bg-white rounded-xl border border-gray-200 p-10 text-center">

        <p className="text-3xl mb-2">
          🔒
        </p>

        <p className="text-gray-600 text-sm">
          You don&apos;t have access to Regulatory Reports.
        </p>

      </div>
    );
  }


  return (

    <div className="space-y-6">

      {/* ======================================================
          PAGE HEADER
          ====================================================== */}

      <div>

        <h1 className="text-xl font-bold text-gray-900">
          Regulatory Reporting
        </h1>

        <p className="text-sm text-gray-500">
          BNR portfolio reports and credit bureau data exports,
          with secure API access for external systems.
        </p>

      </div>


      {/* ======================================================
          TABS
          ====================================================== */}

      <div className="flex gap-2 border-b border-gray-200">

        {[
          {
            key: 'bnr',
            label: '🏦 BNR Reports',
          },

          {
            key: 'credit-bureau',
            label: '📇 Credit Bureau',
          },

          {
            key: 'api-keys',
            label: '🔑 API Access',
            adminOnly: true,
          },

        ]
          .filter(
            (item) =>
              !item.adminOnly ||
              isAdmin
          )
          .map(
            (item) => (

              <button
                key={item.key}
                type="button"
                onClick={() =>
                  setTab(
                    item.key as
                      | 'bnr'
                      | 'credit-bureau'
                      | 'api-keys'
                  )
                }
                className={`
                  px-4
                  py-2.5
                  text-sm
                  font-semibold
                  border-b-2
                  -mb-px
                  transition-colors
                  ${
                    tab === item.key
                      ? 'border-teal-600 text-teal-700'
                      : 'border-transparent text-gray-500 hover:text-gray-700'
                  }
                `}
              >
                {item.label}
              </button>

            )
          )}

      </div>


      {/* ======================================================
          TAB CONTENT
          ====================================================== */}

      {tab === 'bnr' && (
        <BnrTab />
      )}

      {tab === 'credit-bureau' && (
        <CreditBureauTab
          isAdmin={!!isAdmin}
        />
      )}

      {tab === 'api-keys' && isAdmin && (
        <ApiKeysTab />
      )}

    </div>
  );
}


/* ============================================================
   BNR TAB
   ============================================================ */

function BnrTab() {

  const [
    period,
    setPeriod,
  ] = useState<RegulatoryPeriod>(
    'MONTHLY'
  );

  const [
    from,
    setFrom,
  ] = useState('');

  const [
    to,
    setTo,
  ] = useState('');

  const [
    summary,
    setSummary,
  ] = useState<BnrSummary | null>(
    null
  );

  const [
    financialStatement,
    setFinancialStatement,
  ] =
    useState<BnrFinancialStatementReport | null>(
      null
    );

  const [
    loanTypes,
    setLoanTypes,
  ] =
    useState<BreakdownRow[]>(
      []
    );

  const [
    branches,
    setBranches,
  ] =
    useState<BreakdownRow[]>(
      []
    );

  const [
    gender,
    setGender,
  ] =
    useState<BreakdownRow[]>(
      []
    );

  const [
    loading,
    setLoading,
  ] = useState(true);

  const [
    loadError,
    setLoadError,
  ] = useState('');

  const [
    exporting,
    setExporting,
  ] = useState<ExportFormat | null>(
    null
  );


  const params =
    useMemo<BnrReportParams>(
      () => ({

        period,

        from:
          period === 'CUSTOM'
            ? from || undefined
            : undefined,

        to:
          period === 'CUSTOM'
            ? to || undefined
            : undefined,

      }),
      [
        period,
        from,
        to,
      ]
    );


  /* ==========================================================
     LOAD
     ========================================================== */

  const load = async () => {

    if (
      period === 'CUSTOM' &&
      (
        !from ||
        !to
      )
    ) {

      setLoadError(
        'Please select both From and To dates.'
      );

      return;
    }


    if (
      period === 'CUSTOM' &&
      from > to
    ) {

      setLoadError(
        'The start date cannot be after the end date.'
      );

      return;
    }


    setLoading(true);

    setLoadError('');


    try {

      const [
        summaryResult,
        financialResult,
        loanTypeResult,
        branchResult,
        genderResult,
      ] =
        await Promise.all([

          regulatoryApi.bnrSummary(
            params
          ),

          regulatoryApi.bnrFinancialStatement(
            params
          ),

          regulatoryApi.bnrByLoanType(
            params
          ),

          regulatoryApi.bnrByBranch(
            params
          ),

          regulatoryApi.bnrByGender(
            params
          ),

        ]);


      setSummary(
        summaryResult
      );

      setFinancialStatement(
        financialResult
      );

      setLoanTypes(
        loanTypeResult
      );

      setBranches(
        branchResult
      );

      setGender(
        genderResult
      );

    } catch (error) {

      console.error(
        'Failed to load BNR report:',
        error
      );

      setLoadError(
        regulatoryApi.getErrorMessage(
          error,
          'Could not load BNR reports.'
        )
      );

    } finally {

      setLoading(false);
    }
  };


  /* ==========================================================
     INITIAL / PERIOD LOAD
     ========================================================== */

  useEffect(
    () => {

      if (
        period !== 'CUSTOM'
      ) {

        void load();
      }

    },
    // Period change should automatically reload.
    // Custom periods use Apply.
    [period]
  );


  /* ==========================================================
     EXPORT FINANCIAL STATEMENT
     ========================================================== */

  const exportFinancialStatement =
    async (
      format: ExportFormat
    ) => {

      if (
        period === 'CUSTOM' &&
        (
          !from ||
          !to
        )
      ) {

        setLoadError(
          'Please select both From and To dates.'
        );

        return;
      }


      if (
        period === 'CUSTOM' &&
        from > to
      ) {

        setLoadError(
          'The start date cannot be after the end date.'
        );

        return;
      }


      setExporting(
        format
      );

      try {

        await regulatoryApi
          .bnrFinancialStatementExport(
            format,
            params
          );

      } catch (error) {

        console.error(
          'Financial statement export failed:',
          error
        );

        setLoadError(
          regulatoryApi.getErrorMessage(
            error,
            'Financial statement export failed.'
          )
        );

      } finally {

        setExporting(
          null
        );
      }
    };


  const currency =
    summary?.currency ||
    financialStatement?.currency ||
    'RWF';


  /* ==========================================================
     RENDER
     ========================================================== */

  return (

    <div className="space-y-6">

      {/* ======================================================
          FILTERS
          ====================================================== */}

      <div className="flex items-end justify-between gap-4 flex-wrap">

        <div className="flex items-end gap-3 flex-wrap">

          <div>

            <label className="block text-xs font-semibold text-gray-500 mb-1">
              Report Period
            </label>

            <select
              value={period}
              onChange={(event) =>
                setPeriod(
                  event.target.value as RegulatoryPeriod
                )
              }
              className="border border-gray-200 rounded-lg px-3 py-2 text-sm"
            >

              {PERIODS.map(
                (item) => (

                  <option
                    key={item}
                    value={item}
                  >
                    {
                      item.charAt(0) +
                      item.slice(1).toLowerCase()
                    }
                  </option>

                )
              )}

            </select>

          </div>


          {period === 'CUSTOM' && (
            <>

              <div>

                <label className="block text-xs font-semibold text-gray-500 mb-1">
                  From
                </label>

                <input
                  type="date"
                  value={from}
                  onChange={(event) =>
                    setFrom(
                      event.target.value
                    )
                  }
                  className="border border-gray-200 rounded-lg px-3 py-2 text-sm"
                />

              </div>


              <div>

                <label className="block text-xs font-semibold text-gray-500 mb-1">
                  To
                </label>

                <input
                  type="date"
                  value={to}
                  onChange={(event) =>
                    setTo(
                      event.target.value
                    )
                  }
                  className="border border-gray-200 rounded-lg px-3 py-2 text-sm"
                />

              </div>


              <Button
                size="sm"
                variant="secondary"
                onClick={() =>
                  void load()
                }
              >
                Apply
              </Button>

            </>
          )}

        </div>


        {/* ====================================================
            FINANCIAL STATEMENT EXPORTS
            ==================================================== */}

        <div className="flex gap-2">

          {(
            [
              'xlsx',
              'csv',
              'pdf',
            ] as ExportFormat[]
          ).map(
            (format) => (

              <Button
                key={format}
                size="sm"
                variant="outline"
                loading={
                  exporting === format
                }
                disabled={
                  exporting !== null
                }
                onClick={() =>
                  void exportFinancialStatement(
                    format
                  )
                }
              >
                ⬇ {format.toUpperCase()}
              </Button>

            )
          )}

        </div>

      </div>


      {/* ======================================================
          ERROR
          ====================================================== */}

      {loadError && (

        <div className="bg-red-50 border border-red-200 rounded-xl p-5 text-center">

          <p className="text-red-700 text-sm font-semibold mb-3">
            {loadError}
          </p>

          <Button
            size="sm"
            variant="secondary"
            onClick={() =>
              void load()
            }
          >
            Try Again
          </Button>

        </div>
      )}


      {/* ======================================================
          LOADING
          ====================================================== */}

      {loading ? (

        <PageSpinner />

      ) : !summary ? (

        <p className="text-sm text-gray-400 text-center py-8">
          No data available.
        </p>

      ) : (

        <>

          {/* ==================================================
              SUMMARY HEADER
              ================================================== */}

          <div className="bg-white rounded-xl border border-gray-200 p-5">

            <div className="flex items-center justify-between flex-wrap gap-2 mb-1">

              <h2 className="font-semibold text-gray-800 text-sm">
                Loan Portfolio Summary
              </h2>

              <span className="text-xs text-gray-400">

                {summary.organizationName}

                {summary.bnrInstitutionCode
                  ? ` · Institution Code: ${summary.bnrInstitutionCode}`
                  : ''}

                {' · '}

                {summary.periodStart || '—'}

                {' to '}

                {summary.periodEnd || '—'}

              </span>

            </div>

          </div>


          {/* ==================================================
              LOAN STATUS CARDS
              ================================================== */}

          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">

            {[
              {
                label: 'Total Loans Issued',
                value:
                  summary.totalLoans ??
                  summary.totalLoansIssued ??
                  0,
              },

              {
                label: 'Active Loans',
                value:
                  summary.activeLoans ??
                  0,
              },

              {
                label: 'Closed Loans',
                value:
                  summary.closedLoans ??
                  0,
              },

              {
                label: 'Pending Loans',
                value:
                  summary.pendingLoans ??
                  0,
              },

              {
                label: 'Rejected Loans',
                value:
                  summary.rejectedLoans ??
                  0,
              },

              {
                label: 'Overdue Loans',
                value:
                  summary.overdueLoans ??
                  0,
              },

              {
                label: 'Defaulted / Written-off',
                value:
                  summary.defaultedLoans ??
                  0,
              },

            ].map(
              (card) => (

                <div
                  key={card.label}
                  className="bg-white rounded-xl border border-gray-200 p-4"
                >

                  <p className="text-gray-500 text-xs uppercase tracking-wide">
                    {card.label}
                  </p>

                  <p className="text-xl font-bold mt-1 text-gray-900">
                    {card.value}
                  </p>

                </div>

              )
            )}

          </div>


          {/* ==================================================
              FINANCIAL PORTFOLIO CARDS
              ================================================== */}

          <div className="grid grid-cols-2 md:grid-cols-3 gap-4">

            {[
              {
                label: 'Total Principal Disbursed',
                value: fmt(
                  summary.totalPrincipalDisbursed,
                  currency
                ),
                color: 'text-indigo-600',
              },

              {
                label: 'Outstanding Principal',
                value: fmt(
                  summary.outstandingPrincipal,
                  currency
                ),
                color: 'text-blue-600',
              },

              {
                label: 'Total Interest Collected',
                value: fmt(
                  summary.totalInterestCollected,
                  currency
                ),
                color: 'text-green-600',
              },

              {
                label: 'Interest Accrued (Unpaid)',
                value: fmt(
                  summary.interestAccruedUnpaid,
                  currency
                ),
                color: 'text-orange-600',
              },

              {
                label: 'Portfolio at Risk (PAR)',
                value:
                  `${fmt(
                    summary.parAmount,
                    currency
                  )} (${(
                    (summary.parRatio || 0) *
                    100
                  ).toFixed(1)}%)`,
                color: 'text-amber-600',
              },

              {
                label: 'NPL (>90 days)',
                value:
                  `${fmt(
                    summary.nplAmount,
                    currency
                  )} (${(
                    (summary.nplRatio || 0) *
                    100
                  ).toFixed(1)}%)`,
                color: 'text-red-600',
              },

            ].map(
              (card) => (

                <div
                  key={card.label}
                  className="bg-white rounded-xl border border-gray-200 p-5"
                >

                  <p className="text-gray-500 text-xs uppercase tracking-wide">
                    {card.label}
                  </p>

                  <p
                    className={`
                      text-lg
                      font-bold
                      mt-1
                      ${card.color}
                    `}
                  >
                    {card.value}
                  </p>

                </div>

              )
            )}

          </div>


          {/* ==================================================
              FINANCIAL STATEMENT
              ================================================== */}

          <FinancialStatementSection
            report={
              financialStatement
            }
            currency={
              currency
            }
          />


          {/* ==================================================
              BREAKDOWNS
              ================================================== */}

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">

            <div className="bg-white rounded-xl border border-gray-200 p-5">

              <h3 className="font-semibold text-gray-800 text-sm mb-3">
                Financial Inclusion (Gender)
              </h3>

              <BreakdownTable
                rows={gender}
                currency={currency}
              />

            </div>


            <div className="bg-white rounded-xl border border-gray-200 p-5">

              <h3 className="font-semibold text-gray-800 text-sm mb-3">
                By Loan Type
              </h3>

              <BreakdownTable
                rows={loanTypes}
                currency={currency}
              />

            </div>

          </div>


          <div className="bg-white rounded-xl border border-gray-200 p-5">

            <h3 className="font-semibold text-gray-800 text-sm mb-3">
              By Branch
            </h3>

            <BreakdownTable
              rows={branches}
              currency={currency}
            />

          </div>

        </>
      )}

    </div>
  );
}


/* ============================================================
   FINANCIAL STATEMENT SECTION
   ============================================================ */

function FinancialStatementSection({
  report,
  currency,
}: {
  report: BnrFinancialStatementReport | null;
  currency: string;
}) {

  if (!report) {

    return (

      <div className="bg-white rounded-xl border border-gray-200 p-5">

        <h2 className="font-semibold text-gray-800 text-sm">
          BNR Financial Statement
        </h2>

        <p className="text-sm text-gray-400 mt-3">
          No financial statement data available for this period.
        </p>

      </div>
    );
  }


  return (

    <div className="space-y-4">

      {/* ======================================================
          HEADER
          ====================================================== */}

      <div className="bg-white rounded-xl border border-gray-200 p-5">

        <div className="flex items-center justify-between flex-wrap gap-2">

          <div>

            <h2 className="font-semibold text-gray-800 text-sm">
              BNR Financial Statement
            </h2>

            <p className="text-xs text-gray-400 mt-1">
              Balance Sheet, Profit &amp; Loss, Trial Balance and Cash Flow
            </p>

          </div>


          <div className="text-xs text-gray-400">

            {report.periodStart || '—'}

            {' to '}

            {report.periodEnd || '—'}

          </div>

        </div>

      </div>


      {/* ======================================================
          BALANCE SHEET SUMMARY
          ====================================================== */}

      <div className="bg-white rounded-xl border border-gray-200 p-5">

        <h3 className="font-semibold text-gray-800 text-sm mb-4">
          Balance Sheet
        </h3>


        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">

          <StatementMetric
            label="Total Assets"
            value={fmt(
              report.totalAssets,
              currency
            )}
          />

          <StatementMetric
            label="Total Liabilities"
            value={fmt(
              report.totalLiabilities,
              currency
            )}
          />

          <StatementMetric
            label="Total Equity"
            value={fmt(
              report.totalEquity,
              currency
            )}
          />

          <StatementMetric
            label="Current Period Net Income"
            value={fmt(
              report.currentPeriodNetIncome,
              currency
            )}
          />

        </div>


        <BalanceStatus
          label="Balance Sheet"
          balanced={
            report.balanceSheetBalanced
          }
        />

      </div>


      {/* ======================================================
          PROFIT AND LOSS
          ====================================================== */}

      <div className="bg-white rounded-xl border border-gray-200 p-5">

        <h3 className="font-semibold text-gray-800 text-sm mb-4">
          Profit &amp; Loss
        </h3>


        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">

          <StatementMetric
            label="Total Income"
            value={fmt(
              report.totalIncome,
              currency
            )}
          />

          <StatementMetric
            label="Total Expenses"
            value={fmt(
              report.totalExpenses,
              currency
            )}
          />

          <StatementMetric
            label="Net Income"
            value={fmt(
              report.netIncome,
              currency
            )}
          />

        </div>

      </div>


      {/* ======================================================
          TRIAL BALANCE
          ====================================================== */}

      <div className="bg-white rounded-xl border border-gray-200 p-5">

        <h3 className="font-semibold text-gray-800 text-sm mb-4">
          Trial Balance
        </h3>


        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">

          <StatementMetric
            label="Total Debit"
            value={fmt(
              report.trialBalanceDebit,
              currency
            )}
          />

          <StatementMetric
            label="Total Credit"
            value={fmt(
              report.trialBalanceCredit,
              currency
            )}
          />

        </div>


        <BalanceStatus
          label="Trial Balance"
          balanced={
            report.trialBalanceBalanced
          }
        />

      </div>


      {/* ======================================================
          CASH FLOW
          ====================================================== */}

      <div className="bg-white rounded-xl border border-gray-200 p-5">

        <h3 className="font-semibold text-gray-800 text-sm mb-4">
          Cash Flow
        </h3>


        <div className="grid grid-cols-2 md:grid-cols-5 gap-4">

          <StatementMetric
            label="Cash Used For Lending"
            value={fmt(
              report.cashUsedForLending,
              currency
            )}
          />

          <StatementMetric
            label="Cash From Collections"
            value={fmt(
              report.cashFromCollections,
              currency
            )}
          />

          <StatementMetric
            label="Cash From Fees"
            value={fmt(
              report.cashFromFees,
              currency
            )}
          />

          <StatementMetric
            label="Other Cash Movement"
            value={fmt(
              report.otherCashMovement,
              currency
            )}
          />

          <StatementMetric
            label="Net Change In Cash"
            value={fmt(
              report.netChangeInCash,
              currency
            )}
          />

        </div>

      </div>


      {/* ======================================================
          ACCOUNT DETAILS
          ====================================================== */}

      <StatementAccountTable
        title="Assets"
        rows={
          report.assets || []
        }
        currency={
          currency
        }
      />

      <StatementAccountTable
        title="Liabilities"
        rows={
          report.liabilities || []
        }
        currency={
          currency
        }
      />

      <StatementAccountTable
        title="Equity"
        rows={
          report.equity || []
        }
        currency={
          currency
        }
      />

      <StatementAccountTable
        title="Income"
        rows={
          report.income || []
        }
        currency={
          currency
        }
      />

      <StatementAccountTable
        title="Expenses"
        rows={
          report.expenses || []
        }
        currency={
          currency
        }
      />

    </div>
  );
}


/* ============================================================
   STATEMENT METRIC
   ============================================================ */

function StatementMetric({
  label,
  value,
}: {
  label: string;
  value: string;
}) {

  return (

    <div className="bg-gray-50 rounded-lg p-4">

      <p className="text-gray-500 text-xs uppercase tracking-wide">
        {label}
      </p>

      <p className="text-base font-bold mt-1 text-gray-900">
        {value}
      </p>

    </div>
  );
}


/* ============================================================
   BALANCE STATUS
   ============================================================ */

function BalanceStatus({
  label,
  balanced,
}: {
  label: string;
  balanced?: boolean;
}) {

  if (
    balanced === undefined ||
    balanced === null
  ) {

    return null;
  }


  return (

    <div
      className={`
        mt-4
        rounded-lg
        px-4
        py-3
        text-sm
        font-semibold
        ${
          balanced
            ? 'bg-green-50 text-green-700 border border-green-200'
            : 'bg-red-50 text-red-700 border border-red-200'
        }
      `}
    >

      {balanced
        ? `✓ ${label} is balanced`
        : `⚠ ${label} is not balanced`}

    </div>
  );
}


/* ============================================================
   STATEMENT ACCOUNT TABLE
   ============================================================ */

function StatementAccountTable({
  title,
  rows,
  currency,
}: {
  title: string;
  rows: FinancialStatementAccount[];
  currency: string;
}) {

  if (
    rows.length === 0
  ) {

    return null;
  }


  return (

    <div className="bg-white rounded-xl border border-gray-200 p-5">

      <h3 className="font-semibold text-gray-800 text-sm mb-3">
        {title}
      </h3>


      <div className="overflow-x-auto">

        <table className="w-full text-sm">

          <thead>

            <tr className="text-left text-gray-500 text-xs uppercase">

              <th className="pb-2">
                Account
              </th>

              <th className="pb-2 text-right">
                Balance
              </th>

            </tr>

          </thead>


          <tbody>

            {rows.map(
              (row, index) => (

                <tr
                  key={
                    `${row.code || ''}-${row.name || ''}-${index}`
                  }
                  className="border-t border-gray-50"
                >

                  <td className="py-2 text-gray-700">

                    <span className="font-medium">
                      {row.code || ''}
                    </span>

                    {row.code && row.name
                      ? ' - '
                      : ''}

                    {row.name || 'Unnamed account'}

                  </td>


                  <td className="py-2 text-right text-gray-700">

                    {fmt(
                      row.balance,
                      currency
                    )}

                  </td>

                </tr>

              )
            )}

          </tbody>

        </table>

      </div>

    </div>
  );
}


/* ============================================================
   BREAKDOWN TABLE
   ============================================================ */

function BreakdownTable({
  rows,
  currency,
}: {
  rows: BreakdownRow[];
  currency: string;
}) {

  if (
    rows.length === 0
  ) {

    return (

      <p className="text-sm text-gray-400">
        No data for this period.
      </p>
    );
  }


  return (

    <table className="w-full text-sm">

      <thead>

        <tr className="text-left text-gray-500 text-xs uppercase">

          <th className="pb-2">
            Category
          </th>

          <th className="pb-2 text-right">
            Count
          </th>

          <th className="pb-2 text-right">
            Amount
          </th>

        </tr>

      </thead>


      <tbody>

        {rows.map(
          (row, index) => (

            <tr
              key={
                `${row.label}-${index}`
              }
              className="border-t border-gray-50"
            >

              <td className="py-2 text-gray-700">
                {row.label}
              </td>

              <td className="py-2 text-right text-gray-800 font-medium">
                {row.count}
              </td>

              <td className="py-2 text-right text-gray-600">
                {fmt(
                  row.amount,
                  currency
                )}
              </td>

            </tr>

          )
        )}

      </tbody>

    </table>
  );
}


/* ============================================================
   CREDIT BUREAU TAB
   ============================================================ */

function CreditBureauTab({
  isAdmin: _isAdmin,
}: {
  isAdmin: boolean;
}) {

  const [
    records,
    setRecords,
  ] =
    useState<CreditRecord[]>(
      []
    );

  const [
    loading,
    setLoading,
  ] = useState(true);

  const [
    loadError,
    setLoadError,
  ] = useState('');

  const [
    exporting,
    setExporting,
  ] =
    useState<ExportFormat | null>(
      null
    );

  const [
    from,
    setFrom,
  ] = useState('');

  const [
    to,
    setTo,
  ] = useState('');


  const load = async () => {

    setLoading(true);

    setLoadError('');

    try {

      const result =
        await regulatoryApi
          .creditBureauPreview({
            from:
              from || undefined,

            to:
              to || undefined,
          });

      setRecords(
        result
      );

    } catch (error) {

      console.error(
        error
      );

      setLoadError(
        regulatoryApi.getErrorMessage(
          error,
          'Could not load credit bureau records.'
        )
      );

    } finally {

      setLoading(false);
    }
  };


  useEffect(
    () => {

      void load();

    },
    []
  );


  const doExport =
    async (
      format: ExportFormat
    ) => {

      setExporting(
        format
      );

      try {

        await regulatoryApi
          .creditBureauExport(
            format,
            {
              from:
                from || undefined,

              to:
                to || undefined,
            }
          );

      } catch (error) {

        alert(
          regulatoryApi.getErrorMessage(
            error,
            'Export failed'
          )
        );

      } finally {

        setExporting(
          null
        );
      }
    };


  return (

    <div className="space-y-4">

      <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 text-xs text-amber-800">

        This screen contains borrower-level personal data
        (national ID, phone, date of birth). Every view and
        export here is written to the audit log.

      </div>


      <div className="flex items-end justify-between gap-4 flex-wrap">

        <div className="flex items-end gap-3">

          <div>

            <label className="block text-xs font-semibold text-gray-500 mb-1">
              From
            </label>

            <input
              type="date"
              value={from}
              onChange={(event) =>
                setFrom(
                  event.target.value
                )
              }
              className="border border-gray-200 rounded-lg px-3 py-2 text-sm"
            />

          </div>


          <div>

            <label className="block text-xs font-semibold text-gray-500 mb-1">
              To
            </label>

            <input
              type="date"
              value={to}
              onChange={(event) =>
                setTo(
                  event.target.value
                )
              }
              className="border border-gray-200 rounded-lg px-3 py-2 text-sm"
            />

          </div>


          <Button
            size="sm"
            variant="secondary"
            onClick={() =>
              void load()
            }
          >
            Apply
          </Button>

        </div>


        <div className="flex gap-2">

          {(
            [
              'xlsx',
              'csv',
              'pdf',
            ] as ExportFormat[]
          ).map(
            (format) => (

              <Button
                key={format}
                size="sm"
                variant="outline"
                loading={
                  exporting === format
                }
                disabled={
                  exporting !== null
                }
                onClick={() =>
                  void doExport(
                    format
                  )
                }
              >
                ⬇ {format.toUpperCase()}
              </Button>

            )
          )}

        </div>

      </div>


      {loading ? (

        <PageSpinner />

      ) : loadError ? (

        <div className="bg-red-50 border border-red-200 rounded-xl p-5 text-center">

          <p className="text-red-700 text-sm font-semibold mb-3">
            {loadError}
          </p>

          <Button
            size="sm"
            variant="secondary"
            onClick={() =>
              void load()
            }
          >
            Try Again
          </Button>

        </div>

      ) : (

        <div className="bg-white rounded-xl border border-gray-200 overflow-x-auto">

          <table className="w-full text-sm">

            <thead>

              <tr className="text-left text-gray-500 text-xs uppercase bg-gray-50">

                <th className="px-4 py-2">
                  Borrower
                </th>

                <th className="px-4 py-2">
                  Loan #
                </th>

                <th className="px-4 py-2">
                  Type
                </th>

                <th className="px-4 py-2">
                  Status
                </th>

                <th className="px-4 py-2 text-right">
                  Amount
                </th>

                <th className="px-4 py-2 text-right">
                  Outstanding
                </th>

                <th className="px-4 py-2 text-right">
                  Days Past Due
                </th>

                <th className="px-4 py-2">
                  Branch
                </th>

              </tr>

            </thead>


            <tbody>

              {records
                .slice(
                  0,
                  200
                )
                .map(
                  (record, index) => (

                    <tr
                      key={index}
                      className="border-t border-gray-50"
                    >

                      <td className="px-4 py-2 text-gray-800">
                        {record.fullName}
                      </td>

                      <td className="px-4 py-2 text-gray-600">
                        {record.loanNumber}
                      </td>

                      <td className="px-4 py-2 text-gray-600">
                        {record.loanType}
                      </td>

                      <td className="px-4 py-2">

                        <span className="text-xs px-2 py-0.5 rounded-full bg-gray-100 text-gray-700">
                          {record.loanStatus}
                        </span>

                      </td>

                      <td className="px-4 py-2 text-right">
                        {fmt(
                          record.loanAmount
                        )}
                      </td>

                      <td className="px-4 py-2 text-right">
                        {fmt(
                          record.outstandingBalance
                        )}
                      </td>

                      <td className="px-4 py-2 text-right">
                        {record.daysPastDue ?? 0}
                      </td>

                      <td className="px-4 py-2 text-gray-500">
                        {record.branchName}
                      </td>

                    </tr>

                  )
                )}

            </tbody>

          </table>


          {records.length === 0 && (

            <p className="text-center text-sm text-gray-400 py-8">
              No records for this period.
            </p>
          )}


          {records.length > 200 && (

            <p className="text-center text-xs text-gray-400 py-3">

              Showing first 200 of {records.length}
              — full data is in the export.

            </p>
          )}

        </div>

      )}

    </div>
  );
}


/* ============================================================
   API KEYS TAB
   ============================================================ */

function ApiKeysTab() {

  const [
    clients,
    setClients,
  ] =
    useState<ApiClient[]>(
      []
    );

  const [
    loading,
    setLoading,
  ] = useState(true);

  const [
    loadError,
    setLoadError,
  ] = useState('');

  const [
    showCreate,
    setShowCreate,
  ] = useState(false);

  const [
    newKey,
    setNewKey,
  ] =
    useState<{
      apiKey: string;
      client: ApiClient;
    } | null>(
      null
    );

  const [
    form,
    setForm,
  ] =
    useState({
      name: '',
      clientType:
        'BNR' as
          | 'BNR'
          | 'CREDIT_BUREAU',
      contactEmail: '',
      description: '',
    });

  const [
    saving,
    setSaving,
  ] = useState(false);


  const load = async () => {

    setLoading(true);

    setLoadError('');

    try {

      const result =
        await regulatoryApi
          .listApiClients();

      setClients(
        result
      );

    } catch (error) {

      console.error(
        error
      );

      setLoadError(
        regulatoryApi.getErrorMessage(
          error,
          'Could not load API keys.'
        )
      );

    } finally {

      setLoading(false);
    }
  };


  useEffect(
    () => {

      void load();

    },
    []
  );


  const create =
    async () => {

      if (
        !form.name.trim()
      ) {

        alert(
          'Name is required'
        );

        return;
      }


      setSaving(
        true
      );

      try {

        const result =
          await regulatoryApi
            .createApiClient(
              form
            ) as {
              apiKey: string;
              client: ApiClient;
            };


        setNewKey(
          result
        );

        setShowCreate(
          false
        );

        setForm({
          name: '',
          clientType:
            'BNR',
          contactEmail: '',
          description: '',
        });

        await load();

      } catch (error) {

        alert(
          regulatoryApi.getErrorMessage(
            error,
            'Failed to create API key'
          )
        );

      } finally {

        setSaving(
          false
        );
      }
    };


  const revoke =
    async (
      id: number
    ) => {

      if (
        !confirm(
          'Revoke this API key? Any system using it will immediately lose access.'
        )
      ) {

        return;
      }


      try {

        await regulatoryApi
          .revokeApiClient(
            id
          );

        await load();

      } catch (error) {

        alert(
          regulatoryApi.getErrorMessage(
            error,
            'Failed to revoke'
          )
        );
      }
    };


  return (

    <div className="space-y-4">

      <div className="flex items-center justify-between">

        <p className="text-sm text-gray-500 max-w-2xl">

          Issue API keys for external regulatory systems.
          A BNR key can only call the BNR report endpoints;
          a Credit Bureau key can only call the credit bureau
          export endpoints. Keys are scoped to your organization.

        </p>


        <Button
          onClick={() =>
            setShowCreate(
              true
            )
          }
        >
          + New API Key
        </Button>

      </div>


      {loading ? (

        <PageSpinner />

      ) : loadError ? (

        <div className="bg-red-50 border border-red-200 rounded-xl p-5 text-center">

          <p className="text-red-700 text-sm font-semibold mb-3">
            {loadError}
          </p>

          <Button
            size="sm"
            variant="secondary"
            onClick={() =>
              void load()
            }
          >
            Try Again
          </Button>

        </div>

      ) : (

        <div className="bg-white rounded-xl border border-gray-200 overflow-x-auto">

          <table className="w-full text-sm">

            <thead>

              <tr className="text-left text-gray-500 text-xs uppercase bg-gray-50">

                <th className="px-4 py-2">
                  Name
                </th>

                <th className="px-4 py-2">
                  Type
                </th>

                <th className="px-4 py-2">
                  Key Prefix
                </th>

                <th className="px-4 py-2">
                  Status
                </th>

                <th className="px-4 py-2">
                  Last Used
                </th>

                <th className="px-4 py-2">
                </th>

              </tr>

            </thead>


            <tbody>

              {clients.map(
                (client) => (

                  <tr
                    key={client.id}
                    className="border-t border-gray-50"
                  >

                    <td className="px-4 py-2 text-gray-800 font-medium">
                      {client.name}
                    </td>

                    <td className="px-4 py-2">

                      <span
                        className={`
                          text-xs
                          px-2
                          py-0.5
                          rounded-full
                          ${
                            client.clientType === 'BNR'
                              ? 'bg-blue-100 text-blue-700'
                              : 'bg-purple-100 text-purple-700'
                          }
                        `}
                      >

                        {
                          client.clientType === 'BNR'
                            ? 'National Bank of Rwanda'
                            : 'Credit Bureau'
                        }

                      </span>

                    </td>

                    <td className="px-4 py-2 font-mono text-xs text-gray-500">
                      {client.keyPrefix}…
                    </td>

                    <td className="px-4 py-2">

                      {
                        client.revokedAt ||
                        !client.active
                          ? (
                            <span className="text-xs px-2 py-0.5 rounded-full bg-red-100 text-red-700">
                              Revoked
                            </span>
                          )
                          : (
                            <span className="text-xs px-2 py-0.5 rounded-full bg-green-100 text-green-700">
                              Active
                            </span>
                          )
                      }

                    </td>

                    <td className="px-4 py-2 text-gray-500 text-xs">

                      {client.lastUsedAt
                        ? new Date(
                            client.lastUsedAt
                          ).toLocaleString()
                        : 'Never'}

                    </td>

                    <td className="px-4 py-2 text-right">

                      {client.active &&
                        !client.revokedAt && (

                          <Button
                            size="xs"
                            variant="danger"
                            onClick={() =>
                              void revoke(
                                client.id
                              )
                            }
                          >
                            Revoke
                          </Button>

                        )}

                    </td>

                  </tr>

                )
              )}

            </tbody>

          </table>


          {clients.length === 0 && (

            <p className="text-center text-sm text-gray-400 py-8">
              No API keys issued yet.
            </p>
          )}

        </div>
      )}


      {/* ======================================================
          CREATE API KEY MODAL
          ====================================================== */}

      <Modal
        open={showCreate}
        onClose={() =>
          setShowCreate(false)
        }
        title="Issue New API Key"
        footer={
          <>
            <Button
              variant="secondary"
              onClick={() =>
                setShowCreate(false)
              }
            >
              Cancel
            </Button>

            <Button
              loading={saving}
              onClick={() =>
                void create()
              }
            >
              Create Key
            </Button>
          </>
        }
      >

        <div className="space-y-4">

          <div>

            <label className="block text-xs font-semibold text-gray-500 mb-1">
              Integration Name
            </label>

            <input
              value={form.name}
              onChange={(event) =>
                setForm(
                  (current) => ({
                    ...current,
                    name:
                      event.target.value,
                  })
                )
              }
              placeholder="e.g. BNR Production Integration"
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm"
            />

          </div>


          <div>

            <label className="block text-xs font-semibold text-gray-500 mb-1">
              Client Type
            </label>

            <select
              value={
                form.clientType
              }
              onChange={(event) =>
                setForm(
                  (current) => ({
                    ...current,
                    clientType:
                      event.target.value as
                        | 'BNR'
                        | 'CREDIT_BUREAU',
                  })
                )
              }
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm"
            >

              <option value="BNR">
                National Bank of Rwanda (BNR)
              </option>

              <option value="CREDIT_BUREAU">
                Credit Bureau
              </option>

            </select>

          </div>


          <div>

            <label className="block text-xs font-semibold text-gray-500 mb-1">
              Contact Email (optional)
            </label>

            <input
              value={
                form.contactEmail
              }
              onChange={(event) =>
                setForm(
                  (current) => ({
                    ...current,
                    contactEmail:
                      event.target.value,
                  })
                )
              }
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm"
            />

          </div>


          <div>

            <label className="block text-xs font-semibold text-gray-500 mb-1">
              Description (optional)
            </label>

            <textarea
              value={
                form.description
              }
              onChange={(event) =>
                setForm(
                  (current) => ({
                    ...current,
                    description:
                      event.target.value,
                  })
                )
              }
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm"
              rows={2}
            />

          </div>

        </div>

      </Modal>


      {/* ======================================================
          NEW KEY MODAL
          ====================================================== */}

      <Modal
        open={!!newKey}
        onClose={() =>
          setNewKey(null)
        }
        title="API Key Created"
        footer={
          <Button
            onClick={() =>
              setNewKey(null)
            }
          >
            Done
          </Button>
        }
      >

        {newKey && (

          <div className="space-y-3">

            <p className="text-sm text-gray-600">

              Copy this key now — for security, it won&apos;t
              be shown again. Give it to{' '}

              {
                newKey.client.clientType === 'BNR'
                  ? 'BNR'
                  : 'the credit bureau'
              }

              {' '}to use in the{' '}

              <code className="bg-gray-100 px-1 rounded">
                X-Api-Key
              </code>

              {' '}header.

            </p>


            <div className="bg-gray-900 text-teal-400 font-mono text-xs p-3 rounded-lg break-all select-all">
              {newKey.apiKey}
            </div>


            <Button
              size="sm"
              variant="secondary"
              onClick={() =>
                navigator.clipboard.writeText(
                  newKey.apiKey
                )
              }
            >
              📋 Copy to Clipboard
            </Button>

          </div>

        )}

      </Modal>

    </div>
  );
}