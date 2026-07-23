package com.flowledger.ai.tools;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.platform.domain.ModuleCodes;
import com.flowledger.platform.service.FeatureService;
import com.flowledger.retail.dto.RetailDtos.DailySalesResponse;
import com.flowledger.retail.dto.RetailDtos.StoreResponse;
import com.flowledger.retail.service.RetailAnalyticsService;
import com.flowledger.retail.service.RetailStoreService;
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
    private final FeatureService featureService;
    private final RetailStoreService storeService;
    private final RetailAnalyticsService analyticsService;

    public RetailTool(
            FeatureService featureService, RetailStoreService storeService, RetailAnalyticsService analyticsService) {
        this.featureService = featureService;
        this.storeService = storeService;
        this.analyticsService = analyticsService;
    }

    @Tool("Summarize today's retail POS sales across stores")
    public String summarize(String query) {
        var orgId = TenantContext.getOrganizationId();
        if (!featureService.hasModule(orgId, ModuleCodes.RETAIL)) {
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
        } catch (Exception ex) {
            return "Unable to summarize retail sales: " + ex.getMessage();
        }
    }
}
