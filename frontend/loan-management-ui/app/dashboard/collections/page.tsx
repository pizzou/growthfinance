'use client';
import { useEffect, useState, useCallback } from 'react';
import {
  getCollectionsQueue, getCollectionsStats, logCollectionAction, syncCollectionsQueue,
  CollectionCase, CollectionStats, CollectionBucket,
} from '../../../services/collectionsService';
import { PageSpinner } from '../../../components/ui/Skeleton';
import { Pill } from '../../../components/ui/Badge';
import { toast } from '../../../hooks/useToast';

const BUCKET_LABEL: Record<CollectionBucket, string> = {
  CURRENT: 'Current', DPD_1_30: '1-30 DPD', DPD_31_60: '31-60 DPD',
  DPD_61_90: '61-90 DPD', DPD_90_PLUS: '90+ DPD', WRITE_OFF: 'Written Off',
};
const BUCKET_COLOR: Record<CollectionBucket, string> = {
  CURRENT: 'green', DPD_1_30: 'yellow', DPD_31_60: 'yellow',
  DPD_61_90: 'red', DPD_90_PLUS: 'red', WRITE_OFF: 'gray',
};
const PRIORITY_COLOR: Record<string, string> = { LOW: 'gray', MEDIUM: 'blue', HIGH: 'yellow', URGENT: 'red' };

export default function CollectionsPage() {
  const [cases, setCases] = useState<CollectionCase[]>([]);
  const [stats, setStats] = useState<CollectionStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [bucketFilter, setBucketFilter] = useState<CollectionBucket | ''>('');
  const [activeCase, setActiveCase] = useState<CollectionCase | null>(null);
  const [notes, setNotes] = useState('');
  const [promiseDate, setPromiseDate] = useState('');
  const [promiseAmount, setPromiseAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [syncing, setSyncing] = useState(false);

  const getMsg = (err: unknown) => err instanceof Error ? err.message : 'Something went wrong';

  const load = useCallback(() => {
    setLoading(true);
    Promise.all([
      getCollectionsQueue(bucketFilter ? { bucket: bucketFilter } : undefined),
      getCollectionsStats(),
    ]).then(([q, s]) => { setCases(q); setStats(s); })
      .catch((e) => toast('error', getMsg(e)))
      .finally(() => setLoading(false));
  }, [bucketFilter]);

  useEffect(() => { load(); }, [load]);

  const handleSync = async () => {
    setSyncing(true);
    try { const r = await syncCollectionsQueue(); toast('success', typeof r === 'string' ? r : 'Queue synced'); await load(); }
    catch (e) { toast('error', getMsg(e)); }
    finally { setSyncing(false); }
  };

  const handleAction = async (type: 'CALL' | 'PROMISE_TO_PAY' | 'ESCALATED' | 'FIELD_VISIT') => {
    if (!activeCase) return;
    setBusy(true);
    try {
      await logCollectionAction(activeCase.id, {
        actionType: type,
        notes: notes || undefined,
        promiseDate: type === 'PROMISE_TO_PAY' ? promiseDate : undefined,
        promiseAmount: type === 'PROMISE_TO_PAY' ? Number(promiseAmount) : undefined,
      });
      toast('success', 'Action logged');
      setActiveCase(null); setNotes(''); setPromiseDate(''); setPromiseAmount('');
      await load();
    } catch (e) { toast('error', getMsg(e)); }
    finally { setBusy(false); }
  };

  if (loading && !stats) return <PageSpinner />;

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900">Collections</h1>
          <p className="text-sm text-gray-500">Delinquency queue, aging buckets and contact history</p>
        </div>
        <button onClick={handleSync} disabled={syncing}
          className="border border-gray-300 hover:bg-gray-50 px-4 py-2 rounded-lg text-sm font-medium disabled:opacity-60">
          {syncing ? 'Syncing…' : '↻ Sync Overdue Loans'}
        </button>
      </div>

      {stats && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-400">Open Cases</p>
            <p className="text-2xl font-bold text-gray-900">{stats.totalOpenCases}</p>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-400">Total Overdue</p>
            <p className="text-2xl font-bold text-red-600">{stats.totalOverdueAmount?.toLocaleString(undefined, { maximumFractionDigits: 0 })}</p>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-400">Active Promises</p>
            <p className="text-2xl font-bold text-blue-600">{stats.activePromises}</p>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <p className="text-xs text-gray-400">90+ DPD Cases</p>
            <p className="text-2xl font-bold text-gray-900">{stats.casesByBucket?.DPD_90_PLUS ?? 0}</p>
          </div>
        </div>
      )}

      <div className="flex gap-2 flex-wrap">
        <button onClick={() => setBucketFilter('')}
          className={`px-3 py-1.5 rounded-full text-xs font-semibold border ${!bucketFilter ? 'bg-gray-900 text-white border-gray-900' : 'bg-white text-gray-600 border-gray-200'}`}>
          All
        </button>
        {(Object.keys(BUCKET_LABEL) as CollectionBucket[]).map((b) => (
          <button key={b} onClick={() => setBucketFilter(b)}
            className={`px-3 py-1.5 rounded-full text-xs font-semibold border ${bucketFilter === b ? 'bg-gray-900 text-white border-gray-900' : 'bg-white text-gray-600 border-gray-200'}`}>
            {BUCKET_LABEL[b]} {stats ? `(${stats.casesByBucket?.[b] ?? 0})` : ''}
          </button>
        ))}
      </div>

      {cases.length === 0 ? (
        <div className="bg-white rounded-xl border border-gray-200 p-16 text-center">
          <p className="text-3xl mb-3">✅</p>
          <p className="text-gray-500 font-medium">No delinquent cases in this bucket.</p>
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
              <tr>
                <th className="text-left px-4 py-3">Borrower</th>
                <th className="text-left px-4 py-3">Loan Ref</th>
                <th className="text-left px-4 py-3">Bucket</th>
                <th className="text-left px-4 py-3">Priority</th>
                <th className="text-right px-4 py-3">Overdue Amount</th>
                <th className="text-left px-4 py-3">Agent</th>
                <th className="text-left px-4 py-3">Status</th>
                <th className="text-right px-4 py-3">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {cases.map((c) => (
                <tr key={c.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium text-gray-800">
                    {c.loan?.borrower?.firstName} {c.loan?.borrower?.lastName}
                    <div className="text-xs text-gray-400">{c.loan?.borrower?.phone}</div>
                  </td>
                  <td className="px-4 py-3 text-gray-600">{c.loan?.referenceNumber}</td>
                  <td className="px-4 py-3"><Pill label={BUCKET_LABEL[c.bucket]} color={BUCKET_COLOR[c.bucket]} /></td>
                  <td className="px-4 py-3"><Pill label={c.priority} color={PRIORITY_COLOR[c.priority]} /></td>
                  <td className="px-4 py-3 text-right font-semibold text-gray-800">
                    {c.loan?.currency} {c.overdueAmount?.toLocaleString(undefined, { maximumFractionDigits: 0 })}
                  </td>
                  <td className="px-4 py-3 text-gray-600">{c.assignedAgent?.name ?? '—'}</td>
                  <td className="px-4 py-3"><Pill label={c.status.replace(/_/g, ' ')} color="gray" /></td>
                  <td className="px-4 py-3 text-right">
                    <button onClick={() => setActiveCase(c)} className="text-teal-600 hover:underline text-xs font-semibold">
                      Log Action
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {activeCase && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4" onClick={() => setActiveCase(null)}>
          <div className="bg-white rounded-xl p-6 w-full max-w-md space-y-4" onClick={(e) => e.stopPropagation()}>
            <h3 className="font-bold text-gray-900">
              Log action — {activeCase.loan?.borrower?.firstName} {activeCase.loan?.borrower?.lastName}
            </h3>
            <textarea value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Notes (optional)" rows={2}
              className="w-full border border-gray-300 rounded-lg p-3 text-sm resize-none" />
            <div className="grid grid-cols-2 gap-3">
              <input type="date" value={promiseDate} onChange={(e) => setPromiseDate(e.target.value)}
                placeholder="Promise date" className="border border-gray-300 rounded-lg p-2 text-sm" />
              <input type="number" value={promiseAmount} onChange={(e) => setPromiseAmount(e.target.value)}
                placeholder="Promise amount" className="border border-gray-300 rounded-lg p-2 text-sm" />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <button disabled={busy} onClick={() => handleAction('CALL')} className="border border-gray-300 rounded-lg py-2 text-sm font-medium hover:bg-gray-50">📞 Called</button>
              <button disabled={busy} onClick={() => handleAction('FIELD_VISIT')} className="border border-gray-300 rounded-lg py-2 text-sm font-medium hover:bg-gray-50">🚶 Field Visit</button>
              <button disabled={busy || !promiseDate || !promiseAmount} onClick={() => handleAction('PROMISE_TO_PAY')}
                className="bg-blue-600 text-white rounded-lg py-2 text-sm font-medium disabled:opacity-50">🤝 Promise to Pay</button>
              <button disabled={busy} onClick={() => handleAction('ESCALATED')} className="bg-red-600 text-white rounded-lg py-2 text-sm font-medium">⚠ Escalate</button>
            </div>
            <button onClick={() => setActiveCase(null)} className="text-gray-500 text-sm w-full text-center pt-1">Cancel</button>
          </div>
        </div>
      )}
    </div>
  );
}
