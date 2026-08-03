'use client';
import { useEffect, useState } from 'react';
import { useAuth } from '@/hooks/useAuth';
import { regulatoryApi } from '@/services/regulatoryService';
import { PageSpinner } from '@/components/ui/Skeleton';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';

const fmt = (n?: number, currency = 'RWF') =>
  n == null ? '—' : new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 }).format(n) + ' ' + currency;

const PERIODS = ['DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY', 'CUSTOM'];

type BnrSummary = {
  organizationName?: string;
  bnrInstitutionCode?: string;
  reportPeriod?: string;
  periodStart?: string;
  periodEnd?: string;

  totalLoans?: number; // changed
  activeLoans?: number;
  closedLoans?: number;
  pendingLoans?: number;
  rejectedLoans?: number;
  overdueLoans?: number;
  defaultedLoans?: number;

  totalPrincipalDisbursed?: number;
  outstandingPrincipal?: number;
  totalInterestCollected?: number;
  interestAccruedUnpaid?: number;
  totalProcessingFees?: number;
  maleBorrowers?: number;
  femaleBorrowers?: number;
  otherGenderBorrowers?: number;
  parAmount?: number;
  parRatio?: number;
  nplAmount?: number;
  nplRatio?: number;
  currency?: string;
};
type BreakdownRow = { label: string; count: number; amount: number };
type CreditRecord = {
  borrowerId?: number; fullName?: string; nationalId?: string; loanNumber?: string; loanType?: string;
  loanStatus?: string; loanAmount?: number; outstandingBalance?: number; daysPastDue?: number;
  creditScore?: number; dateOpened?: string; lastPaymentDate?: string; branchName?: string;
};
type ApiClient = {
  id: number; name: string; clientType: 'BNR' | 'CREDIT_BUREAU'; keyPrefix: string; active: boolean;
  contactEmail?: string; lastUsedAt?: string; revokedAt?: string; createdAt?: string;
};

export default function RegulatoryReportsPage() {
  const { isAdmin, user } = useAuth();
  const canView = isAdmin || ['MANAGER', 'AUDITOR'].includes(user?.role || '');
  const [tab, setTab] = useState<'bnr' | 'credit-bureau' | 'api-keys'>('bnr');

  if (!canView) {
    return (
      <div className="bg-white rounded-xl border border-gray-200 p-10 text-center">
        <p className="text-3xl mb-2">🔒</p>
        <p className="text-gray-600 text-sm">You don&apos;t have access to Regulatory Reports.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-bold text-gray-900">Regulatory Reporting</h1>
        <p className="text-sm text-gray-500">BNR portfolio reports and credit bureau data exports, with secure API access for external systems.</p>
      </div>

      <div className="flex gap-2 border-b border-gray-200">
        {[
          { key: 'bnr', label: '🏦 BNR Reports' },
          { key: 'credit-bureau', label: '📇 Credit Bureau' },
          { key: 'api-keys', label: '🔑 API Access', adminOnly: true },
        ].filter(t => !t.adminOnly || isAdmin).map(t => (
          <button key={t.key} onClick={() => setTab(t.key as any)}
            className={`px-4 py-2.5 text-sm font-semibold border-b-2 -mb-px transition-colors
              ${tab === t.key ? 'border-teal-600 text-teal-700' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'bnr' && <BnrTab />}
      {tab === 'credit-bureau' && <CreditBureauTab isAdmin={!!isAdmin} />}
      {tab === 'api-keys' && isAdmin && <ApiKeysTab />}
    </div>
  );
}

// ---------------- BNR tab ----------------

function BnrTab() {
  const [period, setPeriod] = useState('MONTHLY');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [summary, setSummary] = useState<BnrSummary | null>(null);
  const [loanTypes, setLoanTypes] = useState<BreakdownRow[]>([]);
  const [branches, setBranches] = useState<BreakdownRow[]>([]);
  const [gender, setGender] = useState<BreakdownRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [exporting, setExporting] = useState(false);

  const params = { period, from: period === 'CUSTOM' ? from : undefined, to: period === 'CUSTOM' ? to : undefined };

  const load = () => {
    setLoading(true);
    setLoadError('');
    Promise.all([
      regulatoryApi.bnrSummary(params),
      regulatoryApi.bnrByLoanType(params),
      regulatoryApi.bnrByBranch(params),
      regulatoryApi.bnrByGender(params),
    ]).then(([s, lt, b, g]) => {
      setSummary(s as BnrSummary);
      setLoanTypes(lt as BreakdownRow[]);
      setBranches(b as BreakdownRow[]);
      setGender(g as BreakdownRow[]);
    }).catch((e) => {
      console.error(e);
      setLoadError(e?.response?.data?.error || e?.message || 'Could not load BNR reports.');
    }).finally(() => setLoading(false));
  };

  useEffect(() => { load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [period]);

  const doExport = async (format: 'xlsx' | 'csv' | 'pdf') => {
    setExporting(true);
    try { await regulatoryApi.bnrExport(format, params); }
    catch (e) { alert(e instanceof Error ? e.message : 'Export failed'); }
    finally { setExporting(false); }
  };

  const currency = summary?.currency || 'RWF';

  return (
    <div className="space-y-6">
      <div className="flex items-end justify-between gap-4 flex-wrap">
        <div className="flex items-end gap-3 flex-wrap">
          <div>
            <label className="block text-xs font-semibold text-gray-500 mb-1">Report Period</label>
            <select value={period} onChange={e => setPeriod(e.target.value)}
              className="border border-gray-200 rounded-lg px-3 py-2 text-sm">
              {PERIODS.map(p => <option key={p} value={p}>{p.charAt(0) + p.slice(1).toLowerCase()}</option>)}
            </select>
          </div>
          {period === 'CUSTOM' && (
            <>
              <div>
                <label className="block text-xs font-semibold text-gray-500 mb-1">From</label>
                <input type="date" value={from} onChange={e => setFrom(e.target.value)}
                  className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
              </div>
              <div>
                <label className="block text-xs font-semibold text-gray-500 mb-1">To</label>
                <input type="date" value={to} onChange={e => setTo(e.target.value)}
                  className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
              </div>
              <Button size="sm" variant="secondary" onClick={load}>Apply</Button>
            </>
          )}
        </div>
        <div className="flex gap-2">
          {(['xlsx', 'csv', 'pdf'] as const).map(f => (
            <Button key={f} size="sm" variant="outline" loading={exporting} onClick={() => doExport(f)}>
              ⬇ {f.toUpperCase()}
            </Button>
          ))}
        </div>
      </div>

      {loading ? <PageSpinner /> : loadError ? (
        <div className="bg-red-50 border border-red-200 rounded-xl p-5 text-center">
          <p className="text-red-700 text-sm font-semibold mb-3">{loadError}</p>
          <Button size="sm" variant="secondary" onClick={load}>Try Again</Button>
        </div>
      ) : !summary ? (
        <p className="text-sm text-gray-400 text-center py-8">No data available.</p>
      ) : (
        <>
          <div className="bg-white rounded-xl border border-gray-200 p-5">
            <div className="flex items-center justify-between flex-wrap gap-2 mb-1">
              <h2 className="font-semibold text-gray-800 text-sm">Loan Portfolio Summary</h2>
              <span className="text-xs text-gray-400">
                {summary.organizationName}{summary.bnrInstitutionCode ? ` · Institution Code: ${summary.bnrInstitutionCode}` : ''}
                {' '}· {summary.periodStart} to {summary.periodEnd}
              </span>
            </div>
          </div>

          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {[
              { label: 'Total Loans Issued', value: summary.totalLoans },
              { label: 'Active Loans', value: summary.activeLoans },
              { label: 'Closed Loans', value: summary.closedLoans },
              { label: 'Pending Loans', value: summary.pendingLoans },
              { label: 'Rejected Loans', value: summary.rejectedLoans },
              { label: 'Overdue Loans', value: summary.overdueLoans },
              { label: 'Defaulted / Written-off', value: summary.defaultedLoans },
            ].map(c => (
              <div key={c.label} className="bg-white rounded-xl border border-gray-200 p-4">
                <p className="text-gray-500 text-xs uppercase tracking-wide">{c.label}</p>
                <p className="text-xl font-bold mt-1 text-gray-900">{c.value ?? 0}</p>
              </div>
            ))}
          </div>

          <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
            {[
              { label: 'Total Principal Disbursed', value: fmt(summary.totalPrincipalDisbursed, currency), color: 'text-indigo-600' },
              { label: 'Outstanding Principal', value: fmt(summary.outstandingPrincipal, currency), color: 'text-blue-600' },
              { label: 'Total Interest Collected', value: fmt(summary.totalInterestCollected, currency), color: 'text-green-600' },
              { label: 'Interest Accrued (Unpaid)', value: fmt(summary.interestAccruedUnpaid, currency), color: 'text-orange-600' },
              { label: 'Portfolio at Risk (PAR)', value: `${fmt(summary.parAmount, currency)} (${((summary.parRatio || 0) * 100).toFixed(1)}%)`, color: 'text-amber-600' },
              { label: 'NPL (>90 days)', value: `${fmt(summary.nplAmount, currency)} (${((summary.nplRatio || 0) * 100).toFixed(1)}%)`, color: 'text-red-600' },
            ].map(c => (
              <div key={c.label} className="bg-white rounded-xl border border-gray-200 p-5">
                <p className="text-gray-500 text-xs uppercase tracking-wide">{c.label}</p>
                <p className={`text-lg font-bold mt-1 ${c.color}`}>{c.value}</p>
              </div>
            ))}
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div className="bg-white rounded-xl border border-gray-200 p-5">
              <h3 className="font-semibold text-gray-800 text-sm mb-3">Financial Inclusion (Gender)</h3>
              <BreakdownTable rows={gender} currency={currency} />
            </div>
            <div className="bg-white rounded-xl border border-gray-200 p-5">
              <h3 className="font-semibold text-gray-800 text-sm mb-3">By Loan Type</h3>
              <BreakdownTable rows={loanTypes} currency={currency} />
            </div>
          </div>

          <div className="bg-white rounded-xl border border-gray-200 p-5">
            <h3 className="font-semibold text-gray-800 text-sm mb-3">By Branch</h3>
            <BreakdownTable rows={branches} currency={currency} />
          </div>
        </>
      )}
    </div>
  );
}

function BreakdownTable({ rows, currency }: { rows: BreakdownRow[]; currency: string }) {
  if (rows.length === 0) return <p className="text-sm text-gray-400">No data for this period.</p>;
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="text-left text-gray-500 text-xs uppercase">
          <th className="pb-2">Category</th><th className="pb-2 text-right">Count</th><th className="pb-2 text-right">Amount</th>
        </tr>
      </thead>
      <tbody>
        {rows.map(r => (
          <tr key={r.label} className="border-t border-gray-50">
            <td className="py-2 text-gray-700">{r.label}</td>
            <td className="py-2 text-right text-gray-800 font-medium">{r.count}</td>
            <td className="py-2 text-right text-gray-600">{fmt(r.amount, currency)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

// ---------------- Credit Bureau tab ----------------

function CreditBureauTab({ isAdmin }: { isAdmin: boolean }) {
  const [records, setRecords] = useState<CreditRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [exporting, setExporting] = useState(false);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');

  const load = () => {
    setLoading(true);
    setLoadError('');
    regulatoryApi.creditBureauPreview({ from: from || undefined, to: to || undefined })
      .then(r => setRecords(r as CreditRecord[]))
      .catch((e) => {
        console.error(e);
        setLoadError(e?.response?.data?.error || e?.message || 'Could not load credit bureau records.');
      }).finally(() => setLoading(false));
  };
  useEffect(() => { load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, []);

  const doExport = async (format: 'xlsx' | 'csv' | 'pdf') => {
    setExporting(true);
    try { await regulatoryApi.creditBureauExport(format, { from: from || undefined, to: to || undefined }); }
    catch (e) { alert(e instanceof Error ? e.message : 'Export failed'); }
    finally { setExporting(false); }
  };

  return (
    <div className="space-y-4">
      <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 text-xs text-amber-800">
        This screen contains borrower-level personal data (national ID, phone, date of birth). Every view and export here is written to the audit log.
      </div>

      <div className="flex items-end justify-between gap-4 flex-wrap">
        <div className="flex items-end gap-3">
          <div>
            <label className="block text-xs font-semibold text-gray-500 mb-1">From</label>
            <input type="date" value={from} onChange={e => setFrom(e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-500 mb-1">To</label>
            <input type="date" value={to} onChange={e => setTo(e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm" />
          </div>
          <Button size="sm" variant="secondary" onClick={load}>Apply</Button>
        </div>
        <div className="flex gap-2">
          {(['xlsx', 'csv', 'pdf'] as const).map(f => (
            <Button key={f} size="sm" variant="outline" loading={exporting} onClick={() => doExport(f)}>⬇ {f.toUpperCase()}</Button>
          ))}
        </div>
      </div>

      {loading ? <PageSpinner /> : loadError ? (
        <div className="bg-red-50 border border-red-200 rounded-xl p-5 text-center">
          <p className="text-red-700 text-sm font-semibold mb-3">{loadError}</p>
          <Button size="sm" variant="secondary" onClick={load}>Try Again</Button>
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-gray-200 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500 text-xs uppercase bg-gray-50">
                <th className="px-4 py-2">Borrower</th><th className="px-4 py-2">Loan #</th><th className="px-4 py-2">Type</th>
                <th className="px-4 py-2">Status</th><th className="px-4 py-2 text-right">Amount</th>
                <th className="px-4 py-2 text-right">Outstanding</th><th className="px-4 py-2 text-right">Days Past Due</th>
                <th className="px-4 py-2">Branch</th>
              </tr>
            </thead>
            <tbody>
              {records.slice(0, 200).map((r, i) => (
                <tr key={i} className="border-t border-gray-50">
                  <td className="px-4 py-2 text-gray-800">{r.fullName}</td>
                  <td className="px-4 py-2 text-gray-600">{r.loanNumber}</td>
                  <td className="px-4 py-2 text-gray-600">{r.loanType}</td>
                  <td className="px-4 py-2"><span className="text-xs px-2 py-0.5 rounded-full bg-gray-100 text-gray-700">{r.loanStatus}</span></td>
                  <td className="px-4 py-2 text-right">{fmt(r.loanAmount)}</td>
                  <td className="px-4 py-2 text-right">{fmt(r.outstandingBalance)}</td>
                  <td className="px-4 py-2 text-right">{r.daysPastDue ?? 0}</td>
                  <td className="px-4 py-2 text-gray-500">{r.branchName}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {records.length === 0 && <p className="text-center text-sm text-gray-400 py-8">No records for this period.</p>}
          {records.length > 200 && <p className="text-center text-xs text-gray-400 py-3">Showing first 200 of {records.length} — full data is in the export.</p>}
        </div>
      )}
    </div>
  );
}

// ---------------- API Keys tab ----------------

function ApiKeysTab() {
  const [clients, setClients] = useState<ApiClient[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [newKey, setNewKey] = useState<{ apiKey: string; client: ApiClient } | null>(null);
  const [form, setForm] = useState({ name: '', clientType: 'BNR' as 'BNR' | 'CREDIT_BUREAU', contactEmail: '', description: '' });
  const [saving, setSaving] = useState(false);

  const load = () => {
    setLoading(true);
    setLoadError('');
    regulatoryApi.listApiClients().then(r => setClients(r as ApiClient[])).catch((e) => {
      console.error(e);
      setLoadError(e?.response?.data?.error || e?.message || 'Could not load API keys.');
    }).finally(() => setLoading(false));
  };
  useEffect(() => { load(); }, []);

  const create = async () => {
    if (!form.name.trim()) { alert('Name is required'); return; }
    setSaving(true);
    try {
      const res = await regulatoryApi.createApiClient(form) as { apiKey: string; client: ApiClient };
      setNewKey(res);
      setShowCreate(false);
      setForm({ name: '', clientType: 'BNR', contactEmail: '', description: '' });
      load();
    } catch (e) { alert(e instanceof Error ? e.message : 'Failed to create API key'); }
    finally { setSaving(false); }
  };

  const revoke = async (id: number) => {
    if (!confirm('Revoke this API key? Any system using it will immediately lose access.')) return;
    try { await regulatoryApi.revokeApiClient(id); load(); }
    catch (e) { alert(e instanceof Error ? e.message : 'Failed to revoke'); }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-gray-500 max-w-2xl">
          Issue API keys for external regulatory systems. A BNR key can only call the BNR report endpoints;
          a Credit Bureau key can only call the credit bureau export endpoints. Keys are scoped to your organization.
        </p>
        <Button onClick={() => setShowCreate(true)}>+ New API Key</Button>
      </div>

      {loading ? <PageSpinner /> : loadError ? (
        <div className="bg-red-50 border border-red-200 rounded-xl p-5 text-center">
          <p className="text-red-700 text-sm font-semibold mb-3">{loadError}</p>
          <Button size="sm" variant="secondary" onClick={load}>Try Again</Button>
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-gray-200 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500 text-xs uppercase bg-gray-50">
                <th className="px-4 py-2">Name</th><th className="px-4 py-2">Type</th><th className="px-4 py-2">Key Prefix</th>
                <th className="px-4 py-2">Status</th><th className="px-4 py-2">Last Used</th><th className="px-4 py-2"></th>
              </tr>
            </thead>
            <tbody>
              {clients.map(c => (
                <tr key={c.id} className="border-t border-gray-50">
                  <td className="px-4 py-2 text-gray-800 font-medium">{c.name}</td>
                  <td className="px-4 py-2">
                    <span className={`text-xs px-2 py-0.5 rounded-full ${c.clientType === 'BNR' ? 'bg-blue-100 text-blue-700' : 'bg-purple-100 text-purple-700'}`}>
                      {c.clientType === 'BNR' ? 'National Bank of Rwanda' : 'Credit Bureau'}
                    </span>
                  </td>
                  <td className="px-4 py-2 font-mono text-xs text-gray-500">{c.keyPrefix}…</td>
                  <td className="px-4 py-2">
                    {c.revokedAt || !c.active
                      ? <span className="text-xs px-2 py-0.5 rounded-full bg-red-100 text-red-700">Revoked</span>
                      : <span className="text-xs px-2 py-0.5 rounded-full bg-green-100 text-green-700">Active</span>}
                  </td>
                  <td className="px-4 py-2 text-gray-500 text-xs">{c.lastUsedAt ? new Date(c.lastUsedAt).toLocaleString() : 'Never'}</td>
                  <td className="px-4 py-2 text-right">
                    {c.active && !c.revokedAt && (
                      <Button size="xs" variant="danger" onClick={() => revoke(c.id)}>Revoke</Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {clients.length === 0 && <p className="text-center text-sm text-gray-400 py-8">No API keys issued yet.</p>}
        </div>
      )}

      <Modal open={showCreate} onClose={() => setShowCreate(false)} title="Issue New API Key"
        footer={<>
          <Button variant="secondary" onClick={() => setShowCreate(false)}>Cancel</Button>
          <Button loading={saving} onClick={create}>Create Key</Button>
        </>}>
        <div className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-gray-500 mb-1">Integration Name</label>
            <input value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
              placeholder="e.g. BNR Production Integration"
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-500 mb-1">Client Type</label>
            <select value={form.clientType} onChange={e => setForm(f => ({ ...f, clientType: e.target.value as any }))}
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm">
              <option value="BNR">National Bank of Rwanda (BNR)</option>
              <option value="CREDIT_BUREAU">Credit Bureau</option>
            </select>
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-500 mb-1">Contact Email (optional)</label>
            <input value={form.contactEmail} onChange={e => setForm(f => ({ ...f, contactEmail: e.target.value }))}
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-500 mb-1">Description (optional)</label>
            <textarea value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm" rows={2} />
          </div>
        </div>
      </Modal>

      <Modal open={!!newKey} onClose={() => setNewKey(null)} title="API Key Created"
        footer={<Button onClick={() => setNewKey(null)}>Done</Button>}>
        {newKey && (
          <div className="space-y-3">
            <p className="text-sm text-gray-600">
              Copy this key now — for security, it won&apos;t be shown again. Give it to {newKey.client.clientType === 'BNR' ? 'BNR' : 'the credit bureau'} to use in the <code className="bg-gray-100 px-1 rounded">X-Api-Key</code> header.
            </p>
            <div className="bg-gray-900 text-teal-400 font-mono text-xs p-3 rounded-lg break-all select-all">
              {newKey.apiKey}
            </div>
            <Button size="sm" variant="secondary" onClick={() => navigator.clipboard.writeText(newKey.apiKey)}>📋 Copy to Clipboard</Button>
          </div>
        )}
      </Modal>
    </div>
  );
}