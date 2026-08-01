
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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Expense>> create(

            @RequestParam("expenseDate")
            String expenseDate,

            @RequestParam("category")
            String category,

            @RequestParam("amount")
            Double amount,

            @RequestParam("paymentAccountId")
            Long paymentAccountId,

            @RequestParam(value = "branchId", required = false)
            Long branchId,

            @RequestParam(value = "description", required = false)
            String description,

            /*
             * Payment method:
             *
             * BANK_ACCOUNT
             * CASH
             * MOBILE_MONEY
             * MOMO_PAY
             * CARD
             * CHEQUE
             * OTHER
             */
            @RequestParam(value = "paymentMethod", required = false)
            String paymentMethod,

            /*
             * Payment provider:
             *
             * MTN
             * AIRTEL
             * BANK NAME
             * VISA
             * MASTERCARD
             * etc.
             */
            @RequestParam(value = "paymentProvider", required = false)
            String paymentProvider,

            /*
             * Phone number used for Mobile Money / MoMo Pay.
             */
            @RequestParam(value = "paymentPhoneNumber", required = false)
            String paymentPhoneNumber,

            /*
             * Mobile Money / MoMo Pay transaction reference.
             */
            @RequestParam(value = "paymentTransactionReference", required = false)
            String paymentTransactionReference,

            /*
             * MoMo Pay merchant/reference code.
             */
            @RequestParam(value = "paymentCode", required = false)
            String paymentCode,

            /*
             * Card information.
             *
             * We do NOT store the full card number.
             */
            @RequestParam(value = "cardBrand", required = false)
            String cardBrand,

            @RequestParam(value = "cardLastFour", required = false)
            String cardLastFour,

            @RequestParam(value = "cardAuthorizationCode", required = false)
            String cardAuthorizationCode,

            /*
             * Cheque information.
             */
            @RequestParam(value = "chequeNumber", required = false)
            String chequeNumber,

            /*
             * Additional payment information.
             */
            @RequestParam(value = "paymentNotes", required = false)
            String paymentNotes,

            /*
             * Receipt / supporting document.
             */
            @RequestParam(value = "receipt", required = false)
            MultipartFile receipt

    ) throws Exception {

        // ------------------------------------------------------------
        // Get current organization
        // ------------------------------------------------------------

        Organization org = orgRepo
                .findById(currentUserUtil.getCurrentOrganizationId())
                .orElseThrow(() ->
                        new RuntimeException("Organization not found")
                );


        // ------------------------------------------------------------
        // Convert payment method
        // ------------------------------------------------------------

        Expense.PaymentMethod method = null;

        if (paymentMethod != null && !paymentMethod.isBlank()) {
            try {
                method = Expense.PaymentMethod.valueOf(
                        paymentMethod.trim().toUpperCase()
                );
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Invalid payment method: " + paymentMethod
                );
            }
        }


        // ------------------------------------------------------------
        // Convert expense category
        // ------------------------------------------------------------

        Expense.ExpenseCategory expenseCategory;

        try {
            expenseCategory = Expense.ExpenseCategory.valueOf(
                    category.trim().toUpperCase()
            );
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid expense category: " + category
            );
        }


        // ------------------------------------------------------------
        // Create expense
        //
        // IMPORTANT:
        // The order here MUST exactly match ExpenseService.create()
        // ------------------------------------------------------------

        Expense created = expenseService.create(

                // 1
                org,

                // 2
                LocalDate.parse(expenseDate),

                // 3
                expenseCategory,

                // 4
                amount,

                // 5
                paymentAccountId,

                // 6
                branchId,

                // 7
                description,

                // 8
                currentUserUtil.getCurrentUser().getName(),

                // 9
                method,

                // 10
                paymentProvider,

                // 11
                paymentPhoneNumber,

                // 12
                paymentTransactionReference,

                // 13
                paymentCode,

                // 14
                cardBrand,

                // 15
                cardLastFour,

                // 16
                cardAuthorizationCode,

                // 17
                chequeNumber,

                // 18
                paymentNotes,

                // 19
                receipt
        );


        // ------------------------------------------------------------
        // Audit
        // ------------------------------------------------------------

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

            @RequestParam(required = false)
            String category,

            @RequestParam(required = false)
            Long branchId,

            @RequestParam(required = false)
            String from,

            @RequestParam(required = false)
            String to,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size

    ) {

        Long orgId =
                currentUserUtil.getCurrentOrganizationId();


        Expense.ExpenseCategory cat = null;

        if (category != null && !category.isBlank()) {
            try {
                cat = Expense.ExpenseCategory.valueOf(
                        category.trim().toUpperCase()
                );
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Invalid expense category: " + category
                );
            }
        }


        LocalDate fromDate =
                from != null && !from.isBlank()
                        ? LocalDate.parse(from)
                        : null;

        LocalDate toDate =
                to != null && !to.isBlank()
                        ? LocalDate.parse(to)
                        : null;


        return ResponseEntity.ok(
                ApiResponse.ok(
                        expenseService.list(
                                orgId,
                                cat,
                                branchId,
                                fromDate,
                                toDate,
                                PageRequest.of(page, size)
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
                currentUserUtil.getCurrentOrganizationId();

        return ResponseEntity.ok(
                ApiResponse.ok(
                        expenseService.getForOrg(id, orgId)
                )
        );
    }


    // ============================================================
    // EXPENSE SUMMARY
    // ============================================================

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary(

            @RequestParam(required = false)
            String from,

            @RequestParam(required = false)
            String to

    ) {

        Long orgId =
                currentUserUtil.getCurrentOrganizationId();


        LocalDate fromDate =
                from != null && !from.isBlank()
                        ? LocalDate.parse(from)
                        : LocalDate.now().withDayOfMonth(1);


        LocalDate toDate =
                to != null && !to.isBlank()
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
    // VOID EXPENSE
    // ============================================================

    @PatchMapping("/{id}/void")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<Expense>> voidExpense(

            @PathVariable Long id,

            @RequestBody(required = false)
            Map<String, String> body

    ) {

        Long orgId =
                currentUserUtil.getCurrentOrganizationId();


        String reason =
                body != null
                        ? body.get("reason")
                        : null;


        Expense voided =
                expenseService.voidExpense(
                        id,
                        orgId,
                        currentUserUtil.getCurrentUser().getName(),
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
                            reason != null && !reason.isBlank()
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
                currentUserUtil.getCurrentOrganizationId();


        Expense expense =
                expenseService.getForOrg(id, orgId);


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
                        MediaType.parseMediaType(contentType)
                )

                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + fileName + "\""
                )

                .body(
                        expense.getReceiptData()
                );
    }
}
