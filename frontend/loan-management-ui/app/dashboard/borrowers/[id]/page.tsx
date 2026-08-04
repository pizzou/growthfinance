
'use client';

import { useEffect, useState, useCallback } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';

import {
  getBorrowerById,
  updateBorrower,
} from '../../../../services/borrowerService';

import { getLoansByBorrower } from '../../../../services/loanService';

import {
  getPaymentsByBorrower,
  BorrowerPayment,
} from '../../../../services/paymentService';

import { Borrower, Loan } from '../../../../types/index';

import {
  KycBadge,
  LoanStatusBadge,
} from '../../../../components/ui/StatusBadge';

import { PageSpinner } from '../../../../components/ui/Skeleton';

import { toast } from '../../../../hooks/useToast';

import DocumentsPanel from '../../../../components/DocumentsPanel';

export default function BorrowerDetailPage() {
  const { id } = useParams<{ id: string }>();

  const borrowerId = Number(id);

  const [borrower, setBorrower] = useState<Borrower | null>(null);
  const [loans, setLoans] = useState<Loan[]>([]);
  const [payments, setPayments] = useState<BorrowerPayment[]>([]);

  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);

  const [editFirst, setEditFirst] = useState('');
  const [editLast, setEditLast] = useState('');
  const [editPhone, setEditPhone] = useState('');
  const [editAddr, setEditAddr] = useState('');

  const getMsg = (err: unknown) =>
    err instanceof Error ? err.message : 'Something went wrong';

  const money = (
    value: number | null | undefined,
    currency?: string | null
  ) => {
    const amount = Number(value ?? 0);

    return `${currency ?? ''} ${amount.toLocaleString(undefined, {
      minimumFractionDigits: 0,
      maximumFractionDigits: 2,
    })}`.trim();
  };

  const formatDate = (
    value?: string | null
  ) => {
    if (!value) return '—';

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
      return value;
    }

    return date.toLocaleDateString();
  };

  const load = useCallback(async () => {
    if (!Number.isFinite(borrowerId)) {
      throw new Error('Invalid borrower ID');
    }

    const [b, l, p] = await Promise.all([
      getBorrowerById(borrowerId),
      getLoansByBorrower(borrowerId),
      getPaymentsByBorrower(borrowerId),
    ]);

    setBorrower(b);
    setLoans(l);
    setPayments(p);

    setEditFirst(b.firstName ?? '');
    setEditLast(b.lastName ?? '');
    setEditPhone(b.phone ?? '');
    setEditAddr(b.addressLine1 ?? '');
  }, [borrowerId]);

  useEffect(() => {
    load()
      .catch((err) => {
        console.error(err);
        toast(
          'error',
          err instanceof Error
            ? err.message
            : 'Failed to load borrower'
        );
      })
      .finally(() => setLoading(false));
  }, [load]);

  const handleSave = async () => {
    setSaving(true);

    try {
      await updateBorrower(borrowerId, {
        firstName: editFirst,
        lastName: editLast,
        phone: editPhone,
        addressLine1: editAddr,
      });

      toast('success', 'Borrower updated');

      setEditing(false);

      await load();
    } catch (err: unknown) {
      toast('error', getMsg(err));
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <PageSpinner />;
  }

  if (!borrower) {
    return (
      <p className="text-center text-gray-400 py-20">
        Borrower not found.
      </p>
    );
  }

  // ============================================================
  // LOAN METRICS
  // ============================================================

  const totalLoans = loans.length;

  const activeLoans = loans.filter(
    (l) =>
      l.status === 'APPROVED' ||
      l.status === 'DISBURSED' ||
      l.status === 'ACTIVE' ||
      l.status === 'OVERDUE'
  );

  const completedLoans = loans.filter(
    (l) =>
      l.status === 'PAID' ||
      l.status === 'CLOSED'
  );

  const overdueLoans = loans.filter(
    (l) => l.status === 'OVERDUE'
  );

  const defaultedLoans = loans.filter(
    (l) =>
      l.status === 'DEFAULTED' ||
      l.status === 'WRITTEN_OFF'
  );

  const totalBorrowed = loans.reduce(
    (sum, loan) => sum + Number(loan.amount ?? 0),
    0
  );

  const totalDisbursed = loans.reduce(
    (sum, loan) => sum + Number(loan.disbursedAmount ?? 0),
    0
  );

  const totalOutstanding = loans.reduce(
    (sum, loan) => sum + Number(loan.outstandingBalance ?? 0),
    0
  );

  const totalPaid = loans.reduce(
    (sum, loan) => sum + Number(loan.totalPaid ?? 0),
    0
  );

  // ============================================================
  // PAYMENT METRICS
  // ============================================================

  const totalPayments = payments.length;

  const successfulPayments = payments.filter(
    (p) =>
      p.status === 'COMPLETED' ||
      p.paid === true
  );

  const latePayments = payments.filter(
    (p) =>
      p.onTime === false ||
      Number(p.daysLate ?? 0) > 0
  );

  const failedPayments = payments.filter(
    (p) => p.status === 'FAILED'
  );

  const totalAmountPaid = payments.reduce(
    (sum, payment) =>
      sum +
      Number(
        payment.amountPaid ??
        payment.amount ??
        0
      ),
    0
  );

  const totalPrincipalPaid = payments.reduce(
    (sum, payment) =>
      sum +
      Number(
        payment.principalComponent ??
        payment.principal ??
        0
      ),
    0
  );

  const totalInterestPaid = payments.reduce(
    (sum, payment) =>
      sum +
      Number(
        payment.interestComponent ??
        payment.interest ??
        0
      ),
    0
  );

  const totalPenalties = payments.reduce(
    (sum, payment) =>
      sum +
      Number(payment.penalty ?? 0),
    0
  );

  const repaymentRate =
    totalPayments > 0
      ? (successfulPayments.length / totalPayments) * 100
      : 0;

  const onTimePayments = payments.filter(
    (p) => p.onTime === true
  );

  const onTimePaymentRate =
    successfulPayments.length > 0
      ? (onTimePayments.length / successfulPayments.length) * 100
      : 0;

  const maximumDaysLate = payments.reduce(
    (max, payment) =>
      Math.max(
        max,
        Number(payment.daysLate ?? 0)
      ),
    0
  );

  const currentlyOverdue =
    overdueLoans.length > 0 ||
    loans.some(
      (loan) => Number(loan.daysOverdue ?? 0) > 0
    );

  const hasDefaultHistory =
    defaultedLoans.length > 0;

  const hasMultipleActiveLoans =
    activeLoans.length > 1;

  // ============================================================
  // BORROWER QUALITY
  // ============================================================

  let borrowerRating = 'GOOD PAYER';

  if (hasDefaultHistory) {
    borrowerRating = 'HIGH RISK';
  } else if (currentlyOverdue) {
    borrowerRating = 'NEEDS ATTENTION';
  } else if (onTimePaymentRate >= 90) {
    borrowerRating = 'EXCELLENT PAYER';
  } else if (onTimePaymentRate >= 75) {
    borrowerRating = 'GOOD PAYER';
  } else if (onTimePaymentRate >= 50) {
    borrowerRating = 'FAIR PAYER';
  } else if (totalPayments > 0) {
    borrowerRating = 'POOR PAYER';
  }

  const riskColor =
    borrowerRating === 'EXCELLENT PAYER'
      ? 'text-green-700 bg-green-50 border-green-200'
      : borrowerRating === 'GOOD PAYER'
      ? 'text-green-700 bg-green-50 border-green-200'
      : borrowerRating === 'FAIR PAYER'
      ? 'text-yellow-700 bg-yellow-50 border-yellow-200'
      : borrowerRating === 'NEEDS ATTENTION'
      ? 'text-orange-700 bg-orange-50 border-orange-200'
      : 'text-red-700 bg-red-50 border-red-200';

  return (
    <div className="space-y-6 max-w-7xl">

      {/* ========================================================
          BACK
      ======================================================== */}

      <Link
        href="/dashboard/borrowers"
        className="text-sm text-gray-500 hover:text-gray-700"
      >
        ← Back to Borrowers
      </Link>

      {/* ========================================================
          BORROWER HEADER
      ======================================================== */}

      <div className="bg-white rounded-xl border border-gray-200 p-6">

        <div className="flex items-start justify-between">

          <div className="flex items-center gap-4">

            <div className="w-16 h-16 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center text-2xl font-bold">
              {borrower.firstName?.[0]?.toUpperCase()}
            </div>

            <div>

              <h1 className="text-2xl font-bold text-gray-900">
                {borrower.firstName} {borrower.lastName}
              </h1>

              <div className="flex items-center gap-2 mt-1">
                <KycBadge status={borrower.kycStatus} />

                <span
                  className={`px-2.5 py-1 rounded-full text-xs font-semibold border ${riskColor}`}
                >
                  {borrowerRating}
                </span>
              </div>

            </div>

          </div>

          {!editing && (
            <button
              onClick={() => setEditing(true)}
              className="text-sm text-blue-600 hover:text-blue-800 font-medium border border-blue-200 px-3 py-1.5 rounded-lg"
            >
              Edit
            </button>
          )}

        </div>

        {/* ======================================================
            PROFILE
        ====================================================== */}

        {editing ? (

          <div className="mt-6 space-y-4">

            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">

              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1">
                  First Name
                </label>

                <input
                  value={editFirst}
                  onChange={(e) =>
                    setEditFirst(e.target.value)
                  }
                  autoFocus
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1">
                  Last Name
                </label>

                <input
                  value={editLast}
                  onChange={(e) =>
                    setEditLast(e.target.value)
                  }
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1">
                  Phone
                </label>

                <input
                  value={editPhone}
                  onChange={(e) =>
                    setEditPhone(e.target.value)
                  }
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1">
                  Address
                </label>

                <input
                  value={editAddr}
                  onChange={(e) =>
                    setEditAddr(e.target.value)
                  }
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
                />
              </div>

            </div>

            <div className="flex gap-3">

              <button
                onClick={handleSave}
                disabled={saving}
                className="bg-blue-600 text-white px-5 py-2 rounded-lg text-sm font-medium disabled:opacity-60"
              >
                {saving ? 'Saving...' : 'Save Changes'}
              </button>

              <button
                onClick={() => setEditing(false)}
                className="text-gray-500 text-sm px-4 py-2 hover:bg-gray-100 rounded-lg"
              >
                Cancel
              </button>

            </div>

          </div>

        ) : (

          <div className="mt-6 grid grid-cols-2 md:grid-cols-4 gap-5 text-sm">

            {[
              {
                label: 'Email',
                value: borrower.email ?? '—',
              },
              {
                label: 'Phone',
                value: borrower.phone ?? '—',
              },
              {
                label: 'National ID',
                value: borrower.nationalId ?? '—',
              },
              {
                label: 'Employer',
                value: borrower.employerName ?? '—',
              },
              {
                label: 'Monthly Income',
                value: money(
                  borrower.monthlyIncome,
                  undefined
                ),
              },
              {
                label: 'Credit Score',
                value:
                  borrower.creditScore != null
                    ? `${borrower.creditScore}`
                    : '—',
              },
              {
                label: 'Country',
                value: borrower.country ?? '—',
              },
              {
                label: 'Since',
                value: formatDate(borrower.createdAt),
              },
              {
                label: 'Marital Status',
                value: borrower.maritalStatus ?? '—',
              },
              {
                label: 'Employment',
                value: borrower.employmentType ?? '—',
              },
              {
                label: 'Job Title',
                value: borrower.jobTitle ?? '—',
              },
              {
                label: 'Address',
                value: borrower.addressLine1 ?? '—',
              },
            ].map(({ label, value }) => (

              <div key={label}>

                <p className="text-gray-400 text-xs">
                  {label}
                </p>

                <p className="font-medium text-gray-800 mt-0.5">
                  {value}
                </p>

              </div>

            ))}

          </div>

        )}

      </div>

      {/* ========================================================
          FINANCIAL OVERVIEW
      ======================================================== */}

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">

        <div className="bg-white rounded-xl border border-gray-200 p-5">
          <p className="text-gray-500 text-xs uppercase">
            Total Loans
          </p>

          <p className="text-2xl font-bold mt-1">
            {totalLoans}
          </p>
        </div>

        <div className="bg-white rounded-xl border border-gray-200 p-5">
          <p className="text-gray-500 text-xs uppercase">
            Active Loans
          </p>

          <p className="text-2xl font-bold text-blue-600 mt-1">
            {activeLoans.length}
          </p>
        </div>

        <div className="bg-white rounded-xl border border-gray-200 p-5">
          <p className="text-gray-500 text-xs uppercase">
            Outstanding
          </p>

          <p className="text-xl font-bold text-orange-600 mt-1">
            {money(
              totalOutstanding,
              loans[0]?.currency
            )}
          </p>
        </div>

        <div className="bg-white rounded-xl border border-gray-200 p-5">
          <p className="text-gray-500 text-xs uppercase">
            Total Paid
          </p>

          <p className="text-xl font-bold text-green-600 mt-1">
            {money(
              totalAmountPaid,
              loans[0]?.currency
            )}
          </p>
        </div>

      </div>

      {/* ========================================================
          REPAYMENT PERFORMANCE
      ======================================================== */}

      <div className="bg-white rounded-xl border border-gray-200 p-6">

        <div className="flex items-center justify-between mb-5">

          <div>
            <h2 className="font-semibold text-gray-900">
              Repayment Performance
            </h2>

            <p className="text-xs text-gray-500 mt-1">
              Historical payment behaviour and borrower reliability
            </p>
          </div>

          <span
            className={`px-3 py-1.5 rounded-full border text-xs font-semibold ${riskColor}`}
          >
            {borrowerRating}
          </span>

        </div>

        <div className="grid grid-cols-2 md:grid-cols-5 gap-4">

          <div className="bg-gray-50 rounded-lg p-4">
            <p className="text-xs text-gray-500">
              Payments
            </p>

            <p className="text-xl font-bold mt-1">
              {totalPayments}
            </p>
          </div>

          <div className="bg-green-50 rounded-lg p-4">
            <p className="text-xs text-green-700">
              Successful
            </p>

            <p className="text-xl font-bold text-green-700 mt-1">
              {successfulPayments.length}
            </p>
          </div>

          <div className="bg-red-50 rounded-lg p-4">
            <p className="text-xs text-red-700">
              Late
            </p>

            <p className="text-xl font-bold text-red-700 mt-1">
              {latePayments.length}
            </p>
          </div>

          <div className="bg-blue-50 rounded-lg p-4">
            <p className="text-xs text-blue-700">
              On-Time Rate
            </p>

            <p className="text-xl font-bold text-blue-700 mt-1">
              {onTimePaymentRate.toFixed(1)}%
            </p>
          </div>

          <div className="bg-orange-50 rounded-lg p-4">
            <p className="text-xs text-orange-700">
              Max Days Late
            </p>

            <p className="text-xl font-bold text-orange-700 mt-1">
              {maximumDaysLate}
            </p>
          </div>

        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mt-4">

          <div>
            <p className="text-xs text-gray-500">
              Principal Paid
            </p>

            <p className="font-semibold mt-1">
              {money(
                totalPrincipalPaid,
                loans[0]?.currency
              )}
            </p>
          </div>

          <div>
            <p className="text-xs text-gray-500">
              Interest Paid
            </p>

            <p className="font-semibold mt-1">
              {money(
                totalInterestPaid,
                loans[0]?.currency
              )}
            </p>
          </div>

          <div>
            <p className="text-xs text-gray-500">
              Penalties
            </p>

            <p className="font-semibold mt-1">
              {money(
                totalPenalties,
                loans[0]?.currency
              )}
            </p>
          </div>

          <div>
            <p className="text-xs text-gray-500">
              Failed Payments
            </p>

            <p className="font-semibold text-red-600 mt-1">
              {failedPayments.length}
            </p>
          </div>

        </div>

      </div>

      {/* ========================================================
          CREDIT / RISK ASSESSMENT
      ======================================================== */}

      <div className="bg-white rounded-xl border border-gray-200 p-6">

        <h2 className="font-semibold text-gray-900">
          Credit & Risk Assessment
        </h2>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mt-5">

          <div>
            <p className="text-xs text-gray-500">
              Credit Score
            </p>

            <p className="text-xl font-bold mt-1">
              {borrower.creditScore ?? '—'}
            </p>
          </div>

          <div>
            <p className="text-xs text-gray-500">
              Current Status
            </p>

            <p className="font-semibold mt-1">
              {currentlyOverdue
                ? 'OVERDUE'
                : 'CURRENT'}
            </p>
          </div>

          <div>
            <p className="text-xs text-gray-500">
              Default History
            </p>

            <p
              className={`font-semibold mt-1 ${
                hasDefaultHistory
                  ? 'text-red-600'
                  : 'text-green-600'
              }`}
            >
              {hasDefaultHistory ? 'YES' : 'NO'}
            </p>
          </div>

          <div>
            <p className="text-xs text-gray-500">
              Multiple Active Loans
            </p>

            <p
              className={`font-semibold mt-1 ${
                hasMultipleActiveLoans
                  ? 'text-orange-600'
                  : 'text-green-600'
              }`}
            >
              {hasMultipleActiveLoans ? 'YES' : 'NO'}
            </p>
          </div>

        </div>

      </div>

      {/* ========================================================
          DOCUMENTS
      ======================================================== */}

      <DocumentsPanel borrowerId={borrowerId} />

      {/* ========================================================
          LOAN HISTORY
      ======================================================== */}

      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">

        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">

          <div>
            <h2 className="font-semibold text-gray-800 text-sm">
              Loan History
            </h2>

            <p className="text-xs text-gray-400 mt-1">
              Complete borrowing history
            </p>
          </div>

          <Link
            href="/dashboard/loans/new"
            className="text-blue-600 text-xs hover:underline"
          >
            + New Loan
          </Link>

        </div>

        <div className="overflow-x-auto">

          <table className="w-full text-sm">

            <thead className="bg-gray-50 text-gray-500 text-xs uppercase">

              <tr>
                {[
                  'Loan',
                  'Amount',
                  'Outstanding',
                  'Interest',
                  'Duration',
                  'Due',
                  'Status',
                  '',
                ].map((h) => (
                  <th
                    key={h}
                    className="px-5 py-3 text-left font-medium"
                  >
                    {h}
                  </th>
                ))}
              </tr>

            </thead>

            <tbody className="divide-y divide-gray-100">

              {loans.length === 0 && (
                <tr>
                  <td
                    colSpan={8}
                    className="text-center py-10 text-gray-400"
                  >
                    No loans yet
                  </td>
                </tr>
              )}

              {loans.map((loan) => (

                <tr
                  key={loan.id}
                  className="hover:bg-gray-50"
                >

                  <td className="px-5 py-4">

                    <p className="font-medium text-gray-900">
                      {loan.referenceNumber}
                    </p>

                    <p className="text-xs text-gray-400">
                      {loan.loanType}
                    </p>

                  </td>

                  <td className="px-5 py-4 font-medium">
                    {money(
                      loan.amount,
                      loan.currency
                    )}
                  </td>

                  <td className="px-5 py-4 text-orange-600 font-medium">
                    {money(
                      loan.outstandingBalance,
                      loan.currency
                    )}
                  </td>

                  <td className="px-5 py-4 text-gray-500">
                    {loan.interestRate}%
                  </td>

                  <td className="px-5 py-4 text-gray-500">
                    {loan.durationMonths}m
                  </td>

                  <td className="px-5 py-4 text-gray-500">
                    {formatDate(
                      loan.nextDueDate
                    )}
                  </td>

                  <td className="px-5 py-4">
                    <LoanStatusBadge
                      status={loan.status}
                    />
                  </td>

                  <td className="px-5 py-4">

                    <Link
                      href={`/dashboard/loans/${loan.id}`}
                      className="text-blue-600 text-xs hover:underline"
                    >
                      View
                    </Link>

                  </td>

                </tr>

              ))}

            </tbody>

          </table>

        </div>

      </div>

      {/* ========================================================
          PAYMENT HISTORY
      ======================================================== */}

      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">

        <div className="px-6 py-4 border-b border-gray-100">

          <div className="flex items-center justify-between">

            <div>

              <h2 className="font-semibold text-gray-800 text-sm">
                Payment History
              </h2>

              <p className="text-xs text-gray-400 mt-1">
                Every payment made by this borrower
              </p>

            </div>

            <div className="text-right">

              <p className="text-xs text-gray-400">
                Total Paid
              </p>

              <p className="font-bold text-green-600">
                {money(
                  totalAmountPaid,
                  loans[0]?.currency
                )}
              </p>

            </div>

          </div>

        </div>

        <div className="overflow-x-auto">

          <table className="w-full text-sm">

            <thead className="bg-gray-50 text-gray-500 text-xs uppercase">

              <tr>

                {[
                  '#',
                  'Loan',
                  'Amount',
                  'Principal',
                  'Interest',
                  'Penalty',
                  'Due Date',
                  'Paid Date',
                  'Method',
                  'Status',
                ].map((h) => (

                  <th
                    key={h}
                    className="px-4 py-3 text-left font-medium whitespace-nowrap"
                  >
                    {h}
                  </th>

                ))}

              </tr>

            </thead>

            <tbody className="divide-y divide-gray-100">

              {payments.length === 0 && (

                <tr>

                  <td
                    colSpan={10}
                    className="text-center py-12 text-gray-400"
                  >
                    No payment history available
                  </td>

                </tr>

              )}

              {payments.map((payment, index) => {

                const amount =
                  payment.amountPaid ??
                  payment.amount ??
                  0;

                const principal =
                  payment.principalComponent ??
                  payment.principal ??
                  0;

                const interest =
                  payment.interestComponent ??
                  payment.interest ??
                  0;

                const late =
                  payment.onTime === false ||
                  Number(payment.daysLate ?? 0) > 0;

                return (

                  <tr
                    key={
                      payment.id ??
                      payment.paymentId ??
                      `${payment.loanId}-${index}`
                    }
                    className="hover:bg-gray-50"
                  >

                    <td className="px-4 py-4 text-gray-400">
                      {payment.installmentNumber ??
                        index + 1}
                    </td>

                    <td className="px-4 py-4">

                      {payment.loanId ? (

                        <Link
                          href={`/dashboard/loans/${payment.loanId}`}
                          className="text-blue-600 hover:underline font-medium"
                        >
                          {payment.loanReference ??
                            payment.loanNumber ??
                            `Loan #${payment.loanId}`}
                        </Link>

                      ) : (

                        <span className="text-gray-500">
                          —
                        </span>

                      )}

                    </td>

                    <td className="px-4 py-4 font-semibold">
                      {money(
                        amount,
                        payment.currency
                      )}
                    </td>

                    <td className="px-4 py-4 text-gray-600">
                      {money(
                        principal,
                        payment.currency
                      )}
                    </td>

                    <td className="px-4 py-4 text-gray-600">
                      {money(
                        interest,
                        payment.currency
                      )}
                    </td>

                    <td className="px-4 py-4 text-gray-600">
                      {money(
                        payment.penalty,
                        payment.currency
                      )}
                    </td>

                    <td className="px-4 py-4 text-gray-500 whitespace-nowrap">
                      {formatDate(
                        payment.dueDate
                      )}
                    </td>

                    <td className="px-4 py-4 whitespace-nowrap">

                      {formatDate(
                        payment.paidDate ??
                        payment.paymentDate
                      )}

                    </td>

                    <td className="px-4 py-4">

                      <span className="text-gray-600">
                        {payment.paymentMethod ??
                          payment.method ??
                          '—'}
                      </span>

                    </td>

                    <td className="px-4 py-4">

                      {late ? (

                        <div>

                          <span className="inline-flex px-2 py-1 rounded-full bg-red-50 text-red-700 text-xs font-semibold">
                            LATE
                          </span>

                          {Number(payment.daysLate ?? 0) > 0 && (
                            <span className="text-xs text-red-500 ml-1">
                              {payment.daysLate}d
                            </span>
                          )}

                        </div>

                      ) : (

                        <span className="inline-flex px-2 py-1 rounded-full bg-green-50 text-green-700 text-xs font-semibold">
                          {payment.status ??
                            'COMPLETED'}
                        </span>

                      )}

                    </td>

                  </tr>

                );

              })}

            </tbody>

          </table>

        </div>

      </div>

      {/* ========================================================
          SUMMARY
      ======================================================== */}

      <div className="bg-white rounded-xl border border-gray-200 p-6">

        <h2 className="font-semibold text-gray-900">
          Borrower Financial Summary
        </h2>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-6 mt-5">

          <div>
            <p className="text-xs text-gray-500">
              Total Borrowed
            </p>

            <p className="text-lg font-bold mt-1">
              {money(
                totalBorrowed,
                loans[0]?.currency
              )}
            </p>
          </div>

          <div>
            <p className="text-xs text-gray-500">
              Total Disbursed
            </p>

            <p className="text-lg font-bold mt-1">
              {money(
                totalDisbursed,
                loans[0]?.currency
              )}
            </p>
          </div>

          <div>
            <p className="text-xs text-gray-500">
              Principal Paid
            </p>

            <p className="text-lg font-bold text-green-600 mt-1">
              {money(
                totalPrincipalPaid,
                loans[0]?.currency
              )}
            </p>
          </div>

          <div>
            <p className="text-xs text-gray-500">
              Outstanding
            </p>

            <p className="text-lg font-bold text-orange-600 mt-1">
              {money(
                totalOutstanding,
                loans[0]?.currency
              )}
            </p>
          </div>

        </div>

      </div>

    </div>
  );
}
