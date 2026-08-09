'use client';

export type OfflineActionType = string;

export interface OfflineAction {
  id:        string;
  type:      OfflineActionType;
  payload:   unknown;
  timestamp: number;
  synced:    boolean;
  retries:   number;
}

const DB_NAME = 'loansaas_offline';
const STORE   = 'pending_actions';
const DB_VER  = 1;

function openDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VER);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE)) {
        db.createObjectStore(STORE, { keyPath: 'id' });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror   = () => reject(req.error);
  });
}

export async function queueAction(
  type: OfflineActionType,
  payload: unknown
): Promise<void> {
  const db = await openDB();
  const action: OfflineAction = {
    id:        `${type}_${Date.now()}_${Math.random().toString(36).slice(2)}`,
    type,
    payload,
    timestamp: Date.now(),
    synced:    false,
    retries:   0,
  };
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, 'readwrite');
    tx.objectStore(STORE).add(action);
    tx.oncomplete = () => resolve();
    tx.onerror    = () => reject(tx.error);
  });
}

export async function getPendingActions(): Promise<OfflineAction[]> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx  = db.transaction(STORE, 'readonly');
    const req = tx.objectStore(STORE).getAll();
    req.onsuccess = () =>
      resolve((req.result as OfflineAction[]).filter(a => !a.synced));
    req.onerror = () => reject(req.error);
  });
}

export async function markSynced(id: string): Promise<void> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx    = db.transaction(STORE, 'readwrite');
    const store = tx.objectStore(STORE);
    const req   = store.get(id);
    req.onsuccess = () => {
      const record = req.result as OfflineAction;
      if (record) {
        record.synced = true;
        store.put(record);
      }
    };
    tx.oncomplete = () => resolve();
    tx.onerror    = () => reject(tx.error);
  });
}

export async function clearSynced(): Promise<void> {
  const db = await openDB();
  return new Promise((resolve, reject) => {
    const tx    = db.transaction(STORE, 'readwrite');
    const store = tx.objectStore(STORE);
    const req   = store.getAll();
    req.onsuccess = () => {
      (req.result as OfflineAction[])
        .filter(a => a.synced)
        .forEach(a => store.delete(a.id));
    };
    tx.oncomplete = () => resolve();
    tx.onerror    = () => reject(tx.error);
  });
}