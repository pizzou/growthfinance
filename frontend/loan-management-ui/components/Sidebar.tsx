'use client';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';
import { useAuth } from '@/hooks/useAuth';
import { getUnreadCount } from '@/services/notificationsService';
import { contactMessageApi } from '@/services/api';

const NAV_STAFF = [
  { section: 'Overview',    items: [
    { href: '/dashboard',            icon: '📊', label: 'Dashboard' },
    { href: '/dashboard/loans',      icon: '💼', label: 'Loan Portfolio' },
    { href: '/dashboard/borrowers',  icon: '👥', label: 'Borrowers' },
    { href: '/dashboard/payments',   icon: '💳', label: 'Payments' },
    { href: '/dashboard/collections',icon: '📉', label: 'Collections' },
    { href: '/dashboard/notifications', icon: '🔔', label: 'Notifications' },
    { href: '/dashboard/messages',   icon: '📬', label: 'Messages' },
  ]},
  { section: 'Tools', items: [
    { href: '/dashboard/reports',    icon: '📈', label: 'Reports' },
    { href: '/dashboard/documents',  icon: '🗂️', label: 'Internal Documents' },
    { href: '/dashboard/currencies', icon: '💱', label: 'FX Rates' },
    { href: '/dashboard/webhooks',   icon: '🔗', label: 'Webhooks' },
  ]},
  { section: 'Admin', items: [
    { href: '/dashboard/products',   icon: '💰', label: 'Loan Products', adminOnly: true },
    { href: '/dashboard/import',     icon: '📥', label: 'Import Legacy Loans' },
    { href: '/dashboard/accounting', icon: '📒', label: 'Accounting' },
    { href: '/dashboard/reports/regulatory', icon: '🏦', label: 'Regulatory Reports' },
    { href: '/dashboard/users',      icon: '🧑‍💼', label: 'Users & Roles', adminOnly: true },
    { href: '/dashboard/audit',      icon: '🛡️', label: 'Audit Log', adminOnly: true },
    { href: '/dashboard/settings',   icon: '⚙️', label: 'Settings' },
  ]},
];

export default function Sidebar() {
  const pathname = usePathname();
  const { user, logout, currency } = useAuth();
  const org = user ? { name: user.organizationName, currency } : null;
  const isAdmin = user?.role === 'ADMIN';
  const canSeeAccounting = ['ADMIN', 'MANAGER', 'ACCOUNTANT'].includes(user?.role || '');
  const [unread, setUnread] = useState(0);
  const [unreadMessages, setUnreadMessages] = useState(0);

  useEffect(() => {
    if (!user) return;
    const load = () => getUnreadCount().then((r) => setUnread(r.count)).catch(() => {});
    load();
    const interval = setInterval(load, 30000);
    return () => clearInterval(interval);
  }, [user]);

  useEffect(() => {
    if (!user) return;
    const load = () => contactMessageApi.unreadCount().then((r: any) => setUnreadMessages(r.count)).catch(() => {});
    load();
    const interval = setInterval(load, 30000);
    return () => clearInterval(interval);
  }, [user]);

  return (
    <aside className="w-64 bg-[#0D1B2A] flex flex-col min-h-screen fixed left-0 top-0 bottom-0 z-40">
      {/* Brand */}
      <div className="flex items-center gap-3 px-5 py-4 border-b border-white/10">
        <div className="w-9 h-9 rounded-lg flex items-center justify-center font-bold text-white text-base" style={{ backgroundColor: '#0D6B3E' }}>G</div>
        <div className="overflow-hidden">
          <div className="text-white font-bold text-base leading-tight truncate">Growth Finance</div>
          <div className="text-[#4ade80] text-[10px] font-bold uppercase tracking-widest">Staff Portal</div>
        </div>
      </div>

      {/* Org */}
      {org && (
        <div className="mx-3 mt-3 px-3 py-2.5 bg-white/5 rounded-lg">
          <div className="text-white text-xs font-semibold truncate">{org.name}</div>
          <div className="text-gray-400 text-[10px] mt-0.5">{org.currency} · {user?.role}</div>
        </div>
      )}

      {/* Nav */}
      <nav className="flex-1 px-3 py-3 overflow-y-auto space-y-4 mt-1">
        {NAV_STAFF.map(section => (
          <div key={section.section}>
            <div className="text-[10px] font-bold text-gray-500 uppercase tracking-widest px-2 mb-1">{section.section}</div>
            {section.items.filter(item => (!(item as any).adminOnly || isAdmin) && (!(item as any).accountingOnly || canSeeAccounting)).map(item => {
              const active = pathname === item.href || (item.href !== '/dashboard' && pathname.startsWith(item.href));
              return (
                <Link key={item.href} href={item.href}
                  className={`flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-medium transition-all mb-0.5
                    ${active ? 'bg-teal-500/15 text-teal-400' : 'text-gray-400 hover:text-white hover:bg-white/8'}`}>
                  <span className="text-base w-5 text-center">{item.icon}</span>
                  <span className="flex-1">{item.label}</span>
                  {item.href === '/dashboard/notifications' && unread > 0 && (
                    <span className="bg-red-500 text-white text-[10px] font-bold rounded-full min-w-[18px] h-[18px] flex items-center justify-center px-1">
                      {unread > 9 ? '9+' : unread}
                    </span>
                  )}
                  {item.href === '/dashboard/messages' && unreadMessages > 0 && (
                    <span className="bg-teal-500 text-white text-[10px] font-bold rounded-full min-w-[18px] h-[18px] flex items-center justify-center px-1">
                      {unreadMessages > 9 ? '9+' : unreadMessages}
                    </span>
                  )}
                </Link>
              );
            })}
          </div>
        ))}
      </nav>

      {/* User footer */}
      <div className="px-3 py-3 border-t border-white/10">
        <div className="flex items-center gap-2.5 px-3 py-2 rounded-lg cursor-pointer hover:bg-white/5 transition-colors"
          onClick={() => { logout(); window.location.href = '/login'; }}>
          <div className="w-8 h-8 bg-teal-500 rounded-full flex items-center justify-center text-[#0D1B2A] font-bold text-sm flex-shrink-0">
            {user?.name?.[0] ?? 'U'}
          </div>
          <div className="overflow-hidden flex-1">
            <div className="text-white text-xs font-semibold truncate">{user?.name}</div>
            <div className="text-gray-500 text-[10px]">Sign out</div>
          </div>
          <span className="text-gray-500 text-xs">→</span>
        </div>
      </div>
    </aside>
  );
}