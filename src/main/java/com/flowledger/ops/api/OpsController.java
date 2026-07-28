package com.flowledger.ops.api;

import com.flowledger.common.dto.ApiResponse;
import com.flowledger.demo.DemoDataOrchestrator.DemoDisabledException;
import com.flowledger.demo.DemoSeedRequest;
import com.flowledger.demo.config.DemoProperties;
import com.flowledger.demo.job.DemoSeedJobService;
import com.flowledger.demo.scenario.DemoScenarioRegistry;
import com.flowledger.ops.audit.OpsAuditService;
import com.flowledger.ops.entity.PlatformAuditLog;
import com.flowledger.ops.service.OpsDashboardService;
import com.flowledger.ops.service.OpsOrganizationService;
import com.flowledger.ops.service.OpsOrganizationService.CreateOrgRequest;
import com.flowledger.platform.service.EditionService;
import com.flowledger.platform.service.OrganizationModuleService;
import com.flowledger.subscription.entity.OrganizationSubscription;
import com.flowledger.subscription.entity.SubscriptionInvoice;
import com.flowledger.subscription.entity.SubscriptionPlan;
import com.flowledger.subscription.repository.OrganizationSubscriptionRepository;
import com.flowledger.subscription.repository.SubscriptionInvoiceRepository;
import com.flowledger.subscription.repository.SubscriptionPlanRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ops")
public class OpsController {
    private final OpsDashboardService dashboard;
    private final OpsOrganizationService organizations;
    private final OpsAuditService audit;
    private final SubscriptionPlanRepository plans;
    private final OrganizationSubscriptionRepository subscriptions;
    private final SubscriptionInvoiceRepository invoices;
    private final OrganizationModuleService modules;
    private final EditionService editions;
    private final DemoProperties demoProps;
    private final DemoScenarioRegistry demoRegistry;
    private final DemoSeedJobService demoSeedJobs;
    private final HealthEndpoint healthEndpoint;

    public OpsController(
            OpsDashboardService dashboard,
            OpsOrganizationService organizations,
            OpsAuditService audit,
            SubscriptionPlanRepository plans,
            OrganizationSubscriptionRepository subscriptions,
            SubscriptionInvoiceRepository invoices,
            OrganizationModuleService modules,
            EditionService editions,
            DemoProperties demoProps,
            DemoScenarioRegistry demoRegistry,
            DemoSeedJobService demoSeedJobs,
            HealthEndpoint healthEndpoint) {
        this.dashboard = dashboard;
        this.organizations = organizations;
        this.audit = audit;
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.invoices = invoices;
        this.modules = modules;
        this.editions = editions;
        this.demoProps = demoProps;
        this.demoRegistry = demoRegistry;
        this.demoSeedJobs = demoSeedJobs;
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/dashboard/summary")
    @PreAuthorize("hasAuthority('DASHBOARD_READ')")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.of(dashboard.summary());
    }

    @GetMapping("/organizations")
    @PreAuthorize("hasAuthority('ORG_READ')")
    public ApiResponse<Map<String, Object>> listOrgs(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> result = organizations.list(q, PageRequest.of(page, Math.min(size, 100)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", result.getContent());
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        return ApiResponse.of(body);
    }

    @GetMapping("/organizations/{id}")
    @PreAuthorize("hasAuthority('ORG_READ')")
    public ApiResponse<Map<String, Object>> getOrg(@PathVariable UUID id) {
        return ApiResponse.of(organizations.get(id));
    }

    @PostMapping("/organizations")
    @PreAuthorize("hasAuthority('ORG_WRITE')")
    public ApiResponse<Map<String, Object>> createOrg(@RequestBody CreateOrgRequest request) {
        return ApiResponse.of(organizations.create(request));
    }

    @PatchMapping("/organizations/{id}/status")
    @PreAuthorize("hasAuthority('ORG_WRITE')")
    public ApiResponse<Map<String, Object>> status(
            @PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ApiResponse.of(organizations.setStatus(id, body.get("status")));
    }

    @DeleteMapping("/organizations/{id}")
    @PreAuthorize("hasAuthority('ORG_WRITE')")
    public ApiResponse<Map<String, Object>> deleteOrg(
            @PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ApiResponse.of(organizations.purge(id, body == null ? null : body.get("confirmName")));
    }

    @GetMapping("/plans")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_READ')")
    public ApiResponse<List<SubscriptionPlan>> plans() {
        return ApiResponse.of(plans.findAll());
    }

    @GetMapping("/subscriptions")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_READ')")
    public ApiResponse<List<OrganizationSubscription>> subscriptions() {
        return ApiResponse.of(subscriptions.findAll());
    }

    @GetMapping("/organizations/{id}/invoices")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_READ')")
    public ApiResponse<List<SubscriptionInvoice>> orgInvoices(@PathVariable UUID id) {
        return ApiResponse.of(invoices.findAll().stream()
                .filter(i -> id.equals(i.getOrganizationId()))
                .toList());
    }

    @GetMapping("/organizations/{id}/modules")
    @PreAuthorize("hasAuthority('ORG_READ')")
    public ApiResponse<?> orgModules(@PathVariable UUID id) {
        return ApiResponse.of(modules.listModules(id));
    }

    @GetMapping("/organizations/{id}/edition")
    @PreAuthorize("hasAuthority('ORG_READ')")
    public ApiResponse<?> orgEdition(@PathVariable UUID id) {
        return ApiResponse.of(editions.currentEdition(id));
    }

    @GetMapping("/demo/scenarios")
    @PreAuthorize("hasAuthority('DEMO_READ')")
    public ApiResponse<List<Map<String, Object>>> demoScenarios() {
        if (!demoProps.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        List<Map<String, Object>> list = demoRegistry.all().stream()
                .map(s -> {
                    var bp = demoRegistry.require(s);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("command", s.command());
                    m.put("organizationName", s.organizationName());
                    m.put("description", s.description());
                    m.put("branchCount", bp.branchCount());
                    m.put("storeCount", bp.storeCount());
                    m.put("productCount", bp.productCount());
                    m.put("estimatedMinutes", bp.estimatedMinutes());
                    return m;
                })
                .toList();
        return ApiResponse.of(list);
    }

    @GetMapping("/demo/options")
    @PreAuthorize("hasAuthority('DEMO_READ')")
    public ApiResponse<Map<String, Object>> demoOptions() {
        if (!demoProps.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("enabled", demoProps.isEnabled());
        opts.put("allowReset", demoProps.isAllowReset());
        opts.put("defaultScenario", demoProps.getScenario());
        return ApiResponse.of(opts);
    }

    @PostMapping("/demo/seed")
    @PreAuthorize("hasAuthority('DEMO_SEED')")
    public ApiResponse<Map<String, Object>> demoSeed(@RequestBody(required = false) DemoSeedRequest request) {
        if (!demoProps.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        try {
            Map<String, Object> job =
                    demoSeedJobs.enqueue(request == null ? new DemoSeedRequest(null, null, null, null) : request);
            audit.record(
                    "DEMO_SEED_ENQUEUED",
                    null,
                    "DemoSeedJob",
                    String.valueOf(job.get("id")),
                    "SUCCESS");
            return ApiResponse.of(job);
        } catch (DemoDisabledException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/demo/jobs")
    @PreAuthorize("hasAuthority('DEMO_READ')")
    public ApiResponse<List<Map<String, Object>>> demoJobs() {
        if (!demoProps.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return ApiResponse.of(demoSeedJobs.listRecent());
    }

    @GetMapping("/demo/jobs/{id}")
    @PreAuthorize("hasAuthority('DEMO_READ')")
    public ApiResponse<Map<String, Object>> demoJob(@PathVariable UUID id) {
        if (!demoProps.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return ApiResponse.of(demoSeedJobs
                .get(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Demo seed job not found")));
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ApiResponse<Map<String, Object>> audit(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        Page<PlatformAuditLog> result = audit.list(PageRequest.of(page, Math.min(size, 100)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", result.getContent());
        body.put("page", result.getNumber());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        return ApiResponse.of(body);
    }

    @GetMapping("/health")
    @PreAuthorize("hasAuthority('HEALTH_READ')")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            var h = healthEndpoint.health();
            body.put("status", h.getStatus().getCode());
            if (h instanceof org.springframework.boot.actuate.health.Health health) {
                body.put("details", health.getDetails());
            }
        } catch (Exception ex) {
            body.put("status", "UNKNOWN");
            body.put("error", ex.getMessage());
        }
        body.put("database", "UP");
        return ApiResponse.of(body);
    }

    /** Phase 2 stubs */
    @GetMapping({
        "/operations",
        "/jobs",
        "/analytics",
        "/monitoring",
        "/events",
        "/notifications",
        "/developer",
        "/support"
    })
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> stub() {
        return ApiResponse.of(Map.of("available", false, "message", "Coming in Phase 2"));
    }
}
