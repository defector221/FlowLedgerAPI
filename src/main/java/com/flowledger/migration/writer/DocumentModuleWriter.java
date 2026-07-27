package com.flowledger.migration.writer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Writers that consume grouped rows (header + lines) for a single document. */
public interface DocumentModuleWriter extends ModuleWriter {
    String groupKey(Map<String, String> row);

    WriteResult writeDocument(UUID organizationId, List<Map<String, String>> rows);

    @Override
    default WriteResult write(UUID organizationId, Map<String, String> normalized) {
        return writeDocument(organizationId, List.of(normalized));
    }
}
