'use client';
import { useEffect, useState, useCallback } from 'react';
import {
  getFilesByBorrower, uploadFile, deleteFile,
  previewFile, downloadFile, verifyFile, formatFileSize, fileIcon,
  DOCUMENT_TYPE_LABELS, VERIFICATION_STATUS_META,
} from '../services/fileService';
import { BorrowerFile } from '../types/index';
import { toast } from '../hooks/useToast';

export default function DocumentsPanel({ borrowerId }: { borrowerId: number }) {
  const [files, setFiles] = useState<BorrowerFile[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [verifyingId, setVerifyingId] = useState<number | null>(null);
  const [commentDraft, setCommentDraft] = useState('');
  const [pendingAction, setPendingAction] = useState<'VERIFIED' | 'REJECTED' | 'REPLACEMENT_REQUESTED' | null>(null);

  const getMsg = (err: unknown) => err instanceof Error ? err.message : 'Something went wrong';

  const load = useCallback(async () => {
    const f = await getFilesByBorrower(borrowerId).catch(() => [] as BorrowerFile[]);
    setFiles(f);
  }, [borrowerId]);

  useEffect(() => { load().catch(console.error).finally(() => setLoading(false)); }, [load]);

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.size > 10 * 1024 * 1024) { toast('error', 'File must be under 10MB'); return; }
    setUploading(true);
    try {
      await uploadFile(borrowerId, file);
      toast('success', file.name + ' uploaded');
      await load();
    } catch (err: unknown) {
      toast('error', getMsg(err));
    } finally {
      setUploading(false);
      e.target.value = '';
    }
  };

  const handleDelete = async (fileId: number, fileName: string) => {
    if (!confirm('Delete ' + fileName + '?')) return;
    try { await deleteFile(fileId); toast('success', 'File deleted'); await load(); }
    catch (err: unknown) { toast('error', getMsg(err)); }
  };

  const handleDownload = async (fileId: number, fileName: string) => {
    try { await downloadFile(fileId, fileName); }
    catch (err: unknown) { toast('error', getMsg(err)); }
  };

  const handlePreview = async (fileId: number) => {
    try { await previewFile(fileId); }
    catch (err: unknown) { toast('error', getMsg(err)); }
  };

  const startVerifyAction = (fileId: number, action: 'VERIFIED' | 'REJECTED' | 'REPLACEMENT_REQUESTED') => {
    if (action === 'VERIFIED') { submitVerify(fileId, action, ''); return; }
    setVerifyingId(fileId);
    setPendingAction(action);
    setCommentDraft('');
  };

  const submitVerify = async (fileId: number, action: 'VERIFIED' | 'REJECTED' | 'REPLACEMENT_REQUESTED', comment: string) => {
    try {
      await verifyFile(fileId, action, comment || undefined);
      toast('success', 'Document ' + action.toLowerCase().replace('_', ' '));
      setVerifyingId(null);
      setPendingAction(null);
      await load();
    } catch (err: unknown) {
      toast('error', getMsg(err));
    }
  };

  if (loading) return <div className="text-center py-10 text-gray-400 text-sm">Loading documents…</div>;

  return (
    <div className="bg-white rounded-xl border border-gray-200 overflow-hidden shadow-sm mt-4">
      <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 bg-gray-50/50">
        <div>
          <h2 className="font-semibold text-gray-800 text-sm">Documents and KYC Files</h2>
          <p className="text-xs text-gray-400 mt-0.5">
            {files.length} {files.length !== 1 ? 'files' : 'file'} uploaded
          </p>
        </div>
        <label
          className={
            'cursor-pointer bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 ' +
            'rounded-lg text-sm font-medium transition shadow-sm ' +
            (uploading ? 'opacity-60 pointer-events-none' : '')
          }
        >
          {uploading ? 'Uploading...' : '+ Upload File'}
          <input
            type="file"
            className="hidden"
            onChange={handleUpload}
            disabled={uploading}
            accept=".pdf,.jpg,.jpeg,.png,.doc,.docx,.xls,.xlsx"
          />
        </label>
      </div>

      {files.length === 0 ? (
        <div className="text-center py-12">
          <p className="text-3xl mb-2">📁</p>
          <p className="text-gray-500 text-sm font-medium">No documents uploaded yet</p>
          <p className="text-gray-400 text-xs mt-1">
            Upload ID documents, KYC files, income proof, etc.
          </p>
        </div>
      ) : (
        <div className="divide-y divide-gray-100">
          {files.map(f => {
            // 🔑 THE FIX: Fall back to safe keys and default objects to protect against undefined 'className'
            const currentStatus = (f.verificationStatus || 'PENDING_VERIFICATION').toUpperCase();
            let normalizedKey = 'PENDING_VERIFICATION';
            if (currentStatus === 'VERIFIED' || currentStatus === 'APPROVED') normalizedKey = 'VERIFIED';
            if (currentStatus === 'REJECTED' || currentStatus === 'DECLINED') normalizedKey = 'REJECTED';
            if (currentStatus === 'REPLACEMENT_REQUESTED') normalizedKey = 'REPLACEMENT_REQUESTED';

            const statusMeta = VERIFICATION_STATUS_META[normalizedKey] || {
              className: 'bg-gray-100 text-gray-600 border-gray-200',
              label: 'Pending Review'
            };

            const isSelfie = f.documentType === 'SELFIE' || f.documentType === 'SELFIE_LIVENESS';
            const isIdFront = f.documentType === 'NATIONAL_ID_FRONT';
            const isIdBack = f.documentType === 'NATIONAL_ID_BACK';

            return (
              <div key={f.id} className="px-6 py-4 hover:bg-gray-50 transition-colors">
                <div className="flex flex-col md:flex-row md:items-center gap-4">
                  <span className="text-2xl flex-shrink-0">
                    {isSelfie ? '🤳' : isIdFront ? '🪪' : isIdBack ? '🆔' : fileIcon(f.fileType)}
                  </span>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-800 truncate">
                      {f.fileName}
                    </p>
                    <p className="text-xs text-gray-400 mt-0.5 flex items-center gap-2 flex-wrap">
                      <span>{f.fileType + ' · ' + formatFileSize(f.fileSize)}</span>
                      {f.documentType && (
                        <span className="px-1.5 py-0.5 rounded bg-gray-100 text-gray-600 font-semibold text-[10px] uppercase tracking-wide">
                          {isIdFront ? 'National ID (Front)' : isIdBack ? 'National ID (Back)' : DOCUMENT_TYPE_LABELS[f.documentType] || f.documentType.replace(/_/g, ' ')}
                        </span>
                      )}
                      {f.uploadedByApplicant && (
                        <span className="px-1.5 py-0.5 rounded bg-blue-50 text-blue-600 font-semibold text-[10px] uppercase tracking-wide">
                          From Applicant
                        </span>
                      )}
                      <span className={`px-1.5 py-0.5 rounded font-semibold text-[10px] uppercase tracking-wide ${statusMeta.className}`}>
                        {statusMeta.label}
                      </span>
                    </p>
                  </div>
                  <div className="flex items-center gap-2 flex-shrink-0 flex-wrap justify-end">
                    <button
                      onClick={() => handlePreview(f.id)}
                      className="text-gray-600 hover:text-gray-800 text-xs font-medium border border-gray-200 bg-gray-50 hover:bg-gray-100 px-3 py-1.5 rounded-lg transition"
                    >
                      Preview
                    </button>
                    <button
                      onClick={() => handleDownload(f.id, f.fileName)}
                      className="text-blue-600 hover:text-blue-800 text-xs font-medium border border-blue-200 bg-blue-50 hover:bg-blue-100 px-3 py-1.5 rounded-lg transition"
                    >
                      Download
                    </button>
                    {f.verificationStatus !== 'VERIFIED' && (
                      <button
                        onClick={() => startVerifyAction(f.id, 'VERIFIED')}
                        className="text-green-700 hover:text-green-800 text-xs font-medium border border-green-200 bg-green-50 hover:bg-green-100 px-3 py-1.5 rounded-lg transition"
                      >
                        Verify
                      </button>
                    )}
                    <button
                      onClick={() => startVerifyAction(f.id, 'REPLACEMENT_REQUESTED')}
                      className="text-amber-700 hover:text-amber-800 text-xs font-medium border border-amber-200 bg-amber-50 hover:bg-amber-100 px-3 py-1.5 rounded-lg transition"
                    >
                      Request Replacement
                    </button>
                    <button
                      onClick={() => startVerifyAction(f.id, 'REJECTED')}
                      className="text-red-500 hover:text-red-700 text-xs font-medium border border-red-100 bg-white hover:bg-red-50 px-3 py-1.5 rounded-lg transition"
                    >
                    Reject
                  </button>
                  <button
                    onClick={() => handleDelete(f.id, f.fileName)}
                    className="text-red-400 hover:text-red-600 text-xs font-medium border border-red-200 bg-red-50 hover:bg-red-100 px-3 py-1.5 rounded-lg transition"
                  >
                    Delete
                  </button>
                </div>
              </div>

              {f.officerComment && (
                <div className="mt-2 ml-11 text-xs bg-gray-50 border border-gray-100 rounded-lg px-3 py-2 text-gray-600">
                  <span className="font-semibold text-gray-700">Officer Comment</span>
                  {f.verifiedByName && <span className="text-gray-400"> · {f.verifiedByName}</span>}
                  <p className="mt-0.5">{f.officerComment}</p>
                </div>
              )}

              {verifyingId === f.id && (
                <div className="mt-3 ml-11 bg-white border border-gray-200 rounded-lg p-3">
                  <label className="text-xs font-semibold text-gray-600 block mb-1.5">
                    {pendingAction === 'REJECTED' ? 'Reason for rejection' : 'Note for the borrower (what to re-upload)'}
                  </label>
                  <textarea
                    value={commentDraft}
                    onChange={e => setCommentDraft(e.target.value)}
                    rows={2}
                    className="w-full text-sm border border-gray-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-200"
                    placeholder={pendingAction === 'REJECTED'
                      ? 'e.g. The uploaded ID photo is blurry and cannot be verified.'
                      : 'e.g. Please upload a bank statement covering the last six months.'}
                  />
                  <div className="flex gap-2 mt-2">
                    <button
                      onClick={() => pendingAction && submitVerify(f.id, pendingAction, commentDraft)}
                      className="text-xs font-bold px-3 py-1.5 rounded-lg bg-gray-800 text-white hover:bg-gray-900"
                    >
                      Submit
                    </button>
                    <button
                      onClick={() => { setVerifyingId(null); setPendingAction(null); }}
                      className="text-xs font-medium px-3 py-1.5 rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-50"
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              )}
            </div>
          );})}
        </div>
      )}

      <div className="px-6 py-3 bg-gray-50 border-t border-gray-100">
        <p className="text-xs text-gray-400">
          Accepted: PDF, JPG, PNG, Word, Excel. Max 10MB per file.
        </p>
      </div>
    </div>
  );
}
