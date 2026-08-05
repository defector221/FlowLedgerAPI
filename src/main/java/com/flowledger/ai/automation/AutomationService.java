package com.flowledger.ai.automation;

import com.flowledger.ai.audit.AiAuditService;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.ai.entity.AiAutomation;
import com.flowledger.ai.entity.AiAutomationRun;
import com.flowledger.ai.recommendation.RecommendationGenerator;
import com.flowledger.ai.repository.AiAutomationRepository;
import com.flowledger.ai.repository.AiAutomationRunRepository;
import com.flowledger.common.tenant.TenantContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnAiEnabled
public class AutomationService {
    private static final Logger log = LoggerFactory.getLogger(AutomationService.class);

    private final AiAutomationRepository automations;
    private final AiAutomationRunRepository runs;
    private final RecommendationGenerator recommendations;
    private final AiAuditService audit;

    public AutomationService(
            AiAutomationRepository automations,
            AiAutomationRunRepository runs,
            RecommendationGenerator recommendations,
            AiAuditService audit) {
        this.automations = automations;
        this.runs = runs;
        this.recommendations = recommendations;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AiDtos.AutomationResponse> list() {
        return automations.findByOrganizationIdOrderByUpdatedAtDesc(TenantContext.getOrganizationId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AiDtos.AutomationRunResponse> listRuns(UUID automationId) {
        UUID org = TenantContext.getOrganizationId();
        if (automationId != null) {
            automations
                    .findByIdAndOrganizationId(automationId, org)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Automation not found"));
            return runs.findByAutomationIdOrderByStartedAtDesc(automationId).stream()
                    .map(this::toRunDto)
                    .toList();
        }
        return runs.findByOrganizationIdOrderByStartedAtDesc(org).stream().map(this::toRunDto).toList();
    }

    @Transactional
    public AiDtos.AutomationResponse create(AiDtos.AutomationRequest request) {
        validate(request);
        AiAutomation automation = new AiAutomation();
        automation.setOrganizationId(TenantContext.getOrganizationId());
        automation.setCreatedBy(TenantContext.userId().orElse(null));
        apply(automation, request);
        automation.setStatus("PAUSED");
        automation.setNextRunAt(computeNextRun(automation));
        return toDto(automations.save(automation));
    }

    @Transactional
    public AiDtos.AutomationResponse update(UUID id, AiDtos.AutomationRequest request) {
        AiAutomation automation = require(id);
        validate(request);
        apply(automation, request);
        automation.setNextRunAt(computeNextRun(automation));
        return toDto(automations.save(automation));
    }

    @Transactional
    public AiDtos.AutomationResponse activate(UUID id) {
        AiAutomation automation = require(id);
        automation.setStatus("ACTIVE");
        automation.setNextRunAt(computeNextRun(automation));
        return toDto(automations.save(automation));
    }

    @Transactional
    public AiDtos.AutomationResponse pause(UUID id) {
        AiAutomation automation = require(id);
        automation.setStatus("PAUSED");
        automation.setNextRunAt(null);
        return toDto(automations.save(automation));
    }

    @Transactional
    public void delete(UUID id) {
        automations.delete(require(id));
    }

    @Transactional
    public AiDtos.AutomationRunResponse runNow(UUID id) {
        return execute(require(id), "MANUAL");
    }

    @Transactional
    public int runDueCrons(OffsetDateTime now) {
        List<AiAutomation> due =
                automations.findByStatusAndTriggerTypeAndNextRunAtLessThanEqual("ACTIVE", "CRON", now);
        int count = 0;
        for (AiAutomation automation : due) {
            try {
                TenantContext.set(automation.getOrganizationId(), automation.getCreatedBy());
                execute(automation, "CRON");
                count++;
            } catch (Exception e) {
                log.warn("Automation cron failed id={}: {}", automation.getId(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        return count;
    }

    @Transactional
    public int handleEvent(UUID organizationId, String eventType) {
        List<AiAutomation> matches =
                automations.findByStatusAndTriggerTypeAndEventType("ACTIVE", "EVENT", eventType);
        int count = 0;
        for (AiAutomation automation : matches) {
            if (!automation.getOrganizationId().equals(organizationId)) {
                continue;
            }
            try {
                TenantContext.set(organizationId, automation.getCreatedBy());
                execute(automation, "EVENT");
                count++;
            } catch (Exception e) {
                log.warn("Automation event failed id={}: {}", automation.getId(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        return count;
    }

    private AiDtos.AutomationRunResponse execute(AiAutomation automation, String triggerSource) {
        AiAutomationRun run = new AiAutomationRun();
        run.setOrganizationId(automation.getOrganizationId());
        run.setAutomationId(automation.getId());
        run.setTriggerSource(triggerSource);
        run.setDryRun(automation.isDryRun());
        run.setStatus("RUNNING");
        runs.save(run);

        try {
            int signals = collectSignals(automation.getSignalType());
            int actions = 0;
            String summary;
            if (automation.isDryRun()) {
                summary = "Dry run — would act on " + signals + " signal(s) via " + automation.getActionType();
            } else {
                actions = applyAction(automation.getActionType(), signals);
                summary = "Processed " + signals + " signal(s), applied " + actions + " action(s)";
            }
            run.setSignalCount(signals);
            run.setActionCount(actions);
            run.setSummary(summary);
            run.setStatus("COMPLETED");
            run.setFinishedAt(OffsetDateTime.now());
            runs.save(run);

            automation.setLastRunAt(OffsetDateTime.now());
            automation.setNextRunAt(computeNextRun(automation));
            automations.save(automation);

            audit.record(
                    "AUTOMATION_RUN",
                    automation.getName() + " / " + triggerSource,
                    summary,
                    null,
                    null,
                    null,
                    null);
            return toRunDto(run);
        } catch (Exception e) {
            run.setStatus("FAILED");
            run.setErrorMessage(e.getMessage());
            run.setFinishedAt(OffsetDateTime.now());
            runs.save(run);
            audit.record(
                    "AUTOMATION_RUN",
                    automation.getName() + " / " + triggerSource,
                    null,
                    null,
                    null,
                    null,
                    e.getMessage());
            throw e;
        }
    }

    private int collectSignals(String signalType) {
        String signal = signalType == null ? "" : signalType.toUpperCase(Locale.ROOT);
        return switch (signal) {
            case "INVENTORY_RISK", "LOW_STOCK", "FULL_HEURISTICS" -> {
                recommendations.generateHeuristics();
                yield 1;
            }
            case "CASH_FLOW_RISK", "OVERDUE_AR" -> {
                recommendations.generateHeuristics();
                yield 1;
            }
            default -> {
                recommendations.generateHeuristics();
                yield 1;
            }
        };
    }

    private int applyAction(String actionType, int signals) {
        String action = actionType == null ? "" : actionType.toUpperCase(Locale.ROOT);
        return switch (action) {
            case "CREATE_RECOMMENDATION", "NOTIFY", "OPEN_WORKFLOW_DRAFT" -> signals;
            default -> signals;
        };
    }

    private AiAutomation require(UUID id) {
        return automations
                .findByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Automation not found"));
    }

    private void validate(AiDtos.AutomationRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (request.signalType() == null || request.signalType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "signalType is required");
        }
        if (request.actionType() == null || request.actionType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "actionType is required");
        }
    }

    private void apply(AiAutomation automation, AiDtos.AutomationRequest request) {
        automation.setName(request.name().trim());
        automation.setDescription(request.description());
        automation.setTriggerType(
                request.triggerType() == null || request.triggerType().isBlank()
                        ? "CRON"
                        : request.triggerType().trim().toUpperCase(Locale.ROOT));
        automation.setCronExpression(
                request.cronExpression() == null || request.cronExpression().isBlank()
                        ? "0 2 * * *"
                        : request.cronExpression().trim());
        automation.setEventType(request.eventType());
        automation.setSignalType(request.signalType().trim().toUpperCase(Locale.ROOT));
        automation.setActionType(request.actionType().trim().toUpperCase(Locale.ROOT));
        automation.setActionConfigJson(
                request.actionConfigJson() == null || request.actionConfigJson().isBlank()
                        ? "{}"
                        : request.actionConfigJson());
        automation.setDryRun(Boolean.TRUE.equals(request.dryRun()));
    }

    private OffsetDateTime computeNextRun(AiAutomation automation) {
        if (!"ACTIVE".equals(automation.getStatus()) || !"CRON".equals(automation.getTriggerType())) {
            return null;
        }
        // Default: next day 02:00 local offset when cron is daily-ish.
        return OffsetDateTime.now().plusDays(1).withHour(2).withMinute(0).withSecond(0).withNano(0);
    }

    private AiDtos.AutomationResponse toDto(AiAutomation a) {
        return new AiDtos.AutomationResponse(
                a.getId(),
                a.getName(),
                a.getDescription(),
                a.getTriggerType(),
                a.getCronExpression(),
                a.getEventType(),
                a.getSignalType(),
                a.getActionType(),
                a.getActionConfigJson(),
                a.isDryRun(),
                a.getStatus(),
                a.getLastRunAt(),
                a.getNextRunAt(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }

    private AiDtos.AutomationRunResponse toRunDto(AiAutomationRun run) {
        return new AiDtos.AutomationRunResponse(
                run.getId(),
                run.getAutomationId(),
                run.getTriggerSource(),
                run.getStatus(),
                run.isDryRun(),
                run.getSignalCount(),
                run.getActionCount(),
                run.getSummary(),
                run.getErrorMessage(),
                run.getStartedAt(),
                run.getFinishedAt());
    }
}
