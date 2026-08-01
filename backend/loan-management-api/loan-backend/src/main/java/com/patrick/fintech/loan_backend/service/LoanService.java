
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.*;
import com.patrick.fintech.loan_backend.dto.publicportal.BorrowerDashboardResponse;
import com.patrick.fintech.loan_backend.dto.publicportal.DashboardSummaryResponse;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import com.patrick.fintech.loan_backend.security.HmacIndexer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanService {

    private final LoanRepository loanRepo;
    private final OrganizationRepository orgRepo;
    private final PaymentRepository paymentRepo;
    private final BorrowerRepository borrowerRepo;
    private final RiskScoringService riskService;
    private final NotificationService notifService;
    private final MailService mailService;
    private final SmsService smsService;
    private final AuditLogRepository auditRepo;
    private final WebhookService webhookService;
    private final AuditService auditService;
    private final LoanProductRepository loanProductRepo;
    private final AccountingService accountingService;
    private final BorrowerFileService fileService;
    private final HolidayService holidayService;
    private final CreditBureauService creditBureauService;
    private final PaymentScheduleService paymentScheduleService;

    /**
     * Used when a loan's product has no requiredDocumentTypes configured.
     */
    private static final List<DocumentType> DEFAULT_REQUIRED_DOCS = List.of(
            DocumentType.NATIONAL_ID,
            DocumentType.SELFIE,
            DocumentType.PROOF_OF_ADDRESS
    );

    /**
     * Default interest rates by loan type.
     */
    private static final Map<Loan.LoanType, Double> BASE_RATES = Map.ofEntries(
            Map.entry(Loan.LoanType.PERSONAL, 10.0),
            Map.entry(Loan.LoanType.MORTGAGE, 8.5),
            Map.entry(Loan.LoanType.AUTO, 10.0),
            Map.entry(Loan.LoanType.BUSINESS, 12.0),
            Map.entry(Loan.LoanType.STUDENT, 10.0),
            Map.entry(Loan.LoanType.EMERGENCY, 10.0),
            Map.entry(Loan.LoanType.ASSET_FINANCE, 11.0),
            Map.entry(Loan.LoanType.SALARY_ADVANCE, 10.0),
            Map.entry(Loan.LoanType.MICROFINANCE, 20.0),
            Map.entry(Loan.LoanType.AGRICULTURAL, 9.0),
            Map.entry(Loan.LoanType.TRADE_FINANCE, 13.0),
            Map.entry(Loan.LoanType.GROUP, 14.0)
    );

    // ============================================================
    // DOCUMENT REQUIREMENTS
    // ============================================================

    private List<DocumentType> requiredDocsFor(Loan loan) {

        LoanProduct product =
                loanProductRepo
                        .findFirstByOrganization_IdAndLoanTypeAndActiveTrue(
                                loan.getOrganization().getId(),
                                loan.getLoanType()
                        )
                        .orElse(null);

        if (product == null) {
            return DEFAULT_REQUIRED_DOCS;
        }

        List<String> configured =
                product.getRequiredDocumentTypesList();

        if (configured == null || configured.isEmpty()) {
            return DEFAULT_REQUIRED_DOCS;
        }

        List<DocumentType> documentTypes =
                new ArrayList<>();

        for (String type : configured) {

            try {

                documentTypes.add(
                        DocumentType.valueOf(
                                type.trim().toUpperCase()
                        )
                );

            } catch (IllegalArgumentException ex) {

                throw new RuntimeException(
                        "Invalid document type configured for Loan Product: "
                                + type
                );
            }
        }

        return documentTypes;
    }

    // ============================================================
    // BORROWER DASHBOARD
    // ============================================================

    public BorrowerDashboardResponse getBorrowerDashboard(
            String reference,
            String phone) {

        String phoneHash =
                HmacIndexer.index(phone);

        Loan loan =
                loanRepo
                        .findByReferenceNumberAndBorrower_PhoneHash(
                                reference,
                                phoneHash
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Loan not found"
                                )
                        );

        return BorrowerDashboardResponse.builder()
                .loanId(loan.getId())
                .referenceNumber(
                        loan.getReferenceNumber()
                )
                .borrowerName(
                        loan.getBorrower().getFullName()
                )
                .loanOfficer(
                        loan.getLoanOfficer() != null
                                ? loan.getLoanOfficer().getFullName()
                                : null
                )
                .status(
                        loan.getStatus() != null
                                ? loan.getStatus().name()
                                : null
                )
                .loanType(
                        loan.getLoanType() != null
                                ? loan.getLoanType().name()
                                : null
                )
                .principal(
                        loan.getAmount()
                )
                .outstandingBalance(
                        loan.getOutstandingBalance()
                )
                .totalPaid(
                        loan.getTotalPaid()
                )
                .totalRepayable(
                        loan.getTotalRepayable()
                )
                .nextInstallmentAmount(
                        loan.getNextInstallmentAmount()
                )
                .nextPaymentDate(
                        loan.getNextPaymentDate()
                )
                .maturityDate(
                        loan.getMaturityDate()
                )
                .missedInstallments(
                        loan.getMissedInstallments()
                )
                .daysOverdue(
                        loan.getDaysOverdue()
                )
                .currency(
                        loan.getCurrency()
                )
                .build();
    }

    // ============================================================
    // BORROWER SUMMARY
    // ============================================================

    public DashboardSummaryResponse getBorrowerSummary(
            String phone) {

        String phoneHash =
                HmacIndexer.index(phone);

        List<Loan> loans =
                loanRepo.findByBorrower_PhoneHash(
                        phoneHash
                );

        if (loans.isEmpty()) {

            throw new RuntimeException(
                    "Borrower not found"
            );
        }

        int activeLoans = 0;
        int overdueLoans = 0;

        BigDecimal totalBorrowed =
                BigDecimal.ZERO;

        BigDecimal outstanding =
                BigDecimal.ZERO;

        BigDecimal totalPaid =
                BigDecimal.ZERO;

        Loan nextLoan = null;

        for (Loan loan : loans) {

            BigDecimal loanAmount =
                    loan.getAmount() != null
                            ? loan.getAmount()
                            : BigDecimal.ZERO;

            BigDecimal loanOutstanding =
                    loan.getOutstandingBalance() != null
                            ? loan.getOutstandingBalance()
                            : BigDecimal.ZERO;

            BigDecimal loanTotalPaid =
                    loan.getTotalPaid() != null
                            ? loan.getTotalPaid()
                            : BigDecimal.ZERO;

            totalBorrowed =
                    totalBorrowed.add(
                            loanAmount
                    );

            outstanding =
                    outstanding.add(
                            loanOutstanding
                    );

            totalPaid =
                    totalPaid.add(
                            loanTotalPaid
                    );

            if (loan.getStatus() == LoanStatus.ACTIVE) {
                activeLoans++;
            }

            if (loan.getStatus() == LoanStatus.OVERDUE) {
                overdueLoans++;
            }

            if (loan.getNextPaymentDate() != null) {

                if (nextLoan == null
                        || loan.getNextPaymentDate()
                        .isBefore(
                                nextLoan.getNextPaymentDate()
                        )) {

                    nextLoan = loan;
                }
            }
        }

        return DashboardSummaryResponse.builder()
                .totalLoans(
                        loans.size()
                )
                .activeLoans(
                        activeLoans
                )
                .totalBorrowed(
                        money(totalBorrowed)
                )
                .outstandingBalance(
                        money(outstanding)
                )
                .totalPaid(
                        money(totalPaid)
                )
                .overdueLoans(
                        overdueLoans
                )
                .nextPaymentAmount(
                        nextLoan == null
                                ? null
                                : nextLoan.getNextInstallmentAmount()
                )
                .nextPaymentDate(
                        nextLoan == null
                                ? null
                                : nextLoan.getNextPaymentDate()
                )
                .build();
    }

    // ============================================================
    // CREATE LOAN
    // ============================================================

    @Transactional
    public Loan createLoan(
            LoanRequest req,
            Long organizationId,
            User createdBy) {

        // ========================================================
        // LOAD ORGANIZATION
        // ========================================================

        Organization org =
                orgRepo.findById(
                        organizationId
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Organization not found: "
                                        + organizationId
                        )
                );

        // ========================================================
        // LOAD BORROWER
        // ========================================================

        Borrower borrower =
                borrowerRepo.findById(
                        req.getBorrowerId()
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Borrower not found: "
                                        + req.getBorrowerId()
                        )
                );

        // ========================================================
        // ORGANIZATION SECURITY CHECK
        // ========================================================

        if (borrower.getOrganization() == null
                || borrower.getOrganization().getId() == null
                || !borrower.getOrganization()
                        .getId()
                        .equals(organizationId)) {

            throw new RuntimeException(
                    "Borrower does not belong to this organization"
            );
        }

        // ========================================================
        // BLACKLIST CHECK
        // ========================================================

        if (borrower.getStatus()
                == Borrower.BorrowerStatus.BLACKLISTED) {

            throw new RuntimeException(
                    "This borrower is blacklisted and cannot be issued "
                            + "a new loan. Reason on file: "
                            + (
                            borrower.getBlacklistReason() != null
                                    ? borrower.getBlacklistReason()
                                    : "not specified"
                    )
            );
        }

        // ========================================================
        // DETERMINE LOAN TYPE
        // ========================================================

        Loan.LoanType requestedType =
                req.getLoanType() != null
                        ? req.getLoanType()
                        : Loan.LoanType.PERSONAL;

        // ========================================================
        // FIND LOAN PRODUCT
        // ========================================================

        LoanProduct product =
                loanProductRepo
                        .findFirstByOrganization_IdAndLoanTypeAndActiveTrue(
                                organizationId,
                                requestedType
                        )
                        .orElse(null);

        // ========================================================
        // VALIDATE PRODUCT AMOUNT / TERM
        // ========================================================

        if (product != null) {

            if (req.getAmount() == null) {

                throw new RuntimeException(
                        "Loan amount is required"
                );
            }

            boolean tooLow =
        product.getMinAmount() != null
                && req.getAmount() != null
                && req.getAmount().compareTo(
                        BigDecimal.valueOf(
                                product.getMinAmount()
                        )
                ) < 0;

boolean tooHigh =
        product.getMaxAmount() != null
                && req.getAmount() != null
                && req.getAmount().compareTo(
                        BigDecimal.valueOf(
                                product.getMaxAmount()
                        )
                ) > 0;

            if (tooLow || tooHigh) {

                String range;

                if (product.getMaxAmount() != null) {

                    range =
                            String.format(
                                    "between %,.0f and %,.0f",
                                    product.getMinAmount().doubleValue(),
                                    product.getMaxAmount().doubleValue()
                            );

                } else {

                    range =
                            String.format(
                                    "at least %,.0f",
                                    product.getMinAmount().doubleValue()
                            );
                }

                throw new RuntimeException(
                        String.format(
                                "%s amount must be %s %s",
                                product.getName(),
                                range,
                                org.getDefaultCurrency()
                        )
                );
            }

            if (req.getDurationMonths()
                    < product.getMinTermMonths()
                    || req.getDurationMonths()
                    > product.getMaxTermMonths()) {

                throw new RuntimeException(
                        String.format(
                                "%s term must be between %d and %d months",
                                product.getName(),
                                product.getMinTermMonths(),
                                product.getMaxTermMonths()
                        )
                );
            }
        }

        // ========================================================
        // DETERMINE INTEREST RATE
        // ========================================================

        double rate;

        if (req.getInterestRate() != null) {

            rate =
                    req.getInterestRate()
                            .doubleValue();

        } else if (product != null
                && product.getInterestRate() != null) {

            rate =
                    product.getInterestRate()
                            .doubleValue();

        } else {

            rate =
                    BASE_RATES.getOrDefault(
                            requestedType,
                            15.0
                    );
        }

        // ========================================================
        // DETERMINE INTEREST RATE TYPE
        // ========================================================

        String rateType =
                req.getInterestRate() != null
                        && req.getInterestRateType() != null
                        ? req.getInterestRateType()
                        : product != null
                        && product.getInterestRateType() != null
                        ? product.getInterestRateType()
                        : "ANNUAL";

        // ========================================================
        // CREDIT SCORE RATE ADJUSTMENT
        // ========================================================

        if (borrower.getCreditScore() != null) {

            rate =
                    adjustRate(
                            rate,
                            borrower.getCreditScore(),
                            rateType
                    );
        }

        // ========================================================
        // BASIC LOAN VALUES
        // ========================================================

        if (req.getAmount() == null) {

            throw new RuntimeException(
                    "Loan amount is required"
            );
        }

        double principal =
                req.getAmount()
                        .doubleValue();

        int months =
                req.getDurationMonths();

        // ========================================================
        // LOAN CALCULATION
        // ========================================================

        double[] calc =
                calcLoan(
                        principal,
                        rate,
                        months,
                        rateType
                );

        // ========================================================
        // PROCESSING FEE
        // ========================================================

        double feePct;

        if (product != null
                && product.getProcessingFeePercent() != null) {

            feePct =
                    product.getProcessingFeePercent()
                            .doubleValue();

        } else {

            feePct = 2.0;
        }

        double processingFee =
                principal
                        * (feePct / 100.0);

        // ========================================================
        // DEBT-TO-INCOME RATIO
        // ========================================================

        double monthlyIncome =
                borrower.getMonthlyIncome() != null
                        ? borrower.getMonthlyIncome()
                                .doubleValue()
                        : 0.0;

        double dti =
                monthlyIncome > 0.0
                        ? (calc[0] / monthlyIncome) * 100.0
                        : 0.0;

        // ========================================================
        // COLLATERAL VALUE
        // ========================================================

        BigDecimal collateralValue =
                req.getCollateralValue();

        // ========================================================
        // CREATE LOAN
        // ========================================================

        Loan loan =
                Loan.builder()
                        .referenceNumber(
                                generateRef(org)
                        )
                        .organization(
                                org
                        )
                        .borrower(
                                borrower
                        )
                        .loanOfficer(
                                createdBy
                        )
                        .loanType(
                                requestedType
                        )
                        .repaymentFrequency(
                                req.getRepaymentFrequency() != null
                                        ? req.getRepaymentFrequency()
                                        : Loan.RepaymentFrequency.MONTHLY
                        )
                        .status(
                                LoanStatus.PENDING
                        )
                        .amount(
                                money(principal)
                        )
                        .interestRate(
                                moneyRate(rate)
                        )
                        .interestRateType(
                                rateType
                        )
                        .durationMonths(
                                months
                        )
                        .currency(
                                req.getCurrency() != null
                                        ? req.getCurrency()
                                        : org.getDefaultCurrency()
                        )
                        .processingFee(
                                money(processingFee)
                        )
                        .totalRepayable(
                                money(calc[1])
                        )
                        .outstandingBalance(
                                money(principal)
                        )
                        .totalPaid(
                                BigDecimal.ZERO
                        )
                        .purpose(
                                req.getPurpose()
                        )
                        .notes(
                                req.getNotes()
                        )
                        .collateralDescription(
                                req.getCollateralDescription()
                        )
                        .collateralValue(
                                collateralValue
                        )
                        .startDate(
                                req.getStartDate() != null
                                        ? LocalDate.parse(
                                                req.getStartDate()
                                        )
                                        : LocalDate.now()
                        )
                        .debtToIncomeRatio(
                                round(dti)
                        )
                        .creditScoreSnapshot(
                                borrower.getCreditScore()
                        )
                        .build();

        // ========================================================
        // SAVE
        // ========================================================

        Loan saved =
                loanRepo.save(loan);

        // ========================================================
        // ASYNC CREDIT / RISK SCORING
        // ========================================================

        scoreAsync(saved);

        // ========================================================
        // AUDIT
        // ========================================================

        audit(
                org,
                createdBy,
                "LOAN_CREATED",
                "LOAN",
                saved.getId().toString(),
                "Loan "
                        + saved.getReferenceNumber()
                        + " created for "
                        + borrower.getFullName()
        );

        return saved;
    }

    // ============================================================
    // APPROVE LOAN
    // ============================================================

    public Loan approveLoan(
            Long loanId,
            User approvedBy,
            String notes) {

        return approveLoan(
                loanId,
                approvedBy,
                notes,
                null
        );
    }

    @Transactional
    public Loan approveLoan(
            Long loanId,
            User approvedBy,
            String notes,
            Double newInterestRate) {

        Loan loan =
                getLoanForOrg(
                        loanId,
                        approvedBy.getOrganization().getId()
                );

        if (loan.getStatus() != LoanStatus.PENDING
                && loan.getStatus() != LoanStatus.UNDER_REVIEW) {

            throw new RuntimeException(
                    "Cannot approve a loan that is "
                            + loan.getStatus()
                            + " — only loans that are Pending or "
                            + "Under Review can be approved."
            );
        }

        if (loan.getBorrower() == null) {

            throw new RuntimeException(
                    "Cannot approve loan "
                            + loan.getReferenceNumber()
                            + " — it has no borrower record linked. "
                            + "This indicates a data problem; fix the "
                            + "borrower link before this loan can proceed."
            );
        }

        List<DocumentType> missingDocs =
                fileService.getMissingDocumentTypes(
                        loan.getBorrower().getId(),
                        requiredDocsFor(loan)
                );

        if (!missingDocs.isEmpty()) {

            throw new RuntimeException(
                    "Cannot approve this loan — the borrower hasn't "
                            + "uploaded: "
                            + missingDocs.stream()
                            .map(DocumentType::name)
                            .collect(
                                    Collectors.joining(", ")
                            )
                            + ". Upload these documents first, or "
                            + "override the product's document "
                            + "requirements if they genuinely don't apply."
            );
        }

        String previousRate =
                loan.getInterestRate() != null
                        ? loan.getInterestRate() + "%"
                        : "unset";

        if (newInterestRate != null
                && (
                loan.getInterestRate() == null
                        || loan.getInterestRate().compareTo(
                                BigDecimal.valueOf(
                                        newInterestRate
                                )
                        ) != 0
        )) {

            double principal =
                    loan.getAmount() != null
                            ? loan.getAmount().doubleValue()
                            : 0.0;

            int months =
                    loan.getDurationMonths() != null
                            ? loan.getDurationMonths()
                            : 1;

            String rateType =
                    loan.getInterestRateType() != null
                            ? loan.getInterestRateType()
                            : "ANNUAL";

            double[] calc =
                    calcLoan(
                            principal,
                            newInterestRate,
                            months,
                            rateType
                    );

            loan.setInterestRate(
                    moneyRate(
                            newInterestRate
                    )
            );

            loan.setTotalRepayable(
                    money(calc[1])
            );
        }

        loan.setStatus(
                LoanStatus.APPROVED
        );

        loan.setApprovedBy(
                approvedBy
        );

        loan.setApprovedAt(
                LocalDate.now()
        );

        if (notes != null) {
            loan.setInternalNotes(notes);
        }

        Loan saved =
                loanRepo.save(loan);

        if (paymentRepo.findByLoanId(
                saved.getId()
        ).isEmpty()) {

            generateRepaymentSchedule(
                    saved
            );

        } else {

            log.warn(
                    "Repayment schedule already exists for loan {}, "
                            + "skipping regeneration",
                    saved.getId()
            );
        }

        audit(
                loan.getOrganization(),
                approvedBy,
                "LOAN_APPROVED",
                "LOAN",
                loanId.toString(),
                "Loan "
                        + loan.getReferenceNumber()
                        + " approved"
                        + (
                        newInterestRate != null
                                ? " — rate changed from "
                                + previousRate
                                + " to "
                                + newInterestRate
                                + "%"
                                : ""
                )
        );

        try {

            mailService.sendLoanApproved(
                    saved
            );

        } catch (Exception e) {

            log.warn(
                    "Loan approval email failed",
                    e
            );
        }

        try {

            smsService.sendLoanApproved(
                    saved
            );

        } catch (Exception e) {

            log.warn(
                    "Loan approval SMS failed",
                    e
            );
        }

        notifyOfficer(
                saved,
                approvedBy,
                "Loan Approved",
                "Loan "
                        + saved.getReferenceNumber()
                        + " has been approved by "
                        + approvedBy.getName()
                        + ".",
                "success"
        );

        webhookService.dispatch(
                loan.getOrganization(),
                "LOAN_APPROVED",
                saved
        );

        return saved;
    }

    // ============================================================
    // AMORTIZE
    // ============================================================

    public double[] amortize(
            double principal,
            double rate,
            int months,
            String rateType) {

        return calcLoan(
                principal,
                rate,
                months,
                rateType
        );
    }

    // ============================================================
    // NEW REFERENCE NUMBER
    // ============================================================

    public String newReferenceNumber(
            Organization org) {

        return generateRef(org);
    }

    // ============================================================
    // REJECT LOAN
    // ============================================================

    @Transactional
    public Loan rejectLoan(
            Long loanId,
            User rejectedBy,
            String reason) {

        Loan loan =
                getLoanForOrg(
                        loanId,
                        rejectedBy.getOrganization().getId()
                );

        if (loan.getStatus() != LoanStatus.PENDING
                && loan.getStatus() != LoanStatus.UNDER_REVIEW) {

            throw new RuntimeException(
                    "Cannot reject a loan that is "
                            + loan.getStatus()
                            + " — only loans that are Pending or "
                            + "Under Review can be rejected."
            );
        }

        loan.setStatus(
                LoanStatus.REJECTED
        );

        loan.setRejectionReason(
                reason
        );

        Loan saved =
                loanRepo.save(loan);

        audit(
                loan.getOrganization(),
                rejectedBy,
                "LOAN_REJECTED",
                "LOAN",
                loanId.toString(),
                "Reason: " + reason
        );

        try {

            mailService.sendLoanRejected(
                    saved
            );

        } catch (Exception e) {

            log.warn(
                    "Loan rejection email failed",
                    e
            );
        }

        try {

            smsService.sendLoanRejected(
                    saved
            );

        } catch (Exception e) {

            log.warn(
                    "Loan rejection SMS failed",
                    e
            );
        }

        notifyOfficer(
                saved,
                rejectedBy,
                "Loan Rejected",
                "Loan "
                        + saved.getReferenceNumber()
                        + " has been rejected by "
                        + rejectedBy.getName()
                        + (
                        reason != null && !reason.isBlank()
                                ? ". Reason: " + reason
                                : "."
                ),
                "warning"
        );

        webhookService.dispatch(
                loan.getOrganization(),
                "LOAN_REJECTED",
                saved
        );

        return saved;
    }

    // ============================================================
    // DISBURSE LOAN
    // ============================================================

    @Transactional
    public Loan disburseLoan(
            Long loanId,
            User officer,
            String disbursementMethod) {

        Loan loan =
                getLoanForOrg(
                        loanId,
                        officer.getOrganization().getId()
                );

        if (loan.getStatus() != LoanStatus.APPROVED) {

            throw new RuntimeException(
                    "Loan must be APPROVED before disbursement"
            );
        }

        if (loan.getBorrower() == null) {

            throw new RuntimeException(
                    "Cannot disburse loan "
                            + loan.getReferenceNumber()
                            + " — it has no borrower record linked. "
                            + "This indicates a data problem; fix the "
                            + "borrower link before funds can be released."
            );
        }

        List<DocumentType> unverifiedDocs =
                fileService.getUnverifiedDocumentTypes(
                        loan.getBorrower().getId(),
                        requiredDocsFor(loan)
                );

        if (!unverifiedDocs.isEmpty()) {

            throw new RuntimeException(
                    "Cannot disburse this loan — staff still needs "
                            + "to verify: "
                            + unverifiedDocs.stream()
                            .map(DocumentType::name)
                            .collect(
                                    Collectors.joining(", ")
                            )
                            + " in the Documents tab before funds "
                            + "can be released."
            );
        }

        loan.setStatus(
                LoanStatus.ACTIVE
        );

        loan.setDisbursedAt(
                LocalDate.now()
        );

        loan.setDisbursedAmount(
                loan.getAmount()
        );

        loan.setMaturityDate(
                LocalDate.now()
                        .plusMonths(
                                loan.getDurationMonths()
                        )
        );

        loan.setNextDueDate(
                LocalDate.now()
                        .plusMonths(1)
        );

        Loan saved =
                loanRepo.save(loan);

        paymentScheduleService.generateSchedule(
                saved
        );

        PaymentSchedule first =
                paymentScheduleService.getNextInstallment(
                        saved.getId()
                );

        if (first != null) {

            saved.setNextPaymentDate(
                    first.getDueDate()
            );

            saved.setNextInstallmentAmount(
                    first.getInstallmentAmount()
            );

            saved.setNextDueDate(
                    first.getDueDate()
            );
        }

        saved =
                loanRepo.save(saved);

        try {

            creditBureauService.reportDisbursedLoan(
                    saved,
                    officer.getName()
            );

            log.info(
                    "Loan {} successfully reported to Credit Bureau.",
                    saved.getReferenceNumber()
            );

        } catch (Exception ex) {

            log.error(
                    "Unable to report loan {} to Credit Bureau.",
                    saved.getReferenceNumber(),
                    ex
            );
        }

        audit(
                saved.getOrganization(),
                officer,
                "LOAN_DISBURSED",
                "LOAN",
                loanId.toString(),
                "Disbursed via "
                        + disbursementMethod
        );

        accountingService.postDisbursement(
                saved
        );

        try {

            mailService.sendLoanDisbursed(
                    saved,
                    disbursementMethod
            );

        } catch (Exception e) {

            log.warn(
                    "Loan disbursement email failed.",
                    e
            );
        }

        try {

            smsService.sendLoanDisbursed(
                    saved,
                    disbursementMethod
            );

        } catch (Exception e) {

            log.warn(
                    "Loan disbursement SMS failed.",
                    e
            );
        }

        notifyOfficer(
                saved,
                officer,
                "Loan Disbursed",
                "Loan "
                        + saved.getReferenceNumber()
                        + " ("
                        + saved.getCurrency()
                        + " "
                        + saved.getDisbursedAmount()
                        + ") has been disbursed via "
                        + disbursementMethod
                        + ".",
                "success"
        );

        webhookService.dispatch(
                saved.getOrganization(),
                "LOAN_DISBURSED",
                saved
        );

        return saved;
    }

    // ============================================================
    // NOTIFY OFFICER
    // ============================================================

    private void notifyOfficer(
            Loan loan,
            User actor,
            String title,
            String message,
            String type) {

        User officer =
                loan.getLoanOfficer();

        if (officer == null) {
            return;
        }

        if (actor != null
                && officer.getId().equals(
                        actor.getId()
                )) {

            return;
        }

        try {

            notifService.notifyUsers(
                    List.of(officer),
                    title,
                    message,
                    type,
                    "/dashboard/loans/"
                            + loan.getId()
            );

        } catch (Exception e) {

            log.warn(
                    "In-app notification failed",
                    e
            );
        }
    }

    // ============================================================
    // UPDATE STATUS
    // ============================================================

    @Transactional
    public Loan updateStatus(
            Long loanId,
            User user,
            LoanStatus newStatus,
            String notes) {

        Loan loan =
                getLoanForOrg(
                        loanId,
                        user.getOrganization().getId()
                );

        LoanStatus current =
                loan.getStatus();

        switch (newStatus) {

            case UNDER_REVIEW -> {

                if (current != LoanStatus.PENDING) {

                    throw new RuntimeException(
                            "Only a Pending loan can be moved "
                                    + "to Under Review (currently "
                                    + current
                                    + ")"
                    );
                }
            }

            case DEFAULTED -> {

                if (current != LoanStatus.ACTIVE
                        && current != LoanStatus.OVERDUE) {

                    throw new RuntimeException(
                            "Only an Active or Overdue loan can "
                                    + "be marked Defaulted (currently "
                                    + current
                                    + ")"
                    );
                }
            }

            case CLOSED -> {

                if (current != LoanStatus.PAID
                        && current != LoanStatus.WRITTEN_OFF) {

                    String outstandingMessage = "";

                    if (loan.getOutstandingBalance() != null
                            && loan.getOutstandingBalance()
                            .compareTo(
                                    new BigDecimal("0.01")
                            ) > 0) {

                        outstandingMessage =
                                " — outstanding balance is "
                                        + loan.getOutstandingBalance();
                    }

                    throw new RuntimeException(
                            "Only a fully Paid or Written-off loan "
                                    + "can be Closed (currently "
                                    + current
                                    + outstandingMessage
                    );
                }
            }

            case RESTRUCTURED -> {

                throw new RuntimeException(
                        "Use the Restructure Loan action instead — "
                                + "it recalculates the repayment schedule "
                                + "correctly rather than just changing "
                                + "the label."
                );
            }

            default -> {

                throw new RuntimeException(
                        "Use the dedicated Approve / Reject / "
                                + "Disburse actions for that change, "
                                + "not this generic status update."
                );
            }
        }

        loan.setStatus(
                newStatus
        );

        if (notes != null
                && !notes.isBlank()) {

            loan.setInternalNotes(
                    notes
            );
        }

        Loan saved =
                loanRepo.save(loan);

        audit(
                loan.getOrganization(),
                user,
                "LOAN_STATUS_CHANGED",
                "LOAN",
                loanId.toString(),
                current
                        + " -> "
                        + newStatus
                        + (
                        notes != null && !notes.isBlank()
                                ? ": " + notes
                                : ""
                )
        );

        webhookService.dispatch(
                loan.getOrganization(),
                "LOAN_STATUS_CHANGED",
                saved
        );

        return saved;
    }

    // ============================================================
    // GET LOANS
    // ============================================================

    public Page<Loan> getLoans(
            Organization org,
            int page,
            int size,
            String status,
            String type) {

        LoanStatus ls =
                status != null
                        && !status.isBlank()
                        ? LoanStatus.valueOf(
                                status.toUpperCase()
                        )
                        : null;

        Loan.LoanType lt =
                type != null
                        && !type.isBlank()
                        ? Loan.LoanType.valueOf(
                                type.toUpperCase()
                        )
                        : null;

        return loanRepo.findByFilters(
                org,
                ls,
                lt,
                PageRequest.of(
                        page,
                        size
                )
        );
    }

    // ============================================================
    // GET LOAN FOR ORGANIZATION
    // ============================================================

    public Loan getLoanForOrg(
            Long loanId,
            Long orgId) {

        Loan loan =
                loanRepo.findById(
                        loanId
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Loan not found: "
                                        + loanId
                        )
                );

        if (loan.getOrganization() == null
                || loan.getOrganization().getId() == null
                || !loan.getOrganization()
                        .getId()
                        .equals(orgId)) {

            throw new RuntimeException(
                    "Access denied to loan: "
                            + loanId
            );
        }

        return loan;
    }

    // ============================================================
    // DOCUMENT REQUIREMENTS
    // ============================================================

    public Map<String, Object> getDocumentRequirements(
            Long loanId,
            Long orgId) {

        Loan loan =
                getLoanForOrg(
                        loanId,
                        orgId
                );

        Map<String, Object> result =
                new LinkedHashMap<>();

        if (loan.getBorrower() == null) {

            result.put(
                    "required",
                    List.of()
            );

            result.put(
                    "missing",
                    List.of()
            );

            result.put(
                    "unverified",
                    List.of()
            );

            result.put(
                    "readyToApprove",
                    false
            );

            result.put(
                    "readyToDisburse",
                    false
            );

            result.put(
                    "noBorrowerLinked",
                    true
            );

            return result;
        }

        List<DocumentType> required =
                requiredDocsFor(loan);

        List<DocumentType> missing =
                fileService.getMissingDocumentTypes(
                        loan.getBorrower().getId(),
                        required
                );

        List<DocumentType> unverified =
                fileService.getUnverifiedDocumentTypes(
                        loan.getBorrower().getId(),
                        required
                );

        result.put(
                "required",
                required.stream()
                        .map(DocumentType::name)
                        .toList()
        );

        result.put(
                "missing",
                missing.stream()
                        .map(DocumentType::name)
                        .toList()
        );

        result.put(
                "unverified",
                unverified.stream()
                        .map(DocumentType::name)
                        .toList()
        );

        result.put(
                "readyToApprove",
                missing.isEmpty()
        );

        result.put(
                "readyToDisburse",
                unverified.isEmpty()
        );

        return result;
    }

    // ============================================================
    // DASHBOARD
    // ============================================================

    public DashboardStats getDashboard(
            Organization org) {

        LocalDate firstOfMonth =
                LocalDate.now()
                        .withDayOfMonth(1);

        long overdueCount =
                paymentRepo
                        .findByOrganization_IdAndPaidFalseAndDueDateBefore(
                                org.getId(),
                                LocalDate.now()
                        )
                        .size();

        List<Map<String, Object>> typeBreakdown =
                loanRepo.getLoanTypeBreakdown(org)
                        .stream()
                        .map(r -> {

                            Map<String, Object> m =
                                    new LinkedHashMap<>();

                            m.put(
                                    "type",
                                    r[0]
                            );

                            m.put(
                                    "count",
                                    r[1]
                            );

                            m.put(
                                    "amount",
                                    r[2]
                            );

                            return m;
                        })
                        .collect(
                                Collectors.toList()
                        );

        List<Loan> recent =
                loanRepo.findRecentByOrg(
                        org,
                        PageRequest.of(
                                0,
                                8
                        )
                );

        return DashboardStats.builder()
                .totalLoans(
                        loanRepo.countByOrganization(
                                org
                        )
                )
                .pendingLoans(
                        loanRepo.countByOrganizationAndStatus(
                                org,
                                LoanStatus.PENDING
                        )
                )
                .activeLoans(
                        loanRepo.countByOrganizationAndStatus(
                                org,
                                LoanStatus.ACTIVE
                        )
                )
                .overdueLoans(
                        overdueCount
                )
                .completedLoans(
                        loanRepo.countByOrganizationAndStatus(
                                org,
                                LoanStatus.PAID
                        )
                )
                .defaultedLoans(
                        loanRepo.countByOrganizationAndStatus(
                                org,
                                LoanStatus.DEFAULTED
                        )
                )
                .totalDisbursed(
                        Optional.ofNullable(
                                loanRepo.sumActivePrincipal(
                                        org
                                )
                        )
                        .orElse(0.0)
                )
                .totalCollected(
                        Optional.ofNullable(
                                loanRepo.sumTotalCollected(
                                        org
                                )
                        )
                        .orElse(0.0)
                )
                .outstandingBalance(
                        Optional.ofNullable(
                                loanRepo.sumOutstandingBalance(
                                        org
                                )
                        )
                        .orElse(0.0)
                )
                .collectedThisMonth(
                        Optional.ofNullable(
                                paymentRepo.sumCollectedSince(
                                        org,
                                        firstOfMonth
                                )
                        )
                        .orElse(0.0)
                )
                .totalBorrowers(
                        borrowerRepo.countByOrganization(
                                org
                        )
                )
                .latePaymentsCount(
                        Optional.ofNullable(
                                paymentRepo.countLatePayments(
                                        org
                                )
                        )
                        .orElse(0L)
                )
                .loanTypeBreakdown(
                        typeBreakdown
                )
                .recentLoans(
                        recent
                )
                .build();
    }

    // ============================================================
    // GENERATE REPAYMENT SCHEDULE
    // ============================================================

    private void generateRepaymentSchedule(
            Loan loan) {

        double principal =
                loan.getAmount() != null
                        ? loan.getAmount()
                                .doubleValue()
                        : 0.0;

        double rate =
                loan.getInterestRate() != null
                        ? loan.getInterestRate()
                                .doubleValue()
                        : 0.0;

        String rateType =
                loan.getInterestRateType() != null
                        ? loan.getInterestRateType()
                        : "MONTHLY";

        int months =
                loan.getDurationMonths() != null
                        ? loan.getDurationMonths()
                        : 1;

        if (months <= 0) {

            throw new RuntimeException(
                    "Loan duration must be greater than zero"
            );
        }

        double monthlyPayment =
                calcLoan(
                        principal,
                        rate,
                        months,
                        rateType
                )[0];

        BigDecimal balance =
                money(principal);

        double monthlyRate;

        if ("MONTHLY".equalsIgnoreCase(
                rateType
        )) {

            monthlyRate =
                    rate / 100.0;

        } else {

            monthlyRate =
                    rate / 100.0 / 12.0;
        }

        Long orgId =
                loan.getOrganization()
                        .getId();

        LocalDate due =
                holidayService.adjustToBusinessDay(
                        orgId,
                        (
                                loan.getStartDate() != null
                                        ? loan.getStartDate()
                                        : LocalDate.now()
                        )
                        .plusMonths(1)
                );

        for (int i = 1;
             i <= months;
             i++) {

            BigDecimal interest =
                    money(
                            balance.doubleValue()
                                    * monthlyRate
                    );

            BigDecimal principalComponent;

            if (i == months) {

                principalComponent =
                        balance;

                monthlyPayment =
                        principalComponent
                                .add(interest)
                                .doubleValue();

                balance =
                        BigDecimal.ZERO;

            } else {

                principalComponent =
                        money(
                                monthlyPayment
                                        - interest.doubleValue()
                        );

                if (principalComponent
                        .compareTo(
                                BigDecimal.ZERO
                        ) < 0) {

                    principalComponent =
                            BigDecimal.ZERO;
                }

                balance =
                        balance.subtract(
                                principalComponent
                        );

                if (balance
                        .compareTo(
                                BigDecimal.ZERO
                        ) < 0) {

                    balance =
                            BigDecimal.ZERO;
                }
            }

            Payment payment =
                    Payment.builder()
                            .paymentReference(
                                    generatePayRef(
                                            loan,
                                            i
                                    )
                            )
                            .loan(
                                    loan
                            )
                            .organization(
                                    loan.getOrganization()
                            )
                            .installmentNumber(
                                    i
                            )
                            .amount(
                                    money(
                                            monthlyPayment
                                    )
                            )
                            .principalComponent(
                                    principalComponent
                            )
                            .interestComponent(
                                    interest
                            )
                            .dueDate(
                                    due
                            )
                            .paid(
                                    false
                            )
                            .penalty(
                                    BigDecimal.ZERO
                            )
                            .outstandingAfter(
                                    money(balance)
                            )
                            .status(
                                    Payment.PaymentStatus.PENDING
                            )
                            .build();

            paymentRepo.save(
                    payment
            );

            due =
                    holidayService.adjustToBusinessDay(
                            orgId,
                            due.plusMonths(1)
                    );
        }

        loan.setNextDueDate(
                holidayService.adjustToBusinessDay(
                        orgId,
                        (
                                loan.getStartDate() != null
                                        ? loan.getStartDate()
                                        : LocalDate.now()
                        )
                        .plusMonths(1)
                )
        );

        loanRepo.save(loan);
    }

    // ============================================================
    // ASYNC RISK SCORING
    // ============================================================

    @Async
    public void scoreAsync(
            Loan loan) {

        try {

            var risk =
                    riskService.score(
                            loan
                    );

            loan.setRiskScore(
                    risk.getScore()
            );

            loan.setRiskCategory(
                    risk.getCategory()
            );

            loanRepo.save(
                    loan
            );

        } catch (Exception e) {

            log.warn(
                    "Risk scoring skipped: {}",
                    e.getMessage()
            );
        }
    }

    // ============================================================
    // AUDIT
    // ============================================================

    private void audit(
            Organization org,
            User user,
            String action,
            String entityType,
            String entityId,
            String desc) {

        auditService.log(
                org,
                user,
                action,
                entityType,
                entityId,
                desc
        );
    }

    // ============================================================
    // ADJUST RATE
    // ============================================================

    private double adjustRate(
            double base,
            int creditScore,
            String rateType) {

        if ("MONTHLY".equalsIgnoreCase(
                rateType
        )) {

            if (creditScore >= 750) {

                return Math.max(
                        6.0,
                        base - 2.0
                );
            }

            if (creditScore >= 650) {

                return base;
            }

            return Math.min(
                    10.0,
                    base + 2.0
            );
        }

        if (creditScore >= 800) {

            return base - 2.0;
        }

        if (creditScore >= 750) {

            return base - 1.0;
        }

        if (creditScore >= 700) {

            return base;
        }

        if (creditScore >= 650) {

            return base + 1.0;
        }

        return base + 3.0;
    }

    // ============================================================
    // CALCULATE LOAN
    // ============================================================

    private double[] calcLoan(
            double principal,
            double rate,
            int months,
            String rateType) {

        if (months <= 0) {

            throw new IllegalArgumentException(
                    "Loan duration must be greater than zero"
            );
        }

        double mr =
                "MONTHLY".equalsIgnoreCase(
                        rateType
                )
                        ? rate / 100.0
                        : rate / 100.0 / 12.0;

        if (mr == 0) {

            return new double[]{
                    principal / months,
                    principal
            };
        }

        double factor =
                Math.pow(
                        1 + mr,
                        months
                );

        double monthly =
                principal
                        * (mr * factor)
                        / (factor - 1);

        return new double[]{
                monthly,
                monthly * months
        };
    }

    // ============================================================
    // MONEY - DOUBLE TO BIGDECIMAL
    // ============================================================

    private BigDecimal money(
            double value) {

        return BigDecimal
                .valueOf(value)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }

    // ============================================================
    // MONEY - BIGDECIMAL NORMALIZATION
    // ============================================================

    private BigDecimal money(
            BigDecimal value) {

        if (value == null) {

            return BigDecimal.ZERO
                    .setScale(
                            2,
                            RoundingMode.HALF_UP
                    );
        }

        return value.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    // ============================================================
    // INTEREST RATE CONVERSION
    // ============================================================

    private BigDecimal moneyRate(
            double value) {

        return BigDecimal
                .valueOf(value)
                .setScale(
                        4,
                        RoundingMode.HALF_UP
                );
    }

    // ============================================================
    // ROUND
    // ============================================================

    private double round(
            double value) {

        return BigDecimal
                .valueOf(value)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                )
                .doubleValue();
    }

    // ============================================================
    // GENERATE LOAN REFERENCE
    // ============================================================

    private String generateRef(
            Organization org) {

        String prefix = "RW";

        if (org != null
                && org.getCountry() != null
                && !org.getCountry()
                        .trim()
                        .isEmpty()) {

            prefix =
                    org.getCountry()
                            .trim()
                            .toUpperCase();
        }

        String timestamp =
                LocalDateTime.now()
                        .format(
                                DateTimeFormatter.ofPattern(
                                        "yyyyMMddHHmmssSSS"
                                )
                        );

        return prefix + timestamp;
    }

    // ============================================================
    // GENERATE PAYMENT REFERENCE
    // ============================================================

    private String generatePayRef(
            Loan loan,
            int installment) {

        return "PAY-"
                + loan.getReferenceNumber()
                + "-"
                + String.format(
                        "%03d",
                        installment
                );
    }

    // ============================================================
    // LOAN REPOSITORY ACCESSOR
    // ============================================================

    public LoanRepository getLoanRepository() {

        return loanRepo;
    }
}
