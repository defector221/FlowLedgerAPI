package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailInventoryLocation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailInventoryLocationRepository extends JpaRepository<RetailInventoryLocation, UUID> {
    List<RetailInventoryLocation> findByOrganizationIdAndStoreIdAndDeletedFalseOrderByNameAsc(
            UUID organizationId, UUID storeId);

    Optional<RetailInventoryLocation> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndStoreIdAndCodeIgnoreCaseAndDeletedFalse(
            UUID organizationId, UUID storeId, String code);
}
