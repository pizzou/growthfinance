package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.*;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.service.*;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService        loanService;
    private final PaymentService     paymentService;   // injected directly — avoids circular dep
    private final RiskScoringService riskScoringService;
    private final LoanApprovalService loanApprovalService; // avoids circular dep — LoanApprovalService itself depends on LoanService
    private final CurrentUserUtil    currentUserUtil;
    private final com.patrick.fintech.loan_backend.repository.LoanCommentRepository loanCommentRepo;
    private final SmsService         smsService;
    private final MailService  mailService;
    private final AuditService       auditService;

    @PostMapping
    @PreAuthorize("hasAnyRole('LOAN_OFFICER','ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Loan>> createLoan(@Valid @RequestBody LoanRequest req) {
        User user = currentUserUtil.getCurrentUser();
        Loan loan = loanService.createLoan(req, user.getOrganization().getId(), user);
        loanApprovalService.initiateChain(loan); // sets up the maker-checker steps this loan's size requires
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Loan created", loan));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Loan>>> getLoans(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String status,
            @RequestParam(required = false)    String type) {
        Organization org = currentUserUtil.getCurrentUser().getOrganization();
        return ResponseEntity.ok(ApiResponse.ok(loanService.getLoans(org, page, size, status, type)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Loan>> getLoan(@PathVariable Long id) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        return ResponseEntity.ok(ApiResponse.ok(loanService.getLoanForOrg(id, orgId)));
    }

    @GetMapping("/{id}/schedule")
    public ResponseEntity<ApiResponse<List<Payment>>> getSchedule(@PathVariable Long id) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        loanService.getLoanForOrg(id, orgId); // access check
        // Use paymentService directly — no more circular dep via loanService.getPaymentService()
        return ResponseEntity.ok(ApiResponse.ok(paymentService.getLoanSchedule(id, orgId)));
    }

    /**
     * Powers the "Required Documents" checklist on the loan detail page.
     * This was previously missing here, so the frontend's call to
     * GET /api/loans/{id}/document-requirements fell through to Spring's
     * static-resource handler and blew up as a 404 NoResourceFoundException
     * logged as an "Unhandled error" 500.
     */
    @GetMapping("/{id}/document-requirements")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDocumentRequirements(@PathVariable Long id) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        return ResponseEntity.ok(ApiResponse.ok(loanService.getDocumentRequirements(id, orgId)));
    }

    @GetMapping("/borrower/{borrowerId}")
    public ResponseEntity<ApiResponse<List<Loan>>> getByBorrower(@PathVariable Long borrowerId) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        return ResponseEntity.ok(ApiResponse.ok(
            loanService.getLoanRepository().findByBorrowerIdAndOrganizationId(borrowerId, orgId)));
    }

    @GetMapping("/{id}/risk")
    public ResponseEntity<ApiResponse<RiskScoringService.RiskResult>> getRisk(@PathVariable Long id) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        Loan loan  = loanService.getLoanForOrg(id, orgId);
        return ResponseEntity.ok(ApiResponse.ok(riskScoringService.score(loan)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
    public ResponseEntity<ApiResponse<Loan>> approveLoan(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        User user  = currentUserUtil.getCurrentUser();
        String notes = body != null ? body.get("notes") : null;
        Double newInterestRate = null;
        if (body != null && body.get("interestRate") != null && !body.get("interestRate").isBlank()) {
            try { newInterestRate = Double.valueOf(body.get("interestRate")); }
            catch (NumberFormatException e) { throw new RuntimeException("interestRate must be a number"); }
        }
        loanApprovalService.decide(id, user, "APPROVED", notes, newInterestRate);
        return ResponseEntity.ok(ApiResponse.ok("Decision recorded", loanService.getLoanForOrg(id, user.getOrganization().getId())));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
    public ResponseEntity<ApiResponse<Loan>> rejectLoan(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        User user   = currentUserUtil.getCurrentUser();
        String reason = body != null ? body.get("reason") : "No reason provided";
        loanApprovalService.decide(id, user, "REJECTED", reason);
        return ResponseEntity.ok(ApiResponse.ok("Decision recorded", loanService.getLoanForOrg(id, user.getOrganization().getId())));
    }

    @PostMapping("/{id}/disburse")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Loan>> disburseLoan(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        User user   = currentUserUtil.getCurrentUser();
        String method = (body != null) ? body.getOrDefault("disbursementMethod", "BANK_TRANSFER") : "BANK_TRANSFER";
        return ResponseEntity.ok(ApiResponse.ok("Loan disbursed",
            loanService.disburseLoan(id, user, method)));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
    public ResponseEntity<ApiResponse<Loan>> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        User user = currentUserUtil.getCurrentUser();
        LoanStatus newStatus = LoanStatus.valueOf(body.get("status"));
        return ResponseEntity.ok(ApiResponse.ok("Status updated",
            loanService.updateStatus(id, user, newStatus, body.get("notes"))));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardStats>> getDashboard() {
        Organization org = currentUserUtil.getCurrentUser().getOrganization();
        return ResponseEntity.ok(ApiResponse.ok(loanService.getDashboard(org)));
    }

    /**
     * Staff note on an application — e.g. "please upload your land title
     * document". Visible to the applicant on the public tracking page unless
     * visibleToApplicant is explicitly false (for internal-only notes).
     */
    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ApiResponse<LoanComment>> addComment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        User user = currentUserUtil.getCurrentUser();
        Loan loan = loanService.getLoanForOrg(id, user.getOrganization().getId());

        String message = body.get("message") != null ? body.get("message").toString().trim() : "";
        if (message.isEmpty()) throw new RuntimeException("Comment message is required");
        boolean visible = body.get("visibleToApplicant") == null || Boolean.parseBoolean(body.get("visibleToApplicant").toString());

        LoanComment comment = loanCommentRepo.save(LoanComment.builder()
            .loan(loan).author(user).message(message).visibleToApplicant(visible).build());

        auditService.log(loan.getOrganization(), user, "LOAN_COMMENT_ADDED", "LOAN",
            id.toString(), (visible ? "Applicant-visible comment" : "Internal comment") + " added to loan "
                + loan.getReferenceNumber() + ": " + message, null, null, "Loans");

        if (visible && loan.getBorrower() != null && loan.getBorrower().getPhone() != null) {
            try {
                smsService.sendCustom(loan.getBorrower().getPhone(), String.format(
                    "%s: New update on your application %s. Please check your application status online for details.",
                    loan.getOrganization().getName(), loan.getReferenceNumber()));
            } catch (Exception ignored) { /* best-effort — comment is saved either way */ }
        }
        if (visible && loan.getBorrower() != null && loan.getBorrower().getEmail() != null) {
            try { mailService.sendLoanUpdateComment(loan, message); } catch (Exception ignored) { /* best-effort */ }
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Comment added", comment));
    }

    /** Full comment history (internal + applicant-visible) for staff viewing a loan. */
    @GetMapping("/{id}/comments")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<LoanComment>>> getComments(@PathVariable Long id) {
        User user = currentUserUtil.getCurrentUser();
        loanService.getLoanForOrg(id, user.getOrganization().getId()); // ownership check
        return ResponseEntity.ok(ApiResponse.ok(loanCommentRepo.findByLoanIdOrderByCreatedAtAsc(id)));
    }
}