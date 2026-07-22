package com.flowledger.transport.repository;

import com.flowledger.transport.entity.ShipmentLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ShipmentLineRepository extends JpaRepository<ShipmentLine, UUID> {
    List<ShipmentLine> findByShipmentIdOrderByLineOrder(UUID shipmentId);
    @Modifying
    @Query("delete from ShipmentLine l where l.shipmentId = :shipmentId")
    void deleteByShipmentId(UUID shipmentId);
}
