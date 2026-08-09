'use client';
import { useToast, Toast } from '../../hooks/useToast';

const COLORS: Record<Toast['type'], string> = {
  success: 'border-l-4 border-green-500 bg-white',
  error:   'border-l-4 border-red-500   bg-white',
  warning: 'border-l-4 border-yellow-400 bg-white',
  info:    'border-l-4 border-blue-500  bg-white',
};

export function ToastContainer() {
  const { toasts, remove } = useToast();
  return (
    <div className="fixed bottom-6 right-6 z-50 space-y-3 max-w-sm w-full pointer-events-none">
      {toasts.map((t) => (
        <div key={t.id} className={`pointer-events-auto flex items-start gap-3 p-4 rounded-xl shadow-lg ${COLORS[t.type]}`}>
          <p className="text-sm text-gray-800 flex-1">{t.message}</p>
          <button onClick={() => remove(t.id)} className="text-gray-400 hover:text-gray-600 text-xl leading-none">&times;</button>
        </div>
      ))}
    </div>
  );
}