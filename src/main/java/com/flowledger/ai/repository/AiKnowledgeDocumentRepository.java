package com.flowledger.ai.repository;

import com.flowledger.ai.entity.AiKnowledgeDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiKnowledgeDocumentRepository extends JpaRepository<AiKnowledgeDocument, UUID> {
    List<AiKnowledgeDocument> findByOrganizationIdOrderByUpdatedAtDesc(UUID organizationId);

    Optional<AiKnowledgeDocument> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByOrganizationId(UUID organizationId);

    @Query(
            """
            select d from AiKnowledgeDocument d
            where d.organizationId = :org
              and (lower(d.title) like lower(concat('%', :q, '%'))
                   or lower(d.content) like lower(concat('%', :q, '%')))
            order by d.updatedAt desc
            """)
    List<AiKnowledgeDocument> search(@Param("org") UUID organizationId, @Param("q") String query);
}
