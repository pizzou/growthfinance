'use client';
import type { ReactNode } from 'react';
import { LoanStatus, RiskCategory } from '@/types';
import { STATUS_COLORS, RISK_COLORS } from '@/lib/utils';

export function StatusBadge({ status }: { status: LoanStatus }) {
  return (
    <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold border ${STATUS_COLORS[status] || 'bg-gray-100 text-gray-700'}`}>
      <span className="w-1.5 h-1.5 rounded-full bg-current opacity-70" />
      {status.replace(/_/g, ' ')}
    </span>
  );
}

export function RiskBadge({ category, score }: { category: RiskCategory; score?: number }) {
  return (
    <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold ${RISK_COLORS[category] || 'bg-gray-100 text-gray-600'}`}>
      {category}
      {score != null && <span className="opacity-60">({score})</span>}
    </span>
  );
}

export function Pill({ label, color = 'gray' }: { label: ReactNode; color?: string }) {
  const colors: Record<string, string> = {
    gray:   'bg-gray-100 text-gray-700',
    blue:   'bg-blue-100 text-blue-700',
    green:  'bg-green-100 text-green-700',
    red:    'bg-red-100 text-red-700',
    yellow: 'bg-yellow-100 text-yellow-700',
    purple: 'bg-purple-100 text-purple-700',
    teal:   'bg-teal-100 text-teal-700',
  };
  return (
    <span className={`inline-flex px-2.5 py-0.5 rounded-full text-xs font-semibold ${colors[color] || colors.gray}`}>
      {label}
    </span>
  );
}
