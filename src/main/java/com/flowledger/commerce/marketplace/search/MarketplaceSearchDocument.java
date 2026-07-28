package com.flowledger.commerce.marketplace.search;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** OpenSearch document for marketplace store/product discovery (separate from ERP global search index). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceSearchDocument {
    public static final String TYPE_STORE = "MARKETPLACE_STORE";
    public static final String TYPE_PRODUCT = "MARKETPLACE_PRODUCT";

    private String documentId;
    private String entityType;
    private String indexRowId;
    private String storeId;
    private String productId;
    private String title;
    private String searchText;
    private String sku;
    private String barcode;
    private String gtin;
    private String brand;
    private String categoryId;
    private String categoryName;
    private BigDecimal price;
    private String currency;
    private BigDecimal inventoryQty;
    private String city;
    private String postalCode;
    private String visibility;
    private boolean published;
    private List<String> imageUrls;
    private Double latitude;
    private Double longitude;
    private Map<String, Double> location;
    private String updatedAt;
}
