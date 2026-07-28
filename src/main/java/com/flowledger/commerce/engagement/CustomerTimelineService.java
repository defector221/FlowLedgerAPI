package com.flowledger.commerce.engagement;

import com.flowledger.commerce.engagement.entity.CommerceCustomerNotification;
import com.flowledger.platform.event.bus.PlatformEventOutbox;
import com.flowledger.platform.event.bus.PlatformEventOutboxRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerTimelineService {
    private final CommerceCustomerNotificationService notifications;
    private final PlatformEventOutboxRepository outbox;

    public CustomerTimelineService(CommerceCustomerNotificationService notifications, PlatformEventOutboxRepository outbox) {
        this.notifications = notifications;
        this.outbox = outbox;
    }

    public List<TimelineEntry> timeline(UUID customerId, UUID organizationId) {
        List<TimelineEntry> entries = new ArrayList<>();

        for (CommerceCustomerNotification n : notifications.list(customerId)) {
            if (organizationId != null && n.getOrganizationId() != null && !organizationId.equals(n.getOrganizationId())) {
                continue;
            }
            entries.add(new TimelineEntry(
                    n.getId(),
                    n.getEventType(),
                    n.getTitle(),
                    n.getBody(),
                    n.getCreatedAt()));
        }

        List<PlatformEventOutbox> events = outbox.findUnpublished(PageRequest.of(0, 100));
        for (PlatformEventOutbox e : events) {
            if (e.getActorId() != null && e.getActorId().equals(customerId)) {
                entries.add(new TimelineEntry(
                        e.getId(),
                        e.getEventType(),
                        e.getEventType(),
                        null,
                        e.getOccurredAt()));
            }
        }

        entries.sort(Comparator.comparing(TimelineEntry::occurredAt).reversed());
        return entries;
    }

    public record TimelineEntry(UUID id, String eventType, String title, String body, OffsetDateTime occurredAt) {}
}
