'use client';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { createBorrower } from '../../../../services/borrowerService';
import { toast } from '../../../../hooks/useToast';

// ── Field component OUTSIDE the page component ───────────────
// This is critical — if defined inside, React recreates it on
// every keystroke causing the input to lose focus after 1 char
function Field({ label, error, required, hint, children }: {
  label: string;
  error?: string;
  required?: boolean;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <label className="block text-sm font-medium text-gray-700 mb-1">
        {label}
        {required && <span className="text-red-500 ml-0.5">*</span>}
        {hint && <span className="text-gray-400 font-normal ml-1 text-xs">{hint}</span>}
      </label>
      {children}
      {error && (
        <p className="text-xs text-red-600 mt-1 flex items-center gap-1">
          <svg className="w-3 h-3 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd"
              d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z"
              clipRule="evenodd" />
          </svg>
          {error}
        </p>
      )}
    </div>
  );
}

function inputCls(error?: string) {
  return (
    'w-full border rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 ' +
    (error
      ? 'border-red-400 focus:ring-red-400 bg-red-50'
      : 'border-gray-300 focus:ring-blue-500')
  );
}

export default function NewBorrowerPage() {
  const router = useRouter();

  const [firstName,   setFirstName]   = useState('');
  const [lastName,    setLastName]    = useState('');
  const [email,       setEmail]       = useState('');
  const [phone,       setPhone]       = useState('');
  const [nationalId,  setNationalId]  = useState('');
  const [address,     setAddress]     = useState('');
  const [creditScore, setCreditScore] = useState('');
  const [loading,     setLoading]     = useState(false);
  const [errors,      setErrors]      = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState('');
  const [duplicateBorrower, setDuplicateBorrower] = useState<{
    id: number; firstName: string; lastName: string; matchedOn: string;
  } | null>(null);

  const getMsg = (err: unknown) =>
    err instanceof Error ? err.message : 'Something went wrong';

  const validate = (): boolean => {
    const e: Record<string, string> = {};

    if (!firstName.trim()) {
      e.firstName = 'First name is required';
    } else if (firstName.trim().length < 2) {
      e.firstName = 'First name must be at least 2 characters';
    }

    if (!lastName.trim()) {
      e.lastName = 'Last name is required';
    } else if (lastName.trim().length < 2) {
      e.lastName = 'Last name must be at least 2 characters';
    }

    if (!nationalId.trim()) {
      e.nationalId = 'National ID is required';
    } else if (!/^[0-9]{16}$/.test(nationalId)) {
      e.nationalId = 'National ID must be exactly 16 digits';
    }

    if (!phone.trim()) {
      e.phone = 'Phone number is required';
    }

    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      e.email = 'Invalid email address';
    }

    if (creditScore && (Number(creditScore) < 0 || Number(creditScore) > 1000)) {
      e.creditScore = 'Credit score must be between 0 and 1000';
    }

    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setServerError('');
    setDuplicateBorrower(null);
    if (!validate()) return;
    setLoading(true);
    try {
      await createBorrower({
        firstName:   firstName.trim(),
        lastName:    lastName.trim(),
        email:       email.trim() || undefined,
        phone:       phone.trim(),
        nationalId:  nationalId.trim(),
        addressLine1: address.trim() || undefined,
        creditScore: creditScore ? Number(creditScore) : undefined,
      });
      toast('success', 'Borrower created successfully');
      router.push('/dashboard/borrowers');
    } catch (err: any) {
      if (err?.status === 409 && err?.data?.existingBorrower) {
        setDuplicateBorrower(err.data.existingBorrower);
      } else {
        setServerError(getMsg(err));
      }
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-2xl space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900">New Borrower</h1>
          <p className="text-sm text-gray-500 mt-0.5">Fields marked * are required</p>
        </div>
        <Link href="/dashboard/borrowers"
          className="text-sm text-gray-500 hover:text-gray-700">
          Cancel
        </Link>
      </div>

      {serverError && (
        <div className="p-4 rounded-xl bg-red-50 border border-red-200 flex items-start gap-3">
          <svg className="w-5 h-5 text-red-500 flex-shrink-0 mt-0.5" fill="none"
            stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <div>
            <p className="text-sm font-medium text-red-800">Cannot create borrower</p>
            <p className="text-sm text-red-700 mt-0.5">{serverError}</p>
          </div>
        </div>
      )}

      {duplicateBorrower && (
        <div className="p-4 rounded-xl bg-amber-50 border border-amber-200 flex items-start gap-3">
          <span className="text-xl flex-shrink-0">👤</span>
          <div className="flex-1">
            <p className="text-sm font-medium text-amber-900">
              This person already exists — matched by {duplicateBorrower.matchedOn}
            </p>
            <p className="text-sm text-amber-700 mt-0.5">
              {duplicateBorrower.firstName} {duplicateBorrower.lastName} is already a borrower.
              If they need another loan, add it to their existing profile instead of creating a duplicate.
            </p>
            <div className="flex gap-2 mt-3">
              <Link href={`/dashboard/borrowers/${duplicateBorrower.id}`}
                className="text-xs font-bold px-3 py-2 rounded-lg bg-amber-600 text-white hover:bg-amber-700 transition">
                View Existing Profile
              </Link>
              <Link href={`/dashboard/loans/new?borrowerId=${duplicateBorrower.id}`}
                className="text-xs font-bold px-3 py-2 rounded-lg border border-amber-300 text-amber-800 hover:bg-amber-100 transition">
                Create New Loan For Them
              </Link>
            </div>
          </div>
        </div>
      )}

      <form onSubmit={handleSubmit}
        className="bg-white rounded-xl border border-gray-200 p-6 space-y-5">

        <div className="grid grid-cols-2 gap-4">
          <Field label="First Name" required error={errors.firstName}>
            <input
              value={firstName}
              onChange={e => setFirstName(e.target.value)}
              placeholder="John"
              className={inputCls(errors.firstName)}
            />
          </Field>
          <Field label="Last Name" required error={errors.lastName}>
            <input
              value={lastName}
              onChange={e => setLastName(e.target.value)}
              placeholder="Doe"
              className={inputCls(errors.lastName)}
            />
          </Field>
        </div>

        <Field label="National ID" required hint="(exactly 16 digits)"
          error={errors.nationalId}>
          <input
            value={nationalId}
            onChange={e => setNationalId(
              e.target.value.replace(/[^0-9]/g, '').slice(0, 16)
            )}
            inputMode="numeric"
            placeholder="e.g. 1199780012345678"
            maxLength={16}
            className={inputCls(errors.nationalId)}
          />
          <p className="text-xs text-gray-400 mt-1">
            {nationalId.length}/16 digits{nationalId.length === 16 ? ' ✓' : ''}
          </p>
        </Field>

        <div className="grid grid-cols-2 gap-4">
          <Field label="Phone" required hint="(e.g. +250788000000)"
            error={errors.phone}>
            <input
              value={phone}
              onChange={e => setPhone(e.target.value)}
              inputMode="tel"
              placeholder="+250 788 000 000"
              className={inputCls(errors.phone)}
            />
          </Field>
          <Field label="Email" hint="(optional)" error={errors.email}>
            <input
              type="email"
              value={email}
              onChange={e => setEmail(e.target.value)}
              placeholder="john@example.com"
              className={inputCls(errors.email)}
            />
          </Field>
        </div>

        <Field label="Address" hint="(optional)">
          <input
            value={address}
            onChange={e => setAddress(e.target.value)}
            placeholder="Kigali, Rwanda"
            className={inputCls(errors.address)}
          />
        </Field>

        <Field label="Credit Score" hint="(0 - 1000, optional)"
          error={errors.creditScore}>
          <input
            type="number"
            min="0"
            max="1000"
            value={creditScore}
            onChange={e => setCreditScore(e.target.value)}
            inputMode="numeric"
            placeholder="e.g. 750"
            className={inputCls(errors.creditScore)}
          />
        </Field>

        <div className="pt-2 flex gap-3">
          <button
            type="submit"
            disabled={loading}
            className="bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white px-6 py-2.5 rounded-lg text-sm font-medium transition"
          >
            {loading ? 'Creating...' : 'Create Borrower'}
          </button>
          <Link
            href="/dashboard/borrowers"
            className="px-6 py-2.5 rounded-lg text-sm font-medium text-gray-600 hover:bg-gray-100 transition"
          >
            Cancel
          </Link>
        </div>
      </form>
    </div>
  );
}