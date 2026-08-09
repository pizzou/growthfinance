
// lib/offlineSync.ts
//
// Durable offline mutation replay.
//
// Responsibilities:
// - Read pending mutations from IndexedDB
// - Replay them when the network is available
// - Preserve action order
// - Remove successful actions
// - Increment attempts for failed actions
// - Avoid retrying authentication/authorization failures
// - Support both relative and absolute action URLs
// - Never silently lose an offline action

import {
    getPendingActions,
    removePendingAction,
    bumpAttempt,
    PendingAction,
} from "./offlineDb";

const API_BASE = (
    process.env.NEXT_PUBLIC_API_URL ||
    "http://localhost:8080/api"
).replace(/\/+$/, "");

const MAX_ATTEMPTS = 5;

let syncInProgress = false;

export interface SyncResult {
    succeeded: PendingAction[];

    failed: {
        action: PendingAction;
        error: string;
        status?: number;
        retryable: boolean;
    }[];
}

function buildUrl(actionUrl: string): string {
    // Already absolute
    if (
        actionUrl.startsWith("http://") ||
        actionUrl.startsWith("https://")
    ) {
        return actionUrl;
    }

    // Relative API path
    if (actionUrl.startsWith("/")) {
        return `${API_BASE}${actionUrl}`;
    }

    return `${API_BASE}/${actionUrl}`;
}

function isOnline(): boolean {
    if (typeof navigator === "undefined") {
        return true;
    }

    return navigator.onLine;
}

function getErrorMessage(
    status: number,
    responseText: string
): string {

    if (responseText) {
        try {
            const parsed = JSON.parse(responseText);

            return (
                parsed?.error ||
                parsed?.message ||
                parsed?.detail ||
                responseText
            );
        } catch {
            return responseText;
        }
    }

    switch (status) {
        case 401:
            return "Authentication expired. Please log in again.";

        case 403:
            return "You do not have permission to perform this action.";

        case 404:
            return "The requested API endpoint was not found.";

        case 409:
            return "The action conflicts with the current server state.";

        case 422:
            return "The server rejected the submitted data.";

        case 429:
            return "Too many requests. The action will be retried later.";

        case 500:
        case 502:
        case 503:
        case 504:
            return "The server is temporarily unavailable.";

        default:
            return `HTTP ${status}`;
    }
}

function isRetryableStatus(status: number): boolean {

    // Authentication/authorization problems should NOT
    // be retried automatically.
    if (status === 401 || status === 403) {
        return false;
    }

    // Invalid request/data should not be retried forever.
    if (
        status === 400 ||
        status === 404 ||
        status === 405 ||
        status === 409 ||
        status === 422
    ) {
        return false;
    }

    // Server/network/rate-limit failures can be retried.
    return (
        status === 408 ||
        status === 425 ||
        status === 429 ||
        status >= 500
    );
}

/**
 * Replay all durable offline actions.
 *
 * The function is protected against multiple simultaneous
 * sync operations.
 */
export async function drainOfflineQueue(
    authHeader: () => Record<string, string>
): Promise<SyncResult> {

    if (syncInProgress) {
        return {
            succeeded: [],
            failed: [],
        };
    }

    if (!isOnline()) {
        console.log(
            "[OfflineSync] Device is offline. Sync postponed."
        );

        return {
            succeeded: [],
            failed: [],
        };
    }

    syncInProgress = true;

    const result: SyncResult = {
        succeeded: [],
        failed: [],
    };

    try {

        const actions = await getPendingActions();

        if (!actions.length) {
            console.log(
                "[OfflineSync] Queue is empty."
            );

            return result;
        }

        console.log(
            `[OfflineSync] ${actions.length} pending action(s) found.`
        );

        /*
         * IMPORTANT:
         *
         * Process sequentially.
         *
         * If action A creates a borrower and action B creates
         * a loan for that borrower, B must not run before A.
         */
        for (const action of actions) {

            if (!isOnline()) {

                console.warn(
                    "[OfflineSync] Connection lost during sync."
                );

                break;
            }

            if (action.attempts >= MAX_ATTEMPTS) {

                console.warn(
                    "[OfflineSync] Maximum attempts reached:",
                    action.id,
                    action.url
                );

                continue;
            }

            const url = buildUrl(action.url);

            try {

                console.log(
                    "[OfflineSync] Replaying:",
                    action.method,
                    url,
                    "attempt:",
                    action.attempts + 1
                );

                const headers: Record<string, string> = {
                    Accept: "application/json",
                    ...authHeader(),
                };

                /*
                 * Only send JSON content type when there is
                 * actually a JSON body.
                 */
                if (action.body !== undefined && action.body !== null) {
                    headers["Content-Type"] = "application/json";
                }

                const response = await fetch(
                    url,
                    {
                        method: action.method,
                        headers,
                        body:
                            action.body !== undefined &&
                            action.body !== null
                                ? JSON.stringify(action.body)
                                : undefined,

                        /*
                         * Prevent browser cache from interfering
                         * with mutation replay.
                         */
                        cache: "no-store",
                    }
                );

                const responseText =
                    await response.text();

                if (!response.ok) {

                    const error =
                        getErrorMessage(
                            response.status,
                            responseText
                        );

                    const retryable =
                        isRetryableStatus(
                            response.status
                        );

                    /*
                     * Authentication/authorization errors should
                     * stop automatic replay instead of consuming
                     * all retry attempts.
                     */
                    if (
                        response.status === 401 ||
                        response.status === 403
                    ) {

                        console.error(
                            "[OfflineSync] Authentication/authorization failed:",
                            url,
                            response.status,
                            error
                        );

                        result.failed.push({
                            action,
                            error,
                            status: response.status,
                            retryable: false,
                        });

                        /*
                         * Do not remove the action.
                         *
                         * It remains in IndexedDB so that after the
                         * user authenticates again it can be replayed.
                         */
                        break;
                    }

                    /*
                     * Permanent validation/business error.
                     *
                     * Keep the action in IndexedDB so the user can
                     * inspect/recover it, but don't blindly retry
                     * it on every online event.
                     */
                    if (!retryable) {

                        await bumpAttempt(
                            action.id,
                            error
                        );

                        result.failed.push({
                            action,
                            error,
                            status: response.status,
                            retryable: false,
                        });

                        console.error(
                            "[OfflineSync] Permanent server rejection:",
                            url,
                            response.status,
                            error
                        );

                        continue;
                    }

                    /*
                     * Temporary server failure.
                     */
                    await bumpAttempt(
                        action.id,
                        error
                    );

                    result.failed.push({
                        action,
                        error,
                        status: response.status,
                        retryable: true,
                    });

                    console.warn(
                        "[OfflineSync] Retryable failure:",
                        url,
                        response.status,
                        error
                    );

                    /*
                     * Stop here so later mutations don't execute
                     * against an uncertain server state.
                     */
                    break;
                }

                /*
                 * SUCCESS
                 */
                await removePendingAction(
                    action.id
                );

                result.succeeded.push(action);

                console.log(
                    "[OfflineSync] Successfully synced:",
                    action.method,
                    url
                );

            } catch (error) {

                const message =
                    error instanceof Error
                        ? error.message
                        : "Network error";

                /*
                 * A fetch TypeError is commonly a network failure,
                 * which is retryable.
                 */
                await bumpAttempt(
                    action.id,
                    message
                );

                result.failed.push({
                    action,
                    error: message,
                    retryable: true,
                });

                console.warn(
                    "[OfflineSync] Network failure:",
                    url,
                    message
                );

                /*
                 * Do not execute later mutations if the connection
                 * or server cannot be reached.
                 */
                break;
            }
        }

        return result;

    } finally {

        syncInProgress = false;
    }
}
