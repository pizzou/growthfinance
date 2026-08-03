package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.dto.CreditBureauCheckResponse;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.CreditBureauCheck;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.CreditBureauCheckRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditBureauService {

    private final CreditBureauCheckRepository checkRepo;

    private final BorrowerRepository borrowerRepo;

    private final AuditService auditService;

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;


    // ============================================================
    // CONFIGURATION
    // ============================================================

    @Value("${app.credit-bureau.enabled:false}")
    private boolean bureauEnabled;

    @Value("${app.credit-bureau.provider:INTERNAL_SIMULATED}")
    private String providerName;

    @Value("${app.credit-bureau.base-url:}")
    private String baseUrl;

    @Value("${app.credit-bureau.api-key:}")
    private String apiKey;


    // ============================================================
    // RUN CREDIT BUREAU CHECK
    // ============================================================

    @Transactional
    public CreditBureauCheck runCheck(
            Long borrowerId,
            Long orgId,
            String requestedBy
    ) {

        if (borrowerId == null) {
            throw new IllegalArgumentException(
                    "Borrower ID is required"
            );
        }

        if (orgId == null) {
            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }

        Borrower borrower =
                borrowerRepo.findById(borrowerId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Borrower not found: "
                                                + borrowerId
                                )
                        );


        // ========================================================
        // TENANT SECURITY
        // ========================================================

        assertBorrowerBelongsToOrganization(
                borrower,
                orgId
        );


        CreditBureauCheck check;


        // ========================================================
        // LIVE PROVIDER
        // ========================================================

        if (
                bureauEnabled
                        &&
                apiKey != null
                        &&
                !apiKey.isBlank()
                        &&
                baseUrl != null
                        &&
                !baseUrl.isBlank()
        ) {

            check = tryLiveProvider(borrower);

        } else {

            // ====================================================
            // INTERNAL SIMULATION
            // ====================================================

            check = simulate(borrower);
        }


        // ========================================================
        // COMMON INFORMATION
        // ========================================================

        check.setBorrower(borrower);

        check.setOrganization(
                borrower.getOrganization()
        );

        check.setRequestedBy(
                clean(requestedBy)
        );

        check.setNationalIdChecked(
                clean(
                        borrower.getNationalId()
                )
        );

        check.setReference(
                generateReference(
                        borrower
                                .getOrganization()
                                .getId()
                )
        );


        // ========================================================
        // SAVE CHECK
        // ========================================================

        check = checkRepo.save(check);


        // ========================================================
        // UPDATE BORROWER CREDIT INFORMATION
        // ========================================================

        if (
                check.getStatus()
                        ==
                CreditBureauCheck.CheckStatus.COMPLETED
                        &&
                check.getCreditScore() != null
        ) {

            borrower.setCreditScore(
                    check.getCreditScore()
            );

            borrower.setCreditBureau(
                    check.getProvider()
            );

            borrower.setCreditReportDate(
                    LocalDate.now()
            );

            borrowerRepo.save(borrower);
        }


        // ========================================================
        // AUDIT
        // ========================================================

        StringBuilder description =
                new StringBuilder();

        description.append(
                "Credit bureau check run via "
        );

        description.append(
                check.getProvider()
        );

        description.append(
                " -> "
        );

        description.append(
                check.getStatus()
        );


        if (check.getCreditScore() != null) {

            description.append(
                    " (score "
            );

            description.append(
                    check.getCreditScore()
            );

            description.append(
                    ")"
            );
        }


        auditService.log(
                borrower.getOrganization(),
                null,
                "CREDIT_BUREAU_CHECK",
                "BORROWER",
                String.valueOf(
                        borrowerId
                ),
                description.toString(),
                null,
                null,
                "Credit Bureau"
        );


        return check;
    }


    // ============================================================
    // REPORT DISBURSED LOAN
    // ============================================================

    @Transactional
    public void reportDisbursedLoan(
            Loan loan,
            String reportedBy
    ) {

        if (loan == null) {

            throw new IllegalArgumentException(
                    "Loan is required"
            );
        }


        Borrower borrower =
                loan.getBorrower();


        if (borrower == null) {

            throw new IllegalArgumentException(
                    "Borrower not found for loan"
            );
        }


        if (loan.getOrganization() == null) {

            throw new IllegalArgumentException(
                    "Loan organization is required"
            );
        }


        // ========================================================
        // LIVE CREDIT BUREAU
        // ========================================================

        if (
                bureauEnabled
                        &&
                apiKey != null
                        &&
                !apiKey.isBlank()
                        &&
                baseUrl != null
                        &&
                !baseUrl.isBlank()
        ) {

            try {

                HttpHeaders headers =
                        new HttpHeaders();

                headers.setBearerAuth(
                        apiKey
                );

                headers.setContentType(
                        MediaType.APPLICATION_JSON
                );


                Map<String, Object> payload =
                        new LinkedHashMap<>();


                payload.put(
                        "loanNumber",
                        loan.getReferenceNumber()
                );

                payload.put(
                        "nationalId",
                        borrower.getNationalId()
                );

                payload.put(
                        "borrowerName",
                        buildBorrowerName(
                                borrower
                        )
                );

                payload.put(
                        "loanAmount",
                        loan.getAmount()
                );

                payload.put(
                        "outstandingBalance",
                        loan.getOutstandingBalance()
                );

                payload.put(
                        "currency",
                        loan.getCurrency()
                );

                payload.put(
                        "status",
                        loan.getStatus() != null
                                ? loan.getStatus().name()
                                : null
                );

                payload.put(
                        "disbursedDate",
                        loan.getDisbursedAt()
                );

                payload.put(
                        "nextPaymentDate",
                        loan.getNextPaymentDate()
                );

                payload.put(
                        "reportedBy",
                        clean(reportedBy)
                );


                HttpEntity<Map<String, Object>> entity =
                        new HttpEntity<>(
                                payload,
                                headers
                        );


                ResponseEntity<String> response =
                        restTemplate.postForEntity(
                                normalizeBaseUrl(baseUrl)
                                        + "/v1/loan-report",
                                entity,
                                String.class
                        );


                if (
                        !response.getStatusCode()
                                .is2xxSuccessful()
                ) {

                    throw new IllegalStateException(
                            "Credit Bureau returned HTTP "
                                    +
                            response.getStatusCode().value()
                    );
                }


                log.info(
                        "Loan {} successfully reported to Credit Bureau.",
                        loan.getReferenceNumber()
                );


                auditService.log(
                        loan.getOrganization(),
                        null,
                        "CREDIT_BUREAU_LOAN_REPORTED",
                        "LOAN",
                        String.valueOf(
                                loan.getId()
                        ),
                        "Disbursed loan "
                                + loan.getReferenceNumber()
                                + " reported to "
                                + providerName,
                        null,
                        null,
                        "Credit Bureau"
                );


            } catch (Exception ex) {

                log.error(
                        "Credit Bureau reporting failed for loan {}",
                        loan.getReferenceNumber(),
                        ex
                );


                auditService.log(
                        loan.getOrganization(),
                        null,
                        "CREDIT_BUREAU_REPORT_FAILED",
                        "LOAN",
                        String.valueOf(
                                loan.getId()
                        ),
                        "Failed to report loan "
                                + loan.getReferenceNumber()
                                + " to "
                                + providerName
                                + ": "
                                + ex.getMessage(),
                        null,
                        null,
                        "Credit Bureau"
                );


                throw new IllegalStateException(
                        "Credit Bureau reporting failed: "
                                + ex.getMessage(),
                        ex
                );
            }


        } else {

            // ====================================================
            // INTEGRATION DISABLED
            // ====================================================

            log.info(
                    "Credit Bureau integration disabled. "
                            + "Loan {} was not externally reported.",
                    loan.getReferenceNumber()
            );


            auditService.log(
                    loan.getOrganization(),
                    null,
                    "CREDIT_BUREAU_REPORT_SKIPPED",
                    "LOAN",
                    String.valueOf(
                            loan.getId()
                    ),
                    "Credit Bureau integration disabled; loan "
                            + loan.getReferenceNumber()
                            + " was not externally reported.",
                    null,
                    null,
                    "Credit Bureau"
            );
        }
    }


    // ============================================================
    // LIVE CREDIT BUREAU CHECK
    // ============================================================

    private CreditBureauCheck tryLiveProvider(
            Borrower borrower
    ) {

        try {

            HttpHeaders headers =
                    new HttpHeaders();

            headers.setBearerAuth(
                    apiKey
            );

            headers.setContentType(
                    MediaType.APPLICATION_JSON
            );


            Map<String, Object> payload =
                    new LinkedHashMap<>();


            payload.put(
                    "nationalId",
                    borrower.getNationalId() != null
                            ? borrower.getNationalId()
                            : ""
            );

            payload.put(
                    "firstName",
                    borrower.getFirstName()
            );

            payload.put(
                    "lastName",
                    borrower.getLastName() != null
                            ? borrower.getLastName()
                            : ""
            );


            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(
                            payload,
                            headers
                    );


            ResponseEntity<Map> response =
                    restTemplate.exchange(
                            normalizeBaseUrl(baseUrl)
                                    + "/v1/credit-report",
                            HttpMethod.POST,
                            entity,
                            Map.class
                    );


            if (
                    !response.getStatusCode()
                            .is2xxSuccessful()
            ) {

                throw new IllegalStateException(
                        "Credit Bureau returned HTTP "
                                +
                        response.getStatusCode().value()
                );
            }


            Map<?, ?> body =
                    response.getBody();


            if (body == null) {

                throw new IllegalStateException(
                        "Empty Credit Bureau response"
                );
            }


            return CreditBureauCheck.builder()

                    .provider(providerName)

                    .status(
                            CreditBureauCheck.CheckStatus.COMPLETED
                    )

                    .creditScore(
                            toInt(
                                    body.get("creditScore")
                            )
                    )

                    .riskGrade(
                            toStringValue(
                                    body.get("riskGrade")
                            )
                    )

                    .activeFacilities(
                            toInt(
                                    body.get("activeFacilities")
                            )
                    )

                    .delinquentAccounts(
                            toInt(
                                    body.get("delinquentAccounts")
                            )
                    )

                    .totalOutstandingDebt(
                            toDouble(
                                    body.get(
                                            "totalOutstandingDebt"
                                    )
                            )
                    )

                    .totalMonthlyObligations(
                            toDouble(
                                    body.get(
                                            "totalMonthlyObligations"
                                    )
                            )
                    )

                    .hasDefaultHistory(
                            toBoolean(
                                    body.get(
                                            "hasDefaultHistory"
                                    )
                            )
                    )

                    .hasActiveListing(
                            toBoolean(
                                    body.get(
                                            "hasActiveListing"
                                    )
                            )
                    )

                    .listingReason(
                            toStringValue(
                                    body.get(
                                            "listingReason"
                                    )
                            )
                    )

                    .rawResponse(
                            toJson(body)
                    )

                    .build();


        } catch (Exception e) {

            log.warn(
                    "Live Credit Bureau provider failed ({}). "
                            + "Falling back to internal simulation. Reason: {}",
                    providerName,
                    e.getMessage()
            );


            CreditBureauCheck fallback =
                    simulate(borrower);


            fallback.setFailureReason(
                    "Live provider unavailable; "
                            + "internal simulation used. "
                            + "Reason: "
                            + e.getMessage()
            );


            return fallback;
        }
    }


    // ============================================================
    // INTERNAL SIMULATION
    // ============================================================

    private CreditBureauCheck simulate(
            Borrower borrower
    ) {

        long seed;


        if (
                borrower.getNationalId() != null
                        &&
                !borrower.getNationalId().isBlank()
        ) {

            seed =
                    borrower
                            .getNationalId()
                            .hashCode();

        } else if (
                borrower.getId() != null
        ) {

            seed =
                    borrower.getId();

        } else {

            seed = 1L;
        }


        Random random =
                new Random(seed);


        int baseScore;


        if (borrower.getCreditScore() != null) {

            baseScore =
                    borrower.getCreditScore();

        } else {

            baseScore =
                    550 +
                    random.nextInt(200);
        }


        int jitter =
                random.nextInt(41) - 20;


        int score =
                Math.max(
                        300,
                        Math.min(
                                850,
                                baseScore + jitter
                        )
                );


        String grade;


        if (score >= 750) {

            grade = "EXCELLENT";

        } else if (score >= 680) {

            grade = "GOOD";

        } else if (score >= 600) {

            grade = "FAIR";

        } else if (score >= 500) {

            grade = "POOR";

        } else {

            grade = "VERY_POOR";
        }


        int delinquent;


        if (score < 550) {

            delinquent =
                    random.nextInt(3) + 1;

        } else if (score < 650) {

            delinquent =
                    random.nextInt(2);

        } else {

            delinquent = 0;
        }


        boolean defaulted =
                score < 480
                        &&
                random.nextInt(3) == 0;


        int facilities =
                random.nextInt(4);


        double income =
                toDouble(
                        borrower.getMonthlyIncome()
                );


        double outstanding;


        if (facilities > 0) {

            if (income > 0) {

                outstanding =
                        facilities
                                *
                        (
                                income
                                        *
                                (
                                        0.5
                                                +
                                        random.nextDouble()
                                )
                        );

            } else {

                outstanding =
                        facilities
                                *
                        (
                                50_000
                                        +
                                random.nextInt(500_000)
                        );
            }

        } else {

            outstanding = 0.0;
        }


        double monthlyObligations =
                facilities > 0
                        ?
                outstanding /
                        (
                                12
                                        +
                                random.nextInt(24)
                        )
                        :
                0.0;


        boolean activeListing =
                defaulted
                        &&
                random.nextBoolean();


        Map<String, Object> snapshot =
                new LinkedHashMap<>();


        snapshot.put(
                "simulated",
                true
        );

        snapshot.put(
                "provider",
                "INTERNAL_SIMULATED"
        );

        snapshot.put(
                "note",
                "No live licensed Credit Bureau credentials "
                        + "are configured. This is an internal "
                        + "development estimate and must not be "
                        + "treated as an official bureau report."
        );

        snapshot.put(
                "creditScore",
                score
        );

        snapshot.put(
                "riskGrade",
                grade
        );

        snapshot.put(
                "activeFacilities",
                facilities
        );

        snapshot.put(
                "delinquentAccounts",
                delinquent
        );

        snapshot.put(
                "totalOutstandingDebt",
                roundMoney(outstanding)
        );

        snapshot.put(
                "totalMonthlyObligations",
                roundMoney(monthlyObligations)
        );

        snapshot.put(
                "hasDefaultHistory",
                defaulted
        );

        snapshot.put(
                "hasActiveListing",
                activeListing
        );


        if (activeListing) {

            snapshot.put(
                    "listingReason",
                    "Historical default recorded "
                            + "on internal ledger"
            );
        }


        return CreditBureauCheck.builder()

                .provider(
                        "INTERNAL_SIMULATED"
                )

                .status(
                        CreditBureauCheck.CheckStatus.COMPLETED
                )

                .creditScore(score)

                .riskGrade(grade)

                .activeFacilities(facilities)

                .delinquentAccounts(delinquent)

                .totalOutstandingDebt(
                        roundMoney(outstanding)
                )

                .totalMonthlyObligations(
                        roundMoney(
                                monthlyObligations
                        )
                )

                .hasDefaultHistory(defaulted)

                .hasActiveListing(activeListing)

                .listingReason(
                        activeListing
                                ?
                        "Historical default recorded "
                                + "on internal ledger"
                                :
                        null
                )

                .rawResponse(
                        toJson(snapshot)
                )

                .build();
    }


    // ============================================================
    // HISTORY
    // ============================================================

    // ============================================================
// HISTORY - INTERNAL / SERVICE USE
// ============================================================

@Transactional(readOnly = true)
public List<CreditBureauCheck> getHistory(
        Long borrowerId,
        Long orgId
) {

    assertBorrowerBelongsToOrganization(
            borrowerId,
            orgId
    );

    return checkRepo
            .findByBorrower_IdOrderByCreatedAtDesc(
                    borrowerId
            );
}


// ============================================================
// OFFICER HISTORY
// ============================================================
//
// Returns sanitized CreditBureauCheckResponse objects.
//
// Loan officers should NOT receive the JPA entity directly.
// This prevents accidental exposure of:
// - borrower entity
// - organization entity
// - raw provider response
// - Hibernate internals
//
// ============================================================

@Transactional(readOnly = true)
public List<CreditBureauCheckResponse> getOfficerHistory(
        Long borrowerId,
        Long orgId
) {

    assertBorrowerBelongsToOrganization(
            borrowerId,
            orgId
    );

    List<CreditBureauCheck> checks =
            checkRepo.findByBorrower_IdOrderByCreatedAtDesc(
                    borrowerId
            );

    return checks.stream()
            .map(this::toOfficerResponse)
            .toList();
}


// ============================================================
// OFFICER LATEST CHECK
// ============================================================

@Transactional(readOnly = true)
public Optional<CreditBureauCheckResponse> getOfficerLatest(
        Long borrowerId,
        Long orgId
) {

    assertBorrowerBelongsToOrganization(
            borrowerId,
            orgId
    );

    return checkRepo
            .findFirstByBorrower_IdOrderByCreatedAtDesc(
                    borrowerId
            )
            .map(this::toOfficerResponse);
}


// ============================================================
// ENTITY -> OFFICER RESPONSE
// ============================================================

 // ============================================================
// ENTITY -> OFFICER RESPONSE
// ============================================================

public CreditBureauCheckResponse toOfficerResponse(
        CreditBureauCheck check
) {

    if (check == null) {
        return null;
    }

    return CreditBureauCheckResponse.builder()

            .id(
                    check.getId()
            )

            .reference(
                    check.getReference()
            )

            .provider(
                    check.getProvider()
            )

            .status(
                    check.getStatus()
            )

            .creditScore(
                    check.getCreditScore()
            )

            .riskGrade(
                    check.getRiskGrade()
            )

            .activeFacilities(
                    check.getActiveFacilities()
            )

            .delinquentAccounts(
                    check.getDelinquentAccounts()
            )

            .totalOutstandingDebt(
                    check.getTotalOutstandingDebt()
            )

            .totalMonthlyObligations(
                    check.getTotalMonthlyObligations()
            )

            .hasDefaultHistory(
                    check.getHasDefaultHistory()
            )

            .hasActiveListing(
                    check.getHasActiveListing()
            )

            .listingReason(
                    check.getListingReason()
            )

            .requestedBy(
                    check.getRequestedBy()
            )

            .failureReason(
                    check.getFailureReason()
            )

            .createdAt(
                    check.getCreatedAt()
            )

            .expiresAt(
                    check.getExpiresAt()
            )

            .valid(
                    check.isValid()
            )

            .expired(
                    check.isExpired()
            )

            .build();
}

    @Transactional(readOnly = true)
    public List<CreditBureauCheck> getRegulatoryHistory(
            Long borrowerId,
            Long orgId,
            LocalDate from,
            LocalDate to
    ) {

        assertBorrowerBelongsToOrganization(
                borrowerId,
                orgId
        );


        LocalDateTime fromDateTime =
                from != null
                        ?
                from.atStartOfDay()
                        :
                null;


        LocalDateTime toDateTime =
                to != null
                        ?
                to.plusDays(1)
                        .atStartOfDay()
                        :
                null;


        // --------------------------------------------------------
        // No date filters
        // --------------------------------------------------------

        if (
                fromDateTime == null
                        &&
                toDateTime == null
        ) {

            return checkRepo
                    .findByBorrower_IdOrderByCreatedAtDesc(
                            borrowerId
                    );
        }


        // --------------------------------------------------------
        // From only
        // --------------------------------------------------------

        if (
                fromDateTime != null
                        &&
                toDateTime == null
        ) {

            return checkRepo
                    .findByBorrower_IdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                            borrowerId,
                            fromDateTime
                    );
        }


        // --------------------------------------------------------
        // To only
        // --------------------------------------------------------

        if (
                fromDateTime == null
                        &&
                toDateTime != null
        ) {

            return checkRepo
                    .findByBorrower_IdAndCreatedAtLessThanOrderByCreatedAtDesc(
                            borrowerId,
                            toDateTime
                    );
        }


        // --------------------------------------------------------
        // From + To
        // --------------------------------------------------------

        return checkRepo
                .findByBorrower_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        borrowerId,
                        fromDateTime,
                        toDateTime
                );
    }


    // ============================================================
    // REGULATORY HISTORY FOR ENTIRE ORGANIZATION
    // ============================================================

    @Transactional(readOnly = true)
    public List<CreditBureauCheck>
    getOrganizationRegulatoryHistory(
            Long orgId,
            LocalDate from,
            LocalDate to
    ) {

        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }


        LocalDateTime fromDateTime =
                from != null
                        ?
                from.atStartOfDay()
                        :
                null;


        LocalDateTime toDateTime =
                to != null
                        ?
                to.plusDays(1)
                        .atStartOfDay()
                        :
                null;


        // --------------------------------------------------------
        // No filters
        // --------------------------------------------------------

        if (
                fromDateTime == null
                        &&
                toDateTime == null
        ) {

            return checkRepo
                    .findByOrganization_IdOrderByCreatedAtDesc(
                            orgId
                    );
        }


        // --------------------------------------------------------
        // From only
        // --------------------------------------------------------

        if (
                fromDateTime != null
                        &&
                toDateTime == null
        ) {

            return checkRepo
                    .findByOrganization_IdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                            orgId,
                            fromDateTime
                    );
        }


        // --------------------------------------------------------
        // To only
        // --------------------------------------------------------

        if (
                fromDateTime == null
                        &&
                toDateTime != null
        ) {

            return checkRepo
                    .findByOrganization_IdAndCreatedAtLessThanOrderByCreatedAtDesc(
                            orgId,
                            toDateTime
                    );
        }


        // --------------------------------------------------------
        // From + To
        // --------------------------------------------------------

        return checkRepo
                .findByOrganization_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        orgId,
                        fromDateTime,
                        toDateTime
                );
    }


    // ============================================================
    // TENANT VALIDATION
    // ============================================================

    private Borrower assertBorrowerBelongsToOrganization(
            Long borrowerId,
            Long orgId
    ) {

        if (borrowerId == null) {

            throw new IllegalArgumentException(
                    "Borrower ID is required"
            );
        }


        if (orgId == null) {

            throw new IllegalArgumentException(
                    "Organization ID is required"
            );
        }


        Borrower borrower =
                borrowerRepo.findById(
                        borrowerId
                )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Borrower not found: "
                                                + borrowerId
                                )
                        );


        assertBorrowerBelongsToOrganization(
                borrower,
                orgId
        );


        return borrower;
    }


    private void assertBorrowerBelongsToOrganization(
            Borrower borrower,
            Long orgId
    ) {

        if (
                borrower == null
                        ||
                borrower.getOrganization() == null
                        ||
                borrower
                        .getOrganization()
                        .getId() == null
        ) {

            throw new SecurityException(
                    "Access denied: borrower has no organization"
            );
        }


        if (
                !borrower
                        .getOrganization()
                        .getId()
                        .equals(orgId)
        ) {

            throw new SecurityException(
                    "Access denied: borrower does not belong "
                            + "to your organization"
            );
        }
    }


    // ============================================================
    // REFERENCE GENERATOR
    // ============================================================

    private String generateReference(
            Long organizationId
    ) {

        String country = "RW";


        String timestamp =
                String.valueOf(
                        System.currentTimeMillis()
                );


        String suffix =
                timestamp.substring(
                        Math.max(
                                0,
                                timestamp.length() - 8
                        )
                );


        return "CRB-"
                + country
                + "-"
                + organizationId
                + "-"
                + suffix;
    }


    private String buildBorrowerName(
            Borrower borrower
    ) {

        String first =
                borrower.getFirstName() != null
                        ?
                borrower.getFirstName().trim()
                        :
                "";


        String last =
                borrower.getLastName() != null
                        ?
                borrower.getLastName().trim()
                        :
                "";


        return (
                first
                        + " "
                        + last
        ).trim();
    }

    private Integer toInt(
            Object value
    ) {

        if (value == null) {
            return null;
        }


        if (value instanceof Number number) {

            return number.intValue();
        }


        try {

            return Integer.parseInt(
                    value.toString()
            );

        } catch (NumberFormatException e) {

            return null;
        }
    }


    private Double toDouble(
            Object value
    ) {

        if (value == null) {
            return null;
        }


        if (value instanceof Number number) {

            return number.doubleValue();
        }


        try {

            return Double.parseDouble(
                    value.toString()
            );

        } catch (NumberFormatException e) {

            return null;
        }
    }


    private Boolean toBoolean(
            Object value
    ) {

        if (value == null) {
            return null;
        }


        if (value instanceof Boolean bool) {

            return bool;
        }


        return Boolean.parseBoolean(
                value.toString()
        );
    }


    private String toStringValue(
            Object value
    ) {

        return value == null
                ?
                null
                :
                value.toString();
    }


    private double roundMoney(
            double value
    ) {

        return Math.round(
                value * 100.0
        ) / 100.0;
    }


    // ============================================================
    // STRING CLEANING
    // ============================================================

    private String clean(
            String value
    ) {

        if (value == null) {
            return null;
        }


        String cleaned =
                value.trim();


        return cleaned.isEmpty()
                ?
                null
                :
                cleaned;
    }


    // ============================================================
    // BASE URL NORMALIZATION
    // ============================================================

    private String normalizeBaseUrl(
            String url
    ) {

        if (url == null) {
            return "";
        }


        String value =
                url.trim();


        while (
                value.endsWith("/")
        ) {

            value =
                    value.substring(
                            0,
                            value.length() - 1
                    );
        }


        return value;
    }



    private String toJson(
            Object object
    ) {

        try {

            return objectMapper.writeValueAsString(
                    object
            );

        } catch (Exception e) {

            log.warn(
                    "Unable to serialize Credit Bureau response",
                    e
            );

            return "{}";
        }
    }
}