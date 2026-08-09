
'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';

import { borrowerApi } from '@/services/api';
import { Borrower } from '@/types';

import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';

import {
  Table,
  Thead,
  Th,
  Tbody,
  Tr,
  Td,
  EmptyRow,
} from '@/components/ui/Table';

import { Modal } from '@/components/ui/Modal';

import {
  FormGroup,
  Input,
  Select,
  FormRow,
  Alert,
} from '@/components/ui/Form';

import {
  formatCurrency,
  formatDate,
  formatNumber,
  COUNTRIES,
} from '@/lib/utils';

import { useAuth } from '@/hooks/useAuth';

export default function BorrowersPage() {
  const router = useRouter();

  const [borrowers, setBorrowers] = useState<Borrower[]>([]);
  const [total, setTotal] = useState(0);

  const [page, setPage] = useState(0);
  const [q, setQ] = useState('');

  const [loading, setLoading] = useState(true);
  const [addOpen, setAddOpen] = useState(false);

  const [msg, setMsg] = useState('');
  const [saving, setSaving] = useState(false);

  const { currency, locale } = useAuth();

  const blank = {
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    nationalId: '',
    dateOfBirth: '',
    gender: '',
    nationality: 'RW',
    employerName: '',
    employmentType: 'PERMANENT',
    jobTitle: '',
    monthlyIncome: '',
    monthlyExpenses: '',
    creditScore: '',
    addressLine1: '',
    city: '',
    country: 'RW',
    bankName: '',
    bankAccountNumber: '',
  };

  const [form, setForm] =
    useState<Record<string, string>>(blank);

  const load = useCallback(async () => {
    setLoading(true);

    try {
      const response: any = await borrowerApi.list(
        page,
        20,
        q,
      );

      const content = Array.isArray(response)
        ? response
        : response?.content ?? [];

      setBorrowers(content);

      setTotal(
        response?.totalElements ??
          response?.total ??
          content.length,
      );
    } catch (error) {
      console.error(
        'Failed to load borrowers:',
        error,
      );

      setBorrowers([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [page, q]);

  useEffect(() => {
    load();
  }, [load]);

  const handleAdd = async (
    e: React.FormEvent,
  ) => {
    e.preventDefault();

    setSaving(true);
    setMsg('');

    try {
      await borrowerApi.create({
        ...form,

        monthlyIncome: form.monthlyIncome
          ? Number(form.monthlyIncome)
          : undefined,

        monthlyExpenses: form.monthlyExpenses
          ? Number(form.monthlyExpenses)
          : undefined,

        creditScore: form.creditScore
          ? Number(form.creditScore)
          : undefined,
      });

      setAddOpen(false);
      setForm({ ...blank });

      await load();
    } catch (error: any) {
      console.error(
        'Failed to create borrower:',
        error,
      );

      setMsg(
        error?.message ||
          'Failed to create borrower',
      );
    } finally {
      setSaving(false);
    }
  };

  const set =
    (key: string) =>
    (
      e: React.ChangeEvent<
        HTMLInputElement | HTMLSelectElement
      >,
    ) => {
      setForm((current) => ({
        ...current,
        [key]: e.target.value,
      }));
    };

  const openBorrower = (
    borrowerId: number | string,
  ) => {
    const id = Number(borrowerId);

    if (!Number.isFinite(id) || id <= 0) {
      return;
    }

    router.push(
      `/dashboard/borrowers/${id}`,
    );
  };

  const getInitials = (
    borrower: Borrower,
  ) => {
    return (
      `${borrower.firstName?.[0] ?? ''}${borrower.lastName?.[0] ?? ''}`
    ).toUpperCase();
  };

  const getCreditStyle = (
    score?: number | null,
  ) => {
    const value = score ?? 0;

    if (value >= 700) {
      return {
        text: 'text-emerald-700',
        bg: 'bg-emerald-50',
        border: 'border-emerald-200',
        label: 'Strong',
      };
    }

    if (value >= 600) {
      return {
        text: 'text-amber-700',
        bg: 'bg-amber-50',
        border: 'border-amber-200',
        label: 'Fair',
      };
    }

    return {
      text: 'text-rose-700',
      bg: 'bg-rose-50',
      border: 'border-rose-200',
      label: 'High Risk',
    };
  };

  return (
    <div className="min-h-full bg-slate-50/60 -m-6 p-6 md:p-8">
      <div className="max-w-[1600px] mx-auto space-y-6">

        {/* =====================================================
            HEADER
        ===================================================== */}

        <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">

          <div>
            <div className="flex items-center gap-2 mb-2">
              <span className="h-2 w-2 rounded-full bg-emerald-500" />

              <span className="text-[11px] font-bold uppercase tracking-[0.18em] text-slate-400">
                Client Management
              </span>
            </div>

            <h1 className="text-[30px] leading-tight font-bold tracking-[-0.03em] text-slate-950">
              Borrowers
            </h1>

            <p className="mt-1.5 text-sm text-slate-500">
              Manage your borrowers, financial profiles,
              and repayment relationships.
            </p>
          </div>

          <Button
            icon="+"
            onClick={() => {
              setMsg('');
              setAddOpen(true);
            }}
          >
            Add Borrower
          </Button>
        </div>

        {/* =====================================================
            SUMMARY STRIP
        ===================================================== */}

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">

          <SummaryCard
            label="Total borrowers"
            value={formatNumber(total)}
            description="Registered clients"
            icon="users"
          />

          <SummaryCard
            label="Current page"
            value={formatNumber(borrowers.length)}
            description="Borrowers displayed"
            icon="list"
          />

          <SummaryCard
            label="Portfolio view"
            value="Active"
            description="Client management"
            icon="activity"
          />

        </div>

        {/* =====================================================
            SEARCH / FILTER BAR
        ===================================================== */}

        <Card>
          <div className="p-1">

            <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">

              <div className="relative w-full lg:max-w-md">

                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-4">
                  <svg
                    className="h-4 w-4 text-slate-400"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                  >
                    <circle
                      cx="11"
                      cy="11"
                      r="7"
                    />
                    <path d="m20 20-4-4" />
                  </svg>
                </div>

                <Input
                  placeholder="Search by name, email or national ID..."
                  className="!h-11 !rounded-xl !border-slate-200 !bg-slate-50 pl-11 pr-10 text-sm focus:!border-emerald-400 focus:!ring-2 focus:!ring-emerald-100"
                  value={q}
                  onChange={(e) => {
                    setQ(e.target.value);
                    setPage(0);
                  }}
                />

                {q && (
                  <button
                    type="button"
                    onClick={() => {
                      setQ('');
                      setPage(0);
                    }}
                    className="absolute inset-y-0 right-0 flex items-center pr-4 text-slate-400 transition hover:text-slate-700"
                  >
                    ×
                  </button>
                )}
              </div>

              <div className="flex items-center gap-3 text-xs text-slate-500">
                <span className="inline-flex h-2 w-2 rounded-full bg-emerald-500" />
                {formatNumber(total)} borrowers
              </div>

            </div>
          </div>
        </Card>

        {/* =====================================================
            TABLE
        ===================================================== */}

        <Card>
          <div className="overflow-hidden">

            <div className="flex items-center justify-between border-b border-slate-100 px-6 py-5">
              <div>
                <h2 className="text-base font-bold text-slate-900">
                  Borrower Directory
                </h2>

                <p className="mt-0.5 text-xs text-slate-400">
                  Select a borrower to view their complete
                  financial profile.
                </p>
              </div>

              <div className="hidden sm:flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                <span className="text-[11px] font-semibold text-slate-500">
                  Live data
                </span>
              </div>
            </div>

            {loading ? (
              <div className="flex min-h-[420px] flex-col items-center justify-center">

                <div className="relative mb-5">
                  <div className="h-10 w-10 rounded-full border-[3px] border-slate-200" />

                  <div className="absolute inset-0 h-10 w-10 animate-spin rounded-full border-[3px] border-transparent border-t-emerald-500" />
                </div>

                <p className="text-sm font-semibold text-slate-600">
                  Loading borrowers
                </p>

                <p className="mt-1 text-xs text-slate-400">
                  Retrieving client information...
                </p>
              </div>
            ) : (
              <div className="overflow-x-auto">

                <Table>
                  <Thead>
                    <tr className="bg-slate-50/80">
                      <Th>Name</Th>
                      <Th>Email</Th>
                      <Th>Phone</Th>
                      <Th>National ID</Th>
                      <Th>Employer</Th>
                      <Th>Monthly Income</Th>
                      <Th>Credit</Th>
                      <Th>Country</Th>
                      <Th>Registered</Th>
                    </tr>
                  </Thead>

                  <Tbody>
                    {borrowers.length === 0 ? (
                      <EmptyRow
                        cols={9}
                        message={
                          q
                            ? 'No borrowers match your search.'
                            : 'No borrowers found.'
                        }
                      />
                    ) : (
                      borrowers.map(
                        (
                          borrower: Borrower,
                        ) => {
                          const credit =
                            getCreditStyle(
                              borrower.creditScore,
                            );

                          return (
                            <Tr
                              key={
                                borrower.id
                              }
                              className="group cursor-pointer border-b border-slate-100 transition-colors hover:bg-emerald-50/30"
                              onClick={() =>
                                openBorrower(
                                  borrower.id,
                                )
                              }
                            >

                              {/* NAME */}

                              <Td>
                                <div className="flex min-w-[220px] items-center gap-3">

                                  <div className="relative flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-emerald-100 to-teal-50 text-xs font-bold text-emerald-700 ring-1 ring-emerald-100">
                                    {getInitials(
                                      borrower,
                                    )}

                                    <span className="absolute -bottom-0.5 -right-0.5 h-2.5 w-2.5 rounded-full border-2 border-white bg-emerald-500" />
                                  </div>

                                  <div className="min-w-0">
                                    <div className="truncate text-sm font-bold text-slate-900 group-hover:text-emerald-700">
                                      {
                                        borrower.firstName
                                      }{' '}
                                      {
                                        borrower.lastName
                                      }
                                    </div>

                                    <div className="mt-0.5 text-[11px] font-medium uppercase tracking-wide text-slate-400">
                                      {
                                        borrower.employmentType ??
                                        'Borrower'
                                      }
                                    </div>
                                  </div>

                                </div>
                              </Td>

                              {/* EMAIL */}

                              <Td>
                                <span className="text-sm text-slate-600">
                                  {borrower.email ||
                                    '—'}
                                </span>
                              </Td>

                              {/* PHONE */}

                              <Td>
                                <span className="whitespace-nowrap text-sm text-slate-600">
                                  {borrower.phone ||
                                    '—'}
                                </span>
                              </Td>

                              {/* NATIONAL ID */}

                              <Td>
                                {borrower.nationalId ? (
                                  <span className="inline-flex rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1 font-mono text-[11px] font-medium text-slate-600">
                                    {
                                      borrower.nationalId
                                    }
                                  </span>
                                ) : (
                                  <span className="text-sm text-slate-300">
                                    —
                                  </span>
                                )}
                              </Td>

                              {/* EMPLOYER */}

                              <Td>
                                <span className="text-sm text-slate-600">
                                  {borrower.employerName ||
                                    '—'}
                                </span>
                              </Td>

                              {/* INCOME */}

                              <Td>
                                <span className="whitespace-nowrap text-sm font-bold text-slate-800">
                                  {formatCurrency(
                                    borrower.monthlyIncome,
                                    currency,
                                    locale,
                                  )}
                                </span>
                              </Td>

                              {/* CREDIT */}

                              <Td>
                                <div className="flex items-center gap-2">

                                  <span
                                    className={`inline-flex min-w-[48px] justify-center rounded-lg border px-2 py-1 text-xs font-bold ${credit.bg} ${credit.text} ${credit.border}`}
                                  >
                                    {borrower.creditScore ??
                                      '—'}
                                  </span>

                                  {borrower.creditScore !=
                                    null && (
                                    <span
                                      className={`hidden xl:inline text-[10px] font-bold uppercase tracking-wide ${credit.text}`}
                                    >
                                      {
                                        credit.label
                                      }
                                    </span>
                                  )}

                                </div>
                              </Td>

                              {/* COUNTRY */}

                              <Td>
                                <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                                  {borrower.country ||
                                    '—'}
                                </span>
                              </Td>

                              {/* DATE */}

                              <Td>
                                <span className="whitespace-nowrap text-xs font-medium text-slate-400">
                                  {formatDate(
                                    borrower.createdAt,
                                    locale,
                                  )}
                                </span>
                              </Td>

                            </Tr>
                          );
                        },
                      )
                    )}
                  </Tbody>
                </Table>

              </div>
            )}
          </div>
        </Card>

        {/* =====================================================
            PAGINATION
        ===================================================== */}

        {total > 20 && (
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">

            <p className="text-xs font-medium text-slate-400">
              Showing{' '}
              <span className="font-bold text-slate-600">
                {borrowers.length}
              </span>{' '}
              of{' '}
              <span className="font-bold text-slate-600">
                {formatNumber(total)}
              </span>{' '}
              borrowers
            </p>

            <div className="flex items-center gap-2">

              <Button
                variant="secondary"
                disabled={page === 0}
                onClick={() =>
                  setPage(
                    Math.max(
                      0,
                      page - 1,
                    ),
                  )
                }
              >
                ← Previous
              </Button>

              <div className="flex h-9 min-w-9 items-center justify-center rounded-lg border border-slate-200 bg-white px-3 text-xs font-bold text-slate-700">
                {page + 1}
              </div>

              <Button
                variant="secondary"
                disabled={
                  (page + 1) * 20 >=
                  total
                }
                onClick={() =>
                  setPage(page + 1)
                }
              >
                Next →
              </Button>

            </div>
          </div>
        )}

      </div>

      {/* =====================================================
          ADD BORROWER MODAL
      ===================================================== */}

      <Modal
        open={addOpen}
        onClose={() =>
          setAddOpen(false)
        }
        title="Add New Borrower"
        size="lg"
        footer={
          <>
            <Button
              variant="secondary"
              onClick={() =>
                setAddOpen(false)
              }
            >
              Cancel
            </Button>

            <Button
              loading={saving}
              onClick={
                handleAdd as any
              }
            >
              Save Borrower
            </Button>
          </>
        }
      >
        <form onSubmit={handleAdd}>
          {msg && (
            <Alert type="error">
              {msg}
            </Alert>
          )}

          <div className="mb-5">
            <div className="mb-1 text-xs font-bold uppercase tracking-[0.14em] text-slate-400">
              Personal Information
            </div>

            <div className="text-xs text-slate-400">
              Basic identity and contact information.
            </div>
          </div>

          <FormRow>
            <FormGroup
              label="First Name"
              required
            >
              <Input
                required
                value={form.firstName}
                onChange={set(
                  'firstName',
                )}
              />
            </FormGroup>

            <FormGroup
              label="Last Name"
              required
            >
              <Input
                required
                value={form.lastName}
                onChange={set(
                  'lastName',
                )}
              />
            </FormGroup>
          </FormRow>

          <FormRow>
            <FormGroup label="Email">
              <Input
                type="email"
                value={form.email}
                onChange={set(
                  'email',
                )}
              />
            </FormGroup>

            <FormGroup label="Phone">
              <Input
                value={form.phone}
                onChange={set(
                  'phone',
                )}
              />
            </FormGroup>
          </FormRow>

          <FormRow>
            <FormGroup label="National ID">
              <Input
                value={
                  form.nationalId
                }
                onChange={set(
                  'nationalId',
                )}
              />
            </FormGroup>

            <FormGroup label="Date of Birth">
              <Input
                type="date"
                value={
                  form.dateOfBirth
                }
                onChange={set(
                  'dateOfBirth',
                )}
              />
            </FormGroup>
          </FormRow>

          <FormRow>
            <FormGroup label="Gender">
              <Select
                value={form.gender}
                onChange={set(
                  'gender',
                )}
              >
                <option value="">
                  Select…
                </option>

                {[
                  'Male',
                  'Female',
                  'Other',
                  'Prefer not to say',
                ].map((gender) => (
                  <option
                    key={gender}
                    value={gender}
                  >
                    {gender}
                  </option>
                ))}
              </Select>
            </FormGroup>

            <FormGroup label="Nationality">
              <Select
                value={
                  form.nationality
                }
                onChange={set(
                  'nationality',
                )}
              >
                {COUNTRIES.map(
                  (country) => (
                    <option
                      key={
                        country.code
                      }
                      value={
                        country.code
                      }
                    >
                      {country.name}
                    </option>
                  ),
                )}
              </Select>
            </FormGroup>
          </FormRow>

          <div className="mb-5 mt-7">
            <div className="mb-1 text-xs font-bold uppercase tracking-[0.14em] text-slate-400">
              Employment & Finance
            </div>

            <div className="text-xs text-slate-400">
              Income, employment and credit information.
            </div>
          </div>

          <FormRow>
            <FormGroup label="Employer Name">
              <Input
                value={
                  form.employerName
                }
                onChange={set(
                  'employerName',
                )}
              />
            </FormGroup>

            <FormGroup label="Employment Type">
              <Select
                value={
                  form.employmentType
                }
                onChange={set(
                  'employmentType',
                )}
              >
                {[
                  'PERMANENT',
                  'CONTRACT',
                  'SELF_EMPLOYED',
                  'UNEMPLOYED',
                ].map((type) => (
                  <option
                    key={type}
                    value={type}
                  >
                    {type}
                  </option>
                ))}
              </Select>
            </FormGroup>
          </FormRow>

          <FormRow>
            <FormGroup label="Monthly Income">
              <Input
                type="number"
                min="0"
                value={
                  form.monthlyIncome
                }
                onChange={set(
                  'monthlyIncome',
                )}
              />
            </FormGroup>

            <FormGroup label="Monthly Expenses">
              <Input
                type="number"
                min="0"
                value={
                  form.monthlyExpenses
                }
                onChange={set(
                  'monthlyExpenses',
                )}
              />
            </FormGroup>
          </FormRow>

          <FormRow>
            <FormGroup label="Credit Score">
              <Input
                type="number"
                min="300"
                max="850"
                value={
                  form.creditScore
                }
                onChange={set(
                  'creditScore',
                )}
              />
            </FormGroup>

            <FormGroup label="Country">
              <Select
                value={form.country}
                onChange={set(
                  'country',
                )}
              >
                {COUNTRIES.map(
                  (country) => (
                    <option
                      key={
                        country.code
                      }
                      value={
                        country.code
                      }
                    >
                      {country.name}
                    </option>
                  ),
                )}
              </Select>
            </FormGroup>
          </FormRow>

          <div className="mb-5 mt-7">
            <div className="mb-1 text-xs font-bold uppercase tracking-[0.14em] text-slate-400">
              Banking Information
            </div>

            <div className="text-xs text-slate-400">
              Optional banking details for the borrower.
            </div>
          </div>

          <FormRow>
            <FormGroup label="Bank Name">
              <Input
                value={form.bankName}
                onChange={set(
                  'bankName',
                )}
              />
            </FormGroup>

            <FormGroup label="Account Number">
              <Input
                value={
                  form.bankAccountNumber
                }
                onChange={set(
                  'bankAccountNumber',
                )}
              />
            </FormGroup>
          </FormRow>
        </form>
      </Modal>
    </div>
  );
}

/* ============================================================
   SUMMARY CARD
============================================================ */

function SummaryCard({
  label,
  value,
  description,
  icon,
}: {
  label: string;
  value: string;
  description: string;
  icon: 'users' | 'list' | 'activity';
}) {
  return (
    <div className="group rounded-2xl border border-slate-200/80 bg-white p-5 shadow-[0_2px_12px_rgba(15,23,42,0.03)] transition-all duration-200 hover:-translate-y-0.5 hover:border-emerald-200 hover:shadow-[0_8px_30px_rgba(15,23,42,0.06)]">

      <div className="flex items-start justify-between">

        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.14em] text-slate-400">
            {label}
          </p>

          <p className="mt-2 text-2xl font-extrabold tracking-[-0.03em] text-slate-950">
            {value}
          </p>

          <p className="mt-1 text-xs font-medium text-slate-400">
            {description}
          </p>
        </div>

        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600">

          {icon === 'users' && (
            <svg
              className="h-5 w-5"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
            >
              <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
              <circle
                cx="9"
                cy="7"
                r="4"
              />
              <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
              <path d="M16 3.13a4 4 0 0 1 0 7.75" />
            </svg>
          )}

          {icon === 'list' && (
            <svg
              className="h-5 w-5"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
            >
              <path d="M8 6h13" />
              <path d="M8 12h13" />
              <path d="M8 18h13" />
              <path d="M3 6h.01" />
              <path d="M3 12h.01" />
              <path d="M3 18h.01" />
            </svg>
          )}

          {icon === 'activity' && (
            <svg
              className="h-5 w-5"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
            >
              <path d="M3 12h4l3-8 4 16 3-8h4" />
            </svg>
          )}

        </div>

      </div>
    </div>
  );
}
