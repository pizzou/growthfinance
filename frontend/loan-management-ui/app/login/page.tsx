'use client';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { authApi } from '@/services/api';
import { AuthContext, useAuthState } from '@/hooks/useAuth';
import { Button } from '@/components/ui/Button';
import { FormGroup, Input, Alert } from '@/components/ui/Form';
import Link from 'next/link';

const DEMOS = [
  { label: 'Admin',   email: 'admin@growthfinance.rw',   password: 'Admin@1234' },
  { label: 'Officer', email: 'officer@growthfinance.rw',  password: 'Officer@1234' },
];

function LoginInner() {
  const [email, setEmail]       = useState('');
  const [password, setPassword] = useState('');
  const [mfaCode, setMfaCode]   = useState('');
  const [mfaRequired, setMfaRequired] = useState(false);
  const [otp, setOtp]           = useState('');
  const [otpRequired, setOtpRequired] = useState(false);
  const [otpMessage, setOtpMessage]   = useState('');
  const [loading, setLoading]   = useState(false);
  const [error, setError]       = useState('');
  const { login }               = useAuthState();
  const router                  = useRouter();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault(); setError(''); setLoading(true);
    try {
      const res: any = await authApi.login(
        email, password,
        mfaRequired ? mfaCode : undefined,
        otpRequired ? otp : undefined,
      );

      if (res?.mfaSetupRequired) {
        // This role requires MFA but hasn't enrolled yet. The backend gave us a
        // short-lived setup-only token — it can't be used as a real session, only
        // to complete enrollment. Hand off to the setup flow.
        sessionStorage.setItem('mfaSetupToken', res.setupToken);
        sessionStorage.setItem('mfaSetupEmail', res.email ?? email);
        router.push('/mfa-setup');
        return;
      }
      if (res?.mfaRequired) { setMfaRequired(true); setLoading(false); return; }
      if (res?.otpRequired) {
        setOtpRequired(true);
        setOtpMessage(res.message || 'We sent a 6-digit verification code to your email.');
        setLoading(false);
        return;
      }
      if (!res?.token) {
        setError('Unexpected response from server. Please try again.');
        setLoading(false);
        return;
      }
      login(res, res.token);
      router.replace('/dashboard');
    } catch (err: any) { setError(err.message || 'Invalid credentials'); }
    setLoading(false);
  };

  return (
    <div className="min-h-screen flex">
      {/* Left panel */}
      <div className="hidden lg:flex w-1/2 flex-col justify-between px-16 py-12 relative overflow-hidden"
        style={{ background: 'linear-gradient(160deg, #0B1220 0%, #0D6B3E 130%)' }}>

        <Link href="/" className="flex items-center gap-3 relative z-10">
          <div className="w-10 h-10 rounded-md flex items-center justify-center font-bold text-white text-lg" style={{ backgroundColor: '#0D6B3E' }}>G</div>
          <div>
            <div className="text-white font-bold">Growth Finance Services Ltd</div>
            <div className="text-white/50 text-xs">Staff Portal</div>
          </div>
        </Link>

        <div className="relative z-10">
          <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded border border-white/20 text-xs font-semibold mb-8 text-white/80 uppercase tracking-wide">
            Secure Staff Portal
          </div>
          <h1 className="text-4xl font-bold text-white leading-tight mb-6 tracking-tight">
            Internal Operations<br/>
            <span style={{ color: '#F5A623' }}>&amp; Loan Management</span>
          </h1>
          <p className="text-white/70 leading-relaxed mb-10">
            Manage loans, borrowers, payments, KYC/AML compliance, FX rates, and reporting for Growth Finance Services Ltd.
          </p>
          <div className="grid grid-cols-2 gap-3">
            {['KYC / AML','Multi-factor auth','FX rates','Webhooks','Bulk disbursement','Audit reports'].map(f => (
              <div key={f} className="flex items-center gap-2 bg-white/5 border border-white/10 rounded-md px-4 py-2.5">
                <span className="text-white text-sm font-medium">{f}</span>
              </div>
            ))}
          </div>
        </div>

        <div className="text-white/35 text-xs relative z-10">
          © {new Date().getFullYear()} Growth Finance Services Ltd. All rights reserved.
        </div>
      </div>

      {/* Right panel */}
      <div className="flex-1 flex items-center justify-center px-8 bg-gray-50">
        <div className="w-full max-w-sm">
          <div className="lg:hidden mb-8 flex items-center gap-2">
            <div className="w-8 h-8 rounded-md flex items-center justify-center font-bold text-white text-sm" style={{ backgroundColor: '#0D6B3E' }}>G</div>
            <span className="text-sm font-bold text-gray-900">Growth Finance Services Ltd</span>
          </div>

          <h2 className="text-2xl font-bold text-gray-900 mb-1">Staff Sign In</h2>
          <p className="text-sm text-gray-500 mb-6">Access your organization dashboard</p>

          {error && <Alert type="error">{error}</Alert>}

          <form onSubmit={handleSubmit} className="space-y-4">
            {!mfaRequired && !otpRequired ? (
              <>
                <FormGroup label="Email Address" required>
                  <Input type="email" required value={email} onChange={e => setEmail(e.target.value)}
                    placeholder="you@organization.com" autoComplete="email" />
                </FormGroup>
                <FormGroup label="Password" required>
                  <Input type="password" required value={password} onChange={e => setPassword(e.target.value)}
                    placeholder="••••••••" autoComplete="current-password" />
                </FormGroup>
              </>
            ) : mfaRequired ? (
              <div className="text-center py-4">
                <div className="font-bold text-gray-900 mb-1">Two-Factor Authentication</div>
                <div className="text-gray-500 text-sm mb-4">Enter the 6-digit code from your authenticator app</div>
                <FormGroup label="Verification Code">
                  <Input type="text" inputMode="numeric" maxLength={6}
                    placeholder="000000" value={mfaCode} onChange={e => setMfaCode(e.target.value.replace(/\D/g,''))}
                    className="text-center text-2xl tracking-[0.5em] font-mono" autoFocus />
                </FormGroup>
              </div>
            ) : (
              <div className="text-center py-4">
                <div className="font-bold text-gray-900 mb-1">Check Your Email</div>
                <div className="text-gray-500 text-sm mb-4">{otpMessage}</div>
                <FormGroup label="Verification Code">
                  <Input type="text" inputMode="numeric" maxLength={6}
                    placeholder="000000" value={otp} onChange={e => setOtp(e.target.value.replace(/\D/g,''))}
                    className="text-center text-2xl tracking-[0.5em] font-mono" autoFocus />
                </FormGroup>
              </div>
            )}
            <Button type="submit" className="w-full justify-center py-3 text-base" loading={loading}
              style={{ backgroundColor: '#0D6B3E' } as React.CSSProperties}>
              {mfaRequired || otpRequired ? 'Verify & Sign In' : 'Sign In →'}
            </Button>
          </form>

          {(mfaRequired || otpRequired) && (
            <button
              onClick={() => { setMfaRequired(false); setOtpRequired(false); setMfaCode(''); setOtp(''); setError(''); }}
              className="mt-3 text-xs font-semibold text-gray-500 hover:text-gray-700 w-full text-center">
              ← Back to sign in
            </button>
          )}

          {/* Demo accounts */}
          <div className="mt-6 pt-5 border-t border-gray-200">
            <p className="text-xs text-gray-400 mb-3 font-semibold uppercase tracking-wider">Demo Accounts</p>
            <div className="grid grid-cols-2 gap-2">
              {DEMOS.map(d => (
                <button key={d.email} onClick={() => { setEmail(d.email); setPassword(d.password); setMfaRequired(false); setOtpRequired(false); }}
                  className="text-xs px-3 py-2 border border-gray-200 rounded-lg hover:border-teal-400 hover:text-teal-700 transition-colors text-gray-600 text-left">
                  {d.label}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function LoginPage() {
  const auth = useAuthState();
  return (
    <AuthContext.Provider value={auth}>
      <LoginInner />
    </AuthContext.Provider>
  );
}
