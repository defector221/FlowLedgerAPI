package com.flowledger.search.dto;

import java.util.List;
import java.util.UUID;

public final class SearchDtos {
    private SearchDtos() {}

    public record Result(UUID entityId, String entityType, String title, String subtitle, String referenceNumber) {}

    public record Response(String query, List<Result> results, long total, int page, int size, boolean hasMore) {}

    public record ReindexResponse(int indexed, int failed) {}
}
