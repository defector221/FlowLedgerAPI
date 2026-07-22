package com.flowledger.ai.tools;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.supplier.dto.SupplierDtos;
import com.flowledger.supplier.service.SupplierService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAiEnabled
public class SupplierTool {
    private final SupplierService supplierService;

    public SupplierTool(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @Tool("Summarize suppliers")
    public String summarize(String query) {
        var page = supplierService.search(new SupplierDtos.Search(null, false), PageRequest.of(0, 20));
        String sample = page.getContent().stream()
                .map(s -> s.supplierCode() + " " + s.supplierName())
                .reduce((a, b) -> a + "; " + b)
                .orElse("none");
        return "Suppliers: total=" + page.getTotalElements() + ". Sample: " + sample + ". Query=" + query;
    }
}
