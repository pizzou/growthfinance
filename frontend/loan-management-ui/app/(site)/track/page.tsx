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

interface StatusResult {
reference: string;
status: string;
statusLabel: string;
statusSteps: StatusStep[];
progressSteps: StatusStep[];
timeline: TimelineEvent[];
loanType: string;
amount: number;
currency: string;
submittedDate: string;
updatedDate: string;
rejectionReason?: string;
maritalStatus?: string;
documentsRequired: DocumentRequirements | null;
}

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

type TrackResult = StatusResult & Partial<DashboardResult>;

const DOC_LABELS: Record<string, string> = {
NATIONAL_ID: 'National ID',
PASSPORT: 'Passport',
DRIVING_LICENSE: 'Driving License',
VOTER_CARD: 'Voter Card',
RESIDENCE_PERMIT: 'Residence Permit',
PROOF_OF_ADDRESS: 'Proof of Address',
BANK_STATEMENT: 'Bank Statement',
PAYSLIP: 'Payslip',
EMPLOYMENT_LETTER: 'Employment Letter',
BUSINESS_REGISTRATION: 'Business Registration',
TAX_CERTIFICATE: 'Tax Certificate',
COLLATERAL_DOCUMENT: 'Collateral Document',
MARRIAGE_CERTIFICATE: 'Marriage Certificate',
SINGLE_CERTIFICATE: 'Single Status Certificate',
SELFIE: 'Selfie Photo',
SIGNATURE: 'Signature',
OTHER: 'Other Document',
};

const docLabel = (t: string) =>
DOC_LABELS[t] ?? t.replace(/_/g, ' ');

const PAY_METHODS: {
key: 'MOBILE_MONEY' | 'BANK_TRANSFER' | 'CARD';
label: string;
icon: string;
networks?: string[];
}[] = [
{
key: 'MOBILE_MONEY',
label: 'MTN Mobile Money',
icon: '📱',
networks: ['MTN'],
},
{
key: 'MOBILE_MONEY',
label: 'Airtel Money',
icon: '📱',
networks: ['AIRTEL'],
},
{
key: 'BANK_TRANSFER',
label: 'Bank Transfer',
icon: '🏦',
},
{
key: 'CARD',
label: 'Visa / Mastercard',
icon: '💳',
},
];

const statusLabel = (status?: string) => {
if (!status) return 'Unknown';

return status
.replace(/_/g, ' ')
.toLowerCase()
.replace(/\b\w/g, c => c.toUpperCase());
};

export default function TrackPage() {
const tenant = useTenant();

const [reference, setReference] = useState('');
const [phone, setPhone] = useState('');

const [loading, setLoading] = useState(false);
const [error, setError] = useState('');

const [result, setResult] = useState<TrackResult | null>(null);

const [comments, setComments] = useState<Comment[]>([]);
const [commentsError, setCommentsError] = useState(false);

const [showPaySheet, setShowPaySheet] = useState(false);
const [payChoice, setPayChoice] = useState(0);

const [momoPhone, setMomoPhone] = useState('');
const [cardNumber, setCardNumber] = useState('');
const [cardExpiry, setCardExpiry] = useState('');
const [cardCvv, setCardCvv] = useState('');

const [paying, setPaying] = useState(false);
const [paySuccess, setPaySuccess] = useState(false);
const [payMessage, setPayMessage] = useState('');

const [downloadingDoc, setDownloadingDoc] =
useState<'agreement' | 'schedule' | 'receipt' | null>(null);

const [uploadedDocs, setUploadedDocs] = useState<UploadedDoc[]>([]);
const [uploadingType, setUploadingType] = useState<string | null>(null);
const [uploadError, setUploadError] = useState('');

const primary = tenant?.primaryColor ?? '#0D6B3E';

const accent = '#F4C430';

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
  const status =
    await publicApi.trackApplication(ref, ph) as StatusResult;

  let merged: TrackResult = status;

  try {
    const dashboard =
      await publicApi.trackDashboard(ref, ph) as DashboardResult;

    merged = {
      ...status,
      ...dashboard,
    };
  } catch {
    // Application may not have an active loan yet.
  }

  setResult(merged);

  publicApi.trackComments(ref, ph)
    .then(c => setComments(c as Comment[]))
    .catch(() => setCommentsError(true));

  publicApi.listDocuments(ref, ph)
    .then(d => setUploadedDocs(d as UploadedDoc[]))
    .catch(() => setUploadedDocs([]));

} catch (err: any) {
  setError(
    err.message ||
    'We could not find an application matching those details.'
  );
} finally {
  setLoading(false);
}


};

const openPaySheet = () => {
setShowPaySheet(true);
setPaySuccess(false);
setPayMessage('');
setError('');
};

const handlePayment = async () => {
if (!result) return;


const choice = PAY_METHODS[payChoice];

if (
  choice.key === 'MOBILE_MONEY' &&
  !momoPhone.trim()
) {
  setError('Please enter your mobile money number.');
  return;
}

if (
  choice.key === 'CARD' &&
  (!cardNumber || !cardExpiry || !cardCvv)
) {
  setError('Please complete your card details.');
  return;
}

setPaying(true);
setError('');
setPaySuccess(false);

try {
  const payload: {
    paymentMethod:
      | 'MOBILE_MONEY'
      | 'BANK_TRANSFER'
      | 'CARD';
    phoneNumber?: string;
    network?: string;
    cardNumber?: string;
    cardCvv?: string;
    cardExpiryMonth?: string;
    cardExpiryYear?: string;
  } = {
    paymentMethod: choice.key,
  };

  if (choice.key === 'MOBILE_MONEY') {
    payload.phoneNumber = momoPhone.trim();
    payload.network = choice.networks?.[0];
  }

  if (choice.key === 'CARD') {
    const [month, year] =
      cardExpiry.split('/').map(s => s.trim());

    payload.cardNumber = cardNumber.trim();
    payload.cardExpiryMonth = month;
    payload.cardExpiryYear = year;
    payload.cardCvv = cardCvv.trim();
  }

  const res =
    await publicApi.initiatePayment(
      result.referenceNumber || result.reference,
      phone.trim(),
      payload
    ) as any;

  const data = res?.data ?? res;

  setPaySuccess(true);

  setPayMessage(
    res?.message || 'Payment initiated successfully.'
  );

  if (data?.recorded) {
    setShowPaySheet(false);

    toast(
      'success',
      'Payment recorded successfully.'
    );
  } else {
    toast(
      'success',
      'Payment initiated. Please complete the confirmation.'
    );
  }

} catch (err: any) {
  setError(
    err.response?.data?.error ||
    err.message ||
    'Payment request failed.'
  );
} finally {
  setPaying(false);
}


};

const handleDownloadDoc = async (
doc: 'agreement' | 'schedule' | 'receipt',
label: string
) => {
if (!result) return;


setDownloadingDoc(doc);

try {
  const res =
    await publicApi.downloadDocument(
      result.referenceNumber || result.reference,
      phone.trim(),
      doc
    );

  const url =
    URL.createObjectURL(res.data as Blob);

  const a = document.createElement('a');

  a.href = url;

  a.download =
    `${label}-${result.referenceNumber || result.reference}.pdf`;

  document.body.appendChild(a);
  a.click();
  a.remove();

  setTimeout(
    () => URL.revokeObjectURL(url),
    60000
  );

} catch (err: any) {
  toast(
    'error',
    err.response?.data?.error ||
    err.message ||
    `Could not download ${label}.`
  );
} finally {
  setDownloadingDoc(null);
}


};

const handleUpload = async (
documentType: string,
file: File
) => {
if (!result) return;


setUploadingType(documentType);
setUploadError('');

const ref =
  result.referenceNumber ||
  result.reference;

const ph = phone.trim();

try {
  await publicApi.uploadDocument(
    ref,
    ph,
    documentType,
    file
  );

  toast(
    'success',
    `${docLabel(documentType)} uploaded successfully.`
  );

  const [docs, status] =
    await Promise.all([
      publicApi.listDocuments(
        ref,
        ph
      ) as Promise<UploadedDoc[]>,

      publicApi.trackApplication(
        ref,
        ph
      ) as Promise<StatusResult>,
    ]);

  setUploadedDocs(docs);

  setResult(prev =>
    prev
      ? { ...prev, ...status }
      : status
  );

} catch (err: any) {
  setUploadError(
    err.response?.data?.error ||
    err.message ||
    `Could not upload ${docLabel(documentType)}.`
  );
} finally {
  setUploadingType(null);
}


};

const fmt = (n?: number) =>
(n ?? 0).toLocaleString(
'en-RW',
{
minimumFractionDigits: 0,
maximumFractionDigits: 2,
}
);

const fmtDate = (d?: string) =>
d
? new Date(d).toLocaleDateString(
'en-RW',
{
year: 'numeric',
month: 'short',
day: 'numeric',
}
)
: '—';

const fmtDateTime = (d?: string) =>
d
? new Date(d).toLocaleString(
'en-RW',
{
year: 'numeric',
month: 'short',
day: 'numeric',
hour: '2-digit',
minute: '2-digit',
}
)
: '—';

const canPay =
!!result &&
(
result.status === 'ACTIVE' ||
result.status === 'OVERDUE'
) &&
(result.outstandingBalance ?? 1) > 0;

const dueNow =
result?.nextInstallmentAmount &&
result.nextInstallmentAmount > 0
? result.nextInstallmentAmount
: result?.outstandingBalance ?? 0;

const repaymentProgress =
Math.min(
100,
Math.max(
0,
result?.repaymentProgress ?? 0
)
);

const isActiveLoan =
result &&
(
result.status === 'ACTIVE' ||
result.status === 'OVERDUE' ||
result.status === 'PAID' ||
result.status === 'CLOSED'
);

const progressSteps =
result?.progressSteps ??
result?.statusSteps ??
[];

return ( <div className="min-h-screen bg-[#F6F8F7] text-gray-900">

  {/* =====================================================
      HERO
  ===================================================== */}

  <section
    className="relative overflow-hidden text-white"
    style={{
      background: `
        radial-gradient(
          circle at 85% 20%,
          rgba(244,196,48,.18),
          transparent 28%
        ),
        linear-gradient(
          135deg,
          ${primary} 0%,
          #07502F 55%,
          #063B25 100%
        )
      `,
    }}
  >
    <div className="absolute inset-0 opacity-[0.04]">
      <div
        className="absolute inset-0"
        style={{
          backgroundImage:
            'linear-gradient(rgba(255,255,255,.5) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.5) 1px, transparent 1px)',
          backgroundSize: '40px 40px',
        }}
      />
    </div>

    <div className="relative max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-14 md:py-20">

      <div className="max-w-3xl">

        <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-white/10 border border-white/10 text-[10px] font-black uppercase tracking-[0.18em] mb-5">
          <span
            className="w-2 h-2 rounded-full"
            style={{ backgroundColor: accent }}
          />
          {tenant?.name ?? 'Growth Finance'}
        </div>

        <h1 className="text-4xl md:text-5xl font-black tracking-tight leading-[1.05]">
          Your loan,
          <span
            className="block"
            style={{ color: accent }}
          >
            always within reach.
          </span>
        </h1>

        <p className="mt-5 text-white/75 text-sm md:text-base max-w-xl leading-7">
          Securely track your application, monitor repayment,
          upload documents, download loan records and make
          payments from one place.
        </p>

      </div>
    </div>
  </section>

  {/* =====================================================
      SEARCH
  ===================================================== */}

  <main className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 -mt-8 relative z-10 pb-20">

    <div className="bg-white rounded-2xl shadow-[0_20px_60px_rgba(0,0,0,.10)] border border-gray-100 overflow-hidden">

      <div className="p-6 md:p-8">

        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 mb-7">

          <div>
            <div className="text-[10px] font-black uppercase tracking-[0.18em] text-emerald-700 mb-1">
              Secure Loan Lookup
            </div>

            <h2 className="text-xl md:text-2xl font-black text-gray-900">
              Track your application
            </h2>

            <p className="text-xs text-gray-500 mt-1">
              Enter the reference number and phone used during application.
            </p>
          </div>

          <div className="hidden md:flex items-center gap-2 text-[10px] font-bold text-gray-400">
            <span className="w-7 h-7 rounded-full bg-emerald-50 flex items-center justify-center">
              🔒
            </span>
            Your information is protected
          </div>

        </div>

        <form
          onSubmit={handleSubmit}
          className="grid grid-cols-1 md:grid-cols-[1fr_1fr_auto] gap-3"
        >

          <div>
            <label className="block text-[10px] font-black uppercase tracking-wider text-gray-500 mb-2">
              Reference Number
            </label>

            <input
              required
              value={reference}
              onChange={e =>
                setReference(
                  e.target.value.toUpperCase()
                )
              }
              placeholder="GFS-2026-000123"
              className="w-full h-12 px-4 border border-gray-200 rounded-xl bg-gray-50 text-sm font-bold uppercase tracking-wide focus:bg-white focus:outline-none focus:ring-2 focus:ring-emerald-600/20 focus:border-emerald-600 transition"
            />
          </div>

          <div>
            <label className="block text-[10px] font-black uppercase tracking-wider text-gray-500 mb-2">
              Phone Number
            </label>

            <input
              required
              type="tel"
              value={phone}
              onChange={e =>
                setPhone(e.target.value)
              }
              placeholder="Phone used on application"
              className="w-full h-12 px-4 border border-gray-200 rounded-xl bg-gray-50 text-sm font-semibold focus:bg-white focus:outline-none focus:ring-2 focus:ring-emerald-600/20 focus:border-emerald-600 transition"
            />
          </div>

          <div className="flex items-end">

            <button
              type="submit"
              disabled={loading}
              className="w-full md:w-auto h-12 px-7 rounded-xl text-white text-xs font-black shadow-lg transition-all hover:-translate-y-0.5 disabled:opacity-60 disabled:hover:translate-y-0"
              style={{
                backgroundColor: primary,
              }}
            >
              {loading
                ? 'Checking…'
                : 'Track Loan →'}
            </button>

          </div>

        </form>

        {error && (
          <div className="mt-4 flex items-start gap-3 bg-red-50 border border-red-100 text-red-700 rounded-xl p-4 text-xs font-semibold">
            <span className="text-base">!</span>
            <span>{error}</span>
          </div>
        )}

      </div>
    </div>

    {/* =====================================================
        RESULT
    ===================================================== */}

    {result && (
      <div className="mt-6 space-y-5 animate-fadeIn">

        {/* Loan identity / status */}

        <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">

          <div
            className="h-1.5"
            style={{
              backgroundColor:
                result.status === 'OVERDUE'
                  ? '#DC2626'
                  : accent,
            }}
          />

          <div className="p-6 md:p-8">

            <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-5">

              <div>

                <div className="text-[10px] uppercase tracking-[0.18em] font-black text-gray-400">
                  Loan Reference
                </div>

                <div className="flex flex-wrap items-center gap-3 mt-2">

                  <span className="font-mono text-lg font-black text-gray-900">
                    {result.referenceNumber ||
                      result.reference}
                  </span>

                  <span
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-[10px] font-black uppercase tracking-wide"
                    style={{
                      backgroundColor:
                        result.status === 'OVERDUE'
                          ? '#FEF2F2'
                          : `${primary}12`,
                      color:
                        result.status === 'OVERDUE'
                          ? '#DC2626'
                          : primary,
                    }}
                  >
                    <span className="w-1.5 h-1.5 rounded-full bg-current" />
                    {statusLabel(
                      result.statusLabel ||
                      result.status
                    )}
                  </span>

                </div>

                <div className="flex flex-wrap gap-x-5 gap-y-1 mt-3 text-xs text-gray-500">
                  {result.borrowerName && (
                    <span>
                      <strong className="text-gray-700">
                        Borrower:
                      </strong>{' '}
                      {result.borrowerName}
                    </span>
                  )}

                  {result.loanType && (
                    <span>
                      <strong className="text-gray-700">
                        Product:
                      </strong>{' '}
                      {result.loanType}
                    </span>
                  )}

                  {result.loanOfficer && (
                    <span>
                      <strong className="text-gray-700">
                        Officer:
                      </strong>{' '}
                      {result.loanOfficer}
                    </span>
                  )}
                </div>

              </div>

              <div className="md:text-right">

                <div className="text-[10px] uppercase tracking-wider font-black text-gray-400">
                  Application Updated
                </div>

                <div className="text-xs font-bold text-gray-700 mt-1">
                  {fmtDateTime(result.updatedDate)}
                </div>

              </div>

            </div>

            {/* Progress */}

            {progressSteps.length > 0 && (
              <div className="mt-9">

                <div className="flex items-center justify-between mb-3">

                  <span className="text-[10px] font-black uppercase tracking-[0.15em] text-gray-400">
                    Application Progress
                  </span>

                  <span
                    className="text-[10px] font-black"
                    style={{ color: primary }}
                  >
                    {progressSteps.filter(
                      s => s.complete
                    ).length}{' '}
                    of {progressSteps.length}
                  </span>

                </div>

                <div className="relative">

                  <div className="absolute left-0 right-0 top-4 h-1 bg-gray-100 rounded-full" />

                  <div
                    className="absolute left-0 top-4 h-1 rounded-full transition-all"
                    style={{
                      width: `${
                        progressSteps.length
                          ? (
                              progressSteps.filter(
                                s => s.complete
                              ).length /
                              progressSteps.length
                            ) * 100
                          : 0
                      }%`,
                      backgroundColor: primary,
                    }}
                  />

                  <div className="relative grid grid-cols-3 sm:grid-cols-5 lg:grid-cols-9 gap-2">

                    {progressSteps.map(
                      (step, i) => (
                        <div
                          key={`${step.label}-${i}`}
                          className="flex flex-col items-center text-center"
                        >

                          <div
                            className="w-8 h-8 rounded-full border-4 border-white shadow-sm flex items-center justify-center text-[10px] font-black"
                            style={{
                              backgroundColor:
                                step.failed
                                  ? '#DC2626'
                                  : step.complete
                                    ? primary
                                    : '#E5E7EB',
                              color:
                                step.failed ||
                                step.complete
                                  ? '#fff'
                                  : '#9CA3AF',
                            }}
                          >
                            {step.failed
                              ? '×'
                              : step.complete
                                ? '✓'
                                : i + 1}
                          </div>

                          <span className="mt-2 text-[8px] sm:text-[9px] leading-tight font-bold text-gray-500 uppercase">
                            {step.label}
                          </span>

                        </div>
                      )
                    )}

                  </div>

                </div>

              </div>
            )}

          </div>
        </div>

        {/* Rejection */}

        {result.rejectionReason && (
          <div className="bg-red-50 border border-red-100 rounded-2xl p-5 flex gap-4">

            <div className="w-10 h-10 shrink-0 rounded-xl bg-red-100 flex items-center justify-center text-red-600">
              ⚠️
            </div>

            <div>
              <div className="text-xs font-black text-red-900">
                Application Decision
              </div>

              <p className="text-xs text-red-700 mt-1 leading-5">
                {result.rejectionReason}
              </p>
            </div>

          </div>
        )}

        {/* =================================================
            ACTIVE LOAN COMMAND CENTER
        ================================================= */}

        {isActiveLoan && (
          <>

            <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">

              <div className="bg-white border border-gray-100 rounded-2xl p-5 shadow-sm">
                <div className="text-[9px] uppercase tracking-wider font-black text-gray-400">
                  Original Principal
                </div>

                <div className="text-lg font-black text-gray-900 mt-2">
                  {result.currency}{' '}
                  {fmt(result.principal)}
                </div>
              </div>

              <div className="bg-white border border-gray-100 rounded-2xl p-5 shadow-sm">
                <div className="text-[9px] uppercase tracking-wider font-black text-gray-400">
                  Outstanding
                </div>

                <div className="text-lg font-black text-red-600 mt-2">
                  {result.currency}{' '}
                  {fmt(result.outstandingBalance)}
                </div>
              </div>

              <div className="bg-white border border-gray-100 rounded-2xl p-5 shadow-sm">
                <div className="text-[9px] uppercase tracking-wider font-black text-gray-400">
                  Total Paid
                </div>

                <div className="text-lg font-black text-emerald-700 mt-2">
                  {result.currency}{' '}
                  {fmt(result.totalPaid)}
                </div>
              </div>

              <div className="bg-white border border-gray-100 rounded-2xl p-5 shadow-sm">
                <div className="text-[9px] uppercase tracking-wider font-black text-gray-400">
                  Total Repayable
                </div>

                <div className="text-lg font-black text-gray-900 mt-2">
                  {result.currency}{' '}
                  {fmt(result.totalRepayable)}
                </div>
              </div>

            </div>

            {/* Payment / repayment hero */}

            <div
              className="rounded-2xl overflow-hidden shadow-lg"
              style={{
                background:
                  'linear-gradient(135deg, #063B25 0%, #0D6B3E 100%)',
              }}
            >

              <div className="p-6 md:p-8 text-white">

                <div className="grid lg:grid-cols-[1.2fr_.8fr] gap-8 items-center">

                  <div>

                    <div className="text-[10px] uppercase tracking-[0.18em] font-black text-white/50">
                      Repayment Progress
                    </div>

                    <div className="flex items-end gap-3 mt-2">

                      <div className="text-4xl md:text-5xl font-black">
                        {Math.round(
                          repaymentProgress
                        )}%
                      </div>

                      <div className="text-xs text-white/60 pb-2">
                        of your loan repaid
                      </div>

                    </div>

                    <div className="mt-5 h-3 bg-white/10 rounded-full overflow-hidden">

                      <div
                        className="h-full rounded-full transition-all"
                        style={{
                          width: `${repaymentProgress}%`,
                          backgroundColor: accent,
                        }}
                      />

                    </div>

                    <div className="flex justify-between mt-2 text-[10px] font-bold text-white/50">
                      <span>
                        Paid {result.currency}{' '}
                        {fmt(result.totalPaid)}
                      </span>

                      <span>
                        Remaining {result.currency}{' '}
                        {fmt(result.outstandingBalance)}
                      </span>
                    </div>

                  </div>

                  <div className="bg-white/10 border border-white/10 rounded-2xl p-5">

                    <div className="text-[10px] uppercase tracking-wider font-black text-white/50">
                      Next Payment
                    </div>

                    <div className="text-2xl font-black mt-2">
                      {result.currency}{' '}
                      {fmt(result.nextInstallmentAmount)}
                    </div>

                    <div className="text-xs text-white/65 mt-1">
                      Due {fmtDate(result.nextDueDate)}
                    </div>

                    {canPay && (
                      <button
                        type="button"
                        onClick={openPaySheet}
                        className="w-full mt-4 h-11 rounded-xl text-[#173C27] text-xs font-black shadow-lg hover:brightness-105 transition"
                        style={{
                          backgroundColor: accent,
                        }}
                      >
                        Pay Now →
                      </button>
                    )}

                  </div>

                </div>

              </div>
            </div>

            {/* Loan details */}

            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">

              {[
                [
                  'Interest Rate',
                  result.interestRate != null
                    ? `${result.interestRate}%`
                    : '—',
                ],
                [
                  'Next Due',
                  fmtDate(result.nextDueDate),
                ],
                [
                  'Maturity',
                  fmtDate(result.maturityDate),
                ],
                [
                  'Missed Installments',
                  String(
                    result.missedInstallments ?? 0
                  ),
                ],
              ].map(([label, value]) => (
                <div
                  key={label}
                  className="bg-white rounded-2xl border border-gray-100 p-5"
                >
                  <div className="text-[9px] uppercase tracking-wider font-black text-gray-400">
                    {label}
                  </div>

                  <div className="text-sm font-black text-gray-900 mt-2">
                    {value}
                  </div>
                </div>
              ))}

            </div>

            {/* Due warning */}

            {result.status === 'OVERDUE' &&
              (result.daysOverdue ?? 0) > 0 && (
                <div className="bg-red-50 border border-red-100 rounded-2xl p-5 flex items-center justify-between gap-4">

                  <div className="flex gap-3 items-center">

                    <div className="w-10 h-10 rounded-xl bg-red-100 flex items-center justify-center">
                      ⚠️
                    </div>

                    <div>
                      <div className="text-xs font-black text-red-900">
                        Payment overdue
                      </div>

                      <div className="text-xs text-red-700 mt-1">
                        Your payment is{' '}
                        {result.daysOverdue}{' '}
                        day
                        {result.daysOverdue === 1
                          ? ''
                          : 's'} overdue.
                      </div>
                    </div>

                  </div>

                  {canPay && (
                    <button
                      type="button"
                      onClick={openPaySheet}
                      className="px-4 py-2.5 rounded-xl bg-red-600 text-white text-[10px] font-black"
                    >
                      Pay Now
                    </button>
                  )}

                </div>
              )}

          </>
        )}

        {/* =================================================
            DOCUMENTS
        ================================================= */}

        {result.documentsRequired && (
          <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 md:p-8">

            <div className="flex items-start justify-between gap-4 mb-6">

              <div>
                <div className="text-[10px] uppercase tracking-[0.15em] font-black text-emerald-700">
                  Verification
                </div>

                <h3 className="text-lg font-black text-gray-900 mt-1">
                  Documents
                </h3>

                <p className="text-xs text-gray-500 mt-1">
                  Keep your application documents complete and up to date.
                </p>
              </div>

              <div className="text-right">
                <div className="text-xl font-black text-gray-900">
                  {result.documentsRequired.required.length -
                    result.documentsRequired.missing.length}
                  /
                  {result.documentsRequired.required.length}
                </div>

                <div className="text-[9px] uppercase font-black text-gray-400">
                  Submitted
                </div>
              </div>

            </div>

            {uploadError && (
              <div className="bg-red-50 border border-red-100 text-red-700 rounded-xl p-3 text-xs font-semibold mb-4">
                {uploadError}
              </div>
            )}

            <div className="grid md:grid-cols-2 gap-3">

              {result.documentsRequired.missing.map(
                docType => (
                  <div
                    key={docType}
                    className="border border-amber-200 bg-amber-50/50 rounded-2xl p-4"
                  >

                    <div className="flex items-start justify-between gap-3">

                      <div>
                        <div className="text-xs font-black text-gray-900">
                          {docLabel(docType)}
                        </div>

                        <div className="text-[10px] text-amber-700 font-semibold mt-1">
                          Required to continue
                        </div>
                      </div>

                      <span className="px-2 py-1 rounded-full bg-amber-100 text-amber-700 text-[8px] font-black uppercase">
                        Missing
                      </span>

                    </div>

                    <label className="inline-block mt-4">

                      <input
                        type="file"
                        accept="image/*,application/pdf"
                        className="hidden"
                        disabled={
                          uploadingType === docType
                        }
                        onChange={e => {
                          const f =
                            e.target.files?.[0];

                          if (f)
                            handleUpload(
                              docType,
                              f
                            );

                          e.target.value = '';
                        }}
                      />

                      <span
                        className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl text-white text-[10px] font-black cursor-pointer"
                        style={{
                          backgroundColor: primary,
                        }}
                      >
                        {uploadingType === docType
                          ? 'Uploading…'
                          : 'Upload Document'}
                      </span>

                    </label>

                  </div>
                )
              )}

              {uploadedDocs
                .filter(
                  d =>
                    !result.documentsRequired!.missing.includes(
                      d.documentType
                    )
                )
                .map(doc => {

                  const rejected =
                    doc.verificationStatus ===
                      'REJECTED' ||
                    doc.verificationStatus ===
                      'REPLACEMENT_REQUESTED';

                  const verified =
                    doc.verificationStatus ===
                    'VERIFIED';

                  return (
                    <div
                      key={doc.id}
                      className={`rounded-2xl border p-4 ${
                        rejected
                          ? 'border-red-200 bg-red-50/50'
                          : 'border-gray-100 bg-gray-50/40'
                      }`}
                    >

                      <div className="flex items-start justify-between gap-3">

                        <div className="min-w-0">

                          <div className="text-xs font-black text-gray-900">
                            {docLabel(
                              doc.documentType
                            )}
                          </div>

                          <div className="text-[10px] text-gray-400 mt-1 truncate">
                            {doc.fileName}
                          </div>

                        </div>

                        <span
                          className={`shrink-0 px-2 py-1 rounded-full text-[8px] font-black uppercase ${
                            verified
                              ? 'bg-emerald-100 text-emerald-700'
                              : rejected
                                ? 'bg-red-100 text-red-700'
                                : 'bg-blue-100 text-blue-700'
                          }`}
                        >
                          {verified
                            ? 'Verified'
                            : rejected
                              ? 'Replace'
                              : 'Reviewing'}
                        </span>

                      </div>

                      {rejected && (
                        <label className="inline-block mt-4">

                          <input
                            type="file"
                            accept="image/*,application/pdf"
                            className="hidden"
                            disabled={
                              uploadingType ===
                              doc.documentType
                            }
                            onChange={e => {
                              const f =
                                e.target.files?.[0];

                              if (f)
                                handleUpload(
                                  doc.documentType,
                                  f
                                );

                              e.target.value = '';
                            }}
                          />

                          <span className="inline-flex px-3 py-2 rounded-xl bg-red-600 text-white text-[10px] font-black cursor-pointer">
                            Upload Replacement
                          </span>

                        </label>
                      )}

                    </div>
                  );
                })}

            </div>

          </div>
        )}

        {/* =================================================
            UPCOMING PAYMENTS
        ================================================= */}

        {result.upcomingInstallments &&
          result.upcomingInstallments.length > 0 && (
            <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 md:p-8">

              <div className="mb-6">

                <div className="text-[10px] uppercase tracking-[0.15em] font-black text-emerald-700">
                  Repayment Plan
                </div>

                <h3 className="text-lg font-black text-gray-900 mt-1">
                  Upcoming installments
                </h3>

              </div>

              <div className="overflow-hidden border border-gray-100 rounded-2xl">

                {result.upcomingInstallments.map(
                  (inst, i) => (
                    <div
                      key={inst.installmentNumber}
                      className={`grid grid-cols-[auto_1fr_auto] md:grid-cols-[80px_1fr_150px_150px] gap-4 items-center p-4 ${
                        i <
                        result.upcomingInstallments!.length -
                          1
                          ? 'border-b border-gray-100'
                          : ''
                      }`}
                    >

                      <div className="w-9 h-9 rounded-xl bg-emerald-50 text-emerald-700 flex items-center justify-center text-[10px] font-black">
                        {inst.installmentNumber}
                      </div>

                      <div>
                        <div className="text-xs font-black text-gray-900">
                          Installment #
                          {inst.installmentNumber}
                        </div>

                        <div className="text-[10px] text-gray-400 mt-1">
                          Due {fmtDate(inst.dueDate)}
                        </div>
                      </div>

                      <div className="hidden md:block">
                        <div className="text-[9px] uppercase font-black text-gray-400">
                          Principal
                        </div>

                        <div className="text-xs font-bold text-gray-700 mt-1">
                          {result.currency}{' '}
                          {fmt(inst.principal)}
                        </div>
                      </div>

                      <div className="text-right">
                        <div className="text-xs font-black text-gray-900">
                          {result.currency}{' '}
                          {fmt(inst.amount)}
                        </div>

                        <div className="text-[9px] uppercase font-black text-amber-600 mt-1">
                          {statusLabel(
                            inst.status
                          )}
                        </div>
                      </div>

                    </div>
                  )
                )}

              </div>

            </div>
          )}

        {/* =================================================
            PAYMENT HISTORY
        ================================================= */}

        {result.recentPayments &&
          result.recentPayments.length > 0 && (
            <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 md:p-8">

              <div className="mb-6">

                <div className="text-[10px] uppercase tracking-[0.15em] font-black text-emerald-700">
                  Account Activity
                </div>

                <h3 className="text-lg font-black text-gray-900 mt-1">
                  Recent payments
                </h3>

              </div>

              <div className="divide-y divide-gray-100">

                {result.recentPayments.map(
                  payment => (
                    <div
                      key={payment.paymentId}
                      className="flex items-center justify-between py-4 first:pt-0 last:pb-0"
                    >

                      <div className="flex items-center gap-3">

                        <div className="w-10 h-10 rounded-xl bg-emerald-50 flex items-center justify-center">
                          ✓
                        </div>

                        <div>
                          <div className="text-xs font-black text-gray-900">
                            Payment received
                          </div>

                          <div className="text-[10px] text-gray-400 mt-1">
                            {fmtDate(
                              payment.paymentDate
                            )}{' '}
                            · {payment.method}
                          </div>
                        </div>

                      </div>

                      <div className="text-right">

                        <div className="text-xs font-black text-gray-900">
                          {result.currency}{' '}
                          {fmt(payment.amount)}
                        </div>

                        <div
                          className={`text-[9px] font-black uppercase mt-1 ${
                            payment.status ===
                            'COMPLETED'
                              ? 'text-emerald-600'
                              : 'text-gray-400'
                          }`}
                        >
                          {statusLabel(
                            payment.status
                          )}
                        </div>

                      </div>

                    </div>
                  )
                )}

              </div>

            </div>
          )}

        {/* =================================================
            TIMELINE
        ================================================= */}

        {result.timeline &&
          result.timeline.length > 0 && (
            <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 md:p-8">

              <div className="mb-7">

                <div className="text-[10px] uppercase tracking-[0.15em] font-black text-emerald-700">
                  Application History
                </div>

                <h3 className="text-lg font-black text-gray-900 mt-1">
                  Timeline
                </h3>

              </div>

              <div className="relative">

                <div className="absolute left-4 top-2 bottom-2 w-px bg-gray-200" />

                <div className="space-y-6">

                  {result.timeline.map(
                    (event, i) => (
                      <div
                        key={i}
                        className="relative flex gap-5"
                      >

                        <div
                          className="relative z-10 w-8 h-8 rounded-full border-4 border-white shadow-sm flex items-center justify-center"
                          style={{
                            backgroundColor:
                              primary,
                          }}
                        >
                          <span className="w-2 h-2 rounded-full bg-white" />
                        </div>

                        <div className="pt-0.5">

                          <div className="text-xs font-black text-gray-900">
                            {event.label}
                          </div>

                          <div className="text-[10px] text-gray-400 mt-1">
                            {fmtDateTime(
                              event.date
                            )}
                          </div>

                        </div>

                      </div>
                    )
                  )}

                </div>

              </div>

            </div>
          )}

        {/* =================================================
            LOAN DOCUMENTS
        ================================================= */}

        {isActiveLoan && (
          <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 md:p-8">

            <div className="mb-6">

              <div className="text-[10px] uppercase tracking-[0.15em] font-black text-emerald-700">
                Records
              </div>

              <h3 className="text-lg font-black text-gray-900 mt-1">
                Loan documents
              </h3>

              <p className="text-xs text-gray-500 mt-1">
                Download official copies of your loan records.
              </p>

            </div>

            <div className="grid md:grid-cols-3 gap-3">

              {[
                {
                  key: 'agreement' as const,
                  label: 'Loan Agreement',
                  icon: '📄',
                },
                {
                  key: 'schedule' as const,
                  label: 'Repayment Schedule',
                  icon: '📊',
                },
                {
                  key: 'receipt' as const,
                  label: 'Disbursement Receipt',
                  icon: '🧾',
                },
              ].map(doc => (
                <button
                  key={doc.key}
                  type="button"
                  disabled={
                    downloadingDoc ===
                    doc.key
                  }
                  onClick={() =>
                    handleDownloadDoc(
                      doc.key,
                      doc.label
                    )
                  }
                  className="group text-left border border-gray-100 rounded-2xl p-5 hover:border-emerald-200 hover:bg-emerald-50/30 transition-all disabled:opacity-50"
                >

                  <div className="flex items-center justify-between">

                    <span className="w-10 h-10 rounded-xl bg-gray-50 group-hover:bg-white flex items-center justify-center text-lg">
                      {doc.icon}
                    </span>

                    <span
                      className="text-xs font-black"
                      style={{
                        color: primary,
                      }}
                    >
                      {downloadingDoc ===
                      doc.key
                        ? 'Downloading…'
                        : '↓'}
                    </span>

                  </div>

                  <div className="text-xs font-black text-gray-900 mt-4">
                    {doc.label}
                  </div>

                  <div className="text-[10px] text-gray-400 mt-1">
                    Official PDF document
                  </div>

                </button>
              ))}

            </div>

          </div>
        )}

        {/* =================================================
            MESSAGES
        ================================================= */}

        <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 md:p-8">

          <div className="mb-6">

            <div className="text-[10px] uppercase tracking-[0.15em] font-black text-emerald-700">
              Communication
            </div>

            <h3 className="text-lg font-black text-gray-900 mt-1">
              Updates from {tenant?.name ?? 'our team'}
            </h3>

            <p className="text-xs text-gray-500 mt-1">
              Important messages and document feedback from your loan team.
            </p>

          </div>

          {commentsError && (
            <div className="bg-amber-50 border border-amber-100 text-amber-800 rounded-xl p-4 text-xs font-semibold">
              We could not load your messages right now. Please refresh and try again.
            </div>
          )}

          {!commentsError &&
            comments.length === 0 && (
              <div className="border border-dashed border-gray-200 rounded-2xl p-8 text-center">

                <div className="text-2xl mb-2">
                  💬
                </div>

                <div className="text-xs font-black text-gray-700">
                  No messages yet
                </div>

                <div className="text-[10px] text-gray-400 mt-1">
                  Your loan team will post updates here when needed.
                </div>

              </div>
            )}

          {!commentsError &&
            comments.length > 0 && (
              <div className="space-y-3">

                {comments.map(
                  (comment, i) => (
                    <div
                      key={i}
                      className="rounded-2xl bg-gray-50 border border-gray-100 p-5"
                    >

                      <div className="flex items-start gap-3">

                        <div
                          className="w-9 h-9 shrink-0 rounded-xl flex items-center justify-center text-white text-xs font-black"
                          style={{
                            backgroundColor:
                              primary,
                          }}
                        >
                          {(
                            comment.from ||
                            'L'
                          )
                            .charAt(0)
                            .toUpperCase()}
                        </div>

                        <div className="min-w-0">

                          <div className="text-xs font-black text-gray-900">
                            {comment.from ||
                              'Loan Officer'}
                          </div>

                          <div className="text-[10px] text-gray-400 mt-0.5">
                            {fmtDateTime(
                              comment.createdAt
                            )}
                          </div>

                          <p className="text-xs text-gray-700 leading-6 mt-3">
                            {comment.message}
                          </p>

                        </div>

                      </div>

                    </div>
                  )
                )}

              </div>
            )}

        </div>

      </div>
    )}

  </main>

  {/* =====================================================
      PAYMENT MODAL
  ===================================================== */}

  {showPaySheet && result && (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">

      <div className="bg-white rounded-3xl max-w-md w-full shadow-2xl overflow-hidden">

        <div
          className="px-6 py-5 text-white"
          style={{
            background:
              'linear-gradient(135deg, #063B25, #0D6B3E)',
          }}
        >

          <div className="flex items-start justify-between">

            <div>

              <div className="text-[9px] uppercase tracking-[0.18em] font-black text-white/50">
                Secure Payment
              </div>

              <h3 className="text-lg font-black mt-1">
                Make a payment
              </h3>

            </div>

            <button
              type="button"
              onClick={() =>
                setShowPaySheet(false)
              }
              className="w-8 h-8 rounded-full bg-white/10 text-white/70 hover:text-white text-lg"
            >
              ×
            </button>

          </div>

          <div className="mt-5 bg-white/10 border border-white/10 rounded-2xl p-4">

            <div className="text-[9px] uppercase tracking-wider font-black text-white/50">
              Amount Due
            </div>

            <div className="text-2xl font-black mt-1">
              {result.currency}{' '}
              {fmt(dueNow)}
            </div>

            <div className="text-[10px] text-white/50 mt-1">
              Reference {result.referenceNumber || result.reference}
            </div>

          </div>

        </div>

        <div className="p-6 space-y-5">

          <div>

            <div className="text-[10px] uppercase tracking-wider font-black text-gray-400 mb-2">
              Payment Method
            </div>

            <div className="grid grid-cols-2 gap-2">

              {PAY_METHODS.map(
                (method, i) => (
                  <button
                    key={`${method.label}-${i}`}
                    type="button"
                    onClick={() =>
                      setPayChoice(i)
                    }
                    className={`p-3 rounded-2xl border text-left transition-all ${
                      payChoice === i
                        ? 'border-emerald-500 bg-emerald-50'
                        : 'border-gray-100 bg-gray-50 hover:bg-white'
                    }`}
                  >

                    <div className="text-lg">
                      {method.icon}
                    </div>

                    <div className="text-[10px] font-black text-gray-800 mt-2">
                      {method.label}
                    </div>

                  </button>
                )
              )}

            </div>

          </div>

          {PAY_METHODS[payChoice].key ===
            'MOBILE_MONEY' && (
            <div>

              <label className="text-[10px] uppercase tracking-wider font-black text-gray-400">
                Mobile Money Number
              </label>

              <input
                type="tel"
                value={momoPhone}
                onChange={e =>
                  setMomoPhone(
                    e.target.value
                  )
                }
                placeholder="07XXXXXXXX"
                className="w-full h-12 mt-2 px-4 rounded-xl border border-gray-200 bg-gray-50 text-sm font-bold focus:outline-none focus:ring-2 focus:ring-emerald-600/20 focus:border-emerald-600"
              />

            </div>
          )}

          {PAY_METHODS[payChoice].key ===
            'BANK_TRANSFER' && (
            <div className="bg-blue-50 border border-blue-100 rounded-2xl p-4 text-xs text-blue-800 leading-5">
              After confirming, you will receive the bank transfer instructions required to settle this payment.
            </div>
          )}

          {PAY_METHODS[payChoice].key ===
            'CARD' && (
            <div className="space-y-3">

              <input
                type="text"
                value={cardNumber}
                onChange={e =>
                  setCardNumber(
                    e.target.value
                  )
                }
                placeholder="Card number"
                className="w-full h-12 px-4 rounded-xl border border-gray-200 bg-gray-50 text-sm font-bold font-mono focus:outline-none focus:ring-2 focus:ring-emerald-600/20 focus:border-emerald-600"
              />

              <div className="grid grid-cols-2 gap-3">

                <input
                  type="text"
                  value={cardExpiry}
                  onChange={e =>
                    setCardExpiry(
                      e.target.value
                    )
                  }
                  placeholder="MM/YY"
                  className="w-full h-12 px-4 rounded-xl border border-gray-200 bg-gray-50 text-sm font-bold font-mono focus:outline-none focus:ring-2 focus:ring-emerald-600/20 focus:border-emerald-600"
                />

                <input
                  type="password"
                  value={cardCvv}
                  onChange={e =>
                    setCardCvv(
                      e.target.value
                    )
                  }
                  placeholder="CVV"
                  className="w-full h-12 px-4 rounded-xl border border-gray-200 bg-gray-50 text-sm font-bold font-mono focus:outline-none focus:ring-2 focus:ring-emerald-600/20 focus:border-emerald-600"
                />

              </div>

            </div>
          )}

          {paySuccess && (
            <div className="bg-emerald-50 border border-emerald-100 text-emerald-800 rounded-xl p-4 text-xs font-bold">
              ✓ {payMessage}
            </div>
          )}

          {error && (
            <div className="bg-red-50 border border-red-100 text-red-700 rounded-xl p-4 text-xs font-semibold">
              {error}
            </div>
          )}

          <button
            type="button"
            onClick={handlePayment}
            disabled={paying}
            className="w-full h-12 rounded-xl text-[#183D28] font-black text-xs shadow-lg disabled:opacity-50"
            style={{
              backgroundColor: accent,
            }}
          >
            {paying
              ? 'Processing payment…'
              : `Confirm Payment · ${result.currency} ${fmt(dueNow)}`}
          </button>

          <div className="text-center text-[9px] text-gray-400">
            Payments are processed securely. Never share your PIN or OTP with anyone.
          </div>

        </div>

      </div>

    </div>
  )}

</div>

);
}
