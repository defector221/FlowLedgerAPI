package com.flowledger.notification.service;

import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.notification.dto.InAppNotificationDtos.InAppNotificationResponse;
import com.flowledger.notification.dto.InAppNotificationDtos.UnreadCountResponse;
import com.flowledger.notification.entity.InAppNotification;
import com.flowledger.notification.repository.InAppNotificationRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InAppNotificationService {
    private final InAppNotificationRepository repository;

    public InAppNotificationService(InAppNotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<InAppNotificationResponse> list(Pageable pageable) {
        UUID userId = requireUser();
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount() {
        return new UnreadCountResponse(repository.countByUserIdAndReadAtIsNull(requireUser()));
    }

    public InAppNotificationResponse markRead(UUID id) {
        InAppNotification notification = repository
                .findByIdAndUserId(id, requireUser())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(OffsetDateTime.now());
        }
        return toResponse(notification);
    }

    public UnreadCountResponse markAllRead() {
        UUID userId = requireUser();
        repository.markAllRead(userId, OffsetDateTime.now());
        return new UnreadCountResponse(0);
    }

    private UUID requireUser() {
        return TenantContext.userId()
                .orElseThrow(() -> new IllegalStateException("User context is not set"));
    }

    private InAppNotificationResponse toResponse(InAppNotification n) {
        return new InAppNotificationResponse(
                n.getId(),
                n.getTitle(),
                n.getBody(),
                n.getNotificationType(),
                n.getEntityType(),
                n.getEntityId(),
                n.getReadAt() != null,
                n.getCreatedAt(),
                buildLink(n.getEntityType(), n.getEntityId()));
    }

    private static String buildLink(String entityType, UUID entityId) {
        if (entityType == null || entityId == null) return null;
        return switch (entityType) {
            case "SalesInvoice" -> "/sales/invoices/" + entityId;
            case "User" -> "/settings/users";
            case "OrganizationMembership" -> "/settings/users";
            default -> null;
        };
    }
}
