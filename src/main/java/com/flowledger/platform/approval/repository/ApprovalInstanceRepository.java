package com.flowledger.platform.approval.repository;

import com.flowledger.platform.approval.domain.ApprovalStatus;
import com.flowledger.platform.approval.entity.ApprovalInstance;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalInstanceRepository extends JpaRepository<ApprovalInstance, UUID> {
    Optional<ApprovalInstance> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<ApprovalInstance> findFirstByOrganizationIdAndEntityTypeAndEntityIdAndStatusOrderByRequestedAtDesc(
            UUID organizationId, String entityType, UUID entityId, ApprovalStatus status);

    Page<ApprovalInstance> findByOrganizationIdAndStatus(UUID organizationId, ApprovalStatus status, Pageable pageable);

    Page<ApprovalInstance> findByOrganizationId(UUID organizationId, Pageable pageable);

    List<ApprovalInstance> findByOrganizationIdAndEntityTypeAndEntityId(
            UUID organizationId, String entityType, UUID entityId);
}
