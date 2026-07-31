package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.LoanRequest;
import com.patrick.fintech.loan_backend.dto.publicportal.BorrowerDashboardResponse;
import com.patrick.fintech.loan_backend.dto.publicportal.DashboardSummaryResponse;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import com.patrick.fintech.loan_backend.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Public-facing API — NO authentication required.
 * Serves this bank's own branding config and accepts online loan applications
 * from the public website. Single-tenant: every endpoint here resolves to this
 * deployment's one organization, not a directory of tenants.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Slf4j
public class PublicController {

    private final OrganizationRepository orgRepo;
    private final BorrowerRepository     borrowerRepo;
    private final UserRepository         userRepo;
    private final LoanRepository         loanRepo;
    private final LoanService            loanService;
    private final BorrowerFileService    fileService;
    private final SmsService             smsService;
    private final NotificationService    notificationService;
    private final MailService            mailService;
    private final AuditService           auditService;
    private final ObjectMapper           objectMapper;
    private final LoanProductRepository  loanProductRepo;
    private final IdempotencyService     idempotencyService;
    private final com.patrick.fintech.loan_backend.repository.LoanCommentRepository loanCommentRepo;
    private final com.patrick.fintech.loan_backend.repository.ContactMessageRepository contactMessageRepo;
    private final FlutterwaveService     flutterwaveService;
    private final PaymentService         paymentService;
    private final PaymentRepository      paymentRepo;
    private final ReportExportService    exportService;

    /**
     * Public "Contact Us" form submission — notifies the org's staff in-app
     * so it doesn't disappear into the void (previously the frontend form was decorative only).
     */
    @PostMapping("/contact")
    public ResponseEntity<ApiResponse<String>> submitContact(@RequestBody Map<String,Object> body) {
        String slug = str(body.get("tenantSlug"));
        Organization org = resolveOrg(slug);
        if (org == null) throw new RuntimeException("We couldn't identify this lender. Please refresh and try again.");

        String name    = str(body.get("name"));
        String subject = str(body.get("subject"));
        String message = str(body.get("message"));
        String email    = str(body.get("email"));
        String phone    = str(body.get("phone"));
        if (name == null || message == null) throw new RuntimeException("Name and message are required");

        contactMessageRepo.save(ContactMessage.builder()
            .organization(org).name(name).email(email).phone(phone).subject(subject).message(message).build());

        List<User> staff = userRepo.findByOrganization(org).stream()
            .filter(u -> u.getRole() != null && Set.of("ADMIN", "MANAGER").contains(u.getRole().getName()))
            .toList();
        notificationService.notifyUsers(staff,
            "New Contact Message: " + (subject != null ? subject : "General Inquiry"),
            name + (phone != null ? " (" + phone + ")" : "") + (email != null ? " <" + email + ">" : "") + ": " + message,
            "info", "/dashboard/messages");

        auditService.log(org, null, "PUBLIC_CONTACT_MESSAGE", "ORGANIZATION", org.getId().toString(),
            "Contact form submitted by " + name);

        return ResponseEntity.ok(ApiResponse.ok("Message received — we'll get back to you within 24 hours"));
    }


    @GetMapping("/borrower/dashboard")
public ResponseEntity<BorrowerDashboardResponse> borrowerDashboard(

        @RequestParam String reference,

        @RequestParam String phone) {

    return ResponseEntity.ok(
            loanService.getBorrowerDashboard(
                    reference,
                    phone));
}

@GetMapping("/borrower/summary")
public ResponseEntity<DashboardSummaryResponse> borrowerSummary(

        @RequestParam String phone) {

    return ResponseEntity.ok(

            loanService.getBorrowerSummary(phone));
}
    /**
     * Lets an applicant check their loan application status from the public
     * website using the reference number they were given at submission, plus
     * the phone number on the application as a lightweight ownership check
     * (this endpoint has no auth — the phone match keeps a reference number
     * alone, which is guessable, from exposing anyone else's application).
     */
    @GetMapping("/applications/{reference}/status")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String,Object>>> trackApplication(
            @PathVariable String reference,
            @RequestParam String phone) {
        Loan loan = verifyOwnership(reference, phone);

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("reference",     loan.getReferenceNumber());
        result.put("status",        loan.getStatus().name());
        result.put("statusLabel",   statusLabel(loan.getStatus()));
        result.put("statusSteps",   statusSteps(loan.getStatus()));
        result.put("progressSteps", progressSteps(loan));
        result.put("timeline",      timeline(loan));
        result.put("loanType",      loan.getLoanType());
        result.put("amount",        loan.getAmount());
        result.put("principal",     loan.getAmount());
        result.put("currency",      loan.getCurrency());
        result.put("submittedDate", loan.getCreatedAt());
        result.put("updatedDate",   loan.getUpdatedAt());
        result.put("rejectionReason", loan.getStatus() == LoanStatus.REJECTED ? loan.getRejectionReason() : null);
        result.put("maritalStatus", loan.getBorrower().getMaritalStatus());
        result.put("documentsRequired", loan.getBorrower() != null
            ? loanService.getDocumentRequirements(loan.getId(), loan.getOrganization().getId())
            : null);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * KYC document upload for an applicant who already has a reference number —
     * used both right after submitting an application and later from the
     * tracking page if something was missed. Same phone-match ownership check
     * as tracking; no session/login involved.
     */
   @PostMapping("/applications/{reference}/documents")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String,Object>>> uploadApplicationDocument(
            @PathVariable String reference,
            @RequestParam String phone,
            @RequestParam String documentType,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws Exception {
        Loan loan = verifyOwnership(reference, phone);

        DocumentType docType;
        try {
            docType = DocumentType.valueOf(documentType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unknown document type.");
        }

        var saved = fileService.upload(loan.getBorrower().getId(), file, docType, true);
        auditService.log(loan.getBorrower().getOrganization(), null, "APPLICANT_DOCUMENT_UPLOADED",
            "BORROWER_FILE", saved.getId().toString(),
            docType + " uploaded by applicant for application " + loan.getReferenceNumber());

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("id", saved.getId());
        result.put("documentType", saved.getDocumentType());
        result.put("fileName", saved.getFileName());
        result.put("fileSize", saved.getFileSize());
        return ResponseEntity.ok(ApiResponse.ok("Document uploaded", result));
    }

    /** Checklist view for the upload UI — what's been uploaded so far, without exposing file bytes. */
    @GetMapping("/applications/{reference}/documents")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Map<String,Object>>>> listApplicationDocuments(
            @PathVariable String reference,
            @RequestParam String phone) {
        Loan loan = verifyOwnership(reference, phone);
        List<Map<String,Object>> docs = fileService.getByBorrowerMetadataOnly(loan.getBorrower().getId())
            .stream()
            .map(f -> {
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("id", f.getId());
                m.put("documentType", f.getDocumentType());
                m.put("fileName", f.getFileName());
                m.put("fileSize", f.getFileSize());
                m.put("uploadedAt", f.getUploadedAt());
                m.put("verificationStatus", f.getVerificationStatus());
                return m;
            })
            .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(docs));
    }

    /**
     * Lets an applicant remove a document they uploaded by mistake (wrong file, wrong page,
     * blurry photo) and re-upload a corrected one. Restricted to files:
     *   - actually uploaded by an applicant (not something staff attached themselves), and
     *   - belonging to THIS borrower (ownership re-checked here, not just at the loan level —
     *     a file ID alone must never be enough to delete someone else's document), and
     *   - not yet VERIFIED — once a loan officer has signed off on a document, an applicant
     *     can no longer make it disappear out from under that review. Documents that are
     *     PENDING_VERIFICATION, REJECTED, or REPLACEMENT_REQUESTED can still be removed.
     */
    @DeleteMapping("/applications/{reference}/documents/{fileId}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteApplicationDocument(
            @PathVariable String reference,
            @PathVariable Long fileId,
            @RequestParam String phone) {
        Loan loan = verifyOwnership(reference, phone);
        var file = fileService.getById(fileId);

        if (file.getBorrower() == null || !file.getBorrower().getId().equals(loan.getBorrower().getId()))
            throw new RuntimeException("Document not found.");
        if (!file.isUploadedByApplicant())
            throw new RuntimeException("This document was added by our staff and can't be removed here — contact us if it needs changing.");
        if (file.getVerificationStatus() == VerificationStatus.VERIFIED)
            throw new RuntimeException("This document has already been verified and can no longer be removed. Contact us if it needs to be replaced.");

        DocumentType documentType = file.getDocumentType();
        String fileName = file.getFileName();
        fileService.delete(fileId);

        auditService.log(loan.getBorrower().getOrganization(), null, "APPLICANT_DOCUMENT_DELETED",
            "BORROWER_FILE", fileId.toString(),
            documentType + " (" + fileName + ") removed by applicant for application " + loan.getReferenceNumber());

        return ResponseEntity.ok(ApiResponse.ok("Document removed", null));
    }

    /** Applicant-visible messages from staff — e.g. "please upload your land title document". */
    @GetMapping("/applications/{reference}/comments")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Map<String,Object>>>> getApplicationComments(
            @PathVariable String reference,
            @RequestParam String phone) {
        Loan loan = verifyOwnership(reference, phone);
        List<Map<String,Object>> comments = loanCommentRepo.findVisibleToApplicantByLoanId(loan.getId())
            .stream()
            .map(c -> {
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("message", c.getMessage());
                m.put("createdAt", c.getCreatedAt());
                String roleLabel = (c.getAuthor() != null && c.getAuthor().getRole() != null)
                    ? humanizeRole(c.getAuthor().getRole().getName())
                    : loan.getOrganization().getName() + " Team";
                m.put("from", roleLabel);
                return m;
            })
            .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(comments));
    }

    /** "LOAN_OFFICER" -> "Loan Officer" — role shown to applicants instead of staff names. */
    private String humanizeRole(String roleName) {
        if (roleName == null || roleName.isBlank()) return "Our Team";
        String[] parts = roleName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        return sb.toString().trim();
    }

    /**
     * Lets a borrower pay their loan themselves from the tracking page — card, mobile
     * money, or bank transfer, via the same Flutterwave integration staff use internally
     * (see PaymentGatewayController). Same phone-match ownership check as the rest of
     * this controller; no session/login involved.
     *
     * If "amount" isn't supplied, defaults to the next installment due, falling back to
     * the full outstanding balance if there's no scheduled installment on record.
     *
     * Card/bank-transfer/most mobile-money charges are asynchronous — Flutterwave confirms
     * them later via webhook (PaymentWebhookController), not in this response.
     */
    @PostMapping("/applications/{reference}/payments/initiate")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String,Object>>> initiatePublicPayment(
            @PathVariable String reference,
            @RequestParam String phone,
            @RequestBody Map<String,Object> body) {
        Loan loan = verifyOwnership(reference, phone);

        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.OVERDUE) {
            throw new RuntimeException("This loan isn't currently accepting payments (status: " + loan.getStatus() + ").");
        }

        String method = str(body.get("paymentMethod"));
        if (method == null) throw new RuntimeException("Payment method is required.");

        Double requestedAmount = num(body.get("amount"));
        double dueAmount;
        if (requestedAmount != null && requestedAmount > 0) {
            dueAmount = requestedAmount;
        } else if (loan.getNextInstallmentAmount() != null && loan.getNextInstallmentAmount() > 0) {
            dueAmount = loan.getNextInstallmentAmount();
        } else {
            dueAmount = loan.getOutstandingBalance() != null ? loan.getOutstandingBalance() : 0;
        }
        if (dueAmount <= 0) throw new RuntimeException("There's nothing due on this loan right now.");

        com.patrick.fintech.loan_backend.dto.PaymentGatewayRequest req = new com.patrick.fintech.loan_backend.dto.PaymentGatewayRequest();
        req.setAmount(dueAmount);
        req.setPaymentMethod(method.toUpperCase());
        req.setPhoneNumber(str(body.get("phoneNumber")));
        req.setNetwork(str(body.get("network")));
        req.setCardNumber(str(body.get("cardNumber")));
        req.setCardCvv(str(body.get("cardCvv")));
        req.setCardExpiryMonth(str(body.get("cardExpiryMonth")));
        req.setCardExpiryYear(str(body.get("cardExpiryYear")));
        req.setAccountNumber(str(body.get("accountNumber")));
        req.setBankCode(str(body.get("bankCode")));
        req.setEmail(str(body.get("email")));

        com.patrick.fintech.loan_backend.dto.PaymentGatewayResponse gw = flutterwaveService.initiatePayment(
            loan.getId(), req, dueAmount, loan.getCurrency(), "Loan repayment " + loan.getReferenceNumber());

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("status", gw.getStatus());
        result.put("message", gw.getMessage());
        result.put("transactionId", gw.getTransactionId());
        result.put("redirectUrl", gw.getRedirectUrl()); // present for card 3DS — frontend should redirect here if set

        if ("success".equals(gw.getStatus())) {
            // Simulation mode, or a gateway that confirms synchronously — record right away.
            // recordedBy is null: this is a borrower self-service payment, no staff actor.
            paymentService.recordPayment(loan.getId(), gw.getAmount() != null ? gw.getAmount() : dueAmount,
                req.getPaymentMethod(), gw.getTransactionId(), "GATEWAY", "Paid via Flutterwave (borrower self-service)", null);
            result.put("recorded", true);
            auditService.log(loan.getOrganization(), null, "PUBLIC_PAYMENT_COMPLETED", "LOAN", loan.getId().toString(),
                "Borrower self-service payment of " + dueAmount + " " + loan.getCurrency() + " completed for loan " + loan.getReferenceNumber());
            return ResponseEntity.ok(ApiResponse.ok("Payment completed", result));
        }

        if ("pending".equals(gw.getStatus())) {
            // Real mobile money / bank transfer — waiting on the borrower to approve on their
            // phone, or on bank settlement. The webhook completes this, NOT this response.
            result.put("recorded", false);
            auditService.log(loan.getOrganization(), null, "PUBLIC_PAYMENT_INITIATED", "LOAN", loan.getId().toString(),
                "Borrower self-service payment of " + dueAmount + " " + loan.getCurrency() + " initiated (pending confirmation) for loan " + loan.getReferenceNumber());
            return ResponseEntity.ok(ApiResponse.ok(
                "Payment initiated — confirm on your phone, or await bank settlement", result));
        }

        throw new RuntimeException("Payment failed: " + gw.getMessage());
    }

    /** Loan agreement — key terms, as a downloadable PDF. */
    @GetMapping("/applications/{reference}/documents/agreement.pdf")
    public ResponseEntity<byte[]> downloadAgreement(@PathVariable String reference, @RequestParam String phone) {
        Loan loan = verifyOwnership(reference, phone);
        byte[] pdf = exportService.toPdf("Loan Agreement", List.of("Field", "Detail"), agreementRows(loan), loan.getOrganization().getName());
        return pdfResponse(pdf, "Loan-Agreement-" + loan.getReferenceNumber());
    }

    /** Full installment-by-installment repayment schedule, as a downloadable PDF. */
    @GetMapping("/applications/{reference}/documents/schedule.pdf")
    public ResponseEntity<byte[]> downloadSchedule(@PathVariable String reference, @RequestParam String phone) {
        Loan loan = verifyOwnership(reference, phone);
        List<String> columns = List.of("#", "Due Date", "Principal", "Interest", "Total", "Balance", "Status");
        // Sourced from the live payment ledger (the same table PaymentService.recordPayment()
        // updates on every real payment) — not the one-time schedule generated at
        // disbursement, which never changes once the borrower starts paying flexible
        // amounts and would otherwise show a schedule that no longer matches reality.
        List<Map<String,Object>> rows = paymentRepo.findByLoanId(loan.getId()).stream()
            .sorted(java.util.Comparator.comparing(Payment::getInstallmentNumber))
            .map(p -> {
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("#", p.getInstallmentNumber());
                m.put("Due Date", p.getDueDate());
                m.put("Principal", p.getPrincipalComponent());
                m.put("Interest", p.getInterestComponent());
                m.put("Total", p.getAmount() != null ? p.getAmount() : p.getAmountPaid());
                m.put("Balance", p.getOutstandingAfter());
                m.put("Status", p.getStatus());
                return m;
            }).toList();
        byte[] pdf = exportService.toPdf("Repayment Schedule", columns, rows, loan.getOrganization().getName());
        return pdfResponse(pdf, "Repayment-Schedule-" + loan.getReferenceNumber());
    }

    /** Disbursement receipt — only available once funds have actually gone out. */
    @GetMapping("/applications/{reference}/documents/receipt.pdf")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable String reference, @RequestParam String phone) {
        Loan loan = verifyOwnership(reference, phone);
        if (loan.getDisbursedAt() == null)
            throw new RuntimeException("This loan hasn't been disbursed yet — no receipt is available.");
        byte[] pdf = exportService.toPdf("Disbursement Receipt", List.of("Field", "Detail"), receiptRows(loan), loan.getOrganization().getName());
        return pdfResponse(pdf, "Disbursement-Receipt-" + loan.getReferenceNumber());
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] bytes, String filenameBase) {
        return ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filenameBase + ".pdf\"")
            .body(bytes);
    }

    private List<Map<String,Object>> agreementRows(Loan loan) {
        List<Map<String,Object>> rows = new ArrayList<>();
        java.util.function.BiConsumer<String,Object> add = (k, v) -> {
            Map<String,Object> m = new LinkedHashMap<>(); m.put("Field", k); m.put("Detail", v); rows.add(m);
        };
        add.accept("Borrower", loan.getBorrower() != null ? loan.getBorrower().getFullName() : null);
        add.accept("Reference Number", loan.getReferenceNumber());
        add.accept("Loan Type", loan.getLoanType());
        add.accept("Principal Amount", loan.getCurrency() + " " + loan.getAmount());
        add.accept("Interest Rate", loan.getInterestRate() + "% per month");
        add.accept("Duration", loan.getDurationMonths() + " months");
        add.accept("Repayment Frequency", loan.getRepaymentFrequency());
        add.accept("Total Repayable", loan.getCurrency() + " " + loan.getTotalRepayable());
        add.accept("Processing Fee", loan.getCurrency() + " " + loan.getProcessingFee());
        add.accept("Start Date", loan.getStartDate());
        add.accept("Maturity Date", loan.getMaturityDate());
        add.accept("Purpose", loan.getPurpose());
        add.accept("Collateral", loan.getCollateralDescription());
        add.accept("Loan Officer", loan.getLoanOfficer() != null ? loan.getLoanOfficer().getFullName() : "—");
        return rows;
    }

    private List<Map<String,Object>> receiptRows(Loan loan) {
        List<Map<String,Object>> rows = new ArrayList<>();
        java.util.function.BiConsumer<String,Object> add = (k, v) -> {
            Map<String,Object> m = new LinkedHashMap<>(); m.put("Field", k); m.put("Detail", v); rows.add(m);
        };
        add.accept("Borrower", loan.getBorrower() != null ? loan.getBorrower().getFullName() : null);
        add.accept("Reference Number", loan.getReferenceNumber());
        add.accept("Amount Disbursed", loan.getCurrency() + " " + loan.getDisbursedAmount());
        add.accept("Disbursement Date", loan.getDisbursedAt());
        add.accept("Loan Officer", loan.getLoanOfficer() != null ? loan.getLoanOfficer().getFullName() : "—");
        add.accept("First Installment Due", loan.getNextPaymentDate());
        return rows;
    }

    /**
     * Looks up an application by reference and confirms the caller knows the
     * phone number on file — the only "auth" a public, session-less endpoint
     * like this has. A reference number alone is guessable/sequential, so
     * without this check anyone could pull up (or upload documents into)
     * a stranger's application.
     */
    private Loan verifyOwnership(String reference, String phone) {
        Loan loan = loanRepo.findByReferenceNumber(reference.trim().toUpperCase())
            .orElseThrow(() -> new RuntimeException("We couldn't find an application with that reference number."));
        Borrower borrower = loan.getBorrower();
        String suppliedHash = com.patrick.fintech.loan_backend.security.HmacIndexer.index(phone.trim());
        if (borrower == null || borrower.getPhoneHash() == null || !borrower.getPhoneHash().equals(suppliedHash)) {
            throw new RuntimeException("We couldn't find an application with that reference number and phone number.");
        }
        return loan;
    }

    /** Applicant-facing plain-English label for each internal status. */
    private String statusLabel(LoanStatus status) {
        return switch (status) {
            case PENDING, UNDER_REVIEW -> "Under Review";
            case APPROVED              -> "Approved — awaiting disbursement";
            case REJECTED              -> "Not Approved";
            case DISBURSED, ACTIVE     -> "Active — funds disbursed";
            case OVERDUE               -> "Active — payment overdue";
            case DEFAULTED             -> "In Default";
            case RESTRUCTURED          -> "Restructured";
            case WRITTEN_OFF           -> "Written Off";
            case PAID, CLOSED          -> "Completed";
            case CANCELLED             -> "Cancelled";
        };
    }

    /** Simple 4-stage tracker for the public status page — which stage is current/done. */
    private List<Map<String,Object>> statusSteps(LoanStatus status) {
        int stage = switch (status) {
            case PENDING, UNDER_REVIEW -> 1;
            case APPROVED -> 2;
            case REJECTED, CANCELLED -> -1;
            case DISBURSED, ACTIVE, OVERDUE, DEFAULTED, RESTRUCTURED, WRITTEN_OFF, PAID, CLOSED -> 3;
        };
        String[] labels = { "Application Received", "Under Review", "Decision Made", "Funds Disbursed" };
        List<Map<String,Object>> steps = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            Map<String,Object> s = new LinkedHashMap<>();
            s.put("label", labels[i]);
            s.put("complete", stage >= 0 && i <= stage);
            s.put("failed", stage == -1 && i == 1);
            steps.add(s);
        }
        return steps;
    }

    /**
     * The richer 9-stage tracker for the borrower dashboard — aware of document
     * upload/verification state and credit assessment, not just the coarse loan
     * status. Same {label, complete, failed} shape as statusSteps() above, so the
     * existing generic step-renderer on the frontend needs no changes to show it.
     */
    private List<Map<String,Object>> progressSteps(Loan loan) {
        LoanStatus status = loan.getStatus();
        boolean failedApplication = status == LoanStatus.REJECTED || status == LoanStatus.CANCELLED;

        boolean docsUploaded = false, docsVerified = false;
        if (loan.getBorrower() != null) {
            Map<String,Object> docReq = loanService.getDocumentRequirements(loan.getId(), loan.getOrganization().getId());
            docsUploaded = Boolean.TRUE.equals(docReq.get("readyToApprove"));
            docsVerified = Boolean.TRUE.equals(docReq.get("readyToDisburse"));
        }

        boolean underReview   = status != LoanStatus.PENDING;
        boolean creditAssessed = loan.getRiskScore() != null;
        boolean approved      = loan.getApprovedAt() != null;
        boolean disbursed     = loan.getDisbursedAt() != null;
        boolean closed        = status == LoanStatus.PAID || status == LoanStatus.CLOSED;

        String[] labels = {
            "Application Submitted", "Documents Uploaded", "Documents Verified", "Under Review",
            "Credit Assessment", "Loan Approved", "Loan Disbursed", "Loan Active", "Loan Closed"
        };
        boolean[] complete = { true, docsUploaded, docsVerified, underReview, creditAssessed, approved, disbursed, disbursed, closed };

        List<Map<String,Object>> steps = new ArrayList<>();
        boolean failurePlaced = false;
        for (int i = 0; i < labels.length; i++) {
            Map<String,Object> s = new LinkedHashMap<>();
            s.put("label", labels[i]);
            boolean isFailurePoint = failedApplication && !failurePlaced && !complete[i];
            s.put("complete", complete[i] && !failedApplication);
            s.put("failed", isFailurePoint);
            if (isFailurePoint) failurePlaced = true;
            steps.add(s);
        }
        return steps;
    }

    /**
     * A dated event log built from real, already-recorded timestamps (loan lifecycle
     * dates, document upload/verification dates, per-installment paid dates) — not a
     * separate tracking mechanism, so there's nothing new to keep in sync.
     */
    private List<Map<String,Object>> timeline(Loan loan) {
        List<Map.Entry<LocalDateTime,String>> raw = new ArrayList<>();

        if (loan.getCreatedAt() != null) raw.add(Map.entry(loan.getCreatedAt(), "Application submitted"));

        if (loan.getBorrower() != null) {
            List<BorrowerFile> files = fileService.getByBorrowerMetadataOnly(loan.getBorrower().getId());
            files.stream().map(BorrowerFile::getUploadedAt).filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .ifPresent(d -> raw.add(Map.entry(d, "Documents uploaded")));
            files.stream().map(BorrowerFile::getVerifiedAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .ifPresent(d -> raw.add(Map.entry(d, "Documents verified")));
        }

        if (loan.getApprovedAt() != null) raw.add(Map.entry(loan.getApprovedAt().atStartOfDay(), "Loan approved"));
        if (loan.getStatus() == LoanStatus.REJECTED && loan.getUpdatedAt() != null)
            raw.add(Map.entry(loan.getUpdatedAt(), "Application not approved"
                + (loan.getRejectionReason() != null && !loan.getRejectionReason().isBlank() ? ": " + loan.getRejectionReason() : "")));
        if (loan.getDisbursedAt() != null) raw.add(Map.entry(loan.getDisbursedAt().atStartOfDay(), "Loan disbursed"));

        paymentService.getLoanSchedule(loan.getId(), loan.getOrganization().getId()).stream()
            .filter(p -> Boolean.TRUE.equals(p.getPaid()) && p.getPaidDate() != null)
            .forEach(p -> raw.add(Map.entry(p.getPaidDate().atStartOfDay(), "Installment #" + p.getInstallmentNumber() + " paid")));

        return raw.stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> {
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("date", e.getKey());
                m.put("label", e.getValue());
                return m;
            })
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Returns tenant branding, services config, and contact info
     * for the public website — identified by URL slug or registration number.
     */
    @GetMapping("/tenant/{slug}")
    public ResponseEntity<ApiResponse<Map<String,Object>>> getTenantConfig(@PathVariable String slug) {
        Organization org = resolveOrg(slug);
        if (org == null) return ResponseEntity.ok(ApiResponse.ok(demoConfig()));

        Map<String,Object> config = new LinkedHashMap<>();
        config.put("id",                 org.getId());
        config.put("name",               org.getName());
        config.put("slug",               slug);
        config.put("country",            org.getCountry());
        config.put("currency",           org.getDefaultCurrency());
        config.put("primaryColor",       org.getPrimaryColor() != null ? org.getPrimaryColor() : "#0D6B3E");
        config.put("accentColor",        org.getAccentColor() != null ? org.getAccentColor() : "#F5A623");
        config.put("logoUrl",            org.getLogoUrl());
        config.put("contactEmail",       org.getContactEmail());
        config.put("contactPhone",       org.getContactPhone());
        config.put("address",            org.getAddress());
        config.put("website",            org.getWebsite());
        config.put("registrationNumber", org.getRegistrationNumber());
        config.put("minLoanAmount",      org.getMinLoanAmount());
        config.put("maxLoanAmount",      org.getMaxLoanAmount());
        config.put("status",             org.getStatus());
        config.put("tagline",            org.getTagline() != null ? org.getTagline() : "Empowering Your Financial Growth");
        config.put("mission",            org.getMission() != null ? org.getMission()
            : "To provide accessible, affordable and transparent financial services to our clients.");
        config.put("vision",             org.getVision() != null ? org.getVision()
            : "To be the most trusted financial partner in our community.");
        config.put("founded",            org.getFoundedYear() != null ? org.getFoundedYear().toString() : null);
        config.put("mapUrl",             org.getMapUrl());
        Map<String,String> social = new LinkedHashMap<>();
        if (org.getFacebookUrl()  != null) social.put("facebook",  org.getFacebookUrl());
        if (org.getInstagramUrl() != null) social.put("instagram", org.getInstagramUrl());
        if (org.getLinkedinUrl()  != null) social.put("linkedin",  org.getLinkedinUrl());
        if (org.getTwitterUrl()   != null) social.put("twitter",   org.getTwitterUrl());
        if (org.getWhatsappUrl()  != null) social.put("whatsapp",  org.getWhatsappUrl());
        config.put("socialMedia", social);
        config.put("services", servicesFor(org));
        config.put("hero", Map.of(
            "headline", org.getHeroHeadline() != null ? org.getHeroHeadline() : "Borrow & Grow with us",
            "subtext",  org.getHeroSubtext()  != null ? org.getHeroSubtext()
                : "Fast approvals, competitive rates, and flexible terms. Whether you need a personal loan, business financing, or agricultural credit — we make it simple."
        ));
        config.put("stats", parseListOrDefault(org.getStatsJson(), defaultStats()));
        config.put("testimonials", parseListOrDefault(org.getTestimonialsJson(), defaultTestimonials(org.getName())));
        config.put("team", parseListOrDefault(org.getTeamJson(), defaultTeam()));
        return ResponseEntity.ok(ApiResponse.ok(config));
    }

    /**
     * Accepts a public loan application submitted from the website.
     * Finds-or-creates the borrower, creates a PENDING loan for staff to
     * review, notifies the org's staff in-app, and confirms to the applicant
     * by SMS — all fully persisted (this used to only log to the console).
     *
     * Required fields: phone, firstName, amount, email, gender, maritalStatus,
     * and nationalId (must be exactly 16 digits). All are validated up-front
     * before any borrower record is touched.
     */
        @PostMapping("/loan-application")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String,Object>>> submitApplication(
            @RequestBody Map<String,Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String slug = str(body.get("tenantSlug"));
        Organization org = resolveOrg(slug);
        if (org == null) throw new RuntimeException("We couldn't identify this lender. Please refresh the page and try again.");

        // Applications queued while offline are retried on sync — this key stops duplicate submissions
        var idempotency = idempotencyService.checkOrReserve(idempotencyKey, org, "POST /public/loan-application", body.toString());
        if (idempotency.isReplay()) {
            return ResponseEntity.ok(ApiResponse.ok("Application received", Map.of("status", "RECEIVED", "message", "Already submitted")));
        }

        String phone = str(body.get("phone"));
        if (phone == null || phone.isBlank()) throw new RuntimeException("Phone number is required");
        String firstName = str(body.get("firstName"));
        if (firstName == null || firstName.isBlank()) throw new RuntimeException("First name is required");
        Double amount = num(body.get("amount"));
        if (amount == null || amount <= 0) throw new RuntimeException("Loan amount is required");

        // --- Mandatory applicant identity fields ---
        String inputEmail = str(body.get("email"));
        if (inputEmail == null || inputEmail.isBlank()) throw new RuntimeException("Email is required");

        String gender = str(body.get("gender"));
        if (gender == null || gender.isBlank()) throw new RuntimeException("Gender is required");

        String maritalStatus = str(body.get("maritalStatus"));
        if (maritalStatus == null || maritalStatus.isBlank()) throw new RuntimeException("Marital status is required");

        String nationalId = str(body.get("nationalId"));
        if (nationalId == null || !nationalId.trim().matches("\\d{16}")) {
            throw new RuntimeException("National ID is required and must be exactly 16 digits");
        }
        nationalId = nationalId.trim();
        // --- end mandatory applicant identity fields ---

        boolean acceptedTerms = body.get("acceptedTerms") != null && Boolean.parseBoolean(body.get("acceptedTerms").toString());
        if (!acceptedTerms) throw new RuntimeException("You must accept the Terms & Conditions to submit an application");

        if ("Married".equalsIgnoreCase(maritalStatus) && (str(body.get("spouseFullName")) == null)) {
            throw new RuntimeException("Spouse's full name is required for married applicants");
        }
        if ("Single".equalsIgnoreCase(maritalStatus) && (str(body.get("singleCertificateNumber")) == null)) {
            throw new RuntimeException("Single Status Certificate number is required for single applicants");
        }

        // Find or create the borrower for this org
        Borrower borrower = borrowerRepo.findByPhoneHashAndOrganization_Id(
                com.patrick.fintech.loan_backend.security.HmacIndexer.index(phone), org.getId())
            .orElseGet(() -> Borrower.builder().organization(org).build());

        borrower.setFirstName(firstName);
        borrower.setLastName(str(body.get("lastName")));
        borrower.setPhone(phone);
        borrower.setEmail(inputEmail.trim());
        borrower.setNationalId(nationalId);
        borrower.setDateOfBirth(date(body.get("dateOfBirth")));
        borrower.setGender(gender);
        borrower.setMaritalStatus(maritalStatus);
        borrower.setSingleCertificateNumber(str(body.get("singleCertificateNumber")));
        borrower.setSpouseFullName(str(body.get("spouseFullName")));
        borrower.setSpouseNationalId(str(body.get("spouseNationalId")));
        borrower.setSpousePhone(str(body.get("spousePhone")));
        borrower.setSpouseConsent(body.get("spouseConsent") != null ? Boolean.parseBoolean(body.get("spouseConsent").toString()) : null);
        borrower.setAddress(str(body.get("address")));
        borrower.setAddressLine1(str(body.get("address")));
        borrower.setCity(str(body.get("city")));
        borrower.setStateProvince(str(body.get("province")));
        borrower.setCountry(org.getCountry());
        borrower.setEmploymentType(str(body.get("employmentType")));
        borrower.setEmployerName(str(body.get("employerName")));
        borrower.setJobTitle(str(body.get("jobTitle")));
        borrower.setMonthlyIncome(num(body.get("monthlyIncome")));
        borrower.setMonthlyExpenses(num(body.get("monthlyExpenses")));
        borrower = borrowerRepo.save(borrower);

        int months = 12;
        if (body.get("durationMonths") != null) {
            try { months = Integer.parseInt(body.get("durationMonths").toString()); } catch (NumberFormatException ignored) {}
        }
        months = Math.max(1, Math.min(months, 360));

        LoanRequest req = LoanRequest.builder()
            .borrowerId(borrower.getId())
            .amount(amount)
            .durationMonths(months)
            .currency(org.getDefaultCurrency())
            .purpose(str(body.get("purpose")))
            .notes("Submitted via public website" + (body.get("loanType") != null ? " — product: " + body.get("loanType") : ""))
            .collateralValue(num(body.get("collateralValue")))
            .collateralDescription(str(body.get("collateral")))
            .loanType(mapLoanType(str(body.get("loanType"))))
            .startDate(LocalDate.now().toString())
            .build();

        Loan loan = loanService.createLoan(req, org.getId(), null);
        loan.setTermsAcceptedAt(LocalDateTime.now());
        loan = loanRepo.save(loan);

        notifyStaff(org, borrower, loan);

        try {
            mailService.sendApplicationReceived(loan);
            log.info("[EMAIL SUCCESS] Dispatched application received alert to: {}", borrower.getEmail());
        } catch (Exception e) {
            log.error("[EMAIL CRITICAL FAILURE] Outbound loan receipt transmission failed line error trace:", e);
        }

        try {
            smsService.sendCustom(phone, String.format(
                "%s: Thank you %s! We received your loan application %s for %s %,.0f. We'll contact you within 24-48 hours.",
                org.getName(), firstName, loan.getReferenceNumber(), loan.getCurrency(), amount));
        } catch (Exception e) {
            log.warn("Application confirmation SMS failed: {}", e.getMessage());
        }

        auditService.log(org, null, "PUBLIC_LOAN_APPLICATION", "LOAN", loan.getId().toString(),
            "Online application submitted by " + borrower.getFullName() + " via " + org.getName() + " website");

        return ResponseEntity.ok(ApiResponse.ok("Application received", Map.of(
            "reference", loan.getReferenceNumber(),
            "loanId",    loan.getId(),
            "message",   "We will contact you within 24-48 hours",
            "status",    "RECEIVED"
        )));
    }

    /**
     * Returns all public-facing loan products for a tenant.
     */
    @GetMapping("/tenant/{slug}/products")
    public ResponseEntity<ApiResponse<List<Map<String,Object>>>> getProducts(@PathVariable String slug) {
        Organization org = resolveOrg(slug);
        return ResponseEntity.ok(ApiResponse.ok(org != null ? servicesFor(org) : buildProducts()));
    }

    private List<Map<String,Object>> servicesFor(Organization org) {
        List<LoanProduct> products = loanProductRepo.findByOrganization_IdAndActiveTrueOrderByDisplayOrderAsc(org.getId());
        if (!products.isEmpty()) {
            return products.stream().map(p -> {
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("title", p.getName());
                m.put("icon", p.getIcon() != null ? p.getIcon() : "💰");
                m.put("rate", (p.getInterestRate() != null ? p.getInterestRate() : 0) + "% / month");
                m.put("maxAmount", p.getMaxAmount() != null ? String.valueOf(p.getMaxAmount().longValue()) : "Unlimited");
                m.put("term", "up to " + p.getMaxTermMonths() + " months");
                m.put("description", p.getDescription() != null ? p.getDescription() : "");
                return m;
            }).toList();
        }
        // No real products configured yet — fall back to the marketing-only JSON, then generic defaults
        return parseListOrDefault(org.getServicesJson(), buildProducts());
    }

    private List<Map<String,Object>> parseListOrDefault(String json, List<Map<String,Object>> fallback) {
        if (json == null || json.isBlank()) return fallback;
        try {
            List<Map<String,Object>> parsed = objectMapper.readValue(json, new TypeReference<List<Map<String,Object>>>() {});
            return (parsed == null || parsed.isEmpty()) ? fallback : parsed;
        } catch (Exception e) {
            log.warn("Could not parse stored CMS JSON, using default: {}", e.getMessage());
            return fallback;
        }
    }

    private List<Map<String,Object>> defaultStats() {
        List<Map<String,Object>> stats = new ArrayList<>();
        String[][] rows = {
            {"👥","5,000+","Happy Clients"}, {"💰","RWF 2B+","Loans Disbursed"},
            {"⚡","24 hrs","Average Approval"}, {"⭐","98%","Client Satisfaction"},
        };
        for (String[] r : rows) stats.add(Map.of("icon", r[0], "value", r[1], "label", r[2]));
        return stats;
    }

    private List<Map<String,Object>> defaultTestimonials(String orgName) {
        List<Map<String,Object>> t = new ArrayList<>();
        t.add(Map.of("name","Amina K.","role","Small Business Owner","rating",5,
            "text", orgName + " helped me expand my shop with a business loan. The process was fast and the staff were very professional."));
        t.add(Map.of("name","Jean-Pierre N.","role","Farmer, Eastern Province","rating",5,
            "text","I got an agricultural loan to buy quality seeds and farming equipment. My harvest doubled this season. Thank you " + orgName + "!"));
        t.add(Map.of("name","Marie-Claire U.","role","Teacher","rating",5,
            "text","The salary advance helped me cover my children's school fees on time. Easy application, same-day approval. Excellent service!"));
        return t;
    }

    private List<Map<String,Object>> defaultTeam() {
        List<Map<String,Object>> team = new ArrayList<>();
        team.add(Map.of("name","Emmanuel R.","role","Chief Executive Officer","initials","ER"));
        team.add(Map.of("name","Grace N.","role","Chief Finance Officer","initials","GN"));
        team.add(Map.of("name","Patrick M.","role","Head of Credit","initials","PM"));
        team.add(Map.of("name","Alice K.","role","Head of Operations","initials","AK"));
        return team;
    }

    private List<Map<String,Object>> buildProducts() {
        List<Map<String,Object>> products = new ArrayList<>();
        String[][] defaults = {
            {"Personal Loan",     "👤","10","5000000","36","Fast personal financing for any purpose"},
            {"Business Loan",     "🏢","12","50000000","60","Working capital and business expansion"},
            {"Agricultural Loan", "🌾","9", "10000000","24","Seasonal farming and agribusiness finance"},
            {"SME Finance",       "📦","11","30000000","48","Tailored finance for small businesses"},
            {"Salary Advance",    "💵","10", "2000000", "3", "Quick advance on your monthly salary"},
            {"Microfinance",      "💡","18","500000",  "12","Small loans for micro-entrepreneurs"},
        };
        for (String[] p : defaults) {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("title",       p[0]); m.put("icon",        p[1]);
            m.put("rate",        p[2]+"% / month"); m.put("maxAmount", p[3]);
            m.put("term",        "up to "+p[4]+" months"); m.put("description", p[5]);
            products.add(m);
        }
        return products;
    }

    // ---- Helpers ----

    /** Resolves an org by matching its name (lowercased, spaces stripped) or registration number against the slug. */
    private Organization resolveOrg(String slug) {
        if (slug == null || slug.isBlank()) return null;
        return orgRepo.findAll().stream()
            .filter(o -> o.getName().toLowerCase().replace(" ", "").contains(slug.toLowerCase())
                || (o.getRegistrationNumber() != null && o.getRegistrationNumber().toLowerCase().contains(slug.toLowerCase())))
            .findFirst().orElse(null);
    }

    /** Notifies every ADMIN / MANAGER / LOAN_OFFICER at the org that a new application has arrived. */
    private void notifyStaff(Organization org, Borrower borrower, Loan loan) {
        List<User> staff = userRepo.findByOrganization(org).stream()
            .filter(u -> u.getRole() != null && Set.of("ADMIN", "MANAGER", "LOAN_OFFICER").contains(u.getRole().getName()))
            .toList();
        notificationService.notifyUsers(staff,
            "New Loan Application",
            borrower.getFullName() + " applied for " + loan.getCurrency() + " " + fmt(loan.getAmount())
                + " (" + loan.getLoanType() + ") — Ref " + loan.getReferenceNumber(),
            "info",
            "/dashboard/loans/" + loan.getId());
    }

    private Loan.LoanType mapLoanType(String label) {
        if (label == null) return Loan.LoanType.PERSONAL;
        String l = label.toLowerCase();
        if (l.contains("business") || l.contains("sme"))   return Loan.LoanType.BUSINESS;
        if (l.contains("agri"))                             return Loan.LoanType.AGRICULTURAL;
        if (l.contains("salary"))                            return Loan.LoanType.SALARY_ADVANCE;
        if (l.contains("micro"))                             return Loan.LoanType.MICROFINANCE;
        if (l.contains("auto") || l.contains("car") || l.contains("asset")) return Loan.LoanType.ASSET_FINANCE;
        if (l.contains("mortgage") || l.contains("home"))    return Loan.LoanType.MORTGAGE;
        if (l.contains("student") || l.contains("education")) return Loan.LoanType.STUDENT;
        if (l.contains("emergency"))                         return Loan.LoanType.EMERGENCY;
        if (l.contains("trade"))                             return Loan.LoanType.TRADE_FINANCE;
        if (l.contains("group"))                             return Loan.LoanType.GROUP;
        return Loan.LoanType.PERSONAL;
    }

    private String str(Object o) { return o == null ? null : o.toString().isBlank() ? null : o.toString(); }
    private Double num(Object o) {
        if (o == null || o.toString().isBlank()) return null;
        try { return Double.valueOf(o.toString()); } catch (NumberFormatException e) { return null; }
    }
    private LocalDate date(Object o) {
        if (o == null || o.toString().isBlank()) return null;
        try { return LocalDate.parse(o.toString()); } catch (Exception e) { return null; }
    }
    private String fmt(Double d) { return d == null ? "0" : String.format("%,.0f", d); }

    private Map<String,Object> demoConfig() {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("name", "Growth Finance Services Ltd");
        m.put("slug", "growthfinance");
        m.put("country", "Rwanda");
        m.put("currency", "RWF");
        m.put("primaryColor", "#0D6B3E");
        m.put("accentColor", "#F5A623");
        m.put("contactEmail", "info@growthfinance.rw");
        m.put("contactPhone", "+250 788 000 000");
        m.put("address", "KG 7 Ave, Kigali, Rwanda");
        m.put("status", "ACTIVE");
        return m;
    }
}