package com.flowledger.ai.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Evaluates ACTIVE AI workflow drafts and gates ERP actions via {@code approval_requests}.
 * Matching docs require human approve before convert/confirm proceeds.
 */
@Service
@ConditionalOnAiEnabled
public class AiWorkflowGateService {
    private static final Logger log = LoggerFactory.getLogger(AiWorkflowGateService.class);
    private static final List<String> DOCUMENT_TYPES =
            List.of("QUOTATION", "SALES_ORDER", "SALES_INVOICE", "PURCHASE_ORDER", "PURCHASE_INVOICE");

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
     * If any ACTIVE workflow matches this document, require an APPROVED request.
     * Creates PENDING on first hit and throws 409; retries after approve succeed.
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
                    "Awaiting workflow approval for "
                            + documentType
                            + ". Approve it under AI → Workflows, then retry "
                            + action
                            + ".");
        }

        AiWorkflowDraft primary = matched.get(0);
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
        request.setRemarks(buildRemarks(primary, action, amount, matched.size()));
        request = requests.save(request);
        saveAction(request.getId(), "SUBMITTED", request.getRemarks());

        log.info(
                "AI workflow gate submitted approval org={} type={} id={} workflow={}",
                org,
                documentType,
                entityId,
                primary.getName());

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Submitted for workflow approval (\""
                        + primary.getName()
                        + "\"). Approve under AI → Workflows, then retry "
                        + action
                        + ".");
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> listPending() {
        return requests
                .findByOrganizationIdAndStatusOrderByRequestedAtDesc(
                        TenantContext.getOrganizationId(), ApprovalStatus.PENDING)
                .stream()
                .filter(r -> DOCUMENT_TYPES.contains(r.getEntityType()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequest> listRecent() {
        return requests.findByOrganizationIdAndEntityTypeInOrderByRequestedAtDesc(
                TenantContext.getOrganizationId(), DOCUMENT_TYPES);
    }

    @Transactional
    public ApprovalRequest approve(UUID requestId, String remarks) {
        return decide(requestId, ApprovalStatus.APPROVED, remarks);
    }

    @Transactional
    public ApprovalRequest reject(UUID requestId, String remarks) {
        return decide(requestId, ApprovalStatus.REJECTED, remarks);
    }

    private ApprovalRequest decide(UUID requestId, ApprovalStatus decision, String remarks) {
        UUID org = TenantContext.getOrganizationId();
        UUID user = TenantContext.userId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user required"));
        ApprovalRequest request = requests
                .findByIdAndOrganizationId(requestId, org)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval request not found"));
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval request is not pending");
        }
        request.setStatus(decision);
        request.setDecidedBy(user);
        request.setDecidedAt(OffsetDateTime.now());
        request.setUpdatedBy(user);
        if (remarks != null && !remarks.isBlank()) {
            request.setRemarks(remarks);
        }
        saveAction(request.getId(), decision.name(), remarks);
        return requests.save(request);
    }

    private List<AiWorkflowDraft> matchingActive(UUID org, String documentType, BigDecimal amount) {
        List<AiWorkflowDraft> active = drafts.findByOrganizationIdAndStatusIgnoreCaseOrderByUpdatedAtDesc(org, "ACTIVE");
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
        String trigger = draft.getTriggerType() == null ? "" : draft.getTriggerType().toUpperCase(Locale.ROOT);
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
        if (conditions != null && conditions.has("documentTypes") && conditions.get("documentTypes").isArray()) {
            boolean listed = false;
            for (JsonNode n : conditions.get("documentTypes")) {
                if (doc.equalsIgnoreCase(n.asText())) {
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

    private static String buildRemarks(AiWorkflowDraft draft, String action, BigDecimal amount, int matchedCount) {
        return "AI workflow \""
                + draft.getName()
                + "\" · action="
                + action
                + " · amount="
                + (amount == null ? "0" : amount.toPlainString())
                + " · matched="
                + matchedCount;
    }

    private void saveAction(UUID requestId, String value, String remarks) {
        ApprovalAction a = new ApprovalAction();
        a.setRequestId(requestId);
        a.setAction(value);
        a.setActorId(TenantContext.userId().orElse(null));
        a.setActedAt(OffsetDateTime.now());
        a.setRemarks(remarks);
        actions.save(a);
    }
}
