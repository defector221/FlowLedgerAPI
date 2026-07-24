package com.flowledger.ai.tools;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.sales.entity.SalesInvoice;
import com.flowledger.sales.service.SalesInvoiceService;
import dev.langchain4j.agent.tool.Tool;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAiEnabled
public class SalesTool {
    private final SalesInvoiceService salesInvoiceService;

    public SalesTool(SalesInvoiceService salesInvoiceService) {
        this.salesInvoiceService = salesInvoiceService;
    }

    @Tool("Summarize recent sales invoices")
    public String summarize(String query) {
        List<SalesInvoice> invoices =
                salesInvoiceService.list(null, null, Pageable.unpaged()).content();
        String sample = invoices.stream()
                .limit(10)
                .map(i -> i.getInvoiceNumber()
                        + " date="
                        + i.getInvoiceDate()
                        + " status="
                        + i.getStatus()
                        + " total="
                        + i.getGrandTotal()
                        + " outstanding="
                        + i.getOutstandingAmount())
                .collect(Collectors.joining("; "));
        return "Sales invoices: count="
                + invoices.size()
                + ". Recent: "
                + (sample.isBlank() ? "none" : sample)
                + ". Query="
                + query;
    }
}
