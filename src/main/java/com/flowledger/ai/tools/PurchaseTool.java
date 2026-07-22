package com.flowledger.ai.tools;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.purchase.entity.PurchaseOrder;
import com.flowledger.purchase.service.PurchaseOrderService;
import dev.langchain4j.agent.tool.Tool;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAiEnabled
public class PurchaseTool {
    private final PurchaseOrderService purchaseOrderService;

    public PurchaseTool(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @Tool("Summarize purchase orders")
    public String summarize(String query) {
        List<PurchaseOrder> orders = purchaseOrderService.list();
        String sample = orders.stream()
                .limit(10)
                .map(o -> o.getPoNumber()
                        + " date="
                        + o.getOrderDate()
                        + " status="
                        + o.getStatus()
                        + " total="
                        + o.getGrandTotal())
                .collect(Collectors.joining("; "));
        return "Purchase orders: count="
                + orders.size()
                + ". Recent: "
                + (sample.isBlank() ? "none" : sample)
                + ". Query="
                + query;
    }
}
