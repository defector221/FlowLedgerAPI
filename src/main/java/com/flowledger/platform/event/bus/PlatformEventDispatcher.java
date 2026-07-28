package com.flowledger.platform.event.bus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PlatformEventDispatcher {
    private static final Logger log = LoggerFactory.getLogger(PlatformEventDispatcher.class);

    private final PlatformEventOutboxRepository outbox;
    private final ApplicationEventPublisher springEvents;
    private final ObjectMapper objectMapper;
    private final Map<String, List<Consumer<PlatformEventEnvelope>>> handlers = new ConcurrentHashMap<>();

    public PlatformEventDispatcher(
            PlatformEventOutboxRepository outbox,
            ApplicationEventPublisher springEvents,
            ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.springEvents = springEvents;
        this.objectMapper = objectMapper;
    }

    public void register(String eventType, Consumer<PlatformEventEnvelope> handler) {
        handlers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(handler);
    }

    @Scheduled(fixedDelayString = "${platform.event-bus.poll-ms:2000}")
    @Transactional
    public void pollAndDispatch() {
        List<PlatformEventOutbox> batch = outbox.findUnpublished(PageRequest.of(0, 50));
        for (PlatformEventOutbox row : batch) {
            try {
                PlatformEventEnvelope envelope = toEnvelope(row);
                springEvents.publishEvent(envelope);
                List<Consumer<PlatformEventEnvelope>> typeHandlers = handlers.get(row.getEventType());
                if (typeHandlers != null) {
                    for (Consumer<PlatformEventEnvelope> handler : typeHandlers) {
                        handler.accept(envelope);
                    }
                }
                row.setPublishedAt(OffsetDateTime.now());
                outbox.save(row);
            } catch (Exception ex) {
                log.warn("Failed to dispatch platform event {}: {}", row.getId(), ex.getMessage());
            }
        }
    }

    private PlatformEventEnvelope toEnvelope(PlatformEventOutbox row) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(row.getPayload(), new TypeReference<>() {});
        return new PlatformEventEnvelope(
                row.getId(),
                row.getEventType(),
                row.getEventVersion(),
                row.getOrganizationId(),
                row.getAggregateType(),
                row.getAggregateId(),
                payload,
                row.getCorrelationId(),
                row.getActorId(),
                row.getOccurredAt().toInstant());
    }
}
