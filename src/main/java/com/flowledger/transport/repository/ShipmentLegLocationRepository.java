package com.flowledger.transport.repository;

import com.flowledger.transport.entity.ShipmentLegLocation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentLegLocationRepository extends JpaRepository<ShipmentLegLocation, UUID> {
    List<ShipmentLegLocation> findByLegIdOrderByRecordedAtDesc(UUID legId);
}
