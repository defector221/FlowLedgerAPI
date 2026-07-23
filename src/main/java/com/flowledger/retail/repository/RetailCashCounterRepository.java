package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailCashCounter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailCashCounterRepository extends JpaRepository<RetailCashCounter, UUID> {
    List<RetailCashCounter> findByOrganizationIdAndStoreIdAndDeletedFalseOrderByNameAsc(
            UUID organizationId, UUID storeId);

    Optional<RetailCashCounter> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndStoreIdAndCodeIgnoreCaseAndDeletedFalse(
            UUID organizationId, UUID storeId, String code);
}
