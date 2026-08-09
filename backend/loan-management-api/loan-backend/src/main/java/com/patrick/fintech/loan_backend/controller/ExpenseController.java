package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.Expense;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.ExpenseService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','ACCOUNTANT')")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final OrganizationRepository orgRepo;
    private final CurrentUserUtil currentUserUtil;
    private final AuditService auditService;

    // ============================================================
    // CREATE EXPENSE
    // ============================================================

    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<Expense>> create(

            @RequestParam("expenseDate")
            String expenseDate,

            @RequestParam("category")
            String category,

            @RequestParam("amount")
            BigDecimal amount,

            @RequestParam("paymentAccountId")
            Long paymentAccountId,

            @RequestParam(
                    value = "branchId",
                    required = false
            )
            Long branchId,

            @RequestParam(
                    value = "description",
                    required = false
            )
            String description,

            @RequestParam(
                    value = "paymentMethod",
                    required = false
            )
            String paymentMethod,

            @RequestParam(
                    value = "paymentProvider",
                    required = false
            )
            String paymentProvider,

            @RequestParam(
                    value = "paymentPhoneNumber",
                    required = false
            )
            String paymentPhoneNumber,

            @RequestParam(
                    value = "paymentTransactionReference",
                    required = false
            )
            String paymentTransactionReference,

            @RequestParam(
                    value = "paymentCode",
                    required = false
            )
            String paymentCode,

            @RequestParam(
                    value = "cardBrand",
                    required = false
            )
            String cardBrand,

            @RequestParam(
                    value = "cardLastFour",
                    required = false
            )
            String cardLastFour,

            @RequestParam(
                    value = "cardAuthorizationCode",
                    required = false
            )
            String cardAuthorizationCode,

            @RequestParam(
                    value = "chequeNumber",
                    required = false
            )
            String chequeNumber,

            @RequestParam(
                    value = "paymentNotes",
                    required = false
            )
            String paymentNotes,

            @RequestParam(
                    value = "receipt",
                    required = false
            )
            MultipartFile receipt

    ) throws Exception {

        // ========================================================
        // ORGANIZATION
        // ========================================================

        Long organizationId =
                currentUserUtil.getCurrentOrganizationId();

        if (organizationId == null) {
            throw new IllegalStateException(
                    "Current organization could not be determined"
            );
        }

        Organization org =
                orgRepo.findById(organizationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Organization not found"
                                )
                        );

        // ========================================================
        // BASIC REQUEST VALIDATION
        // ========================================================

        if (expenseDate == null || expenseDate.isBlank()) {
            throw new IllegalArgumentException(
                    "Expense date is required"
            );
        }

        LocalDate parsedExpenseDate;

        try {
            parsedExpenseDate =
                    LocalDate.parse(expenseDate.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Invalid expense date. Expected format: yyyy-MM-dd"
            );
        }

        if (amount == null) {
            throw new IllegalArgumentException(
                    "Expense amount is required"
            );
        }

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Expense amount must be greater than zero"
            );
        }

        // ========================================================
        // PAYMENT METHOD
        // ========================================================

        Expense.PaymentMethod method = null;

        if (paymentMethod != null
                && !paymentMethod.isBlank()) {

            try {

                method =
                        Expense.PaymentMethod.valueOf(
                                paymentMethod
                                        .trim()
                                        .toUpperCase()
                        );

            } catch (IllegalArgumentException ex) {

                throw new IllegalArgumentException(
                        "Invalid payment method: "
                                + paymentMethod
                );
            }
        }

        // ========================================================
        // CATEGORY
        // ========================================================

        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException(
                    "Expense category is required"
            );
        }

        Expense.ExpenseCategory expenseCategory;

        try {

            expenseCategory =
                    Expense.ExpenseCategory.valueOf(
                            category
                                    .trim()
                                    .toUpperCase()
                    );

        } catch (IllegalArgumentException ex) {

            throw new IllegalArgumentException(
                    "Invalid expense category: "
                            + category
            );
        }

        // ========================================================
        // CURRENT USER
        // ========================================================

        if (currentUserUtil.getCurrentUser() == null) {
            throw new IllegalStateException(
                    "Authenticated user could not be determined"
            );
        }

        String createdByName =
                currentUserUtil
                        .getCurrentUser()
                        .getName();

        // ========================================================
        // CREATE EXPENSE
        // ========================================================

        Expense created =
                expenseService.create(

                        org,

                        parsedExpenseDate,

                        expenseCategory,

                        amount,

                        paymentAccountId,

                        branchId,

                        description,

                        createdByName,

                        method,

                        paymentProvider,

                        paymentPhoneNumber,

                        paymentTransactionReference,

                        paymentCode,

                        cardBrand,

                        cardLastFour,

                        cardAuthorizationCode,

                        chequeNumber,

                        paymentNotes,

                        receipt
                );

        // ========================================================
        // AUDIT
        // ========================================================

        auditService.log(
                org,
                currentUserUtil.getCurrentUser(),
                "EXPENSE_RECORDED",
                "EXPENSE",
                String.valueOf(created.getId()),
                "Recorded "
                        + created.getCategory().getLabel()
                        + " expense of "
                        + created.getAmount()
                        + " "
                        + created.getCurrency(),
                null,
                null,
                "Accounting"
        );

        // ========================================================
        // RESPONSE
        // ========================================================

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Expense recorded",
                        created
                )
        );
    }

    // ============================================================
    // LIST EXPENSES
    // ============================================================

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Expense>>> list(

            @RequestParam(
                    required = false
            )
            String category,

            @RequestParam(
                    required = false
            )
            Long branchId,

            @RequestParam(
                    required = false
            )
            String from,

            @RequestParam(
                    required = false
            )
            String to,

            @RequestParam(
                    defaultValue = "0"
            )
            int page,

            @RequestParam(
                    defaultValue = "20"
            )
            int size

    ) {

        Long orgId =
                currentUserUtil
                        .getCurrentOrganizationId();

        if (orgId == null) {
            throw new IllegalStateException(
                    "Current organization could not be determined"
            );
        }

        // ========================================================
        // PAGINATION SAFETY
        // ========================================================

        if (page < 0) {
            page = 0;
        }

        if (size <= 0) {
            size = 20;
        }

        if (size > 100) {
            size = 100;
        }

        // ========================================================
        // CATEGORY
        // ========================================================

        Expense.ExpenseCategory cat = null;

        if (category != null
                && !category.isBlank()) {

            try {

                cat =
                        Expense.ExpenseCategory.valueOf(
                                category
                                        .trim()
                                        .toUpperCase()
                        );

            } catch (IllegalArgumentException ex) {

                throw new IllegalArgumentException(
                        "Invalid expense category: "
                                + category
                );
            }
        }

        // ========================================================
        // FROM DATE
        // ========================================================

        LocalDate fromDate = null;

        if (from != null && !from.isBlank()) {

            try {

                fromDate =
                        LocalDate.parse(
                                from.trim()
                        );

            } catch (Exception ex) {

                throw new IllegalArgumentException(
                        "Invalid from date. Expected format: yyyy-MM-dd"
                );
            }
        }

        // ========================================================
        // TO DATE
        // ========================================================

        LocalDate toDate = null;

        if (to != null && !to.isBlank()) {

            try {

                toDate =
                        LocalDate.parse(
                                to.trim()
                        );

            } catch (Exception ex) {

                throw new IllegalArgumentException(
                        "Invalid to date. Expected format: yyyy-MM-dd"
                );
            }
        }

        // ========================================================
        // DATE RANGE VALIDATION
        // ========================================================

        if (fromDate != null
                && toDate != null
                && fromDate.isAfter(toDate)) {

            throw new IllegalArgumentException(
                    "From date cannot be after to date"
            );
        }

        // ========================================================
        // FETCH
        // ========================================================

        Page<Expense> expenses =
                expenseService.list(
                        orgId,
                        cat,
                        branchId,
                        fromDate,
                        toDate,
                        PageRequest.of(
                                page,
                                size
                        )
                );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        expenses
                )
        );
    }

    // ============================================================
    // EXPENSE SUMMARY
    // ============================================================

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary(

            @RequestParam(
                    required = false
            )
            String from,

            @RequestParam(
                    required = false
            )
            String to

    ) {

        Long orgId =
                currentUserUtil
                        .getCurrentOrganizationId();

        if (orgId == null) {
            throw new IllegalStateException(
                    "Current organization could not be determined"
            );
        }

        LocalDate fromDate = null;
        LocalDate toDate = null;

        if (from != null && !from.isBlank()) {

            try {

                fromDate =
                        LocalDate.parse(
                                from.trim()
                        );

            } catch (Exception ex) {

                throw new IllegalArgumentException(
                        "Invalid from date. Expected format: yyyy-MM-dd"
                );
            }
        }

        if (to != null && !to.isBlank()) {

            try {

                toDate =
                        LocalDate.parse(
                                to.trim()
                        );

            } catch (Exception ex) {

                throw new IllegalArgumentException(
                        "Invalid to date. Expected format: yyyy-MM-dd"
                );
            }
        }

        return ResponseEntity.ok(
                ApiResponse.ok(
                        expenseService.summary(
                                orgId,
                                fromDate,
                                toDate
                        )
                )
        );
    }

    // ============================================================
    // GET SINGLE EXPENSE
    // ============================================================

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Expense>> get(
            @PathVariable Long id
    ) {

        Long orgId =
                currentUserUtil
                        .getCurrentOrganizationId();

        if (orgId == null) {
            throw new IllegalStateException(
                    "Current organization could not be determined"
            );
        }

        Expense expense =
                expenseService.getForOrg(
                        id,
                        orgId
                );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        expense
                )
        );
    }

    // ============================================================
    // VOID EXPENSE
    // ============================================================

    @PatchMapping("/{id}/void")
    @PreAuthorize(
            "hasAnyRole('ADMIN','ACCOUNTANT')"
    )
    public ResponseEntity<ApiResponse<Expense>> voidExpense(

            @PathVariable Long id,

            @RequestBody(
                    required = false
            )
            Map<String, String> body

    ) {

        Long orgId =
                currentUserUtil
                        .getCurrentOrganizationId();

        if (orgId == null) {
            throw new IllegalStateException(
                    "Current organization could not be determined"
            );
        }

        String reason =
                body != null
                        ? body.get("reason")
                        : null;

        Expense voided =
                expenseService.voidExpense(
                        id,
                        orgId,
                        currentUserUtil
                                .getCurrentUser()
                                .getName(),
                        reason
                );

        auditService.log(
                voided.getOrganization(),
                currentUserUtil.getCurrentUser(),
                "EXPENSE_VOIDED",
                "EXPENSE",
                String.valueOf(id),
                "Voided expense #"
                        + id
                        + (
                        reason != null
                                && !reason.isBlank()
                                ? ": " + reason
                                : ""
                ),
                null,
                null,
                "Accounting"
        );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Expense voided",
                        voided
                )
        );
    }

    // ============================================================
    // GET RECEIPT
    // ============================================================

    @GetMapping("/{id}/receipt")
    public ResponseEntity<byte[]> receipt(
            @PathVariable Long id
    ) {

        Long orgId =
                currentUserUtil
                        .getCurrentOrganizationId();

        if (orgId == null) {
            throw new IllegalStateException(
                    "Current organization could not be determined"
            );
        }

        Expense expense =
                expenseService.getForOrg(
                        id,
                        orgId
                );

        if (!expense.hasReceipt()) {

            throw new IllegalArgumentException(
                    "No receipt attached to this expense"
            );
        }

        String contentType =
                expense.getReceiptFileType() != null
                        ? expense.getReceiptFileType()
                        : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        String fileName =
                expense.getReceiptFileName() != null
                        ? expense.getReceiptFileName()
                        : "receipt";

        MediaType mediaType;

        try {

            mediaType =
                    MediaType.parseMediaType(
                            contentType
                    );

        } catch (Exception ex) {

            mediaType =
                    MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" +
                                sanitizeFileName(fileName) +
                                "\""
                )
                .body(
                        expense.getReceiptData()
                );
    }

    // ============================================================
    // FILE NAME SANITIZATION
    // ============================================================

    private String sanitizeFileName(String fileName) {

        if (fileName == null || fileName.isBlank()) {
            return "receipt";
        }

        return fileName
                .replace("\\", "_")
                .replace("/", "_")
                .replace("\"", "_")
                .replace("\r", "_")
                .replace("\n", "_");
    }
}