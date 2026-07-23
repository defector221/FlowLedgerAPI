package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailTerminal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailTerminalRepository extends JpaRepository<RetailTerminal, UUID> {
    List<RetailTerminal> findByOrganizationIdAndStoreIdAndDeletedFalseOrderByNameAsc(UUID organizationId, UUID storeId);

    Optional<RetailTerminal> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndStoreIdAndCodeIgnoreCaseAndDeletedFalse(
            UUID organizationId, UUID storeId, String code);
}
