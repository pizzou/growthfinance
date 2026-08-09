
"use client";

import {
    useCallback,
    useEffect,
    useRef,
    useState,
} from "react";

import { drainOfflineQueue } from "./offlineSync";
import {
    pendingCount,
} from "./offlineDb";

/**
 * LoanSaaS Pro — Offline Synchronization Provider
 *
 * Responsibilities:
 *
 * 1. Detect when the browser comes back online.
 * 2. Synchronize queued POST / PUT / PATCH / DELETE requests.
 * 3. Retry synchronization periodically.
 * 4. Prevent multiple sync processes from running simultaneously.
 * 5. Keep the pending-change indicator updated.
 *
 * IMPORTANT:
 *
 * This provider synchronizes MUTATIONS only.
 *
 * Offline GET/read caching is handled by api.ts + offlineDb.ts.
 */

export default function SyncProvider() {

    const [pending, setPending] =
        useState<number>(0);

    const [syncing, setSyncing] =
        useState<boolean>(false);

    /**
     * Prevent overlapping synchronization runs.
     *
     * Without this, these can all fire at almost the same time:
     *
     * - initial syncNow()
     * - online event
     * - 30-second interval
     *
     * That could cause the same queued request to be
     * submitted more than once.
     */
    const syncRunning =
        useRef<boolean>(false);

    /**
     * Prevent state updates after component unmount.
     */
    const mounted =
        useRef<boolean>(false);

    /**
     * ------------------------------------------------------------
     * REFRESH PENDING COUNT
     * ------------------------------------------------------------
     */

    const refreshPending =
        useCallback(async () => {

            try {

                const count =
                    await pendingCount();

                if (mounted.current) {
                    setPending(count);
                }

            } catch (error) {

                console.error(
                    "Failed to read offline queue:",
                    error
                );

            }

        }, []);

    /**
     * ------------------------------------------------------------
     * AUTH HEADER
     * ------------------------------------------------------------
     */

    const getAuthHeader =
        useCallback(
            (): Record<string, string> => {

                if (
                    typeof window ===
                    "undefined"
                ) {
                    return {};
                }

                const token =
                    localStorage.getItem(
                        "token"
                    );

                if (!token) {
                    return {};
                }

                return {
                    Authorization:
                        `Bearer ${token}`,
                };
            },
            []
        );

    /**
     * ------------------------------------------------------------
     * SYNCHRONIZE OFFLINE QUEUE
     * ------------------------------------------------------------
     */

    const syncNow =
        useCallback(async () => {

            /**
             * Never attempt synchronization while offline.
             */
            if (
                typeof navigator !==
                    "undefined" &&
                !navigator.onLine
            ) {
                return;
            }

            /**
             * Prevent overlapping sync operations.
             */
            if (syncRunning.current) {
                console.log(
                    "Offline sync already running."
                );

                return;
            }

            syncRunning.current = true;

            if (mounted.current) {
                setSyncing(true);
            }

            try {

                console.log(
                    "Starting offline synchronization..."
                );

                const result =
                    await drainOfflineQueue(
                        getAuthHeader
                    );

                /**
                 * Successfully synchronized requests.
                 */
                if (
                    result.succeeded.length >
                    0
                ) {

                    console.log(
                        `Successfully synced ${result.succeeded.length} offline request(s).`
                    );

                    for (
                        const action
                        of result.succeeded
                    ) {

                        console.log(
                            "Synced offline action:",
                            {
                                id:
                                    action.id,

                                method:
                                    action.method,

                                url:
                                    action.url,

                                label:
                                    action.label,
                            }
                        );
                    }
                }

                /**
                 * Failed requests remain in IndexedDB.
                 */
                if (
                    result.failed.length >
                    0
                ) {

                    console.warn(
                        `Failed to sync ${result.failed.length} offline request(s).`
                    );

                    for (
                        const failure
                        of result.failed
                    ) {

                        console.warn(
                            "Offline sync failure:",
                            {
                                id:
                                    failure.action.id,

                                method:
                                    failure.action.method,

                                url:
                                    failure.action.url,

                                error:
                                    failure.error,
                            }
                        );
                    }
                }

                /**
                 * Always refresh the queue count.
                 */
                await refreshPending();

            } catch (error) {

                console.error(
                    "Offline synchronization failed:",
                    error
                );

                await refreshPending();

            } finally {

                syncRunning.current =
                    false;

                if (mounted.current) {
                    setSyncing(false);
                }
            }

        }, [
            getAuthHeader,
            refreshPending,
        ]);

    /**
     * ------------------------------------------------------------
     * ONLINE EVENT
     * ------------------------------------------------------------
     *
     * Browser fires this when network connectivity returns.
     */

    const handleOnline =
        useCallback(() => {

            console.log(
                "Internet connection restored."
            );

            /**
             * Small delay gives the browser/network
             * a moment to stabilize before sending
             * queued requests.
             */
            window.setTimeout(
                () => {
                    void syncNow();
                },
                1000
            );

        }, [syncNow]);

    /**
     * ------------------------------------------------------------
     * OFFLINE EVENT
     * ------------------------------------------------------------
     */

    const handleOffline =
        useCallback(() => {

            console.warn(
                "Internet connection lost. Offline mode active."
            );

        }, []);

    /**
     * ------------------------------------------------------------
     * INITIALIZATION
     * ------------------------------------------------------------
     */

    useEffect(() => {

        mounted.current = true;

        /**
         * Load current pending count.
         */
        void refreshPending();

        /**
         * Register connectivity listeners.
         */
        window.addEventListener(
            "online",
            handleOnline
        );

        window.addEventListener(
            "offline",
            handleOffline
        );

        /**
         * Try synchronization immediately.
         *
         * This handles the case where:
         *
         * - user had pending actions
         * - closes browser
         * - reopens application while online
         */
        void syncNow();

        /**
         * Periodic retry.
         *
         * This protects against situations where:
         *
         * - online event is missed
         * - Wi-Fi reconnects strangely
         * - backend was temporarily unavailable
         * - Render backend was waking up
         */
        const interval =
            window.setInterval(
                () => {

                    if (
                        navigator.onLine
                    ) {
                        void syncNow();
                    }

                },
                30_000
            );

        /**
         * Cleanup.
         */
        return () => {

            mounted.current =
                false;

            window.removeEventListener(
                "online",
                handleOnline
            );

            window.removeEventListener(
                "offline",
                handleOffline
            );

            window.clearInterval(
                interval
            );
        };

    }, [
        handleOnline,
        handleOffline,
        refreshPending,
        syncNow,
    ]);

    /**
     * ------------------------------------------------------------
     * UI
     * ------------------------------------------------------------
     */

    /**
     * Nothing pending and not syncing.
     */
    if (
        pending === 0 &&
        !syncing
    ) {
        return null;
    }

    /**
     * Currently synchronizing.
     */
    if (syncing) {

        return (
            <div
                style={{
                    position: "fixed",
                    top: 0,
                    left: 0,
                    right: 0,

                    background:
                        "#2563EB",

                    color: "#FFFFFF",

                    padding:
                        "8px 12px",

                    textAlign:
                        "center",

                    fontWeight: 600,

                    fontSize:
                        "14px",

                    zIndex: 9999,
                }}
            >
                🔄 Synchronizing
                offline changes...
            </div>
        );
    }

    /**
     * Pending actions remain.
     */
    return (
        <div
            style={{
                position: "fixed",

                top: 0,
                left: 0,
                right: 0,

                background:
                    "#0D9488",

                color:
                    "#FFFFFF",

                padding:
                    "8px 12px",

                textAlign:
                    "center",

                fontWeight:
                    600,

                fontSize:
                    "14px",

                zIndex:
                    9999,
            }}
        >
            ⏳ {pending} change
            {pending > 1 ? "s" : ""}
            {" "}waiting to sync...
        </div>
    );
}
