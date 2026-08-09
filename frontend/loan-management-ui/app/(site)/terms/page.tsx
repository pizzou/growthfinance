'use client';
import Link from 'next/link';
import { useTenant } from '../layout';

export default function TermsPage() {
  const tenant = useTenant();
  if (!tenant) return null;

  return (
    <div className="max-w-3xl mx-auto px-4 py-16">
      <div className="bg-amber-50 border border-amber-300 text-amber-900 rounded-xl px-5 py-4 mb-10 text-sm leading-relaxed">
        <strong>⚠️ Draft template — not legal advice.</strong> This page was generated as a starting
        point so the application form has somewhere real to link to instead of a dead link. It is
        <em> not</em> a substitute for review by a qualified lawyer licensed in {tenant.country || 'your'}
        {' '}jurisdiction, and must not be published to real customers until that review is complete
        and it reflects your actual licensing terms, regulatory obligations, and business practices.
      </div>

      <h1 className="text-3xl font-extrabold text-gray-900 mb-2">Terms &amp; Conditions</h1>
      <p className="text-sm text-gray-500 mb-10">Last updated: [DATE] — {tenant.name}</p>

      <div className="prose prose-gray max-w-none space-y-8 text-gray-700 text-sm leading-relaxed">
        <section>
          <h2 className="text-lg font-bold text-gray-900 mb-2">1. Acceptance of these terms</h2>
          <p>By submitting a loan application through this website, you confirm that you are at
          least 18 years old, that the information you provide is true and complete, and that you
          agree to be bound by these Terms &amp; Conditions and our <Link href="/privacy" className="underline font-medium">Privacy Policy</Link>.</p>
        </section>

        <section>
          <h2 className="text-lg font-bold text-gray-900 mb-2">2. Nature of an application</h2>
          <p>Submitting an application is a request to be considered for a loan from {tenant.name}.
          It is not an offer, approval, or guarantee of credit. {tenant.name} evaluates every
          application at its sole discretion, including identity verification, creditworthiness,
          and internal risk policy, and may approve, decline, or request more information before
          deciding.</p>
        </section>

        <section>
          <h2 className="text-lg font-bold text-gray-900 mb-2">3. Verification and documentation</h2>
          <p>You authorize {tenant.name} to verify the information you provide, including your
          identity, employment, income, and any documents you upload, and to contact you, your
          employer, or references for that purpose. {tenant.name} may decline or pause your
          application if required documents are missing, unclear, or cannot be verified.</p>
        </section>

        <section>
          <h2 className="text-lg font-bold text-gray-900 mb-2">4. Interest, fees, and repayment</h2>
          <p>If approved, your loan agreement will state the principal amount, interest rate,
          repayment schedule, applicable fees, and any penalties for late or missed payments.
          These figures are specific to your approved loan and are not fixed by this page — refer
          to your individual loan agreement and repayment schedule for the terms that apply to you.</p>
        </section>

        <section>
          <h2 className="text-lg font-bold text-gray-900 mb-2">5. Default and collections</h2>
          <p>Failure to repay according to your agreed schedule may result in penalty charges,
          restructuring, referral to collections, reporting to a credit reference bureau where
          applicable, and other consequences described in your individual loan agreement.</p>
        </section>

        <section>
          <h2 className="text-lg font-bold text-gray-900 mb-2">6. Communications</h2>
          <p>By applying, you consent to receive communications about your application and any
          resulting loan by phone, SMS, and email, including automated messages and reminders.</p>
        </section>

        <section>
          <h2 className="text-lg font-bold text-gray-900 mb-2">7. Changes to these terms</h2>
          <p>{tenant.name} may update these terms from time to time. Continued use of this site
          after an update constitutes acceptance of the revised terms.</p>
        </section>

        <section>
          <h2 className="text-lg font-bold text-gray-900 mb-2">8. Governing law</h2>
          <p>[INSERT: governing law and jurisdiction, dispute resolution process, and regulator
          name/contact if applicable — this must be confirmed with legal counsel.]</p>
        </section>

        <section>
          <h2 className="text-lg font-bold text-gray-900 mb-2">9. Contact</h2>
          <p>Questions about these terms can be sent to {tenant.contactEmail || '[contact email]'}
          {tenant.contactPhone ? ` or ${tenant.contactPhone}` : ''}.</p>
        </section>
      </div>
    </div>
  );
}
