package com.flowledger.search.event;

import com.flowledger.search.model.SearchDocument;
import com.flowledger.search.service.SearchEntityDocumentLoader;
import com.flowledger.search.service.SearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexEventListener {
    private final SearchIndexService indexService;
    private final SearchEntityDocumentLoader documentLoader;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUpsert(SearchIndexUpsertEvent event) {
        try {
            SearchDocument document = documentLoader.load(event.organizationId(), event.entityType(), event.entityId());
            if (document == null) {
                indexService.delete(event.organizationId(), event.entityType(), event.entityId());
                return;
            }
            indexService.index(document);
        } catch (Exception ex) {
            log.warn(
                    "AFTER_COMMIT search upsert failed type={} entityId={}: {}",
                    event.entityType(),
                    event.entityId(),
                    ex.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDelete(SearchIndexDeleteEvent event) {
        try {
            indexService.delete(event.organizationId(), event.entityType(), event.entityId());
        } catch (Exception ex) {
            log.warn(
                    "AFTER_COMMIT search delete failed type={} entityId={}: {}",
                    event.entityType(),
                    event.entityId(),
                    ex.getMessage());
        }
    }
}
