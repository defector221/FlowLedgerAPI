package com.flowledger.commerce.order.repository;

import com.flowledger.commerce.order.entity.CommerceOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceOrderRepository extends JpaRepository<CommerceOrder, UUID> {
    Page<CommerceOrder> findByCustomerIdOrderByPlacedAtDesc(UUID customerId, Pageable pageable);

    Optional<CommerceOrder> findByIdAndCustomerId(UUID id, UUID customerId);

    Optional<CommerceOrder> findByCheckoutSessionId(UUID checkoutSessionId);

    long countByOrganizationId(UUID organizationId);
}
