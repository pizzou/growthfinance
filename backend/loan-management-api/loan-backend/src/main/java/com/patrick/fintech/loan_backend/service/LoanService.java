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

    private final LoanRepository       loanRepo;
    private final OrganizationRepository orgRepo;
    private final PaymentRepository    paymentRepo;
    private final BorrowerRepository   borrowerRepo;
    private final RiskScoringService   riskService;
    private final NotificationService  notifService;
    private final MailService          mailService;
    private final SmsService             smsService;
    private final AuditLogRepository   auditRepo;
    private final WebhookService       webhookService;
    private final AuditService         auditService;
    private final LoanProductRepository loanProductRepo;
    private final AccountingService    accountingService;
    private final BorrowerFileService  fileService;
    private final HolidayService       holidayService;
    private final CreditBureauService creditBureauService;

    private final PaymentScheduleService paymentScheduleService;

    /** Used when a loan's product has no requiredDocumentTypes configured (see V23 migration) —
     *  every loan needs at least proof of identity and address on file before it can move. */
      private static final List<DocumentType> DEFAULT_REQUIRED_DOCS = List.of(
            DocumentType.NATIONAL_ID,
            DocumentType.SELFIE,
            DocumentType.PROOF_OF_ADDRESS
    );

    /** Resolves which document types this specific loan needs: the product's configured list
     *  if one exists, otherwise the baseline. Product lookup mirrors createLoan's own lookup,
     *  so "which product governs this loan" stays defined in exactly one place. */
    private List<DocumentType> requiredDocsFor(Loan loan) {

    LoanProduct product = loanProductRepo
            .findFirstByOrganization_IdAndLoanTypeAndActiveTrue(
                    loan.getOrganization().getId(),
                    loan.getLoanType())
            .orElse(null);

    if (product == null) {
        return DEFAULT_REQUIRED_DOCS;
    }

    List<String> configured = product.getRequiredDocumentTypesList();

    if (configured == null || configured.isEmpty()) {
        return DEFAULT_REQUIRED_DOCS;
    }

    List<DocumentType> documentTypes = new ArrayList<>();

    for (String type : configured) {
        try {
            documentTypes.add(DocumentType.valueOf(type.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException(
                    "Invalid document type configured for Loan Product: " + type
            );
        }
    }

    return documentTypes;
}

    // Annual rates by loan type
    private static final Map<Loan.LoanType, Double> BASE_RATES = Map.ofEntries(
        Map.entry(Loan.LoanType.PERSONAL,       10.0),
        Map.entry(Loan.LoanType.MORTGAGE,        8.5),
        Map.entry(Loan.LoanType.AUTO,           10.0),
        Map.entry(Loan.LoanType.BUSINESS,       12.0),
        Map.entry(Loan.LoanType.STUDENT,         10.0),
        Map.entry(Loan.LoanType.EMERGENCY,      10.0),
        Map.entry(Loan.LoanType.ASSET_FINANCE,  11.0),
        Map.entry(Loan.LoanType.SALARY_ADVANCE,  10.0),
        Map.entry(Loan.LoanType.MICROFINANCE,   20.0),
        Map.entry(Loan.LoanType.AGRICULTURAL,    9.0),
        Map.entry(Loan.LoanType.TRADE_FINANCE,  13.0),
        Map.entry(Loan.LoanType.GROUP,          14.0)
    );


    public BorrowerDashboardResponse getBorrowerDashboard(
        String reference,
        String phone) {

    String phoneHash = HmacIndexer.index(phone);

    Loan loan = loanRepo
            .findByReferenceNumberAndBorrower_PhoneHash(
                    reference,
                    phoneHash)
            .orElseThrow(() ->
                    new RuntimeException("Loan not found"));

    return BorrowerDashboardResponse.builder()
            .loanId(loan.getId())
            .referenceNumber(loan.getReferenceNumber())
            .borrowerName(loan.getBorrower().getFullName())
            .loanOfficer(
                    loan.getLoanOfficer() != null
                            ? loan.getLoanOfficer().getFullName()
                            : null)
            .status(loan.getStatus().name())
            .loanType(loan.getLoanType().name())
            .principal(loan.getAmount())
            .outstandingBalance(loan.getOutstandingBalance())
            .totalPaid(loan.getTotalPaid())
            .totalRepayable(loan.getTotalRepayable())
            .nextInstallmentAmount(loan.getNextInstallmentAmount())
            .nextPaymentDate(loan.getNextPaymentDate())
            .maturityDate(loan.getMaturityDate())
            .missedInstallments(loan.getMissedInstallments())
            .daysOverdue(loan.getDaysOverdue())
            .currency(loan.getCurrency())
            .build();
}


public DashboardSummaryResponse getBorrowerSummary(String phone) {

    String phoneHash = HmacIndexer.index(phone);

    List<Loan> loans =
            loanRepo.findByBorrower_PhoneHash(phoneHash);

    if (loans.isEmpty()) {
        throw new RuntimeException("Borrower not found");
    }

    int activeLoans = 0;
    int overdueLoans = 0;

    double totalBorrowed = 0;
    double outstanding = 0;
    double totalPaid = 0;

    Loan nextLoan = null;

    for (Loan loan : loans) {

        totalBorrowed += loan.getAmount() == null ? 0 : loan.getAmount();

        outstanding += loan.getOutstandingBalance() == null
                ? 0
                : loan.getOutstandingBalance();

        totalPaid += loan.getTotalPaid() == null
                ? 0
                : loan.getTotalPaid();

       if (loan.getStatus() == LoanStatus.ACTIVE) {
    activeLoans++;
}

if (loan.getStatus() == LoanStatus.OVERDUE) {
    overdueLoans++;
}

        if (loan.getNextPaymentDate() != null) {

            if (nextLoan == null ||
                    loan.getNextPaymentDate().isBefore(nextLoan.getNextPaymentDate())) {

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
                            : nextLoan.getNextInstallmentAmount())

            .nextPaymentDate(
                    nextLoan == null
                            ? null
                            : nextLoan.getNextPaymentDate())

            .build();
}

    @Transactional
    public Loan createLoan(LoanRequest req, Long organizationId, User createdBy) {
        Organization org = orgRepo.findById(organizationId)
            .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));
        Borrower borrower = borrowerRepo.findById(req.getBorrowerId())
            .orElseThrow(() -> new RuntimeException("Borrower not found: " + req.getBorrowerId()));

        if (!borrower.getOrganization().getId().equals(organizationId))
            throw new RuntimeException("Borrower does not belong to this organization");

        if (borrower.getStatus() == Borrower.BorrowerStatus.BLACKLISTED) {
            throw new RuntimeException(
                "This borrower is blacklisted and cannot be issued a new loan. Reason on file: "
                + (borrower.getBlacklistReason() != null ? borrower.getBlacklistReason() : "not specified"));
        }

        Loan.LoanType requestedType = req.getLoanType() != null ? req.getLoanType() : Loan.LoanType.PERSONAL;
        LoanProduct product = loanProductRepo
            .findFirstByOrganization_IdAndLoanTypeAndActiveTrue(organizationId, requestedType)
            .orElse(null);

        if (product != null) {
            boolean tooLow  = req.getAmount() < product.getMinAmount();
            boolean tooHigh = product.getMaxAmount() != null && req.getAmount() > product.getMaxAmount();
            if (tooLow || tooHigh) {
                String range = product.getMaxAmount() != null
                    ? String.format("between %,.0f and %,.0f", product.getMinAmount(), product.getMaxAmount())
                    : String.format("at least %,.0f", product.getMinAmount());
                throw new RuntimeException(String.format("%s amount must be %s %s",
                    product.getName(), range, org.getDefaultCurrency()));
            }
            if (req.getDurationMonths() < product.getMinTermMonths() || req.getDurationMonths() > product.getMaxTermMonths()) {
                throw new RuntimeException(String.format(
                    "%s term must be between %d and %d months", product.getName(),
                    product.getMinTermMonths(), product.getMaxTermMonths()));
            }
        }

        double rate = req.getInterestRate() != null
            ? req.getInterestRate()
            : product != null ? product.getInterestRate() : BASE_RATES.getOrDefault(requestedType, 15.0);

        String rateType = req.getInterestRate() != null && req.getInterestRateType() != null
            ? req.getInterestRateType()
            : product != null ? product.getInterestRateType() : "ANNUAL";

        // Adjust rate for credit score
        if (borrower.getCreditScore() != null) rate = adjustRate(rate, borrower.getCreditScore(), rateType);

        double principal = req.getAmount();
        int    months    = req.getDurationMonths();
        double[] calc    = calcLoan(principal, rate, months, rateType);
        double feePct    = product != null && product.getProcessingFeePercent() != null ? product.getProcessingFeePercent() : 2.0;
        double procFee   = principal * (feePct / 100.0);
        double dti       = (borrower.getMonthlyIncome() != null && borrower.getMonthlyIncome() > 0)
                           ? (calc[0] / borrower.getMonthlyIncome()) * 100 : 0;

        Loan loan = Loan.builder()
            .referenceNumber(generateRef(org))
            .organization(org)
            .borrower(borrower)
            .loanOfficer(createdBy)
            .loanType(requestedType)
            .repaymentFrequency(req.getRepaymentFrequency() != null
                ? req.getRepaymentFrequency() : Loan.RepaymentFrequency.MONTHLY)
            .status(LoanStatus.PENDING)
            .amount(principal)
            .interestRate(rate)
            .interestRateType(rateType)
            .durationMonths(months)
            .currency(req.getCurrency() != null ? req.getCurrency() : org.getDefaultCurrency())
            .processingFee(round(procFee))
            .totalRepayable(round(calc[1]))
            .outstandingBalance(round(req.getAmount()))
            .totalPaid(0.0)
            .purpose(req.getPurpose())
            .notes(req.getNotes())
            .collateralDescription(req.getCollateralDescription())
            .collateralValue(req.getCollateralValue())
            .startDate(req.getStartDate() != null ? LocalDate.parse(req.getStartDate()) : LocalDate.now())
            .debtToIncomeRatio(round(dti))
            .creditScoreSnapshot(borrower.getCreditScore())
            .build();

        Loan saved = loanRepo.save(loan);

        // Async risk scoring
        scoreAsync(saved);

        audit(org, createdBy, "LOAN_CREATED", "LOAN", saved.getId().toString(),
              "Loan " + saved.getReferenceNumber() + " created for " + borrower.getFullName());

        return saved;
    }

    public Loan approveLoan(Long loanId, User approvedBy, String notes) {
        return approveLoan(loanId, approvedBy, notes, null);
    }

    /**
     * @param newInterestRate optional — lets the approving officer adjust the rate from
     *                        whatever was set at application time (e.g. the public site's
     *                        default 10%/month) to a rate the officer judges more appropriate
     *                        for this borrower. Recalculates totalRepayable using the same
     *                        rateType (MONTHLY/ANNUAL) the loan already has, before the
     *                        actual installment-by-installment schedule is generated below
     *                        (generateRepaymentSchedule reads loan.getInterestRate() fresh,
     *                        so this change flows through automatically). Null keeps the
     *                        rate exactly as it was set at application time.
     */
    @Transactional
    public Loan approveLoan(Long loanId, User approvedBy, String notes, Double newInterestRate) {
        Loan loan = getLoanForOrg(loanId, approvedBy.getOrganization().getId());

        if (loan.getStatus() != LoanStatus.PENDING && loan.getStatus() != LoanStatus.UNDER_REVIEW) {
            throw new RuntimeException("Cannot approve a loan that is " + loan.getStatus()
                + " — only loans that are Pending or Under Review can be approved."
                + (loan.getOutstandingBalance() != null && loan.getOutstandingBalance() <= 0.01 && loan.getStatus() == LoanStatus.PAID
                    ? " This loan has already been fully paid off." : ""));
        }

        if (loan.getBorrower() == null) {
            throw new RuntimeException("Cannot approve loan " + loan.getReferenceNumber()
                + " — it has no borrower record linked. This indicates a data problem; fix the "
                + "borrower link before this loan can proceed.");
        }
        List<DocumentType> missingDocs = fileService.getMissingDocumentTypes(
        loan.getBorrower().getId(),
        requiredDocsFor(loan));

if (!missingDocs.isEmpty()) {
    throw new RuntimeException(
        "Cannot approve this loan — the borrower hasn't uploaded: "
        + missingDocs.stream()
                     .map(DocumentType::name)
                     .collect(java.util.stream.Collectors.joining(", "))
        + ". Upload these documents first, or override the product's document requirements if they genuinely don't apply."
    );
}
        String previousRate = loan.getInterestRate() != null ? loan.getInterestRate() + "%" : "unset";
        if (newInterestRate != null && !newInterestRate.equals(loan.getInterestRate())) {
            double principal = loan.getAmount() != null ? loan.getAmount() : 0;
            int months = loan.getDurationMonths() != null ? loan.getDurationMonths() : 1;
            String rateType = loan.getInterestRateType() != null ? loan.getInterestRateType() : "ANNUAL";
            double[] calc = calcLoan(principal, newInterestRate, months, rateType);
            loan.setInterestRate(newInterestRate);
            loan.setTotalRepayable(round(calc[1]));
        }

        loan.setStatus(LoanStatus.APPROVED);
        loan.setApprovedBy(approvedBy);
        loan.setApprovedAt(LocalDate.now());
        if (notes != null) loan.setInternalNotes(notes);
        Loan saved = loanRepo.save(loan);

        // Idempotency guard: if two approve requests race each other (double-click,
        // slow network + retry), both can pass the status check above before either
        // commits. Only generate the schedule if it doesn't already exist, so the
        // loser of the race is a no-op instead of a duplicate-key error.
        if (paymentRepo.findByLoanId(saved.getId()).isEmpty()) {
            generateRepaymentSchedule(saved);
        } else {
            log.warn("Repayment schedule already exists for loan {}, skipping regeneration", saved.getId());
        }
        audit(loan.getOrganization(), approvedBy, "LOAN_APPROVED", "LOAN",
              loanId.toString(), "Loan " + loan.getReferenceNumber() + " approved"
                  + (newInterestRate != null ? " — rate changed from " + previousRate + " to " + newInterestRate + "%" : ""));
        try { mailService.sendLoanApproved(saved); } catch (Exception e) { log.warn("Notif failed", e); }
        try { smsService.sendLoanApproved(saved); } catch (Exception e) { log.warn("SMS failed", e); }
        notifyOfficer(saved, approvedBy, "Loan Approved",
            "Loan " + saved.getReferenceNumber() + " has been approved by " + approvedBy.getName() + ".", "success");
        webhookService.dispatch(loan.getOrganization(), "LOAN_APPROVED", saved);
        return saved;
    }

    /** Public wrapper around calcLoan for other services (e.g. bulk legacy-loan import)
     *  that need the same amortization math without duplicating it. */
    public double[] amortize(double principal, double rate, int months, String rateType) {
        return calcLoan(principal, rate, months, rateType);
    }

    /** Public wrapper around generateRef so every loan — created normally or via bulk
     *  import — gets a reference number from the same single scheme. */
    public String newReferenceNumber(Organization org) {
        return generateRef(org);
    }

    @Transactional
    public Loan rejectLoan(Long loanId, User rejectedBy, String reason) {
        Loan loan = getLoanForOrg(loanId, rejectedBy.getOrganization().getId());
        if (loan.getStatus() != LoanStatus.PENDING && loan.getStatus() != LoanStatus.UNDER_REVIEW) {
            throw new RuntimeException("Cannot reject a loan that is " + loan.getStatus()
                + " — only loans that are Pending or Under Review can be rejected.");
        }
        loan.setStatus(LoanStatus.REJECTED);
        loan.setRejectionReason(reason);
        Loan saved = loanRepo.save(loan);
        audit(loan.getOrganization(), rejectedBy, "LOAN_REJECTED", "LOAN",
              loanId.toString(), "Reason: " + reason);
        try { mailService.sendLoanRejected(saved); } catch (Exception e) { log.warn("Notif failed", e); }
        try { smsService.sendLoanRejected(saved); } catch (Exception e) { log.warn("SMS failed", e); }
        notifyOfficer(saved, rejectedBy, "Loan Rejected",
            "Loan " + saved.getReferenceNumber() + " has been rejected by " + rejectedBy.getName()
                + (reason != null && !reason.isBlank() ? ". Reason: " + reason : "."), "warning");
        webhookService.dispatch(loan.getOrganization(), "LOAN_REJECTED", saved);
        return saved;
    }

    @Transactional
public Loan disburseLoan(Long loanId, User officer, String disbursementMethod) {

    Loan loan = getLoanForOrg(loanId, officer.getOrganization().getId());

    if (loan.getStatus() != LoanStatus.APPROVED) {
        throw new RuntimeException("Loan must be APPROVED before disbursement");
    }

    if (loan.getBorrower() == null) {
        throw new RuntimeException(
            "Cannot disburse loan " + loan.getReferenceNumber()
            + " — it has no borrower record linked. This indicates a data problem; "
            + "fix the borrower link before funds can be released."
        );
    }

    List<DocumentType> unverifiedDocs =
            fileService.getUnverifiedDocumentTypes(
                    loan.getBorrower().getId(),
                    requiredDocsFor(loan));

    if (!unverifiedDocs.isEmpty()) {
        throw new RuntimeException(
                "Cannot disburse this loan — staff still needs to verify: "
                        + unverifiedDocs.stream()
                        .map(DocumentType::name)
                        .collect(Collectors.joining(", "))
                        + " in the Documents tab before funds can be released."
        );
    }

    // =====================================================
    // DISBURSE LOAN
    // =====================================================

    loan.setStatus(LoanStatus.ACTIVE);
    loan.setDisbursedAt(LocalDate.now());
    loan.setDisbursedAmount(loan.getAmount());
    loan.setMaturityDate(LocalDate.now().plusMonths(loan.getDurationMonths()));
    loan.setNextDueDate(LocalDate.now().plusMonths(1));

    Loan saved = loanRepo.save(loan);

    // =====================================================
    // GENERATE REPAYMENT SCHEDULE
    // =====================================================

    paymentScheduleService.generateSchedule(saved);

    PaymentSchedule first =
            paymentScheduleService.getNextInstallment(saved.getId());

    if (first != null) {
        saved.setNextPaymentDate(first.getDueDate());
        saved.setNextInstallmentAmount(first.getInstallmentAmount());
        saved.setNextDueDate(first.getDueDate());
    }

    saved = loanRepo.save(saved);

    // =====================================================
    // REPORT TO CREDIT BUREAU
    // =====================================================

    try {

        creditBureauService.reportDisbursedLoan(
                saved,
                officer.getName()
        );

        log.info("Loan {} successfully reported to Credit Bureau.",
                saved.getReferenceNumber());

    } catch (Exception ex) {

        log.error(
                "Unable to report loan {} to Credit Bureau.",
                saved.getReferenceNumber(),
                ex
        );

        // Do NOT rollback disbursement because the bureau
        // may simply be temporarily unavailable.
    }

    // =====================================================
    // AUDIT
    // =====================================================

    audit(
            saved.getOrganization(),
            officer,
            "LOAN_DISBURSED",
            "LOAN",
            loanId.toString(),
            "Disbursed via " + disbursementMethod
    );

    // =====================================================
    // ACCOUNTING
    // =====================================================

    accountingService.postDisbursement(saved);

    // =====================================================
    // EMAIL
    // =====================================================

    try {
        mailService.sendLoanDisbursed(saved, disbursementMethod);
    } catch (Exception e) {
        log.warn("Loan disbursement email failed.", e);
    }

    // =====================================================
    // SMS
    // =====================================================

    try {
        smsService.sendLoanDisbursed(saved, disbursementMethod);
    } catch (Exception e) {
        log.warn("Loan disbursement SMS failed.", e);
    }

    // =====================================================
    // IN-APP NOTIFICATION
    // =====================================================

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

    // =====================================================
    // WEBHOOK
    // =====================================================

    webhookService.dispatch(
            saved.getOrganization(),
            "LOAN_DISBURSED",
            saved
    );

    return saved;
}
    /** Notifies the loan's assigned officer in-app when someone else (a manager, another
     *  officer) changes the loan's status — a no-op if the officer isn't set or is the actor. */
    private void notifyOfficer(Loan loan, User actor, String title, String message, String type) {
        User officer = loan.getLoanOfficer();
        if (officer == null || (actor != null && officer.getId().equals(actor.getId()))) return;
        try {
            notifService.notifyUsers(java.util.List.of(officer), title, message, type, "/dashboard/loans/" + loan.getId());
        } catch (Exception e) {
            log.warn("In-app notification failed", e);
        }
    }

    @Transactional
    public Loan updateStatus(Long loanId, User user, LoanStatus newStatus, String notes) {
        Loan loan = getLoanForOrg(loanId, user.getOrganization().getId());
        LoanStatus current = loan.getStatus();

        switch (newStatus) {
            case UNDER_REVIEW -> {
                if (current != LoanStatus.PENDING)
                    throw new RuntimeException("Only a Pending loan can be moved to Under Review (currently " + current + ")");
            }
            case DEFAULTED -> {
                if (current != LoanStatus.ACTIVE && current != LoanStatus.OVERDUE)
                    throw new RuntimeException("Only an Active or Overdue loan can be marked Defaulted (currently " + current + ")");
            }
            case CLOSED -> {
                if (current != LoanStatus.PAID && current != LoanStatus.WRITTEN_OFF)
                    throw new RuntimeException("Only a fully Paid or Written-off loan can be Closed (currently " + current
                        + (loan.getOutstandingBalance() != null && loan.getOutstandingBalance() > 0.01
                            ? " — outstanding balance is " + loan.getOutstandingBalance() : "") + ")");
            }
            case RESTRUCTURED -> throw new RuntimeException(
                "Use the Restructure Loan action instead — it recalculates the repayment schedule correctly rather than just changing the label.");
            default -> throw new RuntimeException(
                "Use the dedicated Approve / Reject / Disburse actions for that change, not this generic status update.");
        }

        loan.setStatus(newStatus);
        if (notes != null && !notes.isBlank()) loan.setInternalNotes(notes);
        Loan saved = loanRepo.save(loan);
        audit(loan.getOrganization(), user, "LOAN_STATUS_CHANGED", "LOAN", loanId.toString(),
            current + " -> " + newStatus + (notes != null && !notes.isBlank() ? ": " + notes : ""));
        webhookService.dispatch(loan.getOrganization(), "LOAN_STATUS_CHANGED", saved);
        return saved;
    }

    public Page<Loan> getLoans(Organization org, int page, int size, String status, String type) {
        LoanStatus ls = (status != null && !status.isBlank()) ? LoanStatus.valueOf(status) : null;
        Loan.LoanType lt = (type != null && !type.isBlank()) ? Loan.LoanType.valueOf(type) : null;
        return loanRepo.findByFilters(org, ls, lt, PageRequest.of(page, size));
    }

    public Loan getLoanForOrg(Long loanId, Long orgId) {
        Loan loan = loanRepo.findById(loanId)
            .orElseThrow(() -> new RuntimeException("Loan not found: " + loanId));
        if (!loan.getOrganization().getId().equals(orgId))
            throw new RuntimeException("Access denied to loan: " + loanId);
        return loan;
    }

    /** Powers the "Required Documents" checklist on the loan detail page — lets an officer see
     *  what's missing/unverified before they click Approve or Disburse and hit the exception. */
    public Map<String, Object> getDocumentRequirements(Long loanId, Long orgId) {
        Loan loan = getLoanForOrg(loanId, orgId);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        if (loan.getBorrower() == null) {
            // Shouldn't happen for any loan created through createLoan() — borrower_id is
            // NOT NULL in the schema — but old/imported data can be missing it. Degrade
            // gracefully instead of throwing, same as the frontend's Documents tab does.
            result.put("required", List.of());
            result.put("missing", List.of());
            result.put("unverified", List.of());
            result.put("readyToApprove", false);
            result.put("readyToDisburse", false);
            result.put("noBorrowerLinked", true);
            return result;
        }
        List<DocumentType> required = requiredDocsFor(loan);

List<DocumentType> missing =
        fileService.getMissingDocumentTypes(
                loan.getBorrower().getId(),
                required);

List<DocumentType> unverified =
        fileService.getUnverifiedDocumentTypes(
                loan.getBorrower().getId(),
                required);

// Convert enums to readable strings for the API response
result.put(
        "required",
        required.stream()
                .map(DocumentType::name)
                .toList());

result.put(
        "missing",
        missing.stream()
                .map(DocumentType::name)
                .toList());

result.put(
        "unverified",
        unverified.stream()
                .map(DocumentType::name)
                .toList());

result.put("readyToApprove", missing.isEmpty());
result.put("readyToDisburse", unverified.isEmpty());

return result;
    }

    public DashboardStats getDashboard(Organization org) {
        LocalDate firstOfMonth = LocalDate.now().withDayOfMonth(1);
        long overdueCount = paymentRepo.findByOrganization_IdAndPaidFalseAndDueDateBefore(
            org.getId(), LocalDate.now()).size();

        List<Map<String,Object>> typeBreakdown = loanRepo.getLoanTypeBreakdown(org).stream()
            .map(r -> { Map<String,Object> m = new LinkedHashMap<>();
                m.put("type", r[0]); m.put("count", r[1]); m.put("amount", r[2]); return m; })
            .collect(Collectors.toList());

        List<Loan> recent = loanRepo.findRecentByOrg(org, PageRequest.of(0, 8));

        return DashboardStats.builder()
            .totalLoans(loanRepo.countByOrganization(org))
            .pendingLoans(loanRepo.countByOrganizationAndStatus(org, LoanStatus.PENDING))
            .activeLoans(loanRepo.countByOrganizationAndStatus(org, LoanStatus.ACTIVE))
            .overdueLoans((long) overdueCount)
            .completedLoans(loanRepo.countByOrganizationAndStatus(org, LoanStatus.PAID))
            .defaultedLoans(loanRepo.countByOrganizationAndStatus(org, LoanStatus.DEFAULTED))
            .totalDisbursed(Optional.ofNullable(loanRepo.sumActivePrincipal(org)).orElse(0.0))
            .totalCollected(Optional.ofNullable(loanRepo.sumTotalCollected(org)).orElse(0.0))
            .outstandingBalance(Optional.ofNullable(loanRepo.sumOutstandingBalance(org)).orElse(0.0))
            .collectedThisMonth(Optional.ofNullable(paymentRepo.sumCollectedSince(org, firstOfMonth)).orElse(0.0))
            .totalBorrowers(borrowerRepo.countByOrganization(org))
            .latePaymentsCount(Optional.ofNullable(paymentRepo.countLatePayments(org)).orElse(0L))
            .loanTypeBreakdown(typeBreakdown)
            .recentLoans(recent)
            .build();
    }

    // ===== helpers =====
   // ===== helpers =====
private void generateRepaymentSchedule(Loan loan) {

    double principal = loan.getAmount() != null ? loan.getAmount() : 0.0;
    double rate = loan.getInterestRate() != null ? loan.getInterestRate() : 0.0;
    String rateType = loan.getInterestRateType() != null
            ? loan.getInterestRateType()
            : "MONTHLY";
    int months = loan.getDurationMonths() != null
            ? loan.getDurationMonths()
            : 1;

    // Monthly installment calculated using the same logic everywhere
    double monthlyPayment = calcLoan(principal, rate, months, rateType)[0];

    // IMPORTANT: Outstanding balance starts with the principal,
    // NOT the total repayable.
    double balance = principal;

    // Monthly interest rate
    double monthlyRate;

    if ("MONTHLY".equalsIgnoreCase(rateType)) {
        monthlyRate = rate / 100.0;
    } else {
        monthlyRate = rate / 100.0 / 12.0;
    }

    Long orgId = loan.getOrganization().getId();

    LocalDate due = holidayService.adjustToBusinessDay(
            orgId,
            (loan.getStartDate() != null
                    ? loan.getStartDate()
                    : LocalDate.now()).plusMonths(1)
    );

    for (int i = 1; i <= months; i++) {

        double interest = round(balance * monthlyRate);

        double principalComponent;

        if (i == months) {
            // Last installment clears whatever principal remains
            principalComponent = balance;
            monthlyPayment = principalComponent + interest;
            balance = 0;
        } else {

            principalComponent = monthlyPayment - interest;

            if (principalComponent < 0) {
                principalComponent = 0;
            }

            balance -= principalComponent;

            if (balance < 0) {
                balance = 0;
            }
        }

        Payment payment = Payment.builder()
                .paymentReference(generatePayRef(loan, i))
                .loan(loan)
                .organization(loan.getOrganization())
                .installmentNumber(i)
                .amount(round(monthlyPayment))
                .principalComponent(round(principalComponent))
                .interestComponent(round(interest))
                .dueDate(due)
                .paid(false)
                .penalty(0.0)
                .outstandingAfter(round(balance))
                .status(Payment.PaymentStatus.PENDING)
                .build();

        paymentRepo.save(payment);

        due = holidayService.adjustToBusinessDay(
                orgId,
                due.plusMonths(1)
        );
    }

    loan.setNextDueDate(
            holidayService.adjustToBusinessDay(
                    orgId,
                    loan.getStartDate() != null
                            ? loan.getStartDate().plusMonths(1)
                            : LocalDate.now().plusMonths(1)
            )
    );

    loanRepo.save(loan);
}

    @Async
    public void scoreAsync(Loan loan) {
        try {
            var risk = riskService.score(loan);
            loan.setRiskScore(risk.getScore());
            loan.setRiskCategory(risk.getCategory());
            loanRepo.save(loan);
        } catch (Exception e) { log.warn("Risk scoring skipped: {}", e.getMessage()); }
    }

    private void audit(Organization org, User user, String action,
                       String entityType, String entityId, String desc) {
        auditService.log(org, user, action, entityType, entityId, desc);
    }

    private double adjustRate(double base, int creditScore, String rateType) {
        if ("MONTHLY".equalsIgnoreCase(rateType)) {
            // Scaled for a 6-10%/month range instead of the wider annual spread below —
            // e.g. an 8%/month base product lands excellent-credit borrowers at 6%,
            // good/fair credit stays at the product's own rate, weaker credit at +2.
            if (creditScore >= 750) return Math.max(6.0, base - 2.0);
            if (creditScore >= 650) return base;
            return Math.min(10.0, base + 2.0);
        }
        if (creditScore >= 800) return base - 2.0;
        if (creditScore >= 750) return base - 1.0;
        if (creditScore >= 700) return base;
        if (creditScore >= 650) return base + 1.0;
        return base + 3.0;
    }

    private double[] calcLoan(double principal, double rate, int months, String rateType) {
        double mr = "MONTHLY".equalsIgnoreCase(rateType) ? rate / 100 : rate / 100 / 12;
        if (mr == 0) return new double[]{principal / months, principal};
        double monthly = principal * (mr * Math.pow(1+mr, months)) / (Math.pow(1+mr, months)-1);
        return new double[]{monthly, monthly * months};
    }

    private double round(double v) { return Math.round(v * 100.0) / 100.0; }

   private String generateRef(Organization org) {

    String prefix = "RW";

    if (org != null &&
        org.getCountry() != null &&
        !org.getCountry().trim().isEmpty()) {
        prefix = org.getCountry().trim().toUpperCase();
    }

    String timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));

    return prefix + timestamp;
}

    private String generatePayRef(Loan loan, int installment) {
        return "PAY-" + loan.getReferenceNumber() + "-" + String.format("%03d", installment);
    }

    // Accessor for controller use
    public LoanRepository getLoanRepository() { return loanRepo; }

}