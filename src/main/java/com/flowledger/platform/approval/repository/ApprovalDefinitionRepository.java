package com.flowledger.platform.approval.repository;

import com.flowledger.platform.approval.entity.ApprovalDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalDefinitionRepository extends JpaRepository<ApprovalDefinition, UUID> {
    List<ApprovalDefinition> findByOrganizationIdAndEntityTypeAndActiveTrue(UUID organizationId, String entityType);

    Optional<ApprovalDefinition> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
