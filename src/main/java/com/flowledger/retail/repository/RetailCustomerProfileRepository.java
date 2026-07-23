package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailCustomerProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailCustomerProfileRepository extends JpaRepository<RetailCustomerProfile, UUID> {
    Optional<RetailCustomerProfile> findByOrganizationIdAndCustomerId(UUID organizationId, UUID customerId);
}
