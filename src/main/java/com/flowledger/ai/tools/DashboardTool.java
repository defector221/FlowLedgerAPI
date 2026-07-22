package com.flowledger.ai.tools;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.dashboard.service.DashboardService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAiEnabled
public class DashboardTool {
    private final DashboardService dashboardService;

    public DashboardTool(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Tool("Summarize organization dashboard KPIs")
    public String summarize(String query) {
        return "Dashboard summary: " + dashboardService.summary() + "; Query=" + query;
    }
}
