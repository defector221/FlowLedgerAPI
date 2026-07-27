package com.flowledger.migration.mapping;

import java.util.Locale;

public record FieldMapping(String sourceColumn, String targetField, String transform, String defaultValue) {
    public static FieldMapping of(String source, String target) {
        return new FieldMapping(source, target, null, null);
    }

    public String normalizedSource() {
        return sourceColumn == null ? "" : sourceColumn.trim().toLowerCase(Locale.ROOT);
    }
}
