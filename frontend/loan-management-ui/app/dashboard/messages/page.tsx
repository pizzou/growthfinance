'use client';
import { useEffect, useState } from 'react';
import { contactMessageApi } from '@/services/api';
import { PageSpinner } from '@/components/ui/Skeleton';

interface ContactMsg {
  id: number; name: string; email?: string; phone?: string;
  subject?: string; message: string; read: boolean; createdAt: string;
}

export default function MessagesPage() {
  const [messages, setMessages] = useState<ContactMsg[]>([]);
  const [loading, setLoading]   = useState(true);
  const [expanded, setExpanded] = useState<number | null>(null);

  const load = () => contactMessageApi.list()
    .then((data) => setMessages(data as ContactMsg[]))
    .catch(console.error)
    .finally(() => setLoading(false));

  useEffect(() => { load(); }, []);

  const openMessage = async (m: ContactMsg) => {
    setExpanded(expanded === m.id ? null : m.id);
    if (!m.read) {
      try {
        await contactMessageApi.markRead(m.id);
        setMessages(prev => prev.map(x => x.id === m.id ? { ...x, read: true } : x));
      } catch { /* non-fatal */ }
    }
  };

  const handleDelete = async (id: number, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!confirm('Delete this message?')) return;
    try {
      await contactMessageApi.delete(id);
      setMessages(prev => prev.filter(x => x.id !== id));
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Could not delete message');
    }
  };

  if (loading) return <PageSpinner />;

  const unreadCount = messages.filter(m => !m.read).length;

  return (
    <div className="space-y-5 max-w-3xl">
      <div>
        <h1 className="text-xl font-bold text-gray-900">Messages</h1>
        <p className="text-sm text-gray-500">
          {messages.length} message{messages.length !== 1 ? 's' : ''} from your website's contact form
          {unreadCount > 0 && <span className="text-teal-600 font-semibold"> · {unreadCount} unread</span>}
        </p>
      </div>

      {messages.length === 0 ? (
        <div className="text-center py-16 bg-white rounded-2xl border border-gray-100">
          <p className="text-3xl mb-3">📬</p>
          <p className="text-gray-500 font-medium">No messages yet</p>
          <p className="text-gray-400 text-sm mt-1">Messages submitted through your public website's contact form will appear here.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {messages.map(m => (
            <div key={m.id}
              onClick={() => openMessage(m)}
              className={`bg-white rounded-xl border p-4 cursor-pointer transition ${
                !m.read ? 'border-teal-200 ring-1 ring-teal-100' : 'border-gray-100 hover:border-gray-200'
              }`}>
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    {!m.read && <span className="w-2 h-2 rounded-full bg-teal-500 shrink-0" />}
                    <p className="font-semibold text-sm text-gray-900">{m.subject || 'General Inquiry'}</p>
                    <span className="text-xs text-gray-400">from {m.name}</span>
                  </div>
                  <p className={`text-sm text-gray-600 mt-1 ${expanded === m.id ? '' : 'truncate'}`}>
                    {m.message}
                  </p>
                  {expanded === m.id && (
                    <div className="mt-3 pt-3 border-t border-gray-100 text-xs text-gray-500 space-y-1">
                      {m.email && <div>Email: <span className="text-gray-700 font-medium">{m.email}</span></div>}
                      {m.phone && <div>Phone: <span className="text-gray-700 font-medium">{m.phone}</span></div>}
                      <div>Received: <span className="text-gray-700 font-medium">{new Date(m.createdAt).toLocaleString()}</span></div>
                    </div>
                  )}
                </div>
                <div className="flex items-center gap-3 shrink-0">
                  <span className="text-xs text-gray-400">{new Date(m.createdAt).toLocaleDateString()}</span>
                  <button onClick={(e) => handleDelete(m.id, e)}
                    className="text-xs text-red-400 hover:text-red-600 font-medium">
                    Delete
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
