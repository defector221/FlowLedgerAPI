package com.flowledger.commerce.api;

import com.flowledger.commerce.rules.context.PromotionContext;
import com.flowledger.commerce.rules.context.PromotionResult;
import com.flowledger.commerce.rules.domain.PromotionRuleType;
import com.flowledger.commerce.rules.domain.RewardOutcomeType;
import com.flowledger.commerce.rules.domain.SalesChannel;
import com.flowledger.commerce.rules.engine.PromotionEngine;
import com.flowledger.commerce.rules.entity.CommercePromotionRule;
import com.flowledger.commerce.rules.repository.CommercePromotionRuleRepository;
import com.flowledger.common.dto.ApiResponse;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.retail.service.PosSaleService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commerce/rules")
public class CommerceRulesController {
    private final CommercePromotionRuleRepository rules;
    private final PromotionEngine promotionEngine;

    public CommerceRulesController(CommercePromotionRuleRepository rules, PromotionEngine promotionEngine) {
        this.rules = rules;
        this.promotionEngine = promotionEngine;
    }

    @GetMapping("/promotions")
    @PreAuthorize("hasAuthority('COMMERCE_VIEW')")
    public ApiResponse<List<CommercePromotionRule>> listPromotions() {
        UUID orgId = TenantContext.getOrganizationId();
        return ApiResponse.of(rules.findByOrganizationIdAndActiveTrueOrderByPriorityAsc(orgId));
    }

    @PostMapping("/promotions")
    @PreAuthorize("hasAnyAuthority('COMMERCE_CONFIG_WRITE', 'COMMERCE_ADMIN')")
    public ApiResponse<CommercePromotionRule> createPromotion(@RequestBody CreatePromotionRequest request) {
        UUID orgId = TenantContext.getOrganizationId();
        CommercePromotionRule rule = new CommercePromotionRule();
        rule.setOrganizationId(orgId);
        rule.setStoreId(request.storeId());
        rule.setCode(request.code());
        rule.setName(request.name());
        rule.setRuleType(request.ruleType());
        rule.setRewardType(request.rewardType());
        rule.setDiscountPercent(request.discountPercent());
        rule.setDiscountAmount(request.discountAmount());
        rule.setCouponCode(request.couponCode());
        rule.setMinOrderTotal(request.minOrderTotal());
        rule.setMaxRedemptions(request.maxRedemptions());
        rule.setPriority(request.priority() != null ? request.priority() : 100);
        rule.setSalesChannel(request.salesChannel());
        rule.setActive(true);
        return ApiResponse.of(rules.save(rule));
    }

    @PostMapping("/evaluate")
    @PreAuthorize("hasAuthority('COMMERCE_VIEW')")
    public ApiResponse<PromotionResult> evaluate(@RequestBody EvaluatePromotionRequest request) {
        UUID orgId = TenantContext.getOrganizationId();
        PromotionContext ctx = PromotionContext.forCheckout(
                orgId,
                request.storeId(),
                request.customerId(),
                request.channel() != null ? request.channel() : SalesChannel.COMMERCE,
                request.orderTotal(),
                request.couponCode(),
                request.firstOrder(),
                List.of());
        return ApiResponse.of(promotionEngine.evaluate(ctx));
    }

    public record CreatePromotionRequest(
            UUID storeId,
            @NotBlank String code,
            @NotBlank String name,
            @NotNull PromotionRuleType ruleType,
            @NotNull RewardOutcomeType rewardType,
            BigDecimal discountPercent,
            BigDecimal discountAmount,
            String couponCode,
            BigDecimal minOrderTotal,
            Integer maxRedemptions,
            Integer priority,
            SalesChannel salesChannel) {}

    public record EvaluatePromotionRequest(
            UUID storeId,
            UUID customerId,
            SalesChannel channel,
            @NotNull BigDecimal orderTotal,
            String couponCode,
            boolean firstOrder) {}
}
