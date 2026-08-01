package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.BorrowerRequest;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
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


    // ================================================================
    // GET ALL BORROWERS
    // ================================================================

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Borrower>>> list(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size,

            @RequestParam(required = false)
            String q) {


        Organization org =
                currentUserUtil
                        .getCurrentUser()
                        .getOrganization();


        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by("createdAt").descending()
                );


        Page<Borrower> result;


        if (q != null && !q.isBlank()) {

            result =
                    borrowerRepo.search(
                            org,
                            q.trim(),
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
                ApiResponse.ok(result)
        );
    }


    // ================================================================
    // GET SINGLE BORROWER
    // ================================================================

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Borrower>> get(
            @PathVariable Long id) {


        Organization org =
                currentUserUtil
                        .getCurrentUser()
                        .getOrganization();


        Borrower borrower =
                borrowerRepo
                        .findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Borrower not found: " + id
                                )
                        );


        // ------------------------------------------------------------
        // ORGANIZATION SECURITY
        // ------------------------------------------------------------

        if (borrower.getOrganization() == null
                || borrower.getOrganization().getId() == null
                || !borrower.getOrganization()
                        .getId()
                        .equals(org.getId())) {

            throw new RuntimeException(
                    "Access denied"
            );
        }


        return ResponseEntity.ok(
                ApiResponse.ok(borrower)
        );
    }


    // ================================================================
    // CREATE BORROWER
    // ================================================================

    @PostMapping
    public ResponseEntity<ApiResponse<Borrower>> create(
            @Valid
            @RequestBody
            BorrowerRequest req) {


        Organization org =
                currentUserUtil
                        .getCurrentUser()
                        .getOrganization();


        // ------------------------------------------------------------
        // EMAIL DUPLICATE CHECK
        // ------------------------------------------------------------

        if (req.getEmail() != null
                && !req.getEmail().isBlank()
                && borrowerRepo.existsByEmailAndOrganization(
                        req.getEmail(),
                        org)) {

            throw new RuntimeException(
                    "Email already registered: "
                            + req.getEmail()
            );
        }


        // ------------------------------------------------------------
        // DATE OF BIRTH
        // ------------------------------------------------------------

        LocalDate dateOfBirth = null;

        if (req.getDateOfBirth() != null
                && !req.getDateOfBirth().isBlank()) {

            try {

                dateOfBirth =
                        LocalDate.parse(
                                req.getDateOfBirth()
                        );

            } catch (Exception e) {

                throw new RuntimeException(
                        "Invalid date of birth. "
                                + "Expected format: yyyy-MM-dd"
                );
            }
        }


        // ------------------------------------------------------------
        // CREATE BORROWER
        // ------------------------------------------------------------

        Borrower borrower =
                Borrower.builder()

                        // ------------------------------------------------
                        // ORGANIZATION
                        // ------------------------------------------------

                        .organization(org)


                        // ------------------------------------------------
                        // PERSONAL INFORMATION
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
                                dateOfBirth
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
                        // CREDIT INFORMATION
                        // ------------------------------------------------

                        .creditScore(
                                req.getCreditScore()
                        )

                        .creditBureau(
                                req.getCreditBureau()
                        )


                        // ------------------------------------------------
                        // BANK INFORMATION
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


        // ------------------------------------------------------------
        // SAVE
        // ------------------------------------------------------------

        Borrower saved =
                borrowerRepo.save(
                        borrower
                );


        // ------------------------------------------------------------
        // AUDIT
        // ------------------------------------------------------------

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
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.ok(
                                "Borrower created",
                                saved
                        )
                );
    }


    // ================================================================
    // UPDATE BORROWER
    // ================================================================

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Borrower>> update(

            @PathVariable
            Long id,

            @Valid
            @RequestBody
            BorrowerRequest req) {


        Organization org =
                currentUserUtil
                        .getCurrentUser()
                        .getOrganization();


        // ------------------------------------------------------------
        // FIND BORROWER
        // ------------------------------------------------------------

        Borrower borrower =
                borrowerRepo
                        .findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Borrower not found: "
                                                + id
                                )
                        );


        // ------------------------------------------------------------
        // ORGANIZATION SECURITY
        // ------------------------------------------------------------

        if (borrower.getOrganization() == null
                || borrower.getOrganization().getId() == null
                || !borrower.getOrganization()
                        .getId()
                        .equals(org.getId())) {

            throw new RuntimeException(
                    "Access denied"
            );
        }


        // ------------------------------------------------------------
        // BASIC INFORMATION
        // ------------------------------------------------------------

        if (req.getFirstName() != null) {

            borrower.setFirstName(
                    req.getFirstName()
            );
        }


        if (req.getLastName() != null) {

            borrower.setLastName(
                    req.getLastName()
            );
        }


        if (req.getPhone() != null) {

            borrower.setPhone(
                    req.getPhone()
            );
        }


        if (req.getAlternatePhone() != null) {

            borrower.setAlternatePhone(
                    req.getAlternatePhone()
            );
        }


        if (req.getEmail() != null) {

            String email =
                    req.getEmail().trim();


            if (!email.equalsIgnoreCase(
                    borrower.getEmail() != null
                            ? borrower.getEmail()
                            : ""
            )
                    && borrowerRepo
                    .existsByEmailAndOrganization(
                            email,
                            org
                    )) {

                throw new RuntimeException(
                        "Email already registered: "
                                + email
                );
            }


            borrower.setEmail(email);
        }


        // ------------------------------------------------------------
        // PERSONAL INFORMATION
        // ------------------------------------------------------------

        if (req.getNationalId() != null) {

            borrower.setNationalId(
                    req.getNationalId()
            );
        }


        if (req.getPassportNumber() != null) {

            borrower.setPassportNumber(
                    req.getPassportNumber()
            );
        }


        if (req.getTaxIdentificationNumber() != null) {

            borrower.setTaxIdentificationNumber(
                    req.getTaxIdentificationNumber()
            );
        }


        if (req.getDateOfBirth() != null
                && !req.getDateOfBirth().isBlank()) {

            try {

                borrower.setDateOfBirth(
                        LocalDate.parse(
                                req.getDateOfBirth()
                        )
                );

            } catch (Exception e) {

                throw new RuntimeException(
                        "Invalid date of birth. "
                                + "Expected format: yyyy-MM-dd"
                );
            }
        }


        if (req.getGender() != null) {

            borrower.setGender(
                    req.getGender()
            );
        }


        if (req.getMaritalStatus() != null) {

            borrower.setMaritalStatus(
                    req.getMaritalStatus()
            );
        }


        if (req.getNationality() != null) {

            borrower.setNationality(
                    req.getNationality()
            );
        }


        // ------------------------------------------------------------
        // ADDRESS
        // ------------------------------------------------------------

        if (req.getAddressLine1() != null) {

            borrower.setAddressLine1(
                    req.getAddressLine1()
            );
        }


        if (req.getAddressLine2() != null) {

            borrower.setAddressLine2(
                    req.getAddressLine2()
            );
        }


        if (req.getCity() != null) {

            borrower.setCity(
                    req.getCity()
            );
        }


        if (req.getStateProvince() != null) {

            borrower.setStateProvince(
                    req.getStateProvince()
            );
        }


        if (req.getPostalCode() != null) {

            borrower.setPostalCode(
                    req.getPostalCode()
            );
        }


        if (req.getCountry() != null) {

            borrower.setCountry(
                    req.getCountry()
            );
        }


        // ------------------------------------------------------------
        // EMPLOYMENT
        // ------------------------------------------------------------

        if (req.getEmployerName() != null) {

            borrower.setEmployerName(
                    req.getEmployerName()
            );
        }


        if (req.getEmploymentType() != null) {

            borrower.setEmploymentType(
                    req.getEmploymentType()
            );
        }


        if (req.getJobTitle() != null) {

            borrower.setJobTitle(
                    req.getJobTitle()
            );
        }


        // ------------------------------------------------------------
        // FINANCIAL INFORMATION
        //
        // BigDecimal is passed directly.
        // ------------------------------------------------------------

        if (req.getMonthlyIncome() != null) {

            borrower.setMonthlyIncome(
                    req.getMonthlyIncome()
            );
        }


        if (req.getMonthlyExpenses() != null) {

            borrower.setMonthlyExpenses(
                    req.getMonthlyExpenses()
            );
        }


        if (req.getNetWorth() != null) {

            borrower.setNetWorth(
                    req.getNetWorth()
            );
        }


        // ------------------------------------------------------------
        // CREDIT
        // ------------------------------------------------------------

        if (req.getCreditScore() != null) {

            borrower.setCreditScore(
                    req.getCreditScore()
            );
        }


        if (req.getCreditBureau() != null) {

            borrower.setCreditBureau(
                    req.getCreditBureau()
            );
        }


        // ------------------------------------------------------------
        // BANK
        // ------------------------------------------------------------

        if (req.getBankName() != null) {

            borrower.setBankName(
                    req.getBankName()
            );
        }


        if (req.getBankAccountNumber() != null) {

            borrower.setBankAccountNumber(
                    req.getBankAccountNumber()
            );
        }


        if (req.getBankBranch() != null) {

            borrower.setBankBranch(
                    req.getBankBranch()
            );
        }


        // ------------------------------------------------------------
        // SAVE
        // ------------------------------------------------------------

        Borrower saved =
                borrowerRepo.save(
                        borrower
                );


        // ------------------------------------------------------------
        // AUDIT
        // ------------------------------------------------------------

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
