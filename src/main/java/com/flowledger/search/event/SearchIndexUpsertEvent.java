package com.flowledger.search.event;

import com.flowledger.search.model.SearchEntityType;
import java.util.UUID;

public record SearchIndexUpsertEvent(UUID organizationId, SearchEntityType entityType, UUID entityId) {}
