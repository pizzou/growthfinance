
'use client';

import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';

import {
  getCollectionsQueue,
  getCollectionsStats,
  logCollectionAction,
  syncCollectionsQueue,
  CollectionCase,
  CollectionStats,
  CollectionBucket,
} from '../../../services/collectionsService';

import { PageSpinner } from '../../../components/ui/Skeleton';
import { Pill } from '../../../components/ui/Badge';
import { toast } from '../../../hooks/useToast';

const BUCKET_LABEL: Record<CollectionBucket, string> = {
  CURRENT: 'Current',
  DPD_1_30: '1-30 DPD',
  DPD_31_60: '31-60 DPD',
  DPD_61_90: '61-90 DPD',
  DPD_90_PLUS: '90+ DPD',
  WRITE_OFF: 'Written Off',
};

const BUCKET_COLOR: Record<CollectionBucket, string> = {
  CURRENT: 'green',
  DPD_1_30: 'yellow',
  DPD_31_60: 'yellow',
  DPD_61_90: 'red',
  DPD_90_PLUS: 'red',
  WRITE_OFF: 'gray',
};

const PRIORITY_COLOR: Record<string, string> = {
  LOW: 'gray',
  MEDIUM: 'blue',
  HIGH: 'yellow',
  URGENT: 'red',
};

type CollectionActionType =
  | 'CALL'
  | 'PROMISE_TO_PAY'
  | 'ESCALATED'
  | 'FIELD_VISIT';

interface ActionFormState {
  notes: string;
  promiseDate: string;
  promiseAmount: string;
}

const INITIAL_ACTION_FORM: ActionFormState = {
  notes: '',
  promiseDate: '',
  promiseAmount: '',
};

function getErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }

  if (
    typeof error === 'object' &&
    error !== null &&
    'message' in error &&
    typeof (error as { message?: unknown }).message === 'string'
  ) {
    const message = (error as { message: string }).message;

    if (message.trim()) {
      return message;
    }
  }

  return 'Something went wrong. Please try again.';
}

function formatMoney(
  value: unknown,
  currency?: string | null,
): string {
  if (value === null || value === undefined || value === '') {
    return currency ? `${currency} 0` : '0';
  }

  const numericValue =
    typeof value === 'number'
      ? value
      : Number(value);

  if (!Number.isFinite(numericValue)) {
    return currency ? `${currency} 0` : '0';
  }

  const formatted = numericValue.toLocaleString(undefined, {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  });

  return currency
    ? `${currency} ${formatted}`
    : formatted;
}

function getBorrowerName(collectionCase: CollectionCase): string {
  const firstName =
    collectionCase.loan?.borrower?.firstName?.trim() ?? '';

  const lastName =
    collectionCase.loan?.borrower?.lastName?.trim() ?? '';

  const fullName =
    `${firstName} ${lastName}`.trim();

  return fullName || 'Unknown borrower';
}

function getStatusLabel(status: string | undefined): string {
  if (!status) {
    return 'Unknown';
  }

  return status
    .replace(/_/g, ' ')
    .toLowerCase()
    .replace(/\b\w/g, (character) =>
      character.toUpperCase(),
    );
}

function isValidPromiseAmount(value: string): boolean {
  const normalized = value.trim();

  if (!normalized) {
    return false;
  }

  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) {
    return false;
  }

  const amount = Number(normalized);

  return Number.isFinite(amount) && amount > 0;
}

function isValidPromiseDate(value: string): boolean {
  if (!value) {
    return false;
  }

  const selectedDate = new Date(`${value}T00:00:00`);

  if (Number.isNaN(selectedDate.getTime())) {
    return false;
  }

  const today = new Date();

  today.setHours(0, 0, 0, 0);

  return selectedDate >= today;
}

export default function CollectionsPage() {
  const [cases, setCases] = useState<CollectionCase[]>([]);
  const [stats, setStats] =
    useState<CollectionStats | null>(null);

  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [bucketFilter, setBucketFilter] =
    useState<CollectionBucket | ''>('');

  const [activeCase, setActiveCase] =
    useState<CollectionCase | null>(null);

  const [actionForm, setActionForm] =
    useState<ActionFormState>(
      INITIAL_ACTION_FORM,
    );

  const [busy, setBusy] = useState(false);
  const [syncing, setSyncing] = useState(false);

  const modalRef =
    useRef<HTMLDivElement | null>(null);

  const load = useCallback(
    async (showInitialLoader = false) => {
      if (showInitialLoader) {
        setLoading(true);
      } else {
        setRefreshing(true);
      }

      try {
        const [queue, collectionStats] =
          await Promise.all([
            getCollectionsQueue(
              bucketFilter
                ? { bucket: bucketFilter }
                : undefined,
            ),
            getCollectionsStats(),
          ]);

        setCases(
          Array.isArray(queue)
            ? queue.filter(Boolean)
            : [],
        );

        setStats(collectionStats ?? null);
      } catch (error) {
        toast(
          'error',
          getErrorMessage(error),
        );
      } finally {
        if (showInitialLoader) {
          setLoading(false);
        } else {
          setRefreshing(false);
        }
      }
    },
    [bucketFilter],
  );

  useEffect(() => {
    void load(true);
  }, [load]);

  useEffect(() => {
    if (!activeCase) {
      return;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busy) {
        closeActionModal();
      }
    };

    document.addEventListener(
      'keydown',
      handleKeyDown,
    );

    const previousActiveElement =
      document.activeElement as HTMLElement | null;

    window.setTimeout(() => {
      modalRef.current?.focus();
    }, 0);

    return () => {
      document.removeEventListener(
        'keydown',
        handleKeyDown,
      );

      previousActiveElement?.focus?.();
    };
  }, [activeCase, busy]);

  function closeActionModal() {
    if (busy) {
      return;
    }

    setActiveCase(null);
    setActionForm(INITIAL_ACTION_FORM);
  }

  function updateActionForm(
    field: keyof ActionFormState,
    value: string,
  ) {
    setActionForm((current) => ({
      ...current,
      [field]: value,
    }));
  }

  async function handleSync() {
    if (syncing || refreshing || loading) {
      return;
    }

    setSyncing(true);

    try {
      const result =
        await syncCollectionsQueue();

      toast(
        'success',
        typeof result === 'string'
          ? result
          : 'Collection queue synchronized successfully.',
      );

      await load(false);
    } catch (error) {
      toast(
        'error',
        getErrorMessage(error),
      );
    } finally {
      setSyncing(false);
    }
  }

  async function handleAction(
    type: CollectionActionType,
  ) {
    if (!activeCase || busy) {
      return;
    }

    if (!activeCase.id) {
      toast(
        'error',
        'The selected collection case is invalid.',
      );
      return;
    }

    const notes =
      actionForm.notes.trim();

    if (type === 'PROMISE_TO_PAY') {
      if (
        !isValidPromiseDate(
          actionForm.promiseDate,
        )
      ) {
        toast(
          'error',
          'Please select a valid promise-to-pay date.',
        );
        return;
      }

      if (
        !isValidPromiseAmount(
          actionForm.promiseAmount,
        )
      ) {
        toast(
          'error',
          'Please enter a valid promise amount greater than zero.',
        );
        return;
      }
    }

    setBusy(true);

    try {
      await logCollectionAction(
        activeCase.id,
        {
          actionType: type,
          notes: notes || undefined,

          promiseDate:
            type === 'PROMISE_TO_PAY'
              ? actionForm.promiseDate
              : undefined,

          /*
           * Keep the existing collectionsService
           * contract here.
           *
           * The backend should ultimately persist
           * this value as BigDecimal/NUMERIC rather
           * than Double.
           */
          promiseAmount:
            type === 'PROMISE_TO_PAY'
              ? Number(
                  actionForm.promiseAmount,
                )
              : undefined,
        },
      );

      toast(
        'success',
        'Collection action logged successfully.',
      );

      setActiveCase(null);
      setActionForm(
        INITIAL_ACTION_FORM,
      );

      await load(false);
    } catch (error) {
      toast(
        'error',
        getErrorMessage(error),
      );
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return <PageSpinner />;
  }

  return (
    <div className="space-y-6">
      {/* =====================================================
          HEADER
      ====================================================== */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">
            Collections
          </h1>

          <p className="mt-1 text-sm text-gray-500">
            Delinquency queue, aging buckets and contact history
          </p>
        </div>

        <button
          type="button"
          onClick={() => void handleSync()}
          disabled={
            syncing ||
            refreshing ||
            loading
          }
          className="inline-flex items-center justify-center rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-semibold text-gray-700 shadow-sm transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {syncing
            ? 'Syncing…'
            : refreshing
              ? 'Refreshing…'
              : '↻ Sync Overdue Loans'}
        </button>
      </div>

      {/* =====================================================
          STATS
      ====================================================== */}
      {stats && (
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
          <div className="rounded-xl border border-gray-200 bg-white p-4">
            <p className="text-xs text-gray-400">
              Open Cases
            </p>

            <p className="text-2xl font-bold text-gray-900">
              {stats.totalOpenCases ?? 0}
            </p>
          </div>

          <div className="rounded-xl border border-gray-200 bg-white p-4">
            <p className="text-xs text-gray-400">
              Total Overdue
            </p>

            <p className="text-2xl font-bold text-red-600">
              {formatMoney(
                stats.totalOverdueAmount,
              )}
            </p>
          </div>

          <div className="rounded-xl border border-gray-200 bg-white p-4">
            <p className="text-xs text-gray-400">
              Active Promises
            </p>

            <p className="text-2xl font-bold text-blue-600">
              {stats.activePromises ?? 0}
            </p>
          </div>

          <div className="rounded-xl border border-gray-200 bg-white p-4">
            <p className="text-xs text-gray-400">
              90+ DPD Cases
            </p>

            <p className="text-2xl font-bold text-gray-900">
              {stats.casesByBucket?.DPD_90_PLUS ?? 0}
            </p>
          </div>
        </div>
      )}

      {/* =====================================================
          BUCKET FILTERS
      ====================================================== */}
      <div
        className="flex flex-wrap gap-2"
        aria-label="Collection bucket filters"
      >
        <button
          type="button"
          onClick={() =>
            setBucketFilter('')
          }
          className={`rounded-full border px-3 py-1.5 text-xs font-semibold ${
            !bucketFilter
              ? 'border-gray-900 bg-gray-900 text-white'
              : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50'
          }`}
          aria-pressed={!bucketFilter}
        >
          All
        </button>

        {(
          Object.keys(
            BUCKET_LABEL,
          ) as CollectionBucket[]
        ).map((bucket) => (
          <button
            key={bucket}
            type="button"
            onClick={() =>
              setBucketFilter(bucket)
            }
            className={`rounded-full border px-3 py-1.5 text-xs font-semibold ${
              bucketFilter === bucket
                ? 'border-gray-900 bg-gray-900 text-white'
                : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50'
            }`}
            aria-pressed={
              bucketFilter === bucket
            }
          >
            {BUCKET_LABEL[bucket]}

            {stats
              ? ` (${
                  stats.casesByBucket?.[
                    bucket
                  ] ?? 0
                })`
              : ''}
          </button>
        ))}
      </div>

      {/* =====================================================
          QUEUE
      ====================================================== */}
      {cases.length === 0 ? (
        <div className="rounded-xl border border-gray-200 bg-white p-16 text-center">
          <p
            className="mb-3 text-3xl"
            aria-hidden="true"
          >
            ✅
          </p>

          <p className="font-medium text-gray-500">
            No delinquent cases in this bucket.
          </p>
        </div>
      ) : (
        <div className="overflow-hidden rounded-xl border border-gray-200 bg-white">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[1000px] text-sm">
              <caption className="sr-only">
                Collection delinquency queue
              </caption>

              <thead className="bg-gray-50 text-xs uppercase text-gray-500">
                <tr>
                  <th
                    scope="col"
                    className="px-4 py-3 text-left"
                  >
                    Borrower
                  </th>

                  <th
                    scope="col"
                    className="px-4 py-3 text-left"
                  >
                    Loan Ref
                  </th>

                  <th
                    scope="col"
                    className="px-4 py-3 text-left"
                  >
                    Bucket
                  </th>

                  <th
                    scope="col"
                    className="px-4 py-3 text-left"
                  >
                    Priority
                  </th>

                  <th
                    scope="col"
                    className="px-4 py-3 text-right"
                  >
                    Overdue Amount
                  </th>

                  <th
                    scope="col"
                    className="px-4 py-3 text-left"
                  >
                    Agent
                  </th>

                  <th
                    scope="col"
                    className="px-4 py-3 text-left"
                  >
                    Status
                  </th>

                  <th
                    scope="col"
                    className="px-4 py-3 text-right"
                  >
                    Actions
                  </th>
                </tr>
              </thead>

              <tbody className="divide-y divide-gray-100">
                {cases.map((collectionCase) => {
                  const borrowerName =
                    getBorrowerName(
                      collectionCase,
                    );

                  return (
                    <tr
                      key={collectionCase.id}
                      className="hover:bg-gray-50"
                    >
                      <td className="px-4 py-3 font-medium text-gray-800">
                        {borrowerName}

                        {collectionCase.loan
                          ?.borrower?.phone && (
                          <div className="text-xs text-gray-400">
                            {
                              collectionCase
                                .loan
                                .borrower
                                .phone
                            }
                          </div>
                        )}
                      </td>

                      <td className="px-4 py-3 text-gray-600">
                        {collectionCase.loan
                          ?.referenceNumber ??
                          '—'}
                      </td>

                      <td className="px-4 py-3">
                        <Pill
                          label={
                            BUCKET_LABEL[
                              collectionCase
                                .bucket
                            ] ??
                            collectionCase.bucket
                          }
                          color={
                            BUCKET_COLOR[
                              collectionCase
                                .bucket
                            ] ?? 'gray'
                          }
                        />
                      </td>

                      <td className="px-4 py-3">
                        <Pill
                          label={
                            collectionCase.priority ??
                            'UNKNOWN'
                          }
                          color={
                            PRIORITY_COLOR[
                              collectionCase
                                .priority
                            ] ?? 'gray'
                          }
                        />
                      </td>

                      <td className="px-4 py-3 text-right font-semibold text-gray-800">
                        {formatMoney(
                          collectionCase.overdueAmount,
                          collectionCase.loan
                            ?.currency,
                        )}
                      </td>

                      <td className="px-4 py-3 text-gray-600">
                        {collectionCase
                          .assignedAgent
                          ?.name ?? '—'}
                      </td>

                      <td className="px-4 py-3">
                        <Pill
                          label={getStatusLabel(
                            collectionCase.status,
                          )}
                          color="gray"
                        />
                      </td>

                      <td className="px-4 py-3 text-right">
                        <button
                          type="button"
                          onClick={() => {
                            setActionForm(
                              INITIAL_ACTION_FORM,
                            );
                            setActiveCase(
                              collectionCase,
                            );
                          }}
                          disabled={busy}
                          className="text-xs font-semibold text-teal-600 hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          Log Action
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* =====================================================
          ACTION MODAL
      ====================================================== */}
      {activeCase && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="presentation"
          onMouseDown={(event) => {
            if (
              event.target ===
                event.currentTarget &&
              !busy
            ) {
              closeActionModal();
            }
          }}
        >
          <div
            ref={modalRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby="collection-action-title"
            tabIndex={-1}
            className="w-full max-w-md space-y-4 rounded-xl bg-white p-6 shadow-xl outline-none"
            onMouseDown={(event) =>
              event.stopPropagation()
            }
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <h2
                  id="collection-action-title"
                  className="font-bold text-gray-900"
                >
                  Log Collection Action
                </h2>

                <p className="mt-1 text-sm text-gray-500">
                  {getBorrowerName(
                    activeCase,
                  )}
                </p>

                {activeCase.loan
                  ?.referenceNumber && (
                  <p className="text-xs text-gray-400">
                    Loan:{' '}
                    {
                      activeCase.loan
                        .referenceNumber
                    }
                  </p>
                )}
              </div>

              <button
                type="button"
                onClick={closeActionModal}
                disabled={busy}
                aria-label="Close dialog"
                className="rounded-md px-2 py-1 text-gray-400 hover:bg-gray-100 hover:text-gray-700 disabled:opacity-50"
              >
                ✕
              </button>
            </div>

            <div>
              <label
                htmlFor="collection-notes"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                Notes
              </label>

              <textarea
                id="collection-notes"
                value={actionForm.notes}
                onChange={(event) =>
                  updateActionForm(
                    'notes',
                    event.target.value,
                  )
                }
                placeholder="Add collection notes..."
                rows={3}
                maxLength={2000}
                disabled={busy}
                className="w-full resize-none rounded-lg border border-gray-300 p-3 text-sm outline-none focus:border-teal-500 focus:ring-1 focus:ring-teal-500 disabled:bg-gray-100"
              />

              <p className="mt-1 text-right text-xs text-gray-400">
                {actionForm.notes.length}/2000
              </p>
            </div>

            {/* Promise fields are shown only when
                the user selects Promise to Pay. */}
            <div className="rounded-lg border border-gray-200 bg-gray-50 p-3">
              <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">
                Promise to Pay
              </p>

              <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div>
                  <label
                    htmlFor="promise-date"
                    className="mb-1 block text-xs font-medium text-gray-600"
                  >
                    Promise date
                  </label>

                  <input
                    id="promise-date"
                    type="date"
                    value={
                      actionForm.promiseDate
                    }
                    onChange={(event) =>
                      updateActionForm(
                        'promiseDate',
                        event.target.value,
                      )
                    }
                    disabled={busy}
                    min={
                      new Date()
                        .toISOString()
                        .slice(0, 10)
                    }
                    className="w-full rounded-lg border border-gray-300 bg-white p-2 text-sm outline-none focus:border-teal-500 focus:ring-1 focus:ring-teal-500 disabled:bg-gray-100"
                  />
                </div>

                <div>
                  <label
                    htmlFor="promise-amount"
                    className="mb-1 block text-xs font-medium text-gray-600"
                  >
                    Promise amount
                  </label>

                  <input
                    id="promise-amount"
                    type="number"
                    inputMode="decimal"
                    min="0.01"
                    step="0.01"
                    value={
                      actionForm.promiseAmount
                    }
                    onChange={(event) =>
                      updateActionForm(
                        'promiseAmount',
                        event.target.value,
                      )
                    }
                    disabled={busy}
                    placeholder="0.00"
                    className="w-full rounded-lg border border-gray-300 bg-white p-2 text-sm outline-none focus:border-teal-500 focus:ring-1 focus:ring-teal-500 disabled:bg-gray-100"
                  />
                </div>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                disabled={busy}
                onClick={() =>
                  void handleAction('CALL')
                }
                className="rounded-lg border border-gray-300 py-2 text-sm font-medium transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
              >
                📞 Called
              </button>

              <button
                type="button"
                disabled={busy}
                onClick={() =>
                  void handleAction(
                    'FIELD_VISIT',
                  )
                }
                className="rounded-lg border border-gray-300 py-2 text-sm font-medium transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
              >
                🚶 Field Visit
              </button>

              <button
                type="button"
                disabled={
                  busy ||
                  !isValidPromiseDate(
                    actionForm.promiseDate,
                  ) ||
                  !isValidPromiseAmount(
                    actionForm.promiseAmount,
                  )
                }
                onClick={() =>
                  void handleAction(
                    'PROMISE_TO_PAY',
                  )
                }
                className="rounded-lg bg-blue-600 py-2 text-sm font-medium text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                🤝 Promise to Pay
              </button>

              <button
                type="button"
                disabled={busy}
                onClick={() =>
                  void handleAction(
                    'ESCALATED',
                  )
                }
                className="rounded-lg bg-red-600 py-2 text-sm font-medium text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                ⚠ Escalate
              </button>
            </div>

            <button
              type="button"
              onClick={closeActionModal}
              disabled={busy}
              className="w-full pt-1 text-center text-sm text-gray-500 hover:text-gray-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Cancel
            </button>

            {busy && (
              <p
                className="text-center text-xs text-gray-400"
                role="status"
                aria-live="polite"
              >
                Saving collection action…
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

