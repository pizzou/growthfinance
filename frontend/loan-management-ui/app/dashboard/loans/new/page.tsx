'use client';
import { useState, useEffect, useCallback, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { createLoan, CreateLoanPayload } from '../../../../services/loanService';
import { getBorrowers } from '../../../../services/borrowerService';
import { Borrower } from '../../../../types/index';
import { toast } from '../../../../hooks/useToast';

export default function NewLoanPage() {
  return (
    <Suspense fallback={<div className="p-6 text-sm text-gray-400">Loading…</div>}>
      <NewLoanForm />
    </Suspense>
  );
}

function NewLoanForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [borrowers,      setBorrowers]      = useState<Borrower[]>([]);
  const [loading,        setLoading]        = useState(false);
  const [borrowerId,     setBorrowerId]     = useState(searchParams.get('borrowerId') ?? '');
  const [amount,         setAmount]         = useState('');
  const [interestRate,   setInterestRate]   = useState('');
  const [interestRateType, setInterestRateType] = useState<'MONTHLY' | 'ANNUAL'>('MONTHLY');
  const [durationMonths, setDurationMonths] = useState('');
  const [currency,       setCurrency]       = useState('USD');
  const [startDate,      setStartDate]      = useState(new Date().toISOString().slice(0, 10));
  const [notes,          setNotes]          = useState('');
  const [collateralValue, setCollateralValue] = useState('');
  const [collateralDesc,  setCollateralDesc]  = useState('');

  useEffect(() => {
    getBorrowers()
      .then(data => setBorrowers(data as Borrower[]))
      .catch(console.error);
  }, []);

  const getMsg = (err: unknown) =>
    err instanceof Error ? err.message : 'Something went wrong';

  const monthlyPreview = (() => {
    const P = Number(amount);
    // Previously this always divided by 1200 (i.e. treated every rate as annual, /100/12) —
    // so entering "10" here silently meant 10% per YEAR even when the officer intended 10%
    // per MONTH. Now it respects whichever type is actually selected below.
    const r = interestRateType === 'MONTHLY' ? Number(interestRate) / 100 : Number(interestRate) / 1200;
    const n = Number(durationMonths);
    if (!P || !n) return null;
    if (r === 0) return (P / n).toFixed(2);
    const M = (P * r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1);
    return isFinite(M) ? M.toFixed(2) : null;
  })();

  const ltv = (() => {
    const a = Number(amount);
    const c = Number(collateralValue);
    if (!a || !c) return null;
    return ((a / c) * 100).toFixed(1);
  })();

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    if (!borrowerId) { toast('error', 'Please select a borrower'); return; }
    setLoading(true);
    const payload: CreateLoanPayload = {
      borrowerId:            Number(borrowerId),
      amount:                Number(amount),
      interestRate:          Number(interestRate),
      interestRateType,
      durationMonths:        Number(durationMonths),
      currency,
      startDate,
      notes:                 notes.trim() || undefined,
      collateralValue:       collateralValue ? Number(collateralValue) : undefined,
      collateralDescription: collateralDesc.trim() || undefined,
    };
    try {
      await createLoan(payload);
      toast('success', 'Loan application submitted!');
      router.push('/dashboard/loans');
    } catch (err: unknown) {
      toast('error', getMsg(err));
    } finally {
      setLoading(false);
    }
  }, [borrowerId, amount, interestRate, durationMonths, currency,
      startDate, notes, collateralValue, collateralDesc, router]);

  return (
    <div className="max-w-2xl">
      <div className="mb-6">
        <Link href="/dashboard/loans" className="text-sm text-gray-500 hover:text-gray-700">
          ← Back
        </Link>
        <h1 className="text-xl font-bold text-gray-900 mt-2">New Loan Application</h1>
      </div>

      <form onSubmit={handleSubmit} className="bg-white rounded-xl border border-gray-200 p-6 space-y-5">

        {/* Borrower */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Borrower *</label>
          <select
            value={borrowerId}
            onChange={e => setBorrowerId(e.target.value)}
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">Select a borrower...</option>
            {borrowers.map(b => (
              <option key={b.id} value={b.id}>{b.firstName} {b.lastName}</option>
            ))}
          </select>
        </div>

        {/* Amount + Currency */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Amount *</label>
            <div className="flex">
              <select
                value={currency}
                onChange={e => setCurrency(e.target.value)}
                className="px-3 py-2.5 border border-r-0 border-gray-300 rounded-l-lg text-sm bg-gray-50 focus:outline-none"
              >
                {['USD','RWF','EUR','KES','GBP','NGN'].map(c => (
                  <option key={c}>{c}</option>
                ))}
              </select>
              <input
                type="number" min="1" required
                value={amount}
                onChange={e => setAmount(e.target.value)}
                placeholder="0.00"
                className="flex-1 px-4 py-2.5 border border-gray-300 rounded-r-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              Interest Rate ({interestRateType === 'MONTHLY' ? '% per month' : '% per year'}) *
            </label>
            <div className="flex gap-2 mb-2">
              <button type="button" onClick={() => setInterestRateType('MONTHLY')}
                className={`flex-1 text-xs font-semibold py-1.5 rounded-lg border transition ${
                  interestRateType === 'MONTHLY' ? 'bg-blue-600 text-white border-blue-600' : 'border-gray-300 text-gray-600 hover:border-blue-400'
                }`}>
                Monthly
              </button>
              <button type="button" onClick={() => setInterestRateType('ANNUAL')}
                className={`flex-1 text-xs font-semibold py-1.5 rounded-lg border transition ${
                  interestRateType === 'ANNUAL' ? 'bg-blue-600 text-white border-blue-600' : 'border-gray-300 text-gray-600 hover:border-blue-400'
                }`}>
                Annual
              </button>
            </div>
            <input
              type="number" step="0.1" min="0" required
              value={interestRate}
              onChange={e => setInterestRate(e.target.value)}
              placeholder={interestRateType === 'MONTHLY' ? 'e.g. 10' : 'e.g. 12'}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            {interestRateType === 'MONTHLY' && (
              <div className="flex gap-1.5 mt-1.5">
                {[6, 8, 10].map(r => (
                  <button key={r} type="button" onClick={() => setInterestRate(String(r))}
                    className={`text-xs px-2.5 py-1 rounded border font-semibold transition-colors ${
                      interestRate === String(r) ? 'bg-blue-50 text-blue-700 border-blue-300' : 'border-gray-300 text-gray-600 hover:border-blue-400'
                    }`}>
                    {r}%/mo
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Duration + Start Date */}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">
              Duration (months) *
            </label>
            <input
              type="number" min="1" max="360" required
              value={durationMonths}
              onChange={e => setDurationMonths(e.target.value)}
              placeholder="e.g. 12"
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Start Date *</label>
            <input
              type="date" required
              value={startDate}
              onChange={e => setStartDate(e.target.value)}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>

        {/* Collateral */}
        <div className="border-t border-gray-100 pt-5">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">
            Collateral <span className="text-gray-400 font-normal">(optional)</span>
          </h3>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">
                Collateral Value ({currency})
              </label>
              <input
                type="number" min="0"
                value={collateralValue}
                onChange={e => setCollateralValue(e.target.value)}
                placeholder="e.g. 50000"
                className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Description</label>
              <input
                type="text"
                value={collateralDesc}
                onChange={e => setCollateralDesc(e.target.value)}
                placeholder="e.g. Land title, Vehicle"
                className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>
          {ltv && (
            <div className={`mt-3 flex items-center gap-2 text-xs font-medium px-3 py-2 rounded-lg ${
              Number(ltv) <= 70 ? 'bg-green-50 text-green-700' :
              Number(ltv) <= 90 ? 'bg-yellow-50 text-yellow-700' :
              'bg-red-50 text-red-700'
            }`}>
              <span>📊 Loan-to-Value (LTV): {ltv}%</span>
              <span>&mdash;</span>
              <span>
                {Number(ltv) <= 70
                  ? 'Excellent coverage'
                  : Number(ltv) <= 90
                  ? 'Acceptable'
                  : 'High risk — collateral may be insufficient'}
              </span>
            </div>
          )}
        </div>

        {/* Notes */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1.5">
            Notes <span className="text-gray-400 font-normal">(optional)</span>
          </label>
          <textarea
            value={notes}
            onChange={e => setNotes(e.target.value)}
            rows={2}
            placeholder="Purpose of loan, additional terms, etc."
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
          />
        </div>

        {/* Monthly preview */}
        {monthlyPreview && (
          <div className="bg-blue-50 border border-blue-200 rounded-xl p-4">
            <p className="text-sm text-blue-700 font-medium">Estimated monthly installment</p>
            <p className="text-2xl font-bold text-blue-800 mt-0.5">
              {currency} {monthlyPreview}
            </p>
            <p className="text-xs text-blue-500 mt-1">
              Reducing balance (amortization) method
            </p>
          </div>
        )}

        <div className="flex gap-3 pt-2">
          <button
            type="submit"
            disabled={loading}
            className="bg-blue-600 hover:bg-blue-700 text-white px-6 py-2.5 rounded-lg text-sm font-medium disabled:opacity-60 transition"
          >
            {loading ? 'Submitting...' : 'Submit Application'}
          </button>
          <Link
            href="/dashboard/loans"
            className="px-6 py-2.5 rounded-lg text-sm font-medium text-gray-600 hover:bg-gray-100 transition"
          >
            Cancel
          </Link>
        </div>
      </form>
    </div>
  );
}