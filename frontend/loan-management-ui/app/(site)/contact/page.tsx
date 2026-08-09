'use client';
import { useState } from 'react';
import { useTenant } from '../layout';
import { TENANT_SLUG } from '../../../lib/tenant';

export default function ContactPage() {
  const tenant = useTenant();
  const [form, setForm]       = useState({ name:'', email:'', phone:'', subject:'', message:'' });
  const [sent, setSent]       = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError]     = useState('');

  if (!tenant) return null;
  const primary = tenant.primaryColor;
  const accent  = tenant.accentColor;

  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement|HTMLSelectElement|HTMLTextAreaElement>) =>
    setForm(f => ({...f, [k]: e.target.value}));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault(); setSending(true); setError('');
    try {
      const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
      const res = await fetch(`${API_BASE}/public/contact`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...form, tenantSlug: TENANT_SLUG }),
      });
      const json = await res.json();
      if (!res.ok || json.success === false) throw new Error(json.error || json.message || 'Could not send your message.');
      setSent(true);
    } catch (err: any) {
      setError(err.message || 'Something went wrong. Please try again.');
    } finally {
      setSending(false);
    }
  };

  const inp = "w-full px-4 py-3 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 transition-colors mt-1.5";
  const inpStyle = { focusBorderColor: primary };

  return (
    <div>
      {/* Hero */}
      <section className="py-20 text-white text-center"
        style={{ background: `linear-gradient(135deg, ${primary} 0%, #0a4a2b 100%)` }}>
        <div className="max-w-3xl mx-auto px-4">
          <h1 className="text-4xl md:text-5xl font-extrabold mb-4">Get in Touch</h1>
          <p className="text-white/80 text-lg">
            We are here to help. Reach out by phone, email, or visit our office in Kigali.
          </p>
        </div>
      </section>

      <div className="max-w-7xl mx-auto px-4 py-20">
        <div className="grid md:grid-cols-2 gap-12">

          {/* Contact info */}
          <div>
            <h2 className="text-2xl font-extrabold text-gray-900 mb-8">Contact Information</h2>

            <div className="space-y-6 mb-10">
              {[
                { icon:'📍', label:'Our Office',    value: tenant.address ?? 'Kigali, Rwanda', sub:'Monday – Friday: 8:00 AM – 5:00 PM\nSaturday: 8:00 AM – 1:00 PM' },
                { icon:'📞', label:'Phone',         value: tenant.contactPhone ?? '+250 788 000 000', sub:'Available Mon–Sat, 8AM–5PM EAT' },
                { icon:'✉️', label:'Email',         value: tenant.contactEmail ?? 'info@growthfinance.rw', sub:'We reply within 24 hours' },
                { icon:'💬', label:'WhatsApp',      value: 'Chat with us instantly', sub: tenant.socialMedia?.whatsapp ?? '' },
              ].map(item => (
                <div key={item.label} className="flex gap-4">
                  <div className="w-12 h-12 rounded-xl flex items-center justify-center text-xl flex-shrink-0"
                    style={{ backgroundColor: primary + '15' }}>
                    {item.icon}
                  </div>
                  <div>
                    <div className="font-bold text-gray-900">{item.label}</div>
                    <div className="text-gray-700 text-sm font-semibold">{item.value}</div>
                    {item.sub && <div className="text-gray-400 text-xs mt-0.5 whitespace-pre-line">{item.sub}</div>}
                  </div>
                </div>
              ))}
            </div>

            {/* Social media */}
            <div>
              <div className="text-sm font-bold text-gray-500 uppercase tracking-wider mb-4">Follow Us</div>
              <div className="flex gap-3">
                {[
                  { label:'Facebook',  icon:'f',  href: tenant.socialMedia?.facebook },
                  { label:'Instagram', icon:'ig', href: tenant.socialMedia?.instagram },
                  { label:'LinkedIn',  icon:'in', href: tenant.socialMedia?.linkedin },
                  { label:'Twitter',   icon:'𝕏',  href: tenant.socialMedia?.twitter },
                  { label:'WhatsApp',  icon:'💬', href: tenant.socialMedia?.whatsapp },
                ].filter(s => s.href).map(s => (
                  <a key={s.label} href={s.href!} target="_blank" rel="noopener"
                    className="w-11 h-11 rounded-xl flex items-center justify-center font-bold text-white text-sm hover:opacity-80 transition"
                    style={{ backgroundColor: primary }}>
                    {s.icon}
                  </a>
                ))}
              </div>
            </div>

            {/* Google Map */}
            {tenant.mapUrl && (
              <div className="mt-8 rounded-2xl overflow-hidden border border-gray-200 shadow-sm">
                <iframe
                  src={tenant.mapUrl}
                  width="100%" height="300"
                  style={{ border: 0 }}
                  allowFullScreen loading="lazy"
                  referrerPolicy="no-referrer-when-downgrade"
                  title="Office Location" />
              </div>
            )}
          </div>

          {/* Contact form */}
          <div>
            <h2 className="text-2xl font-extrabold text-gray-900 mb-8">Send Us a Message</h2>

            {sent ? (
              <div className="text-center py-16 bg-green-50 rounded-3xl border border-green-200">
                <div className="text-5xl mb-4">✅</div>
                <h3 className="text-xl font-extrabold text-green-800 mb-2">Message Received!</h3>
                <p className="text-green-700">Thank you {form.name}. We'll get back to you within 24 hours.</p>
                <button onClick={() => { setSent(false); setForm({ name:'',email:'',phone:'',subject:'',message:'' }); }}
                  className="mt-6 px-8 py-3 rounded-xl text-white font-bold hover:opacity-90 transition"
                  style={{ backgroundColor: primary }}>
                  Send Another Message
                </button>
              </div>
            ) : (
              <form onSubmit={handleSubmit} className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="text-xs font-bold text-gray-500 uppercase tracking-wider">Full Name <span className="text-red-500">*</span></label>
                    <input required className={inp} value={form.name} onChange={set('name')} placeholder="Your full name" />
                  </div>
                  <div>
                    <label className="text-xs font-bold text-gray-500 uppercase tracking-wider">Phone <span className="text-red-500">*</span></label>
                    <input required className={inp} value={form.phone} onChange={set('phone')} placeholder="+250 7XX XXX XXX" />
                  </div>
                </div>
                <div>
                  <label className="text-xs font-bold text-gray-500 uppercase tracking-wider">Email Address</label>
                  <input type="email" className={inp} value={form.email} onChange={set('email')} placeholder="your@email.com" />
                </div>
                <div>
                  <label className="text-xs font-bold text-gray-500 uppercase tracking-wider">Subject <span className="text-red-500">*</span></label>
                  <select required className={inp} value={form.subject} onChange={set('subject')}>
                    <option value="">Select a topic…</option>
                    <option>Loan Inquiry</option>
                    <option>Application Status</option>
                    <option>Repayment Query</option>
                    <option>Complaint or Feedback</option>
                    <option>Partnership / Business</option>
                    <option>Other</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs font-bold text-gray-500 uppercase tracking-wider">Message <span className="text-red-500">*</span></label>
                  <textarea required className={`${inp} min-h-[140px] resize-y`}
                    value={form.message} onChange={set('message')}
                    placeholder="Tell us how we can help you…" />
                </div>
                <button type="submit" disabled={sending}
                  className="w-full py-4 rounded-xl text-white font-bold text-lg hover:opacity-90 transition flex items-center justify-center gap-3 disabled:opacity-60"
                  style={{ backgroundColor: primary }}>
                  {sending && <span className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />}
                  {sending ? 'Sending…' : 'Send Message ✓'}
                </button>
                {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg px-3 py-2">{error}</div>}
              </form>
            )}

            {/* Quick links */}
            <div className="mt-8 grid grid-cols-2 gap-3">
              <a href={`tel:${tenant.contactPhone}`}
                className="flex items-center gap-3 p-4 bg-gray-50 rounded-xl border border-gray-200 hover:border-gray-300 transition text-sm font-semibold text-gray-700">
                <span className="text-2xl">📞</span> Call Us Now
              </a>
              <a href={tenant.socialMedia?.whatsapp ?? '#'}
                className="flex items-center gap-3 p-4 rounded-xl border text-sm font-semibold text-white hover:opacity-90 transition"
                style={{ backgroundColor: '#25D366' }}>
                <span className="text-2xl">💬</span> WhatsApp Us
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
