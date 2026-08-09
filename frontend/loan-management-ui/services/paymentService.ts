
import { get, post } from './api';
import { Payment } from '../types/index';



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

  amount?: number;

  amountPaid?: number;

  principalComponent?: number;

  interestComponent?: number;

  principal?: number;

  interest?: number;

  fees?: number;

  penalty?: number;

  totalPaid?: number;


  // ============================================================
  // DATES
  // ============================================================

  dueDate?: string | null;

  paidDate?: string | null;

  paymentDate?: string | null;


  // ============================================================
  // PAYMENT METHOD
  // ============================================================

  paymentMethod?: string | null;

  method?: string | null;


  // ============================================================
  // STATUS
  // ============================================================

  status?: string | null;

  paid?: boolean;

  onTime?: boolean;

  daysLate?: number;


  // ============================================================
  // LOAN INFORMATION
  // ============================================================

  currency?: string | null;


  // ============================================================
  // TRANSACTION
  // ============================================================

  transactionId?: string | null;

  externalReference?: string | null;

  paymentReference?: string | null;


  // ============================================================
  // ADDITIONAL
  // ============================================================

  installmentNumber?: number;

  outstandingAfter?: number;

  waivedAmount?: number;

  channel?: string | null;

  notes?: string | null;
}


/**
 * ============================================================
 * API RESPONSE
 * ============================================================
 */
interface ApiResponse<T> {
  success?: boolean;
  message?: string;
  data?: T;
  content?: T;
}


/**
 * ============================================================
 * UNWRAP API RESPONSE
 * ============================================================
 */
const unwrap = <T>(
  response: T | ApiResponse<T> | null | undefined
): T => {

  if (
    response !== null &&
    response !== undefined &&
    typeof response === 'object'
  ) {

    const wrapped =
      response as ApiResponse<T>;

    if (
      wrapped.data !== undefined
    ) {
      return wrapped.data;
    }

    if (
      wrapped.content !== undefined
    ) {
      return wrapped.content;
    }
  }

  return response as T;
};


/**
 * ============================================================
 * LOAN PAYMENTS
 * ============================================================
 */
export const getPaymentsByLoan = async (
  loanId: number
): Promise<Payment[]> => {

  const response =
    await get(
      `/loans/${loanId}/payments`
    ) as
      | Payment[]
      | ApiResponse<Payment[]>;

  const payments =
    unwrap(response);

  return Array.isArray(payments)
    ? payments
    : [];
};


/**
 * ============================================================
 * ALL PAYMENTS
 * ============================================================
 */
export const getAllPayments = async (): Promise<Payment[]> => {

  const response =
    await get(
      '/payments'
    ) as
      | Payment[]
      | ApiResponse<Payment[]>;

  const payments =
    unwrap(response);

  return Array.isArray(payments)
    ? payments
    : [];
};


/**
 * ============================================================
 * OVERDUE PAYMENTS
 * ============================================================
 */
export const getOverduePayments = async (): Promise<Payment[]> => {

  const response =
    await get(
      '/payments/overdue'
    ) as
      | Payment[]
      | ApiResponse<Payment[]>;

  const payments =
    unwrap(response);

  return Array.isArray(payments)
    ? payments
    : [];
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

  const response =
    await post(
      `/loans/${loanId}/payments`,
      {
        amount,
        paymentMethod: method,
        transactionId: txId,
      }
    ) as
      | Payment
      | ApiResponse<Payment>;

  return unwrap(response);
};


/**
 * ============================================================
 * BORROWER PAYMENT HISTORY
 * ============================================================
 *
 * Backend:
 *
 * GET /api/borrowers/{borrowerId}/details
 *
 * Controller:
 *
 * ApiResponse<BorrowerDetailsResponse>
 *
 * BorrowerDetailsResponse contains:
 *
 * payments
 */
export const getPaymentsByBorrower = async (
  borrowerId: number
): Promise<BorrowerPayment[]> => {

  if (
    !Number.isInteger(borrowerId) ||
    borrowerId <= 0
  ) {
    throw new Error(
      'Invalid borrower ID'
    );
  }

  const response =
    await get(
      `/borrowers/${borrowerId}/details`
    ) as
      | {
          payments?: BorrowerPayment[];
        }
      | ApiResponse<{
          payments?: BorrowerPayment[];
        }>;

  const details =
    unwrap(response);

  if (
    details &&
    typeof details === 'object' &&
    Array.isArray(
      details.payments
    )
  ) {

    return details.payments;
  }

  return [];
};


/**
 * ============================================================
 * COMPATIBILITY ALIAS
 * ============================================================
 */
export const getBorrowerPayments = (
  borrowerId: number
): Promise<BorrowerPayment[]> =>
  getPaymentsByBorrower(
    borrowerId
  );
