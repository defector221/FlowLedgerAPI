package com.flowledger.platform.approval.service;

import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.platform.approval.domain.ApprovalActionType;
import com.flowledger.platform.approval.domain.ApprovalStatus;
import com.flowledger.platform.approval.dto.ApprovalDtos.*;
import com.flowledger.platform.approval.entity.ApprovalDefinition;
import com.flowledger.platform.approval.entity.ApprovalInstance;
import com.flowledger.platform.approval.entity.ApprovalInstanceAction;
import com.flowledger.platform.approval.repository.ApprovalDefinitionRepository;
import com.flowledger.platform.approval.repository.ApprovalInstanceActionRepository;
import com.flowledger.platform.approval.repository.ApprovalInstanceRepository;
import com.flowledger.platform.event.DomainEventPublisher;
import com.flowledger.platform.history.service.DocumentHistoryService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalEngineService {
    private final ApprovalDefinitionRepository definitions;
    private final ApprovalInstanceRepository instances;
    private final ApprovalInstanceActionRepository actions;
    private final DocumentHistoryService history;
    private final DomainEventPublisher events;

    public ApprovalEngineService(
            ApprovalDefinitionRepository definitions,
            ApprovalInstanceRepository instances,
            ApprovalInstanceActionRepository actions,
            DocumentHistoryService history,
            DomainEventPublisher events) {
        this.definitions = definitions;
        this.instances = instances;
        this.actions = actions;
        this.history = history;
        this.events = events;
    }

    @Transactional
    public DefinitionResponse upsertDefinition(DefinitionRequest request) {
        UUID org = TenantContext.getOrganizationId();
        ApprovalDefinition def = new ApprovalDefinition();
        def.setOrganizationId(org);
        def.setEntityType(request.entityType());
        def.setName(request.name());
        def.setLevelsJson(request.levelsJson() != null ? request.levelsJson() : def.getLevelsJson());
        def.setMinAmount(request.minAmount());
        def.setMaxAmount(request.maxAmount());
        def.setActive(request.active() == null || request.active());
        def.setCreatedBy(events.currentUser());
        return toDefinition(definitions.save(def));
    }

    @Transactional(readOnly = true)
    public List<DefinitionResponse> listDefinitions(String entityType) {
        UUID org = TenantContext.getOrganizationId();
        return definitions.findByOrganizationIdAndEntityTypeAndActiveTrue(org, entityType).stream()
                .map(this::toDefinition)
                .toList();
    }

    @Transactional
    public InstanceResponse submit(SubmitRequest request) {
        UUID org = TenantContext.getOrganizationId();
        UUID user = TenantContext.userId().orElseThrow(() -> new IllegalStateException("User context is not set"));
        ApprovalDefinition def = resolveDefinition(org, request.entityType(), request.amount());
        int levels = countLevels(def.getLevelsJson());

        ApprovalInstance instance = new ApprovalInstance();
        instance.setOrganizationId(org);
        instance.setDefinitionId(def.getId());
        instance.setEntityType(request.entityType());
        instance.setEntityId(request.entityId());
        instance.setStatus(ApprovalStatus.PENDING);
        instance.setCurrentLevel(1);
        instance.setTotalLevels(levels);
        instance.setRequestedBy(user);
        instance.setRequestedAt(OffsetDateTime.now());
        instance.setAmount(request.amount());
        instance.setRemarks(request.remarks());
        instance.setCreatedBy(user);
        instance = instances.save(instance);

        recordAction(org, instance.getId(), 1, ApprovalActionType.SUBMIT, user, request.remarks());
        history.record(request.entityType(), request.entityId(), "APPROVAL_SUBMITTED", "Approval submitted", null);
        return toInstance(instance);
    }

    @Transactional
    public InstanceResponse approve(UUID id, DecideRequest request) {
        return decide(id, true, request);
    }

    @Transactional
    public InstanceResponse reject(UUID id, DecideRequest request) {
        return decide(id, false, request);
    }

    @Transactional
    public InstanceResponse cancel(UUID id, DecideRequest request) {
        UUID org = TenantContext.getOrganizationId();
        UUID user = TenantContext.userId().orElseThrow(() -> new IllegalStateException("User context is not set"));
        ApprovalInstance instance = load(id, org);
        if (instance.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("Only pending approvals can be cancelled");
        }
        instance.setStatus(ApprovalStatus.CANCELLED);
        instance.setDecidedBy(user);
        instance.setDecidedAt(OffsetDateTime.now());
        instance.setRemarks(request != null ? request.remarks() : instance.getRemarks());
        recordAction(
                org,
                instance.getId(),
                instance.getCurrentLevel(),
                ApprovalActionType.CANCEL,
                user,
                request != null ? request.remarks() : null);
        history.record(
                instance.getEntityType(), instance.getEntityId(), "APPROVAL_CANCELLED", "Approval cancelled", null);
        return toInstance(instances.save(instance));
    }

    @Transactional(readOnly = true)
    public Page<InstanceResponse> inbox(ApprovalStatus status, Pageable pageable) {
        UUID org = TenantContext.getOrganizationId();
        Page<ApprovalInstance> page = status == null
                ? instances.findByOrganizationId(org, pageable)
                : instances.findByOrganizationIdAndStatus(org, status, pageable);
        return page.map(this::toInstance);
    }

    @Transactional(readOnly = true)
    public InstanceResponse get(UUID id) {
        return toInstance(load(id, TenantContext.getOrganizationId()));
    }

    private InstanceResponse decide(UUID id, boolean approve, DecideRequest request) {
        UUID org = TenantContext.getOrganizationId();
        UUID user = TenantContext.userId().orElseThrow(() -> new IllegalStateException("User context is not set"));
        ApprovalInstance instance = load(id, org);
        if (instance.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("Approval is not pending");
        }
        recordAction(
                org,
                instance.getId(),
                instance.getCurrentLevel(),
                approve ? ApprovalActionType.APPROVE : ApprovalActionType.REJECT,
                user,
                request != null ? request.remarks() : null);

        if (!approve) {
            instance.setStatus(ApprovalStatus.REJECTED);
            instance.setDecidedBy(user);
            instance.setDecidedAt(OffsetDateTime.now());
            history.record(
                    instance.getEntityType(), instance.getEntityId(), "APPROVAL_REJECTED", "Approval rejected", null);
        } else if (instance.getCurrentLevel() >= instance.getTotalLevels()) {
            instance.setStatus(ApprovalStatus.APPROVED);
            instance.setDecidedBy(user);
            instance.setDecidedAt(OffsetDateTime.now());
            history.record(
                    instance.getEntityType(), instance.getEntityId(), "APPROVAL_APPROVED", "Approval completed", null);
        } else {
            instance.setCurrentLevel(instance.getCurrentLevel() + 1);
            history.record(
                    instance.getEntityType(),
                    instance.getEntityId(),
                    "APPROVAL_LEVEL_ADVANCED",
                    "Moved to level " + instance.getCurrentLevel(),
                    null);
        }
        if (request != null && request.remarks() != null) instance.setRemarks(request.remarks());
        return toInstance(instances.save(instance));
    }

    private ApprovalDefinition resolveDefinition(UUID org, String entityType, BigDecimal amount) {
        List<ApprovalDefinition> defs = definitions.findByOrganizationIdAndEntityTypeAndActiveTrue(org, entityType);
        ApprovalDefinition match = defs.stream()
                .filter(d -> amountInRange(amount, d.getMinAmount(), d.getMaxAmount()))
                .findFirst()
                .orElse(null);
        if (match != null) return match;
        ApprovalDefinition fallback = new ApprovalDefinition();
        fallback.setOrganizationId(org);
        fallback.setEntityType(entityType);
        fallback.setName("Default " + entityType);
        fallback.setLevelsJson("[{\"level\":1,\"role\":\"ORGANIZATION_ADMIN\"}]");
        fallback.setActive(true);
        return definitions.save(fallback);
    }

    private static boolean amountInRange(BigDecimal amount, BigDecimal min, BigDecimal max) {
        if (amount == null) return min == null && max == null;
        if (min != null && amount.compareTo(min) < 0) return false;
        if (max != null && amount.compareTo(max) > 0) return false;
        return true;
    }

    private static int countLevels(String levelsJson) {
        if (levelsJson == null || levelsJson.isBlank()) return 1;
        int count = levelsJson.split("\"level\"").length - 1;
        return Math.max(1, count);
    }

    private void recordAction(
            UUID org, UUID instanceId, int level, ApprovalActionType type, UUID actor, String remarks) {
        ApprovalInstanceAction action = new ApprovalInstanceAction();
        action.setOrganizationId(org);
        action.setInstanceId(instanceId);
        action.setLevelNumber(level);
        action.setAction(type);
        action.setActorId(actor);
        action.setActedAt(OffsetDateTime.now());
        action.setRemarks(remarks);
        actions.save(action);
    }

    private ApprovalInstance load(UUID id, UUID org) {
        return instances
                .findByIdAndOrganizationId(id, org)
                .orElseThrow(() -> new ResourceNotFoundException("Approval not found"));
    }

    private DefinitionResponse toDefinition(ApprovalDefinition d) {
        return new DefinitionResponse(
                d.getId(),
                d.getEntityType(),
                d.getName(),
                d.isActive(),
                d.getLevelsJson(),
                d.getMinAmount(),
                d.getMaxAmount());
    }

    private InstanceResponse toInstance(ApprovalInstance i) {
        List<ActionResponse> actionResponses = actions.findByInstanceIdOrderByActedAtAsc(i.getId()).stream()
                .map(a -> new ActionResponse(
                        a.getId(),
                        a.getLevelNumber(),
                        a.getAction().name(),
                        a.getActorId(),
                        a.getActedAt(),
                        a.getRemarks()))
                .toList();
        return new InstanceResponse(
                i.getId(),
                i.getEntityType(),
                i.getEntityId(),
                i.getStatus(),
                i.getCurrentLevel(),
                i.getTotalLevels(),
                i.getRequestedBy(),
                i.getRequestedAt(),
                i.getDecidedBy(),
                i.getDecidedAt(),
                i.getAmount(),
                i.getRemarks(),
                actionResponses);
    }
}
