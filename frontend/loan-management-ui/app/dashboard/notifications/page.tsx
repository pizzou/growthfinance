'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { getLoans } from '../../../services/loanService';
import { getOverduePayments } from '../../../services/paymentService';
import { getDashboardStats } from '../../../services/dashboardService';
import { getMyNotifications, markNotificationRead } from '../../../services/notificationsService';
import { Loan, Payment, DashboardStats } from '../../../types/index';
import { PageSpinner } from '../../../components/ui/Skeleton';

interface Notif {
  id: string; type: 'danger' | 'warning' | 'success' | 'info';
  title: string; message: string; link?: string; time: string;
  realId?: number; read?: boolean;
}

export default function NotificationsPage() {
  const [notifs,  setNotifs]  = useState<Notif[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter,  setFilter]  = useState<'all' | 'danger' | 'warning' | 'success' | 'info'>('all');

  useEffect(() => {
    Promise.all([
      getLoans().catch((e) => { console.error('notifications: getLoans failed', e); return []; }),
      getOverduePayments().catch((e) => { console.error('notifications: getOverduePayments failed', e); return []; }),
      getDashboardStats().catch((e) => { console.error('notifications: getDashboardStats failed', e); return null; }),
      getMyNotifications().catch((e) => { console.error('notifications: getMyNotifications failed', e); return []; }),
    ])
      .then(([loans, overdue, stats, real]) => {
        const l = Array.isArray(loans) ? loans as Loan[] : [];
        const o = Array.isArray(overdue) ? overdue as Payment[] : [];
        const s = stats as DashboardStats | null;
        const realList = Array.isArray(real) ? real as any[] : [];
        if (!Array.isArray(real)) console.error('notifications: getMyNotifications did not return an array:', real);
        const n: Notif[] = [];

        // Real, backend-persisted notifications (new applications, contact messages, loan
        // status changes) come first and are pushed before anything else — a failure further
        // down (synthetic portfolio-derived alerts) must never wipe these out.
        realList.forEach((r) => {
          n.push({
            id: `real-${r.id}`, realId: r.id, read: r.read,
            type: (r.type as Notif['type']) || 'info',
            title: r.title, message: r.message, link: r.link,
            time: r.createdAt ? new Date(r.createdAt).toLocaleString() : '',
          });
        });

        // Everything below is derived client-side from portfolio data — wrapped separately so
        // a bad value here (e.g. an unexpected stats shape) can't blank out the real notifications above.
        try {
          if (o.length > 0)
            n.push({ id: 'ov', type: 'danger', title: `${o.length} Overdue Payment${o.length > 1 ? 's' : ''}`,
              message: `${o.length} payment${o.length > 1 ? 's are' : ' is'} past due. Penalties accruing daily.`,
              link: '/dashboard/payments', time: 'Now' });

          const pending = l.filter(x => x.status === 'PENDING');
          if (pending.length > 0)
            n.push({ id: 'pend', type: 'warning', title: `${pending.length} Loan${pending.length > 1 ? 's' : ''} Awaiting Approval`,
              message: `${pending.length} application${pending.length > 1 ? 's need' : ' needs'} your review.`,
              link: '/dashboard/approvals', time: 'Today' });

          const hr = l.filter(x => x.riskCategory === 'HIGH' || x.riskCategory === 'CRITICAL');
          if (hr.length > 0)
            n.push({ id: 'hr', type: 'warning', title: `${hr.length} High-Risk Loan${hr.length > 1 ? 's' : ''}`,
              message: `${hr.length} loan${hr.length > 1 ? 's are' : ' is'} rated HIGH or CRITICAL risk. Review collateral.`,
              link: '/dashboard/loans', time: 'Today' });

          const rate = s && s.totalDisbursed > 0 ? (s.totalCollected / s.totalDisbursed) * 100 : 0;
          if (s && rate >= 80)
            n.push({ id: 'cr-good', type: 'success', title: 'Strong Collection Rate',
              message: `Portfolio collection rate is ${rate.toFixed(0)}% — excellent performance!`, time: 'This week' });
          else if (s && rate < 50 && s.totalDisbursed > 0)
            n.push({ id: 'cr-low', type: 'warning', title: 'Low Collection Rate',
              message: `Collection rate is only ${rate.toFixed(0)}%. Consider sending payment reminders.`, time: 'This week' });

          if (s && s.completedLoans > 0)
            n.push({ id: 'closed', type: 'success', title: `${s.completedLoans} Loan${s.completedLoans > 1 ? 's' : ''} Fully Repaid`,
              message: `${s.completedLoans} loan${s.completedLoans > 1 ? 's have' : ' has'} been fully repaid. Great portfolio health!`,
              link: '/dashboard/loans', time: 'This month' });

          if (s)
            n.push({ id: 'summary', type: 'info', title: 'Portfolio Summary',
              message: `${s.totalBorrowers} borrowers · ${s.activeLoans} active loans · $${s.totalDisbursed.toLocaleString()} disbursed.`,
              link: '/dashboard/reports', time: 'Today' });
        } catch (e) {
          console.error('notifications: failed to build portfolio-derived alerts', e);
        }

        setNotifs(n);
      })
      .catch((e) => console.error('notifications: unexpected failure loading notifications', e))
      .finally(() => setLoading(false));
  }, []);

  const filtered = filter === 'all' ? notifs : notifs.filter(n => n.type === filter);

  const ICON  = { danger: '🔴', warning: '⚠️', success: '✅', info: '💡' };
  const BG    = { danger: 'bg-red-50 border-red-100', warning: 'bg-yellow-50 border-yellow-100', success: 'bg-green-50 border-green-100', info: 'bg-blue-50 border-blue-100' };
  const TXT   = { danger: 'text-red-700', warning: 'text-yellow-700', success: 'text-green-700', info: 'text-blue-700' };
  const BTN   = { danger: 'bg-red-100 text-red-700 hover:bg-red-200', warning: 'bg-yellow-100 text-yellow-700 hover:bg-yellow-200', success: 'bg-green-100 text-green-700 hover:bg-green-200', info: 'bg-blue-100 text-blue-700 hover:bg-blue-200' };

  if (loading) return <PageSpinner />;

  return (
    <div className="space-y-5 max-w-3xl">
      <div>
        <h1 className="text-xl font-bold text-gray-900">Notifications</h1>
        <p className="text-sm text-gray-500">{notifs.length} alerts for your portfolio</p>
      </div>

      <div className="flex gap-1 bg-gray-100 p-1 rounded-xl w-fit flex-wrap">
        {(['all','danger','warning','success','info'] as const).map(f => (
          <button key={f} onClick={() => setFilter(f)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium capitalize transition ${filter === f ? 'bg-white shadow text-green-600' : 'text-gray-500 hover:text-gray-800'}`}>
            {f === 'all' ? 'All' : ICON[f] + ' ' + f.charAt(0).toUpperCase() + f.slice(1)}
          </button>
        ))}
      </div>

      <div className="space-y-3">
        {filtered.length === 0 && (
          <div className="text-center py-16 bg-white rounded-2xl border border-gray-100">
            <p className="text-3xl mb-3">🔔</p>
            <p className="text-gray-500 font-medium">No notifications</p>
            <p className="text-gray-400 text-sm mt-1">All caught up!</p>
          </div>
        )}
        {filtered.map(n => (
          <div key={n.id} className={`rounded-2xl border p-5 ${BG[n.type]} ${n.realId && !n.read ? 'ring-2 ring-offset-1 ring-teal-300' : ''}`}>
            <div className="flex items-start gap-4">
              <span className="text-2xl flex-shrink-0">{ICON[n.type]}</span>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between gap-2 mb-1">
                  <p className={`font-semibold text-sm ${TXT[n.type]}`}>{n.title}</p>
                  <span className="text-xs text-gray-400 flex-shrink-0">{n.time}</span>
                </div>
                <p className="text-sm text-gray-600 leading-relaxed">{n.message}</p>
                {n.link && (
                  <Link href={n.link} onClick={() => { if (n.realId && !n.read) markNotificationRead(n.realId).catch(() => {}); }}
                    className={`inline-block mt-3 text-xs font-semibold px-3 py-1.5 rounded-lg transition ${BTN[n.type]}`}>
                    Take action →
                  </Link>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}