'use client';
import { useEffect, useState } from 'react';
import { getDashboardStats, getLoanChartData, getCollectionChart } from '../../../services/dashboardService';
import { getOverduePayments } from '../../../services/paymentService';
import { getLoans } from '../../../services/loanService';
import { DashboardStats, Payment, ChartPoint, Loan } from '../../../types/index';
import { PageSpinner } from '../../../components/ui/Skeleton';
import { BarChart, AreaChart } from '../../../components/charts/BarChart';
import API from '../../../services/api';
import Link from 'next/link';

const fmt = (n?: number) =>
  n == null ? '—' : new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 0 }).format(n);

async function downloadReport(endpoint: string, label: string) {
  try {
    const res = await API.get(`/reports/export/${endpoint}`, { responseType: 'blob' });
    const url = URL.createObjectURL(res.data as Blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${label}-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  } catch (err: unknown) {
    alert(err instanceof Error ? err.message : `Could not export ${label}`);
  }
}

export default function ReportsPage() {
  const [stats,        setStats]        = useState<DashboardStats | null>(null);
  const [overdue,      setOverdue]      = useState<Payment[]>([]);
  const [loans,        setLoans]        = useState<Loan[]>([]);
  const [loanChart,    setLoanChart]    = useState<ChartPoint[]>([]);
  const [collectChart, setCollectChart] = useState<ChartPoint[]>([]);
  const [loading,      setLoading]      = useState(true);

  useEffect(() => {
    Promise.all([
      getDashboardStats(),
      getOverduePayments(),
      getLoans().catch(() => [] as Loan[]),
      getLoanChartData().catch(() => [] as ChartPoint[]),
      getCollectionChart().catch(() => [] as ChartPoint[]),
    ])
      .then(([s, o, ln, lc, cc]) => {
        setStats(s as DashboardStats);
        setOverdue(o as Payment[]);
        setLoans(ln as Loan[]);
        setLoanChart(lc as ChartPoint[]);
        setCollectChart(cc as ChartPoint[]);
      })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <PageSpinner />;

  const rate = stats && stats.totalDisbursed > 0
    ? ((stats.totalCollected / stats.totalDisbursed) * 100).toFixed(1) : '0';
  const penaltiesSum = overdue.reduce((sum, p) => sum + (p.penalty ?? 0), 0);
  const rejectedCount = loans.filter(l => l.status === 'REJECTED').length;

  const statusRows = [
    { label: 'Active',   count: stats?.activeLoans    ?? 0, color: 'bg-green-500'  },
    { label: 'Pending',  count: stats?.pendingLoans   ?? 0, color: 'bg-yellow-400' },
    { label: 'Rejected', count: rejectedCount,              color: 'bg-red-500'    },
    { label: 'Closed',   count: stats?.completedLoans ?? 0, color: 'bg-gray-400'   },
  ];
  const total = statusRows.reduce((s, r) => s + r.count, 0) || 1;

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-xl font-bold text-gray-900">Reports &amp; Analytics</h1>
          <p className="text-sm text-gray-500">Portfolio overview and financial performance</p>
        </div>
        <div className="flex gap-2 flex-wrap">
          {[
            { key: 'loans',    label: 'Loans' },
            { key: 'payments', label: 'Payments' },
            { key: 'overdue',  label: 'Overdue' },
            { key: 'summary',  label: 'Summary' },
          ].map(({ key, label }) => (
            <button key={key}
              onClick={() => downloadReport(key, label.toLowerCase())}
              className="text-xs font-semibold px-3 py-2 rounded-lg border border-gray-200 bg-white hover:bg-gray-50 text-gray-700 transition"
            >
              ⬇ Export {label}
            </button>
          ))}
        </div>
      </div>

      {/* Regulatory reporting */}
      <Link href="/dashboard/reports/regulatory"
        className="flex items-center justify-between bg-[#0D1B2A] rounded-xl p-5 text-white hover:opacity-95 transition">
        <div className="flex items-center gap-4">
          <span className="text-2xl">🏦</span>
          <div>
            <p className="font-semibold text-sm">Regulatory Reporting</p>
            <p className="text-white/60 text-xs mt-0.5">BNR portfolio reports, credit bureau exports &amp; API access for external systems</p>
          </div>
        </div>
        <span className="text-white/50">→</span>
      </Link>

      {/* KPI cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {[
          { label: 'Total Disbursed',  value: fmt(stats?.totalDisbursed), color: 'text-indigo-600' },
          { label: 'Collected',        value: fmt(stats?.totalCollected), color: 'text-green-600'  },
          { label: 'Collection Rate',  value: `${rate}%`,                 color: 'text-blue-600'   },
          { label: 'Penalty Income',   value: fmt(penaltiesSum),          color: 'text-orange-600' },
        ].map(({ label, value, color }) => (
          <div key={label} className="bg-white rounded-xl border border-gray-200 p-5">
            <p className="text-gray-500 text-xs uppercase tracking-wide">{label}</p>
            <p className={`text-2xl font-bold mt-1 ${color}`}>{value}</p>
          </div>
        ))}
      </div>

      {/* Charts from API */}
      {(loanChart.length > 0 || collectChart.length > 0) && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          {loanChart.length > 0 && (
            <BarChart data={loanChart} label="Monthly Loan Disbursements (6 months)" color="bg-indigo-500" valuePrefix="$" />
          )}
          {collectChart.length > 0 && (
            <AreaChart data={collectChart} label="Monthly Collections (6 months)" color="#10b981" valuePrefix="$" />
          )}
        </div>
      )}

      {/* Status distribution */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl border border-gray-200 p-6">
          <h2 className="font-semibold text-gray-800 mb-5 text-sm">Loan Status Distribution</h2>
          <div className="space-y-4">
            {statusRows.map(r => (
              <div key={r.label}>
                <div className="flex justify-between text-sm mb-1">
                  <span className="text-gray-600">{r.label}</span>
                  <span className="font-medium text-gray-800">{r.count} ({Math.round((r.count / total) * 100)}%)</span>
                </div>
                <div className="w-full bg-gray-100 rounded-full h-2">
                  <div className={`${r.color} h-2 rounded-full transition-all`} style={{ width: `${(r.count / total) * 100}%` }} />
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Overdue list */}
        <div className="bg-white rounded-xl border border-gray-200 p-6">
          <div className="flex items-center justify-between mb-5">
            <h2 className="font-semibold text-gray-800 text-sm">Overdue Payments</h2>
            {overdue.length > 0 && <span className="bg-red-100 text-red-700 px-2.5 py-1 rounded-full text-xs font-medium">{overdue.length} overdue</span>}
          </div>
          {overdue.length === 0 ? (
            <div className="text-center py-8"><p className="text-3xl mb-2">✅</p><p className="text-gray-500 text-sm">No overdue payments!</p></div>
          ) : (
            <div className="space-y-3 max-h-64 overflow-y-auto">
              {overdue.slice(0, 12).map(p => {
                const days = Math.floor((Date.now() - new Date(p.dueDate).getTime()) / 86400000);
                return (
                  <div key={p.id} className="flex items-center justify-between py-2 border-b border-gray-50 last:border-0">
                    <div>
                      <p className="text-sm font-medium text-gray-800">Payment #{p.id}</p>
                      <p className="text-xs text-gray-400">Due {p.dueDate} &middot; {days}d overdue</p>
                    </div>
                    <div className="text-right">
                      <p className="font-semibold text-red-600 text-sm">${p.amount?.toLocaleString()}</p>
                      {(p.penalty ?? 0) > 0 && <p className="text-xs text-orange-500">+${p.penalty?.toFixed(2)} penalty</p>}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* Portfolio health */}
      <div className="bg-white rounded-xl border border-gray-200 p-6">
        <h2 className="font-semibold text-gray-800 mb-5 text-sm">Portfolio Health</h2>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-6 text-center">
          {[
            { label: 'Borrowers',    value: stats?.totalBorrowers  ?? 0, emoji: '👥' },
            { label: 'Active Loans', value: stats?.activeLoans     ?? 0, emoji: '📋' },
            { label: 'Overdue',      value: stats?.overdueLoans    ?? 0, emoji: '⚠️' },
            { label: 'Closed',       value: stats?.completedLoans  ?? 0, emoji: '✅' },
          ].map(({ label, value, emoji }) => (
            <div key={label}>
              <p className="text-3xl mb-1">{emoji}</p>
              <p className="text-2xl font-bold text-gray-800">{value}</p>
              <p className="text-gray-500 text-sm mt-0.5">{label}</p>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}