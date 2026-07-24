package com.flowledger.finance.currency.repository;

import com.flowledger.finance.currency.entity.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CurrencyRepository extends JpaRepository<Currency, UUID> {
    Optional<Currency> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Currency> findByOrganizationIdOrderByCodeAsc(UUID organizationId);

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);

    @Modifying
    @Query("update Currency c set c.base = false where c.organizationId = :org")
    void clearBase(UUID org);
}
