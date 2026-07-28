package com.flowledger.commerce.cart;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.commerce.marketplace.domain.MarketplaceProduct;
import com.flowledger.commerce.pricing.CommerceLinePricing;
import com.flowledger.commerce.pricing.CommercePricingService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CartItemSnapshotBuilder {
    private final ObjectMapper objectMapper;

    public CartItemSnapshotBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Snapshots build(MarketplaceProduct product, CommerceLinePricing pricing, BigDecimal quantity) {
        Map<String, Object> productSnap = new HashMap<>();
        productSnap.put("productId", product.productId());
        productSnap.put("storeId", product.storeId());
        productSnap.put("sku", product.sku());
        productSnap.put("barcode", product.barcode());
        productSnap.put("gtin", product.gtin());
        productSnap.put("name", product.name());
        productSnap.put("description", product.description());
        productSnap.put("brand", product.brand());
        productSnap.put("categoryId", product.categoryId());
        productSnap.put("categoryName", product.categoryName());

        Map<String, Object> priceSnap = new HashMap<>();
        priceSnap.put("unitPrice", pricing.unitPrice());
        priceSnap.put("source", pricing.priceSource());
        priceSnap.put("currency", pricing.currency());
        priceSnap.put("lineSubtotal", pricing.lineSubtotal());
        priceSnap.put("discount", pricing.discount());

        Map<String, Object> taxSnap = new HashMap<>();
        taxSnap.put("lineTax", pricing.lineTax());
        taxSnap.put("taxRate", pricing.taxRate());
        taxSnap.put("taxType", pricing.taxType());

        Map<String, Object> invSnap = Map.of("quantity", quantity, "warehouseId", pricing.warehouseId());

        return new Snapshots(
                toJson(productSnap),
                toJson(priceSnap),
                toJson(taxSnap),
                "{}",
                toJson(invSnap),
                toJson(product.imageUrls() != null ? product.imageUrls() : List.of()),
                pricing.lineSubtotal(),
                pricing.lineTax(),
                pricing.lineTotal());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    public record Snapshots(
            String productSnapshot,
            String priceSnapshot,
            String taxSnapshot,
            String promotionSnapshot,
            String inventorySnapshot,
            String imageSnapshot,
            BigDecimal lineSubtotal,
            BigDecimal lineTax,
            BigDecimal lineTotal) {}
}
