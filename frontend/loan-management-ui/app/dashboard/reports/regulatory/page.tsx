'use client';

import React, {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';

import {
  regulatoryApi,
  type BnrReportParams,
  type BnrSummary,
  type BreakdownRow,
  type ExportFormat,
  type RegulatoryPeriod,
} from '@/services/regulatoryService';


// ============================================================
// TYPES
// ============================================================

type DownloadingFormat =
  | ExportFormat
  | null;


// ============================================================
// RESPONSE NORMALIZATION
// ============================================================

/**
 * The backend returns:
 *
 * ApiResponse<List<BnrBreakdownRow>>
 *
 * The shared API client normally unwraps ApiResponse.data.
 * This extra normalization protects the page when a response
 * arrives as:
 *
 * 1. [...]
 * 2. { data: [...] }
 * 3. { rows: [...] }
 * 4. { items: [...] }
 * 5. { breakdown: [...] }
 * 6. { content: [...] }
 *
 * The page must NEVER pass a non-array value to .map().
 */
function normalizeBreakdownRows(
  value: unknown
): BreakdownRow[] {

  if (Array.isArray(value)) {
    return value as BreakdownRow[];
  }

  if (
    value &&
    typeof value === 'object'
  ) {

    const object =
      value as Record<string, unknown>;

    const candidates = [
      object.data,
      object.rows,
      object.items,
      object.breakdown,
      object.content,
      object.results,
    ];

    for (
      const candidate of candidates
    ) {

      if (Array.isArray(candidate)) {

        return candidate as BreakdownRow[];
      }

      if (
        candidate &&
        typeof candidate === 'object'
      ) {

        const nested =
          candidate as Record<string, unknown>;

        const nestedCandidates = [
          nested.data,
          nested.rows,
          nested.items,
          nested.breakdown,
          nested.content,
          nested.results,
        ];

        for (
          const nestedCandidate
          of nestedCandidates
        ) {

          if (
            Array.isArray(
              nestedCandidate
            )
          ) {

            return nestedCandidate as BreakdownRow[];
          }
        }
      }
    }
  }

  return [];
}


// ============================================================
// SUMMARY NORMALIZATION
// ============================================================

function normalizeSummary(
  value: unknown
): BnrSummary | null {

  if (
    !value ||
    typeof value !== 'object'
  ) {

    return null;
  }

  const object =
    value as Record<string, unknown>;

  /*
   * Normal API response:
   *
   * {
   *   organizationName: ...,
   *   totalLoans: ...
   * }
   */

  if (
    'organizationName' in object ||
    'totalLoans' in object ||
    'activeLoans' in object ||
    'outstandingPrincipal' in object
  ) {

    return value as BnrSummary;
  }

  /*
   * Defensive support for wrapped responses.
   */

  const candidates = [
    object.data,
    object.result,
    object.summary,
    object.report,
  ];

  for (
    const candidate of candidates
  ) {

    if (
      candidate &&
      typeof candidate === 'object'
    ) {

      return candidate as BnrSummary;
    }
  }

  return null;
}


// ============================================================
// PAGE
// ============================================================

export default function BnrReportPage() {

  // ==========================================================
  // FILTERS
  // ==========================================================

  const [period, setPeriod] =
    useState<RegulatoryPeriod>(
      'MONTHLY'
    );

  const [from, setFrom] =
    useState<string>('');

  const [to, setTo] =
    useState<string>('');


  // ==========================================================
  // DATA
  // ==========================================================

  const [summary, setSummary] =
    useState<BnrSummary | null>(
      null
    );

  const [
    loanTypeBreakdown,
    setLoanTypeBreakdown,
  ] = useState<BreakdownRow[]>(
    []
  );

  const [
    branchBreakdown,
    setBranchBreakdown,
  ] = useState<BreakdownRow[]>(
    []
  );

  const [
    genderBreakdown,
    setGenderBreakdown,
  ] = useState<BreakdownRow[]>(
    []
  );


  // ==========================================================
  // UI STATE
  // ==========================================================

  const [loading, setLoading] =
    useState<boolean>(true);

  const [
    downloadingFormat,
    setDownloadingFormat,
  ] = useState<DownloadingFormat>(
    null
  );

  const [error, setError] =
    useState<string | null>(null);


  // ==========================================================
  // REPORT PARAMETERS
  // ==========================================================

  const reportParams =
    useMemo<BnrReportParams>(
      () => {

        const params: BnrReportParams = {
          period,
        };

        if (
          period === 'CUSTOM'
        ) {

          if (from) {
            params.from = from;
          }

          if (to) {
            params.to = to;
          }
        }

        return params;

      },
      [
        period,
        from,
        to,
      ]
    );


  // ==========================================================
  // VALIDATE FILTERS
  // ==========================================================

  const validateFilters =
    useCallback(
      (): string | null => {

        if (
          period !== 'CUSTOM'
        ) {

          return null;
        }

        if (!from) {

          return (
            'Please select a start date.'
          );
        }

        if (!to) {

          return (
            'Please select an end date.'
          );
        }

        if (from > to) {

          return (
            'The start date cannot be after the end date.'
          );
        }

        return null;
      },
      [
        period,
        from,
        to,
      ]
    );


  // ==========================================================
  // LOAD BNR REPORT
  // ==========================================================

  const loadReport =
    useCallback(
      async (): Promise<void> => {

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
            loanTypeResult,
            branchResult,
            genderResult,
          ] = await Promise.all([

            regulatoryApi.bnrSummary(
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


          // ==================================================
          // IMPORTANT
          // ==================================================
          //
          // NEVER directly assign the breakdown response to
          // array state.
          //
          // The backend returns ApiResponse<List<...>>.
          // normalizeBreakdownRows guarantees that React
          // receives an actual array.
          //
          // ==================================================

          setSummary(
            normalizeSummary(
              summaryResult
            )
          );

          setLoanTypeBreakdown(
            normalizeBreakdownRows(
              loanTypeResult
            )
          );

          setBranchBreakdown(
            normalizeBreakdownRows(
              branchResult
            )
          );

          setGenderBreakdown(
            normalizeBreakdownRows(
              genderResult
            )
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

          /*
           * Never leave stale/non-array data in state after
           * an API failure.
           */

          setLoanTypeBreakdown([]);
          setBranchBreakdown([]);
          setGenderBreakdown([]);

        } finally {

          setLoading(false);
        }

      },
      [
        reportParams,
        validateFilters,
      ]
    );


  // ==========================================================
  // INITIAL LOAD
  // ==========================================================

  useEffect(
    () => {

      void loadReport();

    },
    [
      loadReport,
    ]
  );


  // ==========================================================
  // DOWNLOAD REPORT
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

          /*
           * The regulatory service handles:
           *
           * /regulatory/bnr/export/pdf
           * /regulatory/bnr/export/xlsx
           * /regulatory/bnr/export/csv
           */

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
  // PDF
  // ==========================================================

  const handleDownloadPdf =
    useCallback(
      async (): Promise<void> => {

        await downloadReport(
          'pdf'
        );

      },
      [
        downloadReport,
      ]
    );


  // ==========================================================
  // EXCEL
  // ==========================================================

  const handleDownloadExcel =
    useCallback(
      async (): Promise<void> => {

        await downloadReport(
          'xlsx'
        );

      },
      [
        downloadReport,
      ]
    );


  // ==========================================================
  // CSV
  // ==========================================================

  const handleDownloadCsv =
    useCallback(
      async (): Promise<void> => {

        await downloadReport(
          'csv'
        );

      },
      [
        downloadReport,
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
          'RWF';

        const amount =
          Number(
            value || 0
          );

        return new Intl.NumberFormat(
          'en-RW',
          {
            style: 'currency',
            currency,
            maximumFractionDigits: 2,
          }
        ).format(
          amount
        );

      },
      [
        summary?.currency,
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
          Number(
            value || 0
          )
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
          value || 0
        ).toFixed(2)}%`;

      },
      []
    );


  // ==========================================================
  // LOADING
  // ==========================================================

  if (loading) {

    return (

      <div className="min-h-screen bg-gray-50 p-6">

        <div className="mx-auto max-w-7xl">

          <div className="animate-pulse space-y-6">

            <div
              className="h-10 w-72 rounded bg-gray-200"
            />

            <div
              className="h-24 rounded bg-gray-200"
            />

            <div
              className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4"
            >

              {Array.from(
                { length: 4 }
              ).map(
                (_, index) => (

                  <div
                    key={index}
                    className="h-32 rounded bg-gray-200"
                  />

                )
              )}

            </div>

          </div>

        </div>

      </div>

    );
  }


  // ==========================================================
  // RENDER
  // ==========================================================

  return (

    <div className="min-h-screen bg-gray-50">

      <div
        className="
          mx-auto
          max-w-7xl
          space-y-6
          p-6
        "
      >

        {/* ================================================== */}
        {/* HEADER */}
        {/* ================================================== */}

        <div
          className="
            flex
            flex-col
            gap-4
            md:flex-row
            md:items-center
            md:justify-between
          "
        >

          <div>

            <h1
              className="
                text-2xl
                font-bold
                text-gray-900
              "
            >
              BNR Regulatory Report
            </h1>

            <p
              className="
                mt-1
                text-sm
                text-gray-500
              "
            >
              Regulatory reporting and
              portfolio information.
            </p>

          </div>


          {/* ================================================= */}
          {/* EXPORT BUTTONS */}
          {/* ================================================= */}

          <div
            className="
              flex
              flex-wrap
              gap-2
            "
          >

            <button
              type="button"
              onClick={
                handleDownloadPdf
              }
              disabled={
                downloadingFormat !== null
              }
              className="
                rounded-lg
                bg-red-600
                px-4
                py-2
                text-sm
                font-medium
                text-white
                transition
                hover:bg-red-700
                disabled:cursor-not-allowed
                disabled:opacity-50
              "
            >

              {
                downloadingFormat === 'pdf'
                  ? 'Downloading PDF...'
                  : 'Download PDF'
              }

            </button>


            <button
              type="button"
              onClick={
                handleDownloadExcel
              }
              disabled={
                downloadingFormat !== null
              }
              className="
                rounded-lg
                bg-green-600
                px-4
                py-2
                text-sm
                font-medium
                text-white
                transition
                hover:bg-green-700
                disabled:cursor-not-allowed
                disabled:opacity-50
              "
            >

              {
                downloadingFormat === 'xlsx'
                  ? 'Downloading Excel...'
                  : 'Download Excel'
              }

            </button>


            <button
              type="button"
              onClick={
                handleDownloadCsv
              }
              disabled={
                downloadingFormat !== null
              }
              className="
                rounded-lg
                bg-blue-600
                px-4
                py-2
                text-sm
                font-medium
                text-white
                transition
                hover:bg-blue-700
                disabled:cursor-not-allowed
                disabled:opacity-50
              "
            >

              {
                downloadingFormat === 'csv'
                  ? 'Downloading CSV...'
                  : 'Download CSV'
              }

            </button>

          </div>

        </div>


        {/* ================================================== */}
        {/* ERROR */}
        {/* ================================================== */}

        {error && (

          <div
            className="
              rounded-lg
              border
              border-red-200
              bg-red-50
              p-4
            "
          >

            <div
              className="
                flex
                items-start
                justify-between
                gap-4
              "
            >

              <div>

                <p
                  className="
                    font-semibold
                    text-red-800
                  "
                >
                  Report error
                </p>

                <p
                  className="
                    mt-1
                    text-sm
                    text-red-700
                  "
                >
                  {error}
                </p>

              </div>


              <button
                type="button"
                onClick={
                  () => setError(null)
                }
                className="
                  text-sm
                  font-medium
                  text-red-700
                  hover:text-red-900
                "
              >
                Dismiss
              </button>

            </div>

          </div>

        )}


        {/* ================================================== */}
        {/* REPORT PERIOD */}
        {/* ================================================== */}

        <div
          className="
            rounded-xl
            border
            border-gray-200
            bg-white
            p-5
            shadow-sm
          "
        >

          <div className="mb-4">

            <h2
              className="
                font-semibold
                text-gray-900
              "
            >
              Report period
            </h2>

            <p
              className="
                text-sm
                text-gray-500
              "
            >
              Select the reporting period
              used for the BNR report.
            </p>

          </div>


          <div
            className="
              grid
              grid-cols-1
              gap-4
              md:grid-cols-4
            "
          >

            {/* PERIOD */}

            <div>

              <label
                htmlFor="bnr-period"
                className="
                  mb-1
                  block
                  text-sm
                  font-medium
                  text-gray-700
                "
              >
                Period
              </label>

              <select
                id="bnr-period"
                value={period}
                onChange={(event) =>
                  setPeriod(
                    event.target
                      .value as RegulatoryPeriod
                  )
                }
                className="
                  w-full
                  rounded-lg
                  border
                  border-gray-300
                  px-3
                  py-2
                  text-sm
                  outline-none
                  focus:border-blue-500
                  focus:ring-2
                  focus:ring-blue-100
                "
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

            </div>


            {/* FROM */}

            <div>

              <label
                htmlFor="bnr-from"
                className="
                  mb-1
                  block
                  text-sm
                  font-medium
                  text-gray-700
                "
              >
                From
              </label>

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
                className="
                  w-full
                  rounded-lg
                  border
                  border-gray-300
                  px-3
                  py-2
                  text-sm
                  outline-none
                  disabled:bg-gray-100
                  focus:border-blue-500
                  focus:ring-2
                  focus:ring-blue-100
                "
              />

            </div>


            {/* TO */}

            <div>

              <label
                htmlFor="bnr-to"
                className="
                  mb-1
                  block
                  text-sm
                  font-medium
                  text-gray-700
                "
              >
                To
              </label>

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
                className="
                  w-full
                  rounded-lg
                  border
                  border-gray-300
                  px-3
                  py-2
                  text-sm
                  outline-none
                  disabled:bg-gray-100
                  focus:border-blue-500
                  focus:ring-2
                  focus:ring-blue-100
                "
              />

            </div>


            {/* REFRESH */}

            <div
              className="
                flex
                items-end
              "
            >

              <button
                type="button"
                onClick={
                  () => void loadReport()
                }
                disabled={loading}
                className="
                  w-full
                  rounded-lg
                  bg-gray-900
                  px-5
                  py-2
                  text-sm
                  font-medium
                  text-white
                  hover:bg-gray-800
                  disabled:cursor-not-allowed
                  disabled:opacity-50
                "
              >
                Refresh Report
              </button>

            </div>

          </div>

        </div>


        {/* ================================================== */}
        {/* ORGANIZATION */}
        {/* ================================================== */}

        {summary && (

          <div
            className="
              rounded-xl
              border
              border-gray-200
              bg-white
              p-5
              shadow-sm
            "
          >

            <div
              className="
                flex
                flex-col
                gap-2
                md:flex-row
                md:items-center
                md:justify-between
              "
            >

              <div>

                <h2
                  className="
                    text-lg
                    font-semibold
                    text-gray-900
                  "
                >
                  {
                    summary.organizationName ||
                    'Organization'
                  }
                </h2>

                <p
                  className="
                    text-sm
                    text-gray-500
                  "
                >
                  BNR Institution Code:{' '}

                  {
                    summary.bnrInstitutionCode ||
                    'Not configured'
                  }

                </p>

              </div>


              <div
                className="
                  text-sm
                  text-gray-500
                "
              >

                {
                  summary.periodStart ||
                  '—'
                }

                {' → '}

                {
                  summary.periodEnd ||
                  '—'
                }

              </div>

            </div>

          </div>

        )}


        {/* ================================================== */}
        {/* KPI SUMMARY */}
        {/* ================================================== */}

        <div
          className="
            grid
            grid-cols-1
            gap-4
            sm:grid-cols-2
            lg:grid-cols-4
          "
        >

          <MetricCard
            label="Total Loans Issued"
            value={
              formatNumber(
                summary?.totalLoans
              )
            }
          />

          <MetricCard
            label="Active Loans"
            value={
              formatNumber(
                summary?.activeLoans
              )
            }
          />

          <MetricCard
            label="Principal Disbursed"
            value={
              formatMoney(
                summary?.totalPrincipalDisbursed
              )
            }
          />

          <MetricCard
            label="Outstanding Principal"
            value={
              formatMoney(
                summary?.outstandingPrincipal
              )
            }
          />

          <MetricCard
            label="Interest Collected"
            value={
              formatMoney(
                summary?.totalInterestCollected
              )
            }
          />

          <MetricCard
            label="Overdue Loans"
            value={
              formatNumber(
                summary?.overdueLoans
              )
            }
          />

          <MetricCard
            label="PAR Ratio"
            value={
              formatPercent(
                summary?.parRatio
              )
            }
            secondary={
              formatMoney(
                summary?.parAmount
              )
            }
          />

          <MetricCard
            label="NPL Ratio"
            value={
              formatPercent(
                summary?.nplRatio
              )
            }
            secondary={
              formatMoney(
                summary?.nplAmount
              )
            }
          />

        </div>


        {/* ================================================== */}
        {/* LOAN STATUS */}
        {/* ================================================== */}

        <div
          className="
            rounded-xl
            border
            border-gray-200
            bg-white
            p-5
            shadow-sm
          "
        >

          <h2
            className="
              mb-4
              text-lg
              font-semibold
              text-gray-900
            "
          >
            Loan Status
          </h2>

          <div
            className="
              grid
              grid-cols-2
              gap-4
              md:grid-cols-5
            "
          >

            <StatusItem
              label="Active"
              value={
                summary?.activeLoans
              }
            />

            <StatusItem
              label="Closed"
              value={
                summary?.closedLoans
              }
            />

            <StatusItem
              label="Pending"
              value={
                summary?.pendingLoans
              }
            />

            <StatusItem
              label="Rejected"
              value={
                summary?.rejectedLoans
              }
            />

            <StatusItem
              label="Defaulted"
              value={
                summary?.defaultedLoans
              }
            />

          </div>

        </div>


        {/* ================================================== */}
        {/* BREAKDOWNS */}
        {/* ================================================== */}

        <div
          className="
            grid
            grid-cols-1
            gap-6
            lg:grid-cols-3
          "
        >

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


        {/* ================================================== */}
        {/* FOOTER */}
        {/* ================================================== */}

        <div
          className="
            pb-8
            text-center
            text-xs
            text-gray-400
          "
        >

          BNR regulatory report •{' '}
          {period}

        </div>

      </div>

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

    <div
      className="
        rounded-xl
        border
        border-gray-200
        bg-white
        p-5
        shadow-sm
      "
    >

      <p
        className="
          text-sm
          text-gray-500
        "
      >
        {label}
      </p>

      <p
        className="
          mt-2
          text-2xl
          font-bold
          text-gray-900
        "
      >
        {value}
      </p>

      {secondary && (

        <p
          className="
            mt-1
            text-xs
            text-gray-500
          "
        >
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

    <div
      className="
        rounded-lg
        bg-gray-50
        p-4
      "
    >

      <p
        className="
          text-sm
          text-gray-500
        "
      >
        {label}
      </p>

      <p
        className="
          mt-1
          text-xl
          font-semibold
          text-gray-900
        "
      >
        {
          new Intl.NumberFormat(
            'en-US'
          ).format(
            Number(
              value || 0
            )
          )
        }
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

  /*
   * FINAL SAFETY CHECK
   *
   * Even if something unexpected comes through from the API,
   * this prevents:
   *
   * TypeError: rows.map is not a function
   */

  const safeRows =
    Array.isArray(rows)
      ? rows
      : [];


  return (

    <div
      className="
        rounded-xl
        border
        border-gray-200
        bg-white
        shadow-sm
      "
    >

      <div
        className="
          border-b
          border-gray-200
          p-5
        "
      >

        <h2
          className="
            text-lg
            font-semibold
            text-gray-900
          "
        >
          {title}
        </h2>

      </div>


      {safeRows.length === 0 ? (

        <div
          className="
            p-6
            text-center
            text-sm
            text-gray-500
          "
        >
          No data available for this period.
        </div>

      ) : (

        <div
          className="
            overflow-x-auto
          "
        >

          <table
            className="
              min-w-full
              divide-y
              divide-gray-200
            "
          >

            <thead
              className="
                bg-gray-50
              "
            >

              <tr>

                <th
                  className="
                    px-5
                    py-3
                    text-left
                    text-xs
                    font-semibold
                    uppercase
                    tracking-wide
                    text-gray-500
                  "
                >
                  Category
                </th>

                <th
                  className="
                    px-5
                    py-3
                    text-right
                    text-xs
                    font-semibold
                    uppercase
                    tracking-wide
                    text-gray-500
                  "
                >
                  Count
                </th>

                <th
                  className="
                    px-5
                    py-3
                    text-right
                    text-xs
                    font-semibold
                    uppercase
                    tracking-wide
                    text-gray-500
                  "
                >
                  Amount
                </th>

              </tr>

            </thead>


            <tbody
              className="
                divide-y
                divide-gray-100
                bg-white
              "
            >

              {safeRows.map(
                (
                  row,
                  index
                ) => (

                  <tr
                    key={
                      `${row.label}-${index}`
                    }
                    className="
                      hover:bg-gray-50
                    "
                  >

                    <td
                      className="
                        whitespace-nowrap
                        px-5
                        py-3
                        text-sm
                        text-gray-700
                      "
                    >
                      {
                        row.label ||
                        'Unknown'
                      }
                    </td>

                    <td
                      className="
                        whitespace-nowrap
                        px-5
                        py-3
                        text-right
                        text-sm
                        font-medium
                        text-gray-800
                      "
                    >
                      {
                        formatNumber(
                          row.count
                        )
                      }
                    </td>

                    <td
                      className="
                        whitespace-nowrap
                        px-5
                        py-3
                        text-right
                        text-sm
                        text-gray-600
                      "
                    >
                      {
                        formatMoney(
                          row.amount
                        )
                      }
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