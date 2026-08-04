
'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';

import {
  loanApi,
  paymentApi,
  creditBureauApi,
  esignatureApi
} from '@/services/api';

import { Loan, Payment } from '@/types';

import {
  Card,
  CardHeader,
  CardBody
} from '@/components/ui/Card';

import { Button } from '@/components/ui/Button';

import {
  StatusBadge,
  RiskBadge,
  Pill
} from '@/components/ui/Badge';

import {
  Table,
  Thead,
  Th,
  Tbody,
  Tr,
  Td
} from '@/components/ui/Table';

import { Modal } from '@/components/ui/Modal';

import {
  FormGroup,
  Input,
  Select,
  Textarea,
  Alert
} from '@/components/ui/Form';

import {
  formatCurrency,
  formatDate,
  LOAN_TYPE_META
} from '@/lib/utils';

import {
  IconBank,
  IconSignature,
  IconCard,
  IconCoins,
  IconSend,
  IconCheckCircle,
  IconClock,
  IconFileText,
  IconAlertTriangle,
  IconFileEdit,
  IconSearch,
  IconCalendar,
  IconFlag
} from '@/components/ui/Icons';

import { useAuth } from '@/hooks/useAuth';

import { useOnlineStatus } from '@/hooks/useOnlineStatus';

import {
  queueAction,
  cacheGet,
  cacheSet
} from '@/lib/offlineDb';

import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer
} from 'recharts';

import DocumentsPanel from '@/components/DocumentsPanel';

import {
  DOCUMENT_TYPE_LABELS
} from '@/services/fileService';


const TABS = [
  'Overview',
  'Borrower',
  'Documents',
  'Schedule',
  'Timeline',
  'Comments'
] as const;

type Tab = typeof TABS[number];


// ============================================================
// FIELD
// ============================================================

function Field({
  label,
  value
}: {
  label: string;
  value?: React.ReactNode;
}) {
  return (
    <div>
      <div className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-1">
        {label}
      </div>

      <div className="text-sm font-medium text-gray-800">
        {value ?? '—'}
      </div>
    </div>
  );
}


// ============================================================
// CREDIT BUREAU TYPE
// ============================================================

type CreditBureauCheck = {
  id?: number;
  reference?: string;
  provider?: string;
  status?: string;
  creditScore?: number;
  riskGrade?: string;
  activeFacilities?: number;
  delinquentAccounts?: number;
  totalOutstandingDebt?: number;
  totalMonthlyObligations?: number;
  hasDefaultHistory?: boolean;
  hasActiveListing?: boolean;
  listingReason?: string;
  failureReason?: string;
  requestedBy?: string;
  createdAt?: string;
  checkedAt?: string;
};


// ============================================================
// CREDIT SCORE COLOR
// ============================================================

function creditScoreColor(score?: number) {
  if (score == null) {
    return 'text-gray-500';
  }

  if (score >= 750) {
    return 'text-teal-600';
  }

  if (score >= 680) {
    return 'text-blue-600';
  }

  if (score >= 600) {
    return 'text-yellow-600';
  }

  if (score >= 500) {
    return 'text-orange-600';
  }

  return 'text-red-600';
}


// ============================================================
// CREDIT SCORE LABEL
// ============================================================

function creditScoreLabel(score?: number) {
  if (score == null) {
    return 'Unknown';
  }

  if (score >= 750) {
    return 'Excellent';
  }

  if (score >= 680) {
    return 'Good';
  }

  if (score >= 600) {
    return 'Fair';
  }

  if (score >= 500) {
    return 'Poor';
  }

  return 'Very Poor';
}


// ============================================================
// CREDIT BUREAU REPORT CARD
// ============================================================

function CreditBureauReport({
  report,
  history,
  currency,
  locale,
  loading
}: {
  report: CreditBureauCheck | null;
  history: CreditBureauCheck[];
  currency: string;
  locale: string;
  loading: boolean;
}) {
  const fc = (n?: number) =>
    formatCurrency(n, currency, locale);

  if (!report) {
    return (
      <Card className="mt-5">
        <CardHeader title="Credit Bureau Report" />

        <CardBody>
          <div className="text-center py-8">

            <div className="w-14 h-14 mx-auto rounded-full bg-gray-100 flex items-center justify-center mb-3">
              <IconBank className="w-7 h-7 text-gray-400" />
            </div>

            <div className="font-semibold text-gray-700">
              No Credit Bureau Check Available
            </div>

            <p className="text-sm text-gray-400 mt-1">
              Use the Credit Bureau Check button above to retrieve the borrower's credit information.
            </p>

          </div>
        </CardBody>
      </Card>
    );
  }

  const simulated =
    report.provider === 'INTERNAL_SIMULATED';

  return (
    <Card className="mt-5">

      {/* ======================================================
          HEADER
      ====================================================== */}

      <CardHeader
        title="Credit Bureau Report"
      />

      <CardBody>

        {/* ====================================================
            SIMULATION WARNING
        ==================================================== */}

        {simulated && (
          <div className="mb-5 bg-amber-50 border border-amber-200 rounded-xl p-4">

            <div className="flex gap-3">

              <IconAlertTriangle className="w-5 h-5 text-amber-600 shrink-0" />

              <div>

                <div className="font-bold text-amber-800">
                  Internal Credit Estimate
                </div>

                <div className="text-sm text-amber-700 mt-1">
                  No live licensed Credit Bureau is currently connected.
                  This result was generated by the internal simulation and
                  must not be treated as an official Credit Bureau report.
                </div>

              </div>

            </div>

          </div>
        )}


        {/* ====================================================
            SCORE HERO
        ==================================================== */}

        <div className="grid grid-cols-1 lg:grid-cols-4 gap-4 mb-5">

          <div className="lg:col-span-1 bg-gray-50 rounded-xl p-5 text-center">

            <div className="text-xs font-bold text-gray-400 uppercase tracking-wider">
              Credit Score
            </div>

            <div
              className={`text-5xl font-extrabold mt-2 ${creditScoreColor(
                report.creditScore
              )}`}
            >
              {report.creditScore ?? '—'}
            </div>

            <div
              className={`text-sm font-bold mt-1 ${creditScoreColor(
                report.creditScore
              )}`}
            >
              {creditScoreLabel(report.creditScore)}
            </div>

          </div>


          <div className="bg-gray-50 rounded-xl p-5">

            <div className="text-xs font-bold text-gray-400 uppercase tracking-wider">
              Risk Grade
            </div>

            <div className="text-2xl font-extrabold text-gray-900 mt-2">
              {report.riskGrade ?? '—'}
            </div>

            <div className="text-xs text-gray-400 mt-1">
              Bureau risk classification
            </div>

          </div>


          <div className="bg-gray-50 rounded-xl p-5">

            <div className="text-xs font-bold text-gray-400 uppercase tracking-wider">
              Provider
            </div>

            <div className="text-lg font-extrabold text-gray-900 mt-2 break-words">
              {report.provider ?? '—'}
            </div>

            <div className="text-xs text-gray-400 mt-1">
              {report.status ?? 'Unknown status'}
            </div>

          </div>


          <div className="bg-gray-50 rounded-xl p-5">

            <div className="text-xs font-bold text-gray-400 uppercase tracking-wider">
              Reference
            </div>

            <div className="text-sm font-bold text-gray-900 mt-2 break-all">
              {report.reference ?? '—'}
            </div>

            <div className="text-xs text-gray-400 mt-1">
              Bureau check reference
            </div>

          </div>

        </div>


        {/* ====================================================
            CREDIT FACILITIES
        ==================================================== */}

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 mb-5">

          <div className="border border-gray-200 rounded-xl p-4">

            <div className="text-xs text-gray-400">
              Active Facilities
            </div>

            <div className="text-xl font-extrabold text-gray-900 mt-1">
              {report.activeFacilities ?? 0}
            </div>

          </div>


          <div className="border border-gray-200 rounded-xl p-4">

            <div className="text-xs text-gray-400">
              Delinquent Accounts
            </div>

            <div
              className={`text-xl font-extrabold mt-1 ${
                (report.delinquentAccounts ?? 0) > 0
                  ? 'text-red-600'
                  : 'text-teal-600'
              }`}
            >
              {report.delinquentAccounts ?? 0}
            </div>

          </div>


          <div className="border border-gray-200 rounded-xl p-4">

            <div className="text-xs text-gray-400">
              Outstanding Debt
            </div>

            <div className="text-xl font-extrabold text-gray-900 mt-1">
              {fc(report.totalOutstandingDebt)}
            </div>

          </div>


          <div className="border border-gray-200 rounded-xl p-4">

            <div className="text-xs text-gray-400">
              Monthly Obligations
            </div>

            <div className="text-xl font-extrabold text-gray-900 mt-1">
              {fc(report.totalMonthlyObligations)}
            </div>

          </div>

        </div>


        {/* ====================================================
            DEFAULT / LISTING
        ==================================================== */}

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-5">

          <div
            className={`rounded-xl border p-4 ${
              report.hasDefaultHistory
                ? 'bg-red-50 border-red-200'
                : 'bg-teal-50 border-teal-200'
            }`}
          >

            <div className="flex items-center gap-2">

              {report.hasDefaultHistory ? (
                <IconAlertTriangle className="w-5 h-5 text-red-600" />
              ) : (
                <IconCheckCircle className="w-5 h-5 text-teal-600" />
              )}

              <div>

                <div className="text-xs font-bold uppercase tracking-wider">
                  Default History
                </div>

                <div className="font-bold mt-1">
                  {report.hasDefaultHistory
                    ? 'Default history detected'
                    : 'No default history detected'}
                </div>

              </div>

            </div>

          </div>


          <div
            className={`rounded-xl border p-4 ${
              report.hasActiveListing
                ? 'bg-red-50 border-red-200'
                : 'bg-teal-50 border-teal-200'
            }`}
          >

            <div className="flex items-center gap-2">

              {report.hasActiveListing ? (
                <IconAlertTriangle className="w-5 h-5 text-red-600" />
              ) : (
                <IconCheckCircle className="w-5 h-5 text-teal-600" />
              )}

              <div>

                <div className="text-xs font-bold uppercase tracking-wider">
                  Active Listing
                </div>

                <div className="font-bold mt-1">
                  {report.hasActiveListing
                    ? 'Active listing detected'
                    : 'No active listing'}
                </div>

              </div>

            </div>

            {report.listingReason && (
              <div className="text-sm mt-2">
                <strong>Reason:</strong>{' '}
                {report.listingReason}
              </div>
            )}

          </div>

        </div>


        {/* ====================================================
            FAILURE REASON
        ==================================================== */}

        {report.failureReason && (
          <div className="mb-5 bg-orange-50 border border-orange-200 rounded-xl p-4">

            <div className="font-bold text-orange-800">
              Provider Notice
            </div>

            <div className="text-sm text-orange-700 mt-1">
              {report.failureReason}
            </div>

          </div>
        )}


        {/* ====================================================
            CHECK INFORMATION
        ==================================================== */}

        <div className="border-t border-gray-100 pt-4">

          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">

            <Field
              label="Requested By"
              value={report.requestedBy}
            />

            <Field
              label="Check Date"
              value={formatDate(
                report.createdAt ?? report.checkedAt,
                locale
              )}
            />

            <Field
              label="Status"
              value={
                <Pill
                  label={report.status ?? 'UNKNOWN'}
                  color={
                    report.status === 'COMPLETED'
                      ? 'teal'
                      : 'gray'
                  }
                />
              }
            />

            <Field
              label="Report Type"
              value={
                simulated
                  ? 'Internal Simulation'
                  : 'Live Bureau'
              }
            />

          </div>

        </div>


        {/* ====================================================
            HISTORY
        ==================================================== */}

        {history.length > 0 && (

          <div className="mt-6 border-t border-gray-100 pt-5">

            <div className="font-bold text-gray-800 mb-3">
              Previous Credit Bureau Checks
            </div>

            <div className="overflow-x-auto">

              <Table>

                <Thead>

                  <tr>
                    <Th>Date</Th>
                    <Th>Provider</Th>
                    <Th>Score</Th>
                    <Th>Risk</Th>
                    <Th>Delinquent</Th>
                    <Th>Status</Th>
                    <Th>Requested By</Th>
                  </tr>

                </Thead>

                <Tbody>

                  {history.map((item, index) => (

                    <Tr key={item.id ?? index}>

                      <Td>
                        {formatDate(
                          item.createdAt ?? item.checkedAt,
                          locale
                        )}
                      </Td>

                      <Td>
                        {item.provider ?? '—'}
                      </Td>

                      <Td>
                        <span
                          className={`font-bold ${creditScoreColor(
                            item.creditScore
                          )}`}
                        >
                          {item.creditScore ?? '—'}
                        </span>
                      </Td>

                      <Td>
                        {item.riskGrade ?? '—'}
                      </Td>

                      <Td>
                        {item.delinquentAccounts ?? 0}
                      </Td>

                      <Td>
                        {item.status ?? '—'}
                      </Td>

                      <Td>
                        {item.requestedBy ?? '—'}
                      </Td>

                    </Tr>

                  ))}

                </Tbody>

              </Table>

            </div>

          </div>

        )}

      </CardBody>

    </Card>
  );
}


// ============================================================
// MAIN PAGE
// ============================================================

export default function LoanDetailPage() {

  const { id } = useParams<{ id: string }>();

  const router = useRouter();

  const {
    currency,
    locale,
    isOfficer
  } = useAuth();

  const fc = (n?: number) =>
    formatCurrency(n, currency, locale);


  // ==========================================================
  // LOAN
  // ==========================================================

  const [loan, setLoan] =
    useState<Loan | null>(null);

  const [schedule, setSchedule] =
    useState<Payment[]>([]);

  const [loading, setLoading] =
    useState(true);

  const [tab, setTab] =
    useState<Tab>('Overview');

  const [msg, setMsg] =
    useState<{
      type: 'error' | 'success';
      text: string;
    } | null>(null);


  // ==========================================================
  // CREDIT BUREAU
  // ==========================================================

  const [creditReport, setCreditReport] =
    useState<CreditBureauCheck | null>(null);

  const [creditHistory, setCreditHistory] =
    useState<CreditBureauCheck[]>([]);

  const [cbBusy, setCbBusy] =
    useState(false);

  const [cbHistoryLoading, setCbHistoryLoading] =
    useState(false);


  // ==========================================================
  // PAYMENT
  // ==========================================================

  const [payOpen, setPayOpen] =
    useState(false);

  const [payForm, setPayForm] =
    useState({
      amount: '',
      paymentMethod: 'BANK_TRANSFER',
      transactionId: '',
      channel: '',
      notes: ''
    });

  const [paying, setPaying] =
    useState(false);


  // ==========================================================
  // STATUS
  // ==========================================================

  const [stOpen, setStOpen] =
    useState(false);

  const [stForm, setStForm] =
    useState({
      status: '',
      rejectionReason: '',
      internalNotes: '',
      interestRate: ''
    });

  const [stSaving, setStSaving] =
    useState(false);


  // ==========================================================
  // E-SIGNATURE
  // ==========================================================

  const [esignBusy, setEsignBusy] =
    useState(false);


  // ==========================================================
  // LOAD LOAN
  // ==========================================================

  const load = () => {

    Promise.all([
      loanApi.get(Number(id)),
      loanApi.schedule(Number(id))
    ])

      .then(([l, s]: [any, any]) => {

        setLoan(l);

        setSchedule(
          Array.isArray(s)
            ? s
            : []
        );

        cacheSet(
          `/loans/${id}`,
          {
            loan: l,
            schedule: s
          }
        );

      })

      .catch(async (e) => {

        const cached =
          await cacheGet<{
            loan: Loan;
            schedule: Payment[];
          }>(`/loans/${id}`);

        if (cached) {

          setLoan(cached.loan);

          setSchedule(
            cached.schedule
          );

          setMsg({
            type: 'error',
            text:
              "You're offline — showing the last saved version of this loan."
          });

        } else {

          setMsg({
            type: 'error',
            text: e.message
          });

        }

      })

      .finally(() => {

        setLoading(false);

      });

  };


  useEffect(() => {

    load();

  }, [id]);


  // ==========================================================
  // LOAD CREDIT HISTORY
  // ==========================================================

  const loadCreditHistory = async (
    borrowerId?: number
  ) => {

    if (!borrowerId) {
      return;
    }

    setCbHistoryLoading(true);

    try {

      const history =
        await creditBureauApi.history(
          borrowerId
        );

      const list =
        Array.isArray(history)
          ? history
          : [];

      setCreditHistory(list);

      if (list.length > 0) {

        setCreditReport(
          list[0]
        );

      }

    } catch (error) {

      console.error(
        'Failed to load credit bureau history',
        error
      );

    } finally {

      setCbHistoryLoading(false);

    }

  };


  // ==========================================================
  // LOAD LATEST CREDIT REPORT
  // ==========================================================

  const loadLatestCreditReport = async (
    borrowerId?: number
  ) => {

    if (!borrowerId) {
      return;
    }

    try {

      const latest =
        await creditBureauApi.latest(
          borrowerId
        );

      if (latest) {

        setCreditReport(
          latest
        );

      }

    } catch (error) {

      console.error(
        'Failed to load latest credit bureau report',
        error
      );

    }

  };


  // ==========================================================
  // LOAD CREDIT DATA WHEN LOAN LOADS
  // ==========================================================

  useEffect(() => {

    if (
      loan?.borrower?.id &&
      isOfficer
    ) {

      loadLatestCreditReport(
        loan.borrower.id
      );

      loadCreditHistory(
        loan.borrower.id
      );

    }

  }, [
    loan?.borrower?.id,
    isOfficer
  ]);


  // ==========================================================
  // CREDIT BUREAU CHECK
  // ==========================================================

  const handleCreditBureauCheck =
    async () => {

      if (!loan?.borrower?.id) {

        setMsg({
          type: 'error',
          text: 'No borrower is linked to this loan.'
        });

        return;
      }

      const borrowerId =
        loan.borrower.id;

      setCbBusy(true);

      setMsg(null);

      try {

        // ------------------------------------------------------
        // 1. PERFORM THE ACTUAL CREDIT BUREAU CHECK
        // ------------------------------------------------------

        const result =
          await creditBureauApi.check(
            borrowerId
          );

        const report =
          result as CreditBureauCheck;

        // ------------------------------------------------------
        // 2. IMMEDIATELY SHOW THE RESULT
        // ------------------------------------------------------

        setCreditReport(
          report
        );

        // ------------------------------------------------------
        // 3. REFRESH LATEST REPORT
        // ------------------------------------------------------

        await loadLatestCreditReport(
          borrowerId
        );

        // ------------------------------------------------------
        // 4. REFRESH CREDIT BUREAU HISTORY
        // ------------------------------------------------------

        await loadCreditHistory(
          borrowerId
        );

        // ------------------------------------------------------
        // 5. SHOW RESULT MESSAGE
        // ------------------------------------------------------

        const simulated =
          report?.provider ===
          'INTERNAL_SIMULATED';

        setMsg({

          type:
            simulated
              ? 'error'
              : 'success',

          text:
            simulated

              ? `⚠️ Internal credit estimate generated. Score ${
                  report?.creditScore ?? 'N/A'
                } (${
                  report?.riskGrade ?? 'N/A'
                }).`

              : `Credit Bureau check completed via ${
                  report?.provider ?? 'provider'
                }. Score ${
                  report?.creditScore ?? 'N/A'
                } (${
                  report?.riskGrade ?? 'N/A'
                }).`

        });

      } catch (err: any) {

        setMsg({
          type: 'error',
          text:
            err?.message ??
            'Credit Bureau check failed.'
        });

      } finally {

        setCbBusy(false);

      }

    };


  // ==========================================================
  // COMMENTS
  // ==========================================================

  const [comments, setComments] =
    useState<any[]>([]);

  const [commentText, setCommentText] =
    useState('');

  const [commentVisible, setCommentVisible] =
    useState(true);

  const [commentSaving, setCommentSaving] =
    useState(false);


  const loadComments = () =>
    loanApi
      .getComments(Number(id))
      .then((c) =>
        setComments(
          Array.isArray(c)
            ? c
            : []
        )
      )
      .catch(() => {});


  useEffect(() => {

    loadComments();

  }, [id]);


  const handleAddComment =
    async () => {

      if (!commentText.trim()) {
        return;
      }

      setCommentSaving(true);

      try {

        await loanApi.addComment(
          Number(id),
          commentText.trim(),
          commentVisible
        );

        setCommentText('');

        loadComments();

      } catch (e: any) {

        setMsg({
          type: 'error',
          text: e.message
        });

      } finally {

        setCommentSaving(false);

      }

    };


  // ==========================================================
  // DOCUMENT REQUIREMENTS
  // ==========================================================

  const [docReq, setDocReq] =
    useState<{
      required: string[];
      missing: string[];
      unverified: string[];
      readyToApprove: boolean;
      readyToDisburse: boolean;
    } | null>(null);


  const loadDocReq = () =>
    loanApi
      .documentRequirements(Number(id))
      .then((r: any) =>
        setDocReq(r)
      )
      .catch(() =>
        setDocReq(null)
      );


  useEffect(() => {

    loadDocReq();

  }, [id]);


  // ==========================================================
  // ONLINE
  // ==========================================================

  const online =
    useOnlineStatus();


  // ==========================================================
  // PAYMENT
  // ==========================================================

  const handlePay =
    async (e: React.FormEvent) => {

      e.preventDefault();

      setPaying(true);

      setMsg(null);

      if (!online) {

        try {

          await queueAction({

            url:
              `/loans/${id}/payments`,

            method:
              'POST',

            body: {
              ...payForm,
              amount:
                Number(payForm.amount)
            },

            label:
              `Payment — ${
                loan?.borrower?.firstName ?? 'Loan'
              } ${
                loan?.referenceNumber ?? ''
              } (${
                payForm.amount
              })`

          });


          setMsg({
            type: 'success',
            text:
              "Saved offline — you're not connected. This payment will submit automatically once you're back online."
          });


          setPayOpen(false);

        } catch (err: any) {

          setMsg({
            type: 'error',
            text:
              'Could not save offline: '
              + err.message
          });

        }

        setPaying(false);

        return;

      }


      try {

        await paymentApi.record(
          Number(id),
          {
            ...payForm,
            amount:
              Number(payForm.amount)
          }
        );


        setMsg({
          type: 'success',
          text:
            'Payment recorded successfully!'
        });


        setPayOpen(false);

        load();

      } catch (err: any) {

        setMsg({
          type: 'error',
          text: err.message
        });

      }

      setPaying(false);

    };


  // ==========================================================
  // STATUS
  // ==========================================================

  const handleStatus =
    async (e: React.FormEvent) => {

      e.preventDefault();

      setStSaving(true);

      setMsg(null);

      try {

        if (
          stForm.status === 'APPROVED'
        ) {

          await loanApi.approve(
            Number(id),
            stForm.internalNotes,
            stForm.interestRate
              ? Number(stForm.interestRate)
              : undefined
          );

        } else if (
          stForm.status === 'REJECTED'
        ) {

          await loanApi.reject(
            Number(id),
            stForm.rejectionReason
          );

        } else if (
          stForm.status === 'DISBURSED'
        ) {

          await loanApi.disburse(
            Number(id),
            'BANK_TRANSFER'
          );

        } else if (
          stForm.status
        ) {

          await loanApi.updateStatus(
            Number(id),
            stForm.status,
            stForm.internalNotes
          );

        } else {

          throw new Error(
            'Select a status first'
          );

        }


        setMsg({
          type: 'success',
          text:
            'Status updated!'
        });


        setStOpen(false);

        load();

        loadDocReq();

      } catch (err: any) {

        setMsg({
          type: 'error',
          text: err.message
        });

      }

      setStSaving(false);

    };


  // ==========================================================
  // E-SIGNATURE
  // ==========================================================

  const handleSendForSignature =
    async () => {

      setEsignBusy(true);

      setMsg(null);

      try {

        await esignatureApi.initiate(
          Number(id)
        );


        setMsg({
          type: 'success',
          text:
            'Signing link + verification code sent to the borrower by SMS.'
        });

      } catch (err: any) {

        setMsg({
          type: 'error',
          text: err.message
        });

      }

      setEsignBusy(false);

    };


  // ==========================================================
  // LOADING
  // ==========================================================

  if (loading) {

    return (
      <div className="flex items-center justify-center h-64">

        <div className="w-8 h-8 border-2 border-teal-500 border-t-transparent rounded-full animate-spin" />

      </div>
    );

  }


  if (!loan) {

    return (
      <div className="bg-red-50 border border-red-200 rounded-xl p-6 text-red-700">
        Loan not found
      </div>
    );

  }


  // ==========================================================
  // KPIs
  // ==========================================================

  const prog =
    loan.totalRepayable &&
    loan.totalPaid

      ? Math.min(
          100,
          Math.round(
            (loan.totalPaid /
              loan.totalRepayable) *
              100
          )
        )

      : 0;


  const chartData =
    schedule
      .filter(
        p => p.paid
      )
      .slice(-12)
      .map((p) => ({
        n:
          `#${p.installmentNumber}`,

        balance:
          p.outstandingAfter ?? 0,

        principal:
          p.principalComponent,

        interest:
          p.interestComponent

      }));


  const totalPenalty =
    schedule.reduce(
      (sum, p) =>
        sum + (p.penalty ?? 0),
      0
    );


  // ==========================================================
  // RENDER
  // ==========================================================

  return (

    <div>

      {/* ======================================================
          HEADER
      ====================================================== */}

      <div className="flex items-start justify-between mb-6">

        <div>

          <button
            onClick={() =>
              router.back()
            }
            className="text-sm text-gray-400 hover:text-gray-600 mb-2 flex items-center gap-1"
          >
            ← Back
          </button>


          <h1 className="text-2xl font-extrabold text-gray-900">
            {loan.referenceNumber}
          </h1>


          <div className="flex items-center gap-2 mt-1 flex-wrap">

            <StatusBadge
              status={loan.status}
            />


            {loan.riskCategory && (

              <RiskBadge
                category={loan.riskCategory}
                score={loan.riskScore}
              />

            )}


            <Pill
              label={`${
                LOAN_TYPE_META[
                  loan.loanType
                ]?.icon
              } ${
                LOAN_TYPE_META[
                  loan.loanType
                ]?.label ??
                loan.loanType
              }`}
              color="blue"
            />


            <Pill
              label={loan.currency}
              color="teal"
            />


            {loan.daysOverdue &&
              loan.daysOverdue > 0 ? (

              <Pill
                label={
                  <span className="inline-flex items-center gap-1">

                    <IconAlertTriangle className="w-3 h-3" />

                    {loan.daysOverdue}d overdue

                  </span>
                }
                color="red"
              />

            ) : null}

          </div>

        </div>


        {/* ====================================================
            ACTIONS
        ==================================================== */}

        <div className="flex gap-2 flex-wrap">

          {/* ==================================================
              SINGLE CREDIT BUREAU ACTION
          ================================================== */}

          {isOfficer &&
            loan.borrower && (

            <Button
              variant="outline"
              onClick={
                handleCreditBureauCheck
              }
              disabled={cbBusy}
            >

              <IconBank className="w-4 h-4" />

              {cbBusy
                ? 'Checking…'
                : 'Credit Bureau Check'}

            </Button>

          )}


          {isOfficer &&
            (
              loan.status === 'APPROVED' ||
              loan.status === 'DISBURSED' ||
              loan.status === 'ACTIVE'
            ) && (

            <Button
              variant="outline"
              onClick={
                handleSendForSignature
              }
              disabled={esignBusy}
            >

              <IconSignature className="w-4 h-4" />

              {esignBusy
                ? 'Sending…'
                : 'Send for E-Signature'}

            </Button>

          )}


          {isOfficer && (

            <Button
              variant="outline"
              onClick={() =>
                setStOpen(true)
              }
            >
              Update Status
            </Button>

          )}


          {loan.status === 'ACTIVE' && (

            <Button
              onClick={() => {

                setPayForm(
                  f => ({
                    ...f,

                    amount:
                      String(
                        loan.totalRepayable
                          ? Math.round(
                              (
                                loan.totalRepayable /
                                loan.durationMonths!
                              ) *
                              100
                            ) / 100

                          : ''
                      )

                  })
                );

                setPayOpen(true);

              }}
            >

              <IconCard className="w-4 h-4" />

              Record Payment

            </Button>

          )}

        </div>

      </div>


      {/* ======================================================
          MESSAGE
      ====================================================== */}

      {msg && (

        <div className="mb-5">

          <Alert type={msg.type}>
            {msg.text}
          </Alert>

        </div>

      )}


      {/* ======================================================
          CREDIT BUREAU REPORT
      ====================================================== */}

      {isOfficer &&
        loan.borrower && (

        <CreditBureauReport

          report={
            creditReport
          }

          history={
            creditHistory
          }

          currency={
            currency
          }

          locale={
            locale
          }

          loading={
            cbBusy ||
            cbHistoryLoading
          }

        />

      )}


      {/* ======================================================
          HERO KPIs
      ====================================================== */}

      <div className="grid grid-cols-2 lg:grid-cols-5 gap-3 mb-5 mt-5">

        {[
          {
            label: 'Principal',
            value: fc(loan.amount),
            Icon: IconCoins,
            color: '#3B82F6'
          },

          {
            label: 'Disbursed',
            value: fc(loan.disbursedAmount),
            Icon: IconSend,
            color: '#8B5CF6'
          },

          {
            label: 'Total Paid',
            value: fc(loan.totalPaid),
            Icon: IconCheckCircle,
            color: '#0D9488'
          },

          {
            label: 'Outstanding',
            value: fc(loan.outstandingBalance),
            Icon: IconClock,
            color: '#F59E0B'
          },

          {
            label: 'Penalty',
            value: fc(totalPenalty),
            Icon: IconFileText,
            color: '#6B7280'
          }

        ].map(
          ({
            label,
            value,
            Icon,
            color
          }) => (

          <div
            key={label}
            className="bg-white rounded-xl border border-gray-200 p-4"
          >

            <Icon
              className="w-5 h-5 mb-1.5"
              style={{ color }}
            />

            <div className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">
              {label}
            </div>

            <div className="text-lg font-extrabold text-gray-900 font-mono mt-0.5">
              {value}
            </div>

          </div>

        ))}

      </div>


      {/* ======================================================
          PROGRESS
      ====================================================== */}

      <Card className="mb-5">

        <CardBody>

          <div className="flex items-center justify-between mb-2">

            <span className="text-sm font-semibold text-gray-700">
              Repayment Progress
            </span>

            <span className="text-lg font-extrabold text-teal-600">
              {prog}%
            </span>

          </div>


          <div className="w-full bg-gray-100 rounded-full h-3 overflow-hidden">

            <div
              className="h-3 rounded-full transition-all duration-500"
              style={{
                width: `${prog}%`,
                background:
                  prog >= 100
                    ? '#0D9488'
                    : prog > 50
                      ? '#3B82F6'
                      : '#F59E0B'
              }}
            />

          </div>


          <div className="flex justify-between text-xs text-gray-400 mt-1.5">

            <span>
              {fc(loan.totalPaid)} paid
            </span>

            <span>
              {fc(loan.outstandingBalance)} remaining
            </span>

            <span>
              {fc(loan.totalRepayable)} total
            </span>

          </div>


          {loan.status === 'PAID' && (

            <div className="mt-2 bg-teal-50 border border-teal-200 text-teal-700 text-xs rounded-lg px-3 py-2 flex items-center gap-1.5">

              <IconCheckCircle className="w-4 h-4" />

              Loan fully repaid

            </div>

          )}

        </CardBody>

      </Card>


      {/* ======================================================
          DOCUMENT REQUIREMENTS
      ====================================================== */}

      {docReq &&
        (
          docReq.missing.length > 0 ||
          docReq.unverified.length > 0
        ) && (

        <div className="bg-amber-50 border border-amber-200 rounded-xl px-4 py-3 mb-5 text-sm">

          <div className="font-bold text-amber-800 mb-1 flex items-center gap-1.5">

            <IconAlertTriangle className="w-4 h-4" />

            Required documents not yet in order

          </div>


          {docReq.missing.length > 0 && (

            <div className="text-amber-700">

              Not uploaded
              (blocks <strong>Approve</strong>):

              {' '}

              {docReq.missing
                .map(
                  t =>
                    DOCUMENT_TYPE_LABELS[t] ??
                    t
                )
                .join(', ')}

            </div>

          )}


          {docReq.missing.length === 0 &&
            docReq.unverified.length > 0 && (

            <div className="text-amber-700">

              Uploaded but not yet staff-verified
              (blocks <strong>Disburse</strong>):

              {' '}

              {docReq.unverified
                .map(
                  t =>
                    DOCUMENT_TYPE_LABELS[t] ??
                    t
                )
                .join(', ')}

            </div>

          )}


          <button
            onClick={() =>
              setTab('Documents')
            }
            className="text-xs font-bold text-amber-800 underline mt-1"
          >
            Go to Documents →
          </button>

        </div>

      )}


      {/* ======================================================
          TABS
      ====================================================== */}

      <div className="flex border-b border-gray-200 mb-5 gap-0 overflow-x-auto">

        {TABS.map(t => (

          <button
            key={t}
            onClick={() =>
              setTab(t)
            }
            className={`px-5 py-2.5 text-sm font-semibold border-b-2 transition-colors -mb-px whitespace-nowrap
              ${
                tab === t
                  ? 'border-teal-500 text-teal-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
          >
            {t}
          </button>

        ))}

      </div>


      {/* ======================================================
          OVERVIEW
      ====================================================== */}

      {tab === 'Overview' && (

        <Card>

          <CardHeader
            title="Loan Details"
          />

          <CardBody>

            <div className="grid grid-cols-2 lg:grid-cols-4 gap-x-6 gap-y-5">

              <Field
                label="Loan Type"
                value={`${
                  LOAN_TYPE_META[
                    loan.loanType
                  ]?.icon
                } ${
                  LOAN_TYPE_META[
                    loan.loanType
                  ]?.label
                }`}
              />

              <Field
                label="Interest Rate"
                value={`${loan.interestRate}% p.a.`}
              />

              <Field
                label="Term"
                value={`${loan.durationMonths} months`}
              />

              <Field
                label="Schedule"
                value={loan.repaymentFrequency}
              />

              <Field
                label="Processing Fee"
                value={fc(loan.processingFee)}
              />

              <Field
                label="DTI Ratio"
                value={
                  loan.debtToIncomeRatio
                    ? `${loan.debtToIncomeRatio.toFixed(1)}%`
                    : undefined
                }
              />

              <Field
                label="Credit Score"
                value={loan.creditScoreSnapshot}
              />

              <Field
                label="Risk Category"
                value={loan.riskCategory}
              />

              <Field
                label="Start Date"
                value={formatDate(
                  loan.startDate,
                  locale
                )}
              />

              <Field
                label="Approved"
                value={formatDate(
                  loan.approvedAt,
                  locale
                )}
              />

              <Field
                label="Disbursed"
                value={formatDate(
                  loan.disbursedAt,
                  locale
                )}
              />

              <Field
                label="Maturity"
                value={formatDate(
                  loan.maturityDate,
                  locale
                )}
              />

              <Field
                label="Purpose"
                value={
                  <span className="whitespace-pre-wrap">
                    {loan.purpose}
                  </span>
                }
              />

              <Field
                label="Collateral"
                value={
                  loan.collateralDescription
                }
              />

              <Field
                label="Collateral Value"
                value={
                  fc(
                    loan.collateralValue
                  )
                }
              />

              <Field
                label="Currency"
                value={loan.currency}
              />

            </div>


            {(loan.rejectionReason ||
              loan.internalNotes) && (

              <>

                <hr className="my-4 border-gray-100" />

                {loan.rejectionReason && (

                  <Field
                    label="Rejection Reason"
                    value={
                      <span className="text-red-600">
                        {loan.rejectionReason}
                      </span>
                    }
                  />

                )}

                {loan.internalNotes && (

                  <div className="mt-3">

                    <Field
                      label="Internal Notes"
                      value={
                        loan.internalNotes
                      }
                    />

                  </div>

                )}

              </>

            )}


            <hr className="my-4 border-gray-100" />


            <div className="flex gap-3 flex-wrap text-xs text-gray-500">

              {loan.loanOfficer && (

                <span className="bg-gray-100 px-3 py-1.5 rounded-lg">

                  👤 Officer:

                  {' '}

                  <strong>
                    {loan.loanOfficer.name}
                  </strong>

                </span>

              )}


              {loan.approvedBy && (

                <span className="bg-gray-100 px-3 py-1.5 rounded-lg inline-flex items-center gap-1.5">

                  <IconCheckCircle className="w-3.5 h-3.5 text-teal-600" />

                  Approved by:

                  {' '}

                  <strong>
                    {loan.approvedBy.name}
                  </strong>

                </span>

              )}

            </div>

          </CardBody>

        </Card>

      )}


      {/* ======================================================
          BORROWER
      ====================================================== */}

      {tab === 'Borrower' &&
        loan.borrower && (

        <Card>

          <CardHeader
            title="Borrower Profile"
          />

          <CardBody>

            <div className="flex items-center gap-4 mb-5">

              <div className="w-14 h-14 bg-teal-100 rounded-full flex items-center justify-center text-2xl font-bold text-teal-700">

                {loan.borrower.firstName?.[0]}

                {loan.borrower.lastName?.[0]}

              </div>


              <div>

                <div className="font-bold text-lg text-gray-900">

                  {loan.borrower.firstName}

                  {' '}

                  {loan.borrower.lastName}

                </div>

                <div className="text-sm text-gray-500">

                  {loan.borrower.email}

                  {' · '}

                  {loan.borrower.phone}

                </div>

              </div>


              <div className="ml-auto flex items-center gap-6">

                <div>

                  <div className="text-xs text-gray-400 mb-0.5">
                    Credit Score
                  </div>

                  <div
                    className={`text-2xl font-extrabold ${
                      (loan.borrower.creditScore ?? 0) >= 700
                        ? 'text-teal-600'
                        : 'text-orange-500'
                    }`}
                  >

                    {loan.borrower.creditScore ?? '—'}

                  </div>

                </div>


                <button
                  onClick={() =>
                    setTab('Documents')
                  }
                  className="text-xs font-bold px-3 py-2 rounded-lg border border-teal-200 bg-teal-50 text-teal-700 hover:bg-teal-100 transition whitespace-nowrap"
                >
                  View KYC Documents →
                </button>

              </div>

            </div>


            <div className="grid grid-cols-2 lg:grid-cols-4 gap-x-6 gap-y-4">

              <Field
                label="National ID"
                value={
                  loan.borrower.nationalId
                }
              />

              <Field
                label="Nationality"
                value={
                  loan.borrower.nationality
                }
              />

              <Field
                label="Date of Birth"
                value={formatDate(
                  loan.borrower.dateOfBirth,
                  locale
                )}
              />

              <Field
                label="Gender"
                value={
                  loan.borrower.gender
                }
              />

              <Field
                label="Employer"
                value={
                  loan.borrower.employerName
                }
              />

              <Field
                label="Job Title"
                value={
                  loan.borrower.jobTitle
                }
              />

              <Field
                label="Employment Type"
                value={
                  loan.borrower.employmentType
                }
              />

              <Field
                label="Monthly Income"
                value={
                  fc(
                    loan.borrower.monthlyIncome
                  )
                }
              />

              <Field
                label="Monthly Expenses"
                value={
                  fc(
                    loan.borrower.monthlyExpenses
                  )
                }
              />

              <Field
                label="Net Worth"
                value={
                  fc(
                    loan.borrower.netWorth
                  )
                }
              />

              <Field
                label="City"
                value={
                  loan.borrower.city
                }
              />

              <Field
                label="Country"
                value={
                  loan.borrower.country
                }
              />

              <Field
                label="Bank"
                value={
                  loan.borrower.bankName
                }
              />

              <Field
                label="Account Number"
                value={
                  loan.borrower.bankAccountNumber
                }
              />

            </div>

          </CardBody>

        </Card>

      )}


      {/* ======================================================
          DOCUMENTS
      ====================================================== */}

      {tab === 'Documents' && (

        loan.borrower?.id

          ? (
            <DocumentsPanel
              borrowerId={
                loan.borrower.id
              }
              key={
                loan.borrower.id
              }
            />
          )

          : (

            <div className="bg-white rounded-xl border border-gray-200 p-8 text-center text-gray-400 text-sm">

              No borrower record is linked to this loan,
              so documents can't be shown.

            </div>

          )

      )}


      {/* ======================================================
          SCHEDULE
      ====================================================== */}

      {tab === 'Schedule' && (

        <>

          {chartData.length > 1 && (

            <Card className="mb-4">

              <CardHeader
                title="Outstanding Balance Over Time"
              />

              <CardBody>

                <ResponsiveContainer
                  width="100%"
                  height={180}
                >

                  <AreaChart
                    data={chartData}
                  >

                    <defs>

                      <linearGradient
                        id="balGrad"
                        x1="0"
                        y1="0"
                        x2="0"
                        y2="1"
                      >

                        <stop
                          offset="5%"
                          stopColor="#0D9488"
                          stopOpacity={0.15}
                        />

                        <stop
                          offset="95%"
                          stopColor="#0D9488"
                          stopOpacity={0}
                        />

                      </linearGradient>

                    </defs>


                    <CartesianGrid
                      strokeDasharray="3 3"
                      stroke="#F3F4F6"
                      vertical={false}
                    />


                    <XAxis
                      dataKey="n"
                      tick={{
                        fontSize: 11,
                        fill: '#9CA3AF'
                      }}
                    />


                    <YAxis
                      tick={{
                        fontSize: 11,
                        fill: '#9CA3AF'
                      }}
                      tickFormatter={
                        v => fc(v)
                      }
                    />


                    <Tooltip
                      formatter={
                        (v: number) =>
                          fc(v)
                      }
                    />


                    <Area
                      type="monotone"
                      dataKey="balance"
                      stroke="#0D9488"
                      fill="url(#balGrad)"
                      strokeWidth={2}
                      name="Balance"
                    />

                  </AreaChart>

                </ResponsiveContainer>

              </CardBody>

            </Card>

          )}


          <Card>

            <CardHeader
              title={`Repayment Schedule (${schedule.length} installments)`}
            />


            <Table>

              <Thead>

                <tr>

                  <Th>#</Th>
                  <Th>Due Date</Th>
                  <Th>Amount</Th>
                  <Th>Principal</Th>
                  <Th>Interest</Th>
                  <Th>Penalty</Th>
                  <Th>Balance After</Th>
                  <Th>Status</Th>
                  <Th>Paid Date</Th>
                  <Th>Method</Th>

                </tr>

              </Thead>


              <Tbody>

                {schedule.length === 0

                  ? (

                    <Tr>

                      <Td className="text-center py-10 text-gray-400">

                        No schedule generated yet

                      </Td>

                    </Tr>

                  )

                  : schedule.map(
                      p => (

                        <Tr
                          key={p.id}
                          className={
                            p.isLate
                              ? 'bg-orange-50'
                              : ''
                          }
                        >

                          <Td className="font-mono text-xs text-gray-500">
                            {p.installmentNumber}
                          </Td>

                          <Td>
                            {formatDate(
                              p.dueDate,
                              locale
                            )}
                          </Td>

                          <Td className="font-semibold">
                            {fc(p.amount)}
                          </Td>

                          <Td className="text-blue-600">
                            {fc(
                              p.principalComponent
                            )}
                          </Td>

                          <Td className="text-purple-600">
                            {fc(
                              p.interestComponent
                            )}
                          </Td>

                          <Td className="text-red-500">

                            {p.penalty &&
                            p.penalty > 0
                              ? fc(p.penalty)
                              : '—'}

                          </Td>

                          <Td>
                            {fc(
                              p.outstandingAfter
                            )}
                          </Td>

                          <Td>

                            {p.paid

                              ? (

                                <span className="inline-flex items-center gap-1 text-xs font-semibold text-teal-700 bg-teal-50 px-2 py-0.5 rounded-full">

                                  ✓ Paid

                                </span>

                              )

                              : p.isLate

                                ? (

                                  <span className="inline-flex items-center gap-1 text-xs font-semibold text-orange-700 bg-orange-50 px-2 py-0.5 rounded-full">

                                    ⚠ Overdue

                                  </span>

                                )

                                : (

                                  <span className="inline-flex items-center gap-1 text-xs font-semibold text-gray-500 bg-gray-50 px-2 py-0.5 rounded-full">

                                    Pending

                                  </span>

                                )}

                          </Td>

                          <Td className="text-gray-400 text-xs">

                            {formatDate(
                              p.paidDate,
                              locale
                            )}

                          </Td>

                          <Td className="text-xs">

                            {p.paid &&
                            p.paymentMethod

                              ? (

                                <span
                                  title={
                                    p.transactionId
                                      ? `Ref: ${p.transactionId}`
                                      : ''
                                  }
                                  className="text-gray-600 font-medium"
                                >

                                  {p.paymentMethod.replace(
                                    /_/g,
                                    ' '
                                  )}

                                </span>

                              )

                              : (

                                <span className="text-gray-300">
                                  —
                                </span>

                              )}

                          </Td>

                        </Tr>

                      )
                    )}

              </Tbody>

            </Table>

          </Card>

        </>

      )}


      {/* ======================================================
          TIMELINE
      ====================================================== */}

      {tab === 'Timeline' && (

        <Card>

          <CardHeader
            title="Loan Timeline"
          />

          <CardBody>

            <div className="relative">

              {[
                {
                  icon: IconFileEdit,
                  label: 'Application Submitted',
                  date: loan.startDate,
                  done: true
                },

                {
                  icon: IconSearch,
                  label: 'Under Review',
                  date: loan.startDate,
                  done:
                    loan.status !== 'PENDING'
                },

                {
                  icon: IconCheckCircle,
                  label: 'Approved',
                  date: loan.approvedAt,
                  done:
                    !!loan.approvedAt
                },

                {
                  icon: IconCoins,
                  label: 'Disbursed',
                  date: loan.disbursedAt,
                  done:
                    !!loan.disbursedAt
                },

                {
                  icon: IconCalendar,
                  label: 'Next Payment Due',
                  date: loan.nextDueDate,
                  done: false
                },

                {
                  icon: IconFlag,
                  label: 'Maturity Date',
                  date: loan.maturityDate,
                  done:
                    loan.status === 'PAID'
                }

              ].map(
                (
                  step,
                  i,
                  arr
                ) => (

                  <div
                    key={i}
                    className="flex gap-4 pb-6 relative"
                  >

                    <div className="flex flex-col items-center">

                      <div
                        className={`w-9 h-9 rounded-full flex items-center justify-center text-lg border-2 z-10 ${
                          step.done
                            ? 'bg-teal-500 border-teal-500 text-white'
                            : 'bg-white border-gray-200 text-gray-400'
                        }`}
                      >

                        <step.icon className="w-4 h-4" />

                      </div>


                      {i < arr.length - 1 && (

                        <div
                          className={`w-0.5 flex-1 mt-1 ${
                            step.done
                              ? 'bg-teal-300'
                              : 'bg-gray-200'
                          }`}
                          style={{
                            minHeight: 28
                          }}
                        />

                      )}

                    </div>


                    <div className="pt-1.5">

                      <div
                        className={`font-semibold text-sm ${
                          step.done
                            ? 'text-gray-900'
                            : 'text-gray-400'
                        }`}
                      >
                        {step.label}
                      </div>

                      <div className="text-xs text-gray-400 mt-0.5">

                        {formatDate(
                          step.date,
                          locale
                        )}

                      </div>

                    </div>

                  </div>

                )
              )}

            </div>

          </CardBody>

        </Card>

      )}


      {/* ======================================================
          COMMENTS
      ====================================================== */}

      {tab === 'Comments' && (

        <Card>

          <CardHeader
            title="Comments & Document Requests"
          />

          <CardBody>

            <div className="mb-6 bg-gray-50 rounded-xl p-4">

              <Textarea
                placeholder="e.g. Please upload your land title document, or a recent utility bill as proof of address."
                value={commentText}
                onChange={e =>
                  setCommentText(
                    e.target.value
                  )
                }
                rows={3}
              />


              <div className="flex items-center justify-between mt-3">

                <label className="flex items-center gap-2 text-sm text-gray-600 cursor-pointer">

                  <input
                    type="checkbox"
                    checked={
                      commentVisible
                    }
                    onChange={e =>
                      setCommentVisible(
                        e.target.checked
                      )
                    }
                  />

                  Visible to applicant
                  on the tracking page

                </label>


                <Button
                  loading={
                    commentSaving
                  }
                  disabled={
                    !commentText.trim()
                  }
                  onClick={
                    handleAddComment
                  }
                >
                  Post
                </Button>

              </div>


              {!commentVisible && (

                <p className="text-xs text-amber-600 mt-2">

                  This note will be internal-only —
                  the applicant won't see it.

                </p>

              )}

            </div>


            {comments.length === 0 && (

              <p className="text-sm text-gray-400 text-center py-6">

                No comments yet.

              </p>

            )}


            <div className="space-y-4">

              {comments
                .slice()
                .reverse()
                .map(
                  (c: any) => (

                    <div
                      key={c.id}
                      className="flex gap-3"
                    >

                      <div className="w-8 h-8 rounded-full bg-teal-100 text-teal-700 flex items-center justify-center text-xs font-bold shrink-0">

                        {(c.author?.name ||
                          'S')[0]}

                      </div>


                      <div className="flex-1 min-w-0">

                        <div className="flex items-center gap-2 flex-wrap">

                          <span className="text-sm font-semibold text-gray-800">

                            {c.author?.name ||
                              'Staff'}

                          </span>


                          <span className="text-xs text-gray-400">

                            {formatDate(
                              c.createdAt,
                              locale
                            )}

                          </span>


                          {c.visibleToApplicant

                            ? (

                              <Pill
                                label="Visible to applicant"
                                color="blue"
                              />

                            )

                            : (

                              <Pill
                                label="Internal only"
                                color="gray"
                              />

                            )}

                        </div>


                        <p className="text-sm text-gray-700 mt-1">

                          {c.message}

                        </p>

                      </div>

                    </div>

                  )
                )}

            </div>

          </CardBody>

        </Card>

      )}


      {/* ======================================================
          PAYMENT MODAL
      ====================================================== */}

      <Modal
        open={payOpen}
        onClose={() =>
          setPayOpen(false)
        }
        title="Record Payment"

        footer={

          <>

            <Button
              variant="secondary"
              onClick={() =>
                setPayOpen(false)
              }
            >
              Cancel
            </Button>

            <Button
              loading={paying}
              onClick={
                handlePay as any
              }
            >
              Confirm Payment
            </Button>

          </>

        }
      >

        <form
          onSubmit={handlePay}
        >

          <div className="bg-gray-50 rounded-xl p-4 mb-4 grid grid-cols-2 gap-3 text-sm">

            {[
              [
                'Outstanding',
                fc(
                  loan.outstandingBalance
                )
              ],

              [
                'Next Due',
                formatDate(
                  loan.nextDueDate,
                  locale
                )
              ],

              [
                'Penalty',
                fc(0)
              ],

              [
                'Currency',
                loan.currency
              ]

            ].map(
              ([l, v]) => (

                <div key={l}>

                  <div className="text-xs text-gray-400">
                    {l}
                  </div>

                  <div className="font-bold">
                    {v}
                  </div>

                </div>

              )
            )}

          </div>


          <div className="grid grid-cols-2 gap-4">

            <FormGroup
              label="Amount"
              required
            >

              <Input
                type="number"
                min="1"
                required
                value={
                  payForm.amount
                }
                onChange={e =>
                  setPayForm(
                    f => ({
                      ...f,
                      amount:
                        e.target.value
                    })
                  )
                }
              />

            </FormGroup>


            <FormGroup
              label="Method"
              required
            >

              <Select
                value={
                  payForm.paymentMethod
                }
                onChange={e =>
                  setPayForm(
                    f => ({
                      ...f,
                      paymentMethod:
                        e.target.value
                    })
                  )
                }
              >

                {[
                  'BANK_TRANSFER',
                  'MOBILE_MONEY',
                  'CASH',
                  'CARD',
                  'CHEQUE',
                  'DIRECT_DEBIT'
                ].map(
                  m => (

                    <option
                      key={m}
                    >
                      {m.replace(
                        /_/g,
                        ' '
                      )}
                    </option>

                  )
                )}

              </Select>

            </FormGroup>


            <FormGroup
              label="Transaction ID"
            >

              <Input
                placeholder="e.g. MPesa code"
                value={
                  payForm.transactionId
                }
                onChange={e =>
                  setPayForm(
                    f => ({
                      ...f,
                      transactionId:
                        e.target.value
                    })
                  )
                }
              />

            </FormGroup>


            <FormGroup
              label="Channel"
            >

              <Input
                placeholder="e.g. Mobile, Branch"
                value={
                  payForm.channel
                }
                onChange={e =>
                  setPayForm(
                    f => ({
                      ...f,
                      channel:
                        e.target.value
                    })
                  )
                }
              />

            </FormGroup>

          </div>


          <FormGroup
            label="Notes"
          >

            <Textarea
              value={
                payForm.notes
              }
              onChange={e =>
                setPayForm(
                  f => ({
                    ...f,
                    notes:
                      e.target.value
                  })
                )
              }
            />

          </FormGroup>

        </form>

      </Modal>


      {/* ======================================================
          STATUS MODAL
      ====================================================== */}

      <Modal
        open={stOpen}
        onClose={() =>
          setStOpen(false)
        }
        title="Update Loan Status"

        footer={

          <>

            <Button
              variant="secondary"
              onClick={() =>
                setStOpen(false)
              }
            >
              Cancel
            </Button>

            <Button
              loading={stSaving}
              onClick={
                handleStatus as any
              }
            >
              Update
            </Button>

          </>

        }
      >

        <form
          onSubmit={handleStatus}
        >

          <div className="bg-gray-50 rounded-xl p-3 mb-4 text-sm flex items-center gap-2">

            Current:

            <StatusBadge
              status={loan.status}
            />

          </div>


          <FormGroup
            label="New Status"
            required
          >

            <Select
              value={
                stForm.status
              }
              onChange={e =>
                setStForm(
                  f => ({
                    ...f,
                    status:
                      e.target.value
                  })
                )
              }
              required
            >

              <option value="">
                Select status…
              </option>


              {(() => {

                const VALID_FROM:
                  Record<
                    string,
                    string[]
                  > = {

                    PENDING: [
                      'UNDER_REVIEW',
                      'APPROVED',
                      'REJECTED'
                    ],

                    UNDER_REVIEW: [
                      'APPROVED',
                      'REJECTED'
                    ],

                    APPROVED: [
                      'DISBURSED'
                    ],

                    ACTIVE: [
                      'DEFAULTED'
                    ],

                    OVERDUE: [
                      'DEFAULTED'
                    ],

                    PAID: [
                      'CLOSED'
                    ],

                    WRITTEN_OFF: [
                      'CLOSED'
                    ]

                  };


                const options =
                  VALID_FROM[
                    loan.status
                  ] ?? [];


                if (
                  options.length === 0
                ) {

                  return (

                    <option disabled>

                      No status changes
                      available from{' '}

                      {loan.status.replace(
                        /_/g,
                        ' '
                      )}

                    </option>

                  );

                }


                return options.map(
                  s => (

                    <option
                      key={s}
                      value={s}
                    >

                      {s.replace(
                        /_/g,
                        ' '
                      )}

                    </option>

                  )
                );

              })()}

            </Select>

          </FormGroup>


          {stForm.status ===
            'APPROVED' && (

            <FormGroup
              label={`Interest Rate ${
                loan.interestRateType ===
                'MONTHLY'
                  ? '(monthly)'
                  : '(annual)'
              }`}
            >

              <p className="text-xs text-gray-400 mb-2">

                Applied on the website at{' '}

                <strong>
                  {loan.interestRate}%
                </strong>.

                Loans are flexible —
                adjust it here if this
                borrower should get a
                different rate; leave it
                as-is to keep the original.

              </p>


              <div className="flex gap-2 flex-wrap">

                {[
                  '6',
                  '8',
                  '10'
                ].map(
                  r => (

                    <button
                      key={r}
                      type="button"
                      onClick={() =>
                        setStForm(
                          f => ({
                            ...f,
                            interestRate:
                              r
                          })
                        )
                      }
                      className={`px-4 py-2 rounded-lg text-sm font-semibold border transition-colors ${
                        stForm.interestRate ===
                        r
                          ? 'bg-teal-600 text-white border-teal-600'
                          : 'bg-white text-gray-700 border-gray-200 hover:bg-gray-50'
                      }`}
                    >
                      {r}%
                    </button>

                  )
                )}


                <button
                  type="button"
                  onClick={() =>
                    setStForm(
                      f => ({
                        ...f,
                        interestRate:
                          ''
                      })
                    )
                  }
                  className={`px-4 py-2 rounded-lg text-sm font-semibold border transition-colors ${
                    stForm.interestRate ===
                    ''
                      ? 'bg-teal-600 text-white border-teal-600'
                      : 'bg-white text-gray-700 border-gray-200 hover:bg-gray-50'
                  }`}
                >

                  Keep{' '}
                  {loan.interestRate}%

                </button>


                <input
                  type="number"
                  step="0.1"
                  min="0"
                  placeholder="Custom %"
                  value={
                    [
                      '6',
                      '8',
                      '10',
                      ''
                    ].includes(
                      stForm.interestRate
                    )
                      ? ''
                      : stForm.interestRate
                  }
                  onChange={e =>
                    setStForm(
                      f => ({
                        ...f,
                        interestRate:
                          e.target.value
                      })
                    )
                  }
                  className="w-28 px-3 py-2 rounded-lg text-sm border border-gray-200 focus:outline-none focus:ring-2 focus:ring-teal-500"
                />

              </div>

            </FormGroup>

          )}


          {stForm.status ===
            'REJECTED' && (

            <FormGroup
              label="Rejection Reason"
              required
            >

              <Textarea
                required
                value={
                  stForm.rejectionReason
                }
                onChange={e =>
                  setStForm(
                    f => ({
                      ...f,
                      rejectionReason:
                        e.target.value
                    })
                  )
                }
              />

            </FormGroup>

          )}


          <FormGroup
            label="Internal Notes"
          >

            <Textarea
              placeholder="For internal records only"
              value={
                stForm.internalNotes
              }
              onChange={e =>
                setStForm(
                  f => ({
                    ...f,
                    internalNotes:
                      e.target.value
                  })
                )
              }
            />

          </FormGroup>

        </form>

      </Modal>

    </div>
  );
}
