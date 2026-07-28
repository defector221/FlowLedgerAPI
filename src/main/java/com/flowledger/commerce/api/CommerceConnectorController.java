package com.flowledger.commerce.api;

import com.flowledger.commerce.auth.CommerceSecurityContext;
import com.flowledger.commerce.connector.ConnectorSyncService;
import com.flowledger.commerce.connector.entity.CommerceConnectorSyncJob;
import com.flowledger.common.dto.ApiResponse;
import com.flowledger.common.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commerce/connectors")
@PreAuthorize("hasAuthority('COMMERCE_VIEW')")
public class CommerceConnectorController {
    private final ConnectorSyncService syncService;

    public CommerceConnectorController(ConnectorSyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/jobs")
    public ApiResponse<List<CommerceConnectorSyncJob>> listJobs() {
        UUID orgId = TenantContext.getOrganizationId();
        return ApiResponse.of(syncService.listJobs(orgId));
    }

    @PostMapping("/jobs")
    @PreAuthorize("hasAnyAuthority('COMMERCE_CONFIG_WRITE', 'COMMERCE_ADMIN')")
    public ApiResponse<CommerceConnectorSyncJob> enqueue(@RequestBody EnqueueSyncRequest request) {
        UUID orgId = TenantContext.getOrganizationId();
        return ApiResponse.of(syncService.enqueue(orgId, request.connectorType(), request.syncType(), request.payload()));
    }

    public record EnqueueSyncRequest(String connectorType, String syncType, Map<String, Object> payload) {}
}
