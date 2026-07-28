package com.flowledger.commerce.engagement;

import com.flowledger.commerce.engagement.entity.CommerceCustomerNotification;
import com.flowledger.commerce.engagement.repository.CommerceCustomerNotificationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommerceCustomerNotificationService {
    private final CommerceCustomerNotificationRepository notifications;

    public CommerceCustomerNotificationService(CommerceCustomerNotificationRepository notifications) {
        this.notifications = notifications;
    }

    public CommerceCustomerNotification create(
            UUID customerId, UUID organizationId, String eventType, String title, String body) {
        CommerceCustomerNotification n = new CommerceCustomerNotification();
        n.setCustomerId(customerId);
        n.setOrganizationId(organizationId);
        n.setEventType(eventType);
        n.setTitle(title);
        n.setBody(body);
        return notifications.save(n);
    }

    @Transactional(readOnly = true)
    public List<CommerceCustomerNotification> list(UUID customerId) {
        return notifications.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    public void markRead(UUID notificationId, UUID customerId) {
        notifications.findById(notificationId).ifPresent(n -> {
            if (n.getCustomerId().equals(customerId)) {
                n.setReadAt(OffsetDateTime.now());
                notifications.save(n);
            }
        });
    }
}
