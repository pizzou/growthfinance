'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { loanApi } from '@/services/api';
import { DashboardStats, Loan } from '@/types';
import { StatCard, Card, CardHeader, CardBody } from '@/components/ui/Card';
import { StatusBadge, RiskBadge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import {
Table,
Thead,
Th,
Tbody,
Tr,
Td,
} from '@/components/ui/Table';
import {
formatCurrency,
formatDate,
formatNumber,
LOAN_TYPE_META,
} from '@/lib/utils';
import { useAuth } from '@/hooks/useAuth';

import {
BarChart,
Bar,
XAxis,
YAxis,
CartesianGrid,
Tooltip,
ResponsiveContainer,
PieChart,
Pie,
Cell,
Legend,
AreaChart,
Area,
} from 'recharts';

/* ============================================================
GROWTH FINANCE BRAND
============================================================ */

const COLORS = [
'#16A34A',
'#EAB308',
'#22C55E',
'#F59E0B',
'#15803D',
'#CA8A04',
];

const GREEN = '#16A34A';
const YELLOW = '#EAB308';

/* ============================================================
DASHBOARD
============================================================ */

export default function DashboardPage() {
const router = useRouter();

const { currency, locale, user } = useAuth();

const [stats, setStats] = useState<DashboardStats | null>(null);
const [loading, setLoading] = useState(true);
const [error, setError] = useState('');

/* ==========================================================
LOAD DASHBOARD
========================================================== */

useEffect(() => {
let mounted = true;


setLoading(true);
setError('');

loanApi
  .dashboard()
  .then((data: DashboardStats) => {
    if (!mounted) return;

    setStats(data);
  })
  .catch((e: any) => {
    if (!mounted) return;

    setError(
      e?.message ||
        'Unable to load dashboard information.'
    );
  })
  .finally(() => {
    if (!mounted) return;

    setLoading(false);
  });

return () => {
  mounted = false;
};


}, []);

/* ==========================================================
HELPERS
========================================================== */

const fc = (value?: number | null) =>
formatCurrency(
Number(value || 0),
currency,
locale
);

const today = new Date().toLocaleDateString(
locale || 'en-US',
{
weekday: 'long',
year: 'numeric',
month: 'long',
day: 'numeric',
}
);

/* ==========================================================
LOADING
========================================================== */

if (loading) {
return ( <div className="min-h-[70vh] flex items-center justify-center"> <div className="flex flex-col items-center gap-4"> <div className="relative"> <div className="w-12 h-12 rounded-full border-4 border-green-100" />


        <div className="absolute inset-0 w-12 h-12 rounded-full border-4 border-green-600 border-t-transparent animate-spin" />
      </div>

      <div className="text-center">
        <p className="text-sm font-semibold text-gray-800">
          Loading your dashboard
        </p>

        <p className="text-xs text-gray-400 mt-1">
          Preparing your portfolio overview...
        </p>
      </div>
    </div>
  </div>
);


}

/* ==========================================================
ERROR
========================================================== */

if (error) {
return ( <div className="min-h-[60vh] flex items-center justify-center"> <div className="max-w-md w-full bg-white border border-red-200 rounded-2xl shadow-sm p-8 text-center"> <div className="w-12 h-12 mx-auto rounded-full bg-red-50 flex items-center justify-center text-xl">
⚠️ </div>


      <h2 className="mt-4 text-lg font-bold text-gray-900">
        Unable to load dashboard
      </h2>

      <p className="mt-2 text-sm text-gray-500">
        {error}
      </p>

      <Button
        className="mt-6"
        onClick={() => window.location.reload()}
      >
        Try Again
      </Button>
    </div>
  </div>
);


}

/* ==========================================================
EMPTY STATE
========================================================== */

if (!stats) {
return ( <div className="min-h-[60vh] flex items-center justify-center"> <div className="text-center"> <div className="text-4xl mb-3">📊</div>


      <h2 className="text-lg font-bold text-gray-900">
        No dashboard data
      </h2>

      <p className="text-sm text-gray-500 mt-1">
        There is currently no portfolio information available.
      </p>
    </div>
  </div>
);


}

/* ==========================================================
DERIVED DATA
========================================================== */

const pieData = [
{
name: 'Active',
value: Number(stats.activeLoans || 0),
},
{
name: 'Pending',
value: Number(stats.pendingLoans || 0),
},
{
name: 'Overdue',
value: Number(stats.overdueLoans || 0),
},
{
name: 'Paid',
value: Number(stats.completedLoans || 0),
},
{
name: 'Defaulted',
value: Number(stats.defaultedLoans || 0),
},
].filter((item) => item.value > 0);

const typeData = (
stats.loanTypeBreakdown || []
).map((item) => ({
name:
LOAN_TYPE_META[String(item.type)]?.label ??
String(item.type),


count: Number(item.count || 0),

amount: Number(item.amount || 0),

}));

const totalDisbursed =
Number(stats.totalDisbursed || 0);

const outstandingBalance =
Number(stats.outstandingBalance || 0);

const calculatedPar =
totalDisbursed > 0
? (outstandingBalance / totalDisbursed) * 100
: 0;

const portfolioAtRisk =
Number(
stats.portfolioAtRiskPct ??
calculatedPar ??
0
);

const recentLoans =
stats.recentLoans || [];

/* ==========================================================
RENDER
========================================================== */

return ( <div className="space-y-6 pb-10">


  {/* ======================================================
      HEADER
  ====================================================== */}

  <div className="relative overflow-hidden rounded-2xl bg-gradient-to-r from-green-700 via-green-600 to-green-500 text-white shadow-lg">

    <div className="absolute -right-16 -top-16 w-56 h-56 rounded-full bg-yellow-400/20" />

    <div className="absolute right-20 -bottom-20 w-48 h-48 rounded-full bg-yellow-300/10" />

    <div className="relative px-6 py-7 lg:px-8">

      <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-5">

        <div>

          <div className="flex items-center gap-2 mb-2">

            <span className="inline-flex items-center px-3 py-1 rounded-full bg-yellow-400 text-green-950 text-xs font-bold">
              GROWTH FINANCE
            </span>

            <span className="text-green-100 text-xs">
              Loan Management Platform
            </span>

          </div>

          <h1 className="text-2xl lg:text-3xl font-extrabold tracking-tight">
            Welcome back{user?.name ? `, ${user.name}` : ''}
          </h1>

          <p className="text-green-100 text-sm mt-1">
            Here is your portfolio overview for today.
          </p>

          <p className="text-green-200 text-xs mt-3">
            {user?.organizationName || 'Organization'}{' '}
            · {today}
          </p>

        </div>

        <div className="flex flex-wrap gap-3">

          <Button
            variant="secondary"
            icon="👥"
            onClick={() =>
              router.push('/dashboard/borrowers')
            }
          >
            Borrowers
          </Button>

          <Button
            icon="💼"
            onClick={() =>
              router.push('/dashboard/loans')
            }
          >
            View Loans
          </Button>

        </div>

      </div>

    </div>
  </div>


  {/* ======================================================
      EXECUTIVE KPI SECTION
  ====================================================== */}

  <div>

    <div className="flex items-center justify-between mb-3">

      <div>
        <h2 className="text-sm font-bold text-gray-900 uppercase tracking-wide">
          Portfolio Overview
        </h2>

        <p className="text-xs text-gray-400 mt-1">
          Real-time lending performance
        </p>
      </div>

    </div>

    <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">

      <StatCard
        icon="💼"
        label="Total Loans"
        value={formatNumber(stats.totalLoans || 0)}
        sub={`${formatNumber(
          stats.activeLoans || 0
        )} active`}
        color={GREEN}
      />

      <StatCard
        icon="👥"
        label="Total Borrowers"
        value={formatNumber(
          stats.totalBorrowers || 0
        )}
        sub="Registered clients"
        color="#15803D"
      />

      <StatCard
        icon="💰"
        label="Total Disbursed"
        value={fc(stats.totalDisbursed)}
        sub="Loan portfolio"
        color={GREEN}
      />

      <StatCard
        icon="📊"
        label="Outstanding"
        value={fc(stats.outstandingBalance)}
        sub="Current balance"
        color="#CA8A04"
      />

    </div>

  </div>


  {/* ======================================================
      ATTENTION / PERFORMANCE KPI
  ====================================================== */}

  <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">

    <StatCard
      icon="⏳"
      label="Pending Review"
      value={formatNumber(
        stats.pendingLoans || 0
      )}
      sub="Awaiting action"
      color={YELLOW}
    />

    <StatCard
      icon="⚠️"
      label="Overdue Loans"
      value={formatNumber(
        stats.overdueLoans || 0
      )}
      sub={`${formatNumber(
        stats.latePaymentsCount || 0
      )} late payments`}
      color="#DC2626"
    />

    <StatCard
      icon="✅"
      label="Collected"
      value={fc(stats.totalCollected)}
      sub={`${fc(
        stats.collectedThisMonth
      )} this month`}
      color={GREEN}
    />

    <StatCard
      icon="🎯"
      label="Portfolio at Risk"
      value={`${portfolioAtRisk.toFixed(1)}%`}
      sub={
        portfolioAtRisk > 5
          ? 'Requires attention'
          : 'Healthy portfolio'
      }
      color={
        portfolioAtRisk > 5
          ? '#DC2626'
          : GREEN
      }
    />

  </div>


  {/* ======================================================
      MANAGEMENT ATTENTION PANEL
  ====================================================== */}

  {(stats.pendingLoans > 0 ||
    stats.overdueLoans > 0 ||
    stats.defaultedLoans > 0) && (

    <Card className="border-yellow-200 bg-gradient-to-r from-yellow-50 to-white">

      <CardBody>

        <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-5">

          <div className="flex items-start gap-4">

            <div className="w-11 h-11 rounded-xl bg-yellow-100 flex items-center justify-center text-xl shrink-0">
              ⚡
            </div>

            <div>

              <h3 className="font-bold text-gray-900">
                Management attention required
              </h3>

              <p className="text-sm text-gray-500 mt-1">
                There are portfolio items that may require
                immediate action.
              </p>

              <div className="flex flex-wrap gap-2 mt-3">

                {stats.pendingLoans > 0 && (
                  <span className="px-2.5 py-1 rounded-full bg-yellow-100 text-yellow-800 text-xs font-semibold">
                    {stats.pendingLoans} pending
                  </span>
                )}

                {stats.overdueLoans > 0 && (
                  <span className="px-2.5 py-1 rounded-full bg-red-100 text-red-700 text-xs font-semibold">
                    {stats.overdueLoans} overdue
                  </span>
                )}

                {stats.defaultedLoans > 0 && (
                  <span className="px-2.5 py-1 rounded-full bg-red-100 text-red-700 text-xs font-semibold">
                    {stats.defaultedLoans} defaulted
                  </span>
                )}

              </div>

            </div>

          </div>

          <Button
            onClick={() =>
              router.push('/dashboard/loans')
            }
          >
            Review Portfolio →
          </Button>

        </div>

      </CardBody>

    </Card>

  )}


  {/* ======================================================
      ANALYTICS
  ====================================================== */}

  <div className="grid grid-cols-1 xl:grid-cols-3 gap-5">

    {/* ----------------------------------------------------
        LOAN TYPE
    ---------------------------------------------------- */}

    <Card className="xl:col-span-2">

      <CardHeader
        title="Portfolio by Loan Type"
        action={
          <span className="text-xs text-gray-400">
            Number of loans
          </span>
        }
      />

      <CardBody>

        {typeData.length > 0 ? (

          <ResponsiveContainer
            width="100%"
            height={280}
          >

            <BarChart
              data={typeData}
              barSize={28}
              margin={{
                top: 10,
                right: 10,
                left: -10,
                bottom: 5,
              }}
            >

              <CartesianGrid
                strokeDasharray="3 3"
                stroke="#E5E7EB"
                vertical={false}
              />

              <XAxis
                dataKey="name"
                tick={{
                  fontSize: 10,
                  fill: '#6B7280',
                }}
                axisLine={false}
                tickLine={false}
              />

              <YAxis
                allowDecimals={false}
                tick={{
                  fontSize: 10,
                  fill: '#9CA3AF',
                }}
                axisLine={false}
                tickLine={false}
              />

              <Tooltip
                formatter={(value: number) =>
                  formatNumber(Number(value || 0))
                }
                contentStyle={{
                  borderRadius: 12,
                  border: '1px solid #E5E7EB',
                  boxShadow:
                    '0 10px 30px rgba(0,0,0,0.08)',
                }}
              />

              <Bar
                dataKey="count"
                fill={GREEN}
                radius={[6, 6, 0, 0]}
                name="Loans"
              />

            </BarChart>

          </ResponsiveContainer>

        ) : (

          <div className="h-[280px] flex flex-col items-center justify-center">

            <div className="text-3xl mb-3">
              📊
            </div>

            <p className="text-sm font-medium text-gray-500">
              No loan data yet
            </p>

            <p className="text-xs text-gray-400 mt-1">
              Loan analytics will appear here.
            </p>

          </div>

        )}

      </CardBody>

    </Card>


    {/* ----------------------------------------------------
        STATUS DISTRIBUTION
    ---------------------------------------------------- */}

    <Card>

      <CardHeader
        title="Loan Status"
        action={
          <span className="text-xs text-gray-400">
            Distribution
          </span>
        }
      />

      <CardBody>

        {pieData.length > 0 ? (

          <ResponsiveContainer
            width="100%"
            height={280}
          >

            <PieChart>

              <Pie
                data={pieData}
                dataKey="value"
                nameKey="name"
                cx="50%"
                cy="45%"
                outerRadius={82}
                innerRadius={48}
                paddingAngle={3}
              >

                {pieData.map(
                  (_, index) => (
                    <Cell
                      key={`status-${index}`}
                      fill={
                        COLORS[
                          index %
                            COLORS.length
                        ]
                      }
                    />
                  )
                )}

              </Pie>

              <Tooltip />

              <Legend
                iconSize={8}
                wrapperStyle={{
                  fontSize: 11,
                }}
              />

            </PieChart>

          </ResponsiveContainer>

        ) : (

          <div className="h-[280px] flex items-center justify-center text-sm text-gray-400">
            No loan status data
          </div>

        )}

      </CardBody>

    </Card>

  </div>


  {/* ======================================================
      COLLECTION PERFORMANCE
  ====================================================== */}

  <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">

    <Card className="lg:col-span-2">

      <CardHeader
        title="Collections Performance"
        action={
          <span className="text-xs text-gray-400">
            Current portfolio
          </span>
        }
      />

      <CardBody>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">

          <div className="rounded-xl bg-green-50 p-4">
            <p className="text-xs font-medium text-green-700">
              Total Collected
            </p>

            <p className="text-lg font-extrabold text-green-900 mt-1">
              {fc(stats.totalCollected)}
            </p>
          </div>

          <div className="rounded-xl bg-yellow-50 p-4">
            <p className="text-xs font-medium text-yellow-700">
              This Month
            </p>

            <p className="text-lg font-extrabold text-yellow-900 mt-1">
              {fc(stats.collectedThisMonth)}
            </p>
          </div>

          <div className="rounded-xl bg-gray-50 p-4">
            <p className="text-xs font-medium text-gray-600">
              Outstanding
            </p>

            <p className="text-lg font-extrabold text-gray-900 mt-1">
              {fc(stats.outstandingBalance)}
            </p>
          </div>

          <div className="rounded-xl bg-red-50 p-4">
            <p className="text-xs font-medium text-red-600">
              Overdue
            </p>

            <p className="text-lg font-extrabold text-red-900 mt-1">
              {formatNumber(
                stats.overdueLoans || 0
              )}
            </p>
          </div>

        </div>

      </CardBody>

    </Card>


    <Card>

      <CardHeader title="Quick Actions" />

      <CardBody>

        <div className="space-y-2">

          <button
            type="button"
            onClick={() =>
              router.push('/dashboard/loans')
            }
            className="w-full flex items-center gap-3 rounded-xl border border-gray-100 px-4 py-3 text-left hover:bg-green-50 hover:border-green-200 transition"
          >
            <span className="w-9 h-9 rounded-lg bg-green-100 flex items-center justify-center">
              💼
            </span>

            <span>
              <span className="block text-sm font-semibold text-gray-900">
                Manage Loans
              </span>

              <span className="block text-xs text-gray-400">
                Review loan portfolio
              </span>
            </span>
          </button>


          <button
            type="button"
            onClick={() =>
              router.push('/dashboard/borrowers')
            }
            className="w-full flex items-center gap-3 rounded-xl border border-gray-100 px-4 py-3 text-left hover:bg-yellow-50 hover:border-yellow-200 transition"
          >
            <span className="w-9 h-9 rounded-lg bg-yellow-100 flex items-center justify-center">
              👥
            </span>

            <span>
              <span className="block text-sm font-semibold text-gray-900">
                Borrowers
              </span>

              <span className="block text-xs text-gray-400">
                Manage customer profiles
              </span>
            </span>
          </button>


          <button
            type="button"
            onClick={() =>
              router.push('/dashboard/payments')
            }
            className="w-full flex items-center gap-3 rounded-xl border border-gray-100 px-4 py-3 text-left hover:bg-green-50 hover:border-green-200 transition"
          >
            <span className="w-9 h-9 rounded-lg bg-green-100 flex items-center justify-center">
              💳
            </span>

            <span>
              <span className="block text-sm font-semibold text-gray-900">
                Payments
              </span>

              <span className="block text-xs text-gray-400">
                Track collections
              </span>
            </span>
          </button>

        </div>

      </CardBody>

    </Card>

  </div>


  {/* ======================================================
      RECENT LOANS
  ====================================================== */}

  <Card>

    <CardHeader
      title="Recent Loan Applications"
      action={
        <Button
          variant="ghost"
          size="sm"
          onClick={() =>
            router.push('/dashboard/loans')
          }
        >
          See all →
        </Button>
      }
    />

    <Table>

      <Thead>

        <tr>

          <Th>Reference</Th>
          <Th>Borrower</Th>
          <Th>Type</Th>
          <Th>Amount</Th>
          <Th>Rate</Th>
          <Th>Risk</Th>
          <Th>Status</Th>
          <Th>Applied</Th>

        </tr>

      </Thead>

      <Tbody>

        {recentLoans.length === 0 ? (

          <Tr>

            <Td className="text-center py-12 text-gray-400">

              <div className="flex flex-col items-center">

                <span className="text-3xl mb-2">
                  💼
                </span>

                <span className="text-sm font-medium">
                  No loan applications yet
                </span>

                <span className="text-xs mt-1">
                  New applications will appear here.
                </span>

              </div>

            </Td>

          </Tr>

        ) : (

          recentLoans.map(
            (loan: Loan) => (

              <Tr
                key={loan.id}
                onClick={() =>
                  router.push(
                    `/dashboard/loans/${loan.id}`
                  )
                }
              >

                <Td>

                  <code className="text-xs bg-gray-100 px-2 py-1 rounded-md font-mono">
                    {loan.referenceNumber}
                  </code>

                </Td>


                <Td>

                  <div className="flex items-center gap-3">

                    <div className="w-8 h-8 rounded-full bg-green-100 text-green-700 flex items-center justify-center text-xs font-bold">

                      {(
                        loan.borrower?.firstName
                          ?.charAt(0) || ''
                      ).toUpperCase()}

                      {(
                        loan.borrower?.lastName
                          ?.charAt(0) || ''
                      ).toUpperCase()}

                    </div>

                    <div>

                      <div className="font-semibold text-gray-900 text-sm">
                        {loan.borrower?.firstName}{' '}
                        {loan.borrower?.lastName}
                      </div>

                      <div className="text-xs text-gray-400">
                        {loan.borrower?.nationalId ||
                          loan.borrower?.email ||
                          'Borrower'}
                      </div>

                    </div>

                  </div>

                </Td>


                <Td>

                  <span className="text-xs font-medium">

                    {LOAN_TYPE_META[
                      loan.loanType
                    ]?.icon}{' '}

                    {LOAN_TYPE_META[
                      loan.loanType
                    ]?.label ??
                      loan.loanType}

                  </span>

                </Td>


                <Td>

                  <span className="font-bold text-gray-900">
                    {fc(loan.amount)}
                  </span>

                </Td>


                <Td className="text-gray-500">
                  {loan.interestRate}%
                </Td>


                <Td>

                  {loan.riskCategory ? (

                    <RiskBadge
                      category={
                        loan.riskCategory
                      }
                      score={
                        loan.riskScore
                      }
                    />

                  ) : (

                    <span className="text-gray-400">
                      —
                    </span>

                  )}

                </Td>


                <Td>

                  <StatusBadge
                    status={loan.status}
                  />

                </Td>


                <Td className="text-gray-400 text-xs">

                  {formatDate(
                    loan.startDate ||
                      loan.createdAt,
                    locale
                  )}

                </Td>

              </Tr>

            )
          )

        )}

      </Tbody>

    </Table>

  </Card>


  {/* ======================================================
      FOOTER
  ====================================================== */}

  <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 px-1">

    <p className="text-xs text-gray-400">
      Growth Finance · Loan Management Platform
    </p>

    <p className="text-xs text-gray-400">
      Portfolio data is updated from your organization account.
    </p>

  </div>

</div>


);
}
