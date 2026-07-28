package com.flowledger.ops.api;

import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.onboarding.domain.MerchantOnboardingState;
import com.flowledger.commerce.onboarding.entity.MerchantOnboarding;
import com.flowledger.common.dto.ApiResponse;
import com.flowledger.common.dto.PageResponse;
import com.flowledger.ops.security.PlatformPrincipal;
import com.flowledger.ops.service.OpsCommerceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ops/commerce")
public class OpsCommerceController {
    private final OpsCommerceService commerce;

    public OpsCommerceController(OpsCommerceService commerce) {
        this.commerce = commerce;
    }

    @GetMapping("/dashboard/summary")
    @PreAuthorize("hasAuthority('COMMERCE_OPS_READ')")
    public ApiResponse<CommerceDtos.CommerceDashboardSummary> dashboardSummary() {
        return ApiResponse.of(commerce.dashboardSummary());
    }

    @GetMapping("/merchants")
    @PreAuthorize("hasAuthority('COMMERCE_OPS_READ')")
    public ApiResponse<PageResponse<CommerceDtos.OpsMerchantRow>> listMerchants(Pageable pageable) {
        return ApiResponse.of(PageResponse.from(commerce.listMerchants(pageable)));
    }

    @GetMapping("/merchants/{organizationId}")
    @PreAuthorize("hasAuthority('COMMERCE_OPS_READ')")
    public ApiResponse<CommerceDtos.OpsMerchantDetailResponse> getMerchant(@PathVariable UUID organizationId) {
        return ApiResponse.of(commerce.getMerchant(organizationId));
    }

    @PostMapping("/merchants/{organizationId}/onboard")
    @PreAuthorize("hasAuthority('COMMERCE_OPS_WRITE')")
    public ApiResponse<MerchantOnboarding> onboard(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CommerceDtos.OpsOnboardMerchantRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ApiResponse.of(commerce.onboard(organizationId, request, principal.getId()));
    }

    @PutMapping("/merchants/{organizationId}/onboarding")
    @PreAuthorize("hasAuthority('COMMERCE_OPS_WRITE')")
    public ApiResponse<MerchantOnboarding> transition(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CommerceDtos.OpsOnboardingTransitionRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        return ApiResponse.of(commerce.transition(organizationId, request.state(), principal.getId()));
    }

    @PutMapping("/merchants/{organizationId}/suspend")
    @PreAuthorize("hasAuthority('COMMERCE_OPS_WRITE')")
    public ApiResponse<MerchantOnboarding> suspend(
            @PathVariable UUID organizationId, @AuthenticationPrincipal PlatformPrincipal principal) {
        return ApiResponse.of(commerce.suspend(organizationId, principal.getId()));
    }

    @PutMapping("/merchants/{organizationId}/activate")
    @PreAuthorize("hasAuthority('COMMERCE_OPS_WRITE')")
    public ApiResponse<MerchantOnboarding> activate(
            @PathVariable UUID organizationId, @AuthenticationPrincipal PlatformPrincipal principal) {
        return ApiResponse.of(commerce.activate(organizationId, principal.getId()));
    }

    @GetMapping("/integrations/health")
    @PreAuthorize("hasAuthority('COMMERCE_OPS_READ')")
    public ApiResponse<List<CommerceDtos.IntegrationHealthRow>> integrationHealth() {
        return ApiResponse.of(commerce.integrationHealth());
    }
}
