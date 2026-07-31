import { get, post } from './api';
import { Loan, RiskScore } from '../types/index';

export interface CreateLoanPayload {
  borrowerId:             number;
  loanType?:              'PERSONAL'|'MORTGAGE'|'AUTO'|'BUSINESS'|'STUDENT'|'EMERGENCY'|
                           'ASSET_FINANCE'|'SALARY_ADVANCE'|'MICROFINANCE'|'AGRICULTURAL'|
                           'TRADE_FINANCE'|'GROUP';
  amount:                 number;
  interestRate:           number;
  interestRateType?:      'ANNUAL' | 'MONTHLY';
  durationMonths:         number;
  currency:               string;
  startDate:              string;
  notes?:                 string;
  collateralValue?:       number;
  collateralDescription?: string;
}

export const getLoans           = (): Promise<Loan[]>         =>
  get('/loans?page=0&size=5000').then((r: any) => (Array.isArray(r) ? r : (r?.content ?? [])));
export const getLoanById        = (id: number): Promise<Loan> => get(`/loans/${id}`) as Promise<Loan>;
export const getLoansByBorrower = (id: number): Promise<Loan[]> => get(`/loans/borrower/${id}`) as Promise<Loan[]>;
export const createLoan         = (p: CreateLoanPayload): Promise<Loan> => post('/loans', p) as Promise<Loan>;
export const approveLoan        = (id: number, interestRate?: number, notes?: string): Promise<Loan> =>
  post(`/loans/${id}/approve`, { interestRate: interestRate != null ? String(interestRate) : undefined, notes }) as Promise<Loan>;
export const rejectLoan         = (id: number, reason: string): Promise<Loan> =>
  post(`/loans/${id}/reject`, { reason }) as Promise<Loan>;
export const getLoanRiskScore   = (id: number): Promise<RiskScore> =>
  get(`/loans/${id}/risk`) as Promise<RiskScore>;