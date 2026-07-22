package com.flowledger.ai.chat;

import com.flowledger.ai.agent.AgentSelector;
import com.flowledger.ai.agent.AiAgent;
import com.flowledger.ai.agent.AiAgentType;
import com.flowledger.ai.agent.MultiAgentCollaborator;
import com.flowledger.ai.audit.AiAuditService;
import com.flowledger.ai.config.AiProperties;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.ai.embedding.EmbeddingPipeline;
import com.flowledger.ai.entity.AiAgentRun;
import com.flowledger.ai.entity.AiConversation;
import com.flowledger.ai.entity.AiKnowledgeDocument;
import com.flowledger.ai.entity.AiMessage;
import com.flowledger.ai.memory.ConversationMemoryService;
import com.flowledger.ai.prompt.PromptTemplateService;
import com.flowledger.ai.provider.AIProvider;
import com.flowledger.ai.provider.AIProviderRegistry;
import com.flowledger.ai.provider.AiProviderException;
import com.flowledger.ai.rag.RagService;
import com.flowledger.ai.repository.AiAgentRunRepository;
import com.flowledger.ai.repository.AiKnowledgeDocumentRepository;
import com.flowledger.ai.tools.AiToolRegistry;
import com.flowledger.common.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Orchestrates chat: agent selection → optional multi-agent consult → RAG → tools → provider.
 */
@Service
@ConditionalOnAiEnabled
public class ChatOrchestrationService {
    private final AiProperties properties;
    private final AgentSelector agentSelector;
    private final MultiAgentCollaborator collaborator;
    private final PromptTemplateService prompts;
    private final ConversationMemoryService memory;
    private final AiToolRegistry tools;
    private final RagService rag;
    private final AIProviderRegistry providers;
    private final AiAuditService audit;
    private final AiKnowledgeDocumentRepository knowledgeDocuments;
    private final EmbeddingPipeline embeddingPipeline;
    private final AiAgentRunRepository agentRuns;

    public ChatOrchestrationService(
            AiProperties properties,
            AgentSelector agentSelector,
            MultiAgentCollaborator collaborator,
            PromptTemplateService prompts,
            ConversationMemoryService memory,
            AiToolRegistry tools,
            RagService rag,
            AIProviderRegistry providers,
            AiAuditService audit,
            AiKnowledgeDocumentRepository knowledgeDocuments,
            EmbeddingPipeline embeddingPipeline,
            AiAgentRunRepository agentRuns) {
        this.properties = properties;
        this.agentSelector = agentSelector;
        this.collaborator = collaborator;
        this.prompts = prompts;
        this.memory = memory;
        this.tools = tools;
        this.rag = rag;
        this.providers = providers;
        this.audit = audit;
        this.knowledgeDocuments = knowledgeDocuments;
        this.embeddingPipeline = embeddingPipeline;
        this.agentRuns = agentRuns;
    }

    @Transactional
    public AiDtos.ChatResponse chat(AiDtos.ChatRequest request) {
        if (!properties.isChatEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI chat is disabled");
        }
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }

        UUID org = TenantContext.getOrganizationId();
        UUID userId = TenantContext.userId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User context required"));

        seedKnowledgeIfNeeded(org);

        long start = System.currentTimeMillis();
        AiAgent agent = agentSelector.select(request.agent(), request.message());
        AiConversation conversation = memory.getOrCreate(
                request.conversationId(), userId, agent.type().name(), request.message());
        conversation.setAgentType(agent.type().name());

        memory.append(conversation, "user", request.message(), null, null, null, null);

        boolean useRag = request.useRag() == null ? properties.isRagEnabled() : request.useRag();
        String ragContext = useRag ? rag.retrieveContext(request.message(), 3) : "";
        String toolContext = gatherToolContext(agent.allowedTools(), request.message());

        MultiAgentCollaborator.ConsultResult consult = collaborator.consult(agent.type(), request.message());
        String consultBlock =
                consult.specialistNotes().isBlank() ? "" : "Specialist consult notes:\n" + consult.specialistNotes();

        String system = prompts.render(
                agent.systemPromptTemplate(),
                Map.of(
                        "organizationName",
                        org.toString(),
                        "context",
                        (ragContext + "\n" + toolContext + "\n" + consultBlock).trim(),
                        "question",
                        request.message()));

        List<AIProvider.ChatMessage> messages = new ArrayList<>();
        messages.add(new AIProvider.ChatMessage("system", system));
        messages.addAll(memory.historyAsChat(conversation.getId(), 12));

        AIProvider.ChatResult result;
        try {
            result = providers
                    .active()
                    .chat(new AIProvider.ChatRequest(properties.getOpenai().getChatModel(), messages, 0.2));
        } catch (AiProviderException e) {
            audit.record("CHAT", request.message(), null, null, null, null, e.getMessage());
            recordRun(
                    org,
                    userId,
                    conversation.getId(),
                    agent.type(),
                    consult.consultedAgents(),
                    request.message(),
                    start,
                    "ERROR");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI temporarily unavailable");
        } catch (Exception e) {
            audit.record("CHAT", request.message(), null, null, null, null, e.getMessage());
            recordRun(
                    org,
                    userId,
                    conversation.getId(),
                    agent.type(),
                    consult.consultedAgents(),
                    request.message(),
                    start,
                    "ERROR");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI temporarily unavailable");
        }

        AiMessage assistant = memory.append(
                conversation,
                "assistant",
                result.content(),
                result.model(),
                result.promptTokens(),
                result.completionTokens(),
                (int) result.latencyMs());

        int tokens = (result.promptTokens() == null ? 0 : result.promptTokens())
                + (result.completionTokens() == null ? 0 : result.completionTokens());
        audit.record(
                "CHAT", request.message(), result.content(), result.model(), tokens, (int) result.latencyMs(), null);

        recordRun(
                org,
                userId,
                conversation.getId(),
                agent.type(),
                consult.consultedAgents(),
                request.message(),
                start,
                "OK");

        return new AiDtos.ChatResponse(
                conversation.getId(),
                assistant.getId(),
                agent.type().name(),
                result.content(),
                result.model(),
                result.latencyMs(),
                List.copyOf(consult.consultedAgents()));
    }

    /** Forced Global Ask Agent entry for FAB clients. */
    @Transactional
    public AiDtos.ChatResponse ask(AiDtos.ChatRequest request) {
        String message = request == null ? null : request.message();
        return chat(new AiDtos.ChatRequest(
                request == null ? null : request.conversationId(),
                message,
                AiAgentType.ASK.name(),
                request == null ? null : request.useRag()));
    }

    private void recordRun(
            UUID org,
            UUID userId,
            UUID conversationId,
            AiAgentType primary,
            List<String> consulted,
            String message,
            long start,
            String status) {
        try {
            AiAgentRun run = new AiAgentRun();
            run.setOrganizationId(org);
            run.setUserId(userId);
            run.setConversationId(conversationId);
            run.setPrimaryAgent(primary.name());
            run.setConsultedAgents(consulted == null || consulted.isEmpty() ? null : String.join(",", consulted));
            run.setMessagePreview(message == null ? null : message.substring(0, Math.min(500, message.length())));
            run.setLatencyMs((int) (System.currentTimeMillis() - start));
            run.setStatus(status);
            agentRuns.save(run);
        } catch (Exception ignored) {
            // audit best-effort
        }
    }

    private String gatherToolContext(Set<String> allowed, String message) {
        Set<String> toRun = selectToolsForMessage(allowed, message);
        if (toRun.isEmpty()) {
            if (allowed.contains("dashboard")) {
                toRun = Set.of("dashboard");
            } else {
                return "";
            }
        }
        return "Tool data:\n" + tools.invokeAllowed(toRun, message);
    }

    private Set<String> selectToolsForMessage(Set<String> allowed, String message) {
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT);
        java.util.LinkedHashSet<String> selected = new java.util.LinkedHashSet<>();
        addIf(allowed, selected, "inventory", m, "stock", "inventory", "reorder", "warehouse");
        addIf(allowed, selected, "sales", m, "sales", "invoice", "revenue", "pipeline");
        addIf(allowed, selected, "purchase", m, "purchase", "po", "grn", "vendor");
        addIf(allowed, selected, "payment", m, "payment", "receivable", "payable", "cash", "collect", "overdue");
        addIf(allowed, selected, "gst", m, "gst", "tax", "cgst", "sgst", "igst");
        addIf(allowed, selected, "customer", m, "customer", "crm");
        addIf(allowed, selected, "supplier", m, "supplier", "vendor");
        addIf(allowed, selected, "accounting", m, "ledger", "journal", "trial", "p&l", "balance sheet", "reconcile");
        addIf(allowed, selected, "report", m, "report", "gstr");
        addIf(allowed, selected, "dashboard", m, "dashboard", "overview", "kpi", "summary", "budget", "profit");
        return selected;
    }

    private static void addIf(Set<String> allowed, Set<String> selected, String tool, String message, String... keys) {
        if (!allowed.contains(tool)) {
            return;
        }
        for (String k : keys) {
            if (message.contains(k)) {
                selected.add(tool);
                return;
            }
        }
    }

    private void seedKnowledgeIfNeeded(UUID org) {
        if (knowledgeDocuments.countByOrganizationId(org) > 0) {
            return;
        }
        seedDoc(
                org,
                "GST filing basics",
                "GST",
                "FlowLedger GST tip: Use GSTR-1 style sales reports for outward supplies. "
                        + "CGST/SGST apply for intra-state; IGST for inter-state. Reverse charge must be flagged on invoices.");
        seedDoc(
                org,
                "Inventory SOP stub",
                "SOP",
                "Inventory SOP: Monitor low-stock and reorder alerts daily. Confirm goods receipts before stock is saleable. "
                        + "Do not rely on AI for stock posting — use ERP inventory transactions.");
        seedDoc(
                org,
                "Collections playbook",
                "FINANCE",
                "Collections: Prioritize overdue sales invoices from the dashboard. Match incoming receipts to invoices via payments module.");
    }

    private void seedDoc(UUID org, String title, String type, String content) {
        AiKnowledgeDocument d = new AiKnowledgeDocument();
        d.setOrganizationId(org);
        d.setTitle(title);
        d.setDocType(type);
        d.setContent(content);
        d.setContentHash(EmbeddingPipeline.sha256(content));
        AiKnowledgeDocument saved = knowledgeDocuments.save(d);
        try {
            embeddingPipeline.embedKnowledge(saved);
        } catch (Exception ignored) {
            // embeddings optional
        }
    }
}
