package com.flowledger.lead.repository;

import com.flowledger.lead.entity.Lead;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {
    Optional<Lead> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<Lead> findByOrganizationId(UUID organizationId, Pageable pageable);

    Page<Lead> findByOrganizationIdAndStatus(UUID organizationId, String status, Pageable pageable);

    List<Lead> findByOrganizationId(UUID organizationId);
}
