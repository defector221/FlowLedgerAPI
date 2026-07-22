package com.flowledger.ai.tools;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.inventory.dto.InventoryDtos.Alert;
import com.flowledger.inventory.dto.InventoryDtos.StockPosition;
import com.flowledger.inventory.service.InventoryService;
import dev.langchain4j.agent.tool.Tool;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAiEnabled
public class InventoryTool {
    private final InventoryService inventoryService;

    public InventoryTool(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Tool("Summarize inventory stock overview and low-stock / reorder alerts")
    public String summarize(String query) {
        List<StockPosition> positions = inventoryService.stockOverview();
        List<Alert> low = inventoryService.lowStockAlerts(false);
        List<Alert> reorder = inventoryService.lowStockAlerts(true);
        String top = positions.stream()
                .limit(15)
                .map(p -> p.productName()
                        + " sku="
                        + p.sku()
                        + " qty="
                        + p.available()
                        + " min="
                        + p.minimumStockLevel())
                .collect(Collectors.joining("; "));
        return "Stock positions (sample "
                + Math.min(15, positions.size())
                + "/"
                + positions.size()
                + "): "
                + (top.isBlank() ? "none" : top)
                + ". Low-stock alerts: "
                + low.size()
                + ". Reorder alerts: "
                + reorder.size()
                + ". Query="
                + query;
    }
}
