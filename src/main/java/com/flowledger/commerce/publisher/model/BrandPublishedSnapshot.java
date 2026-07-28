package com.flowledger.commerce.publisher.model;

import java.util.UUID;

public record BrandPublishedSnapshot(UUID organizationId, String brandName, int productCount) {}
