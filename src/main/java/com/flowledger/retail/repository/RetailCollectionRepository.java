package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailCollection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailCollectionRepository extends JpaRepository<RetailCollection, UUID> {
    List<RetailCollection> findByOrganizationIdAndDeletedFalseOrderByNameAsc(UUID organizationId);

    Optional<RetailCollection> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(UUID organizationId, String code);
}
