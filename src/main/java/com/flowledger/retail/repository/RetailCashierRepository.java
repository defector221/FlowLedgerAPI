package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailCashier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailCashierRepository extends JpaRepository<RetailCashier, UUID> {
    List<RetailCashier> findByOrganizationIdAndStoreIdAndDeletedFalseOrderByDisplayNameAsc(
            UUID organizationId, UUID storeId);

    Optional<RetailCashier> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndStoreIdAndUserIdAndDeletedFalse(
            UUID organizationId, UUID storeId, UUID userId);
}
