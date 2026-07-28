package com.flowledger.commerce.api;

import com.flowledger.commerce.analytics.AnalyticsProjectionService;
import com.flowledger.commerce.analytics.entity.AnalyticsOrderFact;
import com.flowledger.common.dto.ApiResponse;
import com.flowledger.common.tenant.TenantContext;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commerce/analytics")
@PreAuthorize("hasAuthority('COMMERCE_VIEW')")
public class CommerceAnalyticsController {
    private final AnalyticsProjectionService analytics;

    public CommerceAnalyticsController(AnalyticsProjectionService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/orders")
    public ApiResponse<List<AnalyticsOrderFact>> orderFacts(
            @RequestParam OffsetDateTime from, @RequestParam OffsetDateTime to) {
        UUID orgId = TenantContext.getOrganizationId();
        return ApiResponse.of(analytics.orderFacts(orgId, from, to));
    }

    @GetMapping("/funnel")
    public ApiResponse<Map<String, Long>> funnel(
            @RequestParam OffsetDateTime from, @RequestParam OffsetDateTime to) {
        UUID orgId = TenantContext.getOrganizationId();
        return ApiResponse.of(analytics.funnelCounts(orgId, from, to));
    }
}
