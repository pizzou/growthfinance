
'use client';

import { useEffect, useMemo, useState } from 'react';
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

interface BankAccountRow {
  id: number;
  name: string;
  accountType: 'CASH' | 'BANK' | string;
  bankName?: string;
  accountNumber?: string;
  branchName?: string;
  glAccountCode: string;
  glAccountId?: number;
  active: boolean;
  balance: number;
}

interface BranchRow {
  id: number;
  name: string;
}

type BankTransactionType = 'DEPOSIT' | 'WITHDRAWAL';

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

type Tab = (typeof TABS)[number];

/* =========================================================
   MAIN ACCOUNTING PAGE
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
  const [branches, setBranches] = useState<BranchRow[]>([]);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [expanded, setExpanded] = useState<number | null>(null);

  const [showBankAccountForm, setShowBankAccountForm] =
    useState(false);

  const [showTransactionForm, setShowTransactionForm] =
    useState(false);

  const [showTransferForm, setShowTransferForm] =
    useState(false);

  const [selectedBankAccount, setSelectedBankAccount] =
    useState<BankAccountRow | null>(null);

  /* =======================================================
     LOAD ACCOUNTING DATA
  ======================================================= */

  const loadAll = async () => {
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
        branchesResponse,
      ] = await Promise.all([
        accountingApi.chartOfAccounts().catch(() => []),
        accountingApi.journal().catch(() => []),
        accountingApi.trialBalance().catch(() => null),
        accountingApi.balanceSheet().catch(() => null),
        accountingApi.profitAndLoss().catch(() => null),
        accountingApi.cashFlow().catch(() => null),
        accountingApi.branchSummary().catch(() => []),
        bankAccountApi.list().catch(() => []),
        branchApi.list().catch(() => []),
      ]);

      setAccounts(accountsResponse as Account[]);
      setJournal(journalResponse as JournalEntryRow[]);
      setTrial(trialResponse as TrialBalance | null);

      setBalanceSheet(
        balanceSheetResponse as BalanceSheet | null
      );

      setPnl(pnlResponse as ProfitAndLoss | null);

      setCashFlow(
        cashFlowResponse as CashFlow | null
      );

      setBranchSummary(
        branchSummaryResponse as BranchSummaryRow[]
      );

      setBankAccounts(
        bankAccountsResponse as BankAccountRow[]
      );

      setBranches(
        branchesResponse as BranchRow[]
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
  };

  useEffect(() => {
    void loadAll();
  }, []);

  /* =======================================================
     REVERSE JOURNAL ENTRY
  ======================================================= */

  const handleReverse = async (id: number) => {
    const reason =
      window.prompt(
        'Reason for reversing this entry (optional):'
      ) ?? '';

    try {
      setError('');

      await accountingApi.reverseEntry(
        id,
        reason || undefined
      );

      await loadAll();
    } catch (err: unknown) {
      const msg =
        err instanceof Error
          ? err.message
          : 'Could not reverse entry';

      setError(msg);
    }
  };

  /* =======================================================
     OPEN DEPOSIT / WITHDRAWAL MODAL
  ======================================================= */

  const openTransactionModal = (
    account: BankAccountRow
  ) => {
    if (!account.active) {
      setError(
        `Bank account "${account.name}" is inactive.`
      );
      return;
    }

    setError('');
    setSelectedBankAccount(account);
    setShowTransactionForm(true);
  };

  /* =======================================================
     OPEN TRANSFER MODAL
  ======================================================= */

  const openTransferModal = (
    account: BankAccountRow
  ) => {
    if (!account.active) {
      setError(
        `Bank account "${account.name}" is inactive.`
      );
      return;
    }

    setError('');
    setSelectedBankAccount(account);
    setShowTransferForm(true);
  };

  /* =======================================================
     FORMATTERS
  ======================================================= */

  const fmt = (n: number) =>
    new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(n ?? 0);

  const formatAccountNumber = (
    accountNumber?: string
  ) => {
    if (!accountNumber) {
      return '—';
    }

    if (accountNumber.length <= 4) {
      return accountNumber;
    }

    return `•••• ${accountNumber.slice(-4)}`;
  };

  /* =======================================================
     ACTIVE GL COUNTER ACCOUNTS
  ======================================================= */

  const transactionAccounts = useMemo(
    () =>
      accounts.filter(
        (account) =>
          account.active &&
          account.type !== 'ASSET'
      ),
    [accounts]
  );

  /* =======================================================
     LOADING
  ======================================================= */

  if (loading) {
    return <PageSpinner />;
  }

  /* =======================================================
     RENDER
  ======================================================= */

  return (
    <div className="space-y-6">

      {/* ===================================================
          PAGE HEADER
      =================================================== */}

      <div>
        <h1 className="text-2xl font-bold text-gray-900">
          Accounting
        </h1>

        <p className="text-gray-500 text-sm mt-1">
          General ledger, chart of accounts, cash management,
          and financial reports — {currency}.
        </p>
      </div>

      {/* ===================================================
          GLOBAL ERROR
      =================================================== */}

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg px-4 py-3 flex items-start justify-between gap-4">
          <span>{error}</span>

          <button
            type="button"
            onClick={() => setError('')}
            className="text-red-500 hover:text-red-700 font-bold"
          >
            ×
          </button>
        </div>
      )}

      {/* ===================================================
          TABS
      =================================================== */}

      <div className="flex gap-1 border-b border-gray-200 overflow-x-auto">
        {TABS.map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => setTab(t)}
            className={`px-4 py-2.5 text-sm font-semibold border-b-2 transition-colors whitespace-nowrap ${
              tab === t
                ? 'border-[#0D6B3E] text-[#0D6B3E]'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            {t}
          </button>
        ))}
      </div>

      {/* ===================================================
          TRIAL BALANCE
      =================================================== */}

      {tab === 'Trial Balance' && trial && (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">

          <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
            <div>
              <div className="font-bold text-gray-900">
                Trial Balance
              </div>

              <p className="text-xs text-gray-500 mt-1">
                Verification that total debits equal total credits.
              </p>
            </div>

            <span
              className={`text-xs font-bold px-3 py-1.5 rounded-full ${
                trial.balanced
                  ? 'bg-green-50 text-green-700'
                  : 'bg-red-50 text-red-700'
              }`}
            >
              {trial.balanced
                ? '✓ Balanced'
                : '⚠ Out of Balance'}
            </span>
          </div>

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
              {trial.accounts.map((row) => (
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
                      className={`text-[10px] font-bold px-2 py-0.5 rounded ${
                        TYPE_COLORS[row.type] ||
                        'bg-gray-100 text-gray-600'
                      }`}
                    >
                      {row.type}
                    </span>
                  </td>

                  <td className="px-5 py-2.5 text-right font-mono">
                    {row.debit ? fmt(row.debit) : ''}
                  </td>

                  <td className="px-5 py-2.5 text-right font-mono">
                    {row.credit ? fmt(row.credit) : ''}
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
                  {fmt(trial.totalDebit)}
                </td>

                <td className="px-5 py-3 text-right font-mono">
                  {fmt(trial.totalCredit)}
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}

      {/* ===================================================
          BALANCE SHEET
      =================================================== */}

      {tab === 'Balance Sheet' && balanceSheet && (
        <div className="bg-white rounded-xl border border-gray-200 p-5 space-y-6">

          <div className="flex items-center justify-between">
            <div>
              <div className="font-bold text-gray-900">
                Balance Sheet
              </div>

              <p className="text-xs text-gray-500 mt-1">
                Financial position as of today.
              </p>
            </div>

            <span
              className={`text-xs font-bold px-3 py-1.5 rounded-full ${
                balanceSheet.balanced
                  ? 'bg-green-50 text-green-700'
                  : 'bg-red-50 text-red-700'
              }`}
            >
              {balanceSheet.balanced
                ? '✓ Balanced'
                : '⚠ Out of Balance'}
            </span>
          </div>

          {[
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
              'Equity',
              balanceSheet.equity,
              balanceSheet.totalEquity,
            ],
          ].map(([label, rows, total]) => (
            <div key={label as string}>

              <div className="text-sm font-bold text-gray-700 mb-2">
                {label as string}
              </div>

              <div className="divide-y divide-gray-100 border border-gray-100 rounded-lg overflow-hidden">

                {(rows as StatementRow[]).map((r) => (
                  <div
                    key={r.code}
                    className="flex justify-between px-4 py-2 text-sm"
                  >
                    <span className="text-gray-600">
                      {r.code} — {r.name}
                    </span>

                    <span className="font-mono">
                      {fmt(r.balance)}
                    </span>
                  </div>
                ))}

                {label === 'Equity' && (
                  <div className="flex justify-between px-4 py-2 text-sm bg-gray-50">
                    <span className="text-gray-600">
                      Current Period Net Income
                    </span>

                    <span className="font-mono">
                      {fmt(
                        balanceSheet.currentPeriodNetIncome
                      )}
                    </span>
                  </div>
                )}

                <div className="flex justify-between px-4 py-2 text-sm font-bold bg-gray-50 border-t border-gray-200">
                  <span>
                    Total {label as string}
                  </span>

                  <span className="font-mono">
                    {fmt(total as number)}
                  </span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* ===================================================
          PROFIT & LOSS
      =================================================== */}

      {tab === 'Profit & Loss' && pnl && (
        <div className="bg-white rounded-xl border border-gray-200 p-5 space-y-6">

          <div>
            <div className="font-bold text-gray-900">
              Profit &amp; Loss
            </div>

            <p className="text-xs text-gray-500 mt-1">
              Current month-to-date income and expenses.
            </p>
          </div>

          {[
            ['Income', pnl.income, pnl.totalIncome],
            ['Expense', pnl.expense, pnl.totalExpense],
          ].map(([label, rows, total]) => (
            <div key={label as string}>

              <div className="text-sm font-bold text-gray-700 mb-2">
                {label as string}
              </div>

              <div className="divide-y divide-gray-100 border border-gray-100 rounded-lg overflow-hidden">

                {(rows as PnlRow[]).map((r) => (
                  <div
                    key={r.code}
                    className="flex justify-between px-4 py-2 text-sm"
                  >
                    <span className="text-gray-600">
                      {r.code} — {r.name}
                    </span>

                    <span className="font-mono">
                      {fmt(r.amount)}
                    </span>
                  </div>
                ))}

                {(rows as PnlRow[]).length === 0 && (
                  <div className="px-4 py-3 text-sm text-gray-400">
                    No activity this period.
                  </div>
                )}

                <div className="flex justify-between px-4 py-2 text-sm font-bold bg-gray-50 border-t border-gray-200">
                  <span>
                    Total {label as string}
                  </span>

                  <span className="font-mono">
                    {fmt(total as number)}
                  </span>
                </div>
              </div>
            </div>
          ))}

          <div
            className={`flex justify-between px-4 py-3 rounded-lg font-bold text-sm ${
              pnl.netIncome >= 0
                ? 'bg-green-50 text-green-700'
                : 'bg-red-50 text-red-700'
            }`}
          >
            <span>Net Income</span>

            <span className="font-mono">
              {fmt(pnl.netIncome)}
            </span>
          </div>
        </div>
      )}

      {/* ===================================================
          CASH FLOW
      =================================================== */}

      {tab === 'Cash Flow' && cashFlow && (
        <div className="bg-white rounded-xl border border-gray-200 p-5 space-y-2">

          <div className="mb-3">
            <div className="font-bold text-gray-900">
              Cash Flow
            </div>

            <p className="text-xs text-gray-500 mt-1">
              Current month-to-date cash movement.
            </p>
          </div>

          {[
            [
              'Cash Used for Lending (disbursements)',
              cashFlow.cashUsedForLending,
            ],
            [
              'Cash From Collections',
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
              className="flex justify-between px-4 py-2 text-sm border-b border-gray-100"
            >
              <span className="text-gray-600">
                {label as string}
              </span>

              <span className="font-mono">
                {fmt(value as number)}
              </span>
            </div>
          ))}

          <div className="flex justify-between px-4 py-3 rounded-lg font-bold text-sm bg-gray-50 mt-2">
            <span>Net Change in Cash</span>

            <span className="font-mono">
              {fmt(cashFlow.netChangeInCash)}
            </span>
          </div>
        </div>
      )}

      {/* ===================================================
          CHART OF ACCOUNTS
      =================================================== */}

      {tab === 'Chart of Accounts' && (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">

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

              {accounts.map((acc) => (
                <tr
                  key={acc.id}
                  className="hover:bg-gray-50"
                >
                  <td className="px-5 py-2.5 font-mono text-gray-500">
                    {acc.code}
                  </td>

                  <td className="px-5 py-2.5 font-medium text-gray-900">
                    {acc.name}
                  </td>

                  <td className="px-5 py-2.5">
                    <span
                      className={`text-[10px] font-bold px-2 py-0.5 rounded ${
                        TYPE_COLORS[acc.type] ||
                        'bg-gray-100 text-gray-600'
                      }`}
                    >
                      {acc.type}
                    </span>
                  </td>

                  <td className="px-5 py-2.5 text-gray-600">
                    {acc.normalBalance}
                  </td>

                  <td className="px-5 py-2.5">
                    <span
                      className={`text-[10px] font-bold px-2 py-0.5 rounded ${
                        acc.active
                          ? 'bg-green-50 text-green-700'
                          : 'bg-gray-100 text-gray-500'
                      }`}
                    >
                      {acc.active
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
      )}

      {/* ===================================================
          JOURNAL
      =================================================== */}

      {tab === 'Journal' && (
        <div className="bg-white rounded-xl border border-gray-200 divide-y divide-gray-100">

          {journal.length === 0 && (
            <div className="px-5 py-8 text-center text-gray-400 text-sm">
              No journal entries yet.
            </div>
          )}

          {journal.map((entry) => (
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
                onKeyDown={(e) => {
                  if (
                    e.key === 'Enter' ||
                    e.key === ' '
                  ) {
                    setExpanded(
                      expanded === entry.id
                        ? null
                        : entry.id
                    );
                  }
                }}
                className="w-full flex items-center justify-between px-5 py-3.5 hover:bg-gray-50 text-left cursor-pointer"
              >

                <div className="flex items-center gap-3">

                  <span className="text-xs text-gray-400 w-24 shrink-0">
                    {new Date(
                      entry.entryDate
                    ).toLocaleDateString()}
                  </span>

                  <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-gray-100 text-gray-600">
                    {entry.sourceType}
                  </span>

                  <span className="text-sm font-medium text-gray-900">
                    {entry.description}
                  </span>

                  {entry.branchName && (
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-indigo-50 text-indigo-600">
                      {entry.branchName}
                    </span>
                  )}

                  {entry.reversed && (
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-red-50 text-red-600">
                      REVERSED
                    </span>
                  )}
                </div>

                <div className="flex items-center gap-3 text-xs text-gray-400">

                  <code>
                    {entry.reference}
                  </code>

                  {!entry.reversed &&
                    entry.sourceType !== 'REVERSAL' && (
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          void handleReverse(entry.id);
                        }}
                        className="text-red-500 hover:text-red-700 font-semibold border border-red-100 bg-white hover:bg-red-50 px-2 py-1 rounded"
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

                  <table className="w-full text-xs bg-gray-50 rounded-lg overflow-hidden">

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

                      {entry.lines?.map((line) => (
                        <tr key={line.id}>

                          <td className="px-3 py-2">
                            {line.account?.code}
                            {' — '}
                            {line.account?.name}
                          </td>

                          <td className="px-3 py-2 text-right font-mono">
                            {line.debit
                              ? fmt(line.debit)
                              : ''}
                          </td>

                          <td className="px-3 py-2 text-right font-mono">
                            {line.credit
                              ? fmt(line.credit)
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

      {/* ===================================================
          BANK ACCOUNTS
      =================================================== */}

      {tab === 'Bank Accounts' && (
        <div className="space-y-4">

          {/* HEADER */}

          <div className="flex items-center justify-between">

            <div>
              <h2 className="text-lg font-bold text-gray-900">
                Bank & Cash Accounts
              </h2>

              <p className="text-sm text-gray-500 mt-1">
                Manage institutional bank accounts,
                cash drawers, deposits, withdrawals,
                and internal transfers.
              </p>
            </div>

            <button
              type="button"
              onClick={() =>
                setShowBankAccountForm(true)
              }
              className="inline-flex items-center gap-2 px-4 py-2.5 bg-[#0D6B3E] hover:bg-[#09552F] text-white text-sm font-semibold rounded-lg transition"
            >
              <span className="text-lg leading-none">
                +
              </span>

              Add Account
            </button>

          </div>

          {/* INFORMATION */}

          <div className="bg-blue-50 border border-blue-100 rounded-xl px-4 py-3">

            <div className="flex gap-3">

              <div className="text-blue-600 font-bold">
                ℹ
              </div>

              <div>

                <p className="text-sm font-semibold text-blue-900">
                  Bank accounts are now transaction-enabled
                </p>

                <p className="text-xs text-blue-700 mt-1 leading-5">
                  An account can be created with a zero
                  opening balance and funded later using
                  Deposit. Withdrawals and transfers are
                  also recorded through the general ledger.
                  The displayed balance comes from posted
                  journal entries.
                </p>

              </div>
            </div>
          </div>

          {/* ACCOUNTS TABLE */}

          <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">

            <div className="overflow-x-auto">

              <table className="w-full text-sm">

                <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wider">

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

                  {bankAccounts.map((account) => (
                    <tr
                      key={account.id}
                      className="hover:bg-gray-50"
                    >

                      <td className="px-5 py-3">

                        <div className="font-semibold text-gray-900">
                          {account.name}
                        </div>

                        {account.accountType === 'BANK' &&
                          account.accountNumber && (
                            <div className="text-xs text-gray-400 mt-0.5">
                              {formatAccountNumber(
                                account.accountNumber
                              )}
                            </div>
                          )}

                      </td>

                      <td className="px-5 py-3">

                        <span
                          className={`text-[10px] font-bold px-2 py-1 rounded ${
                            account.accountType ===
                            'BANK'
                              ? 'bg-blue-50 text-blue-700'
                              : 'bg-amber-50 text-amber-700'
                          }`}
                        >
                          {account.accountType}
                        </span>

                      </td>

                      <td className="px-5 py-3 text-gray-600">

                        {account.accountType ===
                          'BANK'
                          ? account.bankName || '—'
                          : 'Petty Cash / Cash'}

                      </td>

                      <td className="px-5 py-3 text-gray-600">
                        {account.branchName ||
                          'Head Office'}
                      </td>

                      <td className="px-5 py-3 font-mono text-gray-500">
                        {account.glAccountCode}
                      </td>

                      <td className="px-5 py-3 text-right font-mono font-semibold">
                        {currency}{' '}
                        {fmt(account.balance)}
                      </td>

                      <td className="px-5 py-3 text-center">

                        <span
                          className={`text-[10px] font-bold px-2 py-1 rounded ${
                            account.active
                              ? 'bg-green-50 text-green-700'
                              : 'bg-gray-100 text-gray-500'
                          }`}
                        >
                          {account.active
                            ? 'Active'
                            : 'Inactive'}
                        </span>

                      </td>

                      <td className="px-5 py-3">

                        <div className="flex justify-end gap-2">

                          <button
                            type="button"
                            disabled={!account.active}
                            onClick={() =>
                              openTransactionModal(
                                account
                              )
                            }
                            className="px-2.5 py-1.5 rounded-md text-xs font-semibold bg-green-50 text-green-700 hover:bg-green-100 disabled:opacity-40 disabled:cursor-not-allowed"
                          >
                            Deposit / Withdraw
                          </button>

                          <button
                            type="button"
                            disabled={!account.active}
                            onClick={() =>
                              openTransferModal(
                                account
                              )
                            }
                            className="px-2.5 py-1.5 rounded-md text-xs font-semibold bg-blue-50 text-blue-700 hover:bg-blue-100 disabled:opacity-40 disabled:cursor-not-allowed"
                          >
                            Transfer
                          </button>

                        </div>

                      </td>

                    </tr>
                  ))}

                  {bankAccounts.length === 0 && (
                    <tr>
                      <td
                        colSpan={8}
                        className="px-5 py-12 text-center"
                      >

                        <div className="mx-auto max-w-md">

                          <div className="text-4xl mb-3">
                            🏦
                          </div>

                          <h3 className="font-semibold text-gray-900">
                            No payment accounts yet
                          </h3>

                          <p className="text-sm text-gray-500 mt-1">
                            Add your organization's first
                            bank account or cash account.
                          </p>

                          <button
                            type="button"
                            onClick={() =>
                              setShowBankAccountForm(true)
                            }
                            className="mt-4 px-4 py-2 bg-[#0D6B3E] hover:bg-[#09552F] text-white text-sm font-semibold rounded-lg"
                          >
                            + Add Bank / Cash Account
                          </button>

                        </div>

                      </td>
                    </tr>
                  )}

                </tbody>
              </table>

            </div>
          </div>
        </div>
      )}

      {/* ===================================================
          BRANCHES
      =================================================== */}

      {tab === 'Branches' && (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">

          <table className="w-full text-sm">

            <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wider">

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

              {branchSummary.map((r) => (
                <tr
                  key={r.branch}
                  className="hover:bg-gray-50"
                >

                  <td className="px-5 py-2.5 font-medium text-gray-900">
                    {r.branch}
                  </td>

                  <td className="px-5 py-2.5 text-right font-mono">
                    {fmt(r.disbursed)}
                  </td>

                  <td className="px-5 py-2.5 text-right font-mono">
                    {fmt(r.collected)}
                  </td>

                  <td className="px-5 py-2.5 text-right font-mono">
                    {fmt(r.feeIncome)}
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
      )}

      {/* ===================================================
          ADD BANK ACCOUNT MODAL
      =================================================== */}

      {showBankAccountForm && (
        <ModalShell>
          <AddBankAccountModal
            branches={branches}
            currency={currency}
            onClose={() =>
              setShowBankAccountForm(false)
            }
            onSaved={async () => {
              setShowBankAccountForm(false);
              await loadAll();
            }}
          />
        </ModalShell>
      )}

      {/* ===================================================
          DEPOSIT / WITHDRAW MODAL
      =================================================== */}

      {showTransactionForm &&
        selectedBankAccount && (
          <ModalShell>

            <BankTransactionModal
              account={selectedBankAccount}
              currency={currency}
              counterAccounts={transactionAccounts}
              onClose={() => {
                setShowTransactionForm(false);
                setSelectedBankAccount(null);
              }}
              onSaved={async () => {
                setShowTransactionForm(false);
                setSelectedBankAccount(null);
                await loadAll();
              }}
            />

          </ModalShell>
        )}

      {/* ===================================================
          TRANSFER MODAL
      =================================================== */}

      {showTransferForm &&
        selectedBankAccount && (
          <ModalShell>

            <TransferModal
              fromAccount={selectedBankAccount}
              accounts={bankAccounts}
              currency={currency}
              onClose={() => {
                setShowTransferForm(false);
                setSelectedBankAccount(null);
              }}
              onSaved={async () => {
                setShowTransferForm(false);
                setSelectedBankAccount(null);
                await loadAll();
              }}
            />

          </ModalShell>
        )}

    </div>
  );
}

/* =========================================================
   MODAL SHELL
========================================================= */

function ModalShell({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      {children}
    </div>
  );
}

/* =========================================================
   ADD BANK ACCOUNT MODAL
========================================================= */

function AddBankAccountModal({
  branches,
  currency,
  onClose,
  onSaved,
}: {
  branches: BranchRow[];
  currency: string;
  onClose: () => void;
  onSaved: () => Promise<void> | void;
}) {
  const [accountType, setAccountType] =
    useState<'BANK' | 'CASH'>('BANK');

  const [name, setName] = useState('');
  const [bankName, setBankName] = useState('');
  const [accountNumber, setAccountNumber] =
    useState('');
  const [branchId, setBranchId] = useState('');
  const [openingBalance, setOpeningBalance] =
    useState('');

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  /* =======================================================
     SUBMIT
  ======================================================= */

  const handleSubmit = async (
    e: React.FormEvent
  ) => {
    e.preventDefault();

    setError('');

    const trimmedName = name.trim();

    if (!trimmedName) {
      setError('Enter an account name.');
      return;
    }

    if (
      accountType === 'BANK' &&
      !bankName.trim()
    ) {
      setError(
        'Enter the bank name for this bank account.'
      );
      return;
    }

    const opening =
      openingBalance.trim() === ''
        ? 0
        : Number(openingBalance);

    if (
      !Number.isFinite(opening) ||
      opening < 0
    ) {
      setError(
        'Opening balance must be zero or a positive amount.'
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
            ? bankName.trim()
            : undefined,
        accountNumber:
          accountType === 'BANK'
            ? accountNumber.trim() || undefined
            : undefined,
        openingBalance: opening,
        branchId: branchId
          ? Number(branchId)
          : undefined,
      });

      await onSaved();
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Could not create bank account.'
      );
    } finally {
      setSaving(false);
    }
  };

  /* =======================================================
     MODAL
  ======================================================= */

  return (
    <div className="bg-white rounded-2xl shadow-xl w-full max-w-xl max-h-[90vh] overflow-y-auto">

      <div className="px-6 py-5 border-b border-gray-100 flex items-start justify-between">

        <div>
          <h2 className="text-lg font-bold text-gray-900">
            Add Bank or Cash Account
          </h2>

          <p className="text-sm text-gray-500 mt-1">
            Add an account where your organization
            holds or manages money.
          </p>
        </div>

        <button
          type="button"
          onClick={onClose}
          disabled={saving}
          className="text-gray-400 hover:text-gray-700 text-2xl leading-none"
        >
          ×
        </button>

      </div>

      <form
        onSubmit={handleSubmit}
        className="p-6 space-y-5"
      >

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-sm">
            {error}
          </div>
        )}

        {/* ACCOUNT TYPE */}

        <div>

          <label className="block text-sm font-semibold text-gray-700 mb-2">
            Account Type
          </label>

          <div className="grid grid-cols-2 gap-3">

            <button
              type="button"
              onClick={() =>
                setAccountType('BANK')
              }
              className={`text-left border rounded-xl p-4 transition ${
                accountType === 'BANK'
                  ? 'border-[#0D6B3E] bg-green-50 ring-1 ring-[#0D6B3E]'
                  : 'border-gray-200 hover:border-gray-300'
              }`}
            >

              <div className="font-semibold text-gray-900">
                🏦 Bank Account
              </div>

              <p className="text-xs text-gray-500 mt-1">
                A formal bank account held with
                a financial institution.
              </p>

            </button>

            <button
              type="button"
              onClick={() =>
                setAccountType('CASH')
              }
              className={`text-left border rounded-xl p-4 transition ${
                accountType === 'CASH'
                  ? 'border-[#0D6B3E] bg-green-50 ring-1 ring-[#0D6B3E]'
                  : 'border-gray-200 hover:border-gray-300'
              }`}
            >

              <div className="font-semibold text-gray-900">
                💵 Cash Account
              </div>

              <p className="text-xs text-gray-500 mt-1">
                Physical cash such as petty cash
                or a branch cash drawer.
              </p>

            </button>

          </div>
        </div>

        {/* NAME */}

        <div>

          <label className="block text-sm font-semibold text-gray-700 mb-1.5">
            Account Name *
          </label>

          <input
            type="text"
            required
            value={name}
            onChange={(e) =>
              setName(e.target.value)
            }
            placeholder={
              accountType === 'BANK'
                ? 'e.g. Bank of Kigali - Main Account'
                : 'e.g. Kigali Head Office Petty Cash'
            }
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0D6B3E]"
          />

        </div>

        {/* BANK FIELDS */}

        {accountType === 'BANK' && (
          <>
            <div>

              <label className="block text-sm font-semibold text-gray-700 mb-1.5">
                Bank Name *
              </label>

              <input
                type="text"
                required
                value={bankName}
                onChange={(e) =>
                  setBankName(e.target.value)
                }
                placeholder="e.g. Bank of Kigali"
                className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0D6B3E]"
              />

            </div>

            <div>

              <label className="block text-sm font-semibold text-gray-700 mb-1.5">
                Account Number
              </label>

              <input
                type="text"
                value={accountNumber}
                onChange={(e) =>
                  setAccountNumber(
                    e.target.value
                  )
                }
                placeholder="Enter bank account number"
                className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0D6B3E]"
              />

              <p className="text-xs text-gray-400 mt-1">
                Used for identification and reconciliation.
              </p>

            </div>
          </>
        )}

        {/* BRANCH */}

        <div>

          <label className="block text-sm font-semibold text-gray-700 mb-1.5">
            Branch
          </label>

          <select
            value={branchId}
            onChange={(e) =>
              setBranchId(e.target.value)
            }
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-[#0D6B3E]"
          >

            <option value="">
              Head Office / Organization-wide
            </option>

            {branches.map((branch) => (
              <option
                key={branch.id}
                value={branch.id}
              >
                {branch.name}
              </option>
            ))}

          </select>

        </div>

        {/* OPENING BALANCE */}

        <div>

          <label className="block text-sm font-semibold text-gray-700 mb-1.5">
            Opening Balance
          </label>

          <div className="relative">

            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm">
              {currency}
            </span>

            <input
              type="number"
              min="0"
              step="0.01"
              value={openingBalance}
              onChange={(e) =>
                setOpeningBalance(
                  e.target.value
                )
              }
              placeholder="0.00"
              className="w-full pl-14 pr-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0D6B3E]"
            />

          </div>

          <p className="text-xs text-gray-400 mt-1">
            Enter zero if the account has no opening
            balance. You can fund it later using Deposit.
          </p>

        </div>

        {/* ACCOUNTING INFO */}

        <div className="bg-gray-50 border border-gray-200 rounded-xl p-4">

          <div className="text-sm font-semibold text-gray-800">
            Accounting treatment
          </div>

          <p className="text-xs text-gray-500 mt-1 leading-5">
            The system creates a dedicated asset GL
            account. A positive opening balance creates
            an opening journal entry. A zero opening
            balance creates the account at zero and does
            not create a journal entry.
          </p>

        </div>

        {/* BUTTONS */}

        <div className="flex gap-3 pt-2">

          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            className="flex-1 px-4 py-2.5 border border-gray-300 rounded-lg text-sm font-semibold text-gray-700 hover:bg-gray-50 disabled:opacity-50"
          >
            Cancel
          </button>

          <button
            type="submit"
            disabled={saving}
            className="flex-1 px-4 py-2.5 bg-[#0D6B3E] hover:bg-[#09552F] disabled:opacity-50 text-white rounded-lg text-sm font-semibold"
          >
            {saving
              ? 'Creating Account…'
              : 'Create Account'}
          </button>

        </div>

      </form>
    </div>
  );
}

/* =========================================================
   BANK TRANSACTION MODAL
   DEPOSIT / WITHDRAWAL
========================================================= */

function BankTransactionModal({
  account,
  currency,
  counterAccounts,
  onClose,
  onSaved,
}: {
  account: BankAccountRow;
  currency: string;
  counterAccounts: Account[];
  onClose: () => void;
  onSaved: () => Promise<void> | void;
}) {
  const [type, setType] =
    useState<BankTransactionType>('DEPOSIT');

  const [amount, setAmount] = useState('');
  const [counterAccountId, setCounterAccountId] =
    useState('');

  const [description, setDescription] =
    useState('');

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  /* =======================================================
     SUBMIT
  ======================================================= */

  const handleSubmit = async (
    e: React.FormEvent
  ) => {
    e.preventDefault();

    setError('');

    const numericAmount = Number(amount);

    if (
      !Number.isFinite(numericAmount) ||
      numericAmount <= 0
    ) {
      setError(
        'Amount must be greater than zero.'
      );
      return;
    }

    const counterId =
      Number(counterAccountId);

    if (
      !Number.isInteger(counterId) ||
      counterId <= 0
    ) {
      setError(
        'Select the counter GL account.'
      );
      return;
    }

    setSaving(true);

    try {
      /*
       * IMPORTANT:
       *
       * bankAccountApi.recordTransaction()
       * requires:
       *
       * recordTransaction(id, data)
       */

      await bankAccountApi.recordTransaction(
        account.id,
        {
          type,
          amount: numericAmount,
          counterAccountId: counterId,
          description:
            description.trim() ||
            `${type === 'DEPOSIT'
              ? 'Deposit into'
              : 'Withdrawal from'} ${account.name}`,
        }
      );

      await onSaved();
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
    <div className="bg-white rounded-2xl shadow-xl w-full max-w-xl max-h-[90vh] overflow-y-auto">

      {/* HEADER */}

      <div className="px-6 py-5 border-b border-gray-100 flex items-start justify-between">

        <div>

          <h2 className="text-lg font-bold text-gray-900">
            {type === 'DEPOSIT'
              ? 'Deposit Money'
              : 'Withdraw Money'}
          </h2>

          <p className="text-sm text-gray-500 mt-1">
            {account.name}
          </p>

          <p className="text-xs text-gray-400 mt-1">
            Current balance: {currency}{' '}
            {new Intl.NumberFormat('en-US', {
              minimumFractionDigits: 2,
              maximumFractionDigits: 2,
            }).format(account.balance ?? 0)}
          </p>

        </div>

        <button
          type="button"
          onClick={onClose}
          disabled={saving}
          className="text-gray-400 hover:text-gray-700 text-2xl leading-none"
        >
          ×
        </button>

      </div>

      {/* FORM */}

      <form
        onSubmit={handleSubmit}
        className="p-6 space-y-5"
      >

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-sm">
            {error}
          </div>
        )}

        {/* TRANSACTION TYPE */}

        <div>

          <label className="block text-sm font-semibold text-gray-700 mb-2">
            Transaction Type
          </label>

          <div className="grid grid-cols-2 gap-3">

            <button
              type="button"
              onClick={() =>
                setType('DEPOSIT')
              }
              className={`border rounded-xl p-4 text-left ${
                type === 'DEPOSIT'
                  ? 'border-green-600 bg-green-50 ring-1 ring-green-600'
                  : 'border-gray-200 hover:border-gray-300'
              }`}
            >

              <div className="font-semibold text-gray-900">
                ↓ Deposit
              </div>

              <p className="text-xs text-gray-500 mt-1">
                Increase the bank/cash balance.
              </p>

            </button>

            <button
              type="button"
              onClick={() =>
                setType('WITHDRAWAL')
              }
              className={`border rounded-xl p-4 text-left ${
                type === 'WITHDRAWAL'
                  ? 'border-red-600 bg-red-50 ring-1 ring-red-600'
                  : 'border-gray-200 hover:border-gray-300'
              }`}
            >

              <div className="font-semibold text-gray-900">
                ↑ Withdrawal
              </div>

              <p className="text-xs text-gray-500 mt-1">
                Reduce the bank/cash balance.
              </p>

            </button>

          </div>
        </div>

        {/* AMOUNT */}

        <div>

          <label className="block text-sm font-semibold text-gray-700 mb-1.5">
            Amount *
          </label>

          <div className="relative">

            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm">
              {currency}
            </span>

            <input
              type="number"
              required
              min="0.01"
              step="0.01"
              value={amount}
              onChange={(e) =>
                setAmount(e.target.value)
              }
              placeholder="0.00"
              className="w-full pl-14 pr-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0D6B3E]"
            />

          </div>

        </div>

        {/* COUNTER ACCOUNT */}

        <div>

          <label className="block text-sm font-semibold text-gray-700 mb-1.5">
            Counter GL Account *
          </label>

          <select
            required
            value={counterAccountId}
            onChange={(e) =>
              setCounterAccountId(
                e.target.value
              )
            }
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-[#0D6B3E]"
          >

            <option value="">
              Select counter account
            </option>

            {counterAccounts.map((account) => (
              <option
                key={account.id}
                value={account.id}
              >
                {account.code} — {account.name}
              </option>
            ))}

          </select>

          <p className="text-xs text-gray-400 mt-1">
            This is the other side of the journal
            transaction.
          </p>

        </div>

        {/* DESCRIPTION */}

        <div>

          <label className="block text-sm font-semibold text-gray-700 mb-1.5">
            Description
          </label>

          <textarea
            value={description}
            onChange={(e) =>
              setDescription(
                e.target.value
              )
            }
            rows={3}
            placeholder={
              type === 'DEPOSIT'
                ? 'e.g. Cash received from investor'
                : 'e.g. Bank charges or cash payment'
            }
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm resize-none focus:outline-none focus:ring-2 focus:ring-[#0D6B3E]"
          />

        </div>

        {/* ACCOUNTING PREVIEW */}

        <div className="bg-gray-50 border border-gray-200 rounded-xl p-4">

          <div className="text-sm font-semibold text-gray-800">
            Journal treatment
          </div>

          {type === 'DEPOSIT' ? (
            <div className="text-xs text-gray-600 mt-2 space-y-1">
              <div>
                <strong>Debit:</strong>{' '}
                {account.name}
              </div>

              <div>
                <strong>Credit:</strong>{' '}
                Counter GL Account
              </div>
            </div>
          ) : (
            <div className="text-xs text-gray-600 mt-2 space-y-1">
              <div>
                <strong>Debit:</strong>{' '}
                Counter GL Account
              </div>

              <div>
                <strong>Credit:</strong>{' '}
                {account.name}
              </div>
            </div>
          )}

        </div>

        {/* BUTTONS */}

        <div className="flex gap-3 pt-2">

          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            className="flex-1 px-4 py-2.5 border border-gray-300 rounded-lg text-sm font-semibold text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>

          <button
            type="submit"
            disabled={saving}
            className={`flex-1 px-4 py-2.5 text-white rounded-lg text-sm font-semibold disabled:opacity-50 ${
              type === 'DEPOSIT'
                ? 'bg-[#0D6B3E] hover:bg-[#09552F]'
                : 'bg-red-600 hover:bg-red-700'
            }`}
          >
            {saving
              ? 'Processing…'
              : type === 'DEPOSIT'
                ? 'Record Deposit'
                : 'Record Withdrawal'}
          </button>

        </div>

      </form>
    </div>
  );
}

/* =========================================================
   TRANSFER MODAL
========================================================= */

function TransferModal({
  fromAccount,
  accounts,
  currency,
  onClose,
  onSaved,
}: {
  fromAccount: BankAccountRow;
  accounts: BankAccountRow[];
  currency: string;
  onClose: () => void;
  onSaved: () => Promise<void> | void;
}) {
  const [destinationId, setDestinationId] =
    useState('');

  const [amount, setAmount] = useState('');
  const [description, setDescription] =
    useState('');

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const destinationAccounts =
    accounts.filter(
      (account) =>
        account.id !== fromAccount.id &&
        account.active
    );

  /* =======================================================
     SUBMIT
  ======================================================= */

  const handleSubmit = async (
    e: React.FormEvent
  ) => {
    e.preventDefault();

    setError('');

    const destinationAccountId =
      Number(destinationId);

    if (
      !Number.isInteger(
        destinationAccountId
      ) ||
      destinationAccountId <= 0
    ) {
      setError(
        'Select a destination account.'
      );
      return;
    }

    const numericAmount = Number(amount);

    if (
      !Number.isFinite(numericAmount) ||
      numericAmount <= 0
    ) {
      setError(
        'Transfer amount must be greater than zero.'
      );
      return;
    }

    if (
      numericAmount >
      Number(fromAccount.balance ?? 0)
    ) {
      setError(
        `Insufficient balance. Available balance is ${currency} ${new Intl.NumberFormat(
          'en-US',
          {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
          }
        ).format(fromAccount.balance ?? 0)}.`
      );
      return;
    }

    setSaving(true);

    try {
      /*
       * IMPORTANT:
       *
       * bankAccountApi.transfer()
       * accepts ONE object.
       *
       * Correct:
       *
       * transfer({
       *   fromAccountId,
       *   toAccountId,
       *   amount,
       *   description
       * })
       */

      await bankAccountApi.transfer({
        fromAccountId: fromAccount.id,
        toAccountId:
          destinationAccountId,
        amount: numericAmount,
        description:
          description.trim() ||
          `Transfer from ${fromAccount.name}`,
      });

      await onSaved();
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Could not transfer money.'
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="bg-white rounded-2xl shadow-xl w-full max-w-xl max-h-[90vh] overflow-y-auto">

      {/* HEADER */}

      <div className="px-6 py-5 border-b border-gray-100 flex items-start justify-between">

        <div>

          <h2 className="text-lg font-bold text-gray-900">
            Transfer Money
          </h2>

          <p className="text-sm text-gray-500 mt-1">
            Transfer between bank and cash accounts.
          </p>

        </div>

        <button
          type="button"
          onClick={onClose}
          disabled={saving}
          className="text-gray-400 hover:text-gray-700 text-2xl leading-none"
        >
          ×
        </button>

      </div>

      {/* FORM */}

      <form
        onSubmit={handleSubmit}
        className="p-6 space-y-5"
      >

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-sm">
            {error}
          </div>
        )}

        {/* FROM */}

        <div className="bg-gray-50 border border-gray-200 rounded-xl p-4">

          <div className="text-xs text-gray-500 uppercase font-semibold">
            From Account
          </div>

          <div className="font-semibold text-gray-900 mt-1">
            {fromAccount.name}
          </div>

          <div className="text-sm text-gray-500 mt-1">
            Available:{' '}
            <span className="font-mono font-semibold text-gray-900">
              {currency}{' '}
              {new Intl.NumberFormat(
                'en-US',
                {
                  minimumFractionDigits: 2,
                  maximumFractionDigits: 2,
                }
              ).format(
                fromAccount.balance ?? 0
              )}
            </span>
          </div>

        </div>

        {/* DESTINATION */}

        <div>

          <label className="block text-sm font-semibold text-gray-700 mb-1.5">
            Destination Account *
          </label>

          <select
            required
            value={destinationId}
            onChange={(e) =>
              setDestinationId(
                e.target.value
              )
            }
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-[#0D6B3E]"
          >

            <option value="">
              Select destination account
            </option>

            {destinationAccounts.map(
              (account) => (
                <option
                  key={account.id}
                  value={account.id}
                >
                  {account.name} —{' '}
                  {currency}{' '}
                  {new Intl.NumberFormat(
                    'en-US',
                    {
                      minimumFractionDigits: 2,
                      maximumFractionDigits: 2,
                    }
                  ).format(
                    account.balance ?? 0
                  )}
                </option>
              )
            )}

          </select>

          {destinationAccounts.length === 0 && (
            <p className="text-xs text-amber-600 mt-1">
              Create another active bank or cash
              account before making a transfer.
            </p>
          )}

        </div>

        {/* AMOUNT */}

        <div>

          <label className="block text-sm font-semibold text-gray-700 mb-1.5">
            Transfer Amount *
          </label>

          <div className="relative">

            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-sm">
              {currency}
            </span>

            <input
              type="number"
              required
              min="0.01"
              step="0.01"
              value={amount}
              onChange={(e) =>
                setAmount(e.target.value)
              }
              placeholder="0.00"
              className="w-full pl-14 pr-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#0D6B3E]"
            />

          </div>

        </div>

        {/* DESCRIPTION */}

        <div>

          <label className="block text-sm font-semibold text-gray-700 mb-1.5">
            Description
          </label>

          <textarea
            value={description}
            onChange={(e) =>
              setDescription(
                e.target.value
              )
            }
            rows={3}
            placeholder="e.g. Transfer operating funds to branch account"
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm resize-none focus:outline-none focus:ring-2 focus:ring-[#0D6B3E]"
          />

        </div>

        {/* ACCOUNTING PREVIEW */}

        <div className="bg-blue-50 border border-blue-100 rounded-xl p-4">

          <div className="text-sm font-semibold text-blue-900">
            Accounting treatment
          </div>

          <div className="text-xs text-blue-700 mt-2 space-y-1">

            <div>
              <strong>Debit:</strong>{' '}
              Destination account
            </div>

            <div>
              <strong>Credit:</strong>{' '}
              {fromAccount.name}
            </div>

          </div>

          <p className="text-xs text-blue-600 mt-2">
            This is an internal transfer. It does not
            create income or expense.
          </p>

        </div>

        {/* BUTTONS */}

        <div className="flex gap-3 pt-2">

          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            className="flex-1 px-4 py-2.5 border border-gray-300 rounded-lg text-sm font-semibold text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>

          <button
            type="submit"
            disabled={
              saving ||
              destinationAccounts.length === 0
            }
            className="flex-1 px-4 py-2.5 bg-[#0D6B3E] hover:bg-[#09552F] disabled:opacity-50 text-white rounded-lg text-sm font-semibold"
          >
            {saving
              ? 'Transferring…'
              : 'Transfer Money'}
          </button>

        </div>

      </form>
    </div>
  );
}
