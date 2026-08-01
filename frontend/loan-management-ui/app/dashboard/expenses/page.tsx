
'use client';

import { useEffect, useState, useCallback } from 'react';
import { expenseApi, bankAccountApi, branchApi } from '@/services/api';
import { PageSpinner } from '@/components/ui/Skeleton';
import { useAuth } from '@/hooks/useAuth';

const CATEGORIES = [
  { value: 'SALARIES_AND_WAGES',       label: 'Salaries and Wages' },
  { value: 'RENT',                     label: 'Rent' },
  { value: 'UTILITIES',               label: 'Utilities' },
  { value: 'INTERNET',                label: 'Internet' },
  { value: 'TRANSPORT',               label: 'Transport' },
  { value: 'FUEL',                    label: 'Fuel' },
  { value: 'OFFICE_SUPPLIES',         label: 'Office Supplies' },
  { value: 'BANK_CHARGES',            label: 'Bank Charges' },
  { value: 'INSURANCE',               label: 'Insurance' },
  { value: 'MARKETING',               label: 'Marketing' },
  { value: 'LEGAL_FEES',              label: 'Legal Fees' },
  { value: 'AUDIT_FEES',              label: 'Audit Fees' },
  { value: 'DEPRECIATION',             label: 'Depreciation' },
  { value: 'LOAN_RECOVERY_EXPENSES',   label: 'Loan Recovery Expenses' },
  { value: 'IT_EXPENSES',             label: 'IT Expenses' },
  { value: 'OTHER_OPERATING_EXPENSES', label: 'Other Operating Expenses' },
];

const PAYMENT_METHODS = [
  { value: 'CASH', label: 'Cash' },
  { value: 'BANK_ACCOUNT', label: 'Bank Account' },
  { value: 'MOBILE_MONEY', label: 'Mobile Money' },
  { value: 'MOMO_PAY', label: 'MoMo Pay' },
  { value: 'CARD', label: 'Card' },
  { value: 'CHEQUE', label: 'Cheque' },
  { value: 'OTHER', label: 'Other' },
];

interface BankAccountRow {
  id: number;
  name: string;
  accountType: string;
  active: boolean;
}

interface BranchRow {
  id: number;
  name: string;
}

interface ExpenseRow {
  id: number;
  expenseDate: string;
  category: string;
  amount: number;
  currency: string;

  description?: string;

  status: 'POSTED' | 'VOID';

  paymentAccount?: {
    id: number;
    name: string;
  };

  paymentMethod?: string;
  paymentProvider?: string;
  paymentPhoneNumber?: string;
  paymentTransactionReference?: string;
  paymentCode?: string;

  cardBrand?: string;
  cardLastFour?: string;
  cardAuthorizationCode?: string;

  chequeNumber?: string;
  paymentNotes?: string;

  branch?: {
    id: number;
    name: string;
  };

  createdByName?: string;
  receiptFileName?: string;
}

const categoryLabel = (value: string) =>
  CATEGORIES.find(c => c.value === value)?.label || value;

const paymentMethodLabel = (value?: string) =>
  PAYMENT_METHODS.find(p => p.value === value)?.label || value || '—';


export default function ExpensesPage() {

  const { currency } = useAuth();

  const [expenses, setExpenses] = useState<ExpenseRow[]>([]);
  const [bankAccounts, setBankAccounts] = useState<BankAccountRow[]>([]);
  const [branches, setBranches] = useState<BranchRow[]>([]);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [showForm, setShowForm] = useState(false);

  const [filterCategory, setFilterCategory] = useState('');


  const load = useCallback(() => {

    setLoading(true);
    setError('');

    Promise.all([

      expenseApi
        .list({
          category: filterCategory || undefined
        })
        .catch(() => ({ content: [] })),

      bankAccountApi
        .list()
        .catch(() => []),

      branchApi
        .list()
        .catch(() => [])

    ])
      .then(([exp, ba, br]) => {

        setExpenses(
          (((exp as any)?.content ?? exp ?? []) as ExpenseRow[])
        );

        setBankAccounts(
          ba as BankAccountRow[]
        );

        setBranches(
          br as BranchRow[]
        );

      })
      .catch(() => {
        setError('Could not load expenses.');
      })
      .finally(() => {
        setLoading(false);
      });

  }, [filterCategory]);


  useEffect(() => {
    load();
  }, [load]);


  const handleVoid = async (id: number) => {

    const reason =
      window.prompt(
        'Reason for voiding this expense (optional):'
      ) ?? '';

    try {

      await expenseApi.void(
        id,
        reason || undefined
      );

      load();

    } catch (e) {

      alert(
        e instanceof Error
          ? e.message
          : 'Could not void expense'
      );

    }
  };


  const fmt = (n: number) =>
    new Intl.NumberFormat('en-US', {
      maximumFractionDigits: 0
    }).format(n || 0);


  if (loading) {
    return <PageSpinner />;
  }


  const total =
    expenses
      .filter(e => e.status === 'POSTED')
      .reduce(
        (sum, e) => sum + e.amount,
        0
      );


  return (
    <div>

      {/* ============================================================
          HEADER
      ============================================================ */}

      <div className="flex items-center justify-between mb-6">

        <div>

          <h1 className="text-xl font-bold text-gray-900">
            Expenses
          </h1>

          <p className="text-sm text-gray-500 mt-1">
            {expenses.length} record
            {expenses.length === 1 ? '' : 's'}
            {' · '}
            {currency} {fmt(total)} total posted
          </p>

        </div>


        <button
          onClick={() => setShowForm(true)}
          className="px-4 py-2.5 bg-teal-600 hover:bg-teal-700 text-white text-sm font-semibold rounded-lg transition"
        >
          + Add Expense
        </button>

      </div>


      {/* ============================================================
          ERROR
      ============================================================ */}

      {error && (

        <div className="mb-4 px-4 py-3 bg-red-50 text-red-700 text-sm rounded-lg border border-red-200">

          {error}

        </div>

      )}


      {/* ============================================================
          FILTER
      ============================================================ */}

      <div className="mb-4">

        <select
          value={filterCategory}
          onChange={e => setFilterCategory(e.target.value)}
          className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >

          <option value="">
            All categories
          </option>

          {CATEGORIES.map(c => (

            <option
              key={c.value}
              value={c.value}
            >
              {c.label}
            </option>

          ))}

        </select>

      </div>


      {/* ============================================================
          EXPENSE TABLE
      ============================================================ */}

      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">

        <div className="overflow-x-auto">

          <table className="w-full text-sm">

            <thead className="bg-gray-50 text-left text-xs font-semibold text-gray-500 uppercase">

              <tr>

                <th className="px-4 py-3">
                  Date
                </th>

                <th className="px-4 py-3">
                  Type
                </th>

                <th className="px-4 py-3">
                  Amount
                </th>

                <th className="px-4 py-3">
                  Payment Account
                </th>

                <th className="px-4 py-3">
                  Payment Method
                </th>

                <th className="px-4 py-3">
                  Payment Reference
                </th>

                <th className="px-4 py-3">
                  Branch
                </th>

                <th className="px-4 py-3">
                  Description
                </th>

                <th className="px-4 py-3">
                  Status
                </th>

                <th className="px-4 py-3">
                  Created By
                </th>

                <th className="px-4 py-3">
                </th>

              </tr>

            </thead>


            <tbody className="divide-y divide-gray-100">

              {expenses.map(e => (

                <tr
                  key={e.id}
                  className={
                    e.status === 'VOID'
                      ? 'opacity-50'
                      : ''
                  }
                >

                  <td className="px-4 py-3 whitespace-nowrap">
                    {e.expenseDate}
                  </td>


                  <td className="px-4 py-3">
                    {categoryLabel(e.category)}
                  </td>


                  <td className="px-4 py-3 font-semibold whitespace-nowrap">
                    {e.currency} {fmt(e.amount)}
                  </td>


                  <td className="px-4 py-3">
                    {e.paymentAccount?.name ?? '—'}
                  </td>


                  <td className="px-4 py-3">
                    {paymentMethodLabel(e.paymentMethod)}

                    {e.paymentProvider && (
                      <div className="text-xs text-gray-400 mt-0.5">
                        {e.paymentProvider}
                      </div>
                    )}
                  </td>


                  <td className="px-4 py-3">

                    {e.paymentTransactionReference ? (

                      <div>

                        <div className="font-medium">
                          {e.paymentTransactionReference}
                        </div>

                        {e.paymentPhoneNumber && (
                          <div className="text-xs text-gray-400">
                            {e.paymentPhoneNumber}
                          </div>
                        )}

                      </div>

                    ) : e.paymentCode ? (

                      <span>
                        Code: {e.paymentCode}
                      </span>

                    ) : e.cardLastFour ? (

                      <span>
                        Card **** {e.cardLastFour}
                      </span>

                    ) : e.chequeNumber ? (

                      <span>
                        Cheque #{e.chequeNumber}
                      </span>

                    ) : (

                      '—'

                    )}

                  </td>


                  <td className="px-4 py-3">
                    {e.branch?.name ?? 'Head Office'}
                  </td>


                  <td className="px-4 py-3 text-gray-500 max-w-xs truncate">
                    {e.description ?? '—'}
                  </td>


                  <td className="px-4 py-3">

                    <span
                      className={`text-xs font-semibold px-2 py-0.5 rounded-full ${
                        e.status === 'POSTED'
                          ? 'bg-green-50 text-green-700'
                          : 'bg-gray-100 text-gray-500'
                      }`}
                    >
                      {e.status}
                    </span>

                  </td>


                  <td className="px-4 py-3 text-gray-500">
                    {e.createdByName ?? '—'}
                  </td>


                  <td className="px-4 py-3 text-right space-x-3 whitespace-nowrap">

                    {e.receiptFileName && (

                      <a
                        href={expenseApi.receiptUrl(e.id)}
                        target="_blank"
                        rel="noreferrer"
                        className="text-xs text-blue-600 hover:underline"
                      >
                        Receipt
                      </a>

                    )}


                    {e.status === 'POSTED' && (

                      <button
                        onClick={() => handleVoid(e.id)}
                        className="text-xs text-red-600 hover:underline"
                      >
                        Void
                      </button>

                    )}

                  </td>

                </tr>

              ))}


              {expenses.length === 0 && (

                <tr>

                  <td
                    colSpan={11}
                    className="px-4 py-10 text-center text-gray-400"
                  >
                    No expenses recorded yet.
                  </td>

                </tr>

              )}

            </tbody>

          </table>

        </div>

      </div>


      {/* ============================================================
          ADD EXPENSE MODAL
      ============================================================ */}

      {showForm && (

        <AddExpenseModal
          bankAccounts={bankAccounts}
          branches={branches}

          onClose={() => setShowForm(false)}

          onSaved={() => {
            setShowForm(false);
            load();
          }}
        />

      )}

    </div>
  );
}


/* ==================================================================
   ADD EXPENSE MODAL
================================================================== */

function AddExpenseModal({
  bankAccounts,
  branches,
  onClose,
  onSaved
}: {
  bankAccounts: BankAccountRow[];
  branches: BranchRow[];
  onClose: () => void;
  onSaved: () => void;
}) {

  const [expenseDate, setExpenseDate] =
    useState(
      new Date()
        .toISOString()
        .slice(0, 10)
    );

  const [category, setCategory] =
    useState('OFFICE_SUPPLIES');

  const [amount, setAmount] =
    useState('');

  const [paymentAccountId, setPaymentAccountId] =
    useState('');

  const [branchId, setBranchId] =
    useState('');

  const [description, setDescription] =
    useState('');

  const [paymentMethod, setPaymentMethod] =
    useState('');


  const [paymentProvider, setPaymentProvider] =
    useState('');

  const [paymentPhoneNumber, setPaymentPhoneNumber] =
    useState('');

  const [paymentTransactionReference, setPaymentTransactionReference] =
    useState('');

  const [paymentCode, setPaymentCode] =
    useState('');


  const [cardBrand, setCardBrand] =
    useState('');

  const [cardLastFour, setCardLastFour] =
    useState('');

  const [cardAuthorizationCode, setCardAuthorizationCode] =
    useState('');


  const [chequeNumber, setChequeNumber] =
    useState('');

  const [paymentNotes, setPaymentNotes] =
    useState('');


  const [receipt, setReceipt] =
    useState<File | null>(null);

  const [saving, setSaving] =
    useState(false);

  const [error, setError] =
    useState('');


  /* ================================================================
     SUBMIT
  ================================================================ */

  const handleSubmit = async (
    e: React.FormEvent
  ) => {

    e.preventDefault();

    setError('');


    if (!paymentAccountId) {

      setError(
        'Select the bank, cash, or payment account used to pay this expense.'
      );

      return;
    }


    if (!amount || Number(amount) <= 0) {

      setError(
        'Enter a valid expense amount.'
      );

      return;
    }


    if (!paymentMethod) {

      setError(
        'Select how this expense was paid.'
      );

      return;
    }


    /* --------------------------------------------------------------
       MOBILE MONEY VALIDATION
    -------------------------------------------------------------- */

    if (
      paymentMethod === 'MOBILE_MONEY' ||
      paymentMethod === 'MOMO_PAY'
    ) {

      if (!paymentProvider) {

        setError(
          'Select the mobile money provider.'
        );

        return;
      }


      if (!paymentTransactionReference) {

        setError(
          'Enter the transaction reference.'
        );

        return;
      }

      if (
        paymentMethod === 'MOBILE_MONEY' &&
        !paymentPhoneNumber
      ) {

        setError(
          'Enter the phone number used for the payment.'
        );

        return;
      }

    }


    /* --------------------------------------------------------------
       CARD VALIDATION
    -------------------------------------------------------------- */

    if (paymentMethod === 'CARD') {

      if (!cardBrand) {

        setError(
          'Select the card brand.'
        );

        return;
      }


      if (
        !cardLastFour ||
        cardLastFour.length !== 4
      ) {

        setError(
          'Enter the last four digits of the card.'
        );

        return;
      }

    }


    /* --------------------------------------------------------------
       CHEQUE VALIDATION
    -------------------------------------------------------------- */

    if (
      paymentMethod === 'CHEQUE' &&
      !chequeNumber
    ) {

      setError(
        'Enter the cheque number.'
      );

      return;
    }


    setSaving(true);


    try {

      await expenseApi.create({

        expenseDate,

        category,

        amount: Number(amount),

        paymentAccountId:
          Number(paymentAccountId),

        branchId:
          branchId
            ? Number(branchId)
            : undefined,

        description:
          description || undefined,


        /* PAYMENT */

        paymentMethod,

        paymentProvider:
          paymentProvider || undefined,

        paymentPhoneNumber:
          paymentPhoneNumber || undefined,

        paymentTransactionReference:
          paymentTransactionReference || undefined,

        paymentCode:
          paymentCode || undefined,


        /* CARD */

        cardBrand:
          cardBrand || undefined,

        cardLastFour:
          cardLastFour || undefined,

        cardAuthorizationCode:
          cardAuthorizationCode || undefined,


        /* CHEQUE */

        chequeNumber:
          chequeNumber || undefined,


        /* OTHER */

        paymentNotes:
          paymentNotes || undefined,


        receipt

      });


      onSaved();


    } catch (err) {

      setError(
        err instanceof Error
          ? err.message
          : 'Could not record expense'
      );

    } finally {

      setSaving(false);

    }
  };


  /* ================================================================
     INPUT STYLING
  ================================================================ */

  const inputClass =
    "w-full px-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500";

  const labelClass =
    "block text-sm font-medium text-gray-700 mb-1.5";


  return (

    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">

      <div className="bg-white rounded-xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">

        <form
          onSubmit={handleSubmit}
          className="p-6 space-y-5"
        >

          <div>

            <h2 className="text-lg font-bold text-gray-900">
              Add Expense
            </h2>

            <p className="text-sm text-gray-500 mt-1">
              Record the expense and how it was paid.
            </p>

          </div>


          {/* ======================================================
              ERROR
          ====================================================== */}

          {error && (

            <div className="px-3 py-2 bg-red-50 text-red-700 text-sm rounded-lg border border-red-200">
              {error}
            </div>

          )}


          {/* ======================================================
              BASIC EXPENSE INFORMATION
          ====================================================== */}

          <div className="border-b border-gray-200 pb-5">

            <h3 className="text-sm font-semibold text-gray-900 mb-4">
              Expense Information
            </h3>


            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">

              <div>

                <label className={labelClass}>
                  Date *
                </label>

                <input
                  type="date"
                  required
                  value={expenseDate}
                  onChange={e =>
                    setExpenseDate(e.target.value)
                  }
                  className={inputClass}
                />

              </div>


              <div>

                <label className={labelClass}>
                  Expense Type *
                </label>

                <select
                  required
                  value={category}
                  onChange={e =>
                    setCategory(e.target.value)
                  }
                  className={inputClass}
                >

                  {CATEGORIES.map(c => (

                    <option
                      key={c.value}
                      value={c.value}
                    >
                      {c.label}
                    </option>

                  ))}

                </select>

              </div>


              <div>

                <label className={labelClass}>
                  Amount *
                </label>

                <input
                  type="number"
                  min="0.01"
                  step="0.01"
                  required
                  value={amount}
                  onChange={e =>
                    setAmount(e.target.value)
                  }
                  placeholder="0.00"
                  className={inputClass}
                />

              </div>


              <div>

                <label className={labelClass}>
                  Branch
                </label>

                <select
                  value={branchId}
                  onChange={e =>
                    setBranchId(e.target.value)
                  }
                  className={inputClass}
                >

                  <option value="">
                    Head Office / Org-wide
                  </option>

                  {branches.map(b => (

                    <option
                      key={b.id}
                      value={b.id}
                    >
                      {b.name}
                    </option>

                  ))}

                </select>

              </div>

            </div>

          </div>


          {/* ======================================================
              PAYMENT ACCOUNT
          ====================================================== */}

          <div className="border-b border-gray-200 pb-5">

            <h3 className="text-sm font-semibold text-gray-900 mb-4">
              Payment Information
            </h3>


            <div>

              <label className={labelClass}>
                Payment Account *
              </label>

              <select
                required
                value={paymentAccountId}
                onChange={e =>
                  setPaymentAccountId(e.target.value)
                }
                className={inputClass}
              >

                <option value="">
                  Select account...
                </option>

                {bankAccounts
                  .filter(a => a.active)
                  .map(a => (

                    <option
                      key={a.id}
                      value={a.id}
                    >
                      {a.name} ({a.accountType})
                    </option>

                  ))}

              </select>


              {bankAccounts.length === 0 && (

                <p className="text-xs text-amber-600 mt-1">

                  No bank/cash accounts set up yet —
                  create one under Accounting → Bank Accounts first.

                </p>

              )}

            </div>


            <div className="mt-4">

              <label className={labelClass}>
                Payment Method *
              </label>

              <select
                required
                value={paymentMethod}
                onChange={e => {

                  setPaymentMethod(e.target.value);

                  /*
                   * Clear method-specific information
                   * when payment method changes.
                   */

                  setPaymentProvider('');
                  setPaymentPhoneNumber('');
                  setPaymentTransactionReference('');
                  setPaymentCode('');

                  setCardBrand('');
                  setCardLastFour('');
                  setCardAuthorizationCode('');

                  setChequeNumber('');
                  setPaymentNotes('');

                }}
                className={inputClass}
              >

                <option value="">
                  Select payment method...
                </option>

                {PAYMENT_METHODS.map(method => (

                  <option
                    key={method.value}
                    value={method.value}
                  >
                    {method.label}
                  </option>

                ))}

              </select>

            </div>


            {/* ==================================================
                MOBILE MONEY
            ================================================== */}

            {(
              paymentMethod === 'MOBILE_MONEY' ||
              paymentMethod === 'MOMO_PAY'
            ) && (

              <div className="mt-4 p-4 bg-gray-50 rounded-lg border border-gray-200">

                <h4 className="text-sm font-semibold text-gray-800 mb-3">
                  Mobile Payment Details
                </h4>


                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">

                  <div>

                    <label className={labelClass}>
                      Provider *
                    </label>

                    <select
                      required
                      value={paymentProvider}
                      onChange={e =>
                        setPaymentProvider(e.target.value)
                      }
                      className={inputClass}
                    >

                      <option value="">
                        Select provider...
                      </option>

                      <option value="MTN">
                        MTN
                      </option>

                      <option value="AIRTEL">
                        Airtel
                      </option>

                    </select>

                  </div>


                  {paymentMethod === 'MOBILE_MONEY' && (

                    <div>

                      <label className={labelClass}>
                        Phone Number *
                      </label>

                      <input
                        type="tel"
                        required
                        value={paymentPhoneNumber}
                        onChange={e =>
                          setPaymentPhoneNumber(e.target.value)
                        }
                        placeholder="e.g. 078XXXXXXX"
                        className={inputClass}
                      />

                    </div>

                  )}


                  {paymentMethod === 'MOMO_PAY' && (

                    <div>

                      <label className={labelClass}>
                        MoMo Pay Code
                      </label>

                      <input
                        type="text"
                        value={paymentCode}
                        onChange={e =>
                          setPaymentCode(e.target.value)
                        }
                        placeholder="Merchant / payment code"
                        className={inputClass}
                      />

                    </div>

                  )}


                  <div className="md:col-span-2">

                    <label className={labelClass}>
                      Transaction Reference *
                    </label>

                    <input
                      type="text"
                      required
                      value={paymentTransactionReference}
                      onChange={e =>
                        setPaymentTransactionReference(
                          e.target.value
                        )
                      }
                      placeholder="e.g. transaction ID / reference"
                      className={inputClass}
                    />

                    <p className="text-xs text-gray-500 mt-1">
                      Enter the transaction ID/reference shown by
                      the mobile money provider.
                    </p>

                  </div>

                </div>

              </div>

            )}


            {/* ==================================================
                BANK ACCOUNT
            ================================================== */}

            {paymentMethod === 'BANK_ACCOUNT' && (

              <div className="mt-4 p-4 bg-gray-50 rounded-lg border border-gray-200">

                <h4 className="text-sm font-semibold text-gray-800 mb-3">
                  Bank Transaction Details
                </h4>


                <div>

                  <label className={labelClass}>
                    Bank / Provider
                  </label>

                  <input
                    type="text"
                    value={paymentProvider}
                    onChange={e =>
                      setPaymentProvider(e.target.value)
                    }
                    placeholder="e.g. Bank of Kigali"
                    className={inputClass}
                  />

                </div>


                <div className="mt-4">

                  <label className={labelClass}>
                    Transaction Reference
                  </label>

                  <input
                    type="text"
                    value={paymentTransactionReference}
                    onChange={e =>
                      setPaymentTransactionReference(e.target.value)
                    }
                    placeholder="Bank transaction reference"
                    className={inputClass}
                  />

                </div>

              </div>

            )}


            {/* ==================================================
                CARD
            ================================================== */}

            {paymentMethod === 'CARD' && (

              <div className="mt-4 p-4 bg-gray-50 rounded-lg border border-gray-200">

                <h4 className="text-sm font-semibold text-gray-800 mb-3">
                  Card Payment Details
                </h4>


                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">

                  <div>

                    <label className={labelClass}>
                      Card Brand *
                    </label>

                    <select
                      required
                      value={cardBrand}
                      onChange={e =>
                        setCardBrand(e.target.value)
                      }
                      className={inputClass}
                    >

                      <option value="">
                        Select card brand...
                      </option>

                      <option value="VISA">
                        Visa
                      </option>

                      <option value="MASTERCARD">
                        Mastercard
                      </option>

                      <option value="AMERICAN_EXPRESS">
                        American Express
                      </option>

                      <option value="OTHER">
                        Other
                      </option>

                    </select>

                  </div>


                  <div>

                    <label className={labelClass}>
                      Last 4 Digits *
                    </label>

                    <input
                      type="text"
                      required
                      maxLength={4}
                      inputMode="numeric"
                      value={cardLastFour}
                      onChange={e =>
                        setCardLastFour(
                          e.target.value
                            .replace(/\D/g, '')
                            .slice(0, 4)
                        )
                      }
                      placeholder="1234"
                      className={inputClass}
                    />

                    <p className="text-xs text-gray-500 mt-1">
                      Never enter the full card number.
                    </p>

                  </div>


                  <div className="md:col-span-2">

                    <label className={labelClass}>
                      Authorization Code
                    </label>

                    <input
                      type="text"
                      value={cardAuthorizationCode}
                      onChange={e =>
                        setCardAuthorizationCode(e.target.value)
                      }
                      placeholder="Card authorization / approval code"
                      className={inputClass}
                    />

                  </div>

                </div>

              </div>

            )}


            {/* ==================================================
                CHEQUE
            ================================================== */}

            {paymentMethod === 'CHEQUE' && (

              <div className="mt-4 p-4 bg-gray-50 rounded-lg border border-gray-200">

                <h4 className="text-sm font-semibold text-gray-800 mb-3">
                  Cheque Details
                </h4>


                <div>

                  <label className={labelClass}>
                    Cheque Number *
                  </label>

                  <input
                    type="text"
                    required
                    value={chequeNumber}
                    onChange={e =>
                      setChequeNumber(e.target.value)
                    }
                    placeholder="Enter cheque number"
                    className={inputClass}
                  />

                </div>

              </div>

            )}


            {/* ==================================================
                ADDITIONAL PAYMENT NOTES
            ================================================== */}

            <div className="mt-4">

              <label className={labelClass}>
                Payment Notes
              </label>

              <textarea
                value={paymentNotes}
                onChange={e =>
                  setPaymentNotes(e.target.value)
                }
                rows={2}
                placeholder="Additional payment information or reference notes"
                className={inputClass}
              />

            </div>

          </div>


          {/* ======================================================
              DESCRIPTION
          ====================================================== */}

          <div>

            <label className={labelClass}>
              Expense Description
            </label>

            <textarea
              value={description}
              onChange={e =>
                setDescription(e.target.value)
              }
              rows={2}
              placeholder="e.g. July office rent"
              className={inputClass}
            />

          </div>


          {/* ======================================================
              RECEIPT
          ====================================================== */}

          <div>

            <label className={labelClass}>
              Receipt / Supporting Document
            </label>

            <input
              type="file"
              accept="application/pdf,image/jpeg,image/png,image/webp"
              onChange={e =>
                setReceipt(
                  e.target.files?.[0] ?? null
                )
              }
              className="w-full text-sm text-gray-600"
            />

            <p className="text-xs text-gray-500 mt-1">
              PDF, JPG, PNG or WEBP. Maximum 8MB.
            </p>

          </div>


          {/* ======================================================
              ACTIONS
          ====================================================== */}

          <div className="flex gap-3 pt-2">

            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-2.5 border border-gray-300 rounded-lg text-sm font-semibold text-gray-700 hover:bg-gray-50"
            >
              Cancel
            </button>


            <button
              type="submit"
              disabled={saving}
              className="flex-1 px-4 py-2.5 bg-teal-600 hover:bg-teal-700 disabled:opacity-50 text-white text-sm font-semibold rounded-lg"
            >
              {saving
                ? 'Saving…'
                : 'Record Expense'}
            </button>

          </div>

        </form>

      </div>

    </div>
  );
}
