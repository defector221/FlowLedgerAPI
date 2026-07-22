package com.flowledger.subscription.repository;

import com.flowledger.subscription.entity.OrganizationSubscription;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationSubscriptionRepository extends JpaRepository<OrganizationSubscription, UUID> {
    Optional<OrganizationSubscription> findByOrganizationId(UUID organizationId);
}
