
'use client';

import Link from 'next/link';
import React from 'react';
import { useTenant } from './layout';
import { useScrollReveal, useCountUp } from '../../hooks/useScrollReveal';

/* ============================================================
   ICONS
============================================================ */

function IconCheck() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"
      strokeLinejoin="round">
      <path d="M20 6 9 17l-5-5" />
    </svg>
  );
}

function IconShield() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="2" strokeLinecap="round"
      strokeLinejoin="round">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}

function IconClock() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="2" strokeLinecap="round"
      strokeLinejoin="round">
      <circle cx="12" cy="12" r="10" />
      <path d="M12 6v6l4 2" />
    </svg>
  );
}

function IconDevice() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="2" strokeLinecap="round"
      strokeLinejoin="round">
      <rect x="5" y="2" width="14" height="20" rx="2" />
      <path d="M12 18h.01" />
    </svg>
  );
}

function IconArrow() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="2" strokeLinecap="round"
      strokeLinejoin="round">
      <path d="M5 12h14" />
      <path d="m13 6 6 6-6 6" />
    </svg>
  );
}

function IconCalculator() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"
      strokeLinejoin="round">
      <rect x="5" y="2" width="14" height="20" rx="2" />
      <path d="M8 6h8" />
      <path d="M8 10h.01M12 10h.01M16 10h.01" />
      <path d="M8 14h.01M12 14h.01M16 14h.01" />
      <path d="M8 18h.01M12 18h4" />
    </svg>
  );
}

function IconDocument() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"
      strokeLinejoin="round">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <path d="M14 2v6h6" />
      <path d="M8 13h8M8 17h6" />
    </svg>
  );
}

function IconBank() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"
      strokeLinejoin="round">
      <path d="m3 10 9-6 9 6" />
      <path d="M5 10h14" />
      <path d="M6 10v8M10 10v8M14 10v8M18 10v8" />
      <path d="M3 20h18" />
    </svg>
  );
}

/* ============================================================
   HOME PAGE
============================================================ */

export default function HomePage() {
  const tenant = useTenant();

  if (!tenant) return null;

  const primary = tenant.primaryColor;
  const accent = tenant.accentColor;

  const countryName =
    tenant.country === 'RW'
      ? 'Rwanda'
      : tenant.country;

  const trustItems = [
    { icon: <IconShield />, label: 'Secure & compliant' },
    { icon: <IconClock />, label: 'Fast decisions' },
    { icon: <IconDevice />, label: '100% online application' },
    { icon: <IconCheck />, label: 'Transparent terms' },
  ];

  return (
    <main className="bg-white text-gray-900">

      {/* ======================================================
          HERO
      ====================================================== */}

      <section
        className="relative overflow-hidden text-white"
        style={{
          background: `
            radial-gradient(circle at 80% 20%, ${primary}55 0%, transparent 32%),
            linear-gradient(135deg, #07111f 0%, #0b1727 45%, ${primary} 150%)
          `,
        }}
      >
        {/* Decorative background */}
        <div className="absolute inset-0 pointer-events-none">
          <div
            className="absolute -right-32 -top-32 w-96 h-96 rounded-full blur-3xl opacity-20"
            style={{ backgroundColor: accent }}
          />
          <div
            className="absolute left-1/3 bottom-0 w-72 h-72 rounded-full blur-3xl opacity-10"
            style={{ backgroundColor: primary }}
          />
        </div>

        <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-20 md:py-28 lg:py-32">
          <div className="grid lg:grid-cols-[1.08fr_.92fr] gap-14 lg:gap-20 items-center">

            {/* Hero copy */}
            <div>

              <div
                className="inline-flex items-center gap-2 rounded-full border px-4 py-2 mb-7 text-xs font-bold uppercase tracking-wider"
                style={{
                  borderColor: `${accent}55`,
                  backgroundColor: `${accent}12`,
                  color: '#fff',
                }}
              >
                <span
                  className="w-2 h-2 rounded-full"
                  style={{ backgroundColor: accent }}
                />
                Trusted lending partner in {countryName}
              </div>

              <h1 className="text-4xl sm:text-5xl lg:text-6xl font-black leading-[1.05] tracking-tight max-w-3xl">
                {tenant.hero?.headline ??
                  'Finance your next move with confidence.'}
              </h1>

              <p className="mt-7 text-lg md:text-xl leading-relaxed text-white/70 max-w-2xl">
                {tenant.hero?.subtext ??
                  'Flexible financing, transparent terms and a simple digital application designed around your needs.'}
              </p>

              <div className="flex flex-col sm:flex-row gap-3 mt-9">

                <Link
                  href="/apply"
                  className="group inline-flex items-center justify-center gap-2 px-7 py-4 rounded-xl font-extrabold shadow-xl transition-all hover:-translate-y-0.5 hover:shadow-2xl"
                  style={{
                    backgroundColor: accent,
                    color: '#111827',
                  }}
                >
                  Apply for a Loan
                  <IconArrow />
                </Link>

                <Link
                  href="/track"
                  className="inline-flex items-center justify-center gap-2 px-7 py-4 rounded-xl font-bold border border-white/20 bg-white/5 hover:bg-white/10 transition-colors"
                >
                  Track Application
                </Link>

              </div>

              <div className="grid grid-cols-2 sm:grid-cols-4 gap-5 mt-12 pt-8 border-t border-white/10">
                {trustItems.map((item) => (
                  <div
                    key={item.label}
                    className="flex items-center gap-2.5 text-white/65"
                  >
                    <span style={{ color: accent }}>
                      {item.icon}
                    </span>
                    <span className="text-xs sm:text-sm font-semibold">
                      {item.label}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            {/* Calculator */}
            <div className="lg:justify-self-end w-full max-w-xl">
              <div className="relative">

                <div
                  className="absolute -inset-3 rounded-[2rem] opacity-30 blur-2xl"
                  style={{ backgroundColor: primary }}
                />

                <div className="relative bg-white rounded-3xl shadow-2xl overflow-hidden text-gray-900">

                  <div
                    className="px-7 py-6 border-b border-gray-100"
                    style={{
                      background: `linear-gradient(135deg, ${primary}08, transparent)`,
                    }}
                  >
                    <div className="flex items-center gap-3">
                      <div
                        className="w-11 h-11 rounded-xl flex items-center justify-center"
                        style={{
                          backgroundColor: `${primary}12`,
                          color: primary,
                        }}
                      >
                        <IconCalculator />
                      </div>

                      <div>
                        <h2 className="text-lg font-black">
                          Loan Calculator
                        </h2>
                        <p className="text-xs text-gray-500 mt-0.5">
                          Estimate your repayment
                        </p>
                      </div>
                    </div>
                  </div>

                  <div className="p-7">
                    <LoanCalculator
                      primary={primary}
                      accent={accent}
                      currency={tenant.currency}
                    />
                  </div>

                </div>
              </div>
            </div>

          </div>
        </div>
      </section>

      {/* ======================================================
          STATS
      ====================================================== */}

      <section className="border-b border-gray-100 bg-gray-50/80">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <div className="grid grid-cols-2 md:grid-cols-4 divide-x divide-gray-200">

            {(tenant.stats ?? [
              { value: '5,000+', label: 'Clients served', icon: '' },
              { value: '24 hrs', label: 'Average approval time', icon: '' },
            ]).map((stat, i) => (
              <StatCard
                key={stat.label}
                stat={stat}
                primary={primary}
                delay={i}
              />
            ))}

          </div>
        </div>
      </section>

      {/* ======================================================
          PRODUCTS
      ====================================================== */}

      <section className="py-24 md:py-28">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">

          <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-6 mb-12">

            <div>
              <div
                className="text-xs font-black uppercase tracking-[0.2em] mb-3"
                style={{ color: primary }}
              >
                Financing solutions
              </div>

              <h2 className="text-3xl md:text-4xl font-black tracking-tight">
                Financing built around you
              </h2>

              <p className="text-gray-500 text-lg mt-4 max-w-2xl">
                Choose from flexible lending solutions designed for individuals,
                businesses and growing enterprises.
              </p>
            </div>

            <Link
              href="/services"
              className="inline-flex items-center gap-2 text-sm font-bold whitespace-nowrap"
              style={{ color: primary }}
            >
              View all products
              <IconArrow />
            </Link>

          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">

            {tenant.services?.map((service, index) => (
              <div
                key={service.title}
                className="group relative bg-white rounded-2xl border border-gray-200 p-7 hover:border-gray-300 hover:shadow-xl transition-all duration-300"
              >

                <div
                  className="w-12 h-12 rounded-xl flex items-center justify-center mb-6"
                  style={{
                    backgroundColor: `${primary}10`,
                    color: primary,
                  }}
                >
                  {index % 3 === 0
                    ? <IconBank />
                    : index % 3 === 1
                      ? <IconDocument />
                      : <IconCalculator />}
                </div>

                <h3 className="text-xl font-black text-gray-900 mb-3">
                  {service.title}
                </h3>

                <p className="text-gray-500 text-sm leading-6 min-h-[72px]">
                  {service.description}
                </p>

                <div className="flex items-center justify-between gap-3 mt-6 pt-5 border-t border-gray-100">

                  <span
                    className="text-xs font-bold px-3 py-2 rounded-lg"
                    style={{
                      backgroundColor: `${primary}10`,
                      color: primary,
                    }}
                  >
                    From {service.rate}
                    {service.rateType === 'MONTHLY'
                      ? ' / month'
                      : ' p.a.'}
                  </span>

                  <span className="text-xs font-semibold text-gray-400">
                    Up to {tenant.currency} {service.maxAmount}
                  </span>

                </div>

                <Link
                  href={`/apply?type=${service.title.replace(/ /g, '_').toUpperCase()}`}
                  className="mt-6 w-full inline-flex items-center justify-center gap-2 py-3 rounded-xl text-sm font-bold border-2 transition-all"
                  style={{
                    borderColor: primary,
                    color: primary,
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.backgroundColor = primary;
                    e.currentTarget.style.color = '#fff';
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.backgroundColor = 'transparent';
                    e.currentTarget.style.color = primary;
                  }}
                >
                  Explore & Apply
                  <IconArrow />
                </Link>

              </div>
            ))}

          </div>
        </div>
      </section>

      {/* ======================================================
          HOW IT WORKS
      ====================================================== */}

      <section
        className="py-24"
        style={{
          background: `linear-gradient(180deg, ${primary}05, #f8fafc)`,
        }}
      >
        <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8">

          <div className="text-center mb-14">

            <div
              className="text-xs font-black uppercase tracking-[0.2em] mb-3"
              style={{ color: primary }}
            >
              Simple process
            </div>

            <h2 className="text-3xl md:text-4xl font-black tracking-tight">
              From application to funding
            </h2>

            <p className="text-gray-500 text-lg mt-4">
              A simple, secure process with no unnecessary paperwork.
            </p>

          </div>

          <div className="grid md:grid-cols-4 gap-6">

            {[
              {
                step: '01',
                title: 'Apply online',
                desc: 'Complete your application in just a few minutes from any device.',
              },
              {
                step: '02',
                title: 'Upload documents',
                desc: 'Submit your identification and supporting documents securely.',
              },
              {
                step: '03',
                title: 'Get a decision',
                desc: 'Our credit team reviews your application and assesses your eligibility.',
              },
              {
                step: '04',
                title: 'Receive funds',
                desc: 'Once approved, funds are sent directly to your chosen account.',
              },
            ].map((item) => (
              <div
                key={item.step}
                className="relative bg-white rounded-2xl border border-gray-200 p-7 shadow-sm"
              >

                <div
                  className="text-3xl font-black mb-6"
                  style={{ color: `${primary}25` }}
                >
                  {item.step}
                </div>

                <h3 className="font-black text-lg text-gray-900 mb-3">
                  {item.title}
                </h3>

                <p className="text-sm text-gray-500 leading-6">
                  {item.desc}
                </p>

              </div>
            ))}

          </div>

          <div className="text-center mt-12">

            <Link
              href="/apply"
              className="inline-flex items-center justify-center gap-2 px-9 py-4 rounded-xl text-white font-extrabold shadow-lg hover:shadow-xl hover:-translate-y-0.5 transition-all"
              style={{ backgroundColor: primary }}
            >
              Start Your Application
              <IconArrow />
            </Link>

          </div>

        </div>
      </section>

      {/* ======================================================
          TESTIMONIALS
      ====================================================== */}

      {tenant.testimonials &&
        tenant.testimonials.length > 0 && (
          <section className="py-24">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">

              <div className="text-center mb-14">

                <div
                  className="text-xs font-black uppercase tracking-[0.2em] mb-3"
                  style={{ color: primary }}
                >
                  Client experience
                </div>

                <h2 className="text-3xl md:text-4xl font-black tracking-tight">
                  Trusted by our clients
                </h2>

                <p className="text-gray-500 text-lg mt-4">
                  Real experiences from people and businesses we have helped.
                </p>

              </div>

              <div className="grid md:grid-cols-3 gap-6">

                {tenant.testimonials.map((t, i) => (
                  <TestimonialCard
                    key={t.name}
                    t={t}
                    primary={primary}
                    accent={accent}
                    delay={i}
                  />
                ))}

              </div>

            </div>
          </section>
        )}

      {/* ======================================================
          FINAL CTA
      ====================================================== */}

      <section className="pb-20">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">

          <div
            className="relative overflow-hidden rounded-3xl px-7 py-16 md:px-16 md:py-20 text-center text-white"
            style={{
              background: `
                radial-gradient(circle at 80% 20%, ${primary} 0%, transparent 35%),
                linear-gradient(135deg, #07111f, #102235)
              `,
            }}
          >

            <div
              className="absolute -right-24 -bottom-24 w-72 h-72 rounded-full blur-3xl opacity-20"
              style={{ backgroundColor: accent }}
            />

            <div className="relative z-10">

              <div
                className="inline-flex items-center gap-2 text-xs font-bold uppercase tracking-[0.18em] mb-5"
                style={{ color: accent }}
              >
                Ready when you are
              </div>

              <h2 className="text-3xl md:text-5xl font-black tracking-tight mb-5">
                Take the next step with confidence.
              </h2>

              <p className="text-white/65 text-lg max-w-2xl mx-auto mb-9">
                Apply online today and let our team help you find the right
                financing solution for your needs.
              </p>

              <div className="flex flex-col sm:flex-row justify-center gap-3">

                <Link
                  href="/apply"
                  className="inline-flex items-center justify-center gap-2 px-9 py-4 rounded-xl font-extrabold shadow-xl"
                  style={{
                    backgroundColor: accent,
                    color: '#111827',
                  }}
                >
                  Apply for a Loan
                  <IconArrow />
                </Link>

                <Link
                  href="/track"
                  className="inline-flex items-center justify-center px-9 py-4 rounded-xl border border-white/20 bg-white/5 hover:bg-white/10 font-bold"
                >
                  Track My Application
                </Link>

              </div>

            </div>
          </div>

        </div>
      </section>

    </main>
  );
}

/* ============================================================
   LOAN CALCULATOR
============================================================ */

function LoanCalculator({
  primary,
  accent,
  currency,
}: {
  primary: string;
  accent: string;
  currency: string;
}) {
  const [amount, setAmount] = React.useState(500000);
  const [months, setMonths] = React.useState(12);

  /*
   * IMPORTANT:
   * This is an ESTIMATE only.
   *
   * Your backend should ultimately provide the actual rate
   * for the selected loan product. The 10% value below is
   * retained from your original page so this replacement
   * does not silently change the existing calculation.
   */
  const monthlyRate = 0.10;

  const monthlyInterest = amount * monthlyRate;
  const monthlyPrincipal = amount / months;
  const monthly = monthlyPrincipal + monthlyInterest;

  const total = monthly * months;
  const interest = total - amount;

  const fmt = (n: number) =>
    n.toLocaleString('en-RW', {
      maximumFractionDigits: 0,
    });

  return (
    <div>

      {/* Amount */}
      <div className="mb-7">

        <div className="flex justify-between items-center mb-3">
          <label className="text-xs font-black uppercase tracking-wider text-gray-500">
            Loan amount
          </label>

          <span
            className="text-xs font-bold"
            style={{ color: primary }}
          >
            {currency}
          </span>
        </div>

        <input
          type="range"
          min={100000}
          max={10000000}
          step={100000}
          value={amount}
          onChange={(e) => setAmount(Number(e.target.value))}
          className="w-full"
          style={{ accentColor: primary }}
        />

        <div
          className="text-3xl font-black mt-3"
          style={{ color: primary }}
        >
          {currency} {fmt(amount)}
        </div>

        <div className="flex justify-between text-[10px] text-gray-400 mt-1 font-semibold">
          <span>{currency} 100K</span>
          <span>{currency} 10M</span>
        </div>

      </div>

      {/* Term */}
      <div className="mb-7">

        <label className="text-xs font-black uppercase tracking-wider text-gray-500">
          Repayment term
        </label>

        <div className="grid grid-cols-3 gap-2 mt-3">

          {[3, 6, 12, 24, 36, 48].map((m) => (
            <button
              key={m}
              type="button"
              onClick={() => setMonths(m)}
              className="py-2.5 rounded-xl text-xs font-bold border transition-all"
              style={
                months === m
                  ? {
                      backgroundColor: primary,
                      color: '#fff',
                      borderColor: primary,
                    }
                  : {
                      borderColor: '#e5e7eb',
                      color: '#6b7280',
                    }
              }
            >
              {m} months
            </button>
          ))}

        </div>
      </div>

      {/* Results */}
      <div
        className="rounded-2xl border p-5 mb-5"
        style={{
          backgroundColor: `${primary}06`,
          borderColor: `${primary}15`,
        }}
      >

        <div className="grid grid-cols-3 divide-x divide-gray-200">

          <div className="text-center px-2">
            <div className="text-[9px] uppercase tracking-wider font-black text-gray-400">
              Monthly
            </div>
            <div
              className="text-sm font-black mt-1"
              style={{ color: primary }}
            >
              {currency} {fmt(monthly)}
            </div>
          </div>

          <div className="text-center px-2">
            <div className="text-[9px] uppercase tracking-wider font-black text-gray-400">
              Total
            </div>
            <div className="text-sm font-black mt-1 text-gray-900">
              {currency} {fmt(total)}
            </div>
          </div>

          <div className="text-center px-2">
            <div className="text-[9px] uppercase tracking-wider font-black text-gray-400">
              Interest
            </div>
            <div className="text-sm font-black mt-1 text-gray-900">
              {currency} {fmt(interest)}
            </div>
          </div>

        </div>

      </div>

      <div className="flex gap-2 text-[11px] text-gray-400 leading-5 mb-5">
        <span>ⓘ</span>
        <p>
          This calculator provides an estimate only. Final rates,
          fees and repayment terms are determined after credit assessment.
        </p>
      </div>

      <Link
        href="/apply"
        className="w-full inline-flex items-center justify-center gap-2 py-3.5 rounded-xl font-extrabold shadow-md hover:shadow-lg hover:-translate-y-0.5 transition-all"
        style={{
          backgroundColor: accent,
          color: '#111827',
        }}
      >
        Apply for This Loan
        <IconArrow />
      </Link>

    </div>
  );
}

/* ============================================================
   TESTIMONIAL
============================================================ */

function TestimonialCard({
  t,
  primary,
  accent,
  delay,
}: {
  t: {
    name: string;
    role: string;
    text: string;
    rating?: number;
  };
  primary: string;
  accent: string;
  delay: number;
}) {
  const { ref, visible } = useScrollReveal();

  const rating = Math.min(5, Math.max(1, t.rating ?? 5));

  return (
    <div
      ref={ref}
      className={`
        reveal
        reveal-delay-${Math.min(delay + 1, 4)}
        ${visible ? 'reveal-visible' : ''}
        card-lift
        bg-white
        rounded-2xl
        p-7
        border
        border-gray-200
      `}
    >

      <div className="flex gap-1 mb-5">
        {[0, 1, 2, 3, 4].map((i) => (
          <span
            key={i}
            className="text-lg"
            style={{
              color: i < rating ? accent : '#d1d5db',
            }}
          >
            ★
          </span>
        ))}
      </div>

      <p className="text-gray-600 text-sm leading-7 mb-7">
        “{t.text}”
      </p>

      <div className="flex items-center gap-3">

        <div
          className="w-10 h-10 rounded-full flex items-center justify-center text-white font-black text-sm"
          style={{ backgroundColor: primary }}
        >
          {t.name.charAt(0).toUpperCase()}
        </div>

        <div>
          <div className="font-black text-gray-900 text-sm">
            {t.name}
          </div>

          <div className="text-gray-400 text-xs mt-0.5">
            {t.role}
          </div>
        </div>

      </div>

    </div>
  );
}

/* ============================================================
   STAT CARD
============================================================ */

function StatCard({
  stat,
  primary,
  delay,
}: {
  stat: {
    icon: string;
    value: string;
    label: string;
  };
  primary: string;
  delay: number;
}) {
  const { ref, visible } = useScrollReveal();

  const numericMatch = stat.value.match(/^([\d,]+)$/);

  const numericTarget = numericMatch
    ? Number(numericMatch[1].replace(/,/g, ''))
    : null;

  const animated = useCountUp(
    numericTarget ?? 0,
    visible && numericTarget !== null
  );

  return (
    <div
      ref={ref}
      className={`
        reveal
        reveal-delay-${Math.min(delay + 1, 4)}
        ${visible ? 'reveal-visible' : ''}
        text-center px-4
      `}
    >

      <div
        className="text-2xl md:text-3xl font-black"
        style={{ color: primary }}
      >
        {numericTarget !== null
          ? `${animated.toLocaleString()}+`
          : stat.value}
      </div>

      <div className="text-xs md:text-sm text-gray-500 font-semibold mt-1">
        {stat.label}
      </div>

    </div>
  );
}
