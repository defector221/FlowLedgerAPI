package com.flowledger.migration.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ParsedSheet(String name, List<String> columns, List<Map<String, String>> rows) {
    public static ParsedSheet empty(String name) {
        return new ParsedSheet(name, List.of(), List.of());
    }

    public ParsedSheet withNormalizedColumns() {
        List<String> normalized =
                columns.stream().map(c -> c == null ? "" : c.trim()).toList();
        List<Map<String, String>> remapped = new ArrayList<>();
        for (Map<String, String> row : rows) {
            Map<String, String> copy = new LinkedHashMap<>();
            for (String col : columns) {
                String key = col == null ? "" : col.trim();
                copy.put(key, row.getOrDefault(col, row.getOrDefault(key, "")));
            }
            remapped.add(copy);
        }
        return new ParsedSheet(name, normalized, remapped);
    }
}
