'use client';

import { useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
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

import { loanApi } from '@/services/api';
import { DashboardStats, Loan } from '@/types';

import {
StatCard,
Card,
CardHeader,
CardBody,
} from '@/components/ui/Card';

import {
StatusBadge,
RiskBadge,
} from '@/components/ui/Badge';

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

/* ============================================================
GROWTH FINANCE BRAND
============================================================ */

const BRAND = {
green: '#0D9488',
greenDark: '#0F766E',
greenLight: '#CCFBF1',
yellow: '#F59E0B',
yellowLight: '#FEF3C7',
red: '#EF4444',
blue: '#3B82F6',
purple: '#8B5CF6',
};

/* ============================================================
CHART COLORS
============================================================ */

const CHART_COLORS = [
'#0D9488',
'#F59E0B',
'#3B82F6',
'#EF4444',
'#8B5CF6',
'#EC4899',
];

/* ============================================================
DASHBOARD
============================================================ */

export default function DashboardPage() {

const [stats, setStats] = useState<DashboardStats | null>(null);
const [loading, setLoading] = useState(true);
const [error, setError] = useState('');

const {
user,
currency,
locale,
} = useAuth();

const router = useRouter();

const fc = (value?: number) =>
formatCurrency(value, currency, locale);

/* ==========================================================
LOAD DASHBOARD
========================================================== */

useEffect(() => {


let mounted = true;

setLoading(true);
setError('');

loanApi.dashboard()
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

    if (mounted) {
      setLoading(false);
    }

  });

return () => {
  mounted = false;
};


}, []);

/* ==========================================================
LOADING
========================================================== */

if (loading) {


return (
  <div className="min-h-[70vh] flex items-center justify-center">

    <div className="flex flex-col items-center gap-4">

      <div
        className="
          w-11 h-11
          border-4
          border-teal-100
          border-t-teal-600
          rounded-full
          animate-spin
        "
      />

      <div className="text-center">

        <p className="text-sm font-semibold text-gray-700">
          Preparing your dashboard
        </p>

        <p className="text-xs text-gray-400 mt-1">
          Loading portfolio information...
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


return (
  <div className="max-w-2xl mx-auto py-16">

    <Card>

      <CardBody>

        <div className="text-center">

          <div
            className="
              w-14 h-14
              mx-auto
              rounded-full
              bg-red-50
              flex items-center justify-center
              text-2xl
            "
          >
            ⚠️
          </div>

          <h2 className="mt-4 text-lg font-bold text-gray-900">
            Dashboard unavailable
          </h2>

          <p className="mt-2 text-sm text-gray-500">
            {error}
          </p>

          <Button
            className="mt-5"
            onClick={() => window.location.reload()}
          >
            Try Again
          </Button>

        </div>

      </CardBody>

    </Card>

  </div>
);


}

if (!stats) return null;

/* ==========================================================
CALCULATIONS
========================================================== */

const portfolioAtRisk =
stats.totalDisbursed > 0
? (
(stats.outstandingBalance /
stats.totalDisbursed) *
100
)
: 0;

const collectionRate =
stats.totalDisbursed > 0
? (
(stats.totalCollected /
stats.totalDisbursed) *
100
)
: 0;

const activePortfolio =
stats.outstandingBalance || 0;

/* ==========================================================
PIE DATA
========================================================== */

const pieData = [
{
name: 'Active',
value: stats.activeLoans || 0,
},
{
name: 'Pending',
value: stats.pendingLoans || 0,
},
{
name: 'Overdue',
value: stats.overdueLoans || 0,
},
{
name: 'Completed',
value: stats.completedLoans || 0,
},
{
name: 'Defaulted',
value: stats.defaultedLoans || 0,
},
].filter(item => item.value > 0);

/* ==========================================================
LOAN TYPE DATA
========================================================== */

const typeData = (
stats.loanTypeBreakdown || []
).map(item => ({


name:
  LOAN_TYPE_META[String(item.type)]?.label ??
  String(item.type),

count:
  Number(item.count) || 0,

amount:
  Number(item.amount) || 0,


}));

/* ==========================================================
RECENT LOANS
========================================================== */

const recentLoans = useMemo(
() => stats.recentLoans || [],
[stats.recentLoans]
);

/* ==========================================================
DATE
========================================================== */

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
DASHBOARD
========================================================== */

return (


<div className="space-y-6 pb-10">


  {/* ======================================================
      HERO HEADER
      ====================================================== */}

  <section
    className="
      relative
      overflow-hidden
      rounded-2xl
      border
      border-teal-100
      bg-white
      shadow-sm
    "
  >

    <div
      className="
        absolute
        top-0
        right-0
        w-72
        h-72
        rounded-full
        bg-teal-50
        blur-3xl
        opacity-70
        pointer-events-none
      "
    />

    <div
      className="
        absolute
        bottom-0
        right-32
        w-40
        h-40
        rounded-full
        bg-yellow-50
        blur-3xl
        opacity-70
        pointer-events-none
      "
    />

    <div className="relative p-6 lg:p-7">

      <div
        className="
          flex
          flex-col
          lg:flex-row
          lg:items-center
          lg:justify-between
          gap-5
        "
      >

        <div>

          <div className="flex items-center gap-2 mb-2">

            <span
              className="
                inline-flex
                items-center
                gap-1.5
                px-2.5
                py-1
                rounded-full
                bg-teal-50
                text-teal-700
                text-xs
                font-bold
              "
            >
              <span className="w-1.5 h-1.5 rounded-full bg-teal-500" />
              Portfolio Overview
            </span>

          </div>

          <h1
            className="
              text-2xl
              lg:text-3xl
              font-extrabold
              tracking-tight
              text-gray-900
            "
          >
            Welcome back, {user?.name || 'there'}
          </h1>

          <p className="mt-1.5 text-sm text-gray-500">

            {user?.organizationName || 'Growth Finance'}

            <span className="mx-2 text-gray-300">
              •
            </span>

            {today}

          </p>

        </div>


        <div className="flex flex-wrap gap-2">

          <Button
            variant="ghost"
            onClick={() =>
              router.push('/dashboard/borrowers')
            }
          >
            👥 Borrowers
          </Button>

          <Button
            onClick={() =>
              router.push('/dashboard/loans')
            }
          >
            💼 View Loans
          </Button>

        </div>

      </div>


      {/* ==================================================
          PORTFOLIO SUMMARY
          ================================================== */}

      <div
        className="
          grid
          grid-cols-1
          sm:grid-cols-3
          gap-3
          mt-7
        "
      >

        <div
          className="
            rounded-xl
            border
            border-gray-100
            bg-gray-50/70
            px-4
            py-3.5
          "
        >

          <p className="text-xs font-medium text-gray-500">
            Active Portfolio
          </p>

          <p className="mt-1 text-lg font-extrabold text-gray-900">
            {fc(activePortfolio)}
          </p>

        </div>


        <div
          className="
            rounded-xl
            border
            border-gray-100
            bg-gray-50/70
            px-4
            py-3.5
          "
        >

          <p className="text-xs font-medium text-gray-500">
            Collection Rate
          </p>

          <p className="mt-1 text-lg font-extrabold text-teal-700">
            {collectionRate.toFixed(1)}%
          </p>

        </div>


        <div
          className="
            rounded-xl
            border
            border-gray-100
            bg-gray-50/70
            px-4
            py-3.5
          "
        >

          <p className="text-xs font-medium text-gray-500">
            Portfolio at Risk
          </p>

          <p
            className={`
              mt-1
              text-lg
              font-extrabold
              ${
                portfolioAtRisk > 5
                  ? 'text-red-600'
                  : 'text-teal-700'
              }
            `}
          >
            {portfolioAtRisk.toFixed(1)}%
          </p>

        </div>

      </div>

    </div>

  </section>


  {/* ======================================================
      KPI GRID
      ====================================================== */}

  <section>

    <div className="flex items-center justify-between mb-3">

      <div>

        <h2 className="text-sm font-bold text-gray-900">
          Portfolio Performance
        </h2>

        <p className="text-xs text-gray-400 mt-0.5">
          Key lending and collection indicators
        </p>

      </div>

    </div>


    <div
      className="
        grid
        grid-cols-2
        lg:grid-cols-4
        gap-4
      "
    >

      <StatCard
        icon="💼"
        label="Total Loans"
        value={formatNumber(stats.totalLoans)}
        sub={`${formatNumber(stats.activeLoans)} active`}
        color={BRAND.green}
      />

      <StatCard
        icon="👥"
        label="Total Borrowers"
        value={formatNumber(stats.totalBorrowers)}
        sub="Registered clients"
        color={BRAND.yellow}
      />

      <StatCard
        icon="💰"
        label="Total Disbursed"
        value={fc(stats.totalDisbursed)}
        sub="Loan capital released"
        color={BRAND.green}
      />

      <StatCard
        icon="📊"
        label="Outstanding"
        value={fc(stats.outstandingBalance)}
        sub="Current portfolio balance"
        color="#6366F1"
      />

      <StatCard
        icon="✅"
        label="Total Collected"
        value={fc(stats.totalCollected)}
        sub={`${fc(stats.collectedThisMonth)} this month`}
        color={BRAND.green}
      />

      <StatCard
        icon="⏳"
        label="Pending Review"
        value={formatNumber(stats.pendingLoans)}
        sub="Awaiting action"
        color={BRAND.yellow}
      />

      <StatCard
        icon="⚠️"
        label="Overdue Loans"
        value={formatNumber(stats.overdueLoans)}
        sub={`${formatNumber(stats.latePaymentsCount)} late payments`}
        color={BRAND.red}
      />

      <StatCard
        icon="🎯"
        label="Portfolio at Risk"
        value={`${portfolioAtRisk.toFixed(1)}%`}
        sub={
          portfolioAtRisk > 5
            ? 'Needs attention'
            : 'Healthy range'
        }
        color={
          portfolioAtRisk > 5
            ? BRAND.red
            : BRAND.green
        }
      />

    </div>

  </section>


  {/* ======================================================
      ANALYTICS
      ====================================================== */}

  <section
    className="
      grid
      grid-cols-1
      lg:grid-cols-3
      gap-4
    "
  >


    {/* ====================================================
        LOAN TYPE
        ==================================================== */}

    <Card className="lg:col-span-2">

      <CardHeader
        title="Portfolio by Loan Type"
      />

      <CardBody>

        {typeData.length > 0 ? (

          <ResponsiveContainer
            width="100%"
            height={280}
          >

            <BarChart
              data={typeData}
              barSize={24}
              margin={{
                top: 10,
                right: 10,
                left: -15,
                bottom: 5,
              }}
            >

              <CartesianGrid
                strokeDasharray="3 3"
                stroke="#F3F4F6"
                vertical={false}
              />

              <XAxis
                dataKey="name"
                tick={{
                  fontSize: 10,
                  fill: '#9CA3AF',
                }}
                axisLine={false}
                tickLine={false}
              />

              <YAxis
                tick={{
                  fontSize: 10,
                  fill: '#9CA3AF',
                }}
                axisLine={false}
                tickLine={false}
              />

              <Tooltip
                contentStyle={{
                  borderRadius: 12,
                  border: '1px solid #E5E7EB',
                  boxShadow:
                    '0 10px 25px rgba(0,0,0,0.08)',
                }}
              />

              <Bar
                dataKey="count"
                fill={BRAND.green}
                radius={[6, 6, 0, 0]}
                name="Loans"
              />

            </BarChart>

          </ResponsiveContainer>

        ) : (

          <div
            className="
              h-[280px]
              flex
              items-center
              justify-center
              text-sm
              text-gray-400
            "
          >
            No loan portfolio data yet
          </div>

        )}

      </CardBody>

    </Card>


    {/* ====================================================
        STATUS DISTRIBUTION
        ==================================================== */}

    <Card>

      <CardHeader
        title="Loan Status"
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
                innerRadius={46}
                paddingAngle={3}
              >

                {pieData.map((_, index) => (

                  <Cell
                    key={`status-${index}`}
                    fill={
                      CHART_COLORS[
                        index %
                        CHART_COLORS.length
                      ]
                    }
                  />

                ))}

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

          <div
            className="
              h-[280px]
              flex
              items-center
              justify-center
              text-sm
              text-gray-400
            "
          >
            No loan status data
          </div>

        )}

      </CardBody>

    </Card>

  </section>


  {/* ======================================================
      PORTFOLIO HEALTH
      ====================================================== */}

  <section
    className="
      grid
      grid-cols-1
      md:grid-cols-3
      gap-4
    "
  >

    <Card>

      <CardBody>

        <div className="flex items-start justify-between">

          <div>

            <p className="text-xs font-semibold text-gray-500">
              Active Loans
            </p>

            <p className="text-2xl font-extrabold text-gray-900 mt-1">
              {formatNumber(stats.activeLoans)}
            </p>

          </div>

          <div
            className="
              w-10 h-10
              rounded-xl
              bg-teal-50
              flex items-center justify-center
              text-lg
            "
          >
            📈
          </div>

        </div>

        <div className="mt-4">

          <div className="h-2 bg-gray-100 rounded-full overflow-hidden">

            <div
              className="h-full bg-teal-500 rounded-full"
              style={{
                width: `${Math.min(
                  100,
                  stats.totalLoans > 0
                    ? (stats.activeLoans /
                        stats.totalLoans) *
                      100
                    : 0
                )}%`,
              }}
            />

          </div>

          <p className="text-xs text-gray-400 mt-2">
            Portion of total loan book
          </p>

        </div>

      </CardBody>

    </Card>


    <Card>

      <CardBody>

        <div className="flex items-start justify-between">

          <div>

            <p className="text-xs font-semibold text-gray-500">
              Collection This Month
            </p>

            <p className="text-2xl font-extrabold text-gray-900 mt-1">
              {fc(stats.collectedThisMonth)}
            </p>

          </div>

          <div
            className="
              w-10 h-10
              rounded-xl
              bg-yellow-50
              flex items-center justify-center
              text-lg
            "
          >
            💰
          </div>

        </div>

        <p className="text-xs text-gray-400 mt-4">
          Total collections received during the current month
        </p>

      </CardBody>

    </Card>


    <Card>

      <CardBody>

        <div className="flex items-start justify-between">

          <div>

            <p className="text-xs font-semibold text-gray-500">
              Credit Risk
            </p>

            <p
              className={`
                text-2xl
                font-extrabold
                mt-1
                ${
                  portfolioAtRisk > 5
                    ? 'text-red-600'
                    : 'text-teal-700'
                }
              `}
            >
              {portfolioAtRisk.toFixed(1)}%
            </p>

          </div>

          <div
            className={`
              w-10 h-10
              rounded-xl
              flex items-center justify-center
              text-lg
              ${
                portfolioAtRisk > 5
                  ? 'bg-red-50'
                  : 'bg-teal-50'
              }
            `}
          >
            🎯
          </div>

        </div>

        <p className="text-xs text-gray-400 mt-4">
          Outstanding balance relative to total disbursements
        </p>

      </CardBody>

    </Card>

  </section>


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

            <Td
              className="
                text-center
                py-12
                text-gray-400
              "
            >
              No loan applications yet
            </Td>

          </Tr>

        ) : (

          recentLoans.map((loan: Loan) => (

            <Tr
              key={loan.id}
              onClick={() =>
                router.push(
                  `/dashboard/loans/${loan.id}`
                )
              }
            >

              <Td>

                <code
                  className="
                    text-xs
                    bg-gray-100
                    px-2
                    py-1
                    rounded-md
                    font-mono
                    text-gray-600
                  "
                >
                  {loan.referenceNumber}
                </code>

              </Td>


              <Td>

                <div className="font-semibold text-gray-900 text-sm">

                  {loan.borrower?.firstName || ''}{' '}

                  {loan.borrower?.lastName || ''}

                </div>

                <div className="text-xs text-gray-400 mt-0.5">

                  {loan.borrower?.nationalId || 'No ID'}

                </div>

              </Td>


              <Td>

                <span className="text-xs font-medium">

                  {LOAN_TYPE_META[loan.loanType]?.icon}{' '}

                  {LOAN_TYPE_META[
                    loan.loanType
                  ]?.label ?? loan.loanType}

                </span>

              </Td>


              <Td>

                <span className="font-bold text-gray-900">

                  {fc(loan.amount)}

                </span>

              </Td>


              <Td className="text-gray-500 text-sm">

                {loan.interestRate}%

              </Td>


              <Td>

                {loan.riskCategory ? (

                  <RiskBadge
                    category={loan.riskCategory}
                    score={loan.riskScore}
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

          ))

        )}

      </Tbody>

    </Table>

  </Card>


  {/* ======================================================
      QUICK ACTIONS
      ====================================================== */}

  <section>

    <div className="mb-3">

      <h2 className="text-sm font-bold text-gray-900">
        Quick Actions
      </h2>

      <p className="text-xs text-gray-400 mt-0.5">
        Common tasks for your lending team
      </p>

    </div>


    <div
      className="
        grid
        grid-cols-2
        md:grid-cols-4
        gap-3
      "
    >

      <button
        type="button"
        onClick={() =>
          router.push('/dashboard/loans/new')
        }
        className="
          group
          text-left
          rounded-xl
          border
          border-teal-100
          bg-white
          p-4
          hover:border-teal-300
          hover:shadow-md
          transition-all
        "
      >

        <div
          className="
            w-10 h-10
            rounded-xl
            bg-teal-50
            flex
            items-center
            justify-center
            text-lg
            group-hover:scale-105
            transition-transform
          "
        >
          ➕
        </div>

        <p className="mt-3 text-sm font-bold text-gray-900">
          New Loan
        </p>

        <p className="mt-1 text-xs text-gray-400">
          Create an application
        </p>

      </button>


      <button
        type="button"
        onClick={() =>
          router.push('/dashboard/borrowers')
        }
        className="
          group
          text-left
          rounded-xl
          border
          border-yellow-100
          bg-white
          p-4
          hover:border-yellow-300
          hover:shadow-md
          transition-all
        "
      >

        <div
          className="
            w-10 h-10
            rounded-xl
            bg-yellow-50
            flex
            items-center
            justify-center
            text-lg
            group-hover:scale-105
            transition-transform
          "
        >
          👥
        </div>

        <p className="mt-3 text-sm font-bold text-gray-900">
          Borrowers
        </p>

        <p className="mt-1 text-xs text-gray-400">
          Manage client profiles
        </p>

      </button>


      <button
        type="button"
        onClick={() =>
          router.push('/dashboard/payments')
        }
        className="
          group
          text-left
          rounded-xl
          border
          border-gray-100
          bg-white
          p-4
          hover:border-teal-200
          hover:shadow-md
          transition-all
        "
      >

        <div
          className="
            w-10 h-10
            rounded-xl
            bg-gray-50
            flex
            items-center
            justify-center
            text-lg
            group-hover:scale-105
            transition-transform
          "
        >
          💳
        </div>

        <p className="mt-3 text-sm font-bold text-gray-900">
          Payments
        </p>

        <p className="mt-1 text-xs text-gray-400">
          Track collections
        </p>

      </button>


      <button
        type="button"
        onClick={() =>
          router.push('/dashboard/reports')
        }
        className="
          group
          text-left
          rounded-xl
          border
          border-gray-100
          bg-white
          p-4
          hover:border-teal-200
          hover:shadow-md
          transition-all
        "
      >

        <div
          className="
            w-10 h-10
            rounded-xl
            bg-gray-50
            flex
            items-center
            justify-center
            text-lg
            group-hover:scale-105
            transition-transform
          "
        >
          📑
        </div>

        <p className="mt-3 text-sm font-bold text-gray-900">
          Reports
        </p>

        <p className="mt-1 text-xs text-gray-400">
          Portfolio intelligence
        </p>

      </button>

    </div>

  </section>

</div>


);
}
