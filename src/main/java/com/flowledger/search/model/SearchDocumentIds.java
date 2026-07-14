package com.flowledger.search.model;

import java.util.UUID;

public final class SearchDocumentIds {
    private SearchDocumentIds() {}

    public static String of(UUID organizationId, SearchEntityType type, UUID entityId) {
        return organizationId + ":" + type.name() + ":" + entityId;
    }
}
