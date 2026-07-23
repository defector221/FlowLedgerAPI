package com.flowledger.ai.tools;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.organization.repository.OrganizationSettingsRepository;
import com.flowledger.retail.dto.RetailDtos.DailySalesResponse;
import com.flowledger.retail.dto.RetailDtos.StoreResponse;
import com.flowledger.retail.service.RetailAnalyticsService;
import com.flowledger.retail.service.RetailStoreService;
import com.flowledger.common.tenant.TenantContext;
import dev.langchain4j.agent.tool.Tool;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Future-ready retail AI assistant tools — summarizes store POS sales when retail is enabled.
 */
@Component
@ConditionalOnAiEnabled
public class RetailTool {
    private final OrganizationSettingsRepository settingsRepository;
    private final RetailStoreService storeService;
    private final RetailAnalyticsService analyticsService;

    public RetailTool(
            OrganizationSettingsRepository settingsRepository,
            RetailStoreService storeService,
            RetailAnalyticsService analyticsService) {
        this.settingsRepository = settingsRepository;
        this.storeService = storeService;
        this.analyticsService = analyticsService;
    }

    @Tool("Summarize today's retail POS sales across stores")
    public String summarize(String query) {
        var orgId = TenantContext.getOrganizationId();
        boolean enabled = settingsRepository
                .findByOrganizationId(orgId)
                .map(s -> s.isRetailEnabled())
                .orElse(false);
        if (!enabled) {
            return "Retail module is not enabled for this organization. Query=" + query;
        }
        try {
            List<StoreResponse> stores = storeService.listStores();
            if (stores.isEmpty()) {
                return "No retail stores configured. Query=" + query;
            }
            StringBuilder sb = new StringBuilder("Retail daily sales (" + LocalDate.now() + "): ");
            for (StoreResponse store : stores) {
                DailySalesResponse day = analyticsService.dailySales(store.id(), LocalDate.now());
                sb.append(store.code())
                        .append(" bills=")
                        .append(day.saleCount())
                        .append(" total=")
                        .append(day.grandTotal())
                        .append("; ");
            }
            sb.append("Query=").append(query);
            return sb.toString();
        } catch (Exception e) {
            return "Retail summary unavailable: " + e.getMessage() + "; Query=" + query;
        }
    }
}
