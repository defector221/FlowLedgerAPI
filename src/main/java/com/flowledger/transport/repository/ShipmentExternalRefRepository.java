package com.flowledger.transport.repository;

import com.flowledger.transport.entity.ShipmentExternalRef;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentExternalRefRepository extends JpaRepository<ShipmentExternalRef, UUID> {
    Optional<ShipmentExternalRef> findByShipmentIdAndProviderTypeAndExternalId(
            UUID shipmentId, String providerType, String externalId);
}
