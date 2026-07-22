package com.flowledger.transport.repository;

import com.flowledger.transport.entity.ShipmentEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent, UUID> {
    List<ShipmentEvent> findByShipmentIdOrderByOccurredAtAsc(UUID shipmentId);
}
