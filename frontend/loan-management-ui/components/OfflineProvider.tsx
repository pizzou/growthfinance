'use client';
import { useEffect, useRef, useState } from 'react';
import { useOnlineStatus } from '../hooks/useOnlineStatus';
import { pendingCount } from '../lib/offlineDb';
import { drainOfflineQueue } from '../lib/offlineSync';
import { toast } from '../hooks/useToast';

/**
 * Mount once near the root of the app. Handles three jobs:
 *  1. Registers the offline service worker (safe no-op if unsupported).
 *  2. Shows a persistent banner while offline, with a live count of
 *     changes waiting to sync.
 *  3. The moment connectivity returns, replays the offline queue against
 *     the real API and reports the result.
 */
export function OfflineProvider({ authHeader }: { authHeader: () => Record<string, string> }) {
  const online = useOnlineStatus();
  const [pending, setPending] = useState(0);
  const [syncing, setSyncing] = useState(false);
  const wasOffline = useRef(false);

  useEffect(() => {
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('/sw.js').catch(() => { /* offline caching is best-effort */ });
    }
  }, []);

  useEffect(() => {
    pendingCount().then(setPending);
    const interval = setInterval(() => pendingCount().then(setPending), 5000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    if (!online) { wasOffline.current = true; return; }
    if (!wasOffline.current) return; // only sync on an actual offline -> online transition
    wasOffline.current = false;

    (async () => {
      const before = await pendingCount();
      if (before === 0) return;
      setSyncing(true);
      const result = await drainOfflineQueue(authHeader);
      setSyncing(false);
      setPending(await pendingCount());

      if (result.succeeded.length > 0) {
        toast('success', `Back online — synced ${result.succeeded.length} saved change${result.succeeded.length > 1 ? 's' : ''}.`);
      }
      if (result.failed.length > 0) {
        toast('warning', `${result.failed.length} change${result.failed.length > 1 ? 's' : ''} couldn't sync yet — will keep retrying.`);
      }
    })();
  }, [online, authHeader]);

  if (online && pending === 0) return null;

  return (
    <div className={`fixed top-0 left-0 right-0 z-[60] text-center text-xs font-semibold py-2 px-4
      ${!online ? 'bg-amber-500 text-white' : syncing ? 'bg-blue-500 text-white' : 'bg-teal-600 text-white'}`}>
      {!online
        ? `📡 You're offline — changes are being saved on this device${pending > 0 ? ` (${pending} waiting)` : ''} and will sync automatically once you're back online.`
        : syncing
          ? `🔄 Syncing ${pending} saved change${pending > 1 ? 's' : ''}…`
          : `⏳ ${pending} change${pending > 1 ? 's' : ''} waiting to sync…`}
    </div>
  );
}
