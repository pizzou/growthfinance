'use client';
import { useEffect, useState, useCallback } from 'react';
import { creditBureauApi } from '../services/api';
import { useAuth } from '../hooks/useAuth';
import { toast } from '../hooks/useToast';

interface CreditBureauCheckRow {
  id: number;
  reference: string;
  provider: string;
  nationalIdChecked?: string;
  status: 'PENDING' | 'COMPLETED' | 'FAILED' | 'NO_RECORD_FOUND';
  creditScore?: number;
  riskGrade?: string;
  activeFacilities?: number;
  delinquentAccounts?: number;
  totalOutstandingDebt?: number;
  totalMonthlyObligations?: number;
  hasDefaultHistory?: boolean;
  hasActiveListing?: boolean;
  listingReason?: string;
  requestedBy?: string;
  failureReason?: string;
  createdAt: string;
  expiresAt?: string;
}

const GRADE_STYLE: Record<string, string> = {
  EXCELLENT: 'bg-green-50 text-green-700 border-green-200',
  GOOD:      'bg-blue-50 text-blue-700 border-blue-200',
  FAIR:      'bg-amber-50 text-amber-700 border-amber-200',
  POOR:      'bg-orange-50 text-orange-700 border-orange-200',
  VERY_POOR: 'bg-red-50 text-red-700 border-red-200',
};

const isSimulated = (r: CreditBureauCheckRow) => r.provider === 'INTERNAL_SIMULATED';
const isExpired = (r: CreditBureauCheckRow) => !!r.expiresAt && new Date(r.expiresAt) < new Date();

export default function CreditBureauPanel({ borrowerId, borrowerName }: { borrowerId: number; borrowerName?: string }) {
  const { currency } = useAuth();
  const [latest, setLatest] = useState<CreditBureauCheckRow | null>(null);
  const [history, setHistory] = useState<CreditBureauCheckRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [showHistory, setShowHistory] = useState(false);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const getMsg = (err: unknown) => err instanceof Error ? err.message : 'Something went wrong';

  const load = useCallback(async () => {
    const [l, h] = await Promise.all([
      creditBureauApi.latest(borrowerId).catch(() => null),
      creditBureauApi.history(borrowerId).catch(() => []),
    ]);
    setLatest(l as CreditBureauCheckRow | null);
    setHistory((h as CreditBureauCheckRow[]) ?? []);
  }, [borrowerId]);

  useEffect(() => { load().catch(console.error).finally(() => setLoading(false)); }, [load]);

  const handleRunCheck = async () => {
    setRunning(true);
    try {
      await creditBureauApi.check(borrowerId);
      toast('success', 'Credit bureau check completed');
      await load();
    } catch (err) {
      toast('error', getMsg(err));
    } finally {
      setRunning(false);
    }
  };

  const fmt = (n?: number) => n == null ? '—' : new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 }).format(n);
  const fmtDate = (d?: string) => d ? new Date(d).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' }) : '—';

  const handlePrint = () => {
    if (!latest) return;
    const win = window.open('', '_blank', 'width=800,height=1000');
    if (!win) return;
    win.document.write(`
      <html>
        <head>
          <title>Credit Bureau Report — ${borrowerName ?? 'Borrower'} #${borrowerId}</title>
          <style>
            body { font-family: Arial, sans-serif; padding: 32px; color: #1f2937; }
            h1 { font-size: 18px; margin-bottom: 4px; }
            .sub { color: #6b7280; font-size: 13px; margin-bottom: 24px; }
            table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }
            td { padding: 8px 0; border-bottom: 1px solid #e5e7eb; font-size: 13px; }
            td.label { color: #6b7280; width: 220px; }
            td.value { font-weight: 600; }
            .score { font-size: 40px; font-weight: 700; }
            .grade { display: inline-block; padding: 4px 12px; border-radius: 999px; font-size: 12px; font-weight: 700; border: 1px solid; margin-left: 12px; }
            .warn { color: #b45309; font-size: 12px; margin-top: 8px; }
            .flag { color: #b91c1c; font-weight: 700; }
          </style>
        </head>
        <body>
          <h1>Credit Bureau Report</h1>
          <div class="sub">${borrowerName ?? 'Borrower #' + borrowerId} · Reference ${latest.reference} · Generated ${new Date().toLocaleString()}</div>
          <div><span class="score">${latest.creditScore ?? '—'}</span><span class="grade" style="border-color:#999">${latest.riskGrade ?? '—'}</span></div>
          ${isSimulated(latest) ? '<div class="warn">⚠️ Internal estimate — no live bureau connected. Not a verified TransUnion/CRB report.</div>' : ''}
          <table>
            <tr><td class="label">Provider</td><td class="value">${latest.provider}</td></tr>
            <tr><td class="label">Status</td><td class="value">${latest.status}</td></tr>
            <tr><td class="label">National ID checked</td><td class="value">${latest.nationalIdChecked ?? '—'}</td></tr>
            <tr><td class="label">Active facilities</td><td class="value">${latest.activeFacilities ?? '—'}</td></tr>
            <tr><td class="label">Delinquent accounts</td><td class="value">${latest.delinquentAccounts ?? '—'}</td></tr>
            <tr><td class="label">Total outstanding debt</td><td class="value">${currency} ${fmt(latest.totalOutstandingDebt)}</td></tr>
            <tr><td class="label">Total monthly obligations</td><td class="value">${currency} ${fmt(latest.totalMonthlyObligations)}</td></tr>
            <tr><td class="label">Default history</td><td class="value ${latest.hasDefaultHistory ? 'flag' : ''}">${latest.hasDefaultHistory ? 'Yes' : 'No'}</td></tr>
            <tr><td class="label">Active negative listing</td><td class="value ${latest.hasActiveListing ? 'flag' : ''}">${latest.hasActiveListing ? 'Yes — ' + (latest.listingReason ?? '') : 'No'}</td></tr>
            <tr><td class="label">Requested by</td><td class="value">${latest.requestedBy ?? '—'}</td></tr>
            <tr><td class="label">Report date</td><td class="value">${fmtDate(latest.createdAt)}</td></tr>
            <tr><td class="label">Valid until</td><td class="value">${fmtDate(latest.expiresAt)}${isExpired(latest) ? ' (EXPIRED)' : ''}</td></tr>
          </table>
        </body>
      </html>
    `);
    win.document.close();
    win.focus();
    win.print();
  };

  if (loading) return <div className="text-sm text-gray-400 py-6">Loading credit bureau report…</div>;

  return (
    <div className="bg-white rounded-xl border border-gray-200 p-6">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-base font-bold text-gray-900">Credit Bureau Report</h2>
        <div className="flex gap-2">
          {latest && (
            <button onClick={handlePrint}
              className="text-xs font-semibold px-3 py-1.5 rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50">
              Print
            </button>
          )}
          <button onClick={handleRunCheck} disabled={running}
            className="text-xs font-semibold px-3 py-1.5 rounded-lg bg-teal-600 hover:bg-teal-700 disabled:opacity-50 text-white">
            {running ? 'Checking…' : latest ? 'Run New Check' : 'Run Check'}
          </button>
        </div>
      </div>

      {!latest && (
        <p className="text-sm text-gray-400 py-4">No credit bureau check on file yet for this borrower.</p>
      )}

      {latest && (
        <>
          {isSimulated(latest) && (
            <div className="mb-4 px-3 py-2 bg-amber-50 border border-amber-200 rounded-lg text-xs text-amber-800">
              ⚠️ Internal estimate — no live bureau connected. This is not a verified TransUnion/CRB report.
            </div>
          )}
          {isExpired(latest) && (
            <div className="mb-4 px-3 py-2 bg-gray-50 border border-gray-200 rounded-lg text-xs text-gray-600">
              This report expired on {fmtDate(latest.expiresAt)} — consider running a new check.
            </div>
          )}

          <div className="flex items-center gap-4 mb-6">
            <div className="text-4xl font-bold text-gray-900">{latest.creditScore ?? '—'}</div>
            <span className={`text-xs font-bold px-3 py-1 rounded-full border ${GRADE_STYLE[latest.riskGrade || ''] || 'bg-gray-50 text-gray-600 border-gray-200'}`}>
              {latest.riskGrade ?? 'UNKNOWN'}
            </span>
            <span className="text-xs text-gray-400">Reference {latest.reference}</span>
          </div>

          <div className="grid grid-cols-2 gap-x-8 gap-y-3 text-sm mb-2">
            <Field label="Provider" value={latest.provider} />
            <Field label="Report date" value={fmtDate(latest.createdAt)} />
            <Field label="Active facilities" value={String(latest.activeFacilities ?? '—')} />
            <Field label="Delinquent accounts" value={String(latest.delinquentAccounts ?? '—')} highlight={!!latest.delinquentAccounts} />
            <Field label="Total outstanding debt" value={`${currency} ${fmt(latest.totalOutstandingDebt)}`} />
            <Field label="Monthly obligations" value={`${currency} ${fmt(latest.totalMonthlyObligations)}`} />
            <Field label="Default history" value={latest.hasDefaultHistory ? 'Yes' : 'No'} highlight={!!latest.hasDefaultHistory} />
            <Field label="Negative listing" value={latest.hasActiveListing ? `Yes — ${latest.listingReason ?? ''}` : 'No'} highlight={!!latest.hasActiveListing} />
            <Field label="Requested by" value={latest.requestedBy ?? '—'} />
            <Field label="Valid until" value={fmtDate(latest.expiresAt)} />
          </div>
        </>
      )}

      {history.length > 0 && (
        <div className="mt-6 pt-4 border-t border-gray-100">
          <button onClick={() => setShowHistory(s => !s)} className="text-xs font-semibold text-blue-600 hover:underline">
            {showHistory ? 'Hide' : 'Show'} check history ({history.length})
          </button>

          {showHistory && (
            <table className="w-full text-sm mt-3">
              <thead className="text-left text-xs font-semibold text-gray-500 uppercase">
                <tr>
                  <th className="py-2">Date</th>
                  <th className="py-2">Provider</th>
                  <th className="py-2">Score</th>
                  <th className="py-2">Grade</th>
                  <th className="py-2">Status</th>
                  <th className="py-2">Requested By</th>
                  <th className="py-2"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {history.map(h => (
                  <>
                    <tr key={h.id}>
                      <td className="py-2">{fmtDate(h.createdAt)}</td>
                      <td className="py-2">{h.provider}</td>
                      <td className="py-2 font-semibold">{h.creditScore ?? '—'}</td>
                      <td className="py-2">{h.riskGrade ?? '—'}</td>
                      <td className="py-2">
                        <span className={`text-xs px-2 py-0.5 rounded-full ${h.status === 'COMPLETED' ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'}`}>
                          {h.status}
                        </span>
                      </td>
                      <td className="py-2 text-gray-500">{h.requestedBy ?? '—'}</td>
                      <td className="py-2 text-right">
                        <button onClick={() => setExpandedId(id => id === h.id ? null : h.id)} className="text-xs text-blue-600 hover:underline">
                          {expandedId === h.id ? 'Hide' : 'Details'}
                        </button>
                      </td>
                    </tr>
                    {expandedId === h.id && (
                      <tr key={`${h.id}-detail`}>
                        <td colSpan={7} className="py-3 bg-gray-50 px-3 text-xs text-gray-600">
                          <div className="grid grid-cols-3 gap-3">
                            <div>Active facilities: <b>{h.activeFacilities ?? '—'}</b></div>
                            <div>Delinquent accounts: <b>{h.delinquentAccounts ?? '—'}</b></div>
                            <div>Outstanding debt: <b>{currency} {fmt(h.totalOutstandingDebt)}</b></div>
                            <div>Monthly obligations: <b>{currency} {fmt(h.totalMonthlyObligations)}</b></div>
                            <div>Default history: <b>{h.hasDefaultHistory ? 'Yes' : 'No'}</b></div>
                            <div>Negative listing: <b>{h.hasActiveListing ? 'Yes' : 'No'}</b></div>
                          </div>
                          {h.failureReason && <div className="mt-2 text-red-600">Failure: {h.failureReason}</div>}
                        </td>
                      </tr>
                    )}
                  </>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
}

function Field({ label, value, highlight }: { label: string; value: string; highlight?: boolean }) {
  return (
    <div>
      <div className="text-xs text-gray-400">{label}</div>
      <div className={`font-medium ${highlight ? 'text-red-600' : 'text-gray-900'}`}>{value}</div>
    </div>
  );
}