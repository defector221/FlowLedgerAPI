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
                .filter(d -> !isDeleted(d))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public AiDtos.WorkflowDraftResponse create(AiDtos.WorkflowDraftRequest request) {
        ensureBuilder();
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        AiWorkflowDraft d = new AiWorkflowDraft();
        d.setOrganizationId(TenantContext.getOrganizationId());
        d.setCreatedBy(TenantContext.userId().orElse(null));
        apply(d, request);
        d.setStatus("DRAFT");
        return toDto(drafts.save(d));
    }

    @Transactional
    public AiDtos.WorkflowDraftResponse update(UUID id, AiDtos.WorkflowDraftRequest request) {
        ensureBuilder();
        AiWorkflowDraft d = requireAlive(id);
        if ("ACTIVE".equalsIgnoreCase(d.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Deactivate before editing an active workflow");
        }
        apply(d, request);
        return toDto(drafts.save(d));
    }

    @Transactional
    public AiDtos.WorkflowDraftResponse activate(UUID id) {
        ensureBuilder();
        AiWorkflowDraft d = requireAlive(id);
        d.setStatus("ACTIVE");
        return toDto(drafts.save(d));
    }

    @Transactional
    public AiDtos.WorkflowDraftResponse deactivate(UUID id) {
        ensureBuilder();
        AiWorkflowDraft d = requireAlive(id);
        d.setStatus("DRAFT");
        return toDto(drafts.save(d));
    }

    @Transactional
    public void softDelete(UUID id) {
        ensureBuilder();
        AiWorkflowDraft d = requireAlive(id);
        d.setStatus("DELETED");
        drafts.save(d);
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
        AiWorkflowDraft d = new AiWorkflowDraft();
        d.setOrganizationId(TenantContext.getOrganizationId());
        d.setCreatedBy(TenantContext.userId().orElse(null));
        d.setName(String.valueOf(plan.getOrDefault("name", "AI suggested workflow")));
        d.setTriggerType(String.valueOf(plan.getOrDefault("triggerType", "DOCUMENT_APPROVAL")));
        d.setDescription(String.valueOf(plan.getOrDefault("description", prompt)));
        d.setConditionsJson(String.valueOf(plan.getOrDefault("conditionsJson", "{}")));
        d.setStepsJson(String.valueOf(plan.getOrDefault("stepsJson", "[]")));
        d.setSuggestedApprovers(String.valueOf(plan.getOrDefault("suggestedApprovers", "ORGANIZATION_ADMIN")));
        d.setStatus("DRAFT");
        return toDto(drafts.save(d));
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
            Map<String, String> s = steps.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"order\":")
                    .append(s.get("order"))
                    .append(",\"role\":\"")
                    .append(s.get("role"))
                    .append("\",\"action\":\"")
                    .append(s.get("action"))
                    .append("\"}");
        }
        sb.append(']');
        plan.put("stepsJson", sb.toString());
        plan.put("suggestedApprovers", "ORGANIZATION_ADMIN,ACCOUNTANT");
        return plan;
    }

    private void apply(AiWorkflowDraft d, AiDtos.WorkflowDraftRequest request) {
        if (request.name() != null && !request.name().isBlank()) {
            d.setName(request.name().trim());
        }
        if (request.triggerType() != null && !request.triggerType().isBlank()) {
            d.setTriggerType(request.triggerType().trim());
        }
        if (request.description() != null) {
            d.setDescription(request.description());
        }
        if (request.conditionsJson() != null) {
            d.setConditionsJson(request.conditionsJson());
        }
        if (request.stepsJson() != null) {
            d.setStepsJson(request.stepsJson());
        }
        if (request.suggestedApprovers() != null) {
            d.setSuggestedApprovers(request.suggestedApprovers());
        }
    }

    private AiWorkflowDraft require(UUID id) {
        return drafts.findByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow draft not found"));
    }

    private AiWorkflowDraft requireAlive(UUID id) {
        AiWorkflowDraft d = require(id);
        if (isDeleted(d)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow draft not found");
        }
        return d;
    }

    private static boolean isDeleted(AiWorkflowDraft d) {
        return "DELETED".equalsIgnoreCase(d.getStatus());
    }

    private void ensureBuilder() {
        if (!properties.isWorkflowBuilderEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Workflow builder disabled. Set flowledger.ai.workflow-builder-enabled=true.");
        }
    }

    private AiDtos.WorkflowDraftResponse toDto(AiWorkflowDraft d) {
        return new AiDtos.WorkflowDraftResponse(
                d.getId(),
                d.getName(),
                d.getTriggerType(),
                d.getDescription(),
                d.getConditionsJson(),
                d.getStepsJson(),
                d.getSuggestedApprovers(),
                d.getStatus(),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }
}
