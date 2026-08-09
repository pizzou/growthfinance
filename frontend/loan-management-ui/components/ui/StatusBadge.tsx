const LOAN: Record<string, string> = {
  APPROVED: 'bg-green-100 text-green-700',
  PENDING:  'bg-yellow-100 text-yellow-700',
  REJECTED: 'bg-red-100 text-red-700',
  PAID:     'bg-gray-100 text-gray-600',
  ACTIVE:   'bg-blue-100 text-blue-700',
};
const KYC: Record<string, string> = {
  VERIFIED: 'bg-green-100 text-green-700',
  PENDING:  'bg-yellow-100 text-yellow-700',
  REJECTED: 'bg-red-100 text-red-700',
};

export function LoanStatusBadge({ status }: { status: string }) {
  return <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${LOAN[status] || 'bg-gray-100 text-gray-600'}`}>{status}</span>;
}
export function KycBadge({ status }: { status: string }) {
  return <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${KYC[status] || 'bg-gray-100 text-gray-600'}`}>{status}</span>;
}