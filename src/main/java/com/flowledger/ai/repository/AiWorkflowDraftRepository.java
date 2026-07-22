package com.flowledger.ai.repository;

import com.flowledger.ai.entity.AiWorkflowDraft;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiWorkflowDraftRepository extends JpaRepository<AiWorkflowDraft, UUID> {
    List<AiWorkflowDraft> findByOrganizationIdOrderByUpdatedAtDesc(UUID organizationId);

    Optional<AiWorkflowDraft> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<AiWorkflowDraft> findByOrganizationIdAndStatusIgnoreCaseOrderByUpdatedAtDesc(
            UUID organizationId, String status);
}
