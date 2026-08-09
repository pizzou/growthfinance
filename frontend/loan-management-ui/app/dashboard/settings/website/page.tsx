'use client';
import { useEffect, useState } from 'react';
import { get, put } from '../../../../services/api';
import { toast } from '../../../../hooks/useToast';
import { PageSpinner } from '../../../../components/ui/Skeleton';

interface Stat        { icon: string; value: string; label: string; }
interface Service      { title: string; icon: string; rate: string; maxAmount: string; term: string; description: string; }
interface Testimonial  { name: string; role: string; text: string; rating: number; }
interface TeamMember   { name: string; role: string; initials: string; }

interface OrgData {
  id: number;
  name: string;
  logoUrl?: string;
  primaryColor?: string;
  accentColor?: string;
  website?: string;
  contactEmail?: string;
  contactPhone?: string;
  address?: string;
  tagline?: string;
  mission?: string;
  vision?: string;
  foundedYear?: number;
  mapUrl?: string;
  facebookUrl?: string;
  instagramUrl?: string;
  linkedinUrl?: string;
  twitterUrl?: string;
  whatsappUrl?: string;
  hero?: { headline: string; subtext: string };
  stats?: Stat[];
  services?: Service[];
  testimonials?: Testimonial[];
  team?: TeamMember[];
}

const DEFAULT_STATS: Stat[] = [
  { icon: '👥', value: '5,000+', label: 'Happy Clients' },
  { icon: '💰', value: 'RWF 2B+', label: 'Loans Disbursed' },
  { icon: '⚡', value: '24 hrs', label: 'Average Approval' },
  { icon: '⭐', value: '98%', label: 'Client Satisfaction' },
];
const DEFAULT_TESTIMONIALS: Testimonial[] = [
  { name: 'Amina K.', role: 'Small Business Owner', text: 'Great service and fast approval.', rating: 5 },
];
const DEFAULT_TEAM: TeamMember[] = [
  { name: 'Jane Doe', role: 'Chief Executive Officer', initials: 'JD' },
];

function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="block text-sm font-medium text-gray-700 mb-1">{label}</label>
      {children}
      {hint && <p className="text-xs text-gray-400 mt-1">{hint}</p>}
    </div>
  );
}

const inputCls = 'w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-teal-500';
const smallInputCls = 'w-full border border-gray-300 rounded-lg px-2.5 py-2 text-xs focus:outline-none focus:ring-2 focus:ring-teal-500';

function RepeaterSection<T>({ title, hint, items, onChange, newItem, renderRow }: {
  title: string; hint?: string; items: T[]; onChange: (items: T[]) => void; newItem: T;
  renderRow: (item: T, update: (patch: Partial<T>) => void) => React.ReactNode;
}) {
  const update = (idx: number, patch: Partial<T>) => {
    const next = [...items];
    next[idx] = { ...next[idx], ...patch };
    onChange(next);
  };
  const remove = (idx: number) => onChange(items.filter((_, i) => i !== idx));
  const add = () => onChange([...items, newItem]);

  return (
    <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-4">
      <div className="flex items-center justify-between border-b border-gray-100 pb-3">
        <div>
          <h2 className="font-semibold text-gray-800">{title}</h2>
          {hint && <p className="text-xs text-gray-400 mt-0.5">{hint}</p>}
        </div>
        <button onClick={add} className="text-teal-600 text-sm font-semibold hover:underline whitespace-nowrap">+ Add</button>
      </div>
      <div className="space-y-3">
        {items.length === 0 && <p className="text-sm text-gray-400 italic">Nothing added yet — click "+ Add" to create one.</p>}
        {items.map((item, idx) => (
          <div key={idx} className="border border-gray-100 rounded-lg p-4 bg-gray-50/50 relative">
            <button onClick={() => remove(idx)}
              className="absolute top-2 right-2 text-gray-300 hover:text-red-500 text-xs font-bold w-6 h-6 flex items-center justify-center">✕</button>
            {renderRow(item, (patch) => update(idx, patch))}
          </div>
        ))}
      </div>
    </div>
  );
}

export default function WebsiteSettingsPage() {
  const [org, setOrg]         = useState<OrgData | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving]   = useState(false);
  // Single-tenant deployment — the public site is always the root URL, not a
  // /{slug} path. (Previously this looked up the org's slug from /public/tenants
  // and linked to /${slug}, which 404'd once the tenant-picker routing was removed.)
  const liveUrl = '/';

  useEffect(() => {
    get('/organizations/me').then((d) => {
      const data = d as OrgData;
      setOrg({
        ...data,
        hero: data.hero ?? { headline: '', subtext: '' },
        stats: data.stats?.length ? data.stats : DEFAULT_STATS,
        testimonials: data.testimonials?.length ? data.testimonials : DEFAULT_TESTIMONIALS,
        team: data.team?.length ? data.team : DEFAULT_TEAM,
      });
    }).catch(console.error).finally(() => setLoading(false));
  }, []);

  const set = (patch: Partial<OrgData>) => setOrg((o) => (o ? { ...o, ...patch } : o));

  const handleSave = async () => {
    if (!org) return;
    setSaving(true);
    try {
      await put('/organizations/me', org);
      toast('success', 'Website content updated');
    } catch (err) {
      toast('error', err instanceof Error ? err.message : 'Could not save changes');
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <PageSpinner />;
  if (!org) return <div className="text-sm text-gray-500">Could not load organization settings.</div>;

  return (
    <div className="space-y-6 max-w-3xl pb-10">
      <div className="flex items-center justify-between sticky top-0 bg-gray-50/95 backdrop-blur z-10 py-3 -mx-1 px-1">
        <div>
          <h1 className="text-xl font-bold text-gray-900">Website Content</h1>
          <p className="text-sm text-gray-500">Full control over your public site — home, services, about, and contact.</p>
        </div>
        <div className="flex items-center gap-3">
          {liveUrl && (
            <a href={liveUrl} target="_blank" rel="noopener" className="text-teal-600 text-sm font-semibold hover:underline whitespace-nowrap">
              View live site ↗
            </a>
          )}
          <button onClick={handleSave} disabled={saving}
            className="bg-teal-600 hover:bg-teal-700 text-white px-5 py-2 rounded-lg text-sm font-semibold disabled:opacity-60 transition whitespace-nowrap">
            {saving ? 'Saving…' : 'Save Changes'}
          </button>
        </div>
      </div>

      {/* Branding */}
      <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-5">
        <h2 className="font-semibold text-gray-800 border-b border-gray-100 pb-3">Branding</h2>
        <div className="grid grid-cols-2 gap-4">
          <Field label="Organization Name">
            <input className={inputCls} value={org.name ?? ''} onChange={(e) => set({ name: e.target.value })} />
          </Field>
          <Field label="Tagline" hint="Shown under your logo on the site">
            <input className={inputCls} value={org.tagline ?? ''} onChange={(e) => set({ tagline: e.target.value })} />
          </Field>
          <Field label="Primary Color">
            <div className="flex items-center gap-2">
              <input type="color" value={org.primaryColor ?? '#0D6B3E'} onChange={(e) => set({ primaryColor: e.target.value })}
                className="w-10 h-10 rounded border border-gray-300 cursor-pointer" />
              <input className={inputCls} value={org.primaryColor ?? ''} onChange={(e) => set({ primaryColor: e.target.value })} />
            </div>
          </Field>
          <Field label="Accent Color">
            <div className="flex items-center gap-2">
              <input type="color" value={org.accentColor ?? '#F5A623'} onChange={(e) => set({ accentColor: e.target.value })}
                className="w-10 h-10 rounded border border-gray-300 cursor-pointer" />
              <input className={inputCls} value={org.accentColor ?? ''} onChange={(e) => set({ accentColor: e.target.value })} />
            </div>
          </Field>
          <Field label="Logo URL"><input className={inputCls} value={org.logoUrl ?? ''} onChange={(e) => set({ logoUrl: e.target.value })} placeholder="https://..." /></Field>
          <Field label="Founded Year">
            <input type="number" className={inputCls} value={org.foundedYear ?? ''} onChange={(e) => set({ foundedYear: Number(e.target.value) || undefined })} />
          </Field>
        </div>
      </div>

      {/* Home Page Hero */}
      <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-5">
        <h2 className="font-semibold text-gray-800 border-b border-gray-100 pb-3">Home Page — Hero Banner</h2>
        <Field label="Headline">
          <input className={inputCls} value={org.hero?.headline ?? ''}
            onChange={(e) => set({ hero: { headline: e.target.value, subtext: org.hero?.subtext ?? '' } })}
            placeholder="Your Trusted Financial Partner" />
        </Field>
        <Field label="Subtext">
          <textarea rows={2} className={inputCls} value={org.hero?.subtext ?? ''}
            onChange={(e) => set({ hero: { headline: org.hero?.headline ?? '', subtext: e.target.value } })} />
        </Field>
      </div>

      {/* Stats */}
      <RepeaterSection title="Home Page — Stats" hint="4 highlight numbers shown on your homepage"
        items={org.stats ?? []} onChange={(stats) => set({ stats })} newItem={{ icon: '📈', value: '', label: '' }}
        renderRow={(s, update) => (
          <div className="grid grid-cols-3 gap-2">
            <input className={smallInputCls} value={s.icon} onChange={(e) => update({ icon: e.target.value })} placeholder="Emoji" />
            <input className={smallInputCls} value={s.value} onChange={(e) => update({ value: e.target.value })} placeholder="Value e.g. 5,000+" />
            <input className={smallInputCls} value={s.label} onChange={(e) => update({ label: e.target.value })} placeholder="Label e.g. Happy Clients" />
          </div>
        )} />

      {/* Services now point to real Loan Products */}
      <div className="bg-white rounded-xl border border-gray-200 p-6 flex items-center justify-between">
        <div>
          <h2 className="font-semibold text-gray-800">Loan Products / Services</h2>
          <p className="text-xs text-gray-400 mt-1">Your real rates and limits now live on a dedicated page — they drive both your website and actual loan approvals.</p>
        </div>
        <a href="/dashboard/products" className="text-teal-600 text-sm font-semibold hover:underline whitespace-nowrap">
          Manage Products →
        </a>
      </div>

      {/* Our Story */}
      <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-5">
        <h2 className="font-semibold text-gray-800 border-b border-gray-100 pb-3">About Page — Our Story</h2>
        <Field label="Mission"><textarea rows={3} className={inputCls} value={org.mission ?? ''} onChange={(e) => set({ mission: e.target.value })} /></Field>
        <Field label="Vision"><textarea rows={3} className={inputCls} value={org.vision ?? ''} onChange={(e) => set({ vision: e.target.value })} /></Field>
      </div>

      {/* Team */}
      <RepeaterSection title="About Page — Leadership Team"
        items={org.team ?? []} onChange={(team) => set({ team })}
        newItem={{ name: '', role: '', initials: '' }}
        renderRow={(m, update) => (
          <div className="grid grid-cols-3 gap-2">
            <input className={smallInputCls} value={m.name} onChange={(e) => update({ name: e.target.value })} placeholder="Full name" />
            <input className={smallInputCls} value={m.role} onChange={(e) => update({ role: e.target.value })} placeholder="Role / title" />
            <input className={smallInputCls} value={m.initials} onChange={(e) => update({ initials: e.target.value.toUpperCase().slice(0,2) })} placeholder="Initials" />
          </div>
        )} />

      {/* Testimonials */}
      <RepeaterSection title="Home Page — Client Testimonials"
        items={org.testimonials ?? []} onChange={(testimonials) => set({ testimonials })}
        newItem={{ name: '', role: '', text: '', rating: 5 }}
        renderRow={(t, update) => (
          <div className="space-y-2">
            <div className="grid grid-cols-2 gap-2">
              <input className={smallInputCls} value={t.name} onChange={(e) => update({ name: e.target.value })} placeholder="Client name" />
              <input className={smallInputCls} value={t.role} onChange={(e) => update({ role: e.target.value })} placeholder="Role e.g. Small Business Owner" />
            </div>
            <textarea className={smallInputCls} rows={2} value={t.text} onChange={(e) => update({ text: e.target.value })} placeholder="Testimonial text" />
            <select className={smallInputCls} value={t.rating} onChange={(e) => update({ rating: Number(e.target.value) })}>
              {[5,4,3,2,1].map(n => <option key={n} value={n}>{n} star{n > 1 ? 's' : ''}</option>)}
            </select>
          </div>
        )} />

      {/* Contact & Location */}
      <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-5">
        <h2 className="font-semibold text-gray-800 border-b border-gray-100 pb-3">Contact & Location</h2>
        <div className="grid grid-cols-2 gap-4">
          <Field label="Contact Email"><input className={inputCls} value={org.contactEmail ?? ''} onChange={(e) => set({ contactEmail: e.target.value })} /></Field>
          <Field label="Contact Phone"><input className={inputCls} value={org.contactPhone ?? ''} onChange={(e) => set({ contactPhone: e.target.value })} /></Field>
          <Field label="Website"><input className={inputCls} value={org.website ?? ''} onChange={(e) => set({ website: e.target.value })} placeholder="https://..." /></Field>
          <Field label="Address"><input className={inputCls} value={org.address ?? ''} onChange={(e) => set({ address: e.target.value })} /></Field>
        </div>
        <Field label="Google Maps Embed URL" hint="Optional — shown on your Contact page">
          <input className={inputCls} value={org.mapUrl ?? ''} onChange={(e) => set({ mapUrl: e.target.value })} placeholder="https://www.google.com/maps/embed?..." />
        </Field>
      </div>

      {/* Social */}
      <div className="bg-white rounded-xl border border-gray-200 p-6 space-y-5">
        <h2 className="font-semibold text-gray-800 border-b border-gray-100 pb-3">Social Media</h2>
        <div className="grid grid-cols-2 gap-4">
          <Field label="Facebook"><input className={inputCls} value={org.facebookUrl ?? ''} onChange={(e) => set({ facebookUrl: e.target.value })} /></Field>
          <Field label="Instagram"><input className={inputCls} value={org.instagramUrl ?? ''} onChange={(e) => set({ instagramUrl: e.target.value })} /></Field>
          <Field label="LinkedIn"><input className={inputCls} value={org.linkedinUrl ?? ''} onChange={(e) => set({ linkedinUrl: e.target.value })} /></Field>
          <Field label="Twitter / X"><input className={inputCls} value={org.twitterUrl ?? ''} onChange={(e) => set({ twitterUrl: e.target.value })} /></Field>
          <Field label="WhatsApp Link" hint="e.g. https://wa.me/250788000000">
            <input className={inputCls} value={org.whatsappUrl ?? ''} onChange={(e) => set({ whatsappUrl: e.target.value })} />
          </Field>
        </div>
      </div>

      <div className="flex justify-end gap-3 pb-8">
        <button onClick={handleSave} disabled={saving}
          className="bg-teal-600 hover:bg-teal-700 text-white px-6 py-2.5 rounded-lg text-sm font-semibold disabled:opacity-60 transition">
          {saving ? 'Saving…' : 'Save Changes'}
        </button>
      </div>
    </div>
  );
}