'use client';

import { useEffect, useState } from 'react';

import { webhookApi } from '@/services/api';
import { WebhookEndpoint } from '@/types';

import {
  Card,
  CardHeader,
  CardBody,
} from '@/components/ui/Card';

import { Button } from '@/components/ui/Button';

import {
  Table,
  Thead,
  Th,
  Tbody,
  Tr,
  Td,
} from '@/components/ui/Table';

import { Modal } from '@/components/ui/Modal';

import {
  FormGroup,
  Input,
  Alert,
} from '@/components/ui/Form';


const ALL_EVENTS = [
  'LOAN_CREATED',
  'LOAN_APPROVED',
  'LOAN_REJECTED',
  'LOAN_DISBURSED',
  'PAYMENT_MADE',
  'LOAN_OVERDUE',
  'LOAN_DEFAULTED',
];


export default function WebhooksPage() {

  // ================================================================
  // STATE
  // ================================================================

  const [webhooks, setWebhooks] =
    useState<WebhookEndpoint[]>([]);


  const [loading, setLoading] =
    useState(true);


  const [refreshing, setRefreshing] =
    useState(false);


  const [addOpen, setAddOpen] =
    useState(false);


  const [saving, setSaving] =
    useState(false);


  const [msg, setMsg] =
    useState('');


  const [lastRefresh, setLastRefresh] =
    useState<Date | null>(null);


  const [form, setForm] =
    useState({
      url: '',
      description: '',
      subscribedEvents: [...ALL_EVENTS],
    });


  // ================================================================
  // LOAD WEBHOOKS
  // ================================================================

  const load = async (
    showSpinner = true
  ) => {

    try {

      if (showSpinner) {
        setLoading(true);
      } else {
        setRefreshing(true);
      }


      const response =
        await webhookApi.list();


      const data =
        Array.isArray(response)
          ? response
          : [];


      setWebhooks(data);


      setLastRefresh(
        new Date()
      );

    } catch (error) {

      console.error(
        'Failed to load webhooks:',
        error
      );

    } finally {

      setLoading(false);
      setRefreshing(false);
    }
  };


  // ================================================================
  // INITIAL LOAD
  // ================================================================

  useEffect(() => {

    load(true);

  }, []);


  // ================================================================
  // AUTO REFRESH
  // ================================================================

  useEffect(() => {

    /*
     * Refresh every 10 seconds.
     *
     * This means after a borrower makes a payment, the dashboard
     * can update without manually refreshing the whole browser.
     */
    const interval =
      window.setInterval(
        () => {
          load(false);
        },
        10000
      );


    return () => {
      window.clearInterval(
        interval
      );
    };

  }, []);


  // ================================================================
  // ADD WEBHOOK
  // ================================================================

  const handleAdd =
    async (
      e: React.FormEvent
    ) => {

      e.preventDefault();

      setSaving(true);

      setMsg('');


      try {

        await webhookApi.create(
          form
        );


        setAddOpen(
          false
        );


        setForm({
          url: '',
          description: '',
          subscribedEvents: [
            ...ALL_EVENTS,
          ],
        });


        await load(false);

      } catch (error: any) {

        console.error(
          'Failed to create webhook:',
          error
        );


        setMsg(
          error?.message
            ?? 'Failed to create webhook endpoint'
        );

      } finally {

        setSaving(false);
      }
    };


  // ================================================================
  // DELETE WEBHOOK
  // ================================================================

  const handleDelete =
    async (
      id: number
    ) => {

      if (
        !confirm(
          'Delete this webhook endpoint?'
        )
      ) {
        return;
      }


      try {

        await webhookApi.remove(
          id
        );


        await load(false);

      } catch (error) {

        console.error(
          'Failed to delete webhook:',
          error
        );

        alert(
          'Failed to delete webhook endpoint.'
        );
      }
    };


  // ================================================================
  // TOGGLE EVENT
  // ================================================================

  const toggleEvent =
    (
      event: string
    ) => {

      setForm(
        current => ({

          ...current,

          subscribedEvents:
            current.subscribedEvents.includes(
              event
            )

              ? current.subscribedEvents.filter(
                  existing =>
                    existing !== event
                )

              : [
                  ...current.subscribedEvents,
                  event,
                ],
        })
      );
    };


  // ================================================================
  // FORMAT DATE
  // ================================================================

  const formatDate =
    (
      value: any
    ) => {

      if (!value) {
        return '—';
      }


      try {

        return new Date(
          value
        ).toLocaleString();

      } catch {

        return '—';
      }
    };


  // ================================================================
  // STATUS DISPLAY
  // ================================================================

  const renderDeliveryStatus =
    (
      webhook: WebhookEndpoint
    ) => {

      if (
        !webhook.lastDeliveryStatus
      ) {

        return (
          <span className="text-xs text-gray-400">
            No delivery yet
          </span>
        );
      }


      const status =
        webhook.lastDeliveryStatus;


      const success =
        status === 'SUCCESS';


      return (
        <div className="flex flex-col gap-1">

          <span
            className={
              `inline-flex w-fit items-center gap-1 text-[10px] font-semibold px-2 py-0.5 rounded-full ${
                success
                  ? 'bg-green-100 text-green-700'
                  : 'bg-red-100 text-red-600'
              }`
            }
          >

            {success
              ? '● SUCCESS'
              : '● FAILED'}

          </span>


          {!success && (
            <span
              className="text-[10px] text-red-400 max-w-[220px] break-words"
            >
              {status}
            </span>
          )}

        </div>
      );
    };


  // ================================================================
  // PAGE
  // ================================================================

  return (

    <div>

      {/* ============================================================
          HEADER
          ============================================================ */}

      <div className="flex items-start justify-between mb-6">

        <div>

          <h1 className="text-2xl font-extrabold text-gray-900">
            Webhook Endpoints
          </h1>


          <p className="text-sm text-gray-500 mt-0.5">
            Real-time event delivery to your systems via
            HMAC-SHA256 signed POST requests
          </p>


          {lastRefresh && (
            <p className="text-[11px] text-gray-400 mt-1">
              Last refreshed:{' '}
              {lastRefresh.toLocaleTimeString()}
            </p>
          )}

        </div>


        <div className="flex items-center gap-2">

          <Button
            variant="secondary"
            loading={refreshing}
            onClick={() => load(false)}
          >
            ↻ Refresh
          </Button>


          <Button
            icon="+"
            onClick={() => {

              setMsg('');

              setAddOpen(true);

            }}
          >
            Add Endpoint
          </Button>

        </div>

      </div>


      {/* ============================================================
          INFO CARD
          ============================================================ */}

      <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 mb-5 text-sm text-blue-700">

        <strong>
          How it works:
        </strong>{' '}

        When events occur, such as a borrower payment,
        loan approval, or loan disbursement, the system sends
        a POST request to your registered endpoint.


        <div className="mt-2">

          The request contains:

          <ul className="list-disc ml-5 mt-1">

            <li>
              Event name such as
              <code className="ml-1 bg-blue-100 px-1 rounded">
                PAYMENT_MADE
              </code>
            </li>

            <li>
              Organization ID
            </li>

            <li>
              Event timestamp
            </li>

            <li>
              Event data
            </li>

            <li>
              HMAC-SHA256 signature
            </li>

          </ul>

        </div>

      </div>


      {/* ============================================================
          IMPORTANT PAYMENT WEBHOOK INFORMATION
          ============================================================ */}

      <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 mb-5 text-sm text-amber-800">

        <div className="font-semibold mb-1">
          Payment webhook monitoring
        </div>


        <div>

          When a borrower pays, the payment service dispatches:

          <code className="ml-1 bg-amber-100 px-1.5 py-0.5 rounded font-mono text-xs">
            PAYMENT_MADE
          </code>

        </div>


        <div className="mt-1 text-xs text-amber-700">

          The endpoint below must be subscribed to
          <strong className="ml-1">
            PAYMENT_MADE
          </strong>
          for the delivery to occur.

        </div>

      </div>


      {/* ============================================================
          WEBHOOK TABLE
          ============================================================ */}

      <Card>

        {loading ? (

          <div className="flex items-center justify-center py-12">

            <div className="w-7 h-7 border-2 border-teal-500 border-t-transparent rounded-full animate-spin" />

          </div>

        ) : webhooks.length === 0 ? (

          <div className="py-14 text-center">

            <div className="text-5xl mb-3">
              🔗
            </div>


            <div className="font-semibold text-gray-700">
              No webhooks configured
            </div>


            <div className="text-sm text-gray-400 mt-1">
              Add an endpoint to receive real-time events.
            </div>


            <div className="mt-4">

              <Button
                icon="+"
                onClick={() => setAddOpen(true)}
              >
                Add Webhook Endpoint
              </Button>

            </div>

          </div>

        ) : (

          <Table>

            <Thead>

              <tr>

                <Th>
                  URL
                </Th>

                <Th>
                  Description
                </Th>

                <Th>
                  Events
                </Th>

                <Th>
                  Endpoint
                </Th>

                <Th>
                  Last Delivery
                </Th>

                <Th>
                  Status
                </Th>

                <Th>
                  Failures
                </Th>

                <Th>
                  {null}
                </Th>

              </tr>

            </Thead>


            <Tbody>

              {webhooks.map(
                (
                  webhook: WebhookEndpoint
                ) => (

                  <Tr
                    key={
                      webhook.id
                    }
                  >

                    {/* ==================================================
                        URL
                        ================================================== */}

                    <Td>

                      <code className="text-xs bg-gray-100 px-2 py-1 rounded font-mono break-all">
                        {webhook.url}
                      </code>

                    </Td>


                    {/* ==================================================
                        DESCRIPTION
                        ================================================== */}

                    <Td className="text-sm text-gray-600">

                      {webhook.description
                        ?? '—'}

                    </Td>


                    {/* ==================================================
                        EVENTS
                        ================================================== */}

                    <Td>

                      <div className="flex flex-wrap gap-1">

                        {(
                          webhook.subscribedEvents
                          ?? []
                        )
                          .slice(
                            0,
                            3
                          )
                          .map(
                            (
                              event
                            ) => (

                              <span
                                key={event}
                                className="text-[10px] bg-gray-100 text-gray-600 px-1.5 py-0.5 rounded font-mono"
                              >

                                {event.replace(
                                  /_/g,
                                  ' '
                                )}

                              </span>

                            )
                          )}


                        {(
                          webhook.subscribedEvents
                          ?? []
                        ).length > 3 && (

                          <span className="text-[10px] text-gray-400">

                            +
                            {(
                              webhook.subscribedEvents
                              ?? []
                            ).length - 3}

                          </span>

                        )}

                      </div>


                      {(
                        webhook.subscribedEvents
                        ?? []
                      ).includes(
                        'PAYMENT_MADE'
                      ) && (

                        <div className="text-[9px] text-green-600 mt-1 font-semibold">
                          ✓ PAYMENT_MADE enabled
                        </div>

                      )}

                    </Td>


                    {/* ==================================================
                        ACTIVE STATUS
                        ================================================== */}

                    <Td>

                      <span
                        className={
                          `inline-flex items-center gap-1 text-xs font-semibold px-2 py-0.5 rounded-full ${
                            webhook.active
                              ? 'bg-green-100 text-green-700'
                              : 'bg-red-100 text-red-600'
                          }`
                        }
                      >

                        {webhook.active
                          ? '● Active'
                          : '● Disabled'}

                      </span>

                    </Td>


                    {/* ==================================================
                        LAST DELIVERY
                        ================================================== */}

                    <Td className="text-xs text-gray-500">

                      {formatDate(
                        webhook.lastDeliveryAt
                      )}

                    </Td>


                    {/* ==================================================
                        DELIVERY STATUS
                        ================================================== */}

                    <Td>

                      {renderDeliveryStatus(
                        webhook
                      )}

                    </Td>


                    {/* ==================================================
                        FAILURE COUNT
                        ================================================== */}

                    <Td
                      className={
                        `font-semibold ${
                          (
                            webhook.failureCount
                            ?? 0
                          ) > 0
                            ? 'text-red-500'
                            : 'text-gray-400'
                        }`
                      }
                    >

                      {
                        webhook.failureCount
                        ?? 0
                      }

                    </Td>


                    {/* ==================================================
                        DELETE
                        ================================================== */}

                    <Td>

                      <Button
                        variant="ghost"
                        size="xs"
                        onClick={() =>
                          handleDelete(
                            webhook.id!
                          )
                        }
                      >
                        🗑
                      </Button>

                    </Td>

                  </Tr>

                )
              )}

            </Tbody>

          </Table>

        )}

      </Card>


      {/* ============================================================
          ADD WEBHOOK MODAL
          ============================================================ */}

      <Modal
        open={addOpen}
        onClose={() => {

          if (!saving) {
            setAddOpen(false);
          }

        }}
        title="Add Webhook Endpoint"

        footer={

          <>

            <Button
              variant="secondary"
              onClick={() => {

                if (!saving) {
                  setAddOpen(false);
                }

              }}
            >
              Cancel
            </Button>


            <Button
              loading={saving}
              onClick={
                handleAdd as any
              }
            >
              Save Endpoint
            </Button>

          </>

        }
      >

        <form
          onSubmit={handleAdd}
        >

          {/* ==========================================================
              ERROR
              ========================================================== */}

          {msg && (
            <Alert type="error">
              {msg}
            </Alert>
          )}


          {/* ==========================================================
              URL
              ========================================================== */}

          <FormGroup
            label="Endpoint URL"
            required
          >

            <Input
              type="url"
              required
              placeholder="https://yourapp.com/webhooks/loansaas"
              value={form.url}
              onChange={
                e =>
                  setForm(
                    current => ({
                      ...current,
                      url:
                        e.target.value,
                    })
                  )
              }
            />

          </FormGroup>


          {/* ==========================================================
              DESCRIPTION
              ========================================================== */}

          <FormGroup
            label="Description"
          >

            <Input
              placeholder="e.g. CRM integration, Core banking sync"
              value={
                form.description
              }
              onChange={
                e =>
                  setForm(
                    current => ({
                      ...current,
                      description:
                        e.target.value,
                    })
                  )
              }
            />

          </FormGroup>


          {/* ==========================================================
              EVENTS
              ========================================================== */}

          <FormGroup
            label="Subscribed Events"
          >

            <div className="grid grid-cols-2 gap-2 mt-1">

              {ALL_EVENTS.map(
                event => (

                  <label
                    key={event}
                    className="flex items-center gap-2 cursor-pointer text-sm"
                  >

                    <input
                      type="checkbox"
                      checked={
                        form.subscribedEvents.includes(
                          event
                        )
                      }
                      onChange={() =>
                        toggleEvent(
                          event
                        )
                      }
                      className="w-4 h-4 text-teal-600 rounded border-gray-300 focus:ring-teal-500"
                    />


                    <span className="text-gray-700 font-mono text-xs">

                      {event.replace(
                        /_/g,
                        ' '
                      )}

                    </span>

                  </label>

                )
              )}

            </div>

          </FormGroup>


          {/* ==========================================================
              PAYMENT EVENT WARNING
              ========================================================== */}

          {!form.subscribedEvents.includes(
            'PAYMENT_MADE'
          ) && (

            <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-xs text-red-700 mt-2">

              ⚠️

              <strong className="ml-1">
                PAYMENT_MADE is not selected.
              </strong>

              <div className="mt-1">
                This endpoint will not receive borrower payment
                webhook events.
              </div>

            </div>

          )}


          {/* ==========================================================
              SECRET INFORMATION
              ========================================================== */}

          <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-3 text-xs text-yellow-700 mt-2">

            🔑

            A signing secret will be generated automatically.

            Use the secret to verify the
            <code className="mx-1 bg-yellow-100 px-1 rounded">
              X-Webhook-Signature
            </code>
            header in your receiving system.

          </div>

        </form>

      </Modal>

    </div>
  );
}