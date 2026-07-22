package com.flowledger.transport.repository;

import com.flowledger.transport.entity.ApprovalRequest;
import com.flowledger.transport.domain.TransportEnums.ApprovalStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {
    Optional<ApprovalRequest> findFirstByOrganizationIdAndEntityTypeAndEntityIdAndStatusOrderByRequestedAtDesc(
            UUID organizationId, String entityType, UUID entityId, ApprovalStatus status);
}
