package com.flowledger.ai.memory;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.entity.AiConversation;
import com.flowledger.ai.entity.AiMessage;
import com.flowledger.ai.provider.AIProvider;
import com.flowledger.ai.repository.AiConversationRepository;
import com.flowledger.ai.repository.AiMessageRepository;
import com.flowledger.common.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnAiEnabled
public class ConversationMemoryService {
    private final AiConversationRepository conversations;
    private final AiMessageRepository messages;

    public ConversationMemoryService(AiConversationRepository conversations, AiMessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    @Transactional
    public AiConversation getOrCreate(UUID conversationId, UUID userId, String agentType, String titleSeed) {
        UUID org = TenantContext.getOrganizationId();
        if (conversationId != null) {
            return conversations
                    .findByIdAndOrganizationId(conversationId, org)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        }
        AiConversation aiConversation = new AiConversation();
        aiConversation.setOrganizationId(org);
        aiConversation.setUserId(userId);
        aiConversation.setAgentType(agentType);
        aiConversation.setTitle(titleFrom(titleSeed));
        aiConversation.setStatus("ACTIVE");
        return conversations.save(aiConversation);
    }

    @Transactional(readOnly = true)
    public List<AiConversation> listForUser(UUID userId) {
        return conversations.findByOrganizationIdAndUserIdOrderByUpdatedAtDesc(
                TenantContext.getOrganizationId(), userId);
    }

    @Transactional(readOnly = true)
    public List<AiMessage> messages(UUID conversationId) {
        return messages.findByConversationIdAndOrganizationIdOrderByCreatedAtAsc(
                conversationId, TenantContext.getOrganizationId());
    }

    @Transactional
    public AiMessage append(
            AiConversation conversation,
            String role,
            String content,
            String model,
            Integer promptTokens,
            Integer completionTokens,
            Integer latencyMs) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversation.getId());
        message.setOrganizationId(conversation.getOrganizationId());
        message.setRole(role);
        message.setContent(content);
        message.setModel(model);
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setLatencyMs(latencyMs);
        AiMessage saved = messages.save(message);
        conversation.setUpdatedAt(java.time.OffsetDateTime.now());
        if (conversation.getTitle() == null || conversation.getTitle().isBlank()) {
            conversation.setTitle(titleFrom(content));
        }
        conversations.save(conversation);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AIProvider.ChatMessage> historyAsChat(UUID conversationId, int limit) {
        List<AiMessage> all = messages(conversationId);
        int from = Math.max(0, all.size() - limit);
        List<AIProvider.ChatMessage> out = new ArrayList<>();
        for (AiMessage message : all.subList(from, all.size())) {
            out.add(new AIProvider.ChatMessage(message.getRole(), message.getContent()));
        }
        return out;
    }

    private static String titleFrom(String seed) {
        if (seed == null || seed.isBlank()) {
            return "New conversation";
        }
        String trimmed = seed.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80) + "...";
    }
}
