'use client';
import { useEffect, useState } from 'react';
import { currencyApi } from '@/services/api';
import { Card, CardHeader, CardBody, StatCard } from '@/components/ui/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/Table';
import { FormGroup, Input, Select, Alert } from '@/components/ui/Form';
import { Button } from '@/components/ui/Button';
import { SUPPORTED_CURRENCIES } from '@/lib/utils';

export default function CurrenciesPage() {
  const [rates, setRates]     = useState<any[]>([]);
  const [base, setBase]       = useState('USD');
  const [loading, setLoading] = useState(true);
  const [from, setFrom]       = useState('USD');
  const [to, setTo]           = useState('KES');
  const [amount, setAmount]   = useState('1000');
  const [converted, setConverted] = useState<number | null>(null);
  const [converting, setConverting] = useState(false);
  const [error, setError]     = useState('');
  const [refreshing, setRefreshing] = useState(false);
  const [refreshMsg, setRefreshMsg] = useState('');

  const load = () => {
    setLoading(true);
    currencyApi.rates(base)
      .then((data: any) => setRates(data)).catch((e: any) => setError(e.message)).finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, [base]);

  const handleRefresh = async () => {
    setRefreshing(true);
    setRefreshMsg('');
    try {
      const r: any = await currencyApi.refresh();
      setRefreshMsg(r?.success ? `Updated ${r.updatedCount} currencies from ${r.source}.` : (r?.source || 'Refresh did not update any rates.'));
      load();
    } catch (e: any) {
      setRefreshMsg(e.message || 'Refresh failed.');
    } finally {
      setRefreshing(false);
    }
  };

  const handleConvert = async () => {
    setConverting(true);
    try {
      const r: any = await currencyApi.convert(from, to, Number(amount));
      setConverted(r?.converted ?? r);
    } catch (e: any) { setError(e.message); }
    setConverting(false);
  };

  return (
    <div>
      <div className="mb-6 flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl font-extrabold text-gray-900">FX Rates & Currency</h1>
          <p className="text-sm text-gray-500 mt-0.5">Multi-currency support across 25 currencies · Live rates via exchangerate-api.com, refreshed daily</p>
        </div>
        <div className="text-right">
          <Button onClick={handleRefresh} loading={refreshing} variant="secondary">
            {refreshing ? 'Refreshing…' : '↻ Refresh Now'}
          </Button>
          {refreshMsg && <p className="text-xs text-gray-500 mt-1.5">{refreshMsg}</p>}
        </div>
      </div>

      {error && <Alert type="error">{error}</Alert>}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 mb-6">
        {/* Converter */}
        <Card className="lg:col-span-1">
          <CardHeader title="💱 Currency Converter" />
          <CardBody>
            <FormGroup label="Amount">
              <Input type="number" value={amount} onChange={e => setAmount(e.target.value)} />
            </FormGroup>
            <div className="grid grid-cols-2 gap-3">
              <FormGroup label="From">
                <Select value={from} onChange={e => setFrom(e.target.value)}>
                  {SUPPORTED_CURRENCIES.map(c => <option key={c}>{c}</option>)}
                </Select>
              </FormGroup>
              <FormGroup label="To">
                <Select value={to} onChange={e => setTo(e.target.value)}>
                  {SUPPORTED_CURRENCIES.map(c => <option key={c}>{c}</option>)}
                </Select>
              </FormGroup>
            </div>
            <Button className="w-full justify-center mt-1" loading={converting} onClick={handleConvert}>Convert</Button>
            {converted != null && (
              <div className="mt-4 bg-teal-50 border border-teal-200 rounded-xl p-4 text-center">
                <div className="text-xs text-teal-600 mb-1">{amount} {from} =</div>
                <div className="text-3xl font-extrabold text-teal-700">{converted.toLocaleString()} {to}</div>
              </div>
            )}
          </CardBody>
        </Card>

        {/* Rates table */}
        <Card className="lg:col-span-2">
          <CardHeader title={`Exchange Rates — Base: ${base}`}
            action={
              <Select value={base} onChange={e => setBase(e.target.value)} className="w-24 text-sm">
                {SUPPORTED_CURRENCIES.map(c => <option key={c}>{c}</option>)}
              </Select>
            } />
          {loading ? (
            <div className="flex items-center justify-center py-12"><div className="w-6 h-6 border-2 border-teal-500 border-t-transparent rounded-full animate-spin" /></div>
          ) : (
            <Table>
              <Thead><tr><Th>Currency</Th><Th>Rate</Th><Th>Last Updated</Th></tr></Thead>
              <Tbody>
                {(Array.isArray(rates) ? rates : []).length === 0
                  ? <Tr><Td className="text-center py-8 text-gray-400">
                      No rates cached yet — click "Refresh Now" above to pull live rates.
                    </Td></Tr>
                  : (Array.isArray(rates) ? rates : []).map((r: any) => (
                    <Tr key={r.targetCurrency}>
                      <Td>
                        <div className="flex items-center gap-2">
                          <span className="w-10 h-6 bg-gray-100 rounded text-xs font-bold flex items-center justify-center text-gray-700">{r.targetCurrency}</span>
                          <span className="text-sm text-gray-600">{r.targetCurrency}</span>
                        </div>
                      </Td>
                      <Td className="font-mono font-semibold text-gray-900">{Number(r.rate).toFixed(4)}</Td>
                      <Td className="text-xs text-gray-400">{r.fetchedAt ? new Date(r.fetchedAt).toLocaleString() : '—'}</Td>
                    </Tr>
                  ))
                }
              </Tbody>
            </Table>
          )}
        </Card>
      </div>

      {/* Supported currencies grid */}
      <Card>
        <CardHeader title="Supported Currencies" subtitle="All currencies available for loan issuance" />
        <CardBody>
          <div className="grid grid-cols-4 sm:grid-cols-6 lg:grid-cols-9 gap-2">
            {SUPPORTED_CURRENCIES.map(c => (
              <div key={c} className="bg-gray-50 border border-gray-200 rounded-lg p-3 text-center hover:border-teal-400 transition-colors cursor-default">
                <div className="text-sm font-bold text-gray-800">{c}</div>
              </div>
            ))}
          </div>
        </CardBody>
      </Card>
    </div>
  );
}