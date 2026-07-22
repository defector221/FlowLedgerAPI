package com.flowledger.ai.controller;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.ai.recommendation.RecommendationService;
import com.flowledger.common.security.UserPrincipal;
import com.flowledger.common.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ai/recommendations")
@ConditionalOnAiEnabled
public class RecommendationController {
    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AI_RECOMMENDATION') or hasRole('ORGANIZATION_ADMIN')")
    public List<AiDtos.RecommendationResponse> list(
            @AuthenticationPrincipal UserPrincipal principal, @RequestParam(required = false) String status) {
        ensureTenant(principal);
        return recommendationService.list(status);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('AI_RECOMMENDATION') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.RecommendationResponse patch(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody AiDtos.RecommendationPatchRequest request) {
        ensureTenant(principal);
        return recommendationService.patch(id, request);
    }

    @PatchMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('AI_RECOMMENDATION') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.RecommendationResponse acknowledge(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        ensureTenant(principal);
        return recommendationService.acknowledge(id);
    }

    @PatchMapping("/{id}/dismiss")
    @PreAuthorize("hasAuthority('AI_RECOMMENDATION') or hasRole('ORGANIZATION_ADMIN')")
    public AiDtos.RecommendationResponse dismiss(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        ensureTenant(principal);
        return recommendationService.dismiss(id);
    }

    private void ensureTenant(UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        TenantContext.set(principal.getOrgId(), principal.getId());
    }
}
