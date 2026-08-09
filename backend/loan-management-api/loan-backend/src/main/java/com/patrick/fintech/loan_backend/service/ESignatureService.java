
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.ESignatureRequest;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.ESignatureRequestRepository;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ESignatureService {

    private final ESignatureRequestRepository esignRepo;
    private final LoanRepository loanRepo;
    private final SmsService smsService;
    private final MailService mailService;
    private final AuditService auditService;

    /**
     * Production frontend URL.
     *
     * Example:
     * app.frontend.url=https://fintech01-aydw.vercel.app
     */
    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * How long a signing request remains valid.
     *
     * Default: 7 days.
     */
    @Value("${app.esignature.expiry-days:7}")
    private long expiryDays;

    /**
     * Maximum incorrect OTP attempts.
     */
    @Value("${app.esignature.max-otp-attempts:5}")
    private int maxOtpAttempts;

    /**
     * Minimum interval between OTP resends.
     *
     * Default: 60 seconds.
     */
    @Value("${app.esignature.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds;

    /**
     * Maximum OTP resends for one signing request.
     *
     * Default: 5.
     *
     * This is an application-level limit. A proper production deployment
     * should also have IP/device/user rate limiting at the API gateway.
     */
    @Value("${app.esignature.max-resends:5}")
    private int maxResends;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final DateTimeFormatter DOCUMENT_DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "dd MMM yyyy HH:mm",
                    Locale.US
            );

    /**
     * ============================================================
     * INITIATE SIGNATURE REQUEST
     * ============================================================
     *
     * Creates:
     * - signing token
     * - OTP
     * - immutable document snapshot
     * - SHA-256 document hash
     * - SHA-256 OTP hash
     * - signing request
     *
     * The plaintext OTP is never stored in the database.
     */
    @Transactional
    public ESignatureRequest initiate(
            Long loanId,
            String documentType,
            String initiatedBy
    ) {

        if (loanId == null) {
            throw new IllegalArgumentException(
                    "Loan ID is required"
            );
        }

        Loan loan = loanRepo.findById(loanId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Loan not found: " + loanId
                        )
                );

        Borrower borrower = loan.getBorrower();

        if (borrower == null) {
            throw new IllegalStateException(
                    "Loan has no borrower on file"
            );
        }

        Organization organization = loan.getOrganization();

        if (organization == null || organization.getId() == null) {
            throw new IllegalStateException(
                    "Loan has no valid organization"
            );
        }

        boolean hasPhone =
                borrower.getPhone() != null
                        && !borrower.getPhone().isBlank();

        boolean hasEmail =
                borrower.getEmail() != null
                        && !borrower.getEmail().isBlank();

        if (!hasPhone && !hasEmail) {
            throw new IllegalStateException(
                    "Borrower has no phone number or email address "
                            + "on file to receive the signing OTP"
            );
        }

        /*
         * Prevent creation of multiple simultaneously active signing
         * requests for the same loan.
         *
         * We use history() rather than requiring a new repository method,
         * keeping this service compatible with the current repository.
         */
        List<ESignatureRequest> existingRequests =
                esignRepo.findByLoan_IdOrderByCreatedAtDesc(loanId);

        for (ESignatureRequest existing : existingRequests) {

            if (existing == null || existing.getStatus() == null) {
                continue;
            }

            if (existing.getStatus()
                    != ESignatureRequest.SignatureStatus.SIGNED
                    && existing.getStatus()
                    != ESignatureRequest.SignatureStatus.DECLINED
                    && existing.getStatus()
                    != ESignatureRequest.SignatureStatus.EXPIRED) {

                /*
                 * Do not silently create competing signing links.
                 *
                 * The officer should resend the existing request instead.
                 */
                throw new IllegalStateException(
                        "An active e-signature request already exists "
                                + "for this loan. Resend the existing OTP "
                                + "instead of creating another signing request."
                );
            }
        }

        LocalDateTime now = LocalDateTime.now();

        /*
         * UUID token is long and cryptographically unpredictable.
         *
         * The database currently stores the token itself because the
         * existing repository exposes findBySigningToken().
         */
        String token =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "");

        String otp =
                generateOtp();

        /*
         * IMPORTANT:
         *
         * Generate the document snapshot once and never regenerate it
         * during the signing process.
         *
         * The exact snapshot shown to the borrower is hashed and persisted.
         */
        String documentTypeValue =
                clean(documentType) != null
                        ? clean(documentType)
                        : "LOAN_AGREEMENT";

        String documentSnapshot =
                renderAgreement(
                        loan,
                        borrower,
                        now
                );

        String documentHash =
                sha256(documentSnapshot);

        String signLink =
                buildSigningLink(token);

        ESignatureRequest request =
                ESignatureRequest.builder()
                        .loan(loan)
                        .borrower(borrower)
                        .organization(organization)

                        .signingToken(token)

                        .documentType(
                                documentTypeValue
                        )

                        .status(
                                ESignatureRequest.SignatureStatus.OTP_SENT
                        )

                        .otpCodeHash(
                                sha256(otp)
                        )

                        .otpAttempts(0)

                        .otpSentAt(now)

                        .documentSnapshot(
                                documentSnapshot
                        )

                        .documentHash(
                                documentHash
                        )

                        .consentText(
                                "By entering the verification code and "
                                        + "typing your full legal name, you "
                                        + "confirm that you have reviewed "
                                        + "the loan agreement presented to "
                                        + "you and agree that your electronic "
                                        + "signature constitutes your "
                                        + "acceptance of the agreement, "
                                        + "subject to applicable law."
                        )

                        .createdBy(
                                clean(initiatedBy)
                        )

                        .sentAt(now)

                        .build();

        request =
                esignRepo.save(request);

        /*
         * Send notifications after persistence.
         *
         * The signing request exists before the external notification
         * providers are called, so a provider failure does not mean the
         * request itself never existed.
         */
        sendSigningNotifications(
                request,
                borrower,
                loan,
                signLink,
                otp
        );

        /*
         * Never put the OTP, full borrower name, email, phone number,
         * document contents or signing token into audit logs.
         */
        auditService.log(
                organization,
                null,
                "ESIGNATURE_INITIATED",
                "LOAN",
                String.valueOf(loanId),
                "E-signature request created for document type "
                        + documentTypeValue
                        + " by "
                        + safeActor(initiatedBy),
                null,
                null,
                "E-Signature"
        );

        log.info(
                "E-signature request {} initiated for loan {} "
                        + "by actor {}",
                safeRequestId(request),
                loanId,
                safeActor(initiatedBy)
        );

        return request;
    }

    /**
     * ============================================================
     * RESEND OTP
     * ============================================================
     */
    @Transactional
    public ESignatureRequest resendOtp(
            String token
    ) {

        ESignatureRequest request =
                getActiveByToken(token);

        LocalDateTime now =
                LocalDateTime.now();

        /*
         * Prevent OTP bombing.
         */
        if (request.getOtpSentAt() != null) {

            Duration elapsed =
                    Duration.between(
                            request.getOtpSentAt(),
                            now
                    );

            if (elapsed.getSeconds()
                    < resendCooldownSeconds) {

                long remaining =
                        resendCooldownSeconds
                                - elapsed.getSeconds();

                throw new IllegalStateException(
                        "Please wait "
                                + Math.max(1, remaining)
                                + " seconds before requesting "
                                + "another verification code."
                );
            }
        }

        /*
         * We use otpAttempts as the existing persisted counter for
         * verification attempts only.
         *
         * Do not silently reuse it as a resend counter because doing so
         * could make the security semantics confusing.
         *
         * A proper production schema should eventually add:
         * otpResendCount.
         *
         * For the current model we rely on cooldown + API rate limiting.
         */

        String otp =
                generateOtp();

        request.setOtpCodeHash(
                sha256(otp)
        );

        request.setOtpSentAt(now);

        request.setOtpAttempts(0);

        request =
                esignRepo.save(request);

        String signLink =
                buildSigningLink(
                        request.getSigningToken()
                );

        Borrower borrower =
                request.getBorrower();

        Loan loan =
                request.getLoan();

        if (borrower == null || loan == null) {
            throw new IllegalStateException(
                    "Signing request is missing borrower or loan information"
            );
        }

        sendSigningNotifications(
                request,
                borrower,
                loan,
                signLink,
                otp
        );

        log.info(
                "E-signature OTP resent for request {} and loan {}",
                safeRequestId(request),
                loan.getId()
        );

        return request;
    }

    /**
     * ============================================================
     * VERIFY OTP AND SIGN
     * ============================================================
     */
    @Transactional
    public ESignatureRequest verifyAndSign(
            String token,
            String otp,
            String typedFullName,
            String ipAddress,
            String userAgent
    ) {

        ESignatureRequest request =
                getActiveByToken(token);

        /*
         * Validate OTP input before hashing.
         */
        String suppliedOtp =
                clean(otp);

        if (suppliedOtp == null
                || !suppliedOtp.matches("\\d{6}")) {

            incrementOtpAttempt(request);

            throw new IllegalArgumentException(
                    "Verification code must contain exactly 6 digits."
            );
        }

        /*
         * Maximum incorrect attempts.
         */
        int attempts =
                request.getOtpAttempts() == null
                        ? 0
                        : request.getOtpAttempts();

        if (attempts >= maxOtpAttempts) {

            throw new IllegalStateException(
                    "Too many incorrect verification attempts. "
                            + "Request a new verification code."
            );
        }

        /*
         * Legal name validation.
         */
        String signerName =
                clean(typedFullName);

        if (signerName == null
                || signerName.length() < 3) {

            throw new IllegalArgumentException(
                    "Please type your full legal name to sign."
            );
        }

        /*
         * Basic name length protection.
         */
        if (signerName.length() > 200) {

            throw new IllegalArgumentException(
                    "Signer name is too long."
            );
        }

        /*
         * Constant-time comparison prevents timing attacks.
         */
        String suppliedHash =
                sha256(suppliedOtp);

        if (!secureEquals(
                suppliedHash,
                request.getOtpCodeHash()
        )) {

            incrementOtpAttempt(request);

            throw new IllegalArgumentException(
                    "Incorrect verification code."
            );
        }

        LocalDateTime now =
                LocalDateTime.now();

        /*
         * Signing is now complete.
         */
        request.setStatus(
                ESignatureRequest.SignatureStatus.SIGNED
        );

        request.setSignerFullNameTyped(
                signerName
        );

        /*
         * Do not trust arbitrary whitespace or giant headers.
         */
        request.setSignerIpAddress(
                sanitizeIpAddress(ipAddress)
        );

        request.setSignerUserAgent(
                sanitizeUserAgent(userAgent)
        );

        request.setSignedAt(
                now
        );

        /*
         * OTP must not remain usable after successful signing.
         */
        request.setOtpCodeHash(null);

        request.setOtpAttempts(
                maxOtpAttempts
        );

        request =
                esignRepo.save(request);

        /*
         * Do NOT log the complete IP, user agent, name, OTP or token
         * in application logs.
         *
         * The database still contains the evidentiary fields.
         */
        auditService.log(
                request.getOrganization(),
                null,
                "ESIGNATURE_SIGNED",
                "LOAN",
                String.valueOf(
                        request.getLoan().getId()
                ),
                "Loan agreement successfully signed electronically.",
                null,
                null,
                "E-Signature"
        );

        log.info(
                "Loan {} e-signature completed for request {}",
                request.getLoan().getId(),
                safeRequestId(request)
        );

        return request;
    }

    /**
     * ============================================================
     * DECLINE SIGNATURE
     * ============================================================
     */
    @Transactional
    public ESignatureRequest decline(
            String token,
            String reason
    ) {

        ESignatureRequest request =
                getActiveByToken(token);

        String cleanReason =
                clean(reason);

        if (cleanReason == null) {
            cleanReason =
                    "Borrower declined to sign.";
        }

        if (cleanReason.length() > 1000) {
            cleanReason =
                    cleanReason.substring(0, 1000);
        }

        request.setStatus(
                ESignatureRequest.SignatureStatus.DECLINED
        );

        request.setDeclinedAt(
                LocalDateTime.now()
        );

        request.setDeclineReason(
                cleanReason
        );

        /*
         * Invalidate OTP after decline.
         */
        request.setOtpCodeHash(null);

        request =
                esignRepo.save(request);

        auditService.log(
                request.getOrganization(),
                null,
                "ESIGNATURE_DECLINED",
                "LOAN",
                String.valueOf(
                        request.getLoan().getId()
                ),
                "Borrower declined the electronic signature request.",
                null,
                null,
                "E-Signature"
        );

        log.info(
                "Loan {} e-signature request {} declined",
                request.getLoan().getId(),
                safeRequestId(request)
        );

        return request;
    }

    /**
     * ============================================================
     * GET REQUEST BY TOKEN
     * ============================================================
     *
     * This method should normally be used only for the signing page.
     *
     * Do not expose the JPA entity directly from a public controller
     * in production because it can expose relationships and internal
     * fields.
     */
    @Transactional(readOnly = true)
    public ESignatureRequest getByToken(
            String token
    ) {

        String cleanToken =
                clean(token);

        if (cleanToken == null) {

            throw new IllegalArgumentException(
                    "Signing token is required."
            );
        }

        ESignatureRequest request =
                esignRepo.findBySigningToken(
                        cleanToken
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Signing link not found."
                        )
                );

        return request;
    }

    /**
     * ============================================================
     * HISTORY
     * ============================================================
     *
     * IMPORTANT:
     * The controller must authorize the requesting officer/admin
     * before calling this method.
     */
    @Transactional(readOnly = true)
    public List<ESignatureRequest> history(
            Long loanId
    ) {

        if (loanId == null) {
            throw new IllegalArgumentException(
                    "Loan ID is required."
            );
        }

        /*
         * Ensure the loan actually exists.
         *
         * This prevents returning an empty list for a completely
         * invalid loan ID and makes authorization easier at the
         * service/controller layer.
         */
        loanRepo.findById(loanId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Loan not found: " + loanId
                        )
                );

        return esignRepo
                .findByLoan_IdOrderByCreatedAtDesc(
                        loanId
                );
    }

    /**
     * ============================================================
     * GET ACTIVE REQUEST
     * ============================================================
     */
    private ESignatureRequest getActiveByToken(
            String token
    ) {

        ESignatureRequest request =
                getByToken(token);

        if (request.getStatus()
                == ESignatureRequest.SignatureStatus.SIGNED) {

            throw new IllegalStateException(
                    "This document has already been signed."
            );
        }

        if (request.getStatus()
                == ESignatureRequest.SignatureStatus.DECLINED) {

            throw new IllegalStateException(
                    "This signing request was declined."
            );
        }

        if (request.getStatus()
                == ESignatureRequest.SignatureStatus.EXPIRED) {

            throw new IllegalStateException(
                    "This signing link has expired. "
                            + "Ask your loan officer to create "
                            + "a new signing request."
            );
        }

        if (request.isExpired()) {

            /*
             * Persist expired state.
             */
            request.setStatus(
                    ESignatureRequest.SignatureStatus.EXPIRED
            );

            request.setOtpCodeHash(null);

            esignRepo.save(request);

            throw new IllegalStateException(
                    "This signing link has expired. "
                            + "Ask your loan officer to create "
                            + "a new signing request."
            );
        }

        if (request.getStatus()
                != ESignatureRequest.SignatureStatus.OTP_SENT) {

            throw new IllegalStateException(
                    "This signing request is not available for signing."
            );
        }

        return request;
    }

    /**
     * ============================================================
     * SEND SMS + EMAIL
     * ============================================================
     */
    private void sendSigningNotifications(
            ESignatureRequest request,
            Borrower borrower,
            Loan loan,
            String signLink,
            String otp
    ) {

        boolean hasPhone =
                borrower.getPhone() != null
                        && !borrower.getPhone().isBlank();

        boolean hasEmail =
                borrower.getEmail() != null
                        && !borrower.getEmail().isBlank();

        /*
         * At least one channel was validated before initiation.
         */
        if (!hasPhone && !hasEmail) {
            throw new IllegalStateException(
                    "Borrower has no notification channel."
            );
        }

        String organizationName =
                orgName(loan);

        /*
         * SMS.
         */
        if (hasPhone) {

            try {

                String message =
                        String.format(
                                Locale.US,
                                "%s: Sign your loan agreement here: %s "
                                        + "Verification code: %s. "
                                        + "Code expires with the signing "
                                        + "request. If you did not request "
                                        + "this, contact your loan officer.",
                                organizationName,
                                signLink,
                                otp
                        );

                smsService.sendCustom(
                        borrower.getPhone(),
                        message
                );

            } catch (Exception ex) {

                log.error(
                        "Unable to send e-signature SMS for request {} "
                                + "and loan {}",
                        safeRequestId(request),
                        loan.getId(),
                        ex
                );

                /*
                 * We deliberately do not include the OTP, phone number
                 * or signing token in the log.
                 */
            }
        }

        /*
         * Email.
         */
        if (hasEmail) {

            try {

                mailService.sendESignatureRequest(
                        borrower,
                        organizationName,
                        signLink,
                        otp
                );

            } catch (Exception ex) {

                log.error(
                        "Unable to send e-signature email for request {} "
                                + "and loan {}",
                        safeRequestId(request),
                        loan.getId(),
                        ex
                );
            }
        }
    }

    /**
     * ============================================================
     * RENDER IMMUTABLE AGREEMENT SNAPSHOT
     * ============================================================
     *
     * The timestamp is passed into this method so the document hash
     * does not change merely because the agreement is rendered again.
     */
    private String renderAgreement(
            Loan loan,
            Borrower borrower,
            LocalDateTime generatedAt
    ) {

        Organization organization =
                loan.getOrganization();

        String organizationName =
                organization != null
                        && organization.getName() != null
                        ? organization.getName()
                        : "Lender";

        String borrowerName =
                borrower.getFullName() != null
                        ? borrower.getFullName()
                        : buildBorrowerName(borrower);

        String currency =
                loan.getCurrency() != null
                        ? loan.getCurrency()
                        : "";

        double amount =
                loan.getAmount() != null
                        ? loan.getAmount()
                        : 0.0;

        double interestRate =
                loan.getInterestRate() != null
                        ? loan.getInterestRate()
                        : 0.0;

        int durationMonths =
                loan.getDurationMonths() != null
                        ? loan.getDurationMonths()
                        : 0;

        String frequency =
                loan.getRepaymentFrequency() != null
                        ? loan.getRepaymentFrequency().toString()
                        : "MONTHLY";

        double processingFee =
                loan.getProcessingFee() != null
                        ? loan.getProcessingFee()
                        : 0.0;

        double totalRepayable =
                loan.getTotalRepayable() != null
                        ? loan.getTotalRepayable()
                        : 0.0;

        String purpose =
                loan.getPurpose() != null
                        ? loan.getPurpose()
                        : "General";

        String reference =
                loan.getReferenceNumber() != null
                        ? loan.getReferenceNumber()
                        : "N/A";

        return String.format(
                Locale.US,

                "LOAN AGREEMENT%n"
                        + "Lender: %s%n"
                        + "Borrower: %s%n"
                        + "Loan Reference: %s%n"
                        + "Principal Amount: %s %,.2f%n"
                        + "Interest Rate (annual): %.2f%%%n"
                        + "Term: %d months, repaid %s%n"
                        + "Processing Fee: %s %,.2f%n"
                        + "Total Repayable: %s %,.2f%n"
                        + "Purpose: %s%n%n"
                        + "By signing below, the Borrower acknowledges "
                        + "receipt of the loan terms above, agrees to "
                        + "repay the loan in accordance with the repayment "
                        + "schedule provided, and consents to the Lender's "
                        + "terms and conditions and applicable privacy "
                        + "requirements.%n%n"
                        + "Document generated: %s%n",

                organizationName,
                borrowerName,
                reference,
                currency,
                amount,
                interestRate,
                durationMonths,
                frequency,
                currency,
                processingFee,
                currency,
                totalRepayable,
                purpose,
                generatedAt.format(
                        DOCUMENT_DATE_FORMAT
                )
        );
    }

    /**
     * ============================================================
     * OTP GENERATOR
     * ============================================================
     */
    private String generateOtp() {

        return String.format(
                Locale.US,
                "%06d",
                RANDOM.nextInt(1_000_000)
        );
    }

    /**
     * ============================================================
     * SIGNING LINK
     * ============================================================
     */
    private String buildSigningLink(
            String token
    ) {

        String base =
                frontendUrl != null
                        ? frontendUrl.trim()
                        : "";

        while (base.endsWith("/")) {

            base =
                    base.substring(
                            0,
                            base.length() - 1
                    );
        }

        if (base.isBlank()) {

            throw new IllegalStateException(
                    "Frontend URL is not configured."
            );
        }

        return base
                + "/sign/"
                + token;
    }

    /**
     * ============================================================
     * OTP ATTEMPT
     * ============================================================
     */
    private void incrementOtpAttempt(
            ESignatureRequest request
    ) {

        int attempts =
                request.getOtpAttempts() == null
                        ? 0
                        : request.getOtpAttempts();

        attempts++;

        request.setOtpAttempts(
                attempts
        );

        esignRepo.save(request);
    }

    /**
     * ============================================================
     * CONSTANT-TIME STRING COMPARISON
     * ============================================================
     */
    private boolean secureEquals(
            String expected,
            String supplied
    ) {

        if (expected == null
                || supplied == null) {

            return false;
        }

        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * ============================================================
     * SHA-256
     * ============================================================
     */
    private String sha256(
            String value
    ) {

        if (value == null) {
            return null;
        }

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            value.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            StringBuilder result =
                    new StringBuilder(
                            hash.length * 2
                    );

            for (byte b : hash) {

                result.append(
                        String.format(
                                Locale.ROOT,
                                "%02x",
                                b & 0xff
                        )
                );
            }

            return result.toString();

        } catch (Exception ex) {

            throw new IllegalStateException(
                    "Unable to calculate SHA-256 hash.",
                    ex
            );
        }
    }

    /**
     * ============================================================
     * BORROWER NAME
     * ============================================================
     */
    private String buildBorrowerName(
            Borrower borrower
    ) {

        String first =
                borrower.getFirstName() != null
                        ? borrower.getFirstName().trim()
                        : "";

        String last =
                borrower.getLastName() != null
                        ? borrower.getLastName().trim()
                        : "";

        return (
                first
                        + " "
                        + last
        ).trim();
    }

    /**
     * ============================================================
     * ORGANIZATION NAME
     * ============================================================
     */
    private String orgName(
            Loan loan
    ) {

        if (loan == null
                || loan.getOrganization() == null
                || loan.getOrganization().getName() == null
                || loan.getOrganization().getName().isBlank()) {

            return "LoanSaaS";
        }

        return loan
                .getOrganization()
                .getName()
                .trim();
    }

    /**
     * ============================================================
     * STRING CLEANING
     * ============================================================
     */
    private String clean(
            String value
    ) {

        if (value == null) {
            return null;
        }

        String cleaned =
                value.trim();

        return cleaned.isEmpty()
                ? null
                : cleaned;
    }

    /**
     * ============================================================
     * IP ADDRESS SANITIZATION
     * ============================================================
     */
    private String sanitizeIpAddress(
            String ipAddress
    ) {

        String value =
                clean(ipAddress);

        if (value == null) {
            return null;
        }

        /*
         * Prevent database/log abuse through giant headers.
         */
        if (value.length() > 100) {
            return value.substring(0, 100);
        }

        return value;
    }

    /**
     * ============================================================
     * USER AGENT SANITIZATION
     * ============================================================
     */
    private String sanitizeUserAgent(
            String userAgent
    ) {

        String value =
                clean(userAgent);

        if (value == null) {
            return null;
        }

        /*
         * Browser User-Agent strings can be large.
         */
        if (value.length() > 1000) {
            return value.substring(0, 1000);
        }

        return value;
    }

    /**
     * ============================================================
     * SAFE LOG REQUEST ID
     * ============================================================
     */
    private String safeRequestId(
            ESignatureRequest request
    ) {

        if (request == null
                || request.getId() == null) {

            return "NEW";
        }

        return String.valueOf(
                request.getId()
        );
    }

    /**
     * ============================================================
     * SAFE ACTOR
     * ============================================================
     */
    private String safeActor(
            String actor
    ) {

        String value =
                clean(actor);

        if (value == null) {
            return "SYSTEM";
        }

        if (value.length() > 100) {
            return value.substring(0, 100);
        }

        return value;
    }
}
