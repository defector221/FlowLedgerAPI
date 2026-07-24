package com.flowledger.platform.document.repository;

import com.flowledger.platform.document.entity.DocumentAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentAttachmentRepository extends JpaRepository<DocumentAttachment, UUID> {
    List<DocumentAttachment> findByOrganizationIdAndEntityTypeAndEntityIdAndDeletedAtIsNull(
            UUID organizationId, String entityType, UUID entityId);

    Optional<DocumentAttachment> findByIdAndOrganizationIdAndDeletedAtIsNull(UUID id, UUID organizationId);
}
