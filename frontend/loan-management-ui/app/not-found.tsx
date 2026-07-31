import Link from 'next/link';

export default function NotFound() {
  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center px-4">
      <div className="bg-white rounded-2xl border border-gray-200 p-10 max-w-md w-full text-center shadow-sm">
        <p className="text-6xl font-bold text-gray-200 mb-4">404</p>
        <h1 className="text-xl font-bold text-gray-900 mb-2">Page not found</h1>
        <p className="text-gray-500 text-sm mb-6">
          The page you are looking for does not exist or has been moved.
        </p>
        <Link href="/dashboard"
          className="inline-block bg-blue-600 hover:bg-blue-700 text-white px-6 py-2.5 rounded-lg text-sm font-medium transition">
          Back to dashboard
        </Link>
      </div>
    </div>
  );
}