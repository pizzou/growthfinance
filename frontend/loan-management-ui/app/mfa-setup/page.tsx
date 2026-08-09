'use client';
import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import axios from 'axios';
import Link from 'next/link';
import { Button } from '@/components/ui/Button';
import { FormGroup, Input, Alert } from '@/components/ui/Form';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

/**
 * Mandatory MFA enrollment for roles that require it (ADMIN, MANAGER — see
 * AuthController.MFA_MANDATORY_ROLES on the backend). Reached only right after
 * a login attempt returns `mfaSetupRequired: true`. Uses the short-lived
 * setup-only token from that response — NOT the normal session token/localStorage
 * — since JwtAuthFilter restricts setup tokens to only the /api/mfa/** routes.
 */
export default function MfaSetupPage() {
  const router = useRouter();
  const [setupToken, setSetupToken] = useState<string | null>(null);
  const [email, setEmail]           = useState('');
  const [qrCodeDataUri, setQr]      = useState('');
  const [secret, setSecret]         = useState('');
  const [code, setCode]             = useState('');
  const [loading, setLoading]       = useState(true);
  const [confirming, setConfirming] = useState(false);
  const [error, setError]           = useState('');
  const [done, setDone]             = useState(false);

  useEffect(() => {
    const tok = sessionStorage.getItem('mfaSetupToken');
    const em  = sessionStorage.getItem('mfaSetupEmail');
    if (!tok) { router.replace('/login'); return; }
    setSetupToken(tok);
    setEmail(em ?? '');

    axios.post(`${API_BASE}/mfa/setup`, {}, { headers: { Authorization: `Bearer ${tok}` } })
      .then(r => {
        const data = r.data?.data ?? r.data;
        setQr(data.qrCodeDataUri);
        setSecret(data.secret);
      })
      .catch(err => setError(err.response?.data?.error || err.response?.data?.message || 'Could not start MFA setup. Please log in again.'))
      .finally(() => setLoading(false));
  }, [router]);

  const handleConfirm = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!setupToken) return;
    setError(''); setConfirming(true);
    try {
      await axios.post(`${API_BASE}/mfa/confirm`, { code },
        { headers: { Authorization: `Bearer ${setupToken}` } });
      sessionStorage.removeItem('mfaSetupToken');
      sessionStorage.removeItem('mfaSetupEmail');
      setDone(true);
    } catch (err: any) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Invalid verification code. Please try again.');
    } finally {
      setConfirming(false);
    }
  };

  if (done) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 p-4">
        <div className="w-full max-w-sm bg-white rounded-xl border border-gray-200 shadow-sm p-8 text-center">
          <div className="w-12 h-12 rounded-full bg-green-50 text-green-600 flex items-center justify-center mx-auto mb-4 text-2xl">✓</div>
          <h1 className="text-xl font-bold text-gray-900 mb-2">Two-factor authentication enabled</h1>
          <p className="text-sm text-gray-500 mb-6">
            Sign in again with your password and the 6-digit code from your authenticator app.
          </p>
          <Button className="w-full justify-center" onClick={() => router.replace('/login')}>
            Back to Sign In
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 p-4">
      <div className="w-full max-w-sm bg-white rounded-xl border border-gray-200 shadow-sm p-8">
        <div className="mb-6">
          <div className="w-9 h-9 rounded-md flex items-center justify-center font-bold text-white text-sm mb-3" style={{ backgroundColor: '#0D6B3E' }}>G</div>
          <h1 className="text-xl font-bold text-gray-900">Set Up Two-Factor Authentication</h1>
          <p className="text-sm text-gray-500 mt-1">
            {email && <>Required for <span className="font-semibold">{email}</span> — </>}
            your role requires MFA before you can access the dashboard.
          </p>
        </div>

        {error && <Alert type="error">{error}</Alert>}

        {loading ? (
          <div className="py-10 flex justify-center">
            <div className="w-6 h-6 border-2 border-gray-300 border-t-[#0D6B3E] rounded-full animate-spin" />
          </div>
        ) : qrCodeDataUri ? (
          <>
            <ol className="text-sm text-gray-600 space-y-3 mb-6 list-decimal list-inside">
              <li>Open an authenticator app (Google Authenticator, Authy, 1Password, etc.)</li>
              <li>Scan this QR code, or enter the setup key manually</li>
              <li>Enter the 6-digit code it generates below</li>
            </ol>

            <div className="flex justify-center mb-4">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={qrCodeDataUri} alt="MFA QR code" className="w-40 h-40 border border-gray-200 rounded-lg p-2" />
            </div>

            {secret && (
              <div className="mb-6 text-center">
                <div className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-1">Manual setup key</div>
                <code className="text-xs bg-gray-50 border border-gray-200 rounded px-2 py-1.5 font-mono tracking-wider text-gray-700 break-all">{secret}</code>
              </div>
            )}

            <form onSubmit={handleConfirm}>
              <FormGroup label="Verification Code" required>
                <Input
                  value={code}
                  onChange={e => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  placeholder="000000"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={6}
                  className="text-center text-lg tracking-[0.4em] font-mono"
                  autoFocus
                />
              </FormGroup>
              <Button type="submit" className="w-full justify-center py-3" loading={confirming}
                disabled={code.length !== 6}
                style={{ backgroundColor: '#0D6B3E' } as React.CSSProperties}>
                Verify &amp; Enable
              </Button>
            </form>
          </>
        ) : (
          <p className="text-sm text-gray-500">
            Something went wrong loading your setup code.{' '}
            <Link href="/login" className="font-semibold" style={{ color: '#0D6B3E' }}>Return to sign in</Link> and try again.
          </p>
        )}
      </div>
    </div>
  );
}
