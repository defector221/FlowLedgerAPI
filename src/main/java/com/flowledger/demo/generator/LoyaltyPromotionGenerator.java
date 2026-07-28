package com.flowledger.demo.generator;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.demo.DemoSeedContext;
import com.flowledger.demo.scenario.DemoBlueprint;
import com.flowledger.demo.util.DemoIsolatedWork;
import com.flowledger.demo.util.ProgressLogger;
import com.flowledger.retail.domain.RetailEnums.PromoType;
import com.flowledger.retail.dto.RetailDtos.PromotionRequest;
import com.flowledger.retail.dto.RetailDtos.TierRequest;
import com.flowledger.retail.service.RetailLoyaltyService;
import com.flowledger.retail.service.RetailPricingService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LoyaltyPromotionGenerator {
    private final RetailLoyaltyService retailLoyaltyService;
    private final RetailPricingService retailPricingService;
    private final DemoIsolatedWork isolated;

    public LoyaltyPromotionGenerator(
            RetailLoyaltyService retailLoyaltyService,
            RetailPricingService retailPricingService,
            DemoIsolatedWork isolated) {
        this.retailLoyaltyService = retailLoyaltyService;
        this.retailPricingService = retailPricingService;
        this.isolated = isolated;
    }

    public void generate(DemoSeedContext ctx, ProgressLogger progress) {
        DemoBlueprint blueprint = ctx.getBlueprint();
        TenantContext.set(ctx.getOrganizationId(), ctx.getAdminUserId());
        progress.stage("Loyalty and promotions");

        List<String> created = new ArrayList<>();

        if (blueprint.workflow().loyaltyEnabled()) {
            try {
                isolated.run(() -> {
                    retailLoyaltyService.createTier(
                            new TierRequest("SILVER", "Silver", new BigDecimal("500"), new BigDecimal("1")));
                    retailLoyaltyService.createTier(
                            new TierRequest("GOLD", "Gold", new BigDecimal("2000"), new BigDecimal("1.5")));
                });
                created.add("loyalty:Silver/Gold");
            } catch (RuntimeException ex) {
                ctx.getMeta().put("loyaltySeedError", ex.getMessage());
            }
        }

        if (blueprint.workflow().promotionsEnabled()) {
            try {
                isolated.run(() -> {
                    retailPricingService.createPromotion(new PromotionRequest(
                            "WELCOME10",
                            "Welcome 10% Off",
                            PromoType.PERCENT_OFF,
                            new BigDecimal("10"),
                            null,
                            null,
                            null,
                            new BigDecimal("500"),
                            null,
                            OffsetDateTime.now().minusDays(1),
                            OffsetDateTime.now().plusMonths(3),
                            null,
                            null,
                            null,
                            null,
                            true));
                    retailPricingService.createPromotion(new PromotionRequest(
                            "FLAT100",
                            "Flat ₹100 Off",
                            PromoType.AMOUNT_OFF,
                            null,
                            new BigDecimal("100"),
                            null,
                            null,
                            new BigDecimal("999"),
                            "FLAT100",
                            OffsetDateTime.now().minusDays(1),
                            OffsetDateTime.now().plusMonths(2),
                            ctx.getStoreIds().isEmpty()
                                    ? null
                                    : ctx.getStoreIds().get(0),
                            null,
                            null,
                            null,
                            true));
                });
                created.add("promotions:WELCOME10/FLAT100");
            } catch (RuntimeException ex) {
                ctx.getMeta().put("promotionSeedError", ex.getMessage());
            }
        }

        ctx.getMeta().put("loyaltyPromotionVignettes", created);
        progress.done("Loyalty/promotions (" + String.join(", ", created) + ")");
    }
}
