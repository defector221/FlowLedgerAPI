package com.flowledger.transport.repository;

import com.flowledger.transport.entity.ShipmentLegDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentLegDocumentRepository extends JpaRepository<ShipmentLegDocument, UUID> {
    List<ShipmentLegDocument> findByLegIdAndDeletedFalseOrderByCreatedAtDesc(UUID legId);

    Optional<ShipmentLegDocument> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);
}
