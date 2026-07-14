package com.flowledger.search.event;

import com.flowledger.search.model.SearchEntityType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SearchIndexEventPublisher {
    private final ApplicationEventPublisher events;

    public void upsert(UUID organizationId, SearchEntityType type, UUID entityId) {
        events.publishEvent(new SearchIndexUpsertEvent(organizationId, type, entityId));
    }

    public void delete(UUID organizationId, SearchEntityType type, UUID entityId) {
        events.publishEvent(new SearchIndexDeleteEvent(organizationId, type, entityId));
    }
}
