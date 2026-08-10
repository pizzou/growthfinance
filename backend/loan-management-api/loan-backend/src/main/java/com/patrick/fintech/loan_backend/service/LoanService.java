package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.LoanRequest;
import com.patrick.fintech.loan_backend.dto.DashboardStats;
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

    private static final List<DocumentType> DEFAULT_REQUIRED_DOCS =
            List.of(
                    DocumentType.NATIONAL_ID,
                    DocumentType.SELFIE,
                    DocumentType.PROOF_OF_ADDRESS
            );

    // ================================================================
    // BASE RATES
    // ================================================================

    private static final Map<Loan.LoanType, BigDecimal> BASE_RATES =
            Map.ofEntries(
                    Map.entry(Loan.LoanType.PERSONAL, bd("10.0")),
                    Map.entry(Loan.LoanType.MORTGAGE, bd("8.5")),
                    Map.entry(Loan.LoanType.AUTO, bd("10.0")),
                    Map.entry(Loan.LoanType.BUSINESS, bd("12.0")),
                    Map.entry(Loan.LoanType.STUDENT, bd("10.0")),
                    Map.entry(Loan.LoanType.EMERGENCY, bd("10.0")),
                    Map.entry(Loan.LoanType.ASSET_FINANCE, bd("11.0")),
                    Map.entry(Loan.LoanType.SALARY_ADVANCE, bd("10.0")),
                    Map.entry(Loan.LoanType.MICROFINANCE, bd("20.0")),
                    Map.entry(Loan.LoanType.AGRICULTURAL, bd("9.0")),
                    Map.entry(Loan.LoanType.TRADE_FINANCE, bd("13.0")),
                    Map.entry(Loan.LoanType.GROUP, bd("14.0"))
            );

    // ================================================================
    // REQUIRED DOCUMENTS
    // ================================================================

    private List<DocumentType> requiredDocsFor(Loan loan) {

        if (loan == null || loan.getOrganization() == null) {
            return DEFAULT_REQUIRED_DOCS;
        }

        Long organizationId = loan.getOrganization().getId();

        if (organizationId == null || loan.getLoanType() == null) {
            return DEFAULT_REQUIRED_DOCS;
        }

        LoanProduct product =
                loanProductRepo
                        .findFirstByOrganization_IdAndLoanTypeAndActiveTrue(
                                organizationId,
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

        List<DocumentType> documentTypes = new ArrayList<>();

        for (String type : configured) {

            if (type == null || type.isBlank()) {
                continue;
            }

            try {

                documentTypes.add(
                        DocumentType.valueOf(
                                type.trim().toUpperCase()
                        )
                );

            } catch (IllegalArgumentException ex) {

                throw new RuntimeException(
                        "Invalid document type configured for Loan Product: "
                                + type,
                        ex
                );
            }
        }

        return documentTypes.isEmpty()
                ? DEFAULT_REQUIRED_DOCS
                : documentTypes;
    }

    // ================================================================
    // BORROWER DASHBOARD
    // ================================================================

    public BorrowerDashboardResponse getBorrowerDashboard(
            String reference,
            String phone
    ) {

        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException(
                    "Loan reference is required"
            );
        }

        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException(
                    "Phone number is required"
            );
        }

        String phoneHash = HmacIndexer.index(phone);

        Loan loan =
                loanRepo
                        .findByReferenceNumberAndBorrower_PhoneHash(
                                reference,
                                phoneHash
                        )
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Loan not found"
                                )
                        );

        return BorrowerDashboardResponse.builder()
                .loanId(loan.getId())
                .referenceNumber(loan.getReferenceNumber())
                .borrowerName(
                        loan.getBorrower() != null
                                ? loan.getBorrower().getFullName()
                                : null
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
                        loan.getAmountDecimal()
                )
                .outstandingBalance(
                        loan.getOutstandingBalanceDecimal()
                )
                .totalPaid(
                        loan.getTotalPaidDecimal()
                )
                .totalRepayable(
                        loan.getTotalRepayableDecimal()
                )
                .nextInstallmentAmount(
                        loan.getNextInstallmentAmountDecimal()
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

        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException(
                    "Phone number is required"
            );
        }

        String phoneHash = HmacIndexer.index(phone);

        List<Loan> loans =
                loanRepo.findByBorrower_PhoneHash(phoneHash);

        if (loans == null || loans.isEmpty()) {
            throw new RuntimeException(
                    "Borrower not found"
            );
        }

        int activeLoans = 0;
        int overdueLoans = 0;

        BigDecimal totalBorrowed = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;

        Loan nextLoan = null;

        for (Loan loan : loans) {

            if (loan == null) {
                continue;
            }

            totalBorrowed =
                    money(
                            totalBorrowed.add(
                                    moneyValue(
                                            loan.getAmountDecimal()
                                    )
                            )
                    );

            outstanding =
                    money(
                            outstanding.add(
                                    moneyValue(
                                            loan.getOutstandingBalanceDecimal()
                                    )
                            )
                    );

            totalPaid =
                    money(
                            totalPaid.add(
                                    moneyValue(
                                            loan.getTotalPaidDecimal()
                                    )
                            )
                    );

            if (loan.getStatus() == LoanStatus.ACTIVE) {
                activeLoans++;
            }

            if (loan.getStatus() == LoanStatus.OVERDUE) {
                overdueLoans++;
            }

            if (loan.getNextPaymentDate() != null) {

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
                .totalLoans(loans.size())
                .activeLoans(activeLoans)
                .totalBorrowed(totalBorrowed)
                .outstandingBalance(outstanding)
                .totalPaid(totalPaid)
                .overdueLoans(overdueLoans)
                .nextPaymentAmount(
                        nextLoan == null
                                ? null
                                : nextLoan.getNextInstallmentAmountDecimal()
                )
                .nextPaymentDate(
                        nextLoan == null
                                ? null
                                : nextLoan.getNextPaymentDate()
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

        if (req == null) {
            throw new IllegalArgumentException(
                    "Loan request cannot be null"
            );
        }

        if (organizationId == null) {
            throw new IllegalArgumentException(
                    "Organization ID cannot be null"
            );
        }

        Organization org =
                orgRepo.findById(organizationId)
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Organization not found: "
                                                + organizationId
                                )
                        );


        if (createdBy != null) {

            if (
                    createdBy.getOrganization() == null
                            || createdBy.getOrganization().getId() == null
                            || !createdBy.getOrganization()
                            .getId()
                            .equals(organizationId)
            ) {

                throw new RuntimeException(
                        "Creating user does not belong to this organization"
                );
            }
        }

        if (req.getBorrowerId() == null) {
            throw new IllegalArgumentException(
                    "Borrower ID is required"
            );
        }

        Borrower borrower =
                borrowerRepo.findById(
                                req.getBorrowerId()
                        )
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Borrower not found: "
                                                + req.getBorrowerId()
                                )
                        );

        if (
                borrower.getOrganization() == null
                        || borrower.getOrganization().getId() == null
                        || !borrower.getOrganization()
                        .getId()
                        .equals(organizationId)
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
                            borrower.getBlacklistReason() != null
                                    ? borrower.getBlacklistReason()
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
        // PRINCIPAL
        // ============================================================

        BigDecimal requestedAmount =
                toBigDecimal(req.getAmount());

        if (
                requestedAmount == null
                        || requestedAmount.compareTo(
                        BigDecimal.ZERO
                ) <= 0
        ) {

            throw new RuntimeException(
                    "Loan amount must be greater than zero"
            );
        }

        BigDecimal principal =
                normalizePrincipal(
                        requestedAmount
                );

        // ============================================================
        // PRODUCT LIMITS
        // ============================================================

        if (product != null) {

            BigDecimal minimumAmount =
                    toBigDecimal(
                            product.getMinAmount()
                    );

            BigDecimal maximumAmount =
                    toBigDecimal(
                            product.getMaxAmount()
                    );

            boolean tooLow =
                    minimumAmount != null
                            && principal.compareTo(
                            minimumAmount
                    ) < 0;

            boolean tooHigh =
                    maximumAmount != null
                            && principal.compareTo(
                            maximumAmount
                    ) > 0;

            if (tooLow || tooHigh) {

                String range;

                if (
                        minimumAmount != null
                                && maximumAmount != null
                ) {

                    range =
                            String.format(
                                    "between %,.0f and %,.0f",
                                    minimumAmount.doubleValue(),
                                    maximumAmount.doubleValue()
                            );

                } else if (minimumAmount != null) {

                    range =
                            String.format(
                                    "at least %,.0f",
                                    minimumAmount.doubleValue()
                            );

                } else {

                    range =
                            String.format(
                                    "up to %,.0f",
                                    maximumAmount != null
                                            ? maximumAmount.doubleValue()
                                            : 0.0
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

            Integer requestedMonths =
                    req.getDurationMonths();

            if (requestedMonths == null) {

                throw new RuntimeException(
                        "Loan duration is required"
                );
            }

            if (
                    requestedMonths < product.getMinTermMonths()
                            || requestedMonths > product.getMaxTermMonths()
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

        BigDecimal rate;

        if (req.getInterestRate() != null) {

            rate =
                    toBigDecimal(
                            req.getInterestRate()
                    );

        } else if (product != null) {

            rate =
                    toBigDecimal(
                            product.getInterestRate()
                    );

            if (rate == null) {

                rate =
                        BASE_RATES.getOrDefault(
                                requestedType,
                                bd("15.0")
                        );
            }

        } else {

            rate =
                    BASE_RATES.getOrDefault(
                            requestedType,
                            bd("15.0")
                    );
        }

        if (rate == null) {
            rate = bd("15.0");
        }

        rate =
                rate.setScale(
                        8,
                        RoundingMode.HALF_UP
                );

        if (
                rate.compareTo(
                        BigDecimal.ZERO
                ) < 0
        ) {

            throw new RuntimeException(
                    "Interest rate cannot be negative"
            );
        }

        String rateType;

        if (
                req.getInterestRate() != null
                        && req.getInterestRateType() != null
                        && !req.getInterestRateType().isBlank()
        ) {

            rateType =
                    req.getInterestRateType()
                            .trim()
                            .toUpperCase();

        } else if (
                product != null
                        && product.getInterestRateType() != null
                        && !product.getInterestRateType().isBlank()
        ) {

            rateType =
                    product.getInterestRateType()
                            .trim()
                            .toUpperCase();

        } else {

            rateType = "ANNUAL";
        }

        validateRateType(rateType);

        if (borrower.getCreditScore() != null) {

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

        Integer requestedDuration =
                req.getDurationMonths();

        if (requestedDuration == null) {

            throw new RuntimeException(
                    "Loan duration is required"
            );
        }

        int months =
                requestedDuration;

        if (months <= 0) {

            throw new RuntimeException(
                    "Loan duration must be greater than zero"
            );
        }

        BigDecimal[] calc =
                calcLoan(
                        principal,
                        rate,
                        months,
                        rateType
                );

        BigDecimal monthlyInstallment =
                calc[0];

        BigDecimal totalRepayable =
                calc[1];

        // ============================================================
        // PROCESSING FEE
        // ============================================================

        BigDecimal feePct =
                product != null
                        ? toBigDecimal(
                        product.getProcessingFeePercent()
                )
                        : null;

        if (feePct == null) {
            feePct = bd("2.0");
        }

        BigDecimal processingFee =
                money(
                        principal
                                .multiply(feePct)
                                .divide(
                                        bd("100"),
                                        8,
                                        RoundingMode.HALF_UP
                                )
                );

        // ============================================================
        // DTI
        // ============================================================

        BigDecimal monthlyIncome =
                moneyValue(
                        borrower.getMonthlyIncome()
                );

        BigDecimal dti =
                monthlyIncome.compareTo(
                        BigDecimal.ZERO
                ) > 0
                        ? money(
                        monthlyInstallment
                                .divide(
                                        monthlyIncome,
                                        8,
                                        RoundingMode.HALF_UP
                                )
                                .multiply(
                                        bd("100")
                                )
                )
                        : BigDecimal.ZERO;

        // ============================================================
        // COLLATERAL
        // ============================================================

        BigDecimal collateralValue =
                req.getCollateralValue() != null
                        ? money(
                        toBigDecimal(
                                req.getCollateralValue()
                        )
                )
                        : null;

        // ============================================================
        // BUILD LOAN
        // ============================================================

        LocalDate startDate =
                req.getStartDate() != null
                        && !req.getStartDate().isBlank()
                        ? LocalDate.parse(
                        req.getStartDate()
                )
                        : LocalDate.now();

        Loan loan =
                Loan.builder()
                        .referenceNumber(
                                generateRef(org)
                        )
                        .organization(org)
                        .borrower(borrower)
                        .createdBy(createdBy)

                       
                        .loanOfficer(createdBy)

                        .loanType(requestedType)
                        .repaymentFrequency(
                                req.getRepaymentFrequency() != null
                                        ? req.getRepaymentFrequency()
                                        : Loan.RepaymentFrequency.MONTHLY
                        )
                        .status(LoanStatus.PENDING)
                        .amount(principal)
                        .interestRate(rate)
                        .interestRateType(rateType)
                        .durationMonths(months)
                        .currency(
                                req.getCurrency() != null
                                        && !req.getCurrency().isBlank()
                                        ? req.getCurrency()
                                        : org.getDefaultCurrency()
                        )
                        .processingFee(processingFee)
                        .totalRepayable(totalRepayable)
                        .outstandingBalance(principal)
                        .totalPaid(BigDecimal.ZERO)
                        .purpose(req.getPurpose())
                        .notes(req.getNotes())
                        .collateralDescription(
                                req.getCollateralDescription()
                        )
                        .collateralValue(collateralValue)
                        .startDate(startDate)
                        .debtToIncomeRatio(dti)
                        .creditScoreSnapshot(
                                borrower.getCreditScore()
                        )
                        .nextDueDate(
                                holidayService.adjustToBusinessDay(
                                        organizationId,
                                        startDate.plusMonths(1)
                                )
                        )
                        .build();

        Loan saved =
                loanRepo.save(loan);

        // ============================================================
        // PRINCIPAL SAFETY CHECK
        // ============================================================

        BigDecimal savedPrincipal =
                moneyValue(
                        saved.getAmountDecimal()
                );

        if (
                savedPrincipal.compareTo(
                        principal
                ) != 0
        ) {

            log.error(
                    "PRINCIPAL MISMATCH AFTER SAVE. Expected={}, saved={}, loanId={}",
                    principal,
                    savedPrincipal,
                    saved.getId()
            );

            throw new IllegalStateException(
                    "Loan principal changed during save. Expected "
                            + principal
                            + " but saved "
                            + savedPrincipal
            );
        }

        // ============================================================
        // RISK SCORING
        // ============================================================

        scoreAsync(saved);

        // ============================================================
        // AUDIT
        // ============================================================

        String creatorDescription;

        if (createdBy != null) {

            creatorDescription =
                    "Loan "
                            + saved.getReferenceNumber()
                            + " created by "
                            + createdBy.getName()
                            + " for "
                            + borrower.getFullName()
                            + " — principal "
                            + principal;

        } else {

            creatorDescription =
                    "Public borrower loan application "
                            + saved.getReferenceNumber()
                            + " created for "
                            + borrower.getFullName()
                            + " — principal "
                            + principal;
        }

        audit(
                org,
                createdBy,
                "LOAN_CREATED",
                "LOAN",
                saved.getId().toString(),
                creatorDescription
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

        if (
                approvedBy == null
                        || approvedBy.getOrganization() == null
                        || approvedBy.getOrganization().getId() == null
        ) {

            throw new RuntimeException(
                    "Approving user must belong to an organization"
            );
        }

        Loan loan =
                getLoanForOrg(
                        loanId,
                        approvedBy.getOrganization().getId()
                );

        if (
                loan.getStatus() != LoanStatus.PENDING
                        && loan.getStatus() != LoanStatus.UNDER_REVIEW
        ) {

            throw new RuntimeException(
                    "Cannot approve a loan that is "
                            + loan.getStatus()
                            + " — only loans that are Pending or Under Review can be approved."
            );
        }

        if (loan.getBorrower() == null) {

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
                            .collect(Collectors.joining(", "))
            );
        }

        BigDecimal previousRate =
                moneyValue(
                        loan.getInterestRateDecimal()
                );

        if (newInterestRate != null) {

            BigDecimal requestedRate =
                    bd(newInterestRate);

            if (
                    requestedRate.compareTo(
                            BigDecimal.ZERO
                    ) < 0
            ) {

                throw new IllegalArgumentException(
                        "Interest rate cannot be negative"
                );
            }

            if (
                    previousRate.compareTo(
                            requestedRate
                    ) != 0
            ) {

                BigDecimal principal =
                        normalizePrincipal(
                                moneyValue(
                                        loan.getAmountDecimal()
                                )
                        );

                int durationMonths =
                        loan.getDurationMonths() != null
                                ? loan.getDurationMonths()
                                : 1;

                String rateType =
                        loan.getInterestRateType() != null
                                ? loan.getInterestRateType()
                                : "ANNUAL";

                validateRateType(rateType);

                BigDecimal[] calc =
                        calcLoan(
                                principal,
                                requestedRate,
                                durationMonths,
                                rateType
                        );

                loan.setInterestRate(
                        requestedRate
                );

                loan.setTotalRepayable(
                        calc[1]
                );
            }
        }

        BigDecimal exactPrincipal =
                normalizePrincipal(
                        moneyValue(
                                loan.getAmountDecimal()
                        )
                );

        loan.setAmount(exactPrincipal);

        if (loan.getOutstandingBalanceDecimal() == null) {
            loan.setOutstandingBalance(
                    exactPrincipal
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

        if (notes != null && !notes.isBlank()) {
            loan.setInternalNotes(notes);
        }

        Loan saved =
                loanRepo.save(loan);

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
                saved.getOrganization(),
                approvedBy,
                "LOAN_APPROVED",
                "LOAN",
                loanId.toString(),
                "Loan "
                        + saved.getReferenceNumber()
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
                saved.getOrganization(),
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

        BigDecimal[] result =
                calcLoan(
                        bd(principal),
                        bd(rate),
                        months,
                        rateType
                );

        return new double[]{
                result[0].doubleValue(),
                result[1].doubleValue()
        };
    }

    // ================================================================
    // NEW REFERENCE
    // ================================================================

    public String newReferenceNumber(
            Organization org
    ) {

        return generateRef(org);
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

        if (
                rejectedBy == null
                        || rejectedBy.getOrganization() == null
                        || rejectedBy.getOrganization().getId() == null
        ) {

            throw new RuntimeException(
                    "Rejecting user must belong to an organization"
            );
        }

        Loan loan =
                getLoanForOrg(
                        loanId,
                        rejectedBy
                                .getOrganization()
                                .getId()
                );

        if (
                loan.getStatus() != LoanStatus.PENDING
                        && loan.getStatus() != LoanStatus.UNDER_REVIEW
        ) {

            throw new RuntimeException(
                    "Cannot reject a loan that is "
                            + loan.getStatus()
            );
        }

        if (reason == null || reason.isBlank()) {

            throw new IllegalArgumentException(
                    "Rejection reason is required"
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
                        reason != null
                                && !reason.isBlank()
                                ? ". Reason: " + reason
                                : "."
                ),
                "warning"
        );

        webhookService.dispatch(
                saved.getOrganization(),
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

        if (
                officer == null
                        || officer.getOrganization() == null
                        || officer.getOrganization().getId() == null
        ) {

            throw new RuntimeException(
                    "Disbursing officer must belong to an organization"
            );
        }

        Loan loan =
                getLoanForOrg(
                        loanId,
                        officer
                                .getOrganization()
                                .getId()
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
                            .collect(Collectors.joining(", "))
            );
        }

        BigDecimal exactPrincipal =
                normalizePrincipal(
                        moneyValue(
                                loan.getAmountDecimal()
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

        LocalDateTime exactDisbursementTimestamp =
                LocalDateTime.now();

        loan.setDisbursedAt(
                exactDisbursementTimestamp
        );

        loan.setDisbursedAmount(
                exactPrincipal
        );

        LocalDate disbursementDate =
                exactDisbursementTimestamp.toLocalDate();

        Integer duration =
                loan.getDurationMonths() != null
                        ? loan.getDurationMonths()
                        : 1;

        loan.setMaturityDate(
                disbursementDate.plusMonths(
                        duration
                )
        );

        loan.setNextDueDate(
                holidayService.adjustToBusinessDay(
                        loan.getOrganization().getId(),
                        disbursementDate.plusMonths(1)
                )
        );

        Loan saved =
                loanRepo.save(loan);

        log.info(
                "Loan {} disbursed at exact timestamp {}",
                saved.getReferenceNumber(),
                saved.getDisbursedAt()
        );

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

        if (creditBureauService.isReportingRequiredForDisbursement()) {

            /*
             * Production safety: when Credit Bureau reporting is required,
             * a failed or unconfigured real provider MUST abort disbursement.
             * The surrounding @Transactional method then rolls the loan
             * status back to APPROVED instead of creating an ACTIVE loan
             * that was never reported.
             */
            creditBureauService.reportDisbursedLoan(
                    saved,
                    officer.getName()
            );

            log.info(
                    "Loan {} successfully reported to Credit Bureau.",
                    saved.getReferenceNumber()
            );

        } else {

            log.warn(
                    "Credit Bureau reporting is explicitly disabled for disbursement. "
                            + "loan={}",
                    saved.getReferenceNumber()
            );
        }

        audit(
                saved.getOrganization(),
                officer,
                "LOAN_DISBURSED",
                "LOAN",
                loanId.toString(),
                "Disbursed via "
                        + (
                        disbursementMethod != null
                                ? disbursementMethod
                                : "unspecified"
                )
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
                        + saved.getDisbursedAmountDecimal()
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

        if (loan == null) {
            return;
        }

        User officer =
                loan.getLoanOfficer();

        if (officer == null) {
            return;
        }

        if (
                actor != null
                        && officer.getId() != null
                        && officer.getId().equals(
                        actor.getId()
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

        if (
                user == null
                        || user.getOrganization() == null
                        || user.getOrganization().getId() == null
        ) {

            throw new RuntimeException(
                    "User must belong to an organization"
            );
        }

        if (newStatus == null) {

            throw new IllegalArgumentException(
                    "New loan status cannot be null"
            );
        }

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
                            "Only a Pending loan can be moved to Under Review (currently "
                                    + current
                                    + ")"
                    );
                }
            }

            case DEFAULTED -> {

                if (
                        current != LoanStatus.ACTIVE
                                && current != LoanStatus.OVERDUE
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
                        current != LoanStatus.PAID
                                && current != LoanStatus.WRITTEN_OFF
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

        if (notes != null && !notes.isBlank()) {
            loan.setInternalNotes(notes);
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
                        notes != null
                                && !notes.isBlank()
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

        if (org == null || org.getId() == null) {
            throw new IllegalArgumentException(
                    "Organization is required"
            );
        }

        if (page < 0) {
            page = 0;
        }

        if (size <= 0) {
            size = 20;
        }

        LoanStatus ls = null;

        if (status != null && !status.isBlank()) {

            try {

                ls =
                        LoanStatus.valueOf(
                                status.trim().toUpperCase()
                        );

            } catch (IllegalArgumentException e) {

                throw new IllegalArgumentException(
                        "Invalid loan status: " + status
                );
            }
        }

        Loan.LoanType lt = null;

        if (type != null && !type.isBlank()) {

            try {

                lt =
                        Loan.LoanType.valueOf(
                                type.trim().toUpperCase()
                        );

            } catch (IllegalArgumentException e) {

                throw new IllegalArgumentException(
                        "Invalid loan type: " + type
                );
            }
        }

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

        if (loanId == null) {

            throw new IllegalArgumentException(
                    "Loan ID cannot be null"
            );
        }

        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID cannot be null"
            );
        }

        Loan loan =
                loanRepo.findById(
                                loanId
                        )
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Loan not found: "
                                                + loanId
                                )
                        );

        if (
                loan.getOrganization() == null
                        || loan.getOrganization().getId() == null
                        || !loan.getOrganization()
                        .getId()
                        .equals(orgId)
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
                missing.isEmpty()
                        && unverified.isEmpty()
        );

        result.put(
                "noBorrowerLinked",
                false
        );

        return result;
    }

    // ================================================================
    // DASHBOARD
    // ================================================================

    public DashboardStats getDashboard(
            Organization org
    ) {

        if (org == null || org.getId() == null) {

            throw new IllegalArgumentException(
                    "Organization is required"
            );
        }

        LocalDate today =
                LocalDate.now();

        LocalDate firstOfMonth =
                today.withDayOfMonth(1);

        long overdueCount =
                paymentRepo
                        .findByOrganization_IdAndPaidFalseAndDueDateBefore(
                                org.getId(),
                                today
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
                        .collect(Collectors.toList());

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
                                .map(
                                        this::toDouble
                                )
                                .orElse(0.0)
                )
                .totalCollected(
                        Optional.ofNullable(
                                        loanRepo.sumTotalCollected(
                                                org
                                        )
                                )
                                .map(
                                        this::toDouble
                                )
                                .orElse(0.0)
                )
                .outstandingBalance(
                        Optional.ofNullable(
                                        loanRepo.sumOutstandingBalance(
                                                org
                                        )
                                )
                                .map(
                                        this::toDouble
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
                                .map(
                                        this::toDouble
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

    // ================================================================
    // GENERATE REPAYMENT SCHEDULE
    // ================================================================

    private void generateRepaymentSchedule(
            Loan loan
    ) {

        if (loan == null) {
            throw new IllegalArgumentException(
                    "Loan cannot be null"
            );
        }

        if (
                loan.getOrganization() == null
                        || loan.getOrganization().getId() == null
        ) {

            throw new IllegalArgumentException(
                    "Loan organization is required"
            );
        }

        BigDecimal principal =
                normalizePrincipal(
                        moneyValue(
                                loan.getAmountDecimal()
                        )
                );

        BigDecimal rate =
                moneyValue(
                        loan.getInterestRateDecimal()
                );

        String rateType =
                loan.getInterestRateType() != null
                        ? loan.getInterestRateType()
                        : "ANNUAL";

        validateRateType(rateType);

        int months =
                loan.getDurationMonths() != null
                        ? loan.getDurationMonths()
                        : 1;

        if (months <= 0) {
            months = 1;
        }

        BigDecimal monthlyPayment =
                calcLoan(
                        principal,
                        rate,
                        months,
                        rateType
                )[0];

        BigDecimal balance =
                principal;

        BigDecimal monthlyRate;

        if (
                "MONTHLY"
                        .equalsIgnoreCase(rateType)
        ) {

            monthlyRate =
                    rate.divide(
                            bd("100"),
                            16,
                            RoundingMode.HALF_UP
                    );

        } else {

            monthlyRate =
                    rate.divide(
                            bd("100"),
                            16,
                            RoundingMode.HALF_UP
                    )
                    .divide(
                            bd("12"),
                            16,
                            RoundingMode.HALF_UP
                    );
        }

        Long orgId =
                loan.getOrganization()
                        .getId();

        LocalDate startDate =
                loan.getStartDate() != null
                        ? loan.getStartDate()
                        : LocalDate.now();

        LocalDate due =
                holidayService.adjustToBusinessDay(
                        orgId,
                        startDate.plusMonths(1)
                );

        for (
                int i = 1;
                i <= months;
                i++
        ) {

            balance =
                    money(balance);

            BigDecimal interest =
                    money(
                            balance.multiply(
                                    monthlyRate
                            )
                    );

            BigDecimal principalComponent;
            BigDecimal installmentAmount;

            if (i == months) {

                principalComponent =
                        balance;

                installmentAmount =
                        money(
                                principalComponent.add(
                                        interest
                                )
                        );

                balance =
                        BigDecimal.ZERO;

            } else {

                installmentAmount =
                        monthlyPayment;

                principalComponent =
                        money(
                                installmentAmount.subtract(
                                        interest
                                )
                        );

                if (
                        principalComponent.compareTo(
                                BigDecimal.ZERO
                        ) < 0
                ) {

                    principalComponent =
                            BigDecimal.ZERO;
                }

                if (
                        principalComponent.compareTo(
                                balance
                        ) > 0
                ) {

                    principalComponent =
                            balance;
                }

                balance =
                        money(
                                balance.subtract(
                                        principalComponent
                                )
                        );

                if (
                        balance.compareTo(
                                bd("0.01")
                        ) < 0
                ) {

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
                            .loan(loan)
                            .organization(
                                    loan.getOrganization()
                            )
                            .installmentNumber(i)
                            .amount(
                                    installmentAmount
                            )
                            .principalComponent(
                                    money(
                                            principalComponent
                                    )
                            )
                            .interestComponent(
                                    money(
                                            interest
                                    )
                            )
                            .dueDate(due)
                            .paid(false)
                            .amountPaid(
                                    BigDecimal.ZERO
                            )
                            .penalty(
                                    BigDecimal.ZERO
                            )
                            .outstandingAfter(
                                    balance
                            )
                            .status(
                                    Payment.PaymentStatus.PENDING
                            )
                            .build();

            paymentRepo.save(payment);

            due =
                    holidayService.adjustToBusinessDay(
                            orgId,
                            due.plusMonths(1)
                    );
        }

        loan.setAmount(
                principal
        );

        loan.setOutstandingBalance(
                principal
        );

        loan.setNextDueDate(
                holidayService.adjustToBusinessDay(
                        orgId,
                        startDate.plusMonths(1)
                )
        );

        loanRepo.save(loan);
    }

    // ================================================================
    // RISK SCORING
    // ================================================================

    @Async
    public void scoreAsync(
            Loan loan
    ) {

        try {

            if (loan == null) {
                return;
            }

            RiskScoringService.RiskResult risk =
                    riskService.score(loan);

            if (risk == null) {
                return;
            }

            loan.setRiskScore(
                    risk.getScore()
            );

            loan.setRiskCategory(
                    risk.getCategory()
            );

            loanRepo.save(loan);

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

    private BigDecimal adjustRate(
            BigDecimal base,
            int creditScore,
            String rateType
    ) {

        if (base == null) {
            base = bd("15.0");
        }

        if (
                "MONTHLY"
                        .equalsIgnoreCase(rateType)
        ) {

            if (creditScore >= 750) {

                return base
                        .subtract(
                                bd("2.0")
                        )
                        .max(
                                bd("6.0")
                        );
            }

            if (creditScore >= 650) {
                return base;
            }

            return base
                    .add(
                            bd("2.0")
                    )
                    .min(
                            bd("10.0")
                    );
        }

        if (creditScore >= 800) {

            return base.subtract(
                    bd("2.0")
            );
        }

        if (creditScore >= 750) {

            return base.subtract(
                    bd("1.0")
            );
        }

        if (creditScore >= 700) {
            return base;
        }

        if (creditScore >= 650) {

            return base.add(
                    bd("1.0")
            );
        }

        return base.add(
                bd("3.0")
        );
    }

    // ================================================================
    // LOAN CALCULATION
    // ================================================================

    private BigDecimal[] calcLoan(
            BigDecimal principal,
            BigDecimal rate,
            int months,
            String rateType
    ) {

        principal =
                normalizePrincipal(
                        principal
                );

        if (months <= 0) {

            throw new IllegalArgumentException(
                    "Loan duration must be greater than zero"
            );
        }

        if (rate == null) {
            rate = BigDecimal.ZERO;
        }

        if (
                rate.compareTo(
                        BigDecimal.ZERO
                ) < 0
        ) {

            throw new IllegalArgumentException(
                    "Interest rate cannot be negative"
            );
        }

        BigDecimal monthlyRate;

        if (
                "MONTHLY"
                        .equalsIgnoreCase(rateType)
        ) {

            monthlyRate =
                    rate.divide(
                            bd("100"),
                            16,
                            RoundingMode.HALF_UP
                    );

        } else {

            monthlyRate =
                    rate.divide(
                            bd("100"),
                            16,
                            RoundingMode.HALF_UP
                    )
                    .divide(
                            bd("12"),
                            16,
                            RoundingMode.HALF_UP
                    );
        }

        if (
                monthlyRate.compareTo(
                        BigDecimal.ZERO
                ) == 0
        ) {

            BigDecimal monthly =
                    money(
                            principal.divide(
                                    BigDecimal.valueOf(
                                            months
                                    ),
                                    16,
                                    RoundingMode.HALF_UP
                            )
                    );

            return new BigDecimal[]{
                    monthly,
                    money(
                            monthly.multiply(
                                    BigDecimal.valueOf(
                                            months
                                    )
                            )
                    )
            };
        }

        double monthlyRateDouble =
                monthlyRate.doubleValue();

        double factorDouble =
                Math.pow(
                        1.0 + monthlyRateDouble,
                        months
                );

        if (
                Double.isInfinite(
                        factorDouble
                )
                        || Double.isNaN(
                        factorDouble
                )
        ) {

            throw new IllegalArgumentException(
                    "Unable to calculate loan repayment schedule"
            );
        }

        BigDecimal factor =
                BigDecimal.valueOf(
                        factorDouble
                );

        BigDecimal numerator =
                principal
                        .multiply(
                                monthlyRate
                        )
                        .multiply(
                                factor
                        );

        BigDecimal denominator =
                factor.subtract(
                        BigDecimal.ONE
                );

        if (
                denominator.compareTo(
                        BigDecimal.ZERO
                ) == 0
        ) {

            throw new IllegalArgumentException(
                    "Invalid interest calculation"
            );
        }

        BigDecimal monthly =
                money(
                        numerator.divide(
                                denominator,
                                16,
                                RoundingMode.HALF_UP
                        )
                );

        BigDecimal total =
                money(
                        monthly.multiply(
                                BigDecimal.valueOf(
                                        months
                                )
                        )
                );

        return new BigDecimal[]{
                monthly,
                total
        };
    }

    // ================================================================
    // MONEY HELPERS
    // ================================================================

    private static BigDecimal bd(
            String value
    ) {

        return new BigDecimal(
                value
        );
    }

    private static BigDecimal bd(
            double value
    ) {

        if (
                Double.isNaN(value)
                        || Double.isInfinite(value)
        ) {

            throw new IllegalArgumentException(
                    "Invalid numeric value: "
                            + value
            );
        }

        return BigDecimal.valueOf(
                value
        );
    }

    private BigDecimal toBigDecimal(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        if (value instanceof BigDecimal decimal) {
            return decimal;
        }

        if (value instanceof Number number) {

            return BigDecimal.valueOf(
                    number.doubleValue()
            );
        }

        if (value instanceof String string) {

            if (string.isBlank()) {
                return null;
            }

            return new BigDecimal(
                    string.trim()
            );
        }

        throw new IllegalArgumentException(
                "Unsupported numeric type: "
                        + value.getClass().getName()
        );
    }

    private BigDecimal moneyValue(
            Object value
    ) {

        BigDecimal result =
                toBigDecimal(value);

        return result != null
                ? result
                : BigDecimal.ZERO;
    }

    private BigDecimal money(
            BigDecimal value
    ) {

        if (value == null) {

            return BigDecimal.ZERO.setScale(
                    2,
                    RoundingMode.HALF_UP
            );
        }

        return value.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal normalizePrincipal(
            BigDecimal value
    ) {

        if (value == null) {

            throw new IllegalArgumentException(
                    "Invalid loan principal: null"
            );
        }

        if (
                value.compareTo(
                        BigDecimal.ZERO
                ) < 0
        ) {

            throw new IllegalArgumentException(
                    "Invalid loan principal: "
                            + value
            );
        }

        return value.setScale(
                0,
                RoundingMode.HALF_UP
        );
    }

    private double toDouble(
            Object value
    ) {

        return moneyValue(
                value
        ).doubleValue();
    }

    private void validateRateType(
            String rateType
    ) {

        if (
                !"ANNUAL".equalsIgnoreCase(
                        rateType
                )
                        && !"MONTHLY".equalsIgnoreCase(
                        rateType
                )
        ) {

            throw new IllegalArgumentException(
                    "Interest rate type must be MONTHLY or ANNUAL"
            );
        }
    }

    // ================================================================
    // REFERENCES
    // ================================================================

    private String generateRef(
            Organization org
    ) {

        String prefix = "RW";

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
                LocalDateTime.now()
                        .format(
                                DateTimeFormatter.ofPattern(
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

        /*
         * user is intentionally allowed to be null.
         *
         * Public website:
         *     user == null
         *
         * Organization dashboard:
         *     user != null
         *
         * AuditService is responsible for safely recording
         * the public/unauthenticated action.
         */
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