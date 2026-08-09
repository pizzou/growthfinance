'use client';
import { useEffect, useState, useCallback } from 'react';
import { useParams } from 'next/navigation';
import axios from 'axios';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

interface SignView {
  status: 'PENDING' | 'OTP_SENT' | 'SIGNED' | 'DECLINED' | 'EXPIRED';
  documentType: string;
  documentText: string;
  consentText: string;
  borrowerName?: string;
  expiresAt?: string;
  signedAt?: string;
}

export default function SigningPage() {
  const params = useParams();
  const token = Array.isArray(params?.token) ? params.token[0] : (params?.token as string);

  const [view, setView] = useState<SignView | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [otp, setOtp] = useState('');
  const [fullName, setFullName] = useState('');
  const [busy, setBusy] = useState(false);
  const [resending, setResending] = useState(false);
  const [resendSuccess, setResendSuccess] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [declining, setDeclining] = useState(false);
  const [success, setSuccess] = useState(false);

  const load = useCallback(() => {
    setLoading(true); 
    setError('');
    axios.get(`${API_BASE}/public/esignature/${token}`)
      .then((r) => setView(r.data?.data ?? r.data))
      .catch((e) => setError(e.response?.data?.error || e.response?.data?.message || 'This signing link could not be found or has expired.'))
      .finally(() => setLoading(false));
  }, [token]);

  useEffect(() => { 
    if (token) load(); 
  }, [token, load]);

  // Handle countdown loop to prevent button spamming
  useEffect(() => {
    if (countdown <= 0) return;
    const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
    return () => clearTimeout(timer);
  }, [countdown]);

  const handleResend = async () => {
    setResending(true); 
    setError('');
    setResendSuccess(false);
    
    try { 
      await axios.post(`${API_BASE}/public/esignature/${token}/resend-otp`); 
      setResendSuccess(true);
      setCountdown(60); // Locks resend button for exactly 60 seconds
    } catch (e: any) { 
      setError(e.response?.data?.error || e.response?.data?.message || 'Could not resend code. Please try again.'); 
    } finally {
      setResending(false);
    }
  };

  const handleSign = async () => {
    setBusy(true); 
    setError('');
    try {
      await axios.post(`${API_BASE}/public/esignature/${token}/sign`, { otp, fullName });
      setSuccess(true);
    } catch (e: any) { 
      setError(e.response?.data?.error || e.response?.data?.message || 'Could not verify — please check the code and try again.'); 
    } finally {
      setBusy(false);
    }
  };

  const handleDecline = async () => {
    if (!confirm('Are you sure you want to decline signing this agreement?')) return;
    setDeclining(true); 
    setError('');
    try { 
      await axios.post(`${API_BASE}/public/esignature/${token}/decline`, { reason: 'Declined by borrower' }); 
      load(); 
    } catch (e: any) { 
      setError(e.response?.data?.error || e.response?.data?.message || 'Could not record decline'); 
    } finally {
      setDeclining(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="w-8 h-8 border-2 border-emerald-600 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (error && !view) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 p-6">
        <div className="bg-white rounded-2xl border border-gray-200 p-8 max-w-md text-center shadow-sm">
          <div className="text-3xl mb-3">⚠️</div>
          <h1 className="font-bold text-gray-900 mb-2">Link unavailable</h1>
          <p className="text-sm text-gray-500">{error}</p>
        </div>
      </div>
    );
  }

  const alreadyDone = view?.status === 'SIGNED' || success;
  const declined = view?.status === 'DECLINED';
  const expired = view?.status === 'EXPIRED';

  return (
    <div className="min-h-screen bg-gray-50 py-10 px-4">
      <div className="max-w-2xl mx-auto">
        <div className="text-center mb-6">
          <div className="inline-flex w-12 h-12 bg-[#0D6B3E] rounded-xl items-center justify-center text-white font-black text-lg mb-3 shadow-md">✍️</div>
          <h1 className="text-xl font-extrabold text-gray-900">Sign Your Loan Agreement</h1>
          <p className="text-sm text-gray-500 mt-1">Secure electronic signature — {view?.documentType?.replace(/_/g, ' ')}</p>
        </div>

        {alreadyDone ? (
          <div className="bg-white rounded-2xl border border-gray-200 p-8 text-center shadow-sm animate-fadeIn">
            <div className="text-4xl mb-3">✅</div>
            <h2 className="font-bold text-gray-900 mb-1">Document signed</h2>
            <p className="text-sm text-gray-500">Thank you{view?.borrowerName ? `, ${view.borrowerName}` : ''}. Your signed agreement has been recorded and your loan officer has been notified.</p>
          </div>
        ) : declined ? (
          <div className="bg-white rounded-2xl border border-gray-200 p-8 text-center shadow-sm">
            <div className="text-4xl mb-3">🚫</div>
            <h2 className="font-bold text-gray-900 mb-1">Signing declined</h2>
            <p className="text-sm text-gray-500">You've declined to sign this agreement. Your loan officer will be in touch.</p>
          </div>
        ) : expired ? (
          <div className="bg-white rounded-2xl border border-gray-200 p-8 text-center shadow-sm">
            <div className="text-4xl mb-3">⏰</div>
            <h2 className="font-bold text-gray-900 mb-1">Link expired</h2>
            <p className="text-sm text-gray-500">This signing link has expired. Please ask your loan officer to send a new one.</p>
          </div>
        ) : (
          <>
            <div className="bg-white rounded-2xl border border-gray-200 p-6 mb-4 shadow-sm">
              <h2 className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-2">Agreement</h2>
              <pre className="whitespace-pre-wrap text-sm text-gray-700 font-sans leading-relaxed max-h-80 overflow-y-auto border border-gray-100 rounded-lg p-4 bg-gray-50">
                {view?.documentText}
              </pre>
            </div>

            <div className="bg-white rounded-2xl border border-gray-200 p-6 space-y-4 shadow-sm">
              <p className="text-xs text-gray-500 leading-relaxed">{view?.consentText}</p>

              {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg px-3 py-2 font-medium">{error}</div>}
              
              {resendSuccess && (
                <div className="bg-green-50 border border-green-200 text-green-800 text-xs rounded-lg px-3 py-2 font-semibold animate-fadeIn">
                  📨 A fresh 6-digit verification code has been successfully dispatched to your email!
                </div>
              )}

              <div>
                <label className="text-xs font-bold text-gray-600">Type your full legal name</label>
                <input 
                  value={fullName} 
                  onChange={(e) => setFullName(e.target.value)} 
                  placeholder="e.g. patrick NSH"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm mt-1 focus:outline-none focus:ring-2 focus:ring-emerald-600 font-medium" 
                />
              </div>

              <div>
                <label className="text-xs font-bold text-gray-600">Verification code (sent by Email/SMS)</label>
                <input 
                  value={otp} 
                  onChange={(e) => setOtp(e.target.value)} 
                  maxLength={6} 
                  placeholder="6-digit code"
                  className="w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm mt-1 tracking-widest font-mono font-bold focus:outline-none focus:ring-2 focus:ring-emerald-600" 
                />
                
                <div className="mt-2">
                  <button 
                    onClick={handleResend} 
                    disabled={resending || countdown > 0} 
                    className="text-xs text-emerald-700 font-bold hover:underline disabled:text-gray-400 disabled:no-underline flex items-center gap-1"
                  >
                    {resending ? (
                      'Requesting new code…'
                    ) : countdown > 0 ? (
                      `Resend available in ${countdown}s`
                    ) : (
                      "Didn't get a code? Request a new one"
                    )}
                  </button>
                </div>
              </div>

              <div className="flex gap-3 pt-2">
                <button 
                  onClick={handleSign} 
                  disabled={busy || !otp || fullName.trim().length < 3}
                  className="flex-1 bg-[#0D6B3E] hover:bg-emerald-800 text-white rounded-lg py-3 text-sm font-extrabold shadow-sm transition-colors disabled:opacity-40"
                >
                  {busy ? 'Signing…' : '✓ I Agree — Sign Document'}
                </button>
                <button 
                  onClick={handleDecline} 
                  disabled={declining}
                  className="border border-gray-300 text-gray-600 rounded-lg py-3 px-5 text-sm font-bold bg-white hover:bg-gray-50 transition-colors"
                >
                  Decline
                </button>
              </div>
            </div>
          </>
        )}

        <p className="text-center text-x-[10px] text-gray-400 mt-6 font-medium">Protected electronic signature · IP address and timestamp recorded for verification</p>
      </div>
    </div>
  );
}
