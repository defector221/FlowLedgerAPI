package com.flowledger.commerce.onboarding.repository;

import com.flowledger.commerce.onboarding.domain.MerchantOnboardingState;
import com.flowledger.commerce.onboarding.entity.MerchantOnboarding;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantOnboardingRepository extends JpaRepository<MerchantOnboarding, UUID> {
    Optional<MerchantOnboarding> findByOrganizationId(UUID organizationId);

    Page<MerchantOnboarding> findAll(Pageable pageable);

    long countByState(MerchantOnboardingState state);
}
