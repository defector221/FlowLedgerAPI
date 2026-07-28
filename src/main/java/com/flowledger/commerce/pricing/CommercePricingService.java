package com.flowledger.commerce.pricing;

import com.flowledger.commerce.common.CommerceTenantScope;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.commerce.rules.context.PromotionContext;
import com.flowledger.commerce.rules.context.PromotionResult;
import com.flowledger.commerce.rules.domain.SalesChannel;
import com.flowledger.commerce.rules.engine.CouponEngine;
import com.flowledger.commerce.rules.engine.PromotionEngine;
import com.flowledger.retail.dto.RetailDtos.ApplyCouponRequest;
import com.flowledger.retail.dto.RetailDtos.ApplyCouponResponse;
import com.flowledger.retail.dto.RetailDtos.ResolvePriceResponse;
import com.flowledger.retail.entity.RetailStore;
import com.flowledger.retail.repository.RetailStoreRepository;
import com.flowledger.retail.service.RetailPricingService;
import com.flowledger.tax.dto.TaxCalculationDtos;
import com.flowledger.tax.dto.TaxCalculationDtos.TaxResult;
import com.flowledger.tax.service.TaxLineCalculator;
import com.flowledger.tax.service.TaxLineCalculator.DocumentLineInput;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CommercePricingService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final RetailPricingService retailPricing;
    private final TaxLineCalculator taxLineCalculator;
    private final RetailStoreRepository stores;
    private final OrganizationRepository organizations;
    private final PromotionEngine promotionEngine;
    private final CouponEngine couponEngine;

    public CommercePricingService(
            RetailPricingService retailPricing,
            TaxLineCalculator taxLineCalculator,
            RetailStoreRepository stores,
            OrganizationRepository organizations,
            PromotionEngine promotionEngine,
            CouponEngine couponEngine) {
        this.retailPricing = retailPricing;
        this.taxLineCalculator = taxLineCalculator;
        this.stores = stores;
        this.organizations = organizations;
        this.promotionEngine = promotionEngine;
        this.couponEngine = couponEngine;
    }

    public CommerceLinePricing priceLine(
            UUID organizationId,
            UUID storeId,
            UUID productId,
            UUID variantId,
            BigDecimal quantity,
            String placeOfSupplyState) {
        return CommerceTenantScope.run(organizationId, () -> {
            RetailStore store = stores
                    .findByIdAndOrganizationIdAndDeletedFalse(storeId, organizationId)
                    .orElseThrow(() -> new IllegalArgumentException("Store not found"));
            BigDecimal qty = quantity == null || quantity.signum() <= 0 ? BigDecimal.ONE : quantity;

            ResolvePriceResponse price =
                    retailPricing.resolvePrice(storeId, productId, variantId, qty);
            BigDecimal unitPrice = price.unitPrice() != null ? price.unitPrice() : BigDecimal.ZERO;
            BigDecimal lineSubtotal = unitPrice.multiply(qty).setScale(4, RoundingMode.HALF_UP);

            Organization org = organizations
                    .findById(organizationId)
                    .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
            String orgState = org.getStateCode() != null ? org.getStateCode() : org.getState();
            String pos = placeOfSupplyState != null ? placeOfSupplyState : store.getState();

            TaxResult tax = taxLineCalculator.calculateDocumentLine(new DocumentLineInput(
                    orgState,
                    pos,
                    "IN",
                    LocalDate.now(),
                    productId,
                    null,
                    null,
                    qty,
                    unitPrice,
                    BigDecimal.ZERO,
                    false,
                    null,
                    "GST",
                    null,
                    null,
                    null));

            BigDecimal lineTax = tax.taxableAmount() != null
                    ? nz(tax.cgstAmount()).add(nz(tax.sgstAmount())).add(nz(tax.igstAmount()))
                    : BigDecimal.ZERO;
            BigDecimal lineTotal = lineSubtotal.add(lineTax).setScale(4, RoundingMode.HALF_UP);

            return new CommerceLinePricing(
                    productId,
                    variantId,
                    store.getWarehouseId(),
                    unitPrice,
                    price.source(),
                    "INR",
                    lineSubtotal,
                    BigDecimal.ZERO,
                    lineTax,
                    lineTotal,
                    extractTaxRate(tax),
                    "GST");
        });
    }

    public ApplyCouponResponse applyCoupon(UUID organizationId, String couponCode, BigDecimal billAmount) {
        return CommerceTenantScope.run(organizationId, () -> {
            var rule = couponEngine.validate(organizationId, couponCode);
            if (rule.isPresent()) {
                BigDecimal discount = couponEngine.previewDiscount(rule.get(), billAmount);
                BigDecimal net = billAmount.subtract(discount);
                return new ApplyCouponResponse(couponCode, true, discount, net, rule.get().getName());
            }
            PromotionContext ctx = PromotionContext.forCheckout(
                    organizationId, null, null, SalesChannel.COMMERCE, billAmount, couponCode, false, List.of());
            PromotionResult result = promotionEngine.evaluate(ctx);
            if (result.applied()) {
                BigDecimal net = billAmount.subtract(result.discountTotal());
                return new ApplyCouponResponse(couponCode, true, result.discountTotal(), net, "Promotion applied");
            }
            return retailPricing.applyCoupon(new ApplyCouponRequest(couponCode, billAmount));
        });
    }

    private static BigDecimal extractTaxRate(TaxResult tax) {
        if (tax.breakdown() != null && tax.breakdown().lines() != null) {
            return tax.breakdown().lines().stream()
                    .map(TaxCalculationDtos.TaxBreakdownLine::rate)
                    .filter(r -> r != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
