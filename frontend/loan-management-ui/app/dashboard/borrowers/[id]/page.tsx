'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
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

  const borrowerId = Number(
    params?.id,
  );

  const [details, setDetails] =
    useState<BorrowerDetails | null>(
      null,
    );

  const [loading, setLoading] =
    useState(true);

  const [error, setError] =
    useState('');

  /**
   * ============================================================
   * LOAD BORROWER DETAILS
   * ============================================================
   */

  useEffect(() => {
    if (
      !borrowerId ||
      Number.isNaN(borrowerId)
    ) {
      setError(
        'Invalid borrower ID.',
      );

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
      <div className="flex items-center justify-center py-20">
        <div className="w-10 h-10 border-2 border-teal-500 border-t-transparent rounded-full animate-spin" />
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
      <div className="space-y-4">
        <Button
          variant="secondary"
          onClick={() =>
            router.push(
              '/dashboard/borrowers',
            )
          }
        >
          ← Back to Borrowers
        </Button>

        <Card>
          <div className="py-12 text-center">
            <div className="text-red-500 text-4xl mb-3">
              !
            </div>

            <h2 className="text-lg font-bold text-gray-900">
              Failed to load borrower
            </h2>

            <p className="text-sm text-gray-500 mt-2">
              {error ||
                'Borrower details could not be loaded.'}
            </p>

            <div className="mt-5">
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

  /**
   * ============================================================
   * BORROWER INITIALS
   * ============================================================
   */

  const initials =
    `${details.firstName?.[0] ?? ''}${details.lastName?.[0] ?? ''}`
      .toUpperCase();

  return (
    <div className="space-y-6">
      {/* ========================================================
          BACK
      ======================================================== */}

      <div>
        <Button
          variant="secondary"
          onClick={() =>
            router.push(
              '/dashboard/borrowers',
            )
          }
        >
          ← Back to Borrowers
        </Button>
      </div>

      {/* ========================================================
          HEADER
      ======================================================== */}

      <Card>
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-5">
          <div className="flex items-center gap-4">
            <div className="w-16 h-16 bg-teal-100 rounded-full flex items-center justify-center text-xl font-bold text-teal-700">
              {initials ||
                details.fullName?.[0] ||
                '?'}
            </div>

            <div>
              <h1 className="text-2xl font-extrabold text-gray-900">
                {details.fullName ||
                  `${details.firstName ?? ''} ${details.lastName ?? ''}`}
              </h1>

              <p className="text-sm text-gray-500 mt-1">
                Borrower #{details.borrowerId}
              </p>

              <div className="flex flex-wrap gap-2 mt-2">
                {details.status && (
                  <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-gray-100 text-gray-700">
                    {details.status}
                  </span>
                )}

                {details.goodPayer && (
                  <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-teal-100 text-teal-700">
                    Good Payer
                  </span>
                )}

                {details.currentlyOverdue && (
                  <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-red-100 text-red-700">
                    Currently Overdue
                  </span>
                )}
              </div>
            </div>
          </div>

          <div className="text-left md:text-right">
            <div className="text-xs uppercase tracking-wider text-gray-400 font-bold">
              Credit Score
            </div>

            <div
              className={`text-3xl font-extrabold ${
                (details.creditScore ?? 0) >=
                700
                  ? 'text-teal-600'
                  : (details.creditScore ?? 0) >=
                    600
                  ? 'text-yellow-600'
                  : 'text-red-500'
              }`}
            >
              {details.creditScore ??
                '—'}
            </div>
          </div>
        </div>
      </Card>

      {/* ========================================================
          PROFILE INFORMATION
      ======================================================== */}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card>
          <div className="font-bold text-gray-900 mb-4">
            Personal Information
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Info
              label="First Name"
              value={
                details.firstName
              }
            />

            <Info
              label="Last Name"
              value={
                details.lastName
              }
            />

            <Info
              label="Email"
              value={
                details.email
              }
            />

            <Info
              label="Phone"
              value={
                details.phone
              }
            />

            <Info
              label="Alternate Phone"
              value={
                details.alternatePhone
              }
            />

            <Info
              label="National ID"
              value={
                details.nationalId
              }
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
              value={
                details.gender
              }
            />

            <Info
              label="Marital Status"
              value={
                details.maritalStatus
              }
            />

            <Info
              label="Nationality"
              value={
                details.nationality
              }
            />

            <Info
              label="Country"
              value={
                details.country
              }
            />
          </div>
        </Card>

        <Card>
          <div className="font-bold text-gray-900 mb-4">
            Employment & Finance
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
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
              value={
                details.jobTitle
              }
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
          </div>
        </Card>
      </div>

      {/* ========================================================
          ADDRESS
      ======================================================== */}

      <Card>
        <div className="font-bold text-gray-900 mb-4">
          Address
        </div>

        <div className="text-sm text-gray-600">
          {details.address ||
            'No address recorded.'}
        </div>
      </Card>

      {/* ========================================================
          PORTFOLIO SUMMARY
      ======================================================== */}

      <div>
        <h2 className="text-lg font-bold text-gray-900 mb-3">
          Loan Portfolio
        </h2>

        <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-5 gap-4">
          <StatCard
            label="Total Loans"
            value={formatNumber(
              details.totalLoans,
            )}
          />

          <StatCard
            label="Active Loans"
            value={formatNumber(
              details.activeLoans,
            )}
          />

          <StatCard
            label="Completed"
            value={formatNumber(
              details.completedLoans,
            )}
          />

          <StatCard
            label="Overdue"
            value={formatNumber(
              details.overdueLoans,
            )}
          />

          <StatCard
            label="Defaulted"
            value={formatNumber(
              details.defaultedLoans,
            )}
          />

          <StatCard
            label="Written Off"
            value={formatNumber(
              details.writtenOffLoans,
            )}
          />

          <StatCard
            label="Total Borrowed"
            value={formatCurrency(
              details.totalBorrowed,
              currency,
              locale,
            )}
          />

          <StatCard
            label="Total Disbursed"
            value={formatCurrency(
              details.totalDisbursed,
              currency,
              locale,
            )}
          />

          <StatCard
            label="Outstanding"
            value={formatCurrency(
              details.totalOutstanding,
              currency,
              locale,
            )}
          />

          <StatCard
            label="Total Paid"
            value={formatCurrency(
              details.totalPaid,
              currency,
              locale,
            )}
          />
        </div>
      </div>

      {/* ========================================================
          REPAYMENT PERFORMANCE
      ======================================================== */}

      <Card>
        <div className="font-bold text-gray-900 mb-4">
          Repayment Performance
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-5">
          <Performance
            label="Total Payments"
            value={formatNumber(
              details.totalPayments,
            )}
          />

          <Performance
            label="Successful Payments"
            value={formatNumber(
              details.successfulPayments,
            )}
          />

          <Performance
            label="Missed Payments"
            value={formatNumber(
              details.missedPayments,
            )}
          />

          <Performance
            label="Overdue Payments"
            value={formatNumber(
              details.overduePayments,
            )}
          />

          <Performance
            label="Repayment Rate"
            value={`${Number(
              details.repaymentRate ?? 0,
            ).toFixed(1)}%`}
          />

          <Performance
            label="On-Time Rate"
            value={`${Number(
              details.onTimePaymentRate ?? 0,
            ).toFixed(1)}%`}
          />

          <Performance
            label="Current Days Past Due"
            value={formatNumber(
              details.currentDaysPastDue,
            )}
          />

          <Performance
            label="Maximum Days Past Due"
            value={formatNumber(
              details.maximumDaysPastDue,
            )}
          />
        </div>
      </Card>

      {/* ========================================================
          RISK
      ======================================================== */}

      <Card>
        <div className="font-bold text-gray-900 mb-4">
          Risk & Behaviour
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <Info
            label="Risk Level"
            value={
              details.riskLevel
            }
          />

          <Info
            label="Repayment Behaviour"
            value={
              details.repaymentBehaviour
            }
          />

          <Info
            label="Good Payer"
            value={
              details.goodPayer
                ? 'Yes'
                : 'No'
            }
          />

          <Info
            label="Currently Overdue"
            value={
              details.currentlyOverdue
                ? 'Yes'
                : 'No'
            }
          />

          <Info
            label="Default History"
            value={
              details.hasDefaultHistory
                ? 'Yes'
                : 'No'
            }
          />

          <Info
            label="Multiple Active Loans"
            value={
              details.hasMultipleActiveLoans
                ? 'Yes'
                : 'No'
            }
          />
        </div>
      </Card>

      {/* ========================================================
          LOANS
      ======================================================== */}

      <Card>
        <div className="flex items-center justify-between mb-4">
          <div className="font-bold text-gray-900">
            Loans
          </div>

          <span className="text-sm text-gray-500">
            {loans.length} loans
          </span>
        </div>

        {loans.length === 0 ? (
          <div className="py-10 text-center text-sm text-gray-500">
            No loans found for this borrower.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b text-left">
                  <th className="py-3 px-2">
                    Reference
                  </th>

                  <th className="py-3 px-2">
                    Type
                  </th>

                  <th className="py-3 px-2">
                    Status
                  </th>

                  <th className="py-3 px-2">
                    Amount
                  </th>

                  <th className="py-3 px-2">
                    Outstanding
                  </th>

                  <th className="py-3 px-2">
                    Rate
                  </th>

                  <th className="py-3 px-2">
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
                      className="border-b hover:bg-gray-50"
                    >
                      <td className="py-3 px-2 font-semibold">
                        {loan.referenceNumber ??
                          `#${loan.loanId}`}
                      </td>

                      <td className="py-3 px-2">
                        {loan.loanType ??
                          '—'}
                      </td>

                      <td className="py-3 px-2">
                        <span className="px-2 py-1 rounded-full bg-gray-100 text-xs font-semibold">
                          {loan.status ??
                            '—'}
                        </span>
                      </td>

                      <td className="py-3 px-2">
                        {formatCurrency(
                          loan.loanAmount,
                          loan.currency ??
                            currency,
                          locale,
                        )}
                      </td>

                      <td className="py-3 px-2 font-semibold">
                        {formatCurrency(
                          loan.outstandingBalance,
                          loan.currency ??
                            currency,
                          locale,
                        )}
                      </td>

                      <td className="py-3 px-2">
                        {loan.interestRate !=
                        null
                          ? `${loan.interestRate}%`
                          : '—'}
                      </td>

                      <td className="py-3 px-2">
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
      </Card>

      {/* ========================================================
          PAYMENTS
      ======================================================== */}

      <Card>
        <div className="flex items-center justify-between mb-4">
          <div className="font-bold text-gray-900">
            Payment History
          </div>

          <span className="text-sm text-gray-500">
            {payments.length} payments
          </span>
        </div>

        {payments.length === 0 ? (
          <div className="py-10 text-center text-sm text-gray-500">
            No payments found for this borrower.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b text-left">
                  <th className="py-3 px-2">
                    Date
                  </th>

                  <th className="py-3 px-2">
                    Loan
                  </th>

                  <th className="py-3 px-2">
                    Amount
                  </th>

                  <th className="py-3 px-2">
                    Principal
                  </th>

                  <th className="py-3 px-2">
                    Interest
                  </th>

                  <th className="py-3 px-2">
                    Method
                  </th>

                  <th className="py-3 px-2">
                    Status
                  </th>

                  <th className="py-3 px-2">
                    Late
                  </th>
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
                      className="border-b hover:bg-gray-50"
                    >
                      <td className="py-3 px-2">
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
                      </td>

                      <td className="py-3 px-2 font-semibold">
                        {payment.loanReference ??
                          payment.loanNumber ??
                          `#${payment.loanId}`}
                      </td>

                      <td className="py-3 px-2 font-semibold">
                        {formatCurrency(
                          payment.amountPaid ??
                            payment.amount ??
                            payment.totalPaid,
                          payment.currency ??
                            currency,
                          locale,
                        )}
                      </td>

                      <td className="py-3 px-2">
                        {formatCurrency(
                          payment.principalComponent ??
                            payment.principal,
                          payment.currency ??
                            currency,
                          locale,
                        )}
                      </td>

                      <td className="py-3 px-2">
                        {formatCurrency(
                          payment.interestComponent ??
                            payment.interest,
                          payment.currency ??
                            currency,
                          locale,
                        )}
                      </td>

                      <td className="py-3 px-2">
                        {payment.paymentMethod ??
                          payment.method ??
                          '—'}
                      </td>

                      <td className="py-3 px-2">
                        <span className="px-2 py-1 rounded-full bg-gray-100 text-xs font-semibold">
                          {payment.status ??
                            '—'}
                        </span>
                      </td>

                      <td className="py-3 px-2">
                        {payment.isLate ||
                        payment.onTime ===
                          false
                          ? (
                            <span className="text-red-600 font-semibold">
                              Yes
                            </span>
                          )
                          : (
                            <span className="text-teal-600 font-semibold">
                              No
                            </span>
                          )}
                      </td>
                    </tr>
                  ),
                )}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* ========================================================
          CREATED
      ======================================================== */}

      {details.createdAt && (
        <div className="text-xs text-gray-400">
          Borrower registered{' '}
          {formatDate(
            details.createdAt,
            locale,
          )}
        </div>
      )}
    </div>
  );
}

/**
 * ============================================================
 * INFO COMPONENT
 * ============================================================
 */

function Info({
  label,
  value,
}: {
  label: string;
  value?: string | number | null;
}) {
  return (
    <div>
      <div className="text-xs uppercase tracking-wider text-gray-400 font-semibold mb-1">
        {label}
      </div>

      <div className="text-sm text-gray-800 font-medium break-words">
        {value !== undefined &&
        value !== null &&
        String(value).trim() !== ''
          ? String(value)
          : '—'}
      </div>
    </div>
  );
}

/**
 * ============================================================
 * STAT CARD
 * ============================================================
 */

function StatCard({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  return (
    <Card>
      <div className="text-xs uppercase tracking-wider text-gray-400 font-semibold">
        {label}
      </div>

      <div className="text-xl font-extrabold text-gray-900 mt-2">
        {value}
      </div>
    </Card>
  );
}

/**
 * ============================================================
 * PERFORMANCE
 * ============================================================
 */

function Performance({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  return (
    <div>
      <div className="text-xs uppercase tracking-wider text-gray-400 font-semibold">
        {label}
      </div>

      <div className="text-lg font-bold text-gray-900 mt-1">
        {value}
      </div>
    </div>
  );
}