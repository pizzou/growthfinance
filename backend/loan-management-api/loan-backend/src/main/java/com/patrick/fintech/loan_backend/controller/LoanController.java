package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.DashboardStats;
import com.patrick.fintech.loan_backend.dto.LoanRequest;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanComment;
import com.patrick.fintech.loan_backend.model.LoanApproval;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.LoanCommentRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.LoanApprovalService;
import com.patrick.fintech.loan_backend.service.LoanService;
import com.patrick.fintech.loan_backend.service.MailService;
import com.patrick.fintech.loan_backend.service.PaymentService;
import com.patrick.fintech.loan_backend.service.RiskScoringService;
import com.patrick.fintech.loan_backend.service.SmsService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    private final PaymentService paymentService;

    private final RiskScoringService riskScoringService;

    private final LoanApprovalService loanApprovalService;

    private final CurrentUserUtil currentUserUtil;

    private final LoanCommentRepository loanCommentRepo;

    private final SmsService smsService;

    private final MailService mailService;

    private final AuditService auditService;


    // ================================================================
    // CREATE LOAN
    // ================================================================

    @PostMapping
    @PreAuthorize("hasAnyRole('LOAN_OFFICER','ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Loan>> createLoan(
            @Valid @RequestBody LoanRequest req
    ) {

        User user =
                currentUserUtil.getCurrentUser();

        Loan loan =
                loanService.createLoan(
                        req,
                        user.getOrganization().getId(),
                        user
                );

        /*
         * Create the maker-checker approval chain immediately
         * after the loan has been persisted.
         */
        loanApprovalService.initiateChain(
                loan
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.ok(
                                "Loan application created and submitted for approval",
                                loan
                        )
                );
    }


    // ================================================================
    // GET LOANS
    // ================================================================

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Loan>>> getLoans(
            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size,

            @RequestParam(required = false)
            String status,

            @RequestParam(required = false)
            String type
    ) {

        Organization organization =
                currentUserUtil
                        .getCurrentUser()
                        .getOrganization();

        return ResponseEntity.ok(
                ApiResponse.ok(
                        loanService.getLoans(
                                organization,
                                page,
                                size,
                                status,
                                type
                        )
                )
        );
    }


    // ================================================================
    // GET SINGLE LOAN
    // ================================================================

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Loan>> getLoan(
            @PathVariable Long id
    ) {

        Long organizationId =
                currentUserUtil
                        .getCurrentOrganizationId();

        return ResponseEntity.ok(
                ApiResponse.ok(
                        loanService.getLoanForOrg(
                                id,
                                organizationId
                        )
                )
        );
    }


    // ================================================================
    // GET LOAN SCHEDULE
    // ================================================================

    @GetMapping("/{id}/schedule")
    public ResponseEntity<ApiResponse<List<Payment>>> getSchedule(
            @PathVariable Long id
    ) {

        Long organizationId =
                currentUserUtil
                        .getCurrentOrganizationId();

        /*
         * Explicit organization ownership check.
         */
        loanService.getLoanForOrg(
                id,
                organizationId
        );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        paymentService.getLoanSchedule(
                                id,
                                organizationId
                        )
                )
        );
    }


    // ================================================================
    // DOCUMENT REQUIREMENTS
    // ================================================================

    @GetMapping("/{id}/document-requirements")
    public ResponseEntity<ApiResponse<Map<String, Object>>>
    getDocumentRequirements(
            @PathVariable Long id
    ) {

        Long organizationId =
                currentUserUtil
                        .getCurrentOrganizationId();

        return ResponseEntity.ok(
                ApiResponse.ok(
                        loanService.getDocumentRequirements(
                                id,
                                organizationId
                        )
                )
        );
    }


    // ================================================================
    // GET LOANS BY BORROWER
    // ================================================================

    @GetMapping("/borrower/{borrowerId}")
    public ResponseEntity<ApiResponse<List<Loan>>> getByBorrower(
            @PathVariable Long borrowerId
    ) {

        Long organizationId =
                currentUserUtil
                        .getCurrentOrganizationId();

        return ResponseEntity.ok(
                ApiResponse.ok(
                        loanService
                                .getLoanRepository()
                                .findByBorrowerIdAndOrganizationId(
                                        borrowerId,
                                        organizationId
                                )
                )
        );
    }


    // ================================================================
    // RISK SCORE
    // ================================================================

    @GetMapping("/{id}/risk")
    public ResponseEntity<
            ApiResponse<RiskScoringService.RiskResult>
            > getRisk(
            @PathVariable Long id
    ) {

        Long organizationId =
                currentUserUtil
                        .getCurrentOrganizationId();

        Loan loan =
                loanService.getLoanForOrg(
                        id,
                        organizationId
                );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        riskScoringService.score(
                                loan
                        )
                )
        );
    }


    // ================================================================
    // APPROVE LOAN
    // ================================================================

    /**
     * Records the next maker-checker approval step.
     *
     * IMPORTANT:
     *
     * Loan officers create applications but do not approve their
     * own applications.
     *
     * The LoanApprovalService performs the final role, tenant,
     * maker-checker and duplicate-approver checks.
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Loan>> approveLoan(
            @PathVariable Long id,

            @RequestBody(required = false)
            Map<String, String> body
    ) {

        User user =
                currentUserUtil.getCurrentUser();

        String notes =
                body != null
                        ? body.get("notes")
                        : null;

        Double newInterestRate = null;

        if (body != null
                && body.get("interestRate") != null
                && !body.get("interestRate").isBlank()) {

            try {

                newInterestRate =
                        Double.valueOf(
                                body.get("interestRate")
                        );

            } catch (NumberFormatException e) {

                throw new IllegalArgumentException(
                        "interestRate must be a valid number."
                );
            }

            if (newInterestRate < 0) {

                throw new IllegalArgumentException(
                        "interestRate cannot be negative."
                );
            }
        }

        loanApprovalService.decide(
                id,
                user,
                "APPROVED",
                notes,
                newInterestRate
        );

        Loan updatedLoan =
                loanService.getLoanForOrg(
                        id,
                        user.getOrganization().getId()
                );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Approval decision recorded",
                        updatedLoan
                )
        );
    }


    // ================================================================
    // REJECT LOAN
    // ================================================================

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Loan>> rejectLoan(
            @PathVariable Long id,

            @RequestBody(required = false)
            Map<String, String> body
    ) {

        User user =
                currentUserUtil.getCurrentUser();

        String reason =
                body != null
                        && body.get("reason") != null
                        && !body.get("reason").isBlank()
                        ? body.get("reason").trim()
                        : "No reason provided";

        loanApprovalService.decide(
                id,
                user,
                "REJECTED",
                reason
        );

        Loan updatedLoan =
                loanService.getLoanForOrg(
                        id,
                        user.getOrganization().getId()
                );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Rejection decision recorded",
                        updatedLoan
                )
        );
    }


    // ================================================================
    // DISBURSE
    // ================================================================

    @PostMapping("/{id}/disburse")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Loan>> disburseLoan(
            @PathVariable Long id,

            @RequestBody(required = false)
            Map<String, String> body
    ) {

        User user =
                currentUserUtil.getCurrentUser();

        String method =
                body != null
                        ? body.getOrDefault(
                                "disbursementMethod",
                                "BANK_TRANSFER"
                        )
                        : "BANK_TRANSFER";

        Loan loan =
                loanService.disburseLoan(
                        id,
                        user,
                        method
                );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Loan disbursed",
                        loan
                )
        );
    }


    // ================================================================
    // UPDATE STATUS
    // ================================================================

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
    public ResponseEntity<ApiResponse<Loan>> updateStatus(
            @PathVariable Long id,

            @RequestBody
            Map<String, String> body
    ) {

        if (body == null
                || body.get("status") == null
                || body.get("status").isBlank()) {

            throw new IllegalArgumentException(
                    "status is required."
            );
        }

        User user =
                currentUserUtil.getCurrentUser();

        LoanStatus newStatus;

        try {

            newStatus =
                    LoanStatus.valueOf(
                            body.get("status")
                                    .trim()
                                    .toUpperCase()
                    );

        } catch (IllegalArgumentException e) {

            throw new IllegalArgumentException(
                    "Invalid loan status: "
                            + body.get("status")
            );
        }

        Loan loan =
                loanService.updateStatus(
                        id,
                        user,
                        newStatus,
                        body.get("notes")
                );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Status updated",
                        loan
                )
        );
    }


    // ================================================================
    // DASHBOARD
    // ================================================================

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardStats>>
    getDashboard() {

        Organization organization =
                currentUserUtil
                        .getCurrentUser()
                        .getOrganization();

        return ResponseEntity.ok(
                ApiResponse.ok(
                        loanService.getDashboard(
                                organization
                        )
                )
        );
    }


    // ================================================================
    // ADD STAFF COMMENT
    // ================================================================

    /**
     * Staff note on a loan application.
     *
     * Applicant-visible comments can be sent to the borrower.
     * Internal comments remain staff-only.
     */
    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
    @Transactional
    public ResponseEntity<ApiResponse<LoanComment>> addComment(
            @PathVariable Long id,

            @RequestBody
            Map<String, Object> body
    ) {

        User user =
                currentUserUtil.getCurrentUser();

        Loan loan =
                loanService.getLoanForOrg(
                        id,
                        user.getOrganization().getId()
                );

        String message =
                body != null
                        && body.get("message") != null
                        ? body.get("message")
                        .toString()
                        .trim()
                        : "";

        if (message.isEmpty()) {

            throw new IllegalArgumentException(
                    "Comment message is required."
            );
        }

        boolean visibleToApplicant =
                body == null
                        || body.get("visibleToApplicant") == null
                        || Boolean.parseBoolean(
                                body.get("visibleToApplicant")
                                        .toString()
                        );

        LoanComment comment =
                loanCommentRepo.save(
                        LoanComment.builder()
                                .loan(loan)
                                .author(user)
                                .message(message)
                                .visibleToApplicant(
                                        visibleToApplicant
                                )
                                .build()
                );

        auditService.log(
                loan.getOrganization(),
                user,
                "LOAN_COMMENT_ADDED",
                "LOAN",
                id.toString(),
                (
                        visibleToApplicant
                                ? "Applicant-visible comment"
                                : "Internal comment"
                )
                        + " added to loan "
                        + loan.getReferenceNumber()
                        + ": "
                        + message,
                null,
                null,
                "Loans"
        );


        // ------------------------------------------------------------
        // SMS
        // ------------------------------------------------------------

        if (visibleToApplicant
                && loan.getBorrower() != null
                && loan.getBorrower().getPhone() != null
                && !loan.getBorrower()
                .getPhone()
                .isBlank()) {

            try {

                smsService.sendCustom(
                        loan.getBorrower().getPhone(),

                        String.format(
                                "%s: New update on your application %s. "
                                        + "Please check your application status online for details.",

                                loan.getOrganization().getName(),

                                loan.getReferenceNumber()
                        )
                );

            } catch (Exception e) {

                /*
                 * Notification failure must not roll back
                 * the saved comment.
                 */
                org.slf4j.LoggerFactory
                        .getLogger(LoanController.class)
                        .warn(
                                "Failed to send applicant comment SMS for loan {}",
                                id,
                                e
                        );
            }
        }


        // ------------------------------------------------------------
        // EMAIL
        // ------------------------------------------------------------

        if (visibleToApplicant
                && loan.getBorrower() != null
                && loan.getBorrower().getEmail() != null
                && !loan.getBorrower()
                .getEmail()
                .isBlank()) {

            try {

                mailService.sendLoanUpdateComment(
                        loan,
                        message
                );

            } catch (Exception e) {

                /*
                 * Notification failure must not roll back
                 * the saved comment.
                 */
                org.slf4j.LoggerFactory
                        .getLogger(LoanController.class)
                        .warn(
                                "Failed to send applicant comment email for loan {}",
                                id,
                                e
                        );
            }
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.ok(
                                "Comment added",
                                comment
                        )
                );
    }


    // ================================================================
    // GET COMMENTS
    // ================================================================

    /**
     * Full internal staff comment history.
     */
    @GetMapping("/{id}/comments")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<LoanComment>>>
    getComments(
            @PathVariable Long id
    ) {

        User user =
                currentUserUtil.getCurrentUser();

        /*
         * Explicit organization ownership check.
         */
        loanService.getLoanForOrg(
                id,
                user.getOrganization().getId()
        );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        loanCommentRepo
                                .findByLoanIdOrderByCreatedAtAsc(
                                        id
                                )
                )
        );
    }
}