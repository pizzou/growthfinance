'use client';
import { useState } from 'react';

interface Installment { month: number; payment: number; principal: number; interest: number; balance: number; }

export default function CalculatorPage() {
  const [amount,    setAmount]    = useState('');
  const [rate,      setRate]      = useState('');
  const [duration,  setDuration]  = useState('');
  const [currency,  setCurrency]  = useState('USD');
  const [schedule,  setSchedule]  = useState<Installment[]>([]);
  const [summary,   setSummary]   = useState<{ monthly: number; total: number; interest: number } | null>(null);

  const calculate = () => {
    const P = parseFloat(amount);
    const r = parseFloat(rate) / 100 / 12;
    const n = parseInt(duration);
    if (!P || !r || !n || P <= 0 || r <= 0 || n <= 0) return;

    const monthly = r === 0 ? P / n : (P * r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1);
    const totalPayment = monthly * n;
    const totalInterest = totalPayment - P;

    const sched: Installment[] = [];
    let balance = P;
    for (let i = 1; i <= n; i++) {
      const interest  = balance * r;
      const principal = monthly - interest;
      balance -= principal;
      sched.push({
        month: i,
        payment: monthly,
        principal,
        interest,
        balance: Math.max(balance, 0),
      });
    }
    setSchedule(sched);
    setSummary({ monthly, total: totalPayment, interest: totalInterest });
  };

  const fmt = (n: number) => currency + ' ' + n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  const exportCSV = () => {
    const rows = [
      ['Month', 'Payment', 'Principal', 'Interest', 'Balance'],
      ...schedule.map(s => [String(s.month), s.payment.toFixed(2), s.principal.toFixed(2), s.interest.toFixed(2), s.balance.toFixed(2)])
    ];
    const csv  = rows.map(r => r.join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const a    = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = 'loan-schedule.csv'; a.click();
  };

  return (
    <div className="space-y-6 max-w-4xl">
      <div>
        <h1 className="text-xl font-bold text-gray-900">Loan Calculator</h1>
        <p className="text-sm text-gray-500">Calculate monthly payments and full amortization schedule</p>
      </div>

      {/* Input card */}
      <div className="bg-white rounded-2xl border border-gray-100 p-6">
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
          <div>
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">Currency</label>
            <select value={currency} onChange={e => setCurrency(e.target.value)}
              className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-green-400">
              {['USD','EUR','GBP','RWF','KES','UGX','TZS'].map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">Loan Amount</label>
            <input type="number" value={amount} onChange={e => setAmount(e.target.value)}
              placeholder="e.g. 10000"
              className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-green-400" />
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">Annual Rate (%)</label>
            <input type="number" value={rate} onChange={e => setRate(e.target.value)}
              placeholder="e.g. 12"
              className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-green-400" />
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">Duration (months)</label>
            <input type="number" value={duration} onChange={e => setDuration(e.target.value)}
              placeholder="e.g. 24"
              className="w-full border border-gray-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-green-400" />
          </div>
        </div>
        <button onClick={calculate}
          className="w-full bg-green-500 hover:bg-green-600 text-white font-semibold py-3 rounded-xl transition text-sm">
          Calculate Repayment Schedule
        </button>
      </div>

      {/* Summary */}
      {summary && (
        <div className="grid grid-cols-3 gap-4">
          {[
            { label: 'Monthly Payment', value: fmt(summary.monthly), color: 'text-green-600' },
            { label: 'Total Repayment', value: fmt(summary.total),   color: 'text-indigo-600' },
            { label: 'Total Interest',  value: fmt(summary.interest), color: 'text-orange-500' },
          ].map(s => (
            <div key={s.label} className="bg-white rounded-2xl border border-gray-100 p-5 text-center">
              <p className="text-gray-400 text-xs mb-1">{s.label}</p>
              <p className={`text-xl font-bold ${s.color}`}>{s.value}</p>
            </div>
          ))}
        </div>
      )}

      {/* Visual breakdown */}
      {summary && (
        <div className="bg-white rounded-2xl border border-gray-100 p-6">
          <h2 className="font-semibold text-gray-800 text-sm mb-4">Principal vs Interest Breakdown</h2>
          <div className="flex rounded-full overflow-hidden h-6 mb-3">
            <div className="bg-green-500 flex items-center justify-center text-white text-xs font-medium"
              style={{ width: (parseFloat(amount) / summary.total * 100) + '%' }}>
              Principal
            </div>
            <div className="bg-orange-400 flex items-center justify-center text-white text-xs font-medium flex-1">
              Interest
            </div>
          </div>
          <div className="flex justify-between text-xs text-gray-500">
            <span>🟢 Principal: {(parseFloat(amount) / summary.total * 100).toFixed(1)}%</span>
            <span>🟠 Interest: {(summary.interest / summary.total * 100).toFixed(1)}%</span>
          </div>
        </div>
      )}

      {/* Schedule table */}
      {schedule.length > 0 && (
        <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
          <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
            <h2 className="font-semibold text-gray-800 text-sm">Amortization Schedule ({schedule.length} installments)</h2>
            <button onClick={exportCSV}
              className="text-xs font-medium text-green-600 border border-green-200 bg-green-50 hover:bg-green-100 px-3 py-1.5 rounded-xl transition">
              ↓ Export CSV
            </button>
          </div>
          <div className="overflow-x-auto max-h-96 overflow-y-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 sticky top-0">
                <tr>
                  {['Month','Payment','Principal','Interest','Balance'].map(h => (
                    <th key={h} className="px-5 py-3 text-left text-xs font-semibold text-gray-500 uppercase">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {schedule.map(s => (
                  <tr key={s.month} className="hover:bg-gray-50 transition-colors">
                    <td className="px-5 py-3 font-medium text-gray-700">Month {s.month}</td>
                    <td className="px-5 py-3 font-semibold text-gray-900">{fmt(s.payment)}</td>
                    <td className="px-5 py-3 text-green-600">{fmt(s.principal)}</td>
                    <td className="px-5 py-3 text-orange-500">{fmt(s.interest)}</td>
                    <td className="px-5 py-3 text-gray-500">{fmt(s.balance)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
