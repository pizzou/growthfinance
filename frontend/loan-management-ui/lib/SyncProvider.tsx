"use client";

import { useEffect, useState } from "react";
import { drainOfflineQueue } from "./offlineSync";
import { pendingCount } from "./offlineDb";

export default function SyncProvider() {
  const [pending, setPending] = useState(0);

  async function refreshPending() {
    setPending(await pendingCount());
  }

  async function syncNow() {
    if (!navigator.onLine) return;

    try {
      const result = await drainOfflineQueue((): Record<string, string> => {
    const token = localStorage.getItem("token");

    if (!token) {
        return {};
    }

    return {
        Authorization: `Bearer ${token}`,
    };
});

      if (result.succeeded.length > 0) {
        console.log(
          `Successfully synced ${result.succeeded.length} offline request(s).`
        );
      }

      if (result.failed.length > 0) {
        console.warn(
          `Failed to sync ${result.failed.length} request(s).`,
          result.failed
        );
      }

      await refreshPending();
    } catch (e) {
      console.error("Offline synchronization failed", e);
    }
  }

  useEffect(() => {
    refreshPending();

    window.addEventListener("online", syncNow);

    // Try immediately on page load
    syncNow();

    const interval = setInterval(syncNow, 30000);

    return () => {
      window.removeEventListener("online", syncNow);
      clearInterval(interval);
    };
  }, []);

  if (pending === 0) return null;

  return (
    <div
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        background: "#0D9488",
        color: "#fff",
        padding: "8px",
        textAlign: "center",
        fontWeight: 600,
        zIndex: 9999,
      }}
    >
      ⏳ {pending} change{pending > 1 ? "s" : ""} waiting to sync...
    </div>
  );
}