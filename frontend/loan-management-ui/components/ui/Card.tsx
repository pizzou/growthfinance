'use client';
import React from 'react';

interface CardProps { children: React.ReactNode; className?: string; onClick?: () => void; }
export function Card({ children, className = '', onClick }: CardProps) {
  return (
    <div onClick={onClick}
      className={`bg-white rounded-xl border border-gray-200 shadow-sm ${onClick ? 'cursor-pointer hover:border-teal-400 hover:shadow-md transition-all' : ''} ${className}`}>
      {children}
    </div>
  );
}

export function CardHeader({ title, subtitle, action }: { title: string; subtitle?: string; action?: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between px-5 py-4 border-b border-gray-100">
      <div>
        <h3 className="text-sm font-bold text-gray-900">{title}</h3>
        {subtitle && <p className="text-xs text-gray-500 mt-0.5">{subtitle}</p>}
      </div>
      {action && <div className="ml-4">{action}</div>}
    </div>
  );
}

export function CardBody({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return <div className={`px-5 py-4 ${className}`}>{children}</div>;
}

interface StatCardProps {
  icon: string; label: string; value: string | number;
  sub?: string; color?: string; trend?: number;
}
export function StatCard({ icon, label, value, sub, color = '#0D9488', trend }: StatCardProps) {
  return (
    <Card>
      <CardBody>
        <div className="flex items-start justify-between mb-3">
          <div className="w-10 h-10 rounded-lg flex items-center justify-center text-xl"
            style={{ background: color + '18' }}>{icon}</div>
          {trend != null && (
            <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${trend >= 0 ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
              {trend >= 0 ? '↑' : '↓'} {Math.abs(trend)}%
            </span>
          )}
        </div>
        <div className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1">{label}</div>
        <div className="text-2xl font-extrabold text-gray-900 font-mono">{value}</div>
        {sub && <div className="text-xs text-gray-400 mt-1">{sub}</div>}
      </CardBody>
    </Card>
  );
}
