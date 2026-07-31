'use client';
import { useEffect, useState, Fragment } from 'react';
import { get } from '../../../services/api';
import { PageSpinner } from '../../../components/ui/Skeleton';

interface AuditLog {
  id: number;
  action: string;
  entityType: string;
  entityId?: string;
  description?: string;
  module?: string;
  timestamp: string;
  user?: { name: string; email?: string };
  ipAddress?: string;
  operatingSystem?: string;
  browser?: string;
  location?: string;
}

export default function AuditPage() {
  const [logs,    setLogs]    = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [search,  setSearch]  = useState('');
  const [expanded, setExpanded] = useState<number | null>(null);

  useEffect(() => {
    get('/audit').then((data) => setLogs(data as AuditLog[])).catch(console.error).finally(() => setLoading(false));
  }, []);

  const visible = logs.filter((l) =>
    !search ||
    l.action.toLowerCase().includes(search.toLowerCase()) ||
    (l.entityType ?? '').toLowerCase().includes(search.toLowerCase()) ||
    (l.module ?? '').toLowerCase().includes(search.toLowerCase()) ||
    (l.user?.name ?? '').toLowerCase().includes(search.toLowerCase()) ||
    (l.ipAddress ?? '').toLowerCase().includes(search.toLowerCase()) ||
    (l.location ?? '').toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-xl font-bold text-gray-900">Audit Log</h1>
        <p className="text-sm text-gray-500">Immutable record of all system actions — who, from where, and on what device</p>
      </div>
      <input value={search} onChange={(e) => setSearch(e.target.value)}
        placeholder="Filter by action, entity, user, IP or location..."
        className="px-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 max-w-sm w-full" />
      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
        {loading ? <PageSpinner /> : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
              <tr>
                {['Time', 'User', 'Action', 'Module', 'Entity', 'IP Address', 'Location', 'Device', ''].map((h) => (
                  <th key={h} className="px-5 py-3 text-left font-medium">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {visible.length === 0 && (
                <tr><td colSpan={9} className="text-center py-10 text-gray-400 text-sm">No audit entries</td></tr>
              )}
              {visible.map((l) => (
                <Fragment key={l.id}>
                  <tr className="hover:bg-gray-50 cursor-pointer" onClick={() => setExpanded(expanded === l.id ? null : l.id)}>
                    <td className="px-5 py-3 text-xs text-gray-400 whitespace-nowrap">{new Date(l.timestamp).toLocaleString()}</td>
                    <td className="px-5 py-3 font-medium text-gray-800">
                      {l.user?.name ?? <span className="text-gray-400 italic font-normal">System / Public</span>}
                    </td>
                    <td className="px-5 py-3">
                      <span className="bg-blue-100 text-blue-700 px-2.5 py-1 rounded-full text-xs font-medium">{l.action}</span>
                    </td>
                    <td className="px-5 py-3">
                      <span className="bg-purple-50 text-purple-700 px-2.5 py-1 rounded-full text-xs font-medium">{l.module ?? 'General'}</span>
                    </td>
                    <td className="px-5 py-3 text-gray-600">{l.entityType}{l.entityId ? ` #${l.entityId}` : ''}</td>
                    <td className="px-5 py-3 text-gray-500 font-mono text-xs">{l.ipAddress ?? '—'}</td>
                    <td className="px-5 py-3 text-gray-500 text-xs">{l.location ?? '—'}</td>
                    <td className="px-5 py-3 text-gray-500 text-xs">{[l.operatingSystem, l.browser].filter(Boolean).join(' · ') || '—'}</td>
                    <td className="px-5 py-3 text-gray-300 text-xs">{expanded === l.id ? '▲' : '▼'}</td>
                  </tr>
                  {expanded === l.id && l.description && (
                    <tr className="bg-gray-50">
                      <td colSpan={9} className="px-5 py-3 text-xs text-gray-500">{l.description}</td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
