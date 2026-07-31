'use client';
import { useEffect, useState } from 'react';
import { useNetworkStatus } from '../../hooks/useNetworkStatus';
import { getPendingActions, clearSynced } from '../../hooks/useOfflineSync';

export function OfflineBanner() {
  const { isOnline }              = useNetworkStatus();
  const [pending,  setPending]    = useState(0);
  const [syncing,  setSyncing]    = useState(false);
  const [synced,   setSynced]     = useState(0);
  const [mounted,  setMounted]    = useState(false);

  // Only run client-side
  useEffect(() => { setMounted(true); }, []);

  useEffect(() => {
    if (!mounted) return;
    getPendingActions()
      .then(a => setPending(a.length))
      .catch(() => {});
  }, [isOnline, mounted]);

  useEffect(() => {
    if (!mounted || !isOnline || pending === 0) return;
    setSyncing(true);
    // Give API interceptors time to sync, then clear
    const t = setTimeout(async () => {
      await clearSynced().catch(() => {});
      const remaining = await getPendingActions().catch(() => []);
      setSynced(pending - remaining.length);
      setPending(remaining.length);
      setSyncing(false);
    }, 3000);
    return () => clearTimeout(t);
  }, [isOnline, pending, mounted]);

  if (!mounted) return null;
  if (isOnline && pending === 0 && synced === 0) return null;

  return (
    <div className={`fixed top-0 left-0 right-0 z-50 flex items-center justify-center gap-2 px-4 py-2 text-sm font-medium text-white transition-all ${
      !isOnline ? 'bg-red-600' : syncing ? 'bg-blue-600' : 'bg-green-600'
    }`}>
      {!isOnline && (
        <>
          <span className="w-2 h-2 rounded-full bg-white animate-pulse inline-block" />
          You are offline. {pending > 0 ? `${pending} action${pending !== 1 ? 's' : ''} queued and will sync when reconnected.` : 'Changes will sync when reconnected.'}
        </>
      )}
      {isOnline && syncing && (
        <>
          <span className="animate-spin w-3 h-3 border-2 border-white border-t-transparent rounded-full inline-block" />
          Syncing {pending} offline action{pending !== 1 ? 's' : ''}…
        </>
      )}
      {isOnline && !syncing && synced > 0 && (
        <>
          ✓ {synced} offline action{synced !== 1 ? 's' : ''} synced successfully.
          <button onClick={() => setSynced(0)} className="ml-2 underline text-white/80 hover:text-white">Dismiss</button>
        </>
      )}
    </div>
  );
}
