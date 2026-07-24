package com.flowledger.platform.history.repository;

import com.flowledger.platform.history.entity.DocumentHistoryEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentHistoryRepository extends JpaRepository<DocumentHistoryEntry, UUID> {
    List<DocumentHistoryEntry> findByOrganizationIdAndEntityTypeAndEntityIdOrderByOccurredAtDesc(
            UUID organizationId, String entityType, UUID entityId);
}
