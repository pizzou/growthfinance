
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
            Double amount,

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

        Organization org =
                orgRepo.findById(
                        currentUserUtil
                                .getCurrentOrganizationId()
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Organization not found"
                        )
                );


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
        // CREATE
        // ========================================================

        Expense created =
                expenseService.create(

                        org,

                        LocalDate.parse(
                                expenseDate
                        ),

                        expenseCategory,

                        amount,

                        paymentAccountId,

                        branchId,

                        description,

                        currentUserUtil
                                .getCurrentUser()
                                .getName(),

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


        if (page < 0) {
            page = 0;
        }

        if (size <= 0) {
            size = 20;
        }

        if (size > 100) {
            size = 100;
        }


        Expense.ExpenseCategory cat =
                null;

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


        LocalDate fromDate =
                from != null
                        && !from.isBlank()
                        ? LocalDate.parse(from)
                        : null;


        LocalDate toDate =
                to != null
                        && !to.isBlank()
                        ? LocalDate.parse(to)
                        : null;


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


        LocalDate fromDate =
                from != null
                        && !from.isBlank()
                        ? LocalDate.parse(from)
                        : LocalDate.now()
                                .withDayOfMonth(1);


        LocalDate toDate =
                to != null
                        && !to.isBlank()
                        ? LocalDate.parse(to)
                        : LocalDate.now();


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


        Expense expense =
                expenseService.getForOrg(
                        id,
                        orgId
                );


        if (!expense.hasReceipt()) {

            throw new RuntimeException(
                    "No receipt attached to this expense"
            );
        }


        String contentType =
                expense.getReceiptFileType() != null
                        ? expense.getReceiptFileType()
                        : "application/octet-stream";


        String fileName =
                expense.getReceiptFileName() != null
                        ? expense.getReceiptFileName()
                        : "receipt";


        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                contentType
                        )
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\""
                                + fileName
                                + "\""
                )
                .body(
                        expense.getReceiptData()
                );
    }
}
