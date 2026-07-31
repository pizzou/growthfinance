'use client';
import { useState, useEffect, createContext, useContext } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { OfflineProvider } from '../../components/OfflineProvider';
import { ToastContainer } from '../../components/ui/ToastContainer';
import { TENANT_SLUG } from '../../lib/tenant';

interface TenantConfig {
  name: string;
  slug: string;
  country: string;
  currency: string;
  primaryColor: string;
  accentColor: string;
  logoUrl?: string;
  contactEmail?: string;
  contactPhone?: string;
  website?: string;
  address?: string;
  tagline?: string;
  mission?: string;
  vision?: string;
  founded?: string;
  registrationNumber?: string;
  socialMedia?: { facebook?: string; instagram?: string; linkedin?: string; twitter?: string; whatsapp?: string };
  mapUrl?: string;
  services?: { title: string; description: string; icon: string; rate: string; rateType?: string; maxAmount: string; term: string }[];
  hero?: { headline: string; subtext: string };
  stats?: { icon: string; value: string; label: string }[];
  testimonials?: { name: string; role: string; text: string; rating: number }[];
  team?: { name: string; role: string; initials: string }[];
}

const TenantCtx = createContext<TenantConfig | null>(null);
export const useTenant = () => useContext(TenantCtx);

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'https://fintech01.onrender.com/api';

const LOGO_DATA_URI = "data:image/png;base64,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";

/** Shown only while the one configured institution's profile is loading. */
const FALLBACK_TENANT: TenantConfig = {
  name: "Loading...", slug: TENANT_SLUG, country: "Rwanda", currency: "RWF",
  primaryColor: "#0D6B3E", accentColor: "#F5A623", services: [],
};

function IconPhone() {
  return <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z"/></svg>;
}
function IconMail() {
  return <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M4 4h16v16H4z" opacity="0"/><path d="M22 6c0-1.1-.9-2-2-2H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6z"/><path d="m22 6-10 7L2 6"/></svg>;
}
function IconShield() {
  return <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="m9 12 2 2 4-4"/></svg>;
}

export default function SiteLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const slug = TENANT_SLUG;
  const [tenant, setTenant]   = useState<TenantConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setNotFound(false);
    fetch(`${API_BASE}/public/tenant/${slug}`)
      .then(r => r.json())
      .then((configRes) => {
        if (cancelled) return;
        const data = configRes?.data;
        if (!data || configRes?.success === false) { setNotFound(true); return; }
        setTenant({ ...FALLBACK_TENANT, ...data, slug });
      })
      .catch(() => { if (!cancelled) setNotFound(true); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [slug]);

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-white">
        <div className="w-8 h-8 border-2 border-[#0D6B3E] border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (notFound || !tenant) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 p-6">
        <div className="bg-white rounded-lg border border-gray-200 p-8 max-w-md text-center">
          <h1 className="font-bold text-gray-900 mb-2">Site temporarily unavailable</h1>
          <p className="text-sm text-gray-500">We couldn't reach our services. Please try again shortly, or contact us directly if this persists.</p>
        </div>
      </div>
    );
  }

  const navLinks = [
    { href: `/`,          label: 'Home' },
    { href: `/services`, label: 'Services' },
    { href: `/about`,    label: 'About Us' },
    { href: `/contact`,  label: 'Contact' },
    { href: `/track`,    label: 'Track Application' },
  ];

  const isActive = (href: string) => pathname === href;
  const primary  = tenant.primaryColor;
  const accent   = tenant.accentColor;

  return (
    <TenantCtx.Provider value={tenant}>
      <OfflineProvider authHeader={() => ({})} />
      <ToastContainer />
      <div className="min-h-screen bg-white font-sans">

        {/* Top utility bar */}
        <div style={{ backgroundColor: '#0B1220' }} className="text-white/80 text-xs py-2 px-4">
          <div className="max-w-7xl mx-auto flex items-center justify-between">
            <div className="flex items-center gap-6">
              {tenant.contactPhone && <span className="flex items-center gap-1.5"><IconPhone /> {tenant.contactPhone}</span>}
              {tenant.contactEmail && <span className="hidden sm:flex items-center gap-1.5"><IconMail /> {tenant.contactEmail}</span>}
            </div>
            <div className="flex items-center gap-1.5 text-white/60">
              <IconShield /> <span className="hidden sm:inline">Licensed &amp; regulated financial institution</span>
              <span className="sm:hidden">Regulated institution</span>
            </div>
          </div>
        </div>

        {/* Main nav */}
        <nav className="bg-white border-b border-gray-200 sticky top-0 z-50">
          <div className="max-w-7xl mx-auto px-4 py-3.5 flex items-center justify-between">
            {/* Brand */}
            <Link href={`/`} className="flex items-center gap-3">
              <img src={LOGO_DATA_URI} alt={`${tenant.name} logo`}
                className="h-11 w-auto object-contain" />
              <div>
                <div className="font-display font-bold text-gray-900 text-lg leading-tight tracking-tight">{tenant.name}</div>
                <div className="text-[11px] font-semibold uppercase tracking-wider" style={{ color: primary }}>{tenant.tagline}</div>
              </div>
            </Link>

            {/* Desktop nav */}
            <div className="hidden md:flex items-center gap-1">
              {navLinks.map(link => (
                <Link key={link.href} href={link.href}
                  className={`px-4 py-2 rounded-md text-sm font-semibold transition-colors border-b-2
                    ${isActive(link.href) ? '' : 'text-gray-600 hover:text-gray-900 border-transparent'}`}
                  style={isActive(link.href) ? { color: primary, borderColor: primary } : {}}>
                  {link.label}
                </Link>
              ))}
              <Link href="/login" className="ml-2 px-4 py-2 rounded-md text-sm font-semibold border transition-colors hover:bg-gray-50"
                style={{ borderColor: '#D1D5DB', color: '#374151' }}>
                Staff Login
              </Link>
              <Link href="/apply" className="ml-1 px-5 py-2.5 rounded-md text-sm font-bold text-white shadow-sm hover:opacity-90 transition-opacity"
                style={{ backgroundColor: primary }}>
                Apply Now
              </Link>
            </div>

            {/* Mobile menu */}
            <button className="md:hidden" onClick={() => setMenuOpen(!menuOpen)} aria-label="Toggle menu">
              <div className="w-6 h-0.5 bg-gray-700 my-1.5" />
              <div className="w-6 h-0.5 bg-gray-700 my-1.5" />
              <div className="w-6 h-0.5 bg-gray-700 my-1.5" />
            </button>
          </div>

          {menuOpen && (
            <div className="md:hidden border-t border-gray-100 px-4 py-3 space-y-1">
              {navLinks.map(link => (
                <Link key={link.href} href={link.href} onClick={() => setMenuOpen(false)}
                  className="block px-4 py-2.5 rounded-md text-sm font-semibold text-gray-700 hover:bg-gray-50">
                  {link.label}
                </Link>
              ))}
              <Link href="/apply" onClick={() => setMenuOpen(false)}
                className="block px-4 py-2.5 rounded-md text-sm font-bold text-white text-center mt-2" style={{ backgroundColor: primary }}>
                Apply Now
              </Link>
              <Link href="/login" onClick={() => setMenuOpen(false)} className="block px-4 py-2.5 text-sm font-semibold" style={{ color: primary }}>
                Staff Login →
              </Link>
            </div>
          )}
        </nav>

        {/* Page content */}
        <main>{children}</main>

        {/* Footer */}
        <footer style={{ backgroundColor: '#0B1220' }} className="text-white mt-16">
          <div className="max-w-7xl mx-auto px-4 py-12 grid grid-cols-1 md:grid-cols-4 gap-8">
            <div className="md:col-span-2">
              <div className="font-display text-xl font-bold mb-2">{tenant.name}</div>
              <div className="text-white/60 text-sm leading-relaxed mb-4 max-w-md">{tenant.mission}</div>
              <div className="text-sm text-white/50 space-y-1">
                <div className="flex items-center gap-2">{tenant.address}</div>
                <div className="flex items-center gap-2"><IconPhone /> {tenant.contactPhone}</div>
                <div className="flex items-center gap-2"><IconMail /> {tenant.contactEmail}</div>
              </div>
            </div>
            <div>
              <div className="font-semibold mb-4 text-white/90 text-sm uppercase tracking-wider">Quick Links</div>
              <div className="space-y-2 text-sm text-white/60">
                {navLinks.map(l => (
                  <Link key={l.href} href={l.href} className="block hover:text-white transition">{l.label}</Link>
                ))}
              </div>
            </div>
            <div>
              <div className="font-semibold mb-4 text-white/90 text-sm uppercase tracking-wider">Our Services</div>
              <div className="space-y-2 text-sm text-white/60">
                {tenant.services?.slice(0,5).map(s => (
                  <div key={s.title}>{s.title}</div>
                ))}
              </div>
            </div>
          </div>
          <div className="border-t border-white/10 px-4 py-4">
            <div className="max-w-7xl mx-auto flex flex-col md:flex-row items-center justify-between text-xs text-white/40 gap-2">
              <span>© {new Date().getFullYear()} {tenant.name}. All rights reserved. {tenant.registrationNumber ? `Reg. No. ${tenant.registrationNumber}` : ''}</span>
              <span className="flex items-center gap-4">
                <Link href="/terms" className="hover:text-white/70 transition">Terms &amp; Conditions</Link>
                <Link href="/privacy" className="hover:text-white/70 transition">Privacy Policy</Link>
              </span>
              <span>Your deposits and data are protected in line with applicable financial regulations.</span>
            </div>
          </div>
        </footer>
      </div>
    </TenantCtx.Provider>
  );
}