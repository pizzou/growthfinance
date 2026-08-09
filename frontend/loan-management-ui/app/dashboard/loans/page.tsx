'use client';
import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { loanApi } from '@/services/api';
import { Loan, LoanStatus } from '@/types';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { StatusBadge, RiskBadge } from '@/components/ui/Badge';
import { Table, Thead, Th, Tbody, Tr, Td, EmptyRow } from '@/components/ui/Table';
import { Select, Input } from '@/components/ui/Form';
import { formatCurrency, formatDate, formatNumber, LOAN_TYPE_META } from '@/lib/utils';
import { useAuth } from '@/hooks/useAuth';

const STATUSES: LoanStatus[] = ['PENDING','UNDER_REVIEW','APPROVED','ACTIVE','OVERDUE','DEFAULTED','PAID','CLOSED','REJECTED'];
const TYPES = ['PERSONAL','MORTGAGE','AUTO','BUSINESS','STUDENT','EMERGENCY','ASSET_FINANCE','SALARY_ADVANCE','MICROFINANCE','AGRICULTURAL','TRADE_FINANCE','GROUP'];

export default function LoansPage() {
  const [loans, setLoans]       = useState<Loan[]>([]);
  const [total, setTotal]       = useState(0);
  const [page, setPage]         = useState(0);
  const [loading, setLoading]   = useState(true);
  const [status, setStatus]     = useState('');
  const [type, setType]         = useState('');
  const [actionId, setActionId] = useState<number | null>(null);
  const { currency, locale, isOfficer } = useAuth();
  const router = useRouter();
  const fc = (n?: number) => formatCurrency(n, currency, locale);

  const load = useCallback(() => {
    setLoading(true);
    loanApi.list(page, 20, status, type)
      .then((r: any) => { setLoans(r.content ?? []); setTotal(r.totalElements ?? 0); })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [page, status, type]);

  useEffect(() => { load(); }, [load]);

  const quickAction = async (e: React.MouseEvent, loanId: number, action: string) => {
    e.stopPropagation();
    setActionId(loanId);
    try {
      if (action === 'approve')  await loanApi.approve(loanId);
      if (action === 'disburse') await loanApi.disburse(loanId, 'BANK_TRANSFER');
      load();
    } catch (err: any) { alert(err.message); }
    setActionId(null);
  };

  const totalPages = Math.ceil(total / 20);

  return (
    <div>
      <div className="flex items-start justify-between mb-6">
        <div>
          <h1 className="text-2xl font-extrabold text-gray-900">Loan Portfolio</h1>
          <p className="text-sm text-gray-500 mt-0.5">{formatNumber(total)} loans total</p>
        </div>
        {isOfficer && <Button icon="+" onClick={() => router.push('/dashboard/loans/new')}>New Loan</Button>}
      </div>

      {/* Filters */}
      <div className="flex gap-3 mb-4 flex-wrap items-center">
        <div className="relative">
          <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm">🔍</span>
          <Input placeholder="Search loans…" className="pl-9 w-52" />
        </div>
        <Select value={status} onChange={e => { setStatus(e.target.value); setPage(0); }} className="w-40">
          <option value="">All Statuses</option>
          {STATUSES.map(s => <option key={s} value={s}>{s.replace(/_/g,' ')}</option>)}
        </Select>
        <Select value={type} onChange={e => { setType(e.target.value); setPage(0); }} className="w-44">
          <option value="">All Types</option>
          {TYPES.map(t => <option key={t} value={t}>{LOAN_TYPE_META[t]?.label ?? t}</option>)}
        </Select>
        {(status || type) && (
          <Button variant="ghost" size="sm" onClick={() => { setStatus(''); setType(''); setPage(0); }}>✕ Clear</Button>
        )}
      </div>

      <Card>
        {loading ? (
          <div className="flex items-center justify-center py-16"><div className="w-8 h-8 border-2 border-teal-500 border-t-transparent rounded-full animate-spin" /></div>
        ) : (
          <Table>
            <Thead>
              <tr>
                <Th>Reference</Th><Th>Borrower</Th><Th>Type</Th><Th>Amount</Th>
                <Th>Rate</Th><Th>Term</Th><Th>Progress</Th><Th>Risk</Th>
                <Th>Status</Th><Th>Officer</Th><Th>Date</Th>
                {isOfficer && <Th>Actions</Th>}
              </tr>
            </Thead>
            <Tbody>
              {loans.length === 0 ? <EmptyRow cols={12} message="No loans match your filters" /> :
                loans.map(loan => {
                  const prog = loan.totalRepayable && loan.totalPaid
                    ? Math.min(100, Math.round((loan.totalPaid / loan.totalRepayable) * 100)) : 0;
                  return (
                    <Tr key={loan.id} onClick={() => router.push(`/dashboard/loans/${loan.id}`)}>
                      <Td><code className="text-xs bg-gray-100 px-2 py-0.5 rounded font-mono">{loan.referenceNumber}</code></Td>
                      <Td>
                        <div className="font-semibold text-sm text-gray-900">{loan.borrower?.firstName} {loan.borrower?.lastName}</div>
                        <div className="text-xs text-gray-400">Score: {loan.creditScoreSnapshot ?? '—'}</div>
                      </Td>
                      <Td className="text-sm">{LOAN_TYPE_META[loan.loanType]?.icon} {LOAN_TYPE_META[loan.loanType]?.label ?? loan.loanType}</Td>
                      <Td className="font-bold text-gray-900">{fc(loan.amount)}</Td>
                      <Td className="text-gray-500">{loan.interestRate}%</Td>
                      <Td className="text-gray-500">{loan.durationMonths}mo</Td>
                      <Td className="min-w-[100px]">
                        <div className="flex items-center gap-2">
                          <div className="flex-1 bg-gray-100 rounded-full h-1.5 overflow-hidden">
                            <div className="h-1.5 rounded-full transition-all"
                              style={{ width: `${prog}%`, background: prog >= 100 ? '#0D9488' : prog > 50 ? '#3B82F6' : '#F59E0B' }} />
                          </div>
                          <span className="text-xs text-gray-400 w-8 text-right">{prog}%</span>
                        </div>
                      </Td>
                      <Td>{loan.riskCategory ? <RiskBadge category={loan.riskCategory} score={loan.riskScore} /> : <span className="text-gray-300">—</span>}</Td>
                      <Td><StatusBadge status={loan.status} /></Td>
                      <Td className="text-xs text-gray-400">{loan.loanOfficer?.name ?? '—'}</Td>
                      <Td className="text-xs text-gray-400">{formatDate(loan.startDate, locale)}</Td>
                      {isOfficer && (
                        <Td onClick={e => e.stopPropagation()}>
                          <div className="flex gap-1.5">
                            {loan.status === 'PENDING' && (
                              <Button size="xs" loading={actionId === loan.id} onClick={e => quickAction(e, loan.id, 'approve')}>Approve</Button>
                            )}
                            {loan.status === 'APPROVED' && (
                              <Button size="xs" variant="secondary" loading={actionId === loan.id} onClick={e => quickAction(e, loan.id, 'disburse')}>Disburse</Button>
                            )}
                            <Button size="xs" variant="ghost" onClick={e => { e.stopPropagation(); router.push(`/dashboard/loans/${loan.id}`); }}>→</Button>
                          </div>
                        </Td>
                      )}
                    </Tr>
                  );
                })
              }
            </Tbody>
          </Table>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between px-5 py-3 border-t border-gray-100 bg-gray-50 rounded-b-xl">
            <span className="text-xs text-gray-500">Showing {page * 20 + 1}–{Math.min((page + 1) * 20, total)} of {formatNumber(total)}</span>
            <div className="flex gap-2">
              <Button variant="secondary" size="xs" disabled={page === 0} onClick={() => setPage(p => p - 1)}>← Prev</Button>
              {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => (
                <Button key={i} size="xs" variant={i === page ? 'primary' : 'secondary'} onClick={() => setPage(i)}>{i + 1}</Button>
              ))}
              <Button variant="secondary" size="xs" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>Next →</Button>
            </div>
          </div>
        )}
      </Card>
    </div>
  );
}