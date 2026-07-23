package com.flowledger.transport.repository;

import com.flowledger.transport.entity.ShipmentLeg;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShipmentLegRepository extends JpaRepository<ShipmentLeg, UUID> {
    List<ShipmentLeg> findByShipmentIdAndDeletedFalseOrderBySequenceNo(UUID shipmentId);

    List<ShipmentLeg> findByShipmentIdOrderBySequenceNo(UUID shipmentId);

    Optional<ShipmentLeg> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);

    boolean existsByShipmentIdAndSequenceNoAndDeletedFalseAndIdNot(UUID shipmentId, int sequenceNo, UUID id);

    boolean existsByShipmentIdAndSequenceNoAndDeletedFalse(UUID shipmentId, int sequenceNo);

    @Modifying
    @Query("update ShipmentLeg l set l.deleted = true where l.shipmentId = :shipmentId")
    void softDeleteByShipmentId(@Param("shipmentId") UUID shipmentId);

    @Modifying
    @Query("delete from ShipmentLeg l where l.shipmentId = :shipmentId")
    void deleteByShipmentId(@Param("shipmentId") UUID shipmentId);

    List<ShipmentLeg> findByOrganizationIdAndDriverIdAndDeletedFalseAndStatusIn(
            UUID organizationId, UUID driverId, List<com.flowledger.transport.domain.TransportEnums.ShipmentLegStatus> statuses);

    List<ShipmentLeg> findByOrganizationIdAndVehicleIdAndDeletedFalseAndStatusIn(
            UUID organizationId, UUID vehicleId, List<com.flowledger.transport.domain.TransportEnums.ShipmentLegStatus> statuses);
}
