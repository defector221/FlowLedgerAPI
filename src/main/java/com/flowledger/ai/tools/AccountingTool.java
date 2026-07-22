package com.flowledger.ai.tools;

import com.flowledger.accounting.service.AccountService;
import com.flowledger.accounting.service.reporting.AccountingReportService;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import dev.langchain4j.agent.tool.Tool;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAiEnabled
public class AccountingTool {
    private final AccountingReportService accountingReportService;
    private final AccountService accountService;

    public AccountingTool(AccountingReportService accountingReportService, AccountService accountService) {
        this.accountingReportService = accountingReportService;
        this.accountService = accountService;
    }

    @Tool("Summarize accounting dashboard and chart of accounts size")
    public String summarize(String query) {
        try {
            var dash = accountingReportService.dashboard();
            int accounts = accountService.list().size();
            LocalDate to = LocalDate.now();
            LocalDate from = to.withDayOfMonth(1);
            var gst = accountingReportService.gstSummary(from, to);
            return "Accounting dashboard="
                    + dash
                    + "; accounts="
                    + accounts
                    + "; MTD GST summary="
                    + gst
                    + "; Query="
                    + query;
        } catch (Exception e) {
            return "Accounting summary unavailable: " + e.getMessage() + "; Query=" + query;
        }
    }
}
