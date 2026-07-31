'use client';
import { useEffect, useState, useCallback } from 'react';
import {
  getInternalDocuments, uploadInternalDocument, deleteInternalDocument, downloadInternalDocument,
  formatFileSize, INTERNAL_DOC_CATEGORY_LABELS, InternalDocumentSummary,
} from '../../../services/internalDocumentService';
import { useAuth } from '@/hooks/useAuth';
import { toast } from '../../../hooks/useToast';

const CATEGORIES = Object.keys(INTERNAL_DOC_CATEGORY_LABELS);

export default function InternalDocumentsPage() {
  const { user, isAdmin } = useAuth();
  const canDelete = isAdmin || user?.role === 'MANAGER';

  const [docs, setDocs] = useState<InternalDocumentSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('');
  const [uploading, setUploading] = useState(false);
  const [showUpload, setShowUpload] = useState(false);

  const [title, setTitle] = useState('');
  const [category, setCategory] = useState('OTHER');
  const [description, setDescription] = useState('');
  const [file, setFile] = useState<File | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    getInternalDocuments(filter || undefined)
      .then(setDocs)
      .catch(() => toast('error', 'Could not load documents'))
      .finally(() => setLoading(false));
  }, [filter]);

  useEffect(() => { load(); }, [load]);

  const handleUpload = async () => {
    if (!file) { toast('error', 'Choose a file first'); return; }
    if (file.size > 20 * 1024 * 1024) { toast('error', 'File must be under 20MB'); return; }
    setUploading(true);
    try {
      await uploadInternalDocument(file, title || file.name, category, description || undefined);
      toast('success', 'Document uploaded');
      setShowUpload(false); setTitle(''); setCategory('OTHER'); setDescription(''); setFile(null);
      load();
    } catch (err: unknown) {
      toast('error', err instanceof Error ? err.message : 'Upload failed');
    } finally {
      setUploading(false);
    }
  };

  const handleDelete = async (id: number, docTitle: string) => {
    if (!confirm(`Delete "${docTitle}"? This cannot be undone.`)) return;
    try { await deleteInternalDocument(id); toast('success', 'Document deleted'); load(); }
    catch (err: unknown) { toast('error', err instanceof Error ? err.message : 'Delete failed'); }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-xl font-bold text-gray-900">Internal Documents</h1>
          <p className="text-sm text-gray-500">Policies, contracts, memos, and templates — separate from borrower KYC files</p>
        </div>
        <button onClick={() => setShowUpload(true)}
          className="text-sm font-semibold px-4 py-2.5 rounded-lg bg-teal-600 text-white hover:bg-teal-700 transition">
          ⬆ Upload Document
        </button>
      </div>

      <div className="flex gap-2 flex-wrap">
        <button onClick={() => setFilter('')}
          className={`text-xs font-semibold px-3 py-1.5 rounded-full border transition ${
            filter === '' ? 'bg-teal-600 text-white border-teal-600' : 'bg-white text-gray-600 border-gray-200 hover:bg-gray-50'}`}>
          All
        </button>
        {CATEGORIES.map(c => (
          <button key={c} onClick={() => setFilter(c)}
            className={`text-xs font-semibold px-3 py-1.5 rounded-full border transition ${
              filter === c ? 'bg-teal-600 text-white border-teal-600' : 'bg-white text-gray-600 border-gray-200 hover:bg-gray-50'}`}>
            {INTERNAL_DOC_CATEGORY_LABELS[c]}
          </button>
        ))}
      </div>

      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
        {loading ? (
          <div className="p-10 text-center text-gray-400 text-sm">Loading…</div>
        ) : docs.length === 0 ? (
          <div className="p-10 text-center">
            <p className="text-3xl mb-2">🗂️</p>
            <p className="text-gray-500 text-sm">No documents {filter ? `in ${INTERNAL_DOC_CATEGORY_LABELS[filter]}` : 'yet'}.</p>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-500 text-xs uppercase tracking-wide">
              <tr>
                <th className="text-left px-5 py-3">Title</th>
                <th className="text-left px-5 py-3">Category</th>
                <th className="text-left px-5 py-3">Uploaded by</th>
                <th className="text-left px-5 py-3">Size</th>
                <th className="text-left px-5 py-3">Date</th>
                <th className="px-5 py-3"></th>
              </tr>
            </thead>
            <tbody>
              {docs.map(d => (
                <tr key={d.id} className="border-t border-gray-100 hover:bg-gray-50">
                  <td className="px-5 py-3">
                    <p className="font-medium text-gray-800">{d.title}</p>
                    {d.description && <p className="text-xs text-gray-400 mt-0.5">{d.description}</p>}
                  </td>
                  <td className="px-5 py-3">
                    <span className="text-xs font-semibold px-2 py-1 rounded-full bg-gray-100 text-gray-600">
                      {INTERNAL_DOC_CATEGORY_LABELS[d.category] ?? d.category}
                    </span>
                  </td>
                  <td className="px-5 py-3 text-gray-600">{d.uploadedByName ?? '—'}</td>
                  <td className="px-5 py-3 text-gray-500">{formatFileSize(d.fileSize)}</td>
                  <td className="px-5 py-3 text-gray-500">{new Date(d.createdAt).toLocaleDateString()}</td>
                  <td className="px-5 py-3 text-right whitespace-nowrap">
                    <button onClick={() => downloadInternalDocument(d.id, d.fileName)}
                      className="text-teal-600 hover:text-teal-700 font-semibold text-xs mr-3">⬇ Download</button>
                    {canDelete && (
                      <button onClick={() => handleDelete(d.id, d.title)}
                        className="text-red-500 hover:text-red-600 font-semibold text-xs">Delete</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {showUpload && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4"
          onClick={() => !uploading && setShowUpload(false)}>
          <div className="bg-white rounded-xl p-6 w-full max-w-md" onClick={e => e.stopPropagation()}>
            <h2 className="font-bold text-gray-900 mb-4">Upload Document</h2>
            <div className="space-y-3">
              <div>
                <label className="block text-xs font-semibold text-gray-600 mb-1">Title</label>
                <input value={title} onChange={e => setTitle(e.target.value)} placeholder="e.g. Credit Policy 2026"
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm" />
              </div>
              <div>
                <label className="block text-xs font-semibold text-gray-600 mb-1">Category</label>
                <select value={category} onChange={e => setCategory(e.target.value)}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm">
                  {CATEGORIES.map(c => <option key={c} value={c}>{INTERNAL_DOC_CATEGORY_LABELS[c]}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-xs font-semibold text-gray-600 mb-1">Description (optional)</label>
                <textarea value={description} onChange={e => setDescription(e.target.value)} rows={2}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm" />
              </div>
              <div>
                <label className="block text-xs font-semibold text-gray-600 mb-1">File (PDF, Word, Excel, image — up to 20MB)</label>
                <input type="file" onChange={e => setFile(e.target.files?.[0] ?? null)}
                  className="w-full text-sm" />
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-5">
              <button onClick={() => setShowUpload(false)} disabled={uploading}
                className="px-4 py-2 text-sm font-semibold text-gray-600 hover:bg-gray-50 rounded-lg">Cancel</button>
              <button onClick={handleUpload} disabled={uploading || !file}
                className="px-4 py-2 text-sm font-semibold text-white bg-teal-600 hover:bg-teal-700 rounded-lg disabled:opacity-50">
                {uploading ? 'Uploading…' : 'Upload'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
