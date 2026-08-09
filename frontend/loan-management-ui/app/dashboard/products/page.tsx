'use client';
import { useEffect, useState } from 'react';
import { get, post, put, del } from '../../../services/api';
import { toast } from '../../../hooks/useToast';
import { PageSpinner } from '../../../components/ui/Skeleton';
import { Pill } from '../../../components/ui/Badge';
import { LOAN_TYPE_META } from '../../../lib/utils';

interface Product {
  id: number;
  name: string;
  icon?: string;
  description?: string;
  loanType: string;
  interestRate: number;
  interestRateType: 'ANNUAL' | 'MONTHLY';
  minInterestRate?: number | null;
  minAmount: number;
  maxAmount: number | null;
  minTermMonths: number;
  maxTermMonths: number;
  processingFeePercent: number;
  active: boolean;
  displayOrder?: number;
}

const LOAN_TYPES = [
  'PERSONAL','BUSINESS','MORTGAGE','AUTO','STUDENT','EMERGENCY','ASSET_FINANCE',
  'SALARY_ADVANCE','MICROFINANCE','AGRICULTURAL','TRADE_FINANCE','GROUP',
];

const emptyForm = (): Partial<Product> => ({
  name: '', icon: '💰', description: '', loanType: 'PERSONAL',
  interestRate: 10, interestRateType: 'ANNUAL', minAmount: 500000, maxAmount: 100000000,
  minTermMonths: 1, maxTermMonths: 12, processingFeePercent: 2, active: true,
});

export default function LoanProductsPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading]   = useState(true);
  const [editing, setEditing]   = useState<Partial<Product> | null>(null);
  const [saving, setSaving]     = useState(false);

  const getMsg = (err: unknown) => err instanceof Error ? err.message : 'Something went wrong';

  const load = () => {
    setLoading(true);
    get('/loan-products').then((d) => setProducts(d as Product[])).catch((e) => toast('error', getMsg(e))).finally(() => setLoading(false));
  };
  useEffect(() => { load(); }, []);

  const handleSave = async () => {
    if (!editing) return;
    setSaving(true);
    try {
      if (editing.id) await put(`/loan-products/${editing.id}`, editing);
      else await post('/loan-products', editing);
      toast('success', editing.id ? 'Product updated' : 'Product created');
      setEditing(null);
      load();
    } catch (err) { toast('error', getMsg(err)); }
    finally { setSaving(false); }
  };

  const handleToggle = async (p: Product) => {
    try { await post(`/loan-products/${p.id}/toggle`); load(); }
    catch (err) { toast('error', getMsg(err)); }
  };

  const handleDelete = async (p: Product) => {
    if (!confirm(`Delete "${p.name}"? This cannot be undone.`)) return;
    try { await del(`/loan-products/${p.id}`); toast('success', 'Product deleted'); load(); }
    catch (err) { toast('error', getMsg(err)); }
  };

  if (loading) return <PageSpinner />;

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900">Loan Products</h1>
          <p className="text-sm text-gray-500">The real rates and limits your organization offers — drives both your public website and actual loan approvals.</p>
        </div>
        <button onClick={() => setEditing(emptyForm())}
          className="bg-teal-600 hover:bg-teal-700 text-white px-4 py-2 rounded-lg text-sm font-semibold">
          + New Product
        </button>
      </div>

      {products.length === 0 ? (
        <div className="bg-white rounded-xl border border-gray-200 p-16 text-center">
          <p className="text-3xl mb-3">💰</p>
          <p className="text-gray-500 font-medium mb-1">No products configured yet.</p>
          <p className="text-sm text-gray-400">Until you add one, your website shows generic example rates that don't reflect real approvals.</p>
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
              <tr>
                <th className="text-left px-4 py-3">Product</th>
                <th className="text-left px-4 py-3">Type</th>
                <th className="text-right px-4 py-3">Rate</th>
                <th className="text-right px-4 py-3">Amount Range</th>
                <th className="text-right px-4 py-3">Term</th>
                <th className="text-left px-4 py-3">Status</th>
                <th className="text-right px-4 py-3">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {products.map((p) => (
                <tr key={p.id} className={`hover:bg-gray-50 ${!p.active ? 'opacity-50' : ''}`}>
                  <td className="px-4 py-3 font-medium text-gray-800">{p.icon} {p.name}</td>
                  <td className="px-4 py-3 text-gray-500 text-xs">{p.loanType.replace('_', ' ')}</td>
                  <td className="px-4 py-3 text-right font-semibold">{p.interestRate}%<span className="text-gray-400 font-normal text-xs"> {p.interestRateType === 'MONTHLY' ? '/mo' : 'p.a.'}</span></td>
                  <td className="px-4 py-3 text-right text-gray-600 text-xs">
                    {p.minAmount.toLocaleString()} – {p.maxAmount !== null ? p.maxAmount.toLocaleString() : <span className="text-teal-600 font-semibold">Unlimited</span>}
                  </td>
                  <td className="px-4 py-3 text-right text-gray-600 text-xs">{p.minTermMonths}–{p.maxTermMonths} mo</td>
                  <td className="px-4 py-3">
                    <button onClick={() => handleToggle(p)}>
                      <Pill label={p.active ? 'Active' : 'Inactive'} color={p.active ? 'green' : 'gray'} />
                    </button>
                  </td>
                  <td className="px-4 py-3 text-right space-x-3">
                    <button onClick={() => setEditing(p)} className="text-teal-600 hover:underline text-xs font-semibold">Edit</button>
                    <button onClick={() => handleDelete(p)} className="text-red-500 hover:underline text-xs font-semibold">Delete</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editing && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4" onClick={() => setEditing(null)}>
          <div className="bg-white rounded-xl p-6 w-full max-w-lg space-y-4 max-h-[90vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
            <h3 className="font-bold text-gray-900">{editing.id ? 'Edit Product' : 'New Loan Product'}</h3>

            <div className="grid grid-cols-4 gap-3">
              <input className="col-span-1 border border-gray-300 rounded-lg p-2 text-sm text-center" placeholder="Icon"
                value={editing.icon ?? ''} onChange={(e) => setEditing({ ...editing, icon: e.target.value })} />
              <input className="col-span-3 border border-gray-300 rounded-lg p-2 text-sm" placeholder="Product name"
                value={editing.name ?? ''} onChange={(e) => setEditing({ ...editing, name: e.target.value })} />
            </div>

            <textarea className="w-full border border-gray-300 rounded-lg p-2 text-sm resize-none" rows={2} placeholder="Description shown on your website"
              value={editing.description ?? ''} onChange={(e) => setEditing({ ...editing, description: e.target.value })} />

            <div>
              <label className="text-xs font-semibold text-gray-500">Loan Type (drives which product applies when this type of loan is created)</label>
              <select className="w-full border border-gray-300 rounded-lg p-2 text-sm mt-1"
                value={editing.loanType ?? 'PERSONAL'} onChange={(e) => setEditing({ ...editing, loanType: e.target.value })}>
                {LOAN_TYPES.map(t => <option key={t} value={t}>{LOAN_TYPE_META[t]?.label ?? t.replace('_', ' ')}</option>)}
              </select>
            </div>

            <div>
              <div className="flex items-center justify-between">
                <label className="text-xs font-semibold text-gray-500">
                  Interest Rate ({editing.interestRateType === 'MONTHLY' ? '% per month' : '% p.a.'})
                </label>
                <div className="flex rounded-md border border-gray-300 overflow-hidden text-xs font-semibold">
                  <button type="button"
                    onClick={() => setEditing({ ...editing, interestRateType: 'ANNUAL' })}
                    className={`px-2 py-0.5 transition-colors ${
                      (editing.interestRateType ?? 'ANNUAL') === 'ANNUAL' ? 'bg-teal-600 text-white' : 'bg-white text-gray-600 hover:bg-gray-50'
                    }`}>
                    p.a.
                  </button>
                  <button type="button"
                    onClick={() => setEditing({ ...editing, interestRateType: 'MONTHLY' })}
                    className={`px-2 py-0.5 border-l border-gray-300 transition-colors ${
                      editing.interestRateType === 'MONTHLY' ? 'bg-teal-600 text-white' : 'bg-white text-gray-600 hover:bg-gray-50'
                    }`}>
                    per month
                  </button>
                </div>
              </div>
              <input type="number" step="0.1" className="w-full border border-gray-300 rounded-lg p-2 text-sm mt-1"
                value={editing.interestRate ?? 0} onChange={(e) => setEditing({ ...editing, interestRate: Number(e.target.value) })} />
              <div className="flex gap-1.5 mt-1.5">
                {(editing.interestRateType === 'MONTHLY' ? [6, 8, 10] : [10, 12, 15]).map(r => (
                  <button key={r} type="button"
                    onClick={() => setEditing({ ...editing, interestRate: r })}
                    className={`text-xs px-2.5 py-1 rounded border font-semibold transition-colors
                      ${editing.interestRate === r ? 'bg-teal-600 text-white border-teal-600' : 'border-gray-300 text-gray-600 hover:border-teal-400'}`}>
                    {r}%
                  </button>
                ))}
              </div>
              <div className="mt-2">
                <label className="text-xs font-semibold text-gray-500">
                  Negotiable down to (optional)
                </label>
                <input type="number" step="0.1" min="0" placeholder="Not negotiable — approve at listed rate only"
                  className="w-full border border-gray-300 rounded-lg p-2 text-sm mt-1"
                  value={editing.minInterestRate ?? ''}
                  onChange={(e) => setEditing({ ...editing, minInterestRate: e.target.value === '' ? null : Number(e.target.value) })} />
                <p className="text-[11px] text-gray-400 mt-1">
                  Lets a loan officer approve this product below the listed rate, down to this floor — e.g. 10% advertised, 6% floor for negotiated deals. Leave blank if the rate isn&apos;t negotiable.
                </p>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs font-semibold text-gray-500">Processing Fee (%)</label>
                <input type="number" step="0.1" className="w-full border border-gray-300 rounded-lg p-2 text-sm mt-1"
                  value={editing.processingFeePercent ?? 2} onChange={(e) => setEditing({ ...editing, processingFeePercent: Number(e.target.value) })} />
              </div>
              <div>
                <label className="text-xs font-semibold text-gray-500">Min Amount</label>
                <input type="number" className="w-full border border-gray-300 rounded-lg p-2 text-sm mt-1"
                  value={editing.minAmount ?? 0} onChange={(e) => setEditing({ ...editing, minAmount: Number(e.target.value) })} />
              </div>
              <div>
                <label className="text-xs font-semibold text-gray-500">Max Amount</label>
                <input type="number" disabled={editing.maxAmount === null}
                  className="w-full border border-gray-300 rounded-lg p-2 text-sm mt-1 disabled:bg-gray-100 disabled:text-gray-400"
                  value={editing.maxAmount ?? ''} placeholder={editing.maxAmount === null ? 'Unlimited' : ''}
                  onChange={(e) => setEditing({ ...editing, maxAmount: Number(e.target.value) })} />
                <label className="flex items-center gap-1.5 text-xs text-gray-500 mt-1.5 cursor-pointer">
                  <input type="checkbox" checked={editing.maxAmount === null}
                    onChange={(e) => setEditing({ ...editing, maxAmount: e.target.checked ? null : 5000000 })} />
                  No maximum (unlimited)
                </label>
              </div>
              <div>
                <label className="text-xs font-semibold text-gray-500">Min Term (months)</label>
                <input type="number" className="w-full border border-gray-300 rounded-lg p-2 text-sm mt-1"
                  value={editing.minTermMonths ?? 0} onChange={(e) => setEditing({ ...editing, minTermMonths: Number(e.target.value) })} />
              </div>
              <div>
                <label className="text-xs font-semibold text-gray-500">Max Term (months)</label>
                <input type="number" className="w-full border border-gray-300 rounded-lg p-2 text-sm mt-1"
                  value={editing.maxTermMonths ?? 0} onChange={(e) => setEditing({ ...editing, maxTermMonths: Number(e.target.value) })} />
              </div>
            </div>

            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={editing.active ?? true} onChange={(e) => setEditing({ ...editing, active: e.target.checked })} />
              Active (visible on website and available for new loans)
            </label>

            <div className="flex gap-2 pt-2">
              <button onClick={handleSave} disabled={saving || !editing.name}
                className="flex-1 bg-teal-600 text-white rounded-lg py-2.5 text-sm font-semibold disabled:opacity-50">
                {saving ? 'Saving…' : editing.id ? 'Save Changes' : 'Create Product'}
              </button>
              <button onClick={() => setEditing(null)} className="px-5 border border-gray-300 rounded-lg py-2.5 text-sm font-medium">Cancel</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}