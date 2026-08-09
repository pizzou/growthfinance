'use client';
import React from 'react';

export function Table({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return (
    <div className={`overflow-x-auto rounded-xl border border-gray-200 ${className}`}>
      <table className="w-full text-sm border-collapse">{children}</table>
    </div>
  );
}

export function Thead({ children }: { children: React.ReactNode }) {
  return <thead className="bg-gray-50 border-b border-gray-200">{children}</thead>;
}

export function Th({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return (
    <th className={`px-4 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider whitespace-nowrap ${className}`}>
      {children}
    </th>
  );
}

export function Tbody({ children }: { children: React.ReactNode }) {
  return <tbody className="divide-y divide-gray-100 bg-white">{children}</tbody>;
}

export function Tr({ children, onClick, className = '' }: { children: React.ReactNode; onClick?: () => void; className?: string }) {
  return (
    <tr onClick={onClick}
      className={`${onClick ? 'cursor-pointer hover:bg-gray-50' : ''} transition-colors ${className}`}>
      {children}
    </tr>
  );
}

export function Td({ children, className = '', onClick }: { children: React.ReactNode; className?: string; onClick?: (e: React.MouseEvent) => void }) {
  return <td className={`px-4 py-3 text-gray-700 ${className}`} onClick={onClick}>{children}</td>;
}

export function EmptyRow({ cols, message = 'No data found' }: { cols: number; message?: string }) {
  return (
    <Tr>
      <Td className={`col-span-${cols} text-center py-12 text-gray-400`}>
        <div className="text-4xl mb-2 opacity-40">📋</div>
        <div className="font-medium">{message}</div>
      </Td>
    </Tr>
  );
}