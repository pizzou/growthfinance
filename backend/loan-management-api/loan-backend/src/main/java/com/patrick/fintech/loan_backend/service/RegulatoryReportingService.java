package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.regulatory.*;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.CreditBureauSubmissionRecordRepository;
import com.patrick.fintech.loan_backend.repository.CreditBureauSubmissionRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegulatoryReportingService {

    private final LoanRepository loanRepository;
    private final OrganizationRepository organizationRepository;
    private final CreditBureauSubmissionRepository submissionRepo;
    private final CreditBureauSubmissionRecordRepository submissionRecordRepo;
    private final AuditService auditService;

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public enum ReportPeriod {
        DAILY,
        WEEKLY,
        MONTHLY,
        QUARTERLY,
        YEARLY,
        CUSTOM
    }

   
    public LocalDate[] resolvePeriod(
            ReportPeriod period,
            LocalDate from,
            LocalDate to) {

        LocalDate today = LocalDate.now();

        if (period == null) {
            period = ReportPeriod.CUSTOM;
        }

        return switch (period) {

            case DAILY ->
                    new LocalDate[]{
                            today,
                            today.plusDays(1)
                    };

            case WEEKLY ->
                    new LocalDate[]{
                            today.with(
                                    TemporalAdjusters.previousOrSame(
                                            DayOfWeek.MONDAY
                                    )
                            ),
                            today.plusDays(1)
                    };

            case MONTHLY ->
                    new LocalDate[]{
                            today.withDayOfMonth(1),
                            today.plusDays(1)
                    };

            case QUARTERLY -> {
                int quarterMonth =
                        ((today.getMonthValue() - 1) / 3) * 3 + 1;

                yield new LocalDate[]{
                        LocalDate.of(
                                today.getYear(),
                                quarterMonth,
                                1
                        ),
                        today.plusDays(1)
                };
            }

            case YEARLY ->
                    new LocalDate[]{
                            today.withDayOfYear(1),
                            today.plusDays(1)
                    };

            case CUSTOM -> {

                if (from == null) {
                    throw new IllegalArgumentException(
                            "Custom report period requires a start date."
                    );
                }

                yield new LocalDate[]{
                        from,
                        to == null ? null : to.plusDays(1)
                };
            }
        };
    }

    /**
     * Converts YYYY-MM into the calendar month's [from, to) window.
     */
    private LocalDate[] resolveNamedPeriod(String period) {

        if (period == null || !period.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException(
                    "Reporting period must use YYYY-MM format."
            );
        }

        LocalDate first = LocalDate.parse(period + "-01");

        return new LocalDate[]{
                first,
                first.plusMonths(1)
        };
    }

    // ============================================================
    // LOAN FETCHING
    // ============================================================

    private List<Loan> fetchLoans(
            Long orgId,
            Long branchId,
            LocalDate from,
            LocalDate to) {

        LocalDateTime fromDt =
                from == null
                        ? null
                        : from.atStartOfDay();

        LocalDateTime toDt =
                to == null
                        ? null
                        : to.atStartOfDay();

        return loanRepository.findForRegulatoryReport(
                orgId,
                branchId,
                fromDt,
                toDt
        );
    }

    // ============================================================
    // BNR SUMMARY
    // ============================================================

    @Transactional(readOnly = true)
    public BnrSummaryReport buildBnrSummary(
            Long orgId,
            Long branchId,
            ReportPeriod period,
            LocalDate from,
            LocalDate to) {

        LocalDate[] window =
                resolvePeriod(period, from, to);

        List<Loan> loans =
                fetchLoans(
                        orgId,
                        branchId,
                        window[0],
                        window[1]
                );

        Organization org =
                organizationRepository.findById(orgId)
                        .orElse(null);

        long active = 0;
        long closed = 0;
        long pending = 0;
        long rejected = 0;
        long overdue = 0;
        long defaulted = 0;

        BigDecimal principalDisbursed = ZERO;
        BigDecimal outstanding = ZERO;
        BigDecimal interestCollected = ZERO;
        BigDecimal interestAccrued = ZERO;
        BigDecimal fees = ZERO;

        long male = 0;
        long female = 0;
        long other = 0;

        BigDecimal parAmount = ZERO;
        BigDecimal nplAmount = ZERO;

        for (Loan loan : loans) {

            LoanStatus status = loan.getStatus();

            if (status != null) {

                switch (status) {

                    case ACTIVE, DISBURSED ->
                            active++;

                    case CLOSED, PAID ->
                            closed++;

                    case PENDING, UNDER_REVIEW, APPROVED ->
                            pending++;

                    case REJECTED, CANCELLED ->
                            rejected++;

                    default -> {
                        // Nothing
                    }
                }

                if (status == LoanStatus.OVERDUE) {
                    overdue++;
                }

                if (status == LoanStatus.DEFAULTED ||
                        status == LoanStatus.WRITTEN_OFF) {

                    defaulted++;
                }
            }

            Integer daysOverdue =
                    loan.getDaysOverdue();

            if (daysOverdue != null && daysOverdue > 0) {
                overdue++;

                BigDecimal loanOutstanding =
                        money(loan.getOutstandingBalance());

                parAmount =
                        parAmount.add(loanOutstanding);

                if (daysOverdue > 90) {
                    nplAmount =
                            nplAmount.add(loanOutstanding);
                }
            }

            principalDisbursed =
                    principalDisbursed.add(
                            money(loan.getDisbursedAmount())
                    );

            outstanding =
                    outstanding.add(
                            money(loan.getOutstandingBalance())
                    );

            fees =
                    fees.add(
                            money(loan.getProcessingFee())
                    );

            if (loan.getPayments() != null) {

                for (Payment payment : loan.getPayments()) {

                    BigDecimal interest =
                            money(payment.getInterestComponent());

                    if (Boolean.TRUE.equals(payment.getPaid())) {

                        interestCollected =
                                interestCollected.add(interest);

                    } else {

                        interestAccrued =
                                interestAccrued.add(interest);
                    }
                }
            }

            // ----------------------------------------------------
            // Gender
            // ----------------------------------------------------

            String gender =
                    loan.getBorrower() != null
                            ? loan.getBorrower().getGender()
                            : null;

            if (gender != null) {

                switch (gender.trim().toUpperCase()) {

                    case "MALE", "M" ->
                            male++;

                    case "FEMALE", "F" ->
                            female++;

                    default ->
                            other++;
                }
            }
        }

        BigDecimal parRatio =
                outstanding.signum() > 0
                        ? parAmount.divide(
                                outstanding,
                                8,
                                java.math.RoundingMode.HALF_UP
                        )
                        : ZERO;

        BigDecimal nplRatio =
                outstanding.signum() > 0
                        ? nplAmount.divide(
                                outstanding,
                                8,
                                java.math.RoundingMode.HALF_UP
                        )
                        : ZERO;

        /*
         * IMPORTANT:
         *
         * The DTO in your existing project appears to use Double
         * for report amounts. Therefore the conversion happens only
         * here at the DTO boundary.
         *
         * Internally all financial calculations remain BigDecimal.
         */
        return BnrSummaryReport.builder()

                .organizationId(orgId)

                .organizationName(
                        org != null
                                ? org.getName()
                                : null
                )

                .bnrInstitutionCode(
                        org != null
                                ? org.getRegistrationNumber()
                                : null
                )

                .branchId(branchId)

                .reportPeriod(
                        (period == null
                                ? ReportPeriod.CUSTOM
                                : period).name()
                )

                .periodStart(window[0])

                .periodEnd(
                        window[1] == null
                                ? null
                                : window[1].minusDays(1)
                )

                .totalLoansIssued(loans.size())

                .activeLoans(active)

                .closedLoans(closed)

                .pendingLoans(pending)

                .rejectedLoans(rejected)

                .overdueLoans(overdue)

                .defaultedLoans(defaulted)

                .totalPrincipalDisbursed(
                        principalDisbursed.doubleValue()
                )

                .outstandingPrincipal(
                        outstanding.doubleValue()
                )

                .totalInterestCollected(
                        interestCollected.doubleValue()
                )

                .interestAccruedUnpaid(
                        interestAccrued.doubleValue()
                )

                .totalProcessingFees(
                        fees.doubleValue()
                )

                .maleBorrowers(male)

                .femaleBorrowers(female)

                .otherGenderBorrowers(other)

                .parAmount(
                        parAmount.doubleValue()
                )

                .parRatio(
                        parRatio.doubleValue()
                )

                .nplAmount(
                        nplAmount.doubleValue()
                )

                .nplRatio(
                        nplRatio.doubleValue()
                )

                .currency(
                        org != null &&
                                org.getDefaultCurrency() != null
                                ? org.getDefaultCurrency()
                                : "RWF"
                )

                .generatedAt(LocalDateTime.now())

                .build();
    }

    // ============================================================
    // BREAKDOWN BY LOAN TYPE
    // ============================================================

    @Transactional(readOnly = true)
    public List<BnrBreakdownRow> breakdownByLoanType(
            Long orgId,
            Long branchId,
            ReportPeriod period,
            LocalDate from,
            LocalDate to) {

        LocalDate[] window =
                resolvePeriod(period, from, to);

        List<Loan> loans =
                fetchLoans(
                        orgId,
                        branchId,
                        window[0],
                        window[1]
                );

        return groupAndSum(
                loans,
                loan ->
                        loan.getLoanType() == null
                                ? "UNSPECIFIED"
                                : loan.getLoanType().name()
        );
    }

    // ============================================================
    // BREAKDOWN BY BRANCH
    // ============================================================

    @Transactional(readOnly = true)
    public List<BnrBreakdownRow> breakdownByBranch(
            Long orgId,
            ReportPeriod period,
            LocalDate from,
            LocalDate to) {

        LocalDate[] window =
                resolvePeriod(period, from, to);

        List<Loan> loans =
                fetchLoans(
                        orgId,
                        null,
                        window[0],
                        window[1]
                );

        return groupAndSum(
                loans,
                loan ->
                        loan.getBranch() == null
                                ? "Unassigned"
                                : loan.getBranch().getName()
        );
    }

    // ============================================================
    // BREAKDOWN BY GENDER
    // ============================================================

    @Transactional(readOnly = true)
    public List<BnrBreakdownRow> breakdownByGender(
            Long orgId,
            Long branchId,
            ReportPeriod period,
            LocalDate from,
            LocalDate to) {

        LocalDate[] window =
                resolvePeriod(period, from, to);

        List<Loan> loans =
                fetchLoans(
                        orgId,
                        branchId,
                        window[0],
                        window[1]
                );

        return groupAndSum(
                loans,
                loan -> {

                    String gender =
                            loan.getBorrower() != null
                                    ? loan.getBorrower().getGender()
                                    : null;

                    if (gender == null) {
                        return "UNSPECIFIED";
                    }

                    return switch (
                            gender.trim().toUpperCase()
                    ) {

                        case "MALE", "M" ->
                                "MALE";

                        case "FEMALE", "F" ->
                                "FEMALE";

                        default ->
                                "OTHER";
                    };
                }
        );
    }

    // ============================================================
    // GROUP AND SUM
    // ============================================================

    private List<BnrBreakdownRow> groupAndSum(
            List<Loan> loans,
            Function<Loan, String> keyFn) {

        Map<String, Long> counts =
                new LinkedHashMap<>();

        Map<String, BigDecimal> amounts =
                new LinkedHashMap<>();

        for (Loan loan : loans) {

            String key =
                    keyFn.apply(loan);

            counts.merge(
                    key,
                    1L,
                    Long::sum
            );

            BigDecimal amount =
                    loan.getDisbursedAmount() != null
                            ? loan.getDisbursedAmount()
                            : loan.getAmount();

            amount =
                    money(amount);

            amounts.merge(
                    key,
                    amount,
                    BigDecimal::add
            );
        }

        return counts.entrySet()
                .stream()
                .map(entry ->
                        new BnrBreakdownRow(
                                entry.getKey(),
                                entry.getValue(),
                                amounts.getOrDefault(
                                        entry.getKey(),
                                        ZERO
                                ).doubleValue()
                        )
                )
                .sorted(
                        Comparator.comparing(
                                BnrBreakdownRow::getLabel
                        )
                )
                .collect(Collectors.toList());
    }

    // ============================================================
    // CREDIT BUREAU LIVE EXPORT
    // ============================================================

    @Transactional(readOnly = true)
    public List<CreditBureauRecord> buildCreditBureauExport(
            Long orgId,
            Long branchId,
            LocalDate from,
            LocalDate to) {

        List<Loan> loans =
                fetchLoans(
                        orgId,
                        branchId,
                        from,
                        to == null
                                ? null
                                : to.plusDays(1)
                );

        List<CreditBureauRecord> result =
                new ArrayList<>();

        for (Loan loan : loans) {
            result.add(toRecord(loan));
        }

        return result;
    }

    // ============================================================
    // LOAN -> CREDIT BUREAU RECORD
    // ============================================================

    private CreditBureauRecord toRecord(Loan loan) {

        Borrower borrower =
                loan.getBorrower();

        boolean closed =
                loan.getStatus() == LoanStatus.CLOSED ||
                loan.getStatus() == LoanStatus.PAID;

        return CreditBureauRecord.builder()

                .borrowerId(
                        borrower != null
                                ? borrower.getId()
                                : null
                )

                .fullName(
                        borrower != null
                                ? buildFullName(borrower)
                                : null
                )

                .nationalId(
                        borrower != null
                                ? borrower.getNationalId()
                                : null
                )

                .dateOfBirth(
                        borrower != null
                                ? borrower.getDateOfBirth()
                                : null
                )

                .gender(
                        borrower != null
                                ? borrower.getGender()
                                : null
                )

                .phone(
                        borrower != null
                                ? borrower.getPhone()
                                : null
                )

                .loanNumber(
                        loan.getReferenceNumber()
                )

                .loanType(
                        loan.getLoanType() != null
                                ? loan.getLoanType().name()
                                : null
                )

                .loanStatus(
                        loan.getStatus() != null
                                ? loan.getStatus().name()
                                : null
                )

                .loanAmount(
                        loan.getDisbursedAmount() != null
                                ? loan.getDisbursedAmount()
                                : loan.getAmount()
                )

                .outstandingBalance(
                        loan.getOutstandingBalance()
                )

                .daysPastDue(
                        loan.getDaysOverdue()
                )

                .creditScore(
                        loan.getCreditScoreSnapshot() != null
                                ? loan.getCreditScoreSnapshot()
                                : borrower != null
                                    ? borrower.getCreditScore()
                                    : null
                )

                .dateOpened(
                        loan.getDisbursedAt() != null
                                ? loan.getDisbursedAt()
                                : loan.getStartDate()
                )

                .lastPaymentDate(
                        loan.getLastPaymentDate()
                )

                .maturityDate(
                        loan.getMaturityDate()
                )

                .dateClosed(
                        closed
                                ? loan.getMaturityDate()
                                : null
                )

                .branchName(
                        loan.getBranch() != null
                                ? loan.getBranch().getName()
                                : null
                )

                .currency(
                        loan.getCurrency()
                )

                .build();
    }

    private String buildFullName(Borrower borrower) {

        String firstName =
                borrower.getFirstName() != null
                        ? borrower.getFirstName()
                        : "";

        String lastName =
                borrower.getLastName() != null
                        ? borrower.getLastName()
                        : "";

        return (firstName + " " + lastName).trim();
    }

    // ============================================================
    // PERSIST CREDIT BUREAU SUBMISSION
    // ============================================================

    @Transactional
    public CreditBureauSubmission persistSubmission(
            Organization org,
            String period,
            String submittedBy) {

        if (org == null || org.getId() == null) {
            throw new IllegalArgumentException(
                    "Organization is required."
            );
        }

        LocalDate[] window =
                resolveNamedPeriod(period);

        List<Loan> loans =
                fetchLoans(
                        org.getId(),
                        null,
                        window[0],
                        window[1]
                );

        List<CreditBureauSubmissionRecord> toSubmit =
                new ArrayList<>();

        for (Loan loan : loans) {

            try {

                Optional<CreditBureauSubmissionRecord> existingOpt =
                        submissionRecordRepo
                                .findLatestForLoanPeriod(
                                        loan.getId(),
                                        period
                                );

                if (existingOpt.isPresent()) {

                    CreditBureauSubmissionRecord existing =
                            existingOpt.get();

                    if (existing.getReportingStatus()
                            == CreditBureauSubmissionRecord.ReportingStatus.PENDING) {

                        /*
                         * Nothing has been submitted yet.
                         * It is safe to replace the pending record.
                         */
                        submissionRecordRepo.delete(existing);

                    } else {

                        /*
                         * Existing record was already reported.
                         * Create a correction instead of mutating
                         * the original record.
                         */
                        CreditBureauSubmissionRecord correction =
                                buildSubmissionRecord(
                                        org,
                                        loan,
                                        period
                                );

                        correction.setCorrectionOfRecordId(
                                existing.getId()
                        );

                        correction.setReportingStatus(
                                CreditBureauSubmissionRecord.ReportingStatus.VALIDATED
                        );

                        submissionRecordRepo.save(correction);

                        existing.setReportingStatus(
                                CreditBureauSubmissionRecord.ReportingStatus.CORRECTED
                        );

                        submissionRecordRepo.save(existing);

                        toSubmit.add(correction);

                        continue;
                    }
                }

                CreditBureauSubmissionRecord fresh =
                        buildSubmissionRecord(
                                org,
                                loan,
                                period
                        );

                fresh.setReportingStatus(
                        CreditBureauSubmissionRecord.ReportingStatus.VALIDATED
                );

                submissionRecordRepo.save(fresh);

                toSubmit.add(fresh);

            } catch (Exception e) {

                log.warn(
                        "Could not build CRB submission record for loan {} period {}: {}",
                        loan.getId(),
                        period,
                        e.getMessage(),
                        e
                );
            }
        }

        if (toSubmit.isEmpty()) {

            throw new IllegalStateException(
                    "No reportable loans found for period " + period
            );
        }

        String payload =
                buildPayload(toSubmit);

        String checksum =
                sha256(payload);

        CreditBureauSubmission submission =
                CreditBureauSubmission.builder()

                        .organization(org)

                        .reportingPeriod(period)

                        .provider("INTERNAL_SIMULATED")

                        .recordCount(
                                toSubmit.size()
                        )

                        .payloadChecksum(
                                checksum
                        )

                        .status(
                                CreditBureauSubmission.Status.PENDING
                        )

                        .submittedBy(
                                submittedBy
                        )

                        .build();

        submission =
                submissionRepo.save(submission);

        /*
         * Simulated transmission.
         *
         * Replace this block with the real CRB API connector
         * when available.
         */
        submission.setStatus(
                CreditBureauSubmission.Status.ACCEPTED
        );

        submission.setSubmittedAt(
                LocalDateTime.now()
        );

        submission.setResponseReference(
                "SIM-" + period + "-" + submission.getId()
        );

        submission.setResponseMessage(
                "Simulated acceptance — no live CRB connector configured."
        );

        submission.setRespondedAt(
                LocalDateTime.now()
        );

        submission =
                submissionRepo.save(submission);

        for (CreditBureauSubmissionRecord record : toSubmit) {

            record.setSubmission(
                    submission
            );

            record.setReportingStatus(
                    CreditBureauSubmissionRecord.ReportingStatus.ACCEPTED
            );

            submissionRecordRepo.save(record);
        }

        auditService.log(
                org,
                null,
                "CRB_SUBMITTED",
                "CreditBureauSubmission",
                String.valueOf(submission.getId()),
                "Submitted "
                        + toSubmit.size()
                        + " record(s) for "
                        + period
                        + " by "
                        + submittedBy
                        + " — checksum "
                        + checksum.substring(0, 12)
                        + "…",
                null,
                null,
                "Regulatory Reporting"
        );

        return submission;
    }

    // ============================================================
    // BUILD SUBMISSION RECORD
    // ============================================================

    private CreditBureauSubmissionRecord buildSubmissionRecord(
            Organization org,
            Loan loan,
            String period) {

        CreditBureauRecord live =
                toRecord(loan);

        return CreditBureauSubmissionRecord.builder()

                .organization(org)

                .borrower(
                        loan.getBorrower()
                )

                .loan(loan)

                .reportingPeriod(period)

                .fullName(
                        live.getFullName()
                )

                .nationalId(
                        live.getNationalId()
                )

                .dateOfBirth(
                        live.getDateOfBirth()
                )

                .gender(
                        live.getGender()
                )

                .phone(
                        live.getPhone()
                )

                .loanNumber(
                        live.getLoanNumber()
                )

                .loanType(
                        live.getLoanType()
                )

                .loanStatus(
                        live.getLoanStatus()
                )

                .loanAmount(
                        live.getLoanAmount()
                )

                .outstandingBalance(
                        live.getOutstandingBalance()
                )

                .daysPastDue(
                        live.getDaysPastDue()
                )

                .creditScore(
                        live.getCreditScore()
                )

                .dateOpened(
                        live.getDateOpened()
                )

                .lastPaymentDate(
                        live.getLastPaymentDate()
                )

                .maturityDate(
                        live.getMaturityDate()
                )

                .dateClosed(
                        live.getDateClosed()
                )

                .branchName(
                        live.getBranchName()
                )

                .currency(
                        live.getCurrency()
                )

                .classification(
                        loan.getCreditQuality() != null
                                ? loan.getCreditQuality().name()
                                : "CURRENT"
                )

                .repaymentStatus(
                        loan.getArrearsStatus() != null
                                ? loan.getArrearsStatus().name()
                                : "NOT_DUE"
                )

                .reportingStatus(
                        CreditBureauSubmissionRecord.ReportingStatus.PENDING
                )

                .build();
    }

    // ============================================================
    // BUILD CREDIT BUREAU PAYLOAD
    // ============================================================

    private String buildPayload(
            List<CreditBureauSubmissionRecord> records) {

        StringBuilder sb =
                new StringBuilder("[\n");

        for (int i = 0; i < records.size(); i++) {

            CreditBureauSubmissionRecord record =
                    records.get(i);

            sb.append("  {")
                    .append("\"loanNumber\":")
                    .append(jsonString(record.getLoanNumber()))
                    .append(",")

                    .append("\"nationalId\":")
                    .append(jsonString(record.getNationalId()))
                    .append(",")

                    .append("\"reportingPeriod\":")
                    .append(jsonString(record.getReportingPeriod()))
                    .append(",")

                    .append("\"outstandingBalance\":")
                    .append(jsonNumber(record.getOutstandingBalance()))
                    .append(",")

                    .append("\"daysPastDue\":")
                    .append(record.getDaysPastDue() == null
                            ? "null"
                            : record.getDaysPastDue())
                    .append(",")

                    .append("\"classification\":")
                    .append(jsonString(record.getClassification()))

                    .append("}");

            if (i < records.size() - 1) {
                sb.append(",");
            }

            sb.append("\n");
        }

        sb.append("]");

        return sb.toString();
    }

    private String jsonString(String value) {

        if (value == null) {
            return "null";
        }

        return "\""
                + value
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                + "\"";
    }

    private String jsonNumber(BigDecimal value) {

        return value == null
                ? "null"
                : value.toPlainString();
    }

    // ============================================================
    // SHA-256
    // ============================================================

    private String sha256(String input) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash =
                    digest.digest(
                            input.getBytes(StandardCharsets.UTF_8)
                    );

            StringBuilder hex =
                    new StringBuilder();

            for (byte b : hash) {

                hex.append(
                        String.format(
                                "%02x",
                                b
                        )
                );
            }

            return hex.toString();

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Could not compute payload checksum",
                    e
            );
        }
    }

    // ============================================================
    // PERSISTED CREDIT BUREAU HISTORY
    // ============================================================

    @Transactional(readOnly = true)
    public List<CreditBureauSubmissionRecord>
    getSubmissionRecordsForPeriod(
            Long orgId,
            String period) {

        return submissionRecordRepo
                .findByOrganization_IdAndReportingPeriodOrderByIdAsc(
                        orgId,
                        period
                );
    }

    @Transactional(readOnly = true)
    public List<CreditBureauSubmissionRecord>
    getHistoryForLoan(Long loanId) {

        return submissionRecordRepo
                .findByLoan_IdOrderByCreatedAtDesc(
                        loanId
                );
    }

    @Transactional(readOnly = true)
    public List<CreditBureauSubmission>
    getSubmissions(Long orgId) {

        return submissionRepo
                .findByOrganization_IdOrderByCreatedAtDesc(
                        orgId
                );
    }

    @Transactional(readOnly = true)
    public CreditBureauSubmission getSubmission(
            Long id,
            Long orgId) {

        return submissionRepo
                .findByIdAndOrganization_Id(
                        id,
                        orgId
                )
                .orElseThrow(
                        () -> new RuntimeException(
                                "Submission not found: " + id
                        )
                );
    }

    // ============================================================
    // MONEY HELPER
    // ============================================================

    private BigDecimal money(BigDecimal value) {

        return value == null
                ? ZERO
                : value;
    }
}