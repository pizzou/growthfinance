
const DB_NAME = "loansaas-offline";
const DB_VERSION = 2;

const STORE_QUEUE = "pendingActions";
const STORE_CACHE = "cache";

export interface PendingAction {
    id: string;
    url: string;

    method:
        | "POST"
        | "PUT"
        | "PATCH"
        | "DELETE";

    body?: unknown;

    /**
     * Human-readable description.
     *
     * Example:
     * "Loan application — Jean Uwimana"
     */
    label: string;

    createdAt: string;

    attempts: number;

    lastError?: string;
}

export interface CachedResponse<T = unknown> {
    url: string;
    data: T;
    cachedAt: string;
}

/**
 * ------------------------------------------------------------
 * OPEN DATABASE
 * ------------------------------------------------------------
 */

function openDb(): Promise<IDBDatabase> {
    return new Promise((resolve, reject) => {
        if (typeof window === "undefined") {
            reject(
                new Error(
                    "IndexedDB is unavailable during server-side rendering."
                )
            );
            return;
        }

        if (!("indexedDB" in window)) {
            reject(
                new Error(
                    "IndexedDB is not supported by this browser."
                )
            );
            return;
        }

        const request = indexedDB.open(
            DB_NAME,
            DB_VERSION
        );

        request.onupgradeneeded = () => {
            const db = request.result;

            /**
             * Pending offline mutations.
             */
            if (!db.objectStoreNames.contains(STORE_QUEUE)) {
                db.createObjectStore(
                    STORE_QUEUE,
                    {
                        keyPath: "id",
                    }
                );
            }

            /**
             * Cached GET responses.
             */
            if (!db.objectStoreNames.contains(STORE_CACHE)) {
                db.createObjectStore(
                    STORE_CACHE,
                    {
                        keyPath: "url",
                    }
                );
            }
        };

        request.onsuccess = () => {
            const db = request.result;

            /**
             * If another tab upgrades the database, close this
             * connection so the browser can complete the upgrade.
             */
            db.onversionchange = () => {
                db.close();
            };

            resolve(db);
        };

        request.onerror = () => {
            reject(
                request.error ||
                    new Error(
                        "Failed to open offline database."
                    )
            );
        };

        request.onblocked = () => {
            console.warn(
                "Offline database upgrade is blocked by another tab."
            );
        };
    });
}

/**
 * Generic IndexedDB request helper.
 */
async function withStore<T>(
    storeName: string,
    mode: IDBTransactionMode,
    operation: (
        store: IDBObjectStore
    ) => IDBRequest
): Promise<T> {

    const db = await openDb();

    return new Promise<T>(
        (resolve, reject) => {

            let transaction: IDBTransaction;

            try {
                transaction =
                    db.transaction(
                        storeName,
                        mode
                    );
            } catch (error) {
                db.close();
                reject(error);
                return;
            }

            const store =
                transaction.objectStore(
                    storeName
                );

            let request: IDBRequest;

            try {
                request = operation(store);
            } catch (error) {
                db.close();
                reject(error);
                return;
            }

            request.onsuccess = () => {
                resolve(
                    request.result as T
                );
            };

            request.onerror = () => {
                reject(
                    request.error ||
                        new Error(
                            "IndexedDB request failed."
                        )
                );
            };

            transaction.onabort = () => {
                reject(
                    transaction.error ||
                        new Error(
                            "IndexedDB transaction aborted."
                        )
                );
            };

            transaction.onerror = () => {
                reject(
                    transaction.error ||
                        new Error(
                            "IndexedDB transaction failed."
                        )
                );
            };

            transaction.oncomplete = () => {
                db.close();
            };
        }
    );
}

/**
 * ============================================================
 * PENDING ACTION QUEUE
 * ============================================================
 */

/**
 * Add an offline mutation to the queue.
 */
export async function queueAction(
    action: Omit<
        PendingAction,
        "id" | "createdAt" | "attempts"
    >
): Promise<PendingAction> {

    const id =
        typeof crypto !== "undefined" &&
        typeof crypto.randomUUID === "function"
            ? crypto.randomUUID()
            : `${Date.now()}-${Math.random()
                  .toString(36)
                  .slice(2)}`;

    const fullAction: PendingAction = {
        ...action,

        id,

        createdAt:
            new Date().toISOString(),

        attempts: 0,
    };

    await withStore(
        STORE_QUEUE,
        "readwrite",
        (store) =>
            store.put(fullAction)
    );

    return fullAction;
}

/**
 * Return pending mutations in creation order.
 */
export async function getPendingActions(): Promise<
    PendingAction[]
> {

    try {

        const actions =
            await withStore<
                PendingAction[]
            >(
                STORE_QUEUE,
                "readonly",
                (store) =>
                    store.getAll()
            );

        return (
            actions ?? []
        ).sort(
            (a, b) =>
                a.createdAt.localeCompare(
                    b.createdAt
                )
        );

    } catch (error) {

        console.error(
            "Failed to read offline queue:",
            error
        );

        return [];
    }
}

/**
 * Remove successfully synchronized action.
 */
export async function removePendingAction(
    id: string
): Promise<void> {

    await withStore(
        STORE_QUEUE,
        "readwrite",
        (store) =>
            store.delete(id)
    );
}

/**
 * Increase retry counter and save error.
 */
export async function bumpAttempt(
    id: string,
    error: string
): Promise<void> {

    const db = await openDb();

    await new Promise<void>(
        (resolve, reject) => {

            const transaction =
                db.transaction(
                    STORE_QUEUE,
                    "readwrite"
                );

            const store =
                transaction.objectStore(
                    STORE_QUEUE
                );

            const request =
                store.get(id);

            request.onsuccess = () => {

                const item =
                    request.result as
                        | PendingAction
                        | undefined;

                if (!item) {
                    resolve();
                    return;
                }

                item.attempts =
                    (item.attempts ?? 0) + 1;

                item.lastError =
                    error;

                store.put(item);
            };

            request.onerror = () => {
                reject(
                    request.error
                );
            };

            transaction.oncomplete = () => {
                db.close();
                resolve();
            };

            transaction.onerror = () => {
                db.close();

                reject(
                    transaction.error ||
                        new Error(
                            "Failed to update offline attempt."
                        )
                );
            };

            transaction.onabort = () => {
                db.close();

                reject(
                    transaction.error ||
                        new Error(
                            "Offline attempt transaction aborted."
                        )
                );
            };
        }
    );
}

/**
 * Number of pending mutations.
 */
export async function pendingCount(): Promise<number> {

    try {

        const actions =
            await getPendingActions();

        return actions.length;

    } catch {

        return 0;
    }
}

/**
 * ============================================================
 * GET RESPONSE CACHE
 * ============================================================
 */

/**
 * Save the last successful GET response.
 *
 * The URL should include query parameters.
 *
 * Example:
 *
 * /loans?page=0&size=20
 *
 * and
 *
 * /loans?page=1&size=20
 *
 * are stored separately.
 */
export async function cacheSet<T>(
    url: string,
    data: T
): Promise<void> {

    try {

        const row: CachedResponse<T> = {
            url,
            data,
            cachedAt:
                new Date().toISOString(),
        };

        await withStore(
            STORE_CACHE,
            "readwrite",
            (store) =>
                store.put(row)
        );

    } catch (error) {

        /**
         * Cache failure must NEVER break the actual API request.
         */
        console.warn(
            "Offline cache write failed:",
            error
        );
    }
}

/**
 * Read a cached GET response.
 */
export async function cacheGet<T = unknown>(
    url: string
): Promise<T | null> {

    try {

        const row =
            await withStore<
                CachedResponse<T> | undefined
            >(
                STORE_CACHE,
                "readonly",
                (store) =>
                    store.get(url)
            );

        if (!row) {
            return null;
        }

        return row.data;

    } catch (error) {

        console.warn(
            "Offline cache read failed:",
            error
        );

        return null;
    }
}

/**
 * Check whether a cached response exists.
 */
export async function hasCachedResponse(
    url: string
): Promise<boolean> {

    try {

        const row =
            await withStore<
                CachedResponse | undefined
            >(
                STORE_CACHE,
                "readonly",
                (store) =>
                    store.get(url)
            );

        return !!row;

    } catch {

        return false;
    }
}

/**
 * Delete one cached GET response.
 */
export async function cacheRemove(
    url: string
): Promise<void> {

    try {

        await withStore(
            STORE_CACHE,
            "readwrite",
            (store) =>
                store.delete(url)
        );

    } catch (error) {

        console.warn(
            "Failed to remove cached response:",
            error
        );
    }
}

/**
 * Clear every cached GET response.
 *
 * Useful after logout or organization/account switch.
 */
export async function clearOfflineCache(): Promise<void> {

    try {

        await withStore(
            STORE_CACHE,
            "readwrite",
            (store) =>
                store.clear()
        );

    } catch (error) {

        console.warn(
            "Failed to clear offline cache:",
            error
        );
    }
}

/**
 * Clear pending mutation queue.
 *
 * Use carefully — normally pending actions should be
 * synchronized rather than deleted.
 */
export async function clearPendingActions(): Promise<void> {

    try {

        await withStore(
            STORE_QUEUE,
            "readwrite",
            (store) =>
                store.clear()
        );

    } catch (error) {

        console.warn(
            "Failed to clear pending actions:",
            error
        );
    }
}

/**
 * ============================================================
 * OFFLINE STATUS
 * ============================================================
 */

export function isBrowserOnline(): boolean {

    if (typeof navigator === "undefined") {
        return true;
    }

    return navigator.onLine;
}
