package com.flowledger.commerce.publisher.model;

import java.util.UUID;

public record CategoryPublishedSnapshot(
        UUID organizationId, UUID categoryId, String name, UUID parentId, int productCount) {}
