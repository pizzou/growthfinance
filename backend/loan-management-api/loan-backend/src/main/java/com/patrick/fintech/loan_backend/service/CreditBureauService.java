package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.dto.CreditBureauCheckResponse;
import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.CreditBureauCheck;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.CreditBureauCheckRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
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
    private final PlatformTransactionManager transactionManager;
    private final Environment environment;

    // ============================================================
    // CONFIGURATION
    // ============================================================

    @Value("${app.credit-bureau.enabled:false}")
    private boolean bureauEnabled;

    @Value("${app.credit-bureau.provider:TransUnion}")
    private String providerName;

    @Value("${app.credit-bureau.base-url:}")
    private String baseUrl;

    @Value("${app.credit-bureau.api-key:}")
    private String apiKey;

    /*
     * IMPORTANT:
     *
     * This remains false.
     *
     * Internal simulation is NOT used.
     */
    @Value("${app.credit-bureau.simulation-enabled:false}")
    private boolean simulationEnabled;

    @Value("${app.credit-bureau.validity-days:30}")
    private int validityDays;

    @Value("${app.credit-bureau.store-raw-response:false}")
    private boolean storeRawResponse;

    @Value("${app.credit-bureau.db-transaction-timeout-seconds:30}")
    private int dbTransactionTimeoutSeconds;

    @Value("${app.credit-bureau.required-for-disbursement:true}")
    private boolean requiredForDisbursement;

    @Value("${app.environment:staging}")
    private String applicationEnvironment;

    // ============================================================
    // STARTUP VALIDATION
    // ============================================================

    /**
     * IMPORTANT:
     *
     * The application is allowed to START when the real
     * Credit Bureau URL/API key have not yet been supplied.
     *
     * However, Credit Bureau checks are fail-closed.
     *
     * There is NEVER an internal simulation fallback.
     */
    @PostConstruct
    private void validateProductionConfiguration() {

        /*
         * Internal simulation is never permitted.
         */
        if (simulationEnabled) {

            throw new IllegalStateException(
                    "Internal Credit Bureau simulation is disabled. "
                            + "Set app.credit-bureau.simulation-enabled=false."
            );
        }

        /*
         * Validate basic validity configuration.
         */
        if (validityDays <= 0) {

            throw new IllegalStateException(
                    "app.credit-bureau.validity-days must be greater than zero."
            );
        }

        /*
         * The application can start without a real provider.
         *
         * This is intentional because the real TransUnion URL
         * and API key have not yet been supplied.
         */
        if (!isLiveProviderConfigured()) {

            log.warn(
                    "Real Credit Bureau provider is NOT configured. "
                            + "Provider={}, enabled={}, baseUrlConfigured={}, "
                            + "apiKeyConfigured={}, simulationEnabled={}. "
                            + "Application will start, but Credit Bureau "
                            + "checks will fail until the real provider URL "
                            + "and API key are configured.",
                    providerName,
                    bureauEnabled,
                    isBaseUrlConfigured(),
                    isApiKeyConfigured(),
                    simulationEnabled
            );

            return;
        }

        /*
         * Real provider is configured.
         */
        log.info(
                "Real Credit Bureau provider configuration detected. "
                        + "Provider={}",
                providerName
        );

        /*
         * Validate the provider name.
         */
        if (
                providerName == null
                        || providerName.isBlank()
        ) {

            throw new IllegalStateException(
                    "Credit Bureau provider name is required."
            );
        }

        if (
                "INTERNAL_SIMULATED"
                        .equalsIgnoreCase(
                                providerName.trim()
                        )
        ) {

            throw new IllegalStateException(
                    "Internal simulated Credit Bureau provider is forbidden."
            );
        }

        /*
         * Validate production URL.
         */
        validateProductionBaseUrl();

        log.info(
                "Real Credit Bureau configuration validated successfully. "
                        + "Provider={}, Base URL={}, Validity={} days.",
                providerName,
                sanitizeUrlForLog(baseUrl),
                validityDays
        );
    }

    // ============================================================
    // RUN CREDIT BUREAU CHECK
    // ============================================================

    /**
     * Runs a REAL Credit Bureau check.
     *
     * There is NO simulation fallback.
     *
     * If TransUnion is not configured, this method fails clearly.
     */
    public CreditBureauCheck runCheck(
            Long borrowerId,
            Long orgId,
            String requestedBy
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
                assertBorrowerBelongsToOrganization(
                        borrowerId,
                        orgId
                );

        validateBorrowerForCreditCheck(
                borrower
        );

        /*
         * REAL PROVIDER IS REQUIRED.
         *
         * There is deliberately no simulation branch.
         */
        if (!isLiveProviderConfigured()) {

            throw new IllegalStateException(
                    "TransUnion Credit Bureau is not configured. "
                            + "The official TransUnion base URL and API key "
                            + "must be configured before a real credit bureau "
                            + "check can be performed. "
                            + "Internal simulation is disabled."
            );
        }

        /*
         * Execute the real provider.
         */
        CreditBureauCheck check =
                tryLiveProvider(
                        borrower
                );

        /*
         * Persist only after the external request succeeds.
         */
        return persistCompletedCheck(
                borrowerId,
                orgId,
                requestedBy,
                check
        );
    }

    // ============================================================
    // PERSIST CREDIT CHECK
    // ============================================================

    private CreditBureauCheck persistCompletedCheck(
            Long borrowerId,
            Long orgId,
            String requestedBy,
            CreditBureauCheck check
    ) {

        if (check == null) {

            throw new IllegalArgumentException(
                    "Credit Bureau check result is required."
            );
        }

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        if (dbTransactionTimeoutSeconds > 0) {

            transactionTemplate.setTimeout(
                    dbTransactionTimeoutSeconds
            );
        }

        CreditBureauCheck saved =
                transactionTemplate.execute(
                        status -> {

                            Borrower borrower =
                                    assertBorrowerBelongsToOrganization(
                                            borrowerId,
                                            orgId
                                    );

                            check.setBorrower(
                                    borrower
                            );

                            check.setOrganization(
                                    borrower.getOrganization()
                            );

                            check.setRequestedBy(
                                    normalizeActor(
                                            requestedBy
                                    )
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

                            if (check.getExpiresAt() == null) {

                                check.setExpiresAt(
                                        LocalDateTime.now()
                                                .plusDays(
                                                        validityDays
                                                )
                                );
                            }

                            /*
                             * Never persist raw provider data in production
                             * unless explicitly enabled.
                             */
                            if (
                                    !storeRawResponse
                                            &&
                                    isProductionEnvironment()
                            ) {

                                check.setRawResponse(
                                        null
                                );
                            }

                            CreditBureauCheck savedCheck =
                                    checkRepo.save(
                                            check
                                    );

                            /*
                             * Update borrower only after successful
                             * Credit Bureau response.
                             */
                            if (
                                    savedCheck.getStatus()
                                            == CreditBureauCheck.CheckStatus.COMPLETED
                                            &&
                                    savedCheck.getCreditScore() != null
                            ) {

                                borrower.setCreditScore(
                                        savedCheck.getCreditScore()
                                );

                                borrower.setCreditBureau(
                                        clean(
                                                savedCheck.getProvider()
                                        )
                                );

                                borrower.setCreditReportDate(
                                        LocalDate.now()
                                );

                                borrowerRepo.save(
                                        borrower
                                );
                            }

                            /*
                             * Audit within the same database transaction.
                             */
                            auditCreditBureauCheck(
                                    borrower,
                                    borrowerId,
                                    savedCheck
                            );

                            return savedCheck;
                        }
                );

        if (saved == null) {

            throw new IllegalStateException(
                    "Credit Bureau check could not be persisted."
            );
        }

        return saved;
    }

    // ============================================================
    // REPORT DISBURSED LOAN
    // ============================================================

    /**
     * Reports a disbursed loan to the REAL Credit Bureau.
     *
     * No simulation.
     */
    public void reportDisbursedLoan(
            Loan loan,
            String reportedBy
    ) {

        validateLoanForReporting(
                loan
        );

        Borrower borrower =
                loan.getBorrower();

        /*
         * Real provider is mandatory.
         */
        if (!isLiveProviderConfigured()) {

            if (isProductionEnvironment()) {

                auditService.log(
                        loan.getOrganization(),
                        null,
                        "CREDIT_BUREAU_REPORT_FAILED",
                        "LOAN",
                        String.valueOf(
                                loan.getId()
                        ),
                        "Production Credit Bureau reporting could not be "
                                + "performed because the real provider is "
                                + "not configured.",
                        null,
                        null,
                        "Credit Bureau"
                );
            }

            throw new IllegalStateException(
                    "TransUnion Credit Bureau is not configured. "
                            + "The official TransUnion base URL and API key "
                            + "are required before loan reporting can occur. "
                            + "Internal simulation is disabled."
            );
        }

        try {

            HttpHeaders headers =
                    buildAuthenticatedHeaders();

            Map<String, Object> payload =
                    new LinkedHashMap<>();

            payload.put(
                    "loanNumber",
                    clean(
                            loan.getReferenceNumber()
                    )
            );

            payload.put(
                    "nationalId",
                    clean(
                            borrower.getNationalId()
                    )
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
                    clean(
                            loan.getCurrency()
                    )
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
                    normalizeActor(
                            reportedBy
                    )
            );

            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(
                            payload,
                            headers
                    );

            String endpoint =
                    buildEndpoint(
                            "/v1/loan-report"
                    );

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
                        "TransUnion rejected loan report. "
                                + "HTTP status: "
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
                    String.valueOf(
                            loan.getId()
                    ),
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
                    String.valueOf(
                            loan.getId()
                    ),
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
                            + "The loan was NOT confirmed as reported.",
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
                    String.valueOf(
                            loan.getId()
                    ),
                    "Unexpected Credit Bureau reporting failure for loan "
                            + loan.getReferenceNumber(),
                    null,
                    null,
                    "Credit Bureau"
            );

            throw new IllegalStateException(
                    "Credit Bureau reporting failed. "
                            + "The loan was NOT confirmed as reported.",
                    ex
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
                    buildAuthenticatedHeaders();

            Map<String, Object> payload =
                    new LinkedHashMap<>();

            payload.put(
                    "nationalId",
                    clean(
                            borrower.getNationalId()
                    )
            );

            payload.put(
                    "firstName",
                    clean(
                            borrower.getFirstName()
                    )
            );

            payload.put(
                    "lastName",
                    clean(
                            borrower.getLastName()
                    )
            );

            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(
                            payload,
                            headers
                    );

            String endpoint =
                    buildEndpoint(
                            "/v1/credit-report"
                    );

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
                        "TransUnion returned HTTP status "
                                + statusCode
                );
            }

            Map<?, ?> body =
                    response.getBody();

            if (
                    body == null
                            ||
                    body.isEmpty()
            ) {

                throw new IllegalStateException(
                        "TransUnion returned an empty response."
                );
            }

            Integer creditScore =
                    toInt(
                            body.get(
                                    "creditScore"
                            )
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
                        "TransUnion returned an invalid credit score."
                );
            }

            CreditBureauCheck.CreditBureauCheckBuilder builder =
                    CreditBureauCheck.builder()

                            .provider(
                                    clean(
                                            providerName
                                    )
                            )

                            .status(
                                    CreditBureauCheck.CheckStatus.COMPLETED
                            )

                            .creditScore(
                                    creditScore
                            )

                            .riskGrade(
                                    toStringValue(
                                            body.get(
                                                    "riskGrade"
                                            )
                                    )
                            )

                            .activeFacilities(
                                    toInt(
                                            body.get(
                                                    "activeFacilities"
                                            )
                                    )
                            )

                            .delinquentAccounts(
                                    toInt(
                                            body.get(
                                                    "delinquentAccounts"
                                            )
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
                            );

            if (storeRawResponse) {

                builder.rawResponse(
                        toJson(
                                body
                        )
                );
            }

            return builder.build();

        } catch (RestClientException ex) {

            log.error(
                    "Credit Bureau provider request failed for borrower {}.",
                    borrower.getId(),
                    ex
            );

            throw new IllegalStateException(
                    "TransUnion Credit Bureau provider is currently unavailable.",
                    ex
            );

        } catch (Exception ex) {

            log.error(
                    "Credit Bureau response processing failed for borrower {}.",
                    borrower.getId(),
                    ex
            );

            throw new IllegalStateException(
                    "TransUnion Credit Bureau response could not be processed.",
                    ex
            );
        }
    }

    // ============================================================
    // HISTORY
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
                checkRepo
                        .findByBorrower_IdOrderByCreatedAtDesc(
                                borrowerId
                        );

        return checks.stream()
                .map(
                        this::toOfficerResponse
                )
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
                .map(
                        this::toOfficerResponse
                );
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

        if (borrower == null) {

            throw new IllegalArgumentException(
                    "Borrower is required."
            );
        }

        if (borrower.getOrganization() == null) {

            throw new SecurityException(
                    "Borrower has no organization."
            );
        }

        boolean hasNationalId =
                borrower.getNationalId() != null
                        &&
                !borrower.getNationalId().isBlank();

        boolean hasName =
                buildBorrowerName(
                        borrower
                ).length() >= 2;

        if (!hasNationalId) {

            throw new IllegalArgumentException(
                    "Borrower national ID is required "
                            + "for credit bureau screening."
            );
        }

        if (!hasName) {

            throw new IllegalArgumentException(
                    "Borrower name is required "
                            + "for credit bureau screening."
            );
        }
    }

    // ============================================================
    // LOAN VALIDATION
    // ============================================================

    private void validateLoanForReporting(
            Loan loan
    ) {

        if (loan == null) {

            throw new IllegalArgumentException(
                    "Loan is required."
            );
        }

        if (loan.getId() == null) {

            throw new IllegalArgumentException(
                    "Loan ID is required."
            );
        }

        Borrower borrower =
                loan.getBorrower();

        if (borrower == null) {

            throw new IllegalArgumentException(
                    "Borrower not found for loan."
            );
        }

        if (loan.getOrganization() == null) {

            throw new IllegalArgumentException(
                    "Loan organization is required."
            );
        }

        if (borrower.getOrganization() == null) {

            throw new IllegalArgumentException(
                    "Borrower organization is required."
            );
        }

        if (
                loan.getOrganization().getId() == null
                        ||
                borrower.getOrganization().getId() == null
                        ||
                !loan.getOrganization()
                        .getId()
                        .equals(
                                borrower
                                        .getOrganization()
                                        .getId()
                        )
        ) {

            throw new SecurityException(
                    "Loan and borrower belong to different organizations."
            );
        }

        if (
                borrower.getNationalId() == null
                        ||
                borrower.getNationalId().isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Borrower national ID is required "
                            + "for Credit Bureau reporting."
            );
        }

        if (
                buildBorrowerName(
                        borrower
                ).length() < 2
        ) {

            throw new IllegalArgumentException(
                    "Borrower name is required "
                            + "for Credit Bureau reporting."
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
                        .orElseThrow(
                                () ->
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
                    "Access denied: borrower has no organization."
            );
        }

        if (
                orgId == null
                        ||
                !borrower
                        .getOrganization()
                        .getId()
                        .equals(
                                orgId
                        )
        ) {

            throw new SecurityException(
                    "Access denied: borrower does not belong "
                            + "to your organization."
            );
        }
    }

    // ============================================================
    // PROVIDER CONFIGURATION
    // ============================================================

    private boolean isLiveProviderConfigured() {

        if (!bureauEnabled) {
            return false;
        }

        if (!isApiKeyConfigured()) {
            return false;
        }

        if (!isBaseUrlConfigured()) {
            return false;
        }

        if (
                providerName == null
                        ||
                providerName.isBlank()
        ) {

            return false;
        }

        if (
                "INTERNAL_SIMULATED"
                        .equalsIgnoreCase(
                                providerName.trim()
                        )
        ) {

            return false;
        }

        return true;
    }

    private boolean isApiKeyConfigured() {

        return apiKey != null
                &&
                !apiKey.isBlank();
    }

    private boolean isBaseUrlConfigured() {

        return baseUrl != null
                &&
                !baseUrl.isBlank();
    }

    /**
     * Returns whether a successful real Credit Bureau report is required
     * before a loan may be disbursed. Production defaults to required;
     * development can explicitly disable the requirement.
     */
    public boolean isReportingRequiredForDisbursement() {
        return requiredForDisbursement;
    }

    // ============================================================
    // PRODUCTION DETECTION
    // ============================================================

    private boolean isProductionEnvironment() {

        if (
                applicationEnvironment != null
                        &&
                (
                        "production"
                                .equalsIgnoreCase(
                                        applicationEnvironment.trim()
                                )
                                ||
                        "prod"
                                .equalsIgnoreCase(
                                        applicationEnvironment.trim()
                                )
                )
        ) {

            return true;
        }

        try {

            return environment.acceptsProfiles(
                    Profiles.of(
                            "production",
                            "prod"
                    )
            );

        } catch (Exception ex) {

            log.warn(
                    "Unable to inspect active Spring profiles. "
                            + "Falling back to app.environment={}.",
                    applicationEnvironment
            );

            return false;
        }
    }

    // ============================================================
    // PRODUCTION URL VALIDATION
    // ============================================================

    private void validateProductionBaseUrl() {

        if (!isBaseUrlConfigured()) {

            throw new IllegalStateException(
                    "Credit Bureau base URL is not configured."
            );
        }

        try {

            URI uri =
                    URI.create(
                            normalizeBaseUrl(
                                    baseUrl
                            )
                    );

            if (
                    uri.getScheme() == null
                            ||
                    !"https".equalsIgnoreCase(
                            uri.getScheme()
                    )
            ) {

                throw new IllegalStateException(
                        "Credit Bureau base URL must use HTTPS."
                );
            }

            if (
                    uri.getHost() == null
                            ||
                    uri.getHost().isBlank()
            ) {

                throw new IllegalStateException(
                        "Credit Bureau base URL has no valid host."
                );
            }

        } catch (IllegalArgumentException ex) {

            throw new IllegalStateException(
                    "Invalid Credit Bureau base URL.",
                    ex
            );
        }
    }

    // ============================================================
    // AUTHENTICATED HEADERS
    // ============================================================

    private HttpHeaders buildAuthenticatedHeaders() {

        if (!isApiKeyConfigured()) {

            throw new IllegalStateException(
                    "TransUnion Credit Bureau API key is not configured."
            );
        }

        HttpHeaders headers =
                new HttpHeaders();

        headers.setBearerAuth(
                apiKey.trim()
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
                    "TransUnion Credit Bureau base URL is not configured."
            );
        }

        if (
                path == null
                        ||
                path.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Credit Bureau endpoint path is required."
            );
        }

        String normalizedPath =
                path.startsWith("/")
                        ? path
                        : "/" + path;

        return normalized
                +
                normalizedPath;
    }

    // ============================================================
    // AUDIT
    // ============================================================

    private void auditCreditBureauCheck(
            Borrower borrower,
            Long borrowerId,
            CreditBureauCheck check
    ) {

        String provider =
                clean(
                        check.getProvider()
                );

        if (provider == null) {

            provider =
                    "UNKNOWN_PROVIDER";
        }

        StringBuilder description =
                new StringBuilder(
                        "Credit bureau check completed via "
                );

        description.append(
                provider
        );

        description.append(
                " -> "
        );

        description.append(
                check.getStatus()
        );

        if (
                check.getCreditScore() != null
        ) {

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
    }

    // ============================================================
    // REFERENCE GENERATOR
    // ============================================================

    private String generateReference(
            Long organizationId
    ) {

        String country =
                "RW";

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
                        .toUpperCase(
                                Locale.ROOT
                        );

        return "CRB-"
                +
                country
                +
                "-"
                +
                organizationId
                +
                "-"
                +
                uuid;
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
                        +
                " "
                        +
                last
        ).trim();
    }

    // ============================================================
    // ACTOR NORMALIZATION
    // ============================================================

    private String normalizeActor(
            String value
    ) {

        String cleaned =
                clean(
                        value
                );

        return cleaned != null
                ? cleaned
                : "SYSTEM";
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

        } catch (NumberFormatException ex) {

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

            double result =
                    number.doubleValue();

            return Double.isFinite(
                    result
            )
                    ? result
                    : null;
        }

        try {

            double result =
                    Double.parseDouble(
                            value.toString().trim()
                    );

            return Double.isFinite(
                    result
            )
                    ? result
                    : null;

        } catch (NumberFormatException ex) {

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
                        .toLowerCase(
                                Locale.ROOT
                        );

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
    // URL LOG SANITIZATION
    // ============================================================

    private String sanitizeUrlForLog(
            String url
    ) {

        if (url == null) {
            return "";
        }

        try {

            URI uri =
                    URI.create(
                            url
                    );

            if (
                    uri.getScheme() == null
                            ||
                    uri.getHost() == null
            ) {

                return "[INVALID_URL]";
            }

            return uri.getScheme()
                    +
                    "://"
                    +
                    uri.getHost()
                    +
                    (
                            uri.getPort() > 0
                                    ?
                            ":" + uri.getPort()
                                    :
                            ""
                    );

        } catch (Exception ex) {

            return "[INVALID_URL]";
        }
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
                from.isAfter(
                        to
                )
        ) {

            throw new IllegalArgumentException(
                    "From date cannot be after to date."
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
                    field
                            +
                    " is required."
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

        } catch (Exception ex) {

            log.warn(
                    "Unable to serialize Credit Bureau response.",
                    ex
            );

            return "{}";
        }
    }
}
