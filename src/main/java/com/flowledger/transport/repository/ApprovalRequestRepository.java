package com.flowledger.transport.repository;

import com.flowledger.transport.domain.TransportEnums.ApprovalStatus;
import com.flowledger.transport.entity.ApprovalRequest;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {
    Optional<ApprovalRequest> findFirstByOrganizationIdAndEntityTypeAndEntityIdAndStatusOrderByRequestedAtDesc(
            UUID organizationId, String entityType, UUID entityId, ApprovalStatus status);

    Optional<ApprovalRequest> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<ApprovalRequest> findByOrganizationIdAndStatusOrderByRequestedAtDesc(
            UUID organizationId, ApprovalStatus status);

    List<ApprovalRequest> findByOrganizationIdAndEntityTypeInOrderByRequestedAtDesc(
            UUID organizationId, Collection<String> entityTypes);

    List<ApprovalRequest> findByOrganizationIdAndEntityTypeAndEntityIdOrderByRequestedAtDesc(
            UUID organizationId, String entityType, UUID entityId);
}
