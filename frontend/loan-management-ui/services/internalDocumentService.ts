import API from './api';

export interface InternalDocumentSummary {
  id: number;
  title: string;
  category: string;
  description?: string;
  fileName: string;
  fileType: string;
  fileSize: number;
  uploadedByName?: string;
  createdAt: string;
}

const unwrap = (body: unknown): unknown => {
  if (body !== null && typeof body === 'object' && 'data' in body) {
    return (body as { data: unknown }).data;
  }
  return body;
};

export const getInternalDocuments = (category?: string): Promise<InternalDocumentSummary[]> =>
  API.get('/internal-documents', { params: category ? { category } : {} })
    .then(r => unwrap(r.data) as InternalDocumentSummary[]);

export const uploadInternalDocument = (
  file: File, title: string, category: string, description?: string
): Promise<InternalDocumentSummary> => {
  const form = new FormData();
  form.append('file', file);
  form.append('title', title);
  form.append('category', category);
  if (description) form.append('description', description);
  return API.post('/internal-documents', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }).then(r => unwrap(r.data) as InternalDocumentSummary);
};

export const deleteInternalDocument = (id: number): Promise<void> =>
  API.delete(`/internal-documents/${id}`).then(() => undefined);

// Same reasoning as fileService.ts's fetchAsBlobUrl: a plain <a href> can't attach the JWT
// this endpoint requires, so it 401s silently. Fetch as a blob through the authenticated
// axios instance instead.
export const downloadInternalDocument = async (id: number, fileName: string): Promise<void> => {
  const res = await API.get(`/internal-documents/${id}/download`, { responseType: 'blob' });
  const url = URL.createObjectURL(res.data as Blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName || 'document';
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 60000);
};

export const INTERNAL_DOC_CATEGORY_LABELS: Record<string, string> = {
  POLICY: 'Policy', CONTRACT: 'Contract', MEMO: 'Memo', TEMPLATE: 'Template',
  BOARD_MINUTES: 'Board Minutes', COMPLIANCE: 'Compliance', OTHER: 'Other',
};

export const formatFileSize = (bytes?: number): string => {
  if (!bytes) return '—';
  if (bytes < 1024)        return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};
