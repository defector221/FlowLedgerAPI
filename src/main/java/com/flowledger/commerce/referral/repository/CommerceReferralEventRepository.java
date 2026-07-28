package com.flowledger.commerce.referral.repository;

import com.flowledger.commerce.referral.entity.CommerceReferralEvent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceReferralEventRepository extends JpaRepository<CommerceReferralEvent, UUID> {
    Optional<CommerceReferralEvent> findByRefereeCustomerIdAndOrganizationId(UUID refereeCustomerId, UUID organizationId);
}
