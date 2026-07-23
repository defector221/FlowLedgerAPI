package com.flowledger.ai.tools;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Registry of ERP tools for AI agents. Tools call existing services only (never repositories directly
 * from the LLM layer). LangChain4j @Tool binding can wrap these later; ChatOrchestrationService uses
 * keyword routing as an interim bridge.
 */
@Component
@ConditionalOnAiEnabled
public class AiToolRegistry {
    private final Map<String, Function<String, String>> tools = new HashMap<>();

    public AiToolRegistry(
            InventoryTool inventoryTool,
            SalesTool salesTool,
            AccountingTool accountingTool,
            PurchaseTool purchaseTool,
            DashboardTool dashboardTool,
            GstTool gstTool,
            PaymentTool paymentTool,
            CustomerTool customerTool,
            SupplierTool supplierTool,
            ReportTool reportTool,
            RetailTool retailTool) {
        tools.put("inventory", inventoryTool::summarize);
        tools.put("sales", salesTool::summarize);
        tools.put("accounting", accountingTool::summarize);
        tools.put("purchase", purchaseTool::summarize);
        tools.put("dashboard", dashboardTool::summarize);
        tools.put("gst", gstTool::summarize);
        tools.put("payment", paymentTool::summarize);
        tools.put("customer", customerTool::summarize);
        tools.put("supplier", supplierTool::summarize);
        tools.put("report", reportTool::summarize);
        tools.put("retail", retailTool::summarize);
    }

    public String invoke(String name, String query) {
        Function<String, String> fn = tools.get(name);
        if (fn == null) {
            return "Unknown tool: " + name;
        }
        try {
            return fn.apply(query == null ? "" : query);
        } catch (Exception e) {
            return "Tool " + name + " failed: " + e.getMessage();
        }
    }

    public String invokeAllowed(Set<String> allowed, String query) {
        if (allowed == null || allowed.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String name : allowed) {
            sb.append("[").append(name).append("]\n");
            sb.append(invoke(name, query)).append("\n\n");
        }
        return sb.toString().trim();
    }

    public Set<String> names() {
        return Set.copyOf(tools.keySet());
    }
}
