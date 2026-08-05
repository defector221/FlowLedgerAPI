package com.flowledger.commerce.api;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.commerce.ai.CommerceAiService;
import com.flowledger.commerce.auth.CommerceSecurityContext;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commerce/ai")
@ConditionalOnAiEnabled
public class CommerceAiController {
    private final CommerceAiService commerceAi;

    public CommerceAiController(CommerceAiService commerceAi) {
        this.commerceAi = commerceAi;
    }

    @GetMapping("/stores/{storeId}/similar/{productIndexId}")
    public List<AiDtos.CommerceSimilarProductResponse> similar(
            @PathVariable UUID storeId,
            @PathVariable UUID productIndexId,
            @RequestParam(defaultValue = "6") int limit) {
        return commerceAi.similarProducts(storeId, productIndexId, limit);
    }

    @GetMapping("/stores/{storeId}/search")
    public List<AiDtos.CommerceSimilarProductResponse> semanticSearch(
            @PathVariable UUID storeId,
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit) {
        return commerceAi.semanticSearch(storeId, q, limit);
    }

    @PostMapping("/support")
    @PreAuthorize("isAuthenticated()")
    public AiDtos.CommerceSupportResponse support(@RequestBody AiDtos.CommerceSupportRequest request) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        return commerceAi.support(customerId, request);
    }
}
