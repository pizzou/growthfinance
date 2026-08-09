import API from './api';
import { BorrowerFile } from '../types/index';

const unwrap = (body: unknown): unknown => {
  if (body !== null && typeof body === 'object' && 'data' in body) {
    return (body as { data: unknown }).data;
  }
  return body;
};

export const getFilesByBorrower = (borrowerId: number): Promise<BorrowerFile[]> =>
  API.get(`/files/borrower/${borrowerId}`)
    .then(r => unwrap(r.data) as BorrowerFile[]);

export const uploadFile = (borrowerId: number, file: File, documentType = 'OTHER'): Promise<BorrowerFile> => {
  const form = new FormData();
  form.append('file', file);
  form.append('documentType', documentType);
  return API.post(`/files/upload/${borrowerId}`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }).then(r => unwrap(r.data) as BorrowerFile);
};

export const deleteFile = (fileId: number): Promise<void> =>
  API.delete(`/files/${fileId}`).then(() => undefined);

export const verifyFile = (
  fileId: number,
  status: 'VERIFIED' | 'REJECTED' | 'REPLACEMENT_REQUESTED',
  comment?: string
): Promise<BorrowerFile> =>
  API.patch(`/files/${fileId}/verify`, { status, comment })
    .then(r => unwrap(r.data) as BorrowerFile);

// `/api/files/**` requires a JWT (sent as an Authorization header by the axios
// instance's interceptor). A plain `window.open('/files/download/1')` or an <a href>
// can't attach that header, so the request 401s with nothing visibly wrong in the UI —
// that silent failure is why staff could never actually view uploaded documents.
// Fetching as a blob through the authenticated axios instance, then handing the
// browser an object URL, is the fix.
async function fetchAsBlobUrl(path: string): Promise<string> {
  const res = await API.get(path, { responseType: 'blob' });
  return URL.createObjectURL(res.data as Blob);
}

/** Opens the document inline in a new tab (image/PDF renders directly). */
export const previewFile = async (fileId: number): Promise<void> => {
  const url = await fetchAsBlobUrl(`/files/preview/${fileId}`);
  window.open(url, '_blank');
  setTimeout(() => URL.revokeObjectURL(url), 60000);
};

/** Forces a "Save As" download with the original filename. */
export const downloadFile = async (fileId: number, fileName: string): Promise<void> => {
  const url = await fetchAsBlobUrl(`/files/download/${fileId}`);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName || 'document';
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 60000);
};

/** Resolves to an object URL for inline rendering (e.g. an <img>/<iframe> in a KYC panel)
 *  — caller is responsible for revoking it (URL.revokeObjectURL) when done. */
export const getInlineBlobUrl = (fileId: number): Promise<string> =>
  fetchAsBlobUrl(`/files/preview/${fileId}`);

export const formatFileSize = (bytes?: number): string => {
  if (!bytes) return '—';
  if (bytes < 1024)        return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

export const fileIcon = (fileType?: string): string => {
  if (!fileType) return '📄';
  if (fileType.includes('pdf'))                                    return '📕';
  if (fileType.includes('image'))                                  return '🖼️';
  if (fileType.includes('word') || fileType.includes('document'))  return '📝';
  if (fileType.includes('sheet') || fileType.includes('excel'))    return '📊';
  return '📄';
};

export const DOCUMENT_TYPE_LABELS: Record<string, string> = {
  NATIONAL_ID: 'National ID', PASSPORT: 'Passport', DRIVING_LICENSE: 'Driving License',
  PROOF_OF_ADDRESS: 'Proof of Address', BANK_STATEMENT: 'Bank Statement', PAYSLIP: 'Payslip',
  EMPLOYMENT_LETTER: 'Employment Letter', BUSINESS_REGISTRATION: 'Business Registration Certificate',
  COLLATERAL_DOCUMENT: 'Collateral Document', SINGLE_CERTIFICATE: 'Single Status Certificate',
  MARRIAGE_CERTIFICATE: 'Marriage Certificate', SELFIE: 'Selfie', OTHER: 'Other Document',
};

export const VERIFICATION_STATUS_META: Record<string, { label: string; className: string }> = {
  PENDING_VERIFICATION:  { label: 'Pending Verification',   className: 'bg-gray-100 text-gray-600' },
  VERIFIED:               { label: 'Verified',               className: 'bg-green-50 text-green-700' },
  REJECTED:                { label: 'Rejected',               className: 'bg-red-50 text-red-700' },
  REPLACEMENT_REQUESTED:   { label: 'Replacement Requested',  className: 'bg-amber-50 text-amber-700' },
};
