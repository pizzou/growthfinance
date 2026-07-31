package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.CurrencyRate;
import com.patrick.fintech.loan_backend.repository.CurrencyRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;

/**
 * Multi-currency FX service.
 * Refreshes rates daily (and once at startup) from exchangerate-api.com's open endpoint —
 * no API key or signup required, unlike Open Exchange Rates which this used to call and which
 * silently did nothing without a paid app-id, leaving every rate at its seeded/1:1 fallback.
 * Falls back to the last successfully cached rates if the API is briefly unavailable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CurrencyService {

    private final CurrencyRateRepository rateRepo;
    private final RestTemplate           restTemplate;

    /** Free, keyless endpoint — no signup, no app-id, ~160 ISO currencies including all of
     *  SUPPORTED_CURRENCIES below. Updated roughly daily by the provider. */
    private static final String RATES_URL = "https://open.er-api.com/v6/latest/USD";

    public static final List<String> SUPPORTED_CURRENCIES = List.of(
        "USD","EUR","GBP","KES","UGX","TZS","RWF","ETB",
        "NGN","GHS","ZAR","INR","AED","SAR","QAR","EGP",
        "XOF","XAF","MWK","ZMW","BDT","PKR","LKR","PHP","BRL"
    );

    /** Convert amount from one currency to another */
    public double convert(double amount, String from, String to) {
        if (from.equalsIgnoreCase(to)) return amount;
        double rate = getRate(from, to);
        return Math.round(amount * rate * 100.0) / 100.0;
    }

    public double getRate(String from, String to) {
        return rateRepo.findByBaseCurrencyAndTargetCurrency(from, to)
            .map(CurrencyRate::getRate)
            .orElseGet(() -> {
                // fallback: try USD pivot
                double fromUsd = rateRepo.findByBaseCurrencyAndTargetCurrency("USD", from)
                    .map(r -> 1.0 / r.getRate()).orElse(1.0);
                double toUsd = rateRepo.findByBaseCurrencyAndTargetCurrency("USD", to)
                    .map(CurrencyRate::getRate).orElse(1.0);
                return fromUsd * toUsd;
            });
    }

    public List<CurrencyRate> getRatesForBase(String base) {
        return rateRepo.findByBaseCurrency(base);
    }

    /** Refreshes from the live FX API — runs once ~5s after startup, then every 24 hours.
     *  Also callable directly (see CurrencyController's manual refresh endpoint) so staff
     *  don't have to wait for the schedule or restart the server to pull fresh rates. */
    @Scheduled(fixedDelay = 86_400_000, initialDelay = 5_000)
    public RefreshResult refreshRates() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(RATES_URL, Map.class);
            if (response == null) return new RefreshResult(false, "empty response from FX rate provider", 0);
            if (!"success".equals(response.get("result")))
                return new RefreshResult(false, "FX rate provider returned: " + response.get("result"), 0);
            @SuppressWarnings("unchecked")
            Map<String, Number> rates = (Map<String, Number>) response.get("rates");
            if (rates == null) return new RefreshResult(false, "no rates in FX provider response", 0);
            int updated = 0;
            for (String currency : SUPPORTED_CURRENCIES) {
                if (rates.containsKey(currency)) {
                    double rate = rates.get(currency).doubleValue();
                    CurrencyRate cr = rateRepo.findByBaseCurrencyAndTargetCurrency("USD", currency)
                        .orElse(CurrencyRate.builder().baseCurrency("USD").targetCurrency(currency).build());
                    cr.setRate(rate);
                    rateRepo.save(cr);
                    updated++;
                } else {
                    log.warn("FX provider response did not include a rate for {}", currency);
                }
            }
            log.info("FX rates refreshed for {} currencies from live API", updated);
            return new RefreshResult(true, "exchangerate-api.com (live)", updated);
        } catch (Exception e) {
            log.warn("FX rate refresh failed, using cached rates: {}", e.getMessage());
            return new RefreshResult(false, "cached rates (refresh failed: " + e.getMessage() + ")", 0);
        }
    }

    /** Result of an FX refresh attempt: whether it succeeded, where the rates came from, and how many currencies were updated. */
    public record RefreshResult(boolean success, String source, int updatedCount) {}
}
