package com.flowledger.ai.controller;

import com.flowledger.ai.automation.AutomationService;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.common.security.UserPrincipal;
import com.flowledger.common.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ai/automations")
@ConditionalOnAiEnabled
public class AiAutomationController {
    private final AutomationService automations;

    public AiAutomationController(AutomationService automations) {
        this.automations = automations;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AI_AUTOMATION') or hasAuthority('AI_ADMIN') or hasRole('ORGANIZATION_ADMIN')")
    public List<AiDtos.AutomationResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        ensureTenant(principal);
        return automations.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('AI_AUTOMATION') or hasAuthority('AI_ADMIN') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.AutomationResponse create(
            @AuthenticationPrincipal UserPrincipal principal, @RequestBody AiDtos.AutomationRequest request) {
        ensureTenant(principal);
        return automations.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('AI_AUTOMATION') or hasAuthority('AI_ADMIN') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.AutomationResponse update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody AiDtos.AutomationRequest request) {
        ensureTenant(principal);
        return automations.update(id, request);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('AI_AUTOMATION') or hasAuthority('AI_ADMIN') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.AutomationResponse activate(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        ensureTenant(principal);
        return automations.activate(id);
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAuthority('AI_AUTOMATION') or hasAuthority('AI_ADMIN') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.AutomationResponse pause(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        ensureTenant(principal);
        return automations.pause(id);
    }

    @PostMapping("/{id}/run")
    @PreAuthorize("hasAuthority('AI_AUTOMATION') or hasAuthority('AI_ADMIN') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.AutomationRunResponse run(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        ensureTenant(principal);
        return automations.runNow(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('AI_AUTOMATION') or hasAuthority('AI_ADMIN') or hasRole('ORGANIZATION_ADMIN')")
    public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        ensureTenant(principal);
        automations.delete(id);
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAuthority('AI_AUTOMATION') or hasAuthority('AI_ADMIN') or hasRole('ORGANIZATION_ADMIN')")
    public List<AiDtos.AutomationRunResponse> runs(
            @AuthenticationPrincipal UserPrincipal principal, @RequestParam(required = false) UUID automationId) {
        ensureTenant(principal);
        return automations.listRuns(automationId);
    }

    private void ensureTenant(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        TenantContext.set(principal.getOrgId(), principal.getId());
    }
}
