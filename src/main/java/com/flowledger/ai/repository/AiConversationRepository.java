package com.flowledger.ai.repository;

import com.flowledger.ai.entity.AiConversation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {
    List<AiConversation> findByOrganizationIdAndUserIdOrderByUpdatedAtDesc(UUID organizationId, UUID userId);

    Optional<AiConversation> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
