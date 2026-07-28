package com.flowledger.commerce.customer.repository;

import com.flowledger.commerce.customer.entity.CommerceCustomerMembership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceCustomerMembershipRepository extends JpaRepository<CommerceCustomerMembership, UUID> {
    Page<CommerceCustomerMembership> findByOrganizationId(UUID organizationId, Pageable pageable);

    Optional<CommerceCustomerMembership> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<CommerceCustomerMembership> findByCustomerIdAndOrganizationId(UUID customerId, UUID organizationId);
}
