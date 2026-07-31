/**
 * Offline data layer — a small IndexedDB wrapper with two stores:
 *
 *  - `pendingActions`: a durable queue of API calls made while offline
 *    (loan applications, payments recorded in the field, etc). Each one
 *    is replayed against the real API, in order, the moment connectivity
 *    returns — see SyncProvider.
 *
 *  - `cache`: last-known-good responses for GET requests, so screens the
 *    user already visited (their loan list, a borrower's profile) keep
 *    showing real data instead of a blank/broken page when offline.
 *
 * No external dependency — IndexedDB is wrapped directly in promises,
 * since the API surface we need here is small and stable.
 */

const DB_NAME = 'loansaas-offline';
const DB_VERSION = 1;
const STORE_QUEUE = 'pendingActions';
const STORE_CACHE = 'cache';

export interface PendingAction {
  id: string;
  url: string;
  method: 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  label: string;        // human-readable, shown while syncing e.g. "Loan application — Jean Uwimana"
  createdAt: string;
  attempts: number;
  lastError?: string;
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') { reject(new Error('IndexedDB unavailable')); return; }
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_QUEUE)) db.createObjectStore(STORE_QUEUE, { keyPath: 'id' });
      if (!db.objectStoreNames.contains(STORE_CACHE))  db.createObjectStore(STORE_CACHE,  { keyPath: 'url' });
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror   = () => reject(req.error);
  });
}

async function withStore<T>(storeName: string, mode: IDBTransactionMode, fn: (store: IDBObjectStore) => IDBRequest): Promise<T> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, mode);
    const store = tx.objectStore(storeName);
    const req = fn(store);
    req.onsuccess = () => resolve(req.result as T);
    req.onerror   = () => reject(req.error);
  });
}

// ---- Pending action queue ----

export async function queueAction(action: Omit<PendingAction, 'id' | 'createdAt' | 'attempts'>): Promise<PendingAction> {
  const full: PendingAction = { ...action, id: crypto.randomUUID(), createdAt: new Date().toISOString(), attempts: 0 };
  await withStore(STORE_QUEUE, 'readwrite', (s) => s.put(full));
  return full;
}

export async function getPendingActions(): Promise<PendingAction[]> {
  try {
    const all = await withStore<PendingAction[]>(STORE_QUEUE, 'readonly', (s) => s.getAll());
    return (all ?? []).sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  } catch { return []; }
}

export async function removePendingAction(id: string): Promise<void> {
  await withStore(STORE_QUEUE, 'readwrite', (s) => s.delete(id));
}

export async function bumpAttempt(id: string, error: string): Promise<void> {
  const db = await openDb();
  const tx = db.transaction(STORE_QUEUE, 'readwrite');
  const store = tx.objectStore(STORE_QUEUE);
  const getReq = store.get(id);
  getReq.onsuccess = () => {
    const item = getReq.result as PendingAction | undefined;
    if (item) store.put({ ...item, attempts: item.attempts + 1, lastError: error });
  };
}

export async function pendingCount(): Promise<number> {
  return (await getPendingActions()).length;
}

// ---- Read cache (best-effort, non-durable UX sugar) ----

export async function cacheSet(url: string, data: unknown): Promise<void> {
  try { await withStore(STORE_CACHE, 'readwrite', (s) => s.put({ url, data, cachedAt: new Date().toISOString() })); }
  catch { /* best-effort */ }
}

export async function cacheGet<T = unknown>(url: string): Promise<T | null> {
  try {
    const row = await withStore<{ url: string; data: T } | undefined>(STORE_CACHE, 'readonly', (s) => s.get(url));
    return row?.data ?? null;
  } catch { return null; }
}
