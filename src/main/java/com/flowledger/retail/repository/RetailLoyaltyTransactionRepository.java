package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailLoyaltyTransaction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailLoyaltyTransactionRepository extends JpaRepository<RetailLoyaltyTransaction, UUID> {
    List<RetailLoyaltyTransaction> findByOrganizationIdAndAccountIdOrderByCreatedAtDesc(
            UUID organizationId, UUID accountId);
}
