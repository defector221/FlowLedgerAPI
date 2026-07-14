package com.flowledger.search.model;

import java.util.List;

public record SearchPageResult(List<SearchDocument> documents, long total, int from, int size) {
    public boolean hasMore() {
        return from + documents.size() < total;
    }
}
