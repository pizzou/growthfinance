package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreditBureauDisputeService {

    private final CreditBureauDisputeRepository disputeRepo;
    private final BorrowerRepository borrowerRepo;
    private final LoanRepository loanRepo;
    private final CreditBureauSubmissionRecordRepository submissionRecordRepo;
    private final AuditService auditService;

    @Transactional
    public CreditBureauDispute open(Organization org, Long borrowerId, Long loanId, Long submissionRecordId,
                                     String reason, String disputedField, String oldValue, String requestedValue,
                                     String supportingDocumentUrl) {
        Borrower borrower = borrowerRepo.findById(borrowerId)
            .orElseThrow(() -> new IllegalArgumentException("Borrower not found: " + borrowerId));
        if (!borrower.getOrganization().getId().equals(org.getId()))
            throw new RuntimeException("Access denied");

        Loan loan = loanId != null ? loanRepo.findById(loanId).orElse(null) : null;
        CreditBureauSubmissionRecord record = submissionRecordId != null
            ? submissionRecordRepo.findById(submissionRecordId).orElse(null) : null;

        CreditBureauDispute dispute = CreditBureauDispute.builder()
            .organization(org)
            .borrower(borrower)
            .loan(loan)
            .submissionRecord(record)
            .reason(reason)
            .disputedField(disputedField)
            .oldValue(oldValue)
            .requestedValue(requestedValue)
            .supportingDocumentUrl(supportingDocumentUrl)
            .status(CreditBureauDispute.Status.OPEN)
            .build();
        dispute = disputeRepo.save(dispute);

        auditService.log(org, null, "CRB_DISPUTE_OPENED", "CreditBureauDispute", String.valueOf(dispute.getId()),
            "Dispute opened for " + borrower.getFirstName() + " " + borrower.getLastName()
                + (disputedField != null ? " on field '" + disputedField + "'" : ""));

        return dispute;
    }

    @Transactional
    public CreditBureauDispute review(Long id, Long orgId, String reviewer, String notes) {
        CreditBureauDispute d = getForOrg(id, orgId);
        d.setStatus(CreditBureauDispute.Status.UNDER_REVIEW);
        d.setReviewedBy(reviewer);
        d.setReviewedAt(LocalDateTime.now());
        if (notes != null) d.setResolution(notes);
        d = disputeRepo.save(d);
        auditService.log(d.getOrganization(), null, "CRB_DISPUTE_REVIEWED", "CreditBureauDispute", String.valueOf(id),
            "Under review by " + reviewer);
        return d;
    }

    /** Accepting a dispute doesn't retroactively edit any already-submitted
     *  CreditBureauSubmissionRecord — that would destroy history. Fix the underlying
     *  Borrower/Loan data instead; the next persistSubmission() run for a later period will
     *  naturally produce a CORRECTED record chain reflecting the fix. */
    @Transactional
    public CreditBureauDispute resolve(Long id, Long orgId, String reviewer, CreditBureauDispute.Status outcome, String resolution) {
        if (outcome != CreditBureauDispute.Status.ACCEPTED && outcome != CreditBureauDispute.Status.REJECTED
            && outcome != CreditBureauDispute.Status.CORRECTED && outcome != CreditBureauDispute.Status.ESCALATED)
            throw new IllegalArgumentException("Invalid resolution outcome: " + outcome);

        CreditBureauDispute d = getForOrg(id, orgId);
        d.setStatus(outcome);
        d.setReviewedBy(reviewer);
        d.setReviewedAt(LocalDateTime.now());
        d.setResolution(resolution);
        d = disputeRepo.save(d);

        auditService.log(d.getOrganization(), null, "CRB_DISPUTE_RESOLVED", "CreditBureauDispute", String.valueOf(id),
            "Resolved as " + outcome + " by " + reviewer + (resolution != null ? ": " + resolution : ""));

        return d;
    }

    public List<CreditBureauDispute> list(Long orgId, CreditBureauDispute.Status status) {
        return status != null
            ? disputeRepo.findByOrganization_IdAndStatusOrderByCreatedAtDesc(orgId, status)
            : disputeRepo.findByOrganization_IdOrderByCreatedAtDesc(orgId);
    }

    public List<CreditBureauDispute> listForBorrower(Long borrowerId) {
        return disputeRepo.findByBorrower_IdOrderByCreatedAtDesc(borrowerId);
    }

    public CreditBureauDispute getForOrg(Long id, Long orgId) {
        return disputeRepo.findByIdAndOrganization_Id(id, orgId)
            .orElseThrow(() -> new RuntimeException("Dispute not found: " + id));
    }
}