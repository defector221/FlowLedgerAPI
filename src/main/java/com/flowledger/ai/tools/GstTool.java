package com.flowledger.ai.tools;

import com.flowledger.accounting.service.reporting.AccountingReportService;
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
public class GstTool {
    private final AccountingReportService accountingReportService;
    private final ReportService reportService;

    public GstTool(AccountingReportService accountingReportService, ReportService reportService) {
        this.accountingReportService = accountingReportService;
        this.reportService = reportService;
    }

    @Tool("Summarize GST for the current month and sample GSTR-1 rows")
    public String summarize(String query) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.withDayOfMonth(1);
        try {
            var gst = accountingReportService.gstSummary(from, to);
            List<Map<String, Object>> gstr1 =
                    reportService.report("gstr1", new ReportFilter(from, to, null, null, null, null, null));
            return "GST summary ("
                    + from
                    + " to "
                    + to
                    + "): "
                    + gst
                    + "; GSTR-1 rows="
                    + gstr1.size()
                    + "; Query="
                    + query;
        } catch (Exception e) {
            return "GST summary unavailable: " + e.getMessage() + "; Query=" + query;
        }
    }
}
