
'use client';

import {
  FormEvent,
  Suspense,
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';

import {
  createLoan,
  CreateLoanPayload,
} from '../../../../services/loanService';

import { getBorrowers } from '../../../../services/borrowerService';
import { Borrower } from '../../../../types/index';
import { toast } from '../../../../hooks/useToast';

type InterestRateType = 'MONTHLY' | 'ANNUAL';

const CURRENCIES = ['RWF','USD', 'EUR', 'KES', 'GBP', 'NGN'] as const;

const MIN_LOAN_AMOUNT = 1;
const MAX_LOAN_AMOUNT = 100_000_000_000;

const MIN_INTEREST_RATE = 0;
const MAX_MONTHLY_INTEREST_RATE = 100;
const MAX_ANNUAL_INTEREST_RATE = 100;

const MIN_DURATION_MONTHS = 1;
const MAX_DURATION_MONTHS = 360;

function LoadingState() {
  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <div className="mx-auto max-w-5xl">
        <div className="animate-pulse space-y-5">
          <div className="h-5 w-24 rounded bg-gray-200" />
          <div className="h-8 w-64 rounded bg-gray-200" />
          <div className="rounded-xl border border-gray-200 bg-white p-6">
            <div className="space-y-5">
              <div className="h-10 rounded bg-gray-100" />
              <div className="h-10 rounded bg-gray-100" />
              <div className="h-10 rounded bg-gray-100" />
              <div className="h-24 rounded bg-gray-100" />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function NewLoanPage() {
  return (
    <Suspense fallback={<LoadingState />}>
      <NewLoanForm />
    </Suspense>
  );
}

function NewLoanForm() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const [borrowers, setBorrowers] = useState<Borrower[]>([]);
  const [borrowersLoading, setBorrowersLoading] = useState(true);
  const [borrowersError, setBorrowersError] = useState<string | null>(null);

  const [loading, setLoading] = useState(false);

  const [borrowerId, setBorrowerId] = useState(
    searchParams.get('borrowerId') ?? ''
  );

  const [amount, setAmount] = useState('');
  const [interestRate, setInterestRate] = useState('');
  const [interestRateType, setInterestRateType] =
    useState<InterestRateType>('MONTHLY');

  const [durationMonths, setDurationMonths] = useState('');
  const [currency, setCurrency] = useState('USD');

  const [startDate, setStartDate] = useState(
    new Date().toISOString().slice(0, 10)
  );

  const [notes, setNotes] = useState('');
  const [collateralValue, setCollateralValue] = useState('');
  const [collateralDesc, setCollateralDesc] = useState('');

  const [submitted, setSubmitted] = useState(false);

  const getMsg = (err: unknown): string => {
    if (err instanceof Error && err.message.trim()) {
      return err.message;
    }

    if (
      typeof err === 'object' &&
      err !== null &&
      'message' in err &&
      typeof (err as { message?: unknown }).message === 'string'
    ) {
      return String((err as { message: string }).message);
    }

    return 'Unable to create the loan application. Please try again.';
  };

  const loadBorrowers = useCallback(async () => {
    setBorrowersLoading(true);
    setBorrowersError(null);

    try {
      const data = await getBorrowers();

      const normalized = Array.isArray(data)
        ? (data as Borrower[])
        : [];

      setBorrowers(normalized);

      if (
        borrowerId &&
        !normalized.some(
          (borrower) => String(borrower.id) === String(borrowerId)
        )
      ) {
        setBorrowerId('');
      }
    } catch (error) {
      console.error('Failed to load borrowers:', error);
      setBorrowersError(
        'Unable to load borrowers. Please refresh and try again.'
      );
    } finally {
      setBorrowersLoading(false);
    }
  }, [borrowerId]);

  useEffect(() => {
    loadBorrowers();
  }, [loadBorrowers]);

  const selectedBorrower = useMemo(() => {
    if (!borrowerId) {
      return undefined;
    }

    return borrowers.find(
      (borrower) => String(borrower.id) === String(borrowerId)
    );
  }, [borrowers, borrowerId]);

  const numericAmount = useMemo(() => {
    const value = Number(amount);

    return Number.isFinite(value) && value > 0 ? value : 0;
  }, [amount]);

  const numericInterestRate = useMemo(() => {
    const value = Number(interestRate);

    return Number.isFinite(value) && value >= 0 ? value : 0;
  }, [interestRate]);

  const numericDuration = useMemo(() => {
    const value = Number(durationMonths);

    return Number.isInteger(value) && value > 0 ? value : 0;
  }, [durationMonths]);

  const numericCollateralValue = useMemo(() => {
    if (!collateralValue.trim()) {
      return 0;
    }

    const value = Number(collateralValue);

    return Number.isFinite(value) && value >= 0 ? value : 0;
  }, [collateralValue]);

  /*
   * This preview is ONLY a staff-side estimate.
   *
   * The backend remains authoritative for:
   * - actual loan rate
   * - loan product limits
   * - amortization
   * - repayment schedule
   * - approval
   * - accounting
   */
  const monthlyPreview = useMemo(() => {
    if (
      numericAmount <= 0 ||
      numericDuration <= 0 ||
      numericInterestRate < 0
    ) {
      return null;
    }

    let monthlyRate: number;

    if (interestRateType === 'MONTHLY') {
      monthlyRate = numericInterestRate / 100;
    } else {
      monthlyRate = numericInterestRate / 100 / 12;
    }

    if (monthlyRate === 0) {
      return (numericAmount / numericDuration).toFixed(2);
    }

    const factor = Math.pow(
      1 + monthlyRate,
      numericDuration
    );

    if (!Number.isFinite(factor) || factor <= 0) {
      return null;
    }

    const payment =
      (numericAmount * monthlyRate * factor) /
      (factor - 1);

    if (!Number.isFinite(payment) || payment <= 0) {
      return null;
    }

    return payment.toFixed(2);
  }, [
    numericAmount,
    numericDuration,
    numericInterestRate,
    interestRateType,
  ]);

  const ltv = useMemo(() => {
    if (
      numericAmount <= 0 ||
      numericCollateralValue <= 0
    ) {
      return null;
    }

    const value =
      (numericAmount / numericCollateralValue) * 100;

    if (!Number.isFinite(value)) {
      return null;
    }

    return value.toFixed(1);
  }, [numericAmount, numericCollateralValue]);

  const today = useMemo(
    () => new Date().toISOString().slice(0, 10),
    []
  );

  const validationError = useMemo(() => {
    if (!borrowerId) {
      return 'Please select a borrower.';
    }

    if (!selectedBorrower) {
      return 'The selected borrower could not be found. Please select the borrower again.';
    }

    if (
      !Number.isFinite(numericAmount) ||
      numericAmount < MIN_LOAN_AMOUNT ||
      numericAmount > MAX_LOAN_AMOUNT
    ) {
      return `Loan amount must be between ${MIN_LOAN_AMOUNT.toLocaleString()} and ${MAX_LOAN_AMOUNT.toLocaleString()}.`;
    }

    if (
      !Number.isFinite(numericInterestRate) ||
      numericInterestRate < MIN_INTEREST_RATE
    ) {
      return 'Interest rate cannot be negative.';
    }

    if (
      interestRateType === 'MONTHLY' &&
      numericInterestRate > MAX_MONTHLY_INTEREST_RATE
    ) {
      return 'Monthly interest rate is too high.';
    }

    if (
      interestRateType === 'ANNUAL' &&
      numericInterestRate > MAX_ANNUAL_INTEREST_RATE
    ) {
      return 'Annual interest rate is too high.';
    }

    if (
      !Number.isInteger(numericDuration) ||
      numericDuration < MIN_DURATION_MONTHS ||
      numericDuration > MAX_DURATION_MONTHS
    ) {
      return `Duration must be between ${MIN_DURATION_MONTHS} and ${MAX_DURATION_MONTHS} months.`;
    }

    if (!startDate) {
      return 'Start date is required.';
    }

    if (startDate < today) {
      return 'Start date cannot be in the past.';
    }

    if (
      collateralValue.trim() &&
      (
        !Number.isFinite(numericCollateralValue) ||
        numericCollateralValue < 0
      )
    ) {
      return 'Collateral value must be zero or greater.';
    }

    if (numericCollateralValue > 0 && numericAmount > numericCollateralValue) {
      // Do not block the application automatically.
      // LTV can legitimately exceed 100% depending on the institution's policy.
    }

    if (notes.length > 2000) {
      return 'Notes cannot exceed 2,000 characters.';
    }

    if (collateralDesc.length > 500) {
      return 'Collateral description cannot exceed 500 characters.';
    }

    return null;
  }, [
    borrowerId,
    selectedBorrower,
    numericAmount,
    numericInterestRate,
    interestRateType,
    numericDuration,
    startDate,
    today,
    collateralValue,
    numericCollateralValue,
    notes,
    collateralDesc,
  ]);

  const handleSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();

      if (loading || submitted) {
        return;
      }

      if (validationError) {
        toast('error', validationError);
        return;
      }

      setLoading(true);

      const payload: CreateLoanPayload = {
        borrowerId: Number(borrowerId),
        amount: Number(numericAmount.toFixed(2)),
        interestRate: Number(numericInterestRate.toFixed(4)),
        interestRateType,
        durationMonths: numericDuration,
        currency,
        startDate,
        notes: notes.trim() || undefined,
        collateralValue:
          collateralValue.trim()
            ? Number(numericCollateralValue.toFixed(2))
            : undefined,
        collateralDescription:
          collateralDesc.trim() || undefined,
      };

      try {
        await createLoan(payload);

        setSubmitted(true);

        toast(
          'success',
          'Loan application submitted for review.'
        );

        /*
         * IMPORTANT:
         *
         * Creating the loan application does NOT mean it is approved.
         *
         * The backend maker-checker workflow must determine who can
         * review/approve it. The staff member who created the application
         * must not approve their own application.
         */
        router.push('/dashboard/loans');
      } catch (error) {
        console.error(
          'Failed to create loan application:',
          error
        );

        toast('error', getMsg(error));
      } finally {
        setLoading(false);
      }
    },
    [
      loading,
      submitted,
      validationError,
      borrowerId,
      numericAmount,
      numericInterestRate,
      interestRateType,
      numericDuration,
      currency,
      startDate,
      notes,
      collateralValue,
      numericCollateralValue,
      collateralDesc,
      router,
    ]
  );

  const handleRateTypeChange = (
    type: InterestRateType
  ) => {
    if (loading || submitted) {
      return;
    }

    setInterestRateType(type);
    setInterestRate('');
  };

  return (
    <div className="min-h-screen bg-gray-50 p-4 sm:p-6">
      <div className="mx-auto max-w-5xl">

        {/* Header */}
        <div className="mb-6">
          <Link
            href="/dashboard/loans"
            className="inline-flex items-center text-sm font-medium text-gray-600 hover:text-gray-900"
          >
            ← Back to Loans
          </Link>

          <div className="mt-4">
            <h1 className="text-2xl font-bold text-gray-900">
              New Loan Application
            </h1>

            <p className="mt-1 text-sm text-gray-500">
              Create a loan application for review and approval.
            </p>
          </div>
        </div>

        {/* Maker-checker notice */}
        <div className="mb-5 rounded-xl border border-blue-200 bg-blue-50 p-4">
          <div className="flex gap-3">
            <div className="mt-0.5 text-blue-600">
              ℹ
            </div>

            <div>
              <p className="text-sm font-semibold text-blue-900">
                Maker-checker approval workflow
              </p>

              <p className="mt-1 text-sm text-blue-800">
                Submitting this application creates a loan application
                for review. The staff member who creates the application
                cannot approve their own application.
              </p>
            </div>
          </div>
        </div>

        {/* Borrower loading error */}
        {borrowersError && (
          <div className="mb-5 rounded-xl border border-red-200 bg-red-50 p-4">
            <div className="flex items-center justify-between gap-4">
              <p className="text-sm text-red-700">
                {borrowersError}
              </p>

              <button
                type="button"
                onClick={loadBorrowers}
                disabled={borrowersLoading}
                className="rounded-lg border border-red-300 bg-white px-3 py-2 text-xs font-semibold text-red-700 hover:bg-red-100 disabled:opacity-50"
              >
                Retry
              </button>
            </div>
          </div>
        )}

        <form
          onSubmit={handleSubmit}
          className="space-y-6 rounded-xl border border-gray-200 bg-white p-5 shadow-sm sm:p-6"
        >
          {/* Borrower */}
          <section>
            <div className="mb-4">
              <h2 className="text-sm font-semibold text-gray-900">
                Borrower
              </h2>

              <p className="mt-1 text-xs text-gray-500">
                Select the existing borrower receiving this loan.
              </p>
            </div>

            <label
              htmlFor="borrower"
              className="mb-1.5 block text-sm font-medium text-gray-700"
            >
              Borrower *
            </label>

            <select
              id="borrower"
              value={borrowerId}
              onChange={(event) =>
                setBorrowerId(event.target.value)
              }
              disabled={borrowersLoading || loading || submitted}
              required
              className="w-full rounded-lg border border-gray-300 px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
            >
              <option value="">
                {borrowersLoading
                  ? 'Loading borrowers...'
                  : 'Select a borrower...'}
              </option>

              {borrowers.map((borrower) => (
                <option
                  key={borrower.id}
                  value={borrower.id}
                >
                  {borrower.firstName} {borrower.lastName}
                </option>
              ))}
            </select>

            {selectedBorrower && (
              <div className="mt-3 rounded-lg bg-gray-50 px-4 py-3">
                <p className="text-xs text-gray-500">
                  Selected borrower
                </p>

                <p className="mt-0.5 text-sm font-semibold text-gray-900">
                  {selectedBorrower.firstName}{' '}
                  {selectedBorrower.lastName}
                </p>
              </div>
            )}
          </section>

          <div className="border-t border-gray-100" />

          {/* Loan terms */}
          <section>
            <div className="mb-4">
              <h2 className="text-sm font-semibold text-gray-900">
                Loan Terms
              </h2>

              <p className="mt-1 text-xs text-gray-500">
                Enter the proposed lending terms. Final terms remain
                subject to institutional policy and approval.
              </p>
            </div>

            <div className="grid grid-cols-1 gap-5 md:grid-cols-2">

              {/* Amount */}
              <div>
                <label
                  htmlFor="amount"
                  className="mb-1.5 block text-sm font-medium text-gray-700"
                >
                  Loan Amount *
                </label>

                <div className="flex">
                  <select
                    value={currency}
                    onChange={(event) =>
                      setCurrency(event.target.value)
                    }
                    disabled={loading || submitted}
                    className="rounded-l-lg border border-r-0 border-gray-300 bg-gray-50 px-3 py-2.5 text-sm focus:outline-none disabled:bg-gray-100"
                  >
                    {CURRENCIES.map((item) => (
                      <option key={item} value={item}>
                        {item}
                      </option>
                    ))}
                  </select>

                  <input
                    id="amount"
                    type="number"
                    min={MIN_LOAN_AMOUNT}
                    max={MAX_LOAN_AMOUNT}
                    step="0.01"
                    required
                    value={amount}
                    onChange={(event) =>
                      setAmount(event.target.value)
                    }
                    disabled={loading || submitted}
                    placeholder="0.00"
                    className="min-w-0 flex-1 rounded-r-lg border border-gray-300 px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
                  />
                </div>
              </div>

              {/* Duration */}
              <div>
                <label
                  htmlFor="duration"
                  className="mb-1.5 block text-sm font-medium text-gray-700"
                >
                  Duration (months) *
                </label>

                <input
                  id="duration"
                  type="number"
                  min={MIN_DURATION_MONTHS}
                  max={MAX_DURATION_MONTHS}
                  step="1"
                  required
                  value={durationMonths}
                  onChange={(event) =>
                    setDurationMonths(event.target.value)
                  }
                  disabled={loading || submitted}
                  placeholder="e.g. 12"
                  className="w-full rounded-lg border border-gray-300 px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
                />
              </div>

              {/* Interest type */}
              <div>
                <label className="mb-1.5 block text-sm font-medium text-gray-700">
                  Interest Rate Type *
                </label>

                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() =>
                      handleRateTypeChange('MONTHLY')
                    }
                    disabled={loading || submitted}
                    className={`rounded-lg border py-2.5 text-sm font-semibold transition ${
                      interestRateType === 'MONTHLY'
                        ? 'border-blue-600 bg-blue-600 text-white'
                        : 'border-gray-300 bg-white text-gray-700 hover:border-blue-400'
                    } disabled:cursor-not-allowed disabled:opacity-60`}
                  >
                    Monthly
                  </button>

                  <button
                    type="button"
                    onClick={() =>
                      handleRateTypeChange('ANNUAL')
                    }
                    disabled={loading || submitted}
                    className={`rounded-lg border py-2.5 text-sm font-semibold transition ${
                      interestRateType === 'ANNUAL'
                        ? 'border-blue-600 bg-blue-600 text-white'
                        : 'border-gray-300 bg-white text-gray-700 hover:border-blue-400'
                    } disabled:cursor-not-allowed disabled:opacity-60`}
                  >
                    Annual
                  </button>
                </div>
              </div>

              {/* Interest rate */}
              <div>
                <label
                  htmlFor="interestRate"
                  className="mb-1.5 block text-sm font-medium text-gray-700"
                >
                  Interest Rate (
                  {interestRateType === 'MONTHLY'
                    ? '% per month'
                    : '% per year'}
                  ) *
                </label>

                <input
                  id="interestRate"
                  type="number"
                  min="0"
                  max={
                    interestRateType === 'MONTHLY'
                      ? MAX_MONTHLY_INTEREST_RATE
                      : MAX_ANNUAL_INTEREST_RATE
                  }
                  step="0.01"
                  required
                  value={interestRate}
                  onChange={(event) =>
                    setInterestRate(event.target.value)
                  }
                  disabled={loading || submitted}
                  placeholder={
                    interestRateType === 'MONTHLY'
                      ? 'e.g. 10'
                      : 'e.g. 12'
                  }
                  className="w-full rounded-lg border border-gray-300 px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
                />

                {interestRateType === 'MONTHLY' && (
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {[6, 8, 10].map((rate) => (
                      <button
                        key={rate}
                        type="button"
                        disabled={loading || submitted}
                        onClick={() =>
                          setInterestRate(String(rate))
                        }
                        className={`rounded border px-2.5 py-1 text-xs font-semibold transition ${
                          interestRate === String(rate)
                            ? 'border-blue-300 bg-blue-50 text-blue-700'
                            : 'border-gray-300 text-gray-600 hover:border-blue-400'
                        } disabled:opacity-50`}
                      >
                        {rate}% / mo
                      </button>
                    ))}
                  </div>
                )}
              </div>

              {/* Start date */}
              <div>
                <label
                  htmlFor="startDate"
                  className="mb-1.5 block text-sm font-medium text-gray-700"
                >
                  Start Date *
                </label>

                <input
                  id="startDate"
                  type="date"
                  required
                  value={startDate}
                  onChange={(event) =>
                    setStartDate(event.target.value)
                  }
                  disabled={loading || submitted}
                  className="w-full rounded-lg border border-gray-300 px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
                />
              </div>
            </div>
          </section>

          <div className="border-t border-gray-100" />

          {/* Collateral */}
          <section>
            <div className="mb-4">
              <h2 className="text-sm font-semibold text-gray-900">
                Collateral
                <span className="ml-1 font-normal text-gray-400">
                  (optional)
                </span>
              </h2>

              <p className="mt-1 text-xs text-gray-500">
                Record collateral information where applicable.
              </p>
            </div>

            <div className="grid grid-cols-1 gap-5 md:grid-cols-2">

              <div>
                <label
                  htmlFor="collateralValue"
                  className="mb-1.5 block text-sm font-medium text-gray-700"
                >
                  Collateral Value ({currency})
                </label>

                <input
                  id="collateralValue"
                  type="number"
                  min="0"
                  step="0.01"
                  value={collateralValue}
                  onChange={(event) =>
                    setCollateralValue(event.target.value)
                  }
                  disabled={loading || submitted}
                  placeholder="e.g. 50000"
                  className="w-full rounded-lg border border-gray-300 px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
                />
              </div>

              <div>
                <label
                  htmlFor="collateralDescription"
                  className="mb-1.5 block text-sm font-medium text-gray-700"
                >
                  Description
                </label>

                <input
                  id="collateralDescription"
                  type="text"
                  maxLength={500}
                  value={collateralDesc}
                  onChange={(event) =>
                    setCollateralDesc(event.target.value)
                  }
                  disabled={loading || submitted}
                  placeholder="e.g. Land title, Vehicle"
                  className="w-full rounded-lg border border-gray-300 px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
                />
              </div>
            </div>

            {ltv !== null && (
              <div
                className={`mt-4 rounded-lg px-4 py-3 text-xs font-medium ${
                  Number(ltv) <= 70
                    ? 'bg-green-50 text-green-700'
                    : Number(ltv) <= 90
                      ? 'bg-yellow-50 text-yellow-700'
                      : 'bg-red-50 text-red-700'
                }`}
              >
                <div className="flex flex-wrap items-center gap-2">
                  <span>
                    Loan-to-Value (LTV): {ltv}%
                  </span>

                  <span>—</span>

                  <span>
                    {Number(ltv) <= 70
                      ? 'Strong collateral coverage'
                      : Number(ltv) <= 90
                        ? 'Moderate collateral coverage'
                        : 'High LTV — review collateral policy'}
                  </span>
                </div>

                <p className="mt-1 font-normal opacity-80">
                  LTV shown here is an informational calculation only.
                  Final collateral assessment must follow institutional
                  lending policy.
                </p>
              </div>
            )}
          </section>

          <div className="border-t border-gray-100" />

          {/* Notes */}
          <section>
            <div className="mb-4">
              <h2 className="text-sm font-semibold text-gray-900">
                Application Notes
              </h2>

              <p className="mt-1 text-xs text-gray-500">
                Add relevant loan purpose or staff notes for the reviewer.
              </p>
            </div>

            <label
              htmlFor="notes"
              className="mb-1.5 block text-sm font-medium text-gray-700"
            >
              Notes
              <span className="ml-1 font-normal text-gray-400">
                (optional)
              </span>
            </label>

            <textarea
              id="notes"
              maxLength={2000}
              value={notes}
              onChange={(event) =>
                setNotes(event.target.value)
              }
              disabled={loading || submitted}
              rows={4}
              placeholder="Purpose of loan, relevant information, additional terms, or notes for the reviewer..."
              className="w-full resize-none rounded-lg border border-gray-300 px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100"
            />

            <div className="mt-1 text-right text-xs text-gray-400">
              {notes.length}/2000
            </div>
          </section>

          {/* Preview */}
          {monthlyPreview !== null && (
            <section className="rounded-xl border border-blue-200 bg-blue-50 p-4">
              <p className="text-sm font-medium text-blue-700">
                Estimated monthly installment
              </p>

              <p className="mt-1 text-2xl font-bold text-blue-900">
                {currency} {monthlyPreview}
              </p>

              <p className="mt-1 text-xs text-blue-600">
                Staff-side estimate using reducing-balance
                amortization. The server-generated repayment schedule
                is authoritative.
              </p>
            </section>
          )}

          {/* Validation */}
          {validationError && (
            <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
              <p className="text-sm text-amber-800">
                {validationError}
              </p>
            </div>
          )}

          {/* Actions */}
          <div className="flex flex-col-reverse gap-3 pt-2 sm:flex-row sm:items-center">
            <Link
              href="/dashboard/loans"
              className="rounded-lg px-6 py-2.5 text-center text-sm font-medium text-gray-600 transition hover:bg-gray-100"
            >
              Cancel
            </Link>

            <button
              type="submit"
              disabled={
                loading ||
                submitted ||
                borrowersLoading ||
                Boolean(borrowersError)
              }
              className="rounded-lg bg-blue-600 px-6 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {loading
                ? 'Submitting Application...'
                : submitted
                  ? 'Application Submitted'
                  : 'Submit Application for Review'}
            </button>
          </div>

          {/* Workflow explanation */}
          <div className="rounded-lg bg-gray-50 px-4 py-3">
            <p className="text-xs font-semibold text-gray-700">
              What happens after submission?
            </p>

            <ol className="mt-2 list-decimal space-y-1 pl-4 text-xs text-gray-500">
              <li>
                The loan application is created under your staff account.
              </li>
              <li>
                The application enters the appropriate review workflow.
              </li>
              <li>
                A permitted reviewer evaluates the application.
              </li>
              <li>
                Maker-checker separation prevents the creator from
                approving their own application.
              </li>
            </ol>
          </div>
        </form>
      </div>
    </div>
  );
}
