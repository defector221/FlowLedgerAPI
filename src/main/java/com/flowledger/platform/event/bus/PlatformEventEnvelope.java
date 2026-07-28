package com.flowledger.platform.event.bus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PlatformEventEnvelope(
        UUID id,
        String eventType,
        int eventVersion,
        UUID organizationId,
        String aggregateType,
        UUID aggregateId,
        Map<String, Object> payload,
        UUID correlationId,
        UUID actorId,
        Instant occurredAt) {}
