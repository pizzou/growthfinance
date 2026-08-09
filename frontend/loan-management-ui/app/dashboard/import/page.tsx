'use client';
import { useEffect, useState } from 'react';
import { importApi } from '../../../services/api';
import { toast } from '../../../hooks/useToast';
import { PageSpinner } from '../../../components/ui/Skeleton';
import { Pill } from '../../../components/ui/Badge';

interface RowResult {
  rowNumber: number;
  success: boolean;
  borrowerAction?: string;
  borrowerName?: string;
  loanReferenceNumber?: string;
  error?: string;
}

interface Batch {
  id: number;
  fileName: string;
  totalRows: number;
  successCount: number;
  failureCount: number;
  status: string;
  createdAt: string;
  importedBy?: { name?: string };
}

export default function ImportLegacyLoansPage() {
  const [file, setFile]           = useState<File | null>(null);
  const [preview, setPreview]     = useState<RowResult[] | null>(null);
  const [previewMsg, setPreviewMsg] = useState('');
  const [busy, setBusy]           = useState(false);
  const [batches, setBatches]     = useState<Batch[]>([]);
  const [loadingBatches, setLoadingBatches] = useState(true);

  const loadBatches = () =>
    importApi.batches().then((b: any) => setBatches(Array.isArray(b) ? b : [])).finally(() => setLoadingBatches(false));

  useEffect(() => { loadBatches(); }, []);

  const handleDownloadTemplate = async () => {
    const res: any = await importApi.template();
    const url = window.URL.createObjectURL(new Blob([res.data]));
    const a = document.createElement('a');
    a.href = url; a.download = 'legacy-loan-import-template.csv'; a.click();
    window.URL.revokeObjectURL(url);
  };

  const handleFileChange = (f: File | null) => {
    setFile(f); setPreview(null); setPreviewMsg('');
  };

  const handlePreview = async () => {
    if (!file) return;
    setBusy(true);
    try {
      const result: any = await importApi.preview(file);
      const rows: RowResult[] = Array.isArray(result) ? result : [];
      setPreview(rows);
      const ok = rows.filter(r => r.success).length;
      setPreviewMsg(`${ok}/${rows.length} rows would import successfully. Nothing has been saved yet — review below, then Commit.`);
    } catch (err: any) { toast('error', err.message); }
    setBusy(false);
  };

  const handleCommit = async () => {
    if (!file) return;
    if (!confirm('This will actually create borrowers and loans in the system from this file. Continue?')) return;
    setBusy(true);
    try {
      const batch: any = await importApi.commit(file);
      toast('success', `Imported ${batch.successCount}/${batch.totalRows} rows.`);
      setFile(null); setPreview(null); setPreviewMsg('');
      loadBatches();
    } catch (err: any) { toast('error', err.message); }
    setBusy(false);
  };

  return (
    <div className="max-w-5xl mx-auto p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Import Legacy Loans</h1>
        <p className="text-gray-500 text-sm mt-1">
          Bring in loans your clients were previously tracking manually (e.g. in Excel) — each row becomes
          a borrower (matched by National ID if they already exist) and a loan record.
        </p>
      </div>

      <div className="bg-white border border-gray-200 rounded-xl p-6 space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="font-semibold text-gray-900">1. Get the template</h2>
          <button onClick={handleDownloadTemplate}
            className="text-sm font-semibold text-blue-600 hover:underline">⬇️ Download CSV template</button>
        </div>
        <p className="text-xs text-gray-400">
          Fill it in with your existing records (or export your Excel sheet as CSV/XLSX and rename the columns
          to match). National ID, name, phone, gender, amount, interest rate, duration, start date, and status
          are required — everything else is optional.
        </p>

        <h2 className="font-semibold text-gray-900 pt-2">2. Upload your file</h2>
        <input type="file" accept=".csv,.xlsx,.xls"
          onChange={e => handleFileChange(e.target.files?.[0] ?? null)}
          className="block w-full text-sm text-gray-600 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:bg-blue-50 file:text-blue-700 file:font-semibold hover:file:bg-blue-100" />

        {file && (
          <div className="flex gap-2">
            <button onClick={handlePreview} disabled={busy}
              className="bg-gray-100 hover:bg-gray-200 text-gray-800 px-4 py-2 rounded-lg text-sm font-medium disabled:opacity-60">
              {busy ? 'Checking…' : '🔍 Preview (no data saved)'}
            </button>
            {preview && preview.some(r => r.success) && (
              <button onClick={handleCommit} disabled={busy}
                className="bg-green-600 hover:bg-green-700 text-white px-4 py-2 rounded-lg text-sm font-medium disabled:opacity-60">
                {busy ? 'Importing…' : `✅ Commit Import (${preview.filter(r => r.success).length} rows)`}
              </button>
            )}
          </div>
        )}

        {previewMsg && <p className="text-sm text-gray-600">{previewMsg}</p>}

        {preview && (
          <div className="border border-gray-200 rounded-lg overflow-hidden max-h-96 overflow-y-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 sticky top-0">
                <tr className="text-left text-gray-500">
                  <th className="p-2">Row</th>
                  <th className="p-2">Status</th>
                  <th className="p-2">Borrower</th>
                  <th className="p-2">Loan Ref</th>
                  <th className="p-2">Detail</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {preview.map(r => (
                  <tr key={r.rowNumber} className={r.success ? '' : 'bg-red-50'}>
                    <td className="p-2 text-gray-500">{r.rowNumber}</td>
                    <td className="p-2">
                      {r.success
                        ? <Pill label="Would import" color="green" />
                        : <Pill label="Would fail" color="red" />}
                    </td>
                    <td className="p-2">{r.borrowerName ?? '—'}</td>
                    <td className="p-2 font-mono text-xs">{r.loanReferenceNumber ?? '—'}</td>
                    <td className="p-2 text-xs text-gray-600">
                      {r.success
                        ? (r.borrowerAction === 'CREATED_NEW_BORROWER' ? 'New borrower created' : 'Matched existing borrower')
                        : r.error}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="bg-white border border-gray-200 rounded-xl p-6">
        <h2 className="font-semibold text-gray-900 mb-3">Import history</h2>
        {loadingBatches ? <PageSpinner /> : batches.length === 0 ? (
          <p className="text-sm text-gray-400">No imports yet.</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500 border-b border-gray-200">
                <th className="p-2">File</th>
                <th className="p-2">Rows</th>
                <th className="p-2">Status</th>
                <th className="p-2">Imported by</th>
                <th className="p-2">Date</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {batches.map(b => (
                <tr key={b.id}>
                  <td className="p-2">{b.fileName}</td>
                  <td className="p-2">{b.successCount}/{b.totalRows} succeeded{b.failureCount > 0 ? `, ${b.failureCount} failed` : ''}</td>
                  <td className="p-2">
                    <Pill label={b.status}
                      color={b.status === 'COMPLETED' ? 'green' : b.status === 'PARTIAL' ? 'yellow' : 'red'} />
                  </td>
                  <td className="p-2">{b.importedBy?.name ?? '—'}</td>
                  <td className="p-2 text-gray-500">{new Date(b.createdAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
