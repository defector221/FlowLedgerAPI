package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailStoreCredit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailStoreCreditRepository extends JpaRepository<RetailStoreCredit, UUID> {
    Optional<RetailStoreCredit> findByOrganizationIdAndCustomerId(UUID organizationId, UUID customerId);
}
