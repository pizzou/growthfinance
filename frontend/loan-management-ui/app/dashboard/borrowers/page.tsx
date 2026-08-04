
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

  const { currency, locale } = useAuth();

  const [borrowers, setBorrowers] = useState<Borrower[]>([]);
  const [total, setTotal] = useState(0);

  const [page, setPage] = useState(0);
  const [q, setQ] = useState('');

  const [loading, setLoading] = useState(true);

  const [addOpen, setAddOpen] = useState(false);

  const [msg, setMsg] = useState('');
  const [saving, setSaving] = useState(false);

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

  /**
   * ============================================================
   * LOAD BORROWERS
   * ============================================================
   */

  const load = useCallback(async () => {
    setLoading(true);

    try {
      const response: any =
        await borrowerApi.list(
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

  /**
   * ============================================================
   * ADD BORROWER
   * ============================================================
   */

  const handleAdd = async (
    e: React.FormEvent,
  ) => {
    e.preventDefault();

    setSaving(true);
    setMsg('');

    try {
      await borrowerApi.create({
        ...form,

        monthlyIncome:
          form.monthlyIncome
            ? Number(form.monthlyIncome)
            : undefined,

        monthlyExpenses:
          form.monthlyExpenses
            ? Number(form.monthlyExpenses)
            : undefined,

        creditScore:
          form.creditScore
            ? Number(form.creditScore)
            : undefined,
      });

      setAddOpen(false);

      setForm({
        ...blank,
      });

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

  /**
   * ============================================================
   * FORM HELPER
   * ============================================================
   */

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

  /**
   * ============================================================
   * OPEN BORROWER DETAILS
   * ============================================================
   *
   * IMPORTANT:
   *
   * Correct route:
   *
   * /dashboard/borrowers/{id}
   *
   * Example:
   *
   * /dashboard/borrowers/1
   */

  const openBorrower = (
    borrowerId: number | string,
  ) => {
    const id = Number(borrowerId);

    if (
      !Number.isFinite(id) ||
      id <= 0
    ) {
      console.error(
        'Invalid borrower ID:',
        borrowerId,
      );

      return;
    }

    router.push(
      `/dashboard/borrowers/${id}`,
    );
  };

  /**
   * ============================================================
   * SEARCH
   * ============================================================
   */

  const handleSearch = (
    value: string,
  ) => {
    setQ(value);
    setPage(0);
  };

  return (
    <div className="space-y-6">

      {/* ======================================================
          PAGE HEADER
      ====================================================== */}

      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">

        <div>
          <div className="flex items-center gap-2">
            <div className="w-2 h-7 rounded-full bg-teal-500" />

            <h1 className="text-2xl font-extrabold tracking-tight text-gray-900">
              Borrowers
            </h1>
          </div>

          <p className="mt-1 ml-4 text-sm text-gray-500">
            Manage your customers, credit profiles and repayment relationships.
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

      {/* ======================================================
          SUMMARY CARDS
      ====================================================== */}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">

        <SummaryCard
          label="Total Borrowers"
          value={formatNumber(total)}
          description="Registered customers"
          icon="👥"
        />

        <SummaryCard
          label="Current Page"
          value={formatNumber(
            borrowers.length,
          )}
          description="Borrowers displayed"
          icon="📋"
        />

        <SummaryCard
          label="Page"
          value={String(page + 1)}
          description="Current result page"
          icon="↔"
        />

        <SummaryCard
          label="Records Per Page"
          value="20"
          description="Maximum displayed"
          icon="▦"
        />

      </div>

      {/* ======================================================
          SEARCH / FILTER BAR
      ====================================================== */}

      <Card>
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">

          <div>
            <h2 className="font-bold text-gray-900">
              Borrower Directory
            </h2>

            <p className="text-xs text-gray-500 mt-1">
              Search and select a borrower to view their complete financial profile.
            </p>
          </div>

          <div className="relative w-full lg:w-96">

            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">
              🔍
            </span>

            <Input
              placeholder="Search name, email or national ID..."
              className="pl-10 w-full"
              value={q}
              onChange={(e) =>
                handleSearch(
                  e.target.value,
                )
              }
            />

            {q && (
              <button
                type="button"
                onClick={() =>
                  handleSearch('')
                }
                className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-700"
              >
                ×
              </button>
            )}

          </div>
        </div>
      </Card>

      {/* ======================================================
          BORROWER TABLE
      ====================================================== */}

      <Card className="overflow-hidden">

        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">

          <div>
            <h2 className="font-bold text-gray-900">
              All Borrowers
            </h2>

            <p className="text-xs text-gray-500 mt-0.5">
              {formatNumber(
                borrowers.length,
              )}{' '}
              records shown
            </p>
          </div>

          {q && (
            <div className="text-xs bg-gray-100 text-gray-600 px-3 py-1.5 rounded-full">
              Search: "{q}"
            </div>
          )}

        </div>

        {loading ? (
          <div className="flex flex-col items-center justify-center py-20">

            <div className="w-9 h-9 border-2 border-teal-500 border-t-transparent rounded-full animate-spin" />

            <p className="text-sm text-gray-500 mt-4">
              Loading borrowers...
            </p>

          </div>
        ) : borrowers.length === 0 ? (
          <div className="py-20 text-center">

            <div className="mx-auto w-14 h-14 rounded-full bg-gray-100 flex items-center justify-center text-2xl">
              👥
            </div>

            <h3 className="mt-4 font-bold text-gray-900">
              No borrowers found
            </h3>

            <p className="mt-1 text-sm text-gray-500">
              {q
                ? 'Try a different search term.'
                : 'There are no borrowers registered yet.'}
            </p>

            {q && (
              <button
                type="button"
                onClick={() =>
                  handleSearch('')
                }
                className="mt-4 text-sm font-semibold text-teal-600 hover:text-teal-700"
              >
                Clear search
              </button>
            )}

          </div>
        ) : (
          <div className="overflow-x-auto">

            <Table>

              <Thead>
                <tr>
                  <Th>Borrower</Th>
                  <Th>Contact</Th>
                  <Th>Identification</Th>
                  <Th>Employment</Th>
                  <Th>Income</Th>
                  <Th>Credit</Th>
                  <Th>Country</Th>
                  <Th>Registered</Th>
                </tr>
              </Thead>

              <Tbody>

                {borrowers.map(
                  (
                    borrower: Borrower,
                  ) => {

                    const initials =
                      `${borrower.firstName?.[0] ?? ''}${borrower.lastName?.[0] ?? ''}`
                        .toUpperCase();

                    const score =
                      borrower.creditScore ??
                      0;

                    return (
                      <Tr
                        key={borrower.id}
                        className="group cursor-pointer hover:bg-gray-50 transition-colors"
                        onClick={() =>
                          openBorrower(
                            borrower.id,
                          )
                        }
                      >

                        {/* BORROWER */}

                        <Td>

                          <div className="flex items-center gap-3 min-w-[190px]">

                            <div className="w-10 h-10 rounded-xl bg-teal-50 border border-teal-100 flex items-center justify-center text-sm font-bold text-teal-700 flex-shrink-0">
                              {initials ||
                                '?'}
                            </div>

                            <div className="min-w-0">

                              <div className="font-bold text-sm text-gray-900 truncate">
                                {borrower.firstName}{' '}
                                {borrower.lastName}
                              </div>

                              <div className="text-xs text-gray-400 mt-0.5">
                                Borrower #
                                {borrower.id}
                              </div>

                            </div>

                          </div>

                        </Td>

                        {/* CONTACT */}

                        <Td>

                          <div className="space-y-1 min-w-[170px]">

                            <div className="text-sm text-gray-700 truncate">
                              {borrower.email ||
                                'No email'}
                            </div>

                            <div className="text-xs text-gray-400">
                              {borrower.phone ||
                                'No phone'}
                            </div>

                          </div>

                        </Td>

                        {/* IDENTIFICATION */}

                        <Td>

                          <div className="min-w-[130px]">

                            <div className="text-xs uppercase tracking-wide text-gray-400 font-semibold">
                              National ID
                            </div>

                            <div className="mt-1">

                              {borrower.nationalId ? (
                                <code className="text-xs bg-gray-100 text-gray-700 px-2 py-1 rounded-md">
                                  {
                                    borrower.nationalId
                                  }
                                </code>
                              ) : (
                                <span className="text-sm text-gray-400">
                                  —
                                </span>
                              )}

                            </div>

                          </div>

                        </Td>

                        {/* EMPLOYMENT */}

                        <Td>

                          <div className="min-w-[150px]">

                            <div className="font-medium text-sm text-gray-800">
                              {borrower.employerName ||
                                'Not provided'}
                            </div>

                            <div className="text-xs text-gray-400 mt-1">
                              {borrower.employmentType ||
                                'Employment unknown'}
                            </div>

                          </div>

                        </Td>

                        {/* INCOME */}

                        <Td>

                          <div className="min-w-[120px]">

                            <div className="text-xs text-gray-400 uppercase tracking-wide font-semibold">
                              Monthly
                            </div>

                            <div className="font-bold text-sm text-gray-900 mt-1">
                              {formatCurrency(
                                borrower.monthlyIncome,
                                currency,
                                locale,
                              )}
                            </div>

                          </div>

                        </Td>

                        {/* CREDIT SCORE */}

                        <Td>

                          <div className="min-w-[100px]">

                            <CreditBadge
                              score={
                                borrower.creditScore
                              }
                            />

                          </div>

                        </Td>

                        {/* COUNTRY */}

                        <Td>

                          <div className="min-w-[80px]">

                            <span className="inline-flex items-center px-2.5 py-1 rounded-lg bg-gray-50 border border-gray-100 text-xs font-semibold text-gray-600">
                              {borrower.country ||
                                '—'}
                            </span>

                          </div>

                        </Td>

                        {/* CREATED */}

                        <Td>

                          <div className="min-w-[100px]">

                            <div className="text-sm text-gray-600">
                              {borrower.createdAt
                                ? formatDate(
                                    borrower.createdAt,
                                    locale,
                                  )
                                : '—'}
                            </div>

                            <div className="text-xs text-teal-600 mt-1 font-medium opacity-0 group-hover:opacity-100 transition-opacity">
                              View profile →
                            </div>

                          </div>

                        </Td>

                      </Tr>
                    );
                  },
                )}

              </Tbody>

            </Table>

          </div>
        )}

      </Card>

      {/* ======================================================
          PAGINATION
      ====================================================== */}

      {total > 20 && (
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4">

          <div className="text-sm text-gray-500">
            Showing{' '}
            <span className="font-semibold text-gray-700">
              {page * 20 + 1}
            </span>{' '}
            to{' '}
            <span className="font-semibold text-gray-700">
              {Math.min(
                (page + 1) * 20,
                total,
              )}
            </span>{' '}
            of{' '}
            <span className="font-semibold text-gray-700">
              {formatNumber(total)}
            </span>
          </div>

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

            <div className="px-4 py-2 rounded-lg bg-gray-100 text-sm font-semibold text-gray-700">
              Page {page + 1}
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

      {/* ======================================================
          ADD BORROWER MODAL
      ====================================================== */}

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

        <form
          onSubmit={handleAdd}
          className="space-y-5"
        >

          {msg && (
            <Alert type="error">
              {msg}
            </Alert>
          )}

          {/* PERSONAL */}

          <FormSection
            title="Personal Information"
            description="Basic identity and contact information."
          />

          <FormRow>

            <FormGroup
              label="First Name"
              required
            >
              <Input
                required
                value={
                  form.firstName
                }
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
                value={
                  form.lastName
                }
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
                ].map(
                  (gender) => (
                    <option
                      key={gender}
                      value={gender}
                    >
                      {gender}
                    </option>
                  ),
                )}

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
                      {
                        country.name
                      }
                    </option>
                  ),
                )}

              </Select>
            </FormGroup>

          </FormRow>

          {/* EMPLOYMENT */}

          <FormSection
            title="Employment & Finance"
            description="Income, employment and credit information."
          />

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
                ].map(
                  (type) => (
                    <option
                      key={type}
                      value={type}
                    >
                      {type}
                    </option>
                  ),
                )}

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
                      {
                        country.name
                      }
                    </option>
                  ),
                )}

              </Select>
            </FormGroup>

          </FormRow>

          {/* BANK */}

          <FormSection
            title="Bank Details"
            description="Optional banking information."
          />

          <FormRow>

            <FormGroup label="Bank Name">
              <Input
                value={
                  form.bankName
                }
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

/**
 * ============================================================
 * SUMMARY CARD
 * ============================================================
 */

function SummaryCard({
  label,
  value,
  description,
  icon,
}: {
  label: string;
  value: string;
  description: string;
  icon: string;
}) {
  return (
    <Card>
      <div className="flex items-start justify-between">

        <div>

          <div className="text-xs uppercase tracking-wider font-bold text-gray-400">
            {label}
          </div>

          <div className="text-2xl font-extrabold text-gray-900 mt-2">
            {value}
          </div>

          <div className="text-xs text-gray-500 mt-1">
            {description}
          </div>

        </div>

        <div className="w-10 h-10 rounded-xl bg-teal-50 flex items-center justify-center text-lg">
          {icon}
        </div>

      </div>
    </Card>
  );
}

/**
 * ============================================================
 * CREDIT BADGE
 * ============================================================
 */

function CreditBadge({
  score,
}: {
  score?: number | null;
}) {
  if (
    score === null ||
    score === undefined
  ) {
    return (
      <span className="inline-flex items-center px-2.5 py-1 rounded-lg bg-gray-100 text-gray-500 text-xs font-bold">
        Not rated
      </span>
    );
  }

  if (score >= 700) {
    return (
      <div>
        <span className="inline-flex items-center px-2.5 py-1 rounded-lg bg-teal-50 text-teal-700 text-xs font-extrabold">
          {score}
        </span>

        <div className="text-[10px] text-teal-600 mt-1 font-semibold">
          Excellent
        </div>
      </div>
    );
  }

  if (score >= 600) {
    return (
      <div>
        <span className="inline-flex items-center px-2.5 py-1 rounded-lg bg-yellow-50 text-yellow-700 text-xs font-extrabold">
          {score}
        </span>

        <div className="text-[10px] text-yellow-600 mt-1 font-semibold">
          Fair
        </div>
      </div>
    );
  }

  return (
    <div>
      <span className="inline-flex items-center px-2.5 py-1 rounded-lg bg-red-50 text-red-600 text-xs font-extrabold">
        {score}
      </span>

      <div className="text-[10px] text-red-500 mt-1 font-semibold">
        High Risk
      </div>
    </div>
  );
}

/**
 * ============================================================
 * FORM SECTION
 * ============================================================
 */

function FormSection({
  title,
  description,
}: {
  title: string;
  description: string;
}) {
  return (
    <div className="pt-2 pb-1 border-b border-gray-100">

      <div className="text-sm font-extrabold text-gray-900">
        {title}
      </div>

      <div className="text-xs text-gray-500 mt-1">
        {description}
      </div>

    </div>
  );
}
