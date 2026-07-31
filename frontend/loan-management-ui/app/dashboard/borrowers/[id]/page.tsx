'use client';
import { useEffect, useState, useCallback } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { getBorrowerById, updateBorrower } from '../../../../services/borrowerService';
import { getLoansByBorrower } from '../../../../services/loanService';
import { Borrower, Loan } from '../../../../types/index';
import { KycBadge, LoanStatusBadge } from '../../../../components/ui/StatusBadge';
import { PageSpinner } from '../../../../components/ui/Skeleton';
import { toast } from '../../../../hooks/useToast';
import DocumentsPanel from '../../../../components/DocumentsPanel';

export default function BorrowerDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [borrower,  setBorrower]  = useState<Borrower | null>(null);
  const [loans,     setLoans]     = useState<Loan[]>([]);
  const [loading,   setLoading]   = useState(true);
  const [editing,   setEditing]   = useState(false);
  const [saving,    setSaving]    = useState(false);
  const [editFirst, setEditFirst] = useState('');
  const [editLast,  setEditLast]  = useState('');
  const [editPhone, setEditPhone] = useState('');
  const [editAddr,  setEditAddr]  = useState('');

  const getMsg = (err: unknown) =>
    err instanceof Error ? err.message : 'Something went wrong';

  const load = useCallback(async () => {
    const [b, l] = await Promise.all([
      getBorrowerById(Number(id)),
      getLoansByBorrower(Number(id)),
    ]);
    setBorrower(b);
    setLoans(l);
    setEditFirst(b.firstName ?? '');
    setEditLast(b.lastName   ?? '');
    setEditPhone(b.phone     ?? '');
    setEditAddr(b.addressLine1 ?? '');
  }, [id]);

  useEffect(() => {
    load().catch(console.error).finally(() => setLoading(false));
  }, [load]);

  const handleSave = async () => {
    setSaving(true);
    try {
      await updateBorrower(Number(id), {
        firstName: editFirst,
        lastName:  editLast,
        phone:     editPhone,
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

  if (loading) return <PageSpinner />;
  if (!borrower) return (
    <p className="text-center text-gray-400 py-20">Borrower not found.</p>
  );

  const totalBorrowed = loans
    .filter(l => l.status === 'APPROVED')
    .reduce((s, l) => s + (l.amount ?? 0), 0);

  return (
    <div className="space-y-6 max-w-4xl">

      <Link
        href="/dashboard/borrowers"
        className="text-sm text-gray-500 hover:text-gray-700"
      >
        Back to Borrowers
      </Link>

      <div className="bg-white rounded-xl border border-gray-200 p-6">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center text-xl font-bold">
              {borrower.firstName?.[0]?.toUpperCase()}
            </div>
            <div>
              <h1 className="text-xl font-bold text-gray-900">
                {borrower.firstName} {borrower.lastName}
              </h1>
              <KycBadge status={borrower.kycStatus} />
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

        {editing ? (
          <div className="mt-5 space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1">
                  First Name
                </label>
                <input
                  value={editFirst}
                  onChange={e => setEditFirst(e.target.value)}
                  autoFocus
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1">
                  Last Name
                </label>
                <input
                  value={editLast}
                  onChange={e => setEditLast(e.target.value)}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1">
                  Phone
                </label>
                <input
                  value={editPhone}
                  onChange={e => setEditPhone(e.target.value)}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1">
                  Address
                </label>
                <input
                  value={editAddr}
                  onChange={e => setEditAddr(e.target.value)}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
            </div>
            <div className="flex gap-3">
              <button
                onClick={handleSave}
                disabled={saving}
                className="bg-blue-600 text-white px-5 py-2 rounded-lg text-sm font-medium disabled:opacity-60 hover:bg-blue-700 transition"
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
          <div className="mt-5 grid grid-cols-2 md:grid-cols-3 gap-4 text-sm">
            {[
              { label: 'Email',        value: borrower.email      ?? '—' },
              { label: 'Phone',        value: borrower.phone      ?? '—' },
              { label: 'National ID',  value: borrower.nationalId ?? '—' },
              { label: 'Address',      value: borrower.addressLine1 ?? '—' },
              { label: 'Credit Score', value: borrower.creditScore != null
                  ? `${borrower.creditScore}${borrower.creditBureau === 'INTERNAL_SIMULATED' ? ' (⚠️ estimate — no live bureau)' : ''}`
                  : '—' },
              { label: 'KYC Status',   value: borrower.kycStatus },
              { label: 'Marital Status', value: borrower.maritalStatus ?? '—' },
              ...(borrower.maritalStatus === 'Single' ? [
                { label: 'Single Status Certificate #', value: borrower.singleCertificateNumber ?? '—' },
              ] : []),
              ...(borrower.maritalStatus === 'Married' ? [
                { label: 'Spouse Name',        value: borrower.spouseFullName ?? '—' },
                { label: 'Spouse National ID', value: borrower.spouseNationalId ?? '—' },
                { label: 'Spouse Phone',       value: borrower.spousePhone ?? '—' },
                { label: 'Spouse Consent',     value: borrower.spouseConsent ? '✅ Given' : '⚠️ Not confirmed' },
              ] : []),
            ].map(({ label, value }) => (
              <div key={label}>
                <p className="text-gray-400 text-xs">{label}</p>
                <p className="font-medium text-gray-800 mt-0.5">{value}</p>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="grid grid-cols-3 gap-4">
        <div className="bg-white rounded-xl border border-gray-200 p-5">
          <p className="text-gray-500 text-xs uppercase tracking-wide">Total Loans</p>
          <p className="text-2xl font-bold text-gray-900 mt-1">{loans.length}</p>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 p-5">
          <p className="text-gray-500 text-xs uppercase tracking-wide">Active Loans</p>
          <p className="text-2xl font-bold text-green-600 mt-1">
            {loans.filter(l => l.status === 'APPROVED').length}
          </p>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 p-5">
          <p className="text-gray-500 text-xs uppercase tracking-wide">Total Borrowed</p>
          <p className="text-2xl font-bold text-indigo-600 mt-1">
            ${totalBorrowed.toLocaleString()}
          </p>
        </div>
      </div>

      <DocumentsPanel borrowerId={Number(id)} />

      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="font-semibold text-gray-800 text-sm">Loan History</h2>
          <Link
            href="/dashboard/loans/new"
            className="text-blue-600 text-xs hover:underline"
          >
            + New Loan
          </Link>
        </div>
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
            <tr>
              {['Amount', 'Interest', 'Duration', 'Collateral', 'Status', ''].map(h => (
                <th key={h} className="px-5 py-3 text-left font-medium">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {loans.length === 0 && (
              <tr>
                <td colSpan={6} className="text-center py-10 text-gray-400 text-sm">
                  No loans yet
                </td>
              </tr>
            )}
            {loans.map(l => (
              <tr key={l.id} className="hover:bg-gray-50">
                <td className="px-5 py-4 font-medium">
                  {l.currency} {l.amount?.toLocaleString()}
                </td>
                <td className="px-5 py-4 text-gray-500">{l.interestRate}%</td>
                <td className="px-5 py-4 text-gray-500">{l.durationMonths}m</td>
                <td className="px-5 py-4 text-gray-500">
                  {l.collateralValue
                    ? l.currency + ' ' + l.collateralValue.toLocaleString()
                    : '—'}
                </td>
                <td className="px-5 py-4">
                  <LoanStatusBadge status={l.status} />
                </td>
                <td className="px-5 py-4">
                  <Link
                    href={'/dashboard/loans/' + l.id}
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
  );
}