'use client';

import React, {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';

import {
  regulatoryApi,
  type BnrFinancialStatementReport,
  type BnrReportParams,
  type BnrSummary,
  type BreakdownRow,
  type ExportFormat,
  type FinancialStatementRow,
  type RegulatoryPeriod,
} from '@/services/regulatoryService';


// ============================================================
// TYPES
// ============================================================

type DownloadingFormat =
  | ExportFormat
  | null;


// ============================================================
// PAGE
// ============================================================

export default function BnrReportPage() {

  // ==========================================================
  // FILTERS
  // ==========================================================

  const [period, setPeriod] =
    useState<RegulatoryPeriod>('MONTHLY');

  const [from, setFrom] =
    useState<string>('');

  const [to, setTo] =
    useState<string>('');


  // ==========================================================
  // REPORT DATA
  // ==========================================================

  const [summary, setSummary] =
    useState<BnrSummary | null>(null);

  const [financialStatement, setFinancialStatement] =
    useState<BnrFinancialStatementReport | null>(null);

  const [loanTypeBreakdown, setLoanTypeBreakdown] =
    useState<BreakdownRow[]>([]);

  const [branchBreakdown, setBranchBreakdown] =
    useState<BreakdownRow[]>([]);

  const [genderBreakdown, setGenderBreakdown] =
    useState<BreakdownRow[]>([]);


  // ==========================================================
  // UI STATE
  // ==========================================================

  const [loading, setLoading] =
    useState<boolean>(true);

  const [downloadingFormat, setDownloadingFormat] =
    useState<DownloadingFormat>(null);

  const [error, setError] =
    useState<string | null>(null);


  // ==========================================================
  // REPORT PARAMETERS
  // ==========================================================

  const reportParams =
    useMemo<BnrReportParams>(() => {

      const params: BnrReportParams = {
        period,
      };

      if (period === 'CUSTOM') {

        if (from) {
          params.from = from;
        }

        if (to) {
          params.to = to;
        }
      }

      return params;

    }, [
      period,
      from,
      to,
    ]);


  // ==========================================================
  // VALIDATE FILTERS
  // ==========================================================

  const validateFilters =
    useCallback((): string | null => {

      if (period !== 'CUSTOM') {
        return null;
      }

      if (!from) {
        return 'Please select a start date.';
      }

      if (!to) {
        return 'Please select an end date.';
      }

      if (from > to) {
        return 'The start date cannot be after the end date.';
      }

      return null;

    }, [
      period,
      from,
      to,
    ]);


  // ==========================================================
  // LOAD REPORT
  // ==========================================================

  const loadReport =
    useCallback(async (): Promise<void> => {

      const validationError =
        validateFilters();

      if (validationError) {

        setError(
          validationError
        );

        return;
      }

      try {

        setLoading(true);

        setError(null);


        const [
          summaryResult,
          financialStatementResult,
          loanTypeResult,
          branchResult,
          genderResult,
        ] = await Promise.all([

          // ----------------------------------------------------
          // BNR SUMMARY
          // ----------------------------------------------------

          regulatoryApi.bnrSummary(
            reportParams
          ),


          // ----------------------------------------------------
          // BNR FINANCIAL STATEMENT
          // ----------------------------------------------------

          regulatoryApi.bnrFinancialStatement(
            reportParams
          ),


          // ----------------------------------------------------
          // LOAN TYPE
          // ----------------------------------------------------

          regulatoryApi.bnrByLoanType(
            reportParams
          ),


          // ----------------------------------------------------
          // BRANCH
          // ----------------------------------------------------

          regulatoryApi.bnrByBranch(
            reportParams
          ),


          // ----------------------------------------------------
          // GENDER
          // ----------------------------------------------------

          regulatoryApi.bnrByGender(
            reportParams
          ),
        ]);


        setSummary(
          summaryResult
        );

        setFinancialStatement(
          financialStatementResult
        );

        setLoanTypeBreakdown(
          loanTypeResult
        );

        setBranchBreakdown(
          branchResult
        );

        setGenderBreakdown(
          genderResult
        );

      } catch (err) {

        console.error(
          'Failed to load BNR report:',
          err
        );

        setError(
          regulatoryApi.getErrorMessage(
            err,
            'Failed to load the BNR report.'
          )
        );

      } finally {

        setLoading(false);
      }

    }, [
      reportParams,
      validateFilters,
    ]);


  // ==========================================================
  // INITIAL LOAD
  // ==========================================================

  useEffect(() => {

    void loadReport();

  }, [
    loadReport,
  ]);


  // ==========================================================
  // DOWNLOAD
  // ==========================================================

  const downloadReport =
    useCallback(
      async (
        format: ExportFormat
      ): Promise<void> => {

        const validationError =
          validateFilters();

        if (validationError) {

          setError(
            validationError
          );

          return;
        }

        try {

          setError(null);

          setDownloadingFormat(
            format
          );

          await regulatoryApi.bnrExport(
            format,
            reportParams
          );

        } catch (err) {

          console.error(
            `Failed to download BNR ${format} report:`,
            err
          );

          setError(
            regulatoryApi.getErrorMessage(
              err,
              `Failed to download BNR ${format.toUpperCase()} report.`
            )
          );

        } finally {

          setDownloadingFormat(
            null
          );
        }

      },
      [
        reportParams,
        validateFilters,
      ]
    );


  // ==========================================================
  // DOWNLOAD HANDLERS
  // ==========================================================

  const handleDownloadPdf =
    useCallback(
      async (): Promise<void> => {

        await downloadReport(
          'pdf'
        );

      },
      [
        downloadReport,
      ]
    );


  const handleDownloadExcel =
    useCallback(
      async (): Promise<void> => {

        await downloadReport(
          'xlsx'
        );

      },
      [
        downloadReport,
      ]
    );


  const handleDownloadCsv =
    useCallback(
      async (): Promise<void> => {

        await downloadReport(
          'csv'
        );

      },
      [
        downloadReport,
      ]
    );


  // ==========================================================
  // FORMAT MONEY
  // ==========================================================

  const formatMoney =
    useCallback(
      (
        value?: number
      ): string => {

        const currency =
          summary?.currency ||
          financialStatement?.currency ||
          'RWF';

        const amount =
          Number(value ?? 0);

        try {

          return new Intl.NumberFormat(
            'en-RW',
            {
              style: 'currency',
              currency,
              maximumFractionDigits: 2,
            }
          ).format(amount);

        } catch {

          return `${currency} ${amount.toLocaleString(
            'en-US',
            {
              maximumFractionDigits: 2,
            }
          )}`;
        }

      },
      [
        summary?.currency,
        financialStatement?.currency,
      ]
    );


  // ==========================================================
  // FORMAT NUMBER
  // ==========================================================

  const formatNumber =
    useCallback(
      (
        value?: number
      ): string => {

        return new Intl.NumberFormat(
          'en-US'
        ).format(
          Number(value ?? 0)
        );

      },
      []
    );


  // ==========================================================
  // FORMAT PERCENT
  // ==========================================================

  const formatPercent =
    useCallback(
      (
        value?: number
      ): string => {

        return `${Number(value ?? 0).toFixed(2)}%`;

      },
      []
    );


  // ==========================================================
  // LOADING
  // ==========================================================

  if (loading) {

    return (
      <div className="min-h-screen bg-gray-50 p-6">

        <div className="mx-auto max-w-7xl">

          <div className="animate-pulse space-y-6">

            <div className="h-10 w-72 rounded bg-gray-200" />

            <div className="h-24 rounded bg-gray-200" />

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">

              {Array.from(
                {
                  length: 8,
                }
              ).map(
                (_, index) => (

                  <div
                    key={index}
                    className="h-32 rounded bg-gray-200"
                  />

                )
              )}

            </div>

          </div>

        </div>

      </div>
    );
  }


  // ==========================================================
  // RENDER
  // ==========================================================

  return (

    <div className="min-h-screen bg-gray-50">

      <div className="mx-auto max-w-7xl space-y-6 p-6">


        {/* ================================================== */}
        {/* HEADER */}
        {/* ================================================== */}

        <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">

          <div>

            <h1 className="text-2xl font-bold text-gray-900">
              BNR Regulatory Report
            </h1>

            <p className="mt-1 text-sm text-gray-500">
              Regulatory reporting, portfolio quality,
              financial statements and BNR reporting information.
            </p>

          </div>


          {/* ================================================== */}
          {/* EXPORT BUTTONS */}
          {/* ================================================== */}

          <div className="flex flex-wrap gap-2">

            <button
              type="button"
              onClick={handleDownloadPdf}
              disabled={
                downloadingFormat !== null
              }
              className="rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50"
            >

              {downloadingFormat === 'pdf'
                ? 'Downloading PDF...'
                : 'Download PDF'}

            </button>


            <button
              type="button"
              onClick={handleDownloadExcel}
              disabled={
                downloadingFormat !== null
              }
              className="rounded-lg bg-green-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-green-700 disabled:cursor-not-allowed disabled:opacity-50"
            >

              {downloadingFormat === 'xlsx'
                ? 'Downloading Excel...'
                : 'Download Excel'}

            </button>


            <button
              type="button"
              onClick={handleDownloadCsv}
              disabled={
                downloadingFormat !== null
              }
              className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
            >

              {downloadingFormat === 'csv'
                ? 'Downloading CSV...'
                : 'Download CSV'}

            </button>

          </div>

        </div>


        {/* ================================================== */}
        {/* ERROR */}
        {/* ================================================== */}

        {error && (

          <div className="rounded-lg border border-red-200 bg-red-50 p-4">

            <div className="flex items-start justify-between gap-4">

              <div>

                <p className="font-semibold text-red-800">
                  Report error
                </p>

                <p className="mt-1 text-sm text-red-700">
                  {error}
                </p>

              </div>


              <button
                type="button"
                onClick={() => setError(null)}
                className="text-sm font-medium text-red-700 hover:text-red-900"
              >
                Dismiss
              </button>

            </div>

          </div>

        )}


        {/* ================================================== */}
        {/* FILTERS */}
        {/* ================================================== */}

        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">

          <div className="mb-4">

            <h2 className="font-semibold text-gray-900">
              Reporting Period
            </h2>

            <p className="text-sm text-gray-500">
              Select the reporting period used for the BNR report.
            </p>

          </div>


          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">


            {/* PERIOD */}

            <div>

              <label
                htmlFor="bnr-period"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                Period
              </label>

              <select
                id="bnr-period"
                value={period}
                onChange={(event) =>
                  setPeriod(
                    event.target.value as RegulatoryPeriod
                  )
                }
                className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              >

                <option value="DAILY">
                  Daily
                </option>

                <option value="WEEKLY">
                  Weekly
                </option>

                <option value="MONTHLY">
                  Monthly
                </option>

                <option value="QUARTERLY">
                  Quarterly
                </option>

                <option value="YEARLY">
                  Yearly
                </option>

                <option value="CUSTOM">
                  Custom
                </option>

              </select>

            </div>


            {/* FROM */}

            <div>

              <label
                htmlFor="bnr-from"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                From
              </label>

              <input
                id="bnr-from"
                type="date"
                value={from}
                disabled={
                  period !== 'CUSTOM'
                }
                onChange={(event) =>
                  setFrom(
                    event.target.value
                  )
                }
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none disabled:bg-gray-100 focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              />

            </div>


            {/* TO */}

            <div>

              <label
                htmlFor="bnr-to"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                To
              </label>

              <input
                id="bnr-to"
                type="date"
                value={to}
                disabled={
                  period !== 'CUSTOM'
                }
                onChange={(event) =>
                  setTo(
                    event.target.value
                  )
                }
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none disabled:bg-gray-100 focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              />

            </div>

          </div>


          <div className="mt-4 flex justify-end">

            <button
              type="button"
              onClick={() => void loadReport()}
              disabled={loading}
              className="rounded-lg bg-gray-900 px-5 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Refresh Report
            </button>

          </div>

        </div>


        {/* ================================================== */}
        {/* ORGANIZATION */}
        {/* ================================================== */}

        {summary && (

          <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">

            <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">

              <div>

                <h2 className="text-lg font-semibold text-gray-900">
                  {summary.organizationName ||
                    'Organization'}
                </h2>

                <p className="text-sm text-gray-500">
                  BNR Institution Code:{' '}
                  {summary.bnrInstitutionCode ||
                    'Not configured'}
                </p>

                <p className="text-xs text-gray-400">
                  Registration:{' '}
                  {summary.registrationNumber ||
                    'Not configured'}
                </p>

              </div>


              <div className="text-sm text-gray-500">

                <div>
                  Period:{' '}
                  {summary.periodStart ||
                    '—'}
                  {' → '}
                  {summary.periodEnd ||
                    '—'}
                </div>

                <div>
                  Currency:{' '}
                  {summary.currency ||
                    'RWF'}
                </div>

                <div>
                  Status:{' '}
                  {summary.reportStatus ||
                    '—'}
                </div>

              </div>

            </div>

          </div>

        )}


        {/* ================================================== */}
        {/* KPI CARDS */}
        {/* ================================================== */}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">


          <KpiCard
            label="Total Loans"
            value={formatNumber(
              summary?.totalLoans
            )}
          />


          <KpiCard
            label="Active Loans"
            value={formatNumber(
              summary?.activeLoans
            )}
          />


          <KpiCard
            label="Principal Disbursed"
            value={formatMoney(
              summary?.totalPrincipalDisbursed
            )}
          />


          <KpiCard
            label="Outstanding Principal"
            value={formatMoney(
              summary?.outstandingPrincipal
            )}
          />


          <KpiCard
            label="Interest Collected"
            value={formatMoney(
              summary?.totalInterestCollected
            )}
          />


          <KpiCard
            label="Total Collected"
            value={formatMoney(
              summary?.totalAmountCollected
            )}
          />


          <KpiCard
            label="Overdue Loans"
            value={formatNumber(
              summary?.overdueLoans
            )}
          />


          <KpiCard
            label="Defaulted Loans"
            value={formatNumber(
              summary?.defaultedLoans
            )}
          />

        </div>


        {/* ================================================== */}
        {/* PORTFOLIO QUALITY */}
        {/* ================================================== */}

        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">

          <h2 className="mb-4 text-lg font-semibold text-gray-900">
            Portfolio Quality
          </h2>


          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">


            <MetricCard
              label="PAR"
              value={formatPercent(
                summary?.parRatio
              )}
              secondary={formatMoney(
                summary?.parAmount
              )}
            />


            <MetricCard
              label="PAR > 30 Days"
              value={formatPercent(
                summary?.par30Ratio
              )}
              secondary={formatMoney(
                getPar30Amount(summary)
              )}
            />


            <MetricCard
              label="PAR > 60 Days"
              value={formatPercent(
                summary?.par60Ratio
              )}
              secondary={formatMoney(
                getPar60Amount(summary)
              )}
            />


            <MetricCard
              label="PAR > 90 Days"
              value={formatPercent(
                summary?.par90Ratio
              )}
              secondary={formatMoney(
                getPar90Amount(summary)
              )}
            />


            <MetricCard
              label="NPL Ratio"
              value={formatPercent(
                summary?.nplRatio
              )}
              secondary={formatMoney(
                summary?.nplAmount
              )}
            />


            <MetricCard
              label="NPL Loans"
              value={formatNumber(
                summary?.nplLoanCount
              )}
            />


            <MetricCard
              label="Loans > 30 DPD"
              value={formatNumber(
                summary?.loansOver30Days
              )}
            />


            <MetricCard
              label="Loans > 90 DPD"
              value={formatNumber(
                summary?.loansOver90Days
              )}
            />

          </div>

        </div>


        {/* ================================================== */}
        {/* PAR BUCKETS */}
        {/* ================================================== */}

        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">

          <h2 className="mb-4 text-lg font-semibold text-gray-900">
            Portfolio at Risk Aging
          </h2>


          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">


            <MetricCard
              label="1–30 Days"
              value={formatMoney(
                summary?.par1To30Amount
              )}
            />


            <MetricCard
              label="31–60 Days"
              value={formatMoney(
                summary?.par31To60Amount
              )}
            />


            <MetricCard
              label="61–90 Days"
              value={formatMoney(
                summary?.par61To90Amount
              )}
            />


            <MetricCard
              label="91–180 Days"
              value={formatMoney(
                summary?.par91To180Amount
              )}
            />


            <MetricCard
              label="181–365 Days"
              value={formatMoney(
                summary?.par181To365Amount
              )}
            />


            <MetricCard
              label="Over 365 Days"
              value={formatMoney(
                summary?.parOver365Amount
              )}
            />

          </div>

        </div>


        {/* ================================================== */}
        {/* LOAN STATUS */}
        {/* ================================================== */}

        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">

          <h2 className="mb-4 text-lg font-semibold text-gray-900">
            Loan Status
          </h2>


          <div className="grid grid-cols-2 gap-4 md:grid-cols-4 lg:grid-cols-6">


            <StatusItem
              label="Active"
              value={summary?.activeLoans}
            />


            <StatusItem
              label="Closed"
              value={summary?.closedLoans}
            />


            <StatusItem
              label="Paid"
              value={summary?.paidLoans}
            />


            <StatusItem
              label="Pending"
              value={summary?.pendingLoans}
            />


            <StatusItem
              label="Approved"
              value={summary?.approvedLoans}
            />


            <StatusItem
              label="Rejected"
              value={summary?.rejectedLoans}
            />


            <StatusItem
              label="Cancelled"
              value={summary?.cancelledLoans}
            />


            <StatusItem
              label="Defaulted"
              value={summary?.defaultedLoans}
            />


            <StatusItem
              label="Written Off"
              value={summary?.writtenOffLoans}
            />


            <StatusItem
              label="Overdue"
              value={summary?.overdueLoans}
            />

          </div>

        </div>


        {/* ================================================== */}
        {/* BORROWERS */}
        {/* ================================================== */}

        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">

          <h2 className="mb-4 text-lg font-semibold text-gray-900">
            Borrower Statistics
          </h2>


          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">


            <StatusItem
              label="Total Borrowers"
              value={summary?.totalBorrowers}
            />


            <StatusItem
              label="Active Borrowers"
              value={summary?.activeBorrowers}
            />


            <StatusItem
              label="Male Borrowers"
              value={summary?.maleBorrowers}
            />


            <StatusItem
              label="Female Borrowers"
              value={summary?.femaleBorrowers}
            />


            <StatusItem
              label="Youth Borrowers"
              value={summary?.youthBorrowers}
            />


            <StatusItem
              label="Adult Borrowers"
              value={summary?.adultBorrowers}
            />


            <StatusItem
              label="Senior Borrowers"
              value={summary?.seniorBorrowers}
            />


            <StatusItem
              label="Multiple Loans"
              value={summary?.borrowersWithMultipleLoans}
            />

          </div>

        </div>


        {/* ================================================== */}
        {/* CREDIT INFORMATION */}
        {/* ================================================== */}

        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">

          <h2 className="mb-4 text-lg font-semibold text-gray-900">
            Credit Information
          </h2>


          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">


            <StatusItem
              label="Credit Checked"
              value={summary?.borrowersCreditChecked}
            />


            <StatusItem
              label="Default History"
              value={summary?.borrowersWithDefaultHistory}
            />


            <StatusItem
              label="Active Listings"
              value={summary?.borrowersWithActiveListing}
            />


            <StatusItem
              label="Multiple Facilities"
              value={summary?.borrowersWithMultipleFacilities}
            />


            <MetricCard
              label="External Debt"
              value={formatMoney(
                summary?.totalExternalDebt
              )}
            />

          </div>

        </div>


        {/* ================================================== */}
        {/* REPAYMENT PERFORMANCE */}
        {/* ================================================== */}

        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">

          <h2 className="mb-4 text-lg font-semibold text-gray-900">
            Repayment Performance
          </h2>


          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">


            <MetricCard
              label="Principal Collected"
              value={formatMoney(
                summary?.totalPrincipalCollected
              )}
            />


            <MetricCard
              label="Interest Collected"
              value={formatMoney(
                summary?.totalInterestCollected
              )}
            />


            <MetricCard
              label="Fees Collected"
              value={formatMoney(
                summary?.totalFeesCollected
              )}
            />


            <MetricCard
              label="Total Collected"
              value={formatMoney(
                summary?.totalAmountCollected
              )}
            />


            <MetricCard
              label="Unpaid Interest"
              value={formatMoney(
                summary?.interestAccruedUnpaid
              )}
            />


            <MetricCard
              label="Unpaid Fees"
              value={formatMoney(
                summary?.feesAccruedUnpaid
              )}
            />


            <MetricCard
              label="Missed Payments"
              value={formatNumber(
                summary?.missedPayments
              )}
            />


            <MetricCard
              label="Overdue Payments"
              value={formatNumber(
                summary?.overduePayments
              )}
            />

          </div>

        </div>


        {/* ================================================== */}
        {/* FINANCIAL STATEMENT */}
        {/* ================================================== */}

        <FinancialStatementSection
          report={financialStatement}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />


        {/* ================================================== */}
        {/* DATA QUALITY */}
        {/* ================================================== */}

        <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">

          <h2 className="mb-4 text-lg font-semibold text-gray-900">
            Data Quality
          </h2>


          <div className="grid grid-cols-2 gap-4 md:grid-cols-5">


            <StatusItem
              label="Missing Borrower"
              value={summary?.loansMissingBorrower}
            />


            <StatusItem
              label="Missing National ID"
              value={summary?.borrowersMissingNationalId}
            />


            <StatusItem
              label="Missing Branch"
              value={summary?.loansMissingBranch}
            />


            <StatusItem
              label="Missing Currency"
              value={summary?.loansMissingCurrency}
            />


            <StatusItem
              label="Missing Schedule"
              value={summary?.loansMissingRepaymentSchedule}
            />

          </div>


          {summary?.dataQualityWarnings &&
            summary.dataQualityWarnings.length > 0 && (

            <div className="mt-5 rounded-lg border border-yellow-200 bg-yellow-50 p-4">

              <p className="font-semibold text-yellow-800">
                Validation warnings
              </p>

              <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-yellow-700">

                {summary.dataQualityWarnings.map(
                  (
                    warning,
                    index
                  ) => (

                    <li
                      key={`${warning}-${index}`}
                    >
                      {warning}
                    </li>

                  )
                )}

              </ul>

            </div>

          )}

        </div>


        {/* ================================================== */}
        {/* BREAKDOWNS */}
        {/* ================================================== */}

        <BreakdownTable
          title="Borrowers by Gender"
          rows={genderBreakdown}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />


        <BreakdownTable
          title="Loans by Loan Type"
          rows={loanTypeBreakdown}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />


        <BreakdownTable
          title="Loans by Branch"
          rows={branchBreakdown}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />


        {/* ================================================== */}
        {/* FOOTER */}
        {/* ================================================== */}

        <div className="pb-8 text-center text-xs text-gray-400">

          BNR regulatory report •{' '}
          {period}

          {summary?.reportReference && (
            <>
              {' • '}
              {summary.reportReference}
            </>
          )}

        </div>

      </div>

    </div>
  );
}


// ============================================================
// KPI CARD
// ============================================================

function KpiCard({
  label,
  value,
}: {
  label: string;
  value: string;
}) {

  return (

    <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">

      <p className="text-sm text-gray-500">
        {label}
      </p>

      <p className="mt-2 text-2xl font-bold text-gray-900">
        {value}
      </p>

    </div>
  );
}


// ============================================================
// METRIC CARD
// ============================================================

function MetricCard({
  label,
  value,
  secondary,
}: {
  label: string;
  value: string;
  secondary?: string;
}) {

  return (

    <div className="rounded-lg bg-gray-50 p-4">

      <p className="text-sm text-gray-500">
        {label}
      </p>

      <p className="mt-1 text-xl font-semibold text-gray-900">
        {value}
      </p>

      {secondary && (

        <p className="mt-1 text-xs text-gray-500">
          {secondary}
        </p>

      )}

    </div>
  );
}


// ============================================================
// STATUS ITEM
// ============================================================

function StatusItem({
  label,
  value,
}: {
  label: string;
  value?: number;
}) {

  return (

    <div className="rounded-lg bg-gray-50 p-4">

      <p className="text-sm text-gray-500">
        {label}
      </p>

      <p className="mt-1 text-xl font-semibold text-gray-900">
        {new Intl.NumberFormat(
          'en-US'
        ).format(
          Number(value ?? 0)
        )}
      </p>

    </div>
  );
}


// ============================================================
// FINANCIAL STATEMENT SECTION
// ============================================================

function FinancialStatementSection({
  report,
  formatMoney,
  formatNumber,
}: {
  report: BnrFinancialStatementReport | null;

  formatMoney: (
    value?: number
  ) => string;

  formatNumber: (
    value?: number
  ) => string;
}) {

  if (!report) {

    return (

      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">

        <h2 className="text-lg font-semibold text-gray-900">
          BNR Financial Statement
        </h2>

        <p className="mt-2 text-sm text-gray-500">
          Financial statement data is not available for this period.
        </p>

      </div>
    );
  }


  return (

    <div className="space-y-6">

      {/* ================================================== */}
      {/* HEADER */}
      {/* ================================================== */}

      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">

        <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">

          <div>

            <h2 className="text-lg font-semibold text-gray-900">
              BNR Financial Statement
            </h2>

            <p className="text-sm text-gray-500">
              Statement of financial position, income,
              expenses, cash flow and trial balance.
            </p>

          </div>


          <div className="text-sm text-gray-500">

            {report.periodStart ||
              '—'}

            {' → '}

            {report.periodEnd ||
              '—'}

          </div>

        </div>

      </div>


      {/* ================================================== */}
      {/* BALANCE SHEET */}
      {/* ================================================== */}

      <div className="rounded-xl border border-gray-200 bg-white shadow-sm">

        <div className="border-b border-gray-200 p-5">

          <h3 className="text-lg font-semibold text-gray-900">
            Statement of Financial Position
          </h3>

        </div>


        <FinancialRows
          title="Assets"
          rows={report.assets}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />


        <FinancialRows
          title="Liabilities"
          rows={report.liabilities}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />


        <FinancialRows
          title="Equity"
          rows={report.equity}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />


        <div className="grid grid-cols-1 gap-4 border-t border-gray-200 p-5 sm:grid-cols-2 lg:grid-cols-4">

          <MetricCard
            label="Total Assets"
            value={formatMoney(
              report.totalAssets
            )}
          />

          <MetricCard
            label="Total Liabilities"
            value={formatMoney(
              report.totalLiabilities
            )}
          />

          <MetricCard
            label="Total Equity"
            value={formatMoney(
              report.totalEquity
            )}
          />

          <MetricCard
            label="Current Period Net Income"
            value={formatMoney(
              report.currentPeriodNetIncome
            )}
          />

        </div>


        <div className="border-t border-gray-200 p-5">

          <BalanceIndicator
            label="Balance Sheet"
            balanced={
              report.balanceSheetBalanced
            }
          />

        </div>

      </div>


      {/* ================================================== */}
      {/* INCOME STATEMENT */}
      {/* ================================================== */}

      <div className="rounded-xl border border-gray-200 bg-white shadow-sm">

        <div className="border-b border-gray-200 p-5">

          <h3 className="text-lg font-semibold text-gray-900">
            Income Statement
          </h3>

        </div>


        <FinancialRows
          title="Income"
          rows={report.income}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />


        <FinancialRows
          title="Expenses"
          rows={report.expenses}
          formatMoney={formatMoney}
          formatNumber={formatNumber}
        />


        <div className="grid grid-cols-1 gap-4 border-t border-gray-200 p-5 sm:grid-cols-3">

          <MetricCard
            label="Total Income"
            value={formatMoney(
              report.totalIncome
            )}
          />

          <MetricCard
            label="Total Expenses"
            value={formatMoney(
              report.totalExpenses
            )}
          />

          <MetricCard
            label="Net Income"
            value={formatMoney(
              report.netIncome
            )}
          />

        </div>

      </div>


      {/* ================================================== */}
      {/* CASH FLOW */}
      {/* ================================================== */}

      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">

        <h3 className="mb-4 text-lg font-semibold text-gray-900">
          Cash Flow
        </h3>


        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">

          <MetricCard
            label="Cash Used For Lending"
            value={formatMoney(
              report.cashUsedForLending
            )}
          />

          <MetricCard
            label="Cash From Collections"
            value={formatMoney(
              report.cashFromCollections
            )}
          />

          <MetricCard
            label="Cash From Fees"
            value={formatMoney(
              report.cashFromFees
            )}
          />

          <MetricCard
            label="Other Cash Movement"
            value={formatMoney(
              report.otherCashMovement
            )}
          />

          <MetricCard
            label="Net Change In Cash"
            value={formatMoney(
              report.netChangeInCash
            )}
          />

        </div>

      </div>


      {/* ================================================== */}
      {/* TRIAL BALANCE */}
      {/* ================================================== */}

      <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">

        <h3 className="mb-4 text-lg font-semibold text-gray-900">
          Trial Balance
        </h3>


        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">

          <MetricCard
            label="Total Debit"
            value={formatMoney(
              report.trialBalanceDebit
            )}
          />

          <MetricCard
            label="Total Credit"
            value={formatMoney(
              report.trialBalanceCredit
            )}
          />

          <BalanceIndicator
            label="Trial Balance"
            balanced={
              report.trialBalanceBalanced
            }
          />

        </div>

      </div>

    </div>
  );
}


// ============================================================
// FINANCIAL ROWS
// ============================================================

function FinancialRows({
  title,
  rows,
  formatMoney,
  formatNumber,
}: {
  title: string;

  rows?: FinancialStatementRow[];

  formatMoney: (
    value?: number
  ) => string;

  formatNumber: (
    value?: number
  ) => string;
}) {

  if (!rows || rows.length === 0) {

    return (

      <div className="border-b border-gray-200 p-5">

        <h4 className="mb-2 font-medium text-gray-800">
          {title}
        </h4>

        <p className="text-sm text-gray-500">
          No accounts reported.
        </p>

      </div>
    );
  }


  return (

    <div className="border-b border-gray-200">

      <div className="p-5 pb-2">

        <h4 className="font-medium text-gray-800">
          {title}
        </h4>

      </div>


      <div className="overflow-x-auto px-5 pb-5">

        <table className="min-w-full text-sm">

          <thead>

            <tr className="border-b border-gray-200 text-left text-xs uppercase text-gray-500">

              <th className="px-3 py-2">
                Code
              </th>

              <th className="px-3 py-2">
                Account
              </th>

              <th className="px-3 py-2 text-right">
                Balance
              </th>

            </tr>

          </thead>


          <tbody className="divide-y divide-gray-100">

            {rows.map(
              (
                row,
                index
              ) => {

                const value =
                  row.balance ??
                  row.amount ??
                  row.credit ??
                  row.debit ??
                  0;

                return (

                  <tr
                    key={`${row.code || row.name || 'row'}-${index}`}
                  >

                    <td className="px-3 py-2 text-gray-500">
                      {row.code ||
                        '—'}
                    </td>

                    <td className="px-3 py-2 font-medium text-gray-800">
                      {row.name ||
                        'Unnamed Account'}
                    </td>

                    <td className="px-3 py-2 text-right font-medium text-gray-900">
                      {formatMoney(
                        value
                      )}
                    </td>

                  </tr>

                );
              }
            )}

          </tbody>

        </table>

      </div>

    </div>
  );
}


// ============================================================
// BALANCE INDICATOR
// ============================================================

function BalanceIndicator({
  label,
  balanced,
}: {
  label: string;
  balanced?: boolean;
}) {

  const isBalanced =
    balanced === true;

  return (

    <div className="rounded-lg bg-gray-50 p-4">

      <p className="text-sm text-gray-500">
        {label}
      </p>

      <p
        className={
          isBalanced
            ? 'mt-1 text-lg font-semibold text-green-700'
            : 'mt-1 text-lg font-semibold text-red-700'
        }
      >
        {isBalanced
          ? 'Balanced'
          : 'Not Balanced'}
      </p>

    </div>
  );
}


// ============================================================
// BREAKDOWN TABLE
// ============================================================

function BreakdownTable({
  title,
  rows,
  formatMoney,
  formatNumber,
}: {
  title: string;

  rows: BreakdownRow[];

  formatMoney: (
    value?: number
  ) => string;

  formatNumber: (
    value?: number
  ) => string;
}) {

  return (

    <div className="rounded-xl border border-gray-200 bg-white shadow-sm">

      <div className="border-b border-gray-200 p-5">

        <h2 className="text-lg font-semibold text-gray-900">
          {title}
        </h2>

      </div>


      {rows.length === 0 ? (

        <div className="p-6 text-center text-sm text-gray-500">
          No data available for this period.
        </div>

      ) : (

        <div className="overflow-x-auto">

          <table className="min-w-full divide-y divide-gray-200">

            <thead className="bg-gray-50">

              <tr>

                <th className="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500">
                  Category
                </th>

                <th className="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-gray-500">
                  Loans
                </th>

                <th className="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-gray-500">
                  Amount
                </th>

              </tr>

            </thead>


            <tbody className="divide-y divide-gray-100 bg-white">

              {rows.map(
                (
                  row,
                  index
                ) => (

                  <tr
                    key={`${row.label}-${index}`}
                    className="hover:bg-gray-50"
                  >

                    <td className="whitespace-nowrap px-5 py-3 text-sm font-medium text-gray-900">
                      {row.label}
                    </td>

                    <td className="whitespace-nowrap px-5 py-3 text-right text-sm text-gray-600">
                      {formatNumber(
                        row.count
                      )}
                    </td>

                    <td className="whitespace-nowrap px-5 py-3 text-right text-sm font-medium text-gray-900">
                      {formatMoney(
                        row.amount
                      )}
                    </td>

                  </tr>

                )
              )}

            </tbody>

          </table>

        </div>

      )}

    </div>
  );
}


// ============================================================
// PAR 30 AMOUNT
// ============================================================

function getPar30Amount(
  summary: BnrSummary | null
): number {

  if (!summary) {
    return 0;
  }

  return (
    Number(summary.par31To60Amount ?? 0) +
    Number(summary.par61To90Amount ?? 0) +
    Number(summary.par91To180Amount ?? 0) +
    Number(summary.par181To365Amount ?? 0) +
    Number(summary.parOver365Amount ?? 0)
  );
}


// ============================================================
// PAR 60 AMOUNT
// ============================================================

function getPar60Amount(
  summary: BnrSummary | null
): number {

  if (!summary) {
    return 0;
  }

  return (
    Number(summary.par61To90Amount ?? 0) +
    Number(summary.par91To180Amount ?? 0) +
    Number(summary.par181To365Amount ?? 0) +
    Number(summary.parOver365Amount ?? 0)
  );
}


// ============================================================
// PAR 90 AMOUNT
// ============================================================

function getPar90Amount(
  summary: BnrSummary | null
): number {

  if (!summary) {
    return 0;
  }

  return (
    Number(summary.par91To180Amount ?? 0) +
    Number(summary.par181To365Amount ?? 0) +
    Number(summary.parOver365Amount ?? 0)
  );
}