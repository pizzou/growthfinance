
package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.*;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.BorrowerDetailsService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/borrowers")
@RequiredArgsConstructor
public class BorrowerController {

    private final BorrowerRepository borrowerRepo;

    private final CurrentUserUtil currentUserUtil;

    private final AuditService auditService;

    /*
     * NEW:
     *
     * Provides the complete borrower 360-degree profile:
     *
     * - borrower information
     * - loans
     * - payments
     * - repayment performance
     * - overdue information
     * - credit information
     * - risk information
     */
    private final BorrowerDetailsService borrowerDetailsService;


    // ============================================================
    // LIST BORROWERS
    // ============================================================

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Borrower>>> list(
            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size,

            @RequestParam(required = false)
            String q
    ) {

        Organization org =
                currentUserUtil
                        .getCurrentUser()
                        .getOrganization();


        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                "createdAt"
                        ).descending()
                );


        Page<Borrower> result;


        if (
                q != null
                        &&
                !q.isBlank()
        ) {

            result =
                    borrowerRepo.search(
                            org,
                            q,
                            pageable
                    );

        } else {

            result =
                    borrowerRepo.findByOrganization(
                            org,
                            pageable
                    );
        }


        return ResponseEntity.ok(
                ApiResponse.ok(
                        result
                )
        );
    }


    // ============================================================
    // GET BASIC BORROWER
    // ============================================================

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Borrower>> get(
            @PathVariable Long id
    ) {

        Organization org =
                currentUserUtil
                        .getCurrentUser()
                        .getOrganization();


        Borrower borrower =
                borrowerRepo
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new RuntimeException(
                                                "Borrower not found: "
                                                        + id
                                        )
                        );


        /*
         * MULTI-TENANT SECURITY
         *
         * A borrower belonging to another organization
         * must never be accessible.
         */

        if (
                borrower.getOrganization() == null
                        ||
                borrower.getOrganization().getId() == null
                        ||
                !borrower
                        .getOrganization()
                        .getId()
                        .equals(
                                org.getId()
                        )
        ) {

            throw new RuntimeException(
                    "Access denied"
            );
        }


        return ResponseEntity.ok(
                ApiResponse.ok(
                        borrower
                )
        );
    }


    // ============================================================
    // NEW: BORROWER 360 DETAILS
    // ============================================================

    @GetMapping("/{id}/details")
    public ResponseEntity<
            ApiResponse<BorrowerDetailsResponse>
            > details(
                    @PathVariable Long id
            ) {

        Organization org =
                currentUserUtil
                        .getCurrentUser()
                        .getOrganization();


        /*
         * The service performs the complete borrower
         * financial analysis.
         *
         * The organization ID is passed explicitly so
         * tenant isolation remains enforced.
         */

        BorrowerDetailsResponse response =
                borrowerDetailsService
                        .getBorrowerDetails(
                                id,
                                org.getId()
                        );


        return ResponseEntity.ok(
                ApiResponse.ok(
                        response
                )
        );
    }


    // ============================================================
    // CREATE BORROWER
    // ============================================================

    @PostMapping
    public ResponseEntity<ApiResponse<Borrower>> create(
            @Valid
            @RequestBody
            BorrowerRequest req
    ) {

        Organization org =
                currentUserUtil
                        .getCurrentUser()
                        .getOrganization();


        /*
         * Prevent duplicate email addresses
         * inside the same organization.
         */

        if (
                req.getEmail() != null
                        &&
                borrowerRepo.existsByEmailAndOrganization(
                        req.getEmail(),
                        org
                )
        ) {

            throw new RuntimeException(
                    "Email already registered: "
                            + req.getEmail()
            );
        }


        Borrower borrower =
                Borrower.builder()

                        // ------------------------------------------------
                        // ORGANIZATION
                        // ------------------------------------------------

                        .organization(
                                org
                        )

                        // ------------------------------------------------
                        // PERSONAL
                        // ------------------------------------------------

                        .firstName(
                                req.getFirstName()
                        )

                        .lastName(
                                req.getLastName()
                        )

                        .email(
                                req.getEmail()
                        )

                        .phone(
                                req.getPhone()
                        )

                        .alternatePhone(
                                req.getAlternatePhone()
                        )

                        .nationalId(
                                req.getNationalId()
                        )

                        .passportNumber(
                                req.getPassportNumber()
                        )

                        .taxIdentificationNumber(
                                req.getTaxIdentificationNumber()
                        )

                        .dateOfBirth(
                                req.getDateOfBirth() != null
                                        ? LocalDate.parse(
                                                req.getDateOfBirth()
                                        )
                                        : null
                        )

                        .gender(
                                req.getGender()
                        )

                        .maritalStatus(
                                req.getMaritalStatus()
                        )

                        .nationality(
                                req.getNationality()
                        )

                        // ------------------------------------------------
                        // ADDRESS
                        // ------------------------------------------------

                        .addressLine1(
                                req.getAddressLine1()
                        )

                        .addressLine2(
                                req.getAddressLine2()
                        )

                        .city(
                                req.getCity()
                        )

                        .stateProvince(
                                req.getStateProvince()
                        )

                        .postalCode(
                                req.getPostalCode()
                        )

                        .country(
                                req.getCountry()
                        )

                        // ------------------------------------------------
                        // EMPLOYMENT
                        // ------------------------------------------------

                        .employerName(
                                req.getEmployerName()
                        )

                        .employmentType(
                                req.getEmploymentType()
                        )

                        .jobTitle(
                                req.getJobTitle()
                        )

                        // ------------------------------------------------
                        // FINANCIAL
                        // ------------------------------------------------

                        .monthlyIncome(
                                req.getMonthlyIncome()
                        )

                        .monthlyExpenses(
                                req.getMonthlyExpenses()
                        )

                        .netWorth(
                                req.getNetWorth()
                        )

                        // ------------------------------------------------
                        // CREDIT
                        // ------------------------------------------------

                        .creditScore(
                                req.getCreditScore()
                        )

                        .creditBureau(
                                req.getCreditBureau()
                        )

                        // ------------------------------------------------
                        // BANK
                        // ------------------------------------------------

                        .bankName(
                                req.getBankName()
                        )

                        .bankAccountNumber(
                                req.getBankAccountNumber()
                        )

                        .bankBranch(
                                req.getBankBranch()
                        )

                        // ------------------------------------------------
                        // STATUS
                        // ------------------------------------------------

                        .status(
                                Borrower.BorrowerStatus.ACTIVE
                        )

                        .build();


        Borrower saved =
                borrowerRepo.save(
                        borrower
                );


        // ========================================================
        // AUDIT
        // ========================================================

        auditService.log(
                org,
                currentUserUtil
                        .getCurrentUser(),

                "BORROWER_CREATED",

                "BORROWER",

                String.valueOf(
                        saved.getId()
                ),

                "Created borrower "
                        + saved.getFirstName()
                        + " "
                        + saved.getLastName()
        );


        return ResponseEntity
                .status(
                        HttpStatus.CREATED
                )
                .body(
                        ApiResponse.ok(
                                "Borrower created",
                                saved
                        )
                );
    }


    // ============================================================
    // UPDATE BORROWER
    // ============================================================

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Borrower>> update(
            @PathVariable Long id,

            @RequestBody
            BorrowerRequest req
    ) {

        Organization org =
                currentUserUtil
                        .getCurrentUser()
                        .getOrganization();


        Borrower borrower =
                borrowerRepo
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new RuntimeException(
                                                "Borrower not found"
                                        )
                        );


        /*
         * MULTI-TENANT SECURITY
         */

        if (
                borrower.getOrganization() == null
                        ||
                borrower
                        .getOrganization()
                        .getId() == null
                        ||
                !borrower
                        .getOrganization()
                        .getId()
                        .equals(
                                org.getId()
                        )
        ) {

            throw new RuntimeException(
                    "Access denied"
            );
        }


        // ========================================================
        // UPDATE BASIC INFORMATION
        // ========================================================

        if (
                req.getFirstName() != null
        ) {

            borrower.setFirstName(
                    req.getFirstName()
            );
        }


        if (
                req.getLastName() != null
        ) {

            borrower.setLastName(
                    req.getLastName()
            );
        }


        if (
                req.getPhone() != null
        ) {

            borrower.setPhone(
                    req.getPhone()
            );
        }


        // ========================================================
        // UPDATE FINANCIAL INFORMATION
        // ========================================================

        if (
                req.getMonthlyIncome() != null
        ) {

            borrower.setMonthlyIncome(
                    req.getMonthlyIncome()
            );
        }


        if (
                req.getCreditScore() != null
        ) {

            borrower.setCreditScore(
                    req.getCreditScore()
            );
        }


        // ========================================================
        // UPDATE EMPLOYMENT
        // ========================================================

        if (
                req.getEmployerName() != null
        ) {

            borrower.setEmployerName(
                    req.getEmployerName()
            );
        }


        if (
                req.getEmploymentType() != null
        ) {

            borrower.setEmploymentType(
                    req.getEmploymentType()
            );
        }


        // ========================================================
        // SAVE
        // ========================================================

        Borrower saved =
                borrowerRepo.save(
                        borrower
                );


        // ========================================================
        // AUDIT
        // ========================================================

        auditService.log(
                org,
                currentUserUtil
                        .getCurrentUser(),

                "BORROWER_UPDATED",

                "BORROWER",

                String.valueOf(
                        saved.getId()
                ),

                "Updated borrower "
                        + saved.getFirstName()
                        + " "
                        + saved.getLastName()
        );


        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Borrower updated",
                        saved
                )
        );
    }
}
