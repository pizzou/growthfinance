'use client';
import { useState } from 'react';
import { put } from '@/services/api';
import { useAuth } from '@/hooks/useAuth';

/**
 * Rendered instead of the dashboard when the signed-in user's account still has
 * mustChangePassword=true — either they just logged in for the first time with a
 * system-generated temporary password, or an admin reset their password for them.
 * Reuses the existing self-service password-change endpoint (PUT /api/users/{id}),
 * which already clears the flag server-side on success — this component just also
 * updates the locally cached user object so the guard in DashboardLayout re-renders
 * into the real dashboard without needing a fresh login.
 */
export default function ForcedPasswordChange() {
  const { user, token, login } = useAuth();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const policyIssues = () => {
    if (newPassword.length < 10) return 'Must be at least 10 characters';
    if (!/[A-Z]/.test(newPassword)) return 'Must include an uppercase letter';
    if (!/[a-z]/.test(newPassword)) return 'Must include a lowercase letter';
    if (!/[0-9]/.test(newPassword)) return 'Must include a digit';
    if (!/[^A-Za-z0-9]/.test(newPassword)) return 'Must include a special character (e.g. !@#$%)';
    return '';
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!currentPassword) { setError('Enter the temporary password you were given'); return; }
    const issue = policyIssues();
    if (issue) { setError(issue); return; }
    if (newPassword !== confirmPassword) { setError('Passwords do not match'); return; }
    if (!user) return;

    setSaving(true);
    try {
      await put(`/users/${user.userId}`, { currentPassword, password: newPassword });
      // Server already cleared mustChangePassword — mirror that locally so the
      // dashboard guard lets this render through immediately, no re-login needed.
      login({ ...user, mustChangePassword: false }, token!);
    } catch (err: any) {
      setError(err.response?.data?.error || err.message || 'Could not update your password.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-xl border border-gray-100 max-w-md w-full p-8">
        <h1 className="text-lg font-bold text-gray-900 mb-1">Set a New Password</h1>
        <p className="text-sm text-gray-500 mb-6">
          For security, you need to set your own password before continuing — the temporary one you were emailed won&apos;t work again after this.
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-gray-500 mb-1">Temporary Password</label>
            <input type="password" value={currentPassword} onChange={e => setCurrentPassword(e.target.value)}
              className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-teal-500"
              placeholder="The password from your email" required />
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-500 mb-1">New Password</label>
            <input type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)}
              className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-teal-500"
              placeholder="10+ chars, upper, lower, digit, symbol" required />
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-500 mb-1">Confirm New Password</label>
            <input type="password" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)}
              className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-teal-500"
              placeholder="Repeat new password" required />
          </div>

          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-xs rounded-lg px-3 py-2.5 font-semibold">{error}</div>
          )}

          <button type="submit" disabled={saving}
            className="w-full bg-teal-600 hover:bg-teal-700 disabled:opacity-60 text-white py-2.5 rounded-lg text-sm font-semibold transition">
            {saving ? 'Updating…' : 'Set Password & Continue'}
          </button>
        </form>
      </div>
    </div>
  );
}