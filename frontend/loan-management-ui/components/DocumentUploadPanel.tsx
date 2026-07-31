'use client';
import { useEffect, useState, useCallback } from 'react';
import { publicApi } from '@/services/api';
import CameraCapture from './CameraCapture';

interface DocItem { id: number; documentType: string; fileName: string; fileSize: number; verificationStatus?: string; }

interface RequiredDoc {
  type: string;
  label: string;
  count: number; // how many of this type are required (e.g. 3 bank statements)
  camera?: boolean;
  accept?: string;
}

const OPTIONAL_DOC_TYPES: { type: string; label: string }[] = [
  { type: 'PASSPORT',              label: 'Passport' },
  { type: 'DRIVING_LICENSE',       label: 'Driving License' },
  { type: 'PROOF_OF_ADDRESS',      label: 'Proof of Address' },
  { type: 'PAYSLIP',               label: 'Payslip' },
  { type: 'EMPLOYMENT_LETTER',     label: 'Employment Letter' },
  { type: 'BUSINESS_REGISTRATION', label: 'Business Registration Certificate' },
  { type: 'COLLATERAL_DOCUMENT',   label: 'Collateral Document' },
  { type: 'OTHER',                 label: 'Other Document' },
];

function requiredDocsFor(maritalStatus?: string): RequiredDoc[] {
  const docs: RequiredDoc[] = [
    { type: 'NATIONAL_ID', label: 'National ID', count: 1, accept: 'image/*,application/pdf' },
  ];
  if ((maritalStatus || '').toLowerCase() === 'married') {
    docs.push({ type: 'MARRIAGE_CERTIFICATE', label: 'Marriage Certificate', count: 1, accept: 'image/*,application/pdf' });
  } else {
    docs.push({ type: 'SINGLE_CERTIFICATE', label: 'Single Status Certificate', count: 1, accept: 'image/*,application/pdf' });
  }
  docs.push({ type: 'BANK_STATEMENT', label: 'Bank Statements (last 3 months)', count: 1, accept: 'image/*,application/pdf' });
  docs.push({ type: 'SELFIE', label: 'Selfie (for identity verification)', count: 1, camera: true, accept: 'image/*' });
  return docs;
}

export default function DocumentUploadPanel({
  reference, phone, maritalStatus, primary = '#0D6B3E', onStatusChange,
}: { reference: string; phone: string; maritalStatus?: string; primary?: string; onStatusChange?: (complete: boolean) => void }) {
  const [uploaded, setUploaded] = useState<DocItem[]>([]);
  const [loading, setLoading]   = useState(true);
  const [uploadingType, setUploadingType] = useState<string | null>(null);
  const [error, setError]       = useState('');
  const [cameraFor, setCameraFor] = useState<string | null>(null);

  const required = requiredDocsFor(maritalStatus);

  const refresh = useCallback(() => {
    publicApi.listDocuments(reference, phone)
      .then((data) => setUploaded(data as DocItem[]))
      .catch(() => setError('Could not load your uploaded documents.'))
      .finally(() => setLoading(false));
  }, [reference, phone]);

  useEffect(() => { refresh(); }, [refresh]);

  const countFor = (type: string) => uploaded.filter(d => d.documentType === type).length;
  const filesFor = (type: string) => uploaded.filter(d => d.documentType === type);

  const doUpload = async (type: string, file: File | Blob) => {
    setError(''); setUploadingType(type);
    try {
      await publicApi.uploadDocument(reference, phone, type, file);
      refresh();
    } catch (err: any) {
      setError(err.message || 'Upload failed. Please try again.');
    } finally {
      setUploadingType(null);
    }
  };

  const [deletingId, setDeletingId] = useState<number | null>(null);

  const handleDelete = async (doc: DocItem) => {
    if (doc.verificationStatus === 'VERIFIED') return; // backend blocks this too; button is hidden for these anyway
    if (!confirm(`Remove "${doc.fileName}"? You can upload a replacement right after.`)) return;
    setError(''); setDeletingId(doc.id);
    try {
      await publicApi.deleteDocument(reference, phone, doc.id);
      refresh();
    } catch (err: any) {
      setError(err.message || 'Could not remove that file. Please try again.');
    } finally {
      setDeletingId(null);
    }
  };

  const handleFileInput = (type: string) => (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) doUpload(type, file);
    e.target.value = '';
  };

  const allComplete = required.every(d => countFor(d.type) >= d.count);

  useEffect(() => {
    if (!loading) onStatusChange?.(allComplete);
  }, [allComplete, loading]);

  if (loading) {
    return <div className="text-center py-8 text-sm text-gray-400">Loading document checklist…</div>;
  }

  return (
    <div className="bg-white rounded-xl border border-gray-200 p-6">
      <div className="flex items-center justify-between mb-1">
        <h3 className="font-bold text-gray-900">Required Documents</h3>
        {allComplete && (
          <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-green-50 text-green-700">All uploaded ✓</span>
        )}
      </div>
      <p className="text-xs text-gray-500 mb-5">Accepted formats: PDF, JPG, PNG (max 8MB each).</p>

      {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg px-3 py-2.5 mb-4">{error}</div>}

      <div className="space-y-3">
        {required.map(doc => {
          const count = countFor(doc.type);
          const complete = count >= doc.count;
          const busy = uploadingType === doc.type;
          return (
            <div key={doc.type} className="flex items-center justify-between gap-3 border border-gray-100 rounded-lg px-4 py-3">
              <div className="flex items-center gap-3 min-w-0">
                <div className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold shrink-0 ${complete ? 'text-white' : 'bg-gray-100 text-gray-400'}`}
                  style={complete ? { backgroundColor: primary } : {}}>
                  {complete ? '✓' : ''}
                </div>
                <div className="min-w-0">
                  <div className="text-sm font-semibold text-gray-800 truncate">{doc.label}</div>
                  {doc.count > 1 && (
                    <div className="text-xs text-gray-400">{count} of {doc.count} uploaded</div>
                  )}
                </div>
              </div>

              {doc.camera ? (
                <button
                  onClick={() => setCameraFor(doc.type)}
                  disabled={busy}
                  className="text-xs font-bold px-3 py-2 rounded-md border shrink-0 disabled:opacity-50"
                  style={{ borderColor: primary, color: primary }}>
                  {busy ? 'Uploading…' : complete ? 'Retake' : 'Open Camera'}
                </button>
              ) : (
                <label className="text-xs font-bold px-3 py-2 rounded-md border shrink-0 cursor-pointer text-center"
                  style={{ borderColor: primary, color: primary, opacity: busy ? 0.5 : 1 }}>
                  {busy ? 'Uploading…' : count >= doc.count ? 'Add More' : 'Upload'}
                  <input type="file" accept={doc.accept} className="hidden" disabled={busy} onChange={handleFileInput(doc.type)} />
                </label>
              )}
            </div>
          );
        })}
      </div>

      {/* Individual uploaded files, so a wrong upload can be removed and replaced rather than
          being stuck once a document type shows as "complete". */}
      {uploaded.length > 0 && (
        <div className="mt-4 space-y-1.5">
          {required.filter(d => countFor(d.type) > 0).map(doc => (
            <div key={doc.type}>
              {filesFor(doc.type).map(f => (
                <div key={f.id} className="flex items-center justify-between gap-3 text-xs text-gray-500 px-4 py-1.5">
                  <span className="truncate">📄 {f.fileName} <span className="text-gray-400">({doc.label})</span></span>
                  {f.verificationStatus === 'VERIFIED' ? (
                    <span className="text-green-600 font-semibold shrink-0">Verified</span>
                  ) : (
                    <button
                      onClick={() => handleDelete(f)}
                      disabled={deletingId === f.id}
                      className="text-red-500 hover:text-red-700 font-semibold shrink-0 disabled:opacity-50"
                    >
                      {deletingId === f.id ? 'Removing…' : '✕ Remove'}
                    </button>
                  )}
                </div>
              ))}
            </div>
          ))}
        </div>
      )}

      {cameraFor && (
        <CameraCapture
          primary={primary}
          onClose={() => setCameraFor(null)}
          onCapture={(blob) => { const type = cameraFor; setCameraFor(null); doUpload(type, blob); }}
        />
      )}

      <div className="mt-6 pt-5 border-t border-gray-100">
        <h4 className="text-sm font-bold text-gray-800 mb-1">Additional Documents</h4>
        <p className="text-xs text-gray-500 mb-3">
          Upload anything else your loan officer may need — payslip, employment letter, proof of address,
          business registration, collateral documents, etc.
        </p>
        <div className="space-y-2">
          {OPTIONAL_DOC_TYPES.map(doc => {
            const count = countFor(doc.type);
            const busy = uploadingType === doc.type;
            return (
              <div key={doc.type} className="border border-gray-100 rounded-lg px-4 py-2.5">
                <div className="flex items-center justify-between gap-3">
                  <div className="text-sm text-gray-700 flex items-center gap-2 min-w-0">
                    <span className="truncate">{doc.label}</span>
                    {count > 0 && <span className="text-xs text-gray-400 shrink-0">({count} uploaded)</span>}
                  </div>
                  <label className="text-xs font-bold px-3 py-1.5 rounded-md border shrink-0 cursor-pointer text-center"
                    style={{ borderColor: primary, color: primary, opacity: busy ? 0.5 : 1 }}>
                    {busy ? 'Uploading…' : count > 0 ? 'Add More' : 'Upload'}
                    <input type="file" accept="image/*,application/pdf" className="hidden" disabled={busy} onChange={handleFileInput(doc.type)} />
                  </label>
                </div>
                {filesFor(doc.type).map(f => (
                  <div key={f.id} className="flex items-center justify-between gap-3 text-xs text-gray-500 pt-1.5 mt-1.5 border-t border-gray-50">
                    <span className="truncate">📄 {f.fileName}</span>
                    {f.verificationStatus === 'VERIFIED' ? (
                      <span className="text-green-600 font-semibold shrink-0">Verified</span>
                    ) : (
                      <button
                        onClick={() => handleDelete(f)}
                        disabled={deletingId === f.id}
                        className="text-red-500 hover:text-red-700 font-semibold shrink-0 disabled:opacity-50"
                      >
                        {deletingId === f.id ? 'Removing…' : '✕ Remove'}
                      </button>
                    )}
                  </div>
                ))}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
