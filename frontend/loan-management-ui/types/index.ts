



export type LoanStatus =
  | 'PENDING'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'DISBURSED'
  | 'ACTIVE'
  | 'OVERDUE'
  | 'DEFAULTED'
  | 'RESTRUCTURED'
  | 'WRITTEN_OFF'
  | 'PAID'
  | 'CLOSED'
  | 'CANCELLED';

export type LoanType =
  | 'PERSONAL'
  | 'MORTGAGE'
  | 'AUTO'
  | 'BUSINESS'
  | 'STUDENT'
  | 'EMERGENCY'
  | 'ASSET_FINANCE'
  | 'SALARY_ADVANCE'
  | 'MICROFINANCE'
  | 'AGRICULTURAL'
  | 'TRADE_FINANCE'
  | 'GROUP';

export type RepaymentFrequency =
  | 'WEEKLY'
  | 'BIWEEKLY'
  | 'MONTHLY'
  | 'QUARTERLY'
  | 'BULLET';

export type RiskCategory =
  | 'LOW'
  | 'MEDIUM'
  | 'HIGH'
  | 'CRITICAL';

  

export type OrgStatus =
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'TRIAL'
  | 'EXPIRED'
  | 'PENDING_SETUP';

export type SubscriptionTier =
  | 'TRIAL'
  | 'STARTER'
  | 'PROFESSIONAL'
  | 'ENTERPRISE'
  | 'UNLIMITED';

export type BorrowerStatus =
  | 'ACTIVE'
  | 'INACTIVE'
  | 'BLACKLISTED'
  | 'DECEASED';

export type UserStatus =
  | 'ACTIVE'
  | 'INACTIVE'
  | 'SUSPENDED'
  | 'PENDING_INVITE';

export type PaymentStatus =
  | 'PENDING'
  | 'COMPLETED'
  | 'FAILED'
  | 'REVERSED'
  | 'PARTIALLY_PAID';


// ============================================================
// ORGANIZATION
// ============================================================

export interface Organization {
  id: number;
  name: string;

  industry?: string;

  country: string;
  defaultCurrency: string;
  timezone: string;
  locale: string;

  logoUrl?: string;
  primaryColor?: string;
  website?: string;

  contactEmail?: string;
  contactPhone?: string;
  address?: string;
  registrationNumber?: string;

  subscriptionTier: SubscriptionTier;
  status: OrgStatus;

  maxUsers: number;
  maxActiveLoans: number;

  maxLoanAmount: number;
  minLoanAmount: number;

  trialEndsAt?: string;
  subscriptionExpiresAt?: string;
  createdAt?: string;
}


// ============================================================
// ROLE
// ============================================================

export interface Role {
  id: number;
  name: string;
  description?: string;
}


// ============================================================
// USER
// ============================================================

export interface User {
  id: number;
  name: string;
  email: string;

  phone?: string;
  avatarUrl?: string;
  jobTitle?: string;

  status: UserStatus;

  role: Role;
  organization: Organization;

  lastLoginAt?: string;
  createdAt?: string;
}


// ============================================================
// BORROWER
// ============================================================

export interface Borrower {
  id: number;

  organization?: {
    id: number;
    name: string;
  };

  firstName: string;
  lastName: string;

  email?: string;
  phone?: string;
  alternatePhone?: string;

  nationalId?: string;
  passportNumber?: string;
  taxIdentificationNumber?: string;

  dateOfBirth?: string;
  gender?: string;

  maritalStatus?: string;

  singleCertificateNumber?: string;

  spouseFullName?: string;
  spouseNationalId?: string;
  spousePhone?: string;
  spouseConsent?: boolean;

  nationality?: string;

  addressLine1?: string;
  addressLine2?: string;

  city?: string;
  stateProvince?: string;
  postalCode?: string;

  country?: string;

  employerName?: string;
  employmentType?: string;
  jobTitle?: string;

  monthlyIncome?: number;
  monthlyExpenses?: number;
  netWorth?: number;

  creditScore?: number;
  creditBureau?: string;
  creditReportDate?: string;

  status: BorrowerStatus;

  kycStatus:
    | 'PENDING'
    | 'VERIFIED'
    | 'REJECTED';

  bankName?: string;
  bankAccountNumber?: string;
  bankBranch?: string;

  createdAt?: string;
}


// ============================================================
// LOAN
// ============================================================

export interface Loan {
  id: number;

  referenceNumber: string;

  organization?: {
    id: number;
    name: string;
    currency: string;
  };

  borrower: Borrower;

  approvedBy?: {
    id: number;
    name: string;
  };

  loanOfficer?: {
    id: number;
    name: string;
  };

  loanType: LoanType;

  repaymentFrequency: RepaymentFrequency;

  status: LoanStatus;

  amount: number;

  interestRate: number;

  interestRateType?: string;

  durationMonths: number;

  currency: string;

  processingFee?: number;

  disbursedAmount?: number;

  totalRepayable?: number;

  totalPaid?: number;

  outstandingBalance?: number;

  notes?: string;

  purpose?: string;

  collateralDescription?: string;

  collateralValue?: number;

  rejectionReason?: string;

  internalNotes?: string;

  riskScore?: number;

  riskCategory?: RiskCategory;

  debtToIncomeRatio?: number;

  creditScoreSnapshot?: number;

  startDate?: string;

  approvedAt?: string;

  disbursedAt?: string;

  maturityDate?: string;

  nextDueDate?: string;

  lastPaymentDate?: string;

  missedInstallments?: number;

  daysOverdue?: number;

  createdAt?: string;

  repaymentProgressPct?: number;
}


// ============================================================
// PAYMENT
// Normal payment API response
// ============================================================

export interface Payment {
  id: number;

  paymentReference?: string;

  loan?: {
    id: number;
    referenceNumber: string;
  };

  installmentNumber?: number;

  amount: number;

  principalComponent?: number;

  interestComponent?: number;

  amountPaid?: number;

  penalty?: number;

  waivedAmount?: number;

  outstandingAfter?: number;

  paid: boolean;

  dueDate: string;

  paidDate?: string;

  paymentMethod?: string;

  transactionId?: string;

  externalReference?: string;

  channel?: string;

  notes?: string;

  isLate?: boolean;

  daysLate?: number;

  status: PaymentStatus;
}


// ============================================================
// BORROWER LOAN SUMMARY
//
// Used inside the advanced borrower details response.
// Backend equivalent:
// BorrowerDetailsResponse.LoanSummary
// ============================================================

export interface BorrowerLoanSummary {
  loanId: number;

  /**
   * Your Loan entity uses referenceNumber,
   * NOT loanNumber.
   */
  referenceNumber?: string | null;

  loanType?: LoanType | string | null;

  status?: LoanStatus | string | null;

  loanAmount?: number | null;

  disbursedAmount?: number | null;

  outstandingBalance?: number | null;

  principalPaid?: number | null;

  interestPaid?: number | null;

  totalPaid?: number | null;

  interestRate?: number | null;

  durationMonths?: number | null;

  daysPastDue?: number | null;

  repaymentClassification?: string | null;

  dateOpened?: string | null;

  maturityDate?: string | null;

  lastPaymentDate?: string | null;

  branchName?: string | null;

  currency?: string | null;
}


// ============================================================
// BORROWER PAYMENT
//
// Used inside the advanced borrower details response.
//
// This is intentionally different from Payment above.
// ============================================================

export interface BorrowerPayment {
  paymentId: number;

  loanId: number;

  /**
   * PaymentSummary may expose the loan reference
   * under loanReference.
   */
  loanReference?: string | null;

  /**
   * Some backend versions may expose loanNumber.
   * Keeping this optional makes the frontend tolerant
   * while the backend is being standardized.
   */
  loanNumber?: string | null;

  borrowerName?: string | null;

  amount?: number | null;

  amountPaid?: number | null;

  principalComponent?: number | null;

  interestComponent?: number | null;

  principal?: number | null;

  interest?: number | null;

  fees?: number | null;

  penalty?: number | null;

  totalPaid?: number | null;

  outstandingAfter?: number | null;

  dueDate?: string | null;

  paidDate?: string | null;

  /**
   * Some UI code previously used paymentDate.
   * Keep it optional so old components don't fail.
   */
  paymentDate?: string | null;

  paymentMethod?: string | null;

  /**
   * Some frontend code previously used method.
   */
  method?: string | null;

  transactionId?: string | null;

  externalReference?: string | null;

  channel?: string | null;

  status?: PaymentStatus | string | null;

  paid?: boolean | null;

  onTime?: boolean | null;

  isLate?: boolean | null;

  daysLate?: number | null;

  installmentNumber?: number | null;

  /**
   * Currency may be supplied by the frontend
   * mapping or backend DTO.
   */
  currency?: string | null;

  notes?: string | null;
}


// ============================================================
// BORROWER DETAILS
//
// Complete advanced borrower profile.
// Backend equivalent:
// BorrowerDetailsResponse
// ============================================================

export interface BorrowerDetails {
  // ============================================================
  // BORROWER PROFILE
  // ============================================================

  borrowerId: number;

  fullName: string;

  firstName?: string | null;

  lastName?: string | null;

  email?: string | null;

  phone?: string | null;

  alternatePhone?: string | null;

  nationalId?: string | null;

  passportNumber?: string | null;

  dateOfBirth?: string | null;

  gender?: string | null;

  maritalStatus?: string | null;

  nationality?: string | null;

  country?: string | null;

  address?: string | null;


  // ============================================================
  // EMPLOYMENT / FINANCIAL PROFILE
  // ============================================================

  employerName?: string | null;

  employmentType?: string | null;

  jobTitle?: string | null;

  monthlyIncome?: number | null;

  monthlyExpenses?: number | null;

  netWorth?: number | null;

  creditScore?: number | null;

  creditBureau?: string | null;

  creditReportDate?: string | null;


  // ============================================================
  // BORROWER STATUS
  // ============================================================

  status?: BorrowerStatus | string | null;

  createdAt?: string | null;


  // ============================================================
  // LOAN SUMMARY
  // ============================================================

  totalLoans: number;

  activeLoans: number;

  completedLoans: number;

  overdueLoans: number;

  defaultedLoans: number;

  writtenOffLoans: number;

  totalBorrowed: number;

  totalDisbursed: number;

  totalOutstanding: number;

  totalPrincipalPaid: number;

  totalInterestPaid: number;

  totalFeesPaid: number;

  totalPaid: number;


  // ============================================================
  // REPAYMENT PERFORMANCE
  // ============================================================

  totalPayments: number;

  successfulPayments: number;

  missedPayments: number;

  overduePayments: number;

  repaymentRate: number;

  onTimePaymentRate: number;

  currentDaysPastDue: number;

  maximumDaysPastDue: number;


  // ============================================================
  // RISK
  // ============================================================

  riskLevel?: string | null;

  repaymentBehaviour?: string | null;

  goodPayer: boolean;

  currentlyOverdue: boolean;

  hasDefaultHistory: boolean;

  hasMultipleActiveLoans: boolean;


  // ============================================================
  // LOANS
  // ============================================================

  loans: BorrowerLoanSummary[];


  // ============================================================
  // PAYMENTS
  // ============================================================

  payments: BorrowerPayment[];
}


// ============================================================
// ALIAS
//
// If some existing frontend code uses the backend DTO name,
// this keeps it compatible.
// ============================================================

export type BorrowerDetailsResponse = BorrowerDetails;


// ============================================================
// DASHBOARD STATS
// ============================================================

export interface DashboardStats {
  totalLoans: number;

  pendingLoans: number;

  activeLoans: number;

  overdueLoans: number;

  completedLoans: number;

  defaultedLoans: number;

  totalDisbursed: number;

  totalCollected: number;

  outstandingBalance: number;

  collectedThisMonth: number;

  totalBorrowers: number;

  latePaymentsCount: number;

  portfolioAtRiskPct: number;

  recentLoans: Loan[];

  loanTypeBreakdown: {
    type: string;
    count: number;
    amount: number;
  }[];
}


// ============================================================
// CHART
// ============================================================

export interface ChartPoint {
  month: string;
  amount: number;
}


// ============================================================
// RISK SCORE
// ============================================================

export interface RiskScore {
  score: number;

  category: RiskCategory;

  factors?: string[];
}


// ============================================================
// AUTH
// ============================================================

export interface AuthResponse {
  token: string;

  userId: number;

  name: string;

  email: string;

  role: string;

  organizationId: number;

  organizationName: string;

  currency: string;

  locale: string;

  timezone: string;

  mustChangePassword?: boolean;
}


// ============================================================
// WEBHOOK
// ============================================================

export interface WebhookEndpoint {
  id?: number;

  url: string;

  description?: string;

  secret?: string;

  active: boolean;

  subscribedEvents?: string[];

  failureCount?: number;

  lastDeliveryAt?: string;

  lastDeliveryStatus?: string;
}


// ============================================================
// CURRENCY
// ============================================================

export interface CurrencyRate {
  baseCurrency: string;

  targetCurrency: string;

  rate: number;

  fetchedAt: string;
}


// ============================================================
// PAGINATION
// ============================================================

export interface PageResponse<T> {
  content: T[];

  page: number;

  size: number;

  totalElements: number;

  totalPages: number;

  last: boolean;
}


// ============================================================
// BORROWER FILE
// ============================================================

export interface BorrowerFile {
  id: number;

  fileName: string;

  fileType: string;

  fileSize: number;

  documentType?: string;

  uploadedByApplicant?: boolean;

  uploadedAt?: string;

  verificationStatus?:
    | 'PENDING_VERIFICATION'
    | 'VERIFIED'
    | 'REJECTED'
    | 'REPLACEMENT_REQUESTED';

  officerComment?: string;

  verifiedByName?: string;

  verifiedAt?: string;
}
