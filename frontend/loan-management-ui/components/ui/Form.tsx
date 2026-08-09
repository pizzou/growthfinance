'use client';
import React from 'react';

export function FormGroup({ label, required, hint, error, children }: {
  label: string; required?: boolean; hint?: string; error?: string; children: React.ReactNode;
}) {
  return (
    <div className="mb-4">
      <label className="block text-xs font-semibold text-gray-600 mb-1.5 uppercase tracking-wider">
        {label}{required && <span className="text-red-500 ml-1">*</span>}
      </label>
      {children}
      {hint  && !error && <p className="text-xs text-gray-400 mt-1">{hint}</p>}
      {error && <p className="text-xs text-red-500 mt-1">{error}</p>}
    </div>
  );
}

export function Input(props: React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input {...props}
      className={`w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm text-gray-900 bg-white
        placeholder:text-gray-400 focus:outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-500/20
        disabled:bg-gray-50 disabled:text-gray-400 transition-colors ${props.className || ''}`} />
  );
}

export function Select({ children, ...props }: React.SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select {...props}
      className={`w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm text-gray-900 bg-white
        focus:outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-500/20
        disabled:bg-gray-50 disabled:text-gray-400 transition-colors cursor-pointer ${props.className || ''}`}>
      {children}
    </select>
  );
}

export function Textarea(props: React.TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea {...props}
      className={`w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm text-gray-900 bg-white
        placeholder:text-gray-400 focus:outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-500/20
        resize-y min-h-[80px] transition-colors ${props.className || ''}`} />
  );
}

export function FormRow({ children }: { children: React.ReactNode }) {
  return <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4">{children}</div>;
}

export function Alert({ type = 'error', children }: { type?: 'error' | 'success' | 'warning' | 'info'; children: React.ReactNode }) {
  const styles = {
    error:   'bg-red-50 border-red-200 text-red-700',
    success: 'bg-green-50 border-green-200 text-green-700',
    warning: 'bg-yellow-50 border-yellow-200 text-yellow-700',
    info:    'bg-blue-50 border-blue-200 text-blue-700',
  };
  const icons = { error: '⚠️', success: '✅', warning: '⚠️', info: 'ℹ️' };
  return (
    <div className={`flex gap-2 items-start p-3 rounded-lg border text-sm mb-4 ${styles[type]}`}>
      <span>{icons[type]}</span><span>{children}</span>
    </div>
  );
}
