package com.flowledger.commerce.cart.repository;

import com.flowledger.commerce.cart.entity.CommerceCartItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceCartItemRepository extends JpaRepository<CommerceCartItem, UUID> {
    List<CommerceCartItem> findByCartIdOrderByCreatedAtAsc(UUID cartId);

    Optional<CommerceCartItem> findByCartIdAndProductId(UUID cartId, UUID productId);

    Optional<CommerceCartItem> findByIdAndCartId(UUID id, UUID cartId);

    void deleteByCartId(UUID cartId);
}
