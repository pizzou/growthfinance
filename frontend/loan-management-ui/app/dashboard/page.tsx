'use client';
import { useEffect, useState } from 'react';
import { loanApi } from '@/services/api';
import { DashboardStats, Loan } from '@/types';
import { StatCard, Card, CardHeader, CardBody } from '@/components/ui/Card';
import { StatusBadge, RiskBadge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/Table';
import { formatCurrency, formatDate, formatNumber, LOAN_TYPE_META } from '@/lib/utils';
import { useAuth } from '@/hooks/useAuth';
import { useRouter } from 'next/navigation';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, PieChart, Pie, Cell, Legend, AreaChart, Area,
} from 'recharts';

const COLORS = ['#0D9488','#3B82F6','#F59E0B','#EF4444','#8B5CF6','#EC4899'];

export default function DashboardPage() {
  const [stats, setStats]   = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError]   = useState('');
  const { currency, locale, user } = useAuth();
  const router = useRouter();
  const fc = (n?: number) => formatCurrency(n, currency, locale);

  useEffect(() => {
    loanApi.dashboard()
      .then((data: any) => setStats(data))
      .catch(e => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return (
    <div className="flex items-center justify-center h-64">
      <div className="w-8 h-8 border-2 border-teal-500 border-t-transparent rounded-full animate-spin" />
    </div>
  );

  if (error) return (
    <div className="bg-red-50 border border-red-200 rounded-xl p-6 text-red-700 text-sm">{error}</div>
  );

  if (!stats) return null;

  const pieData = [
    { name: 'Active',    value: stats.activeLoans    || 0 },
    { name: 'Pending',   value: stats.pendingLoans   || 0 },
    { name: 'Overdue',   value: stats.overdueLoans   || 0 },
    { name: 'Paid',      value: stats.completedLoans || 0 },
    { name: 'Defaulted', value: stats.defaultedLoans || 0 },
  ].filter(d => d.value > 0);

  const typeData = (stats.loanTypeBreakdown || []).map(t => ({
    name:   LOAN_TYPE_META[String(t.type)]?.label ?? String(t.type),
    count:  Number(t.count)  || 0,
    amount: Number(t.amount) || 0,
  }));

  const par = stats.totalDisbursed > 0
    ? ((stats.outstandingBalance / stats.totalDisbursed) * 100).toFixed(1)
    : '0.0';

  return (
    <div>
      {/* Page header */}
      <div className="flex items-start justify-between mb-6">
        <div>
          <h1 className="text-2xl font-extrabold text-gray-900">Dashboard</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            {user?.organizationName} · {new Date().toLocaleDateString(locale, { weekday:'long', year:'numeric', month:'long', day:'numeric' })}
          </p>
        </div>
        <Button icon="💼" onClick={() => router.push('/dashboard/loans')}>View All Loans</Button>
      </div>

      {/* KPI Grid */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <StatCard icon="💼" label="Total Loans"      value={formatNumber(stats.totalLoans)}      sub={`${stats.activeLoans} active`}       color="#3B82F6" trend={8} />
        <StatCard icon="⏳" label="Pending Review"   value={formatNumber(stats.pendingLoans)}    sub="Awaiting action"                      color="#F59E0B" />
        <StatCard icon="⚠️" label="Overdue Loans"   value={formatNumber(stats.overdueLoans)}    sub={`${stats.latePaymentsCount} late pmts`} color="#EF4444" />
        <StatCard icon="👥" label="Total Borrowers"  value={formatNumber(stats.totalBorrowers)}  sub="Registered clients"                   color="#8B5CF6" />
        <StatCard icon="💰" label="Total Disbursed"  value={fc(stats.totalDisbursed)}            sub="Active portfolio"                     color="#0D9488" trend={12} />
        <StatCard icon="✅" label="Total Collected"  value={fc(stats.totalCollected)}            sub={`${fc(stats.collectedThisMonth)} this month`} color="#0D9488" trend={5} />
        <StatCard icon="📊" label="Outstanding"      value={fc(stats.outstandingBalance)}        sub="Across active loans"                  color="#6366F1" />
        <StatCard icon="🎯" label="Portfolio at Risk" value={`${par}%`}                          sub={Number(par) > 5 ? 'Above 5% threshold' : 'Healthy range'}
                  color={Number(par) > 5 ? '#EF4444' : '#0D9488'} />
      </div>

      {/* Charts row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 mb-6">
        {/* Bar chart */}
        <Card className="lg:col-span-2">
          <CardHeader title="Portfolio by Loan Type" />
          <CardBody>
            {typeData.length > 0 ? (
              <ResponsiveContainer width="100%" height={220}>
                <BarChart data={typeData} barSize={22}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#F3F4F6" vertical={false} />
                  <XAxis dataKey="name" tick={{ fontSize: 11, fill: '#9CA3AF' }} />
                  <YAxis tick={{ fontSize: 11, fill: '#9CA3AF' }} />
                  <Tooltip formatter={(v: number) => formatNumber(v)} />
                  <Bar dataKey="count" fill="#0D9488" radius={[4,4,0,0]} name="Loans" />
                </BarChart>
              </ResponsiveContainer>
            ) : <div className="h-[220px] flex items-center justify-center text-gray-400 text-sm">No loan data yet</div>}
          </CardBody>
        </Card>

        {/* Pie chart */}
        <Card>
          <CardHeader title="Status Distribution" />
          <CardBody>
            {pieData.length > 0 ? (
              <ResponsiveContainer width="100%" height={220}>
                <PieChart>
                  <Pie data={pieData} dataKey="value" nameKey="name"
                    cx="50%" cy="45%" outerRadius={72} innerRadius={32}>
                    {pieData.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                  </Pie>
                  <Tooltip />
                  <Legend iconSize={8} wrapperStyle={{ fontSize: 12 }} />
                </PieChart>
              </ResponsiveContainer>
            ) : <div className="h-[220px] flex items-center justify-center text-gray-400 text-sm">No data</div>}
          </CardBody>
        </Card>
      </div>

      {/* Recent Loans */}
      <Card>
        <CardHeader title="Recent Loan Applications"
          action={<Button variant="ghost" size="sm" onClick={() => router.push('/dashboard/loans')}>See all →</Button>} />
        <Table>
          <Thead>
            <tr>
              <Th>Reference</Th><Th>Borrower</Th><Th>Type</Th>
              <Th>Amount</Th><Th>Rate</Th><Th>Risk</Th><Th>Status</Th><Th>Applied</Th>
            </tr>
          </Thead>
          <Tbody>
            {(!stats.recentLoans || stats.recentLoans.length === 0) ? (
              <Tr><Td className="text-center py-10 text-gray-400 col-span-8">No loans yet</Td></Tr>
            ) : stats.recentLoans.map((loan: Loan) => (
              <Tr key={loan.id} onClick={() => router.push(`/dashboard/loans/${loan.id}`)}>
                <Td><code className="text-xs bg-gray-100 px-2 py-0.5 rounded font-mono">{loan.referenceNumber}</code></Td>
                <Td>
                  <div className="font-semibold text-gray-900 text-sm">{loan.borrower?.firstName} {loan.borrower?.lastName}</div>
                  <div className="text-xs text-gray-400">{loan.borrower?.nationalId}</div>
                </Td>
                <Td><span className="text-xs">{LOAN_TYPE_META[loan.loanType]?.icon} {LOAN_TYPE_META[loan.loanType]?.label ?? loan.loanType}</span></Td>
                <Td><span className="font-bold text-gray-900">{fc(loan.amount)}</span></Td>
                <Td className="text-gray-500">{loan.interestRate}%</Td>
                <Td>{loan.riskCategory ? <RiskBadge category={loan.riskCategory} score={loan.riskScore} /> : <span className="text-gray-400">—</span>}</Td>
                <Td><StatusBadge status={loan.status} /></Td>
                <Td className="text-gray-400 text-xs">{formatDate(loan.startDate, locale)}</Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      </Card>
    </div>
  );
}