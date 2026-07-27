package com.flowledger.labels.repository;

import com.flowledger.labels.entity.BarcodePrintJob;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BarcodePrintJobRepository extends JpaRepository<BarcodePrintJob, UUID> {
    List<BarcodePrintJob> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    Optional<BarcodePrintJob> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
