package com.flowledger.finance.currency.repository;

import com.flowledger.finance.currency.entity.ExchangeRate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {
    Optional<ExchangeRate> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<ExchangeRate> findByOrganizationIdOrderByEffectiveDateDesc(UUID organizationId);

    boolean existsByOrganizationIdAndFromCurrencyAndToCurrencyAndEffectiveDate(
            UUID organizationId, String fromCurrency, String toCurrency, LocalDate effectiveDate);
}
