package com.flowledger.ai.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.entity.AiWorkflowDraft;
import com.flowledger.ai.repository.AiWorkflowDraftRepository;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.transport.domain.TransportEnums.ApprovalStatus;
import com.flowledger.transport.entity.ApprovalAction;
import com.flowledger.transport.entity.ApprovalRequest;
import com.flowledger.transport.repository.ApprovalActionRepository;
import com.flowledger.transport.repository.ApprovalRequestRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Evaluates ACTIVE AI workflow drafts and gates ERP actions via {@code approval_requests}.
 * Matching docs require sequential human approvals (e.g. Admin then Accountant) before convert/confirm.
 */
@Service
@ConditionalOnAiEnabled
public class AiWorkflowGateService {
    private static final Logger log = LoggerFactory.getLogger(AiWorkflowGateService.class);
    private static final List<String> DOCUMENT_TYPES =
            List.of("QUOTATION", "SALES_ORDER", "SALES_INVOICE", "PURCHASE_ORDER", "PURCHASE_INVOICE");
    private static final Set<String> APPROVAL_ACTIONS = Set.of("APPROVE", "REVIEW");
    private static final Set<String> SKIP_ROLES = Set.of("REQUESTER");

    private final AiWorkflowDraftRepository drafts;
    private final ApprovalRequestRepository requests;
    private final ApprovalActionRepository actions;
    private final ObjectMapper objectMapper;

    public AiWorkflowGateService(
            AiWorkflowDraftRepository drafts,
            ApprovalRequestRepository requests,
            ApprovalActionRepository actions,
            ObjectMapper objectMapper) {
        this.drafts = drafts;
        this.requests = requests;
        this.actions = actions;
        this.objectMapper = objectMapper;
    }

    /**
     * If any ACTIVE workflow matches this document, require a fully APPROVED request
     * (all sequential steps completed). Creates PENDING on first hit and throws 409.
     */
    @Transactional
    public void requireApproved(String documentType, UUID entityId, BigDecimal amount, String action) {
        UUID org = TenantContext.getOrganizationId();
        List<AiWorkflowDraft> matched = matchingActive(org, documentType, amount);
        if (matched.isEmpty()) {
            return;
        }

        var approved = requests.findFirstByOrganizationIdAndEntityTypeAndEntityIdAndStatusOrderByRequestedAtDesc(
                org, documentType, entityId, ApprovalStatus.APPROVED);
        if (approved.isPresent()) {
            return;
        }

        var pending = requests.findFirstByOrganizationIdAndEntityTypeAndEntityIdAndStatusOrderByRequestedAtDesc(
                org, documentType, entityId, ApprovalStatus.PENDING);
        if (pending.isPresent()) {
            ApprovalRequest pendingRequest = pending.get();
            String stepHint = describeCurrentStep(pendingRequest);
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Awaiting workflow approval for "
                            + documentType
                            + (stepHint.isBlank() ? "" : " (" + stepHint + ")")
                            + ". Approve under AI → Workflows, then retry "
                            + action
                            + ".");
        }

        AiWorkflowDraft primary = matched.get(0);
        List<WorkflowStep> approvalSteps = approvalStepsFromDraft(primary);
        UUID user = TenantContext.userId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user required"));

        ApprovalRequest request = new ApprovalRequest();
        request.setOrganizationId(org);
        request.setEntityType(documentType);
        request.setEntityId(entityId);
        request.setStatus(ApprovalStatus.PENDING);
        request.setRequestedBy(user);
        request.setRequestedAt(OffsetDateTime.now());
        request.setCreatedBy(user);
        request.setUpdatedBy(user);
        request.setWorkflowDraftId(primary.getId());
        request.setWorkflowName(primary.getName());
        request.setCurrentStep(1);
        request.setTotalSteps(approvalSteps.size());
        request.setStepsSnapshotJson(toStepsJson(approvalSteps));
        request.setRemarks(buildRemarks(primary, action, amount, matched.size(), approvalSteps));
        request = requests.save(request);
        saveAction(
                request.getId(),
                "SUBMITTED",
                "Submitted · awaiting step 1/"
                        + approvalSteps.size()
                        + " · "
                        + approvalSteps.get(0).role());

        log.info(
                "AI workflow gate submitted approval org={} type={} id={} workflow={} steps={}",
                org,
                documentType,
                entityId,
                primary.getName(),
                approvalSteps.size());

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Submitted for workflow approval (\""
                        + primary.getName()
                        + "\", step 1/"
                        + approvalSteps.size()
                        + " · "
                        + humanize(approvalSteps.get(0).role())
                        + "). Approve under AI → Workflows, then retry "
                        + action
                        + ".");
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> listPending() {
        return requests
                .findByOrganizationIdAndStatusOrderByRequestedAtDesc(
                        TenantContext.getOrganizationId(), ApprovalStatus.PENDING)
                .stream()
                .filter(approvalRequest -> DOCUMENT_TYPES.contains(approvalRequest.getEntityType()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> listRecent() {
        return requests.findByOrganizationIdAndEntityTypeInOrderByRequestedAtDesc(
                TenantContext.getOrganizationId(), DOCUMENT_TYPES);
    }

    @Transactional
    public ApprovalRequest approve(UUID requestId, String remarks) {
        UUID org = TenantContext.getOrganizationId();
        UUID user = TenantContext.userId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user required"));
        ApprovalRequest request = requests.findByIdAndOrganizationId(requestId, org)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval request not found"));
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval request is not pending");
        }

        List<WorkflowStep> steps = parseStepsSnapshot(request.getStepsSnapshotJson());
        if (steps.isEmpty()) {
            // Legacy single-flip approvals created before multi-step
            request.setStatus(ApprovalStatus.APPROVED);
            request.setDecidedBy(user);
            request.setDecidedAt(OffsetDateTime.now());
            request.setUpdatedBy(user);
            if (remarks != null && !remarks.isBlank()) {
                request.setRemarks(remarks);
            }
            saveAction(request.getId(), "APPROVED", remarks);
            return requests.save(request);
        }

        int stepIndex = Math.max(1, request.getCurrentStep());
        if (stepIndex > steps.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval request has no remaining steps");
        }
        WorkflowStep step = steps.get(stepIndex - 1);
        assertCanApproveStep(step.role());

        String stepRemark = "Step "
                + stepIndex
                + "/"
                + steps.size()
                + " · "
                + step.role()
                + " · "
                + step.action()
                + (remarks == null || remarks.isBlank() ? "" : " · " + remarks.trim());
        saveAction(request.getId(), "STEP_APPROVED", stepRemark);
        request.setUpdatedBy(user);

        if (stepIndex >= steps.size()) {
            request.setStatus(ApprovalStatus.APPROVED);
            request.setCurrentStep(steps.size());
            request.setDecidedBy(user);
            request.setDecidedAt(OffsetDateTime.now());
            if (remarks != null && !remarks.isBlank()) {
                request.setRemarks(remarks);
            } else {
                request.setRemarks("Fully approved · " + steps.size() + " step(s)");
            }
            saveAction(request.getId(), "APPROVED", "All " + steps.size() + " approval steps completed");
            log.info("AI workflow fully approved request={}", request.getId());
        } else {
            int next = stepIndex + 1;
            request.setCurrentStep(next);
            WorkflowStep nextStep = steps.get(next - 1);
            request.setRemarks("Awaiting step "
                    + next
                    + "/"
                    + steps.size()
                    + " · "
                    + humanize(nextStep.role())
                    + " · "
                    + humanize(nextStep.action()));
            log.info(
                    "AI workflow step approved request={} step={}/{} nextRole={}",
                    request.getId(),
                    stepIndex,
                    steps.size(),
                    nextStep.role());
        }
        return requests.save(request);
    }

    @Transactional
    public ApprovalRequest reject(UUID requestId, String remarks) {
        UUID org = TenantContext.getOrganizationId();
        UUID user = TenantContext.userId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user required"));
        ApprovalRequest request = requests.findByIdAndOrganizationId(requestId, org)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval request not found"));
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval request is not pending");
        }
        request.setStatus(ApprovalStatus.REJECTED);
        request.setDecidedBy(user);
        request.setDecidedAt(OffsetDateTime.now());
        request.setUpdatedBy(user);
        if (remarks != null && !remarks.isBlank()) {
            request.setRemarks(remarks);
        }
        saveAction(request.getId(), "REJECTED", remarks);
        return requests.save(request);
    }

    public ApprovalProgress progressOf(ApprovalRequest request) {
        List<WorkflowStep> steps = parseStepsSnapshot(request.getStepsSnapshotJson());
        int total = request.getTotalSteps() > 0 ? request.getTotalSteps() : Math.max(steps.size(), 1);
        int current = Math.max(1, request.getCurrentStep());
        String role = null;
        String action = null;
        if (!steps.isEmpty() && request.getStatus() == ApprovalStatus.PENDING) {
            int idx = Math.min(current, steps.size()) - 1;
            role = steps.get(idx).role();
            action = steps.get(idx).action();
        }
        boolean canApprove =
                request.getStatus() == ApprovalStatus.PENDING && (role == null || currentUserCanApprove(role));
        return new ApprovalProgress(
                request.getWorkflowDraftId(),
                request.getWorkflowName(),
                current,
                total,
                role,
                action,
                request.getStepsSnapshotJson(),
                canApprove,
                steps);
    }

    private void assertCanApproveStep(String requiredRole) {
        if (!currentUserCanApprove(requiredRole)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Step requires role "
                            + humanize(requiredRole)
                            + ". Sign in as that role (or Organization Admin) to approve.");
        }
    }

    private boolean currentUserCanApprove(String requiredRole) {
        if (requiredRole == null || requiredRole.isBlank()) {
            return true;
        }
        Set<String> authorities = currentAuthorities();
        if (authorities.contains("ROLE_ORGANIZATION_ADMIN") || authorities.contains("ORGANIZATION_ADMIN")) {
            return true;
        }
        String role = requiredRole.trim().toUpperCase(Locale.ROOT);
        return authorities.contains("ROLE_" + role) || authorities.contains(role);
    }

    private Set<String> currentAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    private List<AiWorkflowDraft> matchingActive(UUID org, String documentType, BigDecimal amount) {
        List<AiWorkflowDraft> active =
                drafts.findByOrganizationIdAndStatusIgnoreCaseOrderByUpdatedAtDesc(org, "ACTIVE");
        List<AiWorkflowDraft> matched = new ArrayList<>();
        BigDecimal amt = amount == null ? BigDecimal.ZERO : amount;
        for (AiWorkflowDraft draft : active) {
            if (matches(draft, documentType, amt)) {
                matched.add(draft);
            }
        }
        return matched;
    }

    private boolean matches(AiWorkflowDraft draft, String documentType, BigDecimal amount) {
        String trigger =
                draft.getTriggerType() == null ? "" : draft.getTriggerType().toUpperCase(Locale.ROOT);
        String doc = documentType.toUpperCase(Locale.ROOT);

        boolean triggerOk =
                switch (trigger) {
                    case "PAYMENT_OR_INVOICE" ->
                        doc.equals("SALES_INVOICE")
                                || doc.equals("QUOTATION")
                                || doc.equals("SALES_ORDER")
                                || doc.equals("PURCHASE_INVOICE");
                    case "PURCHASE_ORDER" -> doc.equals("PURCHASE_ORDER") || doc.equals("PURCHASE_INVOICE");
                    case "DOCUMENT_APPROVAL", "MANUAL", "" -> true;
                    case "INVENTORY_ADJUSTMENT" -> doc.equals("INVENTORY_ADJUSTMENT");
                    case "GST_COMPLIANCE" -> doc.contains("INVOICE") || doc.equals("QUOTATION");
                    default -> trigger.contains(doc) || doc.contains(trigger.replace('_', ' '));
                };
        if (!triggerOk) {
            return false;
        }

        JsonNode conditions = parseJson(draft.getConditionsJson());
        if (conditions != null
                && conditions.has("documentTypes")
                && conditions.get("documentTypes").isArray()) {
            boolean listed = false;
            for (JsonNode node : conditions.get("documentTypes")) {
                if (doc.equalsIgnoreCase(node.asText())) {
                    listed = true;
                    break;
                }
            }
            if (!listed) {
                return false;
            }
        }

        if (conditions != null && conditions.has("minAmount")) {
            try {
                BigDecimal min = new BigDecimal(conditions.get("minAmount").asText());
                if (amount.compareTo(min) < 0) {
                    return false;
                }
            } catch (Exception ignored) {
                // ignore bad minAmount
            }
        }
        return true;
    }

    private List<WorkflowStep> approvalStepsFromDraft(AiWorkflowDraft draft) {
        List<WorkflowStep> raw = parseStepsSnapshot(draft.getStepsJson());
        List<WorkflowStep> approval = raw.stream()
                .filter(step ->
                        step.role() == null || !SKIP_ROLES.contains(step.role().toUpperCase(Locale.ROOT)))
                .filter(step -> {
                    String action = step.action() == null ? "" : step.action().toUpperCase(Locale.ROOT);
                    return action.isBlank() || APPROVAL_ACTIONS.contains(action);
                })
                .sorted(Comparator.comparingInt(WorkflowStep::order))
                .toList();
        if (approval.isEmpty()) {
            return List.of(new WorkflowStep(1, "ORGANIZATION_ADMIN", "APPROVE"));
        }
        List<WorkflowStep> renumbered = new ArrayList<>();
        for (int i = 0; i < approval.size(); i++) {
            WorkflowStep step = approval.get(i);
            renumbered.add(new WorkflowStep(
                    i + 1,
                    step.role() == null || step.role().isBlank()
                            ? "ORGANIZATION_ADMIN"
                            : step.role().toUpperCase(Locale.ROOT),
                    step.action() == null || step.action().isBlank()
                            ? "APPROVE"
                            : step.action().toUpperCase(Locale.ROOT)));
        }
        return renumbered;
    }

    private List<WorkflowStep> parseStepsSnapshot(String raw) {
        JsonNode root = parseJson(raw);
        if (root == null || !root.isArray()) {
            return List.of();
        }
        List<WorkflowStep> steps = new ArrayList<>();
        for (JsonNode node : root) {
            int order = node.has("order") ? node.get("order").asInt(steps.size() + 1) : steps.size() + 1;
            String role = node.has("role") ? node.get("role").asText(null) : null;
            String action = node.has("action") ? node.get("action").asText(null) : null;
            steps.add(new WorkflowStep(order, role, action));
        }
        steps.sort(Comparator.comparingInt(WorkflowStep::order));
        return steps;
    }

    private String toStepsJson(List<WorkflowStep> steps) {
        ArrayNode arr = objectMapper.createArrayNode();
        for (WorkflowStep step : steps) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("order", step.order());
            node.put("role", step.role());
            node.put("action", step.action());
            arr.add(node);
        }
        return arr.toString();
    }

    private JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private String describeCurrentStep(ApprovalRequest request) {
        ApprovalProgress progress = progressOf(request);
        if (progress.currentStepRole() == null) {
            return "step " + progress.currentStep() + "/" + progress.totalSteps();
        }
        return "step "
                + progress.currentStep()
                + "/"
                + progress.totalSteps()
                + " · "
                + humanize(progress.currentStepRole());
    }

    private static String buildRemarks(
            AiWorkflowDraft draft, String action, BigDecimal amount, int matchedCount, List<WorkflowStep> steps) {
        return "AI workflow \""
                + draft.getName()
                + "\" · action="
                + action
                + " · amount="
                + (amount == null ? "0" : amount.toPlainString())
                + " · matched="
                + matchedCount
                + " · steps="
                + steps.size()
                + " · awaiting "
                + humanize(steps.get(0).role());
    }

    private static String humanize(String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        String[] parts = value.toLowerCase(Locale.ROOT).replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private void saveAction(UUID requestId, String value, String remarks) {
        ApprovalAction action = new ApprovalAction();
        action.setRequestId(requestId);
        action.setAction(value);
        action.setActorId(TenantContext.userId().orElse(null));
        action.setActedAt(OffsetDateTime.now());
        action.setRemarks(remarks);
        actions.save(action);
    }

    public record WorkflowStep(int order, String role, String action) {}

    public record ApprovalProgress(
            UUID workflowDraftId,
            String workflowName,
            int currentStep,
            int totalSteps,
            String currentStepRole,
            String currentStepAction,
            String stepsSnapshotJson,
            boolean canApprove,
            List<WorkflowStep> steps) {}
}
