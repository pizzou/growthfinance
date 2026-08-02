
package com.patrick.fintech.loan_backend.controller;
import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.BranchRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.BankAccountService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bank-accounts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','ACCOUNTANT')")
public class BankAccountController {

    private final BankAccountService bankAccountService;
    private final OrganizationRepository orgRepo;
    private final BranchRepository branchRepo;
    private final CurrentUserUtil currentUserUtil;
    private final AuditService auditService;

    // ============================================================
    // LIST BANK / CASH ACCOUNTS
    // ============================================================

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list() {

        Long orgId = currentUserUtil.getCurrentOrganizationId();

        List<Map<String, Object>> out =
                bankAccountService.list(orgId)
                        .stream()
                        .map(a -> {

                            Map<String, Object> m =
                                    new LinkedHashMap<>();

                            m.put("id", a.getId());
                            m.put("name", a.getName());
                            m.put("accountType", a.getAccountType());
                            m.put("bankName", a.getBankName());
                            m.put("accountNumber", a.getAccountNumber());

                            m.put(
                                    "branchName",
                                    a.getBranch() != null
                                            ? a.getBranch().getName()
                                            : null
                            );

                            m.put(
                                    "glAccountCode",
                                    a.getGlAccount() != null
                                            ? a.getGlAccount().getCode()
                                            : null
                            );

                            m.put("active", a.getActive());

                            m.put(
                                    "balance",
                                    bankAccountService.getBalance(a)
                            );

                            return m;

                        })
                        .toList();

        return ResponseEntity.ok(
                ApiResponse.ok(out)
        );
    }


    // ============================================================
    // CREATE BANK / CASH ACCOUNT
    // ============================================================

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<BankAccount>> create(
            @RequestBody Map<String, Object> body) {

        Long orgId =
                currentUserUtil.getCurrentOrganizationId();

        Organization org =
                orgRepo.findById(orgId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Organization not found: " + orgId
                                )
                        );


        // --------------------------------------------------------
        // BRANCH
        // --------------------------------------------------------

        Long branchId = null;

        if (body.get("branchId") != null) {

            String branchValue =
                    body.get("branchId").toString().trim();

            if (!branchValue.isEmpty()) {

                try {

                    branchId =
                            Long.valueOf(branchValue);

                } catch (NumberFormatException ex) {

                    throw new IllegalArgumentException(
                            "Invalid branchId: " + branchValue
                    );
                }
            }
        }

        Branch branch = null;

        if (branchId != null) {

            /*
             * IMPORTANT:
             *
             * branchId is copied into a final variable before
             * being referenced by the lambda.
             */
            final Long finalBranchId = branchId;

            branch =
                    branchRepo.findById(finalBranchId)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Branch not found: "
                                                    + finalBranchId
                                    )
                            );

            /*
             * Prevent a user from assigning another
             * organization's branch to this account.
             */
            if (branch.getOrganization() == null
                    || branch.getOrganization().getId() == null
                    || !branch.getOrganization()
                            .getId()
                            .equals(orgId)) {

                throw new IllegalArgumentException(
                        "Branch does not belong to the current organization"
                );
            }
        }


        // --------------------------------------------------------
        // BASIC FIELDS
        // --------------------------------------------------------

        String name =
                body.get("name") != null
                        ? body.get("name").toString().trim()
                        : null;

        String accountType =
                body.get("accountType") != null
                        ? body.get("accountType")
                                .toString()
                                .trim()
                                .toUpperCase()
                        : null;

        String bankName =
                body.get("bankName") != null
                        ? body.get("bankName")
                                .toString()
                                .trim()
                        : null;

        String accountNumber =
                body.get("accountNumber") != null
                        ? body.get("accountNumber")
                                .toString()
                                .trim()
                        : null;


        // --------------------------------------------------------
        // VALIDATION
        // --------------------------------------------------------

        if (name == null || name.isBlank()) {

            throw new IllegalArgumentException(
                    "Account name is required"
            );
        }

        if (accountType == null || accountType.isBlank()) {

            throw new IllegalArgumentException(
                    "Account type is required"
            );
        }

        if (!"CASH".equals(accountType)
                && !"BANK".equals(accountType)) {

            throw new IllegalArgumentException(
                    "accountType must be CASH or BANK"
            );
        }


        // --------------------------------------------------------
        // OPENING BALANCE
        // --------------------------------------------------------

        double openingBalance = 0.0;

        if (body.get("openingBalance") != null) {

            String openingValue =
                    body.get("openingBalance")
                            .toString()
                            .trim();

            if (!openingValue.isEmpty()) {

                try {

                    openingBalance =
                            Double.parseDouble(openingValue);

                } catch (NumberFormatException ex) {

                    throw new IllegalArgumentException(
                            "Invalid openingBalance: "
                                    + openingValue
                    );
                }
            }
        }

        if (openingBalance < 0) {

            throw new IllegalArgumentException(
                    "Opening balance cannot be negative"
            );
        }


        // --------------------------------------------------------
        // CREATE ACCOUNT
        // --------------------------------------------------------

        BankAccount created =
                bankAccountService.create(
                        org,
                        branch,
                        name,
                        accountType,
                        bankName,
                        accountNumber,
                        openingBalance,
                        currentUserUtil
                                .getCurrentUser()
                                .getName()
                );


        // --------------------------------------------------------
        // AUDIT
        // --------------------------------------------------------

        auditService.log(
                org,
                currentUserUtil.getCurrentUser(),
                "BANK_ACCOUNT_CREATED",
                "BANK_ACCOUNT",
                String.valueOf(created.getId()),
                "Created "
                        + created.getAccountType()
                        + " account: "
                        + created.getName(),
                null,
                null,
                "Cashbook & Banking"
        );


        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Account created",
                        created
                )
        );
    }


    // ============================================================
    // RECORD DEPOSIT / WITHDRAWAL
    // ============================================================

    @PostMapping("/{id}/transactions")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','MANAGER')")
    public ResponseEntity<ApiResponse<JournalEntry>> recordTransaction(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        Long orgId =
                currentUserUtil.getCurrentOrganizationId();

        Organization org =
                orgRepo.findById(orgId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Organization not found: " + orgId
                                )
                        );

        String type =
                body.get("type") != null
                        ? body.get("type")
                                .toString()
                                .trim()
                                .toUpperCase()
                        : null;

        if (type == null || type.isBlank()) {

            throw new IllegalArgumentException(
                    "Transaction type is required"
            );
        }

        if (!"DEPOSIT".equals(type)
                && !"WITHDRAWAL".equals(type)) {

            throw new IllegalArgumentException(
                    "type must be DEPOSIT or WITHDRAWAL"
            );
        }


        // --------------------------------------------------------
        // AMOUNT
        // --------------------------------------------------------

        if (body.get("amount") == null) {

            throw new IllegalArgumentException(
                    "Amount is required"
            );
        }

        double amount;

        try {

            amount =
                    Double.parseDouble(
                            body.get("amount")
                                    .toString()
                                    .trim()
                    );

        } catch (NumberFormatException ex) {

            throw new IllegalArgumentException(
                    "Invalid amount"
            );
        }

        if (amount <= 0) {

            throw new IllegalArgumentException(
                    "Amount must be positive"
            );
        }


        // --------------------------------------------------------
        // COUNTER ACCOUNT
        // --------------------------------------------------------

        if (body.get("counterAccountId") == null) {

            throw new IllegalArgumentException(
                    "counterAccountId is required"
            );
        }

        Long counterAccountId;

        try {

            counterAccountId =
                    Long.valueOf(
                            body.get("counterAccountId")
                                    .toString()
                                    .trim()
                    );

        } catch (NumberFormatException ex) {

            throw new IllegalArgumentException(
                    "Invalid counterAccountId"
            );
        }


        // --------------------------------------------------------
        // DESCRIPTION
        // --------------------------------------------------------

        String description =
                body.get("description") != null
                        ? body.get("description")
                                .toString()
                                .trim()
                        : type + " on bank account " + id;

        if (description.isBlank()) {

            description =
                    type + " on bank account " + id;
        }


        // --------------------------------------------------------
        // RECORD TRANSACTION
        // --------------------------------------------------------

        JournalEntry entry =
                bankAccountService.recordTransaction(
                        org,
                        id,
                        type,
                        amount,
                        counterAccountId,
                        description,
                        currentUserUtil
                                .getCurrentUser()
                                .getName()
                );


        // --------------------------------------------------------
        // AUDIT
        // --------------------------------------------------------

        auditService.log(
                org,
                currentUserUtil.getCurrentUser(),
                "CASHBOOK_" + type,
                "BANK_ACCOUNT",
                String.valueOf(id),
                description + " (" + amount + ")",
                null,
                null,
                "Cashbook & Banking"
        );


        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Transaction recorded",
                        entry
                )
        );
    }


    // ============================================================
    // TRANSFER BETWEEN BANK / CASH ACCOUNTS
    // ============================================================

    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','MANAGER')")
    public ResponseEntity<ApiResponse<JournalEntry>> transfer(
            @RequestBody Map<String, Object> body) {

        Long orgId =
                currentUserUtil.getCurrentOrganizationId();

        Organization org =
                orgRepo.findById(orgId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Organization not found: " + orgId
                                )
                        );


        // --------------------------------------------------------
        // FROM ACCOUNT
        // --------------------------------------------------------

        if (body.get("fromAccountId") == null) {

            throw new IllegalArgumentException(
                    "fromAccountId is required"
            );
        }

        Long fromId;

        try {

            fromId =
                    Long.valueOf(
                            body.get("fromAccountId")
                                    .toString()
                                    .trim()
                    );

        } catch (NumberFormatException ex) {

            throw new IllegalArgumentException(
                    "Invalid fromAccountId"
            );
        }


        // --------------------------------------------------------
        // TO ACCOUNT
        // --------------------------------------------------------

        if (body.get("toAccountId") == null) {

            throw new IllegalArgumentException(
                    "toAccountId is required"
            );
        }

        Long toId;

        try {

            toId =
                    Long.valueOf(
                            body.get("toAccountId")
                                    .toString()
                                    .trim()
                    );

        } catch (NumberFormatException ex) {

            throw new IllegalArgumentException(
                    "Invalid toAccountId"
            );
        }


        // --------------------------------------------------------
        // AMOUNT
        // --------------------------------------------------------

        if (body.get("amount") == null) {

            throw new IllegalArgumentException(
                    "Amount is required"
            );
        }

        double amount;

        try {

            amount =
                    Double.parseDouble(
                            body.get("amount")
                                    .toString()
                                    .trim()
                    );

        } catch (NumberFormatException ex) {

            throw new IllegalArgumentException(
                    "Invalid amount"
            );
        }

        if (amount <= 0) {

            throw new IllegalArgumentException(
                    "Amount must be positive"
            );
        }


        // --------------------------------------------------------
        // DESCRIPTION
        // --------------------------------------------------------

        String description =
                body.get("description") != null
                        ? body.get("description")
                                .toString()
                                .trim()
                        : "Internal transfer";


        // --------------------------------------------------------
        // TRANSFER
        // --------------------------------------------------------

        JournalEntry entry =
                bankAccountService.transfer(
                        org,
                        fromId,
                        toId,
                        amount,
                        description,
                        currentUserUtil
                                .getCurrentUser()
                                .getName()
                );


        // --------------------------------------------------------
        // AUDIT
        // --------------------------------------------------------

        auditService.log(
                org,
                currentUserUtil.getCurrentUser(),
                "CASHBOOK_TRANSFER",
                "BANK_ACCOUNT",
                fromId + "->" + toId,
                "Transferred "
                        + amount
                        + " between accounts",
                null,
                null,
                "Cashbook & Banking"
        );


        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Transfer complete",
                        entry
                )
        );
    }
}
