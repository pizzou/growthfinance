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

    const objectUrl =
      URL.createObjectURL(blob);

    const a =
      document.createElement('a');

    a.href = objectUrl;

    const date =
      new Date()
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
    <div className="flex items-center gap-1.5">
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
          text-xs
          font-semibold
          px-3
          py-2
          rounded-lg
          border
          border-gray-200
          bg-white
          hover:bg-gray-50
          text-gray-700
          transition
        "
      >
        CSV
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
          text-xs
          font-semibold
          px-3
          py-2
          rounded-lg
          border
          border-green-200
          bg-green-50
          hover:bg-green-100
          text-green-700
          transition
        "
      >
        Excel
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
  // LOAD REPORT DATA
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
      color: 'bg-green-500',
    },
    {
      label: 'Pending',
      count:
        stats?.pendingLoans ?? 0,
      color: 'bg-yellow-400',
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
      color: 'bg-gray-400',
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
    <div className="space-y-6">

      {/* ======================================================
          HEADER
      ====================================================== */}

      <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">

        <div>
          <h1 className="text-2xl font-bold text-gray-900">
            Reports &amp; Analytics
          </h1>

          <p className="text-gray-500 text-sm mt-1">
            Portfolio overview and financial performance
          </p>
        </div>

        {/* Export controls */}

        <div className="flex flex-wrap items-center gap-2">

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

      {/* ======================================================
          EXPORT INFORMATION
      ====================================================== */}

      <div className="bg-blue-50 border border-blue-100 rounded-xl p-4">

        <div className="flex items-start gap-3">

          <span className="text-xl">
            📊
          </span>

          <div>

            <p className="text-sm font-semibold text-blue-900">
              Report exports
            </p>

            <p className="text-xs text-blue-700 mt-1">
              Export Loans, Payments, Overdue Payments
              and Portfolio Summary as CSV or Microsoft
              Excel (.xlsx) files.
            </p>

          </div>

        </div>

      </div>

      {/* ======================================================
          REGULATORY REPORTING
      ====================================================== */}

      <Link
        href="/dashboard/reports/regulatory"
        className="
          flex
          items-center
          justify-between
          bg-[#0D1B2A]
          rounded-xl
          p-5
          text-white
          hover:opacity-95
          transition
        "
      >

        <div className="flex items-center gap-4">

          <span className="text-2xl">
            🏦
          </span>

          <div>

            <p className="font-semibold text-sm">
              Regulatory Reporting
            </p>

            <p className="text-white/60 text-xs mt-0.5">
              BNR portfolio reports, credit bureau
              exports &amp; API access for external systems
            </p>

          </div>

        </div>

        <span className="text-white/50">
          →
        </span>

      </Link>

      {/* ======================================================
          KPI CARDS
      ====================================================== */}

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">

        {[
          {
            label: 'Total Disbursed',
            value:
              fmt(
                stats?.totalDisbursed
              ),
            color: 'text-indigo-600',
          },
          {
            label: 'Collected',
            value:
              fmt(
                stats?.totalCollected
              ),
            color: 'text-green-600',
          },
          {
            label: 'Collection Rate',
            value:
              `${rate}%`,
            color: 'text-blue-600',
          },
          {
            label: 'Penalty Income',
            value:
              fmt(penaltiesSum),
            color: 'text-orange-600',
          },
        ].map(
          ({
            label,
            value,
            color,
          }) => (

            <div
              key={label}
              className="
                bg-white
                rounded-xl
                border
                border-gray-200
                p-5
              "
            >

              <p className="text-gray-500 text-xs uppercase tracking-wide">
                {label}
              </p>

              <p
                className={`
                  text-2xl
                  font-bold
                  mt-1
                  ${color}
                `}
              >
                {value}
              </p>

            </div>

          )
        )}

      </div>

      {/* ======================================================
          CHARTS
      ====================================================== */}

      {(loanChart.length > 0 ||
        collectChart.length > 0) && (

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">

          {loanChart.length > 0 && (

            <BarChart
              data={loanChart}
              label="Monthly Loan Disbursements (6 months)"
              color="bg-indigo-500"
              valuePrefix="$"
            />

          )}

          {collectChart.length > 0 && (

            <AreaChart
              data={collectChart}
              label="Monthly Collections (6 months)"
              color="#10b981"
              valuePrefix="$"
            />

          )}

        </div>

      )}

      {/* ======================================================
          STATUS + OVERDUE
      ====================================================== */}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">

        {/* Loan status */}

        <div className="
          bg-white
          rounded-xl
          border
          border-gray-200
          p-6
        ">

          <h2 className="font-semibold text-gray-800 mb-5 text-sm">
            Loan Status Distribution
          </h2>

          <div className="space-y-4">

            {statusRows.map(
              row => (

                <div key={row.label}>

                  <div className="flex justify-between text-sm mb-1">

                    <span className="text-gray-600">
                      {row.label}
                    </span>

                    <span className="font-medium text-gray-800">
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

                  <div className="
                    w-full
                    bg-gray-100
                    rounded-full
                    h-2
                  ">

                    <div
                      className={`
                        ${row.color}
                        h-2
                        rounded-full
                        transition-all
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

        <div className="
          bg-white
          rounded-xl
          border
          border-gray-200
          p-6
        ">

          <div className="
            flex
            items-center
            justify-between
            mb-5
          ">

            <h2 className="font-semibold text-gray-800 text-sm">
              Overdue Payments
            </h2>

            {overdue.length > 0 && (

              <span className="
                bg-red-100
                text-red-700
                px-2.5
                py-1
                rounded-full
                text-xs
                font-medium
              ">
                {overdue.length} overdue
              </span>

            )}

          </div>

          {overdue.length === 0 ? (

            <div className="text-center py-8">

              <p className="text-3xl mb-2">
                ✅
              </p>

              <p className="text-gray-500 text-sm">
                No overdue payments!
              </p>

            </div>

          ) : (

            <div className="
              space-y-3
              max-h-64
              overflow-y-auto
            ">

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
                        py-2
                        border-b
                        border-gray-50
                        last:border-0
                      "
                    >

                      <div>

                        <p className="
                          text-sm
                          font-medium
                          text-gray-800
                        ">
                          Payment #{payment.id}
                        </p>

                        <p className="
                          text-xs
                          text-gray-400
                        ">
                          Due {payment.dueDate}
                          {' · '}
                          {days}d overdue
                        </p>

                      </div>

                      <div className="text-right">

                        <p className="
                          font-semibold
                          text-red-600
                          text-sm
                        ">
                          $
                          {payment.amount?.toLocaleString()}
                        </p>

                        {(payment.penalty ?? 0) > 0 && (

                          <p className="
                            text-xs
                            text-orange-500
                          ">
                            +
                            $
                            {payment.penalty?.toFixed(
                              2
                            )}
                            {' '}penalty
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

      <div className="
        bg-white
        rounded-xl
        border
        border-gray-200
        p-6
      ">

        <h2 className="
          font-semibold
          text-gray-800
          mb-5
          text-sm
        ">
          Portfolio Health
        </h2>

        <div className="
          grid
          grid-cols-2
          md:grid-cols-4
          gap-6
          text-center
        ">

          {[
            {
              label: 'Borrowers',
              value:
                stats?.totalBorrowers ?? 0,
              emoji: '👥',
            },
            {
              label: 'Active Loans',
              value:
                stats?.activeLoans ?? 0,
              emoji: '📋',
            },
            {
              label: 'Overdue',
              value:
                stats?.overdueLoans ?? 0,
              emoji: '⚠️',
            },
            {
              label: 'Closed',
              value:
                stats?.completedLoans ?? 0,
              emoji: '✅',
            },
          ].map(
            ({
              label,
              value,
              emoji,
            }) => (

              <div key={label}>

                <p className="text-3xl mb-1">
                  {emoji}
                </p>

                <p className="
                  text-2xl
                  font-bold
                  text-gray-800
                ">
                  {value}
                </p>

                <p className="
                  text-gray-500
                  text-sm
                  mt-0.5
                ">
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