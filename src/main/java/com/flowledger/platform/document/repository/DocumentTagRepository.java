package com.flowledger.platform.document.repository;

import com.flowledger.platform.document.entity.DocumentTag;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTagRepository extends JpaRepository<DocumentTag, UUID> {
    List<DocumentTag> findByOrganizationIdAndEntityTypeAndEntityId(
            UUID organizationId, String entityType, UUID entityId);

    Optional<DocumentTag> findByOrganizationIdAndEntityTypeAndEntityIdAndTag(
            UUID organizationId, String entityType, UUID entityId, String tag);

    void deleteByOrganizationIdAndEntityTypeAndEntityIdAndTag(
            UUID organizationId, String entityType, UUID entityId, String tag);
}
