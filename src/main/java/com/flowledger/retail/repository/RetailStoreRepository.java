package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailStoreRepository extends JpaRepository<RetailStore, UUID> {
    List<RetailStore> findByOrganizationIdAndDeletedFalseOrderByNameAsc(UUID organizationId);

    Optional<RetailStore> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(UUID organizationId, String code);
}
