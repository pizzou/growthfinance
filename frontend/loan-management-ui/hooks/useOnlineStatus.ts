'use client';
import { useEffect, useState } from 'react';

/** Tracks real connectivity, not just navigator.onLine (which is unreliable alone —
 *  it can report true on a wifi network with no actual internet). We treat the
 *  browser events as the source of truth for transitions, since that's what's
 *  actually available cross-browser without pinging a server on every render. */
export function useOnlineStatus() {
  const [online, setOnline] = useState(true);

  useEffect(() => {
    setOnline(typeof navigator !== 'undefined' ? navigator.onLine : true);
    const goOnline  = () => setOnline(true);
    const goOffline = () => setOnline(false);
    window.addEventListener('online', goOnline);
    window.addEventListener('offline', goOffline);
    return () => {
      window.removeEventListener('online', goOnline);
      window.removeEventListener('offline', goOffline);
    };
  }, []);

  return online;
}
