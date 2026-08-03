'use client';

import React, {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';

import {
  regulatoryApi,
  type RegulatoryApiClient,
  type RegulatoryApiClientType,
} from '@/services/regulatoryService';


// ============================================================
// TYPES
// ============================================================

type ClientFilter =
  | 'ALL'
  | 'BNR'
  | 'CREDIT_BUREAU';


// ============================================================
// PAGE
// ============================================================

export default function ApiKeysPage() {

  // ==========================================================
  // DATA
  // ==========================================================

  const [clients, setClients] =
    useState<RegulatoryApiClient[]>([]);


  // ==========================================================
  // UI
  // ==========================================================

  const [loading, setLoading] =
    useState<boolean>(true);

  const [creating, setCreating] =
    useState<boolean>(false);

  const [revokingId, setRevokingId] =
    useState<number | null>(null);

  const [error, setError] =
    useState<string | null>(null);

  const [success, setSuccess] =
    useState<string | null>(null);


  // ==========================================================
  // FILTER
  // ==========================================================

  const [filter, setFilter] =
    useState<ClientFilter>('ALL');


  // ==========================================================
  // CREATE FORM
  // ==========================================================

  const [name, setName] =
    useState<string>('');

  const [clientType, setClientType] =
    useState<RegulatoryApiClientType>('BNR');

  const [contactEmail, setContactEmail] =
    useState<string>('');

  const [description, setDescription] =
    useState<string>('');

  const [expiresAt, setExpiresAt] =
    useState<string>('');


  // ==========================================================
  // REVOKE MODAL
  // ==========================================================

  const [clientToRevoke, setClientToRevoke] =
    useState<RegulatoryApiClient | null>(null);

  const [revokeReason, setRevokeReason] =
    useState<string>('');


  // ==========================================================
  // GENERATED KEY
  // ==========================================================

  const [generatedKey, setGeneratedKey] =
    useState<string | null>(null);


  // ==========================================================
  // LOAD CLIENTS
  // ==========================================================

  const loadClients =
    useCallback(
      async (): Promise<void> => {

        try {

          setLoading(true);

          setError(null);


          const result =
            await regulatoryApi.listApiClients();


          setClients(
            Array.isArray(result)
              ? result
              : []
          );

        } catch (err) {

          console.error(
            'Failed to load API clients:',
            err
          );


          setError(
            regulatoryApi.getErrorMessage(
              err,
              'Failed to load API clients.'
            )
          );

        } finally {

          setLoading(false);
        }

      },
      []
    );


  // ==========================================================
  // INITIAL LOAD
  // ==========================================================

  useEffect(
    () => {

      void loadClients();

    },
    [
      loadClients,
    ]
  );


  // ==========================================================
  // FILTERED CLIENTS
  // ==========================================================

  const filteredClients =
    useMemo(
      () => {

        if (
          filter === 'ALL'
        ) {

          return clients;
        }


        return clients.filter(
          (
            client
          ) =>
            client.clientType === filter
        );

      },
      [
        clients,
        filter,
      ]
    );


  // ==========================================================
  // STATISTICS
  // ==========================================================

  const statistics =
    useMemo(
      () => {

        const total =
          clients.length;


        const bnr =
          clients.filter(
            client =>
              client.clientType === 'BNR'
          ).length;


        const creditBureau =
          clients.filter(
            client =>
              client.clientType === 'CREDIT_BUREAU'
          ).length;


        const active =
          clients.filter(
            client =>
              client.active !== false &&
              client.revoked !== true
          ).length;


        const revoked =
          clients.filter(
            client =>
              client.revoked === true ||
              client.active === false
          ).length;


        return {
          total,
          bnr,
          creditBureau,
          active,
          revoked,
        };

      },
      [
        clients,
      ]
    );


  // ==========================================================
  // CREATE CLIENT
  // ==========================================================

  const handleCreate =
    useCallback(
      async (
        event: React.FormEvent<HTMLFormElement>
      ): Promise<void> => {

        event.preventDefault();


        if (!name.trim()) {

          setError(
            'Please enter a name for the API client.'
          );

          return;
        }


        try {

          setCreating(true);

          setError(null);

          setSuccess(null);

          setGeneratedKey(null);


          const created =
            await regulatoryApi.createApiClient(
              {
                name:
                  name.trim(),

                clientType,

                contactEmail:
                  contactEmail.trim()
                    || undefined,

                description:
                  description.trim()
                    || undefined,

                expiresAt:
                  expiresAt
                    || null,
              }
            );


          const key =
            created.apiKey ||
            created.key ||
            null;


          if (key) {

            setGeneratedKey(
              key
            );
          }


          setSuccess(
            'API client created successfully.'
          );


          setName('');

          setContactEmail('');

          setDescription('');

          setExpiresAt('');

          setClientType('BNR');


          await loadClients();

        } catch (err) {

          console.error(
            'Failed to create API client:',
            err
          );


          setError(
            regulatoryApi.getErrorMessage(
              err,
              'Failed to create API client.'
            )
          );

        } finally {

          setCreating(false);
        }

      },
      [
        name,
        clientType,
        contactEmail,
        description,
        expiresAt,
        loadClients,
      ]
    );


  // ==========================================================
  // OPEN REVOKE
  // ==========================================================

  const openRevoke =
    useCallback(
      (
        client: RegulatoryApiClient
      ): void => {

        setClientToRevoke(
          client
        );

        setRevokeReason('');

        setError(null);

        setSuccess(null);
      },
      []
    );


  // ==========================================================
  // CLOSE REVOKE
  // ==========================================================

  const closeRevoke =
    useCallback(
      (): void => {

        if (
          revokingId !== null
        ) {

          return;
        }


        setClientToRevoke(
          null
        );

        setRevokeReason('');
      },
      [
        revokingId,
      ]
    );


  // ==========================================================
  // CONFIRM REVOKE
  // ==========================================================

  const confirmRevoke =
    useCallback(
      async (): Promise<void> => {

        if (
          !clientToRevoke
        ) {

          return;
        }


        const id =
          clientToRevoke.id;


        try {

          setRevokingId(
            id
          );

          setError(null);

          setSuccess(null);


          await regulatoryApi.revokeApiClient(
            id,
            revokeReason.trim()
              || undefined
          );


          setSuccess(
            `API client "${clientToRevoke.name}" has been revoked.`
          );


          setClientToRevoke(
            null
          );

          setRevokeReason('');


          await loadClients();

        } catch (err) {

          console.error(
            'Failed to revoke API client:',
            err
          );


          setError(
            regulatoryApi.getErrorMessage(
              err,
              'Failed to revoke API client.'
            )
          );

        } finally {

          setRevokingId(
            null
          );
        }

      },
      [
        clientToRevoke,
        revokeReason,
        loadClients,
      ]
    );


  // ==========================================================
  // COPY API KEY
  // ==========================================================

  const copyApiKey =
    useCallback(
      async (
        key: string
      ): Promise<void> => {

        try {

          await navigator.clipboard.writeText(
            key
          );


          setSuccess(
            'API key copied to clipboard.'
          );

        } catch {

          setError(
            'Unable to copy the API key.'
          );
        }
      },
      []
    );


  // ==========================================================
  // FORMAT DATE
  // ==========================================================

  const formatDate =
    useCallback(
      (
        value?: string | null
      ): string => {

        if (!value) {

          return '—';
        }


        const date =
          new Date(
            value
          );


        if (
          Number.isNaN(
            date.getTime()
          )
        ) {

          return value;
        }


        return new Intl.DateTimeFormat(
          'en-RW',
          {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
          }
        ).format(
          date
        );
      },
      []
    );


  // ==========================================================
  // CLIENT STATUS
  // ==========================================================

  const isClientActive =
    useCallback(
      (
        client: RegulatoryApiClient
      ): boolean => {

        return (
          client.revoked !== true &&
          client.active !== false
        );
      },
      []
    );


  // ==========================================================
  // LOADING
  // ==========================================================

  if (loading) {

    return (

      <div className="min-h-screen bg-slate-950 p-6">

        <div className="mx-auto max-w-7xl">

          <div className="animate-pulse space-y-6">

            <div className="h-12 w-80 rounded-xl bg-slate-800" />

            <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">

              {Array.from(
                {
                  length: 4,
                }
              ).map(
                (
                  _,
                  index
                ) => (

                  <div
                    key={index}
                    className="h-32 rounded-2xl bg-slate-800"
                  />

                )
              )}

            </div>

            <div className="h-96 rounded-2xl bg-slate-800" />

          </div>

        </div>

      </div>
    );
  }


  // ==========================================================
  // RENDER
  // ==========================================================

  return (

    <div className="min-h-screen bg-slate-950 text-white">

      <div className="mx-auto max-w-7xl space-y-8 p-6 lg:p-8">


        {/* ================================================== */}
        {/* HEADER */}
        {/* ================================================== */}

        <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">

          <div>

            <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-cyan-400/20 bg-cyan-400/10 px-3 py-1 text-xs font-medium text-cyan-300">

              <span className="h-2 w-2 rounded-full bg-cyan-400" />

              REGULATORY ACCESS CONTROL

            </div>


            <h1 className="text-3xl font-bold tracking-tight lg:text-4xl">

              API Key Management

            </h1>


            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-400">

              Manage secure API credentials for BNR and Credit Bureau
              regulatory integrations. Each client is scoped to its
              designated regulatory service.

            </p>

          </div>


          <button
            type="button"
            onClick={() => void loadClients()}
            className="rounded-xl border border-slate-700 bg-slate-900 px-5 py-3 text-sm font-semibold text-slate-200 transition hover:border-cyan-400/40 hover:bg-slate-800"
          >
            Refresh Clients
          </button>

        </div>


        {/* ================================================== */}
        {/* ALERTS */}
        {/* ================================================== */}

        {error && (

          <div className="rounded-2xl border border-red-500/20 bg-red-500/10 p-4">

            <p className="font-semibold text-red-300">
              Error
            </p>

            <p className="mt-1 text-sm text-red-400">
              {error}
            </p>

          </div>

        )}


        {success && (

          <div className="rounded-2xl border border-emerald-500/20 bg-emerald-500/10 p-4">

            <p className="font-semibold text-emerald-300">
              Success
            </p>

            <p className="mt-1 text-sm text-emerald-400">
              {success}
            </p>

          </div>

        )}


        {/* ================================================== */}
        {/* GENERATED API KEY */}
        {/* ================================================== */}

        {generatedKey && (

          <div className="rounded-2xl border border-amber-400/30 bg-amber-400/10 p-5">

            <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">

              <div>

                <p className="text-sm font-semibold text-amber-300">
                  New API key generated
                </p>

                <p className="mt-1 text-xs text-amber-200/70">
                  Copy this key now and store it securely.
                  Depending on your backend, the full key may not
                  be displayed again.

                </p>

              </div>


              <button
                type="button"
                onClick={() =>
                  void copyApiKey(
                    generatedKey
                  )
                }
                className="rounded-xl bg-amber-400 px-5 py-3 text-sm font-bold text-slate-950 transition hover:bg-amber-300"
              >
                Copy API Key
              </button>

            </div>


            <div className="mt-4 overflow-x-auto rounded-xl border border-amber-400/20 bg-slate-950/60 p-4">

              <code className="break-all font-mono text-sm text-amber-200">
                {generatedKey}
              </code>

            </div>

          </div>

        )}


        {/* ================================================== */}
        {/* STATISTICS */}
        {/* ================================================== */}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">


          <StatCard
            label="Total Clients"
            value={statistics.total}
            icon="◈"
          />


          <StatCard
            label="Active"
            value={statistics.active}
            icon="●"
          />


          <StatCard
            label="BNR"
            value={statistics.bnr}
            icon="▣"
          />


          <StatCard
            label="Credit Bureau"
            value={statistics.creditBureau}
            icon="▤"
          />


          <StatCard
            label="Revoked"
            value={statistics.revoked}
            icon="×"
          />

        </div>


        {/* ================================================== */}
        {/* CREATE CLIENT */}
        {/* ================================================== */}

        <section className="overflow-hidden rounded-3xl border border-slate-800 bg-slate-900/80 shadow-2xl shadow-black/20">

          <div className="border-b border-slate-800 px-6 py-5">

            <div className="flex items-center gap-3">

              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-cyan-400/10 text-cyan-300">
                +
              </div>

              <div>

                <h2 className="text-lg font-semibold">
                  Create Regulatory API Client
                </h2>

                <p className="text-sm text-slate-400">
                  Create a dedicated credential for BNR or Credit Bureau access.
                </p>

              </div>

            </div>

          </div>


          <form
            onSubmit={handleCreate}
            className="grid grid-cols-1 gap-5 p-6 md:grid-cols-2"
          >

            {/* NAME */}

            <div>

              <label className="mb-2 block text-sm font-medium text-slate-300">
                Client Name
              </label>

              <input
                value={name}
                onChange={(event) =>
                  setName(
                    event.target.value
                  )
                }
                placeholder="e.g. BNR Production Integration"
                className="w-full rounded-xl border border-slate-700 bg-slate-950 px-4 py-3 text-sm text-white outline-none transition placeholder:text-slate-600 focus:border-cyan-400"
              />

            </div>


            {/* TYPE */}

            <div>

              <label className="mb-2 block text-sm font-medium text-slate-300">
                Regulatory Service
              </label>

              <select
                value={clientType}
                onChange={(event) =>
                  setClientType(
                    event.target.value as RegulatoryApiClientType
                  )
                }
                className="w-full rounded-xl border border-slate-700 bg-slate-950 px-4 py-3 text-sm text-white outline-none focus:border-cyan-400"
              >

                <option value="BNR">
                  BNR
                </option>

                <option value="CREDIT_BUREAU">
                  Credit Bureau
                </option>

              </select>

            </div>


            {/* EMAIL */}

            <div>

              <label className="mb-2 block text-sm font-medium text-slate-300">
                Contact Email
              </label>

              <input
                type="email"
                value={contactEmail}
                onChange={(event) =>
                  setContactEmail(
                    event.target.value
                  )
                }
                placeholder="regulatory@example.com"
                className="w-full rounded-xl border border-slate-700 bg-slate-950 px-4 py-3 text-sm text-white outline-none placeholder:text-slate-600 focus:border-cyan-400"
              />

            </div>


            {/* EXPIRY */}

            <div>

              <label className="mb-2 block text-sm font-medium text-slate-300">
                Expiry Date
              </label>

              <input
                type="date"
                value={expiresAt}
                onChange={(event) =>
                  setExpiresAt(
                    event.target.value
                  )
                }
                className="w-full rounded-xl border border-slate-700 bg-slate-950 px-4 py-3 text-sm text-white outline-none focus:border-cyan-400"
              />

            </div>


            {/* DESCRIPTION */}

            <div className="md:col-span-2">

              <label className="mb-2 block text-sm font-medium text-slate-300">
                Description
              </label>

              <textarea
                value={description}
                onChange={(event) =>
                  setDescription(
                    event.target.value
                  )
                }
                rows={3}
                placeholder="Describe the integration and its intended purpose."
                className="w-full resize-none rounded-xl border border-slate-700 bg-slate-950 px-4 py-3 text-sm text-white outline-none placeholder:text-slate-600 focus:border-cyan-400"
              />

            </div>


            {/* SUBMIT */}

            <div className="flex justify-end md:col-span-2">

              <button
                type="submit"
                disabled={creating}
                className="rounded-xl bg-cyan-400 px-6 py-3 text-sm font-bold text-slate-950 transition hover:bg-cyan-300 disabled:cursor-not-allowed disabled:opacity-50"
              >

                {creating
                  ? 'Creating API Client...'
                  : 'Create API Client'}

              </button>

            </div>

          </form>

        </section>


        {/* ================================================== */}
        {/* CLIENT LIST */}
        {/* ================================================== */}

        <section className="overflow-hidden rounded-3xl border border-slate-800 bg-slate-900/80 shadow-2xl shadow-black/20">

          {/* HEADER */}

          <div className="flex flex-col gap-4 border-b border-slate-800 px-6 py-5 lg:flex-row lg:items-center lg:justify-between">

            <div>

              <h2 className="text-lg font-semibold">
                Regulatory API Clients
              </h2>

              <p className="mt-1 text-sm text-slate-400">
                Credentials currently configured for regulatory integrations.
              </p>

            </div>


            {/* FILTER */}

            <div className="flex rounded-xl border border-slate-700 bg-slate-950 p-1">

              {(
                [
                  'ALL',
                  'BNR',
                  'CREDIT_BUREAU',
                ] as ClientFilter[]
              ).map(
                option => (

                  <button
                    key={option}
                    type="button"
                    onClick={() =>
                      setFilter(
                        option
                      )
                    }
                    className={
                      filter === option
                        ? 'rounded-lg bg-cyan-400 px-4 py-2 text-xs font-bold text-slate-950'
                        : 'rounded-lg px-4 py-2 text-xs font-semibold text-slate-400 hover:text-white'
                    }
                  >
                    {option === 'ALL'
                      ? 'All'
                      : option === 'BNR'
                        ? 'BNR'
                        : 'Credit Bureau'}
                  </button>

                )
              )}

            </div>

          </div>


          {/* TABLE */}

          {filteredClients.length === 0 ? (

            <div className="px-6 py-16 text-center">

              <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-slate-800 text-2xl text-slate-500">
                ◈
              </div>

              <h3 className="mt-4 font-semibold text-white">
                No API clients found
              </h3>

              <p className="mt-1 text-sm text-slate-500">
                Create a regulatory API client above to get started.
              </p>

            </div>

          ) : (

            <div className="overflow-x-auto">

              <table className="min-w-full">

                <thead className="border-b border-slate-800 bg-slate-950/70">

                  <tr>

                    <th className="px-6 py-4 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
                      Client
                    </th>

                    <th className="px-6 py-4 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
                      Service
                    </th>

                    <th className="px-6 py-4 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
                      Status
                    </th>

                    <th className="px-6 py-4 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
                      Created
                    </th>

                    <th className="px-6 py-4 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
                      Expires
                    </th>

                    <th className="px-6 py-4 text-right text-xs font-semibold uppercase tracking-wider text-slate-500">
                      Action
                    </th>

                  </tr>

                </thead>


                <tbody className="divide-y divide-slate-800">

                  {filteredClients.map(
                    client => {

                      const active =
                        isClientActive(
                          client
                        );


                      return (

                        <tr
                          key={client.id}
                          className="transition hover:bg-slate-800/40"
                        >

                          {/* CLIENT */}

                          <td className="px-6 py-5">

                            <div className="flex items-center gap-3">

                              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-800 font-bold text-cyan-300">
                                {client.name
                                  ?.charAt(0)
                                  .toUpperCase()
                                  || 'A'}
                              </div>

                              <div>

                                <p className="font-semibold text-white">
                                  {client.name}
                                </p>

                                {client.contactEmail && (

                                  <p className="mt-1 text-xs text-slate-500">
                                    {client.contactEmail}
                                  </p>

                                )}

                              </div>

                            </div>

                          </td>


                          {/* SERVICE */}

                          <td className="px-6 py-5">

                            <ServiceBadge
                              type={
                                client.clientType
                              }
                            />

                          </td>


                          {/* STATUS */}

                          <td className="px-6 py-5">

                            {active ? (

                              <span className="inline-flex items-center gap-2 rounded-full bg-emerald-400/10 px-3 py-1.5 text-xs font-semibold text-emerald-300">

                                <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />

                                Active

                              </span>

                            ) : (

                              <span className="inline-flex items-center gap-2 rounded-full bg-red-400/10 px-3 py-1.5 text-xs font-semibold text-red-300">

                                <span className="h-1.5 w-1.5 rounded-full bg-red-400" />

                                Revoked

                              </span>

                            )}

                          </td>


                          {/* CREATED */}

                          <td className="px-6 py-5 text-sm text-slate-400">

                            {formatDate(
                              client.createdAt
                            )}

                          </td>


                          {/* EXPIRY */}

                          <td className="px-6 py-5 text-sm text-slate-400">

                            {formatDate(
                              client.expiresAt
                            )}

                          </td>


                          {/* ACTION */}

                          <td className="px-6 py-5 text-right">

                            {active ? (

                              <button
                                type="button"
                                onClick={() =>
                                  openRevoke(
                                    client
                                  )
                                }
                                className="rounded-lg border border-red-500/20 bg-red-500/10 px-3 py-2 text-xs font-semibold text-red-300 transition hover:bg-red-500/20"
                              >
                                Revoke
                              </button>

                            ) : (

                              <span className="text-xs text-slate-600">
                                Revoked
                              </span>

                            )}

                          </td>

                        </tr>

                      );
                    }
                  )}

                </tbody>

              </table>

            </div>

          )}

        </section>


        {/* ================================================== */}
        {/* SECURITY INFORMATION */}
        {/* ================================================== */}

        <section className="grid grid-cols-1 gap-5 md:grid-cols-3">

          <SecurityCard
            title="BNR Scoped Access"
            description="BNR credentials are intended only for BNR regulatory reporting endpoints."
            icon="▣"
          />


          <SecurityCard
            title="Credit Bureau Scoped Access"
            description="Credit Bureau credentials are separated from BNR reporting access."
            icon="▤"
          />


          <SecurityCard
            title="Revocation"
            description="Credentials can be immediately revoked when an integration is no longer trusted."
            icon="×"
          />

        </section>


      </div>


      {/* ==================================================== */}
      {/* REVOKE MODAL */}
      {/* ==================================================== */}

      {clientToRevoke && (

        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm">

          <div className="w-full max-w-lg rounded-3xl border border-slate-700 bg-slate-900 p-6 shadow-2xl">

            <div className="flex items-start justify-between gap-4">

              <div>

                <h2 className="text-xl font-bold">
                  Revoke API Client
                </h2>

                <p className="mt-2 text-sm text-slate-400">
                  You are about to revoke:
                </p>

                <p className="mt-1 font-semibold text-white">
                  {clientToRevoke.name}
                </p>

              </div>


              <button
                type="button"
                onClick={closeRevoke}
                className="text-xl text-slate-500 hover:text-white"
              >
                ×
              </button>

            </div>


            <div className="mt-6">

              <label className="mb-2 block text-sm font-medium text-slate-300">
                Reason
              </label>

              <textarea
                value={revokeReason}
                onChange={(event) =>
                  setRevokeReason(
                    event.target.value
                  )
                }
                rows={4}
                placeholder="Why is this API client being revoked?"
                className="w-full resize-none rounded-xl border border-slate-700 bg-slate-950 px-4 py-3 text-sm text-white outline-none placeholder:text-slate-600 focus:border-red-400"
              />

            </div>


            <div className="mt-6 flex justify-end gap-3">

              <button
                type="button"
                onClick={closeRevoke}
                disabled={
                  revokingId !== null
                }
                className="rounded-xl border border-slate-700 px-5 py-3 text-sm font-semibold text-slate-300 hover:bg-slate-800"
              >
                Cancel
              </button>


              <button
                type="button"
                onClick={() =>
                  void confirmRevoke()
                }
                disabled={
                  revokingId !== null
                }
                className="rounded-xl bg-red-500 px-5 py-3 text-sm font-bold text-white hover:bg-red-400 disabled:opacity-50"
              >

                {revokingId !== null
                  ? 'Revoking...'
                  : 'Revoke API Client'}

              </button>

            </div>

          </div>

        </div>

      )}

    </div>
  );
}


// ============================================================
// STAT CARD
// ============================================================

function StatCard({
  label,
  value,
  icon,
}: {
  label: string;
  value: number;
  icon: string;
}) {

  return (

    <div className="rounded-2xl border border-slate-800 bg-slate-900 p-5">

      <div className="flex items-center justify-between">

        <span className="text-sm text-slate-400">
          {label}
        </span>

        <span className="text-lg text-cyan-300">
          {icon}
        </span>

      </div>


      <p className="mt-3 text-3xl font-bold text-white">
        {value.toLocaleString('en-US')}
      </p>

    </div>
  );
}


// ============================================================
// SERVICE BADGE
// ============================================================

function ServiceBadge({
  type,
}: {
  type: string;
}) {

  const isBnr =
    type === 'BNR';


  return (

    <span
      className={
        isBnr
          ? 'inline-flex rounded-full bg-cyan-400/10 px-3 py-1.5 text-xs font-semibold text-cyan-300'
          : 'inline-flex rounded-full bg-violet-400/10 px-3 py-1.5 text-xs font-semibold text-violet-300'
      }
    >

      {isBnr
        ? 'BNR'
        : 'Credit Bureau'}

    </span>
  );
}


// ============================================================
// SECURITY CARD
// ============================================================

function SecurityCard({
  title,
  description,
  icon,
}: {
  title: string;
  description: string;
  icon: string;
}) {

  return (

    <div className="rounded-2xl border border-slate-800 bg-slate-900 p-5">

      <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-slate-800 text-cyan-300">
        {icon}
      </div>


      <h3 className="mt-4 font-semibold text-white">
        {title}
      </h3>


      <p className="mt-2 text-sm leading-6 text-slate-400">
        {description}
      </p>

    </div>
  );
}