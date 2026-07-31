'use client';
import { useEffect, useState, useCallback } from 'react';
import { expenseApi, bankAccountApi, branchApi } from '@/services/api';
import { PageSpinner } from '@/components/ui/Skeleton';
import { useAuth } from '@/hooks/useAuth';

const CATEGORIES = [
  { value: 'SALARIES_AND_WAGES',      label: 'Salaries and Wages' },
  { value: 'RENT',                    label: 'Rent' },
  { value: 'UTILITIES',               label: 'Utilities' },
  { value: 'INTERNET',                label: 'Internet' },
  { value: 'TRANSPORT',               label: 'Transport' },
  { value: 'FUEL',                    label: 'Fuel' },
  { value: 'OFFICE_SUPPLIES',         label: 'Office Supplies' },
  { value: 'BANK_CHARGES',            label: 'Bank Charges' },
  { value: 'INSURANCE',               label: 'Insurance' },
  { value: 'MARKETING',               label: 'Marketing' },
  { value: 'LEGAL_FEES',              label: 'Legal Fees' },
  { value: 'AUDIT_FEES',              label: 'Audit Fees' },
  { value: 'DEPRECIATION',            label: 'Depreciation' },
  { value: 'LOAN_RECOVERY_EXPENSES',  label: 'Loan Recovery Expenses' },
  { value: 'IT_EXPENSES',             label: 'IT Expenses' },
  { value: 'OTHER_OPERATING_EXPENSES',label: 'Other Operating Expenses' },
];

interface BankAccountRow { id: number; name: string; accountType: string; active: boolean; }
interface BranchRow { id: number; name: string; }
interface ExpenseRow {
  id: number; expenseDate: string; category: string; amount: number; currency: string;
  description?: string; status: 'POSTED' | 'VOID';
  paymentAccount?: { id: number; name: string };
  branch?: { id: number; name: string };
  createdByName?: string; receiptFileName?: string;
}

const categoryLabel = (v: string) => CATEGORIES.find(c => c.value === v)?.label || v;

export default function ExpensesPage() {
  const { currency } = useAuth();
  const [expenses, setExpenses] = useState<ExpenseRow[]>([]);
  const [bankAccounts, setBankAccounts] = useState<BankAccountRow[]>([]);
  const [branches, setBranches] = useState<BranchRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [filterCategory, setFilterCategory] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    Promise.all([
      expenseApi.list({ category: filterCategory || undefined }).catch(() => ({ content: [] })),
      bankAccountApi.list().catch(() => []),
      branchApi.list().catch(() => []),
    ])
      .then(([exp, ba, br]) => {
        setExpenses(((exp as any)?.content ?? exp ?? []) as ExpenseRow[]);
        setBankAccounts(ba as BankAccountRow[]);
        setBranches(br as BranchRow[]);
      })
      .catch(() => setError('Could not load expenses.'))
      .finally(() => setLoading(false));
  }, [filterCategory]);

  useEffect(() => { load(); }, [load]);

  const handleVoid = async (id: number) => {
    const reason = window.prompt('Reason for voiding this expense (optional):') ?? '';
    try {
      await expenseApi.void(id, reason || undefined);
      load();
    } catch (e) {
      alert(e instanceof Error ? e.message : 'Could not void expense');
    }
  };

  const fmt = (n: number) =>
    new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 }).format(n || 0);

  if (loading) return <PageSpinner />;

  const total = expenses.filter(e => e.status === 'POSTED').reduce((s, e) => s + e.amount, 0);

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-bold text-gray-900">Expenses</h1>
          <p className="text-sm text-gray-500 mt-1">
            {expenses.length} record{expenses.length === 1 ? '' : 's'} · {currency} {fmt(total)} total posted
          </p>
        </div>
        <button
          onClick={() => setShowForm(true)}
          className="px-4 py-2.5 bg-teal-600 hover:bg-teal-700 text-white text-sm font-semibold rounded-lg transition"
        >
          + Add Expense
        </button>
      </div>

      {error && (
        <div className="mb-4 px-4 py-3 bg-red-50 text-red-700 text-sm rounded-lg border border-red-200">{error}</div>
      )}

      <div className="mb-4">
        <select
          value={filterCategory}
          onChange={e => setFilterCategory(e.target.value)}
          className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">All categories</option>
          {CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
        </select>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase">
            <tr>
              <th className="px-4 py-3">Date</th>
              <th className="px-4 py-3">Type</th>
              <th className="px-4 py-3">Amount</th>
              <th className="px-4 py-3">Payment Account</th>
              <th className="px-4 py-3">Branch</th>
              <th className="px-4 py-3">Description</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Created By</th>
              <th className="px-4 py-3"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {expenses.map(e => (
              <tr key={e.id} className={e.status === 'VOID' ? 'opacity-50' : ''}>
                <td className="px-4 py-3 whitespace-nowrap">{e.expenseDate}</td>
                <td className="px-4 py-3">{categoryLabel(e.category)}</td>
                <td className="px-4 py-3 font-semibold">{e.currency} {fmt(e.amount)}</td>
                <td className="px-4 py-3">{e.paymentAccount?.name ?? '—'}</td>
                <td className="px-4 py-3">{e.branch?.name ?? '—'}</td>
                <td className="px-4 py-3 text-gray-500 max-w-xs truncate">{e.description ?? '—'}</td>
                <td className="px-4 py-3">
                  <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${
                    e.status === 'POSTED' ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-500'
                  }`}>{e.status}</span>
                </td>
                <td className="px-4 py-3 text-gray-500">{e.createdByName ?? '—'}</td>
                <td className="px-4 py-3 text-right space-x-3 whitespace-nowrap">
                  {e.receiptFileName && (
                    <a href={expenseApi.receiptUrl(e.id)} target="_blank" rel="noreferrer"
                       className="text-xs text-blue-600 hover:underline">Receipt</a>
                  )}
                  {e.status === 'POSTED' && (
                    <button onClick={() => handleVoid(e.id)} className="text-xs text-red-600 hover:underline">Void</button>
                  )}
                </td>
              </tr>
            ))}
            {expenses.length === 0 && (
              <tr><td colSpan={9} className="px-4 py-10 text-center text-gray-400">No expenses recorded yet.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {showForm && (
        <AddExpenseModal
          bankAccounts={bankAccounts}
          branches={branches}
          onClose={() => setShowForm(false)}
          onSaved={() => { setShowForm(false); load(); }}
        />
      )}
    </div>
  );
}

function AddExpenseModal({ bankAccounts, branches, onClose, onSaved }: {
  bankAccounts: BankAccountRow[]; branches: BranchRow[];
  onClose: () => void; onSaved: () => void;
}) {
  const [expenseDate, setExpenseDate] = useState(new Date().toISOString().slice(0, 10));
  const [category, setCategory] = useState('OFFICE_SUPPLIES');
  const [amount, setAmount] = useState('');
  const [paymentAccountId, setPaymentAccountId] = useState('');
  const [branchId, setBranchId] = useState('');
  const [description, setDescription] = useState('');
  const [receipt, setReceipt] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!paymentAccountId) { setError('Select which account this was paid from.'); return; }
    setSaving(true);
    setError('');
    try {
      await expenseApi.create({
        expenseDate, category, amount: Number(amount),
        paymentAccountId: Number(paymentAccountId),
        branchId: branchId ? Number(branchId) : undefined,
        description: description || undefined,
        receipt,
      });
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not record expense');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-xl w-full max-w-lg max-h-[90vh] overflow-y-auto">
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <h2 className="text-lg font-bold text-gray-900">Add Expense</h2>

          {error && <div className="px-3 py-2 bg-red-50 text-red-700 text-sm rounded-lg">{error}</div>}

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Date *</label>
            <input type="date" required value={expenseDate} onChange={e => setExpenseDate(e.target.value)}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Expense Type *</label>
            <select required value={category} onChange={e => setCategory(e.target.value)}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
              {CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Amount *</label>
            <input type="number" min="1" step="0.01" required value={amount} onChange={e => setAmount(e.target.value)}
              placeholder="0.00"
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Payment Account *</label>
            <select required value={paymentAccountId} onChange={e => setPaymentAccountId(e.target.value)}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
              <option value="">Select account...</option>
              {bankAccounts.filter(a => a.active).map(a => (
                <option key={a.id} value={a.id}>{a.name} ({a.accountType})</option>
              ))}
            </select>
            {bankAccounts.length === 0 && (
              <p className="text-xs text-amber-600 mt-1">
                No bank/cash accounts set up yet — create one under Accounting → Bank Accounts first.
              </p>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Branch</label>
            <select value={branchId} onChange={e => setBranchId(e.target.value)}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
              <option value="">Head Office / Org-wide</option>
              {branches.map(b => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Description</label>
            <textarea value={description} onChange={e => setDescription(e.target.value)} rows={2}
              placeholder="e.g. July office rent"
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Receipt</label>
            <input type="file" accept="application/pdf,image/jpeg,image/png,image/webp"
              onChange={e => setReceipt(e.target.files?.[0] ?? null)}
              className="w-full text-sm text-gray-600" />
          </div>

          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose}
              className="flex-1 px-4 py-2.5 border border-gray-300 rounded-lg text-sm font-semibold text-gray-700 hover:bg-gray-50">
              Cancel
            </button>
            <button type="submit" disabled={saving}
              className="flex-1 px-4 py-2.5 bg-teal-600 hover:bg-teal-700 disabled:opacity-50 text-white text-sm font-semibold rounded-lg">
              {saving ? 'Saving…' : 'Record Expense'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}