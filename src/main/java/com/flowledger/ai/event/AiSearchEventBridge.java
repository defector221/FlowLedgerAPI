package com.flowledger.ai.event;

import com.flowledger.ai.automation.AutomationService;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.recommendation.RecommendationGenerator;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.search.event.SearchIndexDeleteEvent;
import com.flowledger.search.event.SearchIndexUpsertEvent;
import com.flowledger.search.model.SearchEntityType;
import com.flowledger.search.service.SearchEntityDocumentLoader;
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
    private final SearchEntityDocumentLoader documentLoader;
    private final AiEntityCleanupService cleanupService;
    private final AutomationService automations;

    public AiSearchEventBridge(
            RecommendationGenerator recommendationGenerator,
            AiLifecycleEventPublisher lifecycleEvents,
            SearchEntityDocumentLoader documentLoader,
            AiEntityCleanupService cleanupService,
            AutomationService automations) {
        this.recommendationGenerator = recommendationGenerator;
        this.lifecycleEvents = lifecycleEvents;
        this.documentLoader = documentLoader;
        this.cleanupService = cleanupService;
        this.automations = automations;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUpsert(SearchIndexUpsertEvent event) {
        try {
            TenantContext.set(event.organizationId(), null);
            if (documentLoader.load(event.organizationId(), event.entityType(), event.entityId()) == null) {
                int removed =
                        cleanupService.cleanupEntity(event.organizationId(), event.entityType(), event.entityId());
                log.debug(
                        "AI cleanup on missing upsert type={} entityId={} removed={}",
                        event.entityType(),
                        event.entityId(),
                        removed);
                return;
            }
            SearchEntityType type = event.entityType();
            switch (type) {
                case PRODUCT -> {
                    int n = recommendationGenerator.onProductChanged(event.entityId());
                    lifecycleEvents.recommendationSeed(event.organizationId(), type.name(), event.entityId());
                    automations.handleEvent(event.organizationId(), "PRODUCT_UPSERT");
                    log.debug("AI inventory heuristics created={} for product={}", n, event.entityId());
                }
                case CUSTOMER -> {
                    int n = recommendationGenerator.onCustomerChanged(event.entityId());
                    lifecycleEvents.recommendationSeed(event.organizationId(), type.name(), event.entityId());
                    automations.handleEvent(event.organizationId(), "CUSTOMER_UPSERT");
                    log.debug("AI credit heuristics created={} for customer={}", n, event.entityId());
                }
                case SALES_INVOICE, PURCHASE_INVOICE, SUPPLIER, SHIPMENT -> {
                    lifecycleEvents.publish(
                            event.organizationId(),
                            AiLifecycleEvent.RECOMMENDATION_SEED,
                            type.name(),
                            event.entityId(),
                            java.util.Map.of("source", "search-upsert"));
                    automations.handleEvent(event.organizationId(), type.name() + "_UPSERT");
                }
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
            TenantContext.set(event.organizationId(), null);
            int removed = cleanupService.cleanupEntity(event.organizationId(), event.entityType(), event.entityId());
            log.debug(
                    "AI delete cleanup type={} entityId={} removed={}", event.entityType(), event.entityId(), removed);
        } catch (Exception ex) {
            log.warn(
                    "AFTER_COMMIT AI delete bridge failed type={} entityId={}: {}",
                    event.entityType(),
                    event.entityId(),
                    ex.getMessage());
        } finally {
            TenantContext.clear();
        }
    }
}
