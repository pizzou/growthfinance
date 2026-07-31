'use client';
import { useEffect, useState } from 'react';
import { accountingApi, bankAccountApi } from '@/services/api';
import { PageSpinner } from '@/components/ui/Skeleton';
import { useAuth } from '@/hooks/useAuth';

interface Account {
  id: number; code: string; name: string;
  type: 'ASSET' | 'LIABILITY' | 'EQUITY' | 'INCOME' | 'EXPENSE';
  normalBalance: 'DEBIT' | 'CREDIT'; active: boolean;
}
interface JournalLine { id: number; account: Account; debit: number; credit: number; description?: string; }
interface JournalEntryRow {
  id: number; entryDate: string; reference: string; sourceType: string;
  description: string; createdBy: string; reversed: boolean; lines: JournalLine[]; branchName?: string;
}
interface TrialBalanceRow { code: string; name: string; type: string; debit: number; credit: number; }
interface TrialBalance { accounts: TrialBalanceRow[]; totalDebit: number; totalCredit: number; balanced: boolean; }
interface StatementRow { code: string; name: string; balance: number; }
interface BalanceSheet {
  assets: StatementRow[]; liabilities: StatementRow[]; equity: StatementRow[];
  currentPeriodNetIncome: number; totalAssets: number; totalLiabilities: number; totalEquity: number; balanced: boolean;
}
interface PnlRow { code: string; name: string; amount: number; }
interface ProfitAndLoss { income: PnlRow[]; expense: PnlRow[]; totalIncome: number; totalExpense: number; netIncome: number; }
interface CashFlow {
  cashUsedForLending: number; cashFromCollections: number; cashFromFees: number;
  otherCashMovement: number; netChangeInCash: number;
}
interface BranchSummaryRow { branch: string; disbursed: number; collected: number; feeIncome: number; }
interface BankAccountRow {
  id: number; name: string; accountType: string; bankName?: string; accountNumber?: string;
  branchName?: string; glAccountCode: string; active: boolean; balance: number;
}

const TYPE_COLORS: Record<string, string> = {
  ASSET: 'bg-blue-50 text-blue-700', LIABILITY: 'bg-orange-50 text-orange-700',
  EQUITY: 'bg-purple-50 text-purple-700', INCOME: 'bg-green-50 text-green-700', EXPENSE: 'bg-red-50 text-red-700',
};

const TABS = ['Trial Balance', 'Balance Sheet', 'Profit & Loss', 'Cash Flow', 'Chart of Accounts', 'Journal', 'Bank Accounts', 'Branches'] as const;
type Tab = typeof TABS[number];

export default function AccountingPage() {
  const { currency } = useAuth();
  const [tab, setTab] = useState<Tab>('Trial Balance');
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [journal, setJournal]   = useState<JournalEntryRow[]>([]);
  const [trial, setTrial]       = useState<TrialBalance | null>(null);
  const [balanceSheet, setBalanceSheet] = useState<BalanceSheet | null>(null);
  const [pnl, setPnl]           = useState<ProfitAndLoss | null>(null);
  const [cashFlow, setCashFlow] = useState<CashFlow | null>(null);
  const [branchSummary, setBranchSummary] = useState<BranchSummaryRow[]>([]);
  const [bankAccounts, setBankAccounts]   = useState<BankAccountRow[]>([]);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState('');
  const [expanded, setExpanded] = useState<number | null>(null);

  const loadAll = () => {
    Promise.all([
      accountingApi.chartOfAccounts().catch(() => []),
      accountingApi.journal().catch(() => []),
      accountingApi.trialBalance().catch(() => null),
      accountingApi.balanceSheet().catch(() => null),
      accountingApi.profitAndLoss().catch(() => null),
      accountingApi.cashFlow().catch(() => null),
      accountingApi.branchSummary().catch(() => []),
      bankAccountApi.list().catch(() => []),
    ])
      .then(([a, j, t, bs, pl, cf, brs, ba]) => {
        setAccounts(a as Account[]);
        setJournal(j as JournalEntryRow[]);
        setTrial(t as TrialBalance);
        setBalanceSheet(bs as BalanceSheet);
        setPnl(pl as ProfitAndLoss);
        setCashFlow(cf as CashFlow);
        setBranchSummary(brs as BranchSummaryRow[]);
        setBankAccounts(ba as BankAccountRow[]);
      })
      .catch(() => setError('Could not load accounting data.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { loadAll(); }, []);

  const handleReverse = async (id: number) => {
    const reason = window.prompt('Reason for reversing this entry (optional):') ?? '';
    try {
      await accountingApi.reverseEntry(id, reason || undefined);
      loadAll();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Could not reverse entry';
      setError(msg);
    }
  };

  const fmt = (n: number) =>
    new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n ?? 0);

  if (loading) return <PageSpinner />;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Accounting</h1>
        <p className="text-gray-500 text-sm mt-1">General ledger, chart of accounts, and trial balance — {currency}.</p>
      </div>

      {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg px-4 py-3">{error}</div>}

      {/* Tabs */}
      <div className="flex gap-1 border-b border-gray-200">
        {TABS.map(t => (
          <button key={t} onClick={() => setTab(t)}
            className={`px-4 py-2.5 text-sm font-semibold border-b-2 transition-colors
              ${tab === t ? 'border-[#0D6B3E] text-[#0D6B3E]' : 'border-transparent text-gray-500 hover:text-gray-700'}`}>
            {t}
          </button>
        ))}
      </div>

      {/* Trial Balance */}
      {tab === 'Trial Balance' && trial && (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
            <div className="font-bold text-gray-900">Trial Balance</div>
            <span className={`text-xs font-bold px-3 py-1.5 rounded-full ${trial.balanced ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'}`}>
              {trial.balanced ? '✓ Balanced' : '⚠ Out of Balance'}
            </span>
          </div>
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wider">
              <tr>
                <th className="text-left px-5 py-2.5 font-semibold">Code</th>
                <th className="text-left px-5 py-2.5 font-semibold">Account</th>
                <th className="text-left px-5 py-2.5 font-semibold">Type</th>
                <th className="text-right px-5 py-2.5 font-semibold">Debit</th>
                <th className="text-right px-5 py-2.5 font-semibold">Credit</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {trial.accounts.map(row => (
                <tr key={row.code} className="hover:bg-gray-50">
                  <td className="px-5 py-2.5 font-mono text-gray-500">{row.code}</td>
                  <td className="px-5 py-2.5 font-medium text-gray-900">{row.name}</td>
                  <td className="px-5 py-2.5">
                    <span className={`text-[10px] font-bold px-2 py-0.5 rounded ${TYPE_COLORS[row.type] || 'bg-gray-100 text-gray-600'}`}>{row.type}</span>
                  </td>
                  <td className="px-5 py-2.5 text-right font-mono">{row.debit ? fmt(row.debit) : ''}</td>
                  <td className="px-5 py-2.5 text-right font-mono">{row.credit ? fmt(row.credit) : ''}</td>
                </tr>
              ))}
            </tbody>
            <tfoot className="bg-gray-50 font-bold border-t-2 border-gray-200">
              <tr>
                <td colSpan={3} className="px-5 py-3 text-right">Totals</td>
                <td className="px-5 py-3 text-right font-mono">{fmt(trial.totalDebit)}</td>
                <td className="px-5 py-3 text-right font-mono">{fmt(trial.totalCredit)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}

      {/* Chart of Accounts */}
      {tab === 'Chart of Accounts' && (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wider">
              <tr>
                <th className="text-left px-5 py-2.5 font-semibold">Code</th>
                <th className="text-left px-5 py-2.5 font-semibold">Account Name</th>
                <th className="text-left px-5 py-2.5 font-semibold">Type</th>
                <th className="text-left px-5 py-2.5 font-semibold">Normal Balance</th>
                <th className="text-left px-5 py-2.5 font-semibold">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {accounts.map(acc => (
                <tr key={acc.id} className="hover:bg-gray-50">
                  <td className="px-5 py-2.5 font-mono text-gray-500">{acc.code}</td>
                  <td className="px-5 py-2.5 font-medium text-gray-900">{acc.name}</td>
                  <td className="px-5 py-2.5">
                    <span className={`text-[10px] font-bold px-2 py-0.5 rounded ${TYPE_COLORS[acc.type] || 'bg-gray-100 text-gray-600'}`}>{acc.type}</span>
                  </td>
                  <td className="px-5 py-2.5 text-gray-600">{acc.normalBalance}</td>
                  <td className="px-5 py-2.5">
                    <span className={`text-[10px] font-bold px-2 py-0.5 rounded ${acc.active ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-500'}`}>
                      {acc.active ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                </tr>
              ))}
              {accounts.length === 0 && (
                <tr><td colSpan={5} className="px-5 py-8 text-center text-gray-400">No accounts found.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Journal */}
      {tab === 'Journal' && (
        <div className="bg-white rounded-xl border border-gray-200 divide-y divide-gray-100">
          {journal.length === 0 && <div className="px-5 py-8 text-center text-gray-400 text-sm">No journal entries yet.</div>}
          {journal.map(entry => (
            <div key={entry.id}>
              <div
                role="button" tabIndex={0}
                onClick={() => setExpanded(expanded === entry.id ? null : entry.id)}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') setExpanded(expanded === entry.id ? null : entry.id); }}
                className="w-full flex items-center justify-between px-5 py-3.5 hover:bg-gray-50 text-left cursor-pointer">
                <div className="flex items-center gap-3">
                  <span className="text-xs text-gray-400 w-24 shrink-0">{new Date(entry.entryDate).toLocaleDateString()}</span>
                  <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-gray-100 text-gray-600">{entry.sourceType}</span>
                  <span className="text-sm font-medium text-gray-900">{entry.description}</span>
                  {entry.branchName && <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-indigo-50 text-indigo-600">{entry.branchName}</span>}
                  {entry.reversed && <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-red-50 text-red-600">REVERSED</span>}
                </div>
                <div className="flex items-center gap-3 text-xs text-gray-400">
                  <code>{entry.reference}</code>
                  {!entry.reversed && entry.sourceType !== 'REVERSAL' && (
                    <button
                      onClick={(e) => { e.stopPropagation(); handleReverse(entry.id); }}
                      className="text-red-500 hover:text-red-700 font-semibold border border-red-100 bg-white hover:bg-red-50 px-2 py-1 rounded"
                    >
                      Reverse
                    </button>
                  )}
                  <span>{expanded === entry.id ? '▲' : '▼'}</span>
                </div>
              </div>
              {expanded === entry.id && (
                <div className="px-5 pb-4">
                  <table className="w-full text-xs bg-gray-50 rounded-lg overflow-hidden">
                    <thead className="text-gray-500 uppercase">
                      <tr>
                        <th className="text-left px-3 py-2">Account</th>
                        <th className="text-right px-3 py-2">Debit</th>
                        <th className="text-right px-3 py-2">Credit</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200">
                      {entry.lines?.map(line => (
                        <tr key={line.id}>
                          <td className="px-3 py-2">{line.account?.code} — {line.account?.name}</td>
                          <td className="px-3 py-2 text-right font-mono">{line.debit ? fmt(line.debit) : ''}</td>
                          <td className="px-3 py-2 text-right font-mono">{line.credit ? fmt(line.credit) : ''}</td>
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
      {/* Balance Sheet */}
      {tab === 'Balance Sheet' && balanceSheet && (
        <div className="bg-white rounded-xl border border-gray-200 p-5 space-y-6">
          <div className="flex items-center justify-between">
            <div className="font-bold text-gray-900">Balance Sheet (as of today)</div>
            <span className={`text-xs font-bold px-3 py-1.5 rounded-full ${balanceSheet.balanced ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'}`}>
              {balanceSheet.balanced ? '✓ Balanced' : '⚠ Out of Balance'}
            </span>
          </div>
          {([
            ['Assets', balanceSheet.assets, balanceSheet.totalAssets],
            ['Liabilities', balanceSheet.liabilities, balanceSheet.totalLiabilities],
            ['Equity (incl. current period net income)', balanceSheet.equity, balanceSheet.totalEquity],
          ] as const).map(([label, rows, total]) => (
            <div key={label}>
              <div className="text-sm font-bold text-gray-700 mb-2">{label}</div>
              <div className="divide-y divide-gray-100 border border-gray-100 rounded-lg overflow-hidden">
                {rows.map(r => (
                  <div key={r.code} className="flex justify-between px-4 py-2 text-sm">
                    <span className="text-gray-600">{r.code} — {r.name}</span>
                    <span className="font-mono">{fmt(r.balance)}</span>
                  </div>
                ))}
                {label.startsWith('Equity') && (
                  <div className="flex justify-between px-4 py-2 text-sm bg-gray-50">
                    <span className="text-gray-600">Current Period Net Income</span>
                    <span className="font-mono">{fmt(balanceSheet.currentPeriodNetIncome)}</span>
                  </div>
                )}
                <div className="flex justify-between px-4 py-2 text-sm font-bold bg-gray-50 border-t border-gray-200">
                  <span>Total {label.split(' ')[0]}</span>
                  <span className="font-mono">{fmt(total)}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Profit & Loss */}
      {tab === 'Profit & Loss' && pnl && (
        <div className="bg-white rounded-xl border border-gray-200 p-5 space-y-6">
          <div className="font-bold text-gray-900">Profit &amp; Loss (this month to date)</div>
          {([['Income', pnl.income, pnl.totalIncome], ['Expense', pnl.expense, pnl.totalExpense]] as const).map(([label, rows, total]) => (
            <div key={label}>
              <div className="text-sm font-bold text-gray-700 mb-2">{label}</div>
              <div className="divide-y divide-gray-100 border border-gray-100 rounded-lg overflow-hidden">
                {rows.map(r => (
                  <div key={r.code} className="flex justify-between px-4 py-2 text-sm">
                    <span className="text-gray-600">{r.code} — {r.name}</span>
                    <span className="font-mono">{fmt(r.amount)}</span>
                  </div>
                ))}
                {rows.length === 0 && <div className="px-4 py-3 text-sm text-gray-400">No activity this period.</div>}
                <div className="flex justify-between px-4 py-2 text-sm font-bold bg-gray-50 border-t border-gray-200">
                  <span>Total {label}</span><span className="font-mono">{fmt(total)}</span>
                </div>
              </div>
            </div>
          ))}
          <div className={`flex justify-between px-4 py-3 rounded-lg font-bold text-sm ${pnl.netIncome >= 0 ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'}`}>
            <span>Net Income</span><span className="font-mono">{fmt(pnl.netIncome)}</span>
          </div>
        </div>
      )}

      {/* Cash Flow */}
      {tab === 'Cash Flow' && cashFlow && (
        <div className="bg-white rounded-xl border border-gray-200 p-5 space-y-2">
          <div className="font-bold text-gray-900 mb-3">Cash Flow (this month to date)</div>
          {[
            ['Cash Used for Lending (disbursements)', cashFlow.cashUsedForLending],
            ['Cash From Collections (principal + interest + penalties)', cashFlow.cashFromCollections],
            ['Cash From Fees', cashFlow.cashFromFees],
            ['Other Cash Movement', cashFlow.otherCashMovement],
          ].map(([label, val]) => (
            <div key={label as string} className="flex justify-between px-4 py-2 text-sm border-b border-gray-100">
              <span className="text-gray-600">{label}</span><span className="font-mono">{fmt(val as number)}</span>
            </div>
          ))}
          <div className="flex justify-between px-4 py-3 rounded-lg font-bold text-sm bg-gray-50 mt-2">
            <span>Net Change in Cash</span><span className="font-mono">{fmt(cashFlow.netChangeInCash)}</span>
          </div>
        </div>
      )}

      {/* Bank Accounts / Cashbook */}
      {tab === 'Bank Accounts' && (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wider">
              <tr>
                <th className="text-left px-5 py-2.5 font-semibold">Name</th>
                <th className="text-left px-5 py-2.5 font-semibold">Type</th>
                <th className="text-left px-5 py-2.5 font-semibold">Bank / Branch</th>
                <th className="text-left px-5 py-2.5 font-semibold">GL Code</th>
                <th className="text-right px-5 py-2.5 font-semibold">Balance</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {bankAccounts.map(a => (
                <tr key={a.id} className="hover:bg-gray-50">
                  <td className="px-5 py-2.5 font-medium text-gray-900">{a.name}</td>
                  <td className="px-5 py-2.5">
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-blue-50 text-blue-700">{a.accountType}</span>
                  </td>
                  <td className="px-5 py-2.5 text-gray-600">{a.bankName || a.branchName || '—'}</td>
                  <td className="px-5 py-2.5 font-mono text-gray-500">{a.glAccountCode}</td>
                  <td className="px-5 py-2.5 text-right font-mono">{fmt(a.balance)}</td>
                </tr>
              ))}
              {bankAccounts.length === 0 && (
                <tr><td colSpan={5} className="px-5 py-8 text-center text-gray-400">No bank/cash accounts set up yet.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Branches */}
      {tab === 'Branches' && (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wider">
              <tr>
                <th className="text-left px-5 py-2.5 font-semibold">Branch</th>
                <th className="text-right px-5 py-2.5 font-semibold">Disbursed</th>
                <th className="text-right px-5 py-2.5 font-semibold">Collected</th>
                <th className="text-right px-5 py-2.5 font-semibold">Fee Income</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {branchSummary.map(r => (
                <tr key={r.branch} className="hover:bg-gray-50">
                  <td className="px-5 py-2.5 font-medium text-gray-900">{r.branch}</td>
                  <td className="px-5 py-2.5 text-right font-mono">{fmt(r.disbursed)}</td>
                  <td className="px-5 py-2.5 text-right font-mono">{fmt(r.collected)}</td>
                  <td className="px-5 py-2.5 text-right font-mono">{fmt(r.feeIncome)}</td>
                </tr>
              ))}
              {branchSummary.length === 0 && (
                <tr><td colSpan={4} className="px-5 py-8 text-center text-gray-400">No branch activity this period.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
