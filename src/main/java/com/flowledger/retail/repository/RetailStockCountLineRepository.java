package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailStockCountLine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailStockCountLineRepository extends JpaRepository<RetailStockCountLine, UUID> {
    List<RetailStockCountLine> findByOrganizationIdAndCountId(UUID organizationId, UUID countId);

    Optional<RetailStockCountLine> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
