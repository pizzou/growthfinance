package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
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
            String requestedBy) {

        Borrower borrower =
                borrowerRepo.findById(borrowerId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Borrower not found: "
                                                + borrowerId
                                )
                        );


        // --------------------------------------------------------
        // RUN LIVE OR SIMULATED CHECK
        // --------------------------------------------------------

        CreditBureauCheck check;

        if (bureauEnabled
                && apiKey != null
                && !apiKey.isBlank()) {

            check = tryLiveProvider(borrower);

        } else {

            check = simulate(borrower);
        }


        // --------------------------------------------------------
        // SET CHECK DETAILS
        // --------------------------------------------------------

        check.setBorrower(borrower);

        check.setOrganization(
                borrower.getOrganization()
        );

        check.setRequestedBy(
                requestedBy
        );

        check.setNationalIdChecked(
                borrower.getNationalId()
        );

        check.setReference(
                "CRB-"
                        + (
                        borrower.getOrganization() != null
                                && borrower.getOrganization()
                                .getCountry() != null
                                ? borrower.getOrganization()
                                .getCountry()
                                : "XX"
                )
                        + "-"
                        + System.currentTimeMillis()
        );


        // --------------------------------------------------------
        // SAVE
        // --------------------------------------------------------

        check =
                checkRepo.save(check);


        // --------------------------------------------------------
        // UPDATE BORROWER CREDIT SCORE
        // --------------------------------------------------------

        if (check.getStatus()
                == CreditBureauCheck.CheckStatus.COMPLETED
                && check.getCreditScore() != null) {

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


        // --------------------------------------------------------
        // AUDIT
        // --------------------------------------------------------

        auditService.log(
                borrower.getOrganization(),
                null,
                "CREDIT_BUREAU_CHECK",
                "BORROWER",
                String.valueOf(borrowerId),
                "Credit bureau check run via "
                        + check.getProvider()
                        + " -> "
                        + check.getStatus()
                        + (
                        check.getCreditScore() != null
                                ? " (score "
                                + check.getCreditScore()
                                + ")"
                                : ""
                )
        );


        return check;
    }


    // ============================================================
    // REPORT DISBURSED LOAN
    // ============================================================

    @Transactional
    public void reportDisbursedLoan(
            Loan loan,
            String reportedBy) {

        Borrower borrower =
                loan.getBorrower();


        if (borrower == null) {

            throw new RuntimeException(
                    "Borrower not found for loan."
            );
        }


        // --------------------------------------------------------
        // LIVE CREDIT BUREAU
        // --------------------------------------------------------

        if (bureauEnabled
                && apiKey != null
                && !apiKey.isBlank()) {

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
                        new HashMap<>();


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
                        borrower.getFirstName()
                                + " "
                                + (
                                borrower.getLastName() != null
                                        ? borrower.getLastName()
                                        : ""
                        )
                );

                /*
                 * Loan amount is BigDecimal.
                 * Keep it as BigDecimal in the payload.
                 */
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
                        reportedBy
                );


                HttpEntity<Map<String, Object>> entity =
                        new HttpEntity<>(
                                payload,
                                headers
                        );


                restTemplate.postForEntity(
                        baseUrl + "/v1/loan-report",
                        entity,
                        String.class
                );


                log.info(
                        "Loan {} reported to Credit Bureau.",
                        loan.getReferenceNumber()
                );


            } catch (Exception ex) {

                log.error(
                        "Credit Bureau reporting failed.",
                        ex
                );

                throw ex;
            }


        } else {

            log.info(
                    "Credit Bureau integration disabled. "
                            + "Loan {} not reported.",
                    loan.getReferenceNumber()
            );
        }
    }


    // ============================================================
    // LIVE CREDIT BUREAU PROVIDER
    // ============================================================

    private CreditBureauCheck tryLiveProvider(
            Borrower borrower) {

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
                    new HashMap<>();


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


            ResponseEntity<Map> response =
                    restTemplate.exchange(
                            baseUrl
                                    + "/v1/credit-report",
                            HttpMethod.POST,
                            new HttpEntity<>(
                                    payload,
                                    headers
                            ),
                            Map.class
                    );


            Map<?, ?> body =
                    response.getBody();


            if (body == null) {

                throw new RuntimeException(
                        "Empty bureau response"
                );
            }


            return CreditBureauCheck.builder()

                    .provider(
                            providerName
                    )

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

                    /*
                     * IMPORTANT:
                     *
                     * These values are BigDecimal.
                     */
                    .totalOutstandingDebt(
                            toBigDecimal(
                                    body.get(
                                            "totalOutstandingDebt"
                                    )
                            )
                    )

                    .totalMonthlyObligations(
                            toBigDecimal(
                                    body.get(
                                            "totalMonthlyObligations"
                                    )
                            )
                    )

                    .hasDefaultHistory(
                            Boolean.TRUE.equals(
                                    body.get(
                                            "hasDefaultHistory"
                                    )
                            )
                    )

                    .hasActiveListing(
                            Boolean.TRUE.equals(
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
                    "Live credit bureau provider failed ({}), "
                            + "falling back to simulation: {}",
                    providerName,
                    e.getMessage()
            );


            CreditBureauCheck fallback =
                    simulate(borrower);


            fallback.setFailureReason(
                    "Live provider unreachable, "
                            + "used internal estimate: "
                            + e.getMessage()
            );


            return fallback;
        }
    }


    // ============================================================
    // INTERNAL CREDIT BUREAU SIMULATION
    // ============================================================

    private CreditBureauCheck simulate(
            Borrower borrower) {

        long seed;


        if (borrower.getNationalId() != null
                && !borrower.getNationalId().isBlank()) {

            seed =
                    borrower.getNationalId()
                            .hashCode();

        } else {

            seed =
                    borrower.getId() != null
                            ? borrower.getId()
                            : 1L;
        }


        Random random =
                new Random(seed);


        // --------------------------------------------------------
        // CREDIT SCORE
        // --------------------------------------------------------

        int base =
                borrower.getCreditScore() != null
                        ? borrower.getCreditScore()
                        : 550 + random.nextInt(200);


        int jitter =
                random.nextInt(41) - 20;


        int score =
                Math.max(
                        300,
                        Math.min(
                                850,
                                base + jitter
                        )
                );


        // --------------------------------------------------------
        // RISK GRADE
        // --------------------------------------------------------

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


        // --------------------------------------------------------
        // DELINQUENCY / DEFAULT
        // --------------------------------------------------------

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
                        && random.nextInt(3) == 0;


        // --------------------------------------------------------
        // ACTIVE FACILITIES
        // --------------------------------------------------------

        int facilities =
                random.nextInt(4);


        // ========================================================
        // MONTHLY INCOME
        // ========================================================
        //
        // Borrower.monthlyIncome is BigDecimal.
        //
        // DO NOT do:
        //
        // double income = borrower.getMonthlyIncome();
        //
        // Instead convert explicitly.
        // ========================================================

        BigDecimal income =
                borrower.getMonthlyIncome() != null
                        ? borrower.getMonthlyIncome()
                        : BigDecimal.ZERO;


        // --------------------------------------------------------
        // OUTSTANDING DEBT
        // --------------------------------------------------------

        BigDecimal outstanding;


        if (facilities > 0) {

            /*
             * Generate a factor between 0.5 and 1.5.
             */
            double factor =
                    0.5 + random.nextDouble();


            outstanding =
                    income.multiply(
                            BigDecimal.valueOf(
                                    factor
                            )
                    ).multiply(
                            BigDecimal.valueOf(
                                    facilities
                            )
                    );


        } else {

            /*
             * Simulated debt when there are no active facilities.
             *
             * Generates between 50,000 and 550,000.
             */
            outstanding =
                    BigDecimal.valueOf(
                            50_000L
                                    + random.nextInt(
                                    500_001
                            )
                    );
        }


        outstanding =
                outstanding.setScale(
                        2,
                        RoundingMode.HALF_UP
                );


        // --------------------------------------------------------
        // MONTHLY OBLIGATIONS
        // --------------------------------------------------------

        BigDecimal monthlyObligations;


        if (facilities > 0) {

            int repaymentMonths =
                    12 + random.nextInt(24);


            monthlyObligations =
                    outstanding.divide(
                            BigDecimal.valueOf(
                                    repaymentMonths
                            ),
                            2,
                            RoundingMode.HALF_UP
                    );

        } else {

            monthlyObligations =
                    BigDecimal.ZERO;
        }


        // --------------------------------------------------------
        // SNAPSHOT
        // --------------------------------------------------------

        Map<String, Object> snapshot =
                new LinkedHashMap<>();


        snapshot.put(
                "simulated",
                true
        );

        snapshot.put(
                "note",
                "No live BNR-licensed CRB credentials "
                        + "configured — internal estimate generated "
                        + "from borrower profile."
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
                "hasDefaultHistory",
                defaulted
        );

        snapshot.put(
                "totalOutstandingDebt",
                outstanding
        );

        snapshot.put(
                "totalMonthlyObligations",
                monthlyObligations
        );


        // ========================================================
        // BUILD CREDIT BUREAU CHECK
        // ========================================================

        return CreditBureauCheck.builder()

                .provider(
                        "INTERNAL_SIMULATED"
                )

                .status(
                        CreditBureauCheck.CheckStatus.COMPLETED
                )

                .creditScore(
                        score
                )

                .riskGrade(
                        grade
                )

                .activeFacilities(
                        facilities
                )

                .delinquentAccounts(
                        delinquent
                )

                .totalOutstandingDebt(
                        outstanding
                )

                .totalMonthlyObligations(
                        monthlyObligations
                )

                .hasDefaultHistory(
                        defaulted
                )

                .hasActiveListing(
                        defaulted
                                && random.nextBoolean()
                )

                .listingReason(
                        defaulted
                                ? "Historical default recorded "
                                + "on internal ledger"
                                : null
                )

                .rawResponse(
                        toJson(snapshot)
                )

                .build();
    }


    // ============================================================
    // HISTORY
    // ============================================================

    public List<CreditBureauCheck> getHistory(
            Long borrowerId) {

        return checkRepo
                .findByBorrower_IdOrderByCreatedAtDesc(
                        borrowerId
                );
    }


    // ============================================================
    // LATEST CHECK
    // ============================================================

    public Optional<CreditBureauCheck> getLatest(
            Long borrowerId) {

        return checkRepo
                .findFirstByBorrower_IdOrderByCreatedAtDesc(
                        borrowerId
                );
    }


    // ============================================================
    // JSON HELPER
    // ============================================================

    private String toJson(
            Object object) {

        try {

            return objectMapper.writeValueAsString(
                    object
            );

        } catch (Exception e) {

            return "{}";
        }
    }


    // ============================================================
    // INTEGER CONVERSION
    // ============================================================

    private Integer toInt(
            Object value) {

        if (value == null) {

            return null;
        }


        if (value instanceof Number number) {

            return number.intValue();
        }


        return Integer.parseInt(
                value.toString()
        );
    }


    // ============================================================
    // BIGDECIMAL CONVERSION
    // ============================================================

    private BigDecimal toBigDecimal(
            Object value) {

        if (value == null) {

            return null;
        }


        if (value instanceof BigDecimal decimal) {

            return decimal;
        }


        if (value instanceof Number number) {

            return BigDecimal.valueOf(
                    number.doubleValue()
            );
        }


        return new BigDecimal(
                value.toString()
        );
    }


    // ============================================================
    // STRING CONVERSION
    // ============================================================

    private String toStringValue(
            Object value) {

        return value != null
                ? value.toString()
                : null;
    }
}

