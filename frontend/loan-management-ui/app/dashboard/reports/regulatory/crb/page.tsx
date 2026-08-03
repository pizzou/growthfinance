'use client';

import React, {
  useCallback,
  useEffect,
  useState,
} from 'react';

import {
  regulatoryApi,
  type CreditRecord,
  type ExportFormat,
} from '@/services/regulatoryService';


export default function CreditBureauPage() {

  const [borrowerId, setBorrowerId] =
    useState('');

  const [from, setFrom] =
    useState('');

  const [to, setTo] =
    useState('');

  const [records, setRecords] =
    useState<CreditRecord[]>([]);

  const [loading, setLoading] =
    useState(false);

  const [error, setError] =
    useState<string | null>(null);

  const [exporting, setExporting] =
    useState<ExportFormat | null>(null);


  // ==========================================================
  // PREVIEW
  // ==========================================================

  const loadPreview =
    useCallback(async () => {

      try {

        setLoading(true);
        setError(null);

        const result =
          await regulatoryApi.creditBureauPreview({
            ...(borrowerId
              ? {
                  borrowerId: Number(borrowerId),
                }
              : {}),
            ...(from ? { from } : {}),
            ...(to ? { to } : {}),
          });

        setRecords(
          Array.isArray(result)
            ? result
            : []
        );

      } catch (err) {

        console.error(
          'Credit Bureau preview error:',
          err
        );

        setError(
          regulatoryApi.getErrorMessage(
            err,
            'Unable to load Credit Bureau records.'
          )
        );

      } finally {

        setLoading(false);

      }

    }, [
      borrowerId,
      from,
      to,
    ]);


  // ==========================================================
  // EXPORT
  // ==========================================================

  const exportRecords =
    async (
      format: ExportFormat
    ) => {

      try {

        setExporting(format);
        setError(null);

        await regulatoryApi.creditBureauExport(
          format,
          {
            ...(borrowerId
              ? {
                  borrowerId: Number(borrowerId),
                }
              : {}),
            ...(from ? { from } : {}),
            ...(to ? { to } : {}),
          }
        );

      } catch (err) {

        setError(
          regulatoryApi.getErrorMessage(
            err,
            `Unable to export Credit Bureau ${format.toUpperCase()} report.`
          )
        );

      } finally {

        setExporting(null);

      }

    };


  // ==========================================================
  // INITIAL
  // ==========================================================

  useEffect(() => {

    void loadPreview();

  }, [
    loadPreview,
  ]);


  return (

    <main className="min-h-screen bg-slate-50">

      <div className="mx-auto max-w-[1600px] space-y-6 p-4 md:p-6 lg:p-8">

        {/* ================================================== */}
        {/* HEADER */}
        {/* ================================================== */}

        <section className="rounded-3xl bg-gradient-to-br from-slate-950 via-indigo-950 to-violet-950 p-6 text-white shadow-xl md:p-8">

          <div className="flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">

            <div>

              <div className="mb-3 inline-flex rounded-full border border-white/10 bg-white/10 px-3 py-1 text-xs font-medium">
                Credit Information
              </div>

              <h1 className="text-3xl font-bold md:text-4xl">
                Credit Bureau
              </h1>

              <p className="mt-2 max-w-2xl text-sm text-indigo-200 md:text-base">
                Review borrower credit information, loan history,
                repayment performance and credit reporting records.
              </p>

            </div>


            <div className="flex flex-wrap gap-2">

              {(['pdf', 'xlsx', 'csv'] as ExportFormat[]).map(
                (format) => (

                  <button
                    key={format}
                    type="button"
                    onClick={() =>
                      void exportRecords(format)
                    }
                    disabled={exporting !== null}
                    className="rounded-xl border border-white/15 bg-white/10 px-4 py-2 text-sm font-semibold backdrop-blur hover:bg-white/20 disabled:opacity-50"
                  >

                    {exporting === format
                      ? 'Exporting...'
                      : `Export ${format.toUpperCase()}`}

                  </button>

                )
              )}

            </div>

          </div>

        </section>


        {/* ================================================== */}
        {/* ERROR */}
        {/* ================================================== */}

        {error && (

          <div className="rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            {error}
          </div>

        )}


        {/* ================================================== */}
        {/* SEARCH */}
        {/* ================================================== */}

        <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm md:p-6">

          <div className="mb-5">

            <h2 className="text-xl font-bold text-slate-900">
              Credit Bureau Search
            </h2>

            <p className="mt-1 text-sm text-slate-500">
              Filter the credit records you want to review.
            </p>

          </div>


          <div className="grid gap-4 md:grid-cols-4">

            <Field
              label="Borrower ID"
              value={borrowerId}
              onChange={setBorrowerId}
              placeholder="e.g. 1024"
            />

            <Field
              label="From"
              type="date"
              value={from}
              onChange={setFrom}
            />

            <Field
              label="To"
              type="date"
              value={to}
              onChange={setTo}
            />

            <div className="flex items-end">

              <button
                type="button"
                onClick={() => void loadPreview()}
                disabled={loading}
                className="w-full rounded-xl bg-slate-950 px-5 py-2.5 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-50"
              >

                {loading
                  ? 'Searching...'
                  : 'Search Records'}

              </button>

            </div>

          </div>

        </section>


        {/* ================================================== */}
        {/* SUMMARY */}
        {/* ================================================== */}

        <div className="grid gap-4 sm:grid-cols-3">

          <SummaryCard
            label="Records"
            value={records.length}
          />

          <SummaryCard
            label="Borrowers"
            value={
              new Set(
                records.map(
                  record => record.borrowerId
                )
              ).size
            }
          />

          <SummaryCard
            label="Default / Delinquent"
            value={
              records.filter(
                record =>
                  Number(record.daysPastDue ?? 0) > 0
              ).length
            }
          />

        </div>


        {/* ================================================== */}
        {/* TABLE */}
        {/* ================================================== */}

        <section className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">

          <div className="border-b border-slate-200 p-5">

            <h2 className="text-xl font-bold text-slate-900">
              Credit Records
            </h2>

          </div>


          {records.length === 0 ? (

            <div className="p-12 text-center">

              <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-100 text-2xl">
                🧾
              </div>

              <h3 className="mt-4 font-semibold text-slate-900">
                No credit records found
              </h3>

              <p className="mt-1 text-sm text-slate-500">
                Try changing your search criteria.
              </p>

            </div>

          ) : (

            <div className="overflow-x-auto">

              <table className="min-w-[1200px] w-full text-sm">

                <thead className="bg-slate-50">

                  <tr>

                    <Header>
                      Borrower
                    </Header>

                    <Header>
                      National ID
                    </Header>

                    <Header>
                      Loan Number
                    </Header>

                    <Header>
                      Loan Type
                    </Header>

                    <Header>
                      Status
                    </Header>

                    <Header align="right">
                      Loan Amount
                    </Header>

                    <Header align="right">
                      Outstanding
                    </Header>

                    <Header align="right">
                      DPD
                    </Header>

                    <Header align="right">
                      Credit Score
                    </Header>

                  </tr>

                </thead>


                <tbody className="divide-y divide-slate-100">

                  {records.map(
                    (record, index) => (

                      <tr
                        key={`${record.borrowerId}-${record.loanNumber}-${index}`}
                        className="hover:bg-slate-50"
                      >

                        <Cell>
                          <div>

                            <p className="font-semibold text-slate-800">
                              {record.fullName ||
                                'Unknown Borrower'}
                            </p>

                            <p className="text-xs text-slate-400">
                              ID #{record.borrowerId ?? '—'}
                            </p>

                          </div>
                        </Cell>

                        <Cell>
                          {record.nationalId || '—'}
                        </Cell>

                        <Cell>
                          {record.loanNumber || '—'}
                        </Cell>

                        <Cell>
                          {record.loanType || '—'}
                        </Cell>

                        <Cell>
                          <StatusBadge
                            status={
                              record.loanStatus ||
                              'UNKNOWN'
                            }
                          />
                        </Cell>

                        <Cell align="right">
                          {money(
                            record.loanAmount
                          )}
                        </Cell>

                        <Cell align="right">
                          {money(
                            record.outstandingBalance
                          )}
                        </Cell>

                        <Cell align="right">
                          <span
                            className={
                              Number(
                                record.daysPastDue ?? 0
                              ) > 0
                                ? 'font-semibold text-red-600'
                                : 'text-slate-600'
                            }
                          >
                            {record.daysPastDue ?? 0}
                          </span>
                        </Cell>

                        <Cell align="right">
                          {record.creditScore ?? '—'}
                        </Cell>

                      </tr>

                    )
                  )}

                </tbody>

              </table>

            </div>

          )}

        </section>

      </div>

    </main>
  );
}


// ============================================================
// COMPONENTS
// ============================================================

function Field({
  label,
  value,
  onChange,
  placeholder,
  type = 'text',
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  type?: string;
}) {

  return (

    <label>

      <span className="mb-1.5 block text-sm font-medium text-slate-700">
        {label}
      </span>

      <input
        type={type}
        value={value}
        placeholder={placeholder}
        onChange={(event) =>
          onChange(event.target.value)
        }
        className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-indigo-500 focus:ring-4 focus:ring-indigo-50"
      />

    </label>

  );
}


function SummaryCard({
  label,
  value,
}: {
  label: string;
  value: number;
}) {

  return (

    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">

      <p className="text-sm text-slate-500">
        {label}
      </p>

      <p className="mt-2 text-3xl font-bold text-slate-900">
        {value.toLocaleString()}
      </p>

    </div>

  );
}


function Header({
  children,
  align = 'left',
}: {
  children: React.ReactNode;
  align?: 'left' | 'right';
}) {

  return (

    <th
      className={`px-5 py-3 text-${align} text-xs font-semibold uppercase tracking-wider text-slate-500`}
    >
      {children}
    </th>

  );
}


function Cell({
  children,
  align = 'left',
}: {
  children: React.ReactNode;
  align?: 'left' | 'right';
}) {

  return (

    <td
      className={`whitespace-nowrap px-5 py-4 text-${align} text-slate-600`}
    >
      {children}
    </td>

  );
}


function StatusBadge({
  status,
}: {
  status: string;
}) {

  const normalized =
    status.toUpperCase();

  const positive =
    [
      'ACTIVE',
      'PAID',
      'CLOSED',
      'CURRENT',
    ].includes(normalized);

  const negative =
    [
      'DEFAULTED',
      'OVERDUE',
      'WRITTEN_OFF',
      'WRITTEN OFF',
    ].includes(normalized);

  return (

    <span
      className={
        positive
          ? 'rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700'
          : negative
            ? 'rounded-full bg-red-50 px-2.5 py-1 text-xs font-semibold text-red-700'
            : 'rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600'
      }
    >
      {status}
    </span>

  );
}


function money(
  value?: number
): string {

  return new Intl.NumberFormat(
    'en-RW',
    {
      style: 'currency',
      currency: 'RWF',
      maximumFractionDigits: 2,
    }
  ).format(
    Number(value ?? 0)
  );
}