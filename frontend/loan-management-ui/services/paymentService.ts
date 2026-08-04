
import { get, post } from './api';
import { Payment } from '../types/index';

/**
 * ============================================================
 * BORROWER PAYMENT
 * ============================================================
 *
 * Frontend representation of a payment returned from:
 *
 * GET /borrowers/{borrowerId}/details
 *
 * This intentionally supports the fields used by the borrower
 * details page while remaining compatible with the Spring Boot
 * Payment entity.
 */

export interface BorrowerPayment {

  id?: number;

  paymentId?: number;

  loanId?: number;

  loanReference?: string;

  loanNumber?: string;

  borrowerName?: string;

  // ============================================================
  // PAYMENT AMOUNTS
  // ============================================================

  amount?: number | null;

  amountPaid?: number | null;

  principalComponent?: number | null;

  interestComponent?: number | null;

  /**
   * Compatibility aliases used by older frontend code.
   */
  principal?: number | null;

  interest?: number | null;

  fees?: number | null;

  penalty?: number | null;

  totalPaid?: number | null;

  outstandingAfter?: number | null;

  waivedAmount?: number | null;

  // ============================================================
  // DATES
  // ============================================================

  dueDate?: string | null;

  paidDate?: string | null;

  /**
   * Compatibility field used by older borrower-details code.
   */
  paymentDate?: string | null;

  // ============================================================
  // PAYMENT METHOD
  // ============================================================

  paymentMethod?: string | null;

  /**
   * Compatibility alias.
   */
  method?: string | null;

  // ============================================================
  // STATUS
  // ============================================================

  status?: string | null;

  paid?: boolean | null;

  onTime?: boolean | null;

  daysLate?: number | null;

  // ============================================================
  // LOAN / CURRENCY
  // ============================================================

  currency?: string | null;

  // ============================================================
  // TRANSACTION
  // ============================================================

  transactionId?: string | null;

  externalReference?: string | null;

  paymentReference?: string | null;

  // ============================================================
  // ADDITIONAL INFORMATION
  // ============================================================

  installmentNumber?: number | null;

  channel?: string | null;

  notes?: string | null;
}


/**
 * ============================================================
 * LOAN PAYMENTS
 * ============================================================
 */

export const getPaymentsByLoan = async (
  loanId: number
): Promise<Payment[]> => {

  return await get(
    `/loans/${loanId}/payments`
  ) as Payment[];
};


/**
 * ============================================================
 * ALL PAYMENTS
 * ============================================================
 */

export const getAllPayments = async (): Promise<Payment[]> => {

  return await get(
    '/payments'
  ) as Payment[];
};


/**
 * ============================================================
 * OVERDUE PAYMENTS
 * ============================================================
 */

export const getOverduePayments = async (): Promise<Payment[]> => {

  return await get(
    '/payments/overdue'
  ) as Payment[];
};


/**
 * ============================================================
 * MAKE PAYMENT
 * ============================================================
 */

export const makePayment = async (
  loanId: number,
  amount: number,
  method: string,
  txId?: string
): Promise<Payment> => {

  return await post(
    `/loans/${loanId}/payments`,
    {
      amount,
      paymentMethod: method,
      transactionId: txId,
    }
  ) as Payment;
};


/**
 * ============================================================
 * BORROWER PAYMENT HISTORY
 * ============================================================
 *
 * Endpoint:
 *
 * GET /borrowers/{borrowerId}/details
 *
 * The backend borrower-details response contains a payments
 * collection.
 */

export const getPaymentsByBorrower = async (
  borrowerId: number
): Promise<BorrowerPayment[]> => {

  const response = await get(
    `/borrowers/${borrowerId}/details`
  ) as {
    payments?: BorrowerPayment[];
  };

  return response?.payments ?? [];
};


/**
 * ============================================================
 * COMPATIBILITY ALIAS
 * ============================================================
 */

export const getBorrowerPayments = (
  borrowerId: number
): Promise<BorrowerPayment[]> => {

  return getPaymentsByBorrower(borrowerId);
};
