'use client';
import { useState, useCallback, useEffect } from 'react';

export type ToastType = 'success' | 'error' | 'warning' | 'info';
export interface Toast { id: string; type: ToastType; message: string; }

let _add: ((t: Omit<Toast, 'id'>) => void) | null = null;
export const toast = (type: ToastType, message: string) => { if (_add) _add({ type, message }); };

export function useToast() {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const add = useCallback((t: Omit<Toast, 'id'>) => {
    const id = Math.random().toString(36).slice(2);
    setToasts((p) => [...p, { ...t, id }]);
    setTimeout(() => setToasts((p) => p.filter((x) => x.id !== id)), 4000);
  }, []);

  useEffect(() => { _add = add; return () => { _add = null; }; }, [add]);

  const remove = (id: string) => setToasts((p) => p.filter((x) => x.id !== id));
  return { toasts, remove };
}