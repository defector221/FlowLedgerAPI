package com.flowledger.ai.event;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.recommendation.RecommendationGenerator;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.search.event.SearchIndexDeleteEvent;
import com.flowledger.search.event.SearchIndexUpsertEvent;
import com.flowledger.search.model.SearchEntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges search-index domain events into AI heuristics AFTER_COMMIT so AI work never runs inside
 * ERP business transactions. Active only when {@code flowledger.ai.enabled=true}.
 */
@Component
@ConditionalOnAiEnabled
public class AiSearchEventBridge {
    private static final Logger log = LoggerFactory.getLogger(AiSearchEventBridge.class);

    private final RecommendationGenerator recommendationGenerator;
    private final AiLifecycleEventPublisher lifecycleEvents;

    public AiSearchEventBridge(
            RecommendationGenerator recommendationGenerator, AiLifecycleEventPublisher lifecycleEvents) {
        this.recommendationGenerator = recommendationGenerator;
        this.lifecycleEvents = lifecycleEvents;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUpsert(SearchIndexUpsertEvent event) {
        try {
            TenantContext.set(event.organizationId(), null);
            SearchEntityType type = event.entityType();
            switch (type) {
                case PRODUCT -> {
                    int n = recommendationGenerator.onProductChanged(event.entityId());
                    lifecycleEvents.recommendationSeed(event.organizationId(), type.name(), event.entityId());
                    log.debug("AI inventory heuristics created={} for product={}", n, event.entityId());
                }
                case CUSTOMER -> {
                    int n = recommendationGenerator.onCustomerChanged(event.entityId());
                    lifecycleEvents.recommendationSeed(event.organizationId(), type.name(), event.entityId());
                    log.debug("AI credit heuristics created={} for customer={}", n, event.entityId());
                }
                case SALES_INVOICE, PURCHASE_INVOICE, SUPPLIER, SHIPMENT ->
                    lifecycleEvents.publish(
                            event.organizationId(),
                            AiLifecycleEvent.RECOMMENDATION_SEED,
                            type.name(),
                            event.entityId(),
                            java.util.Map.of("source", "search-upsert"));
                default -> {
                    // no-op
                }
            }
        } catch (Exception ex) {
            log.warn(
                    "AFTER_COMMIT AI upsert bridge failed type={} entityId={}: {}",
                    event.entityType(),
                    event.entityId(),
                    ex.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDelete(SearchIndexDeleteEvent event) {
        try {
            // Knowledge / embedding cleanup hooks can be added later; keep bridge present for symmetry.
            lifecycleEvents.publish(
                    event.organizationId(),
                    AiLifecycleEvent.KNOWLEDGE_REFRESH,
                    event.entityType().name(),
                    event.entityId(),
                    java.util.Map.of("action", "delete"));
        } catch (Exception ex) {
            log.warn(
                    "AFTER_COMMIT AI delete bridge failed type={} entityId={}: {}",
                    event.entityType(),
                    event.entityId(),
                    ex.getMessage());
        }
    }
}
