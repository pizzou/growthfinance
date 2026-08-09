package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.CurrencyRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface CurrencyRateRepository extends JpaRepository<CurrencyRate, Long> {
    Optional<CurrencyRate> findByBaseCurrencyAndTargetCurrency(String base, String target);
    List<CurrencyRate> findByBaseCurrency(String base);
}
