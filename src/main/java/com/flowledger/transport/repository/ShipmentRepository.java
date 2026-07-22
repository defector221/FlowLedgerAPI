package com.flowledger.transport.repository;

import com.flowledger.transport.entity.Shipment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID>, JpaSpecificationExecutor<Shipment> {
    Optional<Shipment> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);
}
