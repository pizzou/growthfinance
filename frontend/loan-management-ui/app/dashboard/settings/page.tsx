'use client';
import { useState } from 'react';
import Link from 'next/link';
import { getCurrentUser, logout } from '../../../services/authService';
import { put } from '../../../services/api';
import { toast } from '../../../hooks/useToast';

function inputCls(err?: string) {
  return 'w-full border rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 ' +
    (err ? 'border-red-400 focus:ring-red-400 bg-red-50' : 'border-gray-300 focus:ring-blue-500');
}

export default function SettingsPage() {
  const user = getCurrentUser();

  const [name,       setName]       = useState(user?.name ?? '');
  const [savingName, setSavingName] = useState(false);

  const [email,        setEmail]        = useState(user?.email ?? '');
  const [emailPw,      setEmailPw]      = useState('');
  const [savingEmail,  setSavingEmail]  = useState(false);
  const [emailErrors,  setEmailErrors]  = useState<Record<string, string>>({});

  const [currentPw, setCurrentPw] = useState('');
  const [newPw,     setNewPw]     = useState('');
  const [confirmPw, setConfirmPw] = useState('');
  const [savingPw,  setSavingPw]  = useState(false);
  const [pwErrors,  setPwErrors]  = useState<Record<string, string>>({});

  const getMsg = (err: unknown) => err instanceof Error ? err.message : 'Something went wrong';

  const handleProfileSave = async () => {
    if (!name.trim()) { toast('error', 'Name cannot be empty'); return; }
    setSavingName(true);
    try {
      await put('/users/' + user?.userId, { name: name.trim() });
      const stored = JSON.parse(localStorage.getItem('user') ?? '{}');
      localStorage.setItem('user', JSON.stringify({ ...stored, name: name.trim() }));
      toast('success', 'Profile updated');
    } catch (err: unknown) {
      toast('error', getMsg(err));
    } finally { setSavingName(false); }
  };

  const handleEmailChange = async (e: React.FormEvent) => {
    e.preventDefault();
    const e2: Record<string, string> = {};
    if (!email.trim()) e2.email = 'Email is required';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) e2.email = 'Enter a valid email address';
    if (email.trim().toLowerCase() === user?.email?.toLowerCase()) e2.email = 'This is already your current email';
    if (!emailPw) e2.emailPw = 'Enter your current password to confirm';
    setEmailErrors(e2);
    if (Object.keys(e2).length > 0) return;

    setSavingEmail(true);
    try {
      await put('/users/' + user?.userId, { email: email.trim(), currentPassword: emailPw });
      const stored = JSON.parse(localStorage.getItem('user') ?? '{}');
      localStorage.setItem('user', JSON.stringify({ ...stored, email: email.trim() }));
      setEmailPw('');
      toast('success', 'Email updated. Please log in again with your new email.');
      setTimeout(logout, 2000);
    } catch (err: unknown) {
      toast('error', getMsg(err));
    } finally { setSavingEmail(false); }
  };

  const handlePasswordChange = async (e: React.FormEvent) => {
    e.preventDefault();
    const e2: Record<string, string> = {};
    if (!currentPw) e2.currentPw = 'Current password is required';
    if (!newPw) e2.newPw = 'New password is required';
    else if (newPw.length < 8) e2.newPw = 'Must be at least 8 characters';
    if (newPw !== confirmPw) e2.confirmPw = 'Passwords do not match';
    if (currentPw === newPw && currentPw) e2.newPw = 'New password must differ from current';
    setPwErrors(e2);
    if (Object.keys(e2).length > 0) return;

    setSavingPw(true);
    try {
      await put('/users/' + user?.userId, { password: newPw, currentPassword: currentPw });
      setCurrentPw(''); setNewPw(''); setConfirmPw('');
      toast('success', 'Password changed. Please log in again.');
      setTimeout(logout, 2000);
    } catch (err: unknown) {
      toast('error', getMsg(err));
    } finally { setSavingPw(false); }
  };

  return (
    <div className="space-y-6 max-w-xl">

      <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-5">
        <h2 className="font-semibold text-gray-800 border-b border-gray-100 pb-3">Profile</h2>
        <div className="flex items-center gap-4">
          <div className="w-14 h-14 rounded-full bg-blue-600 flex items-center justify-center text-white text-xl font-bold flex-shrink-0">
            {user?.name?.[0]?.toUpperCase() ?? '?'}
          </div>
          <div>
            <p className="font-semibold text-gray-800">{user?.name}</p>
            <p className="text-sm text-gray-500">{user?.email}</p>
            <span className="bg-blue-100 text-blue-700 px-2.5 py-0.5 rounded-full text-xs font-medium mt-1 inline-block">
              {user?.role}
            </span>
          </div>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Display Name</label>
          <input
            value={name}
            onChange={e => setName(e.target.value)}
            autoComplete="name"
            className={inputCls()}
          />
        </div>
        <button
          onClick={handleProfileSave}
          disabled={savingName}
          className="bg-blue-600 hover:bg-blue-700 text-white px-5 py-2 rounded-lg text-sm font-medium disabled:opacity-60 transition"
        >
          {savingName ? 'Saving...' : 'Save Profile'}
        </button>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 p-6">
        <h2 className="font-semibold text-gray-800 border-b border-gray-100 pb-3 mb-5">
          Change Email
        </h2>
        <form onSubmit={handleEmailChange} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Email Address</label>
            <input type="email" value={email} onChange={e => setEmail(e.target.value)}
              autoComplete="email" placeholder="you@example.com"
              className={inputCls(emailErrors.email)} />
            {emailErrors.email && <p className="text-xs text-red-600 mt-1">{emailErrors.email}</p>}
            <p className="text-xs text-gray-400 mt-1">You'll use this new address to log in going forward.</p>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Current Password</label>
            <input type="password" value={emailPw} onChange={e => setEmailPw(e.target.value)}
              autoComplete="current-password" placeholder="Confirm it's you"
              className={inputCls(emailErrors.emailPw)} />
            {emailErrors.emailPw && <p className="text-xs text-red-600 mt-1">{emailErrors.emailPw}</p>}
          </div>
          <button type="submit" disabled={savingEmail}
            className="bg-blue-600 hover:bg-blue-700 text-white px-5 py-2 rounded-lg text-sm font-medium disabled:opacity-60 transition">
            {savingEmail ? 'Saving...' : 'Change Email'}
          </button>
        </form>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-3">
        <h2 className="font-semibold text-gray-800 border-b border-gray-100 pb-3">Organization</h2>
        <div className="grid grid-cols-2 gap-4 text-sm">
          {[
            ['Organization', user?.organizationName ?? '—'],
            ['Org ID',       '#' + (user?.organizationId ?? '')],
            ['Role',         user?.role ?? '—'],
            ['User ID',      '#' + (user?.userId ?? '')],
          ].map(([l, v]) => (
            <div key={l}>
              <p className="text-gray-400 text-xs mb-0.5">{l}</p>
              <p className="font-medium text-gray-800">{v}</p>
            </div>
          ))}
        </div>
        {user?.role === 'ADMIN' && (
          <Link href="/dashboard/settings/website"
            className="inline-flex items-center gap-1.5 text-teal-600 hover:text-teal-700 text-sm font-semibold pt-2">
            🌐 Manage Website Content →
          </Link>
        )}
      </div>

      <div className="bg-white rounded-xl border border-gray-200 p-6">
        <h2 className="font-semibold text-gray-800 border-b border-gray-100 pb-3 mb-5">
          Change Password
        </h2>
        <form onSubmit={handlePasswordChange} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Current Password</label>
            <input type="password" value={currentPw} onChange={e => setCurrentPw(e.target.value)}
              autoComplete="current-password" placeholder="Enter current password"
              className={inputCls(pwErrors.currentPw)} />
            {pwErrors.currentPw && <p className="text-xs text-red-600 mt-1">{pwErrors.currentPw}</p>}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">New Password</label>
            <input type="password" value={newPw} onChange={e => setNewPw(e.target.value)}
              autoComplete="new-password" placeholder="Min 8 characters"
              className={inputCls(pwErrors.newPw)} />
            {pwErrors.newPw && <p className="text-xs text-red-600 mt-1">{pwErrors.newPw}</p>}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Confirm New Password</label>
            <input type="password" value={confirmPw} onChange={e => setConfirmPw(e.target.value)}
              autoComplete="new-password" placeholder="Repeat new password"
              className={inputCls(pwErrors.confirmPw)} />
            {pwErrors.confirmPw && <p className="text-xs text-red-600 mt-1">{pwErrors.confirmPw}</p>}
          </div>
          <div className="pt-1">
            <button type="submit" disabled={savingPw}
              className="bg-gray-800 hover:bg-gray-900 text-white px-5 py-2 rounded-lg text-sm font-medium disabled:opacity-60 transition">
              {savingPw ? 'Changing...' : 'Change Password'}
            </button>
          </div>
        </form>
      </div>

      <div className="bg-white rounded-xl border border-red-200 p-6 space-y-3">
        <h2 className="font-semibold text-red-700 text-sm">Sign Out</h2>
        <p className="text-sm text-gray-500">Sign out of your account on this device.</p>
        <button onClick={logout}
          className="bg-red-50 hover:bg-red-100 text-red-600 border border-red-200 px-5 py-2 rounded-lg text-sm font-medium transition">
          Sign Out
        </button>
      </div>
    </div>
  );
}