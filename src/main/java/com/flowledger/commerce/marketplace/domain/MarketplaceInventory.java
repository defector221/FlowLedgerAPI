package com.flowledger.commerce.marketplace.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record MarketplaceInventory(
        UUID id, UUID storeId, UUID productId, BigDecimal inventoryQty, long version) {}
