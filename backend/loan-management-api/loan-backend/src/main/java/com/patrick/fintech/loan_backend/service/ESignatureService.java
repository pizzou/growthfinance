package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.ESignatureRequestRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Lightweight, audit-trail-based e-signature workflow ("click-to-sign") for
 * loan agreements — the same pattern used by HelloSign/DocuSign click-wrap
 * flows: the borrower receives a one-time link + OTP, reviews the rendered
 * agreement, types their full legal name, and confirms via OTP. We capture
 * IP address, user agent, timestamp, and a SHA-256 hash of the exact
 * document text shown at signing time to preserve evidentiary integrity.
 *
 * This is not a PKI/qualified-signature product — for jurisdictions that
 * require an advanced/qualified electronic signature it should be paired
 * with a licensed signature provider. For most microfinance/SACCO loan
 * agreements in the region this audit-trail model is the standard.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ESignatureService {

    private final ESignatureRequestRepository esignRepo;
    private final LoanRepository loanRepo;
    private final SmsService smsService;
    private final MailService mailService;
    private final AuditService auditService;

    @org.springframework.beans.factory.annotation.Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    // backend/loan-management-api/loan-backend/src/main/java/com/patrick/fintech/loan_backend/service/ESignatureService.java

@Transactional
public ESignatureRequest initiate(Long loanId, String documentType, String initiatedBy) {
    Loan loan = loanRepo.findById(loanId).orElseThrow(() -> new RuntimeException("Loan not found: " + loanId));
    Borrower borrower = loan.getBorrower();
    if (borrower == null) throw new RuntimeException("Loan has no borrower on file");
    boolean hasPhone = borrower.getPhone() != null && !borrower.getPhone().isBlank();
    boolean hasEmail = borrower.getEmail() != null && !borrower.getEmail().isBlank();
    if (!hasPhone && !hasEmail)
        throw new RuntimeException("Borrower has no phone number or email on file to receive the signing OTP");

    String token = UUID.randomUUID().toString().replace("-", "");
    String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
    String docText = renderAgreement(loan, borrower);
    String signLink = frontendUrl + "/sign/" + token;

    ESignatureRequest req = ESignatureRequest.builder()
        .loan(loan).borrower(borrower).organization(loan.getOrganization())
        .signingToken(token)
        .documentType(documentType != null ? documentType : "LOAN_AGREEMENT")
        .status(ESignatureRequest.SignatureStatus.OTP_SENT)
        .otpCodeHash(sha256(otp))
        .otpAttempts(0)
        .otpSentAt(LocalDateTime.now())
        .documentSnapshot(docText)
        .documentHash(sha256(docText))
        .consentText("By entering the code below and typing your full legal name, you agree this " +
            "constitutes your electronic signature on the loan agreement above, legally binding as " +
            "if signed by hand, in accordance with applicable electronic transactions law.")
        .createdBy(initiatedBy)
        .sentAt(LocalDateTime.now())
        .build();
    req = esignRepo.save(req);

    if (hasPhone) {
        smsService.sendCustom(borrower.getPhone(),
            String.format("%s: Sign your loan agreement here: %s Verification code: %s (valid 7 days). " +
                "Reply to your loan officer if you did not request this.", orgName(loan), signLink, otp));
    }

    if (hasEmail) {
        mailService.sendESignatureRequest(borrower, orgName(loan), signLink, otp);
    }

    auditService.log(loan.getOrganization(), null, "ESIGNATURE_INITIATED", "LOAN",
        String.valueOf(loanId), "E-signature request created for " + req.getDocumentType() + " by " + initiatedBy
            + (hasEmail && hasPhone ? " (link emailed + OTP texted)" : hasEmail ? " (OTP emailed, no phone on file)" : " (OTP texted, no email on file)"));

    return req;
}

@Transactional
public ESignatureRequest resendOtp(String token) {
    ESignatureRequest req = getActiveByToken(token);
    String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
    String signLink = frontendUrl + "/sign/" + token;
    req.setOtpCodeHash(sha256(otp));
    req.setOtpSentAt(LocalDateTime.now());
    req.setOtpAttempts(0);
    esignRepo.save(req);
    if (req.getBorrower().getPhone() != null && !req.getBorrower().getPhone().isBlank()) {
        smsService.sendCustom(req.getBorrower().getPhone(),
            String.format("%s: Sign here: %s New verification code: %s.", orgName(req.getLoan()), signLink, otp));
    }
    if (req.getBorrower().getEmail() != null && !req.getBorrower().getEmail().isBlank()) {
        mailService.sendESignatureRequest(req.getBorrower(), orgName(req.getLoan()), signLink, otp);
    }
    return req;
}

    @Transactional
    public ESignatureRequest verifyAndSign(String token, String otp, String typedFullName,
                                            String ipAddress, String userAgent) {
        ESignatureRequest req = getActiveByToken(token);

        if (req.getOtpAttempts() != null && req.getOtpAttempts() >= 5) {
            throw new RuntimeException("Too many incorrect attempts. Request a new code.");
        }
        if (typedFullName == null || typedFullName.trim().length() < 3) {
            throw new RuntimeException("Please type your full legal name to sign.");
        }
        if (!sha256(otp).equals(req.getOtpCodeHash())) {
            req.setOtpAttempts((req.getOtpAttempts() == null ? 0 : req.getOtpAttempts()) + 1);
            esignRepo.save(req);
            throw new RuntimeException("Incorrect verification code.");
        }

        req.setStatus(ESignatureRequest.SignatureStatus.SIGNED);
        req.setSignerFullNameTyped(typedFullName.trim());
        req.setSignerIpAddress(ipAddress);
        req.setSignerUserAgent(userAgent);
        req.setSignedAt(LocalDateTime.now());
        req = esignRepo.save(req);

        auditService.log(req.getOrganization(), null, "ESIGNATURE_SIGNED", "LOAN",
            String.valueOf(req.getLoan().getId()),
            "Loan agreement signed by " + typedFullName + " from IP " + ipAddress + " at " + req.getSignedAt());

        return req;
    }

    @Transactional
    public ESignatureRequest decline(String token, String reason) {
        ESignatureRequest req = getActiveByToken(token);
        req.setStatus(ESignatureRequest.SignatureStatus.DECLINED);
        req.setDeclinedAt(LocalDateTime.now());
        req.setDeclineReason(reason);
        req = esignRepo.save(req);
        auditService.log(req.getOrganization(), null, "ESIGNATURE_DECLINED", "LOAN",
            String.valueOf(req.getLoan().getId()), "Borrower declined to sign: " + reason);
        return req;
    }

    @Transactional(readOnly = true)
    public ESignatureRequest getByToken(String token) {
        return esignRepo.findBySigningToken(token).orElseThrow(() -> new RuntimeException("Signing link not found or expired"));
    }

    public List<ESignatureRequest> history(Long loanId) {
        return esignRepo.findByLoan_IdOrderByCreatedAtDesc(loanId);
    }

    private ESignatureRequest getActiveByToken(String token) {
        ESignatureRequest req = getByToken(token);
        if (req.getStatus() == ESignatureRequest.SignatureStatus.SIGNED)
            throw new RuntimeException("This document has already been signed.");
        if (req.getStatus() == ESignatureRequest.SignatureStatus.DECLINED)
            throw new RuntimeException("This signing request was declined.");
        if (req.isExpired()) {
            req.setStatus(ESignatureRequest.SignatureStatus.EXPIRED);
            esignRepo.save(req);
            throw new RuntimeException("This signing link has expired. Ask your loan officer to resend it.");
        }
        return req;
    }

    private String renderAgreement(Loan loan, Borrower b) {
        Organization org = loan.getOrganization();
        return String.format(Locale.US,
            "LOAN AGREEMENT%n" +
            "Lender: %s%n" +
            "Borrower: %s%n" +
            "Loan Reference: %s%n" +
            "Principal Amount: %s %,.2f%n" +
            "Interest Rate (annual): %.2f%%%n" +
            "Term: %d months, repaid %s%n" +
            "Processing Fee: %s %,.2f%n" +
            "Total Repayable: %s %,.2f%n" +
            "Purpose: %s%n%n" +
            "By signing below, the Borrower acknowledges receipt of the loan terms above, agrees to repay " +
            "in accordance with the repayment schedule provided, and consents to the Lender's terms and " +
            "conditions and privacy policy. Document generated %s.",
            org != null ? org.getName() : "Lender",
            b.getFullName(),
            loan.getReferenceNumber(),
            loan.getCurrency(), loan.getAmount(),
            loan.getInterestRate() != null ? loan.getInterestRate() : 0,
            loan.getDurationMonths() != null ? loan.getDurationMonths() : 0,
            loan.getRepaymentFrequency() != null ? loan.getRepaymentFrequency().toString() : "MONTHLY",
            loan.getCurrency(), loan.getProcessingFee() != null ? loan.getProcessingFee() : 0,
            loan.getCurrency(), loan.getTotalRepayable() != null ? loan.getTotalRepayable() : 0,
            loan.getPurpose() != null ? loan.getPurpose() : "General",
            LocalDateTime.now().format(FMT));
    }

    private String orgName(Loan l) { return l.getOrganization() != null ? l.getOrganization().getName() : "LoanSaaS"; }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte bb : hash) sb.append(String.format("%02x", bb));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}