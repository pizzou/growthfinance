package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.CurrencyRate;
import com.patrick.fintech.loan_backend.service.CurrencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/currencies")
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyService currencyService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CurrencyRate>>> rates(
            @RequestParam(defaultValue = "USD") String base) {
        return ResponseEntity.ok(ApiResponse.ok(currencyService.getRatesForBase(base)));
    }

    @GetMapping("/convert")
    public ResponseEntity<ApiResponse<Map<String,Object>>> convert(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam double amount) {
        double converted = currencyService.convert(amount, from, to);
        double rate      = currencyService.getRate(from, to);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "from", from, "to", to,
            "amount", amount, "converted", converted, "rate", rate)));
    }

    @GetMapping("/supported")
    public ResponseEntity<ApiResponse<List<String>>> supported() {
        return ResponseEntity.ok(ApiResponse.ok(CurrencyService.SUPPORTED_CURRENCIES));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<CurrencyService.RefreshResult>> refresh() {
        CurrencyService.RefreshResult result = currencyService.refreshRates();
        return ResponseEntity.ok(result.success()
            ? ApiResponse.ok("Rates refreshed from " + result.source(), result)
            : ApiResponse.ok("Refresh did not update rates: " + result.source(), result));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String,Object>>> status() {
        List<CurrencyRate> rates = currencyService.getRatesForBase("USD");
        Map<String,Object> status = new java.util.LinkedHashMap<>();
        status.put("ratesConfigured", !rates.isEmpty());
        status.put("currencyCount", rates.size());
        status.put("lastFetchedAt", rates.stream()
            .map(CurrencyRate::getFetchedAt).filter(java.util.Objects::nonNull)
            .max(java.time.LocalDateTime::compareTo).orElse(null));
        status.put("source", "exchangerate-api.com (live, no key required)");
        return ResponseEntity.ok(ApiResponse.ok(status));
    }
}
