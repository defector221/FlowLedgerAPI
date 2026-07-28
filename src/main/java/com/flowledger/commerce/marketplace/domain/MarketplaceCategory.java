package com.flowledger.commerce.marketplace.domain;

import java.util.UUID;

public record MarketplaceCategory(UUID id, UUID categoryId, String name, UUID parentId, int productCount) {}
