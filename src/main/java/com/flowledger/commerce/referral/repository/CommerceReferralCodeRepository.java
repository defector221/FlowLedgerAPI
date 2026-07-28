package com.flowledger.commerce.referral.repository;

import com.flowledger.commerce.referral.entity.CommerceReferralCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceReferralCodeRepository extends JpaRepository<CommerceReferralCode, UUID> {
    Optional<CommerceReferralCode> findByCodeIgnoreCase(String code);

    Optional<CommerceReferralCode> findByCustomerIdAndOrganizationId(UUID customerId, UUID organizationId);
}
