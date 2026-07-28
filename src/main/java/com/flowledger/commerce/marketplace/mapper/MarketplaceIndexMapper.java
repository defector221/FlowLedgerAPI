package com.flowledger.commerce.marketplace.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.commerce.marketplace.domain.MarketplaceBrand;
import com.flowledger.commerce.marketplace.domain.MarketplaceCategory;
import com.flowledger.commerce.marketplace.domain.MarketplaceInventory;
import com.flowledger.commerce.marketplace.domain.MarketplaceProduct;
import com.flowledger.commerce.marketplace.domain.MarketplaceStore;
import com.flowledger.commerce.publisher.entity.MarketplaceBrandIndex;
import com.flowledger.commerce.publisher.entity.MarketplaceCategoryIndex;
import com.flowledger.commerce.publisher.entity.MarketplaceInventoryIndex;
import com.flowledger.commerce.publisher.entity.MarketplaceProductIndex;
import com.flowledger.commerce.publisher.entity.MarketplaceStoreIndex;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MarketplaceIndexMapper {
    private final ObjectMapper objectMapper;

    public MarketplaceIndexMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MarketplaceStore toStore(MarketplaceStoreIndex index, Double distanceKm) {
        Map<String, Object> payload = parsePayload(index.getPayload());
        return new MarketplaceStore(
                index.getId(),
                index.getStoreId(),
                index.getName(),
                index.getCity(),
                index.getPostalCode(),
                index.getState(),
                index.getCountry(),
                index.getLatitude(),
                index.getLongitude(),
                index.getDiscoveryRadiusKm(),
                index.getVisibility(),
                bool(payload.get("supportsDelivery")),
                bool(payload.get("supportsPickup")),
                bool(payload.get("supportsClickCollect")),
                bool(payload.get("supportsScanAndGo")),
                distanceKm,
                payload);
    }

    public MarketplaceProduct toProduct(MarketplaceProductIndex index) {
        Map<String, Object> payload = parsePayload(index.getPayload());
        String description = payload.get("description") != null ? String.valueOf(payload.get("description")) : null;
        return new MarketplaceProduct(
                index.getId(),
                index.getStoreId(),
                index.getProductId(),
                index.getSku(),
                index.getBarcode(),
                index.getGtin(),
                index.getName(),
                description,
                index.getBrand(),
                index.getCategoryId(),
                index.getCategoryName(),
                index.getPrice(),
                index.getCurrency(),
                index.getInventoryQty(),
                parseImageUrls(index.getImageUrls()),
                payload);
    }

    public MarketplaceInventory toInventory(MarketplaceInventoryIndex index) {
        return new MarketplaceInventory(
                index.getId(), index.getStoreId(), index.getProductId(), index.getInventoryQty(), index.getVersion());
    }

    public MarketplaceCategory toCategory(MarketplaceCategoryIndex index) {
        return new MarketplaceCategory(
                index.getId(), index.getCategoryId(), index.getName(), index.getParentId(), index.getProductCount());
    }

    public MarketplaceBrand toBrand(MarketplaceBrandIndex index) {
        return new MarketplaceBrand(index.getId(), index.getBrandName(), index.getProductCount());
    }

    private Map<String, Object> parsePayload(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private List<String> parseImageUrls(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean b && b;
    }
}
