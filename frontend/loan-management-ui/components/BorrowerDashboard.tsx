'use client';

import React from 'react';

interface PaymentHistory {
  paymentId: number;
  paymentDate: string;
  amount: number;
  method: string;
  status: string;
}

interface Installment {
  installmentNumber: number;
  dueDate: string;
  amount: number;
  principal: number;
  interest: number;
  status: string;
}

interface Dashboard {

  loanId:number;

  referenceNumber:string;

  borrowerName:string;

  status:string;

  loanType:string;

  principal:number;

  outstandingBalance:number;

  totalPaid:number;

  totalRepayable:number;

  interestRate:number;

  currency:string;

  nextInstallmentAmount:number;

  nextPaymentDate:string;

  maturityDate:string;

  repaymentProgress:number;

  activeLoans:number;

  overdueLoans:number;

  completedLoans:number;

  recentPayments:PaymentHistory[];

  upcomingInstallments:Installment[];

  availablePaymentMethods:string[];

}

export default function BorrowerDashboard({

dashboard

}:{dashboard:Dashboard}){

return(

<div className="space-y-6">

{/* HEADER */}

<div className="bg-white rounded-xl shadow p-6">

<h2 className="text-2xl font-bold">

Welcome {dashboard.borrowerName}

</h2>

<p className="text-gray-500">

Reference

<strong>

{dashboard.referenceNumber}

</strong>

</p>

</div>

{/* SUMMARY */}

<div className="grid md:grid-cols-4 gap-4">

<Card title="Principal"

value={`${dashboard.currency} ${dashboard.principal.toLocaleString()}`}/>

<Card title="Outstanding"

value={`${dashboard.currency} ${dashboard.outstandingBalance.toLocaleString()}`}/>

<Card title="Total Paid"

value={`${dashboard.currency} ${dashboard.totalPaid.toLocaleString()}`}/>

<Card title="Interest"

value={`${dashboard.interestRate}%`}/>

</div>

{/* PROGRESS */}

<div className="bg-white rounded-xl shadow p-6">

<div className="flex justify-between mb-2">

<span>Loan Progress</span>

<span>

{dashboard.repaymentProgress.toFixed(1)}%

</span>

</div>

<div className="w-full h-4 rounded bg-gray-200">

<div

className="bg-green-600 h-4 rounded"

style={{

width:`${dashboard.repaymentProgress}%`

}}

></div>

</div>

</div>

{/* NEXT PAYMENT */}

<div className="bg-white rounded-xl shadow p-6">

<h3 className="font-bold mb-3">

Next Payment

</h3>

<div className="grid md:grid-cols-3 gap-4">

<Card

title="Amount"

value={`${dashboard.currency} ${dashboard.nextInstallmentAmount.toLocaleString()}`}

/>

<Card

title="Due Date"

value={dashboard.nextPaymentDate}

/>

<Card

title="Maturity"

value={dashboard.maturityDate}

/>

</div>

</div>

{/* RECENT PAYMENTS */}

<div className="bg-white rounded-xl shadow">

<div className="p-4 font-bold">

Recent Payments

</div>

<table className="w-full">

<thead>

<tr className="bg-gray-100">

<th>Date</th>

<th>Amount</th>

<th>Method</th>

<th>Status</th>

</tr>

</thead>

<tbody>

{

dashboard.recentPayments.map(payment=>(

<tr key={payment.paymentId}

className="border-t">

<td>{payment.paymentDate}</td>

<td>

{dashboard.currency}

{' '}

{payment.amount.toLocaleString()}

</td>

<td>{payment.method}</td>

<td>{payment.status}</td>

</tr>

))

}

</tbody>

</table>

</div>

{/* UPCOMING */}

<div className="bg-white rounded-xl shadow">

<div className="p-4 font-bold">

Upcoming Installments

</div>

<table className="w-full">

<thead>

<tr className="bg-gray-100">

<th>#</th>

<th>Date</th>

<th>Principal</th>

<th>Interest</th>

<th>Total</th>

</tr>

</thead>

<tbody>

{

dashboard.upcomingInstallments.map(i=>(

<tr key={i.installmentNumber}

className="border-t">

<td>{i.installmentNumber}</td>

<td>{i.dueDate}</td>

<td>{i.principal.toLocaleString()}</td>

<td>{i.interest.toLocaleString()}</td>

<td>{i.amount.toLocaleString()}</td>

</tr>

))

}

</tbody>

</table>

</div>

{/* PAYMENT METHODS */}

<div className="bg-white rounded-xl shadow p-6">

<h3 className="font-bold mb-4">

Payment Methods

</h3>

<div className="grid md:grid-cols-2 gap-3">

{

dashboard.availablePaymentMethods.map(method=>(

<button

key={method}

className="border rounded-lg p-4 hover:bg-green-600 hover:text-white transition"

>

{method}

</button>

))

}

</div>

</div>

</div>

);

}

function Card({

title,

value

}:{

title:string,

value:string

}){

return(

<div className="bg-white rounded-xl shadow p-4">

<div className="text-gray-500 text-sm">

{title}

</div>

<div className="text-xl font-bold mt-2">

{value}

</div>

</div>

);

}