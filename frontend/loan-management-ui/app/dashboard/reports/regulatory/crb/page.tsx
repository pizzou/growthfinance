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
  // EXPORT (FIXED: Safely invokes pipeline without extra logic)
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


  useEffect(() => {

    void loadPreview();

  }, [
    loadPreview,
  ]);


  return (

    <main className="min-h-screen bg-slate-50">

      <div className="mx-auto max-w-[1600px] space-y-6 p-4 md:p-6 lg:p-8">

       

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


      

        {error && (

          <div className="rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
            {error}
          </div>

        )}



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
                      Outstanding Balance
                    </Header>

                  </tr>

                </thead>

                <tbody className="divide-y divide-slate-200 bg-white">
                  {records.map((record, index) => (
                    <tr key={index} className="hover:bg-slate-50">
                      <td className="px-6 py-4 whitespace-nowrap font-medium text-slate-900">
                        {record.fullName || 'N/A'}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-slate-500">
                        {record.nationalId || 'N/A'}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-slate-500">
                        {record.loanNumber || 'N/A'}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-slate-500">
                        {record.loanType || 'N/A'}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-slate-900 font-semibold">
                        {record.outstandingBalance != null ? record.outstandingBalance.toFixed(2) : '0.00'}
                      </td>
                    </tr>
                  ))}
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
// LOCAL COMPONENT UI DUMMIES
// ============================================================

function Field({ label, value, onChange, placeholder, type = 'text' }: any) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-xs font-semibold text-slate-700">{label}</label>
      <input
        type={type}
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        className="rounded-xl border border-slate-200 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none"
      />
    </div>
  );
}

function SummaryCard({ label, value }: any) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
        {label}
      </p>
      <p className="mt-2 text-2xl font-bold text-slate-900">
        {value}
      </p>
    </div>
  );
}

function Header({ children }: { children: React.ReactNode }) {
  return (
    <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
      {children}
    </th>
  );
}
