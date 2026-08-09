import { LoanStatus, RiskCategory } from '@/types';

export function formatCurrency(
  amount: number | null | undefined,
  currency = 'USD',
  locale = 'en-US'
): string {
  if (amount == null) return '—';
  try {
    return new Intl.NumberFormat(locale, {
      style: 'currency', currency,
      minimumFractionDigits: 0, maximumFractionDigits: 0,
    }).format(amount);
  } catch {
    return `${currency} ${amount.toLocaleString()}`;
  }
}

export function formatDate(date: string | null | undefined, locale = 'en-US'): string {
  if (!date) return '—';
  return new Date(date).toLocaleDateString(locale, { year: 'numeric', month: 'short', day: 'numeric' });
}

export function formatDateTime(date: string | null | undefined): string {
  if (!date) return '—';
  return new Date(date).toLocaleString('en-US', {
    year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

export function formatRelative(date: string | null | undefined): string {
  if (!date) return '—';
  const diff = Date.now() - new Date(date).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  const days = Math.floor(hrs / 24);
  if (days < 7) return `${days}d ago`;
  return formatDate(date);
}

export function formatNumber(n: number | null | undefined): string {
  if (n == null) return '—';
  return new Intl.NumberFormat('en').format(n);
}

export function repaymentProgress(totalRepayable: number, totalPaid: number): number {
  if (!totalRepayable || totalRepayable === 0) return 0;
  return Math.min(100, Math.round((totalPaid / totalRepayable) * 100));
}

/** Renders an interest rate with the correct basis label. Every rate in this system is
 *  MONTHLY now (Annual has been removed) — the ANNUAL branch only remains as a defensive
 *  fallback in case older stored data is ever encountered. */
export function formatInterestRate(
  rate: number | null | undefined,
  rateType?: string | null
): string {
  if (rate == null) return '—';
  return rateType === 'MONTHLY' ? `${rate}% per month` : `${rate}% p.a.`;
}

export const STATUS_COLORS: Record<LoanStatus, string> = {
  PENDING:       'bg-yellow-100 text-yellow-800 border-yellow-200',
  UNDER_REVIEW:  'bg-blue-100 text-blue-800 border-blue-200',
  APPROVED:      'bg-green-100 text-green-800 border-green-200',
  REJECTED:      'bg-red-100 text-red-800 border-red-200',
  DISBURSED:     'bg-purple-100 text-purple-800 border-purple-200',
  ACTIVE:        'bg-emerald-100 text-emerald-800 border-emerald-200',
  OVERDUE:       'bg-orange-100 text-orange-800 border-orange-200',
  DEFAULTED:     'bg-red-200 text-red-900 border-red-300',
  RESTRUCTURED:  'bg-indigo-100 text-indigo-800 border-indigo-200',
  WRITTEN_OFF:   'bg-gray-200 text-gray-700 border-gray-300',
  PAID:          'bg-teal-100 text-teal-800 border-teal-200',
  CLOSED:        'bg-gray-100 text-gray-600 border-gray-200',
  CANCELLED:     'bg-gray-100 text-gray-500 border-gray-200',
};

export const RISK_COLORS: Record<RiskCategory, string> = {
  LOW:      'bg-green-100 text-green-700',
  MEDIUM:   'bg-yellow-100 text-yellow-700',
  HIGH:     'bg-orange-100 text-orange-700',
  CRITICAL: 'bg-red-100 text-red-700',
};

export const LOAN_TYPE_META: Record<string, { icon: string; label: string; rate: number }> = {
  PERSONAL:       { icon: '👤', label: 'Personal',              rate: 10   },
  MORTGAGE:       { icon: '🏠', label: 'Home Loan',              rate: 8.5  },
  AUTO:           { icon: '🚗', label: 'Car Loan',               rate: 10   },
  BUSINESS:       { icon: '🏢', label: 'Business Loan',          rate: 12   },
  STUDENT:        { icon: '🎓', label: 'Students Loan',          rate: 7    },
  EMERGENCY:      { icon: '⚡', label: 'Emergency Loan',         rate: 18   },
  ASSET_FINANCE:  { icon: '⚙️', label: 'Equipments Financing',   rate: 11   },
  SALARY_ADVANCE: { icon: '💵', label: 'Salary Loan',            rate: 5    },
  MICROFINANCE:   { icon: '💡', label: 'Microfinance',           rate: 20   },
  AGRICULTURAL:   { icon: '🌾', label: 'Agricultural',           rate: 9    },
  TRADE_FINANCE:  { icon: '📦', label: 'Contract / Invoice Financing', rate: 13 },
  GROUP:          { icon: '👥', label: 'Group Loan',             rate: 14   },
};

export const SUPPORTED_CURRENCIES = [
  'USD','EUR','GBP','KES','UGX','TZS','RWF','ETB',
  'NGN','GHS','ZAR','INR','AED','SAR','EGP','BRL','PHP',
];

export const COUNTRIES = [
  
   { code: 'RW', name: 'Rwanda' },      { code: 'UG', name: 'Uganda' },
  { code: 'TZ', name: 'Tanzania' },     { code: 'KE', name: 'Kenya' }, 
  { code: 'ET', name: 'Ethiopia' },     { code: 'NG', name: 'Nigeria' },
  { code: 'GH', name: 'Ghana' },        { code: 'ZA', name: 'South Africa' },
  { code: 'EG', name: 'Egypt' },        { code: 'IN', name: 'India' },
  { code: 'US', name: 'United States' },{ code: 'GB', name: 'United Kingdom' },
  { code: 'AE', name: 'UAE' },          { code: 'BR', name: 'Brazil' },
  { code: 'PH', name: 'Philippines' },
];

export function calcLoan(principal: number, annualRate: number, months: number) {
  const mr = annualRate / 100 / 12;
  if (mr === 0) return { monthly: principal / months, total: principal, interest: 0 };
  const monthly = principal * (mr * Math.pow(1 + mr, months)) / (Math.pow(1 + mr, months) - 1);
  const total   = monthly * months;
  return {
    monthly: Math.round(monthly * 100) / 100,
    total:   Math.round(total   * 100) / 100,
    interest:Math.round((total - principal) * 100) / 100,
  };
}