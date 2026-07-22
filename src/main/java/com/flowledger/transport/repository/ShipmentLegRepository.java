package com.flowledger.transport.repository;

import com.flowledger.transport.entity.ShipmentLeg;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ShipmentLegRepository extends JpaRepository<ShipmentLeg, UUID> {
    List<ShipmentLeg> findByShipmentIdOrderBySequenceNo(UUID shipmentId);

    @Modifying
    @Query("delete from ShipmentLeg l where l.shipmentId = :shipmentId")
    void deleteByShipmentId(UUID shipmentId);
}
