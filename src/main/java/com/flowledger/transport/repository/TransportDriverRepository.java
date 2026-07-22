package com.flowledger.transport.repository;

import com.flowledger.transport.entity.TransportDriver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportDriverRepository extends JpaRepository<TransportDriver, UUID> {
    List<TransportDriver> findByOrganizationIdAndDeletedFalseOrderByNameAsc(UUID organizationId);

    Optional<TransportDriver> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndLicenseNumberIgnoreCaseAndDeletedFalse(UUID organizationId, String number);
}
