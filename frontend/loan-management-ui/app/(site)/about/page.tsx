'use client';
import Link from 'next/link';
import { useTenant } from '../layout';

export default function AboutPage() {
  const tenant = useTenant();
  if (!tenant) return null;
  const primary = tenant.primaryColor;
  const accent  = tenant.accentColor;

  return (
    <div>
      {/* Hero */}
      <section className="py-20 text-white relative overflow-hidden"
        style={{ background: `linear-gradient(135deg, ${primary} 0%, #0a4a2b 100%)` }}>
        <div className="max-w-7xl mx-auto px-4 grid md:grid-cols-2 gap-12 items-center">
          <div>
            <div className="text-sm font-bold uppercase tracking-widest mb-4 opacity-70">About Us</div>
            <h1 className="text-4xl md:text-5xl font-extrabold mb-6">
              Empowering Rwandans<br/>
              <span style={{ color: accent }}>Since {tenant.founded}</span>
            </h1>
            <p className="text-white/80 text-lg leading-relaxed">{tenant.mission}</p>
          </div>
          <div className="grid grid-cols-2 gap-4">
            {[
              { icon: '🏆', label: 'Founded', value: tenant.founded! },
              { icon: '📍', label: 'Location', value: tenant.address?.split(',').slice(-2).join(',').trim() || tenant.country || 'Rwanda' },
              { icon: '🇷🇼', label: 'Reg. No.', value: tenant.registrationNumber! },
              { icon: '💼', label: 'Services', value: `${tenant.services?.length ?? 6} Products` },
            ].map(item => (
              <div key={item.label} className="bg-white/10 rounded-2xl p-5 backdrop-blur-sm">
                <div className="text-3xl mb-2">{item.icon}</div>
                <div className="text-white/60 text-xs uppercase tracking-wider">{item.label}</div>
                <div className="text-white font-bold mt-0.5">{item.value}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Mission / Vision / Values */}
      <section className="py-20 max-w-7xl mx-auto px-4">
        <div className="grid md:grid-cols-3 gap-8">
          {[
            { icon: '🎯', title: 'Our Mission', text: tenant.mission ?? '' },
            { icon: '🔭', title: 'Our Vision',  text: tenant.vision  ?? 'To be the most trusted financial institution in East Africa, creating lasting prosperity for every client we serve.' },
            { icon: '💎', title: 'Our Values',  text: 'Integrity · Transparency · Innovation · Inclusion · Excellence. We believe everyone deserves access to fair and dignified financial services.' },
          ].map(item => (
            <div key={item.title}
              className="text-center p-8 rounded-3xl border border-gray-100 shadow-sm hover:shadow-md transition-all">
              <div className="text-5xl mb-4">{item.icon}</div>
              <h3 className="text-xl font-extrabold mb-3" style={{ color: primary }}>{item.title}</h3>
              <p className="text-gray-600 leading-relaxed">{item.text}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Why choose us */}
      <section className="py-20" style={{ backgroundColor: primary + '06' }}>
        <div className="max-w-7xl mx-auto px-4">
          <div className="text-center mb-12">
            <h2 className="text-3xl font-extrabold text-gray-900 mb-4">Why Choose {tenant.name}?</h2>
            <p className="text-gray-500 text-lg">We are different — and here is why thousands of Rwandans trust us</p>
          </div>
          <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
            {[
              { icon:'⚡', title:'Fast Approval',        desc:'Applications reviewed within 24 hours. No lengthy waiting periods.' },
              { icon:'📱', title:'Apply from Anywhere',   desc:'Our online platform lets you apply on your phone, tablet or computer.' },
              { icon:'🔒', title:'Safe & Secure',         desc:'Your data is protected with bank-grade encryption and security.' },
              { icon:'💬', title:'Dedicated Support',     desc:'Our team is available 6 days a week to assist with your application.' },
              { icon:'🤝', title:'No Hidden Fees',        desc:'All charges are clearly disclosed upfront. What we quote is what you pay.' },
              { icon:'🌍', title:'Serving All Rwandans', desc:'We serve urban and rural clients alike — including farmers and cooperatives.' },
            ].map(item => (
              <div key={item.title} className="flex gap-4 bg-white rounded-2xl p-6 shadow-sm border border-gray-100">
                <div className="w-12 h-12 rounded-xl flex items-center justify-center text-2xl flex-shrink-0"
                  style={{ backgroundColor: primary + '15' }}>
                  {item.icon}
                </div>
                <div>
                  <div className="font-bold text-gray-900 mb-1">{item.title}</div>
                  <div className="text-gray-500 text-sm">{item.desc}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Team */}
      <section className="py-20 max-w-7xl mx-auto px-4">
        <div className="text-center mb-12">
          <h2 className="text-3xl font-extrabold text-gray-900 mb-4">Our Leadership Team</h2>
          <p className="text-gray-500">Experienced professionals committed to your financial success</p>
        </div>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
          {(tenant.team ?? []).map(person => (
            <div key={person.name} className="text-center">
              <div className="w-20 h-20 rounded-full flex items-center justify-center text-white font-black text-xl mx-auto mb-4 shadow-lg"
                style={{ backgroundColor: primary }}>
                {person.initials}
              </div>
              <div className="font-bold text-gray-900">{person.name}</div>
              <div className="text-gray-500 text-sm mt-0.5">{person.role}</div>
            </div>
          ))}
        </div>
      </section>

      {/* CTA */}
      <section className="py-16 text-center px-4">
        <div className="max-w-2xl mx-auto">
          <h2 className="text-3xl font-extrabold text-gray-900 mb-4">Ready to Partner with Us?</h2>
          <p className="text-gray-500 mb-8">Join thousands of Rwandans who have achieved their goals with our support.</p>
          <Link href={`/apply`}
            className="inline-block px-12 py-4 rounded-full text-white font-bold text-lg shadow-xl hover:opacity-90 transition"
            style={{ backgroundColor: primary }}>
            Apply for a Loan →
          </Link>
        </div>
      </section>
    </div>
  );
}
