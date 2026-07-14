package com.flowledger.search.event;

import com.flowledger.search.model.SearchEntityType;
import java.util.UUID;

public record SearchIndexDeleteEvent(UUID organizationId, SearchEntityType entityType, UUID entityId) {}
