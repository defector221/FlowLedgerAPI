package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailStockCount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailStockCountRepository extends JpaRepository<RetailStockCount, UUID> {
    List<RetailStockCount> findByOrganizationIdAndDeletedFalseOrderByCreatedAtDesc(UUID organizationId);

    Optional<RetailStockCount> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);
}
