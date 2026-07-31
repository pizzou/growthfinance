'use client';
import { useState } from 'react';
import { useTenant } from '../layout';
import { publicApi } from '@/services/api';
import { toast } from '@/hooks/useToast';

interface StatusStep {
  label: string;
  complete: boolean;
  failed: boolean;
}

interface Comment {
  message: string;
  createdAt: string;
  from: string;
}

interface PaymentHistory {
  paymentId: number;
  paymentDate: string;
  amount: number;
  method: string;
  status: string;
}

interface UpcomingInstallment {
  installmentNumber: number;
  dueDate: string;
  amount: number;
  principal: number;
  interest: number;
  status: string;
}

interface TimelineEvent {
  label: string;
  date: string;
}

interface DocumentRequirements {
  required: string[];
  missing: string[];
  unverified: string[];
  readyToApprove: boolean;
  readyToDisburse: boolean;
}

interface UploadedDoc {
  id: number;
  documentType: string;
  fileName: string;
  fileSize: number;
  uploadedAt: string;
  verificationStatus: string;
}

// What GET /public/applications/{ref}/status returns — application-stage info.
interface StatusResult {
  reference: string; status: string; statusLabel: string; statusSteps: StatusStep[];
  progressSteps: StatusStep[]; timeline: TimelineEvent[];
  loanType: string; amount: number; currency: string;
  submittedDate: string; updatedDate: string; rejectionReason?: string; maritalStatus?: string;
  documentsRequired: DocumentRequirements | null;
}

// What POST /public/dashboard returns — the rich, active-loan financial view.
interface DashboardResult {
  loanId: number;
  referenceNumber: string;
  borrowerName: string;
  status: string;
  loanType: string;
  principal: number;
  outstandingBalance: number;
  totalPaid: number;
  totalRepayable: number;
  repaymentProgress: number;
  currency: string;
  interestRate: number;
  nextInstallmentAmount: number;
  nextPaymentDate: string;
  nextDueDate: string;
  maturityDate: string;
  missedInstallments: number;
  daysOverdue: number;
  daysUntilDue: number;
  loanOfficer: string;
  activeLoans: number;
  overdueLoans: number;
  completedLoans: number;
  recentPayments: PaymentHistory[];
  upcomingInstallments: UpcomingInstallment[];
  availablePaymentMethods: string[];
}

// Merged view the page actually renders from.
type TrackResult = StatusResult & Partial<DashboardResult>;

const DOC_LABELS: Record<string, string> = {
  NATIONAL_ID: 'National ID', PASSPORT: 'Passport', DRIVING_LICENSE: "Driving License",
  VOTER_CARD: 'Voter Card', RESIDENCE_PERMIT: 'Residence Permit', PROOF_OF_ADDRESS: 'Proof of Address',
  BANK_STATEMENT: 'Bank Statement', PAYSLIP: 'Payslip', EMPLOYMENT_LETTER: 'Employment Letter',
  BUSINESS_REGISTRATION: 'Business Registration', TAX_CERTIFICATE: 'Tax Certificate',
  COLLATERAL_DOCUMENT: 'Collateral Document', MARRIAGE_CERTIFICATE: 'Marriage Certificate',
  SINGLE_CERTIFICATE: 'Single Status Certificate', SELFIE: 'Selfie Photo', SIGNATURE: 'Signature', OTHER: 'Other Document',
};
const docLabel = (t: string) => DOC_LABELS[t] ?? t.replace(/_/g, ' ');

const PAY_METHODS: { key: 'MOBILE_MONEY' | 'BANK_TRANSFER' | 'CARD'; label: string; icon: string; networks?: string[] }[] = [
  { key: 'MOBILE_MONEY', label: 'MTN Mobile Money', icon: '🟨', networks: ['MTN'] },
  { key: 'MOBILE_MONEY', label: 'Airtel Money', icon: '🟥', networks: ['AIRTEL'] },
  { key: 'BANK_TRANSFER', label: 'Bank Transfer', icon: '🏦' },
  { key: 'CARD', label: 'Visa / Mastercard', icon: '💳' },
];

export default function TrackPage() {
  const tenant = useTenant();
  const [reference, setReference] = useState('');
  const [phone, setPhone] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState<TrackResult | null>(null);
  const [comments, setComments] = useState<Comment[]>([]);
  const [commentsError, setCommentsError] = useState(false);

  // 💳 Payment sheet state
  const [showPaySheet, setShowPaySheet] = useState(false);
  const [payChoice, setPayChoice] = useState(0); // index into PAY_METHODS
  const [momoPhone, setMomoPhone] = useState('');
  const [cardNumber, setCardNumber] = useState('');
  const [cardExpiry, setCardExpiry] = useState(''); // MM/YY
  const [cardCvv, setCardCvv] = useState('');
  const [paying, setPaying] = useState(false);
  const [paySuccess, setPaySuccess] = useState(false);
  const [payMessage, setPayMessage] = useState('');
  const [downloadingDoc, setDownloadingDoc] = useState<'agreement' | 'schedule' | 'receipt' | null>(null);
  const [uploadedDocs, setUploadedDocs] = useState<UploadedDoc[]>([]);
  const [uploadingType, setUploadingType] = useState<string | null>(null);
  const [uploadError, setUploadError] = useState('');

  const primary = tenant?.primaryColor ?? '#0D6B3E';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setResult(null);
    setComments([]);
    setCommentsError(false);
    setPaySuccess(false);
    setLoading(true);

    const ref = reference.trim();
    const ph = phone.trim();

    try {
      // Application-stage info (status steps, rejection reason, submitted/updated dates)
      // and the rich financial dashboard (balances, installments, payment history, loan
      // officer) come from two different endpoints — fetch both and merge. The dashboard
      // call is allowed to fail quietly for an application that's still PENDING (no loan
      // numbers to show yet); the status call is the one that must succeed.
      const status = await publicApi.trackApplication(ref, ph) as StatusResult;
      let merged: TrackResult = status;

      try {
        const dashboard = await publicApi.trackDashboard(ref, ph) as DashboardResult;
        merged = { ...status, ...dashboard };
      } catch {
        // No active loan yet (still under review) — status-only view is fine.
      }

      setResult(merged);

      publicApi.trackComments(ref, ph)
        .then((c) => setComments(c as Comment[]))
        .catch(() => setCommentsError(true));

      publicApi.listDocuments(ref, ph)
        .then((d) => setUploadedDocs(d as UploadedDoc[]))
        .catch(() => setUploadedDocs([]));
    } catch (err: any) {
      setError(err.message || 'We could not find that application matching those parameters.');
    } finally {
      setLoading(false);
    }
  };

  const openPaySheet = () => {
    setShowPaySheet(true);
    setPaySuccess(false);
    setPayMessage('');
  };

  const handlePayment = async () => {
    if (!result) return;
    const choice = PAY_METHODS[payChoice];
    if (choice.key === 'MOBILE_MONEY' && !momoPhone) return;
    if (choice.key === 'CARD' && (!cardNumber || !cardExpiry || !cardCvv)) return;

    setPaying(true);
    setError('');
    setPaySuccess(false);

    try {
      const payload: {
        paymentMethod: 'MOBILE_MONEY' | 'BANK_TRANSFER' | 'CARD';
        phoneNumber?: string; network?: string;
        cardNumber?: string; cardCvv?: string; cardExpiryMonth?: string; cardExpiryYear?: string;
      } = { paymentMethod: choice.key };

      if (choice.key === 'MOBILE_MONEY') {
        payload.phoneNumber = momoPhone.trim();
        payload.network = choice.networks?.[0];
      } else if (choice.key === 'CARD') {
        const [month, year] = cardExpiry.split('/').map(s => s.trim());
        payload.cardNumber = cardNumber.trim();
        payload.cardExpiryMonth = month;
        payload.cardExpiryYear = year;
        payload.cardCvv = cardCvv.trim();
      }

      const res = await publicApi.initiatePayment(result.referenceNumber || result.reference, phone.trim(), payload) as any;
      const data = res?.data ?? res;

      setPaySuccess(true);
      setPayMessage(res?.message || 'Payment initiated.');
      if (data?.recorded) {
        setShowPaySheet(false);
        toast('success', 'Payment recorded — thank you!');
      } else {
        toast('success', 'Payment initiated — confirm on your phone or await bank settlement.');
      }
    } catch (err: any) {
      setError(err.response?.data?.error || err.message || 'Payment request failed.');
    } finally {
      setPaying(false);
    }
  };

  const handleDownloadDoc = async (doc: 'agreement' | 'schedule' | 'receipt', label: string) => {
    if (!result) return;
    setDownloadingDoc(doc);
    try {
      const res = await publicApi.downloadDocument(result.referenceNumber || result.reference, phone.trim(), doc);
      const url = URL.createObjectURL(res.data as Blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${label}-${result.referenceNumber || result.reference}.pdf`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 60000);
    } catch (err: any) {
      toast('error', err.response?.data?.error || err.message || `Could not download ${label}.`);
    } finally {
      setDownloadingDoc(null);
    }
  };

  const handleUpload = async (documentType: string, file: File) => {
    if (!result) return;
    setUploadingType(documentType);
    setUploadError('');
    const ref = result.referenceNumber || result.reference;
    const ph = phone.trim();
    try {
      await publicApi.uploadDocument(ref, ph, documentType, file);
      toast('success', `${docLabel(documentType)} uploaded — awaiting review.`);

      // Refresh both the document list and the status (so the missing-docs
      // list and 9-step tracker reflect the new upload immediately).
      const [docs, status] = await Promise.all([
        publicApi.listDocuments(ref, ph) as Promise<UploadedDoc[]>,
        publicApi.trackApplication(ref, ph) as Promise<StatusResult>,
      ]);
      setUploadedDocs(docs);
      setResult(prev => prev ? { ...prev, ...status } : status);
    } catch (err: any) {
      setUploadError(err.response?.data?.error || err.message || `Could not upload ${docLabel(documentType)}.`);
    } finally {
      setUploadingType(null);
    }
  };

  const fmt = (n?: number) => (n ?? 0).toLocaleString();
  const fmtDate = (d?: string) => d ? new Date(d).toLocaleDateString('en-RW', { year: 'numeric', month: 'short', day: 'numeric' }) : '—';
  const fmtDateTime = (d?: string) => d ? new Date(d).toLocaleString('en-RW', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';

  const canPay = !!result && (result.status === 'ACTIVE' || result.status === 'OVERDUE') && (result.outstandingBalance ?? 1) > 0;
  const dueNow = result?.nextInstallmentAmount && result.nextInstallmentAmount > 0 ? result.nextInstallmentAmount : result?.outstandingBalance ?? 0;

  return (
    <div className="bg-gray-50 min-h-screen antialiased">
      <section className="py-16 text-white text-center shadow-inner"
        style={{ background: `linear-gradient(135deg, ${primary} 0%, #0a4a2b 100%)` }}>
        <div className="max-w-2xl mx-auto px-4">
          <h1 className="text-3xl md:text-4xl font-black mb-3 tracking-tight">Track Your Loan</h1>
          <p className="text-white/80 text-sm">Check your application status, see your repayment schedule, and pay online — anytime.</p>
        </div>
      </section>

      <section className="max-w-lg mx-auto px-4 -mt-10 pb-24 space-y-6">
        <div className="bg-white rounded-xl shadow-xl border border-gray-100 p-6 md:p-8">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-1.5">Reference Number</label>
              <input
                required value={reference}
                onChange={e => setReference(e.target.value)}
                placeholder="e.g. GFS-2026-000123"
                className="w-full px-4 py-3 border border-gray-300 rounded-lg text-sm font-semibold uppercase focus:outline-none focus:ring-2 focus:ring-emerald-600"
              />
            </div>
            <div>
              <label className="block text-xs font-bold text-gray-500 uppercase tracking-wider mb-1.5">Phone Number</label>
              <input
                required value={phone}
                onChange={e => setPhone(e.target.value)}
                placeholder="Phone number used on application"
                className="w-full px-4 py-3 border border-gray-300 rounded-lg text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-emerald-600"
              />
            </div>
            {error && (
              <div className="bg-red-50 border border-red-200 text-red-700 text-xs rounded-lg px-3 py-2.5 font-semibold">{error}</div>
            )}
            <button type="submit" disabled={loading}
              className="w-full py-3.5 rounded-xl font-extrabold text-xs text-white shadow-md hover:opacity-95 transition-opacity disabled:opacity-60"
              style={{ backgroundColor: primary }}>
              {loading ? 'Looking up your loan...' : '🔍 Check Application Status'}
            </button>
          </form>
        </div>

        {result && (
          <div className="bg-white rounded-xl shadow-xl border border-gray-100 p-6 md:p-8 animate-fadeIn">
            <div className="flex items-start justify-between mb-2">
              <div>
                <div className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Reference Code</div>
                <div className="font-mono font-bold text-gray-900 text-sm">{result.referenceNumber || result.reference}</div>
              </div>
              <span className="px-3 py-1.5 rounded-full text-[10px] font-extrabold uppercase tracking-wide"
                style={{ backgroundColor: primary + '15', color: primary }}>
                {result.status}
              </span>
            </div>
            {(result.borrowerName || result.loanOfficer) && (
              <div className="flex items-center justify-between text-[11px] text-gray-500 mb-6 font-semibold">
                <span>{result.borrowerName}</span>
                {result.loanOfficer && <span>Officer: {result.loanOfficer}</span>}
              </div>
            )}
            {!result.borrowerName && !result.loanOfficer && <div className="mb-6" />}

            {/* Progress steps */}
            <div className="flex items-center mb-8 flex-wrap gap-y-3">
              {(result.progressSteps ?? result.statusSteps)?.map((step, i, arr) => (
                <div key={step.label} className="flex-1 flex items-center min-w-[70px]">
                  <div className="flex flex-col items-center flex-1">
                    <div className="w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold text-white shadow-sm"
                      style={{
                        backgroundColor: step.failed ? '#dc2626' : step.complete ? primary : '#e5e7eb',
                        color: step.complete || step.failed ? '#fff' : '#9ca3af'
                      }}>
                      {step.failed ? '✕' : step.complete ? '✓' : i + 1}
                    </div>
                    <div className="text-[9px] font-bold text-center text-gray-400 mt-1.5 leading-tight px-1 uppercase tracking-tight">{step.label}</div>
                  </div>
                  {i < arr.length - 1 && (
                    <div className="h-0.5 flex-1 -mt-5" style={{ backgroundColor: step.complete ? primary : '#e5e7eb' }} />
                  )}
                </div>
              ))}
            </div>

            {result.rejectionReason && (
              <div className="bg-red-50 border border-red-100 text-red-700 text-xs rounded-xl p-3 mb-4 font-medium">
                <strong>Rejection Notice:</strong> {result.rejectionReason}
              </div>
            )}

            {/* Loan summary */}
            <div className="grid grid-cols-2 gap-4 text-xs border-t border-gray-100 pt-4 font-semibold text-gray-600">
              <div>
                <span className="text-gray-400 text-[10px] uppercase font-bold">Loan Amount</span>
                <div className="font-bold text-gray-900 mt-1">{result.currency} {fmt(result.principal)}</div>
              </div>
              <div>
                <span className="text-gray-400 text-[10px] uppercase font-bold">Loan Product</span>
                <div className="font-bold text-gray-900 mt-1">{result.loanType}</div>
              </div>

              {(result.status === 'ACTIVE' || result.status === 'OVERDUE' || result.status === 'PAID' || result.status === 'CLOSED') && (
                <>
                  <div>
                    <span className="text-gray-400 text-[10px] uppercase font-bold">Outstanding Balance</span>
                    <div className="font-bold text-red-600 mt-1">{result.currency} {fmt(result.outstandingBalance)}</div>
                  </div>
                  <div>
                    <span className="text-gray-400 text-[10px] uppercase font-bold">Total Paid</span>
                    <div className="font-bold text-green-600 mt-1">{result.currency} {fmt(result.totalPaid)}</div>
                  </div>
                  <div>
                    <span className="text-gray-400 text-[10px] uppercase font-bold">Total Repayable</span>
                    <div className="font-bold text-gray-900 mt-1">{result.currency} {fmt(result.totalRepayable)}</div>
                  </div>
                  <div>
                    <span className="text-gray-400 text-[10px] uppercase font-bold">Interest Rate</span>
                    <div className="font-bold text-gray-900 mt-1">{result.interestRate != null ? `${result.interestRate}%` : '—'}</div>
                  </div>
                  <div>
                    <span className="text-gray-400 text-[10px] uppercase font-bold">Next Installment</span>
                    <div className="font-bold text-blue-700 mt-1">{result.currency} {fmt(result.nextInstallmentAmount)}</div>
                  </div>
                  <div>
                    <span className="text-gray-400 text-[10px] uppercase font-bold">Next Due Date</span>
                    <div className="font-bold text-gray-900 mt-1">{fmtDate(result.nextDueDate)}</div>
                  </div>

                  {result.repaymentProgress != null && (
                    <div className="col-span-2">
                      <div className="flex justify-between text-[10px] uppercase font-bold text-gray-400 mb-1">
                        <span>Repayment Progress</span>
                        <span>{Math.round(result.repaymentProgress)}%</span>
                      </div>
                      <div className="w-full bg-gray-100 rounded-full h-2">
                        <div className="h-2 rounded-full transition-all" style={{ width: `${Math.min(100, result.repaymentProgress)}%`, backgroundColor: primary }} />
                      </div>
                    </div>
                  )}

                  {result.status === 'OVERDUE' && (result.daysOverdue ?? 0) > 0 ? (
                    <div className="col-span-2 bg-red-50 border border-red-100 rounded-xl p-3 flex items-center gap-2">
                      <span className="text-lg">⚠️</span>
                      <div className="text-xs text-red-700 font-semibold">
                        Your installment is overdue by {result.daysOverdue} day{result.daysOverdue === 1 ? '' : 's'} — outstanding {result.currency} {fmt(result.outstandingBalance)}.
                      </div>
                    </div>
                  ) : result.daysUntilDue != null && result.daysUntilDue >= 0 && result.daysUntilDue <= 14 ? (
                    <div className="col-span-2 bg-blue-50 border border-blue-100 rounded-xl p-3 text-xs text-blue-700 font-semibold">
                      Reminder: your next installment of {result.currency} {fmt(result.nextInstallmentAmount)} is due in {result.daysUntilDue} day{result.daysUntilDue === 1 ? '' : 's'}.
                    </div>
                  ) : null}
                </>
              )}

              <div className="border-t col-span-2 pt-3 flex justify-between text-[11px] text-gray-400">
                <span>Created: {fmtDate(result.submittedDate)}</span>
                <span>Updated: {fmtDate(result.updatedDate)}</span>
              </div>
            </div>

            {canPay && (
              <div className="mt-4 pt-4 border-t border-gray-100">
                <button type="button" onClick={openPaySheet}
                  className="w-full bg-emerald-700 hover:bg-emerald-800 text-white text-xs font-extrabold py-3 rounded-xl transition-all shadow-md active:scale-[0.99]">
                  💳 Pay {result.currency} {fmt(dueNow)} Now
                </button>
              </div>
            )}
          </div>
        )}

        {/* Documents needed / uploaded */}
        {result?.documentsRequired && (
          <div className="bg-white rounded-xl shadow-xl border border-gray-100 p-6 md:p-8 animate-fadeIn">
            <h3 className="font-bold text-gray-900 text-sm mb-0.5">Required Documents</h3>
            <p className="text-xs text-gray-400 mb-4">
              {result.documentsRequired.missing.length > 0
                ? "We still need the documents below before your loan can move forward."
                : result.documentsRequired.unverified.length > 0
                  ? "Your documents are uploaded and awaiting review by our team."
                  : "All required documents are uploaded and verified."}
            </p>

            {uploadError && (
              <div className="bg-red-50 border border-red-200 text-red-700 text-xs rounded-lg px-3 py-2.5 font-semibold mb-3">{uploadError}</div>
            )}

            <div className="space-y-3">
              {result.documentsRequired.missing.map(docType => (
                <div key={docType} className="border border-amber-200 bg-amber-50/40 rounded-xl p-3">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-bold text-gray-800">{docLabel(docType)}</span>
                    <span className="text-[9px] font-bold uppercase text-amber-600">Missing</span>
                  </div>
                  <label className="block">
                    <input type="file" accept="image/*,application/pdf" className="hidden"
                      disabled={uploadingType === docType}
                      onChange={e => { const f = e.target.files?.[0]; if (f) handleUpload(docType, f); e.target.value = ''; }} />
                    <span className={`inline-flex items-center gap-1.5 text-xs font-bold px-3 py-2 rounded-lg cursor-pointer transition-colors ${
                      uploadingType === docType ? 'bg-gray-200 text-gray-400 cursor-wait' : 'text-white'
                    }`} style={uploadingType === docType ? {} : { backgroundColor: primary }}>
                      {uploadingType === docType ? 'Uploading…' : '📎 Upload File'}
                    </span>
                  </label>
                </div>
              ))}

              {uploadedDocs.filter(d => !result.documentsRequired!.missing.includes(d.documentType)).map(doc => {
                const rejected = doc.verificationStatus === 'REJECTED' || doc.verificationStatus === 'REPLACEMENT_REQUESTED';
                const verified = doc.verificationStatus === 'VERIFIED';
                return (
                  <div key={doc.id} className={`border rounded-xl p-3 ${rejected ? 'border-red-200 bg-red-50/40' : 'border-gray-200'}`}>
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-xs font-bold text-gray-800">{docLabel(doc.documentType)}</span>
                      <span className={`text-[9px] font-bold uppercase ${
                        verified ? 'text-green-600' : rejected ? 'text-red-600' : 'text-blue-500'
                      }`}>
                        {verified ? 'Verified' : rejected ? 'Needs Replacement' : 'Pending Review'}
                      </span>
                    </div>
                    <div className="text-[10px] text-gray-400 truncate">{doc.fileName}</div>
                    {rejected && (
                      <label className="block mt-2">
                        <input type="file" accept="image/*,application/pdf" className="hidden"
                          disabled={uploadingType === doc.documentType}
                          onChange={e => { const f = e.target.files?.[0]; if (f) handleUpload(doc.documentType, f); e.target.value = ''; }} />
                        <span className={`inline-flex items-center gap-1.5 text-xs font-bold px-3 py-2 rounded-lg cursor-pointer transition-colors ${
                          uploadingType === doc.documentType ? 'bg-gray-200 text-gray-400 cursor-wait' : 'bg-red-600 text-white'
                        }`}>
                          {uploadingType === doc.documentType ? 'Uploading…' : '📎 Upload Replacement'}
                        </span>
                      </label>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Timeline */}
        {result?.timeline && result.timeline.length > 0 && (
          <div className="bg-white rounded-xl shadow-xl border border-gray-100 p-6 md:p-8 animate-fadeIn">
            <h3 className="font-bold text-gray-900 text-sm mb-4">Timeline</h3>
            <div className="space-y-0">
              {result.timeline.map((ev, i) => (
                <div key={i} className="flex gap-3 pb-4 last:pb-0">
                  <div className="flex flex-col items-center">
                    <div className="w-2 h-2 rounded-full mt-1.5" style={{ backgroundColor: primary }} />
                    {i < result.timeline.length - 1 && <div className="w-px flex-1 bg-gray-200 mt-1" />}
                  </div>
                  <div className="pb-1">
                    <div className="text-xs font-bold text-gray-800">{ev.label}</div>
                    <div className="text-[10px] text-gray-400">{fmtDateTime(ev.date)}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Loan documents */}
        {result && (result.status === 'ACTIVE' || result.status === 'OVERDUE' || result.status === 'PAID' || result.status === 'CLOSED') && (
          <div className="bg-white rounded-xl shadow-xl border border-gray-100 p-6 md:p-8 animate-fadeIn">
            <h3 className="font-bold text-gray-900 text-sm mb-4">Loan Documents</h3>
            <div className="space-y-3">
              {([
                { key: 'agreement', label: 'Loan Agreement' },
                { key: 'schedule', label: 'Repayment Schedule' },
                { key: 'receipt', label: 'Disbursement Receipt' },
              ] as const).map(doc => (
                <button key={doc.key} type="button" disabled={downloadingDoc === doc.key}
                  onClick={() => handleDownloadDoc(doc.key, doc.label)}
                  className="w-full flex items-center justify-between border border-gray-200 rounded-xl px-4 py-3 text-xs font-bold text-gray-700 hover:bg-gray-50 transition-colors disabled:opacity-50">
                  <span>{doc.label}</span>
                  <span style={{ color: primary }}>{downloadingDoc === doc.key ? 'Downloading…' : '⬇ Download PDF'}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Upcoming installments */}
        {result?.upcomingInstallments && result.upcomingInstallments.length > 0 && (
          <div className="bg-white rounded-xl shadow-xl border border-gray-100 p-6 md:p-8 animate-fadeIn">
            <h3 className="font-bold text-gray-900 text-sm mb-4">Upcoming Installments</h3>
            <div className="space-y-3">
              {result.upcomingInstallments.map(inst => (
                <div key={inst.installmentNumber} className="flex items-center justify-between border-b border-gray-50 pb-3 last:border-0 last:pb-0">
                  <div>
                    <div className="text-xs font-bold text-gray-800">#{inst.installmentNumber} — {fmtDate(inst.dueDate)}</div>
                    <div className="text-[10px] text-gray-400">Principal {fmt(inst.principal)} + Interest {fmt(inst.interest)}</div>
                  </div>
                  <div className="text-right">
                    <div className="text-xs font-bold text-gray-900">{result.currency} {fmt(inst.amount)}</div>
                    <span className="text-[9px] font-bold uppercase text-amber-600">{inst.status}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Payment history */}
        {result?.recentPayments && result.recentPayments.length > 0 && (
          <div className="bg-white rounded-xl shadow-xl border border-gray-100 p-6 md:p-8 animate-fadeIn">
            <h3 className="font-bold text-gray-900 text-sm mb-4">Recent Payments</h3>
            <div className="space-y-3">
              {result.recentPayments.map(p => (
                <div key={p.paymentId} className="flex items-center justify-between border-b border-gray-50 pb-3 last:border-0 last:pb-0">
                  <div>
                    <div className="text-xs font-bold text-gray-800">{fmtDate(p.paymentDate)}</div>
                    <div className="text-[10px] text-gray-400">{p.method}</div>
                  </div>
                  <div className="text-right">
                    <div className="text-xs font-bold text-gray-900">{result.currency} {fmt(p.amount)}</div>
                    <span className={`text-[9px] font-bold uppercase ${p.status === 'COMPLETED' ? 'text-green-600' : 'text-gray-400'}`}>{p.status}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Messages */}
        {result && (
          <div className="bg-white rounded-xl shadow-xl border border-gray-100 p-6 md:p-8 animate-fadeIn">
            <h3 className="font-bold text-gray-900 text-sm mb-0.5">Updates from {tenant?.name ?? 'our team'}</h3>
            <p className="text-xs text-gray-400 mb-4">Messages from your loan officer, including any additional document feedback.</p>

            {commentsError && (
              <p className="text-xs text-amber-700 bg-amber-50 border border-amber-100 rounded-lg p-3 font-semibold">
                Could not load messages right now — please refresh.
              </p>
            )}

            {!commentsError && comments.length === 0 && (
              <p className="text-xs text-gray-400 italic">No messages yet.</p>
            )}

            {!commentsError && comments.length > 0 && (
              <div className="space-y-4">
                {comments.map((c, i) => (
                  <div key={i} className="border-l-2 pl-4 py-1 border-emerald-500 bg-gray-50/50 rounded-r-xl p-3">
                    <p className="text-xs font-bold text-gray-800 leading-relaxed">{c.message}</p>
                    <div className="text-[10px] text-gray-400 font-semibold mt-1.5 flex justify-between">
                      <span>By: {c.from || 'Loan Officer'}</span>
                      <span>{fmtDateTime(c.createdAt)}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Payment sheet */}
        {showPaySheet && result && (
          <div className="fixed inset-0 bg-black/70 z-50 flex items-center justify-center p-4 backdrop-blur-sm animate-fadeIn">
            <div className="bg-white rounded-2xl max-w-sm w-full overflow-hidden shadow-2xl border border-gray-100">
              <div className="px-6 py-4 bg-gray-50 border-b border-gray-100 flex items-center justify-between">
                <h3 className="font-black text-gray-900 text-xs uppercase tracking-wider">Choose Payment Method</h3>
                <button type="button" onClick={() => setShowPaySheet(false)} className="text-gray-400 hover:text-gray-700 font-bold text-xl">×</button>
              </div>

              <div className="p-6 space-y-4">
                <div className="text-xs text-gray-500 font-semibold text-center -mt-1">
                  Amount due: <span className="text-gray-900 font-bold">{result.currency} {fmt(dueNow)}</span>
                </div>

                <div className="grid grid-cols-2 gap-2">
                  {PAY_METHODS.map((m, i) => (
                    <button key={m.label} type="button" onClick={() => setPayChoice(i)}
                      className={`p-3 border rounded-xl flex flex-col items-center gap-1.5 font-bold text-xs transition-colors ${
                        payChoice === i ? 'border-emerald-500 bg-emerald-50/40 text-emerald-900' : 'border-gray-200 bg-white'
                      }`}>
                      <span>{m.icon}</span>
                      <span>{m.label}</span>
                    </button>
                  ))}
                </div>

                {PAY_METHODS[payChoice].key === 'MOBILE_MONEY' && (
                  <div>
                    <label className="text-[10px] font-bold text-gray-400 uppercase tracking-wider block">Mobile Money Number</label>
                    <input type="tel" value={momoPhone} onChange={(e) => setMomoPhone(e.target.value)}
                      placeholder="07XXXXXXXX" required
                      className="w-full border border-gray-300 rounded-xl px-3 py-2.5 text-xs font-semibold mt-1 focus:ring-2 focus:ring-emerald-600 focus:outline-none font-mono" />
                  </div>
                )}

                {PAY_METHODS[payChoice].key === 'BANK_TRANSFER' && (
                  <div className="bg-blue-50 border border-blue-100 rounded-xl p-3 text-xs text-blue-700 font-semibold">
                    You'll receive bank transfer details after confirming — settlement can take up to 1 business day.
                  </div>
                )}

                {PAY_METHODS[payChoice].key === 'CARD' && (
                  <div className="space-y-2">
                    <input type="text" value={cardNumber} onChange={(e) => setCardNumber(e.target.value)}
                      placeholder="Card number" required
                      className="w-full border border-gray-300 rounded-xl px-3 py-2.5 text-xs font-semibold font-mono focus:ring-2 focus:ring-emerald-600 focus:outline-none" />
                    <div className="flex gap-2">
                      <input type="text" value={cardExpiry} onChange={(e) => setCardExpiry(e.target.value)}
                        placeholder="MM/YY" required
                        className="w-1/2 border border-gray-300 rounded-xl px-3 py-2.5 text-xs font-semibold font-mono focus:ring-2 focus:ring-emerald-600 focus:outline-none" />
                      <input type="text" value={cardCvv} onChange={(e) => setCardCvv(e.target.value)}
                        placeholder="CVV" required
                        className="w-1/2 border border-gray-300 rounded-xl px-3 py-2.5 text-xs font-semibold font-mono focus:ring-2 focus:ring-emerald-600 focus:outline-none" />
                    </div>
                  </div>
                )}

                {paySuccess && (
                  <div className="bg-green-50 border border-green-200 text-green-800 text-xs rounded-lg px-3 py-2.5 font-bold animate-fadeIn">✓ {payMessage}</div>
                )}
                {error && (
                  <div className="bg-red-50 border border-red-200 text-red-700 text-xs rounded-lg px-3 py-2.5 font-semibold">{error}</div>
                )}

                <button type="button" onClick={handlePayment} disabled={paying}
                  className="w-full bg-[#0D6B3E] hover:bg-emerald-800 text-white font-extrabold text-xs py-3.5 rounded-xl shadow-md transition-all disabled:opacity-40">
                  {paying ? 'Processing...' : `⚡ Pay ${result.currency} ${fmt(dueNow)}`}
                </button>
              </div>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}