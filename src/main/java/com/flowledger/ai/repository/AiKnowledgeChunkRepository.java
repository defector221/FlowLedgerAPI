package com.flowledger.ai.repository;

import com.flowledger.ai.entity.AiKnowledgeChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiKnowledgeChunkRepository extends JpaRepository<AiKnowledgeChunk, UUID> {
    List<AiKnowledgeChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

    List<AiKnowledgeChunk> findByOrganizationId(UUID organizationId);

    void deleteByDocumentId(UUID documentId);

    long countByOrganizationId(UUID organizationId);
}
