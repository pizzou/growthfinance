'use client';
import Link from 'next/link';
import { useTenant } from '../layout';

export default function PrivacyPage() {
  const tenant = useTenant();
  if (!tenant) return null;

  return (
    <div className="max-w-3xl mx-auto px-4 py-16">
      <div className="bg-amber-50 border border-amber-300 text-amber-900 rounded-xl px-5 py-4 mb-10 text-sm leading-relaxed">
        <strong>⚠️ Draft template — not legal advice.</strong> This describes, in general terms,
        the categories of data this platform actually collects and stores (based on its own code),
        so it starts from something true rather than generic boilerplate. It still needs review by
        a qualified lawyer for compliance with the data protection law that applies to
        {' '}{tenant.name} (e.g. Rwanda's Law No. 058/2021 on the protection of personal data, or
        the equivalent in your jurisdiction) before this is published to real customers.
      </div>

      <h1 className="text-3xl font-extrabold text-gray-900 mb-2">Privacy Policy</h1>
      <p className="text-sm text-gray-500 mb-10">Last updated: [DATE] — {tenant.name}</p>

      <div className="prose prose-gray max-w-none space-y-8 text-gray-700 text-sm leading-relaxed">
        <section>
          <h2 className="text-lg font-bold text-gray-900 mb-2">1. What we collect</h2>
          <p>When you apply for a loan or use our borrower portal, we collect: your name, date of
          birth, gender, national ID number, phone number, email, home address, marital status
          (and spouse details where applicable), employment and income information, loan purpose
          and amount requested, and any identity or income documents you upload (e.g. national ID,
          proof of address, payslip). We also keep a record of communications, payments, and
          decisions related to your application and loan.</p>
        </section>

        <section>
          <h2 className="text-lg font-bold text-gray-900 mb-2">2. How we use it</h2>
          <p>We use this information to verify your identity, assess your application, service
          your loan if approved, communicate with you about your application or account, meet our
          legal and regulatory obligations (including KYC/AML requirements), and improve our
          services.</p>
        </section>

        <section>
          <h2 className="text-lg font-bold text-gray-900 mb-2">3. How we protect it</h2>
          <p>Sensitive fields — including your national ID, phone number, and address — are
          encrypted in our systems. Access to your information is limited to staff who need it to
          process your application or service your loan, and all such access is logged.</p>
        </section>

        <section>
          <h2 className="text-lg font-bold text-gray-900 mb-2">4. Who we share it with</h2>
          <p>We may share your information with: our payment processing provider to disburse funds
          or collect repayments; a credit reference bureau, where we use one, to assess
          creditworthiness and report repayment history; and regulators or law enforcement where
          required by law. [INSERT: name any additional processors/providers you actually use, and
          confirm whether a data processing agreement is in place with each.] We do not sell your
          personal information.</p>
        </section>

        <section>
          <h2 className="text-lg font-bold text-gray-900 mb-2">5. How long we keep it</h2>
          <p>[INSERT: your actual retention period — typically driven by financial recordkeeping
          regulations in your jurisdiction, often several years after a loan is closed.]</p>
        </section>

        <section>
          <h2 className="text-lg font-bold text-gray-900 mb-2">6. Your rights</h2>
          <p>Depending on where you live, you may have the right to request a copy of the personal
          data we hold about you, ask us to correct inaccurate data, or ask about how your data is
          used. [INSERT: the specific process and contact for exercising these rights under your
          applicable law.]</p>
        </section>

        <section>
          <h2 className="text-lg font-bold text-gray-900 mb-2">7. Contact</h2>
          <p>Questions about this policy or your data can be sent to
          {' '}{tenant.contactEmail || '[contact email]'}{tenant.contactPhone ? ` or ${tenant.contactPhone}` : ''}.</p>
        </section>

        <section>
          <p className="text-xs text-gray-400">See also our <Link href="/terms" className="underline">Terms &amp; Conditions</Link>.</p>
        </section>
      </div>
    </div>
  );
}
