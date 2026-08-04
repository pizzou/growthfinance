
import { get, post, put } from './api';
import { Borrower } from '../types';

export interface BorrowerDetails {
  borrower: Borrower;

  loans?: any[];
  payments?: any[];

  totalLoans?: number;
  activeLoans?: number;
  completedLoans?: number;
  overdueLoans?: number;

  totalBorrowed?: number;
  totalPaid?: number;
  totalPrincipalPaid?: number;
  totalInterestPaid?: number;
  totalPenaltiesPaid?: number;

  outstandingBalance?: number;

  paymentCount?: number;
  paidPaymentCount?: number;
  latePaymentCount?: number;
  overduePaymentCount?: number;

  repaymentRate?: number;
  riskScore?: number;
  riskCategory?: string;

  [key: string]: any;
}

export const borrowerApi = {

  // ============================================================
  // LIST
  // ============================================================

  list: (
    page = 0,
    size = 20,
    search = ''
  ): Promise<any> => {

    const query =
      `/borrowers?page=${page}&size=${size}` +
      (search
        ? `&q=${encodeURIComponent(search)}`
        : '');

    return get(query);
  },


  // ============================================================
  // BASIC BORROWER
  // ============================================================

  getById: (
    id: number
  ): Promise<any> =>
    get(`/borrowers/${id}`),


  // ============================================================
  // COMPLETE BORROWER 360 PROFILE
  // ============================================================

  getDetails: (
    id: number
  ): Promise<BorrowerDetails> =>
    get(`/borrowers/${id}/details`) as Promise<BorrowerDetails>,


  // ============================================================
  // CREATE
  // ============================================================

  create: (
    payload: Partial<Borrower>
  ): Promise<Borrower> =>
    post(
      '/borrowers',
      payload
    ) as Promise<Borrower>,


  // ============================================================
  // UPDATE
  // ============================================================

  update: (
    id: number,
    payload: Partial<Borrower>
  ): Promise<Borrower> =>
    put(
      `/borrowers/${id}`,
      payload
    ) as Promise<Borrower>
};
