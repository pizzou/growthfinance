package com.patrick.fintech.loan_backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.repository.CreditBureauCheckRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Credit bureau integration service.
 *
 * In Rwanda, credit reference bureau checks are typically sourced from
 * providers such as TransUnion Rwanda (the BNR-licensed CRB). This service
 * is written against a generic provider contract so a real provider can be
 * plugged in by setting app.credit-bureau.enabled=true plus the base-url/
 * api-key properties. Until real credentials are supplied it deterministically
 * SIMULATES a bureau response from data already on file for the borrower so
 * that risk scoring, underwriting screens, and demos behave realistically
 * without ever fabricating a "live" external call.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditBureauService {

    private final CreditBureauCheckRepository checkRepo;
    private final BorrowerRepository borrowerRepo;
    private final AuditService auditService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.credit-bureau.enabled:false}")     private boolean bureauEnabled;
    @Value("${app.credit-bureau.provider:INTERNAL_SIMULATED}") private String providerName;
    @Value("${app.credit-bureau.base-url:}")          private String baseUrl;
    @Value("${app.credit-bureau.api-key:}")           private String apiKey;

    @Transactional
    public CreditBureauCheck runCheck(Long borrowerId, Long orgId, String requestedBy) {
        Borrower borrower = borrowerRepo.findById(borrowerId)
            .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));

        CreditBureauCheck check;
        if (bureauEnabled && apiKey != null && !apiKey.isBlank()) {
            check = tryLiveProvider(borrower);
        } else {
            check = simulate(borrower);
        }
        check.setBorrower(borrower);
        check.setOrganization(borrower.getOrganization());
        check.setRequestedBy(requestedBy);
        check.setNationalIdChecked(borrower.getNationalId());
        check.setReference("CRB-" + (borrower.getOrganization() != null && borrower.getOrganization().getCountry() != null
            ? borrower.getOrganization().getCountry() : "XX") + "-" + System.currentTimeMillis());

        check = checkRepo.save(check);

        // Sync borrower's headline credit fields so the rest of the app (risk
        // scoring, underwriting views) reflects the freshest bureau pull.
        if (check.getStatus() == CreditBureauCheck.CheckStatus.COMPLETED && check.getCreditScore() != null) {
            borrower.setCreditScore(check.getCreditScore());
            borrower.setCreditBureau(check.getProvider());
            borrower.setCreditReportDate(java.time.LocalDate.now());
            borrowerRepo.save(borrower);
        }

        auditService.log(borrower.getOrganization(), null, "CREDIT_BUREAU_CHECK", "BORROWER",
            String.valueOf(borrowerId),
            "Credit bureau check run via " + check.getProvider() + " -> " + check.getStatus()
                + (check.getCreditScore() != null ? " (score " + check.getCreditScore() + ")" : ""));

        return check;
    }

    @Transactional
public void reportDisbursedLoan(Loan loan, String reportedBy) {

    Borrower borrower = loan.getBorrower();

    if (borrower == null) {
        throw new RuntimeException("Borrower not found for loan.");
    }

    if (bureauEnabled && apiKey != null && !apiKey.isBlank()) {

        try {

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = new HashMap<>();

            payload.put("loanNumber", loan.getReferenceNumber());
            payload.put("nationalId", borrower.getNationalId());
            payload.put("borrowerName",
                    borrower.getFirstName() + " " + borrower.getLastName());
            payload.put("loanAmount", loan.getAmount());
            payload.put("outstandingBalance", loan.getAmount());
            payload.put("currency", loan.getCurrency());
            payload.put("status", loan.getStatus().name());
            payload.put("disbursedDate", loan.getDisbursedAt());
            payload.put("nextPaymentDate", loan.getNextPaymentDate());

            HttpEntity<Map<String, Object>> entity =
                    new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(
                    baseUrl + "/v1/loan-report",
                    entity,
                    String.class
            );

            log.info("Loan {} reported to Credit Bureau.",
                    loan.getReferenceNumber());

        } catch (Exception ex) {

            log.error("Credit Bureau reporting failed.", ex);

            throw ex;
        }

    } else {

        log.info(
                "Credit Bureau integration disabled. Loan {} not reported.",
                loan.getReferenceNumber());

    }
}

    private CreditBureauCheck tryLiveProvider(Borrower borrower) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> payload = Map.of(
                "nationalId", borrower.getNationalId() != null ? borrower.getNationalId() : "",
                "firstName", borrower.getFirstName(),
                "lastName", borrower.getLastName() != null ? borrower.getLastName() : ""
            );
            ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl + "/v1/credit-report", HttpMethod.POST, new HttpEntity<>(payload, headers), Map.class);

            Map<?, ?> body = resp.getBody();
            if (body == null) throw new RuntimeException("Empty bureau response");

            return CreditBureauCheck.builder()
                .provider(providerName)
                .status(CreditBureauCheck.CheckStatus.COMPLETED)
                .creditScore(toInt(body.get("creditScore")))
                .riskGrade((String) body.get("riskGrade"))
                .activeFacilities(toInt(body.get("activeFacilities")))
                .delinquentAccounts(toInt(body.get("delinquentAccounts")))
                .totalOutstandingDebt(toDouble(body.get("totalOutstandingDebt")))
                .totalMonthlyObligations(toDouble(body.get("totalMonthlyObligations")))
                .hasDefaultHistory(Boolean.TRUE.equals(body.get("hasDefaultHistory")))
                .hasActiveListing(Boolean.TRUE.equals(body.get("hasActiveListing")))
                .listingReason((String) body.get("listingReason"))
                .rawResponse(toJson(body))
                .build();
        } catch (Exception e) {
            log.warn("Live credit bureau provider failed ({}), falling back to simulation: {}", providerName, e.getMessage());
            CreditBureauCheck fallback = simulate(borrower);
            fallback.setFailureReason("Live provider unreachable, used internal estimate: " + e.getMessage());
            return fallback;
        }
    }

    /**
     * Deterministic simulation seeded from the borrower's own on-file data
     * (national ID / id / existing credit score / income) so repeated checks
     * for the same borrower are stable rather than random noise, while still
     * varying sensibly between borrowers.
     */
    private CreditBureauCheck simulate(Borrower b) {
        long seed = (b.getNationalId() != null && !b.getNationalId().isBlank())
            ? b.getNationalId().hashCode() : (b.getId() != null ? b.getId() : 1L);
        Random rnd = new Random(seed);

        int base = (b.getCreditScore() != null) ? b.getCreditScore() : 550 + rnd.nextInt(200);
        int jitter = rnd.nextInt(41) - 20; // +/-20
        int score = Math.max(300, Math.min(850, base + jitter));

        String grade;
        if      (score >= 750) grade = "EXCELLENT";
        else if (score >= 680) grade = "GOOD";
        else if (score >= 600) grade = "FAIR";
        else if (score >= 500) grade = "POOR";
        else                   grade = "VERY_POOR";

        int delinquent = score < 550 ? rnd.nextInt(3) + 1 : (score < 650 ? rnd.nextInt(2) : 0);
        boolean defaulted = score < 480 && rnd.nextInt(3) == 0;
        int facilities = rnd.nextInt(4);
        double income = b.getMonthlyIncome() != null ? b.getMonthlyIncome() : 0;
        double outstanding = facilities * (income > 0 ? income * (0.5 + rnd.nextDouble()) : 50_000 + rnd.nextInt(500_000));
        double monthlyObligations = facilities > 0 ? outstanding / (12 + rnd.nextInt(24)) : 0;

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("simulated", true);
        snapshot.put("note", "No live BNR-licensed CRB credentials configured — internal estimate generated from borrower profile.");
        snapshot.put("creditScore", score);
        snapshot.put("riskGrade", grade);
        snapshot.put("activeFacilities", facilities);
        snapshot.put("delinquentAccounts", delinquent);
        snapshot.put("hasDefaultHistory", defaulted);

        return CreditBureauCheck.builder()
            .provider("INTERNAL_SIMULATED")
            .status(CreditBureauCheck.CheckStatus.COMPLETED)
            .creditScore(score)
            .riskGrade(grade)
            .activeFacilities(facilities)
            .delinquentAccounts(delinquent)
            .totalOutstandingDebt(Math.round(outstanding * 100.0) / 100.0)
            .totalMonthlyObligations(Math.round(monthlyObligations * 100.0) / 100.0)
            .hasDefaultHistory(defaulted)
            .hasActiveListing(defaulted && rnd.nextBoolean())
            .listingReason(defaulted ? "Historical default recorded on internal ledger" : null)
            .rawResponse(toJson(snapshot))
            .build();
    }

    public List<CreditBureauCheck> getHistory(Long borrowerId) {
        return checkRepo.findByBorrower_IdOrderByCreatedAtDesc(borrowerId);
    }

    public Optional<CreditBureauCheck> getLatest(Long borrowerId) {
        return checkRepo.findFirstByBorrower_IdOrderByCreatedAtDesc(borrowerId);
    }

    private String toJson(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }
    private Integer toInt(Object o) { return o == null ? null : (o instanceof Number n ? n.intValue() : Integer.parseInt(o.toString())); }
    private Double  toDouble(Object o) { return o == null ? null : (o instanceof Number n ? n.doubleValue() : Double.parseDouble(o.toString())); }
}
