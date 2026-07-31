'use client';
import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { borrowerApi } from '@/services/api';
import { Borrower } from '@/types';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Table, Thead, Th, Tbody, Tr, Td, EmptyRow } from '@/components/ui/Table';
import { Modal } from '@/components/ui/Modal';
import { FormGroup, Input, Select, FormRow, Alert } from '@/components/ui/Form';
import { formatCurrency, formatDate, formatNumber, COUNTRIES, SUPPORTED_CURRENCIES } from '@/lib/utils';
import { useAuth } from '@/hooks/useAuth';

export default function BorrowersPage() {
  const [borrowers, setBorrowers] = useState<Borrower[]>([]);
  const [total, setTotal]         = useState(0);
  const [page, setPage]           = useState(0);
  const [q, setQ]                 = useState('');
  const [loading, setLoading]     = useState(true);
  const [addOpen, setAddOpen]     = useState(false);
  const [msg, setMsg]             = useState('');
  const [saving, setSaving]       = useState(false);
  const { currency, locale }      = useAuth();

  const blank = { firstName:'', lastName:'', email:'', phone:'', nationalId:'',
    dateOfBirth:'', gender:'', nationality:'KE', employerName:'', employmentType:'PERMANENT',
    jobTitle:'', monthlyIncome:'', monthlyExpenses:'', creditScore:'', addressLine1:'',
    city:'', country:'KE', bankName:'', bankAccountNumber:'' };
  const [form, setForm] = useState<Record<string,string>>(blank);

  const load = useCallback(() => {
    setLoading(true);
    borrowerApi.list(page, 20, q)
      .then((r: any) => { setBorrowers(r.content ?? r); setTotal(r.totalElements ?? (r.content ?? r).length); })
      .catch(console.error).finally(() => setLoading(false));
  }, [page, q]);

  useEffect(() => { load(); }, [load]);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault(); setSaving(true); setMsg('');
    try {
      await borrowerApi.create({ ...form, monthlyIncome: Number(form.monthlyIncome), monthlyExpenses: Number(form.monthlyExpenses), creditScore: Number(form.creditScore) });
      setAddOpen(false); setForm(blank); load();
    } catch (err: any) { setMsg(err.message); }
    setSaving(false);
  };

  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement|HTMLSelectElement>) =>
    setForm(f => ({...f, [k]: e.target.value}));

  return (
    <div>
      <div className="flex items-start justify-between mb-6">
        <div>
          <h1 className="text-2xl font-extrabold text-gray-900">Borrowers</h1>
          <p className="text-sm text-gray-500 mt-0.5">{formatNumber(total)} registered clients</p>
        </div>
        <Button icon="+" onClick={() => setAddOpen(true)}>Add Borrower</Button>
      </div>

      <div className="flex gap-3 mb-4">
        <div className="relative">
          <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm">🔍</span>
          <Input placeholder="Search name, email, ID…" className="pl-9 w-64"
            value={q} onChange={e => { setQ(e.target.value); setPage(0); }} />
        </div>
      </div>

      <Card>
        {loading ? (
          <div className="flex items-center justify-center py-16"><div className="w-8 h-8 border-2 border-teal-500 border-t-transparent rounded-full animate-spin" /></div>
        ) : (
          <Table>
            <Thead>
              <tr><Th>Name</Th><Th>Email</Th><Th>Phone</Th><Th>National ID</Th><Th>Employer</Th><Th>Income</Th><Th>Credit Score</Th><Th>Country</Th><Th>Since</Th></tr>
            </Thead>
            <Tbody>
              {borrowers.length === 0 ? <EmptyRow cols={9} message="No borrowers found" /> :
                borrowers.map((b: Borrower) => (
                  <Tr key={b.id}>
                    <Td>
                      <div className="flex items-center gap-2">
                        <div className="w-8 h-8 bg-teal-100 rounded-full flex items-center justify-center text-sm font-bold text-teal-700 flex-shrink-0">
                          {b.firstName?.[0]}{b.lastName?.[0]}
                        </div>
                        <div>
                          <div className="font-semibold text-sm text-gray-900">{b.firstName} {b.lastName}</div>
                          <div className="text-xs text-gray-400">{b.employmentType}</div>
                        </div>
                      </div>
                    </Td>
                    <Td className="text-sm text-gray-600">{b.email ?? '—'}</Td>
                    <Td className="text-sm text-gray-600">{b.phone ?? '—'}</Td>
                    <Td><code className="text-xs bg-gray-100 px-2 py-0.5 rounded">{b.nationalId ?? '—'}</code></Td>
                    <Td className="text-sm text-gray-600">{b.employerName ?? '—'}</Td>
                    <Td className="font-semibold text-sm">{formatCurrency(b.monthlyIncome, currency, locale)}</Td>
                    <Td>
                      <span className={`font-bold text-sm ${(b.creditScore ?? 0) >= 700 ? 'text-teal-600' : (b.creditScore ?? 0) >= 600 ? 'text-yellow-600' : 'text-red-500'}`}>
                        {b.creditScore ?? '—'}
                      </span>
                    </Td>
                    <Td className="text-xs text-gray-500">{b.country ?? '—'}</Td>
                    <Td className="text-xs text-gray-400">{formatDate(b.createdAt, locale)}</Td>
                  </Tr>
                ))
              }
            </Tbody>
          </Table>
        )}
      </Card>

      {/* Add Borrower Modal */}
      <Modal open={addOpen} onClose={() => setAddOpen(false)} title="Add New Borrower" size="lg"
        footer={<>
          <Button variant="secondary" onClick={() => setAddOpen(false)}>Cancel</Button>
          <Button loading={saving} onClick={handleAdd as any}>Save Borrower</Button>
        </>}>
        <form onSubmit={handleAdd}>
          {msg && <Alert type="error">{msg}</Alert>}
          <div className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-3">Personal Information</div>
          <FormRow>
            <FormGroup label="First Name" required><Input required value={form.firstName} onChange={set('firstName')} /></FormGroup>
            <FormGroup label="Last Name" required><Input required value={form.lastName} onChange={set('lastName')} /></FormGroup>
          </FormRow>
          <FormRow>
            <FormGroup label="Email"><Input type="email" value={form.email} onChange={set('email')} /></FormGroup>
            <FormGroup label="Phone"><Input value={form.phone} onChange={set('phone')} /></FormGroup>
          </FormRow>
          <FormRow>
            <FormGroup label="National ID"><Input value={form.nationalId} onChange={set('nationalId')} /></FormGroup>
            <FormGroup label="Date of Birth"><Input type="date" value={form.dateOfBirth} onChange={set('dateOfBirth')} /></FormGroup>
          </FormRow>
          <FormRow>
            <FormGroup label="Gender">
              <Select value={form.gender} onChange={set('gender')}>
                <option value="">Select…</option>
                {['Male','Female','Other','Prefer not to say'].map(g => <option key={g}>{g}</option>)}
              </Select>
            </FormGroup>
            <FormGroup label="Nationality">
              <Select value={form.nationality} onChange={set('nationality')}>
                {COUNTRIES.map(c => <option key={c.code} value={c.code}>{c.name}</option>)}
              </Select>
            </FormGroup>
          </FormRow>

          <div className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-3 mt-4">Employment & Finance</div>
          <FormRow>
            <FormGroup label="Employer Name"><Input value={form.employerName} onChange={set('employerName')} /></FormGroup>
            <FormGroup label="Employment Type">
              <Select value={form.employmentType} onChange={set('employmentType')}>
                {['PERMANENT','CONTRACT','SELF_EMPLOYED','UNEMPLOYED'].map(t => <option key={t}>{t}</option>)}
              </Select>
            </FormGroup>
          </FormRow>
          <FormRow>
            <FormGroup label="Monthly Income"><Input type="number" value={form.monthlyIncome} onChange={set('monthlyIncome')} /></FormGroup>
            <FormGroup label="Monthly Expenses"><Input type="number" value={form.monthlyExpenses} onChange={set('monthlyExpenses')} /></FormGroup>
          </FormRow>
          <FormRow>
            <FormGroup label="Credit Score"><Input type="number" min="300" max="850" value={form.creditScore} onChange={set('creditScore')} /></FormGroup>
            <FormGroup label="Country">
              <Select value={form.country} onChange={set('country')}>
                {COUNTRIES.map(c => <option key={c.code} value={c.code}>{c.name}</option>)}
              </Select>
            </FormGroup>
          </FormRow>

          <div className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-3 mt-4">Bank Details</div>
          <FormRow>
            <FormGroup label="Bank Name"><Input value={form.bankName} onChange={set('bankName')} /></FormGroup>
            <FormGroup label="Account Number"><Input value={form.bankAccountNumber} onChange={set('bankAccountNumber')} /></FormGroup>
          </FormRow>
        </form>
      </Modal>
    </div>
  );
}
