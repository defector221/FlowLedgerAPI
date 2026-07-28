package com.flowledger.commerce.cart.repository;

import com.flowledger.commerce.cart.domain.CartStatus;
import com.flowledger.commerce.cart.entity.CommerceCart;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceCartRepository extends JpaRepository<CommerceCart, UUID> {
    Optional<CommerceCart> findByCustomerIdAndStoreIdAndStatus(UUID customerId, UUID storeId, CartStatus status);

    Optional<CommerceCart> findByIdAndCustomerId(UUID id, UUID customerId);
}
