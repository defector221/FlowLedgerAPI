package com.flowledger.ai.workflow;

import com.flowledger.ai.config.AiProperties;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.ai.entity.AiWorkflowDraft;
import com.flowledger.ai.repository.AiWorkflowDraftRepository;
import com.flowledger.common.tenant.TenantContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnAiEnabled
public class WorkflowDraftService {
    private final AiProperties properties;
    private final AiWorkflowDraftRepository drafts;
    private final WorkflowSuggestionService textSuggestions;

    public WorkflowDraftService(
            AiProperties properties, AiWorkflowDraftRepository drafts, WorkflowSuggestionService textSuggestions) {
        this.properties = properties;
        this.drafts = drafts;
        this.textSuggestions = textSuggestions;
    }

    public List<AiDtos.WorkflowDraftResponse> list() {
        ensureBuilder();
        UUID org = TenantContext.getOrganizationId();
        return drafts.findByOrganizationIdOrderByUpdatedAtDesc(org).stream()
                .filter(draft -> !isDeleted(draft))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public AiDtos.WorkflowDraftResponse create(AiDtos.WorkflowDraftRequest request) {
        ensureBuilder();
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        AiWorkflowDraft draft = new AiWorkflowDraft();
        draft.setOrganizationId(TenantContext.getOrganizationId());
        draft.setCreatedBy(TenantContext.userId().orElse(null));
        apply(draft, request);
        draft.setStatus("DRAFT");
        return toDto(drafts.save(draft));
    }

    @Transactional
    public AiDtos.WorkflowDraftResponse update(UUID id, AiDtos.WorkflowDraftRequest request) {
        ensureBuilder();
        AiWorkflowDraft draft = requireAlive(id);
        if ("ACTIVE".equalsIgnoreCase(draft.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Deactivate before editing an active workflow");
        }
        apply(draft, request);
        return toDto(drafts.save(draft));
    }

    @Transactional
    public AiDtos.WorkflowDraftResponse activate(UUID id) {
        ensureBuilder();
        AiWorkflowDraft draft = requireAlive(id);
        draft.setStatus("ACTIVE");
        return toDto(drafts.save(draft));
    }

    @Transactional
    public AiDtos.WorkflowDraftResponse deactivate(UUID id) {
        ensureBuilder();
        AiWorkflowDraft draft = requireAlive(id);
        draft.setStatus("DRAFT");
        return toDto(drafts.save(draft));
    }

    @Transactional
    public void softDelete(UUID id) {
        ensureBuilder();
        AiWorkflowDraft draft = requireAlive(id);
        draft.setStatus("DELETED");
        drafts.save(draft);
    }

    /** NL → draft suggestion fields + optional persisted draft shell. */
    @Transactional
    public AiDtos.WorkflowDraftResponse suggestAndCreate(AiDtos.WorkflowNlSuggestRequest request) {
        ensureBuilder();
        String prompt = request == null || request.prompt() == null
                ? ""
                : request.prompt().trim();
        if (prompt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prompt is required");
        }

        Map<String, Object> plan = buildApprovalPlan(prompt);
        AiWorkflowDraft draft = new AiWorkflowDraft();
        draft.setOrganizationId(TenantContext.getOrganizationId());
        draft.setCreatedBy(TenantContext.userId().orElse(null));
        draft.setName(String.valueOf(plan.getOrDefault("name", "AI suggested workflow")));
        draft.setTriggerType(String.valueOf(plan.getOrDefault("triggerType", "DOCUMENT_APPROVAL")));
        draft.setDescription(String.valueOf(plan.getOrDefault("description", prompt)));
        draft.setConditionsJson(String.valueOf(plan.getOrDefault("conditionsJson", "{}")));
        draft.setStepsJson(String.valueOf(plan.getOrDefault("stepsJson", "[]")));
        draft.setSuggestedApprovers(String.valueOf(plan.getOrDefault("suggestedApprovers", "ORGANIZATION_ADMIN")));
        draft.setStatus("DRAFT");
        return toDto(drafts.save(draft));
    }

    public AiDtos.WorkflowSuggestResponse suggestFields(AiDtos.WorkflowSuggestRequest request) {
        // Reuse document text suggest when document-ai enabled; otherwise light heuristic for workflow builder
        if (properties.isDocumentAiEnabled()) {
            return textSuggestions.suggestFromText(request);
        }
        if (!properties.isWorkflowBuilderEnabled()) {
            return new AiDtos.WorkflowSuggestResponse(
                    false,
                    "Workflow builder not configured. Set flowledger.ai.workflow-builder-enabled=true.",
                    Map.of());
        }
        String text = request == null || request.text() == null ? "" : request.text();
        Map<String, Object> plan = buildApprovalPlan(text);
        plan.put("draftOnly", true);
        return new AiDtos.WorkflowSuggestResponse(true, "Advisory workflow suggestion only.", plan);
    }

    private Map<String, Object> buildApprovalPlan(String prompt) {
        String lower = prompt.toLowerCase(Locale.ROOT);
        Map<String, Object> plan = new LinkedHashMap<>();
        String trigger = "MANUAL";
        if (lower.contains("invoice") || lower.contains("payment")) {
            trigger = "PAYMENT_OR_INVOICE";
        } else if (lower.contains("purchase") || lower.contains("po")) {
            trigger = "PURCHASE_ORDER";
        } else if (lower.contains("stock") || lower.contains("inventory")) {
            trigger = "INVENTORY_ADJUSTMENT";
        } else if (lower.contains("gst") || lower.contains("tax")) {
            trigger = "GST_COMPLIANCE";
        }
        plan.put("name", "Approval: " + trigger.replace('_', ' ').toLowerCase(Locale.ROOT));
        plan.put("triggerType", trigger);
        plan.put(
                "description",
                "AI-suggested approval workflow. Activate to require approval before matching sales convert/confirm.");
        plan.put(
                "conditionsJson",
                "{\"source\":\"ai\",\"promptPreview\":\""
                        + prompt.replace("\"", "'").substring(0, Math.min(200, prompt.length()))
                        + "\"}");
        List<Map<String, String>> steps = new ArrayList<>();
        steps.add(Map.of("order", "1", "role", "REQUESTER", "action", "SUBMIT"));
        steps.add(Map.of("order", "2", "role", "ORGANIZATION_ADMIN", "action", "APPROVE"));
        if (lower.contains("finance") || lower.contains("payment") || lower.contains("cash")) {
            steps.add(Map.of("order", "3", "role", "ACCOUNTANT", "action", "REVIEW"));
        }
        plan.put("stepsJson", steps.toString().replace("=", ":"));
        // simpler JSON-ish string for UI
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < steps.size(); i++) {
            Map<String, String> step = steps.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"order\":")
                    .append(step.get("order"))
                    .append(",\"role\":\"")
                    .append(step.get("role"))
                    .append("\",\"action\":\"")
                    .append(step.get("action"))
                    .append("\"}");
        }
        sb.append(']');
        plan.put("stepsJson", sb.toString());
        plan.put("suggestedApprovers", "ORGANIZATION_ADMIN,ACCOUNTANT");
        return plan;
    }

    private void apply(AiWorkflowDraft draft, AiDtos.WorkflowDraftRequest request) {
        if (request.name() != null && !request.name().isBlank()) {
            draft.setName(request.name().trim());
        }
        if (request.triggerType() != null && !request.triggerType().isBlank()) {
            draft.setTriggerType(request.triggerType().trim());
        }
        if (request.description() != null) {
            draft.setDescription(request.description());
        }
        if (request.conditionsJson() != null) {
            draft.setConditionsJson(request.conditionsJson());
        }
        if (request.stepsJson() != null) {
            draft.setStepsJson(request.stepsJson());
        }
        if (request.suggestedApprovers() != null) {
            draft.setSuggestedApprovers(request.suggestedApprovers());
        }
    }

    private AiWorkflowDraft require(UUID id) {
        return drafts.findByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow draft not found"));
    }

    private AiWorkflowDraft requireAlive(UUID id) {
        AiWorkflowDraft draft = require(id);
        if (isDeleted(draft)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow draft not found");
        }
        return draft;
    }

    private static boolean isDeleted(AiWorkflowDraft draft) {
        return "DELETED".equalsIgnoreCase(draft.getStatus());
    }

    private void ensureBuilder() {
        if (!properties.isWorkflowBuilderEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Workflow builder disabled. Set flowledger.ai.workflow-builder-enabled=true.");
        }
    }

    private AiDtos.WorkflowDraftResponse toDto(AiWorkflowDraft draft) {
        return new AiDtos.WorkflowDraftResponse(
                draft.getId(),
                draft.getName(),
                draft.getTriggerType(),
                draft.getDescription(),
                draft.getConditionsJson(),
                draft.getStepsJson(),
                draft.getSuggestedApprovers(),
                draft.getStatus(),
                draft.getCreatedAt(),
                draft.getUpdatedAt());
    }
}
