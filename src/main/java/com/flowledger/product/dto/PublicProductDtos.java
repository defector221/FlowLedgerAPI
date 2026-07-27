package com.flowledger.product.dto;

import java.util.UUID;

public final class PublicProductDtos {
    private PublicProductDtos() {}

    public record PublicProductCard(
            UUID id,
            String name,
            String sku,
            String barcode,
            String brand,
            String description,
            String hsnSacCode,
            String organizationName,
            String imageUrl,
            String publishedUrl) {}
}
