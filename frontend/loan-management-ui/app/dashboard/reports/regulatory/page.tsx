
'use client';

import {
  useEffect,
  useMemo,
  useState,
} from 'react';

import { useAuth } from '@/hooks/useAuth';

import {
  regulatoryApi,

  type ApiClient,
  type BnrSummary,
  type BreakdownRow,
  type ClientType,
  type CreditRecord,
  type ExportFormat,
  type FinancialBreakdownRow,
  type FinancialSummary,
  type RegulatoryPeriod,
  type RegulatoryReportType,
} from '@/services/regulatoryService';

import { PageSpinner } from '@/components/ui/Skeleton';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';


// ============================================================
// CONSTANTS
// ============================================================

const PERIODS: RegulatoryPeriod[] = [
  'DAILY',
  'WEEKLY',
  'MONTHLY',
  'QUARTERLY',
  'YEARLY',
  'CUSTOM',
];


const EXPORT_FORMATS: ExportFormat[] = [
  'xlsx',
  'csv',
  'pdf',
];


const FINANCIAL_EXPORT_FORMATS: ExportFormat[] = [
  'xlsx',
  'csv',
  'pdf',
];


// ============================================================
// FORMATTERS
// ============================================================

function fmt(
  value?: number,
  currency = 'RWF'
): string {

  if (value == null) {
    return '—';
  }

  return (
    new Intl.NumberFormat(
      'en-US',
      {
        maximumFractionDigits: 0,
      }
    ).format(value) +
    ' ' +
    currency
  );
}


function formatDate(
  value?: string
): string {

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

  return date.toLocaleDateString();
}


function formatDateTime(
  value?: string
): string {

  if (!value) {
    return 'Never';
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

  return date.toLocaleString();
}


function getErrorMessage(
  error: unknown,
  fallback: string
): string {

  return regulatoryApi.getErrorMessage(
    error,
    fallback
  );
}


// ============================================================
// MAIN PAGE
// ============================================================

export default function RegulatoryReportsPage() {

  const {
    isAdmin,
    user,
  } = useAuth();


  const canView =
    !!isAdmin ||
    [
      'MANAGER',
      'AUDITOR',
    ].includes(
      user?.role || ''
    );


  const [
    reportType,
    setReportType,
  ] = useState<RegulatoryReportType>(
    'BNR'
  );


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

      {/* ================================================== */}
      {/* HEADER */}
      {/* ================================================== */}

      <div>

        <h1 className="text-xl font-bold text-gray-900">
          Regulatory & Financial Reporting
        </h1>

        <p className="text-sm text-gray-500 mt-1">
          Generate BNR regulatory reports, financial
          statements and credit bureau data exports.
        </p>

      </div>


      {/* ================================================== */}
      {/* REPORT TYPE SELECTOR */}
      {/* ================================================== */}

      <div className="bg-white rounded-xl border border-gray-200 p-4">

        <div className="flex items-center gap-4 flex-wrap">

          <div>

            <label className="block text-xs font-semibold text-gray-500 mb-1">
              Report Type
            </label>

            <select
              value={reportType}
              onChange={(event) =>
                setReportType(
                  event.target
                    .value as RegulatoryReportType
                )
              }
              className="border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white min-w-[220px]"
            >

              <option value="BNR">
                BNR Regulatory Report
              </option>

              <option value="FINANCIAL">
                Financial Report
              </option>

              <option value="CREDIT_BUREAU">
                Credit Bureau Report
              </option>

            </select>

          </div>


          <div className="text-xs text-gray-400 max-w-xl">

            {reportType === 'BNR' && (
              <>
                Generate regulatory portfolio
                reports including loan, PAR, NPL,
                gender, branch and loan-type data.
              </>
            )}

            {reportType === 'FINANCIAL' && (
              <>
                Generate financial reports covering
                income, expenses, profit and cash flow.
              </>
            )}

            {reportType === 'CREDIT_BUREAU' && (
              <>
                Generate restricted borrower-level
                credit bureau information.
              </>
            )}

          </div>

        </div>

      </div>


      {/* ================================================== */}
      {/* REPORT */}
      {/* ================================================== */}

      {reportType === 'BNR' && (
        <BnrReport />
      )}


      {reportType === 'FINANCIAL' && (
        <FinancialReport />
      )}


      {reportType === 'CREDIT_BUREAU' && (
        <CreditBureauReport />
      )}


      {/* ================================================== */}
      {/* API ACCESS */}
      {/* ================================================== */}

      {isAdmin && (
        <ApiKeysSection />
      )}

    </div>
  );
}


// ============================================================
// BNR REPORT
// ============================================================

function BnrReport() {

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
    loanTypes,
    setLoanTypes,
  ] = useState<BreakdownRow[]>(
    []
  );


  const [
    branches,
    setBranches,
  ] = useState<BreakdownRow[]>(
    []
  );


  const [
    gender,
    setGender,
  ] = useState<BreakdownRow[]>(
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


  const params = useMemo(
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


  const load = async () => {

    if (
      period === 'CUSTOM' &&
      (!from || !to)
    ) {

      setLoadError(
        'Please select both a start and end date.'
      );

      return;
    }


    setLoading(true);
    setLoadError('');


    try {

      const [
        summaryResponse,
        loanTypeResponse,
        branchResponse,
        genderResponse,
      ] = await Promise.all([

        regulatoryApi.bnrSummary(
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
        summaryResponse
      );


      setLoanTypes(
        loanTypeResponse || []
      );


      setBranches(
        branchResponse || []
      );


      setGender(
        genderResponse || []
      );

    } catch (error) {

      console.error(
        'Failed to load BNR report:',
        error
      );


      setLoadError(
        getErrorMessage(
          error,
          'Could not load BNR reports.'
        )
      );

    } finally {

      setLoading(false);

    }
  };


  useEffect(() => {

    if (
      period !== 'CUSTOM'
    ) {

      void load();

    }

    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [period]);


  const doExport = async (
    format: ExportFormat
  ) => {

    if (
      period === 'CUSTOM' &&
      (!from || !to)
    ) {

      alert(
        'Please select both From and To dates.'
      );

      return;
    }


    setExporting(format);


    try {

      await regulatoryApi.bnrExport(
        format,
        params
      );

    } catch (error) {

      alert(
        getErrorMessage(
          error,
          'Export failed.'
        )
      );

    } finally {

      setExporting(null);

    }
  };


  const currency =
    summary?.currency || 'RWF';


  return (
    <div className="space-y-6">

      {/* FILTERS */}

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
              className="border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white"
            >

              {PERIODS.map(
                (item) => (

                  <option
                    key={item}
                    value={item}
                  >
                    {item.charAt(0) +
                      item
                        .slice(1)
                        .toLowerCase()}
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


        <div className="flex gap-2 flex-wrap">

          {EXPORT_FORMATS.map(
            (format) => (

              <Button
                key={format}
                size="sm"
                variant="outline"
                loading={
                  exporting === format
                }
                onClick={() =>
                  void doExport(format)
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

        <ErrorPanel
          message={loadError}
          onRetry={() =>
            void load()
          }
        />

      ) : !summary ? (

        <EmptyState
          message="No BNR data available."
        />

      ) : (

        <>

          <div className="bg-white rounded-xl border border-gray-200 p-5">

            <div className="flex items-center justify-between flex-wrap gap-2">

              <div>

                <h2 className="font-semibold text-gray-800 text-sm">
                  Loan Portfolio Summary
                </h2>

                <p className="text-xs text-gray-400 mt-1">
                  Reporting period
                </p>

              </div>


              <span className="text-xs text-gray-400">

                {summary.organizationName || '—'}

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


          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">

            {[
              [
                'Total Loans Issued',
                summary.totalLoansIssued,
              ],

              [
                'Active Loans',
                summary.activeLoans,
              ],

              [
                'Closed Loans',
                summary.closedLoans,
              ],

              [
                'Pending Loans',
                summary.pendingLoans,
              ],

              [
                'Rejected Loans',
                summary.rejectedLoans,
              ],

              [
                'Overdue Loans',
                summary.overdueLoans,
              ],

              [
                'Defaulted / Written-off',
                summary.defaultedLoans,
              ],

            ].map(
              ([label, value]) => (

                <div
                  key={String(label)}
                  className="bg-white rounded-xl border border-gray-200 p-4"
                >

                  <p className="text-gray-500 text-xs uppercase tracking-wide">
                    {label}
                  </p>

                  <p className="text-xl font-bold mt-1 text-gray-900">
                    {value ?? 0}
                  </p>

                </div>

              )
            )}

          </div>


          <div className="grid grid-cols-2 md:grid-cols-3 gap-4">

            {[
              {
                label:
                  'Total Principal Disbursed',

                value:
                  fmt(
                    summary.totalPrincipalDisbursed,
                    currency
                  ),

                color:
                  'text-indigo-600',
              },

              {
                label:
                  'Outstanding Principal',

                value:
                  fmt(
                    summary.outstandingPrincipal,
                    currency
                  ),

                color:
                  'text-blue-600',
              },

              {
                label:
                  'Total Interest Collected',

                value:
                  fmt(
                    summary.totalInterestCollected,
                    currency
                  ),

                color:
                  'text-green-600',
              },

              {
                label:
                  'Interest Accrued',

                value:
                  fmt(
                    summary.interestAccruedUnpaid,
                    currency
                  ),

                color:
                  'text-orange-600',
              },

              {
                label:
                  'Portfolio at Risk',

                value:
                  `${fmt(
                    summary.parAmount,
                    currency
                  )} (${(
                    (summary.parRatio || 0) *
                    100
                  ).toFixed(1)}%)`,

                color:
                  'text-amber-600',
              },

              {
                label:
                  'NPL >90 Days',

                value:
                  `${fmt(
                    summary.nplAmount,
                    currency
                  )} (${(
                    (summary.nplRatio || 0) *
                    100
                  ).toFixed(1)}%)`,

                color:
                  'text-red-600',
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
                    className={`text-lg font-bold mt-1 ${card.color}`}
                  >
                    {card.value}
                  </p>

                </div>

              )
            )}

          </div>


          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">

            <div className="bg-white rounded-xl border border-gray-200 p-5">

              <h3 className="font-semibold text-gray-800 text-sm mb-3">
                Financial Inclusion — Gender
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


// ============================================================
// FINANCIAL REPORT
// ============================================================

function FinancialReport() {

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
  ] = useState<FinancialSummary | null>(
    null
  );


  const [
    income,
    setIncome,
  ] = useState<FinancialBreakdownRow[]>(
    []
  );


  const [
    expenses,
    setExpenses,
  ] = useState<FinancialBreakdownRow[]>(
    []
  );


  const [
    cashFlow,
    setCashFlow,
  ] = useState<FinancialBreakdownRow[]>(
    []
  );


  const [
    loading,
    setLoading,
  ] = useState(false);


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


  const params = useMemo(
    () => ({
      from,
      to,
    }),
    [
      from,
      to,
    ]
  );


  const load = async () => {

    if (!from || !to) {

      setLoadError(
        'Please select both a start and end date.'
      );

      return;
    }


    if (from > to) {

      setLoadError(
        'The start date cannot be after the end date.'
      );

      return;
    }


    setLoading(true);
    setLoadError('');


    try {

      const [
        summaryResponse,
        incomeResponse,
        expenseResponse,
        cashFlowResponse,
      ] = await Promise.all([

        regulatoryApi.financialSummary(
          params
        ),

        regulatoryApi.financialIncome(
          params
        ),

        regulatoryApi.financialExpenses(
          params
        ),

        regulatoryApi.financialCashFlow(
          params
        ),

      ]);


      setSummary(
        summaryResponse
      );


      setIncome(
        incomeResponse || []
      );


      setExpenses(
        expenseResponse || []
      );


      setCashFlow(
        cashFlowResponse || []
      );

    } catch (error) {

      console.error(
        'Failed to load financial report:',
        error
      );


      setLoadError(
        getErrorMessage(
          error,
          'Could not load financial report.'
        )
      );

    } finally {

      setLoading(false);

    }
  };


  const doExport = async (
    format: ExportFormat
  ) => {

    if (!from || !to) {

      alert(
        'Please select both From and To dates.'
      );

      return;
    }


    if (from > to) {

      alert(
        'The start date cannot be after the end date.'
      );

      return;
    }


    setExporting(format);


    try {

      await regulatoryApi.financialExport(
        format,
        params
      );

    } catch (error) {

      alert(
        getErrorMessage(
          error,
          'Financial report export failed.'
        )
      );

    } finally {

      setExporting(null);

    }
  };


  const currency =
    summary?.currency || 'RWF';


  return (
    <div className="space-y-6">

      {/* ================================================== */}
      {/* FILTERS */}
      {/* ================================================== */}

      <div className="bg-white rounded-xl border border-gray-200 p-5">

        <div className="flex items-end justify-between gap-4 flex-wrap">

          <div className="flex items-end gap-3 flex-wrap">

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
              Generate Report
            </Button>

          </div>


          {/* ================================================== */}
          {/* EXPORT FORMAT */}
          {/* ================================================== */}

          <div>

            <label className="block text-xs font-semibold text-gray-500 mb-1">
              Export Format
            </label>

            <div className="flex items-center gap-2">

              <select
                defaultValue="pdf"
                id="financial-export-format"
                className="border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white"
              >

                {FINANCIAL_EXPORT_FORMATS.map(
                  (format) => (

                    <option
                      key={format}
                      value={format}
                    >
                      {format === 'xlsx'
                        ? 'Excel (XLSX)'
                        : format.toUpperCase()}
                    </option>

                  )
                )}

              </select>


              <Button
                size="sm"
                variant="outline"
                loading={!!exporting}
                onClick={() => {

                  const element =
                    document.getElementById(
                      'financial-export-format'
                    ) as HTMLSelectElement | null;

                  const format =
                    (element?.value ||
                      'pdf') as ExportFormat;

                  void doExport(format);

                }}
              >
                ⬇ Export
              </Button>

            </div>

          </div>

        </div>


        <p className="text-xs text-gray-400 mt-3">
          Select the reporting period, generate the
          report, then choose PDF, CSV or Excel for export.
        </p>

      </div>


      {/* ================================================== */}
      {/* EMPTY */}
      {/* ================================================== */}

      {!loading &&
        !summary &&
        !loadError && (

          <div className="bg-blue-50 border border-blue-200 rounded-xl p-5">

            <p className="text-sm text-blue-700">
              Select a From and To date, then click
              Generate Report.
            </p>

          </div>

        )}


      {/* ================================================== */}
      {/* LOADING */}
      {/* ================================================== */}

      {loading && (
        <PageSpinner />
      )}


      {/* ================================================== */}
      {/* ERROR */}
      {/* ================================================== */}

      {!loading && loadError && (

        <ErrorPanel
          message={loadError}
          onRetry={() =>
            void load()
          }
        />

      )}


      {/* ================================================== */}
      {/* REPORT */}
      {/* ================================================== */}

      {!loading &&
        !loadError &&
        summary && (

          <>

            {/* REPORT HEADER */}

            <div className="bg-white rounded-xl border border-gray-200 p-5">

              <div className="flex items-center justify-between gap-4 flex-wrap">

                <div>

                  <h2 className="font-semibold text-gray-900">
                    Financial Report
                  </h2>

                  <p className="text-xs text-gray-400 mt-1">
                    {summary.organizationName || '—'}
                  </p>

                </div>


                <div className="text-xs text-gray-500">

                  {summary.periodStart || from}
                  {' → '}
                  {summary.periodEnd || to}

                </div>

              </div>

            </div>


            {/* ================================================== */}
            {/* PROFIT & LOSS */}
            {/* ================================================== */}

            <div>

              <h2 className="font-semibold text-gray-800 text-sm mb-3">
                Income Statement
              </h2>


              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">

                <FinancialCard
                  label="Total Income"
                  value={fmt(
                    summary.totalIncome,
                    currency
                  )}
                  className="text-green-600"
                />


                <FinancialCard
                  label="Total Expenses"
                  value={fmt(
                    summary.totalExpenses,
                    currency
                  )}
                  className="text-red-600"
                />


                <FinancialCard
                  label="Net Profit"
                  value={fmt(
                    summary.netProfit,
                    currency
                  )}
                  className={
                    (summary.netProfit || 0) >= 0
                      ? 'text-blue-600'
                      : 'text-red-600'
                  }
                />

              </div>

            </div>


            {/* ================================================== */}
            {/* CASH FLOW */}
            {/* ================================================== */}

            <div>

              <h2 className="font-semibold text-gray-800 text-sm mb-3">
                Cash Flow
              </h2>


              <div className="grid grid-cols-2 md:grid-cols-4 gap-4">

                <FinancialCard
                  label="Opening Cash Balance"
                  value={fmt(
                    summary.openingCashBalance,
                    currency
                  )}
                />


                <FinancialCard
                  label="Cash Inflows"
                  value={fmt(
                    summary.cashInflows,
                    currency
                  )}
                  className="text-green-600"
                />


                <FinancialCard
                  label="Cash Outflows"
                  value={fmt(
                    summary.cashOutflows,
                    currency
                  )}
                  className="text-red-600"
                />


                <FinancialCard
                  label="Net Cash Flow"
                  value={fmt(
                    summary.netCashFlow,
                    currency
                  )}
                  className={
                    (summary.netCashFlow || 0) >= 0
                      ? 'text-blue-600'
                      : 'text-red-600'
                  }
                />

              </div>

            </div>


            {/* ================================================== */}
            {/* CLOSING CASH */}
            {/* ================================================== */}

            <div className="bg-white rounded-xl border border-gray-200 p-5">

              <div className="flex items-center justify-between">

                <span className="text-sm font-semibold text-gray-700">
                  Closing Cash Balance
                </span>

                <span className="text-xl font-bold text-gray-900">
                  {fmt(
                    summary.closingCashBalance,
                    currency
                  )}
                </span>

              </div>

            </div>


            {/* ================================================== */}
            {/* INCOME BREAKDOWN */}
            {/* ================================================== */}

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">

              <FinancialBreakdownTable
                title="Income"
                rows={income}
                currency={currency}
              />


              <FinancialBreakdownTable
                title="Expenses"
                rows={expenses}
                currency={currency}
              />

            </div>


            {/* ================================================== */}
            {/* CASH FLOW BREAKDOWN */}
            {/* ================================================== */}

            <FinancialBreakdownTable
              title="Cash Flow"
              rows={cashFlow}
              currency={currency}
            />

          </>

        )}

    </div>
  );
}


// ============================================================
// FINANCIAL CARD
// ============================================================

function FinancialCard({
  label,
  value,
  className = 'text-gray-900',
}: {
  label: string;
  value: string;
  className?: string;
}) {

  return (
    <div className="bg-white rounded-xl border border-gray-200 p-5">

      <p className="text-gray-500 text-xs uppercase tracking-wide">
        {label}
      </p>

      <p
        className={`text-xl font-bold mt-1 ${className}`}
      >
        {value}
      </p>

    </div>
  );
}


// ============================================================
// FINANCIAL BREAKDOWN TABLE
// ============================================================

function FinancialBreakdownTable({
  title,
  rows,
  currency,
}: {
  title: string;

  rows: FinancialBreakdownRow[];

  currency: string;
}) {

  return (
    <div className="bg-white rounded-xl border border-gray-200 p-5">

      <h3 className="font-semibold text-gray-800 text-sm mb-3">
        {title}
      </h3>


      {!rows.length ? (

        <p className="text-sm text-gray-400">
          No data available for this period.
        </p>

      ) : (

        <div className="overflow-x-auto">

          <table className="w-full text-sm">

            <thead>

              <tr className="text-left text-gray-500 text-xs uppercase">

                <th className="pb-2">
                  Category
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

                    <td className="py-2 text-right font-medium text-gray-800">
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

        </div>

      )}

    </div>
  );
}


// ============================================================
// CREDIT BUREAU REPORT
// ============================================================

function CreditBureauReport() {

  const [
    records,
    setRecords,
  ] = useState<CreditRecord[]>(
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

      const response =
        await regulatoryApi.creditBureauPreview({
          from:
            from || undefined,

          to:
            to || undefined,
        });


      setRecords(
        response || []
      );

    } catch (error) {

      console.error(
        'Failed to load credit bureau:',
        error
      );


      setLoadError(
        getErrorMessage(
          error,
          'Could not load credit bureau records.'
        )
      );

    } finally {

      setLoading(false);

    }
  };


  useEffect(() => {

    void load();

    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);


  const doExport = async (
    format: ExportFormat
  ) => {

    setExporting(format);


    try {

      await regulatoryApi.creditBureauExport(
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
        getErrorMessage(
          error,
          'Export failed.'
        )
      );

    } finally {

      setExporting(null);

    }
  };


  return (
    <div className="space-y-4">

      <div className="bg-amber-50 border border-amber-200 rounded-xl p-4">

        <p className="text-xs text-amber-800">

          <strong>
            Restricted information:
          </strong>{' '}

          This screen contains borrower-level personal
          and credit information. Views and exports
          should be audited by the backend.

        </p>

      </div>


      <div className="bg-white rounded-xl border border-gray-200 p-5">

        <div className="flex items-end justify-between gap-4 flex-wrap">

          <div className="flex items-end gap-3 flex-wrap">

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


          <ExportSelector
            formats={EXPORT_FORMATS}
            exporting={exporting}
            onExport={doExport}
          />

        </div>

      </div>


      {loading ? (

        <PageSpinner />

      ) : loadError ? (

        <ErrorPanel
          message={loadError}
          onRetry={() =>
            void load()
          }
        />

      ) : (

        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">

          <div className="overflow-x-auto">

            <table className="w-full text-sm">

              <thead>

                <tr className="text-left text-gray-500 text-xs uppercase bg-gray-50">

                  <th className="px-4 py-3">
                    Borrower
                  </th>

                  <th className="px-4 py-3">
                    Loan #
                  </th>

                  <th className="px-4 py-3">
                    Type
                  </th>

                  <th className="px-4 py-3">
                    Status
                  </th>

                  <th className="px-4 py-3 text-right">
                    Amount
                  </th>

                  <th className="px-4 py-3 text-right">
                    Outstanding
                  </th>

                  <th className="px-4 py-3 text-right">
                    Days Past Due
                  </th>

                  <th className="px-4 py-3">
                    Branch
                  </th>

                </tr>

              </thead>


              <tbody>

                {records
                  .slice(0, 200)
                  .map(
                    (record, index) => (

                      <tr
                        key={
                          record.borrowerId ??
                          record.loanNumber ??
                          index
                        }
                        className="border-t border-gray-50 hover:bg-gray-50"
                      >

                        <td className="px-4 py-3 text-gray-800">
                          {record.fullName || '—'}
                        </td>

                        <td className="px-4 py-3 text-gray-600">
                          {record.loanNumber || '—'}
                        </td>

                        <td className="px-4 py-3 text-gray-600">
                          {record.loanType || '—'}
                        </td>

                        <td className="px-4 py-3">

                          <span className="text-xs px-2 py-0.5 rounded-full bg-gray-100 text-gray-700">
                            {record.loanStatus || '—'}
                          </span>

                        </td>

                        <td className="px-4 py-3 text-right">
                          {fmt(
                            record.loanAmount
                          )}
                        </td>

                        <td className="px-4 py-3 text-right">
                          {fmt(
                            record.outstandingBalance
                          )}
                        </td>

                        <td className="px-4 py-3 text-right">
                          {record.daysPastDue ?? 0}
                        </td>

                        <td className="px-4 py-3 text-gray-500">
                          {record.branchName || '—'}
                        </td>

                      </tr>

                    )
                  )}

              </tbody>

            </table>

          </div>


          {records.length === 0 && (
            <EmptyState
              message="No records for this period."
            />
          )}


          {records.length > 200 && (

            <p className="text-center text-xs text-gray-400 py-3 border-t">

              Showing first 200 of{' '}
              {records.length}.

            </p>

          )}

        </div>

      )}

    </div>
  );
}


// ============================================================
// API ACCESS
// ============================================================

function ApiKeysSection() {

  const [
    clients,
    setClients,
  ] = useState<ApiClient[]>(
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
  ] = useState<{
    apiKey: string;
    client: ApiClient;
  } | null>(null);


  const [
    form,
    setForm,
  ] = useState<{
    name: string;
    clientType: ClientType;
    contactEmail: string;
    description: string;
    expiresAt: string;
  }>({
    name: '',
    clientType: 'BNR',
    contactEmail: '',
    description: '',
    expiresAt: '',
  });


  const [
    saving,
    setSaving,
  ] = useState(false);


  const [
    revokingId,
    setRevokingId,
  ] = useState<number | null>(
    null
  );


  const load = async () => {

    setLoading(true);
    setLoadError('');


    try {

      const response =
        await regulatoryApi.listApiClients();


      setClients(
        response || []
      );

    } catch (error) {

      console.error(
        'Failed to load API clients:',
        error
      );


      setLoadError(
        getErrorMessage(
          error,
          'Could not load API keys.'
        )
      );

    } finally {

      setLoading(false);

    }
  };


  useEffect(() => {

    void load();

  }, []);


  const resetForm = () => {

    setForm({
      name: '',
      clientType: 'BNR',
      contactEmail: '',
      description: '',
      expiresAt: '',
    });
  };


  const create = async () => {

    const name =
      form.name.trim();


    if (!name) {

      alert(
        'Integration name is required.'
      );

      return;
    }


    setSaving(true);


    try {

      const response =
        await regulatoryApi.createApiClient({
          name,

          clientType:
            form.clientType,

          contactEmail:
            form.contactEmail.trim() ||
            undefined,

          description:
            form.description.trim() ||
            undefined,

          expiresAt:
            form.expiresAt
              ? new Date(
                  `${form.expiresAt}T23:59:59`
                ).toISOString()
              : null,
        });


      if (
        !response?.apiKey ||
        !response?.client
      ) {

        throw new Error(
          'The server created the API client but did not return the API key.'
        );
      }


      setNewKey({
        apiKey:
          response.apiKey,

        client:
          response.client,
      });


      setShowCreate(false);

      resetForm();

      await load();

    } catch (error) {

      alert(
        getErrorMessage(
          error,
          'Failed to create API key.'
        )
      );

    } finally {

      setSaving(false);

    }
  };


  const revoke = async (
    id: number
  ) => {

    const reason =
      window.prompt(
        'Optional reason for revoking this API key:',
        ''
      );


    if (reason === null) {
      return;
    }


    const confirmed =
      window.confirm(
        'Revoke this API key? Any system using it will immediately lose access.'
      );


    if (!confirmed) {
      return;
    }


    setRevokingId(id);


    try {

      await regulatoryApi.revokeApiClient(
        id,
        reason.trim() || undefined
      );


      await load();

    } catch (error) {

      alert(
        getErrorMessage(
          error,
          'Failed to revoke API key.'
        )
      );

    } finally {

      setRevokingId(null);

    }
  };


  return (
    <div className="space-y-4">

      <div className="bg-white rounded-xl border border-gray-200 p-5">

        <div className="flex items-start justify-between gap-4 flex-wrap">

          <div>

            <h2 className="font-semibold text-gray-900">
              External API Access
            </h2>

            <p className="text-sm text-gray-500 mt-1 max-w-2xl">
              Issue and revoke API credentials used
              by authorized regulatory and credit-bureau
              integrations.
            </p>

          </div>


          <Button
            onClick={() =>
              setShowCreate(true)
            }
          >
            + New API Key
          </Button>

        </div>

      </div>


      {loading ? (

        <PageSpinner />

      ) : loadError ? (

        <ErrorPanel
          message={loadError}
          onRetry={() =>
            void load()
          }
        />

      ) : (

        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">

          <div className="overflow-x-auto">

            <table className="w-full text-sm">

              <thead>

                <tr className="text-left text-gray-500 text-xs uppercase bg-gray-50">

                  <th className="px-4 py-3">
                    Name
                  </th>

                  <th className="px-4 py-3">
                    Type
                  </th>

                  <th className="px-4 py-3">
                    Key Prefix
                  </th>

                  <th className="px-4 py-3">
                    Status
                  </th>

                  <th className="px-4 py-3">
                    Expires
                  </th>

                  <th className="px-4 py-3">
                    Last Used
                  </th>

                  <th className="px-4 py-3">
                  </th>

                </tr>

              </thead>


              <tbody>

                {clients.map(
                  (client) => {

                    const revoked =
                      !!client.revokedAt ||
                      !client.active;


                    const expired =
                      !!client.expiresAt &&
                      new Date(
                        client.expiresAt
                      ).getTime() <=
                        Date.now();


                    return (
                      <tr
                        key={client.id}
                        className="border-t border-gray-50 hover:bg-gray-50"
                      >

                        <td className="px-4 py-3">

                          <p className="font-medium text-gray-800">
                            {client.name}
                          </p>

                          {client.contactEmail && (

                            <p className="text-xs text-gray-400 mt-0.5">
                              {client.contactEmail}
                            </p>

                          )}

                        </td>


                        <td className="px-4 py-3">

                          <span className="text-xs px-2 py-0.5 rounded-full bg-blue-100 text-blue-700">
                            {client.clientType}
                          </span>

                        </td>


                        <td className="px-4 py-3 font-mono text-xs text-gray-500">
                          {client.keyPrefix}…
                        </td>


                        <td className="px-4 py-3">

                          {revoked ? (

                            <span className="text-xs px-2 py-0.5 rounded-full bg-red-100 text-red-700">
                              Revoked
                            </span>

                          ) : expired ? (

                            <span className="text-xs px-2 py-0.5 rounded-full bg-orange-100 text-orange-700">
                              Expired
                            </span>

                          ) : (

                            <span className="text-xs px-2 py-0.5 rounded-full bg-green-100 text-green-700">
                              Active
                            </span>

                          )}

                        </td>


                        <td className="px-4 py-3 text-xs text-gray-500">
                          {formatDate(
                            client.expiresAt
                          )}
                        </td>


                        <td className="px-4 py-3 text-xs text-gray-500">
                          {formatDateTime(
                            client.lastUsedAt
                          )}
                        </td>


                        <td className="px-4 py-3 text-right">

                          {!revoked &&
                            !expired && (

                              <Button
                                size="xs"
                                variant="danger"
                                loading={
                                  revokingId ===
                                  client.id
                                }
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
                    );
                  }
                )}

              </tbody>

            </table>

          </div>


          {clients.length === 0 && (
            <EmptyState
              message="No API keys issued yet."
            />
          )}

        </div>

      )}


      {/* CREATE MODAL */}

      <Modal
        open={showCreate}
        onClose={() => {

          if (!saving) {
            setShowCreate(false);
          }

        }}
        title="Issue New API Key"
        footer={
          <>

            <Button
              variant="secondary"
              onClick={() =>
                setShowCreate(false)
              }
              disabled={saving}
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
              value={form.clientType}
              onChange={(event) =>
                setForm(
                  (current) => ({
                    ...current,

                    clientType:
                      event.target
                        .value as ClientType,
                  })
                )
              }
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white"
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
              Contact Email
            </label>

            <input
              type="email"
              value={form.contactEmail}
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
              Expiration Date
            </label>

            <input
              type="date"
              value={form.expiresAt}
              onChange={(event) =>
                setForm(
                  (current) => ({
                    ...current,
                    expiresAt:
                      event.target.value,
                  })
                )
              }
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm"
            />

          </div>


          <div>

            <label className="block text-xs font-semibold text-gray-500 mb-1">
              Description
            </label>

            <textarea
              value={form.description}
              onChange={(event) =>
                setForm(
                  (current) => ({
                    ...current,
                    description:
                      event.target.value,
                  })
                )
              }
              rows={3}
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm"
            />

          </div>


          <div className="bg-amber-50 border border-amber-200 rounded-lg p-3">

            <p className="text-xs text-amber-800">
              The complete API key will be displayed
              only once after creation. Store it securely.
            </p>

          </div>

        </div>

      </Modal>


      {/* CREATED KEY */}

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

          <div className="space-y-4">

            <div className="bg-green-50 border border-green-200 rounded-lg p-3">

              <p className="text-sm font-semibold text-green-800">
                API key created successfully.
              </p>

              <p className="text-xs text-green-700 mt-1">
                This is the only time the complete key
                will be displayed.
              </p>

            </div>


            <div>

              <p className="text-sm text-gray-600">

                Integration:

                <strong className="ml-1">
                  {newKey.client.name}
                </strong>

              </p>

            </div>


            <div className="bg-gray-900 rounded-lg p-4">

              <p className="text-xs text-gray-400 mb-2">
                API Key
              </p>

              <code className="text-teal-400 font-mono text-xs break-all select-all">
                {newKey.apiKey}
              </code>

            </div>


            <Button
              size="sm"
              variant="secondary"
              onClick={async () => {

                try {

                  await navigator.clipboard.writeText(
                    newKey.apiKey
                  );

                  alert(
                    'API key copied to clipboard.'
                  );

                } catch {

                  alert(
                    'Could not access clipboard.'
                  );

                }

              }}
            >
              📋 Copy to Clipboard
            </Button>

          </div>

        )}

      </Modal>

    </div>
  );
}


// ============================================================
// EXPORT SELECTOR
// ============================================================

function ExportSelector({
  formats,
  exporting,
  onExport,
}: {
  formats: ExportFormat[];

  exporting:
    ExportFormat | null;

  onExport:
    (format: ExportFormat) => Promise<void>;
}) {

  const [
    format,
    setFormat,
  ] = useState<ExportFormat>(
    'pdf'
  );


  return (
    <div>

      <label className="block text-xs font-semibold text-gray-500 mb-1">
        Export Format
      </label>

      <div className="flex items-center gap-2">

        <select
          value={format}
          onChange={(event) =>
            setFormat(
              event.target
                .value as ExportFormat
            )
          }
          className="border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white"
        >

          {formats.map(
            (item) => (

              <option
                key={item}
                value={item}
              >
                {item === 'xlsx'
                  ? 'Excel (XLSX)'
                  : item.toUpperCase()}
              </option>

            )
          )}

        </select>


        <Button
          size="sm"
          variant="outline"
          loading={!!exporting}
          onClick={() =>
            void onExport(format)
          }
        >
          ⬇ Export
        </Button>

      </div>

    </div>
  );
}


// ============================================================
// BREAKDOWN TABLE
// ============================================================

function BreakdownTable({
  rows,
  currency,
}: {
  rows: BreakdownRow[];

  currency: string;
}) {

  if (!rows.length) {

    return (
      <p className="text-sm text-gray-400">
        No data for this period.
      </p>
    );
  }


  return (
    <div className="overflow-x-auto">

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
            (row) => (

              <tr
                key={row.label}
                className="border-t border-gray-50"
              >

                <td className="py-2 text-gray-700">
                  {row.label}
                </td>

                <td className="py-2 text-right text-gray-800 font-medium">
                  {row.count ?? 0}
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

    </div>
  );
}


// ============================================================
// ERROR
// ============================================================

function ErrorPanel({
  message,
  onRetry,
}: {
  message: string;

  onRetry: () => void;
}) {

  return (
    <div className="bg-red-50 border border-red-200 rounded-xl p-5 text-center">

      <p className="text-red-700 text-sm font-semibold mb-3">
        {message}
      </p>

      <Button
        size="sm"
        variant="secondary"
        onClick={onRetry}
      >
        Try Again
      </Button>

    </div>
  );
}


// ============================================================
// EMPTY
// ============================================================

function EmptyState({
  message,
}: {
  message: string;
}) {

  return (
    <div className="text-center py-8">

      <p className="text-sm text-gray-400">
        {message}
      </p>

    </div>
  );
}
