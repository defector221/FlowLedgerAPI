package com.flowledger.transport.repository;

import com.flowledger.transport.entity.TransportVehicle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportVehicleRepository extends JpaRepository<TransportVehicle, UUID> {
    List<TransportVehicle> findByOrganizationIdAndDeletedFalseOrderByVehicleNumberAsc(UUID organizationId);
    Optional<TransportVehicle> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);
    boolean existsByOrganizationIdAndVehicleNumberIgnoreCaseAndDeletedFalse(UUID organizationId, String number);
}
