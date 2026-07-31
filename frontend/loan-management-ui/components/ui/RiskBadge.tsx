import { RiskCategory } from '../../types/index';

const COLORS: Record<RiskCategory, { bg: string; text: string; bar: string }> = {
  LOW:      { bg: 'bg-green-50',  text: 'text-green-700',  bar: 'bg-green-500'  },
  MEDIUM:   { bg: 'bg-yellow-50', text: 'text-yellow-700', bar: 'bg-yellow-400' },
  HIGH:     { bg: 'bg-orange-50', text: 'text-orange-700', bar: 'bg-orange-500' },
  CRITICAL: { bg: 'bg-red-50',    text: 'text-red-700',    bar: 'bg-red-500'    },
};

export function RiskBadge({ category }: { category: RiskCategory }) {
  const c = COLORS[category] ?? COLORS.HIGH;
  return (
    <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${c.bg} ${c.text}`}>
      {category} RISK
    </span>
  );
}

interface RiskGaugeProps {
  score:           number;
  category:        RiskCategory;
  recommendation:  string;
  repaymentFactor: number;
  creditFactor:    number;
  ltvFactor:       number;
  kycFactor:       number;
}

export function RiskGauge({
  score, category, recommendation,
  repaymentFactor, creditFactor, ltvFactor, kycFactor,
}: RiskGaugeProps) {
  const c = COLORS[category] ?? COLORS.HIGH;
  const factors = [
    { label: 'Repayment History', value: repaymentFactor, max: 40 },
    { label: 'Credit Score',      value: creditFactor,    max: 25 },
    { label: 'Loan-to-Value',     value: ltvFactor,       max: 20 },
    { label: 'KYC Status',        value: kycFactor,       max: 10 },
  ];
  return (
    <div className={`rounded-xl border p-5 ${c.bg} border-current/10`}>
      <div className="flex items-center justify-between mb-4">
        <div>
          <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">
            Risk Assessment
          </p>
          <div className="flex items-center gap-3 mt-1">
            <span className={`text-3xl font-bold ${c.text}`}>{score.toFixed(1)}</span>
            <RiskBadge category={category} />
          </div>
        </div>
        <div className="w-16 h-16 relative">
          <svg viewBox="0 0 36 36" className="w-full h-full -rotate-90">
            <circle cx="18" cy="18" r="14" fill="none" stroke="#e5e7eb" strokeWidth="4" />
            <circle cx="18" cy="18" r="14" fill="none"
              stroke={
                category === 'LOW'      ? '#22c55e' :
                category === 'MEDIUM'   ? '#eab308' :
                category === 'HIGH'     ? '#f97316' : '#ef4444'
              }
              strokeWidth="4"
              strokeDasharray={`${(score / 100) * 88} 88`}
              strokeLinecap="round"
            />
          </svg>
          <span className="absolute inset-0 flex items-center justify-center text-xs font-bold text-gray-700">
            {Math.round(score)}
          </span>
        </div>
      </div>
      <p className="text-sm text-gray-600 mb-4 italic">{recommendation}</p>
      <div className="space-y-2">
        {factors.map(f => (
          <div key={f.label}>
            <div className="flex justify-between text-xs text-gray-500 mb-0.5">
              <span>{f.label}</span>
              <span>{f.value.toFixed(1)} / {f.max}</span>
            </div>
            <div className="w-full bg-white/60 rounded-full h-1.5">
              <div
                className={`${c.bar} h-1.5 rounded-full`}
                style={{ width: `${(f.value / f.max) * 100}%` }}
              />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}