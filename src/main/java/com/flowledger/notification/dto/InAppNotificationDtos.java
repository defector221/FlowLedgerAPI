package com.flowledger.notification.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class InAppNotificationDtos {
    private InAppNotificationDtos() {}

    public record InAppNotificationResponse(
            UUID id,
            String title,
            String body,
            String notificationType,
            String entityType,
            UUID entityId,
            boolean read,
            OffsetDateTime createdAt,
            String link) {}

    public record UnreadCountResponse(long count) {}
}
