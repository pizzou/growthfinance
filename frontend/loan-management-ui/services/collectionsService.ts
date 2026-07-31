import { get, post } from './api';

export type CollectionBucket = 'CURRENT' | 'DPD_1_30' | 'DPD_31_60' | 'DPD_61_90' | 'DPD_90_PLUS' | 'WRITE_OFF';
export type CollectionStatus = 'OPEN' | 'IN_PROGRESS' | 'PROMISE_TO_PAY' | 'ESCALATED' | 'LEGAL' | 'RESOLVED' | 'WRITTEN_OFF';
export type CollectionActionType =
  | 'CALL' | 'SMS' | 'EMAIL' | 'FIELD_VISIT' | 'LEGAL_NOTICE' | 'PROMISE_TO_PAY'
  | 'PAYMENT_RECEIVED' | 'ESCALATED' | 'CASE_OPENED' | 'CASE_CLOSED' | 'WRITE_OFF';

export interface CollectionCase {
  id: number;
  loan?: { id: number; referenceNumber: string; currency: string; outstandingBalance: number;
    borrower?: { firstName: string; lastName: string; phone?: string } };
  assignedAgent?: { id: number; name: string };
  bucket: CollectionBucket;
  status: CollectionStatus;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
  daysPastDue?: number;
  overdueAmount?: number;
  totalOutstanding?: number;
  lastContactDate?: string;
  nextActionDate?: string;
  promiseToPayDate?: string;
  promiseToPayAmount?: number;
  resolutionNotes?: string;
}

export interface CollectionAction {
  id: number;
  actionType: CollectionActionType;
  performedBy?: string;
  notes?: string;
  outcome?: string;
  promiseDate?: string;
  promiseAmount?: number;
  createdAt: string;
}

export interface CollectionStats {
  casesByBucket: Record<string, number>;
  overdueAmountByBucket: Record<string, number>;
  totalOpenCases: number;
  totalOverdueAmount: number;
  activePromises: number;
}

export const getCollectionsQueue = (params?: { bucket?: CollectionBucket; status?: CollectionStatus; agentId?: number }) => {
  const qs = new URLSearchParams();
  if (params?.bucket) qs.set('bucket', params.bucket);
  if (params?.status) qs.set('status', params.status);
  if (params?.agentId) qs.set('agentId', String(params.agentId));
  const suffix = qs.toString() ? `?${qs.toString()}` : '';
  return get(`/collections/queue${suffix}`) as Promise<CollectionCase[]>;
};

export const getCollectionsStats = (): Promise<CollectionStats> => get('/collections/stats') as Promise<CollectionStats>;

export const getCollectionCase = (id: number): Promise<CollectionCase> => get(`/collections/cases/${id}`) as Promise<CollectionCase>;

export const getCollectionActions = (id: number): Promise<CollectionAction[]> =>
  get(`/collections/cases/${id}/actions`) as Promise<CollectionAction[]>;

export const assignCollectionAgent = (caseId: number, agentId: number) =>
  post(`/collections/cases/${caseId}/assign`, { agentId });

export const logCollectionAction = (caseId: number, payload: {
  actionType: CollectionActionType; notes?: string; outcome?: string; promiseDate?: string; promiseAmount?: number;
}) => post(`/collections/cases/${caseId}/actions`, payload);

export const syncCollectionsQueue = () => post('/collections/sync');
