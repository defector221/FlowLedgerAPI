package com.flowledger.ai.controller;

import com.flowledger.ai.agent.AgentCatalogService;
import com.flowledger.ai.chat.ChatOrchestrationService;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.ai.entity.AiConversation;
import com.flowledger.ai.entity.AiMessage;
import com.flowledger.ai.memory.ConversationMemoryService;
import com.flowledger.ai.workflow.VoiceAiService;
import com.flowledger.common.security.UserPrincipal;
import com.flowledger.common.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ai")
@ConditionalOnAiEnabled
public class AiChatController {
    private final ChatOrchestrationService chat;
    private final ConversationMemoryService memory;
    private final AgentCatalogService agents;
    private final VoiceAiService voiceAi;

    public AiChatController(
            ChatOrchestrationService chat,
            ConversationMemoryService memory,
            AgentCatalogService agents,
            VoiceAiService voiceAi) {
        this.chat = chat;
        this.memory = memory;
        this.agents = agents;
        this.voiceAi = voiceAi;
    }

    @GetMapping("/agents")
    @PreAuthorize("hasAuthority('AI_CHAT') or hasRole('ORGANIZATION_ADMIN')")
    public List<AiDtos.AgentInfo> listAgents(@AuthenticationPrincipal UserPrincipal principal) {
        ensureTenant(principal);
        return agents.list();
    }

    @PostMapping("/chat")
    @PreAuthorize("hasAuthority('AI_CHAT') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.ChatResponse chat(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody AiDtos.ChatRequest request) {
        ensureTenant(principal);
        return chat.chat(request);
    }

    @PostMapping("/ask")
    @PreAuthorize("hasAuthority('AI_CHAT') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.ChatResponse ask(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody AiDtos.ChatRequest request) {
        ensureTenant(principal);
        return chat.ask(request);
    }

    @PostMapping("/voice-chat")
    @PreAuthorize("hasAuthority('AI_CHAT') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.ChatResponse voiceChat(
            @AuthenticationPrincipal UserPrincipal principal, @RequestBody AiDtos.VoiceChatRequest request) {
        ensureTenant(principal);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request required");
        }
        AiDtos.VoiceAiResponse transcribed =
                voiceAi.transcribe(new AiDtos.VoiceAiRequest(request.contentType(), request.audioBase64()));
        if (!transcribed.configured()
                || transcribed.transcript() == null
                || transcribed.transcript().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    transcribed.message() == null ? "AI temporarily unavailable" : transcribed.message());
        }
        return chat.chat(
                new AiDtos.ChatRequest(request.conversationId(), transcribed.transcript(), request.agent(), true));
    }

    @GetMapping("/conversations")
    @PreAuthorize("hasAuthority('AI_CHAT') or hasRole('ORGANIZATION_ADMIN')")
    public List<AiDtos.ConversationResponse> conversations(@AuthenticationPrincipal UserPrincipal principal) {
        ensureTenant(principal);
        return memory.listForUser(principal.getId()).stream()
                .map(this::toConversation)
                .toList();
    }

    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize("hasAuthority('AI_CHAT') or hasRole('ORGANIZATION_ADMIN')")
    public List<AiDtos.MessageResponse> messages(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        ensureTenant(principal);
        AiConversation conversation = memory.getOrCreate(id, principal.getId(), null, null);
        if (!conversation.getUserId().equals(principal.getId())
                && principal.getAuthorities().stream()
                        .noneMatch(a -> a.getAuthority().equals("ROLE_ORGANIZATION_ADMIN")
                                || a.getAuthority().equals("AI_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conversation access denied");
        }
        return memory.messages(id).stream().map(this::toMessage).toList();
    }

    private void ensureTenant(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        TenantContext.set(principal.getOrgId(), principal.getId());
    }

    private AiDtos.ConversationResponse toConversation(AiConversation c) {
        return new AiDtos.ConversationResponse(
                c.getId(), c.getTitle(), c.getAgentType(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt());
    }

    private AiDtos.MessageResponse toMessage(AiMessage m) {
        return new AiDtos.MessageResponse(
                m.getId(),
                m.getRole(),
                m.getContent(),
                m.getModel(),
                m.getPromptTokens(),
                m.getCompletionTokens(),
                m.getLatencyMs(),
                m.getCreatedAt());
    }
}
