package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailLoyaltyAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailLoyaltyAccountRepository extends JpaRepository<RetailLoyaltyAccount, UUID> {
    Optional<RetailLoyaltyAccount> findByOrganizationIdAndCustomerId(UUID organizationId, UUID customerId);

    Optional<RetailLoyaltyAccount> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
