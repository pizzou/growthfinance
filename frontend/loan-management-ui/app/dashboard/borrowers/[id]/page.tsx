'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';

import { borrowerApi } from '@/services/api';

import {
  BorrowerDetails,
  BorrowerLoanSummary,
  BorrowerPayment,
} from '@/types';

import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';

import {
  formatCurrency,
  formatDate,
  formatNumber,
} from '@/lib/utils';

import { useAuth } from '@/hooks/useAuth';

export default function BorrowerDetailsPage() {
  const params = useParams();
  const router = useRouter();

  const { currency, locale } = useAuth();

  const borrowerId = Number(params?.id);

  const [details, setDetails] =
    useState<BorrowerDetails | null>(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!borrowerId || Number.isNaN(borrowerId)) {
      setError('Invalid borrower ID.');
      setLoading(false);
      return;
    }

    let cancelled = false;

    const load = async () => {
      try {
        setLoading(true);
        setError('');

        const response =
          await borrowerApi.getDetails(borrowerId);

        if (!cancelled) {
          setDetails(response as BorrowerDetails);
        }
      } catch (err: any) {
        console.error(
          'Failed to load borrower details:',
          err,
        );

        if (!cancelled) {
          setError(
            err?.message ||
              'Failed to load borrower details.',
          );
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    load();

    return () => {
      cancelled = true;
    };
  }, [borrowerId]);

  if (loading) {
    return (
      <div className="min-h-[500px] flex items-center justify-center">
        <div className="flex flex-col items-center gap-4">
          <div className="w-10 h-10 rounded-full border-[3px] border-slate-200 border-t-teal-600 animate-spin" />

          <span className="text-sm font-medium text-slate-500">
            Loading borrower profile...
          </span>
        </div>
      </div>
    );
  }

  if (error || !details) {
    return (
      <div className="max-w-2xl mx-auto py-12">
        <Card>
          <div className="p-10 text-center">
            <div className="mx-auto mb-5 w-14 h-14 rounded-2xl bg-red-50 flex items-center justify-center">
              <span className="text-red-600 text-2xl font-bold">
                !
              </span>
            </div>

            <h2 className="text-xl font-bold text-slate-900">
              Unable to load borrower
            </h2>

            <p className="mt-2 text-sm leading-6 text-slate-500">
              {error ||
                'Borrower details could not be loaded.'}
            </p>

            <div className="mt-7 flex justify-center gap-3">
              <Button
                variant="secondary"
                onClick={() =>
                  router.push(
                    '/dashboard/borrowers',
                  )
                }
              >
                Back to Borrowers
              </Button>

              <Button
                onClick={() =>
                  window.location.reload()
                }
              >
                Try Again
              </Button>
            </div>
          </div>
        </Card>
      </div>
    );
  }

  const loans = details.loans ?? [];
  const payments = details.payments ?? [];

  const initials =
    `${details.firstName?.[0] ?? ''}${details.lastName?.[0] ?? ''}`
      .toUpperCase();

  const displayName =
    details.fullName ||
    `${details.firstName ?? ''} ${details.lastName ?? ''}`.trim() ||
    'Unnamed Borrower';

  return (
    <div className="min-h-screen bg-slate-50/70 -m-6 p-6">
      <div className="max-w-[1600px] mx-auto space-y-6">

        {/* =====================================================
            TOP NAVIGATION
        ===================================================== */}

        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <button
              type="button"
              onClick={() =>
                router.push(
                  '/dashboard/borrowers',
                )
              }
              className="inline-flex items-center gap-2 text-sm font-semibold text-slate-500 hover:text-teal-700 transition-colors"
            >
              <span className="text-lg">←</span>
              Borrowers
            </button>

            <div className="mt-3">
              <div className="text-xs font-bold uppercase tracking-[0.16em] text-slate-400">
                Borrower Profile
              </div>

              <h1 className="mt-1 text-2xl sm:text-3xl font-bold tracking-tight text-slate-950">
                {displayName}
              </h1>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <Button
              variant="secondary"
              onClick={() =>
                router.push(
                  '/dashboard/borrowers',
                )
              }
            >
              Back
            </Button>
          </div>
        </div>

        {/* =====================================================
            PROFILE HERO
        ===================================================== */}

        <Card>
          <div className="relative overflow-hidden">
            <div className="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-teal-500 via-cyan-500 to-teal-600" />

            <div className="p-6 sm:p-8">
              <div className="flex flex-col xl:flex-row xl:items-center xl:justify-between gap-7">

                <div className="flex items-start gap-5">
                  <div className="relative flex-shrink-0">
                    <div className="w-20 h-20 sm:w-24 sm:h-24 rounded-3xl bg-gradient-to-br from-teal-500 to-cyan-600 text-white flex items-center justify-center text-2xl sm:text-3xl font-bold shadow-lg shadow-teal-500/20">
                      {initials ||
                        displayName[0] ||
                        '?'}
                    </div>

                    <div className="absolute -right-1 -bottom-1 w-6 h-6 rounded-full bg-white flex items-center justify-center">
                      <div className="w-3.5 h-3.5 rounded-full bg-emerald-500 ring-2 ring-white" />
                    </div>
                  </div>

                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="text-xl sm:text-2xl font-bold text-slate-950">
                        {displayName}
                      </h2>

                      {details.status && (
                        <StatusBadge
                          value={details.status}
                        />
                      )}
                    </div>

                    <p className="mt-1 text-sm text-slate-500">
                      Borrower #{details.borrowerId}
                    </p>

                    <div className="flex flex-wrap gap-2 mt-4">
                      {details.goodPayer && (
                        <Badge
                          text="Good Payer"
                          type="success"
                        />
                      )}

                      {details.currentlyOverdue && (
                        <Badge
                          text="Currently Overdue"
                          type="danger"
                        />
                      )}

                      {details.hasDefaultHistory && (
                        <Badge
                          text="Default History"
                          type="warning"
                        />
                      )}

                      {details.hasMultipleActiveLoans && (
                        <Badge
                          text="Multiple Active Loans"
                          type="warning"
                        />
                      )}
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-8 xl:pr-4">
                  <HeroMetric
                    label="Credit Score"
                    value={
                      details.creditScore != null
                        ? String(
                            details.creditScore,
                          )
                        : '—'
                    }
                    tone={
                      (details.creditScore ?? 0) >=
                      700
                        ? 'good'
                        : (details.creditScore ?? 0) >=
                          600
                        ? 'warning'
                        : 'danger'
                    }
                  />

                  <div className="hidden sm:block w-px h-14 bg-slate-200" />

                  <HeroMetric
                    label="Risk Level"
                    value={
                      details.riskLevel || '—'
                    }
                    tone="neutral"
                  />
                </div>
              </div>
            </div>
          </div>
        </Card>

        {/* =====================================================
            PORTFOLIO SUMMARY
        ===================================================== */}

        <section>
          <SectionHeading
            title="Portfolio Overview"
            subtitle="Current lending relationship and repayment position"
          />

          <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-5 gap-4">
            <MetricCard
              label="Total Loans"
              value={formatNumber(
                details.totalLoans,
              )}
              icon="▣"
            />

            <MetricCard
              label="Active Loans"
              value={formatNumber(
                details.activeLoans,
              )}
              icon="↗"
              tone="teal"
            />

            <MetricCard
              label="Outstanding"
              value={formatCurrency(
                details.totalOutstanding,
                currency,
                locale,
              )}
              icon="◉"
              tone="amber"
            />

            <MetricCard
              label="Total Paid"
              value={formatCurrency(
                details.totalPaid,
                currency,
                locale,
              )}
              icon="✓"
              tone="green"
            />

            <MetricCard
              label="Overdue Loans"
              value={formatNumber(
                details.overdueLoans,
              )}
              icon="!"
              tone={
                details.overdueLoans > 0
                  ? 'red'
                  : 'green'
              }
            />
          </div>
        </section>

        {/* =====================================================
            PERSONAL + FINANCE
        ===================================================== */}

        <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">

          <ProfileCard
            title="Personal Information"
            subtitle="Identity and contact details"
          >
            <InfoGrid>
              <Info
                label="First Name"
                value={details.firstName}
              />

              <Info
                label="Last Name"
                value={details.lastName}
              />

              <Info
                label="Email"
                value={details.email}
              />

              <Info
                label="Phone"
                value={details.phone}
              />

              <Info
                label="Alternate Phone"
                value={details.alternatePhone}
              />

              <Info
                label="National ID"
                value={details.nationalId}
                mono
              />

              <Info
                label="Passport"
                value={
                  details.passportNumber
                }
              />

              <Info
                label="Date of Birth"
                value={
                  details.dateOfBirth
                    ? formatDate(
                        details.dateOfBirth,
                        locale,
                      )
                    : undefined
                }
              />

              <Info
                label="Gender"
                value={details.gender}
              />

              <Info
                label="Marital Status"
                value={
                  details.maritalStatus
                }
              />

              <Info
                label="Nationality"
                value={details.nationality}
              />

              <Info
                label="Country"
                value={details.country}
              />
            </InfoGrid>
          </ProfileCard>

          <ProfileCard
            title="Employment & Finance"
            subtitle="Income, employment and financial profile"
          >
            <InfoGrid>
              <Info
                label="Employer"
                value={
                  details.employerName
                }
              />

              <Info
                label="Employment Type"
                value={
                  details.employmentType
                }
              />

              <Info
                label="Job Title"
                value={details.jobTitle}
              />

              <Info
                label="Monthly Income"
                value={formatCurrency(
                  details.monthlyIncome,
                  currency,
                  locale,
                )}
              />

              <Info
                label="Monthly Expenses"
                value={formatCurrency(
                  details.monthlyExpenses,
                  currency,
                  locale,
                )}
              />

              <Info
                label="Net Worth"
                value={formatCurrency(
                  details.netWorth,
                  currency,
                  locale,
                )}
              />

              <Info
                label="Credit Bureau"
                value={
                  details.creditBureau
                }
              />

              <Info
                label="Credit Report Date"
                value={
                  details.creditReportDate
                    ? formatDate(
                        details.creditReportDate,
                        locale,
                      )
                    : undefined
                }
              />
            </InfoGrid>
          </ProfileCard>
        </div>

        {/* =====================================================
            ADDRESS
        ===================================================== */}

        <ProfileCard
          title="Address"
          subtitle="Registered residential information"
        >
          <div className="rounded-2xl bg-slate-50 border border-slate-100 p-5">
            <div className="flex items-start gap-4">
              <div className="w-10 h-10 rounded-xl bg-white border border-slate-200 flex items-center justify-center text-slate-500">
                ⌖
              </div>

              <div>
                <div className="text-xs font-bold uppercase tracking-wider text-slate-400">
                  Residential Address
                </div>

                <div className="mt-1 text-sm font-semibold text-slate-800">
                  {details.address ||
                    'No address recorded.'}
                </div>
              </div>
            </div>
          </div>
        </ProfileCard>

        {/* =====================================================
            LOAN STATISTICS
        ===================================================== */}

        <section>
          <SectionHeading
            title="Loan Statistics"
            subtitle="Historical and current portfolio performance"
          />

          <div className="grid grid-cols-2 md:grid-cols-4 xl:grid-cols-6 gap-4">
            <SmallStat
              label="Completed"
              value={details.completedLoans}
            />

            <SmallStat
              label="Defaulted"
              value={details.defaultedLoans}
              danger={
                details.defaultedLoans > 0
              }
            />

            <SmallStat
              label="Written Off"
              value={details.writtenOffLoans}
              danger={
                details.writtenOffLoans > 0
              }
            />

            <SmallStat
              label="Total Borrowed"
              value={formatCurrency(
                details.totalBorrowed,
                currency,
                locale,
              )}
            />

            <SmallStat
              label="Total Disbursed"
              value={formatCurrency(
                details.totalDisbursed,
                currency,
                locale,
              )}
            />

            <SmallStat
              label="Total Payments"
              value={details.totalPayments}
            />
          </div>
        </section>

        {/* =====================================================
            REPAYMENT PERFORMANCE
        ===================================================== */}

        <ProfileCard
          title="Repayment Performance"
          subtitle="Payment behaviour and collection quality"
        >
          <div className="grid grid-cols-2 md:grid-cols-4 gap-y-7 gap-x-6">

            <PerformanceMetric
              label="Repayment Rate"
              value={`${Number(
                details.repaymentRate ?? 0,
              ).toFixed(1)}%`}
              progress={Number(
                details.repaymentRate ?? 0,
              )}
            />

            <PerformanceMetric
              label="On-Time Rate"
              value={`${Number(
                details.onTimePaymentRate ?? 0,
              ).toFixed(1)}%`}
              progress={Number(
                details.onTimePaymentRate ?? 0,
              )}
            />

            <PerformanceMetric
              label="Successful Payments"
              value={formatNumber(
                details.successfulPayments,
              )}
            />

            <PerformanceMetric
              label="Missed Payments"
              value={formatNumber(
                details.missedPayments,
              )}
              danger={
                details.missedPayments > 0
              }
            />

            <PerformanceMetric
              label="Overdue Payments"
              value={formatNumber(
                details.overduePayments,
              )}
              danger={
                details.overduePayments > 0
              }
            />

            <PerformanceMetric
              label="Current Days Past Due"
              value={formatNumber(
                details.currentDaysPastDue,
              )}
              danger={
                details.currentDaysPastDue > 0
              }
            />

            <PerformanceMetric
              label="Maximum Days Past Due"
              value={formatNumber(
                details.maximumDaysPastDue,
              )}
              danger={
                details.maximumDaysPastDue > 0
              }
            />

            <PerformanceMetric
              label="Behaviour"
              value={
                details.repaymentBehaviour ||
                '—'
              }
            />
          </div>
        </ProfileCard>

        {/* =====================================================
            RISK & BEHAVIOUR
        ===================================================== */}

        <ProfileCard
          title="Risk & Behaviour"
          subtitle="Credit risk indicators used by the lending team"
        >
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">

            <RiskBox
              label="Risk Level"
              value={
                details.riskLevel || '—'
              }
              tone="neutral"
            />

            <RiskBox
              label="Repayment Behaviour"
              value={
                details.repaymentBehaviour ||
                '—'
              }
              tone="neutral"
            />

            <RiskBox
              label="Good Payer"
              value={
                details.goodPayer
                  ? 'Yes'
                  : 'No'
              }
              tone={
                details.goodPayer
                  ? 'good'
                  : 'bad'
              }
            />

            <RiskBox
              label="Currently Overdue"
              value={
                details.currentlyOverdue
                  ? 'Yes'
                  : 'No'
              }
              tone={
                details.currentlyOverdue
                  ? 'bad'
                  : 'good'
              }
            />

            <RiskBox
              label="Default History"
              value={
                details.hasDefaultHistory
                  ? 'Yes'
                  : 'No'
              }
              tone={
                details.hasDefaultHistory
                  ? 'bad'
                  : 'good'
              }
            />

            <RiskBox
              label="Multiple Active Loans"
              value={
                details.hasMultipleActiveLoans
                  ? 'Yes'
                  : 'No'
              }
              tone={
                details.hasMultipleActiveLoans
                  ? 'warning'
                  : 'good'
              }
            />
          </div>
        </ProfileCard>

        {/* =====================================================
            LOANS
        ===================================================== */}

        <ProfileCard
          title="Loan Portfolio"
          subtitle="All loans associated with this borrower"
          right={
            <span className="px-3 py-1.5 rounded-full bg-slate-100 text-xs font-bold text-slate-600">
              {loans.length}{' '}
              {loans.length === 1
                ? 'Loan'
                : 'Loans'}
            </span>
          }
        >
          {loans.length === 0 ? (
            <EmptyState message="No loans found for this borrower." />
          ) : (
            <div className="overflow-x-auto -mx-2">
              <table className="w-full min-w-[900px]">
                <thead>
                  <tr className="border-b border-slate-100">
                    <TableHeader>
                      Reference
                    </TableHeader>

                    <TableHeader>
                      Type
                    </TableHeader>

                    <TableHeader>
                      Status
                    </TableHeader>

                    <TableHeader>
                      Amount
                    </TableHeader>

                    <TableHeader>
                      Outstanding
                    </TableHeader>

                    <TableHeader>
                      Rate
                    </TableHeader>

                    <TableHeader>
                      Maturity
                    </TableHeader>
                  </tr>
                </thead>

                <tbody>
                  {loans.map(
                    (
                      loan: BorrowerLoanSummary,
                    ) => (
                      <tr
                        key={loan.loanId}
                        className="border-b border-slate-50 hover:bg-slate-50/70 transition-colors"
                      >
                        <TableCell strong>
                          {loan.referenceNumber ??
                            `#${loan.loanId}`}
                        </TableCell>

                        <TableCell>
                          {loan.loanType ?? '—'}
                        </TableCell>

                        <TableCell>
                          <StatusBadge
                            value={
                              loan.status ?? '—'
                            }
                          />
                        </TableCell>

                        <TableCell>
                          {formatCurrency(
                            loan.loanAmount,
                            loan.currency ??
                              currency,
                            locale,
                          )}
                        </TableCell>

                        <TableCell strong>
                          {formatCurrency(
                            loan.outstandingBalance,
                            loan.currency ??
                              currency,
                            locale,
                          )}
                        </TableCell>

                        <TableCell>
                          {loan.interestRate !=
                          null
                            ? `${loan.interestRate}%`
                            : '—'}
                        </TableCell>

                        <TableCell>
                          {loan.maturityDate
                            ? formatDate(
                                loan.maturityDate,
                                locale,
                              )
                            : '—'}
                        </TableCell>
                      </tr>
                    ),
                  )}
                </tbody>
              </table>
            </div>
          )}
        </ProfileCard>

        {/* =====================================================
            PAYMENT HISTORY
        ===================================================== */}

        <ProfileCard
          title="Payment History"
          subtitle="Complete repayment transaction history"
          right={
            <span className="px-3 py-1.5 rounded-full bg-slate-100 text-xs font-bold text-slate-600">
              {payments.length}{' '}
              {payments.length === 1
                ? 'Payment'
                : 'Payments'}
            </span>
          }
        >
          {payments.length === 0 ? (
            <EmptyState message="No payments found for this borrower." />
          ) : (
            <div className="overflow-x-auto -mx-2">
              <table className="w-full min-w-[1000px]">
                <thead>
                  <tr className="border-b border-slate-100">
                    <TableHeader>
                      Date
                    </TableHeader>

                    <TableHeader>
                      Loan
                    </TableHeader>

                    <TableHeader>
                      Amount
                    </TableHeader>

                    <TableHeader>
                      Principal
                    </TableHeader>

                    <TableHeader>
                      Interest
                    </TableHeader>

                    <TableHeader>
                      Method
                    </TableHeader>

                    <TableHeader>
                      Status
                    </TableHeader>

                    <TableHeader>
                      Late
                    </TableHeader>
                  </tr>
                </thead>

                <tbody>
                  {payments.map(
                    (
                      payment: BorrowerPayment,
                    ) => (
                      <tr
                        key={
                          payment.paymentId
                        }
                        className="border-b border-slate-50 hover:bg-slate-50/70 transition-colors"
                      >
                        <TableCell>
                          {(
                            payment.paidDate ??
                            payment.paymentDate ??
                            payment.dueDate
                          )
                            ? formatDate(
                                payment.paidDate ??
                                  payment.paymentDate ??
                                  payment.dueDate ??
                                  undefined,
                                locale,
                              )
                            : '—'}
                        </TableCell>

                        <TableCell strong>
                          {payment.loanReference ??
                            payment.loanNumber ??
                            `#${payment.loanId}`}
                        </TableCell>

                        <TableCell strong>
                          {formatCurrency(
                            payment.amountPaid ??
                              payment.amount ??
                              payment.totalPaid,
                            payment.currency ??
                              currency,
                            locale,
                          )}
                        </TableCell>

                        <TableCell>
                          {formatCurrency(
                            payment.principalComponent ??
                              payment.principal,
                            payment.currency ??
                              currency,
                            locale,
                          )}
                        </TableCell>

                        <TableCell>
                          {formatCurrency(
                            payment.interestComponent ??
                              payment.interest,
                            payment.currency ??
                              currency,
                            locale,
                          )}
                        </TableCell>

                        <TableCell>
                          {payment.paymentMethod ??
                            payment.method ??
                            '—'}
                        </TableCell>

                        <TableCell>
                          <StatusBadge
                            value={
                              payment.status ??
                              '—'
                            }
                          />
                        </TableCell>

                        <TableCell>
                          {payment.isLate ||
                          payment.onTime ===
                            false ? (
                            <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-red-50 text-red-700 text-xs font-bold">
                              <span className="w-1.5 h-1.5 rounded-full bg-red-500" />
                              Late
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700 text-xs font-bold">
                              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                              On Time
                            </span>
                          )}
                        </TableCell>
                      </tr>
                    ),
                  )}
                </tbody>
              </table>
            </div>
          )}
        </ProfileCard>

        {/* =====================================================
            FOOTER
        ===================================================== */}

        {details.createdAt && (
          <div className="flex items-center justify-between border-t border-slate-200 pt-5 pb-6">
            <span className="text-xs font-medium text-slate-400">
              Borrower registered{' '}
              {formatDate(
                details.createdAt,
                locale,
              )}
            </span>

            <span className="text-xs font-semibold text-slate-400">
              ID #{details.borrowerId}
            </span>
          </div>
        )}
      </div>
    </div>
  );
}

/* =============================================================
   SECTION HEADING
============================================================= */

function SectionHeading({
  title,
  subtitle,
}: {
  title: string;
  subtitle: string;
}) {
  return (
    <div className="mb-4">
      <h2 className="text-lg font-bold text-slate-950">
        {title}
      </h2>

      <p className="text-sm text-slate-500 mt-0.5">
        {subtitle}
      </p>
    </div>
  );
}

/* =============================================================
   PROFILE CARD
============================================================= */

function ProfileCard({
  title,
  subtitle,
  children,
  right,
}: {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  right?: React.ReactNode;
}) {
  return (
    <Card>
      <div className="p-6 sm:p-7">
        <div className="flex items-start justify-between gap-4 mb-6">
          <div>
            <h2 className="text-base font-bold text-slate-950">
              {title}
            </h2>

            {subtitle && (
              <p className="text-xs text-slate-400 mt-1">
                {subtitle}
              </p>
            )}
          </div>

          {right}
        </div>

        {children}
      </div>
    </Card>
  );
}

/* =============================================================
   INFO GRID
============================================================= */

function InfoGrid({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-6">
      {children}
    </div>
  );
}

/* =============================================================
   INFO
============================================================= */

function Info({
  label,
  value,
  mono = false,
}: {
  label: string;
  value?: string | number | null;
  mono?: boolean;
}) {
  const empty =
    value === undefined ||
    value === null ||
    String(value).trim() === '';

  return (
    <div className="min-w-0">
      <div className="text-[10px] uppercase tracking-[0.14em] font-bold text-slate-400">
        {label}
      </div>

      <div
        className={`mt-1.5 text-sm font-semibold break-words ${
          mono
            ? 'font-mono text-slate-700'
            : 'text-slate-800'
        }`}
      >
        {empty ? '—' : String(value)}
      </div>
    </div>
  );
}

/* =============================================================
   HERO METRIC
============================================================= */

function HeroMetric({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone:
    | 'good'
    | 'warning'
    | 'danger'
    | 'neutral';
}) {
  const color =
    tone === 'good'
      ? 'text-emerald-600'
      : tone === 'warning'
      ? 'text-amber-600'
      : tone === 'danger'
      ? 'text-red-600'
      : 'text-slate-900';

  return (
    <div>
      <div className="text-[10px] uppercase tracking-[0.16em] font-bold text-slate-400">
        {label}
      </div>

      <div
        className={`mt-1 text-2xl sm:text-3xl font-extrabold tracking-tight ${color}`}
      >
        {value}
      </div>
    </div>
  );
}

/* =============================================================
   METRIC CARD
============================================================= */

function MetricCard({
  label,
  value,
  icon,
  tone = 'neutral',
}: {
  label: string;
  value: string;
  icon: string;
  tone?:
    | 'neutral'
    | 'teal'
    | 'amber'
    | 'green'
    | 'red';
}) {
  const iconClass =
    tone === 'teal'
      ? 'bg-teal-50 text-teal-600'
      : tone === 'amber'
      ? 'bg-amber-50 text-amber-600'
      : tone === 'green'
      ? 'bg-emerald-50 text-emerald-600'
      : tone === 'red'
      ? 'bg-red-50 text-red-600'
      : 'bg-slate-100 text-slate-600';

  return (
    <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-sm hover:shadow-md hover:border-slate-300 transition-all">
      <div className="flex items-center justify-between gap-3">
        <div
          className={`w-9 h-9 rounded-xl flex items-center justify-center text-sm font-bold ${iconClass}`}
        >
          {icon}
        </div>
      </div>

      <div className="mt-5">
        <div className="text-[10px] uppercase tracking-[0.13em] font-bold text-slate-400">
          {label}
        </div>

        <div className="mt-1.5 text-xl font-extrabold tracking-tight text-slate-950 truncate">
          {value}
        </div>
      </div>
    </div>
  );
}

/* =============================================================
   SMALL STAT
============================================================= */

function SmallStat({
  label,
  value,
  danger = false,
}: {
  label: string;
  value: string | number;
  danger?: boolean;
}) {
  return (
    <div className="bg-white border border-slate-200 rounded-2xl px-5 py-4 shadow-sm">
      <div className="text-[10px] uppercase tracking-[0.12em] font-bold text-slate-400">
        {label}
      </div>

      <div
        className={`mt-2 text-lg font-extrabold ${
          danger
            ? 'text-red-600'
            : 'text-slate-950'
        }`}
      >
        {value}
      </div>
    </div>
  );
}

/* =============================================================
   PERFORMANCE
============================================================= */

function PerformanceMetric({
  label,
  value,
  progress,
  danger = false,
}: {
  label: string;
  value: string;
  progress?: number;
  danger?: boolean;
}) {
  return (
    <div>
      <div className="text-[10px] uppercase tracking-[0.12em] font-bold text-slate-400">
        {label}
      </div>

      <div
        className={`mt-1.5 text-lg font-extrabold ${
          danger
            ? 'text-red-600'
            : 'text-slate-900'
        }`}
      >
        {value}
      </div>

      {progress !== undefined && (
        <div className="mt-2 h-1.5 bg-slate-100 rounded-full overflow-hidden">
          <div
            className="h-full bg-teal-500 rounded-full transition-all"
            style={{
              width: `${Math.min(
                100,
                Math.max(0, progress),
              )}%`,
            }}
          />
        </div>
      )}
    </div>
  );
}

/* =============================================================
   RISK BOX
============================================================= */

function RiskBox({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone:
    | 'neutral'
    | 'good'
    | 'bad'
    | 'warning';
}) {
  const styles =
    tone === 'good'
      ? 'bg-emerald-50 border-emerald-100 text-emerald-700'
      : tone === 'bad'
      ? 'bg-red-50 border-red-100 text-red-700'
      : tone === 'warning'
      ? 'bg-amber-50 border-amber-100 text-amber-700'
      : 'bg-slate-50 border-slate-100 text-slate-700';

  return (
    <div
      className={`rounded-2xl border p-4 ${styles}`}
    >
      <div className="text-[10px] uppercase tracking-[0.12em] font-bold opacity-60">
        {label}
      </div>

      <div className="mt-1.5 text-sm font-bold">
        {value}
      </div>
    </div>
  );
}

/* =============================================================
   BADGE
============================================================= */

function Badge({
  text,
  type,
}: {
  text: string;
  type:
    | 'success'
    | 'danger'
    | 'warning';
}) {
  const styles =
    type === 'success'
      ? 'bg-emerald-50 text-emerald-700 border-emerald-100'
      : type === 'danger'
      ? 'bg-red-50 text-red-700 border-red-100'
      : 'bg-amber-50 text-amber-700 border-amber-100';

  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-[11px] font-bold ${styles}`}
    >
      <span className="w-1.5 h-1.5 rounded-full bg-current" />
      {text}
    </span>
  );
}

/* =============================================================
   STATUS BADGE
============================================================= */

function StatusBadge({
  value,
}: {
  value: string;
}) {
  const normalized =
    value.toUpperCase();

  let styles =
    'bg-slate-100 text-slate-600 border-slate-200';

  if (
    normalized.includes('ACTIVE') ||
    normalized.includes('APPROVED') ||
    normalized.includes('COMPLETED') ||
    normalized.includes('PAID')
  ) {
    styles =
      'bg-emerald-50 text-emerald-700 border-emerald-100';
  }

  if (
    normalized.includes('PENDING') ||
    normalized.includes('PROCESS')
  ) {
    styles =
      'bg-amber-50 text-amber-700 border-amber-100';
  }

  if (
    normalized.includes('OVERDUE') ||
    normalized.includes('DEFAULT') ||
    normalized.includes('REJECT') ||
    normalized.includes('FAILED')
  ) {
    styles =
      'bg-red-50 text-red-700 border-red-100';
  }

  return (
    <span
      className={`inline-flex items-center px-2.5 py-1 rounded-full border text-[10px] uppercase tracking-wide font-bold ${styles}`}
    >
      {value}
    </span>
  );
}

/* =============================================================
   TABLE
============================================================= */

function TableHeader({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <th className="px-3 py-3 text-left text-[10px] uppercase tracking-[0.12em] font-bold text-slate-400 whitespace-nowrap">
      {children}
    </th>
  );
}

function TableCell({
  children,
  strong = false,
}: {
  children: React.ReactNode;
  strong?: boolean;
}) {
  return (
    <td
      className={`px-3 py-4 text-sm whitespace-nowrap ${
        strong
          ? 'font-bold text-slate-800'
          : 'font-medium text-slate-600'
      }`}
    >
      {children}
    </td>
  );
}

/* =============================================================
   EMPTY STATE
============================================================= */

function EmptyState({
  message,
}: {
  message: string;
}) {
  return (
    <div className="py-14 text-center">
      <div className="mx-auto w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center text-slate-400 text-xl">
        —
      </div>

      <div className="mt-4 text-sm font-semibold text-slate-700">
        {message}
      </div>

      <div className="mt-1 text-xs text-slate-400">
        There is currently no information to display.
      </div>
    </div>
  );
}