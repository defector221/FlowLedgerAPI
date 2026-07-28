package com.flowledger.commerce.capability.repository;

import com.flowledger.commerce.capability.entity.MerchantCapabilityProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantCapabilityProfileRepository extends JpaRepository<MerchantCapabilityProfile, UUID> {
    Optional<MerchantCapabilityProfile> findByOrganizationId(UUID organizationId);
}
