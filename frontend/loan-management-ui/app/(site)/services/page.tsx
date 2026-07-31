'use client';
import Link from 'next/link';
import { useTenant } from '../layout';

export default function ServicesPage() {
  const tenant = useTenant();
  if (!tenant) return null;
  const primary = tenant.primaryColor;
  const accent  = tenant.accentColor;

  return (
    <div>
      {/* Hero */}
      <section className="py-20 text-white text-center"
        style={{ background: `linear-gradient(135deg, ${primary} 0%, #0a4a2b 100%)` }}>
        <div className="max-w-3xl mx-auto px-4">
          <h1 className="text-4xl md:text-5xl font-extrabold mb-4">Our Financial Services</h1>
          <p className="text-white/80 text-lg">
            Flexible, affordable loan products designed for every Rwandan — from salary earners to farmers and entrepreneurs.
          </p>
        </div>
      </section>

      {/* Services grid */}
      <section className="py-20 max-w-7xl mx-auto px-4">
        <div className="space-y-12">
          {tenant.services?.map((service, idx) => (
            <div key={service.title}
              className={`grid md:grid-cols-2 gap-10 items-center ${idx % 2 === 1 ? 'md:flex-row-reverse' : ''}`}>
              <div className={idx % 2 === 1 ? 'md:order-2' : ''}>
                <div className="w-20 h-20 rounded-3xl flex items-center justify-center text-5xl mb-6"
                  style={{ backgroundColor: primary + '15' }}>
                  {service.icon}
                </div>
                <h2 className="text-2xl md:text-3xl font-extrabold text-gray-900 mb-3">{service.title}</h2>
                <p className="text-gray-600 text-lg leading-relaxed mb-6">{service.description}</p>
                <div className="grid grid-cols-3 gap-4 mb-6">
                  {[
                    ['Interest Rate', service.rate + (service.rateType === 'MONTHLY' ? ' per month' : ' p.a.')],
                    ['Max Amount',    tenant.currency + ' ' + service.maxAmount],
                    ['Loan Term',     service.term],
                  ].map(([label, value]) => (
                    <div key={label} className="bg-gray-50 rounded-xl p-4 text-center border border-gray-100">
                      <div className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-1">{label}</div>
                      <div className="font-extrabold text-sm" style={{ color: primary }}>{value}</div>
                    </div>
                  ))}
                </div>
                <div className="flex gap-3">
                  <Link href={`/apply?type=${service.title.replace(/ /g,'_').toUpperCase()}`}
                    className="px-8 py-3 rounded-full text-white font-bold shadow-md hover:opacity-90 transition"
                    style={{ backgroundColor: primary }}>
                    Apply Now →
                  </Link>
                  <Link href={`/contact`}
                    className="px-8 py-3 rounded-full font-bold border-2 hover:bg-gray-50 transition"
                    style={{ borderColor: primary, color: primary }}>
                    Learn More
                  </Link>
                </div>
              </div>

              {/* Requirements */}
              <div className={`bg-gray-50 rounded-3xl p-8 border border-gray-100 ${idx % 2 === 1 ? 'md:order-1' : ''}`}>
                <h3 className="font-bold text-gray-900 mb-4">Requirements</h3>
                <ul className="space-y-3 text-sm text-gray-700">
                  {[
                    '✅ Valid national ID or passport',
                    '✅ Proof of income or business registration',
                    '✅ Recent bank statement or Mobile Money statement',
                    '✅ Collateral documentation (where applicable)',
                    '✅ Completed loan application form',
                  ].map(req => <li key={req} className="flex items-start gap-2">{req}</li>)}
                </ul>
                <div className="mt-6 pt-4 border-t border-gray-200">
                  <div className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-3">Processing Time</div>
                  <div className="flex items-center gap-2">
                    <span className="text-2xl">⚡</span>
                    <span className="font-bold text-gray-800">Decision within <span style={{ color: primary }}>24–48 hours</span></span>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* CTA */}
      <section className="py-16 text-center" style={{ backgroundColor: primary + '08' }}>
        <div className="max-w-2xl mx-auto px-4">
          <h2 className="text-3xl font-extrabold text-gray-900 mb-4">Not sure which loan is right for you?</h2>
          <p className="text-gray-500 mb-8">Our financial advisors are here to help you choose the best option for your needs.</p>
          <div className="flex gap-4 justify-center flex-wrap">
            <Link href={`/contact`}
              className="px-8 py-3 rounded-full text-white font-bold hover:opacity-90 transition"
              style={{ backgroundColor: primary }}>
              Talk to an Advisor
            </Link>
            <Link href={`/apply`}
              className="px-8 py-3 rounded-full font-bold hover:opacity-90 transition text-white"
              style={{ backgroundColor: accent }}>
              Apply Online →
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}