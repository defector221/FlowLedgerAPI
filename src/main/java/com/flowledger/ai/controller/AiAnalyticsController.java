package com.flowledger.ai.controller;

import com.flowledger.ai.analytics.ForecastService;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.common.security.UserPrincipal;
import com.flowledger.common.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ai/analytics")
@ConditionalOnAiEnabled
public class AiAnalyticsController {
    private final ForecastService forecastService;

    public AiAnalyticsController(ForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @GetMapping("/forecasts")
    @PreAuthorize("hasAuthority('AI_ANALYSIS') or hasAuthority('AI_CHAT') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.ForecastResponse forecasts(
            @AuthenticationPrincipal UserPrincipal principal, @RequestParam(defaultValue = "SALES") String type) {
        ensureTenant(principal);
        return forecastService.forecast(type);
    }

    private void ensureTenant(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        TenantContext.set(principal.getOrgId(), principal.getId());
    }
}
