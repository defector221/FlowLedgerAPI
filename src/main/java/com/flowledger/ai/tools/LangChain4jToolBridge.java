package com.flowledger.ai.tools;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Bridges domain tool beans annotated with LangChain4j {@code @Tool} into chat orchestration.
 * Full AiServices automatic tool loop can wrap this catalog in a later hardening pass.
 */
@Component
@ConditionalOnAiEnabled
public class LangChain4jToolBridge {
    private final List<Object> toolBeans;

    public LangChain4jToolBridge(
            InventoryTool inventoryTool,
            SalesTool salesTool,
            AccountingTool accountingTool,
            PurchaseTool purchaseTool,
            DashboardTool dashboardTool,
            GstTool gstTool,
            PaymentTool paymentTool,
            CustomerTool customerTool,
            SupplierTool supplierTool,
            RetailTool retailTool) {
        this.toolBeans = List.of(
                inventoryTool,
                salesTool,
                accountingTool,
                purchaseTool,
                dashboardTool,
                gstTool,
                paymentTool,
                customerTool,
                supplierTool,
                retailTool);
    }

    public List<ToolSpecification> specificationsFor(Set<String> allowedToolNames) {
        List<ToolSpecification> specs = new ArrayList<>();
        for (Object bean : toolBeans) {
            for (Method method : bean.getClass().getMethods()) {
                if (method.getAnnotation(dev.langchain4j.agent.tool.Tool.class) == null) {
                    continue;
                }
                String name = method.getName().toLowerCase();
                boolean allowed = allowedToolNames == null
                        || allowedToolNames.isEmpty()
                        || allowedToolNames.stream().anyMatch(a -> name.contains(a.toLowerCase())
                                || a.toLowerCase().contains(name));
                if (!allowed) {
                    continue;
                }
                try {
                    specs.addAll(ToolSpecifications.toolSpecificationsFrom(bean.getClass()));
                    break;
                } catch (Exception ignored) {
                    // keep keyword bridge as fallback
                }
            }
        }
        return specs;
    }

    public String describeTools(Set<String> allowedToolNames) {
        List<ToolSpecification> specs = specificationsFor(allowedToolNames);
        if (specs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Available structured tools:\n");
        for (ToolSpecification spec : specs) {
            sb.append("- ").append(spec.name()).append(": ").append(spec.description()).append('\n');
        }
        return sb.toString();
    }
}
