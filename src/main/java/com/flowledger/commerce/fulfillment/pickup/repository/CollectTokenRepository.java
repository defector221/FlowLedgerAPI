package com.flowledger.commerce.fulfillment.pickup.repository;

import com.flowledger.commerce.fulfillment.pickup.entity.CollectToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectTokenRepository extends JpaRepository<CollectToken, UUID> {
    Optional<CollectToken> findByTokenHash(String tokenHash);

    Optional<CollectToken> findByCollectCodeAndVerifiedAtIsNull(String collectCode);

    Optional<CollectToken> findFirstByFulfillmentOrderIdAndVerifiedAtIsNullOrderByCreatedAtDesc(
            UUID fulfillmentOrderId);
}
