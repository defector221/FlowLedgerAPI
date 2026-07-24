package com.flowledger.platform.document.repository;

import com.flowledger.platform.document.entity.DocumentComment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentCommentRepository extends JpaRepository<DocumentComment, UUID> {
    List<DocumentComment> findByOrganizationIdAndEntityTypeAndEntityIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            UUID organizationId, String entityType, UUID entityId);

    Optional<DocumentComment> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);
}
