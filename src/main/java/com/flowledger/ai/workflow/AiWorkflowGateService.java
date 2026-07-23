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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
            List.of(
                    "QUOTATION",
                    "SALES_ORDER",
                    "DELIVERY_CHALLAN",
                    "SALES_INVOICE",
                    "PURCHASE_ORDER",
                    "PURCHASE_INVOICE");
    private static final Set<String> APPROVAL_ACTIONS = Set.of("APPROVE", "REVIEW");
    private static final Set<String> SKIP_ROLES = Set.of("REQUESTER");

    private final AiWorkflowDraftRepository drafts;
    private final ApprovalRequestRepository requests;
    private final ApprovalActionRepository actions;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNewTx;
    private final AiWorkflowNotificationService workflowNotifications;

    public AiWorkflowGateService(
            AiWorkflowDraftRepository drafts,
            ApprovalRequestRepository requests,
            ApprovalActionRepository actions,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            AiWorkflowNotificationService workflowNotifications) {
        this.drafts = drafts;
        this.requests = requests;
        this.actions = actions;
        this.objectMapper = objectMapper;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.workflowNotifications = workflowNotifications;
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
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Awaiting approval. Open AI → Workflows to review, then retry " + action + ".");
        }

        AiWorkflowDraft primary = selectPrimaryWorkflow(matched);
        List<WorkflowStep> approvalSteps = approvalStepsFromDraft(primary);
        UUID user = TenantContext.userId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user required"));

        // Persist outside the caller's transaction so a 409 on convert/confirm does not roll back the inbox item.
        ApprovalRequest submitted = requiresNewTx.execute(status -> {
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
            return request;
        });

        if (submitted != null) {
            String docLabel = humanize(documentType);
            String actionLabel = action == null || action.isBlank() ? "review" : action;
            workflowNotifications.notifyApprovers(
                    submitted,
                    approvalSteps.get(0).role(),
                    "Approval needed · " + docLabel,
                    "A " + docLabel.toLowerCase(Locale.ROOT) + " is waiting for your approval to " + actionLabel + ".");
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Submitted for approval (\""
                        + primary.getName()
                        + "\"). Open AI → Workflows to review, then retry "
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

    @Transactional(readOnly = true)
    public List<ApprovalRequest> listForEntity(String entityType, UUID entityId) {
        return requests.findByOrganizationIdAndEntityTypeAndEntityIdOrderByRequestedAtDesc(
                TenantContext.getOrganizationId(), entityType, entityId);
    }

    @Transactional(readOnly = true)
    public List<ApprovalAction> listActions(UUID requestId) {
        return actions.findByRequestIdOrderByActedAtAsc(requestId);
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

        List<WorkflowStep> steps = normalizeStoredSteps(request);
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
            ApprovalRequest saved = requests.save(request);
            workflowNotifications.notifyDecision(
                    saved,
                    "Request approved",
                    decisionBody(saved, "was approved. You can retry the original action.", remarks));
            return saved;
        }

        int stepIndex = Math.max(1, request.getCurrentStep());
        if (stepIndex > steps.size()) {
            stepIndex = steps.size();
            request.setCurrentStep(stepIndex);
        }
        WorkflowStep step = steps.get(stepIndex - 1);
        assertCanApproveStep(step.role());

        String stepRemark = "Step "
                + stepIndex
                + "/"
                + steps.size()
                + " · "
                + humanize(step.role())
                + (remarks == null || remarks.isBlank() ? "" : " · " + remarks.trim());
        saveAction(request.getId(), "STEP_APPROVED", stepRemark);
        request.setUpdatedBy(user);

        if (stepIndex >= steps.size()) {
            request.setStatus(ApprovalStatus.APPROVED);
            request.setCurrentStep(steps.size());
            request.setTotalSteps(steps.size());
            request.setDecidedBy(user);
            request.setDecidedAt(OffsetDateTime.now());
            if (remarks != null && !remarks.isBlank()) {
                request.setRemarks(remarks.trim());
            } else {
                request.setRemarks("Fully approved · " + steps.size() + " step(s)");
            }
            String approveNote = remarks != null && !remarks.isBlank()
                    ? remarks.trim()
                    : "All " + steps.size() + " approval steps completed";
            saveAction(request.getId(), "APPROVED", approveNote);
            log.info("AI workflow fully approved request={}", request.getId());
            ApprovalRequest saved = requests.saveAndFlush(request);
            workflowNotifications.notifyDecision(
                    saved,
                    "Request approved",
                    decisionBody(saved, "was fully approved. You can retry the original action.", remarks));
            return saved;
        }

        int next = stepIndex + 1;
        request.setCurrentStep(next);
        request.setTotalSteps(steps.size());
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
        ApprovalRequest saved = requests.saveAndFlush(request);
        workflowNotifications.notifyApprovers(
                saved,
                nextStep.role(),
                "Approval needed · " + humanize(saved.getEntityType()),
                "Step " + next + " of " + steps.size() + " is waiting for " + humanize(nextStep.role()) + ".");
        workflowNotifications.notifyDecision(
                saved,
                "Approval progress",
                decisionBody(
                        saved,
                        "moved to step " + next + "/" + steps.size() + " (" + humanize(nextStep.role()) + ").",
                        remarks));
        return saved;
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
        String comment = remarks != null && !remarks.isBlank() ? remarks.trim() : "Rejected";
        request.setRemarks(comment);
        saveAction(request.getId(), "REJECTED", comment);
        ApprovalRequest saved = requests.saveAndFlush(request);
        workflowNotifications.notifyDecision(
                saved,
                "Request rejected",
                decisionBody(saved, "was rejected. Open the document to review comments.", comment));
        return saved;
    }

    private static String decisionBody(ApprovalRequest request, String outcome, String remarks) {
        String doc = humanize(request.getEntityType()).toLowerCase(Locale.ROOT);
        String body = "Your " + doc + " " + outcome;
        if (remarks != null && !remarks.isBlank()) {
            body = body + " Comment: " + remarks.trim();
        }
        return body;
    }

    public ApprovalProgress progressOf(ApprovalRequest request) {
        List<WorkflowStep> raw = parseStepsSnapshot(request.getStepsSnapshotJson());
        List<WorkflowStep> steps = filterApprovalSteps(raw);
        if (steps.isEmpty()) {
            steps = List.of(new WorkflowStep(1, "ORGANIZATION_ADMIN", "APPROVE"));
        }
        int total = steps.size();
        int current = mapCurrentStepAfterFilter(raw, steps, request.getCurrentStep());
        String role = null;
        String action = null;
        if (request.getStatus() == ApprovalStatus.PENDING) {
            WorkflowStep step = steps.get(current - 1);
            role = step.role();
            action = step.action();
        }
        boolean roleMatch = role == null || roleAssignedToCurrentUser(role);
        boolean canApprove = request.getStatus() == ApprovalStatus.PENDING && (roleMatch || isOrganizationAdmin());
        return new ApprovalProgress(
                request.getWorkflowDraftId(),
                request.getWorkflowName(),
                current,
                total,
                role,
                action,
                toStepsJson(steps),
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
        return requiredRole == null
                || requiredRole.isBlank()
                || roleAssignedToCurrentUser(requiredRole)
                || isOrganizationAdmin();
    }

    private boolean roleAssignedToCurrentUser(String requiredRole) {
        if (requiredRole == null || requiredRole.isBlank()) {
            return true;
        }
        Set<String> authorities = currentAuthorities();
        String role = requiredRole.trim().toUpperCase(Locale.ROOT);
        return authorities.contains("ROLE_" + role) || authorities.contains(role);
    }

    private boolean isOrganizationAdmin() {
        Set<String> authorities = currentAuthorities();
        return authorities.contains("ROLE_ORGANIZATION_ADMIN") || authorities.contains("ORGANIZATION_ADMIN");
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

    /**
     * Ensure stored snapshot only contains real approval steps (no REQUESTER/SUBMIT), and keep
     * currentStep aligned with that list. Fixes older requests that snapped the full draft chain.
     */
    private List<WorkflowStep> normalizeStoredSteps(ApprovalRequest request) {
        List<WorkflowStep> raw = parseStepsSnapshot(request.getStepsSnapshotJson());
        List<WorkflowStep> filtered = filterApprovalSteps(raw);
        if (filtered.isEmpty()) {
            return List.of();
        }
        String normalizedJson = toStepsJson(filtered);
        if (!normalizedJson.equals(request.getStepsSnapshotJson() == null ? "" : request.getStepsSnapshotJson())) {
            int mapped = mapCurrentStepAfterFilter(raw, filtered, request.getCurrentStep());
            request.setStepsSnapshotJson(normalizedJson);
            request.setTotalSteps(filtered.size());
            request.setCurrentStep(mapped);
        } else if (request.getTotalSteps() != filtered.size()) {
            request.setTotalSteps(filtered.size());
        }
        return filtered;
    }

    private static int mapCurrentStepAfterFilter(
            List<WorkflowStep> raw, List<WorkflowStep> filtered, int currentStep) {
        if (raw.isEmpty() || filtered.isEmpty()) {
            return 1;
        }
        int idx = Math.max(1, Math.min(currentStep, raw.size())) - 1;
        WorkflowStep current = raw.get(idx);
        for (int i = 0; i < filtered.size(); i++) {
            WorkflowStep step = filtered.get(i);
            if (equalsIgnoreCase(step.role(), current.role()) && equalsIgnoreCase(step.action(), current.action())) {
                return i + 1;
            }
        }
        // If we were still on a skipped REQUESTER step, start at first approval step.
        if (current.role() != null && SKIP_ROLES.contains(current.role().toUpperCase(Locale.ROOT))) {
            return 1;
        }
        return Math.max(1, Math.min(currentStep, filtered.size()));
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.equalsIgnoreCase(b);
    }

    private List<WorkflowStep> filterApprovalSteps(List<WorkflowStep> raw) {
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
            return List.of();
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

    /**
     * When several ACTIVE workflows match the same document, enforce the strictest chain
     * (most APPROVE/REVIEW steps). Ties prefer higher minAmount, then most recently updated.
     */
    private AiWorkflowDraft selectPrimaryWorkflow(List<AiWorkflowDraft> matched) {
        return matched.stream()
                .max(Comparator.comparingInt((AiWorkflowDraft draft) -> approvalStepsFromDraft(draft).size())
                        .thenComparing(this::minAmountOf, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(
                                AiWorkflowDraft::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No matching workflow"));
    }

    private BigDecimal minAmountOf(AiWorkflowDraft draft) {
        JsonNode conditions = parseJson(draft.getConditionsJson());
        if (conditions == null || !conditions.has("minAmount")) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(conditions.get("minAmount").asText());
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    private boolean matches(AiWorkflowDraft draft, String documentType, BigDecimal amount) {
        String trigger =
                draft.getTriggerType() == null ? "" : draft.getTriggerType().toUpperCase(Locale.ROOT);
        String doc = documentType.toUpperCase(Locale.ROOT);

        boolean triggerOk =
                switch (trigger) {
                    case "PAYMENT_OR_INVOICE" ->
                        doc.equals("SALES_INVOICE")
                                || doc.equals("DELIVERY_CHALLAN")
                                || doc.equals("QUOTATION")
                                || doc.equals("SALES_ORDER")
                                || doc.equals("PURCHASE_INVOICE");
                    case "PURCHASE_ORDER" -> doc.equals("PURCHASE_ORDER") || doc.equals("PURCHASE_INVOICE");
                    case "DOCUMENT_APPROVAL", "MANUAL", "" -> true;
                    case "INVENTORY_ADJUSTMENT" -> doc.equals("INVENTORY_ADJUSTMENT");
                    case "GST_COMPLIANCE" ->
                        doc.contains("INVOICE") || doc.equals("QUOTATION") || doc.equals("DELIVERY_CHALLAN");
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
                String listedType = node.asText();
                if (doc.equalsIgnoreCase(listedType)) {
                    listed = true;
                    break;
                }
                // Invoice workflows also gate challan → invoice conversions.
                if (doc.equals("DELIVERY_CHALLAN") && "SALES_INVOICE".equalsIgnoreCase(listedType)) {
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
        List<WorkflowStep> filtered = filterApprovalSteps(parseStepsSnapshot(draft.getStepsJson()));
        if (filtered.isEmpty()) {
            return List.of(new WorkflowStep(1, "ORGANIZATION_ADMIN", "APPROVE"));
        }
        return filtered;
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
