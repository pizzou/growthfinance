import API, { get, post } from './api';

export const regulatoryApi = {
  // BNR — staff preview
  bnrSummary: (params: { branchId?: number; period?: string; from?: string; to?: string }) =>
    get(`/regulatory/bnr/summary?${qs(params)}`),
  bnrByLoanType: (params: { branchId?: number; period?: string; from?: string; to?: string }) =>
    get(`/regulatory/bnr/breakdown/loan-type?${qs(params)}`),
  bnrByBranch: (params: { period?: string; from?: string; to?: string }) =>
    get(`/regulatory/bnr/breakdown/branch?${qs(params)}`),
  bnrByGender: (params: { branchId?: number; period?: string; from?: string; to?: string }) =>
    get(`/regulatory/bnr/breakdown/gender?${qs(params)}`),
  bnrExport: (format: 'xlsx' | 'csv' | 'pdf', params: { branchId?: number; period?: string; from?: string; to?: string }) =>
    downloadFile(`/regulatory/bnr/export?format=${format}&${qs(params)}`, `bnr-summary.${format}`),

  // Credit Bureau — staff preview
  creditBureauPreview: (params: { branchId?: number; from?: string; to?: string }) =>
    get(`/regulatory/credit-bureau/preview?${qs(params)}`),
  creditBureauExport: (format: 'xlsx' | 'csv' | 'pdf', params: { branchId?: number; from?: string; to?: string }) =>
    downloadFile(`/regulatory/credit-bureau/export?format=${format}&${qs(params)}`, `credit-bureau-export.${format}`),

  // API client (key) management
  listApiClients: () => get('/regulatory/api-clients'),
  createApiClient: (data: { name: string; clientType: 'BNR' | 'CREDIT_BUREAU'; contactEmail?: string; description?: string; expiresAt?: string | null }) =>
    post('/regulatory/api-clients', data),
  revokeApiClient: (id: number, reason?: string) =>
    post(`/regulatory/api-clients/${id}/revoke`, { reason }),
};

function qs(params: Record<string, unknown>) {
  const p = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.set(k, String(v));
  });
  return p.toString();
}

async function downloadFile(path: string, filename: string) {
  const res = await API.get(path, { responseType: 'blob' });
  const url = URL.createObjectURL(res.data as Blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 60000);
}