
'use client';

import { useEffect, useState } from 'react';

import {
  getDashboardStats,
  getLoanChartData,
  getCollectionChart,
} from '../../../services/dashboardService';

import { getOverduePayments } from '../../../services/paymentService';
import { getLoans } from '../../../services/loanService';

import {
  DashboardStats,
  Payment,
  ChartPoint,
  Loan,
} from '../../../types/index';

import { PageSpinner } from '../../../components/ui/Skeleton';

import {
  BarChart,
  AreaChart,
} from '../../../components/charts/BarChart';

import API from '../../../services/api';
import Link from 'next/link';

const fmt = (n?: number) =>
  n == null
    ? '—'
    : new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: 'USD',
        minimumFractionDigits: 0,
      }).format(n);

// ============================================================
// REPORT DOWNLOAD
// ============================================================

async function downloadReport(
  endpoint: string,
  label: string,
  format: 'csv' | 'excel'
) {
  try {
    const url =
      format === 'excel'
        ? `/reports/export/${endpoint}/excel`
        : `/reports/export/${endpoint}`;

    const res = await API.get(url, {
      responseType: 'blob',
    });

    const blob =
      res.data instanceof Blob
        ? res.data
        : new Blob([res.data]);

    const objectUrl = URL.createObjectURL(blob);

    const a = document.createElement('a');

    a.href = objectUrl;

    const date = new Date()
      .toISOString()
      .slice(0, 10);

    const extension =
      format === 'excel'
        ? 'xlsx'
        : 'csv';

    a.download =
      `${label}-${date}.${extension}`;

    document.body.appendChild(a);
    a.click();
    a.remove();

    setTimeout(() => {
      URL.revokeObjectURL(objectUrl);
    }, 60000);
  } catch (err: unknown) {
    console.error(
      `Could not export ${label} as ${format}`,
      err
    );

    alert(
      err instanceof Error
        ? err.message
        : `Could not export ${label} as ${format.toUpperCase()}`
    );
  }
}

// ============================================================
// EXPORT BUTTONS
// ============================================================

function ExportButtons({
  endpoint,
  label,
}: {
  endpoint: string;
  label: string;
}) {
  return (
    <div className="flex items-center gap-2">
      <button
        type="button"
        onClick={() =>
          downloadReport(
            endpoint,
            label,
            'csv'
          )
        }
        className="
          inline-flex
          items-center
          gap-1.5
          rounded-lg
          border
          border-slate-200
          bg-white
          px-3
          py-2
          text-xs
          font-semibold
          text-slate-700
          shadow-sm
          transition
          hover:border-slate-300
          hover:bg-slate-50
          hover:shadow
        "
      >
        <span>CSV</span>
      </button>

      <button
        type="button"
        onClick={() =>
          downloadReport(
            endpoint,
            label,
            'excel'
          )
        }
        className="
          inline-flex
          items-center
          gap-1.5
          rounded-lg
          border
          border-emerald-200
          bg-emerald-50
          px-3
          py-2
          text-xs
          font-semibold
          text-emerald-700
          shadow-sm
          transition
          hover:border-emerald-300
          hover:bg-emerald-100
          hover:shadow
        "
      >
        <span>Excel</span>
      </button>
    </div>
  );
}

// ============================================================
// PAGE
// ============================================================

export default function ReportsPage() {
  const [stats, setStats] =
    useState<DashboardStats | null>(null);

  const [overdue, setOverdue] =
    useState<Payment[]>([]);

  const [loans, setLoans] =
    useState<Loan[]>([]);

  const [loanChart, setLoanChart] =
    useState<ChartPoint[]>([]);

  const [collectChart, setCollectChart] =
    useState<ChartPoint[]>([]);

  const [loading, setLoading] =
    useState(true);

  // ============================================================
  // LOAD DATA
  // ============================================================

  useEffect(() => {
    Promise.all([
      getDashboardStats(),

      getOverduePayments(),

      getLoans().catch(
        () => [] as Loan[]
      ),

      getLoanChartData().catch(
        () => [] as ChartPoint[]
      ),

      getCollectionChart().catch(
        () => [] as ChartPoint[]
      ),
    ])
      .then(
        ([
          dashboardStats,
          overduePayments,
          loanList,
          loanChartData,
          collectionChartData,
        ]) => {
          setStats(
            dashboardStats as DashboardStats
          );

          setOverdue(
            overduePayments as Payment[]
          );

          setLoans(
            loanList as Loan[]
          );

          setLoanChart(
            loanChartData as ChartPoint[]
          );

          setCollectChart(
            collectionChartData as ChartPoint[]
          );
        }
      )
      .catch(console.error)
      .finally(() => {
        setLoading(false);
      });
  }, []);

  // ============================================================
  // LOADING
  // ============================================================

  if (loading) {
    return <PageSpinner />;
  }

  // ============================================================
  // CALCULATIONS
  // ============================================================

  const rate =
    stats &&
    stats.totalDisbursed > 0
      ? (
          (stats.totalCollected /
            stats.totalDisbursed) *
          100
        ).toFixed(1)
      : '0';

  const penaltiesSum =
    overdue.reduce(
      (sum, payment) =>
        sum + (payment.penalty ?? 0),
      0
    );

  const rejectedCount =
    loans.filter(
      loan =>
        loan.status === 'REJECTED'
    ).length;

  const statusRows = [
    {
      label: 'Active',
      count:
        stats?.activeLoans ?? 0,
      color: 'bg-emerald-500',
    },
    {
      label: 'Pending',
      count:
        stats?.pendingLoans ?? 0,
      color: 'bg-amber-400',
    },
    {
      label: 'Rejected',
      count:
        rejectedCount,
      color: 'bg-red-500',
    },
    {
      label: 'Closed',
      count:
        stats?.completedLoans ?? 0,
      color: 'bg-slate-400',
    },
  ];

  const total =
    statusRows.reduce(
      (sum, row) =>
        sum + row.count,
      0
    ) || 1;

  // ============================================================
  // PAGE
  // ============================================================

  return (
    <div className="space-y-6 pb-10">

      {/* ======================================================
          PREMIUM HEADER
      ====================================================== */}

      <div
        className="
          overflow-hidden
          rounded-2xl
          bg-gradient-to-r
          from-slate-950
          via-slate-900
          to-blue-950
          shadow-lg
        "
      >
        <div className="px-6 py-7 sm:px-8">
          <div className="flex flex-col gap-6 xl:flex-row xl:items-center xl:justify-between">

            {/* Header information */}

            <div className="flex items-start gap-4">

              <div
                className="
                  flex
                  h-12
                  w-12
                  shrink-0
                  items-center
                  justify-center
                  rounded-xl
                  bg-white/10
                  text-2xl
                  ring-1
                  ring-white/15
                "
              >
                📊
              </div>

              <div>
                <div className="flex flex-wrap items-center gap-2">

                  <h1 className="text-2xl font-bold tracking-tight text-white sm:text-3xl">
                    Reports &amp; Analytics
                  </h1>

                  <span
                    className="
                      rounded-full
                      border
                      border-blue-300/20
                      bg-blue-400/10
                      px-2.5
                      py-1
                      text-[10px]
                      font-semibold
                      uppercase
                      tracking-wider
                      text-blue-200
                    "
                  >
                    Executive View
                  </span>

                </div>

                <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-300">
                  Monitor portfolio performance, collections,
                  loan activity and repayment risk from one
                  centralized reporting dashboard.
                </p>
              </div>

            </div>

            {/* Export controls */}

            <div className="flex flex-col gap-2 xl:items-end">

              <p className="text-[10px] font-semibold uppercase tracking-widest text-slate-400">
                Export Reports
              </p>

              <div className="flex flex-wrap gap-2">

                <ExportButtons
                  endpoint="loans"
                  label="loans"
                />

                <ExportButtons
                  endpoint="payments"
                  label="payments"
                />

                <ExportButtons
                  endpoint="overdue"
                  label="overdue-payments"
                />

                <ExportButtons
                  endpoint="summary"
                  label="portfolio-summary"
                />

              </div>

            </div>

          </div>
        </div>
      </div>

      {/* ======================================================
          REPORT EXPORT INFORMATION
      ====================================================== */}

      <div
        className="
          flex
          flex-col
          gap-4
          rounded-xl
          border
          border-slate-200
          bg-white
          p-5
          shadow-sm
          sm:flex-row
          sm:items-center
          sm:justify-between
        "
      >
        <div className="flex items-start gap-3">

          <div
            className="
              flex
              h-10
              w-10
              shrink-0
              items-center
              justify-center
              rounded-lg
              bg-blue-50
              text-lg
            "
          >
            📁
          </div>

          <div>
            <p className="text-sm font-semibold text-slate-900">
              Report exports
            </p>

            <p className="mt-1 text-xs leading-5 text-slate-500">
              Download Loans, Payments, Overdue Payments
              and Portfolio Summary reports in CSV or
              Microsoft Excel (.xlsx) format.
            </p>
          </div>

        </div>

        <div className="flex items-center gap-2 text-xs font-medium text-slate-500">
          <span className="rounded-md bg-slate-100 px-2.5 py-1.5">
            CSV
          </span>

          <span className="text-slate-300">
            +
          </span>

          <span className="rounded-md bg-emerald-50 px-2.5 py-1.5 text-emerald-700">
            Excel
          </span>
        </div>

      </div>

      {/* ======================================================
          REGULATORY REPORTING
      ====================================================== */}

      <Link
        href="/dashboard/reports/regulatory"
        className="
          group
          flex
          items-center
          justify-between
          overflow-hidden
          rounded-2xl
          bg-gradient-to-r
          from-[#0D1B2A]
          to-[#16324F]
          p-5
          text-white
          shadow-md
          transition
          hover:-translate-y-0.5
          hover:shadow-lg
        "
      >

        <div className="flex items-center gap-4">

          <div
            className="
              flex
              h-12
              w-12
              shrink-0
              items-center
              justify-center
              rounded-xl
              bg-white/10
              text-2xl
              ring-1
              ring-white/10
            "
          >
            🏦
          </div>

          <div>

            <p className="text-sm font-semibold text-white">
              Regulatory Reporting
            </p>

            <p className="mt-1 text-xs leading-5 text-slate-300">
              BNR portfolio reports, credit bureau exports
              and API access for external systems.
            </p>

          </div>

        </div>

        <div
          className="
            ml-4
            flex
            h-9
            w-9
            shrink-0
            items-center
            justify-center
            rounded-lg
            bg-white/10
            text-white
            transition
            group-hover:bg-white/20
          "
        >
          →
        </div>

      </Link>

      {/* ======================================================
          KPI CARDS
      ====================================================== */}

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">

        {[
          {
            label: 'Total Disbursed',
            value:
              fmt(stats?.totalDisbursed),
            accent:
              'border-indigo-100',
            icon:
              '💰',
            iconBg:
              'bg-indigo-50',
          },
          {
            label: 'Collected',
            value:
              fmt(stats?.totalCollected),
            accent:
              'border-emerald-100',
            icon:
              '✓',
            iconBg:
              'bg-emerald-50',
          },
          {
            label: 'Collection Rate',
            value:
              `${rate}%`,
            accent:
              'border-blue-100',
            icon:
              '📈',
            iconBg:
              'bg-blue-50',
          },
          {
            label: 'Penalty Income',
            value:
              fmt(penaltiesSum),
            accent:
              'border-orange-100',
            icon:
              '⚠',
            iconBg:
              'bg-orange-50',
          },
        ].map(
          ({
            label,
            value,
            accent,
            icon,
            iconBg,
          }) => (

            <div
              key={label}
              className={`
                group
                rounded-xl
                border
                ${accent}
                bg-white
                p-5
                shadow-sm
                transition
                hover:-translate-y-0.5
                hover:shadow-md
              `}
            >

              <div className="flex items-start justify-between">

                <div>
                  <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-400">
                    {label}
                  </p>

                  <p className="mt-2 text-xl font-bold tracking-tight text-slate-900 sm:text-2xl">
                    {value}
                  </p>
                </div>

                <div
                  className={`
                    flex
                    h-9
                    w-9
                    items-center
                    justify-center
                    rounded-lg
                    ${iconBg}
                    text-sm
                  `}
                >
                  {icon}
                </div>

              </div>

            </div>

          )
        )}

      </div>

      {/* ======================================================
          CHARTS
      ====================================================== */}

      {(loanChart.length > 0 ||
        collectChart.length > 0) && (

        <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">

          {loanChart.length > 0 && (
            <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">

              <BarChart
                data={loanChart}
                label="Monthly Loan Disbursements (6 months)"
                color="bg-indigo-500"
                valuePrefix="$"
              />

            </div>
          )}

          {collectChart.length > 0 && (
            <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">

              <AreaChart
                data={collectChart}
                label="Monthly Collections (6 months)"
                color="#10b981"
                valuePrefix="$"
              />

            </div>
          )}

        </div>

      )}

      {/* ======================================================
          STATUS + OVERDUE
      ====================================================== */}

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">

        {/* Loan status */}

        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">

          <div className="mb-6 flex items-center justify-between">

            <div>
              <h2 className="text-sm font-bold text-slate-900">
                Loan Status Distribution
              </h2>

              <p className="mt-1 text-xs text-slate-400">
                Current portfolio composition
              </p>
            </div>

            <div className="rounded-lg bg-slate-50 px-3 py-2 text-xs font-semibold text-slate-500">
              {total} loans
            </div>

          </div>

          <div className="space-y-5">

            {statusRows.map(
              row => (

                <div key={row.label}>

                  <div className="mb-2 flex items-center justify-between">

                    <div className="flex items-center gap-2">

                      <span
                        className={`
                          h-2
                          w-2
                          rounded-full
                          ${row.color}
                        `}
                      />

                      <span className="text-sm font-medium text-slate-600">
                        {row.label}
                      </span>

                    </div>

                    <span className="text-xs font-semibold text-slate-700">
                      {row.count}{' '}
                      (
                      {Math.round(
                        (row.count /
                          total) *
                          100
                      )}
                      %)
                    </span>

                  </div>

                  <div className="h-2 overflow-hidden rounded-full bg-slate-100">

                    <div
                      className={`
                        h-full
                        rounded-full
                        ${row.color}
                        transition-all
                        duration-500
                      `}
                      style={{
                        width:
                          `${(
                            (row.count /
                              total) *
                            100
                          )}%`,
                      }}
                    />

                  </div>

                </div>

              )
            )}

          </div>

        </div>

        {/* Overdue */}

        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">

          <div className="mb-5 flex items-center justify-between">

            <div>
              <h2 className="text-sm font-bold text-slate-900">
                Overdue Payments
              </h2>

              <p className="mt-1 text-xs text-slate-400">
                Payments requiring attention
              </p>
            </div>

            {overdue.length > 0 && (

              <span
                className="
                  rounded-full
                  bg-red-50
                  px-3
                  py-1.5
                  text-[11px]
                  font-bold
                  text-red-600
                "
              >
                {overdue.length} overdue
              </span>

            )}

          </div>

          {overdue.length === 0 ? (

            <div className="rounded-xl bg-emerald-50 py-10 text-center">

              <div className="mb-3 text-3xl">
                ✓
              </div>

              <p className="text-sm font-semibold text-emerald-800">
                Portfolio is up to date
              </p>

              <p className="mt-1 text-xs text-emerald-600">
                No overdue payments detected.
              </p>

            </div>

          ) : (

            <div className="max-h-72 space-y-1 overflow-y-auto pr-1">

              {overdue
                .slice(0, 12)
                .map(payment => {

                  const days = Math.max(
                    0,
                    Math.floor(
                      (
                        Date.now() -
                        new Date(
                          payment.dueDate
                        ).getTime()
                      ) /
                        86400000
                    )
                  );

                  return (

                    <div
                      key={payment.id}
                      className="
                        flex
                        items-center
                        justify-between
                        rounded-lg
                        border-b
                        border-slate-50
                        px-2
                        py-3
                        last:border-0
                        hover:bg-slate-50
                      "
                    >

                      <div className="min-w-0">

                        <p className="truncate text-sm font-semibold text-slate-800">
                          Payment #{payment.id}
                        </p>

                        <p className="mt-1 text-[11px] text-slate-400">
                          Due {payment.dueDate}
                          {' · '}
                          <span className="font-medium text-red-500">
                            {days}d overdue
                          </span>
                        </p>

                      </div>

                      <div className="ml-4 shrink-0 text-right">

                        <p className="text-sm font-bold text-red-600">
                          $
                          {payment.amount?.toLocaleString()}
                        </p>

                        {(payment.penalty ?? 0) > 0 && (

                          <p className="mt-1 text-[11px] font-medium text-orange-500">
                            +
                            $
                            {payment.penalty?.toFixed(
                              2
                            )}{' '}
                            penalty
                          </p>

                        )}

                      </div>

                    </div>

                  );
                })}

            </div>

          )}

        </div>

      </div>

      {/* ======================================================
          PORTFOLIO HEALTH
      ====================================================== */}

      <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">

        <div className="mb-6">

          <h2 className="text-sm font-bold text-slate-900">
            Portfolio Health
          </h2>

          <p className="mt-1 text-xs text-slate-400">
            High-level portfolio indicators
          </p>

        </div>

        <div className="grid grid-cols-2 gap-4 md:grid-cols-4">

          {[
            {
              label: 'Borrowers',
              value:
                stats?.totalBorrowers ?? 0,
              icon:
                '👥',
              bg:
                'bg-blue-50',
            },
            {
              label: 'Active Loans',
              value:
                stats?.activeLoans ?? 0,
              icon:
                '📋',
              bg:
                'bg-emerald-50',
            },
            {
              label: 'Overdue',
              value:
                stats?.overdueLoans ?? 0,
              icon:
                '⚠️',
              bg:
                'bg-orange-50',
            },
            {
              label: 'Closed',
              value:
                stats?.completedLoans ?? 0,
              icon:
                '✓',
              bg:
                'bg-slate-100',
            },
          ].map(
            ({
              label,
              value,
              icon,
              bg,
            }) => (

              <div
                key={label}
                className="
                  rounded-xl
                  border
                  border-slate-100
                  bg-slate-50/60
                  p-5
                  text-center
                  transition
                  hover:bg-white
                  hover:shadow-sm
                "
              >

                <div
                  className={`
                    mx-auto
                    mb-3
                    flex
                    h-11
                    w-11
                    items-center
                    justify-center
                    rounded-xl
                    ${bg}
                    text-lg
                  `}
                >
                  {icon}
                </div>

                <p className="text-2xl font-bold tracking-tight text-slate-900">
                  {value}
                </p>

                <p className="mt-1 text-xs font-medium text-slate-500">
                  {label}
                </p>

              </div>

            )
          )}

        </div>

      </div>

    </div>
  );
}
