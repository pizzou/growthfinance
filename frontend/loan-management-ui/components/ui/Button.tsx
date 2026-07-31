'use client';
import React from 'react';

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost' | 'outline';
type Size    = 'xs' | 'sm' | 'md' | 'lg';

interface Props extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant; size?: Size; loading?: boolean; icon?: string;
}

const variants: Record<Variant, string> = {
  primary:   'bg-teal-600 hover:bg-teal-700 text-white shadow-sm',
  secondary: 'bg-gray-100 hover:bg-gray-200 text-gray-700 border border-gray-200',
  danger:    'bg-red-600 hover:bg-red-700 text-white shadow-sm',
  ghost:     'hover:bg-gray-100 text-gray-600',
  outline:   'border-2 border-teal-600 text-teal-600 hover:bg-teal-50',
};

const sizes: Record<Size, string> = {
  xs: 'px-2.5 py-1 text-xs',
  sm: 'px-3 py-1.5 text-sm',
  md: 'px-4 py-2 text-sm',
  lg: 'px-6 py-3 text-base',
};

export function Button({ variant = 'primary', size = 'md', loading, icon, children, disabled, className = '', ...rest }: Props) {
  return (
    <button
      disabled={disabled || loading}
      className={`inline-flex items-center gap-2 rounded-lg font-semibold transition-all duration-150 focus:outline-none focus:ring-2 focus:ring-teal-500 focus:ring-offset-1 disabled:opacity-50 disabled:cursor-not-allowed ${variants[variant]} ${sizes[size]} ${className}`}
      {...rest}
    >
      {loading ? <span className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin" /> : icon && <span>{icon}</span>}
      {children}
    </button>
  );
}
