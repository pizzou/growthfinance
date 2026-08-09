'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { getLoans, approveLoan, rejectLoan } from '../../../services/loanService';
import { Loan } from '../../../types/index';
import { formatInterestRate } from '../../../lib/utils';
import { PageSpinner } from '../../../components/ui/Skeleton';
import { toast } from '../../../hooks/useToast';
import DocumentsPanel from '../../../components/DocumentsPanel';

export default function ApprovalsPage() {
  const [loans,        setLoans]        = useState<Loan[]>([]);
  const [loading,      setLoading]      = useState(true);
  const [busy,         setBusy]         = useState<number | null>(null);
  const [rejectId,     setRejectId]     = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [docsOpenFor,  setDocsOpenFor]  = useState<number | null>(null);

  const getMsg = (err: unknown) => err instanceof Error ? err.message : 'Something went wrong';

  const load = () =>
    getLoans()
      .then((all) => setLoans(all.filter((l) => l.status === 'PENDING')))
      .finally(() => setLoading(false));

  useEffect(() => { load(); }, []);

  const handleApprove = async (id: number) => {
    setBusy(id);
    try { await approveLoan(id); await load(); toast('success', 'Loan approved!'); }
    catch (err: unknown) { toast('error', getMsg(err)); }
    finally { setBusy(null); }
  };

  const handleReject = async () => {
    if (!rejectId || !rejectReason.trim()) return;
    setBusy(rejectId);
    try { await rejectLoan(rejectId, rejectReason); setRejectId(null); setRejectReason(''); await load(); toast('success', 'Loan rejected'); }
    catch (err: unknown) { toast('error', getMsg(err)); }
    finally { setBusy(null); }
  };

  if (loading) return <PageSpinner />;

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-xl font-bold text-gray-900">Loan Approvals</h1>
        <p className="text-sm text-gray-500">{loans.length} pending review</p>
      </div>
      {loans.length === 0 ? (
        <div className="bg-white rounded-xl border border-gray-200 p-16 text-center">
          <p className="text-3xl mb-3">✅</p>
          <p className="text-gray-500 font-medium">All caught up — no pending loans!</p>
        </div>
      ) : (
        <div className="space-y-4">
          {loans.map((loan) => (
            <div key={loan.id} className="bg-white rounded-xl border border-gray-200 p-6">
              <div className="flex items-start justify-between gap-4">
                <div className="space-y-3 flex-1">
                  <p className="text-lg font-semibold text-gray-800">
                    {loan.borrower?.firstName} {loan.borrower?.lastName}
                  </p>
                  <div className="grid grid-cols-3 gap-4 text-sm">
                    <div>
                      <p className="text-gray-400 text-xs">Amount</p>
                      <p className="font-semibold">{loan.currency} {loan.amount?.toLocaleString()}</p>
                    </div>
                    <div>
                      <p className="text-gray-400 text-xs">Interest</p>
                      <p className="font-semibold">{formatInterestRate(loan.interestRate, loan.interestRateType)}</p>
                    </div>
                    <div>
                      <p className="text-gray-400 text-xs">Duration</p>
                      <p className="font-semibold">{loan.durationMonths} months</p>
                    </div>
                  </div>
                  {loan.notes && <p className="text-gray-400 text-sm italic">&quot;{loan.notes}&quot;</p>}
                  {loan.borrower && (
                    <button
                      onClick={() => setDocsOpenFor(docsOpenFor === loan.id ? null : loan.id)}
                      className="text-xs font-semibold text-blue-600 hover:text-blue-800 inline-flex items-center gap-1"
                    >
                      📎 {docsOpenFor === loan.id ? 'Hide' : 'View'} Uploaded Documents & KYC
                      <span className="text-gray-400">{docsOpenFor === loan.id ? '▲' : '▼'}</span>
                    </button>
                  )}
                </div>
                <div className="flex flex-col gap-2 flex-shrink-0">
                  {loan.borrower && (
                    <Link href={`/dashboard/loans/${loan.id}`}
                      className="text-center border border-gray-200 text-gray-600 hover:bg-gray-50 px-5 py-2 rounded-lg text-sm font-medium">
                      Full Application →
                    </Link>
                  )}
                  <button onClick={() => handleApprove(loan.id)} disabled={busy === loan.id}
                    className="bg-green-600 hover:bg-green-700 text-white px-5 py-2 rounded-lg text-sm font-medium disabled:opacity-60">
                    {busy === loan.id ? '...' : 'Approve'}
                  </button>
                  <button onClick={() => { setRejectId(loan.id); setRejectReason(''); }}
                    className="border border-red-200 text-red-600 hover:bg-red-50 px-5 py-2 rounded-lg text-sm font-medium">
                    Reject
                  </button>
                </div>
              </div>
              {docsOpenFor === loan.id && loan.borrower && (
                <div className="mt-4 pt-4 border-t border-gray-100">
                  <DocumentsPanel borrowerId={loan.borrower.id} />
                </div>
              )}
              {rejectId === loan.id && (
                <div className="mt-4 pt-4 border-t border-gray-100 space-y-3">
                  <textarea value={rejectReason} onChange={(e) => setRejectReason(e.target.value)}
                    placeholder="Reason for rejection (required)..." rows={2}
                    className="w-full border border-gray-300 rounded-lg p-3 text-sm focus:outline-none resize-none" />
                  <div className="flex gap-2">
                    <button onClick={handleReject} disabled={!rejectReason.trim() || busy === loan.id}
                      className="bg-red-600 text-white px-4 py-2 rounded-lg text-sm font-medium disabled:opacity-60">
                      Confirm Rejection
                    </button>
                    <button onClick={() => setRejectId(null)} className="text-gray-500 text-sm px-4 py-2">Cancel</button>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}