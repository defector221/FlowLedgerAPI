package com.flowledger.ai.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowledger.ai.agent.AiAgent;
import com.flowledger.ai.agent.AiAgentType;
import com.flowledger.ai.agent.AgentSelector;
import com.flowledger.ai.agent.MultiAgentCollaborator;
import com.flowledger.ai.audit.AiAuditService;
import com.flowledger.ai.config.AiProperties;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.ai.embedding.EmbeddingPipeline;
import com.flowledger.ai.entity.AiConversation;
import com.flowledger.ai.entity.AiMessage;
import com.flowledger.ai.memory.ConversationMemoryService;
import com.flowledger.ai.prompt.PromptTemplateService;
import com.flowledger.ai.provider.AIProvider;
import com.flowledger.ai.provider.AIProviderRegistry;
import com.flowledger.ai.rag.RagService;
import com.flowledger.ai.repository.AiAgentRunRepository;
import com.flowledger.ai.repository.AiKnowledgeDocumentRepository;
import com.flowledger.ai.tools.AiToolRegistry;
import com.flowledger.common.tenant.TenantContext;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {
    @Mock
    AgentSelector agentSelector;

    @Mock
    MultiAgentCollaborator collaborator;

    @Mock
    PromptTemplateService prompts;

    @Mock
    ConversationMemoryService memory;

    @Mock
    AiToolRegistry tools;

    @Mock
    RagService rag;

    @Mock
    AIProviderRegistry providers;

    @Mock
    AIProvider provider;

    @Mock
    AiAuditService audit;

    @Mock
    AiKnowledgeDocumentRepository knowledgeDocuments;

    @Mock
    EmbeddingPipeline embeddingPipeline;

    @Mock
    AiAgentRunRepository agentRuns;

    private ChatOrchestrationService service;
    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(orgId, userId);
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setChatEnabled(true);
        properties.setRagEnabled(true);
        properties.setMultiAgentEnabled(true);
        properties.getOpenai().setChatModel("gpt-4o-mini");
        service = new ChatOrchestrationService(
                properties,
                agentSelector,
                collaborator,
                prompts,
                memory,
                tools,
                rag,
                providers,
                audit,
                knowledgeDocuments,
                embeddingPipeline,
                agentRuns);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void chatReturnsMockProviderAnswer() {
        AiAgent agent = new AiAgent() {
            @Override
            public AiAgentType type() {
                return AiAgentType.ASK;
            }

            @Override
            public String systemPromptTemplate() {
                return "agent-ask";
            }

            @Override
            public Set<String> allowedTools() {
                return Set.of("dashboard");
            }
        };
        when(knowledgeDocuments.countByOrganizationId(orgId)).thenReturn(1L);
        when(agentSelector.select(any(), anyString())).thenReturn(agent);
        when(collaborator.consult(any(), anyString()))
                .thenReturn(new MultiAgentCollaborator.ConsultResult(List.of("CFO"), "### AI CFO\nok"));

        AiConversation conversation = new AiConversation();
        conversation.setId(UUID.randomUUID());
        conversation.setOrganizationId(orgId);
        conversation.setUserId(userId);
        when(memory.getOrCreate(any(), eq(userId), anyString(), anyString())).thenReturn(conversation);
        when(memory.append(any(), eq("user"), anyString(), any(), any(), any(), any()))
                .thenReturn(new AiMessage());
        when(rag.retrieveContext(anyString(), anyInt())).thenReturn("");
        when(tools.invokeAllowed(any(), anyString())).thenReturn("dashboard ok");
        when(prompts.render(eq("agent-ask"), any())).thenReturn("system prompt");
        when(memory.historyAsChat(any(), anyInt()))
                .thenReturn(List.of(new AIProvider.ChatMessage("user", "summary please")));
        when(providers.active()).thenReturn(provider);
        when(provider.chat(any()))
                .thenReturn(new AIProvider.ChatResult(
                        "AI mock response (no API key configured): summary please", "mock-gpt-4o-mini", 0, 10, 5L));

        AiMessage assistant = new AiMessage();
        assistant.setId(UUID.randomUUID());
        assistant.setContent("AI mock response (no API key configured): summary please");
        when(memory.append(any(), eq("assistant"), anyString(), anyString(), any(), any(), any()))
                .thenReturn(assistant);

        AiDtos.ChatResponse response =
                service.chat(new AiDtos.ChatRequest(null, "Give me a dashboard summary", null, true));

        assertEquals(conversation.getId(), response.conversationId());
        assertTrue(response.content().contains("AI mock response"));
        assertEquals("ASK", response.agent());
        assertEquals(List.of("CFO"), response.consultedAgents());
        verify(audit).record(eq("CHAT"), anyString(), anyString(), anyString(), any(), any(), eq(null));
    }
}
