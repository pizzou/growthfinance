'use client';

import { useEffect, useState, useCallback } from 'react';
import {
  accountingApi,
  bankAccountApi,
  branchApi,
} from '@/services/api';
import { PageSpinner } from '@/components/ui/Skeleton';
import { useAuth } from '@/hooks/useAuth';

/* =========================================================
   TYPES
========================================================= */

interface Account {
  id: number;
  code: string;
  name: string;
  type: 'ASSET' | 'LIABILITY' | 'EQUITY' | 'INCOME' | 'EXPENSE';
  normalBalance: 'DEBIT' | 'CREDIT';
  active: boolean;
}

interface JournalLine {
  id: number;
  account: Account;
  debit: number;
  credit: number;
  description?: string;
}

interface JournalEntryRow {
  id: number;
  entryDate: string;
  reference: string;
  sourceType: string;
  description: string;
  createdBy: string;
  reversed: boolean;
  lines: JournalLine[];
  branchName?: string;
}

interface TrialBalanceRow {
  code: string;
  name: string;
  type: string;
  debit: number;
  credit: number;
}

interface TrialBalance {
  accounts: TrialBalanceRow[];
  totalDebit: number;
  totalCredit: number;
  balanced: boolean;
}

interface StatementRow {
  code: string;
  name: string;
  balance: number;
}

interface BalanceSheet {
  assets: StatementRow[];
  liabilities: StatementRow[];
  equity: StatementRow[];
  currentPeriodNetIncome: number;
  totalAssets: number;
  totalLiabilities: number;
  totalEquity: number;
  balanced: boolean;
}

interface PnlRow {
  code: string;
  name: string;
  amount: number;
}

interface ProfitAndLoss {
  income: PnlRow[];
  expense: PnlRow[];
  totalIncome: number;
  totalExpense: number;
  netIncome: number;
}

interface CashFlow {
  cashUsedForLending: number;
  cashFromCollections: number;
  cashFromFees: number;
  otherCashMovement: number;
  netChangeInCash: number;
}

interface BranchSummaryRow {
  branch: string;
  disbursed: number;
  collected: number;
  feeIncome: number;
}

interface BranchRow {
  id: number;
  name: string;
}

interface BankAccountRow {
  id: number;
  name: string;
  accountType: string;
  bankName?: string;
  accountNumber?: string;
  branchName?: string;
  glAccountCode: string;
  active: boolean;
  balance: number;
}

/* =========================================================
   CONSTANTS
========================================================= */

const TYPE_COLORS: Record<string, string> = {
  ASSET: 'bg-blue-50 text-blue-700',
  LIABILITY: 'bg-orange-50 text-orange-700',
  EQUITY: 'bg-purple-50 text-purple-700',
  INCOME: 'bg-green-50 text-green-700',
  EXPENSE: 'bg-red-50 text-red-700',
};

const TABS = [
  'Trial Balance',
  'Balance Sheet',
  'Profit & Loss',
  'Cash Flow',
  'Chart of Accounts',
  'Journal',
  'Bank Accounts',
  'Branches',
] as const;

type Tab = typeof TABS[number];

/* =========================================================
   HELPERS
========================================================= */

function formatMoney(value: number, currency: string) {
  return `${currency} ${new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value ?? 0)}`;
}

function unwrapApiData<T>(response: any): T {
  if (response?.data?.data !== undefined) {
    return response.data.data as T;
  }

  if (response?.data !== undefined) {
    return response.data as T;
  }

  return response as T;
}

function normalizeBranchList(response: any): BranchRow[] {
  const data = unwrapApiData<any>(response);

  if (Array.isArray(data)) {
    return data
      .map((item: any) => ({
        id: Number(item?.id),
        name: String(item?.name ?? ''),
      }))
      .filter((item: BranchRow) => item.id && item.name);
  }

  if (Array.isArray(data?.content)) {
    return data.content
      .map((item: any) => ({
        id: Number(item?.id),
        name: String(item?.name ?? ''),
      }))
      .filter((item: BranchRow) => item.id && item.name);
  }

  return [];
}

/* =========================================================
   MAIN PAGE
========================================================= */

export default function AccountingPage() {
  const { currency } = useAuth();

  const [tab, setTab] = useState<Tab>('Trial Balance');

  const [accounts, setAccounts] = useState<Account[]>([]);
  const [journal, setJournal] = useState<JournalEntryRow[]>([]);
  const [trial, setTrial] = useState<TrialBalance | null>(null);
  const [balanceSheet, setBalanceSheet] =
    useState<BalanceSheet | null>(null);
  const [pnl, setPnl] = useState<ProfitAndLoss | null>(null);
  const [cashFlow, setCashFlow] = useState<CashFlow | null>(null);
  const [branchSummary, setBranchSummary] =
    useState<BranchSummaryRow[]>([]);
  const [bankAccounts, setBankAccounts] =
    useState<BankAccountRow[]>([]);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [expanded, setExpanded] = useState<number | null>(null);

  /* Bank account modals */
  const [showBankAccountForm, setShowBankAccountForm] =
    useState(false);

  const [showTransactionForm, setShowTransactionForm] =
    useState(false);

  const [showTransferForm, setShowTransferForm] =
    useState(false);

  const [selectedBankAccount, setSelectedBankAccount] =
    useState<BankAccountRow | null>(null);

  /* =========================================================
     LOAD ACCOUNTING DATA
  ========================================================= */

  const loadAll = useCallback(async () => {
    setLoading(true);
    setError('');

    try {
      const [
        accountsResponse,
        journalResponse,
        trialResponse,
        balanceSheetResponse,
        pnlResponse,
        cashFlowResponse,
        branchSummaryResponse,
        bankAccountsResponse,
      ] = await Promise.all([
        accountingApi.chartOfAccounts().catch(() => []),
        accountingApi.journal().catch(() => []),
        accountingApi.trialBalance().catch(() => null),
        accountingApi.balanceSheet().catch(() => null),
        accountingApi.profitAndLoss().catch(() => null),
        accountingApi.cashFlow().catch(() => null),
        accountingApi.branchSummary().catch(() => []),
        bankAccountApi.list().catch(() => []),
      ]);

      setAccounts(
        unwrapApiData<Account[]>(accountsResponse) ?? []
      );

      setJournal(
        unwrapApiData<JournalEntryRow[]>(journalResponse) ?? []
      );

      setTrial(
        unwrapApiData<TrialBalance | null>(trialResponse)
      );

      setBalanceSheet(
        unwrapApiData<BalanceSheet | null>(balanceSheetResponse)
      );

      setPnl(
        unwrapApiData<ProfitAndLoss | null>(pnlResponse)
      );

      setCashFlow(
        unwrapApiData<CashFlow | null>(cashFlowResponse)
      );

      setBranchSummary(
        unwrapApiData<BranchSummaryRow[]>(branchSummaryResponse) ?? []
      );

      setBankAccounts(
        unwrapApiData<BankAccountRow[]>(bankAccountsResponse) ?? []
      );
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Could not load accounting data.'
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  /* =========================================================
     REVERSE JOURNAL ENTRY
  ========================================================= */

  const handleReverse = async (id: number) => {
    const reason =
      window.prompt(
        'Reason for reversing this entry (optional):'
      ) ?? '';

    try {
      await accountingApi.reverseEntry(
        id,
        reason || undefined
      );

      await loadAll();
    } catch (err: unknown) {
      const message =
        err instanceof Error
          ? err.message
          : 'Could not reverse entry';

      setError(message);
    }
  };

  /* =========================================================
     BANK ACCOUNT CALLBACKS
  ========================================================= */

  const handleBankAccountSaved = async () => {
    setShowBankAccountForm(false);
    await loadAll();
  };

  const handleTransactionSaved = async () => {
    setShowTransactionForm(false);
    setSelectedBankAccount(null);
    await loadAll();
  };

  const handleTransferSaved = async () => {
    setShowTransferForm(false);
    await loadAll();
  };

  /* =========================================================
     LOADING
  ========================================================= */

  if (loading) {
    return <PageSpinner />;
  }

  /* =========================================================
     DERIVED BANK ACCOUNT VALUES
  ========================================================= */

  const activeBankAccounts = bankAccounts.filter(
    account => account.active
  );

  const totalCashPosition = bankAccounts.reduce(
    (sum, account) => sum + (account.balance || 0),
    0
  );

  const totalBankBalance = bankAccounts
    .filter(account => account.accountType === 'BANK')
    .reduce(
      (sum, account) => sum + (account.balance || 0),
      0
    );

  const totalCashBalance = bankAccounts
    .filter(account => account.accountType === 'CASH')
    .reduce(
      (sum, account) => sum + (account.balance || 0),
      0
    );

  /* =========================================================
     RENDER
  ========================================================= */

  return (
    <div className="space-y-6">

      {/* =====================================================
          PAGE HEADER
      ===================================================== */}

      <div>
        <h1 className="text-2xl font-bold text-gray-900">
          Accounting
        </h1>

        <p className="text-gray-500 text-sm mt-1">
          General ledger, financial statements, cash management,
          and accounting controls — {currency}.
        </p>
      </div>

      {/* =====================================================
          ERROR
      ===================================================== */}

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700
                        text-sm rounded-lg px-4 py-3 flex items-start
                        justify-between gap-4">

          <div>
            <div className="font-semibold">
              Accounting error
            </div>

            <div className="mt-0.5">
              {error}
            </div>
          </div>

          <button
            onClick={() => setError('')}
            className="text-red-400 hover:text-red-600"
          >
            ×
          </button>

        </div>
      )}

      {/* =====================================================
          TABS
      ===================================================== */}

      <div className="flex gap-1 border-b border-gray-200 overflow-x-auto">

        {TABS.map(currentTab => (
          <button
            key={currentTab}
            onClick={() => setTab(currentTab)}
            className={`
              px-4 py-2.5 text-sm font-semibold
              border-b-2 whitespace-nowrap transition-colors
              ${
                tab === currentTab
                  ? 'border-[#0D6B3E] text-[#0D6B3E]'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
              }
            `}
          >
            {currentTab}
          </button>
        ))}

      </div>

      {/* =====================================================
          TRIAL BALANCE
      ===================================================== */}

      {tab === 'Trial Balance' && trial && (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">

          <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">

            <div>
              <div className="font-bold text-gray-900">
                Trial Balance
              </div>

              <div className="text-xs text-gray-500 mt-0.5">
                Verification of debit and credit balances
              </div>
            </div>

            <span
              className={`
                text-xs font-bold px-3 py-1.5 rounded-full
                ${
                  trial.balanced
                    ? 'bg-green-50 text-green-700'
                    : 'bg-red-50 text-red-700'
                }
              `}
            >
              {trial.balanced
                ? '✓ Balanced'
                : '⚠ Out of Balance'}
            </span>

          </div>

          <div className="overflow-x-auto">

            <table className="w-full text-sm">

              <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wider">

                <tr>
                  <th className="text-left px-5 py-2.5 font-semibold">
                    Code
                  </th>

                  <th className="text-left px-5 py-2.5 font-semibold">
                    Account
                  </th>

                  <th className="text-left px-5 py-2.5 font-semibold">
                    Type
                  </th>

                  <th className="text-right px-5 py-2.5 font-semibold">
                    Debit
                  </th>

                  <th className="text-right px-5 py-2.5 font-semibold">
                    Credit
                  </th>
                </tr>

              </thead>

              <tbody className="divide-y divide-gray-100">

                {trial.accounts.map(row => (
                  <tr
                    key={row.code}
                    className="hover:bg-gray-50"
                  >

                    <td className="px-5 py-2.5 font-mono text-gray-500">
                      {row.code}
                    </td>

                    <td className="px-5 py-2.5 font-medium text-gray-900">
                      {row.name}
                    </td>

                    <td className="px-5 py-2.5">

                      <span
                        className={`
                          text-[10px] font-bold px-2 py-0.5 rounded
                          ${
                            TYPE_COLORS[row.type] ||
                            'bg-gray-100 text-gray-600'
                          }
                        `}
                      >
                        {row.type}
                      </span>

                    </td>

                    <td className="px-5 py-2.5 text-right font-mono">
                      {row.debit
                        ? formatMoney(row.debit, currency)
                        : ''}
                    </td>

                    <td className="px-5 py-2.5 text-right font-mono">
                      {row.credit
                        ? formatMoney(row.credit, currency)
                        : ''}
                    </td>

                  </tr>
                ))}

              </tbody>

              <tfoot className="bg-gray-50 font-bold border-t-2 border-gray-200">

                <tr>

                  <td
                    colSpan={3}
                    className="px-5 py-3 text-right"
                  >
                    Totals
                  </td>

                  <td className="px-5 py-3 text-right font-mono">
                    {formatMoney(
                      trial.totalDebit,
                      currency
                    )}
                  </td>

                  <td className="px-5 py-3 text-right font-mono">
                    {formatMoney(
                      trial.totalCredit,
                      currency
                    )}
                  </td>

                </tr>

              </tfoot>

            </table>

          </div>

        </div>
      )}

      {/* =====================================================
          BALANCE SHEET
      ===================================================== */}

      {tab === 'Balance Sheet' && balanceSheet && (
        <div className="bg-white rounded-xl border border-gray-200 p-5 space-y-6">

          <div className="flex items-center justify-between">

            <div>
              <div className="font-bold text-gray-900">
                Balance Sheet
              </div>

              <div className="text-xs text-gray-500 mt-0.5">
                Financial position as of today
              </div>
            </div>

            <span
              className={`
                text-xs font-bold px-3 py-1.5 rounded-full
                ${
                  balanceSheet.balanced
                    ? 'bg-green-50 text-green-700'
                    : 'bg-red-50 text-red-700'
                }
              `}
            >
              {balanceSheet.balanced
                ? '✓ Balanced'
                : '⚠ Out of Balance'}
            </span>

          </div>

          {([
            [
              'Assets',
              balanceSheet.assets,
              balanceSheet.totalAssets,
            ],
            [
              'Liabilities',
              balanceSheet.liabilities,
              balanceSheet.totalLiabilities,
            ],
            [
              'Equity (incl. current period net income)',
              balanceSheet.equity,
              balanceSheet.totalEquity,
            ],
          ] as const).map(([label, rows, total]) => (

            <div key={label}>

              <div className="text-sm font-bold text-gray-700 mb-2">
                {label}
              </div>

              <div className="divide-y divide-gray-100 border
                              border-gray-100 rounded-lg overflow-hidden">

                {rows.map(row => (
                  <div
                    key={row.code}
                    className="flex justify-between px-4 py-2 text-sm"
                  >
                    <span className="text-gray-600">
                      {row.code} — {row.name}
                    </span>

                    <span className="font-mono">
                      {formatMoney(row.balance, currency)}
                    </span>
                  </div>
                ))}

                {label.startsWith('Equity') && (
                  <div className="flex justify-between px-4 py-2 text-sm bg-gray-50">

                    <span className="text-gray-600">
                      Current Period Net Income
                    </span>

                    <span className="font-mono">
                      {formatMoney(
                        balanceSheet.currentPeriodNetIncome,
                        currency
                      )}
                    </span>

                  </div>
                )}

                <div className="flex justify-between px-4 py-2 text-sm
                                font-bold bg-gray-50 border-t border-gray-200">

                  <span>
                    Total {label.split(' ')[0]}
                  </span>

                  <span className="font-mono">
                    {formatMoney(total, currency)}
                  </span>

                </div>

              </div>

            </div>

          ))}

        </div>
      )}

      {/* =====================================================
          PROFIT & LOSS
      ===================================================== */}

      {tab === 'Profit & Loss' && pnl && (
        <div className="bg-white rounded-xl border border-gray-200 p-5 space-y-6">

          <div>
            <div className="font-bold text-gray-900">
              Profit & Loss
            </div>

            <div className="text-xs text-gray-500 mt-0.5">
              Current month to date
            </div>
          </div>

          {([
            [
              'Income',
              pnl.income,
              pnl.totalIncome,
            ],
            [
              'Expense',
              pnl.expense,
              pnl.totalExpense,
            ],
          ] as const).map(([label, rows, total]) => (

            <div key={label}>

              <div className="text-sm font-bold text-gray-700 mb-2">
                {label}
              </div>

              <div className="divide-y divide-gray-100 border
                              border-gray-100 rounded-lg overflow-hidden">

                {rows.map(row => (
                  <div
                    key={row.code}
                    className="flex justify-between px-4 py-2 text-sm"
                  >

                    <span className="text-gray-600">
                      {row.code} — {row.name}
                    </span>

                    <span className="font-mono">
                      {formatMoney(row.amount, currency)}
                    </span>

                  </div>
                ))}

                {rows.length === 0 && (
                  <div className="px-4 py-3 text-sm text-gray-400">
                    No activity this period.
                  </div>
                )}

                <div className="flex justify-between px-4 py-2 text-sm
                                font-bold bg-gray-50 border-t border-gray-200">

                  <span>
                    Total {label}
                  </span>

                  <span className="font-mono">
                    {formatMoney(total, currency)}
                  </span>

                </div>

              </div>

            </div>

          ))}

          <div
            className={`
              flex justify-between px-4 py-3 rounded-lg
              font-bold text-sm
              ${
                pnl.netIncome >= 0
                  ? 'bg-green-50 text-green-700'
                  : 'bg-red-50 text-red-700'
              }
            `}
          >

            <span>
              Net Income
            </span>

            <span className="font-mono">
              {formatMoney(pnl.netIncome, currency)}
            </span>

          </div>

        </div>
      )}

      {/* =====================================================
          CASH FLOW
      ===================================================== */}

      {tab === 'Cash Flow' && cashFlow && (
        <div className="bg-white rounded-xl border border-gray-200 p-5 space-y-2">

          <div className="mb-3">

            <div className="font-bold text-gray-900">
              Cash Flow
            </div>

            <div className="text-xs text-gray-500 mt-0.5">
              Current month to date
            </div>

          </div>

          {[
            [
              'Cash Used for Lending (disbursements)',
              cashFlow.cashUsedForLending,
            ],
            [
              'Cash From Collections (principal + interest + penalties)',
              cashFlow.cashFromCollections,
            ],
            [
              'Cash From Fees',
              cashFlow.cashFromFees,
            ],
            [
              'Other Cash Movement',
              cashFlow.otherCashMovement,
            ],
          ].map(([label, value]) => (

            <div
              key={label as string}
              className="flex justify-between px-4 py-2 text-sm
                         border-b border-gray-100"
            >

              <span className="text-gray-600">
                {label}
              </span>

              <span className="font-mono">
                {formatMoney(
                  value as number,
                  currency
                )}
              </span>

            </div>

          ))}

          <div className="flex justify-between px-4 py-3 rounded-lg
                          font-bold text-sm bg-gray-50 mt-2">

            <span>
              Net Change in Cash
            </span>

            <span className="font-mono">
              {formatMoney(
                cashFlow.netChangeInCash,
                currency
              )}
            </span>

          </div>

        </div>
      )}

      {/* =====================================================
          CHART OF ACCOUNTS
      ===================================================== */}

      {tab === 'Chart of Accounts' && (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">

          <div className="px-5 py-4 border-b border-gray-100">

            <h2 className="font-bold text-gray-900">
              Chart of Accounts
            </h2>

            <p className="text-xs text-gray-500 mt-0.5">
              General ledger accounts used by the organization.
            </p>

          </div>

          <div className="overflow-x-auto">

            <table className="w-full text-sm">

              <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wider">

                <tr>

                  <th className="text-left px-5 py-2.5 font-semibold">
                    Code
                  </th>

                  <th className="text-left px-5 py-2.5 font-semibold">
                    Account Name
                  </th>

                  <th className="text-left px-5 py-2.5 font-semibold">
                    Type
                  </th>

                  <th className="text-left px-5 py-2.5 font-semibold">
                    Normal Balance
                  </th>

                  <th className="text-left px-5 py-2.5 font-semibold">
                    Status
                  </th>

                </tr>

              </thead>

              <tbody className="divide-y divide-gray-100">

                {accounts.map(account => (

                  <tr
                    key={account.id}
                    className="hover:bg-gray-50"
                  >

                    <td className="px-5 py-2.5 font-mono text-gray-500">
                      {account.code}
                    </td>

                    <td className="px-5 py-2.5 font-medium text-gray-900">
                      {account.name}
                    </td>

                    <td className="px-5 py-2.5">

                      <span
                        className={`
                          text-[10px] font-bold px-2 py-0.5 rounded
                          ${
                            TYPE_COLORS[account.type] ||
                            'bg-gray-100 text-gray-600'
                          }
                        `}
                      >
                        {account.type}
                      </span>

                    </td>

                    <td className="px-5 py-2.5 text-gray-600">
                      {account.normalBalance}
                    </td>

                    <td className="px-5 py-2.5">

                      <span
                        className={`
                          text-[10px] font-bold px-2 py-0.5 rounded
                          ${
                            account.active
                              ? 'bg-green-50 text-green-700'
                              : 'bg-gray-100 text-gray-500'
                          }
                        `}
                      >
                        {account.active
                          ? 'Active'
                          : 'Inactive'}
                      </span>

                    </td>

                  </tr>

                ))}

                {accounts.length === 0 && (
                  <tr>
                    <td
                      colSpan={5}
                      className="px-5 py-8 text-center text-gray-400"
                    >
                      No accounts found.
                    </td>
                  </tr>
                )}

              </tbody>

            </table>

          </div>

        </div>
      )}

      {/* =====================================================
          JOURNAL
      ===================================================== */}

      {tab === 'Journal' && (
        <div className="bg-white rounded-xl border border-gray-200 divide-y divide-gray-100">

          {journal.length === 0 && (
            <div className="px-5 py-8 text-center text-gray-400 text-sm">
              No journal entries yet.
            </div>
          )}

          {journal.map(entry => (

            <div key={entry.id}>

              <div
                role="button"
                tabIndex={0}
                onClick={() =>
                  setExpanded(
                    expanded === entry.id
                      ? null
                      : entry.id
                  )
                }
                onKeyDown={event => {
                  if (
                    event.key === 'Enter' ||
                    event.key === ' '
                  ) {
                    setExpanded(
                      expanded === entry.id
                        ? null
                        : entry.id
                    );
                  }
                }}
                className="w-full flex items-center justify-between
                           px-5 py-3.5 hover:bg-gray-50 text-left
                           cursor-pointer"
              >

                <div className="flex items-center gap-3 min-w-0">

                  <span className="text-xs text-gray-400 w-24 shrink-0">
                    {new Date(
                      entry.entryDate
                    ).toLocaleDateString()}
                  </span>

                  <span className="text-[10px] font-bold px-2 py-0.5
                                   rounded bg-gray-100 text-gray-600">
                    {entry.sourceType}
                  </span>

                  <span className="text-sm font-medium text-gray-900 truncate">
                    {entry.description}
                  </span>

                  {entry.branchName && (
                    <span className="text-[10px] font-bold px-2 py-0.5
                                     rounded bg-indigo-50 text-indigo-600">
                      {entry.branchName}
                    </span>
                  )}

                  {entry.reversed && (
                    <span className="text-[10px] font-bold px-2 py-0.5
                                     rounded bg-red-50 text-red-600">
                      REVERSED
                    </span>
                  )}

                </div>

                <div className="flex items-center gap-3 text-xs text-gray-400 shrink-0">

                  <code>
                    {entry.reference}
                  </code>

                  {!entry.reversed &&
                    entry.sourceType !== 'REVERSAL' && (
                      <button
                        onClick={event => {
                          event.stopPropagation();
                          handleReverse(entry.id);
                        }}
                        className="text-red-500 hover:text-red-700
                                   font-semibold border border-red-100
                                   bg-white hover:bg-red-50 px-2 py-1 rounded"
                      >
                        Reverse
                      </button>
                    )}

                  <span>
                    {expanded === entry.id
                      ? '▲'
                      : '▼'}
                  </span>

                </div>

              </div>

              {expanded === entry.id && (
                <div className="px-5 pb-4">

                  <table className="w-full text-xs bg-gray-50
                                    rounded-lg overflow-hidden">

                    <thead className="text-gray-500 uppercase">

                      <tr>

                        <th className="text-left px-3 py-2">
                          Account
                        </th>

                        <th className="text-right px-3 py-2">
                          Debit
                        </th>

                        <th className="text-right px-3 py-2">
                          Credit
                        </th>

                      </tr>

                    </thead>

                    <tbody className="divide-y divide-gray-200">

                      {entry.lines?.map(line => (

                        <tr key={line.id}>

                          <td className="px-3 py-2">
                            {line.account?.code}
                            {' — '}
                            {line.account?.name}
                          </td>

                          <td className="px-3 py-2 text-right font-mono">
                            {line.debit
                              ? formatMoney(
                                  line.debit,
                                  currency
                                )
                              : ''}
                          </td>

                          <td className="px-3 py-2 text-right font-mono">
                            {line.credit
                              ? formatMoney(
                                  line.credit,
                                  currency
                                )
                              : ''}
                          </td>

                        </tr>

                      ))}

                    </tbody>

                  </table>

                </div>
              )}

            </div>

          ))}

        </div>
      )}

      {/* =====================================================
          BANK ACCOUNTS
      ===================================================== */}

      {tab === 'Bank Accounts' && (
        <div className="space-y-5">

          {/* Header */}

          <div className="flex flex-col sm:flex-row sm:items-center
                          sm:justify-between gap-4">

            <div>

              <h2 className="text-lg font-bold text-gray-900">
                Bank & Cash Accounts
              </h2>

              <p className="text-sm text-gray-500 mt-1">
                Manage the institution's bank accounts, cash drawers,
                and payment sources.
              </p>

            </div>

            <button
              onClick={() =>
                setShowBankAccountForm(true)
              }
              className="inline-flex items-center justify-center
                         gap-2 px-4 py-2.5 bg-[#0D6B3E]
                         hover:bg-[#095631] text-white
                         text-sm font-semibold rounded-lg transition"
            >
              <span className="text-lg leading-none">
                +
              </span>

              Add Account
            </button>

          </div>

          {/* Summary cards */}

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">

            <div className="bg-white border border-gray-200 rounded-xl p-4">

              <div className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
                Total Accounts
              </div>

              <div className="mt-2 text-2xl font-bold text-gray-900">
                {bankAccounts.length}
              </div>

              <div className="mt-1 text-xs text-gray-500">
                Bank and cash accounts
              </div>

            </div>

            <div className="bg-white border border-gray-200 rounded-xl p-4">

              <div className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
                Active Accounts
              </div>

              <div className="mt-2 text-2xl font-bold text-green-700">
                {activeBankAccounts.length}
              </div>

              <div className="mt-1 text-xs text-gray-500">
                Available as payment sources
              </div>

            </div>

            <div className="bg-white border border-gray-200 rounded-xl p-4">

              <div className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
                Bank Balance
              </div>

              <div className="mt-2 text-xl font-bold text-gray-900">
                {formatMoney(
                  totalBankBalance,
                  currency
                )}
              </div>

              <div className="mt-1 text-xs text-gray-500">
                All bank accounts
              </div>

            </div>

            <div className="bg-white border border-gray-200 rounded-xl p-4">

              <div className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
                Total Cash Position
              </div>

              <div className="mt-2 text-xl font-bold text-gray-900">
                {formatMoney(
                  totalCashPosition,
                  currency
                )}
              </div>

              <div className="mt-1 text-xs text-gray-500">
                Bank + physical cash
              </div>

            </div>

          </div>

          {/* Accounts table */}

          <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">

            <div className="px-5 py-4 border-b border-gray-100
                            flex flex-col sm:flex-row
                            sm:items-center sm:justify-between gap-3">

              <div>

                <h3 className="font-bold text-gray-900">
                  Accounts
                </h3>

                <p className="text-xs text-gray-500 mt-0.5">
                  These accounts can be selected as payment
                  sources for expenses and cashbook transactions.
                </p>

              </div>

              <button
                onClick={() =>
                  setShowTransferForm(true)
                }
                disabled={
                  activeBankAccounts.length < 2
                }
                className="px-3 py-2 text-xs font-semibold
                           border border-gray-300 rounded-lg
                           text-gray-700 hover:bg-gray-50
                           disabled:opacity-40
                           disabled:cursor-not-allowed"
              >
                Transfer Funds
              </button>

            </div>

            {bankAccounts.length === 0 ? (

              <div className="px-6 py-14 text-center">

                <div className="mx-auto w-14 h-14 rounded-full
                                bg-gray-100 flex items-center
                                justify-center text-gray-400 text-2xl">
                  $
                </div>

                <h3 className="mt-4 text-sm font-bold text-gray-900">
                  No bank or cash accounts configured
                </h3>

                <p className="mt-1 text-sm text-gray-500
                              max-w-md mx-auto">
                  Add the institution's bank accounts or cash
                  drawers before recording expenses, deposits,
                  withdrawals, or transfers.
                </p>

                <button
                  onClick={() =>
                    setShowBankAccountForm(true)
                  }
                  className="mt-5 px-4 py-2.5 bg-[#0D6B3E]
                             hover:bg-[#095631] text-white
                             text-sm font-semibold rounded-lg"
                >
                  + Add First Account
                </button>

              </div>

            ) : (

              <div className="overflow-x-auto">

                <table className="w-full text-sm">

                  <thead className="bg-gray-50 text-gray-500
                                    text-xs uppercase tracking-wider">

                    <tr>

                      <th className="text-left px-5 py-3 font-semibold">
                        Account
                      </th>

                      <th className="text-left px-5 py-3 font-semibold">
                        Type
                      </th>

                      <th className="text-left px-5 py-3 font-semibold">
                        Bank / Details
                      </th>

                      <th className="text-left px-5 py-3 font-semibold">
                        Branch
                      </th>

                      <th className="text-left px-5 py-3 font-semibold">
                        GL Code
                      </th>

                      <th className="text-right px-5 py-3 font-semibold">
                        Balance
                      </th>

                      <th className="text-center px-5 py-3 font-semibold">
                        Status
                      </th>

                      <th className="text-right px-5 py-3 font-semibold">
                        Actions
                      </th>

                    </tr>

                  </thead>

                  <tbody className="divide-y divide-gray-100">

                    {bankAccounts.map(account => (

                      <tr
                        key={account.id}
                        className="hover:bg-gray-50"
                      >

                        {/* Account */}

                        <td className="px-5 py-4">

                          <div className="font-semibold text-gray-900">
                            {account.name}
                          </div>

                          <div className="text-xs text-gray-400 mt-0.5">
                            Account #{account.id}
                          </div>

                        </td>

                        {/* Type */}

                        <td className="px-5 py-4">

                          <span
                            className={`
                              inline-flex items-center px-2 py-1
                              rounded-full text-[10px] font-bold uppercase
                              ${
                                account.accountType === 'BANK'
                                  ? 'bg-blue-50 text-blue-700'
                                  : 'bg-amber-50 text-amber-700'
                              }
                            `}
                          >
                            {account.accountType}
                          </span>

                        </td>

                        {/* Bank */}

                        <td className="px-5 py-4">

                          {account.accountType === 'BANK' ? (

                            <>
                              <div className="text-gray-700">
                                {account.bankName ||
                                  'Bank not specified'}
                              </div>

                              {account.accountNumber && (
                                <div className="text-xs text-gray-400 mt-0.5">
                                  {account.accountNumber}
                                </div>
                              )}
                            </>

                          ) : (

                            <span className="text-gray-500">
                              Physical cash
                            </span>

                          )}

                        </td>

                        {/* Branch */}

                        <td className="px-5 py-4 text-gray-600">

                          {account.branchName ||
                            'Head Office / Organization-wide'}

                        </td>

                        {/* GL */}

                        <td className="px-5 py-4">

                          <span className="font-mono text-xs text-gray-500">
                            {account.glAccountCode}
                          </span>

                        </td>

                        {/* Balance */}

                        <td className="px-5 py-4 text-right">

                          <div className="font-mono font-semibold text-gray-900">
                            {formatMoney(
                              account.balance,
                              currency
                            )}
                          </div>

                        </td>

                        {/* Status */}

                        <td className="px-5 py-4 text-center">

                          <span
                            className={`
                              inline-flex items-center px-2 py-1
                              rounded-full text-[10px] font-bold
                              ${
                                account.active
                                  ? 'bg-green-50 text-green-700'
                                  : 'bg-gray-100 text-gray-500'
                              }
                            `}
                          >
                            {account.active
                              ? 'ACTIVE'
                              : 'INACTIVE'}
                          </span>

                        </td>

                        {/* Actions */}

                        <td className="px-5 py-4 text-right">

                          {account.active && (

                            <button
                              onClick={() => {
                                setSelectedBankAccount(account);
                                setShowTransactionForm(true);
                              }}
                              className="px-2.5 py-1.5 text-xs
                                         font-semibold text-gray-700
                                         border border-gray-300 rounded-md
                                         hover:bg-gray-50"
                            >
                              Transaction
                            </button>

                          )}

                        </td>

                      </tr>

                    ))}

                  </tbody>

                </table>

              </div>

            )}

          </div>

          {/* Account explanation */}

          <div className="bg-blue-50 border border-blue-100
                          rounded-xl p-4">

            <div className="flex items-start gap-3">

              <div className="w-8 h-8 rounded-full bg-blue-100
                              text-blue-700 flex items-center
                              justify-center font-bold text-sm shrink-0">
                i
              </div>

              <div>

                <h4 className="text-sm font-bold text-blue-900">
                  How payment sources work
                </h4>

                <p className="text-xs text-blue-800 mt-1 leading-5">
                  Each active bank or cash account represents a real
                  source of funds owned by the organization. When an
                  expense is recorded, the selected payment source is
                  used to identify which account paid the expense.
                  The account is also linked to its own General Ledger
                  account so that balances remain visible in the
                  accounting records.
                </p>

              </div>

            </div>

          </div>

        </div>
      )}

      {/* =====================================================
          BRANCHES
      ===================================================== */}

      {tab === 'Branches' && (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">

          <div className="px-5 py-4 border-b border-gray-100">

            <h2 className="font-bold text-gray-900">
              Branch Performance
            </h2>

            <p className="text-xs text-gray-500 mt-0.5">
              Lending and collection activity by branch.
            </p>

          </div>

          <div className="overflow-x-auto">

            <table className="w-full text-sm">

              <thead className="bg-gray-50 text-gray-500
                                text-xs uppercase tracking-wider">

                <tr>

                  <th className="text-left px-5 py-2.5 font-semibold">
                    Branch
                  </th>

                  <th className="text-right px-5 py-2.5 font-semibold">
                    Disbursed
                  </th>

                  <th className="text-right px-5 py-2.5 font-semibold">
                    Collected
                  </th>

                  <th className="text-right px-5 py-2.5 font-semibold">
                    Fee Income
                  </th>

                </tr>

              </thead>

              <tbody className="divide-y divide-gray-100">

                {branchSummary.map(row => (

                  <tr
                    key={row.branch}
                    className="hover:bg-gray-50"
                  >

                    <td className="px-5 py-2.5 font-medium text-gray-900">
                      {row.branch}
                    </td>

                    <td className="px-5 py-2.5 text-right font-mono">
                      {formatMoney(
                        row.disbursed,
                        currency
                      )}
                    </td>

                    <td className="px-5 py-2.5 text-right font-mono">
                      {formatMoney(
                        row.collected,
                        currency
                      )}
                    </td>

                    <td className="px-5 py-2.5 text-right font-mono">
                      {formatMoney(
                        row.feeIncome,
                        currency
                      )}
                    </td>

                  </tr>

                ))}

                {branchSummary.length === 0 && (

                  <tr>

                    <td
                      colSpan={4}
                      className="px-5 py-8 text-center text-gray-400"
                    >
                      No branch activity this period.
                    </td>

                  </tr>

                )}

              </tbody>

            </table>

          </div>

        </div>
      )}

      {/* =====================================================
          ADD BANK ACCOUNT MODAL
      ===================================================== */}

      {showBankAccountForm && (
        <AddBankAccountModal
          onClose={() =>
            setShowBankAccountForm(false)
          }
          onSaved={handleBankAccountSaved}
          currency={currency}
        />
      )}

      {/* =====================================================
          TRANSACTION MODAL
      ===================================================== */}

      {showTransactionForm && selectedBankAccount && (
        <BankTransactionModal
          account={selectedBankAccount}
          accounts={bankAccounts}
          onClose={() => {
            setShowTransactionForm(false);
            setSelectedBankAccount(null);
          }}
          onSaved={handleTransactionSaved}
          currency={currency}
        />
      )}

      {/* =====================================================
          TRANSFER MODAL
      ===================================================== */}

      {showTransferForm && (
        <TransferFundsModal
          accounts={bankAccounts}
          onClose={() =>
            setShowTransferForm(false)
          }
          onSaved={handleTransferSaved}
          currency={currency}
        />
      )}

    </div>
  );
}

/* =========================================================
   ADD BANK / CASH ACCOUNT MODAL
========================================================= */

function AddBankAccountModal({
  onClose,
  onSaved,
  currency,
}: {
  onClose: () => void;
  onSaved: () => void;
  currency: string;
}) {
  const [name, setName] = useState('');
  const [accountType, setAccountType] =
    useState<'BANK' | 'CASH'>('BANK');

  const [bankName, setBankName] = useState('');
  const [accountNumber, setAccountNumber] = useState('');
  const [openingBalance, setOpeningBalance] = useState('');
  const [branchId, setBranchId] = useState('');

  const [branches, setBranches] = useState<BranchRow[]>([]);

  const [loadingBranches, setLoadingBranches] =
    useState(true);

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  /* Load branches */

  useEffect(() => {
    let mounted = true;

    setLoadingBranches(true);

    branchApi
      .list()
      .then(response => {
        if (!mounted) return;

        setBranches(
          normalizeBranchList(response)
        );
      })
      .catch(() => {
        if (!mounted) return;
        setBranches([]);
      })
      .finally(() => {
        if (!mounted) return;
        setLoadingBranches(false);
      });

    return () => {
      mounted = false;
    };
  }, []);

  /* Submit */

  const handleSubmit = async (
    event: React.FormEvent
  ) => {
    event.preventDefault();

    setError('');

    const trimmedName = name.trim();
    const trimmedBankName = bankName.trim();
    const trimmedAccountNumber =
      accountNumber.trim();

    if (!trimmedName) {
      setError('Account name is required.');
      return;
    }

    if (
      accountType === 'BANK' &&
      !trimmedBankName
    ) {
      setError(
        'Bank name is required for a bank account.'
      );
      return;
    }

    if (
      accountType === 'BANK' &&
      !trimmedAccountNumber
    ) {
      setError(
        'Account number is required for a bank account.'
      );
      return;
    }

    const opening =
      openingBalance.trim() === ''
        ? 0
        : Number(openingBalance);

    if (!Number.isFinite(opening)) {
      setError(
        'Opening balance must be a valid number.'
      );
      return;
    }

    if (opening < 0) {
      setError(
        'Opening balance cannot be negative.'
      );
      return;
    }

    setSaving(true);

    try {
      await bankAccountApi.create({
        name: trimmedName,
        accountType,

        bankName:
          accountType === 'BANK'
            ? trimmedBankName
            : undefined,

        accountNumber:
          accountType === 'BANK'
            ? trimmedAccountNumber
            : undefined,

        openingBalance: opening,

        branchId: branchId
          ? Number(branchId)
          : undefined,
      });

      onSaved();
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Could not create account.'
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <ModalShell
      title="Add Bank or Cash Account"
      description="Configure an account that the institution can use for payments and cashbook transactions."
      onClose={onClose}
      maxWidth="max-w-lg"
    >

      <form onSubmit={handleSubmit}>

        <div className="p-6 space-y-5">

          {/* Error */}

          {error && (
            <div className="px-4 py-3 bg-red-50 border
                            border-red-200 text-red-700
                            text-sm rounded-lg">
              {error}
            </div>
          )}

          {/* Account name */}

          <div>

            <label className="block text-sm font-semibold
                              text-gray-700 mb-1.5">
              Account Name *
            </label>

            <input
              value={name}
              onChange={event =>
                setName(event.target.value)
              }
              placeholder={
                accountType === 'BANK'
                  ? 'e.g. Bank of Kigali - Main Account'
                  : 'e.g. Kigali Branch Petty Cash'
              }
              className="w-full px-4 py-2.5 border
                         border-gray-300 rounded-lg text-sm
                         focus:outline-none focus:ring-2
                         focus:ring-[#0D6B3E]"
            />

            <p className="text-xs text-gray-400 mt-1">
              Use a clear name that staff will recognize
              when selecting a payment source.
            </p>

          </div>

          {/* Account type */}

          <div>

            <label className="block text-sm font-semibold
                              text-gray-700 mb-1.5">
              Account Type *
            </label>

            <div className="grid grid-cols-2 gap-3">

              <button
                type="button"
                onClick={() =>
                  setAccountType('BANK')
                }
                className={`
                  p-4 rounded-lg border text-left transition
                  ${
                    accountType === 'BANK'
                      ? 'border-[#0D6B3E] bg-green-50 ring-1 ring-[#0D6B3E]'
                      : 'border-gray-300 hover:bg-gray-50'
                  }
                `}
              >

                <div className="font-semibold text-sm text-gray-900">
                  Bank Account
                </div>

                <div className="text-xs text-gray-500 mt-1">
                  Commercial bank or financial institution
                </div>

              </button>

              <button
                type="button"
                onClick={() =>
                  setAccountType('CASH')
                }
                className={`
                  p-4 rounded-lg border text-left transition
                  ${
                    accountType === 'CASH'
                      ? 'border-[#0D6B3E] bg-green-50 ring-1 ring-[#0D6B3E]'
                      : 'border-gray-300 hover:bg-gray-50'
                  }
                `}
              >

                <div className="font-semibold text-sm text-gray-900">
                  Cash Account
                </div>

                <div className="text-xs text-gray-500 mt-1">
                  Physical cash drawer or petty cash
                </div>

              </button>

            </div>

          </div>

          {/* Bank-specific fields */}

          {accountType === 'BANK' && (
            <>

              <div>

                <label className="block text-sm font-semibold
                                  text-gray-700 mb-1.5">
                  Bank Name *
                </label>

                <input
                  value={bankName}
                  onChange={event =>
                    setBankName(event.target.value)
                  }
                  placeholder="e.g. Bank of Kigali"
                  className="w-full px-4 py-2.5 border
                             border-gray-300 rounded-lg text-sm
                             focus:outline-none focus:ring-2
                             focus:ring-[#0D6B3E]"
                />

              </div>

              <div>

                <label className="block text-sm font-semibold
                                  text-gray-700 mb-1.5">
                  Account Number *
                </label>

                <input
                  value={accountNumber}
                  onChange={event =>
                    setAccountNumber(
                      event.target.value
                    )
                  }
                  placeholder="Enter bank account number"
                  className="w-full px-4 py-2.5 border
                             border-gray-300 rounded-lg text-sm
                             focus:outline-none focus:ring-2
                             focus:ring-[#0D6B3E]"
                />

              </div>

            </>
          )}

          {/* Branch */}

          <div>

            <label className="block text-sm font-semibold
                              text-gray-700 mb-1.5">
              Branch
            </label>

            <select
              value={branchId}
              onChange={event =>
                setBranchId(event.target.value)
              }
              disabled={loadingBranches}
              className="w-full px-4 py-2.5 border
                         border-gray-300 rounded-lg text-sm
                         bg-white focus:outline-none
                         focus:ring-2 focus:ring-[#0D6B3E]
                         disabled:bg-gray-50"
            >

              <option value="">
                Head Office / Organization-wide
              </option>

              {branches.map(branch => (
                <option
                  key={branch.id}
                  value={branch.id}
                >
                  {branch.name}
                </option>
              ))}

            </select>

            <p className="text-xs text-gray-400 mt-1">
              Leave blank if this account belongs to the
              entire organization.
            </p>

          </div>

          {/* Opening balance */}

          <div>

            <label className="block text-sm font-semibold
                              text-gray-700 mb-1.5">
              Opening Balance
            </label>

            <div className="relative">

              <span className="absolute left-3 top-1/2
                               -translate-y-1/2 text-xs
                               font-semibold text-gray-400">
                {currency}
              </span>

              <input
                type="number"
                min="0"
                step="0.01"
                value={openingBalance}
                onChange={event =>
                  setOpeningBalance(
                    event.target.value
                  )
                }
                placeholder="0.00"
                className="w-full pl-14 pr-4 py-2.5
                           border border-gray-300 rounded-lg
                           text-sm focus:outline-none
                           focus:ring-2 focus:ring-[#0D6B3E]"
              />

            </div>

            <p className="text-xs text-gray-400 mt-1">
              Enter the actual balance available when
              this account is introduced into the system.
            </p>

          </div>

          {/* Accounting explanation */}

          <div className="bg-blue-50 border border-blue-100
                          rounded-lg p-4">

            <div className="text-xs font-bold text-blue-800 uppercase">
              Accounting treatment
            </div>

            <p className="text-xs text-blue-700 mt-1 leading-5">
              The system will automatically create a
              dedicated asset account in the Chart of
              Accounts. If you enter an opening balance,
              an opening journal entry will also be created.
            </p>

          </div>

        </div>

        {/* Footer */}

        <div className="px-6 py-4 bg-gray-50 border-t
                        border-gray-100 flex gap-3">

          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            className="flex-1 px-4 py-2.5 border
                       border-gray-300 rounded-lg text-sm
                       font-semibold text-gray-700
                       hover:bg-white disabled:opacity-50"
          >
            Cancel
          </button>

          <button
            type="submit"
            disabled={saving}
            className="flex-1 px-4 py-2.5 bg-[#0D6B3E]
                       hover:bg-[#095631] disabled:opacity-50
                       text-white rounded-lg text-sm
                       font-semibold"
          >
            {saving
              ? 'Creating Account...'
              : 'Create Account'}
          </button>

        </div>

      </form>

    </ModalShell>
  );
}

/* =========================================================
   BANK TRANSACTION MODAL
========================================================= */

function BankTransactionModal({
  account,
  accounts,
  onClose,
  onSaved,
  currency,
}: {
  account: BankAccountRow;
  accounts: BankAccountRow[];
  onClose: () => void;
  onSaved: () => void;
  currency: string;
}) {
  const [type, setType] =
    useState<'DEPOSIT' | 'WITHDRAWAL'>(
      'DEPOSIT'
    );

  const [amount, setAmount] = useState('');
  const [counterAccountId, setCounterAccountId] =
    useState('');

  const [description, setDescription] =
    useState('');

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  /*
   * Counter accounts are GL accounts, not bank accounts.
   *
   * Your current backend endpoint requires:
   * counterAccountId
   *
   * We therefore need the Chart of Accounts to populate
   * this dropdown. This modal loads it directly.
   */

  const [glAccounts, setGlAccounts] =
    useState<Account[]>([]);

  const [loadingAccounts, setLoadingAccounts] =
    useState(true);

  useEffect(() => {
    let mounted = true;

    accountingApi
      .chartOfAccounts()
      .then(response => {
        if (!mounted) return;

        setGlAccounts(
          unwrapApiData<Account[]>(response) ?? []
        );
      })
      .catch(() => {
        if (!mounted) return;

        setGlAccounts([]);
      })
      .finally(() => {
        if (!mounted) return;

        setLoadingAccounts(false);
      });

    return () => {
      mounted = false;
    };
  }, []);

  const availableCounterAccounts =
    glAccounts.filter(
      glAccount =>
        glAccount.active &&
        glAccount.id !== account.id
    );

  const handleSubmit = async (
    event: React.FormEvent
  ) => {
    event.preventDefault();

    setError('');

    if (!counterAccountId) {
      setError(
        'Select the accounting account affected by this transaction.'
      );
      return;
    }

    const numericAmount = Number(amount);

    if (
      !Number.isFinite(numericAmount) ||
      numericAmount <= 0
    ) {
      setError(
        'Enter a valid transaction amount greater than zero.'
      );
      return;
    }

    setSaving(true);

    try {
      await bankAccountApi.recordTransaction(
        account.id,
        {
          type,
          amount: numericAmount,
          counterAccountId:
            Number(counterAccountId),
          description:
            description.trim() ||
            `${type === 'DEPOSIT'
              ? 'Deposit'
              : 'Withdrawal'} on ${account.name}`,
        }
      );

      onSaved();
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Could not record transaction.'
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <ModalShell
      title={`${type === 'DEPOSIT'
        ? 'Record Deposit'
        : 'Record Withdrawal'}`}
      description={`${account.name} · Current balance ${formatMoney(
        account.balance,
        currency
      )}`}
      onClose={onClose}
      maxWidth="max-w-lg"
    >

      <form onSubmit={handleSubmit}>

        <div className="p-6 space-y-5">

          {error && (
            <div className="px-4 py-3 bg-red-50 border
                            border-red-200 text-red-700
                            text-sm rounded-lg">
              {error}
            </div>
          )}

          {/* Transaction type */}

          <div>

            <label className="block text-sm font-semibold
                              text-gray-700 mb-1.5">
              Transaction Type *
            </label>

            <div className="grid grid-cols-2 gap-3">

              <button
                type="button"
                onClick={() =>
                  setType('DEPOSIT')
                }
                className={`
                  p-3 rounded-lg border text-left
                  ${
                    type === 'DEPOSIT'
                      ? 'border-green-600 bg-green-50 ring-1 ring-green-600'
                      : 'border-gray-300 hover:bg-gray-50'
                  }
                `}
              >

                <div className="font-semibold text-sm">
                  Deposit
                </div>

                <div className="text-xs text-gray-500 mt-1">
                  Money enters this account
                </div>

              </button>

              <button
                type="button"
                onClick={() =>
                  setType('WITHDRAWAL')
                }
                className={`
                  p-3 rounded-lg border text-left
                  ${
                    type === 'WITHDRAWAL'
                      ? 'border-red-600 bg-red-50 ring-1 ring-red-600'
                      : 'border-gray-300 hover:bg-gray-50'
                  }
                `}
              >

                <div className="font-semibold text-sm">
                  Withdrawal
                </div>

                <div className="text-xs text-gray-500 mt-1">
                  Money leaves this account
                </div>

              </button>

            </div>

          </div>

          {/* Amount */}

          <div>

            <label className="block text-sm font-semibold
                              text-gray-700 mb-1.5">
              Amount *
            </label>

            <div className="relative">

              <span className="absolute left-3 top-1/2
                               -translate-y-1/2 text-xs
                               font-semibold text-gray-400">
                {currency}
              </span>

              <input
                type="number"
                min="0.01"
                step="0.01"
                required
                value={amount}
                onChange={event =>
                  setAmount(event.target.value)
                }
                placeholder="0.00"
                className="w-full pl-14 pr-4 py-2.5
                           border border-gray-300 rounded-lg
                           text-sm focus:outline-none
                           focus:ring-2 focus:ring-[#0D6B3E]"
              />

            </div>

          </div>

          {/* Counter account */}

          <div>

            <label className="block text-sm font-semibold
                              text-gray-700 mb-1.5">
              Counter Account *
            </label>

            <select
              required
              value={counterAccountId}
              onChange={event =>
                setCounterAccountId(
                  event.target.value
                )
              }
              disabled={loadingAccounts}
              className="w-full px-4 py-2.5 border
                         border-gray-300 rounded-lg text-sm
                         bg-white focus:outline-none
                         focus:ring-2 focus:ring-[#0D6B3E]
                         disabled:bg-gray-50"
            >

              <option value="">
                {loadingAccounts
                  ? 'Loading accounts...'
                  : 'Select accounting account...'}
              </option>

              {availableCounterAccounts.map(
                glAccount => (
                  <option
                    key={glAccount.id}
                    value={glAccount.id}
                  >
                    {glAccount.code} — {glAccount.name}
                  </option>
                )
              )}

            </select>

            <p className="text-xs text-gray-400 mt-1">
              This is the other side of the accounting
              entry. For example, an expense account for
              a withdrawal or an equity account for a
              capital deposit.
            </p>

          </div>

          {/* Description */}

          <div>

            <label className="block text-sm font-semibold
                              text-gray-700 mb-1.5">
              Description
            </label>

            <textarea
              rows={3}
              value={description}
              onChange={event =>
                setDescription(
                  event.target.value
                )
              }
              placeholder="Describe the transaction..."
              className="w-full px-4 py-2.5 border
                         border-gray-300 rounded-lg text-sm
                         focus:outline-none focus:ring-2
                         focus:ring-[#0D6B3E]"
            />

          </div>

          {/* Preview */}

          <div className="bg-gray-50 border border-gray-200
                          rounded-lg p-4">

            <div className="text-xs font-bold text-gray-600 uppercase">
              Accounting Preview
            </div>

            <div className="mt-3 space-y-2 text-sm">

              <div className="flex justify-between">

                <span className="text-gray-600">
                  {type === 'DEPOSIT'
                    ? 'Debit'
                    : 'Credit'}
                </span>

                <span className="font-mono font-semibold">
                  {amount
                    ? formatMoney(
                        Number(amount),
                        currency
                      )
                    : formatMoney(0, currency)}
                </span>

              </div>

              <div className="flex justify-between">

                <span className="text-gray-600">
                  {type === 'DEPOSIT'
                    ? 'Credit'
                    : 'Debit'}
                </span>

                <span className="font-mono font-semibold">
                  {amount
                    ? formatMoney(
                        Number(amount),
                        currency
                      )
                    : formatMoney(0, currency)}
                </span>

              </div>

            </div>

          </div>

        </div>

        <div className="px-6 py-4 bg-gray-50 border-t
                        border-gray-100 flex gap-3">

          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            className="flex-1 px-4 py-2.5 border
                       border-gray-300 rounded-lg text-sm
                       font-semibold text-gray-700
                       hover:bg-white disabled:opacity-50"
          >
            Cancel
          </button>

          <button
            type="submit"
            disabled={saving}
            className={`
              flex-1 px-4 py-2.5 text-white rounded-lg
              text-sm font-semibold disabled:opacity-50
              ${
                type === 'DEPOSIT'
                  ? 'bg-[#0D6B3E] hover:bg-[#095631]'
                  : 'bg-red-600 hover:bg-red-700'
              }
            `}
          >
            {saving
              ? 'Saving...'
              : type === 'DEPOSIT'
                ? 'Record Deposit'
                : 'Record Withdrawal'}
          </button>

        </div>

      </form>

    </ModalShell>
  );
}

/* =========================================================
   TRANSFER FUNDS MODAL
========================================================= */

function TransferFundsModal({
  accounts,
  onClose,
  onSaved,
  currency,
}: {
  accounts: BankAccountRow[];
  onClose: () => void;
  onSaved: () => void;
  currency: string;
}) {
  const activeAccounts = accounts.filter(
    account => account.active
  );

  const [fromAccountId, setFromAccountId] =
    useState('');

  const [toAccountId, setToAccountId] =
    useState('');

  const [amount, setAmount] =
    useState('');

  const [description, setDescription] =
    useState('');

  const [saving, setSaving] =
    useState(false);

  const [error, setError] =
    useState('');

  const selectedFromAccount =
    activeAccounts.find(
      account =>
        account.id === Number(fromAccountId)
    );

  const handleSubmit = async (
    event: React.FormEvent
  ) => {
    event.preventDefault();

    setError('');

    if (!fromAccountId) {
      setError(
        'Select the account the money will come from.'
      );
      return;
    }

    if (!toAccountId) {
      setError(
        'Select the account receiving the money.'
      );
      return;
    }

    if (
      Number(fromAccountId) ===
      Number(toAccountId)
    ) {
      setError(
        'The source and destination accounts must be different.'
      );
      return;
    }

    const numericAmount = Number(amount);

    if (
      !Number.isFinite(numericAmount) ||
      numericAmount <= 0
    ) {
      setError(
        'Enter a valid transfer amount greater than zero.'
      );
      return;
    }

    if (
      selectedFromAccount &&
      numericAmount > selectedFromAccount.balance
    ) {
      setError(
        'The transfer amount exceeds the current balance of the source account.'
      );
      return;
    }

    setSaving(true);

    try {
      await bankAccountApi.transfer({
        fromAccountId:
          Number(fromAccountId),

        toAccountId:
          Number(toAccountId),

        amount: numericAmount,

        description:
          description.trim() ||
          'Internal account transfer',
      });

      onSaved();
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Could not complete transfer.'
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <ModalShell
      title="Transfer Funds"
      description="Move funds between two bank or cash accounts owned by the organization."
      onClose={onClose}
      maxWidth="max-w-lg"
    >

      <form onSubmit={handleSubmit}>

        <div className="p-6 space-y-5">

          {error && (
            <div className="px-4 py-3 bg-red-50 border
                            border-red-200 text-red-700
                            text-sm rounded-lg">
              {error}
            </div>
          )}

          {/* From */}

          <div>

            <label className="block text-sm font-semibold
                              text-gray-700 mb-1.5">
              From Account *
            </label>

            <select
              required
              value={fromAccountId}
              onChange={event =>
                setFromAccountId(
                  event.target.value
                )
              }
              className="w-full px-4 py-2.5 border
                         border-gray-300 rounded-lg text-sm
                         bg-white focus:outline-none
                         focus:ring-2 focus:ring-[#0D6B3E]"
            >

              <option value="">
                Select source account...
              </option>

              {activeAccounts.map(account => (

                <option
                  key={account.id}
                  value={account.id}
                >
                  {account.name} —{' '}
                  {formatMoney(
                    account.balance,
                    currency
                  )}
                </option>

              ))}

            </select>

          </div>

          {/* To */}

          <div>

            <label className="block text-sm font-semibold
                              text-gray-700 mb-1.5">
              To Account *
            </label>

            <select
              required
              value={toAccountId}
              onChange={event =>
                setToAccountId(
                  event.target.value
                )
              }
              className="w-full px-4 py-2.5 border
                         border-gray-300 rounded-lg text-sm
                         bg-white focus:outline-none
                         focus:ring-2 focus:ring-[#0D6B3E]"
            >

              <option value="">
                Select destination account...
              </option>

              {activeAccounts
                .filter(
                  account =>
                    account.id !==
                    Number(fromAccountId)
                )
                .map(account => (

                  <option
                    key={account.id}
                    value={account.id}
                  >
                    {account.name}
                  </option>

                ))}

            </select>

          </div>

          {/* Amount */}

          <div>

            <label className="block text-sm font-semibold
                              text-gray-700 mb-1.5">
              Transfer Amount *
            </label>

            <div className="relative">

              <span className="absolute left-3 top-1/2
                               -translate-y-1/2 text-xs
                               font-semibold text-gray-400">
                {currency}
              </span>

              <input
                type="number"
                min="0.01"
                step="0.01"
                required
                value={amount}
                onChange={event =>
                  setAmount(
                    event.target.value
                  )
                }
                placeholder="0.00"
                className="w-full pl-14 pr-4 py-2.5
                           border border-gray-300 rounded-lg
                           text-sm focus:outline-none
                           focus:ring-2 focus:ring-[#0D6B3E]"
              />

            </div>

            {selectedFromAccount && (
              <p className="text-xs text-gray-400 mt-1">
                Available balance:{' '}
                {formatMoney(
                  selectedFromAccount.balance,
                  currency
                )}
              </p>
            )}

          </div>

          {/* Description */}

          <div>

            <label className="block text-sm font-semibold
                              text-gray-700 mb-1.5">
              Description
            </label>

            <textarea
              rows={3}
              value={description}
              onChange={event =>
                setDescription(
                  event.target.value
                )
              }
              placeholder="e.g. Transfer funds to Kigali branch petty cash"
              className="w-full px-4 py-2.5 border
                         border-gray-300 rounded-lg text-sm
                         focus:outline-none focus:ring-2
                         focus:ring-[#0D6B3E]"
            />

          </div>

          {/* Explanation */}

          <div className="bg-blue-50 border border-blue-100
                          rounded-lg p-4">

            <div className="text-xs font-bold text-blue-800 uppercase">
              Accounting treatment
            </div>

            <p className="text-xs text-blue-700 mt-1 leading-5">
              This transfer moves money between the
              organization's own accounts. It does not
              create income or expense. The source account
              is credited and the destination account is
              debited.
            </p>

          </div>

        </div>

        <div className="px-6 py-4 bg-gray-50 border-t
                        border-gray-100 flex gap-3">

          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            className="flex-1 px-4 py-2.5 border
                       border-gray-300 rounded-lg text-sm
                       font-semibold text-gray-700
                       hover:bg-white disabled:opacity-50"
          >
            Cancel
          </button>

          <button
            type="submit"
            disabled={saving}
            className="flex-1 px-4 py-2.5 bg-[#0D6B3E]
                       hover:bg-[#095631] disabled:opacity-50
                       text-white rounded-lg text-sm
                       font-semibold"
          >
            {saving
              ? 'Transferring...'
              : 'Transfer Funds'}
          </button>

        </div>

      </form>

    </ModalShell>
  );
}

/* =========================================================
   GENERIC MODAL SHELL
========================================================= */

function ModalShell({
  title,
  description,
  onClose,
  maxWidth,
  children,
}: {
  title: string;
  description?: string;
  onClose: () => void;
  maxWidth?: string;
  children: React.ReactNode;
}) {
  return (
    <div
      className="fixed inset-0 z-50 bg-black/40
                 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
    >

      <div
        className={`
          bg-white rounded-xl shadow-xl w-full
          ${maxWidth || 'max-w-lg'}
          max-h-[92vh] overflow-y-auto
        `}
      >

        {/* Header */}

        <div className="px-6 py-5 border-b border-gray-100">

          <div className="flex items-start justify-between gap-4">

            <div>

              <h2 className="text-lg font-bold text-gray-900">
                {title}
              </h2>

              {description && (
                <p className="text-sm text-gray-500 mt-1 leading-5">
                  {description}
                </p>
              )}

            </div>

            <button
              type="button"
              onClick={onClose}
              className="w-8 h-8 rounded-lg flex items-center
                         justify-center text-gray-400
                         hover:text-gray-700 hover:bg-gray-100
                         transition shrink-0"
              aria-label="Close"
            >
              ×
            </button>

          </div>

        </div>

        {children}

      </div>

    </div>
  );
}