package com.flowledger.migration.writer;

import com.flowledger.migration.domain.ImportModule;
import java.util.Map;
import java.util.UUID;

public interface ModuleWriter {
    ImportModule module();

    WriteResult write(UUID organizationId, Map<String, String> normalized);

    record WriteResult(boolean success, boolean skipped, UUID entityId, String entityType, String message) {
        public static WriteResult imported(UUID id, String type) {
            return new WriteResult(true, false, id, type, null);
        }

        public static WriteResult skipped(String message) {
            return new WriteResult(true, true, null, null, message);
        }

        public static WriteResult failed(String message) {
            return new WriteResult(false, false, null, null, message);
        }
    }
}
