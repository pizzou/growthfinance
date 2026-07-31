import { get, post } from './api';
import { Payment } from '../types/index';

export const getPaymentsByLoan  = (id: number): Promise<Payment[]> =>
  get(`/loans/${id}/payments`) as Promise<Payment[]>;
export const getAllPayments      = (): Promise<Payment[]> =>
  get('/payments') as Promise<Payment[]>;
export const getOverduePayments = (): Promise<Payment[]> =>
  get('/payments/overdue') as Promise<Payment[]>;
export const makePayment        = (loanId: number, amount: number, method: string, txId?: string): Promise<Payment> =>
  post(`/loans/${loanId}/payments`, { amount, paymentMethod: method, transactionId: txId }) as Promise<Payment>;