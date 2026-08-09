package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Sends email via Brevo's HTTP API instead of raw SMTP.
 *
 * Render's free-tier services block outbound SMTP ports (25/465/587) entirely,
 * so this uses an HTTP API call (port 443, never blocked) instead.
 *
 * Uses Brevo (not SendGrid) specifically because it's a separate company from
 * Twilio — no shared login/account-linking conflicts if you already have a
 * Twilio account for SMS. Supports single-sender verification without owning
 * a domain: verify one email address you control, then send to any recipient.
 */
@Service
@Slf4j
public class MailService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:}")
    private String from;

    @Value("${app.mail.brevo-api-key:}")
    private String brevoApiKey;

    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";

    @Async
    public void sendApplicationReceived(Loan loan) {
        if (!mailEnabled) { log.info("[EMAIL] Application received: {}", loan.getReferenceNumber()); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to, "Loan Application Received",
            "<p>Dear " + loan.getBorrower().getFullName() + ",</p>" +
            "<p>We have successfully received your loan application.</p>" +
            "<p><strong>Reference Number:</strong> " + loan.getReferenceNumber() + "</p>" +
            "<p><strong>Current Status:</strong> Submitted</p>" +
            "<p>You can track your application from your borrower dashboard.</p>" +
            "<p>Thank you.<br/>Loan Management System</p>");
    }

    @Async
    public void sendLoanUpdateComment(Loan loan, String message) {
        if (!mailEnabled) { log.info("[EMAIL] Loan update comment: {}", loan.getReferenceNumber()); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to, "New Update on Your Application — " + loan.getReferenceNumber(),
            "<h2>New Message From Your Loan Officer</h2>" +
            "<p>There's a new update on your loan application <strong>" + loan.getReferenceNumber() + "</strong>:</p>" +
            "<blockquote style=\"border-left:3px solid #0D9488;margin:12px 0;padding:8px 16px;color:#374151;background:#f9fafb;\">" + message + "</blockquote>" +
            "<p>You can view the full details and respond from your application tracking page.</p>");
    }

    @Async
    public void sendLoginOtp(User user, String code) {
        if (!mailEnabled) { log.info("[EMAIL] Login OTP for {}: {}", user.getEmail(), code); return; }
        send(user.getEmail(), "Your sign-in code: " + code,
            "<h2>Sign-in Verification Code</h2>" +
            "<p>Use this code to finish signing in:</p>" +
            "<p style=\"font-size:28px;font-weight:bold;letter-spacing:6px;color:#0D6B3E;\">" + code + "</p>" +
            "<p>This code expires in 5 minutes. If you didn't try to sign in, you can ignore this email — your account is still safe, but consider changing your password if it happens again.</p>");
    }

    @Async
    public void sendLoanApproved(Loan loan) {
        if (!mailEnabled) { log.info("[EMAIL] Loan approved: {}", loan.getReferenceNumber()); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to, "Your Loan Has Been Approved — " + loan.getReferenceNumber(),
            "<h2>Congratulations!</h2>" +
            "<p>Your loan application <strong>" + loan.getReferenceNumber() + "</strong> has been approved.</p>" +
            "<p>Amount: <strong>" + loan.getCurrency() + " " + loan.getAmount() + "</strong></p>" +
            "<p>You will receive the funds shortly. Please review your repayment schedule.</p>");
    }

    @Async
    public void sendLoanRejected(Loan loan) {
        if (!mailEnabled) { log.info("[EMAIL] Loan rejected: {}", loan.getReferenceNumber()); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to, "Update on Your Loan Application — " + loan.getReferenceNumber(),
            "<h2>Application Update</h2>" +
            "<p>We regret to inform you that your loan application <strong>" + loan.getReferenceNumber() + "</strong> was not approved at this time.</p>" +
            "<p>Reason: " + (loan.getRejectionReason() != null ? loan.getRejectionReason() : "See your loan officer") + "</p>");
    }

    @Async
    public void sendPaymentConfirmation(Loan loan, Double amount) {
        if (!mailEnabled) { log.info("[EMAIL] Payment confirmed: {} -> {}", loan.getReferenceNumber(), amount); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to, "Payment Received — " + loan.getReferenceNumber(),
            "<h2>Payment Confirmed</h2>" +
            "<p>We have received your payment of <strong>" + loan.getCurrency() + " " + amount + "</strong> on loan <strong>" + loan.getReferenceNumber() + "</strong>.</p>" +
            "<p>Outstanding balance: <strong>" + loan.getCurrency() + " " + loan.getOutstandingBalance() + "</strong></p>");
    }

    @Async
    public void sendLoanDisbursed(Loan loan, String method) {
        if (!mailEnabled) { log.info("[EMAIL] Loan disbursed: {}", loan.getReferenceNumber()); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to, "Funds Disbursed — " + loan.getReferenceNumber(),
            "<h2>Funds Sent Successfully</h2>" +
            "<p>Your loan <strong>" + loan.getReferenceNumber() + "</strong> has been disbursed.</p>" +
            "<p>Amount: <strong>" + loan.getCurrency() + " " + loan.getDisbursedAmount() + "</strong> via " + method + "</p>" +
            "<p>Your first payment is due on <strong>" + loan.getNextDueDate() + "</strong>. You can track your loan from your borrower dashboard.</p>");
    }

    @Async
    public void sendPaymentDueReminder(Loan loan) {
        if (!mailEnabled) { log.info("[EMAIL] Payment reminder: {}", loan.getReferenceNumber()); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to, "Payment Due Reminder — " + loan.getReferenceNumber(),
            "<h2>Payment Reminder</h2>" +
            "<p>Your next payment on loan <strong>" + loan.getReferenceNumber() + "</strong> is due on <strong>" + loan.getNextDueDate() + "</strong>.</p>" +
            "<p>Please ensure your account has sufficient funds to avoid penalties.</p>");
    }

    @Async
    public void sendLoanRestructured(Loan loan, String reason) {
        if (!mailEnabled) { log.info("[EMAIL] Loan restructured: {}", loan.getReferenceNumber()); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to, "Your Loan Terms Have Changed — " + loan.getReferenceNumber(),
            "<h2>Loan Restructured</h2>" +
            "<p>The terms on your loan <strong>" + loan.getReferenceNumber() + "</strong> have been updated:</p>" +
            "<p>New term: <strong>" + loan.getDurationMonths() + " months</strong> at <strong>" + loan.getInterestRate() + "%</strong></p>" +
            (reason != null && !reason.isBlank() ? "<p>Reason: " + reason + "</p>" : "") +
            "<p>Your repayment schedule has been recalculated — please review it from your borrower dashboard.</p>");
    }

    @Async
    public void sendLoanWrittenOff(Loan loan, String reason) {
        if (!mailEnabled) { log.info("[EMAIL] Loan written off: {}", loan.getReferenceNumber()); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to, "Update on Your Loan — " + loan.getReferenceNumber(),
            "<h2>Account Status Update</h2>" +
            "<p>Your loan <strong>" + loan.getReferenceNumber() + "</strong> has been written off by " + org(loan) + ".</p>" +
            (reason != null && !reason.isBlank() ? "<p>Reason: " + reason + "</p>" : "") +
            "<p>Please contact us if you have questions about what this means for your account.</p>");
    }

    @Async
    public void sendMoratoriumGranted(Loan loan, int pauseMonths, String reason) {
        if (!mailEnabled) { log.info("[EMAIL] Moratorium granted: {}", loan.getReferenceNumber()); return; }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to, "Payment Pause Approved — " + loan.getReferenceNumber(),
            "<h2>Payment Moratorium Granted</h2>" +
            "<p>Your upcoming payments on loan <strong>" + loan.getReferenceNumber() + "</strong> have been paused for <strong>" + pauseMonths + " month(s)</strong>.</p>" +
            (reason != null && !reason.isBlank() ? "<p>Reason: " + reason + "</p>" : "") +
            "<p>Your next due date is now <strong>" + loan.getNextDueDate() + "</strong>. No action is needed from you during the pause.</p>");
    }

    private String org(Loan loan) {
        return loan.getOrganization() != null && loan.getOrganization().getName() != null ? loan.getOrganization().getName() : "our team";
    }

    @Async
    public void sendPasswordResetEmail(User user, String resetLink) {
        if (!mailEnabled) { log.info("[EMAIL] Password reset for {}: {}", user.getEmail(), resetLink); return; }
        send(user.getEmail(), "Reset Your LoanSaaS Pro Password",
            "<h2>Password Reset Request</h2>" +
            "<p>Hi " + (user.getName() != null ? user.getName() : "") + ",</p>" +
            "<p>Click the link below to reset your password. This link expires in 1 hour.</p>" +
            "<p><a href=\"" + resetLink + "\" style=\"background:#0D9488;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:bold;\">Reset Password</a></p>" +
            "<p>If you did not request a password reset, please ignore this email.</p>");
    }

    /** Sent alongside (never instead of) the SMS OTP — the email carries the clickable
     *  signing link; the OTP that actually completes the signature still only ever goes
     *  to the phone number on file, so this doesn't weaken the verification model. */
    @Async
    public void sendESignatureRequest(Borrower borrower, String orgName, String signLink) {
        if (!mailEnabled) {
            log.info("[EMAIL] E-signature link for {}: {}", borrower.getEmail(), signLink);
            return;
        }
        if (borrower.getEmail() == null || borrower.getEmail().isBlank()) return;
        send(borrower.getEmail(), "Your Loan Agreement Is Ready to Sign",
            "<h2>Loan Agreement Ready for Signature</h2>" +
            "<p>Dear " + borrower.getFullName() + ",</p>" +
            "<p>" + orgName + " has sent your loan agreement for e-signature. Click below to review and sign it.</p>" +
            "<p><a href=\"" + signLink + "\" style=\"background:#0D9488;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:bold;\">Review &amp; Sign</a></p>" +
            "<p>We've also texted a verification code to your phone — you'll need to enter it on that page to complete your signature.</p>" +
            "<p>This link expires in 7 days. If you weren't expecting this, please contact your loan officer.</p>");
    }

    /** Sent once, at account creation, when an admin adds a new staff member. Contains
     *  the system-generated temporary password in plain text — this is the one and only
     *  time it exists outside its bcrypt hash, so this email is the account's sole
     *  recovery path if the recipient doesn't act on it immediately. The account is
     *  flagged mustChangePassword=true, so whoever reads this can only use it once to
     *  log in and is then required to set a password only they know. */
    @Async
    public void sendNewUserCredentials(User user, String tempPassword, String loginLink) {
        if (!mailEnabled) {
            log.info("[EMAIL] New account for {}: temp password {}", user.getEmail(), tempPassword);
            return;
        }
        send(user.getEmail(), "Your LoanSaaS Pro Account Has Been Created",
            "<h2>Welcome to LoanSaaS Pro</h2>" +
            "<p>Hi " + (user.getName() != null ? user.getName() : "") + ",</p>" +
            "<p>An administrator" + (user.getOrganization() != null ? " at " + user.getOrganization().getName() : "") +
            " created a staff account for you" + (user.getRole() != null ? " with the role <strong>" + user.getRole().getName() + "</strong>" : "") + ".</p>" +
            "<p><strong>Email:</strong> " + user.getEmail() + "<br/>" +
            "<strong>Temporary password:</strong> <code style=\"background:#f3f4f6;padding:2px 8px;border-radius:4px;font-size:15px;\">" + tempPassword + "</code></p>" +
            "<p><a href=\"" + loginLink + "\" style=\"background:#0D9488;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:bold;\">Sign In</a></p>" +
            "<p>You'll be asked to set your own password the first time you sign in — the temporary one above will stop working once you do.</p>" +
            "<p>If you weren't expecting this account, please contact your administrator.</p>");
    }

    @Async
    public void sendBorrowerWelcome(Borrower borrower) {
        if (!mailEnabled) {
            log.info("[EMAIL] Borrower profile created: {}", borrower.getEmail());
            return;
        }
        if (borrower.getEmail() == null) return;
        send(borrower.getEmail(),
            "Your Profile Has Been Created",
            "<h2>Welcome</h2>" +
            "<p>Dear " + borrower.getFullName() + ",</p>" +
            "<p>A borrower profile has been created for you. Our team will reach out if we need any further information from you.</p>" +
            "<p>Thank you.<br/>Loan Management System</p>"
        );
    }

    // backend/loan-management-api/loan-backend/src/main/java/com/patrick/fintech/loan_backend/service/MailService.java

/** Sent alongside SMS when available, and as the primary channel when the borrower has
 *  no phone on file — the email carries both the clickable signing link and the OTP
 *  itself, so signing still works on deployments where the SMS gateway isn't configured. */
@Async
public void sendESignatureRequest(Borrower borrower, String orgName, String signLink, String otp) {
    if (!mailEnabled) {
        log.info("[EMAIL] E-signature link for {}: {} (OTP {})", borrower.getEmail(), signLink, otp);
        return;
    }
    if (borrower.getEmail() == null || borrower.getEmail().isBlank()) return;
    send(borrower.getEmail(), "Your Loan Agreement Is Ready to Sign",
        "<h2>Loan Agreement Ready for Signature</h2>" +
        "<p>Dear " + borrower.getFullName() + ",</p>" +
        "<p>" + orgName + " has sent your loan agreement for e-signature. Click below to review and sign it.</p>" +
        "<p><a href=\"" + signLink + "\" style=\"background:#0D9488;color:#fff;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:bold;\">Review &amp; Sign</a></p>" +
        "<p>Enter this verification code on that page to complete your signature (also sent by SMS, if we have a valid number on file):</p>" +
        "<p style=\"font-size:28px;font-weight:bold;letter-spacing:6px;color:#0D6B3E;\">" + otp + "</p>" +
        "<p>This link expires in 7 days. If you weren't expecting this, please contact your loan officer.</p>");
}

    @Async
    public void sendDocumentVerified(Borrower borrower, String documentType) {
        if (!mailEnabled) {
            log.info("[EMAIL] Document verified: {} for {}", documentType, borrower.getEmail());
            return;
        }
        if (borrower.getEmail() == null) return;
        send(borrower.getEmail(),
            "Document Verified: " + humanizeDocType(documentType),
            "<h2>Document Verified</h2>" +
            "<p>Dear " + borrower.getFullName() + ",</p>" +
            "<p>Your <strong>" + humanizeDocType(documentType) + "</strong> has been verified. No further action is needed for this document.</p>" +
            "<p>You can check your overall application status from your borrower dashboard.</p>"
        );
    }

    @Async
    public void sendDocumentRejected(Borrower borrower, String documentType, String reason) {
        if (!mailEnabled) {
            log.info("[EMAIL] Document rejected: {} for {}", documentType, borrower.getEmail());
            return;
        }
        if (borrower.getEmail() == null) return;
        send(borrower.getEmail(),
            "Action Needed: " + humanizeDocType(documentType) + " Rejected",
            "<h2>Document Rejected</h2>" +
            "<p>Dear " + borrower.getFullName() + ",</p>" +
            "<p>Your <strong>" + humanizeDocType(documentType) + "</strong> could not be accepted." +
            (reason != null && !reason.isBlank() ? " Reason: " + reason : "") + "</p>" +
            "<p>Please upload a corrected document from your application tracking page.</p>"
        );
    }

    @Async
    public void sendDocumentReplacementRequested(Borrower borrower, String documentType, String note) {
        if (!mailEnabled) {
            log.info("[EMAIL] Document replacement requested: {} for {}", documentType, borrower.getEmail());
            return;
        }
        if (borrower.getEmail() == null) return;
        send(borrower.getEmail(),
            "Please Re-upload: " + humanizeDocType(documentType),
            "<h2>Replacement Document Requested</h2>" +
            "<p>Dear " + borrower.getFullName() + ",</p>" +
            "<p>We need a new copy of your <strong>" + humanizeDocType(documentType) + "</strong>." +
            (note != null && !note.isBlank() ? " " + note : "") + "</p>" +
            "<p>Please upload it from your application tracking page as soon as possible to avoid delays.</p>"
        );
    }

    @Async
    public void sendOverdueReminder(Loan loan, Integer daysOverdue) {
        if (!mailEnabled) {
            log.info("[EMAIL] Overdue reminder: {} ({} days)", loan.getReferenceNumber(), daysOverdue);
            return;
        }
        String to = loan.getBorrower().getEmail();
        if (to == null) return;
        send(to,
            "URGENT: Payment Overdue Notice — " + loan.getReferenceNumber(),
            "<h2>Payment Overdue Alert</h2>" +
            "<p>Dear " + loan.getBorrower().getFullName() + ",</p>" +
            "<p>Your loan <strong>" + loan.getReferenceNumber() + "</strong> is currently marked as <strong>OVERDUE</strong> by <strong>" + daysOverdue + " days</strong>.</p>" +
            "<p>Please settle your outstanding balance immediately to avoid further penalization, legal escalation, or collection queue assignment.</p>"
        );
    }

    private String humanizeDocType(String type) {
        if (type == null) return "document";
        String[] parts = type.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    /** Sends via Brevo's HTTP API (port 443 — not blocked, unlike raw SMTP on Render). */
    private void send(String to, String subject, String html) {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            log.warn("Email send skipped — BREVO_API_KEY is not set. Would have sent to {}: {}", to, subject);
            return;
        }
        if (from == null || from.isBlank()) {
            log.warn("Email send skipped — MAIL_FROM is not set to your verified Brevo sender address. Would have sent to {}: {}", to, subject);
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            Map<String, Object> payload = Map.of(
                "sender", Map.of("email", from),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "htmlContent", html
            );

            restTemplate.exchange(BREVO_URL, HttpMethod.POST, new HttpEntity<>(payload, headers), String.class);
        } catch (Exception e) {
            log.warn("Email send failed to {}: {}", to, e.getMessage());
        }
    }
}
