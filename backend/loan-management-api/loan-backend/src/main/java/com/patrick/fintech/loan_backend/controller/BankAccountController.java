package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.BankAccount;
import com.patrick.fintech.loan_backend.model.Branch;
import com.patrick.fintech.loan_backend.model.JournalEntry;
import com.patrick.fintech.loan_backend.model.Organization;
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


    
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<BankAccount>> create(
            @RequestBody Map<String, Object> body
    ) {

        Long orgId =
                currentUserUtil.getCurrentOrganizationId();

        Organization org =
                orgRepo.findById(orgId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Organization not found: " + orgId
                                )
                        );

        final Long branchId =
                body.get("branchId") != null
                        && !body.get("branchId").toString().trim().isEmpty()
                        ? Long.valueOf(
                                body.get("branchId").toString()
                        )
                        : null;


        /*
         * Branch is optional.
         */
        final Branch branch;

        if (branchId != null) {

            branch =
                    branchRepo.findById(branchId)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Branch not found: " + branchId
                                    )
                            );

            if (branch.getOrganization() != null
                    && branch.getOrganization().getId() != null
                    && !branch.getOrganization()
                             .getId()
                             .equals(orgId)) {

                throw new IllegalArgumentException(
                        "Branch does not belong to the current organization"
                );
            }

        } else {

            branch = null;
        }


        // ========================================================
        // READ REQUEST DATA
        // ========================================================

        String name =
                body.get("name") != null
                        ? body.get("name").toString().trim()
                        : null;

        String accountType =
                body.get("accountType") != null
                        ? body.get("accountType").toString().trim().toUpperCase()
                        : null;

        String bankName =
                body.get("bankName") != null
                        ? body.get("bankName").toString().trim()
                        : null;

        String accountNumber =
                body.get("accountNumber") != null
                        ? body.get("accountNumber").toString().trim()
                        : null;


        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Account name is required"
            );
        }

        if (!"CASH".equals(accountType)
                && !"BANK".equals(accountType)) {

            throw new IllegalArgumentException(
                    "accountType must be CASH or BANK"
            );
        }


        double openingBalance = 0.0;

        if (body.get("openingBalance") != null
                && !body.get("openingBalance").toString().trim().isEmpty()) {

            try {

                openingBalance =
                        Double.parseDouble(
                                body.get("openingBalance")
                                        .toString()
                                        .trim()
                        );

            } catch (NumberFormatException ex) {

                throw new IllegalArgumentException(
                        "openingBalance must be a valid number"
                );
            }
        }


        if (openingBalance < 0) {
            throw new IllegalArgumentException(
                    "Opening balance cannot be negative"
            );
        }


        // ========================================================
        // CREATE
        // ========================================================

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


    @GetMapping 
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list() { 
        Long orgId = 
        currentUserUtil.getCurrentOrganizationId();
         return ResponseEntity.ok( 
            ApiResponse.ok( 
                bankAccountService.listForApi(orgId) 
            
            ) ); }
    
    @PostMapping("/{id}/transactions")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','MANAGER')")
    public ResponseEntity<ApiResponse<JournalEntry>> recordTransaction(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {

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
                        ? body.get("type").toString().trim().toUpperCase()
                        : null;

        if (!"DEPOSIT".equals(type)
                && !"WITHDRAWAL".equals(type)) {

            throw new IllegalArgumentException(
                    "type must be DEPOSIT or WITHDRAWAL"
            );
        }


        if (body.get("amount") == null) {
            throw new IllegalArgumentException(
                    "amount is required"
            );
        }


        double amount;

        try {

            amount =
                    Double.parseDouble(
                            body.get("amount")
                                    .toString()
                    );

        } catch (NumberFormatException ex) {

            throw new IllegalArgumentException(
                    "amount must be a valid number"
            );
        }


        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be positive"
            );
        }


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
                    );

        } catch (NumberFormatException ex) {

            throw new IllegalArgumentException(
                    "counterAccountId must be a valid ID"
            );
        }


        String description =
                body.get("description") != null
                        ? body.get("description").toString()
                        : type + " on bank account " + id;


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
    // TRANSFER
    // ============================================================

    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','MANAGER')")
    public ResponseEntity<ApiResponse<JournalEntry>> transfer(
            @RequestBody Map<String, Object> body
    ) {

        Long orgId =
                currentUserUtil.getCurrentOrganizationId();

        Organization org =
                orgRepo.findById(orgId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Organization not found: " + orgId
                                )
                        );


        if (body.get("fromAccountId") == null) {
            throw new IllegalArgumentException(
                    "fromAccountId is required"
            );
        }

        if (body.get("toAccountId") == null) {
            throw new IllegalArgumentException(
                    "toAccountId is required"
            );
        }

        if (body.get("amount") == null) {
            throw new IllegalArgumentException(
                    "amount is required"
            );
        }


        Long fromId =
                Long.valueOf(
                        body.get("fromAccountId").toString()
                );

        Long toId =
                Long.valueOf(
                        body.get("toAccountId").toString()
                );


        double amount =
                Double.parseDouble(
                        body.get("amount").toString()
                );


        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be positive"
            );
        }


        String description =
                body.get("description") != null
                        ? body.get("description").toString()
                        : "Internal transfer";


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
