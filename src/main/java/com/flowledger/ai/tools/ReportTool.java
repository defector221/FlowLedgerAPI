package com.flowledger.ai.tools;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.report.dto.ReportFilter;
import com.flowledger.report.service.ReportService;
import dev.langchain4j.agent.tool.Tool;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAiEnabled
public class ReportTool {
    private final ReportService reportService;

    public ReportTool(ReportService reportService) {
        this.reportService = reportService;
    }

    @Tool("Summarize sales report for the last 30 days")
    public String summarize(String query) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(30);
        try {
            List<Map<String, Object>> rows =
                    reportService.report("sales", new ReportFilter(from, to, null, null, null, null, null));
            return "Sales report rows (" + from + " to " + to + "): " + rows.size() + "; Query=" + query;
        } catch (Exception e) {
            return "Report unavailable: " + e.getMessage() + "; Query=" + query;
        }
    }
}
