
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

  const [loading, setLoading] =
    useState(true);

  const [error, setError] =
    useState('');

  /**
   * ============================================================
   * LOAD BORROWER
   * ============================================================
   */

  useEffect(() => {
    if (
      !borrowerId ||
      Number.isNaN(borrowerId)
    ) {
      setError('Invalid borrower ID.');
      setLoading(false);
      return;
    }

    let cancelled = false;

    const load = async () => {
      setLoading(true);
      setError('');

      try {
        const response =
          await borrowerApi.getDetails(
            borrowerId,
          );

        if (!cancelled) {
          setDetails(
            response as BorrowerDetails,
          );
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

  /**
   * ============================================================
   * LOADING
   * ============================================================
   */

  if (loading) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <div className="text-center">

          <div className="mx-auto w-11 h-11 border-[3px] border-teal-500 border-t-transparent rounded-full animate-spin" />

          <p className="mt-4 text-sm text-gray-500">
            Loading borrower profile...
          </p>

        </div>
      </div>
    );
  }

  /**
   * ============================================================
   * ERROR
   * ============================================================
   */

  if (error || !details) {
    return (
      <div className="max-w-3xl mx-auto py-10">

        <button
          type="button"
          onClick={() =>
            router.push(
              '/dashboard/borrowers',
            )
          }
          className="text-sm font-semibold text-gray-500 hover:text-gray-900 mb-5"
        >
          ← Back to borrowers
        </button>

        <Card>
          <div className="py-16 text-center">

            <div className="mx-auto w-14 h-14 rounded-2xl bg-red-50 text-red-500 flex items-center justify-center text-2xl font-bold">
              !
            </div>

            <h2 className="mt-5 text-xl font-bold text-gray-900">
              Unable to load borrower
            </h2>

            <p className="mt-2 text-sm text-gray-500">
              {error ||
                'Borrower details could not be loaded.'}
            </p>

            <div className="mt-6">
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

  const loans =
    details.loans ?? [];

  const payments =
    details.payments ?? [];

  const initials =
    `${details.firstName?.[0] ?? ''}${details.lastName?.[0] ?? ''}`
      .toUpperCase();

  const fullName =
    details.fullName ||
    `${details.firstName ?? ''} ${details.lastName ?? ''}`.trim();

  return (
    <div className="space-y-6 pb-10">

      {/* ======================================================
          TOP NAVIGATION
      ====================================================== */}

      <div className="flex items-center justify-between">

        <button
          type="button"
          onClick={() =>
            router.push(
              '/dashboard/borrowers',
            )
          }
          className="inline-flex items-center gap-2 text-sm font-semibold text-gray-500 hover:text-gray-900 transition"
        >
          <span className="text-lg">
            ←
          </span>

          Borrowers
        </button>

        <div className="text-xs text-gray-400">
          Borrower #{details.borrowerId}
        </div>

      </div>

      {/* ======================================================
          HERO
      ====================================================== */}

      <section className="rounded-2xl bg-white border border-gray-200 shadow-sm overflow-hidden">

        <div className="h-2 bg-teal-500" />

        <div className="p-6 md:p-8">

          <div className="flex flex-col xl:flex-row xl:items-center xl:justify-between gap-7">

            {/* PROFILE */}

            <div className="flex items-start gap-5">

              <div className="w-20 h-20 md:w-24 md:h-24 rounded-2xl bg-teal-50 border border-teal-100 flex items-center justify-center text-2xl md:text-3xl font-extrabold text-teal-700 flex-shrink-0">
                {initials ||
                  fullName?.[0] ||
                  '?'}
              </div>

              <div className="min-w-0">

                <div className="flex flex-wrap items-center gap-2">

                  <h1 className="text-2xl md:text-3xl font-extrabold tracking-tight text-gray-900">
                    {fullName ||
                      'Unnamed Borrower'}
                  </h1>

                  {details.goodPayer && (
                    <span className="px-2.5 py-1 rounded-full bg-teal-50 text-teal-700 text-xs font-bold">
                      Good Payer
                    </span>
                  )}

                  {details.currentlyOverdue && (
                    <span className="px-2.5 py-1 rounded-full bg-red-50 text-red-600 text-xs font-bold">
                      Overdue
                    </span>
                  )}

                </div>

                <div className="mt-2 text-sm text-gray-500">
                  Customer since{' '}
                  {details.createdAt
                    ? formatDate(
                        details.createdAt,
                        locale,
                      )
                    : '—'}
                </div>

                <div className="flex flex-wrap gap-2 mt-4">

                  {details.status && (
                    <Badge
                      label={details.status}
                      type="neutral"
                    />
                  )}

                  {details.riskLevel && (
                    <Badge
                      label={`Risk: ${details.riskLevel}`}
                      type={
                        details.riskLevel
                          ?.toString()
                          .toUpperCase()
                          .includes('HIGH')
                          ? 'danger'
                          : 'neutral'
                      }
                    />
                  )}

                  {details.employmentType && (
                    <Badge
                      label={
                        details.employmentType
                      }
                      type="neutral"
                    />
                  )}

                </div>

              </div>

            </div>

            {/* CREDIT */}

            <div className="xl:min-w-[220px] xl:border-l xl:border-gray-100 xl:pl-8">

              <div className="text-xs uppercase tracking-[0.12em] text-gray-400 font-bold">
                Credit Score
              </div>

              <div className="flex items-end gap-3 mt-1">

                <div
                  className={`text-4xl font-extrabold ${
                    getCreditColor(
                      details.creditScore,
                    )
                  }`}
                >
                  {details.creditScore ??
                    '—'}
                </div>

                {details.creditScore !=
                  null && (
                  <div className="text-xs text-gray-500 pb-1">
                    {getCreditLabel(
                      details.creditScore,
                    )}
                  </div>
                )}

              </div>

              <div className="mt-3 h-2 rounded-full bg-gray-100 overflow-hidden">

                <div
                  className={`h-full rounded-full ${getCreditBarColor(
                    details.creditScore,
                  )}`}
                  style={{
                    width: `${Math.min(
                      100,
                      Math.max(
                        0,
                        ((details.creditScore ??
                          0) -
                          300) /
                          5.5,
                      ),
                    )}%`,
                  }}
                />

              </div>

              <div className="flex justify-between mt-1 text-[10px] text-gray-400">
                <span>300</span>
                <span>850</span>
              </div>

            </div>

          </div>

        </div>

      </section>

      {/* ======================================================
          FINANCIAL KPI GRID
      ====================================================== */}

      <div className="grid grid-cols-2 lg:grid-cols-5 gap-3">

        <KpiCard
          label="Total Loans"
          value={formatNumber(
            details.totalLoans,
          )}
          icon="↗"
        />

        <KpiCard
          label="Active Loans"
          value={formatNumber(
            details.activeLoans,
          )}
          icon="◷"
        />

        <KpiCard
          label="Outstanding"
          value={formatCurrency(
            details.totalOutstanding,
            currency,
            locale,
          )}
          icon="◆"
        />

        <KpiCard
          label="Total Paid"
          value={formatCurrency(
            details.totalPaid,
            currency,
            locale,
          )}
          icon="✓"
        />

        <KpiCard
          label="Repayment Rate"
          value={`${Number(
            details.repaymentRate ?? 0,
          ).toFixed(1)}%`}
          icon="%"
        />

      </div>

      {/* ======================================================
          PROFILE + FINANCE
      ====================================================== */}

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">

        {/* PERSONAL */}

        <SectionCard
          title="Personal Information"
          subtitle="Identity and contact details"
        >

          <InfoGrid>

            <InfoItem
              label="First Name"
              value={
                details.firstName
              }
            />

            <InfoItem
              label="Last Name"
              value={
                details.lastName
              }
            />

            <InfoItem
              label="Email"
              value={
                details.email
              }
            />

            <InfoItem
              label="Phone"
              value={
                details.phone
              }
            />

            <InfoItem
              label="Alternate Phone"
              value={
                details.alternatePhone
              }
            />

            <InfoItem
              label="National ID"
              value={
                details.nationalId
              }
            />

            <InfoItem
              label="Passport"
              value={
                details.passportNumber
              }
            />

            <InfoItem
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

            <InfoItem
              label="Gender"
              value={
                details.gender
              }
            />

            <InfoItem
              label="Marital Status"
              value={
                details.maritalStatus
              }
            />

            <InfoItem
              label="Nationality"
              value={
                details.nationality
              }
            />

            <InfoItem
              label="Country"
              value={
                details.country
              }
            />

          </InfoGrid>

        </SectionCard>

        {/* EMPLOYMENT */}

        <SectionCard
          title="Employment & Finance"
          subtitle="Income and financial profile"
        >

          <InfoGrid>

            <InfoItem
              label="Employer"
              value={
                details.employerName
              }
            />

            <InfoItem
              label="Employment Type"
              value={
                details.employmentType
              }
            />

            <InfoItem
              label="Job Title"
              value={
                details.jobTitle
              }
            />

            <InfoItem
              label="Monthly Income"
              value={formatCurrency(
                details.monthlyIncome,
                currency,
                locale,
              )}
            />

            <InfoItem
              label="Monthly Expenses"
              value={formatCurrency(
                details.monthlyExpenses,
                currency,
                locale,
              )}
            />

            <InfoItem
              label="Net Worth"
              value={formatCurrency(
                details.netWorth,
                currency,
                locale,
              )}
            />

            <InfoItem
              label="Credit Bureau"
              value={
                details.creditBureau
              }
            />

            <InfoItem
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

        </SectionCard>

      </div>

      {/* ======================================================
          ADDRESS
      ====================================================== */}

      <SectionCard
        title="Address"
        subtitle="Registered residential information"
      >

        <div className="rounded-xl bg-gray-50 border border-gray-100 px-4 py-4 text-sm text-gray-700">
          {details.address ||
            'No address recorded.'}
        </div>

      </SectionCard>

      {/* ======================================================
          PORTFOLIO
      ====================================================== */}

      <SectionCard
        title="Loan Portfolio"
        subtitle="Current and historical lending exposure"
        right={
          <span className="text-xs font-semibold text-gray-400">
            {loans.length}{' '}
            {loans.length === 1
              ? 'loan'
              : 'loans'}
          </span>
        }
      >

        {loans.length === 0 ? (
          <EmptyState
            icon="◆"
            title="No loans"
            message="This borrower has no loan records."
          />
        ) : (
          <div className="overflow-x-auto">

            <table className="w-full min-w-[850px]">

              <thead>
                <tr className="border-b border-gray-100">

                  <th className="text-left py-3 px-3 text-[11px] uppercase tracking-wider text-gray-400 font-bold">
                    Loan
                  </th>

                  <th className="text-left py-3 px-3 text-[11px] uppercase tracking-wider text-gray-400 font-bold">
                    Type
                  </th>

                  <th className="text-left py-3 px-3 text-[11px] uppercase tracking-wider text-gray-400 font-bold">
                    Status
                  </th>

                  <th className="text-right py-3 px-3 text-[11px] uppercase tracking-wider text-gray-400 font-bold">
                    Amount
                  </th>

                  <th className="text-right py-3 px-3 text-[11px] uppercase tracking-wider text-gray-400 font-bold">
                    Outstanding
                  </th>

                  <th className="text-right py-3 px-3 text-[11px] uppercase tracking-wider text-gray-400 font-bold">
                    Rate
                  </th>

                  <th className="text-right py-3 px-3 text-[11px] uppercase tracking-wider text-gray-400 font-bold">
                    Maturity
                  </th>

                </tr>
              </thead>

              <tbody>

                {loans.map(
                  (
                    loan: BorrowerLoanSummary,
                  ) => (
                    <tr
                      key={
                        loan.loanId
                      }
                      className="border-b border-gray-50 hover:bg-gray-50 transition"
                    >

                      <td className="py-4 px-3">

                        <div className="font-bold text-sm text-gray-900">
                          {loan.referenceNumber ??
                            `#${loan.loanId}`}
                        </div>

                        <div className="text-xs text-gray-400 mt-1">
                          Loan ID #
                          {
                            loan.loanId
                          }
                        </div>

                      </td>

                      <td className="py-4 px-3 text-sm text-gray-600">
                        {loan.loanType ??
                          '—'}
                      </td>

                      <td className="py-4 px-3">

                        <StatusBadge
                          status={
                            loan.status
                          }
                        />

                      </td>

                      <td className="py-4 px-3 text-right text-sm font-semibold text-gray-800">
                        {formatCurrency(
                          loan.loanAmount,
                          loan.currency ??
                            currency,
                          locale,
                        )}
                      </td>

                      <td className="py-4 px-3 text-right text-sm font-bold text-gray-900">
                        {formatCurrency(
                          loan.outstandingBalance,
                          loan.currency ??
                            currency,
                          locale,
                        )}
                      </td>

                      <td className="py-4 px-3 text-right text-sm text-gray-600">
                        {loan.interestRate !=
                        null
                          ? `${loan.interestRate}%`
                          : '—'}
                      </td>

                      <td className="py-4 px-3 text-right text-sm text-gray-500">
                        {loan.maturityDate
                          ? formatDate(
                              loan.maturityDate,
                              locale,
                            )
                          : '—'}
                      </td>

                    </tr>
                  ),
                )}

              </tbody>

            </table>

          </div>
        )}

      </SectionCard>

      {/* ======================================================
          REPAYMENT PERFORMANCE
      ====================================================== */}

      <SectionCard
        title="Repayment Performance"
        subtitle="Borrower repayment behaviour"
      >

        <div className="grid grid-cols-2 md:grid-cols-4 gap-6">

          <Metric
            label="Total Payments"
            value={formatNumber(
              details.totalPayments,
            )}
          />

          <Metric
            label="Successful"
            value={formatNumber(
              details.successfulPayments,
            )}
            positive
          />

          <Metric
            label="Missed"
            value={formatNumber(
              details.missedPayments,
            )}
            danger={
              Number(
                details.missedPayments ??
                  0,
              ) > 0
            }
          />

          <Metric
            label="Overdue"
            value={formatNumber(
              details.overduePayments,
            )}
            danger={
              Number(
                details.overduePayments ??
                  0,
              ) > 0
            }
          />

          <Metric
            label="Repayment Rate"
            value={`${Number(
              details.repaymentRate ??
                0,
            ).toFixed(1)}%`}
          />

          <Metric
            label="On-Time Rate"
            value={`${Number(
              details.onTimePaymentRate ??
                0,
            ).toFixed(1)}%`}
            positive
          />

          <Metric
            label="Current DPD"
            value={formatNumber(
              details.currentDaysPastDue,
            )}
            danger={
              Number(
                details.currentDaysPastDue ??
                  0,
              ) > 0
            }
          />

          <Metric
            label="Maximum DPD"
            value={formatNumber(
              details.maximumDaysPastDue,
            )}
            danger={
              Number(
                details.maximumDaysPastDue ??
                  0,
              ) > 0
            }
          />

        </div>

      </SectionCard>

      {/* ======================================================
          RISK
      ====================================================== */}

      <SectionCard
        title="Risk & Behaviour"
        subtitle="Credit and repayment risk indicators"
      >

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">

          <RiskCard
            label="Risk Level"
            value={
              details.riskLevel
            }
          />

          <RiskCard
            label="Repayment Behaviour"
            value={
              details.repaymentBehaviour
            }
          />

          <RiskCard
            label="Good Payer"
            value={
              details.goodPayer
                ? 'Yes'
                : 'No'
            }
            positive={
              details.goodPayer
            }
          />

          <RiskCard
            label="Currently Overdue"
            value={
              details.currentlyOverdue
                ? 'Yes'
                : 'No'
            }
            positive={
              !details.currentlyOverdue
            }
            danger={
              details.currentlyOverdue
            }
          />

          <RiskCard
            label="Default History"
            value={
              details.hasDefaultHistory
                ? 'Yes'
                : 'No'
            }
            positive={
              !details.hasDefaultHistory
            }
            danger={
              details.hasDefaultHistory
            }
          />

          <RiskCard
            label="Multiple Active Loans"
            value={
              details.hasMultipleActiveLoans
                ? 'Yes'
                : 'No'
            }
            danger={
              details.hasMultipleActiveLoans
            }
            positive={
              !details.hasMultipleActiveLoans
            }
          />

        </div>

      </SectionCard>

      {/* ======================================================
          PAYMENT HISTORY
      ====================================================== */}

      <SectionCard
        title="Payment History"
        subtitle="Recent borrower payment activity"
        right={
          <span className="text-xs font-semibold text-gray-400">
            {payments.length}{' '}
            {payments.length === 1
              ? 'payment'
              : 'payments'}
          </span>
        }
      >

        {payments.length === 0 ? (
          <EmptyState
            icon="✓"
            title="No payments"
            message="No payment records have been recorded for this borrower."
          />
        ) : (
          <div className="overflow-x-auto">

            <table className="w-full min-w-[900px]">

              <thead>
                <tr className="border-b border-gray-100">

                  <th className="text-left py-3 px-3 text-[11px] uppercase tracking-wider text-gray-400 font-bold">
                    Date
                  </th>

                  <th className="text-left py-3 px-3 text-[11px] uppercase tracking-wider text-gray-400 font-bold">
                    Loan
                  </th>

                  <th className="text-right py-3 px-3 text-[11px] uppercase tracking-wider text-gray-400 font-bold">
                    Amount
                  </th>

                  <th className="text-right py-3 px-3 text-[11px] uppercase tracking-wider text-gray-400 font-bold">
                    Principal
                  </th>

                  <th className="text-right py-3 px-3 text-[11px] uppercase tracking-wider text-gray-400 font-bold">
                    Interest
                  </th>

                  <th className="text-left py-3 px-3 text-[11px] uppercase tracking-wider text-gray-400 font-bold">
                    Method
                  </th>

                  <th className="text-left py-3 px-3 text-[11px] uppercase tracking-wider text-gray-400 font-bold">
                    Status
                  </th>

                  <th className="text-left py-3 px-3 text-[11px] uppercase tracking-wider text-gray-400 font-bold">
                    Timing
                  </th>

                </tr>
              </thead>

              <tbody>

                {payments.map(
                  (
                    payment: BorrowerPayment,
                  ) => {

                    const paymentDate =
                      payment.paidDate ??
                      payment.paymentDate ??
                      payment.dueDate;

                    const late =
                      payment.isLate ||
                      payment.onTime ===
                        false;

                    return (
                      <tr
                        key={
                          payment.paymentId
                        }
                        className="border-b border-gray-50 hover:bg-gray-50 transition"
                      >

                        <td className="py-4 px-3 text-sm text-gray-600">
                          {paymentDate
                            ? formatDate(
                                paymentDate,
                                locale,
                              )
                            : '—'}
                        </td>

                        <td className="py-4 px-3">

                          <div className="font-bold text-sm text-gray-800">
                            {payment.loanReference ??
                              payment.loanNumber ??
                              `#${payment.loanId}`}
                          </div>

                        </td>

                        <td className="py-4 px-3 text-right text-sm font-bold text-gray-900">
                          {formatCurrency(
                            payment.amountPaid ??
                              payment.amount ??
                              payment.totalPaid,
                            payment.currency ??
                              currency,
                            locale,
                          )}
                        </td>

                        <td className="py-4 px-3 text-right text-sm text-gray-600">
                          {formatCurrency(
                            payment.principalComponent ??
                              payment.principal,
                            payment.currency ??
                              currency,
                            locale,
                          )}
                        </td>

                        <td className="py-4 px-3 text-right text-sm text-gray-600">
                          {formatCurrency(
                            payment.interestComponent ??
                              payment.interest,
                            payment.currency ??
                              currency,
                            locale,
                          )}
                        </td>

                        <td className="py-4 px-3 text-sm text-gray-600">
                          {payment.paymentMethod ??
                            payment.method ??
                            '—'}
                        </td>

                        <td className="py-4 px-3">

                          <StatusBadge
                            status={
                              payment.status
                            }
                          />

                        </td>

                        <td className="py-4 px-3">

                          {late ? (
                            <span className="inline-flex items-center px-2.5 py-1 rounded-full bg-red-50 text-red-600 text-xs font-bold">
                              Late
                            </span>
                          ) : (
                            <span className="inline-flex items-center px-2.5 py-1 rounded-full bg-teal-50 text-teal-700 text-xs font-bold">
                              On time
                            </span>
                          )}

                        </td>

                      </tr>
                    );
                  },
                )}

              </tbody>

            </table>

          </div>
        )}

      </SectionCard>

      {/* ======================================================
          FOOTER
      ====================================================== */}

      {details.createdAt && (
        <div className="flex items-center justify-between text-xs text-gray-400 pt-2">

          <span>
            Borrower registered{' '}
            {formatDate(
              details.createdAt,
              locale,
            )}
          </span>

          <span>
            ID #{details.borrowerId}
          </span>

        </div>
      )}

    </div>
  );
}

/**
 * ============================================================
 * SECTION CARD
 * ============================================================
 */

function SectionCard({
  title,
  subtitle,
  right,
  children,
}: {
  title: string;
  subtitle?: string;
  right?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <Card>

      <div className="flex items-start justify-between gap-4 mb-5">

        <div>
          <h2 className="font-bold text-gray-900">
            {title}
          </h2>

          {subtitle && (
            <p className="text-xs text-gray-500 mt-1">
              {subtitle}
            </p>
          )}
        </div>

        {right}

      </div>

      {children}

    </Card>
  );
}

/**
 * ============================================================
 * INFO GRID
 * ============================================================
 */

function InfoGrid({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-5">
      {children}
    </div>
  );
}

/**
 * ============================================================
 * INFO ITEM
 * ============================================================
 */

function InfoItem({
  label,
  value,
}: {
  label: string;
  value?: string | number | null;
}) {
  const hasValue =
    value !== undefined &&
    value !== null &&
    String(value).trim() !== '';

  return (
    <div>

      <div className="text-[10px] uppercase tracking-[0.12em] text-gray-400 font-bold">
        {label}
      </div>

      <div className="mt-1 text-sm font-semibold text-gray-800 break-words">
        {hasValue
          ? String(value)
          : '—'}
      </div>

    </div>
  );
}

/**
 * ============================================================
 * KPI CARD
 * ============================================================
 */

function KpiCard({
  label,
  value,
  icon,
}: {
  label: string;
  value: string;
  icon: string;
}) {
  return (
    <Card>

      <div className="flex items-start justify-between gap-3">

        <div className="min-w-0">

          <div className="text-[10px] uppercase tracking-[0.1em] text-gray-400 font-bold">
            {label}
          </div>

          <div className="mt-2 text-lg font-extrabold text-gray-900 truncate">
            {value}
          </div>

        </div>

        <div className="w-8 h-8 rounded-lg bg-teal-50 text-teal-600 flex items-center justify-center text-sm font-bold flex-shrink-0">
          {icon}
        </div>

      </div>

    </Card>
  );
}

/**
 * ============================================================
 * METRIC
 * ============================================================
 */

function Metric({
  label,
  value,
  positive,
  danger,
}: {
  label: string;
  value: string;
  positive?: boolean;
  danger?: boolean;
}) {
  return (
    <div>

      <div className="text-[10px] uppercase tracking-[0.1em] text-gray-400 font-bold">
        {label}
      </div>

      <div
        className={`mt-1 text-lg font-extrabold ${
          danger
            ? 'text-red-600'
            : positive
            ? 'text-teal-600'
            : 'text-gray-900'
        }`}
      >
        {value}
      </div>

    </div>
  );
}

/**
 * ============================================================
 * RISK CARD
 * ============================================================
 */

function RiskCard({
  label,
  value,
  positive,
  danger,
}: {
  label: string;
  value?: string | null;
  positive?: boolean;
  danger?: boolean;
}) {
  return (
    <div className="rounded-xl border border-gray-100 bg-gray-50/70 p-4">

      <div className="text-[10px] uppercase tracking-[0.1em] text-gray-400 font-bold">
        {label}
      </div>

      <div
        className={`mt-2 text-sm font-bold ${
          danger
            ? 'text-red-600'
            : positive
            ? 'text-teal-600'
            : 'text-gray-800'
        }`}
      >
        {value || '—'}
      </div>

    </div>
  );
}

/**
 * ============================================================
 * STATUS BADGE
 * ============================================================
 */

function StatusBadge({
  status,
}: {
  status?: string | null;
}) {
  const value =
    status || 'Unknown';

  const normalized =
    value.toUpperCase();

  let classes =
    'bg-gray-100 text-gray-600';

  if (
    normalized.includes('PAID') ||
    normalized.includes('COMPLETED') ||
    normalized.includes('SUCCESS')
  ) {
    classes =
      'bg-teal-50 text-teal-700';
  } else if (
    normalized.includes('ACTIVE') ||
    normalized.includes('APPROVED')
  ) {
    classes =
      'bg-blue-50 text-blue-700';
  } else if (
    normalized.includes('OVERDUE') ||
    normalized.includes('DEFAULT') ||
    normalized.includes('FAILED') ||
    normalized.includes('REJECTED')
  ) {
    classes =
      'bg-red-50 text-red-600';
  } else if (
    normalized.includes('PENDING')
  ) {
    classes =
      'bg-yellow-50 text-yellow-700';
  }

  return (
    <span
      className={`inline-flex items-center px-2.5 py-1 rounded-full text-[10px] font-bold ${classes}`}
    >
      {value}
    </span>
  );
}

/**
 * ============================================================
 * GENERAL BADGE
 * ============================================================
 */

function Badge({
  label,
  type = 'neutral',
}: {
  label: string;
  type?: 'neutral' | 'danger';
}) {
  return (
    <span
      className={`px-2.5 py-1 rounded-full text-[10px] font-bold ${
        type === 'danger'
          ? 'bg-red-50 text-red-600'
          : 'bg-gray-100 text-gray-600'
      }`}
    >
      {label}
    </span>
  );
}

/**
 * ============================================================
 * EMPTY STATE
 * ============================================================
 */

function EmptyState({
  icon,
  title,
  message,
}: {
  icon: string;
  title: string;
  message: string;
}) {
  return (
    <div className="py-12 text-center">

      <div className="mx-auto w-12 h-12 rounded-xl bg-gray-100 text-gray-400 flex items-center justify-center font-bold">
        {icon}
      </div>

      <h3 className="mt-4 text-sm font-bold text-gray-800">
        {title}
      </h3>

      <p className="mt-1 text-xs text-gray-500">
        {message}
      </p>

    </div>
  );
}

/**
 * ============================================================
 * CREDIT HELPERS
 * ============================================================
 */

function getCreditColor(
  score?: number | null,
) {
  if (
    score == null
  ) {
    return 'text-gray-400';
  }

  if (score >= 700) {
    return 'text-teal-600';
  }

  if (score >= 600) {
    return 'text-yellow-600';
  }

  return 'text-red-500';
}

function getCreditBarColor(
  score?: number | null,
) {
  if (
    score == null
  ) {
    return 'bg-gray-300';
  }

  if (score >= 700) {
    return 'bg-teal-500';
  }

  if (score >= 600) {
    return 'bg-yellow-500';
  }

  return 'bg-red-500';
}

function getCreditLabel(
  score: number,
) {
  if (score >= 750) {
    return 'Excellent';
  }

  if (score >= 700) {
    return 'Very Good';
  }

  if (score >= 650) {
    return 'Good';
  }

  if (score >= 600) {
    return 'Fair';
  }

  return 'High Risk';
}
