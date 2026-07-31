// ============================================================
// LoanSaaS Pro — International-grade TypeScript types
// ============================================================

export type LoanStatus =
  | 'PENDING' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED'
  | 'DISBURSED' | 'ACTIVE' | 'OVERDUE' | 'DEFAULTED'
  | 'RESTRUCTURED' | 'WRITTEN_OFF' | 'PAID' | 'CLOSED' | 'CANCELLED';

export type LoanType =
  | 'PERSONAL' | 'MORTGAGE' | 'AUTO' | 'BUSINESS' | 'STUDENT'
  | 'EMERGENCY' | 'ASSET_FINANCE' | 'SALARY_ADVANCE' | 'MICROFINANCE'
  | 'AGRICULTURAL' | 'TRADE_FINANCE' | 'GROUP';

export type RepaymentFrequency = 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'BULLET';
export type RiskCategory       = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type OrgStatus          = 'ACTIVE' | 'SUSPENDED' | 'TRIAL' | 'EXPIRED' | 'PENDING_SETUP';
export type SubscriptionTier   = 'TRIAL' | 'STARTER' | 'PROFESSIONAL' | 'ENTERPRISE' | 'UNLIMITED';
export type BorrowerStatus     = 'ACTIVE' | 'INACTIVE' | 'BLACKLISTED' | 'DECEASED';
export type UserStatus         = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'PENDING_INVITE';
export type PaymentStatus      = 'PENDING' | 'COMPLETED' | 'FAILED' | 'REVERSED' | 'PARTIALLY_PAID';

export interface Organization {
  id:                  number;
  name:                string;
  industry?:           string;
  country:             string;       // ISO-3166 alpha-2
  defaultCurrency:     string;       // ISO-4217
  timezone:            string;       // IANA
  locale:              string;       // BCP-47
  logoUrl?:            string;
  primaryColor?:       string;
  website?:            string;
  contactEmail?:       string;
  contactPhone?:       string;
  address?:            string;
  registrationNumber?: string;
  subscriptionTier:    SubscriptionTier;
  status:              OrgStatus;
  maxUsers:            number;
  maxActiveLoans:      number;
  maxLoanAmount:       number;
  minLoanAmount:       number;
  trialEndsAt?:        string;
  subscriptionExpiresAt?: string;
  createdAt?:          string;
}

export interface Role {
  id:          number;
  name:        string;
  description?: string;
}

export interface User {
  id:           number;
  name:         string;
  email:        string;
  phone?:       string;
  avatarUrl?:   string;
  jobTitle?:    string;
  status:       UserStatus;
  role:         Role;
  organization: Organization;
  lastLoginAt?: string;
  createdAt?:   string;
}

export interface Borrower {
  id:                      number;
  organization?:           { id: number; name: string };
  firstName:               string;
  lastName:                string;
  email?:                  string;
  phone?:                  string;
  alternatePhone?:         string;
  nationalId?:             string;
  passportNumber?:         string;
  taxIdentificationNumber?: string;
  dateOfBirth?:            string;
  gender?:                 string;
  maritalStatus?:          string;
  singleCertificateNumber?: string;
  spouseFullName?:         string;
  spouseNationalId?:       string;
  spousePhone?:            string;
  spouseConsent?:          boolean;
  nationality?:            string;
  addressLine1?:           string;
  addressLine2?:           string;
  city?:                   string;
  stateProvince?:          string;
  postalCode?:             string;
  country?:                string;
  employerName?:           string;
  employmentType?:         string;
  jobTitle?:               string;
  monthlyIncome?:          number;
  monthlyExpenses?:        number;
  netWorth?:               number;
  creditScore?:            number;
  creditBureau?:           string;
  creditReportDate?:       string;
  status:                  BorrowerStatus;
  kycStatus:               'PENDING' | 'VERIFIED' | 'REJECTED';
  bankName?:               string;
  bankAccountNumber?:      string;
  bankBranch?:             string;
  createdAt?:              string;
}

export interface Loan {
  id:                    number;
  referenceNumber:       string;
  organization?:         { id: number; name: string; currency: string };
  borrower:              Borrower;
  approvedBy?:           { id: number; name: string };
  loanOfficer?:          { id: number; name: string };
  loanType:              LoanType;
  repaymentFrequency:    RepaymentFrequency;
  status:                LoanStatus;
  amount:                number;
  interestRate:          number;
  interestRateType?:     string;
  durationMonths:        number;
  currency:              string;
  processingFee?:        number;
  disbursedAmount?:      number;
  totalRepayable?:       number;
  totalPaid?:            number;
  outstandingBalance?:   number;
  notes?:                string;
  purpose?:              string;
  collateralDescription?: string;
  collateralValue?:      number;
  rejectionReason?:      string;
  internalNotes?:        string;
  riskScore?:            number;
  riskCategory?:         RiskCategory;
  debtToIncomeRatio?:    number;
  creditScoreSnapshot?:  number;
  startDate?:            string;
  approvedAt?:           string;
  disbursedAt?:          string;
  maturityDate?:         string;
  nextDueDate?:          string;
  lastPaymentDate?:      string;
  missedInstallments?:   number;
  daysOverdue?:          number;
  createdAt?:            string;
  repaymentProgressPct?: number;
}

export interface Payment {
  id:                  number;
  paymentReference?:   string;
  loan?:               { id: number; referenceNumber: string };
  installmentNumber?:  number;
  amount:              number;
  principalComponent?: number;
  interestComponent?:  number;
  amountPaid?:         number;
  penalty?:            number;
  waivedAmount?:       number;
  outstandingAfter?:   number;
  paid:                boolean;
  dueDate:             string;
  paidDate?:           string;
  paymentMethod?:      string;
  transactionId?:      string;
  externalReference?:  string;
  channel?:            string;
  notes?:              string;
  isLate?:             boolean;
  daysLate?:           number;
  status:              PaymentStatus;
}

export interface DashboardStats {
  totalLoans:          number;
  pendingLoans:        number;
  activeLoans:         number;
  overdueLoans:        number;
  completedLoans:      number;
  defaultedLoans:      number;
  totalDisbursed:      number;
  totalCollected:      number;
  outstandingBalance:  number;
  collectedThisMonth:  number;
  totalBorrowers:      number;
  latePaymentsCount:   number;
  portfolioAtRiskPct:  number;
  recentLoans:         Loan[];
  loanTypeBreakdown:   { type: string; count: number; amount: number }[];
}

export interface ChartPoint {
  month:  string;
  amount: number;
}

export interface RiskScore {
  score:    number;
  category: RiskCategory;
  factors?: string[];
}

export interface AuthResponse {
  token:            string;
  userId:           number;
  name:             string;
  email:            string;
  role:             string;
  organizationId:   number;
  organizationName: string;
  currency:         string;
  locale:           string;
  timezone:         string;
  mustChangePassword?: boolean;
}

export interface WebhookEndpoint {
  id?:               number;
  url:               string;
  description?:      string;
  secret?:           string;
  active:            boolean;
  subscribedEvents?: string[];
  failureCount?:     number;
  lastDeliveryAt?:   string;
  lastDeliveryStatus?: string;
}

export interface CurrencyRate {
  baseCurrency:   string;
  targetCurrency: string;
  rate:           number;
  fetchedAt:      string;
}

export interface PageResponse<T> {
  content:       T[];
  page:          number;
  size:          number;
  totalElements: number;
  totalPages:    number;
  last:          boolean;
}

export interface BorrowerFile {
  id:                   number;
  fileName:             string;
  fileType:             string;
  fileSize:             number;
  documentType?:        string;
  uploadedByApplicant?: boolean;
  uploadedAt?:          string;
  verificationStatus?:  'PENDING_VERIFICATION' | 'VERIFIED' | 'REJECTED' | 'REPLACEMENT_REQUESTED';
  officerComment?:      string;
  verifiedByName?:      string;
  verifiedAt?:          string;
}