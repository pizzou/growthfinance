package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.*;
import com.patrick.fintech.loan_backend.dto.publicportal.BorrowerDashboardResponse;
import com.patrick.fintech.loan_backend.dto.publicportal.DashboardSummaryResponse;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import com.patrick.fintech.loan_backend.security.HmacIndexer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
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


    private static final List<DocumentType> DEFAULT_REQUIRED_DOCS =
            List.of(
                    DocumentType.NATIONAL_ID,
                    DocumentType.SELFIE,
                    DocumentType.PROOF_OF_ADDRESS
            );


    // ================================================================
    // REQUIRED DOCUMENTS
    // ================================================================

    private List<DocumentType> requiredDocsFor(
            Loan loan
    ) {

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


        if (
                configured == null
                        || configured.isEmpty()
        ) {

            return DEFAULT_REQUIRED_DOCS;
        }


        List<DocumentType> documentTypes =
                new ArrayList<>();


        for (String type :
                configured) {

            try {

                documentTypes.add(
                        DocumentType.valueOf(
                                type.trim()
                                        .toUpperCase()
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


    // ================================================================
    // BASE RATES
    // ================================================================

    private static final Map<Loan.LoanType, Double> BASE_RATES =
            Map.ofEntries(
                    Map.entry(
                            Loan.LoanType.PERSONAL,
                            10.0
                    ),
                    Map.entry(
                            Loan.LoanType.MORTGAGE,
                            8.5
                    ),
                    Map.entry(
                            Loan.LoanType.AUTO,
                            10.0
                    ),
                    Map.entry(
                            Loan.LoanType.BUSINESS,
                            12.0
                    ),
                    Map.entry(
                            Loan.LoanType.STUDENT,
                            10.0
                    ),
                    Map.entry(
                            Loan.LoanType.EMERGENCY,
                            10.0
                    ),
                    Map.entry(
                            Loan.LoanType.ASSET_FINANCE,
                            11.0
                    ),
                    Map.entry(
                            Loan.LoanType.SALARY_ADVANCE,
                            10.0
                    ),
                    Map.entry(
                            Loan.LoanType.MICROFINANCE,
                            20.0
                    ),
                    Map.entry(
                            Loan.LoanType.AGRICULTURAL,
                            9.0
                    ),
                    Map.entry(
                            Loan.LoanType.TRADE_FINANCE,
                            13.0
                    ),
                    Map.entry(
                            Loan.LoanType.GROUP,
                            14.0
                    )
            );


    // ================================================================
    // BORROWER DASHBOARD
    // ================================================================

    public BorrowerDashboardResponse getBorrowerDashboard(
            String reference,
            String phone
    ) {

        String phoneHash =
                HmacIndexer.index(phone);


        Loan loan =
                loanRepo
                        .findByReferenceNumberAndBorrower_PhoneHash(
                                reference,
                                phoneHash
                        )
                        .orElseThrow(
                                () ->
                                        new RuntimeException(
                                                "Loan not found"
                                        )
                        );


        return BorrowerDashboardResponse.builder()
                .loanId(
                        loan.getId()
                )
                .referenceNumber(
                        loan.getReferenceNumber()
                )
                .borrowerName(
                        loan.getBorrower()
                                .getFullName()
                )
                .loanOfficer(
                        loan.getLoanOfficer() != null
                                ? loan.getLoanOfficer()
                                .getFullName()
                                : null
                )
                .status(
                        loan.getStatus().name()
                )
                .loanType(
                        loan.getLoanType().name()
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


    // ================================================================
    // BORROWER SUMMARY
    // ================================================================

    public DashboardSummaryResponse getBorrowerSummary(
            String phone
    ) {

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


        double totalBorrowed = 0.0;
        double outstanding = 0.0;
        double totalPaid = 0.0;


        Loan nextLoan = null;


        for (Loan loan :
                loans) {

            totalBorrowed =
                    roundMoney(
                            totalBorrowed
                                    + safe(
                                    loan.getAmount()
                            )
                    );


            outstanding =
                    roundMoney(
                            outstanding
                                    + safe(
                                    loan.getOutstandingBalance()
                            )
                    );


            totalPaid =
                    roundMoney(
                            totalPaid
                                    + safe(
                                    loan.getTotalPaid()
                            )
                    );


            if (
                    loan.getStatus()
                            == LoanStatus.ACTIVE
            ) {

                activeLoans++;
            }


            if (
                    loan.getStatus()
                            == LoanStatus.OVERDUE
            ) {

                overdueLoans++;
            }


            if (
                    loan.getNextPaymentDate()
                            != null
            ) {

                if (
                        nextLoan == null
                                || loan.getNextPaymentDate()
                                .isBefore(
                                        nextLoan.getNextPaymentDate()
                                )
                ) {

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
                        totalBorrowed
                )
                .outstandingBalance(
                        outstanding
                )
                .totalPaid(
                        totalPaid
                )
                .overdueLoans(
                        overdueLoans
                )
                .nextPaymentAmount(
                        nextLoan == null
                                ? null
                                : nextLoan
                                .getNextInstallmentAmount()
                )
                .nextPaymentDate(
                        nextLoan == null
                                ? null
                                : nextLoan
                                .getNextPaymentDate()
                )
                .build();
    }


    // ================================================================
    // CREATE LOAN
    // ================================================================

    @Transactional
    public Loan createLoan(
            LoanRequest req,
            Long organizationId,
            User createdBy
    ) {

        Organization org =
                orgRepo.findById(
                                organizationId
                        )
                        .orElseThrow(
                                () ->
                                        new RuntimeException(
                                                "Organization not found: "
                                                        + organizationId
                                        )
                        );


        Borrower borrower =
                borrowerRepo.findById(
                                req.getBorrowerId()
                        )
                        .orElseThrow(
                                () ->
                                        new RuntimeException(
                                                "Borrower not found: "
                                                        + req.getBorrowerId()
                                        )
                        );


        if (
                borrower.getOrganization()
                        == null
                        || !borrower
                        .getOrganization()
                        .getId()
                        .equals(
                                organizationId
                        )
        ) {

            throw new RuntimeException(
                    "Borrower does not belong to this organization"
            );
        }


        if (
                borrower.getStatus()
                        == Borrower.BorrowerStatus.BLACKLISTED
        ) {

            throw new RuntimeException(
                    "This borrower is blacklisted and cannot be issued a new loan. Reason on file: "
                            + (
                            borrower.getBlacklistReason()
                                    != null
                                    ? borrower
                                    .getBlacklistReason()
                                    : "not specified"
                    )
            );
        }


        // ============================================================
        // LOAN TYPE
        // ============================================================

        Loan.LoanType requestedType =
                req.getLoanType() != null
                        ? req.getLoanType()
                        : Loan.LoanType.PERSONAL;


        LoanProduct product =
                loanProductRepo
                        .findFirstByOrganization_IdAndLoanTypeAndActiveTrue(
                                organizationId,
                                requestedType
                        )
                        .orElse(null);


        // ============================================================
        // NORMALIZE PRINCIPAL ONCE
        // ============================================================

        if (
                req.getAmount() == null
                        || req.getAmount() <= 0
        ) {

            throw new RuntimeException(
                    "Loan amount must be greater than zero"
            );
        }


        /*
         * IMPORTANT:
         *
         * We continue using Double.
         *
         * RWF has no fractional currency unit, so normalize the
         * principal to the nearest whole currency unit.
         *
         * 5,000,000 -> 5,000,000
         * 4,999,999.4 -> 4,999,999
         * 4,999,999.6 -> 5,000,000
         */
        double principal =
                normalizePrincipal(
                        req.getAmount()
                );


        // ============================================================
        // PRODUCT LIMITS
        // ============================================================

        if (product != null) {

            boolean tooLow =
                    principal
                            < product.getMinAmount();


            boolean tooHigh =
                    product.getMaxAmount() != null
                            && principal
                            > product.getMaxAmount();


            if (
                    tooLow
                            || tooHigh
            ) {

                String range =
                        product.getMaxAmount() != null
                                ? String.format(
                                "between %,.0f and %,.0f",
                                product.getMinAmount(),
                                product.getMaxAmount()
                        )
                                : String.format(
                                "at least %,.0f",
                                product.getMinAmount()
                        );


                throw new RuntimeException(
                        String.format(
                                "%s amount must be %s %s",
                                product.getName(),
                                range,
                                org.getDefaultCurrency()
                        )
                );
            }


            if (
                    req.getDurationMonths()
                            < product.getMinTermMonths()
                            || req.getDurationMonths()
                            > product.getMaxTermMonths()
            ) {

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


        // ============================================================
        // RATE
        // ============================================================

        double rate =
                req.getInterestRate() != null
                        ? req.getInterestRate()
                        : product != null
                        ? product.getInterestRate()
                        : BASE_RATES.getOrDefault(
                        requestedType,
                        15.0
                );


        String rateType =
                req.getInterestRate() != null
                        && req.getInterestRateType() != null
                        ? req.getInterestRateType()
                        : product != null
                        ? product.getInterestRateType()
                        : "ANNUAL";


        if (
                borrower.getCreditScore()
                        != null
        ) {

            rate =
                    adjustRate(
                            rate,
                            borrower.getCreditScore(),
                            rateType
                    );
        }


        // ============================================================
        // LOAN CALCULATION
        // ============================================================

        int months =
                req.getDurationMonths();


        double[] calc =
                calcLoan(
                        principal,
                        rate,
                        months,
                        rateType
                );


        double monthlyInstallment =
                roundMoney(
                        calc[0]
                );


        double totalRepayable =
                roundMoney(
                        calc[1]
                );


        // ============================================================
        // PROCESSING FEE
        // ============================================================

        double feePct =
                product != null
                        && product
                        .getProcessingFeePercent()
                        != null
                        ? product
                        .getProcessingFeePercent()
                        : 2.0;


        double processingFee =
                roundMoney(
                        principal
                                * (
                                feePct
                                        / 100.0
                        )
                );


        // ============================================================
        // DTI
        // ============================================================

        double monthlyIncome =
                safe(
                        borrower.getMonthlyIncome()
                );


        double dti =
                monthlyIncome > 0
                        ? roundMoney(
                        (
                                monthlyInstallment
                                        / monthlyIncome
                        )
                                * 100.0
                )
                        : 0.0;


        // ============================================================
        // BUILD LOAN
        // ============================================================

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
                                req.getRepaymentFrequency()
                                        != null
                                        ? req.getRepaymentFrequency()
                                        : Loan.RepaymentFrequency.MONTHLY
                        )
                        .status(
                                LoanStatus.PENDING
                        )

                        /*
                         * EXACT SAME NORMALIZED PRINCIPAL
                         */
                        .amount(
                                principal
                        )

                        .interestRate(
                                rate
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
                                processingFee
                        )
                        .totalRepayable(
                                totalRepayable
                        )

                        /*
                         * IMPORTANT:
                         *
                         * Do NOT use req.getAmount() here.
                         *
                         * Use the exact same principal stored in amount.
                         */
                        .outstandingBalance(
                                principal
                        )

                        .totalPaid(
                                0.0
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
                                req.getCollateralValue()
                        )
                        .startDate(
                                req.getStartDate() != null
                                        ? LocalDate.parse(
                                        req.getStartDate()
                                )
                                        : LocalDate.now()
                        )
                        .debtToIncomeRatio(
                                dti
                        )
                        .creditScoreSnapshot(
                                borrower.getCreditScore()
                        )
                        .build();


        Loan saved =
                loanRepo.save(
                        loan
                );


        // ============================================================
        // SAFETY CHECK
        // ============================================================

        /*
         * This catches a database/entity/converter problem immediately.
         *
         * If the requested amount was 5,000,000 but Hibernate/database
         * returns 4,999,999, we know the corruption happened outside
         * the calculation below.
         */
        if (
                Math.abs(
                        safe(saved.getAmount())
                                - principal
                ) > 0.001
        ) {

            log.error(
                    "PRINCIPAL MISMATCH AFTER SAVE. Requested={}, saved={}, loanId={}",
                    principal,
                    saved.getAmount(),
                    saved.getId()
            );


            throw new IllegalStateException(
                    "Loan principal changed during save. Expected "
                            + principal
                            + " but saved "
                            + saved.getAmount()
            );
        }


        // ============================================================
        // RISK SCORING
        // ============================================================

        scoreAsync(
                saved
        );


        // ============================================================
        // AUDIT
        // ============================================================

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
                        + " — principal "
                        + principal
        );


        return saved;
    }


    // ================================================================
    // APPROVE LOAN
    // ================================================================

    public Loan approveLoan(
            Long loanId,
            User approvedBy,
            String notes
    ) {

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
            Double newInterestRate
    ) {

        Loan loan =
                getLoanForOrg(
                        loanId,
                        approvedBy
                                .getOrganization()
                                .getId()
                );


        if (
                loan.getStatus()
                        != LoanStatus.PENDING
                        && loan.getStatus()
                        != LoanStatus.UNDER_REVIEW
        ) {

            throw new RuntimeException(
                    "Cannot approve a loan that is "
                            + loan.getStatus()
                            + " — only loans that are Pending or Under Review can be approved."
            );
        }


        if (
                loan.getBorrower()
                        == null
        ) {

            throw new RuntimeException(
                    "Cannot approve loan "
                            + loan.getReferenceNumber()
                            + " — it has no borrower record linked."
            );
        }


        List<DocumentType> missingDocs =
                fileService.getMissingDocumentTypes(
                        loan.getBorrower().getId(),
                        requiredDocsFor(loan)
                );


        if (!missingDocs.isEmpty()) {

            throw new RuntimeException(
                    "Cannot approve this loan — the borrower hasn't uploaded: "
                            + missingDocs.stream()
                            .map(DocumentType::name)
                            .collect(
                                    Collectors.joining(", ")
                            )
            );
        }


        String previousRate =
                loan.getInterestRate() != null
                        ? loan.getInterestRate()
                        + "%"
                        : "unset";


        if (
                newInterestRate != null
                        && !newInterestRate.equals(
                        loan.getInterestRate()
                )
        ) {

            double principal =
                    normalizePrincipal(
                            safe(
                                    loan.getAmount()
                            )
                    );


            int months =
                    loan.getDurationMonths() != null
                            ? loan.getDurationMonths()
                            : 1;


            String rateType =
                    loan.getInterestRateType()
                            != null
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
                    newInterestRate
            );


            loan.setTotalRepayable(
                    roundMoney(
                            calc[1]
                    )
            );
        }


        /*
         * Ensure the existing loan principal remains normalized.
         */
        double exactPrincipal =
                normalizePrincipal(
                        safe(
                                loan.getAmount()
                        )
                );


        loan.setAmount(
                exactPrincipal
        );


        loan.setOutstandingBalance(
                Math.max(
                        0.0,
                        safe(
                                loan.getOutstandingBalance()
                        )
                )
        );


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

            loan.setInternalNotes(
                    notes
            );
        }


        Loan saved =
                loanRepo.save(
                        loan
                );


        /*
         * Do not generate duplicate schedules.
         */
        if (
                paymentRepo
                        .findByLoanId(
                                saved.getId()
                        )
                        .isEmpty()
        ) {

            generateRepaymentSchedule(
                    saved
            );

        } else {

            log.warn(
                    "Repayment schedule already exists for loan {}, skipping regeneration",
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
                    "Notif failed",
                    e
            );
        }


        try {

            smsService.sendLoanApproved(
                    saved
            );

        } catch (Exception e) {

            log.warn(
                    "SMS failed",
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


    // ================================================================
    // AMORTIZE
    // ================================================================

    public double[] amortize(
            double principal,
            double rate,
            int months,
            String rateType
    ) {

        return calcLoan(
                principal,
                rate,
                months,
                rateType
        );
    }


    // ================================================================
    // NEW REFERENCE
    // ================================================================

    public String newReferenceNumber(
            Organization org
    ) {

        return generateRef(
                org
        );
    }


    // ================================================================
    // REJECT LOAN
    // ================================================================

    @Transactional
    public Loan rejectLoan(
            Long loanId,
            User rejectedBy,
            String reason
    ) {

        Loan loan =
                getLoanForOrg(
                        loanId,
                        rejectedBy
                                .getOrganization()
                                .getId()
                );


        if (
                loan.getStatus()
                        != LoanStatus.PENDING
                        && loan.getStatus()
                        != LoanStatus.UNDER_REVIEW
        ) {

            throw new RuntimeException(
                    "Cannot reject a loan that is "
                            + loan.getStatus()
            );
        }


        loan.setStatus(
                LoanStatus.REJECTED
        );


        loan.setRejectionReason(
                reason
        );


        Loan saved =
                loanRepo.save(
                        loan
                );


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
                    "Notif failed",
                    e
            );
        }


        try {

            smsService.sendLoanRejected(
                    saved
            );

        } catch (Exception e) {

            log.warn(
                    "SMS failed",
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
                        reason != null
                                && !reason.isBlank()
                                ? ". Reason: "
                                + reason
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


    // ================================================================
    // DISBURSE LOAN
    // ================================================================

    @Transactional
    public Loan disburseLoan(
            Long loanId,
            User officer,
            String disbursementMethod
    ) {

        Loan loan =
                getLoanForOrg(
                        loanId,
                        officer
                                .getOrganization()
                                .getId()
                );


        if (
                loan.getStatus()
                        != LoanStatus.APPROVED
        ) {

            throw new RuntimeException(
                    "Loan must be APPROVED before disbursement"
            );
        }


        if (
                loan.getBorrower()
                        == null
        ) {

            throw new RuntimeException(
                    "Cannot disburse loan "
                            + loan.getReferenceNumber()
                            + " — it has no borrower record linked."
            );
        }


        List<DocumentType> unverifiedDocs =
                fileService.getUnverifiedDocumentTypes(
                        loan.getBorrower().getId(),
                        requiredDocsFor(loan)
                );


        if (!unverifiedDocs.isEmpty()) {

            throw new RuntimeException(
                    "Cannot disburse this loan — staff still needs to verify: "
                            + unverifiedDocs.stream()
                            .map(DocumentType::name)
                            .collect(
                                    Collectors.joining(", ")
                            )
            );
        }


        // ============================================================
        // NORMALIZE PRINCIPAL BEFORE DISBURSEMENT
        // ============================================================

        double exactPrincipal =
                normalizePrincipal(
                        safe(
                                loan.getAmount()
                        )
                );


        loan.setAmount(
                exactPrincipal
        );


        loan.setOutstandingBalance(
                exactPrincipal
        );


        loan.setStatus(
                LoanStatus.ACTIVE
        );


        loan.setDisbursedAt(
                LocalDate.now()
        );


        loan.setDisbursedAmount(
                exactPrincipal
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
                loanRepo.save(
                        loan
                );


        // ============================================================
        // GENERATE REPAYMENT SCHEDULE
        // ============================================================

        paymentScheduleService.generateSchedule(
                saved
        );


        PaymentSchedule first =
                paymentScheduleService
                        .getNextInstallment(
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
                loanRepo.save(
                        saved
                );


        // ============================================================
        // CREDIT BUREAU
        // ============================================================

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


        // ============================================================
        // AUDIT
        // ============================================================

        audit(
                saved.getOrganization(),
                officer,
                "LOAN_DISBURSED",
                "LOAN",
                loanId.toString(),
                "Disbursed via "
                        + disbursementMethod
        );


        // ============================================================
        // ACCOUNTING
        // ============================================================

        accountingService.postDisbursement(
                saved
        );


        // ============================================================
        // EMAIL
        // ============================================================

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


        // ============================================================
        // SMS
        // ============================================================

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


        // ============================================================
        // NOTIFICATION
        // ============================================================

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


        // ============================================================
        // WEBHOOK
        // ============================================================

        webhookService.dispatch(
                saved.getOrganization(),
                "LOAN_DISBURSED",
                saved
        );


        return saved;
    }


    // ================================================================
    // NOTIFY OFFICER
    // ================================================================

    private void notifyOfficer(
            Loan loan,
            User actor,
            String title,
            String message,
            String type
    ) {

        User officer =
                loan.getLoanOfficer();


        if (
                officer == null
                        || (
                        actor != null
                                && officer.getId()
                                .equals(
                                        actor.getId()
                                )
                )
        ) {

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


    // ================================================================
    // UPDATE STATUS
    // ================================================================

    @Transactional
    public Loan updateStatus(
            Long loanId,
            User user,
            LoanStatus newStatus,
            String notes
    ) {

        Loan loan =
                getLoanForOrg(
                        loanId,
                        user.getOrganization()
                                .getId()
                );


        LoanStatus current =
                loan.getStatus();


        switch (newStatus) {

            case UNDER_REVIEW -> {

                if (
                        current
                                != LoanStatus.PENDING
                ) {

                    throw new RuntimeException(
                            "Only a Pending loan can be moved to Under Review (currently "
                                    + current
                                    + ")"
                    );
                }
            }


            case DEFAULTED -> {

                if (
                        current
                                != LoanStatus.ACTIVE
                                && current
                                != LoanStatus.OVERDUE
                ) {

                    throw new RuntimeException(
                            "Only an Active or Overdue loan can be marked Defaulted (currently "
                                    + current
                                    + ")"
                    );
                }
            }


            case CLOSED -> {

                if (
                        current
                                != LoanStatus.PAID
                                && current
                                != LoanStatus.WRITTEN_OFF
                ) {

                    throw new RuntimeException(
                            "Only a fully Paid or Written-off loan can be Closed (currently "
                                    + current
                                    + ")"
                    );
                }
            }


            case RESTRUCTURED ->

                    throw new RuntimeException(
                            "Use the Restructure Loan action instead."
                    );


            default ->

                    throw new RuntimeException(
                            "Use the dedicated Approve / Reject / Disburse actions."
                    );
        }


        loan.setStatus(
                newStatus
        );


        if (
                notes != null
                        && !notes.isBlank()
        ) {

            loan.setInternalNotes(
                    notes
            );
        }


        Loan saved =
                loanRepo.save(
                        loan
                );


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
                        notes != null
                                && !notes.isBlank()
                                ? ": "
                                + notes
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


    // ================================================================
    // GET LOANS
    // ================================================================

    public Page<Loan> getLoans(
            Organization org,
            int page,
            int size,
            String status,
            String type
    ) {

        LoanStatus ls =
                (
                        status != null
                                && !status.isBlank()
                )
                        ? LoanStatus.valueOf(
                        status
                )
                        : null;


        Loan.LoanType lt =
                (
                        type != null
                                && !type.isBlank()
                )
                        ? Loan.LoanType.valueOf(
                        type
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


    // ================================================================
    // GET LOAN
    // ================================================================

    public Loan getLoanForOrg(
            Long loanId,
            Long orgId
    ) {

        Loan loan =
                loanRepo.findById(
                                loanId
                        )
                        .orElseThrow(
                                () ->
                                        new RuntimeException(
                                                "Loan not found: "
                                                        + loanId
                                        )
                        );


        if (
                loan.getOrganization() == null
                        || !loan
                        .getOrganization()
                        .getId()
                        .equals(
                                orgId
                        )
        ) {

            throw new RuntimeException(
                    "Access denied to loan: "
                            + loanId
            );
        }


        return loan;
    }


    // ================================================================
    // DOCUMENT REQUIREMENTS
    // ================================================================

    public Map<String, Object> getDocumentRequirements(
            Long loanId,
            Long orgId
    ) {

        Loan loan =
                getLoanForOrg(
                        loanId,
                        orgId
                );


        Map<String, Object> result =
                new LinkedHashMap<>();


        if (
                loan.getBorrower()
                        == null
        ) {

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
                requiredDocsFor(
                        loan
                );


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
                        .map(
                                DocumentType::name
                        )
                        .toList()
        );


        result.put(
                "missing",
                missing.stream()
                        .map(
                                DocumentType::name
                        )
                        .toList()
        );


        result.put(
                "unverified",
                unverified.stream()
                        .map(
                                DocumentType::name
                        )
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


    // ================================================================
    // DASHBOARD
    // ================================================================

    public DashboardStats getDashboard(
            Organization org
    ) {

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
                loanRepo
                        .getLoanTypeBreakdown(org)
                        .stream()
                        .map(
                                r -> {

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
                                }
                        )
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
                                        loanRepo.sumActivePrincipal(org)
                                )
                                .orElse(0.0)
                )
                .totalCollected(
                        Optional.ofNullable(
                                        loanRepo.sumTotalCollected(org)
                                )
                                .orElse(0.0)
                )
                .outstandingBalance(
                        Optional.ofNullable(
                                        loanRepo.sumOutstandingBalance(org)
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
                                        paymentRepo.countLatePayments(org)
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


    // ================================================================
    // GENERATE REPAYMENT SCHEDULE
    // ================================================================

    private void generateRepaymentSchedule(
            Loan loan
    ) {

        /*
         * IMPORTANT:
         *
         * Schedule always starts from PRINCIPAL,
         * never totalRepayable.
         */
        double principal =
                normalizePrincipal(
                        safe(
                                loan.getAmount()
                        )
                );


        double rate =
                safe(
                        loan.getInterestRate()
                );


        String rateType =
                loan.getInterestRateType() != null
                        ? loan.getInterestRateType()
                        : "ANNUAL";


        int months =
                loan.getDurationMonths() != null
                        ? loan.getDurationMonths()
                        : 1;


        double monthlyPayment =
                roundMoney(
                        calcLoan(
                                principal,
                                rate,
                                months,
                                rateType
                        )[0]
                );


        double balance =
                principal;


        double monthlyRate;


        if (
                "MONTHLY"
                        .equalsIgnoreCase(
                                rateType
                        )
        ) {

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
                                loan.getStartDate()
                                        != null
                                        ? loan.getStartDate()
                                        : LocalDate.now()
                        ).plusMonths(1)
                );


        for (
                int i = 1;
                i <= months;
                i++
        ) {

            balance =
                    roundMoney(
                            balance
                    );


            double interest =
                    roundMoney(
                            balance
                                    * monthlyRate
                    );


            double principalComponent;


            double installmentAmount;


            if (
                    i == months
            ) {

                /*
                 * Final installment clears the exact remaining
                 * principal after rounding.
                 */
                principalComponent =
                        balance;


                installmentAmount =
                        roundMoney(
                                principalComponent
                                        + interest
                        );


                balance =
                        0.0;

            } else {

                installmentAmount =
                        monthlyPayment;


                principalComponent =
                        roundMoney(
                                installmentAmount
                                        - interest
                        );


                if (
                        principalComponent
                                < 0
                ) {

                    principalComponent =
                            0.0;
                }


                if (
                        principalComponent
                                > balance
                ) {

                    principalComponent =
                            balance;
                }


                balance =
                        roundMoney(
                                balance
                                        - principalComponent
                        );


                if (
                        balance < 0.01
                ) {

                    balance =
                            0.0;
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
                                    installmentAmount
                            )
                            .principalComponent(
                                    roundMoney(
                                            principalComponent
                                    )
                            )
                            .interestComponent(
                                    roundMoney(
                                            interest
                                    )
                            )
                            .dueDate(
                                    due
                            )
                            .paid(
                                    false
                            )
                            .amountPaid(
                                    0.0
                            )
                            .penalty(
                                    0.0
                            )
                            .outstandingAfter(
                                    balance
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


        loan.setAmount(
                principal
        );


        /*
         * At schedule generation, outstanding principal must remain
         * principal, not total repayment.
         */
        loan.setOutstandingBalance(
                principal
        );


        loan.setNextDueDate(
                holidayService.adjustToBusinessDay(
                        orgId,
                        (
                                loan.getStartDate()
                                        != null
                                        ? loan.getStartDate()
                                        : LocalDate.now()
                        ).plusMonths(1)
                )
        );


        loanRepo.save(
                loan
        );
    }


    // ================================================================
    // RISK SCORING
    // ================================================================

    @Async
    public void scoreAsync(
            Loan loan
    ) {

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


    // ================================================================
    // RATE ADJUSTMENT
    // ================================================================

    private double adjustRate(
            double base,
            int creditScore,
            String rateType
    ) {

        if (
                "MONTHLY"
                        .equalsIgnoreCase(
                                rateType
                        )
        ) {

            if (
                    creditScore >= 750
            ) {

                return Math.max(
                        6.0,
                        base - 2.0
                );
            }


            if (
                    creditScore >= 650
            ) {

                return base;
            }


            return Math.min(
                    10.0,
                    base + 2.0
            );
        }


        if (
                creditScore >= 800
        ) {

            return base - 2.0;
        }


        if (
                creditScore >= 750
        ) {

            return base - 1.0;
        }


        if (
                creditScore >= 700
        ) {

            return base;
        }


        if (
                creditScore >= 650
        ) {

            return base + 1.0;
        }


        return base + 3.0;
    }


    // ================================================================
    // LOAN CALCULATION
    // ================================================================

    private double[] calcLoan(
            double principal,
            double rate,
            int months,
            String rateType
    ) {

        principal =
                normalizePrincipal(
                        principal
                );


        if (
                months <= 0
        ) {

            throw new IllegalArgumentException(
                    "Loan duration must be greater than zero"
            );
        }


        double monthlyRate =
                "MONTHLY"
                        .equalsIgnoreCase(
                                rateType
                        )
                        ? rate / 100.0
                        : rate / 100.0 / 12.0;


        if (
                monthlyRate == 0.0
        ) {

            double monthly =
                    roundMoney(
                            principal
                                    / months
                    );


            return new double[]{
                    monthly,
                    roundMoney(
                            monthly * months
                    )
            };
        }


        double factor =
                Math.pow(
                        1.0 + monthlyRate,
                        months
                );


        double monthly =
                principal
                        * (
                        monthlyRate
                                * factor
                )
                        / (
                        factor
                                - 1.0
                );


        monthly =
                roundMoney(
                        monthly
                );


        double total =
                roundMoney(
                        monthly
                                * months
                );


        return new double[]{
                monthly,
                total
        };
    }


    // ================================================================
    // MONEY HELPERS
    // ================================================================

    private double safe(
            Double value
    ) {

        if (
                value == null
                        || Double.isNaN(value)
                        || Double.isInfinite(value)
        ) {

            return 0.0;
        }


        return value;
    }


    /**
     * Standard two-decimal rounding while keeping Double.
     */
    private double roundMoney(
            double value
    ) {

        if (
                Double.isNaN(value)
                        || Double.isInfinite(value)
        ) {

            return 0.0;
        }


        return Math.round(
                value * 100.0
        ) / 100.0;
    }


    /**
     * Principal normalization.
     *
     * For whole-unit currencies such as RWF, the principal should not
     * contain fractional currency units.
     *
     * This keeps Double while ensuring:
     *
     * 5,000,000 -> 5,000,000
     */
    private double normalizePrincipal(
            double value
    ) {

        if (
                Double.isNaN(value)
                        || Double.isInfinite(value)
                        || value < 0
        ) {

            throw new IllegalArgumentException(
                    "Invalid loan principal: " + value
            );
        }


        return Math.round(
                value
        );
    }


    // ================================================================
    // REFERENCES
    // ================================================================

    private String generateRef(
            Organization org
    ) {

        String prefix =
                "RW";


        if (
                org != null
                        && org.getCountry() != null
                        && !org.getCountry()
                        .trim()
                        .isEmpty()
        ) {

            prefix =
                    org.getCountry()
                            .trim()
                            .toUpperCase();
        }


        String timestamp =
                java.time.LocalDateTime.now()
                        .format(
                                java.time.format.DateTimeFormatter
                                        .ofPattern(
                                                "yyyyMMddHHmmssSSS"
                                        )
                        );


        return prefix
                + timestamp;
    }


    private String generatePayRef(
            Loan loan,
            int installment
    ) {

        return "PAY-"
                + loan.getReferenceNumber()
                + "-"
                + String.format(
                "%03d",
                installment
        );
    }


    // ================================================================
    // AUDIT
    // ================================================================

    private void audit(
            Organization org,
            User user,
            String action,
            String entityType,
            String entityId,
            String desc
    ) {

        auditService.log(
                org,
                user,
                action,
                entityType,
                entityId,
                desc
        );
    }


    // ================================================================
    // CONTROLLER ACCESS
    // ================================================================

    public LoanRepository getLoanRepository() {

        return loanRepo;
    }
}