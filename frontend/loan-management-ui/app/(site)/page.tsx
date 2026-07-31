'use client';
import Link from 'next/link';
import React from 'react';
import { useTenant } from './layout';
import { useScrollReveal, useCountUp } from '../../hooks/useScrollReveal';

function IconCheck() {
  return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M20 6 9 17l-5-5"/></svg>;
}
function IconShield() {
  return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="m9 12 2 2 4-4"/></svg>;
}
function IconClock() {
  return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>;
}
function IconDevice() {
  return <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="5" y="2" width="14" height="20" rx="2"/><path d="M12 18h.01"/></svg>;
}

export default function HomePage() {
  const tenant = useTenant();
  if (!tenant) return null;

  const primary = tenant.primaryColor;
  const accent  = tenant.accentColor;

  return (
    <div>
      {/* ── HERO ── */}
      <section className="relative overflow-hidden"
        style={{ background: `linear-gradient(160deg, #0B1220 0%, ${primary} 130%)` }}>
        <div className="relative max-w-7xl mx-auto px-4 py-20 md:py-28 grid md:grid-cols-2 gap-12 items-center">
          <div className="text-white">
            <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded border border-white/20 text-xs font-semibold mb-6 tracking-wide uppercase text-white/80">
              Licensed &amp; regulated in {tenant.country === 'RW' ? 'Rwanda' : tenant.country}
            </div>
            <h1 className="text-4xl md:text-5xl font-bold leading-[1.15] mb-6 tracking-tight">
              {tenant.hero?.headline ?? 'Your Trusted Financial Partner'}
            </h1>
            <p className="text-white/75 text-lg leading-relaxed mb-8 max-w-lg">
              {tenant.hero?.subtext ?? 'Fast approvals, competitive rates, and flexible terms — built on a secure, compliant lending platform.'}
            </p>
            <div className="flex flex-wrap gap-4">
              <Link href="/apply"
                className="px-7 py-3.5 rounded-md font-bold text-base shadow-lg hover:opacity-90 transition-opacity"
                style={{ backgroundColor: accent, color: '#111' }}>
                Apply for a Loan →
              </Link>
              <Link href="/services"
                className="px-7 py-3.5 rounded-md font-semibold text-base border border-white/30 text-white hover:bg-white/10 transition-colors">
                View Our Services
              </Link>
            </div>
            {/* Trust badges */}
            <div className="flex flex-wrap items-center gap-x-8 gap-y-4 mt-12 pt-8 border-t border-white/10">
              {[[IconClock,'Fast approval'],[IconShield,'Bank-grade security'],[IconDevice,'Apply online'],[IconCheck,'No hidden fees']].map(([Icon, label]) => {
                const IconComp = Icon as React.FC;
                return (
                  <div key={label as string} className="flex items-center gap-2 text-white/70">
                    <IconComp />
                    <span className="text-sm font-medium">{label as string}</span>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Loan calculator card */}
          <div className="bg-white rounded-xl shadow-2xl p-8">
            <h3 className="text-lg font-bold mb-1 text-gray-900">Loan Calculator</h3>
            <p className="text-gray-500 text-sm mb-6">Estimate your monthly repayment</p>
            <LoanCalculator primary={primary} accent={accent} currency={tenant.currency} />
          </div>
        </div>
      </section>

      {/* ── STATS ── */}
      <section className="py-14 bg-gray-50 border-b border-gray-100">
        <div className="max-w-7xl mx-auto px-4 grid grid-cols-2 md:grid-cols-4 gap-6">
          {(tenant.stats ?? [
            { value: '5,000+', label: 'Clients served', icon: '' },
            { value: '24 hrs', label: 'Average approval time', icon: '' },
          ]).map((stat, i) => (
            <StatCard key={stat.label} stat={stat} primary={primary} delay={i} />
          ))}
        </div>
      </section>

      {/* ── SERVICES PREVIEW ── */}
      <section className="py-20 max-w-7xl mx-auto px-4">
        <div className="text-center mb-12">
          <h2 className="text-3xl font-bold text-gray-900 mb-4 tracking-tight">Our Financial Products</h2>
          <p className="text-gray-500 text-lg max-w-2xl mx-auto">
            Tailored lending solutions for individuals, businesses, and farmers across {tenant.country === 'RW' ? 'Rwanda' : tenant.country}.
          </p>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {tenant.services?.map(service => (
            <div key={service.title}
              className="group bg-white rounded-lg border border-gray-200 p-6 hover:shadow-lg hover:border-gray-300 transition-all duration-200">
              <h3 className="text-lg font-bold text-gray-900 mb-2">{service.title}</h3>
              <p className="text-gray-500 text-sm leading-relaxed mb-4">{service.description}</p>
              <div className="flex items-center justify-between text-xs mb-4">
                <span className="font-bold px-3 py-1.5 rounded" style={{ backgroundColor: primary + '12', color: primary }}>
                  From {service.rate} {service.rateType === 'MONTHLY' ? 'per month' : 'p.a.'}
                </span>
                <span className="text-gray-400">Up to {tenant.currency} {service.maxAmount}</span>
              </div>
              <Link href={`/apply?type=${service.title.replace(/ /g,'_').toUpperCase()}`}
                className="block text-center py-2.5 rounded-md text-sm font-bold border transition-colors"
                style={{ borderColor: primary, color: primary }}
                onMouseEnter={e => { (e.target as HTMLElement).style.backgroundColor = primary; (e.target as HTMLElement).style.color = '#fff'; }}
                onMouseLeave={e => { (e.target as HTMLElement).style.backgroundColor = 'transparent'; (e.target as HTMLElement).style.color = primary; }}>
                Apply Now →
              </Link>
            </div>
          ))}
        </div>
      </section>

      {/* ── HOW IT WORKS ── */}
      <section className="py-20" style={{ backgroundColor: primary + '06' }}>
        <div className="max-w-5xl mx-auto px-4">
          <div className="text-center mb-12">
            <h2 className="text-3xl font-bold text-gray-900 mb-4 tracking-tight">How to Get a Loan</h2>
            <p className="text-gray-500 text-lg">A simple, secure 4-step process</p>
          </div>
          <div className="grid md:grid-cols-4 gap-6">
            {[
              { step:'1', title:'Apply Online', desc:'Complete our application form in a few minutes, from any device.' },
              { step:'2', title:'Submit Documents', desc:'Upload your ID and supporting documents securely — no branch visit required.' },
              { step:'3', title:'Get Approved', desc:'Our credit team reviews your application and responds within 24 hours.' },
              { step:'4', title:'Receive Funds', desc:'Approved funds are disbursed directly to your mobile money or bank account.' },
            ].map(item => (
              <div key={item.step} className="text-center bg-white rounded-lg p-6 border border-gray-100 relative">
                <div className="absolute -top-3 left-1/2 -translate-x-1/2 w-7 h-7 rounded-full text-white font-bold text-xs flex items-center justify-center"
                  style={{ backgroundColor: primary }}>{item.step}</div>
                <div className="font-bold text-gray-900 mb-2 mt-3">{item.title}</div>
                <div className="text-gray-500 text-sm">{item.desc}</div>
              </div>
            ))}
          </div>
          <div className="text-center mt-10">
            <Link href="/apply"
              className="inline-block px-10 py-3.5 rounded-md text-white font-bold text-base shadow-md hover:opacity-90 transition-opacity"
              style={{ backgroundColor: primary }}>
              Start Your Application →
            </Link>
          </div>
        </div>
      </section>

      {/* ── TESTIMONIALS ── */}
      {tenant.testimonials && tenant.testimonials.length > 0 && (
        <section className="py-20 max-w-7xl mx-auto px-4">
          <div className="text-center mb-12">
            <h2 className="text-3xl font-bold text-gray-900 mb-4 tracking-tight">What Our Clients Say</h2>
          </div>
          <div className="grid md:grid-cols-3 gap-6">
            {tenant.testimonials.map((t, i) => (
              <TestimonialCard key={t.name} t={t} primary={primary} accent={accent} delay={i} />
            ))}
          </div>
        </section>
      )}

      {/* ── CTA BANNER ── */}
      <section className="py-16 mx-4 md:mx-auto max-w-7xl mb-16">
        <div className="rounded-xl p-12 text-center text-white relative overflow-hidden"
          style={{ background: `linear-gradient(135deg, #0B1220, ${primary})` }}>
          <h2 className="text-3xl md:text-4xl font-bold mb-4 relative z-10 tracking-tight">
            Ready to Take the Next Step?
          </h2>
          <p className="text-white/75 text-lg mb-8 relative z-10 max-w-xl mx-auto">
            Apply today and get a response within 24 hours. No hidden fees, no surprises.
          </p>
          <Link href="/apply"
            className="inline-block px-12 py-3.5 rounded-md font-bold text-base shadow-lg hover:opacity-90 transition-opacity relative z-10"
            style={{ backgroundColor: accent, color: '#111' }}>
            Apply for a Loan Now →
          </Link>
        </div>
      </section>
    </div>
  );
}

// Inline calculator component
function LoanCalculator({ primary, accent, currency }: { primary: string; accent: string; currency: string }) {
  const [amount, setAmount]   = React.useState(500000);
  const [months, setMonths]   = React.useState(12);
  
  // 1. Force the monthly interest rate calculation to exactly 10% (0.10)
  const monthlyRate = 0.10; 
  
  // 2. Calculate the monthly interest amount accumulated (Flat rate math)
  const monthlyInterest = amount * monthlyRate;
  
  // 3. Calculate the monthly principal payback allocation
  const monthlyPrincipal = amount / months;
  
  // 4. Combine them to get the total fixed monthly installment payment
  const monthly = monthlyPrincipal + monthlyInterest;
  
  // 5. Aggregate totals
  const total    = monthly * months;
  const interest = total - amount;
  
  const fmt = (n: number) => n.toLocaleString('en-RW', { maximumFractionDigits: 0 });


  return (
    <div>
      <div className="mb-4">
        <label className="text-xs font-bold text-gray-500 uppercase tracking-wider">Loan Amount ({currency})</label>
        <input type="range" min={100000} max={10000000} step={100000} value={amount}
          onChange={e => setAmount(Number(e.target.value))}
          className="w-full mt-2" style={{ accentColor: primary }} />
        <div className="text-2xl font-bold mt-1" style={{ color: primary }}>{currency} {fmt(amount)}</div>
      </div>
      <div className="mb-4">
        <label className="text-xs font-bold text-gray-500 uppercase tracking-wider">Loan Term</label>
        <div className="flex gap-2 flex-wrap mt-2">
          {[3,6,12,24,36,48].map(m => (
            <button key={m} onClick={() => setMonths(m)}
              className="px-3 py-1.5 rounded-md text-sm font-semibold border transition-all"
              style={months === m ? { backgroundColor: primary, color: '#fff', borderColor: primary }
                : { borderColor: '#e5e7eb', color: '#6b7280' }}>
              {m}mo
            </button>
          ))}
        </div>
      </div>
      <div className="grid grid-cols-3 gap-3 my-6 bg-gray-50 rounded-lg p-4 border border-gray-100">
        {[
          ['Monthly', currency + ' ' + fmt(monthly)],
          ['Total',   currency + ' ' + fmt(total)],
          ['Interest',currency + ' ' + fmt(interest)],
        ].map(([label, value]) => (
          <div key={label} className="text-center">
            <div className="text-[10px] text-gray-400 uppercase font-bold">{label}</div>
            <div className="text-sm font-bold mt-0.5" style={{ color: primary }}>{value}</div>
          </div>
        ))}
      </div>
      <p className="text-[11px] text-gray-400 mb-4">Estimate only. Final rate and terms are confirmed after credit assessment.</p>
      <Link href="/apply"
        className="block text-center py-3.5 rounded-md text-white font-bold text-base shadow-md hover:opacity-90 transition-opacity"
        style={{ backgroundColor: accent, color: '#111' }}>
        Apply for This Loan →
      </Link>
    </div>
  );
}

function TestimonialCard({ t, primary, accent, delay }: { t: { name: string; role: string; text: string; rating?: number }; primary: string; accent: string; delay: number }) {
  const { ref, visible } = useScrollReveal();
  return (
    <div ref={ref} className={`reveal reveal-delay-${Math.min(delay + 1, 4)} ${visible ? 'reveal-visible' : ''}
      card-lift bg-white rounded-lg p-6 border border-gray-100`}>
      <div className="flex mb-3">
        {'★★★★★'.split('').map((s, i) => (
          <span key={i} style={{ color: accent }} className="text-lg">{s}</span>
        ))}
      </div>
      <p className="text-gray-600 text-sm leading-relaxed mb-4">&ldquo;{t.text}&rdquo;</p>
      <div className="flex items-center gap-3">
        <div className="w-9 h-9 rounded-full flex items-center justify-center text-white font-bold text-sm"
          style={{ backgroundColor: primary }}>
          {t.name[0]}
        </div>
        <div>
          <div className="font-bold text-gray-900 text-sm">{t.name}</div>
          <div className="text-gray-400 text-xs">{t.role}</div>
        </div>
      </div>
    </div>
  );
}

function StatCard({ stat, primary, delay }: { stat: { icon: string; value: string; label: string }; primary: string; delay: number }) {
  const { ref, visible } = useScrollReveal();
  const numericMatch = stat.value.match(/^([\d,]+)$/);
  const numericTarget = numericMatch ? Number(numericMatch[1].replace(/,/g, '')) : null;
  const animated = useCountUp(numericTarget ?? 0, visible && numericTarget !== null);

  return (
    <div ref={ref} className={`reveal reveal-delay-${Math.min(delay + 1, 4)} ${visible ? 'reveal-visible' : ''}
      card-lift bg-white rounded-lg p-6 text-center border border-gray-100`}>
      <div className="text-3xl font-bold mb-1" style={{ color: primary }}>
        {numericTarget !== null ? animated.toLocaleString() : stat.value}
      </div>
      <div className="text-gray-500 text-sm">{stat.label}</div>
    </div>
  );
}