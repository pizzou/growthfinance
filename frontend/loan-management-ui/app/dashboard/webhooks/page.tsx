'use client';
import { useEffect, useState } from 'react';
import { webhookApi } from '@/services/api';
import { WebhookEndpoint } from '@/types';
import { Card, CardHeader, CardBody } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/Table';
import { Modal } from '@/components/ui/Modal';
import { FormGroup, Input, Alert } from '@/components/ui/Form';

const ALL_EVENTS = ['LOAN_CREATED','LOAN_APPROVED','LOAN_REJECTED','LOAN_DISBURSED','PAYMENT_MADE','LOAN_OVERDUE','LOAN_DEFAULTED'];

export default function WebhooksPage() {
  const [webhooks, setWebhooks] = useState<WebhookEndpoint[]>([]);
  const [loading, setLoading]   = useState(true);
  const [addOpen, setAddOpen]   = useState(false);
  const [form, setForm]         = useState({ url: '', description: '', subscribedEvents: ALL_EVENTS });
  const [saving, setSaving]     = useState(false);
  const [msg, setMsg]           = useState('');

  const load = () => {
    setLoading(true);
    webhookApi.list().then((r: any) => setWebhooks(Array.isArray(r) ? r : [])).catch(console.error).finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault(); setSaving(true); setMsg('');
    try { await webhookApi.create(form); setAddOpen(false); setForm({ url: '', description: '', subscribedEvents: ALL_EVENTS }); load(); }
    catch (err: any) { setMsg(err.message); }
    setSaving(false);
  };

  const handleDelete = async (id: number) => {
    if (!confirm('Delete this webhook endpoint?')) return;
    await webhookApi.remove(id); load();
  };

  const toggleEvent = (ev: string) =>
    setForm(f => ({ ...f, subscribedEvents: f.subscribedEvents.includes(ev)
      ? f.subscribedEvents.filter(e => e !== ev)
      : [...f.subscribedEvents, ev] }));

  return (
    <div>
      <div className="flex items-start justify-between mb-6">
        <div>
          <h1 className="text-2xl font-extrabold text-gray-900">Webhook Endpoints</h1>
          <p className="text-sm text-gray-500 mt-0.5">Real-time event delivery to your systems via HMAC-SHA256 signed POST requests</p>
        </div>
        <Button icon="+" onClick={() => setAddOpen(true)}>Add Endpoint</Button>
      </div>

      {/* Info card */}
      <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 mb-5 text-sm text-blue-700">
        <strong>How it works:</strong> When events occur (loan approved, payment made, etc.), we send a POST request to your endpoint URL with a JSON payload. Each request is signed with <code className="bg-blue-100 px-1 py-0.5 rounded">X-Webhook-Signature: sha256=…</code> for security.
      </div>

      <Card>
        {loading ? (
          <div className="flex items-center justify-center py-12"><div className="w-7 h-7 border-2 border-teal-500 border-t-transparent rounded-full animate-spin" /></div>
        ) : (
          <Table>
            <Thead><tr><Th>URL</Th><Th>Description</Th><Th>Events</Th><Th>Status</Th><Th>Last Delivery</Th><Th>Failures</Th><Th>{null}</Th></tr></Thead>
            <Tbody>
              {webhooks.length === 0 ? (
                <Tr><Td className="text-center py-12 text-gray-400">
                  <div className="text-4xl mb-2">🔗</div>
                  <div className="font-medium">No webhooks configured</div>
                  <div className="text-xs mt-1">Add an endpoint to receive real-time events</div>
                </Td></Tr>
              ) : webhooks.map((wh: WebhookEndpoint) => (
                <Tr key={wh.id}>
                  <Td>
                    <code className="text-xs bg-gray-100 px-2 py-1 rounded font-mono break-all">{wh.url}</code>
                  </Td>
                  <Td className="text-sm text-gray-600">{wh.description ?? '—'}</Td>
                  <Td>
                    <div className="flex flex-wrap gap-1">
                      {(wh.subscribedEvents ?? []).slice(0, 3).map(ev => (
                        <span key={ev} className="text-[10px] bg-gray-100 text-gray-600 px-1.5 py-0.5 rounded font-mono">{ev.replace(/_/g,' ')}</span>
                      ))}
                      {(wh.subscribedEvents ?? []).length > 3 && (
                        <span className="text-[10px] text-gray-400">+{(wh.subscribedEvents ?? []).length - 3}</span>
                      )}
                    </div>
                  </Td>
                  <Td>
                    <span className={`inline-flex items-center gap-1 text-xs font-semibold px-2 py-0.5 rounded-full ${wh.active ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'}`}>
                      {wh.active ? '● Active' : '● Disabled'}
                    </span>
                  </Td>
                  <Td className="text-xs text-gray-400">
                    {wh.lastDeliveryAt ? new Date(wh.lastDeliveryAt).toLocaleString() : '—'}
                    {wh.lastDeliveryStatus && (
                      <div className={`text-[10px] mt-0.5 ${wh.lastDeliveryStatus === 'SUCCESS' ? 'text-teal-500' : 'text-red-400'}`}>
                        {wh.lastDeliveryStatus}
                      </div>
                    )}
                  </Td>
                  <Td className={`font-semibold ${(wh.failureCount ?? 0) > 0 ? 'text-red-500' : 'text-gray-400'}`}>
                    {wh.failureCount ?? 0}
                  </Td>
                  <Td>
                    <Button variant="ghost" size="xs" onClick={() => handleDelete(wh.id!)}>🗑</Button>
                  </Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        )}
      </Card>

      {/* Add Modal */}
      <Modal open={addOpen} onClose={() => setAddOpen(false)} title="Add Webhook Endpoint"
        footer={<>
          <Button variant="secondary" onClick={() => setAddOpen(false)}>Cancel</Button>
          <Button loading={saving} onClick={handleAdd as any}>Save Endpoint</Button>
        </>}>
        <form onSubmit={handleAdd}>
          {msg && <Alert type="error">{msg}</Alert>}
          <FormGroup label="Endpoint URL" required>
            <Input type="url" required placeholder="https://yourapp.com/webhooks/loansaas" value={form.url} onChange={e => setForm(f => ({...f, url: e.target.value}))} />
          </FormGroup>
          <FormGroup label="Description">
            <Input placeholder="e.g. CRM integration, Core banking sync" value={form.description} onChange={e => setForm(f => ({...f, description: e.target.value}))} />
          </FormGroup>
          <FormGroup label="Subscribed Events">
            <div className="grid grid-cols-2 gap-2 mt-1">
              {ALL_EVENTS.map(ev => (
                <label key={ev} className="flex items-center gap-2 cursor-pointer text-sm">
                  <input type="checkbox" checked={form.subscribedEvents.includes(ev)}
                    onChange={() => toggleEvent(ev)}
                    className="w-4 h-4 text-teal-600 rounded border-gray-300 focus:ring-teal-500" />
                  <span className="text-gray-700 font-mono text-xs">{ev.replace(/_/g,' ')}</span>
                </label>
              ))}
            </div>
          </FormGroup>
          <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-3 text-xs text-yellow-700 mt-2">
            🔑 A signing secret will be generated automatically. Use it to verify webhook authenticity in your system.
          </div>
        </form>
      </Modal>
    </div>
  );
}