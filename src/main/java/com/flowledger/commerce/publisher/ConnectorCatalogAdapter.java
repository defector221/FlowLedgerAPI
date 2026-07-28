package com.flowledger.commerce.publisher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.commerce.integration.entity.MerchantIntegrationProfile;
import com.flowledger.commerce.merchant.domain.MerchantType;
import com.flowledger.commerce.store.entity.StoreCommerceProfile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ConnectorCatalogAdapter implements CatalogAdapter {
    private final ObjectMapper objectMapper;

    public ConnectorCatalogAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public MerchantType merchantType() {
        return MerchantType.PARTNER;
    }

    @Override
    public List<CatalogItemSnapshot> loadCatalog(MerchantIntegrationProfile integration, StoreCommerceProfile profile) {
        if (integration.getConfigurationJson() == null || integration.getConfigurationJson().isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(integration.getConfigurationJson());
            JsonNode items = root.path("catalog");
            if (!items.isArray()) {
                return List.of();
            }
            List<CatalogItemSnapshot> snapshots = new ArrayList<>();
            for (JsonNode item : items) {
                snapshots.add(parseItem(item));
            }
            return snapshots;
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public Optional<CatalogItemSnapshot> loadItem(
            MerchantIntegrationProfile integration, StoreCommerceProfile profile, UUID productId) {
        return loadCatalog(integration, profile).stream()
                .filter(item -> item.productId().equals(productId))
                .findFirst();
    }

    private CatalogItemSnapshot parseItem(JsonNode item) {
        UUID productId = UUID.fromString(item.path("productId").asText(UUID.randomUUID().toString()));
        List<String> imageUrls = new ArrayList<>();
        JsonNode images = item.path("imageUrls");
        if (images.isArray()) {
            images.forEach(n -> imageUrls.add(n.asText()));
        }
        UUID categoryId = item.hasNonNull("categoryId")
                ? UUID.fromString(item.path("categoryId").asText())
                : null;
        return new CatalogItemSnapshot(
                productId,
                item.path("sku").asText(""),
                item.path("name").asText(""),
                item.path("description").asText(null),
                item.path("price").isNumber() ? item.path("price").decimalValue() : BigDecimal.ZERO,
                item.path("inventoryQty").isNumber() ? item.path("inventoryQty").decimalValue() : BigDecimal.ZERO,
                item.path("barcode").asText(null),
                item.path("gtin").asText(null),
                item.path("brand").asText(null),
                categoryId,
                item.path("categoryName").asText(null),
                item.path("currency").asText("INR"),
                imageUrls,
                item.path("version").asLong(System.currentTimeMillis()));
    }
}
