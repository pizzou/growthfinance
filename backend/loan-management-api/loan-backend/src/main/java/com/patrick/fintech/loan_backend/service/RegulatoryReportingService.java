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

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds the two regulatory report families described in the platform's reporting
 * requirements:
 *  - BNR (National Bank of Rwanda): aggregate portfolio statistics — counts, principal,
 *    interest, gender split, PAR/NPL.
 *  - Credit Bureau: borrower-level records — identity + repayment status per loan.
 *
 * Both are scoped to a single organization (tenant) and optionally a branch and date
 * window. Aggregation is done in-memory over the filtered loan set rather than in SQL —
 * loan books at MFI/SACCO scale are small enough that this is simpler and safer than
 * hand-rolling aggregate JPQL across encrypted borrower columns.
 *
 * persistSubmission() below is the one addition beyond the original live export: it takes
 * exactly what buildCreditBureauExport() already produces and snapshots it into an immutable,
 * checksummed record per loan per period — so "what did we report in July" stays answerable
 * after August's data changes. The live preview/export endpoints are unchanged and still work
 * exactly as before; this is purely additive.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegulatoryReportingService {

    private final LoanRepository loanRepository;
    private final OrganizationRepository organizationRepository;
    private final CreditBureauSubmissionRepository submissionRepo;
    private final CreditBureauSubmissionRecordRepository submissionRecordRepo;
    private final AuditService auditService;

    public enum ReportPeriod { DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY, CUSTOM }

    // ---------- period resolution ----------

    /** Resolves a named period (or CUSTOM with explicit from/to) into a concrete [from, to) window. */
    public LocalDate[] resolvePeriod(ReportPeriod period, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        if (period == null) period = ReportPeriod.CUSTOM;
        return switch (period) {
            case DAILY -> new LocalDate[]{ today, today.plusDays(1) };
            case WEEKLY -> new LocalDate[]{ today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)), today.plusDays(1) };
            case MONTHLY -> new LocalDate[]{ today.withDayOfMonth(1), today.plusDays(1) };
            case QUARTERLY -> {
                int qMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                yield new LocalDate[]{ LocalDate.of(today.getYear(), qMonth, 1), today.plusDays(1) };
            }
            case YEARLY -> new LocalDate[]{ today.withDayOfYear(1), today.plusDays(1) };
            case CUSTOM -> new LocalDate[]{ from, to == null ? null : to.plusDays(1) };
        };
    }

    /** 'YYYY-MM' -> the calendar month's [from, to) window. */
    private LocalDate[] resolveNamedPeriod(String period) {
        LocalDate first = LocalDate.parse(period + "-01");
        return new LocalDate[]{ first, first.plusMonths(1) };
    }

    private List<Loan> fetchLoans(Long orgId, Long branchId, LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from == null ? null : from.atStartOfDay();
        LocalDateTime toDt = to == null ? null : to.atStartOfDay();
        return loanRepository.findForRegulatoryReport(orgId, branchId, fromDt, toDt);
    }

    // ---------- BNR summary ----------

    @Transactional(readOnly = true)
    public BnrSummaryReport buildBnrSummary(Long orgId, Long branchId, ReportPeriod period, LocalDate from, LocalDate to) {
        LocalDate[] window = resolvePeriod(period, from, to);
        List<Loan> loans = fetchLoans(orgId, branchId, window[0], window[1]);
        Organization org = organizationRepository.findById(orgId).orElse(null);

        long active = 0, closed = 0, pending = 0, rejected = 0, overdue = 0, defaulted = 0;
        double principalDisbursed = 0, outstanding = 0, interestCollected = 0, interestAccrued = 0, fees = 0;
        long male = 0, female = 0, other = 0;
        double parAmount = 0, nplAmount = 0;

        for (Loan l : loans) {
            LoanStatus s = l.getStatus();
            switch (s) {
                case ACTIVE, DISBURSED -> active++;
                case CLOSED, PAID -> closed++;
                case PENDING, UNDER_REVIEW, APPROVED -> pending++;
                case REJECTED, CANCELLED -> rejected++;
                default -> { }
            }
            if (s == LoanStatus.OVERDUE || (l.getDaysOverdue() != null && l.getDaysOverdue() > 0)) overdue++;
            if (s == LoanStatus.DEFAULTED || s == LoanStatus.WRITTEN_OFF) defaulted++;

            if (l.getDisbursedAmount() != null) principalDisbursed += l.getDisbursedAmount();
            if (l.getOutstandingBalance() != null) outstanding += l.getOutstandingBalance();
            if (l.getProcessingFee() != null) fees += l.getProcessingFee();

            if (l.getPayments() != null) {
                for (Payment p : l.getPayments()) {
                    if (p.getInterestComponent() == null) continue;
                    if (Boolean.TRUE.equals(p.getPaid())) interestCollected += p.getInterestComponent();
                    else interestAccrued += p.getInterestComponent();
                }
            }

            String gender = l.getBorrower() != null ? l.getBorrower().getGender() : null;
            if (gender != null) {
                switch (gender.trim().toUpperCase()) {
                    case "MALE", "M" -> male++;
                    case "FEMALE", "F" -> female++;
                    default -> other++;
                }
            }

            Integer dpd = l.getDaysOverdue();
            if (dpd != null && dpd > 0 && l.getOutstandingBalance() != null) {
                parAmount += l.getOutstandingBalance();
                if (dpd > 90) nplAmount += l.getOutstandingBalance();
            }
        }

        return BnrSummaryReport.builder()
            .organizationId(orgId)
            .organizationName(org != null ? org.getName() : null)
            .bnrInstitutionCode(org != null ? org.getRegistrationNumber() : null)
            .branchId(branchId)
            .reportPeriod((period == null ? ReportPeriod.CUSTOM : period).name())
            .periodStart(window[0])
            .periodEnd(window[1] == null ? null : window[1].minusDays(1))
            .totalLoansIssued(loans.size())
            .activeLoans(active)
            .closedLoans(closed)
            .pendingLoans(pending)
            .rejectedLoans(rejected)
            .overdueLoans(overdue)
            .defaultedLoans(defaulted)
            .totalPrincipalDisbursed(principalDisbursed)
            .outstandingPrincipal(outstanding)
            .totalInterestCollected(interestCollected)
            .interestAccruedUnpaid(interestAccrued)
            .totalProcessingFees(fees)
            .maleBorrowers(male)
            .femaleBorrowers(female)
            .otherGenderBorrowers(other)
            .parAmount(parAmount)
            .parRatio(outstanding > 0 ? parAmount / outstanding : 0)
            .nplAmount(nplAmount)
            .nplRatio(outstanding > 0 ? nplAmount / outstanding : 0)
            .currency(org != null ? org.getDefaultCurrency() : "RWF")
            .generatedAt(LocalDateTime.now())
            .build();
    }

    @Transactional(readOnly = true)
    public List<BnrBreakdownRow> breakdownByLoanType(Long orgId, Long branchId, ReportPeriod period, LocalDate from, LocalDate to) {
        LocalDate[] window = resolvePeriod(period, from, to);
        List<Loan> loans = fetchLoans(orgId, branchId, window[0], window[1]);
        return groupAndSum(loans, l -> l.getLoanType() == null ? "UNSPECIFIED" : l.getLoanType().name());
    }

    @Transactional(readOnly = true)
    public List<BnrBreakdownRow> breakdownByBranch(Long orgId, ReportPeriod period, LocalDate from, LocalDate to) {
        LocalDate[] window = resolvePeriod(period, from, to);
        List<Loan> loans = fetchLoans(orgId, null, window[0], window[1]);
        return groupAndSum(loans, l -> l.getBranch() == null ? "Unassigned" : l.getBranch().getName());
    }

    @Transactional(readOnly = true)
    public List<BnrBreakdownRow> breakdownByGender(Long orgId, Long branchId, ReportPeriod period, LocalDate from, LocalDate to) {
        LocalDate[] window = resolvePeriod(period, from, to);
        List<Loan> loans = fetchLoans(orgId, branchId, window[0], window[1]);
        return groupAndSum(loans, l -> {
            String g = l.getBorrower() != null ? l.getBorrower().getGender() : null;
            if (g == null) return "UNSPECIFIED";
            return switch (g.trim().toUpperCase()) {
                case "MALE", "M" -> "MALE";
                case "FEMALE", "F" -> "FEMALE";
                default -> "OTHER";
            };
        });
    }

    private List<BnrBreakdownRow> groupAndSum(List<Loan> loans, java.util.function.Function<Loan, String> keyFn) {
        Map<String, long[]> counts = new LinkedHashMap<>();
        Map<String, double[]> amounts = new LinkedHashMap<>();
        for (Loan l : loans) {
            String key = keyFn.apply(l);
            counts.computeIfAbsent(key, k -> new long[1])[0]++;
            double amt = l.getDisbursedAmount() != null ? l.getDisbursedAmount() : (l.getAmount() != null ? l.getAmount() : 0);
            amounts.computeIfAbsent(key, k -> new double[1])[0] += amt;
        }
        return counts.entrySet().stream()
            .map(e -> new BnrBreakdownRow(e.getKey(), e.getValue()[0], amounts.get(e.getKey())[0]))
            .sorted(Comparator.comparing(BnrBreakdownRow::getLabel))
            .collect(Collectors.toList());
    }

    // ---------- Credit Bureau: live export (unchanged) ----------

    @Transactional(readOnly = true)
    public List<CreditBureauRecord> buildCreditBureauExport(Long orgId, Long branchId, LocalDate from, LocalDate to) {
        List<Loan> loans = fetchLoans(orgId, branchId, from, to == null ? null : to.plusDays(1));
        List<CreditBureauRecord> out = new ArrayList<>();
        for (Loan l : loans) {
            out.add(toRecord(l));
        }
        return out;
    }

    private CreditBureauRecord toRecord(Loan l) {
        Borrower b = l.getBorrower();
        boolean closed = l.getStatus() == LoanStatus.CLOSED || l.getStatus() == LoanStatus.PAID;
        return CreditBureauRecord.builder()
            .borrowerId(b != null ? b.getId() : null)
            .fullName(b != null ? ((b.getFirstName() != null ? b.getFirstName() : "") + " " + (b.getLastName() != null ? b.getLastName() : "")).trim() : null)
            .nationalId(b != null ? b.getNationalId() : null)
            .dateOfBirth(b != null ? b.getDateOfBirth() : null)
            .gender(b != null ? b.getGender() : null)
            .phone(b != null ? b.getPhone() : null)
            .loanNumber(l.getReferenceNumber())
            .loanType(l.getLoanType() != null ? l.getLoanType().name() : null)
            .loanStatus(l.getStatus() != null ? l.getStatus().name() : null)
            .loanAmount(l.getDisbursedAmount() != null ? l.getDisbursedAmount() : l.getAmount())
            .outstandingBalance(l.getOutstandingBalance())
            .daysPastDue(l.getDaysOverdue())
            .creditScore(l.getCreditScoreSnapshot() != null ? l.getCreditScoreSnapshot() : (b != null ? b.getCreditScore() : null))
            .dateOpened(l.getDisbursedAt() != null ? l.getDisbursedAt() : l.getStartDate())
            .lastPaymentDate(l.getLastPaymentDate())
            .maturityDate(l.getMaturityDate())
            .dateClosed(closed ? l.getMaturityDate() : null)
            .branchName(l.getBranch() != null ? l.getBranch().getName() : null)
            .currency(l.getCurrency())
            .build();
    }

    
    @Transactional
    public CreditBureauSubmission persistSubmission(Organization org, String period, String submittedBy) {
        LocalDate[] window = resolveNamedPeriod(period);
        List<Loan> loans = fetchLoans(org.getId(), null, window[0], window[1]);

        List<CreditBureauSubmissionRecord> toSubmit = new ArrayList<>();
        for (Loan loan : loans) {
            try {
                Optional<CreditBureauSubmissionRecord> existingOpt =
                    submissionRecordRepo.findLatestForLoanPeriod(loan.getId(), period);

                if (existingOpt.isPresent()) {
                    CreditBureauSubmissionRecord existing = existingOpt.get();
                    if (existing.getReportingStatus() == CreditBureauSubmissionRecord.ReportingStatus.PENDING) {
                        submissionRecordRepo.delete(existing); // nothing sent yet — safe to replace
                    } else {
                        CreditBureauSubmissionRecord correction = buildSubmissionRecord(org, loan, period);
                        correction.setCorrectionOfRecordId(existing.getId());
                        correction.setReportingStatus(CreditBureauSubmissionRecord.ReportingStatus.VALIDATED);
                        submissionRecordRepo.save(correction);
                        existing.setReportingStatus(CreditBureauSubmissionRecord.ReportingStatus.CORRECTED);
                        submissionRecordRepo.save(existing);
                        toSubmit.add(correction);
                        continue;
                    }
                }

                CreditBureauSubmissionRecord fresh = buildSubmissionRecord(org, loan, period);
                fresh.setReportingStatus(CreditBureauSubmissionRecord.ReportingStatus.VALIDATED);
                submissionRecordRepo.save(fresh);
                toSubmit.add(fresh);
            } catch (Exception e) {
                log.warn("Could not build CRB submission record for loan {} period {}: {}", loan.getId(), period, e.getMessage());
            }
        }

        if (toSubmit.isEmpty())
            throw new IllegalStateException("No reportable loans found for period " + period);

        String payload = buildPayload(toSubmit);
        String checksum = sha256(payload);

        CreditBureauSubmission submission = CreditBureauSubmission.builder()
            .organization(org)
            .reportingPeriod(period)
            .provider("INTERNAL_SIMULATED")
            .recordCount(toSubmit.size())
            .payloadChecksum(checksum)
            .status(CreditBureauSubmission.Status.PENDING)
            .submittedBy(submittedBy)
            .build();
        submission = submissionRepo.save(submission);

        // "Transmit" — simulated. Replace with a real CRB API call once connectivity is
        // confirmed; the record-keeping above/below is unchanged either way.
        submission.setStatus(CreditBureauSubmission.Status.ACCEPTED);
        submission.setSubmittedAt(LocalDateTime.now());
        submission.setResponseReference("SIM-" + period + "-" + submission.getId());
        submission.setResponseMessage("Simulated acceptance — no live CRB connector configured.");
        submission.setRespondedAt(LocalDateTime.now());
        submission = submissionRepo.save(submission);

        for (CreditBureauSubmissionRecord r : toSubmit) {
            r.setSubmission(submission);
            r.setReportingStatus(CreditBureauSubmissionRecord.ReportingStatus.ACCEPTED);
            submissionRecordRepo.save(r);
        }

        auditService.log(org, null, "CRB_SUBMITTED", "CreditBureauSubmission", String.valueOf(submission.getId()),
            "Submitted " + toSubmit.size() + " record(s) for " + period + " by " + submittedBy
                + " — checksum " + checksum.substring(0, 12) + "…",
            null, null, "Regulatory Reporting");

        return submission;
    }

    private CreditBureauSubmissionRecord buildSubmissionRecord(Organization org, Loan loan, String period) {
        CreditBureauRecord live = toRecord(loan);
        return CreditBureauSubmissionRecord.builder()
            .organization(org)
            .borrower(loan.getBorrower())
            .loan(loan)
            .reportingPeriod(period)
            .fullName(live.getFullName())
            .nationalId(live.getNationalId())
            .dateOfBirth(live.getDateOfBirth())
            .gender(live.getGender())
            .phone(live.getPhone())
            .loanNumber(live.getLoanNumber())
            .loanType(live.getLoanType())
            .loanStatus(live.getLoanStatus())
            .loanAmount(live.getLoanAmount())
            .outstandingBalance(live.getOutstandingBalance())
            .daysPastDue(live.getDaysPastDue())
            .creditScore(live.getCreditScore())
            .dateOpened(live.getDateOpened())
            .lastPaymentDate(live.getLastPaymentDate())
            .maturityDate(live.getMaturityDate())
            .dateClosed(live.getDateClosed())
            .branchName(live.getBranchName())
            .currency(live.getCurrency())
            .classification(loan.getCreditQuality() != null ? loan.getCreditQuality().name() : "CURRENT")
            .repaymentStatus(loan.getArrearsStatus() != null ? loan.getArrearsStatus().name() : "NOT_DUE")
            .reportingStatus(CreditBureauSubmissionRecord.ReportingStatus.PENDING)
            .build();
    }

    private String buildPayload(List<CreditBureauSubmissionRecord> records) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < records.size(); i++) {
            CreditBureauSubmissionRecord r = records.get(i);
            sb.append("  {")
              .append("\"loanNumber\":\"").append(r.getLoanNumber()).append("\",")
              .append("\"nationalId\":\"").append(r.getNationalId()).append("\",")
              .append("\"reportingPeriod\":\"").append(r.getReportingPeriod()).append("\",")
              .append("\"outstandingBalance\":").append(r.getOutstandingBalance()).append(",")
              .append("\"daysPastDue\":").append(r.getDaysPastDue()).append(",")
              .append("\"classification\":\"").append(r.getClassification()).append("\"")
              .append("}").append(i < records.size() - 1 ? ",\n" : "\n");
        }
        sb.append("]");
        return sb.toString();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not compute payload checksum", e);
        }
    }

    // ---------- Credit Bureau: reads for persisted history (NEW) ----------

    public List<CreditBureauSubmissionRecord> getSubmissionRecordsForPeriod(Long orgId, String period) {
        return submissionRecordRepo.findByOrganization_IdAndReportingPeriodOrderByIdAsc(orgId, period);
    }

    public List<CreditBureauSubmissionRecord> getHistoryForLoan(Long loanId) {
        return submissionRecordRepo.findByLoan_IdOrderByCreatedAtDesc(loanId);
    }

    public List<CreditBureauSubmission> getSubmissions(Long orgId) {
        return submissionRepo.findByOrganization_IdOrderByCreatedAtDesc(orgId);
    }

    public CreditBureauSubmission getSubmission(Long id, Long orgId) {
        return submissionRepo.findByIdAndOrganization_Id(id, orgId)
            .orElseThrow(() -> new RuntimeException("Submission not found: " + id));
    }
}