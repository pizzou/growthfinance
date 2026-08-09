
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    /**
     * Enable the real external credit bureau integration.
     *
     * IMPORTANT:
     * In production this should normally be true only when
     * a licensed/approved bureau integration is configured.
     */
    @Value("${app.credit-bureau.enabled:false}")
    private boolean bureauEnabled;

    /**
     * Provider display name.
     */
    @Value("${app.credit-bureau.provider:INTERNAL_SIMULATED}")
    private String providerName;

    /**
     * External credit bureau base URL.
     */
    @Value("${app.credit-bureau.base-url:}")
    private String baseUrl;

    /**
     * API credential.
     *
     * This must come from the deployment environment/secret manager,
     * never from source control.
     */
    @Value("${app.credit-bureau.api-key:}")
    private String apiKey;

    /**
     * Connect timeout in milliseconds.
     */
    @Value("${app.credit-bureau.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    /**
     * Read timeout in milliseconds.
     */
    @Value("${app.credit-bureau.read-timeout-ms:15000}")
    private int readTimeoutMs;

    /**
     * Whether the internal simulation is allowed.
     *
     * IMPORTANT:
     * This should be false in production.
     */
    @Value("${app.credit-bureau.simulation-enabled:false}")
    private boolean simulationEnabled;

    // ============================================================
    // RUN CREDIT BUREAU CHECK
    // ============================================================

    /**
     * Runs a credit bureau check for a borrower.
     *
     * Production rules:
     *
     * 1. Borrower must exist.
     * 2. Borrower must belong to the requested organization.
     * 3. Real provider is used when explicitly enabled/configured.
     * 4. Internal simulation is only allowed when explicitly enabled.
     * 5. A failed real bureau request NEVER becomes a fake successful
     *    simulated bureau result.
     */
    @Transactional
    public CreditBureauCheck runCheck(
            Long borrowerId,
            Long orgId,
            String requestedBy
    ) {

        validateRequiredId(borrowerId, "Borrower ID");
        validateRequiredId(orgId, "Organization ID");

        Borrower borrower = assertBorrowerBelongsToOrganization(
                borrowerId,
                orgId
        );

        validateBorrowerForCreditCheck(borrower);

        CreditBureauCheck check;

        if (isLiveProviderConfigured()) {

            /*
             * Production behaviour:
             *
             * DO NOT silently replace a failed bureau response
             * with an internally generated score.
             */
            check = tryLiveProvider(borrower);

        } else if (simulationEnabled) {

            /*
             * Simulation is explicitly opt-in.
             *
             * This should normally be disabled in production.
             */
            check = simulate(borrower);

            log.warn(
                    "INTERNAL CREDIT BUREAU SIMULATION USED for borrower {}. "
                            + "This must not be used for production underwriting.",
                    borrowerId
            );

        } else {

            throw new IllegalStateException(
                    "Credit Bureau integration is not configured. "
                            + "Configure a licensed provider or explicitly enable "
                            + "simulation for non-production environments."
            );
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
                        == CreditBureauCheck.CheckStatus.COMPLETED
                        &&
                check.getCreditScore() != null
        ) {

            borrower.setCreditScore(
                    check.getCreditScore()
            );

            borrower.setCreditBureau(
                    clean(check.getProvider())
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
                new StringBuilder(
                        "Credit bureau check completed via "
                );

        description.append(
                clean(check.getProvider()) != null
                        ? check.getProvider()
                        : "UNKNOWN_PROVIDER"
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
                String.valueOf(borrowerId),
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

    /**
     * Reports a disbursed loan to the external credit bureau.
     *
     * This method deliberately fails when the real bureau is configured
     * but unavailable. A successful-looking internal simulation must
     * never be sent to the regulatory bureau workflow.
     */
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

        if (loan.getId() == null) {
            throw new IllegalArgumentException(
                    "Loan ID is required"
            );
        }

        Borrower borrower = loan.getBorrower();

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

        if (borrower.getOrganization() == null) {
            throw new IllegalArgumentException(
                    "Borrower organization is required"
            );
        }

        if (
                borrower.getOrganization().getId() == null
                        ||
                loan.getOrganization().getId() == null
                        ||
                !loan.getOrganization()
                        .getId()
                        .equals(
                                borrower.getOrganization().getId()
                        )
        ) {

            throw new SecurityException(
                    "Loan and borrower belong to different organizations"
            );
        }

        // ========================================================
        // PROVIDER NOT CONFIGURED
        // ========================================================

        if (!isLiveProviderConfigured()) {

            if (simulationEnabled) {

                log.warn(
                        "Credit Bureau reporting skipped for loan {} because "
                                + "simulation mode is enabled and no live provider "
                                + "is configured.",
                        loan.getReferenceNumber()
                );

                auditService.log(
                        loan.getOrganization(),
                        null,
                        "CREDIT_BUREAU_REPORT_SIMULATION",
                        "LOAN",
                        String.valueOf(loan.getId()),
                        "Live Credit Bureau unavailable; external reporting "
                                + "was not performed for loan "
                                + loan.getReferenceNumber(),
                        null,
                        null,
                        "Credit Bureau"
                );

                return;
            }

            log.warn(
                    "Credit Bureau integration is disabled/not configured. "
                            + "Loan {} was not externally reported.",
                    loan.getReferenceNumber()
            );

            auditService.log(
                    loan.getOrganization(),
                    null,
                    "CREDIT_BUREAU_REPORT_SKIPPED",
                    "LOAN",
                    String.valueOf(loan.getId()),
                    "Credit Bureau integration is not configured; loan "
                            + loan.getReferenceNumber()
                            + " was not externally reported.",
                    null,
                    null,
                    "Credit Bureau"
            );

            return;
        }

        // ========================================================
        // LIVE CREDIT BUREAU
        // ========================================================

        try {

            HttpHeaders headers =
                    buildAuthenticatedHeaders();

            Map<String, Object> payload =
                    new LinkedHashMap<>();

            payload.put(
                    "loanNumber",
                    clean(loan.getReferenceNumber())
            );

            payload.put(
                    "nationalId",
                    clean(borrower.getNationalId())
            );

            payload.put(
                    "borrowerName",
                    buildBorrowerName(borrower)
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
                    clean(loan.getCurrency())
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

            String endpoint =
                    buildEndpoint("/v1/loan-report");

            ResponseEntity<String> response =
                    restTemplate.exchange(
                            endpoint,
                            HttpMethod.POST,
                            entity,
                            String.class
                    );

            if (
                    response == null
                            ||
                    !response.getStatusCode()
                            .is2xxSuccessful()
            ) {

                int statusCode =
                        response != null
                                ? response.getStatusCode().value()
                                : -1;

                throw new IllegalStateException(
                        "Credit Bureau rejected loan report. HTTP status: "
                                + statusCode
                );
            }

            log.info(
                    "Loan {} successfully reported to Credit Bureau provider {}.",
                    loan.getReferenceNumber(),
                    providerName
            );

            auditService.log(
                    loan.getOrganization(),
                    null,
                    "CREDIT_BUREAU_LOAN_REPORTED",
                    "LOAN",
                    String.valueOf(loan.getId()),
                    "Disbursed loan "
                            + loan.getReferenceNumber()
                            + " successfully reported to "
                            + providerName,
                    null,
                    null,
                    "Credit Bureau"
            );

        } catch (RestClientException ex) {

            log.error(
                    "Credit Bureau HTTP request failed for loan {} using provider {}.",
                    loan.getReferenceNumber(),
                    providerName,
                    ex
            );

            auditService.log(
                    loan.getOrganization(),
                    null,
                    "CREDIT_BUREAU_REPORT_FAILED",
                    "LOAN",
                    String.valueOf(loan.getId()),
                    "Credit Bureau reporting failed for loan "
                            + loan.getReferenceNumber()
                            + " using provider "
                            + providerName,
                    null,
                    null,
                    "Credit Bureau"
            );

            throw new IllegalStateException(
                    "Credit Bureau reporting failed. "
                            + "The loan was not confirmed as reported.",
                    ex
            );

        } catch (Exception ex) {

            log.error(
                    "Unexpected Credit Bureau reporting failure for loan {}.",
                    loan.getReferenceNumber(),
                    ex
            );

            auditService.log(
                    loan.getOrganization(),
                    null,
                    "CREDIT_BUREAU_REPORT_FAILED",
                    "LOAN",
                    String.valueOf(loan.getId()),
                    "Unexpected Credit Bureau reporting failure for loan "
                            + loan.getReferenceNumber(),
                    null,
                    null,
                    "Credit Bureau"
            );

            throw new IllegalStateException(
                    "Credit Bureau reporting failed. "
                            + "The loan was not confirmed as reported.",
                    ex
            );
        }
    }

    // ============================================================
    // LIVE CREDIT BUREAU CHECK
    // ============================================================

    /**
     * Executes a real bureau check.
     *
     * IMPORTANT:
     * This method does NOT fall back to simulation.
     */
    private CreditBureauCheck tryLiveProvider(
            Borrower borrower
    ) {

        try {

            HttpHeaders headers =
                    buildAuthenticatedHeaders();

            Map<String, Object> payload =
                    new LinkedHashMap<>();

            payload.put(
                    "nationalId",
                    clean(borrower.getNationalId())
            );

            payload.put(
                    "firstName",
                    clean(borrower.getFirstName())
            );

            payload.put(
                    "lastName",
                    clean(borrower.getLastName())
            );

            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(
                            payload,
                            headers
                    );

            String endpoint =
                    buildEndpoint("/v1/credit-report");

            ResponseEntity<Map> response =
                    restTemplate.exchange(
                            endpoint,
                            HttpMethod.POST,
                            entity,
                            Map.class
                    );

            if (
                    response == null
                            ||
                    !response.getStatusCode()
                            .is2xxSuccessful()
            ) {

                int statusCode =
                        response != null
                                ? response.getStatusCode().value()
                                : -1;

                throw new IllegalStateException(
                        "Credit Bureau returned HTTP status "
                                + statusCode
                );
            }

            Map<?, ?> body =
                    response.getBody();

            if (body == null || body.isEmpty()) {

                throw new IllegalStateException(
                        "Credit Bureau returned an empty response"
                );
            }

            Integer creditScore =
                    toInt(
                            body.get("creditScore")
                    );

            if (
                    creditScore != null
                            &&
                    (
                            creditScore < 300
                                    ||
                            creditScore > 850
                    )
            ) {

                throw new IllegalStateException(
                        "Credit Bureau returned an invalid credit score"
                );
            }

            return CreditBureauCheck.builder()

                    .provider(
                            clean(providerName)
                    )

                    .status(
                            CreditBureauCheck.CheckStatus.COMPLETED
                    )

                    .creditScore(
                            creditScore
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

        } catch (RestClientException ex) {

            log.error(
                    "Credit Bureau provider request failed for borrower {}.",
                    borrower.getId(),
                    ex
            );

            throw new IllegalStateException(
                    "Credit Bureau provider is currently unavailable.",
                    ex
            );

        } catch (Exception ex) {

            log.error(
                    "Credit Bureau response processing failed for borrower {}.",
                    borrower.getId(),
                    ex
            );

            throw new IllegalStateException(
                    "Credit Bureau response could not be processed.",
                    ex
            );
        }
    }

    // ============================================================
    // INTERNAL SIMULATION
    // ============================================================

    /**
     * Internal simulation.
     *
     * This method exists only for development/testing.
     *
     * It MUST NOT be enabled in production.
     */
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

        } else if (borrower.getId() != null) {

            seed =
                    borrower.getId();

        } else {

            seed = 1L;
        }

        java.util.Random random =
                new java.util.Random(seed);

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
                outstanding
                        /
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
                "Internal development simulation only. "
                        + "This result is not an official credit bureau report "
                        + "and must not be used as production credit bureau data."
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
                    "Historical default recorded on internal ledger"
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
                        "Historical default recorded on internal ledger"
                                :
                        null
                )

                .rawResponse(
                        toJson(snapshot)
                )

                .build();
    }

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

    /**
     * Returns sanitized DTOs.
     *
     * Do not expose CreditBureauCheck entities directly
     * through controller endpoints.
     */
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

    // ============================================================
    // BORROWER REGULATORY HISTORY
    // ============================================================

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
                        ? from.atStartOfDay()
                        : null;

        LocalDateTime toDateTime =
                to != null
                        ? to.plusDays(1).atStartOfDay()
                        : null;

        validateDateRange(
                from,
                to
        );

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

        return checkRepo
                .findByBorrower_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        borrowerId,
                        fromDateTime,
                        toDateTime
                );
    }

    // ============================================================
    // ORGANIZATION REGULATORY HISTORY
    // ============================================================

    @Transactional(readOnly = true)
    public List<CreditBureauCheck> getOrganizationRegulatoryHistory(
            Long orgId,
            LocalDate from,
            LocalDate to
    ) {

        validateRequiredId(
                orgId,
                "Organization ID"
        );

        validateDateRange(
                from,
                to
        );

        LocalDateTime fromDateTime =
                from != null
                        ? from.atStartOfDay()
                        : null;

        LocalDateTime toDateTime =
                to != null
                        ? to.plusDays(1).atStartOfDay()
                        : null;

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

        return checkRepo
                .findByOrganization_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        orgId,
                        fromDateTime,
                        toDateTime
                );
    }

    // ============================================================
    // KYC / CREDIT CHECK VALIDATION
    // ============================================================

    private void validateBorrowerForCreditCheck(
            Borrower borrower
    ) {

        if (borrower.getOrganization() == null) {

            throw new SecurityException(
                    "Borrower has no organization"
            );
        }

        boolean hasNationalId =
                borrower.getNationalId() != null
                        &&
                !borrower.getNationalId().isBlank();

        boolean hasName =
                buildBorrowerName(borrower)
                        .length() >= 2;

        if (!hasNationalId) {

            throw new IllegalArgumentException(
                    "Borrower national ID is required for credit bureau screening"
            );
        }

        if (!hasName) {

            throw new IllegalArgumentException(
                    "Borrower name is required for credit bureau screening"
            );
        }
    }

    // ============================================================
    // TENANT VALIDATION
    // ============================================================

    private Borrower assertBorrowerBelongsToOrganization(
            Long borrowerId,
            Long orgId
    ) {

        validateRequiredId(
                borrowerId,
                "Borrower ID"
        );

        validateRequiredId(
                orgId,
                "Organization ID"
        );

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
                borrower.getOrganization().getId() == null
        ) {

            throw new SecurityException(
                    "Access denied: borrower has no organization"
            );
        }

        if (
                orgId == null
                        ||
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
    // PROVIDER CONFIGURATION
    // ============================================================

    private boolean isLiveProviderConfigured() {

        return bureauEnabled
                &&
                apiKey != null
                &&
                !apiKey.isBlank()
                &&
                baseUrl != null
                &&
                !baseUrl.isBlank();
    }

    private HttpHeaders buildAuthenticatedHeaders() {

        if (
                apiKey == null
                        ||
                apiKey.isBlank()
        ) {

            throw new IllegalStateException(
                    "Credit Bureau API key is not configured"
            );
        }

        HttpHeaders headers =
                new HttpHeaders();

        headers.setBearerAuth(
                apiKey
        );

        headers.setContentType(
                MediaType.APPLICATION_JSON
        );

        headers.setAccept(
                List.of(
                        MediaType.APPLICATION_JSON
                )
        );

        return headers;
    }

    // ============================================================
    // ENDPOINT BUILDER
    // ============================================================

    private String buildEndpoint(
            String path
    ) {

        String normalized =
                normalizeBaseUrl(
                        baseUrl
                );

        if (normalized.isBlank()) {

            throw new IllegalStateException(
                    "Credit Bureau base URL is not configured"
            );
        }

        if (
                path == null
                        ||
                path.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Credit Bureau endpoint path is required"
            );
        }

        String normalizedPath =
                path.startsWith("/")
                        ? path
                        : "/" + path;

        return normalized + normalizedPath;
    }

    // ============================================================
    // REFERENCE GENERATOR
    // ============================================================

    /**
     * Uses UUID instead of timestamp-only generation to avoid
     * collisions during concurrent requests.
     */
    private String generateReference(
            Long organizationId
    ) {

        String country = "RW";

        String uuid =
                UUID.randomUUID()
                        .toString()
                        .replace(
                                "-",
                                ""
                        )
                        .substring(
                                0,
                                12
                        )
                        .toUpperCase();

        return "CRB-"
                + country
                + "-"
                + organizationId
                + "-"
                + uuid;
    }

    // ============================================================
    // BORROWER NAME
    // ============================================================

    private String buildBorrowerName(
            Borrower borrower
    ) {

        if (borrower == null) {
            return "";
        }

        String first =
                borrower.getFirstName() != null
                        ? borrower.getFirstName().trim()
                        : "";

        String last =
                borrower.getLastName() != null
                        ? borrower.getLastName().trim()
                        : "";

        return (
                first
                        + " "
                        + last
        ).trim();
    }

    // ============================================================
    // TYPE CONVERSION
    // ============================================================

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
                    value.toString().trim()
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
                    value.toString().trim()
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

        String normalized =
                value.toString()
                        .trim()
                        .toLowerCase();

        if ("true".equals(normalized)) {
            return true;
        }

        if ("false".equals(normalized)) {
            return false;
        }

        return null;
    }

    private String toStringValue(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        return clean(
                value.toString()
        );
    }

    // ============================================================
    // MONEY ROUNDING
    // ============================================================

    /**
     * Kept for compatibility with the existing entity fields,
     * which appear to use Double.
     *
     * Financial fields should ideally be migrated to BigDecimal
     * throughout the accounting/loan domain.
     */
    private double roundMoney(
            double value
    ) {

        if (!Double.isFinite(value)) {
            return 0.0;
        }

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
                ? null
                : cleaned;
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

    // ============================================================
    // DATE VALIDATION
    // ============================================================

    private void validateDateRange(
            LocalDate from,
            LocalDate to
    ) {

        if (
                from != null
                        &&
                to != null
                        &&
                from.isAfter(to)
        ) {

            throw new IllegalArgumentException(
                    "From date cannot be after to date"
            );
        }
    }

    // ============================================================
    // REQUIRED ID VALIDATION
    // ============================================================

    private void validateRequiredId(
            Long value,
            String field
    ) {

        if (
                value == null
                        ||
                value <= 0
        ) {

            throw new IllegalArgumentException(
                    field + " is required"
            );
        }
    }

    // ============================================================
    // JSON SERIALIZATION
    // ============================================================

    private String toJson(
            Object object
    ) {

        try {

            return objectMapper.writeValueAsString(
                    object
            );

        } catch (Exception e) {

            log.warn(
                    "Unable to serialize Credit Bureau response.",
                    e
            );

            return "{}";
        }
    }
}
