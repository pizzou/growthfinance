
'use client';

import { useCallback, useEffect, useState } from 'react';
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


// ============================================================
// PAGE
// ============================================================

export default function BorrowerDetailsPage() {

  const params = useParams();
  const router = useRouter();

  const { currency, locale } = useAuth();


  // ============================================================
  // BORROWER ID
  // ============================================================

  const borrowerId = Number(
    Array.isArray(params?.id)
      ? params.id[0]
      : params?.id
  );


  // ============================================================
  // STATE
  // ============================================================

  const [borrower, setBorrower] =
    useState<BorrowerDetails | null>(null);

  const [loading, setLoading] =
    useState(true);

  const [error, setError] =
    useState('');

  const [activeTab, setActiveTab] =
    useState<'overview' | 'loans' | 'payments'>(
      'overview'
    );


  // ============================================================
  // LOAD BORROWER DETAILS
  // ============================================================

  const loadBorrower = useCallback(
    async () => {

      if (
        !Number.isFinite(borrowerId) ||
        borrowerId <= 0
      ) {

        setError(
          'Invalid borrower ID.'
        );

        setLoading(false);

        return;
      }


      try {

        setLoading(true);
        setError('');


        const result =
          await borrowerApi.getDetails(
            borrowerId
          );


        setBorrower(result);

      } catch (err: any) {

        console.error(
          'Failed to load borrower details:',
          err
        );


        setError(
          err?.message ||
          'Unable to load borrower details.'
        );

      } finally {

        setLoading(false);
      }

    },
    [borrowerId]
  );


  // ============================================================
  // LOAD
  // ============================================================

  useEffect(() => {

    loadBorrower();

  }, [loadBorrower]);


  // ============================================================
  // LOADING
  // ============================================================

  if (loading) {

    return (
      <div className="min-h-[400px] flex items-center justify-center">

        <div className="flex flex-col items-center gap-3">

          <div
            className="
              w-10
              h-10
              border-4
              border-gray-200
              border-t-teal-600
              rounded-full
              animate-spin
            "
          />

          <p className="text-sm text-gray-500">
            Loading borrower details...
          </p>

        </div>

      </div>
    );
  }


  // ============================================================
  // ERROR
  // ============================================================

  if (error || !borrower) {

    return (
      <div className="space-y-4">

        <Button
          variant="secondary"
          onClick={() => router.back()}
        >
          ← Back
        </Button>


        <Card>

          <div className="py-12 text-center">

            <div className="text-4xl mb-3">
              ⚠️
            </div>

            <h2 className="text-lg font-bold text-gray-900">
              Unable to load borrower
            </h2>

            <p className="text-sm text-gray-500 mt-2">
              {error || 'Borrower was not found.'}
            </p>


            <div className="mt-5 flex justify-center gap-3">

              <Button
                variant="secondary"
                onClick={() => router.back()}
              >
                Go Back
              </Button>

              <Button
                onClick={loadBorrower}
              >
                Try Again
              </Button>

            </div>

          </div>

        </Card>

      </div>
    );
  }


  // ============================================================
  // HELPERS
  // ============================================================

  const money = (
    value?: number | null
  ) => {

    return formatCurrency(
      value ?? 0,
      currency,
      locale
    );
  };


  const date = (
    value?: string | null
  ) => {

    if (!value) {
      return '—';
    }

    return formatDate(
      value,
      locale
    );
  };


  const percentage = (
    value?: number | null
  ) => {

    return `${(
      value ?? 0
    ).toFixed(1)}%`;
  };


  const getInitials = () => {

    const first =
      borrower.firstName?.[0] ||
      '';

    const last =
      borrower.lastName?.[0] ||
      '';

    return (
      `${first}${last}`
    ).toUpperCase();
  };


  const getLoanStatusClass = (
    status?: string | null
  ) => {

    switch (
      status?.toUpperCase()
    ) {

      case 'ACTIVE':
        return 'bg-teal-100 text-teal-700';

      case 'APPROVED':
        return 'bg-blue-100 text-blue-700';

      case 'DISBURSED':
        return 'bg-blue-100 text-blue-700';

      case 'OVERDUE':
        return 'bg-orange-100 text-orange-700';

      case 'DEFAULTED':
        return 'bg-red-100 text-red-700';

      case 'WRITTEN_OFF':
        return 'bg-red-100 text-red-700';

      case 'PAID':
        return 'bg-green-100 text-green-700';

      case 'CLOSED':
        return 'bg-gray-100 text-gray-700';

      case 'REJECTED':
        return 'bg-red-100 text-red-700';

      default:
        return 'bg-gray-100 text-gray-700';
    }
  };


  const getPaymentStatusClass = (
    status?: string | null
  ) => {

    switch (
      status?.toUpperCase()
    ) {

      case 'COMPLETED':
        return 'bg-green-100 text-green-700';

      case 'PARTIALLY_PAID':
        return 'bg-yellow-100 text-yellow-700';

      case 'FAILED':
        return 'bg-red-100 text-red-700';

      case 'REVERSED':
        return 'bg-red-100 text-red-700';

      default:
        return 'bg-gray-100 text-gray-700';
    }
  };


  // ============================================================
  // RENDER
  // ============================================================

  return (

    <div className="space-y-6">


      {/* ======================================================
          HEADER
      ====================================================== */}

      <div className="flex items-start justify-between gap-4">

        <div className="flex items-center gap-4">

          <Button
            variant="secondary"
            onClick={() => router.back()}
          >
            ← Back
          </Button>


          <div
            className="
              w-14
              h-14
              rounded-full
              bg-teal-100
              text-teal-700
              flex
              items-center
              justify-center
              text-lg
              font-extrabold
            "
          >
            {getInitials()}
          </div>


          <div>

            <h1 className="text-2xl font-extrabold text-gray-900">

              {borrower.fullName ||
                `${borrower.firstName ?? ''} ${borrower.lastName ?? ''}`}

            </h1>

            <p className="text-sm text-gray-500 mt-1">

              Borrower ID:
              {' '}
              {borrower.borrowerId}

            </p>

          </div>

        </div>


        <div className="flex items-center gap-2">

          <span
            className={`
              px-3
              py-1.5
              rounded-full
              text-xs
              font-bold
              ${
                borrower.status === 'ACTIVE'
                  ? 'bg-green-100 text-green-700'
                  : 'bg-gray-100 text-gray-700'
              }
            `}
          >
            {borrower.status ?? 'UNKNOWN'}
          </span>


          {borrower.currentlyOverdue && (

            <span
              className="
                px-3
                py-1.5
                rounded-full
                text-xs
                font-bold
                bg-red-100
                text-red-700
              "
            >
              OVERDUE
            </span>

          )}

        </div>

      </div>


      {/* ======================================================
          QUICK SUMMARY
      ====================================================== */}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">

        <SummaryCard
          title="Total Borrowed"
          value={money(borrower.totalBorrowed)}
        />

        <SummaryCard
          title="Outstanding"
          value={money(borrower.totalOutstanding)}
          danger={
            borrower.totalOutstanding > 0 &&
            borrower.currentlyOverdue
          }
        />

        <SummaryCard
          title="Total Paid"
          value={money(borrower.totalPaid)}
        />

        <SummaryCard
          title="Repayment Rate"
          value={percentage(borrower.repaymentRate)}
        />

      </div>


      {/* ======================================================
          TABS
      ====================================================== */}

      <div
        className="
          border-b
          border-gray-200
          flex
          gap-6
        "
      >

        <TabButton
          active={
            activeTab === 'overview'
          }
          onClick={() =>
            setActiveTab('overview')
          }
        >
          Overview
        </TabButton>


        <TabButton
          active={
            activeTab === 'loans'
          }
          onClick={() =>
            setActiveTab('loans')
          }
        >
          Loans
          {' '}
          ({borrower.loans?.length ?? 0})
        </TabButton>


        <TabButton
          active={
            activeTab === 'payments'
          }
          onClick={() =>
            setActiveTab('payments')
          }
        >
          Payments
          {' '}
          ({borrower.payments?.length ?? 0})
        </TabButton>

      </div>


      {/* ======================================================
          OVERVIEW
      ====================================================== */}

      {activeTab === 'overview' && (

        <div className="space-y-6">


          {/* --------------------------------------------------
              PERSONAL INFORMATION
          -------------------------------------------------- */}

          <Card>

            <SectionTitle>
              Personal Information
            </SectionTitle>


            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">

              <InfoItem
                label="First Name"
                value={borrower.firstName}
              />

              <InfoItem
                label="Last Name"
                value={borrower.lastName}
              />

              <InfoItem
                label="Email"
                value={borrower.email}
              />

              <InfoItem
                label="Phone"
                value={borrower.phone}
              />

              <InfoItem
                label="Alternate Phone"
                value={borrower.alternatePhone}
              />

              <InfoItem
                label="National ID"
                value={borrower.nationalId}
              />

              <InfoItem
                label="Passport Number"
                value={borrower.passportNumber}
              />

              <InfoItem
                label="Date of Birth"
                value={date(borrower.dateOfBirth)}
              />

              <InfoItem
                label="Gender"
                value={borrower.gender}
              />

              <InfoItem
                label="Marital Status"
                value={borrower.maritalStatus}
              />

              <InfoItem
                label="Nationality"
                value={borrower.nationality}
              />

              <InfoItem
                label="Country"
                value={borrower.country}
              />

              <InfoItem
                label="Address"
                value={borrower.address}
              />

            </div>

          </Card>


          {/* --------------------------------------------------
              EMPLOYMENT & FINANCIAL
          -------------------------------------------------- */}

          <Card>

            <SectionTitle>
              Employment & Financial Information
            </SectionTitle>


            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">

              <InfoItem
                label="Employer"
                value={borrower.employerName}
              />

              <InfoItem
                label="Employment Type"
                value={borrower.employmentType}
              />

              <InfoItem
                label="Job Title"
                value={borrower.jobTitle}
              />

              <InfoItem
                label="Monthly Income"
                value={money(borrower.monthlyIncome)}
              />

              <InfoItem
                label="Monthly Expenses"
                value={money(borrower.monthlyExpenses)}
              />

              <InfoItem
                label="Net Worth"
                value={money(borrower.netWorth)}
              />

            </div>

          </Card>


          {/* --------------------------------------------------
              CREDIT & RISK
          -------------------------------------------------- */}

          <Card>

            <SectionTitle>
              Credit & Risk
            </SectionTitle>


            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">

              <InfoItem
                label="Credit Score"
                value={
                  borrower.creditScore !== null &&
                  borrower.creditScore !== undefined
                    ? formatNumber(
                        borrower.creditScore
                      )
                    : '—'
                }
              />

              <InfoItem
                label="Credit Bureau"
                value={borrower.creditBureau}
              />

              <InfoItem
                label="Risk Level"
                value={borrower.riskLevel}
              />

              <InfoItem
                label="Repayment Behaviour"
                value={
                  borrower.repaymentBehaviour
                }
              />

              <InfoItem
                label="Good Payer"
                value={
                  borrower.goodPayer
                    ? 'Yes'
                    : 'No'
                }
              />

              <InfoItem
                label="Currently Overdue"
                value={
                  borrower.currentlyOverdue
                    ? 'Yes'
                    : 'No'
                }
              />

              <InfoItem
                label="Default History"
                value={
                  borrower.hasDefaultHistory
                    ? 'Yes'
                    : 'No'
                }
              />

              <InfoItem
                label="Multiple Active Loans"
                value={
                  borrower.hasMultipleActiveLoans
                    ? 'Yes'
                    : 'No'
                }
              />

            </div>

          </Card>


          {/* --------------------------------------------------
              PORTFOLIO SUMMARY
          -------------------------------------------------- */}

          <Card>

            <SectionTitle>
              Loan Portfolio
            </SectionTitle>


            <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">

              <Metric
                label="Total Loans"
                value={
                  borrower.totalLoans
                }
              />

              <Metric
                label="Active"
                value={
                  borrower.activeLoans
                }
              />

              <Metric
                label="Completed"
                value={
                  borrower.completedLoans
                }
              />

              <Metric
                label="Overdue"
                value={
                  borrower.overdueLoans
                }
              />

              <Metric
                label="Defaulted"
                value={
                  borrower.defaultedLoans
                }
              />

              <Metric
                label="Written Off"
                value={
                  borrower.writtenOffLoans
                }
              />

            </div>

          </Card>


          {/* --------------------------------------------------
              REPAYMENT PERFORMANCE
          -------------------------------------------------- */}

          <Card>

            <SectionTitle>
              Repayment Performance
            </SectionTitle>


            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">

              <Metric
                label="Total Payments"
                value={
                  borrower.totalPayments
                }
              />

              <Metric
                label="Successful"
                value={
                  borrower.successfulPayments
                }
              />

              <Metric
                label="Missed"
                value={
                  borrower.missedPayments
                }
              />

              <Metric
                label="Overdue"
                value={
                  borrower.overduePayments
                }
              />

              <Metric
                label="Repayment Rate"
                value={
                  percentage(
                    borrower.repaymentRate
                  )
                }
              />

              <Metric
                label="On-Time Rate"
                value={
                  percentage(
                    borrower.onTimePaymentRate
                  )
                }
              />

              <Metric
                label="Current DPD"
                value={
                  borrower.currentDaysPastDue
                }
              />

              <Metric
                label="Maximum DPD"
                value={
                  borrower.maximumDaysPastDue
                }
              />

            </div>

          </Card>

        </div>

      )}


      {/* ======================================================
          LOANS
      ====================================================== */}

      {activeTab === 'loans' && (

        <Card>

          <SectionTitle>
            Borrower Loans
          </SectionTitle>


          {!borrower.loans ||
          borrower.loans.length === 0 ? (

            <EmptyState
              message="This borrower has no loans."
            />

          ) : (

            <div className="overflow-x-auto">

              <table
                className="
                  min-w-full
                  text-sm
                "
              >

                <thead>

                  <tr className="border-b">

                    <th className="text-left py-3 pr-4">
                      Reference
                    </th>

                    <th className="text-left py-3 pr-4">
                      Type
                    </th>

                    <th className="text-left py-3 pr-4">
                      Status
                    </th>

                    <th className="text-right py-3 pr-4">
                      Amount
                    </th>

                    <th className="text-right py-3 pr-4">
                      Outstanding
                    </th>

                    <th className="text-right py-3 pr-4">
                      Paid
                    </th>

                    <th className="text-right py-3 pr-4">
                      Rate
                    </th>

                    <th className="text-left py-3">
                      Maturity
                    </th>

                  </tr>

                </thead>


                <tbody>

                  {borrower.loans.map(
                    (
                      loan: BorrowerLoanSummary
                    ) => (

                      <tr
                        key={loan.loanId}
                        className="border-b hover:bg-gray-50"
                      >

                        <td className="py-3 pr-4">

                          <button
                            className="
                              font-semibold
                              text-teal-700
                              hover:underline
                            "
                            onClick={() =>
                              router.push(
                                `/dashboard/loans/${loan.loanId}`
                              )
                            }
                          >
                            {
                              loan.referenceNumber ||
                              `Loan #${loan.loanId}`
                            }
                          </button>

                        </td>


                        <td className="py-3 pr-4">
                          {loan.loanType || '—'}
                        </td>


                        <td className="py-3 pr-4">

                          <span
                            className={`
                              px-2.5
                              py-1
                              rounded-full
                              text-xs
                              font-bold
                              ${getLoanStatusClass(
                                loan.status
                              )}
                            `}
                          >
                            {loan.status || '—'}
                          </span>

                        </td>


                        <td className="py-3 pr-4 text-right">
                          {money(loan.loanAmount)}
                        </td>


                        <td className="py-3 pr-4 text-right font-semibold">
                          {money(
                            loan.outstandingBalance
                          )}
                        </td>


                        <td className="py-3 pr-4 text-right">
                          {money(
                            loan.totalPaid
                          )}
                        </td>


                        <td className="py-3 pr-4 text-right">
                          {loan.interestRate !== null &&
                          loan.interestRate !== undefined
                            ? `${loan.interestRate}%`
                            : '—'}
                        </td>


                        <td className="py-3">
                          {date(
                            loan.maturityDate
                          )}
                        </td>

                      </tr>

                    )
                  )}

                </tbody>

              </table>

            </div>

          )}

        </Card>

      )}


      {/* ======================================================
          PAYMENTS
      ====================================================== */}

      {activeTab === 'payments' && (

        <Card>

          <SectionTitle>
            Payment History
          </SectionTitle>


          {!borrower.payments ||
          borrower.payments.length === 0 ? (

            <EmptyState
              message="This borrower has no payment history."
            />

          ) : (

            <div className="overflow-x-auto">

              <table
                className="
                  min-w-full
                  text-sm
                "
              >

                <thead>

                  <tr className="border-b">

                    <th className="text-left py-3 pr-4">
                      Date
                    </th>

                    <th className="text-left py-3 pr-4">
                      Loan
                    </th>

                    <th className="text-left py-3 pr-4">
                      Method
                    </th>

                    <th className="text-right py-3 pr-4">
                      Amount
                    </th>

                    <th className="text-right py-3 pr-4">
                      Principal
                    </th>

                    <th className="text-right py-3 pr-4">
                      Interest
                    </th>

                    <th className="text-right py-3 pr-4">
                      Penalty
                    </th>

                    <th className="text-left py-3">
                      Status
                    </th>

                  </tr>

                </thead>


                <tbody>

                  {borrower.payments.map(
                    (
                      payment: BorrowerPayment
                    ) => (

                      <tr
                        key={payment.paymentId}
                        className="border-b hover:bg-gray-50"
                      >

                        <td className="py-3 pr-4">

                          {date(
                            payment.paidDate ||
                            payment.paymentDate
                          )}

                        </td>


                        <td className="py-3 pr-4">

                          {payment.loanReference ||
                            payment.loanNumber ||
                            (
                              payment.loanId
                                ? `Loan #${payment.loanId}`
                                : '—'
                            )}

                        </td>


                        <td className="py-3 pr-4">

                          {
                            payment.paymentMethod ||
                            payment.method ||
                            '—'
                          }

                        </td>


                        <td className="py-3 pr-4 text-right font-semibold">

                          {money(
                            payment.amountPaid ??
                            payment.amount ??
                            payment.totalPaid
                          )}

                        </td>


                        <td className="py-3 pr-4 text-right">

                          {money(
                            payment.principalComponent ??
                            payment.principal
                          )}

                        </td>


                        <td className="py-3 pr-4 text-right">

                          {money(
                            payment.interestComponent ??
                            payment.interest
                          )}

                        </td>


                        <td className="py-3 pr-4 text-right">

                          {money(
                            payment.penalty
                          )}

                        </td>


                        <td className="py-3">

                          <span
                            className={`
                              px-2.5
                              py-1
                              rounded-full
                              text-xs
                              font-bold
                              ${getPaymentStatusClass(
                                payment.status
                              )}
                            `}
                          >
                            {
                              payment.status ||
                              (
                                payment.paid
                                  ? 'COMPLETED'
                                  : 'PENDING'
                              )
                            }
                          </span>

                        </td>

                      </tr>

                    )
                  )}

                </tbody>

              </table>

            </div>

          )}

        </Card>

      )}

    </div>
  );
}


// ============================================================
// SUMMARY CARD
// ============================================================

function SummaryCard({
  title,
  value,
  danger = false,
}: {
  title: string;
  value: string;
  danger?: boolean;
}) {

  return (

    <Card>

      <div className="p-1">

        <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">
          {title}
        </p>

        <p
          className={`
            text-xl
            font-extrabold
            mt-2
            ${
              danger
                ? 'text-red-600'
                : 'text-gray-900'
            }
          `}
        >
          {value}
        </p>

      </div>

    </Card>
  );
}


// ============================================================
// TAB BUTTON
// ============================================================

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {

  return (

    <button
      type="button"
      onClick={onClick}
      className={`
        pb-3
        text-sm
        font-semibold
        border-b-2
        transition
        ${
          active
            ? 'border-teal-600 text-teal-700'
            : 'border-transparent text-gray-500 hover:text-gray-900'
        }
      `}
    >
      {children}
    </button>
  );
}


// ============================================================
// SECTION TITLE
// ============================================================

function SectionTitle({
  children,
}: {
  children: React.ReactNode;
}) {

  return (

    <h2
      className="
        text-sm
        font-extrabold
        uppercase
        tracking-wider
        text-gray-500
        mb-5
      "
    >
      {children}
    </h2>
  );
}


// ============================================================
// INFO ITEM
// ============================================================

function InfoItem({
  label,
  value,
}: {
  label: string;
  value?: string | number | null;
}) {

  return (

    <div>

      <p className="text-xs text-gray-400 mb-1">
        {label}
      </p>

      <p className="text-sm font-semibold text-gray-900 break-words">
        {
          value !== undefined &&
          value !== null &&
          String(value).trim() !== ''
            ? value
            : '—'
        }
      </p>

    </div>
  );
}


// ============================================================
// METRIC
// ============================================================

function Metric({
  label,
  value,
}: {
  label: string;
  value: string | number;
}) {

  return (

    <div
      className="
        rounded-lg
        bg-gray-50
        border
        border-gray-100
        p-4
      "
    >

      <p className="text-xs text-gray-500">
        {label}
      </p>

      <p className="text-lg font-extrabold text-gray-900 mt-1">
        {value}
      </p>

    </div>
  );
}


// ============================================================
// EMPTY STATE
// ============================================================

function EmptyState({
  message,
}: {
  message: string;
}) {

  return (

    <div className="py-12 text-center">

      <div className="text-3xl mb-2">
        📋
      </div>

      <p className="text-sm text-gray-500">
        {message}
      </p>

    </div>
  );
}
