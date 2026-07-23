package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailLoyaltyTier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailLoyaltyTierRepository extends JpaRepository<RetailLoyaltyTier, UUID> {
    List<RetailLoyaltyTier> findByOrganizationIdAndDeletedFalseOrderByMinPointsAsc(UUID organizationId);

    Optional<RetailLoyaltyTier> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(UUID organizationId, String code);
}
