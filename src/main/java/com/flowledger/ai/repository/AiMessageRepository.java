package com.flowledger.ai.repository;

import com.flowledger.ai.entity.AiMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {
    List<AiMessage> findByConversationIdAndOrganizationIdOrderByCreatedAtAsc(UUID conversationId, UUID organizationId);
}
