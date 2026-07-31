import {
    getPendingActions,
    removePendingAction,
    bumpAttempt,
    PendingAction
} from "./offlineDb";

const API_BASE =
    process.env.NEXT_PUBLIC_API_URL ||
    "http://localhost:8080/api";

export interface SyncResult {
    succeeded: PendingAction[];
    failed: {
        action: PendingAction;
        error: string;
    }[];
}

export async function drainOfflineQueue(
    authHeader: () => Record<string, string>
): Promise<SyncResult> {

    const actions = await getPendingActions();

    const result: SyncResult = {
        succeeded: [],
        failed: [],
    };

    for (const action of actions) {

        if (action.attempts >= 5) {
            console.warn("Skipping permanently failed action", action.id);
            continue;
        }

        try {

            const response = await fetch(
                `${API_BASE}${action.url}`,
                {
                    method: action.method,
                    headers: {
                        "Content-Type": "application/json",
                        "Idempotency-Key": action.id,
                        ...authHeader(),
                    },
                    body: action.body
                        ? JSON.stringify(action.body)
                        : undefined,
                }
            );

            if (!response.ok) {

                const text = await response.text();

                throw new Error(
                    text || `HTTP ${response.status}`
                );
            }

            await removePendingAction(action.id);

            result.succeeded.push(action);

            console.log(
                "Synced:",
                action.url
            );

        } catch (e) {

            const error =
                e instanceof Error
                    ? e.message
                    : "Unknown error";

            await bumpAttempt(action.id, error);

            result.failed.push({
                action,
                error,
            });

            console.error(
                "Sync failed:",
                action.url,
                error
            );
        }
    }

    return result;
}