package com.flowledger.ai.tools;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.customer.dto.CustomerDtos;
import com.flowledger.customer.service.CustomerService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAiEnabled
public class CustomerTool {
    private final CustomerService customerService;

    public CustomerTool(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Tool("Summarize customers")
    public String summarize(String query) {
        var page = customerService.search(new CustomerDtos.Search(null, false), PageRequest.of(0, 20));
        String sample = page.getContent().stream()
                .map(c -> c.customerCode() + " " + c.customerName())
                .reduce((a, b) -> a + "; " + b)
                .orElse("none");
        return "Customers: total=" + page.getTotalElements() + ". Sample: " + sample + ". Query=" + query;
    }
}
