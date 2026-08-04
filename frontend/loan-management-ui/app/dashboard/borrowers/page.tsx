
'use client';

import { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';

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

  /**
   * ============================================================
   * LOAD BORROWERS
   * ============================================================
   */

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
   * BORROWER DETAILS URL
   * ============================================================
   *
   * IMPORTANT:
   *
   * The borrower details page is:
   *
   * /dashboard/borrowers/[id]
   *
   * Therefore every borrower link MUST use:
   *
   * /dashboard/borrowers/{id}
   *
   * Never:
   *
   * /dashboard/{id}
   */

  const borrowerDetailsUrl = (
    borrowerId: number,
  ) => `/dashboard/borrowers/${borrowerId}`;

  return (
    <div>
      {/* ======================================================
          HEADER
      ====================================================== */}

      <div className="flex items-start justify-between mb-6">
        <div>
          <h1 className="text-2xl font-extrabold text-gray-900">
            Borrowers
          </h1>

          <p className="text-sm text-gray-500 mt-0.5">
            {formatNumber(total)} registered clients
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
          SEARCH
      ====================================================== */}

      <div className="flex gap-3 mb-4">
        <div className="relative">
          <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm">
            🔍
          </span>

          <Input
            placeholder="Search name, email, ID…"
            className="pl-9 w-64"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
          />
        </div>
      </div>

      {/* ======================================================
          BORROWER TABLE
      ====================================================== */}

      <Card>
        {loading ? (
          <div className="flex items-center justify-center py-16">
            <div className="w-8 h-8 border-2 border-teal-500 border-t-transparent rounded-full animate-spin" />
          </div>
        ) : (
          <Table>
            <Thead>
              <tr>
                <Th>Name</Th>
                <Th>Email</Th>
                <Th>Phone</Th>
                <Th>National ID</Th>
                <Th>Employer</Th>
                <Th>Income</Th>
                <Th>Credit Score</Th>
                <Th>Country</Th>
                <Th>Since</Th>
              </tr>
            </Thead>

            <Tbody>
              {borrowers.length === 0 ? (
                <EmptyRow
                  cols={9}
                  message="No borrowers found"
                />
              ) : (
                borrowers.map(
                  (borrower: Borrower) => {
                    const borrowerId = Number(
                      borrower.id,
                    );

                    const detailsUrl =
                      borrowerDetailsUrl(
                        borrowerId,
                      );

                    return (
                      <Tr
                        key={borrower.id}
                        className="hover:bg-gray-50"
                      >
                        {/* ==================================================
                            NAME
                        ================================================== */}

                        <Td>
                          <Link
                            href={detailsUrl}
                            className="flex items-center gap-2 group"
                          >
                            <div className="w-8 h-8 bg-teal-100 rounded-full flex items-center justify-center text-sm font-bold text-teal-700 flex-shrink-0">
                              {borrower.firstName?.[0] ??
                                ''}
                              {borrower.lastName?.[0] ??
                                ''}
                            </div>

                            <div>
                              <div className="font-semibold text-sm text-gray-900 group-hover:text-teal-600 transition-colors">
                                {borrower.firstName}{' '}
                                {borrower.lastName}
                              </div>

                              <div className="text-xs text-gray-400">
                                {borrower.employmentType ??
                                  '—'}
                              </div>
                            </div>
                          </Link>
                        </Td>

                        {/* ==================================================
                            EMAIL
                        ================================================== */}

                        <Td className="text-sm text-gray-600">
                          {borrower.email ?? '—'}
                        </Td>

                        {/* ==================================================
                            PHONE
                        ================================================== */}

                        <Td className="text-sm text-gray-600">
                          {borrower.phone ?? '—'}
                        </Td>

                        {/* ==================================================
                            NATIONAL ID
                        ================================================== */}

                        <Td>
                          <code className="text-xs bg-gray-100 px-2 py-0.5 rounded">
                            {borrower.nationalId ?? '—'}
                          </code>
                        </Td>

                        {/* ==================================================
                            EMPLOYER
                        ================================================== */}

                        <Td className="text-sm text-gray-600">
                          {borrower.employerName ?? '—'}
                        </Td>

                        {/* ==================================================
                            INCOME
                        ================================================== */}

                        <Td className="font-semibold text-sm">
                          {formatCurrency(
                            borrower.monthlyIncome,
                            currency,
                            locale,
                          )}
                        </Td>

                        {/* ==================================================
                            CREDIT SCORE
                        ================================================== */}

                        <Td>
                          <span
                            className={`font-bold text-sm ${
                              (borrower.creditScore ?? 0) >=
                              700
                                ? 'text-teal-600'
                                : (borrower.creditScore ?? 0) >=
                                  600
                                ? 'text-yellow-600'
                                : 'text-red-500'
                            }`}
                          >
                            {borrower.creditScore ??
                              '—'}
                          </span>
                        </Td>

                        {/* ==================================================
                            COUNTRY
                        ================================================== */}

                        <Td className="text-xs text-gray-500">
                          {borrower.country ?? '—'}
                        </Td>

                        {/* ==================================================
                            CREATED DATE
                        ================================================== */}

                        <Td className="text-xs text-gray-400">
                          {formatDate(
                            borrower.createdAt,
                            locale,
                          )}
                        </Td>
                      </Tr>
                    );
                  },
                )
              )}
            </Tbody>
          </Table>
        )}
      </Card>

      {/* ======================================================
          PAGINATION
      ====================================================== */}

      {total > 20 && (
        <div className="flex items-center justify-between mt-4">
          <Button
            variant="secondary"
            disabled={page === 0}
            onClick={() =>
              setPage(
                Math.max(0, page - 1),
              )
            }
          >
            Previous
          </Button>

          <span className="text-sm text-gray-500">
            Page {page + 1}
          </span>

          <Button
            variant="secondary"
            disabled={
              (page + 1) * 20 >= total
            }
            onClick={() =>
              setPage(page + 1)
            }
          >
            Next
          </Button>
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
              onClick={handleAdd as any}
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

          {/* ==================================================
              PERSONAL INFORMATION
          ================================================== */}

          <div className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-3">
            Personal Information
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
                onChange={set('email')}
              />
            </FormGroup>

            <FormGroup label="Phone">
              <Input
                value={form.phone}
                onChange={set('phone')}
              />
            </FormGroup>
          </FormRow>

          <FormRow>
            <FormGroup label="National ID">
              <Input
                value={form.nationalId}
                onChange={set(
                  'nationalId',
                )}
              />
            </FormGroup>

            <FormGroup label="Date of Birth">
              <Input
                type="date"
                value={form.dateOfBirth}
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
                onChange={set('gender')}
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
                value={form.nationality}
                onChange={set(
                  'nationality',
                )}
              >
                {COUNTRIES.map(
                  (country) => (
                    <option
                      key={country.code}
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

          {/* ==================================================
              EMPLOYMENT & FINANCE
          ================================================== */}

          <div className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-3 mt-4">
            Employment & Finance
          </div>

          <FormRow>
            <FormGroup label="Employer Name">
              <Input
                value={form.employerName}
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
                      key={country.code}
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

          {/* ==================================================
              BANK DETAILS
          ================================================== */}

          <div className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-3 mt-4">
            Bank Details
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
