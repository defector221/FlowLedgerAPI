package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailStoreType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailStoreTypeRepository extends JpaRepository<RetailStoreType, UUID> {
    List<RetailStoreType> findByOrganizationIdAndDeletedFalseOrderByNameAsc(UUID organizationId);

    Optional<RetailStoreType> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(UUID organizationId, String code);
}
