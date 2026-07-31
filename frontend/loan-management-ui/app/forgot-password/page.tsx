'use client';
import { useState } from 'react';
import Link from 'next/link';
import { post } from '../../services/api';

export default function ForgotPasswordPage() {
  const [email,   setEmail]   = useState('');
  const [loading, setLoading] = useState(false);
  const [sent,    setSent]    = useState(false);
  const [error,   setError]   = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true); setError('');
    try {
      await post('/auth/forgot-password', { email });
      setSent(true);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
    } finally { setLoading(false); }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-blue-950 to-slate-900 flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-blue-600 mb-4">
            <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
            </svg>
          </div>
          <h1 className="text-2xl font-bold text-white">Reset password</h1>
          <p className="text-slate-400 text-sm mt-1">LoanSaaS</p>
        </div>
        <div className="bg-white rounded-2xl shadow-2xl p-8">
          {sent ? (
            <div className="text-center">
              <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
                <svg className="w-8 h-8 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
              </div>
              <h2 className="text-lg font-semibold text-gray-800 mb-2">Check your email</h2>
              <p className="text-gray-500 text-sm mb-6">
                If an account exists for <strong>{email}</strong>, a reset link has been sent.
              </p>
              <Link href="/login" className="text-blue-600 text-sm hover:underline">Back to sign in</Link>
            </div>
          ) : (
            <>
              <h2 className="text-xl font-semibold text-gray-800 mb-2">Forgot your password?</h2>
              <p className="text-gray-500 text-sm mb-6">
                Enter your email and we will send you a reset link.
              </p>
              {error && (
                <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm">{error}</div>
              )}
              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Email address</label>
                  <input type="email" required value={email} onChange={e => setEmail(e.target.value)}
                    placeholder="you@company.com"
                    className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                </div>
                <button type="submit" disabled={loading}
                  className="w-full py-2.5 bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white font-medium rounded-lg text-sm transition">
                  {loading ? 'Sending...' : 'Send reset link'}
                </button>
              </form>
              <p className="text-center text-gray-500 text-sm mt-4">
                Remembered it?{' '}
                <Link href="/login" className="text-blue-600 hover:underline">Sign in</Link>
              </p>
            </>
          )}
        </div>
      </div>
    </div>
  );
}