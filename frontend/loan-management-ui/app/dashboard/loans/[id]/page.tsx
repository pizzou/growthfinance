'use client';
import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { loanApi, paymentApi, creditBureauApi, esignatureApi } from '@/services/api';
import { Loan, Payment } from '@/types';
import { Card, CardHeader, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { StatusBadge, RiskBadge, Pill } from '@/components/ui/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/Table';
import { Modal } from '@/components/ui/Modal';
import { FormGroup, Input, Select, Textarea, Alert } from '@/components/ui/Form';
import { formatCurrency, formatDate, formatNumber, LOAN_TYPE_META } from '@/lib/utils';
import {
  IconBank,
  IconSignature,
  IconCard,
  IconCoins,
  IconSend,
  IconCheckCircle,
  IconClock,
  IconFileText,
  IconAlertTriangle,
  IconFileEdit,
  IconSearch,
  IconCalendar,
  IconFlag
} from '@/components/ui/Icons';
import { useAuth } from '@/hooks/useAuth';
import { useOnlineStatus } from '@/hooks/useOnlineStatus';
import { queueAction, cacheGet, cacheSet } from '@/lib/offlineDb';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import DocumentsPanel from '@/components/DocumentsPanel';
import { DOCUMENT_TYPE_LABELS } from '@/services/fileService';

const TABS = ['Overview', 'Borrower', 'Documents', 'Schedule', 'Timeline', 'Comments'] as const;
type Tab = typeof TABS[number];

function Field({ label, value }: { label: string; value?: React.ReactNode }) {
  return (
    <div>
      <div className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-1">{label}</div>
      <div className="text-sm font-medium text-gray-800">{value ?? '—'}</div>
    </div>
  );
}

export default function LoanDetailPage() {
  const { id }   = useParams<{ id: string }>();
  const router   = useRouter();
  const { currency, locale, isOfficer } = useAuth();
  const fc = (n?: number) => formatCurrency(n, currency, locale);

  const [loan, setLoan]         = useState<Loan | null>(null);
  const [schedule, setSchedule] = useState<Payment[]>([]);
  const [loading, setLoading]   = useState(true);
  const [tab, setTab]           = useState<Tab>('Overview');
  const [msg, setMsg]           = useState<{ type: 'error'|'success'; text: string } | null>(null);

  // Pay modal
  const [payOpen, setPayOpen]   = useState(false);
  const [payForm, setPayForm]   = useState({ amount: '', paymentMethod: 'BANK_TRANSFER', transactionId: '', channel: '', notes: '' });
  const [paying, setPaying]     = useState(false);

  // Status modal
  const [stOpen, setStOpen]     = useState(false);
  const [stForm, setStForm]     = useState({ status: '', rejectionReason: '', internalNotes: '', interestRate: '' });
  const [stSaving, setStSaving] = useState(false);
  const [cbBusy, setCbBusy]     = useState(false);
  const [esignBusy, setEsignBusy] = useState(false);

  const load = () => {
    Promise.all([loanApi.get(Number(id)), loanApi.schedule(Number(id))])
      .then(([l, s]: [any, any]) => {
        setLoan(l); setSchedule(Array.isArray(s) ? s : []);
        cacheSet(`/loans/${id}`, { loan: l, schedule: s });
      })
      .catch(async (e) => {
        const cached = await cacheGet<{ loan: Loan; schedule: Payment[] }>(`/loans/${id}`);
        if (cached) {
          setLoan(cached.loan); setSchedule(cached.schedule);
          setMsg({ type: 'error', text: "You're offline — showing the last saved version of this loan." });
        } else {
          setMsg({ type: 'error', text: e.message });
        }
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, [id]);

  const [comments, setComments] = useState<any[]>([]);
  const [commentText, setCommentText] = useState('');
  const [commentVisible, setCommentVisible] = useState(true);
  const [commentSaving, setCommentSaving] = useState(false);

  // Required-documents checklist — lets an officer see what's missing/unverified before
  // clicking Approve/Disburse and hitting the same check the server enforces.
  const [docReq, setDocReq] = useState<{
    required: string[]; missing: string[]; unverified: string[];
    readyToApprove: boolean; readyToDisburse: boolean;
  } | null>(null);
  const loadDocReq = () => loanApi.documentRequirements(Number(id)).then((r: any) => setDocReq(r)).catch(() => setDocReq(null));
  useEffect(() => { loadDocReq(); }, [id]);


  const loadComments = () => loanApi.getComments(Number(id)).then((c) => setComments(Array.isArray(c) ? c : [])).catch(() => {});
  useEffect(() => { loadComments(); }, [id]);

  const handleAddComment = async () => {
    if (!commentText.trim()) return;
    setCommentSaving(true);
    try {
      await loanApi.addComment(Number(id), commentText.trim(), commentVisible);
      setCommentText('');
      loadComments();
    } catch (e: any) {
      setMsg({ type: 'error', text: e.message });
    } finally {
      setCommentSaving(false);
    }
  };


  const online = useOnlineStatus();

  const handlePay = async (e: React.FormEvent) => {
    e.preventDefault(); setPaying(true); setMsg(null);

    if (!online) {
      try {
        await queueAction({
          url: `/loans/${id}/payments`,
          method: 'POST',
          body: { ...payForm, amount: Number(payForm.amount) },
          label: `Payment — ${loan?.borrower?.firstName ?? 'Loan'} ${loan?.referenceNumber ?? ''} (${payForm.amount})`,
        });
        setMsg({ type: 'success', text: "Saved offline — you're not connected. This payment will submit automatically once you're back online." });
        setPayOpen(false);
      } catch (err: any) {
        setMsg({ type: 'error', text: 'Could not save offline: ' + err.message });
      }
      setPaying(false);
      return;
    }

    try {
      await paymentApi.record(Number(id), { ...payForm, amount: Number(payForm.amount) });
      setMsg({ type: 'success', text: 'Payment recorded successfully!' });
      setPayOpen(false); load();
    } catch (err: any) { setMsg({ type: 'error', text: err.message }); }
    setPaying(false);
  };

  const handleStatus = async (e: React.FormEvent) => {
    e.preventDefault(); setStSaving(true); setMsg(null);
    try {
      if (stForm.status === 'APPROVED')  await loanApi.approve(Number(id), stForm.internalNotes, stForm.interestRate ? Number(stForm.interestRate) : undefined);
      else if (stForm.status === 'REJECTED') await loanApi.reject(Number(id), stForm.rejectionReason);
      else if (stForm.status === 'DISBURSED') await loanApi.disburse(Number(id), 'BANK_TRANSFER');
      else if (stForm.status) await loanApi.updateStatus(Number(id), stForm.status, stForm.internalNotes);
      else throw new Error('Select a status first');
      setMsg({ type: 'success', text: 'Status updated!' });
      setStOpen(false); load(); loadDocReq();
    } catch (err: any) { setMsg({ type: 'error', text: err.message }); }
    setStSaving(false);
  };

  const handleCreditBureauCheck = async () => {
    if (!loan?.borrower) return;
    setCbBusy(true); setMsg(null);
    try {
      const result: any = await creditBureauApi.check(loan.borrower.id);
      const simulated = result?.provider === 'INTERNAL_SIMULATED';
      setMsg({ type: simulated ? 'error' : 'success', text: simulated
        ? `⚠️ No live credit bureau connected — this is an internal ESTIMATE (score ${result?.creditScore ?? 'N/A'}, ${result?.riskGrade ?? 'N/A'}), not a verified bureau report. Do not treat this as confirmed credit history.`
        : `Credit bureau check complete via ${result?.provider} — score ${result?.creditScore ?? 'N/A'} (${result?.riskGrade ?? 'N/A'})` });
    } catch (err: any) { setMsg({ type: 'error', text: err.message }); }
    setCbBusy(false);
  };

  const handleSendForSignature = async () => {
    setEsignBusy(true); setMsg(null);
    try {
      await esignatureApi.initiate(Number(id));
      setMsg({ type: 'success', text: 'Signing link + verification code sent to the borrower by SMS.' });
    } catch (err: any) { setMsg({ type: 'error', text: err.message }); }
    setEsignBusy(false);
  };

  if (loading) return (
    <div className="flex items-center justify-center h-64">
      <div className="w-8 h-8 border-2 border-teal-500 border-t-transparent rounded-full animate-spin" />
    </div>
  );
  if (!loan) return <div className="bg-red-50 border border-red-200 rounded-xl p-6 text-red-700">Loan not found</div>;

  const prog = loan.totalRepayable && loan.totalPaid
    ? Math.min(100, Math.round((loan.totalPaid / loan.totalRepayable) * 100)) : 0;

  const chartData = schedule.filter(p => p.paid).slice(-12).map((p, i) => ({
    n: `#${p.installmentNumber}`, balance: p.outstandingAfter ?? 0,
    principal: p.principalComponent, interest: p.interestComponent,
  }));
  const totalPenalty = schedule.reduce((sum, p) => sum + (p.penalty ?? 0), 0);

  return (
    <div>
      {/* Header */}
      <div className="flex items-start justify-between mb-6">
        <div>
          <button onClick={() => router.back()} className="text-sm text-gray-400 hover:text-gray-600 mb-2 flex items-center gap-1">← Back</button>
          <h1 className="text-2xl font-extrabold text-gray-900">{loan.referenceNumber}</h1>
          <div className="flex items-center gap-2 mt-1 flex-wrap">
            <StatusBadge status={loan.status} />
            {loan.riskCategory && <RiskBadge category={loan.riskCategory} score={loan.riskScore} />}
            <Pill label={`${LOAN_TYPE_META[loan.loanType]?.icon} ${LOAN_TYPE_META[loan.loanType]?.label ?? loan.loanType}`} color="blue" />
            <Pill label={loan.currency} color="teal" />
            {loan.daysOverdue && loan.daysOverdue > 0
              ? <Pill label={<span className="inline-flex items-center gap-1"><IconAlertTriangle className="w-3 h-3" />{loan.daysOverdue}d overdue</span>} color="red" /> : null}
          </div>
        </div>
        <div className="flex gap-2">
          {isOfficer && loan.borrower && (
            <Button variant="outline" onClick={handleCreditBureauCheck} disabled={cbBusy}>
              <IconBank className="w-4 h-4" /> {cbBusy ? 'Checking…' : 'Credit Bureau Check'}
            </Button>
          )}
          {isOfficer && (loan.status === 'APPROVED' || loan.status === 'DISBURSED' || loan.status === 'ACTIVE') && (
            <Button variant="outline" onClick={handleSendForSignature} disabled={esignBusy}>
              <IconSignature className="w-4 h-4" /> {esignBusy ? 'Sending…' : 'Send for E-Signature'}
            </Button>
          )}
          {isOfficer && <Button variant="outline" onClick={() => setStOpen(true)}>Update Status</Button>}
          {loan.status === 'ACTIVE' && (
            <Button onClick={() => { setPayForm(f => ({ ...f, amount: String(loan.totalRepayable ? Math.round((loan.totalRepayable / loan.durationMonths!) * 100) / 100 : '') })); setPayOpen(true); }}>
              <IconCard className="w-4 h-4" /> Record Payment
            </Button>
          )}
        </div>
      </div>

      {msg && <Alert type={msg.type}>{msg.text}</Alert>}

      {/* Hero KPIs */}
      <div className="grid grid-cols-2 lg:grid-cols-5 gap-3 mb-5">
        {[
          { label: 'Principal',   value: fc(loan.amount),              Icon: IconCoins,       color: '#3B82F6' },
          { label: 'Disbursed',   value: fc(loan.disbursedAmount),     Icon: IconSend,        color: '#8B5CF6' },
          { label: 'Total Paid',  value: fc(loan.totalPaid),           Icon: IconCheckCircle, color: '#0D9488' },
          { label: 'Outstanding', value: fc(loan.outstandingBalance),  Icon: IconClock,       color: '#F59E0B' },
          { label: 'Penalty',     value: fc(totalPenalty),             Icon: IconFileText,    color: '#6B7280' },
        ].map(({ label, value, Icon, color }) => (
          <div key={label} className="bg-white rounded-xl border border-gray-200 p-4">
            <Icon className="w-5 h-5 mb-1.5" style={{ color }} />
            <div className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">{label}</div>
            <div className="text-lg font-extrabold text-gray-900 font-mono mt-0.5">{value}</div>
          </div>
        ))}
      </div>

      {/* Progress bar */}
      <Card className="mb-5">
        <CardBody>
          <div className="flex items-center justify-between mb-2">
            <span className="text-sm font-semibold text-gray-700">Repayment Progress</span>
            <span className="text-lg font-extrabold text-teal-600">{prog}%</span>
          </div>
          <div className="w-full bg-gray-100 rounded-full h-3 overflow-hidden">
            <div className="h-3 rounded-full transition-all duration-500"
              style={{ width: `${prog}%`, background: prog >= 100 ? '#0D9488' : prog > 50 ? '#3B82F6' : '#F59E0B' }} />
          </div>
          <div className="flex justify-between text-xs text-gray-400 mt-1.5">
            <span>{fc(loan.totalPaid)} paid</span>
            <span>{fc(loan.outstandingBalance)} remaining</span>
            <span>{fc(loan.totalRepayable)} total</span>
          </div>
          {loan.status === 'PAID' && (
            <div className="mt-2 bg-teal-50 border border-teal-200 text-teal-700 text-xs rounded-lg px-3 py-2 flex items-center gap-1.5">
              <IconCheckCircle className="w-4 h-4" /> Loan fully repaid
            </div>
          )}
        </CardBody>
      </Card>

      {/* Required-documents checklist — warns before the officer hits the same gate the
          server enforces in approveLoan/disburseLoan, instead of only finding out on click. */}
      {docReq && (docReq.missing.length > 0 || docReq.unverified.length > 0) && (
        <div className="bg-amber-50 border border-amber-200 rounded-xl px-4 py-3 mb-5 text-sm">
          <div className="font-bold text-amber-800 mb-1 flex items-center gap-1.5"><IconAlertTriangle className="w-4 h-4" /> Required documents not yet in order</div>
          {docReq.missing.length > 0 && (
            <div className="text-amber-700">
              Not uploaded (blocks <strong>Approve</strong>): {docReq.missing.map(t => DOCUMENT_TYPE_LABELS[t] ?? t).join(', ')}
            </div>
          )}
          {docReq.missing.length === 0 && docReq.unverified.length > 0 && (
            <div className="text-amber-700">
              Uploaded but not yet staff-verified (blocks <strong>Disburse</strong>): {docReq.unverified.map(t => DOCUMENT_TYPE_LABELS[t] ?? t).join(', ')}
            </div>
          )}
          <button onClick={() => setTab('Documents')}
            className="text-xs font-bold text-amber-800 underline mt-1">
            Go to Documents →
          </button>
        </div>
      )}

      {/* Tabs */}
      <div className="flex border-b border-gray-200 mb-5 gap-0">
        {TABS.map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={`px-5 py-2.5 text-sm font-semibold border-b-2 transition-colors -mb-px
              ${tab === t ? 'border-teal-500 text-teal-600' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>
            {t}
          </button>
        ))}
      </div>

      {/* Tab: Overview */}
      {tab === 'Overview' && (
        <Card>
          <CardHeader title="Loan Details" />
          <CardBody>
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-x-6 gap-y-5">
              <Field label="Loan Type"   value={`${LOAN_TYPE_META[loan.loanType]?.icon} ${LOAN_TYPE_META[loan.loanType]?.label}`} />
              <Field label="Interest Rate" value={`${loan.interestRate}% p.a.`} />
              <Field label="Term"        value={`${loan.durationMonths} months`} />
              <Field label="Schedule"    value={loan.repaymentFrequency} />
              <Field label="Processing Fee" value={fc(loan.processingFee)} />
              <Field label="DTI Ratio"   value={loan.debtToIncomeRatio ? `${loan.debtToIncomeRatio.toFixed(1)}%` : undefined} />
              <Field label="Credit Score" value={loan.creditScoreSnapshot} />
              <Field label="Risk Category" value={loan.riskCategory} />
              <Field label="Start Date"   value={formatDate(loan.startDate, locale)} />
              <Field label="Approved"     value={formatDate(loan.approvedAt, locale)} />
              <Field label="Disbursed"    value={formatDate(loan.disbursedAt, locale)} />
              <Field label="Maturity"     value={formatDate(loan.maturityDate, locale)} />
              <Field label="Purpose" value={<span className="whitespace-pre-wrap">{loan.purpose}</span>} />
              <Field label="Collateral"   value={loan.collateralDescription} />
              <Field label="Collateral Value" value={fc(loan.collateralValue)} />
              <Field label="Currency"     value={loan.currency} />
            </div>
            {(loan.rejectionReason || loan.internalNotes) && (
              <>
                <hr className="my-4 border-gray-100" />
                {loan.rejectionReason && <Field label="Rejection Reason" value={<span className="text-red-600">{loan.rejectionReason}</span>} />}
                {loan.internalNotes   && <div className="mt-3"><Field label="Internal Notes" value={loan.internalNotes} /></div>}
              </>
            )}
            <hr className="my-4 border-gray-100" />
            <div className="flex gap-3 flex-wrap text-xs text-gray-500">
              {loan.loanOfficer && <span className="bg-gray-100 px-3 py-1.5 rounded-lg">👤 Officer: <strong>{loan.loanOfficer.name}</strong></span>}
              {loan.approvedBy  && <span className="bg-gray-100 px-3 py-1.5 rounded-lg inline-flex items-center gap-1.5"><IconCheckCircle className="w-3.5 h-3.5 text-teal-600" /> Approved by: <strong>{loan.approvedBy.name}</strong></span>}
            </div>
          </CardBody>
        </Card>
      )}

      {/* Tab: Borrower */}
      {tab === 'Borrower' && loan.borrower && (
        <Card>
          <CardHeader title="Borrower Profile" />
          <CardBody>
            <div className="flex items-center gap-4 mb-5">
              <div className="w-14 h-14 bg-teal-100 rounded-full flex items-center justify-center text-2xl font-bold text-teal-700">
                {loan.borrower.firstName?.[0]}{loan.borrower.lastName?.[0]}
              </div>
              <div>
                <div className="font-bold text-lg text-gray-900">{loan.borrower.firstName} {loan.borrower.lastName}</div>
                <div className="text-sm text-gray-500">{loan.borrower.email} · {loan.borrower.phone}</div>
              </div>
              <div className="ml-auto flex items-center gap-6">
                <div>
                  <div className="text-xs text-gray-400 mb-0.5">Credit Score</div>
                  <div className={`text-2xl font-extrabold ${(loan.borrower.creditScore ?? 0) >= 700 ? 'text-teal-600' : 'text-orange-500'}`}>
                    {loan.borrower.creditScore ?? '—'}
                  </div>
                </div>
                <button onClick={() => setTab('Documents')}
                  className="text-xs font-bold px-3 py-2 rounded-lg border border-teal-200 bg-teal-50 text-teal-700 hover:bg-teal-100 transition whitespace-nowrap">
                  View KYC Documents →
                </button>
              </div>
            </div>
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-x-6 gap-y-4">
              <Field label="National ID"     value={loan.borrower.nationalId} />
              <Field label="Nationality"     value={loan.borrower.nationality} />
              <Field label="Date of Birth"   value={formatDate(loan.borrower.dateOfBirth, locale)} />
              <Field label="Gender"          value={loan.borrower.gender} />
              <Field label="Employer"        value={loan.borrower.employerName} />
              <Field label="Job Title"       value={loan.borrower.jobTitle} />
              <Field label="Employment Type" value={loan.borrower.employmentType} />
              <Field label="Monthly Income"  value={fc(loan.borrower.monthlyIncome)} />
              <Field label="Monthly Expenses" value={fc(loan.borrower.monthlyExpenses)} />
              <Field label="Net Worth"       value={fc(loan.borrower.netWorth)} />
              <Field label="City"            value={loan.borrower.city} />
              <Field label="Country"         value={loan.borrower.country} />
              <Field label="Bank"            value={loan.borrower.bankName} />
              <Field label="Account Number"  value={loan.borrower.bankAccountNumber} />
            </div>
          </CardBody>
        </Card>
      )}

      {/* Tab: Documents — this is the KYC review surface for a Loan Officer opening an
          application: uploaded documents, verification status, and officer comments, without
          navigating away to the borrower's own profile page. */}
      {tab === 'Documents' && (
        loan.borrower?.id
          ? <DocumentsPanel borrowerId={loan.borrower.id} key={loan.borrower.id} />
          : <div className="bg-white rounded-xl border border-gray-200 p-8 text-center text-gray-400 text-sm">
              No borrower record is linked to this loan, so documents can't be shown.
            </div>
      )}

      {/* Tab: Schedule */}
      {tab === 'Schedule' && (
        <>
          {chartData.length > 1 && (
            <Card className="mb-4">
              <CardHeader title="Outstanding Balance Over Time" />
              <CardBody>
                <ResponsiveContainer width="100%" height={180}>
                  <AreaChart data={chartData}>
                    <defs>
                      <linearGradient id="balGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%"  stopColor="#0D9488" stopOpacity={0.15} />
                        <stop offset="95%" stopColor="#0D9488" stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="#F3F4F6" vertical={false} />
                    <XAxis dataKey="n" tick={{ fontSize: 11, fill: '#9CA3AF' }} />
                    <YAxis tick={{ fontSize: 11, fill: '#9CA3AF' }} tickFormatter={v => fc(v)} />
                    <Tooltip formatter={(v: number) => fc(v)} />
                    <Area type="monotone" dataKey="balance" stroke="#0D9488" fill="url(#balGrad)" strokeWidth={2} name="Balance" />
                  </AreaChart>
                </ResponsiveContainer>
              </CardBody>
            </Card>
          )}
          <Card>
            <CardHeader title={`Repayment Schedule (${schedule.length} installments)`} />
            <Table>
              <Thead>
                <tr><Th>#</Th><Th>Due Date</Th><Th>Amount</Th><Th>Principal</Th><Th>Interest</Th><Th>Penalty</Th><Th>Balance After</Th><Th>Status</Th><Th>Paid Date</Th><Th>Method</Th></tr>
              </Thead>
              <Tbody>
                {schedule.length === 0
                  ? <Tr><Td className="text-center py-10 text-gray-400">No schedule generated yet</Td></Tr>
                  : schedule.map(p => (
                    <Tr key={p.id} className={p.isLate ? 'bg-orange-50' : ''}>
                      <Td className="font-mono text-xs text-gray-500">{p.installmentNumber}</Td>
                      <Td>{formatDate(p.dueDate, locale)}</Td>
                      <Td className="font-semibold">{fc(p.amount)}</Td>
                      <Td className="text-blue-600">{fc(p.principalComponent)}</Td>
                      <Td className="text-purple-600">{fc(p.interestComponent)}</Td>
                      <Td className="text-red-500">{p.penalty && p.penalty > 0 ? fc(p.penalty) : '—'}</Td>
                      <Td>{fc(p.outstandingAfter)}</Td>
                      <Td>
                        {p.paid
                          ? <span className="inline-flex items-center gap-1 text-xs font-semibold text-teal-700 bg-teal-50 px-2 py-0.5 rounded-full">✓ Paid</span>
                          : p.isLate
                            ? <span className="inline-flex items-center gap-1 text-xs font-semibold text-orange-700 bg-orange-50 px-2 py-0.5 rounded-full">⚠ Overdue</span>
                            : <span className="inline-flex items-center gap-1 text-xs font-semibold text-gray-500 bg-gray-50 px-2 py-0.5 rounded-full">Pending</span>
                        }
                      </Td>
                      <Td className="text-gray-400 text-xs">{formatDate(p.paidDate, locale)}</Td>
                      <Td className="text-xs">
                        {p.paid && p.paymentMethod
                          ? <span title={p.transactionId ? `Ref: ${p.transactionId}` : ''} className="text-gray-600 font-medium">
                              {p.paymentMethod.replace(/_/g,' ')}
                            </span>
                          : <span className="text-gray-300">—</span>}
                      </Td>
                    </Tr>
                  ))
                }
              </Tbody>
            </Table>
          </Card>
        </>
      )}

      {/* Tab: Timeline */}
      {tab === 'Timeline' && (
        <Card>
          <CardHeader title="Loan Timeline" />
          <CardBody>
            <div className="relative">
              {[
                { icon: IconFileEdit, label: 'Application Submitted', date: loan.startDate,    done: true },
                { icon: IconSearch, label: 'Under Review',          date: loan.startDate,    done: loan.status !== 'PENDING' },
                { icon: IconCheckCircle, label: 'Approved',              date: loan.approvedAt,   done: !!loan.approvedAt },
                { icon: IconCoins, label: 'Disbursed',             date: loan.disbursedAt,  done: !!loan.disbursedAt },
                { icon: IconCalendar, label: 'Next Payment Due',      date: loan.nextDueDate,  done: false },
                { icon: IconFlag, label: 'Maturity Date',         date: loan.maturityDate, done: loan.status === 'PAID' },
              ].map((step, i, arr) => (
                <div key={i} className="flex gap-4 pb-6 relative">
                  <div className="flex flex-col items-center">
                    <div className={`w-9 h-9 rounded-full flex items-center justify-center text-lg border-2 z-10
                      ${step.done ? 'bg-teal-500 border-teal-500 text-white' : 'bg-white border-gray-200 text-gray-400'}`}>
                      <step.icon className="w-4 h-4" />
                    </div>
                    {i < arr.length - 1 && (
                      <div className={`w-0.5 flex-1 mt-1 ${step.done ? 'bg-teal-300' : 'bg-gray-200'}`} style={{ minHeight: 28 }} />
                    )}
                  </div>
                  <div className="pt-1.5">
                    <div className={`font-semibold text-sm ${step.done ? 'text-gray-900' : 'text-gray-400'}`}>{step.label}</div>
                    <div className="text-xs text-gray-400 mt-0.5">{formatDate(step.date, locale)}</div>
                  </div>
                </div>
              ))}
            </div>
          </CardBody>
        </Card>
      )}

      {tab === 'Comments' && (
        <Card>
          <CardHeader title="Comments & Document Requests" />
          <CardBody>
            <div className="mb-6 bg-gray-50 rounded-xl p-4">
              <Textarea
                placeholder="e.g. Please upload your land title document, or a recent utility bill as proof of address."
                value={commentText}
                onChange={e => setCommentText(e.target.value)}
                rows={3}
              />
              <div className="flex items-center justify-between mt-3">
                <label className="flex items-center gap-2 text-sm text-gray-600 cursor-pointer">
                  <input type="checkbox" checked={commentVisible} onChange={e => setCommentVisible(e.target.checked)} />
                  Visible to applicant on the tracking page
                </label>
                <Button loading={commentSaving} disabled={!commentText.trim()} onClick={handleAddComment}>
                  Post
                </Button>
              </div>
              {!commentVisible && (
                <p className="text-xs text-amber-600 mt-2">This note will be internal-only — the applicant won't see it.</p>
              )}
            </div>

            {comments.length === 0 && (
              <p className="text-sm text-gray-400 text-center py-6">No comments yet.</p>
            )}
            <div className="space-y-4">
              {comments.slice().reverse().map((c: any) => (
                <div key={c.id} className="flex gap-3">
                  <div className="w-8 h-8 rounded-full bg-teal-100 text-teal-700 flex items-center justify-center text-xs font-bold shrink-0">
                    {(c.author?.name || 'S')[0]}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-semibold text-gray-800">{c.author?.name || 'Staff'}</span>
                      <span className="text-xs text-gray-400">{formatDate(c.createdAt, locale)}</span>
                      {c.visibleToApplicant
                        ? <Pill label="Visible to applicant" color="blue" />
                        : <Pill label="Internal only" color="gray" />}
                    </div>
                    <p className="text-sm text-gray-700 mt-1">{c.message}</p>
                  </div>
                </div>
              ))}
            </div>
          </CardBody>
        </Card>
      )}

      {/* Payment Modal */}
      <Modal open={payOpen} onClose={() => setPayOpen(false)} title="Record Payment"
        footer={<>
          <Button variant="secondary" onClick={() => setPayOpen(false)}>Cancel</Button>
          <Button loading={paying} onClick={handlePay as any}>Confirm Payment</Button>
        </>}>
        <form onSubmit={handlePay}>
          <div className="bg-gray-50 rounded-xl p-4 mb-4 grid grid-cols-2 gap-3 text-sm">
            {[['Outstanding', fc(loan.outstandingBalance)], ['Next Due', formatDate(loan.nextDueDate, locale)],
              ['Penalty', fc(0)], ['Currency', loan.currency]].map(([l,v]) => (
              <div key={l}><div className="text-xs text-gray-400">{l}</div><div className="font-bold">{v}</div></div>
            ))}
          </div>
          <div className="grid grid-cols-2 gap-4">
            <FormGroup label="Amount" required>
              <Input type="number" min="1" required value={payForm.amount} onChange={e => setPayForm(f => ({...f, amount: e.target.value}))} />
            </FormGroup>
            <FormGroup label="Method" required>
              <Select value={payForm.paymentMethod} onChange={e => setPayForm(f => ({...f, paymentMethod: e.target.value}))}>
                {['BANK_TRANSFER','MOBILE_MONEY','CASH','CARD','CHEQUE','DIRECT_DEBIT'].map(m => <option key={m}>{m.replace(/_/g,' ')}</option>)}
              </Select>
            </FormGroup>
            <FormGroup label="Transaction ID">
              <Input placeholder="e.g. MPesa code" value={payForm.transactionId} onChange={e => setPayForm(f => ({...f, transactionId: e.target.value}))} />
            </FormGroup>
            <FormGroup label="Channel">
              <Input placeholder="e.g. Mobile, Branch" value={payForm.channel} onChange={e => setPayForm(f => ({...f, channel: e.target.value}))} />
            </FormGroup>
          </div>
          <FormGroup label="Notes">
            <Textarea value={payForm.notes} onChange={e => setPayForm(f => ({...f, notes: e.target.value}))} />
          </FormGroup>
        </form>
      </Modal>

      {/* Status Modal */}
      <Modal open={stOpen} onClose={() => setStOpen(false)} title="Update Loan Status"
        footer={<>
          <Button variant="secondary" onClick={() => setStOpen(false)}>Cancel</Button>
          <Button loading={stSaving} onClick={handleStatus as any}>Update</Button>
        </>}>
        <form onSubmit={handleStatus}>
          <div className="bg-gray-50 rounded-xl p-3 mb-4 text-sm flex items-center gap-2">
            Current: <StatusBadge status={loan.status} />
          </div>
          <FormGroup label="New Status" required>
            <Select value={stForm.status} onChange={e => setStForm(f => ({...f, status: e.target.value}))} required>
              <option value="">Select status…</option>
              {(() => {
                const VALID_FROM: Record<string, string[]> = {
                  PENDING:      ['UNDER_REVIEW', 'APPROVED', 'REJECTED'],
                  UNDER_REVIEW: ['APPROVED', 'REJECTED'],
                  APPROVED:     ['DISBURSED'],
                  ACTIVE:       ['DEFAULTED'],
                  OVERDUE:      ['DEFAULTED'],
                  PAID:         ['CLOSED'],
                  WRITTEN_OFF:  ['CLOSED'],
                };
                const options = VALID_FROM[loan.status] ?? [];
                if (options.length === 0) {
                  return <option disabled>No status changes available from {loan.status.replace(/_/g,' ')}</option>;
                }
                return options.map(s => <option key={s} value={s}>{s.replace(/_/g,' ')}</option>);
              })()}
            </Select>
          </FormGroup>
          {stForm.status === 'APPROVED' && (
            <FormGroup label={`Interest Rate ${loan.interestRateType === 'MONTHLY' ? '(monthly)' : '(annual)'}`}>
              <p className="text-xs text-gray-400 mb-2">
                Applied on the website at <strong>{loan.interestRate}%</strong>. Loans are flexible — adjust it here if this borrower should get a different rate; leave it as-is to keep the original.
              </p>
              <div className="flex gap-2 flex-wrap">
                {['6', '8', '10'].map(r => (
                  <button key={r} type="button"
                    onClick={() => setStForm(f => ({ ...f, interestRate: r }))}
                    className={`px-4 py-2 rounded-lg text-sm font-semibold border transition-colors ${
                      stForm.interestRate === r ? 'bg-teal-600 text-white border-teal-600' : 'bg-white text-gray-700 border-gray-200 hover:bg-gray-50'
                    }`}>
                    {r}%
                  </button>
                ))}
                <button type="button"
                  onClick={() => setStForm(f => ({ ...f, interestRate: '' }))}
                  className={`px-4 py-2 rounded-lg text-sm font-semibold border transition-colors ${
                    stForm.interestRate === '' ? 'bg-teal-600 text-white border-teal-600' : 'bg-white text-gray-700 border-gray-200 hover:bg-gray-50'
                  }`}>
                  Keep {loan.interestRate}%
                </button>
                <input type="number" step="0.1" min="0" placeholder="Custom %"
                  value={['6','8','10',''].includes(stForm.interestRate) ? '' : stForm.interestRate}
                  onChange={e => setStForm(f => ({ ...f, interestRate: e.target.value }))}
                  className="w-28 px-3 py-2 rounded-lg text-sm border border-gray-200 focus:outline-none focus:ring-2 focus:ring-teal-500" />
              </div>
            </FormGroup>
          )}
          {stForm.status === 'REJECTED' && (
            <FormGroup label="Rejection Reason" required>
              <Textarea required value={stForm.rejectionReason} onChange={e => setStForm(f => ({...f, rejectionReason: e.target.value}))} />
            </FormGroup>
          )}
          <FormGroup label="Internal Notes">
            <Textarea placeholder="For internal records only" value={stForm.internalNotes} onChange={e => setStForm(f => ({...f, internalNotes: e.target.value}))} />
          </FormGroup>
        </form>
      </Modal>
    </div>
  );
}