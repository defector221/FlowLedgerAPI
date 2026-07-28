package com.flowledger.commerce.integration.repository;

import com.flowledger.commerce.integration.entity.MerchantIntegrationProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantIntegrationProfileRepository extends JpaRepository<MerchantIntegrationProfile, UUID> {
    Optional<MerchantIntegrationProfile> findByOrganizationId(UUID organizationId);
}
